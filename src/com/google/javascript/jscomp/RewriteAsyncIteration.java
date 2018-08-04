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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.IR.getprop;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;

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

  private final AbstractCompiler compiler;
  private final ArrayDeque<FunctionFlavor> functionFlavorStack;

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

  /** Indicates the type of function currently being parsed. */
  private enum FunctionFlavor {
    NORMAL(false, false),
    GENERATOR(true, false),
    ASYNCHRONOUS(false, true),
    ASYNCHRONOUS_GENERATOR(true, true);

    final boolean isGenerator;
    final boolean isAsynchronous;

    FunctionFlavor(boolean isGenerator, boolean isAsynchronous) {
      this.isGenerator = isGenerator;
      this.isAsynchronous = isAsynchronous;
    }

    static FunctionFlavor fromFunctionNode(Node functionNode) {
      checkState(functionNode.isFunction(), functionNode);
      if (functionNode.isAsyncGeneratorFunction()) {
        return ASYNCHRONOUS_GENERATOR;
      } else if (functionNode.isAsyncFunction()) {
        return ASYNCHRONOUS;
      } else if (functionNode.isGeneratorFunction()) {
        return GENERATOR;
      } else {
        return NORMAL;
      }
    }
  }

  RewriteAsyncIteration(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.functionFlavorStack = new ArrayDeque<>();
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    checkState(functionFlavorStack.isEmpty());
    TranspilationPasses.processTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
    checkState(functionFlavorStack.isEmpty());
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(functionFlavorStack.isEmpty());
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
    checkState(functionFlavorStack.isEmpty());
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFunction()) {
      functionFlavorStack.push(FunctionFlavor.fromFunctionNode(n));
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (functionFlavorStack.peek() == FunctionFlavor.ASYNCHRONOUS_GENERATOR) {
      if (n.isAsyncGeneratorFunction()) {
        convertAsyncGenerator(n);
      } else if (n.isAwait()) {
        convertAwaitOfAsyncGenerator(n);
      } else if (n.isYield()) { // isYield includes yield*
        convertYieldOfAsyncGenerator(n);
      }
    }

    if (n.isForAwaitOf()) {
      replaceForAwaitOf(n);
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }

    if (n.isFunction()) {
      functionFlavorStack.pop();
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

    // TODO(mattmm): Redirect this, super, arguments to appropriate context
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
  private void convertAwaitOfAsyncGenerator(Node awaitNode) {
    checkNotNull(awaitNode);
    checkState(awaitNode.isAwait());
    checkState(functionFlavorStack.peek() == FunctionFlavor.ASYNCHRONOUS_GENERATOR);

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
   * @param yieldNode
   */
  private void convertYieldOfAsyncGenerator(Node yieldNode) {
    checkNotNull(yieldNode);
    checkState(yieldNode.isYield());
    checkState(functionFlavorStack.peek() == FunctionFlavor.ASYNCHRONOUS_GENERATOR);

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
  private void replaceForAwaitOf(Node forAwaitOf) {
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
        IR.constNode(IR.name(resultTempName), constructAwaitNextResult(iteratorTempName));
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

  private Node constructAwaitNextResult(String iteratorTempName) {
    if (functionFlavorStack.peek() != FunctionFlavor.ASYNCHRONOUS_GENERATOR) {
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
}
