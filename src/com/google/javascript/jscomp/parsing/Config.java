/*
 * Copyright 2009 Google Inc.
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

import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.util.Set;

/**
 * Configuration for the AST factory. Should be shared across AST creation
 * for all files of a compilation process.
 *
*
 */
class Config {

  /**
   * Central registry for type info.
   */
  final JSTypeRegistry registry;

  /**
   * Whether to parse the descriptions of jsdoc comments.
   */
  final boolean parseJsDocDocumentation;

  /**
   * JSDoc annotations that should not be warned about, even if
   * the parser doesn't know what to do with them otherwise.
   */
  final Set<String> annotationWhitelist;

  Config(JSTypeRegistry registry, Set<String> annotationWhitelist,
      boolean parseJsDocDocumentation) {
    this.registry = registry;
    this.annotationWhitelist = annotationWhitelist;
    this.parseJsDocDocumentation = parseJsDocDocumentation;
  }
}
