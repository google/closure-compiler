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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Reorder constant expression hoping for a better compression.
 * ex. x === 0 -> 0 === x
 * After reordering, expressions like 0 === x and 0 === y may have higher
 * compression together than their original counterparts.
 *
 */
class ReorderConstantExpression extends AbstractPeepholeOptimization {

  // TODO(user): Rename this pass to PeepholeReorderConstantExpression
  // to follow our naming convention.
  @Override
  Node optimizeSubtree(Node subtree) {
    // if the operator is symmetric
    if (NodeUtil.isSymmetricOperation(subtree)
        || NodeUtil.isRelationalOperation(subtree)) {
      if (NodeUtil.precedence(subtree.getToken())
          == NodeUtil.precedence(subtree.getFirstChild().getToken())) {
        // then flipping would add parens
        return subtree;
      }
      // right value is immutable and left is not
      if (NodeUtil.isImmutableValue(subtree.getLastChild())
          && !NodeUtil.isImmutableValue(subtree.getFirstChild())) {

        // if relational, get the inverse operator.
        if (NodeUtil.isRelationalOperation(subtree)){
          Token inverseOperator = NodeUtil.getInverseOperator(subtree.getToken());
          subtree.setToken(inverseOperator);
        }

        // swap them
        Node firstNode = subtree.getFirstChild().detach();
        Node lastNode = subtree.getLastChild().detach();

        subtree.addChildToFront(lastNode);
        subtree.addChildToBack(firstNode);
        reportCodeChange();
      }
    }
    return subtree;
  }
}
