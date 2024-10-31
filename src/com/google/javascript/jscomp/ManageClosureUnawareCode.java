/*
 * Copyright 2024 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeNative;
import org.jspecify.annotations.Nullable;

/**
 * This set of passes work together to hide code that is "closure-unaware" from the rest of the
 * compiler passes (that might mis-optimize this code).
 *
 * <p>The passes in this class are used like so:
 *
 * <p>ManageClosureUnawareCode.wrap() should be run as soon as possible, before any other passes can
 * look at this code. It performs rigorous checking to validate that inputs tagged with
 * '@closureUnaware' are the correct shape, and will wrap the relevant code blocks such that their
 * content is now hidden from other passes.
 *
 * <p>ManageClosureUnawareCode.unwrap() should be run after all other passes have run, to unwrap the
 * code and re-expose it to the code-printing stage of the compiler.
 */
final class ManageClosureUnawareCode implements CompilerPass {

  public static final DiagnosticType UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE =
      DiagnosticType.error(
          "JSC_UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE",
          "This reference to $jscomp_wrap_closure_unaware_code is not expected.");

  public static final DiagnosticType UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE =
      DiagnosticType.error(
          "JSC_UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE",
          "This script does not conform to the expected shape of code annotated with"
              + " @closureUnaware.");

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private static final String JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN =
      "$jscomp_wrap_closure_unaware_code";

  private final boolean isUnwrapping;

  /** Whether the synthetic extern for JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN has been injected */
  private boolean preserveFunctionInjected = false;

  private ManageClosureUnawareCode(AbstractCompiler compiler, final boolean unwrapPhase) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.isUnwrapping = unwrapPhase;
  }

  static ManageClosureUnawareCode wrap(AbstractCompiler compiler) {
    return new ManageClosureUnawareCode(compiler, false);
  }

  static ManageClosureUnawareCode unwrap(AbstractCompiler compiler) {
    return new ManageClosureUnawareCode(compiler, true);
  }

  @Override
  public void process(Node externs, Node root) {
    if (isUnwrapping) {
      NodeTraversal.traverse(compiler, root, new UnwrapConcealedClosureUnawareCode());
      return;
    }
    // wrapping mode
    NodeTraversal.traverse(compiler, root, new ValidateAndWrapGlobalIifeCode());
  }

  /**
   * Validates the structure of SCRIPT nodes annotated at the file level with @closureUnaware, and
   * rewrites specific expected patterns to hide the closure-unaware code inside. This code is
   * expected to be inside a globally-scoped IIFE:
   *
   * <pre>{@code
   * (function() {
   *   // closure-unaware code here
   * }).call(globalThis);
   * }</pre>
   *
   * These scripts can optionally contain:
   *
   * <p>goog.module calls
   *
   * <p>goog.require calls (with or without a CONST LHS)
   *
   * <p>if statements with blocks containing single children that are the global IIFEs
   *
   * <p>doubly-nested if statements with single children, with the nested IF's blocks containing the
   * global IIFEs.
   *
   * <p>exports assignment statements
   */
  private final class ValidateAndWrapGlobalIifeCode implements NodeTraversal.Callback {

    private void reportUnexpectedCode(Node n) {
      compiler.report(JSError.make(n, UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE));
    }

    @Override
    public final boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      return parent == null || !parent.isScript(); // Don't traverse children of scripts
    }

    @Override
    public final void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      if (n == null || !n.isScript()) {
        return;
      }
      if (!n.isClosureUnawareCode()) {
        return;
      }

      // We want to look at:
      // top-level CALLs
      //   (SCRIPT -> (MODULE_BODY -> )? EXPR_RESULT -> CALL)
      // second-level CALLS inside IF
      //   (SCRIPT -> (MODULE_BODY -> )? EXPR_RESULT -> IF -> BLOCK -> EXPR_RESULT -> CALL)
      // calls inside nested IF
      //   (SCRIPT -> (MODULE_BODY -> )? EXPR_RESULT -> IF -> BLOCK -> IF -> BLOCK -> EXPR_RESULT ->
      // CALL)

      // We are going to manually find the relevant nodes here, and anything else we will report an
      // error for.
      Node script = n;

      Node exprParent = script.getFirstChild();
      if (exprParent == null) {
        reportUnexpectedCode(script);
        return;
      }
      if (!exprParent.isModuleBody()) {
        if (exprParent.isExprResult()) {
          if (NodeUtil.isExprCall(exprParent)
              && NodeUtil.isBundledGoogModuleCall(exprParent.getFirstChild())) {
            // EXPR_RESULT -> CALL -> FUNCTION -> BLOCK -> EXPR_RESULT(s)
            // We want the BLOCK node, as we will iterate over it to get EXPR_RESULTs
            exprParent = exprParent.getFirstChild().getSecondChild().getChildAtIndex(2);
          } else {
            // Sometimes, there is no MODULE_BODY node in the AST, just SCRIPT -> EXPR_RESULT(s).
            // Assume that if we didn't find the MODULE_BODY as the first child of the SCRIPT that
            // all the EXPR_RESULT are direct children of the SCRIPT.
            exprParent = script;
          }
        } else {
          reportUnexpectedCode(script);
          return;
        }
      }

      // MODULE_BODY has a list of statement kinds. We want to validate that they are all "known"
      for (Node child = exprParent.getFirstChild(); child != null; child = child.getNext()) {
        // Allowed statement types:
        if (child.isExprResult()
            && child.getFirstChild().isString()
            && child.getFirstChild().getString().equals("use strict")) {
          // "use strict" pragmas, likely added by the whitespace-wrapping of Closure modules
          continue;
        }

        // -- calls to goog.module
        if (NodeUtil.isGoogModuleCall(child)) {
          continue;
        }

        // -- direct calls to goog.require (no assignments)
        if (NodeUtil.isExprCall(child) && NodeUtil.isGoogRequireCall(child.getFirstChild())) {
          continue;
        }

        if (child.isConst()) {
          if (child.getFirstChild().isDestructuringLhs()) {
            Node rhs = child.getFirstChild().getSecondChild();
            if (rhs != null && NodeUtil.isGoogRequireCall(rhs)) {
              continue;
            }
            reportUnexpectedCode(child);
            continue;
          }
          // get RHS of this const declaration and see if it is a goog.require
          Node rhs = child.getSecondChild();
          if (rhs != null && NodeUtil.isGoogRequireCall(rhs)) {
            continue;
          }
          reportUnexpectedCode(child);
          continue;
        }

        if (NodeUtil.isExprCall(child)) {
          maybeRewriteCall(t, child.getFirstChild());
          continue;
        }

        if (child.isIf()) {
          visitIf(t, child, 0);
          continue;
        }

        // exports =
        if (child.isExprResult() && child.getFirstChild().isAssign()) {
          Node lhs = child.getFirstFirstChild();
          if (lhs != null && lhs.matchesName("exports")) {
            continue;
          }
          reportUnexpectedCode(child);
          continue;
        }

        if (child.isReturn() && child.getFirstChild().matchesName("exports")) {
          // return exports;
          // Likely added by whitespace-wrapping of Closure modules
          continue;
        }

        // Fallthrough: we don't know why this statement is here?
        reportUnexpectedCode(child);
      }
    }

    private void visitIfBlockStmt(NodeTraversal t, Node n, int depth) {
      Node child = n.getFirstChild();
      if (NodeUtil.isExprCall(child)) {
        maybeRewriteCall(t, child.getFirstChild());
        return;
      }

      // Could be a nested if
      if (child.isIf()) {
        if (depth > 1) {
          // This nested if statement is too deep - we only expect at most two layers of nested IF
          reportUnexpectedCode(child);
          return;
        } else {
          visitIf(t, child, depth + 1);
          return;
        }
      }
      // We don't know what this is.
      reportUnexpectedCode(child);
    }

    private void visitIf(NodeTraversal t, Node n, int depth) {
      Node trueBlock = n.getSecondChild();
      @Nullable Node falseBlock = null;
      if (n.getChildCount() > 2) {
        falseBlock = n.getChildAtIndex(2);
      }
      boolean hasProblems = false;
      if (trueBlock.getChildCount() > 1) {
        reportUnexpectedCode(trueBlock);
        hasProblems = true;
      }
      if (falseBlock != null && falseBlock.getChildCount() > 1) {
        reportUnexpectedCode(falseBlock);
        hasProblems = true;
      }
      if (hasProblems) {
        return;
      }

      // This IF has a true (and possibly a false) block, both with one child.
      visitIfBlockStmt(t, trueBlock, depth);
      if (falseBlock != null) {
        visitIfBlockStmt(t, falseBlock, depth);
      }
    }

    private void maybeRewriteCall(NodeTraversal t, Node n) {
      if (!n.isCall() || n.getChildCount() != 2) {
        reportUnexpectedCode(n);
        return;
      }

      Node prop = n.getFirstChild();
      // <>.call()
      if (!prop.isGetProp() || !prop.getString().equals("call") || prop.getChildCount() != 1) {
        reportUnexpectedCode(prop);
        return;
      }
      Node globalThisArg = n.getSecondChild();
      if (globalThisArg == null || !globalThisArg.getString().equals("globalThis")) {
        reportUnexpectedCode(globalThisArg);
        return;
      }

      Node evalFn = prop.getFirstChild();
      if (!evalFn.isFunction() || evalFn.getSecondChild().hasChildren()) {
        // not a function, or a function with parameters
        reportUnexpectedCode(evalFn);
        return;
      }

      Node closureUnawareCodeBlock = evalFn.getChildAtIndex(2);
      if (!closureUnawareCodeBlock.isBlock()) {
        reportUnexpectedCode(closureUnawareCodeBlock);
        return;
      }

      wrapClosureUnawareCode(t, n);
    }
  }

  private void wrapClosureUnawareCode(NodeTraversal t, Node iifeNode) {
    // These code blocks should be in the form of:
    // CALL
    //   GETPROP
    //     FUNCTION
    //       NAME
    //       PARAM_LIST
    //       BLOCK
    //         ... closure unaware code here ...
    //   NAME globalThis

    if (!preserveFunctionInjected) {
      NodeUtil.createSynthesizedExternsSymbol(compiler, JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN);
      preserveFunctionInjected = true;
    }

    Node codeBlock = iifeNode.getFirstFirstChild().getChildAtIndex(2);
    String stringifiedCode = compiler.toSource(codeBlock);

    Node wrappedReplacement =
        astFactory
            .createCall(
                astFactory.createNameWithUnknownType(JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN),
                AstFactory.type(JSTypeNative.UNKNOWN_TYPE, StandardColors.UNKNOWN),
                astFactory.createString(stringifiedCode))
            .srcrefTree(iifeNode);

    wrappedReplacement.getFirstChild().putBooleanProp(Node.IS_CONSTANT_NAME, true);

    iifeNode.replaceWith(wrappedReplacement);
    NodeUtil.markFunctionsDeleted(iifeNode.getFirstFirstChild(), compiler);
    t.reportCodeChange();
  }

  private final class UnwrapConcealedClosureUnawareCode implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      if (parent == null || !parent.isScript()) {
        return true; // keep going
      }

      if (parent.isScript() && !parent.isClosureUnawareCode()) {
        return false;
      }

      // Once inside a closureUnaware script, we want to traverse the entire thing to make sure we
      // find all the calls to $jscomp_wrap_closure_unaware_code.
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      if (!n.isCall()) {
        // We only expect free calls to the preserve function - anything else is probably a bug.
        if (n.matchesName(JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN) && !parent.isCall()) {
          compiler.report(JSError.make(n, UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE));
        }
        return;
      }

      Node call = n;
      Node callee = call.getFirstChild();
      // <>.call()
      if (!callee.matchesName(JSCOMP_CLOSURE_UNAWARE_CODE_PRESERVE_FN)) {
        return;
      }

      if (call.getChildCount() != 2) {
        compiler.report(JSError.make(call, UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE));
        return; // callee and a single arg
      }

      Node stringifiedSource = call.getSecondChild();
      if (stringifiedSource == null || !stringifiedSource.isString()) {
        compiler.report(JSError.make(call, UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE));
        return;
      }
      String wrappedSrc = stringifiedSource.getString();

      // We add the @closureUnaware annotation to the wrapped block so that the parsed SCRIPT node
      // will have JSDoc with isClosureUnawareCode returning true.
      // We need to do this because this block of code is parsed in isolation and there is no other
      // signal present to prevent emitting invalid JSDoc parse errors when the wrapped code
      // contains
      // "invalid" (non-closure?) JSDoc.
      // This annotation is not attached to the CALL node which we will attach to the AST later, and
      // that is intentional.
      String unwrappedCode =
          "/**\n"
              + " * @fileoverview\n"
              + " * @closureUnaware\n"
              + " */\n"
              + "(/** @closureUnaware */ function() "
              + wrappedSrc
              + ").call(globalThis);";

      try {
        Node parsedCodeRoot =
            compiler.parseSyntheticCode(
                // The wrappedSrc is the entire BLOCK node from the previous function.
                call.getSourceFileName(), unwrappedCode);
        Node parsedCode = parsedCodeRoot.getOnlyChild().getOnlyChild();
        parsedCode.srcrefTree(call); // The original src info should have been stashed here
        parsedCode.detach();

        call.replaceWith(parsedCode);
        NodeUtil.markNewScopesChanged(parsedCode.getFirstFirstChild(), compiler);
        t.reportCodeChange();
      } catch (RuntimeException e) {
        System.err.println(e);
        throw e;
      }
    }
  }
}
