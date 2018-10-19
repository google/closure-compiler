/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RewriteAsyncFunctionsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteAsyncFunctions.Builder(compiler)
        .rewriteSuperPropertyReferencesWithoutSuper(
            !compiler.getOptions().needsTranspilationFrom(FeatureSet.ES6))
        .build();
  }

  // Don't let the compiler actually inject any code.
  // It just makes the expected output hard to read and write.
  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Test
  public void testInnerArrowFunctionUsingThis() {
    test(
        lines(
            "class X {",
            "  async m() {",
            "    return new Promise((resolve, reject) => {",
            "      return this;",
            "    });",
            "  }",
            "}"),
        lines(
            "class X {",
            "  m() {",
            "    const $jscomp$async$this = this;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return new Promise((resolve, reject) => {",
            "            return $jscomp$async$this;",
            "          });",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testInnerSuperCall() {
    test(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async m() {",
            "    return super.m();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  m() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$super$get$m = () => super.m;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testInnerSuperReference() {
    test(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async m() {",
            "    const tmp = super.m;",
            "    return tmp.call(null);",
            "  }",
            "}"),
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  m() {",
            "    const $jscomp$async$super$get$m = () => super.m;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          const tmp = $jscomp$async$super$get$m();",
            "          return tmp.call(null);",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testInnerSuperCallEs2015Out() {
    setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async m() {",
            "    return super.m();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  m() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$super$get$m =",
            "        () => Object.getPrototypeOf(Object.getPrototypeOf(this)).m;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testInnerSuperCallStaticEs2015Out() {
    setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "class A {",
            "  static m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  static async m() {",
            "    return super.m();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  static m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  static m() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$super$get$m = () => Object.getPrototypeOf(this).m;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testNestedArrowFunctionUsingThis() {
    test(
        lines(
            "class X {",
            "  m() {",
            "    return async () => (() => this);",
            "  }",
            "}"),
        lines(
            "class X {",
            "  m() {",
            "    return () => {",
            "      const $jscomp$async$this = this;",
            "      return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "          function* () {",
            "            return () => $jscomp$async$this;",
            "          })",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testInnerArrowFunctionUsingArguments() {
    test(
        lines(
            "class X {",
            "  async m() {",
            "    return new Promise((resolve, reject) => {",
            "      return arguments;",
            "    });",
            "  }",
            "}"),
        lines(
            "class X {",
            "  m() {",
            "    const $jscomp$async$arguments = arguments;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return new Promise((resolve, reject) => {",
            "            return $jscomp$async$arguments",
            "          });",
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testRequiredCodeInjected() {
    test(
        "async function foo() { return 1; }",
        lines(
            "function foo() {",
            "  return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "      function* () {",
            "        return 1;",
            "      });",
            "}"));
    assertThat(getLastCompiler().injected).containsExactly("es6/execute_async_generator");
  }

  @Test
  public void testAwaitReplacement() {
    test(
        "async function foo(promise) { return await promise; }",
        lines(
            "function foo(promise) {",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return yield promise;",
            "        });",
            "}"));
  }

  @Test
  public void testArgumentsReplacement_topLevelCode() {
    testSame("arguments;");
  }

  @Test
  public void testArgumentsReplacement_normalFunction() {
    testSame("function f(a, b, ...rest) { return arguments.length; }");
  }

  @Test
  public void testArgumentsReplacement_asyncFunction() {
    test(
        "async function f(a, b, ...rest) { return arguments.length; }",
        lines(
            "function f(a, b, ...rest) {",
            "  const $jscomp$async$arguments = arguments;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$arguments.length;", // arguments replaced
            "        });",
            "}"));
  }

  @Test
  public void testArgumentsReplacement_asyncClosure() {
    test(
        lines(
            "function outer() {",
            "  async function f() { return arguments.length; }",
            "  return f(arguments)",
            "}"),
        lines(
            "function outer() {",
            "  function f() {",
            "    const $jscomp$async$arguments = arguments;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$arguments.length;", // arguments replaced
            "        });",
            "  }",
            "  return f(arguments)", // unchanged
            "}"));
  }

  @Test
  public void testArgumentsReplacement_normalClosureInAsync() {
    test(
        lines(
            "async function a() {",
            "  function inner() {",
            "    return arguments.length;",
            "  }",
            "  return inner.apply(undefined, arguments);", // this should get replaced
            "}"),
        lines(
            "function a() {",
            "  const $jscomp$async$arguments = arguments;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          function inner() {",
            "            return arguments.length;", // unchanged
            "          }",
            "          return inner.apply(undefined, $jscomp$async$arguments);",
            "        });",
            "}"));
  }

  @Test
  public void testClassMethod() {
    test(
        "class A { async f() { return this.x; } }",
        lines(
            "class A {",
            "  f() {",
            "    const $jscomp$async$this = this;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function *() {",
            "          return $jscomp$async$this.x;", // this replaced
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testAsyncClassMethodWithAsyncArrow() {
    test(
        lines(
            "class A {",
            "  async f() {",
            "    let g = async () => { console.log(this, arguments); };",
            "    g();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  f() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$arguments = arguments;",
            "      return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "          function *() {",
            "            let g = () => {",
            "              return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "                  function *() {",
            "                    console.log($jscomp$async$this, $jscomp$async$arguments);",
            "                  });",
            "            };",
            "            g();",
            "          });",
            "  }",
            "}"));
  }

  @Test
  public void testNonAsyncClassMethodWithAsyncArrow() {
    test(
        lines(
            "class A {",
            "  f() {",
            "    let g = async () => { console.log(this, arguments); };",
            "    g();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  f() {",
            "    let g = () => {",
            "      const $jscomp$async$this = this;",
            "      const $jscomp$async$arguments = arguments;",
            "      return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "          function *() {",
            "            console.log($jscomp$async$this, $jscomp$async$arguments);",
            "          });",
            "    };",
            "    g();",
            "  }",
            "}"));
  }

  @Test
  public void testArrowFunctionExpressionBody() {
    test(
        "let f = async () => 1;",
        lines(
            "let f = () => {",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return 1;",
            "        });",
            "}"));
  }
}
