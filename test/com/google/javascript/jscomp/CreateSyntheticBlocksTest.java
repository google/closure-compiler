/*
 * Copyright 2009 Google Inc.
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

import com.google.javascript.rhino.Node;

/**
 * Tests for {@link CreateSyntheticBlocks}
 *
*
 */
public class CreateSyntheticBlocksTest extends CompilerTestCase {
  private static final String START_MARKER = "startMarker";
  private static final String END_MARKER = "endMarker";

  public CreateSyntheticBlocksTest() {
    // Can't use compare as a tree because of the added synthetic blocks.
    super("", false);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      public void process(Node externs, Node js) {
        new CreateSyntheticBlocks(compiler, START_MARKER, END_MARKER).process(
            externs, js);
        NodeTraversal.traverse(compiler, js, new FoldConstants(compiler));
        NodeTraversal.traverse(compiler, js, new MinimizeExitPoints(compiler));
        NodeTraversal.traverse(compiler, js, new FoldConstants(compiler));
        new Denormalize(compiler).process(externs, js);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testFold1() {
    test("function() { if (x) return; y(); }",
         "function(){if(!x)y()}");
  }

  public void testFoldWithMarkers1() {
    testSame("function(){startMarker();if(x)return;endMarker();y()}");
  }

  public void testFold2() {
    test("function() { if (x) return; y(); if (a) return; b(); }",
         "function(){if(!x){y();if(!a)b()}}");
  }

  public void testFoldWithMarkers2() {
    testSame("function(){startMarker(\"FOO\");startMarker(\"BAR\");" +
             "if(x)return;endMarker(\"BAR\");y();if(a)return;" +
             "endMarker(\"FOO\");b()}");
  }

  public void testUnmatchedStartMarker() {
    testSame("startMarker()", CreateSyntheticBlocks.UNMATCHED_START_MARKER);
  }

  public void testUnmatchedEndMarker1() {
    testSame("endMarker()", CreateSyntheticBlocks.UNMATCHED_END_MARKER);
  }

  public void testUnmatchedEndMarker2() {
    test("if(y){startMarker();x()}endMarker()",
        "if(y){startMarker();x()}endMarker()", null,
         CreateSyntheticBlocks.UNMATCHED_END_MARKER);
  }

  public void testDenormalize() {
    testSame("startMarker();for(;;);endMarker()");
  }

  public void testNonMarkingUse() {
    testSame("function foo(endMarker){}");
    testSame("function foo(){startMarker:foo()}");
  }
}
