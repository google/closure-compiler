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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractNodeTypePruningCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link NodeTraversal}.
 */
public class NodeTraversalTest extends TestCase {
  public void testPruningCallbackShouldTraverse1() {
    PruningCallback include =
      new PruningCallback(ImmutableSet.of(Token.SCRIPT, Token.VAR), true);

    Node script = new Node(Token.SCRIPT);
    assertTrue(include.shouldTraverse(null, script, null));
    assertTrue(include.shouldTraverse(null, new Node(Token.VAR), null));
    assertFalse(include.shouldTraverse(null, new Node(Token.NAME), null));
    assertFalse(include.shouldTraverse(null, new Node(Token.ADD), null));
  }

  public void testPruningCallbackShouldTraverse2() {
    PruningCallback include =
      new PruningCallback(ImmutableSet.of(Token.SCRIPT, Token.VAR), false);

    Node script = new Node(Token.SCRIPT);
    assertFalse(include.shouldTraverse(null, script, null));
    assertFalse(include.shouldTraverse(null, new Node(Token.VAR), null));
    assertTrue(include.shouldTraverse(null, new Node(Token.NAME), null));
    assertTrue(include.shouldTraverse(null, new Node(Token.ADD), null));
  }

  /**
   * Concrete implementation of AbstractPrunedCallback to test the
   * AbstractNodeTypePruningCallback shouldTraverse method.
   */
  static class PruningCallback extends AbstractNodeTypePruningCallback {
    public PruningCallback(Set<Integer> nodeTypes, boolean include) {
      super(nodeTypes, include);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      throw new UnsupportedOperationException();
    }
  }

  public void testReport() {
    final List<JSError> errors = new ArrayList<JSError>();

    Compiler compiler = new Compiler(new BasicErrorManager() {

      @Override public void report(CheckLevel level, JSError error) {
        errors.add(error);
      }

      @Override public void println(CheckLevel level, JSError error) {
      }

      @Override protected void printSummary() {
      }
    });
    compiler.initCompilerOptionsIfTesting();

    NodeTraversal t = new NodeTraversal(compiler, null);
    DiagnosticType dt = DiagnosticType.warning("FOO", "{0}, {1} - {2}");

    t.report(null, dt, "Foo", "Bar", "Hello");
    assertEquals(1, errors.size());
    assertEquals("Foo, Bar - Hello", errors.get(0).description);
  }

  public void testUnexpectedException() {
    final String TEST_EXCEPTION = "test me";

    NodeTraversal.Callback cb = new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        throw new RuntimeException(TEST_EXCEPTION);
      }
    };

    Compiler compiler = new Compiler();
    NodeTraversal t = new NodeTraversal(compiler, cb);
    String code = "function foo() {}";
    Node tree = parse(compiler, code);

    try {
      t.traverse(tree);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().startsWith(
          "INTERNAL COMPILER ERROR.\n" +
          "Please report this problem.\n" +
          "test me"));
    }
  }


  public void testGetScopeRoot() {
    Compiler compiler = new Compiler();
    NodeTraversal t = new NodeTraversal(compiler,
        new NodeTraversal.ScopedCallback() {

          @Override
          public void enterScope(NodeTraversal t) {
            Node root1 = t.getScopeRoot();
            Node root2 = t.getScope().getRootNode();
            assertEquals(root1, root2);
          }

          @Override
          public void exitScope(NodeTraversal t) {
          }

          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            return true;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
          }
        }
    );

    String code = "" +
            "var a; " +
            "function foo() {" +
            "  var b" +
            "}";
    Node tree = parse(compiler, code);
    t.traverse(tree);
  }

  public void testGetCurrentNode() {
    Compiler compiler = new Compiler();
    ScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal t = new NodeTraversal(compiler, callback, creator);

    String code = "" +
            "var a; " +
            "function foo() {" +
            "  var b;" +
            "}";

    Node tree = parse(compiler, code);
    Scope topScope = creator.createScope(tree, null);

    // Calling #traverseWithScope uses the given scope but starts traversal at
    // the given node.
    callback.expect(tree.getFirstChild(), tree);
    t.traverseWithScope(tree.getFirstChild(), topScope);
    callback.assertEntered();

    // Calling #traverse creates a new scope with the given node as the root.
    callback.expect(tree.getFirstChild(), tree.getFirstChild());
    t.traverse(tree.getFirstChild());
    callback.assertEntered();

    // Calling #traverseAtScope starts traversal from the scope's root.
    Node fn = tree.getFirstChild().getNext();
    Scope fnScope = creator.createScope(fn, topScope);
    callback.expect(fn, fn);
    t.traverseAtScope(fnScope);
    callback.assertEntered();
  }


  // Helper class used to test getCurrentNode
  private static class ExpectNodeOnEnterScope implements
      NodeTraversal.ScopedCallback {
    private Node node;
    private Node scopeRoot;
    private boolean entered = false;

    private void expect(Node node, Node scopeRoot) {
      this.node = node;
      this.scopeRoot = scopeRoot;
      entered = false;
    }

    private void assertEntered() {
      assertTrue(entered);
    }

    @Override
    public void enterScope(NodeTraversal t) {
      assertEquals(node, t.getCurrentNode());
      assertEquals(scopeRoot, t.getScopeRoot());
      entered = true;
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    return n;
  }
}
