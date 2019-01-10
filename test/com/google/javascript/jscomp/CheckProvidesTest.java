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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckProvides}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckProvidesTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckProvides(compiler);
  }

  @Test
  public void testIrrelevant() {
    testSame("var str = 'g4';");
  }

  @Test
  public void testHarmlessProcedural() {
    testSame("goog.provide('X'); /** @constructor */ function X(){};");
  }

  @Test
  public void testHarmless() {
    String js = "goog.provide('X'); /** @constructor */ X = function(){};";
    testSame(js);
  }

  @Test
  public void testHarmlessEs6Class() {
    testSame("goog.provide('X'); var X = class {};");
    testSame("goog.provide('X'); class X {};");
    testSame("goog.provide('foo.bar.X'); foo.bar.X = class {};");
  }

  @Test
  public void testMissingProvideEs6Class() {
    String js = "goog.require('Y'); class X {};";
    String warning = "missing goog.provide('X')";
    testSame(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));


    js = "goog.require('Y'); var X = class {};";
    testSame(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));

    js = "goog.require('Y'); foo.bar.X = class {};";
    warning = "missing goog.provide('foo.bar.X')";
    testSame(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));
  }

  @Test
  public void testNoProvideInnerClass() {
    testSame(
        "goog.provide('X');\n" +
        "/** @constructor */ function X(){};" +
        "/** @constructor */ X.Y = function(){};");
  }

  @Test
  public void testMissingGoogProvide() {
    String[] js = new String[] {"goog.require('Y'); /** @constructor */ X = function(){};"};
    String warning = "missing goog.provide('X')";
    testWarning(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));
  }

  @Test
  public void testMissingGoogProvideWithNamespace() {
    String[] js =
        new String[] {"goog = {}; goog.require('Y'); /** @constructor */ goog.X = function(){};"};
    String warning = "missing goog.provide('goog.X')";
    testWarning(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));
  }

  @Test
  public void testMissingGoogProvideWithinGoogScope() {
    String[] js = new String[]{
        "/** @constructor */ $jscomp.scope.bar = function() {};"};
    test(js, js);
  }

  @Test
  public void testGoogProvideInWrongFileShouldCreateWarning() {
    String good = "goog.provide('X'); goog.provide('Y');"
        + "/** @constructor */ X = function(){};"
        + "/** @constructor */ Y = function(){};";
    String bad = "goog.require('Z'); /** @constructor */ X = function(){};";
    String[] js = new String[] {good, bad};
    String warning = "missing goog.provide('X')";
    testWarning(srcs(js), warning(MISSING_PROVIDE_WARNING).withMessage(warning));
  }

  @Test
  public void testGoogProvideMissingConstructorIsOkForNow() {
    // TODO(user) to prevent orphan goog.provide calls, the pass would have to
    // account for enums, static functions and constants
    testSame(new String[]{"goog.provide('Y'); X = function(){};"});
  }

  @Test
  public void testIgnorePrivateConstructor() {
    String js = "/** @constructor*/ X_ = function(){};";
    testSame(js);
  }

  @Test
  public void testIgnorePrivatelyAnnotatedConstructor() {
    testSame("/** @private\n@constructor */ X = function(){};");
    testSame("/** @constructor\n@private */ X = function(){};");

    testSame("/** @private */ var X = class {};");
    testSame("/** @private */ let X = class {};");
    testSame("/** @private */ X = class {};");

    testSame("/** @private */ var X = class Y {};");
    testSame("/** @private */ let X = class Y {};");
    testSame("/** @private */ X = class Y {};");

    testSame("/** @private */ class X {}");
  }

  @Test
  public void testIgnorePrivateByConventionConstructor() {
    testSame("/** @constructor */ privateFn_ = function(){};");
    testSame("/** @constructor */ privateFn_ = function(){};");

    testSame("var privateCls_ = class {};");
    testSame("let privateCls_ = class {};");
    testSame("privateCls_ = class {};");

    testSame("class privateCls_ {}");
  }

  @Test
  public void testArrowFunction() {
    testSame("/** @constructor*/ X = ()=>{};");
  }
}
