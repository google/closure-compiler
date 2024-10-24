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

  // BEFORE: async function f() { BODY }
  //  AFTER: async function f() { var swap = enter(); try { BODY } finally { swap() } }
  //
  // BEFORE: await EXPR
  //  AFTER: swap(await swap(EXPR), 1)
  //
  // Explanation:
  //   * swap(x) is "exit the function context and return x"
  //   * swap(x, 1) is "reenter the function context and return x"
  //   * This way, we compute the expression under the function context, then exit only for the
  //     await, and reenter again immediately after the await.
  //   * We also wrap the entire async body in a try-finally with an "exit" (empty swap()) so that
  //     the function context is purged at the end.
  //   * Note that "exit" is only required after a "reenter", but it's not harmful to do it if the
  //     try block were to throw before any awaits, since the initial "restored outer context" is
  //     the same as the function context - i.e. exit before reenter is a no-op.
  @Test
  public void testAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // BEFORE: for await (const x of EXPR) { BODY }
  //  AFTER: for await (const x of swap(EXPR)) { swap(0, 1); BODY; swap() } swap(0, 1)
  //
  // Explanation:
  //   * each iteration of the for-await loop is effectively an await; the top of the loop body is
  //     always the entrypoint, but there is an exit immediately after evaluating the async iterator
  //     subject, as well as at the end of the loop body.
  //   * after the loop, we reenter because it requires an await to determine the iterator is "done"
  //   * the 0 in swap(0, 1) is just a spacer - since the return value is ignored, the first
  //     parameter is irrelevant.  '0' is the smallest thing we can put in that position in order to
  //     pass a '1' as the second parameter to make this a "reenter"
  @Test
  public void testForAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of gen()) {",
            "    use(x);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    for await (const x of $jscomp$swapContext(gen())) {",
            "      $jscomp$swapContext(0, 1);",
            "      use(x);",
            "      $jscomp$swapContext();",
            "    }",
            "    $jscomp$swapContext(0, 1);",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // TODO: b/371578480 - test break and continue inside for-await

  // A block is added if necessary so that the post-loop reenter call is appropriately sequenced.
  @Test
  public void testForAwaitNotInBlock() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  if (1)",
            "    for await (const x of gen()) {",
            "      use(x);",
            "    }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    if (1) {",
            "      for await (const x of $jscomp$swapContext(gen())) {",
            "        $jscomp$swapContext(0, 1);",
            "        use(x);",
            "        $jscomp$swapContext();",
            "      }",
            "      $jscomp$swapContext(0, 1);",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // Await nodes within the for-await should also be instrumented appropriately.
  @Test
  public void testForAwaitMixedWithAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of await gen()) {",
            "    await use(x);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    for await (const x of",
            "                   $jscomp$swapContext(",
            "                       $jscomp$swapContext(await $jscomp$swapContext(gen()), 1))) {",
            "      $jscomp$swapContext(0, 1);",
            "      $jscomp$swapContext(",
            "          await $jscomp$swapContext(use(x)), 1);",
            "      $jscomp$swapContext();",
            "    }",
            "    $jscomp$swapContext(0, 1);",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // NOTE: This test is disabled because we do not yet support top-level await.  But if it becomes
  // supported, this is how we should handle it.
  @Test
  public void testTopLevelAwait() {
    if (!SUPPORT_TOP_LEVEL_AWAIT) {
      return;
    }
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "await 1;",
            "await 2;"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "$jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "$jscomp$swapContext(await $jscomp$swapContext(2), 1);"));
  }

  // Multiple awaits should each get instrumented separately, but the top-level function
  // instrumentation should only happen once.
  @Test
  public void testMultipleAwaits() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "  await 2;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    $jscomp$swapContext(await $jscomp$swapContext(2), 1);",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // BEFORE: try { ... } catch { BODY }
  //  AFTER: try { ... } catch { swap(0, 1); BODY }
  //
  // Explanation:
  //   * If the immediate body of a "try" node has an await in it, and the await throws, then the
  //     reenter call around the await will not run.  Instead, control flow returns to the function
  //     body via the "catch" or "finally" block, so we need to additionally insert the reenter call
  //     at the top of either catch or finally, whichever comes first.
  @Test
  public void testAwaitInsideTryCatch() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    await 1;",
            "  } catch (e) {",
            "    console.log(e);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    } catch (e) {",
            "      $jscomp$swapContext(0, 1);",
            "      console.log(e);",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // BEFORE: try { ... } catch { BODY1 } finally { BODY2 }
  //  AFTER: try { ... } catch { swap(0, 1); BODY1 } finally { BODY2 }
  //
  // Explanation:
  //   * If there is both a "catch" and a "finally", then only the former needs a reenter call.
  //   * The "finally" block will _only_ run if the "catch" block terminates normally, so we can
  //     guarantee that the context will have reentered before we reach it.
  @Test
  public void testAwaitInsideTryCatchFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
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
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    } catch (e) {",
            "      $jscomp$swapContext(0, 1);",
            "      console.log(e);",
            "    } finally {",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // BEFORE: try { ... } finally { BODY }
  //  AFTER: try { ... } finally { swap(0, 1); BODY }
  @Test
  public void testAwaitInsideTryFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    await 1;",
            "  } finally {",
            "    console.log('finally');",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    } finally {",
            "      $jscomp$swapContext(0, 1);",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // `return await` does not require any instrumentation if it's not inside a try block.
  @Test
  public void testReturnAwait() {
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  return await 1;",
            "}"));
  }

  // `return await` does not require any instrumentation if it's not inside a try block.
  // But if there's a catch block, then the function context may still get reentered after the
  // await, if the awaited promise is rejected.  So this should be instrumented normally.
  @Test
  public void testReturnAwaitInsideTryCatch() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    return await 1;",
            "  } catch (e) {",
            "    console.log(e);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      return $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    } catch (e) {",
            "      $jscomp$swapContext(0, 1);",
            "      console.log(e);",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // `return await` does not require any instrumentation if it's not inside a try block.
  // But if there's a finally, then the function context will still get reentered after the await.
  @Test
  public void testReturnAwaitInsideTryFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  try {",
            "    return await 1;",
            "  } finally {",
            "    console.log('finally');",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    try {",
            "      return $jscomp$swapContext(await $jscomp$swapContext(1), 1);",
            "    } finally {",
            "      $jscomp$swapContext(0, 1);",
            "      console.log('finally');",
            "    }",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }

  // `return await` does not require any instrumentation if it's the entire arrow function
  // expression.  There is no "enter" call, either, since we will never need to restore the
  // context after the await (there's no code there that would need it).
  @Test
  public void testAwaitImmediatelyReturnedFromBodylessArrow() {
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "(async () => await 1)();"));
  }

  // A shorthand arrow with a nested await needs instrumentation, since there's ECMAScript code that
  // executes after the await.  Promote the shorthand arrow into a full block, with a proper return
  // statement, etc.
  @Test
  public void testAwaitInsideBodylessArrow() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "(async () => use(await 1))();"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "(async () => {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    return use($jscomp$swapContext(await $jscomp$swapContext(1), 1));",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "})();"));
  }

  // Instrumentation is tied to `await`, not `async`.  If there is no await, then there's no reentry
  // and we don't need to save the entry context to restore later.  No instrumentation is required.
  @Test
  public void testAsyncFunctionWithNoAwait() {
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  return 1;",
            "}"));
  }

  // BEFORE: function* f() { BODY }
  //  AFTER: function f() {
  //           var swap = enter(1);
  //           return function*() { swap(0, 1); try { BODY } finally { swap() } }()
  //         }
  //
  // BEFORE: yield EXPR
  //  AFTER: swap(yield swap(EXPR), 1)
  //
  // Explanation:
  //   * in order to snapshot the context from the initial call, we need to wrap the generator into
  //     an ordinary function to call enter() outside the generator, and then we use an immediately
  //     invoked generator to run the body, which now needs an explit swap(0, 1) to reenter at the
  //     front, since it may happen at some later time in a different context.
  //   * a generator's "function context" is snapshotted when the generator is first called, but no
  //     ECMAScript code runs at that time - the generator body doesn't begin until the first call
  //     to iter.next(): enter(1) indicates that the context should start as "exited".
  @Test
  public void testGenerator() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // yield has the same behavior as await w.r.t. try blocks
  @Test
  public void testGeneratorWithTryCatchFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
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
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      try {",
            "        $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "      } catch (e) {",
            "        $jscomp$swapContext(0, 1);",
            "        $jscomp$swapContext(yield $jscomp$swapContext(2), 1);",
            "      } finally {",
            "        $jscomp$swapContext(yield $jscomp$swapContext(3), 1);",
            "      }",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // yield has the same behavior as await w.r.t. try blocks
  @Test
  public void testGeneratorWithTryFinally() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f() {",
            "  try {",
            "    yield 1;",
            "  } finally {",
            "    yield 3;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      try {",
            "        $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "      } finally {",
            "        $jscomp$swapContext(0, 1);",
            "        $jscomp$swapContext(yield $jscomp$swapContext(3), 1);",
            "      }",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // yield has the same behavior as await w.r.t. try blocks: only the inner try-catch gets
  // instrumented with a re-enter.
  @Test
  public void testGeneratorWithNestedTry() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
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
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      try {",
            "        try {",
            "          $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "        } catch (e) {",
            "          $jscomp$swapContext(0, 1);",
            "          console.log(e);",
            "        }",
            "      } catch (e) {",
            "        console.log(e);",
            "      }",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Empty yield is a special case that wasn't present with await (you can't have an empty await).
  // In this case, the exit call (`swap()`) has an implicit "undefined" for the first argument,
  // which evaluates to `yield undefined`, which is equivalent to just `yield`.
  @Test
  public void testGeneratorWithEmptyYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Yield-less generators still need instrumentation, unlike await-less async functions, since the
  // generator body may run at a later time and needs to have the initial context restored.
  @Test
  public void testGeneratorWithNoYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f() {",
            "  console.log(1);",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      console.log(1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // No instrumentation is needed because there's no code inside to observe any variables.
  @Test
  public void testGeneratorWithEmptyBody() {
    testSame(
        lines(
            "const v = new AsyncContext.Variable();", "v.run(100, () => {});", "function* f() {}"));
  }

  // Because of the nested generator (which cannot be an arrow because generator arrow don't exist),
  // we need to indirect access to "arguments" and/or "this".
  @Test
  public void testGeneratorWithArguments() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f(a) {",
            "  yield a + arguments[1];",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f(a) {",
            "  const $jscomp$arguments$m1146332801$0 = arguments;",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(",
            "          yield $jscomp$swapContext(a + $jscomp$arguments$m1146332801$0[1]), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Generator methods are transpiled the same as generator functions, as long as there is no
  // reference to "super".
  @Test
  public void testGeneratorMethod() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    yield 1;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return (function*() {",
            "      $jscomp$swapContext(0, 1);",
            "      try {",
            "        $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "      } finally {",
            "        $jscomp$swapContext();",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  // Generator methods are transpiled the same as generator functions, as long as there is no
  // reference to "super".
  @Test
  public void testGeneratorMethodWithNoYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    console.log(42);",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return (function*() {",
            "      $jscomp$swapContext(0, 1);",
            "      try {",
            "        console.log(42);",
            "      } finally {",
            "        $jscomp$swapContext();",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  // Generator methods are transpiled the same as generator functions, as long as there is no
  // reference to "super".  In this case, the "this" needs to be stored, since the "function*"
  // would clobber it.
  //
  // TODO(sdh): we may be able to use .apply(this, arguments) instead, though this could possibly
  // impact performance negatively.
  @Test
  public void testGeneratorMethodWithThis() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *g() {",
            "    yield this;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  g() {",
            "    const $jscomp$this$m1146332801$0 = this;",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return (function*() {",
            "      $jscomp$swapContext(0, 1);",
            "      try {",
            "        $jscomp$swapContext(",
            "            yield $jscomp$swapContext($jscomp$this$m1146332801$0), 1);",
            "      } finally {",
            "        $jscomp$swapContext();",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  // Generator methods are transpiled the same as generator functions, as long as there is no
  // reference to "super".
  @Test
  public void testGeneratorMethodWithArguments() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *g() {",
            "    yield arguments.length;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  g() {",
            "    const $jscomp$arguments$m1146332801$0 = arguments;",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return (function*() {",
            "      $jscomp$swapContext(0, 1);",
            "      try {",
            "        $jscomp$swapContext(",
            "            yield $jscomp$swapContext($jscomp$arguments$m1146332801$0.length), 1);",
            "      } finally {",
            "        $jscomp$swapContext();",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  // Generator methods that reference "super" need special handling because there's no way to pass
  // the super through the immediately invoked generator wrapped inside.  Instead, we define an
  // additional separate method, to which we pass the context, as well as any arguments.  The new
  // method should match the original in terms of static-vs-instance.
  @Test
  public void testGeneratorMethodSuperStatic() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  /** @nocollapse */",
            "  static *f() {",
            "    yield* super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  /** @nocollapse */",
            "  static f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext);",
            "  }",
            "  /** @nocollapse */",
            "  static *f$jscomp$m1146332801$0($jscomp$swapContext) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield* $jscomp$swapContext(super.f()), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Generator methods that reference "super" need special handling because there's no way to pass
  // the super through the immediately invoked generator wrapped inside.  Instead, we define an
  // additional separate method, to which we pass the context, as well as any arguments.  The new
  // method should match the original in terms of static-vs-instance.
  @Test
  public void testGeneratorMethodSuper() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    yield super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.f()), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // This is the same as the case with yield.
  @Test
  public void testGeneratorMethodSuperWithNoYield() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f() {",
            "    return super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      return super.f();",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Parameters are copied over and forwarded to the new method.
  @Test
  public void testGeneratorMethodSuperWithParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a, b, c) {",
            "    yield super.x;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a, b, c) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext, a, b, c);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, a, b, c) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.x), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Because of the additional context parameter, we can't just read arguments directly in the inner
  // generator.  Instead, we pass the arguments array explicitly as its own parameter, if it's
  // accessed from within the generator body.
  @Test
  public void testGeneratorMethodSuperReadsArgumentsArray() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a) {",
            "    yield super.x + arguments.length;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext, arguments, a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, $jscomp$arguments$m1146332801$1, a) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(",
            "          yield $jscomp$swapContext(",
            "              super.x + $jscomp$arguments$m1146332801$1.length),",
            "          1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Default parameter initializers are retained on the inner method, rather than the outer method.
  // This is required so that they will be correctly excluded from arguments arrays (i.e. if you
  // write `function f(x = 1) { return arguments[0]; }` then you can distinguish a call of `f()`
  // from a call of `f(1)` since the former returns `undefined` (and the array has length 0).
  @Test
  public void testGeneratorMethodSuperWithDefaultParameter() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a = 1) {",
            "    yield super.y;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext, a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, a = 1) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.y), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Destructurd parameters are also retained on the inner method, for similar reasons to defaults.
  @Test
  public void testGeneratorMethodSuperWithDestructuredParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f({a}, [b = 1]) {",
            "    yield super.x;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f($jscomp$m1146332801$1, $jscomp$m1146332801$2) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0(",
            "        $jscomp$swapContext, ",
            "        $jscomp$m1146332801$1, ",
            "        $jscomp$m1146332801$2);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, {a}, [b = 1]) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.x), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Rest parameters are copied on both the original generator-turned-ordinary-method as well as on
  // the new inner generator, since they're important for passing along all the arguments.
  @Test
  public void testGeneratorMethodSuperWithRestParameter() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(...a) {",
            "    yield super.foo;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(...a) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext, ...a);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, ...a) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.foo), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // This case just combines all the earlier cases into one.
  @Test
  public void testGeneratorMethodSuperWithComplicatedParameters() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *f(a, {b}, c = 1, ...[d]) {",
            "    yield super.foo;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f(a, $jscomp$m1146332801$1, c, ...$jscomp$m1146332801$2) {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0(",
            "        $jscomp$swapContext, a, $jscomp$m1146332801$1, c, ...$jscomp$m1146332801$2);",
            "  }",
            "  *f$jscomp$m1146332801$0($jscomp$swapContext, a, {b}, c = 1, ...[d]) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.foo), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // When the generator method has a computed key, we can't include the name in the new inner
  // generator.
  @Test
  public void testGeneratorMethodSuperWithComputedKey() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  *[Symbol.iterator]() {",
            "    yield super.foo();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  [Symbol.iterator]() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.$jscomp$m1146332801$0($jscomp$swapContext);",
            "  }",
            "  *$jscomp$m1146332801$0($jscomp$swapContext) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(super.foo()), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Async generators combine both async and generator transpilations into one.
  @Test
  public void testAsyncGenerator() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function* f() {",
            "  yield await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return async function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(",
            "          yield $jscomp$swapContext(",
            "              $jscomp$swapContext(await $jscomp$swapContext(1), 1)),",
            "          1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // The generator transpilation is still required, even when there's no yields or awaits.
  @Test
  public void testAsyncGeneratorWithNoYieldNorAwait() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function* f() {",
            "  return 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return async function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      return 1;",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Async generator methods are transpiled the same as functions as long as there is no super.
  @Test
  public void testAsyncGeneratorMethod() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  async *f() {",
            "    yield await 1;",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return (async function*() {",
            "      $jscomp$swapContext(0, 1);",
            "      try {",
            "        $jscomp$swapContext(",
            "            yield $jscomp$swapContext(",
            "                $jscomp$swapContext(await $jscomp$swapContext(1), 1)),",
            "            1);",
            "      } finally {",
            "        $jscomp$swapContext();",
            "      }",
            "    })();",
            "  }",
            "}"));
  }

  // Async generator methods get the same treatment as generator methods if they access super.
  @Test
  public void testAsyncGeneratorMethodSuper() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  async *f() {",
            "    yield await super.f();",
            "  }",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "class Foo {",
            "  f() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "    return this.f$jscomp$m1146332801$0($jscomp$swapContext);",
            "  }",
            "  async *f$jscomp$m1146332801$0($jscomp$swapContext) {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(",
            "          yield $jscomp$swapContext(",
            "              $jscomp$swapContext(",
            "                  await $jscomp$swapContext(super.f()), 1)),",
            "          1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}"));
  }

  // Test that we don't do anything with async functions when instrumentAwait is false.
  // This option is used when the output level is less than ES2017.  In that case, async functions
  // transpile down to generators with ordinary Promise objects, and the AsyncContext.Variable
  // runtime polyfill will already make the necessary monkey-patches to Promise so that these get
  // instrumented for free.
  @Test
  public void testNoInstrumentAwait_await() {
    instrumentAwait = false;
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  await 1;",
            "}"));
  }

  // No "await" instrumentation.
  @Test
  public void testNoInstrumentAwait_forAwait() {
    instrumentAwait = false;
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  for await (const x of gen()) {",
            "    use(x);",
            "  }",
            "}"));
  }

  // This test is unaffected by instrumentAwait=false because generators still need to be
  // instrumented even when async functions are rewritten.  Generators are rewritten for all
  // language-out levels, including ES5 which transpiles the generator away.
  @Test
  public void testNoInstrumentAwait_generator() {
    instrumentAwait = false;
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function* f() {",
            "  yield 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(1), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Test that only the yield is instrumented, but that awaits are left alone.
  @Test
  public void testNoInstrumentAwait_asyncGenerator() {
    instrumentAwait = false;
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function* f() {",
            "  yield await 1;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter(1);",
            "  return async function*() {",
            "    $jscomp$swapContext(0, 1);",
            "    try {",
            "      $jscomp$swapContext(yield $jscomp$swapContext(await 1), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }();",
            "}"));
  }

  // Test that if the input is already instrumented, we don't double-instrument.
  @Test
  public void testAlreadyInstrumented() {
    testSame(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  $jscomp$swapContext(await $jscomp$swapContext(42), 1);",
            "}"));
  }

  // Already-instrumented check applies on the function level: `f` is skipped, but
  // `g` and `h` get instrumentation.
  @Test
  public void testAlreadyInstrumented_partial() {
    test(
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  $jscomp$swapContext(await $jscomp$swapContext(2), 1);",
            "  async function g() {",
            "    await 3;",
            "  }",
            "}",
            "async function h() {",
            "  await 4;",
            "}"),
        lines(
            "const v = new AsyncContext.Variable();",
            "v.run(100, () => {});",
            "async function f() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  $jscomp$swapContext(await $jscomp$swapContext(2), 1);",
            "  async function g() {",
            "    var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "    try {",
            "      $jscomp$swapContext(await $jscomp$swapContext(3), 1);",
            "    } finally {",
            "      $jscomp$swapContext();",
            "    }",
            "  }",
            "}",
            "async function h() {",
            "  var $jscomp$swapContext = $jscomp.asyncContextEnter();",
            "  try {",
            "    $jscomp$swapContext(await $jscomp$swapContext(4), 1);",
            "  } finally {",
            "    $jscomp$swapContext();",
            "  }",
            "}"));
  }
}
