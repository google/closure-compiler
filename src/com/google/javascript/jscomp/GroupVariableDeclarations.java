/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Iterator;
import java.util.Set;

/**
 * Groups multiple variable declarations (that may or may not be contiguous)
 * in the same scope into a single one. i.e.
 *
 * <pre>
 * var a, b = 10;
 * f1();
 * var c, d;
 * ... some other code ...
 * var x, y, z = 100;
 * ... some other code ...
 * var p = 200, q = 300;
 * </pre>
 *
 * becomes:
 *
 * <pre>
 * var a, b = 10, c, d, x, y, z;
 * f1();
 * ... some other code ...
 * z = 100;
 * ... some other code ...
 * var p = 200, q = 300;
 * </pre>
 *
 * This reduces the generated uncompressed code size.
 *
 * For any scope, we use the first VAR statement as the statement to collapse
 * the other declarations into. For other VAR statements in the scope, we only
 * consider ones that either (a) have no variable that is initialized in the
 * the statement, or (b) have exactly one variable that is initialized (e.g.
 * the "var x, y, z = 100;" statement in the example above. VAR statements
 * with more than one variable initialization are not collapsed. This is
 * because doing so would increase uncompressed code size.
 *
 */
class GroupVariableDeclarations implements CompilerPass, ScopedCallback {
  private final AbstractCompiler compiler;

  GroupVariableDeclarations(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Set<Node> varNodes = Sets.newLinkedHashSet();
    Iterator<Var> scopeVarIter = t.getScope().getVars();
    while (scopeVarIter.hasNext()) {
      Node parentNode = scopeVarIter.next().getParentNode();
      if (parentNode.isVar()) {
        varNodes.add(parentNode);
      }
    }
    if (varNodes.size() <= 1) {
      return;
    }
    Iterator<Node> varNodeIter = varNodes.iterator();
    Node firstVarNode = varNodeIter.next();
    while (varNodeIter.hasNext()) {
      Node varNode = varNodeIter.next();
      applyGroupingToVar(firstVarNode, varNode);
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
                                Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  /**
   * Attempts to collapse groupVar. This can only happen if groupVar has at most
   * one variable initialization (it may have multiple variable declarations).
   * If successful, then detaches groupVar's children and appends them to
   * firstVar
   *
   * @param firstVar The first VAR {@code Node} in that scope. This is the node
   *                 that we want to collapse groupVar into
   * @param groupVar The VAR {@code Node} that we want to try collapsing
   *                 into the first VAR node of that scope
   */
  private void applyGroupingToVar(Node firstVar, Node groupVar) {
    Node child = groupVar.getFirstChild();
    // if some variable is initialized, then the corresponding NAME node will be
    // stored here
    Node initializedName = null;
    while (child != null) {
      if (child.hasChildren()) {
        // check that no more than one var is initialized
        if (initializedName != null) {
          return;
        }
        initializedName = child;
      }
      child = child.getNext();
    }

    // we will be modifying the groupVar subtree so get its parent
    Node groupVarParent = groupVar.getParent();


    if (initializedName != null) {
      if (NodeUtil.isForIn(groupVarParent)) {
        // The target of the for-in expression must be an assignable expression.
        return;
      }

      // we have an initialized var in the VAR node. We will replace the
      // VAR node with an assignment.

      // first create a detached childless clone of initializedName.
      Node clone = initializedName.cloneNode();
      // replace
      groupVar.replaceChild(initializedName, clone);
      // add the assignment now.
      Node initializedVal = initializedName.removeFirstChild();
      Node assignmentNode = IR.assign(initializedName, initializedVal);
      if (groupVarParent.isFor()) {
        // Handle For and For-In Loops specially. For these, we do not need
        // to construct an EXPR_RESULT node.
        groupVarParent.replaceChild(groupVar, assignmentNode);
      } else {
        Node exprNode = NodeUtil.newExpr(assignmentNode);
        groupVarParent.replaceChild(groupVar, exprNode);
      }
    } else {
      // There is no initialized var. But we need to handle FOR and
      // FOR-IN loops specially
      if (groupVarParent.isFor()) {
        if (NodeUtil.isForIn(groupVarParent)) {
          // In For-In loop, we replace the VAR node with a NAME node
          Node nameNodeClone = groupVar.getFirstChild().cloneNode();
          groupVarParent.replaceChild(groupVar, nameNodeClone);
        } else {
          // In For loop, we replace the VAR node with an EMPTY node
          Node emptyNode = IR.empty();
          groupVarParent.replaceChild(groupVar, emptyNode);
        }
      } else {
        // we can safely remove the VAR node
        groupVarParent.removeChild(groupVar);
      }
    }

    Node children = groupVar.removeChildren();
    firstVar.addChildrenToBack(children);

    compiler.reportCodeChange();
  }
}
