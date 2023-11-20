/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.refactoring.examples;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.refactoring.Match;
import com.google.javascript.refactoring.NodeMetadata;
import com.google.javascript.refactoring.Scanner;
import com.google.javascript.refactoring.SuggestedFix;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;

/**
 * Replaces goog.bind(..., this) with arrow functions. The pretty-printer does not do well with
 * arrow functions, so it is recommended that you run 'g4 fix' to invoke clang-format on the CLs
 * created by this refactoring.
 *
 * TODO(tbreisacher): Handle (function(){}).bind(this); as well.
 */
public final class GoogBindToArrow extends Scanner {
  private static final QualifiedName GOOG_BIND_NAME = QualifiedName.of("goog.bind");

  private static boolean isGoogBind(Node n) {
    return n.isGetProp() && GOOG_BIND_NAME.matches(n);
  }

  @Override
  public boolean matches(Node node, NodeMetadata metadata) {
    if (!node.isCall()) {
      return false;
    }
    Node callee = node.getFirstChild();
    if (!isGoogBind(callee)) {
      return false;
    }

    Node firstArg = callee.getNext();
    if (firstArg == null || !firstArg.isFunction()) {
      return false;
    }

    Node secondArg = firstArg.getNext();
    return secondArg != null && secondArg.isThis() && secondArg.getNext() == null;
  }

  @Override
  public ImmutableList<SuggestedFix> processMatch(Match match) {
    AbstractCompiler compiler = match.getMetadata().getCompiler();
    Node googBindCall = match.getNode();
    Node function = googBindCall.getFirstChild().getNext();
    Node arrowFunction = function.cloneTree();
    arrowFunction.setIsArrowFunction(true);

    // For "function() { return x; }", transform to "() => x" instead of "() => { return x; }".
    Node body = function.getLastChild();
    if (body.hasOneChild()) {
      Node returnNode = body.getFirstChild();
      if (returnNode.isReturn()) {
        arrowFunction.getLastChild().replaceWith(returnNode.removeFirstChild());
      }
    }

    SuggestedFix.Builder fix = new SuggestedFix.Builder();
    fix.replace(googBindCall, arrowFunction, compiler);

    return ImmutableList.<SuggestedFix>of(fix.build());
  }
}
