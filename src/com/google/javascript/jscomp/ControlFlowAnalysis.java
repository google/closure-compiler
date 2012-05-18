/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * This is a compiler pass that computes a control flow graph.
 *
 */
final class ControlFlowAnalysis implements Callback, CompilerPass {

  /**
   * Based roughly on the first few pages of
   *
   * "Declarative Intraprocedural Flow Analysis of Java Source Code by
   * Nilsson-Nyman, Hedin, Magnusson & Ekman",
   *
   * this pass computes the control flow graph from the AST. However, a full
   * attribute grammar is not necessary. We will compute the flow edges with a
   * single post order traversal. The "follow()" of a given node will be
   * computed recursively in a demand driven fashion.
   *
   * As of this moment, we are not performing any inter-procedural analysis
   * within our framework.
   */

  private final AbstractCompiler compiler;

  private ControlFlowGraph<Node> cfg;

  private Map<Node, Integer> astPosition;

  // TODO(nicksantos): should these be node annotations?
  private Map<DiGraphNode<Node, Branch>, Integer> nodePriorities;

  // We order CFG nodes by by looking at the AST positions.
  // CFG nodes that come first lexically should be visited first, because
  // they will often be executed first in the source program.
  private final Comparator<DiGraphNode<Node, Branch>> priorityComparator =
      new Comparator<DiGraphNode<Node, Branch>>() {
    @Override
    public int compare(
        DiGraphNode<Node, Branch> a, DiGraphNode<Node, Branch> b) {
      return astPosition.get(a.getValue()) - astPosition.get(b.getValue());
    }
  };

  private int astPositionCounter;
  private int priorityCounter;

  private final boolean shouldTraverseFunctions;
  private final boolean edgeAnnotations;

  // We need to store where we started, in case we aren't doing a flow analysis
  // for the whole scope. This happens, for example, when running type inference
  // on only the externs.
  private Node root;

  /*
   * This stack captures the structure of nested TRY blocks. The top of the
   * stack is the inner most TRY block. A FUNCTION node in this stack implies
   * that the handler is determined by the caller of the function at runtime.
   */
  private final Deque<Node> exceptionHandler = new ArrayDeque<Node>();

  /*
   * This map is used to handle the follow of FINALLY. For example:
   *
   * while(x) {
   *  try {
   *    try {
   *      break;
   *    } catch (a) {
   *    } finally {
   *      foo();
   *    }
   *    fooFollow();
   *  } catch (b) {
   *  } finally {
   *    bar();
   *  }
   *  barFollow();
   * }
   * END();
   *
   * In this case finallyMap will contain a map from:
   *    first FINALLY -> bar()
   *    second FINALLY -> END()
   *
   * When we are connecting foo() and bar() to to their respective follow, we
   * must also look up this map and connect:
   *   foo() -> bar()
   *   bar() -> END
   */
  private final Multimap<Node, Node> finallyMap = HashMultimap.create();

  /**
   * Constructor.
   *
   * @param compiler Compiler instance.
   * @param shouldTraverseFunctions Whether functions should be traversed (true
   *    by default).
   * @param edgeAnnotations Whether to allow edge annotations. By default,
   *    only node annotations are allowed.
   */
  ControlFlowAnalysis(AbstractCompiler compiler,
      boolean shouldTraverseFunctions, boolean edgeAnnotations) {
    this.compiler = compiler;
    this.shouldTraverseFunctions = shouldTraverseFunctions;
    this.edgeAnnotations = edgeAnnotations;
  }

  ControlFlowGraph<Node> getCfg() {
    return cfg;
  }

  @Override
  public void process(Node externs, Node root) {
    this.root = root;
    astPositionCounter = 0;
    astPosition = Maps.newHashMap();
    nodePriorities = Maps.newHashMap();
    cfg = new AstControlFlowGraph(computeFallThrough(root), nodePriorities,
                                  edgeAnnotations);
    NodeTraversal.traverse(compiler, root, this);
    astPosition.put(null, ++astPositionCounter); // the implicit return is last.

    // Now, generate the priority of nodes by doing a depth-first
    // search on the CFG.
    priorityCounter = 0;
    DiGraphNode<Node, Branch> entry = cfg.getEntry();
    prioritizeFromEntryNode(entry);

    if (shouldTraverseFunctions) {
      // If we're traversing inner functions, we need to rank the
      // priority of them too.
      for (DiGraphNode<Node, Branch> candidate : cfg.getDirectedGraphNodes()) {
        Node value = candidate.getValue();
        if (value != null && value.isFunction()) {
          Preconditions.checkState(
              !nodePriorities.containsKey(candidate) || candidate == entry);
          prioritizeFromEntryNode(candidate);
        }
      }
    }

    // At this point, all reachable nodes have been given a priority, but
    // unreachable nodes have not been given a priority. Put them last.
    // Presumably, it doesn't really matter what priority they get, since
    // this shouldn't happen in real code.
    for (DiGraphNode<Node, Branch> candidate : cfg.getDirectedGraphNodes()) {
      if (!nodePriorities.containsKey(candidate)) {
        nodePriorities.put(candidate, ++priorityCounter);
      }
    }

    // Again, the implicit return node is always last.
    nodePriorities.put(cfg.getImplicitReturn(), ++priorityCounter);
  }

  /**
   * Given an entry node, find all the nodes reachable from that node
   * and prioritize them.
   */
  private void prioritizeFromEntryNode(DiGraphNode<Node, Branch> entry) {
    PriorityQueue<DiGraphNode<Node, Branch>> worklist =
        new PriorityQueue<DiGraphNode<Node, Branch>>(10, priorityComparator);
    worklist.add(entry);

    while (!worklist.isEmpty()) {
      DiGraphNode<Node, Branch> current = worklist.remove();
      if (nodePriorities.containsKey(current)) {
        continue;
      }

      nodePriorities.put(current, ++priorityCounter);

      List<DiGraphNode<Node, Branch>> successors =
          cfg.getDirectedSuccNodes(current);
      for (DiGraphNode<Node, Branch> candidate : successors) {
        worklist.add(candidate);
      }
    }
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal nodeTraversal, Node n, Node parent) {
    astPosition.put(n, astPositionCounter++);

    switch (n.getType()) {
      case Token.FUNCTION:
        if (shouldTraverseFunctions || n == cfg.getEntry().getValue()) {
          exceptionHandler.push(n);
          return true;
        }
        return false;
      case Token.TRY:
        exceptionHandler.push(n);
        return true;
    }

    /*
     * We are going to stop the traversal depending on what the node's parent
     * is.
     *
     * We are only interested in adding edges between nodes that change control
     * flow. The most obvious ones are loops and IF-ELSE's. A statement
     * transfers control to its next sibling.
     *
     * In case of an expression tree, there is no control flow within the tree
     * even when there are short circuited operators and conditionals. When we
     * are doing data flow analysis, we will simply synthesize lattices up the
     * expression tree by finding the meet at each expression node.
     *
     * For example: within a Token.SWITCH, the expression in question does not
     * change the control flow and need not to be considered.
     */
    if (parent != null) {
      switch (parent.getType()) {
        case Token.FOR:
          // Only traverse the body of the for loop.
          return n == parent.getLastChild();

        // Skip the conditions.
        case Token.IF:
        case Token.WHILE:
        case Token.WITH:
          return n != parent.getFirstChild();
        case Token.DO:
          return n != parent.getFirstChild().getNext();
        // Only traverse the body of the cases
        case Token.SWITCH:
        case Token.CASE:
        case Token.CATCH:
        case Token.LABEL:
          return n != parent.getFirstChild();
        case Token.FUNCTION:
          return n == parent.getFirstChild().getNext().getNext();
        case Token.CONTINUE:
        case Token.BREAK:
        case Token.EXPR_RESULT:
        case Token.VAR:
        case Token.RETURN:
        case Token.THROW:
          return false;
        case Token.TRY:
          /* Just before we are about to visit the second child of the TRY node,
           * we know that we will be visiting either the CATCH or the FINALLY.
           * In other words, we know that the post order traversal of the TRY
           * block has been finished, no more exceptions can be caught by the
           * handler at this TRY block and should be taken out of the stack.
           */
          if (n == parent.getFirstChild().getNext()) {
            Preconditions.checkState(exceptionHandler.peek() == parent);
            exceptionHandler.pop();
          }
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.IF:
        handleIf(n);
        return;
      case Token.WHILE:
        handleWhile(n);
        return;
      case Token.DO:
        handleDo(n);
        return;
      case Token.FOR:
        handleFor(n);
        return;
      case Token.SWITCH:
        handleSwitch(n);
        return;
      case Token.CASE:
        handleCase(n);
        return;
      case Token.DEFAULT_CASE:
        handleDefault(n);
        return;
      case Token.BLOCK:
      case Token.SCRIPT:
        handleStmtList(n);
        return;
      case Token.FUNCTION:
        handleFunction(n);
        return;
      case Token.EXPR_RESULT:
        handleExpr(n);
        return;
      case Token.THROW:
        handleThrow(n);
        return;
      case Token.TRY:
        handleTry(n);
        return;
      case Token.CATCH:
        handleCatch(n);
        return;
      case Token.BREAK:
        handleBreak(n);
        return;
      case Token.CONTINUE:
        handleContinue(n);
        return;
      case Token.RETURN:
        handleReturn(n);
        return;
      case Token.WITH:
        handleWith(n);
        return;
      case Token.LABEL:
        return;
      default:
        handleStmt(n);
        return;
    }
  }

  private void handleIf(Node node) {
    Node thenBlock = node.getFirstChild().getNext();
    Node elseBlock = thenBlock.getNext();
    createEdge(node, Branch.ON_TRUE, computeFallThrough(thenBlock));

    if (elseBlock == null) {
      createEdge(node, Branch.ON_FALSE,
          computeFollowNode(node, this)); // not taken branch
    } else {
      createEdge(node, Branch.ON_FALSE, computeFallThrough(elseBlock));
    }
    connectToPossibleExceptionHandler(
        node, NodeUtil.getConditionExpression(node));
  }

  private void handleWhile(Node node) {
    // Control goes to the first statement if the condition evaluates to true.
    createEdge(node, Branch.ON_TRUE,
        computeFallThrough(node.getFirstChild().getNext()));

    // Control goes to the follow() if the condition evaluates to false.
    createEdge(node, Branch.ON_FALSE,
        computeFollowNode(node, this));
    connectToPossibleExceptionHandler(
        node, NodeUtil.getConditionExpression(node));
  }

  private void handleDo(Node node) {
    // The first edge can be the initial iteration as well as the iterations
    // after.
    createEdge(node, Branch.ON_TRUE, computeFallThrough(node.getFirstChild()));
    // The edge that leaves the do loop if the condition fails.
    createEdge(node, Branch.ON_FALSE,
        computeFollowNode(node, this));
    connectToPossibleExceptionHandler(
        node, NodeUtil.getConditionExpression(node));
  }

  private void handleFor(Node forNode) {
    if (forNode.getChildCount() == 4) {
      // We have for (init; cond; iter) { body }
      Node init = forNode.getFirstChild();
      Node cond = init.getNext();
      Node iter = cond.getNext();
      Node body = iter.getNext();
      // After initialization, we transfer to the FOR which is in charge of
      // checking the condition (for the first time).
      createEdge(init, Branch.UNCOND, forNode);
      // The edge that transfer control to the beginning of the loop body.
      createEdge(forNode, Branch.ON_TRUE, computeFallThrough(body));
      // The edge to end of the loop.
      createEdge(forNode, Branch.ON_FALSE,
          computeFollowNode(forNode, this));
      // The end of the body will have a unconditional branch to our iter
      // (handled by calling computeFollowNode of the last instruction of the
      // body. Our iter will jump to the forNode again to another condition
      // check.
      createEdge(iter, Branch.UNCOND, forNode);
      connectToPossibleExceptionHandler(init, init);
      connectToPossibleExceptionHandler(forNode, cond);
      connectToPossibleExceptionHandler(iter, iter);
    } else {
      // We have for (item in collection) { body }
      Node item = forNode.getFirstChild();
      Node collection = item.getNext();
      Node body = collection.getNext();
      // The collection behaves like init.
      createEdge(collection, Branch.UNCOND, forNode);
      // The edge that transfer control to the beginning of the loop body.
      createEdge(forNode, Branch.ON_TRUE, computeFallThrough(body));
      // The edge to end of the loop.
      createEdge(forNode, Branch.ON_FALSE,
          computeFollowNode(forNode, this));
      connectToPossibleExceptionHandler(forNode, collection);
    }
  }

  private void handleSwitch(Node node) {
    // Transfer to the first non-DEFAULT CASE. if there are none, transfer
    // to the DEFAULT or the EMPTY node.
    Node next = getNextSiblingOfType(
        node.getFirstChild().getNext(), Token.CASE, Token.EMPTY);
    if (next != null) { // Has at least one CASE or EMPTY
      createEdge(node, Branch.UNCOND, next);
    } else { // Has no CASE but possibly a DEFAULT
      if (node.getFirstChild().getNext() != null) {
        createEdge(node, Branch.UNCOND, node.getFirstChild().getNext());
      } else { // No CASE, no DEFAULT
        createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
      }
    }
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }

  private void handleCase(Node node) {
    // Case is a bit tricky....First it goes into the body if condition is true.
    createEdge(node, Branch.ON_TRUE,
        node.getFirstChild().getNext());
    // Look for the next CASE, skipping over DEFAULT.
    Node next = getNextSiblingOfType(node.getNext(), Token.CASE);
    if (next != null) { // Found a CASE
      Preconditions.checkState(next.isCase());
      createEdge(node, Branch.ON_FALSE, next);
    } else { // No more CASE found, go back and search for a DEFAULT.
      Node parent = node.getParent();
      Node deflt = getNextSiblingOfType(
        parent.getFirstChild().getNext(), Token.DEFAULT_CASE);
      if (deflt != null) { // Has a DEFAULT
        createEdge(node, Branch.ON_FALSE, deflt);
      } else { // No DEFAULT found, go to the follow of the SWITCH.
        createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
      }
    }
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }

  private void handleDefault(Node node) {
    // Directly goes to the body. It should not transfer to the next case.
    createEdge(node, Branch.UNCOND, node.getFirstChild());
  }

  private void handleWith(Node node) {
    // Directly goes to the body. It should not transfer to the next case.
    createEdge(node, Branch.UNCOND, node.getLastChild());
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }

  private void handleStmtList(Node node) {
    Node parent = node.getParent();
    // Special case, don't add a block of empty CATCH block to the graph.
    if (node.isBlock() && parent != null &&
        parent.isTry() &&
        NodeUtil.getCatchBlock(parent) == node &&
        !NodeUtil.hasCatchHandler(node)) {
      return;
    }

    // A block transfer control to its first child if it is not empty.
    Node child = node.getFirstChild();

    // Function declarations are skipped since control doesn't go into that
    // function (unless it is called)
    while (child != null && child.isFunction()) {
      child = child.getNext();
    }

    if (child != null) {
      createEdge(node, Branch.UNCOND, computeFallThrough(child));
    } else {
      createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    }

    // Synthetic blocks
    if (parent != null) {
      switch (parent.getType()) {
        case Token.DEFAULT_CASE:
        case Token.CASE:
        case Token.TRY:
          break;
        default:
          if (node.isBlock() && node.isSyntheticBlock()) {
            createEdge(node, Branch.SYN_BLOCK, computeFollowNode(node, this));
          }
          break;
      }
    }
  }

  private void handleFunction(Node node) {
    // A block transfer control to its first child if it is not empty.
    Preconditions.checkState(node.getChildCount() >= 3);
    createEdge(node, Branch.UNCOND,
        computeFallThrough(node.getFirstChild().getNext().getNext()));
    Preconditions.checkState(exceptionHandler.peek() == node);
    exceptionHandler.pop();
  }

  private void handleExpr(Node node) {
    createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, node);
  }

  private void handleThrow(Node node) {
    connectToPossibleExceptionHandler(node, node);
  }

  private void handleTry(Node node) {
    createEdge(node, Branch.UNCOND, node.getFirstChild());
  }

  private void handleCatch(Node node) {
    createEdge(node, Branch.UNCOND, node.getLastChild());
  }

  private void handleBreak(Node node) {
    String label = null;
    // See if it is a break with label.
    if (node.hasChildren()) {
      label = node.getFirstChild().getString();
    }
    Node cur;
    Node previous = null;
    Node lastJump;
    Node parent = node.getParent();
    /*
     * Continuously look up the ancestor tree for the BREAK target or the target
     * with the corresponding label and connect to it. If along the path we
     * discover a FINALLY, we will connect the BREAK to that FINALLY. From then
     * on, we will just record the control flow changes in the finallyMap. This
     * is due to the fact that we need to connect any node that leaves its own
     * FINALLY block to the outer FINALLY or the BREAK's target but those nodes
     * are not known yet due to the way we traverse the nodes.
     */
    for (cur = node, lastJump = node;
        !isBreakTarget(cur, label);
        cur = parent, parent = parent.getParent()) {
      if (cur.isTry() && NodeUtil.hasFinally(cur)
          && cur.getLastChild() != previous) {
        if (lastJump == node) {
          createEdge(lastJump, Branch.UNCOND, computeFallThrough(
              cur.getLastChild()));
        } else {
          finallyMap.put(lastJump, computeFallThrough(cur.getLastChild()));
        }
        lastJump = cur;
      }
      if (parent == null) {
        if (compiler.isIdeMode()) {
          // In IDE mode, we expect that the data flow graph may
          // not be well-formed.
          return;
        } else {
          throw new IllegalStateException("Cannot find break target.");
        }
      }
      previous = cur;
    }
    if (lastJump == node) {
      createEdge(lastJump, Branch.UNCOND, computeFollowNode(cur, this));
    } else {
      finallyMap.put(lastJump, computeFollowNode(cur, this));
    }
  }

  private void handleContinue(Node node) {
    String label = null;
    if (node.hasChildren()) {
      label = node.getFirstChild().getString();
    }
    Node cur;
    Node previous = null;
    Node lastJump;

    // Similar to handBreak's logic with a few minor variation.
    Node parent = node.getParent();
    for (cur = node, lastJump = node;
        !isContinueTarget(cur, parent, label);
        cur = parent, parent = parent.getParent()) {
      if (cur.isTry() && NodeUtil.hasFinally(cur)
          && cur.getLastChild() != previous) {
        if (lastJump == node) {
          createEdge(lastJump, Branch.UNCOND, cur.getLastChild());
        } else {
          finallyMap.put(lastJump, computeFallThrough(cur.getLastChild()));
        }
        lastJump = cur;
      }
      Preconditions.checkState(parent != null, "Cannot find continue target.");
      previous = cur;
    }
    Node iter = cur;
    if (cur.getChildCount() == 4) {
      iter = cur.getFirstChild().getNext().getNext();
    }

    if (lastJump == node) {
      createEdge(node, Branch.UNCOND, iter);
    } else {
      finallyMap.put(lastJump, iter);
    }
  }

  private void handleReturn(Node node) {
    Node lastJump = null;
    for (Iterator<Node> iter = exceptionHandler.iterator(); iter.hasNext();) {
      Node curHandler = iter.next();
      if (curHandler.isFunction()) {
        break;
      }
      if (NodeUtil.hasFinally(curHandler)) {
        if (lastJump == null) {
          createEdge(node, Branch.UNCOND, curHandler.getLastChild());
        } else {
          finallyMap.put(lastJump,
              computeFallThrough(curHandler.getLastChild()));
        }
        lastJump = curHandler;
      }
    }

    if (node.hasChildren()) {
      connectToPossibleExceptionHandler(node, node.getFirstChild());
    }

    if (lastJump == null) {
      createEdge(node, Branch.UNCOND, null);
    } else {
      finallyMap.put(lastJump, null);
    }
  }

  private void handleStmt(Node node) {
    // Simply transfer to the next line.
    createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, node);
  }

  static Node computeFollowNode(Node node, ControlFlowAnalysis cfa) {
    return computeFollowNode(node, node, cfa);
  }

  static Node computeFollowNode(Node node) {
    return computeFollowNode(node, node, null);
  }

  /**
   * Computes the follow() node of a given node and its parent. There is a side
   * effect when calling this function. If this function computed an edge that
   * exists a FINALLY, it'll attempt to connect the fromNode to the outer
   * FINALLY according to the finallyMap.
   *
   * @param fromNode The original source node since {@code node} is changed
   *        during recursion.
   * @param node The node that follow() should compute.
   */
  private static Node computeFollowNode(
      Node fromNode, Node node, ControlFlowAnalysis cfa) {
    /*
     * This is the case where:
     *
     * 1. Parent is null implies that we are transferring control to the end of
     * the script.
     *
     * 2. Parent is a function implies that we are transferring control back to
     * the caller of the function.
     *
     * 3. If the node is a return statement, we should also transfer control
     * back to the caller of the function.
     *
     * 4. If the node is root then we have reached the end of what we have been
     * asked to traverse.
     *
     * In all cases we should transfer control to a "symbolic return" node.
     * This will make life easier for DFAs.
     */
    Node parent = node.getParent();
    if (parent == null || parent.isFunction() ||
        (cfa != null && node == cfa.root)) {
      return null;
    }

    // If we are just before a IF/WHILE/DO/FOR:
    switch (parent.getType()) {
      // The follow() of any of the path from IF would be what follows IF.
      case Token.IF:
        return computeFollowNode(fromNode, parent, cfa);
      case Token.CASE:
      case Token.DEFAULT_CASE:
        // After the body of a CASE, the control goes to the body of the next
        // case, without having to go to the case condition.
        if (parent.getNext() != null) {
          if (parent.getNext().isCase()) {
            return parent.getNext().getFirstChild().getNext();
          } else if (parent.getNext().isDefaultCase()) {
            return parent.getNext().getFirstChild();
          } else {
            Preconditions.checkState(false, "Not reachable");
          }
        } else {
          return computeFollowNode(fromNode, parent, cfa);
        }
        break;
      case Token.FOR:
        if (NodeUtil.isForIn(parent)) {
          return parent;
        } else {
          return parent.getFirstChild().getNext().getNext();
        }
      case Token.WHILE:
      case Token.DO:
        return parent;
      case Token.TRY:
        // If we are coming out of the TRY block...
        if (parent.getFirstChild() == node) {
          if (NodeUtil.hasFinally(parent)) { // and have FINALLY block.
            return computeFallThrough(parent.getLastChild());
          } else { // and have no FINALLY.
            return computeFollowNode(fromNode, parent, cfa);
          }
        // CATCH block.
        } else if (NodeUtil.getCatchBlock(parent) == node){
          if (NodeUtil.hasFinally(parent)) { // and have FINALLY block.
            return computeFallThrough(node.getNext());
          } else {
            return computeFollowNode(fromNode, parent, cfa);
          }
        // If we are coming out of the FINALLY block...
        } else if (parent.getLastChild() == node){
          if (cfa != null) {
            for (Node finallyNode : cfa.finallyMap.get(parent)) {
              cfa.createEdge(fromNode, Branch.UNCOND, finallyNode);
            }
          }
          return computeFollowNode(fromNode, parent, cfa);
        }
    }

    // Now that we are done with the special cases follow should be its
    // immediate sibling, unless its sibling is a function
    Node nextSibling = node.getNext();

    // Skip function declarations because control doesn't get pass into it.
    while (nextSibling != null && nextSibling.isFunction()) {
      nextSibling = nextSibling.getNext();
    }

    if (nextSibling != null) {
      return computeFallThrough(nextSibling);
    } else {
      // If there are no more siblings, control is transferred up the AST.
      return computeFollowNode(fromNode, parent, cfa);
    }
  }

  /**
   * Computes the destination node of n when we want to fallthrough into the
   * subtree of n. We don't always create a CFG edge into n itself because of
   * DOs and FORs.
   */
  static Node computeFallThrough(Node n) {
    switch (n.getType()) {
      case Token.DO:
        return computeFallThrough(n.getFirstChild());
      case Token.FOR:
        if (NodeUtil.isForIn(n)) {
          return n.getFirstChild().getNext();
        }
        return computeFallThrough(n.getFirstChild());
      case Token.LABEL:
        return computeFallThrough(n.getLastChild());
      default:
        return n;
    }
  }

  /**
   * Connects the two nodes in the control flow graph.
   *
   * @param fromNode Source.
   * @param toNode Destination.
   */
  private void createEdge(Node fromNode, ControlFlowGraph.Branch branch,
      Node toNode) {
    cfg.createNode(fromNode);
    cfg.createNode(toNode);
    cfg.connectIfNotFound(fromNode, branch, toNode);
  }

  /**
   * Connects cfgNode to the proper CATCH block if target subtree might throw
   * an exception. If there are FINALLY blocks reached before a CATCH, it will
   * make the corresponding entry in finallyMap.
   */
  private void connectToPossibleExceptionHandler(Node cfgNode, Node target) {
    if (mayThrowException(target) && !exceptionHandler.isEmpty()) {
      Node lastJump = cfgNode;
      for (Node handler : exceptionHandler) {
        if (handler.isFunction()) {
          return;
        }
        Preconditions.checkState(handler.isTry());
        Node catchBlock = NodeUtil.getCatchBlock(handler);

        if (!NodeUtil.hasCatchHandler(catchBlock)) { // No catch but a FINALLY.
          if (lastJump == cfgNode) {
            createEdge(cfgNode, Branch.ON_EX, handler.getLastChild());
          } else {
            finallyMap.put(lastJump, handler.getLastChild());
          }
        } else { // Has a catch.
          if (lastJump == cfgNode) {
            createEdge(cfgNode, Branch.ON_EX, catchBlock);
            return;
          } else {
            finallyMap.put(lastJump, catchBlock);
          }
        }
        lastJump = handler;
      }
    }
  }

  /**
   * Get the next sibling (including itself) of one of the given types.
   */
  private static Node getNextSiblingOfType(Node first, int ... types) {
    for (Node c = first; c != null; c = c.getNext()) {
      for (int type : types) {
        if (c.getType() == type) {
          return c;
        }
      }
    }
    return null;
  }

  /**
   * Checks if target is actually the break target of labeled continue. The
   * label can be null if it is an unlabeled break.
   */
  public static boolean isBreakTarget(Node target, String label) {
    return isBreakStructure(target, label != null) &&
      matchLabel(target.getParent(), label);
  }

  /**
   * Checks if target is actually the continue target of labeled continue. The
   * label can be null if it is an unlabeled continue.
   */
  private static boolean isContinueTarget(
      Node target, Node parent, String label) {
    return isContinueStructure(target) && matchLabel(parent, label);
  }
  /**
   * Check if label is actually referencing the target control structure. If
   * label is null, it always returns true.
   */
  private static boolean matchLabel(Node target, String label) {
    if (label == null) {
      return true;
    }
    while (target.isLabel()) {
      if (target.getFirstChild().getString().equals(label)) {
        return true;
      }
      target = target.getParent();
    }
    return false;
  }

  /**
   * Determines if the subtree might throw an exception.
   */
  public static boolean mayThrowException(Node n) {
    switch (n.getType()) {
      case Token.CALL:
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.THROW:
      case Token.NEW:
      case Token.ASSIGN:
      case Token.INC:
      case Token.DEC:
      case Token.INSTANCEOF:
        return true;
      case Token.FUNCTION:
        return false;
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (!ControlFlowGraph.isEnteringNewCfgNode(c) && mayThrowException(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines whether the given node can be terminated with a BREAK node.
   */
  static boolean isBreakStructure(Node n, boolean labeled) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      case Token.SWITCH:
        return true;
      case Token.BLOCK:
      case Token.IF:
      case Token.TRY:
        return labeled;
      default:
        return false;
    }
  }

  /**
   * Determines whether the given node can be advanced with a CONTINUE node.
   */
  static boolean isContinueStructure(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
        return true;
      default:
        return false;
    }
  }

  /**
   * Get the TRY block with a CATCH that would be run if n throws an exception.
   * @return The CATCH node or null if it there isn't a CATCH before the
   *     the function terminates.
   */
  static Node getExceptionHandler(Node n) {
    for (Node cur = n;
        !cur.isScript() && !cur.isFunction();
        cur = cur.getParent()) {
      Node catchNode = getCatchHandlerForBlock(cur);
      if (catchNode != null) {
        return catchNode;
      }
    }
    return null;
  }

  /**
   * Locate the catch BLOCK given the first block in a TRY.
   * @return The CATCH node or null there is no catch handler.
   */
  static Node getCatchHandlerForBlock(Node block) {
    if (block.isBlock() &&
        block.getParent().isTry() &&
        block.getParent().getFirstChild() == block) {
      for (Node s = block.getNext(); s != null; s = s.getNext()) {
        if (NodeUtil.hasCatchHandler(s)) {
          return s.getFirstChild();
        }
      }
    }
    return null;
  }

  /**
   * A {@link ControlFlowGraph} which provides a node comparator based on the
   * pre-order traversal of the AST.
   */
  private static class AstControlFlowGraph extends ControlFlowGraph<Node> {
    private final Map<DiGraphNode<Node, Branch>, Integer> priorities;

    /**
     * Constructor.
     * @param entry The entry node.
     * @param priorities The map from nodes to position in the AST (to be
     *    filled by the {@link ControlFlowAnalysis#shouldTraverse}).
     */
    private AstControlFlowGraph(Node entry,
        Map<DiGraphNode<Node, Branch>, Integer> priorities,
        boolean edgeAnnotations) {
      super(entry,
          true /* node annotations */, edgeAnnotations);
      this.priorities = priorities;
    }

    @Override
    /**
     * Returns a node comparator based on the pre-order traversal of the AST.
     * @param isForward x 'before' y in the pre-order traversal implies
     * x 'less than' y (if true) and x 'greater than' y (if false).
     */
    public Comparator<DiGraphNode<Node, Branch>> getOptionalNodeComparator(
        boolean isForward) {
      if (isForward) {
        return new Comparator<DiGraphNode<Node, Branch>>() {
          @Override
          public int compare(
              DiGraphNode<Node, Branch> n1, DiGraphNode<Node, Branch> n2) {
            return getPosition(n1) - getPosition(n2);
          }
        };
      } else {
        return new Comparator<DiGraphNode<Node, Branch>>() {
          @Override
          public int compare(
              DiGraphNode<Node, Branch> n1, DiGraphNode<Node, Branch> n2) {
            return getPosition(n2) - getPosition(n1);
          }
        };
      }
    }

    /**
     * Gets the pre-order traversal position of the given node.
     * @return An arbitrary counter used for comparing positions.
     */
    private int getPosition(DiGraphNode<Node, Branch> n) {
      Integer priority = priorities.get(n);
      Preconditions.checkNotNull(priority);
      return priority;
    }
  }
}
