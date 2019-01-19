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
import com.google.javascript.rhino.jstype.JSType;

/** An optimization pass to re-write J2CL Equality.$same. */
public class J2clEqualitySameRewriterPass extends AbstractPeepholeOptimization {

  private final boolean useTypes;
  private boolean shouldRunJ2clPasses;

  J2clEqualitySameRewriterPass(boolean useTypes) {
    this.useTypes = useTypes;
  }

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
      reportChangeToEnclosingScope(replacement);
    }
    return replacement;
  }

  private Node trySubstituteEqualitySame(Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    NodeValue firstExprValue = getKnownLiteralValue(firstExpr);
    Node secondExpr = callNode.getLastChild();
    NodeValue secondExprValue = getKnownLiteralValue(secondExpr);

    if (firstExprValue == NodeValue.UNKNOWN && secondExprValue == NodeValue.UNKNOWN) {
      return callNode;
    }

    if (firstExprValue == NodeValue.NULL_OR_UNDEFINED) {
      return rewriteNullCheck(secondExpr, firstExpr);
    }

    if (secondExprValue == NodeValue.NULL_OR_UNDEFINED) {
      return rewriteNullCheck(firstExpr, secondExpr);
    }

    // There is a coercion danger (e.g. 0 == null) but since at least one side is not null, we can
    // safely use === that will not trigger any coercion.
    return rewriteAsStrictEq(firstExpr, secondExpr);
  }

  private Node rewriteNullCheck(Node expr, Node nullExpression) {
    expr.detach();
    nullExpression.detach();
    if (useTypes && canOnlyBeObject(expr)) {
      return IR.not(expr);
    }
    // At least one side is null or undefined so no coercion danger with ==.
    return IR.eq(expr, nullExpression);
  }

  private boolean canOnlyBeObject(Node n) {
    JSType type = n.getJSType();
    if (type == null) {
      return false;
    }
    type = type.restrictByNotNullOrUndefined();
    return !type.isUnknownType() && !type.isEmptyType() && !type.isAllType() && type.isObjectType();
  }

  private Node rewriteAsStrictEq(Node firstExpr, Node secondExpr) {
    firstExpr.detach();
    secondExpr.detach();
    return IR.sheq(firstExpr, secondExpr);
  }

  private enum NodeValue {
    NULL_OR_UNDEFINED,
    NON_NULL,
    UNKNOWN,
  }

  private static NodeValue getKnownLiteralValue(Node n) {
    switch (NodeUtil.getKnownValueType(n)) {
      case VOID:
        return NodeUtil.canBeSideEffected(n) ? NodeValue.UNKNOWN : NodeValue.NULL_OR_UNDEFINED;
      case NULL:
        return NodeValue.NULL_OR_UNDEFINED;

      case NUMBER:
      case STRING:
      case BOOLEAN:
      case OBJECT:
        return NodeValue.NON_NULL;

      case UNDETERMINED:
        return NodeValue.UNKNOWN;
    }
    throw new AssertionError("Unknown ValueType");
  }

  private static boolean isEqualitySameCall(Node node) {
    return node.isCall()
        // Do not optimize if one or both parameters were removed
        && node.hasXChildren(3)
        && isEqualitySameMethodName(node.getFirstChild());
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
