/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for ES6 transpilation. This tests {@link LateEs6ToEs3Converter} */
@RunWith(JUnit4.class)
public final class LateEs6ToEs3ConverterTest extends CompilerTestCase {
  // Sample first script for use when testing non-first scripts
  private static final String SCRIPT1 = "var x;";

  private static final String RUNTIME_STUBS =
      lines(
          "/** @const */",
          "var $jscomp = {};",
          "$jscomp.createTemplateTagFirstArg = function(arrayStrings) {};",
          "$jscomp.createTemplateTagFirstArgWithRaw = function(arrayStrings, rawArrayStrings) {};");

  public LateEs6ToEs3ConverterTest() {
    super(MINIMAL_EXTERNS + RUNTIME_STUBS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new LateEs6ToEs3Converter(compiler);
  }

  @Test
  public void testObjectLiteralMemberFunctionDef() {
    test(
        "var x = {/** @return {number} */ a() { return 0; } };",
        "var x = {a: function() { return 0; } };");
  }

  @Test
  public void testInitSymbolIterator() {
    test(
        externs("/** @type {symbol} */ Symbol.iterator;"),
        srcs("var x = {[Symbol.iterator]: function() { return this; }};"),
        expected(lines(
            "var $jscomp$compprop0 = {};",
            "var x = ($jscomp$compprop0[Symbol.iterator] = function() {return this;},",
            "         $jscomp$compprop0)")));
  }

  @Test
  public void testMethodInObject() {
    test("var obj = { f() {alert(1); } };",
        "var obj = { f: function() {alert(1); } };");

    test(
        "var obj = { f() { alert(1); }, x };",
        "var obj = { f: function() { alert(1); }, x: x };");
  }

  @Test
  public void testComputedPropertiesWithMethod() {
    test(
        "var obj = { ['f' + 1]: 1, m() {}, ['g' + 1]: 1, };",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0.m = function() {}, ",
            "     ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));"));
  }

  @Test
  public void testComputedProperties() {
    test(
        "var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));"));

    test(
        "var obj = { ['f'] : 1};",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);"));

    test(
        "var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        lines(
            "var $jscomp$compprop0 = {};",
            "var o = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);",
            "var $jscomp$compprop1 = {};",
            "var p = ($jscomp$compprop1['g'] = 1,",
            "  $jscomp$compprop1);"));

    test(
        "({['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  $jscomp$compprop0)"));

    test(
        "({'a' : 2, ['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));

    test(
        "({['f' + 1] : 1, 'a' : 2})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));"));

    test("({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        lines(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 1,",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));"
    ));

    test(
        "({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))"));

    test(
        "({a : x++, ['f' + x++] : 1, b : x++})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0.b = x++, $jscomp$compprop0)))"));

    test(
        "({a, ['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))"));

    test(
        "({['f' + 1] : 1, a})",
        lines(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))"));

    test(
        "var obj = { [foo]() {}}",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)"));
  }

  @Test
  public void testComputedPropGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test(
        "var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        lines(
            "var $jscomp$compprop0 = {get l () {return null;}};",
            "var obj = ($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));
    test(
        "var obj = {['a' + 'b'] : 2, set l (str) {}}",
        lines(
            "var $jscomp$compprop0 = {set l (str) {}};",
            "var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);"));
  }

  @Test
  public void testComputedPropCannotConvert() {
    testError("var o = { get [foo]() {}}", CANNOT_CONVERT_YET);
    testError("var o = { set [foo](val) {}}", CANNOT_CONVERT_YET);
  }

  @Test
  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  @Test
  public void testUntaggedTemplateLiteral() {
    test("``", "''");
    test("`\"`", "'\\\"'");
    test("`'`", "\"'\"");
    test("`\\``", "'`'");
    test("`\\\"`", "'\\\"'");
    test("`\\\\\"`", "'\\\\\\\"'");
    test("`\"\\\\`", "'\"\\\\'");
    test("`$$`", "'$$'");
    test("`$$$`", "'$$$'");
    test("`\\$$$`", "'$$$'");
    test("`hello`", "'hello'");
    test("`hello\nworld`", "'hello\\nworld'");
    test("`hello\rworld`", "'hello\\nworld'");
    test("`hello\r\nworld`", "'hello\\nworld'");
    test("`hello\n\nworld`", "'hello\\n\\nworld'");
    test("`hello\\r\\nworld`", "'hello\\r\\nworld'");
    test("`${world}`", "'' + world");
    test("`hello ${world}`", "'hello ' + world");
    test("`${hello} world`", "hello + ' world'");
    test("`${hello}${world}`", "'' + hello + world");
    test("`${a} b ${c} d ${e}`", "a + ' b ' + c + ' d ' + e");
    test("`hello ${a + b}`", "'hello ' + (a + b)");
    test("`hello ${a, b, c}`", "'hello ' + (a, b, c)");
    test("`hello ${a ? b : c}${a * b}`", "'hello ' + (a ? b : c) + (a * b)");
  }

  /**
   * Tests that the tagged template literals in the first script get transpiled correctly.
   *
   * <p>The vars created during tagged template literal transpilation have have a filePath based
   * uniqueID in them. This uniqueID is obfucated by using a generic name `TAGGED_TEMPLATE_TMP_VAR`
   * here that gets replaced by the runtime-computed uniqueID before test execution.
   */
  @Test
  public void testTaggedTemplateLiteral_singleScript() {
    taggedTemplateLiteralTestRunner(
        srcs("tag``"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0);")));

    taggedTemplateLiteralTestRunner(
        srcs("tag`${hello} world`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['', ' world']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0, hello);")));

    taggedTemplateLiteralTestRunner(
        srcs("tag`${hello} ${world}`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 = ",
                "    $jscomp.createTemplateTagFirstArg(['', ' ', '']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0, hello, world);")));

    taggedTemplateLiteralTestRunner(
        srcs("tag`\"`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['\"']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0);")));

    // The cooked string and the raw string are different.
    // Note that this test is tricky to read, because any escape sequences will be escaped twice.
    // This table is helpful:
    //
    //     Java String    JavaScript String      JavaScript Value
    //
    //     ----------------------------------------------------------------
    //     \t        ->   <tab character>    -> <tab character> (length: 1)
    //     \\t       ->   \t                 -> <tab character> (length: 1)
    //     \\\t      ->   \<tab character>   -> <tab character> (length: 1)
    //     \\\\t     ->   \\t                -> \t              (length: 2)
    //
    taggedTemplateLiteralTestRunner(
        srcs("tag`a\\tb`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArgWithRaw(['a\\tb'],['a\\\\tb']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0);")));

    taggedTemplateLiteralTestRunner(
        srcs("tag()`${hello} world`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 = ",
                "    $jscomp.createTemplateTagFirstArg(['', ' world']);",
                "tag()(TAGGED_TEMPLATE_TMP_VAR$0, hello);")));

    taggedTemplateLiteralTestRunner(
        externs(RUNTIME_STUBS, "var a = {}; a.b;"),
        srcs("a.b`${hello} world`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['', ' world']);",
                "a.b(TAGGED_TEMPLATE_TMP_VAR$0, hello);")));

    // https://github.com/google/closure-compiler/issues/1299
    taggedTemplateLiteralTestRunner(
        srcs("tag`<p class=\"foo\">${x}</p>`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR" + "$0 =",
                "    $jscomp.createTemplateTagFirstArg(['<p class=\"foo\">','</p>']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0, x);")));
    taggedTemplateLiteralTestRunner(
        srcs("tag`<p class='foo'>${x}</p>`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR" + "$0 =",
                "    $jscomp.createTemplateTagFirstArg(['<p class=\\'foo\\'>','</p>']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0, x);")));

    // invalid escape sequences result in undefined cooked string
    taggedTemplateLiteralTestRunner(
        srcs("tag`\\unicode`"),
        expected(
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR" + "$0 =",
                "    $jscomp.createTemplateTagFirstArgWithRaw([void 0],['\\\\unicode']);",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0);")));
  }

  /**
   * Tests that calls to `createTemplateTagFirstArg` are inserted after its injected declaration
   * when input is a single script
   */
  @Test
  public void testTaggedTemplateLiteral_insertPosition_singleScript() {
    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(lines(RUNTIME_STUBS, "var a = {};", "tag``; var b;")),
        expected(
            lines(
                RUNTIME_STUBS,
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0);",
                "var b;")));

    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(lines(RUNTIME_STUBS, "var a = {};", "function foo() {tag``;}")),
        expected(
            lines(
                RUNTIME_STUBS,
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "function foo() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}")));

    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(lines(RUNTIME_STUBS, "var a = {};", "function foo() {function bar() {tag``;}}")),
        expected(
            lines(
                RUNTIME_STUBS,
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "function foo() {function bar() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}}")));
  }

  /**
   * When input is multiple scripts, tests that calls to `createTemplateTagFirstArg` are inserted at
   * the top for any subsequent (non-first) script.
   */
  @Test
  public void testTaggedTemplateLiteral_insertPosition_multipleScripts() {
    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(lines(RUNTIME_STUBS, SCRIPT1), lines("var a = {};", "tag``; var b;")),
        expected(
            lines(RUNTIME_STUBS, SCRIPT1),
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "tag(TAGGED_TEMPLATE_TMP_VAR$0); var b;")));

    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(lines(RUNTIME_STUBS, SCRIPT1), lines("var a = {};", "function foo() {tag``;}")),
        expected(
            lines(RUNTIME_STUBS, SCRIPT1),
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "function foo() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}")));

    taggedTemplateLiteralTestRunner(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            lines(SCRIPT1, RUNTIME_STUBS),
            lines("var a = {};", "function foo() {function bar() {tag``;}}")),
        expected(
            lines(SCRIPT1, RUNTIME_STUBS),
            lines(
                "/** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =",
                "    $jscomp.createTemplateTagFirstArg(['']);",
                "var a = {};",
                "function foo() {function bar() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}}")));
  }

  /** Runs the tagged template literal test with externs. */
  private void taggedTemplateLiteralTestRunner(Externs externs, Sources inputs, Expected outputs) {
    Expected exp = replaceVarNamesInExpectedFiles(inputs, outputs);
    test(externs, inputs, exp);
  }

  /** Runs the tagged template literal test. */
  private void taggedTemplateLiteralTestRunner(Sources inputs, Expected outputs) {
    Expected exp = replaceVarNamesInExpectedFiles(inputs, outputs);
    test(inputs, exp);
  }

  /**
   * Helper to replace the generic name `TAGGED_TEMPLATE_TMP_VAR` in each of the expected test
   * output files with the calculated uniqueID hashString based on the corresponding test's input
   * file.
   *
   * <p>The vars created during tagged template literal transpilation have have a filePath based
   * uniqueID in them. This uniqueID is obfucated by using a generic name `TAGGED_TEMPLATE_TMP_VAR`
   * in the test sources. This function replaces that generic name by the runtime-computed uniqueID
   * before test execution.
   */
  private static Expected replaceVarNamesInExpectedFiles(Sources inputs, Expected outputs) {
    int inLength = ((FlatSources) inputs).sources.size();
    int outLength = outputs.expected.size();
    Preconditions.checkArgument(inLength == outLength);

    List<SourceFile> updatedOutputs = new ArrayList<>();
    for (int i = 0; i < inLength; i++) {
      SourceFile inputScript = ((FlatSources) inputs).sources.get(i);
      SourceFile outputScript = outputs.expected.get(i);

      int fileHashCode = inputScript.getName().hashCode();
      String fileHashString = (fileHashCode < 0) ? ("m" + -fileHashCode) : ("" + fileHashCode);
      String expectedCode = "";
      String expectedCodeFileName = outputScript.getName();
      try {
        expectedCode = outputScript.getCode();
      } catch (IOException e) {
        new RuntimeException("Read error: " + expectedCodeFileName);
      }
      expectedCode =
          expectedCode.replace("TAGGED_TEMPLATE_TMP_VAR", "$jscomp$templatelit$" + fileHashString);
      updatedOutputs.add(SourceFile.fromCode(expectedCodeFileName, expectedCode));
    }
    return expected(updatedOutputs);
  }

  @Test
  public void testUnicodeEscapes() {
    test("var \\u{73} = \'\\u{2603}\'", "var s = \'\u2603\'");  // ☃
    test("var \\u{63} = \'\\u{1f42a}\'", "var c = \'\uD83D\uDC2A\'");  // 🐪
    test("var str = `begin\\u{2026}end`", "var str = 'begin\\u2026end'");
  }
}
