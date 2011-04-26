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

import java.util.Map;

/**
 * Named groups of DiagnosticTypes exposed by Compiler.
 * @author nicksantos@google.com (Nick Santos)
 */
public class DiagnosticGroups {

  public DiagnosticGroups() {}

  private final static Map<String, DiagnosticGroup> groupsByName =
      Maps.newHashMap();

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
  protected DiagnosticGroup forName(String name) {
    return groupsByName.get(name);
  }

  // A bit of a hack to display the available groups on the command-line.
  // New groups should be added to this list if they are public and should
  // be listed on the command-line as an available option.
  //
  // If a group is suppressable on a per-file basis, it should be added
  // to parser/ParserConfig.properties
  static final String DIAGNOSTIC_GROUP_NAMES =
      "accessControls, ambiguousFunctionDecl, checkRegExp," +
      "checkTypes, checkVars, constantProperty, deprecated, " +
      "externsValidation, fileoverviewTags, globalThis, " +
      "internetExplorerChecks, invalidCasts, missingProperties, " +
      "nonStandardJsDocs, strictModuleDepCheck, typeInvalidation, " +
      "undefinedVars, unknownDefines, uselessCode, " +
      "visibility";

  public static DiagnosticGroup GLOBAL_THIS = DiagnosticGroups
      .registerGroup("globalThis",
          CheckGlobalThis.GLOBAL_THIS);

  public static DiagnosticGroup DEPRECATED = DiagnosticGroups
      .registerGroup("deprecated",
          CheckAccessControls.DEPRECATED_NAME,
          CheckAccessControls.DEPRECATED_NAME_REASON,
          CheckAccessControls.DEPRECATED_PROP,
          CheckAccessControls.DEPRECATED_PROP_REASON,
          CheckAccessControls.DEPRECATED_CLASS,
          CheckAccessControls.DEPRECATED_CLASS_REASON);

  public static DiagnosticGroup VISIBILITY = DiagnosticGroups
      .registerGroup("visibility",
          CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS,
          CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS,
          CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS,
          CheckAccessControls.PRIVATE_OVERRIDE,
          CheckAccessControls.VISIBILITY_MISMATCH);

  public static DiagnosticGroup CONSTANT_PROPERTY = DiagnosticGroups
      .registerGroup("constantProperty",
          CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE);

  public static DiagnosticGroup NON_STANDARD_JSDOC =
      DiagnosticGroups.registerGroup("nonStandardJsDocs",
          RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

  public static DiagnosticGroup ACCESS_CONTROLS =
      DiagnosticGroups.registerGroup("accessControls",
          DEPRECATED, VISIBILITY);

  public static DiagnosticGroup INVALID_CASTS = DiagnosticGroups
      .registerGroup("invalidCasts",
          TypeValidator.INVALID_CAST);

  public static DiagnosticGroup FILEOVERVIEW_JSDOC =
      DiagnosticGroups.registerGroup("fileoverviewTags",
          RhinoErrorReporter.EXTRA_FILEOVERVIEW);

  public static DiagnosticGroup STRICT_MODULE_DEP_CHECK =
      DiagnosticGroups.registerGroup("strictModuleDepCheck",
          VarCheck.STRICT_MODULE_DEP_ERROR,
          CheckGlobalNames.STRICT_MODULE_DEP_QNAME);

  public static DiagnosticGroup EXTERNS_VALIDATION =
      DiagnosticGroups.registerGroup("externsValidation",
          VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR,
          VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

  public static DiagnosticGroup AMBIGUOUS_FUNCTION_DECL =
      DiagnosticGroups.registerGroup("ambiguousFunctionDecl",
          VariableReferenceCheck.AMBIGUOUS_FUNCTION_DECL);

  public static DiagnosticGroup UNKNOWN_DEFINES =
      DiagnosticGroups.registerGroup("unknownDefines",
          ProcessDefines.UNKNOWN_DEFINE_WARNING);

  public static DiagnosticGroup TWEAKS =
      DiagnosticGroups.registerGroup("tweakValidation",
          ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_WARNING,
          ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING,
          ProcessTweaks.UNKNOWN_TWEAK_WARNING);

  public static DiagnosticGroup MISSING_PROPERTIES =
      DiagnosticGroups.registerGroup("missingProperties",
          TypeCheck.INEXISTENT_PROPERTY);

  // TODO: add more IE specific checks here.
  public static DiagnosticGroup INTERNET_EXPLORER_CHECKS =
      DiagnosticGroups.registerGroup("internetExplorerChecks",
          RhinoErrorReporter.TRAILING_COMMA);

  public static DiagnosticGroup UNDEFINED_VARIABLES =
      DiagnosticGroups.registerGroup("undefinedVars",
          VarCheck.UNDEFINED_VAR_ERROR);

  public static DiagnosticGroup CHECK_REGEXP =
      DiagnosticGroups.registerGroup("checkRegExp",
          CheckRegExp.REGEXP_REFERENCE);

  public static DiagnosticGroup CHECK_TYPES =
      DiagnosticGroups.registerGroup("checkTypes",
          TypeValidator.ALL_DIAGNOSTICS,
          TypeCheck.ALL_DIAGNOSTICS);

  public static DiagnosticGroup CHECK_VARIABLES =
      DiagnosticGroups.registerGroup("checkVars",
          VarCheck.UNDEFINED_VAR_ERROR,
          SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);

  public static DiagnosticGroup CHECK_USELESS_CODE =
      DiagnosticGroups.registerGroup("uselessCode",
          CheckSideEffects.USELESS_CODE_ERROR,
          CheckUnreachableCode.UNREACHABLE_CODE);

  public static DiagnosticGroup TYPE_INVALIDATION =
    DiagnosticGroups.registerGroup("typeInvalidation",
        DisambiguateProperties.Warnings.INVALIDATION);

  /**
   * Adds warning levels by name.
   */
  void setWarningLevel(CompilerOptions options,
      String name, CheckLevel level) {
    DiagnosticGroup group = forName(name);
    Preconditions.checkNotNull(group, "No warning class for name: " + name);
    options.setWarningLevel(group, level);
  }
}
