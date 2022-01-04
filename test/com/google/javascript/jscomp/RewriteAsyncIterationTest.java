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

import static com.google.javascript.jscomp.testing.CodeSubTree.findFirstNode;
import static com.google.javascript.jscomp.testing.CodeSubTree.findFunctionDefinition;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.CodeSubTree;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RewriteAsyncIterationTest extends CompilerTestCase {

  public RewriteAsyncIterationTest() {
    super(
        new TestExternsBuilder()
            .addAsyncIterable()
            .addArray()
            .addMath()
            .addArguments()
            .addObject()
            .addJSCompLibraries()
            .build());
  }

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
    allowExternsChanges();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return RewriteAsyncIteration.create(compiler);
  }

  private final Color getGlobalColor(ColorId colorId) {
    return getLastCompiler().getColorRegistry().get(colorId);
  }

  private Color getJSCompAsyncGeneratorWrapperClassColor() {
    return findFirstNode(
            getLastCompiler().getExternsRoot(),
            (n) -> n.matchesQualifiedName("$jscomp.AsyncGeneratorWrapper"))
        .getNext()
        .getColor();
  }

  private Color getJSCompAsyncGeneratorWrapperInstanceColor() {
    return Color.createUnion(getJSCompAsyncGeneratorWrapperClassColor().getInstanceColors());
  }

  @Test
  public void testBug173319540() {
    test(
        srcs(
            lines(
                "", //
                "let key, value;",
                "window.onload = async function() {",
                "  for await ([key,value] of window[\"unknownAsyncIterable\"]) {",
                "    alert(key,value);",
                "  }",
                "}",
                "")),
        expected(
            lines(
                "", //
                "let key, value;",
                "window.onload = async function() {",
                "  for (const $jscomp$forAwait$tempIterator0 =",
                "      $jscomp.makeAsyncIterator(window[\"unknownAsyncIterable\"]);;) {",
                "        const $jscomp$forAwait$tempResult0 =",
                "            await $jscomp$forAwait$tempIterator0.next();",
                "    if ($jscomp$forAwait$tempResult0.done) {",
                "      break;",
                "    }",
                "    [key, value] = $jscomp$forAwait$tempResult0.value;",
                "    {",
                "      alert(key, value);",
                "    }",
                "  }",
                "};",
                "")));

    test(
        srcs(
            lines(
                "", //
                "window.onload = async function() {",
                "  for await (const [key,value] of window[\"unknownAsyncIterable\"]) {",
                "    alert(key,value);",
                "  }",
                "}",
                "")),
        expected(
            lines(
                "", //
                "window.onload = async function() {",
                "  for (const $jscomp$forAwait$tempIterator0 =",
                "      $jscomp.makeAsyncIterator(window[\"unknownAsyncIterable\"]);;) {",
                "        const $jscomp$forAwait$tempResult0 =",
                "            await $jscomp$forAwait$tempIterator0.next();",
                "    if ($jscomp$forAwait$tempResult0.done) {",
                "      break;",
                "    }",
                "    const [key, value] = $jscomp$forAwait$tempResult0.value;",
                "    {",
                "      alert(key, value);",
                "    }",
                "  }",
                "};",
                "")));
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

    CodeSubTree bazSubTree = findFunctionDefinition(getLastCompiler(), "baz");
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertNode(wrapper).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperClassColor());
    assertNode(newExpr).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperInstanceColor());
    assertNode(innerGeneratorCall)
        .hasColorThat()
        .isEqualTo(getGlobalColor(StandardColors.GENERATOR_ID));
  }

  @Test
  public void testAwaitInAsyncGenerator() {
    test(
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "/** @return {!AsyncGenerator<undefined>} */",
            "async function* baz() { await foo() }"),
        lines(
            "let foo;",
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));

    CodeSubTree bazSubTree = findFunctionDefinition(getLastCompiler(), "baz");
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertNode(wrapper).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperClassColor());
    assertNode(newExpr).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperInstanceColor());
    assertNode(innerGeneratorCall)
        .hasColorThat()
        .isEqualTo(getGlobalColor(StandardColors.GENERATOR_ID));

    test(
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "async function* baz() { bar = await foo() }"),
        lines(
            "let foo;",
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));

    Node bar =
        findFunctionDefinition(getLastCompiler(), "baz").findMatchingQNameReferences("bar").get(0);

    assertNode(bar).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testYieldInAsyncGenerator() {
    test(
        lines(
            "/** @return {!AsyncGenerator<number>} */", //
            "async function* baz() { yield 2+2 }"),
        lines(
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, 2+2);",
            "  })());",
            "}"));

    CodeSubTree bazSubTree = findFunctionDefinition(getLastCompiler(), "baz");
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertNode(wrapper).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperClassColor());
    assertNode(newExpr).hasColorThat().isEqualTo(getJSCompAsyncGeneratorWrapperInstanceColor());
    assertNode(innerGeneratorCall)
        .hasColorThat()
        .isEqualTo(getGlobalColor(StandardColors.GENERATOR_ID));

    test(
        lines(
            "/** @return {!AsyncGenerator<number>} */", //
            "async function* baz() { bar = yield 2+2 }"),
        lines(
            "function baz() {", //
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, 2+2);",
            "  })());",
            "}"));

    Node bar =
        findFunctionDefinition(getLastCompiler(), "baz").findMatchingQNameReferences("bar").get(0);

    // The generator yields numbers but yield expressions should always be "?" as next accepts "?"
    assertNode(bar).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
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
        lines(
            "async function* baz(outerT = this) {",
            "  return async function*(innerT = this) {",
            "    yield innerT || this;",
            "  }",
            "}"),
        lines(
            // `this` in parameter list shouldn't be aliased
            "function baz(outerT = this) {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            // `this` in parameter list shouldn't be aliased
            "    return function(innerT = this) {",
            "      const $jscomp$asyncIter$this = this;",
            "      return new $jscomp.AsyncGeneratorWrapper((function*() {",
            // `this` in body should be aliased
            "        yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "          $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE,",
            "          innerT || $jscomp$asyncIter$this);",
            "      })());",
            "    };",
            "  })());",
            "}"));
  }

  @Test
  public void testThisInArrowNestedInAsyncGenerator() {
    test(
        lines(
            "async function* baz() {",
            // both instances of `this` musts be changed to aliases
            "  return (t = this) => t || this;",
            "}"),
        lines(
            "function baz() {",
            "  const $jscomp$asyncIter$this = this;",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    return (t = $jscomp$asyncIter$this) =>",
            "        t || $jscomp$asyncIter$this;",
            "      })());",
            "}",
            ""));
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
            "        () => super.m;",
            "    return new $jscomp.AsyncGeneratorWrapper(",
            "        function* () {",
            "          const tmp = $jscomp$asyncIter$super$get$m();",
            "          return tmp.call(null);",
            "        }());",
            "  }",
            "}"));
  }

  @Test
  public void testInnerSuperCallInAsyncGenerator() {
    test(
        lines(
            "class A {",
            "  m() {",
            "    return this;",
            "  }",
            "}",
            "class X extends A {",
            "  async *m() {",
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
            "    const $jscomp$asyncIter$this = this;",
            "    const $jscomp$asyncIter$super$get$m =",
            "        () => super.m;",
            "    return new $jscomp.AsyncGeneratorWrapper(",
            "        function* () {",
            "          return $jscomp$asyncIter$super$get$m().call($jscomp$asyncIter$this);",
            "        }());",
            "  }",
            "}"));
  }

  @Test
  public void testCannotConvertSuperGetElemInAsyncGenerator() {
    // The rewriting gets partially done before we notice and report that we cannot convert
    // the code. The partially done code is invalid, so we must disable AST validation to see the
    // error message. (AST validation is not enabled in normal execution, just developer mode.)
    disableAstValidation();
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
        lines(
            "/** @type {number|undefined} */",
            "let a;",
            "async function abc() { for await (a of foo()) { bar(); } }",
            "/** @return {!AsyncGenerator<number>} */",
            "function foo() {}"),
        lines(
            "let a;",
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
            "}",
            "function foo() {}"));

    Node forNode =
        findFunctionDefinition(getLastCompiler(), "abc")
            .getRootNode()
            .getLastChild() // block
            .getFirstChild(); // for
    Node tempIterator0 = forNode.getFirstFirstChild();
    Node makeAsyncIteratorCall = tempIterator0.getFirstChild();
    Node block = forNode.getLastChild();
    Node tempResult0 = block.getFirstFirstChild();
    Node await = tempResult0.getFirstChild();
    Node nextCall = await.getFirstChild();
    Node done =
        block
            .getSecondChild() // if
            .getFirstChild();
    Node value =
        block
            .getChildAtIndex(2) // exprResult
            .getFirstChild() // assign
            .getLastChild(); // getprop

    assertNode(tempIterator0)
        .hasColorThat()
        .isEqualTo(getGlobalColor(StandardColors.ASYNC_ITERATOR_ITERABLE_ID));
    assertNode(makeAsyncIteratorCall)
        .hasColorThat()
        .isEqualTo(getGlobalColor(StandardColors.ASYNC_ITERATOR_ITERABLE_ID));
    assertNode(tempResult0).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT); // IIterableResult
    assertNode(await).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT); // IIterableResult
    assertNode(nextCall).hasColorThat().isEqualTo(getGlobalColor(StandardColors.PROMISE_ID));
    assertNode(done).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
    assertNode(value).hasColorThat().isEqualTo(StandardColors.NUMBER);

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
}
