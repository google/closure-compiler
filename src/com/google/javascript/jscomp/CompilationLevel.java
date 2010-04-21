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

package com.google.javascript.jscomp;

/**
 * A CompilationLevel represents the level of optimization that should be
 * applied when compiling JavaScript code.
 *
 * @author bolinfest@google.com (Michael Bolin)
 */
public enum CompilationLevel {

  /**
   * WHITESPACE_ONLY removes comments and extra whitespace in the input JS.
   */
  WHITESPACE_ONLY,

  /**
   * SIMPLE_OPTIMIZATIONS performs transformations to the input JS that do not
   * require any changes to JS that depend on the input JS. For example,
   * function arguments are renamed (which should not matter to code that
   * depends on the input JS), but functions themselves are not renamed (which
   * would otherwise require external code to change to use the renamed function
   * names).
   */
  SIMPLE_OPTIMIZATIONS,

  /**
   * ADVANCED_OPTIMIZATIONS aggressively reduces code size by renaming function
   * names and variables, removing code which is never called, etc.
   */
  ADVANCED_OPTIMIZATIONS,
  ;

  private CompilationLevel() {}

  public void setOptionsForCompilationLevel(CompilerOptions options) {
    switch (this) {
      case WHITESPACE_ONLY:
        applyBasicCompilationOptions(options);
        break;
      case SIMPLE_OPTIMIZATIONS:
        applySafeCompilationOptions(options);
        break;
      case ADVANCED_OPTIMIZATIONS:
        applyFullCompilationOptions(options);
        break;
      default:
        throw new RuntimeException("Unknown compilation level.");
    }
  }

  public void setDebugOptionsForCompilationLevel(CompilerOptions options) {
    options.anonymousFunctionNaming = AnonymousFunctionNamingPolicy.UNMAPPED;
    options.generatePseudoNames = true;
  }

  /**
   * Gets options that only strip whitespace and comments.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applyBasicCompilationOptions(CompilerOptions options) {
    options.skipAllCompilerPasses();
    options.checkGlobalThisLevel = CheckLevel.OFF;

    // Allows annotations that are not standard.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.OFF);
  }

  /**
   * Add options that are safe. Safe means options that won't break the
   * JavaScript code even if no symbols are exported and no coding convention
   * is used.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applySafeCompilationOptions(CompilerOptions options) {
    // Does not call applyBasicCompilationOptions(options) because the call to
    // skipAllCompilerPasses() cannot be easily undone.
    options.closurePass = true;
    options.variableRenaming = VariableRenamingPolicy.LOCAL;
    options.inlineLocalVariables = true;
    options.inlineLocalFunctions = true;
    options.inlineAnonymousFunctionExpressions = true;
    options.decomposeExpressions = true;
    options.checkGlobalThisLevel = CheckLevel.OFF;
    options.foldConstants = true;
    options.removeConstantExpressions = true;
    options.coalesceVariableNames = true;
    options.deadAssignmentElimination = true;
    options.collapseVariableDeclarations = true;
    options.convertToDottedProperties = true;
    options.labelRenaming = true;
    options.removeDeadCode = true;
    options.optimizeArgumentsArray = true;
    options.removeUnusedVars = true;
    options.removeUnusedVarsInGlobalScope = false;

    // Allows annotations that are not standard.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.OFF);
  }

  /**
   * Add the options that will work only if the user exported all the symbols
   * correctly.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applyFullCompilationOptions(CompilerOptions options) {
    // Do not call applySafeCompilationOptions(options) because the call can
    // create possible conflicts between multiple diagnostic groups.

    // All the safe optimizations.
    options.closurePass = true;
    options.checkGlobalThisLevel = CheckLevel.OFF;
    options.foldConstants = true;
    options.removeConstantExpressions = true;
    options.coalesceVariableNames = true;
    options.deadAssignmentElimination = true;
    options.extractPrototypeMemberDeclarations = true;
    options.collapseVariableDeclarations = true;
    options.convertToDottedProperties = true;
    options.rewriteFunctionExpressions = true;
    options.labelRenaming = true;
    options.removeDeadCode = true;
    options.optimizeArgumentsArray = true;

    // All the advance optimizations.
    options.reserveRawExports = true;
    options.variableRenaming = VariableRenamingPolicy.ALL;
    options.propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
    options.removeUnusedPrototypeProperties = true;
    options.removeUnusedPrototypePropertiesInExterns = true;
    options.collapseAnonymousFunctions = true;
    options.collapseProperties = true;
    options.rewriteFunctionExpressions = true;
    options.devirtualizePrototypeMethods = true;
    options.smartNameRemoval = true;
    options.inlineConstantVars = true;
    options.inlineFunctions = true;
    options.inlineLocalFunctions = true;
    options.inlineAnonymousFunctionExpressions = true;
    options.decomposeExpressions = true;
    options.inlineGetters = true;
    options.inlineVariables = true;
    options.removeConstantExpressions = true;
    options.computeFunctionSideEffects = true;
    
    // Remove unused vars also removes unused functions.
    options.removeUnusedVars = true;
    options.removeUnusedVarsInGlobalScope = true;

    // Move code around based on the defined modules.
    options.crossModuleCodeMotion = true;
    options.crossModuleMethodMotion = true;
    
    // Kindly tell the user that they have JsDocs that we don't understand.
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
        CheckLevel.WARNING);
  }
}
