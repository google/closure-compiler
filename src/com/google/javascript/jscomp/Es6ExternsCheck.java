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

import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Checks to make sure the required ES6 externs are present.
 */
final class Es6ExternsCheck extends AbstractPreOrderCallback implements CompilerPass {
  static final DiagnosticType MISSING_ES6_EXTERNS =
      DiagnosticType.error("JSC_MISSING_ES6_EXTERNS",
          "Missing externs definition for Symbol. Did you forget to include the ES6 externs?");

  private final AbstractCompiler compiler;
  private boolean hasSymbolExterns = false;

  Es6ExternsCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    if (!hasSymbolExterns) {
      compiler.report(JSError.make(MISSING_ES6_EXTERNS));
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (NodeUtil.isFunctionDeclaration(n) && n.getFirstChild().matchesQualifiedName("Symbol")) {
      hasSymbolExterns = true;
    }
    return !hasSymbolExterns;
  }
}
