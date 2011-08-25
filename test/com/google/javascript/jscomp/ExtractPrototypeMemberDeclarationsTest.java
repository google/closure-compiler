/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;

/**
 * Tests for {@link ExtractPrototypeMemberDeclarations}.
 *
 */
public class ExtractPrototypeMemberDeclarationsTest extends CompilerTestCase {
  private static final String TMP = "JSCompiler_prototypeAlias";
  private Pattern pattern = Pattern.USE_GLOBAL_TEMP;

  @Override
  protected void setUp() {
    super.enableLineNumberCheck(true);
    enableNormalize();
    pattern = Pattern.USE_GLOBAL_TEMP;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExtractPrototypeMemberDeclarations(compiler, pattern);
  }

  public void testNotEnoughPrototypeToExtract() {
    // switch statement with stuff after "return"
    for (int i = 0; i < 7; i++) {
      testSame(generatePrototypeDeclarations("x", i));
    }
  }

  public void testExtractingSingleClassPrototype() {
    extract(generatePrototypeDeclarations("x", 7),
        loadPrototype("x") +
        generateExtractedDeclarations(7));
  }

  public void testExtractingTwoClassPrototype() {
    extract(
        generatePrototypeDeclarations("x", 6) +
        generatePrototypeDeclarations("y", 6),
        loadPrototype("x") +
        generateExtractedDeclarations(6) +
        loadPrototype("y") +
        generateExtractedDeclarations(6));
  }

  public void testExtractingTwoClassPrototypeInDifferentBlocks() {
    extract(
        generatePrototypeDeclarations("x", 6) +
        "if (foo()) {" +
        generatePrototypeDeclarations("y", 6) +
        "}",
        loadPrototype("x") +
        generateExtractedDeclarations(6) +
        "if (foo()) {" +
        loadPrototype("y") +
        generateExtractedDeclarations(6) +
        "}");
  }

  public void testNoMemberDeclarations() {
    testSame(
        "x.prototype = {}; x.prototype = {}; x.prototype = {};" +
        "x.prototype = {}; x.prototype = {}; x.prototype = {};" +
        "x.prototype = {}; x.prototype = {}; x.prototype = {};");
  }

  public void testExtractingPrototypeWithQName() {
    extract(
        generatePrototypeDeclarations("com.google.javascript.jscomp.x", 7),
        loadPrototype("com.google.javascript.jscomp.x") +
        generateExtractedDeclarations(7));
  }

  public void testInterweaved() {
    testSame(
        "x.prototype.a=1; y.prototype.a=1;" +
        "x.prototype.b=1; y.prototype.b=1;" +
        "x.prototype.c=1; y.prototype.c=1;" +
        "x.prototype.d=1; y.prototype.d=1;" +
        "x.prototype.e=1; y.prototype.e=1;" +
        "x.prototype.f=1; y.prototype.f=1;");
  }

  public void testExtractingPrototypeWithNestedMembers() {
    extract(
        "x.prototype.y.a = 1;" +
        "x.prototype.y.b = 1;" +
        "x.prototype.y.c = 1;" +
        "x.prototype.y.d = 1;" +
        "x.prototype.y.e = 1;" +
        "x.prototype.y.f = 1;" +
        "x.prototype.y.g = 1;",
        loadPrototype("x") +
        TMP + ".y.a = 1;" +
        TMP + ".y.b = 1;" +
        TMP + ".y.c = 1;" +
        TMP + ".y.d = 1;" +
        TMP + ".y.e = 1;" +
        TMP + ".y.f = 1;" +
        TMP + ".y.g = 1;");
  }

  public void testWithDevirtualization() {
    extract(
        "x.prototype.a = 1;" +
        "x.prototype.b = 1;" +
        "function devirtualize1() { }" +
        "x.prototype.c = 1;" +
        "x.prototype.d = 1;" +
        "x.prototype.e = 1;" +
        "x.prototype.f = 1;" +
        "x.prototype.g = 1;",

        loadPrototype("x") +
        TMP + ".a = 1;" +
        TMP + ".b = 1;" +
        "function devirtualize1() { }" +
        TMP + ".c = 1;" +
        TMP + ".d = 1;" +
        TMP + ".e = 1;" +
        TMP + ".f = 1;" +
        TMP + ".g = 1;");

    extract(
        "x.prototype.a = 1;" +
        "x.prototype.b = 1;" +
        "function devirtualize1() { }" +
        "x.prototype.c = 1;" +
        "x.prototype.d = 1;" +
        "function devirtualize2() { }" +
        "x.prototype.e = 1;" +
        "x.prototype.f = 1;" +
        "function devirtualize3() { }" +
        "x.prototype.g = 1;",

        loadPrototype("x") +
        TMP + ".a = 1;" +
        TMP + ".b = 1;" +
        "function devirtualize1() { }" +
        TMP + ".c = 1;" +
        TMP + ".d = 1;" +
        "function devirtualize2() { }" +
        TMP + ".e = 1;" +
        TMP + ".f = 1;" +
        "function devirtualize3() { }" +
        TMP + ".g = 1;");
  }

  public void testAnonSimple() {
    pattern = Pattern.USE_ANON_FUNCTION;

    extract(
        generatePrototypeDeclarations("x", 3),
        generateExtractedDeclarations(3) +
        loadPrototype("x"));

    testSame(generatePrototypeDeclarations("x", 1));
    testSame(generatePrototypeDeclarations("x", 2));

    extract(
        generatePrototypeDeclarations("x", 7),
        generateExtractedDeclarations(7) +
        loadPrototype("x"));

  }

  public void testAnonWithDevirtualization() {
    pattern = Pattern.USE_ANON_FUNCTION;

    extract(
        "x.prototype.a = 1;" +
        "x.prototype.b = 1;" +
        "function devirtualize() { }" +
        "x.prototype.c = 1;",

        "(function(" + TMP + "){" +
        TMP + ".a = 1;" +
        TMP + ".b = 1;" +
        TMP + ".c = 1;" +
        loadPrototype("x") +
        "function devirtualize() { }");

    extract(
        "x.prototype.a = 1;" +
        "function devirtualize1() { }" +
        "x.prototype.b = 1;" +
        "function devirtualize2() { }" +
        "x.prototype.c = 1;" +
        "function devirtualize3() { }",

        "(function(" + TMP + "){" +
        TMP + ".a = 1;" +
        TMP + ".b = 1;" +
        TMP + ".c = 1;" +
        loadPrototype("x") +
        "function devirtualize1() { }" +
        "function devirtualize2() { }" +
        "function devirtualize3() { }");
  }

  public void testAnonWithSideFx() {
    pattern = Pattern.USE_ANON_FUNCTION;
    testSame(
        "function foo() {};" +
        "foo.prototype.a1 = 1;" +
        "bar();;" +
        "foo.prototype.a2 = 2;" +
        "bar();;" +
        "foo.prototype.a3 = 3;" +
        "bar();;" +
        "foo.prototype.a4 = 4;" +
        "bar();;" +
        "foo.prototype.a5 = 5;" +
        "bar();;" +
        "foo.prototype.a6 = 6;" +
        "bar();;" +
        "foo.prototype.a7 = 7;" +
        "bar();");
  }

  public String loadPrototype(String qName) {
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      return TMP + " = " + qName + ".prototype;";
    } else {
      return "})(" + qName + ".prototype);";
    }
  }

  public void extract(String src, String expected) {
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      test(src, "var " + TMP + ";" + expected);
    } else {
      test(src, expected);
    }
  }

  public String generatePrototypeDeclarations(String className, int num) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < num; i++) {
      char member = (char) ('a' + i);
      builder.append(generatePrototypeDeclaration(
          className, "" + member,  "" + member));
    }
    return builder.toString();
  }

  public String generatePrototypeDeclaration(String className, String member,
      String value) {
    return className + ".prototype." + member + " = " + value + ";";
  }

  public String generateExtractedDeclarations(int num) {
    StringBuilder builder = new StringBuilder();

    if (pattern == Pattern.USE_ANON_FUNCTION) {
      builder.append("(function(" + TMP + "){");
    }

    for (int i = 0; i < num; i++) {
      char member = (char) ('a' + i);
      builder.append(generateExtractedDeclaration("" + member,  "" + member));
    }
    return builder.toString();
  }

  public String generateExtractedDeclaration(String member, String value) {
    return TMP + "." + member + " = " + value + ";";
  }
}
