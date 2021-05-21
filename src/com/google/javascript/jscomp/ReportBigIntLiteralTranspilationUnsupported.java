/*
 * Copyright 2020 The Closure Compiler Authors.
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


import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/** Reports an error when attempting to transpile away BitInt literals. */
final class ReportBigIntLiteralTranspilationUnsupported implements CompilerPass {
  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.BIGINT);

  static final DiagnosticType BIGINT_TRANSPILATION =
      DiagnosticType.error("JSC_BIGINT_TRANSPILATION", "transpilation of BigInt is not supported");

  private final AbstractCompiler compiler;

  ReportBigIntLiteralTranspilationUnsupported(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static class ReportErrorForBigIntLiterals extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isBigInt()) {
        // Transpilation of BigInt is not supported
        t.report(n, BIGINT_TRANSPILATION);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(
        compiler, root, TRANSPILED_FEATURES, new ReportErrorForBigIntLiterals());
  }
}
