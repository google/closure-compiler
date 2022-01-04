/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents various aspects of language version and support.
 *
 * <p>This is somewhat redundant with LanguageMode, but is separate for two reasons: (1) it's used
 * for parsing, which cannot depend on LanguageMode, and (2) it's concerned with slightly different
 * nuances: implemented features and modules rather than strictness.
 *
 * <p>In the long term, it would be good to disentangle all these concerns and pull out a single
 * LanguageSyntax enum with a separate strict mode flag, and then these could possibly be unified.
 *
 * <p>Instances of this class are immutable.
 */
@Immutable
public final class FeatureSet implements Serializable {
  private final ImmutableSet<Feature> features;

  /** The bare minimum set of features. */
  public static final FeatureSet BARE_MINIMUM = new FeatureSet(emptyEnumSet());

  /** Features from ES3. */
  public static final FeatureSet ES3 = BARE_MINIMUM.with(LangVersion.ES3.features());

  /** Features from ES5 only. */
  public static final FeatureSet ES5 = ES3.with(LangVersion.ES5.features());

  /** All ES2015 features, including modules. */
  public static final FeatureSet ES2015_MODULES = ES5.with(LangVersion.ES2015.features());

  /** The full set of ES2015 features, not including modules. */
  public static final FeatureSet ES2015 = ES2015_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2016_MODULES =
      ES2015_MODULES.with(LangVersion.ES2016.features());

  public static final FeatureSet ES2016 = ES2016_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2017_MODULES =
      ES2016_MODULES.with(LangVersion.ES2017.features());

  public static final FeatureSet ES2017 = ES2017_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2018_MODULES =
      ES2017_MODULES.with(LangVersion.ES2018.features());

  public static final FeatureSet ES2018 = ES2018_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2019_MODULES =
      ES2018_MODULES.with(LangVersion.ES2019.features());

  public static final FeatureSet ES2019 = ES2019_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2020_MODULES =
      ES2019_MODULES.with(LangVersion.ES2020.features());

  public static final FeatureSet ES2020 = ES2020_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2021_MODULES =
      ES2020_MODULES.with(LangVersion.ES2021.features());

  public static final FeatureSet ES2021 = ES2021_MODULES.without(Feature.MODULES);

  // Set of all fully supported features, even those part of language versions not fully supported
  public static final FeatureSet ES_NEXT = ES2021_MODULES.with(LangVersion.ES_NEXT.features());

  // Set of features fully supported in checks, even those not fully supported in optimizations
  public static final FeatureSet ES_NEXT_IN = ES_NEXT.with(LangVersion.ES_NEXT_IN.features());

  // Set of all features that can be parsed, even those not yet fully supported in checks.
  public static final FeatureSet ES_UNSUPPORTED =
      ES_NEXT_IN.with(LangVersion.ES_UNSUPPORTED.features());

  public static final FeatureSet BROWSER_2020 =
      ES2019_MODULES.without(
          // https://kangax.github.io/compat-table/es2016plus/
          // All four of these are missing in Firefox 71 and lookbehind is missing in Safari 13.
          Feature.REGEXP_FLAG_S,
          Feature.REGEXP_LOOKBEHIND,
          Feature.REGEXP_NAMED_GROUPS,
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);

  public static final FeatureSet BROWSER_2021 =
      ES2020_MODULES.without(
          // https://kangax.github.io/compat-table/es2016plus/
          // Regexp lookbehind is missing in Safari 14.
          // IMPORTANT: There is special casing for this feature and the ones excluded for
          // BROWSER_2020 above in RewritePolyfills.
          // If future Browser FeatureSet Year definitions have to remove any other features, then
          // we need to change the way that is done to avoid incorrect inclusion of polyfills.
          Feature.REGEXP_LOOKBEHIND);

  public static final FeatureSet ALL = ES_UNSUPPORTED.with(LangVersion.TYPESCRIPT.features());

  private enum LangVersion {
    ES3,
    ES5,
    ES2015,
    ES2016,
    ES2017,
    ES2018,
    ES2019,
    ES2020,
    ES2021,
    ES_NEXT_IN,
    ES_NEXT,
    ES_UNSUPPORTED,
    TYPESCRIPT,
    ;

    private EnumSet<Feature> features() {
      EnumSet<Feature> set = EnumSet.noneOf(Feature.class);
      for (Feature feature : Feature.values()) {
        if (feature.version == this) {
          set.add(feature);
        }
      }
      return set;
    }
  }

  /** Specific features that can be included in a FeatureSet. */
  public enum Feature {
    // ES5 features
    ES3_KEYWORDS_AS_IDENTIFIERS("ES3 keywords as identifiers", LangVersion.ES5),
    GETTER("getters", LangVersion.ES5),
    KEYWORDS_AS_PROPERTIES("reserved words as properties", LangVersion.ES5),
    SETTER("setters", LangVersion.ES5),
    STRING_CONTINUATION("string continuation", LangVersion.ES5),
    TRAILING_COMMA("trailing comma", LangVersion.ES5),

    // ES2015 features (besides modules): all stable browsers are now fully compliant
    ARRAY_PATTERN_REST("array pattern rest", LangVersion.ES2015),
    ARROW_FUNCTIONS("arrow function", LangVersion.ES2015),
    BINARY_LITERALS("binary literal", LangVersion.ES2015),
    BLOCK_SCOPED_FUNCTION_DECLARATION("block-scoped function declaration", LangVersion.ES2015),
    CLASSES("class", LangVersion.ES2015),
    CLASS_EXTENDS("class extends", LangVersion.ES2015),
    CLASS_GETTER_SETTER("class getters/setters", LangVersion.ES2015),
    COMPUTED_PROPERTIES("computed property", LangVersion.ES2015),
    CONST_DECLARATIONS("const declaration", LangVersion.ES2015),
    DEFAULT_PARAMETERS("default parameter", LangVersion.ES2015),
    ARRAY_DESTRUCTURING("array destructuring", LangVersion.ES2015),
    OBJECT_DESTRUCTURING("object destructuring", LangVersion.ES2015),
    EXTENDED_OBJECT_LITERALS("extended object literal", LangVersion.ES2015),
    FOR_OF("for-of loop", LangVersion.ES2015),
    GENERATORS("generator", LangVersion.ES2015),
    LET_DECLARATIONS("let declaration", LangVersion.ES2015),
    MEMBER_DECLARATIONS("member declaration", LangVersion.ES2015),
    NEW_TARGET("new.target", LangVersion.ES2015),
    OCTAL_LITERALS("octal literal", LangVersion.ES2015),
    REGEXP_FLAG_U("RegExp flag 'u'", LangVersion.ES2015),
    REGEXP_FLAG_Y("RegExp flag 'y'", LangVersion.ES2015),
    REST_PARAMETERS("rest parameter", LangVersion.ES2015),
    SPREAD_EXPRESSIONS("spread expression", LangVersion.ES2015),
    SUPER("super", LangVersion.ES2015),
    TEMPLATE_LITERALS("template literal", LangVersion.ES2015),

    // ES modules
    MODULES("modules", LangVersion.ES2015),

    // ES 2016 only added one new feature:
    EXPONENT_OP("exponent operator (**)", LangVersion.ES2016),

    // ES 2017 features:
    ASYNC_FUNCTIONS("async function", LangVersion.ES2017),
    TRAILING_COMMA_IN_PARAM_LIST("trailing comma in param list", LangVersion.ES2017),

    // ES 2018 adds https://github.com/tc39/proposal-object-rest-spread
    OBJECT_LITERALS_WITH_SPREAD("object literals with spread", LangVersion.ES2018),
    OBJECT_PATTERN_REST("object pattern rest", LangVersion.ES2018),

    // https://github.com/tc39/proposal-async-iteration
    ASYNC_GENERATORS("async generator functions", LangVersion.ES2018),
    FOR_AWAIT_OF("for-await-of loop", LangVersion.ES2018),

    // ES 2018 adds Regex Features:
    // https://github.com/tc39/proposal-regexp-dotall-flag
    REGEXP_FLAG_S("RegExp flag 's'", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-lookbehind
    REGEXP_LOOKBEHIND("RegExp Lookbehind", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-named-groups
    REGEXP_NAMED_GROUPS("RegExp named groups", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-unicode-property-escapes
    REGEXP_UNICODE_PROPERTY_ESCAPE("RegExp unicode property escape", LangVersion.ES2018),

    // ES 2019 adds https://github.com/tc39/proposal-json-superset
    UNESCAPED_UNICODE_LINE_OR_PARAGRAPH_SEP(
        "Unescaped unicode line or paragraph separator", LangVersion.ES2019),

    // ES 2019 adds optional catch bindings:
    // https://github.com/tc39/proposal-optional-catch-binding
    OPTIONAL_CATCH_BINDING("Optional catch binding", LangVersion.ES2019),

    // ES 2020 Stage 4
    DYNAMIC_IMPORT("Dynamic module import", LangVersion.ES2020),
    BIGINT("bigint", LangVersion.ES2020),
    IMPORT_META("import.meta", LangVersion.ES2020),
    NULL_COALESCE_OP("Nullish coalescing", LangVersion.ES2020),
    OPTIONAL_CHAINING("Optional chaining", LangVersion.ES2020),

    // ES 2021 Stage 4
    NUMERIC_SEPARATOR("numeric separator", LangVersion.ES2021),
    LOGICAL_ASSIGNMENT("Logical assignments", LangVersion.ES2021),

    // ES_NEXT: Features that are fully supported, but part of a language version that is not yet
    // fully supported

    // Checks and optimizations are supported, but not transpilation
    PUBLIC_CLASS_FIELDS("Public class fields", LangVersion.ES_NEXT), // Part of ES2022

    // ES_UNSUPORTED: Features that we can parse, but not yet supported in all checks

    // TypeScript type syntax that will never be implemented in browsers. Only used as an indicator
    // to the CodeGenerator that it should handle type syntax.
    TYPE_ANNOTATION("type annotation", LangVersion.TYPESCRIPT);

    private final String name;
    private final LangVersion version;

    private Feature(String name, LangVersion version) {
      this.name = name;
      this.version = version;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private FeatureSet(EnumSet<Feature> features) {
    // ImmutableSet will only use an EnumSet if the set starts as an EnumSet.
    this.features = ImmutableSet.copyOf(features);
  }

  /**
   * Returns a string representation suitable for use in polyfill definition files and encoding in
   * depgraph and deps.js files.
   */
  public String version() {
    if (ES3.contains(this)) {
      return "es3";
    }
    if (ES5.contains(this)) {
      return "es5";
    }
    if (ES2015_MODULES.contains(this)) {
      return "es6";
    }
    if (ES2016_MODULES.contains(this)) {
      return "es7";
    }
    if (ES2017_MODULES.contains(this)) {
      return "es8";
    }
    if (ES2018_MODULES.contains(this)) {
      return "es9";
    }
    if (ES2019_MODULES.contains(this)) {
      return "es_2019";
    }
    if (ES2020_MODULES.contains(this)) {
      return "es_2020";
    }
    if (ES2021_MODULES.contains(this)) {
      return "es_2021";
    }
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    if (ES_NEXT_IN.contains(this)) {
      return "es_next_in";
    }
    if (ES_UNSUPPORTED.contains(this)) {
      return "es_unsupported";
    }
    if (ALL.contains(this)) {
      return "all";
    }
    throw new IllegalStateException(this.toString());
  }

  /**
   * Returns a string representation useful for debugging.
   *
   * @deprecated Please use {@link #version()} instead.
   */
  @Deprecated
  public String versionForDebugging() {
    return version();
  }

  public FeatureSet without(Feature featureToRemove, Feature... moreFeaturesToRemove) {
    return new FeatureSet(difference(features, EnumSet.of(featureToRemove, moreFeaturesToRemove)));
  }

  public FeatureSet without(FeatureSet other) {
    return new FeatureSet(difference(features, other.features));
  }

  public FeatureSet withoutTypes() {
    return new FeatureSet(difference(features, LangVersion.TYPESCRIPT.features()));
  }

  /**
   * Returns a new {@link FeatureSet} including all features of both {@code this} and {@code other}.
   */
  public FeatureSet union(FeatureSet other) {
    return new FeatureSet(union(features, other.features));
  }

  /** Does this {@link FeatureSet} contain at least one of the features of {@code other}? */
  public boolean containsAtLeastOneOf(FeatureSet other) {
    for (Feature otherFeature : other.features) {
      if (this.features.contains(otherFeature)) {
        return true;
      }
    }
    return false;
  }

  /** Does this {@link FeatureSet} contain all of the features of {@code other}? */
  public boolean contains(FeatureSet other) {
    return this.features.containsAll(other.features);
  }

  /** Does this {@link FeatureSet} contain the given feature? */
  public boolean contains(Feature feature) {
    return this.features.containsAll(EnumSet.of(feature));
  }

  private static EnumSet<Feature> emptyEnumSet() {
    return EnumSet.noneOf(Feature.class);
  }

  private static EnumSet<Feature> enumSetOf(Set<Feature> set) {
    return set.isEmpty() ? emptyEnumSet() : EnumSet.copyOf(set);
  }

  private static EnumSet<Feature> add(Set<Feature> features, Feature feature) {
    EnumSet<Feature> result = enumSetOf(features);
    result.add(feature);
    return result;
  }

  private static EnumSet<Feature> union(Set<Feature> features, Set<Feature> newFeatures) {
    EnumSet<Feature> result = enumSetOf(features);
    result.addAll(newFeatures);
    return result;
  }

  private static EnumSet<Feature> difference(Set<Feature> features, Set<Feature> removedFeatures) {
    EnumSet<Feature> result = enumSetOf(features);
    result.removeAll(removedFeatures);
    return result;
  }

  /** Returns a feature set combining all the features from {@code this} and {@code feature}. */
  public FeatureSet with(Feature feature) {
    if (features.contains(feature)) {
      return this;
    }
    return new FeatureSet(add(features, feature));
  }

  /** Returns a feature set combining all the features from {@code this} and {@code newFeatures}. */
  @VisibleForTesting
  public FeatureSet with(Feature... newFeatures) {
    return new FeatureSet(union(features, ImmutableSet.copyOf(newFeatures)));
  }

  /** Returns a feature set combining all the features from {@code this} and {@code newFeatures}. */
  @VisibleForTesting
  public FeatureSet with(Set<Feature> newFeatures) {
    return new FeatureSet(union(features, newFeatures));
  }

  /** Returns a feature set combining all the features from {@code this} and {@code newFeatures}. */
  @VisibleForTesting
  public FeatureSet with(FeatureSet newFeatures) {
    return new FeatureSet(union(features, newFeatures.features));
  }

  /** Does this {@link FeatureSet} include {@code feature}? */
  public boolean has(Feature feature) {
    return features.contains(feature);
  }

  public ImmutableSet<Feature> getFeatures() {
    return features;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FeatureSet && ((FeatureSet) other).features.equals(features);
  }

  @Override
  public int hashCode() {
    return features.hashCode();
  }

  @Override
  public String toString() {
    return features.toString();
  }

  /** Parses known strings into feature sets. */
  public static FeatureSet valueOf(String name) {
    switch (name) {
      case "es3":
        return ES3;
      case "es5":
        return ES5;
      case "es_2015":
      case "es6":
        return ES2015;
      case "es_2016":
      case "es7":
        return ES2016;
      case "es_2017":
      case "es8":
        return ES2017;
      case "es_2018":
      case "es9":
        return ES2018;
      case "es_2019":
        return ES2019;
      case "es_2020":
        return ES2020;
      case "es_2021":
        return ES2021;
      case "es_next":
        return ES_NEXT;
      case "es_next_in":
        return ES_NEXT_IN;
      case "es_unsupported":
        return ES_UNSUPPORTED;
      case "all":
        return ALL;
      default:
        throw new IllegalArgumentException("No such FeatureSet: " + name);
    }
  }

  /**
   * Returns a {@code FeatureSet} containing all known features.
   *
   * <p>NOTE: {@code PassFactory} classes that claim to support {@code FeatureSet.all()} should be
   * only those that cannot be broken by new features being added to the language. Mainly these are
   * passes that don't have to actually look at the AST at all, like empty marker passes.
   */
  public static FeatureSet all() {
    return ALL;
  }

  public static FeatureSet latest() {
    return ES_NEXT;
  }
}
