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

/** Unit tests for {@link Es6RewriteGenerators}. */
// TODO(tbreisacher): Rewrite direct calls to test() to use rewriteGeneratorBody
// or rewriteGeneratorBodyWithVars.
public final class Es6RewriteGeneratorsTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    runTypeCheckAfterProcessing = true;
    compareJsDoc = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new Es6RewriteGenerators(compiler);
  }

  public void rewriteGeneratorBody(String beforeBody, String afterBody) {
    rewriteGeneratorBodyWithVars(beforeBody, "", afterBody);
  }

  public void rewriteGeneratorBodyWithVars(
      String beforeBody, String varDecls, String afterBody) {
    test(
        "function *f() {" + beforeBody + "}",
        LINE_JOINER.join(
            "/** @suppress {uselessCode} */",
            "function f() {",
            "  var $jscomp$generator$state = 0;",
            varDecls,
            "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "      $jscomp$generator$throw$arg) {",
            "    while (1) switch ($jscomp$generator$state) {",
            afterBody,
            "      default:",
            "        return {value: undefined, done: true};",
            "    }",
            "  }",
            "  var iterator = /** @type {!Generator<?>} */ ({",
            "    next: function(arg) { return $jscomp$generator$impl(arg, undefined); },",
            "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "    return: function(arg) { throw Error('Not yet implemented'); },",
            "  });",
            "  $jscomp.initSymbolIterator();",
            "  /** @this {!Generator<?>} */",
            "  iterator[Symbol.iterator] = function() { return this; };",
            "  return iterator;",
            "}"));
  }

  public void testSimpleGenerator() {
    rewriteGeneratorBody(
        "",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = -1;"));
    assertThat(((NoninjectingCompiler) getLastCompiler()).injected).containsExactly("es6_runtime");

    rewriteGeneratorBody(
        "yield 1;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = 1;",
            "  return {value: 1, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    test(
        "/** @param {*} a */ function *f(a, b) {}",
        LINE_JOINER.join(
            "/** @param {*} a @suppress {uselessCode} */",
            "function f(a, b) {",
            "  var $jscomp$generator$state = 0;",
            "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "      $jscomp$generator$throw$arg) {",
            "    while (1) switch ($jscomp$generator$state) {",
            "      case 0:",
            "        $jscomp$generator$state = -1;",
            "      default:",
            "        return {value: undefined, done: true}",
            "    }",
            "  }",
            "  var iterator = /** @type {!Generator<?>} */ ({",
            "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "    return: function(arg) { throw Error('Not yet implemented'); },",
            "  });",
            "  $jscomp.initSymbolIterator();",
            "  /** @this {!Generator<?>} */",
            "  iterator[Symbol.iterator] = function() { return this; };",
            "  return iterator;",
            "}"));

    rewriteGeneratorBodyWithVars(
        "var i = 0, j = 2",
        "var j; var i;",
        LINE_JOINER.join(
            "case 0:",
            "  i = 0;",
            "  j = 2;",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "var i = 0; yield i; i = 1; yield i; i = i + 1; yield i;",
        "var i;",
        LINE_JOINER.join(
            "case 0:",
            "  i = 0;",
            "  $jscomp$generator$state = 1;",
            "  return {value: i, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  i = 1;",
            "  $jscomp$generator$state = 3;",
            "  return {value: i, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  i = i + 1;",
            "  $jscomp$generator$state = 5;",
            "  return {value: i, done: false};",
            "case 5:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 6; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 6:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testReturnGenerator() {
    test(
        "function f() { return function *g() {yield 1;} }",
        LINE_JOINER.join(
            "function f() {",
            "  return /** @suppress {uselessCode} */ function g() {",
            "    var $jscomp$generator$state = 0;",
            "    function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "        $jscomp$generator$throw$arg) {",
            "      while (1) switch ($jscomp$generator$state) {",
            "        case 0:",
            "          $jscomp$generator$state = 1;",
            "          return {value: 1, done: false};",
            "        case 1:",
            "          if (!($jscomp$generator$throw$arg !== undefined)) {",
            "            $jscomp$generator$state = 2; break;",
            "          }",
            "          $jscomp$generator$state = -1;",
            "          throw $jscomp$generator$throw$arg;",
            "        case 2:",
            "          $jscomp$generator$state = -1;",
            "        default:",
            "          return {value: undefined, done: true}",
            "      }",
            "    }",
            "    var iterator = /** @type {!Generator<?>} */ ({",
            "      next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "      throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "      return: function(arg) { throw Error('Not yet implemented'); },",
            "    });",
            "    $jscomp.initSymbolIterator();",
            "    /** @this {!Generator<?>} */",
            "    iterator[Symbol.iterator] = function() { return this; };",
            "    return iterator;",
            "  }",
            "}"));
  }

  public void testNestedGenerator() {
    test(
        "function *f() { function *g() {yield 2;} yield 1; }",
        LINE_JOINER.join(
            "/** @suppress {uselessCode} */",
            "function f() {",
            "  var $jscomp$generator$state = 0;",
            "  /** @suppress {uselessCode} */",
            "  function g() {",
            "    var $jscomp$generator$state = 0;",
            "    function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "        $jscomp$generator$throw$arg) {",
            "      while (1) switch ($jscomp$generator$state) {",
            "        case 0:",
            "          $jscomp$generator$state = 1;",
            "           return {value: 2, done: false};",
            "        case 1:",
            "          if (!($jscomp$generator$throw$arg !== undefined)) {",
            "            $jscomp$generator$state = 2; break;",
            "          }",
            "          $jscomp$generator$state = -1;",
            "          throw $jscomp$generator$throw$arg;",
            "        case 2:",
            "          $jscomp$generator$state = -1;",
            "        default:",
            "          return {value: undefined, done: true}",
            "      }",
            "    }",
            "    var iterator = /** @type {!Generator<?>} */ ({",
            "      next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "      throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "      return: function(arg) { throw Error('Not yet implemented'); },",
            "    })",
            "    $jscomp.initSymbolIterator();",
            "    /** @this {!Generator<?>} */",
            "    iterator[Symbol.iterator] = function() { return this; };",
            "    return iterator;",
            "  }",
            "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "      $jscomp$generator$throw$arg) {",
            "    while (1) switch ($jscomp$generator$state) {",
            "      case 0:",
            "        $jscomp$generator$state = 1;",
            "         return {value: 1, done: false};",
            "      case 1:",
            "        if (!($jscomp$generator$throw$arg !== undefined)) {",
            "          $jscomp$generator$state = 2; break;",
            "        }",
            "        $jscomp$generator$state = -1;",
            "        throw $jscomp$generator$throw$arg;",
            "      case 2:",
            "        $jscomp$generator$state = -1;",
            "      default:",
            "        return {value: undefined, done: true}",
            "    }",
            "  }",
            "  var iterator = /** @type {!Generator<?>} */ ({",
            "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "    return: function(arg) { throw Error('Not yet implemented'); },",
            "  });",
            "  $jscomp.initSymbolIterator();",
            "  /** @this {!Generator<?>} */",
            "  iterator[Symbol.iterator] = function() { return this; };",
            "  return iterator;",
            "}"));
  }


  public void testForLoopsGenerator() {
    rewriteGeneratorBodyWithVars(
        "var i = 0; for (var j = 0; j < 10; j++) { i += j; } yield i;",
        "var i;",
        LINE_JOINER.join(
            "case 0:",
            "  i = 0;",
            "  for (var j = 0; j < 10; j++) { i += j; }",
            "  $jscomp$generator$state = 1;",
            "  return {value: i, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
            "  $jscomp$generator$state = 4;",
            "  return {value: j, done: false};",
            "case 4:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 5; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 5:",
            "case 2:",
            "  j++",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 3:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "var i = 0; for (var j = 0; j < 10; j++) { i += j; throw 5; } yield i;",
        "var j; var i;",
        LINE_JOINER.join(
            "case 0:",
            "  i = 0;",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) {",
            "    $jscomp$generator$state = 3;",
            "    break;",
            "  }",
            "  i += j;",
            "  $jscomp$generator$state = -1;",
            "  throw 5;",
            "case 2:",
            "  j++;",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 3:",
            "  $jscomp$generator$state = 4;",
            "  return {value: i, done: false};",
            "case 4:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 5; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 5:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testWhileLoopsGenerator() {
    rewriteGeneratorBodyWithVars(
        "var i = 0; while (i < 10) { i++; i++; i++; } yield i;",
            "  var i;",
        LINE_JOINER.join(
            "case 0:",
            "  i = 0;",
            "  while (i < 10) { i ++; i++; i++; }",
            "  $jscomp$generator$state = 1;",
            "  return {value: i, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; j++; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
            "  $jscomp$generator$state = 3;",
            "  return {value: j, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  j++",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testUndecomposableExpression() {
    testError("function *f() { obj.bar(yield 5); }",
        Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testGeneratorCannotConvertYet() {
    testError("function *f() {switch (i) {default: case 1: yield 1;}}",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("function *f() { l: if (true) { var x = 5; break l; x++; yield x; }; }",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("function *f(b, i) {switch (i) { case (b || (yield 1)): yield 2; }}",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testThrowGenerator() {
    rewriteGeneratorBody(
        "throw 1;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = -1;",
            "  throw 1;",
            "  $jscomp$generator$state = -1;"));
  }

  public void testLabelsGenerator() {
    rewriteGeneratorBody(
        "l: if (true) { break l; }",
        LINE_JOINER.join(
            "case 0:",
            "  l: if (true) { break l; }",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBody(
        "l: for (;;) { yield i; continue l; }",
        LINE_JOINER.join(
            "case 0:",
            "case 1:",
            "  if (!true) { $jscomp$generator$state = 2; break; }",
            "  $jscomp$generator$state = 3;",
            "  return {value: i, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testIfGenerator() {
    rewriteGeneratorBodyWithVars(
        "var j = 0; if (j < 1) { yield j; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "  if (!(j < 1)) { $jscomp$generator$state = 1; break; }",
            "  $jscomp$generator$state = 2;",
            "  return {value: j, done: false};",
            "case 2:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 3; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 3:",
            "case 1:",
            "  $jscomp$generator$state = -1;"));

    test(
        "function *f(i) { if (i < 1) { yield i; } else { yield 1; } }",
        LINE_JOINER.join(
            "/** @suppress {uselessCode} */",
            "function f(i) {",
            "  var $jscomp$generator$state = 0;",
            "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "      $jscomp$generator$throw$arg) {",
            "    while (1) switch ($jscomp$generator$state) {",
            "      case 0:",
            "        if (!(i < 1)) { $jscomp$generator$state = 1; break; }",
            "        $jscomp$generator$state = 3;",
            "        return {value: i, done: false};",
            "      case 3:",
            "        if (!($jscomp$generator$throw$arg !== undefined)) {",
            "          $jscomp$generator$state = 4; break;",
            "        }",
            "        $jscomp$generator$state = -1;",
            "        throw $jscomp$generator$throw$arg;",
            "      case 4:",
            "        $jscomp$generator$state = 2;",
            "        break;",
            "      case 1:",
            "        $jscomp$generator$state = 5;",
            "        return {value: 1, done: false};",
            "      case 5:",
            "        if (!($jscomp$generator$throw$arg !== undefined)) {",
            "          $jscomp$generator$state = 6; break;",
            "        }",
            "        $jscomp$generator$state = -1;",
            "        throw $jscomp$generator$throw$arg;",
            "      case 6:",
            "      case 2:",
            "        $jscomp$generator$state = -1;",
            "      default:",
            "        return {value: undefined, done: true}",
            "    }",
            "  }",
            "  var iterator = /** @type {!Generator<?>} */ ({",
            "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "    return: function(arg) { throw Error('Not yet implemented'); },",
            "  });",
            "  $jscomp.initSymbolIterator();",
            "  /** @this {!Generator<?>} */",
            "  iterator[Symbol.iterator] = function() { return this; };",
            "  return iterator;",
            "}"));
  }

  public void testGeneratorReturn() {
    rewriteGeneratorBody(
        "return 1;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = -1;",
            "  return {value: 1, done: true};",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorBreakContinue() {
    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; break; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
            "  $jscomp$generator$state = 3;",
            "  return {value: j, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  $jscomp$generator$state = 2;",
            "  break;",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "var j = 0; while (j < 10) { yield j; continue; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
            "  $jscomp$generator$state = 3;",
            "  return {value: j, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; break; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
            "  $jscomp$generator$state = 4;",
            "  return {value: j, done: false};",
            "case 4:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 5; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 5:",
            "  $jscomp$generator$state = 3;",
            "  break;",
            "case 2:",
            "  j++;",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 3:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBodyWithVars(
        "for (var j = 0; j < 10; j++) { yield j; continue; }",
        "var j;",
        LINE_JOINER.join(
            "case 0:",
            "  j = 0;",
            "case 1:",
            "  if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
            "  $jscomp$generator$state = 4;",
            "  return {value: j, done: false};",
            "case 4:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 5; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 5:",
            "  $jscomp$generator$state = 2;",
            "  break;",
            "case 2:",
            "  j++;",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 3:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testDoWhileLoopsGenerator() {
    rewriteGeneratorBodyWithVars(
        "do { yield j; } while (j < 10);",
        "var $jscomp$generator$first$do;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$first$do = true;",
            "case 1:",
            "  if (!($jscomp$generator$first$do || j < 10)) {",
            "    $jscomp$generator$state = 3; break; }",
            "  $jscomp$generator$state = 4;",
            "  return {value: j, done: false};",
            "case 4:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 5; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 5:",
            "case 2:",
            "  $jscomp$generator$first$do = false;",
            "  $jscomp$generator$state = 1;",
            "  break",
            "case 3:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testYieldNoValue() {
    rewriteGeneratorBody(
        "yield;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = 1;",
            "  return {value: undefined, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testReturnNoValue() {
    rewriteGeneratorBody(
        "return;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = -1;",
            "  return {value: undefined, done: true};",
            "  $jscomp$generator$state = -1;"));
  }

  public void testYieldExpression() {
    rewriteGeneratorBodyWithVars(
        "return (yield 1);",
        "var $jscomp$generator$next$arg0;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = 1;",
            "  return {value: 1, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$next$arg0 = $jscomp$generator$next$arg;",
            "  $jscomp$generator$state = -1;",
            "  return {value: $jscomp$generator$next$arg0, done: true};",
            "  $jscomp$generator$state = -1;"));
  }

  public void testFunctionInGenerator() {
    test(
        "function *f() { function g() {} }",
        LINE_JOINER.join(
            "/** @suppress {uselessCode} */",
            "function f() {",
            "  var $jscomp$generator$state = 0;",
            "  function g() {}",
            "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
            "      $jscomp$generator$throw$arg) {",
            "    while (1) switch ($jscomp$generator$state) {",
            "      case 0:",
            "        $jscomp$generator$state = -1;",
            "      default:",
            "        return {value: undefined, done: true}",
            "    }",
            "  }",
            "  var iterator = /** @type {!Generator<?>} */ ({",
            "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
            "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
            "    return: function(arg) { throw Error('Not yet implemented'); },",
            "  });",
            "  $jscomp.initSymbolIterator();",
            "  /** @this {!Generator<?>} */",
            "  iterator[Symbol.iterator] = function() { return this; };",
            "  return iterator;",
            "}"));
  }

  public void testYieldAll() {
    rewriteGeneratorBodyWithVars(
        "yield * n;",
        "var $jscomp$generator$yield$entry; var $jscomp$generator$yield$all;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$yield$all = $jscomp.makeIterator(n);",
            "case 1:",
            "  if (!!($jscomp$generator$yield$entry =",
            "      $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {",
            "    $jscomp$generator$state = 2;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = 3;",
            "  return {value: $jscomp$generator$yield$entry.value, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
    assertThat(((NoninjectingCompiler) getLastCompiler()).injected).containsExactly("es6_runtime");

    rewriteGeneratorBodyWithVars(
        "var i = yield * n;",
        "var i;" + "var $jscomp$generator$yield$entry;" + "var $jscomp$generator$yield$all;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$yield$all = $jscomp.makeIterator(n);",
            "case 1:",
            "  if (!!($jscomp$generator$yield$entry =",
            "      $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {",
            "    $jscomp$generator$state = 2;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = 3;",
            "  return {value: $jscomp$generator$yield$entry.value, done: false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 2:",
            "  i = $jscomp$generator$yield$entry.value;",
            "  $jscomp$generator$state = -1;"));
  }

  public void testYieldArguments() {
    rewriteGeneratorBodyWithVars(
        "yield arguments[0];",
        "var $jscomp$generator$arguments = arguments;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = 1;",
            "  return {value: $jscomp$generator$arguments[0], done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testYieldThis() {
    rewriteGeneratorBodyWithVars(
        "yield this;",
        "var $jscomp$generator$this = this;",
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$state = 1;",
            "  return {value: $jscomp$generator$this, done: false};",
            "case 1:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 2; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorShortCircuit() {
    rewriteGeneratorBody(
        "0 || (yield 1);",
        LINE_JOINER.join(
            "case 0:",
            "  if (!0) {",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = 2;",
            "  break;",
            "case 1:",
            "  $jscomp$generator$state = 3;",
            "  return{value:1, done:false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "case 2:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBody(
        "0 && (yield 1);",
        LINE_JOINER.join(
            "case 0:",
            "  if (!0) {",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = 2;",
            "  return{value:1, done:false};",
            "case 2:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 3;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 3:",
            "case 1:",
            "  $jscomp$generator$state = -1;"));

    rewriteGeneratorBody(
        "0 ? 1 : (yield 1);",
        LINE_JOINER.join(
            "case 0:",
            "  if (!0) {",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "  1;",
            "  $jscomp$generator$state = 2;",
            "  break;",
            "case 1:",
            "  $jscomp$generator$state = 3;",
            "  return{value:1, done:false};",
            "case 3:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 4;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 4:",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testYieldSwitch() {
    rewriteGeneratorBodyWithVars(
        LINE_JOINER.join(
            "while (1) {",
            "  switch (i) {",
            "    case 1:",
            "      yield 2;",
            "      break;",
            "    case 2:",
            "      yield 3;",
            "      continue;",
            "    case 3:",
            "      yield 4;",
            "    default:",
            "      yield 5;",
            "  }",
            "}"),
            "var $jscomp$generator$switch$val1; var $jscomp$generator$switch$entered0;",
        LINE_JOINER.join(
            "case 0:",
            "case 1:",
            "  if (!1) {",
            "    $jscomp$generator$state = 2;",
            "    break;",
            "  }",
            "  $jscomp$generator$switch$entered0 = false;",
            "  $jscomp$generator$switch$val1 = i;",
            "  if (!($jscomp$generator$switch$entered0",
            "      || $jscomp$generator$switch$val1 === 1)) {",
            "    $jscomp$generator$state = 4;",
            "    break;",
            "  }",
            "  $jscomp$generator$switch$entered0 = true;",
            "  $jscomp$generator$state = 5;",
            "  return {value: 2, done: false};",
            "case 5:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 6; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 6:",
            "  $jscomp$generator$state = 3;",
            "  break;",
            "case 4:",
            "  if (!($jscomp$generator$switch$entered0",
            "      || $jscomp$generator$switch$val1 === 2)) {",
            "    $jscomp$generator$state = 7;",
            "    break;",
            "  }",
            "  $jscomp$generator$switch$entered0 = true;",
            "  $jscomp$generator$state = 8;",
            "  return {value: 3, done: false};",
            "case 8:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 9; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 9:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 7:",
            "  if (!($jscomp$generator$switch$entered0",
            "      || $jscomp$generator$switch$val1 === 3)) {",
            "    $jscomp$generator$state = 10;",
            "    break;",
            "  }",
            "  $jscomp$generator$switch$entered0 = true;",
            "  $jscomp$generator$state = 11;",
            "  return{value: 4, done: false};",
            "case 11:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 12; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 12:",
            "case 10:",
            "  $jscomp$generator$switch$entered0 = true;",
            "  $jscomp$generator$state = 13;",
            "  return {value: 5, done: false};",
            "case 13:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 14; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 14:",
            "case 3:",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorNoTranslate() {
    rewriteGeneratorBody(
        "if (1) { try {} catch (e) {} throw 1; }",
        LINE_JOINER.join(
            "case 0:",
            "  if (!1) {",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "  try {} catch (e) {}",
            "  $jscomp$generator$state = -1;",
            "  throw 1;",
            "case 1:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorForIn() {
    rewriteGeneratorBodyWithVars(
        "for (var i in j) { yield 1; }",
        LINE_JOINER.join(
            "var i;",
            "var $jscomp$generator$forin$iter0;",
            "var $jscomp$generator$forin$var0;",
            "var $jscomp$generator$forin$array0;"),
        LINE_JOINER.join(
            "case 0:",
            "  $jscomp$generator$forin$array0 = [];",
            "  $jscomp$generator$forin$iter0 = j;",
            "  for (i in $jscomp$generator$forin$iter0) {",
            "    $jscomp$generator$forin$array0.push(i);",
            "  }",
            "  $jscomp$generator$forin$var0 = 0;",
            "case 1:",
            "  if (!($jscomp$generator$forin$var0",
            "      < $jscomp$generator$forin$array0.length)) {",
            "    $jscomp$generator$state = 3;",
            "    break;",
            "  }",
            "  i = $jscomp$generator$forin$array0[$jscomp$generator$forin$var0];",
            "  if (!(!(i in $jscomp$generator$forin$iter0))) {",
            "    $jscomp$generator$state = 4;",
            "    break;",
            "  }",
            "  $jscomp$generator$state = 2;",
            "  break;",
            "case 4:",
            "  $jscomp$generator$state = 5;",
            "  return{value:1, done:false};",
            "case 5:",
            "  if (!($jscomp$generator$throw$arg !== undefined)) {",
            "    $jscomp$generator$state = 6; break;",
            "  }",
            "  $jscomp$generator$state = -1;",
            "  throw $jscomp$generator$throw$arg;",
            "case 6:",
            "case 2:",
            "  $jscomp$generator$forin$var0++;",
            "  $jscomp$generator$state = 1;",
            "  break;",
            "case 3:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorTryCatch() {
    rewriteGeneratorBodyWithVars(
        "try {yield 1;} catch (e) {}",
        "var e; var $jscomp$generator$global$error;",
        LINE_JOINER.join(
            "case 0:",
            "  try {",
            "    $jscomp$generator$state = 3;",
            "    return {value: 1, done: false};",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 3:",
            "  try {",
            "    if (!($jscomp$generator$throw$arg !== undefined)) {",
            "      $jscomp$generator$state = 4; break;",
            "    }",
            "    $jscomp$generator$state = -1;",
            "    throw $jscomp$generator$throw$arg;",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 4:",
            "  try {",
            "    $jscomp$generator$state = 2;",
            "    break;",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 1:",
            "  e = $jscomp$generator$global$error;",
            "case 2:",
            "  $jscomp$generator$state = -1;"));
  }

  public void testGeneratorFinally() {
    rewriteGeneratorBodyWithVars(
        "try {yield 1;} catch (e) {} finally {b();}",
        "var e; var $jscomp$generator$finally0; var $jscomp$generator$global$error;",
        LINE_JOINER.join(
            "case 0:",
            "  try {",
            "    $jscomp$generator$state = 4;",
            "    return {value: 1, done: false};",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 4:",
            "  try {",
            "    if (!($jscomp$generator$throw$arg !== undefined)) {",
            "      $jscomp$generator$state = 5; break;",
            "    }",
            "    $jscomp$generator$state = -1;",
            "    throw $jscomp$generator$throw$arg;",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 5:",
            "  try {",
            "    $jscomp$generator$finally0 = 3;",
            "    $jscomp$generator$state = 2;",
            "    break;",
            "  } catch ($jscomp$generator$e) {",
            "    $jscomp$generator$global$error = $jscomp$generator$e;",
            "    $jscomp$generator$state = 1;",
            "    break;",
            "  }",
            "case 1:",
            "  e = $jscomp$generator$global$error;",
            "  $jscomp$generator$finally0 = 3;",
            "case 2:",
            "  b();",
            "  $jscomp$generator$state = $jscomp$generator$finally0;",
            "  break;",
            "case 3:",
            "  $jscomp$generator$state = -1;"));
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }
}
