/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link Es6RenameVariablesInParamLists}.
 *
 * @author moz@google.com (Michael Zhou)
 */
@RunWith(JUnit4.class)
public final class Es6RenameVariablesInParamListsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RenameVariablesInParamLists(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testRenameVar() {
    test("var x = 5; function f(y=x) { var x; }",
        "var x = 5; function f(y=x) { var x$0; }");

    test(
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x; y++;",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x$0; y++;",
            "}"));

    test(
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x;",
            "  { let x; x++; }",
            "  x++;",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x$0;",
            "  { let x; x++; }",
            "  x$0++;",
            "}"));

    test(
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x; { x++ };",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function y() { return x(); }())) {",
            "  var x$0; { x$0++ };",
            "}"));

    test(
        lines(
            "function f(a = x, b = y) {",
            "  var y, x;",
            "  return function() { var x = () => y };",
            "}"),
        lines(
            "function f(a = x, b = y) {",
            "  var y$0, x$1;",
            "  return function() { var x = () => y$0 };",
            "}"));

    test(
        lines(
            "var x = 4;", "function f(a=x) { let x = 5; { let x = 99; } return a + x; }"),
        lines(
            "var x = 4;", "function f(a=x) { let x$0 = 5; { let x = 99; } return a + x$0; }"));

  }

  @Test
  public void testRenameFunction() {
    test(
        lines(
            "function x() {}", "function f(y=x()) {", "  x();", "  function x() {}", "}"),
        lines(
            "function x() {}", "function f(y=x()) {", "  x$0();", "  function x$0() {}", "}"));
  }

  @Test
  public void testGlobalDeclaration() {
    test(
        lines(
            "function x() {}",
            "function f(y=(function y() { w = 5; return w; }())) {",
            "  let x = w;",
            "  var w = 3;",
            "  return w;",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function y() { w = 5; return w; }())) {",
            "  let x = w$0;",
            "  var w$0 = 3;",
            "  return w$0;",
            "}"));

    testSame(
        lines(
            "function x() {}",
            "function f(y=(function () { w = 5; return w; }())) {",
            "  w;",
            "  return w;",
            "}"));

    test(
        lines(
            "function x() {}",
            "function f(y=(function () { w = 5; return w; }())) {",
            "  w;",
            "  var w = 3;",
            "  return w;",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function () { w = 5; return w; }())) {",
            "  w$0;",
            "  var w$0 = 3;",
            "  return w$0;",
            "}"));

    test(
        lines(
            "function x() {}",
            "function f(y=(function () { w = 5; return w; }())) {",
            "  w;",
            "  let w = 3;",
            "  return w;",
            "}"),
        lines(
            "function x() {}",
            "function f(y=(function () { w = 5; return w; }())) {",
            "  w$0;",
            "  let w$0 = 3;",
            "  return w$0;",
            "}"));
  }

  @Test
  public void testMultipleDefaultParams() {
    test(
        lines(
            "function x() {}",
            "var y = 1;",
            "function f(z=x, w=y) {",
            "  let x = y;",
            "  var y = 3;",
            "  return w;",
            "}"),
        lines(
            "function x() {}",
            "var y = 1;",
            "function f(z=x, w=y) {",
            "  let x$0 = y$1;",
            "  var y$1 = 3;",
            "  return w;",
            "}"));

    test(
        lines(
            "function x() {}",
            "var y = 1;",
            "function f(z=x, w=y) {",
            "  var x;",
            "  { let y; y++; }",
            "  { var y; y++; }",
            "  x++;",
            "}"),
        lines(
            "function x() {}",
            "var y = 1;",
            "function f(z=x, w=y) {",
            "  var x$0;",
            "  { let y; y++; }",
            "  { var y$1; y$1++; }",
            "  x$0++;",
            "}"));
  }

  @Test
  public void testArrow() {
    testSame("var x = true; var f = (a=x) => x;");
    test("var x = true; var f = (a=x) => { var x = false; return a; }",
        "var x = true; var f = (a=x) => { var x$0 = false; return a; }");
  }
}
