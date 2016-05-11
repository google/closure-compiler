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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Checks for invalid uses of the "super" keyword.
 */
final class Es6SuperCheck extends AbstractPostOrderCallback implements CompilerPass {
  static final DiagnosticType INVALID_SUPER_CALL = DiagnosticType.error(
      "JSC_INVALID_SUPER_CALL",
      "super() not allowed except in the constructor of a subclass");

  static final DiagnosticType INVALID_SUPER_CALL_WITH_SUGGESTION = DiagnosticType.error(
      "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION",
      "super() not allowed here. Did you mean super.{0}?");

  private final AbstractCompiler compiler;

  Es6SuperCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isSuper()) {
      return;
    }
    Node classNode = NodeUtil.getEnclosingClass(n);
    if (classNode == null || classNode.getSecondChild().isEmpty()) {
      t.report(n, INVALID_SUPER_CALL);
      return;
    }

    if (parent.isCall()) {
      Node fn = NodeUtil.getEnclosingFunction(parent);
      if (fn == null) {
        t.report(n, INVALID_SUPER_CALL);
        return;
      }

      Node memberDef = fn.getParent();
      if (memberDef.isMemberFunctionDef()) {
        if (memberDef.matchesQualifiedName("constructor")) {
          // No error.
        } else {
          t.report(n, INVALID_SUPER_CALL_WITH_SUGGESTION, memberDef.getString());
        }
      } else {
        t.report(n, INVALID_SUPER_CALL);
      }
    }
  }
}
