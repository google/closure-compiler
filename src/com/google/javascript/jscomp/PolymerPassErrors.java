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
  static final DiagnosticType POLYMER_DESCRIPTOR_NOT_VALID =
      DiagnosticType.warning(
          "JSC_POLYMER_DESCRIPTOR_NOT_VALID",
          "The argument to Polymer() is not an obj lit or the Polymer 2 class does not have a"
              + " static getter named 'config'. Ignoring this definition.");

  // Disallow 'const Foo = Polymer(...)' because the code the PolymerPass outputs will reassign
  // Foo which is not allowed for 'const' variables.
  static final DiagnosticType POLYMER_INVALID_DECLARATION = DiagnosticType.error(
      "JSC_POLYMER_INVALID_DECLARATION", "A Polymer() declaration cannot use ''const''.");

  static final DiagnosticType POLYMER_INVALID_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_INVALID_BEHAVIOR", "A Polymer behavior may not include an ''is'' property.");

  static final DiagnosticType POLYMER_MISSING_IS = DiagnosticType.error("JSC_POLYMER_MISSING_IS",
      "The class descriptor must include an ''is'' property.");

  static final DiagnosticType POLYMER_UNEXPECTED_PARAMS = DiagnosticType.error(
      "JSC_POLYMER_UNEXPECTED_PARAMS", "The class definition has too many arguments.");

  static final DiagnosticType POLYMER_MISSING_EXTERNS = DiagnosticType.error(
      "JSC_POLYMER_MISSING_EXTERNS", "Missing Polymer externs.");

  static final DiagnosticType POLYMER_INVALID_PROPERTY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_PROPERTY", "Polymer property has an invalid or missing type.");

  static final DiagnosticType POLYMER_INVALID_EXTENDS = DiagnosticType.error(
      "JSC_POLYMER_INVALID_EXTENDS",
      "Cannot extend HTML element ''{0}''. The element is probably either misspelled,"
          + " or needs to be added to the list of known elements.");

  static final DiagnosticType POLYMER_INVALID_BEHAVIOR_ARRAY = DiagnosticType.error(
      "JSC_POLYMER_INVALID_BEHAVIOR_ARRAY", "The behaviors property must be an array literal.");

  static final DiagnosticType POLYMER_UNQUALIFIED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNQUALIFIED_BEHAVIOR",
      "Behaviors must be global names or qualified names that are declared as object literals or "
      + "array literals of other valid Behaviors.");

  static final DiagnosticType POLYMER_UNANNOTATED_BEHAVIOR = DiagnosticType.error(
      "JSC_POLYMER_UNANNOTATED_BEHAVIOR",
      "Behavior declarations must be annotated with @polymerBehavior.");

  static final DiagnosticType POLYMER_CLASS_PROPERTIES_INVALID =
      DiagnosticType.error(
          "JSC_POLYMER_CLASS_PROPERTIES_INVALID",
          "The Polymer element class 'properties' getter does not return an object literal. "
              + "Ignoring this definition.");

  static final DiagnosticType POLYMER_CLASS_PROPERTIES_NOT_STATIC =
      DiagnosticType.error(
          "JSC_POLYMER_CLASS_PROPERTIES_NOT_STATIC",
          "The Polymer element class 'properties' getter is not declared static. "
              + "Ignoring this definition.");

  static final DiagnosticType POLYMER_CLASS_UNNAMED =
      DiagnosticType.warning(
          "JSC_POLYMER2_UNNAMED",
          "Unable to locate a valid name for the Polymer element class."
              + "Ignoring this definition.");

  static final DiagnosticType POLYMER_MISPLACED_PROPERTY_JSDOC =
      DiagnosticType.warning(
          "JSC_POLYMER_MISPLACED_PROPERTY_JSDOC",
          "When a Polymer property is declared in the constructor, its JSDoc "
              + "should only be in the constructor, not on the Polymer properties configuration.");

  private PolymerPassErrors() {}
}
