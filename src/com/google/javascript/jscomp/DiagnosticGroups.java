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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;

import java.util.Map;

/**
 * Named groups of DiagnosticTypes exposed by Compiler.
 * @author nicksantos@google.com (Nick Santos)
 */
public class DiagnosticGroups {
  static final DiagnosticType UNUSED =
      DiagnosticType.warning("JSC_UNUSED", "{0}");

  public DiagnosticGroups() {}

  private static final Map<String, DiagnosticGroup> groupsByName =
      Maps.newHashMap();

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
  protected Map<String, DiagnosticGroup> getRegisteredGroups() {
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
      "accessControls, ambiguousFunctionDecl, checkEventfulObjectDisposal, " +
      "checkRegExp, checkStructDictInheritance, checkTypes, checkVars, " +
      "conformanceViolations, " +
      "const, constantProperty, deprecated, duplicateMessage, es3, " +
      "es5Strict, externsValidation, fileoverviewTags, globalThis, " +
      "inferredConstCheck, " +
      "internetExplorerChecks, invalidCasts, misplacedTypeAnnotation, " +
      "missingGetCssName, missingProperties, " +
      "missingProvide, missingRequire, missingReturn," +
      "newCheckTypes, nonStandardJsDocs, reportUnknownTypes, suspiciousCode, " +
      "strictModuleDepCheck, typeInvalidation, " +
      "undefinedNames, undefinedVars, unknownDefines, uselessCode, " +
      "useOfGoogBase, visibility";

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

  public static final DiagnosticGroup UNNECESSARY_CASTS =
      DiagnosticGroups.registerGroup("unnecessaryCasts",
          TypeValidator.UNNECESSARY_CAST);

  public static final DiagnosticGroup INFERRED_CONST_CHECKS =
      DiagnosticGroups.registerGroup("inferredConstCheck",
          TypedScopeCreator.CANNOT_INFER_CONST_TYPE);

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

  public static final DiagnosticGroup CHECK_TYPES =
      DiagnosticGroups.registerGroup("checkTypes",
          TypeValidator.ALL_DIAGNOSTICS,
          TypeCheck.ALL_DIAGNOSTICS);

  // Part of the new type inference (under development)
  public static final DiagnosticGroup NEW_CHECK_TYPES =
      DiagnosticGroups.registerGroup("newCheckTypes",
          GlobalTypeInfo.ALL_DIAGNOSTICS,
          NewTypeInference.ALL_DIAGNOSTICS);

  static {
      DiagnosticGroups.registerGroup("newCheckTypesWarningsOverload",
          JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION,
          NewTypeInference.NULLABLE_DEREFERENCE);

      // Warnings that are absent in closure library
      DiagnosticGroups.registerGroup("newCheckTypesClosureClean",
//           JSTypeCreatorFromJSDoc.BAD_JSDOC_ANNOTATION,
          JSTypeCreatorFromJSDoc.CONFLICTING_EXTENDED_TYPE,
          JSTypeCreatorFromJSDoc.CONFLICTING_IMPLEMENTED_TYPE,
          JSTypeCreatorFromJSDoc.CONFLICTING_SHAPE_TYPE,
          JSTypeCreatorFromJSDoc.DICT_IMPLEMENTS_INTERF,
          JSTypeCreatorFromJSDoc.EXTENDS_NON_OBJECT,
          JSTypeCreatorFromJSDoc.EXTENDS_NOT_ON_CTOR_OR_INTERF,
          JSTypeCreatorFromJSDoc.IMPLEMENTS_WITHOUT_CONSTRUCTOR,
          JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE,
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
//           GlobalTypeInfo.INVALID_PROP_OVERRIDE,
          GlobalTypeInfo.LENDS_ON_BAD_TYPE,
          GlobalTypeInfo.MALFORMED_ENUM,
//           GlobalTypeInfo.MISPLACED_CONST_ANNOTATION,
//           GlobalTypeInfo.REDECLARED_PROPERTY,
          GlobalTypeInfo.STRUCTDICT_WITHOUT_CTOR,
          GlobalTypeInfo.UNDECLARED_NAMESPACE,
//           GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME,
          TypeCheck.CONFLICTING_EXTENDED_TYPE,
          TypeCheck.ENUM_NOT_CONSTANT,
          TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
          TypeCheck.MULTIPLE_VAR_DEF,
//           TypeCheck.UNKNOWN_OVERRIDE,
          TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED,
//           NewTypeInference.ASSERT_FALSE,
          NewTypeInference.CALL_FUNCTION_WITH_BOTTOM_FORMAL,
          NewTypeInference.CANNOT_BIND_CTOR,
//           NewTypeInference.CONST_REASSIGNED,
          NewTypeInference.CROSS_SCOPE_GOTCHA,
//           NewTypeInference.FAILED_TO_UNIFY,
//           NewTypeInference.FORIN_EXPECTS_OBJECT,
          NewTypeInference.FORIN_EXPECTS_STRING_KEY,
//           NewTypeInference.GOOG_BIND_EXPECTS_FUNCTION,
//           NewTypeInference.INVALID_ARGUMENT_TYPE,
          NewTypeInference.INVALID_INFERRED_RETURN_TYPE,
//           NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE,
//           NewTypeInference.INVALID_OPERAND_TYPE,
//           NewTypeInference.INVALID_THIS_TYPE_IN_BIND,
//           NewTypeInference.MISTYPED_ASSIGN_RHS,
//           NewTypeInference.NON_NUMERIC_ARRAY_INDEX,
//           NewTypeInference.NOT_A_CONSTRUCTOR,
//           NewTypeInference.NOT_UNIQUE_INSTANTIATION,
//           NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY,
//           NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT,
//           NewTypeInference.RETURN_NONDECLARED_TYPE,
          NewTypeInference.UNKNOWN_ASSERTION_TYPE,
          CheckGlobalThis.GLOBAL_THIS,
//           CheckMissingReturn.MISSING_RETURN_STATEMENT,
          TypeCheck.CONSTRUCTOR_NOT_CALLABLE,
          TypeCheck.ILLEGAL_OBJLIT_KEY,
//           TypeCheck.ILLEGAL_PROPERTY_CREATION,
          TypeCheck.IN_USED_WITH_STRUCT,
//           TypeCheck.INEXISTENT_PROPERTY,
//           TypeCheck.NOT_CALLABLE,
//           TypeCheck.WRONG_ARGUMENT_COUNT,
//           TypeValidator.ILLEGAL_PROPERTY_ACCESS,
//           TypeValidator.INVALID_CAST,
          TypeValidator.UNKNOWN_TYPEOF_VALUE);
  }

  public static final DiagnosticGroup CHECK_EVENTFUL_OBJECT_DISPOSAL =
      DiagnosticGroups.registerGroup("checkEventfulObjectDisposal",
          CheckEventfulObjectDisposal.EVENTFUL_OBJECT_NOT_DISPOSED,
          CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL,
          CheckEventfulObjectDisposal.OVERWRITE_PRIVATE_EVENTFUL_OBJECT,
          CheckEventfulObjectDisposal.UNLISTEN_WITH_ANONBOUND);

  public static final DiagnosticGroup REPORT_UNKNOWN_TYPES =
      DiagnosticGroups.registerGroup("reportUnknownTypes",
          TypeCheck.UNKNOWN_EXPR_TYPE);

  public static final DiagnosticGroup CHECK_STRUCT_DICT_INHERITANCE =
      DiagnosticGroups.registerGroup("checkStructDictInheritance",
          TypeCheck.CONFLICTING_SHAPE_TYPE);

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
          VarCheck.VAR_MULTIPLY_DECLARED_ERROR,
          TypeValidator.DUP_VAR_DECLARATION,
          TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH,
          VariableReferenceCheck.REDECLARED_VARIABLE);

  public static final DiagnosticGroup ES3 =
      DiagnosticGroups.registerGroup("es3",
          RhinoErrorReporter.INVALID_ES3_PROP_NAME,
          RhinoErrorReporter.TRAILING_COMMA);

  static final DiagnosticGroup ES5_STRICT_UNCOMMON =
      DiagnosticGroups.registerGroup("es5StrictUncommon",
          RhinoErrorReporter.INVALID_OCTAL_LITERAL,
          StrictModeCheck.USE_OF_WITH,
          StrictModeCheck.UNKNOWN_VARIABLE,
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

  // TODO(johnlenz): Remove this in favor or "missingProvide" which matches
  // the existing and more popular linter suppression
  public static final DiagnosticGroup CHECK_PROVIDES =
      DiagnosticGroups.registerGroup("checkProvides",
          CheckProvides.MISSING_PROVIDE_WARNING);

  public static final DiagnosticGroup MISSING_PROVIDE =
      DiagnosticGroups.registerGroup("missingProvide",
          CheckProvides.MISSING_PROVIDE_WARNING);

  public static final DiagnosticGroup MISSING_REQUIRE =
      DiagnosticGroups.registerGroup("missingRequire",
          CheckRequiresForConstructors.MISSING_REQUIRE_WARNING);

  public static final DiagnosticGroup MISSING_GETCSSNAME =
      DiagnosticGroups.registerGroup("missingGetCssName",
          CheckMissingGetCssName.MISSING_GETCSSNAME);

  public static final DiagnosticGroup DUPLICATE_MESSAGE =
      DiagnosticGroups.registerGroup("duplicateMessage",
          JsMessageVisitor.MESSAGE_DUPLICATE_KEY);

  public static final DiagnosticGroup MISPLACED_TYPE_ANNOTATION =
      DiagnosticGroups.registerGroup("misplacedTypeAnnotation",
          RhinoErrorReporter.MISPLACED_TYPE_ANNOTATION,
          RhinoErrorReporter.MISPLACED_FUNCTION_ANNOTATION);

  public static final DiagnosticGroup SUSPICIOUS_CODE =
      DiagnosticGroups.registerGroup("suspiciousCode",
          CheckSuspiciousCode.SUSPICIOUS_SEMICOLON,
          CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN,
          CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR,
          CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);

  // These checks are not intended to be enabled as errors. It is
  // recommended that you think of them as "linter" warnings that
  // provide optional suggestions.
  public static final DiagnosticGroup LINT_CHECKS =
      DiagnosticGroups.registerGroup("lintChecks",
          CheckEnums.DUPLICATE_ENUM_VALUE,
          // TODO(tbreisacher): Consider moving the CheckInterfaces warnings into the
          // checkTypes DiagnosticGroup
          CheckInterfaces.INTERFACE_FUNCTION_NOT_EMPTY,
          CheckInterfaces.INTERFACE_SHOULD_NOT_TAKE_ARGS,
          CheckNullableReturn.NULLABLE_RETURN,
          CheckNullableReturn.NULLABLE_RETURN_WITH_NAME,
          CheckPrototypeProperties.ILLEGAL_PROTOTYPE_MEMBER);

  public static final DiagnosticGroup USE_OF_GOOG_BASE =
      DiagnosticGroups.registerGroup("useOfGoogBase",
          ProcessClosurePrimitives.USE_OF_GOOG_BASE);

  public static final DiagnosticGroup CLOSURE_DEP_METHOD_USAGE_CHECKS =
      DiagnosticGroups.registerGroup("closureDepMethodUsageChecks",
          ProcessClosurePrimitives.INVALID_CLOSURE_CALL_ERROR);

  // This group exists so that generated code can suppress these
  // warnings. Not for general use. These diagnostics will most likely
  // be moved to the suspiciousCode group.
  public static final DiagnosticGroup TRANSITIONAL_SUSPICOUS_CODE_WARNINGS =
      DiagnosticGroups.registerGroup("transitionalSuspiciousCodeWarnings",
          PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR,
          PeepholeFoldConstants.NEGATING_A_NON_NUMBER_ERROR,
          PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE,
          PeepholeFoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS,
          PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);

  public static final DiagnosticGroup CONFORMANCE_VIOLATIONS =
      DiagnosticGroups.registerGroup("conformanceViolations",
          CheckConformance.CONFORMANCE_VIOLATION,
          CheckConformance.CONFORMANCE_POSSIBLE_VIOLATION);

  static {
    // For internal use only, so there is no constant for it.
    DiagnosticGroups.registerGroup("invalidProvide",
        ProcessClosurePrimitives.INVALID_PROVIDE_ERROR);
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
