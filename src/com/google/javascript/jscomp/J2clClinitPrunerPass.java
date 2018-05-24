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

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeTraversal.ChangeScopeRootCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/** An optimization pass to prune J2CL clinits. */
public class J2clClinitPrunerPass implements CompilerPass {

  private final Map<String, Node> emptiedClinitMethods = new LinkedHashMap<>();
  private final AbstractCompiler compiler;
  private final List<Node> changedScopeNodes;

  J2clClinitPrunerPass(AbstractCompiler compiler, List<Node> changedScopeNodes) {
    this.compiler = compiler;
    this.changedScopeNodes = changedScopeNodes;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }

    removeRedundantClinits(root, changedScopeNodes);

    pruneEmptyClinits(root, changedScopeNodes);

    if (emptiedClinitMethods.isEmpty()) {
      // Since no clinits are pruned, we don't need look for more opportunities.
      return;
    }

    Multimap<String, Node> clinitReferences = collectClinitReferences(root);
    do {
      List<Node> newChangedScopes = cleanEmptyClinitReferences(clinitReferences);
      pruneEmptyClinits(root, newChangedScopes);
    } while (!emptiedClinitMethods.isEmpty());

    // Update the function side-effect markers on the AST.
    // Removing a clinit from a function may make it side-effect free.
    new PureFunctionIdentifier.Driver(compiler, null).process(externs, root);
  }

  private void removeRedundantClinits(Node root, List<Node> changedScopeNodes) {
    RedundantClinitPruner redundantClinitPruner = new RedundantClinitPruner();
    NodeTraversal.traverseScopeRoots(
        compiler,
        root,
        getNonNestedParentScopeNodes(changedScopeNodes),
        redundantClinitPruner,
        redundantClinitPruner, // FunctionCallback
        true);

    NodeTraversal.traverseScopeRoots(
        compiler, root, changedScopeNodes, new LookAheadRedundantClinitPruner(), false);
  }

  private void pruneEmptyClinits(Node root, List<Node> changedScopes) {
    // Clear emptiedClinitMethods before EmptyClinitPruner to populate only with new ones.
    emptiedClinitMethods.clear();
    NodeTraversal.traverseScopeRoots(
        compiler, root, changedScopes, new EmptyClinitPruner(), false);

    // Make sure replacements are to final destination instead of pointing intermediate ones.
    for (Entry<String, Node> clinitReplacementEntry : emptiedClinitMethods.entrySet()) {
      clinitReplacementEntry.setValue(resolveReplacement(clinitReplacementEntry.getValue()));
    }
  }

  private Node resolveReplacement(Node node) {
    if (node == null) {
      return null;
    }
    String clinitName = getClinitMethodName(node);
    return emptiedClinitMethods.containsKey(clinitName)
        ? resolveReplacement(emptiedClinitMethods.get(clinitName))
        : node;
  }

  private Multimap<String, Node> collectClinitReferences(Node root) {
    final Multimap<String, Node> clinitReferences = HashMultimap.create();
    NodeTraversal.traverse(
        compiler,
        root,
        new AbstractPostOrderCallback() {
          @Override
          public void visit(NodeTraversal t, Node node, Node parent) {
            String clinitName = getClinitMethodName(node);
            if (clinitName != null) {
              clinitReferences.put(clinitName, node);
            }
          }
        });
    return clinitReferences;
  }

  private List<Node> cleanEmptyClinitReferences(Multimap<String, Node> clinitReferences) {
    final List<Node> newChangedScopes = new ArrayList<>();
    // resolveReplacement step above should not require any particular iteration order of the
    // emptiedClinitMethods but we are using LinkedHashMap to be extra safe.
    for (Entry<String, Node> clinitReplacementEntry : emptiedClinitMethods.entrySet()) {
      String clinitName = clinitReplacementEntry.getKey();
      Node replacement = clinitReplacementEntry.getValue();

      Collection<Node> references = clinitReferences.removeAll(clinitName);
      for (Node reference : references) {
        Node changedScope = NodeUtil.getEnclosingChangeScopeRoot(reference.getParent());
        if (replacement == null) {
          NodeUtil.deleteFunctionCall(reference, compiler);
        } else {
          replacement = replacement.cloneTree();
          reference.replaceWith(replacement);
          compiler.reportChangeToChangeScope(changedScope);
          clinitReferences.put(getClinitMethodName(replacement), replacement);
        }
        newChangedScopes.add(changedScope);
      }
    }
    return newChangedScopes;
  }

  private static List<Node> getNonNestedParentScopeNodes(List<Node> changedScopeNodes) {
    return changedScopeNodes == null
        ? null
        : NodeUtil.removeNestedChangeScopeNodes(
            NodeUtil.getParentChangeScopeNodes(changedScopeNodes));
  }

  /** Removes redundant clinit calls inside method body if it is guaranteed to be called earlier. */
  private final class RedundantClinitPruner implements Callback, ChangeScopeRootCallback {

    @Override
    public void enterChangeScopeRoot(AbstractCompiler compiler, Node root) {
      // Reset the clinit call tracking when starting over on a new scope.
      clinitsCalledAtBranch = new HierarchicalSet<>(null);
      stateStack.clear();
    }

    private final Deque<HierarchicalSet<String>> stateStack = new ArrayDeque<>();
    private HierarchicalSet<String> clinitsCalledAtBranch = new HierarchicalSet<>(null);

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node node, Node parent) {
      if (NodeUtil.isFunctionDeclaration(node) || node.isScript()) {
        // In the case of function declarations, unlike function expressions, we don't know when the
        // function will be executed so assume there are no clinits already executed.
        // In the case of script roots, when using modules, we can not assume the scripts are going
        // to be executed in program order.
        stateStack.addLast(clinitsCalledAtBranch);
        clinitsCalledAtBranch = new HierarchicalSet<>(null);
      } 

      if (isNewControlBranch(parent)) {
        clinitsCalledAtBranch = new HierarchicalSet<>(clinitsCalledAtBranch);
        if (isClinitMethod(parent)) {
          // Adds itself as any of your children can assume clinit is already called.
          clinitsCalledAtBranch.add(NodeUtil.getName(parent));
        }
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      tryRemovingClinit(node);

      if (isNewControlBranch(parent)) {
        clinitsCalledAtBranch = clinitsCalledAtBranch.parent;
      }

      if (NodeUtil.isFunctionDeclaration(node) || node.isScript()) {
        // In the case of function declarations, unlike function expressions, we don't know when the
        // function will be executed so assume there are no clinits already executed.
        // In the case of script roots, when using modules, we can not assume the scripts are going
        // to be executed in program order.
        clinitsCalledAtBranch = stateStack.removeLast();
      }
    }

    private void tryRemovingClinit(Node node) {
      String clinitName = getClinitMethodName(node);
      if (clinitName == null) {
        return;
      }

      if (clinitsCalledAtBranch.add(clinitName)) {
        // This is the first time we are seeing this clinit so cannot remove it.
        return;
      }

      NodeUtil.deleteFunctionCall(node, compiler);
    }

    private boolean isNewControlBranch(Node n) {
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
  private final class LookAheadRedundantClinitPruner extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (!node.isExprResult()) {
        return;
      }

      // Find clinit calls.
      String clinitName = getClinitMethodName(node.getFirstChild());
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

      // Check that the clinit call is safe to prune.
      Node staticFnNode = var.getInitialValue();
      if (callsClinit(staticFnNode, clinitName) && hasSafeArguments(t, callOrNewNode)) {
        parent.removeChild(node);
        compiler.reportChangeToEnclosingScope(parent);
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
          return n.hasOneChild() ? getCallOrNewNode(n.getFirstFirstChild()) : null;
        default:
          return null;
      }
    }

    /** Returns whether the specified function contains a call to the specified clinit. */
    private boolean callsClinit(Node fnNode, String clinitName) {
      checkNotNull(clinitName);
      // TODO(michaelthomas): Consider checking all children, but watch out for return statements
      // that could short-circuit the clinit.
      Node child = fnNode.getLastChild().getFirstChild();
      return child != null
          && child.isExprResult()
          && clinitName.equals(getClinitMethodName(child.getFirstChild()));
    }
  }

  /** A traversal callback that removes the body of empty clinits. */
  private final class EmptyClinitPruner extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      if (!isClinitMethod(node)) {
        return;
      }

      trySubstituteEmptyFunction(node);
    }

    /** Clears the body of any functions that are equivalent to empty functions. */
    private void trySubstituteEmptyFunction(Node fnNode) {
      String fnQualifiedName = NodeUtil.getName(fnNode);

      // Ignore anonymous/constructor functions.
      if (Strings.isNullOrEmpty(fnQualifiedName)) {
        return;
      }

      Node body = fnNode.getLastChild();
      if (!body.hasChildren()) {
        return;
      }

      // Ensure that the first expression in the body is setting itself to the empty function
      Node firstExpr = body.getFirstChild();
      if (!isAssignToEmptyFn(firstExpr, fnQualifiedName)) {
        return;
      }

      Node secondExpr = firstExpr.getNext();
      Node replaceReferencesWith;
      if (secondExpr == null) {
        // There are no expressions in clinit so it is noop; remove all references.
        replaceReferencesWith = null;
      } else if (secondExpr.getNext() == null
          && secondExpr.isExprResult()
          && getClinitMethodName(secondExpr.getFirstChild()) != null) {
        // Only expression in clinit is a call to another clinit. We can safely replace all
        // references with the other clinit.
        replaceReferencesWith = secondExpr.getFirstChild();
      } else {
        // Clinit is not empty.
        return;
      }

      emptiedClinitMethods.put(fnQualifiedName, replaceReferencesWith);
      NodeUtil.deleteNode(firstExpr, compiler);
    }

    private boolean isAssignToEmptyFn(Node node, String enclosingFnName) {
      if (!NodeUtil.isExprAssign(node)) {
        return false;
      }

      Node lhs = node.getFirstFirstChild();
      Node rhs = node.getFirstChild().getLastChild();
      return NodeUtil.isEmptyFunctionExpression(rhs) && lhs.matchesQualifiedName(enclosingFnName);
    }
  }

  private static boolean isClinitMethod(Node node) {
    return node.isFunction() && isClinitMethodName(NodeUtil.getName(node));
  }

  private static String getClinitMethodName(Node node) {
    if (node.isCall()) {
      String fnName = node.getFirstChild().getQualifiedName();
      return isClinitMethodName(fnName) ? fnName : null;
    }
    return null;
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
    private final Set<T> currentSet = new HashSet<>();
    @Nullable private final HierarchicalSet<T> parent;

    public HierarchicalSet(@Nullable HierarchicalSet<T> parent) {
      this.parent = parent;
    }

    public boolean add(T o) {
      return !parentsContains(o) && currentSet.add(o);
    }

    /** Returns true either my parent or any of its parents contains the item. */
    private boolean parentsContains(T o) {
      return parent != null && (parent.currentSet.contains(o) || parent.parentsContains(o));
    }
  }
}
