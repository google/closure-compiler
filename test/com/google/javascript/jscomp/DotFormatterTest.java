/*
 * Copyright 2007 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

public class DotFormatterTest extends TestCase {
  /**
   * Tests that keys are assigned sequentially.
   */
  public void testKeyAssignementSequential() throws Exception {
    DotFormatter dot = DotFormatter.newInstanceForTesting();
    assertEquals(0, dot.key(new Node(Token.BLOCK)));
    assertEquals(1, dot.key(new Node(Token.BLOCK)));
    assertEquals(2, dot.key(new Node(Token.BLOCK)));
    assertEquals(3, dot.key(new Node(Token.BLOCK)));
    assertEquals(4, dot.key(new Node(Token.BLOCK)));
  }

  /**
   * Tests that keys are assigned once per node.
   */
  public void testKeyAssignementOncePerNode() throws Exception {
    DotFormatter dot = DotFormatter.newInstanceForTesting();
    Node node0 = new Node(Token.BLOCK);
    Node node1 = new Node(Token.BLOCK);
    Node node2 = new Node(Token.BLOCK);

    assertEquals(0, dot.key(node0));
    assertEquals(1, dot.key(node1));
    assertEquals(2, dot.key(node2));
    assertEquals(0, dot.key(node0));
    assertEquals(1, dot.key(node1));
    assertEquals(2, dot.key(node2));
  }

  /**
   * Tests the formatting (simple tree).
   */
  public void testToDotSimple() throws Exception {
    Node ast = new Node(Token.BITOR);

    String expected = "digraph AST {\n" +
        "  node [color=lightblue2, style=filled];\n" +
        "  node0 [label=\"BITOR\"];\n" +
        "}\n";
    test(expected, ast);
  }

  /**
   * Tests the formatting (3 element tree).
   */
  public void testToDot3Elements() throws Exception {
    Node ast = new Node(Token.BLOCK);
    ast.addChildToBack(new Node(Token.NAME));
    ast.addChildToBack(new Node(Token.STRING));

    String expected = "digraph AST {\n" +
        "  node [color=lightblue2, style=filled];\n" +
        "  node0 [label=\"BLOCK\"];\n" +
        "  node1 [label=\"NAME\"];\n" +
        "  node0 -> node1 [weight=1];\n" +
        "  node2 [label=\"STRING\"];\n" +
        "  node0 -> node2 [weight=1];\n" +
        "}\n";
    test(expected, ast);
  }

  private void test(String expected, Node ast) {
    try {
      assertEquals(expected, DotFormatter.toDot(ast));
    } catch (java.io.IOException e) {
      fail("Tests failed with IOExceptions");
    }
  }
}
