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

import com.google.javascript.jscomp.OptionalChainRewriter.TmpVarNameCreator;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.function.Function;

/** Replaces the ES2020 `?.` operator with conditional (? :). */
final class RewriteOptionalChainingOperator implements CompilerPass {

  private static final FeatureSet TRANSPILED_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.OPTIONAL_CHAINING);

  private final AbstractCompiler compiler;
  /**
   * Produces the object responsible for creating temporary variable names for a given
   * CompilerInput.
   */
  private final Function<CompilerInput, TmpVarNameCreator> getTmpVarNameCreatorForInput;

  /** Constructor to be used for actual transpilation */
  RewriteOptionalChainingOperator(AbstractCompiler compiler) {
    this.compiler = compiler;
    // Temporary variable names are generated based on the compiler input they will go into and use
    // a recognizable prefix to aid debugging.
    final UniqueIdSupplier uniqueIdSupplier = compiler.getUniqueIdSupplier();
    this.getTmpVarNameCreatorForInput =
        (CompilerInput input) -> () -> "$jscomp$optchain$tmp" + uniqueIdSupplier.getUniqueId(input);
  }

  /** Constructor for testing. */
  RewriteOptionalChainingOperator(AbstractCompiler compiler, TmpVarNameCreator tmpVarNameCreator) {
    this.compiler = compiler;
    // The test provides a simple variable name creator that makes for more readable test cases.
    this.getTmpVarNameCreatorForInput = (CompilerInput input) -> tmpVarNameCreator;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(
        compiler, externs, TRANSPILED_FEATURES, new TranspilationCallback());
    TranspilationPasses.processTranspile(
        compiler, root, TRANSPILED_FEATURES, new TranspilationCallback());
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, TRANSPILED_FEATURES);
  }

  /** Locates and transpiles all optional chains. */
  private class TranspilationCallback implements NodeTraversal.Callback {
    private final OptionalChainRewriter.Builder rewriterBuilder =
        OptionalChainRewriter.builder(compiler);
    private final ArrayList<OptionalChainRewriter> optChains = new ArrayList<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        // Set the TmpVarNameCreator to be used when rewriting optional chains in this script.
        rewriterBuilder.setTmpVarNameCreator(getTmpVarNameCreatorForInput.apply(t.getInput()));
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isEndOfFullOptChain(n)) {
        optChains.add(rewriterBuilder.build(n));
      } else if (n.isScript()) {
        // We transpile all of the optional chains in a single script as a batch because,
        // rewriting changes the AST in ways that could interfere with traversal
        // if we change the chains as we visit them.
        if (!optChains.isEmpty()) {
          for (OptionalChainRewriter optChain : optChains) {
            optChain.rewrite();
          }
          optChains.clear();
        }
      }
    }
  }
}
