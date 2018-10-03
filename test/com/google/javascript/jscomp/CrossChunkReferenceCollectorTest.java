/*
 * Copyright 2017 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_NEXT;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CrossChunkReferenceCollector.TopLevelStatement;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CrossChunkReferenceCollectorTest extends CompilerTestCase {
  private CrossChunkReferenceCollector testedCollector;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    setLanguage(ECMASCRIPT_NEXT, ECMASCRIPT_NEXT);
  }

  @Override
  protected int getNumRepetitions() {
    // Default behavior for CompilerTestCase.test*() methods is to do the whole test twice,
    // because passes that modify the AST need to be idempotent.
    // Since CrossChunkReferenceCollector() just gathers information, it doesn't make sense to
    // run it twice, and doing so just complicates debugging test cases.
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);
    testedCollector = new CrossChunkReferenceCollector(compiler, scopeCreator);
    return testedCollector;
  }

  @Test
  public void testVarInBlock() {
    testSame(lines(
            "  if (true) {",
            "    var y = x;",
            "    y;",
            "    y;",
            "  }"));
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    assertThat(globalVariableNamesMap).containsKey("y");
    Var yVar = globalVariableNamesMap.get("y");
    ReferenceCollection yRefs = testedCollector.getReferences(yVar);
    assertThat(yRefs.isAssignedOnceInLifetime()).isTrue();
    assertThat(yRefs.isWellDefined()).isTrue();
  }

  @Test
  public void testVarInLoopNotAssignedOnlyOnceInLifetime() {
    testSame("var x; while (true) { x = 0; }");
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isFalse();

    testSame("let x; while (true) { x = 0; }");
    globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isFalse();
  }

  /**
   * Although there is only one assignment to x in the code, it's in a function which could be
   * called multiple times, so {@code isAssignedOnceInLifetime()} returns false.
   */
  @Test
  public void testVarInFunctionNotAssignedOnlyOnceInLifetime() {
    testSame("var x; function f() { x = 0; }");
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isFalse();

    testSame("let x; function f() { x = 0; }");
    globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isFalse();
  }

  @Test
  public void testVarAssignedOnceInLifetime1() {
    testSame("var x = 0;");
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isTrue();

    testSame("let x = 0;");
    globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isTrue();
  }

  @Test
  public void testVarAssignedOnceInLifetime2() {
    testSame("{ var x = 0; }");
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isTrue();
  }

  @Test
  public void testBasicBlocks() {
    testSame(lines(
            "var x = 0;",
            "switch (x) {",
            "  case 0:",
            "    x;",
            "}"));
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.references).hasSize(3);
    assertNode(xRefs.references.get(0).getBasicBlock().getRoot()).hasType(Token.ROOT);
    assertNode(xRefs.references.get(1).getBasicBlock().getRoot()).hasType(Token.ROOT);
    assertNode(xRefs.references.get(2).getBasicBlock().getRoot()).hasType(Token.CASE);
  }

  @Test
  public void testTopLevelStatements() {
    testSame(lines(
        "var x = 1;",
        "const y = x;",
        "let z = x - y;",
        "function f(x, y) {",   // only f and z globals referenced
        "  return x + y + z;",
        "}"));

    // Pull out all the references for comparison.
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    ImmutableList<Reference> xReferences =
        ImmutableList.copyOf(testedCollector.getReferences(globalVariableNamesMap.get("x")));
    ImmutableList<Reference> yReferences =
        ImmutableList.copyOf(testedCollector.getReferences(globalVariableNamesMap.get("y")));
    ImmutableList<Reference> zReferences =
        ImmutableList.copyOf(testedCollector.getReferences(globalVariableNamesMap.get("z")));
    ImmutableList<Reference> fReferences =
        ImmutableList.copyOf(testedCollector.getReferences(globalVariableNamesMap.get("f")));

    // Make sure the statements have the references we expect.
    List<TopLevelStatement> topLevelStatements = testedCollector.getTopLevelStatements();
    assertThat(topLevelStatements).hasSize(4);
    // var x = 1;
    TopLevelStatement xEquals1 = topLevelStatements.get(0);
    assertThat(xEquals1.getOriginalOrder()).isEqualTo(0);
    assertThat(xEquals1.getDeclaredNameReference()).isEqualTo(xReferences.get(0));
    assertThat(xEquals1.getNonDeclarationReferences()).isEmpty();
    // const y = x;
    TopLevelStatement yEqualsX = topLevelStatements.get(1);
    assertThat(yEqualsX.getOriginalOrder()).isEqualTo(1);
    assertThat(yEqualsX.getNonDeclarationReferences())
        .containsExactly(xReferences.get(1));
    // let z = x - y;
    TopLevelStatement zEqualsXMinusY = topLevelStatements.get(2);
    assertThat(zEqualsXMinusY.getOriginalOrder()).isEqualTo(2);
    assertThat(zEqualsXMinusY.getNonDeclarationReferences())
        .containsExactly(xReferences.get(2), yReferences.get(1));
    // function f(x, y) { return x + y + z; }
    TopLevelStatement functionDeclaration = topLevelStatements.get(3);
    assertThat(functionDeclaration.getOriginalOrder()).isEqualTo(3);
    assertThat(functionDeclaration.getDeclaredNameReference()).isEqualTo(fReferences.get(0));
    assertThat(functionDeclaration.getNonDeclarationReferences())
        .containsExactly(zReferences.get(1));
  }

  @Test
  public void testVarDeclarationStatement() {
    testSame("var x = 1;");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    ReferenceCollection xRefs = getReferencesForName("x", testedCollector);
    assertThat(statements).hasSize(1);
    TopLevelStatement varStatement = statements.get(0);
    Reference declaredNameReference = varStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(declaredNameReference).isEqualTo(xRefs.references.get(0));
    assertThat(varStatement.getNonDeclarationReferences()).isEmpty();
    Node valueNode = varStatement.getDeclaredValueNode();
    assertThat(valueNode).isNotNull();
    assertThat(valueNode.getDouble()).isEqualTo(1.0);
  }

  @Test
  public void testFunctionDeclarationStatement() {
    testSame("function x() {}");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(1);
    ReferenceCollection xRefs = getReferencesForName("x", testedCollector);

    TopLevelStatement functionDeclaration = statements.get(0);
    Reference declaredNameReference = functionDeclaration.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(declaredNameReference).isEqualTo(xRefs.references.get(0));
    assertThat(functionDeclaration.getNonDeclarationReferences()).isEmpty();
  }

  @Test
  public void testVariableAssignmentStatement() {
    testSame("var x; x = 1;");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(2);
    ReferenceCollection xRefs = getReferencesForName("x", testedCollector);

    TopLevelStatement assignmentStatement = statements.get(1);
    Reference declaredNameReference = assignmentStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(declaredNameReference).isEqualTo(xRefs.references.get(1));
    assertThat(assignmentStatement.getNonDeclarationReferences()).isEmpty();
    Node valueNode = assignmentStatement.getDeclaredValueNode();
    assertThat(valueNode).isNotNull();
    assertThat(valueNode.getDouble()).isEqualTo(1.0);
  }

  @Test
  public void testPropertyAssignmentStatement() {
    testSame("var x = {}; x.prop = 1;");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(2);
    ReferenceCollection xRefs = getReferencesForName("x", testedCollector);

    TopLevelStatement assignmentStatement = statements.get(1);
    Reference declaredNameReference = assignmentStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(declaredNameReference).isEqualTo(xRefs.references.get(1));
    assertThat(assignmentStatement.getNonDeclarationReferences()).isEmpty();
    Node valueNode = assignmentStatement.getDeclaredValueNode();
    assertThat(valueNode).isNotNull();
    assertThat(valueNode.getDouble()).isEqualTo(1.0);
  }

  @Test
  public void testGoogInheritsIsMovableDeclaration() {
    testSame("function A() {} function B() {} goog.inherits(B, A);");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(3);

    ReferenceCollection refsToA = getReferencesForName("A", testedCollector);
    ReferenceCollection refsToB = getReferencesForName("B", testedCollector);

    TopLevelStatement inheritsStatement = statements.get(2);
    Reference declaredNameReference = inheritsStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(declaredNameReference).isEqualTo(refsToB.references.get(1));
    assertThat(inheritsStatement.getNonDeclarationReferences())
        .containsExactly(refsToA.references.get(1));
    // inherits statements are always movable
    assertThat(inheritsStatement.isMovableDeclaration()).isTrue();
  }

  @Test
  public void testDefinePropertiesIsMovableDeclaration() {
    testSame("function A() {} Object.defineProperties(A, {});");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(2);

    TopLevelStatement definePropertiesStatement = statements.get(1);
    Reference declaredNameReference = definePropertiesStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(definePropertiesStatement.getNonDeclarationReferences()).isEmpty();
    // defineProperties statements are always movable
    assertThat(definePropertiesStatement.isMovableDeclaration()).isTrue();
  }

  @Test
  public void testDefinePropertiesWithPrototypeIsMovableDeclaration() {
    testSame("function A() {} Object.defineProperties(A.prototype, {});");

    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements).hasSize(2);

    TopLevelStatement definePropertiesStatement = statements.get(1);
    Reference declaredNameReference = definePropertiesStatement.getDeclaredNameReference();
    assertThat(declaredNameReference).isNotNull();
    assertThat(definePropertiesStatement.getNonDeclarationReferences()).isEmpty();
    // defineProperties statements are always movable
    assertThat(definePropertiesStatement.isMovableDeclaration()).isTrue();
  }

  @Test
  public void testFunctionDeclarationOrAssignmentIsMovable() {
    testSame("function f() {}");
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isTrue();
    testSame("var f = function() {};");
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testLiteralValueIsMovable() {
    testSame("var f = 1;");
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testFunctionCallsAreNotMovableExceptForMethodStubs() {
    testSame(lines(
        "function Foo() {}",
        "Foo.prototype.stub = JSCompiler_stubMethod(x);",
        "Foo.prototype.unstub = JSCompiler_unstubMethod(x);",
        "Foo.prototype.other = other();"));
    List<TopLevelStatement> statements = testedCollector.getTopLevelStatements();
    assertThat(statements.get(1).isMovableDeclaration()).isTrue();
    assertThat(statements.get(2).isMovableDeclaration()).isFalse();
    assertThat(statements.get(3).isMovableDeclaration()).isFalse();
  }

  @Test
  public void testUnknownNameValueIsImmovable() {
    assertStatementIsImmovable("var a = unknownName;");
  }

  @Test
  public void testWellDefinedNameValueIsMovable() {
    testSame("var wellDefined = 1; var other = wellDefined;");
    assertThat(testedCollector.getTopLevelStatements().get(1).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testUninitializedNameValueIsNotMovable() {
    testSame("var value; var other = value;");
    assertThat(testedCollector.getTopLevelStatements().get(1).isMovableDeclaration()).isFalse();
  }

  @Test
  public void testReDefinedNameValueIsNotMovable() {
    testSame("var redefined = 1; redefined = 2; var other = redefined;");
    assertThat(testedCollector.getTopLevelStatements().get(2).isMovableDeclaration()).isFalse();
  }

  @Test
  public void testEmptyArrayLiteralIsMovable() {
    testSame("var a = [];");
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testArrayLiteralOfMovablesIsMovable() {
    testSame("var wellDefinedName = 1; var a = [function(){}, 1, wellDefinedName, []];");
    assertThat(testedCollector.getTopLevelStatements().get(1).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testArrayLiteralWithImmovableIsImmovable() {
    assertStatementIsImmovable("var a = [unknownValue];");
  }

  @Test
  public void testEmptyObjectLiteralIsMovable() {
    testSame("var o = {};");
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testObjectLiteralOfMovablesIsMovable() {
    testSame(lines(
        "var wellDefinedName = 1;",
        "var o = {",
        "  f: function(){},",
        "  one: 1,",
        "  n: wellDefinedName,",
        "  o: {},",
        "  'quoted': 1,",
        "  123: 2,",
        // computed
        "  ['computed string']: 1,",
        "  [234]: 1,",
        // method shorthand
        "  method() {},",
        "  'quoted method'() {},",
        "  ['computed method']() {},",
        "  [345]() {},",
        // variable shorthand
        "  wellDefinedName,",
        // getters
        "  get x() {},",
        "  get 'a'() {},",
        "  get ['a']() {},",
        "  get 456() {},",
        "  get [567]() {},",
        // setters
        "  set x(x) {},",
        "  set 'a'(v) {},",
        "  set ['a'](v) {},",
        "  set 678(v) {},",
        "  set [678](v) {},",
        "};"));
    assertThat(testedCollector.getTopLevelStatements().get(1).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testObjectLiteralWithImmovableIsImmovable() {
    assertStatementIsImmovable("var o = { v: unknownValue };");
    assertStatementIsImmovable("var o = { [unknownValue]: 1 };");
    assertStatementIsImmovable("var o = { [unknownValue]() {} };");
    assertStatementIsImmovable("var o = { get [unknownValue]() {} };");
    assertStatementIsImmovable("var o = { set [unknownValue](x) {} };");
  }

  private void assertStatementIsImmovable(String statement) {
    testSame(statement);
    assertThat(testedCollector.getTopLevelStatements().get(0).isMovableDeclaration()).isFalse();
  }

  @Test
  public void testTemplateLiteralIsMovableIfSubstitutionsAreMovable() {
    testSame(lines(
        "var wellDefinedName = 1;",
        "var t = `${wellDefinedName}`;"));
    assertThat(testedCollector.getTopLevelStatements().get(1).isMovableDeclaration()).isTrue();
  }

  @Test
  public void testTemplateLiteralIsImmovableIfSubstitutionsAreImmovable() {
    assertStatementIsImmovable("var t = `${unknownValue}`");
  }

  //  try to find cases to copy from CrossChunkCodeMotion
  private ReferenceCollection getReferencesForName(
      String name, CrossChunkReferenceCollector collector) {
    Var v = collector.getGlobalVariableNamesMap().get(name);
    assertThat(v).isNotNull();
    return collector.getReferences(v);
  }
}
