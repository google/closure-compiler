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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes "may be" reaching use for all definitions of each variables.
 *
 * A use of {@code A} in {@code alert(A)} is a "may be" reaching use of
 * the definition of {@code A} at {@code A = foo()} if at least one path from
 * the use node reaches that definition and it is the last definition before
 * the use on that path.
 *
 */
class MaybeReachingVariableUse extends
    DataFlowAnalysis<Node, MaybeReachingVariableUse.ReachingUses> {

  // The scope of the function that we are analyzing.
  private final Set<Var> escaped;
  private final Map<String, Var> allVarsInFn;
  private final List<Var> orderedVars;

  MaybeReachingVariableUse(
      ControlFlowGraph<Node> cfg,
      Scope jsScope,
      AbstractCompiler compiler,
      Es6SyntacticScopeCreator scopeCreator) {
    super(cfg, new ReachingUsesJoinOp());
    this.escaped = new HashSet<>();
    this.allVarsInFn = new HashMap<>();
    this.orderedVars = new ArrayList<>();

    // TODO(user): Maybe compute it somewhere else and re-use the escape
    // local set here.
    computeEscaped(jsScope.getParent(), escaped, compiler, scopeCreator);
    NodeUtil.getAllVarsDeclaredInFunction(
        allVarsInFn, orderedVars, compiler, scopeCreator, jsScope.getParent());
  }

  /**
   * May use definition lattice representation. It captures a product
   * lattice for each local (non-escaped) variable. The sub-lattice is
   * a n + 2 power set element lattice with all the Nodes in the program,
   * TOP and BOTTOM. This is better explained with an example:
   *
   * Consider: A sub-lattice element representing the variable A represented
   * by { N_4, N_5} where N_x is a Node in the program. This implies at
   * that particular point in the program the content of A is "upward exposed"
   * at point N_4 and N_5.
   *
   * Example:
   *
   * A = 1;
   * ...
   * N_3:
   * N_4: print(A);
   * N_5: y = A;
   * N_6: A = 1;
   * N_7: print(A);
   *
   * At N_3, reads of A in {N_4, N_5} are said to be upward exposed.
   */
  static final class ReachingUses implements LatticeElement {
    final Multimap<Var, Node> mayUseMap;

    public ReachingUses() {
      mayUseMap = HashMultimap.create();
    }

    /**
     * Copy constructor.
     *
     * @param other The constructed object is a replicated copy of this element.
     */
    public ReachingUses(ReachingUses other) {
      mayUseMap = MultimapBuilder.hashKeys().hashSetValues().build(other.mayUseMap);
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof ReachingUses)
          && ((ReachingUses) other).mayUseMap.equals(this.mayUseMap);
    }

    @Override
    public int hashCode() {
      return mayUseMap.hashCode();
    }
  }

  /**
   * The join is a simple union because of the "may be" nature of the analysis.
   *
   * Consider: A = 1; if (x) { A = 2 }; alert(A);
   *
   * The read of A "may be" exposed to A = 1 in the beginning.
   */
  private static class ReachingUsesJoinOp implements JoinOp<ReachingUses> {
    @Override
    public ReachingUses apply(List<ReachingUses> from) {
      ReachingUses result = new ReachingUses();
      for (ReachingUses uses : from) {
        result.mayUseMap.putAll(uses.mayUseMap);
      }
      return result;
    }
  }

  @Override
  boolean isForward() {
    return false;
  }

  @Override
  ReachingUses createEntryLattice() {
    return new ReachingUses();
  }

  @Override
  ReachingUses createInitialEstimateLattice() {
    return new ReachingUses();
  }

  @Override
  ReachingUses flowThrough(Node n, ReachingUses input) {
    ReachingUses output = new ReachingUses(input);

    // If there's an ON_EX edge, this cfgNode may or may not get executed.
    // We can express this concisely by just pretending this happens in
    // a conditional.
    boolean conditional = hasExceptionHandler(n);
    computeMayUse(n, n, output, conditional);

    return output;
  }

  private boolean hasExceptionHandler(Node cfgNode) {
    List<DiGraphEdge<Node, Branch>> branchEdges = getCfg().getOutEdges(cfgNode);
    for (DiGraphEdge<Node, Branch> edge : branchEdges) {
      if (edge.getValue() == Branch.ON_EX) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param conditional Whether {@code n} is only conditionally evaluated given that {@code cfgNode}
   *     is evaluated. Do not remove conditionally redefined variables from the reaching uses set.
   */
  private void computeMayUse(Node n, Node cfgNode, ReachingUses output, boolean conditional) {
    switch (n.getToken()) {
      case BLOCK:
      case ROOT:
      case FUNCTION:
        return;

      case NAME:
        if (NodeUtil.isLhsByDestructuring(n)) {
          if (!conditional) {
            removeFromUseIfLocal(n.getString(), output);
          }
        } else {
          addToUseIfLocal(n.getString(), cfgNode, output);
        }
        return;

      case WHILE:
      case DO:
      case IF:
      case FOR:
        computeMayUse(NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
        return;

      case FOR_IN:
      case FOR_OF:
        // for(x in y) {...}
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        if (NodeUtil.isNameDeclaration(lhs)) {
          lhs = lhs.getLastChild(); // for(var x in y) {...}
          if (lhs.isDestructuringLhs()) {
            lhs = lhs.getFirstChild(); // for (let [x] of obj) {...}
          }
        }
        if (lhs.isName() && !conditional) {
          removeFromUseIfLocal(lhs.getString(), output);
        } else if (lhs.isDestructuringPattern()) {
          computeMayUse(lhs, cfgNode, output, true);
        }
        computeMayUse(rhs, cfgNode, output, conditional);
        return;

      case AND:
      case OR:
        computeMayUse(n.getLastChild(), cfgNode, output, true);
        computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
        return;

      case HOOK:
        computeMayUse(n.getLastChild(), cfgNode, output, true);
        computeMayUse(n.getSecondChild(), cfgNode, output, true);
        computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
        return;

      case VAR:
      case LET:
      case CONST:
        Node varName = n.getFirstChild();
        checkState(n.hasChildren(), "AST should be normalized", n);

        if (varName.isDestructuringLhs()) {
          // Note: since destructuring is evaluated in reverse AST order, we traverse the first
          // child before the second in order to do our backwards data flow analysis.
          computeMayUse(varName.getFirstChild(), cfgNode, output, conditional);
          computeMayUse(varName.getSecondChild(), cfgNode, output, conditional);
        } else if (varName.hasChildren()) {
          computeMayUse(varName.getFirstChild(), cfgNode, output, conditional);
          if (!conditional) {
            removeFromUseIfLocal(varName.getString(), output);
          }
        } // else var name declaration with no initial value
        return;

      case DEFAULT_VALUE:
        if (n.getFirstChild().isDestructuringPattern()) {
          computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
          computeMayUse(n.getSecondChild(), cfgNode, output, true);
        } else if (n.getFirstChild().isName()) {
          // assigning to the name occurs after evaluating the default value
          if (!conditional) {
            removeFromUseIfLocal(n.getFirstChild().getString(), output);
          }
          computeMayUse(n.getSecondChild(), cfgNode, output, true);
        } else {
          computeMayUse(n.getSecondChild(), cfgNode, output, true);
          computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
        }
        break;

      default:
        if (NodeUtil.isAssignmentOp(n) && n.getFirstChild().isName()) {
          Node name = n.getFirstChild();
          if (!conditional) {
            removeFromUseIfLocal(name.getString(), output);
          }

          // In case of a += "Hello". There is a read of a.
          if (!n.isAssign()) {
            addToUseIfLocal(name.getString(), cfgNode, output);
          }

          computeMayUse(name.getNext(), cfgNode, output, conditional);
        } else if (n.isAssign() && n.getFirstChild().isDestructuringPattern()) {
          // Note: the rhs of destructuring is evaluated before the lhs
          computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
          computeMayUse(n.getSecondChild(), cfgNode, output, conditional);
        } else {
          /*
           * We want to traverse in reverse order because we want the LAST
           * definition in the sub-tree.
           */
          for (Node c = n.getLastChild(); c != null; c = c.getPrevious()) {
            computeMayUse(c, cfgNode, output, conditional);
          }
        }
    }
  }

  /**
   * Sets the variable for the given name to the node value in the upward
   * exposed lattice. Do nothing if the variable name is one of the escaped
   * variable.
   */
  private void addToUseIfLocal(String name, Node node, ReachingUses use) {
    Var var = allVarsInFn.get(name);
    if (var == null) {
      return;
    }
    if (!escaped.contains(var)) {
      use.mayUseMap.put(var, node);
    }
  }

  /**
   * Removes the variable for the given name from the node value in the upward
   * exposed lattice. Do nothing if the variable name is one of the escaped
   * variable.
   */
  private void removeFromUseIfLocal(String name, ReachingUses use) {
    Var var = allVarsInFn.get(name);
    if (var == null) {
      return;
    }
    if (!escaped.contains(var)) {
      use.mayUseMap.removeAll(var);
    }
  }

  /**
   * Gets a list of nodes that may be using the value assigned to {@code name}
   * in {@code defNode}. {@code defNode} must be one of the control flow graph
   * nodes.
   *
   * @param name name of the variable. It can only be names of local variable
   *     that are not function parameters, escaped variables or variables
   *     declared in catch.
   * @param defNode The list of upward exposed use for the variable.
   */
  Collection<Node> getUses(String name, Node defNode) {
    GraphNode<Node, Branch> n = getCfg().getNode(defNode);
    checkNotNull(n);
    FlowState<ReachingUses> state = n.getAnnotation();
    return state.getOut().mayUseMap.get(allVarsInFn.get(name));
  }
}
