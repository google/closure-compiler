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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
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
 *
 * <p>Valid "closure-unaware" scripts should:
 * <li>be explicitly annotated with a @fileoverview JSDoc comment that also has the @closureUnaware
 *     JSDoc tag. This is done so that checking whether an arbitrary node is closure-unaware is a
 *     very quick operation (as the JSDoc from the SCRIPT node determines whether a bit is set on
 *     the relevant SourceFile object for those AST nodes), and this higher-level check is used in
 *     several places in the compiler to avoid reporting various errors.
 * <li>annotate each expression within the script that is really "closure-unaware" with a JSDoc
 *     comment with the @closureUnaware JSDoc tag. Currently, these comments must be attached to
 *     FUNCTION nodes, and the AST inside the BLOCK child node is considered the closure-unaware
 *     code. This allows the compiler to differentiate between the parts of the AST containing code
 *     that has to be "closure-aware" (such as closure module system constructs, assertions that
 *     should optimize away, etc) from the code that is actually closure-unaware.
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
  private static final String JSCOMP_CLOSURE_UNAWARE_CODE_SHADOW_HOST_NAME =
      "$jscomp_wrap_closure_unaware_code";

  private final boolean isUnwrapping;

  /**
   * Whether the synthetic extern for JSCOMP_CLOSURE_UNAWARE_CODE_SHADOW_HOST_NAME has been injected
   */
  private boolean shadowHostNameExternInjected = false;

  private ManageClosureUnawareCode(AbstractCompiler compiler, final boolean unwrapPhase) {
    this.compiler = compiler;
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
    new ValidateAndWrapClosureUnawareCode(compiler).process(externs, root);
  }

  private final class ValidateAndWrapClosureUnawareCode implements CompilerPass {

    private final AbstractCompiler compiler;

    private ValidateAndWrapClosureUnawareCode(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {

      for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
        if (script.isClosureUnawareCode()) {

          ValidateAndWrapClosureUnawareScript validateAndWrapClosureUnawareScript =
              new ValidateAndWrapClosureUnawareScript(compiler);
          NodeTraversal.traverse(compiler, script, validateAndWrapClosureUnawareScript);
          // If the file was marked as @closureUnaware, but we didn't actually wrap anything, that
          // means that the file was not in the expected shape.
          boolean hasWrappedSomething =
              validateAndWrapClosureUnawareScript.hasSeenClosureUnawareAnnotation();
          if (!hasWrappedSomething) {
            reportUnexpectedClosureUnawareCode(compiler, script);
          }
        } else {
          // This would occur if the annotation was present in a non-fileoverview comment and the
          // fileoverview did not contain this annotation.
          NodeUtil.visitPreOrder(
              script,
              new NodeUtil.Visitor() {
                @Override
                public void visit(Node n) {
                  JSDocInfo jsDocInfo = n.getJSDocInfo();
                  if (jsDocInfo != null && jsDocInfo.isClosureUnawareCode()) {
                    reportUnexpectedClosureUnawareCode(compiler, n);
                  }
                }
              });
        }
      }
    }
  }

  private static final void reportUnexpectedClosureUnawareCode(AbstractCompiler compiler, Node n) {
    compiler.report(JSError.make(n, UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE));
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
  private final class ValidateAndWrapClosureUnawareScript extends AbstractPostOrderCallback {

    private final AbstractCompiler compiler;
    private final AstFactory astFactory;

    ValidateAndWrapClosureUnawareScript(AbstractCompiler compiler) {
      this.compiler = compiler;
      this.astFactory = compiler.createAstFactory();
    }

    private boolean seenClosureUnawareAnnotation = false;

    @Override
    public final void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      checkArgument(
          !n.isRoot(),
          "ValidateAndWrapClosureUnawareScript should be run directly on each SCRIPT node"
              + " individually, and should not be re-used across SCRIPT nodes.");

      JSDocInfo jsDocInfo = n.getJSDocInfo();
      if (jsDocInfo == null || !jsDocInfo.isClosureUnawareCode()) {
        return;
      }

      if (n.isScript()) {
        // This is a file-overview comment, which should not trigger shadowing.
        return;
      }

      seenClosureUnawareAnnotation = true;

      if (!isValidClosureUnawareAnnotatedNode(n)) {
        reportUnexpectedClosureUnawareCode(compiler, n);
        return;
      }

      hideClosureUnawareCodeRoot(t, n);
    }

    private final boolean hasSeenClosureUnawareAnnotation() {
      return seenClosureUnawareAnnotation;
    }

    private final boolean isValidClosureUnawareAnnotatedNode(Node n) {
      // We only intend to support hiding FUNCTION nodes that are children under a CALL node
      // as this generally matches the mental model of what we are rewriting it with.
      if (!n.isFunction()) {
        return false;
      }

      Node parent = n.getParent();
      if (parent == null) {
        return false;
      }
      // We only support:

      //  - FUNCTION nodes that are direct CALL callees
      if (parent.isCall()) {
        return true;
      }
      //  - FUNCTION nodes that are children of a GETPROP node that is a direct CALL callee (e.g.
      //    fn.call or fn.apply can be the CALL callee)
      Node grandparent = parent.getParent();
      if (grandparent == null) {
        return false;
      }
      if (parent.isGetProp() && grandparent.isCall()) {
        String calledFn = parent.getString();
        return calledFn.equals("call") || calledFn.equals("apply");
      }

      return false;
    }

    private final void hideClosureUnawareCodeRoot(NodeTraversal t, Node n) {
      if (!shadowHostNameExternInjected) {
        NodeUtil.createSynthesizedExternsSymbol(
            compiler, JSCOMP_CLOSURE_UNAWARE_CODE_SHADOW_HOST_NAME);
        shadowHostNameExternInjected = true;
      }

      Node shadowNameNode =
          astFactory.createNameWithUnknownType(JSCOMP_CLOSURE_UNAWARE_CODE_SHADOW_HOST_NAME);
      shadowNameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      n.replaceWith(shadowNameNode);

      Node shadowJsRoot = IR.root(IR.script(astFactory.exprResult(n)));

      shadowNameNode.setClosureUnawareShadow(shadowJsRoot);
      t.reportCodeChange(shadowNameNode);
    }
  }

  // TODO jameswr: Given that the CodePrinter supports printing out the shadow instead of the shadow
  // host node, do we even need to revert the AST back to the original form at the end of
  // compilation?
  private static final class UnwrapConcealedClosureUnawareCode implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      if (parent == null || !parent.isScript()) {
        return true; // keep going
      }

      if (parent.isScript() && !parent.isClosureUnawareCode()) {
        return false;
      }

      // Once inside a closureUnaware script, we want to traverse the entire thing to make sure we
      // find all the nodes marked as closure-unaware shadows.
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, @Nullable Node parent) {
      tryUnwrapClosureUnawareShadowedCode(t, n, parent);
    }

    private void tryUnwrapClosureUnawareShadowedCode(
        NodeTraversal t, Node n, @Nullable Node parent) {
      Node shadowAstRoot = n.getClosureUnawareShadow();
      if (shadowAstRoot == null) {
        return;
      }

      // ROOT -> SCRIPT -> EXPR_RESULT -> FUNCTION
      Node originalCodeFunction = shadowAstRoot.getFirstFirstChild().getFirstChild();
      originalCodeFunction.detach();
      n.replaceWith(originalCodeFunction);
      t.reportCodeChange(originalCodeFunction);
      // Note: you might be tempted to mark this new FUNCTION as  "newly created scope".
      // This is probably wrong - the scope never actually disappeared from the perspective of the
      // compiler, and ChangeVerifier will complain (correctly) if that was done.

      // With the shadowed code detached, we are just iterating over all the synthetic nodes created
      // above and marking them as deleted to avoid confusing various compiler verification checks.
      NodeUtil.visitPreOrder(
          shadowAstRoot,
          new NodeUtil.Visitor() {
            @Override
            public void visit(Node n) {
              n.setDeleted(true);
            }
          });
      // Also mark the NAME node that was hosting the shadow as deleted, in case anything is looking
      // for it.
      n.setDeleted(true);

      // This ROOT -> SCRIPT -> EXPR_RESULT node structure that was enclosed in the shadow is now
      // dangling and we need to inform the compiler that we've removed it.
      t.reportCodeChange(shadowAstRoot.getFirstChild());
    }
  }
}
