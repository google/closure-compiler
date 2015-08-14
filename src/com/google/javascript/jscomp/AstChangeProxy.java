/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Proxy that provides a high level interface that compiler passes can
 * use to replace or remove sections of the AST.
 *
 */
class AstChangeProxy {

  /**
   * Interface used to notify client code about changes done by
   * AstChangeProxy.
   */
  interface ChangeListener {

    /**
     * Notifies clients about node removals.
     */
    void nodeRemoved(Node node);
  }

  private final List<ChangeListener> listeners;

  AstChangeProxy() {
    listeners = new ArrayList<>();
  }

  /**
   * Registers a change listener.
   */
  final void registerListener(ChangeListener listener) {
    listeners.add(listener);
  }

  /**
   * Unregisters a change listener.
   */
  final void unregisterListener(ChangeListener listener) {
    listeners.remove(listener);
  }

  /**
   * Notifies listeners about a removal.
   */
  private void notifyOfRemoval(Node node) {
    for (ChangeListener listener : listeners) {
      listener.nodeRemoved(node);
    }
  }

  /**
   * Removes a node from the parent's child list.
   */
  final void removeChild(Node parent, Node node) {
    parent.removeChild(node);

    notifyOfRemoval(node);
  }

  /**
   * Replaces a node from the parent's child list.
   */
  final void replaceWith(Node parent, Node node, Node replacement) {
    replaceWith(parent, node, ImmutableList.of(replacement));
  }

  /**
   * Replaces a node with the provided list.
   */
  final void replaceWith(Node parent, Node node, List<Node> replacements) {
    Preconditions.checkNotNull(replacements, "\"replacements\" is null.");

    int size = replacements.size();

    if ((size == 1) && node.isEquivalentTo(replacements.get(0))) {
      // trees are equal... don't replace
      return;
    }

    int parentType = parent.getType();

    Preconditions.checkState(size == 1 ||
        parentType == Token.BLOCK ||
        parentType == Token.SCRIPT ||
        parentType == Token.LABEL);

    if (parentType == Token.LABEL && size != 1) {
      Node block = IR.block();
      for (Node newChild : replacements) {
        newChild.useSourceInfoIfMissingFrom(node);
        block.addChildToBack(newChild);
      }
      parent.replaceChild(node, block);
    } else {
      for (Node newChild : replacements) {
        newChild.useSourceInfoIfMissingFrom(node);
        parent.addChildBefore(newChild, node);
      }
      parent.removeChild(node);
    }
    notifyOfRemoval(node);
  }
}
