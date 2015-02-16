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

import static com.google.javascript.jscomp.ReplaceCssNames.UNEXPECTED_STRING_LITERAL_ERROR;
import static com.google.javascript.jscomp.ReplaceCssNames.UNKNOWN_SYMBOL_WARNING;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;

import java.util.Map;
import java.util.Set;

/**
 * Tests for ReplaceCssNames.java.
 *
 */
public class ReplaceCssNamesTest extends CompilerTestCase {
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
    .build();

  Map<String, String> replacementMapFull =
      new ImmutableMap.Builder<String, String>()
      .put("long-prefix", "h")
      .put("suffix1", "i")
      .put("unrelated-word", "k")
      .put("unrelated", "l")
      .put("long-suffix", "m")
      .put("long-prefix-suffix1", "h-i")
      .build();

  CssRenamingMap renamingMap;
  Set<String> whitelist;

  Map<String, Integer> cssNames;

  public ReplaceCssNamesTest() {
  }

  @Override protected CompilerPass getProcessor(Compiler compiler) {
    return new ReplaceCssNames(compiler, cssNames, whitelist) {
      @Override
      protected CssRenamingMap getCssRenamingMap() {
        return useReplacementMap ? renamingMap : null;
      }
    };
  }

  protected CssRenamingMap getPartialMap() {
    CssRenamingMap map = new CssRenamingMap.ByPart() {
      @Override public String get(String value) {
        return replacementMap.get(value);
      }
    };
    return map;
  }

  protected CssRenamingMap getFullMap() {
    return new CssRenamingMap.ByWhole() {
      @Override public String get(String value) {
        return replacementMapFull.get(value);
      }
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    super.enableLineNumberCheck(true);
    cssNames = Maps.newHashMap();
    useReplacementMap = true;
    renamingMap = getPartialMap();
  }

  @Override
  protected int getNumRepetitions() {
    // The first pass strips the goog.getCssName even if a warning is issued,
    // such that a subsequent pass won't issue a warning.
    return 1;
  }

  public void testDoNotUseReplacementMap() {
    useReplacementMap = false;
    test("var x = goog.getCssName('goog-footer-active')",
         "var x = 'goog-footer-active'");
    test("el.className = goog.getCssName('goog-colorswatch-disabled')",
         "el.className = 'goog-colorswatch-disabled'");
    test("setClass(goog.getCssName('active-buttonbar'))",
         "setClass('active-buttonbar')");
    Map<String, Integer> expected =
        new ImmutableMap.Builder<String, Integer>()
        .put("goog", 2)
        .put("footer", 1)
        .put("active", 2)
        .put("colorswatch", 1)
        .put("disabled", 1)
        .put("buttonbar", 1)
        .build();
    assertEquals(expected, cssNames);
  }

  public void testOneArgWithUnknownStringLiterals() {
    test("var x = goog.getCssName('unknown')",
         "var x = 'unknown'", null, UNKNOWN_SYMBOL_WARNING);
    test("el.className = goog.getCssName('ooo')",
         "el.className = 'ooo'", null, UNKNOWN_SYMBOL_WARNING);
    test("setClass(goog.getCssName('ab'))",
         "setClass('ab')", null, UNKNOWN_SYMBOL_WARNING);
  }

  public void testOneArgWithSimpleStringLiterals() {
    test("var x = goog.getCssName('buttonbar')",
         "var x = 'b'");
    test("el.className = goog.getCssName('colorswatch')",
         "el.className = 'c'");
    test("setClass(goog.getCssName('elephant'))",
         "setClass('e')");
    Map<String, Integer> expected =
        new ImmutableMap.Builder<String, Integer>()
        .put("buttonbar", 1)
        .put("colorswatch", 1)
        .put("elephant", 1)
        .build();
    assertEquals(expected, cssNames);
  }

  public void testOneArgWithCompositeClassNames() {
    test("var x = goog.getCssName('goog-footer-active')",
         "var x = 'g-f-a'");
    test("el.className = goog.getCssName('goog-colorswatch-disabled')",
         "el.className = 'g-c-d'");
    test("setClass(goog.getCssName('active-buttonbar'))",
         "setClass('a-b')");
    Map<String, Integer> expected =
        new ImmutableMap.Builder<String, Integer>()
        .put("goog", 2)
        .put("footer", 1)
        .put("active", 2)
        .put("colorswatch", 1)
        .put("disabled", 1)
        .put("buttonbar", 1)
        .build();
    assertEquals(expected, cssNames);
  }

  public void testOneArgWithCompositeClassNamesFull() {
    renamingMap = getFullMap();

    test("var x = goog.getCssName('long-prefix')",
         "var x = 'h'");
    test("var x = goog.getCssName('long-prefix-suffix1')",
         "var x = 'h-i'");
    test("var x = goog.getCssName('unrelated')",
         "var x = 'l'");
    test("var x = goog.getCssName('unrelated-word')",
         "var x = 'k'");
  }

  public void testOneArgWithCompositeClassNamesWithUnknownParts() {
    test("var x = goog.getCssName('goog-header-active')",
         "var x = 'goog-header-active'", null, UNKNOWN_SYMBOL_WARNING);
    test("el.className = goog.getCssName('goog-colorswatch-focussed')",
         "el.className = 'goog-colorswatch-focussed'",
         null, UNKNOWN_SYMBOL_WARNING);
    test("setClass(goog.getCssName('inactive-buttonbar'))",
        "setClass('inactive-buttonbar')", null, UNKNOWN_SYMBOL_WARNING);
  }

  public void testTwoArgsWithStringLiterals() {
    testError("var x = goog.getCssName('header', 'active')", UNEXPECTED_STRING_LITERAL_ERROR);
    testError("el.className = goog.getCssName('footer', window)",
        ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError(
        "setClass(goog.getCssName('buttonbar', 'disabled'))", UNEXPECTED_STRING_LITERAL_ERROR);
    testError("setClass(goog.getCssName(goog.getCssName('buttonbar'), 'active'))",
        UNEXPECTED_STRING_LITERAL_ERROR);
  }

  public void testTwoArsWithVariableFirstArg() {
    test("var x = goog.getCssName(baseClass, 'active')",
         "var x = baseClass + '-a'");
    test("el.className = goog.getCssName(this.getClass(), 'disabled')",
         "el.className = this.getClass() + '-d'");
    test("setClass(goog.getCssName(BASE_CLASS, 'disabled'))",
         "setClass(BASE_CLASS + '-d')");
  }

  public void testTwoArgsWithVariableFirstArgFull() {
    renamingMap = getFullMap();

    test("var x = goog.getCssName(baseClass, 'long-suffix')",
         "var x = baseClass + '-m'");
  }

  public void testZeroArguments() {
    testError("goog.getCssName()", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
  }

  public void testManyArguments() {
    testError("goog.getCssName('a', 'b', 'c')", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
    testError("goog.getCssName('a', 'b', 'c', 'd')", ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
    testError("goog.getCssName('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i')",
        ReplaceCssNames.INVALID_NUM_ARGUMENTS_ERROR);
  }

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
    testError("goog.getCssName(baseClass, undefined);",
        ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
    testError("goog.getCssName('foo', 3);", ReplaceCssNames.STRING_LITERAL_EXPECTED_ERROR);
  }

  public void testNoSymbolMapStripsCallAndDoesntIssueWarnings() {
    String input = "[goog.getCssName('test'), goog.getCssName(base, 'active')]";
    Compiler compiler = new Compiler();
    ErrorManager errorMan = new BasicErrorManager() {
      @Override protected void printSummary() {}
      @Override public void println(CheckLevel level, JSError error) {}
    };
    compiler.setErrorManager(errorMan);
    Node root = compiler.parseTestCode(input);
    useReplacementMap = false;
    ReplaceCssNames replacer = new ReplaceCssNames(compiler, null, null);
    replacer.process(null, root);
    assertEquals("[\"test\",base+\"-active\"]", compiler.toSource(root));
    assertEquals("There should be no errors", 0, errorMan.getErrorCount());
    assertEquals("There should be no warnings", 0, errorMan.getWarningCount());
  }

  public void testWhitelistByPart() {
    whitelist = ImmutableSet.of("goog", "elephant");
    test("var x = goog.getCssName('goog')",
         "var x = 'goog'");
    test("var x = goog.getCssName('elephant')",
         "var x = 'elephant'");
    // Whitelisting happens before splitting, not after.
    test("var x = goog.getCssName('goog-elephant')",
         "var x = 'g-e'");
  }

  public void testWhitelistByWhole() {
    whitelist = ImmutableSet.of("long-prefix");
    renamingMap = getFullMap();
    test("var x = goog.getCssName('long-prefix')",
         "var x = 'long-prefix'");
  }

  public void testWhitelistWithDashes() {
    whitelist = ImmutableSet.of("goog-elephant");
    test("var x = goog.getCssName('goog')",
        "var x = 'g'");
    test("var x = goog.getCssName('elephant')",
        "var x = 'e'");
    test("var x = goog.getCssName('goog-elephant')",
        "var x = 'goog-elephant'");
  }

}
