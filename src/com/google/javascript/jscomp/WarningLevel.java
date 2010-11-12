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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;

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
  }

  /**
   * Add the default checking pass to the compilation options.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void addDefaultWarnings(CompilerOptions options) {
    options.checkSuspiciousCode = true;
    // TODO(user): This is deprecated. We should turn on the
    // new one once it is default in command line JSCompiler.
    options.checkUndefinedProperties = CheckLevel.OFF;
    options.checkUnusedPropertiesEarly = false;
    options.checkUnreachableCode = CheckLevel.WARNING;
    options.checkControlStructures = true;
  }

  /**
   * Add all the check pass that are possibly relevant to a non googler.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void addVerboseWarnings(CompilerOptions options) {
    addDefaultWarnings(options);

    // checkSuspiciousCode needs to be enabled for CheckGlobalThis to get run.
    options.checkSuspiciousCode = true;
    options.checkGlobalThisLevel = CheckLevel.WARNING;
    options.checkSymbols = true;

    // checkTypes has the side-effect of asserting that the
    // correct number of arguments are passed to a function.
    // Because the CodingConvention used with the web service does not provide a
    // way for optional arguments to be specified, these warnings may result in
    // false positives.
    options.checkTypes = true;
    options.checkGlobalNamesLevel = CheckLevel.WARNING;
    options.aggressiveVarCheck = CheckLevel.WARNING;
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
  }
}
