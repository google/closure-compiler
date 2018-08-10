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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.EnumSet;

/**
 * Looks for presence of features that are not supported for transpilation (mostly new RegExp
 * features). Reports errors for any features are present in the root and not present in the
 * targeted output language.
 */
public final class MarkUntranspilableFeaturesAsRemoved extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  static final DiagnosticType UNTRANSPILABLE_FEATURE_PRESENT =
      DiagnosticType.error(
          "JSC_UNTRANSPILABLE",
          "Cannot convert {0} feature \"{1}\" to targeted output language. "
              + "Either remove feature \"{1}\" or raise output level to {0}.");

  private static final FeatureSet UNTRANSPILABLE_2018_FEATURES =
      FeatureSet.BARE_MINIMUM.with(
          Feature.REGEXP_FLAG_S,
          Feature.REGEXP_LOOKBEHIND,
          Feature.REGEXP_NAMED_GROUPS,
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);

  private static final FeatureSet ALL_UNTRANSPILABLE_FEATURES =
      FeatureSet.BARE_MINIMUM.union(UNTRANSPILABLE_2018_FEATURES);

  private static final FeatureSet ALL_TRANSPILABLE_FEATURES =
      FeatureSet.BARE_MINIMUM.with(
          EnumSet.complementOf(EnumSet.copyOf(ALL_UNTRANSPILABLE_FEATURES.getFeatures())));

  private final AbstractCompiler compiler;
  private final FeatureSet untranspilableFeaturesToRemove;

  public MarkUntranspilableFeaturesAsRemoved(
      AbstractCompiler compiler, FeatureSet inputFeatures, FeatureSet outputFeatures) {
    checkNotNull(compiler);
    checkNotNull(inputFeatures);
    checkNotNull(outputFeatures);
    this.compiler = compiler;
    this.untranspilableFeaturesToRemove =
        inputFeatures // All features in the input language features...
            .without(ALL_TRANSPILABLE_FEATURES) // that we can't transpile...
            .without(outputFeatures); // and do not exist in the output language features
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    checkForUntranspilable(scriptRoot);
  }

  @Override
  public void process(Node externs, Node root) {
    checkForUntranspilable(root);
  }

  private void checkForUntranspilable(Node root) {
    TranspilationPasses.processTranspile(compiler, root, untranspilableFeaturesToRemove, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, untranspilableFeaturesToRemove);
  }

  private void reportUntranspilable(Feature feature, Node node) {
    compiler.report(
        JSError.make(
            node,
            UNTRANSPILABLE_FEATURE_PRESENT,
            LanguageMode.minimumRequiredFor(feature).toString(),
            feature.toString()));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case REGEXP:
        checkForRegExpSFlag(n);
        break;

      default:
        break;
    }
  }

  private void checkForRegExpSFlag(Node regexpNode) {
    checkArgument(regexpNode.isRegExp());
    String flags = regexpNode.hasTwoChildren() ? regexpNode.getLastChild().getString() : "";
    if (flags.contains("s")) {
      reportUntranspilable(Feature.REGEXP_FLAG_S, regexpNode);
    }
  }
}
