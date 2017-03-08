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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import java.util.Collections;
import junit.framework.TestCase;

/**
 * Tests for {@link JsFileParser}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public final class JsFileParserTest extends TestCase {

  JsFileParser parser;
  private ErrorManager errorManager;

  private static final String SRC_PATH = "a";
  private static final String CLOSURE_PATH = "b";

  @Override
  public void setUp() {
    errorManager = new PrintStreamErrorManager(System.err);
    parser = new JsFileParser(errorManager);
    parser.setShortcutMode(true);
  }

  /**
   * Tests:
   *  -Parsing of comments,
   *  -Parsing of different styles of quotes,
   *  -Correct recording of what was parsed.
   */
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

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1", "yes2"),
        ImmutableList.of("yes3", "bar.data.SuperstarAddStarThreadActionRequestDelegate"), false);

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Correct recording of what was parsed.
   */
  public void testParseFile2() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "var yes2 = goog.require('yes2');\n"
      + "var C = goog.require(\"a.b.C\");\n"
      + "let {D, E} = goog.require('a.b.d');";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"),
        ImmutableList.of("yes2", "a.b.C", "a.b.d"),
        ImmutableMap.of("module", "goog"));

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Correct recording of what was parsed.
   */
  public void testParseFile3() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"),
        ImmutableList.of("yes2", "a.b.C", "a.b.d"),
        ImmutableMap.of("module", "goog"));

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Correct recording of what was parsed.
   */
  public void testParseWrappedGoogModule() {
    String contents = ""
      + "goog.loadModule(function(){\"use strict\";goog.module('yes1');\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");});";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"),
        ImmutableList.of("yes2", "a.b.C", "a.b.d"),
        ImmutableMap.<String, String>of()); // wrapped modules aren't marked as modules

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  // TODO(sdh): Add a test for import with .js suffix once #1897 is fixed.

  /**
   * Tests:
   *  -ES6 modules parsed correctly, particularly the various formats.
   */
  public void testParseEs6Module() {
    String contents = ""
        + "import def, {yes2} from './yes2';\n"
        + "import C from './a/b/C';\n"
        + "import * as d from './a/b/d';\n"
        + "import \"./dquote\";\n"
        + "export * from './exported';\n";

    DependencyInfo expected = new SimpleDependencyInfo("a.js", "b.js",
        ImmutableList.of("module$b"),
        ImmutableList.of(
            "module$yes2", "module$a$b$C", "module$a$b$d", "module$dquote", "module$exported"),
        ImmutableMap.of("module", "es6"));

    DependencyInfo result = parser.parseFile("b.js", "a.js", contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Relative paths resolved correctly.
   */
  public void testParseEs6Module2() {
    String contents = ""
        + "import './x';\n"
        + "import '../y';\n"
        + "import '../a/z';\n"
        + "import '../c/w';\n";

    DependencyInfo expected =
        new SimpleDependencyInfo(
            "../../a/b.js",
            "/foo/bar/a/b.js",
            ImmutableList.of("module$foo$bar$a$b"),
            ImmutableList.of(
                "module$foo$bar$a$x", "module$foo$bar$y",
                "module$foo$bar$a$z", "module$foo$bar$c$w"),
            ImmutableMap.of("module", "es6"));

    DependencyInfo result = parser.parseFile("/foo/bar/a/b.js", "../../a/b.js", contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Handles goog.require and import 'goog:...'.
   */
  public void testParseEs6Module3() {
    String contents = ""
        + "import 'goog:foo.bar.baz';\n"
        + "goog.require('baz.qux');\n";

    DependencyInfo expected = new SimpleDependencyInfo("b.js", "a.js",
        ImmutableList.of("module$a"),
        ImmutableList.of("foo.bar.baz", "baz.qux"),
        ImmutableMap.of("module", "es6"));

    DependencyInfo result = parser.parseFile("a.js", "b.js", contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -setModuleLoader taken into account
   */
  public void testParseEs6Module4() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("/foo"),
            ImmutableList.<DependencyInfo>of(),
            ModuleLoader.ResolutionMode.LEGACY);

    String contents = ""
        + "import './a';\n"
        + "import './qux/b';\n"
        + "import '../closure/c';\n"
        + "import '../closure/d/e';\n"
        + "import '../../corge/f';\n";

    DependencyInfo expected = new SimpleDependencyInfo("../bar/baz.js", "/foo/js/bar/baz.js",
        ImmutableList.of("module$js$bar$baz"),
        ImmutableList.of(
            "module$js$bar$a", "module$js$bar$qux$b", "module$js$closure$c",
            "module$js$closure$d$e", "module$corge$f"),
        ImmutableMap.of("module", "es6"));

    DependencyInfo result =
        parser
            .setModuleLoader(loader)
            .parseFile("/foo/js/bar/baz.js", "../bar/baz.js", contents);

    assertDeps(expected, result);
  }

  /**
   * Tests:
   *  -Shortcut mode doesn't stop at setTestOnly() or declareLegacyNamespace().
   */
  public void testNoShortcutForCommonModuleModifiers() {
    String contents = ""
      + "goog.module('yes1');\n"
      + "goog.module.declareLegacyNamespace();\n"
      + "goog.setTestOnly();\n"
      + "var yes2=goog.require('yes2');\n"
      + "var C=goog.require(\"a.b.C\");\n"
      + "const {\n  D,\n  E\n}=goog.require(\"a.b.d\");";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"),
        ImmutableList.of("yes2", "a.b.C", "a.b.d"),
        true);

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  public void testMultiplePerLine() {
    String contents = "goog.provide('yes1');goog.provide('yes2');/*"
        + "goog.provide('no1');*/goog.provide('yes3');//goog.provide('no2');";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1", "yes2", "yes3"), Collections.<String>emptyList(), false);

    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  public void testShortcutMode1() {
    // For efficiency reasons, we stop reading after the ctor.
    String contents = " // hi ! \n /* this is a comment */ "
        + "goog.provide('yes1');\n /* and another comment */ \n"
        + "goog.provide('yes2'); // include this\n"
        + "foo = function() {};\n"
        + "goog.provide('no1');";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1", "yes2"), Collections.<String>emptyList(), false);
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  public void testShortcutMode2() {
    String contents = "/** goog.provide('no1'); \n" +
        " * goog.provide('no2');\n */\n"
        + "goog.provide('yes1');\n";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"), Collections.<String>emptyList(), false);
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  public void testShortcutMode3() {
    String contents = "/**\n" +
        " * goog.provide('no1');\n */\n"
        + "goog.provide('yes1');\n";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("yes1"), Collections.<String>emptyList(), false);
    DependencyInfo result = parser.parseFile(SRC_PATH, CLOSURE_PATH, contents);

    assertDeps(expected, result);
  }

  public void testIncludeGoog1() {
    String contents = "/**\n" +
        " * the first constant in base.js\n" +
        " */\n" +
        "var COMPILED = false;\n";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("goog"), Collections.<String>emptyList(), false);
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  public void testIncludeGoog2() {
    String contents = "goog.require('bar');";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.<String>of(), ImmutableList.of("goog", "bar"), false);
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  public void testIncludeGoog3() {
    // This is pretending to provide goog, but it really doesn't.
    String contents = "goog.provide('x');\n" +
        "/**\n" +
        " * the first constant in base.js\n" +
        " */\n" +
        "var COMPILED = false;\n";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.of("x"), ImmutableList.of("goog"), false);
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  public void testIncludeGoog4() {
    String contents = "goog.addDependency('foo', [], []);\n";

    DependencyInfo expected = new SimpleDependencyInfo(CLOSURE_PATH, SRC_PATH,
        ImmutableList.<String>of(), ImmutableList.of("goog"), false);
    DependencyInfo result = parser.setIncludeGoogBase(true).parseFile(
        SRC_PATH, CLOSURE_PATH, contents);
    assertDeps(expected, result);
  }

  /** Asserts the deps match without errors */
  private void assertDeps(DependencyInfo expected, DependencyInfo actual) {
    assertThat(actual).isEqualTo(expected);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }
}
