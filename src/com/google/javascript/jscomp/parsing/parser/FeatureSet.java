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
import com.google.errorprone.annotations.InlineMe;
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

  public static final FeatureSet ES2022_MODULES =
      ES2021_MODULES.with(LangVersion.ES2022.features());

  public static final FeatureSet ES2022 = ES2022_MODULES.without(Feature.MODULES);

  // NOTE: when ES2023 is added, the BROWSER_2024 and BROWSER_2025 FeatureSets defined below should
  // be updated to include it.

  // Set of all fully supported features, even those part of language versions not fully supported
  public static final FeatureSet ES_NEXT = ES2022_MODULES.with(LangVersion.ES_NEXT.features());

  // Set of features fully supported in checks, even those not fully supported in optimizations
  public static final FeatureSet ES_UNSTABLE = ES_NEXT.with(LangVersion.ES_UNSTABLE.features());

  // Set of all features that can be parsed, even those not yet fully supported in checks.
  public static final FeatureSet ES_UNSUPPORTED =
      ES_UNSTABLE.with(LangVersion.ES_UNSUPPORTED.features());

  // NOTE: The BROWSER_20XX FeatureSets are the set of features supported by major browsers as of
  // January 1st of the given year. So, typically, take the year of the BFSY, subtract 1 to get
  // the correct ES20XX_MODULES FeatureSet as the base, then exclude any features as needed.
  //
  // Features and their supported browser versions can be found at
  // https://compat-table.github.io/compat-table/es2016plus/ or https://caniuse.com/
  //
  // The format for excluded features is: [Browser name] [version] (version release year)
  // Or, if a browser has no support yet: [Browser name] (unsupported)
  //
  // All excluded features should have a (year) this is greater than or equal to the BFSY.

  public static final FeatureSet BROWSER_2019 =
      ES2018_MODULES.without(
          // NOTE: These 4 are excluded because, historically, we defined BROWSER_2019 as ES2017.
          // See cl/266708942 and cl/328623467 for context.
          // Chrome 60 (2017). Firefox 55 (2017). Safari 11.1 (2018).
          Feature.OBJECT_LITERALS_WITH_SPREAD,
          // Chrome 60 (2017). Firefox 55 (2017). Safari 11.1 (2018).
          Feature.OBJECT_PATTERN_REST,
          // Chrome 63 (2017). Firefox 57 (2017). Safari 12 (2018).
          Feature.ASYNC_GENERATORS,
          // Chrome 63 (2017). Firefox 57 (2017). Safari 12 (2018).
          Feature.FOR_AWAIT_OF,

          // Chrome 62 (2017). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_FLAG_S,
          // Chrome 62 (2017). Firefox 78 (2020). Safari 16.4 (2023).
          Feature.REGEXP_LOOKBEHIND,
          // Chrome 64 (2018). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_NAMED_GROUPS,
          // Chrome 64 (2018). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);

  public static final FeatureSet BROWSER_2020 =
      ES2019_MODULES.without(
          // Chrome 62 (2017). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_FLAG_S,
          // Chrome 64 (2018). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_NAMED_GROUPS,
          // Chrome 64 (2018). Firefox 78 (2020). Safari 11.1 (2018).
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE,
          // Chrome 62 (2017). Firefox 78 (2020). Safari 16.4 (2023).
          Feature.REGEXP_LOOKBEHIND);

  public static final FeatureSet BROWSER_2021 =
      ES2020_MODULES.without(
          // Chrome 62 (2017). Firefox 78 (2020). Safari 16.4 (2023).
          Feature.REGEXP_LOOKBEHIND);

  public static final FeatureSet BROWSER_2022 =
      ES2021_MODULES.without(
          // Chrome 62 (2017). Firefox 78 (2020). Safari 16.4 (2023).
          Feature.REGEXP_LOOKBEHIND);

  public static final FeatureSet BROWSER_2023 =
      ES2022_MODULES.without(
          // Chrome 62 (2017). Firefox 78 (2020). Safari 16.4 (2023).
          Feature.REGEXP_LOOKBEHIND,
          // Chrome 94 (2021). Firefox 93 (2021). Safari 16.4 (2023).
          Feature.CLASS_STATIC_BLOCK);

  // Note: Only "Hashbang Grammar" is an ES2023 syntax feature. Its versions are:
  // Chrome 74 (2019). Firefox 67 (2019). Safari 13.1 (2020).
  public static final FeatureSet BROWSER_2024 = ES2022_MODULES;

  // Note: Only "RegExp `v` flag" is an ES2024 syntax feature. Its versions are:
  // Chrome 112 (2023). Firefox 116 (2023). Safari 17 (2023).
  public static final FeatureSet BROWSER_2025 = ES2022_MODULES;

  // Note: Only "RegExp syntax features again for ES2025. The 'i', 'm', and 's' flags, as well
  // as support for duplicate named capture groups. As of Jan 5 2026, Safari hasn't yet
  // implemented the new flags.
  public static final FeatureSet BROWSER_2026 = ES2022_MODULES;

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
    ES2022,
    ES_NEXT,
    ES_UNSTABLE,
    ES_UNSUPPORTED,
    TYPESCRIPT,
    ;

    private EnumSet<Feature> features() {
      EnumSet<Feature> set = EnumSet.noneOf(Feature.class);
      for (Feature feature : EnumSet.allOf(Feature.class)) {
        if (feature.version == this) {
          set.add(feature);
        }
      }
      return set;
    }
  }

  /** Specific features that can be included in a FeatureSet. */
  public enum Feature {
    // ES3 features
    REGEXP_SYNTAX("RegExp syntax", LangVersion.ES3),

    // ES5 features
    ES3_KEYWORDS_AS_IDENTIFIERS("ES3 keywords as identifiers", LangVersion.ES5),
    GETTER("getters", LangVersion.ES5),
    KEYWORDS_AS_PROPERTIES("reserved words as properties", LangVersion.ES5),
    SETTER("setters", LangVersion.ES5),
    STRING_CONTINUATION("string continuation", LangVersion.ES5),
    TRAILING_COMMA("trailing comma", LangVersion.ES5),

    // ES2015 features (besides modules): all stable browsers are now fully compliant
    // go/keep-sorted start
    ARRAY_DESTRUCTURING("array destructuring", LangVersion.ES2015),
    ARRAY_PATTERN_REST("array pattern rest", LangVersion.ES2015),
    ARROW_FUNCTIONS("arrow function", LangVersion.ES2015),
    BINARY_LITERALS("binary literal", LangVersion.ES2015),
    BLOCK_SCOPED_FUNCTION_DECLARATION("block-scoped function declaration", LangVersion.ES2015),
    CLASSES("class", LangVersion.ES2015),
    CLASS_GETTER_SETTER("class getters/setters", LangVersion.ES2015),
    COMPUTED_PROPERTIES("computed property", LangVersion.ES2015),
    CONST_DECLARATIONS("const declaration", LangVersion.ES2015),
    DEFAULT_PARAMETERS("default parameter", LangVersion.ES2015),
    FOR_OF("for-of loop", LangVersion.ES2015),
    GENERATORS("generator", LangVersion.ES2015),
    LET_DECLARATIONS("let declaration", LangVersion.ES2015),
    MEMBER_DECLARATIONS("member declaration", LangVersion.ES2015),
    NEW_TARGET("new.target", LangVersion.ES2015),
    OBJECT_DESTRUCTURING("object destructuring", LangVersion.ES2015),
    OCTAL_LITERALS("octal literal", LangVersion.ES2015),
    REGEXP_FLAG_U("RegExp flag 'u'", LangVersion.ES2015),
    REGEXP_FLAG_Y("RegExp flag 'y'", LangVersion.ES2015),
    REST_PARAMETERS("rest parameter", LangVersion.ES2015),
    SHORTHAND_OBJECT_PROPERTIES("shorthand object property", LangVersion.ES2015),
    SPREAD_EXPRESSIONS("spread expression", LangVersion.ES2015),
    SUPER("super", LangVersion.ES2015),
    TEMPLATE_LITERALS("template literal", LangVersion.ES2015),
    // go/keep-sorted end

    // ES modules
    MODULES("modules", LangVersion.ES2015),

    // ES 2016 only added one new feature:
    EXPONENT_OP("exponent operator (**)", LangVersion.ES2016),

    // ES 2017 features:
    ASYNC_FUNCTIONS("async function", LangVersion.ES2017),

    // ES 2018 adds https://github.com/tc39/proposal-object-rest-spread
    OBJECT_LITERALS_WITH_SPREAD("object literals with spread", LangVersion.ES2018),
    OBJECT_PATTERN_REST("object pattern rest", LangVersion.ES2018),

    // https://github.com/tc39/proposal-async-iteration
    ASYNC_GENERATORS("async generator functions", LangVersion.ES2018),
    FOR_AWAIT_OF("for-await-of loop", LangVersion.ES2018),

    // ES 2018 adds Regex Features:
    // https://github.com/tc39/proposal-regexp-dotall-flag
    // Note: Untranspilable.
    REGEXP_FLAG_S("RegExp flag 's'", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-named-groups
    // Note: Untranspilable.
    REGEXP_NAMED_GROUPS("RegExp named groups", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-unicode-property-escapes
    // Note: Untranspilable.
    REGEXP_UNICODE_PROPERTY_ESCAPE("RegExp unicode property escape", LangVersion.ES2018),
    // https://github.com/tc39/proposal-regexp-lookbehind
    // Note: Untranspilable.
    REGEXP_LOOKBEHIND("RegExp Lookbehind", LangVersion.ES2018),

    // ES 2019 adds https://github.com/tc39/proposal-json-superset
    // Note: Untranspilable.
    UNESCAPED_UNICODE_LINE_OR_PARAGRAPH_SEP(
        "Unescaped unicode line or paragraph separator", LangVersion.ES2019),

    // ES 2019 adds optional catch bindings:
    // https://github.com/tc39/proposal-optional-catch-binding
    OPTIONAL_CATCH_BINDING("Optional catch binding", LangVersion.ES2019),

    // ES 2020 Stage 4
    DYNAMIC_IMPORT("Dynamic module import", LangVersion.ES2020),
    // Note: Untranspilable.
    BIGINT("bigint", LangVersion.ES2020),
    IMPORT_META("import.meta", LangVersion.ES2020),
    NULL_COALESCE_OP("Nullish coalescing", LangVersion.ES2020),
    OPTIONAL_CHAINING("Optional chaining", LangVersion.ES2020),

    // ES 2021 Stage 4
    NUMERIC_SEPARATOR("numeric separator", LangVersion.ES2021),
    LOGICAL_ASSIGNMENT("Logical assignments", LangVersion.ES2021),

    // ES 2022 adds https://github.com/tc39/proposal-class-fields
    PUBLIC_CLASS_FIELDS("Public class fields", LangVersion.ES2022),

    // ES 2022 adds https://github.com/tc39/proposal-class-static-block
    CLASS_STATIC_BLOCK("Class static block", LangVersion.ES2022),

    // ES 2022 adds https://github.com/tc39/proposal-regexp-match-indices
    // Note: Untranspilable.
    REGEXP_FLAG_D("RegExp flag 'd'", LangVersion.ES2022),

    // ES 2022 adds https://github.com/tc39/proposal-top-level-await
    // Note: Untranspilable. Needs to stay UNSUPPORTED because pass-through of top-level await does
    // not make sense as we don't emit ES6 modules so top-level await cannot be used in the output.
    TOP_LEVEL_AWAIT("Top-level await", LangVersion.ES_UNSUPPORTED),

    // ES_NEXT: Features that are fully supported, but part of a language version that is not yet
    // fully supported

    // Polyfill implementations can target "es_next" as their fromLang so we need to ensure that
    // the "es_next" version name is distinct from the latest dated version so we don't incorrectly
    // prune those polyfills.
    ES_NEXT_RUNTIME("es_next runtime", LangVersion.ES_NEXT),

    // ES_UNSTABLE: Features fully supported in checks, but not fully supported everywhere else

    // Polyfill implementations can target "es_unstable" as their fromLang so we need to ensure that
    // the "es_unstable" version name is distinct from the latest dated version so we don't
    // incorrectly prune those polyfills.
    ES_UNSTABLE_RUNTIME("es_unstable runtime", LangVersion.ES_UNSTABLE),

    // ES_UNSUPPORTED: Features that we can parse, but not yet supported in all checks

    // ES 2022 adds https://github.com/tc39/proposal-class-fields
    PRIVATE_CLASS_PROPERTIES("Private class properties", LangVersion.ES_UNSUPPORTED),

    // TypeScript type syntax that will never be implemented in browsers. Only used as an indicator
    // to the CodeGenerator that it should handle type syntax.
    TYPE_ANNOTATION("type annotation", LangVersion.TYPESCRIPT),
    ; // End of list.

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
    if (ES2022_MODULES.contains(this)) {
      return "es_2022";
    }
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    if (ES_UNSTABLE.contains(this)) {
      return "es_unstable";
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
  @InlineMe(replacement = "this.version()")
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
    return other instanceof FeatureSet featureSet && featureSet.features.equals(features);
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
    return switch (name) {
      case "es3" -> ES3;
      case "es5" -> ES5;
      case "es_2015", "es6" -> ES2015;
      case "es_2016", "es7" -> ES2016;
      case "es_2017", "es8" -> ES2017;
      case "es_2018", "es9" -> ES2018;
      case "es_2019" -> ES2019;
      case "es_2020" -> ES2020;
      case "es_2021" -> ES2021;
      case "es_2022" -> ES2022;
      case "es_next" -> ES_NEXT;
      case "es_unstable" -> ES_UNSTABLE;
      case "es_unsupported" -> ES_UNSUPPORTED;
      case "all" -> ALL;
      default -> throw new IllegalArgumentException("No such FeatureSet: " + name);
    };
  }

  /**
   * Variant of {@link #valueOf} that outputs a {@code BROWSER_20XX} feature set equivalent. This
   * will generally exclude some not-yet-implemented features from a given {@code ES20XX} while also
   * including {@link Feature#MODULES}.
   *
   * <p>Note: The returned feature set will include the {@link Feature#MODULES} feature which you
   * may want to exclude manually.
   */
  public static FeatureSet browserFeatureSetValueOf(String name) {
    FeatureSet featureSet = valueOf(name);
    if (featureSet.contains(ES_NEXT)) {
      // For ES_NEXT and ES_UNSTABLE, we need to return the raw featureSet to retain the RUNTIME
      // features which pin polyfills to future versions.
      return featureSet;
    }
    if (featureSet.contains(ES2022)) {
      return BROWSER_2023;
    }
    if (featureSet.contains(ES2021)) {
      return BROWSER_2022;
    }
    if (featureSet.contains(ES2020)) {
      return BROWSER_2021;
    }
    if (featureSet.contains(ES2019)) {
      return BROWSER_2020;
    }
    if (featureSet.contains(ES2018)) {
      return BROWSER_2019;
    }
    return featureSet;
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
    return ES_UNSUPPORTED;
  }
}
