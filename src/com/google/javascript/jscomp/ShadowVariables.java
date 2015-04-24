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
import java.util.Iterator;
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
  private final Multimap<Var, Node> varToNameUsage = HashMultimap.create();

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
    NodeTraversal.traverse(compiler, root, new GatherReferenceInfo());
    NodeTraversal.traverse(compiler, root, new DoShadowVariables());

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

      Var var = t.getScope().getVar(n.getString());
      if (var == null) {
        // extern name or undefined name.
        return;
      }

      if (var.getScope().isGlobal()) {
        // We will not shadow a global variable name.
        return;
      }

      // Using the definition of upward referencing, fill in the map.
      if (var.getScope() != t.getScope()) {
        for (Scope s = t.getScope();
            s != var.getScope() && s.isLocal(); s = s.getParent()) {
          scopeUpRefMap.put(s.getRootNode(), var.name);
        }
      }

      if (var.getScope() == t.getScope()) {
        scopeUpRefMap.put(t.getScopeRoot(), var.name);
      }

      // Find in the usage map that tracks a var and all of its usage.
      varToNameUsage.put(var, n);
    }
  }

  private class DoShadowVariables extends AbstractPostOrderCallback
      implements ScopedCallback {

    @Override
    public void enterScope(NodeTraversal t) {
      Scope s = t.getScope();
      if (!s.isLocal()) {
        return;
      }

      // Since we don't shadow global, there is nothing to be done in the
      // first immediate local scope as well.
      if (s.getParent().isGlobal()) {
        return;
      }

      for (Iterator<Var> vars = s.getVars(); vars.hasNext();) {
        Var var = vars.next();

        // Don't shadow variables that is bleed-out to fix an IE bug.
        if (var.isBleedingFunction()) {
          continue;
        }

        // Don't shadow an exported local.
        if (compiler.getCodingConvention().isExported(var.name, s.isLocal())) {
          continue;
        }

        // Try to look for the best shadow for the current candidate.
        Assignment bestShadow = findBestShadow(s);
        if (bestShadow == null) {
          continue;
        }

        // The name assignment being shadowed.
        Assignment localAssignment = assignments.get(var.getName());

        // Only shadow if this increases the number of occurrences of the
        // shadowed variable.
        if (bestShadow.count < localAssignment.count) {
          continue; // Hope the next local variable would have a smaller count.
        }

        doShadow(localAssignment, bestShadow, var);

        if (oldPseudoNameMap != null) {
          String targetPseudoName =
            oldPseudoNameMap.get(s.getVar(bestShadow.oldName).nameNode);
          for (Node use : varToNameUsage.get(var)) {
            deltaPseudoNameMap.put(use, targetPseudoName);
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
    private Assignment findBestShadow(Scope curScope) {
      // Search for the candidate starting from the most used local.
      for (Assignment assignment : varsByFrequency) {
        if (assignment.oldName.startsWith(RenameVars.LOCAL_VAR_PREFIX)) {
          if (!scopeUpRefMap.containsEntry(curScope.getRootNode(), assignment.oldName)) {
            if (curScope.isDeclared(assignment.oldName, true)) {
              return assignment;
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
      Collection<Node> references = varToNameUsage.get(var);

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
        for (Scope curScope = s; curScope != shadowed.scope;
            curScope = curScope.getParent()) {
          scopeUpRefMap.put(curScope.getRootNode(), toShadow.oldName);
        }
      }

      // Mark all the references as shadowed.
      for (Node n : references) {
        n.setString(toShadow.oldName);
        Node cur = n;
        while (cur != s.getRootNode()) {
          cur = cur.getParent();
          if (cur.isFunction()) {
            scopeUpRefMap.put(cur, toShadow.oldName);
          }
        }
      }
    }
  }
}
