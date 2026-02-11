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

import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.disambiguate.DisambiguateProperties;
import com.google.javascript.jscomp.ijs.IjsErrors;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckConstPrivateProperties;
import com.google.javascript.jscomp.lint.CheckConstantCaseNames;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure;
import com.google.javascript.jscomp.lint.CheckEs6Modules;
import com.google.javascript.jscomp.lint.CheckExtraRequires;
import com.google.javascript.jscomp.lint.CheckGoogModuleTypeScriptName;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNestedNames;
import com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUnusedPrivateProperties;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.lint.CheckVar;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Named groups of DiagnosticTypes exposed by Compiler. */
public class DiagnosticGroups {
  static final DiagnosticType UNUSED = DiagnosticType.warning("JSC_UNUSED", "{0}");

  public static final ImmutableSet<String> wildcardExcludedGroups =
      ImmutableSet.of(
          "reportUnknownTypes",
          "analyzerChecks",
          "missingSourcesWarnings",
          "closureUnawareCodeAnnotationPresent");

  public DiagnosticGroups() {}

  private static final Map<String, DiagnosticGroup> groupsByName = new LinkedHashMap<>();

  static DiagnosticGroup registerDeprecatedGroup(String name) {
    return registerGroup(name, new DiagnosticGroup(name, UNUSED));
  }

  /**
   * Create a group that is unsuppressible via the command line or in code.
   *
   * <p>The resulting group is also undocumented.
   */
  static DiagnosticGroup registerUnsuppressibleGroup(DiagnosticType... types) {
    return new DiagnosticGroup(types);
  }

  @CanIgnoreReturnValue
  static DiagnosticGroup registerGroup(String name, DiagnosticGroup group) {
    groupsByName.put(name, group);
    return group;
  }

  public static DiagnosticGroup registerGroup(String name, DiagnosticType... types) {
    DiagnosticGroup group = new DiagnosticGroup(name, types);
    groupsByName.put(name, group);
    return group;
  }

  static DiagnosticGroup registerGroup(String name, DiagnosticGroup... groups) {
    DiagnosticGroup group = new DiagnosticGroup(name, groups);
    groupsByName.put(name, group);
    return group;
  }

  /** Get the registered diagnostic groups, indexed by name. */
  public static ImmutableMap<String, DiagnosticGroup> getRegisteredGroups() {
    return ImmutableMap.copyOf(groupsByName);
  }

  /** Find the diagnostic group registered under the given name. */
  public static DiagnosticGroup forName(String name) {
    return groupsByName.get(name);
  }

  // A bit of a hack to display the available groups on the command-line.
  // New groups should be added to this list if they are public and should
  // be listed on the command-line as an available option.
  //
  // If a group is suppressible on a per-file basis, it should be added
  // to parsing/ParserConfig.properties
  static final String DIAGNOSTIC_GROUP_NAMES =
      "accessControls, "
          + "checkPrototypalTypes, "
          + "checkRegExp, "
          + "checkTypes, "
          + "checkVars, "
          + "conformanceViolations, "
          + "const, "
          + "constantProperty, "
          + "deprecated, "
          + "deprecatedAnnotations, "
          + "duplicateMessage, "
          + "es5Strict, "
          + "externsValidation, "
          + "functionParams, "
          + "globalThis, "
          + "invalidCasts, "
          + "lintVarDeclarations, "
          + "misplacedTypeAnnotation, "
          + "missingOverride, "
          + "missingPolyfill, "
          + "missingProperties, "
          + "missingProvide, "
          + "missingRequire, "
          + "missingReturn, "
          + "missingSourcesWarnings, "
          + "moduleLoad, "
          + "moduleImport, "
          + "msgDescriptions, "
          + "nonStandardJsDocs, "
          + "partialAlias, "
          + "polymer, "
          + "reportUnknownTypes, "
          + "strictCheckTypes, "
          + "strictMissingProperties, "
          + "strictModuleDepCheck, "
          + "strictPrimitiveOperators, "
          + "suspiciousCode, "
          + "typeInvalidation, "
          + "undefinedVars, "
          + "underscore, "
          + "unknownDefines, "
          + "unusedLocalVariables, "
          + "uselessCode, "
          + "untranspilableFeatures,"
          + "visibility";

  // TODO(b/123768968) remove this diagnostic group, do not allow this suppression. Instead the only
  // work around should be to raise the output language to a high enough level. We need to suppress
  // right now because we don't have any language output level higher than ES5.
  public static final DiagnosticGroup UNTRANSPILABLE_FEATURES =
      DiagnosticGroups.registerGroup(
          "untranspilableFeatures", ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);

  public static final DiagnosticGroup MODULE_LOAD =
      DiagnosticGroups.registerGroup(
          "moduleLoad",
          ModuleLoader.LOAD_WARNING,
          ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT,
          ProcessCommonJSModules.UNKNOWN_REQUIRE_ENSURE);

  public static final DiagnosticGroup MODULE_IMPORT =
      DiagnosticGroups.registerGroup( // undocumented
          "moduleImport",
          ModuleMapCreator.DOES_NOT_HAVE_EXPORT,
          ModuleMapCreator.DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

  public static final DiagnosticGroup GLOBAL_THIS =
      DiagnosticGroups.registerGroup("globalThis", CheckGlobalThis.GLOBAL_THIS);

  public static final DiagnosticGroup DEPRECATED =
      DiagnosticGroups.registerGroup(
          "deprecated",
          CheckAccessControls.DEPRECATED_NAME,
          CheckAccessControls.DEPRECATED_NAME_REASON,
          CheckAccessControls.DEPRECATED_PROP,
          CheckAccessControls.DEPRECATED_PROP_REASON,
          CheckAccessControls.DEPRECATED_CLASS,
          CheckAccessControls.DEPRECATED_CLASS_REASON);

  public static final DiagnosticGroup UNDERSCORE =
      DiagnosticGroups.registerDeprecatedGroup("underscore");

  public static final DiagnosticGroup VISIBILITY =
      DiagnosticGroups.registerGroup(
          "visibility",
          CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS,
          CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS,
          CheckAccessControls.BAD_PACKAGE_PROPERTY_ACCESS,
          CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS,
          CheckAccessControls.EXTEND_FINAL_CLASS,
          CheckAccessControls.FINAL_PROPERTY_OVERRIDDEN,
          CheckAccessControls.PRIVATE_OVERRIDE,
          CheckAccessControls.VISIBILITY_MISMATCH);

  // TODO(tbreisacher): Deprecate this and keep just the "visibility" group.
  public static final DiagnosticGroup ACCESS_CONTROLS =
      DiagnosticGroups.registerGroup("accessControls", VISIBILITY);

  public static final DiagnosticGroup NON_STANDARD_JSDOC =
      DiagnosticGroups.registerGroup(
          "nonStandardJsDocs",
          RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
          RhinoErrorReporter.INVALID_PARAM,
          RhinoErrorReporter.JSDOC_IMPORT_TYPE_WARNING,
          CheckJSDoc.JSDOC_IN_BLOCK_COMMENT);

  public static final DiagnosticGroup INVALID_CASTS =
      DiagnosticGroups.registerGroup("invalidCasts", TypeValidator.INVALID_CAST);

  public static final DiagnosticGroup STRICT_MODULE_DEP_CHECK =
      DiagnosticGroups.registerGroup(
          "strictModuleDepCheck",
          VarCheck.STRICT_CHUNK_DEP_ERROR,
          CheckClosureImports.CROSS_CHUNK_REQUIRE_ERROR);

  public static final DiagnosticGroup VIOLATED_MODULE_DEP =
      DiagnosticGroups.registerGroup("violatedModuleDep", VarCheck.VIOLATED_CHUNK_DEP_ERROR);

  public static final DiagnosticGroup EXTERNS_VALIDATION =
      DiagnosticGroups.registerGroup(
          "externsValidation",
          VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR,
          VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

  public static final DiagnosticGroup UNKNOWN_DEFINES =
      DiagnosticGroups.registerGroup("unknownDefines", ProcessDefines.UNKNOWN_DEFINE_WARNING);

  public static final DiagnosticGroup TWEAKS =
      DiagnosticGroups.registerGroup(
          "tweakValidation",
          ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_WARNING,
          ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);

  public static final DiagnosticGroup MISSING_OVERRIDE =
      DiagnosticGroups.registerGroup(
          "missingOverride",
          TypeCheck.HIDDEN_INTERFACE_PROPERTY,
          TypeCheck.HIDDEN_PROTOTYPAL_SUPERTYPE_PROPERTY,
          TypeCheck.HIDDEN_SUPERCLASS_PROPERTY);

  public static final DiagnosticGroup MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup(
          "missingProperties",
          TypeCheck.INEXISTENT_PROPERTY,
          TypeCheck.INEXISTENT_PROPERTY_WITH_SUGGESTION,
          TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

  public static final DiagnosticGroup GLOBALLY_MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup(
          "globallyMissingProperties", TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

  public static final DiagnosticGroup J2CL_CHECKS =
      DiagnosticGroups.registerGroup("j2clChecks", J2clChecksPass.J2CL_REFERENCE_EQUALITY);

  public static final DiagnosticGroup MISSING_RETURN =
      DiagnosticGroups.registerGroup("missingReturn", CheckMissingReturn.MISSING_RETURN_STATEMENT);

  public static final DiagnosticGroup UNDEFINED_VARIABLES =
      DiagnosticGroups.registerGroup("undefinedVars", VarCheck.UNDEFINED_VAR_ERROR);

  public static final DiagnosticGroup DEBUGGER_STATEMENT_PRESENT =
      DiagnosticGroups.registerGroup(
          "checkDebuggerStatement", CheckDebuggerStatement.DEBUGGER_STATEMENT_PRESENT);

  public static final DiagnosticGroup CHECK_REGEXP =
      DiagnosticGroups.registerGroup(
          "checkRegExp", CheckRegExp.REGEXP_REFERENCE, CheckRegExp.MALFORMED_REGEXP);

  // NOTE(dimvar): it'd be nice to add TypedScopeCreator.ALL_DIAGNOSTICS here,
  // but we would first need to cleanup projects that would break because
  // they set --jscomp_error=checkTypes.
  public static final DiagnosticGroup CHECK_TYPES =
      DiagnosticGroups.registerGroup(
          "checkTypes",
          TypeValidator.ALL_DIAGNOSTICS,
          TypeCheck.ALL_DIAGNOSTICS,
          FunctionTypeBuilder.ALL_DIAGNOSTICS,
          DiagnosticGroups.GLOBAL_THIS,
          //  The subset of CheckJsDoc diagnostics that don't make sense to be enabled when
          // type-checking is disabled.
          new DiagnosticGroup(
              CheckJSDoc.DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL,
              CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION));

  public static final DiagnosticGroup ES5_INHERITANCE_DIAGNOSTIC_GROUP =
      DiagnosticGroups.registerGroup(
          "checkEs5InheritanceCorrectnessConditions", TypeCheck.ES5_INHERITANCE_DIAGNOSTIC_GROUP);

  public static final DiagnosticGroup CHECK_PROTOTYPAL_TYPES =
      DiagnosticGroups.registerGroup(
          "checkPrototypalTypes",
          TypeCheck.UNKNOWN_PROTOTYPAL_OVERRIDE,
          TypeCheck.HIDDEN_PROTOTYPAL_SUPERTYPE_PROPERTY,
          TypeCheck.HIDDEN_PROTOTYPAL_SUPERTYPE_PROPERTY_MISMATCH);

  // This group exists for the J2CL team to suppress the associated diagnostics using Java code
  // rather than `@suppress` annotations.
  public static final DiagnosticGroup CHECK_STATIC_OVERRIDES = CHECK_PROTOTYPAL_TYPES;

  public static final DiagnosticGroup TOO_MANY_TYPE_PARAMS =
      DiagnosticGroups.registerGroup(
          "tooManyTypeParams", RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);

  public static final DiagnosticGroup STRICT_MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup(
          "strictMissingProperties",
          TypeCheck.STRICT_INEXISTENT_PROPERTY,
          TypeCheck.STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION,
          TypeCheck.STRICT_INEXISTENT_UNION_PROPERTY,
          TypeCheck.ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE);

  public static final DiagnosticGroup STRICT_PRIMITIVE_OPERATORS =
      DiagnosticGroups.registerGroup(
          "strictPrimitiveOperators", TypeValidator.INVALID_OPERAND_TYPE);

  public static final DiagnosticGroup STRICT_CHECK_TYPES =
      DiagnosticGroups.registerGroup(
          "strictCheckTypes", STRICT_MISSING_PROPERTIES, STRICT_PRIMITIVE_OPERATORS);

  public static final DiagnosticGroup REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("reportUnknownTypes", TypeCheck.UNKNOWN_EXPR_TYPE);

  public static final DiagnosticGroup CHECK_VARIABLES =
      DiagnosticGroups.registerGroup(
          "checkVars",
          VarCheck.UNDEFINED_VAR_ERROR,
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          VariableReferenceCheck.EARLY_REFERENCE,
          VariableReferenceCheck.REDECLARED_VARIABLE);

  public static final DiagnosticGroup CHECK_USELESS_CODE =
      DiagnosticGroups.registerGroup(
          "uselessCode",
          CheckSideEffects.USELESS_CODE_ERROR,
          CheckUnreachableCode.UNREACHABLE_CODE);

  public static final DiagnosticGroup CONST =
      DiagnosticGroups.registerGroup(
          "const",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE,
          ConstCheck.CONST_REASSIGNED_VALUE_ERROR);

  public static final DiagnosticGroup CONSTANT_PROPERTY =
      DiagnosticGroups.registerGroup(
          "constantProperty",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE);

  static final DiagnosticGroup ACCESS_CONTROLS_CONST =
      DiagnosticGroups.registerGroup("accessControlsConst", CONSTANT_PROPERTY);

  public static final DiagnosticGroup TYPE_INVALIDATION =
      DiagnosticGroups.registerGroup(
          "typeInvalidation", DisambiguateProperties.PROPERTY_INVALIDATION);

  public static final DiagnosticGroup DUPLICATE_VARS =
      DiagnosticGroups.registerGroup(
          "duplicate",
          InlineAndCollapseProperties.NAMESPACE_REDEFINED_WARNING,
          TypeValidator.DUP_VAR_DECLARATION,
          TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH,
          TypeCheck.FUNCTION_MASKS_VARIABLE,
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          VariableReferenceCheck.REDECLARED_VARIABLE);

  public static final DiagnosticGroup ES5_STRICT =
      DiagnosticGroups.registerGroup( // undocumented
          "es5Strict",
          RhinoErrorReporter.INVALID_OCTAL_LITERAL,
          RhinoErrorReporter.DUPLICATE_PARAM,
          StrictModeCheck.ARGUMENTS_ASSIGNMENT,
          StrictModeCheck.ARGUMENTS_DECLARATION,
          StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN,
          StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN,
          StrictModeCheck.DELETE_VARIABLE,
          StrictModeCheck.DUPLICATE_MEMBER,
          StrictModeCheck.EVAL_ASSIGNMENT,
          StrictModeCheck.EVAL_DECLARATION,
          StrictModeCheck.FUNCTION_ARGUMENTS_PROP_FORBIDDEN,
          StrictModeCheck.FUNCTION_CALLER_FORBIDDEN,
          StrictModeCheck.USE_OF_WITH);

  public static final DiagnosticGroup MISSING_PROVIDE =
      DiagnosticGroups.registerGroup(
          "missingProvide",
          // TODO(b/143887932): Move this into a better DiagnosticGroup
          ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE_FOR_FORWARD_DECLARE);

  public static final DiagnosticGroup UNRECOGNIZED_TYPE_ERROR =
      DiagnosticGroups.registerGroup(
          "unrecognizedTypeError", // undocumented
          RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);

  // identical to UNRECOGNIZED_TYPE_ERROR, but allowed in @suppress tags
  static final DiagnosticGroup DANGEROUS_UNRECOGNIZED_TYPE_ERROR =
      DiagnosticGroups.registerGroup("dangerousUnrecognizedTypeError", UNRECOGNIZED_TYPE_ERROR);

  public static final DiagnosticGroup MISSING_REQUIRE =
      DiagnosticGroups.registerGroup(
          "missingRequire",
          CheckMissingRequires.MISSING_REQUIRE,
          CheckMissingRequires.MISSING_REQUIRE_IN_PROVIDES_FILE,
          CheckMissingRequires.MISSING_REQUIRE_IN_GOOG_SCOPE,
          CheckMissingRequires.MISSING_REQUIRE_TYPE_IN_GOOG_SCOPE,
          CheckMissingRequires.MISSING_REQUIRE_TYPE,
          CheckMissingRequires.MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE,
          CheckMissingRequires.INCORRECT_NAMESPACE_ALIAS_REQUIRE,
          CheckMissingRequires.INCORRECT_NAMESPACE_ALIAS_REQUIRE_TYPE,
          CheckMissingRequires.INDIRECT_NAMESPACE_REF_REQUIRE,
          CheckMissingRequires.INDIRECT_NAMESPACE_REF_REQUIRE_TYPE);

  /**
   * A set of diagnostics expected when parsing and type checking partial programs. Useful for clutz
   * (tool that extracts TypeScript definitions from JS code).
   */
  public static final DiagnosticGroup MISSING_SOURCES_WARNINGS =
      DiagnosticGroups.registerGroup(
          "missingSourcesWarnings",
          REPORT_UNKNOWN_TYPES,
          UNDEFINED_VARIABLES,
          MISSING_PROVIDE,
          DiagnosticGroup.forType(MISSING_MODULE_OR_PROVIDE),
          MISSING_PROPERTIES,
          // triggered by typedefs with missing types
          DUPLICATE_VARS,
          // caused by a define depending on another define that's missing
          DiagnosticGroup.forType(ProcessDefines.INVALID_DEFINE_VALUE));

  public static final DiagnosticGroup EXTRA_REQUIRE =
      DiagnosticGroups.registerGroup("extraRequire", CheckExtraRequires.EXTRA_REQUIRE_WARNING);

  public static final DiagnosticGroup DUPLICATE_MESSAGE =
      DiagnosticGroups.registerGroup("duplicateMessage", JsMessageVisitor.MESSAGE_DUPLICATE_KEY);

  public static final DiagnosticGroup MESSAGE_DESCRIPTIONS =
      DiagnosticGroups.registerGroup(
          "msgDescriptions", JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);

  /**
   * Warnings that only apply to people who use MSG_ to denote messages. Note that this doesn't
   * include warnings about proper use of goog.getMsg
   */
  public static final DiagnosticGroup MSG_CONVENTIONS =
      DiagnosticGroups.registerGroup(
          "messageConventions", // undocumented
          JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION,
          JsMessageVisitor.MESSAGE_HAS_NO_TEXT,
          JsMessageVisitor.MESSAGE_TREE_MALFORMED,
          JsMessageVisitor.MESSAGE_HAS_NO_VALUE,
          JsMessageVisitor.MESSAGE_DUPLICATE_KEY,
          JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY);

  public static final DiagnosticGroup MISPLACED_TYPE_ANNOTATION =
      DiagnosticGroups.registerGroup(
          "misplacedTypeAnnotation",
          CheckJSDoc.ARROW_FUNCTION_AS_CONSTRUCTOR,
          CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION,
          CheckJSDoc.DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL,
          CheckJSDoc.DISALLOWED_MEMBER_JSDOC,
          CheckJSDoc.INVALID_NO_SIDE_EFFECT_ANNOTATION,
          CheckJSDoc.INVALID_MODIFIES_ANNOTATION,
          CheckJSDoc.JSDOC_ON_RETURN,
          CheckJSDoc.MISPLACED_ANNOTATION,
          CheckJSDoc.MISPLACED_MSG_ANNOTATION);

  public static final DiagnosticGroup MISPLACED_MSG_ANNOTATION =
      DiagnosticGroups.registerGroup("misplacedMsgAnnotation", CheckJSDoc.MISPLACED_MSG_ANNOTATION);

  public static final DiagnosticGroup MISPLACED_SUPPRESS =
      DiagnosticGroups.registerGroup("misplacedSuppress", CheckJSDoc.MISPLACED_SUPPRESS);

  public static final DiagnosticGroup SUSPICIOUS_CODE =
      DiagnosticGroups.registerGroup(
          "suspiciousCode",
          CheckDuplicateCase.DUPLICATE_CASE,
          CheckSuspiciousCode.SUSPICIOUS_SEMICOLON,
          CheckSuspiciousCode.SUSPICIOUS_BREAKING_OUT_OF_OPTIONAL_CHAIN,
          CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN,
          CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR,
          CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND,
          CheckSuspiciousCode.SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR,
          CheckSuspiciousCode.SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR,
          ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT,
          TypeCheck.DETERMINISTIC_TEST);

  public static final DiagnosticGroup FUNCTION_PARAMS =
      DiagnosticGroups.registerGroup(
          "functionParams",
          FunctionTypeBuilder.INEXISTENT_PARAM,
          FunctionTypeBuilder.OPTIONAL_ARG_AT_END);

  public static final DiagnosticGroup DEPRECATED_ANNOTATIONS =
      DiagnosticGroups.registerGroup("deprecatedAnnotations", CheckJSDoc.ANNOTATION_DEPRECATED);

  public static final DiagnosticGroup UNUSED_LOCAL_VARIABLE =
      DiagnosticGroups.registerGroup(
          "unusedLocalVariables", VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT);

  public static final DiagnosticGroup JSDOC_MISSING_TYPE =
      DiagnosticGroups.registerGroup(
          "jsdocMissingType", RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING);

  public static final DiagnosticGroup TYPE_IMPORT_CODE_REFERENCES =
      DiagnosticGroups.registerGroup(
          "typeImportCodeReferences", CheckTypeImportCodeReferences.TYPE_IMPORT_CODE_REFERENCE);

  public static final DiagnosticGroup PARTIAL_ALIAS =
      DiagnosticGroups.registerGroup(
          "partialAlias", InlineAndCollapseProperties.PARTIAL_NAMESPACE_WARNING);

  // This lint is given its own diagnostic group because it's harder to fix than other
  // lint errors, and we want to discourage users from doing a blanket @suppress {lintChecks}.
  // This is intentionally not public. It should not be enabled directly; instead enable lintChecks.
  static final DiagnosticGroup USE_OF_GOOG_PROVIDE =
      DiagnosticGroups.registerGroup("useOfGoogProvide", ClosureCheckModule.USE_OF_GOOG_PROVIDE);

  /**
   * This is intended to be used to suppress warnings in code that cannot for some reason be updated
   * to use `let` and `const` instead of `var`. It should not be enabled directly, instead enable
   * "lintChecks".
   */
  public static final DiagnosticGroup LINT_VAR_DECLARATIONS =
      DiagnosticGroups.registerGroup(
          "lintVarDeclarations", // undocumented
          CheckVar.VAR);

  // Warnings reported by the linter. If you enable these as errors in your build targets,
  // the JS Compiler team will break your build and not rollback.
  public static final DiagnosticGroup LINT_CHECKS =
      DiagnosticGroups.registerGroup(
          "lintChecks", // undocumented
          CheckJSDocStyle.LINT_DIAGNOSTICS,
          USE_OF_GOOG_PROVIDE,
          LINT_VAR_DECLARATIONS,
          new DiagnosticGroup(
              CheckClosureImports.LET_CLOSURE_IMPORT,
              CheckConstPrivateProperties.MISSING_CONST_PROPERTY,
              CheckConstantCaseNames.REASSIGNED_CONSTANT_CASE_NAME,
              CheckConstantCaseNames.MISSING_CONST_PROPERTY,
              CheckEmptyStatements.USELESS_EMPTY_STATEMENT,
              CheckEnums.COMPUTED_PROP_NAME_IN_ENUM,
              CheckEnums.DUPLICATE_ENUM_VALUE,
              CheckEnums.ENUM_PROP_NOT_CONSTANT,
              CheckEnums.ENUM_TYPE_NOT_STRING_OR_NUMBER,
              CheckEnums.NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM,
              CheckEnums.SHORTHAND_ASSIGNMENT_IN_ENUM,
              CheckEs6ModuleFileStructure.MUST_COME_BEFORE,
              CheckEs6Modules.DUPLICATE_IMPORT,
              CheckEs6Modules.NO_DEFAULT_EXPORT,
              CheckGoogModuleTypeScriptName.MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE,
              // TODO(tbreisacher): Consider moving the CheckInterfaces warnings into the
              // checkTypes DiagnosticGroup
              CheckInterfaces.INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY,
              CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS,
              CheckInterfaces.INTERFACE_DEFINED_WITH_EXTENDS,
              CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE,
              CheckInterfaces.MISSING_JSDOC_IN_DECLARATION_STATEMENT,
              CheckInterfaces.STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS,
              CheckMissingSemicolon.MISSING_SEMICOLON,
              CheckNoMutatedEs6Exports.MUTATED_EXPORT,
              CheckNullabilityModifiers.MISSING_NULLABILITY_MODIFIER_JSDOC,
              CheckNullabilityModifiers.NULL_MISSING_NULLABILITY_MODIFIER_JSDOC,
              CheckNullabilityModifiers.REDUNDANT_NULLABILITY_MODIFIER_JSDOC,
              CheckPrimitiveAsObject.NEW_PRIMITIVE_OBJECT,
              CheckPrimitiveAsObject.PRIMITIVE_OBJECT_DECLARATION,
              CheckPrototypeProperties.ILLEGAL_PROTOTYPE_MEMBER,
              CheckProvidesSorted.PROVIDES_NOT_SORTED,
              CheckRequiresSorted.REQUIRES_NOT_SORTED,
              CheckUnusedLabels.UNUSED_LABEL,
              CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY,
              CheckUselessBlocks.USELESS_BLOCK,
              ClosureCheckModule.DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE,
              ClosureCheckModule.GOOG_MODULE_IN_NON_MODULE,
              ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION,
              ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE,
              RhinoErrorReporter.JSDOC_MISSING_BRACES_WARNING,
              RhinoErrorReporter.UNNECESSARY_ESCAPE,
              RhinoErrorReporter.STRING_CONTINUATION));

  public static final DiagnosticGroup STRICT_MODULE_CHECKS =
      DiagnosticGroups.registerGroup(
          "strictModuleChecks",
          ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE,
          ClosureCheckModule.LET_GOOG_REQUIRE,
          ClosureCheckModule.REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME,
          ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

  // Similar to the lintChecks group above, but includes things that cannot be done on a single
  // file at a time, for example because they require typechecking. If you enable these as errors
  // in your build targets, the JS Compiler team will break your build and not rollback.
  public static final DiagnosticGroup ANALYZER_CHECKS =
      DiagnosticGroups.registerGroup(
          "analyzerChecks", // undocumented
          CheckArrayWithGoogObject.ARRAY_PASSED_TO_GOOG_OBJECT,
          ImplicitNullabilityCheck.IMPLICITLY_NONNULL_JSDOC,
          ImplicitNullabilityCheck.IMPLICITLY_NULLABLE_JSDOC,
          CheckNestedNames.NESTED_NAME_IN_GOOG_MODULE);

  public static final DiagnosticGroup CLOSURE_DEP_METHOD_USAGE_CHECKS =
      DiagnosticGroups.registerGroup(
          "closureDepMethodUsageChecks",
          CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR,
          CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR,
          INVALID_CLOSURE_CALL_SCOPE_ERROR,
          INVALID_GET_CALL_SCOPE);

  public static final DiagnosticGroup CLOSURE_CLASS_CHECKS =
      DiagnosticGroups.registerGroup(
          "closureClassChecks", ProcessClosurePrimitives.POSSIBLE_BASE_CLASS_ERROR);

  // This group exists so that tests can check for these warnings. It is intentionally not
  // named so that it is is not suppressible via the command line or in code.
  @VisibleForTesting
  public static final DiagnosticGroup MALFORMED_GOOG_MODULE =
      DiagnosticGroups.registerUnsuppressibleGroup(
          ClosureCheckModule.GOOG_MODULE_MISPLACED,
          ClosureCheckModule.LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);

  // This group exists so that generated code can suppress these
  // warnings. Not for general use. These diagnostics will most likely
  // be moved to the suspiciousCode group.
  static {
    DiagnosticGroups.registerGroup(
        "transitionalSuspiciousCodeWarnings", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  // This diagnostic group is intentionally absent in ParserConfig.properties.
  // Conformance checks are supposed to be enforced project-wide, so we don't
  // allow suppressions on individual functions.
  // In the future, we may carve out a subset of the conformance checks that is
  // OK to suppress.
  // For now, the only way to suppress a check at a granularity smaller than
  // the file level is by using a allowlist file.
  public static final DiagnosticGroup CONFORMANCE_VIOLATIONS =
      DiagnosticGroups.registerGroup(
          "conformanceViolations",
          CheckConformance.CONFORMANCE_VIOLATION,
          CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

  public static final DiagnosticGroup LATE_PROVIDE =
      DiagnosticGroups.registerGroup(
          "lateProvide", // undocumented
          CheckClosureImports.LATE_PROVIDE_ERROR);

  public static final DiagnosticGroup DUPLICATE_NAMESPACES =
      DiagnosticGroups.registerUnsuppressibleGroup(
          ClosurePrimitiveErrors.DUPLICATE_MODULE,
          ClosurePrimitiveErrors.DUPLICATE_NAMESPACE,
          ClosurePrimitiveErrors.DUPLICATE_NAMESPACE_AND_MODULE);

  public static final DiagnosticGroup INVALID_DEFINES =
      DiagnosticGroups.registerUnsuppressibleGroup(
          ProcessDefines.INVALID_DEFINE_VALUE, ProcessDefines.INVALID_DEFINE_TYPE);

  public static final DiagnosticGroup MISSING_POLYFILL =
      DiagnosticGroups.registerGroup(
          "missingPolyfill", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

  public static final DiagnosticGroup POLYMER =
      DiagnosticGroups.registerGroup("polymer", PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID);

  static final DiagnosticGroup BOUNDED_GENERICS =
      DiagnosticGroups.registerGroup(
          "boundedGenerics",
          RhinoErrorReporter.UNSUPPORTED_BOUNDED_GENERIC_TYPES,
          RhinoErrorReporter.BOUNDED_GENERIC_TYPE_ERROR);

  // This diagnostic group is intentionally absent in ParserConfig.properties and unnamed. User code
  // should never suppress parse errors but it is useful occasionally for tooling to check whether a
  // given error is from parsing.
  public static final DiagnosticGroup PARSING =
      DiagnosticGroup.forType(RhinoErrorReporter.PARSE_ERROR);

  public static final DiagnosticGroup CLOSURE_UNAWARE_CODE_ANNOTATION_PRESENT =
      DiagnosticGroups.registerGroup( // undocumented
          // Deliberately undocumented and unsuppressable per-file, but this should be OK because
          // the diagnostics are disabled by default.
          // They can be enabled by explicitly enabling them using
          // `--jscomp_error=closureUnawareCodeAnnotationPresent`.
          "closureUnawareCodeAnnotationPresent",
          RhinoErrorReporter.CLOSURE_UNAWARE_ANNOTATION_PRESENT);

  // For internal use only, so there are no constants for these groups.
  static {
    DiagnosticGroups.registerGroup(
        "polymerBehavior", PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR);

    DiagnosticGroups.registerGroup(
        "invalidProvide", ProcessClosurePrimitives.INVALID_PROVIDE_ERROR);

    DiagnosticGroups.registerGroup("conflictingIjsFile", IjsErrors.CONFLICTING_IJS_FILE);

    DiagnosticGroups.registerGroup(
        "implicitWeakEntryPoint", JSChunkGraph.IMPLICIT_WEAK_ENTRY_POINT_ERROR);
  }

  public static final DiagnosticGroup ARTIFICIAL_FUNCTION_PURITY_VALIDATION =
      DiagnosticGroups.registerGroup(
          "artificialFunctionPurityValidation",
          PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION);

  /** Adds warning levels by name. */
  public void setWarningLevel(CompilerOptions options, String name, CheckLevel level) {
    DiagnosticGroup group = forName(name);
    Preconditions.checkNotNull(group, "No warning class for name: %s", name);
    options.setWarningLevel(group, level);
  }
}
