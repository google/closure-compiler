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

import static com.google.javascript.jscomp.CheckClosureImports.INVALID_CLOSURE_IMPORT_DESTRUCTURING;
import static com.google.javascript.jscomp.CheckClosureImports.LATE_PROVIDE_ERROR;
import static com.google.javascript.jscomp.CheckClosureImports.LET_CLOSURE_IMPORT;
import static com.google.javascript.jscomp.CheckClosureImports.LHS_OF_CLOSURE_IMPORT_MUST_BE_CONST_IN_ES_MODULE;
import static com.google.javascript.jscomp.CheckClosureImports.NO_CLOSURE_IMPORT_DESTRUCTURING;
import static com.google.javascript.jscomp.CheckClosureImports.ONE_CLOSURE_IMPORT_PER_DECLARATION;
import static com.google.javascript.jscomp.ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_ALIAS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckClosureImports}. */
@RunWith(JUnit4.class)
public class CheckClosureImportsTest extends CompilerTestCase {

  private static final String PROVIDES_SYMBOL_PATH = "symbol.js";

  private static final SourceFile PROVIDES_SYMBOL_SRC =
      SourceFile.fromCode(PROVIDES_SYMBOL_PATH, "goog.provide('symbol');");

  private static final String ES_MODULE_PATH = "es_module.js";

  private static final SourceFile ES_MODULE_SRC =
      SourceFile.fromCode(ES_MODULE_PATH, "goog.declareModuleId('es.module');\nexport {};");

  private static final String TEST_CODE_PATH = "testcode";

  private static final ModuleMetadata PROVIDES_SYMBOL_METADATA =
      ModuleMetadata.builder()
          .addGoogNamespace("symbol")
          .moduleType(ModuleType.GOOG_PROVIDE)
          .usesClosure(true)
          .isTestOnly(false)
          .build();

  private static final ModuleMetadata ES_MODULE_METADATA =
      ModuleMetadata.builder()
          .addGoogNamespace("es.module")
          .moduleType(ModuleType.ES6_MODULE)
          .usesClosure(true)
          .isTestOnly(false)
          .build();

  private static final ModuleMetadata EXTERN_METADATA =
      ModuleMetadata.builder()
          .moduleType(ModuleType.SCRIPT)
          .usesClosure(false)
          .isTestOnly(false)
          .build();

  private static SourceFile makeTestFile(String text) {
    return SourceFile.fromCode(TEST_CODE_PATH, text);
  }

  private ModuleType moduleType;
  private LanguageMode languageMode;

  private static final ImmutableSet<ModuleType> NO_MODULES = ImmutableSet.of();
  private static final ImmutableSet<ModuleType> ALL_MODULES =
      ImmutableSet.copyOf(ModuleType.values());
  private ImmutableSet<ModuleType> typesToRewriteIn = NO_MODULES;

  @Before
  public void reset() {
    moduleType = ModuleType.SCRIPT;
    languageMode = LanguageMode.ECMASCRIPT_2018;
    typesToRewriteIn = ALL_MODULES;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);
    options.setLanguageIn(languageMode);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    ModuleMetadata testMetadata =
        ModuleMetadata.builder()
            .addGoogNamespace("test")
            .moduleType(moduleType)
            .usesClosure(true)
            .isTestOnly(false)
            .build();

    return new CheckClosureImports(
        compiler,
        new ModuleMetadataMap(
            ImmutableMap.of(
                PROVIDES_SYMBOL_PATH,
                PROVIDES_SYMBOL_METADATA,
                ES_MODULE_PATH,
                ES_MODULE_METADATA,
                TEST_CODE_PATH,
                testMetadata,
                "externs",
                EXTERN_METADATA),
            ImmutableMap.of(
                "symbol",
                PROVIDES_SYMBOL_METADATA,
                "es.module",
                ES_MODULE_METADATA,
                "test",
                testMetadata)),
        typesToRewriteIn);
  }

  /** A test common to goog.require, goog.forwardDeclare, and goog.requireType. */
  private void testCommonCase(String source) {
    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.require"))));
    test(
        srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.forwardDeclare"))));
    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.requireType"))));
  }

  /** A test common to goog.require, goog.forwardDeclare, and goog.requireType. */
  private void testCommonCase(String source, DiagnosticType error) {
    test(
        srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.require"))),
        error(error));
    test(
        srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.forwardDeclare"))),
        error(error));
    test(
        srcs(PROVIDES_SYMBOL_SRC, makeTestFile(source.replace("<import>", "goog.requireType"))),
        error(error));
  }

  @Test
  public void launchSetGuardsChecks() {
    typesToRewriteIn = NO_MODULES;

    // referenceMissingSymbolIsError verifies these are errors when typesToRewriteIn is ALL_MODULES.
    testSame("goog.require('dne');");
    testSame("goog.requireType('dne');");
    testSame("goog.module.get('dne');");
  }

  @Test
  public void referenceMissingSymbolIsError() {
    testCommonCase("<import>('symbol');");
    testSame("() => goog.module.get('symbol');");

    // Not an error for goog.forwardDeclare in scripts.
    testError("goog.require('dne');", MISSING_MODULE_OR_PROVIDE);
    testError("goog.requireType('dne');", MISSING_MODULE_OR_PROVIDE);
    testError("() => goog.module.get('dne');", MISSING_MODULE_OR_PROVIDE);
  }

  @Test
  public void importInGlobalScopeIsOk() {
    testCommonCase("<import>('symbol');");
  }

  @Test
  public void importInGoogProvideIsOk() {
    moduleType = ModuleType.GOOG_PROVIDE;

    testCommonCase(
        lines(
            "goog.provide('test');", //
            "<import>('symbol');"));
  }

  @Test
  public void importInGoogModuleScopeIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.module('test');", //
            "<import>('symbol');"));
  }

  @Test
  public void importInGoogLoadModuleScopeIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('test');", //
            "  <import>('symbol');",
            "});"));
  }

  @Test
  public void importInEsModuleScopeIsOk() {
    moduleType = ModuleType.ES6_MODULE;

    testCommonCase(
        lines(
            "<import>('symbol');", //
            "export {};"));
  }

  @Test
  public void importInBlockScopeIsError() {
    testCommonCase("{ <import>('symbol'); }", INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void importInFunctionScopeIsError() {
    testCommonCase("() => <import>('symbol');", INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void importInSingleDeclarationIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.module('test');", //
            "var symbol = <import>('symbol');"));

    moduleType = ModuleType.ES6_MODULE;

    testCommonCase(
        lines(
            "const symbol = <import>('symbol');", //
            "export {};"));
  }

  @Test
  public void mustMatchCapitalization() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.module('test');", //
            "const Symbol = <import>('symbol');"),
        INCORRECT_SHORTNAME_CAPITALIZATION);
  }

  @Test
  public void canHaveDifferentName() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.module('test');", //
            "const localSymbol = <import>('symbol');"));
  }

  @Test
  public void importInMultiDeclarationisError() {
    moduleType = ModuleType.GOOG_MODULE;

    testCommonCase(
        lines(
            "goog.module('test');", //
            "var before, symbol = goog.require('symbol');"),
        ONE_CLOSURE_IMPORT_PER_DECLARATION);

    testCommonCase(
        lines(
            "goog.module('test');", //
            "var symbol = goog.require('symbol'), after;"),
        ONE_CLOSURE_IMPORT_PER_DECLARATION);

    moduleType = ModuleType.ES6_MODULE;

    testCommonCase(
        lines(
            "const before = 0, symbol = goog.require('symbol');", //
            "export {};"),
        ONE_CLOSURE_IMPORT_PER_DECLARATION);

    testCommonCase(
        lines(
            "var symbol = goog.require('symbol'), after = 0;", //
            "export {};"),
        ONE_CLOSURE_IMPORT_PER_DECLARATION);
  }

  @Test
  public void googRequireDeclarationInEsModuleMustBeConst() {
    moduleType = ModuleType.ES6_MODULE;

    testSame(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "const symbol = goog.require('symbol');", //
                    "export {};"))));

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "let symbol = goog.require('symbol');", //
                    "export {};"))),
        error(LET_CLOSURE_IMPORT));

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "var symbol = goog.require('symbol');", //
                    "export {};"))),
        error(LHS_OF_CLOSURE_IMPORT_MUST_BE_CONST_IN_ES_MODULE));
  }

  @Test
  public void googRequireDeclarationCannotBeLet() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "let symbol = goog.require('symbol');"))),
        error(LET_CLOSURE_IMPORT));
  }

  @Test
  public void googRequireTypeDeclarationCannotBeLet() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "let symbol = goog.requireType('symbol');"))),
        error(LET_CLOSURE_IMPORT));
  }

  @Test
  public void googRequireSimpleObjectDestructuringIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {property0, property1: p1} = goog.require('symbol');"))));
  }

  @Test
  public void googRequireTypeSimpleObjectDestructuringIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {property0, property1: p1} = goog.requireType('symbol');"))));
  }

  @Test
  public void googForwardDeclareSimpleObjectDestructuringIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {property0} = goog.forwardDeclare('symbol');"))),
        error(NO_CLOSURE_IMPORT_DESTRUCTURING));
  }

  @Test
  public void googRequireComplexObjectDestructuringIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {property0: {a}} = goog.require('symbol');"))),
        error(INVALID_CLOSURE_IMPORT_DESTRUCTURING));
  }

  @Test
  public void googRequireTypeComplexObjectDestructuringIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {property0: {a}} = goog.requireType('symbol');"))),
        error(INVALID_CLOSURE_IMPORT_DESTRUCTURING));
  }

  @Test
  public void googRequireSimpleArrayDestructuringIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const [e0] = goog.require('symbol');"))),
        error(INVALID_CLOSURE_IMPORT_DESTRUCTURING));
  }

  @Test
  public void googRequireTypeSimpleArrayDestructuringIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const [e0] = goog.requireType('symbol');"))),
        error(INVALID_CLOSURE_IMPORT_DESTRUCTURING));
  }

  @Test
  public void googRequireLateIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile("goog.require('symbol');")));
    test(
        srcs(makeTestFile("goog.require('symbol');"), PROVIDES_SYMBOL_SRC),
        error(LATE_PROVIDE_ERROR));
  }

  @Test
  public void googRequireTypeLateIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile("goog.requireType('symbol');")));

    test(srcs(makeTestFile("goog.requireType('symbol');"), PROVIDES_SYMBOL_SRC));
  }

  @Test
  public void googForwardDeclareLateIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile("goog.forwardDeclare('symbol');")));

    test(srcs(makeTestFile("goog.forwardDeclare('symbol');"), PROVIDES_SYMBOL_SRC));
  }

  @Test
  public void moduleGetInModuleScopeIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "goog.module.get('symbol');"))),
        error(MODULE_USES_GOOG_MODULE_GET));

    moduleType = ModuleType.ES6_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module.get('symbol');", //
                    "export {};"))),
        error(MODULE_USES_GOOG_MODULE_GET));
  }

  @Test
  public void moduleGetInGlobalScopeIsError() {
    moduleType = ModuleType.SCRIPT;

    test(
        srcs(ES_MODULE_SRC, makeTestFile("goog.module.get('es.module');")),
        error(INVALID_GET_CALL_SCOPE));
  }

  @Test
  public void moduleGetInFunctionScopeIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "() => goog.module.get('symbol');"))));

    moduleType = ModuleType.ES6_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "() => goog.module.get('symbol');", //
                    "export {};"))));

    moduleType = ModuleType.SCRIPT;

    test(srcs(PROVIDES_SYMBOL_SRC, makeTestFile("() => goog.module.get('symbol');")));
  }

  @Test
  public void moduleGetFillInForwardDeclareIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "let symbol = goog.forwardDeclare('symbol');",
                    "() => symbol = goog.module.get('symbol');"))));
  }

  @Test
  public void moduleGetOtherAssignmentDeclareIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "let symbol;",
                    "() => symbol = goog.module.get('symbol');"))),
        error(INVALID_GET_ALIAS));
  }

  @Test
  public void moduleGetFillInWrongNamespaceIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "let symbol = goog.forwardDeclare('symbol');",
                    "() => symbol = goog.module.get('test');"))),
        error(INVALID_GET_ALIAS));
  }

  @Test
  public void googForwardDeclareForGlobalInScriptIsOk() {
    moduleType = ModuleType.SCRIPT;

    test(srcs(makeTestFile("goog.forwardDeclare('MyGlobal');")));
  }

  @Test
  public void googForwardDeclareForGlobalInProvideIsOk() {
    moduleType = ModuleType.GOOG_PROVIDE;

    test(
        srcs(
            makeTestFile(
                lines(
                    "goog.provide('test');", //
                    "goog.forwardDeclare('MyGlobal');"))));
  }

  @Test
  public void googForwardDeclareForGlobalInGoogModuleIsError() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "goog.forwardDeclare('MyGlobal');"))),
        error(MISSING_MODULE_OR_PROVIDE));
  }

  @Test
  public void googForwardDeclarForGlobalInEsModuleIsError() {
    moduleType = ModuleType.ES6_MODULE;

    test(
        srcs(
            makeTestFile(
                lines(
                    "export {};", //
                    "goog.forwardDeclare('MyGlobal');"))),
        error(MISSING_MODULE_OR_PROVIDE));
  }

  @Test
  public void googRequireCodeReferenceAliasIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    testSame(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {alias} = goog.require('symbol');",
                    "alias;"))));
  }

  @Test
  public void googForwardDeclareCodeReferenceAliasIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    testSame(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const alias = goog.forwardDeclare('symbol');",
                    "alias;"))));
  }

  @Test
  public void googRequireTypeTypeReferenceAliasIsOk() {
    moduleType = ModuleType.GOOG_MODULE;

    test(
        srcs(
            PROVIDES_SYMBOL_SRC,
            makeTestFile(
                lines(
                    "goog.module('test');", //
                    "const {Type} = goog.requireType('symbol');",
                    "/** !Type */ let x;"))));
  }

  @Test
  public void googRequireBetweenEsModulesIsWarning() {
    moduleType = ModuleType.ES6_MODULE;

    test(
        srcs(
            ES_MODULE_SRC,
            makeTestFile(
                lines(
                    "const {Type} = goog.require('es.module');", //
                    "export {};"))),
        warning(Es6RewriteModules.SHOULD_IMPORT_ES6_MODULE));
  }
}
