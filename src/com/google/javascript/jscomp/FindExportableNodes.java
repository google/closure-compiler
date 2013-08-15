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
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;
import java.util.Set;

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
 * are not supported because the annotation is ambiguous to whether it applies
 * to all the variables or only the first one.
 *
 */
class FindExportableNodes extends AbstractPostOrderCallback {

  static final DiagnosticType NON_GLOBAL_ERROR =
      DiagnosticType.error("JSC_NON_GLOBAL_ERROR",
          "@export only applies to symbols/properties defined in the " +
          "global scope.");

  static final DiagnosticType EXPORT_ANNOTATION_NOT_ALLOWED =
      DiagnosticType.error("JSC_EXPORT_ANNOTATION_NOT_ALLOWED",
          "@export is not supported on this expression.");

  private final AbstractCompiler compiler;

  /**
   * It's convenient to be able to iterate over exports in the order in which
   * they are encountered.
   */
  private final LinkedHashMap<String, GenerateNodeContext> exports =
      Maps.newLinkedHashMap();

  private Set<String> externProps = Sets.newLinkedHashSet();

  private final boolean allowLocalExports;

  FindExportableNodes(
      AbstractCompiler compiler, boolean allowLocalExports) {
    this.compiler = compiler;
    this.allowLocalExports = allowLocalExports;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo = n.getJSDocInfo();
    if (docInfo != null && docInfo.isExport()) {
      String export = null;
      GenerateNodeContext context = null;

      switch (n.getType()) {
        case Token.FUNCTION:
          if (parent.isScript()) {
            export = NodeUtil.getFunctionName(n);
            context = new GenerateNodeContext(n, Mode.EXPORT);
          }
          break;

        case Token.ASSIGN:
          Node grandparent = parent.getParent();
          if (parent.isExprResult() &&
              !n.getLastChild().isAssign()) {
            if (grandparent != null && grandparent.isScript() &&
                n.getFirstChild().isQualifiedName()) {
              export = n.getFirstChild().getQualifiedName();
              context = new GenerateNodeContext(n, Mode.EXPORT);
            } else if (allowLocalExports && n.getFirstChild().isGetProp()) {
              Node target = n.getFirstChild();
              export = target.getLastChild().getString();
              context = new GenerateNodeContext(n, Mode.EXTERN);
            }
          }
          break;

        case Token.VAR:
          if (parent.isScript()) {
            if (n.getFirstChild().hasChildren() &&
                !n.getFirstChild().getFirstChild().isAssign()) {
              export = n.getFirstChild().getString();
              context = new GenerateNodeContext(n, Mode.EXPORT);
            }
          }
          break;

        case Token.GETPROP:
          if (allowLocalExports && parent.isExprResult()) {
            export = n.getLastChild().getString();
            context = new GenerateNodeContext(n, Mode.EXTERN);
          }
          break;

        case Token.STRING_KEY:
          if (allowLocalExports) {
            export = n.getString();
            context = new GenerateNodeContext(n, Mode.EXTERN);
          }
          break;
      }

      if (export != null) {
        exports.put(export, context);
      } else {
        if (allowLocalExports) {
          compiler.report(t.makeError(n, EXPORT_ANNOTATION_NOT_ALLOWED));
        } else {
          compiler.report(t.makeError(n, NON_GLOBAL_ERROR));
        }
      }
    }
  }

  LinkedHashMap<String, GenerateNodeContext> getExports() {
    return exports;
  }

  static enum Mode {
    EXPORT,
    EXTERN
  }

  /**
   * Context holding the node references required for generating the export
   * calls.
   */
  static class GenerateNodeContext {
    private final Node node;
    private final Mode mode;

    GenerateNodeContext(
        Node node, Mode mode) {
      this.node = node;
      this.mode = mode;
    }

    Node getNode() {
      return node;
    }

    public Mode getMode() {
      return mode;
    }
  }
}
