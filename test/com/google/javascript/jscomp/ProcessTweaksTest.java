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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author agrieve@google.com (Andrew Grieve) */
@RunWith(JUnit4.class)
public final class ProcessTweaksTest extends CompilerTestCase {

  Map<String, Node> defaultValueOverrides;
  boolean stripTweaks;

  public ProcessTweaksTest() {
    super("function alert(arg) {}");
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    defaultValueOverrides = new HashMap<>();
    stripTweaks = false;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        ProcessTweaks processTweak = new ProcessTweaks(compiler, stripTweaks);
        processTweak.process(externs, root);

        if (stripTweaks) {
          ImmutableSet<String> emptySet = ImmutableSet.of();
          final StripCode stripCode =
              new StripCode(
                  compiler, emptySet, emptySet, emptySet, /* enableTweakStripping */ true);
          stripCode.process(externs, root);
        }
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // Only do one repetition, so that we can make sure the first pass keeps
    // GlobalNamespace up to date.
    return 1;
  }

  @Test
  public void testBasicTweak1() {
    testSame("goog.tweak.registerBoolean('Foo', 'Description');" +
        "goog.tweak.getBoolean('Foo')");
  }

  @Test
  public void testBasicTweak2() {
    testSame("goog.tweak.registerString('Foo', 'Description');" +
        "goog.tweak.getString('Foo')");
  }

  @Test
  public void testBasicTweak3() {
    testSame("goog.tweak.registerNumber('Foo', 'Description');" +
        "goog.tweak.getNumber('Foo')");
  }

  @Test
  public void testBasicTweak4() {
    testSame("goog.tweak.registerButton('Foo', 'Description', function() {})");
  }

  @Test
  public void testBasicTweak5() {
    testSame("goog.tweak.registerBoolean('A.b_7', 'Description', true, " +
        "{ requiresRestart:false })");
  }

  @Test
  public void testBasicTweak6() {
    testSame("var opts = { requiresRestart:false };" +
        "goog.tweak.registerBoolean('Foo', 'Description', true, opts)");
  }

  @Test
  public void testNonLiteralId1() {
    testError("goog.tweak.registerBoolean(3, 'Description')",
        ProcessTweaks.NON_LITERAL_TWEAK_ID_ERROR);
  }

  @Test
  public void testNonLiteralId2() {
    testError("goog.tweak.getBoolean('a' + 'b')", ProcessTweaks.NON_LITERAL_TWEAK_ID_ERROR);
  }

  @Test
  public void testInvalidId() {
    testError("goog.tweak.registerBoolean('Some ID', 'a')", ProcessTweaks.INVALID_TWEAK_ID_ERROR);
  }

  @Test
  public void testInvalidDefaultValue1() {
    testSame("var val = true; goog.tweak.registerBoolean('Foo', 'desc', val)",
         ProcessTweaks.INVALID_TWEAK_DEFAULT_VALUE_WARNING);
  }

  @Test
  public void testDuplicateTweak() {
    testError("goog.tweak.registerBoolean('TweakA', 'desc');" +
        "goog.tweak.registerBoolean('TweakA', 'desc')",
        ProcessTweaks.TWEAK_MULTIPLY_REGISTERED_ERROR);
  }

  @Test
  public void testRegisterInNonGlobalScope() {
    testError("function foo() {goog.tweak.registerBoolean('TweakA', 'desc');};",
        ProcessTweaks.NON_GLOBAL_TWEAK_INIT_ERROR);
  }

  @Test
  public void testRegisterInIf() {
    testSame("if (true) {goog.tweak.registerBoolean('TweakA', 'desc');};");
  }

  @Test
  public void testWrongGetter1() {
    testSame("goog.tweak.registerBoolean('TweakA', 'desc');" +
        "goog.tweak.getString('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }

  @Test
  public void testWrongGetter2() {
    testSame("goog.tweak.registerString('TweakA', 'desc');" +
        "goog.tweak.getNumber('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }

  @Test
  public void testWrongGetter3() {
    testSame("goog.tweak.registerNumber('TweakA', 'desc');" +
        "goog.tweak.getBoolean('TweakA')",
        ProcessTweaks.TWEAK_WRONG_GETTER_TYPE_WARNING);
  }

  @Test
  public void testWithNoTweaks() {
    testSame("var DEF=true;var x={};x.foo={}");
  }

  @Test
  public void testStrippingWithImplicitDefaultValues() {
    stripTweaks = true;
    test("goog.tweak.registerNumber('TweakA', 'desc');" +
        "goog.tweak.registerBoolean('TweakB', 'desc');" +
        "goog.tweak.registerString('TweakC', 'desc');" +
        "alert(goog.tweak.getNumber('TweakA'));" +
        "alert(goog.tweak.getBoolean('TweakB'));" +
        "alert(goog.tweak.getString('TweakC'));",
        "void 0; void 0; void 0; alert(0); alert(false); alert('')");
  }

  @Test
  public void testStrippingWithExplicitDefaultValues() {
    stripTweaks = true;
    test(
        lines(
            "goog.tweak.registerNumber('TweakA', 'desc', 5);",
            "goog.tweak.registerBoolean('TweakB', 'desc', true);",
            "goog.tweak.registerString('TweakC', 'desc', '!');",
            "alert(goog.tweak.getNumber('TweakA'));",
            "alert(goog.tweak.getBoolean('TweakB'));",
            "alert(goog.tweak.getString('TweakC'));"),
        "void 0; void 0; void 0; alert(5); alert(true); alert('!')");
  }

  @Test
  public void testStrippingWithInCodeOverrides() {
    stripTweaks = true;
    test(
        lines(
            "goog.tweak.registerNumber('TweakA', 'desc');",
            "goog.tweak.registerBoolean('TweakB', 'desc');",
            "goog.tweak.registerString('TweakC', 'desc', 'foo');",
            "alert(goog.tweak.getNumber('TweakA'));",
            "alert(goog.tweak.getBoolean('TweakB'));",
            "alert(goog.tweak.getString('TweakC'));"),
        lines("void 0; void 0; void 0;", "alert(0); alert(false); alert('foo');"));
  }

  @Test
  public void testStrippingOfManuallyRegistered1() {
    stripTweaks = true;
    test(
        "var reg = goog.tweak.getRegistry();"
            + "if (reg) {"
            + "  reg.register(new goog.tweak.BooleanSetting('foo', 'desc'));"
            + "  reg.getEntry('foo').setDefaultValue(1);"
            + "}",
        "if (null);");
  }
}
