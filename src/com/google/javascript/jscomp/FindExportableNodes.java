/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;

/**
 * Records all of the symbols and properties that should be exported.
 *
 * Currently applies to:
 * - function foo() {}
 * - var foo = function() {}
 * - foo.bar = function() {}
 * - var FOO = ...;
 * - foo.BAR = ...;
 *
 * FOO = BAR = 5;
 * and
 * var FOO = BAR = 5;
 * are not supported because the annotation is ambigous to whether it applies
 * to all the variables or only the first one.
 *
 */
public class FindExportableNodes extends AbstractPostOrderCallback {

  static final DiagnosticType NON_GLOBAL_ERROR =
      DiagnosticType.error("JSC_NON_GLOBAL_ERROR",
          "@export only applies to symbols/properties defined in the " +
          "global scope.");

  /**
   * It's convenient to be able to iterate over exports in the order in which
   * they are encountered.
   */
  private final LinkedHashMap<String, GenerateNodeContext> exports;

  private final AbstractCompiler compiler;

  public FindExportableNodes(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.exports = Maps.newLinkedHashMap();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo = n.getJSDocInfo();
    if (docInfo != null && docInfo.isExport()) {
      String export = null;
      GenerateNodeContext context = null;

      switch (n.getType()) {
        case Token.FUNCTION:
          if (parent.getType() == Token.SCRIPT) {
            export = NodeUtil.getFunctionName(n);
            context = new GenerateNodeContext(n, parent, n);
          }
          break;
        case Token.ASSIGN:
          Node grandparent = parent.getParent();
          if (grandparent != null && grandparent.getType() == Token.SCRIPT &&
              parent.getType() == Token.EXPR_RESULT &&
              n.getLastChild().getType() != Token.ASSIGN) {
            export = n.getFirstChild().getQualifiedName();
            context = new GenerateNodeContext(n, grandparent, parent);
          }
          break;
        case Token.VAR:
          if (parent.getType() == Token.SCRIPT) {
            if (n.getFirstChild().hasChildren() &&
                n.getFirstChild().getFirstChild().getType() != Token.ASSIGN) {
              export = n.getFirstChild().getString();
              context = new GenerateNodeContext(n, parent, n);
            }
          }
      }

      if (export != null) {
        exports.put(export, context);
      } else {
        compiler.report(t.makeError(n, NON_GLOBAL_ERROR));
      }
    }
  }

  public LinkedHashMap<String, GenerateNodeContext> getExports() {
    return exports;
  }

  /**
   * Context holding the node references required for generating the export
   * calls.
   */
  public static class GenerateNodeContext {
    private final Node scriptNode;
    private final Node contextNode;
    private final Node node;

    public GenerateNodeContext(Node node, Node scriptNode, Node contextNode) {
      this.node = node;
      this.scriptNode = scriptNode;
      this.contextNode = contextNode;
    }

    public Node getNode() {
      return node;
    }

    public Node getScriptNode() {
      return scriptNode;
    }

    public Node getContextNode() {
      return contextNode;
    }
  }
}
