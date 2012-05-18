/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Performs type-aware peephole optimizations.
 *
 * These peephole optimizations are in their own class because
 * type information may not always be available (such as during pre-processing)
 * or may not be turned on.
 *
 * Currently only Token.TYPEOF is folded -- in the future it may be possible to
 * fold Token.INSTANCEOF as well. Another possibility is folding when
 * non-nullable objects are used in Boolean logic, such as:
 * "if (x) {" or "(!x) ? a : b" or "x && foo()"
 *
 * TODO(dcc): Support folding Token.INSTANCEOF and non-nullable objects
 * in Boolean logic.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
class PeepholeFoldWithTypes extends AbstractPeepholeOptimization {

  @Override
  Node optimizeSubtree(Node subtree) {
    switch (subtree.getType()) {
      case Token.TYPEOF:
        return tryFoldTypeof(subtree);
      default:
        return subtree;
    }
  }

  /**
   * Folds "typeof expression" based on the JSType of "expression" if the
   * expression has no side effects.
   *
   * <p>E.g.,
   * <pre>
   * var x = 6;
   * if (typeof(x) == "number") {
   * }
   * </pre>
   * folds to
   * <pre>
   * var x = 6;
   * if ("number" == "number") {
   * }
   * </pre>
   *
   * <p>This method doesn't fold literal values -- we leave that to
   * PeepholeFoldConstants.
   */
  private Node tryFoldTypeof(Node typeofNode) {
    Preconditions.checkArgument(typeofNode.isTypeOf());
    Preconditions.checkArgument(typeofNode.getFirstChild() != null);

    Node argumentNode = typeofNode.getFirstChild();

    // We'll let PeepholeFoldConstants handle folding literals
    // and we can't remove arguments with possible side effects.
    if (!NodeUtil.isLiteralValue(argumentNode, true) &&
        !mayHaveSideEffects(argumentNode)) {
      JSType argumentType = argumentNode.getJSType();

      String typeName = null;

      if (argumentType != null) {
        // typeof null is "object" in JavaScript
        if (argumentType.isObject() || argumentType.isNullType()) {
          typeName = "object";
        } else if (argumentType.isStringValueType()) {
          typeName = "string";
        } else if (argumentType.isNumberValueType()) {
          typeName = "number";
        } else if (argumentType.isBooleanValueType()) {
          typeName = "boolean";
        } else if (argumentType.isVoidType()) {
           typeName = "undefined";
        } else if (argumentType.isUnionType()) {
          // TODO(dcc): We don't handle union types, for now,
          // but could support, say, unions of different object types
          // in the future.
          typeName = null;
        }

        if (typeName != null) {
          Node newNode = IR.string(typeName);
          typeofNode.getParent().replaceChild(typeofNode, newNode);
          reportCodeChange();

          return newNode;
        }
      }
    }
    return typeofNode;
  }
}
