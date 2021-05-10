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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

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

    // Do not optimize if any parameters were removed during optimizations
    if (!node.hasXChildren(3)) {
      return node;
    }

    Node replacement = null;
    if (isStringEqualsMethod(node)) {
      replacement = trySubstituteStringEquals(node);
    } else if (isEqualitySameCall(node)) {
      replacement = trySubstituteEqualitySame(node);
    }

    if (replacement == null) {
      return node;
    }

    replacement.srcrefIfMissing(node);
    node.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    return replacement;
  }

  private Node trySubstituteStringEquals(Node callNode) {
    NodeValue firstExprValue = getKnownLiteralValue(callNode.getSecondChild());
    if (firstExprValue == NodeValue.UNKNOWN || firstExprValue == NodeValue.NULL_OR_UNDEFINED) {
      // Potential NPE, don't optimize.
      return null;
    }

    return trySubstituteEqualitySame(callNode);
  }

  private Node trySubstituteEqualitySame(Node callNode) {
    Node firstExpr = callNode.getSecondChild();
    NodeValue firstExprValue = getKnownLiteralValue(firstExpr);
    Node secondExpr = callNode.getLastChild();
    NodeValue secondExprValue = getKnownLiteralValue(secondExpr);

    if (firstExprValue == NodeValue.UNKNOWN && secondExprValue == NodeValue.UNKNOWN) {
      return null;
    }

    if (firstExprValue == NodeValue.NULL_OR_UNDEFINED) {
      return rewriteNullCheck(secondExpr, firstExpr);
    }

    if (secondExprValue == NodeValue.NULL_OR_UNDEFINED) {
      return rewriteNullCheck(firstExpr, secondExpr);
    }

    if (firstExprValue == NodeValue.NON_NULL || secondExprValue == NodeValue.NON_NULL) {
      // There is a coercion danger (e.g. 0 == null) but since at least one side is not null, we can
      // safely use === that will not trigger any coercion.
      return rewriteAsStrictEq(firstExpr, secondExpr);
    }

    checkState(firstExprValue == NodeValue.NUMBER || secondExprValue == NodeValue.NUMBER);
    return rewriteNumberCheck(firstExpr, secondExpr);
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
    Color color = n.getColor();
    // Safe as long as the color is a) not the UNKNOWN native color and b) not any primitive
    if (color == null) {
      return false;
    }

    if (color.isUnion()) {
      // ignore null/undefined
      color = color.subtractNullOrVoid();
    }

    // In theory we could allow unions of multiple objects here
    return !color.isUnion() && !color.isPrimitive() && !color.equals(StandardColors.UNKNOWN);
  }

  private Node rewriteAsStrictEq(Node firstExpr, Node secondExpr) {
    firstExpr.detach();
    secondExpr.detach();
    return IR.sheq(firstExpr, secondExpr);
  }

  private Node rewriteNumberCheck(Node firstExpr, Node secondExpr) {
    Double firstValue = NodeUtil.getNumberValue(firstExpr);
    Double secondValue = NodeUtil.getNumberValue(secondExpr);

    if (firstValue != null && secondValue != null) {
      firstExpr.detach();
      secondExpr.detach();
      return NodeUtil.booleanNode(firstValue.equals(secondValue));
    }

    if (isSafeNumber(firstValue) || isSafeNumber(secondValue)) {
      // Since one side is not 0, -0 or NaN, there is no risk of -0 vs 0 or NaN vs NaN comparison.
      return rewriteAsStrictEq(firstExpr, secondExpr);
    }

    if (useTypes && (canOnlyBeObject(firstExpr) || canOnlyBeObject(secondExpr))) {
      // Since one side is not number, there is no risk of -0 vs 0 or NaN vs NaN comparision.
      return rewriteAsStrictEq(firstExpr, secondExpr);
    }

    return null;
  }

  private static boolean isSafeNumber(Double d) {
    return d != null && d != 0 && !d.isNaN();
  }

  private enum NodeValue {
    UNKNOWN,
    NULL_OR_UNDEFINED,
    // JavaScript number. Needs special treatment to preserve Object.is semantics.
    NUMBER,
    // Non-null and also known to be not a number.
    NON_NULL,
  }

  private static NodeValue getKnownLiteralValue(Node n) {
    switch (NodeUtil.getKnownValueType(n)) {
      case VOID:
        return NodeUtil.canBeSideEffected(n) ? NodeValue.UNKNOWN : NodeValue.NULL_OR_UNDEFINED;
      case NULL:
        return NodeValue.NULL_OR_UNDEFINED;

      case STRING:
      case BOOLEAN:
      case OBJECT:
      case BIGINT:
        return NodeValue.NON_NULL;

      case NUMBER:
        return NodeValue.NUMBER;

      case UNDETERMINED:
        return NodeValue.UNKNOWN;
    }
    throw new AssertionError("Unknown ValueType");
  }

  private static boolean isEqualitySameCall(Node node) {
    return node.isCall() && hasName(node.getFirstChild(), "Equality", "$same");
  }

  private static boolean isStringEqualsMethod(Node node) {
    return node.isCall()
        && hasName(node.getFirstChild(), "String", "m_equals__java_lang_String__java_lang_Object");
  }

  private static boolean hasName(Node fnName, String className, String methodName) {
    if (!fnName.isQualifiedName()) {
      return false;
    }
    // NOTE: This should be rewritten to use method name + file name of definition site
    // like other J2CL passes, which is more precise.
    String originalQname = fnName.getOriginalQualifiedName();
    return originalQname.endsWith(methodName) && originalQname.contains(className);
  }
}
