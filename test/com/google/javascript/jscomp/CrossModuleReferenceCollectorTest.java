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
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CrossModuleReferenceCollector.TopLevelStatement;
import com.google.javascript.rhino.Token;
import java.util.List;

public final class CrossModuleReferenceCollectorTest extends CompilerTestCase {
  private CrossModuleReferenceCollector testedCollector;

  @Override
  public void setUp() {
    setLanguage(ECMASCRIPT_NEXT, ECMASCRIPT_NEXT);
  }

  @Override
  protected int getNumRepetitions() {
    // Default behavior for CompilerTestCase.test*() methods is to do the whole test twice,
    // because passes that modify the AST need to be idempotent.
    // Since CrossModuleReferenceCollector() just gathers information, it doesn't make sense to
    // run it twice, and doing so just complicates debugging test cases.
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);
    testedCollector = new CrossModuleReferenceCollector(
        compiler,
        scopeCreator);
    return testedCollector;
  }

  public void testVarInBlock() {
    testSame(LINE_JOINER.join(
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

  public void testVarAssignedOnceInLifetime2() {
    testSame("{ var x = 0; }");
    ImmutableMap<String, Var> globalVariableNamesMap = testedCollector.getGlobalVariableNamesMap();
    Var xVar = globalVariableNamesMap.get("x");
    assertThat(globalVariableNamesMap).containsKey("x");
    ReferenceCollection xRefs = testedCollector.getReferences(xVar);
    assertThat(xRefs.isAssignedOnceInLifetime()).isTrue();
  }

  public void testBasicBlocks() {
    testSame(LINE_JOINER.join(
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

  public void testTopLevelStatements() {
    testSame(LINE_JOINER.join(
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
    assertThat(topLevelStatements.get(0).getContainedReferences())
        .containsExactly(xReferences.get(0));
    // const y = x;
    assertThat(topLevelStatements.get(1).getContainedReferences())
        .containsExactly(yReferences.get(0), xReferences.get(1));
    // let z = x - y;
    assertThat(topLevelStatements.get(2).getContainedReferences())
        .containsExactly(zReferences.get(0), xReferences.get(2), yReferences.get(1));
    // function f(x, y) { return x + y + z; }
    assertThat(topLevelStatements.get(3).getContainedReferences())
        .containsExactly(fReferences.get(0), zReferences.get(1));
  }
}
