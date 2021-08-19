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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallbackInterface;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NodeTraversal}. */
@RunWith(JUnit4.class)
public final class NodeTraversalTest {
  @Test
  public void testReport() {
    final List<JSError> errors = new ArrayList<>();
    DiagnosticType dt = DiagnosticType.warning("FOO", "{0}, {1} - {2}");

    NodeTraversal.Callback callback =
        new NodeTraversal.Callback() {
          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            t.report(n, dt, "Foo", "Bar", "Hello");
            return false;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            throw new AssertionError();
          }
        };

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

    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(callback)
        .traverse(new Node(Token.EMPTY));

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getDescription()).isEqualTo("Foo, Bar - Hello");
  }

  private static final String TEST_EXCEPTION = "test me";

  @Test
  public void testUnexpectedException() {

    AbstractPostOrderCallbackInterface cb =
        (NodeTraversal t, Node n, Node parent) -> {
          throw new RuntimeException(TEST_EXCEPTION);
        };

    Compiler compiler = new Compiler();

    try {
      String code = "function foo() {}";
      Node tree = parse(compiler, code);
      NodeTraversal.builder().setCompiler(compiler).setCallback(cb).traverse(tree);
      assertWithMessage("Expected RuntimeException").fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .startsWith("INTERNAL COMPILER ERROR.\nPlease report this problem.\n\ntest me");
    }
  }

  @Test
  public void testGetScopeRoot() {
    Compiler compiler = new Compiler();
    String code = lines(
        "var a;",
        "function foo() {",
        "  var b",
        "}");
    Node tree = parse(compiler, code);
    NodeTraversal.traverse(
        compiler,
        tree,
        new NodeTraversal.ScopedCallback() {

          @Override
          public void enterScope(NodeTraversal t) {
            Node root1 = t.getScopeRoot();
            Scope scope2 = t.getScope();
            Node root2 = scope2.getRootNode();
            assertNode(root2).isEqualTo(root1);
          }

          @Override
          public void exitScope(NodeTraversal t) {}

          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            return true;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {}
        });
  }

  @Test
  public void testGetScopeRoot_inEsModule() {
    Compiler compiler = new Compiler();
    String code =
        lines(
            "const x = 0;", //
            "export {x};");
    Node tree = parse(compiler, code);
    NodeTraversal.traverse(
        compiler,
        tree,
        new NodeTraversal.Callback() {
          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            if (n.isModuleBody() || (parent != null && parent.isModuleBody())) {
              assertNode(t.getScopeRoot()).hasToken(Token.MODULE_BODY);
            }
            return true;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isModuleBody() || (parent != null && parent.isModuleBody())) {
              assertNode(t.getScopeRoot()).hasToken(Token.MODULE_BODY);
            }
          }
        });
  }

  @Test
  public void testGetScopeRoot_inGoogModule() {
    Compiler compiler = new Compiler();
    String code =
        lines(
            "goog.module('a.b');", //
            "function foo() {",
            "  var b",
            "}");
    Node tree = parse(compiler, code);
    NodeTraversal.traverse(
        compiler,
        tree,
        new NodeTraversal.Callback() {
          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            if (n.isModuleBody() || (parent != null && parent.isModuleBody())) {
              assertNode(t.getScopeRoot()).hasToken(Token.MODULE_BODY);
            }
            return true;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isModuleBody() || (parent != null && parent.isModuleBody())) {
              assertNode(t.getScopeRoot()).hasToken(Token.MODULE_BODY);
            }
          }
        });
  }

  @Test
  public void testGetHoistScopeRoot() {
    Compiler compiler = new Compiler();
    String code = lines(
        "function foo() {",
        "  if (true) { var XXX; }",
        "}");
    Node tree = parse(compiler, code);
    NodeTraversal.traverse(compiler, tree,
        new NodeTraversal.Callback() {

          @Override
          public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            return true;
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isName() && n.getString().equals("XXX")) {
              Node root = t.getClosestHoistScopeRoot();
              assertThat(NodeUtil.isFunctionBlock(root)).isTrue();

              t.getScope();  // force scope creation

              root = t.getClosestHoistScopeRoot();
              assertThat(NodeUtil.isFunctionBlock(root)).isTrue();
            }
          }
        }
    );
  }

  private static class NameChangingCallback implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && n.getString().equals("change")) {
        n.setString("xx");
        t.reportCodeChange();
      }
    }
  }

  @Test
  public void testReportChange1() {
    String code = lines(
        "var change;",
        "function foo() {",
        "  var b",
        "}");
    assertChangesRecorded(code, new NameChangingCallback());
  }

  @Test
  public void testReportChange2() {
    String code = lines(
        "var a;",
        "function foo() {",
        "  var change",
        "}");
    assertChangesRecorded(code, new NameChangingCallback());
  }

  @Test
  public void testReportChange3() {
    String code = lines(
        "var a;",
        "function foo() {",
        "  var b",
        "}",
        "var change");
    assertChangesRecorded(code, new NameChangingCallback());
  }

  @Test
  public void testReportChange4() {
    String code = lines(
        "function foo() {",
        "  function bar() {",
        "    var change",
        "  }",
        "}");
    assertChangesRecorded(code, new NameChangingCallback());
  }

  private void assertChangesRecorded(String code, NodeTraversal.Callback callback) {
    final String externs = "";
    Compiler compiler = new Compiler();
    Node tree = parseRoots(compiler, externs, code);

    ChangeVerifier changeVerifier = new ChangeVerifier(compiler).snapshot(tree);
    NodeTraversal.traverseRoots(
        compiler, callback,  tree.getFirstChild(), tree.getSecondChild());
    changeVerifier.checkRecordedChanges(tree);
  }

  @Test
  public void testGetCurrentNode() {
    Compiler compiler = new Compiler();
    ScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    String code = lines(
        "var a;",
        "function foo() {",
        "  var b;",
        "}");

    Node tree = parse(compiler, code);
    Scope topScope = (Scope) creator.createScope(tree, null);

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
    Scope fnScope = (Scope) creator.createScope(fn, topScope);
    callback.expect(fn, fn);
    t.traverseAtScope(fnScope);
    callback.assertEntered();
  }

  @Test
  public void testTraverseAtScopeWithBlockScope() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    SyntacticScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    String code = lines(
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

  @Test
  public void testTraverseAtScopeWithForScope() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    SyntacticScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    String code =
        lines(
            "function foo() {",
            "  var b = [0];",
            "  for (let a of b) {",
            "    let x;", "  }",
            "}");

    Node tree = parse(compiler, code);
    Scope topScope = creator.createScope(tree, null);

    Node forNode =
        tree // script
            .getFirstChild() // function
            .getLastChild() // function body
            .getSecondChild(); // for (first child is var b)

    Node innerBlock = forNode.getLastChild();

    Scope forScope = creator.createScope(forNode, topScope);
    creator.createScope(innerBlock, forScope);

    callback.expect(forNode, forNode);
    t.traverseAtScope(forScope);
    callback.assertEntered();
  }

  @Test
  public void testTraverseAtScopeWithSwitchScope() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    SyntacticScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    String code =
        lines(
            "function foo() {",
            "  var b = [0];",
            "  switch(b) {",
            "    case 1:",
            "       return b;",
            "    case 2:",
            "  }",
            "}");

    Node tree = parse(compiler, code);
    Scope topScope = creator.createScope(tree, null);

    Node innerBlock =
        tree // script
            .getFirstChild() // function
            .getLastChild() // function body
            .getSecondChild(); // switch (first child is var b)

    Scope blockScope = creator.createScope(innerBlock, topScope);
    callback.expect(innerBlock, innerBlock);
    t.traverseAtScope(blockScope);
    callback.assertEntered();
  }

  @Test
  public void testTraverseAtScopeWithModuleScope() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    SyntacticScopeCreator creator = new SyntacticScopeCreator(compiler);
    ExpectNodeOnEnterScope callback = new ExpectNodeOnEnterScope();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    String code = lines(
        "goog.module('example.module');",
        "",
        "var x;");

    Node tree = parse(compiler, code);
    Scope globalScope = creator.createScope(tree, null);
    Node moduleBody = tree.getFirstChild();
    Scope moduleScope = creator.createScope(moduleBody, globalScope);

    callback.expect(moduleBody, moduleBody);

    t.traverseAtScope(moduleScope);

    callback.assertEntered();
  }

  @Test
  public void testGetVarAccessible() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    SyntacticScopeCreator creator = new SyntacticScopeCreator(compiler);
    AccessibleCallback callback = new AccessibleCallback();
    NodeTraversal.Builder t =
        NodeTraversal.builder()
            .setCompiler(compiler)
            .setCallback(callback)
            .setScopeCreator(creator);

    // variables are hoisted to their enclosing scope
    String code =
        lines(
            "var varDefinedInScript;",
            "var foo = function(param) {",
            "  var varDefinedInFoo;",
            "  var baz = function() {",
            "    var varDefinedInBaz;",
            "  }",
            "}",
            "var bar = function() {",
            "  var varDefinedInBar;",
            "}");

    // the function scope should have access to all variables defined before and in the function
    // scope
    Node tree = parse(compiler, code);
    Node fooNode =
        tree // script
        .getSecondChild() // var foo declaration (first child is var varDefinedInScript)
        .getFirstFirstChild(); // child of the var foo declaration is the foo function
    Scope topScope = creator.createScope(tree, null);
    Scope fooScope = creator.createScope(fooNode, topScope);
    callback.expect(4);
    t.traverseAtScope(fooScope);
    callback.assertAccessible(fooScope);

    // the function block scope should have access to all variables defined in the global, function,
    // and function block scopes
    Node fooBlockNode = fooNode.getLastChild();
    Scope fooBlockScope = creator.createScope(fooBlockNode, fooScope);
    callback.expect(6);
    t.traverseAtScope(fooBlockScope);
    callback.assertAccessible(fooBlockScope);

    // let and const variables are block scoped
    code =
        lines(
            "var foo = function() {",
            "  var varDefinedInFoo;",
            "  var baz = function() {",
            "    var varDefinedInBaz;",
            "    let varDefinedInFoo;", // shadows parent scope
            "  }",
            "  let bar = 1;",
            "}");

    // the baz block scope has access to variables in its scope and parent scopes
    tree = parse(compiler, code);
    fooNode =
        tree // script
        .getFirstChild()// var foo declaration (first child is var varDefinedInScript)
        .getFirstFirstChild(); // child of the var foo declaration is the foo function
    fooBlockNode = fooNode.getLastChild(); // first child is param list of foo
    Node bazNode = fooBlockNode.getSecondChild().getFirstFirstChild();
    Node bazBlockNode = bazNode.getLastChild();

    topScope = creator.createScope(tree, null);
    fooScope = creator.createScope(fooNode, topScope);
    fooBlockScope = creator.createScope(fooBlockNode, fooScope);
    Scope bazScope = creator.createScope(bazNode, fooBlockScope);
    Scope bazBlockScope = creator.createScope(bazBlockNode, bazScope);

    // bar, baz, foo, varDefinedInFoo(in baz function), varDefinedInBaz
    callback.expect(5);
    t.traverseAtScope(bazBlockScope);
    callback.assertAccessible(bazBlockScope);
  }

  @Test
  public void testTraverseEs6ScopeRoots_isLimitedToScope() {
    Compiler compiler = new Compiler();
    StringAccumulator callback = new StringAccumulator();

    String code =
        lines(
            "function foo() {",
            "  'string in foo';",
            "  function baz() {",
            "    'string nested in baz';",
            "  }",
            "}",
            "function bar() {",
            "  'string in bar';",
            "}");

    Node tree = parse(compiler, code);
    Node fooFunction = tree.getFirstChild();

    // Traverse without entering nested scopes.
    NodeTraversal.traverseScopeRoots(
        compiler, null, ImmutableList.of(fooFunction), callback, false);
    assertThat(callback.strings).containsExactly("string in foo");

    callback.strings.clear();

    // Traverse *with* entering nested scopes, now also sees "string nested in baz".
    NodeTraversal.traverseScopeRoots(
        compiler, null, ImmutableList.of(fooFunction), callback, true);
    assertThat(callback.strings).containsExactly("string in foo", "string nested in baz");
  }

  @Test
  public void testTraverseEs6ScopeRoots_parentScopesWork() {
    Compiler compiler = new Compiler();
    LexicallyScopedVarsAccumulator callback = new LexicallyScopedVarsAccumulator();

    String code =
        lines(
            "var varDefinedInScript;",
            "var foo = function() {",
            "  var varDefinedInFoo;",
            "  var baz = function() {",
            "    var varDefinedInBaz;",
            "  }",
            "}",
            "var bar = function() {",
            "  var varDefinedInBar;",
            "}");

    Node tree = parse(compiler, code);
    Node fooFunction = tree.getSecondChild().getFirstFirstChild();

    // Traverse without entering nested scopes.
    NodeTraversal.traverseScopeRoots(
        compiler, null, ImmutableList.of(fooFunction), callback, false);
    assertThat(callback.varNames)
        .containsExactly("varDefinedInScript", "foo", "bar", "varDefinedInFoo", "baz");

    callback.varNames.clear();

    // Traverse *with* entering nested scopes, now also sees "varDefinedInBaz".
    NodeTraversal.traverseScopeRoots(
        compiler, null, ImmutableList.of(fooFunction), callback, true);
    assertThat(callback.varNames)
        .containsExactly(
            "varDefinedInScript", "foo", "bar", "varDefinedInFoo", "baz", "varDefinedInBaz");
  }

  @Test
  public void testTraverseEs6ScopeRoots_callsEnterScope() {
    Compiler compiler = new Compiler();

    List<Node> scopesEntered = new ArrayList<>();

    class TestCallback implements NodeTraversal.ScopedCallback {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}

      @Override
      public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
        return true;
      }

      @Override
      public void enterScope(NodeTraversal t) {
        scopesEntered.add(t.getScopeRoot());
      }

      @Override
      public void exitScope(NodeTraversal t) {}
    }

    String code = "function foo() { {} }";

    Node tree = parse(compiler, code);
    Node fooFunction = tree.getFirstChild();

    NodeTraversal.traverseScopeRoots(
        compiler, null, ImmutableList.of(fooFunction), new TestCallback(), true);
    assertThat(scopesEntered).hasSize(3);  // Function, function's body, and the block inside it.
  }

  @Test
  public void testNodeTraversalInterruptable() {
    Compiler compiler = new Compiler();
    String code = "var a; \n";
    Node tree = parse(compiler, code);

    final AtomicInteger counter = new AtomicInteger(0);
    AbstractPostOrderCallbackInterface countingCallback =
        (NodeTraversal t, Node n, Node parent) -> counter.incrementAndGet();

    NodeTraversal.builder().setCompiler(compiler).setCallback(countingCallback).traverse(tree);
    assertThat(counter.get()).isEqualTo(3);

    counter.set(0);
    Thread.currentThread().interrupt();

    try {
      NodeTraversal.builder().setCompiler(compiler).setCallback(countingCallback).traverse(tree);
      assertWithMessage("Expected a RuntimeException;").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(InterruptedException.class);
    }
  }

  // Helper class used to collect all the vars from current scope and its parent scopes
  private static final class LexicallyScopedVarsAccumulator extends AbstractPostOrderCallback {

    final Set<String> varNames = new LinkedHashSet<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Scope firstScope = t.getScope();
      if (firstScope == null) {
        return;
      }

      for (Scope scope = firstScope; scope != null; scope = scope.getParent()) {
        for (Var var : scope.getVarIterable()) {
          varNames.add(var.getName());
        }
      }
    }
  }

  private static final class StringAccumulator extends AbstractPostOrderCallback {

    final List<String> strings = new ArrayList<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isStringLit()) {
        strings.add(n.getString());
      }
    }
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
      assertThat(entered).isTrue();
    }

    @Override
    public void enterScope(NodeTraversal t) {
      assertNode(t.getCurrentNode()).isEqualTo(node);
      assertNode(t.getScopeRoot()).isEqualTo(scopeRoot);
      if (node.isForIn() || node.isForOf()) {
        node = node.getLastChild();
        scopeRoot = scopeRoot.getLastChild();
      }
      entered = true;
    }

    @Override
    public void exitScope(NodeTraversal t) {}

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !entered;
    }
  }

  // Helper class used to test accessible variables
  private static class AccessibleCallback extends NodeTraversal.AbstractPreOrderCallback
      implements NodeTraversal.ScopedCallback {
    private int numAccessible;

    private void expect(int accessible) {
      this.numAccessible = accessible;
    }

    private void assertAccessible(Scope s) {
      assertThat(s.getAllAccessibleVariables()).hasSize(numAccessible);
    }

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
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    IR.root(n);
    return n;
  }

  private static Node parseRoots(Compiler compiler, String externs, String js) {
    Node extern = parse(compiler, externs).detach();
    Node main = parse(compiler, js).detach();

    return IR.root(IR.root(extern), IR.root(main));
  }
}
