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

import com.google.common.collect.ImmutableMap;
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

  private void testForOf(String src, String originalExpected) {
    testForOf(externs(EXTERNS_BASE), srcs(src), expected(originalExpected));
  }

  private void testForOf(Externs externs, Sources srcs, Expected originalExpected) {
    // change the "KEY" in expected with "$jscomp$key$m123..456" name
    Expected modifiedExpected =
        expected(
            UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                (FlatSources) srcs, originalExpected, ImmutableMap.of("KEY", "$jscomp$key$")));
    test(externs, srcs, modifiedExpected);
  }

  @Test
  public void testForOfLoop() {
    // With array literal and declaring new bound variable.
    testForOf(
        "for (var i of [1,2,3]) { console.log(i); }",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "   i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With simple assign instead of var declaration in bound variable.
    testForOf(
        "for (i of [1,2,3]) { console.log(i); }",
        lines(
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "  i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // With name instead of array literal.
    testForOf(
        "for (var i of arr) { console.log(i); }",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator(arr)",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "   i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // for of with const initializer
    testForOf(
        "for (const i of [1,2,3]) { console.log(i); }",
        lines(
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "  const i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // multiple for-of loops with the const initializer name
    testForOf(
        "for (const i of [1,2,3]) { console.log(i); } for (const i of [4,5,6]) { console.log(i); }",
        lines(
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "  const i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}",
            "var $jscomp$iter$1 = $jscomp.makeIterator([4, 5, 6]);",
            "var KEY$1$i$jscomp$1 = $jscomp$iter$1.next();",
            "for (;",
            "    !KEY$1$i$jscomp$1.done; KEY$1$i$jscomp$1 = $jscomp$iter$1.next())" + " {",
            "  const i$jscomp$1 = KEY$1$i$jscomp$1.value;",
            "  {",
            "    console.log(i$jscomp$1);",
            "  }",
            "}"));

    // With empty loop body.
    testForOf(
        "for (var i of [1,2,3]);",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "   i = KEY$0$i.value;",
            "  {}",
            "}"));

    // With no block in for loop body.
    testForOf(
        "for (var i of [1,2,3]) console.log(i);",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "   i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));

    // Iteration var shadows an outer var ()
    testForOf(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        lines(
            "var i = 'outer';",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var KEY$0$i$jscomp$1 = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$i$jscomp$1.done; KEY$0$i$jscomp$1 = $jscomp$iter$0.next())" + " {",
            "  let i$jscomp$1 = KEY$0$i$jscomp$1.value;",
            "  {",
            "    alert(i$jscomp$1);",
            "  }",
            "}",
            "alert(i);"));
  }

  @Test
  public void testForOfRedeclaredVar() {
    testForOf(
        lines("for (let x of []) {", "  let x = 0;", "}"),
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var KEY$0$x=$jscomp$iter$0.next();",
            "for(;",
            "    !KEY$0$x.done; KEY$0$x=$jscomp$iter$0.next()) {",
            "  let x = KEY$0$x.value;",
            "  {",
            "    let x$jscomp$1 = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testForOfJSDoc() {
    testForOf(
        "for (/** @type {string} */ let x of []) {}",
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var KEY$0$x=$jscomp$iter$0.next();",
            "for(;",
            "    !KEY$0$x.done;KEY$0$x=$jscomp$iter$0.next()) {",
            "  let x = KEY$0$x.value;",
            "  {}",
            "}"));
    testForOf(
        "for (/** @type {string} */ x of []) {}",
        lines(
            "var $jscomp$iter$0=$jscomp.makeIterator([]);",
            "var KEY$0$x=$jscomp$iter$0.next(); ",
            "for(;",
            "    !KEY$0$x.done;KEY$0$x=$jscomp$iter$0.next()) {",
            "  x = KEY$0$x.value;",
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
    testForOf(
        "a: for(var i of [1,2]){console.log(i)}",
        lines(
            "var i;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2]);",
            "var KEY$0$i = $jscomp$iter$0.next();",
            "a: for (;",
            "    !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {",
            "   i = KEY$0$i.value;",
            "  {",
            "    console.log(i);",
            "  }",
            "}"));
    // Test if the iterator variables come before two labels
    testForOf(
        "a: b: for(var x of [1,2]){console.log(x)}",
        lines(
            "var x;",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2]);",
            "var KEY$0$x = $jscomp$iter$0.next();",
            "a: b: for(;",
            "    !KEY$0$x.done; KEY$0$x = $jscomp$iter$0.next()) {",
            "   x = KEY$0$x.value;",
            "  {",
            "    console.log(x);",
            "  }",
            "}"));
  }

  @Test
  public void testForOfWithQualifiedNameInitializer() {
    testForOf(
        "var obj = {a: 0}; for (obj.a of [1,2,3]) { console.log(obj.a); }",
        lines(
            "var obj = {a: 0};",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3])",
            "var KEY$0$a = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$a.done; KEY$0$a = $jscomp$iter$0.next()) {",
            "  obj.a = KEY$0$a.value;",
            "  {",
            "    console.log(obj.a);",
            "  }",
            "}"));
  }

  @Test
  public void testForOfWithComplexInitializer() {
    testForOf(
        "function f() { return {}; } for (f()['x' + 1] of [1,2,3]) {}",
        lines(
            "function f() { return {}; }",
            "var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]);",
            "var KEY$0$a = $jscomp$iter$0.next();",
            "for (;",
            "    !KEY$0$a.done; KEY$0$a = $jscomp$iter$0.next()) {",
            "  f()['x' + 1] = KEY$0$a.value;",
            "  {}",
            "}"));
  }

  @Test
  public void testForLetOfWithoutExterns() {
    testForOf(
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
                "var KEY$0$x = $jscomp$iter$0.next();",
                "for (;",
                "    !KEY$0$x.done; KEY$0$x = $jscomp$iter$0.next()) {",
                "  let x = KEY$0$x.value;",
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
