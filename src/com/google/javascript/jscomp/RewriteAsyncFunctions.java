/*
 * Copyright 2016 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Converts async functions to valid ES6 generator functions code.
 *
 * <p>This pass must run before the passes that transpile let declarations, arrow functions, and
 * generators.
 *
 * <p>An async function, foo(a, b), will be rewritten as:
 *
 * <pre>{@code
 * function foo(a, b) {
 *   let $jscomp$async$this = this;
 *   let $jscomp$async$arguments = arguments;
 *   let $jscomp$async$super$get$x = () => super.x;
 *   return $jscomp.asyncExecutePromiseGeneratorFunction(
 *       function* () {
 *         // original body of foo() with:
 *         // - await (x) replaced with yield (x)
 *         // - arguments replaced with $jscomp$async$arguments
 *         // - this replaced with $jscomp$async$this
 *         // - super.x replaced with $jscomp$async$super$get$x()
 *         // - super.x(5) replaced with $jscomp$async$super$get$x().call($jscomp$async$this, 5)
 *       });
 * }
 * }</pre>
 */
public final class RewriteAsyncFunctions implements NodeTraversal.Callback, CompilerPass {

  private static final String ASYNC_ARGUMENTS = "$jscomp$async$arguments";
  private static final String ASYNC_THIS = "$jscomp$async$this";
  private static final String ASYNC_SUPER_PROP_GETTER_PREFIX = "$jscomp$async$super$get$";
  private final StaticScope namespace;

  /**
   * Information needed to replace a reference to `super.propertyName` with a call to a wrapper
   * function.
   */
  private final class SuperPropertyWrapperInfo {
    // The first `super.property` Node we come across during traversal.
    // Information from this node will be used when creating the wrapper function.
    private final Node firstInstanceOfSuperDotProperty;
    private final String wrapperFunctionName;
    // The type to use for the wrapper function.
    // Will be null if type checking has not run.
    @Nullable private final AstFactory.Type wrapperFunctionReturnType;

    private SuperPropertyWrapperInfo(
        Node firstSuperDotPropertyNode,
        String wrapperFunctionName,
        AstFactory.Type wrapperFunctionReturnType) {
      this.firstInstanceOfSuperDotProperty = firstSuperDotPropertyNode;
      this.wrapperFunctionName = wrapperFunctionName;
      this.wrapperFunctionReturnType = wrapperFunctionReturnType;
    }

    @Nullable
    private Color getPropertyType() {
      return firstInstanceOfSuperDotProperty.getColor();
    }

    private Node createWrapperFunctionNameNode() {
      return astFactory.createName(wrapperFunctionName, type(StandardColors.TOP_OBJECT));
    }

    private Node createWrapperFunctionCallNode() {
      return astFactory.createCall(createWrapperFunctionNameNode(), wrapperFunctionReturnType);
    }
  }

  /**
   * Used to collect information about properties referenced via `super.propertyName` within an
   * async function.
   *
   * <p>We'll have to replace these references with calls to a wrapper function.
   */
  private final class SuperPropertyWrappers {
    // Use LinkedHashMap in order to ensure the ordering is the same on every compile of the same
    // source code.
    // Note that the Colors will be null if type checking hasn't run.
    private final Map<String, SuperPropertyWrapperInfo> propertyNameToTypeMap =
        new LinkedHashMap<>();

    private SuperPropertyWrapperInfo getOrCreateSuperPropertyWrapperInfo(
        Node superDotPropertyNode) {
      checkArgument(superDotPropertyNode.isGetProp(), superDotPropertyNode);
      Node superNode = superDotPropertyNode.getFirstChild();
      checkArgument(superNode.isSuper(), superNode);

      String propertyName = superDotPropertyNode.getString();
      Color propertyType = superDotPropertyNode.getColor();
      final SuperPropertyWrapperInfo superPropertyWrapperInfo;
      if (propertyNameToTypeMap.containsKey(propertyName)) {
        superPropertyWrapperInfo = propertyNameToTypeMap.get(propertyName);
        // Every reference to `super.propertyName` within a single lexical context should
        // have the same type.  Make sure this is true.
        Color existingColor = superPropertyWrapperInfo.getPropertyType();
        checkState(
            Objects.equals(existingColor, propertyType),
            "Previous reference type: %s differs from current reference type: %s",
            existingColor,
            propertyType);
      } else {
        superPropertyWrapperInfo = createNewInfo(superDotPropertyNode);
        propertyNameToTypeMap.put(propertyName, superPropertyWrapperInfo);
      }
      return superPropertyWrapperInfo;
    }

    private SuperPropertyWrapperInfo createNewInfo(Node firstSuperDotPropertyNode) {
      checkArgument(firstSuperDotPropertyNode.isGetProp(), firstSuperDotPropertyNode);
      String propertyName = firstSuperDotPropertyNode.getString();
      final String wrapperFunctionName = ASYNC_SUPER_PROP_GETTER_PREFIX + propertyName;
      return new SuperPropertyWrapperInfo(
          firstSuperDotPropertyNode, wrapperFunctionName, type(firstSuperDotPropertyNode));
    }

    private Collection<SuperPropertyWrapperInfo> asCollection() {
      return propertyNameToTypeMap.values();
    }
  }

  /**
   * Determines both what to do when visiting a node and how to determine the context for its
   * descendents.
   */
  private abstract class LexicalContext {
    final Node contextRootNode;

    LexicalContext(Node contextRootNode) {
      this.contextRootNode = checkNotNull(contextRootNode);
    }

    Node getContextRootNode() {
      return contextRootNode;
    }

    /**
     * Returns the LexicalContext to use for visiting a node.
     *
     * @param n This context's root node or one of its descendents.
     * @return this context or a new one for a child context
     */
    public abstract LexicalContext getContextForNode(Node n);

    public abstract void visit(NodeTraversal t, Node n);
  }

  /** Defines behavior for nodes in the root scope, outside of any functions. */
  private final class RootContext extends LexicalContext {

    private RootContext(Node contextRootNode) {
      super(contextRootNode);
    }

    @Override
    public LexicalContext getContextForNode(Node n) {
      if (n.isFunction()) {
        return new FunctionContext(n);
      } else {
        return this;
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n) {
      // In root context we haven't entered an async function yet, so there's nothing to do.
    }
  }

  /** Defines the behavior for function definition parameter lists and their contents. */
  private final class ParameterListContext extends LexicalContext {
    final FunctionContext functionContext;

    public ParameterListContext(FunctionContext functionContext, Node contextRootNode) {
      super(contextRootNode);
      this.functionContext = checkNotNull(functionContext);
    }

    @Override
    public LexicalContext getContextForNode(Node n) {
      if (n.isFunction()) {
        // Function defined within a parameter list.
        // e.g. `() => something`
        // function someFunc(callback = () => something) {}
        return new FunctionContext(functionContext, n);
      } else {
        return this;
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n) {
      if (functionContext.asyncThisAndArgumentsContext != null
          && functionContext.asyncThisAndArgumentsContext != functionContext) {
        // e.g.
        // async function outer(outerT = this) {
        //   // `this` in outer parameter list must remain unchanged
        //   // but inner parameter list must be aliased
        //   const inner = async (t = this) => t;
        // }
        functionContext.visit(t, n);
      }
    }
  }

  /**
   * Defines behavior for replacing references to `this`, `arguments`, and `super` within async
   * functions and rewriting the async functions themselves.
   */
  private final class FunctionContext extends LexicalContext {

    // If references to `this`, `arguments`, and `super` should be considered in the context of
    // an async function, this will point to that function's FunctionContext.
    // Otherwise, it will be `null`.
    // TODO(bradfordcsmith): It would cost less memory if we defined a separate object to hold
    // the data for async context accounting instead of having the booleans and super property
    // wrapper fields on every FunctionContext.
    @Nullable final FunctionContext asyncThisAndArgumentsContext;
    final SuperPropertyWrappers superPropertyWrappers = new SuperPropertyWrappers();

    boolean mustAddAsyncThisVariable = false;
    // null if mustAddAsyncThisVariable is false
    @Nullable AstFactory.Type typeOfThis;
    boolean mustAddAsyncArgumentsVariable = false;

    FunctionContext(Node contextRootNode) {
      super(contextRootNode);
      if (contextRootNode.isAsyncFunction()) {
        asyncThisAndArgumentsContext = this;
      } else {
        asyncThisAndArgumentsContext = null;
      }
    }

    FunctionContext(FunctionContext outer, Node contextRootNode) {
      super(contextRootNode);
      checkState(contextRootNode.isFunction(), contextRootNode);
      if (contextRootNode.isAsyncFunction()) {
        if (contextRootNode.isArrowFunction()) {
          // An async arrow function context points to outer.asyncThisAndArgumentsContext
          // if non-null, otherwise to itself.
          asyncThisAndArgumentsContext =
              outer.asyncThisAndArgumentsContext == null
                  ? this
                  : outer.asyncThisAndArgumentsContext;
        } else {
          // An async non-arrow function context always points to itself
          asyncThisAndArgumentsContext = this;
        }
      } else {
        if (contextRootNode.isArrowFunction()) {
          // A non-async arrow function context always points to
          // outer.asyncThisAndArgumentsContext
          asyncThisAndArgumentsContext = outer.asyncThisAndArgumentsContext;
        } else {
          // A non-async, non-arrow function has no async context.
          asyncThisAndArgumentsContext = null;
        }
      }
    }

    @Override
    public LexicalContext getContextForNode(Node n) {
      if (n == contextRootNode) {
        return this;
      } else if (n.isFunction()) {
        return new FunctionContext(this, n);
      } else if (n.isParamList()) {
        return new ParameterListContext(this, n);
      } else {
        return this;
      }
    }

    private void recordAsyncThisReplacementWasDone(AstFactory.Type typeOfThis) {
      asyncThisAndArgumentsContext.mustAddAsyncThisVariable = true;
      asyncThisAndArgumentsContext.typeOfThis = typeOfThis;
    }

    private SuperPropertyWrapperInfo getOrCreateSuperPropertyWrapperInfo(
        Node superDotPropertyNode) {
      return asyncThisAndArgumentsContext.superPropertyWrappers.getOrCreateSuperPropertyWrapperInfo(
          superDotPropertyNode);
    }

    private void recordAsyncArgumentsReplacementWasDone() {
      asyncThisAndArgumentsContext.mustAddAsyncArgumentsVariable = true;
    }

    /**
     * Creates a new reference to the variable used to hold the value of `this` for async functions.
     */
    private Node createThisVariableReference(AstFactory.Type typeOfThis) {
      recordAsyncThisReplacementWasDone(typeOfThis);
      return astFactory.createName(ASYNC_THIS, typeOfThis);
    }

    private Node createWrapperArrowFunction(SuperPropertyWrapperInfo wrapperInfo) {
      // super.propertyName
      final Node superDotProperty = wrapperInfo.firstInstanceOfSuperDotProperty.cloneTree();

      // () => super.propertyName
      return astFactory.createZeroArgArrowFunctionForExpression(superDotProperty);
    }

    @Override
    public void visit(NodeTraversal t, Node n) {
      if (contextRootNode == n && contextRootNode.isAsyncFunction()) {
        // We're visiting an async function.
        // All of its descendent nodes will have been updated as necessary, so now we just need to
        // convert the function itself.
        convertAsyncFunction(t, this);
      } else if (asyncThisAndArgumentsContext != null) {
        // We're in the context of an async function's body, so we need to do some replacements.
        switch (n.getToken()) {
          case NAME:
            if (n.matchesQualifiedName("arguments")) {
              n.setString(ASYNC_ARGUMENTS);
              asyncThisAndArgumentsContext.recordAsyncArgumentsReplacementWasDone();
              compiler.reportChangeToChangeScope(contextRootNode);
            }
            break;

          case THIS:
            n.replaceWith(asyncThisAndArgumentsContext.createThisVariableReference(type(n)));
            compiler.reportChangeToChangeScope(contextRootNode);
            break;

          case SUPER:
            {
              Node parent = n.getParent();
              if (!parent.isGetProp()) {
                compiler.report(
                    JSError.make(parent, Es6ToEs3Util.CANNOT_CONVERT_YET, "super expression"));
              }
              // different name for parent for better readability
              Node superDotProperty = parent;

              SuperPropertyWrapperInfo superPropertyWrapperInfo =
                  asyncThisAndArgumentsContext.getOrCreateSuperPropertyWrapperInfo(
                      superDotProperty);

              // super.x   =>   $jscomp$super$get$x()
              Node getPropReplacement = superPropertyWrapperInfo.createWrapperFunctionCallNode();
              Node grandparent = superDotProperty.getParent();
              if (grandparent.isCall() && grandparent.getFirstChild() == superDotProperty) {
                // $jscomp$super$get$x(...)   =>   $jscomp$super$get$x().call($jscomp$async$this,
                // ...)
                getPropReplacement =
                    astFactory.createGetPropWithUnknownType(getPropReplacement, "call");
                Node thisAlias =
                    astFactory
                        .createThisAliasReferenceForEs6Class(
                            ASYNC_THIS,
                            NodeUtil.getEnclosingClass(
                                asyncThisAndArgumentsContext.getContextRootNode()))
                        .srcref(superDotProperty);
                thisAlias.insertAfter(superDotProperty);
                asyncThisAndArgumentsContext.recordAsyncThisReplacementWasDone(type(thisAlias));
              }
              getPropReplacement.srcrefTree(superDotProperty);
              superDotProperty.replaceWith(getPropReplacement);
              compiler.reportChangeToChangeScope(contextRootNode);
            }
            break;

          case AWAIT:
            // Awaits become yields in the converted async function's inner generator function.
            n.replaceWith(astFactory.createYield(type(n), n.removeFirstChild()));
            break;

          default:
            break;
        }
      }
    }
  }

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ASYNC_FUNCTIONS);

  private final Deque<LexicalContext> contextStack;
  private final AbstractCompiler compiler;

  private final AstFactory astFactory;

  private RewriteAsyncFunctions(
      AbstractCompiler compiler, AstFactory astFactory, StaticScope namespace) {
    this.compiler = checkNotNull(compiler);
    this.astFactory = checkNotNull(astFactory);
    this.namespace = checkNotNull(namespace);
    this.contextStack = new ArrayDeque<>();
  }

  static RewriteAsyncFunctions create(AbstractCompiler compiler) {
    AstFactory astFactory = compiler.createAstFactory();
    StaticScope namespace = compiler.getTranspilationNamespace();
    return new RewriteAsyncFunctions(compiler, astFactory, namespace);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (parent == null) {
      checkState(contextStack.isEmpty());
      contextStack.push(new RootContext(n));
    } else {
      LexicalContext parentContext = contextStack.peek();
      LexicalContext nodeContext = parentContext.getContextForNode(n);
      if (nodeContext != parentContext) {
        contextStack.push(nodeContext);
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    LexicalContext context = contextStack.peek();
    context.visit(t, n);
    if (context.getContextRootNode() == n) {
      contextStack.pop();
    }
  }

  private void convertAsyncFunction(NodeTraversal t, FunctionContext functionContext) {
    Node originalFunction = functionContext.getContextRootNode();
    originalFunction.setIsAsyncFunction(false);
    Node originalBody = originalFunction.getLastChild();
    if (originalFunction.isFromExterns()) {
      // A function defined in externs will never be executed, so we don't need to transpile it.
      // Make sure it has an empty body though so later passes won't trip over uses of `await` or
      // anything like that.
      if (!NodeUtil.isEmptyBlock(originalBody)) {
        // TODO(b/119685646): Maybe we should warn for non-empty functions in externs?
        originalBody.replaceWith(astFactory.createBlock());
        NodeUtil.markFunctionsDeleted(originalBody, compiler);
      }
      return;
    }
    Node newBody = astFactory.createBlock();
    originalBody.replaceWith(newBody);

    if (functionContext.mustAddAsyncThisVariable) {
      // const this$ = this;
      newBody.addChildToBack(
          astFactory.createSingleConstNameDeclaration(
              ASYNC_THIS, astFactory.createThis(functionContext.typeOfThis)));
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
    }
    if (functionContext.mustAddAsyncArgumentsVariable) {
      // const arguments$ = arguments;
      newBody.addChildToBack(astFactory.createArgumentsAliasDeclaration(ASYNC_ARGUMENTS));
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
    }
    for (SuperPropertyWrapperInfo superPropertyWrapperInfo :
        functionContext.superPropertyWrappers.asCollection()) {
      Node arrowFunction = functionContext.createWrapperArrowFunction(superPropertyWrapperInfo);

      // const super$get$x = () => super.x;
      Node arrowFunctionDeclarationStatement =
          astFactory.createSingleConstNameDeclaration(
              superPropertyWrapperInfo.wrapperFunctionName, arrowFunction);
      newBody.addChildToBack(arrowFunctionDeclarationStatement);

      // Make sure the compiler knows about the new arrow function's scope
      compiler.reportChangeToChangeScope(arrowFunction);
      // Record that we've added arrow functions and const declarations to this script,
      // so later transpilations of those features will run, if needed.
      Node enclosingScript = t.getCurrentScript();
      NodeUtil.addFeatureToScript(enclosingScript, Feature.ARROW_FUNCTIONS, compiler);
      NodeUtil.addFeatureToScript(enclosingScript, Feature.CONST_DECLARATIONS, compiler);
    }

    // Normalize arrow function short body to block body
    if (!originalBody.isBlock()) {
      originalBody =
          astFactory
              .createBlock(astFactory.createReturn(originalBody))
              .srcrefTreeIfMissing(originalBody);
    }
    // NOTE: visit() will already have made appropriate replacements in originalBody so it may
    // be used as the generator function body.
    Node generatorFunction =
        astFactory.createZeroArgGeneratorFunction("", originalBody, /* returnType= */ null);
    compiler.reportChangeToChangeScope(generatorFunction);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.GENERATORS, compiler);

    // return $jscomp.asyncExecutePromiseGeneratorFunction(function* () { ... });
    newBody.addChildToBack(
        astFactory.createReturn(
            astFactory.createJscompAsyncExecutePromiseGeneratorFunctionCall(
                namespace, generatorFunction)));

    newBody.srcrefTreeIfMissing(originalBody);
    compiler.reportChangeToEnclosingScope(newBody);
  }
}
