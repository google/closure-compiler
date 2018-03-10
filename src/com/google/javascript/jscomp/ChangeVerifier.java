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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * A Class to assist in AST change tracking verification.  To validate a "snapshot" is taken
 * before and "checkRecordedChanges" at the desired check point.
 */
public class ChangeVerifier {
  private final AbstractCompiler compiler;
  private final BiMap<Node, Node> clonesByCurrent = HashBiMap.create();
  private int snapshotChange;

  ChangeVerifier(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  ChangeVerifier snapshot(Node root) {
    // remove any existing snapshot data.
    clonesByCurrent.clear();
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
    if (n.isRoot() || NodeUtil.isChangeScopeRoot(n)) {
      clonesByCurrent.put(n, snapshot);
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
  private void verifyScopeChangesHaveBeenRecorded(String passName, Node root) {
    final String passNameMsg = passName.isEmpty() ? "" : passName + ": ";

    // Gather all the scope nodes that existed when the snapshot was taken.
    final Set<Node> snapshotScopeNodes = new HashSet<>();
    NodeUtil.visitPreOrder(
        clonesByCurrent.get(root),
        new Visitor() {
          @Override
          public void visit(Node oldNode) {
            if (NodeUtil.isChangeScopeRoot(oldNode)) {
              snapshotScopeNodes.add(oldNode);
            }
          }
        });

    NodeUtil.visitPreOrder(
        root,
        new Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isRoot()) {
              verifyRoot(n);
            } else if (NodeUtil.isChangeScopeRoot(n)) {
              Node clone = clonesByCurrent.get(n);
              // Remove any scope nodes that still exist.
              snapshotScopeNodes.remove(clone);
              verifyNode(passNameMsg, n);
              if (clone == null) {
                verifyNewNode(passNameMsg, n);
              } else {
                verifyNodeChange(passNameMsg, n, clone);
              }
            }
          }
        });

    // Only actually deleted snapshot scope nodes should remain.
    verifyDeletedScopeNodes(passNameMsg, snapshotScopeNodes);
  }

  private void verifyDeletedScopeNodes(
      final String passNameMsg, final Set<Node> deletedScopeNodes) {
    for (Node snapshotScopeNode : deletedScopeNodes) {
      Node currentNode = clonesByCurrent.inverse().get(snapshotScopeNode);
      // If the original was marked deleted, that's fine, ignore it.
      if (currentNode.isDeleted()) {
        continue;
      }

      // But if it was deleted but not marked deleted, that's a problem.
      throw new IllegalStateException(
          passNameMsg + "deleted scope was not reported:\n" + currentNode.toStringTree());
    }
  }

  private void verifyNode(String passNameMsg, Node n) {
    if (n.isDeleted()) {
      throw new IllegalStateException(
          passNameMsg + "existing scope is improperly marked as deleted:\n" + n.toStringTree());
    }
  }

  private void verifyNewNode(String passNameMsg, Node n) {
    int changeTime = n.getChangeTime();
    if (changeTime == 0 || changeTime < snapshotChange) {
      throw new IllegalStateException(
          passNameMsg + "new scope not explicitly marked as changed:\n" + n.toStringTree());
    }
  }

  private void verifyRoot(Node root) {
    checkState(root.isRoot());
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
            passNameMsg + "unchanged scope marked as changed: " + getNameForNode(n));
      }
    } else {
      if (!isEquivalentToExcludingFunctions(n, snapshot)) {
        throw new IllegalStateException(
            passNameMsg + "changed scope not marked as changed: " + getNameForNode(n));
      }
    }
  }

  String getNameForNode(Node n) {
    String sourceName = NodeUtil.getSourceName(n);
    switch (n.getToken()) {
      case SCRIPT:
        return "SCRIPT: " + sourceName;
      case FUNCTION:
        String fnName = NodeUtil.getNearestFunctionName(n);
        if (fnName == null) {
          fnName = "anonymous@" + n.getLineno() + ":" + n.getCharno();
        }
        return "FUNCTION: " + fnName + " in " + sourceName;
      default:
        throw new IllegalStateException("unexpected Node type");
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

    if (thisNode.isFunction() && thatNode.isFunction()) {
      if (NodeUtil.isFunctionDeclaration(thisNode) != NodeUtil.isFunctionDeclaration(thatNode)) {
        return false;
      }
    }

    if (thisNode.getParent() != null && thisNode.getParent().isParamList()) {
      if (thisNode.isUnusedParameter() != thatNode.isUnusedParameter()) {
        return false;
      }
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

