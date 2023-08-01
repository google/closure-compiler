/*
 * Copyright 2019 The Closure Compiler Authors.
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

/**
 * Transpiles catch statements with no bindings by adding an unused binding.
 *
 * <pre>{@code
 * try {
 *   stuff();
 * } catch {
 *   onError();
 * }
 * }</pre>
 *
 * Becomes
 *
 * <pre>{@code
 * try {
 *   stuff();
 * } catch ($jscomp$unused$catch) {
 *   onError();
 * }
 * }</pre>
 */
final class RewriteCatchWithNoBinding implements CompilerPass {
  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.OPTIONAL_CATCH_BINDING);
  private static final String BINDING_NAME = "$jscomp$unused$catch$";

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final UniqueIdSupplier uniqueIdSupplier;

  RewriteCatchWithNoBinding(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.uniqueIdSupplier = compiler.getUniqueIdSupplier();
  }

  private class AddBindings extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isCatch() || !n.getFirstChild().isEmpty()) {
        return;
      }

      Node name =
          astFactory.createNameWithUnknownType(
              BINDING_NAME + uniqueIdSupplier.getUniqueId(t.getInput()));
      n.getFirstChild().replaceWith(name.srcrefTree(n.getFirstChild()));
      t.reportCodeChange();
    }
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, TRANSPILED_FEATURES, new AddBindings());
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, TRANSPILED_FEATURES);
  }
}
