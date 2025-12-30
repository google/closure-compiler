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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A Class to assist in AST change tracking verification.  To validate a "snapshot" is taken
 * before and "checkRecordedChanges" at the desired check point.
 */
public class ChangeVerifier {
  private final ChangeTracker changeTracker;
  private final BiMap<Node, Node> clonesByCurrent = HashBiMap.create();
  private int snapshotChange;

  ChangeVerifier(AbstractCompiler compiler) {
    this.changeTracker = compiler.getChangeTracker();
  }

  @CanIgnoreReturnValue
  ChangeVerifier snapshot(Node root) {
    // remove any existing snapshot data.
    clonesByCurrent.clear();
    snapshotChange = changeTracker.getChangeStamp();

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
    if (n.isRoot() || ChangeTracker.isChangeScopeRoot(n)) {
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
    final Set<Node> snapshotScopeNodes = new LinkedHashSet<>();
    NodeUtil.visitPreOrder(
        clonesByCurrent.get(root),
        new Visitor() {
          @Override
          public void visit(Node oldNode) {
            if (ChangeTracker.isChangeScopeRoot(oldNode)) {
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
            } else if (ChangeTracker.isChangeScopeRoot(n)) {
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
    EqualsResult result = getInequivalenceReasonExcludingFunctions(n, snapshot);
    if (n.getChangeTime() > snapshot.getChangeTime()) {
      // If the current node is marked as changed (changeTime > snapshot.getChangeTime)
      // but is actually equal to the snapshot, that's an error.
      if (result.equals()) {
        throw new IllegalStateException(
            passNameMsg + "unchanged scope marked as changed: " + getNameForNode(n));
      }
    } else {
      // If the current node is NOT marked as changed (changeTime <= snapshot.getChangeTime)
      // but is actually different from the snapshot, that's an error.
      if (!result.equals()) {
        throw new IllegalStateException(
            String.format(
                """
                "%schanged scope not marked as changed: %s.
                %s
                Ancestor nodes:
                %s
                """,
                passNameMsg,
                getNameForNode(n),
                result.errorMessage.get(),
                path(n, result.errorNode())));
      }
    }
  }

  String getNameForNode(Node n) {
    String sourceName = NodeUtil.getSourceName(n);
    switch (n.getToken()) {
      case SCRIPT -> {
        return "SCRIPT: " + sourceName;
      }
      case FUNCTION -> {
        String fnName = NodeUtil.getNearestFunctionName(n);
        if (fnName == null) {
          fnName = "anonymous@" + n.getLineno() + ":" + n.getCharno();
        }
        return "FUNCTION: " + fnName + " in " + sourceName;
      }
      default -> throw new IllegalStateException("unexpected Node type");
    }
  }

  /** Returns the path from the ancestor to the child node. */
  private String path(Node ancestor, Node child) {
    ArrayList<String> childToAncestor = new ArrayList<>();
    for (Node current = child; current != ancestor; current = current.getParent()) {
      childToAncestor.add(current.toString());
    }
    childToAncestor.add(ancestor.toString());
    Collections.reverse(childToAncestor);
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < childToAncestor.size(); i++) {
      result.repeat(' ', i * 2); // indent
      result.append(childToAncestor.get(i));
      result.append("\n");
    }
    return result.toString();
  }

  private record EqualsResult(boolean equals, Node errorNode, Supplier<String> errorMessage) {
    static EqualsResult equal() {
      return new EqualsResult(true, null, () -> null);
    }

    static EqualsResult notEqual(Node errorNode, String error, Object after, Object before) {
      return new EqualsResult(
          false,
          errorNode,
          () ->
              String.format(
                  """
                  %s
                  Before: %s
                  After:  %s
                  """,
                  error, before, after));
    }
  }

  /**
   * Checks whether the two given nodes are equivalent, while ignoring differences in descendant
   * functions.
   */
  private static EqualsResult getInequivalenceReasonExcludingFunctions(
      Node thisNode, Node thatNode) {
    checkNotNull(thisNode);
    checkNotNull(thatNode);
    if (thisNode.getChildCount() != thatNode.getChildCount()) {
      return EqualsResult.notEqual(
          thisNode,
          "differing child count",
          thisNode + ": " + thisNode.getChildCount(),
          thatNode + ": " + thatNode.getChildCount());
    }
    if (!thisNode.isEquivalentWithSideEffectsToShallow(thatNode)) {
      return EqualsResult.notEqual(thisNode, "shallow inequivalence", thisNode, thatNode);
    }
    if (thisNode.isFunction() && thatNode.isFunction()) {
      if (NodeUtil.isFunctionDeclaration(thisNode) != NodeUtil.isFunctionDeclaration(thatNode)) {
        return EqualsResult.notEqual(
            thisNode, "mismatched isFunctionDeclaration", thisNode, thatNode);
      }
    }

    Node thisChild = thisNode.getFirstChild();
    Node thatChild = thatNode.getFirstChild();
    while (thisChild != null && thatChild != null) {
      if (thisChild.isFunction() || thisChild.isScript()) {
        // Don't compare function expression name, parameters or bodies.
        // But do check that that the node is there.
        if (thatChild.getToken() != thisChild.getToken()) {
          return EqualsResult.notEqual(thisNode, "different tokens", thisChild, thatChild);
        }
        // Only compare function names for function declarations (not function expressions)
        // as they change the outer scope definition.
        if (thisChild.isFunction() && NodeUtil.isFunctionDeclaration(thisChild)) {
          String thisName = thisChild.getFirstChild().getString();
          String thatName = thatChild.getFirstChild().getString();
          if (!thisName.equals(thatName)) {
            return EqualsResult.notEqual(thisNode, "function name changed", thisName, thatName);
          }
        }
      } else {
        EqualsResult result = getInequivalenceReasonExcludingFunctions(thisChild, thatChild);
        if (!result.equals()) {
          return result;
        }
      }
      thisChild = thisChild.getNext();
      thatChild = thatChild.getNext();
    }

    return EqualsResult.equal();
  }
}
