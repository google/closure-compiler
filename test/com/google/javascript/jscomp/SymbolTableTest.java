/*
 * Copyright 2009 Google Inc.
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

import static com.google.javascript.jscomp.SymbolTable.MISSING_VARIABLE;
import static com.google.javascript.jscomp.SymbolTable.MOVED_VARIABLE;
import static com.google.javascript.jscomp.SymbolTable.VARIABLE_COUNT_MISMATCH;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


/**
 * Tests for {@link SymbolTable}.
*
 */
public class SymbolTableTest extends CompilerTestCase {

  DiagnosticType targetError = null;

  @Override protected CompilerPass getProcessor(Compiler compiler) {
    return new BuggyVariableChanger(compiler);
  }

  @Override public void setUp() {
    setExpectedSymbolTableError(null);
    targetError = null;
  }

  public void testOk() throws Exception {
    // By default, BuggyVariableChanger correctly updates its scope object
    // when it removes variables.
    test("var x = 3;", "");
  }

  public void testCountMismatch() throws Exception {
    setExpectedSymbolTableError(VARIABLE_COUNT_MISMATCH);
    test("var x = 3, y = 5;", "");
  }

  public void testMovedVariable() throws Exception {
    setExpectedSymbolTableError(MOVED_VARIABLE);
    test("var x = 3, y = 5;", "var x, y = 5;");
  }

  public void testMissingVariable() throws Exception {
    setExpectedSymbolTableError(MISSING_VARIABLE);
    test("var x = 3, y = 5;", "var z = 3, y = 5;");
  }

  @Override
  protected void setExpectedSymbolTableError(DiagnosticType type) {
    super.setExpectedSymbolTableError(type);
    targetError = type;
  }

  /**
   * A variable remover that doesn't know that VAR nodes
   * have multiple children, and forgets to update the scope object.
   */
  private class BuggyVariableChanger
      extends NodeTraversal.AbstractPostOrderCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;

    BuggyVariableChanger(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    public void process(Node externs, Node root) {
      SymbolTable table = compiler.acquireSymbolTable();
      (new NodeTraversal(compiler, this, table)).traverseRoots(externs, root);
      table.release();
    }

    public void visit(NodeTraversal t, Node node, Node parent) {
      Scope scope = t.getScope();
      if (node.getType() == Token.VAR) {
        compiler.reportCodeChange();

        if (targetError == null) {
          // the "correct" implementation
          parent.removeChild(node);
          for (Node child = node.getFirstChild();
               child != null; child = child.getNext()) {
            scope.undeclare(scope.getVar(child.getString()));
          }
        } else if (targetError == VARIABLE_COUNT_MISMATCH) {
          // A bad implementation where we forget to undeclare all vars.
          parent.removeChild(node);
          scope.undeclare(scope.getVar(node.getFirstChild().getString()));
        } else if (targetError == MISSING_VARIABLE) {
          // A bad implementation where we don't update the var name.
          node.getFirstChild().setString("z");
        } else if (targetError == MOVED_VARIABLE) {
          // A bad implementation where we take the var out of the tree.
          Node oldName = node.getFirstChild();
          oldName.detachFromParent();
          node.addChildToFront(Node.newString(Token.NAME, "x"));
        }
      }
    }
  }
}
