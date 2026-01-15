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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CreateSyntheticBlocks} */
@RunWith(JUnit4.class)
public final class CreateSyntheticBlocksTest extends CompilerTestCase {
  private static final String START_MARKER = "startMarker";
  private static final String END_MARKER = "endMarker";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // Can't use compare as a tree because of the added synthetic blocks.
    disableCompareAsTree();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node js) {
        new CreateSyntheticBlocks(compiler, START_MARKER, END_MARKER).process(externs, js);
        Normalize.createNormalizeForOptimizations(compiler).process(externs, js);
        new PeepholeOptimizationsPass(
                compiler,
                getName(),
                new MinimizeExitPoints(),
                new PeepholeRemoveDeadCode(),
                new PeepholeMinimizeConditions(/* late= */ true),
                new PeepholeFoldConstants(true, false /* useTypes */))
            .process(externs, js);
        new Denormalize(compiler, FeatureSet.BARE_MINIMUM).process(externs, js);
      }
    };
  }

  // TODO(johnlenz): Add tests to the IntegrationTest.

  @Test
  public void testFold1() {
    test("function f() { if (x) return; y(); }", "function f(){x||y()}");
  }

  @Test
  public void testFoldWithMarkers1() {
    testSame("function f(){startMarker();if(x)return;endMarker();y()}");
  }

  @Test
  public void testFoldWithMarkers1a() {
    testSame("function f(){startMarker();if(x)return;endMarker()}");
  }

  @Test
  public void testFold2() {
    test(
        "function f() { if (x) return; y(); if (a) return; b(); }",
        "function f(){if(!x){y();a||b()}}");
  }

  @Test
  public void testFoldWithMarkers2() {
    testSame(
        """
        function f(){startMarker("FOO");startMarker("BAR");\
        if(x)return;endMarker("BAR");y();if(a)return;\
        endMarker("FOO");b()}\
        """);
  }

  @Test
  public void testUnmatchedStartMarker() {
    testError("startMarker()", CreateSyntheticBlocks.UNMATCHED_START_MARKER);
  }

  @Test
  public void testUnmatchedEndMarker1() {
    testError("endMarker()", CreateSyntheticBlocks.UNMATCHED_END_MARKER);
  }

  @Test
  public void testUnmatchedEndMarker2() {
    testError("if(y){startMarker();x()}endMarker()", CreateSyntheticBlocks.UNMATCHED_END_MARKER);
  }

  @Test
  public void testInvalid1() {
    testError("startMarker() && true", CreateSyntheticBlocks.INVALID_MARKER_USAGE);
  }

  @Test
  public void testInvalid2() {
    testError("false && endMarker()", CreateSyntheticBlocks.INVALID_MARKER_USAGE);
  }

  @Test
  public void testDenormalize() {
    testSame("startMarker();for(;;);endMarker()");
  }

  @Test
  public void testNonMarkingUse() {
    testSame("function foo(endMarker){}");
    testSame("function foo(){startMarker:foo()}");
  }

  @Test
  public void testContainingBlockPreservation() {
    testSame("if(y){startMarker();x();endMarker()}");
  }

  @Test
  public void testArrowFunction() {
    testSame("var y=()=>{startMarker();x();endMarker()}");
    testError(
        "var y=()=>{startMarker();x();};endMarker()", CreateSyntheticBlocks.UNMATCHED_END_MARKER);
    testError("var y=()=>startMarker();", CreateSyntheticBlocks.INVALID_MARKER_USAGE);
  }

  @Test
  public void testFunctionDeclaration1() {
    test(
        "startMarker(); a(); function fn() {}; b(); endMarker()",
        "startMarker();var fn=function(){};a();b();endMarker()");
  }

  @Test
  public void testFunctionDeclaration2() {
    testSame("startMarker();a();var fn=function(){};b();endMarker()");
  }

  @Test
  public void testClassDeclaration1() {
    // Document that classes are mishandled with regard to block scoping
    testSame("startMarker();class C{}endMarker()");
  }

  @Test
  public void testVariableDeclaration1() {
    // Document that let and const are mishandled with regard to block scoping
    testSame("startMarker();var x=1;endMarker()");
    testSame("startMarker();let x=1;endMarker()");
    testSame("startMarker();const x=1;endMarker()");
  }

  @Test
  public void testSyntheticBlock_doesNotCreateNewScope() {
    testSame("startMarker();var x=1;endMarker()");
    Node script = this.getLastCompiler().getJsRoot().getOnlyChild();
    assertNode(script).isScript();
    Node synctheticBlock = script.getFirstChild();
    assertNode(synctheticBlock).isBlock();
    assertThat(synctheticBlock.isSyntheticBlock()).isTrue();
    // confirm that synthetic block does not create a new block scope
    assertThat(NodeUtil.createsBlockScope(synctheticBlock)).isFalse();
  }
}
