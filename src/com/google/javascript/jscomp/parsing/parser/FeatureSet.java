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

  /** All ES6 features, including modules. */
  public static final FeatureSet ES6_MODULES = ES5.with(LangVersion.ES6.features());

  /** The full set of ES6 features, not including modules. */
  public static final FeatureSet ES6 = ES6_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES7_MODULES = ES6_MODULES.with(LangVersion.ES7.features());

  public static final FeatureSet ES7 = ES7_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES8_MODULES = ES7_MODULES.with(LangVersion.ES8.features());

  public static final FeatureSet ES8 = ES8_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2018_MODULES = ES8_MODULES.with(LangVersion.ES2018.features());

  public static final FeatureSet ES2018 = ES2018_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2019_MODULES =
      ES2018_MODULES.with(LangVersion.ES2019.features());

  public static final FeatureSet ES2019 = ES2019_MODULES.without(Feature.MODULES);

  public static final FeatureSet ES2020_MODULES =
      ES2019_MODULES.with(LangVersion.ES2020.features());

  public static final FeatureSet ES2020 = ES2020_MODULES.without(Feature.MODULES);

  // "highest" output level
  public static final FeatureSet ES_NEXT = ES2020_MODULES.with(LangVersion.ES_NEXT.features());

  // "highest" input level; for features that can be transpiled but lack optimization/pass through
  public static final FeatureSet ES_NEXT_IN = ES_NEXT.with(LangVersion.ES_NEXT_IN.features());

  public static final FeatureSet ES_UNSUPPORTED =
      ES_NEXT_IN.with(LangVersion.ES_UNSUPPORTED.features());

  public static final FeatureSet TYPESCRIPT = ES_NEXT_IN.with(LangVersion.TYPESCRIPT.features());

  public static final FeatureSet BROWSER_2020 =
      ES2019_MODULES.without(
          // https://kangax.github.io/compat-table/es2016plus/
          // All four of these are missing in Firefox 71 and lookbehind is missing in Safari 13.
          Feature.REGEXP_FLAG_S,
          Feature.REGEXP_LOOKBEHIND,
          Feature.REGEXP_NAMED_GROUPS,
          Feature.REGEXP_UNICODE_PROPERTY_ESCAPE);

  public static final FeatureSet TS_UNSUPPORTED =
      TYPESCRIPT.with(LangVersion.ES_UNSUPPORTED.features());

  private enum LangVersion {
    ES3,
    ES5,
    ES6,
    ES7,
    ES8,
    ES2018,
    ES2019,
    ES2020,
    ES_NEXT_IN,
    ES_NEXT,
    ES_UNSUPPORTED,
    TYPESCRIPT,
    TS_UNSUPPORTED,
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

    // ES6 features (besides modules): all stable browsers are now fully compliant
    ARRAY_PATTERN_REST("array pattern rest", LangVersion.ES6),
    ARROW_FUNCTIONS("arrow function", LangVersion.ES6),
    BINARY_LITERALS("binary literal", LangVersion.ES6),
    BLOCK_SCOPED_FUNCTION_DECLARATION("block-scoped function declaration", LangVersion.ES6),
    CLASSES("class", LangVersion.ES6),
    CLASS_EXTENDS("class extends", LangVersion.ES6),
    CLASS_GETTER_SETTER("class getters/setters", LangVersion.ES6),
    COMPUTED_PROPERTIES("computed property", LangVersion.ES6),
    CONST_DECLARATIONS("const declaration", LangVersion.ES6),
    DEFAULT_PARAMETERS("default parameter", LangVersion.ES6),
    ARRAY_DESTRUCTURING("array destructuring", LangVersion.ES6),
    OBJECT_DESTRUCTURING("object destructuring", LangVersion.ES6),
    EXTENDED_OBJECT_LITERALS("extended object literal", LangVersion.ES6),
    FOR_OF("for-of loop", LangVersion.ES6),
    GENERATORS("generator", LangVersion.ES6),
    LET_DECLARATIONS("let declaration", LangVersion.ES6),
    MEMBER_DECLARATIONS("member declaration", LangVersion.ES6),
    NEW_TARGET("new.target", LangVersion.ES6),
    OCTAL_LITERALS("octal literal", LangVersion.ES6),
    REGEXP_FLAG_U("RegExp flag 'u'", LangVersion.ES6),
    REGEXP_FLAG_Y("RegExp flag 'y'", LangVersion.ES6),
    REST_PARAMETERS("rest parameter", LangVersion.ES6),
    SPREAD_EXPRESSIONS("spread expression", LangVersion.ES6),
    SUPER("super", LangVersion.ES6),
    TEMPLATE_LITERALS("template literal", LangVersion.ES6),

    // ES6 modules
    MODULES("modules", LangVersion.ES6),

    // ES 2016 only added one new feature:
    EXPONENT_OP("exponent operator (**)", LangVersion.ES7),

    // ES 2017 features:
    ASYNC_FUNCTIONS("async function", LangVersion.ES8),
    TRAILING_COMMA_IN_PARAM_LIST("trailing comma in param list", LangVersion.ES8),

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

    // Stage 3 proposals likely to be part of ES2020
    DYNAMIC_IMPORT("Dynamic module import", LangVersion.ES_UNSUPPORTED),
    IMPORT_META("import.meta", LangVersion.ES_NEXT),

    // ES 2020 Stage 4
    NULL_COALESCE_OP("Nullish coalescing", LangVersion.ES2020),
    OPTIONAL_CHAINING("Optional chaining", LangVersion.ES_UNSUPPORTED),

    // ES6 typed features that are not at all implemented in browsers
    ACCESSIBILITY_MODIFIER("accessibility modifier", LangVersion.TYPESCRIPT),
    AMBIENT_DECLARATION("ambient declaration", LangVersion.TYPESCRIPT),
    CALL_SIGNATURE("call signature", LangVersion.TYPESCRIPT),
    CONSTRUCTOR_SIGNATURE("constructor signature", LangVersion.TYPESCRIPT),
    ENUM("enum", LangVersion.TYPESCRIPT),
    GENERICS("generics", LangVersion.TYPESCRIPT),
    IMPLEMENTS("implements", LangVersion.TYPESCRIPT),
    INDEX_SIGNATURE("index signature", LangVersion.TYPESCRIPT),
    INTERFACE("interface", LangVersion.TYPESCRIPT),
    MEMBER_VARIABLE_IN_CLASS("member variable in class", LangVersion.TYPESCRIPT),
    NAMESPACE_DECLARATION("namespace declaration", LangVersion.TYPESCRIPT),
    OPTIONAL_PARAMETER("optional parameter", LangVersion.TYPESCRIPT),
    TYPE_ALIAS("type alias", LangVersion.TYPESCRIPT),
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

  /** Returns a string representation suitable for encoding in depgraph and deps.js files. */
  public String version() {
    if (ES3.contains(this)) {
      return "es3";
    }
    if (ES5.contains(this)) {
      return "es5";
    }
    if (ES6_MODULES.contains(this)) {
      return "es6";
    }
    if (ES7_MODULES.contains(this)) {
      return "es7";
    }
    if (ES8_MODULES.contains(this)) {
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
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    if (ES_NEXT_IN.contains(this)) {
      return "es_next_in";
    }
    if (ES_UNSUPPORTED.contains(this)) {
      return "es_unsupported";
    }
    if (TYPESCRIPT.contains(this)) {
      return "ts";
    }
    if (TS_UNSUPPORTED.contains(this)) {
      return "ts_unsupported";
    }
    throw new IllegalStateException(this.toString());
  }

  /**
   * Returns a string representation useful for debugging.
   *
   * <p>This is not suitable for encoding in deps.js or depgraph files, because it may return
   * strings like 'otiSupported' and 'ntiSupported' which are not real language modes.
   */
  public String versionForDebugging() {
    if (ES3.contains(this)) {
      return "es3";
    }
    if (ES5.contains(this)) {
      return "es5";
    }
    if (ES6_MODULES.contains(this)) {
      return "es6";
    }
    if (ES7_MODULES.contains(this)) {
      return "es7";
    }
    if (ES8_MODULES.contains(this)) {
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
    // Note that this method will not return "es_next" when ES_NEXT contains only features that
    // are part of an official ES spec release. It will return the name of that release instead.
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    // Note that this method will not return "es_unsupported" when ES_UNSUPPORTED
    // contains the same features as ES_NEXT. It will return es_next.
    if (ES_UNSUPPORTED.contains(this)) {
      return "es_unsupported";
    }
    if (TYPESCRIPT.contains(this)) {
      return "ts";
    }
    if (TS_UNSUPPORTED.contains(this)) {
      return "ts_unsupported";
    }
    throw new IllegalStateException(this.toString());
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

  /**
   * Does this {@link FeatureSet} contain all of the features of {@code other}?
   */
  public boolean contains(FeatureSet other) {
    return this.features.containsAll(other.features);
  }

  /**
   * Does this {@link FeatureSet} contain the given feature?
   */
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

  /**
   * Does this {@link FeatureSet} include {@code feature}?
   */
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
      case "es6":
        return ES6;
      case "es7":
        return ES7;
      case "es8":
        return ES8;
      case "es2018":
      case "es9":
        return ES2018;
      case "es_2019":
        return ES2019;
      case "es_2020":
        return ES2020;
      case "es_next":
        return ES_NEXT;
      case "es_next_in":
        return ES_NEXT_IN;
      case "es_unsupported":
        return ES_UNSUPPORTED;
      case "ts":
        return TYPESCRIPT;
      case "ts_unsupported":
        return TS_UNSUPPORTED;
      default:
        throw new IllegalArgumentException("No such FeatureSet: " + name);
    }
  }

  /**
   * Returns a {@code FeatureSet} containing all known features.
   *
   * <p>NOTE: {@code PassFactory} classes that claim to support {@code FeatureSet.everything()}
   * should be only those that cannot be broken by new features being added to the language. Mainly
   * these are passes that don't have to actually look at the AST at all, like empty marker passes.
   */
  public static FeatureSet all() {
    return TS_UNSUPPORTED;
  }

  public static FeatureSet latest() {
    return ES_NEXT;
  }
}
