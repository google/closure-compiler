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

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for the AST factory. Should be shared across AST creation
 * for all files of a compilation process.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class Config {

  public enum LanguageMode {
    ECMASCRIPT3,
    ECMASCRIPT5,
    ECMASCRIPT5_STRICT,
  }

  /**
   * Whether to parse the descriptions of JsDoc comments.
   */
  final boolean parseJsDocDocumentation;

  /**
   * Whether we're in IDE mode.
   */
  final boolean isIdeMode;

  /**
   * Recognized JSDoc annotations, mapped from their name to their internal
   * representation.
   */
  final Map<String, Annotation> annotationNames;

  /**
   * Recognized names in a {@code @suppress} tag.
   */
  final Set<String> suppressionNames;

  /**
   * Accept ECMAScript5 syntax, such as getter/setter.
   */
  final LanguageMode languageMode;

  /**
   * Accept `const' keyword.
   */
  final boolean acceptConstKeyword;

  /**
   * Annotation names.
   */

  Config(Set<String> annotationWhitelist, Set<String> suppressionNames,
      boolean isIdeMode, LanguageMode languageMode,
      boolean acceptConstKeyword) {
    this.annotationNames = buildAnnotationNames(annotationWhitelist);
    this.parseJsDocDocumentation = isIdeMode;
    this.suppressionNames = suppressionNames;
    this.isIdeMode = isIdeMode;
    this.languageMode = languageMode;
    this.acceptConstKeyword = acceptConstKeyword;
  }

  /**
   * Create the annotation names from the user-specified
   * annotation whitelist.
   */
  private static Map<String, Annotation> buildAnnotationNames(
      Set<String> annotationWhitelist) {
    ImmutableMap.Builder<String, Annotation> annotationBuilder =
        ImmutableMap.builder();
    annotationBuilder.putAll(Annotation.recognizedAnnotations);
    for (String unrecognizedAnnotation : annotationWhitelist) {
      if (!Annotation.recognizedAnnotations.containsKey(
              unrecognizedAnnotation)) {
        annotationBuilder.put(
            unrecognizedAnnotation, Annotation.NOT_IMPLEMENTED);
      }
    }
    return annotationBuilder.build();
  }
}
