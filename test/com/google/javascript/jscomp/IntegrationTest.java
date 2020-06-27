/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.testing.IntegrationTestCase;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the compiler.
 *
 * <p>Before adding a test to this file, consider whether it might belong in a more specific
 * *IntegrationTest.java files.
 */
@RunWith(JUnit4.class)
public final class IntegrationTest extends IntegrationTestCase {
  private static final String CLOSURE_BOILERPLATE =
      "/** @define {boolean} */ var COMPILED = false; var goog = {};"
      + "goog.exportSymbol = function() {};";

  private static final String CLOSURE_COMPILED =
      "var COMPILED = true; var goog$exportSymbol = function() {};";

  @Test
  public void testNewDotTargetTranspilation() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    test(
        options,
        lines(
            "", //
            "class Foo {",
            "  constructor() {",
            "    use(new.target);",
            "    use(() => new.target);", // works in arrow functions, too
            "  }",
            "}",
            ""),
        lines(
            "", //
            "var Foo = function() {",
            "  var a = this;",
            "  use(this.constructor);",
            "  use(function() { return a.constructor; });", // works in arrow functions, too
            "}",
            ""));
  }

  @Test
  public void testNumericSeparator() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    test(options, "1_000", "1000");
  }

  @Test
  public void testCheckUntranspilable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    testNoWarnings(options, "var r = /xx/s;");
  }

  @Test
  public void testBug65688660() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    const b1 = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    const b2 = [];",
            "    for (const kv of []) {",
            "      const key2 = kv.key;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    param = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    param = [];",
            "    for (const kv of []) {",
            "      param = kv.key;",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testBug65688660_pseudoNames() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setGeneratePseudoNames(true);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(param) {",
            "  if (true) {",
            "    const b1 = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    const b2 = [];",
            "    for (const kv of []) {",
            "      const key2 = kv.key;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(b1_b2_key2_param) {",
            "  if (true) {",
            "    b1_b2_key2_param = [];",
            "    for (const [key, value] of []) {}",
            "  }",
            "  if (true) {",
            "    b1_b2_key2_param = [];",
            "    for (const kv of []) {",
            "      b1_b2_key2_param = kv.key;",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testObjDestructuringConst() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setCoalesceVariableNames(true);
    test(
        options,
        LINE_JOINER.join(
            "function f(obj) {",
            "  {",
            "    const {foo} = obj;",
            "    alert(foo);",
            "  }",
            "  {",
            "    const {bar} = obj;",
            "    alert(bar);",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(obj) {",
            "  {",
            "    const {foo} = obj;",
            "    alert(foo);",
            "  }",
            "  {",
            "    ({bar: obj} = obj);",
            "    alert(obj);",
            "  }",
            "}"));
  }

  @Test
  public void testConstructorCycle() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    testNoWarnings(
        options,
        lines(
            "/** @return {function()} */",
            "var AsyncTestCase = function() {};",
            "/**",
            " * @constructor",
            " */",
            "const Foo = /** @type {function(new:Foo)} */ (AsyncTestCase());"));
  }

  // b/27531865
  @Test
  public void testLetInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    let x = 1;",
        "  case 1:",
        "    x = 2;",
        "}");
    String after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    var x = 1;",
        "  case 1:",
        "    x = 2;",
        "}");
    test(options, before, after);

    before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "  default:",
        "    let x = 1;",
        "}");
    after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "  default:",
        "    var x = 1;",
        "}");
    test(options, before, after);
  }

  @Test
  public void testExplicitBlocksInSwitch() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);
    String before = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    { const x = 3; break; }",
        "  case 1:",
        "    { const x = 5; break; }",
        "}");
    String after = LINE_JOINER.join(
        "var a = 0;",
        "switch (a) {",
        "  case 0:",
        "    { var x = 3; break; }",
        "  case 1:",
        "    { var x$0 = 5; break; }",
        "}");
    test(options, before, after);
  }

  @Test
  public void testMultipleAliasesInlined_bug31437418() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    test(
        options,
        LINE_JOINER.join(
            "class A { static z() {} }",
            "const B = {};",
            " B.A = A;",
            " const C = {};",
            " C.A = B.A; ",
            "const D = {};",
            " D.A = C.A;",
            " D.A.z();"),
        LINE_JOINER.join(
            "var A = function(){};",
            "var A$z = function(){};",
            "var B$A = null;",
            "var C$A = null;",
            "var D$A = null;",
            "A$z();"));
  }



  @Test
  public void testBug1956277() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    test(
        options,
        "var CONST = {}; CONST.bar = null;"
        + "function f(url) { CONST.bar = url; }",
        "var CONST$bar = null; function f(url) { CONST$bar = url; }");
  }

  @Test
  public void testBug1962380() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setInlineVariables(true);
    options.setGenerateExports(true);
    test(
        options,
        CLOSURE_BOILERPLATE + "/** @export */ goog.CONSTANT = 1;"
        + "var x = goog.CONSTANT;",
        "(function() {})('goog.CONSTANT', 1);");
  }

  // http://b/31448683
  @Test
  public void testBug31448683() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    options.setInlineFunctions(Reach.ALL);
    test(
        options,
        LINE_JOINER.join(
            "function f() {",
            "  x = x || 1",
            "  var x;",
            "  console.log(x);",
            "}",
            "for (var _ in [1]) {",
            "  f();",
            "}"),
        LINE_JOINER.join(
            "for(var _ in[1]) {",
            "  {",
            "     var x$jscomp$inline_0 = void 0;",
            "     x$jscomp$inline_0 = x$jscomp$inline_0 || 1;",
            "     console.log(x$jscomp$inline_0);",
            "  }",
            "}"));
  }

  @Test
  public void testBug32660578() {
    testSame(createCompilerOptions(), "function f() { alert(x); for (var x in []) {} }");
  }

  // http://blickly.github.io/closure-compiler-issues/#90
  @Test
  public void testIssue90() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setRemoveDeadCode(true);
    test(options, "var x; x && alert(1);", "");
  }

  @Test
  public void testChromePass_noTranspile() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setChromePass(true);
    options.setLanguage(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "var cr = {}; cr.define('my.namespace', function() { class X {} return {X: X}; });",
        LINE_JOINER.join(
            "var cr = {},",
            "    my = my || {};",
            "my.namespace = my.namespace || {};",
            "cr.define('my.namespace', function() {",
            "  my.namespace.X = class {};",
            "  return { X: my.namespace.X };",
            "});"));
  }

  @Test
  public void testChromePass_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setChromePass(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        "cr.define('my.namespace', function() { class X {} return {X: X}; });",
        LINE_JOINER.join(
            "var my = my || {};",
            "my.namespace = my.namespace || {};",
            "cr.define('my.namespace', function() {",
            "  /** @constructor */",
            "  my.namespace.X = function() {};",
            "  return { X: my.namespace.X };",
            "});"));
  }

  @GwtIncompatible("CheckMissingGetCssName is incompatible")
  @Test
  public void testCssNameCheck() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.ERROR);
    options.setCheckMissingGetCssNameBlacklist("foo");
    test(options, "var x = 'foo';", CheckMissingGetCssName.MISSING_GETCSSNAME);
  }

  @Test
  public void testBug2592659() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.WARNING);
    options.setCheckMissingGetCssNameBlacklist("foo");
    test(
        options,
        lines(
            "var goog = {};",
            "/**",
            " * @param {string} className",
            " * @param {string=} opt_modifier",
            " * @return {string}",
            "*/",
            "goog.getCssName = function(className, opt_modifier) {}",
            "var x = goog.getCssName(123, 'a');"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testExportedNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(
        options,
        "/** @define {boolean} */ var COMPILED = false;"
        + "var goog = {}; goog.exportSymbol('b', goog);",
        "var a = true; var c = {}; c.exportSymbol('b', c);");
    test(options,
         "/** @define {boolean} */ var COMPILED = false;" +
         "var goog = {}; goog.exportSymbol('a', goog);",
         "var b = true; var c = {}; c.exportSymbol('a', c);");
  }

  @Test
  public void testCheckGlobalThisOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.ERROR);
    test(options, "function f() { this.y = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  @Test
  public void testSusiciousCodeOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(false);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.ERROR);
    test(options, "function f() { this.y = 3; }", CheckGlobalThis.GLOBAL_THIS);
  }

  @Test
  public void testCheckGlobalThisOff() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.OFF);
    testSame(options, "function f() { this.y = 3; }");
  }

  @Test
  public void testCheckRequiresAndCheckProvidesOff() {
    testSame(createCompilerOptions(), new String[] {
      "/** @constructor */ function Foo() {}",
      "new Foo();"
    });
  }

  @Test
  public void testGenerateExportsOff() {
    testSame(createCompilerOptions(), "/** @export */ function f() {}");
  }

  @Test
  public void testExportTestFunctionsOn1() {
    CompilerOptions options = createCompilerOptions();
    options.exportTestFunctions = true;
    test(
        options,
        "/** @const */ var goog = {}; function testFoo() {}",
        lines(
            "/** @const */",
            "var goog = {};", //
            "/** @export */ function testFoo() {}",
            "goog.exportSymbol('testFoo', testFoo);"));
  }

  @Test
  public void testExportTestFunctionsOn2() {
    CompilerOptions options = createCompilerOptions();
    options.setExportTestFunctions(true);
    options.setClosurePass(true);
    options.setRenamingPolicy(
        VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setGeneratePseudoNames(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options,
        new String[] {
            LINE_JOINER.join(
                "var goog = {};",
                "goog.provide('goog.testing.testSuite');",
                "goog.testing.testSuite = function(a) {};"),
            LINE_JOINER.join(
                "goog.module('testing');",
                "var testSuite = goog.require('goog.testing.testSuite');",
                "testSuite({testMethod:function(){}});")
        },
        new String[] {
            "var $goog$testing$testSuite$$ = function($a$$) {};",
            "$goog$testing$testSuite$$({'testMethod':function(){}})"
        });
  }

  @Test
  public void testAngularPassOff() {
    testSame(createCompilerOptions(),
        "/** @ngInject */ function f() {} " +
        "/** @ngInject */ function g(a){} " +
        "/** @ngInject */ var b = function f(a) {} ");
  }

  @Test
  public void testAngularPassOn() {
    CompilerOptions options = createCompilerOptions();
    options.angularPass = true;
    test(options,
        "/** @ngInject */ function f() {} " +
        "/** @ngInject */ function g(a){} " +
        "/** @ngInject */ var b = function f(a, b, c) {} ",

        "function f() {} " +
        "function g(a) {} g['$inject']=['a'];" +
        "var b = function f(a, b, c) {}; b['$inject']=['a', 'b', 'c']");
  }

  @Test
  public void testAngularPassOn_transpile() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.angularPass = true;
    test(options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "var C = function(x){}; C['$inject'] = ['x'];");
  }

  @Test
  public void testAngularPassOn_Es6Out() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.angularPass = true;
    test(options,
        "class C { /** @ngInject */ constructor(x) {} }",
        "class C { constructor(x){} } C['$inject'] = ['x'];");
  }

  @Test
  public void testExportTestFunctionsOff() {
    testSame(createCompilerOptions(), "function testFoo() {}");
  }

  @Test
  public void testCheckSymbolsOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3;");
  }

  @Test
  public void testCheckSymbolsOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    test(options, "x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testNoTypeWarningForDupExternNamespace() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    externs = ImmutableList.of(SourceFile.fromCode(
        "externs",
        LINE_JOINER.join(
            "/** @const */",
            "var ns = {};",
            "/** @type {number} */",
            "ns.prop1;",
            "/** @const */",
            "var ns = {};",
            "/** @type {number} */",
            "ns.prop2;")));
    testSame(options, "");
  }

  @Test
  public void testCheckReferencesOff() {
    CompilerOptions options = createCompilerOptions();
    testSame(options, "x = 3; var x = 5;");
  }

  @Test
  public void testCheckReferencesOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    test(options, "x = 3; var x = 5;", VariableReferenceCheck.EARLY_REFERENCE);
  }

  @Test
  public void testInferTypes() {
    CompilerOptions options = createCompilerOptions();
    options.inferTypes = true;
    options.setCheckTypes(false);
    options.setClosurePass(true);

    test(options,
        CLOSURE_BOILERPLATE + "goog.provide('Foo'); /** @enum */ Foo = {a: 3};",
        "var COMPILED=true;var goog={};goog.exportSymbol=function(){};var Foo={a:3}");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);

    // This does not generate a warning.
    test(options, "/** @type {number} */ var n = window.name;",
        "var n = window.name;");
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isEqualTo(0.0);
  }

  @Test
  public void testTypeCheckAndInference() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(
        options, "/** @type {number} */ var n = window.name;", TypeValidator.TYPE_MISMATCH_WARNING);
    assertThat(lastCompiler.getErrorManager().getTypedPercent()).isGreaterThan(0.0);
  }

  @Test
  public void testTypeNameParser() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "/** @type {n} */ var n = window.name;",
        RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
  }

  // This tests that the TypedScopeCreator is memoized so that it only creates a
  // Scope object once for each scope. If, when type inference requests a scope,
  // it creates a new one, then multiple JSType objects end up getting created
  // for the same local type, and ambiguate will rename the last statement to
  // o.a(o.a, o.a), which is bad.
  @Test
  public void testMemoizedTypedScopeCreator() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        lines(
            "function someTest() {",
            "  /** @constructor */",
            "  function Foo() { this.instProp = 3; }",
            "  Foo.prototype.protoProp = function(a, b) {};",
            "  /** @constructor\n @extends Foo */",
            "  function Bar() {}",
            "  goog.inherits(Bar, Foo);",
            "  var o = new Bar();",
            "  o.protoProp(o.protoProp, o.instProp);",
            "}"),
        lines(
            "function someTest() {",
            "  function Foo() { this.b = 3; }",
            "  function Bar() {}",
            "  Foo.prototype.a = function(a, b) {};",
            "  goog.c(Bar, Foo);",
            "  var o = new Bar();",
            "  o.a(o.a, o.b);",
            "}"));
  }

  @Test
  public void ambiguatePropertiesWithAliases() {
    // Avoid injecting the polyfills, so we don't have to include them in the expected output.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            lines(
                "var $jscomp = {};",
                "",
                "/**",
                " * @param {?} subClass",
                " * @param {?} superClass",
                " * @return {?} newClass",
                " */",
                "$jscomp.inherits = function(subClass, superClass) {};",
                "")));
    externs = externsList.build();

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        lines(
            "", //
            "class A {",
            "  constructor() {",
            "    this.aProp = 'aProp';",
            "  }",
            "}",
            "",
            "/**",
            " * @const",
            " */",
            "const AConstAlias = A;",
            "",
            "/**",
            " * @constructor",
            " */",
            "const AConstructorAlias = A;",
            "",
            "class B extends A {",
            "  constructor() {",
            "    super();",
            "    this.bProp = 'bProp';",
            "    this.aProp = 'originalAProp';",
            "  }",
            "}",
            "",
            "class BConst extends AConstAlias {",
            "  constructor() {",
            "    super();",
            "    this.bProp = 'bConstProp';",
            "    this.aProp = 'constAliasAProp';",
            "  }",
            "}",
            "",
            "class BConstructorAlias extends AConstructorAlias {",
            "  constructor() {",
            "    super();",
            "    this.bProp = 'bConstructorProp';",
            "    this.aProp = 'constructorAliasAProp';",
            "  }",
            "}",
            "",
            ""),
        lines(
            "", //
            "var A = function() {",
            "  this.a = 'aProp';", // gets a unique name
            "};",
            "",
            "var AConstAlias = A;",
            "",
            "var AConstructorAlias = A;",
            "",
            "var B = function() {",
            "  A.call(this);",
            "  this.b = 'bProp';", // ambiguated with props from other classes
            "  this.a = 'originalAProp';", // matches A class property
            "};",
            "$jscomp.inherits(B,A);",
            "",
            "var BConst = function() {",
            "  A.call(this);",
            "  this.b = 'bConstProp';", // ambiguated with props from other classes
            "  this.a = 'constAliasAProp';", // matches A class property
            "};",
            "$jscomp.inherits(BConst,A);",
            "",
            "var BConstructorAlias = function() {",
            "  A.call(this);",
            "  this.b = 'bConstructorProp';", // ambiguated with props from other classes
            "  this.a = 'constructorAliasAProp';", // matches A class property
            "};",
            "$jscomp.inherits(BConstructorAlias,A)",
            "",
            ""));
  }

  @Test
  public void ambiguatePropertiesWithMixins() {
    // Avoid injecting the polyfills, so we don't have to include them in the expected output.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            lines(
                "var $jscomp = {};",
                "",
                "/**",
                " * @param {?} subClass",
                " * @param {?} superClass",
                " * @return {?} newClass",
                " */",
                "$jscomp.inherits = function(subClass, superClass) {};",
                "")));
    externs = externsList.build();

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        lines(
            "", //
            "class A {",
            "  constructor() {",
            "    this.aProp = 'aProp';",
            "  }",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  return class extends baseType {",
            "    constructor() {",
            "      super();",
            "      this.x = 'x';",
            "    }",
            "  };",
            "}",
            "/** @constructor */",
            "const BSuper = mixinX(A);",
            "",
            "class B extends BSuper {",
            "  constructor() {",
            "    super();",
            "    this.bProp = 'bProp';",
            "  }",
            "}",
            "",
            ""),
        lines(
            "", //
            "var A = function() {",
            "  this.a = 'aProp';", // unique property name
            "}",
            "",
            "function mixinX(baseType) {",
            "  var i0$classdecl$var0 = function() {",
            "    var $jscomp$super$this = baseType.call(this) || this;",
            "    $jscomp$super$this.c = 'x';", // unique property name
            "    return $jscomp$super$this;",
            "  };",
            "  $jscomp.inherits(i0$classdecl$var0,baseType);",
            "  return i0$classdecl$var0;",
            "}",
            "",
            "var BSuper = mixinX(A);",
            "",
            "var B = function() {",
            "  var $jscomp$super$this = BSuper.call(this) || this;",
            "  $jscomp$super$this.b = 'bProp';", // unique property name
            "  return $jscomp$super$this;",
            "};",
            "$jscomp.inherits(B,BSuper);",
            ""));
  }

  @Test
  public void ambiguatePropertiesWithEs5Mixins() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns",
            lines(
                "", //
                "var goog;",
                "/**",
                " * @param {!Function} childCtor",
                " * @param {!Function} parentCtor",
                " */",
                "goog.inherits = function(childCtor, parentCtor) {};",
                "")));
    externs = externsList.build();

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(
        options,
        lines(
            "", //
            "/** @constructor */",
            "function A() {",
            "   this.aProp = 'aProp';",
            "}",
            "",
            "/**",
            " * @template T",
            " * @param {function(new: T)} baseType",
            " * @return {?}",
            " */",
            "function mixinX(baseType) {",
            "  /**",
            "   * @constructor",
            "   * @extends {baseType}",
            "   */",
            "  const newClass = function() {",
            "    baseType.call(this);",
            "    this.x = 'x';",
            "  };",
            "  goog.inherits(newClass, baseType)",
            "  return newClass;",
            "}",
            // "/** @type {function(new: ?)} */",
            "/** @constructor */",
            "const BSuper = mixinX(A);",
            "",
            "/**",
            " * @constructor",
            " * @extends {BSuper}",
            " */",
            "function B() {",
            "  BSuper.call(this);",
            "  this.bProp = 'bProp';",
            "}",
            "goog.inherits(B, BSuper);",
            "",
            ""),
        lines(
            "", //
            "function A() {",
            "  this.a = 'aProp';", // unique prop name
            "}",
            "",
            "function mixinX(baseType) {",
            "  var newClass = function() {",
            "    baseType.call(this);",
            "    this.c = 'x';", // unique prop name
            "  };",
            "  goog.inherits(newClass,baseType);",
            "  return newClass;",
            "}",
            "",
            "var BSuper = mixinX(A);",
            "",
            "function B() {",
            "  BSuper.call(this);",
            "  this.b = 'bProp';", // unique prop name
            "}",
            "goog.inherits(B,BSuper)",
            ""));
  }

  @Test
  public void testCheckTypes() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    test(options, "var x = x || {}; x.f = function() {}; x.f(3);", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testReplaceCssNames() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setGatherCssNames(true);
    test(
        options,
        "/** @define {boolean} */\n"
        + "var COMPILED = false;\n"
        + "goog.setCssNameMapping({'foo':'bar'});\n"
        + "function getCss() {\n"
        + "  return goog.getCssName('foo');\n"
        + "}",
        "var COMPILED = true;\n"
        + "function getCss() {\n"
        + "  return 'bar';"
        + "}");
    assertThat(lastCompiler.getCssNames()).containsExactly("foo", 1);
  }

  @Test
  public void testReplaceIdGeneratorsTest() {
    CompilerOptions options = createCompilerOptions();
    options.replaceIdGenerators = true;

    options.setIdGenerators(ImmutableMap.<String, RenamingMap>of(
        "xid", new RenamingMap() {
      @Override
      public String get(String value) {
        return ":" + value + ":";
      }
    }));

    test(options, "/** @idGenerator {mapped} */"
         + "var xid = function() {};\n"
         + "function f() {\n"
         + "  return xid('foo');\n"
         + "}",
         "var xid = function() {};\n"
         + "function f() {\n"
         + "  return ':foo:';\n"
         + "}");
  }

  @Test
  public void testRemoveClosureAsserts() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    testSame(
        options,
        "var goog = {};"
        + "goog.asserts.assert(goog);");
    options.removeClosureAsserts = true;
    test(options,
        "var goog = {};"
        + "goog.asserts.assert(goog);",
        "var goog = {};");
  }

  @Test
  public void testDeprecation() {
    String code = "/** @deprecated */ function f() { } function g() { f(); }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckAccessControls.DEPRECATED_NAME);
  }

  @Test
  public void testVisibility() {
    String[] code = {
        "/** @private */ function f() { }",
        "function g() { f(); }"
    };

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS);
  }

  @Test
  public void testUnreachableCode() {
    String code = "function f() { return \n x(); }";

    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.ERROR);
    test(options, code, CheckUnreachableCode.UNREACHABLE_CODE);
  }

  @Test
  public void testMissingReturn() {
    String code =
        "/** @return {number} */ function f() { if (f) { return 3; } }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setWarningLevel(DiagnosticGroups.MISSING_RETURN, CheckLevel.ERROR);
    testSame(options, code);

    options.setCheckTypes(true);
    test(options, code, CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  @Test
  public void testIdGenerators() {
    String code =  "function f() {} f('id');";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setIdGenerators(ImmutableSet.of("f"));
    test(options, code, "function f() {} 'a';");
  }

  @Test
  public void testOptimizeArgumentsArray() {
    String code =  "function f() { return arguments[0]; }";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeArgumentsArray(true);
    String argName = "JSCompiler_OptimizeArgumentsArray_p0";
    test(options, code,
         "function f(" + argName + ") { return " + argName + "; }");
  }

  @Test
  public void testOptimizeParameters() {
    String code = "function f(a) {} f(true);";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "function f() { var a = true;} f();");
  }

  @Test
  public void testOptimizeReturns() {
    String code = "var x; function f() { return x; } f();";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setOptimizeCalls(true);
    test(options, code, "var x; function f() {x; return;} f();");
  }

  @Test
  public void testRemoveAbstractMethods() {
    String code = CLOSURE_BOILERPLATE +
        "var x = {}; x.foo = goog.abstractMethod; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRemoveAbstractMethods(true);
    test(options, code, CLOSURE_COMPILED + " var x$bar = 3;");
  }
  @Test
  public void testCollapseProperties1() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  @Test
  public void testCollapseProperties2() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.collapseObjectLiterals = true;
    test(options, code, "var x$FOO = 5; var x$bar = 3;");
  }

  @Test
  public void testCollapseObjectLiteral1() {
    // Verify collapseObjectLiterals does nothing in global scope
    String code = "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.collapseObjectLiterals = true;
    testSame(options, code);
  }

  @Test
  public void testCollapseObjectLiteral2() {
    String code = "function f() {var x = {}; x.FOO = 5; x.bar = 3;}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.collapseObjectLiterals = true;
    test(
        options,
        code,
        LINE_JOINER.join(
            "function f() {",
            "  var JSCompiler_object_inline_FOO_0 = 5;",
            "  var JSCompiler_object_inline_bar_1 = 3;",
            "}"));
  }

  @Test
  public void testDisambiguateProperties() {
    String code =
        "/** @constructor */ function Foo(){} Foo.prototype.bar = 3;" +
        "/** @constructor */ function Baz(){} Baz.prototype.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setDisambiguateProperties(true);
    options.setCheckTypes(true);
    test(options, code,
        "function Foo(){} Foo.prototype.Foo_prototype$bar = 3;"
        + "function Baz(){} Baz.prototype.Baz_prototype$bar = 3;");
  }

  // When closure-code-removal runs before disambiguate-properties, make sure
  // that removing abstract methods doesn't mess up disambiguation.
  @Test
  public void testDisambiguateProperties2() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveAbstractMethods(true);
    test(options,
        lines(
            "/** @const */ var goog = {};",
            "goog.abstractMethod = function() {};",
            "/** @interface */ function I() {}",
            "I.prototype.a = function(x) {};",
            "/** @constructor @implements {I} */ function Foo() {}",
            "/** @override */ Foo.prototype.a = goog.abstractMethod;",
            "/** @constructor @extends Foo */ function Bar() {}",
            "/** @override */ Bar.prototype.a = function(x) {};"),
        lines(
            "var goog={};",
            "goog.abstractMethod = function() {};",
            "function I(){}",
            "I.prototype.a=function(x){};",
            "function Foo(){}",
            "function Bar(){}",
            "Bar.prototype.a=function(x){};"));
  }

  @Test
  public void testDisambiguatePropertiesWithPropertyInvalidationError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setPropertyInvalidationErrors(
       ImmutableMap.of("a", CheckLevel.ERROR));
    options.setRemoveDeadCode(true);
    options.setRemoveAbstractMethods(true);
    test(
        options,
        lines(
            "function fn(x){return x.a;}",
            "/** @interface */ function I() {}",
            "I.prototype.a = function(x) {};",
            "/** @constructor @implements {I} */ function Foo() {}",
            "/** @override */ Foo.prototype.a = function(x) {};",
            "/** @constructor @extends Foo */ function Bar() {}",
            "/** @override */ Bar.prototype.a = function(x) {};"),
        lines(
            "function fn(x){return x.a;}",
            "function I(){}",
            "I.prototype.a=function(x){};",
            "function Foo(){}",
            "Foo.prototype.a = function(x) {};",
            "function Bar(){}",
            "Bar.prototype.a=function(x){};"),
        PropertyRenamingDiagnostics.INVALIDATION);
  }

  @Test
  public void testMarkPureCalls() {
    String testCode = "function foo() {} foo();";
    CompilerOptions options = createCompilerOptions();
    options.setRemoveDeadCode(true);

    testSame(options, testCode);

    options.setComputeFunctionSideEffects(true);
    test(options, testCode, "function foo() {}");
  }

  @Test
  public void testExtraAnnotationNames() {
    CompilerOptions options = createCompilerOptions();
    options.setExtraAnnotationNames(ImmutableSet.of("TagA", "TagB"));
    test(
        options,
        "/** @TagA */ var f = new Foo(); /** @TagB */ f.bar();",
        "var f = new Foo(); f.bar();");
  }

  @Test
  public void testDevirtualizeMethods() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizeMethods(true);
    test(
        options,
        "/** @constructor */ var Foo = function() {}; "
        + "Foo.prototype.bar = function() {};"
        + "(new Foo()).bar();",
        "var Foo = function() {};"
        + "var JSCompiler_StaticMethods_bar = "
        + "    function(JSCompiler_StaticMethods_bar$self) {};"
        + "JSCompiler_StaticMethods_bar(new Foo());");
  }

  @Test
  public void testCheckConsts() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineConstantVars(true);
    test(options, "var FOO = true; FOO = false", ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  @Test
  public void testAllChecksOn() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    options.setGenerateExports(true);
    options.exportTestFunctions = true;
    options.setClosurePass(true);
    options.setCheckMissingGetCssNameLevel(CheckLevel.ERROR);
    options.setCheckMissingGetCssNameBlacklist("goog");
    options.syntheticBlockStartMarker = "synStart";
    options.syntheticBlockEndMarker = "synEnd";
    options.setCheckSymbols(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(options, CLOSURE_BOILERPLATE, CLOSURE_COMPILED);
  }

  @Test
  public void testTypeCheckingWithSyntheticBlocks() {
    CompilerOptions options = createCompilerOptions();
    options.syntheticBlockStartMarker = "synStart";
    options.syntheticBlockEndMarker = "synEnd";
    options.setCheckTypes(true);

    // We used to have a bug where the CFG drew an
    // edge straight from synStart to f(progress).
    // If that happens, then progress will get type {number|undefined}.
    testNoWarnings(
        options,
        lines(
            "/** @param {number} x */ function f(x) {}",
            "function g() {",
            "  synStart('foo');",
            "  var progress = 1;",
            "  f(progress);",
            "  synEnd('foo');",
            "}"));
  }

  @Test
  public void testCompilerDoesNotBlowUpIfUndefinedSymbols() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);

    // Disable the undefined variable check.
    options.setWarningLevel(
        DiagnosticGroup.forType(VarCheck.UNDEFINED_VAR_ERROR),
        CheckLevel.OFF);

    // The compiler used to throw an IllegalStateException on this.
    testSame(options, "var x = {foo: y};");
  }

  // Make sure that if we change variables which are constant to have
  // $$constant appended to their names, we remove that tag before
  // we finish.
  @Test
  public void testConstantTagsMustAlwaysBeRemoved() {
    CompilerOptions options = createCompilerOptions();

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    String originalText =
        "var G_GEO_UNKNOWN_ADDRESS=1;\n"
        + "function foo() {"
        + "  var localVar = 2;\n"
        + "  if (G_GEO_UNKNOWN_ADDRESS == localVar) {\n"
        + "    alert('A'); }}";
    String expectedText = "var G_GEO_UNKNOWN_ADDRESS=1;" +
        "function foo(){var a=2;if(G_GEO_UNKNOWN_ADDRESS==a){alert('A')}}";

    test(options, originalText, expectedText);
  }

  @GwtIncompatible("com.google.javascript.jscomp.ReplaceMessages is incompatible")
  @Test
  public void testReplaceMessages() {
    CompilerOptions options = createCompilerOptions();
    String prefix = "var goog = {}; goog.getMsg = function() {};";
    testSame(options, prefix + "var MSG_HI = goog.getMsg('hi');");

    options.setMessageBundle(new EmptyMessageBundle());
    test(options, prefix + "/** @desc xyz */ var MSG_HI = goog.getMsg('hi');",
        prefix + "var MSG_HI = 'hi';");
  }

  @Test
  public void testCheckGlobalNames() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckGlobalNamesLevel(CheckLevel.ERROR);
    test(options, "var x = {}; var y = x.z;", CheckGlobalNames.UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testInlineGetters() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function Foo() {} Foo.prototype.bar = function() { return 3; };" +
        "var x = new Foo(); x.bar();";

    testSame(options, code);
    options.inlineGetters = true;

    test(options, code,
        "function Foo() {} Foo.prototype.bar = function() { return 3 };"
        + "var x = new Foo(); 3;");
  }

  @Test
  public void testInlineGettersWithAmbiguate() {
    CompilerOptions options = createCompilerOptions();

    String code =
        "/** @constructor */" +
        "function Foo() {}" +
        "/** @type {number} */ Foo.prototype.field;" +
        "Foo.prototype.getField = function() { return this.field; };" +
        "/** @constructor */" +
        "function Bar() {}" +
        "/** @type {string} */ Bar.prototype.field;" +
        "Bar.prototype.getField = function() { return this.field; };" +
        "new Foo().getField();" +
        "new Bar().getField();";

    testSame(options, code);

    options.inlineGetters = true;

    test(options, code,
        "function Foo() {}"
        + "Foo.prototype.field;"
        + "Foo.prototype.getField = function() { return this.field; };"
        + "function Bar() {}"
        + "Bar.prototype.field;"
        + "Bar.prototype.getField = function() { return this.field; };"
        + "new Foo().field;"
        + "new Bar().field;");

    options.setCheckTypes(true);
    options.setAmbiguateProperties(true);

    // Propagating the wrong type information may cause ambiguate properties
    // to generate bad code.
    testSame(options, code);
  }

  @Test
  public void testInlineVariables() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x);";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "(function foo() {})(3);");
  }

  @Test
  public void testInlineConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "function foo() {} var x = 3; foo(x); var YYY = 4; foo(YYY);";
    testSame(options, code);

    options.setInlineConstantVars(true);
    test(options, code, "function foo() {} foo(3); foo(4);");
  }

  @Test
  public void testMinimizeExits() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() {" +
        "  if (window.foo) return; window.h(); " +
        "}";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code,
        "function f() {"
        + "  window.foo || window.h(); "
        + "}");
  }

  @Test
  public void testFoldConstants() {
    CompilerOptions options = createCompilerOptions();
    String code = "if (true) { window.foo(); }";
    testSame(options, code);

    options.setFoldConstants(true);
    test(options, code, "window.foo();");
  }

  @Test
  public void testRemoveUnreachableCode() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    String code = "function f() { return; f(); }";
    testSame(options, code);

    options.setRemoveDeadCode(true);
    test(options, code, "function f() {}");
  }

  @Test
  public void testRemoveUnusedPrototypeProperties1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function Foo() {} " +
        "Foo.prototype.bar = function() { return new Foo(); };";
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    test(options, code, "function Foo() {}");
  }

  @Test
  public void testRemoveUnusedPrototypeProperties2() {
    CompilerOptions options = createCompilerOptions();
    String code = "function Foo() {} " +
        "Foo.prototype.bar = function() { return new Foo(); };" +
        "function f(x) { x.bar(); }";
    testSame(options, code);

    options.setRemoveUnusedPrototypeProperties(true);
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "");
  }

  @Test
  public void testSmartNamePassBug11163486() {
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.extraSmartNameRemoval = true;
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code = "/** @constructor */ function A() {} " +
        "A.prototype.foo = function() { " +
        "  window.console.log('A'); " +
        "}; " +
        "/** @constructor */ function B() {} " +
        "B.prototype.foo = function() { " +
        "  window.console.log('B'); " +
        "};" +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.foo(); " +
        "  window['b'] = new B; " +
        "}; " +
        "function notCalled() { " +
        "  var something = {}; " +
        "  something.foo(); " +
        "}";

    String expected = "function A() {} " +
        "A.prototype.A_prototype$foo = function() { " +
        "  window.console.log('A'); " +
        "}; " +
        "function B() {} " +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.A_prototype$foo(); " +
        "  window['b'] = new B; " +
        "}";

    test(options, code, expected);
  }

  @Test
  public void testDeadCodeHasNoDisambiguationSideEffects() {
    // This test case asserts that unreachable code does not
    // confuse the disambigation process and type inferencing.
    CompilerOptions options = createCompilerOptions();

    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);
    options.setRemoveDeadCode(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setSmartNameRemoval(true);
    options.extraSmartNameRemoval = true;
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    String code =
        "/** @constructor */ function A() {} "
        + "A.prototype.always = function() { "
        + "  window.console.log('AA'); "
        + "}; "
        + "A.prototype.sometimes = function() { "
        + "  window.console.log('SA'); "
        + "}; "
        + "/** @constructor */ function B() {} "
        + "B.prototype.always = function() { "
        + "  window.console.log('AB'); "
        + "};"
        + "B.prototype.sometimes = function() { "
        + "  window.console.log('SB'); "
        + "};"
        + "/** @constructor @struct @template T */ function C() {} "
        + "/** @param {!T} x */ C.prototype.touch = function(x) { "
        + "  return x.sometimes(); "
        + "}; "
        + "window['main'] = function() { "
        + "  var a = window['a'] = new A; "
        + "  a.always(); "
        + "  a.sometimes(); "
        + "  var b = window['b'] = new B; "
        + "  b.always(); "
        + "};"
        + "function notCalled() { "
        + "  var something = {}; "
        + "  something.always(); "
        + "  var c = new C; "
        + "  c.touch(something);"
        + "}";

    // B.prototype.sometimes should be stripped out, as it is not used, and the
    // type ambiguity in function notCalled is unreachable.
    String expected = "function A() {} " +
        "A.prototype.A_prototype$always = function() { " +
        "  window.console.log('AA'); " +
        "}; " +
        "A.prototype.A_prototype$sometimes = function(){ " +
        "  window.console.log('SA'); " +
        "}; " +
        "function B() {} " +
        "B.prototype.B_prototype$always=function(){ " +
        "  window.console.log('AB'); " +
        "};" +
        "window['main'] = function() { " +
        "  var a = window['a'] = new A; " +
        "  a.A_prototype$always(); " +
        "  a.A_prototype$sometimes(); " +
        "  (window['b'] = new B).B_prototype$always(); " +
        "}";


    test(options, code, expected);

  }

  @Test
  public void testQMarkTIsNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code = LINE_JOINER.join(
        "/** @constructor @template T */",
        "function F() {}",
        "",
        "/** @return {?T} */",
        "F.prototype.foo = function() {",
        "  return null;",
        "}",
        "",
        "/** @type {F<string>} */",
        "var f = new F;",
        "/** @type {string} */",
        "var s = f.foo(); // Type error: f.foo() has type {?string}.");

    test(options, code, TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testTIsNotNullable() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    String code = LINE_JOINER.join("/** @constructor @template T */",
        "function F() {}",
        "",
        "/** @param {T} t */",
        "F.prototype.foo = function(t) {",
        "}",
        "",
        "/** @type {F<string>} */",
        "var f = new F;",
        "/** @type {?string} */",
        "var s = null;",
        "f.foo(s); // Type error: f.foo() takes a {string}, not a {?string}");

    test(options, code, TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testDeadAssignmentsElimination() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; 4; x = 5; return x; } f(); ";
    testSame(options, code);

    options.setDeadAssignmentElimination(true);
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() { 3; 4; var x = 5; return x; } f();");
  }

  @Test
  public void testPreservesCastInformation() {
    // Only set the suffix instead of both prefix and suffix, because J2CL pass
    // looks for that exact suffix, and IntegrationTestCase adds an input
    // id number between prefix and suffix.
    inputFileNameSuffix = "vmbootstrap/Arrays.impl.java.js";
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "var Arrays = function() {};",
        "Arrays.$create = function() { return {}; }",
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        "var x = /** @type {!Foo} */ (Arrays.$create()).myprop;");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setDisambiguateProperties(true);

    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "var Arrays = function() {};",
            "Arrays.$create = function() { return {}; }",
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "var x = {}.Foo$myprop;"));
  }

  @Test
  public void testInliningLocalVarsPreservesCasts() {
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        "/** @return {Object} */",
        "function getSomething() {",
        "  var x = new Bar();",
        "  return new Foo();",
        "}",
        "(function someMethod() {",
        "  var x = getSomething();",
        "  var y = /** @type {Foo} */ (x).myprop;",
        "  return 1 != y;",
        "})()");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setExtraSmartNameRemoval(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "/** @return {Object} */",
            "function getSomething() {",
            "  var x = new Bar();",
            "  return new Foo();",
            "}",
            "(function someMethod() {",
            "  return 1 != getSomething().Foo$myprop;",
            "})()"));
  }

  /**
   * Tests that inlining of local variables doesn't destroy type information when the cast is from a
   * non-nullable type to a nullable type.
   */
  @Test
  public void testInliningLocalVarsPreservesCastsNullable() {
    String code = LINE_JOINER.join(
        "/** @constructor */",
        "function Foo() { this.myprop = 1; }",
        "/** @constructor */",
        "function Bar() { this.myprop = 2; }",
        // Note that this method return a non-nullable type.
        "/** @return {!Object} */",
        "function getSomething() {",
        "  var x = new Bar();",
        "  return new Foo();",
        "}",
        "(function someMethod() {",
        "  var x = getSomething();",
        // Note that this casts from !Object to ?Foo.
        "  var y = /** @type {Foo} */ (x).myprop;",
        "  return 1 != y;",
        "})()");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setCheckTypes(true);
    options.setSmartNameRemoval(true);
    options.setFoldConstants(true);
    options.setExtraSmartNameRemoval(true);
    options.setInlineVariables(true);
    options.setDisambiguateProperties(true);

    // Verify that property disambiguation continues to work after the local
    // var and cast have been removed by inlining.
    test(options, code,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() { this.Foo$myprop = 1; }",
            "/** @constructor */",
            "function Bar() { this.Bar$myprop = 2; }",
            "/** @return {Object} */",
            "function getSomething() {",
            "  var x = new Bar();",
            "  return new Foo();",
            "}",
            "(function someMethod() {",
            "  return 1 != getSomething().Foo$myprop;",
            "})()"));
  }

  @Test
  public void testInlineFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { return 3; } f(); ";
    testSame(options, code);

    options.setInlineFunctions(Reach.ALL);
    test(options, code, "3;");
  }

  @Test
  public void testRemoveUnusedVars1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f(x) {} f();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "function f() {} f();");
  }

  @Test
  public void testRemoveUnusedVars2() {
    CompilerOptions options = createCompilerOptions();
    String code = "(function f(x) {})();var g = function() {}; g();";
    testSame(options, code);

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, code, "(function() {})();var g = function() {}; g();");

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code, "(function f() {})();var g = function $g$() {}; g();");
  }

  @Test
  public void testCrossChunkCodeMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var x = 1;",
      "x;",
    };
    testSame(options, code);

    options.setCrossChunkCodeMotion(true);
    test(options, code,
        new String[] {
            "", "var x = 1; x;",
        });
  }

  @Test
  public void testCrossChunkMethodMotion() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var Foo = function() {}; Foo.prototype.bar = function() {};" +
      "var x = new Foo();",
      "x.bar();",
    };
    testSame(options, code);

    options.setCrossChunkMethodMotion(true);
    test(
        options,
        code,
        new String[] {
          CrossChunkMethodMotion.STUB_DECLARATIONS
              + "var Foo = function() {};"
              + "Foo.prototype.bar=JSCompiler_stubMethod(0); var x=new Foo;",
          "Foo.prototype.bar=JSCompiler_unstubMethod(0,function(){}); x.bar()",
        });
  }

  @Test
  public void testCrossChunkDepCheck() {
    CompilerOptions options = createCompilerOptions();
    String[] code = new String[] {
      "var goog = {}; new goog.Foo();",
      "/** @constructor */ goog.Foo = function() {};",
    };
    testSame(options, code);

    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.WARNING);
    test(options, code, code, CheckGlobalNames.STRICT_MODULE_DEP_QNAME);
  }

  @Test
  public void testFlowSensitiveInlineVariables1() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() { var x = 3; x = 5; return x; }";
    testSame(options, code);

    options.setInlineVariables(true);
    test(options, code, "function f() { var x = 3; return 5; }");

    String unusedVar = "function f() { var x; x = 5; return x; } f()";
    test(options, unusedVar, "(function f() { var x; return 5; })()");

    options.setRemoveUnusedVariables(Reach.ALL);
    test(options, unusedVar, "(function () { return 5; })()");
  }

  @Test
  public void testFlowSensitiveInlineVariables2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    test(options,
        "function f () {\n" +
        "    var ab = 0;\n" +
        "    ab += '-';\n" +
        "    alert(ab);\n" +
        "}",
        "function f () {\n" +
        "    alert('0-');\n" +
        "}");
  }

  @Test
  public void testCollapseAnonymousFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    testSame(options, code);

    options.setCollapseAnonymousFunctions(true);
    test(options, code, "function f() {}");
  }

  @Test
  public void testMoveFunctionDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f(); function f() { return 3; }";
    testSame(options, code);

    options.moveFunctionDeclarations = true;
    test(options, code, "var f = function() { return 3; }; var x = f();");
  }

  @Test
  public void testNameAnonymousFunctions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    testSame(options, code);

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED);
    test(options, code, "var f = function $() {}");
    assertThat(lastCompiler.getResult().namedAnonFunctionMap).isNotNull();

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code, "var f = function $f$() {}");
    assertThat(lastCompiler.getResult().namedAnonFunctionMap).isNull();
  }

  @Test
  public void testNameAnonymousFunctionsWithVarRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setRemoveUnusedVariables(CompilerOptions.Reach.LOCAL_ONLY);
    options.setInlineVariables(true);
    String code = "var f = function longName() {}; var g = function() {};" +
        "function longerName() {} var i = longerName;";
    test(options, code,
         "var f = function() {}; var g = function() {}; " +
         "var i = function() {};");

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.MAPPED);
    test(options, code,
        "var f = function longName() {}; var g = function $() {};"
        + "var i = function longerName(){};");
    assertThat(lastCompiler.getResult().namedAnonFunctionMap).isNotNull();

    options.setAnonymousFunctionNaming(AnonymousFunctionNamingPolicy.UNMAPPED);
    test(options, code,
        "var f = function longName() {}; var g = function $g$() {};"
        + "var i = function longerName(){};");
    assertThat(lastCompiler.getResult().namedAnonFunctionMap).isNull();
  }

  @Test
  public void testExtractPrototypeMemberDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var f = function() {};";
    String expected = "var a; var b = function() {}; a = b.prototype;";
    for (int i = 0; i < 10; i++) {
      code += "f.prototype.a = " + i + ";";
      expected += "a.a = " + i + ";";
    }
    testSame(options, code);

    options.setExtractPrototypeMemberDeclarations(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, expected);
  }

  @Test
  public void testDevirtualizationAndExtractPrototypeMemberDeclarations() {
    CompilerOptions options = createCompilerOptions();
    options.setDevirtualizeMethods(true);
    options.setCollapseAnonymousFunctions(true);
    options.setExtractPrototypeMemberDeclarations(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    String code = "var f = function() {};";
    String expected = "var a; function b() {} a = b.prototype;";
    for (int i = 0; i < 10; i++) {
      code += "f.prototype.argz = function() {arguments};";
      code += "f.prototype.devir" + i + " = function() {};";

      char letter = (char) ('d' + i);

      // skip i,j,o (reserved)
      if (letter >= 'i') {
        letter++;
      }
      if (letter >= 'j') {
        letter++;
      }
      if (letter >= 'o') {
        letter++;
      }

      expected += "a.argz = function() {arguments};";
      expected += "function " + letter + "(c){}";
    }

    code += "var F = new f(); F.argz();";
    expected += "var q = new b(); q.argz();";

    for (int i = 0; i < 10; i++) {
      code += "F.devir" + i + "();";

      char letter = (char) ('d' + i);

      // skip i,j,o (reserved)
      if (letter >= 'i') {
        letter++;
      }
      if (letter >= 'j') {
        letter++;
      }
      if (letter >= 'o') {
        letter++;
      }

      expected += letter + "(q);";
    }
    test(options, code, expected);
  }

  @Test
  public void testPropertyRenaming() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() { return this.foo + this['bar'] + this.Baz; }" +
        "f.prototype.bar = 3; f.prototype.Baz = 3;";
    String all =
        "function f() { return this.c + this['bar'] + this.a; }"
            + "f.prototype.b = 3; f.prototype.a = 3;";
    testSame(options, code);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, all);
  }

  @Test
  public void testConvertToDottedProperties() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() { return this['bar']; } f.prototype.bar = 3;";
    String expected =
        "function f() { return this.bar; } f.prototype.a = 3;";
    testSame(options, code);

    options.convertToDottedProperties = true;
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    test(options, code, expected);
  }

  @Test
  public void testRewriteFunctionExpressions() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a = function() {};";
    String expected = "function JSCompiler_emptyFn(){return function(){}} " +
        "var a = JSCompiler_emptyFn();";
    for (int i = 0; i < 10; i++) {
      code += "a = function() {};";
      expected += "a = JSCompiler_emptyFn();";
    }
    testSame(options, code);

    options.setRewriteFunctionExpressions(true);
    test(options, code, expected);
  }

  @Test
  public void testAliasAllStrings() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "function f() {" + "  return 'aaaaaaaaaaaaaaaaaaaa' + 'aaaaaaaaaaaaaaaaaaaa';" + "}";
    String expected =
        "var $$S_aaaaaaaaaaaaaaaaaaaa = 'aaaaaaaaaaaaaaaaaaaa';"
            + "function f() {"
            + "  return $$S_aaaaaaaaaaaaaaaaaaaa + $$S_aaaaaaaaaaaaaaaaaaaa;"
            + "}";
    testSame(options, code);

    options.setAliasAllStrings(true);
    test(options, code, expected);
  }

  @Test
  public void testRenameVars1() {
    CompilerOptions options = createCompilerOptions();
    String code =
        "var abc = 3; function f() { var xyz = 5; return abc + xyz; }";
    String local = "var abc = 3; function f() { var a = 5; return abc + a; }";
    String all = "var a = 3; function c() { var b = 5; return a + b; }";
    testSame(options, code);

    options.setVariableRenaming(VariableRenamingPolicy.LOCAL);
    test(options, code, local);

    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, all);

    options.reserveRawExports = true;
  }

  @Test
  public void testRenameVars2() {
    CompilerOptions options = createCompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    String code = "var abc = 3; function f() { window['a'] = 5; }";
    String noexport = "var a = 3;   function b() { window['a'] = 5; }";
    String export =   "var b = 3;   function c() { window['a'] = 5; }";

    options.reserveRawExports = false;
    test(options, code, noexport);

    options.reserveRawExports = true;
    test(options, code, export);
  }

  @Test
  public void testRenameLabels() {
    CompilerOptions options = createCompilerOptions();
    String code = "longLabel: for(;true;) { break longLabel; }";
    String expected = "a: for(;true;) { break a; }";
    testSame(options, code);

    options.labelRenaming = true;
    test(options, code, expected);
  }

  @Test
  public void testBadBreakStatementInIdeMode() {
    // Ensure that type-checking doesn't crash, even if the CFG is malformed.
    // This can happen in IDE mode.
    CompilerOptions options = createCompilerOptions();
    options.setDevMode(DevMode.OFF);
    options.setIdeMode(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(options,
         "function f() { try { } catch(e) { break; } }",
         RhinoErrorReporter.PARSE_ERROR);
  }

  // https://github.com/google/closure-compiler/issues/2388
  @Test
  public void testNoCrash_varInCatch() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineFunctions(Reach.ALL);

    test(
        options,
        LINE_JOINER.join(
            "(function() {",
            "  try {",
            "    x = 2;",
            "  } catch (e) {",
            "    var x = 1;",
            "  }",
            "})();"),
      LINE_JOINER.join(
            "{ try {",
            "    x$jscomp$inline_0=2",
            "  } catch(e) {",
            "    var x$jscomp$inline_0=1",
            "  }",
            "}"));
  }

  // https://github.com/google/closure-compiler/issues/2364
  @Test
  public void testNoCrash_varInCatch2() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    testSame(
        options,
        LINE_JOINER.join(
            "function foo() {",
            "  var msg;",
            "}",
            "",
            "function bar() {",
            "  msg;",
            "  try {}",
            "  catch(err) {",
            "    var msg;",
            "  }",
            "}"));
  }

  // http://blickly.github.io/closure-compiler-issues/#63
  @Test
  public void testIssue63SourceMap() {
    CompilerOptions options = createCompilerOptions();
    String code = "var a;";

    options.skipNonTranspilationPasses = true;
    options.sourceMapOutputPath = "./src.map";

    Compiler compiler = compile(options, code);
    compiler.toSource();
  }

  @Test
  public void testRegExp1() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);

    String code = "/(a)/.test('a');";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String expected = "";

    test(options, code, expected);
  }

  @Test
  public void testRegExp2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code = "/(a)/.test('a');var a = RegExp.$1";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, CheckRegExp.REGEXP_REFERENCE);

    options.setWarningLevel(DiagnosticGroups.CHECK_REGEXP, CheckLevel.OFF);

    testSame(options, code);
  }

  @Test
  public void testFoldLocals1() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    // An external object, whose constructor has no side-effects,
    // and whose method "go" only modifies the object.
    String code = "new Widget().go();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, "");
  }

  @Test
  public void testFoldLocals2() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);

    // An external function that returns a local object that the
    // method "go" that only modifies the object.
    String code = "widgetToken().go();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    test(options, code, "widgetToken()");
  }

  @Test
  public void testFoldLocals3() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    // A function "f" who returns a known local object, and a method that
    // modifies only modifies that.
    String definition = "function f(){return new Widget()}";
    String call = "f().go();";
    String code = definition + call;

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    // BROKEN
    //test(options, code, definition);
    testSame(options, code);
  }

  @Test
  public void testFoldLocals4() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        "/** @constructor */\n"
        + "function InternalWidget(){this.x = 1;}"
        + "InternalWidget.prototype.internalGo = function (){this.x = 2};"
        + "new InternalWidget().internalGo();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String optimized =
        ""
        + "function InternalWidget(){this.x = 1;}"
        + "InternalWidget.prototype.internalGo = function (){this.x = 2};";

    test(options, code, optimized);
  }

  @Test
  public void testFoldLocals5() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function fn(){var a={};a.x={};return a}"
        + "fn().x.y = 1;";

    // "fn" returns a unescaped local object, we should be able to fold it,
    // but we don't currently.
    String result = ""
        + "function fn(){var a={x:{}};return a}"
        + "fn().x.y = 1;";

    test(options, code, result);

    options.setComputeFunctionSideEffects(true);

    test(options, code, result);
  }

  @Test
  public void testFoldLocals6() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function fn(){return {}}"
        + "fn().x.y = 1;";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    testSame(options, code);
  }

  @Test
  public void testFoldLocals7() {
    CompilerOptions options = createCompilerOptions();

    options.setFoldConstants(true);

    String code =
        ""
        + "function InternalWidget(){return [];}"
        + "Array.prototype.internalGo = function (){this.x = 2};"
        + "InternalWidget().internalGo();";

    testSame(options, code);

    options.setComputeFunctionSideEffects(true);

    String optimized =
        ""
        + "function InternalWidget(){return [];}"
        + "Array.prototype.internalGo = function (){this.x = 2};";

    test(options, code, optimized);
  }

  @Test
  public void testVarDeclarationsIntoFor() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "var a = 1; for (var b = 2; ;) {}";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "for (var a = 1, b = 2; ;) {}");
  }

  @Test
  public void testExploitAssigns() {
    CompilerOptions options = createCompilerOptions();

    options.setCollapseVariableDeclarations(false);

    String code = "a = 1; b = a; c = b";

    testSame(options, code);

    options.setCollapseVariableDeclarations(true);

    test(options, code, "c=b=a=1");
  }

  @Test
  public void testRecoverOnBadExterns() {
    // This test is for a bug in a very narrow set of circumstances:
    // 1) externs validation has to be off.
    // 2) aliasExternals has to be on.
    // 3) The user has to reference a "normal" variable in externs.
    // This case is handled at checking time by injecting a
    // synthetic extern variable, and adding a "@suppress {duplicate}" to
    // the normal code at compile time. But optimizations may remove that
    // annotation, so we need to make sure that the variable declarations
    // are de-duped before that happens.
    CompilerOptions options = createCompilerOptions();

    externs = ImmutableList.of(SourceFile.fromCode("externs", "extern.foo"));

    test(options,
         "var extern; " +
         "function f() { return extern + extern + extern + extern; }",
         "var extern; " +
         "function f() { return extern + extern + extern + extern; }",
         VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  @Test
  public void testDuplicateVariablesInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckSymbols(true);
    externs = ImmutableList.of(SourceFile.fromCode(
        "externs", "var externs = {}; /** @suppress {duplicate} */ var externs = {};"));
    testSame(options, "");
  }

  @Test
  public void testEs6ClassInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode("extraExterns", "class ExternalClass { externalMethod() {} }"));
    externs = externsList.build();

    testSame(options, "(new ExternalClass).externalMethod();");
    test(options, "(new ExternalClass).nonexistentMethod();", TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testGeneratorFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns", "/** @return {!Iterable<number>} */ function* gen() {}"));
    externs = externsList.build();

    test(
        options,
        "for (let x of gen()) { x.call(); }",
        // Property call never defined on Number.
        TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testAsyncFunctionInExterns() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns", "/** @return {!Promise<number>} */ async function init() {}"));
    externs = externsList.build();

    test(
        options,
        "init().then((n) => { n.call(); });",
        // Property call never defined on Number.
        TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testAsyncFunctionSuper() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPrettyPrint(true);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {};"));
    externs = externsList.build();

    test(
        options,
        lines(
            "class Foo {",
            "  async bar() {",
            "    console.log('bar');",
            "  }",
            "}",
            "",
            "class Baz extends Foo {",
            "  async bar() {",
            "    await Promise.resolve();",
            "    super.bar();",
            "  }",
            "}\n"),
        lines(
            "class Foo {",
            "  bar() {",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(function*() {",
            "      console.log(\"bar\");",
            "    });",
            "  }",
            "}",
            "class Baz extends Foo {",
            "  bar() {",
            "    const $jscomp$async$this = this, $jscomp$async$super$get$bar =",
            "        () => Object.getPrototypeOf(Object.getPrototypeOf(this)).bar;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(function*() {",
            "      yield Promise.resolve();",
            "      $jscomp$async$super$get$bar().call($jscomp$async$this);",
            "    });",
            "  }",
            "}"));
  }

  @Test
  public void testAsyncIterationSuper() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setPrettyPrint(true);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    // include externs definitions for the stuff that would have been injected
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {};"));
    externs = externsList.build();

    test(
        options,
        lines(
            "class Foo {",
            "  async *bar() {",
            "    console.log('bar');",
            "  }",
            "}",
            "",
            "class Baz extends Foo {",
            "  async *bar() {",
            "    super.bar().next();",
            "  }",
            "}\n"),
        lines(
            "class Foo {",
            "  bar() {",
            "    return new $jscomp.AsyncGeneratorWrapper(function*() {",
            "      console.log(\"bar\");",
            "    }());",
            "  }",
            "}",
            "class Baz extends Foo {",
            "  bar() {",
            "    const $jscomp$asyncIter$this = this,",
            "          $jscomp$asyncIter$super$get$bar =",
            "              () => Object.getPrototypeOf(Object.getPrototypeOf(this)).bar;",
            "    return new $jscomp.AsyncGeneratorWrapper(function*() {",
            "      $jscomp$asyncIter$super$get$bar().call($jscomp$asyncIter$this).next();",
            "    }());",
            "  }",
            "}"));
  }

  @Test
  public void testInitSymbolIteratorInjection() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    useNoninjectingCompiler = true;
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {};"));
    externs = externsList.build();
    test(
        options,
        lines(
            "var itr = {",
            "  next: function() { return { value: 1234, done: false }; },",
            "};",
            "itr[Symbol.iterator] = function() { return itr; }"),
        lines(
            "var itr = {",
            "  next: function() { return { value: 1234, done: false }; },",
            "};",
            "itr[Symbol.iterator] = function() { return itr; }"));
  }

  @Test
  public void testInitSymbolIteratorInjectionWithES6Syntax() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    useNoninjectingCompiler = true;
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {};"));
    externs = externsList.build();
    test(
        options,
        lines(
            "let itr = {",
            "  next: function() { return { value: 1234, done: false }; },",
            "};",
            "itr[Symbol.iterator] = function() { return itr; }"),
        lines(
            "var itr = {",
            "  next: function() { return { value: 1234, done: false }; },",
            "};",
            "itr[Symbol.iterator] = function() { return itr; }"));
  }

  @Test
  public void testLanguageMode() {
    CompilerOptions options = createCompilerOptions();

    String code1 = "var a = {get f(){}}";
    String code2 = "var a = {set f(x){}}";

    // Tests that getters/setters are not parsed in Language_IN=ES3
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    Compiler compiler = compile(options, code1);
    checkUnexpectedErrorsOrWarnings(compiler, 1);
    assertThat(compiler.getErrors().get(0).toString())
        .isEqualTo(
            "JSC_PARSE_ERROR. Parse error."
                + " getters are not supported in older versions of JavaScript."
                + " If you are targeting newer versions of JavaScript,"
                + " set the appropriate language_in option."
                + " at i0.js line 1 : 0");

    // Tests no error when language_OUT=ES5
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    testSame(options, code1);
    testSame(options, code2);
  }

  @Test
  public void testLanguageMode2() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);

    String code = "var a  = 2; delete a;";

    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    testSame(options, code);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    testSame(options, code);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    test(options,
        code,
        code,
        StrictModeCheck.DELETE_VARIABLE);
  }

  // http://blickly.github.io/closure-compiler-issues/#598
  @Test
  public void testIssue598() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    String code =
        "'use strict';\n" +
        "function App() {}\n" +
        "App.prototype = {\n" +
        "  get appData() { return this.appData_; },\n" +
        "  set appData(data) { this.appData_ = data; }\n" +
        "};";

    testSame(options, code);
  }

  @Test
  public void testCheckStrictModeGeneratorFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);

    externs = ImmutableList.of(
        SourceFile.fromCode("externs", "var arguments; arguments.callee;"));

    String code = "function *gen() { arguments.callee; }";

    test(options, new String[] {code}, null, StrictModeCheck.ARGUMENTS_CALLEE_FORBIDDEN);
  }

  // http://blickly.github.io/closure-compiler-issues/#701
  @Test
  public void testIssue701() {
    // Check ASCII art in license comments.
    String ascii = "/**\n" +
        " * @preserve\n" +
        "   This\n" +
        "     is\n" +
        "       ASCII    ART\n" +
        "*/";
    String result = "/*\n\n" +
        "   This\n" +
        "     is\n" +
        "       ASCII    ART\n" +
        "*/\n";
    testSame(createCompilerOptions(), ascii);
    assertThat(lastCompiler.toSource()).isEqualTo(result);
  }

  @Test
  public void testCoalesceVariableNames() {
    CompilerOptions options = createCompilerOptions();
    String code = "function f() {var x = 3; var y = x; var z = y; return z;}";
    testSame(options, code);

    options.setCoalesceVariableNames(true);
    test(options, code, "function f() {var x = 3; x = x; x = x; return x;}");
  }

  @Test
  public void testCoalesceVariables() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    options.setFoldConstants(false);
    options.setCoalesceVariableNames(true);

    String code =
        "function f(a) {"
        + "  if (a) {"
        + "    return a;"
        + "  } else {"
        + "    var b = a;"
        + "    return b;"
        + "  }"
        + "  return a;"
        + "}";
    String expected =
        "function f(a) {" +
        "  if (a) {" +
        "    return a;" +
        "  } else {" +
        "    a = a;" +
        "    return a;" +
        "  }" +
        "  return a;" +
        "}";

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(false);

    code =
        "function f(a) {"
        + "  if (a) {"
        + "    return a;"
        + "  } else {"
        + "    var b = a;"
        + "    return b;"
        + "  }"
        + "  return a;"
        + "}";
    expected =
        "function f(a) {" +
        "  if (!a) {" +
        "    var b = a;" +
        "    return b;" +
        "  }" +
        "  return a;" +
        "}";

    test(options, code, expected);

    options.setFoldConstants(true);
    options.setCoalesceVariableNames(true);

    expected =
        "function f(a) {"
        + "  return a;"
        + "}";

    test(options, code, expected);
  }

  @Test
  public void testLateStatementFusion() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "while(a){a();if(b){b();b()}}", "for(;a;)a(),b&&(b(),b())");
  }

  @Test
  public void testLateConstantReordering() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "if (((x < 1 || x > 1) || 1 < x) || 1 > x) { alert(x) }",
        "   (((1 > x || 1 < x) || 1 < x) || 1 > x) && alert(x) ");
  }

  @Test
  public void testsyntheticBlockOnDeadAssignments() {
    CompilerOptions options = createCompilerOptions();
    options.setDeadAssignmentElimination(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    test(options, "var x; x = 1; START(); x = 1;END();x()",
                  "var x; x = 1;{START();{x = 1}END()}x()");
  }

  @Test
  public void testBug4152835() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    test(options, "START();END()", "{START();{}END()}");
  }

  @Test
  public void testNoFuseIntoSyntheticBlock() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.syntheticBlockStartMarker = "START";
    options.syntheticBlockEndMarker = "END";
    testSame(options, "for(;;) { x = 1; {START(); {z = 3} END()} }");
    testSame(options, "x = 1; y = 2; {START(); {z = 3} END()} f()");
  }

  @Test
  public void testBug5786871() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "function () {}");
  }

  // http://blickly.github.io/closure-compiler-issues/#378
  @Test
  public void testIssue378() {
    CompilerOptions options = createCompilerOptions();
    options.setInlineVariables(true);
    testSame(
        options,
        "function f(c) {var f = c; arguments[0] = this;"
        + "    f.apply(this, arguments); return this;}");
  }

  // http://blickly.github.io/closure-compiler-issues/#550
  @Test
  public void testIssue550() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setFoldConstants(true);
    options.setInlineVariables(true);
    test(
        options,
        "function f(h) {\n"
        + "  var a = h;\n"
        + "  a = a + 'x';\n"
        + "  a = a + 'y';\n"
        + "  return a;\n"
        + "}",
        "function f(a) { return a += 'xy'; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#1168
  @Test
  public void testIssue1168() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);
    test(
        options,
        "while (function () {\n" + " function f(){};\n" + " L: while (void(f += 3)) {}\n" + "}) {}",
        "for( ; ; );");
  }

  // http://blickly.github.io/closure-compiler-issues/#1198
  @Test
  public void testIssue1198() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.OFF);

    test(options,
         "function f(x) { return 1; do { x(); } while (true); }",
         "function f(a) { return 1; }");
  }

  // http://blickly.github.io/closure-compiler-issues/#772
  @Test
  public void testIssue772() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    test(
        options,
        "/** @const */ var a = {};"
        + "/** @const */ a.b = {};"
        + "/** @const */ a.b.c = {};"
        + "goog.scope(function() {"
        + "  var b = a.b;"
        + "  var c = b.c;"
        + "  /** @typedef {string} */"
        + "  c.MyType;"
        + "  /** @param {c.MyType} x The variable. */"
        + "  c.myFunc = function(x) {};"
        + "});",
        "/** @const */ var a = {};"
        + "/** @const */ a.b = {};"
        + "/** @const */ a.b.c = {};"
        + "a.b.c.MyType;"
        + "a.b.c.myFunc = function(x) {};");
  }

  @Test
  public void testCodingConvention() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    assertThat(compiler.getCodingConvention()).isInstanceOf(ClosureCodingConvention.class);
  }

  @Test
  public void testJQueryStringSplitLoops() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    test(options, "var x=['1','2','3','4','5','6','7']", "var x='1234567'.split('')");

    options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setComputeFunctionSideEffects(false);
    options.setRemoveUnusedVariables(Reach.ALL);

    // If we do splits too early, it would add a side-effect to x.
    test(options,
      "var x=['1','2','3','4','5','6','7']",
      "");

  }

  @Test
  public void testAlwaysRunSafetyCheck() {
    CompilerOptions options = createCompilerOptions();
    options.setDevMode(DevMode.OFF);
    options.setCheckSymbols(false);
    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
        new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            Node var = root.getLastChild().getFirstChild();
            assertThat(var.getToken()).isEqualTo(Token.VAR);
            var.detach();
          }
        });

    try {
      test(options,
           "var x = 3; function f() { return x + z; }",
           "function f() { return x + z; }");
      assertWithMessage("Expected run-time exception").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("Unexpected variable x");
    }
  }

  @Test
  public void testSuppressCastWarning() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    test(
        options,
        "function f() { var xyz = /** @type {string} */ (0); }",
        TypeValidator.INVALID_CAST);

    testNoWarnings(
        options,
        lines(
            "/** @suppress {invalidCasts} */",
            "function f() { var xyz = /** @type {string} */ (0); }"));

    testNoWarnings(
        options,
        lines(
            "/** @const */ var g = {};",
            "/** @suppress {invalidCasts} */",
            "g.a = g.b = function() {",
            "var xyz = /** @type {string} */ (0);",
            "}"));
  }

  @Test
  public void testLhsCast() {
    CompilerOptions options = createCompilerOptions();
    test(
        options,
        "/** @const */ var g = {};" +
        "/** @type {number} */ g.foo = 3;",
        "/** @const */ var g = {};" +
        "g.foo = 3;");
  }

  @Test
  public void testRenamePrefix() {
    String code = "var x = {}; function f(y) {}";
    CompilerOptions options = createCompilerOptions();
    options.setRenamePrefix("G_");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    test(options, code, "var G_={}; function G_a(a) {}");
  }

  @Test
  public void testRenamePrefixNamespace() {
    String code =
        "var x = {}; x.FOO = 5; x.bar = 3;";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x$FOO = 5; _.x$bar = 3;");
  }

  @Test
  public void testRenamePrefixNamespaceProtectSideEffects() {
    String code = "var x = null; try { +x.FOO; } catch (e) {}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.x = null; try { +_.x.FOO; } catch (a) {}");
  }

  // https://github.com/google/closure-compiler/issues/1875
  @Test
  public void testNoProtectSideEffectsInChecksOnly() {
    String code = "x;";

    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);
    options.setProtectHiddenSideEffects(true);
    testSame(options, code);
  }

  @Test
  public void testRenameCollision() {
    String code = "" +
          "/**\n" +
          " * @fileoverview\n" +
          " * @suppress {uselessCode}\n" +
          " */" +
          "var x = {};\ntry {\n(0,use)(x.FOO);\n} catch (e) {}";

    CompilerOptions options = createCompilerOptions();
    testSame(options, code);

    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    options.setRenamePrefixNamespace("a");
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(false);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);

    test(options, code, "var b = {}; try { (0,use)(b.FOO); } catch (c) {}");
  }

  @Test
  public void testRenamePrefixNamespaceActivatesMoveFunctionDeclarations() {
    CompilerOptions options = createCompilerOptions();
    String code = "var x = f; function f() { return 3; }";
    testSame(options, code);
    assertThat(options.moveFunctionDeclarations).isFalse();
    options.setRenamePrefixNamespace("_");
    test(options, code, "_.f = function() { return 3; }; _.x = _.f;");
  }

  @Test
  public void testIncompleteFunction1() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    DiagnosticType[] warnings =
        new DiagnosticType[] {RhinoErrorReporter.PARSE_ERROR, RhinoErrorReporter.PARSE_ERROR};
    test(options,
        new String[] { "var foo = {bar: function(e) }" },
        new String[] { "var foo = {bar: function(e){}};" },
        warnings
    );
  }

  @Test
  public void testIncompleteFunction2() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "function hi", "function hi() {}");
  }

  @Test
  public void testUnboundedArrayLiteralInfiniteLoop() {
    CompilerOptions options = createCompilerOptions();
    options.setIdeMode(true);
    testParseError(options, "var x = [1, 2", "var x = [1, 2]");
  }

  @Test
  public void testStrictWarningsGuard() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.addWarningsGuard(new StrictWarningsGuard());

    Compiler compiler = compile(options,
        "/** @return {number} */ function f() { return true; }");
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testCheckConstants1() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.QUIET;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var foo; foo();\n" +
        "/** @const */\n" +
        "var x = 1; foo(); x = 2;\n";
    test(options, code, code);
  }

  @Test
  public void testCheckConstants2() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "var foo;\n" +
        "/** @const */\n" +
        "var x = 1; foo(); x = 2;\n";
    test(options, code, ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }

  // http://blickly.github.io/closure-compiler-issues/#937
  @Test
  public void testIssue937() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = "" +
        "console.log(" +
            "/** @type {function():!string} */ ((new x())['abc'])());";
    String result = "" +
        "console.log((new x()).abc());";
    test(options, code, result);
  }

  @Test
  public void testES5toES6() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    CompilationLevel.SIMPLE_OPTIMIZATIONS
        .setOptionsForCompilationLevel(options);
    String code = "f = function(c) { for (var i = 0; i < c.length; i++) {} };";
    compile(options, code);
  }

  // http://blickly.github.io/closure-compiler-issues/#787
  @Test
  public void testIssue787() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code = LINE_JOINER.join(
        "function some_function() {",
        "  var fn1;",
        "  var fn2;",
        "",
        "  if (any_expression) {",
        "    fn2 = external_ref;",
        "    fn1 = function (content) {",
        "      return fn2();",
        "    }",
        "  }",
        "",
        "  return {",
        "    method1: function () {",
        "      if (fn1) fn1();",
        "      return true;",
        "    },",
        "    method2: function () {",
        "      return false;",
        "    }",
        "  }",
        "}");

    String result =
        LINE_JOINER.join(
            "function some_function() {",
            "  if (any_expression) {",
            "    var b = external_ref;",
            "    var a = function(c) {",
            "      return b()",
            "    };",
            "  }",
            "  return {",
            "    method1:function() {",
            "      a && a();",
            "      return !0",
            "    },",
            "    method2: function() {",
            "      return !1",
            "    }",
            "  }",
            "}");

    test(options, code, result);
  }

  @Test
  public void testRmUnusedProtoPropsInExternsUsage() {
    CompilerOptions options = new CompilerOptions();
    options.setRemoveUnusedPrototypePropertiesInExterns(true);
    options.setRemoveUnusedPrototypeProperties(false);
    try {
      test(options, "", "");
      assertWithMessage("Expected CompilerOptionsPreprocessor.InvalidOptionsException").fail();
    } catch (RuntimeException e) {
      if (!(e instanceof CompilerOptionsPreprocessor.InvalidOptionsException)) {
        assertWithMessage("Expected CompilerOptionsPreprocessor.InvalidOptionsException").fail();
      }
    }
  }

  @Test
  public void testMaxFunSizeAfterInliningUsage() {
    CompilerOptions options = new CompilerOptions();
    options.setInlineFunctions(Reach.NONE);
    options.setMaxFunctionSizeAfterInlining(1);
    try {
      test(options, "", "");
      assertWithMessage("Expected CompilerOptionsPreprocessor.InvalidOptionsException").fail();
    } catch (RuntimeException e) {
      if (!(e instanceof CompilerOptionsPreprocessor.InvalidOptionsException)) {
        assertWithMessage("Expected CompilerOptionsPreprocessor.InvalidOptionsException").fail();
      }
    }
  }

  // isEquivalentTo returns false for alpha-equivalent nodes
  @Test
  public void testIsEquivalentTo() {
    String[] input1 = {"function f(z) { return z; }"};
    String[] input2 = {"function f(y) { return y; }"};
    CompilerOptions options = new CompilerOptions();
    Node out1 = parse(input1, options);
    Node out2 = parse(input2, options);
    assertThat(out1.isEquivalentTo(out2)).isFalse();
  }

  @Test
  public void testEs6OutDoesntCrash() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(options, "function f(x) { if (x) var x=5; }", "function f(x) { if (x) x=5; }");
  }

  @Test
  public void testIjsWithGoogScopeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    test(
        options,
        new String[] {
          "/** @typeSummary */ /** @const */ var goog = {};",
          lines(
              "goog.provide('foo.baz');",
              "",
              "goog.scope(function() {",
              "",
              "var RESULT = 5;",
              "/** @return {number} */",
              "foo.baz = function() { return RESULT; }",
              "",
              "}); // goog.scope"),
        },
        new String[] {
          lines(
              "var $jscomp = $jscomp || {};",
              "$jscomp.scope = {};",
              "var foo = {};",
              "/** @const */ $jscomp.scope.RESULT = 5;",
              "/** @return {number} */ foo.baz = function() { return $jscomp.scope.RESULT; }"),
        });
  }

  @Test
  public void testGenerateIjsWithGoogModuleAndGoogScopeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setIncrementalChecks(CompilerOptions.IncrementalCheckMode.GENERATE_IJS);

    testNoWarnings(
        options,
        new String[] {
          "goog.module('m');",
          lines(
              "goog.scope(function() {", //
              "var RESULT = 5;",
              "",
              "}); // goog.scope")
        });
  }

  @Test
  public void testGenerateIjsWithES2018() {
    CompilerOptions options = createCompilerOptions();
    options.setIncrementalChecks(CompilerOptions.IncrementalCheckMode.GENERATE_IJS);
    testNoWarnings(options, "const r = /hello/s;");
  }

  @Test
  public void testIjsWithDestructuringTypeWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    // To enable type-checking
    options.setLanguage(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    testNoWarnings(
        options,
        LINE_JOINER.join(
          "/** @typeSummary */",
          "const ns = {}",
          "/** @enum {number} */ ns.ENUM = {A:1};",
          "const {ENUM} = ns;",
          "/** @type {ENUM} */ let x = ENUM.A;",
          "/** @type {ns.ENUM} */ let y = ENUM.A;",
          ""));
  }

  @Test
  public void testTypeSummaryWithTypedefAliasWorks() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setClosurePass(true);
    // To enable type-checking
    options.setLanguage(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        new String[] {
          LINE_JOINER.join(
              "/** @typeSummary */",
              "goog.module('a.b.Foo');",
              "goog.module.declareLegacyNamespace();",
              "",
              "class Foo {}",
              "",
              "/** @typedef {number} */",
              "Foo.num;",
              "",
              "exports = Foo;"),
          LINE_JOINER.join(
              "goog.module('x.y.z');",
              "",
              "const Foo = goog.require('a.b.Foo');",
              "",
              "/** @type {Foo.num} */",
              "var x = 'str';"),
        },
        (String[]) null,
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  // GitHub issue #2079: https://github.com/google/closure-compiler/issues/2079
  @Test
  public void testStrictEqualityComparisonAgainstUndefined() {
    CompilerOptions options = createCompilerOptions();
    options.setFoldConstants(true);
    options.setCheckTypes(true);
    options.setUseTypesForLocalOptimization(true);
    test(
        options,
        LINE_JOINER.join(
            "if (/** @type {Array|undefined} */ (window['c']) === null) {",
            "  window['d'] = 12;",
            "}"),
            "null===window['c']&&(window['d']=12)");
  }

  @Test
  public void testUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    test(options,
        "var str = '\\q';",
        "var str = 'q';");
  }

  @Test
  public void testWarnUnnecessaryBackslashInStringLiteral() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.UNNECESSARY_ESCAPE, CheckLevel.WARNING);
    test(options,
        "var str = '\\q';",
        "var str = 'q';",
        RhinoErrorReporter.UNNECESSARY_ESCAPE);
  }

  // NOTE(dimvar): the jsdocs are ignored in the comparison of the before/after ASTs. It'd be nice
  // to test the jsdocs as well, but AFAICT we can only do that in CompilerTestCase, not here.
  @Test
  public void testRestParametersWithGenerics() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    test(
        options,
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "/**",
            " * @template T",
            " * @param {...function(!Foo<T>)} x",
            " */",
            "function f(...x) {",
            "  return 123;",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "/**",
            " * @param {...function(!Foo<T>)} x",
            " * @template T",
            " */",
            "function f(x) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 0;",
            "       $jscomp$restIndex < arguments.length;",
            "       ++$jscomp$restIndex) {",
            "         $jscomp$restParams[$jscomp$restIndex - 0] =",
            "        arguments[$jscomp$restIndex];",
            "       }",
            "  {",
            "    var /** @type {!Array<function(!Foo<?>)>} */ x$0 = $jscomp$restParams;",
            "    return 123;",
            "  }",
            "}"));
  }

  @Test
  public void testTranspilingEs2016ToEs2015() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    // Only transpile away the ES2016 ** operator, not the ES2015 const declaration.
    test(options, "const n = 2 ** 5;", "const n = Math.pow(2, 5);");

    // Test skipNonTranspilationPasses separately because it has a different code path
    options.setSkipNonTranspilationPasses(true);
    test(options, "const n = 2 ** 5;", "const n = Math.pow(2, 5);");
    test(options, "class C {}", "class C {}");
  }

  @Test
  public void testMethodDestructuringInTranspiledAsyncFunction() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setOptimizeCalls(false);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    // Create a noninjecting compiler avoid comparing all the polyfill code.
    useNoninjectingCompiler = true;

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(SourceFile.fromCode("extraExterns", "var $jscomp = {}; Symbol.iterator;"));
    externs = externsList.build();

    test(
        options,
        LINE_JOINER.join(
            "class A {",
            "  static doSomething(i) { alert(i); }",
            "}",
            "async function foo() {",
            "  A.doSomething(await 3);",
            "}",
            "foo();"),
        LINE_JOINER.join(
            "var A = function() {};",
            "var A$doSomething = function(i) {",
            "  alert(i);",
            "};",
            "function foo() {",
            "  var JSCompiler_temp_const;",
            "  var JSCompiler_temp_const$jscomp$1;",
            "  return $jscomp.asyncExecutePromiseGeneratorProgram(",
            "      function ($jscomp$generator$context) {",
            "        if ($jscomp$generator$context.nextAddress == 1) {",
            "          JSCompiler_temp_const = A;",
            "          JSCompiler_temp_const$jscomp$1 = A$doSomething;",
            "          return $jscomp$generator$context.yield(3, 2);",
            "        }",
            "        JSCompiler_temp_const$jscomp$1.call(",
            "            JSCompiler_temp_const,",
            "            $jscomp$generator$context.yieldResult);",
            "        $jscomp$generator$context.jumpToEnd();",
            "      });",
            "}",
            "foo();"));
  }

  @Test
  public void testGithubIssue2874() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);

    test(
        options,
        lines(
            "var globalObj = {i0:0, i1: 0};",
            "function func(b) {",
            "  var g = globalObj;",
            "  var f = b;",
            "  g.i0 = f.i0|0;",
            "  g.i1 = f.i1|0;",
            "  g = b;",
            "  g.i0 = 0;",
            "  g.i1 = 0;",
            "}",
            "console.log(globalObj);",
            "func({i0:2, i1: 3});",
            "console.log(globalObj);"),
        lines(
            "var globalObj = {i0: 0, i1: 0};",
            "function func(b) {",
            "  var g = globalObj;",
            "  g.i0 = b.i0 | 0;",
            "  g.i1 = b.i1 | 0;",
            "  g = b;",
            "  g.i0 = 0;",
            "  g.i1 = 0;",
            "}",
            "console.log(globalObj);",
            "func({i0:2, i1: 3});",
            "console.log(globalObj);"));
  }

  @Test
  public void testDestructuringCannotConvert() {
    CompilerOptions options = createCompilerOptions();

    test(options, "for (var   [x] = [], {y} = {}, z = 2;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
    test(options, "for (let   [x] = [], {y} = {}, z = 2;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
    test(options, "for (const [x] = [], {y} = {}, z = 2;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  @Test
  public void testDefaultParameterRemoval() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "foo = { func: (params = {}) => { console.log(params); } }",
        "foo = { func: (params = {}) => { console.log(params); } }");

    // TODO(bradfordcsmith): Default params are removed if source code uses 2018 features because of
    // doesScriptHaveUnsupportedFeatures in processTranspile. This "sometimes-removal" of default
    // parameters in Es6RewriteDestructuring really needs to be avoided.
    test(
        options,
        "let {...foo} = { func: (params = {}) => { console.log(params); } }",
        lines(
            "var $jscomp$destructuring$var0 = {",
            "  func:(params)=>{",
            "    params=params===undefined?{}:params;",
            "    console.log(params);",
            "  }};",
            "var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);",
            "let foo=$jscomp$destructuring$var1"));
  }

  @Test
  public void testAsyncIter() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    useNoninjectingCompiler = true;

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "extraExterns", "var $jscomp = {}; Symbol.iterator; Symbol.asyncIterator;"));
    externs = externsList.build();

    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    testSame(options, "async function* foo() {}");
    testSame(options, "for await (a of b) {}");

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    test(
        options,
        "async function* foo() {}",
        "function foo() { return new $jscomp.AsyncGeneratorWrapper((function*(){})()); }");

    test(
        options,
        lines("async function abc() { for await (a of foo()) { bar(); } }"),
        lines(
            "async function abc() {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 =",
            "        await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testDestructuringRest() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);

    test(options, "const {y} = {}", "const {y} = {}");

    test(
        options,
        "const {...y} = {}",
        lines(
            "var $jscomp$destructuring$var0 = {};",
            "var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);",
            "const y = $jscomp$destructuring$var1"));

    test(
        options,
        "function foo({ a, b, ...c}) { try { foo() } catch({...m}) {} }",
        lines(
            "function foo($jscomp$destructuring$var0){",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  var a=$jscomp$destructuring$var1.a;",
            "  var b=$jscomp$destructuring$var1.b;",
            "  var c= (delete $jscomp$destructuring$var2.a,",
            "          delete $jscomp$destructuring$var2.b,",
            "          $jscomp$destructuring$var2);",
            "  try{ foo() }",
            "  catch ($jscomp$destructuring$var3) {",
            "    var $jscomp$destructuring$var4 = $jscomp$destructuring$var3;",
            "    var $jscomp$destructuring$var5 = Object.assign({}, $jscomp$destructuring$var4);",
            "    let m = $jscomp$destructuring$var5",
            "  }",
            "}"));
  }

  private void addMinimalExternsForRuntimeTypeCheck() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "other_externs.js",
            lines(
                "Array.prototype.join;",
                "Function.prototype.name;",
                "Object.prototype.toString;",
                "var RegExp;",
                "RegExp.prototype.exec;",
                "Window.prototype.frames",
                "Window.prototype.length",
                "")));
    externs = externsList.build();
  }

  @Test
  public void testRuntimeTypeCheckInjection() {
    CompilerOptions options = createCompilerOptions();
    addMinimalExternsForRuntimeTypeCheck();
    options.checkTypes = true;
    options.enableRuntimeTypeCheck("callMe");

    // Verify that no warning/errors or exceptions occure but otherwise ignore the output.
    test(
        options, new String[] { "function callMe(a) {}; /** @type {string} */ var x = y; " },
        (String []) null);
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    return options;
  }

  @Test
  public void testNoDevirtualization_whenSingleDefinitionInSource_ifNameCollidesWithExtern() {
    // See https://github.com/google/closure-compiler/issues/3040
    //
    // We run this as an integration test because it depends on the extern properties having been
    // collected.

    CompilerOptions options = createCompilerOptions();
    options.checkTypes = true;
    options.devirtualizeMethods = true;

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "other_externs.js",
            lines(
                "/** @constructor */",
                "var SomeExternType = function() {",
                "  /** @type {function()} */",
                "  this.restart;",
                "}")));
    externs = externsList.build();

    testSame(
        options,
        lines(
            "/** @constructor */",
            "var X = function() { }",
            "",
            "X.prototype.restart = function(n) {",
            "  console.log(n);",
            "}",
            "",
            "/** @param {SomeExternType} e */",
            "function f(e) {",
            // Notice how `restart` has not been rewritten even though there is only one
            // definition in the sources. A single definition is not a sufficient condition. An
            // extern property may exist with the same name but no definition.
            "  new X().restart(5);",
            "  e.restart();",
            "}"));
  }

  @Test
  public void testGetOriginalQualifiedNameAfterEs6RewriteClasses() {
    // A bug in Es6RewriteClasses meant we were putting the wrong `originalName` on some nodes.
    CompilerOptions options = createCompilerOptions();
    // force SourceInformationAnnotator to run
    options.setExternExports(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);

    test(
        options,
        lines(
            "goog.module('a');",
            "class Foo { static method() {} }",
            "class Bar { foo() { Foo.method(); } }"),
        lines(
            "var module$exports$a = {};",
            "/** @constructor @struct */",
            "var module$contents$a_Foo = function() {};",
            "module$contents$a_Foo.method = function() {};",
            "",
            "/** @constructor @struct */",
            "var module$contents$a_Bar = function () {}",
            "module$contents$a_Bar.prototype.foo = ",
            "    function() { module$contents$a_Foo.method(); }"));

    Node script = lastCompiler.getJsRoot().getFirstChild();

    Node exprResult = script.getLastChild();
    Node anonFunction = exprResult.getFirstChild().getLastChild();
    assertNode(anonFunction).hasToken(Token.FUNCTION);
    Node body = anonFunction.getLastChild();

    Node callNode = body.getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);

    Node modulecontentsFooMethod = callNode.getFirstChild();
    // Verify this is actually "Foo.method" - it used to be "Foo.foo".
    assertThat(modulecontentsFooMethod.getOriginalQualifiedName()).isEqualTo("Foo.method");
    assertThat(modulecontentsFooMethod.getSecondChild().getOriginalName()).isNull();
  }

  @Test
  public void testStructuralSubtypeCheck_doesNotInfinitlyRecurse_onRecursiveTemplatizedTypes() {
    // See: https://github.com/google/closure-compiler/issues/3067.

    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);

    testNoWarnings(
        options,
        lines(
            // We need two templated structural types that might match (i.e. `RecordA` and
            // `RecordB`).
            "/**",
            " * @record",
            " * @template PARAM_A",
            " */",
            "var RecordA = function() {};",
            "",
            "/**",
            " * @record",
            " * @template PARAM_B",
            " */",
            "var RecordB = function() {};",
            "",
            // Then we need to give them both a property that:
            //  - could match
            //  - templates on one of the two types (Notice they can be mixed-and-matched since they
            //    might be structurally equal)
            //  - is a nested template (i.e. `Array<X>`) (This is what would explode the recursion)
            //  - uses each type's template parameter (So there's a variable to recur on)
            "/** @type {!RecordA<!Array<PARAM_A>>} */",
            "RecordA.prototype.prop;",
            "",
            "/** @type {!RecordB<!Array<PARAM_B>>} */",
            "RecordB.prototype.prop;",
            "",
            // Finally, we need to create a union that:
            //  - generated a raw-type for one of the structural template types (i.e. `RecordA')
            //    (`RecordA<number>` and `RecordA<boolean>` were being smooshed into a raw-type)
            //  - attempts a structural match on that raw-type against the other record type
            // For some reason this also needs to be a property of a forward referenced type, which
            // is why we omit the declaration of `Union`.
            "/** @type {(!RecordA<number>|!RecordA<boolean>)|!RecordB<string>} */",
            "Union.anything;"));
  }

  @Test
  public void testOptionalCatchBinding_toEs5() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2019);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        options,
        new String[] {
          lines(
              "function foo() {}",
              "function reportError() {}",
              "try {",
              " foo();",
              "} catch {",
              "  reportError();",
              "}")
        },
        new String[] {
          lines(
              "function foo() {}",
              "function reportError() {}",
              "try {",
              " foo();",
              "} catch ($jscomp$unused$catch) {",
              "  reportError();",
              "}")
        });
  }

  @Test
  public void testOptionalCatchBinding_toEs2019() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
    // trying to make sure the catch binding transpilation pass does not run when it should not be
    testSame(
        options,
        new String[] {
          lines(
              "function foo() {}",
              "function reportError() {}",
              "try {",
              " foo();",
              "} catch {",
              "  reportError();",
              "}")
        });
  }

  @Test
  public void testOptionalCatchBinding_noTranspile() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2019);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    test(
        options,
        new String[] {
          lines(
              "function foo() {}",
              "function reportError() {}",
              "try {",
              " foo();",
              "} catch {",
              "  reportError();",
              "}")
        },
        new String[] {
          lines(
              "function foo() {}",
              "function reportError() {}",
              "try {",
              " foo();",
              "} catch {",
              "  reportError();",
              "}")
        });
  }

  @Test
  public void testTTL() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2019);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

    DiagnosticType[] errors =
        new DiagnosticType[] {RhinoErrorReporter.PARSE_ERROR, RhinoErrorReporter.TYPE_PARSE_ERROR};

    test(
        options,
        new String[] {
          lines(
              "/**",
              " * @template T := maprecord(record({a: 'number'}), (k, v) => record({[k]: 'string'})"
                  + " =:",
              " */",
              "function f() {}")
        },
        null,
        errors);
  }


  @Test
  public void testNoSpuriousWarningsOnGeneratedTypedef() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    testNoWarnings(options, "/** @typedef {number} */ export let Foo;");
  }

  @Test
  public void testBrowserFeaturesetYear2020() {
    CompilerOptions options = createCompilerOptions();
    options.setBrowserFeaturesetYear(2020);

    String googDefine =
        lines(
            "const goog = {};", //
            "/** @define {number} */",
            "goog.FEATURESET_YEAR = 2012;\n");
    String googDefineOutput = "const goog={};goog.FEATURESET_YEAR=2020;";

    // async generators and for await ... of are emitted untranspiled.
    test(
        options,
        lines(
            googDefine,
            "async function* foo() {yield 1; await 0; yield 2;}",
            "async function bar() {",
            "  for await (const val of foo()) {",
            "    console.log(val);",
            "  }",
            "}",
            "bar();"),
        googDefineOutput
            + "async function*foo(){yield 1;await 0;yield 2}async function bar(){for await(const"
            + " val of foo())console.log(val)}bar()");

    // So is object rest and spread.
    test(
        options,
        lines(
            googDefine,
            "const {foo, ...bar} = {foo: 10, bar: 20, ...{baz: 30}};",
            "console.log(foo);",
            "console.log(bar);"),
        googDefineOutput
            + "const {foo,...bar}={foo:10,bar:20,...{baz:30}};console.log(foo);console.log(bar)");

    // But we won't emit ES 2018 regexp features.
    DiagnosticType untranspilable =
        MarkUntranspilableFeaturesAsRemoved.UNTRANSPILABLE_FEATURE_PRESENT;
    test(options, lines(googDefine, "/foo/s"), untranspilable);
    test(options, lines(googDefine, "/(?<foo>.)/"), untranspilable);
    test(options, lines(googDefine, "/(?<=foo)/"), untranspilable);
    test(options, lines(googDefine, "/(?<!foo)/"), untranspilable);
    test(options, lines(googDefine, "/\\p{Number}/u"), untranspilable);
  }

  @Test
  public void testAccessControlsChecks_esClosureInterop_destructuringRequireModule() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.WARNING);
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    test(
        options,
        new String[] {
          new TestExternsBuilder().addClosureExterns().build(),
          lines(
              "goog.module('my.Foo');", //
              "/** @private */",
              "exports.fn = function() {}"),
          lines(
              "const {fn} = goog.require('my.Foo');", //
              "fn();",
              "export {};")
        },
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testAccessControlsChecks_esClosureInterop_requireModule() {
    CompilerOptions options = createCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.WARNING);
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    test(
        options,
        new String[] {
          new TestExternsBuilder().addClosureExterns().build(),
          lines(
              "goog.module('my.Foo');", //
              "class Foo {",
              "  /** @private */",
              "  static build() {}",
              "}",
              "exports = Foo;"),
          lines(
              "const Foo = goog.require('my.Foo');", //
              "Foo.build();",
              "export {};")
        },
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testIsolatePolyfills_transpileOnlyMode() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.STABLE_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setRewritePolyfills(true);
    options.setIsolatePolyfills(true);
    options.setSkipNonTranspilationPasses(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode("testExterns.js", new TestExternsBuilder().addAlert().build()));

    // Polyfill isolation currently doesn't work with transpileOnly mode. It depends on runtime
    // library injection not being disabled.
    assertThrows(Exception.class, () -> compile(options, "alert('hi'.startsWith('h'));"));
  }

  @Test
  public void testArrayOfType() {
    CompilerOptions options = createCompilerOptions();
    options.addWarningsGuard(
        new DiagnosticGroupWarningsGuard(DiagnosticGroups.STRICT_CHECK_TYPES, CheckLevel.WARNING));
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "es6.js",
            lines(
                "/**",
                " * @param {...T} var_args",
                " * @return {!Array<T>}",
                " * @template T",
                " */",
                "Array.of = function(var_args) {};")));
    externs = externsList.build();

    test(
        options,
        lines("const array = Array.of('1', '2', '3');", "if (array[0] - 1) {}"),
        TypeValidator.INVALID_OPERAND_TYPE);
  }

  @Test
  public void testNewTarget() {
    // Repro case for Github issue 3607.  Don't crash with a reference to new.target
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    test(
        options,
        lines(
            "",
            "window.Class = class {",
            "  constructor() {",
            "    let newTarget = new.target;",
            "    return Object.create(newTarget.prototype);",
            "  }",
            "};"),
        lines(
            "",
            "window.a = class {",
            "  constructor() {",
            "    let b = new.target;",
            "    return Object.create(b.prototype);",
            "  }",
            "};"));
  }
}
