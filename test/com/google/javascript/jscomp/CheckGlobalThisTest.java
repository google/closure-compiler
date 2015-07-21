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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests {@link CheckGlobalThis}.
 */
public final class CheckGlobalThisTest extends CompilerTestCase {
  public CheckGlobalThisTest() {
    this.parseTypeInfo = true;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(
        compiler, new CheckGlobalThis(compiler));
  }

  private void testFailure(String js) {
    testSame(js, CheckGlobalThis.GLOBAL_THIS);
  }

  public void testGlobalThis1() throws Exception {
    testSame("var a = this;");
  }

  public void testGlobalThis2() {
    testFailure("this.foo = 5;");
  }

  public void testGlobalThis3() {
    testFailure("this[foo] = 5;");
  }

  public void testGlobalThis4() {
    testFailure("this['foo'] = 5;");
  }

  public void testGlobalThis5() {
    testFailure("(a = this).foo = 4;");
  }

  public void testGlobalThis6() {
    testSame("a = this;");
  }

  public void testGlobalThis7() {
    testFailure("var a = this.foo;");
  }

  public void testStaticFunction1() {
    testSame("function a() { return this; }");
  }

  public void testStaticFunction2() {
    testFailure("function a() { this.complex = 5; }");
  }

  public void testStaticFunction3() {
    testSame("var a = function() { return this; }");
  }

  public void testStaticFunction4() {
    testFailure("var a = function() { this.foo.bar = 6; }");
  }

  public void testStaticFunction5() {
    testSame("function a() { return function() { return this; } }");
  }

  public void testStaticFunction6() {
    testSame("function a() { return function() { this.x = 8; } }");
  }

  public void testStaticFunction7() {
    testSame("var a = function() { return function() { this.x = 8; } }");
  }

  public void testStaticFunction8() {
    testFailure("var a = function() { return this.foo; };");
  }

  public void testConstructor1() {
    testSame("/** @constructor */function A() { this.m2 = 5; }");
  }

  public void testConstructor2() {
    testSame("/** @constructor */var A = function() { this.m2 = 5; }");
  }

  public void testConstructor3() {
    testSame("/** @constructor */a.A = function() { this.m2 = 5; }");
  }

  public void testInterface1() {
    testSame(
        "/** @interface */function A() { /** @type {string} */ this.m2; }");
  }

  public void testOverride1() {
    testSame("/** @constructor */function A() { } var a = new A();" +
             "/** @override */ a.foo = function() { this.bar = 5; };");
  }

  public void testThisJSDoc1() throws Exception {
    testSame("/** @this {whatever} */function h() { this.foo = 56; }");
  }

  public void testThisJSDoc2() throws Exception {
    testSame("/** @this {whatever} */var h = function() { this.foo = 56; }");
  }

  public void testThisJSDoc3() throws Exception {
    testSame("/** @this {whatever} */foo.bar = function() { this.foo = 56; }");
  }

  public void testThisJSDoc4() throws Exception {
    testSame("/** @this {whatever} */function f() { this.foo = 56; }");
  }

  public void testThisJSDoc5() throws Exception {
    testSame("function a() { /** @this {x} */function f() { this.foo = 56; } }");
  }

  public void testMethod1() {
    testSame("A.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testMethod2() {
    testSame("a.B.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testMethod3() {
    testSame("a.b.c.D.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testMethod4() {
    testSame("a.prototype['x' + 'y'] =  function() { this.foo = 3; };");
  }

  public void testPropertyOfMethod() {
    testFailure("a.protoype.b = {}; " +
        "a.prototype.b.c = function() { this.foo = 3; };");
  }

  public void testStaticMethod1() {
    testFailure("a.b = function() { this.m2 = 5; }");
  }

  public void testStaticMethod2() {
    testSame("a.b = function() { return function() { this.m2 = 5; } }");
  }

  public void testStaticMethod3() {
    testSame("a.b.c = function() { return function() { this.m2 = 5; } }");
  }

  public void testMethodInStaticFunction() {
    testSame("function f() { A.prototype.m1 = function() { this.m2 = 5; } }");
  }

  public void testStaticFunctionInMethod1() {
    testSame("A.prototype.m1 = function() { function me() { this.m2 = 5; } }");
  }

  public void testStaticFunctionInMethod2() {
    testSame("A.prototype.m1 = function() {" +
        "  function me() {" +
        "    function myself() {" +
        "      function andI() { this.m2 = 5; } } } }");
  }

  public void testInnerFunction1() {
    testFailure("function f() { function g() { return this.x; } }");
  }

  public void testInnerFunction2() {
    testFailure("function f() { var g = function() { return this.x; } }");
  }

  public void testInnerFunction3() {
    testFailure(
        "function f() { var x = {}; x.y = function() { return this.x; } }");
  }

  public void testInnerFunction4() {
    testSame(
        "function f() { var x = {}; x.y(function() { return this.x; }); }");
  }

  public void testIssue182a() {
    testFailure("var NS = {read: function() { return this.foo; }};");
  }

  public void testIssue182b() {
    testFailure("var NS = {write: function() { this.foo = 3; }};");
  }

  public void testIssue182c() {
    testFailure("var NS = {}; NS.write2 = function() { this.foo = 3; };");
  }

  public void testIssue182d() {
    testSame("function Foo() {} " +
        "Foo.prototype = {write: function() { this.foo = 3; }};");
  }

  public void testLendsAnnotation1() {
    testFailure("/** @constructor */ function F() {}" +
        "dojo.declare(F, {foo: function() { return this.foo; }});");
  }

  public void testLendsAnnotation2() {
    testFailure("/** @constructor */ function F() {}" +
        "dojo.declare(F, /** @lends {F.bar} */ (" +
        "    {foo: function() { return this.foo; }}));");
  }

  public void testLendsAnnotation3() {
    testSame("/** @constructor */ function F() {}" +
        "dojo.declare(F, /** @lends {F.prototype} */ (" +
        "    {foo: function() { return this.foo; }}));");
  }

  public void testSuppressWarning() {
    testFailure("var x = function() { this.complex = 5; };");
  }

  public void testArrowFunction1() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testFailure("var a = () => this.foo;");
  }

  public void testArrowFunction2() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testFailure("(() => this.foo)();");
  }

  public void testArrowFunction3() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testFailure("function Foo() {} " +
        "Foo.prototype.getFoo = () => this.foo;");
  }

  public void testArrowFunction4() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testFailure("function Foo() {} " +
        "Foo.prototype.setFoo = (f) => { this.foo = f; };");
  }

  public void testInnerFunctionInClassMethod1() {
    // TODO(user): It would be nice to warn for using 'this' here
    testSame(LINE_JOINER.join(
        "function Foo() {}",
        "Foo.prototype.init = function() {",
        "  button.addEventListener('click', function () {",
        "    this.click();",
        "  });",
        "}",
        "Foo.prototype.click = function() {}"));
  }

  public void testInnerFunctionInClassMethod2() {
    // TODO(user): It would be nice to warn for using 'this' here
    testSame(LINE_JOINER.join(
        "function Foo() {",
        "  var x = function() {",
        "    button.addEventListener('click', function () {",
        "      this.click();",
        "    });",
        "  }",
        "}"));
  }

  public void testInnerFunctionInEs6ClassMethod() {
    // TODO(user): It would be nice to warn for using 'this' here
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame(LINE_JOINER.join(
        "class Foo {",
        "  constructor() {",
        "    button.addEventListener('click', function () {",
        "      this.click();",
        "    });",
        "  }",
        "  click() {}",
        "}"));
  }
}
