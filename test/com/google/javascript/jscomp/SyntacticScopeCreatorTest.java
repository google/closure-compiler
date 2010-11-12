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

/**
 * Tests for {@link SyntacticScopeCreator}.
 *
 */
public class SyntacticScopeCreatorTest extends TestCase {

  /**
   * Helper to create a top level scope from a javascript string
   */
  private static Scope getScope(String js) {
    Compiler compiler = new Compiler();
    Node root = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    Scope scope =
        new SyntacticScopeCreator(compiler).createScope(root, null);
    return scope;
  }

  /**
   * Helper to traverse the tree creating the Scope object everywhere.
   */
  private static void testScopes(String js, int errorCount) {
    Compiler compiler = new Compiler();
    Node root = compiler.parseTestCode(js);
    NodeTraversal.traverse(
        compiler, root, new NodeTraversal.AbstractPostOrderCallback() {
          @Override
          public
          void visit(NodeTraversal t, Node n, Node parent) {
            t.getScope();
          }
        });
    assertEquals(errorCount, compiler.getErrorCount());
  }

  public void testFunctionScope() {
    Scope scope = getScope("function foo() {}\n" +
                           "var x = function bar(a1) {};" +
                           "[function bar2() { var y; }];" +
                           "if (true) { function z() {} }"
                           );
    assertTrue(scope.isDeclared("foo", false));
    assertTrue(scope.isDeclared("x", false));
    assertTrue(scope.isDeclared("z", false));

    // The following should not be declared in this scope
    assertFalse(scope.isDeclared("a1", false));
    assertFalse(scope.isDeclared("bar", false));
    assertFalse(scope.isDeclared("bar2", false));
    assertFalse(scope.isDeclared("y", false));
    assertFalse(scope.isDeclared("", false));
  }

  public void testScopeRootNode() {
    String js = "function foo() {\n" +
        " var x = 10;" +
        "}";
    Compiler compiler = new Compiler();
    Node root = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());

    Scope globalScope =
        new SyntacticScopeCreator(compiler).createScope(root, null);
    assertEquals(root, globalScope.getRootNode());

    Node fooNode = root.getFirstChild();
    assertEquals(Token.FUNCTION, fooNode.getType());
    Scope fooScope =
        new SyntacticScopeCreator(compiler).createScope(fooNode, null);
    assertEquals(fooNode, fooScope.getRootNode());
    assertTrue(fooScope.isDeclared("x", false));
  }

  public void testRedeclaration1() {
     String js = "var a; var a;";
     int errors = createGlobalScopeHelper(js);
     assertEquals(1, errors);
  }

  public void testRedeclaration2() {
    String js = "var a; /** @suppress {duplicate} */ var a;";
    int errors = createGlobalScopeHelper(js);
    assertEquals(0, errors);
  }

  public void testFunctionScopeArguments() {
    // A var declaration doesn't mask arguments
    testScopes("function f() {var arguments}", 0);

    testScopes("var f = function arguments() {}", 1);
    testScopes("var f = function (arguments) {}", 1);
    testScopes("function f() {try {} catch(arguments) {}}", 1);
  }

  /**
   * Parse the supplied js and create the global SyntaticScope object.
   * @return The error count.
   */
  private int createGlobalScopeHelper(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.checkSymbols = true;
    compiler.initOptions(options);

    Node root = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    Scope globalScope =
      new SyntacticScopeCreator(compiler).createScope(root, null);
    assertEquals(root, globalScope.getRootNode());
    return compiler.getErrorCount();
  }
}
