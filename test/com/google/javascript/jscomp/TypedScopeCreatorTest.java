/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.ScopeSubject.assertScope;
import static com.google.javascript.jscomp.TypedScopeCreator.CTOR_INITIALIZER;
import static com.google.javascript.jscomp.TypedScopeCreator.IFACE_INITIALIZER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TypedScopeCreator} and {@link TypeInference}. Admittedly, the name is a bit of a
 * misnomer.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class TypedScopeCreatorTest extends CompilerTestCase {

  private JSTypeRegistry registry;
  private TypedScope globalScope;
  private TypedScope lastLocalScope;
  private TypedScope lastFunctionScope;

  /**
   * Maps a label name to information about the labeled statement.
   *
   * <p>This map is recreated each time parseAndRunTypeInference() is executed.
   * TODO(bradfordcsmith): This map and LabeledStatement are also in TypeInferenceTest.
   *     It would be good to unify them.
   */
  private Map<String, LabeledStatement> labeledStatementMap;

  /** Stores information about a labeled statement and allows making assertions on it. */
  static class LabeledStatement {
    final Node statementNode;
    final TypedScope enclosingScope;

    LabeledStatement(Node statementNode, TypedScope enclosingScope) {
      this.statementNode = checkNotNull(statementNode);
      this.enclosingScope = checkNotNull(enclosingScope);
    }
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeInfoValidation();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private class ScopeFinder extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      TypedScope scope = t.getTypedScope();
      if (scope.isGlobal()) {
        globalScope = scope;
      } else if (scope.isBlockScope()) {
        // TODO(bradfordcsmith): use labels to find scopes instead of lastLocalScope
        lastLocalScope = scope;
      } else if (scope.isFunctionScope()) {
        lastFunctionScope = scope;
      }
      if (parent != null && parent.isLabel() && !n.isLabelName()) {
        // First child of a LABEL is a LABEL_NAME, n is the second child.
        Node labelNameNode = checkNotNull(n.getPrevious(), n);
        checkState(labelNameNode.isLabelName(), labelNameNode);
        String labelName = labelNameNode.getString();
        assertWithMessage("Duplicate label name: %s", labelName)
            .that(labeledStatementMap)
            .doesNotContainKey(labelName);
        labeledStatementMap.put(labelName, new LabeledStatement(n, scope));
      }
    }
  }

  private LabeledStatement getLabeledStatement(String label) {
    assertWithMessage("No statement found for label: %s", label)
        .that(labeledStatementMap)
        .containsKey(label);
    return labeledStatementMap.get(label);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    registry = compiler.getTypeRegistry();
    // Create a fresh statement map for each test case.
    labeledStatementMap = new HashMap<>();
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
        TypedScope topScope = scopeCreator.createScope(root.getParent(), null);
        (new TypeInferencePass(
            compiler, compiler.getReverseAbstractInterpreter(),
            topScope, scopeCreator)).process(externs, root);
        new NodeTraversal(compiler, new ScopeFinder(), scopeCreator).traverseRoots(externs, root);
      }
    };
  }

  @Test
  public void testVarDeclarationWithJSDocForObjPatWithOneVariable() {
    // Ignore JSDoc on a destructuring declaration, and just infer the type.
    // CheckJSDoc will issue a warning for the @type annotation.
    testSame("/** @type {string} */ var {a} = {a: 1};");
    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testVarDeclarationWithJSDocForObjPatWithMultipleVariables() {
    // Ignore JSDoc on a destructuring declaration
    // CheckJSDoc will issue a warning for the @type annotation.
    testSame("/** @type {string} */ var {a, b} = {a: 1};");
    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();

    TypedVar bVar = checkNotNull(globalScope.getVar("b"));
    assertType(bVar.getType()).toStringIsEqualTo("?");
    assertThat(bVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testVarDeclarationObjPatShorthandProp() {
    testSame("var {/** number */ a} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationObjPatShorthandPropWithDefault() {
    testSame("var {/** number */ a = 2} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationObjPatNormalProp() {
    testSame("var {a: /** number */ a} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationObjPatNormalPropWithDefault() {
    testSame("var {a: /** number */ a = 2} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationObjPatComputedProp() {
    testSame("var {['a']: /** number */ a} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationObjPatComputedPropWithDefault() {
    testSame("var {['a']: /** number */ a = 2} = {a: 1};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationArrayPat() {
    testSame("var [ /** number */ a ] = [1];");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationArrayPatWithDefault() {
    testSame("var [ /** number */ a = 2 ] = [1];");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testVarDeclarationArrayPatRest() {
    // TODO(bradfordcsmith): Add a TypeCheck test case to ensure rest values are always Arrays
    testSame("var [ ... /** !Array<number> */ a ] = [1];");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("Array<number>");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testConstDeclarationObjectPatternInfersType_forAliasedConstructor() {
    testSame(
        lines(
            "const ns = {};",
            "/** @constructor */",
            "ns.Foo = function() {}",
            "",
            "const {Foo} = ns;",
            "const /** !Foo */ fooInstance = new Foo();"));

    TypedVar fooVar = checkNotNull(globalScope.getVar("Foo"));
    assertType(fooVar.getType()).toStringIsEqualTo("function(new:ns.Foo): undefined");
    assertThat(fooVar.isTypeInferred()).isFalse();

    TypedVar fooInstanceVar = checkNotNull(globalScope.getVar("fooInstance"));
    assertType(fooInstanceVar.getType()).toStringIsEqualTo("ns.Foo");
    assertThat(fooInstanceVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testConstDeclarationObjectPatternInfersType_forAliasedTypedef() {
    testSame(
        lines(
            "const ns = {};",
            "/** @typedef {string} */ ns.Foo;",
            "const {Foo} = ns;",
            "let /** !Foo */ f = 'bar';"));

    TypedVar fooVar = checkNotNull(globalScope.getVar("Foo"));
    assertType(fooVar.getType()).toStringIsEqualTo("None");

    JSType fooType = registry.getGlobalType("Foo");
    assertType(fooType).isEqualTo(getNativeType(JSTypeNative.STRING_TYPE));

    TypedVar fooInstanceVar = checkNotNull(globalScope.getVar("f"));
    assertType(fooInstanceVar.getType()).toStringIsEqualTo("string");
    assertThat(fooInstanceVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testConstDeclarationObjectPatternInfersTypeAsDeclared() {
    testSame(
        lines(
            "const /** {a: number} */ obj = {a: 3};", // preserve newline
            "const {a} = obj;"));

    // we treat this as declaring a type on `a`
    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testConstDeclarationObjectPatternInfersTypeGivenComputedProperty() {
    testSame(
        lines(
            "const /** !IObject<string, number> */ obj = {a: 3};", // preserve newline
            "const {['foobar']: a} = obj;"));

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testConstDeclarationObjectPatternInfersTypeGivenUnknownComputedProperty() {
    testSame(
        lines(
            "var obj = {};", // preserve newline
            "const {['foobar']: a} = obj;"));

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("?");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testConstDeclarationArrayPatternInfersType() {
    testSame(
        lines(
            "const /** !Iterable<number> */ arr = [1, 2, 3];", // preserve newline
            "const [a] = arr;"));

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testConstDeclarationWithOrInRhs() {
    // needed because there is a special case for `var goog = goog || {};` that was crashing when
    // given a destructuring lhs.
    testSame("let obj; const {a} = obj || {};");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("?");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testConstDestructuringDeclarationWithUnknownTypeInExtendsClause() {
    disableTypeInfoValidation();
    testWarning(
        externs("var someUnknownExtern;"),
        srcs("const {Parent} = someUnknownExtern; class Child extends Parent {}"),
        warning(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR));

    TypedVar parentVar = checkNotNull(globalScope.getVar("Parent"));
    assertType(parentVar.getType()).isUnknown();

    TypedVar childVar = checkNotNull(globalScope.getVar("Child"));
    FunctionType childType = childVar.getType().toMaybeFunctionType();
    JSType superclassCtor = childType.getSuperClassConstructor();
    assertType(superclassCtor).isNull();
  }

  @Test
  public void testConstDestructuringDeclarationWithUnknownPropertyInExtendsClause() {
    disableTypeInfoValidation();
    testWarning(
        externs("var /** !Object */ someUnknownExtern;"),
        srcs("const {Parent} = someUnknownExtern; class Child extends Parent {}"),
        warning(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR));

    TypedVar parentVar = checkNotNull(globalScope.getVar("Parent"));
    assertType(parentVar.getType()).isUnknown();

    TypedVar childVar = checkNotNull(globalScope.getVar("Child"));
    FunctionType childType = childVar.getType().toMaybeFunctionType();
    JSType superclassCtor = childType.getSuperClassConstructor();
    assertType(superclassCtor).isNull();
  }

  // TODO(bradfordcsmith): Add Object rest test case.

  @Test
  public void testVarDeclarationNestedPatterns() {
    testSame(
        lines(
            "var [",
            "    {a: /** number */ a1},",
            "    {a: /** string */ a2},",
            "    ...{/** number */ length}",
            "  ] = [{a: 1}, {a: '2'}, 1, 2, 3];"));

    TypedVar a1Var = checkNotNull(globalScope.getVar("a1"));
    assertType(a1Var.getType()).toStringIsEqualTo("number");
    assertThat(a1Var.isTypeInferred()).isFalse();

    TypedVar a2Var = checkNotNull(globalScope.getVar("a2"));
    assertType(a2Var.getType()).toStringIsEqualTo("string");
    assertThat(a2Var.isTypeInferred()).isFalse();

    TypedVar lengthVar = checkNotNull(globalScope.getVar("length"));
    assertType(lengthVar.getType()).toStringIsEqualTo("number");
    assertThat(lengthVar.isTypeInferred()).isFalse();
  }

  // The following testAssign* tests check that we never treat qualified names in destructuring
  // patterns as declared. CheckJSDoc will warn on those cases, so TypedScopeCreator just ignores
  // them. The only way to 'declare' a qualified name is:
  //    /** @type {number} */ a.b.c = rhs;

  @Test
  public void testAssignWithJSDocForObjPatWithOneVariable() {
    // Ignore the JSDoc on the assignment
    testSame("const ns = {}; (/** @type {number} */ {a: ns.a} = {a: 1});");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignWithJSDocForObjPatWithMultipleVariables() {
    // Ignore the JSDoc on the assignment
    testSame(
        "const ns = {}; (/** @type {number} */ {a: ns.a, b: ns.b} = {a: 1});");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignObjPatNormalProp() {
    // CheckJSDoc will warn on the inline type annotation here, typechecking just ignores it.
    testSame("const ns = {}; ({a: /** number */ ns.a} = {a: 1});");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignObjPatComputedProp() {
    // CheckJSDoc will warn on the inline type annotation here, typechecking just ignores it.
    testSame("const ns = {}; ({['a']: /** number */ ns.a} = {a: 1});");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignArrayPatWithJSDocOnAssign() {
    testSame("const ns = {}; /** @type {number} */ [ ns.a ] = [1];");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignArrayPatWithQualifiedName() {
    // CheckJSDoc will warn on the inline type annotation here, typechecking just ignores it.
    testSame("const ns = {}; [ /** number */ ns.a ] = [1];");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testAssignArrayPatWithQualifiedNameAndDefaultValue() {
    // CheckJSDoc will warn on the inline type annotation here, typechecking just ignores it.
    testSame("const ns = {}; [ /** number */ ns.a = 1 ] = [];");

    TypedVar aVar = globalScope.getVar("ns.a");
    assertThat(aVar).isNull();
  }

  @Test
  public void testForOfWithObjectPatVarDeclarationWithShorthand() {
    testSame("for (var {/** number */ a} of {}) {}");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testForOfWithArrayPatVarDeclaration() {
    testSame("for (var [/** number */ a] of []) {}");

    TypedVar aVar = checkNotNull(globalScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testCastOnLhsDoesntDeclareProperty() {
    testSame("const ns = {}; /** @type {null} */ (ns.a) = null;");

    assertThat(globalScope.getVar("ns.a")).isNull();
  }

  @Test
  public void testForOfWithObjectPatConstDeclarationShadowedInLoop() {
    testSame(
        lines(
            "for (const {/** number */ a} of {}) {",
            "  const /** string */ a = 'foo';",
            "  IN_LOOP: a;",
            "}"));

    assertScope(globalScope).doesNotDeclare("a");
    TypedScope loopBlockScope = getLabeledStatement("IN_LOOP").enclosingScope;
    TypedScope loopInitializerScope = loopBlockScope.getParent();
    assertThat(loopInitializerScope).isNotEqualTo(globalScope);

    TypedVar aVarloopBlock = loopBlockScope.getVar("a");
    assertType(aVarloopBlock.getType()).toStringIsEqualTo("string");
    assertThat(aVarloopBlock.isTypeInferred()).isFalse();

    TypedVar aVarLoopInit = loopInitializerScope.getVar("a");
    assertType(aVarLoopInit.getType()).toStringIsEqualTo("number");
    assertThat(aVarLoopInit.isTypeInferred()).isFalse();
  }

  @Test
  public void testDeclarativelyUnboundVarsWithoutTypes() {
    testSame(
        lines(
            "var uninitializedVar;",
            "let uninitializedLet;",
            "",
            "/** @type {?} */ var uninitializedVarWithType;",
            "/** @type {?} */ let uninitializedLetWithType;",
            "",
            "/** @typedef {number} */ var typedefVar;",
            "/** @typedef {number} */ let typedefLet;",
            "",
            "var initializedVar = 1;",
            "let initializedLet = 1;",
            "",
            "/** @type {number} */ var initializedVarWithType = 1;",
            "/** @type {number} */ let initializedLetWithType = 1;",
            "",
            "/** @const */ var CONST_VAR = 1;",
            "/** @const */ let CONST_LET = 1;",
            "const CONST = 1;",
            "",
            "/** @const {number} */ var   CONST_VAR_WITH_TYPE = 1;",
            "/** @const {number} */ let   CONST_LET_WITH_TYPE = 1;",
            "/** @type  {number} */ const CONST_WITH_TYPE     = 1;",
            ""));
    String[] expectedVarNames =
        new String[] {
          "uninitializedVar",
          "typedefVar",
        };
    List<TypedVar> expectedVars = new ArrayList<>();
    for (String varName : expectedVarNames) {
      expectedVars.add(globalScope.getVar(varName));
    }
    assertThat(globalScope.getDeclarativelyUnboundVarsWithoutTypes())
        .containsExactlyElementsIn(expectedVars);
  }

  @Test
  public void testRestParameters() {
    testSame(
        lines(
            "/**", // preserve newlines
            " * @param {string} str",
            " * @param {...number} nums",
            " */",
            "function doSomething(str, ...nums) {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("string");
    assertScope(functionBodyScope)
        .declares("nums")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("Array<number>");
  }

  @Test
  public void testRestObjectPatternParameters() {
    testSame(
        externs("/** @type {number} */ Array.prototype.length"),
        srcs(
            lines(
                "/**", // preserve newlines
                " * @param {...string} strs",
                " */",
                "function doSomething(...{length}) {",
                "  FUNCTION_BODY: 0;",
                "}",
                "")));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("length")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("number");
  }

  @Test
  public void testDefaultParameterFullJSDoc() {
    testSame(
        lines(
            "/**", // preserve newlines
            " * @param {string=} str",
            " */",
            "function doSomething(str = '') {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        // TODO(b/117162687): this should just be `string`
        .toStringIsEqualTo("(string|undefined)");
  }

  @Test
  public void testDefaultParameterInlineJSDoc() {
    testSame(
        lines(
            "", // preserve newlines
            "function doSomething(/** string= */ str = '') {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        // TODO(b/117162687): this should just be `string`
        .toStringIsEqualTo("(string|undefined)");
  }

  @Test
  public void testDefaultParameterNoJSDoc() {
    testSame(
        lines(
            "", // preserve newlines
            "function doSomething(str = '') {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("?");
  }

  @Test
  public void testDefaultParameterNoJSDocInferredToBeOptional() {
    testSame("function f(a = 3) {}");

    assertScope(globalScope)
        .declares("f")
        .withTypeThat()
        .toStringIsEqualTo("function(?=): undefined");
  }

  @Test
  public void testDefaultParameterConflictingJSDoc() {
    testSame(
        lines(
            "/**", // preserve newlines
            " * @param {number=} str",
            " */",
            "function doSomething(/** string= */ str = '') {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        // TODO(b/111523967): Should report an error when header and inline JSDoc types
        //     conflict.
        .toStringIsEqualTo("(number|undefined)");
  }

  @Test
  public void testDefaultParameterSetToUndefinedIsDeclaredAsPossiblyUndefined() {
    testSame(
        lines(
            "/** @param {number=} num */",
            "function doSomething(num = undefined) {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("num")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("(number|undefined)");
  }

  @Test
  public void testDefaultParameterFullJSDoc_setToUndefined() {
    testSame(
        lines(
            "/**", // preserve newlines
            " * @param {(string|undefined)=} str", // NOTE: we just drop the |undefined here
            " */",
            "function doSomething(str = '') {",
            "  FUNCTION_BODY: 0;",
            "}",
            ""));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("(string|undefined)");
  }

  @Test
  public void testDefaultDestructuringParameterFullJSDoc() {
    testSame(
        lines(
            "/** @param {{str: (string|undefined)}=} data */",
            "function f({str = ''} = {}) {",
            "  FUNCTION_BODY: 0;",
            "}"));
    TypedScope functionBodyScope = getLabeledStatement("FUNCTION_BODY").enclosingScope;
    assertScope(functionBodyScope)
        .declares("str")
        .onClosestContainerScope()
        .withTypeThat()
        .toStringIsEqualTo("(string|undefined)");
  }

  @Test
  public void testDestructuringParameterWithNoJSDoc() {
    testSame("function f([x, y], {z}) {}");

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(?, ?): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testArrayPatternParameterWithFullJSDoc() {
    testSame(
        lines(
            "/**",
            " * @param {string} x",
            " * @param {!Iterable<number>} arr",
            " */",
            "function f(x, [y]) {}"));

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(string, Iterable<number>): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("string");
    assertThat(xVar.isTypeInferred()).isFalse();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("number");
    assertThat(yVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testArrayPatternParameterWithRestWithFullJSDoc() {
    testSame("/** @param {!Iterable<number>} arr */ function f([x, ...y]) {}");

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(Iterable<number>): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isFalse();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("Array<number>");
    assertThat(yVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testObjectPatternParameterWithFullJSDoc() {
    testSame("/** @param {{a: string, b: number}} arr */ function f({a, b}) {}");

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function({a: string, b: number}): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar aVar = checkNotNull(lastFunctionScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("string");
    assertThat(aVar.isTypeInferred()).isFalse();

    TypedVar bVar = checkNotNull(lastFunctionScope.getVar("b"));
    assertType(bVar.getType()).toStringIsEqualTo("number");
    assertThat(bVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testObjectPatternParameterWithUnknownPropertyWithFullJSDoc() {
    testSame("/** @param {{a: string}} arr */ function f({a, b}) {}");

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function({a: string}): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar aVar = checkNotNull(lastFunctionScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("string");
    assertThat(aVar.isTypeInferred()).isFalse();

    TypedVar bVar = checkNotNull(lastFunctionScope.getVar("b"));
    assertType(bVar.getType()).toStringIsEqualTo("?");
    assertThat(bVar.isTypeInferred()).isTrue();
  }

  /**
   * Note: these tests expect the parameters to have inferred, not declared, types because of the
   * order in which scope creation & named type resolution happens.
   *
   * <p>See also b/118710352
   */
  @Test
  public void testObjectPatternParameter_withUnresolvedForwardDeclaredType_isInferred() {
    // Note that this only emits one UNRECOGNIZED_TYPE_ERROR for SomeUnknownName. We don't emit
    // an error for SomeUnknownName#a, as that would be redundant.
    test(
        srcs(
            lines(
                "goog.forwardDeclare('SomeUnknownName');",
                "/** @param {!SomeUnknownName} obj */ function f({a}) {}")),
        warning(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR));

    TypedVar aVar = checkNotNull(lastFunctionScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("?");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testObjectPatternParameterWithFullJSDoc_withMissingProperty_isInferred() {
    testSame(
        lines(
            "class SomeName {}", //
            "/** @param {!SomeName} obj */ function f({a}) {}"));

    TypedVar aVar = checkNotNull(lastFunctionScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("?");
    // we still consider this a declared type
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testNestedObjectPatternParameter_withNamedType() {
    testSame(
        lines(
            "class SomeOtherName {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.a;",
            "  }",
            "}",
            "class SomeName {",
            "  constructor() {",
            "    /** @type {!SomeOtherName} */",
            "    this.data;",
            "  }",
            "}",
            "/** @param {!SomeName} obj */ function f({data: {a}}) { A: a; }",
            ""));

    // This variable becomes inferred, even though SomeName is declared before it is referenced,
    // just because the SomeName in the @param is still unresolved at type scope creation time.
    TypedVar aVar = checkNotNull(getLabeledStatement("A").enclosingScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testTypecheckingTemplatizedTypesForObjectParameter() {
    testSame(
        lines(
            "/**",
            " * @constructor ",
            " * @template T",
            " */",
            "function TemplatizedClass() {",
            "  /** @const {T} */",
            "  this.data;",
            "}",
            "/** @param {!TemplatizedClass<!SomeName>} obj */",
            "function f({data}) { A: data.a; }",
            "",
            "class SomeName {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.a;",
            "  }",
            "}"));

    LabeledStatement aStatement = getLabeledStatement("A");
    TypedVar dataVar = checkNotNull(aStatement.enclosingScope.getVar("data"));

    assertType(dataVar.getType()).toStringIsEqualTo("SomeName");
    assertType(aStatement.statementNode.getOnlyChild().getJSType()).toStringIsEqualTo("number");
  }

  @Test
  public void testTypecheckingAliasOfTypedefType_forObjectParameter() {
    testSame(
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.a;",
            "  }",
            "}",
            "/** @typedef {!Foo} */",
            "var TypedefOfFoo;",
            "",
            "const ns = {};",
            "/** @const */",
            "ns.TypedefOfFoo = TypedefOfFoo;",
            "",
            "/** @param {!ns.TypedefOfFoo} obj */",
            "function f({a}) { A: a; }"));

    TypedVar aVar = checkNotNull(getLabeledStatement("A").enclosingScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testArrayPatternParameter_withNamedType() {
    testSame(
        lines(
            "/**",
            " * @interface",
            " * @extends {Iterable<T>}",
            " * @template T",
            " */",
            "function TemplatizedClass() {",
            "}",
            "/** @interface @extends {TemplatizedClass<number>} */",
            "class ASubclass {}",
            "/** @param {!ASubclass} obj */ function f([a]) { A: a; }",
            ""));

    // Verify we get the correct type for 'a' after name resolution is complete
    TypedVar aVar = checkNotNull(getLabeledStatement("A").enclosingScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testObjectPatternParameter_withNamedType_andRest() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    testSame(
        lines(
            "class SomeUnknownName {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.data;",
            "  }",
            "}",
            "/** @param {!SomeUnknownName} obj */ function f({...rest}) { REST: rest; }",
            ""));

    TypedVar aVar = checkNotNull(getLabeledStatement("REST").enclosingScope.getVar("rest"));
    assertType(aVar.getType()).toStringIsEqualTo("Object");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testMixedParametersWithFullJSDoc() {
    testSame(
        lines(
            "/**",
            " * @param {string} x",
            " * @param {{a: !Iterable<number>}} obj",
            " * @param {!Iterable<{z: null}>} arr",
            " */",
            "function f(x, {a: [y]}, [{z}]) {}"));

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType())
        .toStringIsEqualTo(
            "function(string, {a: Iterable<number>}, Iterable<{z: null}>): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("string");
    assertThat(xVar.isTypeInferred()).isFalse();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("number");
    assertThat(yVar.isTypeInferred()).isFalse();

    TypedVar zVar = checkNotNull(lastFunctionScope.getVar("z"));
    assertType(zVar.getType()).toStringIsEqualTo("null");
    assertThat(zVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testObjectPatternParameterWithComputedPropertyWithFullJSDoc() {
    testSame(
        lines(
            "/**",
            " * @param {string} x",
            " * @param {!Object<string, number>} arr",
            " */",
            "function f(x, {['foobar' + 3]: a}) {}"));

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("string");
    assertThat(xVar.isTypeInferred()).isFalse();

    TypedVar aVar = checkNotNull(lastFunctionScope.getVar("a"));
    assertType(aVar.getType()).toStringIsEqualTo("number");
    assertThat(aVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testDestructuringParametersInIifeInfersType_withNameArguments() {
    testSame(
        lines(
            "const /** {x:  number} */ data = {x: 3}; ",
            "const /** !Iterable<string> */ strings = ['foo', 'bar'];",
            "",
            "(function ({x}, [y]) {})(data, strings);"));

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isTrue();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("string");
    assertThat(yVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testDestructuringParametersInIifeInfersType_withLiteralArguments() {
    testSame("(function ({x}) {})({x: 3});");

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testDestructuringParametersInIifeInfersType_withLiteralArgumentsAndDefaultValue() {
    testSame("(function ({x = 'bar' + 'baz'}) {})({x: true ? 3 : undefined});");

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("(number|string)");
    assertThat(xVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testDestructuringParametersInCallbackInfersType() {
    testSame(
        lines(
            "function f(/** function({x: number}, !Iterable<string>) */ callback) {}",
            "",
            "f(function ({x}, [y]) {});"));

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isTrue();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("string");
    assertThat(yVar.isTypeInferred()).isTrue();
  }

  @Test
  public void testOutOfOrderJSDocForDestructuringParameter() {
    // Even though regular JSDoc parameters can be out of order, there's currently no way for
    // putting arbitrary orders on destructuring parameters.
    testWarning(
        lines(
            "/**",
            " * @param {!Iterable<number>} arr",
            " * @param {string} x",
            " */",
            "function f(x, [y, z]) {}"),
        FunctionTypeBuilder.INEXISTENT_PARAM);

    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    // TODO(b/77597706): it would make more sense for this to be function(string, ?) instead
    assertType(fVar.getType()).toStringIsEqualTo("function(string, string): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testObjectPatternParameterWithInlineJSDoc() {
    testSame("function f({/** number */ x}) {}");

    // TODO(b/112651122): infer that f takes {x: number}
    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(?): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testArrayPatternParameterWithInlineJSDoc() {
    testSame("function f([/** number */ x]) {}");

    // TODO(b/112651122): either forbid this case or infer that f takes an !Iterable<number>
    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(?): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testArrayPatternParametersWithDifferingInlineJSDoc() {
    testSame("function f([/** number */ x, /** string */ y]) {}");

    // TODO(b/112651122): forbid this case, as there's not a good way to type the function without
    // having tuple types.
    TypedVar fVar = checkNotNull(globalScope.getVar("f"));
    assertType(fVar.getType()).toStringIsEqualTo("function(?): undefined");
    assertThat(fVar.isTypeInferred()).isFalse();

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("number");
    assertThat(xVar.isTypeInferred()).isFalse();

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("string");
    assertThat(yVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testObjectPatternCanAliasUnionTypeProperty() {
    // tests that we can get properties off the union type `{objA|objB}` even though it's not
    // represented as an ObjectType. (this used to crash in TypedScopeCreator)
    testSame(
        lines(
            "const ns = {}; ",
            "",
            "/** @typedef {{propA: number}} */",
            "let objA;",
            "/** @typedef {{propB: string}} */",
            "let objB;",
            "",
            "/** @type {objA|objB} */",
            "ns.ctor;",
            "",
            "const {propA, propB} = ns.ctor;"));

    TypedVar propAVar = checkNotNull(globalScope.getVar("propA"));
    assertType(propAVar.getType()).isEqualTo(getNativeType(JSTypeNative.NUMBER_TYPE));
    assertThat(propAVar.isTypeInferred()).isFalse();

    TypedVar propBVar = checkNotNull(globalScope.getVar("propB"));
    assertType(propBVar.getType()).isEqualTo(getNativeType(JSTypeNative.STRING_TYPE));
    assertThat(propBVar.isTypeInferred()).isFalse();
  }

  @Test
  public void testConstNestedObjectPatternInArrayPattern() {
    // regression test for a TypedScopeCreator crash
    disableTypeInfoValidation();
    testSame(
        lines(
            "const /** !Array<{b: string}> */ a = [{b: 'bbb'}];", //
            "const [{b}] = a;"));

    TypedVar b = checkNotNull(globalScope.getVar("b"));
    assertType(b.getType()).isUnknown();
    assertThat(b.isTypeInferred()).isTrue(); // b is inferred but not treated as declared
  }

  @Test
  public void testConstNestedObjectPatternWithComputedPropertyIsUnknown() {
    // regression test for a TypedScopeCreator crash
    disableTypeInfoValidation(); // fails on a['foo'].b = function() {};
    testSame(
        lines(
            "const a = {};",
            "/** @const */ a['foo'] = {};",
            "/** @const */ a['foo'].b = function() {};",
            "",
            "const {['foo']: {b}} = a;"));

    TypedVar b = checkNotNull(globalScope.getVar("b"));
    assertType(b.getType()).isUnknown();
    assertThat(b.isTypeInferred()).isTrue();
  }

  @Test
  public void testStubProperty() {
    testSame("function Foo() {}; Foo.bar;");
    ObjectType foo = (ObjectType) globalScope.getVar("Foo").getType();
    assertThat(foo.hasProperty("bar")).isFalse();
    assertType(foo.getPropertyType("bar"))
        .isStructurallyEqualTo(registry.getNativeType(UNKNOWN_TYPE));
  }

  @Test
  public void testConstructorProperty() {
    testSame("var foo = {}; /** @constructor */ foo.Bar = function() {};");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertThat(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();

    JSType fooBar = foo.getPropertyType("Bar");
    assertThat(fooBar.toString()).isEqualTo("function(new:foo.Bar): undefined");
  }

  @Test
  public void testPrototypePropertyMethodWithoutAnnotation() {
    testSame("var Foo = function Foo() {};"
        + "var proto = Foo.prototype = {"
        + "   bar: function(a, b){}"
        + "};"
        + "proto.baz = function(c) {};"
        + "(function() { proto.baz = function() {}; })();");
    ObjectType foo = (ObjectType) findNameType("Foo", globalScope);
    assertThat(foo.hasProperty("prototype")).isTrue();

    ObjectType fooProto = (ObjectType) foo.getPropertyType("prototype");
    assertThat(fooProto.hasProperty("bar")).isTrue();
    assertThat(fooProto.getPropertyType("bar").toString()).isEqualTo("function(?, ?): undefined");

    assertThat(fooProto.hasProperty("baz")).isTrue();
    assertThat(fooProto.getPropertyType("baz").toString()).isEqualTo("function(?): undefined");
  }

  @Test
  public void testEnumProperty() {
    testSame("var foo = {}; /** @enum */ foo.Bar = {XXX: 'xxx'};");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertThat(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
    assertThat(foo.isPropertyTypeDeclared("Bar")).isTrue();

    JSType fooBar = foo.getPropertyType("Bar");
    assertThat(fooBar.toString()).isEqualTo("enum{foo.Bar}");
  }

  @Test
  public void testInferredProperty1() {
    testSame("var foo = {}; foo.Bar = 3;");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isTrue();
  }

  @Test
  public void testInferredProperty1a() {
    testSame("var foo = {}; /** @type {number} */ foo.Bar = 3;");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
  }

  @Test
  public void testInferredProperty2() {
    testSame("var foo = { Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isTrue();
  }

  @Test
  public void testInferredProperty2b() {
    testSame("var foo = { /** @type {number} */ Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
  }

  @Test
  public void testInferredProperty2c() {
    testSame("var foo = { /** @return {number} */ Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("function(): number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
  }

  @Test
  public void testInferredProperty3() {
    testSame("var foo = { /** @type {number} */ get Bar() { return 3 } };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("?");
    assertThat(foo.isPropertyTypeInferred("Bar")).isTrue();
  }

  @Test
  public void testInferredProperty4() {
    testSame("var foo = { /** @type {number} */ set Bar(a) {} };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("?");
    assertThat(foo.isPropertyTypeInferred("Bar")).isTrue();
  }

  @Test
  public void testInferredProperty5() {
    testSame("var foo = { /** @return {number} */ get Bar() { return 3 } };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
  }

  @Test
  public void testInferredProperty6() {
    testSame("var foo = { /** @param {number} a */ set Bar(a) {} };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertWithMessage(foo.toString()).that(foo.hasProperty("Bar")).isTrue();
    assertThat(foo.getPropertyType("Bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("Bar")).isFalse();
  }

  @Test
  public void testPrototypeInit() {
    testSame("/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = {bar: 1}; var foo = new Foo();");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertThat(foo.hasProperty("bar")).isTrue();
    assertThat(foo.getPropertyType("bar").toString()).isEqualTo("number");
    assertThat(foo.isPropertyTypeInferred("bar")).isTrue();
  }

  @Test
  public void testBogusPrototypeInit() {
    // This used to cause a compiler crash.
    testSame("/** @const */ var goog = {}; "
        + "goog.F = {}; /** @const */ goog.F.prototype = {};"
        + "/** @constructor */ goog.F = function() {};");
  }

  @Test
  public void testInferredPrototypeProperty1() {
    testSame("/** @constructor */ var Foo = function() {};"
        + "Foo.prototype.bar = 1; var x = new Foo();");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.hasProperty("bar")).isTrue();
    assertThat(x.getPropertyType("bar").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("bar")).isTrue();
  }

  @Test
  public void testInferredPrototypeProperty2() {
    testSame("/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = {bar: 1}; var x = new Foo();");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.hasProperty("bar")).isTrue();
    assertThat(x.getPropertyType("bar").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("bar")).isTrue();
  }

  @Test
  public void testEnum() {
    testSame("/** @enum */ var Foo = {BAR: 1}; var f = Foo;");
    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertThat(f.hasProperty("BAR")).isTrue();
    assertType(f.getPropertyType("BAR")).toStringIsEqualTo("Foo<number>");
    assertThat(f).isInstanceOf(EnumType.class);
  }

  @Test
  public void testLetEnum() {
    testSame("/** @enum */ let Foo = {BAR: 1}; let f = Foo;");
    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertThat(f.hasProperty("BAR")).isTrue();
    assertType(f.getPropertyType("BAR")).toStringIsEqualTo("Foo<number>");
    assertThat(f).isInstanceOf(EnumType.class);
  }

  @Test
  public void testConstEnum() {
    testSame("/** @enum */ const Foo = {BAR: 1}; const f = Foo;");
    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertThat(f.hasProperty("BAR")).isTrue();
    assertType(f.getPropertyType("BAR")).toStringIsEqualTo("Foo<number>");
    assertThat(f).isInstanceOf(EnumType.class);
  }

  @Test
  public void testEnumElement() {
    testSame("/** @enum */ var Foo = {BAR: 1}; var f = Foo;");
    TypedVar bar = globalScope.getVar("Foo.BAR");
    assertThat(bar).isNotNull();
    assertType(bar.getType()).toStringIsEqualTo("Foo<number>");
  }

  @Test
  public void testLetEnumElement() {
    testSame("/** @enum */ let Foo = {BAR: 1}; let f = Foo;");
    TypedVar bar = globalScope.getVar("Foo.BAR");
    assertThat(bar).isNotNull();
    assertType(bar.getType()).toStringIsEqualTo("Foo<number>");
  }

  @Test
  public void testConstEnumElement() {
    testSame("/** @enum */ const Foo = {BAR: 1}; const f = Foo;");
    TypedVar bar = globalScope.getVar("Foo.BAR");
    assertThat(bar).isNotNull();
    assertType(bar.getType()).toStringIsEqualTo("Foo<number>");
  }

  @Test
  public void testNamespacedEnum() {
    testSame("var goog = {}; goog.ui = {};"
        + "/** @constructor */goog.ui.Zippy = function() {};"
        + "/** @enum{string} */goog.ui.Zippy.EventType = { TOGGLE: 'toggle' };"
        + "var x = goog.ui.Zippy.EventType;"
        + "var y = goog.ui.Zippy.EventType.TOGGLE;");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.isEnumType()).isTrue();
    assertThat(x.hasProperty("TOGGLE")).isTrue();
    assertThat(x.getReferenceName()).isEqualTo("enum{goog.ui.Zippy.EventType}");

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertThat(y.isSubtypeOf(getNativeType(STRING_TYPE))).isTrue();
    assertThat(y.isEnumElementType()).isTrue();
    assertThat(y.getReferenceName()).isEqualTo("goog.ui.Zippy.EventType");
  }

  @Test
  public void testGlobalTypedefs1() {
    testSame(
        lines(
            "", // preserve newlines
            "/** @typedef {number} */",
            "var VarTypedef;",
            "{",
            "  /** @typedef {number} */",
            "  var VarTypedefInBlock;", // still global despite enclosing block
            "}",
            "/** @typedef {number} */",
            "let LetTypedef;",
            // TODO(bradfordcsmith): We should probably disallow @typedef on const.
            "/** @typedef {number} */",
            "const ConstTypedef = undefined;",
            ""));
    assertType(registry.getGlobalType("VarTypedef")).isNumber();
    assertType(registry.getGlobalType("VarTypedefInBlock")).isNumber();
    assertType(registry.getGlobalType("LetTypedef")).isNumber();
    assertType(registry.getGlobalType("ConstTypedef")).isNumber();
  }

  @Test
  public void testGlobalTypedefs2() {
    testSame(
        lines(
            "", // preserve newlines
            "{",
            "  /** @typedef {number} */",
            "  var VarTypedefInBlock;", // still global despite enclosing block
            "}",
            ""));
  }

  @Test
  public void testLocalTypedefs1() {
    testSame(
        lines(
            "", // preserve newlines
            "function f() {",
            "  /** @typedef {number} */",
            "  var VarTypedefInFunc;",
            "  /** @typedef {number} */",
            "  let LetTypedefInFunc;",
            "  /** @typedef {number} */",
            // TODO(bradfordcsmith): We should probably disallow @typedef on const.
            "  const ConstTypedefInFunc = undefined;",
            "}",
            ""));
    TypedScope fnRoot = lastLocalScope;
    assertType(registry.getType(fnRoot, "VarTypedefInFunc")).isNumber();
    assertType(registry.getType(fnRoot, "LetTypedefInFunc")).isNumber();
    assertType(registry.getType(fnRoot, "ConstTypedefInFunc")).isNumber();
  }

  @Test
  public void testLocalTypedefs2() {
    testSame(
        lines(
            "", // preserve newlines
            "{",
            "  /** @typedef {number} */",
            "  let LetTypedefInBlock;",
            "  /** @typedef {number} */",
            "  const ConstTypedefInBlock = undefined;",
            "}",
            ""));
    TypedScope fnRoot = lastLocalScope;

    assertType(registry.getType(fnRoot, "LetTypedefInBlock")).isNumber();
    assertType(registry.getType(fnRoot, "ConstTypedefInBlock")).isNumber();
  }

  @Test
  public void testEnumAlias() {
    testSame("/** @enum */ var Foo = {BAR: 1}; " +
        "/** @enum */ var FooAlias = Foo; var f = FooAlias;");

    assertThat(registry.getType(null, "FooAlias").toString()).isEqualTo("Foo<number>");
    assertType(registry.getType(null, "Foo"))
        .isStructurallyEqualTo(registry.getType(null, "FooAlias"));

    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertThat(f.hasProperty("BAR")).isTrue();
    assertThat(f.getPropertyType("BAR").toString()).isEqualTo("Foo<number>");
    assertThat(f).isInstanceOf(EnumType.class);
  }

  @Test
  public void testNamespacesEnumAlias() {
    testSame("var goog = {}; /** @enum */ goog.Foo = {BAR: 1}; " +
        "/** @enum */ goog.FooAlias = goog.Foo;");

    assertThat(registry.getType(null, "goog.FooAlias").toString()).isEqualTo("goog.Foo<number>");
    assertType(registry.getType(null, "goog.FooAlias"))
        .isStructurallyEqualTo(registry.getType(null, "goog.Foo"));
  }

  @Test
  public void testCollectedFunctionStub() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("function(this:f): number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
  }

  @Test
  public void testCollectedFunctionStubLocal() {
    testSame(
        "(function() {" +
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}" +
        "var x = new f();" +
        "});");
    ObjectType x = (ObjectType) findNameType("x", lastLocalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("function(this:f): number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
  }

  @Test
  public void testNamespacedFunctionStub() {
    testSame(
        "var goog = {};" +
        "/** @param {number} x */ goog.foo;");

    ObjectType goog = (ObjectType) findNameType("goog", globalScope);
    assertThat(goog.hasProperty("foo")).isTrue();
    assertThat(goog.getPropertyType("foo").toString()).isEqualTo("function(number): ?");
    assertThat(goog.isPropertyTypeDeclared("foo")).isTrue();

    assertType(goog.getPropertyType("foo"))
        .isStructurallyEqualTo(globalScope.getVar("goog.foo").getType());
  }

  @Test
  public void testNamespacedFunctionStubLocal() {
    testSame(
        "(function() {" +
        "var goog = {};" +
        "/** @param {number} x */ goog.foo;" +
        "});");

    ObjectType goog = (ObjectType) findNameType("goog", lastLocalScope);
    assertThat(goog.hasProperty("foo")).isTrue();
    assertThat(goog.getPropertyType("foo").toString()).isEqualTo("function(number): ?");
    assertThat(goog.isPropertyTypeDeclared("foo")).isTrue();

    assertType(goog.getPropertyType("foo"))
        .isStructurallyEqualTo(lastLocalScope.getVar("goog.foo").getType());
  }

  @Test
  public void testCollectedCtorProperty1() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @type {number} */ this.foo = 3;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty2() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @const {number} */ this.foo = 3;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty3() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @const */ this.foo = 3;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty5() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @const */ this.foo = 'abc' + 'def';" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("string");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty9() {
    testSame(
        "/** @constructor */ function f() {}\n" +
        "f.prototype.init_f = function() {" +
        "  /** @const */ this.FOO = 'abc';" +
        "};" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("FOO")).isTrue();
    assertThat(x.getPropertyType("FOO").toString()).isEqualTo("string");
    assertThat(x.isPropertyTypeInferred("FOO")).isFalse();
    assertThat(x.isPropertyTypeDeclared("FOO")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty10() {
    testSame(
        "/** @constructor */ function f() {}\n" +
        "f.prototype.init_f = function() {" +
        "  /** @const */ this.foo = new String();" +
        "};" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("String");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty11() {
    testSame(
        "/** @constructor */ function f() {}\n" +
        "f.prototype.init_f = function() {" +
        "  /** @const */ this.foo = [];" +
        "};" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("Array");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty12() {
    testSame(
        externs("/** @type {?} */ let unknown;"),
        srcs(
            lines(
                "/** @constructor */ function f() {}",
                "f.prototype.init_f = function() {",
                "  /** @const */ this.foo = !!unknown;",
                "};",
                "var x = new f();")));
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("boolean");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty13() {
    testSame(
        externs("/** @type {?} */ let unknown;"),
        srcs(
            lines(
                "/** @constructor */ function f() {}",
                "f.prototype.init_f = function() {",
                "  /** @const */ this.foo = +unknown;",
                "};",
                "var x = new f();")));
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty14() {
    testSame(
        externs("/** @type {?} */ let unknown;"),
        srcs(
            lines(
                "/** @constructor */ function f() {}",
                "f.prototype.init_f = function() {",
                "  /** @const */ this.foo = unknown + '';",
                "};",
                "var x = new f();")));
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("string");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testCollectedCtorProperty15() {
    testSame(
        "/** " +
        " * @constructor\n" +
        " * @param {string} a\n" +
        " */\n" +
        " function f(a) {" +
        "  /** @const */ this.foo = a;" +
        "};" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("f");
    assertThat(x.hasProperty("foo")).isTrue();
    assertThat(x.getPropertyType("foo").toString()).isEqualTo("string");
    assertThat(x.isPropertyTypeInferred("foo")).isFalse();
    assertThat(x.isPropertyTypeDeclared("foo")).isTrue();
  }

  @Test
  public void testPropertyOnUnknownSuperClass1() {
    testWarning(
        "var goog = this.foo();"
            + "/** @constructor \n * @extends {goog.Unknown} */"
            + "function Foo() {}"
            + "Foo.prototype.bar = 1;"
            + "var x = new Foo();",
        RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("Foo");
    assertThat(x.getImplicitPrototype().hasOwnProperty("bar")).isTrue();
    assertThat(x.getPropertyType("bar").toString()).isEqualTo("?");
    assertThat(x.isPropertyTypeInferred("bar")).isTrue();
  }

  @Test
  public void testPropertyOnUnknownSuperClass2() {
    testWarning(
        "var goog = this.foo();"
            + "/** @constructor \n * @extends {goog.Unknown} */"
            + "function Foo() {}"
            + "Foo.prototype = {bar: 1};"
            + "var x = new Foo();",
        RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("Foo");
    assertThat(x.getImplicitPrototype().toString()).isEqualTo("Foo.prototype");
    assertThat(x.getImplicitPrototype().hasOwnProperty("bar")).isTrue();
    assertThat(x.getPropertyType("bar").toString()).isEqualTo("?");
    assertThat(x.isPropertyTypeInferred("bar")).isTrue();
  }

  @Test
  public void testSubBeforeSuper1() {
    testSame(
        "/** @interface\n * @extends {MidI} */" +
        "function LowI() {}" +
        "/** @interface\n * @extends {HighI} */" +
        "function MidI() {}" +
        "/** @interface */" +
        "function HighI() {}");
  }

  @Test
  public void testSubBeforeSuper2() {
    testSame(
        "/** @constructor\n * @extends {MidI} */" +
        "function LowI() {}" +
        "/** @constructor\n * @extends {HighI} */" +
        "function MidI() {}" +
        "/** @constructor */" +
        "function HighI() {}");
  }

  @Test
  public void testMethodBeforeFunction1() {
    testSame(
        "var y = Window.prototype;" +
        "Window.prototype.alert = function(message) {};" +
        "/** @constructor */ function Window() {}\n" +
        "var window = new Window(); \n" +
        "var x = window;");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("Window");
    assertThat(x.getImplicitPrototype().hasOwnProperty("alert")).isTrue();
    assertThat(x.getPropertyType("alert").toString())
        .isEqualTo("function(this:Window, ?): undefined");
    assertThat(x.isPropertyTypeDeclared("alert")).isTrue();

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertThat(y.getPropertyType("alert").toString())
        .isEqualTo("function(this:Window, ?): undefined");
  }

  @Test
  public void testMethodBeforeFunction2() {
    testSame(
        "var y = Window.prototype;" +
        "Window.prototype = {alert: function(message) {}};" +
        "/** @constructor */ function Window() {}\n" +
        "var window = new Window(); \n" +
        "var x = window;");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertThat(x.toString()).isEqualTo("Window");
    assertThat(x.getImplicitPrototype().hasOwnProperty("alert")).isTrue();
    assertThat(x.getPropertyType("alert").toString())
        .isEqualTo("function(this:Window, ?): undefined");
    assertThat(x.isPropertyTypeDeclared("alert")).isFalse();

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertThat(y.getPropertyType("alert").toString())
        .isEqualTo("function(this:Window, ?): undefined");
  }

  @Test
  public void testAddMethodsPrototypeTwoWays() {
    testSame(
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true};" +
        "A.prototype.m3 = 'third property!';" +
        "var x = new A();");

    ObjectType instanceType = (ObjectType) findNameType("x", globalScope);
    assertThat(instanceType.getPropertiesCount())
        .isEqualTo(getNativeObjectType(OBJECT_TYPE).getPropertiesCount() + 3);
    assertType(instanceType.getPropertyType("m1"))
        .isStructurallyEqualTo(getNativeType(NUMBER_TYPE));
    assertType(instanceType.getPropertyType("m2"))
        .isStructurallyEqualTo(getNativeType(BOOLEAN_TYPE));
    assertType(instanceType.getPropertyType("m3"))
        .isStructurallyEqualTo(getNativeType(STRING_TYPE));

    // Verify the prototype chain.
    // This is a special case where we want the anonymous object to
    // become a prototype.
    assertThat(instanceType.hasOwnProperty("m1")).isFalse();
    assertThat(instanceType.hasOwnProperty("m2")).isFalse();
    assertThat(instanceType.hasOwnProperty("m3")).isFalse();

    ObjectType proto1 = instanceType.getImplicitPrototype();
    assertThat(proto1.hasOwnProperty("m1")).isTrue();
    assertThat(proto1.hasOwnProperty("m2")).isTrue();
    assertThat(proto1.hasOwnProperty("m3")).isTrue();

    ObjectType proto2 = proto1.getImplicitPrototype();
    assertThat(proto2.hasProperty("m1")).isFalse();
    assertThat(proto2.hasProperty("m2")).isFalse();
    assertThat(proto2.hasProperty("m3")).isFalse();
  }

  @Test
  public void testInferredVar() {
    testSame("var x = 3; x = 'x'; x = true;");

    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).toStringIsEqualTo("(boolean|number|string)");
    assertThat(x.isTypeInferred()).isTrue();
  }

  @Test
  public void testInferredLet() {
    testSame("let x = 3; x = 'x'; x = true;");

    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).toStringIsEqualTo("(boolean|number|string)");
    assertThat(x.isTypeInferred()).isTrue();
  }

  @Test
  public void testInferredConst() {
    testSame("const x = 3;");

    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).isNumber();
    assertThat(x.isConst()).isTrue();
    // Although we did infer the type, we'll consider it effectively declared because the variable
    // was declared to be constant. This is consistent with the way we handle the @const annotation
    // on var declarations.
    assertThat(x.isTypeInferred()).isFalse();
  }

  @Test
  public void testInferredAnnotatedConst() {
    testSame("/** @const */ var x = 3;");

    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).isNumber();
    assertThat(x.isConst()).isFalse();
    assertThat(x.isTypeInferred()).isFalse();
  }

  @Test
  public void testDeclaredVar() {
    testSame("/** @type {?number} */ var x = 3; var y = x;");

    assertScope(globalScope).declares("x").directly();
    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).toStringIsEqualTo("(null|number)");
    assertThat(x.isTypeInferred()).isFalse();

    JSType y = findNameType("y", globalScope);
    assertThat(y.toString()).isEqualTo("(null|number)");
  }

  @Test
  public void testDeclaredLet() {
    testSame("/** @type {?number} */ let x = 3; let y = x;");

    assertScope(globalScope).declares("x").directly();
    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).toStringIsEqualTo("(null|number)");
    assertThat(x.isTypeInferred()).isFalse();

    JSType y = findNameType("y", globalScope);
    assertThat(y.toString()).isEqualTo("(null|number)");
  }

  @Test
  public void testDeclaredConst() {
    testSame("/** @type {?number} */ const x = 3; const y = x;");

    assertScope(globalScope).declares("x").directly();
    TypedVar x = globalScope.getVar("x");
    assertType(x.getType()).toStringIsEqualTo("(null|number)");
    assertThat(x.isTypeInferred()).isFalse();
    assertThat(x.isConst()).isTrue();

    JSType y = findNameType("y", globalScope);
    assertThat(y.toString()).isEqualTo("(null|number)");
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface1() {
    testSame("/** @record */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "I.prototype.baz = function(){};");

    TypedVar i = globalScope.getVar("I");
    assertThat(i.getType().toString()).isEqualTo("function(this:I): ?");
    assertThat(i.getType().isInterface()).isTrue();
    assertThat(i.getType().isFunctionType()).isTrue();
    assertThat(i.getType().toMaybeFunctionType().isStructuralInterface()).isTrue();
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface2() {
    testSame("/** @interface */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "I.prototype.baz = function(){};");

    TypedVar i = globalScope.getVar("I");
    assertThat(i.getType().toString()).isEqualTo("function(this:I): ?");
    assertThat(i.getType().isInterface()).isTrue();
    assertThat(i.getType().isFunctionType()).isTrue();
    assertThat(i.getType().toMaybeFunctionType().isStructuralInterface()).isFalse();
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface3() {
    testSame("/** @interface */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "/** @record */ I.prototype.baz = function() {};");

    TypedVar baz = globalScope.getVar("I.prototype.baz");
    assertThat(baz.getType().isInterface()).isTrue();
    assertThat(baz.getType().isFunctionType()).isTrue();
    assertThat(baz.getType().toMaybeFunctionType().isStructuralInterface()).isTrue();
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface4() {
    testSame("/** @interface */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "/** @interface */ I.prototype.baz = function() {};");

    TypedVar baz = globalScope.getVar("I.prototype.baz");
    assertThat(baz.getType().isInterface()).isTrue();
    assertThat(baz.getType().isFunctionType()).isTrue();
    assertThat(baz.getType().toMaybeFunctionType().isStructuralInterface()).isFalse();
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface5() {
    testSame("/** @constructor */ var C = function() {};" +
        "/** @type {number} */ C.prototype.bar;" +
        "/** @record */ C.prototype.baz = function() {};" +
        "var c = new C(); var cbaz = c.baz;");

    TypedVar cBaz = globalScope.getVar("cbaz");
    assertThat(cBaz.getType().isFunctionType()).isTrue();
    assertThat(cBaz.getType().toMaybeFunctionType().isStructuralInterface()).isTrue();
  }

  @Test
  public void testStructuralInterfaceMatchingOnInterface6() {
    testSame("/** @constructor */ var C = function() {};" +
        "/** @type {number} */ C.prototype.bar;" +
        "/** @interface */ C.prototype.baz = function() {};" +
        "var c = new C(); var cbaz = c.baz;");

    TypedVar cBaz = globalScope.getVar("cbaz");
    assertThat(cBaz.getType().isFunctionType()).isTrue();
    assertThat(cBaz.getType().toMaybeFunctionType().isStructuralInterface()).isFalse();
  }

  @Test
  public void testPropertiesOnInterface() {
    testSame("/** @interface */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "I.prototype.baz = function(){};");

    TypedVar i = globalScope.getVar("I");
    assertThat(i.getType().toString()).isEqualTo("function(this:I): ?");
    assertThat(i.getType().isInterface()).isTrue();

    ObjectType iPrototype = (ObjectType)
        ((ObjectType) i.getType()).getPropertyType("prototype");
    assertThat(iPrototype.toString()).isEqualTo("I.prototype");
    assertThat(iPrototype.isFunctionPrototypeType()).isTrue();

    assertThat(iPrototype.getPropertyType("bar").toString()).isEqualTo("number");
    assertThat(iPrototype.getPropertyType("baz").toString())
        .isEqualTo("function(this:I): undefined");

    assertType(globalScope.getVar("I.prototype").getType()).isStructurallyEqualTo(iPrototype);
  }

  @Test
  public void testPropertiesOnInterface2() {
    testSame("/** @interface */ var I = function() {};" +
        "I.prototype = {baz: function(){}};" +
        "/** @type {number} */ I.prototype.bar;");

    TypedVar i = globalScope.getVar("I");
    assertThat(i.getType().toString()).isEqualTo("function(this:I): ?");
    assertThat(i.getType().isInterface()).isTrue();

    ObjectType iPrototype = (ObjectType)
        ((ObjectType) i.getType()).getPropertyType("prototype");
    assertThat(iPrototype.toString()).isEqualTo("I.prototype");
    assertThat(iPrototype.isFunctionPrototypeType()).isTrue();

    assertThat(iPrototype.getPropertyType("bar").toString()).isEqualTo("number");

    assertThat(iPrototype.getPropertyType("baz").toString())
        .isEqualTo("function(this:I): undefined");

    assertThat(globalScope.getVar("I.prototype").getType()).isEqualTo(iPrototype);
  }

  // TODO(johnlenz): A syntax for stubs using object literals?

  @Test
  public void testStubsInExterns() {
    testSame(
        externs(
            "/** @constructor */ function Extern() {}"
                + "Extern.prototype.bar;"
                + "var e = new Extern(); e.baz;"),
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar;"
                + "var f = new Foo(); f.baz;"));

    ObjectType e = (ObjectType) globalScope.getVar("e").getType();
    assertThat(e.getPropertyType("bar").toString()).isEqualTo("?");
    assertThat(e.getPropertyType("baz").toString()).isEqualTo("?");

    ObjectType f = (ObjectType) globalScope.getVar("f").getType();
    assertThat(f.getPropertyType("bar").toString()).isEqualTo("?");
    assertThat(f.hasProperty("baz")).isFalse();
  }

  @Test
  public void testStubsInExterns2() {
    testSame(
        externs(
            "/** @constructor */ function Extern() {}"
                + "/** @type {Extern} */ var myExtern;"
                + "/** @type {number} */ myExtern.foo;"),
        srcs(""));

    JSType e = globalScope.getVar("myExtern").getType();
    assertThat(e.toString()).isEqualTo("(Extern|null)");

    ObjectType externType = (ObjectType) e.restrictByNotNullOrUndefined();
    assertWithMessage(globalScope.getRootNode().toStringTree())
        .that(externType.hasOwnProperty("foo"))
        .isTrue();
    assertThat(externType.isPropertyTypeDeclared("foo")).isTrue();
    assertThat(externType.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(externType.isPropertyInExterns("foo")).isTrue();
  }

  @Test
  public void testStubsInExterns3() {
    testSame(
        externs(
            "/** @type {number} */ myExtern.foo;"
                + "/** @type {Extern} */ var myExtern;"
                + "/** @constructor */ function Extern() {}"),
        srcs(""));

    JSType e = globalScope.getVar("myExtern").getType();
    assertThat(e.toString()).isEqualTo("(Extern|null)");

    ObjectType externType = (ObjectType) e.restrictByNotNullOrUndefined();
    assertWithMessage(globalScope.getRootNode().toStringTree())
        .that(externType.hasOwnProperty("foo"))
        .isTrue();
    assertThat(externType.isPropertyTypeDeclared("foo")).isTrue();
    assertThat(externType.getPropertyType("foo").toString()).isEqualTo("number");
    assertThat(externType.isPropertyInExterns("foo")).isTrue();
  }

  @Test
  public void testStubsInExterns4() {
    testSame(
        externs("Extern.prototype.foo;" + "/** @constructor */ function Extern() {}"), srcs(""));

    JSType e = globalScope.getVar("Extern").getType();
    assertThat(e.toString()).isEqualTo("function(new:Extern): ?");

    ObjectType externProto = ((FunctionType) e).getPrototype();
    assertWithMessage(globalScope.getRootNode().toStringTree())
        .that(externProto.hasOwnProperty("foo"))
        .isTrue();
    assertThat(externProto.isPropertyTypeInferred("foo")).isTrue();
    assertThat(externProto.getPropertyType("foo").toString()).isEqualTo("?");
    assertThat(externProto.isPropertyInExterns("foo")).isTrue();
  }

  @Test
  public void testPropertyInExterns1() {
    // Declaring a property on a non-native extern type (e.g. 'Extern') declares it as a property
    // on the instance type, but only in externs.
    testSame(
        externs(
            lines(
                "/** @constructor */ function Extern() {}",
                "/** @type {Extern} */ var extern;",
                "/** @return {number} */ extern.one;")),
        srcs(
            lines(
                "/** @constructor */ function Normal() {}",
                "/** @type {Normal} */ var normal;",
                "/** @return {number} */ normal.one;",
                "var result = new Extern().one();")));

    JSType e = globalScope.getVar("Extern").getType();
    ObjectType externInstance = ((FunctionType) e).getInstanceType();
    assertThat(externInstance.hasOwnProperty("one")).isTrue();
    assertThat(externInstance.isPropertyTypeDeclared("one")).isTrue();
    assertThat(externInstance.getPropertyType("one").toString()).isEqualTo("function(): number");
    assertThat(globalScope.getVar("result").getType().toString()).isEqualTo("number");

    JSType n = globalScope.getVar("Normal").getType();
    ObjectType normalInstance = ((FunctionType) n).getInstanceType();
    assertThat(normalInstance.hasOwnProperty("one")).isFalse();
  }

  @Test
  public void testPropertyInExterns2() {
    // Native extern types (such as Object) do not get stray properties declared, since this would
    // cause problems with bad externs (such as `/** @type {!Object} */ var api = {}; api.foo;`,
    // where we don't want to declare that all Objects have a "foo" property.  Nevertheless, the
    // specific qualified name (i.e. extern.one, in the example below) is still declared on the
    // global scope, so referring to the "one" property specifically on "extern" is still checked
    // as one would expect.
    testSame(
        externs(
            lines(
                "/** @type {Object} */ var extern;", //
                "/** @return {number} */ extern.one;")),
        srcs(
            lines(
                "/** @type {Object} */ var normal;", //
                "/** @return {number} */ normal.one;",
                "var result = extern.one();")));

    JSType e = globalScope.getVar("extern").getType();
    assertThat(e.dereference().hasOwnProperty("one")).isFalse();
    assertThat(globalScope.getVar("result").getType().toString()).isEqualTo("number");

    JSType normal = globalScope.getVar("normal").getType();
    assertThat(normal.dereference().hasOwnProperty("one")).isFalse();
  }

  @Test
  public void testPropertyInExterns3() {
    testSame(
        externs(
            "/** @constructor \n * @param {*=} x @return {!Object} */"
                + "function Object(x) {}"
                + "/** @type {number} */ Object.one;"),
        srcs(""));

    ObjectType obj = globalScope.getVar("Object").getType().dereference();
    assertThat(obj.hasOwnProperty("one")).isTrue();
    assertThat(obj.getPropertyType("one").toString()).isEqualTo("number");
  }

  @Test
  public void testTypedStubsInExterns() {
    testSame(
        externs(
            "/** @constructor \n * @param {*} var_args */ "
                + "function Function(var_args) {}"
                + "/** @type {!Function} */ Function.prototype.apply;"),
        srcs("var f = new Function();"));

    ObjectType f = (ObjectType) globalScope.getVar("f").getType();

    // The type of apply() on a function instance is resolved dynamically,
    // since apply varies with the type of the function it's called on.
    assertThat(f.getPropertyType("apply").toString()).isEqualTo("function(?=, (Object|null)=): ?");

    // The type of apply() on the function prototype just takes what it was
    // declared with.
    FunctionType func = (FunctionType) globalScope.getVar("Function").getType();
    assertThat(func.getPrototype().getPropertyType("apply").toString()).isEqualTo("Function");
  }

  @Test
  public void testTypesInExterns() {
    testSame(externs(CompilerTypeTestCase.DEFAULT_EXTERNS), srcs(""));

    TypedVar v = globalScope.getVar("Object");
    FunctionType obj = (FunctionType) v.getType();
    assertThat(obj.toString()).isEqualTo("function(new:Object, *=): Object");
    assertThat(v.getNode()).isNotNull();
    assertThat(v.input).isNotNull();
  }

  @Test
  public void testPropertyDeclarationOnInstanceType() {
    testSame(
        "/** @type {!Object} */ var a = {};" +
        "/** @type {number} */ a.name = 0;");

    assertThat(globalScope.getVar("a.name").getType().toString()).isEqualTo("number");

    ObjectType a = (ObjectType) (globalScope.getVar("a").getType());
    assertThat(a.hasProperty("name")).isFalse();
    assertThat(getNativeObjectType(OBJECT_TYPE).hasProperty("name")).isFalse();
  }

  @Test
  public void testPropertyDeclarationOnRecordType() {
    testSame(
        "/** @type {{foo: number}} */ var a = {foo: 3};" +
        "/** @type {number} */ a.name = 0;");

    assertThat(globalScope.getVar("a.name").getType().toString()).isEqualTo("number");

    ObjectType a = (ObjectType) (globalScope.getVar("a").getType());
    assertThat(a.toString()).isEqualTo("{foo: number}");
    assertThat(a.hasProperty("name")).isFalse();
  }

  @Test
  public void testGlobalThis1() {
    testSame(
        "/** @constructor */ function Window() {}" +
        "Window.prototype.alert = function() {};" +
        "var x = this;");

    ObjectType x = (ObjectType) (globalScope.getVar("x").getType());
    FunctionType windowCtor =
        (FunctionType) (globalScope.getVar("Window").getType());
    assertThat(x.toString()).isEqualTo("global this");
    assertThat(x.isSubtypeOf(windowCtor.getInstanceType())).isTrue();
    assertThat(x.isEquivalentTo(windowCtor.getInstanceType())).isFalse();
    assertThat(x.hasProperty("alert")).isTrue();
  }

  @Test
  public void testGlobalThis2() {
    testSame(
        "/** @constructor */ function Window() {}" +
        "Window.prototype = {alert: function() {}};" +
        "var x = this;");

    ObjectType x = (ObjectType) (globalScope.getVar("x").getType());
    FunctionType windowCtor =
        (FunctionType) (globalScope.getVar("Window").getType());
    assertThat(x.toString()).isEqualTo("global this");
    assertThat(x.isSubtypeOf(windowCtor.getInstanceType())).isTrue();
    assertThat(x.isEquivalentTo(windowCtor.getInstanceType())).isFalse();
    assertThat(x.hasProperty("alert")).isTrue();
  }

  @Test
  public void testObjectLiteralCast() {
    // Verify that "goog.reflect.object" does not modify the types on
    // "A.B"
    testSame("/** @constructor */ A.B = function() {}\n" +
             "A.B.prototype.isEnabled = true;\n" +
             "goog.reflect.object(A.B, {isEnabled: 3})\n" +
             "var x = (new A.B()).isEnabled;");

    // Verify that "$jscomp.reflectObject" does not modify the types on
    // "A.B"
    testSame(
        "/** @constructor */ A.B = function() {}\n"
            + "A.B.prototype.isEnabled = true;\n"
            + "$jscomp.reflectObject(A.B, {isEnabled: 3})\n"
            + "var x = (new A.B()).isEnabled;");

    assertThat(findTokenType(Token.OBJECTLIT, globalScope).toString()).isEqualTo("A.B");
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("boolean");
  }

  @Test
  public void testBadObjectLiteralCast1() {
    testWarning(
        "/** @constructor */ A.B = function() {}\n" + "goog.reflect.object(A.B, 1)",
        ClosureCodingConvention.OBJECTLIT_EXPECTED);
  }

  @Test
  public void testBadObjectLiteralCast2() {
    testWarning("goog.reflect.object(A.B, {})", TypedScopeCreator.CONSTRUCTOR_EXPECTED);
  }

  @Test
  public void testConstructorNode() {
    testSame("var goog = {}; /** @constructor */ goog.Foo = function() {};");

    ObjectType ctor = (ObjectType) (findNameType("goog.Foo", globalScope));
    assertThat(ctor).isNotNull();
    assertThat(ctor.isConstructor()).isTrue();
    assertThat(ctor.toString()).isEqualTo("function(new:goog.Foo): undefined");
  }

  @Test
  public void testClassDeclaration() {
    testSame("class Foo {}");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertThat(foo.isConstructor()).isTrue();
    assertScope(globalScope).declares("Foo").withTypeThat().isEqualTo(foo);
  }

  @Test
  public void testClassDeclarationWithoutConstructor() {
    testSame("class Foo {}");

    FunctionType fooClass = (FunctionType) findNameType("Foo", globalScope);
    ObjectType fooProto = fooClass.getPrototype();

    // Test class typing.
    assertThat(fooClass.isConstructor()).isTrue();

    // Test constructor property.
    assertThat(fooProto.hasOwnProperty("constructor")).isTrue();
    assertNode(fooProto.getOwnPropertyDefSite("constructor")).isNull();
    assertType(fooProto)
        .withTypeOfProp("constructor")
        .toStringIsEqualTo("function(new:Foo, ...?): ?");
  }

  @Test
  public void testClassDeclarationWithConstructor() {
    testSame(
        lines(
            "class Foo {", //
            "  /** @param {number} arg */",
            "  constructor(arg) {",
            "    CTOR_BODY:;",
            "  }",
            "}"));

    FunctionType fooClass = (FunctionType) findNameType("Foo", globalScope);
    ObjectType fooProto = fooClass.getPrototype();
    List<JSType> params = ImmutableList.copyOf(fooClass.getParameterTypes());
    Node ctorDef = getLabeledStatement("CTOR_BODY").statementNode.getAncestor(3);

    // Test class typing.
    assertThat(fooClass.isConstructor()).isTrue();
    assertThat(params).hasSize(1);
    assertType(params.get(0)).isNumber();

    // Test constructor property.
    assertThat(fooProto.hasOwnProperty("constructor")).isTrue();
    assertNode(fooProto.getOwnPropertyDefSite("constructor")).isSameAs(ctorDef);
    assertType(fooProto)
        .withTypeOfProp("constructor")
        .toStringIsEqualTo("function(new:Foo, ...?): ?");
  }

  @Test
  public void testInterfaceClassDeclarationWithConstructor() {
    testSame(
        lines(
            "/** @interface */", //
            "class Foo {",
            "  constructor(arg) {}",
            "}"));

    FunctionType fooClass = (FunctionType) findNameType("Foo", globalScope);
    ObjectType fooProto = fooClass.getPrototype();

    // Test class typing.
    assertThat(fooClass.isInterface()).isTrue();

    // Test constructor property.
    assertThat(fooProto.hasOwnProperty("constructor")).isFalse();
  }

  @Test
  public void testClassDeclarationWithExtends() {
    testSame(
        lines(
            "class Bar {}", //
            "class Foo extends Bar {}"));
    FunctionType bar = (FunctionType) (findNameType("Bar", globalScope));
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).isSubtypeOf(bar.getInstanceType());
    assertScope(globalScope).declares("Bar").withTypeThat().isEqualTo(bar);
    assertScope(globalScope).declares("Foo").withTypeThat().isEqualTo(foo);
  }

  @Test
  public void testClassDeclarationWithNestedExtends() {
    testSame(
        lines(
            "class Bar {}", //
            "class Foo extends class extends class extends Bar {} {} {}"));
    FunctionType bar = (FunctionType) (findNameType("Bar", globalScope));
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).isSubtypeOf(bar.getInstanceType());
  }

  @Test
  public void testClassDeclarationWithInheritedConstructor() {
    testSame(
        lines(
            "class Bar {",
            "  constructor(/** string */ arg) {}",
            "}",
            "class Foo extends Bar {}"));

    FunctionType fooClass = (FunctionType) findNameType("Foo", globalScope);
    ObjectType fooProto = fooClass.getPrototype();
    List<JSType> params = ImmutableList.copyOf(fooClass.getParameterTypes());

    // Test class typing.
    assertThat(fooClass.isConstructor()).isTrue();
    assertThat(params).hasSize(1);
    assertType(params.get(0)).isString();

    // Test constructor property.
    assertThat(fooProto.hasOwnProperty("constructor")).isTrue();
    assertNode(fooProto.getOwnPropertyDefSite("constructor")).isNull();
    assertType(fooProto)
        .withTypeOfProp("constructor")
        .toStringIsEqualTo("function(new:Foo, ...?): ?");
  }

  @Test
  public void testClassDeclarationWithOverriddenConstructor() {
    testSame(
        lines(
            "class Bar {",
            "  constructor(/** string */ arg) {}",
            "}",
            "class Foo extends Bar {",
            "  constructor(/** number */ arg) { CTOR_BODY:super(''); }",
            "  static method() {}",
            "}"));

    FunctionType bar = (FunctionType) (findNameType("Bar", globalScope));
    ObjectType barObject = bar.getInstanceType();
    JSType barConstructorProperty = barObject.getPropertyType("constructor");

    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    ObjectType fooObject = foo.getInstanceType();
    ObjectType fooProto = foo.getPrototype();
    JSType fooConstructorProperty = fooObject.getPropertyType("constructor");
    Node fooCtorDef = getLabeledStatement("CTOR_BODY").statementNode.getAncestor(3);

    assertType(foo).withTypeOfProp("method").isNotUnknown();
    assertType(foo).withTypeOfProp("method").isNotEmpty();

    List<JSType> params = ImmutableList.copyOf(foo.getParameterTypes());
    assertThat(params).hasSize(1);
    assertType(params.get(0)).isNumber();
    assertType(barConstructorProperty).toStringIsEqualTo("function(new:Bar, ...?): ?");

    assertType(fooConstructorProperty).toStringIsEqualTo("function(new:Foo, ...?): ?");
    assertNode(fooProto.getOwnPropertyDefSite("constructor")).isSameAs(fooCtorDef);

    assertType(fooConstructorProperty).isSubtypeOf(barConstructorProperty);
    // TODO(b/118174876): The type of `this` should be equal to the Foo class itself.
    assertType(fooConstructorProperty)
        .withTypeOfProp("method")
        .isFunctionTypeThat()
        .hasTypeOfThisThat()
        .isUnknown();
  }

  @Test
  public void testClassDeclarationWithNestedExtendsAndInheritedConstructor() {
    testSame(
        lines(
            "class Bar {",
            "  constructor(/** string */ arg) {}",
            "}",
            "class Foo extends class extends class extends Bar {} {} {}"));
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    List<JSType> params = ImmutableList.copyOf(foo.getParameterTypes());
    assertThat(params).hasSize(1);
    assertType(params.get(0)).isString();
  }

  @Test
  public void testClassDeclarationWithMethod() {
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  method(arg) {",
            "    METHOD:;",
            "    var /** number */ foo;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodBlockScope).declares("foo").directly().withTypeThat().isNumber();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithDucplicatePrototypeMethodAfterward() {
    // Given
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  method(arg) { }",
            "}",
            "",
            // This declaration should be ignored in preference of the one in the CLASS body.
            // Constructs like this are sometimes valid (e.g. in mod files) and have error reporting
            // separate from `TypedScopeCreator`.
            "/** @param {number} arg */",
            "Foo.prototype.method = function(arg) { }"));

    // Then
    FunctionType foo = (FunctionType) findNameType("Foo", globalScope);
    assertType(foo.getInstanceType())
        .withTypeOfProp("method")
        .toStringIsEqualTo("function(this:Foo, string): undefined");
  }

  @Test
  public void testClassDeclarationWithDucplicateStaticMethodAfterward() {
    // Given
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  static method(arg) { }",
            "}",
            "",
            // This declaration should be ignored in preference of the one in the CLASS body.
            // Constructs like this are sometimes valid (e.g. in mod files) and have error reporting
            // separate from `TypedScopeCreator`.
            "/** @param {number} arg */",
            "Foo.method = function(arg) { }"));

    // Then
    FunctionType foo = (FunctionType) findNameType("Foo", globalScope);
    assertType(foo).withTypeOfProp("method").toStringIsEqualTo("function(string): undefined");
  }

  @Test
  public void testClassDeclarationWithMethodAndInlineParamDocs() {
    testSame(
        lines(
            "class Foo {",
            "  method(/** string */ arg) {",
            "    METHOD:;",
            "    var /** number */ foo;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodBlockScope).declares("foo").directly().withTypeThat().isNumber();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithOverriddenMethod() {
    testSame(
        lines(
            "class Bar {",
            "  method(/** string */ arg) {}",
            "}",
            "class Foo extends Bar {",
            "  method(arg) {",
            "    METHOD:;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithStaticMethod() {
    testSame(
        lines(
            "class Foo {",
            "  static method(/** string */ arg) {",
            "    METHOD:;",
            "    var /** number */ foo;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodBlockScope).declares("foo").directly().withTypeThat().isNumber();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    // TODO(sdh): Probably want function(this:function(new:Foo), string): undefined
    assertType(method).toStringIsEqualTo("function(string): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithOverriddenStaticMethod() {
    testSame(
        lines(
            "class Foo {",
            "  /** @param {number} arg */",
            "  static method(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  static method(arg) {",
            "    METHOD:;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isNumber();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    // TODO(sdh): Probably want function(this:function(new:Foo), string): undefined
    assertType(method).toStringIsEqualTo("function(number): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithAsyncMethod() {
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  async method(arg) {",
            "    METHOD:;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): Promise<undefined>");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithGeneratorMethod() {
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  * method(arg) {",
            "    METHOD:;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): Generator<?>");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType()).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassDeclarationWithComputedPropertyMethod() {
    testSame(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  ['method'](arg) {",
            "    METHOD:;",
            "    var /** number */ foo;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodBlockScope).declares("foo").directly().withTypeThat().isNumber();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    assertType(method).toStringIsEqualTo("function(this:Foo, string): undefined");
  }

  @Test
  public void testClassExpressionAssignment() {
    testSame("var Foo = class Bar {}");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertThat(foo.isConstructor()).isTrue();
    FunctionType bar = (FunctionType) (findNameType("Bar", globalScope));
    assertThat(bar).isEqualTo(foo);
  }

  @Test
  public void testClassExpressionBleedingNameScope() {
    testSame(
        lines(
            "var Foo = class Bar {",
            "  constructor() {",
            "    CTOR:;",
            "  }",
            "};"));
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    TypedScope ctorBlockScope = getLabeledStatement("CTOR").enclosingScope;
    TypedScope ctorScope = ctorBlockScope.getParentScope();
    TypedScope classScope = ctorScope.getParentScope();
    assertScope(globalScope).declares("Foo").withTypeThat().isEqualTo(foo);
    assertScope(classScope).declares("Bar").directly().withTypeThat().isEqualTo(foo);
    assertScope(globalScope).doesNotDeclare("Bar");
  }

  @Test
  public void testClassExpressionWithMethod() {
    testSame(
        lines(
            "var Foo = class {",
            "  /** @param {string} arg */",
            "  method(arg) {",
            "    METHOD:;",
            "  }",
            "};"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo.getInstanceType())
        .withTypeOfProp("method")
        .toStringIsEqualTo("function(this:Foo, string): undefined");
  }

  @Test
  public void testClassExpressionWithStaticMethod() {
    testSame(
        lines(
            "var Foo = class {",
            "  static method(/** string */ arg) {",
            "    METHOD:;",
            "    var /** number */ foo;",
            "  }",
            "}"));
    TypedScope methodBlockScope = getLabeledStatement("METHOD").enclosingScope;
    TypedScope methodScope = methodBlockScope.getParentScope();
    assertScope(methodBlockScope).declares("foo").directly().withTypeThat().isNumber();
    assertScope(methodScope).declares("arg").directly().withTypeThat().isString();

    FunctionType method = (FunctionType) methodScope.getRootNode().getJSType();
    // TODO(sdh): Probably want function(this:function(new:Foo), string): undefined
    assertType(method).toStringIsEqualTo("function(string): undefined");
    FunctionType foo = (FunctionType) (findNameType("Foo", globalScope));
    assertType(foo).withTypeOfProp("method").isEqualTo(method);
  }

  @Test
  public void testClassExpressionWithStaticClassAssignedLater() {
    testSame(
        lines(
            "var Foo = class {};",
            // even though there's no JSDoc, this should be treated as a type declaration.
            "Foo.Something = class {};",
            ""));
    assertScope(globalScope)
        .declares("Foo")
        .directly()
        .withTypeThat()
        .toStringIsEqualTo("function(new:Foo): undefined");
    assertScope(globalScope)
        .declares("Foo.Something")
        .directly()
        .withTypeThat()
        .toStringIsEqualTo("function(new:Foo.Something): undefined");
  }

  @Test
  public void testClassExpressionInCallback() {
    testSame(
        lines(
            "function use(arg) {}",
            "use(class Bar {",
            "  constructor() {",
            "    CTOR:;",
            "  }",
            "});"));
    TypedScope ctorBlockScope = getLabeledStatement("CTOR").enclosingScope;
    TypedScope ctorScope = ctorBlockScope.getParentScope();
    TypedScope classScope = ctorScope.getParentScope();
    assertScope(classScope)
        .declares("Bar")
        .directly()
        .withTypeThat()
        // TODO(sdh): Print a better name (https://github.com/google/closure-compiler/issues/2982)
        .toStringIsEqualTo("function(new:<anonymous@testcode:2>): undefined");
    assertScope(globalScope).doesNotDeclare("Bar");
  }

  @Test
  public void testForLoopIntegration() {
    testSame("var y = 3; for (var x = true; x; y = x) {}");

    TypedVar y = globalScope.getVar("y");
    assertThat(y.isTypeInferred()).isTrue();
    assertThat(y.getType().toString()).isEqualTo("(boolean|number)");
  }

  @Test
  public void testConstructorAlias() {
    testSame(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;");
    assertThat(registry.getType(null, "FooAlias").toString()).isEqualTo("Foo");
    assertType(registry.getType(null, "FooAlias"))
        .isStructurallyEqualTo(registry.getType(null, "Foo"));
  }

  @Test
  public void testNamespacedConstructorAlias() {
    testSame(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function() {};" +
        "/** @constructor */ goog.FooAlias = goog.Foo;");
    assertThat(registry.getType(null, "goog.FooAlias").toString()).isEqualTo("goog.Foo");
    assertType(registry.getType(null, "goog.FooAlias"))
        .isStructurallyEqualTo(registry.getType(null, "goog.Foo"));
  }

  @Test
  public void testTemplateType1() {
    testSame(
        "/**\n" +
        " * @param {function(this:T, ...)} fn\n" +
        " * @param {T} thisObj\n" +
        " * @template T\n" +
        " */\n" +
        "function bind(fn, thisObj) {}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @return {number} */\n" +
        "Foo.prototype.baz = function() {};\n" +
        "bind(function() { var g = this; var f = this.baz(); }, new Foo());");
    assertThat(findNameType("g", lastLocalScope).toString()).isEqualTo("Foo");
    assertThat(findNameType("f", lastLocalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testTemplateType2() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testTemplateType2a() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T|undefined}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("(string|undefined)");
  }

  @Test
  public void testTemplateType2b() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string|undefined} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("(string|undefined)");
  }

  @Test
  public void testTemplateType3() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val1 = 'hi';\n" +
        "var result1 = f(val1);" +
        "/** @type {number} */\n" +
        "var val2 = 0;\n" +
        "var result2 = f(val2);");

    assertThat(findNameType("result1", globalScope).toString()).isEqualTo("string");
    assertThat(findNameType("result2", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testTemplateType4() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {!Array<string>} */\n" +
        "var arr = [];\n" +
        "(function() {var result = f(arr);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertThat(resultType.toString()).isEqualTo("Array<string>");
  }

  @Test
  public void testTemplateType4a() {
    testSame(
        "/**\n" +
        " * @param {function():T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @return {string} */\n" +
        "var g = function(){return 'hi'};\n" +
        "(function() {var result = f(g);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertThat(resultType.toString()).isEqualTo("string");
  }

  @Test
  public void testTemplateType4b() {
    testSame(
        "/**\n" +
        " * @param {function(T):void} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @param {string} x */\n" +
        "var g = function(x){};\n" +
        "(function() {var result = f(g);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertThat(resultType.toString()).isEqualTo("string");
  }

  @Test
  public void testTemplateType5() {
    testSame(
        "/**\n" +
        " * @param {Array<T>} arr\n" +
        " * @return {!Array<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(arr) {\n" +
        "  return arr;\n" +
        "}" +
        "/** @type {Array<string>} */\n" +
        "var arr = [];\n" +
        "var result = f(arr);");

    assertThat(findNameTypeStr("result", globalScope)).isEqualTo("Array<string>");
  }

  @Test
  public void testTemplateType6() {
    testSame(
        "/**\n" +
        " * @param {Array<T>|string|undefined} arr\n" +
        " * @return {!Array<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(arr) {\n" +
        "  return arr;\n" +
        "}" +
        "/** @type {Array<string>} */\n" +
        "var arr = [];\n" +
        "var result = f(arr);");

    assertThat(findNameTypeStr("result", globalScope)).isEqualTo("Array<string>");
  }

  @Test
  public void testTemplateType7() {
    testSame(
        lines(
            "var goog = {};",
            "goog.array = {};",
            "/**",
            " * @param {Array<T>} arr",
            " * @param {function(this:S, !T, number, !Array<!T>):boolean} f",
            " * @param {!S=} opt_obj",
            " * @return {!Array<T>}",
            " * @template T,S",
            " */",
            "goog.array.filter = function(arr, f, opt_obj) {",
            "  var res = [];",
            "  for (var i = 0; i < arr.length; i++) {",
            "     const val = arr[i];",
            "     if (f.call(opt_obj, val, i, arr)) {",
            "        res.push(val);",
            "     }",
            "  }",
            "  return res;",
            "}",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {Array<string>} */",
            "var arr = [];",
            "var result = goog.array.filter(arr,",
            "  function(a,b,c) {var self=this;}, new Foo());"));

    assertThat(findNameType("self", lastFunctionScope).toString()).isEqualTo("Foo");
    assertThat(findNameType("a", lastFunctionScope).toString()).isEqualTo("string");
    assertThat(findNameType("b", lastFunctionScope).toString()).isEqualTo("number");
    assertThat(findNameType("c", lastFunctionScope).toString()).isEqualTo("Array<string>");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("Array<string>");
  }

  @Test
  public void testTemplateType7b() {
    testSame(
        lines(
            "var goog = {};",
            "goog.array = {};",
            "/**",
            " * @param {Array<T>} arr",
            " * @param {function(this:S, !T, number, !Array<T>):boolean} f",
            " * @param {!S=} opt_obj",
            " * @return {!Array<T>}",
            " * @template T,S",
            " */",
            "goog.array.filter = function(arr, f, opt_obj) {",
            "  var res = [];",
            "  for (var i = 0; i < arr.length; i++) {",
            "     const val = arr[i];",
            "     if (f.call(opt_obj, val, i, arr)) {",
            "        res.push(val);",
            "     }",
            "  }",
            "  return res;",
            "}",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {Array<string>} */",
            "var arr = [];",
            "var result = goog.array.filter(arr,",
            "  function(a,b,c) {var self=this;}, new Foo());"));

    assertThat(findNameType("self", lastFunctionScope).toString()).isEqualTo("Foo");
    assertThat(findNameType("a", lastFunctionScope).toString()).isEqualTo("string");
    assertThat(findNameType("b", lastFunctionScope).toString()).isEqualTo("number");
    assertThat(findNameType("c", lastFunctionScope).toString()).isEqualTo("Array<string>");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("Array<string>");
  }

  @Test
  public void testTemplateType7c() {
    testSame(
        lines(
            "var goog = {};",
            "goog.array = {};",
            "/**",
            " * @param {Array<T>} arr",
            " * @param {function(this:S, T, number, Array<T>):boolean} f",
            " * @param {!S=} opt_obj",
            " * @return {!Array<T>}",
            " * @template T,S",
            " */",
            "goog.array.filter = function(arr, f, opt_obj) {",
            "  var res = [];",
            "  for (var i = 0; i < arr.length; i++) {",
            "     const val = arr[i];",
            "     if (f.call(opt_obj, val, i, arr)) {",
            "        res.push(val);",
            "     }",
            "  }",
            "  return res;",
            "}",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {Array<string>} */",
            "var arr = [];",
            "var result = goog.array.filter(arr,",
            "  function(a,b,c) {var self=this;}, new Foo());"));

    assertThat(findNameType("self", lastFunctionScope).toString()).isEqualTo("Foo");
    assertThat(findNameType("a", lastFunctionScope).toString()).isEqualTo("string");
    assertThat(findNameType("b", lastFunctionScope).toString()).isEqualTo("number");
    assertThat(findNameType("c", lastFunctionScope).toString()).isEqualTo("(Array<string>|null)");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("Array<string>");
  }

  @Test
  @Ignore
  public void testTemplateType8() {
    // TODO(johnlenz): somehow allow templated typedefs
    testSame(
        "/** @constructor */ NodeList = function() {};" +
        "/** @constructor */ Arguments = function() {};" +
        "var goog = {};" +
        "goog.array = {};" +
        "/**\n" +
        " * @typedef {Array<T>|NodeList|Arguments|{length: number}}\n" +
        " * @template T\n" +
        " */\n" +
        "goog.array.ArrayLike;" +
        "/**\n" +
        " * @param {function(this:T, ...)} fn\n" +
        " * @param {T} thisObj\n" +
        " * @template T\n" +
        " */\n" +
        "function bind(fn, thisObj) {}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @return {number} */\n" +
        "Foo.prototype.baz = function() {};\n" +
        "bind(function() { var g = this; var f = this.baz(); }, new Foo());");
    assertThat(findNameType("g", lastLocalScope).toString()).isEqualTo("T");
    assertThat(findNameType("g", lastLocalScope).isEquivalentTo(registry.getType(null, "Foo")))
        .isTrue();
    assertThat(findNameType("f", lastLocalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testTemplateType9() {
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {}\n" +
        "\n" +
        "var g = new Bar().method();\n");
    assertThat(findNameType("g", globalScope).toString()).isEqualTo("Bar");
  }

  @Test
  public void testTemplateType10() {
    // NOTE: we would like the type within the function to remain "Foo"
    // we can handle this by support template type like "T extends Foo"
    // to provide a "minimum" type for "Foo" within the function body.
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {var g = this;};\n");
    assertThat(findNameType("g", lastLocalScope).toString()).isEqualTo("T");
  }

  @Test
  public void testTemplatedThis_inClassInstanceMethod_isInferredToBe_receiverType() {
    testSame(
        lines(
            "class Foo {",
            "  /**",
            "   * @template THIS",
            "   * @this {THIS}",
            "   * @return {THIS}",
            "   */",
            "  clone() { return this; }",
            "}",
            "",
            "var result = new Foo().clone();"));

    assertType(findNameType("result", globalScope)).toStringIsEqualTo("Foo");
  }

  @Test
  public void testTemplatedThis_inClassInstanceMethod_invokedOnSuper_isInferredToBe_subtype() {
    testSame(
        lines(
            "class Foo {",
            "  /**",
            "   * @template THIS",
            "   * @this {THIS}",
            "   * @return {THIS}",
            "   */",
            "  clone() { return this; }",
            "}",
            "",
            "class SubFoo extends Foo {",
            "  other() {",
            "    LABEL: super.clone();",
            "  }",
            "}"));

    Node superCloneCall = getLabeledStatement("LABEL").statementNode.getOnlyChild();
    assertType(superCloneCall.getJSType()).toStringIsEqualTo("SubFoo");
  }

  @Test
  public void testTemplateType11() {
    testSame(
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "var method = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "function Bar() {}\n" +
        "\n" +
        "var g = method().call(new Bar());\n");
    // NOTE: we would like this to be "Bar"
    assertThat(findNameType("g", globalScope).toString()).isEqualTo("?");
  }

  @Test
  public void testTemplateType12() {
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/**\n" +
        " * @this {Array<T>|{length:number}}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {var g = this;};\n");
    assertThat(findNameType("g", lastLocalScope).toString())
        .isEqualTo("(Array<T>|{length: number})");
  }

  @Test
  @Ignore
  public void testTemplateType13() {
    // TODO(johnlenz): allow template types in @type function expressions
    testSame(
        "/**\n" +
        " * @type {function(T):T}\n" +
        " * @template T\n" +
        " */\n" +
        "var f;" +
        "/** @type {string} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("(string|undefined)");
  }

  @Test
  public void testClassTemplateType1() {
    // Verify that template types used in method signature are resolved.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "C.prototype.method = function() {}\n" +
        "" +
        "/** @type {C<string>} */ var x = new C();\n" +
        "var result = x.method();\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType2() {
    // Verify that template types used in method signature on namespaced
    // objects are resolved.
    testSame(
        "/** @const */ var ns = {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "ns.C = function() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "ns.C.prototype.method = function() {}\n" +
        "" +
        "/** @type {ns.C<string>} */ var x = new ns.C();\n" +
        "var result = x.method();\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType3() {
    // Verify that template types used for instance properties are recognized.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {\n" +
        "  /** @type {T} */\n" +
        "  this.foo;" +
        "};\n" +
        "" +
        "/** @type {C<string>} */ var x = new C();\n" +
        "var result = x.foo;\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType4() {
    // Verify that template types used for instance properties are recognized.
    testSame(
        "/** @const */ var ns = {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "ns.C = function() {\n" +
        "  /** @type {T} */\n" +
        "  this.foo;" +
        "};\n" +
        "" +
        "/** @type {ns.C<string>} */ var x = new ns.C();\n" +
        "var result = x.foo;\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType5() {
    // Verify that template types used for prototype properties in stub
    // declarations are recognized.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {\n" +
        "};\n" +
        "" +
        "/** @type {T} */" +
        "C.prototype.foo;\n" +
        "" +
        "/** @type {C<string>} */ var x = new C();\n" +
        "var result = x.foo;\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType6() {
    // Verify that template types used for prototype properties in assignment
    // expressions are recognized.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {\n" +
        "};\n" +
        "" +
        "/** @type {T} */" +
        "C.prototype.foo = 1;\n" +
        "" +
        "/** @type {C<string>} */ var x = new C();\n" +
        "var result = x.foo;\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateType7() {
    // Verify that template types used in prototype methods are recognized.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "C.prototype.method = function() {\n" +
        "  /** @type {T} */ var local;" +
        "}\n");
    assertThat(findNameType("local", lastLocalScope).toString()).isEqualTo("T");
  }

  @Test
  public void testClassTemplateType8() {
    // Verify that template types used in casts are recognized.
    testSame(
        externs("/** @type {?} */ let unknown;"),
        srcs(
            lines(
                "/**",
                " * @constructor",
                " * @template T",
                " */",
                "function C() {};",
                "",
                "C.prototype.method = function() {",
                "  var local = /** @type {T} */ (unknown);",
                "}")));
    assertThat(findNameType("local", lastLocalScope).toString()).isEqualTo("T");
  }

  @Test
  public void testClassTemplateInheritance1() {
    // Verify that template type inheritance works for prototype properties.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "/** @type {T} */" +
        "C.prototype.foo = 1;\n" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T, U\n" +
        " * @extends {C<U>}" +
        " */\n" +
        "function D() {};\n" +
        "" +
        "/** @type {T} */" +
        "D.prototype.bar;\n" +
        "" +
        "/** @type {D<string, number>} */ var x = new D();\n" +
        "var result1 = x.foo;\n" +
        "var result2 = x.bar;\n");
    assertThat(findNameType("result1", globalScope).toString()).isEqualTo("number");
    assertThat(findNameType("result2", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateInheritance2() {
    // Verify that template type inheritance works for properties and methods.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "C.prototype.method1 = function() {}\n" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T, U\n" +
        " * @extends {C<U>}" +
        " */\n" +
        "function D() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "D.prototype.method2 = function() {}\n" +
        "" +
        "/** @type {D<boolean, string>} */ var x = new D();\n" +
        "var result1 = x.method1();\n" +
        "var result2 = x.method2();\n");
    assertThat(findNameType("result1", globalScope).toString()).isEqualTo("string");
    assertThat(findNameType("result2", globalScope).toString()).isEqualTo("boolean");
  }

  @Test
  public void testClassTemplateInheritance3() {
    // Verify that template type inheritance works when the superclass template
    // types are not specified.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {\n" +
        "  /** @type {T} */\n" +
        "  this.foo;" +
        "};\n" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @extends {C}" +
        " */\n" +
        "function D() {\n" +
        "  /** @type {T} */\n" +
        "  this.bar;" +
        "};\n" +
        "" +
        "/** @type {D<boolean>} */ var x = new D();\n" +
        "var result1 = x.foo;\n" +
        "var result2 = x.bar;\n");
    assertThat(findNameType("result1", globalScope).toString()).isEqualTo("?");
    // TODO(nicksantos): There's a bug where the template name T clashes between
    // D and C.
    // assertEquals("boolean", findNameType("result2", globalScope).toString());
  }

  @Test
  public void testClassTemplateInheritance4() {
    // Verify that overriding methods works with template type inheritance.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "C.prototype.method = function() {}\n" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T, U\n" +
        " * @extends {C<U>}" +
        " */\n" +
        "function D() {};\n" +
        "" +
        "/** @override */\n" +
        "D.prototype.method = function() {}\n" +
        "" +
        "/** @type {D<boolean, string>} */ var x = new D();\n" +
        "var result = x.method();\n");
    assertThat(findNameType("result", globalScope).toString()).isEqualTo("string");
  }

  @Test
  public void testClassTemplateInheritance5() {
    // Verify that overriding methods works with template type inheritance.
    testSame(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "C.prototype.method1 = function() {}\n" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @template T, U\n" +
        " * @extends {C<U>}" +
        " */\n" +
        "function D() {};\n" +
        "" +
        "/** @return {T} */\n" +
        "D.prototype.method2 = function() {}\n" +
        "" +
        "/** @type {D<string, boolean>} */ var x = new D();\n" +
        "/** @type {C<boolean>} */ var y = x;\n" +
        "/** @type {C} */ var z = y;\n" +
        "var result1 = x.method2();\n" +
        "var result2 = y.method1();\n" +
        "var result3 = z.method1();\n");
    assertThat(findNameType("result1", globalScope).toString()).isEqualTo("string");
    assertThat(findNameType("result2", globalScope).toString()).isEqualTo("boolean");
    assertThat(findNameType("result3", globalScope).toString()).isEqualTo("T");
  }

  @Test
  public void testClosureParameterTypesWithoutJSDoc() {
    testSame(
        "/**\n" +
        " * @param {function(!Object)} bar\n" +
        " */\n" +
        "function foo(bar) {}\n" +
        "foo(function(baz) { var f = baz; })\n");
    assertThat(findNameType("f", lastLocalScope).toString()).isEqualTo("Object");
  }

  @Test
  public void testDuplicateExternProperty1() {
    testSame(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype.bar;"
            + "/** @type {number} */ Foo.prototype.bar; var x = (new Foo).bar;");
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testDuplicateExternProperty2() {
    testSame(
        "/** @constructor */ function Foo() {}"
            + "/** @type {number} */ Foo.prototype.bar;"
            + "Foo.prototype.bar; var x = (new Foo).bar;");
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testAbstractMethod() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = abstractMethod;");
    assertThat(findNameType("abstractMethod", globalScope).toString()).isEqualTo("Function");

    FunctionType ctor = (FunctionType) findNameType("Foo", globalScope);
    ObjectType instance = ctor.getInstanceType();
    assertThat(instance.toString()).isEqualTo("Foo");

    ObjectType proto = instance.getImplicitPrototype();
    assertThat(proto.toString()).isEqualTo("Foo.prototype");

    assertThat(proto.getPropertyType("bar").toString()).isEqualTo("function(this:Foo, number): ?");
  }

  @Test
  public void testAbstractMethod2() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @param {number} x */ var y = abstractMethod;");
    assertThat(findNameType("y", globalScope).toString()).isEqualTo("Function");
    assertThat(globalScope.getVar("y").getType().toString()).isEqualTo("function(number): ?");
  }

  @Test
  public void testAbstractMethod3() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @param {number} x */ var y = abstractMethod; y;");
    assertThat(findNameType("y", globalScope).toString()).isEqualTo("function(number): ?");
  }

  @Test
  public void testAbstractMethod4() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {/** @param {number} x */ bar: abstractMethod};");
    assertThat(findNameType("abstractMethod", globalScope).toString()).isEqualTo("Function");

    FunctionType ctor = (FunctionType) findNameType("Foo", globalScope);
    ObjectType instance = ctor.getInstanceType();
    assertThat(instance.toString()).isEqualTo("Foo");

    ObjectType proto = instance.getImplicitPrototype();
    assertThat(proto.toString()).isEqualTo("Foo.prototype");

    assertType(proto.getPropertyType("bar")).toStringIsEqualTo("function(this:Foo, number): ?");
  }

  @Test
  public void testReturnTypeInference1() {
    testSame("function f() {}");
    assertThat(findNameType("f", globalScope).toString()).isEqualTo("function(): undefined");
  }

  @Test
  public void testReturnTypeInference2() {
    testSame("/** @return {?} */ function f() {}");
    assertThat(findNameType("f", globalScope).toString()).isEqualTo("function(): ?");
  }

  @Test
  public void testReturnTypeInference3() {
    testSame("function f() {x: return 3;}");
    assertThat(findNameType("f", globalScope).toString()).isEqualTo("function(): ?");
  }

  @Test
  public void testReturnTypeInference4() {
    testSame("function f() { throw 'error'; }");
    assertThat(findNameType("f", globalScope).toString()).isEqualTo("function(): ?");
  }

  @Test
  public void testReturnTypeInference5() {
    testSame("function f() { if (true) { return 1; } }");
    assertThat(findNameType("f", globalScope).toString()).isEqualTo("function(): ?");
  }

  @Test
  public void testLiteralTypesInferred() {
    testSame("null + true + false + 0 + '' + {}");
    assertThat(findTokenType(Token.NULL, globalScope).toString()).isEqualTo("null");
    assertThat(findTokenType(Token.TRUE, globalScope).toString()).isEqualTo("boolean");
    assertThat(findTokenType(Token.FALSE, globalScope).toString()).isEqualTo("boolean");
    assertThat(findTokenType(Token.NUMBER, globalScope).toString()).isEqualTo("number");
    assertThat(findTokenType(Token.STRING, globalScope).toString()).isEqualTo("string");
    assertThat(findTokenType(Token.OBJECTLIT, globalScope).toString()).isEqualTo("{}");
  }

  @Test
  public void testGlobalQualifiedNameInLocalScope() {
    testSame(
        "var ns = {}; " +
        "(function() { " +
        "    /** @param {number} x */ ns.foo = function(x) {}; })();" +
        "(function() { ns.foo(3); })();");
    assertThat(globalScope.getVar("ns.foo")).isNotNull();
    assertThat(globalScope.getVar("ns.foo").getType().toString())
        .isEqualTo("function(number): undefined");
  }

  @Test
  public void testDeclaredObjectLitProperty1() {
    testSame("var x = {/** @type {number} */ y: 3};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertThat(xType.getPropertyType("y").toString()).isEqualTo("number");
    assertThat(xType.toString()).isEqualTo("{y: number}");
  }

  @Test
  public void testDeclaredObjectLitProperty2() {
    testSame("var x = {/** @param {number} z */ y: function(z){}};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertThat(xType.getPropertyType("y").toString()).isEqualTo("function(number): undefined");
    assertThat(xType.toString()).isEqualTo("{y: function(number): undefined}");
  }

  @Test
  public void testDeclaredObjectLitProperty3() {
    testSame("function f() {" +
        "  var x = {/** @return {number} */ y: function(z){ return 3; }};" +
        "}");
    ObjectType xType = ObjectType.cast(lastLocalScope.getVar("x").getType());
    assertThat(xType.getPropertyType("y").toString()).isEqualTo("function(?): number");
    assertThat(xType.toString()).isEqualTo("{y: function(?): number}");
  }

  @Test
  public void testDeclaredObjectLitProperty4() {
    testSame("var x = {y: 5, /** @type {number} */ z: 3};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertThat(xType.getPropertyType("y").toString()).isEqualTo("number");
    assertThat(xType.isPropertyTypeDeclared("y")).isFalse();
    assertThat(xType.isPropertyTypeDeclared("z")).isTrue();
    assertThat(xType.toString()).isEqualTo("{y: number, z: number}");
  }

  @Test
  public void testDeclaredObjectLitProperty5() {
    testSame("var x = {/** @type {number} */ prop: 3};" +
             "function f() { var y = x.prop; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("number");
  }

  @Test
  public void testDeclaredObjectLitProperty6() {
    testSame("var x = {/** This is JsDoc */ prop: function(){}};");
    TypedVar prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertThat(propType.toString()).isEqualTo("function(): undefined");
    assertThat(prop.isTypeInferred()).isFalse();
    assertThat(ObjectType.cast(globalScope.getVar("x").getType()).isPropertyTypeInferred("prop"))
        .isFalse();
  }

  @Test
  public void testInferredObjectLitProperty1() {
    testSame("var x = {prop: 3};");
    TypedVar prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertThat(propType.toString()).isEqualTo("number");
    assertThat(prop.isTypeInferred()).isTrue();
    assertThat(ObjectType.cast(globalScope.getVar("x").getType()).isPropertyTypeInferred("prop"))
        .isTrue();
  }

  @Test
  public void testInferredObjectLitProperty2() {
    testSame("var x = {prop: function(){}};");
    TypedVar prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertThat(propType.toString()).isEqualTo("function(): undefined");
    assertThat(prop.isTypeInferred()).isTrue();
    assertThat(ObjectType.cast(globalScope.getVar("x").getType()).isPropertyTypeInferred("prop"))
        .isTrue();
  }

  @Test
  public void testDeclaredConstType1() {
    testSame(
        "/** @const */ var x = 3;" +
        "function f() { var y = x; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("number");
  }

  @Test
  public void testDeclaredConstType2() {
    testSame(
        "/** @const */ var x = {};" +
        "function f() { var y = x; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("{}");
  }

  @Test
  public void testDeclaredConstType3() {
    testSame(
        "/** @const */ var x = {};" +
        "/** @const */ x.z = 'hi';" +
        "function f() { var y = x.z; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("string");
  }

  @Test
  public void testDeclaredConstType4() {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.z = 'hi';" +
        "function f() { var y = (new Foo()).z; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("string");

    ObjectType fooType =
        ((FunctionType) globalScope.getVar("Foo").getType()).getInstanceType();
    assertThat(fooType.isPropertyTypeDeclared("z")).isTrue();
  }

  @Test
  public void testDeclaredConstType5a() {
    testSame(
        "/** @const */ var goog = goog || {};" +
        "function f() { var y = goog; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertThat(yType.toString()).isEqualTo("{}");
  }

  @Test
  public void testDeclaredConstType6() {
    testSame(
        "/** " +
        " * @param {{y:string}} a\n" +
        " * @constructor\n" +
        "*/\n" +
        "var C = function(a) { /** @const */ this.x = a.y;};\n" +
        "var instance = new C({y:'str'})");
    ObjectType instance = (ObjectType) findNameType("instance", globalScope);
    assertThat(instance.toString()).isEqualTo("C");
    assertThat(instance.hasProperty("x")).isTrue();
    assertThat(instance.getPropertyType("x").toString()).isEqualTo("string");
    assertThat(instance.isPropertyTypeInferred("x")).isFalse();
  }

  @Test
  public void testBadCtorInit1() {
    testWarning("/** @constructor */ var f;", CTOR_INITIALIZER);
  }

  @Test
  public void testBadCtorInit2() {
    testWarning("var x = {}; /** @constructor */ x.f;", CTOR_INITIALIZER);
  }

  @Test
  public void testBadIfaceInit1() {
    testWarning("/** @interface */ var f;", IFACE_INITIALIZER);
  }

  @Test
  public void testBadIfaceInit2() {
    testWarning("var x = {}; /** @interface */ x.f;", IFACE_INITIALIZER);
  }

  @Test
  public void testDeclaredCatchExpression1() {
    testSame(
        "try {} catch (e) {}");
    assertThat(lastLocalScope.getVar("e").getType()).isNull();
  }

  @Test
  public void testDeclaredCatchExpression2() {
    testSame(
        "try {} catch (/** @type {string} */ e) {}");
    assertThat(lastLocalScope.getVar("e").getType().toString()).isEqualTo("string");
  }

  @Test
  public void testDestructuringCatch() {
    testSame(
        "try {} catch ({/** string */ message, /** number */ errno}) {}");
    assertType(lastLocalScope.getVar("message").getType()).toStringIsEqualTo("string");
    assertType(lastLocalScope.getVar("errno").getType()).toStringIsEqualTo("number");
  }

  @Test
  public void testDuplicateCatchVariableNames() {
    testSame(
        lines(
            "try {}", // preserve newlines
            "catch (err) {",
            "  FIRST_CATCH: err;",
            "}",
            "try {}",
            "catch (err) {",
            "  SECOND_CATCH: err;",
            "}",
            ""));
    TypedScope firstCatchScope = getLabeledStatement("FIRST_CATCH").enclosingScope;
    assertScope(firstCatchScope).declares("err").directly();
    TypedVar firstErrVar = firstCatchScope.getVar("err");

    TypedScope secondCatchScope = getLabeledStatement("SECOND_CATCH").enclosingScope;
    assertScope(firstCatchScope).declares("err").directly();
    assertThat(firstCatchScope).isNotSameAs(secondCatchScope);

    TypedVar secondErrVar = secondCatchScope.getVar("err");
    assertThat(firstErrVar).isNotSameAs(secondErrVar);
  }

  @Test
  public void testGenerator1() {
    testSame("function *gen() { yield 1; } var g = gen();");
    assertThat(findNameType("gen", globalScope).toString()).isEqualTo("function(): Generator<?>");
    assertThat(findNameType("g", globalScope).toString()).isEqualTo("Generator<?>");
  }

  @Test
  public void testGenerator2() {
    testSame("var gen = function *() { yield 1; }; var g = gen();");
    assertThat(findNameType("gen", globalScope).toString()).isEqualTo("function(): Generator<?>");
    assertThat(findNameType("g", globalScope).toString()).isEqualTo("Generator<?>");
  }

  // Just check that this doesn't cause a StackOverflowError.
  @Test
  public void testArgumentsStackOverflow() {
    String js = lines(
        "/**",
        " * @fileoverview",
        " * @suppress {es5Strict}",
        " */",
        "",
        "function getStackTrace() {",
        "  try {",
        "    throw 'error';",
        "  } catch (e) {",
        "    return 0;",
        "  }",
        "",
        "  if (typeof (arguments.caller) != 'undefined') {}",
        "",
        "  return '';",
        "}",
        "");
    testSame(js);
  }

  @Test
  public void testSpreadInIifeBlocksTypeInference() {
    testSame(
        lines(
            "const /** !Array<string> */ arr = [];",
            "(function (x = 4, y = 5) {})(...arr, undefined);"));

    TypedVar xVar = checkNotNull(lastFunctionScope.getVar("x"));
    assertType(xVar.getType()).toStringIsEqualTo("?");

    TypedVar yVar = checkNotNull(lastFunctionScope.getVar("y"));
    assertType(yVar.getType()).toStringIsEqualTo("?");
  }

  @Test
  public void testAliasTypeFromClass() {
    testSame(
        lines(
            "class Foo {};",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;"));
    assertThat(findNameType("E", globalScope).toString()).isEqualTo("enum{Foo.E}");
  }

  @Test
  public void testAliasTypeFromNamespace() {
    testSame(
        lines(
            "const Foo = {};",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;"));
    assertThat(findNameType("E", globalScope).toString()).isEqualTo("enum{Foo.E}");
  }

  @Test
  public void testAliasTypeFromClassPrototype() {
    // This is very weird but falls out of how we module types.
    testSame(
        lines(
            "class Foo {}",
            "/** @enum {number} */ Foo.prototype.E = {A: 1};",
            "const F = new Foo();",
            "const E = F.E;"));
    assertThat(findNameType("E", globalScope).toString()).isEqualTo("enum{Foo.prototype.E}");
  }

  @Test
  public void testAliasTypedef() {
    testSame(
        lines(
            "/** @typedef {number} */ let Foo;", //
            "const E = Foo;",
            "/** @type {E} */ let x;"));
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testAliasTypedefFromNamespace() {
    testSame(
        lines(
            "const Foo = {};",
            "/** @typedef {number} */ Foo.E;",
            "const E = Foo.E;",
            "/** @type {E} */ let x;"));
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testAliasTypedefFromNamespaceAlias() {
    testSame(
        lines(
            "const Foo = {};",
            "/** @typedef {number} */ Foo.E;",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let x;"));
    assertThat(findNameType("x", globalScope).toString()).isEqualTo("number");
  }

  @Test
  public void testMemoization() {
    Node root1 = createEmptyRoot();
    Node root2 = createEmptyRoot();
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    TypedScope scopeA = creator.createScope(root1, null);
    assertThat(creator.createScope(root1, null)).isSameAs(scopeA);
    assertThat(creator.createScope(root2, null)).isNotSameAs(scopeA);
  }

  @Test
  public void testMemoizationPreconditionCheck() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node root = createEmptyRoot();
    TypedScopeCreator creator = new TypedScopeCreator(compiler);
    TypedScope scopeA = creator.createScope(root, null);

    try {
      creator.createScope(root, scopeA);
      assertWithMessage("Expected an IllegalStateException").fail();
    } catch (IllegalStateException expected) {
    }
  }

  private static Node createEmptyRoot() {
    Node root = new Node(Token.ROOT, new Node(Token.SCRIPT));
    root.getFirstChild().setInputId(new InputId("input"));
    return root;
  }

  private JSType findNameType(String name, TypedScope scope) {
    return findTypeOnMatchedNode(n -> n.matchesQualifiedName(name), scope);
  }

  private String findNameTypeStr(String name, TypedScope scope) {
    return findNameType(name, scope).toString();
  }

  private JSType findTokenType(Token type, TypedScope scope) {
    return findTypeOnMatchedNode(n -> type == n.getToken(), scope);
  }

  private JSType findTypeOnMatchedNode(Predicate<Node> matcher, TypedScope scope) {
    Node root = scope.getRootNode();
    Deque<Node> queue = new ArrayDeque<>();
    queue.push(root);
    while (!queue.isEmpty()) {
      Node current = queue.pop();
      if (matcher.apply(current) &&
          current.getJSType() != null) {
        return current.getJSType();
      }

      for (Node child : current.children()) {
        queue.push(child);
      }
    }
    return null;
  }

  private JSType getNativeType(JSTypeNative type) {
    return registry.getNativeType(type);
  }

  private ObjectType getNativeObjectType(JSTypeNative type) {
    return (ObjectType) registry.getNativeType(type);
  }
}
