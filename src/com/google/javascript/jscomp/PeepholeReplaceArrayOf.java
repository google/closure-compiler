/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Replace `Array.of` with an array literal.
 */
class PeepholeReplaceArrayOf extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    if (subtree.isCall()){
      return tryReplaceArrayOf(subtree);
    }
    return subtree;
  }

  private Node tryReplaceArrayOf(Node subtree) {
    checkArgument(subtree.isCall(), subtree);
    Node callTarget = checkNotNull(subtree.getFirstChild());

    if (NodeUtil.isGet(callTarget)) {
      Node clazz = callTarget.getFirstChild();
      if (clazz.isQualifiedName()) {
        switch (clazz.getQualifiedName()) {
          case "Array":
            if (clazz.getNext().getString().equals("of")) {
              return replaceArrayOf(subtree);
            }
            break;
          default: // fall out
        }
      }
    }

    return subtree;
  }

  private Node replaceArrayOf(Node subtree) {
    subtree.removeFirstChild();

    Node arraylit = new Node(Token.ARRAYLIT);
    arraylit.addChildrenToBack(subtree.removeChildren());
    subtree.replaceWith(arraylit);
    reportChangeToEnclosingScope(arraylit);
    return arraylit;
  }
}
