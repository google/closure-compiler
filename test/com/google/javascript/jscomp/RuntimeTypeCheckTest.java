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
import org.junit.Ignore;
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
  public void testParamFormat_simpleName() {
    testChecks(
        "/** @param {number} i */ function f(i) {}",
        lines(
            "/** @param {number} i */ function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_rest() {
    testChecks(
        "/** @param {...number} i */ function f(...i) {}",
        lines(
            "/** @param {...number} i */ function f(...i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.externClassChecker('Array')]);",
            "}"));
  }

  @Test
  public void testParamFormat_arrayDestructuring() {
    testChecks(
        "/** @param {!Iterable<number>} i */ function f([i]) {}",
        lines(
            "/** @param {!Iterable<number>} i */ function f([i]) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_objectDestructuring() {
    testChecks(
        "/** @param {{a: number}} i */ function f({a: i}) {}",
        lines(
            "/** @param {{a: number}} i */ function f({a: i}) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_simpleName_withDefault() {
    testChecks(
        "/** @param {number=} i */ function f(i = 9) {}",
        lines(
            "/** @param {number=} i */ function f(i = 9) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_arrayDestructuring_withDefault() {
    testChecks(
        lines(
            "const /** !Iterable<number> */ itr = [];",
            "",
            "/** @param {!Iterable<number>=} unused */",
            "function f([i] = itr) {}"),
        lines(
            "const /** !Iterable<number> */ itr = [];",
            "",
            "/** @param {!Iterable<number>=} unused */",
            "function f([i] = itr) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_objectDestructuring_withDefault() {
    testChecks(
        "/** @param {{a: number}=} i */ function f({a: i} = {a: 9}) {}",
        lines(
            "/** @param {{a: number}=} i */ function f({a: i} = {a: 9}) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_arrayDestructuring_withDefault_nestedInPattern() {
    testChecks(
        "/** @param {!Iterable<number>} i */ function f([i = 9]) {}",
        lines(
            "/** @param {!Iterable<number>} i */ function f([i = 9]) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testParamFormat_objectDestructuring_withDefault_nestedInPattern() {
    testChecks(
        "/** @param {{a: number}} i */ function f({a: i = 9}) {}",
        lines(
            "/** @param {{a: number}} i */ function f({a: i = 9}) {",
            "  $jscomp.typecheck.checkType(i, ",
            "      [$jscomp.typecheck.valueChecker('number')]);",
            "}"));
  }

  @Test
  public void testConstValue() {
    // User a variable that's immutable by the google coding convention,
    // to ensure the immutable annotations are preserved.
    testChecks(
        "/** @param {number} CONST */ function f(CONST) {}",
        "/** @param {number} CONST */ function f(CONST) {"
            + "  $jscomp.typecheck.checkType(CONST, "
            + "      [$jscomp.typecheck.valueChecker('number')]);"
            + "}");
  }

  @Test
  public void testValueWithInnerFn() {
    testChecks(
        "/** @param {number} i */ function f(i) { function g() {} }",
        "/** @param {number} i */ function f(i) {"
            + "  function g() {}"
            + "  $jscomp.typecheck.checkType(i, "
            + "      [$jscomp.typecheck.valueChecker('number')]);"
            + "}");
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
        lines("/**", " * @param {*} i", " * @param {string} j", " */", "function f(i, j) {}"),
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
    testChecks(
        "/** @param {number|string} x */ function f(x) {}",
        "/** @param {number|string} x */ function f(x) {"
            + "  $jscomp.typecheck.checkType(x, ["
            + "      $jscomp.typecheck.valueChecker('number'), "
            + "      $jscomp.typecheck.valueChecker('string')"
            + "  ]);"
            + "}");
  }

  @Test
  public void testUntypedParam() {
    testChecksSame("/** ... */ function f(x) {}");
  }

  @Test
  public void testReturn_sync() {
    testChecks(
        "/** @return {string} */ function f() { return 'x'; }",
        lines(
            "/** @return {string} */ function f() {",
            "  return $jscomp.typecheck.checkType('x', [",
            "      $jscomp.typecheck.valueChecker('string'),",
            "  ]);",
            "}"));
  }

  @Test
  public void testReturn_async() {
    testChecks(
        "/** @return {!Promise<string>} */ async function f() { return 'x'; }",
        lines(
            "/** @return {!Promise<string>} */ async function f() {",
            "  return $jscomp.typecheck.checkType('x', [",
            "      $jscomp.typecheck.externClassChecker('IThenable'),",
            "      $jscomp.typecheck.valueChecker('string'),",
            "  ]);",
            "}"));
  }

  @Test
  public void testYield_sync() {
    testChecks(
        "/** @return {!Generator<string>} */ function* f() { yield 'x'; }",
        lines(
            "/** @return {!Generator<string>} */ function* f() {",
            "  yield $jscomp.typecheck.checkType('x', [",
            "      $jscomp.typecheck.valueChecker('string'),",
            "  ]);",
            "}"));
  }

  @Test
  @Ignore // TODO(b/120277559): Enable when async generators are supported.
  public void testYield_async() {
    testChecks(
        "/** @return {!AsyncGenerator<string>} */ async function* f() { yield 'x'; }",
        lines(
            "/** @return {!AsyncGenerator<string>} */ function* f() {",
            "  yield $jscomp.typecheck.checkType('x', [",
            "      $jscomp.typecheck.externClassChecker('IThenable'),",
            "      $jscomp.typecheck.valueChecker('string'),",
            "  ]);",
            "}"));
  }

  @Test
  public void testYieldAll_sync() {
    testChecks(
        "/** @return {!Generator<string>} */ function* f() { yield* ['x']; }",
        lines(
            "/** @return {!Generator<string>} */ function* f() {",
            "  yield* $jscomp.typecheck.checkType(['x'], [",
            "      $jscomp.typecheck.externClassChecker('Iterable'),",
            "  ]);",
            "}"));
  }

  @Test
  @Ignore // TODO(b/120277559): Enable when async generators are supported.
  public void testYieldAll_async() {
    testChecks(
        "/** @return {!AsyncGenerator<string>} */ async function* f() { yield* ['x']; }",
        lines(
            "/** @return {!AsyncGenerator<string>} */ async function* f() {",
            "  yield* $jscomp.typecheck.checkType(['x'], [",
            "      $jscomp.typecheck.externClassChecker('IThenable'),",
            "      $jscomp.typecheck.externClassChecker('Iterable'),",
            "  ]);",
            "}"));
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

  @Test
  public void testNullableFunctionType() {
    testChecks(
        lines(
            "/** @type {?function(number):number} */ (/** @param {number} x*/ function(x) {",
            " return x;",
            "})"),
        lines(
            "/** @type {?function(number):number} */ (/** @param {number} x */ function(x) {",
            "  $jscomp.typecheck.checkType(x,[$jscomp.typecheck.valueChecker('number')]);",
            "  return x;",
            "})"));
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
  public void testMarkers_onQualifiedClass_es5() {
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
  public void testMarkers_onQualifiedClass_es6() {
    testChecks(
        lines(
            "var goog = {};",
            "goog.Foo = class {};",
            "",
            "/** @param {!goog.Foo} x */ ",
            "function f(x) {}"),
        lines(
            "var goog = {};",
            "goog.Foo = class {",
            "  ['instance_of__goog.Foo']() {}",
            "};",
            "",
            "/** @param {!goog.Foo} x */ ",
            "function f(x) {",
            "  $jscomp.typecheck.checkType(x, ",
            "    [$jscomp.typecheck.classChecker('goog.Foo')]);",
            "}"));
  }

  @Test
  public void testMarkers_onScopedClass_byFunction_es5() {
    testChecks(
        lines(
            "function f() { /** @constructor */ function inner() {} }",
            "function g() { /** @constructor */ function inner() {} }"),
        lines(
            "function f() {",
            "  /** @constructor */ function inner() {}",
            "  inner.prototype['instance_of__inner'] = true;",
            "}",
            "function g() {",
            "  /** @constructor */ function inner$jscomp$1() {}",
            "  inner$jscomp$1.prototype['instance_of__inner$jscomp$1'] = true;",
            "}"));
  }

  @Test
  public void testMarkers_onScopedClass_byFunction_es6() {
    testChecks(
        lines(
            "function f() { class inner {} }", //
            "function g() { class inner {} }"),
        lines(
            "function f() {",
            "  class inner {",
            "    ['instance_of__inner']() {}",
            "  }",
            "}",
            "function g() {",
            "  class inner$jscomp$1 {",
            "    ['instance_of__inner$jscomp$1']() { }",
            "  }",
            "}"));
  }

  @Test
  public void testMarkers_onScopedClass_byIife_es5() {
    testChecks(
        "(function() { /** @constructor */ function C() {} })()",
        lines(
            "(function() {",
            "  /** @constructor */ function C() {}",
            "  C.prototype['instance_of__C'] = true;",
            "})()"));
  }

  @Test
  public void testMarkers_onScopedClass_byIife_es6() {
    testChecks(
        "(function() { class C {} })()",
        lines(
            "(function() {", //
            "  class C {",
            "    ['instance_of__C']() {}",
            "  }",
            "})()"));
  }

  @Test
  public void testMarkers_forImplementedInterface_es5() {
    testChecks(
        lines(
            "/** @interface */ function I() {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {I}",
            " */",
            "function C() {}"),
        lines(
            "/** @interface */ function I() {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {I}",
            " */",
            "function C() {}",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;"));
  }

  @Test
  public void testMarkers_forImplementedInterface_es6() {
    testChecks(
        lines(
            "/** @interface */ class I {}", //
            "",
            "/** @implements {I} */ class C {}"),
        lines(
            "/** @interface */ class I {}",
            "",
            "/** @implements {I} */ class C {",
            "  ['instance_of__C']() {}",
            "  ['implements__I']() {}",
            "}"));
  }

  @Test
  public void testMarkers_forExtendedInterface_es5() {
    testChecks(
        lines(
            "/** @interface */ function I() {}",
            "",
            "/**",
            " * @interface",
            " * @extends {I}",
            " */",
            "function J() {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {J}",
            " */",
            "function C() {}"),
        lines(
            "/** @interface */ function I() {}",
            "",
            "/**",
            " * @interface",
            " * @extends {I}",
            " */",
            "function J() {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {J}",
            " */",
            "function C() {}",
            "C.prototype['instance_of__C'] = true;",
            "C.prototype['implements__I'] = true;",
            "C.prototype['implements__J'] = true;"));
  }

  @Test
  public void testMarkers_forExtendedInterface_es6() {
    testChecks(
        lines(
            "/** @interface */ class I {}",
            "",
            "/** @interface */ class J extends I {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {J}",
            " */",
            "class C {}"),
        lines(
            "/** @interface */ class I {}",
            "",
            "/** @interface */ class J extends I {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {J}",
            " */",
            "class C {",
            "  ['instance_of__C']() {}",
            "  ['implements__I']() {}",
            "  ['implements__J']() {}",
            "}"));
  }

  @Test
  public void testMarkers_onClass_inCompositeExpression_es5() {
    testChecks(
        lines(
            "/** @interface */ function I() {};", //
            "",
            // `new` is just an example. The important thing is that the class is declared in a
            // larger expression.
            "new (/**",
            "      * @constructor",
            "      * @implements {I}",
            "      */",
            "     function() {});"),
        lines(
            "/** @interface */ function I() {};", //
            "",
            // TODO(b/123018757): There should be markers added here somehow.
            "new (/**",
            "      * @constructor",
            "      * @implements {I}",
            "      */",
            "     function() {});"));
  }

  @Test
  public void testMarkers_onClass_inCompositeExpression_es6() {
    testChecks(
        lines(
            "/** @interface */ class I {};", //
            "",
            // `new` is just an example; the important thing is that the class is declared in a
            // larger expression.
            "new (/** @implements {I} */ class {});"),
        lines(
            "/** @interface */ class I {};", //
            "",
            "new (/** @implements {I} */ class {",
            "  ['implements__I']() {}",
            "});"));
  }

  @Test
  public void testMarkers_onClass_es6_withConstructor() {
    testChecks(
        lines(
            "class C {",
            // This node is also a FUNCTION with a ctor type, so we want to check that the markers
            // aren't duplicated.
            "  constructor() {",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "  }",
            "",
            "  ['instance_of__C']() { }",
            "}"));
  }

  @Test
  public void testInterface() {
    testChecks(
        lines("/** @interface */ function I() {}" + "/** @param {!I} i */ function f(i) {}"),
        lines(
            "/** @interface */ function I() {}",
            "/** @param {!I} i */ function f(i) {",
            "  $jscomp.typecheck.checkType(i, ",
            "    [$jscomp.typecheck.interfaceChecker('I')])",
            "}"));
  }

  @Test
  public void testImplementedInterface_ordering() {
    testChecks(
        lines(
            "/** @interface */ function I() {}",
            "/** @param {!I} i */ function f(i) {}",
            "/** @constructor\n@implements {I} */ function C() {}",
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
  public void testImplementedInterface_ordering_googInherits() {
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
}
