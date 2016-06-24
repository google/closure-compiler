/*
 * Copyright 2011 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Various tests for {@code replaceScript} functionality of Closure Compiler.
 *
 * @author bashir@google.com (Bashir Sadjad)
 */

public final class SimpleReplaceScriptTest extends BaseReplaceScriptTestCase {
  public void testInfer() {
    CompilerOptions options = getOptions(DiagnosticGroups.ACCESS_CONTROLS);
    String source = ""
        + "var obj = {};\n"
        + "/** @param {number} n */\n"
        + "obj.temp = function(n) {this.num = n;};\n"
        + "obj.temp(10);\n";
    Result result = runReplaceScript(options,
        ImmutableList.of(source), 0, 0, source, 0, false).getResult();
    assertTrue(result.success);
  }

  public void testInferWithModules() {
    CompilerOptions options = getOptions();
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("in", ""));

    Result result = compiler.compile(EXTERNS, inputs, options);
    assertTrue(result.success);

    CompilerInput oldInput = compiler.getInput(new InputId("in"));
    JSModule myModule = oldInput.getModule();
    assertThat(myModule.getInputs()).hasSize(1);

    SourceFile newSource = SourceFile.fromCode("in", "var x;");
    JsAst ast = new JsAst(newSource);
    compiler.replaceScript(ast);

    assertThat(myModule.getInputs()).hasSize(1);
    assertThat(myModule.getInputs()).doesNotContain(oldInput);
    assertThat(myModule.getInputs()).containsExactly(compiler.getInput(new InputId("in")));
  }

  public void testreplaceScript() {
    CompilerOptions options = getOptions(DiagnosticGroups.ACCESS_CONTROLS);
    Compiler compiler = new Compiler();
    String source = ""
        + "/** @param {number} n */\n"
        + "temp = function(n) {retrun (n + 1);};\n"
        + "temp(10);\n";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("in", source));
    Result result = compiler.compile(EXTERNS, inputs, options);
    assertTrue(result.success);

    // Now try to re-infer with a modified version of source
    // with a new variable.
    String source2 = ""
        + "var a = 20;\n"
        + "/** @param {number} n */\n"
        + "temp = function(n) {retrun (n + 1);};\n"
        + "temp(a);\n";
    SourceFile newSource = SourceFile.fromCode("in", source2);
    JsAst ast = new JsAst(newSource);
    compiler.replaceScript(ast);
  }

  public void testWithProvidesAndClosureOn() {
    runReplaceScriptWithProvides(true);
  }

  public void testWithProvidesAndClosureOff() {
    runReplaceScriptWithProvides(false);
  }

  private void runReplaceScriptWithProvides(boolean closureOn) {
    CompilerOptions options = getOptions(DiagnosticGroups.ACCESS_CONTROLS);
    options.setClosurePass(closureOn);

    String source =
        "goog.provide('Bar');"
        + "/** @constructor */ Bar = function() {};";
    // A modified version of source
    String newSource = "goog.provide('Bar');\ngoog.provide('Baz');"
        + "/** @constructor */ Bar = function() {};\n"
        + "/** @constructor */ Baz = function() {};";
    Result result = this.runReplaceScript(options, ImmutableList.of(
        CLOSURE_BASE, source), 0, 0, newSource, 1, false).getResult();
    assertNoWarningsOrErrors(result);
  }

  /** Test related to DefaultPassConfig.checkTypes */
  public void testUndefinedVars() {
    // Setting options for checking variables.
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_VARIABLES);
    // We need to set checkSymbols otherwise Compiler.initOptions will turn
    // CHECK_VARIABLES warnings off.
    options.setCheckSymbols(true);

    String firstSource = "var aVar = 10;";
    // Note only bVar is undefined because aVar is defined in firstSource.
    String secondSource = "var n = aVar;\n"
        + "var b = bVar + 1;";
    // Run replaceScript on second with new undefined-var errors.
    // Note there should be no error on aVar but two on bVar and cVar.
    String modifiedSource = "var n = aVar;\n"
        + "var b = bVar + 1;\n"
        + "var c = cVar + 1;";
    Result result = this.runReplaceScript(options, ImmutableList.of(
        firstSource, secondSource), 1, 0, modifiedSource, 1, true).getResult();
    assertFalse(result.success);
    assertThat(result.errors).hasLength(2);
    int i = 2;
    for (JSError e : result.errors) {
      assertErrorType(e, VarCheck.UNDEFINED_VAR_ERROR, i++);
    }
  }

  /** Test related to DefaultPassConfig.checkVariableReferences */
  public Compiler runRedefinedVarsTest(List<String> sources, int numOrigError,
      String newSrc, int newSrcInd, List<Integer> errorLineNumbers) {
    CompilerOptions options = getOptions();
    options.setCheckSymbols(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.ERROR);

    return this.runReplaceScript(options, sources, numOrigError, 0, newSrc, newSrcInd, true);
  }

  /** Test related to DefaultPassConfig.checkVariableReferences */
  public void testRedefinedVars() {
    String src = "var a = 10;\n var a = 20;";
    runRedefinedVarsTest(ImmutableList.of(src), 1, src, 0,
        ImmutableList.of(2));
  }

  public void testReferToExternVar() {
    String src = "var foo = extVar;";
    List<Integer> errorLines = new ArrayList<>();
    runRedefinedVarsTest(ImmutableList.of(src), 0, src, 0, errorLines);
  }

  /** Test for DefaultPassConfig.checkVariableReferences with two files */
  public void testRedefinedVarsTwoFiles() {
    String src0 = "var a = 10; \n var b = 11;";
    String src1 = "var a = 20;";
    runRedefinedVarsTest(ImmutableList.of(src0, src1), 1, src1, 1,
        ImmutableList.of(1));
    String modifiedSrc1 = "var c = 22; \n var b = 21;";
    runRedefinedVarsTest(ImmutableList.of(src0, src1), 1, modifiedSrc1, 1,
        ImmutableList.of(2));
  }

  /**
   * Test for DefaultPassConfig.checkVariableReferences with multiple files
   * where changes in one file causes errors in a down-stream file.
   */
  public void testRedefinedVarsMultipleFiles() {
    String src0 = "var a = 10;\n var b = 11;";
    String src1 = "var a = 20;\n var d = 23;";
    String src2 = "var c = 32;\n var d = 23;";
    String modifiedSrc1 = "var c = 20;\n var b = 20;";
    // Note replaceScript reports errors on files other than modified (expected)
    Compiler compiler = runRedefinedVarsTest(ImmutableList.of(src0, src1,
        src2), 2, modifiedSrc1, 1, ImmutableList.of(2, 1));
    // Now for src2, no errors should be reported on d but one on c.
    flushResults(compiler);
    doReplaceScript(compiler, src2, 2);
    Result result = compiler.getResult();
    assertThat(result.errors).hasLength(3);
    assertErrorType(result.errors[1], VarCheck.VAR_MULTIPLY_DECLARED_ERROR, 1);
  }

  /**
   * Test for DefaultPassConfig.checkVariableReferences with multiple files
   * and with multiple add/remove for same variable in different files.
   */
  public void testRedefinedVarsMultipleChangesForOneVar() {
    String src0 = "var a = 10;\n var b = 11;";
    String src1 = "var b = 20;\n";
    String src2 = "var b = 20;\n var c = 22;";
    // Note replaceScript reports errors on files other than modified (expected)
    Compiler compiler = runRedefinedVarsTest(ImmutableList.of(src0, src1,
        src2), 2, src0, 0, ImmutableList.of(1, 1));
    String modifiedSrc0 = "var a = 10;\n";
    flushResults(compiler);
    doReplaceScript(compiler, modifiedSrc0, 0);
    // Now for src2, one error should be reported on b.
    flushResults(compiler);
    doReplaceScript(compiler, src2, 2);
    Result result = compiler.getResult();
    assertThat(result.errors).hasLength(2);
    assertErrorType(result.errors[0], VarCheck.VAR_MULTIPLY_DECLARED_ERROR, 1);
  }


  /**
   * Test related to DefaultPassConfig.checkVariableReferences where no error
   * is expected (note same variable names in two scopes).
   */
  public void testRedefinedVarsFunction() {
    String src0 = "var a = 10;\n var b = 10;";
    String src1 = "var a = 20;";
    String modifiedSrc1 = "function test() { var a = 20; }\n var b = 20;";
    // Note some of the errors in replaceScript would be on src2.
    runRedefinedVarsTest(ImmutableList.of(src0, src1), 1,
        modifiedSrc1, 1, ImmutableList.of(2));
  }

  /**
   * Undefined vars are added to {@code VarCheck.SYNTHETIC_VARS_DECLAR} and
   * previously this input was not properly added to list of externs which was
   * causing an NPE in hot-swap mode of {@code ReferenceCollectingCallback}.
   */
  public void testAccessToUndefinedVar() {
    String src = "/** \n @fileoverview \n @suppress {checkVars} */ var a = undefVar;\n";
    List<Integer> errorLines = new ArrayList<>();
    runRedefinedVarsTest(ImmutableList.of(src), 0, src, 0, errorLines);
  }

  /**
   * Test that two previously common problems don't happen in inc-compile:
   * (i) require is not defined on goog
   * (ii) "undefined has no properties" on the instantiation of ns.Bar().
   * See the usage in test functions below.
   */
  private void checkProvideRequireErrors(CompilerOptions options) {
    String source0 =
        "goog.provide('ns.Bar');\n"
        + "/** @constructor */ ns.Bar = function() {};";
    String source1 = "goog.require('ns.Bar');\n"
        + "var a = new ns.Bar();";
    Result result = runReplaceScript(options, ImmutableList.of(source0,
        source1), 0, 0, source1, 1, false).getResult();
    assertNoWarningsOrErrors(result);
  }

  public void testProvideRequireErrors() {
    CompilerOptions options = getOptions(DiagnosticGroups.MISSING_PROPERTIES);
    checkProvideRequireErrors(options);
  }

  public void testClassInstantiation() {
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_TYPES);
    checkProvideRequireErrors(options);
  }

  public void testCheckRequires() {
    CompilerOptions options = getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.ERROR);
    // Note it needs declaration of ns to throw the error because closurePass
    // which replaces goog.provide happens afterwards (see checkRequires pass).
    String source0 = "var ns = {};\n goog.provide('ns.Bar');\n"
        + "/** @constructor */ ns.Bar = function() {};";
    String source1 = "var a = new ns.Bar();";
    Result result = runReplaceScript(options, ImmutableList.of(source0,
        source1), 1, 0, source1, 1, true).getResult();
    // TODO(joeltine): Change back to asserting an error when b/28869281
    // is fixed.
    assertTrue(result.success);
  }

  public void testCheckRequiresWithNewVar() {
    CompilerOptions options = getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.ERROR);
    String src = "";
    String modifiedSrc = src + "\n(function() { var a = new ns.Bar(); })();";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 0, 0, modifiedSrc, 0, false).getResult();
    // TODO(joeltine): Change back to asserting an error when b/28869281
    // is fixed.
    assertTrue(result.success);
  }

  public void testCheckProvides() {
    CompilerOptions options = getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.ERROR);
    checkProvideRequireErrors(options);
    String source0 = "goog.provide('ns.Foo'); /** @constructor */ ns.Foo = function() {};"
        + "/** @constructor */ ns.Bar = function() {};";
    Result result = runReplaceScript(options,
        ImmutableList.of(source0), 1, 0, source0, 0, true).getResult();
    assertFalse(result.success);

    assertThat(result.errors).hasLength(1);
    assertErrorType(result.errors[0], CheckProvides.MISSING_PROVIDE_WARNING, 1);
  }


  /** Test related to DefaultPassConfig.inferTypes */
  public void testNewTypeAdded() {
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_TYPES);
    String src = "/** @constructor */\n"
        + "Bar = function() {};\n"
        // TODO(bashir) Why the error goes away by adding /**@type {Bar}*/ here?
        + "var a = new Bar();\n";
    String modifiedSrc = src
        + "var b = a * 20;";
    Result result = this.runReplaceScript(options,
        ImmutableList.of(src), 0, 0, modifiedSrc, 0, false).getResult();
    assertFalse(result.success);

    assertThat(result.errors).hasLength(1);
    assertErrorType(result.errors[0], TypeValidator.TYPE_MISMATCH_WARNING, 4);

    assertThat(result.warnings).isEmpty();
  }

  public void testDeclarationMoved() {
    CompilerOptions options = getOptions();

    String srcPrefix = LINE_JOINER.join(
        "goog.provide('Bar');",
        "/** @constructor */",
        "Bar = function() {};");
    String declaration = "Bar.foo = function() {};";
    String originalSrc = srcPrefix + declaration;
    String modifiedSrc = srcPrefix + "\n\n\n\n" + declaration;

    Compiler compiler =
        runReplaceScript(options, ImmutableList.of(CLOSURE_BASE, originalSrc),
            0, 0, modifiedSrc, 1, false);

    assertNoWarningsOrErrors(compiler.getResult());
    verifyPropertyLineno(compiler, "Bar", "foo", 7);
  }

  public void testTypeDefRedeclaration() {
    // Tests that replacing/redeclaring a @typedef can be replaced via replaceScript.
    CompilerOptions options = getOptions();

    String originalSrc =
        "/** @typedef {number} */ var Foo;";
    Compiler compiler =
        runReplaceScript(options, ImmutableList.of(CLOSURE_BASE, originalSrc),
            0, 0, originalSrc, 1, false);

    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testConstructorDeclarationRedefined() {
    // Tests that redefining a @constructor does not fail when using replaceScript. Regression
    // test for b/28939919.
    CompilerOptions options = getOptions();

    String originalSrc = LINE_JOINER.join(
        "goog.provide('Bar');",
        "/** @constructor */",
        "Bar = function() {};");
    String modifiedSrc = LINE_JOINER.join(
        "goog.provide('Bar');",
        "/**",
        " * @constructor",
        " * @param {string} s",
        " */",
        "Bar = function(s) {};");

    Compiler compiler =
        runReplaceScript(options, ImmutableList.of(CLOSURE_BASE, originalSrc),
            0, 0, modifiedSrc, 1, false);

    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testDeclarationInAnotherFile() {
    CompilerOptions options = getOptions();

    String src = LINE_JOINER.join(
        "goog.provide('ns.Bar');",
        "/** @constructor */",
        "ns.Bar = function() {};");
    String otherSrc = LINE_JOINER.join(
        "goog.require('ns.Bar');",
        "ns.Bar.foo = function() {};");

    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(CLOSURE_BASE, src, otherSrc), 0, 0, src, 1, false);
    assertNoWarningsOrErrors(compiler.getResult());

    // Considering the "ns.Bar" type is deleted in the compilation above, the property "foo" is only
    // updated after the file defining it is replaced.
    doReplaceScript(compiler, otherSrc, 2);
    assertNoWarningsOrErrors(compiler.getResult());

    verifyPropertyLineno(compiler, "ns.Bar", "foo", 2);
  }

  public void testRedeclarationOfStructProperties() {
    // Tests that definition of a property on a @struct does not fail on replaceScript. A regression
    // test for b/28940462.
    CompilerOptions options = getOptions();

    String src = LINE_JOINER.join(
        "goog.provide('ns.Bar');",
        "/**",
        " * @constructor",
        " * @struct",
        " */",
        "ns.Bar = function() {};",
        "/** @private */",
        "ns.Bar.r_ = {};");

    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(CLOSURE_BASE, src), 0, 0, src, 1, false);
    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testInterfaceOverrideDeclarations() {
    // Tests that incremental compilation of a class implementing an interface does not fail
    // on replaceScript. Regression test for b/28942209.
    CompilerOptions options = getOptions();

    String src = LINE_JOINER.join(
        "goog.provide('ns.IBar');",
        "/** @interface */",
        "ns.IBar = function() {};",
        "/** @return {boolean} */",
        "ns.IBar.prototype.x = function() {};",
        "/**",
        " * @private",
        " * @constructor",
        " * @implements {ns.IBar} */",
        "ns.Bar_ = function() {};",
        "/** @override */",
        "ns.Bar_.prototype.x = function() {return true};");

    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(CLOSURE_BASE, src), 0, 0, src, 1, false);
    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testAssignmentToConstProperty() {
    // Tests that defining a field on a @const property does not fail with incorrect
    // "assignment to property" error. Regression test for b/28981397.
    CompilerOptions options = getOptions();

    String src = LINE_JOINER.join(
        "goog.provide('ns.A');",
        "/** @constructor */",
        "ns.A = function() {",
        "  /**",
        "  * @const @private",
        "  */",
        "  this.b = {};",
        "  this.b.ANY = 'FOO';",
        "};");

    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(CLOSURE_BASE, src), 0, 0, src, 1, false);
    assertNoWarningsOrErrors(compiler.getResult());
  }


  public void testDeclarationOverride() {
    CompilerOptions options = getOptions();

    String src1 =
        "goog.provide('ns.Bar');\n"
        + "/** @constructor */\n"
        + "ns.Bar = function() {};\n"
        + "ns.Bar.temp = function() {};\n"
        + "ns.Bar.func = function() {};\n";

    String src2 =
        "goog.provide('ns.Foo');\n" +
        "goog.require('ns.Bar');\n" +
        "/**\n" +
        " * @extends {ns.Bar}\n" +
        " * @constructor\n" +
        " */\n" +
        "ns.Foo = function() {};\n" +
        "goog.inherits(ns.Foo, ns.Bar);\n" +
        "/** @override */\n" +
        "ns.Foo.func = function() {" +
        "  ns.Bar.temp(); " +
        "};\n";

    String newSrc2 = "\n\n\n" + src2;

    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(CLOSURE_BASE, src1, src2), 0, 0, newSrc2, 2, false);

    assertNoWarningsOrErrors(compiler.getResult());
    verifyPropertyLineno(compiler, "ns.Foo", "func", 13);
    doReplaceScript(compiler, src1, 1);
    verifyPropertyLineno(compiler, "ns.Foo", "func", 13);
  }

  public void testDeclarationWithThisMoved() {
    CompilerOptions options = getOptions();

    String src1 =
        "goog.provide('ns.Bar');\n"
        + "/** @constructor */\n"
        + "ns.Bar = function() {\n"
        + "  this.temp = 10;\n"
        + "};\n";
    String src2 =
      "goog.require('ns.Bar');\n" +
      "/** @type {!ns.Bar} */\n" +
      "var test = new ns.Bar();\n";
    String modifiedSrc1 = "\n\n\n\n" + src1;

    Compiler compiler =
        runReplaceScript(options, ImmutableList.of(CLOSURE_BASE, src1, src2),
            0, 0, modifiedSrc1, 1, false);
    assertNoWarningsOrErrors(compiler.getResult());

    // The new property lineno is only picked up after recompiling the file where "test" is defined.
    doReplaceScript(compiler, src2, 2);
    assertNoWarningsOrErrors(compiler.getResult());

    verifyPropertyLineno(compiler, "test", "temp", 8);
  }

  public void testDeclarationOtherTypeWithField() {
    CompilerOptions options = getOptions();

    String srcPrefix =
        "goog.provide('Bar');\n"
        + "/** @constructor */\n"
        + "Bar = function() {};\n";
    String declaration = "Bar.foo = function() {};\n";
    String originalSrc = srcPrefix + declaration;
    String modifiedSrc = srcPrefix + "\n\n\n\n" + declaration;
    String otherSrc =
        "goog.provide('Baz');\n" +
        "/** @constructor */\n" +
        "Baz = function() {};\n" +
        "Baz.foo = function() {};\n";

    Compiler compiler =
        runReplaceScript(options,
            ImmutableList.of(CLOSURE_BASE, originalSrc, otherSrc),
            0, 0, modifiedSrc, 1, false);

    assertNoWarningsOrErrors(compiler.getResult());
    verifyPropertyLineno(compiler, "Bar", "foo", 8);
    verifyPropertyLineno(compiler, "Baz", "foo", 4);
  }

  public void testDeclarationInGoogScopeMoved() {
    CompilerOptions options = getOptions();

    String src1 =
        "/** @constructor */\n"
        + "test.Bar = function() { this.privNum = 10; };\n";
    String src2 =
      "goog.scope(function() {\n" +
      "  var Bar = test.Bar;\n" +
      "  Bar.temp = 10;" +
      "});";
    String modifiedSrc2 = "\n\n\n\n" + src2;

    Compiler compiler =
        runReplaceScript(options, ImmutableList.of(CLOSURE_BASE, src1, src2),
            0, 0, modifiedSrc2, 2, false);

    assertNoWarningsOrErrors(compiler.getResult());
    verifyPropertyLineno(compiler, "test.Bar", "temp", 7);
  }

  private void verifyPropertyLineno(Compiler compiler, String varName,
      String propName, int expectedLineno) {
    TypedVar var = compiler.getTopScope().getVar(varName);
    ObjectType objType = var.getType().toObjectType();
    Node propNode = objType.getPropertyNode(propName);
    assertThat(propNode).isNotNull();
    assertNode(propNode).hasLineno(expectedLineno);
  }

  public void testGlobalVarDeclarationMoved() {
    CompilerOptions options = getOptions();
    String prefix = "var a = 3;\n";
    String declaration = "var b = 4;\n";
    String src = prefix + declaration;
    String newSrc = prefix + "\n\n\n\n" + declaration;

    Compiler compiler = runReplaceScript(
        options, ImmutableList.of(CLOSURE_BASE, src), 0, 0, newSrc, 1, false);

    assertNoWarningsOrErrors(compiler.getResult());
    TypedVar var = compiler.getTopScope().getVar("b");
    assertNode(var.getNode()).hasLineno(6);
  }

  public void testNamespaceTypeInference() {
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_TYPES);
    String decl = "goog.provide('ns.Bar');\n"
        + "/** @constructor */ ns.Bar = function() {};";
    String ref = "goog.require('ns.Bar');\n"
        + "var x = new ns.Bar();";
    Result result = runReplaceScript(options, ImmutableList.of(
        CLOSURE_BASE, decl, ref), 0, 0, ref, 2, false).getResult();
    assertNoWarningsOrErrors(result);
  }

  public void testSourceNodeOfFunctionTypesUpdated() {
    String provideSrc = "goog.provide('ns.Foo');\n";
    String mainSrc = "/** @constructor */\n" +
    "ns.Foo = function() {\n" +
    "};\n" +
    "ns.Foo.prototype.fn = function(val){\n" +
    "  return 'abc';\n" +
    "};\n";

    String newSource = provideSrc + "\n\n\n" + mainSrc;
    String src = provideSrc + mainSrc;
    Compiler compiler = runReplaceScript(getOptions(),
        ImmutableList.of(CLOSURE_BASE, src), 0, 0, newSource, 1, false);
    Result result = compiler.getResult();
    assertNoWarningsOrErrors(result);

    JSType type = compiler.getTypeIRegistry().getType("ns.Foo");
    FunctionType fnType = type.toObjectType().getConstructor();
    Node srcNode = fnType.getSource();
    assertNode(srcNode).hasLineno(6);
  }

  public void testAssociatedNodeOfJsDocNotLeaked() {
    String src = "goog.provide('ns.Foo');\n" +
    "/** @constructor */\n" +
    "ns.Foo = function() {\n" +
    "};\n" +
    "/**\n" +
    " * @param {number} val \n" +
    " * @return {string} \n" +
    " */\n" +
    "ns.Foo.prototype.fn = function(val){\n" +
    "  return 'abc';\n" +
    "};\n";

    Compiler compiler = runFullCompile(
        getOptions(), ImmutableList.of(CLOSURE_BASE, src), 0, 0, false);
    assertNoWarningsOrErrors(compiler.getResult());


    doReplaceScript(compiler, src, 1);
    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testFunctionAssignedToAnotherFunction() {
    String src2 = "goog.provide('ns.Bar');\n" +
    "/** @return {null} */\n" +
    "ns.fn = function() {};\n";

    String src = "goog.provide('ns.Foo');\n" +
    "goog.require('ns.Bar');\n" +
    "/** @constructor */\n" +
    "ns.Foo = function() {\n" +
    "  this.fn();" +
    "};\n" +
    "/**\n" +
    " * Performs feature-specific initialization.\n" +
    " * @protected\n" +
    " */\n" +
    "ns.Foo.prototype.fn = ns.fn;\n";

    CompilerOptions options = getOptions();
    options.setCheckTypes(true);
    Compiler compiler =
        runFullCompile(options, ImmutableList.of(CLOSURE_BASE, src2, src), 0, 0, false);
    assertNoWarningsOrErrors(compiler.getResult());

    doReplaceScript(compiler, src, 2);
    assertNoWarningsOrErrors(compiler.getResult());
  }

  public void testPrototypeSlotChangedOnCompile() {
    String src = "goog.provide('ns.Foo');\n" +
      "/** @constructor */\n" +
      "ns.Foo = function() {\n" +
      "};\n" +
      "ns.Foo.prototype.fn = function(val){\n" +
      "  return 'abc';\n" +
      "};\n";


    Compiler compiler = runFullCompile(
        getOptions(), ImmutableList.of(CLOSURE_BASE, src), 0, 0, false);
    JSType type = compiler.getTypeIRegistry().getType("ns.Foo");
    FunctionType fnType = type.toObjectType().getConstructor();
    StaticTypedSlot<JSType> originalSlot = fnType.getSlot("prototype");

    doReplaceScript(compiler, src, 1);

    assertNoWarningsOrErrors(compiler.getResult());

    type = compiler.getTypeIRegistry().getType("ns.Foo");
    fnType = type.toObjectType().getConstructor();
    StaticTypedSlot<JSType> newSlot = fnType.getSlot("prototype");
    assertNotSame(originalSlot, newSlot);
  }

  /**
   * This test will fail if global scope generation happens before closure-pass.
   */
  public void testGlobalScopeGenerationWithProvide() {
    CompilerOptions options = getOptions();
    options.setCheckSymbols(true);
    String src =
        "goog.provide('namespace.Bar');\n"
        + "/** @constructor */ namespace.Bar = function() {};";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 0, 0, src, 0, false).getResult();
    assertNoWarningsOrErrors(result);
  }

  public void testAccessControls() {
    CompilerOptions options = getOptions(DiagnosticGroups.ACCESS_CONTROLS);
    options.setCheckTypes(true);
    String src0 =
        "/** @constructor */\n"
        + "test.Bar = function() { this.privNum = 10; };\n"
        + "/** @private */\n"
        + "test.Bar.prototype.privNum;\n"
        + "/** @protected */\n"
        + "test.Bar.prototype.protNum;\n";
    String src1 = "var a = new test.Bar();\n"
        + "var b = a.privNum;\n"
        + "a.privNum = 20;\n"
        + "var c = a.protNum;\n";
    Result result = this.runReplaceScript(options,
        ImmutableList.of(src0, src1), 3, 0, src1, 1, true).getResult();
    assertNumWarningsAndErrors(result, 3, 0);
    assertErrorType(result.errors[0],
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS, 2);
    assertErrorType(result.errors[1],
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS, 3);
    assertErrorType(result.errors[2],
        CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS, 4);
  }

  public void testGlobalThisCheck() {
    CompilerOptions options = getOptions(DiagnosticGroups.GLOBAL_THIS);
    String src = "/** @constructor */ namespace.Bar = function() {};\n"
        + "namespace.Bar.someFunc = function() { this.newField = 20; }";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 1, 0, src, 0, true).getResult();
    assertNumWarningsAndErrors(result, 1, 0);
    assertErrorType(result.errors[0], CheckGlobalThis.GLOBAL_THIS, 2);
  }

  public void testNoSideEffect() {
    CompilerOptions options = getOptions();
    options.setCheckSuspiciousCode(true);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);
    String src = "var s = 'test'\n"
        + "'this';\n";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 0, 1, src, 0, true).getResult();
    assertNumWarningsAndErrors(result, 0, 1);
    assertErrorType(result.warnings[0], CheckSideEffects.USELESS_CODE_ERROR, 2);
  }

  public void testAccidentalSemicolon() {
    CompilerOptions options = getOptions();
    options.setCheckSuspiciousCode(true);
    String src = "if (true) ; \n  var s = 'test';\n";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 0, 1, src, 0, true).getResult();
    assertNumWarningsAndErrors(result, 0, 1);
    assertErrorType(result.warnings[0],
        CheckSuspiciousCode.SUSPICIOUS_SEMICOLON, 1);
  }

  public void testUnreachableCode() {
    CompilerOptions options = getOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE, CheckLevel.ERROR);
    String src = "if (false) { \n  var s = 'test';\n }";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 1, 0, src, 0, true).getResult();
    assertNumWarningsAndErrors(result, 1, 0);
    assertErrorType(result.errors[0], CheckUnreachableCode.UNREACHABLE_CODE, 1);
  }

  public void testMissingReturn() {
    CompilerOptions options = getOptions();
    options.setCheckTypes(true);
    options.setWarningLevel(DiagnosticGroups.MISSING_RETURN, CheckLevel.ERROR);
    String src =
        "/** @return {number} */\n"
        + "temp = function() { var t = 20; };\n";
    Result result = runReplaceScript(options,
        ImmutableList.of(src), 1, 0, src, 0, true).getResult();
    assertNumWarningsAndErrors(result, 1, 0);
    assertErrorType(result.errors[0],
        CheckMissingReturn.MISSING_RETURN_STATEMENT, 2);
  }

  /** Test related to DefaultPassConfig.closureGoogScopeAliases */
  public void testGoogScope() {
    // Checking a type of error to make sure goog.scope is processed.
    CompilerOptions options = getOptions(DiagnosticGroups.ACCESS_CONTROLS);
    options.setCheckTypes(true);

    String src0 =
        "/** @constructor */\n"
        + "test.Bar = function() { this.privNum = 10; };\n"
        + "/** @private */\n"
        + "test.Bar.prototype.privNum;\n";
  String src1 = "var a = new test.Bar();\n";
  String modifiedSrc1 = "goog.scope(function() {\n"
      + "  var Bar = test.Bar;\n"
      + "  test.test = function() {\n"
      + "    var a = new Bar();\n"
      + "    var b = a.privNum;\n"
      + "  };"
      + "});";

    Result result = this.runReplaceScript(options, ImmutableList.of(src0,
        src1), 0, 0, modifiedSrc1, 1, true).getResult();
        //ImmutableList.of(src0, modifiedSrc1), 1, 0, modifiedSrc1, 1, true);
    assertFalse(result.success);
    assertThat(result.errors).hasLength(1);
    JSError e = result.errors[0];
    assertErrorType(result.errors[0],
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS, 5);
    assertEquals(e.lineNumber, 5);
  }

  /**
   * Test related to PassConfig.patchGlobalTypedScope.
   * First it generates the global typed scope in a normal full compile. Then
   * with no modifications calls patchGlobalTypedScope on one of the scripts and
   * compare the results to full-compile. Then changes one script and checks
   * the results again.
   */
  public void testPatchGlobalTypedScope() {
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_TYPES);
    String externSrc = "/** @type {number} */ var aNum;\n";
    String src1 = "goog.provide('unique.Bar');\n"
        + "/** @constructor */ unique.Bar = function() {};\n"
        + "/** @type {unique.Bar} */ var obj1 = new unique.Bar();\n"
        + "var testNum = 20;\n"
        + "var objNoType1 = new unique.Bar();\n";
    String src2 = "goog.require('unique.Bar');\n"
        + "/** @type {unique.Bar} */ var obj2 = new unique.Bar();\n"
        + "var objNoType2 = new unique.Bar();";

    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("in1", src1),
        SourceFile.fromCode("in2", src2));

    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("extern", externSrc));

    Compiler compiler = new Compiler();
    Compiler.setLoggingLevel(Level.INFO);
    compiler.compile(externs, inputs, options);
    assertTrue(compiler.getResult().success);
    TypedScope oldGlobalScope = compiler.getTopScope();

    SourceFile newSource1 = SourceFile.fromCode("in1", src1);
    JsAst ast = new JsAst(newSource1);
    compiler.replaceScript(ast);
    assertTrue(compiler.getResult().success);
    assertScopesSimilar(oldGlobalScope, compiler.getTopScope());
    assertScopeAndThisForScopeSimilar(compiler.getTopScope());


    SourceFile newSource2 = SourceFile.fromCode("in2", src2);
    ast = new JsAst(newSource2);
    compiler.replaceScript(ast);
    assertTrue(compiler.getResult().success);
    assertScopesSimilar(oldGlobalScope, compiler.getTopScope());
    assertScopeAndThisForScopeSimilar(compiler.getTopScope());

    newSource2 = SourceFile.fromCode("in2", "");
    ast = new JsAst(newSource2);
    compiler.replaceScript(ast);
    assertTrue(compiler.getResult().success);
    assertSubsetScope(compiler.getTopScope(), oldGlobalScope,
        ImmutableSet.of("obj2", "objNoType2"));
    assertScopeAndThisForScopeSimilar(compiler.getTopScope());
  }

  private void assertScopeAndThisForScopeSimilar(TypedScope scope) {
    ObjectType typeOfThis = scope.getTypeOfThis().toObjectType();
    for (TypedVar v : scope.getAllSymbols()) {
      if (!v.getName().contains(".")) {
        assertEquals(v.getNameNode(), typeOfThis.getPropertyNode(v.getName()));
      }
    }
  }

  private void assertScopesSimilar(TypedScope scope1, TypedScope scope2) {
    assertSubsetScope(scope1, scope2, new HashSet<String>());
  }

  private void assertSubsetScope(TypedScope subScope, TypedScope scope,
      Set<String> missingVars) {
    for (TypedVar var1 : scope.getVarIterable()) {
      TypedVar var2 = subScope.getVar(var1.getName());
      if (missingVars.contains(var1.getName())) {
        assertNull(var2);
      } else {
        assertNotNull(var2);
        assertEquals(var1.getType(), var2.getType());
      }
    }
  }

  public void testInferJsDocInfo() {
    CompilerOptions options = getOptions();
    options.inferTypes = true;
    String src = "";
    String modifiedSrc = "/** @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @type {number} */\n"
        + "Foo.prototype.prop = 10;\n"
        + "var temp = new Foo();";
    Compiler compiler = runReplaceScript(options,
        ImmutableList.of(src), 0, 0, modifiedSrc, 0, true);
    TypedVar var = compiler.getTopScope().getVar("temp");
    ObjectType type = var.getType().toObjectType().getImplicitPrototype();
    assertNotNull(type.getOwnPropertyJSDocInfo("prop"));
  }

  /** Effectively this tests the clean-up of properties on un-named objects. */
  public void testNoErrorOnGoogProvide() {
    CompilerOptions options = getOptions(DiagnosticGroups.CHECK_TYPES);
    String src0 =
        "goog.provide('ns.Foo')\n"
        + "ns.Foo = function() {};\n";
    String src1 = "goog.provide('ns.Bar')\n"
        + "ns.Bar.bar = function() {};\n";
    Result result = this.runReplaceScript(options,
        ImmutableList.of(src0, src1), 0, 0, src1, 1, false).getResult();
    assertTrue(result.success);

    assertThat(result.errors).isEmpty();
    assertThat(result.warnings).isEmpty();
  }

  public void testAddSimpleScript() {
    CompilerOptions options = getOptions();
    options.setClosurePass(false);

    String src =
        "goog.provide('Bar');\n" +
        "/** @constructor */\n" +
        "Bar = function() {};\n";
    String otherSrc =
        "goog.require('Bar');\n" +
        "Bar.foo = function() {};\n";

    Compiler compiler = runAddScript(options,
        ImmutableList.of(CLOSURE_BASE, src), 0, 0, otherSrc, false);

    assertNoWarningsOrErrors(compiler.getResult());
    verifyPropertyLineno(compiler, "Bar", "foo", 2);
  }

  public void testAddExistingScript() {
    CompilerOptions options = getOptions();

    String src =
        "goog.provide('Bar');\n" +
        "/** @constructor */\n" +
        "Bar = function() {};\n";
    String otherSrc =
        "goog.require('Bar');\n" +
        "Bar.foo = function() {};\n";
    String updatedOtherSrc =
        "goog.require('Bar');\n" +
        "\n\n" +
        "Bar.foo = function() {};\n";

    Compiler compiler = runAddScript(
        options, ImmutableList.of(CLOSURE_BASE, src), 0, 0, otherSrc, false);

    try {
      doAddScript(compiler, updatedOtherSrc, 1);
      fail("Expected an IllegalStateException to be thrown");
    } catch (IllegalStateException expectedISE) {
      //ignore expected exception
    }

    // Position of the definition will not have moved as we did not add the
    // updated script
    verifyPropertyLineno(compiler, "Bar", "foo", 2);
  }
}
