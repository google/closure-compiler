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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/**
 * Warns at any usage of Dynamic Import expressions that they are unable to be transpiled.
 */
public class RewriteDynamicImport implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.DYNAMIC_IMPORT);

  static final DiagnosticType DYNAMIC_IMPORT_TRANSPILATION =
      DiagnosticType.error(
          "JSC_DYNAMIC_IMPORT_TRANSPILATION",
          "Dynamic import expressions cannot be transpiled.");

  public RewriteDynamicImport(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case DYNAMIC_IMPORT:
        t.report(n, DYNAMIC_IMPORT_TRANSPILATION);
        break;
      default:
        break;
    }
  }
}
