/*
 * Copyright 2019 The Closure Compiler Authors.
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

/** Checks for invalid code references to type-only imports (i.e., goog.requireType). */
public final class CheckTypeImportCodeReferences extends AbstractPostOrderCallback
    implements CompilerPass {
  public static final DiagnosticType TYPE_IMPORT_CODE_REFERENCE =
      DiagnosticType.error(
          "JSC_TYPE_IMPORT_CODE_REFERENCE",
          "Cannot reference goog.requireType()''d name {0} outside of a type annotation.");

  private static final Node GOOG_REQUIRE_TYPE = IR.getprop(IR.name("goog"), "requireType");

  private final AbstractCompiler compiler;

  public CheckTypeImportCodeReferences(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isName()) {
      return;
    }

    Var var = t.getScope().getVar(n.getString());
    if (var == null) {
      return;
    }

    Node declNameNode = var.getNameNode();

    if (declNameNode == null || declNameNode == n || !NodeUtil.isDeclarationLValue(declNameNode)) {
      return;
    }

    Node decl =
        declNameNode.getParent().isStringKey()
            ? declNameNode.getGrandparent().getGrandparent()
            : declNameNode.getParent();
    if (!NodeUtil.isNameDeclaration(decl)
        || !decl.hasOneChild()
        || !decl.getFirstChild().hasChildren()
        || !decl.getFirstChild().getLastChild().isCall()) {
      return;
    }

    Node callNode = decl.getFirstChild().getLastChild();
    if (!callNode.getFirstChild().matchesQualifiedName(GOOG_REQUIRE_TYPE)) {
      return;
    }

    t.report(n, TYPE_IMPORT_CODE_REFERENCE, n.getString());
  }
}
