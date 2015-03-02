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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.FunctionInjector.CanInlineResult;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Inlines functions that are divided into two types: "direct call node
 * replacement" (aka "direct") and as a block of statements (aka block).
 * Function that can be inlined "directly" functions consist of a single
 * return statement, everything else is must be inlined as a "block". These
 * functions must meet these general requirements:
 * - it is not recursive
 * - the function does not contain another function -- these may be
 *   intentional to to limit the scope of closures.
 * - function is called only once OR the size of the inline function is smaller
 *   than the call itself.
 * - the function name is not referenced in any other manner
 *
 * "directly" inlined functions must meet these additional requirements:
 * - consists of a single return statement
 *
 * @author johnlenz@google.com (John Lenz)
 */
class InlineFunctions implements CompilerPass {

  // TODO(nicksantos): This needs to be completely rewritten to use scopes
  // to do variable lookups. Right now, it assumes that all functions are
  // uniquely named variables. There's currently a stopgap scope-check
  // to ensure that this doesn't produce invalid code. But in the long run,
  // this needs a major refactor.
  private final Map<String, FunctionState> fns = new LinkedHashMap<>();
  private final Map<Node, String> anonFns = new HashMap<>();

  private final AbstractCompiler compiler;

  private final FunctionInjector injector;

  private final boolean blockFunctionInliningEnabled;
  private final boolean inlineGlobalFunctions;
  private final boolean inlineLocalFunctions;
  private final boolean assumeMinimumCapture;

  private final boolean enforceMaxSizeAfterInlining;
  private final int maxSizeAfterInlining;

  InlineFunctions(AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      boolean inlineGlobalFunctions,
      boolean inlineLocalFunctions,
      boolean blockFunctionInliningEnabled,
      boolean assumeStrictThis,
      boolean assumeMinimumCapture,
      int maxSizeAfterInlining) {
    Preconditions.checkArgument(compiler != null);
    Preconditions.checkArgument(safeNameIdSupplier != null);
    this.compiler = compiler;

    this.inlineGlobalFunctions = inlineGlobalFunctions;
    this.inlineLocalFunctions = inlineLocalFunctions;
    this.blockFunctionInliningEnabled = blockFunctionInliningEnabled;
    this.assumeMinimumCapture = assumeMinimumCapture;

    this.maxSizeAfterInlining = maxSizeAfterInlining;
    this.enforceMaxSizeAfterInlining =
        maxSizeAfterInlining != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;

    this.injector = new FunctionInjector(
        compiler, safeNameIdSupplier, true, assumeStrictThis, assumeMinimumCapture);
  }

  FunctionState getOrCreateFunctionState(String fnName) {
    FunctionState fs = fns.get(fnName);
    if (fs == null) {
      fs = new FunctionState();
      fns.put(fnName, fs);
    }
    return fs;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());

    NodeTraversal.traverse(compiler, root, new FindCandidateFunctions());
    if (fns.isEmpty()) {
      return;  // Nothing left to do.
    }
    NodeTraversal.traverse(compiler, root,
       new FindCandidatesReferences(fns, anonFns));
    trimCandidatesNotMeetingMinimumRequirements();
    if (fns.isEmpty()) {
      return;  // Nothing left to do.
    }

    // Store the set of function names eligible for inlining and use this to
    // prevent function names from being moved into temporaries during
    // expression decomposition. If this movement were allowed it would prevent
    // the Inline callback from finding the function calls.
    //
    // This pass already assumes these are constants, so this is safe for anyone
    // using function inlining.
    //
    Set<String> fnNames = Sets.newHashSet(fns.keySet());
    injector.setKnownConstants(fnNames);

    trimCandidatesUsingOnCost();
    if (fns.isEmpty()) {
      return;  // Nothing left to do.
    }
    resolveInlineConflicts();
    decomposeExpressions();
    NodeTraversal.traverse(compiler, root,
        new CallVisitor(fns, anonFns, new Inline(injector)));

    removeInlinedFunctions();
  }

  private static boolean isAlwaysInlinable(Node fn) {
    Preconditions.checkArgument(fn.isFunction());
    Node body = NodeUtil.getFunctionBody(fn);
    int numOfStmsInBody = body.getChildCount();
    return numOfStmsInBody == 0
        || numOfStmsInBody == 1 && body.getFirstChild().isReturn();
  }

  private boolean targetSizeAfterInlineExceedsLimit(
      NodeTraversal t, FunctionState fs) {
    Node containingFunction = getContainingFunction(t);
    // Always inline at the top level,
    // unless maybeAddFunction has marked fs as not inlinable.
    if (containingFunction == null) {
      return false;
    }
    Node inlinedFun = fs.getFn().getFunctionNode();
    if (isAlwaysInlinable(inlinedFun)) {
      return false;
    }
    int inlinedFunSize = NodeUtil.countAstSizeUpToLimit(
        NodeUtil.getFunctionBody(inlinedFun), maxSizeAfterInlining);
    int targetFunSize = NodeUtil.countAstSizeUpToLimit(
        containingFunction, maxSizeAfterInlining);
    return inlinedFunSize + targetFunSize > maxSizeAfterInlining;
  }

  /**
   * Find functions that might be inlined.
   */
  private class FindCandidateFunctions implements Callback {
    private int callsSeen = 0;

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      // Don't traverse into function bodies
      // if we aren't inlining local functions.
      return inlineLocalFunctions || nodeTraversal.inGlobalScope();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if ((t.inGlobalScope() && inlineGlobalFunctions)
          || (!t.inGlobalScope() && inlineLocalFunctions)) {
        findNamedFunctions(t, n, parent);

        findFunctionExpressions(t, n);
      }
    }

    public void findNamedFunctions(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isStatement(n)) {
        // There aren't any interesting functions here.
        return;
      }

      switch (n.getType()) {
        // Functions expressions in the form of:
        //   var fooFn = function(x) { return ... }
        case Token.VAR:
          Preconditions.checkState(n.hasOneChild());
          Node nameNode = n.getFirstChild();
          if (nameNode.isName() && nameNode.hasChildren()
              && nameNode.getFirstChild().isFunction()) {
            maybeAddFunction(new FunctionVar(n), t.getModule());
          }
          break;

        // Named functions
        // function Foo(x) { return ... }
        case Token.FUNCTION:
          Preconditions.checkState(NodeUtil.isStatementBlock(parent)
              || parent.isLabel());
          if (!NodeUtil.isFunctionExpression(n)) {
            Function fn = new NamedFunction(n);
            maybeAddFunction(fn, t.getModule());
          }
          break;
      }
    }

    /**
     * Find function expressions that are called directly in the form of
     *   (function(a,b,...){...})(a,b,...)
     * or
     *   (function(a,b,...){...}).call(this,a,b, ...)
     */
    public void findFunctionExpressions(NodeTraversal t, Node n) {
      switch (n.getType()) {
        // Functions expressions in the form of:
        //   (function(){})();
        case Token.CALL:
          Node fnNode = null;
          if (n.getFirstChild().isFunction()) {
            fnNode = n.getFirstChild();
          } else if (NodeUtil.isFunctionObjectCall(n)) {
            Node fnIdentifingNode = n.getFirstChild().getFirstChild();
            if (fnIdentifingNode.isFunction()) {
              fnNode = fnIdentifingNode;
            }
          }

          // If a interesting function was discovered, add it.
          if (fnNode != null) {
            Function fn = new FunctionExpression(fnNode, callsSeen++);
            maybeAddFunction(fn, t.getModule());
            anonFns.put(fnNode, fn.getName());
          }
          break;
      }
    }
  }

  /**
   * Updates the FunctionState object for the given function. Checks if the
   * given function matches the criteria for an inlinable function.
   */
  private void maybeAddFunction(Function fn, JSModule module) {
    String name = fn.getName();
    FunctionState fs = getOrCreateFunctionState(name);

    // TODO(johnlenz): Maybe "smarten" FunctionState by adding this logic
    // to it?

    // If the function has multiple definitions, don't inline it.
    if (fs.hasExistingFunctionDefinition()) {
      fs.setInline(false);
      return;
    }
    Node fnNode = fn.getFunctionNode();
    if (enforceMaxSizeAfterInlining
        && !isAlwaysInlinable(fnNode)
        && maxSizeAfterInlining
        <= NodeUtil.countAstSizeUpToLimit(fnNode, maxSizeAfterInlining)) {
      fs.setInline(false);
      return;
    }
    // verify the function hasn't already been marked as "don't inline"
    if (fs.canInline()) {
      // store it for use when inlining.
      fs.setFn(fn);
      if (FunctionInjector.isDirectCallNodeReplacementPossible(
          fn.getFunctionNode())) {
        fs.inlineDirectly(true);
      }

      // verify the function meets all the requirements.
      // TODO(johnlenz): Minimum requirement checks are about 5% of the
      // run-time cost of this pass.
      if (!isCandidateFunction(fn)) {
        // It doesn't meet the requirements.
        fs.setInline(false);
      }

      // Set the module and gather names that need temporaries.
      if (fs.canInline()) {
        fs.setModule(module);

        Set<String> namesToAlias =
            FunctionArgumentInjector.findModifiedParameters(fnNode);
        if (!namesToAlias.isEmpty()) {
          fs.inlineDirectly(false);
          fs.setNamesToAlias(namesToAlias);
        }

        Node block = NodeUtil.getFunctionBody(fnNode);
        if (NodeUtil.referencesThis(block)) {
          fs.setReferencesThis(true);
        }

        if (NodeUtil.containsFunction(block)) {
          fs.setHasInnerFunctions(true);
          // If there are inner functions, we can inline into global scope
          // if there are no local vars or named functions.
          // TODO(johnlenz): this can be improved by looking at the possible
          // values for locals.  If there are simple values, or constants
          // we could still inline.
          if (!assumeMinimumCapture && hasLocalNames(fnNode)) {
            fs.setInline(false);
          }
        }
      }

      // Check if block inlining is allowed.
      if (fs.canInline() && !fs.canInlineDirectly()) {
        if (!blockFunctionInliningEnabled) {
          fs.setInline(false);
        }
      }
    }
  }

  /**
   * @param fnNode The function to inspect.
   * @return Whether the function has parameters, var, or function declarations.
   */
  private static boolean hasLocalNames(Node fnNode) {
    Node block = NodeUtil.getFunctionBody(fnNode);
    return NodeUtil.getFunctionParameters(fnNode).hasChildren()
        || NodeUtil.has(
             block,
             new NodeUtil.MatchDeclaration(),
             new NodeUtil.MatchShallowStatement());
  }

  /**
   * Returns the function the traversal is currently traversing, or null
   * if in the global scope.
   */
  private static Node getContainingFunction(NodeTraversal t) {
    return (t.inGlobalScope()) ? null : t.getScopeRoot();
  }

  /**
   * Checks if the given function matches the criteria for an inlinable
   * function.
   */
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
    if (RenameProperties.RENAME_PROPERTY_FUNCTION_NAME.equals(fnName)) {
      return false;
    }

    Node fnNode = fn.getFunctionNode();
    return injector.doesFunctionMeetMinimumRequirements(fnName, fnNode);
  }

  /**
   * @see CallVisitor
   */
  private interface CallVisitorCallback {
    public void visitCallSite(
        NodeTraversal t, Node callNode, FunctionState fs);
  }

  /**
   * Visit call sites for functions in functionMap.
   */
  private static class CallVisitor extends AbstractPostOrderCallback {

    protected CallVisitorCallback callback;
    private Map<String, FunctionState> functionMap;
    private Map<Node, String> anonFunctionMap;

    CallVisitor(Map<String, FunctionState> fns,
                Map<Node, String> anonFns,
                CallVisitorCallback callback) {
      this.functionMap = fns;
      this.anonFunctionMap = anonFns;
      this.callback = callback;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        // Function calls
        case Token.CALL:
          Node child = n.getFirstChild();
          String name = null;
          // NOTE: The normalization pass insures that local names do not
          // collide with global names.
          if (child.isName()) {
            name = child.getString();
          } else if (child.isFunction()) {
            name = anonFunctionMap.get(child);
          } else if (NodeUtil.isFunctionObjectCall(n)) {
            Preconditions.checkState(NodeUtil.isGet(child));
            Node fnIdentifingNode = child.getFirstChild();
            if (fnIdentifingNode.isName()) {
              name = fnIdentifingNode.getString();
            } else if (fnIdentifingNode.isFunction()) {
              name = anonFunctionMap.get(fnIdentifingNode);
            }
          }

          if (name != null) {
            FunctionState fs = functionMap.get(name);

            // Only visit call-sites for functions that can be inlined.
            if (fs != null) {
              callback.visitCallSite(t, n, fs);
            }
          }
          break;
      }
    }
  }

  /**
   * @return Whether the name is used in a way that might be a candidate
   *   for inlining.
   */
  static boolean isCandidateUsage(Node name) {
    Node parent = name.getParent();
    Preconditions.checkState(name.isName());
    if (parent.isVar() || parent.isFunction()) {
      // This is a declaration.  Duplicate declarations are handle during
      // function candidate gathering.
      return true;
    }

    if (parent.isCall() && parent.getFirstChild() == name) {
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
    if (NodeUtil.isGet(parent)
         && name == parent.getFirstChild()
         && name.getNext().isString()
         && name.getNext().getString().equals("call")) {
      Node gramps = name.getAncestor(2);
      if (gramps.isCall()
          && gramps.getFirstChild() == parent) {
        // Yep, a ".call".
        return true;
      }
    }
    return false;
  }

  /**
   * Find references to functions that are inlinable.
   */
  private class FindCandidatesReferences
      extends CallVisitor
      implements CallVisitorCallback {
    FindCandidatesReferences(
        Map<String, FunctionState> fns,
        Map<Node, String> anonFns) {
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
    public void visitCallSite(
        NodeTraversal t, Node callNode, FunctionState fs) {
      maybeAddReference(t, fs, callNode, t.getModule());
    }

    void maybeAddReference(NodeTraversal t, FunctionState fs,
        Node callNode, JSModule module) {
      if (!fs.canInline()) {
        return;
      }

      InliningMode mode = fs.canInlineDirectly()
           ? InliningMode.DIRECT : InliningMode.BLOCK;
      boolean referenceAdded = maybeAddReferenceUsingMode(t, fs, callNode, module, mode);
      if (!referenceAdded &&
          mode == InliningMode.DIRECT && blockFunctionInliningEnabled) {
        // This reference can not be directly inlined, see if
        // block replacement inlining is possible.
        mode = InliningMode.BLOCK;
        referenceAdded = maybeAddReferenceUsingMode(
            t, fs, callNode, module, mode);
      }

      if (!referenceAdded) {
        // Don't try to remove a function if we can't inline all
        // the references.
        fs.setRemove(false);
      }
    }

    private boolean maybeAddReferenceUsingMode(
        NodeTraversal t, FunctionState fs, Node callNode,
        JSModule module, InliningMode mode) {

      // If many functions are inlined into the same function F in the same
      // inlining round, then the size of F may exceed the max size.
      // This could be avoided if we bail later, during the inlining phase, eg,
      // in Inline#visitCallSite. However, that is not safe, because at that
      // point expression decomposition has already run, and we want to
      // decompose expressions only for the calls that are actually inlined.
      if (enforceMaxSizeAfterInlining
          && targetSizeAfterInlineExceedsLimit(t, fs)) {
        return false;
      }

      Reference candidate = new Reference(callNode, t.getScope(), module, mode);
      CanInlineResult result = injector.canInlineReferenceToFunction(
          candidate, fs.getFn().getFunctionNode(),
          fs.getNamesToAlias(), fs.getReferencesThis(),
          fs.hasInnerFunctions());
      if (result != CanInlineResult.NO) {
        // Yeah!
        candidate.setRequiresDecomposition(
            result == CanInlineResult.AFTER_PREPARATION);
        fs.addReference(candidate);
        return true;
      }

      return false;
    }

    /**
     * Find functions that can be inlined.
     */
    private void checkNameUsage(Node n, Node parent) {
      Preconditions.checkState(n.isName());

      if (isCandidateUsage(n)) {
        return;
      }

      // Other refs to a function name remove its candidacy for inlining
      String name = n.getString();
      FunctionState fs = fns.get(name);
      if (fs == null) {
        return;
      }

      // Unlike normal call/new parameters, references passed to
      // JSCompiler_ObjectPropertyString are not aliases of a value, but
      // a reference to the name itself, as such the value of the name is
      // unknown and can not be inlined.
      if (parent.isNew()) {
        Node target = parent.getFirstChild();
        if (target.isName() && target.getString().equals(
            ObjectPropertyStringPreprocess.EXTERN_OBJECT_PROPERTY_STRING)) {
          // This method is going to be replaced so don't inline it anywhere.
          fs.setInline(false);
        }
      }

      // If the name is being assigned to it can not be inlined.
      if (parent.isAssign() && parent.getFirstChild() == n) {
        // e.g. bar = something; <== we can't inline "bar"
        // so mark the function as uninlinable.
        // TODO(johnlenz): Should we just remove it from fns here?
        fs.setInline(false);
      } else {
        // e.g. var fn = bar; <== we can't inline "bar"
        // As this reference can't be inlined mark the function as
        // unremovable.
        fs.setRemove(false);
      }
    }
  }

  /**
   * Inline functions at the call sites.
   */
  private class Inline implements CallVisitorCallback {
    private final FunctionInjector injector;

    Inline(FunctionInjector injector) {
      this.injector = injector;
    }

    @Override
    public void visitCallSite(
        NodeTraversal t, Node callNode, FunctionState fs) {
      Preconditions.checkState(fs.hasExistingFunctionDefinition());
      if (fs.canInline()) {
        Reference ref = fs.getReference(callNode);

        // There are two cases ref can be null: if the call site was introduced
        // because it was part of a function that was inlined during this pass
        // or if the call site was trimmed from the list of references because
        // the function couldn't be inlined at this location.
        if (ref != null) {
          inlineFunction(t, ref, fs);
          // Keep track of references that have been inlined so that
          // we can verify that none have been missed.
          ref.inlined = true;
        }
      }
    }

    /**
     * Inline a function into the call site.
     */
    private void inlineFunction(
        NodeTraversal t, Reference ref, FunctionState fs) {
      Function fn = fs.getFn();
      String fnName = fn.getName();
      Node fnNode = fs.getSafeFnNode();

      Node newExpr = injector.inline(ref, fnName, fnNode);
      if (!newExpr.isEquivalentTo(ref.callNode)) {
        t.getCompiler().reportChangeToEnclosingScope(newExpr);
      }
      t.getCompiler().addToDebugLog("Inlined function: " + fn.getName());
    }
  }

  /**
   * Remove entries that aren't a valid inline candidates, from the list of
   * encountered names.
   */
  private void trimCandidatesNotMeetingMinimumRequirements() {
   Iterator<Entry<String, FunctionState>> i;
   for (i = fns.entrySet().iterator(); i.hasNext();) {
     FunctionState fs = i.next().getValue();
     if (!fs.hasExistingFunctionDefinition() || !fs.canInline()) {
       i.remove();
     }
   }
  }

  /**
   * Remove entries from the list of candidates that can't be inlined.
   */
  private void trimCandidatesUsingOnCost() {
    Iterator<Entry<String, FunctionState>> i;
    for (i = fns.entrySet().iterator(); i.hasNext();) {
      FunctionState fs = i.next().getValue();
      if (fs.hasReferences()) {
        // Only inline function if it decreases the code size.
        boolean lowersCost = mimimizeCost(fs);
        if (!lowersCost) {
          // It shouldn't be inlined; remove it from the list.
          i.remove();
        }
      } else if (!fs.canRemove()) {
        // Don't bother tracking functions without references that can't be
        // removed.
        i.remove();
      }
    }
  }

  /**
   * Determines if the function is worth inlining and potentially
   * trims references that increase the cost.
   * @return Whether inlining the references lowers the overall cost.
   */
  private boolean mimimizeCost(FunctionState fs) {
    if (!inliningLowersCost(fs)) {
      // Try again without Block inlining references
      if (fs.hasBlockInliningReferences()) {
        fs.setRemove(false);
        fs.removeBlockInliningReferences();
        if (!fs.hasReferences() || !inliningLowersCost(fs)) {
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
  private boolean inliningLowersCost(FunctionState fs) {
    return injector.inliningLowersCost(
        fs.getModule(),
        fs.getFn().getFunctionNode(),
        fs.getReferences(),
        fs.getNamesToAlias(),
        fs.canRemove(),
        fs.getReferencesThis());
  }


  /**
   * Size base inlining calculations are thrown off when a function that is
   * being inlined also contains calls to functions that are slated for
   * inlining.
   *
   * Specifically, a clone of the FUNCTION node tree is used when the function
   * is inlined. Calls in this new tree are not included in the list of function
   * references so they won't be inlined (which is what we want). Here we mark
   * those functions as non-removable (as they will have new references in the
   * cloned node trees).
   *
   * This prevents a function that would only be inlined because it is
   * referenced once from being inlined into multiple call sites because
   * the calling function has been inlined in multiple locations or the
   * function being removed while there are still references.
   */
  private void resolveInlineConflicts() {
    for (FunctionState fs : fns.values()) {
      resolveInlineConflictsForFunction(fs);
    }
  }

  /**
   * @see #resolveInlineConflicts
   */
  private void resolveInlineConflictsForFunction(FunctionState fs) {
    // Functions that aren't referenced don't cause conflicts.
    if (!fs.hasReferences() || !fs.canInline()) {
      return;
    }

    Node fnNode = fs.getFn().getFunctionNode();
    Set<String> names = findCalledFunctions(fnNode);
    if (!names.isEmpty()) {
      // Prevent the removal of the referenced functions.
      for (String name : names) {
        FunctionState fsCalled = fns.get(name);
        if (fsCalled != null && fsCalled.canRemove()) {
          fsCalled.setRemove(false);
          // For functions that can no longer be removed, check if they should
          // still be inlined.
          if (!mimimizeCost(fsCalled)) {
            // It can't be inlined remove it from the list.
            fsCalled.setInline(false);
          }
        }
      }

      // Make a copy of the Node, so it isn't changed by other inlines.
      fs.setSafeFnNode(fs.getFn().getFunctionNode().cloneTree());
    }
  }

  /**
   * This functions that may be called directly.
   */
  private Set<String> findCalledFunctions(Node node) {
    Set<String> changed = Sets.newHashSet();
    findCalledFunctions(NodeUtil.getFunctionBody(node), changed);
    return changed;
  }

  /**
   * @see #findCalledFunctions(Node)
   */
  private static void findCalledFunctions(
      Node node, Set<String> changed) {
    Preconditions.checkArgument(changed != null);
    // For each referenced function, add a new reference
    if (node.isName()) {
      if (isCandidateUsage(node)) {
        changed.add(node.getString());
      }
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      findCalledFunctions(c, changed);
    }
  }

  /**
   * For any call-site that needs it, prepare the call-site for inlining
   * by rewriting the containing expression.
   */
  private void decomposeExpressions() {
    for (FunctionState fs : fns.values()) {
      if (fs.canInline()) {
        for (Reference ref : fs.getReferences()) {
          if (ref.requiresDecomposition) {
            injector.maybePrepareCall(ref);
          }
        }
      }
    }
  }

  /**
   * Removed inlined functions that no longer have any references.
   */
  void removeInlinedFunctions() {
    for (FunctionState fs : fns.values()) {
      if (fs.canRemove()) {
        Function fn = fs.getFn();
        Preconditions.checkState(fs.canInline());
        Preconditions.checkState(fn != null);
        verifyAllReferencesInlined(fs);
        fn.remove();
      }
    }
  }

  /**
   * Sanity check to verify, that expression rewriting didn't
   * make a call inaccessible.
   */
  void verifyAllReferencesInlined(FunctionState fs) {
    for (Reference ref : fs.getReferences()) {
      if (!ref.inlined) {
        throw new IllegalStateException("Call site missed.\n call: "
            + ref.callNode.toStringTree() + "\n parent:  "
            + ref.callNode.getParent().toStringTree());
      }
    }
  }

  /**
   * Use to track the decisions that have been made about a function.
   */
  private static class FunctionState {
    private Function fn = null;
    private Node safeFnNode = null;
    private boolean inline = true;
    private boolean remove = true;
    private boolean inlineDirectly = false;
    private boolean referencesThis = false;
    private boolean hasInnerFunctions = false;
    private Map<Node, Reference> references = null;
    private JSModule module = null;
    private Set<String> namesToAlias = null;

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
      for (i = getReferencesInternal().entrySet().iterator(); i.hasNext();) {
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
      Preconditions.checkState(this.fn == null);
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

    public void setInline(boolean inline) {
      this.inline = inline;
      if (!inline) {
        // No need to keep references to function that can't be inlined.
        references = null;
        // Don't remove functions that we aren't inlining.
        remove = false;
      }
    }

    public boolean canRemove() {
      return remove;
    }

    public void setRemove(boolean remove) {
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
        return Collections.emptyMap();
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

    public Set<String> getNamesToAlias() {
      if (namesToAlias == null) {
        return Collections.emptySet();
      }
      return Collections.unmodifiableSet(namesToAlias);
    }

    public void setNamesToAlias(Set<String> names) {
      namesToAlias = names;
    }

    public void setModule(JSModule module) {
      this.module = module;
    }

    public JSModule getModule() {
      return module;
    }
  }

  /**
   * Interface for dealing with function declarations and function
   * expressions equally
   */
  private static interface Function {
    /** Gets the name of the function */
    public String getName();

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
    public Node getFunctionNode() {
      return fn;
    }

    @Override
    public void remove() {
      compiler.reportChangeToEnclosingScope(fn);
      NodeUtil.removeChild(fn.getParent(), fn);
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
    public Node getFunctionNode() {
      return var.getFirstChild().getFirstChild();
    }

    @Override
    public void remove() {
      compiler.reportChangeToEnclosingScope(var);
      NodeUtil.removeChild(var.getParent(), var);
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

    public FunctionExpression(Node fn, int index) {
      this.fn = fn;
      // A number is not a valid function JavaScript identifier
      // so we don't need to worry about collisions.
      this.fakeName = String.valueOf(index);
    }

    @Override
    public String getName() {
      return fakeName;
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
    Reference(
        Node callNode, Scope scope, JSModule module,
        InliningMode mode) {
      super(callNode, scope, module, mode);
    }

    void setRequiresDecomposition(boolean newVal) {
      this.requiresDecomposition = newVal;
    }
  }
}
