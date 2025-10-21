/*
 * Copyright 2025 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;

/** Tracks various kind of changes during a single compilation */
public final class ChangeTracker {
  // Starts at 0, increases as "interesting" things happen.
  // Nothing happens at time START_TIME, the first pass starts at time 1.
  // The correctness of scope-change tracking relies on Node/getIntProp
  // returning 0 if the custom attribute on a node hasn't been set.
  private int changeStamp = 1;
  private final Timeline<Node> changeTimeline = new Timeline<>();
  private final RecentChange recentChange = new RecentChange();
  private final List<CodeChangeHandler> codeChangeHandlers = new ArrayList<>();

  /** Registers a listener for code change events. */
  void addChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.add(handler);
  }

  /** Removes a listener for code change events. */
  void removeChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.remove(handler);
  }

  RecentChange getRecentChange() {
    return recentChange;
  }

  /**
   * Marks modifications to the enclosing change scope, as defined by {@link
   * #isChangeScopeRoot(Node)}
   */
  public void reportChangeToEnclosingScope(Node n) {
    recordChange(getChangeScopeForNode(n));
    notifyChangeHandlers();
  }

  /** Marks modifications to a function or script node */
  public void reportChangeToChangeScope(Node changeScopeRoot) {
    checkState(changeScopeRoot.isScript() || changeScopeRoot.isFunction());
    recordChange(changeScopeRoot);
    notifyChangeHandlers();
  }

  /** Recurses through a tree, marking all function nodes as changed. */
  public void markNewScopesChanged(Node node) {
    if (node.isFunction()) {
      reportChangeToChangeScope(node);
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      markNewScopesChanged(child);
    }
  }

  /**
   * Marks a specific function node as known to be deleted. Is part of having accurate change
   * tracking which is necessary to streamline optimizations.
   */
  public void reportFunctionDeleted(Node n) {
    checkState(n.isFunction());
    n.setDeleted(true);
    changeTimeline.remove(n);
  }

  /**
   * Returns an accumulation of changed scope nodes since the last time the given pass was run.
   *
   * <p>A returned empty list means no scope nodes have changed since the last run and a returned
   * null means this is the first time the pass has run.
   */
  List<Node> getChangedScopeNodesForPass(String passName) {
    List<Node> changedScopeNodes = changeTimeline.getSince(passName);
    changeTimeline.mark(passName);
    return changedScopeNodes;
  }

  /** Returns a monotonically increasing value to identify a change */
  int getChangeStamp() {
    return changeStamp;
  }

  /** Indicates that the current change stamp has been used */
  void incrementChangeStamp() {
    changeStamp++;
  }

  /** Resets the change stamp to 1 */
  void resetChangeStamp() {
    changeStamp = 1;
  }

  /**
   * A change scope does not directly correspond to a language scope but is an internal grouping of
   * changes.
   *
   * @return Whether the node represents a change scope root.
   */
  static boolean isChangeScopeRoot(Node n) {
    return (n.isScript() || n.isFunction());
  }

  /** Returns the change scope root. */
  static Node getEnclosingChangeScopeRoot(Node n) {
    while (n != null && !isChangeScopeRoot(n)) {
      n = n.getParent();
    }
    return n;
  }

  private void notifyChangeHandlers() {
    for (CodeChangeHandler handler : codeChangeHandlers) {
      handler.reportChange();
    }
  }

  private Node getChangeScopeForNode(Node n) {
    /*
     * Compiler change reporting usually occurs after the AST change has already occurred. In the
     * case of node removals those nodes are already removed from the tree and so have no parent
     * chain to walk. In these situations changes are reported instead against what (used to be)
     * their parent. If that parent is itself a script node then it's important to be able to
     * recognize it as the enclosing scope without first stepping to its parent as well.
     */
    if (n.isScript()) {
      return n;
    }

    Node enclosingScopeNode = getEnclosingChangeScopeRoot(n.getParent());
    if (enclosingScopeNode == null) {
      throw new IllegalStateException(
          "An enclosing scope is required for change reports but node " + n + " doesn't have one.");
    }
    return enclosingScopeNode;
  }

  private void recordChange(Node n) {
    if (n.isDeleted()) {
      // Some complicated passes (like SmartNameRemoval) might both change and delete a scope in
      // the same pass, and they might even perform the change after the deletion because of
      // internal queueing. Just ignore the spurious attempt to mark changed after already marking
      // deleted. There's no danger of deleted nodes persisting in the AST since this is enforced
      // separately in ChangeVerifier.
      return;
    }

    n.setChangeTime(changeStamp);
    // Every code change happens at a different time
    changeStamp++;
    changeTimeline.add(n);
  }
}
