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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Computes reaching definition for all use of each variables.
 *
 * A definition of {@code A} in {@code A = foo()} is a reaching definition of
 * the use of {@code A} in {@code alert(A)} if all paths from entry node must
 * reaches that definition and it is the last definition before the use.
 *
 */
final class MustBeReachingVariableDef extends
    DataFlowAnalysis<Node, MustBeReachingVariableDef.MustDef> {

  // The scope of the function that we are analyzing.
  private final Scope jsScope;
  private final AbstractCompiler compiler;
  private final Set<Var> escaped;

  MustBeReachingVariableDef(
      ControlFlowGraph<Node> cfg, Scope jsScope, AbstractCompiler compiler) {
    super(cfg, new MustDefJoin());
    this.jsScope = jsScope;
    this.compiler = compiler;
    this.escaped = Sets.newHashSet();
    computeEscaped(jsScope, escaped, compiler);
  }

  /**
   * Abstraction of a local variable definition. It represents the node which
   * a local variable is defined as well as a set of other local variables that
   * this definition reads from. For example N: a = b + foo.bar(c). The
   * definition node will be N, the depending set would be {b,c}.
   */
  private static class Definition {
    final Node node;
    final Set<Var> depends = Sets.newHashSet();
    Definition(Node node) {
      this.node = node;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Definition)) {
        return false;
      }
      Definition otherDef = (Definition) other;
      // If the var has the same definition node we can assume they have the
      // same depends set.
      return otherDef.node == node;
    }
  }

  /**
   * Must reaching definition lattice representation. It captures a product
   * lattice for each local (non-escaped) variable. The sub-lattice is
   * a n + 2 element lattice with all the {@link Definition} in the program,
   * TOP and BOTTOM.
   *
   * <p>Since this is a Must-Define analysis, BOTTOM represents the case where
   * there might be more than one reaching definition for the variable.
   *
   *
   *           (TOP)
   *       /   |   |      \
   *     N1    N2  N3 ....Nn
   *      \    |   |      /
   *          (BOTTOM)
   *
   */
  static final class MustDef implements LatticeElement {

    // TODO(user): Use bit vector instead of maps might get better
    // performance. Change it after this is tested to be fully functional.

    // When a Var "A" = "TOP", "A" does not exist in reachingDef's keySet.
    // When a Var "A" = Node N, "A" maps to that node.
    // When a Var "A" = "BOTTOM", "A" maps to null.
    final Map<Var, Definition> reachingDef;

    public MustDef() {
      reachingDef = Maps.newHashMap();
    }

    public MustDef(Iterator<Var> vars) {
      this();
      while(vars.hasNext()) {
        Var var = vars.next();
        // Every variable in the scope is defined once in the beginning of the
        // function: all the declared variables are undefined, all functions
        // have been assigned and all arguments has its value from the caller.
        reachingDef.put(var, new Definition(var.scope.getRootNode()));
      }
    }

    /**
     * Copy constructor.
     *
     * @param other The constructed object is a replicated copy of this element.
     */
    public MustDef(MustDef other) {
      reachingDef = Maps.newHashMap(other.reachingDef);
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof MustDef) &&
          ((MustDef) other).reachingDef.equals(this.reachingDef);
    }
  }

  private static class MustDefJoin extends JoinOp.BinaryJoinOp<MustDef> {
    @Override
    public MustDef apply(MustDef a, MustDef b) {
      MustDef result = new MustDef();
      Map<Var, Definition> resultMap = result.reachingDef;

      // Take the join of all variables that are not TOP in this.
      for (Var var : a.reachingDef.keySet()) {

        Definition aDef = a.reachingDef.get(var);

        if (aDef == null) {
          // "a" is BOTTOM implies that the variable has more than one possible
          // definition. We set the join of this to be BOTTOM regardless of what
          // "b" might be.
          resultMap.put(var, null);
          continue;
        }

        Node aNode = aDef.node;

        if (b.reachingDef.containsKey(var)) {
          Definition bDef = b.reachingDef.get(var);

          if (aDef.equals(bDef)) {
            resultMap.put(var, aDef);
          } else {
            resultMap.put(var, null);
          }
        } else {
          resultMap.put(var, aDef);
        }
      }

      // Take the join of all variables that are not TOP in other but it is TOP
      // in this.
      for (Var var : b.reachingDef.keySet()) {
        if (!a.reachingDef.containsKey(var)) {
          resultMap.put(var, b.reachingDef.get(var));
        }
      }
      return result;
    }
  }

  @Override
  boolean isForward() {
    return true;
  }

  @Override
  MustDef createEntryLattice() {
    return new MustDef(jsScope.getVars());
  }

  @Override
  MustDef createInitialEstimateLattice() {
    return new MustDef();
  }

  @Override
  MustDef flowThrough(Node n, MustDef input) {
    // TODO(user): We are doing a straight copy from input to output. There
    // might be some opportunities to cut down overhead.
    MustDef output = new MustDef(input);
    // TODO(user): This must know about ON_EX edges but it should handle
    // it better than what we did in liveness. Because we are in a forward mode,
    // we can used the branched forward analysis.
    computeMustDef(n, n, output, false);
    return output;
  }

  /**
   * @param n The node in question.
   * @param cfgNode The node to add
   * @param conditional true if the definition is not always executed.
   */
  private void computeMustDef(
      Node n, Node cfgNode, MustDef output, boolean conditional) {
    switch (n.getType()) {

      case Token.BLOCK:
      case Token.FUNCTION:
        return;

      case Token.WHILE:
      case Token.DO:
      case Token.IF:
        computeMustDef(
            NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
        return;

      case Token.FOR:
        if (!NodeUtil.isForIn(n)) {
          computeMustDef(
              NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
        } else {
          // for(x in y) {...}
          Node lhs = n.getFirstChild();
          Node rhs = lhs.getNext();
          if (NodeUtil.isVar(lhs)) {
            lhs = lhs.getLastChild(); // for(var x in y) {...}
          }
          if (NodeUtil.isName(lhs)) {
            addToDefIfLocal(lhs.getString(), cfgNode, rhs, output);
          }
        }
        return;

      case Token.AND:
      case Token.OR:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        computeMustDef(n.getLastChild(), cfgNode, output, true);
        return;

      case Token.HOOK:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        computeMustDef(n.getFirstChild().getNext(), cfgNode, output, true);
        computeMustDef(n.getLastChild(), cfgNode, output, true);
        return;

      case Token.VAR:
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          if (c.hasChildren()) {
            computeMustDef(c.getFirstChild(), cfgNode, output, conditional);
            addToDefIfLocal(c.getString(), conditional ? null : cfgNode,
                c.getFirstChild(), output);
          }
        }
        return;

      default:
        if (NodeUtil.isAssignmentOp(n) && NodeUtil.isName(n.getFirstChild())) {
          Node name = n.getFirstChild();
          computeMustDef(name.getNext(), cfgNode, output, conditional);
          addToDefIfLocal(name.getString(), conditional ? null : cfgNode,
              n.getLastChild(), output);
        } else {

          // DEC and INC actually defines the variable.
          if (n.getType() == Token.DEC || n.getType() == Token.INC) {
            Node target = n.getFirstChild();
            if (NodeUtil.isName(target)) {
              addToDefIfLocal(target.getString(),
                  conditional ? null : cfgNode, null, output);
              return;
            }
          }

          for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            computeMustDef(c, cfgNode, output, conditional);
          }
        }
    }
  }

  /**
   * Set the variable lattice for the given name to the node value in the def
   * lattice. Do nothing if the variable name is one of the escaped variable.
   *
   * @param node The CFG node where the definition should be record to.
   *     {@code null} if this is a conditional define.
   */
  private void addToDefIfLocal( String name, @Nullable Node node,
      @Nullable Node rValue, MustDef def) {
    Var var = jsScope.getVar(name);

    // var might be null because the variable might be defined in the extern
    // that we might not traverse.
    if (var == null || var.scope != jsScope) {
      return;
    }

    for (Var other : def.reachingDef.keySet()) {
      Definition otherDef = def.reachingDef.get(other);
      if (otherDef == null) {
        continue;
      }
      if (otherDef.depends.contains(var)) {
        def.reachingDef.put(other, null);
      }
    }

    if (!escaped.contains(var)) {
      if (node == null) {
        def.reachingDef.put(var, null);
      } else {
        Definition definition = new Definition(node);
        if (rValue != null) {
          computeDependence(definition, rValue);
        }
        def.reachingDef.put(var, definition);
      }
    }
  }

  /**
   * Computes all the local variables that rValue reads from and store that
   * in the def's depends set.
   */
  private void computeDependence(final Definition def, Node rValue) {
    NodeTraversal.traverse(compiler, rValue,
        new AbstractCfgNodeTraversalCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (NodeUtil.isName(n) && jsScope.isDeclared(n.getString(), true)) {
          def.depends.add(jsScope.getVar(n.getString()));
        }
      }
    });
  }

  /**
   * Gets the must reaching definition of a given node. The node must be one of
   * the control flow graph nodes.
   *
   * @param name name of the variable. It can only be names of local variable
   *     that are not function parameters, escaped variables or variables
   *     declared in catch.
   * @param useNode the location of the use where the definition reaches.
   */
  Node getDef(String name, Node useNode) {
    Preconditions.checkArgument(getCfg().hasNode(useNode));
    GraphNode<Node, Branch> n = getCfg().getNode(useNode);
    FlowState<MustDef> state = n.getAnnotation();
    Definition def = state.getIn().reachingDef.get(jsScope.getVar(name));
    if (def == null) {
      return null;
    } else {
      return def.node;
    }
  }

  boolean dependsOnOuterScopeVars(String name, Node useNode) {
    Preconditions.checkArgument(getCfg().hasNode(useNode));
    GraphNode<Node, Branch> n = getCfg().getNode(useNode);
    FlowState<MustDef> state = n.getAnnotation();
    Definition def = state.getIn().reachingDef.get(jsScope.getVar(name));
    for (Var s : def.depends) {
      if (s.scope != jsScope) {
        return true;
      }
    }
    return false;
  }
}
