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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ExternExportsPass}.
 *
 */
@RunWith(JUnit4.class)
public final class ExternExportsPassTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableTypeCheck();
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

  @Test
  public void testExportSymbol() {
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

  @Test
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

  @Test
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

  @Test
  public void testExportSymbolDefinedInVar() {
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

  @Test
  public void testExportProperty() {
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

  @Test
  public void testExportMultiple() {
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

  @Test
  public void testExportMultiple2() {
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

  @Test
  public void testExportMultiple3() {
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

  @Test
  public void testExportNonStaticSymbol() {
    compileAndCheck(
        lines(
            "/** @const */ var a = {};",
            "/** @const */ a.b = {};",
            "/** @const */ var d = {};",
            "a.b.c = d;",
            "goog.exportSymbol('foobar', a.b.c)"),
        "var foobar;\n");
  }

  @Test
  public void testExportNonStaticSymbol2() {
    compileAndCheck(
        lines(
            "/** @const */ var a = {};",
            "/** @const */ a.b = {};",
            "var d = function() {};",
            "a.b.c = d;",
            "goog.exportSymbol('foobar', a.b.c())"),
        "var foobar;\n");
  }

  @Test
  public void testExportNonexistentProperty() {
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testExportSymbolWithoutTypeCheck() {
    // ExternExportsPass should not emit annotations
    // if there is no type information available.
    disableTypeCheck();

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

  @Test
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

  @Test
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

  @Test
  public void testExportSymbolWithConstructorWithoutTypeCheck() {
    // For now, skipping type checking should prevent generating
    // annotations of any kind, so, e.g., @constructor is not preserved.
    // This is probably not ideal, but since JSDocInfo for functions is attached
    // to JSTypes and not Nodes (and no JSTypes are created when checkTypes
    // is false), we don't really have a choice.

    disableTypeCheck();

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
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
  @Test
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

  @Test
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

    // NOTE: The type should print {E} for the @param, but is not.
    compileAndCheck(js, expected.replace("{E}", "{number}"));
  }

  /**
   * If we export a property with "prototype" as a path component, there is no need to emit the
   * initializer for prototype because every namespace has one automatically.
   */
  @Test
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
   * Test the workflow of creating an externs file for a library via the export pass and then using
   * that externs file in a client.
   *
   * <p>There should be no warnings in the client if the library includes type information for the
   * exported functions and the client uses them correctly.
   */
  @Test
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

    compileAndExportExterns(
        librarySource,
        MINIMAL_EXTERNS,
        generatedExterns ->
            compileAndExportExterns(clientSource, MINIMAL_EXTERNS + generatedExterns));
  }

  @Test
  public void testDontWarnOnExportFunctionWithUnknownReturnType() {
    String librarySource = lines(
        "var InternalName = function() {",
        "  return 6;",
        "};",
        "goog.exportSymbol('ExternalName', InternalName)");

    compileAndExportExterns(librarySource);
  }

  @Test
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

  @Test
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

  @Test
  public void testExportParamWithNull() {
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

  @Test
  public void testExportConstructor() {
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

  @Test
  public void testExportLocalPropertyInConstructor() {
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

  @Test
  public void testExportLocalPropertyInConstructor2() {
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

  @Test
  public void testExportLocalPropertyInConstructor3() {
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

  @Test
  public void testExportLocalPropertyInConstructor4() {
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

  @Test
  public void testExportLocalPropertyNotInConstructor() {
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

  @Test
  public void testExportParamWithSymbolDefinedInFunction() {
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

  @Test
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

  @Test
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

  @Test
  public void testNamespaceDefinitionInExterns() {
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

  @Test
  public void testNullabilityInFunctionTypes() {
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

  @Test
  public void testNullabilityInRecordTypes() {
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
    compileAndExportExterns(
        js,
        MINIMAL_EXTERNS,
        generatedExterns -> {
          String fileoverview =
              lines("/**", " * @fileoverview Generated externs.", " * @externs", " */", "");
          // NOTE(sdh): The type checker just produces {?}.
          // For now we will not worry about this distinction and just normalize it.
          generatedExterns = generatedExterns.replace("?=", "?");

          assertThat(generatedExterns).isEqualTo(fileoverview + expected);
        });
  }

  @Test
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

    testSame(
        externs(externs),
        srcs(js),
        (Postcondition)
            compiler -> {
              if (consumer != null) {
                consumer.accept(compiler.getResult().externExport);
              }
            });
  }
}
