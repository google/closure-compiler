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
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link Es6ForOfConverter} */
@RunWith(JUnit4.class)
public final class Es6ForOfConverterTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      new TestExternsBuilder().addArguments().addConsole().addJSCompLibraries().build();

  public Es6ForOfConverterTest() {
    super(EXTERNS_BASE);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ForOfConverter(compiler);
  }

  @Test
  public void testForOf() {
    // With array literal and declaring new bound variable.
    test(
        "for (var i of [1,2,3]) { console.log(i); }",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "   i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With simple assign instead of var declaration in bound variable.
    test(
        "for (i of [1,2,3]) { console.log(i); }",
        lines(
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "for (;",
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
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator(arr)",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "   i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With empty loop body.
    test(
        "for (var i of [1,2,3]);",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "   i = $jscomp$key$i.value;",
            "  {}",
            "}"));

    // With no block in for loop body.
    test(
        "for (var i of [1,2,3]) console.log(i);",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "   i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        lines(
            "var i = 'outer';",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var $jscomp$key$i$jscomp$1 = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$i$jscomp$1.done; $jscomp$key$i$jscomp$1 = $jscomp$iter$0.next()) {",
            "  let i$jscomp$1 = $jscomp$key$i$jscomp$1.value;",
            "  {",
            "    alert(i$jscomp$1);",
            "  }",
            "}",
            "alert(i);"));
  }

  @Test
  public void testForOfRedeclaredVar() {
    test(
        lines("for (let x of []) {", "  let x = 0;", "}"),
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var $jscomp$key$x=$jscomp$iter$0.next();",
            "for(;",
            "    !$jscomp$key$x.done; $jscomp$key$x=$jscomp$iter$0.next()) {",
            "  let x = $jscomp$key$x.value;",
            "  {",
            "    let x$jscomp$1 = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testForOfJSDoc() {
    test(
        "for (/** @type {string} */ let x of []) {}",
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var $jscomp$key$x=$jscomp$iter$0.next();",
            "for(;",
            "    !$jscomp$key$x.done;$jscomp$key$x=$jscomp$iter$0.next()) {",
            "  let x = $jscomp$key$x.value;",
            "  {}",
            "}"));
    test(
        "for (/** @type {string} */ x of []) {}",
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var $jscomp$key$x=$jscomp$iter$0.next(); ",
            "for(;",
            "    !$jscomp$key$x.done;$jscomp$key$x=$jscomp$iter$0.next()) {",
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
  public void testLabelForOf() {
    // Tests if iterator variables come before a single label
    test(
        "a: for(var i of [1,2]){console.log(i)}",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2]);",
            "var $jscomp$key$i = $jscomp$iter$0.next();",
            "a: for (;",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "   i = $jscomp$key$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));
    // Test if the iterator variables come before two labels
    test(
        "a: b: for(var x of [1,2]){console.log(x)}",
        lines(
            "var x;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2]);",
            "var $jscomp$key$x = $jscomp$iter$0.next();",
            "a: b: for(;",
            "    !$jscomp$key$x.done; $jscomp$key$x = $jscomp$iter$0.next()) {",
            "   x = $jscomp$key$x.value;",
            "  {",
            "    console.log(x);",
            "  }",
            "}"));
  }

  @Test
  public void testForOfWithQualifiedNameInitializer() {
    test(
        "var obj = {a: 0}; for (obj.a of [1,2,3]) { console.log(obj.a); }",
        lines(
            "var obj = {a: 0};",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var $jscomp$key$a = $jscomp$iter$0.next();",
            "for (;",
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
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var $jscomp$key$a = $jscomp$iter$0.next();",
            "for (;",
            "    !$jscomp$key$a.done; $jscomp$key$a = $jscomp$iter$0.next()) {",
            "  f()['x' + 1] = $jscomp$key$a.value;",
            "  {}",
            "}"));
  }

  @Test
  public void testForLetOfWithoutExterns() {
    test(
        // add only minimal runtime library stubs to prevent AstFactory crash
        externs(
            lines(
                "var $jscomp = {};",
                "/**",
                " * @param {?} iterable",
                " * @return {!Iterator<T>}",
                " * @template T",
                " */",
                "$jscomp.makeIterator = function(iterable) {};")),
        srcs("for (let x of [1, 2, 3]) {}"),
        expected(
            lines(
                "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
                "var $jscomp$key$x = $jscomp$iter$0.next();",
                "for (;",
                "    !$jscomp$key$x.done; $jscomp$key$x = $jscomp$iter$0.next()) {",
                "  let x = $jscomp$key$x.value;",
                "  {}",
                "}")));
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
