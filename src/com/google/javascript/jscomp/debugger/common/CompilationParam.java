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

package com.google.javascript.jscomp.debugger.common;

import com.google.javascript.jscomp.AnonymousFunctionNamingPolicy;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.J2clPassMode;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An enum of boolean CGI parameters to the compilation.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public enum CompilationParam {
  ENABLE_ALL_DIAGNOSTIC_GROUPS {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        for (DiagnosticGroup group : new DiagnosticGroups().getRegisteredGroups().values()) {
          options.setWarningLevel(group, CheckLevel.WARNING);
        }
      }
    }

    @Override
    public String getJavaInfo() {
      return "Sets all registered DiagnosticGroups to CheckLevel.WARNING";
    }
  },

  /** Configures the compiler for use as an IDE backend. */
  IDE_MODE {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setIdeMode(value);
    }
  },

  /** If true, the output language is ES5. If false, we skip transpilation. */
  TRANSPILE(true, ParamGroup.TRANSPILATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
      options.setLanguageOut(
          value
              ? CompilerOptions.LanguageMode.ECMASCRIPT5
              : CompilerOptions.LanguageMode.NO_TRANSPILE);
    }

    @Override
    public String getJavaInfo() {
      return "options.setLanguageOut(LanguageMode.ECMASCRIPT5)";
    }
  },

  /** If true, skip all passes aside from transpilation-related ones. */
  SKIP_NON_TRANSPILATION_PASSES(ParamGroup.TRANSPILATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setSkipNonTranspilationPasses(value);
    }
  },

  // --------------------------------
  // Checks
  // --------------------------------

  CHECK_LINT(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
      }
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("LINT_CHECKS");
    }
  },

  /** Checks that all symbols are defined */
  CHECK_SYMBOLS(true, ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCheckSymbols(value);
    }
  },

  /** Checks missing return */
  CHECK_MISSING_RETURN(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_RETURN, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("MISSING_RETURN");
    }
  },

  /** Checks for suspicious statements that have no effect */
  CHECK_SUSPICIOUS_CODE(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCheckSuspiciousCode(value);
    }
  },

  /** Checks types on expressions */
  CHECK_TYPES(true, ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCheckTypes(value);
    }
  },

  /** Checks types on expressions */
  CHECK_TYPES_NEW_INFERENCE(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setNewTypeInference(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setNewTypeInference(true)";
    }
  },

  /** Checks for missing properties */
  MISSING_PROPERTIES(true, ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_PROPERTIES, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("MISSING_PROPERTIES");
    }
  },

  /**
   * Flags a warning if a property is missing the @override annotation, but it overrides a base
   * class property.
   */
  CHECK_REPORT_MISSING_OVERRIDE(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_OVERRIDE, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return "options.setReportMissingOverride(CheckLevel.WARNING)";
    }
  },

  /** Checks for missing goog.require() calls */
  CHECK_REQUIRES(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_REQUIRE, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("MISSING_REQUIRE");
    }
  },

  /** Checks for missing goog.provides() calls * */
  CHECK_PROVIDES(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.MISSING_PROVIDE, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("MISSING_PROVIDE");
    }
  },

  /** Checks the integrity of references to qualified global names. (e.g. "a.b") */
  CHECK_GLOBAL_NAMES(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCheckGlobalNamesLevel(value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return "options.setCheckGlobalNamesLevel(CheckLevel.WARNING)";
    }
  },

  /** Checks deprecation. */
  CHECK_DEPRECATED(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.DEPRECATED, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("DEPRECATED");
    }
  },

  /** Checks visibility. */
  CHECK_VISIBILITY(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.VISIBILITY, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("VISIBILITY");
    }
  },

  /** Checks visibility. */
  CHECK_CONSTANTS(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(DiagnosticGroups.CONST, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("CONST");
    }
  },

  /** Checks es5strict. */
  CHECK_ES5_STRICT(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.ES5_STRICT, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("ES5_STRICT");
    }
  },

  /**
   * Checks for certain uses of the {@code this} keyword that are considered unsafe because they are
   * likely to reference the global {@code this} object unintentionally.
   */
  CHECK_GLOBAL_THIS(ParamGroup.ERROR_CHECKING) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCheckGlobalThisLevel(value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return "options.setCheckGlobalThisLevel(CheckLevel.WARNING)";
    }
  },

  // --------------------------------
  // Optimizations
  // --------------------------------

  COMPUTE_FUNCTION_SIDE_EFFECTS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setComputeFunctionSideEffects(value);
    }
  },

  /** Folds constants (e.g. (2 + 3) to 5) */
  FOLD_CONSTANTS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setFoldConstants(value);
    }
  },

  DEAD_ASSIGNMENT_ELIMINATION(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setDeadAssignmentElimination(value);
    }
  },

  /** Inlines constants (symbols that are all CAPS) */
  INLINE_CONSTANTS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setInlineConstantVars(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setInlineConstantVars(true)";
    }
  },

  /** Inlines functions */
  INLINE_FUNCTIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setInlineFunctions(value);
      options.setInlineLocalFunctions(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setInlineFunctions(true) + options.setInlineLocalFunctions(true)";
    }
  },

  /** Merge two variables together as one. */
  COALESCE_VARIABLE_NAMES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCoalesceVariableNames(value);
    }
  },

  /** Inlines variables */
  INLINE_VARIABLES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setInlineVariables(value);
    }
  },

  /** Flowsenstive Inlines variables */
  FLOW_SENSITIVE_INLINE_VARIABLES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setFlowSensitiveInlineVariables(value);
    }
  },

  INLINE_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setInlineProperties(value);
    }
  },

  /** Removes code associated with unused global names */
  SMART_NAME_REMOVAL(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setSmartNameRemoval(value);
    }
  },

  /** Removes code that will never execute */
  REMOVE_DEAD_CODE(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRemoveDeadCode(value);
    }
  },

  /** Checks for unreachable code */
  CHECK_UNREACHABLE_CODE(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setWarningLevel(
          DiagnosticGroups.CHECK_USELESS_CODE, value ? CheckLevel.WARNING : CheckLevel.OFF);
    }

    @Override
    public String getJavaInfo() {
      return diagGroupWarningInfo("CHECK_USELESS_CODE");
    }
  },

  /** Extracts common prototype member declarations */
  EXTRACT_PROTOTYPE_MEMBER_DECLARATIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setExtractPrototypeMemberDeclarations(value);
    }
  },

  /** Removes abstract methods */
  REMOVE_ABSTRACT_METHODS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRemoveAbstractMethods(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setRemoveAbstractMethods(true)";
    }
  },

  /** Removes unused member prototypes */
  REMOVE_UNUSED_PROTOTYPE_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedPrototypeProperties(value);
    }
  },

  /** Removes unused static class prototypes */
  REMOVE_UNUSED_CLASS_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedClassProperties(value);
    }
  },

  /** Tells AnalyzePrototypeProperties it can remove externed props. */
  REMOVE_UNUSED_PROTOTYPE_PROPERTIES_IN_EXTERNS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRemoveUnusedPrototypePropertiesInExterns(value);
    }
  },

  /** Removes unused variables */
  REMOVE_UNUSED_VARIABLES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setRemoveUnusedVariables(Reach.ALL);
      } else {
        options.setRemoveUnusedVariables(Reach.NONE);
      }
    }
  },

  /** Collapses multiple variable declarations into one */
  COLLAPSE_VARIABLE_DECLARATIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCollapseVariableDeclarations(value);
    }
  },

  /** Collapses anonymous function expressions into named function declarations */
  COLLAPSE_ANONYMOUS_FUNCTIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCollapseAnonymousFunctions(value);
    }
  },

  /**
   * Aliases all string literals to global instances, to avoid creating more objects than necessary
   * (if true, overrides any set of strings passed in to aliasableStrings)
   */
  ALIAS_ALL_STRINGS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setAliasAllStrings(value);
    }
  },

  /** Converts quoted property accesses to dot syntax (a['b'] -> a.b) */
  CONVERT_TO_DOTTED_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setConvertToDottedProperties(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setConvertToDottedProperties(true)";
    }
  },

  /** Enables a number of peephole optimizations */
  USE_TYPES_FOR_LOCAL_OPTIMIZATION(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setUseTypesForLocalOptimization(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setUseTypesForLocalOptimization(true)";
    }
  },

  // --------------------------------
  // Renaming
  // --------------------------------

  /** Controls label renaming. */
  LABEL_RENAMING(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setLabelRenaming(value);
    }
  },

  /** Generate pseudo names for properties (for debugging purposes) */
  GENERATE_PSEUDO_NAMES {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setGeneratePseudoNames(value);
    }
  },

  /** Flattens multi-level property names (e.g. a$b = x) */
  COLLAPSE_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCollapseProperties(value);
    }
  },

  DEVIRTUALIZE_PROTOTYPE_METHODS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setDevirtualizePrototypeMethods(value);
    }
  },

  REWRITE_FUNCTION_EXPRESSIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setRewriteFunctionExpressions(value);
    }
  },

  DISAMBIGUATE_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setDisambiguateProperties(value);
    }
  },

  AMBIGUATE_PROPERTIES(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setAmbiguateProperties(value);
    }
  },

  /** Give anonymous functions names for easier debugging */
  NAME_ANONYMOUS_FUNCTIONS {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
      }
    }

    @Override
    public String getJavaInfo() {
      return "options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED)";
    }
  },

  /** Give anonymous functions mapped names for easier debugging */
  NAME_ANONYMOUS_FUNCTIONS_MAPPED {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED);
      }
    }

    @Override
    public String getJavaInfo() {
      return "options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED)";
    }
  },

  /** If true, rename all variables */
  VARIABLE_RENAMING(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setVariableRenaming(value ? VariableRenamingPolicy.ALL : VariableRenamingPolicy.OFF);
    }

    @Override
    public String getJavaInfo() {
      return "options.setVariableRenaming(VariableRenamingPolicy.ALL)";
    }
  },

  PROPERTY_RENAMING(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setPropertyRenaming(
          value ? PropertyRenamingPolicy.ALL_UNQUOTED : PropertyRenamingPolicy.OFF);
    }

    @Override
    public String getJavaInfo() {
      return "options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED)";
    }
  },

  OPTIMIZE_CALLS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setOptimizeCalls(value);
    }
  },

  OPTIMIZE_PARAMETERS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setOptimizeParameters(value);
    }
  },

  OPTIMIZE_RETURNS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setOptimizeReturns(value);
    }
  },

  // --------------------------------
  // Special-purpose alterations
  // --------------------------------

  /** Processes goog.provide() and goog.require() calls */
  CLOSURE_PASS(true) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setClosurePass(value);
    }
  },

  /** Move top level function declarations to the top */
  MOVE_FUNCTION_DECLARATIONS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setMoveFunctionDeclarations(value);
    }
  },

  GENERATE_EXPORTS {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setGenerateExports(value);
    }
  },

  ALLOW_LOCAL_EXPORTS {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setExportLocalPropertyDefinitions(value);
    }

    @Override
    public String getJavaInfo() {
      return "options.setExportLocalPropertyDefinitions(true)";
    }
  },

  MARK_NO_SIDE_EFFECT_CALLS(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setMarkNoSideEffectCalls(value);
    }
  },

  CROSS_MODULE_CODE_MOTION(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCrossModuleCodeMotion(value);
    }
  },

  CROSS_MODULE_METHOD_MOTION(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setCrossModuleMethodMotion(value);
    }
  },

  SYNTHETIC_BLOCK_MARKER(ParamGroup.TYPE_CHECKING_OPTIMIZATION) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      if (value) {
        options.setSyntheticBlockStartMarker("start");
        options.setSyntheticBlockEndMarker("end");
      } else {
        options.setSyntheticBlockStartMarker(null);
        options.setSyntheticBlockEndMarker(null);
      }
    }

    @Override
    public String getJavaInfo() {
      return "options.setSyntheticBlockStartMarker(\"start\") + "
          + "options.setSyntheticBlockEndMarker(\"end\")";
    }
  },

  /** Process @ngInject directive */
  ANGULAR_PASS(ParamGroup.SPECIAL_PASSES) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setAngularPass(value);
    }
  },

  POLYMER_PASS(ParamGroup.SPECIAL_PASSES) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setPolymerVersion(value ? 1 : null);
    }
  },

  CHROME_PASS(ParamGroup.SPECIAL_PASSES) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setChromePass(value);
    }
  },

  J2CL_PASS(ParamGroup.SPECIAL_PASSES) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setJ2clPass(value ? J2clPassMode.ON : J2clPassMode.OFF);
    }
  },

  // --------------------------------
  // Output options
  // --------------------------------

  PRESERVE_TYPE_ANNOTATIONS(true) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setPreserveTypeAnnotations(value);
    }
  },

  /** Ouput in pretty indented format */
  PRETTY_PRINT(true) {
    @Override
    public void apply(CompilerOptions options, boolean value) {
      options.setPrettyPrint(value);
    }
  };

  /** Groups parameters into associated types */
  public enum ParamGroup {
    ERROR_CHECKING("Lint and Error Checking"),
    TRANSPILATION("Transpilation"),
    TYPE_CHECKING_OPTIMIZATION("Type Checking/Optimization"),
    SPECIAL_PASSES("Specialized Passes"),
    MISC("Other");

    public final String name;

    ParamGroup(String name) {
      this.name = name;
    }
  }

  private final boolean defaultValue; // default is false.
  private final ParamGroup group;

  CompilationParam() {
    this(false, ParamGroup.MISC);
  }

  CompilationParam(boolean defaultValue) {
    this(defaultValue, ParamGroup.MISC);
  }

  CompilationParam(ParamGroup group) {
    this(false, group);
  }

  CompilationParam(boolean defaultValue, ParamGroup group) {
    this.defaultValue = defaultValue;
    this.group = group;
  }

  /** Returns the default value. */
  public boolean getDefaultValue() {
    return defaultValue;
  }

  /**
   * Optionally returns a hint about the Java API methods/options this param affects, currently
   * implemented for all params where the enum name doesn't directly match to a camel case method
   * CompilerOptions.setSomethingOrOther(true), such as for diagnostic groups or where the option
   * method name has changed. To assist external developers who are trying to correlate their own
   * Java API driven compilation options to the debugger's options when creating reproducible issue
   * reports.
   */
  public String getJavaInfo() {
    return null;
  }

  private static String diagGroupWarningInfo(String diagGroupsMember) {
    return "options.setWarningLevel(DiagnosticGroups." + diagGroupsMember + ", CheckLevel.WARNING)";
  }

  static CompilationParam[] getSortedValues() {
    ArrayList<CompilationParam> values = new ArrayList<>(Arrays.asList(CompilationParam.values()));

    Collections.sort(
        values,
        new java.util.Comparator<CompilationParam>() {
          @Override
          public int compare(CompilationParam o1, CompilationParam o2) {
            return o1.toString().compareTo(o2.toString());
          }
        });

    return values.toArray(new CompilationParam[0]);
  }

  public static Map<ParamGroup, CompilationParam[]> getGroupedSortedValues() {
    Map<ParamGroup, CompilationParam[]> compilationParamsByGroup = new EnumMap<>(ParamGroup.class);

    for (ParamGroup group : ParamGroup.values()) {
      List<CompilationParam> groupParams = new ArrayList<>();
      for (CompilationParam param : CompilationParam.values()) {
        if (param.group == group) {
          groupParams.add(param);
        }
      }
      compilationParamsByGroup.put(group, groupParams.toArray(new CompilationParam[0]));
    }

    return compilationParamsByGroup;
  }

  /** Applies a CGI parameter to the options. */
  public abstract void apply(CompilerOptions options, boolean value);
}
