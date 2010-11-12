/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * Transform the structure of the AST so that the number of explicit exits
 * are minimized.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class MinimizeExitPoints
    extends AbstractPostOrderCallback
    implements CompilerPass {

  AbstractCompiler compiler;

  MinimizeExitPoints(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.LABEL:
        tryMinimizeExits(
            n.getLastChild(), Token.BREAK, n.getFirstChild().getString());
        break;

      case Token.FOR:
      case Token.WHILE:
        tryMinimizeExits(
            NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);
        break;

      case Token.DO:
        tryMinimizeExits(
            NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);

        Node cond = NodeUtil.getConditionExpression(n);
        if (NodeUtil.getBooleanValue(cond) == TernaryValue.FALSE) {
          // Normally, we wouldn't be able to optimize BREAKs inside a loop
          // but as we know the condition will always false, we can treat them
          // as we would a CONTINUE.
          tryMinimizeExits(
              n.getFirstChild(), Token.BREAK, null);
        }
        break;

      case Token.FUNCTION:
        tryMinimizeExits(
            n.getLastChild(), Token.RETURN, null);
        break;
    }
  }

  /**
   * Attempts to minimize the number of explicit exit points in a control
   * structure to take advantage of the implied exit at the end of the
   * structure.  This is accomplished by removing redundant statements, and
   * moving statements following a qualifying IF node into that node.
   * For example:
   *
   * function () {
   *   if (x) return;
   *   else blah();
   *   foo();
   * }
   *
   * becomes:
   *
   * function () {
   *  if (x) ;
   *  else {
   *    blah();
   *    foo();
   *  }
   *
   * @param n The execution node of a parent to inspect.
   * @param exitType The type of exit to look for.
   * @param labelName If parent is a label the name of the label to look for,
   *   null otherwise.
   * @nullable labelName non-null only for breaks within labels.
   */
  void tryMinimizeExits(Node n, int exitType, String labelName) {

    // Just an 'exit'.
    if (matchingExitNode(n, exitType, labelName)) {
      NodeUtil.removeChild(n.getParent(), n);
      compiler.reportCodeChange();
      return;
    }

    // Just an 'if'.
    if (n.getType() == Token.IF) {
      Node ifBlock = n.getFirstChild().getNext();
      tryMinimizeExits(ifBlock, exitType, labelName);
      Node elseBlock = ifBlock.getNext();
      if (elseBlock != null) {
        tryMinimizeExits(elseBlock, exitType, labelName);
      }
      return;
    }

    // Just a 'try/catch/finally'.
    if (n.getType() == Token.TRY) {
      Node tryBlock = n.getFirstChild();
      tryMinimizeExits(tryBlock, exitType, labelName);
      Node allCatchNodes = NodeUtil.getCatchBlock(n);
      if (NodeUtil.hasCatchHandler(allCatchNodes)) {
        Preconditions.checkState(allCatchNodes.hasOneChild());
        Node catchNode = allCatchNodes.getFirstChild();
        Node catchCodeBlock = catchNode.getLastChild();
        tryMinimizeExits(catchCodeBlock, exitType, labelName);
      }
      if (NodeUtil.hasFinally(n)) {
        Node finallyBlock = n.getLastChild();
        tryMinimizeExits(finallyBlock, exitType, labelName);
      }
    }

    // Just a 'label'.
    if (n.getType() == Token.LABEL) {
      Node labelBlock = n.getLastChild();
      tryMinimizeExits(labelBlock, exitType, labelName);
    }

    // TODO(johnlenz): The last case of SWITCH statement?

    // The rest assumes a block with at least one child, bail on anything else.
    if (n.getType() != Token.BLOCK || n.getLastChild() == null) {
      return;
    }

    // Multiple if-exits can be converted in a single pass.
    // Convert "if (blah) break;  if (blah2) break; other_stmt;" to
    // become "if (blah); else { if (blah2); else { other_stmt; } }"
    // which will get converted to "if (!blah && !blah2) { other_stmt; }".
    for (Node c : n.children()) {

      // An 'if' block to process below.
      if (c.getType() == Token.IF) {
        Node ifTree = c;
        Node trueBlock, falseBlock;

        // First, the true condition block.
        trueBlock = ifTree.getFirstChild().getNext();
        falseBlock = trueBlock.getNext();
        tryMinimizeIfBlockExits(trueBlock, falseBlock,
            ifTree, exitType, labelName);

        // Now the else block.
        // The if blocks may have changed, get them again.
        trueBlock = ifTree.getFirstChild().getNext();
        falseBlock = trueBlock.getNext();
        if (falseBlock != null) {
          tryMinimizeIfBlockExits(falseBlock, trueBlock,
              ifTree, exitType, labelName);
        }
      }

      if (c == n.getLastChild()) {
        break;
      }
    }

    // Now try to minimize the exits of the last child, if it is removed
    // look at what has become the last child.
    for (Node c = n.getLastChild(); c != null; c = n.getLastChild()) {
      tryMinimizeExits(c, exitType, labelName);
      // If the node is still the last child, we are done.
      if (c == n.getLastChild()) {
        break;
      }
    }
  }

  /**
   * Look for exits (returns, breaks, or continues, depending on the context) at
   * the end of a block and removes them by moving the if node's siblings, 
   * if any, into the opposite condition block.
   *
   * @param srcBlock The block to inspect.
   * @param destBlock The block to move sibling nodes into.
   * @param ifNode The if node to work with.
   * @param exitType The type of exit to look for.
   * @param labelName The name associated with the exit, if any.
   * @nullable labelName null for anything excepted for named-break associated
   *           with a label.
   */
  private void tryMinimizeIfBlockExits(Node srcBlock, Node destBlock,
      Node ifNode, int exitType, String labelName) {
    Node exitNodeParent = null;
    Node exitNode = null;

    // Pick an exit node candidate.
    if (srcBlock.getType() == Token.BLOCK) {
      if (!srcBlock.hasChildren()) {
        return;
      }
      exitNodeParent = srcBlock;
      exitNode = exitNodeParent.getLastChild();
    } else {
      // Just a single statement, if it isn't an exit bail.
      exitNodeParent = ifNode;
      exitNode = srcBlock;
    }

    // Verify the candidate.
    if (!matchingExitNode(exitNode, exitType, labelName)) {
      return;
    }

    // Take case of the if nodes siblings, if any.
    if (ifNode.getNext() != null) {
      // Move siblings of the if block into the opposite
      // logic block of the exit.
      Node newDestBlock = new Node(Token.BLOCK).copyInformationFrom(ifNode);
      if (destBlock == null) {
        // Only possible if this is the false block.
        ifNode.addChildToBack(newDestBlock);
      } else if (destBlock.getType() == Token.EMPTY) {
        // Use the new block.
        ifNode.replaceChild(destBlock, newDestBlock);
      } else if (destBlock.getType() == Token.BLOCK) {
        // Reuse the existing block.
        newDestBlock = destBlock;
      } else {
        // Add the existing statement to the new block.
        ifNode.replaceChild(destBlock, newDestBlock);
        newDestBlock.addChildToBack(destBlock);
      }

      // Move all the if node's following siblings.
      moveAllFollowing(ifNode, ifNode.getParent(), newDestBlock);
    }

    // Get rid of the "exit", replace with an empty item if needed.
    NodeUtil.removeChild(exitNodeParent, exitNode);

    compiler.reportCodeChange();
  }

  /**
   * Determines if n matches the type and name for the following types of
   * "exits":
   *    - return without values
   *    - continues and breaks with or without names.
   * @param n The node to inspect.
   * @param type The Token type to look for.
   * @param labelName The name that must be associated with the exit type.
   * @nullable labelName non-null only for breaks associated with labels.
   * @return Whether the node matches the specified block-exit type.
   */
  static private boolean matchingExitNode(Node n, int type, String labelName) {
    if (n.getType() == type) {
      if (type == Token.RETURN) {
        // only returns without expressions.
        return !n.hasChildren();
      } else {
        if (labelName == null) {
          return !n.hasChildren();
        } else {
          return n.hasChildren()
            && labelName.equals(n.getFirstChild().getString());
        }
      }
    }
    return false;
  }

  /**
   * Move all the child nodes following start in srcParent to the end of
   * destParent's child list.
   * @param start The start point in the srcParent child list.
   * @param srcParent The parent node of start.
   * @param destParent The destination node.
   */
  static private void moveAllFollowing(
      Node start, Node srcParent, Node destParent) {
    for (Node n = start.getNext(); n != null; n = start.getNext()) {
      boolean isFunctionDeclaration =
          NodeUtil.isFunctionDeclaration(n);

      srcParent.removeChild(n);

      if (isFunctionDeclaration) {
        destParent.addChildToFront(n);
      } else {
        destParent.addChildToBack(n);
      }
    }
  }
}
