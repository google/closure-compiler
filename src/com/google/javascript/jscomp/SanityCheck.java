/*
 * Copyright 2009 Google Inc.
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

/**
 * A compiler pass that verifies the structure of the AST conforms
 * to a number of invariants. Because this can add a lot of overhead,
 * we only run this in development mode.
 *
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
      "Actual\n{1}");

  private final AbstractCompiler compiler;

  SanityCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void process(Node externs, Node root) {
    sanityCheckNormalization(externs, root);
    sanityCheckCodeGeneration(root);
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
      return null;
    }

    String source2 = compiler.toSource(root2);
    if (!source.equals(source2)) {
      compiler.report(JSError.make(GENERATED_BAD_CODE,
              Strings.truncateAtMaxLength(source, 1000, true),
              Strings.truncateAtMaxLength(source2, 1000, true)));
    }

    return root2;
  }

  /**
   * Sanity checks the AST. This is by verifing the normalization passes do
   * nothing.
   */
  private void sanityCheckNormalization(Node externs, Node root) {
    // Verify nothing has inappropriately denormalize the AST.
    CodeChangeHandler.RecentChange handler =
        new CodeChangeHandler.RecentChange();
    compiler.addChangeHandler(handler);

    // TODO(johnlenz): Change these normalization checks Preconditions and
    // Exceptions into Errors so that it is easier to find the root cause
    // when there are cascading issues.
    new PrepareAst(compiler, true).process(null, root);
    Preconditions.checkState(!handler.hasCodeChanged(),
        "This should never fire, NodeTypeNormalizer should assert first.");

    if (compiler.isNormalized()) {
      (new Normalize(compiler, true)).process(externs, root);
      Preconditions.checkState(!handler.hasCodeChanged(),
          "This should never fire, Normalize should assert first.");

      boolean checkUserDeclarations = true;
      CompilerPass pass = new Normalize.VerifyConstants(
          compiler, checkUserDeclarations);
      pass.process(externs, root);
    }

    compiler.removeChangeHandler(handler);
  }
}
