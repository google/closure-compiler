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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.ScopeSubject.assertScope;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A tests for {@link IncrementalScopeCreator}. */
@RunWith(JUnit4.class)
public final class IncrementalScopeCreatorTest {

  @Test
  public void testMemoization() {

    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js", "var ext"));
    List<SourceFile> srcs = ImmutableList.of(
        SourceFile.fromCode("testcode1.js", "var a; var b; function foo() { var inside = 1; }"),
        SourceFile.fromCode("testcode2.js", "var x;"));
    Compiler compiler = initCompiler(externs, srcs);
    IncrementalScopeCreator creator = IncrementalScopeCreator.getInstance(compiler).freeze();
    Node root1 = compiler.getRoot();

    Scope scopeA = creator.createScope(root1, null);
    assertThat(creator.createScope(root1, null)).isSameAs(scopeA);

    IncrementalScopeCreator.getInstance(compiler).thaw();

    IncrementalScopeCreator.getInstance(compiler).freeze();

    assertThat(creator.createScope(root1, null)).isSameAs(scopeA);

    try {
      Node root2 = IR.root();
      creator.createScope(root2, null);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains("the shared persistent scope must always " + "be root at the tip of the AST");
    }
  }

  @Test
  public void testParialGlobalScopeRefresh() {
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js", "var ext"));
    List<SourceFile> srcs = ImmutableList.of(
        SourceFile.fromCode("testcode1.js", "var a; var b; function foo() { var inside = 1; }"),
        SourceFile.fromCode("testcode2.js", "var x;"));
    Compiler compiler = initCompiler(externs, srcs);
    IncrementalScopeCreator creator = IncrementalScopeCreator.getInstance(compiler).freeze();

    Node root = compiler.getRoot();
    Node fnFoo = findDecl(root, "foo");
    checkState(fnFoo.isFunction());

    Scope globalScope = creator.createScope(root, null);
    Scope globalFunction = creator.createScope(fnFoo, globalScope);
    // When refreshing a local scope, the Scope object is preserved but the Var objects are
    // recreated, so we need to inspect a Var in the scope to see if it has been freshed or not.
    Var inside = globalFunction.getVar("inside");

    assertScope(globalScope).declares("a");
    assertScope(globalScope).declares("b");
    assertScope(globalScope).declares("x");
    assertScope(globalScope).declares("ext");
    assertScope(globalScope).doesNotDeclare("nonexistant");

    // Make a change that affects the global scope (and report it)
    removeFirstDecl(compiler, compiler.getRoot(), "a");

    Scope globalScope2 = creator.createScope(compiler.getRoot(), null);
    assertThat(globalScope2).isSameAs(globalScope);
    // unchanged local scopes should be preserved
    assertThat(creator.createScope(fnFoo, globalScope)).isSameAs(globalFunction);
    assertThat(globalFunction.getVar("inside")).isSameAs(inside);

    assertScope(globalScope2).declares("a"); // still declared, scope creator is frozen
    assertScope(globalScope2).declares("b");
    assertScope(globalScope2).declares("x");
    assertScope(globalScope2).declares("ext");
    assertScope(globalScope2).doesNotDeclare("nonexistant");

    // Allow the scopes to be updated by calling "thaw" and "freeze"

    IncrementalScopeCreator.getInstance(compiler).thaw();

    IncrementalScopeCreator.getInstance(compiler).freeze();

    Scope globalScope3 = creator.createScope(compiler.getRoot(), null);
    assertThat(globalScope3).isSameAs(globalScope);
    // unchanged local scopes should be preserved
    assertThat(creator.createScope(fnFoo, globalScope)).isSameAs(globalFunction);
    assertThat(globalFunction.getVar("inside")).isSameAs(inside);

    assertScope(globalScope3).doesNotDeclare("a"); // no declared, scope creator has refreshed
    assertScope(globalScope3).declares("b");
    assertScope(globalScope3).declares("x");
    assertScope(globalScope3).declares("ext");
    assertScope(globalScope3).doesNotDeclare("nonexistant");

    IncrementalScopeCreator.getInstance(compiler).thaw();
  }

  @Test
  public void testPartialGlobalScopeRefreshWithMove() {
    // This test verifies that when a variable declarations moves between script, the
    // original script correctly "forgets" that the moved variables was associated with
    // it.  If this were not the case, invalidating the original script would
    // undeclare a variable and readding the variables when rescanning the script would not
    // readd it.

    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js", ""));
    List<SourceFile> srcs = ImmutableList.of(
        SourceFile.fromCode("testcode1.js", "var a; var b;"),
        SourceFile.fromCode("testcode2.js", "var x; var y;"));
    Compiler compiler = initCompiler(externs, srcs);
    IncrementalScopeCreator creator = IncrementalScopeCreator.getInstance(compiler).freeze();

    Node root = compiler.getRoot();

    Scope globalScope = creator.createScope(root, null);

    assertScope(globalScope).declares("a").directly();
    assertScope(globalScope).declares("b").directly();
    assertScope(globalScope).declares("x").directly();
    assertScope(globalScope).declares("y").directly();
    assertScope(globalScope).doesNotDeclare("nonexistant");

    Node script1 = checkNotNull(NodeUtil.getEnclosingScript(findDecl(root, "a")));
    Node script2 = checkNotNull(NodeUtil.getEnclosingScript(findDecl(root, "x")));


    Node varB = checkNotNull(findDecl(root, "b"));



    // Move B to from script1 to script2
    varB.detach();
    script2.addChildToBack(varB);

    compiler.reportChangeToChangeScope(script1);
    compiler.reportChangeToChangeScope(script2);

    // Allow the scopes to update by "thaw" and "freeze" again.
    creator.thaw();
    creator.freeze();

    globalScope = creator.createScope(root, null);

    assertScope(globalScope).declares("a").directly();
    assertScope(globalScope).declares("b").directly();
    assertScope(globalScope).declares("x").directly();
    assertScope(globalScope).declares("y").directly();
    assertScope(globalScope).doesNotDeclare("nonexistant");

    compiler.reportChangeToChangeScope(script1); // invalidate the original scope.

    // Allow the scopes to update by "thaw" and "freeze" again.
    creator.thaw();
    creator.freeze();

    globalScope = creator.createScope(root, null);

    assertScope(globalScope).declares("a").directly();
    assertScope(globalScope).declares("b").directly();
    assertScope(globalScope).declares("x").directly();
    assertScope(globalScope).declares("y").directly();
    assertScope(globalScope).doesNotDeclare("nonexistant");

    creator.thaw();
  }

  @Test
  public void testRefreshedGlobalScopeWithRedeclaration() {

    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js", ""));
    List<SourceFile> srcs = ImmutableList.of(
        SourceFile.fromCode("testcode1.js", "var a; var b;"),
        SourceFile.fromCode("testcode2.js", "var a;"));
    Compiler compiler = initCompiler(externs, srcs);

    IncrementalScopeCreator creator = IncrementalScopeCreator.getInstance(compiler).freeze();

    Node root = compiler.getRoot();

    Scope globalScope = creator.createScope(root, null);

    assertScope(globalScope).declares("a");
    assertScope(globalScope).declares("b");

    removeFirstDecl(compiler, compiler.getRoot(), "a"); // leaves the second declaration
    removeFirstDecl(compiler, compiler.getRoot(), "b");

    // Allow the scopes to be updated by calling "thaw" and "freeze"

    IncrementalScopeCreator.getInstance(compiler).thaw();

    IncrementalScopeCreator.getInstance(compiler).freeze();

    Scope globalScope2 = creator.createScope(compiler.getRoot(), null);
    assertThat(globalScope2).isSameAs(globalScope);

    assertScope(globalScope2).declares("a"); // still declared in second file
    assertScope(globalScope2).doesNotDeclare("b");

    IncrementalScopeCreator.getInstance(compiler).thaw();
  }

  @Test
  public void testValidScopeReparenting() {
    List<SourceFile> externs = ImmutableList.of(
        SourceFile.fromCode("externs.js", "var ext"));
    List<SourceFile> srcs = ImmutableList.of(
        SourceFile.fromCode("testcode1.js", "var a; var b; "
            + " { function foo() { var inside = 1; } }"),
        SourceFile.fromCode("testcode2.js", "var x;"));
    Compiler compiler = initCompiler(externs, srcs);
    IncrementalScopeCreator creator = IncrementalScopeCreator.getInstance(compiler).freeze();

    Node root = compiler.getRoot();
    Node fnFoo = findDecl(root, "foo");
    checkState(fnFoo.isFunction());

    Node block = fnFoo.getParent();
    checkState(block.isBlock());

    Scope globalScope1 = creator.createScope(root, null);
    Scope blockScope1 = creator.createScope(block, globalScope1);
    Scope fnScope1 = creator.createScope(fnFoo, blockScope1);
    assertThat(fnScope1.getDepth()).isSameAs(blockScope1.getDepth() + 1);
    assertThat(fnScope1.getParent()).isSameAs(blockScope1);

    // When refreshing a local scope, the Scope object is preserved but the Var objects are
    // recreated, so we need to inspect a Var in the scope to see if it has been freshed or not.
    Var inside1 = fnScope1.getVar("inside");

    compiler.reportChangeToEnclosingScope(block);
    block.replaceWith(fnFoo.detach());

    IncrementalScopeCreator.getInstance(compiler).thaw();

    IncrementalScopeCreator.getInstance(compiler).freeze();

    Scope globalScope2 = creator.createScope(root, null);
    Scope fnScope2 = creator.createScope(fnFoo, globalScope2);
    assertThat(fnScope2).isSameAs(fnScope1);
    assertThat(fnScope2.getParent()).isSameAs(globalScope2);
    assertThat(fnScope2.getDepth()).isSameAs(globalScope2.getDepth() + 1);
    assertThat(fnScope2.getVar("inside")).isSameAs(inside1);

    IncrementalScopeCreator.getInstance(compiler).thaw();
  }

  private void removeFirstDecl(Compiler compiler, Node n, String name) {
    Node decl = findDecl(n, name);
    compiler.reportChangeToEnclosingScope(decl);
    decl.detach();
  }

  private Node findDecl(Node n, String name) {
    Node result = find(n, new NodeUtil.MatchNameNode(name), Predicates.<Node>alwaysTrue());
    return result.getParent();
  }

  /**
   * @return Whether the predicate is true for the node or any of its descendants.
   */
  private static Node find(Node node,
                     Predicate<Node> pred,
                     Predicate<Node> traverseChildrenPred) {
    if (pred.apply(node)) {
      return node;
    }

    if (!traverseChildrenPred.apply(node)) {
      return null;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      Node result = find(c, pred, traverseChildrenPred);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  Compiler initCompiler(List<SourceFile> externs, List<SourceFile> srcs) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.init(externs, srcs, options);
    compiler.parseInputs();
    checkState(!compiler.hasErrors());
    return compiler;
  }
}
