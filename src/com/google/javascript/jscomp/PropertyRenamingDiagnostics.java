/*
 * Copyright 2008 The Closure Compiler Authors.
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

/** Shared diagnotic type related to property renaming. */
final class PropertyRenamingDiagnostics {
  // TODO(user): {1} and {2} are not exactly useful for most people.
  static final DiagnosticType INVALIDATION =
      DiagnosticType.disabled(
          "JSC_INVALIDATION",
          "Property disambiguator skipping all instances of property {0} "
              + "because of type {1} node {2}. {3}");

  static final DiagnosticType INVALIDATION_ON_TYPE =
      DiagnosticType.disabled(
          "JSC_INVALIDATION_TYPE",
          "Property disambiguator skipping instances of property {0} on type {1}. {2}");

  // TODO(tbreisacher): Check this in a separate pass, so that users get the error even if
  // optimizations are not running.
  static final DiagnosticType INVALID_RENAME_FUNCTION =
      DiagnosticType.error("JSC_INVALID_RENAME_FUNCTION", "{0} call is invalid: {1}");

  private PropertyRenamingDiagnostics() {}
}
