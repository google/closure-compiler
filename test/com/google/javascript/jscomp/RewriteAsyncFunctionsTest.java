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
import com.google.common.collect.ImmutableMap;
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

  private static final ImmutableMap<String, String> REPLACEMENTS_MAP =
      ImmutableMap.of(
          "ASYNC_THIS",
          "$jscomp$async$this$",
          "ASYNC_ARGUMENTS",
          "$jscomp$async$arguments$",
          "ASYNC_SUPER_GET",
          "$jscomp$async$super$get$");

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
    testAsyncRewriting(
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
            "    const ASYNC_THIS$3 = this;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "            return ASYNC_THIS$3.a + b;",
            "        });",
            "  }",
            "}"));

    Color classXInstanceType = getGlobalInstanceColor("X");

    ImmutableList<Node> thisAliasNameReferences =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$this$m1146332801$3");
    assertThat(thisAliasNameReferences).hasSize(2);

    // const ASYNC_THIS$3 = this;
    // confirm that `this` and `ASYNC_THIS$3` nodes have the right types in
    // declaration
    Node aliasDeclarationReference = thisAliasNameReferences.get(0);
    assertNode(aliasDeclarationReference).hasColorThat().isEqualTo(classXInstanceType);
    Node thisNode = aliasDeclarationReference.getOnlyChild();
    assertNode(thisNode).isThis().hasColorThat().isEqualTo(classXInstanceType);

    // make sure the single reference to ASYNC_THIS$3 has the right type
    assertNode(thisAliasNameReferences.get(1)).hasColorThat().isEqualTo(classXInstanceType);
  }

  @Test
  public void testInnerArrowFunctionUsingThis() {
    testAsyncRewriting(
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
            "    const ASYNC_THIS$1 = this;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return new Promise((resolve, reject) => {",
            "            return ASYNC_THIS$1;",
            "          });",
            "        });",
            "  }",
            "}"));

    Color classXInstanceType = getGlobalInstanceColor("X");

    ImmutableList<Node> thisAliasNameReferences =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$this$m1146332801$1");
    assertThat(thisAliasNameReferences).hasSize(2);

    // const ASYNC_THIS$1 = this;
    // confirm that `this` and `ASYNC_THIS$1` nodes have the right types in
    // declaration
    Node aliasDeclarationReference = thisAliasNameReferences.get(0);
    assertNode(aliasDeclarationReference).hasColorThat().isEqualTo(classXInstanceType);
    Node thisNode = aliasDeclarationReference.getOnlyChild();
    assertNode(thisNode).isThis().hasColorThat().isEqualTo(classXInstanceType);

    // make sure the single reference to ASYNC_THIS$1 has the right type
    assertNode(thisAliasNameReferences.get(1)).hasColorThat().isEqualTo(classXInstanceType);
  }

  @Test
  public void testInnerSuperCall() {
    testAsyncRewriting(
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
                "    const ASYNC_THIS$3 = this;",
                "    const ASYNC_SUPER_GET$5$m = () => {",
                "      return super.m;",
                "    };",
                "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "        function* () {",
                "          return" + " ASYNC_SUPER_GET$5$m().call(ASYNC_THIS$3);",
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
        classXMethodMDefinition.findMatchingQNameReferences(
            "$jscomp$async$super$get$m1146332801$5$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const ASYNC_SUPER_GET$m = () => super.m;
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
    // return ASYNC_SUPER_GET$m().call(ASYNC_THIS$1);
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // optimization colors don't track function signatures
    assertNode(wrapperArrowFunction).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    // `ASYNC_SUPER_GET$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasColorThat().isEqualTo(classAPropertyMType);

    // `ASYNC_SUPER_GET$m().call(ASYNC_THIS$1)`
    Node methodCallNode = wrapperCallNode.getGrandparent();
    // optimization colors don't track .call types
    assertNode(methodCallNode).isCall().hasColorThat().isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void testInnerSuperReference() {
    testAsyncRewriting(
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
                "    const ASYNC_SUPER_GET$5$m = () => {",
                "      return super.m;",
                "    };",
                "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "        function* () {",
                "          const tmp = ASYNC_SUPER_GET$5$m();",
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
        classXMethodMDefinition.findMatchingQNameReferences(
            "$jscomp$async$super$get$m1146332801$5$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const ASYNC_SUPER_GET$m = () => super.m;
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
    // const tmp = ASYNC_SUPER_GET$m();
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // optimization colors don't track function signatures
    assertNode(wrapperReferenceNameNode).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    // `ASYNC_SUPER_GET$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasColorThat().isEqualTo(classAPropertyMType);
  }

  @Test
  public void testMultipleSuperAccessesInAsyncFunction_havingNonIdenticalUnknownTypes() {
    testAsyncRewriting(
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
            "    const ASYNC_THIS$3 = this;",
            "    const ASYNC_SUPER_GET$5$getUpdateComplete = () => {",
            "      return super.getUpdateComplete;",
            "    };",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(function*() {",
            "      if (ASYNC_SUPER_GET$5$getUpdateComplete()) {",
            "        yield" + " ASYNC_SUPER_GET$5$getUpdateComplete().call(ASYNC_THIS$3);",
            "      }",
            "    });",
            "  }",
            "}"));
  }

  @Test
  public void testNestedArrowFunctionUsingThis() {
    testAsyncRewriting(
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
            "      const ASYNC_THIS$3 = this;",
            "      return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "          function* () {",
            "            return () => {",
            "              return ASYNC_THIS$3;",
            "            };",
            "          })",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testInnerArrowFunctionUsingArguments() {
    testAsyncRewriting(
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
                "    const ASYNC_ARGUMENTS$1 = arguments;",
                "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "        function* () {",
                "          return new Promise((resolve, reject) => {",
                "            return ASYNC_ARGUMENTS$1",
                "          });",
                "        });",
                "  }",
                "}")));

    ImmutableList<Node> argumentsAliasRefs =
        findClassDefinition(getLastCompiler(), "X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$arguments$m1146332801$1");
    assertThat(argumentsAliasRefs).hasSize(2); // one declaration and 1 use

    Color argumentsColor = getGlobalColor(StandardColors.ARGUMENTS_ID);

    // declaration reference
    // const ASYNC_ARGUMENTS$1 = arguments;
    Node argumentsAliasDeclaration = argumentsAliasRefs.get(0);
    Node argumentsValue = argumentsAliasDeclaration.getOnlyChild();
    assertNode(argumentsValue)
        .matchesQualifiedName("arguments")
        .hasColorThat()
        .isEqualTo(argumentsColor);
    assertNode(argumentsAliasDeclaration)
        .matchesQualifiedName("$jscomp$async$arguments$m1146332801$1")
        .hasColorThat()
        .isEqualTo(argumentsColor);

    // usage reference
    // return ASYNC_ARGUMENTS$1;
    Node argumentsAliasUsage = argumentsAliasRefs.get(1);
    assertNode(argumentsAliasUsage)
        .matchesQualifiedName("$jscomp$async$arguments$m1146332801$1")
        .hasColorThat()
        .isEqualTo(argumentsColor);
  }

  @Test
  public void testAwaitReplacement() {
    testAsyncRewriting(
        "async function foo(promise) { return await promise; }",
        lines(
            "function foo(promise) {",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
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
    testAsyncRewriting(
        "async function f(a, b, ...rest) { return arguments.length; }",
        lines(
            "function f(a, b, ...rest) {",
            "  const ASYNC_ARGUMENTS$1 = arguments;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return ASYNC_ARGUMENTS$1.length;", // arguments replaced
            "        });",
            "}"));
  }

  @Test
  public void testArgumentsReplacement_asyncClosure() {
    testAsyncRewriting(
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
            "    const ASYNC_ARGUMENTS$3 = arguments;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return ASYNC_ARGUMENTS$3.length;", // arguments replaced
            "        });",
            "  }",
            "  return f(arguments)", // unchanged
            "}"));
  }

  @Test
  public void testArgumentsReplacement_normalClosureInAsync() {
    testAsyncRewriting(
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
                "  const ASYNC_ARGUMENTS$1 = arguments;",
                "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "        function* () {",
                "          function inner() {",
                "            return arguments.length;", // unchanged
                "          }",
                "          return inner.apply(undefined, ASYNC_ARGUMENTS$1);",
                "        });",
                "}")));
  }

  @Test
  public void testClassMethod() {
    testAsyncRewriting(
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
            "    const ASYNC_THIS$3 = this;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function *() {",
            "          return ASYNC_THIS$3.x;", // this replaced
            "        });",
            "  }",
            "}"));
  }

  @Test
  public void testAsyncClassMethodWithAsyncArrow() {
    testAsyncRewriting(
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
                "    const ASYNC_THIS$1 = this;",
                "    const ASYNC_ARGUMENTS$1 = arguments;",
                "      return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "          function *() {",
                "            let g = () => {",
                "              return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "                  function *() {",
                "                    console.log(ASYNC_THIS$1," + " ASYNC_ARGUMENTS$1);",
                "                  });",
                "            };",
                "            g();",
                "          });",
                "  }",
                "}")));
  }

  @Test
  public void testNonAsyncClassMethodWithAsyncArrow() {
    testAsyncRewriting(
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
                "      const ASYNC_THIS$3 = this;",
                "      const ASYNC_ARGUMENTS$3 = arguments;",
                "      return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
                "          function *() {",
                "            console.log(ASYNC_THIS$3," + " ASYNC_ARGUMENTS$3);",
                "          });",
                "    };",
                "    g();",
                "  }",
                "}")));
  }

  @Test
  public void testArrowFunctionExpressionBody() {
    testAsyncRewriting(
        "let f = async () => 1;",
        lines(
            "let f = () => {",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return 1;",
            "        });",
            "}"));
  }

  @Test
  public void testGlobalScopeArrowFunctionRefersToThis() {
    testAsyncRewriting(
        "let f = async () => this;",
        lines(
            "let f = () => {",
            "    const ASYNC_THIS$1 = this;",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return ASYNC_THIS$1;",
            "        });",
            "}"));
  }

  @Test
  public void testGlobalScopeAsyncArrowFunctionDefaultParamValueRefersToThis() {
    testAsyncRewriting(
        "let f = async (t = this) => t;",
        lines(
            "let f = (t = this) => {",
            "    return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "        function* () {",
            "          return t;",
            "        });",
            "}"));
  }

  @Test
  public void testNestedAsyncArrowFunctionDefaultParamValueRefersToThis() {
    testAsyncRewriting(
        lines("let f = async function(outerT = this) {", "  return async (t = this) => t;", "};"),
        lines(
            // `this` is not aliased here
            "let f = function(outerT = this) {",
            "  const ASYNC_THIS$1 = this;",
            "  return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "      function* () {",
            // `this` is aliased here
            "        return (t = ASYNC_THIS$1) => {",
            "          return (0, $jscomp.asyncExecutePromiseGeneratorFunction)(",
            "              function* () {",
            "                return t;",
            "              });",
            "        };",
            "      });",
            "};",
            ""));
  }

  private void testAsyncRewriting(Externs externs, Sources sources, Expected expected) {
    Expected updatedExpected =
        expected(
            UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                (FlatSources) sources, expected, REPLACEMENTS_MAP));
    test(externs, sources, updatedExpected);
  }

  private void testAsyncRewriting(String source, String expected) {
    Expected updatedExpected =
        expected(
            UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                (FlatSources) srcs(source), expected(expected), REPLACEMENTS_MAP));
    test(srcs(source), updatedExpected);
  }
}
