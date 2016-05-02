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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * An optimization pass to prune J2CL clinits.
 */
public class J2clClinitPrunerPass implements CompilerPass {

  private final AbstractCompiler compiler;

  J2clClinitPrunerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new RedundantClinitPruner());
    NodeTraversal.traverseEs6(compiler, root, new EmptyClinitPruner());
  }

  /**
   * Removes redundant clinit calls inside method body if it is guaranteed to be called earlier.
   */
  private static final class RedundantClinitPruner implements Callback {
    private HierarchicalSet<String> clinitsCalledAtBranch = new HierarchicalSet<>(null);

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node node, Node parent) {
      if (parent != null && NodeUtil.isFunctionDeclaration(node)) {
        // Unlike function expressions, we don't know when the function in a function declaration
        // will be executed so lets start a new traversal to avoid inheriting anything from the
        // current branch.
        NodeTraversal.traverseEs6(t.getCompiler(), node, new RedundantClinitPruner());
        return false;
      }

      if (isNewControlBranch(parent)) {
        clinitsCalledAtBranch = new HierarchicalSet(clinitsCalledAtBranch);
        if (isClinitMethod(parent)) {
          // Adds itself as any of your children can assume clinit is already called.
          clinitsCalledAtBranch.add(NodeUtil.getName(parent));
        }
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      tryRemovingClinit(t, node, parent);

      if (isNewControlBranch(parent)) {
        clinitsCalledAtBranch = clinitsCalledAtBranch.parent;
      }
    }

    private void tryRemovingClinit(NodeTraversal t, Node node, Node parent) {
      String clinitName = node.isCall() ? getClinitMethodName(node.getFirstChild()) : null;
      if (clinitName == null) {
        return;
      }

      if (clinitsCalledAtBranch.add(clinitName)) {
        // This is the first time we are seeing this clinit so cannot remove it.
        return;
      }

      // Replacing with '0' is a simple way of removing without introducing invalid AST.
      parent.replaceChild(node, Node.newNumber(0).useSourceInfoIfMissingFrom(node));
      t.getCompiler().reportCodeChange();
    }

    private static boolean isNewControlBranch(Node n) {
      return n != null
          && (NodeUtil.isControlStructure(n)
              || n.isHook()
              || n.isAnd()
              || n.isOr()
              || n.isFunction());
    }
  }

  /**
   * A traversal callback that removes the boy of empty clinits.
   */
  private static final class EmptyClinitPruner extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (!isClinitMethod(node)) {
        return;
      }

      trySubstituteEmptyFunction(node, t.getCompiler());
    }

    /**
     * Clears the body of any functions are are equivalent to empty functions.
     */
    private Node trySubstituteEmptyFunction(Node fnNode, AbstractCompiler compiler) {
      Preconditions.checkArgument(fnNode.isFunction());

      String fnQualifiedName = NodeUtil.getName(fnNode);

      // Ignore anonymous/constructor functions.
      if (Strings.isNullOrEmpty(fnQualifiedName)) {
        return fnNode;
      }

      Node body = fnNode.getLastChild();
      if (!body.hasChildren()) {
        return fnNode;
      }

      // Ensure that the first expression in the body is setting itself to the empty function and
      // there are no other expressions.
      Node firstExpr = body.getFirstChild();
      if (firstExpr.isExprResult()
          && isAssignToEmptyFn(firstExpr.getFirstChild(), fnQualifiedName)
          && (firstExpr.getNext() == null)) {
        body.removeChildren();
        compiler.reportCodeChange();
      }

      return fnNode;
    }

    private static boolean isAssignToEmptyFn(Node assignNode, String enclosingFnName) {
      if (!assignNode.isAssign()) {
        return false;
      }

      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getLastChild();

      // If the RHS is not an empty function then this isn't of interest.
      if (!NodeUtil.isEmptyFunctionExpression(rhs)) {
        return false;
      }

      // Ensure that we are actually mutating the given function.
      return lhs.getQualifiedName() != null && lhs.matchesQualifiedName(enclosingFnName);
    }
  }

  private static boolean isClinitMethod(Node node) {
    return node.isFunction() && isClinitMethodName(NodeUtil.getName(node));
  }

  private static String getClinitMethodName(Node fnNode) {
    String fnName = fnNode.getQualifiedName();
    return isClinitMethodName(fnName) ? fnName : null;
  }

  private static boolean isClinitMethodName(String fnName) {
    // The '.$clinit' case) only happens when collapseProperties is off.
    return fnName != null && (fnName.endsWith("$$0clinit") || fnName.endsWith(".$clinit"));
  }

  /**
   * A minimalist implelementation of a hierarchical Set where an item might exist in current set or
   * any of its parents.
   */
  private static class HierarchicalSet<T> {
    private Set<T> currentSet = new HashSet<>();
    private HierarchicalSet<T> parent;

    public HierarchicalSet(HierarchicalSet parent) {
      this.parent = parent;
    }

    public boolean add(T o) {
      return !parentsContains(o) && currentSet.add(o);
    }

    /** Returns true either my parent or any of its parents contains the item. */
    private boolean parentsContains(Object o) {
      return parent != null && (parent.currentSet.contains(o) || parent.parentsContains(o));
    }
  }
}
