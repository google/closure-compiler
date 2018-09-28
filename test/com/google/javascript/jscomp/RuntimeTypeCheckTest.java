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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RuntimeTypeCheck}.
 *
 */
@RunWith(JUnit4.class)
public final class RuntimeTypeCheckTest extends CompilerTestCase {
  @Nullable private String logFunction = null;

  public RuntimeTypeCheckTest() {
    super("/** @const */ var undefined;");
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    disableLineNumberCheck();
    enableNormalize();
  }

  @Test
  public void testValue() {
    testChecks(
        "/** @param {number} i */ function f(i) {}",
        "/** @param {number} i */ function f(i) {"
            + "  $jscomp.typecheck.checkType(i, "
            + "      [$jscomp.typecheck.valueChecker('number')]);"
            + "}");
  }

  @Test
  public void testConstValue() {
    // User a variable that's immutable by the google coding convention,
    // to ensure the immutable annotations are preserved.
    testChecks("/** @param {number} CONST */ function f(CONST) {}",
        "/** @param {number} CONST */ function f(CONST) {" +
        "  $jscomp.typecheck.checkType(CONST, " +
        "      [$jscomp.typecheck.valueChecker('number')]);" +
        "}");
  }

  @Test
  public void testValueWithInnerFn() {
    testChecks("/** @param {number} i */ function f(i) { function g() {} }",
        "/** @param {number} i */ function f(i) {" +
        "  function g() {}" +
        "  $jscomp.typecheck.checkType(i, " +
        "      [$jscomp.typecheck.valueChecker('number')]);" +
        "}");
  }

  @Test
  public void testNullValue() {
    testChecks(
        "/** @param {null} i */ function f(i) {}",
        "/** @param {null} i */ function f(i) {"
            + "  $jscomp.typecheck.checkType(i, [$jscomp.typecheck.nullChecker]);"
            + "}");
  }

  @Test
  public void testValues() {
    testChecks(
        "/** @param {number} i\n@param {string} j*/ function f(i, j) {}",
        "/** @param {number} i\n@param {string} j*/ function f(i, j) {"
            + "  $jscomp.typecheck.checkType(i, "
            + "      [$jscomp.typecheck.valueChecker('number')]);"
            + "  $jscomp.typecheck.checkType(j, "
            + "      [$jscomp.typecheck.valueChecker('string')]);"
            + "}");
  }

  @Test
  public void testSkipParamOK() {
    testChecks(
        lines(
            "/**", " * @param {*} i", " * @param {string} j", " */", "function f(i, j) {}"),
        lines(
            "/**",
            " * @param {*} i",
            " * @param {string} j",
            " */",
            "function f(i, j) {",
            "  $jscomp.typecheck.checkType(j, ",
            "      [$jscomp.typecheck.valueChecker('string')]);",
            "}"));
  }

  @Test
  public void testUnion() {
    testChecks("/** @param {number|string} x */ function f(x) {}",
        "/** @param {number|string} x */ function f(x) {" +
        "  $jscomp.typecheck.checkType(x, [" +
        "      $jscomp.typecheck.valueChecker('number'), " +
        "      $jscomp.typecheck.valueChecker('string')" +
        "  ]);" +
        "}");
  }

  @Test
  public void testUntypedParam() {
    testChecksSame("/** ... */ function f(x) {}");
  }

  @Test
  public void testReturn() {
    testChecks("/** @return {string} */ function f() { return 'x'; }",
        "/** @return {string} */ function f() {" +
        "  return $jscomp.typecheck.checkType('x', " +
        "      [$jscomp.typecheck.valueChecker('string')]);" +
        "}");
  }

  @Test
  public void testNativeClass() {
    testChecks(
        "/** @param {!String} x */ function f(x) {}",
        "/** @param {!String} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, "
            + "      [$jscomp.typecheck.externClassChecker('String')]);"
            + "}");
  }

  @Test
  public void testFunctionObjectParam() {
    testChecks(
        "/** @param {!Function} x */ function f(x) {}",
        "/** @param {!Function} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, "
            + "      [$jscomp.typecheck.externClassChecker('Function')]);"
            + "}");
  }

  @Test
  public void testFunctionTypeParam() {
    testChecks(
        "/** @param {function()} x */ function f(x) {}",
        "/** @param {function()} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, "
            + "      [$jscomp.typecheck.valueChecker('function')]);"
            + "}");
  }

  // Closure collapses {function()|!Function} into {!Function}
  @Test
  public void testFunctionTypeOrFunctionObjectParam() {
    testChecks(
        "/** @param {function()|!Function} x */ function f(x) {}",
        "/** @param {function()|!Function} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, "
            + "      [$jscomp.typecheck.externClassChecker('Function')]);"
            + "}");
  }

  // Closure collapses {!Function|!Object} into {!Object}
  @Test
  public void testFunctionObjectOrObjectParam() {
    testChecks(
        "/** @param {!Function|!Object} x */ function f(x) {}",
        "/** @param {!Function|!Object} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, "
            + "      [$jscomp.typecheck.objectChecker]);"
            + "}");
  }

  @Test
  public void testQualifiedClass() {
    testChecks(
        lines(
            "var goog = {};",
            "/** @constructor */",
            "goog.Foo = function() {};",
            "/** @param {!goog.Foo} x */ ",
            "function f(x) {}"),
        lines(
            "var goog = {};",
            "/** @constructor */",
            "goog.Foo = function() {};",
            "goog.Foo.prototype['instance_of__goog.Foo'] = true;",
            "/** @param {!goog.Foo} x */ ",
            "function f(x) {",
            "  $jscomp.typecheck.checkType(x, ",
            "    [$jscomp.typecheck.classChecker('goog.Foo')]);",
            "}"));
  }

  @Test
  public void testInnerClasses() {
    testChecks(
        "function f() { /** @constructor */ function inner() {} }" +
        "function g() { /** @constructor */ function inner() {} }",
        "function f() {" +
        "  /** @constructor */ function inner() {}" +
        "  inner.prototype['instance_of__inner'] = true;" +
        "}" +
        "function g() {" +
        "  /** @constructor */ function inner$jscomp$1() {}" +
        "  inner$jscomp$1.prototype['instance_of__inner$jscomp$1'] = true;" +
        "}");
  }

  @Test
  public void testInterface() {
    testChecks("/** @interface */ function I() {}" +
        "/** @param {!I} i */ function f(i) {}",
        "/** @interface */ function I() {}" +
        "/** @param {!I} i */ function f(i) {" +
        "  $jscomp.typecheck.checkType(i, " +
        "    [$jscomp.typecheck.interfaceChecker('I')])" +
        "}");
  }

  @Test
  public void testImplementedInterface() {
    testChecks(
        lines(
            "/** @interface */ function I() {}",
            "/** @param {!I} i */ function f(i) {}",
            "/** @constructor\n@implements {I} */ function C() {}"),
        lines(
            "/** @interface */ function I() {}",
            "/** @param {!I} i */ function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.interfaceChecker('I')])",
            "}",
            "/** @constructor\n@implements {I} */ function C() {}",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;"));
  }

  @Test
  public void testExtendedInterface() {
    testChecks(
        lines(
            "/** @interface */ function I() {}",
            "/** @interface\n@extends {I} */ function J() {}",
            "/** @param {!I} i */function f(i) {}",
            "/** @constructor\n@implements {J} */function C() {}"),
        lines(
            "/** @interface */ function I() {}",
            "/** @interface\n@extends {I} */ function J() {}",
            "/** @param {!I} i */ function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.interfaceChecker('I')])",
            "}",
            "/** @constructor\n@implements {J} */ function C() {}",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;",
            "C.prototype['implements__J'] = true;"));
  }

  @Test
  public void testImplementedInterfaceOrdering() {
    testChecks(
        lines(
            "/** @interface */ function I() {}" ,
            "/** @param {!I} i */ function f(i) {}" ,
            "/** @constructor\n@implements {I} */ function C() {}" ,
            "C.prototype.f = function() {};"),
        lines(
            "/** @interface */ function I() {}",
            "/** @param {!I} i */ function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.interfaceChecker('I')])",
            "}",
            "/** @constructor\n@implements {I} */ function C() {}",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;",
            "C.prototype.f = function() {};"));
  }

  @Test
  public void testImplementedInterfaceOrderingGoogInherits() {
    testChecks(
        lines(
            "var goog = {};",
            "goog.inherits = function(x, y) {};",
            "/** @interface */function I() {}",
            "/** @param {!I} i */function f(i) {}",
            "/** @constructor */function B() {}",
            "/** @constructor\n@extends {B}\n@implements {I} */function C() {}",
            "goog.inherits(C, B);",
            "C.prototype.f = function() {};"),
        lines(
            "var goog = {};",
            "goog.inherits = function(x, y) {};",
            "/** @interface */function I() {}",
            "/** @param {!I} i */function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.interfaceChecker('I')])",
            "}",
            "/** @constructor */function B() {}",
            "B.prototype['instance_of__B'] = true;",
            "/** @constructor\n@extends {B}\n@implements {I} */function C() {}",
            "goog.inherits(C, B);",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;",
            "C.prototype.f = function() {};"));
  }

  @Test
  public void testInnerConstructor() {
    testChecks(
        "(function() { /** @constructor */ function C() {} })()",
        lines(
            "(function() {",
            "  /** @constructor */ function C() {}",
            "  C.prototype['instance_of__C'] = true;",
            "})()"));
  }

  @Test
  public void testReturnNothing() {
    testChecksSame("function f() { return; }");
  }

  @Test
  public void testFunctionType() {
    testChecksSame("/** @type {!Function} */function f() {}");
  }

  @Test
  public void testInjectLogFunction_name() {
    logFunction = "myLogFn";
    Compiler compiler = createCompiler();
    compiler.initOptions(getOptions());
    Node testNode = IR.exprResult(IR.nullNode());
    IR.script(testNode);
    getProcessor(compiler).injectCustomLogFunction(testNode);
    assertThat(compiler.toSource(testNode.getParent())).contains("$jscomp.typecheck.log=myLogFn");
  }

  @Test
  public void testInjectLogFunction_qualifiedName() {
    logFunction = "my.log.fn";
    Compiler compiler = createCompiler();
    compiler.initOptions(getOptions());
    Node testNode = IR.exprResult(IR.nullNode());
    IR.script(testNode);
    getProcessor(compiler).injectCustomLogFunction(testNode);
    assertThat(compiler.toSource(testNode.getParent())).contains("$jscomp.typecheck.log=my.log.fn");
  }

  @Test
  public void testInvalidLogFunction() {
    logFunction = "{}"; // Not a valid qualified name
    Compiler compiler = createCompiler();
    compiler.initOptions(getOptions());
    Node testNode = IR.exprResult(IR.nullNode());
    IR.script(testNode);
    try {
      getProcessor(compiler).injectCustomLogFunction(testNode);
      assertWithMessage("Expected an IllegalStateException").fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("not a valid qualified name");
    }
  }

  private void testChecks(String js, String expected) {
    test(js, expected);
    assertThat(getLastCompiler().injected).containsExactly("runtime_type_check");
  }

  private void testChecksSame(String js) {
    testSame(js);
    assertThat(getLastCompiler().injected).containsExactly("runtime_type_check");
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
  protected RuntimeTypeCheck getProcessor(final Compiler compiler) {
    return new RuntimeTypeCheck(compiler, logFunction);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }
}
