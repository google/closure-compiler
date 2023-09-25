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
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Computes must-be-reaching definition for all uses of each variable.
 *
 * <p>A definition of {@code A} in {@code A = foo()} is a must-be-reaching definition of the use of
 * {@code A} in {@code alert(A)} if all paths from entry node to the use pass through that
 * definition and it is the last definition before the use.
 *
 * <p>By definition, a must-be-reaching definition for a given use is always a single definition and
 * it "dominates" that use (i.e. always must execute before that use).
 */
final class MustBeReachingVariableDef
    extends DataFlowAnalysis<Node, MustBeReachingVariableDef.MustDef> {

  // The scope of the function that we are analyzing.
  private final AbstractCompiler compiler;
  private final Set<Var> escaped;
  private final Map<String, Var> allVarsInFn;

  MustBeReachingVariableDef(
      ControlFlowGraph<Node> cfg,
      AbstractCompiler compiler,
      Set<Var> escaped,
      Map<String, Var> allVarsInFn) {
    super(cfg);
    this.compiler = compiler;
    this.escaped = escaped;
    this.allVarsInFn = allVarsInFn;
  }

  /**
   * Abstraction of a local variable definition. It represents the node which a local variable is
   * defined as well as a set of other local variables that this definition reads from. For example
   * N: a = b + foo.bar(c). The definition node will be N, the depending set would be {b,c}.
   */
  static class Definition {
    final Node node;
    final Set<Var> depends = new LinkedHashSet<>();
    private boolean unknownDependencies = false;

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

    @Override
    public String toString() {
      return "Definition@" + node;
    }

    @Override
    public int hashCode() {
      return node.hashCode();
    }
  }

  /**
   * Must reaching definition lattice representation. It captures a product lattice for each local
   * (non-escaped) variable. The sub-lattice is a n + 2 element lattice with all the {@link
   * Definition} in the program, TOP and BOTTOM.
   *
   * <p>Since this is a Must-Define analysis, BOTTOM represents the case where there might be more
   * than one reaching definition for the variable.
   *
   * <p>(TOP) / | | \ N1 N2 N3 ....Nn \ | | / (BOTTOM)
   */
  static final class MustDef implements LatticeElement {

    // TODO(user): Use bit vector instead of maps might get better
    // performance. Change it after this is tested to be fully functional.

    // When a Var "A" = "TOP", "A" does not exist in reachingDef's keySet.
    // When a Var "A" = Node N, "A" maps to that node.
    // When a Var "A" = "BOTTOM", "A" maps to null.
    final LinkedHashMap<Var, Definition> reachingDef = new LinkedHashMap<>();

    public MustDef() {}

    public MustDef(Collection<Var> vars) {
      for (Var var : vars) {
        reachingDef.put(var, new Definition(var.getScope().getRootNode()));
      }
    }

    /**
     * Copy constructor.
     *
     * @param other The constructed object is a replicated copy of this element.
     */
    public MustDef(MustDef other) {
      this.reachingDef.putAll(other.reachingDef);
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof MustDef) && ((MustDef) other).reachingDef.equals(this.reachingDef);
    }

    @Override
    public int hashCode() {
      return reachingDef.hashCode();
    }
  }

  private static class MustDefJoin implements FlowJoiner<MustDef> {

    final MustDef result = new MustDef();
    final LinkedHashMap<Var, Definition> resultMap = result.reachingDef;

    @Override
    public void joinFlow(MustDef input) {
      input.reachingDef.forEach(this::mergeVarDef);
    }

    private void mergeVarDef(Var var, Definition def) {
      final Definition resultDef;
      if (def == null) {
        resultDef = null;
      } else if (!this.resultMap.containsKey(var)) {
        resultDef = def;
      } else if (def.equals(this.resultMap.get(var))) {
        return;
      } else {
        resultDef = null;
      }
      this.resultMap.put(var, resultDef);
    }

    @Override
    public MustDef finish() {
      return this.result;
    }
  }

  @Override
  boolean isForward() {
    return true;
  }

  @Override
  MustDef createEntryLattice() {
    return new MustDef(allVarsInFn.values());
  }

  @Override
  MustDef createInitialEstimateLattice() {
    return new MustDef();
  }

  @Override
  MustDefJoin createFlowJoiner() {
    return new MustDefJoin();
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
  private void computeMustDef(Node n, Node cfgNode, MustDef output, boolean conditional) {
    switch (n.getToken()) {
      case BLOCK:
      case ROOT:
      case FUNCTION:
        return;

      case WHILE:
      case DO:
      case IF:
      case FOR:
        computeMustDef(NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
        return;

      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        // for(x in y) {...}
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        if (NodeUtil.isNameDeclaration(lhs)) {
          lhs = lhs.getLastChild(); // for(var x in y) {...}
        }
        if (lhs.isName()) {
          // TODO(lharker): This doesn't seem right - given for (x in y), the value set to x isn't y
          addToDefIfLocal(lhs.getString(), cfgNode, rhs, output);
        } else if (lhs.isDestructuringLhs()) {
          lhs = lhs.getFirstChild();
        }
        if (lhs.isDestructuringPattern()) {
          computeMustDef(lhs, cfgNode, output, true);
        }
        return;

      case OPTCHAIN_GETPROP:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        return;

      case AND:
      case OR:
      case COALESCE:
      case OPTCHAIN_GETELEM:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        computeMustDef(n.getLastChild(), cfgNode, output, true);
        return;

      case OPTCHAIN_CALL:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
          computeMustDef(c, cfgNode, output, true);
        }
        return;

      case HOOK:
        computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        computeMustDef(n.getSecondChild(), cfgNode, output, true);
        computeMustDef(n.getLastChild(), cfgNode, output, true);
        return;

      case LET:
      case CONST:
      case VAR:
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          if (c.hasChildren()) {
            if (c.isName()) {
              computeMustDef(c.getFirstChild(), cfgNode, output, conditional);
              addToDefIfLocal(
                  c.getString(), conditional ? null : cfgNode, c.getFirstChild(), output);
            } else {
              checkState(c.isDestructuringLhs(), c);
              computeMustDef(c.getSecondChild(), cfgNode, output, conditional);
              computeMustDef(c.getFirstChild(), cfgNode, output, conditional);
            }
          }
        }
        return;

      case DEFAULT_VALUE:
        if (n.getFirstChild().isDestructuringPattern()) {
          computeMustDef(n.getSecondChild(), cfgNode, output, true);
          computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
        } else if (n.getFirstChild().isName()) {
          computeMustDef(n.getSecondChild(), cfgNode, output, true);
          addToDefIfLocal(
              n.getFirstChild().getString(), conditional ? null : cfgNode, null, output);
        } else {
          computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
          computeMustDef(n.getSecondChild(), cfgNode, output, true);
        }
        break;

      case NAME:
        if (NodeUtil.isLhsByDestructuring(n)) {
          addToDefIfLocal(n.getString(), conditional ? null : cfgNode, null, output);
        } else if ("arguments".equals(n.getString())) {
          escapeParameters(output);
        }
        return;

      default:
        if (NodeUtil.isAssignmentOp(n)) {
          if (n.getFirstChild().isName()) {
            Node name = n.getFirstChild();
            computeMustDef(name.getNext(), cfgNode, output, conditional);
            addToDefIfLocal(
                name.getString(), conditional ? null : cfgNode, n.getLastChild(), output);
            return;
          } else if (NodeUtil.isNormalGet(n.getFirstChild())) {
            // Treat all assignments to arguments as redefining the
            // parameters itself.
            Node obj = n.getFirstFirstChild();
            if (obj.isName() && "arguments".equals(obj.getString())) {
              // TODO(user): More accuracy can be introduced
              // i.e. We know exactly what arguments[x] is if x is a constant
              // number.
              escapeParameters(output);
            }
          } else if (n.getFirstChild().isDestructuringPattern()) {
            computeMustDef(n.getSecondChild(), cfgNode, output, conditional);
            computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
            return;
          }
        }

        // DEC and INC actually defines the variable.
        if (n.isDec() || n.isInc()) {
          Node target = n.getFirstChild();
          if (target.isName()) {
            addToDefIfLocal(target.getString(), conditional ? null : cfgNode, null, output);
            return;
          }
        }

        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          computeMustDef(c, cfgNode, output, conditional);
        }
    }
  }

  /**
   * Set the variable lattice for the given name to the node value in the def lattice. Do nothing if
   * the variable name is one of the escaped variable.
   *
   * @param node The CFG node where the definition should be record to. {@code null} if this is a
   *     conditional define.
   */
  private void addToDefIfLocal(
      String name, @Nullable Node node, @Nullable Node rValue, MustDef def) {
    Var var = allVarsInFn.get(name);

    // var might be null if the variable is defined in the externs
    if (var == null) {
      return;
    }

    for (Map.Entry<Var, Definition> pair : def.reachingDef.entrySet()) {
      Definition otherDef = pair.getValue();
      if (otherDef == null) {
        continue;
      }
      if (otherDef.depends.contains(var)) {
        pair.setValue(null);
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

  private void escapeParameters(MustDef output) {
    for (Var v : allVarsInFn.values()) {
      if (isParameter(v)) {
        // Assume we no longer know where the parameter comes from
        // anymore.
        output.reachingDef.put(v, null);
      }
    }

    // Also, assume we no longer know anything that depends on a parameter.
    for (Map.Entry<Var, Definition> pair : output.reachingDef.entrySet()) {
      Definition value = pair.getValue();
      if (value == null) {
        continue;
      }
      for (Var dep : value.depends) {
        if (isParameter(dep)) {
          pair.setValue(null);
          break;
        }
      }
    }
  }

  private static boolean isParameter(Var v) {
    return v.isParam();
  }

  /**
   * Computes all the local variables that rValue reads from and store that in the def's depends
   * set.
   */
  private void computeDependence(final Definition def, Node rValue) {
    NodeTraversal.traverse(
        compiler,
        rValue,
        new AbstractCfgNodeTraversalCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isName()) {
              Var dep = allVarsInFn.get(n.getString());
              if (dep == null) {
                def.unknownDependencies = true;
              } else {
                def.depends.add(dep);
              }
            }
          }
        });
  }

  /**
   * Gets the must-be-reaching definition of a given use node.
   *
   * @param name name of the variable. It can only be names of local variable that are not function
   *     parameters, escaped variables or variables declared in catch.
   * @param useNode the location of the use where the definition reaches.
   */
  Definition getDef(String name, Node useNode) {
    checkArgument(getCfg().hasNode(useNode));
    GraphNode<Node, Branch> n = getCfg().getNode(useNode);
    LinearFlowState<MustDef> state = n.getAnnotation();
    return state.getIn().reachingDef.get(allVarsInFn.get(name));
  }

  @Nullable Node getDefNode(String name, Node useNode) {
    Definition def = getDef(name, useNode);
    return def == null ? null : def.node;
  }

  boolean dependsOnOuterScopeVars(Definition def) {
    if (def.unknownDependencies) {
      return true;
    }

    for (Var s : def.depends) {
      // Don't inline try catch
      if (s.getScope().isCatchScope()) {
        return true;
      }
    }
    return false;
  }
}
