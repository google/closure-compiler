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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;


/**
 * Splits variable declarations that declare multiple variables into
 * separate declarations, if at least one of the declarations is a
 * destructuring declaration. For example
 * <pre>
 *   var [a, b] = foo(), c = bar();}
 * <pre>
 * becomes
 * <pre>
 *   var [a, b] = foo();
 *   var c = bar();
 * </pre>
 *
 * <p>This runs before the main ES6 transpilation step, to simplify
 * the transpilation of destructuring syntax.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6SplitVariableDeclarations extends
    NodeTraversal.AbstractPostOrderCallback implements HotSwapCompilerPass {
  private final AbstractCompiler compiler;
  public Es6SplitVariableDeclarations(AbstractCompiler compiler) {
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
    if (NodeUtil.isDestructuringDeclaration(n)) {
      splitDeclaration(n, parent);
    }
  }

  public void splitDeclaration(Node n, Node parent) {
    while (n.getFirstChild() != n.getLastChild()) {
      Node child = n.getLastChild().detachFromParent();
      Node newVar = IR.declaration(child, n.getType()).srcref(n);
      parent.addChildAfter(newVar, n);
      compiler.reportCodeChange();
    }
  }
}

