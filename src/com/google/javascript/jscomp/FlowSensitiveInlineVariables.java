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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.MustBeReachingVariableDef.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.NodeUtil.AllVarsDeclaredInFunction;
import com.google.javascript.jscomp.graph.CheckPathsBetweenNodes;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

/**
 * Inline variables when possible. Using the information from {@link MaybeReachingVariableUse} and
 * {@link MustBeReachingVariableDef}, this pass attempts to inline a variable by placing the value
 * at the definition where the variable is used. The basic requirements for inlining are the
 * following:
 *
 * <ul>
 *   <li>There is exactly one reaching definition at the use of that variable
 *   <li>There is exactly one use for that definition of the variable
 * </ul>
 *
 * <p>Other requirements can be found in {@link Candidate#canInline}. Currently this pass does not
 * operate on the global scope due to compilation time.
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

  private final SideEffectPredicate sideEffectPredicate;

  // Worker traversals collect changes locally because the compiler's ChangeTracker is mutable.
  private final Set<Node> changedScopes = new LinkedHashSet<>();

  // These two pieces of data is persistent in the whole execution of enter
  // scope.
  private ControlFlowGraph<Node> cfg;
  private Set<Candidate> candidates;
  private MustBeReachingVariableDef reachingDef;
  private MaybeReachingVariableUse reachingUses;
  private Map<Node, Integer> candidateCountsByUseCfgNode;
  private Map<Node, Map<String, Integer>> useCountsByCfgNode;

  private class SideEffectPredicate implements Predicate<Node> {
    // Check if there are side effects affecting the value of any of these names
    // (but not properties defined on that name)
    private final @Nullable Set<String> namesToCheck;

    SideEffectPredicate() {
      namesToCheck = null;
    }

    SideEffectPredicate(Set<String> names) {
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

      AstAnalyzer astAnalyzer = compiler.getAstAnalyzer();
      // TODO(user): We only care about calls to functions that
      // passes one of the dependent variable to a non-side-effect free
      // function.
      if ((n.isCall() || n.isOptChainCall() || n.isTaggedTemplateLit())
          && astAnalyzer.functionCallHasSideEffects(n)) {
        return true;
      }

      if (n.isNew() && astAnalyzer.constructorCallHasSideEffects(n)) {
        return true;
      }

      if (n.isDelProp()) {
        return true;
      }

      if ((n.isGetProp() || n.isGetElem()) && NodeUtil.isLValue(n)) {
        return namesToCheck == null || !isTopLevelAssignTarget(n);
      }

      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (!ControlFlowGraph.isEnteringNewCfgNode(c) && apply(c)) {
          return true;
        }
      }
      return false;
    }
  }

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
    this.sideEffectPredicate = new SideEffectPredicate();
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

    @Nullable OptimizationWorkReporter workReporter =
        compiler.getOptions().getOptimizationWorkReporter();
    long workStartNanos = workReporter != null ? System.nanoTime() : 0;

    SyntacticScopeCreator scopeCreator = (SyntacticScopeCreator) t.getScopeCreator();

    // Compute the forward reaching definition.
    cfg =
        ControlFlowAnalysis.builder()
            .setCompiler(compiler)
            .setCfgRoot(functionScopeRoot)
            .setIncludeEdgeAnnotations(true)
            .computeCfg();

    LinkedHashSet<Var> escaped = new LinkedHashSet<>();
    Scope scope = t.getScope();
    AllVarsDeclaredInFunction allVarsDeclaredInFunction =
        NodeUtil.getAllVarsDeclaredInFunction(compiler, scopeCreator, scope.getParent());
    Map<String, Var> allVarsInFn = allVarsDeclaredInFunction.getAllVariables();
    DataFlowAnalysis.computeEscaped(
        scope.getParent(), escaped, compiler, scopeCreator, allVarsInFn);

    reachingDef = new MustBeReachingVariableDef(cfg, compiler, escaped, allVarsInFn);
    reachingDef.analyze();
    candidates = new LinkedHashSet<>();

    // Using the forward reaching definition search to find all the inline
    // candidates
    NodeTraversal.traverse(compiler, t.getScopeRoot(), new GatherCandidates());
    int candidateCount = candidates.size();
    int inlinedCount = 0;

    // Compute the backward reaching use. The CFG and per-function variable info can be reused.
    reachingUses = new MaybeReachingVariableUse(cfg, escaped, allVarsInFn);
    reachingUses.analyze();
    candidateCountsByUseCfgNode = new IdentityHashMap<>();
    for (Candidate candidate : candidates) {
      candidateCountsByUseCfgNode.merge(candidate.useCfgNode, 1, Integer::sum);
    }
    useCountsByCfgNode = new IdentityHashMap<>();
    while (!candidates.isEmpty()) {
      Candidate c = candidates.iterator().next();
      Var candidateVar = checkNotNull(allVarsInFn.get(c.varName));
      if (c.canInline(candidateVar.getScope())) {
        c.inlineVariable();
        useCountsByCfgNode.clear();
        inlinedCount++;
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

    if (workReporter != null) {
      workReporter.recordFunction(
          PassNames.FLOW_SENSITIVE_INLINE_VARIABLES,
          functionScopeRoot,
          t.getScope().getVarCount(),
          cfg.getNodes().size(),
          candidateCount,
          inlinedCount,
          System.nanoTime() - workStartNanos);
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

    if (NodeUtil.isNameDeclaration(n)) {
      for (Node name = n.getFirstChild(); name != null; name = name.getNext()) {
        if (name.isName() && name.hasChildren() && isInlineableRhsShape(name.getFirstChild())) {
          return true;
        }
      }
    } else if (n.isAssign()
        && n.getFirstChild().isName()
        && isInlineableRhsShape(n.getLastChild())) {
      return true;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (containsCandidateExpressions(c)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInlineableRhsShape(Node rhs) {
    return !NodeUtil.has(
        rhs,
        (Node input) -> {
          return switch (input.getToken()) {
            case GETELEM,
                GETPROP,
                OPTCHAIN_GETPROP,
                OPTCHAIN_GETELEM,
                CLASS,
                ARRAYLIT,
                OBJECTLIT,
                REGEXP,
                NEW -> true;
            default -> false;
          };
        },
        (Node input) -> !input.isFunction());
  }

  @Override
  public void exitScope(NodeTraversal t) {}

  @Override
  public void process(Node externs, Node root) {
    int parallelism = compiler.getOptions().getNumParallelThreads();
    if (parallelism > 1
        && root.isRoot()
        && root.hasMoreThanOneChild()
        && compiler.getOptions().getOptimizationWorkReporter() == null) {
      processScriptsConcurrently(externs, root, parallelism);
    } else {
      traverseRoots(externs, root);
    }
    for (Node changedScope : changedScopes) {
      compiler.reportChangeToChangeScope(changedScope);
    }
  }

  private void traverseRoots(Node externs, Node root) {
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .traverseRoots(externs, root);
  }

  private void traverseWithScope(Node root, AbstractScope<?, ?> scope) {
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .traverseWithScope(root, scope);
  }

  private void processScriptsConcurrently(Node externs, Node root, int parallelism) {
    ArrayList<Node> scripts = new ArrayList<>();
    for (Node script = root.getFirstChild(); script != null; script = script.getNext()) {
      if (!script.isScript()) {
        traverseRoots(externs, root);
        return;
      }
      scripts.add(script);
    }
    Node globalRoot = checkNotNull(externs.getParent());
    checkState(root.getParent() == globalRoot);
    // Function-local analyses and rewrites in different scripts touch disjoint AST subtrees. Give
    // every worker the same read-only whole-program scope so name resolution remains identical to
    // the serial traversal.
    AbstractScope<?, ?> globalScope =
        new SyntacticScopeCreator(compiler).createScope(globalRoot, null);

    ExecutorService executor =
        Executors.newFixedThreadPool(
            Math.min(parallelism, scripts.size()),
            runnable -> {
              Thread thread = new Thread(runnable, "jscompiler-FlowSensitiveInlineVariables");
              thread.setDaemon(true);
              return thread;
            });
    ArrayList<Future<Set<Node>>> futures = new ArrayList<>(scripts.size());
    boolean completed = false;
    try {
      for (Node script : scripts) {
        futures.add(
            executor.submit(
                () -> {
                  FlowSensitiveInlineVariables worker =
                      new FlowSensitiveInlineVariables(compiler);
                  worker.traverseWithScope(script, globalScope);
                  return worker.changedScopes;
                }));
      }
      executor.shutdown();
      for (Future<Set<Node>> future : futures) {
        try {
          changedScopes.addAll(future.get());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while inlining variables", e);
        } catch (ExecutionException e) {
          throw new IllegalStateException(
              "Cannot inline variables concurrently", e.getCause());
        }
      }
      completed = true;
    } finally {
      if (!completed) {
        executor.shutdownNow();
      }
    }
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
    @Nullable Node cfgNode = null;

    void setCfgNode(Node cfgNode) {
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
        // This pass only runs on local scopes.
        if (compiler.getCodingConvention().isExported(name, /* local= */ true)) {
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
   * Gathers a list of possible candidates for inlining based only on information from {@link
   * MustBeReachingVariableDef}. The list will be stored in {@code candidates} and the validity of
   * each inlining Candidate should be later verified with {@link Candidate#canInline(Scope)} when
   * {@link MaybeReachingVariableUse} has been performed.
   */
  private class GatherCandidates extends AbstractShallowCallback {
    final GatherCandidatesCfgNodeCallback gatherCb = new GatherCandidatesCfgNodeCallback();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      DiGraphNode<Node, Branch> graphNode = cfg.getNode(n);
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

      // Reject multiply-used definitions before doing the more expensive AST and path checks
      // below. Large generated functions may have hundreds of uses that share the same reaching
      // definition, none of which can be inlined without increasing code size.
      if (!hasExactlyOne(reachingUses.getUses(varName, getDefCfgNode()))) {
        return false;
      }

      getDefinition(getDefCfgNode());
      numUsesWithinCfgNode = getNumUsesInCfgNode(useCfgNode);

      // Definition was not found.
      if (def == null) {
        return false;
      }

      // Check that the assignment isn't used as a R-Value.
      // TODO(user): Certain cases we can still inline.
      if (def.isAssign() && !NodeUtil.isExprAssign(def.getParent())) {
        return false;
      }

      Set<String> namesToCheck = new LinkedHashSet<>();
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
      if (compiler.getAstAnalyzer().mayHaveSideEffects(def.getLastChild())) {
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
        CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> pathCheck =
            new CheckPathsBetweenNodes<>(
                cfg,
                cfg.getNode(getDefCfgNode()),
                cfg.getNode(useCfgNode),
                sideEffectPredicate,
                Predicates.<DiGraphEdge<Node, ControlFlowGraph.Branch>>alwaysTrue(),
                false);
        if (pathCheck.somePathsSatisfyPredicate()) {
          return false;
        }
      }

      return true;
    }

    boolean hasExactlyOne(Iterable<Node> iterable) {
      Iterator<Node> iterator = iterable.iterator();
      if (iterator.hasNext()) {
        iterator.next();
        if (!iterator.hasNext()) {
          return true;
        }
      }
      return false;
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
        changedScopes.add(checkNotNull(ChangeTracker.getEnclosingChangeScopeRoot(defParent)));
        defParent.detach();
        use.replaceWith(rhs);
      } else if (NodeUtil.isNameDeclaration(defParent)) {
        Node rhs = def.getLastChild();
        if (defParent.isConst()) {
          // If it is a const var we don't want to remove the rhs of the variable
          rhs.replaceWith(Node.newString(Token.NAME, "undefined"));
          use.replaceWith(rhs);
        } else {
          rhs.detach();
          use.replaceWith(rhs);
        }
      } else {
        throw new IllegalStateException("No other definitions can be inlined.");
      }
      changedScopes.add(checkNotNull(ChangeTracker.getEnclosingChangeScopeRoot(useParent)));
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
                case NAME -> {
                  if (n.getString().equals(varName) && n.hasChildren()) {
                    def = n;
                  }
                  return;
                }
                case ASSIGN -> {
                  Node lhs = n.getFirstChild();
                  if (lhs.isName() && lhs.getString().equals(varName)) {
                    def = n;
                  }
                  return;
                }
                default -> {}
              }
            }
          };
      NodeTraversal.traverse(compiler, n, gatherCb);
    }

    /** Counts name uses, sharing a single traversal when many candidates occupy one CFG node. */
    private int getNumUsesInCfgNode(final Node cfgNode) {
      if (candidateCountsByUseCfgNode.getOrDefault(cfgNode, 0) < 4) {
        return countUsesInCfgNode(cfgNode, varName);
      }
      Map<String, Integer> cached = useCountsByCfgNode.get(cfgNode);
      if (cached != null) {
        return cached.getOrDefault(varName, 0);
      }
      Map<String, Integer> useCounts = new HashMap<>();
      AbstractCfgNodeTraversalCallback gatherCb =
          new AbstractCfgNodeTraversalCallback() {

            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName()) {
                // We make a special exception when the entire cfgNode is a chain
                // of assignments, since in that case the assignment statements
                // will happen after the inlining of the right hand side.
                // TODO(lharker): We can probably remove the isAssignChain check, and instead use
                // the SideEffectPredicate to look for dangerous assignments in the same CFG node
                if (parent.isAssign() && (parent.getFirstChild() == n)
                    && isAssignChain(parent, cfgNode)) {
                  // Don't count lhs of top-level assignment chain
                  return;
                }
                useCounts.merge(n.getString(), 1, Integer::sum);
              }
            }
          };

      NodeTraversal.traverse(compiler, cfgNode, gatherCb);
      useCountsByCfgNode.put(cfgNode, useCounts);
      return useCounts.getOrDefault(varName, 0);
    }

    private int countUsesInCfgNode(final Node cfgNode, String name) {
      numUsesWithinCfgNode = 0;
      NodeTraversal.traverse(
          compiler,
          cfgNode,
          new AbstractCfgNodeTraversalCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName()
                  && n.getString().equals(name)
                  && !(parent.isAssign()
                      && parent.getFirstChild() == n
                      && isAssignChain(parent, cfgNode))) {
                numUsesWithinCfgNode++;
              }
            }
          });
      return numUsesWithinCfgNode;
    }

    private boolean isAssignChain(Node child, Node ancestor) {
      for (Node n = child; n != ancestor; n = n.getParent()) {
        if (!n.isAssign()) {
          return false;
        }
      }
      return true;
    }

    /**
     * Check if the definition we're considering inline has anything that makes inlining unsafe
     * (that hasn't already been caught).
     *
     * @param usageScope The scope we will inline the variable into.
     */
    private boolean isRhsSafeToInline(final Scope usageScope) {
      // Don't inline definitions with an R-Value that has:
      // 1) GETELEM, OPTCHAIN_GETELEM (e.g: foo?.['bar']), GETPROP, OPTCHAIN_GETPROP (e.g:
      // foo?.bar), CLASS, ARRAYLIT,
      // OBJECTLIT, REGEXP
      // 2) anything that creates a new object.
      // Example:
      // var x = a.b.c; j.c = 1; print(x);
      // Inlining print(a.b.c) is not safe - consider if j were an alias to a.b.
      if (!isInlineableRhsShape(def.getLastChild())) {
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
          (Node input) -> {
            if (input.isName()) {
              String name = input.getString();
              if (!name.isEmpty() && !usageScope.hasSlot(name)) {
                return true; // unsafe to inline.
              }
            }
            return false;
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
