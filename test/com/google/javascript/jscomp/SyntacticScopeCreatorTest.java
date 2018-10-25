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
import static com.google.javascript.jscomp.ScopeSubject.assertScope;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SyntacticScopeCreator}.
 *
 */
@RunWith(JUnit4.class)
public final class SyntacticScopeCreatorTest {

  private Compiler compiler;
  private SyntacticScopeCreator scopeCreator;

  @Before
  public void setUp() {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    scopeCreator = SyntacticScopeCreator.makeUntyped(compiler);
  }

  /**
   * Helper to create a top-level scope from a JavaScript string
   */
  private Scope getScope(String js) {
    return (Scope) scopeCreator.createScope(getRoot(js), null);
  }

  private Node getRoot(String js) {
    Node root = compiler.parseTestCode(js);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
    return root;
  }

  @Test
  public void testFunctionScope() {
    compiler.getOptions().setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    Scope scope = getScope("function foo() {}\n" +
        "var x = function bar(a1) {};" +
        "[function bar2() { var y; }];" +
        "if (true) { function z() {} }");

    assertScope(scope).declares("foo").directly();
    assertScope(scope).declares("x").directly();
    assertScope(scope).declares("z").directly();

    // The following should not be declared in this scope
    assertScope(scope).doesNotDeclare("a1");
    assertScope(scope).doesNotDeclare("bar");
    assertScope(scope).doesNotDeclare("bar2");
    assertScope(scope).doesNotDeclare("y");
    assertScope(scope).doesNotDeclare("");
  }

  @Test
  public void testNestedFunctionScope() {
    Node root = getRoot("function f(x) { function g(y) {} }");
    Scope globalScope = (Scope) scopeCreator.createScope(root, null);

    Node fNode = root.getFirstChild();
    Scope outerFScope = (Scope) scopeCreator.createScope(fNode, globalScope);
    assertScope(outerFScope).declares("x").directly();

    Node innerFNode = fNode.getLastChild().getFirstChild();
    Scope innerFScope = (Scope) scopeCreator.createScope(innerFNode, outerFScope);
    assertScope(innerFScope).declares("x").onSomeParent();
    assertScope(innerFScope).declares("y").directly();
  }

  @Test
  public void testScopeRootNode() {
    Node root = getRoot("function foo() { var x = 10; }");

    Scope globalScope = (Scope) scopeCreator.createScope(root, null);
    assertThat(globalScope.getRootNode()).isEqualTo(root);

    Node fooNode = root.getFirstChild();
    assertThat(fooNode.getToken()).isEqualTo(Token.FUNCTION);
    Scope fooScope = (Scope) scopeCreator.createScope(fooNode, globalScope);
    assertThat(fooScope.getRootNode()).isEqualTo(fooNode);
    assertScope(fooScope).declares("x").directly();
  }

  @Test
  public void testFunctionExpressionInForLoopInitializer() {
    Node root = getRoot("for (function foo() {};;) {}");
    Scope globalScope = (Scope) scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("foo");

    Node fNode = root.getFirstFirstChild();
    Scope fScope = (Scope) scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("foo").directly();
  }
}
