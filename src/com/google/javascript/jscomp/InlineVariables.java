/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Using the infrastructure provided by VariableReferencePass, identify
 * variables that are used only once and in a way that is safe to move, and then
 * inline them.
 *
 * This pass has two "modes." One mode only inlines variables declared as
 * constants, for legacy compiler clients. The second mode inlines any
 * variable that we can provably inline. Note that the second mode is a
 * superset of the first mode. We only support the first mode for
 * backwards-compatibility with compiler clients that don't want
 * --inline_variables.
 *
 * The approach of this pass is similar to {@link CrossModuleCodeMotion}
 *
 * @author kushal@google.com (Kushal Dave)
 * @author nicksantos@google.com (Nick Santos)
 */
class InlineVariables implements CompilerPass {

  private final AbstractCompiler compiler;

  enum Mode {
    // Only inline things explicitly marked as constant.
    CONSTANTS_ONLY,
    // Locals only
    LOCALS_ONLY,
    ALL
  }

  private final Mode mode;

  // Inlines all strings, even if they increase the size of the gzipped binary.
  private final boolean inlineAllStrings;

  private final IdentifyConstants identifyConstants = new IdentifyConstants();

  InlineVariables(
      AbstractCompiler compiler,
      Mode mode,
      boolean inlineAllStrings) {
    this.compiler = compiler;
    this.mode = mode;
    this.inlineAllStrings = inlineAllStrings;
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler, new InliningBehavior(), getFilterForMode());
    callback.process(externs, root);
  }

  private Predicate<Var> getFilterForMode() {
    switch (mode) {
      case ALL:
        return Predicates.<Var>alwaysTrue();
      case LOCALS_ONLY:
        return new IdentifyLocals();
      case CONSTANTS_ONLY:
        return new IdentifyConstants();
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Filters variables declared as "constant", and declares them in the outer
   * declaredConstants map.
   *
   * In Google coding conventions, this means anything declared with @const
   * or named in all caps, and initialized to an immutable value.
   * CheckConsts has already verified that these are truly constants.
   */
  private class IdentifyConstants implements Predicate<Var> {
    @Override
    public boolean apply(Var var) {
      return var.isConst();
    }
  }

  /**
   * Filters non-global variables.
   */
  private class IdentifyLocals implements Predicate<Var> {
    @Override
    public boolean apply(Var var) {
      return var.scope.isLocal();
    }
  }

  private static class AliasCandidate {
    private final Var alias;
    private final ReferenceCollection refInfo;

    AliasCandidate(Var alias, ReferenceCollection refInfo) {
      this.alias = alias;
      this.refInfo = refInfo;
    }
  }

  /**
   * Builds up information about nodes in each scope. When exiting the
   * scope, inspects all variables in that scope, and inlines any
   * that we can.
   */
  private class InliningBehavior implements Behavior {

    /**
     * A list of variables that should not be inlined, because their
     * reference information is out of sync with the state of the AST.
     */
    private final Set<Var> staleVars = Sets.newHashSet();

    /**
     * Stored possible aliases of variables that never change, with
     * all the reference info about those variables. Hashed by the NAME
     * node of the variable being aliased.
     */
    final Map<Node, AliasCandidate> aliasCandidates = Maps.newHashMap();

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      collectAliasCandidates(t, referenceMap);
      doInlinesForScope(t, referenceMap);
    }

    /**
     * If any of the variables are well-defined and alias other variables,
     * mark them as aliasing candidates.
     */
    private void collectAliasCandidates(NodeTraversal t,
        ReferenceMap referenceMap) {
      if (mode != Mode.CONSTANTS_ONLY) {
        for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
          Var v = it.next();
          ReferenceCollection referenceInfo = referenceMap.getReferences(v);

          // NOTE(nicksantos): Don't handle variables that are never used.
          // The tests are much easier to write if you don't, and there's
          // another pass that handles unused variables much more elegantly.
          if (referenceInfo != null && referenceInfo.references.size() >= 2 &&
              referenceInfo.isWellDefined() &&
              referenceInfo.isAssignedOnceInLifetime()) {
            Reference init = referenceInfo.getInitializingReference();
            Node value = init.getAssignedValue();
            if (value != null && value.isName()) {
              aliasCandidates.put(value, new AliasCandidate(v, referenceInfo));
            }
          }
        }
      }
    }

    /**
     * For all variables in this scope, see if they are only used once.
     * If it looks safe to do so, inline them.
     */
    private void doInlinesForScope(NodeTraversal t, ReferenceMap referenceMap) {

      boolean maybeModifiedArguments =
          maybeEscapedOrModifiedArguments(t.getScope(), referenceMap);
      for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
        Var v = it.next();

        ReferenceCollection referenceInfo = referenceMap.getReferences(v);

        // referenceInfo will be null if we're in constants-only mode
        // and the variable is not a constant.
        if (referenceInfo == null || isVarInlineForbidden(v)) {
          // Never try to inline exported variables or variables that
          // were not collected or variables that have already been inlined.
          continue;
        } else if (isInlineableDeclaredConstant(v, referenceInfo)) {
          Reference init = referenceInfo.getInitializingReferenceForConstants();
          Node value = init.getAssignedValue();
          inlineDeclaredConstant(v, value, referenceInfo.references);
          staleVars.add(v);
        } else if (mode == Mode.CONSTANTS_ONLY) {
          // If we're in constants-only mode, don't run more aggressive
          // inlining heuristics. See InlineConstantsTest.
          continue;
        } else {
          inlineNonConstants(v, referenceInfo, maybeModifiedArguments);
        }
      }
    }

    private boolean maybeEscapedOrModifiedArguments(
        Scope scope, ReferenceMap referenceMap) {
      if (scope.isLocal()) {
        Var arguments = scope.getArgumentsVar();
        ReferenceCollection refs = referenceMap.getReferences(arguments);
        if (refs != null && !refs.references.isEmpty()) {
          for (Reference ref : refs.references) {
            Node refNode = ref.getNode();
            Node refParent = ref.getParent();
            // Any reference that is not a read of the arguments property
            // consider a escape of the arguments object.
            if (!(NodeUtil.isGet(refParent)
                && refNode == ref.getParent().getFirstChild()
                && !isLValue(refParent))) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private boolean isLValue(Node n) {
      Node parent = n.getParent();
      return (parent.isInc()
          || parent.isDec()
          || (NodeUtil.isAssignmentOp(parent)
          && parent.getFirstChild() == n));
    }

    private void inlineNonConstants(
        Var v, ReferenceCollection referenceInfo,
        boolean maybeModifiedArguments) {
      int refCount = referenceInfo.references.size();
      Reference declaration = referenceInfo.references.get(0);
      Reference init = referenceInfo.getInitializingReference();
      int firstRefAfterInit = (declaration == init) ? 2 : 3;

      if (refCount > 1 &&
          isImmutableAndWellDefinedVariable(v, referenceInfo)) {
        // if the variable is referenced more than once, we can only
        // inline it if it's immutable and never defined before referenced.
        Node value;
        if (init != null) {
          value = init.getAssignedValue();
        } else {
          // Create a new node for variable that is never initialized.
          Node srcLocation = declaration.getNode();
          value = NodeUtil.newUndefinedNode(srcLocation);
        }
        Preconditions.checkNotNull(value);
        inlineWellDefinedVariable(v, value, referenceInfo.references);
        staleVars.add(v);
      } else if (refCount == firstRefAfterInit) {
        // The variable likely only read once, try some more
        // complex inlining heuristics.
        Reference reference = referenceInfo.references.get(
            firstRefAfterInit - 1);
        if (canInline(declaration, init, reference)) {
          inline(v, declaration, init, reference);
          staleVars.add(v);
        }
      } else if (declaration != init && refCount == 2) {
        if (isValidDeclaration(declaration) && isValidInitialization(init)) {
          // The only reference is the initialization, remove the assignment and
          // the variable declaration.
          Node value = init.getAssignedValue();
          Preconditions.checkNotNull(value);
          inlineWellDefinedVariable(v, value, referenceInfo.references);
          staleVars.add(v);
        }
      }

      // If this variable was not inlined normally, check if we can
      // inline an alias of it. (If the variable was inlined, then the
      // reference data is out of sync. We're better off just waiting for
      // the next pass.)
      if (!maybeModifiedArguments &&
          !staleVars.contains(v) && referenceInfo.isWellDefined() &&
          referenceInfo.isAssignedOnceInLifetime()) {
        List<Reference> refs = referenceInfo.references;
        for (int i = 1 /* start from a read */; i < refs.size(); i++) {
          Node nameNode = refs.get(i).getNode();
          if (aliasCandidates.containsKey(nameNode)) {
            AliasCandidate candidate = aliasCandidates.get(nameNode);
            if (!staleVars.contains(candidate.alias) &&
                !isVarInlineForbidden(candidate.alias)) {
              Reference aliasInit;
              aliasInit = candidate.refInfo.getInitializingReference();
              Node value = aliasInit.getAssignedValue();
              Preconditions.checkNotNull(value);
              inlineWellDefinedVariable(candidate.alias,
                  value,
                  candidate.refInfo.references);
              staleVars.add(candidate.alias);
            }
          }
        }
      }
    }

    /**
     * If there are any variable references in the given node tree, blacklist
     * them to prevent the pass from trying to inline the variable.
     */
    private void blacklistVarReferencesInTree(Node root, Scope scope) {
      for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
        blacklistVarReferencesInTree(c, scope);
      }

      if (root.isName()) {
        staleVars.add(scope.getVar(root.getString()));
      }
    }

    /**
     * Whether the given variable is forbidden from being inlined.
     */
    private boolean isVarInlineForbidden(Var var) {
      // A variable may not be inlined if:
      // 1) The variable is exported,
      // 2) A reference to the variable has been inlined. We're downstream
      //    of the mechanism that creates variable references, so we don't
      //    have a good way to update the reference. Just punt on it.
      // 3) Don't inline the special RENAME_PROPERTY_FUNCTION_NAME
      return var.isExtern()
          || compiler.getCodingConvention().isExported(var.name)
          || RenameProperties.RENAME_PROPERTY_FUNCTION_NAME.equals(var.name)
          || staleVars.contains(var);
    }

    /**
     * Do the actual work of inlining a single declaration into a single
     * reference.
     */
    private void inline(Var v, Reference decl, Reference init, Reference ref) {
      Node value = init.getAssignedValue();
      Preconditions.checkState(value != null);
      // Check for function declarations before the value is moved in the AST.
      boolean isFunctionDeclaration = NodeUtil.isFunctionDeclaration(value);
      compiler.reportChangeToEnclosingScope(ref.getNode());
      inlineValue(v, ref, value.detachFromParent());
      if (decl != init) {
        Node expressRoot = init.getGrandparent();
        Preconditions.checkState(expressRoot.isExprResult());
        NodeUtil.removeChild(expressRoot.getParent(), expressRoot);
      }
      // Function declarations have already been removed.
      if (!isFunctionDeclaration) {
        compiler.reportChangeToEnclosingScope(decl.getNode());
        removeDeclaration(decl);
      }
    }

    /**
     * Inline an immutable variable into all of its references.
     */
    private void inlineWellDefinedVariable(Var v, Node value,
        List<Reference> refSet) {
      Reference decl = refSet.get(0);
      for (int i = 1; i < refSet.size(); i++) {
        inlineValue(v, refSet.get(i), value.cloneTree());
      }
      removeDeclaration(decl);
    }

    /**
     * Inline a declared constant.
     */
    private void inlineDeclaredConstant(Var v, Node value,
        List<Reference> refSet) {
      // Replace the references with the constant value
      Reference decl = null;

      for (Reference r : refSet) {
        if (r.getNode() == v.getNameNode()) {
          decl = r;
        } else {
          inlineValue(v, r, value.cloneTree());
        }
      }

      removeDeclaration(decl);
    }

    /**
     * Remove the given VAR declaration.
     */
    private void removeDeclaration(Reference decl) {
      Node varNode = decl.getParent();
      Node grandparent = decl.getGrandparent();

      compiler.reportChangeToEnclosingScope(decl.getNode());
      varNode.removeChild(decl.getNode());
      // Remove var node if empty
      if (!varNode.hasChildren()) {
        Preconditions.checkState(varNode.isVar());
        NodeUtil.removeChild(grandparent, varNode);
      }
    }

    /**
     * Replace the given reference with the given value node.
     *
     * @param v The variable that's referenced.
     * @param ref The reference to replace.
     * @param value The node tree to replace it with. This tree should be safe
     *     to re-parent.
     */
    private void inlineValue(Var v, Reference ref, Node value) {
      compiler.reportChangeToEnclosingScope(ref.getNode());
      if (ref.isSimpleAssignmentToName()) {
        // This is the initial assignment.
        ref.getGrandparent().replaceChild(ref.getParent(), value);
      } else {
        ref.getParent().replaceChild(ref.getNode(), value);
      }
      blacklistVarReferencesInTree(value, v.scope);
    }

    /**
     * Determines whether the given variable is declared as a constant
     * and may be inlined.
     */
    private boolean isInlineableDeclaredConstant(Var var,
        ReferenceCollection refInfo) {
      if (!identifyConstants.apply(var)) {
        return false;
      }

      if (!refInfo.isAssignedOnceInLifetime()) {
        return false;
      }

      Reference init = refInfo.getInitializingReferenceForConstants();
      if (init == null) {
        return false;
      }

      Node value = init.getAssignedValue();
      if (value == null) {
        // This constant is either externally defined or initialized indirectly
        // (e.g. in an function expression used to hide
        // temporary variables), so the constant is ineligible for inlining.
        return false;
      }

      // Is the constant's value immutable?
      if (!NodeUtil.isImmutableValue(value)) {
        return false;
      }

      // Determine if we should really inline a String or not.
      return !value.isString() ||
          isStringWorthInlining(var, refInfo.references);
    }

    /**
     * Compute whether the given string is worth inlining.
     */
    private boolean isStringWorthInlining(Var var, List<Reference> refs) {
      if (!inlineAllStrings && !var.isDefine()) {
        int len = var.getInitialValue().getString().length() + "''".length();

        // if not inlined: var xx="value"; .. xx .. xx ..
        // The 4 bytes per reference is just a heuristic:
        // 2 bytes per var name plus maybe 2 bytes if we don't inline, e.g.
        // in the case of "foo " + CONST + " bar"
        int noInlineBytes = "var xx=;".length() + len +
                            4 * (refs.size() - 1);

        // if inlined:
        // I'm going to assume that half of the quotes will be eliminated
        // thanks to constant folding, therefore I subtract 1 (2/2=1) from
        // the string length.
        int inlineBytes = (len - 1) * (refs.size() - 1);

        // Not inlining if doing so uses more bytes, or this constant is being
        // defined.
        return noInlineBytes >= inlineBytes;
      }

      return true;
    }

    /**
     * @return true if the provided reference and declaration can be safely
     *         inlined according to our criteria
     */
    private boolean canInline(
        Reference declaration,
        Reference initialization,
        Reference reference) {
      if (!isValidDeclaration(declaration)
          || !isValidInitialization(initialization)
          || !isValidReference(reference)) {
        return false;
      }

      // If the value is read more than once, skip it.
      // VAR declarations and EXPR_RESULT don't need the value, but other
      // ASSIGN expressions parents do.
      if (declaration != initialization &&
          !initialization.getGrandparent().isExprResult()) {
        return false;
      }

      // Be very conservative and do no cross control structures or
      // scope boundaries
      if (declaration.getBasicBlock() != initialization.getBasicBlock()
          || declaration.getBasicBlock() != reference.getBasicBlock()) {
        return false;
      }

      // Do not inline into a call node. This would change
      // the context in which it was being called. For example,
      //   var a = b.c;
      //   a();
      // should not be inlined, because it calls a in the context of b
      // rather than the context of the window.
      //   var a = b.c;
      //   f(a)
      // is OK.
      Node value = initialization.getAssignedValue();
      Preconditions.checkState(value != null);
      if (value.isGetProp()
          && reference.getParent().isCall()
          && reference.getParent().getFirstChild() == reference.getNode()) {
        return false;
      }

      if (value.isFunction()) {
        Node callNode = reference.getParent();
        if (reference.getParent().isCall()) {
          CodingConvention convention = compiler.getCodingConvention();
          // Bug 2388531: Don't inline subclass definitions into class defining
          // calls as this confused class removing logic.
          SubclassRelationship relationship =
              convention.getClassesDefinedByCall(callNode);
          if (relationship != null) {
            return false;
          }

          // issue 668: Don't inline singleton getter methods
          // calls as this confused class removing logic.
          if (convention.getSingletonGetterClassName(callNode) != null) {
            return false;
          }
        }
      }

      return canMoveAggressively(value) ||
          canMoveModerately(initialization, reference);
    }

    /**
     * If the value is a literal, we can cross more boundaries to inline it.
     */
    private boolean canMoveAggressively(Node value) {
      // Function expressions and other mutable objects can move within
      // the same basic block.
      return NodeUtil.isLiteralValue(value, true)
          || value.isFunction();
    }

    /**
     * If the value of a variable is not constant, then it may read or modify
     * state. Therefore it cannot be moved past anything else that may modify
     * the value being read or read values that are modified.
     */
    private boolean canMoveModerately(
        Reference initialization,
        Reference reference) {
      // Check if declaration can be inlined without passing
      // any side-effect causing nodes.
      Iterator<Node> it;
      if (initialization.getParent().isVar()) {
        it = NodeIterators.LocalVarMotion.forVar(
            initialization.getNode(),     // NAME
            initialization.getParent(),       // VAR
            initialization.getGrandparent()); // VAR container
      } else if (initialization.getParent().isAssign()) {
        Preconditions.checkState(
            initialization.getGrandparent().isExprResult());
        it = NodeIterators.LocalVarMotion.forAssign(
            initialization.getNode(),     // NAME
            initialization.getParent(),       // ASSIGN
            initialization.getGrandparent(),  // EXPR_RESULT
            initialization.getGrandparent().getParent()); // EXPR container
      } else {
        throw new IllegalStateException("Unexpected initialization parent " +
            initialization.getParent().toStringTree());
      }
      Node targetName = reference.getNode();
      while (it.hasNext()) {
        Node curNode = it.next();
        if (curNode == targetName) {
          return true;
        }
      }

      return false;
    }

    /**
     * @return true if the reference is a normal VAR or FUNCTION declaration.
     */
    private boolean isValidDeclaration(Reference declaration) {
      return (declaration.getParent().isVar()
          && !declaration.getGrandparent().isFor())
          || NodeUtil.isFunctionDeclaration(declaration.getParent());
    }

    /**
     * @return Whether there is a initial value.
     */
    private boolean isValidInitialization(Reference initialization) {
      if (initialization == null) {
        return false;
      } else if (initialization.isDeclaration()) {
        // The reference is a FUNCTION declaration or normal VAR declaration
        // with a value.
        if (!NodeUtil.isFunctionDeclaration(initialization.getParent())
            && initialization.getNode().getFirstChild() == null) {
          return false;
        }
      } else {
        Node parent = initialization.getParent();
        Preconditions.checkState(
            parent.isAssign()
            && parent.getFirstChild() == initialization.getNode());
      }

      Node n = initialization.getAssignedValue();
      if (n.isFunction()) {
        return compiler.getCodingConvention().isInlinableFunction(n);
      }

      return true;
    }

    /**
     * @return true if the reference is a candidate for inlining
     */
    private boolean isValidReference(Reference reference) {
      return !reference.isDeclaration() && !reference.isLvalue();
    }

    /**
     * Determines whether the reference collection describes a variable that
     * is initialized to an immutable value, never modified, and defined before
     * every reference.
     */
    private boolean isImmutableAndWellDefinedVariable(Var v,
        ReferenceCollection refInfo) {
      List<Reference> refSet = refInfo.references;
      int startingReadRef = 1;
      Reference refDecl = refSet.get(0);
      if (!isValidDeclaration(refDecl)) {
        return false;
      }

      boolean isNeverAssigned = refInfo.isNeverAssigned();
      // For values that are never assigned, only the references need to be
      // checked.
      if (!isNeverAssigned) {
        Reference refInit = refInfo.getInitializingReference();
        if (!isValidInitialization(refInit)) {
          return false;
        }

        if (refDecl != refInit) {
          Preconditions.checkState(refInit == refSet.get(1));
          startingReadRef = 2;
        }

        if (!refInfo.isWellDefined()) {
          return false;
        }

        Node value = refInit.getAssignedValue();
        Preconditions.checkNotNull(value);

        boolean isImmutableValueWorthInlining =
            NodeUtil.isImmutableValue(value) &&
            (!value.isString() ||
                isStringWorthInlining(v, refInfo.references));
        boolean isInlinableThisAlias =
            value.isThis() &&
            !refInfo.isEscaped();
        if (!isImmutableValueWorthInlining && !isInlinableThisAlias) {
          return false;
        }
      }

      for (int i = startingReadRef; i < refSet.size(); i++) {
        Reference ref = refSet.get(i);
        if (!isValidReference(ref)) {
          return false;
        }
      }

      return true;
    }
  }
}
