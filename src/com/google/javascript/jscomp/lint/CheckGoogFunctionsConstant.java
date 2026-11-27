/*
 * Copyright 2026 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;

/**
 * Warns when {@code goog.functions.constant(true)}, {@code goog.functions.constant(false)} or
 * {@code goog.functions.constant(null)} is called, and suggests the equivalent shared function
 * {@code goog.functions.TRUE}, {@code goog.functions.FALSE} or {@code goog.functions.NULL}.
 */
public final class CheckGoogFunctionsConstant extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  public static final DiagnosticType USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT =
      DiagnosticType.disabled(
          "JSC_USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT",
          "Use {0} instead of goog.functions.constant({1}).");

  private final AbstractCompiler compiler;

  public CheckGoogFunctionsConstant(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isCall() || !n.getFirstChild().matchesQualifiedName("goog.functions.constant")) {
      return;
    }
    Node arg = n.getSecondChild();
    if (arg == null || arg.getNext() != null) {
      return;
    }
    String literal;
    String replacement;
    if (arg.isTrue()) {
      literal = "true";
      replacement = "goog.functions.TRUE";
    } else if (arg.isFalse()) {
      literal = "false";
      replacement = "goog.functions.FALSE";
    } else if (arg.isNull()) {
      literal = "null";
      replacement = "goog.functions.NULL";
    } else {
      return;
    }
    compiler.report(
        JSError.make(n, USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT, replacement, literal));
  }
}
