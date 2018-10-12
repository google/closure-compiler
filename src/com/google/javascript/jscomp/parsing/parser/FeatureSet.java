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
 * <p>This is somewhat redundant with LanguageMode, but is separate
 * for two reasons: (1) it's used for parsing, which cannot
 * depend on LanguageMode, and (2) it's concerned with slightly
 * different nuances: implemented features and modules rather
 * than strictness.
 *
 * <p>In the long term, it would be good to disentangle all these
 * concerns and pull out a single LanguageSyntax enum with a
 * separate strict mode flag, and then these could possibly be
 * unified.
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

  public static final FeatureSet ES_NEXT = ES2018_MODULES.with(LangVersion.ES_NEXT.features());

  public static final FeatureSet TYPESCRIPT =  ES_NEXT.with(LangVersion.TYPESCRIPT.features());

  // OBJECT_PATTERN_REST is a 2018 feature, but its transpilation is done by the same pass that
  // handles the destructuring transpilation done for ES6.
  public static final FeatureSet TYPE_CHECK_SUPPORTED =
      ES8.without(Feature.ASYNC_FUNCTIONS).with(Feature.OBJECT_PATTERN_REST);

  private enum LangVersion {
    ES3,
    ES5,
    ES6,
    ES7,
    ES8,
    ES2018,
    ES_NEXT,
    TYPESCRIPT;

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
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    if (TYPESCRIPT.contains(this)) {
      return "ts";
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
    if (TYPE_CHECK_SUPPORTED.contains(this)) {
      return "typeCheckSupported";
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
    if (ES_NEXT.contains(this)) {
      return "es_next";
    }
    if (TYPESCRIPT.contains(this)) {
      return "ts";
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
      case "es6-impl":
      case "es6":
        return ES6;
      case "typeCheckSupported":
        return TYPE_CHECK_SUPPORTED;
      case "es7":
        return ES7;
      case "es8":
        return ES8;
      case "es2018":
      case "es9":
        return ES2018;
      case "es_next":
        return ES_NEXT;
      case "ts":
        return TYPESCRIPT;
      default:
        throw new IllegalArgumentException("No such FeatureSet: " + name);
    }
  }

  public static FeatureSet latest() {
    return TYPESCRIPT;
  }
}
