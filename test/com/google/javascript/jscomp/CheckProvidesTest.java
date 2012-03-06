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

import com.google.javascript.jscomp.CheckLevel;

/**
 * Tests for {@link CheckProvides}.
 *
 */
public class CheckProvidesTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckProvides(compiler, CheckLevel.WARNING);
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

  public void testNoProvideInnerClass() {
    testSame(
        "goog.provide('X');\n" +
        "/** @constructor */ function X(){};" +
        "/** @constructor */ X.Y = function(){};");
  }

  public void testMissingGoogProvide(){
    String[] js = new String[]{"/** @constructor */ X = function(){};"};
    String warning = "missing goog.provide('X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testMissingGoogProvideWithNamespace(){
    String[] js = new String[]{"goog = {}; " +
                               "/** @constructor */ goog.X = function(){};"};
    String warning = "missing goog.provide('goog.X')";
    test(js, js, null, MISSING_PROVIDE_WARNING, warning);
  }

  public void testGoogProvideInWrongFileShouldCreateWarning(){
    String bad = "/** @constructor */ X = function(){};";
    String good = "goog.provide('X'); goog.provide('Y');" +
                  "/** @constructor */ X = function(){};" +
                  "/** @constructor */ Y = function(){};";
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
  }
}
