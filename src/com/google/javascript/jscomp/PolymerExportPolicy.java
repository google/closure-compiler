/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

/**
 * How to handle exports/externs for Polymer properties and methods.
 */
public enum PolymerExportPolicy {
  /**
   * If --polymer_version=1, add all Polymer properties (but not methods) to the externs.
   * If --polymer_version=2, add readOnly and reflectToAttribute Polymer properties (but not
   * methods) to the externs.
   *
   * This policy is is not generally safe for use with renaming and unused code optimizations,
   * unless additional steps are taken (e.g. manual exports, goog.reflect.objectProperty, the
   * PolymerRenamer post-processor).
   */
  LEGACY,

  /**
   * Add all Polymer properties and methods to the externs.
   *
   * Since any of these definitions could be referenced by string in HTML templates, observer
   * definitions, or computed property definitions, this is a blunt but safe way to allow Polymer
   * code to be used with renaming and unused code optimizations enabled.
   */
  EXPORT_ALL
}
