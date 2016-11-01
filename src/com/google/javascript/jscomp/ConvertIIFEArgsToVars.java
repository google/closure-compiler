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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convert the parameters of an immediately invoked function expression to variables and assign the
 * value from the call arguments. This improves function inlining.
 *
 * <p>This pass relies on variable renaming (done during normalization) to avoid accidental variable
 * capture.
 *
 * <p>Since passes which implement OptimizeCalls only handle named functions, this pass doesn't
 * extend OptimizeCalls.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
public class ConvertIIFEArgsToVars implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private final Set<Node> iifeCallsWithArgsToRemove = new HashSet<>();

  public ConvertIIFEArgsToVars(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isCall() && this.iifeCallsWithArgsToRemove.contains(n)) {
      this.iifeCallsWithArgsToRemove.remove(n);
      // Remove all arguments of the call node, except "this" if it exists.
      Node removeRef;
      if (n.getBooleanProp(Node.FREE_CALL)) {
        removeRef = n.getFirstChild();
      } else {
        removeRef = n.getSecondChild();
      }
      while (removeRef.getNext() != null) {
        n.removeChildAfter(removeRef);
      }
      return;
    }

    if (!n.isFunction() || parent == null) {
      return;
    }

    Node call;
    Node grandparent = parent.getParent();

    // Check for standard IIFE
    // (function() { })()
    if (parent.isCall() && parent.getFirstChild() == n) {
      call = parent;

      // Check for explicit calls
      // (function() {} ).call(null)
    } else if (parent.isGetProp()
        && parent.getFirstChild() == n
        && n.getNext() != null
        && n.getNext().isString()
        && n.getNext().getString().equals("call")
        && grandparent != null
        && grandparent.isCall()
        && grandparent.getFirstChild() == parent) {
      call = grandparent;
    } else {
      return;
    }

    Node funcParams = NodeUtil.getFunctionParameters(n);
    if (!funcParams.hasChildren() || NodeUtil.isVarArgsFunction(n)) {
      return;
    }

    Node param = funcParams.getFirstChild();
    List<Node> newVars = new ArrayList<>();
    while (param != null) {
      newVars.add(
          IR.var(param.cloneNode(), getCallArgument(param, call)).useSourceInfoFromForTree(param));
      param = param.getNext();
    }
    funcParams.removeChildren();
    for (int i = newVars.size() - 1; i >= 0; i--) {
      funcParams.getNext().addChildToFront(newVars.get(i));
    }

    // When visiting the call, remove arguments that are rewritten here
    this.iifeCallsWithArgsToRemove.add(call);
    compiler.reportCodeChange();
  }

  /**
   * For a function param name of an IIFE, find the matching call argument and return it as a copy.
   */
  public Node getCallArgument(Node param, Node call) {
    Preconditions.checkState(param.isName());
    Preconditions.checkState(param.getParent() != null && param.getParent().isParamList());
    Preconditions.checkState(call != null && call.isCall());

    int argIndex = param.getParent().getIndexOfChild(param);
    Node callArg = call.getSecondChild();
    if (!call.getBooleanProp(Node.FREE_CALL)) {
      callArg = callArg.getNext();
    }
    for (int i = 0; i < argIndex && callArg != null; i++) {
      callArg = callArg.getNext();
    }

    // If the call passes fewer arguments to the IIFE than the declared arguments,
    // assign the rest to "undefined".
    if (callArg == null) {
      return NodeUtil.newName(compiler, "undefined", param, param.getString());
    }

    return callArg.cloneTree();
  }
}
