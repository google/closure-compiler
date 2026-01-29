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
import com.google.javascript.jscomp.CompilerOptions.BrowserFeaturesetYear;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.regex.RegExpTree;
import com.google.javascript.jscomp.regex.RegExpTree.LookbehindAssertion;
import com.google.javascript.jscomp.regex.RegExpTree.NamedCaptureGroup;
import com.google.javascript.jscomp.regex.RegExpTree.UnicodePropertyEscape;
import com.google.javascript.rhino.Node;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Looks for presence of features that are not supported for transpilation (mostly new RegExp
 * features and bigint literal).
 *
 * <p>Reports errors for any features are present in the root and not present in the targeted output
 * language.
 *
 * <p>Note: the errors reported by this pass are suppressible. In most cases the right thing to do
 * if suppressing them is instead to bump the output language level; for example, someone who only
 * needs to support ES2020 but is setting --language_out=ECMASCRIPT_2015 will see errors on ES2018
 * regex literal syntax. The best solution is to set --language_out=ECMASCRIPT_2020. A second
 * solution, which we support but do not recommend, is to suppress these errors & let the compiler
 * silently output untranspiled ES2018 regex literals.
 */
public final class ReportUntranspilableFeatures extends AbstractPeepholeTranspilation {

  @VisibleForTesting
  public static final DiagnosticType UNTRANSPILABLE_FEATURE_PRESENT =
      DiagnosticType.error(
          "JSC_UNTRANSPILABLE",
          "Cannot convert feature \"{0}\" to targeted output language. Feature requires at minimum"
              + " {1}.{2}");

  @VisibleForTesting
  public static final DiagnosticType UNSUPPORTED_FEATURE_PRESENT =
      DiagnosticType.error(
          "JSC_UNSUPPORTED", "The feature \"{0}\" is currently unsupported for transpilation.");

  private static final FeatureSet UNTRANSPILABLE_ES5_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.GETTER, Feature.SETTER);

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

  private static final FeatureSet UNTRANSPILABLE_2020_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.BIGINT);

  private static final FeatureSet UNTRANSPILABLE_2022_FEATURES =
      FeatureSet.BARE_MINIMUM.with(Feature.REGEXP_FLAG_D, Feature.TOP_LEVEL_AWAIT);

  private static final FeatureSet ALL_UNTRANSPILABLE_FEATURES =
      FeatureSet.BARE_MINIMUM
          .union(UNTRANSPILABLE_ES5_FEATURES)
          .union(UNTRANSPILABLE_2018_FEATURES)
          .union(UNTRANSPILABLE_2019_FEATURES)
          .union(UNTRANSPILABLE_2022_FEATURES)
          .union(UNTRANSPILABLE_2020_FEATURES);

  private final AbstractCompiler compiler;
  private final @Nullable BrowserFeaturesetYear browserFeatureSetYear;
  private final FeatureSet untranspilableFeaturesToRemove;

  /**
   * @param compiler the compiler instance
   * @param browserFeatureSetYear the given BrowserFeaturesetYear, or null if it was not specified
   * @param outputFeatures the set of features supported in the given output language.
   */
  ReportUntranspilableFeatures(
      AbstractCompiler compiler,
      @Nullable BrowserFeaturesetYear browserFeatureSetYear,
      FeatureSet outputFeatures) {
    checkNotNull(compiler);
    checkNotNull(outputFeatures);
    this.compiler = compiler;
    this.browserFeatureSetYear = browserFeatureSetYear;
    this.untranspilableFeaturesToRemove =
        ALL_UNTRANSPILABLE_FEATURES // Features that we can't transpile...
            .without(outputFeatures); // and do not exist in the output language features
  }

  @Override
  FeatureSet getTranspiledAwayFeatures() {
    return untranspilableFeaturesToRemove;
  }

  @Override
  FeatureSet getAdditionalFeaturesToRunOn() {
    /*
     * This pass needs to run on all ES3 REGEXP_SYNTAX because it checks for the presence of
     * non-flag RegExp features that are not parsed and not attached to nodes.
     */
    return FeatureSet.BARE_MINIMUM.with(Feature.REGEXP_SYNTAX);
  }

  private void checkForUntranspilable(Node root) {
    // Non-flag RegExp features are not attached to nodes, so we must force traversal.
    switch (root.getToken()) {
      case REGEXP -> {
        String pattern = root.getFirstChild().getString();
        String flags = root.hasTwoChildren() ? root.getLastChild().getString() : "";
        RegExpTree reg;
        try {
          reg = RegExpTree.parseRegExp(pattern, flags);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
          compiler.report(JSError.make(root, MALFORMED_REGEXP, ex.getMessage()));
          break;
        }
        if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_FLAG_S)) {
          checkForRegExpSFlag(root);
        }
        if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_LOOKBEHIND)) {
          checkForLookbehind(root, reg);
        }
        if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_NAMED_GROUPS)) {
          checkForNamedGroups(root, reg);
        }
        if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_UNICODE_PROPERTY_ESCAPE)) {
          checkForUnicodePropertyEscape(root, reg);
        }
        if (untranspilableFeaturesToRemove.contains(Feature.REGEXP_FLAG_D)) {
          checkForRegExpDFlag(root);
        }
      }
      case BIGINT -> {
        // Transpilation of BigInt is not supported
        if (untranspilableFeaturesToRemove.contains(Feature.BIGINT)) {
          reportUntranspilable(Feature.BIGINT, root);
        }
      }
      case GETTER_DEF -> {
        if (untranspilableFeaturesToRemove.contains(Feature.GETTER)) {
          reportUntranspilable(Feature.GETTER, root);
        }
      }
      case SETTER_DEF -> {
        if (untranspilableFeaturesToRemove.contains(Feature.SETTER)) {
          reportUntranspilable(Feature.SETTER, root);
        }
      }
      case AWAIT, FOR_AWAIT_OF -> {
        if (untranspilableFeaturesToRemove.contains(Feature.TOP_LEVEL_AWAIT)) {
          checkForTopLevelAwait(root);
        }
      }
      default -> {}
    }
  }

  private void reportUntranspilable(Feature feature, Node node) {
    LanguageMode minimumLanguageMode = LanguageMode.minimumRequiredFor(feature);
    if (minimumLanguageMode == LanguageMode.UNSUPPORTED) {
      compiler.report(JSError.make(node, UNSUPPORTED_FEATURE_PRESENT, feature.toString()));
      return;
    }

    // The compiler always has an output featureset configured. Sometimes this is configured
    // directly or via a LanguageMode, and in this case browserFeatureSetYear is null. Otherwise if
    // browserFeatureSetYear is not null, the user configured a browserFeatureSetYear specifically.
    // Report an error message based on the actual API the user invoked: sometimes the year in a
    // browser featureset year (e.g. 2020) does not correspond to e.g. ECMASCRIPT_2020.
    String minimum;
    String suggestion = " Consider targeting a more modern output.";
    if (browserFeatureSetYear != null) {
      BrowserFeaturesetYear minimumYear = BrowserFeaturesetYear.minimumRequiredFor(feature);
      if (minimumYear == null) {
        minimum =
            minimumLanguageMode + ", which is not yet supported by any browser featureset year";
        suggestion = "";
      } else {
        minimum = "browser featureset year " + minimumYear.getYear();
        suggestion =
            suggestion + "\nCurrent browser featureset year: " + browserFeatureSetYear.getYear();
      }
    } else {
      minimum = minimumLanguageMode.toString();
    }
    compiler.report(
        JSError.make(
            node, UNTRANSPILABLE_FEATURE_PRESENT, feature.toString(), minimum, suggestion));
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

  private void checkForTopLevelAwait(Node awaitNode) {
    checkArgument(awaitNode.isAwait() || awaitNode.isForAwaitOf());

    if (NodeUtil.getEnclosingFunction(awaitNode) == null) {
      checkArgument(
          NodeUtil.getEnclosingScript(awaitNode).getBooleanProp(Node.ES6_MODULE),
          "Top-level await is only allowed in ES module sources");
      reportUntranspilable(Feature.TOP_LEVEL_AWAIT, awaitNode);
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

  @Override
  @SuppressWarnings("CanIgnoreReturnValueSuggester")
  Node transpileSubtree(Node subtree) {
    checkForUntranspilable(subtree);
    return subtree;
  }
}
