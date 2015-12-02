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

import java.util.Objects;

/**
 * Represents various aspects of language version and support.
 * This is somewhat redundant with LanguageMode, but is separate
 * for two reasons: (1) it's used for parsing, which cannot
 * depend on LanguageMode, and (2) it's concerned with slightly
 * different nuances: implemented features and modules rather
 * than strictness.
 *
 * <p>In the long term, it would be good to disentangle all these
 * concerns and pull out a single LanguageSyntax enum with a
 * separate strict mode flag, and then these could possibly be
 * unified.
 */
public final class FeatureSet {

  /** The number of the language version: 3, 5, or 6. */
  private final int number;
  /** Whether this includes features not supported in current stable browsers. */
  private final boolean unsupported;
  /** Whether ES6 modules are included. */
  private final boolean es6Modules;
  /** Whether TypeScript syntax is included (for .d.ts support). */
  private final boolean typeScript;

  /** The bare minimum set of features in ES3. */
  public static final FeatureSet ES3 = new FeatureSet(3, false, false, false);
  /** Features from ES5 only. */
  public static final FeatureSet ES5 = new FeatureSet(5, false, false, false);
  /** The subset of ES6 features that are implemented in stable Chrome, Firefox, and Edge. */
  public static final FeatureSet ES6_IMPL = new FeatureSet(6, false, false, false);
  /** The full set of ES6 features, not including modules. */
  public static final FeatureSet ES6 = new FeatureSet(6, true, false, false);
  /** All ES6 features, including modules. */
  public static final FeatureSet ES6_MODULES = new FeatureSet(6, true, true, false);
  /** TypeScript syntax. */
  public static final FeatureSet TYPESCRIPT = new FeatureSet(6, true, false, true);

  private FeatureSet(int number, boolean unsupported, boolean es6Modules, boolean typeScript) {
    this.number = number;
    this.unsupported = unsupported;
    this.es6Modules = es6Modules;
    this.typeScript = typeScript;
  }

  /** Returns a string representation suitable for encoding in depgraph and deps.js files. */
  public String version() {
    if (typeScript) {
      return "ts";
    } else if (unsupported || es6Modules) {
      return "es6";
    } else if (number > 5) {
      return "es6-impl";
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
    if (other.number > number
        || (other.unsupported && !unsupported)
        || (other.es6Modules && !es6Modules)
        || (other.typeScript && !typeScript)) {
      return new FeatureSet(
          Math.max(number, other.number),
          unsupported || other.unsupported,
          es6Modules || other.es6Modules,
          typeScript || other.typeScript);
    }
    return this;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FeatureSet
        && ((FeatureSet) other).number == number
        && ((FeatureSet) other).unsupported == unsupported
        && ((FeatureSet) other).es6Modules == es6Modules
        && ((FeatureSet) other).typeScript == typeScript;
  }

  @Override
  public int hashCode() {
    return Objects.hash(number, unsupported, es6Modules, typeScript);
  }
}
