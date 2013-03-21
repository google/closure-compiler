/*
 * Copyright 2013 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

/**
 * This code implements the instrumentation pass over the AST
 * to instrument all object allocations. This methodology should help 
 * identify allocation hotspots and potential for object reuse, with
 * the goal to reduce memory footprint and GC pressure.
 *
 * We are tracking object allocations via new(), array literals, object
 * literals, and function expressions. This does not cover factory methods
 * like Object.create or document.createElement but could be expanded to
 * track those.
 */
class InstrumentMemoryAllocPass implements CompilerPass {

  final AbstractCompiler compiler;

  private static int newSiteId = 1; // 0 is reserved for 'total'

  static final String JS_INSTRUMENT_ALLOCATION_CODE =
      "var __allocStats; \n" +
      "var __alloc = function(obj, sourcePosition, id, typeName) { \n" +
      "  if (!__allocStats) { \n" +
      "    __allocStats = { \n" +
      "      reset: function() { \n" +
      "        this.counts = [{ type:typeName, line:'total', count:0 }]; \n" +
      "      }, \n" +
      "      report: function(opt_n) { \n" +
      "        this.counts.filter(function(x) { \n" +
      "          return x; \n" +
      "        }).sort(function(a, b) { \n" +
      "          return b.count - a.count; \n" +
      "        }).splice(0, opt_n || 50).reverse().forEach(function (x) { \n" +
      "          if (window.console) { \n" +
      "            window.console.log(x.count + ' (' + x.type + ') : ' + x.line); \n" +
      "          } \n" +
      "        }); \n" +
      "      } \n" +
      "    }; \n" +
      "    __allocStats.reset(); \n" +
      "    if (window.parent) { \n" +
      "      window.parent['__allocStats'] = __allocStats; \n" +
      "    } \n" +
      "  } \n" +
      "  if (!__allocStats.counts[id]) { \n" +
      "    __allocStats.counts[id] = { type:typeName, line:sourcePosition, count:0 }; \n" +
      "  } \n" +
      "  __allocStats.counts[0].count++; \n" +
      "  __allocStats.counts[id].count++; \n" +
      "  return obj;\n" +
      "}; \n"
      ;

  /**
   * @param compiler the compiler which generates the AST.
   */
  public InstrumentMemoryAllocPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /**
   * Creates the library code for this instrumentation - a compiled
   * version of above __alloc implementation.
   */
  private Node getInstrumentAllocationCode() {
    return compiler.parseSyntheticCode(JS_INSTRUMENT_ALLOCATION_CODE);
  }

  @Override
  public void process(Node externsNode, Node rootNode) {
    if (rootNode.hasChildren()) {
      NodeTraversal.traverse(compiler, rootNode, new Traversal());
      NodeTraversal.traverse(
          compiler, rootNode, new PrepareAst.PrepareAnnotations());

      Node firstScript = rootNode.getFirstChild();
      Preconditions.checkState(firstScript.isScript());

      compiler.getNodeForCodeInsertion(null).addChildrenToFront(
          getInstrumentAllocationCode().removeChildren());
      compiler.reportCodeChange();
    }
  }

  private Node getTypeString(Node currentNode){
    if (currentNode.getType() == Token.NEW) {
      JSType type = currentNode.getFirstChild().getJSType();
      String typeName = (type != null) ? type.getDisplayName() : "Unknown";
      return IR.string("new " + typeName);
    }

    return
        currentNode.getType() == Token.ARRAYLIT ? IR.string("Array") :
        currentNode.getType() == Token.OBJECTLIT ? IR.string("Object") :
        currentNode.getType() == Token.FUNCTION ? IR.string("Function") :
        IR.string("Unknown");
  }

  /**
   * Find instances of:
   *  - calls to new
   *  - object literals
   *  - array literals
   *  - a function (expression)
   *
   * All of these are known to result in memory allocations.
   * "Intercept" these with a call to our own __alloc for book keeping.
   */
  private class Traversal extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isNew() ||
          n.getType() == Token.ARRAYLIT ||
          n.getType() == Token.OBJECTLIT ||
          NodeUtil.isFunctionExpression(n)) {

        Node instrumentAllocation = IR.call(
            IR.name("__alloc"),
            n.cloneTree(),
            IR.string(n.getSourceFileName() + ":" + n.getLineno()),
            IR.number(newSiteId++),
            getTypeString(n));

        parent.replaceChild(n, instrumentAllocation);
      }
    }
  }
}
