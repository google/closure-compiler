/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;

/** Miscellaneous checks for style in ES6 modules. */
public final class CheckEs6Modules implements Callback, CompilerPass {

  public static final DiagnosticType DUPLICATE_IMPORT =
      DiagnosticType.warning(
          "JSC_DUPLICATE_IMPORT", "The module \"{0}\" has already been imported at {1}, {2}.");

  public static final DiagnosticType NO_DEFAULT_EXPORT =
      DiagnosticType.warning(
          "JSC_DEFAULT_EXPORT",
          "Do not use the default export. There is no way to force consistent naming when "
              + "imported.");

  private final AbstractCompiler compiler;
  private final Map<String, Node> importSpecifiers = new HashMap<>();

  public CheckEs6Modules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isModuleBody()) {
      importSpecifiers.clear();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case ROOT:
      case MODULE_BODY:
        return true;
      case SCRIPT:
        return n.getBooleanProp(Node.ES6_MODULE);
      case IMPORT:
        visitImport(t, n);
        return false;
      case EXPORT:
        visitExport(t, n);
        return false;
      default:
        return false;
    }
  }

  private void visitImport(NodeTraversal t, Node importNode) {
    String specifier = importNode.getLastChild().getString();
    Node duplicateImport = importSpecifiers.putIfAbsent(specifier, importNode);
    if (duplicateImport != null) {
      t.report(
          importNode,
          DUPLICATE_IMPORT,
          specifier,
          "" + duplicateImport.getLineno(),
          "" + duplicateImport.getCharno());
    }
  }

  private void visitExport(NodeTraversal t, Node export) {
    if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
      t.report(export, NO_DEFAULT_EXPORT);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
}
