/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Checks for non side effecting statements such as
 * <pre>
 * var s = "this string is "
 *         "continued on the next line but you forgot the +";
 * x == foo();  // should that be '='?
 * foo();;  // probably just a stray-semicolon. Doesn't hurt to check though
 * </p>
 * and generates warnings.
 *
 */
final class CheckSideEffects extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType USELESS_CODE_ERROR = DiagnosticType.warning(
      "JSC_USELESS_CODE",
      "Suspicious code. {0}");

  static final String PROTECTOR_FN = "JSCOMPILER_PRESERVE";

  private final CheckLevel level;

  private final List<Node> problemNodes = Lists.newArrayList();

  private final AbstractCompiler compiler;

  private final boolean protectSideEffectFreeCode;

  CheckSideEffects(AbstractCompiler compiler, CheckLevel level,
      boolean protectSideEffectFreeCode) {
    this.compiler = compiler;
    this.level = level;
    this.protectSideEffectFreeCode = protectSideEffectFreeCode;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    // Code with hidden side-effect code is common, for example
    // accessing "el.offsetWidth" forces a reflow in browsers, to allow this
    // will still allowing local dead code removal in general,
    // protect the "side-effect free" code in the source.
    //
    if (protectSideEffectFreeCode) {
      protectSideEffects();
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // VOID nodes appear when there are extra semicolons at the BLOCK level.
    // I've been unable to think of any cases where this indicates a bug,
    // and apparently some people like keeping these semicolons around,
    // so we'll allow it.
    if (n.isEmpty() ||
        n.isComma()) {
      return;
    }

    if (parent == null) {
      return;
    }

    int pt = parent.getType();
    if (pt == Token.COMMA) {
      Node gramps = parent.getParent();
      if (gramps.isCall() &&
          parent == gramps.getFirstChild()) {
        // Semantically, a direct call to eval is different from an indirect
        // call to an eval. See Ecma-262 S15.1.2.1. So it's ok for the first
        // expression to a comma to be a no-op if it's used to indirect
        // an eval.
        if (n == parent.getFirstChild() &&
            parent.getChildCount() == 2 &&
            n.getNext().isName() &&
            "eval".equals(n.getNext().getString())) {
          return;
        }
      }

      if (n == parent.getLastChild()) {
        for (Node an : parent.getAncestors()) {
          int ancestorType = an.getType();
          if (ancestorType == Token.COMMA)
            continue;
          if (ancestorType != Token.EXPR_RESULT &&
              ancestorType != Token.BLOCK)
            return;
          else
            break;
        }
      }
    } else if (pt != Token.EXPR_RESULT && pt != Token.BLOCK) {
      if (pt == Token.FOR && parent.getChildCount() == 4 &&
          (n == parent.getFirstChild() ||
           n == parent.getFirstChild().getNext().getNext())) {
        // Fall through and look for warnings for the 1st and 3rd child
        // of a for.
      } else {
        return;  // it might be ok to not have a side-effect
      }
    }

    boolean isSimpleOp = NodeUtil.isSimpleOperatorType(n.getType());
    if (isSimpleOp ||
        !NodeUtil.mayHaveSideEffects(n, t.getCompiler())) {
      if (n.isQualifiedName() && n.getJSDocInfo() != null) {
        // This no-op statement was there so that JSDoc information could
        // be attached to the name. This check should not complain about it.
        return;
      } else if (n.isExprResult()) {
        // we already reported the problem when we visited the child.
        return;
      }

      String msg = "This code lacks side-effects. Is there a bug?";
      if (n.isString()) {
        msg = "Is there a missing '+' on the previous line?";
      } else if (isSimpleOp) {
        msg = "The result of the '" + Token.name(n.getType()).toLowerCase() +
            "' operator is not being used.";
      }

      t.getCompiler().report(
          t.makeError(n, level, USELESS_CODE_ERROR, msg));
      // TODO(johnlenz): determine if it is necessary to
      // try to protect side-effect free statements as well.
      if (!NodeUtil.isStatement(n)) {
        problemNodes.add(n);
      }
    }
  }

  /**
   * Protect side-effect free nodes by making them parameters
   * to a extern function call.  This call will be removed
   * after all the optimizations passes have run.
   */
  private void protectSideEffects() {
    if (!problemNodes.isEmpty()) {
      addExtern();
      for (Node n : problemNodes) {
        Node name = IR.name(PROTECTOR_FN).srcref(n);
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        Node replacement = IR.call(name).srcref(n);
        replacement.putBooleanProp(Node.FREE_CALL, true);
        n.getParent().replaceChild(n, replacement);
        replacement.addChildToBack(n);
      }
      compiler.reportCodeChange();
    }
  }

  private void addExtern() {
    Node name = IR.name(PROTECTOR_FN);
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    Node var = IR.var(name);
    // Add "@noalias" so we can strip the method when AliasExternals is enabled.
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordNoAlias();
    var.setJSDocInfo(builder.build(var));
    CompilerInput input = compiler.getSynthesizedExternsInput();
    input.getAstRoot(compiler).addChildrenToBack(var);
    compiler.reportCodeChange();
  }

  /**
   * Remove side-effect sync functions.
   */
  static class StripProtection extends AbstractPostOrderCallback implements CompilerPass {

    private final AbstractCompiler compiler;

    StripProtection(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node target = n.getFirstChild();
        // TODO(johnlenz): add this to the coding convention
        // so we can remove goog.reflect.sinkValue as well.
        if (target.isName() && target.getString().equals(PROTECTOR_FN)) {
          Node expr = n.getLastChild();
          n.detachChildren();
          parent.replaceChild(n, expr);
        }
      }
    }
  }
}
