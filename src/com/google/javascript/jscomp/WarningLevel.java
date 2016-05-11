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

package com.google.javascript.jscomp;

/**
 * Convert the warnings level to an Options object.
 *
 */
public enum WarningLevel {
  QUIET,

  DEFAULT,

  VERBOSE;

  public void setOptionsForWarningLevel(CompilerOptions options) {
    switch (this) {
      case QUIET:
        silenceAllWarnings(options);
        break;
      case DEFAULT:
        addDefaultWarnings(options);
        break;
      case VERBOSE:
        addVerboseWarnings(options);
        break;
      default:
        throw new RuntimeException("Unknown warning level.");
    }
  }

  /**
   * Silence all non-essential warnings.
   */
  private static void silenceAllWarnings(CompilerOptions options) {
    // Just use a ShowByPath warnings guard, so that we don't have
    // to maintain a separate class of warnings guards for silencing warnings.
    options.addWarningsGuard(
        new ShowByPathWarningsGuard(
            "the_longest_path_that_cannot_be_expressed_as_a_string"));

    // Allow passes that aren't going to report anything to be skipped.

    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.OFF);
    options.setCheckMissingGetCssNameLevel(CheckLevel.OFF);
    options.setCheckTypes(false);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.MISSING_RETURN, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.CONST, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.OFF);
    options.setCheckGlobalNamesLevel(CheckLevel.OFF);
    options.setCheckSuspiciousCode(false);
    options.setCheckGlobalThisLevel(CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);

    // Allows annotations that are not standard.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.OFF);
  }

  /**
   * Add the default checking pass to the compilation options.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void addDefaultWarnings(CompilerOptions options) {
    options.setCheckSuspiciousCode(true);

    // Allows annotations that are not standard.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.OFF);
  }

  /**
   * Add all the check pass that are possibly relevant to a non-googler.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void addVerboseWarnings(CompilerOptions options) {
    addDefaultWarnings(options);

    // checkSuspiciousCode needs to be enabled for CheckGlobalThis to get run.
    options.setCheckSuspiciousCode(true);
    options.setCheckGlobalThisLevel(CheckLevel.WARNING);
    options.setCheckSymbols(true);

    // checkTypes has the side-effect of asserting that the
    // correct number of arguments are passed to a function.
    // Because the CodingConvention used with the web service does not provide a
    // way for optional arguments to be specified, these warnings may result in
    // false positives.
    options.setCheckTypes(true);
    options.setCheckGlobalNamesLevel(CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.DEPRECATED, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.VISIBILITY, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.CONST, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.CHECK_REGEXP, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.STRICT_MODULE_DEP_CHECK, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_RETURN, CheckLevel.WARNING);

    // Kindly tell the user that they have JsDocs that we don't understand.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.WARNING);

    // Transitional.
    options.enforceAccessControlCodingConventions = true;
  }
}
