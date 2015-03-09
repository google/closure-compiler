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

import com.google.common.base.Joiner;

/**
 * Tests for {@link ClosureEarlyOptimize}
 *
 * @author robbyw@google.com (Robby Walker)
 */
public class ClosureEarlyOptimizeTest extends CompilerTestCase {

  private static final String EXTERNS = "var window;"
      + "/**\n" +
      " * @typedef {{then: !Function}}\n" +
      " */\n" +
      "var Thenable;";

  private static final Joiner NEWLINE_JOINER = Joiner.on('\n');
  private static final String PROMISE_BOILERPLATE = NEWLINE_JOINER.join(
      "/** @const */ var goog = {};",
      "/** @constructor */ goog.Promise = function(){};",
      "/** @return {goog.Promise} */",
      "goog.Promise.prototype.then = function(opt_a, opt_b) {",
      "    return new goog.Promise};",
      "goog.Promise.prototype.thenVoid = function(opt_a, opt_b) {};");

  public ClosureEarlyOptimizeTest() {
    super(EXTERNS);
  }

  public void testRemoveAbstract() {
    test("function Foo() {}; Foo.prototype.doSomething = goog.abstractMethod;",
        "function Foo() {};");
  }

  @Override
  public void setUp() {
    super.disableTypeCheck();
  }

  public void testRemoveMultiplySetAbstract() {
    test("function Foo() {}; Foo.prototype.doSomething = "
        + "Foo.prototype.doSomethingElse = Foo.prototype.oneMore = "
        + "goog.abstractMethod;",
        "function Foo() {};");
  }

  public void testDoNotRemoveNormal() {
    testSame("function Foo() {}; Foo.prototype.doSomething = function() {};");
  }

  public void testDoNotRemoveOverride() {
    test("function Foo() {}; Foo.prototype.doSomething = goog.abstractMethod;"
         + "function Bar() {}; goog.inherits(Bar, Foo);"
         + "Bar.prototype.doSomething = function() {}",
         "function Foo() {}; function Bar() {}; goog.inherits(Bar, Foo);"
         + "Bar.prototype.doSomething = function() {}");
  }

  public void testDoNotRemoveNonQualifiedName() {
    testSame("document.getElementById('x').y = goog.abstractMethod;");
  }

  public void testStopRemovalAtNonQualifiedName() {
    test("function Foo() {}; function Bar() {};"
         + "Foo.prototype.x = document.getElementById('x').y = Bar.prototype.x"
         + " = goog.abstractMethod;",
         "function Foo() {}; function Bar() {};"
         + "Foo.prototype.x = document.getElementById('x').y = "
         + "goog.abstractMethod;");
  }

  public void testAssertionRemoval1() {
    test("var x = goog.asserts.assert(y(), 'message');", "var x = y();");
  }

  public void testAssertionRemoval2() {
    test("goog.asserts.assert(y(), 'message');", "");
  }

  public void testAssertionRemoval3() {
    test("goog.asserts.assert();", "");
  }

  public void testAssertionRemoval4() {
    test("var x = goog.asserts.assert();", "var x = void 0;");
  }

  public void testNoReplaceThen1() {
    super.enableTypeCheck(CheckLevel.OFF);

    String boilerplate = PROMISE_BOILERPLATE;
    testSame("var x = y.then();");
    testSame("var x = y.then(function(){});");
    testSame("var x = y.then(function(){}, function() {});");

    testSame("y.then();");
    testSame("y.then(function(){});");
    testSame("y.then(function(){}, function() {});");

    testSame(boilerplate + "var x = y.then();");
    testSame(boilerplate + "var x = y.then(function(){});");
    testSame(boilerplate + "var x = y.then(function(){}, function() {});");

    testSame(boilerplate + "y.then();");
    testSame(boilerplate + "y.then(function(){});");
    testSame(boilerplate + "y.then(function(){}, function() {});");
  }

  public void testNoReplaceThen2() {
    super.enableTypeCheck(CheckLevel.OFF);

    String boilerplate = PROMISE_BOILERPLATE
        + "/** @type {!Thenable} */ var y = f();";

    testSame(boilerplate + "var x = y.then();");
    testSame(boilerplate + "var x = y.then(function(){});");
    testSame(boilerplate + "var x = y.then(function(){}, function() {});");

    testSame(boilerplate + "y.then();");
    testSame(boilerplate + "y.then(function(){});");
    testSame(boilerplate + "y.then(function(){}, function() {});");
  }

  public void testNoReplaceThen3() {
    super.enableTypeCheck(CheckLevel.OFF);

    String boilerplate = PROMISE_BOILERPLATE
        + "/** @type {goog.Promise} */ var y = f();";

    testSame(boilerplate + "var x = y.then();");
    testSame(boilerplate + "var x = y.then(function(){});");
    testSame(boilerplate + "var x = y.then(function(){}, function() {});");

    testSame(boilerplate + "var x = y.then().then();");
  }

  public void testReplaceThen1() {
    super.enableTypeCheck(CheckLevel.OFF);

    String boilerplate = PROMISE_BOILERPLATE
        + "/** @type {goog.Promise} */ var y = f();";

    test(
        boilerplate + "y.then();",
        boilerplate + "y.thenVoid();");
    test(
        boilerplate + "y.then(function f(){});",
        boilerplate + "y.thenVoid(function f(){});");
    test(
        boilerplate + "y.then(function f(){}, function g() {});",
        boilerplate + "y.thenVoid(function f(){}, function g() {});");
    test(
        boilerplate + "y.then().then();",
        boilerplate + "y.then().thenVoid();");

  }

  @Override
  protected ClosureEarlyOptimize getProcessor(Compiler compiler) {
    return new ClosureEarlyOptimize(compiler, true, true);
  }
}
