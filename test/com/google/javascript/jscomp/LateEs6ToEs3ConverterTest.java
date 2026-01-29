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


import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for ES6 transpilation. This tests {@link LateEs6ToEs3Converter} */
@RunWith(JUnit4.class)
public final class LateEs6ToEs3ConverterTest extends CompilerTestCase {
  // Sample first script for use when testing non-first scripts
  private static final String SCRIPT1 = "var x;";

  // The pass generates variable names consisting of filepath based uniqueIDs e.g. {@code
  // $jscomp$templatelit$12345}. Use generic name `TAGGED_TEMPLATE_TMP_VAR` in test sources for
  // readability.
  private static final ImmutableMap<String, String> REPLACEMENT_PREFIXES =
      ImmutableMap.of("TAGGED_TEMPLATE_TMP_VAR", "$jscomp$templatelit$");

  private static final String RUNTIME_STUBS =
      """
      /** @const */
      var $jscomp = {};
      $jscomp.createTemplateTagFirstArg = function(arrayStrings) {};
      $jscomp.createTemplateTagFirstArgWithRaw = function(anotherArray, rawArrayStrings) {};
      """;

  public LateEs6ToEs3ConverterTest() {
    super(MINIMAL_EXTERNS + RUNTIME_STUBS);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(REPLACEMENT_PREFIXES);
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
        expected(
            """
            var $jscomp$compprop0 = {};
            var x = ($jscomp$compprop0[Symbol.iterator] = function() {return this;},
                     $jscomp$compprop0)
            """));
  }

  @Test
  public void testMethodInObject() {
    test("var obj = { f() {alert(1); } };", "var obj = { f: function() {alert(1); } };");

    test("var obj = { f() { alert(1); }, x };", "var obj = { f: function() { alert(1); }, x: x };");
  }

  @Test
  public void testComputedPropertiesWithMethod() {
    test(
        "var obj = { ['f' + 1]: 1, m() {}, ['g' + 1]: 1, };",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0.m = function() {},
             ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));
        """);
  }

  @Test
  public void testComputedProperties() {
    test(
        "var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));
        """);

    test(
        "var obj = { ['f'] : 1};",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f'] = 1,
          $jscomp$compprop0);
        """);

    test(
        "var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        """
        var $jscomp$compprop0 = {};
        var o = ($jscomp$compprop0['f'] = 1,
          $jscomp$compprop0);
        var $jscomp$compprop1 = {};
        var p = ($jscomp$compprop1['g'] = 1,
          $jscomp$compprop1);
        """);

    test(
        "({['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['f' + 1] = 1,
          $jscomp$compprop0)
        """);

    test(
        "({'a' : 2, ['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = 2,
          ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));
        """);

    test(
        "({['f' + 1] : 1, 'a' : 2})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));
        """);

    test(
        "({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = 1,
          ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));
        """);

    test(
        "({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,
          ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))
        """);

    test(
        "({a : x++, ['f' + x++] : 1, b : x++})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,
          ($jscomp$compprop0.b = x++, $jscomp$compprop0)))
        """);

    test(
        "({a, ['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
          ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))
        """);

    test(
        "({['f' + 1] : 1, a})",
        """
        var $jscomp$compprop0 = {};
          ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))
        """);

    test(
        "var obj = { [foo]() {}}",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)
        """);
  }

  @Test
  public void testComputedPropGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (strParam) {}}");
    test(
        "var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        """
        var $jscomp$compprop0 = {get l () {return null;}};
        var obj = ($jscomp$compprop0['a'] = 2,
          ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));
        """);
    test(
        "var obj = {['a' + 'b'] : 2, set l (strParam) {}}",
        """
        var $jscomp$compprop0 = {set l (strParam) {}};
        var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);
        """);
  }

  @Test
  public void testComputedPropCannotConvert() {
    testError(
        "var o = { get [foo]() {}}", ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
    testError(
        "var o = { set [foo](val) {}}",
        ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
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
    test(
        "tag``",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0);
        """);

    test(
        "tag`${hello} world`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['', ' world']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0, hello);
        """);

    test(
        "tag`${hello} ${world}`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['', ' ', '']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0, hello, world);
        """);

    test(
        "tag`\"`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['"']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0);
        """);

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
    test(
        "tag`a\\tb`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArgWithRaw(['a\\tb'],['a\\\\tb']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0);
        """);

    test(
        "tag()`${hello} world`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['', ' world']);
        tag()(TAGGED_TEMPLATE_TMP_VAR$0, hello);
        """);

    test(
        externs(RUNTIME_STUBS, "var a = {}; a.b;"),
        srcs("a.b`${hello} world`"),
        expected(
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['', ' world']);
            a.b(TAGGED_TEMPLATE_TMP_VAR$0, hello);
            """));

    // https://github.com/google/closure-compiler/issues/1299
    test(
        "tag`<p class=\"foo\">${x}</p>`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['<p class="foo">','</p>']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0, x);
        """);
    test(
        "tag`<p class='foo'>${x}</p>`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArg(['<p class=\\'foo\\'>','</p>']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0, x);
        """);

    // invalid escape sequences result in undefined cooked string
    test(
        "tag`\\unicode`",
        """
        /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
            $jscomp.createTemplateTagFirstArgWithRaw([void 0],['\\\\unicode']);
        tag(TAGGED_TEMPLATE_TMP_VAR$0);
        """);
  }

  /**
   * Tests that calls to `createTemplateTagFirstArg` are inserted after its injected declaration
   * when input is a single script
   */
  @Test
  public void testTaggedTemplateLiteral_insertPosition_singleScript() {
    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            RUNTIME_STUBS
                + """
                var a = {};
                tag``; var b;
                """),
        expected(
            RUNTIME_STUBS
                + """
                /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                    $jscomp.createTemplateTagFirstArg(['']);
                var a = {};
                tag(TAGGED_TEMPLATE_TMP_VAR$0);
                var b;
                """));

    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            RUNTIME_STUBS
                + """
                var a = {};
                function foo() {tag``;}
                """),
        expected(
            RUNTIME_STUBS
                + """
                /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                    $jscomp.createTemplateTagFirstArg(['']);
                var a = {};
                function foo() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}
                """));

    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            RUNTIME_STUBS
                + """
                var a = {};
                function foo() {function bar() {tag``;}}
                """),
        expected(
            RUNTIME_STUBS
                + """
                /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                    $jscomp.createTemplateTagFirstArg(['']);
                var a = {};
                function foo() {function bar() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}}
                """));
  }

  /**
   * When input is multiple scripts, tests that calls to `createTemplateTagFirstArg` are inserted at
   * the top for any subsequent (non-first) script.
   */
  @Test
  public void testTaggedTemplateLiteral_insertPosition_multipleScripts() {
    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            RUNTIME_STUBS + SCRIPT1,
            """
            var a = {};
            tag``; var b;
            """),
        expected(
            RUNTIME_STUBS + SCRIPT1,
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['']);
            var a = {};
            tag(TAGGED_TEMPLATE_TMP_VAR$0); var b;
            """));

    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            RUNTIME_STUBS + SCRIPT1,
            """
            var a = {};
            function foo() {tag``;}
            """),
        expected(
            RUNTIME_STUBS + SCRIPT1,
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['']);
            var a = {};
            function foo() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}
            """));

    test(
        externs(""), // clear the default externs which contain the runtime stubs
        srcs(
            SCRIPT1 + RUNTIME_STUBS,
            """
            var a = {};
            function foo() {function bar() {tag``;}}
            """),
        expected(
            SCRIPT1 + RUNTIME_STUBS,
            """
            /** @noinline */ var TAGGED_TEMPLATE_TMP_VAR$0 =
                $jscomp.createTemplateTagFirstArg(['']);
            var a = {};
            function foo() {function bar() {tag(TAGGED_TEMPLATE_TMP_VAR$0);}}
            """));
  }

  @Test
  public void testUnicodeEscapes() {
    test("var \\u{73} = \'\\u{2603}\'", "var s = \'\u2603\'"); // ☃
    test("var \\u{63} = \'\\u{1f42a}\'", "var c = \'\uD83D\uDC2A\'"); // 🐪
    test("var str = `begin\\u{2026}end`", "var str = 'begin\\u2026end'");
  }
}
