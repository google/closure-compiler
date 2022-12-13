/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.StrictMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParsingUtilTest {
  private static final Correspondence<Node, String> NAME_EQUALITY =
      Correspondence.transforming((n) -> n.isName() ? n.getString() : null, "is NAME with string");

  @Test
  public void testNamePatternDeclaresName() {
    assertPatternDeclaresNames("x", ImmutableSet.of("x"));

    assertThrows(AssertionError.class, () -> assertPatternDeclaresNames("x", ImmutableSet.of("y")));
  }

  @Test
  public void testEmptyPatternDeclaresNoNames() {
    assertPatternDeclaresNames("[]", ImmutableSet.of());
    assertPatternDeclaresNames("{}", ImmutableSet.of());
    assertPatternDeclaresNames("[,]", ImmutableSet.of());
  }

  @Test
  public void testNamesDeclaredInArrayPattern() {
    assertPatternDeclaresNames("[x]", ImmutableSet.of("x"));
    assertPatternDeclaresNames("[x, y]", ImmutableSet.of("x", "y"));
    assertPatternDeclaresNames("[x, y, ...z]", ImmutableSet.of("x", "y", "z"));
    assertPatternDeclaresNames("[...z]", ImmutableSet.of("z"));
    assertPatternDeclaresNames("[[x, y]]", ImmutableSet.of("x", "y"));
    assertPatternDeclaresNames("[...[x, y]]", ImmutableSet.of("x", "y"));
    assertPatternDeclaresNames("[[x], [y]]", ImmutableSet.of("x", "y"));
  }

  @Test
  public void testNamesDeclaredInObjectPattern() {
    assertPatternDeclaresNames("{x}", ImmutableSet.of("x"));
    assertPatternDeclaresNames("{x, y}", ImmutableSet.of("x", "y"));
    assertPatternDeclaresNames("{x, y, ...z}", ImmutableSet.of("x", "y", "z"));
    assertPatternDeclaresNames("{...z}", ImmutableSet.of("z"));
    assertPatternDeclaresNames("{x: y}", ImmutableSet.of("y"));
    assertPatternDeclaresNames("{[x]: y}", ImmutableSet.of("y"));
    assertPatternDeclaresNames("{[x()]: y}", ImmutableSet.of("y"));
  }

  @Test
  public void testNamesDeclaredInDefaultValue() {
    assertPatternDeclaresNames("x = 1", ImmutableSet.of("x"));
    assertPatternDeclaresNames("[x, y] = [1, 2]", ImmutableSet.of("x", "y"));
    assertPatternDeclaresNames("{x, y} = {x: 1, y: 2}", ImmutableSet.of("x", "y"));
  }

  @Test
  public void testNamesDeclaredInRest() {
    assertPatternDeclaresNames("...x", ImmutableSet.of("x"));
    assertPatternDeclaresNames("...[x, y, z]", ImmutableSet.of("x", "y", "z"));
  }

  @Test
  public void testNamesDeclaredInParamList() {
    assertPatternDeclaresNames("x, y, z", ImmutableSet.of("x", "y", "z"));
    assertPatternDeclaresNames(
        "[x1, x2], {y1, y2}, z = 0", ImmutableSet.of("x1", "x2", "y1", "y2", "z"));
  }

  private static void assertPatternDeclaresNames(String pattern, Set<String> expected) {
    LinkedHashSet<Node> seen = new LinkedHashSet<>();
    ParsingUtil.getParamOrPatternNames(parsePattern(pattern), seen::add);
    assertThat(seen).comparingElementsUsing(NAME_EQUALITY).containsExactlyElementsIn(expected);
  }

  /**
   * Inserts {@code pattern} into a functions parameter list and returns the {@code Node} that
   * pattern parses to.
   */
  private static Node parsePattern(String pattern) {
    Node script = parse("function foo(" + pattern + "){}");
    assertThat(script.isScript()).isTrue();
    Node function = script.getFirstChild();
    assertThat(function.isFunction()).isTrue();
    Node paramList = function.getSecondChild();
    assertThat(paramList.isParamList()).isTrue();
    return paramList;
  }

  private static Node parse(String source, String... warnings) {
    TestErrorReporter testErrorReporter = new TestErrorReporter().expectAllWarnings(warnings);
    Config config =
        ParserRunner.createConfig(
            LanguageMode.ES_NEXT,
            Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
            Config.RunMode.KEEP_GOING,
            null,
            true,
            StrictMode.STRICT);
    Node script =
        ParserRunner.parse(
                new SimpleSourceFile("input", SourceKind.STRONG), source, config, testErrorReporter)
            .ast;

    // verifying that all warnings were seen
    testErrorReporter.verifyHasEncounteredAllWarningsAndErrors();

    return script;
  }
}
