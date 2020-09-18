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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author nicksantos@google.com (Nick Santos) */
@RunWith(JUnit4.class)
public final class ProcessDefinesTest extends CompilerTestCase {

  public ProcessDefinesTest() {
    super(DEFAULT_EXTERNS + "var externMethod;");
  }

  private final Map<String, Node> overrides = new HashMap<>();
  private GlobalNamespace namespace;
  private boolean checksOnly;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    overrides.clear();
    checksOnly = false;

    // ProcessDefines emits warnings if the user tries to re-define a constant,
    // but the constant is not defined anywhere in the binary.
    allowSourcelessWarnings();
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

  @Test
  public void testBasicDefine1() {
    test("/** @define {boolean} */ var DEF = true", "/** @define {boolean} */ var DEF=true");
  }

  @Test
  public void testBasicDefine2() {
    test("/** @define {string} */ var DEF = 'a'", "/** @define {string} */ var DEF=\"a\"");
  }

  @Test
  public void testBasicDefine3() {
    test("/** @define {number} */ var DEF = 0", "/** @define {number} */ var DEF=0");
  }

  @Test
  public void testDefineBadType() {
    test(
        srcs("/** @define {Object} */ var DEF = {}"),
        error(ProcessDefines.INVALID_DEFINE_TYPE),
        error(ProcessDefines.INVALID_DEFINE_VALUE));
  }

  @Test
  public void testChecksOnlyProducesErrors() {
    checksOnly = true;
    test(
        srcs("/** @define {Object} */ var DEF = {}"),
        error(ProcessDefines.INVALID_DEFINE_TYPE),
        error(ProcessDefines.INVALID_DEFINE_VALUE));
  }

  @Test
  public void testChecksOnlyProducesUnknownDefineWarning() {
    checksOnly = true;
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {};", "var a = {};", warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  @Test
  public void testDefineWithBadValue1() {
    testError(
        "/** @define {boolean} */ var DEF = new Boolean(true);",
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithBadValue2() {
    testError("/** @define {string} */ var DEF = 'x' + y;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithBadValue3() {
    // alias is not const
    testError(
        "let x = 'x'; /** @define {string} */ var DEF = x;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithBadValue4() {
    testError("/** @define {string} */ var DEF = null;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithBadValue5() {
    testError("/** @define {string} */ var DEF = undefined;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithBadValue6() {
    testError("/** @define {string} */ var DEF = NaN;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineWithLet() {
    testError(
        "/** @define {boolean} */ let DEF = new Boolean(true);",
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineInExterns() {
    testSame(externs(DEFAULT_EXTERNS + "/** @define {boolean} */ var EXTERN_DEF;"), srcs(""));
  }

  @Test
  public void testDefineInExternsPlusUsage() {
    testSame(
        externs(DEFAULT_EXTERNS + "/** @define {boolean} */ var EXTERN_DEF;"),
        srcs("/** @define {boolean} */ var DEF = EXTERN_DEF"));
  }

  @Test
  public void testNonDefineInExternsPlusUsage() {
    testError(
        externs(DEFAULT_EXTERNS + "/** @const {boolean} */ var EXTERN_NON_DEF;"),
        srcs("/** @define {boolean} */ var DEF = EXTERN_NON_DEF"),
        error(ProcessDefines.INVALID_DEFINE_VALUE));
  }

  @Test
  public void testDefineCompiledInExterns() {
    testSame(externs(DEFAULT_EXTERNS + "/** @define {boolean} */ var COMPILED;"), srcs(""));
  }

  @Test
  public void testDefineWithDependentValue() {
    test(
        lines(
            "/** @define {boolean} */ var BASE = false;",
            "/** @define {boolean} */ var DEF = !BASE;"),
        lines(
            "/** @define {boolean} */ var BASE = false;",
            "/** @define {boolean} */ var DEF = !BASE"));
    test(
        lines(
            "var a = {};",
            "/** @define {boolean} */ a.BASE = false;",
            "/** @define {boolean} */ a.DEF = !a.BASE;"),
        lines(
            "var a={};",
            "/** @define {boolean} */ a.BASE = false;",
            "/** @define {boolean} */ a.DEF = !a.BASE"));
  }

  @Test
  public void testDefineWithInvalidDependentValue() {
    testError(
        lines(
            "var BASE = false;", //
            "/** @define {boolean} */ var DEF = !BASE;"),
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testOverriding1() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    overrides.put("DEF_OVERRIDE_TO_FALSE", new Node(Token.FALSE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false;"
            + "/** @define {boolean} */ var DEF_OVERRIDE_TO_FALSE = true",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true;"
            + "/** @define {boolean} */var DEF_OVERRIDE_TO_FALSE=false");
  }

  @Test
  public void testOverriding2() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    String normalConst = "var DEF_OVERRIDE_TO_FALSE=true;";
    testWithPrefix(
        normalConst,
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = false",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true");
  }

  @Test
  public void testOverriding3() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true;",
        "/** @define {boolean} */ var DEF_OVERRIDE_TO_TRUE = true");
  }

  @Test
  public void testOverridingString0() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"x\"");
  }

  @Test
  public void testOverridingString1() {
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"x\" + \"y\"");
  }

  @Test
  public void testOverridingString2() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"foo\"");
  }

  @Test
  public void testOverridingString3() {
    overrides.put("DEF_OVERRIDE_STRING", Node.newString("foo"));
    test(
        "/** @define {string} */ var DEF_OVERRIDE_STRING = 'x' + 'y';",
        "/** @define {string} */ var DEF_OVERRIDE_STRING=\"foo\"");
  }

  @Test
  public void testMisspelledOverride() {
    overrides.put("DEF_BAD_OVERIDE", new Node(Token.TRUE));  // NOTYPO: Intentional misspelling.
    test(
        "/** @define {boolean} */ var DEF_BAD_OVERRIDE = true",
        "/** @define {boolean} */ var DEF_BAD_OVERRIDE = true",
        warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  @Test
  public void testCompiledIsKnownDefine() {
    overrides.put("COMPILED", new Node(Token.TRUE));
    testSame("");
  }

  @Test
  public void testSimpleReassign1() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */ var DEF = false;", //
                "DEF = true;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testSimpleReassign2() {
    test(
        srcs(
            lines(
                "/** @define {number|boolean} */ var DEF=false;", //
                "DEF=true;",
                "DEF=3")),
        error(ProcessDefines.NON_CONST_DEFINE),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testSimpleReassign3() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */ var DEF = false;", //
                "var x;",
                "x = DEF = true;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testDefineAssignedToSimpleAlias() {
    testSame(
        lines(
            "const x = true;", //
            "const ALIAS = x;",
            "/** @define {boolean} */ const DEF2 = ALIAS;"));
  }

  @Test
  public void testDefineAssignedToNonConstAlias() {
    testError(
        lines(
            "let X = true;", //
            "X = false;",
            "/** @define {boolean} */ const DEF2 = X;"),
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineAssignedToEnumAlias() {
    testError(
        lines(
            "/** @enum {string} */ const E = {A: 'a'};", //
            "/** @define {string} */ const DEF2 = E.A;"),
        // TODO(sdh): It would be nice if this worked, but doesn't seem worth implementing.
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineAssignedToDefineAlias() {
    overrides.put("DEF2", new Node(Token.TRUE));
    test(
        lines(
            "/** @define {boolean} */ const DEF1 = false;",
            "const ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = ALIAS;"),
        lines(
            "/** @define {boolean} */ const DEF1 = false;",
            "const ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = true;"));
  }

  @Test
  public void testDefineAssignedToQualifiedNameAlias() {
    overrides.put("DEF1", new Node(Token.TRUE));
    test(
        lines(
            "const ns = {};",
            "/** @define {boolean} */ const DEF1 = false;",
            "/** @const */ ns.ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = ns.ALIAS;"),
        lines(
            "const ns = {};",
            "/** @define {boolean} */ const DEF1 = true;",
            "/** @const */ ns.ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = ns.ALIAS;"));
  }

  @Test
  public void testDefineAssignedToNonconstDefineAlias() {
    testError(
        lines(
            "/** @define {boolean} */ const DEF1 = false;",
            "var ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = ALIAS;"),
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testDefineAssignedToNonconstQualifiedNameAlias() {
    testError(
        lines(
            "const ns = {};",
            "/** @define {boolean} */ const DEF1 = false;",
            "ns.ALIAS = DEF1;",
            "/** @define {boolean} */ const DEF2 = ns.ALIAS;"),
        ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testAssignBeforeDeclaration_var() {
    test(
        srcs("DEF=false;var b=false,/** @define {boolean} */DEF=true,c=false"),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testAssignBeforeDeclaration_const() {
    test(
        srcs("DEF=false;const b=false,/** @define {boolean} */DEF=true,c=false"),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testAssignBeforeDeclaration_var_withOverride() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        srcs(
            lines(
                "DEF_OVERRIDE_TO_TRUE = 3;", //
                "/** @define {boolean|number} */ var DEF_OVERRIDE_TO_TRUE = false;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testEmptyDeclaration() {
    testError("/** @define {boolean} */ var DEF;", ProcessDefines.INVALID_DEFINE_VALUE);
  }

  @Test
  public void testReassignAfterCall() {
    testError(
        "/** @define {boolean} */var DEF=true;externMethod();DEF=false",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testReassignAfterRef() {
    testError(
        "/** @define {boolean} */var DEF=true;var x = DEF;DEF=false",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testReassignWithExpr() {
    test(
        srcs("/** @define {boolean} */var DEF=true;var x;DEF=x=false"),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testReassignAfterRefInConditional() {
    testError(
        "/** @define {boolean} */var DEF=true; if (false) {var x=DEF} DEF=false;",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testAssignInFunctionScope() {
    testError(
        "/** @define {boolean} */var DEF=true;function foo() {DEF=false};",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testDeclareInFunctionScope() {
    testError(
        "function foo() {/** @define {boolean} */var DEF=true;};",
        ProcessDefines.INVALID_DEFINE_LOCATION);
  }

  @Test
  public void testDeclareInFunctionScope_withOtherSet() {
    test(
        srcs(
            lines(
                "var DEF = 0;", //
                "function foo() {",
                "  /** @define {boolean} */ DEF=true;",
                "};")),
        error(ProcessDefines.INVALID_DEFINE_LOCATION));
  }

  @Test
  public void testDeclareInBlockScope() {
    testError(
        "{ /** @define {boolean} */ const DEF=true; };", //
        ProcessDefines.INVALID_DEFINE_LOCATION);
  }

  @Test
  public void testDefineAssignmentInLoop() {
    testError(
        "/** @define {boolean} */var DEF=true;var x=0;while (x) {DEF=false;}",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testWithNoDefines() {
    testSame("var DEF=true;var x={};x.foo={}");
  }

  @Test
  public void testNamespacedDefine1() {
    test(
        srcs("var a = {}; /** @define {boolean} */ a.B = false; a.B = true;"),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testNamespacedDefine2a() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; /** @define {boolean} */ a.B = true;");
  }

  @Test
  public void testNamespacedDefine2b() {
    overrides.put("a.B", new Node(Token.TRUE));
    testError(
        "var a = { /** @define {boolean} */ B : false };", //
        ProcessDefines.INVALID_DEFINE_LOCATION);
  }

  @Test
  public void testNamespacedDefine2c() {
    overrides.put("a.B", new Node(Token.TRUE));
    testError(
        "var a = { /** @define {boolean} */ get B() { return false } };",
        ProcessDefines.INVALID_DEFINE_LOCATION);
  }

  @Test
  public void testNamespacedDefine3() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {};", "var a = {};", warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  @Test
  public void testNamespacedDefine4() {
    overrides.put("a.B", new Node(Token.TRUE));
    test("var a = {}; /** @define {boolean} */ a.B = false;",
         "var a = {}; /** @define {boolean} */ a.B = true;");
  }

  @Test
  public void testGoogDefine_notOverridden() {
    test(
        "/** @define {boolean} */ const B = goog.define('a.B', false);",
        "/** @define {boolean} */ const B = false;");
  }

  @Test
  public void testGoogDefine_overridden() {
    overrides.put("a.B", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ const B = goog.define('a.B', false);",
        "/** @define {boolean} */ const B = true;");
  }

  @Test
  public void testOverrideAfterAlias() {
    testError(
        "var x; /** @define {boolean} */var DEF=true; x=DEF; DEF=false;",
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testBasicConstDeclaration() {
    test("/** @define {boolean} */ const DEF = true", "/** @define {boolean} */ const DEF=true");
    test("/** @define {string} */ const DEF = 'a'", "/** @define {string} */ const DEF=\"a\"");
    test("/** @define {number} */ const DEF = 0", "/** @define {number} */ const DEF=0");
  }

  @Test
  public void testConstOverriding1() {
    overrides.put("DEF_OVERRIDE_TO_TRUE", new Node(Token.TRUE));
    test(
        "/** @define {boolean} */ const DEF_OVERRIDE_TO_TRUE = false;",
        "/** @define {boolean} */ const DEF_OVERRIDE_TO_TRUE = true;");
  }

  @Test
  public void testConstOverriding2() {
    test(
        "/** @define {string} */ const DEF_OVERRIDE_STRING = 'x';",
        "/** @define {string} */ const DEF_OVERRIDE_STRING=\"x\"");
  }

  @Test
  public void testConstProducesUnknownDefineWarning() {
    checksOnly = true;
    overrides.put("a.B", new Node(Token.TRUE));
    test("const a = {};", "const a = {};", warning(ProcessDefines.UNKNOWN_DEFINE_WARNING));
  }

  @Test
  public void testSimpleConstReassign() {
    testError(
        lines(
            "/** @define {boolean} */ const DEF = false;", //
            "DEF = true;"),
        ProcessDefines.NON_CONST_DEFINE);
  }

  @Test
  public void testRedeclaration_twoGoogDefine_differentLocalNames() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */",
                "const A = goog.define('a.B', false);",
                "",
                "/** @define {boolean} */",
                "const B = goog.define('a.B', false);")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testRedeclaration_oneGoogDefine_varWithGoogDefineName() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */", //
                "const A = goog.define('B', false);",
                "",
                "const B = false;")),
        expected(
            lines(
                "/** @define {boolean} */", //
                "const A = false;",
                "",
                "const B = false;")));
  }

  @Test
  public void testRedeclaration_oneGoogDefine_varWithSameLocalName() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */",
                "var A = goog.define('B', false);",
                "",
                "var A = false;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testRedeclaration_oneGoogDefine_oneAtDefine() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */",
                "const A = goog.define('B', false);",
                "",
                "/** @define {boolean} */",
                "const B = false;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  @Test
  public void testRedeclaration_oneAtDefine_varWithSameName() {
    test(
        srcs(
            lines(
                "/** @define {boolean} */", //
                "var A = false;",
                "",
                "var A = false;")),
        error(ProcessDefines.NON_CONST_DEFINE));
  }

  private class ProcessDefinesWithInjectedNamespace implements CompilerPass {
    private final Compiler compiler;

    public ProcessDefinesWithInjectedNamespace(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node js) {
      new ProcessClosurePrimitives(compiler, null).process(externs, js);
      namespace = new GlobalNamespace(compiler, externs, js);
      new ProcessDefines.Builder(compiler)
          .putReplacements(overrides)
          .checksOnly(checksOnly)
          .injectNamespace(() -> namespace)
          .build()
          .process(externs, js);
    }
  }
}
