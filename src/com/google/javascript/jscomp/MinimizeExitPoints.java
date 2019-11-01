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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import javax.annotation.Nullable;

/**
 * Transform the structure of the AST so that the number of explicit exits
 * are minimized and instead flows to implicit exits conditions.
 */
class MinimizeExitPoints extends AbstractPeepholeOptimization {
  @Override
  Node optimizeSubtree(Node n) {
    switch (n.getToken()) {
      case LABEL:
        tryMinimizeExits(
            n.getLastChild(), Token.BREAK, n.getFirstChild().getString());
        break;

      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case WHILE:
        tryMinimizeExits(NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);
        break;

      case DO:
        tryMinimizeExits(NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);

        Node cond = NodeUtil.getConditionExpression(n);
        if (getSideEffectFreeBooleanValue(cond) == TernaryValue.FALSE) {
          // Normally, we wouldn't be able to optimize BREAKs inside a loop
          // but as we know the condition will always be false, we can treat them
          // as we would a CONTINUE.
          tryMinimizeExits(n.getFirstChild(), Token.BREAK, null);
        }
        break;

      case BLOCK:
        if (n.getParent() != null && n.getParent().isFunction()) {
          tryMinimizeExits(n, Token.RETURN, null);
        }
        break;

      case SWITCH:
        tryMinimizeSwitchExits(n, Token.BREAK, null);
        break;

        // TODO(johnlenz): Minimize any block that ends in a optimizable statements:
        //   break, continue, return
      default:
        break;
    }
    return n;
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
   *   null otherwise. Non-null only for breaks within labels.
   */
  void tryMinimizeExits(Node n, Token exitType, @Nullable String labelName) {

    // Just an 'exit'.
    if (matchingExitNode(n, exitType, labelName)) {
      reportChangeToEnclosingScope(n);
      NodeUtil.removeChild(n.getParent(), n);
      return;
    }

    // Just an 'if'.
    if (n.isIf()) {
      Node ifBlock = n.getSecondChild();
      tryMinimizeExits(ifBlock, exitType, labelName);
      Node elseBlock = ifBlock.getNext();
      if (elseBlock != null) {
        tryMinimizeExits(elseBlock, exitType, labelName);
      }
      return;
    }

    // Just a 'try/catch/finally'.
    if (n.isTry()) {
      Node tryBlock = n.getFirstChild();
      tryMinimizeExits(tryBlock, exitType, labelName);
      Node allCatchNodes = NodeUtil.getCatchBlock(n);
      if (NodeUtil.hasCatchHandler(allCatchNodes)) {
        checkState(allCatchNodes.hasOneChild());
        Node catchNode = allCatchNodes.getFirstChild();
        Node catchCodeBlock = catchNode.getLastChild();
        tryMinimizeExits(catchCodeBlock, exitType, labelName);
      }
      /* Don't try to minimize the exits of finally blocks, as this
       * can cause problems if it changes the completion type of the finally
       * block. See ECMA 262 Sections 8.9 & 12.14
       */
    }

    // Just a 'label'.
    if (n.isLabel()) {
      Node labelBlock = n.getLastChild();
      tryMinimizeExits(labelBlock, exitType, labelName);
    }

    // We can only minimize switch cases if we are not trying to remove unlabeled breaks.
    if (n.isSwitch()  && (exitType != Token.BREAK || labelName != null)) {
      tryMinimizeSwitchExits(n, exitType, labelName);
      return;
    }

    // The rest assumes a block with at least one child, bail on anything else.
    if (!n.isBlock() || !n.hasChildren()) {
      return;
    }

    // Multiple if-exits can be converted in a single pass.
    // Convert "if (blah) break;  if (blah2) break; other_stmt;" to
    // become "if (blah); else { if (blah2); else { other_stmt; } }"
    // which will get converted to "if (!blah && !blah2) { other_stmt; }".
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      // An 'if' block to process below.
      if (c.isIf()) {
        Node ifTree = c;

        // First, the true condition block.
        Node trueBlock = ifTree.getSecondChild();
        Node falseBlock = trueBlock.getNext();
        tryMinimizeIfBlockExits(trueBlock, falseBlock,
            ifTree, exitType, labelName);

        // Now the else block.
        // The if blocks may have changed, get them again.
        trueBlock = ifTree.getSecondChild();
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

  void tryMinimizeSwitchExits(Node n, Token exitType, @Nullable String labelName) {
    checkState(n.isSwitch());
    // Skipping the switch condition, visit all the children.
    for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
      if (c != n.getLastChild()) {
        tryMinimizeSwitchCaseExits(c, exitType, labelName);
      } else {
        // Last case, the last case block can be optimized more aggressively.
        tryMinimizeExits(c.getLastChild(), exitType, labelName);
      }
    }
  }

  /**
   * Attempt to remove explicit exits from switch cases that also occur implicitly
   * after the switch.
   */
  void tryMinimizeSwitchCaseExits(Node n, Token exitType, @Nullable String labelName) {
    checkState(NodeUtil.isSwitchCase(n));

    checkState(n != n.getParent().getLastChild());
    Node block = n.getLastChild();
    Node maybeBreak = block.getLastChild();
    if (maybeBreak == null || !maybeBreak.isBreak() || maybeBreak.hasChildren()) {
      // Can not minimize exits from a case without an explicit break from the switch.
      return;
    }

    // Now try to minimize the exits of the last child before the break, if it is removed
    // look at what has become the child before the break.
    Node childBeforeBreak = maybeBreak.getPrevious();
    while (childBeforeBreak != null) {
      Node c = childBeforeBreak;
      tryMinimizeExits(c, exitType, labelName);
      // If the node is still the last child, we are done.
      childBeforeBreak = maybeBreak.getPrevious();
      if (c == childBeforeBreak) {
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
   * @param labelName The name associated with the exit, if any. null for anything excepted for
   *     named-break associated with a label.
   */
  private void tryMinimizeIfBlockExits(Node srcBlock, Node destBlock,
      Node ifNode, Token exitType, @Nullable String labelName) {
    Node exitNodeParent = null;
    Node exitNode = null;

    // Pick an exit node candidate.
    if (srcBlock.isBlock()) {
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

    // Ensure no block-scoped declarations are moved into an inner block.
    if (!tryConvertAllBlockScopedFollowing(ifNode)) {
      return;
    }

    // Take case of the if nodes siblings, if any.
    if (ifNode.getNext() != null) {
      // Move siblings of the if block into the opposite
      // logic block of the exit.
      Node newDestBlock = IR.block().srcref(ifNode);
      if (destBlock == null) {
        // Only possible if this is the false block.
        ifNode.addChildToBack(newDestBlock);
      } else if (destBlock.isEmpty()) {
        // Use the new block.
        ifNode.replaceChild(destBlock, newDestBlock);
      } else if (destBlock.isBlock()) {
        // Reuse the existing block.
        newDestBlock = destBlock;
      } else {
        // Add the existing statement to the new block.
        ifNode.replaceChild(destBlock, newDestBlock);
        newDestBlock.addChildToBack(destBlock);
      }

      // Move all the if node's following siblings.
      moveAllFollowing(ifNode, ifNode.getParent(), newDestBlock);
      reportChangeToEnclosingScope(ifNode);
    }
  }

  /**
   * Determines if n matches the type and name for the following types of
   * "exits":
   *    - return without values
   *    - continues and breaks with or without names.
   * @param n The node to inspect.
   * @param type The Token type to look for.
   * @param labelName The name that must be associated with the exit type.
   *     non-null only for breaks associated with labels.
   * @return Whether the node matches the specified block-exit type.
   */
  private static boolean matchingExitNode(Node n, Token type, @Nullable String labelName) {
    if (n.getToken() == type) {
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
  private static void moveAllFollowing(
      Node start, Node srcParent, Node destParent) {
    for (Node n = start.getNext(); n != null; n = start.getNext()) {
      boolean isFunctionDeclaration = NodeUtil.isFunctionDeclaration(n);
      srcParent.removeChild(n);
      if (isFunctionDeclaration) {
        destParent.addChildToFront(n);
      } else {
        destParent.addChildToBack(n);
      }
    }
  }

  /**
   * Convert all let/const declarations following the start node to var declarations if possible.
   *
   * <p>See the unit tests for examples of why this is necessary before moving code into an inner
   * block, and why this is unsafe to do to declarations inside a loop.
   *
   * @param start The start point
   * @return Whether all block-scoped declarations have been converted.
   */
  private static boolean tryConvertAllBlockScopedFollowing(Node start) {
    if (NodeUtil.isWithinLoop(start)) {
      // If in a loop, don't convert anything to a var. Return true only if there are no let/consts.
      return !hasBlockScopedVarsFollowing(start);
    }
    for (Node n = start.getNext(); n != null; n = n.getNext()) {
      if (n.isLet() || n.isConst()) {
        n.setToken(Token.VAR);
      }
    }
    return true;
  }

  /**
   * Detect any block-scoped declarations that are younger siblings of the given starting point.
   *
   * @param start The start point
   */
  private static boolean hasBlockScopedVarsFollowing(Node start) {
    for (Node n = start.getNext(); n != null; n = n.getNext()) {
      if (n.isLet() || n.isConst()) {
        return true;
      }
    }
    return false;
  }
}
