/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.TypeMatchingStrategy.DEFAULT;
import static com.google.javascript.jscomp.TypeMatchingStrategy.EXACT;
import static com.google.javascript.jscomp.TypeMatchingStrategy.STRICT_NULLABILITY;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.TypeMatchingStrategy.MatchResult;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

import junit.framework.TestCase;

public class TypeMatchingStrategyTest extends TestCase {

  private static final String EXTERNS = Joiner.on("\n").join(
      "/** @constructor */",
      "var OtherType = function() {};",
      "/** @constructor */",
      "var SuperType = function() {};",
      "/** @constructor @extends {SuperType} */",
      "var SubType = function() {};");

  public void testMatch_default() {
    assertMatch(DEFAULT, "!SuperType", "!SuperType", true, false);
    assertMatch(DEFAULT, "!SuperType", "?SuperType", true, false);
    assertMatch(DEFAULT, "!SuperType", "SuperType|undefined", true, false);
    assertMatch(DEFAULT, "!SuperType", "SuperType|void", true, false);
    assertMatch(DEFAULT, "!SuperType", "!SubType", true, false);
    assertMatch(DEFAULT, "!SuperType", "?SubType", true, false);
    assertMatch(DEFAULT, "!SuperType", "SubType|undefined", true, false);
    assertMatch(DEFAULT, "!SuperType", "SubType|void", true, false);
    assertMatch(DEFAULT, "!SuperType", "number", false, false);
    assertMatch(DEFAULT, "!SuperType", "*", true, true);
    assertMatch(DEFAULT, "!SuperType", "?", true, true);
    assertMatch(DEFAULT, "?", "string", true, false);
  }

  public void testMatch_respectNullability() {
    assertMatch(STRICT_NULLABILITY, "!SuperType", "!SuperType", true, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "?SuperType", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "SuperType|undefined", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "SuperType|void", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "!SubType", true, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "?SubType", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "SubType|undefined", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "SubType|void", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "number", false, false);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "*", true, true);
    assertMatch(STRICT_NULLABILITY, "!SuperType", "?", true, true);
    assertMatch(STRICT_NULLABILITY, "?", "string", true, false);
  }

  public void testMatch_exact() {
    assertMatch(EXACT, "!SuperType", "!SuperType", true, false);
    assertMatch(EXACT, "!SuperType", "?SuperType", false, false);
    assertMatch(EXACT, "!SuperType", "SuperType|undefined", false, false);
    assertMatch(EXACT, "!SuperType", "SuperType|void", false, false);
    assertMatch(EXACT, "!SuperType", "!SubType", false, false);
    assertMatch(EXACT, "!SuperType", "?SubType", false, false);
    assertMatch(EXACT, "!SuperType", "SubType|undefined", false, false);
    assertMatch(EXACT, "!SuperType", "SubType|void", false, false);
    assertMatch(EXACT, "!SuperType", "number", false, false);
    assertMatch(EXACT, "!SuperType", "*", false, false);
    assertMatch(EXACT, "!SuperType", "?", false, false);
    assertMatch(EXACT, "?", "string", true, false);
  }

  private static void assertMatch(
      TypeMatchingStrategy typeMatchingStrategy,
      String templateType,
      String type,
      boolean isMatch,
      boolean isLooseMatch) {
    MatchResult matchResult = typeMatchingStrategy.match(getJsType(templateType), getJsType(type));
    assertEquals(
        isMatch
            ? "'" + getJsType(templateType) + "' should match '" + getJsType(type) + "'"
            : "'" + templateType + "' should not match '" + type + "'",
        isMatch,
        matchResult.isMatch());
    assertEquals(
        isLooseMatch
            ? "'" + templateType + "' should loosely match '" + type + "'"
            : "'" + templateType + "' should not loosely match '" + type + "'",
        isLooseMatch,
        matchResult.isLooseMatch());
  }

  private static JSType getJsType(String jsType) {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);

    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", EXTERNS)),
        ImmutableList.of(
            SourceFile.fromCode("test", String.format("/** @type {%s} */ var x;", jsType))),
        options);
    Node node = compiler.getRoot().getLastChild();
    return node.getFirstChild().getFirstChild().getFirstChild().getJSType();
  }
}
