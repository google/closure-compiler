/*
 * Copyright 2008 Google Inc.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
*
 *
 */
class NodeTypeNormalizer implements CompilerPass {

  private CodeChangeHandler changeHandler;

  NodeTypeNormalizer() {
    this(null);
  }

  NodeTypeNormalizer(CodeChangeHandler changeHandler) {
    this.changeHandler = changeHandler;
  }

  private void reportChange() {
    if (changeHandler != null) {
      changeHandler.reportChange();
    }
  }

  @Override
  public void process(Node externs, Node root) {
    normalizeNodeTypes(root);
    normalizeJsDocAnnotations(root);
  }

  /**
   * Normalize where JSDoc annotations appear on the AST.
   *
   * In the AST that Rhino gives us, it needs to make a distinction
   * between jsdoc on the object literal node and jsdoc on the object literal
   * value. For example,
   * <pre>
   * var x = {
   *   / JSDOC /
   *   a: 'b',
   *   c: / JSDOC / 'd'
   * };
   * </pre>
   *
   * But in few narrow cases (in particular, function literals), it's
   * a lot easier for us if the doc is attached to the value.
   */
  private void normalizeJsDocAnnotations(Node n) {
    if (n.getType() == Token.OBJECTLIT) {
      for (Node key = n.getFirstChild();
           key != null; key = key.getNext().getNext()) {
        Node value = key.getNext();
        if (key.getJSDocInfo() != null &&
            key.getNext().getType() == Token.FUNCTION) {
          value.setJSDocInfo(key.getJSDocInfo());
        }
      }
    }

    for (Node child = n.getFirstChild();
         child != null; child = child.getNext()) {
      normalizeJsDocAnnotations(child);
    }
  }

  /**
   * Covert EXPR_VOID to EXPR_RESULT to simplify the rest of the code.
   */
  private void normalizeNodeTypes(Node n) {
    if (n.getType() == Token.EXPR_VOID) {
      n.setType(Token.EXPR_RESULT);
      reportChange();
    }

    // Remove unused properties to minimize differences between ASTs
    // produced by the two parsers.
    if (n.getType() == Token.FUNCTION) {
      n.removeProp(Node.FUNCTION_PROP);
      reportChange();
    }

    normalizeBlocks(n);

    for (Node child = n.getFirstChild();
         child != null; child = child.getNext()) {
      // This pass is run during the CompilerTestCase validation, so this
      // parent pointer check serves as a more general check.
      Preconditions.checkState(child.getParent() == n);

      normalizeNodeTypes(child);
    }
  }

  /**
   * Add blocks to IF, WHILE, DO, etc.
   */
  private void normalizeBlocks(Node n) {
    if (NodeUtil.isControlStructure(n)
        && n.getType() != Token.LABEL
        && n.getType() != Token.SWITCH) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (NodeUtil.isControlStructureCodeBlock(n,c) &&
            c.getType() != Token.BLOCK) {
          Node newBlock = new Node(Token.BLOCK);
          n.replaceChild(c, newBlock);
          if (c.getType() != Token.EMPTY) {
            newBlock.addChildrenToFront(c);
          } else {
            newBlock.setWasEmptyNode(true);
          }
          c = newBlock;
          reportChange();
        }
      }
    }
  }

}
