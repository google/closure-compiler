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

/**
 * Tests for {@link FunctionRewriter}
 *
 */
public class FunctionRewriterTest extends CompilerTestCase {

  private static final String RETURNARG_HELPER =
      "function JSCompiler_returnArg(JSCompiler_returnArg_value){" +
      "  return function() { return JSCompiler_returnArg_value }" +
      "}";
  private static final String GET_HELPER =
      "function JSCompiler_get(JSCompiler_get_name){" +
      "  return function() { return this[JSCompiler_get_name] }" +
      "}";
  private static final String SET_HELPER =
      "function JSCompiler_set(JSCompiler_set_name) {" +
      "  return function(JSCompiler_set_value){" +
      "    this[JSCompiler_set_name]=JSCompiler_set_value" +
      "  }" +
      "}";
  private static final String EMPTY_HELPER =
    "function JSCompiler_emptyFn() {" +
    "  return function(){}" +
    "}";
  private static final String IDENTITY_HELPER =
    "function JSCompiler_identityFn() {" +
    "  return function(JSCompiler_identityFn_value) {" +
    "      return JSCompiler_identityFn_value" +
    "  }" +
    "}";

  @Override
  protected void setUp() {
    super.enableLineNumberCheck(false);
  }

  @Override
  protected FunctionRewriter getProcessor(Compiler compiler) {
    return new FunctionRewriter(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // Pass reaches steady state after just 1 iteration
    return 1;
  }

  public void testReplaceReturnConst1() {
    String source = "a.prototype.foo = function() {return \"foobar\"}";
    checkCompilesToSame(source, 3);
    checkCompilesTo(source,
                    RETURNARG_HELPER,
                    "a.prototype.foo = JSCompiler_returnArg(\"foobar\")",
                    4);
  }

  public void testReplaceReturnConst2() {
    checkCompilesToSame("a.prototype.foo = function() {return foobar}", 10);
  }

  public void testReplaceReturnConst3() {
    String source = "a.prototype.foo = function() {return void 0;}";
    checkCompilesToSame(source, 3);
    checkCompilesTo(source,
                    RETURNARG_HELPER,
                    "a.prototype.foo = JSCompiler_returnArg(void 0)",
                    4);
  }

  public void testReplaceGetter1() {
    String source = "a.prototype.foo = function() {return this.foo_}";
    checkCompilesToSame(source, 3);
    checkCompilesTo(source,
                    GET_HELPER,
                    "a.prototype.foo = JSCompiler_get(\"foo_\")",
                    4);
  }

  public void testReplaceGetter2() {
    checkCompilesToSame("a.prototype.foo = function() {return}", 10);
  }

  public void testReplaceSetter1() {
    String source = "a.prototype.foo = function(v) {this.foo_ = v}";
    checkCompilesToSame(source, 4);
    checkCompilesTo(source,
                    SET_HELPER,
                    "a.prototype.foo = JSCompiler_set(\"foo_\")",
                    5);
  }

  public void testReplaceSetter2() {
    String source = "a.prototype.foo = function(v, v2) {this.foo_ = v}";
    checkCompilesToSame(source, 3);
    checkCompilesTo(source,
                    SET_HELPER,
                    "a.prototype.foo = JSCompiler_set(\"foo_\")",
                    4);
  }

  public void testReplaceSetter3() {
    checkCompilesToSame("a.prototype.foo = function() {this.foo_ = v}", 10);
  }

  public void testReplaceSetter4() {
    checkCompilesToSame(
        "a.prototype.foo = function(v, v2) {this.foo_ = v2}", 10);
  }

  public void testReplaceEmptyFunction1() {
    String source = "a.prototype.foo = function() {}";
    checkCompilesToSame(source, 4);
    checkCompilesTo(source,
                    EMPTY_HELPER,
                    "a.prototype.foo = JSCompiler_emptyFn()",
                    5);
  }

  public void testReplaceEmptyFunction2() {
    checkCompilesToSame("function foo() {}", 10);
  }

  public void testReplaceEmptyFunction3() {
    String source = "var foo = function() {}";
    checkCompilesToSame(source, 4);
    checkCompilesTo(source,
                    EMPTY_HELPER,
                    "var foo = JSCompiler_emptyFn()",
                    5);
  }



  public void testReplaceIdentityFunction1() {
    String source = "a.prototype.foo = function(a) {return a}";
    checkCompilesToSame(source, 2);
    checkCompilesTo(source,
                    IDENTITY_HELPER,
                    "a.prototype.foo = JSCompiler_identityFn()",
                    3);
  }

  public void testReplaceIdentityFunction2() {
    checkCompilesToSame("a.prototype.foo = function(a) {return a + 1}", 10);
  }

  public void testIssue538() {
    checkCompilesToSame(      "/** @constructor */\n" +
        "WebInspector.Setting = function() {}\n" +
        "WebInspector.Setting.prototype = {\n" +
        "    get name0(){return this._name;},\n" +
        "    get name1(){return this._name;},\n" +
        "    get name2(){return this._name;},\n" +
        "    get name3(){return this._name;},\n" +
        "    get name4(){return this._name;},\n" +
        "    get name5(){return this._name;},\n" +
        "    get name6(){return this._name;},\n" +
        "    get name7(){return this._name;},\n" +
        "    get name8(){return this._name;},\n" +
        "    get name9(){return this._name;},\n" +
        "}", 1);
  }

  private void checkCompilesTo(String src,
                               String expectedHdr,
                               String expectedBody,
                               int repetitions) {
    StringBuilder srcBuffer = new StringBuilder();
    StringBuilder expectedBuffer = new StringBuilder();

    expectedBuffer.append(expectedHdr);

    for (int idx = 0; idx < repetitions; idx++) {
      if (idx != 0) {
        srcBuffer.append(";");
        expectedBuffer.append(";");
      }
      srcBuffer.append(src);
      expectedBuffer.append(expectedBody);
    }
    test(srcBuffer.toString(), expectedBuffer.toString());
  }

  private void checkCompilesToSame(String src, int repetitions) {
    checkCompilesTo(src, "", src, repetitions);
  }
}
