/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * A compiler pass to run various peephole transpilations (e.g. rewriteCatchBindings,
 * rewriteNewDotTarget, reportUntranspilableFeatures, es6NormalizeShorthandProperties, etc).
 */
class PeepholeTranspilationsPass implements CompilerPass {

  private final AbstractCompiler compiler;
  // NOTE: Use a native array rather than a List to avoid creating iterators for every node in the
  // AST.
  private final AbstractPeepholeTranspilation[] peepholeTranspilations;

  // The feature set to mark as transpiled away after all peephole transpilations are done.
  private final FeatureSet featureSetToMarkAsTranspiledAway;

  // The feature set that triggers this pass. That is, this pass will only run on scripts that
  // contain at least one of these features.
  private final FeatureSet featureSetToRunOn;

  private PeepholeTranspilationsPass(
      AbstractCompiler compiler,
      List<AbstractPeepholeTranspilation> transpilations,
      FeatureSet featureSetToRunOn,
      FeatureSet featureSetToMarkAsTranspiledAway) {
    this.compiler = compiler;
    this.peepholeTranspilations = transpilations.toArray(new AbstractPeepholeTranspilation[0]);
    this.featureSetToRunOn = featureSetToRunOn;
    this.featureSetToMarkAsTranspiledAway = featureSetToMarkAsTranspiledAway;
  }

  /** Creates a peephole optimization pass that runs the given optimizations. */
  public static PeepholeTranspilationsPass create(
      AbstractCompiler compiler, List<AbstractPeepholeTranspilation> transpilations) {
    FeatureSet featureSetToRunOn = FeatureSet.BARE_MINIMUM;
    FeatureSet featureSetToMarkAsTranspiledAway = FeatureSet.BARE_MINIMUM;
    // initialize the feature sets to the union of all the features that the peephole transpilations
    // need to run on and the features that they transpile away.
    for (AbstractPeepholeTranspilation transpilation : transpilations) {
      checkState(
          !transpilation
              .getAdditionalFeaturesToRunOn()
              .containsAtLeastOneOf(transpilation.getTranspiledAwayFeatures()),
          "Transpilation pass %s has getAdditionalFeaturesToRunOn() that contains features that it"
              + " transpiles away.",
          transpilation.getClass().getSimpleName());
      featureSetToRunOn = featureSetToRunOn.with(transpilation.getTranspiledAwayFeatures());
      // some passes need to run on additional features beyond those they transpile away.
      featureSetToRunOn = featureSetToRunOn.with(transpilation.getAdditionalFeaturesToRunOn());
      featureSetToMarkAsTranspiledAway =
          featureSetToMarkAsTranspiledAway.with(transpilation.getTranspiledAwayFeatures());
    }
    return new PeepholeTranspilationsPass(
        compiler, transpilations, featureSetToRunOn, featureSetToMarkAsTranspiledAway);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new PeepCallback());
    // Update the featureSets with all features that got transpiled.
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(
        compiler, root, this.featureSetToMarkAsTranspiledAway);
  }

  private class PeepCallback implements NodeTraversal.Callback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {

      Node currentNode = n;
      for (AbstractPeepholeTranspilation transpilation : peepholeTranspilations) {
        currentNode = transpilation.transpileSubtree(currentNode);
        if (currentNode == null) {
          // this subtree was removed by the current transpilation, so we don't need to run the rest
          // of the peephole transpilations.
          return;
        }
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          // check if the script contains any of the features that we are transpiling.
          FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(n);
          return scriptFeatures.containsAtLeastOneOf(featureSetToRunOn);
        default:
          return true;
      }
    }
  }
}
