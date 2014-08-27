/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for JsFlume {@link Matchers}.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
@RunWith(JUnit4.class)
public class MatchersTest {

  @Test
  public void testAnything() {
    String input = "goog.base(this);";
    Node root = compileToScriptRoot(getCompiler(input));
    Node child = root.getFirstChild();
    assertTrue(Matchers.anything().matches(null, null));
    assertTrue(Matchers.anything().matches(root, null));
    assertTrue(Matchers.anything().matches(child, null));
    assertTrue(Matchers.anything().matches(child, new NodeMetadata(null)));
  }

  @Test
  public void testAllOf() {
    String input = "goog.require('goog.dom');";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());

    Matcher notMatcher = Matchers.not(Matchers.anything());
    assertFalse(Matchers.allOf(notMatcher).matches(root, null));
    assertFalse(Matchers.allOf(notMatcher, Matchers.functionCall()).matches(fnCall, null));
    assertTrue(Matchers.allOf(
        Matchers.anything(), Matchers.functionCall("goog.require")).matches(fnCall, null));
  }

  @Test
  public void testAnyOf() {
    String input = "goog.require('goog.dom');";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());

    Matcher notMatcher = Matchers.not(Matchers.anything());
    assertFalse(Matchers.anyOf(notMatcher).matches(root, null));
    assertTrue(Matchers.anyOf(notMatcher, Matchers.functionCall()).matches(fnCall, null));
    assertTrue(Matchers.anyOf(Matchers.functionCall("goog.require")).matches(fnCall, null));
  }

  @Test
  public void testNot() {
    assertFalse(Matchers.not(Matchers.anything()).matches(null, null));

    String input = "goog.require('goog.dom');";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());
    assertFalse(Matchers.not(Matchers.functionCall()).matches(fnCall, null));
    assertFalse(Matchers.not(Matchers.functionCall("goog.require")).matches(fnCall, null));
  }

  @Test
  public void testConstructor_any() {
    String input = "/** @constructor */ var Foo = function() {};";
    Node root = compileToScriptRoot(getCompiler(input));
    assertTrue(Matchers.constructor().matches(root.getFirstChild(), null));
  }

  @Test
  public void testConstructor_specificClass() {
    String input = "/** @constructor */ var Foo = function() {};";
    Node root = compileToScriptRoot(getCompiler(input));
    assertTrue(Matchers.constructor("Foo").matches(root.getFirstChild(), null));
    assertFalse(Matchers.constructor("Bar").matches(root.getFirstChild(), null));
  }

  @Test
  public void testConstructor_differentConstructorTypes() {
    String input = "/** @constructor */ var Foo = function() {};";
    Node root = compileToScriptRoot(getCompiler(input));
    Node ctorNode = root.getFirstChild();
    assertTrue(Matchers.constructor("Foo").matches(ctorNode, null));

    input = "/** @constructor */ bar.Foo = function() {};";
    root = compileToScriptRoot(getCompiler(input));
    ctorNode = root.getFirstChild().getFirstChild();
    assertTrue(Matchers.constructor("bar.Foo").matches(ctorNode, null));

    input = "/** @constructor */ function Foo() {};";
    root = compileToScriptRoot(getCompiler(input));
    ctorNode = root.getFirstChild();
    assertTrue(Matchers.constructor("Foo").matches(ctorNode, null));

    // TODO(mknichel): Make this test case work.
    // input = "ns = {\n"
    //     + "  /** @constructor */\n"
    //     + "  Foo: function() {}\n"
    //     + "};";
    // root = compileToScriptRoot(getCompiler(input));
    // ctorNode = root.getFirstChild().getFirstChild().getLastChild().getFirstChild();
    // assertTrue(Matchers.constructor("ns.Foo").matches(ctorNode, null));
  }

  @Test
  public void testNewClass() {
    String input = "new Object()";
    Node root = compileToScriptRoot(getCompiler(input));
    Node newNode = root.getFirstChild().getFirstChild();
    assertTrue(newNode.isNew());
    assertTrue(Matchers.newClass().matches(newNode, null));
    assertFalse(Matchers.newClass().matches(newNode.getFirstChild(), null));
  }

  @Test
  public void testNewClass_specificClass() {
    String externs = ""
        + "/** @constructor */\n"
        + "function Foo() {};\n"
        + "/** @constructor */\n"
        + "function Bar() {};";
    String input = "var foo = new Foo();";
    Compiler compiler = getCompiler(externs, input);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Node root = compileToScriptRoot(compiler);
    Node varNode = root.getFirstChild();
    Node newNode = varNode.getFirstChild().getFirstChild();
    assertTrue(newNode.isNew());
    assertTrue(Matchers.newClass("Foo").matches(newNode, metadata));
    assertFalse(Matchers.newClass("Bar").matches(newNode, metadata));
    assertFalse(Matchers.newClass("Foo").matches(newNode.getFirstChild(), metadata));
  }

  @Test
  public void testFunctionCall_any() {
    String input = "goog.base(this)";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());
    assertTrue(Matchers.functionCall().matches(fnCall, null));
  }

  @Test
  public void testFunctionCall_numArgs() {
    String input = "goog.base(this)";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());
    assertTrue(Matchers.functionCallWithNumArgs(1).matches(fnCall, null));
    assertFalse(Matchers.functionCallWithNumArgs(2).matches(fnCall, null));
    assertTrue(Matchers.functionCallWithNumArgs("goog.base", 1).matches(fnCall, null));
    assertFalse(Matchers.functionCallWithNumArgs("goog.base", 2).matches(fnCall, null));
    assertFalse(Matchers.functionCallWithNumArgs("goog.require", 1).matches(fnCall, null));
    assertFalse(Matchers.functionCallWithNumArgs("goog.require", 2).matches(fnCall, null));
  }

  @Test
  public void testFunctionCall_static() {
    String input = "goog.require('goog.dom');";
    Node root = compileToScriptRoot(getCompiler(input));
    Node fnCall = root.getFirstChild().getFirstChild();
    assertTrue(fnCall.isCall());
    assertTrue(Matchers.functionCall("goog.require").matches(fnCall, null));
    assertFalse(Matchers.functionCall("goog.provide").matches(fnCall, null));
  }

  @Test
  public void testFunctionCall_prototype() {
    String externs = ""
        + "/** @constructor */\n"
        + "function Foo() {};\n"
        + "Foo.prototype.bar = function() {};\n"
        + "Foo.prototype.baz = function() {};\n";
    String input = "var foo = new Foo(); foo.bar();";
    Compiler compiler = getCompiler(externs, input);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Node root = compileToScriptRoot(compiler);
    Node fnCall = root.getLastChild().getFirstChild();
    assertTrue(fnCall.isCall());
    assertTrue(Matchers.functionCall("Foo.prototype.bar").matches(fnCall, metadata));
    assertFalse(Matchers.functionCall("Foo.prototype.baz").matches(fnCall, metadata));
  }

  @Test
  public void testEnum() {
    String input = "/** @enum {string} */ var foo = {BAR: 'baz'};";
    Node root = compileToScriptRoot(getCompiler(input));
    Node enumNode = root.getFirstChild().getFirstChild();
    assertTrue(Matchers.enumDefinition().matches(enumNode, null));
  }

  @Test
  public void testEnumOfType() {
    String input = "/** @enum {string} */ var foo = {BAR: 'baz'};";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node enumNode = root.getFirstChild().getFirstChild();
    assertTrue(Matchers.enumDefinitionOfType("string").matches(
        enumNode, new NodeMetadata(compiler)));
    assertFalse(Matchers.enumDefinitionOfType("number").matches(
        enumNode, new NodeMetadata(compiler)));
  }

  @Test
  public void testAssignmentWithRhs() {
    String externs = ""
        + "var goog = {};\n"
        + "goog.number = function() {};\n"
        + "var someObj = {};\n";
    String input = "someObj.foo = goog.number();";
    Compiler compiler = getCompiler(externs, input);
    Node root = compileToScriptRoot(compiler);
    Node assignNode = root.getFirstChild().getFirstChild();
    assertTrue(
        Matchers.assignmentWithRhs(Matchers.functionCall("goog.number")).matches(assignNode, null));
    assertFalse(
        Matchers.assignmentWithRhs(Matchers.functionCall("goog.base")).matches(assignNode, null));
    assertFalse(Matchers.assignmentWithRhs(Matchers.functionCall("goog.number"))
        .matches(assignNode.getFirstChild(), null));
  }

  @Test
  public void testPrototypeDeclarations() {
    String input = ""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.bar = 3;\n"
        + "Foo.prototype.baz = function() {};\n";
    Compiler compiler = getCompiler(input);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Node root = compileToScriptRoot(compiler);
    Node prototypeVarAssign = root.getFirstChild().getNext().getFirstChild();
    Node prototypeFnAssign = root.getLastChild().getFirstChild();

    assertTrue(Matchers.prototypeVariableDeclaration().matches(
        prototypeVarAssign.getFirstChild(), metadata));
    assertFalse(Matchers.prototypeMethodDeclaration().matches(
        prototypeVarAssign.getFirstChild(), metadata));

    assertTrue(Matchers.prototypeMethodDeclaration().matches(
        prototypeFnAssign.getFirstChild(), metadata));
    assertFalse(Matchers.prototypeVariableDeclaration().matches(
        prototypeFnAssign.getFirstChild(), metadata));
  }

  @Test
  public void testJsDocType() {
    String input = "/** @type {number} */ var foo = 1;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node node = root.getFirstChild().getFirstChild();
    assertTrue(Matchers.jsDocType("number").matches(node, new NodeMetadata(compiler)));
    assertFalse(Matchers.jsDocType("string").matches(node, new NodeMetadata(compiler)));
  }

  @Test
  public void testPropertyAccess() {
    String input = "foo.bar.method();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node methodNode = root.getFirstChild().getFirstChild().getFirstChild();
    Node barNode = methodNode.getFirstChild();
    assertTrue(Matchers.propertyAccess().matches(methodNode, new NodeMetadata(compiler)));
    assertTrue(Matchers.propertyAccess().matches(barNode, new NodeMetadata(compiler)));
    assertTrue(Matchers.propertyAccess("foo.bar.method").matches(
          methodNode, new NodeMetadata(compiler)));
    assertTrue(Matchers.propertyAccess("foo.bar").matches(
          barNode, new NodeMetadata(compiler)));
    assertFalse(Matchers.propertyAccess("foo").matches(
          barNode.getFirstChild(), new NodeMetadata(compiler)));
  }

  @Test
  public void testPropertyAccess_instance() {
    String externs = ""
        + "/** @constructor */\n"
        + "function Foo() {};\n"
        + "Foo.prototype.bar = 3;\n";
    String input = "var foo = new Foo(); foo.bar;";
    Compiler compiler = getCompiler(externs, input);
    Node root = compileToScriptRoot(compiler);
    Node node = root.getLastChild().getFirstChild();
    assertTrue(Matchers.propertyAccess().matches(node, new NodeMetadata(compiler)));
    assertTrue(Matchers.propertyAccess("Foo.prototype.bar").matches(
          node, new NodeMetadata(compiler)));
    assertTrue(Matchers.propertyAccess("foo.bar").matches(
          node, new NodeMetadata(compiler)));
  }

  /**
   * Returns the root script node produced from the compiled JS input.
   */
  private Node compileToScriptRoot(Compiler compiler) {
    Node root = compiler.getRoot();
    // The last child of the compiler root is a Block node, and the first child
    // of that is the Script node.
    return root.getLastChild().getFirstChild();
  }

  private Compiler getCompiler(String jsInput) {
    return getCompiler("", jsInput);
  }

  private Compiler getCompiler(String externs, String jsInput) {
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(SourceFile.fromCode("test", jsInput)),
        options);
    return compiler;
  }
}
