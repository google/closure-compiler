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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/** Unit tests for {@link Es6RewriteGenerators}. */
public final class Es6RewriteGeneratorsTest extends CompilerTestCase {
  private boolean allowMethodCallDecomposing;

  public Es6RewriteGeneratorsTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    allowMethodCallDecomposing = false;
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableTypeCheck();
    enableTypeInfoValidation();
    // Es6RewriteGenerators uses named types declared in generator_engine.js
    ensureLibraryInjected("es6/generator_engine");
    // generator_engine depends on util/global, which declares externs for 'window' and 'global'
    allowExternsChanges();
    disableCompareSyntheticCode();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setAllowMethodCallDecomposing(allowMethodCallDecomposing);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6RewriteGenerators(compiler);
  }

  private void rewriteGeneratorBody(String beforeBody, String afterBody) {
    rewriteGeneratorBodyWithVars(beforeBody, "", afterBody);
  }

  private void rewriteGeneratorBodyWithVars(
      String beforeBody, String varDecls, String afterBody) {
    rewriteGeneratorBodyWithVarsAndReturnType(beforeBody, varDecls, afterBody, "?");
  }

  private void rewriteGeneratorBodyWithVarsAndReturnType(
      String beforeBody, String varDecls, String afterBody, String returnType) {
    test(
        "/** @return {" + returnType + "} */ function *f() {" + beforeBody + "}",
        lines(
            "/** @return {" + returnType + "} */",
            "function f() {",
            varDecls,
            "  return $jscomp.generator.createGenerator(",
            "      f,",
            "      /** @suppress {uselessCode} */",
            "      function ($jscomp$generator$context) {",
            "        do switch ($jscomp$generator$context.nextAddress) {",
            "          case 1:",
            afterBody,
            "        } while (0);",
            "      });",
            "}"));
  }

  public void testUnnamed() {
    test(
        lines("f = function *() {};"),
        lines(
            "f = function $jscomp$generator$function() {",
            "  return $jscomp.generator.createGenerator(",
            "      $jscomp$generator$function,",
            "      /** @suppress {uselessCode} */",
            "      function ($jscomp$generator$context) {",
            "        do switch ($jscomp$generator$context.nextAddress) {",
            "          case 1:",
            "            $jscomp$generator$context.jumpToEnd();",
            "        } while (0);",
            "      });",
            "}"));
  }

  public void testSimpleGenerator() {
    rewriteGeneratorBody(
        "",
        "  $jscomp$generator$context.jumpToEnd();");

    rewriteGeneratorBody(
        "yield;",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 0);"));

    rewriteGeneratorBody(
        "yield 1;",
        lines(
            "  return $jscomp$generator$context.yield(1, 0);"));

    test(
        "/** @param {*} a */ function *f(a, b) {}",
        lines(
            "/** @param {*} a */",
            "function f(a, b) {",
            "  return $jscomp.generator.createGenerator(",
            "      f,",
            "      /** @suppress {uselessCode} */",
            "      function($jscomp$generator$context) {",
            "        do switch ($jscomp$generator$context.nextAddress) {",
            "          case 1:",
            "            $jscomp$generator$context.jumpToEnd();",
            "        } while (0);",
            "      });",
            "}"));

    rewriteGeneratorBodyWithVars(
        "var i = 0, j = 2;",
        "var i, j;",
        lines(
            "i = 0, j = 2;",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "var i = 0; yield i; i = 1; yield i; i = i + 1; yield i;",
        "var i;",
        lines(
            "  i = 0;",
            "  return $jscomp$generator$context.yield(i, 2);",
            "case 2:",
            "  i = 1;",
            "  return $jscomp$generator$context.yield(i, 3);",
            "case 3:",
            "  i = i + 1;",
            "  return $jscomp$generator$context.yield(i, 0);"));
  }

  public void testReturnGenerator() {
    test(
        "function f() { return function *g() {yield 1;} }",
        lines(
            "function f() {",
            "  return function g() {",
            "    return $jscomp.generator.createGenerator(",
            "        g,",
            "        /** @suppress {uselessCode} */",
            "        function($jscomp$generator$context) {",
            "          do switch ($jscomp$generator$context.nextAddress) {",
            "            case 1:",
            "              return $jscomp$generator$context.yield(1, 0);",
            "          } while (0);",
            "        });",
            "  }",
            "}"));
  }

  public void testNestedGenerator() {
    test(
        "function *f() { function *g() {yield 2;} yield 1; }",
        lines(
            "function f() {",
            "  function g() {",
            "    return $jscomp.generator.createGenerator(",
            "        g,",
            "        /** @suppress {uselessCode} */",
            "        function($jscomp$generator$context$1) {",
            "          do switch ($jscomp$generator$context$1.nextAddress) {",
            "            case 1:",
            "              return $jscomp$generator$context$1.yield(2, 0);",
            "          } while (0);",
            "        });",
            "  }",
            "  return $jscomp.generator.createGenerator(",
            "      f,",
            "      /** @suppress {uselessCode} */",
            "      function($jscomp$generator$context) {",
            "        do switch ($jscomp$generator$context.nextAddress) {",
            "          case 1:",
            "            return $jscomp$generator$context.yield(1, 0);",
            "        } while (0);",
            "      });",
            "}"));
  }


  public void testForLoops() {
    rewriteGeneratorBodyWithVars(
        "var i = 0; for (var j = 0; j < 10; j++) { i += j; }",
        "var i;",
        lines(
            "  i = 0;",
            "  for (var j = 0; j < 10; j++) { i += j; }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "var i = 0; for (var j = yield; j < 10; j++) { i += j; }",
        "var i;",
        lines(
            "  i = 0;",
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  for (var j = $jscomp$generator$context.yieldResult; j < 10; j++) { i += j; }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "for (;;) { yield 1; }",
        lines(
            "  return $jscomp$generator$context.yield(1, 1);"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 3);",
            "case 3:",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        "var i = 0; for (var j = 0; j < 10; j++) { i += j; yield 5; }",
        "var i; var j;",
        lines(
            "  i = 0;",
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  i += j;",
            "  return $jscomp$generator$context.yield(5, 3);",
            "case 3:",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;"));
  }

  public void testWhileLoops() {
    rewriteGeneratorBodyWithVars(
        "var i = 0; while (i < 10) { i++; i++; i++; } yield i;",
        "  var i;",
        lines(
            "  i = 0;",
            "  while (i < 10) { i ++; i++; i++; }",
            "  return $jscomp$generator$context.yield(i, 0);"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; j++; } j += 10;",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(3);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 4)",
            "case 4:",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;",
            "case 3:",
            "  j += 10;",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; j++; } yield 5",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    return $jscomp$generator$context.yield(5, 0);",
            "  }",
            "  return $jscomp$generator$context.yield(j, 4)",
            "case 4:",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (yield) { j++; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  return $jscomp$generator$context.yield(undefined, 4);",
            "case 4:",
            "  if (!($jscomp$generator$context.yieldResult)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;"));
  }

  public void testUndecomposableExpression() {
    testError("function *f() { obj.bar(yield 5); }", Es6ToEs3Util.CANNOT_CONVERT);
  }

  public void testDecomposableExpression() {
    rewriteGeneratorBodyWithVars(
        "return a + (a = b) + (b = yield) + a;",
        lines("var JSCompiler_temp_const$jscomp$0;"),
        lines(
            "  JSCompiler_temp_const$jscomp$0 = a + (a = b);",
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  return $jscomp$generator$context.return(",
            "JSCompiler_temp_const$jscomp$0 + (b = $jscomp$generator$context.yieldResult) + a);"));

    rewriteGeneratorBodyWithVars(
        "return (yield ((yield 1) + (yield 2)));",
        lines("var JSCompiler_temp_const$jscomp$0;"),
        lines(
            "  return $jscomp$generator$context.yield(1, 3);",
            "case 3:",
            "  JSCompiler_temp_const$jscomp$0=$jscomp$generator$context.yieldResult;",
            "  return $jscomp$generator$context.yield(2, 4);",
            "case 4:",
            "  return $jscomp$generator$context.yield(",
            "      JSCompiler_temp_const$jscomp$0 + $jscomp$generator$context.yieldResult, 2);",
            "case 2:",
            "  return $jscomp$generator$context.return($jscomp$generator$context.yieldResult);"));

    allowMethodCallDecomposing = true;
    rewriteGeneratorBodyWithVars(
        "var obj = {bar: function(x) {}}; obj.bar(yield 5);",
        lines(
            "var obj; var JSCompiler_temp_const$jscomp$1;", "var JSCompiler_temp_const$jscomp$0;"),
        lines(
            "  obj = {bar: function(x) {}};",
            "  JSCompiler_temp_const$jscomp$1 = obj;",
            "  JSCompiler_temp_const$jscomp$0 = JSCompiler_temp_const$jscomp$1.bar;",
            "  return $jscomp$generator$context.yield(5, 2);",
            "case 2:",
            "  JSCompiler_temp_const$jscomp$0.call(",
            "      JSCompiler_temp_const$jscomp$1, $jscomp$generator$context.yieldResult);",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testGeneratorCannotConvertYet() {
    testError("function *f(b, i) {switch (i) { case yield: return b; }}",
        Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  public void testThrow() {
    rewriteGeneratorBody(
        "throw 1;",
        "throw 1;");
  }

  public void testLabels() {
    rewriteGeneratorBody(
        "l: if (true) { break l; }",
        lines(
            "  l: if (true) { break l; }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "l: if (yield) { break l; }",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 3);",
            "case 3:",
            "  if ($jscomp$generator$context.yieldResult) {",
            "   $jscomp$generator$context.jumpTo(0);",
            "   break;",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "l: if (yield) { while (1) {break l;} }",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 3);",
            "case 3:",
            "  if ($jscomp$generator$context.yieldResult) {",
            "    while (1) {",
            "      return $jscomp$generator$context.jumpTo(0);",
            "    }",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "l: for (;;) { yield i; continue l; }",
        lines(
            "  return $jscomp$generator$context.yield(i, 5);",
            "case 5:",
            "  $jscomp$generator$context.jumpTo(1);",
            "  break;",
            "  $jscomp$generator$context.jumpTo(1);",
            "  break;"));

    rewriteGeneratorBody(
        "l1: l2: if (yield) break l1; else break l2;",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 3);",
            "case 3:",
            "  if($jscomp$generator$context.yieldResult) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  } else {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testUnreachable() {
    // TODO(skill): The henerator transpilation shold not produce any unreachable code
    rewriteGeneratorBody(
        "while (true) {yield; break;}",
        lines(
            "  if (!true) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(undefined, 4);",
            "case 4:",
            "  $jscomp$generator$context.jumpTo(0);",
            "  break;",
            "  $jscomp$generator$context.jumpTo(1);",
            "  break;"));
  }

  public void testCaseNumberOptimization() {
    rewriteGeneratorBodyWithVars(
        lines(
            "while (true) {",
            "  var gen = generatorSrc();",
            "  var gotResponse = false;",
            "  for (var response in gen) {",
            "    yield response;",
            "    gotResponse = true;",
            "  }",
            "  if (!gotResponse) {",
            "    return;",
            "  }",
            "}"),
        lines(
            "var gen;",
            "var gotResponse;",
            "var response, $jscomp$generator$forin$0;"),
        lines(
            "  if (!true) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  gen = generatorSrc();",
            "  gotResponse = false;",
            "  $jscomp$generator$forin$0 = $jscomp$generator$context.forIn(gen);",
            "case 4:",
            "  if (!((response=$jscomp$generator$forin$0.getNext()) != null)) {",
            "    if (!gotResponse) return $jscomp$generator$context.return();",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(response, 7);",
            "case 7:",
            "  gotResponse = true;",
            "  $jscomp$generator$context.jumpTo(4);",
            "  break;"));

    rewriteGeneratorBody(
        "do { do { do { yield; } while (3) } while (2) } while (1)",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 10);",
            "case 10:",
            "  if (3) {",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  if (2) {",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  if (1) {",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testIf() {
    rewriteGeneratorBodyWithVars(
        "var j = 0; if (yield) { j = 1; }",
        "var j;",
        lines(
            "  j = 0;",
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  if ($jscomp$generator$context.yieldResult) { j = 1; }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; if (j < 1) { j = 5; } else { yield j; }",
        "var j;",
        lines(
            "  j = 0;",
            "  if (j < 1) {",
            "    j = 5;",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 0);"));

    // When "else" doesn't contain yields, it's more optimal to swap "if" and else "blocks" and
    // negate the condition.
    rewriteGeneratorBodyWithVars(
        "var j = 0; if (j < 1) { yield j; } else { j = 5; }",
        "var j;",
        lines(
            "  j = 0;",
            "  if (!(j < 1)) {",
            "    j = 5;",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 0);"));

    // No "else" block, pretend as it's empty
    rewriteGeneratorBodyWithVars(
        "var j = 0; if (j < 1) { yield j; }",
        "var j;",
        lines(
            "  j = 0;",
            "  if (!(j < 1)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 0);"));

    rewriteGeneratorBody(
        "if (i < 1) { yield i; } else { yield 1; }",
        lines(
            "  if (i < 1) {",
            "    return $jscomp$generator$context.yield(i, 0);",
            "  }",
            "  return $jscomp$generator$context.yield(1, 0);"));

    rewriteGeneratorBody(
        "if (i < 1) { yield i; yield i + 1; i = 10; } else { yield 1; yield 2; i = 5;}",
        lines(
            "  if (i < 1) {",
            "    return $jscomp$generator$context.yield(i, 6);",
            "  }",
            "  return $jscomp$generator$context.yield(1, 4);",
            "case 4:",
            "  return $jscomp$generator$context.yield(2, 5);",
            "case 5:",
            "  i = 5;",
            "  $jscomp$generator$context.jumpTo(0);",
            "  break;",
            "case 6:",
            "  return $jscomp$generator$context.yield(i + 1, 7)",
            "case 7:",
            "  i = 10;",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "if (i < 1) { while (true) { yield 1;} } else {  while (false) { yield 2;}}",
        lines(
            "  if (i < 1) {",
            "    $jscomp$generator$context.jumpTo(7);",
            "    break;",
            "  }",
            "case 4:",
            "  if (!false) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(2, 4);",
            "case 7:",
            "  if (!true) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(1, 7);"));

    rewriteGeneratorBody(
        "if (i < 1) { if (i < 2) {yield 2; } } else { yield 1; }",
        lines(
            "  if (i < 1) {",
            "    if (!(i < 2)) {",
            "      $jscomp$generator$context.jumpTo(0);",
            "      break;",
            "    }",
            "    return $jscomp$generator$context.yield(2, 0);",
            "  }",
            "  return $jscomp$generator$context.yield(1, 0)"));

    rewriteGeneratorBody(
        "if (i < 1) { if (i < 2) {yield 2; } else { yield 3; } } else { yield 1; }",
        lines(
            "  if (i < 1) {",
            "    if (i < 2) {",
            "      return $jscomp$generator$context.yield(2, 0);",
            "    }",
            "    return $jscomp$generator$context.yield(3, 0);",
            "  }",
            "  return $jscomp$generator$context.yield(1, 0)"));
  }

  public void testReturn() {
    rewriteGeneratorBody(
        "return 1;",
        "return $jscomp$generator$context.return(1);");

    rewriteGeneratorBodyWithVars(
        "return this;",
        "/** @const */ var $jscomp$generator$this = this;",
        lines(
            "return $jscomp$generator$context.return($jscomp$generator$this);"));

    rewriteGeneratorBodyWithVars(
        "return this.test({value: this});",
        "/** @const */ var $jscomp$generator$this = this;",
        lines(
            "return $jscomp$generator$context.return(",
            "    $jscomp$generator$this.test({value: $jscomp$generator$this}));"));

    rewriteGeneratorBodyWithVars(
        "return this[yield];",
        "/** @const */ var $jscomp$generator$this = this;",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  return $jscomp$generator$context.return(",
            "      $jscomp$generator$this[$jscomp$generator$context.yieldResult]);"));
  }

  public void testBreakContinue() {
    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; break; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 4);",
            "case 4:",
            "  $jscomp$generator$context.jumpTo(0);",
            "  break;",
            "  $jscomp$generator$context.jumpTo(2)",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; continue; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 4);",
            "case 4:",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;",
            "  $jscomp$generator$context.jumpTo(2)",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; break; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 5);",
            "case 5:",
            "  $jscomp$generator$context.jumpTo(0);",
            "  break;",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2)",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; continue; }",
        "var j;",
        lines(
            "  j = 0;",
            "case 2:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(j, 5);",
            "case 5:",
            "  $jscomp$generator$context.jumpTo(3);",
            "  break;",
            "case 3:",
            "  j++;",
            "  $jscomp$generator$context.jumpTo(2)",
            "  break;"));
  }

  public void testDoWhileLoops() {
    rewriteGeneratorBody(
        "do { yield j; } while (j < 10);",
        lines(
            "  return $jscomp$generator$context.yield(j, 4);",
            "case 4:",
            "  if (j<10) {",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBody(
        "do {} while (yield 1);",
        lines(
            "  return $jscomp$generator$context.yield(1, 5);",
            "case 5:",
            "  if ($jscomp$generator$context.yieldResult) {",
            "    $jscomp$generator$context.jumpTo(1);",
            "    break;",
            "  }",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testYieldNoValue() {
    rewriteGeneratorBody(
        "yield;",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 0);"));
  }

  public void testReturnNoValue() {
    rewriteGeneratorBody(
        "return;",
        "return $jscomp$generator$context.return();");
  }

  public void testYieldExpression() {
    rewriteGeneratorBody(
        "return (yield 1);",
        lines(
            "  return $jscomp$generator$context.yield(1, 2);",
            "case 2:",
            "  return $jscomp$generator$context.return($jscomp$generator$context.yieldResult);"));
  }

  public void testFunctionInGenerator() {
    rewriteGeneratorBodyWithVars(
        "function g() {}",
        "function g() {}",
        "  $jscomp$generator$context.jumpToEnd();");
  }

  public void testYieldAll() {
    rewriteGeneratorBody(
        "yield * n;",
        lines(
            "  return $jscomp$generator$context.yieldAll(n, 0);"));

    rewriteGeneratorBodyWithVars(
        "var i = yield * n;",
        "var i;",
        lines(
            "  return $jscomp$generator$context.yieldAll(n, 2);",
            "case 2:",
            "  i=$jscomp$generator$context.yieldResult;",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testYieldArguments() {
    rewriteGeneratorBodyWithVars(
        "yield arguments[0];",
        "/** @const */ var $jscomp$generator$arguments = arguments;",
        lines(
            "  return $jscomp$generator$context.yield($jscomp$generator$arguments[0], 0);"));
  }

  public void testYieldThis() {
    rewriteGeneratorBodyWithVars(
        "yield this;",
        "/** @const */ var $jscomp$generator$this = this;",
        lines(
            "  return $jscomp$generator$context.yield($jscomp$generator$this, 0);"));
  }

  public void testGeneratorShortCircuit() {
    rewriteGeneratorBodyWithVars(
        "0 || (yield 1);",
        "var JSCompiler_temp$jscomp$0;",
        lines(
            "  if(JSCompiler_temp$jscomp$0 = 0) {",
            "    $jscomp$generator$context.jumpTo(2);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(1, 3);",
            "case 3:",
            "  JSCompiler_temp$jscomp$0=$jscomp$generator$context.yieldResult;",
            "case 2:",
            "  JSCompiler_temp$jscomp$0;",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "0 && (yield 1);",
        "var JSCompiler_temp$jscomp$0;",
        lines(
            "  if(!(JSCompiler_temp$jscomp$0=0)) {",
            "    $jscomp$generator$context.jumpTo(2);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(1, 3);",
            "case 3:",
            "  JSCompiler_temp$jscomp$0=$jscomp$generator$context.yieldResult;",
            "case 2:",
            "  JSCompiler_temp$jscomp$0;",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "0 ? 1 : (yield 1);",
        "var JSCompiler_temp$jscomp$0;",
        lines(
            "  if(0) {",
            "    JSCompiler_temp$jscomp$0 = 1;",
            "    $jscomp$generator$context.jumpTo(2);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(1, 3);",
            "case 3:",
            "  JSCompiler_temp$jscomp$0 = $jscomp$generator$context.yieldResult;",
            "case 2:",
            "  JSCompiler_temp$jscomp$0;",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testVar() {
    rewriteGeneratorBodyWithVars(
        "var a = 10, b, c = yield 10, d = yield 20, f, g='test';",
        "var a, b; var c; var d, f, g;",
        lines(
            "  a = 10;",
            "  return $jscomp$generator$context.yield(10, 2);",
            "case 2:",
            "  c = $jscomp$generator$context.yieldResult;",
            "  return $jscomp$generator$context.yield(20, 3);",
            "case 3:",
            "  d = $jscomp$generator$context.yieldResult, g = 'test';",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        lines(
            "var /** @const @type {number} */ a = 10, b, c = yield 10, d = yield 20, f, g='test';"),
        lines(
            "var /** @type {number} */ a, b;",
            "var c;", // note that the yields cause the var declarations to be split up
            "var d, f, g;"),
        lines(
            "  /** @const @type {number} */ a = 10;",
            "  return $jscomp$generator$context.yield(10, 2);",
            "case 2:",
            "  c = $jscomp$generator$context.yieldResult;",
            "  return $jscomp$generator$context.yield(20, 3);",
            "case 3:",
            "  d = $jscomp$generator$context.yieldResult, g = 'test';",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testYieldSwitch() {
    rewriteGeneratorBody(
        lines(
            "while (1) {",
            "  switch (i) {",
            "    case 1:",
            "      ++i;",
            "      break;",
            "    case 2:",
            "      yield 3;",
            "      continue;",
            "    case 10:",
            "    case 3:",
            "      yield 4;",
            "    case 4:",
            "      return 1;",
            "    case 5:",
            "      return 2;",
            "    default:",
            "      yield 5;",
            "  }",
            "}"),
        lines(
            "  if (!1) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  switch (i) {",
            "    case 1: ++i; break;",
            "    case 2: return $jscomp$generator$context.jumpTo(4);",
            "    case 10:",
            "    case 3: return $jscomp$generator$context.jumpTo(5);",
            "    case 4: return $jscomp$generator$context.jumpTo(6);",
            "    case 5: return $jscomp$generator$context.return(2);",
            "    default: return $jscomp$generator$context.jumpTo(7);",
            "  }",
            "  $jscomp$generator$context.jumpTo(1);",
            "  break;",
            "case 4: return $jscomp$generator$context.yield(3, 9);",
            "case 9:",
            "  $jscomp$generator$context.jumpTo(1);",
            "  break;",
            "case 5: return $jscomp$generator$context.yield(4, 6);",
            "case 6: return $jscomp$generator$context.return(1);",
            "case 7: return $jscomp$generator$context.yield(5, 1);"));

    rewriteGeneratorBody(
        lines(
          "switch (yield) {",
          "  default:",
          "  case 1:",
          "    yield 1;}"),
        lines(
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  switch ($jscomp$generator$context.yieldResult) {",
            "    default:",
            "    case 1:",
            "      return $jscomp$generator$context.jumpTo(3)",
            "  }",
            "  $jscomp$generator$context.jumpTo(0);",
            "  break;",
            "case 3: return $jscomp$generator$context.yield(1, 0);"));
  }

  public void testNoTranslate() {
    rewriteGeneratorBody(
        "if (1) { try {} catch (e) {} throw 1; }",
        lines(
            "  if (1) { try {} catch (e) {} throw 1; }",
            "  $jscomp$generator$context.jumpToEnd();"));
  }

  public void testForIn() {
    rewriteGeneratorBody(
        "for (var i in yield) { }",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 2);",
            "case 2:",
            "  for (var i in $jscomp$generator$context.yieldResult) { }",
            "  $jscomp$generator$context.jumpToEnd();"));


    rewriteGeneratorBodyWithVars(
        "for (var i in j) { yield i; }",
        "var i, $jscomp$generator$forin$0;",
        lines(
            "  $jscomp$generator$forin$0 = $jscomp$generator$context.forIn(j);",
            "case 2:",
            "  if (!((i = $jscomp$generator$forin$0.getNext()) != null)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(i, 2);"));

    rewriteGeneratorBodyWithVars(
        "for (var i in yield) { yield i; }",
        "var i, $jscomp$generator$forin$0;",
        lines(
            "  return $jscomp$generator$context.yield(undefined, 2)",
            "case 2:",
            "  $jscomp$generator$forin$0 = ",
            "      $jscomp$generator$context.forIn($jscomp$generator$context.yieldResult);",
            "case 3:",
            "  if (!((i = $jscomp$generator$forin$0.getNext()) != null)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  return $jscomp$generator$context.yield(i, 3);"));

    rewriteGeneratorBodyWithVars(
        "for (i[yield] in j) {}",
        "var $jscomp$generator$forin$0; var JSCompiler_temp_const$jscomp$1;",
        lines(
            "  $jscomp$generator$forin$0 = $jscomp$generator$context.forIn(j);",
            "case 2:",
            "  JSCompiler_temp_const$jscomp$1 = i;",
            "  return $jscomp$generator$context.yield(undefined, 5);",
            "case 5:",
            "  if (!((JSCompiler_temp_const$jscomp$1[$jscomp$generator$context.yieldResult] =",
            "      $jscomp$generator$forin$0.getNext()) != null)) {",
            "    $jscomp$generator$context.jumpTo(0);",
            "    break;",
            "  }",
            "  $jscomp$generator$context.jumpTo(2);",
            "  break;"));
  }

  public void testTryCatch() {
    rewriteGeneratorBodyWithVars(
        "try {yield 1;} catch (e) {}",
        "var e;",
        lines(
            "  $jscomp$generator$context.setCatchFinallyBlocks(2);",
            "  return $jscomp$generator$context.yield(1, 4);",
            "case 4:",
            "  $jscomp$generator$context.leaveTryBlock(0)",
            "  break;",
            "case 2:",
            "  e=$jscomp$generator$context.enterCatchBlock();",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        lines(
            "try {yield 1;} catch (e) {}",
            "try {yield 1;} catch (e) {}"),
        "var e;",
        lines(
            "  $jscomp$generator$context.setCatchFinallyBlocks(2);",
            "  return $jscomp$generator$context.yield(1, 4);",
            "case 4:",
            "  $jscomp$generator$context.leaveTryBlock(3)",
            "  break;",
            "case 2:",
            "  e=$jscomp$generator$context.enterCatchBlock();",
            "case 3:",
            "  $jscomp$generator$context.setCatchFinallyBlocks(5);",
            "  return $jscomp$generator$context.yield(1, 7);",
            "case 7:",
            "  $jscomp$generator$context.leaveTryBlock(0)",
            "  break;",
            "case 5:",
            "  e = $jscomp$generator$context.enterCatchBlock();",
            "  $jscomp$generator$context.jumpToEnd();"));

    rewriteGeneratorBodyWithVars(
        "l1: try { break l1; } catch (e) { yield; } finally {}",
        "var e;",
        lines(
            "  $jscomp$generator$context.setCatchFinallyBlocks(3, 4);",
            "  $jscomp$generator$context.jumpThroughFinallyBlocks(0);",
            "  break;",
            "case 4:",
            "  $jscomp$generator$context.enterFinallyBlock();",
            "  $jscomp$generator$context.leaveFinallyBlock(0);",
            "  break;",
            "case 3:",
            "  e = $jscomp$generator$context.enterCatchBlock();",
            "  return $jscomp$generator$context.yield(undefined, 4)"));
  }

  public void testFinally() {
    rewriteGeneratorBodyWithVars(
        "try {yield 1;} catch (e) {} finally {b();}",
        "var e;",
        lines(
            "  $jscomp$generator$context.setCatchFinallyBlocks(2, 3);",
            "  return $jscomp$generator$context.yield(1, 3);",
            "case 3:",
            "  $jscomp$generator$context.enterFinallyBlock();",
            "  b();",
            "  $jscomp$generator$context.leaveFinallyBlock(0);",
            "  break;",
            "case 2:",
            "  e = $jscomp$generator$context.enterCatchBlock();",
            "  $jscomp$generator$context.jumpTo(3);",
            "  break;"));

    rewriteGeneratorBodyWithVars(
        lines(
            "try {",
            "  try {",
            "    yield 1;",
            "    throw 2;",
            "  } catch (x) {",
            "    throw yield x;",
            "  } finally {",
            "    yield 5;",
            "  }",
            "} catch (thrown) {",
            "  yield thrown;",
            "}"),
        "var x; var thrown;",
        lines(
            "  $jscomp$generator$context.setCatchFinallyBlocks(2);",
            "  $jscomp$generator$context.setCatchFinallyBlocks(4, 5);",
            "  return $jscomp$generator$context.yield(1, 7);",
            "case 7:",
            "  throw 2;",
            "case 5:",
            "  $jscomp$generator$context.enterFinallyBlock(2);",
            "  return $jscomp$generator$context.yield(5, 8);",
            "case 8:",
            "  $jscomp$generator$context.leaveFinallyBlock(6);",
            "  break;",
            "case 4:",
            "  x = $jscomp$generator$context.enterCatchBlock();",
            "  return $jscomp$generator$context.yield(x, 9);",
            "case 9:",
            "  throw $jscomp$generator$context.yieldResult;",
            "  $jscomp$generator$context.jumpTo(5);",
            "  break;",
            "case 6:",
            "  $jscomp$generator$context.leaveTryBlock(0);",
            "  break;",
            "case 2:",
            "  thrown = $jscomp$generator$context.enterCatchBlock();",
            "  return $jscomp$generator$context.yield(thrown,0)"));
  }

  /** Tests correctness of type information after transpilation */
  public void testYield_withTypes() {
    Node returnNode =
        testAndReturnBodyForNumericGenerator(
                "yield 1 + 2;", "", "return $jscomp$generator$context.yield(1 + 2, 0);")
            .getSecondChild()
            .getFirstChild();

    checkState(returnNode.isReturn(), returnNode);
    Node callNode = returnNode.getFirstChild();
    checkState(callNode.isCall(), callNode);
    // TODO(lharker): this should really be {value: number} and may indicate a bug in OTI
    // Possibly the same as https://github.com/google/closure-compiler/issues/2867.
    assertEquals("{value: VALUE}", callNode.getJSType().toString());

    Node yieldFn = callNode.getFirstChild();
    Node jscompGeneratorContext = yieldFn.getFirstChild();
    assertTrue(yieldFn.getJSType().isFunctionType());
    assertEquals(
        "$jscomp.generator.Context<number>", jscompGeneratorContext.getJSType().toString());

    // Check types on "1 + 2" are still present after transpilation
    Node yieldedValue = callNode.getSecondChild(); // 1 + 2

    checkState(yieldedValue.isAdd(), yieldedValue);
    assertEquals("number", yieldedValue.getJSType().toString());
    assertEquals("number", yieldedValue.getFirstChild().getJSType().toString()); // 1
    assertEquals("number", yieldedValue.getSecondChild().getJSType().toString()); // 2

    Node zero = yieldedValue.getNext();
    checkState(0 == zero.getDouble(), zero);
    assertEquals("number", zero.getJSType().toString());
  }

  public void testYieldAll_withTypes() {
    Node returnNode =
        testAndReturnBodyForNumericGenerator(
                "yield * [1, 2];", "", "return $jscomp$generator$context.yieldAll([1, 2], 0);")
            .getSecondChild()
            .getFirstChild();

    checkState(returnNode.isReturn(), returnNode);
    Node callNode = returnNode.getFirstChild();
    checkState(callNode.isCall(), callNode);
    // TODO(lharker): this really should be {value: number}
    assertEquals("(undefined|{value: VALUE})", callNode.getJSType().toString());

    Node yieldAllFn = callNode.getFirstChild();
    checkState(yieldAllFn.isGetProp());
    assertTrue(yieldAllFn.getJSType().isFunctionType());

    // Check that the original types on "[1, 2]" are still present after transpilation
    Node yieldedValue = callNode.getSecondChild(); // [1, 2]

    checkState(yieldedValue.isArrayLit(), yieldedValue);
    assertEquals("Array", yieldedValue.getJSType().toString()); // [1, 2]
    assertEquals("number", yieldedValue.getFirstChild().getJSType().toString()); // 1
    assertEquals("number", yieldedValue.getSecondChild().getJSType().toString()); // 2

    Node zero = yieldedValue.getNext();
    checkState(0 == zero.getDouble(), zero);
    assertEquals("number", zero.getJSType().toString());
  }

  public void testGeneratorForIn_withTypes() {
    Node case1Node =
        testAndReturnBodyForNumericGenerator(
            "for (var i in []) { yield 3; };",
            "var i, $jscomp$generator$forin$0;",
            lines(
                "$jscomp$generator$forin$0 = $jscomp$generator$context.forIn([]);",
                "case 2:",
                "if (!((i = $jscomp$generator$forin$0.getNext()) != null)) {",
                "  $jscomp$generator$context.jumpTo(4);",
                "  break;",
                "}",
                "return $jscomp$generator$context.yield(3, 2);",
                "case 4:",
                ";",
                "$jscomp$generator$context.jumpToEnd();"));

    // $jscomp$generator$forin$0 = $jscomp$generator$context.forIn([]);
    Node assign = case1Node.getSecondChild().getFirstFirstChild();
    checkState(assign.isAssign(), assign);
    assertEquals("$jscomp.generator.Context.PropertyIterator", assign.getJSType().toString());
    assertEquals(
        "$jscomp.generator.Context.PropertyIterator",
        assign.getFirstChild().getJSType().toString());
    assertEquals(
        "$jscomp.generator.Context.PropertyIterator",
        assign.getSecondChild().getJSType().toString());

    // if (!((i = $jscomp$generator$forin$0.getNext()) != null)) {
    Node case2Node = case1Node.getNext();
    Node ifNode = case2Node.getSecondChild().getFirstChild();
    checkState(ifNode.isIf(), ifNode);
    Node ifCond = ifNode.getFirstChild();
    checkState(ifCond.isNot(), ifCond);
    assertEquals("boolean", ifCond.getJSType().toString());
    Node ne = ifCond.getFirstChild();
    assertEquals("boolean", ifCond.getJSType().toString());

    Node lhs = ne.getFirstChild(); // i = $jscomp$generator$forin$0.getNext()
    assertEquals("(null|string)", lhs.getJSType().toString());
    assertEquals("(null|string)", lhs.getFirstChild().getJSType().toString());
    assertEquals("(null|string)", lhs.getSecondChild().getJSType().toString());
    Node getNextFn = lhs.getSecondChild().getFirstChild();
    assertTrue(getNextFn.getJSType().isFunctionType());

    Node rhs = ne.getSecondChild();
    checkState(rhs.isNull(), rhs);
    assertEquals("null", rhs.getJSType().toString());

    // $jscomp$generator$context.jumpToEnd()
    Node case4Node = case2Node.getNext();
    Node jumpToEndCall = case4Node.getSecondChild().getFirstChild().getNext().getFirstChild();
    checkState(jumpToEndCall.isCall());

    Node jumpToEndFn = jumpToEndCall.getFirstChild();
    Node jscompGeneratorContext = jumpToEndFn.getFirstChild();

    assertEquals("undefined", jumpToEndCall.getJSType().toString());
    assertEquals(
        "$jscomp.generator.Context<number>", jscompGeneratorContext.getJSType().toString());
  }

  public void testGeneratorTryCatch_withTypes() {
    Node case0Node =
        testAndReturnBodyForNumericGenerator(
            "try {yield 1;} catch (e) {}",
            "var e;",
            lines(
                "  $jscomp$generator$context.setCatchFinallyBlocks(2);",
                "  return $jscomp$generator$context.yield(1, 4);",
                "case 4:",
                "  $jscomp$generator$context.leaveTryBlock(0)",
                "  break;",
                "case 2:",
                "  e=$jscomp$generator$context.enterCatchBlock();",
                "  $jscomp$generator$context.jumpToEnd();"));
    Node case2Node = case0Node.getNext().getNext();

    // Test that "e = $jscomp$generator$context.enterCatchBlock();" has the unknown type
    Node eAssign = case2Node.getSecondChild().getFirstFirstChild();
    checkState(eAssign.isAssign(), eAssign);
    assertEquals("?", eAssign.getJSType().toString());
    assertEquals("?", eAssign.getFirstChild().getJSType().toString());
    assertEquals("?", eAssign.getSecondChild().getJSType().toString());

    Node enterCatchBlockFn = eAssign.getSecondChild().getFirstChild();
    checkState(enterCatchBlockFn.isGetProp());
    assertTrue(enterCatchBlockFn.getJSType().isFunctionType());
  }

  public void testGeneratorMultipleVars_withTypes() {
    Node case0Node =
        testAndReturnBodyForNumericGenerator(
            "var a = 1, b = '2';",
            "var a, b;",
            "a = 1, b = '2'; $jscomp$generator$context.jumpToEnd();");
    Node comma = case0Node.getSecondChild().getFirstFirstChild();
    checkState(comma.isComma(), comma);
    assertEquals("string", comma.getJSType().toString());

    // a = 1
    Node assignA = comma.getFirstChild();
    checkState(assignA.isAssign(), assignA);
    assertEquals("number", assignA.getJSType().toString());
    assertEquals("number", assignA.getFirstChild().getJSType().toString());
    assertEquals("number", assignA.getSecondChild().getJSType().toString());

    // b = '2';
    Node assignB = comma.getSecondChild();
    checkState(assignB.isAssign(), assignB);
    assertEquals("string", assignB.getJSType().toString());
    assertEquals("string", assignB.getFirstChild().getJSType().toString());
    assertEquals("string", assignB.getSecondChild().getJSType().toString());
  }

  /**
   * Tests that the given generator transpiles to the given body, and does some basic checks on the
   * transpiled generator.
   *
   * @return The first case statement in the switch inside the transpiled generator
   */
  private Node testAndReturnBodyForNumericGenerator(
      String beforeBody, String varDecls, String afterBody) {
    rewriteGeneratorBodyWithVarsAndReturnType(
        beforeBody, varDecls, afterBody, "!Generator<number>");

    Node transpiledGenerator = getLastCompiler().getJsRoot().getLastChild().getLastChild();
    Node program = getAndCheckGeneratorProgram(transpiledGenerator);

    Node programBlock = NodeUtil.getFunctionBody(program);
    // do switch ($jscomp$generator.context.nextAddress) {"
    Node doNode = programBlock.getFirstChild();
    Node switchNode = doNode.getFirstFirstChild();
    checkState(switchNode.isSwitch());
    Node nextAddress = switchNode.getFirstChild();
    assertEquals("number", nextAddress.getJSType().toString());

    return switchNode.getSecondChild();
  }

  /** Get the "program" function from a tranpsiled generator */
  private Node getAndCheckGeneratorProgram(Node genFunction) {
    Node returnNode = genFunction.getLastChild().getLastChild();
    Node callNode = returnNode.getFirstChild();
    checkState(callNode.isCall(), callNode);

    Node createGenerator = callNode.getFirstChild();
    assertTrue(createGenerator.getJSType().isFunctionType()); // $jscomp.generator.createGenerator
    assertEquals(
        "Generator<number>",
        createGenerator.getJSType().toMaybeFunctionType().getReturnType().toString());

    Node program = createGenerator.getNext().getNext();

    assertTrue("Expected function: " + program.getJSType(), program.getJSType().isFunctionType());
    assertEquals("?", program.getJSType().toMaybeFunctionType().getReturnType().toString());
    return program;
  }
}
