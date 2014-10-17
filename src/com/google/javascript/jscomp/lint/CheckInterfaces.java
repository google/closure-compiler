/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

/**
 * Checks for errors related to interfaces.
 *
 */
public final class CheckInterfaces extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  public static final DiagnosticType INTERFACE_FUNCTION_NOT_EMPTY =
      DiagnosticType.warning(
          "JSC_INTERFACE_FUNCTION_NOT_EMPTY",
          "interface functions must have an empty body");

  public static final DiagnosticType INTERFACE_SHOULD_NOT_TAKE_ARGS =
      DiagnosticType.warning(
          "JSC_INTERFACE_SHOULD_NOT_TAKE_ARGS",
          "Interface functions should not take any arguments");

  private final AbstractCompiler compiler;

  public CheckInterfaces(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  /** Whether a function is an interface constructor, or a method on an interface. */
  private boolean isInterface(Node n) {
    if (!n.isFunction()) {
      return false;
    }

    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
    return jsDoc != null && jsDoc.isInterface();
  }


  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!isInterface(n)) {
      return;
    }

    Node args = n.getFirstChild().getNext();
    if (args.hasChildren()) {
      t.report(args.getFirstChild(), INTERFACE_SHOULD_NOT_TAKE_ARGS);
    }

    Node block = n.getLastChild();
    if (block.hasChildren()) {
      t.report(block.getFirstChild(), INTERFACE_FUNCTION_NOT_EMPTY);
    }
  }
}
