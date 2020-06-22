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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RewriteAsyncIterationTest extends CompilerTestCase {

  public RewriteAsyncIterationTest() {
    super(
        new TestExternsBuilder().addAsyncIterable().addArray().addArguments().addObject().build());
  }

  // TODO(johnplaisted): This is copy and pasted from RewriteAsyncFunctionsTest. We should have
  // a more formal AST matcher.
  /** Represents a subtree of the output from a compilation. */
  private static class CodeSubTree {
    private final Node rootNode;

    private CodeSubTree(Node rootNode) {
      this.rootNode = rootNode;
    }

    /** Returns the SubTree rooted at the first class definition found with the given name. */
    private CodeSubTree findClassDefinition(String wantedClassName) {
      Node classNode =
          findFirstNode(
              rootNode, (node) -> node.isClass() && wantedClassName.equals(NodeUtil.getName(node)));
      return new CodeSubTree(classNode);
    }

    /** Returns the first class method definition found with the given name. */
    private CodeSubTree findMethodDefinition(String wantedMethodName) {
      Node methodDefinitionNode =
          findFirstNode(
              rootNode,
              (node) -> node.isMemberFunctionDef() && wantedMethodName.equals(node.getString()));

      return new CodeSubTree(methodDefinitionNode);
    }

    /** Finds every instance of a given qualified name. */
    private ImmutableList<Node> findMatchingQNameReferences(final String wantedQName) {
      return findNodesAllowEmpty(rootNode, (node) -> node.matchesQualifiedName(wantedQName));
    }
  }

  /** Returns the first function method definition found with the given name. */
  private CodeSubTree findFunctionDefinition(String wantedMethodName) {
    Node functionDefinitionNode =
        findFirstNode(
            getLastCompiler().getJsRoot(),
            (node) ->
                node.isFunction()
                    && node.getFirstChild().isName()
                    && wantedMethodName.equals(node.getFirstChild().getString()));

    return new CodeSubTree(functionDefinitionNode);
  }

  /**
   * Returns a CodeSubTree for the first definition of the given class name in the output from the
   * last compile.
   */
  private CodeSubTree findClassDefinition(String wantedClassName) {
    return new CodeSubTree(getLastCompiler().getJsRoot()).findClassDefinition(wantedClassName);
  }

  /** Return a list of all Nodes matching the given predicate starting at the given root. */
  private static ImmutableList<Node> findNodesAllowEmpty(Node rootNode, Predicate<Node> predicate) {
    ImmutableList.Builder<Node> listBuilder = ImmutableList.builder();
    NodeUtil.visitPreOrder(
        rootNode,
        new Visitor() {
          @Override
          public void visit(Node node) {
            if (predicate.test(node)) {
              listBuilder.add(node);
            }
          }
        });
    return listBuilder.build();
  }

  /** Return a list of all Nodes matching the given predicate starting at the given root. */
  private static ImmutableList<Node> findNodesNonEmpty(Node rootNode, Predicate<Node> predicate) {
    ImmutableList<Node> results = findNodesAllowEmpty(rootNode, predicate);
    checkState(!results.isEmpty(), "no nodes found");
    return results;
  }

  /**
   * Return the shallowest and earliest of all Nodes matching the given predicate starting at the
   * given root.
   *
   * <p>Throws an exception if none found.
   */
  private static Node findFirstNode(Node rootNode, Predicate<Node> predicate) {
    ImmutableList<Node> allMatchingNodes = findNodesNonEmpty(rootNode, predicate);
    return allMatchingNodes.get(0);
  }

  private final JSType getGlobalJSType(String globalTypeName) {
    return getLastCompiler().getTypeRegistry().getGlobalType(globalTypeName);
  }

  private final ObjectType getGlobalObjectType(String globalTypeName) {
    return getGlobalJSType(globalTypeName).assertObjectType();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
  }

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
    disableCompareSyntheticCode();
    allowExternsChanges();
    ensureLibraryInjected("es6/async_generator_wrapper");
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

    CodeSubTree bazSubTree = findFunctionDefinition("baz");
    Node baz = bazSubTree.rootNode;
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertType(baz.getJSType()).toStringIsEqualTo("function(): AsyncGenerator<?,?,?>");
    assertType(wrapper.getJSType())
        .toStringIsEqualTo(
            "function(new:$jscomp.AsyncGeneratorWrapper,"
                + " Generator<($jscomp.AsyncGeneratorWrapper$ActionRecord<?>|null),?,?>):"
                + " undefined");
    assertType(newExpr.getJSType()).toStringIsEqualTo("$jscomp.AsyncGeneratorWrapper");
    assertType(innerGeneratorCall.getJSType())
        .toStringIsEqualTo("Generator<($jscomp.AsyncGeneratorWrapper$ActionRecord<?>|null),?,?>");
  }

  @Test
  public void testAwaitInAsyncGenerator() {
    test(
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "/** @return {!AsyncGenerator<undefined>} */",
            "async function* baz() { await foo() }"),
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "/** @return {!AsyncGenerator<undefined>} */",
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));

    CodeSubTree bazSubTree = findFunctionDefinition("baz");
    Node baz = bazSubTree.rootNode;
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertType(baz.getJSType()).toStringIsEqualTo("function(): AsyncGenerator<undefined,?,?>");
    assertType(wrapper.getJSType())
        .toStringIsEqualTo(
            "function(new:$jscomp.AsyncGeneratorWrapper, "
                + "Generator<($jscomp.AsyncGeneratorWrapper$ActionRecord<undefined>|null),?,?>): "
                + "undefined");
    assertType(newExpr.getJSType()).toStringIsEqualTo("$jscomp.AsyncGeneratorWrapper");
    assertType(innerGeneratorCall.getJSType())
        .toStringIsEqualTo(
            "Generator" + "<($jscomp.AsyncGeneratorWrapper$ActionRecord<undefined>|null),?,?>");

    test(
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "async function* baz() { bar = await foo() }"),
        lines(
            "let /** function(): !Promise<number> */ foo;",
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.AWAIT_VALUE, foo());",
            "  })());",
            "}"));

    Node bar = findFunctionDefinition("baz").findMatchingQNameReferences("bar").get(0);

    assertType(bar.getJSType()).toStringIsEqualTo("number");
  }

  @Test
  public void testYieldInAsyncGenerator() {
    test(
        lines(
            "/** @return {!AsyncGenerator<number>} */", //
            "async function* baz() { yield 2+2 }"),
        lines(
            "/** @return {!AsyncGenerator<number>} */",
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, 2+2);",
            "  })());",
            "}"));

    CodeSubTree bazSubTree = findFunctionDefinition("baz");
    Node baz = bazSubTree.rootNode;
    Node wrapper = bazSubTree.findMatchingQNameReferences("$jscomp.AsyncGeneratorWrapper").get(0);
    Node newExpr = wrapper.getParent();
    Node innerGeneratorCall = newExpr.getSecondChild();

    assertType(baz.getJSType()).toStringIsEqualTo("function(): AsyncGenerator<number,?,?>");
    assertType(wrapper.getJSType())
        .toStringIsEqualTo(
            "function(new:$jscomp.AsyncGeneratorWrapper, "
                + "Generator<($jscomp.AsyncGeneratorWrapper$ActionRecord<number>|null),?,?>): "
                + "undefined");
    assertType(newExpr.getJSType()).toStringIsEqualTo("$jscomp.AsyncGeneratorWrapper");
    assertType(innerGeneratorCall.getJSType())
        .toStringIsEqualTo(
            "Generator" + "<($jscomp.AsyncGeneratorWrapper$ActionRecord<number>|null),?,?>");

    test(
        lines(
            "/** @return {!AsyncGenerator<number>} */", //
            "async function* baz() { bar = yield 2+2 }"),
        lines(
            "/** @return {!AsyncGenerator<number>} */", //
            "function baz() {",
            "  return new $jscomp.AsyncGeneratorWrapper((function*() {",
            "    bar = yield new $jscomp.AsyncGeneratorWrapper$ActionRecord(",
            "      $jscomp.AsyncGeneratorWrapper$ActionEnum.YIELD_VALUE, 2+2);",
            "  })());",
            "}"));

    Node bar = findFunctionDefinition("baz").findMatchingQNameReferences("bar").get(0);

    // The generator yields numbers but yield expressions should always be "?" as next accepts "?"
    assertType(bar.getJSType()).toStringIsEqualTo("?");
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
            "async function abc() { for await (a of foo()) { bar(); } }",
            "/** @return {!AsyncGenerator<number>} */",
            "function foo() {}"),
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
            "}",
            "/** @return {!AsyncGenerator<number>} */",
            "function foo() {}"));

    Node forNode =
        findFunctionDefinition("abc")
            .rootNode
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

    assertType(tempIterator0.getJSType()).toStringIsEqualTo("AsyncIteratorIterable<number>");
    assertType(makeAsyncIteratorCall.getJSType())
        .toStringIsEqualTo("AsyncIteratorIterable<number>");
    assertType(tempResult0.getJSType()).toStringIsEqualTo("IIterableResult<number>");
    assertType(await.getJSType()).toStringIsEqualTo("IIterableResult<number>");
    assertType(nextCall.getJSType()).toStringIsEqualTo("Promise<IIterableResult<number>>");
    assertType(done.getJSType()).toStringIsEqualTo("boolean");
    assertType(value.getJSType()).toStringIsEqualTo("number");

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
