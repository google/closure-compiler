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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
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
public final class NodeTraversalTest extends TestCase {
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
    final List<JSError> errors = new ArrayList<>();

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

    t.report(new Node(Token.EMPTY), dt, "Foo", "Bar", "Hello");
    assertThat(errors).hasSize(1);
    assertEquals("Foo, Bar - Hello", errors.get(0).description);
  }

  private static final String TEST_EXCEPTION = "test me";

  public void testUnexpectedException() {

    NodeTraversal.Callback cb = new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        throw new RuntimeException(TEST_EXCEPTION);
      }
    };

    Compiler compiler = new Compiler();

    try {
      String code = "function foo() {}";
      Node tree = parse(compiler, code);
      NodeTraversal.traverseEs6(compiler, tree, cb);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .startsWith("INTERNAL COMPILER ERROR.\n"
              + "Please report this problem.\n\n"
              + "test me");
    }
  }


  public void testGetScopeRoot() {
    Compiler compiler = new Compiler();
    String code = Joiner.on('\n').join(
        "var a;",
        "function foo() {",
        "  var b",
        "}");
    Node tree = parse(compiler, code);
    NodeTraversal.traverseEs6(compiler, tree,
        new NodeTraversal.ScopedCallback() {

          @Override
          public void enterScope(NodeTraversal t) {
            Node root1 = t.getScopeRoot();
            Node root2 = t.getScope().getRootNode();
            assertNode(root2).isEqualTo(root1);
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
  }

  public void testGetLineNoAndGetCharno() {
    Compiler compiler = new Compiler();
    String code = ""
        + "var a; \n"
        + "function foo() {\n"
        + "  var b;\n"
        + "  if (a) { var c;}\n"
        + "}";
    Node tree = parse(compiler, code);
    final StringBuilder builder = new StringBuilder();
    NodeTraversal.traverseEs6(compiler, tree,
        new NodeTraversal.ScopedCallback() {

          @Override
          public void enterScope(NodeTraversal t) {
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
            builder.append("visit ");
            builder.append(t.getCurrentNode().toString(false, true, true));
            builder.append(" @");
            builder.append(t.getLineNumber());
            builder.append(":");
            builder.append(t.getCharno());
            builder.append("\n");
          }
        }
    );

    // Note the char numbers are 0-indexed but the line numbers are 1-indexed.
    String expectedResult = ""
        + "visit NAME a [source_file: [testcode]] @1:4\n"
        + "visit VAR [source_file: [testcode]] @1:0\n"
        + "visit NAME foo [source_file: [testcode]] @2:9\n"
        + "visit PARAM_LIST [source_file: [testcode]] @2:12\n"
        + "visit NAME b [source_file: [testcode]] @3:6\n"
        + "visit VAR [source_file: [testcode]] @3:2\n"
        + "visit NAME a [source_file: [testcode]] @4:6\n"
        + "visit NAME c [source_file: [testcode]] @4:15\n"
        + "visit VAR [source_file: [testcode]] @4:11\n"
        + "visit BLOCK [source_file: [testcode]] @4:9\n"
        + "visit IF [source_file: [testcode]] @4:2\n"
        + "visit BLOCK [source_file: [testcode]] @2:15\n"
        + "visit FUNCTION foo [source_file: [testcode]] @2:0\n"
        + "visit SCRIPT [synthetic: 1] [source_file: [testcode]] "
        + "[input_id: InputId: [testcode]] "
        + "[feature_set: FeatureSet{number=3}] @1:0\n";

    assertEquals(expectedResult, builder.toString());
  }

  public void testGetCurrentNode() {
    Compiler compiler = new Compiler();
    ScopeCreator creator = SyntacticScopeCreator.makeUntyped(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal t = new NodeTraversal(compiler, callback, creator);

    String code = Joiner.on('\n').join(
        "var a;",
        "function foo() {",
        "  var b;",
        "}");

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
    Node fn = tree.getSecondChild();
    Scope fnScope = creator.createScope(fn, topScope);
    callback.expect(fn, fn);
    t.traverseAtScope(fnScope);
    callback.assertEntered();
  }

  public void testTraverseAtScopeWithBlockScope() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    compiler.initOptions(options);
    ScopeCreator creator = new Es6SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal t = new NodeTraversal(compiler, callback, creator);

    String code = Joiner.on('\n').join(
        "function foo() {",
        "  if (bar) {",
        "    let x;",
        "  }",
        "}");

    Node tree = parse(compiler, code);
    Scope topScope = creator.createScope(tree, null);

    Node innerBlock = tree  // script
        .getFirstChild()    // function
        .getLastChild()     // function body
        .getFirstChild()    // if
        .getLastChild();    // block

    Scope blockScope = creator.createScope(innerBlock, topScope);
    callback.expect(innerBlock, innerBlock);
    t.traverseAtScope(blockScope);
    callback.assertEntered();
  }

  // Helper class used to test getCurrentNode
  private static class ExpectNodeOnEnterScope extends NodeTraversal.AbstractPreOrderCallback
      implements NodeTraversal.ScopedCallback {
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
      assertNode(t.getCurrentNode()).isEqualTo(node);
      assertNode(t.getScopeRoot()).isEqualTo(scopeRoot);
      entered = true;
    }

    @Override
    public void exitScope(NodeTraversal t) {
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }
}
