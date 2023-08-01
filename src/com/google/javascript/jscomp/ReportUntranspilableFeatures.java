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
import static com.google.javascript.jscomp.CheckRegExp.MALFORMED_REGEXP;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.regex.RegExpTree;
import com.google.javascript.jscomp.regex.RegExpTree.LookbehindAssertion;
import com.google.javascript.jscomp.regex.RegExpTree.NamedCaptureGroup;
import com.google.javascript.jscomp.regex.RegExpTree.UnicodePropertyEscape;
import com.google.javascript.rhino.Node;
import java.util.function.Predicate;

/**
 * Looks for presence of features that are not supported for transpilation (mostly new RegExp
 * features and bigint literal). Reports errors for any features are present in the root and not
 * present in the targeted output language.
 */
public final class ReportUntranspilableFeatures extends AbstractPostOrderCallback
    implements CompilerPass {

  @VisibleForTesting
  public static final DiagnosticType UNTRANSPILABLE_FEATURE_PRESENT =
      DiagnosticType.error(
          "JSC_UNTRANSPILABLE",
          // TODO(b/123768968) suggest users raise their language level once we support language
          // output higher than ES5.
          "Cannot convert {0} feature \"{1}\" to targeted output language.");

  private static final FeatureSet UNTRANSPILABLE_2018_FEATURES =
      FeatureSet.BARE_MINIMUM.with(
          Feature.REGEXP_FLAG_S,
          Feature.REGEXP_LOOKBEHIND,
          Feature.REGEXP_NAMED_GROUPS,
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);

  private static final FeatureSet UNTRANSPILABLE_2019_FEATURES =
      FeatureSet.BARE_MINIMUM.with(
          // We could transpile this, but there's no point. We always escape these in the output,
          // no need to have a separate pass to escape them. So we'll piggy back off this pass to
          // mark it as transpiled. Note that we never complain that this feature won't be
          // transpiled below.
          Feature.UNESCAPED_UNICODE_LINE_OR_PARAGRAPH_SEP);

  private static final FeatureSet UNTRANSPILABLE_2022_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.REGEXP_FLAG_D);

  private static final FeatureSet UNTRANSPILABLE_2020_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.BIGINT);

  private static final FeatureSet ALL_UNTRANSPILABLE_FEATURES =
      FeatureSet.BARE_MINIMUM
          .union(UNTRANSPILABLE_2018_FEATURES)
          .union(UNTRANSPILABLE_2019_FEATURES)
          .union(UNTRANSPILABLE_2022_FEATURES)
          .union(UNTRANSPILABLE_2020_FEATURES);

  private final AbstractCompiler compiler;
  private final FeatureSet untranspilableFeaturesToRemove;

  ReportUntranspilableFeatures(AbstractCompiler compiler, FeatureSet outputFeatures) {
    checkNotNull(compiler);
    checkNotNull(outputFeatures);
    this.compiler = compiler;
    this.untranspilableFeaturesToRemove =
        ALL_UNTRANSPILABLE_FEATURES // Features that we can't transpile...
            .without(outputFeatures); // and do not exist in the output language features
  }

  @Override
  public void process(Node externs, Node root) {
    checkForUntranspilable(root);
  }

  private void checkForUntranspilable(Node root) {
    // Non-flag RegExp features are not attached to nodes, so we must force traversal.
    NodeTraversal.traverse(compiler, root, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(
        compiler, root, untranspilableFeaturesToRemove);
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
        {
          String pattern = n.getFirstChild().getString();
          String flags = n.hasTwoChildren() ? n.getLastChild().getString() : "";
          RegExpTree reg;
          try {
            reg = RegExpTree.parseRegExp(pattern, flags);
          } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            t.report(n, MALFORMED_REGEXP, ex.getMessage());
            break;
          }
          if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_FLAG_S)) {
            checkForRegExpSFlag(n);
          }
          if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_LOOKBEHIND)) {
            checkForLookbehind(n, reg);
          }
          if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_NAMED_GROUPS)) {
            checkForNamedGroups(n, reg);
          }
          if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_UNICODE_PROPERTY_ESCAPE)) {
            checkForUnicodePropertyEscape(n, reg);
          }
          if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_FLAG_D)) {
            checkForRegExpDFlag(n);
          }
          break;
        }
      case BIGINT:
        {
          // Transpilation of BigInt is not supported
          if (untranspilableFeaturesToRemove.contains(Feature.BIGINT)) {
            reportUntranspilable(Feature.BIGINT, n);
          }
          break;
        }
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

  private void checkForLookbehind(Node regexpNode, RegExpTree tree) {
    checkArgument(regexpNode != null);
    if (anySubtreeMeetsPredicate(tree, t -> t instanceof LookbehindAssertion)) {
      reportUntranspilable(Feature.REGEXP_LOOKBEHIND, regexpNode);
    }
  }

  private void checkForNamedGroups(Node regexpNode, RegExpTree tree) {
    checkArgument(regexpNode != null);
    if (anySubtreeMeetsPredicate(tree, t -> t instanceof NamedCaptureGroup)) {
      reportUntranspilable(Feature.REGEXP_NAMED_GROUPS, regexpNode);
    }
  }

  private void checkForUnicodePropertyEscape(Node regexpNode, RegExpTree tree) {
    checkArgument(regexpNode != null);
    if (anySubtreeMeetsPredicate(tree, t -> t instanceof UnicodePropertyEscape)) {
      reportUntranspilable(Feature.REGEXP_UNICODE_PROPERTY_ESCAPE, regexpNode);
    }
  }

  private void checkForRegExpDFlag(Node regexpNode) {
    checkArgument(regexpNode.isRegExp());
    String flags = regexpNode.hasTwoChildren() ? regexpNode.getLastChild().getString() : "";
    if (flags.contains("d")) {
      reportUntranspilable(Feature.REGEXP_FLAG_D, regexpNode);
    }
  }

  private static boolean anySubtreeMeetsPredicate(RegExpTree tree, Predicate<RegExpTree> p) {
    if (p.test(tree)) {
      return true;
    }
    for (RegExpTree subTree : tree.children()) {
      if (anySubtreeMeetsPredicate(subTree, p)) {
        return true;
      }
    }
    return false;
  }
}
