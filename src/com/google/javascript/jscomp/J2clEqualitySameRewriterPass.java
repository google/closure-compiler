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
import com.google.javascript.rhino.jstype.JSType;

/**
 * An optimization pass to re-write J2CL Equality.$same.
 */
public class J2clEqualitySameRewriterPass extends AbstractPostOrderCallback
    implements CompilerPass {

  /** Whether to use "==" or "===". */
  private static enum Eq {
    DOUBLE,
    TRIPLE
  }

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

    if (NodeUtil.isNullOrUndefined(firstExpr) || NodeUtil.isNullOrUndefined(secondExpr)) {
      // At least one side is null or undefined so no coercion danger.
      rewriteToEq(callNode, firstExpr, secondExpr, Eq.DOUBLE);
      return;
    }

    if (NodeUtil.isLiteralValue(firstExpr, true) || NodeUtil.isLiteralValue(secondExpr, true)) {
      // There is a coercion danger but since at least one side is not null, we can use === that
      // will not trigger any coercion.
      rewriteToEq(callNode, firstExpr, secondExpr, Eq.TRIPLE);
      return;
    }

    // "--use_types_for_optimization" must be on to enable the following type check.
    if (!compiler.getOptions().useTypesForOptimization) {
      return;
    }

    JSType firstType = getTypeRestrictByNotNullOrUndefined(firstExpr);
    JSType secondType = getTypeRestrictByNotNullOrUndefined(secondExpr);
    if (isObjectType(firstType) || isObjectType(secondType) || sameType(firstType, secondType)) {
      // Typeof is same for both so no coersion danger.
      rewriteToEq(callNode, firstExpr, secondExpr, Eq.DOUBLE);
      return;
    }
  }

  private void rewriteToEq(Node callNode, Node firstExpr, Node secondExpr, Eq eq) {
    firstExpr.detachFromParent();
    secondExpr.detachFromParent();
    Node replacement =
        eq == Eq.DOUBLE ? IR.eq(firstExpr, secondExpr) : IR.sheq(firstExpr, secondExpr);
    callNode.getParent().replaceChild(callNode, replacement.useSourceInfoIfMissingFrom(callNode));
    compiler.reportCodeChange();
  }

  private static boolean isEqualitySameCall(Node node) {
    return node.isCall() && isEqualitySameMethodName(node.getFirstChild());
  }

  private static boolean isEqualitySameMethodName(Node fnName) {
    if (!fnName.isQualifiedName()) {
      return false;
    }
    // NOTE: This should be rewritten to use method name + file name of definition site
    // like other j2cl passes, which is more precise.
    String originalQname = fnName.getOriginalQualifiedName();
    return originalQname.endsWith("Equality.$same");
  }

  private static JSType getTypeRestrictByNotNullOrUndefined(Node node) {
    JSType jsType = node.getJSType();
    return jsType == null ? null : jsType.restrictByNotNullOrUndefined();
  }

  private static boolean isObjectType(JSType jsType) {
    return !isUnknownType(jsType) && jsType.isObject();
  }

  private static boolean sameType(JSType jsType1, JSType jsType2) {
    return !isUnknownType(jsType1) && !isUnknownType(jsType2) && jsType1.equals(jsType2);
  }

  private static boolean isUnknownType(JSType jsType) {
    return jsType == null || jsType.isUnknownType() || jsType.isNoType() || jsType.isAllType();
  }
}
