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

import java.util.function.Consumer;

/**
 * Tests for {@link ExternExportsPass}.
 *
 */
public final class ExternExportsPassTest extends TypeICompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    this.mode = TypeInferenceMode.BOTH;
  }

  @Override
  public CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.externExportsPath = "externs.js";
    // Check types so we can make sure our exported externs have type information.
    options.setCheckSymbols(true);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExternExportsPass(compiler);
  }

  public void testExportSymbol() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {}; /** @const */ a.b = {}; a.b.c = function(d, e, f) {};",
            "goog.exportSymbol('foobar', a.b.c)"),
        lines(
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "var foobar = function(d, e, f) {",
            "};",
            ""));
  }

  public void testInterface() {
    compileAndCheck(
        "/** @interface */ function Iface() {}; goog.exportSymbol('Iface', Iface)",
        lines(
            "/**",
            " * @interface",
            " */",
            "var Iface = function() {",
            "};",
            ""));
  }

  public void testRecord() {
    compileAndCheck(
        "/** @record */ function Iface() {}; goog.exportSymbol('Iface', Iface)",
        lines(
            "/**",
            " * @record",
            " */",
            "var Iface = function() {",
            "};",
            ""));
  }

  public void testExportSymbolDefinedInVar() throws Exception {
    compileAndCheck(
        "var a = function(d, e, f) {}; goog.exportSymbol('foobar', a)",
        lines(
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "var foobar = function(d, e, f) {",
            "};",
            ""));
  }

  public void testExportProperty() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {}; /** @const */ a.b = {}; a.b.c = function(d, e, f) {};",
            "goog.exportProperty(a.b, 'cprop', a.b.c)"),
        lines(
            "var a;",
            "a.b;",
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "a.b.cprop = function(d, e, f) {",
            "};",
            ""));
  }

  public void testExportMultiple() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {}; a.b = function(p1) {};",
            "a.b.c = function(d, e, f) {};",
            "a.b.prototype.c = function(g, h, i) {};",
            "goog.exportSymbol('a.b', a.b);",
            "goog.exportProperty(a.b, 'c', a.b.c);",
            "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);"),
        lines(
            "var a;",
            "/**",
            " * @param {?} p1",
            " * @return {undefined}",
            " */",
            "a.b = function(p1) {",
            "};",
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "a.b.c = function(d, e, f) {",
            "};",
            "/**",
            " * @param {?} g",
            " * @param {?} h",
            " * @param {?} i",
            " * @return {undefined}",
            " */",
            "a.b.prototype.c = function(g, h, i) {",
            "};",
            ""));
  }

  public void testExportMultiple2() throws Exception {
    // TODO(sdh): NTI leaves out the annotation for hello for some reason.
    this.mode = TypeInferenceMode.OTI_ONLY;
    compileAndCheck(
        lines(
            "/** @const */ var a = {};",
            "a.b = function(p1) {};",
            "a.b.c = function(d, e, f) {};",
            "a.b.prototype.c = function(g, h, i) {};",
            "goog.exportSymbol('hello', a);",
            "goog.exportProperty(a.b, 'c', a.b.c);",
            "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);"),
        lines(
            "/** @type {{b: function(?): undefined}} */",
            "var hello = {};",
            "hello.b;",
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "hello.b.c = function(d, e, f) {",
            "};",
            "/**",
            " * @param {?} g",
            " * @param {?} h",
            " * @param {?} i",
            " * @return {undefined}",
            " */",
            "hello.b.prototype.c = function(g, h, i) {",
            "};",
            ""));
  }

  public void testExportMultiple3() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {}; a.b = function(p1) {};",
            "a.b.c = function(d, e, f) {};",
            "a.b.prototype.c = function(g, h, i) {};",
            "goog.exportSymbol('prefix', a.b);",
            "goog.exportProperty(a.b, 'c', a.b.c);"),
        lines(
            "/**",
            " * @param {?} p1",
            " * @return {undefined}",
            " */",
            "var prefix = function(p1) {",
            "};",
            "/**",
            " * @param {?} d",
            " * @param {?} e",
            " * @param {?} f",
            " * @return {undefined}",
            " */",
            "prefix.c = function(d, e, f) {",
            "};",
            ""));
  }

  public void testExportNonStaticSymbol() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {};",
            "/** @const */ a.b = {};",
            "/** @const */ var d = {};",
            "a.b.c = d;",
            "goog.exportSymbol('foobar', a.b.c)"),
        "var foobar;\n");
  }

  public void testExportNonStaticSymbol2() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */ var a = {};",
            "/** @const */ a.b = {};",
            "var d = function() {};",
            "a.b.c = d;",
            "goog.exportSymbol('foobar', a.b.c())"),
        "var foobar;\n");
  }

  public void testExportNonexistentProperty() throws Exception {
    compileAndCheck(
        lines(
            "/** @fileoverview @suppress {missingProperties} */",
            "/** @const */ var a = {};",
            "/** @const */ a.b = {};",
            "a.b.c = function(d, e, f) {};",
            "goog.exportProperty(a.b, 'none', a.b.none)"),
        lines(
            "var a;",
            "a.b;",
            "a.b.none;",
            ""));
  }

  public void testExportSymbolWithTypeAnnotation() {

    compileAndCheck(
        lines(
            "var internalName;",
            "/**",
            " * @param {string} param1",
            " * @param {number} param2",
            " * @return {string}",
            " */",
            "internalName = function(param1, param2) {",
            "return param1 + param2;",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {string} param1",
            " * @param {number} param2",
            " * @return {string}",
            " */",
            "var externalName = function(param1, param2) {",
            "};",
            ""));
  }

  public void testExportSymbolWithTemplateAnnotation() {

    compileAndCheck(
        lines(
            "var internalName;",
            "/**",
            " * @param {T} param1",
            " * @return {T}",
            " * @template T",
            " */",
            "internalName = function(param1) {",
            "return param1;",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {T} param1",
            " * @return {T}",
            " * @template T",
            " */",
            "var externalName = function(param1) {",
            "};",
            ""));
  }

  public void testExportSymbolWithMultipleTemplateAnnotation() {

    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {K} param1",
            " * @return {V}",
            " * @template K,V",
            " */",
            "internalName = function(param1) {",
            "  return /** @type {?} */ (param1);",
            "};",
            "goog.exportSymbol('externalName', internalName);"),
        lines(
            "/**",
            " * @param {K} param1",
            " * @return {V}",
            " * @template K,V",
            " */",
            "var externalName = function(param1) {",
            "};",
            ""));
  }

  public void testExportSymbolWithoutTypeCheck() {
    // ExternExportsPass should not emit annotations
    // if there is no type information available.
    this.mode = TypeInferenceMode.NEITHER;

    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {string} param1",
            " * @param {number} param2",
            " * @return {string}",
            " */",
            "internalName = function(param1, param2) {",
            "return param1 + param2;",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "var externalName = function(param1, param2) {",
            "};",
            ""));
  }

  public void testExportSymbolWithConstructor() {
    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @constructor",
            " */",
            "internalName = function() {",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "var externalName = function() {",
            "};",
            ""));
  }

  public void testNonNullTypes() {
    compileAndCheck(
        lines(
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
        lines(
            "/**",
            " * @constructor",
            " */",
            "var Foo = function() {",
            "};",
            "/**",
            " * @param {!Foo} x",
            " * @return {!Foo}",
            " */",
            "Foo.f = function(x) {",
            "};",
            ""));
  }

  public void testExportSymbolWithConstructorWithoutTypeCheck() {
    // For now, skipping type checking should prevent generating
    // annotations of any kind, so, e.g., @constructor is not preserved.
    // This is probably not ideal, but since JSDocInfo for functions is attached
    // to JSTypes and not Nodes (and no JSTypes are created when checkTypes
    // is false), we don't really have a choice.

    this.mode = TypeInferenceMode.NEITHER;

    compileAndCheck(
        lines(
            "var internalName;",
            "/**",
            " * @constructor",
            " */",
            "internalName = function() {",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "var externalName = function() {",
            "};",
            ""));
  }

  // x.Y is present in the generated externs but lacks the @constructor annotation.
  public void testExportPrototypePropsWithoutConstructor() {
    compileAndCheck(
        lines(
            "/** @constructor */",
            "x.Y = function() {};",
            "x.Y.prototype.z = function() {};",
            "goog.exportProperty(x.Y.prototype, 'z', x.Y.prototype.z);"),
        lines(
            "var x;",
            "x.Y;",
            "/**",
            " * @return {undefined}",
            " */",
            "x.Y.prototype.z = function() {",
            "};",
            ""));
  }

  public void testExportFunctionWithOptionalArguments1() {
    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {number=} a",
            " */",
            "internalName = function(a) {",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {number=} a",
            " * @return {undefined}",
            " */",
            "var externalName = function(a) {",
            "};",
            ""));
  }

  public void testExportFunctionWithOptionalArguments2() {
    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {number=} a",
            " */",
            "internalName = function(a) {",
            "  return /** @type {?} */ (6);",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {number=} a",
            " * @return {?}",
            " */",
            "var externalName = function(a) {",
            "};",
            ""));
  }

  public void testExportFunctionWithOptionalArguments3() {
    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {number=} a",
            " */",
            "internalName = function(a) {",
            "  return /** @type {?} */ (a);",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {number=} a",
            " * @return {?}",
            " */",
            "var externalName = function(a) {",
            "};",
            ""));
  }

  public void testExportFunctionWithVariableArguments() {
    compileAndCheck(
        lines(
            "var internalName;",
            "",
            "/**",
            " * @param {...number} a",
            " * @return {number}",
            " */",
            "internalName = function(a) {",
            "  return 6;",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {...number} a",
            " * @return {number}",
            " */",
            "var externalName = function(a) {",
            "};",
            ""));
  }

  /** Enums are not currently handled. */
  public void testExportEnum() {
    // We don't care what the values of the object properties are.
    // They're ignored by the type checker, and even if they weren't, it'd
    // be incomputable to get them correct in all cases
    // (think complex objects).
    compileAndCheck(
        lines(
            "/**",
            " * @enum {string}",
            " * @export",
            " */",
            "var E = {A:'a', B:'b'};",
            "goog.exportSymbol('E', E);"),
        lines(
            "/** @enum {string} */",
            "var E = {A:1, B:2};",
            ""));
  }

  public void testExportWithReferenceToEnum() {
    String js = lines(
        "/**",
        " * @enum {number}",
        " * @export",
        " */",
        "var E = {A:1, B:2};",
        "goog.exportSymbol('E', E);",
        "",
        "/**",
        " * @param {!E} e",
        " * @export",
        " */",
        "function f(e) {}",
        "goog.exportSymbol('f', f);");
    String expected = lines(
        "/** @enum {number} */",
        "var E = {A:1, B:2};",
        "/**",
        " * @param {E} e",
        " * @return {undefined}",
        " */",
        "var f = function(e) {",
        "};",
        "");

    this.mode = TypeInferenceMode.OTI_ONLY;
    // NOTE: OTI should print {E} for the @param, but does not.
    compileAndCheck(js, expected.replace("{E}", "{number}"));
    this.mode = TypeInferenceMode.NTI_ONLY;
    compileAndCheck(js, expected);
  }

  /** If we export a property with "prototype" as a path component, there
    * is no need to emit the initializer for prototype because every namespace
    * has one automatically.
    */
  public void testExportDontEmitPrototypePathPrefix() {
    compileAndCheck(
        lines(
            "/**",
            " * @constructor",
            " */",
            "var Foo = function() {};",
            "/**",
            " * @return {number}",
            " */",
            "Foo.prototype.m = function() {return 6;};",
            "goog.exportSymbol('Foo', Foo);",
            "goog.exportProperty(Foo.prototype, 'm', Foo.prototype.m);"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "var Foo = function() {",
            "};",
            "/**",
            " * @return {number}",
            " */",
            "Foo.prototype.m = function() {",
            "};",
            ""));
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
        lines(
            "/**",
            " * @param {number} n",
            " * @constructor",
            " */",
            "var InternalName = function(n) {",
            "};",
            "goog.exportSymbol('ExternalName', InternalName)");

    String clientSource =
        lines(
            "var foo = new ExternalName(6);",
            "/**",
            " * @param {ExternalName} x",
            " */",
            "var bar = function(x) {};");

    compileAndExportExterns(librarySource, MINIMAL_EXTERNS, new Consumer<String>() {
      @Override public void accept(String generatedExterns) {
        compileAndExportExterns(clientSource, MINIMAL_EXTERNS + generatedExterns);
      }
    });
  }

  public void testDontWarnOnExportFunctionWithUnknownReturnType() {
    String librarySource = lines(
        "var InternalName = function() {",
        "  return 6;",
        "};",
        "goog.exportSymbol('ExternalName', InternalName)");

    compileAndExportExterns(librarySource);
  }

  public void testDontWarnOnExportConstructorWithUnknownReturnType() {
    String librarySource = lines(
        "/**",
        " * @constructor",
        " */",
        "var InternalName = function() {",
        "};",
        "goog.exportSymbol('ExternalName', InternalName)");

    compileAndExportExterns(librarySource);
  }

  public void testTypedef() {
    compileAndCheck(
        lines(
            "/** @typedef {{x: number, y: number}} */ var Coord;",
            "/**",
            " * @param {Coord} a",
            " * @export",
            " */",
            "var fn = function(a) {};",
            "goog.exportSymbol('fn', fn);"),
        lines(
            "/**",
            " * @param {{x: number, y: number}} a",
            " * @return {undefined}",
            " */",
            "var fn = function(a) {",
            "};",
            ""));
  }

  public void testExportParamWithNull() throws Exception {
    compileAndCheck(
        lines(
            "/** @param {string|null=} d */",
            "var f = function(d) {};",
            "goog.exportSymbol('foobar', f)",
            ""),
        lines(
            "/**",
            " * @param {(null|string)=} d",
            " * @return {undefined}",
            " */",
            "var foobar = function(d) {",
            "};",
            ""));
  }

  public void testExportConstructor() throws Exception {
    compileAndCheck(
        "/** @constructor */ var a = function() {}; goog.exportSymbol('foobar', a)",
        lines(
            "/**",
            " * @constructor",
            " */",
            "var foobar = function() {",
            "};",
            ""));
  }

  public void testExportLocalPropertyInConstructor() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x = 5;} goog.exportSymbol('F', F);",
        lines(
            "/**",
            " * @constructor",
            " */",
            "var F = function() {",
            "};",
            "F.prototype.x;",
            ""));
  }

  public void testExportLocalPropertyInConstructor2() throws Exception {
    ignoreWarnings(NewTypeInference.INEXISTENT_PROPERTY);
    compileAndCheck(
        lines(
            "/** @constructor */function F() { /** @export */ this.x = 5;}",
            "goog.exportSymbol('F', F);",
            "goog.exportProperty(F.prototype, 'x', F.prototype.x);"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "var F = function() {",
            "};",
            "F.prototype.x;",
            ""));
  }

  public void testExportLocalPropertyInConstructor3() throws Exception {
    compileAndCheck(
        "/** @constructor */function F() { /** @export */ this.x;} goog.exportSymbol('F', F);",
        lines(
            "/**",
            " * @constructor",
            " */",
            "var F = function() {",
            "};",
            "F.prototype.x;",
            ""));
  }

  public void testExportLocalPropertyInConstructor4() throws Exception {
    compileAndCheck(
        lines(
            "/** @constructor */",
            "function F() { /** @export */ this.x = function(/** string */ x){};}",
            "goog.exportSymbol('F', F);"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "var F = function() {",
            "};",
            "/**",
            " * @param {string} x",
            " * @return {undefined}",
            " */",
            "F.prototype.x = function(x) {",
            "};",
            ""));
  }

  public void testExportLocalPropertyNotInConstructor() throws Exception {
    compileAndCheck(
        "/** @this {?} */ function f() { /** @export */ this.x = 5;} goog.exportSymbol('f', f);",
        lines(
            "/**",
            " * @return {undefined}",
            " */",
            "var f = function() {",
            "};",
            ""));
  }

  public void testExportParamWithSymbolDefinedInFunction() throws Exception {
    compileAndCheck(
        lines(
            "var id = function() {return /** @type {?} */ ('id')};",
            "var ft = function() {",
            "  var id;",
            "  return 1;",
            "};",
            "goog.exportSymbol('id', id);"),
        lines(
            "/**",
            " * @return {?}",
            " */",
            "var id = function() {",
            "};",
            ""));
  }

  public void testExportSymbolWithFunctionDefinedAsFunction() {

    compileAndCheck(
        lines(
            "/**",
            " * @param {string} param1",
            " * @return {string}",
            " */",
            "function internalName(param1) {",
            "  return param1",
            "};",
            "goog.exportSymbol('externalName', internalName)"),
        lines(
            "/**",
            " * @param {string} param1",
            " * @return {string}",
            " */",
            "var externalName = function(param1) {",
            "};",
            ""));
  }

  public void testExportSymbolWithFunctionAlias() {

    compileAndCheck(
        lines(
            "/**",
            " * @param {string} param1",
            " */",
            "var y = function(param1) {",
            "};",
            "/**",
            " * @param {string} param1",
            " * @param {string} param2",
            " */",
            "var x = function y(param1, param2) {",
            "};",
            "goog.exportSymbol('externalName', y)"),
        lines(
            "/**",
            " * @param {string} param1",
            " * @return {undefined}",
            " */",
            "var externalName = function(param1) {",
            "};",
            ""));
  }

  public void testNamespaceDefinitionInExterns() throws Exception {
    compileAndCheck(
        lines(
            "/** @const */",
            "var ns = {};",
            "/** @const */",
            "ns.subns = {};",
            "/** @constructor */",
            "ns.subns.Foo = function() {};",
            "goog.exportSymbol('ns.subns.Foo', ns.subns.Foo);"),
        lines(
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

  public void testNullabilityInFunctionTypes() throws Exception {
    compileAndCheck(
        lines(
            "/**",
            " * @param {function(Object)} takesNullable",
            " * @param {function(!Object)} takesNonNullable",
            " */",
            "function x(takesNullable, takesNonNullable) {}",
            "goog.exportSymbol('x', x);"),
        lines(
            "/**",
            " * @param {function((Object|null)): ?} takesNullable",
            " * @param {function(!Object): ?} takesNonNullable",
            " * @return {undefined}",
            " */",
            "var x = function(takesNullable, takesNonNullable) {",
            "};",
            ""));
  }

  public void testNullabilityInRecordTypes() throws Exception {
    compileAndCheck(
        lines(
            "/** @typedef {{ nonNullable: !Object, nullable: Object }} */",
            "var foo;",
            "/** @param {foo} record */",
            "function x(record) {}",
            "goog.exportSymbol('x', x);"),
        lines(
            "/**",
            " * @param {{nonNullable: !Object, nullable: (Object|null)}} record",
            " * @return {undefined}",
            " */",
            "var x = function(record) {",
            "};",
            ""));
  }

  private void compileAndCheck(String js, final String expected) {
    compileAndExportExterns(js, MINIMAL_EXTERNS, new Consumer<String>() {
      @Override public void accept(String generatedExterns) {
        String fileoverview = lines(
            "/**",
            " * @fileoverview Generated externs.",
            " * @externs",
            " */",
            "");
        // NOTE(sdh): NTI produces {?=} for many params, while OTI just produces {?}.
        // For now we will not worry about this distinction and just normalize it.
        generatedExterns = generatedExterns.replace("?=", "?");

        assertThat(generatedExterns).isEqualTo(fileoverview + expected);
      }
    });
  }

  public void testDontWarnOnExportFunctionWithUnknownParameterTypes() {
    /* This source is missing types for the b and c parameters */
    String librarySource = lines(
        "/**",
        " * @param {number} a",
        " * @return {number}",
        " */",
        "var InternalName = function(a,b,c) {",
        "  return 6;",
        "};",
        "goog.exportSymbol('ExternalName', InternalName)");

    compileAndExportExterns(librarySource);
  }

  /**
   * Compiles the passed in JavaScript and returns the new externs exported by the this pass.
   *
   * @param js the source to be compiled
   */
  private void compileAndExportExterns(String js) {
    compileAndExportExterns(js, MINIMAL_EXTERNS);
  }

  /**
   * Compiles the passed in JavaScript with the passed in externs and returns
   * the new externs exported by the this pass.
   *
   * @param js the source to be compiled
   * @param externs the externs the {@code js} source needs
   */
  private void compileAndExportExterns(String js, String externs) {
    compileAndExportExterns(js, externs, null);
  }

  /**
   * Compiles the passed in JavaScript with the passed in externs and returns
   * the new externs exported by the this pass.
   *
   * @param js the source to be compiled
   * @param externs the externs the {@code js} source needs
   * @param consumer consumer for the externs generated from {@code js}
   */
  private void compileAndExportExterns(String js, String externs, final Consumer<String> consumer) {
    js = lines(
        "/** @const */ var goog = {};",
        "goog.exportSymbol = function(a, b) {};",
        "goog.exportProperty = function(a, b, c) {};",
        js);

    testSame(externs(externs), srcs(js), (Postcondition) (Compiler compiler) -> {
      if (consumer != null) {
        consumer.accept(compiler.getResult().externExport);
      }
    });
  }
}
