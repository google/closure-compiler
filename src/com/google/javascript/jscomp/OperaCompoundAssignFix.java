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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Deque;
import java.util.List;

/**
 * Temp fix to decompose nested assignment bug in Opera.
 *
 * See open source issue: 390
 *
 *
 * The conditions for which this Opera-specific bug will hit is
 *
 * v = rhs
 *
 * where 'rhs' contains a compound assignment of the form  a[i] or obj.p _and_
 * 'v' is also used in 'rhs'. i.e., for the above example,
 *
 * z = bar[z] = bar[z] || [];
 *
 * or
 *
 * x = foo.bar += x.baz;
 *
 * Opera 11.10 final will have the fix included, but if emitting constructs like
 * the above is common and can be readily avoided, that'd be very helpful. More
 * than happy to supply extra information & work through the details to make
 * this happen, if needed.
 * --sof / sof@opera.com
 *
 */
class OperaCompoundAssignFix extends AbstractPostOrderCallback
    implements CompilerPass, ScopedCallback {
  private AbstractCompiler compiler;
  private final Deque<VariableNameGenerator> names;

  @Override
  public void enterScope(NodeTraversal t) {
    names.push(new VariableNameGenerator(t.getScope()));
  }

  @Override
  public void exitScope(NodeTraversal t) {
    names.pop();
  }

  OperaCompoundAssignFix(AbstractCompiler compiler) {
    this.compiler = compiler;
    names = Lists.newLinkedList();
  }

  @Override
  public void process(Node externs, Node root) {
    List<Node> code = Lists.newArrayList(externs, root);
    NodeTraversal.traverseRoots(compiler, code, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!NodeUtil.isName(n)) {
      return;
    }

    if (!NodeUtil.isGet(parent)) {
      return;
    }

    boolean nested = false;
    boolean reassign = false;
    Node lastAssign = null;
    Node prevParent = n;

    while (!(NodeUtil.isExpressionNode(parent) ||
             NodeUtil.isStatementBlock(parent))) {
      if (NodeUtil.isAssign(parent) &&
          NodeUtil.isName(parent.getFirstChild()) &&
          parent.getFirstChild().getString().equals(n.getString()) &&
          nested) {
        reassign = true;
        break;
      } else if (NodeUtil.isAssignmentOp(parent) &&
          parent.getLastChild() == prevParent) {
        if (lastAssign == null) {
          nested = true;
        }
        lastAssign = parent;
      }
      prevParent = parent;
      parent = parent.getParent();
    }

    if (!(reassign && nested)) {
      return;
    }

    applyWorkAround(parent, t);
  }

  private void applyWorkAround(Node assign, NodeTraversal t) {
//System.out.println("applyWorkAround: " + assign.toStringTree());
    Preconditions.checkArgument(NodeUtil.isAssign(assign));
    Node parent = assign.getParent();
    Node comma = new Node(Token.COMMA);
    comma.copyInformationFrom(assign);
    parent.replaceChild(assign, comma);

    String newName = names.peek().getNextNewName();
    Node newAssign = new Node(Token.ASSIGN,
        Node.newString(Token.NAME, newName));
    newAssign.copyInformationFromForTree(assign);
    newAssign.addChildToBack(assign.getLastChild().detachFromParent());
    comma.addChildrenToBack(newAssign);
    assign.addChildrenToBack(
        Node.newString(Token.NAME, newName).copyInformationFrom(assign));
    comma.addChildrenToBack(assign);

    Node root = t.getScopeRoot();
    Node var = new Node(Token.VAR, Node.newString(Token.NAME, newName));
    var.copyInformationFromForTree(assign);

    if (NodeUtil.isStatementBlock(root)) {
      root = compiler.getNodeForCodeInsertion(t.getModule());
      root.addChildrenToFront(var);
    } else {
      root.getLastChild().addChildrenToFront(var);
    }
    compiler.reportCodeChange();
  }
}
