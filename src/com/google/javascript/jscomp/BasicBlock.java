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

/**
 * Represents a section of code that is uninterrupted by control structures (conditional or
 * iterative logic).
 */
final class BasicBlock implements Serializable {

  private final BasicBlock parent;

  private final Node root;

  /** Whether this block denotes a function scope. */
  private final boolean isFunction;

  /** Whether this block denotes a loop. */
  private final boolean isLoop;

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

    if (root.getParent() != null) {
      Token pType = root.getParent().getToken();
      this.isLoop =
          pType == Token.DO
              || pType == Token.WHILE
              || pType == Token.FOR
              || pType == Token.FOR_OF
              || pType == Token.FOR_AWAIT_OF
              || pType == Token.FOR_IN;
    } else {
      this.isLoop = false;
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
    BasicBlock currentBlock;
    for (currentBlock = thatBlock;
        currentBlock != null && currentBlock != this;
        currentBlock = currentBlock.getParent()) {}

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
