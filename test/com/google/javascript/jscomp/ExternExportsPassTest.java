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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link ExternExportsPass}.
 *
 */

public final class ExternExportsPassTest extends TestCase {

  private boolean runCheckTypes = true;

  /**
   * ExternExportsPass relies on type information to emit JSDoc annotations for
   * exported externs. However, the user can disable type checking and still
   * ask for externs to be exported. Set this flag to enable or disable checking
   * of types during a test.
   */
  private void setRunCheckTypes(boolean shouldRunCheckTypes) {
    runCheckTypes = shouldRunCheckTypes;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setRunCheckTypes(true);
  }

  public void testExportSymbol() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportSymbol('foobar', a.b.c)",
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "var foobar = function(d, e, f) {\n};\n");
  }

  public void testExportSymbolDefinedInVar() throws Exception {
    compileAndCheck("var a = function(d, e, f) {};" +
                    "goog.exportSymbol('foobar', a)",
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "var foobar = function(d, e, f) {\n};\n");
  }

  public void testExportProperty() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportProperty(a.b, 'cprop', a.b.c)",
                    "var a;\n" +
                    "a.b;\n" +
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "a.b.cprop = function(d, e, f) {\n};\n");
  }

  public void testExportMultiple() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('a.b', a.b);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);" +
                    "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);",

                    "var a;\n" +
                    "/**\n" +
                    " * @param {?} p1\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "a.b = function(p1) {\n};\n" +
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "a.b.c = function(d, e, f) {\n};\n" +
                    "/**\n" +
                    " * @param {?} g\n" +
                    " * @param {?} h\n" +
                    " * @param {?} i\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "a.b.prototype.c = function(g, h, i) {\n};\n");
  }

  public void testExportMultiple2() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('hello', a);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);" +
                    "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);",

                    "/** @type {{b: function (?): undefined}} */\n" +
                    "var hello = {};\n" +
                    "hello.b;\n" +
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "hello.b.c = function(d, e, f) {\n};\n" +
                    "/**\n" +
                    " * @param {?} g\n" +
                    " * @param {?} h\n" +
                    " * @param {?} i\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "hello.b.prototype.c = function(g, h, i) {\n};\n");
  }

  public void testExportMultiple3() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('prefix', a.b);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);",

                    "/**\n" +
                    " * @param {?} p1\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "var prefix = function(p1) {\n};\n" +
                    "/**\n" +
                    " * @param {?} d\n" +
                    " * @param {?} e\n" +
                    " * @param {?} f\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "prefix.c = function(d, e, f) {\n};\n");
  }

  public void testExportNonStaticSymbol() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; var d = {}; a.b.c = d;" +
                    "goog.exportSymbol('foobar', a.b.c)",
                    "var foobar;\n");
  }

  public void testExportNonStaticSymbol2() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; var d = null; a.b.c = d;" +
                    "goog.exportSymbol('foobar', a.b.c())",
                    "var foobar;\n");
  }

  public void testExportNonexistentProperty() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportProperty(a.b, 'none', a.b.none)",
                    "var a;\n" +
                    "a.b;\n" +
                    "a.b.none;\n");
  }

  public void testExportSymbolWithTypeAnnotation() {

    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @param {number} param2\n" +
                    " * @return {string}\n" +
                    " */\n" +
                    "internalName = function(param1, param2) {" +
                      "return param1 + param2;" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @param {number} param2\n" +
                    " * @return {string}\n" +
                    " */\n" +
                    "var externalName = function(param1, param2) {\n};\n");
  }

  public void testExportSymbolWithTemplateAnnotation() {

    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @param {T} param1\n" +
                    " * @return {T}\n" +
                    " * @template T\n" +
                    " */\n" +
                    "internalName = function(param1) {" +
                      "return param1;" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "/**\n" +
                    " * @param {T} param1\n" +
                    " * @return {T}\n" +
                    " * @template T\n" +
                    " */\n" +
                    "var externalName = function(param1) {\n};\n");
  }

  public void testExportSymbolWithMultipleTemplateAnnotation() {

    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @param {K} param1\n" +
                    " * @return {V}\n" +
                    " * @template K,V\n" +
                    " */\n" +
                    "internalName = function(param1) {" +
                      "return /** @type {V} */ (param1);" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "/**\n" +
                    " * @param {K} param1\n" +
                    " * @return {V}\n" +
                    " * @template K,V\n" +
                    " */\n" +
                    "var externalName = function(param1) {\n};\n");
  }

  public void testExportSymbolWithoutTypeCheck() {
    // ExternExportsPass should not emit annotations
    // if there is no type information available.
    setRunCheckTypes(false);

    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @param {number} param2\n" +
                    " * @return {string}\n" +
                    " */\n" +
                    "internalName = function(param1, param2) {" +
                      "return param1 + param2;" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "var externalName = function(param1, param2) {\n};\n");
  }

  public void testExportSymbolWithConstructor() {
    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @constructor\n" +
                    " */\n" +
                    "internalName = function() {" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "/**\n" +
                    " * @constructor\n" +
                    " */\n" +
                    "var externalName = function() {\n};\n");
  }

  public void testNonNullTypes() {
    compileAndCheck(
        Joiner.on("\n").join(
            "/**",
            " * @constructor",
            " */",
            "function Foo() {}",
            "goog.exportSymbol('Foo', Foo);",
            "/**",
            " * @param {!Foo} x",
            " * @return {!Foo}",
            " */",
            "Foo.f = function(x) { return x; };",
            "goog.exportProperty(Foo, 'f', Foo.f);"),
        Joiner.on("\n").join(
            "/**",
            " * @constructor",
            " */",
            "var Foo = function() {\n};",
            "/**",
            " * @param {!Foo} x",
            " * @return {!Foo}",
            " */",
            "Foo.f = function(x) {\n};\n"));
  }

  public void testExportSymbolWithConstructorWithoutTypeCheck() {
    // For now, skipping type checking should prevent generating
    // annotations of any kind, so, e.g., @constructor is not preserved.
    // This is probably not ideal, but since JSDocInfo for functions is attached
    // to JSTypes and not Nodes (and no JSTypes are created when checkTypes
    // is false), we don't really have a choice.

    setRunCheckTypes(false);

    compileAndCheck("var internalName;\n" +
                    "/**\n" +
                    " * @constructor\n" +
                    " */\n" +
                    "internalName = function() {" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "var externalName = function() {\n};\n");
  }

  public void testExportFunctionWithOptionalArguments1() {
    compileAndCheck("var internalName;\n" +
        "/**\n" +
        " * @param {number=} a\n" +
        " */\n" +
        "internalName = function(a) {" +
        "};" +
        "goog.exportSymbol('externalName', internalName)",
        "/**\n" +
        " * @param {number=} a\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var externalName = function(a) {\n};\n");
  }

  public void testExportFunctionWithOptionalArguments2() {
    compileAndCheck("var internalName;\n" +
        "/**\n" +
        " * @param {number=} a\n" +
        " */\n" +
        "internalName = function(a) {" +
        "  return 6;\n" +
        "};" +
        "goog.exportSymbol('externalName', internalName)",
        "/**\n" +
        " * @param {number=} a\n" +
        " * @return {?}\n" +
        " */\n" +
        "var externalName = function(a) {\n};\n");
  }

  public void testExportFunctionWithOptionalArguments3() {
    compileAndCheck("var internalName;\n" +
        "/**\n" +
        " * @param {number=} a\n" +
        " */\n" +
        "internalName = function(a) {" +
        "  return a;\n" +
        "};" +
        "goog.exportSymbol('externalName', internalName)",
        "/**\n" +
        " * @param {number=} a\n" +
        " * @return {?}\n" +
        " */\n" +
        "var externalName = function(a) {\n};\n");
  }

  public void testExportFunctionWithVariableArguments() {
    compileAndCheck("var internalName;\n" +
        "/**\n" +
        " * @param {...number} a\n" +
        " * @return {number}\n" +
        " */\n" +
        "internalName = function(a) {" +
        "  return 6;\n" +
        "};" +
        "goog.exportSymbol('externalName', internalName)",
        "/**\n" +
        " * @param {...number} a\n" +
        " * @return {number}\n" +
        " */\n" +
        "var externalName = function(a) {\n};\n");
  }

  /**
   * Enums are not currently handled.
   */
   public void testExportEnum() {
     // We don't care what the values of the object properties are.
     // They're ignored by the type checker, and even if they weren't, it'd
     // be incomputable to get them correct in all cases
     // (think complex objects).
     compileAndCheck(
         "/** @enum {string}\n @export */ var E = {A:8, B:9};" +
         "goog.exportSymbol('E', E);",
         "/** @enum {string} */\n" +
         "var E = {A:1, B:2};\n");
   }

  /** If we export a property with "prototype" as a path component, there
    * is no need to emit the initializer for prototype because every namespace
    * has one automatically.
    */
  public void testExportDontEmitPrototypePathPrefix() {
    compileAndCheck(
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "var Foo = function() {};" +
        "/**\n" +
        " * @return {number}\n" +
        " */\n" +
        "Foo.prototype.m = function() {return 6;};\n" +
        "goog.exportSymbol('Foo', Foo);\n" +
        "goog.exportProperty(Foo.prototype, 'm', Foo.prototype.m);",
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "var Foo = function() {\n};\n" +
        "/**\n" +
        " * @return {number}\n" +
        " */\n" +
        "Foo.prototype.m = function() {\n};\n"
    );
  }

  /**
   * Test the workflow of creating an externs file for a library
   * via the export pass and then using that externs file in a client.
   *
   * There should be no warnings in the client if the library includes
   * type information for the exported functions and the client uses them
   * correctly.
   */
  public void testUseExportsAsExterns() {
    String librarySource =
    "/**\n" +
    " * @param {number} a\n" +
    " * @constructor\n" +
    " */\n" +
    "var InternalName = function(a) {" +
    "};" +
    "goog.exportSymbol('ExternalName', InternalName)";

    String clientSource =
      "var a = new ExternalName(6);\n" +
      "/**\n" +
      " * @param {ExternalName} x\n" +
      " */\n" +
      "var b = function(x) {};";

    Result libraryCompileResult = compileAndExportExterns(librarySource);

    assertThat(libraryCompileResult.warnings).isEmpty();
    assertThat(libraryCompileResult.errors).isEmpty();

    String generatedExterns = libraryCompileResult.externExport;

    Result clientCompileResult = compileAndExportExterns(clientSource,
        generatedExterns);

    assertThat(clientCompileResult.warnings).isEmpty();
    assertThat(clientCompileResult.errors).isEmpty();
  }

  public void testDontWarnOnExportFunctionWithUnknownReturnType() {
    String librarySource =
      "var InternalName = function() {" +
      "  return 6;" +
      "};" +
      "goog.exportSymbol('ExternalName', InternalName)";

      Result libraryCompileResult = compileAndExportExterns(librarySource);

      assertThat(libraryCompileResult.warnings).isEmpty();
      assertThat(libraryCompileResult.errors).isEmpty();
  }

  public void testDontWarnOnExportConstructorWithUnknownReturnType() {
    String librarySource =
      "/**\n" +
      " * @constructor\n" +
      " */\n " +
      "var InternalName = function() {" +
      "};" +
      "goog.exportSymbol('ExternalName', InternalName)";

      Result libraryCompileResult = compileAndExportExterns(librarySource);

      assertThat(libraryCompileResult.warnings).isEmpty();
      assertThat(libraryCompileResult.errors).isEmpty();
  }

  public void testTypedef() {
    compileAndCheck(
        "/** @typedef {{x: number, y: number}} */ var Coord;\n" +
        "/**\n" +
        " * @param {Coord} a\n" +
        " * @export\n" +
        " */\n" +
        "var fn = function(a) {};" +
        "goog.exportSymbol('fn', fn);",
        "/**\n" +
        " * @param {{x: number, y: number}} a\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var fn = function(a) {\n};\n");
  }

  public void testExportParamWithNull() throws Exception {
    compileAndCheck(
        "/** @param {string|null=} d */\n" +
        "var f = function(d) {};\n" +
        "goog.exportSymbol('foobar', f)\n",
        "/**\n" +
        " * @param {(null|string)=} d\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var foobar = function(d) {\n" +
        "};\n");
  }

  public void testExportConstructor() throws Exception {
    compileAndCheck("/** @constructor */ var a = function() {};" +
                    "goog.exportSymbol('foobar', a)",
                    "/**\n" +
                    " * @constructor\n" +
                    " */\n" +
                    "var foobar = function() {\n};\n");
  }

  public void testExportLocalPropertyInConstructor() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x = 5;}"
        + "goog.exportSymbol('F', F);",
        "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "var F = function() {\n};\n"
        + "F.prototype.x;\n");
  }

  public void testExportLocalPropertyInConstructor2() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x = 5;}"
        + "goog.exportSymbol('F', F);"
        + "goog.exportProperty(F.prototype, 'x', F.prototype.x);",
        "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "var F = function() {\n};\n"
        + "F.prototype.x;\n");
  }

  public void testExportLocalPropertyInConstructor3() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x;}"
        + "goog.exportSymbol('F', F);",
        "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "var F = function() {\n};\n"
        + "F.prototype.x;\n");
  }

  public void testExportLocalPropertyInConstructor4() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x = function(/** string */ x){};}"
        + "goog.exportSymbol('F', F);",
        "/**\n"
        + " * @constructor\n"
        + " */\n"
        + "var F = function() {\n};\n"
        + "/**\n"
        + " * @param {string} x\n"
        + " * @return {undefined}\n"
        + " */\n"
        + "F.prototype.x = function(x) {\n};\n");
  }

  public void testExportLocalPropertyNotInConstructor() throws Exception {
    compileAndCheck(
        "function f() { /** @export */ this.x = 5;}"
        + "goog.exportSymbol('f', f);",
        "/**\n"
        + " * @return {undefined}\n"
        + " */\n"
        + "var f = function() {\n};\n");
  }

  public void testExportParamWithSymbolDefinedInFunction() throws Exception {
    compileAndCheck(
        "var id = function() {return 'id'};\n" +
        "var ft = function() {\n" +
        "  var id;\n" +
        "  return 1;\n" +
        "};\n" +
        "goog.exportSymbol('id', id);\n",
        "/**\n" +
        " * @return {?}\n" +
        " */\n" +
        "var id = function() {\n" +
        "};\n");
  }

  public void testExportSymbolWithFunctionDefinedAsFunction() {

    compileAndCheck("/**\n" +
                    " * @param {string} param1\n" +
                    " * @return {string}\n" +
                    " */\n" +
                    "function internalName(param1) {" +
                      "return param1" +
                    "};" +
                    "goog.exportSymbol('externalName', internalName)",
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @return {string}\n" +
                    " */\n" +
                    "var externalName = function(param1) {\n};\n");
  }

  public void testExportSymbolWithFunctionAlias() {

    compileAndCheck("/**\n" +
                    " * @param {string} param1\n" +
                    " */\n" +
                    "var y = function(param1) {" +
                    "};" +
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @param {string} param2\n" +
                    " */\n" +
                    "var x = function y(param1, param2) {" +
                    "};" +
                    "goog.exportSymbol('externalName', y)",
                    "/**\n" +
                    " * @param {string} param1\n" +
                    " * @return {undefined}\n" +
                    " */\n" +
                    "var externalName = function(param1) {\n};\n");
  }

  public void testNamespaceDefinitionInExterns() throws Exception {
    compileAndCheck(
        Joiner.on("\n").join(
            "/** @const */",
            "var ns = {};",
            "/** @const */",
            "ns.subns = {};",
            "/** @constructor */",
            "ns.subns.Foo = function() {};",
            "goog.exportSymbol('ns.subns.Foo', ns.subns.Foo);"),
        Joiner.on("\n").join(
            "/**",
            " @const",
            " @suppress {const,duplicate}",
            " */",
            "var ns = {};",
            "/**",
            " @const",
            " @suppress {const,duplicate}",
            " */",
            "ns.subns = {};",
            "/**",
            " * @constructor",
            " */",
            "ns.subns.Foo = function() {",
            "};",
            ""));
  }

  private void compileAndCheck(String js, String expected) {
    Result result = compileAndExportExterns(js);

    assertEquals(expected, result.externExport);
  }

  public void testDontWarnOnExportFunctionWithUnknownParameterTypes() {
    /* This source is missing types for the b and c parameters */
    String librarySource =
      "/**\n" +
      " * @param {number} a\n" +
      " * @return {number}" +
      " */\n " +
      "var InternalName = function(a,b,c) {" +
      "  return 6;" +
      "};" +
      "goog.exportSymbol('ExternalName', InternalName)";

      Result libraryCompileResult = compileAndExportExterns(librarySource);

      assertThat(libraryCompileResult.warnings).isEmpty();
      assertThat(libraryCompileResult.errors).isEmpty();
  }

  private Result compileAndExportExterns(String js) {
    return compileAndExportExterns(js, "");
  }

  /**
   * Compiles the passed in JavaScript with the passed in externs and returns
   * the new externs exported by the this pass.
   *
   * @param js the source to be compiled
   * @param externs the externs the {@code js} source needs
   * @return the externs generated from {@code js}
   */
  private Result compileAndExportExterns(String js, String externs) {
    Compiler compiler = new Compiler();
    BlackHoleErrorManager.silence(compiler);
    CompilerOptions options = new CompilerOptions();
    options.externExportsPath = "externs.js";
    options.declaredGlobalExternsOnWindow = false;

    // Turn off IDE mode.
    options.setIdeMode(false);

    /* Check types so we can make sure our exported externs have
     * type information.
     */
    options.setCheckSymbols(true);
    options.setCheckTypes(runCheckTypes);

    List<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode(
        "testcode",
        "var goog = {};"
        + "goog.exportSymbol = function(a, b) {}; "
        + "goog.exportProperty = function(a, b, c) {}; " + js));

    List<SourceFile> externFiles = ImmutableList.of(
        SourceFile.fromCode("externs", externs));

    Result result = compiler.compile(externFiles, inputs, options);

    if (!result.success) {
      String msg = "Errors:";
      msg += Joiner.on("\n").join(result.errors);
      assertTrue(msg, result.success);
    }

    return result;
  }
}
