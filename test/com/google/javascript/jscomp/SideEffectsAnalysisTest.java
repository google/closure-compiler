/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.SideEffectsAnalysis.LocationAbstractionMode;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@link SideEffectsAnalysis}.
 *
 * @author dcc@google.com (Devin Coughlin)
 *
 */
public final class SideEffectsAnalysisTest extends CompilerTestCase {

  private static final String SHARED_EXTERNS = "var arguments = [];";

  LocationAbstractionMode currentLocationAbstractionIdentifier;

  SideEffectsAnalysis currentAnalysis = null;

  Compiler currentCompiler = null;

  Node currentJsRoot = null;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    currentCompiler = compiler;

    currentAnalysis = new SideEffectsAnalysis(compiler,
        currentLocationAbstractionIdentifier);

    return new CompilerPass() {

      @Override
      public void process(Node externs, Node root) {

        if (currentLocationAbstractionIdentifier ==
          LocationAbstractionMode.VISIBILITY_BASED) {

          // Run var when using the visibility abstraction
          // because it is unsound if it fails.

          final VarCheck varCheck = new VarCheck(compiler);

          varCheck.process(externs, root);
        }

        currentAnalysis.process(externs, root);

      }
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    currentAnalysis = null;
    currentCompiler = null;
  }

  public void testDegenerateSafeMoves() {
    // Env is empty
    assertSafeMoveDegenerate("src: 1; env: ; dest: 3;");

    // Src and env pure
    assertSafeMoveDegenerate("src: 1; env: 2; dest: 3;");

    // Only refs
    assertSafeMoveDegenerate("src: 1; env: x; dest: 3;");
    assertSafeMoveDegenerate("src: x; env: 1; dest: 3;");

    // Only mods
    assertSafeMoveDegenerate("src: 1; env: x++; dest: 3;");

    assertSafeMoveDegenerate("src: x++; env: 1; dest: 3;");
  }

  public void testVisibilitySafeMoves() {
    // Env is empty
    assertSafeMoveVisibility("src: 1; env: ; dest: 3;");

    // Src and env pure
    assertSafeMoveVisibility("src: 1; env: 2; dest: 3;");

    // Only refs
    assertSafeMoveVisibility("var x; src: 1; env: x; dest: 3;");
    assertSafeMoveVisibility("var x; src: x; env: 1; dest: 3;");

    // Only mods
    assertSafeMoveVisibility("var x; src: 1; env: x++; dest: 3;");
    assertSafeMoveVisibility("var x; src: x++; env: 1; dest: 3;");

    // Source references global, env changes local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x;",
        "function f(){",
        "  var y;",
        "  src: x;",
        "  env: y++;",
        "  dest: 3;",
        "}"));

    // Source changes global, env refs local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x;",
        "function f(){",
        "  var y;",
        "  src: x++;",
        "  env: y;",
        "  dest: 3;",
        "}"));

    // Source references global, env changes local with shadowing
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x;",
        "var y;",
        "function f(){",
        "  var y;",
        "  src: x;",
        "  env: y++;",
        "  dest: 3;",
        "}"));

    // Source changes global, env refs local with shadowing
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x;",
        "var y;",
        "function f(){",
        "  var y;",
        "  src: x++;",
        "  env: y;",
        "  dest: 3;",
        "}"));


    // Source references captured local, env changes local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "function f(){",
        "  var x;",
        "  var y;",
        "  src: x;",
        "  env: y++;",
        "  dest: 3;",
        "  function inner() {",
        "    x",
        "  }",
        "}"));

    // Source changes captured local, env refs local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "function f(){",
        "  var x;",
        "  var y;",
        "  src: x++;",
        "  env: y;",
        "  dest: 3;",
        "  function inner() {",
        "    x",
        "  }",
        "}"));

    // Source references heap, env changes local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(){",
        "  var y;",
        "  src: x.a;",
        "  env: y++;",
        "  dest: 3;",
        "}"));

    // Source changes heap, env refs local
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(){",
        "  var y;",
        "  src: x.a++;",
        "  env: y;",
        "  dest: 3;",
        "}"));

    // MOD in function expressions shouldn't count
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "src: x.a;",
        "env: (function() {",
        "  x.a++;",
        "});",
        "dest: 3;"));

    // REF in function expressions shouldn't count
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "src: x.a++;",
        "env: (function() {",
        "  x.a;",
        "});",
        "dest: 3;"));

  }

  public void testDegenerateUnsafeMoves() {

    // Unsafe to move increment across read
    assertUnsafeMoveDegenerate("src: x++; env: foo(y); dest: 3;");

    // Unsafe to move read across increment
    assertUnsafeMoveDegenerate("src: foo(y); env: x++; dest: 3;");

    // Unsafe to move write across write
    assertUnsafeMoveDegenerate("src: x = 7; env: y = 3; dest:3;");
  }

  public void testVisibilityUnsafeMoves() {

    // Unsafe to move increment across read for global variables
    assertUnsafeMoveVisibility("var x,y; src: x++; env: y; dest: 3;");

    // Unsafe to move increment across read for local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x++; env: y; dest: 3;",
        "}"));

    // Unsafe to move increment across read for captured local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x++; env: y; dest: 3;",
        "  function inner() {",
        "    x; y;",
        "  }",
        "}"));

    // Unsafe to move increment across read for heap locations
    assertUnsafeMoveVisibility("var x,y; src: x.a++; env: y.b; dest: 3;");

    // Unsafe to move read across increment of for global variables
    assertUnsafeMoveVisibility("var x,y; src: y; env: x++; dest: 3;");

    // Unsafe to move read across increment for local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x; env: y++; dest: 3;",
        "}"));

    // Unsafe to move read across increment for captured local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x; env: y++; dest: 3;",
        "  function inner() {",
        "    x; y;",
        "  }",
        "}"));

    // Unsafe to move read across increment for heap locations
    assertUnsafeMoveVisibility("var x,y; src: x.a; env: y.b++; dest: 3;");

    // Unsafe to move write across write for globals
    assertUnsafeMoveVisibility("var x,y; src: x = 7; env: y = 3; dest: 3;");

    // Unsafe to move write across write for local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x = 7; env: y = 3; dest: 3;",
        "}"));

    // Unsafe to move write across write for captured local variables
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function f() {",
        "  var x,y; src: x = 7; env: y = 3; dest: 3;",
        "  function inner() {",
        "    x; y;",
        "  }",
        "}"));

    // Unsafe to move write across write for heap locations
    assertUnsafeMoveVisibility("var x,y; src: x.a = 7; env: y.b = 3; dest: 3;");
  }

  public void testVisibilityMoveCalls() {
    // Interprocedural side effect analysis isn't implemented yet, so any calls
    // should make movement unsafe, since we don't know what those calls are
    // doing.

    // TODO(dcc): implement interprocedural side effect analysis.

    // Source makes call, env refs global
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "var g = function(){};",
        "function f(){",
        "  var y;",
        "  src: g();",
        "  env: x;",
        "  dest: 3;",
        "}"));

    // Source makes refs global, env makes call
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "var g = function(){};",
        "function f(){",
        "  var y;",
        "  src: x;",
        "  env: g();",
        "  dest: 3;",
        "}"));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    // Source makes a taggedTemplate Call.
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function taggedTemplate(){};",
        "function f(){",
        "  src: taggedTemplate`tTemplate`;",
        "  env: 24;",
        "  dest: 42;",
        "}"));

    // Env makes a taggedTemplate Call.
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "function taggedTemplate(){};",
        "function f(){",
        "  src: 24;",
        "  env: taggedTemplate`tTemplate`;",
        "  dest: 42;",
        "}"));
  }

  public void testVisibilityMergesParametersWithHeap() {
    // For now, we expect the visibility based location abstraction
    // to merge parameter variable locations with heap locations because
    // parameters can be references and modified via the arguments object.

    // Source changes heap, env refs parameter
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(y){",
        "  src: x[0]++;",
        "  env: y;",
        "  dest: 3;",
        "}"));

    // Source refs heap, env changes parameters
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(y){",
        "  src: x[0];",
        "  env: y++;",
        "  dest: 3;",
        "}"));

    // Source changes arguments explicitly, env refs parameter
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(y){",
        "  src: arguments[0]++;",
        "  env: y;",
        "  dest: 3;",
        "}"));

    // Source refs arguments explicitly, env changes parameter
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var x = {};",
        "function f(y){",
        "  src: arguments[0];",
        "  env: y++;",
        "  dest: 3;",
        "}"));
  }

  public void testMovedSideEffectsMustHaveSameControlFlow() {

    // Safe to move within IF block
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  if (l) {",
        "    src: a++;",
        "    env: 3;",
        "    dest: 3;",
        "  }",
        "}"));

    // Unsafe to move between two IF blocks
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  if (l) {",
        "    src: a++;",
        "    env: 3;",
        "  }",
        "  if (l) {",
        "    dest: 3;",
        "  }",
        "}"));

    // Unsafe to move between then/else of same IF block
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  if (l) {",
        "    src: a++;",
        "    env: 3;",
        "  } else {",
        "    dest: 3;",
        "  }",
        "}"));

    // Safe to move within WHILE block
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  while (l) {",
        "    src: a++;",
        "    env: 3;",
        "    dest: 3;",
        "  }",
        "}"));

    // Unsafe to move within WHILE block with BREAK
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  while (l) {",
        "    src: a++;",
        "    env: l;",
        "    break;",
        "    dest: 3;",
        "  }",
        "}"));

    // Unsafe to move within WHILE block with continue
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  while (l) {",
        "    src: a++;",
        "    env: 3;",
        "    continue;",
        "    dest: 3;",
        "  }",
        "}"));

    // Unsafe to move within WHILE block with continue
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  while (l) {",
        "    src: a++;",
        "    env: 3;",
        "    return;",
        "    dest: 3;",
        "  }",
        "}"));

    // Safe to move within DO
    assertSafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  do {",
        "    src: a++;",
        "    env: 3;",
        "    dest: 3;",
        "  } while(l)",
        "}"));

    // Unsafe to move outside DO
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  do {",
        "    src: a++;",
        "    env: 3;",
        "  } while(l)",
        "  dest: 3;",
        "}"));

    // It should be safe to move within CASE
    // but we disallow for now because analyzing
    // CASE fall-through and BREAKs is complicated.
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  switch(l) {",
        "    case 17:",
        "      src: a++;",
        "      env: 3;",
        "      dest: 3;",
        "    break;",
        "  }",
        "}"));

    // Unsafe to move between CASEs
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  var l;",
        "  switch(l) {",
        "    case 17:",
        "      src: a++;",
        "      env: 3;",
        "    break;",
        "    case 18:",
        "      dest: 3;",
        "    break;",
        "  }",
        "}"));

    // Unsafe to move between FUNCTIONs
    assertUnsafeMoveVisibility(LINE_JOINER.join(
        "var a;",
        "function f() {",
        "  src: a++;",
        "  env: 3;",
        "}",
        "function g() {",
        "  dest: 3;",
        "}"));
  }

  private SideEffectsAnalysis.AbstractMotionEnvironment environment(
      Node ...nodes) {

    return new SideEffectsAnalysis.RawMotionEnvironment(
        ImmutableSet.copyOf(nodes));
  }

  /**
   * Asserts whether it is safe to move code labeled with {@code src} across
   * an environment labeled {@code env} to another program point (immediately
   * preceding code labeled {@code dest}).
   *
   * @param abstraction The type of location abstraction to use for analysis
   * @param src The code snippet under test.
   * @param expected The expected result of analysis
   */
  private void assertMove(LocationAbstractionMode abstraction,
      String src,
      boolean expected) {
    SideEffectsAnalysis analysis = compileAndRun(src, abstraction);

    Node sourceNode = findLabeledStatement("src");
    Node environmentNode = findLabeledStatement("env");
    Node destinationNode = findLabeledStatement("dest");

    boolean result = analysis.safeToMoveBefore(sourceNode,
        environment(environmentNode), destinationNode);

    if (expected) {
      assertTrue(result);
    } else {
      assertFalse(result);
    }
  }

  private void assertSafeMoveDegenerate(String src) {
    assertMove(LocationAbstractionMode.DEGENERATE, src, true);
  }

  private void assertUnsafeMoveDegenerate(String src) {
    assertMove(LocationAbstractionMode.DEGENERATE, src, false);
  }

  private void assertSafeMoveVisibility(String src) {
    assertMove(LocationAbstractionMode.VISIBILITY_BASED, src, true);
  }

  private void assertUnsafeMoveVisibility(String src) {
    assertMove(LocationAbstractionMode.VISIBILITY_BASED, src, false);
  }

  private SideEffectsAnalysis compileAndRun(String js,
      LocationAbstractionMode locationAbstractionIdentifier) {

    currentLocationAbstractionIdentifier = locationAbstractionIdentifier;

    testSame(SHARED_EXTERNS, js, null);

    currentJsRoot = currentCompiler.jsRoot;

    return currentAnalysis;
  }

  // Shamelessly stolen from NameReferenceGraphConstructionTest
  private Node findLabeledStatement(String label) {
    LabeledStatementSearcher s = new LabeledStatementSearcher(label);

    NodeTraversal.traverseEs6(currentCompiler, currentCompiler.jsRoot, s);
    assertNotNull("Label " + label + " should be in the source code", s.found);

    return s.found;
  }

  /**
   * Quick traversal to find a given labeled statement in the AST.
   *
   * Given "foo", finds the statement a = x in
   * foo: a = x;
   */
  private class LabeledStatementSearcher extends AbstractPostOrderCallback {
    Node found = null;
    final String target;

    LabeledStatementSearcher(String target) {
      this.target = target;
    }
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isLabel() &&
          target.equals(n.getFirstChild().getString())) {

        found = n.getLastChild();
      }
    }
  }
}
