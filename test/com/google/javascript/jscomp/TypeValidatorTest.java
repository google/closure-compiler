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
import com.google.common.truth.Correspondence;
import com.google.common.truth.IterableSubject;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Type-checking tests that can use methods from CompilerTestCase
 *
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

  @Test
  public void testBasicMismatch() {
    testWarning("/** @param {number} x */ function f(x) {} f('a');", TYPE_MISMATCH_WARNING);
    this.assertThatRecordedMismatches()
        .comparingElementsUsing(HAVE_SAME_TYPES)
        .containsExactly(fromNatives(STRING_TYPE, NUMBER_TYPE));
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

    this.assertThatRecordedMismatches()
        .comparingElementsUsing(HAVE_SAME_TYPES)
        .containsExactly(
            TypeMismatch.createForTesting(firstFunction, secondFunction),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE),
            fromNatives(NUMBER_TYPE, STRING_TYPE));
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

    this.assertThatRecordedMismatches()
        .comparingElementsUsing(HAVE_SAME_TYPES)
        .containsExactly(
            TypeMismatch.createForTesting(firstFunction, secondFunction),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE));
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

  @Test
  public void missingPropertiesPrintedWithMismatch_defaultParam() {
    test(
        externs(""),
        srcs(
            lines(
                "/** @unrestricted */",
                "class NumberFormatSymbolsMap {",
                "  constructor() {}",
                "}",
                "/** @type {string} */",
                "NumberFormatSymbolsMap.prototype.DEF_CURRENCY_CODE;",
                "",
                "var /** !NumberFormatSymbolsMap */ numberSymbol;",
                "/**",
                " * @record",
                " */",
                "const Type = class {",
                "  constructor() {",
                "    /** @type {string} */ this.CURRENCY_PATTERN;",
                "    /** @type {string} */ this.DEF_CURRENCY_CODE;",
                "  }",
                "};",
                "/**",
                " * @param {!Type=} symbols",
                " * @constructor",
                " */",
                "let NumberFormat = function(symbols) {};",
                "new NumberFormat(numberSymbol);")),
        warning(TYPE_MISMATCH_WARNING).withMessageContaining("missing : [CURRENCY_PATTERN]"));
  }

  @Test
  public void missingPropertiesPrintedWithMismatch_nullableParam() {
    test(
        externs(""),
        srcs(
            lines(
                "/** @unrestricted */",
                "class NumberFormatSymbolsMap {",
                "  constructor() {}",
                "}",
                "/** @type {string} */",
                "NumberFormatSymbolsMap.prototype.DEF_CURRENCY_CODE;",
                "",
                "var /** !NumberFormatSymbolsMap */ numberSymbol;",
                "/**",
                " * @record",
                " */",
                "const Type = class {",
                "  constructor() {",
                "    /** @type {string} */ this.CURRENCY_PATTERN;",
                "    /** @type {string} */ this.DEF_CURRENCY_CODE;",
                "  }",
                "};",
                "/**",
                " * @param {?Type} symbols",
                " * @constructor",
                " */",
                "let NumberFormat = function(symbols) {};",
                "new NumberFormat(numberSymbol);")),
        warning(TYPE_MISMATCH_WARNING).withMessageContaining("missing : [CURRENCY_PATTERN]"));
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
  public void bug_testMismatchRecursively_throughFields() {
    testWarning(
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */ this.x;",
            "  }",
            "}",
            "",
            "function f(/** {x: string} */ a) {",
            "  const /** !Foo */ b = a;",
            "}"),
        TYPE_MISMATCH_WARNING);

    // TODO(b/148169932): There should be another mismatch {found: string, required: number}.
    this.assertThatRecordedMismatches().hasSize(1);
  }

  @Test
  public void bug_testMismatchRecursively_throughTemplates() {
    testWarning(
        lines(
            "/**",
            " * @interface",
            " * @template T",
            " */",
            "class Foo { }",
            "",
            "/** @implements {Foo<string>} */",
            "class Bar { }",
            "",
            "function f(/** !Bar */ a) {",
            "  const /** !Foo<number> */ b = a;",
            "}"),
        TYPE_MISMATCH_WARNING);

    // TODO(b/148169932): There should be an another mismatch {found: string, required: number}.
    this.assertThatRecordedMismatches().isEmpty();
  }

  @Test
  public void testNullUndefined() {
    testWarning(
        "/** @param {string} x */ function f(x) {}\n"
            + "f(/** @type {string|null|undefined} */ ('a'));",
        TYPE_MISMATCH_WARNING);
    this.assertThatRecordedMismatches().isEmpty();
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
    this.assertThatRecordedMismatches().isEmpty();
  }

  @Test
  public void testUnionsMismatch() {
    testWarning(
        "/** @param {number|string} x */\n"
            + "function f(x) {}\n"
            + "f(/** @type {boolean|string} */ ('a'));",
        TYPE_MISMATCH_WARNING);
    this.assertThatRecordedMismatches().isEmpty();
  }

  @Test
  public void testModuloNullUndef1() {
    testSame(
        srcs(
            SourceFile.fromCode(
                "foo.java.js",
                lines(
                    "function f(/** number */ to, /** (number|null) */ from) {",
                    "  to = from;",
                    "}"))));
  }

  @Test
  public void testModuloNullUndef2() {
    testSame(
        srcs(
            SourceFile.fromCode(
                "foo.java.js",
                lines(
                    "function f(/** number */ to, /** (number|undefined) */ from) {",
                    "  to = from;",
                    "}"))));
  }

  @Test
  public void testModuloNullUndef3() {
    testSame(
        srcs(
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
    testSame(
        srcs(
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
    testSame(
        srcs(
            SourceFile.fromCode(
                "foo.java.js",
                lines(
                    "function f(/** {a: number} */ to, /** {a: (null|number)} */ from) {",
                    "  to = from;",
                    "}"))));
  }

  @Test
  public void testModuloNullUndef6() {
    testSame(
        srcs(
            SourceFile.fromCode(
                "foo.java.js",
                lines(
                    "function f(/** {a: number} */ to, /** ?{a: (null|number)} */ from) {",
                    "  to = from;",
                    "}"))));
  }

  @Test
  public void testModuloNullUndef7() {
    testSame(
        srcs(
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
    testSame(
        srcs(
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
    testSame(
        srcs(
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
    testSame(
        srcs(
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
    testSame(
        srcs(
            SourceFile.fromCode(
                "foo.java.js",
                lines("function f(/** number */ n) {}", "f(/** @type {?number} */ (null));"))));
  }

  @Test
  public void testModuloNullUndef12() {
    // Only warn for the file not ending in .java.js
    testWarning(
        srcs(
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
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testModuloNullUndef13() {
    testSame(srcs(SourceFile.fromCode("foo.java.js", "var /** @type {{ a:number }} */ x = null;")));
  }

  @Test
  public void testInheritanceModuloNullUndef1() {
    testSame(
        srcs(
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
    testSame(
        srcs(
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
    testSame(
        srcs(
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
  public void testOptionalProperties_dontNeedInitializer_inConstructor() {
    testSame(
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @type {boolean|undefined} */",
            "Foo.prototype.prop;",
            "",
            "/** @constructor @implements {Foo} */",
            "function Bar() {",
            // No initializer here.
            "}"));
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
    return TypeMismatch.createForTesting(registry.getNativeType(a), registry.getNativeType(b));
  }

  private IterableSubject assertThatRecordedMismatches() {
    return assertThat(getLastCompiler().getTypeMismatches());
  }

  private static final Correspondence<TypeMismatch, TypeMismatch> HAVE_SAME_TYPES =
      Correspondence.transforming(
          (x) -> ImmutableList.of(x.getFound(), x.getRequired()),
          (x) -> ImmutableList.of(x.getFound(), x.getRequired()),
          "has same types as");
}
