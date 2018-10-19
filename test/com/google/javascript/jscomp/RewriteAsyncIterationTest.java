/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RewriteAsyncIterationTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteAsyncIteration.Builder(compiler)
        .rewriteSuperPropertyReferencesWithoutSuper(
            !compiler.getOptions().needsTranspilationFrom(FeatureSet.ES6))
        .build();
  }

  @Test
  public void testAsyncGenerator() {
    test(
        "async function* baz() { foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    foo();",
            "  })());",
            "}"));
  }

  @Test
  public void testAwaitInAsyncGenerator() {
    test(
        "async function* baz() { await foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));

    test(
        "async function* baz() { bar = await foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));
  }

  @Test
  public void testYieldInAsyncGenerator() {
    test(
        "async function* baz() { yield foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, foo());",
            "  })());",
            "}"));

    test(
        "async function* baz() { bar = yield foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, foo());",
            "  })());",
            "}"));
  }

  @Test
  public void testYieldAllInAsyncGenerator() {
    test(
        "async function* baz() { yield* foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR, foo());",
            "  })());",
            "}"));

    test(
        "async function* baz() { bar = yield* foo() }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR, foo());",
            "  })());",
            "}"));
  }

  @Test
  public void testComplexAsyncGeneratorStatements() {
    test(
        "async function* baz() { yield* (await foo()); }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR,",
            "      yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "          $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE,",
            "          foo()));",
            "  })());",
            "}"));
  }

  @Test
  public void testThisInAsyncGenerator() {
    test(
        "async function* baz() { yield this; }",
        lines(
            "function baz() {",
            "  const $jscomp$asyncIter$this = this;",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, $jscomp$asyncIter$this);",
            "  })());",
            "}"));
  }

  @Test
  public void testThisInAsyncGeneratorNestedInAsyncGenerator() {
    test(
        "async function* baz() { return async function*() { yield this; } }",
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    return function() {",
            "      const $jscomp$asyncIter$this = this;",
            "      return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "        yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "          $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE,",
            "          $jscomp$asyncIter$this);",
            "      })());",
            "    };",
            "  })());",
            "}"));
  }

  @Test
  public void testThisInFunctionNestedInAsyncGenerator() {
    test(
        lines("async function* baz() {  return function() { return this; }; }"),
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    return function() { return this; };",
            "  })());",
            "}"));

    test(
        lines("async function* baz() {  return () => this; }"),
        lines(
            "function baz() {",
            "  const $jscomp$asyncIter$this = this;",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    return () => $jscomp$asyncIter$this;",
            "  })());",
            "}"));
  }

  @Test
  public void testInnerSuperReferenceInAsyncGenerator() {
    test(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async *m() {",
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
            "    const $jscomp$asyncIter$super$get$m =",
            "        () => Object.getPrototypeOf(Object.getPrototypeOf(this)).m;",
            "    return new $jscomp.AsyncGeneratorWrapper(",
            "        function* () {",
            "          const tmp = $jscomp$asyncIter$super$get$m();",
            "          return tmp.call(null);",
            "        }());",
            "  }",
            "}"));
  }

  @Test
  public void testCannotConvertSuperGetElemInAsyncGenerator() {
    testError(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async *m() {",
            "    const tmp = super['m'];",
            "    return tmp.call(null);",
            "  }",
            "}"),
        RewriteAsyncIteration.CANNOT_CONVERT_ASYNCGEN,
        RewriteAsyncIteration.CANNOT_CONVERT_ASYNCGEN.format(
            "super only allowed with getprop (like super.foo(), not super['foo']())"));
  }

  @Test
  public void testInnerArrowFunctionUsingArguments() {
    test(
        lines(
            "class X {",
            "  async *m() {",
            "    return new Promise((resolve, reject) => {",
            "      return arguments;",
            "    });",
            "  }",
            "}"),
        lines(
            "class X {",
            "  m() {",
            "    const $jscomp$asyncIter$arguments = arguments;",
            "    return new $jscomp.AsyncGeneratorWrapper(",
            "        function* () {",
            "          return new Promise((resolve, reject) => {",
            "            return $jscomp$asyncIter$arguments",
            "          });",
            "        }());",
            "  }",
            "}"));
  }

  @Test
  public void testForAwaitOfDeclarations() {
    test(
        lines("async function abc() { for await (a of foo()) { bar(); } }"),
        lines(
            "async function abc() {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));

    test(
        lines("async function abc() { for await (var a of foo()) { bar(); } }"),
        lines(
            "async function abc() {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    var a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));

    test(
        lines("async function abc() { for await (let a of foo()) { bar(); } }"),
        lines(
            "async function abc() {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    let a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));

    test(
        lines("async function abc() { for await (const a of foo()) { bar(); } }"),
        lines(
            "async function abc() {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    const a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testForAwaitOfInAsyncArrow() {
    test(
        lines("async () => { for await (let a of foo()) { bar(); } }"),
        lines(
            "async () => {",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    let a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testLabelledForAwaitOf() {
    test(
        lines(
            "async () => {",
            "  label:",
            "  for await (let a of foo()) {",
            "    bar();",
            "  }",
            "}"),
        lines(
            "async () => {",
            "  label:",
            "  for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(foo());;) {",
            "    const $jscomp$forAwait$tempResult0 = await $jscomp$forAwait$tempIterator0.next();",
            "    if ($jscomp$forAwait$tempResult0.done) {",
            "      break;",
            "    }",
            "    let a = $jscomp$forAwait$tempResult0.value;",
            "    {",
            "      bar();",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testForAwaitOfInAsyncGenerator() {
    test(
        lines(
            "async function* foo() {",
            "  for await (let val of bar()) {",
            "    yield* val;",
            "  }",
            "}"),
        lines(
            "function foo() {",
            "  return new $jscomp.AsyncGeneratorWrapper(function*() {",
            "    for (const $jscomp$forAwait$tempIterator0 = $jscomp.makeAsyncIterator(bar());;) {",
            "      const $jscomp$forAwait$tempResult0 =",
            "          yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "              $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE,",
            "              $jscomp$forAwait$tempIterator0.next());",
            "      if ($jscomp$forAwait$tempResult0.done) {",
            "        break;",
            "      }",
            "      let val = $jscomp$forAwait$tempResult0.value;",
            "      {",
            "        yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "            $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_STAR,val)",
            "      }",
            "    }}());",
            "}"));
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
