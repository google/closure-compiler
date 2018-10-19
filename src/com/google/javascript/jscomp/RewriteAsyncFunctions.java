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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
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
   * Keeps track of whether we're examining nodes within an async function & what changes are needed
   * for the function currently in context.
   */
  private static final class LexicalContext {
    @Nullable final Node function;
    @Nullable final LexicalContext asyncThisAndArgumentsContext;
    final Set<String> replacedSuperProperties = new LinkedHashSet<>();
    boolean mustAddAsyncThisVariable = false;
    boolean mustAddAsyncArgumentsVariable = false;

    LexicalContext() {
      function = null;
      asyncThisAndArgumentsContext = null;
    }

    LexicalContext(LexicalContext outer, Node function) {
      this.function = function;

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

    void recordAsyncSuperReplacementWasDone(String superFunctionName) {
      asyncThisAndArgumentsContext.replacedSuperProperties.add(superFunctionName);
    }

    void recordAsyncArgumentsReplacementWasDone() {
      asyncThisAndArgumentsContext.mustAddAsyncArgumentsVariable = true;
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

  private RewriteAsyncFunctions(Builder builder) {
    checkNotNull(builder);
    this.compiler = builder.compiler;
    this.contextStack = new ArrayDeque<>();
    this.contextStack.addFirst(new LexicalContext());
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

    RewriteAsyncFunctions build() {
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
          parent.replaceChild(n, IR.name(ASYNC_THIS));
          context.recordAsyncThisReplacementWasDone();
          compiler.reportChangeToChangeScope(context.function);
        }
        break;

      case SUPER:
        if (context.mustReplaceThisAndArguments()) {
          if (!parent.isGetProp()) {
            compiler.report(
                JSError.make(parent, Es6ToEs3Util.CANNOT_CONVERT_YET, "super expression"));
          }

          Node medhodName = n.getNext();
          String superPropertyName = ASYNC_SUPER_PROP_GETTER_PREFIX + medhodName.getString();

          // super.x   =>   $super$get$x()
          Node getPropReplacement = NodeUtil.newCallNode(IR.name(superPropertyName));
          Node grandparent = parent.getParent();
          if (grandparent.isCall() && grandparent.getFirstChild() == parent) {
            // super.x(...)   =>   super.x.call($this, ...)
            getPropReplacement = IR.getprop(getPropReplacement, IR.string("call"));
            grandparent.addChildAfter(IR.name(ASYNC_THIS).useSourceInfoFrom(parent), parent);
            context.recordAsyncThisReplacementWasDone();
          }
          getPropReplacement.useSourceInfoFromForTree(parent);
          grandparent.replaceChild(parent, getPropReplacement);
          context.recordAsyncSuperReplacementWasDone(medhodName.getString());
          compiler.reportChangeToChangeScope(context.function);
        }
        break;

      case AWAIT:
        checkState(context.isAsyncContext(), "await found within non-async function body");
        checkState(n.hasOneChild(), "await should have 1 operand, but has %s", n.getChildCount());
        // Awaits become yields in the converted async function's inner generator function.
        parent.replaceChild(n, IR.yield(n.removeFirstChild()));
        break;

      default:
        break;
    }
  }

  private void convertAsyncFunction(NodeTraversal t, LexicalContext functionContext) {
    Node originalFunction = checkNotNull(functionContext.function);
    originalFunction.setIsAsyncFunction(false);
    Node originalBody = originalFunction.getLastChild();
    Node newBody = IR.block();
    originalFunction.replaceChild(originalBody, newBody);

    if (functionContext.mustAddAsyncThisVariable) {
      // const this$ = this;
      newBody.addChildToBack(IR.constNode(IR.name(ASYNC_THIS), IR.thisNode()));
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }
    if (functionContext.mustAddAsyncArgumentsVariable) {
      // const arguments$ = arguments;
      newBody.addChildToBack(IR.constNode(IR.name(ASYNC_ARGUMENTS), IR.name("arguments")));
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }
    for (String replacedMethodName : functionContext.replacedSuperProperties) {
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
        if (!originalFunction.getParent().isStaticMember()) {
          // instance super: Object.getPrototypeOf(Object.getPrototypeOf(this))
          superReference =
              IR.call(IR.getprop(IR.name("Object"), IR.string("getPrototypeOf")), superReference);
        }
      } else {
        superReference = IR.superNode();
      }

      // const super$get$x = () => super.x;
      // OR avoid super for static method (class object -> superclass object)
      // const super$get$x = () => Object.getPrototypeOf(this).x
      // OR avoid super for instance method (instance -> prototype -> super prototype)
      // const super$get$x = () => Object.getPrototypeOf(Object.getPrototypeOf(this)).x
      Node arrowFunction =
          IR.arrowFunction(
              IR.name(""),
              IR.paramList(),
              IR.getprop(superReference, IR.string(replacedMethodName)));
      compiler.reportChangeToChangeScope(arrowFunction);
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.ARROW_FUNCTIONS);

      String superReplacementName = ASYNC_SUPER_PROP_GETTER_PREFIX + replacedMethodName;
      newBody.addChildToBack(IR.constNode(IR.name(superReplacementName), arrowFunction));
      NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.CONST_DECLARATIONS);
    }

    // Normalize arrow function short body to block body
    if (!originalBody.isBlock()) {
      originalBody = IR.block(IR.returnNode(originalBody).useSourceInfoFrom(originalBody))
          .useSourceInfoFrom(originalBody);
    }
    // NOTE: visit() will already have made appropriate replacements in originalBody so it may
    // be used as the generator function body.
    Node generatorFunction = IR.function(IR.name(""), IR.paramList(), originalBody);
    generatorFunction.setIsGeneratorFunction(true);
    compiler.reportChangeToChangeScope(generatorFunction);
    NodeUtil.addFeatureToScript(t.getCurrentFile(), Feature.GENERATORS);

    // return $jscomp.asyncExecutePromiseGeneratorFunction(function* () { ... });
    newBody.addChildToBack(IR.returnNode(IR.call(
        IR.getprop(IR.name("$jscomp"), IR.string("asyncExecutePromiseGeneratorFunction")),
        generatorFunction)));

    newBody.useSourceInfoIfMissingFromForTree(originalBody);
    compiler.reportChangeToEnclosingScope(newBody);
  }
}
