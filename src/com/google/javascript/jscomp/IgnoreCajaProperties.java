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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Caja is a system that rewrites web content (JavaScript, CSS, HTML)
 * into web content that is safe to inline directly into a page.
 * The rewritten ("cajoled") code runs in the presence of a JS library
 * that adds some properties to Object.prototype.  Because JS does not
 * yet (until ES5) allow programmers to mark properties as DontEnum,
 * for..in loops will see unexpected properties.
 *
 * This pass adds a conditional to for..in loops that filters out these
 * properties.
 *
 */

class IgnoreCajaProperties implements CompilerPass {

  final AbstractCompiler compiler;

  // Counts the number of temporary variables introduced.
  int counter;

  public IgnoreCajaProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.counter = 0;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Traversal());
  }

  private class Traversal extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Look for a for..in loop.
      if (n.isFor() && n.getChildCount() == 3) {
        Node body = n.getLastChild();
        n.removeChild(body);
        Node key = n.getFirstChild();
        n.removeChild(key);
        Node tmp = IR.name(
            "JSCompiler_IgnoreCajaProperties_" + counter++);
        n.addChildToFront(IR.var(tmp));
        Node assignment;
        Node ifBody;

        // Construct the body of the if statement.
        if (key.isVar()) {
          // for (var key in x) { body; }
          // =>
          // for (var tmp in x) {
          //   if (!tmp.match(/___$/)) {
          //     var key;
          //     key = tmp;
          //     body;
          //   }
          // }
          ifBody = IR.block(
              key,
              IR.exprResult(
                  IR.assign(
                      key.getFirstChild().cloneNode(),
                      tmp.cloneTree())),
              body);
        } else {
          // for (key in x) { body; }
          // =>
          // for (var tmp in x) {
          //   if (!tmp.match(/___$/)) {
          //     key = tmp;
          //     body;
          //   }
          // }
          ifBody = IR.block(
              IR.exprResult(
                  IR.assign(
                      key,
                      tmp.cloneTree())),
              body);
        }

        // Construct the new body of the for loop.
        Node newBody = IR.block(
            IR.ifNode(
                IR.not(
                    IR.call(
                        IR.getprop(
                            tmp.cloneTree(),
                            IR.string("match")),
                        IR.regexp(
                            IR.string("___$")))),
                ifBody));
        n.addChildToBack(newBody);
        compiler.reportCodeChange();
      }
    }
  }
}
