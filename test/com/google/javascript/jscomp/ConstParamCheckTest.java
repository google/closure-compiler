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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests for {@link ConstParamCheck}.
 */
public final class ConstParamCheckTest extends CompilerTestCase {

  static final String CLOSURE_DEFS = ""
      + "var goog = {};"
      + "goog.string = {};"
      + "goog.string.Const = {};"
      + "goog.string.Const.from = function(x) {};";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInferConsts();
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ConstParamCheck(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  // Tests for string literal arguments.

  public void testStringLiteralArgument() {
    testSame(CLOSURE_DEFS
        + "goog.string.Const.from('foo');");
  }

  public void testTemplateLiteralArgument1() {
    testNoWarning(CLOSURE_DEFS + "goog.string.Const.from(`foo`);");
  }

  public void testTemplateLiteralArgument2() {
    testNoWarning(CLOSURE_DEFS
        + "var FOO = `foo`;"
        + "goog.string.Const.from(FOO);");
  }

  public void testTemplateLiteralWithSubstitutionsArgument1() {
    testError(
        CLOSURE_DEFS + "goog.string.Const.from(`foo${bar}`);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testTemplateLiteralWithSubstitutionsArgument2() {
    testError(CLOSURE_DEFS
        + "var FOO = `foo${bar}`;"
        + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
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

  public void testConcatenatedStringLiteralAndConstantArgument() {
    testSame(CLOSURE_DEFS
        + "var FOO = 'foo' + 'bar';"
        + "goog.string.Const.from('foo' + FOO + FOO + 'baz');");
  }

  public void testNotConstantArgument() {
    testError(
        CLOSURE_DEFS + "var foo = window.location.href;" + "goog.string.Const.from(foo);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument1() {
    testError(CLOSURE_DEFS
        + "var FOO = null;"
        + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument2() {
    testError(CLOSURE_DEFS
        + "var myFunction = function() {};"
        + "var FOO = myFunction();"
        + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testNotStringLiteralConstantArgument3() {
    testError(
        CLOSURE_DEFS + "goog.myFunc = function(param) { goog.string.Const.from(param) };",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testConstStringLiteralConstantArgument1() {
    testSame(CLOSURE_DEFS
        + "const FOO = 'foo';"
        + "goog.string.Const.from(FOO);");
  }

  public void testConstStringLiteralConstantArgument2() {
    testSame(CLOSURE_DEFS
        + "const FOO = 'foo';"
        + "const BAR = 'bar';"
        + "goog.string.Const.from(FOO + BAR);");
  }

  public void testConstNotStringLiteralArgument() {
    testError(CLOSURE_DEFS
            + "const myFunction = function() {};"
            + "goog.string.Const.from(myFunction());",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testConstStringLiteralAnnotatedConstantArgument() {
    testSame(CLOSURE_DEFS
        + "/** @const */ const foo = 'foo';"
        + "goog.string.Const.from(foo);");
  }

  public void testConstConcatenatedStringLiteralConstantArgument() {
    testSame(CLOSURE_DEFS
        + "const FOO = 'foo' + 'bar' + 'baz';"
        + "goog.string.Const.from(FOO);");
  }

  public void testConstConcatenatedStringLiteralAndConstantArgument() {
    testSame(CLOSURE_DEFS
        + "const FOO = 'foo' + 'bar';"
        + "goog.string.Const.from('foo' + FOO + FOO + 'baz');");
  }

  public void testAssignStringLiteralConstantArgument() {
    testError(CLOSURE_DEFS
            + "FOO = 'foo';"
            + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testLetStringLiteralConstantArgument() {
    testSame(CLOSURE_DEFS
            + "let FOO = 'foo';"
            + "goog.string.Const.from(FOO);");
  }

  public void testLetConcatenatedStringLiteralAndConstantArgument() {
    testSame(CLOSURE_DEFS
            + "let FOO = 'foo' + 'bar';"
            + "goog.string.Const.from('foo' + FOO + FOO + 'baz');");
  }

  public void testLetStringLiteralConstantArgumentOrder() {
    testSame(CLOSURE_DEFS
            + "var myFun = function() { goog.string.Const.from(FOO); };"
            + "let FOO = 'asdf';"
            + "myFun();");
  }

  public void testEnclosingProperty() {
    testSame(CLOSURE_DEFS
            + "let f = function() {"
            + "  const STR = '~*~*~';"
            + "  goog.string.Const.from(STR); }");
  }

  public void testNotStringLiteralEnhancedObject() {
    testError(CLOSURE_DEFS
            + "var FOO = obj('bar');"
            + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testObjectLiteralArgument() {
    testError(CLOSURE_DEFS
              + "var FOO = {bar: 'baz'};"
              + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testObjectLiteralGetPropArgument() {
    testError(CLOSURE_DEFS
            + "var FOO = {bar: 'baz'};"
            + "goog.string.Const.from(FOO.bar);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testArrayArgument() {
    testError(CLOSURE_DEFS
            + "var FOO = ['a', 'b', 'c'];"
            + "goog.string.Const.from(FOO[0]);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }

  public void testArrayDestructuring() {
    testError(CLOSURE_DEFS
            + "var [FOO, BAR] = ['a', 'b'];"
            + "goog.string.Const.from(FOO);",
        ConstParamCheck.CONST_NOT_STRING_LITERAL_ERROR);
  }
}
