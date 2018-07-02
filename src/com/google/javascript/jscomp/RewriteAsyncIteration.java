/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * Noop pass reporting an error if scripts attempt to transpile asynchronous generators or
 * for-await-of loops - these features are not yet ready to be transpiled.
 */
public final class RewriteAsyncIteration extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType CANNOT_CONVERT_ASYNC_ITERATION_YET =
      DiagnosticType.error(
          "JSC_CANNOT_CONVERT_ASYNC_ITERATION_YET",
          "Cannot convert async iteration/generators yet.");

  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.ASYNC_GENERATORS, Feature.FOR_AWAIT_OF);

  private final AbstractCompiler compiler;

  public RewriteAsyncIteration(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.processTranspile(compiler, scriptRoot, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isForAwaitOf() || (n.isAsyncFunction() && n.isGeneratorFunction())) {
      compiler.report(JSError.make(n, CANNOT_CONVERT_ASYNC_ITERATION_YET));
    }
  }
}
