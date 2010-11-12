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

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;

/**
 * Breaks up the AST at different levels for parallel analysis and
 * optimizations. In all cases, the subtrees are detached from the original
 * source tree and are replaced by place-holders for the reverse process.
 * Although this class breaks the AST into independent subtrees and make tree
 * transformation safe, it is still up to individual passes to preserve proper
 * semantics when analyzing the subtrees.
 *
 */
class AstParallelizer {

  public static final String TEMP_NAME = "JSC_TMP_PLACE_HOLDER";

  private final Predicate<Node> shouldSplit;

  private final Supplier<Node> placeHolderProvider;

  private final List<Node> forest;

  private final Node root;

  private final boolean includeRoot;

  // Maps to place holder to the original function.
  private final List<DettachPoint> detachPointList;

  /**
   * Constructor.
   *
   * @param shouldSplit Specify at which node it should split the subtree.
   * @param shouldTraverse Specify when to stop looking for subtree to split.
   *     This is <b>very</b> important for performance as we do not want to
   *     traverse too much just looking for subtree.
   * @param placeHolderProvider Specify what type of node should be place as
   *     a temporary place holder for where the subtree is dettached.
   * @param root The AST itself.
   * @param includeRoot Should we include the root inside the forest returned
   *     by {{@link #split()}.
   */
  public AstParallelizer(
      Predicate<Node> shouldSplit,
      Predicate<Node> shouldTraverse,
      Supplier<Node> placeHolderProvider,
      Node root,
      boolean includeRoot) {
    this.shouldSplit = shouldSplit;
    this.placeHolderProvider = placeHolderProvider;
    this.root = root;
    this.includeRoot = includeRoot;
    this.forest = Lists.newLinkedList();
    this.detachPointList = Lists.newLinkedList();
  }

  public static AstParallelizer createNewFunctionLevelAstParallelizer(
      Node root, boolean globalPass) {

    // Split at function level.
    Predicate<Node> shouldSplit = new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        return NodeUtil.isFunction(input);
      }
    };

    // Always traverse until it finds a split point.
    Predicate<Node> shouldTraverse = new Predicate<Node>() {
      @Override
      public boolean apply(Node ignored) {
        return true;
      }
    };

    // Use a function declaration of the same name.
    Supplier<Node> placeHolders = new Supplier<Node>() {
      @Override
      public Node get() {
        return new Node(Token.FUNCTION,
            Node.newString(Token.NAME, TEMP_NAME),
            new Node(Token.LP), new Node(Token.BLOCK));
      }
    };
    return new AstParallelizer(
        shouldSplit, shouldTraverse, placeHolders, root, globalPass);
  }

  public static AstParallelizer createNewFileLevelAstParallelizer(Node root) {

    // Split at every node that has a file name prop.
    Predicate<Node> shouldSplit = new Predicate<Node>() {
      @Override
      public boolean apply(Node input) {
        String sourceName = (String) input.getProp(Node.SOURCENAME_PROP);
        return sourceName != null;
      }
    };

    // Use a string as place holder.
    Supplier<Node> placeHolders = new Supplier<Node>() {
      @Override
      public Node get() {
        return NodeUtil.newExpr(Node.newString(TEMP_NAME));
      }
    };

    // Only traverse blocks.
    Predicate<Node> shouldTraverse = new Predicate<Node>() {
      @Override
      public boolean apply(Node n) {
        return n.getType() == Token.BLOCK;
      }
    };
    return new AstParallelizer(
        shouldSplit, shouldTraverse, placeHolders, root, false);
  }

  /**
   * Remembers the split point for use in {@link #join()}.
   */
  private void recordSplitPoint(Node placeHolder, Node before, Node orginal) {
    detachPointList.add(new DettachPoint(placeHolder, before, orginal));
  }

  /**
   * Splits the AST into subtree at different levels. The subtrees itself are
   * usually not valid javascript but they are all subtreess of some valid
   * javascript.
   */
  public List<Node> split() {
    if (includeRoot) {
      forest.add(root);
    }
    split(root);
    return forest;
  }

  private void split(Node n) {
    Node c = n.getFirstChild();
    Node before = null;
    while (c != null) {
      Node next = c.getNext();
      if (shouldSplit.apply(c)) {
        Node placeHolder = placeHolderProvider.get();
        if (before == null) {
          forest.add(n.removeFirstChild());
          n.addChildToFront(placeHolder);
        } else {
          n.addChildAfter(placeHolder, c);
          n.removeChildAfter(before);
          forest.add(c);
        }
        recordSplitPoint(placeHolder, before, c);
        before = placeHolder;
      } else {
        split(c);
        before = c;
      }
      c = next;
    }
  }

  /**
   * Reverse the splitting done by {@link #split()}.
   */
  public void join() {
    // Revert in a reverse order to undo the detachment.
    while (!detachPointList.isEmpty()) {
      DettachPoint entry = detachPointList.remove(detachPointList.size() - 1);
      entry.reattach();
    }
  }

  /**
   * A class to map the place holder to the original subtree for re-attachment.
   * Normally a Map from Node -> Node is sufficient, however, if we also
   * remember the node before the place holder, we can avoid using
   * {@link Node#replaceChild(Node, Node)} which requires a linear search of
   * the before node. May be someday we should get a prev pointer for this
   * purpose.
   */
  private static class DettachPoint {

    // The place holder to remember where the original node was.
    private Node placeHolder;

    // The node before the place holder and the original, null if
    private Node before;

    // The root of the subtree to be temporary detached.
    private Node original;

    private DettachPoint(Node placeHolder, Node before, Node orginal) {
      this.placeHolder = placeHolder;
      this.before = before;
      this.original = orginal;
    }

    public void reattach() {
      // If the place-holder no longer has a parent, this implies the function
      // has been removed from the AST.
      if (placeHolder.getParent() != null) {
        if (before == null) {
          placeHolder.getParent().addChildrenToFront(original);
          placeHolder.getParent().removeChildAfter(original);
        } else {
          placeHolder.getParent().addChildAfter(original, before);
          placeHolder.getParent().removeChildAfter(original);
        }
      }
    }
  }
}
