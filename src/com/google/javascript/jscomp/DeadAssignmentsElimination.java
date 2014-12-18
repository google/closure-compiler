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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Removes local variable assignments that are useless based on information from
 * {@link LiveVariablesAnalysis}. If there is an assignment to variable
 * {@code x} and {@code x} is dead after this assignment, we know that the
 * current content of {@code x} will not be read and this assignment is useless.
 *
 */
class DeadAssignmentsElimination extends AbstractPostOrderCallback implements
    CompilerPass, ScopedCallback {

  private final AbstractCompiler compiler;
  private LiveVariablesAnalysis liveness;

  // Matches all assignment operators and increment/decrement operators.
  // Does *not* match VAR initialization, since RemoveUnusedVariables
  // will already remove variables that are initialized but unused.
  private static final Predicate<Node> matchRemovableAssigns =
      new Predicate<Node>() {
    @Override
    public boolean apply(Node n) {
      return (NodeUtil.isAssignmentOp(n) &&
              n.getFirstChild().isName()) ||
          n.isInc() || n.isDec();
    }
  };

  public DeadAssignmentsElimination(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkNotNull(externs);
    Preconditions.checkNotNull(root);
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();
    // Global scope _SHOULD_ work, however, liveness won't finish without
    // -Xmx1024 in closure. We might have to look at coding conventions for
    // exported variables as well.
    if (scope.isGlobal()) {
      return;
    }

    if (LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE <
        t.getScope().getVarCount()) {
      return;
    }

    // We are not going to do any dead assignment elimination in when there is
    // at least one inner function because in most browsers, when there is a
    // closure, ALL the variables are saved (escaped).
    Node fnBlock = t.getScopeRoot().getLastChild();
    if (NodeUtil.containsFunction(fnBlock)) {
      return;
    }

    // We don't do any dead assignment elimination if there are no assigns
    // to eliminate. :)
    if (!NodeUtil.has(fnBlock, matchRemovableAssigns,
            Predicates.<Node>alwaysTrue())) {
      return;
    }

    // Computes liveness information first.
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
    liveness = new LiveVariablesAnalysis(cfg, scope, compiler);
    liveness.analyze();
    tryRemoveDeadAssignments(t, cfg);
  }

  @Override
  public void exitScope(NodeTraversal t) {
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  /**
   * Try to remove useless assignments from a control flow graph that has been
   * annotated with liveness information.
   *
   * @param t The node traversal.
   * @param cfg The control flow graph of the program annotated with liveness
   *        information.
   */
  private void tryRemoveDeadAssignments(NodeTraversal t,
      ControlFlowGraph<Node> cfg) {
    Iterable<DiGraphNode<Node, Branch>> nodes = cfg.getDirectedGraphNodes();

    for (DiGraphNode<Node, Branch> cfgNode : nodes) {
      FlowState<LiveVariableLattice> state =
          cfgNode.getAnnotation();
      Node n = cfgNode.getValue();
      if (n == null) {
        continue;
      }
      switch (n.getType()) {
        case Token.IF:
        case Token.WHILE:
        case Token.DO:
          tryRemoveAssignment(t, NodeUtil.getConditionExpression(n), state);
          continue;
        case Token.FOR:
          if (!NodeUtil.isForIn(n)) {
            tryRemoveAssignment(
                t, NodeUtil.getConditionExpression(n), state);
          }
          continue;
        case Token.SWITCH:
        case Token.CASE:
        case Token.RETURN:
          if (n.hasChildren()) {
            tryRemoveAssignment(t, n.getFirstChild(), state);
          }
          continue;
        // TODO(user): case Token.VAR: Remove var a=1;a=2;.....
      }

      tryRemoveAssignment(t, n, state);
    }
  }

  private void tryRemoveAssignment(NodeTraversal t, Node n,
      FlowState<LiveVariableLattice> state) {
    tryRemoveAssignment(t, n, n, state);
  }

  /**
   * Determines if any local variables are dead after the instruction {@code n}
   * and are assigned within the subtree of {@code n}. Removes those assignments
   * if there are any.
   *
   * @param n Target instruction.
   * @param exprRoot The CFG node where the liveness information in state is
   *     still correct.
   * @param state The liveness information at {@code n}.
   */
  private void tryRemoveAssignment(NodeTraversal t, Node n, Node exprRoot,
      FlowState<LiveVariableLattice> state) {

    Node parent = n.getParent();

    if (NodeUtil.isAssignmentOp(n) ||
        n.isInc() || n.isDec()) {

      Node lhs = n.getFirstChild();
      Node rhs = lhs.getNext();

      // Recurse first. Example: dead_x = dead_y = 1; We try to clean up dead_y
      // first.
      if (rhs != null) {
        tryRemoveAssignment(t, rhs, exprRoot, state);
        rhs = lhs.getNext();
      }

      Scope scope = t.getScope();
      if (!lhs.isName()) {
        return; // Not a local variable assignment.
      }
      String name = lhs.getString();
      if (!scope.isDeclared(name, false)) {
        return;
      }
      Var var = scope.getVar(name);

      if (liveness.getEscapedLocals().contains(var)) {
        return; // Local variable that might be escaped due to closures.
      }

      // If we have an identity assignment such as a=a, always remove it
      // regardless of what the liveness results because it
      // does not change the result afterward.
      if (rhs != null &&
          rhs.isName() &&
          rhs.getString().equals(var.name) &&
          n.isAssign()) {
        n.removeChild(rhs);
        n.getParent().replaceChild(n, rhs);
        compiler.reportCodeChange();
        return;
      }

      if (state.getOut().isLive(var)) {
        return; // Variable not dead.
      }

      if (state.getIn().isLive(var) &&
          isVariableStillLiveWithinExpression(n, exprRoot, var.name)) {
        // The variable is killed here but it is also live before it.
        // This is possible if we have say:
        //    if (X = a && a = C) {..} ; .......; a = S;
        // In this case we are safe to remove "a = C" because it is dead.
        // However if we have:
        //    if (a = C && X = a) {..} ; .......; a = S;
        // removing "a = C" is NOT correct, although the live set at the node
        // is exactly the same.
        // TODO(user): We need more fine grain CFA or we need to keep track
        // of GEN sets when we recurse here.
        return;
      }

      if (n.isAssign()) {
        n.removeChild(rhs);
        n.getParent().replaceChild(n, rhs);
      } else if (NodeUtil.isAssignmentOp(n)) {
        n.removeChild(rhs);
        n.removeChild(lhs);
        Node op = new Node(NodeUtil.getOpFromAssignmentOp(n), lhs, rhs);
        parent.replaceChild(n, op);
      } else if (n.isInc() || n.isDec()) {
        if (parent.isExprResult()) {
          parent.replaceChild(n,
              IR.voidNode(IR.number(0).srcref(n)));
        } else if (n.isComma() && n != parent.getLastChild()) {
          parent.removeChild(n);
        } else if (parent.isFor() && !NodeUtil.isForIn(parent) &&
            NodeUtil.getConditionExpression(parent) != n) {
          parent.replaceChild(n, IR.empty());
        } else {
          // Cannot replace x = a++ with x = a because that's not valid
          // when a is not a number.
          return;
        }
      } else {
        // Not reachable.
        throw new IllegalStateException("Unknown statement");
      }

      compiler.reportCodeChange();
      return;

    } else {
      for (Node c = n.getFirstChild(); c != null;) {
        Node next = c.getNext();
        if (!ControlFlowGraph.isEnteringNewCfgNode(c)) {
          tryRemoveAssignment(t, c, exprRoot, state);
        }
        c = next;
      }
      return;
    }
  }

  /**
   * Given a variable, node n in the tree and a sub-tree denoted by exprRoot as
   * the root, this function returns true if there exists a read of that
   * variable before a write to that variable that is on the right side of n.
   *
   * For example, suppose the node is x = 1:
   *
   * y = 1, x = 1; // false, there is no reads at all.
   * y = 1, x = 1, print(x) // true, there is a read right of n.
   * y = 1, x = 1, x = 2, print(x) // false, there is a read right of n but
   *                               // it is after a write.
   *
   * @param n The current node we should look at.
   * @param exprRoot The node
   */
  private boolean isVariableStillLiveWithinExpression(
      Node n, Node exprRoot, String variable) {
    while (n != exprRoot) {
      VariableLiveness state = VariableLiveness.MAYBE_LIVE;
      switch (n.getParent().getType()) {
        case Token.OR:
        case Token.AND:
          // If the currently node is the first child of
          // AND/OR, be conservative only consider the READs
          // of the second operand.
          if (n.getNext() != null) {
            state = isVariableReadBeforeKill(
                n.getNext(), variable);
            if (state == VariableLiveness.KILL) {
              state = VariableLiveness.MAYBE_LIVE;
            }
          }
          break;

        case Token.HOOK:
          // If current node is the condition, check each following
          // branch, otherwise it is a conditional branch and the
          // other branch can be ignored.
          if (n.getNext() != null && n.getNext().getNext() != null) {
            state = checkHookBranchReadBeforeKill(
                n.getNext(), n.getNext().getNext(), variable);
          }
          break;

        default:
          for (Node sibling = n.getNext(); sibling != null;
               sibling = sibling.getNext()) {
            state = isVariableReadBeforeKill(sibling, variable);
            if (state != VariableLiveness.MAYBE_LIVE) {
              break;
            }
          }
      }

      // If we see a READ or KILL there is no need to continue.
      if (state == VariableLiveness.READ) {
        return true;
      } else if (state == VariableLiveness.KILL) {
        return false;
      }
      n = n.getParent();
    }
    return false;
  }

  // The current liveness of the variable
  private enum VariableLiveness {
    MAYBE_LIVE, // May be still live in the current expression tree.
    READ, // Known there is a read left of it.
    KILL, // Known there is a write before any read.
  }

  /**
   * Give an expression and a variable. It returns READ, if the first
   * reference of that variable is a read. It returns KILL, if the first
   * reference of that variable is an assignment. It returns MAY_LIVE otherwise.
   */
  private VariableLiveness isVariableReadBeforeKill(
      Node n, String variable) {
    if (ControlFlowGraph.isEnteringNewCfgNode(n)) { // Not a FUNCTION
      return VariableLiveness.MAYBE_LIVE;
    }

    if (n.isName() && variable.equals(n.getString())) {
      if (NodeUtil.isVarOrSimpleAssignLhs(n, n.getParent())) {
        Preconditions.checkState(n.getParent().isAssign());
        // The expression to which the assignment is made is evaluated before
        // the RHS is evaluated (normal left to right evaluation) but the KILL
        // occurs after the RHS is evaluated.
        Node rhs = n.getNext();
        VariableLiveness state = isVariableReadBeforeKill(rhs, variable);
        if (state == VariableLiveness.READ) {
          return state;
        }
        return VariableLiveness.KILL;
      } else {
        return VariableLiveness.READ;
      }
    }

    switch (n.getType()) {
      // Conditionals
      case Token.OR:
      case Token.AND:
        VariableLiveness v1 = isVariableReadBeforeKill(
          n.getFirstChild(), variable);
        VariableLiveness v2 = isVariableReadBeforeKill(
          n.getLastChild(), variable);
        // With a AND/OR the first branch always runs, but the second is
        // may not.
        if (v1 != VariableLiveness.MAYBE_LIVE) {
          return v1;
        } else if (v2 == VariableLiveness.READ) {
          return VariableLiveness.READ;
        } else {
          return VariableLiveness.MAYBE_LIVE;
        }
      case Token.HOOK:
        VariableLiveness first = isVariableReadBeforeKill(
            n.getFirstChild(), variable);
        if (first != VariableLiveness.MAYBE_LIVE) {
          return first;
        }
        return checkHookBranchReadBeforeKill(
            n.getFirstChild().getNext(), n.getLastChild(), variable);

      default:
        // Expressions are evaluated left-right, depth first.
        for (Node child = n.getFirstChild();
            child != null; child = child.getNext()) {
          VariableLiveness state = isVariableReadBeforeKill(child, variable);
          if (state != VariableLiveness.MAYBE_LIVE) {
            return state;
          }
        }
    }

    return VariableLiveness.MAYBE_LIVE;
  }

  private VariableLiveness checkHookBranchReadBeforeKill(
      Node trueCase, Node falseCase, String variable) {
    VariableLiveness v1 = isVariableReadBeforeKill(
      trueCase, variable);
    VariableLiveness v2 = isVariableReadBeforeKill(
      falseCase, variable);
    // With a hook it is unknown which branch will run, so
    // we must be conservative.  A read by either is a READ, and
    // a KILL is only considered if both KILL.
    if (v1 == VariableLiveness.READ || v2 == VariableLiveness.READ) {
      return VariableLiveness.READ;
    } else if (v1 == VariableLiveness.KILL && v2 == VariableLiveness.KILL) {
      return VariableLiveness.KILL;
    } else {
      return VariableLiveness.MAYBE_LIVE;
    }
  }
}
