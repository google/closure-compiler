/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * If the name of a pass is used in more than one place in the source, it's good to create a
 * symbolic name here.
 */
public final class PassNames {
  public static final String AFTER_MAIN_OPTIMIZATIONS = "afterMainOptimizations";
  public static final String AFTER_STANDARD_CHECKS = "afterStandardChecks";
  public static final String AMBIGUATE_PROPERTIES = "ambiguateProperties";
  public static final String ANALYZER_CHECKS = "analyzerChecks";
  public static final String ANALYZER_CHECKS_INTERNAL = "analyzerChecksInternal";
  public static final String ANGULAR_PASS = "angularPass";
  public static final String BEFORE_STANDARD_OPTIMIZATIONS = "beforeStandardOptimizations";
  public static final String BEFORE_MAIN_OPTIMIZATIONS = "beforeMainOptimizations";
  public static final String BEFORE_TYPE_CHECKING = "beforeTypeChecking";
  public static final String CHAIN_CALLS = "chainCalls";
  public static final String CHECK_CONST_PARAMS = "checkConstParams";
  public static final String CHECK_REG_EXP = "checkRegExp";
  public static final String CHECK_TYPES = "checkTypes";
  public static final String CHECK_VARIABLE_REFERENCES = "checkVariableReferences";
  public static final String CHECK_VARS = "checkVars";
  public static final String CLOSURE_REWRITE_CLASS = "closureRewriteClass";
  public static final String COALESCE_VARIABLE_NAMES = "coalesceVariableNames";
  public static final String COLLAPSE_ANONYMOUS_FUNCTIONS = "collapseAnonymousFunctions";
  public static final String COLLAPSE_OBJECT_LITERALS = "collapseObjectLiterals";
  public static final String COLLAPSE_PROPERTIES = "collapseProperties";
  public static final String COLLAPSE_VARIABLE_DECLARATIONS = "collapseVariableDeclarations";
  public static final String CONVERT_TO_DOTTED_PROPERTIES = "convertToDottedProperties";
  public static final String CROSS_MODULE_CODE_MOTION = "crossModuleCodeMotion";
  public static final String CROSS_MODULE_METHOD_MOTION = "crossModuleMethodMotion";
  public static final String DEAD_ASSIGNMENT_ELIMINATION = "deadAssignmentsElimination";
  public static final String DECLARED_GLOBAL_EXTERNS_ON_WINDOW = "declaredGlobalExternsOnWindow";
  public static final String DESERIALIZE_COMPILER_STATE = "deserializeCompilerState";
  public static final String DEVIRTUALIZE_PROTOTYPE_METHODS = "devirtualizePrototypeMethods";
  public static final String DISAMBIGUATE_PRIVATE_PROPERTIES = "disambiguatePrivateProperties";
  public static final String DISAMBIGUATE_PROPERTIES = "disambiguateProperties";
  public static final String ES6_BLESS_XID_CALLS = "Es6BlessXidCalls";
  public static final String ES6_EXTRACT_CLASSES = "Es6ExtractClasses";
  public static final String EXPLOIT_ASSIGN = "exploitAssign";
  public static final String EXPORT_TEST_FUNCTIONS = "exportTestFunctions";
  public static final String EXTERN_EXPORTS = "externExports";
  public static final String EXTRACT_PROTOTYPE_MEMBER_DECLARATIONS =
      "extractPrototypeMemberDeclarations";
  public static final String FLOW_SENSITIVE_INLINE_VARIABLES = "flowSensitiveInlineVariables";
  public static final String GATHER_RAW_EXPORTS = "gatherRawExports";
  public static final String GENERATE_EXPORTS = "generateExports";
  public static final String INFER_CONSTS = "inferConsts";
  public static final String INFER_TYPES = "inferTypes";
  public static final String INLINE_FUNCTIONS = "inlineFunctions";
  public static final String INLINE_PROPERTIES = "inlineProperties";
  public static final String INLINE_TYPE_ALIASES = "inlineTypeAliases";
  public static final String INLINE_VARIABLES = "inlineVariables";
  public static final String LINT_CHECKS = "lintChecks";
  public static final String MARK_NO_SIDE_EFFECT_CALLS = "markNoSideEffectCalls";
  public static final String MINIMIZE_EXIT_POINTS = "minimizeExitPoints";
  public static final String MOVE_FUNCTION_DECLARATIONS = "moveFunctionDeclarations";
  public static final String NAME_ANONYMOUS_FUNCTIONS = "nameAnonymousFunctions";
  public static final String NORMALIZE = "normalize";
  public static final String OPTIMIZE_ARGUMENTS_ARRAY = "optimizeArgumentsArray";
  public static final String OPTIMIZE_CALLS = "optimizeCalls";
  public static final String PARSE_INPUTS = "parseInputs";
  public static final String PEEPHOLE_OPTIMIZATIONS = "peepholeOptimizations";
  public static final String REWRITE_COMMON_JS_MODULES = "rewriteCommonJsModules";
  public static final String REWRITE_SCRIPTS_TO_ES6_MODULES = "rewriteScriptsToEs6Modules";
  public static final String REMOVE_SUPER_METHODS = "removeSuperMethods";
  public static final String REMOVE_UNREACHABLE_CODE = "removeUnreachableCode";
  public static final String REMOVE_UNUSED_CLASS_PROPERTIES = "removeUnusedClassProperties";
  public static final String REMOVE_UNUSED_PROTOTYPE_PROPERTIES =
      "removeUnusedPrototypeProperties";
  public static final String REMOVE_UNUSED_VARS = "removeUnusedVars";
  public static final String REPLACE_ID_GENERATORS = "replaceIdGenerators";
  public static final String REPLACE_MESSAGES = "replaceMessages";
  public static final String RESOLVE_TYPES = "resolveTypes";
  public static final String REWRITE_FUNCTION_EXPRESSIONS = "rewriteFunctionExpressions";
  public static final String RUNTIME_TYPE_CHECK = "runtimeTypeCheck";
  public static final String SMART_NAME_PASS = "smartNamePass";
  public static final String STRIP_SIDE_EFFECT_PROTECTION = "stripSideEffectProtection";
  public static final String WIZ_PASS = "wizPass";

  private PassNames() {}
}
