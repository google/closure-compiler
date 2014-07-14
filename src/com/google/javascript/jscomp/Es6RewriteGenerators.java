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
package com.google.javascript.jscomp;

import com.google.common.base.Joiner;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts ES6 generator functions to valid ES3 code.
 *
 * @author mattloring@google.com (Matthew Loring)
 */
public class Es6RewriteGenerators extends NodeTraversal.AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  private static final String ITER_KEY = "$$iterator";

  private static final String GENERATOR_CONTEXT = "$jscomp$generator$state";

  public Es6RewriteGenerators(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        if (n.isGeneratorFunction()) {
          visitGenerator(n, parent);
        }
    }
  }

  private void visitGenerator(Node n, Node parent) {
    Node genBlock = compiler.parseSyntheticCode(Joiner.on('\n').join(
      "{",
      "  return {" + ITER_KEY + ": function() {",
      "    var " + GENERATOR_CONTEXT + " = 0;",
      "    return { next: function() {",
      "      while (1) switch (" + GENERATOR_CONTEXT + ") {",
      "        case 0:",
      "          " + GENERATOR_CONTEXT + " = -1;",
      "        default:",
      "          return {done: true};",
      "      }",
      "    }}",
      "  }}",
      "}"
    )).removeFirstChild();

    Node genFunc = IR.function(n.removeFirstChild(), n.removeFirstChild(), genBlock);

    parent.replaceChild(n, genFunc);
    parent.useSourceInfoIfMissingFromForTree(parent);
    compiler.reportCodeChange();
  }

}

