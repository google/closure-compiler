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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

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
          "@export only applies to symbols/properties defined in the "
          + "global scope.");

  static final DiagnosticType EXPORT_ANNOTATION_NOT_ALLOWED =
      DiagnosticType.error("JSC_EXPORT_ANNOTATION_NOT_ALLOWED",
          "@export is not supported on this expression.");

  private final AbstractCompiler compiler;

  /**
   * The set of node with @export annotations and their associated fully qualified names
   */
  private final LinkedHashMap<String, Node> exports = new LinkedHashMap<>();

  /**
   * The set of property names associated with @export annotations that do not have
   * an associated fully qualified name.
   */
  private final LinkedHashSet<String> localExports = new LinkedHashSet<>();

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

      Mode mode = null;
      String export = null;
      Node context = null;

      switch (n.getType()) {
        case Token.FUNCTION:
        case Token.CLASS:
          if (parent.isScript()) {
            export = NodeUtil.getName(n);
            context = n;
            mode = Mode.EXPORT;
          }
          break;

        case Token.MEMBER_FUNCTION_DEF:
          export = n.getString();
          context = n;
          mode = Mode.EXPORT;
          break;

        case Token.ASSIGN:
          Node grandparent = parent.getParent();
          if (parent.isExprResult() && !n.getLastChild().isAssign()) {
            if (grandparent != null
                && grandparent.isScript()
                && n.getFirstChild().isQualifiedName()) {
              export = n.getFirstChild().getQualifiedName();
              context = n;
              mode = Mode.EXPORT;
            } else if (allowLocalExports && n.getFirstChild().isGetProp()) {
              Node target = n.getFirstChild();
              export = target.getLastChild().getString();
              mode = Mode.EXTERN;
            }
          }
          break;

        case Token.VAR:
        case Token.LET:
        case Token.CONST:
          if (parent.isScript()) {
            if (n.getFirstChild().hasChildren() && !n.getFirstFirstChild().isAssign()) {
              export = n.getFirstChild().getString();
              context = n;
              mode = Mode.EXPORT;
            }
          }
          break;

        case Token.GETPROP:
          if (allowLocalExports && parent.isExprResult()) {
            mode = Mode.EXTERN;
            export = n.getLastChild().getString();
            mode = Mode.EXTERN;
          }
          break;

        case Token.STRING_KEY:
        case Token.GETTER_DEF:
        case Token.SETTER_DEF:
          if (allowLocalExports) {
            export = n.getString();
            mode = Mode.EXTERN;
          }
          break;
      }

      if (export != null) {
        if (mode == Mode.EXPORT) {
          Preconditions.checkNotNull(context);
          exports.put(export, context);
        } else {
          Preconditions.checkState(context == null);
          Preconditions.checkState(mode == Mode.EXTERN);
          Preconditions.checkState(!export.isEmpty());
          localExports.add(export);
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

  LinkedHashMap<String, Node> getExports() {
    return exports;
  }

  LinkedHashSet<String> getLocalExports() {
    return localExports;
  }

  static enum Mode {
    EXPORT,
    EXTERN
  }
}
