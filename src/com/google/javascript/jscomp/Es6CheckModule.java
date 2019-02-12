/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;

/**
 * Checks that ES6 Modules are used correctly, and do not reference undefined keywords or features.
 */
public final class Es6CheckModule extends AbstractPostOrderCallback implements HotSwapCompilerPass {
  static final DiagnosticType ES6_MODULE_REFERENCES_THIS =
      DiagnosticType.warning(
          "ES6_MODULE_REFERENCES_THIS", "The body of an ES6 module cannot reference 'this'.");

  static final DiagnosticType IMPORT_CANNOT_BE_REASSIGNED =
      DiagnosticType.error(
          "JSC_IMPORT_CANNOT_BE_REASSIGNED", "Assignment to constant variable \"{0}\".");

  private final AbstractCompiler compiler;

  public Es6CheckModule(AbstractCompiler compiler) {
    this.compiler = compiler;
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
    switch (n.getToken()) {
      case THIS:
        if (t.inModuleHoistScope()) {
          t.report(n, ES6_MODULE_REFERENCES_THIS);
        }
        break;
      case GETPROP:
      case GETELEM:
        if (NodeUtil.isLValue(n)
            && !NodeUtil.isDeclarationLValue(n)
            && n.getFirstChild().isName()) {
          Var var = t.getScope().getVar(n.getFirstChild().getString());
          if (var != null) {
            Node nameNode = var.getNameNode();
            if (nameNode != null && nameNode.isImportStar()) {
              // import * as M from '';
              // M.x = 2;
              compiler.report(t.makeError(n, IMPORT_CANNOT_BE_REASSIGNED, nameNode.getString()));
            }
          }
        }
        break;
      case NAME:
        if (NodeUtil.isLValue(n) && !NodeUtil.isDeclarationLValue(n)) {
          Var var = t.getScope().getVar(n.getString());
          if (var != null) {
            Node nameNode = var.getNameNode();
            if (nameNode != null && nameNode != n) {
              if (NodeUtil.isImportedName(nameNode)) {
                // import { x } from '';
                // x = 2;
                compiler.report(t.makeError(n, IMPORT_CANNOT_BE_REASSIGNED, nameNode.getString()));
              }
            }
          }
        }
        break;
      default:
        break;
    }
  }
}
