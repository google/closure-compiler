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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;

/**
 * Configuration for the AST factory. Should be shared across AST creation for all files of a
 * compilation process.
 */
@Immutable @AutoValue @AutoValue.CopyAnnotations
public abstract class Config {

  /**
   * Level of language strictness required for the input source code.
   */
  public enum StrictMode {
    STRICT, SLOPPY;

    public boolean isStrict() {
      return this == STRICT;
    }
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
    ECMASCRIPT_2018(FeatureSet.ES2018_MODULES),
    ECMASCRIPT_2019(FeatureSet.ES2019_MODULES),
    ECMASCRIPT_2020(FeatureSet.ES2020_MODULES),
    ES_NEXT(FeatureSet.ES_NEXT),
    ES_NEXT_IN(FeatureSet.ES_NEXT_IN),
    UNSUPPORTED(FeatureSet.ES_UNSUPPORTED),
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
        if (mode.featureSet.has(feature)) {
          return mode;
        }
      }
      throw new IllegalStateException("No input language mode supports feature: " + feature);
    }

    /** Returns the lowest {@link LanguageMode} that supports the specified feature set. */
    public static LanguageMode minimumRequiredForSet(FeatureSet featureSet) {
      for (LanguageMode mode : LanguageMode.values()) {
        if (mode.featureSet.contains(featureSet)) {
          return mode;
        }
      }
      throw new IllegalStateException("No input language mode supports feature set: " + featureSet);
    }

    public static LanguageMode latestEcmaScript() {
      return ECMASCRIPT8;
    }
  }

  /**
   * Whether to parse the descriptions of JsDoc comments.
   */
  public enum JsDocParsing {
    TYPES_ONLY,
    INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
    INCLUDE_DESCRIPTIONS_WITH_WHITESPACE,
    INCLUDE_ALL_COMMENTS;

    boolean shouldParseDescriptions() {
      return this != TYPES_ONLY;
    }

    boolean shouldPreserveWhitespace() {
      return this == INCLUDE_DESCRIPTIONS_WITH_WHITESPACE || this == INCLUDE_ALL_COMMENTS;
    }
  }

  /**
   * Whether to keep going after encountering a parse error.
   */
  public enum RunMode {
    STOP_AFTER_ERROR,
    KEEP_GOING,
  }

  /** Language level to accept. */
  public abstract LanguageMode languageMode();

  /** Whether to assume input is strict mode compliant. */
  public abstract StrictMode strictMode();

  /** How to parse the descriptions of JsDoc comments. */
  public abstract JsDocParsing jsDocParsingMode();

  /** Whether to keep going after encountering a parse error. */
  public abstract RunMode runMode();

  /** Recognized JSDoc annotations, mapped from their name to their internal representation. */
  public abstract ImmutableMap<String, Annotation> annotations();

  /** Set of recognized names in a {@code @suppress} tag. */
  public abstract ImmutableSet<String> suppressionNames();

  /** Set of recognized names in a {@code @closurePrimitive} tag. */
  abstract ImmutableSet<String> closurePrimitiveNames();

  /** Whether to parse inline source maps (//# sourceMappingURL=data:...). */
  public abstract boolean parseInlineSourceMaps();

  final ImmutableSet<String> annotationNames() {
    return annotations().keySet();
  }

  public static Builder builder() {
    return new AutoValue_Config.Builder()
        .setLanguageMode(LanguageMode.TYPESCRIPT)
        .setStrictMode(StrictMode.STRICT)
        .setJsDocParsingMode(JsDocParsing.TYPES_ONLY)
        .setRunMode(RunMode.STOP_AFTER_ERROR)
        .setExtraAnnotationNames(ImmutableSet.<String>of())
        .setSuppressionNames(ImmutableSet.<String>of())
        .setClosurePrimitiveNames(ImmutableSet.of())
        .setParseInlineSourceMaps(false);
  }

  /** Builder for a Config. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLanguageMode(LanguageMode mode);

    public abstract Builder setStrictMode(StrictMode mode);

    public abstract Builder setJsDocParsingMode(JsDocParsing mode);

    public abstract Builder setRunMode(RunMode mode);

    public abstract Builder setParseInlineSourceMaps(boolean parseInlineSourceMaps);

    public abstract Builder setSuppressionNames(Iterable<String> names);

    abstract Builder setClosurePrimitiveNames(Iterable<String> names);

    final Builder setExtraAnnotationNames(Iterable<String> names) {
      return setAnnotations(buildAnnotations(names));
    }

    public abstract Config build();

    // The following is intended to be used internally only (but isn't private due to AutoValue).
    public abstract Builder setAnnotations(ImmutableMap<String, Annotation> names);
  }

  /** Create the annotation names from the user-specified annotation allowlist. */
  private static ImmutableMap<String, Annotation> buildAnnotations(Iterable<String> allowlist) {
    ImmutableMap.Builder<String, Annotation> annotationsBuilder = ImmutableMap.builder();
    annotationsBuilder.putAll(Annotation.recognizedAnnotations);
    for (String unrecognizedAnnotation : allowlist) {
      if (!unrecognizedAnnotation.isEmpty()
          && !Annotation.recognizedAnnotations.containsKey(unrecognizedAnnotation)) {
        annotationsBuilder.put(unrecognizedAnnotation, Annotation.NOT_IMPLEMENTED);
      }
    }
    return annotationsBuilder.build();
  }
}
