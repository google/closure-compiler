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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * An optimization pass to re-write J2CL Equality.$same.
 */
public class J2clEqualitySameRewriterPass extends AbstractPostOrderCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;

  J2clEqualitySameRewriterPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node node, Node parent) {
    if (isEqualitySameCall(node)) {
      trySubstituteEqualitySame(node);
    }
  }

  private void trySubstituteEqualitySame(Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    Node secondExpr = callNode.getLastChild();
    if (!NodeUtil.isLiteralValue(firstExpr, true) && !NodeUtil.isLiteralValue(secondExpr, true)) {
      return;
    }

    // At least one is literal value. So we can replace w/ a simpler form.
    firstExpr.detachFromParent();
    secondExpr.detachFromParent();
    Node replacement = asEqOperation(firstExpr, secondExpr);
    callNode.getParent().replaceChild(callNode, replacement.useSourceInfoIfMissingFrom(callNode));
    compiler.reportCodeChange();
  }

  private Node asEqOperation(Node firstExpr, Node secondExpr) {
    return (NodeUtil.isNullOrUndefined(firstExpr) || NodeUtil.isNullOrUndefined(secondExpr))
        ? IR.eq(firstExpr, secondExpr)
        : IR.sheq(firstExpr, secondExpr);
  }

  private static boolean isEqualitySameCall(Node node) {
    return node.isCall() && isEqualitySameMethodName(node.getFirstChild().getQualifiedName());
  }

  private static boolean isEqualitySameMethodName(String fnName) {
    // The '.$same' case only happens when collapseProperties is off.
    return fnName != null
        && (fnName.endsWith("Equality$$0same") || fnName.endsWith("Equality.$same"));
  }
}
