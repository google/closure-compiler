/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * Static error constants related to the {@link PolymerPass}.
 */
final class PolymerPassErrors {
  // TODO(jlklein): Switch back to an error when everyone is upgraded to Polymer 1.0
  static final DiagnosticType POLYMER_DESCRIPTOR_NOT_VALID = DiagnosticType.warning(
      "JSC_POLYMER_DESCRIPTOR_NOT_VALID",
      "The argument to Polymer() is not an obj lit (perhaps because this is a pre-Polymer-1.0 "
      + "call). Ignoring this call.");

  // Disallow 'const Foo = Polymer(...)' because the code the PolymerPass outputs will reassign
  // Foo which is not allowed for 'const' variables.
  static final DiagnosticType POLYMER_INVALID_DECLARATION = DiagnosticType.error(
      "JSC_POLYMER_INVALID_DECLARATION", "A Polymer() declaration cannot use 'const'.");

  static final DiagnosticType POLYMER_MISSING_IS = DiagnosticType.error("JSC_POLYMER_MISSING_IS",
      "The class descriptor must include an 'is' property.");

  static final DiagnosticType POLYMER_UNEXPECTED_PARAMS = DiagnosticType.error(
      "JSC_POLYMER_UNEXPECTED_PARAMS", "The class definition has too many arguments.");

  static final DiagnosticType POLYMER_MISSING_EXTERNS = DiagnosticType.error(
      "JSC_POLYMER_MISSING_EXTERNS", "Missing Polymer externs.");

  static final DiagnosticType POLYMER_INVALID_PROPERTY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_PROPERTY", "Polymer property has an invalid or missing type.");

  static final DiagnosticType POLYMER_INVALID_BEHAVIOR_ARRAY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_BEHAVIOR_ARRAY", "The behaviors property must be an array literal.");

  static final DiagnosticType POLYMER_UNQUALIFIED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNQUALIFIED_BEHAVIOR",
      "Behaviors must be global, fully qualified names which are declared as object literals or "
      + "array literals of other valid Behaviors.");

  static final DiagnosticType POLYMER_UNANNOTATED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNANNOTATED_BEHAVIOR",
      "Behavior declarations must be annotated with @polymerBehavior.");

  static final DiagnosticType POLYMER_SHORTHAND_NOT_SUPPORTED = DiagnosticType.error(
      "JSC_POLYMER_SHORTHAND_NOT_SUPPORTED",
      "Shorthand assignment in object literal is not allowed in "
      + "Polymer call arguments");

  private PolymerPassErrors() {}
}
