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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Ascii;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks for non side effecting statements such as
 *
 * <pre>
 * var s = "this string is "
 *         "continued on the next line but you forgot the +";
 * x == foo();  // should that be '='?
 * foo();;  // probably just a stray-semicolon. Doesn't hurt to check though
 * </pre>
 *
 * and generates warnings.
 */
final class CheckSideEffects extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType USELESS_CODE_ERROR = DiagnosticType.warning(
      "JSC_USELESS_CODE",
      "Suspicious code. {0}");

  // Protect function is used to protect the underlying node from removal. At the very end, this
  // protector function will be completely removed from production but the underlying node will be
  // preserved.
  static final String PROTECTOR_FN = "JSCOMPILER_PRESERVE";

  // J2CL protector function is used to protect the underlying node from removal until the very end
  // so that referred prototype properties won't be removed by the compiler. At the very end, this
  // protector function and its underyling node will be completely removed from production.
  static final String J2CL_PROTECTOR_FN = "$J2CL_PRESERVE$";

  private final boolean report;

  private final List<Node> problemNodes = new ArrayList<>();

  private final Set<String> noSideEffectExterns = new HashSet<>();

  private final AbstractCompiler compiler;

  private final boolean protectSideEffectFreeCode;

  /** Whether the synthetic extern for JSCOMPILER_PRESERVE has been injected */
  private boolean preserveFunctionInjected = false;

  CheckSideEffects(AbstractCompiler compiler, boolean report,
      boolean protectSideEffectFreeCode) {
    this.compiler = compiler;
    this.report = report;
    this.protectSideEffectFreeCode = protectSideEffectFreeCode;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new GetNoSideEffectExterns());

    NodeTraversal.traverse(compiler, root, this);

    // Code with hidden side-effect code is common, for example
    // accessing "el.offsetWidth" forces a reflow in browsers, to allow this
    // will still allowing local dead code removal in general,
    // protect the "side-effect free" code in the source.
    //
    // This also includes function calls such as with document.createElement
    if (protectSideEffectFreeCode) {
      protectSideEffects();
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // VOID nodes appear when there are extra semicolons at the BLOCK level.
    // I've been unable to think of any cases where this indicates a bug,
    // and apparently some people like keeping these semicolons around,
    // so we'll allow it.
    if (n.isEmpty() || n.isComma()) {
      return;
    }

    if (parent == null) {
      return;
    }

    // Do not try to remove a block or an expr result. We already handle
    // these cases when we visit the child, and the peephole passes will
    // fix up the tree in more clever ways when these are removed.
    if (n.isExprResult() || n.isBlock()) {
      return;
    }

    // This no-op statement was there so that JSDoc information could
    // be attached to the name. This check should not complain about it.
    if (n.isQualifiedName() && n.getJSDocInfo() != null) {
      return;
    }

    boolean isResultUsed = NodeUtil.isExpressionResultUsed(n);
    boolean isSimpleOp = NodeUtil.isSimpleOperator(n);
    if (!isResultUsed) {
      if (isSimpleOp || !t.getCompiler().getAstAnalyzer().mayHaveSideEffects(n)) {
        if (report) {
          String msg = "This code lacks side-effects. Is there a bug?";
          if (n.isStringLit() || n.isTemplateLit()) {
            msg = "Is there a missing '+' on the previous line?";
          } else if (isSimpleOp) {
            msg =
                "The result of the '"
                    + Ascii.toLowerCase(n.getToken().toString())
                    + "' operator is not being used.";
          }

          t.report(n, USELESS_CODE_ERROR, msg);
        }
        // TODO(johnlenz): determine if it is necessary to
        // try to protect side-effect free statements as well.
        if (!NodeUtil.isStatement(n)) {
          problemNodes.add(n);
        }
      } else if (n.isCall()
          && (n.getFirstChild().isGetProp()
              || n.getFirstChild().isName()
              || n.getFirstChild().isStringLit())) {
        String qname = n.getFirstChild().getQualifiedName();

        // The name should not be defined in src scopes - only externs
        boolean isDefinedInSrc = false;
        if (qname != null) {
          if (n.getFirstChild().isGetProp()) {
            Node rootNameNode =
                NodeUtil.getRootOfQualifiedName(n.getFirstChild());
            isDefinedInSrc = rootNameNode != null && rootNameNode.isName()
                && t.getScope().getVar(rootNameNode.getString()) != null;
          } else {
            isDefinedInSrc = t.getScope().getVar(qname) != null;
          }
        }

        if (qname != null && noSideEffectExterns.contains(qname) && !isDefinedInSrc) {
          problemNodes.add(n);
          if (report) {
            String msg = "The result of the extern function call '" + qname
                + "' is not being used.";
            t.report(n, USELESS_CODE_ERROR, msg);
          }
        }
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
      if (!preserveFunctionInjected) {
        addExtern(compiler);
      }
      for (Node n : problemNodes) {
        Node name = IR.name(PROTECTOR_FN).srcref(n);
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        Node replacement = IR.call(name).srcref(n);
        replacement.putBooleanProp(Node.FREE_CALL, true);
        n.replaceWith(replacement);
        replacement.addChildToBack(n);
        compiler.reportChangeToEnclosingScope(replacement);
      }
    }
  }

  /** Injects JSCOMPILER_PRESEVE into the synthetic externs */
  static void addExtern(AbstractCompiler compiler) {
    NodeUtil.createSynthesizedExternsSymbol(compiler, PROTECTOR_FN);
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
        if (!target.isName()) {
          return;
        }

        if (target.getString().equals(J2CL_PROTECTOR_FN)) {
          // Do not let non-J2CL code abuse it.
          checkState(n.getSourceFileName().endsWith(".java.js"), "Only allowed for J2CL code");
          NodeUtil.deleteFunctionCall(n, compiler);
          return;
        }

        // TODO(johnlenz): add this to the coding convention
        // so we can remove goog.reflect.sinkValue as well.
        if (target.getString().equals(PROTECTOR_FN)) {
          Node expr = n.getLastChild();
          n.detachChildren();
          n.replaceWith(expr);
          t.reportCodeChange();
        }
      }
    }
  }

  /**
   * Get fully qualified function names which are marked
   * with @nosideeffects
   *
   * TODO(ChadKillingsworth) Add support for object literals
   */
  private class GetNoSideEffectExterns extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
        if (jsDoc != null && jsDoc.isNoSideEffects()) {
          String name = NodeUtil.getName(n);
          noSideEffectExterns.add(name);
        }
      }

      if (n.isName() && PROTECTOR_FN.equals(n.getString())) {
        preserveFunctionInjected = true;
      }
    }
  }
}
