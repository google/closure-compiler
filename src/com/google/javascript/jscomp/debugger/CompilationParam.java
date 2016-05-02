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

package com.google.javascript.jscomp.debugger;

import com.google.javascript.jscomp.AnonymousFunctionNamingPolicy;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.VariableRenamingPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * An enum of boolean CGI parameters to the compilation.
 * @author nicksantos@google.com (Nick Santos)
 */
enum CompilationParam {

  ENABLE_ALL_DIAGNOSTIC_GROUPS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      if (value) {
        for (DiagnosticGroup group : new DiagnosticGroups().getRegisteredGroups().values()) {
          options.setWarningLevel(group, CheckLevel.WARNING);
        }
      }
    }
  },

  /**
   * Configures the compiler for use as an IDE backend.
   */
  IDE_MODE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setIdeMode(value);
    }
  },

  /**
   * If true, the input language is ES6. If false, it's ES5.
   */
  LANG_IN_IS_ES6(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setLanguageIn(value ?
          CompilerOptions.LanguageMode.ECMASCRIPT6 :
          CompilerOptions.LanguageMode.ECMASCRIPT5);
    }
  },

  /**
   * If true, the output language is ES5. If false, we skip transpilation.
   */
  TRANSPILE(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setLanguageOut(value ?
          CompilerOptions.LanguageMode.ECMASCRIPT5 :
          CompilerOptions.LanguageMode.NO_TRANSPILE);
    }
  },

  /**
   * If true, skip all passes aside from transpilation-related ones.
   */
  SKIP_NON_TRANSPILATION_PASSES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setSkipNonTranspilationPasses(value);
    }
  },


  //--------------------------------
  // Checks
  //--------------------------------

  CHECK_LINT {
    @Override
    void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
      }
    }
  },

  /** Checks that all symbols are defined */
  CHECK_SYMBOLS(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCheckSymbols(value);
    }
  },

  /** Checks missing return */
  CHECK_MISSING_RETURN {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_RETURN, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /** Checks for suspicious statements that have no effect */
  CHECK_SUSPICIOUS_CODE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCheckSuspiciousCode(value);
    }
  },

  /** Checks types on expressions */
  CHECK_TYPES(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCheckTypes(value);
    }
  },

  /** Checks types on expressions */
  CHECK_TYPES_NEW_INFERENCE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setNewTypeInference(value);
    }
  },

  /** Checks for missing properties */
  MISSING_PROPERTIES(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_PROPERTIES,
          value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Flags a warning if a property is missing the @override annotation, but it
   * overrides a base class property.
   */
  CHECK_REPORT_MISSING_OVERRIDE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setReportMissingOverride(value ?
          CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /** Checks for missing goog.require() calls **/
  CHECK_REQUIRES {
    @Override
    void apply(CompilerOptions options, boolean value) {
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE,
        value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /** Checks for missing goog.provides() calls **/
  CHECK_PROVIDES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_PROVIDE, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks the integrity of references to qualified global names.
   * (e.g. "a.b")
   */
  CHECK_GLOBAL_NAMES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCheckGlobalNamesLevel(value ?
          CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks deprecation.
   */
  CHECK_DEPRECATED {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.DEPRECATED,
          value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks visibility.
   */
  CHECK_VISIBILITY {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.VISIBILITY,
          value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks visibility.
   */
  CHECK_CONSTANTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.CONST,
          value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks es5strict.
   */
  CHECK_ES5_STRICT {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.ES5_STRICT,
          value ? CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /**
   * Checks for certain uses of the {@code this} keyword that are considered
   * unsafe because they are likely to reference the global {@code this}
   * object unintentionally.
   */
  CHECK_GLOBAL_THIS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCheckGlobalThisLevel(value ?
          CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  //--------------------------------
  // Optimizations
  //--------------------------------

  COMPUTE_FUNCTION_SIDE_EFFECTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setComputeFunctionSideEffects(value);
    }
  },

  /** Folds constants (e.g. (2 + 3) to 5) */
  FOLD_CONSTANTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setFoldConstants(value);
    }
  },

  DEAD_ASSIGNMENT_ELIMINATION {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setDeadAssignmentElimination(value);
    }
  },

  /** Inlines constants (symbols that are all CAPS) */
  INLINE_CONSTANTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setInlineConstantVars(value);
    }
  },

  /** Inlines functions */
  INLINE_FUNCTIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setInlineFunctions(value);
      options.setInlineLocalFunctions(value);
    }
  },

  /** Merge two variables together as one. */
  COALESCE_VARIABLE_NAMES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCoalesceVariableNames(value);
    }
  },

  /** Inlines variables */
  INLINE_VARIABLES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setInlineVariables(value);
    }
  },

  /** Flowsenstive Inlines variables */
  FLOW_SENSITIVE_INLINE_VARIABLES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setFlowSensitiveInlineVariables(value);
    }
  },

  INLINE_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setInlineProperties(value);
    }
  },

  /** Removes code associated with unused global names */
  SMART_NAME_REMOVAL {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setSmartNameRemoval(value);
    }
  },

  /** Removes code that will never execute */
  REMOVE_DEAD_CODE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRemoveDeadCode(value);
    }
  },

  /** Checks for unreachable code */
  CHECK_UNREACHABLE_CODE {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, value ?
          CheckLevel.WARNING : CheckLevel.OFF);
    }
  },

  /** Extracts common prototype member declarations */
  EXTRACT_PROTOTYPE_MEMBER_DECLARATIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setExtractPrototypeMemberDeclarations(value);
    }
  },

  /** Removes unused member prototypes */
  REMOVE_UNUSED_PROTOTYPE_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedPrototypeProperties(value);
    }
  },

  /** Removes unused static class prototypes */
  REMOVE_UNUSED_CLASS_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedClassProperties(value);
    }
  },

  /** Tells AnalyzePrototypeProperties it can remove externed props. */
  REMOVE_UNUSED_PROTOTYPE_PROPERTIES_IN_EXTERNS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedPrototypePropertiesInExterns(value);
    }
  },

  /** Removes unused variables */
  REMOVE_UNUSED_VARIABLES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedVars(value);
    }
  },

  /** Collapses multiple variable declarations into one */
  COLLAPSE_VARIABLE_DECLARATIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCollapseVariableDeclarations(value);
    }
  },

  /**
   * Collapses anonymous function expressions into named function
   * declarations
   */
  COLLAPSE_ANONYMOUS_FUNCTIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCollapseAnonymousFunctions(value);
    }
  },

  /**
   * Aliases all string literals to global instances, to avoid creating more
   * objects than necessary (if true, overrides any set of strings passed in
   * to aliasableStrings)
   */
  ALIAS_ALL_STRINGS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setAliasAllStrings(value);
    }
  },

  /** Converts quoted property accesses to dot syntax (a['b'] -> a.b) */
  COVERT_TO_DOTTED_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setConvertToDottedProperties(value);
    }
  },

  //--------------------------------
  // Renaming
  //--------------------------------

  /** Controls label renaming. */
  LABEL_RENAMING {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setLabelRenaming(value);
    }
  },

  /** Generate pseudo names for properties (for debugging purposes) */
  GENERATE_PSEUDO_NAMES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setGeneratePseudoNames(value);
    }
  },

  /** Flattens multi-level property names (e.g. a$b = x) */
  COLLAPSE_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCollapseProperties(value);
    }
  },

  DEVIRTUALIZE_PROTOTYPE_METHODS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setDevirtualizePrototypeMethods(value);
    }
  },

  REWRITE_FUNCTION_EXPRESSIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setRewriteFunctionExpressions(value);    }
  },

  DISAMBIGUATE_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setDisambiguateProperties(value);
    }
  },

  AMBIGUATE_PROPERTIES {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setAmbiguateProperties(value);
    }
  },

  /** Give anonymous functions names for easier debugging */
  NAME_ANONYMOUS_FUNCTIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setAnonymousFunctionNaming(
            AnonymousFunctionNamingPolicy.UNMAPPED);
      }
    }
  },

  /** Give anonymous functions mapped names for easier debugging */
  NAME_ANONYMOUS_FUNCTIONS_MAPPED {
    @Override
    void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setAnonymousFunctionNaming(
            AnonymousFunctionNamingPolicy.MAPPED);
      }
    }
  },

  /** If true, rename all variables */
  VARIABLE_RENAMING {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setVariableRenaming(value ?
          VariableRenamingPolicy.ALL :
          VariableRenamingPolicy.OFF);
    }
  },

  PROPERTY_RENAMING {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setPropertyRenaming(value ?
          PropertyRenamingPolicy.ALL_UNQUOTED :
          PropertyRenamingPolicy.OFF);
    }
  },

  OPTIMIZE_CALLS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setOptimizeCalls(value);
    }
  },

  OPTIMIZE_PARAMETERS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setOptimizeParameters(value);
    }
  },

  OPTIMIZE_RETURNS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setOptimizeReturns(value);
    }
  },

  //--------------------------------
  // Special-purpose alterations
  //--------------------------------

  /** Processes goog.provide() and goog.require() calls */
  CLOSURE_PASS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setClosurePass(value);
    }
  },

  /** Move top level function declarations to the top */
  MOVE_FUNCTION_DECLARATIONS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setMoveFunctionDeclarations(value);
    }
  },

  GENERATE_EXPORTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setGenerateExports(value);
    }
  },

  ALLOW_LOCAL_EXPORTS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setExportLocalPropertyDefinitions(value);
    }
  },

  MARK_NO_SIDE_EFFECT_CALLS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setMarkNoSideEffectCalls(value);
    }
  },

  CROSS_MODULE_CODE_MOTION {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCrossModuleCodeMotion(value);
    }
  },

  CROSS_MODULE_METHOD_MOTION {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setCrossModuleMethodMotion(value);
    }
  },

  SYNTHETIC_BLOCK_MARKER {
    @Override
    void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setSyntheticBlockStartMarker("start");
        options.setSyntheticBlockEndMarker("end");
      } else {
        options.setSyntheticBlockStartMarker(null);
        options.setSyntheticBlockEndMarker(null);
      }
    }
  },

  /** Process @ngInject directive */
  ANGULAR_PASS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setAngularPass(value);
    }
  },

  POLYMER_PASS {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setPolymerPass(value);
    }
  },

  //--------------------------------
  // Output options
  //--------------------------------

  PRESERVE_TYPE_ANNOTATIONS(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setPreserveTypeAnnotations(value);
    }
  },

  /** Ouput in pretty indented format */
  PRETTY_PRINT(true) {
    @Override
    void apply(CompilerOptions options, boolean value) {
      options.setPrettyPrint(value);
    }
  };

  private final boolean defaultValue;

  CompilationParam() {
    this(false);
  }

  CompilationParam(boolean defaultValue) {
    this.defaultValue = defaultValue;
  }

  /** Returns the default value. */
  boolean getDefaultValue() {
    return defaultValue;
  }

  static CompilationParam[] getSortedValues() {
    ArrayList<CompilationParam> values = new ArrayList<>(
        Arrays.asList(CompilationParam.values()));

    Collections.sort(values, new java.util.Comparator<CompilationParam>() {
      @Override
      public int compare(CompilationParam o1, CompilationParam o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });

    return values.toArray(new CompilationParam[0]);
  }

  /** Applies a CGI parameter to the options. */
  abstract void apply(CompilerOptions options, boolean value);
}
