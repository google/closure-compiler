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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.deps.DependencyInfo.Require.es6Import;
import static com.google.javascript.jscomp.deps.DependencyInfo.Require.googRequireSymbol;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link JsFileParser}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
@RunWith(JUnit4.class)
public final class JsFileParserTest {

  JsFileParser parser;
  private ErrorManager errorManager;

  private static final String SRC_PATH = "a";
  private static final String CLOSURE_PATH = "b";

  @Before
  public void setUp() {
    errorManager = new PrintStreamErrorManager(System.err);
    parser = new JsFileParser(errorManager);
    parser.setShortcutMode(true);
  }

  /**
   * Tests:
   *
   * <ul>
   *   <li>Parsing of comments,
   *   <li>Parsing of different styles of quotes,
   *   <li>Correct recording of what was parsed.
   * </ul>
   */
  @Test
  public void testParseFile() {
    String contents = "/*"
      + "goog.provide('no1');*//*\n"
      + "goog.provide('no2');\n"
      + "*/goog.provide('yes1');\n"
      + "/* blah */goog.provide(\"yes2\")/* blah*/\n"
      + "goog.require('yes3'); // goog.provide('no3');\n"
      + "// goog.provide('no4');\n"
      + "goog.require(\"bar.data.SuperstarAddStarThreadActionRequestDelegate\"); "
      + "//no new line at EOF";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1", "yes2"))
            .setRequires(
                googRequireSymbol("yes3"),
                googRequireSymbol("bar.data.SuperstarAddStarThreadActionRequestDelegate"))
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /** Tests correct recording of what was parsed. */
  @Test
  public void testParseFile2() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "var yes2 = goog.require('yes2');\n"
      + "var C = goog.require(\"a.b.C\");\n"
      + "let {D, E} = goog.require('a.b.d');";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setRequires(
                ImmutableList.of(
                    googRequireSymbol("yes2"),
                    googRequireSymbol("a.b.C"),
                    googRequireSymbol("a.b.d")))
            .setLoadFlags(ImmutableMap.of("module", "goog"))
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /** Tests correct recording of what was parsed. */
  @Test
  public void testParseFile3() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setRequires(
                ImmutableList.of(
                    googRequireSymbol("yes2"),
                    googRequireSymbol("a.b.C"),
                    googRequireSymbol("a.b.d")))
            .setLoadFlags(ImmutableMap.of("module", "goog"))
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testParseGoogModuleWithRequireType() {
    String contents =
        ""
            + "goog.module('yes1');\n"
            + "var yes2=goog.requireType('yes2');\n"
            + "var C=goog.requireType(\"a.b.C\");\n"
            + "const {\n  D,\n  E\n}=goog.requireType(\"a.b.d\");";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setTypeRequires(ImmutableList.of("yes2", "a.b.C", "a.b.d"))
            .setLoadFlags(ImmutableMap.of("module", "goog"))
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testParseScriptWithRequireType() {
    String contents = "" + "goog.provide('yes1');\n" + "goog.requireType('a.b.C');";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setTypeRequires(ImmutableList.of("a.b.C"))
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /** Tests correct recording of what was parsed. */
  @Test
  public void testParseWrappedGoogModule() {
    String contents = ""
      + "goog.loadModule(function(){\"use strict\";goog.module('yes1');\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");});";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setRequires(
                ImmutableList.of(
                    googRequireSymbol("yes2"),
                    googRequireSymbol("a.b.C"),
                    googRequireSymbol("a.b.d")))
            .setLoadFlags(ImmutableMap.<String, String>of())
            .build(); // wrapped modules aren't marked as modules

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  // TODO(sdh): Add a test for import with .js suffix once #1897 is fixed.

  /** Tests ES6 modules parsed correctly, particularly the various formats. */
  @Test
  public void testParseEs6Module() {
    String contents = ""
        + "import def, {yes2} from './yes2';\n"
        + "import C from './a/b/C';\n"
        + "import * as d from './a/b/d';\n"
        + "import \"./dquote\";\n"
        + "export * from './exported';\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("a.js", "b.js")
            .setProvides(ImmutableList.of("module$b"))
            .setRequires(
                es6Import("module$yes2", "./yes2"),
                es6Import("module$a$b$C", "./a/b/C"),
                es6Import("module$a$b$d", "./a/b/d"),
                es6Import("module$dquote", "./dquote"),
                es6Import("module$exported", "./exported"))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result = parser.parseFile("b.js", "a.js", contents);

    assertDeps(expected, result);
  }

  /** Tests relative paths resolved correctly. */
  @Test
  public void testParseEs6Module2() {
    String contents = ""
        + "import './x';\n"
        + "import '../y';\n"
        + "import '../a/z';\n"
        + "import '../c/w';\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../../a/b.js", "/foo/bar/a/b.js")
            .setProvides(ImmutableList.of("module$foo$bar$a$b"))
            .setRequires(
                ImmutableList.of(
                    es6Import("module$foo$bar$a$x", "./x"), es6Import("module$foo$bar$y", "../y"),
                    es6Import("module$foo$bar$a$z", "../a/z"),
                        es6Import("module$foo$bar$c$w", "../c/w")))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result = parser.parseFile("/foo/bar/a/b.js", "../../a/b.js", contents);

    assertDeps(expected, result);
  }

  /** Tests handles goog.require and import 'goog:...'. */
  @Test
  public void testParseEs6Module3() {
    String contents = ""
        + "import 'goog:foo.bar.baz';\n"
        + "goog.require('baz.qux');\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("b.js", "a.js")
            .setProvides(ImmutableList.of("module$a"))
            .setRequires(
                ImmutableList.of(googRequireSymbol("foo.bar.baz"), googRequireSymbol("baz.qux")))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result = parser.parseFile("a.js", "b.js", contents);

    assertDeps(expected, result);
  }

  /** Tests setModuleLoader taken into account */
  @Test
  public void testParseEs6Module4() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("/foo"),
            ImmutableList.of(),
            BrowserModuleResolver.FACTORY);

    String contents = ""
        + "import './a';\n"
        + "import './qux/b';\n"
        + "import '../closure/c';\n"
        + "import '../closure/d/e';\n"
        + "import '../../corge/f';\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../bar/baz.js", "/foo/js/bar/baz.js")
            .setProvides(ImmutableList.of("module$js$bar$baz"))
            .setRequires(
                ImmutableList.of(
                    es6Import("module$js$bar$a", "./a"),
                    es6Import("module$js$bar$qux$b", "./qux/b"),
                    es6Import("module$js$closure$c", "../closure/c"),
                    es6Import("module$js$closure$d$e", "../closure/d/e"),
                    es6Import("module$corge$f", "../../corge/f")))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result =
        parser
            .setModuleLoader(loader)
            .parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertDeps(expected, result);
  }

  // TODO(johnplaisted): This should eventually be an error. For now people are relying on this
  // behavior for interop / ordering. Until we have official channels for these allow this behavior,
  // but don't encourage it.
  @Test
  public void testParseEs6ModuleWithGoogProvide() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("/foo"),
            ImmutableList.of(),
            BrowserModuleResolver.FACTORY);

    String contents = "goog.provide('my.namespace');\nexport {};";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../bar/baz.js", "/foo/js/bar/baz.js")
            .setProvides(ImmutableList.of("my.namespace", "module$js$bar$baz"))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result =
        parser
            .setModuleLoader(loader)
            .parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertThat(result).isEqualTo(expected);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
    assertThat(errorManager.getWarningCount()).isEqualTo(1);
    assertThat(errorManager.getWarnings()[0].getType()).isEqualTo(ModuleLoader.MODULE_CONFLICT);
  }

  @Test
  public void testEs6ModuleWithDeclareNamespace() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("/foo"),
            ImmutableList.of(),
            BrowserModuleResolver.FACTORY);

    String contents = "goog.module.declareNamespace('my.namespace');\nexport {};";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../bar/baz.js", "/foo/js/bar/baz.js")
            .setProvides(ImmutableList.of("my.namespace", "module$js$bar$baz"))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result =
        parser.setModuleLoader(loader).parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertDeps(expected, result);
  }

  @Test
  public void testEs6ModuleWithDeclareModuleId() {
    ModuleLoader loader =
        new ModuleLoader(
            null, ImmutableList.of("/foo"), ImmutableList.of(), BrowserModuleResolver.FACTORY);

    String contents = "goog.declareModuleId('my.namespace');\nexport {};";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../bar/baz.js", "/foo/js/bar/baz.js")
            .setProvides(ImmutableList.of("my.namespace", "module$js$bar$baz"))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result =
        parser.setModuleLoader(loader).parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertDeps(expected, result);
  }

  @Test
  public void testEs6ModuleWithBrowserTransformedPrefixResolver() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of(),
            ImmutableList.of(),
            new BrowserWithTransformedPrefixesModuleResolver.Factory(
                ImmutableMap.of("@root/", "/path/to/project/")));

    String contents = "import '@root/my/file.js';";

    DependencyInfo expected =
        SimpleDependencyInfo.builder("../bar/baz.js", "/foo/js/bar/baz.js")
            .setProvides(ImmutableList.of("module$foo$js$bar$baz"))
            .setRequires(Require.es6Import("module$path$to$project$my$file", "@root/my/file.js"))
            .setLoadFlags(ImmutableMap.of("module", "es6"))
            .build();

    DependencyInfo result =
        parser.setModuleLoader(loader).parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertDeps(expected, result);
  }

  /** Tests shortcut mode doesn't stop at setTestOnly() or declareLegacyNamespace(). */
  @Test
  public void testNoShortcutForCommonModuleModifiers() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "goog.module.declareLegacyNamespace();\n"
      + "goog.setTestOnly();\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1"))
            .setRequires(
                googRequireSymbol("yes2"), googRequireSymbol("a.b.C"), googRequireSymbol("a.b.d"))
            .setGoogModule(true)
            .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testMultiplePerLine() {
    String contents = "goog.provide('yes1');goog.provide('yes2');/*"
        + "goog.provide('no1');*/goog.provide('yes3');//goog.provide('no2');";

    DependencyInfo expected = SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
        .setProvides(ImmutableList.of("yes1", "yes2", "yes3"))
        .setGoogModule(false)
        .build();

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testShortcutMode1() {
    // For efficiency reasons, we stop reading after the ctor.
    String contents = " // hi ! \n /* this is a comment */ "
        + "goog.provide('yes1');\n /* and another comment */ \n"
        + "goog.provide('yes2'); // include this\n"
        + "foo = function() {};\n"
        + "goog.provide('no1');";

    DependencyInfo expected = SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
        .setProvides(ImmutableList.of("yes1", "yes2"))
        .setGoogModule(false)
        .build();
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testShortcutMode2() {
    String contents = "/** goog.provide('no1'); \n" +
        " * goog.provide('no2');\n */\n"
        + "goog.provide('yes1');\n";

    DependencyInfo expected = SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
        .setProvides(ImmutableList.of("yes1"))
        .setGoogModule(false)
        .build();
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testShortcutMode3() {
    String contents = "/**\n" +
        " * goog.provide('no1');\n */\n"
        + "goog.provide('yes1');\n";

    DependencyInfo expected = SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
        .setProvides(ImmutableList.of("yes1"))
        .setGoogModule(false)
        .build();
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testIncludeGoog1() {
    String contents = "/**\n" + " * @provideGoog\n" + " */\n";

    DependencyInfo expected = SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
        .setProvides(ImmutableList.of("goog"))
        .setGoogModule(false)
        .build();
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  @Test
  public void testIncludeGoog2() {
    String contents = "goog.require('bar');";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setRequires(ImmutableList.of(googRequireSymbol("goog"), googRequireSymbol("bar")))
            .setGoogModule(false)
            .build();
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  @Test
  public void testIncludeGoog3() {
    // This is pretending to provide goog, but it really doesn't.
    String contents = "goog.provide('x');\n" +
        "/**\n" +
        " * the first constant in base.js\n" +
        " */\n" +
        "var COMPILED = false;\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("x"))
            .setRequires(ImmutableList.of(googRequireSymbol("goog")))
            .setGoogModule(false)
            .build();
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  @Test
  public void testIncludeGoog4() {
    String contents = "goog.addDependency('foo', [], []);\n";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setRequires(ImmutableList.of(googRequireSymbol("goog")))
            .setGoogModule(false)
            .build();
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  @Test
  public void testParseProvidesAndWrappedGoogModule() {
    String contents =
        ""
            + "goog.loadModule(function(){\"use strict\";goog.module('yes1');\n"
            + "goog.provide('my.provide');\n"
            + "var yes2=goog.require('yes2');\n"
            + "var C=goog.require(\"a.b.C\");\n"
            + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");});";

    DependencyInfo expected =
        SimpleDependencyInfo.builder(CLOSURE_PATH, SRC_PATH)
            .setProvides(ImmutableList.of("yes1", "my.provide"))
            .setRequires(
                ImmutableList.of(
                    googRequireSymbol("yes2"),
                    googRequireSymbol("a.b.C"),
                    googRequireSymbol("a.b.d")))
            .setLoadFlags(ImmutableMap.of())
            .build(); // wrapped modules aren't marked as modules

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  @Test
  public void testEs6AndWrappedGoogModuleIsError() {
    String contents =
        "goog.loadModule(function(){\"use strict\";goog.module('yes1');});\n" + "export {};";

    parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertThat(errorManager.getErrorCount()).isEqualTo(0);
    assertThat(errorManager.getWarningCount()).isEqualTo(1);
    assertThat(errorManager.getWarnings()[0].getType()).isEqualTo(ModuleLoader.MODULE_CONFLICT);
  }

  /** Asserts the deps match without errors */
  private void assertDeps(DependencyInfo expected, DependencyInfo actual) {
    assertThat(actual).isEqualTo(expected);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }
}
