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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/** A simple pass to ensure that all AST nodes that are expressions have types */
final class TypeInfoCheck implements Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private boolean requiresTypes = false;

  TypeInfoCheck(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void setCheckSubTree(Node root) {
    requiresTypes = true;
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void process(Node externs, Node root) {
    requiresTypes = false;
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isScript()) {
      // Each JavaScript file is rooted in a script node, so we'll only
      // have type information inside the script node.
      requiresTypes = true;
    } else if (n.isParamList()) {
      // typeI information is not included for children of PARAM_LIST,
      // so we turn off requireTypes for these nodes.
      requiresTypes = false;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (n.isScript()) {
      requiresTypes = false;
    } else if (requiresTypes && shouldHaveTypeInformation(n)) {
      if (n.isString() && (parent.isGetProp() || parent.isGetElem())) {
        return;
      }
      if (n.isParamList()) {
        return;
      }
      if (n.isName() && parent.isFunction()) {
        // function names does not need to have type information.
        return;
      }
      if (n.getJSType() == null) {
        throw new IllegalStateException(
            "No type information associated with "
                + n
                + " Most likely a Node has been created after type checking"
                + " without setting the type.");
      }
    }
  }

  private static boolean shouldHaveTypeInformation(Node node) {
    return node.isFunction() || !IR.mayBeStatement(node);
  }
}
