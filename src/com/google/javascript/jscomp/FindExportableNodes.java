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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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

  static final DiagnosticType EXPORT_ARRAY_LITERAL_NOT_EXPRESSION =
      DiagnosticType.error("JSC_EXPORT_NOT_STRING_LITERAL",
          "An @export array literal's value must be a standalone expression.");

  static final DiagnosticType EXPORT_NOT_STRING_LITERAL =
      DiagnosticType.error("JSC_EXPORT_NOT_STRING_LITERAL",
          "All members of an @export array literal must be string literals.");

  private final AbstractCompiler compiler;

  /**
   * It's convenient to be able to iterate over exports in the order in which
   * they are encountered.
   */
  private final LinkedHashMap<String, GenerateNodeContext> exports =
       new LinkedHashMap<>();

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

      if (parent.isAssign() && (n.isFunction() || n.isClass())) {
        JSDocInfo parentInfo = parent.getJSDocInfo();
        if (parentInfo != null && parentInfo.isExport()) {
          // ScopedAliases produces export annotations on both the function/class
          // node and assign node, we only want to visit the assign node.
          return;
        }
      }

      String export = null;
      List<String> exportsList = null;
      GenerateNodeContext context = null;

      switch (n.getType()) {
        case Token.FUNCTION:
        case Token.CLASS:
          if (parent.isScript()) {
            export = NodeUtil.getName(n);
            context = new GenerateNodeContext(n, Mode.EXPORT);
          }
          break;

        case Token.MEMBER_FUNCTION_DEF:
          export = n.getString();
          context = new GenerateNodeContext(n, Mode.EXPORT);
          break;

        case Token.ASSIGN:
          Node grandparent = parent.getParent();
          if (parent.isExprResult() && !n.getLastChild().isAssign()) {
            if (grandparent != null
                && grandparent.isScript()
                && n.getFirstChild().isQualifiedName()) {
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
        case Token.LET:
        case Token.CONST:
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
        case Token.GETTER_DEF:
        case Token.SETTER_DEF:
          if (allowLocalExports) {
            export = n.getString();
            context = new GenerateNodeContext(n, Mode.EXTERN);
          }
          break;

        case Token.ARRAYLIT:
          if (allowLocalExports) {
            // Collect exports in the form "/** @export */ ['a', 'b'];"
            if (!n.getParent().isExprResult()
                || !(n.getParent().getParent().isScript()
                     || n.getParent().getParent().isBlock())) {
              compiler.report(t.makeError(n,
                  EXPORT_ARRAY_LITERAL_NOT_EXPRESSION));
            }
            exportsList = new ArrayList<>();
            for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
              if (c.isString()) {
                exportsList.add(c.getString());
              } else {
                compiler.report(t.makeError(c, EXPORT_NOT_STRING_LITERAL));
              }
            }
            context = new GenerateNodeContext(n, Mode.EXTERN);
          }
          break;

        default:
          break; // Error handing below.
      }

      if (export != null) {
        exports.put(export, context);
      } else if (exportsList != null) {
        for (String e : exportsList) {
          exports.put(e, context);
        }
      } else {
        // Don't produce extra warnings for functions values of object literals
        if (!n.isFunction() || !NodeUtil.isObjectLitKey(parent)) {
          if (allowLocalExports) {
            compiler.report(t.makeError(n, EXPORT_ANNOTATION_NOT_ALLOWED));
          } else {
            compiler.report(t.makeError(n, NON_GLOBAL_ERROR));
          }
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
