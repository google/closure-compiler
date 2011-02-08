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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.JoinOp.BinaryJoinOp;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Checks that all paths through a constructor add properties in the same order.
 *
 * Background: one of the key elements of the design of V8 that improves
 * performance is that it attempts to discover the classes of the objects
 * created at run time.  Rather than implementing an object as a hash table,
 * most objects have a reference to a "hidden class" that stores the list of
 * properties so that the object itself can maintain only a list of property
 * values.  If many objects share the same hidden class, then this reduces
 * memory usage.  Furthermore, at a get-prop site, if only one hidden class ever
 * occurs for the receiver, then it can speed up the dispatch process, compiling
 * it into only 3 instructions.  (For more information, see this
 * <a href="http://www.youtube.com/watch?v=hWhMKalEicY">video</a>.)
 *
 * The key point relating to this pass is that a hidden class is not a set of
 * properties but rather an ordered list.  This is necesary so that iterating
 * over an object produces its keys in insertion order, as expected.  As a
 * result, if two different paths through the constructor generate different
 * ordered lists of properties, then the objects created via those paths will
 * have different hidden classes, defeating the optimizations described above.
 * This pass attempts to find and warn the user about such code.
 *
 */
class CheckPropertyOrder extends AbstractPostOrderCallback
    implements CompilerPass {
  static final DiagnosticType UNASSIGNED_PROPERTY = DiagnosticType.error(
      "UNASSIGNED_PROPERTY",
      "not all control paths assign property {1} in function {0}");
  static final DiagnosticType UNEQUAL_PROPERTIES = DiagnosticType.error(
      "UNEQUAL_PROPERTIES",
      "different control paths produce different (ordered) property lists:"
      + " {0} vs. {1}");

  private final AbstractCompiler compiler;
  private final CheckLevel level;
  private final boolean onlyOneError;
  private int errorCount;

  CheckPropertyOrder(AbstractCompiler compiler, CheckLevel level) {
    this(compiler, level, false);
  }

  CheckPropertyOrder(
      AbstractCompiler compiler, CheckLevel level, boolean onlyOneError) {
    this.compiler = compiler;
    this.level = level;
    this.onlyOneError = onlyOneError;
  }

  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  public void visit(NodeTraversal t, Node n, Node parent) {
    // Look for both top-level functions and assignments of functions to
    // qualified names.
    Node func = null;
    String funcName = null;
    if (NodeUtil.isFunction(n) && isConstructor(n)) {
      func = n;
      funcName = n.getFirstChild().getString();
    } else if (NodeUtil.isAssign(n)
               && NodeUtil.isFunction(n.getFirstChild().getNext())
               && isConstructor(n)) {
      func = n.getFirstChild().getNext();
      funcName = n.getFirstChild().getQualifiedName();
    }

    if (func != null) {
      FunctionType funcType = (FunctionType) func.getJSType();
      checkConstructor(
          func, (funcType != null) ? funcType.getInstanceType() : null,
          t.getSourceName(), funcName);
    }
  }

  /** Determines whether the given node is jsdoc-ed as a constructor. */
  private static boolean isConstructor(Node n) {
    return (n.getJSDocInfo() != null) && n.getJSDocInfo().isConstructor();
  }

  @SuppressWarnings("unchecked")
  private void checkConstructor(Node func, ObjectType objType,
                                String sourceName, String funcName) {
    Preconditions.checkArgument(NodeUtil.isFunction(func));

    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    cfa.process(null, func.getFirstChild().getNext().getNext());
    ControlFlowGraph<Node> cfg = cfa.getCfg();

    new PropertyOrdersFlowAnalysis(cfa.getCfg()).analyze();

    Annotation ann = cfa.getCfg().getImplicitReturn().getAnnotation();
    List<String>[] orders =
        ((FlowState<PropertyOrders>) ann).getIn().getOrders();
    if (orders.length == 0) {
      throw new AssertionError(
          "no paths through constructor " + funcName + "?");
    }
    if (orders.length > 1) {
      report(sourceName, func, UNEQUAL_PROPERTIES,
             reverse(orders[0]).toString(), reverse(orders[1]).toString());
    }
    if (objType != null) {
      for (String propName : objType.getOwnPropertyNames()) {
        if (!orders[0].contains(propName)) {
          report(sourceName, func, UNASSIGNED_PROPERTY, funcName, propName);
        }
      }
    }
  }

  /** Reports the given error. Returns whether to continue. */
  private void report(
      String srcName, Node node, DiagnosticType type, String... args) {
    if (!onlyOneError || (++errorCount <= 1)) {
      compiler.report(JSError.make(srcName, node, level, type, args));
    }
  }

  /** Returns the given List in the reverse order. */
  private static <T> List<T> reverse(List<T> seq) {
    if (seq.isEmpty()) {
      return seq;
    }
    List<T> rev = Lists.newArrayList(seq);
    Collections.reverse(seq);
    return rev;
  }

  private static class OrdersJoinOp extends BinaryJoinOp<PropertyOrders> {
    @Override
    public PropertyOrders apply(PropertyOrders a, PropertyOrders b) {
      return new PropertyOrders(
          Sets.newHashSet(Sets.union(a.orders, b.orders)));
    }
  }

  /**
   * Stores all possible (ordered) lists of properties that may have been added
   * to <code>this</code> at some point in the code.
   *
   * The bottom element of the lattice is an empty set, meaning no
   * possibilities.  At the first statement in the constructor, we start with a
   * set containing only one possibility: an empty list of properties.  (Note
   * that we do not model the properties of the superclass, if any.)  Each
   * assignment, say, <code>this.a = ...</code> adds <code>a</code> to the end
   * of all lists in the set.  Joining two results in the union of the
   * possibilities of those results.
   *
   * For example, if the constructor body contained just the code <code><pre>
   *   if (x) {
   *     this.a = 1;
   *     this.b = 2;
   *   } else {
   *     this.b = 2;
   *     this.a = 1;
   *   }
   * </pre></code>, then at the end of the constructor, the possibilities would
   * be {['a', 'b'], ['b', 'a']} because there are two possible orderings,
   * based on which path is taken in the <code>if</code>.
   */
  private static class PropertyOrders implements LatticeElement {
    /** The bottom element of the lattice: an empty set of lists. */
    public static final PropertyOrders EMPTY =
        new PropertyOrders(Sets.<List<String>>newHashSet());

    /** A set of possible ordered lists of properties. */
    private final Set<List<String>> orders;

    private PropertyOrders(Set<List<String>> orders) {
      this.orders = orders;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof PropertyOrders)) {
        return false;
      }
      return orders.equals(((PropertyOrders) other).orders);
    }

    /**
     * Returns a new set of possible orders with the given string added at the
     * end of each possibility.
     */
    public PropertyOrders copyAndAdd(String propName) {
      Set<List<String>> orders = Sets.newHashSet();
      for (List<String> order : this.orders) {
        if (!order.contains(propName)) {
          List<String> nOrder = Lists.newArrayList(order);
          nOrder.add(propName);
          order = nOrder;
        }
        orders.add(order);
      }
      return new PropertyOrders(orders);
    }

    /**
     * Returns all possible orders over the first N properties that are
     * definitely assigned over all paths.
     */
    @SuppressWarnings("unchecked")
    public List<String>[] getOrders() {
      int minSize = Integer.MAX_VALUE;
      for (List<String> seq : this.orders) {
        minSize = Math.min(minSize, seq.size());
      }
      Set<List<String>> orders = Sets.newHashSet();
      for (List<String> seq : this.orders) {
        //orders.add(seq.subList(seq.size() - minSize, seq.size()));
        orders.add(seq.subList(0, minSize));
      }
      return orders.toArray(
          (List<String>[]) new List<?>[orders.size()]);
    }

    @Override
    public String toString() {
      return "{" + Joiner.on(", ").join(orders) + "}";
    }
  }

  /** Implements a data flow analysis over PropertyOrders. */
  private static class PropertyOrdersFlowAnalysis
      extends DataFlowAnalysis<Node, PropertyOrders> {
    public PropertyOrdersFlowAnalysis(ControlFlowGraph<Node> cfg) {
      super(cfg, new OrdersJoinOp());
    }

    @Override
    public boolean isForward() { return true; }

    @Override
    public PropertyOrders createInitialEstimateLattice() {
      // An empty orders means no possibilities at all.
      return PropertyOrders.EMPTY;
    }

    @Override
    public PropertyOrders createEntryLattice() {
      // Initially, we have only one possibility:  no properties.
      Set<List<String>> orders = Sets.newHashSet();
      orders.add(new Stack<String>());
      return new PropertyOrders(orders);
    }

    /** Computes the orders after executing the given node. */
    @Override
    public PropertyOrders flowThrough(Node node, PropertyOrders input) {
      switch (node.getType()) {
        case Token.BLOCK:
        case Token.LABEL:
        case Token.FUNCTION:
          return input;

        case Token.IF:
        case Token.WHILE:
        case Token.DO:
          return flowThrough(NodeUtil.getConditionExpression(node), input);

        case Token.SWITCH:
        case Token.WITH:
          return flowThrough(node.getFirstChild(), input);

        case Token.FOR:
          if (node.getChildCount() == 4) {
            // Note that the post-loop expression is not considered here.  That
            // is handled in the branched version above.
            Node pre = node.getFirstChild(), cond = pre.getNext();
            return flowThrough(cond, flowThrough(pre, input));
          } else {
            Node lhs = node.getFirstChild(), rhs = lhs.getNext();
            return flowThrough(rhs, flowThrough(lhs, input));
          }

        case Token.HOOK:
          Node cond = node.getFirstChild();
          input = flowThrough(cond, input);
          Node ifTrue = cond.getNext(), ifFalse = ifTrue.getNext();
          return join(flowThrough(ifTrue, input), flowThrough(ifFalse, input));

        case Token.AND:
        case Token.OR:
          Node left = node.getFirstChild(), right = left.getNext();
          input = flowThrough(left, input);
          return join(input, flowThrough(right, input));

        case Token.ASSIGN:
          // If the left hand side is "this.x", then add x to the lists.
          Node lhs = node.getFirstChild(), rhs = lhs.getNext();
          if (lhs.getType() == Token.GETPROP) {
            Node llhs = lhs.getFirstChild(), lrhs = llhs.getNext();
            if ((llhs.getType() == Token.THIS)
                && (lrhs.getType() == Token.STRING)
                && (lrhs.getNext() == null)) {
              return flowThrough(rhs, input.copyAndAdd(lrhs.getString()));
            }
          }
          return flowThrough(rhs, flowThrough(lhs, input));

        default:
          for (node = node.getFirstChild();
               node != null;
               node = node.getNext()) {
            input = flowThrough(node, input);
          }
          return input;
      }
    }
  }
}
