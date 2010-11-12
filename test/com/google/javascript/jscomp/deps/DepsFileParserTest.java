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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link DepsFileParser}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class DepsFileParserTest extends TestCase {

  private DepsFileParser parser;
  private ErrorManager errorManager;
  private static final String SRC_PATH = "/path/1.js";
  private final List<String> EMPTY = Collections.emptyList();

  @Override
  public void setUp() {
    errorManager = new PrintStreamErrorManager(System.err);
    parser = new DepsFileParser(errorManager);
    parser.setShortcutMode(true);
  }

  /**
   * Tests:
   *  -Parsing of comments,
   *  -Parsing of different styles of quotes,
   *  -Parsing of empty arrays,
   *  -Parsing of non-empty arrays,
   *  -Correct recording of what was parsed.
   */
  public void testGoodParse() {
    final String CONTENTS = "/*"
        + "goog.addDependency('no1', [], []);*//*\n"
        + "goog.addDependency('no2', [ ], [ ]);\n"
        + "*/goog.addDependency('yes1', [], []);\n"
        + "/* blah */goog.addDependency(\"yes2\", [], [])/* blah*/\n"
        + "goog.addDependency('yes3', ['a','b'], ['c']); // goog.addDependency('no3', [], []);\n"
        + "// goog.addDependency('no4', [], []);\n"
        + "goog.addDependency(\"yes4\", [], [ \"a\",'b' , 'c' ]); //no new line at eof";

    List<DependencyInfo> result = parser.parseFile(SRC_PATH, CONTENTS);
    ImmutableList<DependencyInfo> EXPECTED = ImmutableList.<DependencyInfo>of(
        new SimpleDependencyInfo("yes1", SRC_PATH, EMPTY, EMPTY),
        new SimpleDependencyInfo("yes2", SRC_PATH, EMPTY, EMPTY),
        new SimpleDependencyInfo(
            "yes3", SRC_PATH, ImmutableList.of("a", "b"), ImmutableList.of("c")),
        new SimpleDependencyInfo(
            "yes4", SRC_PATH, EMPTY, ImmutableList.of("a", "b", "c"))
    );

    assertEquals(EXPECTED, result);
    assertEquals(0, errorManager.getErrorCount());
    assertEquals(0, errorManager.getWarningCount());
  }

  public void testTooFewArgs() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', []);");
    assertEquals(1, errorManager.getErrorCount());
    assertEquals(0, errorManager.getWarningCount());
  }

  public void testTooManyArgs() {
    parser.parseFile(SRC_PATH, "goog.addDependency('a', [], [], []);");
    assertEquals(1, errorManager.getErrorCount());
    assertEquals(0, errorManager.getWarningCount());
  }

  public void testShortcutMode() {
    List<DependencyInfo> result = parser.parseFile(SRC_PATH,
        "goog.addDependency('yes1', [], []); \n" +
        "foo();\n" +
        "goog.addDependency('no1', [], []);");
    ImmutableList<DependencyInfo> EXPECTED = ImmutableList.<DependencyInfo>of(
        new SimpleDependencyInfo("yes1", SRC_PATH, EMPTY, EMPTY));
    assertEquals(EXPECTED, result);
  }

  public void testNoShortcutMode() {
    parser.setShortcutMode(false);
    List<DependencyInfo> result = parser.parseFile(SRC_PATH,
        "goog.addDependency('yes1', [], []); \n" +
        "foo();\n" +
        "goog.addDependency('yes2', [], []);");
    ImmutableList<DependencyInfo> EXPECTED = ImmutableList.<DependencyInfo>of(
        new SimpleDependencyInfo("yes1", SRC_PATH, EMPTY, EMPTY),
        new SimpleDependencyInfo("yes2", SRC_PATH, EMPTY, EMPTY));
    assertEquals(EXPECTED, result);
  }
}
