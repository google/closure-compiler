/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;

/**
 * {@link CheckDebuggerStatement} checks for the presence of the "debugger"
 * statement in JavaScript code. It is appropriate to use this statement while
 * developing JavaScript; however, it is generally undesirable to include it in
 * production code.
 *
 * @author bolinfest@google.com (Michael Bolin)
 */
class CheckDebuggerStatement extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType DEBUGGER_STATEMENT_PRESENT =
    DiagnosticType.disabled("JSC_DEBUGGER_STATEMENT_PRESENT",
        "Using the debugger statement can halt your application if the user " +
        "has a JavaScript debugger running.");

  private final AbstractCompiler compiler;

  public CheckDebuggerStatement(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isDebugger()) {
      t.report(n, DEBUGGER_STATEMENT_PRESENT);
    }
  }
}
