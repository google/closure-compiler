/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * <p>Compiler pass that converts all calls to:
 *   goog.object.create(key1, val1, key2, val2, ...) where all of the keys
 *   are literals into object literals.</p>
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
final class ClosureOptimizePrimitives implements CompilerPass {

  /** Reference to the JS compiler */
  private final AbstractCompiler compiler;

  /**
   * Identifies all calls to goog.object.create.
   */
  private class FindObjectCreateCalls extends AbstractPostOrderCallback {
    List<Node> callNodes = Lists.newArrayList();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        String fnName = n.getFirstChild().getQualifiedName();
        if ("goog$object$create".equals(fnName) ||
            "goog.object.create".equals(fnName)) {
          callNodes.add(n);
        }
      }
    }
  }

  /**
   * @param compiler The AbstractCompiler
   */
  ClosureOptimizePrimitives(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FindObjectCreateCalls pass = new FindObjectCreateCalls();
    NodeTraversal.traverse(compiler, root, pass);
    processObjectCreateCalls(pass.callNodes);
  }

  /**
   * Converts all of the given call nodes to object literals that are safe to
   * do so.
   */
  private void processObjectCreateCalls(List<Node> callNodes) {
    for (Node callNode : callNodes) {
      Node curParam = callNode.getFirstChild().getNext();
      if (canOptimizeObjectCreate(curParam)) {
        Node objNode = IR.objectlit().srcref(callNode);
        while (curParam != null) {
          Node keyNode = curParam;
          Node valueNode = curParam.getNext();
          curParam = valueNode.getNext();

          callNode.removeChild(keyNode);
          callNode.removeChild(valueNode);

          if (!keyNode.isString()) {
            keyNode = IR.string(NodeUtil.getStringValue(keyNode))
                .srcref(keyNode);
          }
          keyNode.setType(Token.STRING_KEY);
          keyNode.setQuotedString();
          objNode.addChildToBack(IR.propdef(keyNode, valueNode));
        }
        callNode.getParent().replaceChild(callNode, objNode);
        compiler.reportCodeChange();
      }
    }
  }

  /**
   * Returns whether the given call to goog.object.create can be converted to an
   * object literal.
   */
  private boolean canOptimizeObjectCreate(Node firstParam) {
    Node curParam = firstParam;
    while (curParam != null) {
      // All keys must be strings or numbers.
      if (!curParam.isString() && !curParam.isNumber()) {
        return false;
      }
      curParam = curParam.getNext();

      // Check for an odd number of parameters.
      if (curParam == null) {
        return false;
      }
      curParam = curParam.getNext();
    }
    return true;
  }
}
