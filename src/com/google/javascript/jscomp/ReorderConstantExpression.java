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

/**
 * Reorder constant expression hoping for a better compression.
 * ex. x === 0 -> 0 === x
 * After reordering, expressions like 0 === x and 0 === y may have higher
 * compression together than their original counterparts.
 *
 */
class ReorderConstantExpression implements CompilerPass {

  private AbstractCompiler compiler;

  ReorderConstantExpression(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node node) {
    // if the operator is symertric
    if (NodeUtil.isSymmetricOperation(node)
        || NodeUtil.isRelationalOperation(node)) {
      // right value is immutable and left is not
      if (NodeUtil.isImmutableValue(node.getLastChild())
          && !NodeUtil.isImmutableValue(node.getFirstChild())) {

        // if relational, get the inverse operator.
        if (NodeUtil.isRelationalOperation(node)){
          int inverseOperator = NodeUtil.getInverseOperator(node.getType());
          node.setType(inverseOperator);
        }

        // swap them
        Node firstNode = node.getFirstChild().detachFromParent();
        Node lastNode = node.getLastChild().detachFromParent();

        node.addChildrenToFront(lastNode);
        node.addChildrenToBack(firstNode);
        this.compiler.reportCodeChange();
      }
    }

    // process children then siblings.
    if (node.hasChildren()) {
      Node child = node.getFirstChild();
      while (child != null) {
        process(externs, child);
        child = child.getNext();
      }
    }
  }
}
