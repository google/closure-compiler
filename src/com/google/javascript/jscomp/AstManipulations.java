/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/** Utilities to abstract away certain common AST manipulations */
public final class AstManipulations {

  // Non-instantiable
  private AstManipulations() {}

  /**
   * Returns a single node equivalent to executing {@code <expr1, expr2>}.
   *
   * <p>Requires that expr1 and expr2 are detached (i.e. have no parent nodes). If exp2 is EMPTY
   * this just returns exp1.
   */
  public static Node fuseExpressions(Node exp1, Node exp2) {
    checkArgument(exp1.getParent() == null, "Expected detached node, got %s", exp1);
    checkArgument(exp2.getParent() == null, "Expected detached node, got %s", exp2);
    if (exp2.isEmpty()) {
      return exp1;
    }
    Node comma = new Node(Token.COMMA, exp1);
    comma.srcrefIfMissing(exp2);

    // We can just join the new comma expression with another comma but
    // lets keep all the comma's in a straight line. That way we can use
    // tree comparison.
    if (exp2.isComma()) {
      Node leftMostChild = exp2;
      while (leftMostChild.isComma()) {
        leftMostChild = leftMostChild.getFirstChild();
      }
      Node parent = leftMostChild.getParent();
      comma.addChildToBack(leftMostChild.detach());
      parent.addChildToFront(comma);
      return exp2;
    } else {
      comma.addChildToBack(exp2);
      return comma;
    }
  }
}
