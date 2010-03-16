/*
 * Copyright 2006 Google Inc.
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
 * Tests for {@link MethodCheck}.
 *
*
 */
public class MethodCheckTest extends CompilerTestCase {
  private static final String EXTERNS =
    "var window; window.setTimeout = function(fn, time) {};";

  private static final String METHOD_DEFS =
    "function Foo() {this.bar_ = null;}" +
    "Foo.prototype.oneArg = function(arg) {this.bar_ = arg;};" +
    "Foo.prototype.twoArg = function(arg1, arg2) {this.bar_ = arg1 + arg2;};" +
    "Foo.prototype.oneOrTwoArg = function(arg1) {this.bar_ = arg1;};" +

    "function Biz() {this.bar_ = null;}" +
    "Biz.prototype = " +
        "{oneOrTwoArg: function(arg1, arg2) {this.bar_ = arg1 + arg2;}};" +

    "var Boz = {};" +
    "Boz.staticMethod = function(arg1, arg2) {Boz.boz_ = arg1 + arg2;};" +

    // Methods defined in a separate functions and then added via assignment
    "function oneArg(arg) {arg = arg + 1;};" +
    "function twoArg(arg1, arg2) {arg1 = arg2 + 1;};" +
    "Foo.prototype.oneOrTwoArg2 = oneArg;" +
    "function Baz() {this.bar_ = null;}" +
    "Baz.prototype = {oneOrTwoArg2: twoArg};" +
    "Boz.staticMethod1 = oneArg;" +
    "Boz.staticMethod2 = twoArg;";

  public MethodCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new MethodCheck(compiler, CheckLevel.ERROR);
  }

  @Override
  public void testSame(String js) {
    super.testSame(METHOD_DEFS + js);
  }

  private void testErr(String js, DiagnosticType error) {
    test(METHOD_DEFS + js, null, error);
  }

  public void testMethods() {
    // Correct usage
    testSame("var f = new Foo();f.oneArg(1);");
    testSame("var f = new Foo();f.twoArg(1, 2);");
    testSame("var f = new Foo();f.oneOrTwoArg(1);");
    testSame("var b = new Biz();b.oneOrTwoArg(1, 2);");
    testSame("Boz.staticMethod(1, 2);");
    testSame("window.setTimeout(function() {}, 100);");

    // Incorrect usage. We mainly check the ability of the MethodCheck pass to
    // locate method signatures. Actual comparison of arity between signatures
    // and calls is mainly tested in FunctionCheckTest
    testErr("var f = new Foo();f.oneArg(1, 2);",
            FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    testErr("var f = new Foo();f.twoArg(1);",
            FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    testErr("Boz.staticMethod(1);",
            FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);
    testErr("window.setTimeout(function() {});",
            FunctionCheck.WRONG_ARGUMENT_COUNT_ERROR);

    // These should not throw exceptions
    testSame("var f = new Foo();f[1]();");
    testSame("var f = new Foo();var b = \"oneArg\";f[b]();");
  }

  public void testSeparateMethods() {
    testSame("var f = new Foo();f.oneOrTwoArg2(1);");
    testSame("var f = new Baz();f.oneOrTwoArg2(1, 2);");
    testSame("Boz.staticMethod1(1);");
    testSame("Boz.staticMethod2(1, 2);");

    // Can't detect these incorrect usuages as they are defined indirectly.
    testSame("var f = new Bar();f.oneOrTwoArg2(1, 2, 3);");
    testSame("Boz.staticMethod1(1, 2);");
    testSame("Boz.staticMethod2(1);");
  }

  public void testNoDefinition() {
    testSame("var f = new Foo();f.unknownMethod(1);");
  }
}
