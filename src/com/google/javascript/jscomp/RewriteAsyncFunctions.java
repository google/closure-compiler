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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Deque;

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
 *   let $jscomp$async$arguments = arguments;
 *   let $jscomp$async$this = this;
 *   function* $jscomp$async$generator() {
 *     // original body of foo() with:
 *     // - await (x) replaced with yield (x)
 *     // - arguments replaced with $jscomp$async$arguments
 *     // - this replaced with $jscomp$async$this
 *   }
 *   return $jscomp.executeAsyncGenerator($jscomp$async$generator());
 * }}</pre>
 */
public final class RewriteAsyncFunctions implements NodeTraversal.Callback, HotSwapCompilerPass {

  private static final String ASYNC_GENERATOR_NAME = "$jscomp$async$generator";
  private static final String ASYNC_ARGUMENTS = "$jscomp$async$arguments";
  private static final String ASYNC_THIS = "$jscomp$async$this";

  /**
   * Keeps track of whether we're examining nodes within an async function & what changes are needed
   * for the function currently in context.
   */
  private static final class LexicalContext {
    final Optional<Node> function; // absent for top level
    final LexicalContext thisAndArgumentsContext;
    final Optional<LexicalContext> enclosingAsyncContext;
    boolean asyncThisReplacementWasDone = false;
    boolean asyncArgumentsReplacementWasDone = false;

    /** Creates root-level context. */
    LexicalContext() {
      this.function = Optional.absent();
      this.thisAndArgumentsContext = this;
      this.enclosingAsyncContext = Optional.absent();
    }

    LexicalContext(LexicalContext outer, Node function) {
      this.function = Optional.of(function);
      // An arrow function shares 'this' and 'arguments' with its outer scope.
      this.thisAndArgumentsContext =
          function.isArrowFunction() ? outer.thisAndArgumentsContext : this;
      this.enclosingAsyncContext =
          function.isAsyncFunction() ? Optional.of(this) : outer.enclosingAsyncContext;
    }

    boolean isAsyncFunction() {
      return function.isPresent() && function.get().isAsyncFunction();
    }

    boolean mustReplaceThisAndArguments() {
      return enclosingAsyncContext.isPresent()
          && enclosingAsyncContext.get().thisAndArgumentsContext == thisAndArgumentsContext;
    }

    void recordAsyncThisReplacementWasDone() {
      enclosingAsyncContext.get().asyncThisReplacementWasDone = true;
    }

    void recordAsyncArgumentsReplacementWasDone() {
      enclosingAsyncContext.get().asyncArgumentsReplacementWasDone = true;
    }
  }

  private final Deque<LexicalContext> contextStack;
  private final AbstractCompiler compiler;

  public RewriteAsyncFunctions(AbstractCompiler compiler) {
    Preconditions.checkNotNull(compiler);
    this.compiler = compiler;
    this.contextStack = new ArrayDeque<>();
    this.contextStack.addFirst(new LexicalContext());
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isFunction()) {
      contextStack.addFirst(new LexicalContext(contextStack.getFirst(), n));
      if (n.isAsyncFunction()) {
        compiler.ensureLibraryInjected("es6/execute_async_generator", /* force */ false);
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    LexicalContext context = contextStack.getFirst();

    if (n.getToken() == Token.FUNCTION) {
      checkState(
          context.function.isPresent() && context.function.get() == n,
          "unexpected function context:\nexpected: %s\nactual: %s",
          n,
          context.function);
      contextStack.removeFirst();
    }
    switch (n.getToken()) {
      case FUNCTION:
        if (context.isAsyncFunction()) {
          convertAsyncFunction(context);
        }
        break;

      case NAME:
        if (context.mustReplaceThisAndArguments() && n.matchesQualifiedName("arguments")) {
          n.setString(ASYNC_ARGUMENTS);
          context.recordAsyncArgumentsReplacementWasDone();
        }
        break;

      case THIS:
        if (context.mustReplaceThisAndArguments()) {
          parent.replaceChild(n, IR.name(ASYNC_THIS).useSourceInfoIfMissingFrom(n));
          context.recordAsyncThisReplacementWasDone();
        }
        break;

      case AWAIT:
        checkState(context.isAsyncFunction(), "await found within non-async function body");
        checkState(n.hasOneChild(), "await should have 1 operand, but has %s", n.getChildCount());
        // Awaits become yields in the converted async function's inner generator function.
        parent.replaceChild(n, IR.yield(n.removeFirstChild()).useSourceInfoIfMissingFrom(n));
        break;

      default:
        break;
    }
  }

  private void convertAsyncFunction(LexicalContext functionContext) {
    Node originalFunction = functionContext.function.get();
    originalFunction.setIsAsyncFunction(false);
    Node originalBody = originalFunction.getLastChild();
    Node newBody = IR.block().useSourceInfoIfMissingFrom(originalBody);
    originalFunction.replaceChild(originalBody, newBody);

    if (functionContext.asyncThisReplacementWasDone) {
      newBody.addChildToBack(IR.let(IR.name(ASYNC_THIS), IR.thisNode()));
    }
    if (functionContext.asyncArgumentsReplacementWasDone) {
      newBody.addChildToBack(IR.let(IR.name(ASYNC_ARGUMENTS), IR.name("arguments")));
    }

    // Normalize arrow function short body to block body
    if (!originalBody.isBlock()) {
      originalBody = IR.block(IR.returnNode(originalBody)).useSourceInfoFromForTree(originalBody);
    }
    // NOTE: visit() will already have made appropriate replacements in originalBody so it may
    // be used as the generator function body.
    Node generatorFunction =
        IR.function(IR.name(ASYNC_GENERATOR_NAME), IR.paramList(), originalBody);
    generatorFunction.setIsGeneratorFunction(true);
    // function* $jscomp$async$generator() { ... }
    newBody.addChildToBack(generatorFunction);

    // return $jscomp.executeAsyncGenerator($jscomp$async$generator());
    Node executeAsyncGenerator = IR.getprop(IR.name("$jscomp"), IR.string("executeAsyncGenerator"));
    newBody.addChildToBack(
        IR.returnNode(
            IR.call(executeAsyncGenerator, NodeUtil.newCallNode(IR.name(ASYNC_GENERATOR_NAME)))));

    newBody.useSourceInfoIfMissingFromForTree(originalBody);
    compiler.reportCodeChange();
  }
}
