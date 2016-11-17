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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Fold cases like substring(i, i + 1) with to charAt(i).
 *
 * This is an unsound optimization for cases like substring(NaN, NaN + 1), so we only turn it on
 * for J2CL generated code.
 *
 * @author moz@google.com (Michael Zhou)
 */
class J2clReplaceKnownMethodsPass extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;
  private final boolean useTypes;

  J2clReplaceKnownMethodsPass(AbstractCompiler compiler, boolean useTypes) {
    this.compiler = compiler;
    this.useTypes = useTypes;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    if (useTypes && J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      NodeTraversal.traverseEs6(compiler, scriptRoot, this);
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isCall() && n.hasXChildren(3) && n.getFirstChild().isGetProp()) {
      Node callTarget = n.getFirstChild();
      Node methodName = callTarget.getLastChild();
      switch (methodName.getString()) {
        case "substring":
        case "slice":
          Node stringNode = callTarget.getFirstChild();
          if (stringNode.getJSType() != null && stringNode.getJSType().isStringValueType()) {
            tryReplaceSubstringOrSliceWithCharAtForNameNodes(n);
          }
      }
    }
  }

  private void tryReplaceSubstringOrSliceWithCharAtForNameNodes(Node n) {
    Node callTarget = n.getFirstChild();
    Node firstArg = callTarget.getNext();
    Node secondArg = firstArg.getNext();
    if (firstArg.isName() && secondArg.isAdd()
        && secondArg.getFirstChild().isName()
        && secondArg.getFirstChild().getString().equals(firstArg.getString())
        && firstArg.getJSType() != null && firstArg.getJSType().isNumberValueType()) {
      Double maybeOne = NodeUtil.getNumberValue(secondArg.getSecondChild());
      if (maybeOne != null && maybeOne.intValue() == 1) { // substring(i, i + 1)
        replaceWithCharAt(callTarget, firstArg);
      }
    }
  }

  private void replaceWithCharAt(Node callTarget, Node firstArg) {
    // TODO(moz): Maybe correct the arity of the function type here.
    callTarget.getLastChild().setString("charAt");
    firstArg.getNext().detach();
    compiler.reportCodeChange();
  }
}
