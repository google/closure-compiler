/*
 * Copyright 2016 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;

/**
 * Check that the goog.module does not use a default export.
 *
 * <p>Example code:
 *
 * <pre>{@code class C {...} exports = C; }</pre>
 *
 * <p>Must become:
 *
 * <pre>{@code class C {...} exports = {C}; }</pre>
 *
 * <p>Which requires changes on the importer side from {@code const C = goog.require} to {@code
 * const {C} = goog.require}.
 */
public final class CheckDefaultExportOfGoogModule
    implements NodeTraversal.Callback, HotSwapCompilerPass {
  public static final DiagnosticType DEFAULT_EXPORT_GOOG_MODULE =
      DiagnosticType.disabled(
          "JSC_DEFAULT_EXPORT_GOOG_MODULE",
          "Default exports of goog.modules "
              + "do not translate easily to ES module semantics. Please use named exports"
              + " instead.");

  private final AbstractCompiler compiler;

  public CheckDefaultExportOfGoogModule(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case SCRIPT:
        return n.getBooleanProp(Node.GOOG_MODULE);
      default:
        return true;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isGoogModuleDefaultExportsAssignment(n)) {
      t.report(n, DEFAULT_EXPORT_GOOG_MODULE);
    }
  }

  /**
   * Matches default export assignments `exports = ..;` except when r.h.s is object literal like
   * `exports = {Foo};` since it treated as named exports.
   */
  private static boolean isGoogModuleDefaultExportsAssignment(Node statement) {
    if (!statement.isExprResult()) {
      return false;
    }

    if (!statement.getFirstChild().isAssign()) {
      return false;
    }

    if (!statement.getFirstFirstChild().isName()) {
      return false;
    }

    if (NodeUtil.isNamedExportsLiteral(statement.getFirstChild().getSecondChild())) {
      return false;
    }

    return statement.getFirstFirstChild().getString().equals("exports");
  }
}
