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

import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure;
import com.google.javascript.jscomp.lint.CheckEs6Modules;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Named groups of DiagnosticTypes exposed by Compiler.
 * @author nicksantos@google.com (Nick Santos)
 */
public class DiagnosticGroups {
  static final DiagnosticType UNUSED =
      DiagnosticType.warning("JSC_UNUSED", "{0}");

  public static final Set<String> wildcardExcludedGroups =
      ImmutableSet.of(
          "reportUnknownTypes",
          "analyzerChecks",
          "analyzerChecksInternal",
          "oldReportUnknownTypes",
          "newCheckTypes",
          "newCheckTypesCompatibility",
          "newCheckTypesExtraChecks",
          "missingSourcesWarnings",
          // TODO(johnlenz): "strictMissingProperties" is here until it has a shake down cruise.
          "strictMissingProperties",
          "strictPrimitiveOperators",
          "strictCheckTypes");

  public DiagnosticGroups() {}

  private static final Map<String, DiagnosticGroup> groupsByName =
       new HashMap<>();

  static DiagnosticGroup registerDeprecatedGroup(String name) {
    return registerGroup(name, new DiagnosticGroup(name, UNUSED));
  }

  static DiagnosticGroup registerGroup(String name,
      DiagnosticGroup group) {
    groupsByName.put(name, group);
    return group;
  }

  static DiagnosticGroup registerGroup(String name,
      DiagnosticType ... types) {
    DiagnosticGroup group = new DiagnosticGroup(name, types);
    groupsByName.put(name, group);
    return group;
  }

  static DiagnosticGroup registerGroup(String name,
      DiagnosticGroup ... groups) {
    DiagnosticGroup group = new DiagnosticGroup(name, groups);
    groupsByName.put(name, group);
    return group;
  }

  /** Get the registered diagnostic groups, indexed by name. */
  public Map<String, DiagnosticGroup> getRegisteredGroups() {
    return ImmutableMap.copyOf(groupsByName);
  }

  /** Find the diagnostic group registered under the given name. */
  public DiagnosticGroup forName(String name) {
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
          + "ambiguousFunctionDecl, "
          + "checkRegExp, "
          + "checkTypes, "
          + "checkVars, "
          + "conformanceViolations, "
          + "const, "
          + "constantProperty, "
          + "deprecated, "
          + "deprecatedAnnotations, "
          + "duplicateMessage, "
          + "es3, "
          + "es5Strict, "
          + "externsValidation, "
          + "fileoverviewTags, "
          + "functionParams, "
          + "globalThis, "
          + "internetExplorerChecks, "
          + "invalidCasts, "
          + "misplacedTypeAnnotation, "
          + "missingGetCssName, "
          + "missingOverride, "
          + "missingPolyfill, "
          + "missingProperties, "
          + "missingProvide, "
          + "missingRequire, "
          + "missingReturn, "
          + "moduleLoad, "
          + "msgDescriptions, "
          + "newCheckTypes, "
          + "nonStandardJsDocs, "
          + "missingSourcesWarnings, "
          + "polymer, "
          + "reportUnknownTypes, "
          + "suspiciousCode, "
          + "strictCheckTypes, "
          + "strictMissingProperties, "
          + "strictModuleDepCheck, "
          + "strictPrimitiveOperators, "
          + "typeInvalidation, "
          + "undefinedNames, "
          + "undefinedVars, "
          + "unknownDefines, "
          + "unusedLocalVariables, "
          + "unusedPrivateMembers, "
          + "uselessCode, "
          + "useOfGoogBase, "
          + "underscore, "
          + "visibility";

  public static final DiagnosticGroup MODULE_LOAD =
      DiagnosticGroups.registerGroup("moduleLoad",
          ModuleLoader.LOAD_WARNING,
          ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT,
          ProcessCommonJSModules.UNKNOWN_REQUIRE_ENSURE);

  public static final DiagnosticGroup GLOBAL_THIS =
      DiagnosticGroups.registerGroup("globalThis",
          CheckGlobalThis.GLOBAL_THIS);

  public static final DiagnosticGroup DEPRECATED =
      DiagnosticGroups.registerGroup("deprecated",
          CheckAccessControls.DEPRECATED_NAME,
          CheckAccessControls.DEPRECATED_NAME_REASON,
          CheckAccessControls.DEPRECATED_PROP,
          CheckAccessControls.DEPRECATED_PROP_REASON,
          CheckAccessControls.DEPRECATED_CLASS,
          CheckAccessControls.DEPRECATED_CLASS_REASON);

  public static final DiagnosticGroup UNDERSCORE =
      DiagnosticGroups.registerGroup("underscore",  // undocumented
          CheckJSDocStyle.MUST_BE_PRIVATE,
          CheckJSDocStyle.MUST_HAVE_TRAILING_UNDERSCORE);

  public static final DiagnosticGroup VISIBILITY =
      DiagnosticGroups.registerGroup("visibility",
          CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS,
          CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS,
          CheckAccessControls.BAD_PACKAGE_PROPERTY_ACCESS,
          CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS,
          CheckAccessControls.EXTEND_FINAL_CLASS,
          CheckAccessControls.PRIVATE_OVERRIDE,
          CheckAccessControls.VISIBILITY_MISMATCH,
          CheckAccessControls.CONVENTION_MISMATCH);

  // TODO(tbreisacher): Deprecate this and keep just the "visibility" group.
  public static final DiagnosticGroup ACCESS_CONTROLS =
      DiagnosticGroups.registerGroup("accessControls", VISIBILITY);

  public static final DiagnosticGroup NON_STANDARD_JSDOC =
      DiagnosticGroups.registerGroup(
          "nonStandardJsDocs",
          RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
          RhinoErrorReporter.INVALID_PARAM,
          CheckJSDoc.JSDOC_IN_BLOCK_COMMENT);

  public static final DiagnosticGroup INVALID_CASTS =
      DiagnosticGroups.registerGroup("invalidCasts",
          TypeValidator.INVALID_CAST);

  @Deprecated
  public static final DiagnosticGroup FILEOVERVIEW_JSDOC =
      DiagnosticGroups.registerDeprecatedGroup("fileoverviewTags");

  public static final DiagnosticGroup STRICT_MODULE_DEP_CHECK =
      DiagnosticGroups.registerGroup("strictModuleDepCheck",
          VarCheck.STRICT_MODULE_DEP_ERROR,
          CheckGlobalNames.STRICT_MODULE_DEP_QNAME);

  public static final DiagnosticGroup VIOLATED_MODULE_DEP =
      DiagnosticGroups.registerGroup("violatedModuleDep",
          VarCheck.VIOLATED_MODULE_DEP_ERROR);

  public static final DiagnosticGroup EXTERNS_VALIDATION =
      DiagnosticGroups.registerGroup("externsValidation",
          VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR,
          VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

  public static final DiagnosticGroup AMBIGUOUS_FUNCTION_DECL =
      DiagnosticGroups.registerGroup("ambiguousFunctionDecl",
          StrictModeCheck.BAD_FUNCTION_DECLARATION);

  public static final DiagnosticGroup UNKNOWN_DEFINES =
      DiagnosticGroups.registerGroup("unknownDefines",
          ProcessDefines.UNKNOWN_DEFINE_WARNING);

  public static final DiagnosticGroup TWEAKS =
      DiagnosticGroups.registerGroup("tweakValidation",
          ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_WARNING,
          ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING,
          ProcessTweaks.UNKNOWN_TWEAK_WARNING);

  public static final DiagnosticGroup MISSING_OVERRIDE =
      DiagnosticGroups.registerGroup(
          "missingOverride",
          TypeCheck.HIDDEN_INTERFACE_PROPERTY,
          TypeCheck.HIDDEN_SUPERCLASS_PROPERTY);

  public static final DiagnosticGroup MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup("missingProperties",
          TypeCheck.INEXISTENT_PROPERTY,
          TypeCheck.INEXISTENT_PROPERTY_WITH_SUGGESTION,
          TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

  public static final DiagnosticGroup GLOBALLY_MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup(
          "globallyMissingProperties", TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

  public static final DiagnosticGroup J2CL_CHECKS =
      DiagnosticGroups.registerGroup("j2clChecks",
          J2clChecksPass.J2CL_REFERENCE_EQUALITY);

  public static final DiagnosticGroup MISSING_RETURN =
      DiagnosticGroups.registerGroup("missingReturn",
          CheckMissingReturn.MISSING_RETURN_STATEMENT);

  public static final DiagnosticGroup INTERNET_EXPLORER_CHECKS =
      DiagnosticGroups.registerGroup("internetExplorerChecks",
          RhinoErrorReporter.TRAILING_COMMA);

  public static final DiagnosticGroup UNDEFINED_VARIABLES =
      DiagnosticGroups.registerGroup("undefinedVars",
          VarCheck.UNDEFINED_VAR_ERROR);

  public static final DiagnosticGroup UNDEFINED_NAMES =
      DiagnosticGroups.registerGroup("undefinedNames",
          CheckGlobalNames.UNDEFINED_NAME_WARNING);

  public static final DiagnosticGroup DEBUGGER_STATEMENT_PRESENT =
      DiagnosticGroups.registerGroup("checkDebuggerStatement",
          CheckDebuggerStatement.DEBUGGER_STATEMENT_PRESENT);

  public static final DiagnosticGroup CHECK_REGEXP =
      DiagnosticGroups.registerGroup("checkRegExp",
          CheckRegExp.REGEXP_REFERENCE,
          CheckRegExp.MALFORMED_REGEXP);

  // NOTE(dimvar): it'd be nice to add TypedScopeCreator.ALL_DIAGNOSTICS here,
  // but we would first need to cleanup projects that would break because
  // they set --jscomp_error=checkTypes.
  public static final DiagnosticGroup CHECK_TYPES =
      DiagnosticGroups.registerGroup("checkTypes",
          TypeValidator.ALL_DIAGNOSTICS,
          TypeCheck.ALL_DIAGNOSTICS,
          FunctionTypeBuilder.ALL_DIAGNOSTICS,
          DiagnosticGroups.GLOBAL_THIS);

  // Run the new type inference, but omit many warnings that are not
  // found by the old type checker. This makes migration to NTI more manageable.
  public static final DiagnosticGroup NEW_CHECK_TYPES_COMPATIBILITY_MODE =
      DiagnosticGroups.registerDeprecatedGroup("newCheckTypesCompatibility");

  // no op
  public static final DiagnosticGroup NEW_CHECK_TYPES_EXTRA_CHECKS =
      DiagnosticGroups.registerDeprecatedGroup("newCheckTypesExtraChecks");

  // Part of the new type inference
  public static final DiagnosticGroup NEW_CHECK_TYPES =
      DiagnosticGroups.registerDeprecatedGroup("newCheckTypes");

  public static final DiagnosticGroup NEW_CHECK_TYPES_ALL_CHECKS =
      DiagnosticGroups.registerDeprecatedGroup("newCheckTypesAllChecks");

  public static final DiagnosticGroup TOO_MANY_TYPE_PARAMS =
      DiagnosticGroups.registerGroup("tooManyTypeParams",
          RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);

  @Deprecated
  public static final DiagnosticGroup CHECK_EVENTFUL_OBJECT_DISPOSAL =
      DiagnosticGroups.registerDeprecatedGroup("checkEventfulObjectDisposal");

  public static final DiagnosticGroup OLD_REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("oldReportUnknownTypes", // undocumented
          TypeCheck.UNKNOWN_EXPR_TYPE);

  public static final DiagnosticGroup STRICT_MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup("strictMissingProperties",
          TypeCheck.STRICT_INEXISTENT_PROPERTY,
          TypeCheck.STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION,
          TypeCheck.STRICT_INEXISTENT_UNION_PROPERTY);

  public static final DiagnosticGroup STRICT_PRIMITIVE_OPERATORS =
      DiagnosticGroups.registerGroup("strictPrimitiveOperators",
          TypeValidator.INVALID_OPERAND_TYPE);

  public static final DiagnosticGroup STRICT_CHECK_TYPES =
      DiagnosticGroups.registerGroup("strictCheckTypes",
          STRICT_MISSING_PROPERTIES,
          STRICT_PRIMITIVE_OPERATORS);

  public static final DiagnosticGroup REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("reportUnknownTypes",
          TypeCheck.UNKNOWN_EXPR_TYPE);

  public static final DiagnosticGroup CHECK_VARIABLES =
      DiagnosticGroups.registerGroup("checkVars",
          VarCheck.UNDEFINED_VAR_ERROR,
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          VariableReferenceCheck.EARLY_REFERENCE,
          VariableReferenceCheck.REDECLARED_VARIABLE);

  public static final DiagnosticGroup CHECK_USELESS_CODE =
      DiagnosticGroups.registerGroup("uselessCode",
          CheckSideEffects.USELESS_CODE_ERROR,
          CheckUnreachableCode.UNREACHABLE_CODE);

  public static final DiagnosticGroup CONST =
      DiagnosticGroups.registerGroup("const",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE,
          ConstCheck.CONST_REASSIGNED_VALUE_ERROR);

  static final DiagnosticGroup ACCESS_CONTROLS_CONST =
      DiagnosticGroups.registerGroup("accessControlsConst",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE);

  public static final DiagnosticGroup CONSTANT_PROPERTY =
      DiagnosticGroups.registerGroup("constantProperty",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE);

  public static final DiagnosticGroup TYPE_INVALIDATION =
      DiagnosticGroups.registerGroup("typeInvalidation",
          DisambiguateProperties.Warnings.INVALIDATION,
          DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE);

  public static final DiagnosticGroup DUPLICATE_VARS =
      DiagnosticGroups.registerGroup("duplicate",
          CollapseProperties.NAMESPACE_REDEFINED_WARNING,
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          TypeValidator.DUP_VAR_DECLARATION,
          TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH,
          TypeCheck.FUNCTION_MASKS_VARIABLE,
          VariableReferenceCheck.REDECLARED_VARIABLE);

  public static final DiagnosticGroup ES3 =
      DiagnosticGroups.registerGroup("es3",
          RhinoErrorReporter.INVALID_ES3_PROP_NAME,
          RhinoErrorReporter.TRAILING_COMMA);

  // In the conversion from ES5 to ES6, we remove the strict check that asserts functions
  // must be declared at the top of a new scope or immediately within the declaration of another
  // function
  static final DiagnosticGroup ES5_STRICT_UNCOMMON =
      DiagnosticGroups.registerGroup(
          "es5StrictUncommon",
          RhinoErrorReporter.INVALID_OCTAL_LITERAL,
          RhinoErrorReporter.DUPLICATE_PARAM,
          StrictModeCheck.USE_OF_WITH,
          StrictModeCheck.EVAL_DECLARATION,
          StrictModeCheck.EVAL_ASSIGNMENT,
          StrictModeCheck.ARGUMENTS_DECLARATION,
          StrictModeCheck.ARGUMENTS_ASSIGNMENT,
          StrictModeCheck.DELETE_VARIABLE,
          StrictModeCheck.DUPLICATE_OBJECT_KEY);

  static final DiagnosticGroup ES5_STRICT_REFLECTION =
      DiagnosticGroups.registerGroup("es5StrictReflection",
          StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN,
          StrictModeCheck.ARGUMENTS_CALLER_FORBIDDEN,
          StrictModeCheck.FUNCTION_CALLER_FORBIDDEN,
          StrictModeCheck.FUNCTION_ARGUMENTS_PROP_FORBIDDEN);

  public static final DiagnosticGroup ES5_STRICT =
      DiagnosticGroups.registerGroup("es5Strict",
          ES5_STRICT_UNCOMMON,
          ES5_STRICT_REFLECTION);

  public static final DiagnosticGroup MISSING_PROVIDE =
      DiagnosticGroups.registerGroup(
          "missingProvide", CheckProvides.MISSING_PROVIDE_WARNING, MISSING_MODULE_OR_PROVIDE);

  public static final DiagnosticGroup UNRECOGNIZED_TYPE_ERROR =
      DiagnosticGroups.registerGroup("unrecognizedTypeError", // undocumented
          RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);

  public static final DiagnosticGroup MISSING_REQUIRE =
      DiagnosticGroups.registerGroup(
          "missingRequire", CheckMissingAndExtraRequires.MISSING_REQUIRE_WARNING);

  /**
   * A set of diagnostics expected when parsing and type checking partial programs. Useful for clutz
   * (tool that extracts TypeScript definitions from JS code).
   */
  public static final DiagnosticGroup MISSING_SOURCES_WARNINGS =
      DiagnosticGroups.registerGroup(
          "missingSourcesWarnings",
          REPORT_UNKNOWN_TYPES,
          UNDEFINED_NAMES,
          UNDEFINED_VARIABLES,
          MISSING_PROVIDE,
          DiagnosticGroup.forType(FunctionTypeBuilder.RESOLVED_TAG_EMPTY),
          DiagnosticGroup.forType(ProcessClosurePrimitives.MISSING_PROVIDE_ERROR),
          MISSING_PROPERTIES,
          // triggered by typedefs with missing types
          DUPLICATE_VARS,
          // caused by a define depending on another define that's missing
          DiagnosticGroup.forType(ProcessDefines.INVALID_DEFINE_INIT_ERROR),
          DiagnosticGroup.forType(Es6ExternsCheck.MISSING_ES6_EXTERNS),
          // ES Module imports of files not reachable from this partial program.
          DiagnosticGroup.forType(ModuleLoader.LOAD_WARNING));

  public static final DiagnosticGroup STRICT_MISSING_REQUIRE =
      DiagnosticGroups.registerGroup(
          "strictMissingRequire",
          CheckMissingAndExtraRequires.MISSING_REQUIRE_WARNING,
          CheckMissingAndExtraRequires.MISSING_REQUIRE_FOR_GOOG_SCOPE,
          CheckMissingAndExtraRequires.MISSING_REQUIRE_STRICT_WARNING);

  public static final DiagnosticGroup STRICT_REQUIRES =
      DiagnosticGroups.registerGroup(
          "legacyGoogScopeRequire",
          CheckMissingAndExtraRequires.MISSING_REQUIRE_FOR_GOOG_SCOPE,
          CheckMissingAndExtraRequires.EXTRA_REQUIRE_WARNING);

  public static final DiagnosticGroup EXTRA_REQUIRE =
      DiagnosticGroups.registerGroup(
          "extraRequire", CheckMissingAndExtraRequires.EXTRA_REQUIRE_WARNING);

  @GwtIncompatible("java.util.regex")
  public static final DiagnosticGroup MISSING_GETCSSNAME =
      DiagnosticGroups.registerGroup("missingGetCssName",
          CheckMissingGetCssName.MISSING_GETCSSNAME);

  @GwtIncompatible("JsMessage")
  public static final DiagnosticGroup DUPLICATE_MESSAGE =
      DiagnosticGroups.registerGroup("duplicateMessage",
          JsMessageVisitor.MESSAGE_DUPLICATE_KEY);

  @GwtIncompatible("JsMessage")
  public static final DiagnosticGroup MESSAGE_DESCRIPTIONS =
      DiagnosticGroups.registerGroup("msgDescriptions",
          JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);

  /**
   * Warnings that only apply to people who use MSG_ to denote
   * messages. Note that this doesn't include warnings about
   * proper use of goog.getMsg
   */
  @GwtIncompatible("JsMessage")
  public static final DiagnosticGroup MSG_CONVENTIONS =
      DiagnosticGroups.registerGroup("messageConventions", // undocumented
          JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION,
          JsMessageVisitor.MESSAGE_HAS_NO_TEXT,
          JsMessageVisitor.MESSAGE_TREE_MALFORMED,
          JsMessageVisitor.MESSAGE_HAS_NO_VALUE,
          JsMessageVisitor.MESSAGE_DUPLICATE_KEY,
          JsMessageVisitor.MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX);

  public static final DiagnosticGroup MISPLACED_TYPE_ANNOTATION =
      DiagnosticGroups.registerGroup("misplacedTypeAnnotation",
          CheckJSDoc.ARROW_FUNCTION_AS_CONSTRUCTOR,
          CheckJSDoc.DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL,
          CheckJSDoc.DISALLOWED_MEMBER_JSDOC,
          CheckJSDoc.INVALID_NO_SIDE_EFFECT_ANNOTATION,
          CheckJSDoc.INVALID_MODIFIES_ANNOTATION,
          CheckJSDoc.MISPLACED_ANNOTATION,
          CheckJSDoc.MISPLACED_MSG_ANNOTATION);

  public static final DiagnosticGroup MISPLACED_MSG_ANNOTATION =
      DiagnosticGroups.registerGroup("misplacedMsgAnnotation",
          CheckJSDoc.MISPLACED_MSG_ANNOTATION);

  public static final DiagnosticGroup MISPLACED_SUPPRESS =
      DiagnosticGroups.registerGroup("misplacedSuppress",
          CheckJSDoc.MISPLACED_SUPPRESS);

  public static final DiagnosticGroup SUSPICIOUS_CODE =
      DiagnosticGroups.registerGroup(
          "suspiciousCode",
          CheckDuplicateCase.DUPLICATE_CASE,
          CheckSuspiciousCode.SUSPICIOUS_SEMICOLON,
          CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN,
          CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR,
          CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND,
          CheckSuspiciousCode.SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR,
          TypeCheck.DETERMINISTIC_TEST,
          ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT);

  public static final DiagnosticGroup FUNCTION_PARAMS =
      DiagnosticGroups.registerGroup(
          "functionParams",
          FunctionTypeBuilder.INEXISTENT_PARAM,
          FunctionTypeBuilder.OPTIONAL_ARG_AT_END);

  public static final DiagnosticGroup DEPRECATED_ANNOTATIONS =
      DiagnosticGroups.registerGroup("deprecatedAnnotations",
          CheckJSDoc.ANNOTATION_DEPRECATED);

  public static final DiagnosticGroup UNUSED_PRIVATE_PROPERTY =
      DiagnosticGroups.registerGroup("unusedPrivateMembers",
          CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY);

  public static final DiagnosticGroup UNUSED_LOCAL_VARIABLE =
      DiagnosticGroups.registerGroup("unusedLocalVariables",
          VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT);

  public static final DiagnosticGroup MISSING_CONST_PROPERTY =
      DiagnosticGroups.registerGroup(
          "jsdocMissingConst", CheckConstPrivateProperties.MISSING_CONST_PROPERTY);

  public static final DiagnosticGroup JSDOC_MISSING_TYPE =
      DiagnosticGroups.registerGroup("jsdocMissingType",
              RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING);

  public static final DiagnosticGroup UNNECESSARY_ESCAPE =
      DiagnosticGroups.registerGroup("unnecessaryEscape", RhinoErrorReporter.UNNECESSARY_ESCAPE);

  // Warnings reported by the linter. If you enable these as errors in your build targets,
  // the JS Compiler team will break your build and not rollback.
  public static final DiagnosticGroup LINT_CHECKS =
      DiagnosticGroups.registerGroup(
          "lintChecks", // undocumented
          CheckJSDocStyle.ALL_DIAGNOSTICS,
          new DiagnosticGroup(
              CheckEmptyStatements.USELESS_EMPTY_STATEMENT,
              CheckEnums.COMPUTED_PROP_NAME_IN_ENUM,
              CheckEnums.DUPLICATE_ENUM_VALUE,
              CheckEnums.ENUM_PROP_NOT_CONSTANT,
              CheckEnums.SHORTHAND_ASSIGNMENT_IN_ENUM,
              CheckEs6ModuleFileStructure.MUST_COME_BEFORE,
              CheckEs6Modules.DUPLICATE_IMPORT,
              CheckEs6Modules.NO_DEFAULT_EXPORT,
              CheckNoMutatedEs6Exports.MUTATED_EXPORT,
              // TODO(tbreisacher): Consider moving the CheckInterfaces warnings into the
              // checkTypes DiagnosticGroup
              CheckInterfaces.INTERFACE_FUNCTION_NOT_EMPTY,
              CheckInterfaces.INTERFACE_SHOULD_NOT_TAKE_ARGS,
              CheckMissingSemicolon.MISSING_SEMICOLON,
              CheckNullabilityModifiers.MISSING_NULLABILITY_MODIFIER_JSDOC,
              CheckNullabilityModifiers.NULL_MISSING_NULLABILITY_MODIFIER_JSDOC,
              CheckNullabilityModifiers.REDUNDANT_NULLABILITY_MODIFIER_JSDOC,
              CheckPrimitiveAsObject.NEW_PRIMITIVE_OBJECT,
              CheckPrimitiveAsObject.PRIMITIVE_OBJECT_DECLARATION,
              CheckPrototypeProperties.ILLEGAL_PROTOTYPE_MEMBER,
              CheckRequiresAndProvidesSorted.DUPLICATE_REQUIRE,
              CheckRequiresAndProvidesSorted.REQUIRES_NOT_SORTED,
              CheckRequiresAndProvidesSorted.PROVIDES_NOT_SORTED,
              CheckRequiresAndProvidesSorted.PROVIDES_AFTER_REQUIRES,
              CheckUnusedLabels.UNUSED_LABEL,
              CheckUselessBlocks.USELESS_BLOCK,
              ClosureCheckModule.DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE,
              ClosureCheckModule.GOOG_MODULE_IN_NON_MODULE,
              ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION,
              ClosureCheckModule.LET_GOOG_REQUIRE,
              ClosureCheckModule.JSDOC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME,
              ClosureCheckModule.JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
              ClosureCheckModule.REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME,
              ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
              ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE,
              RhinoErrorReporter.JSDOC_MISSING_BRACES_WARNING));

  static final DiagnosticGroup STRICT_MODULE_CHECKS =
      DiagnosticGroups.registerGroup(
          "strictModuleChecks",
          ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE,
          ClosureCheckModule.LET_GOOG_REQUIRE,
          ClosureCheckModule.JSDOC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME,
          ClosureCheckModule.JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
          ClosureCheckModule.REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME,
          ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

  // A diagnostic group appears to be enabled if any of the DiagnosticTypes it
  // contains are enabled. We need this group so we can distinguish whether
  // ANALYZER_CHECKS was directly enabled or only appears to be, because
  // UNUSED_PRIVATE_PROPERTY was enabled.
  static final DiagnosticGroup ANALYZER_CHECKS_INTERNAL =
      DiagnosticGroups.registerGroup("analyzerChecksInternal", // undocumented
          CheckArrayWithGoogObject.ARRAY_PASSED_TO_GOOG_OBJECT,
          CheckNullableReturn.NULLABLE_RETURN,
          CheckNullableReturn.NULLABLE_RETURN_WITH_NAME,
          ImplicitNullabilityCheck.IMPLICITLY_NULLABLE_JSDOC);

  // Similar to the lintChecks group above, but includes things that cannot be done on a single
  // file at a time, for example because they require typechecking. If you enable these as errors
  // in your build targets, the JS Compiler team will break your build and not rollback.
  public static final DiagnosticGroup ANALYZER_CHECKS =
      DiagnosticGroups.registerGroup(
          "analyzerChecks", // undocumented
          ANALYZER_CHECKS_INTERNAL,
          UNUSED_PRIVATE_PROPERTY,
          MISSING_CONST_PROPERTY);

  public static final DiagnosticGroup USE_OF_GOOG_BASE =
      DiagnosticGroups.registerGroup("useOfGoogBase",
          ProcessClosurePrimitives.USE_OF_GOOG_BASE);

  public static final DiagnosticGroup CLOSURE_DEP_METHOD_USAGE_CHECKS =
      DiagnosticGroups.registerGroup("closureDepMethodUsageChecks", INVALID_CLOSURE_CALL_ERROR);

  // This group exists so that generated code can suppress these
  // warnings. Not for general use. These diagnostics will most likely
  // be moved to the suspiciousCode group.
  static {
    DiagnosticGroups.registerGroup("transitionalSuspiciousCodeWarnings",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR,
        PeepholeFoldConstants.NEGATING_A_NON_NUMBER_ERROR,
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  // This diagnostic group is intentionally absent in ParserConfig.properties.
  // Conformance checks are supposed to be enforced project-wide, so we don't
  // allow suppressions on individual functions.
  // In the future, we may carve out a subset of the conformance checks that is
  // OK to suppress.
  // For now, the only way to suppress a check at a granularity smaller than
  // the file level is by using a whitelist file.
  @GwtIncompatible("Conformance")
  public static final DiagnosticGroup CONFORMANCE_VIOLATIONS =
      DiagnosticGroups.registerGroup(
          "conformanceViolations",
          CheckConformance.CONFORMANCE_VIOLATION,
          CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

  public static final DiagnosticGroup LATE_PROVIDE =
      DiagnosticGroups.registerGroup(
          "lateProvide", // undocumented
          ProcessClosurePrimitives.LATE_PROVIDE_ERROR);

  public static final DiagnosticGroup MISSING_POLYFILL =
      DiagnosticGroups.registerGroup(
          "missingPolyfill", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

  public static final DiagnosticGroup POLYMER =
      DiagnosticGroups.registerGroup("polymer", PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID);

  // For internal use only, so there are no constants for these groups.
  static {
    DiagnosticGroups.registerGroup(
        "invalidProvide", ProcessClosurePrimitives.INVALID_PROVIDE_ERROR);

    DiagnosticGroups.registerGroup("es6Typed", RhinoErrorReporter.MISPLACED_TYPE_SYNTAX);

    DiagnosticGroups.registerDeprecatedGroup("duplicateZipContents");

    // Only exposed for tsickle-generated code.
    DiagnosticGroups.registerGroup(
        "googModuleExportNotAStatement", ClosureCheckModule.EXPORT_NOT_A_STATEMENT);
  }

  /** Adds warning levels by name. */
  public void setWarningLevel(CompilerOptions options, String name, CheckLevel level) {
    DiagnosticGroup group = forName(name);
    Preconditions.checkNotNull(group, "No warning class for name: %s", name);
    options.setWarningLevel(group, level);
  }
}
