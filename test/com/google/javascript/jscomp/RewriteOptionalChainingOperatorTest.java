/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.OptionalChainRewriter.TmpVarNameCreator;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Test cases for transpilation pass that replaces the optional chaining operator (`?.`). */
@RunWith(Enclosed.class)
public final class RewriteOptionalChainingOperatorTest {

  /**
   * Declares a class and variables to make construction of optional chains for test cases
   * convenient.
   */
  private static final String TEST_BASE_EXTERNS =
      CompilerTestCase.lines(
          "", //
          "class TestObject {",
          "  constructor() {",
          "    /** @const {!TestObject} */",
          "    this.obj = this;",
          "    /** @const {!Array<!TestObject>} */",
          "    this.ary = [this];",
          "    /** @const {function(number): !TestObject} */",
          "    this.fun = (num) => this.ary[num];",
          "    /** @const */",
          "    this.num = 0;",
          "  }",
          "  /** @return {!TestObject} */",
          "  getObj() { return this; }",
          "  /** @return {!Array<!TestObject>} */",
          "  getArr() { return this.ary; }",
          "  /** @return {function(number): !TestObject} */",
          "  getFun() { return this.fun; }",
          "  /** @return {number} */",
          "  getNum() { return 0; }",
          "}",
          "",
          "const obj = new TestObject();",
          "const ary = obj.ary;",
          "const fun = obj.fun;",
          "",
          "/** @return {!TestObject} */",
          "function getObj() {",
          "  return obj;",
          "}",
          "",
          "/** @return {!Array<!TestObject>} */",
          "function getAry() {",
          "  return ary;",
          "}",
          "",
          "/** @return {function(number): !TestObject} */",
          "function getFun() {",
          "  return fun;",
          "}",
          "");

  @RunWith(Parameterized.class)
  public static class BaseTestClass extends CompilerTestCase {

    @Parameter(0)
    public String jsSrc;

    @Parameter(1)
    public String jsOutput;

    @Parameters(name = "{0} #{index}")
    public static final ImmutableList<Object> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {
              // Do rewriting within a function.
              // This will fail if the AST change is reported for the script's scope instead of
              // the function's scope.
              lines(
                  "function foo() {", //
                  "  return obj?.num;",
                  "}"),
              lines(
                  "function foo() {", //
                  "  let tmp0;",
                  "  return (tmp0 = obj) == null ? void 0 : tmp0.num;",
                  "}")
            },
            {
              "eval?.('foo()');",
              lines(
                  "let tmp0;", //
                  "(tmp0 = eval) == null",
                  "    ? void 0",
                  // The spec says that `eval?.()` must behave like an indirect
                  // eval, so it is important that `eval?.()` not be transpiled to
                  // anything that ends up containing `eval()`.
                  // We must be sure to call it using the temporary variable.
                  "    : tmp0('foo()');")
            },
            {
              "obj?.ary[getNum()].obj.obj?.obj.ary",
              lines(
                  "let tmp0;", //
                  "let tmp1;",
                  "(tmp0 = obj) == null",
                  "    ? void 0",
                  "    : (tmp1 = tmp0.ary[getNum()].obj.obj) == null",
                  "        ? void 0",
                  "        : tmp1.obj.ary",
                  "")
            },
            {
              "(obj?.ary[getNum()]).obj.ary",
              lines(
                  "let tmp0;", //
                  "((tmp0 = obj) == null",
                  "    ? void 0",
                  "    : tmp0.ary[getNum()]).obj.ary",
                  "")
            },
            {
              "obj?.obj.obj?.fun(obj.getNum())",
              lines(
                  "let tmp0;", //
                  "let tmp1;",
                  "(tmp0 = obj) == null",
                  "    ? void 0",
                  "    : (tmp1 = tmp0.obj.obj) == null",
                  "        ? void 0",
                  "        : tmp1.fun(obj.getNum())",
                  "")
            },
            {
              "obj.ary?.[num].fun(obj.getNum?.())",
              lines(
                  "",
                  "let tmp0;",
                  "let tmp1;",
                  "let tmp2;",
                  "(tmp2 = obj.ary) == null",
                  "    ? void 0",
                  "    : tmp2[num].fun(",
                  "        (tmp1 = (tmp0 = obj).getNum) == null",
                  "            ? void 0",
                  "            : tmp1.call(tmp0))",
                  "")
            },
            {
              "obj?.obj",
              lines(
                  "let tmp0;", //
                  "(tmp0 = obj) == null ? void 0 : tmp0.obj")
            },
            {
              "ary?.[num]",
              lines(
                  "let tmp0", //
                  "(tmp0 = ary) == null ? void 0 : tmp0[num]")
            },
            {
              "obj.getObj?.()",
              lines(
                  "let tmp0;", //
                  "let tmp1;",
                  "(tmp1 = (tmp0 = obj).getObj) == null",
                  "    ? void 0",
                  "    : tmp1.call(tmp0)",
                  "")
            },
            {
              "getObj().getObj?.()",
              lines(
                  "", //
                  "let tmp0;",
                  "let tmp1;",
                  "(tmp1 = (tmp0 = getObj()).getObj) == null",
                  "    ? void 0",
                  "    : tmp1.call(tmp0)",
                  "")
            },
            {
              "(getObj()?.getObj)()",
              lines(
                  "let tmp0;", //
                  "let tmp1;",
                  "((tmp0 = getObj()) == null",
                  "    ? void 0",
                  // Ideally we wouldn't generate a temporary to hold
                  // a temporary we already generated, but our logic
                  // is simpler if we don't worry about it.
                  // We will rely on optimizations to clean this up
                  // after transpilation.
                  "    : (tmp1 = tmp0).getObj).call(tmp1)",
                  "")
            },
            {
              "getAry()?.[num]",
              lines(
                  "", //
                  "let tmp0;",
                  "(tmp0 = getAry()) == null",
                  "    ? void 0",
                  "    : tmp0[num]",
                  "")
            },
            {
              "getFun()?.(num)",
              lines(
                  "", //
                  "let tmp0;",
                  "(tmp0 = getFun()) == null",
                  "    ? void 0",
                  "    : tmp0(num)",
                  "")
            },
            {
              "fun?.(obj?.getNum())",
              lines(
                  "let tmp0;",
                  "let tmp1;",
                  "(tmp1 = fun) == null",
                  "    ? void 0",
                  "    : tmp1(",
                  "          (tmp0 = obj) == null",
                  "              ? void 0",
                  "              : tmp0.getNum())"),
            },
            {
              "obj?.fun(obj?.getNum())",
              lines(
                  "let tmp0;",
                  "let tmp1;",
                  "(tmp1 = obj) == null",
                  "    ? void 0",
                  "    : tmp1.fun(",
                  "        (tmp0 = obj) == null",
                  "            ? void 0",
                  "            : tmp0.getNum())"),
            },
            {
              "while(obj = ary?.[obj?.getNum()]) {}",
              lines(
                  "let tmp0;",
                  "let tmp1;",
                  "while(",
                  "    obj = ",
                  "        (tmp1 = ary) == null",
                  "            ? void 0",
                  "            : tmp1[",
                  "                (tmp0 = obj) == null",
                  "                    ? void 0",
                  "                    : tmp0.getNum()",
                  "            ]) {",
                  "}"),
            },
            {
              "let a = fun?.(num).obj.ary[obj?.getNum()]",
              lines(
                  "let tmp0;",
                  "let tmp1;",
                  "let a =",
                  "    (tmp1 = fun) == null",
                  "        ? void 0",
                  "        : tmp1(num).obj.ary[",
                  "            (tmp0 = obj) == null",
                  "                ? void 0",
                  "                : tmp0.getNum()",
                  "            ]")
            },
          });
    }

    @Override
    @Before
    public void setUp() throws Exception {
      super.setUp();

      setLanguage(LanguageMode.UNSUPPORTED, LanguageMode.ECMASCRIPT_2019);

      enableTypeCheck();
      enableTypeInfoValidation();
    }

    @Override
    protected CompilerPass getProcessor(Compiler compiler) {
      // Just name temporary variables "tmp0", "tmp1", etc. to make the tests clearer.
      TmpVarNameCreator testVarNameCreator =
          new TmpVarNameCreator() {
            int counter = 0;

            @Override
            public String createTmpVarName() {
              return "tmp" + counter++;
            }
          };
      return new RewriteOptionalChainingOperator(compiler, testVarNameCreator);
    }

    @Test
    public void doTest() {
      test(externs(TEST_BASE_EXTERNS), srcs(jsSrc), expected(jsOutput));
    }
  }

  @RunWith(JUnit4.class)
  public static class DeleteOptChainTests extends CompilerTestCase {
    @Override
    @Before
    public void setUp() throws Exception {
      super.setUp();
      setLanguage(LanguageMode.ECMASCRIPT_NEXT_IN, LanguageMode.ECMASCRIPT_2019);
      enableTypeCheck();
      enableTypeInfoValidation();
    }

    @Override
    protected CompilerPass getProcessor(Compiler compiler) {
      // Just name temporary variables "tmp0", "tmp1", etc. to make the tests clearer.
      TmpVarNameCreator testVarNameCreator =
          new TmpVarNameCreator() {
            int counter = 0;

            @Override
            public String createTmpVarName() {
              return "tmp" + counter++;
            }
          };
      return new RewriteOptionalChainingOperator(compiler, testVarNameCreator);
    }

    @Test
    public void testDeleteOptChainGetProp() {
      test(
          externs(TEST_BASE_EXTERNS),
          srcs(lines("delete obj?.num;")),
          expected(lines("let tmp0;", "(tmp0 = obj) == null ? true : delete tmp0.num;")));
    }

    @Test
    public void testDeleteOptChainGetProp2() {
      test(
          externs(TEST_BASE_EXTERNS),
          srcs(lines("delete this?.obj?.num;")),
          expected(
              lines(
                  "let tmp0;",
                  "let tmp1",
                  "(tmp0 = this) == null ? true : (tmp1 = tmp0.obj) == null ? true : delete"
                      + " tmp1.num;")));
    }

    @Test
    public void testDeleteOptChainGetProp3() {
      test(
          externs(TEST_BASE_EXTERNS),
          srcs(
              lines(
                  "delete getFun()?.(num).num" // get the num-th !TestObject inside `ary`, and
                  // delete
                  // its `num` prop
                  )),
          expected(
              lines(
                  "", //
                  "let tmp0;",
                  "(tmp0 = getFun()) == null",
                  "    ? true",
                  "    : delete tmp0(num).num",
                  "")));
    }
  }
}
