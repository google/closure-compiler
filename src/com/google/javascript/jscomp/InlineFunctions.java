/*
 * Copyright 2005 The Closure Compiler Authors.
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
import static com.google.common.base.Predicates.alwaysTrue;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.FunctionInjector.CanInlineResult;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Inlines functions that are divided into two types: "direct call node replacement" (aka "direct")
 * and as a block of statements (aka block). Function that can be inlined "directly" functions
 * consist of a single return statement, everything else is must be inlined as a "block". These
 * functions must meet these general requirements: - it is not recursive - the function does not
 * contain another function -- these may be intentional to to limit the scope of closures. -
 * function is called only once OR the size of the inline function is smaller than the call itself.
 * - the function name is not referenced in any other manner
 *
 * <p>"directly" inlined functions must meet these additional requirements: - consists of a single
 * return statement
 */
class InlineFunctions implements CompilerPass {

  static final DiagnosticType MISSED_REQUIRED_INLINING =
      DiagnosticType.warning(
          "JSC_MISSED_REQUIRED_INLINING",
          "function {1} annotated @requireInlining could not be inlined here");

  // TODO(nicksantos): This needs to be completely rewritten to use scopes
  // to do variable lookups. Right now, it assumes that all functions are
  // uniquely named variables. There's currently a stopgap scope-check
  // to ensure that this doesn't produce invalid code. But in the long run,
  // this needs a major refactor.
  private final Map<String, FunctionState> fns = new LinkedHashMap<>();
  private final Map<Node, String> anonFns = new LinkedHashMap<>();

  private final AbstractCompiler compiler;

  private final FunctionInjector injector;
  private final FunctionArgumentInjector functionArgumentInjector;

  private final Reach reach;
  private final boolean assumeMinimumCapture;

  private final boolean enforceMaxSizeAfterInlining;
  private final int maxSizeAfterInlining;

  InlineFunctions(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      Reach reach,
      boolean assumeStrictThis,
      boolean assumeMinimumCapture,
      int maxSizeAfterInlining) {
    checkArgument(compiler != null);
    checkArgument(safeNameIdSupplier != null);
    checkArgument(reach != Reach.NONE);

    this.compiler = compiler;

    this.reach = reach;
    this.assumeMinimumCapture = assumeMinimumCapture;

    this.maxSizeAfterInlining = maxSizeAfterInlining;
    this.enforceMaxSizeAfterInlining =
        maxSizeAfterInlining != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;

    // TODO(b/124253050): Update bookkeeping logic and reenable method call inliing.
    // Method call decomposition creates new call nodes after all the analysis
    // is done, which would cause such calls to function to be left behind after
    // the function itself is removed.  The function inliner need to be made
    // aware of these new calls in order to enable it.

    this.functionArgumentInjector = new FunctionArgumentInjector(compiler.getAstAnalyzer());
    this.injector =
        new FunctionInjector.Builder(compiler)
            .safeNameIdSupplier(safeNameIdSupplier)
            .assumeStrictThis(assumeStrictThis)
            .assumeMinimumCapture(assumeMinimumCapture)
            .functionArgumentInjector(this.functionArgumentInjector)
            .build();
  }

  FunctionState getOrCreateFunctionState(String fnName) {
    return fns.computeIfAbsent(fnName, (String k) -> new FunctionState());
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());

    NodeTraversal.traverse(compiler, root, new FindCandidateFunctions());
    if (fns.isEmpty()) {
      return; // Nothing left to do.
    }
    NodeTraversal.traverse(compiler, root, new FindCandidatesReferences(fns, anonFns));
    trimCandidatesNotMeetingMinimumRequirements();
    if (fns.isEmpty()) {
      return; // Nothing left to do.
    }

    // Store the set of function names eligible for inlining and use this to
    // prevent function names from being moved into temporaries during
    // expression decomposition. If this movement were allowed it would prevent
    // the Inline callback from finding the function calls.
    //
    // This pass already assumes these are constants, so this is safe for anyone
    // using function inlining.
    //
    ImmutableSet<String> fnNames = ImmutableSet.copyOf(fns.keySet());
    injector.setKnownConstantFunctions(fnNames);

    trimCandidatesUsingOnCost();
    if (fns.isEmpty()) {
      return; // Nothing left to do.
    }
    resolveInlineConflicts();
    decomposeExpressions();
    NodeTraversal.traverse(compiler, root, new CallVisitor(fns, anonFns, new Inline(injector)));

    removeInlinedFunctions();
  }

  private static boolean isAlwaysInlinable(Node fn) {
    checkArgument(fn.isFunction());
    Node body = NodeUtil.getFunctionBody(fn);
    return (!body.hasChildren()) || (body.hasOneChild() && body.getFirstChild().isReturn());
  }

  private boolean targetSizeAfterInlineExceedsLimit(NodeTraversal t, FunctionState functionState) {
    Node containingFunction = t.getEnclosingFunction();
    // Always inline at the top level,
    // unless maybeAddFunction has marked functionState as not inlinable.
    if (containingFunction == null) {
      return false;
    }
    Node inlinedFun = functionState.getFn().getFunctionNode();
    if (isAlwaysInlinable(inlinedFun)) {
      return false;
    }

    int inlinedFunSize =
        NodeUtil.countAstSizeUpToLimit(NodeUtil.getFunctionBody(inlinedFun), maxSizeAfterInlining);
    int targetFunSize = NodeUtil.countAstSizeUpToLimit(containingFunction, maxSizeAfterInlining);
    return inlinedFunSize + targetFunSize > maxSizeAfterInlining;
  }

  /** Find functions that might be inlined. */
  private class FindCandidateFunctions extends AbstractPostOrderCallback {
    private int callsSeen = 0;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (reach.includesGlobals() || !t.inGlobalHoistScope()) {
        findNamedFunctions(t, n, parent);
        findFunctionExpressions(t, n);
      }
    }

    public void findNamedFunctions(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isStatement(n)) {
        // There aren't any interesting functions here.
        return;
      }

      switch (n.getToken()) {
        case VAR, LET, CONST -> {
          // Functions expressions in the form of:
          //   var fooFn = function(x) { return ... }
          Preconditions.checkState(n.hasOneChild(), n);
          Node nameNode = n.getFirstChild();
          if (nameNode.isName()
              && nameNode.hasChildren()
              && nameNode.getFirstChild().isFunction()) {
            maybeAddFunction(new FunctionVar(n), t.getChunk());
          }
          // Named functions
          // function Foo(x) { return ... }
        }
        case FUNCTION -> {
          Preconditions.checkState(NodeUtil.isStatementBlock(parent) || parent.isLabel());
          if (NodeUtil.isFunctionDeclaration(n)) {
            Function fn = new NamedFunction(n);
            maybeAddFunction(fn, t.getChunk());
          }
        }
        default -> {}
      }
    }

    /**
     * Find function expressions that are called directly in the form of
     * (function(a,b,...){...})(a,b,...) or (function(a,b,...){...}).call(this,a,b, ...)
     */
    public void findFunctionExpressions(NodeTraversal t, Node n) {
      switch (n.getToken()) {
        case OPTCHAIN_CALL, CALL -> {
          // Functions expressions in the form of:
          //   (function(){})();
          Node fnNode = null;
          if (n.getFirstChild().isFunction()) {
            fnNode = n.getFirstChild();
          } else if (NodeUtil.isFunctionObjectCall(n)) {
            Node fnIdentifyingNode = n.getFirstFirstChild();
            if (fnIdentifyingNode.isFunction()) {
              fnNode = fnIdentifyingNode;
            }
          }

          // If an interesting function was discovered, add it.
          if (fnNode != null) {
            Function fn = new FunctionExpression(fnNode, callsSeen++);
            maybeAddFunction(fn, t.getChunk());
            anonFns.put(fnNode, fn.getName());
          }
        }
        default -> {}
      }
    }
  }

  /**
   * Updates the FunctionState object for the given function. Checks if the given function matches
   * the criteria for an inlinable function.
   */
  void maybeAddFunction(Function fn, JSChunk chunk) {
    String name = fn.getName();
    FunctionState functionState = getOrCreateFunctionState(name);
    updateFunctionStateForInlining(fn, chunk, name, functionState);
    checkState(!(hasRequireInliningAnnotation(fn.getFunctionNode()) && !functionState.canInline()));
  }

  /**
   * Updates the FunctionState object for the given function. Checks if the given function matches
   * the criteria for an inlinable function.
   */
  void updateFunctionStateForInlining(
      Function fn, JSChunk chunk, String name, FunctionState functionState) {
    // TODO(johnlenz): Maybe "smarten" FunctionState by adding this logic to it?

    // If the function has multiple definitions, don't inline it.
    if (functionState.hasExistingFunctionDefinition()) {
      functionState.disallowInlining(
          compiler, DisallowInliningReason.MULTIPLE_FUNCTION_DEFINITIONS);
      return;
    }
    Node fnNode = fn.getFunctionNode();

    if (hasNoInlineAnnotation(fnNode)) {
      functionState.disallowInlining(compiler, DisallowInliningReason.NO_INLINE_ANNOTATION);
      return;
    }

    if (enforceMaxSizeAfterInlining
        && !isAlwaysInlinable(fnNode)
        && maxSizeAfterInlining <= NodeUtil.countAstSizeUpToLimit(fnNode, maxSizeAfterInlining)) {
      functionState.disallowInlining(compiler, DisallowInliningReason.TARGET_SIZE_EXCEEDS_LIMIT);
      return;
    }

    // verify the function hasn't already been marked as "don't inline"
    if (functionState.canInline()) {
      // store it for use when inlining.
      functionState.setFn(fn);
      if (FunctionInjector.isDirectCallNodeReplacementPossible(fn.getFunctionNode())) {
        functionState.inlineDirectly(true);
      }

      if (hasNonInlinableParam(NodeUtil.getFunctionParameters(fnNode))) {
        functionState.disallowInlining(compiler, DisallowInliningReason.NON_INLINABLE_PARAM);
      }

      // verify the function meets all the requirements.
      // TODO(johnlenz): Minimum requirement checks are about 5% of the
      // run-time cost of this pass.
      if (!isCandidateFunction(fn)) {
        // It doesn't meet the requirements.
        functionState.disallowInlining(compiler, DisallowInliningReason.NOT_CANDIDATE_FUNCTION);
      }

      // Set the chunk and gather names that need temporaries.
      if (functionState.canInline()) {
        functionState.setChunk(chunk);

        Set<String> namesToAlias = functionArgumentInjector.findModifiedParameters(fnNode);
        if (!namesToAlias.isEmpty()) {
          functionState.inlineDirectly(false);
          functionState.setNamesToAlias(namesToAlias);
        }

        Node block = NodeUtil.getFunctionBody(fnNode);
        if (NodeUtil.referencesEnclosingReceiver(block)) {
          functionState.setReferencesThis(true);
        }

        if (NodeUtil.has(block, Node::isFunction, alwaysTrue())) {
          functionState.setHasInnerFunctions(true);
          // If there are inner functions, we can inline into global scope
          // if there are no local vars or named functions.
          // TODO(johnlenz): this can be improved by looking at the possible
          // values for locals.  If there are simple values, or constants
          // we could still inline.
          if (!assumeMinimumCapture && hasLocalNames(fnNode)) {
            functionState.disallowInlining(compiler, DisallowInliningReason.HAS_LOCAL_NAMES);
          }
        }
      }

      if (fnNode.getGrandparent().isVar()) {
        Node block = functionState.getFn().getDeclaringBlock();
        if (block.isBlock()
            && !block.getParent().isFunction()
            && NodeUtil.has(block, (n) -> n.isLet() || n.isConst(), alwaysTrue())) {
          // The function might capture a variable that's not in scope at the call site,
          // so don't inline.
          functionState.disallowInlining(
              compiler, DisallowInliningReason.CAPTURED_VARIABLE_NOT_IN_SCOPE);
        }
      }

      if (fnNode.isGeneratorFunction()) {
        functionState.disallowInlining(compiler, DisallowInliningReason.GENERATOR_FUNCTION);
      }

      if (fnNode.isAsyncFunction()) {
        functionState.disallowInlining(compiler, DisallowInliningReason.ASYNC_FUNCTION);
      }
    }
  }

  private boolean hasNoInlineAnnotation(Node fnNode) {
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(fnNode);
    return jsDocInfo != null && jsDocInfo.isNoInline();
  }

  private static boolean hasEncourageInliningAnnotation(Node fnNode) {
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(fnNode);
    return jsDocInfo != null && jsDocInfo.isEncourageInlining();
  }

  private static boolean hasRequireInliningAnnotation(Node fnNode) {
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(fnNode);
    return jsDocInfo != null && jsDocInfo.isRequireInlining();
  }

  /**
   * @param fnNode The function to inspect.
   * @return Whether the function has parameters, var/const/let, class, or function declarations.
   */
  private static boolean hasLocalNames(Node fnNode) {
    Node block = NodeUtil.getFunctionBody(fnNode);
    return NodeUtil.getFunctionParameters(fnNode).hasChildren()
        || NodeUtil.has(
            block, new NodeUtil.MatchDeclaration(), new NodeUtil.MatchShallowStatement());
  }

  /** Checks if the given function matches the criteria for an inlinable function. */
  private boolean isCandidateFunction(Function fn) {
    // Don't inline exported functions.
    String fnName = fn.getName();
    if (compiler.getCodingConvention().isExported(fnName)) {
      // TODO(johnlenz): Should we allow internal references to be inlined?
      // An exported name can be replaced externally, any inlined instance
      // would not reflect this change.
      // To allow inlining we need to be able to distinguish between exports
      // that are used in a read-only fashion and those that can be replaced
      // by external definitions.
      return false;
    }

    // Don't inline this special function
    if (compiler.getCodingConvention().isPropertyRenameFunction(fn.getNameNode())) {
      return false;
    }

    Node fnNode = fn.getFunctionNode();
    return injector.doesFunctionMeetMinimumRequirements(fnName, fnNode);
  }

  /**
   * @see CallVisitor
   */
  private interface CallVisitorCallback {
    public void visitCallSite(NodeTraversal t, Node callNode, FunctionState functionState);
  }

  /** Visit call sites for functions in functionMap. */
  private static class CallVisitor extends AbstractPostOrderCallback {

    protected CallVisitorCallback callback;
    private final Map<String, FunctionState> functionMap;
    private final Map<Node, String> anonFunctionMap;

    CallVisitor(
        Map<String, FunctionState> fns,
        Map<Node, String> anonFns,
        @Nullable CallVisitorCallback callback) {
      this.functionMap = fns;
      this.anonFunctionMap = anonFns;
      this.callback = callback;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case OPTCHAIN_CALL, CALL -> {
          // Function calls
          Node child = n.getFirstChild();
          String name = null;
          // NOTE: The normalization pass ensures that local names do not collide with global names.
          if (child.isName()) {
            name = child.getString();
          } else if (child.isFunction()) {
            name = anonFunctionMap.get(child);
          } else if (NodeUtil.isFunctionObjectCall(n)) {
            checkState(NodeUtil.isNormalOrOptChainGet(child));
            Node fnIdentifyingNode = child.getFirstChild();
            if (fnIdentifyingNode.isName()) {
              name = fnIdentifyingNode.getString();
            } else if (fnIdentifyingNode.isFunction()) {
              name = anonFunctionMap.get(fnIdentifyingNode);
            }
          }

          if (name != null) {
            FunctionState functionState = functionMap.get(name);

            // Only visit call-sites for functions that can be inlined.
            if (functionState != null) {
              callback.visitCallSite(t, n, functionState);
            }
          }
        }
        default -> {}
      }
    }
  }

  /**
   * @return Whether the name is used in a way that might be a candidate for inlining.
   */
  static boolean isCandidateUsage(Node name) {
    Node parent = name.getParent();
    checkState(name.isName());
    if (NodeUtil.isNameDeclaration(parent) || parent.isFunction()) {
      // This is a declaration.  Duplicate declarations are handle during
      // function candidate gathering.
      return true;
    }

    if (NodeUtil.isNormalOrOptChainCall(parent) && parent.getFirstChild() == name) {
      // This is a normal reference to the function.
      return true;
    }

    // Check for a ".call" to the named function:
    //   CALL
    //     GETPROP/GETELEM
    //       NAME
    //       STRING == "call"
    //     This-Value
    //     Function-parameter-1
    //     ...
    if ((parent.isGetElem()
            && name == parent.getFirstChild()
            && parent.getSecondChild().isStringLit()
            && parent.getSecondChild().getString().equals("call"))
        || (parent.isGetProp() && parent.getString().equals("call"))) {
      Node grandparent = name.getAncestor(2);
      if (grandparent.isCall() && grandparent.getFirstChild() == parent) {
        // Yep, a ".call".
        return true;
      }
    }
    return false;
  }

  /** Find references to functions that are inlinable. */
  private class FindCandidatesReferences extends CallVisitor implements CallVisitorCallback {
    FindCandidatesReferences(Map<String, FunctionState> fns, Map<Node, String> anonFns) {
      super(fns, anonFns, null);
      this.callback = this;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      super.visit(t, n, parent);
      if (n.isName()) {
        checkNameUsage(n, parent);
      }
    }

    @Override
    public void visitCallSite(NodeTraversal t, Node callNode, FunctionState functionState) {
      maybeAddReference(t, functionState, callNode, t.getChunk());
    }

    void maybeAddReference(
        NodeTraversal t, FunctionState functionState, Node callNode, JSChunk chunk) {
      if (!functionState.canInline()) {
        return;
      }

      InliningMode mode =
          functionState.canInlineDirectly() ? InliningMode.DIRECT : InliningMode.BLOCK;
      boolean referenceAdded = maybeAddReferenceUsingMode(t, functionState, callNode, chunk, mode);
      if (!referenceAdded && mode == InliningMode.DIRECT) {
        // This reference can not be directly inlined, see if
        // block replacement inlining is possible.
        mode = InliningMode.BLOCK;
        referenceAdded = maybeAddReferenceUsingMode(t, functionState, callNode, chunk, mode);
      }

      if (!referenceAdded) {
        // Don't try to remove a function if we can't inline all
        // the references.
        functionState.setRemove(
            false,
            compiler,
            CannotRemoveReason.UNINLINABLE_REFERENCE,
            ShouldWarnWhenRequireInliningCannotInline.NO,
            /* contextNode= */ callNode);
      }
    }

    private boolean maybeAddReferenceUsingMode(
        NodeTraversal t,
        FunctionState functionState,
        Node callNode,
        JSChunk chunk,
        InliningMode mode) {

      // If many functions are inlined into the same function F in the same
      // inlining round, then the size of F may exceed the max size.
      // This could be avoided if we bail later, during the inlining phase, eg,
      // in Inline#visitCallSite. However, that is not safe, because at that
      // point expression decomposition has already run, and we want to
      // decompose expressions only for the calls that are actually inlined.
      if (enforceMaxSizeAfterInlining && targetSizeAfterInlineExceedsLimit(t, functionState)) {
        return false;
      }

      Reference candidate = new Reference(callNode, t.getScope(), chunk, mode);
      CanInlineResult result =
          injector.canInlineReferenceToFunction(
              candidate,
              functionState.getFn().getFunctionNode(),
              functionState.getNamesToAlias(),
              functionState.getReferencesThis(),
              functionState.hasInnerFunctions());
      if (result != CanInlineResult.NO) {
        // Yeah!
        candidate.setRequiresDecomposition(result == CanInlineResult.AFTER_PREPARATION);
        functionState.addReference(candidate);
        return true;
      }

      return false;
    }

    /** Find functions that can be inlined. */
    private void checkNameUsage(Node n, Node parent) {
      checkState(n.isName(), n);

      if (isCandidateUsage(n)) {
        return;
      }

      // Other refs to a function name remove its candidacy for inlining
      String name = n.getString();
      FunctionState functionState = fns.get(name);
      if (functionState == null) {
        return;
      }

      // If the name is being assigned to it can not be inlined.
      if (parent.isAssign() && parent.getFirstChild() == n) {
        // e.g. bar = something; <== we can't inline "bar"
        // so mark the function as uninlinable.
        // TODO(johnlenz): Should we just remove it from fns here?
        functionState.disallowInlining(compiler, DisallowInliningReason.REASSIGNED);
      } else if ((parent.isAssign()
              && parent.getJSDocInfo() != null
              && parent.getJSDocInfo().isConstant()
              && parent.getSecondChild() == n)
          || (parent.getParent() != null
              && parent.getParent().isConst()
              && parent.getFirstChild() == n)) {
        // e.g. const bar = foo; exports.bar = foo;
        // We can't see through this reference right now, but we will be able
        // to once `bar` is inlined; so we shouldn't remove `foo` right now but
        // this is probably okay with @requireInlining.
        functionState.setRemove(
            false,
            compiler,
            CannotRemoveReason.UNREMOVABLE_REFERENCE,
            ShouldWarnWhenRequireInliningCannotInline.NO);
      } else {
        // e.g. var fn = bar; <== we can't inline "bar"
        // As this reference can't be inlined mark the function as
        // unremovable.
        functionState.setRemove(
            false,
            compiler,
            CannotRemoveReason.UNREMOVABLE_REFERENCE,
            ShouldWarnWhenRequireInliningCannotInline.YES,
            /* contextNode= */ parent);
      }
    }
  }

  /** Inline functions at the call sites. */
  private static class Inline implements CallVisitorCallback {
    private final FunctionInjector injector;

    Inline(FunctionInjector injector) {
      this.injector = injector;
    }

    @Override
    public void visitCallSite(NodeTraversal t, Node callNode, FunctionState functionState) {
      checkState(functionState.hasExistingFunctionDefinition());
      if (functionState.canInline()) {
        Reference ref = functionState.getReference(callNode);

        // There are two cases ref can be null: if the call site was introduced
        // because it was part of a function that was inlined during this pass
        // or if the call site was trimmed from the list of references because
        // the function couldn't be inlined at this location.
        if (ref != null) {
          inlineFunction(t, ref, functionState);
          // Keep track of references that have been inlined so that
          // we can verify that none have been missed.
          ref.inlined = true;
        }
      }
    }

    /** Inline a function into the call site. */
    private void inlineFunction(NodeTraversal t, Reference ref, FunctionState functionState) {
      Function fn = functionState.getFn();
      String fnName = fn.getName();
      Node fnNode = functionState.getSafeFnNode();

      Node newExpr = injector.inline(ref, fnName, fnNode);
      if (!newExpr.equals(ref.callNode)) {
        t.getCompiler().reportChangeToEnclosingScope(newExpr);
      }
    }
  }

  /** Remove entries that aren't a valid inline candidates, from the list of encountered names. */
  private void trimCandidatesNotMeetingMinimumRequirements() {
    Iterator<Entry<String, FunctionState>> i;
    for (i = fns.entrySet().iterator(); i.hasNext(); ) {
      FunctionState functionState = i.next().getValue();
      if (!functionState.hasExistingFunctionDefinition() || !functionState.canInline()) {
        i.remove();
      }
    }
  }

  /** Remove entries from the list of candidates that can't be inlined. */
  private void trimCandidatesUsingOnCost() {
    Iterator<Entry<String, FunctionState>> i;
    for (i = fns.entrySet().iterator(); i.hasNext(); ) {
      FunctionState functionState = i.next().getValue();
      if (functionState.hasReferences()) {
        // Only inline function if it decreases the code size.
        boolean lowersCost = minimizeCost(functionState);
        if (!lowersCost) {
          // It shouldn't be inlined; remove it from the list.
          i.remove();
        }
      } else if (!functionState.canRemove()) {
        // Don't bother tracking functions without references that can't be
        // removed.
        i.remove();
      }
    }
  }

  /**
   * Determines if the function is worth inlining and potentially trims references that increase the
   * cost.
   *
   * @return Whether inlining the references lowers the overall cost.
   */
  private boolean minimizeCost(FunctionState functionState) {
    if (!inliningLowersCost(functionState)) {
      // Try again without Block inlining references
      if (functionState.hasBlockInliningReferences()) {
        functionState.setRemove(
            false,
            compiler,
            CannotRemoveReason.BLOCK_INLINING_REFERENCE,
            ShouldWarnWhenRequireInliningCannotInline.YES);
        functionState.removeBlockInliningReferences();
        if (!functionState.hasReferences() || !inliningLowersCost(functionState)) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * @return Whether inlining the function reduces code size.
   */
  private boolean inliningLowersCost(FunctionState functionState) {
    return functionState.requireInlining()
        || functionState.encourageInlining()
        || injector.inliningLowersCost(
            functionState.getChunk(),
            functionState.getFn().getFunctionNode(),
            functionState.getReferences(),
            functionState.getNamesToAlias(),
            functionState.canRemove(),
            functionState.getReferencesThis());
  }

  /**
   * Size base inlining calculations are thrown off when a function that is being inlined also
   * contains calls to functions that are slated for inlining.
   *
   * <p>Specifically, a clone of the FUNCTION node tree is used when the function is inlined. Calls
   * in this new tree are not included in the list of function references so they won't be inlined
   * (which is what we want). Here we mark those functions as non-removable (as they will have new
   * references in the cloned node trees).
   *
   * <p>This prevents a function that would only be inlined because it is referenced once from being
   * inlined into multiple call sites because the calling function has been inlined in multiple
   * locations or the function being removed while there are still references.
   */
  private void resolveInlineConflicts() {
    for (FunctionState functionState : fns.values()) {
      resolveInlineConflictsForFunction(functionState);
    }
  }

  /**
   * @return Whether the function has any parameters that would stop the compiler from inlining.
   *     Currently this includes object patterns, array patterns, and default values.
   */
  private static boolean hasNonInlinableParam(Node node) {
    checkNotNull(node);

    Predicate<Node> pred = (Node input) -> input.isDefaultValue() || input.isDestructuringPattern();

    return NodeUtil.has(node, pred, alwaysTrue());
  }

  /**
   * @see #resolveInlineConflicts
   */
  private void resolveInlineConflictsForFunction(FunctionState functionState) {
    // Functions that aren't referenced don't cause conflicts.
    if (!functionState.hasReferences() || !functionState.canInline()) {
      return;
    }

    Node fnNode = functionState.getFn().getFunctionNode();
    Set<String> names = findCalledFunctions(fnNode);
    if (!names.isEmpty()) {
      // Prevent the removal of the referenced functions unless they will be immediately inlined.
      for (String name : names) {
        FunctionState fsCalled = fns.get(name);
        if (fsCalled != null && fsCalled.canRemove()) {
          fsCalled.setRemove(
              false,
              compiler,
              CannotRemoveReason.CALLED_BY_INLINED_FUNCTION,
              ShouldWarnWhenRequireInliningCannotInline.NO);
          // For functions that can no longer be removed, check if they should
          // still be inlined.
          if (!minimizeCost(fsCalled)
              && !fsCalled.requireInlining()
              && !fsCalled.encourageInlining()) {
            // It can't be inlined remove it from the list.
            fsCalled.disallowInlining(compiler, DisallowInliningReason.COST);
          }
        }
      }

      // Make a copy of the Node, so it isn't changed by other inlines.
      functionState.setSafeFnNode(functionState.getFn().getFunctionNode().cloneTree());
    }
  }

  /** This functions that may be called directly. */
  private Set<String> findCalledFunctions(Node node) {
    Set<String> changed = new LinkedHashSet<>();
    findCalledFunctions(NodeUtil.getFunctionBody(node), changed);
    return changed;
  }

  /**
   * @see #findCalledFunctions(Node)
   */
  private static void findCalledFunctions(Node node, Set<String> changed) {
    checkArgument(changed != null);
    // For each referenced function, add a new reference
    if (node.isName() && isCandidateUsage(node)) {
      changed.add(node.getString());
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      findCalledFunctions(c, changed);
    }
  }

  /**
   * For any call-site that needs it, prepare the call-site for inlining by rewriting the containing
   * expression.
   */
  private void decomposeExpressions() {
    for (FunctionState functionState : fns.values()) {
      if (functionState.canInline()) {
        for (Reference ref : functionState.getReferences()) {
          if (ref.requiresDecomposition) {
            injector.maybePrepareCall(ref);
          }
        }
      }
    }
  }

  /** Removed inlined functions that no longer have any references. */
  void removeInlinedFunctions() {
    for (Map.Entry<String, FunctionState> entry : fns.entrySet()) {
      String name = entry.getKey();
      FunctionState functionState = entry.getValue();
      if (functionState.canRemove()) {
        Function fn = functionState.getFn();
        checkState(functionState.canInline());
        checkState(fn != null);
        verifyAllReferencesInlined(name, functionState);
        fn.remove();
        NodeUtil.markFunctionsDeleted(fn.getFunctionNode(), compiler);
      }
    }
  }

  /** Check to verify that expression rewriting didn't make a call inaccessible. */
  void verifyAllReferencesInlined(String name, FunctionState functionState) {
    for (Reference ref : functionState.getReferences()) {
      if (!ref.inlined) {
        Node parent = ref.callNode.getParent();
        throw new IllegalStateException(
            "Call site missed ("
                + name
                + ").\n call: "
                + ref.callNode.toStringTree()
                + "\n parent:  "
                + ((parent == null) ? "null" : parent.toStringTree()));
      }
    }
  }

  /** Use to track the decisions that have been made about a function. */
  private static class FunctionState {
    private @Nullable Function fn = null;
    private @Nullable Node safeFnNode = null;
    private boolean inline = true;
    private boolean remove = true;
    private boolean inlineDirectly = false;
    private boolean referencesThis = false;
    private boolean hasInnerFunctions = false;
    private @Nullable Map<Node, Reference> references = null;
    private @Nullable JSChunk chunk = null;
    private @Nullable Set<String> namesToAlias = null;

    private boolean hasRequireInliningAnnotation = false;
    private boolean hasRequireInliningAnnotationInitialized = false;

    private boolean hasEncourageInliningAnnotation = false;
    private boolean hasEncourageInliningAnnotationInitialized = false;

    boolean hasExistingFunctionDefinition() {
      return (fn != null);
    }

    public void setReferencesThis(boolean referencesThis) {
      this.referencesThis = referencesThis;
    }

    public boolean getReferencesThis() {
      return this.referencesThis;
    }

    public void setHasInnerFunctions(boolean hasInnerFunctions) {
      this.hasInnerFunctions = hasInnerFunctions;
    }

    public boolean hasInnerFunctions() {
      return hasInnerFunctions;
    }

    void removeBlockInliningReferences() {
      Iterator<Entry<Node, Reference>> i;
      for (i = getReferencesInternal().entrySet().iterator(); i.hasNext(); ) {
        Entry<Node, Reference> entry = i.next();
        if (entry.getValue().mode == InliningMode.BLOCK) {
          i.remove();
        }
      }
    }

    public boolean hasBlockInliningReferences() {
      for (Reference r : getReferencesInternal().values()) {
        if (r.mode == InliningMode.BLOCK) {
          return true;
        }
      }
      return false;
    }

    public Function getFn() {
      return fn;
    }

    public void setFn(Function fn) {
      checkState(this.fn == null);
      this.fn = fn;
    }

    public Node getSafeFnNode() {
      return (safeFnNode != null) ? safeFnNode : fn.getFunctionNode();
    }

    public void setSafeFnNode(Node safeFnNode) {
      this.safeFnNode = safeFnNode;
    }

    public boolean canInline() {
      return inline;
    }

    public boolean encourageInlining() {
      if (fn == null) {
        return false;
      }
      if (!hasEncourageInliningAnnotationInitialized) {
        hasEncourageInliningAnnotationInitialized = true;
        hasEncourageInliningAnnotation = hasEncourageInliningAnnotation(fn.getFunctionNode());
      }
      return hasEncourageInliningAnnotation;
    }

    public boolean requireInlining() {
      if (fn == null) {
        return false;
      }
      if (!hasRequireInliningAnnotationInitialized) {
        hasRequireInliningAnnotationInitialized = true;
        hasRequireInliningAnnotation = hasRequireInliningAnnotation(fn.getFunctionNode());
      }
      return hasRequireInliningAnnotation;
    }

    public void disallowInlining(AbstractCompiler compiler, DisallowInliningReason reason) {
      disallowInlining(compiler, reason, ShouldWarnWhenRequireInliningCannotInline.YES);
    }

    public void disallowInlining(
        AbstractCompiler compiler,
        DisallowInliningReason reason,
        ShouldWarnWhenRequireInliningCannotInline shouldWarnWhenRequireInliningCannotInline) {
      if (requireInlining()
          && compiler.isDebugLoggingEnabled()
          && shouldWarnWhenRequireInliningCannotInline
              == ShouldWarnWhenRequireInliningCannotInline.YES) {
        compiler.report(
            JSError.make(fn.getFunctionNode(), MISSED_REQUIRED_INLINING, reason.name()));
      }
      this.inline = false;

      // No need to keep references to function that can't be inlined.
      references = null;
      // Don't remove functions that we aren't inlining.
      remove = false;
    }

    public boolean canRemove() {
      return remove;
    }

    public void setRemove(
        boolean remove,
        AbstractCompiler compiler,
        CannotRemoveReason reason,
        ShouldWarnWhenRequireInliningCannotInline shouldWarnIfRequireInlining) {
      setRemove(remove, compiler, reason, shouldWarnIfRequireInlining, /* contextNode= */ null);
    }

    public void setRemove(
        boolean remove,
        AbstractCompiler compiler,
        CannotRemoveReason reason,
        ShouldWarnWhenRequireInliningCannotInline shouldWarnIfRequireInlining,
        @Nullable Node contextNode) {
      if (!remove
          && (shouldWarnIfRequireInlining != ShouldWarnWhenRequireInliningCannotInline.NO)
          && requireInlining()
          && compiler.isDebugLoggingEnabled()) {
        compiler.report(
            JSError.make(
                fn.getFunctionNode(),
                MISSED_REQUIRED_INLINING,
                reason.name(),
                contextNode == null ? "unknown" : contextNode.toString()));
      }
      this.remove = remove;
    }

    public boolean canInlineDirectly() {
      return inlineDirectly;
    }

    public void inlineDirectly(boolean directReplacement) {
      this.inlineDirectly = directReplacement;
    }

    public boolean hasReferences() {
      return (references != null && !references.isEmpty());
    }

    private Map<Node, Reference> getReferencesInternal() {
      if (references == null) {
        return ImmutableMap.of();
      }
      return references;
    }

    public void addReference(Reference ref) {
      if (references == null) {
        references = new LinkedHashMap<>();
      }
      references.put(ref.callNode, ref);
    }

    public Collection<Reference> getReferences() {
      return getReferencesInternal().values();
    }

    public Reference getReference(Node n) {
      return getReferencesInternal().get(n);
    }

    public ImmutableSet<String> getNamesToAlias() {
      if (namesToAlias == null) {
        return ImmutableSet.of();
      }
      return ImmutableSet.copyOf(namesToAlias);
    }

    public void setNamesToAlias(Set<String> names) {
      namesToAlias = names;
    }

    public void setChunk(JSChunk chunk) {
      this.chunk = chunk;
    }

    public JSChunk getChunk() {
      return chunk;
    }
  }

  /** Interface for dealing with function declarations and function expressions equally */
  private static interface Function {
    /** Gets the name of the function */
    public String getName();

    /** Gets the name node of the function */
    public Node getNameNode();

    /** Gets the function node */
    public Node getFunctionNode();

    /** Removes itself from the JavaScript */
    public void remove();

    public Node getDeclaringBlock();
  }

  /** NamedFunction implementation of the Function interface */
  private class NamedFunction implements Function {
    private final Node fn;

    public NamedFunction(Node fn) {
      this.fn = fn;
    }

    @Override
    public String getName() {
      return fn.getFirstChild().getString();
    }

    @Override
    public Node getNameNode() {
      return fn.getFirstChild();
    }

    @Override
    public Node getFunctionNode() {
      return fn;
    }

    @Override
    public void remove() {
      compiler.reportChangeToEnclosingScope(fn);
      NodeUtil.removeChild(fn.getParent(), fn);
      NodeUtil.markFunctionsDeleted(fn, compiler);
    }

    @Override
    public Node getDeclaringBlock() {
      return fn.getParent();
    }
  }

  /** FunctionVar implementation of the Function interface */
  private class FunctionVar implements Function {
    private final Node var;

    public FunctionVar(Node var) {
      this.var = var;
    }

    @Override
    public String getName() {
      return var.getFirstChild().getString();
    }

    @Override
    public Node getNameNode() {
      return var.getFirstChild();
    }

    @Override
    public Node getFunctionNode() {
      return var.getFirstFirstChild();
    }

    @Override
    public void remove() {
      compiler.reportChangeToEnclosingScope(var);
      NodeUtil.removeChild(var.getParent(), var);
      NodeUtil.markFunctionsDeleted(var, compiler);
    }

    @Override
    public Node getDeclaringBlock() {
      return var.getParent();
    }
  }

  /** FunctionExpression implementation of the Function interface */
  private static class FunctionExpression implements Function {
    private final Node fn;
    private final String fakeName;
    private final Node fakeNameNode;

    public FunctionExpression(Node fn, int index) {
      this.fn = fn;
      // A number is not a valid function JavaScript identifier
      // so we don't need to worry about collisions.
      this.fakeName = String.valueOf(index);
      this.fakeNameNode = IR.name(fakeName);
    }

    @Override
    public String getName() {
      return fakeName;
    }

    @Override
    public Node getNameNode() {
      return fakeNameNode;
    }

    @Override
    public Node getFunctionNode() {
      return fn;
    }

    @Override
    public void remove() {
      // Nothing to do. The function is removed with the call.
    }

    @Override
    public Node getDeclaringBlock() {
      return null;
    }
  }

  static class Reference extends FunctionInjector.Reference {
    boolean requiresDecomposition = false;
    boolean inlined = false;

    Reference(Node callNode, Scope scope, JSChunk chunk, InliningMode mode) {
      super(callNode, scope, chunk, mode);
    }

    void setRequiresDecomposition(boolean newVal) {
      this.requiresDecomposition = newVal;
    }
  }

  static enum ShouldWarnWhenRequireInliningCannotInline {
    YES,
    NO;
  }

  static enum CannotRemoveReason {
    BLOCK_INLINING_REFERENCE,
    CALLED_BY_INLINED_FUNCTION,
    UNINLINABLE_REFERENCE,
    UNREMOVABLE_REFERENCE;
  }

  static enum DisallowInliningReason {
    ASYNC_FUNCTION,
    CAPTURED_VARIABLE_NOT_IN_SCOPE,
    COST,
    GENERATOR_FUNCTION,
    HAS_LOCAL_NAMES,
    MULTIPLE_FUNCTION_DEFINITIONS,
    NON_INLINABLE_PARAM,
    NOT_CANDIDATE_FUNCTION,
    NO_INLINE_ANNOTATION,
    REASSIGNED,
    TARGET_SIZE_EXCEEDS_LIMIT;
  }

  record ShouldRequireInlining(boolean shouldValidate, String optMessage) {}

  static ShouldRequireInlining checkOptimizationLevelSupportsRequireInlining(
      CompilerOptions options) {
    // Non-optimized code might not perform all required inlinings.
    List<String> problems = new ArrayList<>();
    if (!options.shouldOptimize()) {
      problems.add("non-optimized code");
    }
    // Required inlinings don't make sense without inlining. If we don't inline
    // properties we might not be able to inline functions defined on namespaces.
    if (!options.shouldInlineProperties()) {
      problems.add("missing shouldInlineProperties");
    }
    if (!options.shouldInlineConstantVars()) {
      problems.add("missing inlineConstantVars");
    }
    if (!options.getSmartNameRemoval()) {
      problems.add("missing smartNameRemoval");
    }
    if (!options.getInlineFunctionsLevel().isOn()) {
      problems.add("missing inlineFunctions");
    }
    if (!options.getInlineFunctionsLevel().includesGlobals()) {
      problems.add("inlineFunctions does not include globals");
    }
    // Coverage runs may break some inlining.
    if (options.getInstrumentForCoverageOption() != CompilerOptions.InstrumentOption.NONE) {
      problems.add("instrumentForCoverageOption enabled");
    }
    // We need to collapse properties or functions might be retained due to
    // being written on an exported namespace.
    if (!options.getPropertyCollapseLevel().equals(CompilerOptions.PropertyCollapseLevel.ALL)) {
      problems.add("missing property collapsing");
    }
    // Avoid inlining failures due to asserts.
    if (!options.shouldRemoveClosureAsserts()) {
      problems.add("missing removeClosureAsserts");
    }
    // Avoid inlining failures due to being unable to devirtualize a method.
    if (!options.shouldDevirtualizeMethods()) {
      problems.add("missing devirtualizeMethods");
    }
    // We need more than one inlining pass for most required inlinings to work. This
    // eliminates 'fast' mode.
    if (options.getMaxOptimizationLoopIterations() == 1) {
      problems.add(
          "optimizationLoopMaxIterations should be >= 2, but is: "
              + options.getMaxOptimizationLoopIterations());
    }
    if (problems.isEmpty()) {
      return new ShouldRequireInlining(true, "");
    }
    return new ShouldRequireInlining(false, String.format("[%s]", String.join(", ", problems)));
  }
}
