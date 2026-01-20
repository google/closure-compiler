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

import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import org.jspecify.annotations.Nullable;

/**
 * A CompilationLevel represents the level of optimization that should be
 * applied when compiling JavaScript code.
 */
public enum CompilationLevel {
  /** BUNDLE Simply orders and concatenates files to the output. */
  BUNDLE,

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

  /**
   * TRANSPILE_ONLY only transpiles the code down to the requested `--language_out level`, including
   * the addition of polyfills. This mode will fully parse the JS files it is running, so that it
   * can find syntax that needs to be transpiled down and discover required polyfills. This mode
   * will not perform any optimizations.
   */
  TRANSPILE_ONLY,
  ;

  public static @Nullable CompilationLevel fromString(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "BUNDLE" -> CompilationLevel.BUNDLE;
      case "WHITESPACE_ONLY", "WHITESPACE" -> CompilationLevel.WHITESPACE_ONLY;
      case "TRANSPILE_ONLY" -> CompilationLevel.TRANSPILE_ONLY;
      case "SIMPLE_OPTIMIZATIONS", "SIMPLE" -> CompilationLevel.SIMPLE_OPTIMIZATIONS;
      case "ADVANCED_OPTIMIZATIONS", "ADVANCED" -> CompilationLevel.ADVANCED_OPTIMIZATIONS;
      default -> null;
    };
  }

  private CompilationLevel() {}

  public void setOptionsForCompilationLevel(CompilerOptions options) {
    switch (this) {
      case BUNDLE -> {}
      case WHITESPACE_ONLY -> applyBasicCompilationOptions(options);
      case SIMPLE_OPTIMIZATIONS -> applySafeCompilationOptions(options);
      case TRANSPILE_ONLY -> applyTranspileOnlyOptions(options);
      case ADVANCED_OPTIMIZATIONS -> applyFullCompilationOptions(options);
    }
  }

  public void setDebugOptionsForCompilationLevel(CompilerOptions options) {
    options.setGeneratePseudoNames(true);
    options.setRemoveClosureAsserts(false);
    options.setRemoveJ2clAsserts(true);
  }

  /**
   * Gets options that only strip whitespace and comments.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applyBasicCompilationOptions(CompilerOptions options) {
    options.skipAllCompilerPasses();
  }

  /**
   * Gets options that only transpile the code, including polyfills.
   *
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applyTranspileOnlyOptions(CompilerOptions options) {
    // ReplaceIdGenerators is on by default, but should not run in TRANSPILE_ONLY mode.
    options.setReplaceIdGenerators(false);

    options.setClosurePass(true);
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    options.setInlineVariables(Reach.NONE);
    options.setInlineFunctions(Reach.NONE);
    options.setFoldConstants(false);
    options.setCoalesceVariableNames(false);
    options.setDeadAssignmentElimination(false);
    options.setCollapseVariableDeclarations(false);
    options.setConvertToDottedProperties(false);
    options.setLabelRenaming(false);
    options.setRemoveUnusedVariables(Reach.NONE);
    options.setCollapseObjectLiterals(false);
    /*
     * Turn off protecting side-effect free nodes by making them parameters to a extern function
     * call.
     */
    options.setProtectHiddenSideEffects(false);
  }

  /**
   * Add options that are safe. Safe means options that won't break the
   * JavaScript code even if no symbols are exported and no coding convention
   * is used.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applySafeCompilationOptions(CompilerOptions options) {
    // TODO(tjgq): Remove this.
    options.setDependencyOptions(DependencyOptions.sortOnly());

    // ReplaceIdGenerators is on by default, but should not run in simple mode.
    options.setReplaceIdGenerators(false);

    // Does not call applyBasicCompilationOptions(options) because the call to
    // skipAllCompilerPasses() cannot be easily undone.
    options.setClosurePass(true);
    options.setRenamingPolicy(VariableRenamingPolicy.LOCAL, PropertyRenamingPolicy.OFF);
    options.setInlineVariables(Reach.LOCAL_ONLY);
    options.setInlineFunctions(Reach.LOCAL_ONLY);
    options.setAssumeClosuresOnlyCaptureReferences(false);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.OFF);
    options.setFoldConstants(true);
    options.setCoalesceVariableNames(true);
    options.setDeadAssignmentElimination(true);
    options.setDeadPropertyAssignmentElimination(false);
    options.setCollapseVariableDeclarations(true);
    options.setConvertToDottedProperties(true);
    options.setLabelRenaming(true);
    options.setRemoveUnusedVariables(Reach.LOCAL_ONLY);
    options.setCollapseObjectLiterals(true);
    options.setProtectHiddenSideEffects(true);
  }

  /**
   * Add the options that will work only if the user exported all the symbols
   * correctly.
   * @param options The CompilerOptions object to set the options on.
   */
  private static void applyFullCompilationOptions(CompilerOptions options) {
    // TODO(tjgq): Remove this.
    options.setDependencyOptions(DependencyOptions.sortOnly());

    // Do not call applySafeCompilationOptions(options) because the call can
    // create possible conflicts between multiple diagnostic groups.

    options.setCheckSymbols(true);
    options.setCheckTypes(true);

    // All the safe optimizations.
    options.setClosurePass(true);
    options.setFoldConstants(true);
    options.setCoalesceVariableNames(true);
    options.setDeadAssignmentElimination(true);
    options.setExtractPrototypeMemberDeclarations(true);
    options.setCollapseVariableDeclarations(true);
    options.setConvertToDottedProperties(true);
    options.setLabelRenaming(true);
    options.setCollapseObjectLiterals(true);
    options.setProtectHiddenSideEffects(true);

    // All the advanced optimizations.
    options.setRemoveClosureAsserts(true);
    options.setRemoveAbstractMethods(true);
    options.setReserveRawExports(true);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setRemoveUnusedClassProperties(true);
    options.setCollapseAnonymousFunctions(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.WARNING);
    options.setRewriteFunctionExpressions(false);
    options.setSmartNameRemoval(true);
    options.setInlineConstantVars(true);
    options.setInlineFunctions(Reach.ALL);
    options.setAssumeClosuresOnlyCaptureReferences(false);
    options.setInlineVariables(Reach.ALL);
    options.setComputeFunctionSideEffects(true);
    options.setAssumeStrictThis(true);

    // Remove unused vars also removes unused functions.
    options.setRemoveUnusedVariables(Reach.ALL);

    // Move code around based on the defined modules.
    options.setCrossChunkCodeMotion(true);
    options.setCrossChunkMethodMotion(true);

    // Call optimizations
    options.setDevirtualizeMethods(true);
    options.setOptimizeCalls(true);
    options.setOptimizeESClassConstructors(true);
  }

  /**
   * Enable additional optimizations that use type information. Only has
   * an effect for ADVANCED_OPTIMIZATIONS; this is a no-op for other modes.
   * @param options The CompilerOptions object to set the options on.
   */
  public void setTypeBasedOptimizationOptions(CompilerOptions options) {
    switch (this) {
      case ADVANCED_OPTIMIZATIONS -> {
        options.setDisambiguateProperties(true);
        options.setAmbiguateProperties(true);
        options.setInlineProperties(true);
        options.setUseTypesForLocalOptimization(true);
      }
      case SIMPLE_OPTIMIZATIONS, WHITESPACE_ONLY, BUNDLE, TRANSPILE_ONLY -> {}
    }
  }

  /**
   * Enable additional optimizations that operate on global declarations. Advanced mode does
   * this by default, but this isn't valid in simple mode in the general case and should only
   * be enabled when code is self contained (such as when it is enclosed by a function wrapper.
   *
   * @param options The CompilerOptions object to set the options on.
   */
  public void setWrappedOutputOptimizations(CompilerOptions options) {
    // Global variables and properties names can't conflict.
    options.setReserveRawExports(false);
    switch (this) {
      case SIMPLE_OPTIMIZATIONS -> {
        // Enable global variable optimizations (but not property optimizations)
        options.setVariableRenaming(VariableRenamingPolicy.ALL);
        options.setCollapsePropertiesLevel(PropertyCollapseLevel.MODULE_EXPORT);
        options.setCollapseAnonymousFunctions(true);
        options.setInlineConstantVars(true);
        options.setInlineFunctions(Reach.ALL);
        options.setInlineVariables(Reach.ALL);
        options.setRemoveUnusedVariables(Reach.ALL);
      }
      case ADVANCED_OPTIMIZATIONS, WHITESPACE_ONLY, BUNDLE, TRANSPILE_ONLY -> {}
    }
  }
}
