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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.function.Predicate;
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
    enableTypeCheck();
    enableTypeInfoValidation();
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

    /** Returns the first class method definiton found with the given name. */
    private CodeSubTree findMethodDefinition(String wantedMethodName) {
      Node methodDefinitionNode =
          findFirstNode(
              rootNode,
              (node) -> node.isMemberFunctionDef() && wantedMethodName.equals(node.getString()));

      return new CodeSubTree(methodDefinitionNode);
    }

    /** Executes an action for every instance of a given qualified name. */
    private ImmutableList<Node> findMatchingQNameReferences(final String wantedQName) {
      return findNodesAllowEmpty(rootNode, (node) -> node.matchesQualifiedName(wantedQName));
    }
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

    ObjectType classXInstanceType = getGlobalObjectType("X");

    ImmutableList<Node> thisAliasNameReferences =
        findClassDefinition("X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$this");
    assertThat(thisAliasNameReferences).hasSize(2);

    // const $jscomp$async$this = this;
    // confirm that `this` and `$jscomp$async$this` nodes have the right types in declaration
    Node aliasDeclarationReference = thisAliasNameReferences.get(0);
    assertNode(aliasDeclarationReference).hasJSTypeThat().isEqualTo(classXInstanceType);
    Node thisNode = aliasDeclarationReference.getOnlyChild();
    assertNode(thisNode).isThis().hasJSTypeThat().isEqualTo(classXInstanceType);

    // make sure the single reference to $jscomp$async$this has the right type
    assertNode(thisAliasNameReferences.get(1)).hasJSTypeThat().isEqualTo(classXInstanceType);
  }

  @Test
  public void testInnerSuperCall() {
    test(
        externs(new TestExternsBuilder().addPromise().build()),
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
                "    const $jscomp$async$super$get$m = () => super.m;",
                "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
                "        function* () {",
                "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
                "        });",
                "  }",
                "}")));

    ObjectType classAInstanceType = getGlobalObjectType("A");
    // type of A.prototype.m
    FunctionType classAPropertyMType = classAInstanceType.getPropertyType("m").assertFunctionType();

    CodeSubTree classXMethodMDefinition = findClassDefinition("X").findMethodDefinition("m");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodMDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    // arrow function has a JSType representing a function that returns type type of `super.m`
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // wrapper function variable has type matching the function itself
    JSType wrapperArrowFunctionType = wrapperArrowFunction.getJSType();
    assertNode(wrapperDeclarationNameNode).hasJSTypeThat().isEqualTo(wrapperArrowFunctionType);

    // get `super.m` from `() => `super.m`
    Node superDotM = wrapperArrowFunction.getLastChild();
    assertNode(superDotM)
        .matchesQualifiedName("super.m")
        .hasJSTypeThat()
        .isEqualTo(classAPropertyMType);
    Node superNode = superDotM.getFirstChild();
    assertNode(superNode).isSuper().hasJSTypeThat().isEqualTo(classAInstanceType);

    // second name node is reference
    // return $jscomp$async$super$get$m().call($jscomp$async$this);
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // TODO(bradfordcsmith): The name type should be equal to the arrowFunctionType, but it
    //     somehow isn't
    assertNode(wrapperReferenceNameNode)
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // `$jscomp$async$super$get$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasJSTypeThat().isEqualTo(classAPropertyMType);

    // `$jscomp$async$super$get$m().call($jscomp$async$this)`
    Node methodCallNode = wrapperCallNode.getGrandparent();
    // the .call() we created returns the same type as calling the original method directly
    assertNode(methodCallNode)
        .isCall()
        .hasJSTypeThat()
        .isEqualTo(classAPropertyMType.getReturnType());
  }

  @Test
  public void testInnerSuperReference() {
    test(
        externs(new TestExternsBuilder().addFunction().build()),
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
                "    const $jscomp$async$super$get$m = () => super.m;",
                "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
                "        function* () {",
                "          const tmp = $jscomp$async$super$get$m();",
                // type of tmp will indicate it requires `this` be provided, but will allow null.
                "          return tmp.call(null);",
                "        });",
                "  }",
                "}")));

    ObjectType classAInstanceType = getGlobalObjectType("A");
    // type of A.prototype.m
    FunctionType classAPropertyMType = classAInstanceType.getPropertyType("m").assertFunctionType();

    CodeSubTree classXMethodMDefinition = findClassDefinition("X").findMethodDefinition("m");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodMDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    // arrow function has a JSType representing a function that returns type type of `super.m`
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // wrapper function variable has type matching the function itself
    JSType wrapperArrowFunctionType = wrapperArrowFunction.getJSType();
    assertNode(wrapperDeclarationNameNode).hasJSTypeThat().isEqualTo(wrapperArrowFunctionType);

    // get `super.m` from `() => `super.m`
    Node superDotM = wrapperArrowFunction.getLastChild();
    assertNode(superDotM)
        .matchesQualifiedName("super.m")
        .hasJSTypeThat()
        .isEqualTo(classAPropertyMType);
    Node superNode = superDotM.getFirstChild();
    assertNode(superNode).hasJSTypeThat().isEqualTo(classAInstanceType);

    // second name node is reference
    // const tmp = $jscomp$async$super$get$m();
    Node wrapperReferenceNameNode = superMethodWrapperNameNodes.get(1);
    // TODO(bradfordcsmith): The name type should be equal to the arrowFunctionType, but it
    //     somehow isn't
    assertNode(wrapperReferenceNameNode)
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // `$jscomp$async$super$get$m()`
    Node wrapperCallNode = wrapperReferenceNameNode.getParent();
    assertNode(wrapperCallNode).isCall().hasJSTypeThat().isEqualTo(classAPropertyMType);
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

    ObjectType classAInstanceType = getGlobalObjectType("A");
    // type of A.prototype.m
    FunctionType classAPropertyMType = classAInstanceType.getPropertyType("m").assertFunctionType();

    CodeSubTree classXMethodMDefinition = findClassDefinition("X").findMethodDefinition("m");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodMDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    // arrow function has a JSType representing a function that returns type type of `super.m`
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // wrapper function variable has type matching the function itself
    JSType wrapperArrowFunctionType = wrapperArrowFunction.getJSType();
    assertNode(wrapperDeclarationNameNode).hasJSTypeThat().isEqualTo(wrapperArrowFunctionType);

    // get `Object.getPrototypeOf(...).m` from `() => `Object.getPrototypeOf(...).m`
    Node fakeSuperDotM = wrapperArrowFunction.getLastChild();
    assertNode(fakeSuperDotM).hasJSTypeThat().isEqualTo(classAPropertyMType);
    Node fakeSuperNode = fakeSuperDotM.getFirstChild();
    assertNode(fakeSuperNode).isCall().hasJSTypeThat().isEqualTo(classAInstanceType);
    assertNode(fakeSuperNode.getFirstChild()).matchesQualifiedName("Object.getPrototypeOf");
  }

  @Test
  public void testInnerSuperCallStaticEs2015Out() {
    setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "class A {",
            "  /**",
            "   * @return {number}",
            "   */",
            "  static m() {",
            "    return this.someNumber;",
            "  }",
            "}",
            "/** @const {number} */",
            "A.someNumber = 3;",
            "",
            "class X extends A {",
            "  /**",
            "   * @return {!Promise<number>}",
            "   */",
            "  static async asyncM() {",
            "    return super.m();",
            "  }",
            "}"),
        lines(
            "class A {",
            "  /**",
            "   * @return {number}",
            "   */",
            "  static m() {",
            "    return this.someNumber;",
            "  }",
            "}",
            "/** @const {number} */",
            "A.someNumber = 3;",
            "",
            "class X extends A {",
            "  /**",
            "   * @return {!Promise<number>}",
            "   */",
            "  static asyncM() {",
            "    const $jscomp$async$this = this;",
            "    const $jscomp$async$super$get$m = () => Object.getPrototypeOf(this).m;",
            "    return $jscomp.asyncExecutePromiseGeneratorFunction(",
            "        function* () {",
            "          return $jscomp$async$super$get$m().call($jscomp$async$this);",
            "        });",
            "  }",
            "}"));

    ObjectType classAInstanceType = getGlobalObjectType("A");
    // type of A.prototype.m
    FunctionType classAConstructorType = classAInstanceType.getConstructor();
    FunctionType classAPropertyMType =
        classAConstructorType.getPropertyType("m").assertFunctionType();

    CodeSubTree classXMethodDefinition = findClassDefinition("X").findMethodDefinition("asyncM");

    // Check type information on wrapper function for `super.m`
    ImmutableList<Node> superMethodWrapperNameNodes =
        classXMethodDefinition.findMatchingQNameReferences("$jscomp$async$super$get$m");
    // one declaration and one reference
    assertThat(superMethodWrapperNameNodes).hasSize(2);

    // first name node is declaration
    // const $jscomp$async$super$get$m = () => super.m;
    Node wrapperDeclarationNameNode = superMethodWrapperNameNodes.get(0);
    // arrow function has a JSType representing a function that returns type type of `super.m`
    Node wrapperArrowFunction = wrapperDeclarationNameNode.getOnlyChild();
    assertNode(wrapperArrowFunction)
        .isArrowFunction()
        .hasJSTypeThat()
        .isFunctionTypeThat()
        .hasReturnTypeThat()
        .isEqualTo(classAPropertyMType);
    // wrapper function variable has type matching the function itself
    JSType wrapperArrowFunctionType = wrapperArrowFunction.getJSType();
    assertNode(wrapperDeclarationNameNode).hasJSTypeThat().isEqualTo(wrapperArrowFunctionType);

    // get `Object.getPrototypeOf(...).m` from `() => `Object.getPrototypeOf(...).m`
    Node fakeSuperDotM = wrapperArrowFunction.getLastChild();
    assertNode(fakeSuperDotM).hasJSTypeThat().isEqualTo(classAPropertyMType);
    Node fakeSuperNode = fakeSuperDotM.getFirstChild();
    // TODO(b/118174876): We currently type `super` as unknown in static methods.
    assertNode(fakeSuperNode).isCall().hasJSTypeThat().toStringIsEqualTo("?");
    assertNode(fakeSuperNode.getFirstChild()).matchesQualifiedName("Object.getPrototypeOf");
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
        externs(new TestExternsBuilder().addArguments().build()),
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
        findClassDefinition("X")
            .findMethodDefinition("m")
            .findMatchingQNameReferences("$jscomp$async$arguments");
    assertThat(argumentsAliasRefs).hasSize(2); // one declaration and 1 use

    JSType argumentsJsType = getGlobalJSType("Arguments");

    // declaration reference
    // const $jscomp$async$arguments = arguments;
    Node argumentsAliasDeclaration = argumentsAliasRefs.get(0);
    Node argumentsValue = argumentsAliasDeclaration.getOnlyChild();
    assertNode(argumentsValue)
        .matchesQualifiedName("arguments")
        .hasJSTypeThat()
        .isEqualTo(argumentsJsType);
    assertNode(argumentsAliasDeclaration)
        .matchesQualifiedName("$jscomp$async$arguments")
        .hasJSTypeThat()
        .isEqualTo(argumentsJsType);

    // usage reference
    // return $jscomp$async$arguments;
    Node argumentsAliasUsage = argumentsAliasRefs.get(1);
    assertNode(argumentsAliasUsage)
        .matchesQualifiedName("$jscomp$async$arguments")
        .hasJSTypeThat()
        .isEqualTo(argumentsJsType);
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
            "  /**",
            "   * @param {...?} var_args",
            "   * @return {!Promise<number>}",
            "   */",
            "  async function f(var_args) { return arguments.length; }",
            "  return f(arguments)",
            "}"),
        lines(
            "function outer() {",
            "  /**",
            "   * @param {...?} var_args",
            "   * @return {!Promise<number>}",
            "   */",
            "  function f(var_args) {",
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
        externs(new TestExternsBuilder().addFunction().build()),
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
            "  /**",
            "   * @param {number} x",
            "   */",
            "  constructor(x) {",
            "    /** @type {number} */ this.x = x;",
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
        externs(new TestExternsBuilder().addConsole().build()),
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
        externs(new TestExternsBuilder().addConsole().build()),
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
}
