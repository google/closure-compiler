/*
 * Copyright 2013 The Closure Compiler Authors.
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
 * Tests for {@link ConstParamCheck}.
 */
public final class ConstParamCheckTest extends CompilerTestCase {

  static final String CLOSURE_DEFS = ""
      + "var goog = {};"
      + "goog.string = {};"
      + "goog.string.Const = {};"
      + "goog.string.Const.from = function(x) {};";

  public ConstParamCheckTest() {
    enableInferConsts(true);
    enableNormalize();
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ConstParamCheck(compiler);
  }

  @Override
  public int getNumRepetitions() {
    return 1;
  }

  // Tests for string literal arguments.

  public void testStringLiteralArgument() {
    testSame(CLOSURE_DEFS
        + "goog.string.Const.from('foo');");
  }

  public void testConcatenatedStringLiteralArgument() {
    testSame(CLOSURE_DEFS
        + "goog.string.Const.from('foo' + 'bar' + 'baz');");
  }

  public void testNotStringLiteralArgument1() {
    testError(CLOSURE_DEFS
        + "goog.string.Const.from(null);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralArgument2() {
    testError(CLOSURE_DEFS
        + "var myFunction = function() {};"
        + "goog.string.Const.from(myFunction());",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralArgument3() {
    testError(CLOSURE_DEFS
        + "var myFunction = function() {};"
        + "goog.string.Const.from('foo' + myFunction() + 'bar');",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralArgumentAliased() {
    testError(CLOSURE_DEFS
        + "var myFunction = function() {};"
        + "var mkConst = goog.string.Const.from;"
        + "mkConst(myFunction());",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralArgumentAliasedAfterCollapse() {
    testError(
        CLOSURE_DEFS
            + "var myFunction = function() {};"
            + "var mkConst = goog$string$Const$from;"
            + "mkConst(myFunction());",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralArgumentOnCollapsedProperties() {
    testError("goog$string$Const$from(null);", ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  // Tests for string literal constant arguments.

  public void testStringLiteralConstantArgument() {
    testSame(CLOSURE_DEFS
        + "var FOO = 'foo';"
        + "goog.string.Const.from(FOO);");
  }

  public void testStringLiteralAnnotatedConstantArgument() {
    testSame(CLOSURE_DEFS
        + "/** @const */ var foo = 'foo';"
        + "goog.string.Const.from(foo);");
  }

  public void testStringLiteralConstantArgumentOrder() {
    testSame(CLOSURE_DEFS
        + "var myFun = function() { goog.string.Const.from(FOO); };"
        + "var FOO = 'asdf';"
        + "myFun();");
  }

  public void testConcatenatedStringLiteralConstantArgument() {
    testSame(CLOSURE_DEFS
        + "var FOO = 'foo' + 'bar' + 'baz';"
        + "goog.string.Const.from(FOO);");
  }

  public void testNotConstantArgument() {
    testError(
        CLOSURE_DEFS + "var foo = window.location.href;" + "goog.string.Const.from(foo);",
        ConstParamCheck.CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument1() {
    testError(CLOSURE_DEFS
        + "var FOO = null;"
        + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument2() {
    testError(CLOSURE_DEFS
        + "var myFunction = function() {};"
        + "var FOO = myFunction();"
        + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument3() {
    testError(
        CLOSURE_DEFS + "goog.myFunc = function(param) { goog.string.Const.from(param) };",
        ConstParamCheck.CONST_NOT_ASSIGNED_STRING_LITERAL_ERROR);
  }
}
