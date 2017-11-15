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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class Es6ExtractClassesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6ExtractClasses(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  public void testExtractionFromCall() {
    test(
        "f(class{});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "f(testcode$classdecl$var0);"));
  }

  public void testSelfReference1() {
    test(
        "var Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "var Outer=testcode$classdecl$var0"));

    test(
        "let Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "let Outer=testcode$classdecl$var0"));

    test(
        "const Outer = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "const Outer=testcode$classdecl$var0"));
  }

  public void testSelfReference2() {
    test(
        "alert(class C { constructor() { alert(C); } });",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() { alert(testcode$classdecl$var0); }",
            "};",
            "alert(testcode$classdecl$var0)"));
  }

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

  public void testSelfReference_googModule() {
    test(
        lines(
            "goog.module('example');",
            "exports = class Inner { constructor() { alert(Inner); } };"),
        lines(
            "goog.module('example');",
            "const testcode$classdecl$var0 = class {",
            "  constructor() {",
            "    alert(testcode$classdecl$var0);",
            "  }",
            "};",
            "exports = testcode$classdecl$var0;"));
  }

  public void testSelfReference_qualifiedName() {
    test(
        "outer.qual.Name = class Inner { constructor() { alert(Inner); } };",
        lines(
            "const testcode$classdecl$var0 = class {",
            "  constructor() {",
            "    alert(testcode$classdecl$var0);",
            "  }",
            "};",
            "outer.qual.Name = testcode$classdecl$var0;"));
  }

  public void testConstAssignment() {
    test(
        "var foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  public void testLetAssignment() {
    test(
        "let foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "let foo = bar(testcode$classdecl$var0);"));
  }

  public void testVarAssignment() {
    test(
        "var foo = bar(class {});",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  public void testJSDoc() {
    test(
        "/** @unrestricted */ var foo = class bar {};",
        lines(
            "/** @unrestricted */",
            "const testcode$classdecl$var0 = class {};",
            "/** @unrestricted */",
            "var foo = testcode$classdecl$var0;"));
  }

  public void testFilenameContainsAt() {
    test(
        ImmutableList.of(
            SourceFile.fromCode("unusual@name", "alert(class {});")),
        ImmutableList.of(
            SourceFile.fromCode(
                "unusual@name",
                lines(
                    "const unusual$name$classdecl$var0 = class{};",
                    "alert(unusual$name$classdecl$var0);"))));
  }

  public void testFilenameContainsPlus() {
    test(
        ImmutableList.of(
            SourceFile.fromCode("+some/+path/file", "alert(class {});")),
        ImmutableList.of(
            SourceFile.fromCode(
                "+path/file",
                lines(
                    "const $some$$path$file$classdecl$var0 = class{};",
                    "alert($some$$path$file$classdecl$var0);"))));

  }

  public void testConditionalBlocksExtractionFromCall() {
    testError("maybeTrue() && f(class{});", CANNOT_CONVERT);
  }

  public void testExtractionFromArrayLiteral() {
    test(
        "var c = [class C {}];",
        lines(
            "const testcode$classdecl$var0 = class {};",
            "var c = [testcode$classdecl$var0];"));
  }

  public void testTernaryOperatorBlocksExtraction() {
    testError("var c = maybeTrue() ? class A {} : anotherExpr", CANNOT_CONVERT);
    testError("var c = maybeTrue() ? anotherExpr : class B {}", CANNOT_CONVERT);
  }

  public void testCannotExtract() {
    testError(
        "var c = maybeTrue() && class A extends sideEffect() {}",
        CANNOT_CONVERT);

    testError(
        lines(
            "var x;",
            "function f(x, y) {}",

            "f(x = 2, class Foo { [x=3]() {} });"),
        CANNOT_CONVERT);
  }

  public void testClassesHandledByEs6ToEs3Converter() {
    testSame("class C{}");
    testSame("var c = class {};");
  }
}
