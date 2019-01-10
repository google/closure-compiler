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
import static com.google.javascript.rhino.IR.getprop;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
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
public final class RewriteAsyncIteration implements NodeTraversal.Callback, HotSwapCompilerPass {

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ASYNC_GENERATORS, Feature.FOR_AWAIT_OF);

  static final DiagnosticType CANNOT_CONVERT_ASYNCGEN =
      DiagnosticType.error("JSC_CANNOT_CONVERT_ASYNCGEN", "Cannot convert async generator. {0}");

  private static final String GENERATOR_WRAPPER_NAME = "$jscomp.AsyncGeneratorWrapper";
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

    // The current function, or null if root scope where we are not in a function.
    private final Node function;
    // The context of the most recent definition of this/super/arguments
    private final ThisSuperArgsContext thisSuperArgsContext;

    // Represents the global/root scope. Should only exist on the bottom of the contextStack.
    private LexicalContext() {
      this.function = null;
      this.thisSuperArgsContext = null;
    }

    private LexicalContext(LexicalContext parent, Node function) {
      checkNotNull(parent);
      checkNotNull(function);
      checkArgument(function.isFunction());
      this.function = function;

      if (function.isArrowFunction()) {
        // Use the parent context to inherit this, arguments, and super.
        this.thisSuperArgsContext = parent.thisSuperArgsContext;
      } else {
        // Non-arrow gets its own context defining this, arguments, and super.
        this.thisSuperArgsContext = new ThisSuperArgsContext(this);
      }
    }

    static LexicalContext newGlobalContext() {
      return new LexicalContext();
    }

    static LexicalContext newContextForFunction(LexicalContext parent, Node function) {
      return new LexicalContext(parent, function);
    }

    Node getFunctionDeclaringThisArgsSuper() {
      return thisSuperArgsContext.ctx.function;
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
  }

  static class Builder {
    private final AbstractCompiler compiler;
    private boolean rewriteSuperPropertyReferencesWithoutSuper = false;

    Builder(AbstractCompiler compiler) {
      checkNotNull(compiler);
      this.compiler = compiler;
    }

    Builder rewriteSuperPropertyReferencesWithoutSuper(boolean value) {
      rewriteSuperPropertyReferencesWithoutSuper = value;
      return this;
    }

    RewriteAsyncIteration build() {
      return new RewriteAsyncIteration(this);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    process(scriptRoot);
  }

  @Override
  public void process(Node externs, Node root) {
    process(root);
  }

  /**
   * Helper function for both HotSwapCompilerPass#hotSwapScript and CompilerPass#process.
   *
   * @param root Root of AST to rewrite
   */
  private void process(Node root) {
    checkState(contextStack.isEmpty());
    contextStack.push(LexicalContext.newGlobalContext());
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
    checkState(contextStack.element().function == null);
    contextStack.remove();
    checkState(contextStack.isEmpty());
  }

  private boolean isInContextOfAsyncGenerator(LexicalContext ctx) {
    return ctx.thisSuperArgsContext != null
        && ctx.getFunctionDeclaringThisArgsSuper().isAsyncGeneratorFunction();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFunction()) {
      contextStack.push(LexicalContext.newContextForFunction(contextStack.element(), n));
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    LexicalContext ctx = contextStack.element();
    switch (n.getToken()) {
        // Async Generators (and popping contexts)
      case FUNCTION:
        checkState(n.equals(ctx.function));
        if (n.isAsyncGeneratorFunction()) {
          convertAsyncGenerator(n);
          prependTempVarDeclarations(ctx, t);
        }
        contextStack.pop();
        break;
      case AWAIT:
        checkNotNull(ctx.function);
        if (ctx.function.isAsyncGeneratorFunction()) {
          convertAwaitOfAsyncGenerator(ctx, n);
        }
        break;
      case YIELD: // Includes yield*
        checkNotNull(ctx.function);
        if (ctx.function.isAsyncGeneratorFunction()) {
          convertYieldOfAsyncGenerator(ctx, n);
        }
        break;

        // For-Await-Of loops
      case FOR_AWAIT_OF:
        checkNotNull(ctx.function);
        checkState(ctx.function.isAsyncFunction());
        replaceForAwaitOf(ctx, n);
        NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
        break;

        // Maintaining references to this/arguments/super
      case THIS:
        if (isInContextOfAsyncGenerator(ctx)) {
          replaceThis(ctx, n);
        }
        break;
      case NAME:
        if (isInContextOfAsyncGenerator(ctx) && n.matchesQualifiedName("arguments")) {
          replaceArguments(ctx, n);
        }
        break;
      case SUPER:
        if (isInContextOfAsyncGenerator(ctx)) {
          replaceSuper(ctx, n, parent);
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
  private void convertAsyncGenerator(Node originalFunction) {
    checkNotNull(originalFunction);
    checkState(originalFunction.isAsyncGeneratorFunction());

    Node innerBlock = originalFunction.getLastChild();
    originalFunction.removeChild(innerBlock);
    Node innerFunction = IR.function(IR.name(""), IR.paramList(), innerBlock);
    innerFunction.setIsGeneratorFunction(true);

    // Body should be:
    // return new $jscomp.AsyncGeneratorWrapper((new function with original block here)());
    Node outerBlock =
        IR.block(
            IR.returnNode(
                IR.newNode(
                    NodeUtil.newQName(compiler, GENERATOR_WRAPPER_NAME),
                    NodeUtil.newCallNode(innerFunction))));
    originalFunction.addChildToBack(outerBlock);

    originalFunction.setIsAsyncFunction(false);
    originalFunction.setIsGeneratorFunction(false);
    originalFunction.useSourceInfoIfMissingFromForTree(originalFunction);
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
  private void convertAwaitOfAsyncGenerator(LexicalContext ctx, Node awaitNode) {
    checkNotNull(awaitNode);
    checkState(awaitNode.isAwait());
    checkState(ctx != null && ctx.function != null);
    checkState(ctx.function.isAsyncGeneratorFunction());

    Node expression = awaitNode.removeFirstChild();
    checkNotNull(expression, "await needs an expression");
    Node newActionRecord =
        IR.newNode(
            NodeUtil.newQName(compiler, ACTION_RECORD_NAME),
            NodeUtil.newQName(compiler, ACTION_ENUM_AWAIT),
            expression);
    newActionRecord.useSourceInfoIfMissingFromForTree(awaitNode);
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
  private void convertYieldOfAsyncGenerator(LexicalContext ctx, Node yieldNode) {
    checkNotNull(yieldNode);
    checkState(yieldNode.isYield());
    checkState(ctx != null && ctx.function != null);
    checkState(ctx.function.isAsyncGeneratorFunction());

    Node expression = yieldNode.removeFirstChild();
    Node newActionRecord = IR.newNode(NodeUtil.newQName(compiler, ACTION_RECORD_NAME));

    if (yieldNode.isYieldAll()) {
      checkNotNull(expression);
      // yield* expression becomes new ActionRecord(YIELD_STAR, expression)
      newActionRecord.addChildToBack(NodeUtil.newQName(compiler, ACTION_ENUM_YIELD_STAR));
      newActionRecord.addChildToBack(expression);
    } else {
      if (expression == null) {
        expression = NodeUtil.newUndefinedNode(null);
      }
      // yield expression becomes new ActionRecord(YIELD, expression)
      newActionRecord.addChildToBack(NodeUtil.newQName(compiler, ACTION_ENUM_YIELD));
      newActionRecord.addChildToBack(expression);
    }

    newActionRecord.useSourceInfoIfMissingFromForTree(yieldNode);
    yieldNode.addChildToFront(newActionRecord);
    yieldNode.removeProp(Node.YIELD_ALL);
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
  private void replaceForAwaitOf(LexicalContext ctx, Node forAwaitOf) {
    int forAwaitId = nextForAwaitId++;
    String iteratorTempName = FOR_AWAIT_ITERATOR_TEMP_NAME + forAwaitId;
    String resultTempName = FOR_AWAIT_RESULT_TEMP_NAME + forAwaitId;

    checkState(forAwaitOf.getParent() != null, "Cannot replace parentless for-await-of");

    Node lhs = forAwaitOf.removeFirstChild();
    Node rhs = forAwaitOf.removeFirstChild();
    Node originalBody = forAwaitOf.removeFirstChild();

    Node initializer =
        IR.constNode(
                IR.name(iteratorTempName),
                NodeUtil.newCallNode(NodeUtil.newQName(compiler, "$jscomp.makeAsyncIterator"), rhs))
            .useSourceInfoIfMissingFromForTree(rhs);

    // const tmpRes = await tmpIterator.next()
    Node resultDeclaration =
        IR.constNode(IR.name(resultTempName), constructAwaitNextResult(ctx, iteratorTempName));
    Node breakIfDone =
        IR.ifNode(getprop(IR.name(resultTempName), "done"), IR.block(IR.breakNode()));

    // Assignment statement to be moved from lhs into body of new for-loop
    Node lhsAssignment;
    if (lhs.isValidAssignmentTarget()) {
      // In case of "for await (x of _)" just assign into the lhs.
      lhsAssignment = IR.exprResult(IR.assign(lhs, getprop(IR.name(resultTempName), "value")));
    } else if (NodeUtil.isNameDeclaration(lhs)) {
      // In case of "for await (let x of _)" add a rhs to the let, becoming "let x = res.value"
      lhs.getFirstChild().addChildToBack(getprop(IR.name(resultTempName), "value"));
      lhsAssignment = lhs;
    } else {
      throw new AssertionError("unexpected for-await-of lhs");
    }
    lhsAssignment.useSourceInfoIfMissingFromForTree(lhs);

    Node newForLoop =
        IR.forNode(
            initializer,
            IR.empty(),
            IR.empty(),
            IR.block(resultDeclaration, breakIfDone, lhsAssignment, ensureBlock(originalBody)));
    forAwaitOf.replaceWith(newForLoop);
    newForLoop.useSourceInfoIfMissingFromForTree(forAwaitOf);
    compiler.reportChangeToEnclosingScope(newForLoop);
  }

  private Node ensureBlock(Node possiblyBlock) {
    return possiblyBlock.isBlock()
        ? possiblyBlock
        : IR.block(possiblyBlock).useSourceInfoFrom(possiblyBlock);
  }

  private Node constructAwaitNextResult(LexicalContext ctx, String iteratorTempName) {
    checkNotNull(ctx.function);
    if (!ctx.function.isAsyncGeneratorFunction()) {
      return IR.await(NodeUtil.newCallNode(getprop(IR.name(iteratorTempName), "next")));
    } else {
      // We are in an AsyncGenerator and must instead yield an "await" ActionRecord
      return IR.yield(
          IR.newNode(
              NodeUtil.newQName(compiler, ACTION_RECORD_NAME),
              NodeUtil.newQName(compiler, ACTION_ENUM_AWAIT),
              NodeUtil.newCallNode(getprop(IR.name(iteratorTempName), "next"))));
    }
  }

  private void replaceThis(LexicalContext ctx, Node n) {
    checkArgument(n.isThis());
    checkArgument(ctx != null && isInContextOfAsyncGenerator(ctx));
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    n.replaceWith(IR.name(thisVarName).useSourceInfoFrom(n));
    ctx.thisSuperArgsContext.usedThis = true;
    compiler.reportChangeToChangeScope(ctx.function);
  }

  private void replaceArguments(LexicalContext ctx, Node n) {
    checkArgument(n.isName() && "arguments".equals(n.getString()));
    checkArgument(ctx != null && isInContextOfAsyncGenerator(ctx));
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    n.replaceWith(IR.name(argumentsVarName).useSourceInfoFrom(n));
    ctx.thisSuperArgsContext.usedArguments = true;
    compiler.reportChangeToChangeScope(ctx.function);
  }

  private void replaceSuper(LexicalContext ctx, Node n, Node parent) {
    if (!parent.isGetProp()) {
      compiler.report(
          JSError.make(
              parent,
              CANNOT_CONVERT_ASYNCGEN,
              "super only allowed with getprop (like super.foo(), not super['foo']())"));
    }
    checkArgument(n.isSuper());
    checkArgument(ctx != null && isInContextOfAsyncGenerator(ctx));
    checkArgument(ctx.function != null, "Cannot prepend declarations to root scope");
    checkNotNull(ctx.thisSuperArgsContext);

    Node propertyName = n.getNext();
    String propertyReplacementNameText = superPropGetterPrefix + propertyName.getString();

    // super.x   =>   $super$get$x()
    Node getPropReplacement = NodeUtil.newCallNode(IR.name(propertyReplacementNameText));
    Node grandparent = parent.getParent();
    if (grandparent.isCall() && grandparent.getFirstChild() == parent) {
      // super.x(...)   =>   super.x.call($this, ...)
      getPropReplacement = IR.getprop(getPropReplacement, IR.string("call"));
      grandparent.addChildAfter(IR.name(thisVarName).useSourceInfoFrom(parent), parent);
      ctx.thisSuperArgsContext.usedThis = true;
    }
    getPropReplacement.useSourceInfoFromForTree(parent);
    grandparent.replaceChild(parent, getPropReplacement);
    ctx.thisSuperArgsContext.usedSuperProperties.add(propertyName.getString());
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
    Node prefixBlock = IR.block(); // Temporary block to hold all declarations

    if (thisSuperArgsCtx.usedThis) {
      // { // prefixBlock
      //   const $jscomp$asyncIter$this = this;
      // }
      prefixBlock.addChildToBack(
          IR.constNode(IR.name(thisVarName), IR.thisNode()).useSourceInfoFromForTree(block));
    }
    if (thisSuperArgsCtx.usedArguments) {
      // { // prefixBlock
      //   const $jscomp$asyncIter$this = this;
      //   const $jscomp$asyncIter$arguments = arguments;
      // }
      prefixBlock.addChildToBack(
          IR.constNode(IR.name(argumentsVarName), IR.name("arguments"))
              .useSourceInfoFromForTree(block));
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
            IR.call(IR.getprop(IR.name("Object"), IR.string("getPrototypeOf")), IR.thisNode());
        if (!ctx.function.getParent().isStaticMember()) {
          // instance super: Object.getPrototypeOf(Object.getPrototypeOf(this))
          superReference =
              IR.call(IR.getprop(IR.name("Object"), IR.string("getPrototypeOf")), superReference);
        }
      } else {
        superReference = IR.superNode();
      }

      Node arrowFunction =
          IR.arrowFunction(
              IR.name(""),
              IR.paramList(),
              IR.getprop(superReference, IR.string(replacedMethodName)));
      compiler.reportChangeToChangeScope(arrowFunction);
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.ARROW_FUNCTIONS);
      String superReplacementName = superPropGetterPrefix + replacedMethodName;
      prefixBlock.addChildToBack(IR.constNode(IR.name(superReplacementName), arrowFunction));
    }
    prefixBlock.useSourceInfoIfMissingFromForTree(block);
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
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }
  }
}
