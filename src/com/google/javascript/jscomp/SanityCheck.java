/*
 * Copyright 2009 The Closure Compiler Authors.
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

import java.util.Set;

/**
 * A compiler pass that verifies the structure of the AST conforms
 * to a number of invariants. Because this can add a lot of overhead,
 * we only run this in development mode.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class SanityCheck implements CompilerPass {

  static final DiagnosticType CANNOT_PARSE_GENERATED_CODE =
      DiagnosticType.error("JSC_CANNOT_PARSE_GENERATED_CODE",
          "Internal compiler error. Cannot parse generated code: {0}");

  static final DiagnosticType GENERATED_BAD_CODE = DiagnosticType.error(
      "JSC_GENERATED_BAD_CODE",
      "Internal compiler error. Generated bad code." +
      "----------------------------------------\n" +
      "Expected:\n{0}\n" +
      "----------------------------------------\n" +
      "Actual:\n{1}");

  static final DiagnosticType EXTERN_PROPERTIES_CHANGED =
      DiagnosticType.error("JSC_EXTERN_PROPERTIES_CHANGED",
          "Internal compiler error. Extern properties modified.");

  private final AbstractCompiler compiler;
  private final AstValidator astValidator;

  SanityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astValidator = new AstValidator(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    sanityCheckAst(externs, root);
    sanityCheckNormalization(externs, root);
    sanityCheckCodeGeneration(root);
    sanityCheckVars(externs, root);
    sanityCheckExternProperties(externs);
  }

  /**
   * Sanity check the AST is structurally accurate.
   */
  private void sanityCheckAst(Node externs, Node root) {
    astValidator.validateCodeRoot(externs);
    astValidator.validateCodeRoot(root);
  }

  private void sanityCheckVars(Node externs, Node root) {
    if (compiler.getLifeCycleStage().isNormalized()) {
      (new VarCheck(compiler, true)).process(externs, root);
    }
  }

  /**
   * Sanity checks code generation by performing it once, parsing the result,
   * then generating code from the second parse tree to verify that it matches
   * the code generated from the first parse tree.
   *
   * @return The regenerated parse tree. Null on error.
   */
  private Node sanityCheckCodeGeneration(Node root) {
    if (compiler.hasHaltingErrors()) {
      // Don't even bother checking code generation if we already know the
      // the code is bad.
      return null;
    }

    String source = compiler.toSource(root);
    Node root2 = compiler.parseSyntheticCode(source);
    if (compiler.hasHaltingErrors()) {
      compiler.report(JSError.make(CANNOT_PARSE_GENERATED_CODE,
              Strings.truncateAtMaxLength(source, 100, true)));

      // Throw an exception, so that the infrastructure will tell us
      // which pass violated the sanity check.
      throw new IllegalStateException("Sanity Check failed");
    }

    String source2 = compiler.toSource(root2);
    if (!source.equals(source2)) {
      compiler.report(JSError.make(GENERATED_BAD_CODE, source, source2));

      // Throw an exception, so that the infrastructure will tell us
      // which pass violated the sanity check.
      throw new IllegalStateException("Sanity Check failed");
    }

    return root2;
  }

  /**
   * Sanity checks the AST. This is by verifying the normalization passes do
   * nothing.
   */
  private void sanityCheckNormalization(Node externs, Node root) {
    // Verify nothing has inappropriately denormalize the AST.
    CodeChangeHandler handler = new ForbiddenChange();
    compiler.addChangeHandler(handler);

    // TODO(johnlenz): Change these normalization checks Preconditions and
    // Exceptions into Errors so that it is easier to find the root cause
    // when there are cascading issues.
    new PrepareAst(compiler, true).process(null, root);
    if (compiler.getLifeCycleStage().isNormalized()) {
      (new Normalize(compiler, true)).process(externs, root);

      if (compiler.getLifeCycleStage().isNormalizedUnobfuscated()) {
        boolean checkUserDeclarations = true;
        CompilerPass pass = new Normalize.VerifyConstants(
            compiler, checkUserDeclarations);
        pass.process(externs, root);
      }
    }

    compiler.removeChangeHandler(handler);
  }

  private void sanityCheckExternProperties(Node externs) {
    Set<String> externProperties = compiler.getExternProperties();
    if (externProperties == null) {
      // GatherExternProperties hasn't run yet. Don't report a violation.
      return;
    }
    (new GatherExternProperties(compiler)).process(externs, null);
    if (!compiler.getExternProperties().equals(externProperties)) {
      compiler.report(JSError.make(EXTERN_PROPERTIES_CHANGED));
      // Throw an exception, so that the infrastructure will tell us
      // which pass violated the sanity check.
      throw new IllegalStateException("Sanity Check failed");
    }
  }
}
