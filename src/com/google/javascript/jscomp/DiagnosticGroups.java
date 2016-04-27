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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckForInOverArray;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;

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

  public static final Set<String> wildcardExcludedGroups = ImmutableSet.of(
      "reportUnknownTypes", "analyzerChecks", "oldReportUnknownTypes");

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
  // to parser/ParserConfig.properties
  static final String DIAGNOSTIC_GROUP_NAMES =
      "accessControls, ambiguousFunctionDecl, checkEventfulObjectDisposal, "
      + "checkRegExp, checkTypes, checkVars, "
      + "conformanceViolations, const, constantProperty, deprecated, "
      + "deprecatedAnnotations, duplicateMessage, es3, "
      + "es5Strict, externsValidation, fileoverviewTags, globalThis, "
      + "internetExplorerChecks, invalidCasts, "
      + "misplacedTypeAnnotation, missingGetCssName, missingProperties, "
      + "missingProvide, missingRequire, missingReturn, msgDescriptions, "
      + "newCheckTypes, nonStandardJsDocs, reportUnknownTypes, "
      + "suspiciousCode, strictModuleDepCheck, typeInvalidation, "
      + "undefinedNames, undefinedVars, unknownDefines, "
      + "unusedLocalVariables, unusedPrivateMembers, uselessCode, "
      + "useOfGoogBase, underscore, visibility";

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

  public static final DiagnosticGroup ACCESS_CONTROLS =
      DiagnosticGroups.registerGroup("accessControls",
          DEPRECATED, VISIBILITY);

  public static final DiagnosticGroup NON_STANDARD_JSDOC =
      DiagnosticGroups.registerGroup("nonStandardJsDocs",
          RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
          RhinoErrorReporter.INVALID_PARAM,
          RhinoErrorReporter.JSDOC_IN_BLOCK_COMMENT);

  public static final DiagnosticGroup INVALID_CASTS =
      DiagnosticGroups.registerGroup("invalidCasts",
          TypeValidator.INVALID_CAST);

  @Deprecated
  public static final DiagnosticGroup INFERRED_CONST_CHECKS =
      DiagnosticGroups.registerDeprecatedGroup("inferredConstCheck");

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
          VariableReferenceCheck.AMBIGUOUS_FUNCTION_DECL,
          StrictModeCheck.BAD_FUNCTION_DECLARATION);

  public static final DiagnosticGroup UNKNOWN_DEFINES =
      DiagnosticGroups.registerGroup("unknownDefines",
          ProcessDefines.UNKNOWN_DEFINE_WARNING);

  public static final DiagnosticGroup TWEAKS =
      DiagnosticGroups.registerGroup("tweakValidation",
          ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_WARNING,
          ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING,
          ProcessTweaks.UNKNOWN_TWEAK_WARNING);

  public static final DiagnosticGroup MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup("missingProperties",
          TypeCheck.INEXISTENT_PROPERTY,
          TypeCheck.INEXISTENT_PROPERTY_WITH_SUGGESTION,
          TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

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
  public static final DiagnosticGroup OLD_CHECK_TYPES =
      DiagnosticGroups.registerGroup("oldCheckTypes",  // undocumented
          TypeValidator.ALL_DIAGNOSTICS,
          TypeCheck.ALL_DIAGNOSTICS);

  // Part of the new type inference
  public static final DiagnosticGroup NEW_CHECK_TYPES =
      DiagnosticGroups.registerGroup("newCheckTypes",
          JSTypeCreatorFromJSDoc.ALL_DIAGNOSTICS,
          GlobalTypeInfo.ALL_DIAGNOSTICS,
          NewTypeInference.ALL_DIAGNOSTICS);

  public static final DiagnosticGroup CHECK_TYPES =
      DiagnosticGroups.registerGroup("checkTypes",
          OLD_CHECK_TYPES,
          NEW_CHECK_TYPES);

  public static final DiagnosticGroup NEW_CHECK_TYPES_ALL_CHECKS =
      DiagnosticGroups.registerGroup("newCheckTypesAllChecks",
          JSTypeCreatorFromJSDoc.CONFLICTING_SHAPE_TYPE,
          NewTypeInference.NULLABLE_DEREFERENCE);

  static {
      // Warnings that are absent in closure library
      DiagnosticGroups.registerGroup("newCheckTypesClosureClean",
          JSTypeCreatorFromJSDoc.CONFLICTING_EXTENDED_TYPE,
          JSTypeCreatorFromJSDoc.CONFLICTING_IMPLEMENTED_TYPE,
          JSTypeCreatorFromJSDoc.DICT_IMPLEMENTS_INTERF,
          JSTypeCreatorFromJSDoc.EXTENDS_NON_OBJECT,
          JSTypeCreatorFromJSDoc.EXTENDS_NOT_ON_CTOR_OR_INTERF,
          JSTypeCreatorFromJSDoc.IMPLEMENTS_WITHOUT_CONSTRUCTOR,
          JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE,
          JSTypeCreatorFromJSDoc.UNION_IS_UNINHABITABLE,
          GlobalTypeInfo.ANONYMOUS_NOMINAL_TYPE,
          GlobalTypeInfo.CANNOT_INIT_TYPEDEF,
          GlobalTypeInfo.CANNOT_OVERRIDE_FINAL_METHOD,
          GlobalTypeInfo.CONST_WITHOUT_INITIALIZER,
//           GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE,
          GlobalTypeInfo.CTOR_IN_DIFFERENT_SCOPE,
          GlobalTypeInfo.DUPLICATE_JSDOC,
          GlobalTypeInfo.DUPLICATE_PROP_IN_ENUM,
          GlobalTypeInfo.EXPECTED_CONSTRUCTOR,
          GlobalTypeInfo.EXPECTED_INTERFACE,
          GlobalTypeInfo.INEXISTENT_PARAM,
          GlobalTypeInfo.INTERFACE_METHOD_NOT_IMPLEMENTED,
//           GlobalTypeInfo.INVALID_PROP_OVERRIDE,
          GlobalTypeInfo.LENDS_ON_BAD_TYPE,
          GlobalTypeInfo.MALFORMED_ENUM,
          GlobalTypeInfo.MISPLACED_CONST_ANNOTATION,
          GlobalTypeInfo.ONE_TYPE_FOR_MANY_VARS,
//           GlobalTypeInfo.REDECLARED_PROPERTY,
          GlobalTypeInfo.STRUCTDICT_WITHOUT_CTOR,
          GlobalTypeInfo.SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES,
          GlobalTypeInfo.UNDECLARED_NAMESPACE,
          GlobalTypeInfo.UNKNOWN_OVERRIDE,
          GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME,
          NewTypeInference.ASSERT_FALSE,
          NewTypeInference.CANNOT_BIND_CTOR,
          NewTypeInference.CONST_REASSIGNED,
          NewTypeInference.CONSTRUCTOR_NOT_CALLABLE,
          NewTypeInference.CROSS_SCOPE_GOTCHA,
//           NewTypeInference.FAILED_TO_UNIFY,
//           NewTypeInference.FORIN_EXPECTS_OBJECT,
          NewTypeInference.FORIN_EXPECTS_STRING_KEY,
//           NewTypeInference.GLOBAL_THIS,
//           NewTypeInference.GOOG_BIND_EXPECTS_FUNCTION,
          NewTypeInference.ILLEGAL_OBJLIT_KEY,
//           NewTypeInference.ILLEGAL_PROPERTY_ACCESS,
//           NewTypeInference.ILLEGAL_PROPERTY_CREATION,
          NewTypeInference.IN_USED_WITH_STRUCT,
//           NewTypeInference.INEXISTENT_PROPERTY,
//           NewTypeInference.INVALID_ARGUMENT_TYPE,
//           NewTypeInference.INVALID_CAST,
          NewTypeInference.INVALID_INDEX_TYPE,
          NewTypeInference.INVALID_INFERRED_RETURN_TYPE,
          NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE,
//           NewTypeInference.INVALID_OPERAND_TYPE,
          NewTypeInference.INVALID_THIS_TYPE_IN_BIND,
          NewTypeInference.MISSING_RETURN_STATEMENT,
//           NewTypeInference.MISTYPED_ASSIGN_RHS,
//           NewTypeInference.NOT_A_CONSTRUCTOR,
//           NewTypeInference.NOT_CALLABLE,
//           NewTypeInference.NOT_UNIQUE_INSTANTIATION,
//           NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY,
//           NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT,
//           NewTypeInference.RETURN_NONDECLARED_TYPE,
//           NewTypeInference.WRONG_ARGUMENT_COUNT,
          NewTypeInference.UNKNOWN_ASSERTION_TYPE,
          NewTypeInference.UNKNOWN_TYPEOF_VALUE);
  }

  public static final DiagnosticGroup CHECK_EVENTFUL_OBJECT_DISPOSAL =
      DiagnosticGroups.registerGroup("checkEventfulObjectDisposal",
          CheckEventfulObjectDisposal.EVENTFUL_OBJECT_NOT_DISPOSED,
          CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL,
          CheckEventfulObjectDisposal.OVERWRITE_PRIVATE_EVENTFUL_OBJECT,
          CheckEventfulObjectDisposal.UNLISTEN_WITH_ANONBOUND);

  public static final DiagnosticGroup OLD_REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("oldReportUnknownTypes", // undocumented
          TypeCheck.UNKNOWN_EXPR_TYPE);

  public static final DiagnosticGroup REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("reportUnknownTypes",
          TypeCheck.UNKNOWN_EXPR_TYPE,
          NewTypeInference.UNKNOWN_EXPR_TYPE);

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
          ConstCheck.CONST_REASSIGNED_VALUE_ERROR,
          NewTypeInference.CONST_REASSIGNED,
          NewTypeInference.CONST_PROPERTY_REASSIGNED);

  public static final DiagnosticGroup CONSTANT_PROPERTY =
      DiagnosticGroups.registerGroup("constantProperty",
          CheckAccessControls.CONST_PROPERTY_DELETED,
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE,
          NewTypeInference.CONST_PROPERTY_REASSIGNED);

  public static final DiagnosticGroup TYPE_INVALIDATION =
      DiagnosticGroups.registerGroup("typeInvalidation",
          DisambiguateProperties.Warnings.INVALIDATION,
          DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE);

  public static final DiagnosticGroup DUPLICATE_VARS =
      DiagnosticGroups.registerGroup("duplicate",
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          TypeValidator.DUP_VAR_DECLARATION,
          TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH,
          VariableReferenceCheck.REDECLARED_VARIABLE,
          GlobalTypeInfo.REDECLARED_PROPERTY);

  public static final DiagnosticGroup ES3 =
      DiagnosticGroups.registerGroup("es3",
          RhinoErrorReporter.INVALID_ES3_PROP_NAME,
          RhinoErrorReporter.TRAILING_COMMA);

  static final DiagnosticGroup ES5_STRICT_UNCOMMON =
      DiagnosticGroups.registerGroup("es5StrictUncommon",
          RhinoErrorReporter.INVALID_OCTAL_LITERAL,
          RhinoErrorReporter.DUPLICATE_PARAM,
          StrictModeCheck.USE_OF_WITH,
          StrictModeCheck.EVAL_DECLARATION,
          StrictModeCheck.EVAL_ASSIGNMENT,
          StrictModeCheck.ARGUMENTS_DECLARATION,
          StrictModeCheck.ARGUMENTS_ASSIGNMENT,
          StrictModeCheck.DELETE_VARIABLE,
          StrictModeCheck.DUPLICATE_OBJECT_KEY,
          StrictModeCheck.BAD_FUNCTION_DECLARATION);

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
      DiagnosticGroups.registerGroup("missingProvide",
          CheckProvides.MISSING_PROVIDE_WARNING,
          ClosureRewriteModule.MISSING_MODULE,
          ClosureRewriteModule.MISSING_MODULE_OR_PROVIDE);

  public static final DiagnosticGroup MISSING_REQUIRE =
      DiagnosticGroups.registerGroup("missingRequire",
          CheckRequiresForConstructors.MISSING_REQUIRE_WARNING);

  public static final DiagnosticGroup EXTRA_REQUIRE =
      DiagnosticGroups.registerGroup("extraRequire",
          CheckRequiresForConstructors.DUPLICATE_REQUIRE_WARNING,
          CheckRequiresForConstructors.EXTRA_REQUIRE_WARNING);

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
          CheckJSDoc.DISALLOWED_MEMBER_JSDOC,
          CheckJSDoc.MISPLACED_ANNOTATION,
          CheckJSDoc.MISPLACED_MSG_ANNOTATION);

  public static final DiagnosticGroup SUSPICIOUS_CODE =
      DiagnosticGroups.registerGroup(
          "suspiciousCode",
          CheckDuplicateCase.DUPLICATE_CASE,
          CheckSuspiciousCode.SUSPICIOUS_SEMICOLON,
          CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN,
          CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR,
          CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND,
          CheckSuspiciousCode.SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR,
          TypeCheck.DETERMINISTIC_TEST);

  public static final DiagnosticGroup DEPRECATED_ANNOTATIONS =
      DiagnosticGroups.registerGroup("deprecatedAnnotations",
          CheckJSDoc.ANNOTATION_DEPRECATED);

  public static final DiagnosticGroup UNUSED_PRIVATE_PROPERTY =
      DiagnosticGroups.registerGroup("unusedPrivateMembers",
          CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY);

  public static final DiagnosticGroup UNUSED_LOCAL_VARIABLE =
      DiagnosticGroups.registerGroup("unusedLocalVariables",
          VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT);

  // These checks are not intended to be enabled as errors. It is
  // recommended that you think of them as "linter" warnings that
  // provide optional suggestions.
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
              // TODO(tbreisacher): Consider moving the CheckInterfaces warnings into the
              // checkTypes DiagnosticGroup
              CheckInterfaces.INTERFACE_FUNCTION_NOT_EMPTY,
              CheckInterfaces.INTERFACE_SHOULD_NOT_TAKE_ARGS,
              CheckMissingSemicolon.MISSING_SEMICOLON,
              CheckPrimitiveAsObject.NEW_PRIMITIVE_OBJECT,
              CheckPrimitiveAsObject.PRIMITIVE_OBJECT_DECLARATION,
              CheckPrototypeProperties.ILLEGAL_PROTOTYPE_MEMBER,
              CheckRequiresAndProvidesSorted.REQUIRES_NOT_SORTED,
              CheckRequiresAndProvidesSorted.PROVIDES_NOT_SORTED,
              CheckRequiresAndProvidesSorted.PROVIDES_AFTER_REQUIRES,
              // TODO(tbreisacher): Move MISSING_REQUIRE_CALL_WARNING to missingRequire group
              // as soon as projects that enable that group are fixed.
              CheckRequiresForConstructors.MISSING_REQUIRE_CALL_WARNING,
              CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY,
              CheckUnusedLabels.UNUSED_LABEL,
              CheckUselessBlocks.USELESS_BLOCK,
              ClosureCheckModule.LET_GOOG_REQUIRE,
              ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME,
              ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME,
              ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE,
              RhinoErrorReporter.JSDOC_MISSING_BRACES_WARNING,
              RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING,
              RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS,
              VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT));

  // Similar to the lintChecks group above, but includes things that cannot be done on a single
  // file at a time, for example because they require typechecking.
  public static final DiagnosticGroup ANALYZER_CHECKS =
      DiagnosticGroups.registerGroup("analyzerChecks", // undocumented
          CheckForInOverArray.FOR_IN_OVER_ARRAY,
          CheckForInOverArray.ARRAY_PASSED_TO_GOOG_OBJECT,
          CheckNullableReturn.NULLABLE_RETURN,
          CheckNullableReturn.NULLABLE_RETURN_WITH_NAME,
          ImplicitNullabilityCheck.IMPLICITLY_NULLABLE_JSDOC);

  public static final DiagnosticGroup USE_OF_GOOG_BASE =
      DiagnosticGroups.registerGroup("useOfGoogBase",
          ProcessClosurePrimitives.USE_OF_GOOG_BASE);

  public static final DiagnosticGroup CLOSURE_DEP_METHOD_USAGE_CHECKS =
      DiagnosticGroups.registerGroup("closureDepMethodUsageChecks",
          ProcessClosurePrimitives.INVALID_CLOSURE_CALL_ERROR);

  // This group exists so that generated code can suppress these
  // warnings. Not for general use. These diagnostics will most likely
  // be moved to the suspiciousCode group.
  static {
    DiagnosticGroups.registerGroup("transitionalSuspiciousCodeWarnings",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR,
        PeepholeFoldConstants.NEGATING_A_NON_NUMBER_ERROR,
        PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE,
        PeepholeFoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS,
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  @GwtIncompatible("Conformance")
  public static final DiagnosticGroup CONFORMANCE_VIOLATIONS =
      DiagnosticGroups.registerGroup("conformanceViolations",
          CheckConformance.CONFORMANCE_VIOLATION,
          CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

  public static final DiagnosticGroup LATE_PROVIDE =
      DiagnosticGroups.registerGroup(
          "lateProvide", // undocumented
          ProcessClosurePrimitives.LATE_PROVIDE_ERROR,
          ClosureRewriteModule.LATE_PROVIDE_ERROR);

  // For internal use only, so there are no constants for these groups.
  static {
    DiagnosticGroups.registerGroup("invalidProvide",
        ProcessClosurePrimitives.INVALID_PROVIDE_ERROR);

    DiagnosticGroups.registerGroup("es6Typed",
        RhinoErrorReporter.MISPLACED_TYPE_SYNTAX);

    DiagnosticGroups.registerGroup("duplicateZipContents",
        SourceFile.DUPLICATE_ZIP_CONTENTS);

    DiagnosticGroups.registerDeprecatedGroup("unnecessaryCasts");
  }

  /**
   * Adds warning levels by name.
   */
  void setWarningLevel(CompilerOptions options,
      String name, CheckLevel level) {
    DiagnosticGroup group = forName(name);
    Preconditions.checkNotNull(group, "No warning class for name: %s", name);
    options.setWarningLevel(group, level);
  }
}
