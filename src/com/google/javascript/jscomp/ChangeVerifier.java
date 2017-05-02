/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;

/**
 * A Class to assist in AST change tracking verification.  To validate a "snapshot" is taken
 * before and "checkRecordedChanges" at the desired check point.
 */
public class ChangeVerifier {
  private final AbstractCompiler compiler;
  private final Map<Node, Node> map = new HashMap<>();
  private int snapshotChange;

  ChangeVerifier(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  ChangeVerifier snapshot(Node root) {
    // remove any existing snapshot data.
    map.clear();
    snapshotChange = compiler.getChangeStamp();

    Node snapshot = root.cloneTree();
    associateClones(root, snapshot);
    return this;
  }

  void checkRecordedChanges(Node current) {
    checkRecordedChanges("", current);
  }

  void checkRecordedChanges(String passName, Node root) {
    verifyScopeChangesHaveBeenRecorded(passName, root);
  }

  /**
   * Given an AST and its copy, map the root node of each scope of main to the
   * corresponding root node of clone
   */
  private void associateClones(Node n, Node snapshot) {
    // TODO(johnlenz): determine if MODULE_BODY is useful here.
    if (NodeUtil.isChangeScopeRoot(n)) {
      map.put(n, snapshot);
    }

    Node child = n.getFirstChild();
    Node snapshotChild = snapshot.getFirstChild();
    while (child != null) {
      associateClones(child, snapshotChild);
      child = child.getNext();
      snapshotChild = snapshotChild.getNext();
    }
  }

  /** Checks that the scope roots marked as changed have indeed changed */
  private void verifyScopeChangesHaveBeenRecorded(
      String passName, Node root) {
    final String passNameMsg = passName.isEmpty() ? "" : passName + ": ";

    NodeUtil.visitPreOrder(root,
        new Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isRoot()) {
              verifyRoot(n);
            } else if (NodeUtil.isChangeScopeRoot(n)) {
              Node clone = map.get(n);
              if (clone == null) {
                verifyNewNode(n);
              } else {
                verifyNodeChange(passNameMsg, n, clone);
              }
            }
          }
        },
        Predicates.<Node>alwaysTrue());
  }

  private void verifyNewNode(Node n) {
    // TODO(johnlenz): Verify the new nodes are properly tagged.
  }

  private void verifyRoot(Node root) {
    Preconditions.checkState(root.isRoot());
    if (root.getChangeTime() != 0) {
      throw new IllegalStateException("Root nodes should never be marked as changed.");
    }
  }

  private void verifyNodeChange(final String passNameMsg, Node n, Node snapshot) {
    if (n.isRoot()) {
      return;
    }
    if (n.getChangeTime() > snapshot.getChangeTime()) {
      if (isEquivalentToExcludingFunctions(n, snapshot)) {
        throw new IllegalStateException(
            passNameMsg + "unchanged scope marked as changed: " + n.toStringTree());
      }
    } else {
      if (!isEquivalentToExcludingFunctions(n, snapshot)) {
        throw new IllegalStateException(
            passNameMsg + "change scope not marked as changed: " + n.toStringTree());
      }
    }
  }

  /**
   * @return Whether the two node are equivalent while ignoring
   * differences any descendant functions differences.
   */
  private static boolean isEquivalentToExcludingFunctions(
      Node thisNode, Node thatNode) {
    if (thisNode == null || thatNode == null) {
      return thisNode == null && thatNode == null;
    }
    if (!thisNode.isEquivalentWithSideEffectsToShallow(thatNode)) {
      return false;
    }
    if (thisNode.getChildCount() != thatNode.getChildCount()) {
      return false;
    }
    Node thisChild = thisNode.getFirstChild();
    Node thatChild = thatNode.getFirstChild();
    while (thisChild != null && thatChild != null) {
      if (thisChild.isFunction() || thisChild.isScript()) {
        // Don't compare function expression name, parameters or bodies.
        // But do check that that the node is there.
        if (thatChild.getToken() != thisChild.getToken()) {
          return false;
        }
        // Only compare function names for function declarations (not function expressions)
        // as they change the outer scope definition.
        if (thisChild.isFunction() && NodeUtil.isFunctionDeclaration(thisChild)) {
          String thisName = thisChild.getFirstChild().getString();
          String thatName = thatChild.getFirstChild().getString();
          if (!thisName.equals(thatName)) {
            return false;
          }
        }
      } else if (!isEquivalentToExcludingFunctions(thisChild, thatChild)) {
        return false;
      }
      thisChild = thisChild.getNext();
      thatChild = thatChild.getNext();
    }
    return true;
  }
}

