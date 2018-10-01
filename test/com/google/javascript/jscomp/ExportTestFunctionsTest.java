/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for ExportTestFunctions.
 *
 */
@RunWith(JUnit4.class)
public final class ExportTestFunctionsTest extends CompilerTestCase {

  private static final String EXTERNS =
      "function google_exportSymbol(a, b) {}; function google_exportProperty(a, b, c) {};";

  private static final String TEST_FUNCTIONS_WITH_NAMES =
      "function Foo(arg) {}; "
          + "function setUp(arg3) {}; "
          + "function tearDown(arg, arg2) {}; "
          + "function testBar(arg) {}; "
          + "function test$(arg) {}; "
          + "function test$foo(arg) {}";

  public ExportTestFunctionsTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableLineNumberCheck();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExportTestFunctions(compiler, "google_exportSymbol", "google_exportProperty");
  }

  @Override
  protected int getNumRepetitions() {
    // This pass only runs once.
    return 1;
  }

  @Test
  public void testFunctionsAreExported() {
    test(
        TEST_FUNCTIONS_WITH_NAMES,
        "function Foo(arg){}; "
            + "function setUp(arg3){} google_exportSymbol(\"setUp\",setUp);; "
            + "function tearDown(arg,arg2) {} "
            + "google_exportSymbol(\"tearDown\",tearDown);; "
            + "function testBar(arg){} google_exportSymbol(\"testBar\",testBar);; "
            + "function test$(arg){} google_exportSymbol(\"test$\",test$);; "
            + "function test$foo(arg){} google_exportSymbol(\"test$foo\",test$foo)");
  }

  // Helper functions
  @Test
  public void testBasicTestFunctionsAreExported() {
    testSame("function Foo() {function testA(){}}");
    test("function setUp() {}", "function setUp(){} google_exportSymbol('setUp',setUp)");
    test(
        "function setUpPage() {}",
        "function setUpPage(){} google_exportSymbol('setUpPage',setUpPage)");
    test(
        "function shouldRunTests() {}",
        "function shouldRunTests(){}" + "google_exportSymbol('shouldRunTests',shouldRunTests)");
    test(
        "function tearDown() {}", "function tearDown(){} google_exportSymbol('tearDown',tearDown)");
    test(
        "function tearDownPage() {}",
        "function tearDownPage(){} google_exportSymbol('tearDownPage'," + "tearDownPage)");
    test(
        "function testBar() { function testB() {}}",
        "function testBar(){function testB(){}}" + "google_exportSymbol('testBar',testBar)");
    testSame("var testCase = {}; testCase.setUpPage = function() {}");
  }

  /**
   * Make sure this works for global functions declared as function expressions:
   *
   * <pre>
   * var testFunctionName = function() {
   *   // Implementation
   * };
   * </pre>
   *
   * This format should be supported in addition to function statements.
   */
  @Test
  public void testFunctionExpressionsAreExported() {
    testSame("var Foo = function() {var testA = function() {}}");
    test(
        "var setUp = function() {}",
        "var setUp = function() {}; " + "google_exportSymbol('setUp',setUp)");
    test(
        "var setUpPage = function() {}",
        "var setUpPage = function() {}; " + "google_exportSymbol('setUpPage',setUpPage)");
    test(
        "var shouldRunTests = function() {}",
        "var shouldRunTests = function() {}; "
            + "google_exportSymbol('shouldRunTests',shouldRunTests)");
    test(
        "var tearDown = function() {}",
        "var tearDown = function() {}; " + "google_exportSymbol('tearDown',tearDown)");
    test(
        "var tearDownPage = function() {}",
        "var tearDownPage = function() {}; " + "google_exportSymbol('tearDownPage', tearDownPage)");
    test(
        "var testBar = function() { var testB = function() {}}",
        "var testBar = function(){ var testB = function() {}}; "
            + "google_exportSymbol('testBar',testBar)");
  }

  // https://github.com/google/closure-compiler/issues/2563
  @Test
  public void testFunctionExpressionsInAssignAreExported() {
    test(
        "testBar = function() {};",
        "testBar = function() {}; google_exportSymbol('testBar',testBar)");
  }

  @Test
  public void testFunctionExpressionsByLetAreExported() {
    testSame("let Foo = function() {var testA = function() {}}");
    test(
        "let setUp = function() {}",
        "let setUp = function() {}; google_exportSymbol('setUp', setUp)");
    test(
        "let testBar = function() {}",
        "let testBar = function() {}; google_exportSymbol('testBar', testBar)");
    test(
        "let tearDown = function() {}",
        lines(
            "let tearDown = function() {}; ", "google_exportSymbol('tearDown', tearDown)"));
  }

  @Test
  public void testFunctionExpressionsByConstAreExported() {
    testSame("const Foo = function() {var testA = function() {}}");
    test(
        "const setUp = function() {}",
        lines("const setUp = function() {}; ", "google_exportSymbol('setUp', setUp)"));
    test(
        "const testBar = function() {}",
        lines(
            "const testBar = function() {}; ", "google_exportSymbol('testBar', testBar)"));
    test(
        "const tearDown = function() {}",
        lines(
            "const tearDown = function() {}; ", "google_exportSymbol('tearDown', tearDown)"));
  }

  @Test
  public void testArrowFunctionExpressionsAreExported() {
    testSame("var Foo = ()=>{var testA = function() {}}");
    test(
        "var setUp = ()=>{}",
        lines("var setUp = ()=>{}; ", "google_exportSymbol('setUp', setUp)"));
    test(
        "var testBar = ()=>{}",
        lines("var testBar = ()=>{}; ", "google_exportSymbol('testBar', testBar)"));
    test(
        "var tearDown = ()=>{}",
        lines("var tearDown = ()=>{}; ", "google_exportSymbol('tearDown', tearDown)"));
  }

  @Test
  public void testFunctionAssignmentsAreExported() {
    testSame("Foo = {}; Foo.prototype.bar = function() {};");

    test(
        "Foo = {}; Foo.prototype.setUpPage = function() {};",
        "Foo = {}; Foo.prototype.setUpPage = function() {};"
            + "google_exportProperty(Foo.prototype, 'setUpPage', "
            + "Foo.prototype.setUpPage);");

    test(
        "Foo = {}; Foo.prototype.shouldRunTests = function() {};",
        "Foo = {}; Foo.prototype.shouldRunTests = function() {};"
            + "google_exportProperty(Foo.prototype, 'shouldRunTests', "
            + "Foo.prototype.shouldRunTests);");

    test(
        "Foo = {}; Foo.prototype.testBar = function() {};",
        "Foo = {}; Foo.prototype.testBar = function() {};"
            + "google_exportProperty(Foo.prototype, 'testBar', "
            + "Foo.prototype.testBar);");

    test(
        "window.testBar = function() {};",
        "window.testBar = function() {};"
            + "google_exportProperty(window, 'testBar', "
            + "window.testBar);");

    test(
        "Foo = {}; Foo.prototype.testBar = function() " + "{ var testBaz = function() {}};",
        "Foo = {}; Foo.prototype.testBar = function() "
            + "{ var testBaz = function() {}};"
            + "google_exportProperty(Foo.prototype, 'testBar', "
            + "Foo.prototype.testBar);");

    test(
        "Foo = {}; Foo.baz.prototype.testBar = function() " + "{ var testBaz = function() {}};",
        "Foo = {}; Foo.baz.prototype.testBar = function() "
            + "{ var testBaz = function() {}};"
            + "google_exportProperty(Foo.baz.prototype, 'testBar', "
            + "Foo.baz.prototype.testBar);");
  }

  @Test
  public void testExportTestSuite() {
    testSame("goog.testing.testSuite({'a': function() {}, 'b': function() {}});");
    test(
        "goog.testing.testSuite({a: function() {}, b: function() {}});",
        "goog.testing.testSuite({'a': function() {}, 'b': function() {}});");
  }

  @Test
  public void testMemberDefInObjLitInTestSuite_becomesStringKey() {
    test(
        "goog.testing.testSuite({a() {}, b() {}});",
        "goog.testing.testSuite({'a': function() {}, 'b': function() {}});");
  }

  @Test
  public void testMemberDefInObjLitInTestSuite_becomesStringKey_withSameJSDoc() {
    test(
        lines(
            "goog.testing.testSuite({",
            "  /** @suppress {checkTypes} */ a() {},",
            "  b() {}",
            "});"),
        lines(
            "goog.testing.testSuite({",
            "  /** @suppress {checkTypes} */",
            "  'a': function() {},",
            "  'b': function() {}",
            "});"));
  }

  @Test
  public void testComputedPropInObjLitInTestSuite_doesNotChange() {
    testSame(
        lines(
            "goog.testing.testSuite({",
            "  /** @suppress {checkTypes} */ ['a']() {},",
            "  ['b']() {}",
            "});"));
  }

  @Test
  public void testEs6Class_testMethod() {
    test(
        "class MyTest {testFoo() {}} goog.testing.testSuite(new MyTest());",
        "class MyTest {testFoo() {}} "
            + "google_exportProperty(MyTest.prototype, 'testFoo', MyTest.prototype.testFoo); "
            + "goog.testing.testSuite(new MyTest());");
  }

  @Test
  public void testEs6Class_lifeCycleMethods() {
    test(
        "class MyTest {"
            + "testFoo(){} setUp(){} tearDown(){} setUpPage(){} tearDownPage(){} notExported(){}"
            + "}"
            + "goog.testing.testSuite(new MyTest());",
        "class MyTest {"
            + "testFoo(){} setUp(){} tearDown(){} setUpPage(){} tearDownPage(){} notExported(){}"
            + "}"
            + "google_exportProperty(MyTest.prototype, 'testFoo', MyTest.prototype.testFoo);"
            + "google_exportProperty(MyTest.prototype, 'setUp', MyTest.prototype.setUp);"
            + "google_exportProperty(MyTest.prototype, 'tearDown', MyTest.prototype.tearDown);"
            + "google_exportProperty(MyTest.prototype, 'setUpPage', MyTest.prototype.setUpPage);"
            + "google_exportProperty(MyTest.prototype, 'tearDownPage', "
            + "MyTest.prototype.tearDownPage);"
            + "goog.testing.testSuite(new MyTest());");
  }

  // https://github.com/google/closure-compiler/issues/2563
  @Test
  public void testES6ClassAssignmentsAreExported() {
    testSame("Foo = class {bar() {}}");

    test(
        "Foo = class {testBar() {}}",
        "Foo = class {testBar() {}}; "
            + "google_exportProperty(Foo.prototype, 'testBar', Foo.prototype.testBar);");
  }

  @Test
  public void testEs6Class_testClassExpressionMethod() {
    test(
        "var MyTest=class{testFoo() {}}; goog.testing.testSuite(new MyTest());",
        "var MyTest=class{testFoo() {}}; "
            + "google_exportProperty(MyTest.prototype, 'testFoo', MyTest.prototype.testFoo); "
            + "goog.testing.testSuite(new MyTest());");
  }

  @Test
  public void testEs6Class_testClassExpressionByLetMethod() {
    test(
        "let MyTest=class{testFoo() {}}; goog.testing.testSuite(new MyTest());",
        "let MyTest=class{testFoo() {}}; "
            + "google_exportProperty(MyTest.prototype, 'testFoo', MyTest.prototype.testFoo); "
            + "goog.testing.testSuite(new MyTest());");
  }

  @Test
  public void testEs6Class_testClassExpressionByConstMethod() {
    test(
        "const MyTest=class{testFoo() {}}; goog.testing.testSuite(new MyTest());",
        "const MyTest=class{testFoo() {}}; "
            + "google_exportProperty(MyTest.prototype, 'testFoo', MyTest.prototype.testFoo); "
            + "goog.testing.testSuite(new MyTest());");
  }
}
