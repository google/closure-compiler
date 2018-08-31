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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
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
   * The set of class member function nodes with @export annotations and the fully qualified name of
   * the owning class (not including the member fn name).
   */
  private final LinkedHashMap<Node, String> es6ClassExports = new LinkedHashMap<>();

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

      switch (n.getToken()) {
        case FUNCTION:
        case CLASS:
          if (parent.isScript()) {
            export = NodeUtil.getName(n);
            context = n;
            mode = Mode.EXPORT;
          }
          break;

        case ASSIGN:
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

        case VAR:
        case LET:
        case CONST:
          if (parent.isScript()) {
            if (n.getFirstChild().hasChildren() && !n.getFirstFirstChild().isAssign()) {
              export = n.getFirstChild().getString();
              context = n;
              mode = Mode.EXPORT;
            }
          }
          break;

        case GETPROP: {
          // TODO(b/63582201): currently, the IF body is executed even for code in the global
          // scope, e.g., when the pass looks at code transpiled from ES6 that uses getters.
          // This means that the top-level code we want to rewrite works by accident, only when
          // allowLocalExports happens to be true.
          if (allowLocalExports && parent.isExprResult()) {
            export = n.getLastChild().getString();
            mode = Mode.EXTERN;
          }
          break;
        }

        case MEMBER_FUNCTION_DEF:
          if (parent.getParent().isClass()) {
            String methodOwnerName =
                NodeUtil.getBestLValueName(NodeUtil.getBestLValue(parent.getParent()));
            if (methodOwnerName == null) {
              t.report(n, EXPORT_ANNOTATION_NOT_ALLOWED);
              return;
            }
            es6ClassExports.put(n, methodOwnerName + (n.isStaticMember() ? "" : ".prototype"));
            return;
          }
          // fallthrough

        case STRING_KEY:
        case GETTER_DEF:
        case SETTER_DEF:
          if (allowLocalExports) {
            export = n.getString();
            mode = Mode.EXTERN;
          }
          break;
        default:
          break;
      }

      if (export != null) {
        if (mode == Mode.EXPORT) {
          checkNotNull(context);
          exports.put(export, context);
        } else {
          checkState(context == null);
          checkState(mode == Mode.EXTERN);
          checkState(!export.isEmpty());
          localExports.add(export);
        }
      }
      // Silently ignore exports of the form:
      // /** @export */ Foo.prototype.myprop;
      // They are currently used on interfaces and records and have no effect.
      // If we cleanup the code base in the future, we can warn again.
      else if (!(n.isGetProp() && parent.isExprResult())) {
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

  LinkedHashMap<Node, String> getEs6ClassExports() {
    return es6ClassExports;
  }

  static enum Mode {
    EXPORT,
    EXTERN
  }
}
