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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Set;

/** A normalization pass to re-write Util.$getDefine calls to make them work in compiled mode. */
public class J2clUtilGetDefineRewriterPass extends AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private Set<String> defines;

  public J2clUtilGetDefineRewriterPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }
    defines = new ProcessDefines(compiler, null, true).collectDefines(externs, root).keySet();
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (isUtilGetDefineCall(n)) {
      rewriteUtilGetDefine(t, n);
    }
  }

  private void rewriteUtilGetDefine(NodeTraversal t, Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    Node secondExpr = callNode.getLastChild();

    if (secondExpr != firstExpr) {
      secondExpr.detach();
    } else {
      // There is no secondExpr; default to null.
      secondExpr = IR.nullNode();
    }

    Node replacement = getDefineReplacement(firstExpr, secondExpr);
    replacement.useSourceInfoIfMissingFromForTree(callNode);
    callNode.replaceWith(replacement);
    t.reportCodeChange();
  }

  private Node getDefineReplacement(Node firstExpr, Node secondExpr) {
    if (defines.contains(firstExpr.getString())) {
      Node define = NodeUtil.newQName(compiler, firstExpr.getString());
      Node defineStringValue = NodeUtil.newCallNode(IR.name("String"), define);
      return IR.comma(secondExpr, defineStringValue);
    } else {
      return secondExpr;
    }
  }

  private static boolean isUtilGetDefineCall(Node n) {
    return n.isCall() && isUtilGetDefineMethodName(n.getFirstChild().getQualifiedName());
  }

  private static boolean isUtilGetDefineMethodName(String fnName) {
    // TODO: Switch this to the filename + property name heuristic which is less brittle.
    return fnName != null && fnName.endsWith(".$getDefine") && fnName.contains("Util");
  }
}
