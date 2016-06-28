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
    NodeTraversal.traverseEs6(compiler, root, new LookAheadRedundantClinitPruner());
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

  // TODO(michaelthomas): Prune clinit calls in functions, if previous functions or the immediate
  // next function guarantees clinit call. With that we won't need this pass.
  /**
   * Prunes clinit calls which immediately precede calls to a static function which calls the same
   * clinit. e.g. "Foo.clinit(); return new Foo()" -> "return new Foo()"
   */
  private static final class LookAheadRedundantClinitPruner extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (!node.isExprResult()) {
        return;
      }

      // Find clinit calls.
      String clinitName =
          node.getFirstChild().isCall() ? getClinitMethodName(node.getFirstFirstChild()) : null;
      if (clinitName == null) {
        return;
      }

      // Check for calls to a static method immediately following the clinit call.
      Node callOrNewNode = getCallOrNewNode(node.getNext());
      if (callOrNewNode == null || !callOrNewNode.getFirstChild().isName()) {
        return;
      }

      // Check that the call isn't a recursive call to the same function.
      Node enclosingFunction = NodeUtil.getEnclosingFunction(node);
      if (enclosingFunction == null || callOrNewNode.getFirstChild().getString()
          .equals(NodeUtil.getNearestFunctionName(enclosingFunction))) {
        return;
      }

      // Find the definition of the function being called.
      Var var = t.getScope().getVar(callOrNewNode.getFirstChild().getString());
      if (var == null || var.getInitialValue() == null || !var.getInitialValue().isFunction()) {
        return;
      }

      // Check that the clinit is safe to prune.
      Node staticFnNode = var.getInitialValue();
      if (callsClinit(staticFnNode, clinitName) && hasSafeArguments(t, callOrNewNode)) {
        parent.removeChild(node);
        t.getCompiler().reportCodeChange();
      }
    }

    /**
     * Returns whether the arguments to the specified call/new node are "safe". i.e. they are only
     * parameters to the enclosing function or literal values (and not e.g. a static field reference
     * which might need the clinit we are trying to remove).
     */
    private boolean hasSafeArguments(NodeTraversal t, Node callOrNewNode) {
      Node child = callOrNewNode.getSecondChild();
      while (child != null) {
        if (!NodeUtil.isLiteralValue(child, false /* includeFunctions */)
            && !isParameter(t, child)) {
          return false;
        }
        child = child.getNext();
      }
      return true;
    }

    /** Returns whether the specified node is defined as a parameter to its enclosing function. */
    private boolean isParameter(NodeTraversal t, Node n) {
      if (!n.isName()) {
        return false;
      }
      Var var = t.getScope().getVar(n.getString());
      return var.getParentNode().isParamList();
    }

    /**
     * Returns the call node associated with the specified node if one exists, otherwise returns
     * null.
     */
    private Node getCallOrNewNode(Node n) {
      if (n == null) {
        return null;
      }
      switch (n.getToken()) {
        case EXPR_RESULT:
        case RETURN:
          return getCallOrNewNode(n.getFirstChild());
        case CALL:
        case NEW:
          return n;
        case CONST:
        case LET:
        case VAR:
          return n.getChildCount() == 1 ? getCallOrNewNode(n.getFirstFirstChild()) : null;
        default:
          return null;
      }
    }

    /** Returns whether the specified function contains a call to the specified clinit. */
    private boolean callsClinit(Node fnNode, String clinitName) {
      Preconditions.checkNotNull(clinitName);
      // TODO(michaelthomas): Consider checking all children, but watch out for return statements
      // that could short-circuit the clinit.
      Node child = fnNode.getLastChild().getFirstChild();
      return child != null
          && child.isExprResult()
          && child.getFirstChild().isCall()
          && clinitName.equals(getClinitMethodName(child.getFirstFirstChild()));
    }
  }

  /**
   * A traversal callback that removes the body of empty clinits.
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
    private void trySubstituteEmptyFunction(Node fnNode, AbstractCompiler compiler) {
      String fnQualifiedName = NodeUtil.getName(fnNode);

      // Ignore anonymous/constructor functions.
      if (Strings.isNullOrEmpty(fnQualifiedName)) {
        return;
      }

      Node body = fnNode.getLastChild();
      if (!body.hasChildren()) {
        return;
      }

      // Ensure that the first expression in the body is setting itself to the empty function and
      // there are no other expressions.
      Node firstExpr = body.getFirstChild();
      if (!isAssignToEmptyFn(firstExpr, fnQualifiedName) || firstExpr.getNext() != null) {
        return;
      }

      body.removeChild(firstExpr);
      compiler.reportCodeChange();
    }

    private static boolean isAssignToEmptyFn(Node node, String enclosingFnName) {
      if (!NodeUtil.isExprAssign(node)) {
        return false;
      }

      Node lhs = node.getFirstChild().getFirstChild();
      Node rhs = node.getFirstChild().getLastChild();
      return NodeUtil.isEmptyFunctionExpression(rhs) && lhs.matchesQualifiedName(enclosingFnName);
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
