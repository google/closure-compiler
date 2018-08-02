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

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
