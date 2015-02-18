/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.booleanType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.numberType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.stringType;
import static com.google.javascript.rhino.Node.TypeDeclarationNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Verifies that the compiler pass can find JSDoc type annotations on various
 * Node types, and that it sets a declaredTypeExpression property on the Node.
 *
 * <p>Exhaustive testing of the transformation between the two type declaration
 * styles is in
 * {@link com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactoryTest}
 */
public class ConvertToTypedES6Test extends TestCase {

  private Compiler compiler;

  public void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testVar() throws Exception {
    assertIdentifierHasType(
        compile("/** @type {string} */ var s;"),
        "s", stringType());
  }

  public void testPropertyDeclaration() throws Exception {
    assertIdentifierHasType(compile("/** @type {number} */ this.prop;"),
        "this.prop", numberType());
  }

  public void testPropertyDeclarationByAssignment() throws Exception {
    assertIdentifierHasType(compile("/** @type {number} */ this.prop = 1;"),
        "this.prop", numberType());
  }

  public void testFunction() throws Exception {
    assertIdentifierHasType(compile("/** @return {boolean} */ function b(){}"),
        "b", booleanType());
  }

  public void testFunctionParameters() throws Exception {
    assertIdentifierHasType(
        compile("/** @param {number} n @param {string} s */ function t(n,s){}"),
        "n", numberType());
  }

  public void testNoEmptyTypeAnnotationsAttached() throws Exception {
    Node b = findNode(compile("/** some jsdoc */ function b() {}"), "b");
    assertNull(b.getDeclaredTypeExpression());
  }

  public Node compile(String js) {
    SourceFile input = SourceFile.fromCode("js", js);
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(
        VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.of(input), options);
    compiler.parseInputs();

    CompilerPass pass = new ConvertToTypedES6(compiler);
    pass.process(
        compiler.getRoot().getFirstChild(),
        compiler.getRoot().getLastChild());

    return compiler.getRoot().getLastChild();
  }

  private void assertIdentifierHasType(Node root, String identifier,
      TypeDeclarationNode expectedType) {
    TypeDeclarationNode actualType =
        findNode(root, identifier).getDeclaredTypeExpression();
    assertNotNull(
        identifier + " missing DECLARED_TYPE_EXPR in " + root.toStringTree(),
        actualType);
    assertTrue(findNode(root, identifier) + " is of type " + actualType
        + " not of type " + expectedType,
        expectedType.isEquivalentTo(actualType));
  }

  private Node findNode(Node root, String identifier) {
    FindNode visitor = new FindNode(identifier);
    NodeTraversal.traverse(compiler, root, visitor);
    Node foundNode = visitor.foundNode;
    assertNotNull(
        "Did not find node named " + identifier + " in " + root.toStringTree(),
        foundNode);
    return foundNode;
  }

  private static class FindNode extends NodeTraversal.AbstractPostOrderCallback
  {
    final String name;
    Node foundNode;

    FindNode(String name) {
      this.name = name;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Return type is attached to FUNCTION node, but the qualifiedName is on
      // the child NAME node.
      if (parent != null && parent.isFunction()
          && n.matchesQualifiedName(name)) {
        foundNode = parent;
      } else if (n.matchesQualifiedName(name)) {
        foundNode = n;
      }
    }
  }
}
