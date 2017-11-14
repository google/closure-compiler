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
          "JSC_IMPORT_CANNOT_BE_REASSIGNED",
          "Assignment to constant variable \"{0}\".");

  private final AbstractCompiler compiler;

  public Es6CheckModule(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isThis() && t.inModuleHoistScope()) {
      t.report(n, ES6_MODULE_REFERENCES_THIS);
    } else if (NodeUtil.isLValue(n) && !NodeUtil.isDeclaration(parent)) {
      if (n.isGetProp()) {
        if (n.getFirstChild().isName()) {
          Var var = t.getScope().getVar(n.getFirstChild().getString());
          if (var != null) {
            Node nameNode = var.getNameNode();
            if (nameNode.isImportStar()) {
              // import * as M from '';
              // M.x = 2;
              compiler.report(t.makeError(n, IMPORT_CANNOT_BE_REASSIGNED, nameNode.getString()));
            }
          }
        }
      } else if (n.isName() && !parent.isArrayPattern()){
        Var var = t.getScope().getVar(n.getString());
        if (var != null) {
          Node nameNode = var.getNameNode();
          if (nameNode != n) {
            if (NodeUtil.isImportedName(nameNode)) {
              // import { x } from '';
              // x = 2;
              compiler.report(t.makeError(n, IMPORT_CANNOT_BE_REASSIGNED, nameNode.getString()));
            } else if (NodeUtil.isExportedName(nameNode) && !t.getScope().isGlobal()) {
              // TODO(johnplaisted) Implement mutable exports (issue #2710).
              // Note that mutating exports top level currently works, not just in nested scopes.
              // This is because if this is in a function scope then we do not know if this will
              // be called later, which we don't handle right now.
              compiler.report(JSError.make(n, Es6ToEs3Util.CANNOT_CONVERT_YET, "Mutable export."));
            }
          }
        }
      }
    }
  }
}
