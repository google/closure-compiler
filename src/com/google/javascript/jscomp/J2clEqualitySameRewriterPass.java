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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/** An optimization pass to re-write J2CL Equality.$same. */
public class J2clEqualitySameRewriterPass extends AbstractPeepholeOptimization {

  /** Whether to use "==" or "===". */
  private static enum Eq {
    DOUBLE,
    TRIPLE
  }

  private boolean shouldRunJ2clPasses = false;

  @Override
  void beginTraversal(AbstractCompiler compiler) {
    super.beginTraversal(compiler);
    shouldRunJ2clPasses = J2clSourceFileChecker.shouldRunJ2clPasses(compiler);
  }

  @Override
  Node optimizeSubtree(Node node) {
    if (!shouldRunJ2clPasses) {
      return node;
    }

    if (!isEqualitySameCall(node)) {
      return node;
    }

    Node replacement = trySubstituteEqualitySame(node);
    if (replacement != node) {
      replacement = replacement.useSourceInfoIfMissingFrom(node);
      node.replaceWith(replacement);
      compiler.reportChangeToEnclosingScope(replacement);
    }
    return replacement;
  }

  private Node trySubstituteEqualitySame(Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    Node secondExpr = callNode.getLastChild();

    if (NodeUtil.isNullOrUndefined(firstExpr) || NodeUtil.isNullOrUndefined(secondExpr)) {
      // At least one side is null or undefined so no coercion danger.
      return rewriteToEq(firstExpr, secondExpr, Eq.DOUBLE);
    }

    if (NodeUtil.isLiteralValue(firstExpr, true) || NodeUtil.isLiteralValue(secondExpr, true)) {
      // There is a coercion danger but since at least one side is not null, we can use === that
      // will not trigger any coercion.
      return rewriteToEq(firstExpr, secondExpr, Eq.TRIPLE);
    }

    return callNode;
  }

  private Node rewriteToEq(Node firstExpr, Node secondExpr, Eq eq) {
    firstExpr.detach();
    secondExpr.detach();
    return eq == Eq.DOUBLE ? IR.eq(firstExpr, secondExpr) : IR.sheq(firstExpr, secondExpr);
  }

  private static boolean isEqualitySameCall(Node node) {
    return node.isCall()
        && isEqualitySameMethodName(node.getFirstChild())
        // Do not optimize if one or both parameters were removed
        && node.hasXChildren(3);
  }

  private static boolean isEqualitySameMethodName(Node fnName) {
    if (!fnName.isQualifiedName()) {
      return false;
    }
    // NOTE: This should be rewritten to use method name + file name of definition site
    // like other J2CL passes, which is more precise.
    String originalQname = fnName.getOriginalQualifiedName();
    return originalQname.endsWith(".$same") && originalQname.contains("Equality");
  }
}
