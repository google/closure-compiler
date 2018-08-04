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

public class RewriteAsyncIterationTest extends CompilerTestCase {

  @Override
  protected void setUp() throws Exception {
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
    return new RewriteAsyncIteration(compiler);
  }

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

  public void testRuntimeTest() {
    test(
        lines(
            "function testBasic() {",
            "  async function* foo() {",
            "    yield (await Promise.resolve(1234))",
            "  }",
            "  let gen = foo();",
            "  assertEquals({ next: 1234, done: false }, gen.next());",
            "  assertEquals({ next: undefined, done: true }, gen.next());",
            "}"),
        lines(
            "function testBasic(){",
            "  function foo(){",
            "    return new $jscomp.AsyncGeneratorWrapper(function*(){",
            "      yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "        $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE,",
            "        yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "          $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE,",
            "          Promise.resolve(1234)))",
            "    }())",
            "  }",
            "  let gen=foo();",
            "  assertEquals({next:1234,done:false}, gen.next());",
            "  assertEquals({next:undefined,done:true},gen.next())",
            "}"));
  }

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
