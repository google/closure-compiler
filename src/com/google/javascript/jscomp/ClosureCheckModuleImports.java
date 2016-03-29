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
package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks that imports of goog.module provided files are used correctly.
 *
 * Since this is a whole-program check, it can't be part of {@link ClosureCheckModule},
 * but it should eventually become part of {@link ClosureRewriteModule}.
 */
public final class ClosureCheckModuleImports extends AbstractPostOrderCallback
    implements CompilerPass {
  static final DiagnosticType QUALIFIED_REFERENCE_TO_GOOG_MODULE =
      DiagnosticType.error(
          "JSC_QUALIFIED_REFERENCE_TO_GOOG_MODULE",
          "Fully qualified reference to name ''{0}'' provided by a goog.module.\n"
              + "Either use short import syntax or"
              + " convert module to use goog.module.declareLegacyNamespace.");

  private final AbstractCompiler compiler;

  private Set<String> globalModules = new HashSet<>();

  public ClosureCheckModuleImports(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CALL:
        if (n.getFirstChild().matchesQualifiedName("goog.module")) {
          recordModuleCall(n, parent);
        }
        break;
      case Token.GETPROP:
        if (n.isQualifiedName()) {
          checkQualifiedName(t, n);
        }
        break;
    }
  }

  private boolean isCallTo(Node n, String qname) {
    return n.isCall() && n.getFirstChild().matchesQualifiedName(qname);
  }

  private void recordModuleCall(Node callNode, Node parent) {
    Preconditions.checkState(callNode.isCall());
    Node nextStatement = parent.getNext();
    if (parent.isExprResult()
        && nextStatement != null
        && nextStatement.isExprResult()
        && isCallTo(nextStatement.getFirstChild(), "goog.module.declareLegacyNamespace")) {
      return;
    }
    Node moduleNameNode = callNode.getSecondChild();
    if (moduleNameNode.isString()) {
      globalModules.add(moduleNameNode.getString());
    }
  }

  private void checkQualifiedName(NodeTraversal t, Node qnameNode) {
    String qname = qnameNode.getQualifiedName();
    if (globalModules.contains(qname)) {
      t.report(qnameNode, QUALIFIED_REFERENCE_TO_GOOG_MODULE, qname);
    }
  }
}
