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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for {@link Es6ForOfConverter}
 *
 * @author lharker@google.com (Laura Harker)
 */
@RunWith(JUnit4.class)
public final class Es6ForOfConverterTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      lines(
          MINIMAL_EXTERNS,
          "/** @constructor @template T */",
          "function Arguments() {}",
          "/**",
          " * @param {string|!Iterable<T>|!Iterator<T>|!Arguments<T>} iterable",
          " * @return {!Iterator<T>}",
          " * @template T",
          " */",
          "$jscomp.makeIterator = function(iterable) {};",
          "var console;",
          "console.log = function(s) {}");

  public Es6ForOfConverterTest() {
    super(EXTERNS_BASE);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
  }
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ForOfConverter(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testForOf() {

    // With array literal and declaring new bound variable.
    test(
        "for (var i of [1,2,3]) { console.log(i); }",
        lines(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/makeiterator");

    // With simple assign instead of var declaration in bound variable.
    test(
        "for (i of [1,2,3]) { console.log(i); }",
        lines(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With name instead of array literal.
    test(
        "for (var i of arr) { console.log(i); }",
        lines(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator(arr),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With empty loop body.
    test(
        "for (var i of [1,2,3]);",
        lines(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  {}",
            "}"));

    // With no block in for loop body.
    test(
        "for (var i of [1,2,3]) console.log(i);",
        lines(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        lines(
            "var i = 'outer';",
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  let i = $jscomp$key$i.value;",
            "  {",
            "    alert(i);",
            "  }",
            "}",
            "alert(i);"));
  }

  @Test
  public void testForOfRedeclaredVar() {
    test(
        lines("for (let x of []) {", "  let x = 0;", "}"),
        lines(
            "for(var $jscomp$iter$0=$jscomp.makeIterator([]),",
            "    $jscomp$key$x=$jscomp$iter$0.next();",
            "    !$jscomp$key$x.done; $jscomp$key$x=$jscomp$iter$0.next()) {",
            "  let x = $jscomp$key$x.value;",
            "  {",
            "    let x = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testForOfJSDoc() {
    test(
        "for (/** @type {string} */ let x of []) {}",
        lines(
            "for(var $jscomp$iter$0=$jscomp.makeIterator([]),",
            "    $jscomp$key$x=$jscomp$iter$0.next();",
            "    !$jscomp$key$x.done;$jscomp$key$x=$jscomp$iter$0.next()) {",
            "  /** @type {string} */",
            "  let x = $jscomp$key$x.value;",
            "  {}",
            "}"));
    test(
        "for (/** @type {string} */ x of []) {}",
        lines(
            "for(var $jscomp$iter$0=$jscomp.makeIterator([]),",
            "    $jscomp$key$x=$jscomp$iter$0.next();",
            "    !$jscomp$key$x.done;$jscomp$key$x=$jscomp$iter$0.next()) {",
            "  /** @type {string} */",
            "  x = $jscomp$key$x.value;",
            "  {}",
            "}"));
  }

  @Test
  public void testForOfOnNonIterable() {
    testWarning(
        lines(
            "var arrayLike = {",
            "  0: 'x',",
            "  1: 'y',",
            "  length: 2,",
            "};",
            "for (var x of arrayLike) {}"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testForOfWithQualifiedNameInitializer() {
    test(
        "var obj = {a: 0}; for (obj.a of [1,2,3]) { console.log(obj.a); }",
        lines(
            "var obj = {a: 0};",
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$a = $jscomp$iter$0.next();",
            "    !$jscomp$key$a.done; $jscomp$key$a = $jscomp$iter$0.next()) {",
            "  obj.a = $jscomp$key$a.value;",
            "  {",
            "    console.log(obj.a);",
            "  }",
            "}"));
  }

  @Test
  public void testForOfWithComplexInitializer() {
    test(
        "function f() { return {}; } for (f()['x' + 1] of [1,2,3]) {}",
        lines(
            "function f() { return {}; }",
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$a = $jscomp$iter$0.next();",
            "    !$jscomp$key$a.done; $jscomp$key$a = $jscomp$iter$0.next()) {",
            "  f()['x' + 1] = $jscomp$key$a.value;",
            "  {}",
            "}"));
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected  NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
