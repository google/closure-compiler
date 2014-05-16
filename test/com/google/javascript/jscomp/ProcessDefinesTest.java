/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;

/**
 * @author nicksantos@google.com (Nick Santos)
 */
public class ProcessDefinesTest extends CompilerTestCase {

  public ProcessDefinesTest() {
    super("var externMethod;");

    // ProcessDefines emits warnings if the user tries to re-define a constant,
    // but the constant is not defined anywhere in the binary.
    allowSourcelessWarnings();
  }

  private Map<String, Node> overrides = Maps.newHashMap();
  private GlobalNamespace namespace;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    overrides.clear();
    compareJsDoc = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ProcessDefinesWithInjectedNamespace(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // Only do one repetition, so that we can make sure the first pass keeps
    // GlobalNamespace up to date.
    return 1;
  }

  /**
   * Helper for tests that expects definitions to remain unchanged, such
   * that {@code definitions+js} is converted to {@code definitions+expected}.
   */
  private void testWithPrefix(String definitions, String js, String expected) {
    test(definitions + js, definitions + expected);
  }

  public void testBasicDefine1() {
    test("/** @define {boolean} */ var DEF = true", "var DEF=true");
  }

  public void testBasicDefine2() {
    test("/** @define {string} */ var DEF = 'a'", "var DEF=\"a\"");
  }

  public void testBasicDefine3() {
    test("/** @define {number} */ var DEF = 0", "var DEF=0");
  }

  public void testDefineBadType() {
    test("/** @define {Object} */ var DEF = {}",
        null, ProcessDefines.INVALID_DEFINE_TYPE_ERROR);
  }

  public void testDefineWithBadValue1() {
    test("/** @define {boolean} */ var DEF = new Boolean(true);", null,
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testDefineWithBadValue2() {
    test("/** @define {string} */ var DEF = 'x' + y;", null,
        ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testDefineWithDependentValue() {
    test("/** @define {boolean} */ var BASE = false;\n" +
         "/** @define {boolean} */ var DEF = !BASE;",
         "var BASE=false;var DEF=!BASE");
    test("var a = {};\n" +
         "/** @define {boolean} */ a.BASE = false;\n" +
         "/** @define {boolean} */ a.DEF = !a.BASE;",
         "var a={};a.BASE=false;a.DEF=!a.BASE");
  }


  public void testDefineWithInvalidDependentValue() {
    test("var BASE = false;\n" +
         "/** @define {boolean} */ var DEF = !BASE;",
         null,
          ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testOverriding1() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    overrides.put("DEF_OVERRIDE_TO_FALSE", new Node(Token.FALSE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false;" +
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_FALSE = true",
        "var DEF_OVERRIDE_TO_TRUE=true;var DEF_OVERRIDE_TO_FALSE=false");
  }

  public void testOverriding2() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    String normalConst = "var DEF_OVERRIDE_TO_FALSE=true;";
    testWithPrefix(
        normalConst,
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false",
        "var DEF_OVERRIDE_TO_TRUE=true");
  }

  public void testOverriding3() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true;",
        "var DEF_OVERRIDE_TO_TRUE=true");
  }

  public void testOverridingString0() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "var DEF_OVERRIDE_STRING=\"x\"");
  }

  public void testOverridingString1() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "var DEF_OVERRIDE_STRING=\"x\" + \"y\"");
  }

  public void testOverridingString2() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "var DEF_OVERRIDE_STRING=\"foo\"");
  }

  public void testOverridingString3() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "var DEF_OVERRIDE_STRING=\"foo\"");
  }

  public void testMisspelledOverride() {
    overrides.put("DEF_BAD_OVERIDE", new Node(Token.TRUE));
    test("/** @define {boolean} */ var DEF_BAD_OVERRIDE = true",
        "var DEF_BAD_OVERRIDE=true", null,
        ProcessDefines.UNKNOWN_DEFINE_WARNING);
  }

  public void testCompiledIsKnownDefine() {
    overrides.put("COMPILED", new Node(Token.TRUE));
    testSame("");
  }

  public void testSimpleReassign1() {
    test("/** @define {boolean} */ var DEF = false; DEF = true;",
        "var DEF=true;true");
  }

  public void testSimpleReassign2() {
    test("/** @define {number|boolean} */ var DEF=false;DEF=true;DEF=3",
        "var DEF=3;true;3");

    Name def = namespace.getNameIndex().get("DEF");
    assertEquals(1, def.getRefs().size());
    assertEquals(1, def.globalSets);
    assertNotNull(def.getDeclaration());
  }

  public void testSimpleReassign3() {
    test("/** @define {boolean} */ var DEF = false;var x;x = DEF = true;",
        "var DEF=true;var x;x=true");
  }

  public void testAssignBeforeDeclaration1() {
    test("DEF=false;var b=false,/** @define {boolean} */DEF=true,c=false",
         null, ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testAssignBeforeDeclaration2() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        "DEF_OVERRIDE_TO_TRUE = 3;" +
        "/** @define {boolean|number} */ var DEF_OVERRIDE_TO_TRUE = false;",
        null, ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testEmptyDeclaration() {
    test("/** @define {boolean} */ var DEF;",
         null, ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testReassignAfterCall() {
    test("/** @define {boolean} */var DEF=true;externMethod();DEF=false",
        null, ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testReassignAfterRef() {
    test("/** @define {boolean} */var DEF=true;var x = DEF;DEF=false",
        null, ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testReassignWithExpr() {
    test("/** @define {boolean} */var DEF=true;var x;DEF=x=false",
        null, ProcessDefines.INVALID_DEFINE_INIT_ERROR);
  }

  public void testReassignAfterNonGlobalRef() {
    test(
        "/** @define {boolean} */var DEF=true;" +
        "var x=function(){var y=DEF}; DEF=false",
        "var DEF=false;var x=function(){var y=DEF};false");

    Name def = namespace.getNameIndex().get("DEF");
    assertEquals(2, def.getRefs().size());
    assertEquals(1, def.globalSets);
    assertNotNull(def.getDeclaration());
  }

  public void testReassignAfterRefInConditional() {
    test(
        "/** @define {boolean} */var DEF=true;" +
        "if (false) {var x=DEF} DEF=false;",
        null, ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  public void testAssignInNonGlobalScope() {
    test("/** @define {boolean} */var DEF=true;function foo() {DEF=false};",
        null, ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testDeclareInNonGlobalScope() {
    test("function foo() {/** @define {boolean} */var DEF=true;};",
        null, ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testDefineAssignmentInLoop() {
    test("/** @define {boolean} */var DEF=true;var x=0;while (x) {DEF=false;}",
        null, ProcessDefines.NON_GLOBAL_DEFINE_INIT_ERROR);
  }

  public void testWithNoDefines() {
    testSame("var DEF=true;var x={};x.foo={}");
  }

  public void testNamespacedDefine1() {
    test("var a = {}; /** @define {boolean} */ a.B = false; a.B = true;",
         "var a = {}; a.B = true; true;");

    Name aDotB = namespace.getNameIndex().get("a.B");
    assertEquals(1, aDotB.getRefs().size());
    assertEquals(1, aDotB.globalSets);
    assertNotNull(aDotB.getDeclaration());
  }

  public void testNamespacedDefine2a() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; a.B = true;");
  }

  public void testNamespacedDefine2b() {
    // TODO(johnlenz): We should either reject the define as invalid
    // or replace its value.
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = { /** @define {boolean} */ B : false };",
         "var a = {B : false};",
         null, ProcessDefines.UNKNOWN_DEFINE_WARNING);
  }

  public void testNamespacedDefine2c() {
    // TODO(johnlenz): We should either reject the define as invalid
    // or replace its value.
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = { /** @define {boolean} */ get B() { return false } };",
      "var a = {get B() { return false } };",
      null, ProcessDefines.UNKNOWN_DEFINE_WARNING);
  }

  public void testNamespacedDefine3() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {};", "var a = {};", null,
         ProcessDefines.UNKNOWN_DEFINE_WARNING);
  }

  public void testNamespacedDefine4() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; a.B = true;");
  }


  public void testOverrideAfterAlias() {
    test("var x; /** @define {boolean} */var DEF=true; x=DEF; DEF=false;",
         null, ProcessDefines.DEFINE_NOT_ASSIGNABLE_ERROR);
  }

  private class ProcessDefinesWithInjectedNamespace implements CompilerPass {
    private final Compiler compiler;

    public ProcessDefinesWithInjectedNamespace(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node js) {
      namespace = new GlobalNamespace(compiler, js);
      new ProcessDefines(compiler, overrides)
          .injectNamespace(namespace)
          .process(externs, js);
    }
  }
}
