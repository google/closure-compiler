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
import static com.google.javascript.jscomp.testing.CodeSubTree.findClassDefinition;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.CodeSubTree;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RewriteAsyncFunctionsTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      new TestExternsBuilder().addArguments().addJSCompLibraries().build();

  public RewriteAsyncFunctionsTest() {
    super(EXTERNS_BASE);
  }

  @Before
  public void customSetUp() throws Exception {
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableNormalize();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return RewriteAsyncFunctions.create(compiler);
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

  private final Color getGlobalColor(ColorId colorId) {
    return getLastCompiler().getColorRegistry().get(colorId);
  }

  private final Color getGlobalInstanceColor(String globalClassName) {
    return Color.createUnion(
        findClassDefinition(getLastCompiler(), globalClassName)
            .getRootNode()
            .getColor()
            .getInstanceColors());
  }

  @Test
  public void testDefaultParameterUsingThis() {
    test(
        lines(
            "class X {",
            "  /**",
            "   * @param {number} a",
            "   */",
            "  constructor(a) {",
            "    /** @const */ this.a = a;",
            "  }",
            "  /**",
            "   * @param {number} b",
            "   * @return {!Promise<number>}",
            "   */",
            "  async m(b = this.a) {",
            "      return this.a + b;",
            "  }",
            "}"),
        lines(
            "class X {",
            "  constructor(a) {",
            "    /** @const */ this.a = a;",
            "  }",
            "  m(b = this.a) {", // this in parameter default value doesn't get changed
            "    const $jscomp$async$this = this;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "            return $jscomp$async$this.a + b;",
            "        });",
            "  }",
            "}"));

    Color classXInstanceType = getGlobalInstanceColor("X");

    ImmutableList<Node> thisAliasNameReferences =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$this");
    assertThat(thisAliasNameReferences).hasSize(2);

    // const $jscomp$async$this = this;
    // confirm that `this` and `$jscomp$async$this` nodes have the right types in declaration
    Node aliasDeclarationReference = thisAliasNameReferences.get(0);
    assertNode(aliasDeclarationReference).hasColorThat().isEqualTo(classXInstanceType);
    Node thisNode = aliasDeclarationReference.getOnlyChild();
    assertNode(thisNode).isThis().hasColorThat().isEqualTo(classXInstanceType);

    // make sure the single reference to $jscomp$async$this has the right type
    assertNode(thisAliasNameReferences.get(1)).hasColorThat().isEqualTo(classXInstanceType);
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

    Color classXInstanceType = getGlobalInstanceColor("X");

    ImmutableList<Node> thisAliasNameReferences =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$this");
    assertThat(thisAliasNameReferences).hasSize(2);

    // const $jscomp$async$this = this;
    // confirm that `this` and `$jscomp$async$this` nodes have the right types in declaration
    Node aliasDeclarationReference = thisAliasNameReferences.get(0);
    assertNode(aliasDeclarationReference).hasColorThat().isEqualTo(classXInstanceType);
    Node thisNode = aliasDeclarationReference.getOnlyChild();
    assertNode(thisNode).isThis().hasColorThat().isEqualTo(classXInstanceType);

    // make sure the single reference to $jscomp$async$this has the right type
    assertNode(thisAliasNameReferences.get(1)).hasColorThat().isEqualTo(classXInstanceType);
  }

  @Test
  public void testInnerSuperCall() {
    test(
        externs(new TestExternsBuilder().addPromise().addJSCompLibraries().build()),
        srcs(
            lines(
                "class A {",
                "  m() {",
                "    return Promise.resolve(this);",
                "  }",
                "}",
                "class X extends A {",
                "  async m() {",
                "    return super.m();",
                "  }",
                "}")),
        expected(
            lines(
                "class A {",
                "  m() {",
                "    return Promise.resolve(this);",
                "  }",
                "}",
                "class X extends A {",
                "  m() {",
                "    const $jscomp$async$this = this;",
                "    const $jscomp$async$super$get$m = () => {",
                "      return super.m;",
                "    };",
                "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
                "        function* () {",
                "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
                "        });",
                "  }",
                "}")));

    Color classAInstanceType = getGlobalInstanceColor("A");
    // type of A.prototype.m
    Color classAPropertyMType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("m")
            .getRootNode()
            .getColor();

    CodeSubTree classXMethodMDefinition =
        findClassDefinition(getLastCompiler(), "X").findMethodDefinition("m");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodMDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    // optimization colors don't track function signatures
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasColorThat()
        .isEqualTo(StandardColors.TOP_OBJECT);
    // wrapper function variable has type matching the function itself
    Color wrapperArrowColor = wrapperArrowFunction.getColor();
    assertNode(wrapperDeclarationNameNode).hasColorThat().isEqualTo(wrapperArrowColor);

    // get `super.m` from `() => `super.m`
    Node superDotM = wrapperArrowFunction.getLastChild().getFirstFirstChild();
    assertNode(superDotM)
        .matchesQualifiedName("super.m")
        .hasColorThat()
        .isEqualTo(classAPropertyMType);
    Node superNode = superDotM.getFirstChild();
    assertNode(superNode).isSuper().hasColorThat().isEqualTo(classAInstanceType);

    // second name node is reference
    // return $jscomp$async$super$get$m().call($jscomp$async$this);
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // optimization colors don't track function signatures
    assertNode(wrapperArrowFunction).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    // `$jscomp$async$super$get$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasColorThat().isEqualTo(classAPropertyMType);

    // `$jscomp$async$super$get$m().call($jscomp$async$this)`
    Node methodCallNode = wrapperCallNode.getGrandparent();
    // optimization colors don't track .call types
    assertNode(methodCallNode).isCall().hasColorThat().isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void testInnerSuperReference() {
    test(
        externs(new TestExternsBuilder().addFunction().addJSCompLibraries().build()),
        srcs(
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
                "}")),
        expected(
            lines(
                "class A {",
                "  m() {",
                "    return this;",
                "  }",
                "}",
                "class X extends A {",
                "  m() {",
                "    const $jscomp$async$super$get$m = () => {",
                "      return super.m;",
                "    };",
                "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
                "        function* () {",
                "          const tmp = $jscomp$async$super$get$m();",
                // type of tmp will indicate it requires `this` be provided, but will allow null.
                "          return tmp.call(null);",
                "        });",
                "  }",
                "}")));

    // type of A.prototype.m
    Color classAPropertyMType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("m")
            .getRootNode()
            .getColor();
    Color classAInstanceType = getGlobalInstanceColor("A");

    CodeSubTree classXMethodMDefinition =
        findClassDefinition(getLastCompiler(), "X").findMethodDefinition("m");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodMDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    // arrow function has a Color representing a object
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasColorThat()
        .isEqualTo(StandardColors.TOP_OBJECT);
    // wrapper function variable has type matching the function itself
    Color wrapperArrowColor = wrapperArrowFunction.getColor();
    assertNode(wrapperDeclarationNameNode).hasColorThat().isEqualTo(wrapperArrowColor);

    // get `super.m` from `() => `super.m`
    Node superDotM = wrapperArrowFunction.getLastChild().getFirstFirstChild();
    assertNode(superDotM)
        .matchesQualifiedName("super.m")
        .hasColorThat()
        .isEqualTo(classAPropertyMType);
    Node superNode = superDotM.getFirstChild();
    assertNode(superNode).hasColorThat().isEqualTo(classAInstanceType);

    // second name node is reference
    // const tmp = $jscomp$async$super$get$m();
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // optimization colors don't track function signatures
    assertNode(wrapperReferenceNameNode).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    // `$jscomp$async$super$get$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasColorThat().isEqualTo(classAPropertyMType);
  }

  @Test
  public void testMultipleSuperAccessesInAsyncFunction_havingNonIdenticalUnknownTypes() {
    test(
        lines(
            "class UpdatingElement {",
            "  getUpdateComplete() {",
            "  }",
            "}",
            "",
            "class TextFieldBase extends UpdatingElement {",
            "  async _getUpdateComplete() {",
            "    if (super.getUpdateComplete) {", // `?` type
            "      await super.getUpdateComplete();", // `??` type
            "    }",
            "  }",
            "}"),
        lines(
            "class UpdatingElement {",
            "  getUpdateComplete() {",
            "  }",
            "}",
            "class TextFieldBase extends UpdatingElement {",
            "  _getUpdateComplete() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$super$get$getUpdateComplete = () => {",
            "      return super.getUpdateComplete;",
            "    };",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(function*() {",
            "      if ($jscomp$async$super$get$getUpdateComplete()) {",
            "        yield $jscomp$async$super$get$getUpdateComplete().call($jscomp$async$this);",
            "      }",
            "    });",
            "  }",
            "}"));
  }

  @Test
  public void testNestedArrowFunctionUsingThis() {
    test(
        lines(
            "class X {", //
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
            "            return () => {",
            "              return $jscomp$async$this;",
            "            };",
            "          })",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testInnerArrowFunctionUsingArguments() {
    test(
        externs(new TestExternsBuilder().addArguments().addJSCompLibraries().build()),
        srcs(
            lines(
                "class X {",
                "  async m() {",
                "    return new Promise((resolve, reject) => {",
                "      return arguments;",
                "    });",
                "  }",
                "}")),
        expected(
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
                "}")));

    ImmutableList<Node> argumentsAliasRefs =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$arguments");
    assertThat(argumentsAliasRefs).hasSize(2); // one declaration and 1 use

    Color argumentsColor = getGlobalColor(StandardColors.ARGUMENTS_ID);

    // declaration reference
    // const $jscomp$async$arguments = arguments;
    Node argumentsAliasDeclaration = argumentsAliasRefs.get(0);
    Node argumentsValue = argumentsAliasDeclaration.getOnlyChild();
    assertNode(argumentsValue)
        .matchesQualifiedName("arguments")
        .hasColorThat()
        .isEqualTo(argumentsColor);
    assertNode(argumentsAliasDeclaration)
        .matchesQualifiedName("$jscomp$async$arguments")
        .hasColorThat()
        .isEqualTo(argumentsColor);

    // usage reference
    // return $jscomp$async$arguments;
    Node argumentsAliasUsage = argumentsAliasRefs.get(1);
    assertNode(argumentsAliasUsage)
        .matchesQualifiedName("$jscomp$async$arguments")
        .hasColorThat()
        .isEqualTo(argumentsColor);
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
            "  /**",
            "   * @param {...?} varArgs",
            "   * @return {!Promise<number>}",
            "   */",
            "  async function f(varArgs) { return arguments.length; }",
            "  return f(arguments)",
            "}"),
        lines(
            "function outer() {",
            "  function f(varArgs) {",
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
        externs(new TestExternsBuilder().addFunction().addJSCompLibraries().build()),
        srcs(
            lines(
                "async function a() {",
                "  function inner() {",
                "    return arguments.length;",
                "  }",
                "  return inner.apply(undefined, arguments);", // this should get replaced
                "}")),
        expected(
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
                "}")));
  }

  @Test
  public void testClassMethod() {
    test(
        lines(
            "class A {",
            "  /**",
            "   * @param {number} x",
            "   */",
            "  constructor(x) {",
            "    /** @type {number} */ this.x = x;",
            "  }",
            "  async f() {",
            "    return this.x;",
            "  }",
            "}"),
        lines(
            "class A {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
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
        externs(new TestExternsBuilder().addConsole().addJSCompLibraries().build()),
        srcs(
            lines(
                "class A {",
                "  async f() {",
                "    let g = async () => { console.log(this, arguments); };",
                "    g();",
                "  }",
                "}")),
        expected(
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
                "}")));
  }

  @Test
  public void testNonAsyncClassMethodWithAsyncArrow() {
    test(
        externs(new TestExternsBuilder().addConsole().addJSCompLibraries().build()),
        srcs(
            lines(
                "class A {",
                "  f() {",
                "    let g = async () => { console.log(this, arguments); };",
                "    g();",
                "  }",
                "}")),
        expected(
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
                "}")));
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

  @Test
  public void testGlobalScopeArrowFunctionRefersToThis() {
    test(
        "let f = async () => this;",
        lines(
            "let f = () => {",
            "    const $jscomp$async$this = this;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$this;",
            "        });",
            "}"));
  }

  @Test
  public void testGlobalScopeAsyncArrowFunctionDefaultParamValueRefersToThis() {
    test(
        "let f = async (t = this) => t;",
        lines(
            "let f = (t = this) => {",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return t;",
            "        });",
            "}"));
  }

  @Test
  public void testNestedAsyncArrowFunctionDefaultParamValueRefersToThis() {
    test(
        lines("let f = async function(outerT = this) {", "  return async (t = this) => t;", "};"),
        lines(
            // `this` is not aliased here
            "let f = function(outerT = this) {",
            "  const $jscomp$async$this = this;",
            "  return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "      function* () {",
            // `this` is aliased here
            "        return (t = $jscomp$async$this) => {",
            "          return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "              function* () {",
            "                return t;",
            "              });",
            "        };",
            "      });",
            "};",
            ""));
  }
}
