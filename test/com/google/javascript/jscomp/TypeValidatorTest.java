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
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Type-checking tests that can use methods from CompilerTestCase
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class TypeValidatorTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node n) {
        // Do nothing: we're in it for the type-checking.
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testBasicMismatch() {
    testWarning("/** @param {number} x */ function f(x) {} f('a');", TYPE_MISMATCH_WARNING);
    assertMismatches(ImmutableList.of(fromNatives(STRING_TYPE, NUMBER_TYPE)));
  }

  @Test
  public void testFunctionMismatch() {
    testWarning(
        "/** \n"
            + " * @param {function(string): number} x \n"
            + " * @return {function(boolean): string} \n"
            + " */ function f(x) { return x; }",
        TYPE_MISMATCH_WARNING);

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    JSType string = registry.getNativeType(STRING_TYPE);
    JSType bool = registry.getNativeType(BOOLEAN_TYPE);
    JSType number = registry.getNativeType(NUMBER_TYPE);
    JSType firstFunction = registry.createFunctionType(number, string);
    JSType secondFunction = registry.createFunctionType(string, bool);

    assertMismatches(
        ImmutableList.of(
            new TypeMismatch(firstFunction, secondFunction, null),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE),
            fromNatives(NUMBER_TYPE, STRING_TYPE)));
  }

  @Test
  public void testFunctionMismatch2() {
    testWarning(
        "/** \n"
            + " * @param {function(string): number} x \n"
            + " * @return {function(boolean): number} \n"
            + " */ function f(x) { return x; }",
        TYPE_MISMATCH_WARNING);

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    JSType string = registry.getNativeType(STRING_TYPE);
    JSType bool = registry.getNativeType(BOOLEAN_TYPE);
    JSType number = registry.getNativeType(NUMBER_TYPE);
    JSType firstFunction = registry.createFunctionType(number, string);
    JSType secondFunction = registry.createFunctionType(number, bool);

    assertMismatches(
        ImmutableList.of(
            new TypeMismatch(firstFunction, secondFunction, null),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE)));
  }

  @Test
  public void testFunctionMismatchMediumLengthTypes() {
    test(
        externs(""),
        srcs(
            lines(
                "/**",
                " * @param {{a: string, b: string, c: string, d: string, e: string}} x",
                " */",
                "function f(x) {}",
                "var y = {a:'',b:'',c:'',d:'',e:0};",
                "f(y);")),
        warning(TYPE_MISMATCH_WARNING)
            .withMessage(
                lines(
                    "actual parameter 1 of f does not match formal parameter",
                    "found   : {",
                    "  a: string,",
                    "  b: string,",
                    "  c: string,",
                    "  d: string,",
                    "  e: (number|string)",
                    "}",
                    "required: {",
                    "  a: string,",
                    "  b: string,",
                    "  c: string,",
                    "  d: string,",
                    "  e: string",
                    "}",
                    "missing : []",
                    "mismatch: [e]")));
  }

  /**
   * Make sure the 'found' and 'required' strings are not identical when there is a mismatch. See
   * https://code.google.com/p/closure-compiler/issues/detail?id=719.
   */
  @Test
  public void testFunctionMismatchLongTypes() {
    test(
        externs(""),
        srcs(
            lines(
                "/**",
                " * @param {{a: string, b: string, c: string, d: string, e: string,",
                " *          f: string, g: string, h: string, i: string, j: string, k: string}} x",
                " */",
                "function f(x) {}",
                "var y = {a:'',b:'',c:'',d:'',e:'',f:'',g:'',h:'',i:'',j:'',k:0};",
                "f(y);")),
        warning(TYPE_MISMATCH_WARNING)
            .withMessage(
                lines(
                    "actual parameter 1 of f does not match formal parameter",
                    "found   : {a: string, b: string, c: string, d: string, e: string, f: string,"
                        + " g: string, h: string, i: string, j: string, k: (number|string)}",
                    "required: {a: string, b: string, c: string, d: string, e: string, f: string,"
                        + " g: string, h: string, i: string, j: string, k: string}",
                    "missing : []",
                    "mismatch: [k]")));
  }

  /** Same as testFunctionMismatchLongTypes, but with one of the types being a typedef. */
  @Test
  public void testFunctionMismatchTypedef() {
    test(
        externs(""),
        srcs(lines(
            "/**",
            " * @typedef {{a: string, b: string, c: string, d: string, e: string,",
            " *            f: string, g: string, h: string, i: string, j: string, k: string}} x",
            " */",
            "var t;",
            "/**",
            " * @param {t} x",
            " */",
            "function f(x) {}",
            "var y = {a:'',b:'',c:'',d:'',e:'',f:'',g:'',h:'',i:'',j:'',k:0};",
            "f(y);")),
        warning(TYPE_MISMATCH_WARNING)
            .withMessage(
                lines(
                    "actual parameter 1 of f does not match formal parameter",
                    "found   : {a: string, b: string, c: string, d: string, e: string, f: string,"
                        + " g: string, h: string, i: string, j: string, k: (number|string)}",
                    "required: {a: string, b: string, c: string, d: string, e: string, f: string,"
                        + " g: string, h: string, i: string, j: string, k: string}",
                    "missing : []",
                    "mismatch: [k]")));
  }

  @Test
  public void testNullUndefined() {
    testWarning(
        "/** @param {string} x */ function f(x) {}\n"
            + "f(/** @type {string|null|undefined} */ ('a'));",
        TYPE_MISMATCH_WARNING);
    assertMismatches(ImmutableList.of());
  }

  @Test
  public void testSubclass() {
    testWarning(
        "/** @constructor */\n"
            + "function Super() {}\n"
            + "/**\n"
            + " * @constructor\n"
            + " * @extends {Super}\n"
            + " */\n"
            + "function Sub() {}\n"
            + "/** @param {Sub} x */ function f(x) {}\n"
            + "f(/** @type {Super} */ (new Sub));",
        TYPE_MISMATCH_WARNING);
    assertMismatches(ImmutableList.of());
  }

  @Test
  public void testModuloNullUndef1() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function f(/** number */ to, /** (number|null) */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef2() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function f(/** number */ to, /** (number|undefined) */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef3() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "function f(/** !Foo */ to, /** ?Foo */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef4() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "/** @constructor @extends {Foo} */",
                "function Bar() {}",
                "function f(/** !Foo */ to, /** ?Bar */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef5() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function f(/** {a: number} */ to, /** {a: (null|number)} */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef6() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function f(/** {a: number} */ to, /** ?{a: (null|number)} */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef7() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "function f(/** function():!Foo */ to, /** function():?Foo */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef8() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/**",
                " * @constructor",
                " * @template T",
                " */",
                "function Foo() {}",
                "function f(/** !Foo<number> */ to, /** !Foo<(number|null)> */ from) {",
                "  to = from;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef9() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @interface */",
                "function Foo() {}",
                "/** @type {function(?number)} */",
                "Foo.prototype.prop;",
                "/** @constructor @implements {Foo} */",
                "function Bar() {}",
                "/** @type {function(number)} */",
                "Bar.prototype.prop;"))));
  }

  @Test
  public void testModuloNullUndef10() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @constructor */",
                "function Bar() {}",
                "/** @type {!number} */",
                "Bar.prototype.prop;",
                "function f(/** ?number*/ n) {",
                "  (new Bar).prop = n;",
                "}"))));
  }

  @Test
  public void testModuloNullUndef11() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function f(/** number */ n) {}",
                "f(/** @type {?number} */ (null));"))));
  }

  @Test
  public void testModuloNullUndef12() {
    // Only warn for the file not ending in .java.js
    testWarning(ImmutableList.of(
        SourceFile.fromCode(
            "foo.js",
            lines(
                "function f(/** number */ to, /** (number|null) */ from) {",
                "  to = from;",
                "}")),
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "function g(/** number */ to, /** (number|null) */ from) {",
                "  to = from;",
                "}"))),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testModuloNullUndef13() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            "var /** @type {{ a:number }} */ x = null;")));
  }

  @Test
  public void testInheritanceModuloNullUndef1() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "/** @return {string} */",
                "Foo.prototype.toString = function() { return ''; };",
                "/** @constructor @extends {Foo} */",
                "function Bar() {}",
                "/**",
                " * @override",
                " * @return {?string}",
                " */",
                "Bar.prototype.toString = function() { return null; };"))));
  }

  @Test
  public void testInheritanceModuloNullUndef2() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @interface */",
                "function Foo() {}",
                "/** @return {string} */",
                "Foo.prototype.toString = function() {};",
                "/** @constructor @implements {Foo} */",
                "function Bar() {}",
                "/**",
                " * @override",
                " * @return {?string}",
                " */",
                "Bar.prototype.toString = function() {};"))));
  }

  @Test
  public void testInheritanceModuloNullUndef3() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.java.js",
            lines(
                "/** @interface */",
                "function High1() {}",
                "/** @type {number} */",
                "High1.prototype.prop;",
                "/** @interface */",
                "function High2() {}",
                "/** @type {?number} */",
                "High2.prototype.prop;",
                "/**",
                " * @interface",
                " * @extends {High1}",
                " * @extends {High2}",
                " */",
                "function Low() {}"))));
  }

  @Test
  public void testDuplicateSuppression() {
    testWarning(
        lines(
            "/** @const */",
            "var ns2 = {};",
            "/** @type {number} */",
            "ns2.x = 3;",
            "/** @type {number} */",
            "ns2.x = 3;"),
        TypeValidator.DUP_VAR_DECLARATION);
    testWarning(
        lines(
            "/** @const */",
            "var ns2 = {};",
            "/** @type {number|string} */", // so that the second assignment is accepted.
            "ns2.x = 3;",
            "/** @type {string} */",
            "ns2.x = 'a';"),
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH);

    // catch variables in different catch blocks are not duplicate declarations
    testSame(
        lines(
            "try { throw 1; } catch (/** @type {number} */ err) {}",
            "try { throw 1; } catch (/** @type {number} */ err) {}"
            ));

    // duplicates suppressed on 1st declaration.
    testSame(
        lines(
            "/** @const */",
            "var ns1 = {};",
            "/** @type {number} @suppress {duplicate} */",
            "ns1.x = 3;",
            "/** @type {number} */",
            "ns1.x = 3;"));

    // duplicates suppressed on 2nd declaration.
    testSame(
        lines(
            "/** @const */",
            "var ns1 = {};",
            "/** @type {number} */",
            "ns1.x = 3;",
            "/** @type {number} @suppress {duplicate} */",
            "ns1.x = 3;"));

    // duplicates suppressed on file level.
    testSame(
        lines(
            "/** @fileoverview @suppress {duplicate} */",
            "/** @const */",
            "var ns1 = {};",
            "/** @type {number} */",
            "ns1.x = 3;",
            "/** @type {number} */",
            "ns1.x = 3;"));
  }

  @Test
  public void testDuplicateSuppression_class() {
    enableTranspile();
    testWarning(
        lines(
            "class X { constructor() {} }", //
            "function X() {}"),
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH);
    testSame(
        lines(
            "/** @suppress {duplicate} */",
            "class X { constructor() {} }", //
            "function X() {}"));
  }

  @Test
  public void testDuplicateSuppression_typeMismatch() {
    // duplicate diagnostic category includes type mismatches.
    testSame(
        lines(
            "/** @const */",
            "var ns1 = {};",
            "/** @type {number} */",
            "ns1.x = 3;",
            "/** @type {string} @suppress {duplicate} */",
            "ns1.x = 3;"));
    testSame(
        lines(
            "/** @const */",
            "var ns1 = {};",
            "/** @type {number} @suppress {duplicate} */",
            "ns1.x = 3;",
            "/** @type {string} */",
            "ns1.x = 3;"));
  }

  @Test
  public void testDuplicateSuppression_stubs() {
    // No duplicate warning because the first declaration is a stub declaration (property access)
    testSame(
        lines(
            "/** @const */",
            "var ns0 = {};",
            "/** @type {number} */",
            "ns0.x;",
            "/** @type {number} */",
            "ns0.x;"));

    // Type mismatch on stub.
    testWarning(
        lines(
            "/** @const */",
            "var ns3 = {};",
            "/** @type {number} */",
            "ns3.x;",
            "/** @type {string} */",
            "ns3.x;"),
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH);
  }

  @Test
  public void testDuplicateSuppression_topLevelVariables() {
    testWarning(
        lines("/** @type {number} */", "var w;", "/** @type {number} */", "var w;"),
        TypeValidator.DUP_VAR_DECLARATION);

    testWarning(
        lines("/** @type {number} */", "var y = 3;", "/** @type {string} */", "var y = 3;"),
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH);

    // @suppress on file level.
    testSame(
        lines(
            "/** @fileoverview  @suppress {duplicate} */",
            "/** @type {number} */",
            "var x;",
            "/** @type {number} */",
            "var x;"));

    // @suppress on variable declaration.
    testSame(
        lines(
            "/** @type {number} */",
            "var z;",
            "/** @type {number} @suppress {duplicate} */",
            "var z;"));
  }

  @Test
  public void testDuplicateSuppression_topLevelFunctions() {
    testWarning(
        lines(
            "/** @return {number} */",
            "function f() {}",
            "/** @return {number} */",
            "function f() {}"),
        TypeValidator.DUP_VAR_DECLARATION);

    testWarning(
        lines(
            "/** @return {number} */",
            "function f() {}",
            "/** @return {string} */",
            "function f() {}"),
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH);

    testSame(
        lines(
            "/** @return {number} */",
            "function f() {}",
            "/** @return {string} @suppress {duplicate} */",
            "function f() {}"));

    testSame(
        lines(
            "/** @return {number} */",
            "function f() {}",
            "/** @return {string} @suppress {duplicate} */",
            "function f() {}"));
  }

  private TypeMismatch fromNatives(JSTypeNative a, JSTypeNative b) {
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    return new TypeMismatch(
        registry.getNativeType(a), registry.getNativeType(b), null);
  }

  private void assertMismatches(List<TypeMismatch> expected) {
    List<TypeMismatch> actual = ImmutableList.copyOf(getLastCompiler().getTypeMismatches());
    assertThat(actual).isEqualTo(expected);
  }
}
