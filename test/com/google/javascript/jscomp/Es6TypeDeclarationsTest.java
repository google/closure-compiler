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

public class Es6TypeDeclarationsTest extends TestCase {

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

  public void testFunction() throws Exception {
    assertIdentifierHasType(compile("/** @return {boolean} */ function b(){}"),
        "b", booleanType());
  }

  public void testFunctionParameters() throws Exception {
    assertIdentifierHasType(
        compile("/** @param {number} n @param {string} s */ function t(n,s){}"),
        "n", numberType());
  }

  public Node compile(String js) {
    SourceFile input = SourceFile.fromCode("js", js);
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(VariableRenamingPolicy.OFF, PropertyRenamingPolicy.OFF);
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.of(input), options);
    compiler.parseInputs();

    CompilerPass pass = new Es6TypeDeclarations(compiler);
    pass.process(
        compiler.getRoot().getFirstChild(),
        compiler.getRoot().getLastChild());

    return compiler.getRoot().getLastChild();
  }

  private void assertIdentifierHasType(Node root, String identifier,
      TypeDeclarationNode expectedType) {
    FindNode visitor = new FindNode(identifier);
    NodeTraversal.traverse(compiler, root, visitor);
    assertNotNull("Did not find a node named " + identifier + " in " + root.toStringTree(),
        visitor.foundNode);
    TypeDeclarationNode actualType = visitor.foundNode.getDeclaredTypeExpression();
    assertNotNull(identifier + " missing DECLARED_TYPE_EXPR in " + root.toStringTree(),
        actualType);
    assertTrue(visitor.foundNode + " is of type " + actualType
        + " not of type " + expectedType, expectedType.isEquivalentTo(actualType));
  }

  private static class FindNode extends NodeTraversal.AbstractPostOrderCallback {
    final String name;
    Node foundNode;

    FindNode(String name) {
      this.name = name;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Return type is attached to FUNCTION node, but the qualifiedName is on the child NAME node.
      if (parent != null && parent.isFunction() && n.matchesQualifiedName(name)) {
        foundNode = parent;
      } else if (n.matchesQualifiedName(name)) {
        foundNode = n;
      }
    }
  }
}
