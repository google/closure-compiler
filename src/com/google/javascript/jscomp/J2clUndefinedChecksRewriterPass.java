/*
 * Copyright 2025 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

final class J2clUndefinedChecksRewriterPass extends AbstractPeepholeOptimization {

  private boolean shouldRunJ2clPasses = false;

  @Override
  void beginTraversal(AbstractCompiler compiler) {
    super.beginTraversal(compiler);
    shouldRunJ2clPasses = J2clSourceFileChecker.shouldRunJ2clPasses(compiler);
  }

  @Override
  Node optimizeSubtree(Node subtree) {
    if (!shouldRunJ2clPasses) {
      return subtree;
    }

    Node replacement = null;
    if (isUtilCall(subtree, "$isUndefined") && subtree.hasXChildren(2)) {
      replacement = maybeOptimizeIsUndefinedCall(subtree);
    } else if (isUtilCall(subtree, "$coerceToNull") && subtree.hasXChildren(2)) {
      replacement = maybeOptimizeCoerceToNullCall(subtree);
    }

    if (replacement != null) {
      checkState(replacement != subtree);
      replacement.srcrefTreeIfMissing(subtree);
      subtree.replaceWith(replacement);
      reportChangeToEnclosingScope(replacement);
      return replacement;
    }

    return subtree;
  }

  private static @Nullable Node maybeOptimizeIsUndefinedCall(Node node) {
    Node valueExpression = checkNotNull(node.getSecondChild());
    if (NodeUtil.isUndefined(valueExpression)) {
      return IR.trueNode();
    } else if (NodeUtil.isDefinedValue(valueExpression)) {
      return IR.comma(valueExpression.detach(), IR.falseNode());
    } else {
      return null;
    }
  }

  private static @Nullable Node maybeOptimizeCoerceToNullCall(Node node) {
    Node valueExpression = checkNotNull(node.getSecondChild());
    if (NodeUtil.isUndefined(valueExpression)) {
      return IR.nullNode();
    }

    if (NodeUtil.isDefinedValue(valueExpression)) {
      // detach the valueExpression as we'll be using it as the replacement node.
      return valueExpression.detach();
    }

    return null;
  }

  private static boolean isUtilCall(Node node, String methodName) {
    if (!node.isCall() || !node.getFirstChild().isQualifiedName()) {
      return false;
    }
    String qualifiedName = node.getFirstChild().getOriginalQualifiedName();
    return qualifiedName.contains("nativebootstrap$Util") && qualifiedName.endsWith(methodName);
  }
}
