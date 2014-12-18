/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Prepare the AST before we do any checks or optimizations on it.
 *
 * This pass must run. It should bring the AST into a consistent state,
 * and add annotations where necessary. It should not make any transformations
 * on the tree that would lose source information, since we need that source
 * information for checks.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class PrepareAst implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean checkOnly;

  PrepareAst(AbstractCompiler compiler) {
    this(compiler, false);
  }

  PrepareAst(AbstractCompiler compiler, boolean checkOnly) {
    this.compiler = compiler;
    this.checkOnly = checkOnly;
  }

  private void reportChange() {
    if (checkOnly) {
      throw new IllegalStateException("normalizeNodeType constraints violated");
    }
  }

  @Override
  public void process(Node externs, Node root) {
    if (checkOnly) {
      normalizeNodeTypes(root);
    } else {
      // Don't perform "PrepareAnnotations" when doing checks as
      // they currently aren't valid during sanity checks.  In particular,
      // they DIRECT_EVAL shouldn't be applied after inlining has been
      // performed.
      if (externs != null) {
        NodeTraversal.traverse(
            compiler, externs, new PrepareAnnotations());
      }
      if (root != null) {
        NodeTraversal.traverse(
            compiler, root, new PrepareAnnotations());
      }
    }
  }

  /**
   * Covert EXPR_VOID to EXPR_RESULT to simplify the rest of the code.
   */
  private void normalizeNodeTypes(Node n) {
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
        && !n.isLabel()
        && !n.isSwitch()) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (NodeUtil.isControlStructureCodeBlock(n, c) && !c.isBlock()) {
          Node newBlock = IR.block().srcref(n);
          n.replaceChild(c, newBlock);
          newBlock.setIsAddedBlock(true);
          if (!c.isEmpty()) {
            newBlock.addChildrenToFront(c);
          }
          c = newBlock;
          reportChange();
        }
      }
    }
  }

  /**
   * Normalize where annotations appear on the AST. Copies
   * around existing JSDoc annotations as well as internal annotations.
   */
  static class PrepareAnnotations
      implements NodeTraversal.Callback {

    PrepareAnnotations() {
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isObjectLit()) {
        normalizeObjectLiteralAnnotations(n);
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          annotateCalls(n);
          break;
      }
    }

    private void normalizeObjectLiteralAnnotations(Node objlit) {
      Preconditions.checkState(objlit.isObjectLit());
      for (Node key = objlit.getFirstChild();
           key != null; key = key.getNext()) {
        Node value = key.getFirstChild();
        normalizeObjectLiteralKeyAnnotations(objlit, key, value);
      }
    }

    /**
     * There are two types of calls we are interested in calls without explicit
     * "this" values (what we are call "free" calls) and direct call to eval.
     */
    private static void annotateCalls(Node n) {
      Preconditions.checkState(n.isCall());

      // Keep track of of the "this" context of a call.  A call without an
      // explicit "this" is a free call.
      Node first = n.getFirstChild();

      // ignore cast nodes.
      while (first.isCast()) {
        first = first.getFirstChild();
      }

      if (!NodeUtil.isGet(first)) {
        n.putBooleanProp(Node.FREE_CALL, true);
      }

      // Keep track of the context in which eval is called. It is important
      // to distinguish between "(0, eval)()" and "eval()".
      if (first.isName() &&
          "eval".equals(first.getString())) {
        first.putBooleanProp(Node.DIRECT_EVAL, true);
      }
    }

    /**
     * In the AST that Rhino gives us, it needs to make a distinction
     * between JsDoc on the object literal node and JsDoc on the object literal
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
    private static void normalizeObjectLiteralKeyAnnotations(
        Node objlit, Node key, Node value) {
      Preconditions.checkState(objlit.isObjectLit());
      if (key.getJSDocInfo() != null &&
          value.isFunction()) {
        value.setJSDocInfo(key.getJSDocInfo());
      }
    }
  }
}
