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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
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
      Preconditions.checkState(false, "normalizeNodeType constraints violated");
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
            compiler, externs, new PrepareAnnotations(compiler));
      }
      if (root != null) {
        NodeTraversal.traverse(
            compiler, root, new PrepareAnnotations(compiler));
      }
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
      Preconditions.checkState(n.getProp(Node.FUNCTION_PROP) == null);
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
          Node newBlock = new Node(Token.BLOCK, n.getLineno(), n.getCharno());
          newBlock.copyInformationFrom(n);
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

  /**
   * Normalize where annotations appear on the AST. Copies
   * around existing JSDoc annotations as well as internal annotations.
   */
  static class PrepareAnnotations
      implements NodeTraversal.Callback {

    private final CodingConvention convention;

    PrepareAnnotations(AbstractCompiler compiler) {
      this.convention = compiler.getCodingConvention();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.OBJECTLIT) {
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

        case Token.FUNCTION:
          annotateFunctions(n, parent);
          annotateDispatchers(n, parent);
          break;
      }
    }

    private void normalizeObjectLiteralAnnotations(Node objlit) {
      Preconditions.checkState(objlit.getType() == Token.OBJECTLIT);
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
    private void annotateCalls(Node n) {
      Preconditions.checkState(n.getType() == Token.CALL);

      // Keep track of of the "this" context of a call.  A call without an
      // explicit "this" is a free call.
      Node first = n.getFirstChild();
      if (!NodeUtil.isGet(first)) {
        n.putBooleanProp(Node.FREE_CALL, true);
      }

      // Keep track of the context in which eval is called. It is important
      // to distinguish between "(0, eval)()" and "eval()".
      if (first.getType() == Token.NAME &&
          "eval".equals(first.getString())) {
        first.putBooleanProp(Node.DIRECT_EVAL, true);
      }
    }

    /**
     * Translate dispatcher info into the property expected node.
     */
    private void annotateDispatchers(Node n, Node parent) {
      Preconditions.checkState(n.getType() == Token.FUNCTION);
      if (parent.getJSDocInfo() != null
          && parent.getJSDocInfo().isJavaDispatch()) {
        if (parent.getType() == Token.ASSIGN) {
          Preconditions.checkState(parent.getLastChild() == n);
          n.putBooleanProp(Node.IS_DISPATCHER, true);
        }
      }
    }

    /**
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
    private void normalizeObjectLiteralKeyAnnotations(
        Node objlit, Node key, Node value) {
      Preconditions.checkState(objlit.getType() == Token.OBJECTLIT);
      if (key.getJSDocInfo() != null &&
          value.getType() == Token.FUNCTION) {
        value.setJSDocInfo(key.getJSDocInfo());
      }
    }

    /**
     * Annotate optional and var_arg function parameters.
     */
    private void annotateFunctions(Node n, Node parent) {
      JSDocInfo fnInfo = NodeUtil.getFunctionInfo(n);

      // Compute which function parameters are optional and
      // which are var_args.
      Node args = n.getFirstChild().getNext();
      for (Node arg = args.getFirstChild();
           arg != null;
           arg = arg.getNext()) {
        String argName = arg.getString();
        JSTypeExpression typeExpr = fnInfo == null ?
            null : fnInfo.getParameterType(argName);

        if (convention.isOptionalParameter(arg) ||
            typeExpr != null && typeExpr.isOptionalArg()) {
          arg.putBooleanProp(Node.IS_OPTIONAL_PARAM, true);
        }
        if (convention.isVarArgsParameter(arg) ||
            typeExpr != null && typeExpr.isVarArgs()) {
          arg.putBooleanProp(Node.IS_VAR_ARGS_PARAM, true);
        }
      }
    }
  }
}
