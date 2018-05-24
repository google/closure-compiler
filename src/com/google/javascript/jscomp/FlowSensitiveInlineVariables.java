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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.MustBeReachingVariableDef.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Inline variables when possible. Using the information from
 * {@link MaybeReachingVariableUse} and {@link MustBeReachingVariableDef},
 * this pass attempts to inline a variable by placing the value at the
 * definition where the variable is used. The basic requirements for inlining
 * are the following:
 *
 * <ul>
 * <li> There is exactly one reaching definition at the use of that variable
 * </li>
 * <li> There is exactly one use for that definition of the variable
 * </li>
 * </ul>
 *
 * <p>Other requirements can be found in {@link Candidate#canInline}. Currently
 * this pass does not operate on the global scope due to compilation time.
 *
 */
class FlowSensitiveInlineVariables implements CompilerPass, ScopedCallback {

  /**
   * Implementation:
   *
   * This pass first perform a traversal to gather a list of Candidates that
   * could be inlined using {@link GatherCandidates}.
   *
   * The second step involves verifying that each candidate is actually safe
   * to inline with {@link Candidate#canInline(Scope)} and finally perform
   * inlining using {@link Candidate#inlineVariable()}.
   *
   * The reason for the delayed evaluation of the candidates is because we
   * need two separate dataflow result.
   */
  private final AbstractCompiler compiler;

  // These two pieces of data is persistent in the whole execution of enter
  // scope.
  private ControlFlowGraph<Node> cfg;
  private Set<Candidate> candidates;
  private MustBeReachingVariableDef reachingDef;
  private MaybeReachingVariableUse reachingUses;

  private static class SideEffectPredicate implements Predicate<Node> {
    // Check if there are side effects affecting the value of any of these names
    // (but not properties defined on that name)
    private final Set<String> namesToCheck;

    public SideEffectPredicate() {
      namesToCheck = null;
    }

    public SideEffectPredicate(Set<String> names) {
      this.namesToCheck = names;
    }

    @Override
    public boolean apply(Node n) {
      // When the node is null it means, we reached the implicit return
      // where the function returns (possibly without an return statement)
      if (n == null) {
        return false;
      }

      if (namesToCheck != null
          && n.isName()
          && namesToCheck.contains(n.getString())
          && NodeUtil.isLValue(n)) {
        // the name is being written to. this is a problem, unless it is part of a top-level assign
        // chain and the write will take place after all CFG node subexpressions are evaluated
        return !isTopLevelAssignTarget(n);
      }

      // TODO(user): We only care about calls to functions that
      // passes one of the dependent variable to a non-side-effect free
      // function.
      if (n.isCall() && NodeUtil.functionCallHasSideEffects(n)) {
        return true;
      }

      if (n.isNew() && NodeUtil.constructorCallHasSideEffects(n)) {
        return true;
      }

      if (n.isDelProp()) {
        return true;
      }

      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (!ControlFlowGraph.isEnteringNewCfgNode(c) && apply(c)) {
          return true;
        }
      }
      return false;
    }
  }

  // predicate that does not check for any ASSIGNs, only function calls and delete props
  private static final Predicate<Node> SIDE_EFFECT_PREDICATE = new SideEffectPredicate();

  /** Whether the given node is the target of a (possibly chained) assignment */
  private static boolean isTopLevelAssignTarget(Node n) {
    Node ancestor = n.getParent();
    while (ancestor.isAssign()) {
      ancestor = ancestor.getParent();
    }
    return ancestor.isExprResult();
  }

  public FlowSensitiveInlineVariables(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return !n.isScript() || !t.getInput().isExtern();
  }

  @Override
  public void enterScope(NodeTraversal t) {

    if (t.inGlobalScope()) {
      return; // Don't even brother. All global variables are likely escaped.
    }

    if (!t.getScope().isFunctionBlockScope()) {
      return; // Only want to do the following if its a function block scope.
    }

    Node functionScopeRoot = t.getScopeRoot().getParent();

    if (!isCandidateFunction(functionScopeRoot)) {
      return;
    }

    if (LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE < t.getScope().getVarCount()) {
      return;
    }

    Es6SyntacticScopeCreator scopeCreator = (Es6SyntacticScopeCreator) t.getScopeCreator();

    // Compute the forward reaching definition.
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);

    // Process the body of the function.
    cfa.process(null, functionScopeRoot);
    cfg = cfa.getCfg();

    reachingDef = new MustBeReachingVariableDef(cfg, t.getScope(), compiler, scopeCreator);
    reachingDef.analyze();
    candidates = new LinkedHashSet<>();

    // Using the forward reaching definition search to find all the inline
    // candidates
    NodeTraversal.traverse(compiler, t.getScopeRoot(), new GatherCandidates());
    // Compute the backward reaching use. The CFG can be reused.
    reachingUses = new MaybeReachingVariableUse(cfg, t.getScope(), compiler, scopeCreator);
    reachingUses.analyze();
    while (!candidates.isEmpty()) {
      Candidate c = candidates.iterator().next();
      if (c.canInline(t.getScope())) {
        c.inlineVariable();
        candidates.remove(c);

        // If candidate "c" has dependencies, then inlining it may have introduced new dependencies
        // for our other inlining candidates. MustBeReachingVariableDef uses a dependency graph in
        // its analysis. Generating a new dependency graph will need another CFG computation.
        // Ideally we should iterate to a fixed point, but that can be costly. Therefore, we use
        // a conservative heuristic here: For each candidate "other", we back off if its set of
        // dependencies cannot contain all of "c"'s dependencies.
        if (!c.defMetadata.depends.isEmpty()) {
          for (Iterator<Candidate> it = candidates.iterator(); it.hasNext();) {
            Candidate other = it.next();
            if (other.defMetadata.depends.contains(t.getScope().getVar(c.varName))
                && !other.defMetadata.depends.containsAll(c.defMetadata.depends)) {
              it.remove();
            }
          }
        }
      } else {
        candidates.remove(c);
      }
    }
  }

  private boolean isCandidateFunction(Node fn) {
    Node fnBody = fn.getLastChild();
    return containsCandidateExpressions(fnBody);
  }

  private static boolean containsCandidateExpressions(Node n) {
    if (n.isFunction()) {
      // don't recurse into inner functions or into expressions the can't contain declarations.
      return false;
    }

    if (NodeUtil.isNameDeclaration(n) || isAssignmentToName(n)) {
      // if it is a simple assignment
      if (n.getFirstChild().isName()) {
        return true;
      }
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (containsCandidateExpressions(c)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAssignmentToName(Node n) {
    if (NodeUtil.isAssignmentOp(n) || n.isDec() || n.isInc()) {
      // if it is a simple assignment
      return (n.getFirstChild().isName());
    }
    return false;
  }

  @Override
  public void exitScope(NodeTraversal t) {}

  @Override
  public void process(Node externs, Node root) {
    (new NodeTraversal(compiler, this,  new Es6SyntacticScopeCreator(compiler)))
        .traverseRoots(externs, root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // TODO(user): While the helpers do a subtree traversal on the AST, the
    // compiler pass itself only traverse the AST to look for function
    // declarations to perform dataflow analysis on. We could combine
    // the traversal in DataFlowAnalysis's computeEscaped later to save some
    // time.
  }

  private class GatherCandidatesCfgNodeCallback extends AbstractCfgNodeTraversalCallback {
    Node cfgNode = null;

    public void setCfgNode(Node cfgNode) {
      this.cfgNode = cfgNode;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {

        // n.getParent() isn't null. This just the case where n is the root
        // node that gatherCb started at.
        if (parent == null) {
          return;
        }

        // Make sure that the name node is purely a read.
        if ((NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n)
            || NodeUtil.isNameDeclaration(parent)
            || parent.isInc()
            || parent.isDec()
            || parent.isParamList()
            || parent.isCatch()
            || NodeUtil.isLhsByDestructuring(n)) {
          return;
        }

        String name = n.getString();
        if (compiler.getCodingConvention().isExported(name)) {
          return;
        }

        Definition def = reachingDef.getDef(name, cfgNode);
        // TODO(nicksantos): We need to add some notion of @const outer
        // scope vars. We can inline those just fine.
        if (def != null && !reachingDef.dependsOnOuterScopeVars(def)) {
          candidates.add(new Candidate(name, def, n, cfgNode));
        }
      }
    }
  }

  /**
   * Gathers a list of possible candidates for inlining based only on
   * information from {@link MustBeReachingVariableDef}. The list will be stored
   * in {@code candidates} and the validity of each inlining Candidate should
   * be later verified with {@link Candidate#canInline(Scope)} when
   * {@link MaybeReachingVariableUse} has been performed.
   */
  private class GatherCandidates extends AbstractShallowCallback {
    final GatherCandidatesCfgNodeCallback gatherCb = new GatherCandidatesCfgNodeCallback();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      DiGraphNode<Node, Branch> graphNode = cfg.getDirectedGraphNode(n);
      if (graphNode == null) {
        // Not a CFG node.
        return;
      }
      final Node cfgNode = n;

      gatherCb.setCfgNode(cfgNode);
      NodeTraversal.traverse(compiler, cfgNode, gatherCb);
    }
  }

  /**
   * Models the connection between a definition and a use of that definition.
   */
  private class Candidate {

    // Name of the variable.
    private final String varName;

    // Nodes related to the definition.
    private Node def;
    private final Definition defMetadata;

    // Nodes related to the use.
    private final Node use;
    private final Node useCfgNode;

    // Number of uses of the variable within the current CFG node.
    private int numUsesWithinCfgNode;

    Candidate(String varName, Definition defMetadata,
        Node use, Node useCfgNode) {
      checkArgument(use.isName());
      this.varName = varName;
      this.defMetadata = defMetadata;
      this.use = use;
      this.useCfgNode = useCfgNode;
    }

    private Node getDefCfgNode() {
      return defMetadata.node;
    }

    private boolean canInline(final Scope scope) {
      // Cannot inline a parameter.
      if (getDefCfgNode().isFunction()) {
        return false;
      }

      getDefinition(getDefCfgNode());
      getNumUseInUseCfgNode(useCfgNode);

      // Definition was not found.
      if (def == null) {
        return false;
      }

      // Check that the assignment isn't used as a R-Value.
      // TODO(user): Certain cases we can still inline.
      if (def.isAssign() && !NodeUtil.isExprAssign(def.getParent())) {
        return false;
      }

      Set<String> namesToCheck = new HashSet<>();
      if (defMetadata.depends != null) {
        for (Var var : defMetadata.depends) {
          namesToCheck.add(var.getName());
        }
      }

      SideEffectPredicate sideEffectPredicateWithNames = new SideEffectPredicate(namesToCheck);

      // A subexpression evaluated after the variable has a side effect.
      // Example, for x:
      // x = readProp(b), modifyProp(b); print(x);
      if (checkPostExpressions(def, getDefCfgNode(), sideEffectPredicateWithNames)) {
        return false;
      }

      // Similar check as the above but this time, all the sub-expressions
      // evaluated before the variable.
      // x = readProp(b); modifyProp(b), print(x);
      if (checkPreExpressions(use, useCfgNode, sideEffectPredicateWithNames)) {
        return false;
      }

      // TODO(user): Side-effect is OK sometimes. As long as there are no
      // side-effect function down all paths to the use. Once we have all the
      // side-effect analysis tool.
      if (NodeUtil.mayHaveSideEffects(def.getLastChild(), compiler)) {
        return false;
      }

      // TODO(user): We could inline all the uses if the expression is short.

      // Finally we have to make sure that there are no more than one use
      // in the program and in the CFG node. Even when it is semantically
      // correctly inlining twice increases code size.
      if (numUsesWithinCfgNode != 1) {
        return false;
      }

      // Make sure that the name is not within a loop
      if (NodeUtil.isWithinLoop(use)) {
        return false;
      }

      Collection<Node> uses = reachingUses.getUses(varName, getDefCfgNode());

      if (uses.size() != 1) {
        return false;
      }

      if (!isRhsSafeToInline(scope)) {
        return false;
      }

      // We can skip the side effect check along the paths of two nodes if
      // they are just next to each other.
      if (NodeUtil.isStatementBlock(getDefCfgNode().getParent()) &&
          getDefCfgNode().getNext() != useCfgNode) {
        // Similar side effect check as above but this time the side effect is
        // else where along the path.
        // x = readProp(b); while(modifyProp(b)) {}; print(x);
        CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch>
            pathCheck = new CheckPathsBetweenNodes<>(
            cfg,
            cfg.getDirectedGraphNode(getDefCfgNode()),
            cfg.getDirectedGraphNode(useCfgNode),
            SIDE_EFFECT_PREDICATE,
            Predicates.
                <DiGraphEdge<Node, ControlFlowGraph.Branch>>alwaysTrue(),
            false);
        if (pathCheck.somePathsSatisfyPredicate()) {
          return false;
        }
      }

      return true;
    }

    /**
     * Actual transformation.
     */
    private void inlineVariable() {
      Node defParent = def.getParent();
      Node useParent = use.getParent();
      if (def.isAssign()) {
        Node rhs = def.getLastChild();
        rhs.detach();
        // Oh yes! I have grandparent to remove this.
        checkState(defParent.isExprResult());
        while (defParent.getParent().isLabel()) {
          defParent = defParent.getParent();
        }
        compiler.reportChangeToEnclosingScope(defParent);
        defParent.detach();
        useParent.replaceChild(use, rhs);
      } else if (NodeUtil.isNameDeclaration(defParent)) {
        Node rhs = def.getLastChild();
        if (defParent.isConst()) {
          // If it is a const var we don't want to remove the rhs of the variable
          def.replaceChild(rhs, Node.newString(Token.NAME, "undefined"));
          useParent.replaceChild(use, rhs);
        } else {
          def.removeChild(rhs);
          useParent.replaceChild(use, rhs);
        }
      } else {
        throw new IllegalStateException("No other definitions can be inlined.");
      }
      compiler.reportChangeToEnclosingScope(useParent);
    }

    /**
     * Set the def node
     *
     * @param n A node that has a corresponding CFG node in the CFG.
     */
    private void getDefinition(Node n) {
      AbstractCfgNodeTraversalCallback gatherCb =
          new AbstractCfgNodeTraversalCallback() {

            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              switch (n.getToken()) {
                case NAME:
                  if (n.getString().equals(varName) && n.hasChildren()) {
                    def = n;
                  }
                  return;

                case ASSIGN:
                  Node lhs = n.getFirstChild();
                  if (lhs.isName() && lhs.getString().equals(varName)) {
                    def = n;
                  }
                  return;
                default:
                  break;
              }
            }
          };
      NodeTraversal.traverse(compiler, n, gatherCb);
    }

    /**
     * Computes the number of uses of the variable varName and store it in
     * numUseWithinUseCfgNode.
     */
    private void getNumUseInUseCfgNode(final Node cfgNode) {

      numUsesWithinCfgNode = 0;
      AbstractCfgNodeTraversalCallback gatherCb =
          new AbstractCfgNodeTraversalCallback() {

            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName() && n.getString().equals(varName)) {
                // We make a special exception when the entire cfgNode is a chain
                // of assignments, since in that case the assignment statements
                // will happen after the inlining of the right hand side.
                // TODO(lharker): We can probably remove the isAssignChain check, and instead use
                // the SideEffectPredicate to look for dangerous assignments in the same CFG node
                if (parent.isAssign() && (parent.getFirstChild() == n)
                    && isAssignChain(parent, cfgNode)) {
                  // Don't count lhs of top-level assignment chain
                  return;
                } else {
                  numUsesWithinCfgNode++;
                }
              }
            }

            private boolean isAssignChain(Node child, Node ancestor) {
              for (Node n = child; n != ancestor; n = n.getParent()) {
                if (!n.isAssign()) {
                  return false;
                }
              }
              return true;
            }
          };

      NodeTraversal.traverse(compiler, cfgNode, gatherCb);
    }

    /**
     * Check if the definition we're considering inline has anything that makes inlining unsafe
     * (that hasn't already been caught).
     *
     * @param usageScope The scope we will inline the variable into.
     */
    private boolean isRhsSafeToInline(final Scope usageScope) {
      // Don't inline definitions with an R-Value that has:
      // 1) GETPROP, GETELEM,
      // 2) anything that creates a new object.
      // Example:
      // var x = a.b.c; j.c = 1; print(x);
      // Inlining print(a.b.c) is not safe - consider if j were an alias to a.b.
      if (NodeUtil.has(
          def.getLastChild(),
          new Predicate<Node>() {
            @Override
            public boolean apply(Node input) {
              switch (input.getToken()) {
                case GETELEM:
                case GETPROP:
                case ARRAYLIT:
                case OBJECTLIT:
                case REGEXP:
                case NEW:
                  return true; // unsafe to inline.
                default:
                  break;
              }
              return false;
            }
          },
          new Predicate<Node>() {
            @Override
            public boolean apply(Node input) {
              // Recurse if the node is not a function.
              return !input.isFunction();
            }
          })) {
        return false;
      }

      // Don't inline definitions with an rvalue referencing names that are not declared in the
      // usage's scope. (Unlike the above check, this includes names referenced inside function
      // expressions in the rvalue).
      // e.g. the name "a" below in the definition of "b":
      //   {
      //     let a = 3;
      //     var b = a;
      //   }
      // return b;   // "a" is not declared in this scope so we can't inline this to "return a;"
      if (NodeUtil.has(
          def.getLastChild(),
          new Predicate<Node>() {
            @Override
            public boolean apply(Node input) {
              if (input.isName()) {
                String name = input.getString();
                if (!name.isEmpty() && !usageScope.hasSlot(name)) {
                  return true; // unsafe to inline.
                }
              }
              return false;
            }
          },
          Predicates.alwaysTrue())) {
        return false;
      }
      return true;
    }
  }

  /**
   * Given an expression by its root and sub-expression n, return true if the predicate is true for
   * some expression evaluated after n.
   *
   * <p>NOTE: this doesn't correctly check destructuring patterns, because their order of evaluation
   * is different from AST traversal order,  but currently this is ok because
   * FlowSensitiveInlineVariables never inlines variable assignments inside destructuring.
   *
   * <p>Example:
   *
   * <p>NotChecked(), NotChecked(), n, Checked(), Checked();
   */
  private static boolean checkPostExpressions(
      Node n, Node expressionRoot, Predicate<Node> predicate) {
    for (Node p = n; p != expressionRoot; p = p.getParent()) {
      for (Node cur = p.getNext(); cur != null; cur = cur.getNext()) {
        if (predicate.apply(cur)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Given an expression by its root and sub-expression n, return true if the predicate is true for
   * some expression evaluated before n.
   *
   * <p>In most cases evaluation order follows left-to-right AST order. Destructuring pattern
   * evaluation is an exception.
   *
   * <p>Example:
   *
   * <p>Checked(), Checked(), n, NotChecked(), NotChecked();
   */
  private static boolean checkPreExpressions(
      Node n, Node expressionRoot, Predicate<Node> predicate) {
    for (Node p = n; p != expressionRoot; p = p.getParent()) {
      Node oldestSibling = p.getParent().getFirstChild();
      // Evaluate a destructuring assignment right-to-left.
      if (oldestSibling.isDestructuringPattern()) {
        if (p.isDestructuringPattern()) {
          if (p.getNext() != null && predicate.apply(p.getNext())) {
            return true;
          }
        }
        continue;
      }
      for (Node cur = oldestSibling; cur != p; cur = cur.getNext()) {
        if (predicate.apply(cur)) {
          return true;
        }
      }
    }
    return false;
  }
}
