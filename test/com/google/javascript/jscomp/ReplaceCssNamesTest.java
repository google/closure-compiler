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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.ReplaceCssNames.INVALID_USE_OF_CLASSES_OBJECT_ERROR;
import static com.google.javascript.jscomp.ReplaceCssNames.UNEXPECTED_SASS_GENERATED_CSS_TS_ERROR;
import static com.google.javascript.jscomp.ReplaceCssNames.UNEXPECTED_STRING_LITERAL_ERROR;
import static com.google.javascript.jscomp.ReplaceCssNames.UNKNOWN_SYMBOL_ERROR;
import static com.google.javascript.jscomp.ReplaceCssNames.UNKNOWN_SYMBOL_WARNING;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ReplaceCssNames.java. */
@RunWith(JUnit4.class)
public final class ReplaceCssNamesTest extends CompilerTestCase {
  /** Whether to pass the map of replacements as opposed to null */
  boolean useReplacementMap;

  /** Map of replacements to use during the test. */
  Map<String, String> replacementMap =
      new ImmutableMap.Builder<String, String>()
          .put("active", "a")
          .put("buttonbar", "b")
          .put("colorswatch", "c")
          .put("disabled", "d")
          .put("elephant", "e")
          .put("footer", "f")
          .put("goog", "g")
          .put("fooStylesBar", "fsr")
          .put("fooStylesBaz", "fsz")
          .buildOrThrow();

  Map<String, String> replacementMapFull =
      new ImmutableMap.Builder<String, String>()
          .put("long-prefix", "h")
          .put("suffix1", "i")
          .put("unrelated-word", "k")
          .put("unrelated", "l")
          .put("long-suffix", "m")
          .put("long-prefix-suffix1", "h-i")
          .buildOrThrow();

  CssRenamingMap renamingMap;
  Set<String> skiplist;

  Set<String> cssNames;

  public ReplaceCssNamesTest() {
    super(
        lines(
            DEFAULT_EXTERNS,
            "Object.prototype.getClass;",
            "goog.getCssName;",
            "goog.setCssNameMapping;"));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ReplaceCssNames(
        compiler, useReplacementMap ? renamingMap : null, cssNames::add, skiplist);
  }

  CssRenamingMap getPartialMap() {
    return new CssRenamingMap.ByPart() {
      @Override
      public String get(String value) {
        return replacementMap.get(value);
      }
    };
  }

  CssRenamingMap getFullMap() {
    return new CssRenamingMap.ByWhole() {
      @Override
      public String get(String value) {
        return replacementMapFull.get(value);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableRewriteClosureCode();
    cssNames = Sets.newHashSet();
    useReplacementMap = true;
    renamingMap = getPartialMap();
  }

  @Override
  protected int getNumRepetitions() {
    // The first pass strips the goog.getCssName even if a warning is issued,
    // such that a subsequent pass won't issue a warning.
    return 1;
  }

  @Test
  public void testSimpleValidMapping() {
    test("function f() { goog.setCssNameMapping({}); }", "function f() {}");
  }

  @Test
  public void testValidSetCssNameMapping() {
    test(
        lines(
            "goog.setCssNameMapping({foo:'bar',\"biz\":'baz'});",
            "const x = goog.getCssName('foo'),",
            "  y = goog.getCssName('biz'),",
            "  z = goog.getCssName('foo-biz');"),
        "const x = 'bar', y = 'baz', z = 'bar-baz';");
  }

  @Test
  public void testValidSetCssNameMapping_byPart() {
    test(
        lines(
            "goog.setCssNameMapping({foo:'bar',\"biz\":'baz'}, 'BY_PART');",
            "const x = goog.getCssName('foo'),",
            "  y = goog.getCssName('biz'),",
            "  z = goog.getCssName('foo-biz');"),
        "const x = 'bar', y = 'baz', z = 'bar-baz';");
  }

  @Test
  public void testValidSetCssNameMapping_byWhole() {
    test(
        lines(
            "goog.setCssNameMapping({foo:'bar',\"biz\":'baz'}, 'BY_WHOLE');",
            "const x = goog.getCssName('foo'),",
            "  y = goog.getCssName('biz'),",
            "  z = goog.getCssName('foo-biz');"),
        "const x = 'bar', y = 'baz', z = 'foo-biz';");

    test(
        lines(
            "goog.setCssNameMapping({foo:'bar',\"biz\":'baz','biz-foo':'baz-bar'}, 'BY_WHOLE');",
            "const x = goog.getCssName('foo'),",
            "  y = goog.getCssName('biz'),",
            "  z = goog.getCssName('foo-biz');"),
        "const x = 'bar', y = 'baz', z = 'foo-biz';");
  }

  @Test
  public void testDoNotUseReplacementMap() {
    useReplacementMap = false;
    test(
        "var x = goog.getCssName('goog-footer-active')", //
        "var x = 'goog-footer-active'");
    test(
        "el.className = goog.getCssName('goog-colorswatch-disabled')",
        "el.className = 'goog-colorswatch-disabled'");
    test(
        "setClass(goog.getCssName('active-buttonbar'))", //
        "setClass('active-buttonbar')");
    assertThat(cssNames)
        .containsExactly("goog-footer-active", "goog-colorswatch-disabled", "active-buttonbar");
  }

  @Test
  public void testOneArgWithUnknownStringLiterals() {
    test(
        "var x = goog.getCssName('unknown')", //
        "var x = 'unknown'",
        warning(UNKNOWN_SYMBOL_WARNING));
    test(
        "el.className = goog.getCssName('ooo')",
        "el.className = 'ooo'",
        warning(UNKNOWN_SYMBOL_WARNING));
    test(
        "setClass(goog.getCssName('ab'))", //
        "setClass('ab')",
        warning(UNKNOWN_SYMBOL_WARNING));
  }

  private void oneArgWithSimpleStringLiterals() {
    test(
        "var x = goog.getCssName('buttonbar')", //
        "var x = 'b'");
    test(
        "el.className = goog.getCssName('colorswatch')", //
        "el.className = 'c'");
    test(
        "setClass(goog.getCssName('elephant'))", //
        "setClass('e')");
    assertThat(cssNames).containsExactly("buttonbar", "colorswatch", "elephant");
  }

  @Test
  public void testOneArgWithSimpleStringLiterals() {
    oneArgWithSimpleStringLiterals();
  }

  private void oneArgWithCompositeClassNames() {
    test(
        "var x = goog.getCssName('goog-footer-active')", //
        "var x = 'g-f-a'");
    test(
        "el.className = goog.getCssName('goog-colorswatch-disabled')", //
        "el.className = 'g-c-d'");
    test(
        "setClass(goog.getCssName('active-buttonbar'))", //
        "setClass('a-b')");
    assertThat(cssNames)
        .containsExactly("goog-footer-active", "goog-colorswatch-disabled", "active-buttonbar");
  }

  @Test
  public void testOneArgWithCompositeClassNames() {
    oneArgWithCompositeClassNames();
  }

  @Test
  public void testOneArgWithCompositeClassNamesFull() {
    renamingMap = getFullMap();

    test(
        "var x = goog.getCssName('long-prefix')", //
        "var x = 'h'");
    test(
        "var x = goog.getCssName('long-prefix-suffix1')", //
        "var x = 'h-i'");
    test(
        "var x = goog.getCssName('unrelated')", //
        "var x = 'l'");
    test(
        "var x = goog.getCssName('unrelated-word')", //
        "var x = 'k'");
  }

  @Test
  public void testOneArgWithCompositeClassNamesWithUnknownParts() {
    test(
        "var x = goog.getCssName('goog-header-active')",
        "var x = 'goog-header-active'",
        warning(UNKNOWN_SYMBOL_WARNING));
    test(
        "el.className = goog.getCssName('goog-colorswatch-focussed')",
        "el.className = 'goog-colorswatch-focussed'",
        warning(UNKNOWN_SYMBOL_WARNING));
    test(
        "setClass(goog.getCssName('inactive-buttonbar'))",
        "setClass('inactive-buttonbar')",
        warning(UNKNOWN_SYMBOL_WARNING));
  }

  @Test
  public void testTwoArgsWithStringLiterals() {
    testError("var x = goog.getCssName('header', 'active')", UNEXPECTED_STRING_LITERAL_ERROR);
    testError(
        "el.className = goog.getCssName('footer', window)",
        ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError(
        "setClass(goog.getCssName('buttonbar', 'disabled'))", UNEXPECTED_STRING_LITERAL_ERROR);
  }

  @Test
  public void testNestedCall() {
    testError(
        "setClass(goog.getCssName(goog.getCssName('buttonbar'), 'active'))",
        ReplaceCssNames.NESTED_CALL_ERROR);
  }

  @Test
  public void testTwoArsWithVariableFirstArg() {
    test(
        "var x = goog.getCssName(baseClass, 'active')", //
        "var x = baseClass + '-a'");
    test(
        "el.className = goog.getCssName((new Object).getClass(), 'disabled')",
        "el.className = (new Object).getClass() + '-d'");
    test(
        "setClass(goog.getCssName(BASE_CLASS, 'disabled'))", //
        "setClass(BASE_CLASS + '-d')");
  }

  @Test
  public void testTwoArgsWithVariableFirstArgFull() {
    renamingMap = getFullMap();

    test(
        "var x = goog.getCssName(baseClass, 'long-suffix')", //
        "var x = baseClass + '-m'");
  }

  @Test
  public void testZeroArguments() {
    testError("goog.getCssName()", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
  }

  @Test
  public void testManyArguments() {
    testError("goog.getCssName('a', 'b', 'c')", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
    testError("goog.getCssName('a', 'b', 'c', 'd')", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
    testError(
        "goog.getCssName('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i')",
        ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
  }

  @Test
  public void testNonStringArgument() {
    testError("goog.getCssName(window);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(555);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName([]);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName({});", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(null);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(undefined);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);

    testError("goog.getCssName(baseClass, window);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(baseClass, 555);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(baseClass, []);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(baseClass, {});", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName(baseClass, null);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError(
        "goog.getCssName(baseClass, undefined);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName('foo', 3);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
  }

  @Test
  public void testNoSymbolMapStripsCallAndDoesntIssueWarnings() {
    String input = "[goog.getCssName('test'), goog.getCssName(base, 'active')]";
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.initOptions(options);
    ErrorManager errorMan =
        new BasicErrorManager() {
          @Override
          protected void printSummary() {}

          @Override
          public void println(CheckLevel level, JSError error) {}
        };
    compiler.setErrorManager(errorMan);
    Node root = compiler.parseTestCode(input);
    useReplacementMap = false;
    ReplaceCssNames replacer = new ReplaceCssNames(compiler, null, unused -> {}, null);
    replacer.process(null, root);
    assertThat(compiler.toSource(root)).isEqualTo("[\"test\",base+\"-active\"]");
    assertWithMessage("There should be no errors").that(errorMan.getErrorCount()).isEqualTo(0);
    assertWithMessage("There should be no warnings").that(errorMan.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testWhitelistByPart() {
    skiplist = ImmutableSet.of("goog", "elephant");
    test(
        "var x = goog.getCssName('goog')", //
        "var x = 'goog'");
    test(
        "var x = goog.getCssName('elephant')", //
        "var x = 'elephant'");
    // Whitelisting happens before splitting, not after.
    test(
        "var x = goog.getCssName('goog-elephant')", //
        "var x = 'g-e'");
  }

  @Test
  public void testWhitelistByWhole() {
    skiplist = ImmutableSet.of("long-prefix");
    renamingMap = getFullMap();
    test(
        "var x = goog.getCssName('long-prefix')", //
        "var x = 'long-prefix'");
  }

  @Test
  public void testWhitelistWithDashes() {
    skiplist = ImmutableSet.of("goog-elephant");
    test(
        "var x = goog.getCssName('goog')", //
        "var x = 'g'");
    test(
        "var x = goog.getCssName('elephant')", //
        "var x = 'e'");
    test(
        "var x = goog.getCssName('goog-elephant')", //
        "var x = 'goog-elephant'");
  }

  @Test
  public void testVariableReferencesToCssClasses() {
    SourceFile cssVarsDefinition =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                // These files will be automatically generated, so we know the
                // exported 'classes' property will always be declared like this.
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': goog.getCssName('fooStylesBar'),",
                "  'Baz': goog.getCssName('fooStylesBaz'),",
                "}"));
    SourceFile cssVarsExpected =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': 'fsr',",
                "  'Baz': 'fsz',",
                "}"));
    SourceFile importer =
        SourceFile.fromCode(
            "foo/importer.closure.js",
            lines(
                "goog.module('foo.importer');",
                "/** @type {string} */",
                "const foo_styles_css = goog.require('foo.styles$2ecss')",
                // Even when the original TS import was
                // `import {classes as foo} from './path/to/file';`
                // tsickle will convert references to `foo` into a fully qualified
                // name like this rather than creating an alias variable named `foo`.
                "var x = foo_styles_css.classes.Bar"));
    test(srcs(cssVarsDefinition, importer), expected(cssVarsExpected, importer));
    assertThat(cssNames).containsExactly("fooStylesBar");
  }

  @Test
  public void testMixedVariableAndStringReferences() {
    SourceFile cssVarsDefinition =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': goog.getCssName('fooStylesBar'),",
                "  'Baz': goog.getCssName('fooStylesBaz'),",
                "}"));
    SourceFile cssVarsExpected =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': 'fsr',",
                "  'Baz': 'fsz',",
                "}"));
    SourceFile importer =
        SourceFile.fromCode(
            "foo/importer.closure.js",
            lines(
                "goog.module('foo.importer');",
                "/** @type {string} */",
                "const foo_styles_css = goog.require('foo.styles$2ecss');",
                "var x = foo_styles_css.classes.Bar;",
                "var y = goog.getCssName('active');"));
    SourceFile importerExpected =
        SourceFile.fromCode(
            "foo/importer.closure.js",
            lines(
                "goog.module('foo.importer');",
                "/** @type {string} */",
                "const foo_styles_css = goog.require('foo.styles$2ecss');",
                "var x = foo_styles_css.classes.Bar;",
                "var y = 'a';"));
    test(srcs(cssVarsDefinition, importer), expected(cssVarsExpected, importerExpected));
    assertThat(cssNames).containsExactly("fooStylesBar", "active");
  }

  @Test
  public void testIgnoreReferencesInCssTsFiles() {
    SourceFile cssVarsDefinition =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': goog.getCssName('fooStylesBar'),",
                "  'Baz': goog.getCssName('fooStylesBaz'),",
                "}"));
    SourceFile cssVarsExpected =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': 'fsr',",
                "  'Baz': 'fsz',",
                "}"));
    test(srcs(cssVarsDefinition), expected(cssVarsExpected));
    assertThat(cssNames).isEmpty();
  }

  @Test
  public void testUnexpectedSassGeneratedCssTsError() {
    SourceFile nonCssClosureFile =
        SourceFile.fromCode(
            "foo/invalid.closure.js",
            lines(
                "/**",
                " * @fileoverview contains an invalid sassGeneratedCssTs annotation",
                // This annotation is invalid since this is not a .css.closure.js file
                " * @sassGeneratedCssTs",
                " */",
                "var x = 'foo';"));
    test(srcs(nonCssClosureFile), error(UNEXPECTED_SASS_GENERATED_CSS_TS_ERROR));
  }

  @Test
  public void testInvalidUseOfClassesObjectError() {
    SourceFile cssVarsDefinition =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': goog.getCssName('fooStylesBar'),",
                "  'Baz': goog.getCssName('fooStylesBaz'),",
                "}"));
    SourceFile importer =
        SourceFile.fromCode(
            "foo/importer.closure.js",
            lines(
                "goog.module('foo.importer');",
                "/** @type {string} */",
                "const foo_styles_css = goog.require('foo.styles$2ecss')",
                "var x = foo_styles_css.classes;"));
    test(srcs(cssVarsDefinition, importer), error(INVALID_USE_OF_CLASSES_OBJECT_ERROR));
  }

  @Test
  public void testUnknownSymbolError() {
    SourceFile cssVarsDefinition =
        SourceFile.fromCode(
            "foo/styles.css.closure.js",
            lines(
                "/**",
                " * @fileoverview generated from foo/styles.css",
                " * @sassGeneratedCssTs",
                " */",
                "goog.module('foo.styles$2ecss');",
                "/** @type {{Bar: string, Baz: string}} */",
                "exports.classes = {",
                "  'Bar': goog.getCssName('fooStylesBar'),",
                "  'Baz': goog.getCssName('fooStylesBaz'),",
                "}"));
    SourceFile importer =
        SourceFile.fromCode(
            "foo/importer.closure.js",
            lines(
                "goog.module('foo.importer');",
                "/** @type {string} */",
                "const foo_styles_css = goog.require('foo.styles$2ecss')",
                "var x = foo_styles_css.classes.Quux;"));
    test(srcs(cssVarsDefinition, importer), error(UNKNOWN_SYMBOL_ERROR));
  }
}
