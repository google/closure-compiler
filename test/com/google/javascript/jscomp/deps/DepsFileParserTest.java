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
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DepsFileParser}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
@RunWith(JUnit4.class)
public final class DepsFileParserTest {

  private DepsFileParser parser;
  private ErrorManager errorManager;
  private static final String SRC_PATH = "/path/1.js";

  @Before
  public void setUp() {
    errorManager = new PrintStreamErrorManager(System.err);
    parser = new DepsFileParser(errorManager);
    parser.setShortcutMode(true);
  }

  /**
   * Tests:
   *
   * <ul>
   *   <li>Parsing of comments,
   *   <li>Parsing of different styles of quotes,
   *   <li>Parsing of empty arrays,
   *   <li>Parsing of non-empty arrays,
   *   <li>Correct recording of what was parsed.
   * </ul>
   */
  @Test
  public void testGoodParse() {
    final String contents =
        "/*"
            + "goog.addDependency('no1', [], []);*//*\n"
            + "goog.addDependency('no2', [ ], [ ]);\n"
            + "*/goog.addDependency('yes1', [], []);\n"
            + "/* blah */goog.addDependency(\"yes2\", [], [])/* blah*/\n"
            + "goog.addDependency('yes3', ['a','b'], ['c']); "
            + "// goog.addDependency('no3', [], []);\n"
            + "// goog.addDependency('no4', [], []);\n"
            + "goog.addDependency(\"yes4\", [], [ \"a\",'b' , 'c' ]); //no new line at EOF";

    List<DependencyInfo> result = parser.parseFile(SRC_PATH, contents);
    ImmutableList<DependencyInfo> expected =
        ImmutableList.of(
            SimpleDependencyInfo.builder("yes1", SRC_PATH).build(),
            SimpleDependencyInfo.builder("yes2", SRC_PATH).build(),
            SimpleDependencyInfo.builder("yes3", SRC_PATH)
                .setProvides(ImmutableList.of("a", "b"))
                .setRequires(Require.parsedFromDeps("c"))
                .build(),
            SimpleDependencyInfo.builder("yes4", SRC_PATH)
                .setRequires(
                    Require.parsedFromDeps("a"),
                    Require.parsedFromDeps("b"),
                    Require.parsedFromDeps("c"))
                .build());

    assertThat(result).isEqualTo(expected);
    assertThat(errorManager.getErrorCount()).isEqualTo(0);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testTooFewArgs() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', []);");
    assertThat(errorManager.getErrorCount()).isEqualTo(1);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testTooManyArgs1() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', [], [], []);");
    assertThat(errorManager.getErrorCount()).isEqualTo(1);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testTooManyArgs2() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', [], [], false, []);");
    assertThat(errorManager.getErrorCount()).isEqualTo(1);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testTooManyArgs3() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', [], [], {}, []);");
    assertThat(errorManager.getErrorCount()).isEqualTo(1);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testBadLoadFlagsSyntax() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', [], [], {module: 'goog'});");
    assertThat(errorManager.getErrorCount()).isEqualTo(1);
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testGoogModule() {
    List<DependencyInfo> result = parser.parseFile(SRC_PATH,
        "goog.addDependency('yes1', [], [], true);\n" +
        "goog.addDependency('yes2', [], [], false);\n");
    ImmutableList<DependencyInfo> expected =
        ImmutableList.of(
            SimpleDependencyInfo.builder("yes1", SRC_PATH).setGoogModule(true).build(),
            SimpleDependencyInfo.builder("yes2", SRC_PATH).build());
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testEs6Module() {
    List<DependencyInfo> result =
        parser.parseFile(
            SRC_PATH,
            "goog.addDependency('path/from/closure.js', [], ['nexttoclosure.js'], "
                + "{'module':'es6'});\n"
                + "goog.addDependency('nexttoclosure.js', [], [], {'module':'es6'});\n");
    ImmutableList<DependencyInfo> expected =
        ImmutableList.of(
            SimpleDependencyInfo.builder("path/from/closure.js", SRC_PATH)
                .setLoadFlags(ImmutableMap.of("module", "es6"))
                .setProvides(ImmutableList.of("path/from/closure.js"))
                .setRequires(Require.parsedFromDeps("nexttoclosure.js"))
                .build(),
            SimpleDependencyInfo.builder("nexttoclosure.js", SRC_PATH)
                .setLoadFlags(ImmutableMap.of("module", "es6"))
                .setProvides(ImmutableList.of("nexttoclosure.js"))
                .build());
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testLoadFlags() {
    List<DependencyInfo> result = parser.parseFile(SRC_PATH, ""
        + "goog.addDependency('yes1', [], [], {'module': 'goog'});\n"
        + "goog.addDependency('yes2', [], [], {\"lang\": \"es6\"});\n"
        + "goog.addDependency('yes3', [], [], {});\n");
    ImmutableList<DependencyInfo> expected =
        ImmutableList.of(
            SimpleDependencyInfo.builder("yes1", SRC_PATH)
                .setLoadFlags(ImmutableMap.of("module", "goog"))
                .build(),
            SimpleDependencyInfo.builder("yes2", SRC_PATH)
                .setLoadFlags(ImmutableMap.of("lang", "es6"))
                .build(),
            SimpleDependencyInfo.builder("yes3", SRC_PATH).build());
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testShortcutMode() {
    List<DependencyInfo> result = parser.parseFile(SRC_PATH,
        "goog.addDependency('yes1', [], []); \n" +
        "foo();\n" +
        "goog.addDependency('no1', [], []);");
    ImmutableList<DependencyInfo> expected = ImmutableList.<DependencyInfo>of(
        SimpleDependencyInfo.builder("yes1", SRC_PATH)
            .build());
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testNoShortcutMode() {
    parser.setShortcutMode(false);
    List<DependencyInfo> result = parser.parseFile(SRC_PATH,
        "goog.addDependency('yes1', [], []); \n" +
        "foo();\n" +
        "goog.addDependency('yes2', [], []);");
    ImmutableList<DependencyInfo> expected =
        ImmutableList.of(
            SimpleDependencyInfo.builder("yes1", SRC_PATH).build(),
            SimpleDependencyInfo.builder("yes2", SRC_PATH).build());
    assertThat(result).isEqualTo(expected);
  }
}
