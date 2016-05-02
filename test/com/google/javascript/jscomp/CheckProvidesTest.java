/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckProvides.MISSING_PROVIDE_WARNING;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests for {@link CheckProvides}.
 *
 */
public final class CheckProvidesTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckProvides(compiler);
  }

  public void testIrrelevant() {
    testSame("var str = 'g4';");
  }

  public void testHarmlessProcedural() {
    testSame("goog.provide('X'); /** @constructor */ function X(){};");
  }

  public void testHarmless() {
    String js = "goog.provide('X'); /** @constructor */ X = function(){};";
    testSame(js);
  }

  public void testHarmlessEs6Class() {
    testSameEs6("goog.provide('X'); var X = class {};");
    testSameEs6("goog.provide('X'); class X {};");
    testSameEs6("goog.provide('foo.bar.X'); foo.bar.X = class {};");
  }

  public void testMissingProvideEs6Class() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    String js = "goog.require('Y'); class X {};";
    String warning = "missing goog.provide('X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);

    js = "goog.require('Y'); var X = class {};";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);

    js = "goog.require('Y'); foo.bar.X = class {};";
    warning = "missing goog.provide('foo.bar.X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testNoProvideInnerClass() {
    testSame(
        "goog.provide('X');\n" +
        "/** @constructor */ function X(){};" +
        "/** @constructor */ X.Y = function(){};");
  }

  public void testMissingGoogProvide(){
    String[] js = new String[] {"goog.require('Y'); /** @constructor */ X = function(){};"};
    String warning = "missing goog.provide('X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testMissingGoogProvideWithNamespace(){
    String[] js =
        new String[] {"goog = {}; goog.require('Y'); /** @constructor */ goog.X = function(){};"};
    String warning = "missing goog.provide('goog.X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testMissingGoogProvideWithinGoogScope(){
    String[] js = new String[]{
        "/** @constructor */ $jscomp.scope.bar = function() {};"};
    test(js, js);
  }

  public void testGoogProvideInWrongFileShouldCreateWarning(){
    String good = "goog.provide('X'); goog.provide('Y');"
        + "/** @constructor */ X = function(){};"
        + "/** @constructor */ Y = function(){};";
    String bad = "goog.require('Z'); /** @constructor */ X = function(){};";
    String[] js = new String[] {good, bad};
    String warning = "missing goog.provide('X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testGoogProvideMissingConstructorIsOkForNow(){
    // TODO(user) to prevent orphan goog.provide calls, the pass would have to
    // account for enums, static functions and constants
    testSame(new String[]{"goog.provide('Y'); X = function(){};"});
  }

  public void testIgnorePrivateConstructor() {
    String js = "/** @constructor*/ X_ = function(){};";
    testSame(js);
  }

  public void testIgnorePrivatelyAnnotatedConstructor() {
    testSame("/** @private\n@constructor */ X = function(){};");
    testSame("/** @constructor\n@private */ X = function(){};");

    testSameEs6("/** @private */ var X = class {};");
    testSameEs6("/** @private */ let X = class {};");
    testSameEs6("/** @private */ X = class {};");

    testSameEs6("/** @private */ var X = class Y {};");
    testSameEs6("/** @private */ let X = class Y {};");
    testSameEs6("/** @private */ X = class Y {};");

    testSameEs6("/** @private */ class X {}");
  }

  public void testIgnorePrivateByConventionConstructor() {
    testSame("/** @constructor */ privateFn_ = function(){};");
    testSame("/** @constructor */ privateFn_ = function(){};");

    testSameEs6("var privateCls_ = class {};");
    testSameEs6("let privateCls_ = class {};");
    testSameEs6("privateCls_ = class {};");

    testSameEs6("class privateCls_ {}");
  }

  public void testArrowFunction() {
    testSameEs6("/** @constructor*/ X = ()=>{};");
  }
}
