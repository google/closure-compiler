/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import java.util.Set;

/**
 * Configuration for the AST factory. Should be shared across AST creation
 * for all files of a compilation process.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class Config {

  /**
   * Level of language strictness required for the input source code.
   */
  public enum StrictMode {
    STRICT, SLOPPY;
  }

  /** JavaScript mode */
  public enum LanguageMode {

    // Note that minimumRequiredFor() relies on these being defined in order from fewest features to
    // most features, and _STRICT versions should be supplied after unspecified strictness.
    ECMASCRIPT3(FeatureSet.ES3),
    ECMASCRIPT5(FeatureSet.ES5),
    ECMASCRIPT6(FeatureSet.ES6_MODULES),
    ECMASCRIPT7(FeatureSet.ES7_MODULES),
    ECMASCRIPT8(FeatureSet.ES8_MODULES),
    TYPESCRIPT(FeatureSet.TYPESCRIPT),
    ;

    public final FeatureSet featureSet;

    LanguageMode(FeatureSet featureSet) {
      this.featureSet = featureSet;
    }

    /**
     * Returns the lowest {@link LanguageMode} that supports the specified feature.
     */
    public static LanguageMode minimumRequiredFor(FeatureSet.Feature feature) {
      // relies on the LanguageMode enums being in the right order
      for (LanguageMode mode : LanguageMode.values()) {
        if (mode.featureSet.contains(feature)) {
          return mode;
        }
      }
      throw new IllegalStateException("No input language mode supports feature: " + feature);
    }
  }

  /**
   * Whether to parse the descriptions of JsDoc comments.
   */
  public enum JsDocParsing {
    TYPES_ONLY,
    INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
    INCLUDE_DESCRIPTIONS_WITH_WHITESPACE;

    boolean shouldParseDescriptions() {
      return this != TYPES_ONLY;
    }
  }
  final JsDocParsing parseJsDocDocumentation;

  /**
   * Whether to keep going after encountering a parse error.
   */
  public enum RunMode {
    STOP_AFTER_ERROR,
    KEEP_GOING,
  }
  final RunMode keepGoing;

  /**
   * Recognized JSDoc annotations, mapped from their name to their internal
   * representation.
   */
  final ImmutableMap<String, Annotation> annotationNames;

  /**
   * Recognized names in a {@code @suppress} tag.
   */
  final ImmutableSet<String> suppressionNames;

  /**
   * Accept ECMAScript5 syntax, such as getter/setter.
   */
  final LanguageMode languageMode;

  final StrictMode strictMode;

  /**
   * Parse inline source maps (//# sourceMappingURL=data:...).
   */
  final boolean parseInlineSourceMaps;

  Config(
      Set<String> annotationWhitelist,
      Set<String> suppressionNames,
      LanguageMode languageMode,
      StrictMode strictMode) {
    this(
        annotationWhitelist,
        JsDocParsing.TYPES_ONLY,
        RunMode.STOP_AFTER_ERROR,
        suppressionNames,
        languageMode,
        false,
        strictMode);
  }

  Config(
      Set<String> annotationWhitelist,
      JsDocParsing parseJsDocDocumentation,
      RunMode keepGoing,
      Set<String> suppressionNames,
      LanguageMode languageMode,
      boolean parseInlineSourceMaps,
      StrictMode strictMode) {
    checkArgument(!(languageMode == LanguageMode.ECMASCRIPT3 && strictMode == StrictMode.STRICT));
    this.parseInlineSourceMaps = parseInlineSourceMaps;
    this.annotationNames = buildAnnotationNames(annotationWhitelist);
    this.parseJsDocDocumentation = parseJsDocDocumentation;
    this.keepGoing = keepGoing;
    this.suppressionNames = ImmutableSet.copyOf(suppressionNames);
    this.languageMode = languageMode;
    this.strictMode = strictMode;
  }

  /**
   * Create the annotation names from the user-specified
   * annotation whitelist.
   */
  private static ImmutableMap<String, Annotation> buildAnnotationNames(
      Set<String> annotationWhitelist) {
    ImmutableMap.Builder<String, Annotation> annotationBuilder =
        ImmutableMap.builder();
    annotationBuilder.putAll(Annotation.recognizedAnnotations);
    for (String unrecognizedAnnotation : annotationWhitelist) {
      if (!unrecognizedAnnotation.isEmpty()
          && !Annotation.recognizedAnnotations.containsKey(
              unrecognizedAnnotation)) {
        annotationBuilder.put(
            unrecognizedAnnotation, Annotation.NOT_IMPLEMENTED);
      }
    }
    return annotationBuilder.build();
  }
}
