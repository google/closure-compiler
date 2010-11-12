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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ParallelCompilerPass.Result;
import com.google.javascript.jscomp.ParallelCompilerPass.Task;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ParallelCompilerPass}.
 *
 * The correctness of ParallelCompilerPass depends largely on the Task so this
 * class is here for a quick sanity test purpose. At the very least, it verifies
 * that all the worker terminates and no dead lock exists in the test cases.
 *
 */
public class ParallelCompilerPassTest extends TestCase {

  public void testNoFunction() {
    replace("\"foo\"");
    replace("var foo");
  }

  public void testOneFunction() {
    replace("\"foo\";function foo(){\"foo\"}");
  }

  public void testTwoFunctions() {
    replace("\"foo\";function f1(){\"foo\"}function f2(){\"foo\"}");
  }

  public void testInnerFunctions() {
    replace("\"foo\";function f1(){\"foo\";function f2(){\"foo\"}}");
  }

  public void testManyFunctions() {
    StringBuilder sb = new StringBuilder("\"foo\";");
    for (int i = 0; i < 20; i++) {
      sb.append("function f");
      sb.append(i);
      sb.append("(){\"foo\"}");
    }
    replace(sb.toString());
  }

  private void replace(String input) {
    String replace = input.replaceAll("foo", "bar");

    final Compiler compiler = new Compiler();

    int[] threadCounts = new int[]{1 , 2 , 4};

    for (int threadCount : threadCounts) {
      Node tree = compiler.parseTestCode(input);

      AstParallelizer splitter = AstParallelizer
          .createNewFunctionLevelAstParallelizer(tree, true);

      Supplier<Task> supplier = new Supplier<Task>() {
        @Override
        public Task get() {
          return new ReplaceStrings(compiler);
        }
      };

      ParallelCompilerPass pass = new ParallelCompilerPass(
          compiler, splitter, supplier, threadCount);
      pass.process(null, tree);
      assertEquals(replace, compiler.toSource(tree));
    }
  }

  /**
   * Replace all occurrences of "foo" with "bar".
   */
  private static class ReplaceStrings implements Task {
    private final Result result = new Result();
    private final AbstractCompiler compiler;

    private ReplaceStrings(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public Result processSubtree(Node subtree) {
      NodeTraversal.traverse(compiler, subtree,
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if ((NodeUtil.isString(n) || NodeUtil.isName(n))
                  && n.getString().equals("foo")) {
                n.setString("bar");
              }
            }
      });
      return result;
    }
  }
}
