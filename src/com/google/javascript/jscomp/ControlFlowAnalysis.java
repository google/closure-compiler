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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Comparator.comparingInt;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * This is a compiler pass that computes a control flow graph. Note that this is only a CompilerPass
 * because the Compiler invokes it via Compiler#process. It is never included in a PassConfig.
 *
 */
public final class ControlFlowAnalysis implements Callback, CompilerPass {

  /**
   * Based roughly on the first few pages of
   *
   * "Declarative Intraprocedural Flow Analysis of Java Source Code by
   * Nilsson-Nyman, Hedin, Magnusson &amp; Ekman",
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
      comparingInt(digraphNode -> astPosition.get(digraphNode.getValue()));

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
  private final Deque<Node> exceptionHandler = new ArrayDeque<>();

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
   * @param shouldTraverseFunctions Whether functions should be traversed
   * @param edgeAnnotations Whether to allow edge annotations.
   */
  ControlFlowAnalysis(
      AbstractCompiler compiler, boolean shouldTraverseFunctions, boolean edgeAnnotations) {
    this.compiler = compiler;
    this.shouldTraverseFunctions = shouldTraverseFunctions;
    this.edgeAnnotations = edgeAnnotations;
  }

  public static ControlFlowGraph<Node> getCfg(AbstractCompiler compiler, Node cfgRoot) {
    checkArgument(NodeUtil.isValidCfgRoot(cfgRoot));
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
    cfa.process(null, cfgRoot);
    return cfa.getCfg();
  }

  ControlFlowGraph<Node> getCfg() {
    return cfg;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkArgument(
        NodeUtil.isValidCfgRoot(root), "Unexpected control flow graph root %s", root);
    this.root = root;
    astPositionCounter = 0;
    astPosition = new HashMap<>();
    nodePriorities = new HashMap<>();
    cfg = new AstControlFlowGraph(computeFallThrough(root), nodePriorities, edgeAnnotations);
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
          prioritizeFromEntryNode(candidate);
        }
      }
    }

    // At this point, all reachable nodes have been given a priority, but
    // unreachable nodes have not been given a priority. Put them last.
    // Presumably, it doesn't really matter what priority they get, since
    // this shouldn't happen in real code.
    for (DiGraphNode<Node, Branch> candidate : cfg.getDirectedGraphNodes()) {
      nodePriorities.computeIfAbsent(candidate, k -> ++priorityCounter);
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
        new PriorityQueue<>(10, priorityComparator);
    worklist.add(entry);

    while (!worklist.isEmpty()) {
      DiGraphNode<Node, Branch> current = worklist.remove();
      if (nodePriorities.containsKey(current)) {
        continue;
      }

      nodePriorities.put(current, ++priorityCounter);

      List<DiGraphNode<Node, Branch>> successors = cfg.getDirectedSuccNodes(current);
      worklist.addAll(successors);
    }
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal nodeTraversal, Node n, Node parent) {
    astPosition.put(n, astPositionCounter++);
    switch (n.getToken()) {
      case FUNCTION:
        if (shouldTraverseFunctions || n == cfg.getEntry().getValue()) {
          exceptionHandler.push(n);
          return true;
        }
        return false;
      case TRY:
        exceptionHandler.push(n);
        return true;
      default:
        break;
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
      switch (parent.getToken()) {
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case FOR_AWAIT_OF:
          // Only traverse the body of the for loop.
          return n == parent.getLastChild();

        case DO:
          // Only traverse the body of the do-while.
          return n != parent.getSecondChild();

        // Skip conditions, and only traverse the body of the cases
        case IF:
        case WHILE:
        case WITH:
        case SWITCH:
        case CASE:
        case CATCH:
        case LABEL:
          return n != parent.getFirstChild();
        case FUNCTION:
          return n == parent.getLastChild();
        case CLASS:
          return shouldTraverseFunctions && n == parent.getLastChild();
        case COMPUTED_PROP:
        case CONTINUE:
        case BREAK:
        case EXPR_RESULT:
        case VAR:
        case LET:
        case CONST:
        case RETURN:
        case THROW:
          return false;
        case TRY:
          /* When we are done with the TRY block and there is no FINALLY block,
           * or done with both the TRY and CATCH block, then no more exceptions
           * can be handled at this TRY statement, so it can be taken out of the
           * stack.
           */
          if ((!NodeUtil.hasFinally(parent) && n == NodeUtil.getCatchBlock(parent))
              || NodeUtil.isTryFinallyNode(parent, n)) {
            checkState(exceptionHandler.peek() == parent);
            exceptionHandler.pop();
          }
          break;
        case CLASS_MEMBERS:
        case MEMBER_FUNCTION_DEF:
        default:
          break;
      }
      // Don't traverse further in an arrow function expression
      if (parent.getParent() != null
          && parent.getParent().isArrowFunction()
          && !parent.isBlock()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case IF:
        handleIf(n);
        return;
      case WHILE:
        handleWhile(n);
        return;
      case DO:
        handleDo(n);
        return;
      case FOR:
        handleFor(n);
        return;
      case FOR_OF:
      case FOR_IN:
      case FOR_AWAIT_OF:
        handleEnhancedFor(n);
        return;
      case SWITCH:
        handleSwitch(n);
        return;
      case CASE:
        handleCase(n);
        return;
      case DEFAULT_CASE:
        handleDefault(n);
        return;
      case BLOCK:
      case ROOT:
      case SCRIPT:
        handleStmtList(n);
        return;
      case FUNCTION:
        handleFunction(n);
        return;
      case EXPR_RESULT:
        handleExpr(n);
        return;
      case THROW:
        handleThrow(n);
        return;
      case TRY:
        handleTry(n);
        return;
      case CATCH:
        handleCatch(n);
        return;
      case BREAK:
        handleBreak(n);
        return;
      case CONTINUE:
        handleContinue(n);
        return;
      case RETURN:
        handleReturn(n);
        return;
      case WITH:
        handleWith(n);
        return;
      case LABEL:
      case CLASS_MEMBERS:
      case MEMBER_FUNCTION_DEF:
        return;
      default:
        handleStmt(n);
        return;
    }
  }

  private void handleIf(Node node) {
    Node thenBlock = node.getSecondChild();
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
    Node cond = node.getFirstChild();
    // Control goes to the first statement if the condition evaluates to true.
    createEdge(node, Branch.ON_TRUE, computeFallThrough(cond.getNext()));

    if (!cond.isTrue()) {
      // Control goes to the follow() if the condition evaluates to false.
      createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
    }
    connectToPossibleExceptionHandler(
        node, NodeUtil.getConditionExpression(node));
  }

  private void handleDo(Node node) {
    Node cond = node.getFirstChild();
    // The first edge can be the initial iteration as well as the iterations
    // after.
    createEdge(node, Branch.ON_TRUE, computeFallThrough(cond));
    if (!cond.isTrue()) {
      // The edge that leaves the do loop if the condition fails.
      createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
    }
    connectToPossibleExceptionHandler(
        node, NodeUtil.getConditionExpression(node));
  }

  private void handleEnhancedFor(Node forNode) {
    // We have:  for (index in collection) { body }
    // or:       for (item of collection) { body }
    // or:       for await (item of collection) { body }
    Node item = forNode.getFirstChild();
    Node collection = item.getNext();
    Node body = collection.getNext();
    // The collection behaves like init.
    createEdge(collection, Branch.UNCOND, forNode);
    // The edge that transfer control to the beginning of the loop body.
    createEdge(forNode, Branch.ON_TRUE, computeFallThrough(body));
    // The edge to end of the loop.
    createEdge(forNode, Branch.ON_FALSE, computeFollowNode(forNode, this));
    connectToPossibleExceptionHandler(forNode, collection);
  }

  private void handleFor(Node forNode) {
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
    if (!cond.isEmpty()) {
      createEdge(forNode, Branch.ON_FALSE, computeFollowNode(forNode, this));
    }
    // The end of the body will have a unconditional branch to our iter
    // (handled by calling computeFollowNode of the last instruction of the
    // body. Our iter will jump to the forNode again to another condition
    // check.
    createEdge(iter, Branch.UNCOND, forNode);
    connectToPossibleExceptionHandler(init, init);
    connectToPossibleExceptionHandler(forNode, cond);
    connectToPossibleExceptionHandler(iter, iter);
  }

  private void handleSwitch(Node node) {
    // Transfer to the first non-DEFAULT CASE. if there are none, transfer
    // to the DEFAULT or the EMPTY node.
    Node next = getNextSiblingOfType(
        node.getSecondChild(), Token.CASE, Token.EMPTY);
    if (next != null) { // Has at least one CASE or EMPTY
      createEdge(node, Branch.UNCOND, next);
    } else { // Has no CASE but possibly a DEFAULT
      if (node.getSecondChild() != null) {
        createEdge(node, Branch.UNCOND, node.getSecondChild());
      } else { // No CASE, no DEFAULT
        createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
      }
    }
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }

  private void handleCase(Node node) {
    // Case is a bit tricky....First it goes into the body if condition is true.
    createEdge(node, Branch.ON_TRUE,

        node.getSecondChild());
    // Look for the next CASE, skipping over DEFAULT.
    Node next = getNextSiblingOfType(node.getNext(), Token.CASE);
    if (next != null) { // Found a CASE
      checkState(next.isCase());
      createEdge(node, Branch.ON_FALSE, next);
    } else { // No more CASE found, go back and search for a DEFAULT.
      Node parent = node.getParent();
      Node deflt = getNextSiblingOfType(
        parent.getSecondChild(), Token.DEFAULT_CASE);
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
    if (node.isBlock()
        && parent.isTry()
        && NodeUtil.getCatchBlock(parent) == node
        && !NodeUtil.hasCatchHandler(node)) {
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
      switch (parent.getToken()) {
        case DEFAULT_CASE:
        case CASE:
        case TRY:
          break;
        case ROOT:
          if (node.isRoot() && node.getNext() != null) {
            createEdge(node, Branch.UNCOND, node.getNext());
          }
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
    checkState(node.isFunction());
    checkState(node.getChildCount() == 3);
    createEdge(node, Branch.UNCOND,
        computeFallThrough(node.getLastChild()));
    checkState(exceptionHandler.peek() == node);
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
        if (compiler.getOptions().canContinueAfterErrors()) {
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
    for (cur = node, lastJump = node;
        !isContinueTarget(cur, label);
        cur = cur.getParent()) {
      if (cur.isTry() && NodeUtil.hasFinally(cur)
          && cur.getLastChild() != previous) {
        if (lastJump == node) {
          createEdge(lastJump, Branch.UNCOND, cur.getLastChild());
        } else {
          finallyMap.put(lastJump, computeFallThrough(cur.getLastChild()));
        }
        lastJump = cur;
      }
      checkState(cur.getParent() != null, "Cannot find continue target.");
      previous = cur;
    }
    Node iter = cur;
    if (cur.isVanillaFor()) {
      // the increment expression happens after the continue
      iter = cur.getChildAtIndex(2);
    }

    if (lastJump == node) {
      createEdge(node, Branch.UNCOND, iter);
    } else {
      finallyMap.put(lastJump, iter);
    }
  }

  private void handleReturn(Node node) {
    Node lastJump = null;
    for (Node curHandler : exceptionHandler) {
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
    switch (parent.getToken()) {
      // The follow() of any of the path from IF would be what follows IF.
      case IF:
        return computeFollowNode(fromNode, parent, cfa);
      case CASE:
      case DEFAULT_CASE:
        // After the body of a CASE, the control goes to the body of the next
        // case, without having to go to the case condition.
        if (parent.getNext() != null) {
          if (parent.getNext().isCase()) {
            return parent.getNext().getSecondChild();
          } else if (parent.getNext().isDefaultCase()) {
            return parent.getNext().getFirstChild();
          } else {
            throw new IllegalStateException("Not reachable");
          }
        } else {
          return computeFollowNode(fromNode, parent, cfa);
        }
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        return parent;
      case FOR:
        return parent.getSecondChild().getNext();
      case WHILE:
      case DO:
        return parent;
      case TRY:
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
              cfa.createEdge(fromNode, Branch.ON_EX, finallyNode);
            }
          }
          return computeFollowNode(fromNode, parent, cfa);
        }
        // fall through
      default:
        break;
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
    switch (n.getToken()) {
      case DO:
      case FOR:
        return computeFallThrough(n.getFirstChild());
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        return n.getSecondChild();
      case LABEL:
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
        checkState(handler.isTry());
        Node catchBlock = NodeUtil.getCatchBlock(handler);

        boolean lastJumpInCatchBlock = false;
        for (Node ancestor : lastJump.getAncestors()) {
          if (ancestor == handler) {
            break;
          } else if (ancestor == catchBlock) {
            lastJumpInCatchBlock = true;
            break;
          }
        }

        // No catch but a FINALLY, or lastJump is inside the catch block.
        if (!NodeUtil.hasCatchHandler(catchBlock) || lastJumpInCatchBlock) {
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
  private static Node getNextSiblingOfType(Node first, Token ... types) {
    for (Node c = first; c != null; c = c.getNext()) {
      for (Token type : types) {
        if (c.getToken() == type) {
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
  static boolean isContinueTarget(
      Node target, String label) {
    return NodeUtil.isLoopStructure(target) && matchLabel(target.getParent(), label);
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
    switch (n.getToken()) {
      case CALL:
      case TAGGED_TEMPLATELIT:
      case GETPROP:
      case GETELEM:
      case THROW:
      case NEW:
      case ASSIGN:
      case INC:
      case DEC:
      case INSTANCEOF:
      case IN:
      case YIELD:
      case AWAIT:
        return true;
      case FUNCTION:
        return false;
      default:
        break;
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
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case DO:
      case WHILE:
      case SWITCH:
        return true;
      case BLOCK:
      case ROOT:
      case IF:
      case TRY:
        return labeled;
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
    if (block.isBlock()
        && block.getParent().isTry()
        && block.getParent().getFirstChild() == block) {
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
        return comparingInt(this::getPosition);
      } else {
        return comparingInt(this::getPosition).reversed();
      }
    }

    /**
     * Gets the pre-order traversal position of the given node.
     * @return An arbitrary counter used for comparing positions.
     */
    private int getPosition(DiGraphNode<Node, Branch> n) {
      Integer priority = priorities.get(n);
      checkNotNull(priority);
      return priority;
    }
  }
}
