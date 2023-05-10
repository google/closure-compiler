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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

/**
 * A compiler pass that verifies the structure of the AST conforms to a number of invariants.
 * Because this can add a lot of overhead, we only run this in development mode.
 */
class ValidityCheck implements CompilerPass {

  static final DiagnosticType CANNOT_PARSE_GENERATED_CODE =
      DiagnosticType.error(
          "JSC_CANNOT_PARSE_GENERATED_CODE",
          "Internal compiler error. Cannot parse generated code: {0}");

  static final DiagnosticType GENERATED_BAD_CODE =
      DiagnosticType.error(
          "JSC_GENERATED_BAD_CODE",
          "Internal compiler error. Generated bad code."
              + "----------------------------------------\n"
              + "Expected:\n{0}\n"
              + "----------------------------------------\n"
              + "Actual:\n{1}");

  static final DiagnosticType EXTERN_PROPERTIES_CHANGED =
      DiagnosticType.error(
          "JSC_EXTERN_PROPERTIES_CHANGED",
          "Internal compiler error. Extern properties modified from:\n{0}\nto:\n{1}");

  private final AbstractCompiler compiler;
  private final AstValidator astValidator;

  ValidityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astValidator = new AstValidator(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    checkAst(externs, root);
    checkNormalization(externs, root);
    checkVars(externs, root);
    checkExternProperties(externs);
  }

  /** Check that the AST is structurally accurate. */
  private void checkAst(Node externs, Node root) {
    astValidator.validateCodeRoot(externs);
    astValidator.validateCodeRoot(root);
  }

  private void checkVars(Node externs, Node root) {
    if (compiler.getLifeCycleStage().isNormalized()) {
      (new VarCheck(compiler, true)).process(externs, root);
    }
  }

  /** Verifies that the normalization pass does nothing on an already-normalized tree. */
  private void checkNormalization(Node externs, Node root) {
    // Verify nothing has inappropriately denormalize the AST.
    CodeChangeHandler handler = new ForbiddenChange();
    compiler.addChangeHandler(handler);

    // TODO(johnlenz): Change these normalization checks Preconditions and
    // Exceptions into Errors so that it is easier to find the root cause
    // when there are cascading issues.
    if (compiler.getLifeCycleStage().isNormalized()) {
      Normalize.builder(compiler).assertOnChange(true).build().process(externs, root);

      if (compiler.getLifeCycleStage().isNormalizedUnobfuscated()) {
        boolean checkUserDeclarations = true;
        CompilerPass pass = new Normalize.VerifyConstants(compiler, checkUserDeclarations);
        pass.process(externs, root);
      }
    }

    compiler.removeChangeHandler(handler);
  }

  private void checkExternProperties(Node externs) {
    ImmutableSet<String> externProperties = compiler.getExternProperties();
    if (externProperties == null) {
      // GatherExternProperties hasn't run yet. Don't report a violation.
      return;
    }
    new GatherExternProperties(compiler, GatherExternProperties.Mode.CHECK).process(externs, null);
    if (!compiler.getExternProperties().equals(externProperties)) {
      compiler.report(
          JSError.make(
              EXTERN_PROPERTIES_CHANGED,
              externProperties.toString(),
              compiler.getExternProperties().toString()));
      // Throw an exception, so that the infrastructure will tell us which pass violated the check.
      throw new IllegalStateException(
          "Validity Check failed: Extern properties changed from:\n"
              + externProperties
              + "\nto:\n"
              + compiler.getExternProperties());
    }
  }
}
