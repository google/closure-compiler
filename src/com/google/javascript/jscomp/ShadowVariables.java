/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.RenameVars.Assignment;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;

/**
 * Tries to compute a list of variables that can shadow a variable in the
 * outer scope.
 *
 * For example:
 *
 * <code>
 * var a = function() {
 *   var b = getB();
 *   b();
 *   return function(y) {};
 * };
 * </code>
 *
 * Normally, b would be mapped to variable L0, y would be L1.
 *
 * Instead we are going to make y shadows L0 in hope of using less variables
 * and reusing frequently used local names.
 *
 */
class ShadowVariables implements CompilerPass {

  // Keep a map of Upward Referencing name nodes of each scope.
  // A name is upward referencing name of a scope if:
  //
  // 1) It refers to (or defines) a name that is defined in the current
  // scope or any scope above the current scope that isn't the
  // global scope.
  //
  // 2) It is a upward referencing name of a child scope of this scope.
  //
  // Example:
  // var x; var y; function foo(a) { function bar(b) { x, a } }
  // The upward referencing names in scope 'foo' is bar, b, x and a;
  // The key to this map is the root node of the scope.
  //
  // We can see that for any variable x in the current scope, we can shadow
  // a variable y in an outer scope given that y is not a upward referencing
  // name of the current scope.

  // TODO(user): Maps scope to string instead of Node to string.
  // Make sure of scope memorization to minimize scope creation cost.
  private final Multimap<Node, String> scopeUpRefMap = HashMultimap.create();

  // Maps each local variable to all of its referencing NAME nodes in any scope.
  private final Multimap<Var, Reference> varToNameUsage = HashMultimap.create();

  private final AbstractCompiler compiler;

  // All the information used for renaming.
  private final SortedSet<Assignment> varsByFrequency;
  private final Map<String, Assignment> assignments;
  private final Map<Node, String> oldPseudoNameMap;
  private final Map<Node, String> deltaPseudoNameMap;


  /**
   * @param assignments Map of old variable names to its assignment Objects.
   * @param varsByFrequency Sorted variable assignments by Frequency.
   * @param pseudoNameMap The current pseudo name map so this pass can update
   *     it accordingly.
   */
  ShadowVariables(
      AbstractCompiler compiler,
      Map<String, Assignment> assignments,
      SortedSet<Assignment> varsByFrequency,
      Map<Node, String> pseudoNameMap) {
    this.compiler = compiler;
    this.assignments = assignments;
    this.varsByFrequency = varsByFrequency;
    this.oldPseudoNameMap = pseudoNameMap;
    this.deltaPseudoNameMap = new LinkedHashMap<>();
  }

  @Override
  public void process(Node externs, Node root) {

    // The algorithm is divided into two stages:
    //
    // 1. Information gathering (variable usage, upward referencing)
    //
    // 2. Tries to find shadows for each variables, updates the
    //    variable usage frequency map.
    //
    // 3. Updates the pseudo naming map if needed.
    NodeTraversal.traverseEs6(compiler, root, new GatherReferenceInfo());
    NodeTraversal.traverseEs6(compiler, root, new DoShadowVariables());

    if (oldPseudoNameMap != null) {
      oldPseudoNameMap.putAll(deltaPseudoNameMap);
    }
  }

  private class GatherReferenceInfo extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Skipping over non-name nodes and empty function names.
      if (!NodeUtil.isReferenceName(n)) {
        return;
      }

      // We focus on shadowing local variables as their name occurs much more
      // than global names.
      // TODO(user): Alternatively, we could experiment with using a local
      // name to shadow a global variable.
      if (t.inGlobalScope()) {
        return;
      }

      Scope scope = t.getScope();
      Var var = scope.getVar(n.getString());
      if (var == null) {
        // extern name or undefined name.
        return;
      }

      if (var.getScope().isGlobal()) {
        // We will not shadow a global variable name.
        return;
      }

      // Using the definition of upward referencing, fill in the map.
      if (var.getScope() != scope) {
        for (Scope s = scope; s != var.getScope() && s.isLocal(); s = s.getParent()) {
          scopeUpRefMap.put(s.getRootNode(), var.name);
        }
      } else {
        scopeUpRefMap.put(t.getScopeRoot(), var.name);
      }

      // Make sure that we don't shadow function parameters or function names from a function block
      // scope, eg.:
      // function f(a) { ... var a; ... } // Unsafe
      if (scope.isFunctionScope() && var.getScope() == scope) {
        scopeUpRefMap.put(scope.getRootNode().getLastChild(), var.name);
      }

      // Find in the usage map that tracks a var and all of its usage.
      varToNameUsage.put(var, new Reference(n, scope));
    }
  }

  private class DoShadowVariables extends AbstractPostOrderCallback
      implements ScopedCallback {

    @Override
    public void enterScope(NodeTraversal t) {
      if (t.inGlobalScope()) {
        return;
      }

      // Since we don't shadow global, there is nothing to be done in the
      // first immediate local scope as well.
      if ((t.getScopeRoot().isFunction()
              && NodeUtil.getEnclosingFunction(t.getScopeRoot().getParent()) == null)
          || (NodeUtil.isFunctionBlock(t.getScopeRoot())
              && NodeUtil.getEnclosingFunction(t.getScopeRoot().getGrandparent()) == null)) {
        return;
      }

      Scope s = t.getScope();
      for (Var var : s.getVarIterable()) {
        // Don't shadow variables that are bleed-out functions or caught exceptions to workaround
        // IE8 bugs.
        // TODO(moz): Gate this behind languageMode=ES3.
        if (var.isBleedingFunction() || var.isCatch()) {
          continue;
        }

        // Don't shadow an exported local.
        if (compiler.getCodingConvention().isExported(var.name, s.isLocal())) {
          continue;
        }

        // Try to look for the best shadow for the current candidate.
        Assignment bestShadow = findBestShadow(s, var);
        if (bestShadow == null) {
          continue;
        }

        // The name assignment being shadowed.
        Assignment localAssignment = assignments.get(var.getName());
        if (localAssignment == null) {
          continue;
        }

        // Only shadow if this increases the number of occurrences of the
        // shadowed variable.
        if (bestShadow.count < localAssignment.count) {
          continue; // Hope the next local variable would have a smaller count.
        }

        doShadow(localAssignment, bestShadow, var);

        if (oldPseudoNameMap != null) {
          String targetPseudoName =
            oldPseudoNameMap.get(s.getVar(bestShadow.oldName).nameNode);
          for (Reference use : varToNameUsage.get(var)) {
            deltaPseudoNameMap.put(use.nameNode, targetPseudoName);
          }
        }
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {}

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}

    /**
     * @return An assignment that can be used as a shadow for a local variable
     *     in the scope defined by curScopeRoot.
     */
    private Assignment findBestShadow(Scope curScope, Var var) {
      // Search for the candidate starting from the most used local.
      for (Assignment assignment : varsByFrequency) {
        if (assignment.oldName.startsWith(RenameVars.LOCAL_VAR_PREFIX)) {
          if (!scopeUpRefMap.containsEntry(curScope.getRootNode(), assignment.oldName)) {
            if (curScope.isDeclared(assignment.oldName, true)) {
              // Don't shadow if the scopes are the same eg.:
              // function f() { var a = 1; { var a = 2; } } // Unsafe
              Var toShadow = curScope.getVar(assignment.oldName);
              if (var.getScope() != toShadow.getScope()) {
                return assignment;
              }
            }
          }
        }
      }
      return null;
    }

    private void doShadow(Assignment original, Assignment toShadow, Var var) {
      Scope s = var.getScope();
      // We are now shadowing 'bestShadow' with localAssignment.
      // All of the reference NAME node of this variable.
      Collection<Reference> references = varToNameUsage.get(var);

      // First remove both assignments from the sorted list since they need
      // to be re-sorted.
      varsByFrequency.remove(original);
      varsByFrequency.remove(toShadow);

      // Adjust the count offset by the inner scope variable.
      original.count -= references.size();
      toShadow.count += references.size();

      // Add it back to the sorted list after re-adjustment.
      varsByFrequency.add(original);
      varsByFrequency.add(toShadow);

      // This is an important step. If variable L7 is going to be renamed to
      // L1, by definition of upward referencing, The name L1 is now in the
      // set of upward referencing names of the current scope up to the
      // declaring scope of the best shadow variable.
      Var shadowed = s.getVar(toShadow.oldName);
      if (shadowed != null) {
        if (s.isFunctionScope() && s.getRootNode().getLastChild().isBlock()) {
          scopeUpRefMap.put(s.getRootNode().getLastChild(), toShadow.oldName);
          scopeUpRefMap.remove(s.getRootNode().getLastChild(), original.oldName);
        }
        for (Scope curScope = s; curScope != shadowed.scope; curScope = curScope.getParent()) {
          scopeUpRefMap.put(curScope.getRootNode(), toShadow.oldName);
          scopeUpRefMap.remove(curScope.getRootNode(), original.oldName);
        }
      }

      // Mark all the references as shadowed.
      for (Reference ref : references) {
        Node n = ref.nameNode;
        n.setString(toShadow.oldName);
        if (ref.scope.getRootNode() == s.getRootNode()) {
          if (var.getNameNode() != ref.nameNode) {
            scopeUpRefMap.put(s.getRootNode(), toShadow.oldName);
            scopeUpRefMap.remove(s.getRootNode(), original.oldName);
          }
        } else {
          for (Scope curScope = ref.scope;
              curScope.getRootNode() != s.getRootNode();
              curScope = curScope.getParent()) {
            scopeUpRefMap.put(curScope.getRootNode(), toShadow.oldName);
            scopeUpRefMap.remove(curScope.getRootNode(), original.oldName);
          }
        }
      }
    }
  }

  private static final class Reference {
    private final Node nameNode;
    private final Scope scope;

    private Reference(Node nameNode, Scope scope) {
      this.nameNode = nameNode;
      this.scope = scope;
    }
  }
}
