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

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Removes local variable assignments that are useless based on information from {@link
 * LiveVariablesAnalysis}. If there is an assignment to variable {@code x} and {@code x} is dead
 * after this assignment, we know that the current content of {@code x} will not be read and this
 * assignment is useless.
 */
class DeadAssignmentsElimination extends AbstractScopedCallback implements CompilerPass {

  private final AbstractCompiler compiler;
  private LiveVariablesAnalysis liveness;
  private final Deque<BailoutInformation> functionStack;

  private static final class BailoutInformation {
    boolean containsFunction;
    boolean containsRemovableAssign;
  }

  public DeadAssignmentsElimination(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.functionStack = new ArrayDeque<>();
  }

  @Override
  public void process(Node externs, Node root) {
    checkNotNull(externs);
    checkNotNull(root);
    checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (functionStack.isEmpty()) {
      return;
    }
    if (n.isFunction()) {
      functionStack.peekFirst().containsFunction = true;
    } else if (isRemovableAssign(n)) {
      functionStack.peekFirst().containsRemovableAssign = true;
    }
  }

  @Override
  public void enterScope(NodeTraversal t) {
    if (t.inFunctionBlockScope()) {
      functionStack.addFirst(new BailoutInformation());
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (t.inFunctionBlockScope()) {
      eliminateDeadAssignments(t);
      functionStack.removeFirst();
    }
  }

  private void eliminateDeadAssignments(NodeTraversal t) {
    checkArgument(t.inFunctionBlockScope());
    checkState(!functionStack.isEmpty());

    // Skip unchanged functions (note that the scope root is the function block, not the function).
    if (!compiler.hasScopeChanged(t.getScopeRoot().getParent())) {
      return;
    }

    BailoutInformation currentFunction = functionStack.peekFirst();
    // We are not going to do any dead assignment elimination in when there is
    // at least one inner function because in most browsers, when there is a
    // closure, ALL the variables are saved (escaped).
    if (currentFunction.containsFunction) {
      return;
    }

    // We don't do any dead assignment elimination if there are no assigns
    // to eliminate. :)
    if (!currentFunction.containsRemovableAssign) {
      return;
    }

    Scope blockScope = t.getScope();
    Scope functionScope = blockScope.getParent();
    if (LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE
        < blockScope.getVarCount() + functionScope.getVarCount()) {
      return;
    }

    // Computes liveness information first.
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
    liveness =
        new LiveVariablesAnalysis(
            cfg, functionScope, blockScope, compiler, new SyntacticScopeCreator(compiler));
    liveness.analyze();
    Map<String, Var> allVarsInFn = liveness.getAllVariables();
    tryRemoveDeadAssignments(t, cfg, allVarsInFn);
  }



  // Matches all assignment operators and increment/decrement operators.
  // Does *not* match VAR initialization, since RemoveUnusedVariables
  // will already remove variables that are initialized but unused.
  boolean isRemovableAssign(Node n) {
    return (NodeUtil.isAssignmentOp(n) && n.getFirstChild().isName()) || n.isInc() || n.isDec();
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
      ControlFlowGraph<Node> cfg,
      Map<String, Var> allVarsInFn) {
    Iterable<DiGraphNode<Node, Branch>> nodes = cfg.getDirectedGraphNodes();

    for (DiGraphNode<Node, Branch> cfgNode : nodes) {
      FlowState<LiveVariableLattice> state =
          cfgNode.getAnnotation();
      Node n = cfgNode.getValue();
      if (n == null) {
        continue;
      }
      switch (n.getToken()) {
        case IF:
        case WHILE:
        case DO:
          tryRemoveAssignment(t, NodeUtil.getConditionExpression(n), state, allVarsInFn);
          continue;
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case FOR_AWAIT_OF:
          if (n.isVanillaFor()) {
            tryRemoveAssignment(t, NodeUtil.getConditionExpression(n), state, allVarsInFn);
          }
          continue;
        case SWITCH:
        case CASE:
        case RETURN:
          if (n.hasChildren()) {
            tryRemoveAssignment(t, n.getFirstChild(), state, allVarsInFn);
          }
          continue;
          // TODO(user): case VAR: Remove var a=1;a=2;.....
        default:
          break;
      }

      tryRemoveAssignment(t, n, state, allVarsInFn);
    }
  }

  private void tryRemoveAssignment(NodeTraversal t, Node n,
      FlowState<LiveVariableLattice> state, Map<String, Var> allVarsInFn) {
    tryRemoveAssignment(t, n, n, state, allVarsInFn);
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
      FlowState<LiveVariableLattice> state, Map<String, Var> allVarsInFn) {

    Node parent = n.getParent();
    boolean isDeclarationNode = NodeUtil.isNameDeclaration(parent);

    if (NodeUtil.isAssignmentOp(n) || n.isInc() || n.isDec() || isDeclarationNode) {

      if (parent.isConst()) {
        // Removing the RHS of a const produces as invalid AST.
        return;
      }

      Node lhs = isDeclarationNode ? n : n.getFirstChild();
      Node rhs = NodeUtil.getRValueOfLValue(lhs);

      // Recurse first. Example: dead_x = dead_y = 1; We try to clean up dead_y
      // first.
      if (rhs != null) {
        tryRemoveAssignment(t, rhs, exprRoot, state, allVarsInFn);
        rhs = NodeUtil.getRValueOfLValue(lhs);
      }

      // Multiple declarations should be processed from right-to-left to ensure side-effects
      // are run in the correct order.
      if (isDeclarationNode && lhs.getNext() != null) {
        tryRemoveAssignment(t, lhs.getNext(), exprRoot, state, allVarsInFn);
      }

      // Ignore declarations that don't initialize a value. Dead code removal will kill those nodes.
      // Also ignore the var declaration if it's in a for-loop instantiation since there's not a
      // safe place to move the side-effects.
      if (isDeclarationNode && (rhs == null || NodeUtil.isAnyFor(parent.getParent()))) {
        return;
      }

      if (!lhs.isName()) {
        return; // Not a local variable assignment.
      }
      String name = lhs.getString();
      Scope scope = t.getScope();
      checkState(scope.isFunctionBlockScope() || scope.isBlockScope());
      if (!allVarsInFn.containsKey(name)) {
        return;
      }
      Var var = allVarsInFn.get(name);

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
        n.replaceWith(rhs);
        compiler.reportChangeToEnclosingScope(rhs);
        return;
      }

      int index = liveness.getVarIndex(var.name);
      if (state.getOut().isLive(index)) {
        return; // Variable not dead.
      }

      if (state.getIn().isLive(index)
          && isVariableStillLiveWithinExpression(n, exprRoot, var.name)) {
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
        n.replaceWith(rhs);
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
        } else if (parent.isVanillaFor() && NodeUtil.getConditionExpression(parent) != n) {
          parent.replaceChild(n, IR.empty());
        } else {
          // Cannot replace x = a++ with x = a because that's not valid
          // when a is not a number.
          return;
        }
      } else if (isDeclarationNode) {
        lhs.removeChild(rhs);
        parent.getParent().addChildAfter(IR.exprResult(rhs), parent);
        rhs.getParent().useSourceInfoFrom(rhs);
      } else {
        // Not reachable.
        throw new IllegalStateException("Unknown statement");
      }

      compiler.reportChangeToEnclosingScope(parent);
      return;
    } else {
      for (Node c = n.getFirstChild(); c != null;) {
        Node next = c.getNext();
        if (!ControlFlowGraph.isEnteringNewCfgNode(c)) {
          tryRemoveAssignment(t, c, exprRoot, state, allVarsInFn);
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
      switch (n.getParent().getToken()) {
        case OR:
        case AND:
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

        case HOOK:
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
      if (NodeUtil.isNameDeclOrSimpleAssignLhs(n, n.getParent())) {
        checkState(n.getParent().isAssign(), n.getParent());
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

    switch (n.getToken()) {
      // Conditionals
      case OR:
      case AND:
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
      case HOOK:
        VariableLiveness first = isVariableReadBeforeKill(
            n.getFirstChild(), variable);
        if (first != VariableLiveness.MAYBE_LIVE) {
          return first;
        }
        return checkHookBranchReadBeforeKill(
            n.getSecondChild(), n.getLastChild(), variable);

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
