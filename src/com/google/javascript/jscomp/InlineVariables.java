/*
 * Copyright 2008 Google Inc.
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
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

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
*
*
 */
class InlineVariables implements CompilerPass {

  private final AbstractCompiler compiler;

  // Only inline things explicitly marked as constant.
  private final boolean onlyConstants;

  // Inlines all strings, even if they increase the size of the gzipped binary.
  private final boolean inlineAllStrings;

  // All declared constant variables with immutable values.
  // These should be inlined even if we can't prove that they're written before
  // first use.
  private final Set<Var> declaredConstants = Sets.newHashSet();

  private final IdentifyConstants identifyConstants = new IdentifyConstants();

  InlineVariables(AbstractCompiler compiler, boolean onlyConstants,
      boolean inlineAllStrings) {
    this.compiler = compiler;
    this.onlyConstants = onlyConstants;
    this.inlineAllStrings = inlineAllStrings;
  }

  @Override
  public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler, new InliningBehavior(),
        onlyConstants ?
            // Filter all constants, and put them in the declaredConstants map.
            identifyConstants :
            // Put all the constants in declaredConstants, but accept
            // all variables.
            Predicates.<Var>or(
                identifyConstants, Predicates.<Var>alwaysTrue()));
    callback.process(externs, root);
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
      if (declaredConstants.contains(var)) {
        return true;
      }

      if (!var.isConst()) {
        return false;
      }

      if (var.getInitialValue() == null) {
        // This constant is either externally defined or initialized shortly
        // after being declared (e.g. in an anonymous function used to hide
        // temporary variables), so the constant is ineligible for inlining.
        return false;
      }

      // Is the constant's value immutable?
      if (!NodeUtil.isImmutableValue(var.getInitialValue())) {
        return false;
      }

      declaredConstants.add(var);
      return true;
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
    public void afterExitScope(NodeTraversal t,
        Map<Var, ReferenceCollection> referenceMap) {
      collectAliasCandidates(t, referenceMap);
      doInlinesForScope(t, referenceMap);
    }

    /**
     * If any of the variables are well-defined and alias other variables,
     * mark them as aliasing candidates.
     */
    private void collectAliasCandidates(NodeTraversal t,
        Map<Var, ReferenceCollection> referenceMap) {
      if (!onlyConstants) {
        for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
          Var v = it.next();
          ReferenceCollection referenceInfo = referenceMap.get(v);

          // NOTE(nicksantos): Don't handle variables that are never used.
          // The tests are much easier to write if you don't, and there's
          // another pass that handles unused variables much more elegantly.
          if (referenceInfo != null && referenceInfo.references.size() >= 2 &&
              referenceInfo.isWellDefined() &&
              referenceInfo.isNeverReassigned()) {
            Reference declaration = referenceInfo.references.get(0);
            Node value = declaration.getNameNode().getFirstChild();
            if (declaration.getParent().getType() == Token.VAR &&
                value != null && value.getType() == Token.NAME) {
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
    private void doInlinesForScope(NodeTraversal t,
        Map<Var, ReferenceCollection> referenceMap) {

      for (Iterator<Var> it = t.getScope().getVars(); it.hasNext();) {
        Var v = it.next();

        ReferenceCollection referenceInfo = referenceMap.get(v);

        // referenceInfo will be null if we're in constants-only mode
        // and the variable is not a constant.
        if (referenceInfo == null || isVarInlineForbidden(v) ||
            staleVars.contains(v)) {
          // Never try to inline exported variables or variables that
          // were not collected or variables that have already been inlined.
          continue;
        } else if (isInlineableDeclaredConstant(v, referenceInfo.references)) {
          inlineDeclaredConstant(v, referenceInfo.references);
          staleVars.add(v);
        } else if (onlyConstants) {
          // If we're in constants-only mode, don't run more aggressive
          // inlining heuristics. See InlineConstantsTest.
          continue;
        } else {
          inlineNonConstants(t.getScope(), v, referenceInfo);
        }
      }
    }

    private void inlineNonConstants(Scope scope,
        Var v, ReferenceCollection referenceInfo) {
      if (referenceInfo.references.size() >= 2 &&
          isImmutableAndWellDefinedVariable(v, referenceInfo)) {
        // if the variable is defined more than twice, we can only
        // inline it if it's immutable and never defined before referenced.
        inlineWellDefinedVariable(v, referenceInfo.references);
        staleVars.add(v);
      } else if (referenceInfo.references.size() == 2) {
        // if the variable is only referenced once, we can try some more
        // complex inlining heuristics.
        Reference declaration = referenceInfo.references.get(0);
        Reference reference = referenceInfo.references.get(1);

        if (canInline(declaration, reference)) {
          // If the value being inlined contains references to variables
          // that have not yet been considered for inlining, we won't
          // be able to inline them later because the reference collection
          // will be wrong. So blacklist those variables from inlining.
          // We'll pick them up on the next pass.
          blacklistVarReferencesInTree(
              declaration.getNameNode().getFirstChild(), scope);

          inline(v, declaration, reference);
          staleVars.add(v);
        }
      }

      // If this variable was not inlined normally, check if we can
      // inline an alias of it. (If the variable was inlined, then the
      // reference data is out of sync. We're better off just waiting for
      // the next pass.)
      if (!staleVars.contains(v) && referenceInfo.isWellDefined() &&
          referenceInfo.isNeverReassigned()) {
        List<Reference> refs = referenceInfo.references;
        for (int i = 1 /* start from a read */; i < refs.size(); i++) {
          Node nameNode = refs.get(i).getNameNode();
          if (aliasCandidates.containsKey(nameNode)) {
            AliasCandidate candidate = aliasCandidates.get(nameNode);
            if (!staleVars.contains(candidate.alias)) {
              inlineWellDefinedVariable(
                  candidate.alias, candidate.refInfo.references);
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

      if (root.getType() == Token.NAME) {
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
      return compiler.getCodingConvention().isExported(var.name) ||
          staleVars.contains(var);
    }

    /**
     * Do the actual work of inlining a single declaration into a single
     * reference.
     */
    private void inline(Var v, Reference declaration, Reference reference) {
      Node name = declaration.getNameNode();
      Preconditions.checkState(name.getFirstChild() != null);
      Node value = name.removeFirstChild();
      inlineValue(v, reference, value);
      removeDeclaration(declaration);
    }

    /**
     * Inline an immutable variable into all of its references.
     */
    private void inlineWellDefinedVariable(Var v,
        List<Reference> refSet) {
      Reference decl = refSet.get(0);

      for (int i = 1; i < refSet.size(); i++) {
        inlineValue(v, refSet.get(i),
            decl.getNameNode().getFirstChild().cloneTree());
      }
      removeDeclaration(decl);
    }

    /**
     * Inline a declared constant.
     */
    private void inlineDeclaredConstant(Var v, List<Reference> refSet) {
      // Replace the references with the constant value
      Reference decl = null;

      for (Reference r : refSet) {
        if (r.getNameNode() == v.getNameNode()) {
          decl = r;
        } else {
          inlineValue(v, r, v.getInitialValue().cloneTree());
        }
      }

      removeDeclaration(decl);
    }

    /**
     * Remove the given VAR declaration.
     */
    private void removeDeclaration(Reference declaration) {
      Node varNode = declaration.getParent();
      varNode.removeChild(declaration.getNameNode());

      // Remove var node if empty
      if (!varNode.hasChildren()) {
        Preconditions.checkState(varNode.getType() == Token.VAR);

        Node grandparent = declaration.getGrandparent();
        NodeUtil.removeChild(grandparent, varNode);
      }

      compiler.reportCodeChange();
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
      ref.getParent().replaceChild(ref.getNameNode(), value);
      blacklistVarReferencesInTree(value, v.scope);
      compiler.reportCodeChange();
    }

    /**
     * Determines whether the given variable is declared as a constant
     * and may be inlined.
     */
    private boolean isInlineableDeclaredConstant(Var var,
        List<Reference> refs) {
      if (!identifyConstants.apply(var)) {
        return false;
      }

      // Determine if we should really inline a String or not.
      return var.getInitialValue().getType() != Token.STRING ||
          isStringWorthInlining(var, refs);
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
    private boolean canInline(Reference declaration, Reference reference) {
      if (!isValidDeclaration(declaration) || !isValidReference(reference)) {
        return false;
      }

      // Be very conservative and do no cross control structures or
      // scope boundaries
      if (declaration.getBasicBlock() != reference.getBasicBlock()) {
        return false;
      }

      // Do not inline into a call node. This would change
      // the context in which it was being called. For example,
      // var a = b.c;
      // a();
      // should not be inlined, because it calls a in the context of b
      // rather than the context of the window.
      if (declaration.getNameNode().getFirstChild().getType() == Token.GETPROP
          && reference.getParent().getType() == Token.CALL) {
        return false;
      }

      return canMoveAggressively(declaration) ||
          canMoveModerately(declaration, reference);
    }

    /**
     * If the value is a literal, we can cross more boundaries to inline it.
     */
    private boolean canMoveAggressively(Reference declaration) {
      // Anonymous functions and other mutable objects can move within 
      // the same basic block.
      Node value = declaration.getNameNode().getFirstChild();
      return NodeUtil.isLiteralValue(value)
          || value.getType() == Token.FUNCTION;
    }

    /**
     * If the value of a variable is not constant, then it may read or modify
     * state. Therefore it cannot be moved past anything else that may modify
     * the value being read or read values that are modified.
     */
    private boolean canMoveModerately(Reference declaration,
        Reference reference) {
      // Check if declaration can be inlined without passing
      // any side-effect causing nodes.
      Iterator<Node> it = new NodeIterators.LocalVarMotion(
          declaration.getNameNode(),
          declaration.getParent(),
          declaration.getGrandparent());
      Node targetName = reference.getNameNode();
      while (it.hasNext()) {
        Node curNode = it.next();
        if (curNode == targetName) {
          return true;
        }
      }

      return false;
    }

    /**
     * @return true if the reference is a normal VAR declaration with
     *    initial value. (Only normal VARs can be inlined.)
     */
    private boolean isValidDeclaration(Reference declaration) {
      return declaration.isDeclaration() &&
          declaration.getNameNode().getFirstChild() != null;
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
      if (!isValidDeclaration(refSet.get(0))) {
        return false;
      }

      for (int i = 1; i < refSet.size(); i++) {
        Reference ref = refSet.get(i);
        if (!isValidReference(ref)) {
          return false;
        }
      }

      if (!refInfo.isWellDefined()) {
        return false;
      }

      Node value = refSet.get(0).getNameNode().getFirstChild();
      return NodeUtil.isImmutableValue(value) &&
          (value.getType() != Token.STRING ||
           isStringWorthInlining(v, refInfo.references));
    }
  }
}
