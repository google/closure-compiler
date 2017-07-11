/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.refactoring.examples;

import com.google.javascript.refactoring.Scanner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * Test case for {@link GoogBindToArrow}.
 */
@RunWith(JUnit4.class)
public class GoogBindToArrowTest extends RefactoringTest {
  @Override
  protected String getExterns() {
    return "";
  }

  @Override
  protected Scanner getScanner() {
    return new GoogBindToArrow();
  }

  @Test
  public void testBasic() {
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(function() {",
            "  console.log(this.name);",
            "}, this)"),
        LINE_JOINER.join(
            "() => {",
            "  console.log(this.name);",
            "}"));

    assertChanges(
        LINE_JOINER.join(
            "var f = goog.bind(function() {",
            "  console.log(this.name);",
            "}, this);"),
        LINE_JOINER.join(
            "var f = () => {",
            "  console.log(this.name);",
            "};"));
  }

  @Test
  public void testParams() {
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(function(a, b, c) {",
            "  console.log(this.name);",
            "}, this)"),
        LINE_JOINER.join(
            "(a, b, c) => {",
            "  console.log(this.name);",
            "}"));
  }

  @Test
  public void testNoThis() {
    // TODO(tbreisacher): Since the function doesn't reference `this` at all, we could convert
    // to just "function() { console.log('hello'); }" for non-ES6 code, either in this refactoring,
    // or possibly as a separate one ("RemoveUnnecessaryGoogBind").
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(function() {",
            "  console.log('hello');",
            "}, this)"),
        LINE_JOINER.join(
            "() => {",
            "  console.log('hello');",
            "}"));
  }

  @Test
  public void testArrow() {
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(() => {",
            "  console.log('hello');",
            "}, this)"),
        LINE_JOINER.join(
            "() => {",
            "  console.log('hello');",
            "}"));
  }

  @Test
  public void testFunctionWithReturn() {
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(function() {",
            "  console.log('hello world');",
            "  return false;",
            "}, this)"),
        LINE_JOINER.join(
            "() => {",
            "  console.log('hello world');",
            "  return false;",
            "}"));
  }

  @Test
  public void testSimpleReturn() {
    assertChanges(
        LINE_JOINER.join(
            "goog.bind(function() {",
            "  return 7;",
            "}, this)"),
        "() => 7");

    assertChanges(
        LINE_JOINER.join(
            "[1,2,3].forEach(goog.bind(function(x) {",
            "  return y;",
            "}, this));"),
        "[1,2,3].forEach((x) => y);");
  }

  /**
   * Test to show the bad indentation that we're getting.
   * @bug 25514142
   */
  @Test
  public void testIndentation() {
    assertChanges(
        LINE_JOINER.join(
            "if (x) {",
            "  if (y) {",
            "    var f = goog.bind(function() {",
            "      alert(x);",
            "      alert(y);",
            "    }, this);",
            "  }",
            "}"),
        LINE_JOINER.join(
            "if (x) {",
            "  if (y) {",
            "    var f = () => {",
            "  alert(x);",
            "  alert(y);",
            "};",
            "  }",
            "}"));
  }

  @Test
  public void testNoChanges() {
    assertNoChanges("goog.bind(function() {}, foo)");
    assertNoChanges("goog.bind(fn, this)");
  }
}
