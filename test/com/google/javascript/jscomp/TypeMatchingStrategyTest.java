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

import static com.google.javascript.jscomp.TypeMatchingStrategy.EXACT;
import static com.google.javascript.jscomp.TypeMatchingStrategy.LOOSE;
import static com.google.javascript.jscomp.TypeMatchingStrategy.STRICT_NULLABILITY;
import static com.google.javascript.jscomp.TypeMatchingStrategy.SUBTYPES;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.TypeMatchingStrategy.MatchResult;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

import junit.framework.TestCase;

public final class TypeMatchingStrategyTest extends TestCase {

  private static final String EXTERNS = Joiner.on("\n").join(
      "/** @constructor */",
      "var OtherType = function() {};",
      "/** @constructor */",
      "var SuperType = function() {};",
      "/** @constructor @extends {SuperType} */",
      "var SubType = function() {};");

  public void testMatch_default() {
    assertMatch(LOOSE, "!SuperType", "!SuperType", true, false);
    assertMatch(LOOSE, "!SuperType", "?SuperType", true, false);
    assertMatch(LOOSE, "!SuperType", "SuperType|undefined", true, false);
    assertMatch(LOOSE, "!SuperType", "SuperType|void", true, false);
    assertMatch(LOOSE, "!SuperType", "!SubType", true, false);
    assertMatch(LOOSE, "!SuperType", "?SubType", true, false);
    assertMatch(LOOSE, "!SuperType", "SubType|undefined", true, false);
    assertMatch(LOOSE, "!SuperType", "SubType|void", true, false);
    assertMatch(LOOSE, "!SuperType", "number", false, false);
    assertMatch(LOOSE, "!SuperType", "*", true, true);
    assertMatch(LOOSE, "!SuperType", "?", true, true);
    assertMatch(LOOSE, "?", "string", true, false);
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

  public void testMatch_subtypes() {
    assertMatch(SUBTYPES, "!SuperType", "!SuperType", true, false);
    assertMatch(SUBTYPES, "!SuperType", "?SuperType", false, false);
    assertMatch(SUBTYPES, "!SuperType", "!OtherType", false, false);
    assertMatch(SUBTYPES, "!SuperType", "SuperType|undefined", false, false);
    assertMatch(SUBTYPES, "!SuperType", "SuperType|void", false, false);
    assertMatch(SUBTYPES, "!SuperType", "!SubType", true, false);
    assertMatch(SUBTYPES, "!SuperType", "?SubType", false, false);
    assertMatch(SUBTYPES, "!SuperType", "SubType|undefined", false, false);
    assertMatch(SUBTYPES, "!SuperType", "SubType|void", false, false);
    assertMatch(SUBTYPES, "!SuperType", "number", false, false);
    assertMatch(SUBTYPES, "!SuperType", "*", false, false);
    assertMatch(SUBTYPES, "!SuperType", "?", false, false);
    assertMatch(SUBTYPES, "?SuperType", "!SuperType", true, false);
    assertMatch(SUBTYPES, "?SuperType", "?SuperType", true, false);
    assertMatch(SUBTYPES, "?SuperType", "?OtherType", false, false);
    assertMatch(SUBTYPES, "?SuperType", "SuperType|undefined", false, false);
    assertMatch(SUBTYPES, "?SuperType", "SuperType|void", false, false);
    assertMatch(SUBTYPES, "?SuperType", "SuperType|OtherType", false, false);
    assertMatch(SUBTYPES, "?SuperType", "!SubType", true, false);
    assertMatch(SUBTYPES, "?SuperType", "?SubType", true, false);
    assertMatch(SUBTYPES, "?SuperType", "SubType|undefined", false, false);
    assertMatch(SUBTYPES, "?SuperType", "SubType|void", false, false);
    assertMatch(SUBTYPES, "?SuperType", "number", false, false);
    assertMatch(SUBTYPES, "?SuperType", "*", false, false);
    assertMatch(SUBTYPES, "?SuperType", "?", false, false);
    assertMatch(SUBTYPES, "?", "string", true, false);
    assertMatch(SUBTYPES, "?", "?", true, false);
    assertMatch(SUBTYPES, "?", "*", true, false);
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

    // It's important that the test uses the same compiler to compile the template type and the
    // type to be matched. Otherwise, equal types won't be considered equal.
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);

    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", EXTERNS)),
        ImmutableList.of(
            SourceFile.fromCode(
                "test",
                String.format(
                    "/** @type {%s} */ var x; /** @type {%s} */ var y;", templateType, type))),
        options);
    Node script = compiler.getRoot().getLastChild().getFirstChild();
    Node xNode = script.getFirstChild();
    Node yNode = script.getLastChild();
    JSType templateJsType = xNode.getFirstChild().getJSType();
    JSType jsType = yNode.getFirstChild().getJSType();

    MatchResult matchResult = typeMatchingStrategy.match(templateJsType, jsType);
    assertEquals(
        isMatch
            ? "'" + templateJsType + "' should match '" + jsType + "'"
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
}
