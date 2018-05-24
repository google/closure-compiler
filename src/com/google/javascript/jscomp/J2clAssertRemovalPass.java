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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/** An optimization pass to remove J2CL Asserts.$assert. */
public class J2clAssertRemovalPass extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  J2clAssertRemovalPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }

    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node node, Node parent) {
    if (isAssertionCall(node)) {
      NodeUtil.deleteFunctionCall(node, compiler);
    }
  }

  private static boolean isAssertionCall(Node node) {
    return node.isCall() && isAssertionMethodName(node.getFirstChild());
  }

  private static boolean isAssertionMethodName(Node fnName) {
    if (!fnName.isQualifiedName()) {
      return false;
    }
    String originalQname = fnName.getOriginalQualifiedName();
    return originalQname.contains("Asserts") && originalQname.contains(".$assert");
  }
}
