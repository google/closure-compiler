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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/**
 * Removes trailing commas when transpiling to anything before ECMASCRIPT 2017.
 *
 * <pre>{@code
 * function f(a, b,) {}
 * f(1, 2,);
 * let x = new Number(1,);
 * }</pre>
 *
 * Becomes
 *
 * <pre>{@code
 * function f(a, b) {}
 * f(1, 2);
 * let x = new Number(1);
 * }</pre>
 */
final class RemoveTrailingCommaFromParamList implements CompilerPass {
  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.TRAILING_COMMA_IN_PARAM_LIST);

  private final AbstractCompiler compiler;

  RemoveTrailingCommaFromParamList(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private static class RemoveComma extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Optional chaining should be removed before this pass runs
      checkState(!n.isOptChainCall());
      if (n.hasTrailingComma() && (n.isParamList() || n.isCall() || n.isNew())) {
        n.setTrailingComma(false);
        // We do not report this as a code change, because trailing commas are not considered when
        // evaluating the equality of nodes.
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, TRANSPILED_FEATURES, new RemoveComma());
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, TRANSPILED_FEATURES);
  }
}
