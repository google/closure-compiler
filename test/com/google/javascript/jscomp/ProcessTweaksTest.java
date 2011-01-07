/*
 * Copyright 2010 The Closure Compiler Authors.
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

/**
 * @author agrieve@google.com (Andrew Grieve)
 */
public class ProcessTweaksTest extends CompilerTestCase {

  public ProcessTweaksTest() {
    super("var externMethod;");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ProcessTweaks(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // Only do one repetition, so that we can make sure the first pass keeps
    // GlobalNamespace up to date.
    return 1;
  }

  public void testBasicTweak1() {
    testSame("goog.tweak.registerBoolean('Foo', 'Description');" +
        "goog.tweak.getBoolean('Foo')");
  }

  public void testBasicTweak2() {
    testSame("goog.tweak.registerString('Foo', 'Description');" +
        "goog.tweak.getString('Foo')");
  }

  public void testBasicTweak3() {
    testSame("goog.tweak.registerNumber('Foo', 'Description');" +
        "goog.tweak.getNumber('Foo')");
  }

  public void testBasicTweak4() {
    testSame("goog.tweak.registerButton('Foo', 'Description', function() {})");
  }

  public void testBasicTweak5() {
    testSame("goog.tweak.registerBoolean('A.b_7', 'Description', true, " +
        "{ requiresRestart:false })");
  }

  public void testBasicTweak6() {
    testSame("var opts = { requiresRestart:false };" +
        "goog.tweak.registerBoolean('Foo', 'Description', true, opts)");
  }

  public void testNonLiteralId1() {
    test("goog.tweak.registerBoolean(3, 'Description')", null,
         ProcessTweaks.NON_LITERAL_TWEAK_ID_ERROR);
  }

  public void testNonLiteralId2() {
    test("goog.tweak.getBoolean('a' + 'b')", null,
         ProcessTweaks.NON_LITERAL_TWEAK_ID_ERROR);
  }
  
  public void testNonLiteralId3() {
    test("var CONST = 'foo'; goog.tweak.overrideDefaultValue(CONST, 3)", null,
        ProcessTweaks.NON_LITERAL_TWEAK_ID_ERROR);
  }

  public void testInvalidId() {
    test("goog.tweak.registerBoolean('Some ID', 'a')", null,
        ProcessTweaks.INVALID_TWEAK_ID_ERROR);
  }
  
  public void testInvalidDefaultValue1() {
    test("var val = true; goog.tweak.registerBoolean('Foo', 'desc', val)", null,
         ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_ERROR);
  }
  
  public void testInvalidDefaultValue2() {
    test("goog.tweak.overrideDefaultValue('Foo', 3 + 1);" +
        "goog.tweak.registerNumber('Foo', 'desc')", null,
        ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_ERROR);
  }

  public void testUnknownGetString() {
    testSame("goog.tweak.getString('huh')",
        ProcessTweaks.UNKNOWN_TWEAK_WARNING);
  }
  
  public void testUnknownGetNumber() {
    testSame("goog.tweak.getNumber('huh')",
        ProcessTweaks.UNKNOWN_TWEAK_WARNING);
  }
  
  public void testUnknownGetBoolean() {
    testSame("goog.tweak.getBoolean('huh')",
        ProcessTweaks.UNKNOWN_TWEAK_WARNING);
  }

  public void testUnknownOverride() {
    testSame("goog.tweak.overrideDefaultValue('huh', 'val')",
        ProcessTweaks.UNKNOWN_TWEAK_WARNING);
  }

  public void testDuplicateTweak() {
    test("goog.tweak.registerBoolean('TweakA', 'desc');" +
        "goog.tweak.registerBoolean('TweakA', 'desc')", null,
        ProcessTweaks.TWEAK_MULTIPLY_REGISTERED_ERROR);
  }

  public void testOverrideAfterRegister() {
    test("goog.tweak.registerBoolean('TweakA', 'desc');" +
        "goog.tweak.overrideDefaultValue('TweakA', 'val')",
         null, ProcessTweaks.TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR);
  }

  public void testRegisterInNonGlobalScope() {
    test("function foo() {goog.tweak.registerBoolean('TweakA', 'desc');};",
        null, ProcessTweaks.NON_GLOBAL_TWEAK_INIT_ERROR);
  }

  public void testWrongGetter1() {
    testSame("goog.tweak.registerBoolean('TweakA', 'desc');" +
        "goog.tweak.getString('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }
  
  public void testWrongGetter2() {
    testSame("goog.tweak.registerString('TweakA', 'desc');" +
        "goog.tweak.getNumber('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }
  
  public void testWrongGetter3() {
    testSame("goog.tweak.registerNumber('TweakA', 'desc');" +
        "goog.tweak.getBoolean('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }

  public void testWithNoTweaks() {
    testSame("var DEF=true;var x={};x.foo={}");
  }
}
