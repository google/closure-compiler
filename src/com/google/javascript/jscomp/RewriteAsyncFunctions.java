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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
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
 * <pre> {@code
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
 * }}</pre>
 */
public final class RewriteAsyncFunctions implements NodeTraversal.Callback, HotSwapCompilerPass {

  private static final String ASYNC_ARGUMENTS = "$jscomp$async$arguments";
  private static final String ASYNC_THIS = "$jscomp$async$this";
  private static final String ASYNC_SUPER_PROP_GETTER_PREFIX = "$jscomp$async$super$get$";

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
    @Nullable private final JSType wrapperFunctionType;

    private SuperPropertyWrapperInfo(
        Node firstSuperDotPropertyNode, String wrapperFunctionName, JSType wrapperFunctionType) {
      this.firstInstanceOfSuperDotProperty = firstSuperDotPropertyNode;
      this.wrapperFunctionName = wrapperFunctionName;
      this.wrapperFunctionType = wrapperFunctionType;
    }

    @Nullable
    private JSType getPropertyType() {
      return firstInstanceOfSuperDotProperty.getJSType();
    }

    private Node createWrapperFunctionNameNode() {
      return astFactory.createName(wrapperFunctionName, wrapperFunctionType);
    }

    private Node createWrapperFunctionCallNode() {
      return astFactory.createCall(createWrapperFunctionNameNode());
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
    // Note that the JSTypes will be null if type checking hasn't run.
    private final Map<String, SuperPropertyWrapperInfo> propertyNameToTypeMap =
        new LinkedHashMap<>();

    private SuperPropertyWrapperInfo getOrCreateSuperPropertyWrapperInfo(
        Node superDotPropertyNode) {
      checkArgument(superDotPropertyNode.isGetProp(), superDotPropertyNode);
      Node superNode = superDotPropertyNode.getFirstChild();
      checkArgument(superNode.isSuper(), superNode);
      Node propertyNameNode = superDotPropertyNode.getLastChild();
      checkArgument(propertyNameNode.isString(), propertyNameNode);

      String propertyName = propertyNameNode.getString();
      JSType propertyType = superDotPropertyNode.getJSType();
      final SuperPropertyWrapperInfo superPropertyWrapperInfo;
      if (propertyNameToTypeMap.containsKey(propertyName)) {
        superPropertyWrapperInfo = propertyNameToTypeMap.get(propertyName);
        // Every reference to `super.propertyName` within a single lexical context should
        // have the same type.  Make sure this is true.
        JSType existingJSType = superPropertyWrapperInfo.getPropertyType();
        checkState(
            Objects.equals(existingJSType, propertyType),
            "Previous reference type: %s differs from current reference type: %s",
            existingJSType,
            propertyType);
      } else {
        superPropertyWrapperInfo = createNewInfo(superDotPropertyNode);
        propertyNameToTypeMap.put(propertyName, superPropertyWrapperInfo);
      }
      return superPropertyWrapperInfo;
    }

    private SuperPropertyWrapperInfo createNewInfo(Node firstSuperDotPropertyNode) {
      checkArgument(firstSuperDotPropertyNode.isGetProp(), firstSuperDotPropertyNode);
      String propertyName = firstSuperDotPropertyNode.getLastChild().getString();
      JSType propertyType = firstSuperDotPropertyNode.getJSType();
      final String wrapperFunctionName = ASYNC_SUPER_PROP_GETTER_PREFIX + propertyName;
      final JSType wrapperFunctionType;
      if (propertyType == null) {
        // type checking hasn't run, so we don't need type information.
        wrapperFunctionType = null;
      } else {
        wrapperFunctionType = new FunctionBuilder(registry).withReturnType(propertyType).build();
      }
      return new SuperPropertyWrapperInfo(
          firstSuperDotPropertyNode, wrapperFunctionName, wrapperFunctionType);
    }

    private Collection<SuperPropertyWrapperInfo> asCollection() {
      return propertyNameToTypeMap.values();
    }
  }

  /**
   * Keeps track of whether we're examining nodes within an async function & what changes are needed
   * for the function currently in context.
   */
  private final class LexicalContext {
    @Nullable final Node function;
    @Nullable final LexicalContext asyncThisAndArgumentsContext;
    final SuperPropertyWrappers superPropertyWrappers = new SuperPropertyWrappers();

    boolean mustAddAsyncThisVariable = false;
    boolean mustAddAsyncArgumentsVariable = false;

    LexicalContext() {
      function = null;
      asyncThisAndArgumentsContext = null;
    }

    LexicalContext(LexicalContext outer, Node function) {
      this.function = checkNotNull(function);

      if (function.isAsyncFunction()) {
        if (function.isArrowFunction()) {
          // An async arrow function context points to outer.asyncThisAndArgumentsContext
          // if non-null, otherwise to itself.
          asyncThisAndArgumentsContext = outer.asyncThisAndArgumentsContext == null
              ? this : outer.asyncThisAndArgumentsContext;
        } else {
          // An async non-arrow function context always points to itself
          asyncThisAndArgumentsContext = this;
        }
      } else {
        if (function.isArrowFunction()) {
          // A non-async arrow function context always points to outer.asyncThisAndArgumentsContext
          asyncThisAndArgumentsContext = outer.asyncThisAndArgumentsContext;
        } else {
          // A non-async, non-arrow function has no async context.
          asyncThisAndArgumentsContext = null;
        }
      }
    }

    boolean isAsyncContext() {
      return function.isAsyncFunction();
    }

    boolean mustReplaceThisAndArguments() {
      return asyncThisAndArgumentsContext != null;
    }

    void recordAsyncThisReplacementWasDone() {
      asyncThisAndArgumentsContext.mustAddAsyncThisVariable = true;
    }

    SuperPropertyWrapperInfo getOrCreateSuperPropertyWrapperInfo(Node superDotPropertyNode) {
      return asyncThisAndArgumentsContext.superPropertyWrappers.getOrCreateSuperPropertyWrapperInfo(
          superDotPropertyNode);
    }

    void recordAsyncArgumentsReplacementWasDone() {
      asyncThisAndArgumentsContext.mustAddAsyncArgumentsVariable = true;
    }

    /**
     * Creates a new reference to the variable used to hold the value of `this` for async functions.
     */
    Node createThisVariableReference() {
      recordAsyncThisReplacementWasDone();
      return astFactory.createThisAliasReferenceForFunction(
          ASYNC_THIS, asyncThisAndArgumentsContext.function);
    }

    /** Creates a correctly typed `this` node for this context. */
    Node createThisReference() {
      return astFactory.createThisForFunction(asyncThisAndArgumentsContext.function);
    }

    private Node createWrapperArrowFunction(SuperPropertyWrapperInfo wrapperInfo) {
      // super.propertyName
      final Node superDotProperty = wrapperInfo.firstInstanceOfSuperDotProperty.cloneTree();
      if (rewriteSuperPropertyReferencesWithoutSuper) {
        // Rewrite to avoid using `super` within an arrow function.
        // See more information on definition of this option.
        // TODO(bradfordcsmith): RewriteAsyncIteration and RewriteAsyncFunctions have the
        // same logic for dealing with super references. Consider having them share
        // it from a common place instead of duplicating.
        final Node thisNode = createThisReference();
        final Node prototypeOfThisNode = astFactory.createObjectGetPrototypeOfCall(thisNode);
        final Node originalSuperNode = superDotProperty.getFirstChild();

        // NOTE: must look at the enclosing MEMBER_FUNCTION_DEF to see if the method is static
        if (asyncThisAndArgumentsContext.function.getParent().isStaticMember()) {
          // For static methods `this` is the class and its direct prototype is the parent
          // class and the super node we want
          // super.propertyName -> Object.getPrototypeOf(this).propertyName
          originalSuperNode.replaceWith(prototypeOfThisNode);
        } else {
          // For instance methods `this` is the instance, and its direct prototype is the
          // ClassName.prototype object. We must go to the prototype of that to get the correct
          // value for `super`.
          // super.propertyName -> Object.getPrototypeOf(Object.getPrototypeOf(this)).propertyName
          originalSuperNode.replaceWith(
              astFactory.createObjectGetPrototypeOfCall(prototypeOfThisNode));
        }
      }
      // () => super.propertyName
      // OR avoid super for static method (class object -> superclass object)
      // () => Object.getPrototypeOf(this).x
      // OR avoid super for instance method (instance -> prototype -> super prototype)
      // () => Object.getPrototypeOf(Object.getPrototypeOf(this)).x
      return astFactory.createZeroArgArrowFunctionForExpression(superDotProperty);
    }
  }

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ASYNC_FUNCTIONS);

  private final Deque<LexicalContext> contextStack;
  private final AbstractCompiler compiler;

  /**
   * If this option is set to true, then this pass will rewrite references to properties using super
   * (e.g. `super.method()`) to avoid using `super` within an arrow function.
   *
   * <p>This option exists due to a bug in MS Edge 17 which causes it to fail to access super
   * properties correctly from within arrow functions.
   *
   * <p>See https://github.com/Microsoft/ChakraCore/issues/5784
   *
   * <p>If the final compiler output will not include ES6 classes, this option should not be set. It
   * isn't needed since the `super` references will be transpiled away anyway. Also, when this
   * option is set it uses `Object.getPrototypeOf()` to rewrite `super`, which may not exist in
   * pre-ES6 JS environments.
   */
  private final boolean rewriteSuperPropertyReferencesWithoutSuper;

  private final JSTypeRegistry registry;
  private final AstFactory astFactory;

  private RewriteAsyncFunctions(Builder builder) {
    checkNotNull(builder);
    this.compiler = builder.compiler;
    this.contextStack = new ArrayDeque<>();
    this.contextStack.addFirst(new LexicalContext());
    this.rewriteSuperPropertyReferencesWithoutSuper =
        builder.rewriteSuperPropertyReferencesWithoutSuper;
    this.registry = checkNotNull(builder.registry);
    this.astFactory = checkNotNull(builder.astFactory);
  }

  static class Builder {
    private final AbstractCompiler compiler;
    private boolean rewriteSuperPropertyReferencesWithoutSuper = false;
    private JSTypeRegistry registry;
    private AstFactory astFactory;

    Builder(AbstractCompiler compiler) {
      checkNotNull(compiler);
      this.compiler = compiler;
    }

    Builder rewriteSuperPropertyReferencesWithoutSuper(boolean value) {
      rewriteSuperPropertyReferencesWithoutSuper = value;
      return this;
    }

    RewriteAsyncFunctions build() {
      astFactory = compiler.createAstFactory();
      registry = compiler.getTypeRegistry();
      return new RewriteAsyncFunctions(this);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFunction()) {
      contextStack.addFirst(new LexicalContext(contextStack.getFirst(), n));
      if (n.isAsyncFunction()) {
        compiler.ensureLibraryInjected("es6/execute_async_generator", /* force= */ false);
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    LexicalContext context = contextStack.getFirst();
    switch (n.getToken()) {
      case FUNCTION:
        checkState(
            context.function == n,
            "unexpected function context:\nexpected: %s\nactual: %s",
            n,
            context.function);
        checkState(contextStack.removeFirst() == context);
        if (context.isAsyncContext()) {
          convertAsyncFunction(t, context);
        }
        break;

      case NAME:
        if (context.mustReplaceThisAndArguments() && n.matchesQualifiedName("arguments")) {
          n.setString(ASYNC_ARGUMENTS);
          context.recordAsyncArgumentsReplacementWasDone();
          compiler.reportChangeToChangeScope(context.function);
        }
        break;

      case THIS:
        if (context.mustReplaceThisAndArguments()) {
          parent.replaceChild(n, context.createThisVariableReference());
          compiler.reportChangeToChangeScope(context.function);
        }
        break;

      case SUPER:
        if (context.mustReplaceThisAndArguments()) {
          if (!parent.isGetProp()) {
            compiler.report(
                JSError.make(parent, Es6ToEs3Util.CANNOT_CONVERT_YET, "super expression"));
          }
          // different name for parent for better readability
          Node superDotProperty = parent;

          SuperPropertyWrapperInfo superPropertyWrapperInfo =
              context.getOrCreateSuperPropertyWrapperInfo(superDotProperty);

          // super.x   =>   $jscomp$super$get$x()
          Node getPropReplacement = superPropertyWrapperInfo.createWrapperFunctionCallNode();
          Node grandparent = superDotProperty.getParent();
          if (grandparent.isCall() && grandparent.getFirstChild() == superDotProperty) {
            // $jscomp$super$get$x(...)   =>   $jscomp$super$get$x().call($jscomp$async$this, ...)
            getPropReplacement = astFactory.createGetProp(getPropReplacement, "call");
            grandparent.addChildAfter(
                astFactory
                    .createThisAliasReferenceForFunction(ASYNC_THIS, context.function)
                    .useSourceInfoFrom(superDotProperty),
                superDotProperty);
            context.recordAsyncThisReplacementWasDone();
          }
          getPropReplacement.useSourceInfoFromForTree(superDotProperty);
          grandparent.replaceChild(superDotProperty, getPropReplacement);
          compiler.reportChangeToChangeScope(context.function);
        }
        break;

      case AWAIT:
        checkState(context.isAsyncContext(), "await found within non-async function body");
        checkState(n.hasOneChild(), "await should have 1 operand, but has %s", n.getChildCount());
        // Awaits become yields in the converted async function's inner generator function.
        parent.replaceChild(n, astFactory.createYield(n.getJSType(), n.removeFirstChild()));
        break;

      default:
        break;
    }
  }

  private void convertAsyncFunction(NodeTraversal t, LexicalContext functionContext) {
    Node originalFunction = checkNotNull(functionContext.function);
    originalFunction.setIsAsyncFunction(false);
    Node originalBody = originalFunction.getLastChild();
    Node newBody = astFactory.createBlock();
    originalFunction.replaceChild(originalBody, newBody);

    if (functionContext.mustAddAsyncThisVariable) {
      // const this$ = this;
      newBody.addChildToBack(
          astFactory.createThisAliasDeclarationForFunction(ASYNC_THIS, functionContext.function));
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }
    if (functionContext.mustAddAsyncArgumentsVariable) {
      // const arguments$ = arguments;
      newBody.addChildToBack(astFactory.createArgumentsAliasDeclaration(ASYNC_ARGUMENTS));
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
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
      Node enclosingScript = t.getCurrentFile();
      NodeUtil.addFeatureToScript(enclosingScript, Feature.ARROW_FUNCTIONS);
      NodeUtil.addFeatureToScript(enclosingScript, Feature.CONST_DECLARATIONS);
    }

    // Normalize arrow function short body to block body
    if (!originalBody.isBlock()) {
      originalBody =
          astFactory
              .createBlock(astFactory.createReturn(originalBody))
              .useSourceInfoIfMissingFromForTree(originalBody);
    }
    // NOTE: visit() will already have made appropriate replacements in originalBody so it may
    // be used as the generator function body.
    Node generatorFunction =
        astFactory.createZeroArgGeneratorFunction("", originalBody, originalFunction.getJSType());
    compiler.reportChangeToChangeScope(generatorFunction);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.GENERATORS);

    // return $jscomp.asyncExecutePromiseGeneratorFunction(function* () { ... });
    newBody.addChildToBack(
        astFactory.createReturn(
            astFactory.createJscompAsyncExecutePromiseGeneratorFunctionCall(
                t.getScope(), generatorFunction)));

    newBody.useSourceInfoIfMissingFromForTree(originalBody);
    compiler.reportChangeToEnclosingScope(newBody);
  }
}
