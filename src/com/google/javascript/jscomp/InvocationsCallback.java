/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Traversal callback that finds method invocations of the form
 *
 * <pre>
 * call
 *   getprop
 *     ...
 *     string
 *   ...
 * </pre>
 *
 * and invokes a method defined by subclasses for processing these invocations.
 *
 */
abstract class InvocationsCallback extends AbstractPostOrderCallback {

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isCall()) {
      return;
    }

    Node function = n.getFirstChild();

    if (!function.isGetProp()) {
      return;
    }

    Node nameNode = function.getFirstChild().getNext();

    // Don't care about numerical or variable indexes
    if (!nameNode.isString()) {
      return;
    }

    visit(t, n, parent, nameNode.getString());
  }

  /**
   * Called for each callnode that is a method invocation.
   *
   * @param callNode node of type call
   * @param parent parent of callNode
   * @param callName name of method invoked by first child of call
   */
  abstract void visit(NodeTraversal t, Node callNode, Node parent,
      String callName);
}
