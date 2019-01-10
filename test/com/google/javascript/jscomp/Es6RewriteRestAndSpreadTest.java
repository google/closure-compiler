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
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6RewriteRestAndSpreadTest extends CompilerTestCase {

  public Es6RewriteRestAndSpreadTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteRestAndSpread(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeInfoValidation();
    enableTypeCheck();
  }

  // Spreading into array literals.

  @Test
  public void testSpreadArrayLiteralIntoArrayLiteral() {
    test("[...[1, 2], 3, ...[4], 5, 6, ...[], 7, 8]", "[1, 2, 3, 4, 5, 6, 7, 8]");
  }

  @Test
  public void testSpreadVariableIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid, 4, 5];",
        "var arr = [1, 2].concat($jscomp.arrayFromIterable(mid), [4, 5]);");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid(), 4, 5];",
        "var arr = [1, 2].concat($jscomp.arrayFromIterable(mid()), [4, 5]);");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionArgumentsIntoArrayLiteral() {
    test(
        "function f() { return [...arguments, 2]; };",
        lines(
            "function f() {",
            "  return [].concat($jscomp.arrayFromIterable(arguments), [2]);",
            "};"));
  }

  @Test
  public void testSpreadVariableAndFunctionReturnIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid, ...mid2(), 4, 5];",
        lines(
            "var arr = [1,2].concat(",
            "    $jscomp.arrayFromIterable(mid), $jscomp.arrayFromIterable(mid2()), [4, 5]);"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoEntireArrayLiteral() {
    test("var arr = [...mid()];", "var arr = [].concat($jscomp.arrayFromIterable(mid()));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionArgumentsIntoEntireArrayLiteral() {
    test(
        "function f() { return [...arguments]; };",
        lines("function f() {", "  return [].concat($jscomp.arrayFromIterable(arguments));", "};"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadArrayLiteralIntoArrayLiteralWithinParameterList() {
    test("f(1, [2, ...[3], 4], 5);", "f(1, [2, 3, 4], 5);");
  }

  @Test
  public void testSpreadVariableIntoArrayLiteralWithinParameterList() {
    test("f(1, [2, ...mid, 4], 5);", "f(1, [2].concat($jscomp.arrayFromIterable(mid), [4]), 5);");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoArrayLiteralWithinParameterList() {
    test(
        "f(1, [2, ...mid(), 4], 5);",
        "f(1, [2].concat($jscomp.arrayFromIterable(mid()), [4]), 5);");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  // Spreading into parameter lists.

  @Test
  public void testSpreadArrayLiteralIntoEntireParameterList() {
    test("f(...[0, 1, 2]);", "f.apply(null, [0, 1, 2]);");
  }

  @Test
  public void testSpreadArrayLiteralIntoParameterList() {
    test("f(...[0, 1, 2], 3);", "f.apply(null, [0, 1, 2, 3]);");
  }

  @Test
  public void testSpreadVariableIntoEntireParameterList() {
    test("f(...arr);", "f.apply(null, $jscomp.arrayFromIterable(arr));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoParameterList() {
    test("f(0, ...arr, 2);", "f.apply(null, [0].concat($jscomp.arrayFromIterable(arr), [2]));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoEntireParameterList() {
    test("f(...g());", "f.apply(null, $jscomp.arrayFromIterable(g()));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoParameterList() {
    test("f(0, ...g(), 2);", "f.apply(null, [0].concat($jscomp.arrayFromIterable(g()), [2]));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoIifeParameterList() {
    test("(function() {})(...arr);", "(function() {}).apply(null, $jscomp.arrayFromIterable(arr))");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoAnonymousFunctionParameterList() {
    test("getF()(...args);", "getF().apply(null, $jscomp.arrayFromIterable(args));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoMethodParameterList() {
    test(
        externs(
            lines(
                "/**",
                " * @constructor",
                " * @struct",
                " */",
                "function TestClass() { }",
                "",
                "/** @param {...string} args */",
                "TestClass.prototype.testMethod = function(args) { }",
                "",
                "/** @return {!TestClass} */",
                "function testClassFactory() { }")),
        srcs(lines("var obj = new TestClass();", "obj.testMethod(...arr);")),
        expected(
            lines(
                "var obj = new TestClass();",
                "obj.testMethod.apply(obj, $jscomp.arrayFromIterable(arr));")));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoDeepMethodParameterList() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/** @param {...number} args */ function numberVarargFn(args) { }",
                    "",
                    "/** @type {!Iterable<number>} */ var numberIterable;")),
        srcs(lines("var x = {y: {z: {m: numberVarargFn}}};", "x.y.z.m(...numberIterable);")),
        expected(
            lines(
                "var x = {y: {z: {m: numberVarargFn}}};",
                "x.y.z.m.apply(x.y.z, $jscomp.arrayFromIterable(numberIterable));")));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadMultipleArrayLiteralsIntoParameterList() {
    test(
        externs(
            MINIMAL_EXTERNS + "/** @param {...number} args */ function numberVarargFn(args) { }"),
        srcs("numberVarargFn(...[1, 2], 3, ...[4, 5], 6, ...[7, 8])"),
        expected("numberVarargFn.apply(null, [1, 2, 3, 4, 5, 6, 7, 8])"));
  }

  @Test
  public void testSpreadMultipleVariablesIntoParameterList() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/** @param {...number} args */ function numberVarargFn(args) { }",
                    "/** @type {!Iterable<number>} */ var numberIterable;")),
        srcs("numberVarargFn(0, ...numberIterable, 2, ...numberIterable, 4);"),
        expected(
            lines(
                "numberVarargFn.apply(",
                "    null,",
                "    [0].concat(",
                "        $jscomp.arrayFromIterable(numberIterable),",
                "        [2],",
                "        $jscomp.arrayFromIterable(numberIterable),",
                "        [4]));")));
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListOnAnonymousRecieverWithSideEffects() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @constructor",
                    " * @struct",
                    " */",
                    "function TestClass() { }",
                    "",
                    "/** @param {...string} args */",
                    "TestClass.prototype.testMethod = function(args) { }",
                    "",
                    "/** @return {!TestClass} */",
                    "function testClassFactory() { }",
                    "",
                    "/** @type {!Iterable<string>} */ var stringIterable;")),
        srcs("testClassFactory().testMethod(...stringIterable);"),
        expected(
            lines(
                "var $jscomp$spread$args0;",
                "($jscomp$spread$args0 = testClassFactory()).testMethod.apply(",
                "    $jscomp$spread$args0, $jscomp.arrayFromIterable(stringIterable));")));
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListOnConditionalRecieverWithSideEffects() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @constructor",
                    " * @struct",
                    " */",
                    "function TestClass() { }",
                    "",
                    "/** @param {...string} args */",
                    "TestClass.prototype.testMethod = function(args) { }",
                    "",
                    "/** @return {!TestClass} */",
                    "function testClassFactory() { }",
                    "",
                    "/** @type {!Iterable<string>} */ var stringIterable;")),
        srcs("var x = b ? testClassFactory().testMethod(...stringIterable) : null;"),
        expected(
            lines(
                "var $jscomp$spread$args0;",
                "var x = b ? ($jscomp$spread$args0 = testClassFactory()).testMethod.apply(",
                "    $jscomp$spread$args0, $jscomp.arrayFromIterable(stringIterable))",
                "        : null;")));
  }

  @Test
  public void
      testSpreadVariableIntoMethodParameterListOnRecieversWithSideEffectsMultipleTimesInOneScope() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @constructor",
                    " * @struct",
                    " */",
                    "function TestClass() { }",
                    "",
                    "/** @param {...string} args */",
                    "TestClass.prototype.testMethod = function(args) { }",
                    "",
                    "/** @return {!TestClass} */",
                    "function testClassFactory() { }",
                    "",
                    "/** @type {!Iterable<string>} */ var stringIterable;")),
        srcs(
            lines(
                "testClassFactory().testMethod(...stringIterable);",
                "testClassFactory().testMethod(...stringIterable);")),
        expected(
            lines(
                "var $jscomp$spread$args0;",
                "($jscomp$spread$args0 = testClassFactory()).testMethod.apply(",
                "    $jscomp$spread$args0, $jscomp.arrayFromIterable(stringIterable));",
                "var $jscomp$spread$args1;",
                "($jscomp$spread$args1 = testClassFactory()).testMethod.apply(",
                "    $jscomp$spread$args1, $jscomp.arrayFromIterable(stringIterable));")));
  }

  @Test
  public void testSpreadFunctionArgumentsIntoSuperParameterList() {
    // TODO(b/76024335): Enable these validations and checks.
    // We need to test super, but super only makes sense in the context of a class, but
    // the type-checker doesn't understand class syntax and fails before the test even runs.
    disableTypeInfoValidation();
    disableTypeCheck();

    test(
        lines(
            "class A {",
            "  constructor(a) {",
            "      this.p = a;",
            "  }",
            "}",
            "",
            "class B extends A {",
            "   constructor(a) {",
            "     super(0, ...arguments, 2);",
            "   }",
            "}"),
        // The `super.apply` syntax below is invalid, but won't survive other transpilation passes.
        lines(
            "class A {",
            "  constructor(a) {",
            "      this.p = a;",
            "  }",
            "}",
            "",
            "class B extends A {",
            "   constructor(a) {",
            "     super.apply(null, [0].concat($jscomp.arrayFromIterable(arguments), [2]));",
            "   }",
            "}"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoSuperParameterList() {
    // TODO(b/76024335): Enable these validations and checks.
    // We need to test super, but super only makes sense in the context of a class, but
    // the type-checker doesn't understand class syntax and fails before the test even runs.
    disableTypeInfoValidation();
    disableTypeCheck();

    test(
        lines(
            "class D {}",
            "",
            "class C extends D {",
            "  constructor(args) {",
            "    super(0, ...args, 2)",
            "  }",
            "}"),
        // The `super.apply` syntax below is invalid, but won't survive other transpilation passes.
        lines(
            "class D {}",
            "",
            "class C extends D {",
            "  constructor(args) {",
            "    super.apply(null, [0].concat($jscomp.arrayFromIterable(args), [2]));",
            "  }",
            "}"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoParameterListWithinArrayLiteral() {
    test(
        "[1, f(2, ...mid, 4), 5];",
        "[1, f.apply(null, [2].concat($jscomp.arrayFromIterable(mid), [4])), 5];");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoNew() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "new F(...args);",
        "new (Function.prototype.bind.apply(F, [null].concat($jscomp.arrayFromIterable(args))));");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  // Rest parameters

  @Test
  public void testUnusedRestParameterAtPositionZero() {
    test("function f(...zero) {}", "function f(zero) {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionOne() {
    test("function f(zero, ...one) {}", "function f(zero, one) {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionTwo() {
    test("function f(zero, one, ...two) {}", "function f(zero, one, two) {}");
  }

  @Test
  public void testUsedRestParameterAtPositionZero() {
    test(
        "function f(...zero) { return zero; }",
        lines(
            "function f(zero) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 0; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 0] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let zero = $jscomp$restParams;",
            "    return zero;",
            "  }",
            "}"));
  }

  @Test
  public void testUsedRestParameterAtPositionTwo() {
    test(
        "function f(zero, one, ...two) { return two; }",
        lines(
            "function f(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let two = $jscomp$restParams;",
            "    return two;",
            "  }",
            "}"));
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithTypingOnFunction() {
    test(
        "/** @param {...number} zero */ function f(...zero) {}",
        "/** @param {...number} zero */ function f(zero) {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithInlineTyping() {
    test("function f(/** ...number */ ...zero) {}", "function f(/** ...number */ zero) {}");
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunction() {
    test(
        "/** @param {...number} two */ function f(zero, one, ...two) { return two; }",
        lines(
            "/** @param {...number} two */",
            "function f(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let two = $jscomp$restParams;",
            "    return two;",
            "  }",
            "}"));
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionVariable() {
    test(
        "/** @param {...number} two */ var f = function(zero, one, ...two) { return two; }",
        lines(
            "/** @param {...number} two */ var f = function(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let two = $jscomp$restParams;",
            "    return two;",
            "  }",
            "}"));
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionProperty() {
    test(
        "/** @param {...number} two */ ns.f = function(zero, one, ...two) { return two; }",
        lines(
            "/** @param {...number} two */ ns.f = function(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let two = $jscomp$restParams;",
            "    return two;",
            "  }",
            "}"));
  }

  @Test
  public void testWarningAboutRestParameterMissingInlineVarArgTyping() {
    // Warn on /** number */
    testWarning(
        "function f(/** number */ ...zero) {}",
        Es6RewriteRestAndSpread.BAD_REST_PARAMETER_ANNOTATION);
  }

  @Test
  public void testWarningAboutRestParameterMissingVarArgTypingOnFunction() {
    testWarning(
        "/** @param {number} zero */ function f(...zero) {}",
        Es6RewriteRestAndSpread.BAD_REST_PARAMETER_ANNOTATION);
  }

  @Test
  public void testUnusedRestParameterAtPositionTwoWithUsedParameterAtPositionOne() {
    test(
        "function f(zero, one, ...two) {one = (one === undefined) ? 1 : one;}",
        lines(
            "function f(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    let two = $jscomp$restParams;",
            "    one = (one === undefined) ? 1 : one;",
            "  }",
            "}"));
  }
}
