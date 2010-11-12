/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks for misplaced semicolons, such as
 * <pre>
 * if (foo()); act_now();
 * </pre>
 * and generates warnings.
 *
 */
final class CheckAccidentalSemicolon extends AbstractPostOrderCallback {

  static final DiagnosticType SUSPICIOUS_SEMICOLON = DiagnosticType.warning(
      "JSC_SUSPICIOUS_SEMICOLON",
      "If this if/for/while really shouldn't have a body, use {}");

  private final CheckLevel level;

  CheckAccidentalSemicolon(CheckLevel level) {
    this.level = level;
  }

  public void visit(NodeTraversal t, Node n, Node parent) {
    Node child;
    switch (n.getType()) {
      case Token.IF:
        child = n.getFirstChild().getNext();  // skip the condition child
        break;

      case Token.WHILE:
      case Token.FOR:
        child = NodeUtil.getLoopCodeBlock(n);
        break;

      default:
        return;  // don't check other types
    }

    // semicolons cause VOID children. Empty blocks are allowed because
    // that's usually intentional, especially with loops.
    for (; child != null; child = child.getNext()) {
      if ((child.getType() == Token.BLOCK) && (!child.hasChildren())) {
        // Only warn on empty blocks that replaced EMPTY nodes.  BLOCKs with no
        // children are considered OK.
        if (child.wasEmptyNode()) {
          t.getCompiler().report(
              t.makeError(n, level, SUSPICIOUS_SEMICOLON));
        }
      }
    }
  }
}
