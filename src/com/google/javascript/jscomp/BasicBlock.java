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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.Serializable;
import org.jspecify.annotations.Nullable;

/**
 * Represents a section of code that is uninterrupted by control structures (conditional or
 * iterative logic).
 */
final class BasicBlock implements Serializable {

  /** Returns true if {@code n} marks the start of a new basic block under {@code parent}. */
  @SuppressWarnings("ReferenceEquality")
  static boolean isBlockBoundary(Node n, @Nullable Node parent) {
    if (parent != null) {
      switch (parent.getToken()) {
        case DO, FOR, FOR_IN, FOR_OF, FOR_AWAIT_OF, TRY, WHILE, WITH, SWITCH_BODY -> {
          // NOTE: TRY has up to 3 child blocks:
          // TRY
          //   BLOCK
          //   BLOCK
          //     CATCH
          //   BLOCK
          // Note that there is an explicit CATCH token but no explicit
          // FINALLY token. For simplicity, we consider each BLOCK
          // a separate basic BLOCK.
          return true;
        }
        case CLASS -> {
          // The name and `extends` clause are evaluated unconditionally, but every child of a
          // class is still treated as a separate block. Passes like InlineVariables only inline
          // within a single basic block, and rely on this to avoid inlining declarations into an
          // `extends` clause (e.g. undoing Es6RewriteClassExtendsExpressions).
          return true;
        }
        case IF, LABEL -> {
          // The if condition or label name is not a boundary, but the corresponding block is.
          return n != parent.getFirstChild();
        }
        case CASE -> {
          // The case body is a boundary: it can be entered by fallthrough from an earlier case
          // without evaluating this case's test expression. See provablyExecutesBefore.
          return n != parent.getFirstChild();
        }
        default -> {}
      }
      if (EvaluationOrder.isConditionalChild(n)) {
        // e.g. the right-hand side of `&&`, either arm of `?:`, or a destructuring default value.
        return true;
      }
    }

    return n.isCase();
  }

  private final BasicBlock parent;

  private final Node root;

  /** Whether this block denotes a function scope. */
  private final boolean isFunction;

  /** Whether this block denotes a loop. */
  private final boolean isLoop;

  /**
   * Whether this block is the body of a {@code CASE}, which may be entered by fallthrough without
   * executing its parent block (the {@code CASE} test).
   */
  private final boolean isCaseBody;

  /**
   * Creates a new block.
   *
   * @param parent The containing block.
   * @param root The root node of the block.
   */
  BasicBlock(BasicBlock parent, Node root) {
    this.parent = parent;
    this.root = root;

    this.isFunction = root.isFunction();

    if (root.hasParent()) {
      Token pType = root.getParent().getToken();
      this.isLoop =
          pType == Token.DO
              || pType == Token.WHILE
              || pType == Token.FOR
              || pType == Token.FOR_OF
              || pType == Token.FOR_AWAIT_OF
              || pType == Token.FOR_IN;
      this.isCaseBody = pType == Token.CASE && root.isBlock();
    } else {
      this.isLoop = false;
      this.isCaseBody = false;
    }
  }

  BasicBlock getParent() {
    return parent;
  }

  /**
   * Determines whether this block is equivalent to the very first block that is created when
   * reference collection traversal enters global scope. Note that when traversing a single script
   * in a hot-swap fashion a new instance of {@code BasicBlock} is created.
   *
   * @return true if this is global scope block.
   */
  boolean isGlobalScopeBlock() {
    return getParent() == null;
  }

  /** Determines whether this block is guaranteed to begin executing before the given block does. */
  boolean provablyExecutesBefore(BasicBlock thatBlock) {
    // If thatBlock is a descendant of this block, and there are no hoisted
    // blocks between them, then this block must start before thatBlock.
    BasicBlock currentBlock = thatBlock;
    while (currentBlock != null && currentBlock != this) {
      BasicBlock parentBlock = currentBlock.getParent();
      if (currentBlock.isCaseBody && parentBlock == this) {
        // In `case init: body`, the body can be reached by fallthrough from an earlier case
        // without evaluating the `init` test expression.
        return false;
      }
      currentBlock = parentBlock;
    }

    if (currentBlock == this) {
      return true;
    }
    return isGlobalScopeBlock() && thatBlock.isGlobalScopeBlock();
  }

  boolean isFunction() {
    return isFunction;
  }

  boolean isLoop() {
    return isLoop;
  }

  Node getRoot() {
    return root;
  }

  @Override
  public String toString() {
    return "BasicBlock @ " + root;
  }
}
