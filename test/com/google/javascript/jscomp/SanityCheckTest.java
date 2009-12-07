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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


/**
*
 */
public class SanityCheckTest extends CompilerTestCase {

  private CompilerPass otherPass = null;

  public SanityCheckTest() {
    super("", false);
  }

  @Override public void setUp() {
    otherPass = null;
  }

  @Override protected int getNumRepetitions() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override public CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        otherPass.process(externs, root);
        (new SanityCheck(compiler)).process(externs, root);
      }
    };
  }

  public void testUnnormalizeNodeTypes() throws Exception {
    otherPass = new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        getLastCompiler().reportCodeChange();
        root.addChildToBack(new Node(Token.EXPR_VOID, Node.newNumber(0)));
      }
    };

    boolean exceptionCaught = false;
    try {
      test("var x = 3;", "var x=3;0;0");
    } catch (IllegalStateException e) {
      assertEquals("normalizeNodeType constraints violated by last pass",
          e.getMessage());
      exceptionCaught = true;
    }
    assert(exceptionCaught);
  }

  public void testUnnormalized() throws Exception {
    otherPass = new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        getLastCompiler().setNormalized();
      }
    };

    boolean exceptionCaught = false;
    try {
      test("while(1){}", "while(1){}");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains(
          "Normalize constraints violated:\nWHILE node"));
      exceptionCaught = true;
    }
    assert(exceptionCaught);
  }

  public void testSymbolTable() throws Exception {
    otherPass = new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        SymbolTable st = getLastCompiler().acquireSymbolTable();
        st.createScope(root, null);
        Node script = root.getFirstChild();
        script.removeChild(script.getFirstChild());
        st.release();
      }
    };

    test("var x;", null, SymbolTable.VARIABLE_COUNT_MISMATCH);
  }
}
