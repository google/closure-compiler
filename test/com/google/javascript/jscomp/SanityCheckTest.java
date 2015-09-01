/*
 * Copyright 2009 The Closure Compiler Authors.
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
import static org.junit.Assert.fail;

import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * @author nicksantos@google.com (Nick Santos)
 */
public final class SanityCheckTest extends CompilerTestCase {

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
        root.getFirstChild().addChildToBack(
              new Node(Token.IF, new Node(Token.TRUE), new Node(Token.EMPTY)));
      }
    };

    try {
      test("var x = 3;", "var x=3;0;0");
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Expected BLOCK but was EMPTY");
    }
  }

  public void testUnnormalized() throws Exception {
    otherPass = new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        getLastCompiler().setLifeCycleStage(LifeCycleStage.NORMALIZED);
      }
    };

    try {
      test("while(1){}", "while(1){}");
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("Normalize constraints violated:\nWHILE node");
    }
  }

  public void testConstantAnnotationMismatch() throws Exception {
    otherPass = new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        getLastCompiler().reportCodeChange();
        Node name = Node.newString(Token.NAME, "x");
        name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        root.getFirstChild().addChildToBack(new Node(Token.EXPR_RESULT, name));
        getLastCompiler().setLifeCycleStage(LifeCycleStage.NORMALIZED);
      }
    };

    try {
      test("var x;", "var x; x;");
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("The name x is not consistently annotated as constant.");
    }
  }
}
