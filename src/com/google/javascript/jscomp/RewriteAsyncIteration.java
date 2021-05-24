/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converts async generator functions into a function returning a new $jscomp.AsyncGenWrapper around
 * the original block and awaits/yields converted to yields of ActionRecords.
 *
 * <pre>{@code
 * async function* foo() {
 *   let res = await myPromise;
 *   yield res + 1;
 * }
 * }</pre>
 *
 * <p>becomes (prefixes trimmed for clarity)
 *
 * <pre>{@code
 * function foo() {
 *   return new $jscomp.AsyncGeneratorWrapper((function*(){
 *     let res = yield new $ActionRecord($ActionEnum.AWAIT_VALUE, myPromise);
 *     yield new $ActionRecord($ActionEnum.YIELD_VALUE, res + 1);
 *   })());
 * }
 * }</pre>
 */
public final class RewriteAsyncIteration implements NodeTraversal.Callback, CompilerPass {

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ASYNC_GENERATORS, Feature.FOR_AWAIT_OF);

  static final DiagnosticType CANNOT_CONVERT_ASYNCGEN =
      DiagnosticType.error("JSC_CANNOT_CONVERT_ASYNCGEN", "Cannot convert async generator. {0}");

  private static final String ACTION_RECORD_NAME = "$jscomp.AsyncGeneratorWrapper$ActionRecord";

  private static final String ACTION_ENUM_AWAIT =
      "$jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE";
  private static final String ACTION_ENUM_YIELD =
      "$jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE";
  private static final String ACTION_ENUM_YIELD_STAR =
      "$jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR";

  private static final String FOR_AWAIT_ITERATOR_TEMP_NAME = "$jscomp$forAwait$tempIterator";
  private static final String FOR_AWAIT_RESULT_TEMP_NAME = "$jscomp$forAwait$tempResult";
  private int nextForAwaitId = 0;

  private final AbstractCompiler compiler;

  private final ArrayDeque<LexicalContext> contextStack;
  private final String thisVarName = "$jscomp$asyncIter$this";
  private final String argumentsVarName = "$jscomp$asyncIter$arguments";
  private final String superPropGetterPrefix = "$jscomp$asyncIter$super$get$";
  private final JSTypeRegistry registry;
  private final AstFactory astFactory;
  private final JSType unknownType;

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

  /**
   * Tracks a function and its context of this/arguments/super, if such a context exists.
   */
  private static final class LexicalContext {

    // Node that creates the context
    private final Node contextRoot;
    // The current function, or null if root scope where we are not in a function.
    private final Node function;
    // The context of the most recent definition of this/super/arguments
    private final ThisSuperArgsContext thisSuperArgsContext;

    // Represents the global/root scope. Should only exist on the bottom of the contextStack.
    private LexicalContext(Node contextRoot) {
      this.contextRoot = checkNotNull(contextRoot);
      this.function = null;
      this.thisSuperArgsContext = null;
    }

    /**
     * Represents the context of a function or its parameter list.
     *
     * @param parent enclosing context
     * @param contextRoot FUNCTION or PARAM_LIST node
     * @param function same as contextRoot or the FUNCTION containing the PARAM_LIST
     */
    private LexicalContext(LexicalContext parent, Node contextRoot, Node function) {
      checkNotNull(parent);
      checkNotNull(contextRoot);
      checkArgument(contextRoot == function || contextRoot.isParamList(), contextRoot);
      checkNotNull(function);
      checkArgument(function.isFunction(), function);
      this.contextRoot = contextRoot;
      this.function = function;

      if (function.isArrowFunction()) {
        // Use the parent context to inherit this, arguments, and super for an arrow function or its
        // parameter list.
        this.thisSuperArgsContext = parent.thisSuperArgsContext;
      } else if (contextRoot.isFunction()) {
        // Non-arrow function gets its own context defining `this`, `arguments`, and `super`.
        this.thisSuperArgsContext = new ThisSuperArgsContext(this);
      } else {
        // contextRoot is a parameter list.
        // Never alias `this`, `arguments`, or `super` for normal function parameter lists.
        // They are implicitly defined there.
        this.thisSuperArgsContext = null;
      }
    }

    static LexicalContext newGlobalContext(Node contextRoot) {
      return new LexicalContext(contextRoot);
    }

    static LexicalContext newContextForFunction(LexicalContext parent, Node function) {
      // Functions need their own context because:
      //     - async generator functions must be transpiled
      //     - non-async generator functions must NOT be transpiled
      //     - arrow functions inside of async generator functions need to have
      //       `this`, `arguments`, and `super` references aliased, including in their
      //       parameter lists
      return new LexicalContext(parent, function, function);
    }

    static LexicalContext newContextForParamList(LexicalContext parent, Node paramList) {
      // Parameter lists need their own context because `this`, `arguments`, and `super` must NOT be
      // aliased for non-arrow function parameter lists, even for async generator functions.
      return new LexicalContext(parent, paramList, parent.function);
    }

    Node getFunctionDeclaringThisArgsSuper() {
      return thisSuperArgsContext.ctx.function;
    }

    /** Is it necessary to replace `this`, `super`, and `arguments` with aliases in this context? */
    boolean mustReplaceThisSuperArgs() {
      return thisSuperArgsContext != null
          && getFunctionDeclaringThisArgsSuper().isAsyncGeneratorFunction();
    }
  }

  /**
   * Tracks how this/arguments/super were used in the function so declarations of replacement
   * variables can be prepended
   */
  private static final class ThisSuperArgsContext {

    /** The LexicalContext representing the function that declared this/super/args */
    private final LexicalContext ctx;

    private final Set<String> usedSuperProperties = new LinkedHashSet<>();
    private boolean usedThis = false;
    private boolean usedArguments = false;

    ThisSuperArgsContext(LexicalContext ctx) {
      this.ctx = ctx;
    }
  }

  private RewriteAsyncIteration(Builder builder) {
    this.compiler = builder.compiler;
    this.contextStack = new ArrayDeque<>();
    this.rewriteSuperPropertyReferencesWithoutSuper =
        builder.rewriteSuperPropertyReferencesWithoutSuper;
    this.registry = builder.registry;
    this.astFactory = builder.astFactory;
    this.unknownType = createType(() -> registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
  }

  private <T extends JSType> T createType(Supplier<T> fn) {
    if (astFactory.isAddingTypes()) {
      return fn.get();
    }
    return null;
  }

  private JSType createGenericType(JSTypeNative typeName, JSType typeArg) {
    return Es6ToEs3Util.createGenericType(astFactory.isAddingTypes(), registry, typeName, typeArg);
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

    RewriteAsyncIteration build() {
      astFactory = compiler.createAstFactory();
      registry = compiler.getTypeRegistry();
      return new RewriteAsyncIteration(this);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(contextStack.isEmpty());
    contextStack.push(LexicalContext.newGlobalContext(root));
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
    checkState(contextStack.element().function == null);
    contextStack.remove();
    checkState(contextStack.isEmpty());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFunction()) {
      contextStack.push(LexicalContext.newContextForFunction(contextStack.element(), n));
    } else if (n.isParamList()) {
      contextStack.push(LexicalContext.newContextForParamList(contextStack.element(), n));
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    LexicalContext ctx = contextStack.element();
    switch (n.getToken()) {
        // Async Generators (and popping contexts)
      case PARAM_LIST:
        // Done handling parameter list, so pop its context
        checkState(n.equals(ctx.contextRoot), n);
        contextStack.pop();
        break;
      case FUNCTION:
        checkState(n.equals(ctx.contextRoot));
        if (n.isAsyncGeneratorFunction()) {
          convertAsyncGenerator(t, n);
          prependTempVarDeclarations(ctx, t);
        }
        // Done handling function, so pop its context
        contextStack.pop();
        break;
      case AWAIT:
        checkNotNull(ctx.function);
        if (ctx.function.isAsyncGeneratorFunction()) {
          convertAwaitOfAsyncGenerator(t, ctx, n);
        }
        break;
      case YIELD: // Includes yield*
        checkNotNull(ctx.function);
        if (ctx.function.isAsyncGeneratorFunction()) {
          convertYieldOfAsyncGenerator(t, ctx, n);
        }
        break;

        // For-Await-Of loops
      case FOR_AWAIT_OF:
        checkNotNull(ctx.function);
        checkState(ctx.function.isAsyncFunction());
        replaceForAwaitOf(t, ctx, n);
        NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
        break;

        // Maintaining references to this/arguments/super
      case THIS:
        if (ctx.mustReplaceThisSuperArgs()) {
          replaceThis(t, ctx, n);
        }
        break;
      case NAME:
        if (ctx.mustReplaceThisSuperArgs() && n.matchesName("arguments")) {
          replaceArguments(t, ctx, n);
        }
        break;
      case SUPER:
        if (ctx.mustReplaceThisSuperArgs()) {
          replaceSuper(t, ctx, n, parent);
        }
        break;

      default:
        break;
    }
  }

  /**
   * Moves the body of an async generator function into a nested generator function and removes the
   * async and generator props from the original function.
   *
   * <pre>{@code
   * async function* foo() {
   *   bar();
   * }
   * }</pre>
   *
   * <p>becomes
   *
   * <pre>{@code
   * function foo() {
   *   return new $jscomp.AsyncGeneratorWrapper((function*(){
   *     bar();
   *   })())
   * }
   * }</pre>
   *
   * @param originalFunction the original AsyncGeneratorFunction Node to be converted.
   */
  private void convertAsyncGenerator(NodeTraversal t, Node originalFunction) {
    checkNotNull(originalFunction);
    checkState(originalFunction.isAsyncGeneratorFunction());

    Node asyncGeneratorWrapperRef =
        astFactory.createAsyncGeneratorWrapperReference(originalFunction.getJSType(), t.getScope());
    Node innerFunction =
        astFactory.createEmptyAsyncGeneratorWrapperArgument(asyncGeneratorWrapperRef.getJSType());

    Node innerBlock = originalFunction.getLastChild();
    innerBlock.detach();
    innerFunction.getLastChild().replaceWith(innerBlock);

    // Body should be:
    // return new $jscomp.AsyncGeneratorWrapper((new function with original block here)());
    Node outerBlock =
        astFactory.createBlock(
            astFactory.createReturn(
                astFactory.createNewNode(
                    asyncGeneratorWrapperRef, astFactory.createCall(innerFunction))));
    originalFunction.addChildToBack(outerBlock);

    originalFunction.setIsAsyncFunction(false);
    originalFunction.setIsGeneratorFunction(false);
    originalFunction.srcrefTreeIfMissing(originalFunction);
    // Both the inner and original functions should be marked as changed.
    compiler.reportChangeToChangeScope(originalFunction);
    compiler.reportChangeToChangeScope(innerFunction);
  }

  /**
   * Converts an await into a yield of an ActionRecord to perform "AWAIT".
   *
   * <pre>{@code await myPromise}</pre>
   *
   * <p>becomes
   *
   * <pre>{@code yield new ActionRecord(ActionEnum.AWAIT_VALUE, myPromise)}</pre>
   *
   * @param awaitNode the original await Node to be converted
   */
  private void convertAwaitOfAsyncGenerator(NodeTraversal t, LexicalContext ctx, Node awaitNode) {
    checkNotNull(awaitNode);
    checkState(awaitNode.isAwait());
    checkState(ctx != null && ctx.function != null);
    checkState(ctx.function.isAsyncGeneratorFunction());

    Node expression = awaitNode.removeFirstChild();
    checkNotNull(expression, "await needs an expression");
    Node newActionRecord =
        astFactory.createNewNode(
            astFactory.createQName(t.getScope(), ACTION_RECORD_NAME),
            astFactory.createQName(t.getScope(), ACTION_ENUM_AWAIT),
            expression);
    newActionRecord.srcrefTreeIfMissing(awaitNode);
    awaitNode.addChildToFront(newActionRecord);
    awaitNode.setToken(Token.YIELD);
  }

  /**
   * Converts a yield into a yield of an ActionRecord to perform "YIELD" or "YIELD_STAR".
   *
   * <pre>{@code
   * yield;
   * yield first;
   * yield* second;
   * }</pre>
   *
   * <p>becomes
   *
   * <pre>{@code
   * yield new ActionRecord(ActionEnum.YIELD_VALUE, undefined);
   * yield new ActionRecord(ActionEnum.YIELD_VALUE, first);
   * yield new ActionRecord(ActionEnum.YIELD_STAR, second);
   * }</pre>
   *
   * @param yieldNode the Node to be converted
   */
  private void convertYieldOfAsyncGenerator(NodeTraversal t, LexicalContext ctx, Node yieldNode) {
    checkNotNull(yieldNode);
    checkState(yieldNode.isYield());
    checkState(ctx != null && ctx.function != null);
    checkState(ctx.function.isAsyncGeneratorFunction());

    Node expression = yieldNode.removeFirstChild();
    Node newActionRecord =
        astFactory.createNewNode(astFactory.createQName(t.getScope(), ACTION_RECORD_NAME));

    if (yieldNode.isYieldAll()) {
      checkNotNull(expression);
      // yield* expression becomes new ActionRecord(YIELD_STAR, expression)
      newActionRecord.addChildToBack(astFactory.createQName(t.getScope(), ACTION_ENUM_YIELD_STAR));
      newActionRecord.addChildToBack(expression);
    } else {
      if (expression == null) {
        expression = NodeUtil.newUndefinedNode(null);
      }
      // yield expression becomes new ActionRecord(YIELD, expression)
      newActionRecord.addChildToBack(astFactory.createQName(t.getScope(), ACTION_ENUM_YIELD));
      newActionRecord.addChildToBack(expression);
    }

    newActionRecord.srcrefTreeIfMissing(yieldNode);
    yieldNode.addChildToFront(newActionRecord);
    yieldNode.putBooleanProp(Node.YIELD_ALL, false);
  }

  /**
   * for await (lhs of rhs) { block(); }
   *
   * <p>...becomes...
   *
   * <pre>{@code
   * for (const tmpIterator = makeAsyncIterator(rhs);;) {
   *    const tmpRes = await tmpIterator.next();
   *    if (tmpRes.done) {
   *      break;
   *    }
   *    lhs = $tmpRes.value;
   *    {
   *      block(); // Wrapped in a block in case block re-declares lhs variable.
   *    }
   * }
   * }</pre>
   *
   * @param forAwaitOf
   */
  private void replaceForAwaitOf(NodeTraversal t, LexicalContext ctx, Node forAwaitOf) {
    int forAwaitId = nextForAwaitId++;
    String iteratorTempName = FOR_AWAIT_ITERATOR_TEMP_NAME + forAwaitId;
    String resultTempName = FOR_AWAIT_RESULT_TEMP_NAME + forAwaitId;

    checkState(forAwaitOf.hasParent(), "Cannot replace parentless for-await-of");

    Node lhs = forAwaitOf.removeFirstChild();
    Node rhs = forAwaitOf.removeFirstChild();
    Node originalBody = forAwaitOf.removeFirstChild();

    JSType typeParam =
        createType(
            () ->
                JsIterables.maybeBoxIterableOrAsyncIterable(rhs.getJSType(), registry)
                    .orElse(unknownType));

    Node initializer =
        astFactory
            .createSingleConstNameDeclaration(
                iteratorTempName, astFactory.createJSCompMakeAsyncIteratorCall(rhs, t.getScope()))
            .srcrefTreeIfMissing(rhs);

    // IIterableResult<VALUE>
    JSType iterableResultType = createGenericType(JSTypeNative.I_ITERABLE_RESULT_TYPE, typeParam);

    // const tmpRes = await tmpIterator.next()
    Node resultDeclaration =
        astFactory.createSingleConstNameDeclaration(
            resultTempName,
            constructAwaitNextResult(
                t,
                ctx,
                iteratorTempName,
                initializer.getFirstChild().getJSType(),
                iterableResultType));

    Node breakIfDone =
        astFactory.createIf(
            astFactory.createGetProp(
                astFactory.createName(resultTempName, iterableResultType), "done"),
            astFactory.createBlock(astFactory.createBreak()));

    // Assignment statement to be moved from lhs into body of new for-loop
    Node lhsAssignment;
    if (lhs.isValidAssignmentTarget()) {
      // In case of "for await (x of _)" just assign into the lhs.
      lhsAssignment =
          astFactory.exprResult(
              astFactory.createAssign(
                  lhs,
                  astFactory.createGetProp(
                      astFactory.createName(resultTempName, iterableResultType), "value")));
    } else if (NodeUtil.isNameDeclaration(lhs)) {
      // In case of "for await (let x of _)" add a rhs to the let, becoming "let x = res.value"
      lhs.getFirstChild()
          .addChildToBack(
              astFactory.createGetProp(
                  astFactory.createName(resultTempName, iterableResultType), "value"));
      lhsAssignment = lhs;
    } else {
      throw new AssertionError("unexpected for-await-of lhs");
    }
    lhsAssignment.srcrefTreeIfMissing(lhs);

    Node newForLoop =
        astFactory.createFor(
            initializer,
            astFactory.createEmpty(),
            astFactory.createEmpty(),
            astFactory.createBlock(
                resultDeclaration, breakIfDone, lhsAssignment, ensureBlock(originalBody)));
    forAwaitOf.replaceWith(newForLoop);
    newForLoop.srcrefTreeIfMissing(forAwaitOf);
    compiler.reportChangeToEnclosingScope(newForLoop);
  }

  private Node ensureBlock(Node possiblyBlock) {
    return possiblyBlock.isBlock()
        ? possiblyBlock
        : astFactory.createBlock(possiblyBlock).srcref(possiblyBlock);
  }

  private Node constructAwaitNextResult(
      NodeTraversal t,
      LexicalContext ctx,
      String iteratorTempName,
      JSType iteratorType,
      JSType iterableResultType) {
    checkNotNull(ctx.function);
    Node result;

    Node iteratorTemp = astFactory.createName(iteratorTempName, iteratorType);

    if (ctx.function.isAsyncGeneratorFunction()) {
      // We are in an AsyncGenerator and must instead yield an "await" ActionRecord
      result =
          astFactory.createYield(
              iterableResultType,
              astFactory.createNewNode(
                  astFactory.createQName(t.getScope(), ACTION_RECORD_NAME),
                  astFactory.createQName(t.getScope(), ACTION_ENUM_AWAIT),
                  astFactory.createCall(astFactory.createGetProp(iteratorTemp, "next"))));
    } else {
      result =
          astFactory.createAwait(
              iterableResultType,
              astFactory.createCall(astFactory.createGetProp(iteratorTemp, "next")));
    }

    return result;
  }

  private void replaceThis(NodeTraversal t, LexicalContext ctx, Node n) {
    checkArgument(n.isThis());
    checkArgument(ctx != null && ctx.mustReplaceThisSuperArgs());
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    n.replaceWith(astFactory.createName(t.getScope(), thisVarName).srcref(n));
    ctx.thisSuperArgsContext.usedThis = true;
    compiler.reportChangeToChangeScope(ctx.function);
  }

  private void replaceArguments(NodeTraversal t, LexicalContext ctx, Node n) {
    checkArgument(n.isName() && "arguments".equals(n.getString()));
    checkArgument(ctx != null && ctx.mustReplaceThisSuperArgs());
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    n.replaceWith(astFactory.createName(t.getScope(), argumentsVarName).srcref(n));
    ctx.thisSuperArgsContext.usedArguments = true;
    compiler.reportChangeToChangeScope(ctx.function);
  }

  private void replaceSuper(NodeTraversal t, LexicalContext ctx, Node n, Node parent) {
    if (!parent.isGetProp()) {
      compiler.report(
          JSError.make(
              parent,
              CANNOT_CONVERT_ASYNCGEN,
              "super only allowed with getprop (like super.foo(), not super['foo']())"));
      return;
    }
    checkArgument(n.isSuper());
    checkArgument(ctx != null && ctx.mustReplaceThisSuperArgs());
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    String propertyName = parent.getString();
    String propertyReplacementNameText = superPropGetterPrefix + propertyName;

    // super.x   =>   $super$get$x()
    Node getPropReplacement =
        astFactory.createCall(astFactory.createName(t.getScope(), propertyReplacementNameText));
    Node grandparent = parent.getParent();
    if (grandparent.isCall() && grandparent.getFirstChild() == parent) {
      // super.x(...)   =>   super.x.call($this, ...)
      getPropReplacement = astFactory.createGetProp(getPropReplacement, "call");
      astFactory.createName(t.getScope(), thisVarName).srcref(parent).insertAfter(parent);
      ctx.thisSuperArgsContext.usedThis = true;
    }
    getPropReplacement.srcrefTree(parent);
    parent.replaceWith(getPropReplacement);
    ctx.thisSuperArgsContext.usedSuperProperties.add(propertyName);
    compiler.reportChangeToChangeScope(ctx.function);
  }

  /**
   * Prepends this/super/argument replacement variables to the top of the context's block
   *
   * <pre>{@code
   * function() {
   *   return new AsyncGenWrapper(function*() {
   *     // code using replacements for this and super.foo
   *   }())
   * }
   * }</pre>
   *
   * will be converted to
   *
   * <pre>{@code
   * function() {
   *   const $jscomp$asyncIter$this = this;
   *   const $jscomp$asyncIter$super$get$foo = () => super.foo;
   *   return new AsyncGenWrapper(function*() {
   *     // code using replacements for this and super.foo
   *   }())
   * }
   * }</pre>
   */
  private void prependTempVarDeclarations(LexicalContext ctx, NodeTraversal t) {
    checkArgument(ctx != null);
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    ThisSuperArgsContext thisSuperArgsCtx = ctx.thisSuperArgsContext;
    Node function = ctx.function;
    Node block = function.getLastChild();
    checkNotNull(block, function);
    Node prefixBlock = astFactory.createBlock(); // Temporary block to hold all declarations

    if (thisSuperArgsCtx.usedThis) {
      // { // prefixBlock
      //   const $jscomp$asyncIter$this = this;
      // }
      prefixBlock.addChildToBack(
          astFactory
              .createThisAliasDeclarationForFunction(thisVarName, function)
              .srcrefTree(block));
    }
    if (thisSuperArgsCtx.usedArguments) {
      // { // prefixBlock
      //   const $jscomp$asyncIter$this = this;
      //   const $jscomp$asyncIter$arguments = arguments;
      // }
      prefixBlock.addChildToBack(
          astFactory
              .createSingleConstNameDeclaration(
                  argumentsVarName, astFactory.createName(t.getScope(), "arguments"))
              .srcrefTree(block));
    }
    for (String replacedMethodName : thisSuperArgsCtx.usedSuperProperties) {
      // const super$get$x = () => super.x;
      // OR avoid super for static method (class object -> superclass object)
      // const super$get$x = () => Object.getPrototypeOf(this).x
      // OR avoid super for instance method (instance -> prototype -> super prototype)
      // const super$get$x = () => Object.getPrototypeOf(Object.getPrototypeOf(this)).x
      Node superReference;
      if (rewriteSuperPropertyReferencesWithoutSuper) {
        // Rewrite to avoid using `super` within an arrow function.
        // See more information on definition of this option.
        // TODO(bradfordcsmith): RewriteAsyncIteration and RewriteAsyncFunctions have the
        // same logic for dealing with super references. Consider having them share
        // it from a common place instead of duplicating.

        // static super: Object.getPrototypeOf(this);
        superReference =
            astFactory.createObjectGetPrototypeOfCall(astFactory.createThisForFunction(function));
        if (!ctx.function.getParent().isStaticMember()) {
          // instance super: Object.getPrototypeOf(Object.getPrototypeOf(this))
          superReference = astFactory.createObjectGetPrototypeOfCall(superReference);
        }
      } else {
        superReference = astFactory.createSuperForFunction(function);
      }

      Node arrowFunction =
          astFactory.createZeroArgArrowFunctionForExpression(
              astFactory.createGetProp(superReference, replacedMethodName));
      compiler.reportChangeToChangeScope(arrowFunction);
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.ARROW_FUNCTIONS, compiler);
      String superReplacementName = superPropGetterPrefix + replacedMethodName;
      prefixBlock.addChildToBack(
          astFactory.createSingleConstNameDeclaration(superReplacementName, arrowFunction));
    }
    prefixBlock.srcrefTreeIfMissing(block);
    // Pulls all declarations out of prefixBlock and prepends in block
    // block: {
    //   // declarations
    //   // code using this/super/args
    // }
    block.addChildrenToFront(prefixBlock.removeChildren());

    if (thisSuperArgsCtx.usedThis
        || thisSuperArgsCtx.usedArguments
        || !thisSuperArgsCtx.usedSuperProperties.isEmpty()) {
      compiler.reportChangeToChangeScope(function);
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.CONST_DECLARATIONS, compiler);
    }
  }
}
