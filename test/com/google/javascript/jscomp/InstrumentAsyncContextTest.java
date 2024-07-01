/*
 * Copyright 2024 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the InstrumentAsyncContext compiler pass. */
@RunWith(JUnit4.class)
public final class InstrumentAsyncContextTest extends CompilerTestCase {

  private boolean instrumentAwait = true;

  private static final boolean SUPPORT_TOP_LEVEL_AWAIT = false;

  private static final String EXTERNS =
      lines(
          "/** @const */",
          "var AsyncContext = {};",
          "",
          "AsyncContext.wrap = function(f) {};",
          "/** @constructor */",
          "AsyncContext.Snapshot = function() {};",
          "/**",
          " * @param {!Function} f",
          " * @return {!Function}",
          " */",
          "AsyncContext.Snapshot.prototype.run = function(f) {};",
          "/**",
          " * @constructor",
          " * @param {string=} name",
          " * @param {*=} defaultValue",
          " */",
          "",
          "AsyncContext.Variable = function(name, defaultValue) {};",
          "/** @return {*} */",
          "AsyncContext.Variable.prototype.get = function() {};",
          "/**",
          " * @param {*} value",
          " * @param {!Function} f",
          " * @return {*}",
          " */",
          "AsyncContext.Variable.prototype.run = function(value, f) {};",
          "",
          "/** @const */",
          "var console = {};",
          "console.log = function(arg) {};");

  public InstrumentAsyncContextTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    instrumentAwait = true;
    // setLanguageOut(LanguageMode.ECMASCRIPT5);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InstrumentAsyncContext(compiler, instrumentAwait);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_POLYFILL, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    (0, $jscomp.asyncContextReenter)(",
            "        await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testForAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of gen()) {",
            "    use(x);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    for await (const x of (0, $jscomp.asyncContextExit)(gen(), $jscomp$context)) {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      use(x);",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testForAwaitNotInBlock() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  if (1)",
            "    for await (const x of gen()) {",
            "      use(x);",
            "    }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    if (1) {",
            "      for await (const x of (0, $jscomp.asyncContextExit)(gen(), $jscomp$context)) {",
            "        (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "        use(x);",
            "        (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "      }",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testForAwaitMixedWithAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of await gen()) {",
            "    await use(x);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    for await (const x of",
            "                   (0, $jscomp.asyncContextExit)(",
            "                       (0, $jscomp.asyncContextReenter)(",
            "                           await (0, $jscomp.asyncContextExit)(gen(),",
            "                               $jscomp$context),",
            "                           $jscomp$context),",
            "                       $jscomp$context)) {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(use(x), $jscomp$context),",
            "          $jscomp$context);",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testTopLevelAwait() {
    if (!SUPPORT_TOP_LEVEL_AWAIT) {
      return;
    }
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "await 1;"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "var $jscomp$context = $jscomp.asyncContextEnter();",
            "(0, $jscomp.asyncContextReenter)(",
            "    await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);"));
  }

  @Test
  public void testMultipleAwaits() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "  await 2;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    (0, $jscomp.asyncContextReenter)(",
            "        await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    (0, $jscomp.asyncContextReenter)(",
            "        await (0, $jscomp.asyncContextExit)(2, $jscomp$context), $jscomp$context);",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testAwaitInsideTryCatch() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    await 1;",
            "  } catch (e) {",
            "    console.log(e);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } catch (e) {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      console.log(e);",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testAwaitInsideTryCatchFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    await 1;",
            "  } catch (e) {",
            "    console.log(e);",
            "  } finally {",
            "    console.log('finally');",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } catch (e) {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      console.log(e);",
            "    } finally {",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testAwaitInsideTryFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    await 1;",
            "  } finally {",
            "    console.log('finally');",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testReturnAwait() {
    // NOTE: `return await` does not require any instrumentation if it's not inside a try block.
    testSame(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  return await 1;",
            "}"));
  }

  @Test
  public void testReturnAwaitInsideTryCatch() {
    // NOTE: `return await` does not require any instrumentation if it's not inside a try block.
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    return await 1;",
            "  } catch (e) {",
            "    console.log(e);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      return (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } catch (e) {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      console.log(e);",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testReturnAwaitInsideTryFinally() {
    // NOTE: `return await` does not require any instrumentation if it's not inside a try block.
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    return await 1;",
            "  } finally {",
            "    console.log('finally');",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      return (0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "}"));
  }

  @Test
  public void testAwaitImmediatelyReturnedFromBodylessArrow() {
    // NOTE: `return await` does not require any instrumentation if it's not inside a try block.
    testSame(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "(async () => await 1)();"));
  }

  @Test
  public void testAwaitInsideBodylessArrow() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "(async () => use(await 1))();"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "(async () => {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  try {",
            "    return use((0, $jscomp.asyncContextReenter)(",
            "          await (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context));",
            "  } finally {",
            "    (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "  }",
            "})();"));
  }

  @Test
  public void testGenerator() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithTryCatchFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  try {",
            "    yield 1;",
            "  } catch (e) {",
            "    yield 2;",
            "  } finally {",
            "    yield 3;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "            $jscomp$context);",
            "      } catch (e) {",
            "        (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(2, $jscomp$context),",
            "            $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(3, $jscomp$context),",
            "            $jscomp$context);",
            "      }",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithTryFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  try {",
            "    yield 1;",
            "  } finally {",
            "    yield 3;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "            $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(3, $jscomp$context),",
            "            $jscomp$context);",
            "      }",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithNestedTry() {
    // Only the inner try-catch gets instrumented with a re-enter.
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  try {",
            "    try {",
            "      yield 1;",
            "    } catch (e) {",
            "      console.log(e);",
            "    }",
            "  } catch (e) {",
            "    console.log(e);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      try {",
            "        try {",
            "          (0, $jscomp.asyncContextReenter)(",
            "              yield (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "              $jscomp$context);",
            "        } catch (e) {",
            "          (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "          console.log(e);",
            "        }",
            "      } catch (e) {",
            "        console.log(e);",
            "      }",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithEmptyYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(void 0, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithNoYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  console.log(1);",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      console.log(1);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorWithEmptyBody() {
    // No instrumentation needed because there's no code inside to observe vars
    testSame(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {}"));
  }

  @Test
  public void testGeneratorWithArguments() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f(a) {",
            "  yield a + arguments[1];",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f(a) {",
            "  const $jscomp$arguments$m1146332801$0 = arguments;",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              a + $jscomp$arguments$m1146332801$0[1],",
            "              $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testGeneratorMethod() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    yield 1;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return (function*() {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "            $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodWithThis() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *g() {",
            "    yield this;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  g() {",
            "    const $jscomp$this$m1146332801$0 = this;",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return (function*() {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(",
            "                $jscomp$this$m1146332801$0, $jscomp$context), $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodWithArguments() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *g() {",
            "    yield arguments.length;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  g() {",
            "    const $jscomp$arguments$m1146332801$0 = arguments;",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return (function*() {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(",
            "                $jscomp$arguments$m1146332801$0.length, $jscomp$context),",
            "                $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperStatic() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  /** @nocollapse */",
            "  static *f() {",
            "    yield* super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  /** @nocollapse */",
            "  static f() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context);",
            "  }",
            "  /** @nocollapse */",
            "  static *f$jscomp$m1146332801$0($jscomp$context) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield* (0, $jscomp.asyncContextExit)(",
            "              super.f(), $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuper() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    yield super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              super.f(), $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a, b, c) {",
            "    yield super.x;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a, b, c) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context, a, b, c);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, a, b, c) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(super.x, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperReadsArgumentsArray() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a) {",
            "    yield super.x + arguments.length;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context, arguments, a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, $jscomp$arguments$m1146332801$1, a) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              super.x + $jscomp$arguments$m1146332801$1.length,",
            "              $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithDefaultParameter() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a = 1) {",
            "    yield super.y;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context, a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, a = 1) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(super.y, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithDestructuredParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f({a}, [b = 1]) {",
            "    yield super.x;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f($jscomp$m1146332801$1, $jscomp$m1146332801$2) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0(",
            "        $jscomp$context, ",
            "        $jscomp$m1146332801$1, ",
            "        $jscomp$m1146332801$2);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, {a}, [b = 1]) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(super.x, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithRestParameter() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(...a) {",
            "    yield super.foo;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(...a) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context, ...a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, ...a) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(super.foo, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithComplicatedParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a, {b}, c = 1, ...[d]) {",
            "    yield super.foo;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a, $jscomp$m1146332801$1, c, ...$jscomp$m1146332801$2) {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0(",
            "        $jscomp$context, a, $jscomp$m1146332801$1, c, ...$jscomp$m1146332801$2);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$context, a, {b}, c = 1, ...[d]) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(super.foo, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testGeneratorMethodSuperWithComputedKey() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  *[Symbol.iterator]() {",
            "    yield super.foo();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  [Symbol.iterator]() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.$jscomp$m1146332801$0($jscomp$context);",
            "  }",
            "  *$jscomp$m1146332801$0($jscomp$context) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              super.foo(), $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testAsyncGenerator() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function* f() {",
            "  yield await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return async function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              (0, $jscomp.asyncContextReenter)(",
            "                  await (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "                  $jscomp$context),",
            "              $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testAsyncGeneratorMethod() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  async *f() {",
            "    yield await 1;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return (async function*() {",
            "      (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "      try {",
            "        (0, $jscomp.asyncContextReenter)(",
            "            yield (0, $jscomp.asyncContextExit)(",
            "                (0, $jscomp.asyncContextReenter)(",
            "                    await (0, $jscomp.asyncContextExit)(1, $jscomp$context),",
            "                    $jscomp$context),",
            "                $jscomp$context),",
            "            $jscomp$context);",
            "      } finally {",
            "        (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  @Test
  public void testAsyncGeneratorMethodSuper() {
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  async *f() {",
            "    yield await super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$context = $jscomp.asyncContextEnter();",
            "    return this.f$jscomp$m1146332801$0($jscomp$context);",
            "  }",
            "  async *f$jscomp$m1146332801$0($jscomp$context) {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(",
            "              (0, $jscomp.asyncContextReenter)(",
            "                  await (0, $jscomp.asyncContextExit)(super.f(), $jscomp$context),",
            "                  $jscomp$context),",
            "              $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testNoInstrumentAwait_await() {
    instrumentAwait = false;
    testSame(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "}"));
  }

  @Test
  public void testNoInstrumentAwait_forAwait() {
    instrumentAwait = false;
    testSame(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of gen()) {",
            "    use(x);",
            "  }",
            "}"));
  }

  @Test
  public void testNoInstrumentAwait_generator() {
    // This test is unaffected by instrumentAwait=false
    instrumentAwait = false;
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(1, $jscomp$context), $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }

  @Test
  public void testNoInstrumentAwait_asyncGenerator() {
    instrumentAwait = false;
    test(
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "async function* f() {",
            "  yield await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable('name', 42);",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$context = $jscomp.asyncContextEnter();",
            "  return async function*() {",
            "    (0, $jscomp.asyncContextReenter)(void 0, $jscomp$context);",
            "    try {",
            "      (0, $jscomp.asyncContextReenter)(",
            "          yield (0, $jscomp.asyncContextExit)(await 1, $jscomp$context),",
            "          $jscomp$context);",
            "    } finally {",
            "      (0, $jscomp.asyncContextExit)(void 0, $jscomp$context);",
            "    }",
            "  }();",
            "}"));
  }
}
