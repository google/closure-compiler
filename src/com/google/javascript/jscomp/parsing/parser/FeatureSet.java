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

import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.Objects;

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

  /** The number of the language version: 3, 5, or 6. */
  private final int number;
  /** Whether this includes only features supported in current stable browsers. */
  private final boolean supported;
  /** Whether ES6 modules are included. */
  private final boolean es6Modules;
  /** Whether TypeScript syntax is included (for .d.ts support). */
  private final boolean typeScript;

  /** The bare minimum set of features in ES3. */
  public static final FeatureSet ES3 = new FeatureSet(3, true, false, false);
  /** Features from ES5 only. */
  public static final FeatureSet ES5 = new FeatureSet(5, true, false, false);
  /** The full set of ES6 features, not including modules. */
  public static final FeatureSet ES6 = new FeatureSet(6, true, false, false);
  /** All ES6 features, including modules. */
  public static final FeatureSet ES6_MODULES = new FeatureSet(6, false, true, false);
  public static final FeatureSet ES7 = new FeatureSet(7, false, false, false);
  public static final FeatureSet ES7_MODULES = new FeatureSet(7, false, true, false);
  public static final FeatureSet ES8 = new FeatureSet(8, false, false, false);
  public static final FeatureSet ES8_MODULES = new FeatureSet(8, false, true, false);
  /** TypeScript syntax. */
  public static final FeatureSet TYPESCRIPT = new FeatureSet(8, false, true, true);

  /**
   * Specific features that can be included (indirectly) in a FeatureSet.
   * This primarily adds a name so that helper functions can simultaneously
   * update the detected features and also warn about unsupported features
   * by name.  Additionally, collecting these all in one place provides a
   * single file that needs to be edited to update the current browser support.
   */
  public enum Feature {
    // ES5 features
    ES3_KEYWORDS_AS_IDENTIFIERS("ES3 keywords as identifiers", ES5),
    GETTER("getters", ES5),
    KEYWORDS_AS_PROPERTIES("reserved words as properties", ES5),
    SETTER("setters", ES5),
    STRING_CONTINUATION("string continuation", ES5),
    TRAILING_COMMA("trailing comma", ES5),

    // ES6 features (besides modules): all stable browsers are now fully compliant
    ARROW_FUNCTIONS("arrow function", ES6),
    BINARY_LITERALS("binary literal", ES6),
    OCTAL_LITERALS("octal literal", ES6),
    CLASSES("class", ES6),
    COMPUTED_PROPERTIES("computed property", ES6),
    EXTENDED_OBJECT_LITERALS("extended object literal", ES6),
    FOR_OF("for-of loop", ES6),
    GENERATORS("generator", ES6),
    LET_DECLARATIONS("let declaration", ES6),
    MEMBER_DECLARATIONS("member declaration", ES6),
    REGEXP_FLAG_Y("RegExp flag 'y'", ES6),
    REST_PARAMETERS("rest parameter", ES6),
    SPREAD_EXPRESSIONS("spread expression", ES6),
    SUPER("super", ES6),
    TEMPLATE_LITERALS("template literal", ES6),
    CONST_DECLARATIONS("const declaration", ES6),
    DESTRUCTURING("destructuring", ES6),
    NEW_TARGET("new.target", ES6),
    REGEXP_FLAG_U("RegExp flag 'u'", ES6),
    DEFAULT_PARAMETERS("default parameter", ES6),

    // ES6 features that include modules
    MODULES("modules", ES6_MODULES),

    // '**' operator
    EXPONENT_OP("exponent operator (**)", ES7),

    // http://tc39.github.io/ecmascript-asyncawait/
    ASYNC_FUNCTIONS("async function", ES8),

    // ES6 typed features that are not at all implemented in browsers
    AMBIENT_DECLARATION("ambient declaration", TYPESCRIPT),
    CALL_SIGNATURE("call signature", TYPESCRIPT),
    CONSTRUCTOR_SIGNATURE("constructor signature", TYPESCRIPT),
    ENUM("enum", TYPESCRIPT),
    GENERICS("generics", TYPESCRIPT),
    IMPLEMENTS("implements", TYPESCRIPT),
    INDEX_SIGNATURE("index signature", TYPESCRIPT),
    INTERFACE("interface", TYPESCRIPT),
    MEMBER_VARIABLE_IN_CLASS("member variable in class", TYPESCRIPT),
    NAMESPACE_DECLARATION("namespace declaration", TYPESCRIPT),
    OPTIONAL_PARAMETER("optional parameter", TYPESCRIPT),
    TYPE_ALIAS("type alias", TYPESCRIPT),
    TYPE_ANNOTATION("type annotation", TYPESCRIPT);

    private final String name;
    private final FeatureSet features;

    private Feature(String name, FeatureSet features) {
      this.name = name;
      this.features = features;
    }

    public FeatureSet features() {
      return features;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private FeatureSet(int number, boolean supported, boolean es6Modules, boolean typeScript) {
    this.number = number;
    this.supported = supported;
    this.es6Modules = es6Modules;
    this.typeScript = typeScript;
  }

  /** Returns a string representation suitable for encoding in depgraph and deps.js files. */
  public String version() {
    if (typeScript) {
      return "ts";
    } else if (number > 5) {
      return "es" + number;
    } else if (es6Modules) {
      return "es6";
    } else if (number > 3) {
      return "es5";
    }
    return "es3";
  }

  /** Returns whether this feature set includes ES6 modules. */
  public boolean hasEs6Modules() {
    return es6Modules;
  }

  /** Returns whether this feature set includes typescript features. */
  public boolean isTypeScript() {
    return typeScript;
  }

  /** Returns a feature set combining all the features from {@code this} and {@code other}. */
  public FeatureSet require(FeatureSet other) {
    return this.contains(other) ? this : this.union(other);
  }

  public FeatureSet withoutModules() {
    if (!es6Modules) {
      return this;
    }
    return new FeatureSet(number, supported, false, typeScript);
  }

  public FeatureSet withoutTypes() {
    if (!typeScript) {
      return this;
    }
    return new FeatureSet(number, supported, es6Modules, false);
  }

  /**
   * Returns a new {@link FeatureSet} including all features of both {@code this} and {@code other}.
   */
  public FeatureSet union(FeatureSet other) {
    return new FeatureSet(
        Math.max(number, other.number),
        supported && other.supported,
        es6Modules || other.es6Modules,
        typeScript || other.typeScript);
  }

  /**
   * Does this {@link FeatureSet} contain all of the features of {@code other}?
   */
  public boolean contains(FeatureSet other) {
    return this.number >= other.number
        && (!this.supported || other.supported)
        && (this.es6Modules || !other.es6Modules)
        && (this.typeScript || !other.typeScript);
  }

  /** Returns a feature set combining all the features from {@code this} and {@code feature}. */
  public FeatureSet require(Feature feature) {
    return require(feature.features);
  }

  /**
   * Does this {@link FeatureSet} include {@code feature}?
   */
  public boolean contains(Feature feature) {
    return contains(feature.features());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FeatureSet
        && ((FeatureSet) other).number == number
        && ((FeatureSet) other).supported == supported
        && ((FeatureSet) other).es6Modules == es6Modules
        && ((FeatureSet) other).typeScript == typeScript;
  }

  @Override
  public int hashCode() {
    return Objects.hash(number, supported, es6Modules, typeScript);
  }

  @Override
  public String toString() {
    return "FeatureSet{number=" + number
        + (!supported ? ", unsupported" : "")
        + (es6Modules ? ", es6Modules" : "")
        + (typeScript ? ", typeScript" : "")
        + "}";
  }

  /** Returns a the name of a corresponding LanguageMode enum element. */
  public String toLanguageModeString() {
    return number > 6 ? "ECMASCRIPT_" + (2009 + number) : "ECMASCRIPT" + number;
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
      case "es7":
        return ES7;
      case "es8":
        return ES8;
      case "ts":
        return TYPESCRIPT;
      default:
        throw new IllegalArgumentException("No such FeatureSet: " + name);
    }
  }

  public boolean isEs6ImplOrHigher() {
    String version = version();
    return !version.equals("es3") && !version.equals("es5");
  }
}
