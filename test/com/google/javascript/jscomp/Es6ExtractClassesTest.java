/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6ExtractClassesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6ExtractClasses(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);

    enableTypeInfoValidation();
    enableRewriteClosureCode();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Test
  public void testExtractionFromCall() {
    test(
        "f(class{});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "f(testcode$classdecl$var0);"));
  }

  @Test
  public void testSelfReference1() {
    test(
        "var Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "/** @constructor */",
            "var Outer=testcode$classdecl$var0"));

    test(
        "let Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "/** @constructor */",
            "let Outer=testcode$classdecl$var0"));

    test(
        "const Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "/** @constructor */",
            "const Outer=testcode$classdecl$var0"));
  }

  @Test
  public void testSelfReference2() {
    test(
        "alert(class C { constructor() { alert(C); } });",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "alert(testcode$classdecl$var0)"));
  }

  @Test
  public void testSelfReference3() {
    test(
        lines(
            "alert(class C {",
            "  m1() { class C {}; alert(C); }",
            "  m2() { alert(C); }",
            "});"),
        lines(
            "const testcode$classdecl$var0 = class {",
            "  m1() { class C {}; alert(C); }",
            "  m2() { alert(testcode$classdecl$var0); }",
            "};",
            "alert(testcode$classdecl$var0)"));
  }

  @Test
  public void testSelfReference_googModule() {
    test(
        lines(
            "goog.module('example');",
            "exports = class Inner { constructor() { alert(Inner); } };"),
        lines(
            "/** @const */ const testcode$classdecl$var0=class {",
            "  constructor(){ alert(testcode$classdecl$var0); }",
            "};",
            "/**",
            " * @constructor",
            " * @const",
            " */ ",
            "var module$exports$example=testcode$classdecl$var0"));
  }

  @Test
  public void testSelfReference_qualifiedName() {
    test(
        lines(
            "const outer = {};",
            "/** @const */ outer.qual = {};",
            "outer.qual.Name = class Inner { constructor() { alert(Inner); } };"),
        lines(
            "const outer = {};",
            "/** @const */ outer.qual = {};",
            "const testcode$classdecl$var0 = class {",
            "  constructor() {",
            "    alert(testcode$classdecl$var0);",
            "  }",
            "};",
            "/** @constructor */",
            "outer.qual.Name = testcode$classdecl$var0;"));
  }

  @Test
  public void testConstAssignment() {
    test(
        "var foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  @Test
  public void testLetAssignment() {
    test(
        "let foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "let foo = bar(testcode$classdecl$var0);"));
  }

  @Test
  public void testVarAssignment() {
    test(
        "var foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  @Test
  public void testJSDocOnVar() {
    test(
        "/** @unrestricted */ var foo = class bar {};",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "/** @constructor */",
            "var foo = testcode$classdecl$var0;"));
  }

  @Test
  public void testFilenameContainsAt() {
    test(
        srcs(SourceFile.fromCode("unusual@name", "alert(class {});")),
        expected(
            SourceFile.fromCode(
                "unusual@name",
                lines(
                    "const unusual$name$classdecl$var0 = class{};",
                    "alert(unusual$name$classdecl$var0);"))));
  }

  @Test
  public void testFilenameContainsPlus() {
    test(
        srcs(SourceFile.fromCode("+some/+path/file", "alert(class {});")),
        expected(
            SourceFile.fromCode(
                "+path/file",
                lines(
                    "const $some$$path$file$classdecl$var0 = class{};",
                    "alert($some$$path$file$classdecl$var0);"))));
  }

  @Test
  public void testConditionalBlocksExtractionFromCall() {
    test(
        "maybeTrue() && f(class{});",
        lines(
            "if (maybeTrue()) {",
            "  const testcode$classdecl$var0=class{};",
            "  f(testcode$classdecl$var0);",
            "}"));
  }

  @Test
  public void testExtractionFromArrayLiteral() {
    test(
        "var c = [class C {}];",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var c = [testcode$classdecl$var0];"));
  }

  @Test
  public void testTernaryOperatorBlocksExtraction() {
    test(
        "var c = maybeTrue() ? class A {} : anotherExpr",
        lines(
            "var JSCompiler_temp$jscomp$0;",
            "if (maybeTrue()) {",
            "  const testcode$classdecl$var0=class{};",
            "  /** @constructor */ JSCompiler_temp$jscomp$0 = testcode$classdecl$var0;",
            "} else {",
            "  JSCompiler_temp$jscomp$0 = anotherExpr;",
            "}",
            "var c = JSCompiler_temp$jscomp$0;"));
    test(
        "var c = maybeTrue() ? anotherExpr : class B {}",
        lines(
            "var JSCompiler_temp$jscomp$0;",
            "if (maybeTrue()) { ",
            "  JSCompiler_temp$jscomp$0=anotherExpr; ",
            "} else {",
            "  const testcode$classdecl$var0 = class{};",
            "  /** @constructor */ JSCompiler_temp$jscomp$0 = testcode$classdecl$var0",
            "}",
            "var c = JSCompiler_temp$jscomp$0;"));
  }

  @Test
  public void testCannotExtract() {
    testError("let x; while(x = class A{}) {use(x);}", CANNOT_CONVERT);

    testError(
        lines(
            "/** @type {number} */ var x = 0;",
            "function f(x, y) {}",
            "while(f(x = 2, class Foo { [x=3]() {} })){}"),
        CANNOT_CONVERT);
  }

  @Test
  public void testClassesHandledByEs6ToEs3Converter() {
    testSame("class C{}");
    testSame("var c = class {};");
  }
}
