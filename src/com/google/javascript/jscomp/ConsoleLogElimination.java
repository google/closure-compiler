/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static java.lang.String.format;
import static java.util.logging.Logger.getLogger;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

import java.util.logging.Logger;


public class ConsoleLogElimination extends AbstractPostOrderCallback implements CompilerPass {

  private static final Logger logger = getLogger(ConsoleLogElimination.class.getName());

  private AbstractCompiler compiler;


  public ConsoleLogElimination(AbstractCompiler compiler) {
    this.compiler = compiler;
  }


  @Override
  public void process(Node node, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }


  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null || !parent.isGetProp())
      return;
    Node secondAncestor = parent.getParent();
    Node thirdAncestor = secondAncestor.getParent();
    if (n.isName() && "console".equals(n.getQualifiedName()) && secondAncestor.isCall() && thirdAncestor.isExprResult()) {
      removeExpressionResult(secondAncestor);
      return;
    } else if (n.isString() && "console".equals(n.getString()) && "window".equals(parent.getFirstChild().getString())) {
      removeExpressionResult(thirdAncestor);
      return;
    }
  }


  private void removeExpressionResult(Node fnCall) {
    for (int i = 1; i < fnCall.getChildCount(); i++) {
      if (NodeUtil.mayHaveSideEffects(fnCall.getChildAtIndex(i))) {
        logger.warning(format("Removing console statement with possible side effects on line %d of '%s'", fnCall.getLineno(), fnCall.getSourceFileName()));
        break;
      }
    }
    fnCall.getParent().getParent().removeChild(fnCall.getParent());
    compiler.reportCodeChange();
  }

}
