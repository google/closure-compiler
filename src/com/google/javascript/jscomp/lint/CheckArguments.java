/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.Node;


/**
 * Check for the 'arguments' object being used in ways which are unlikely to be optimized well,
 * and which we cannot transpile correctly.
 */
public final class CheckArguments extends AbstractPostOrderCallback implements CompilerPass {
  public static final DiagnosticType BAD_ARGUMENTS_USAGE = DiagnosticType.warning(
      "JSC_BAD_ARGUMENTS_USAGE",
      "This use of the 'arguments' object is discouraged.");

  private final AbstractCompiler compiler;

  public CheckArguments(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isName()) {
      return;
    }

    Var var = t.getScope().getVar(n.getString());
    if (var != null && var.isArguments()) {
      checkArgumentsUsage(n, parent);
    }
  }

  private void checkArgumentsUsage(Node arguments, Node parent) {
    if (parent.isSpread()
        || (parent.isGetProp() && parent.matchesQualifiedName("arguments.length"))
        || (parent.isForOf() && arguments == parent.getFirstChild().getNext())
        || (parent.isGetElem() && arguments == parent.getFirstChild())) {
      // No warning.
    } else {
      report(arguments);
    }
  }

  private void report(Node arguments) {
    compiler.report(JSError.make(arguments, BAD_ARGUMENTS_USAGE));
  }
}

