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

import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class Es6ExtractClassesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6ExtractClasses(compiler);
  }

  @Override
  protected void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    disableTypeCheck();
    runTypeCheckAfterProcessing = true;
  }

  public void testExtractionFromCall() {
    test(
        "f(class{});",
        LINE_JOINER.join(
            "const testcode$classdecl$var0 = class {};", "f(testcode$classdecl$var0);"));
  }

  public void testConstAssignment() {
    test(
        "var foo = bar(class {});",
        LINE_JOINER.join(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  public void testLetAssignment() {
    test(
        "let foo = bar(class {});",
        LINE_JOINER.join(
            "const testcode$classdecl$var0 = class {};",
            "let foo = bar(testcode$classdecl$var0);"));
  }

  public void testVarAssignment() {
    test(
        "var foo = bar(class {});",
        LINE_JOINER.join(
            "const testcode$classdecl$var0 = class {};",
            "var foo = bar(testcode$classdecl$var0);"));
  }

  public void testFilenameContainsAt() {
    test(
        ImmutableList.of(
            SourceFile.fromCode("unusual@name", "alert(class {});")),
        ImmutableList.of(
            SourceFile.fromCode(
                "unusual@name",
                LINE_JOINER.join(
                    "const unusual$name$classdecl$var0 = class{};",
                    "alert(unusual$name$classdecl$var0);"))));
  }

  public void testConditionalBlocksExtractionFromCall() {
    testSame("maybeTrue() && f(class{});");
  }

  public void testExtractionFromArrayLiteral() {
    test(
        "var c = [class C {}];",
        LINE_JOINER.join(
            "const testcode$classdecl$var0 = class C {};", "var c = [testcode$classdecl$var0];"));
  }

  public void testConditionalBlocksExtractionFromArrayLiteral() {
    testSame("var c = maybeTrue() && [class {}]");
  }

  public void testTernaryOperatorBlocksExtraction() {
    testSame("var c = maybeTrue() ? class A {} : class B {}");
  }

  public void testClassesHandledByEs6ToEs3Converter() {
    testSame("class C{}");
    testSame("var c = class {};");
  }
}
