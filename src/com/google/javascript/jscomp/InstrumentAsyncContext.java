/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ThisAndArgumentsReferenceUpdater.ThisAndArgumentsContext;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Instruments {@code await} and {@code yield} for the {@code AsyncContext} polyfill. */
public class InstrumentAsyncContext implements CompilerPass, NodeTraversal.Callback {

  private static final String JSCOMP = "$jscomp";
  private static final String START = "asyncContextStart";

  // NOTE: we prefix all internal symbols with a characteristic "ᵃᶜ" (U+1D43, U+1D9C) to
  // significantly reduce the chance of conflicts with existing names in the code.  This isn't
  // perfect, but it results in much more readable transpiled code compared to always generating a
  // longer name, keeps tests simple and deterministic compared to using an ID generator, and is
  // more performant than scanning for conflicting symbols to only disambiguate when necessary.
  private static final String FACTORY = "ᵃᶜfactory";
  private static final String SUSPEND = "ᵃᶜsuspend";
  private static final String RESUME = "ᵃᶜresume";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;

  // Whether `await`s should be instrumented.  When we're transpiling them away, we can skip
  // instrumenting them completely (the transpiled `yield`s don't need instrumentation because we
  // can rely on `Promise.then` to take care of it).
  private final boolean shouldInstrumentAwait;

  // TODO(sdh): Investigate whether async generator instrumentation can be skipped in ES2017
  // output.  They are transpiled to ordinary generators with Promise.then, so we may be able
  // to rely on runtime patching in that case as well.  If so, we'll add an additional boolean
  // constructor parameter for shouldInstrumentAsyncGenerators (and figure out whether that means
  // that both yield _and_ await can be skipped, or just await).

  private final Deque<Node> tryFunctionStack = new ArrayDeque<>();
  private final Set<Node> needsInstrumentation = new LinkedHashSet<>();
  private final Set<Node> alreadyInstrumented = new LinkedHashSet<>();
  private final Set<Node> hasSuper = new LinkedHashSet<>();

  public InstrumentAsyncContext(AbstractCompiler compiler, boolean shouldInstrumentAwait) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.shouldInstrumentAwait = shouldInstrumentAwait;
  }

  @Override
  public void process(Node externs, Node root) {
    // Scan through code and find uses of AsyncContext. Look specifically for Variable.run.
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // The top of tryFunctionStack should always point to the innermost try or function block.
    // This is a minor optimization to avoid recursing up the ancestor chain to determine if a
    // reentrance is within a try block, in which case a "resume" call needs to be added to the
    // subsequent catch or finally.  We add functions to the stack as well because functions have
    // their own separate scope, so any try block surrounding the function body should _not_ be
    // instrumented.
    if (n.isTry() || n.isFunction()) {
      tryFunctionStack.push(n);
    }
    return true;
  }

  private boolean isReentrance(Node n) {
    if (n.isYield()) {
      return true;
    } else if (!shouldInstrumentAwait) {
      return false;
    }
    return n.isForAwaitOf() || n.isAwait();
  }

  @Override
  public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
    if (n.isName() && n.getString().equals(FACTORY)) {
      alreadyInstrumented.add(t.getEnclosingFunction());
    } else if (n.isSuper()) {
      hasSuper.add(t.getEnclosingFunction());
    } else if (isReentrance(n)) {
      instrumentReentrance(t, n, parent);
    } else if (n.equals(tryFunctionStack.peek())) {
      tryFunctionStack.pop();
      if (alreadyInstrumented.contains(n)) {
        return;
      }
      if (needsInstrumentation.contains(n) || n.isGeneratorFunction()) {
        if (n.isTry()) {
          instrumentTry(n);
        } else if (n.isGeneratorFunction()) {
          instrumentGeneratorFunction(t, n);
        } else {
          checkState(n.isAsyncFunction());
          instrumentAsyncFunction(n);
        }
      }
    }
  }

  // When we see a reentrance node, we need to do several things:
  //  1. replace `await EXPR` with `ᵃᶜresume(await ᵃᶜsuspend(EXPR))`
  //  2. ensure the enclosing function has been instrumented with `$jscomp.asyncContextStart` and a
  //     `try`-`finally`
  //  3. look for an immediately-enclosing `try` block and instrument it with `ᵃᶜresume`
  private void instrumentReentrance(NodeTraversal t, Node n, Node parent) {
    Node enclosingTry = tryFunctionStack.peek();
    if (enclosingTry != null && !enclosingTry.isTry()) {
      enclosingTry = null; // not a try
    }
    Node enclosingFunction = t.getEnclosingFunction();
    if (enclosingFunction == null) {
      // NOTE: This is a top-level await, which is currently not supported.  If it becomes supported
      // then we should consider instrumenting the module body as if it were a function.
      // See https://github.com/google/closure-compiler/issues/3835.
      return;
    } else if (alreadyInstrumented.contains(enclosingFunction)) {
      // This function is already instrumented, so don't do any further instrumentation.
      return;
    } else if (parent.isFunction()) {
      // `async (...) => await ...` - no reentrance, so no instrumentation needed.
      return;
    } else if (parent.isReturn() && enclosingTry == null) {
      // `return await ...` - no reentrance unless we're inside a `try`.
      return;
    }

    // At this point, instrumentation is required, both for the immediate node, as well as for any
    // enclosing function or try block.
    needsInstrumentation.add(enclosingFunction);
    if (enclosingTry != null) {
      needsInstrumentation.add(enclosingTry);
    }

    if (n.isForAwaitOf()) {
      instrumentForAwaitOf(t, n, parent);
      return;
    }

    // Wrap yielded/awaited expression with a ᵃᶜsuspend(...) call
    Node placeholder = IR.empty();
    Node arg = n.getFirstChild();
    if (arg == null) {
      n.addChildToBack(createSuspend().srcrefTreeIfMissing(n));
    } else {
      arg.replaceWith(placeholder);
      placeholder.replaceWith(createSuspend(arg).srcrefTreeIfMissing(arg));
    }

    // Wrap the entire yield/await with a ᵃᶜresume(...) call
    n.replaceWith(placeholder);
    placeholder.replaceWith(createResume(n).srcrefTreeIfMissing(n));
  }

  void instrumentForAwaitOf(NodeTraversal t, Node n, Node parent) {
    // for await (const x of expr()) {
    //   use(x);
    // }
    //     becomes
    // for await (const x of suspend(expr())) {
    //   resume();
    //   try {
    //     use(x);
    //   } finally {
    //     suspend();
    //   }
    // }
    // resume();

    // First wrap the iterator argument
    Node placeholder = IR.empty();
    Node arg = n.getSecondChild();
    arg.replaceWith(placeholder);
    placeholder.replaceWith(createSuspend(arg).srcrefTreeIfMissing(arg));

    Node body = n.getLastChild();
    checkState(body.isBlock()); // NOTE: IRFactory normalizes non-block `for` bodies
    body.replaceWith(placeholder);
    placeholder.replaceWith(
        IR.block(
            IR.exprResult(createResume().srcrefTreeIfMissing(arg)),
            IR.tryFinally(
                body, //
                IR.block(IR.exprResult(createSuspend().srcrefTreeIfMissing(arg))))));

    // Add resume call after for-await-of
    if (!parent.isBlock()) {
      // NOTE: if the for-await-of is _not_ the child of a block, we could be in trouble.
      Node block = IR.block();
      n.replaceWith(block);
      block.addChildToFront(n);
      n = block;
    }
    IR.exprResult(createResume().srcrefTreeIfMissing(arg)).insertAfter(n);
  }

  void instrumentTry(Node n) {
    // Find either a catch or a finally block (if there is no catch).
    Node block = n.getSecondChild();
    if (block.hasChildren()) {
      // There's a catch block: descend into the actual block
      block = block.getFirstChild().getSecondChild();
    } else {
      // Look for a finally block instead
      block = block.getNext();
    }
    checkState(block.isBlock());
    // Prepend an empty resume call: ᵃᶜresume()
    block.addChildToFront(IR.exprResult(createResume().srcrefTreeIfMissing(block)));
  }

  void instrumentGeneratorFunction(NodeTraversal t, Node f) {
    // If a function is completely empty then there's no need to instrument it
    // because there's nothing within that can observe the state of any variables.
    if (!f.getLastChild().hasChildren()) {
      return;
    }
    // NOTE: if `f` is a generator _method_ then we need to be more careful.
    // Because generators are not allowed to be arrow functions, we need to
    // use the ThisAndArgumentsReferenceUpdater to extract any references to
    // those symbols within the vanilla IIFE generator.  But this doesn't help
    // with any references to `super`.  Instead, for methods we have a clear
    // method body and can move the generator body into a newly synthesized
    // method that we can directly.  If "arguments" is used, then we need to
    // call it with .apply and forward the arguments.  This is dangerous for
    // certain methods that might be collapsed (e.g. static methods), so we
    // only do when necessary (i.e. `super` is used), which corresponds to a
    // known-safe case (since `super` can't be collapsed).
    if (hasSuper.contains(f)) {
      instrumentGeneratorMethod(t, f);
      return;
    }

    // Turn the generator into an ordinary function with an IIFE generator
    f.setIsGeneratorFunction(false);
    Node generatorBody = f.getLastChild().detach();
    Node inner = astFactory.createEmptyGeneratorFunction(AstFactory.type(f));

    if (f.isAsyncFunction()) {
      f.setIsAsyncFunction(false);
      inner.setIsAsyncFunction(true);
    }
    // inner.getFirstChild().setString(f.getFirstChild().getString());
    inner.getLastChild().replaceWith(generatorBody);

    generatorBody = addFinallySuspend(generatorBody);
    generatorBody.addChildToFront(IR.exprResult(createResume()));
    Node newOuterBody =
        IR.block(
            createFactorySuspendCall(), IR.returnNode(astFactory.createCallWithUnknownType(inner)));
    addSuspendResumeVarsAfter(newOuterBody.getFirstChild());
    f.addChildToBack(newOuterBody.srcrefTreeIfMissing(generatorBody));

    // NOTE: if the IIFE generator refers to `this` or `arguments` then we need to extract these
    // references into local variables.
    ThisAndArgumentsContext thisContext =
        new ThisAndArgumentsContext(
            newOuterBody, false, compiler.getUniqueIdSupplier().getUniqueId(t.getInput()));
    ThisAndArgumentsReferenceUpdater updater =
        new ThisAndArgumentsReferenceUpdater(compiler, thisContext, astFactory);
    NodeTraversal.traverse(compiler, generatorBody, updater);
    // Check the context to see if we found this or arguments.
    thisContext.addVarDeclarations(compiler, astFactory, t.getCurrentScript());

    compiler.reportChangeToChangeScope(f);
    compiler.reportChangeToChangeScope(inner);
  }

  void instrumentGeneratorMethod(NodeTraversal t, Node f) {
    // This requires special handling compared to ordinary generator functions because
    // it may reference `super`, which would break in a vanilla IIFE.  Instead, copy the
    // body to a new method with the same parameters, plus one or two more for the
    // context and possibly `arguments`.

    // Specifically:
    //     *m(a, {b: c=1}, d=2, ...e) { ... }
    // would become
    //     *m$jscomp$123(ᵃᶜfactory, a, {b: c=1}, d=2, ...e) {
    //       var ᵃᶜsuspend = ᵃᶜfactory();
    //       var ᵃᶜresume = ᵃᶜfactory(1);
    //       try { ... } finally { ᵃᶜsuspend(); }
    //     }
    //     m(a, $jscomp$0, d, ...e) {
    //       const ᵃᶜfactory = $jscomp.asyncContextStart();
    //       return this.m$jscomp$123(ᵃᶜfactory, a, $jscomp$0, d, ...e);
    //     }
    // If the function refers to `arguments`, then an additional
    // `ᵃᶜarguments` parameter is added to the synthesized method after
    // the context argument.

    String innerMethodName =
        (f.getParent().isMemberFunctionDef() ? f.getParent().getString() : "")
            + "$jscomp$"
            + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
    Node outerParams = f.getSecondChild();
    Node innerParams = outerParams.cloneTree();

    // Add the ᵃᶜfactory parameter to the front of the inner parameter list
    Node swapFactoryParam = createFactoryName();
    innerParams.addChildToFront(swapFactoryParam);
    AstFactory.Type unknownType = AstFactory.type(swapFactoryParam);

    // Add a finally-suspend wrapper and an initial resume call around the original generator body
    // (which has already had its yields/awaits instrumented).
    Node generatorBody = addFinallySuspend(f.getLastChild());
    generatorBody.addChildToFront(IR.exprResult(createResume()));

    // Look for references to `arguments`.  If found, add an extra parameter to the inner method.
    // We need to save a reference to the parameter in order to replace it in the function call
    // arguments.
    Node argumentsParam = null;
    Node argumentsReference = null;
    ArgumentsRenamer renamer = new ArgumentsRenamer();
    NodeTraversal.traverse(compiler, generatorBody, renamer);
    if (renamer.argumentsName != null) {
      argumentsReference = astFactory.createArgumentsReference();
      argumentsParam =
          astFactory.createName(renamer.argumentsName, AstFactory.type(argumentsReference));
      argumentsParam.insertAfter(swapFactoryParam);
    }

    // Detach the generator body and replace it with a new empty block.  We'll fill the empty block
    // later, and will insert the instrumented generator body into the new inner method.
    Node outerBody = IR.block();
    generatorBody.replaceWith(outerBody);

    // Build the call to the inner method.  Iterate over innerParams to build up the arguments list,
    // replacing any non-simple arguments with simple names.
    Node innerCall =
        astFactory.createCallWithUnknownType(
            astFactory.createGetProp(
                astFactory.createThis(unknownType), innerMethodName, AstFactory.type(f)));
    // Copy the context parameter.
    Node innerParam = innerParams.getFirstChild();
    innerCall.addChildToBack(innerParam.cloneTree());
    innerParam = innerParam.getNext();
    // Copy the arguments parameter if it's there.
    if (innerParam != null && innerParam == argumentsParam) {
      innerCall.addChildToBack(argumentsReference);
    }
    // Copy the remaining parameters.
    for (Node outerParam = outerParams.getFirstChild();
        outerParam != null;
        outerParam = outerParam.getNext()) {
      ArgParamPair argParamPair = simplifyParameter(outerParam.cloneTree(), t);
      outerParam.replaceWith(outerParam = argParamPair.parameter);
      innerCall.addChildToBack(argParamPair.argument);
    }

    // Make a new inner method with the instrumented generator body, and insert it after the
    // original method.
    Node newInnerMethod =
        astFactory.createFunction("", innerParams, generatorBody, AstFactory.type(f));
    IR.memberFunctionDef(innerMethodName, newInnerMethod).insertAfter(f.getParent());
    newInnerMethod.getFirstChild().makeNonIndexable();

    // Move the async/generator flags from the original method to the new one.
    newInnerMethod.setIsAsyncFunction(f.isAsyncFunction());
    newInnerMethod.setIsGeneratorFunction(true);
    f.setIsGeneratorFunction(false);
    f.setIsAsyncFunction(false);

    // Copy static and/or @nocollapse from the parent (member_function_def) node.
    Node fp = f.getParent();
    Node np = newInnerMethod.getParent();
    np.setStaticMember(fp.isStaticMember());
    JSDocInfo jsdoc = fp.getJSDocInfo();
    if (jsdoc != null) {
      var builder = JSDocInfo.builder();
      if (jsdoc.isNoCollapse()) {
        builder.recordNoCollapse();
      }
      if (jsdoc.isNoInline()) {
        builder.recordNoInline();
      }
      np.setJSDocInfo(builder.build());
    }

    // Populate the outer (non-generator) method body with an start() and call to the inner method.
    outerBody.addChildToBack(createFactorySuspendCall());
    addSuspendResumeVarsAtStartOf(generatorBody);
    outerBody.addChildToBack(IR.returnNode(innerCall));

    // Various cleanups
    f.srcrefTreeIfMissing(f);
    newInnerMethod.getParent().srcrefTreeIfMissing(f);
    NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.MEMBER_DECLARATIONS, compiler);
    compiler.reportChangeToChangeScope(f);
    compiler.reportChangeToChangeScope(newInnerMethod);
    compiler.reportChangeToEnclosingScope(f.getParent());
  }

  private ArgParamPair simplifyParameter(Node param, NodeTraversal t) {
    if (param.isRest()) {
      // Replace REST with SPREAD.  For arguments, we know it has to be ITER_REST and ITER_SPREAD.
      NodeUtil.addFeatureToScript(t.getCurrentScript(), Feature.SPREAD_EXPRESSIONS, compiler);
      Node paramElement = param.removeFirstChild();
      ArgParamPair simplified = simplifyParameter(paramElement, t);
      return new ArgParamPair(
          IR.iterSpread(simplified.argument).srcrefTreeIfMissing(param),
          IR.iterRest(simplified.parameter).srcrefTreeIfMissing(param));
    } else if (param.isDefaultValue()) {
      // Ignore any initializers; replace with just the name.
      return simplifyParameter(param.removeFirstChild(), t);
    } else if (param.isArrayPattern() || param.isObjectPattern()) {
      // Replace destructuring patterns with a synthesized name.
      return simplifyParameter(
          astFactory
              .createName(
                  "$jscomp$" + compiler.getUniqueIdSupplier().getUniqueId(t.getInput()),
                  AstFactory.type(param))
              .srcrefTreeIfMissing(param),
          t);
    } else if (param.isName()) {
      return new ArgParamPair(param.cloneTree(), param);
    }
    throw new IllegalStateException("Unexpected parameter: " + param);
  }

  private static class ArgParamPair {
    final Node argument;
    final Node parameter;

    ArgParamPair(Node argument, Node parameter) {
      this.argument = argument;
      this.parameter = parameter;
    }
  }

  // Renames all instances of `arguments` with a new unique name.
  private class ArgumentsRenamer implements NodeTraversal.Callback {
    String argumentsName = null;

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }

    @Override
    public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      if (n.isName() && n.getString().equals("arguments")) {
        if (argumentsName == null) {
          argumentsName =
              "$jscomp$arguments$" + compiler.getUniqueIdSupplier().getUniqueId(t.getInput());
        }
        n.setString(argumentsName);
        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          n.setOriginalName("arguments");
        }
        n.makeNonIndexable();
      }
    }
  }

  /**
   * Given an async function node, instrument it by wrapping the body in a try-finally and adding an
   * "start" call to the front.
   */
  void instrumentAsyncFunction(Node f) {
    // Add a variable to the front of the body
    Node block = f.getLastChild();
    Node newBlock = addFinallySuspend(block);
    newBlock.addChildToFront(createFactoryCall().srcrefTreeIfMissing(newBlock));
    addSuspendResumeVarsAfter(newBlock.getFirstChild());
    compiler.reportChangeToChangeScope(f);

    // TODO(sdh): Need to instrument async functions for unhandled rejections.
    // Ideally this would happen after devirtualization and then we can extract a
    // helper function to call rather than making a new closure.  Would also be nice
    // to not even bother with it if we're not worrying about that case.
  }

  /**
   * Replace a function body with a new body containing only try (original body) finally (suspend).
   */
  Node addFinallySuspend(Node block) {
    Node placeholder = IR.empty();
    block.replaceWith(placeholder);
    if (!block.isBlock()) {
      // NOTE: this can happen with a non-block arrow function.
      block = IR.block(IR.returnNode(block)).srcrefTreeIfMissing(block);
    }
    Node newBlock =
        IR.block(IR.tryFinally(block, IR.block(IR.exprResult(createSuspend()))))
            .srcrefTreeIfMissing(block);
    placeholder.replaceWith(newBlock);
    return newBlock;
  }

  /** Creates an {@code ᵃᶜsuspend} identifier node. */
  private Node createSuspendName() {
    return astFactory.createQNameWithUnknownType(SUSPEND);
  }

  /** Creates a {@code ᵃᶜresume} identifier node. */
  private Node createResumeName() {
    return astFactory.createQNameWithUnknownType(RESUME);
  }

  /** Creates a {@code ᵃᶜfactory} identifier node. */
  private Node createFactoryName() {
    return astFactory.createQNameWithUnknownType(FACTORY);
  }

  private void addSuspendResumeVarsAfter(Node node) {
    // var ᵃᶜsuspend = ᵃᶜfactory();
    // var ᵃᶜresume = ᵃᶜfactory(1);
    Node resumeCall =
        astFactory.createCallWithUnknownType(createFactoryName(), astFactory.createNumber(1));
    IR.var(astFactory.createConstantName(RESUME, AstFactory.type(resumeCall)), resumeCall)
        .srcrefTreeIfMissing(node)
        .insertAfter(node);
    Node suspendCall = astFactory.createCallWithUnknownType(createFactoryName());
    IR.var(astFactory.createConstantName(SUSPEND, AstFactory.type(suspendCall)), suspendCall)
        .srcrefTreeIfMissing(node)
        .insertAfter(node);
  }

  private void addSuspendResumeVarsAtStartOf(Node node) {
    // var ᵃᶜsuspend = ᵃᶜfactory();
    // var ᵃᶜresume = ᵃᶜfactory(1);
    Node resumeCall =
        astFactory.createCallWithUnknownType(createFactoryName(), astFactory.createNumber(1));
    node.addChildToFront(
        IR.var(astFactory.createConstantName(RESUME, AstFactory.type(resumeCall)), resumeCall)
            .srcrefTreeIfMissing(node));
    Node suspendCall = astFactory.createCallWithUnknownType(createFactoryName());
    node.addChildToFront(
        IR.var(astFactory.createConstantName(SUSPEND, AstFactory.type(suspendCall)), suspendCall)
            .srcrefTreeIfMissing(node));
  }

  /** Creates a statement {@code const ᵃᶜfactory = $jscomp$asyncContextStart();}. */
  private Node createFactoryCall(Node... arg) {
    Node call =
        astFactory.createCallWithUnknownType(
            astFactory.createQNameWithUnknownType(JSCOMP, ImmutableList.of(START)), arg);
    return createVar(astFactory.createConstantName(FACTORY, AstFactory.type(call)), call);
  }

  private Node createVar(Node name, Node value) {
    return IR.var(name, value);
  }

  /** Creates a statement {@code const ᵃᶜfactory = $jscomp$asyncContextStart(1);}. */
  private Node createFactorySuspendCall() {
    return createFactoryCall(astFactory.createNumber(1));
  }

  /** Wraps the given node in an suspend call: {@code ᵃᶜsuspend(NODE)}. */
  private Node createSuspend(Node inner) {
    return astFactory.createCall(createSuspendName(), AstFactory.type(inner), inner);
  }

  /** Creates an empty suspend call: {@code ᵃᶜsuspend()}. */
  private Node createSuspend() {
    Node suspend = createSuspendName();
    return astFactory.createCall(suspend, AstFactory.type(suspend));
  }

  /** Wraps the given node in a resume call: {@code ᵃᶜresume(NODE)}. */
  private Node createResume(Node inner) {
    return astFactory.createCall(createResumeName(), AstFactory.type(inner), inner);
  }

  /** Creates an empty resume call: {@code ᵃᶜresume()}. */
  private Node createResume() {
    Node resume = createResumeName();
    return astFactory.createCall(resume, AstFactory.type(resume));
  }
}
