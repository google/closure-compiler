/*
 * Copyright 2018 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Prints out a report of the compiler passes that will be executed for advanced mode compilation
 * including indication of what language level they support.
 *
 * <p>This is intended for use in tracking efforts to support language features up to ES_2017
 * in all compiler passes. It can be removed when those efforts are complete.
 */
@GwtIncompatible("Unnecessary")
public class CompilerPassReport {

  public static void main(String[] unusedArgs) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    DefaultPassConfig defaultPassConfig = new DefaultPassConfig(options);
    ArrayList<PassFactory> passesInOrder = new ArrayList<>();
    Set<String> printedPasses = new HashSet<>();

    passesInOrder.addAll(defaultPassConfig.getChecks());
    passesInOrder.addAll(defaultPassConfig.getOptimizations());
    // A pass can get executed more than once, but we only care about the first time it is executed
    // here.
    for (PassFactory pass : passesInOrder) {
      String passName = pass.getName();
      if (!printedPasses.contains(passName)) {
        printPass(pass);
        printedPasses.add(passName);
      }
    }
  }

  private enum PassKind {
    CHECK,
    GATHER_TYPE_INFO,
    TRANSPILE_ES6,
    TRANSPILE_ES6_MODULES,
    TRANSPILE_ES_2016,
    TRANSPILE_ES_2017,
    TRANSPILE_ES_NEXT,
    OPTIMIZATION,
    MARKER,
    OTHER
  }

  private static final ImmutableMap<String, PassKind> passNameToKind =
      ImmutableMap.<String, PassKind>builder()
          .put("beforeStandardChecks", PassKind.MARKER)
          .put("rewriteScriptsToEs6Modules", PassKind.TRANSPILE_ES6_MODULES)
          .put("checkJsDocAndEs6Modules", PassKind.CHECK)
          .put("rewriteObjRestSpread", PassKind.TRANSPILE_ES_NEXT)
          .put("setFeatureSet:es8", PassKind.MARKER)
          .put("es6RewriteModule", PassKind.TRANSPILE_ES6_MODULES)
          .put("checkVariableReferences", PassKind.CHECK)
          .put("checkStrictMode", PassKind.CHECK)
          .put("closureCheckModule", PassKind.CHECK)
          .put("closureRewriteModule", PassKind.OTHER)
          .put("declaredGlobalExternsOnWindow", PassKind.CHECK)
          .put("checkSuper", PassKind.CHECK)
          .put("closureGoogScopeAliases", PassKind.OTHER)
          .put("closureRewriteClass", PassKind.OTHER)
          .put("checkSideEffects", PassKind.CHECK)
          .put("closurePrimitives", PassKind.OTHER)
          .put("checkVars", PassKind.CHECK)
          .put("inferConsts", PassKind.OTHER)
          .put("checkRegExp", PassKind.CHECK)
          .put("rewriteAsyncFunctions", PassKind.TRANSPILE_ES_2017)
          .put("setFeatureSet:es7", PassKind.MARKER)
          .put("convertEs7ToEs6", PassKind.TRANSPILE_ES_2016)
          .put("setFeatureSet:es6", PassKind.MARKER)
          .put("es6ExternsCheck", PassKind.CHECK)
          .put("es6NormalizeShorthandProperties", PassKind.TRANSPILE_ES6)
          .put("es6ConvertSuper", PassKind.TRANSPILE_ES6)
          .put("Es6RenameVariablesInParamLists", PassKind.TRANSPILE_ES6)
          .put("Es6SplitVariableDeclarations", PassKind.TRANSPILE_ES6)
          .put("Es6RewriteDestructuring", PassKind.TRANSPILE_ES6)
          .put("Es6RewriteArrowFunction", PassKind.TRANSPILE_ES6)
          .put("Es6ExtractClasses", PassKind.TRANSPILE_ES6)
          .put("Es6RewriteClass", PassKind.TRANSPILE_ES6)
          .put("earlyConvertEs6", PassKind.TRANSPILE_ES6)
          .put("lateConvertEs6", PassKind.TRANSPILE_ES6)
          .put("Es6RewriteBlockScopedFunctionDeclaration", PassKind.TRANSPILE_ES6)
          .put("Es6RewriteBlockScopedDeclaration", PassKind.TRANSPILE_ES6)
          .put("rewriteGenerators", PassKind.TRANSPILE_ES6)
          .put("setFeatureSet:es5", PassKind.MARKER)
          .put("Es6StaticInheritance", PassKind.TRANSPILE_ES6)
          .put("beforeTypeChecking", PassKind.MARKER)
          .put("j2clSourceFileChecker", PassKind.CHECK)
          .put("inlineTypeAliases", PassKind.OTHER)
          .put("resolveTypes", PassKind.GATHER_TYPE_INFO)
          .put("inferTypes", PassKind.GATHER_TYPE_INFO)
          .put("checkTypes", PassKind.GATHER_TYPE_INFO)
          .put("clearTypedScopePass", PassKind.CHECK)
          .put("checkControlFlow", PassKind.CHECK)
          .put("checkAccessControls", PassKind.CHECK)
          .put("checkConsts", PassKind.CHECK)
          .put("checkConformance", PassKind.CHECK)
          .put("closureReplaceGetCssName", PassKind.OTHER)
          .put("j2clChecksPass", PassKind.CHECK)
          .put("es6ConvertSuperConstructorCalls", PassKind.TRANSPILE_ES6)
          .put("afterStandardChecks", PassKind.MARKER)
          .put("garbageCollectChecks", PassKind.CHECK)
          .put("processDefines", PassKind.OTHER)
          .put("normalize", PassKind.OTHER)
          .put("gatherExternProperties", PassKind.OTHER)
          .put("j2clPass", PassKind.OTHER)
          .put("beforeStandardOptimizations", PassKind.MARKER)
          .put("replaceIdGenerators", PassKind.OTHER)
          .put("optimizeArgumentsArray", PassKind.OPTIMIZATION)
          .put("closureCodeRemoval", PassKind.OPTIMIZATION)
          .put("j2clAssertRemovalPass", PassKind.OPTIMIZATION)
          .put("aggressiveInlineAliases", PassKind.OPTIMIZATION)
          .put("j2clES6Pass", PassKind.OPTIMIZATION)
          .put("collapseProperties", PassKind.OPTIMIZATION)
          .put("checkConstParams", PassKind.OPTIMIZATION)
          .put("earlyInlineVariables", PassKind.OPTIMIZATION)
          .put("earlyPeepholeOptimizations", PassKind.OPTIMIZATION)
          .put("removeUnusedCode", PassKind.OPTIMIZATION)
          .put("markPureFunctions", PassKind.OTHER)
          .put("collapseObjectLiterals", PassKind.OPTIMIZATION)
          .put("inlineVariables", PassKind.OPTIMIZATION)
          .put("peepholeOptimizations", PassKind.OPTIMIZATION)
          .put("removeUnreachableCode", PassKind.OPTIMIZATION)
          .put("closureOptimizePrimitives", PassKind.OPTIMIZATION)
          .put("crossModuleCodeMotion", PassKind.OPTIMIZATION)
          .put("devirtualizePrototypeMethods", PassKind.OPTIMIZATION)
          .put("beforeMainOptimizations", PassKind.MARKER)
          .put("flowSensitiveInlineVariables", PassKind.OPTIMIZATION)
          .put("inlineSimpleMethods", PassKind.OPTIMIZATION)
          .put("inlineFunctions", PassKind.OPTIMIZATION)
          .put("deadAssignmentsElimination", PassKind.OPTIMIZATION)
          .put("deadPropertyAssignmentElimination", PassKind.OPTIMIZATION)
          .put("optimizeCalls", PassKind.OPTIMIZATION)
          .put("j2clConstantHoisterPass", PassKind.OPTIMIZATION)
          .put("j2clClinitPass", PassKind.OPTIMIZATION)
          .put("afterMainOptimizations", PassKind.MARKER)
          .put("beforeModuleMotion", PassKind.MARKER)
          .put("crossModuleMethodMotion", PassKind.OPTIMIZATION)
          .put("afterModuleMotion", PassKind.MARKER)
          .put("collapseAnonymousFunctions", PassKind.OPTIMIZATION)
          .put("extractPrototypeMemberDeclarations", PassKind.OPTIMIZATION)
          .put("renameProperties", PassKind.OPTIMIZATION)
          .put("gatherRawExports", PassKind.OPTIMIZATION)
          .put("convertToDottedProperties", PassKind.OPTIMIZATION)
          .put("coalesceVariableNames", PassKind.OPTIMIZATION)
          .put("markUnnormalized", PassKind.OPTIMIZATION)
          .put("exploitAssign", PassKind.OPTIMIZATION)
          .put("collapseVariableDeclarations", PassKind.OPTIMIZATION)
          .put("denormalize", PassKind.OPTIMIZATION)
          .put("renameVars", PassKind.OPTIMIZATION)
          .put("renameLabels", PassKind.OPTIMIZATION)
          .put("latePeepholeOptimizations", PassKind.OPTIMIZATION)
          .put("stripSideEffectProtection", PassKind.OPTIMIZATION)
          .put("checkAstValidity", PassKind.CHECK)
          .put("varCheckValidity", PassKind.CHECK)
          .build();

  private static PassKind getPassKind(PassFactory pass) {
    PassKind kind = passNameToKind.get(pass.getName());
    return (kind == null) ? PassKind.OTHER : kind;
  }

  private static void printPass(PassFactory pass) {
    PassKind kind = getPassKind(pass);
    String passName = pass.getName();
    FeatureSet supportedFeatureSet = pass.featureSet();
    System.out.printf("%s\t%s\t%s\n", supportedFeatureSet.version(), kind.toString(), passName);
  }
}
