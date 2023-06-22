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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.CodingConvention.Cache;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.nullness.Nullable;

/**
 * Compiler pass that computes function purity and annotates invocation nodes with those purities.
 *
 * <p>A function is pure if it has no outside visible side effects, and the result of the
 * computation does not depend on external factors that are beyond the control of the application;
 * repeated calls to the function should return the same value as long as global state hasn't
 * changed.
 *
 * <p>`Date.now` is an example of a function that has no side effects but is not pure.
 *
 * <p>Functions are not tracked individually but rather in aggregate by their name. This is because
 * it's impossible to determine exactly which function named "foo" is being called at a particular
 * site. Therefore, if <em>any</em> function "foo" has a particular side-effect, <em>all</em>
 * invocations "foo" are assumed to trigger it.
 *
 * <p>This pass could be greatly improved by proper tracking of locals within function bodies. Every
 * instance of the call to {@link NodeUtil#evaluatesToLocalValue(Node)} and {@link
 * NodeUtil#allArgsUnescapedLocal(Node)} do not actually take into account local variables. They
 * only assume literals, primitives, and operations on primitives are local.
 */
class PureFunctionIdentifier implements OptimizeCalls.CallGraphCompilerPass {
  // A prefix to differentiate property names from variable names.
  // TODO(nickreid): This pass could be made more efficient if props and variables were maintained
  // in separate datastructures. We wouldn't allocate a bunch of extra strings.
  private static final String PROP_NAME_PREFIX = ".";

  private final AbstractCompiler compiler;
  private final AstAnalyzer astAnalyzer;

  /**
   * Map of function names to the summary of the functions with that name.
   *
   * <p>Variable names are recorded as-is. Property names are prefixed with {@link
   * #PROP_NAME_PREFIX} to differentiate them from variable names.
   *
   * @see {@link AmbiguatedFunctionSummary}
   */
  private final Map<String, AmbiguatedFunctionSummary> summariesByName = new HashMap<>();

  /**
   * Mapping from function node to summaries for all names associated with that node.
   *
   * <p>This is a multimap because you can construct situations in which a function node has
   * multiple names, and therefore multiple associated summaries. For example:
   *
   * <pre>
   *   // Not enough type information to collapse/disambiguate properties on "staticMethod".
   *   SomeClass.staticMethod = function anotherName() {};
   *   OtherClass.staticMethod = function() { global++; }
   * </pre>
   *
   * <p>In this situation we want to keep the side effects for "staticMethod" which are "global"
   * separate from "anotherName". Hence the function node should point to the {@link
   * AmbiguatedFunctionSummary} for both "staticMethod" and "anotherName".
   *
   * <p>We could instead store a map of FUNCTION nodes to names, and then join that with the name of
   * names to infos. However, since names are 1:1 with infos, it's more effecient to "pre-join" in
   * this way.
   */
  private final Multimap<Node, AmbiguatedFunctionSummary> summariesForAllNamesOfFunctionByNode =
      ArrayListMultimap.create();

  // List of all function call sites. Storing them here during the function analysis traversal
  // prevents us from doing a second traversal to annotate them with side-effects. We can just
  // iterate the list.
  private final List<Node> allFunctionCalls = new ArrayList<>();

  /**
   * A graph linking the summary of a function callee to the summaries of its callers.
   *
   * <p>Each node represents an aggregate summary of every function with a particular name. The edge
   * values indicate the details of the invocation necessary to propagate function impurity from
   * callee to caller.
   *
   * <p>Once all the invocation edges are in place, this graph will be traversed to transitively
   * propagate side-effect information across it's entire structure. The resultant side-effects can
   * then be attached to invocation sites.
   */
  private final LinkedDirectedGraph<AmbiguatedFunctionSummary, SideEffectPropagation>
      reverseCallGraph = LinkedDirectedGraph.createWithoutAnnotations();

  /**
   * A summary for a function for which no definition was found.
   *
   * <p>We assume it has all possible side-effects. It's useful for references like function
   * parameters, or inner functions.
   */
  private final AmbiguatedFunctionSummary unknownFunctionSummary =
      AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<unknown>")
          .setMutatesGlobalStateAndAllOtherFlags();

  /**
   * A function node representing a function implicit in the AST that is known to be pure
   *
   * <p>For example: `class C {}` would get this node.
   */
  private static final Node IMPLICIT_PURE_FN = IR.function(IR.name(""), IR.paramList(), IR.block());

  private final boolean assumeGettersArePure;

  private boolean hasProcessed = false;

  public PureFunctionIdentifier(AbstractCompiler compiler, boolean assumeGettersArePure) {
    this.compiler = checkNotNull(compiler);
    this.assumeGettersArePure = assumeGettersArePure;
    this.astAnalyzer = compiler.getAstAnalyzer();
  }

  @Override
  public void process(Node externs, Node root, ReferenceMap references) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    checkState(
        !hasProcessed, "PureFunctionIdentifier::process may only be called once per instance.");
    this.hasProcessed = true;

    populateDatastructuresForAnalysisTraversal(references);

    NodeTraversal.traverse(compiler, externs, new ExternFunctionAnnotationAnalyzer());
    NodeTraversal.traverse(compiler, root, new FunctionBodyAnalyzer());

    propagateSideEffects();

    markPureFunctionCalls();
  }

  /**
   * Traverses an {@code expr} to collect nodes representing potential callables that it may resolve
   * to well known callables.
   *
   * @see {@link #collectCallableLeavesInternal}
   * @return the disovered callables, or {@code null} if an unexpected possible value was found.
   */
  private static @Nullable ImmutableList<Node> collectCallableLeaves(Node expr) {
    ArrayList<Node> callables = new ArrayList<>();
    boolean allLegal = collectCallableLeavesInternal(expr, callables);
    return allLegal ? ImmutableList.copyOf(callables) : null;
  }

  /**
   * Traverses an {@code expr} to collect nodes representing potential callables that it may resolve
   * to well known callables.
   *
   * <p>For example:
   *
   * <pre>
   *   `a.c || b` => [a.c, b]
   *   `x ? a.c : b` => [a.c, b]
   *   `(function f() { }) && x || class Foo { constructor() { } }` => [function, x, constructor]`
   * </pre>
   *
   * <p>This function is applicable to finding both assignment aliases and call targets. That is,
   * one way to find the possible callees of an invocation is to pass the complex expression
   * representing the final callee to this method.
   *
   * <p>If a node that isn't understood is detected, false is returned and the caller is expected to
   * invalidate the entire collection. If true is returned, this method is guaranteed to have added
   * at least one result to the collection.
   *
   * @see {@link #collectCallableLeaves}
   * @param expr A possibly complicated expression.
   * @param results The collection of qualified names and functions.
   * @return {@code true} iff only understood results were discovered.
   */
  private static boolean collectCallableLeavesInternal(Node expr, ArrayList<Node> results) {
    switch (expr.getToken()) {
      case FUNCTION:
      case GETPROP:
      case OPTCHAIN_GETPROP:
      case NAME:
        results.add(expr);
        return true;

      case SUPER:
        {
          // Pretend that `super` is an alias for the superclass reference.
          Node clazz = checkNotNull(NodeUtil.getEnclosingClass(expr));
          return collectCallableLeavesInternal(clazz.getSecondChild(), results);
        }

      case CLASS:
        {
          // Collect the constructor function, or failing that, the superclass reference.
          @Nullable Node ctorDef = NodeUtil.getEs6ClassConstructorMemberFunctionDef(expr);
          if (ctorDef != null) {
            return collectCallableLeavesInternal(ctorDef.getOnlyChild(), results);
          } else if (expr.getSecondChild().isEmpty()) {
            results.add(IMPLICIT_PURE_FN);
            return true; // A class an implicit ctor is pure when there is no superclass.
          } else {
            return collectCallableLeavesInternal(expr.getSecondChild(), results);
          }
        }

      case AND:
      case OR:
      case COALESCE:
        return collectCallableLeavesInternal(expr.getFirstChild(), results)
            && collectCallableLeavesInternal(expr.getSecondChild(), results);

      case COMMA:
      case ASSIGN:
        return collectCallableLeavesInternal(expr.getSecondChild(), results);

      case HOOK:
        return collectCallableLeavesInternal(expr.getSecondChild(), results)
            && collectCallableLeavesInternal(expr.getChildAtIndex(2), results);

      case NEW_TARGET:
      case THIS:
        // These could be an alias to any function. Treat them as an unknown callable.
      default:
        return false; // Unsupported call type.
    }
  }

  /**
   * Return {@code true} only if {@code rvalue} is defintely a reference reading a value.
   *
   * <p>For the most part it's sufficient to cover cases where a nominal function reference might
   * reasonably be expected, since those are the values that matter to analysis.
   *
   * <p>It's very important that this never returns {@code true} for an L-value, including when new
   * syntax is added to the language. That would cause some impure functions to be considered pure.
   * Therefore, this method is a very explict allowlist. Anything that's unrecognized is considered
   * not an R-value. This is insurance against new syntax.
   *
   * <p>New cases can be added as needed to increase the accuracy of the analysis. They just have to
   * be verified as always R-values.
   */
  private static boolean isDefinitelyRValue(Node rvalue) {
    Node parent = rvalue.getParent();

    switch (parent.getToken()) {
      case AND:
      case ARRAYLIT:
      case CALL:
      case COALESCE:
      case COMMA:
      case EQ:
      case GETELEM:
      case GETPROP:
      case HOOK:
      case INSTANCEOF:
      case NEW:
      case NOT:
      case NAME:
      case OPTCHAIN_CALL:
      case OPTCHAIN_GETELEM:
      case OPTCHAIN_GETPROP:
      case OR:
      case RETURN:
      case SHEQ:
      case TAGGED_TEMPLATELIT:
      case TYPEOF:
      case YIELD:
        return true;

      case CASE:
      case IF:
      case SWITCH:
      case WHILE:
        return rvalue.isFirstChildOf(parent);

      case EXPR_RESULT:
        // Extern declarations are sometimes stubs. These must be considered L-values with no
        // associated R-values.
        return !rvalue.isFromExterns();

      case ASSIGN:
      case CLASS: // `extends` clause.
        return rvalue.isSecondChildOf(parent);

      case STRING_KEY: // Assignment to an object literal property. Excludes object destructuring.
        return parent.getParent().isObjectLit();

      default:
        // Anything not explicitly listed may not be an R-value. We only worry about the likely
        // cases for nominal function values since those are what interest us and its safe to miss
        // some R-values. It's more important that we correctly identify L-values.
        return false;
    }
  }

  private ImmutableList<Node> getGoogCacheCallableExpression(Cache cacheCall) {
    checkNotNull(cacheCall);

    ImmutableList.Builder<Node> builder =
        ImmutableList.<Node>builder().addAll(collectCallableLeaves(cacheCall.valueFn));
    if (cacheCall.keyFn != null) {
      builder.addAll(collectCallableLeaves(cacheCall.keyFn));
    }
    return builder.build();
  }

  private ImmutableList<AmbiguatedFunctionSummary> getSummariesForCallee(Node invocation) {
    checkArgument(NodeUtil.isInvocation(invocation), invocation);

    Cache cacheCall = compiler.getCodingConvention().describeCachingCall(invocation);

    final ImmutableList<Node> callees;
    if (cacheCall != null) {
      callees = getGoogCacheCallableExpression(cacheCall);
    } else if (isInvocationViaCallOrApply(invocation)) {
      callees = ImmutableList.of(invocation.getFirstFirstChild());
    } else {
      callees = collectCallableLeaves(invocation.getFirstChild());
    }

    if (callees == null) {
      return ImmutableList.of(unknownFunctionSummary);
    }

    checkState(!callees.isEmpty(), "Unexpected empty callees for valid result");
    ImmutableList.Builder<AmbiguatedFunctionSummary> results = ImmutableList.builder();
    for (Node callee : callees) {
      if (callee.isFunction()) {
        checkState(callee.isFunction(), callee);

        Collection<AmbiguatedFunctionSummary> summariesForFunction =
            summariesForAllNamesOfFunctionByNode.get(callee);
        checkState(!summariesForFunction.isEmpty(), "Function missed during analysis: %s", callee);

        results.addAll(summariesForFunction);
      } else {
        String calleeName = nameForReference(callee);
        results.add(summariesByName.getOrDefault(calleeName, unknownFunctionSummary));
      }
    }
    return results.build();
  }

  /**
   * Fill all of the auxiliary data-structures used by this pass based on the results in {@code
   * referenceMap}.
   *
   * <p>This is the first step of analysis. These structures will be used by a traversal that
   * analyzes the bodies of located functions for side-effects. That traversal is separate because
   * it needs access to scopes and also depends on global knowledge of functions.
   */
  private void populateDatastructuresForAnalysisTraversal(ReferenceMap referenceMap) {
    // Merge the prop and name references into a single multimap since only the name matters.
    ArrayListMultimap<String, Node> referencesByName = ArrayListMultimap.create();
    for (Map.Entry<String, ? extends List<Node>> entry : referenceMap.getNameReferences()) {
      referencesByName.putAll(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, ? extends List<Node>> entry : referenceMap.getPropReferences()) {
      referencesByName.putAll(PROP_NAME_PREFIX + entry.getKey(), entry.getValue());
    }
    // Empty function names cause a crash during analysis that is better to detect here.
    // Additionally, functions require a name to be invoked in a statically analyzable way; there's
    // no value in tracking the set of anonymous functions.
    checkState(!referencesByName.containsKey(""));
    checkState(!referencesByName.containsKey(PROP_NAME_PREFIX));

    // Create and store a summary for all known names.
    for (String name : referencesByName.keySet()) {
      summariesByName.put(name, AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, name));
    }

    Multimaps.asMap(referencesByName).forEach(this::populateFunctionDefinitions);
    this.summariesForAllNamesOfFunctionByNode.put(
        IMPLICIT_PURE_FN,
        AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<implicit pure class ctor"));
  }

  /**
   * For a name and its set of references, record the set of functions that may define that name or
   * skiplist the name if there are unclear definitions.
   *
   * @param name A variable or property name,
   * @param references The set of all nodes representing R- and L-value references to {@code name}.
   */
  private void populateFunctionDefinitions(String name, List<Node> references) {
    AmbiguatedFunctionSummary summaryForName = checkNotNull(summariesByName.get(name));

    // Make sure we get absolutely every R-value assigned to `name` or at the very least detect
    // there are some we're missing. Overlooking a single R-value would invalidate the analysis.

    boolean invalid = false;
    List<ImmutableList<Node>> rvaluesAssignedToName = new ArrayList<>();
    for (Node reference : references) {
      // Eliminate any references that we're sure are R-values themselves. Otherwise
      // there's a high probability we'll inspect an R-value for futher R-values. We wouldn't
      // find any, and then we'd have to consider `name` impure.
      if (!isDefinitelyRValue(reference)) {
        // For anything that might be an L-reference, get the expression being assigned to it.
        Node rvalue = NodeUtil.getRValueOfLValue(reference);
        if (rvalue == null) {
          invalid = true;
          break;
        } else {
          // If the assigned R-value is an analyzable expression, collect all the possible
          // FUNCTIONs that could result from that expression. If the expression isn't analyzable,
          // represent that with `null` so we can skiplist `name`.
          ImmutableList<Node> callables = collectCallableLeaves(rvalue);
          if (callables == null) {
            invalid = true;
            break;
          }

          rvaluesAssignedToName.add(callables);
        }
      }
    }

    if (rvaluesAssignedToName.isEmpty() || invalid) {
      // Any of:
      // - There are no L-values with this name.
      // - There's a an L-value and we can't find the associated R-values.
      // - There's a an L-value with R-values are not all known to be callable.
      summaryForName.setMutatesGlobalStateAndAllOtherFlags();
    } else {
      for (ImmutableList<Node> callables : rvaluesAssignedToName) {
        for (Node rvalue : callables) {
          if (rvalue.isFunction()) {
            summariesForAllNamesOfFunctionByNode.put(rvalue, summaryForName);
          } else {
            String rvalueName = nameForReference(rvalue);
            AmbiguatedFunctionSummary rvalueSummary =
                summariesByName.getOrDefault(rvalueName, unknownFunctionSummary);

            reverseCallGraph.connect(
                rvalueSummary.graphNode,
                SideEffectPropagation.forAlias(),
                summaryForName.graphNode);
          }
        }
      }
    }
  }

  /**
   * Propagate side effect information in {@link #reverseCallGraph} from callees to callers.
   *
   * <p>This is an iterative process executed until a fixed point, where no caller summary would be
   * given new side-effects from from any callee summary, is reached.
   */
  private void propagateSideEffects() {
    FixedPointGraphTraversal.newTraversal(
            (AmbiguatedFunctionSummary source,
                SideEffectPropagation edge,
                AmbiguatedFunctionSummary destination) -> edge.propagate(source, destination))
        .computeFixedPoint(reverseCallGraph);
  }

  /** Set no side effect property at pure-function call sites. */
  private void markPureFunctionCalls() {
    for (Node callNode : allFunctionCalls) {
      ImmutableList<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(callNode);

      // Default to side effects, non-local results
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      if (calleeSummaries.isEmpty()) {
        flags.setAllFlags();
      } else {
        flags.clearAllFlags();
        for (AmbiguatedFunctionSummary calleeSummary : calleeSummaries) {
          checkNotNull(calleeSummary);
          if (calleeSummary.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }

          if (calleeSummary.mutatesArguments()) {
            flags.setMutatesArguments();
          }

          if (calleeSummary.functionThrows()) {
            flags.setThrows();
          }

          if (isCallOrTaggedTemplateLit(callNode)) {
            if (calleeSummary.mutatesThis()) {
              // A summary for "f" maps to both "f()" and "f.call()" nodes.
              if (isInvocationViaCallOrApply(callNode)) {
                flags.setMutatesArguments(); // `this` is actually an argument.
              } else {
                flags.setMutatesThis();
              }
            }
          }
        }
      }

      if (callNode.getFirstChild().isSuper()) {
        // All `super()` calls (i.e. from subclass constructors) implicitly mutate `this`; they
        // determine its value in the caller scope. Concretely, `super()` calls must not be removed
        // or reordered. Marking them this way ensures that without pinning the enclosing function.
        flags.setMutatesThis();
      }

      // Handle special cases (Math, RegExp)
      if (isCallOrTaggedTemplateLit(callNode)) {
        if (!astAnalyzer.functionCallHasSideEffects(callNode)) {
          flags.clearAllFlags();
        }
      } else if (callNode.isNew()) {
        // Handle known cases now (Object, Date, RegExp, etc)
        if (!astAnalyzer.constructorCallHasSideEffects(callNode)) {
          flags.clearAllFlags();
        }
      }

      if (callNode.getSideEffectFlags() != flags.valueOf()) {
        callNode.setSideEffectFlags(flags);
        compiler.reportChangeToEnclosingScope(callNode);
      }
    }
  }

  /**
   * Inspects function JSDoc for side effects and applies them to the associated {@link
   * AmbiguatedFunctionSummary}.
   *
   * <p>This callback is only meant for use on externs.
   */
  private final class ExternFunctionAnnotationAnalyzer implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      if (!node.isFunction()) {
        return;
      }

      for (AmbiguatedFunctionSummary definitionSummary :
          summariesForAllNamesOfFunctionByNode.get(node)) {
        updateSideEffectsForExternFunction(node, definitionSummary);
      }
    }

    private void updateSideEffectsForExternFunction(
        Node externFunction, AmbiguatedFunctionSummary summary) {
      checkArgument(externFunction.isFunction());
      checkArgument(externFunction.isFromExterns());

      JSDocInfo info = NodeUtil.getBestJSDocInfo(externFunction);
      if (info == null) {
        // We don't know anything about this function so we assume it has side effects.
        summary.setMutatesGlobalStateAndAllOtherFlags();
        return;
      }

      if (info.modifiesThis()) {
        summary.setMutatesThis();
      }
      if (info.hasSideEffectsArgumentsAnnotation()) {
        summary.setMutatesArguments();
      }
      if (!info.getThrowsAnnotations().isEmpty()) {
        summary.setThrows();
      }

      if (!info.isNoSideEffects() && summary.hasNoFlagsSet()) {
        // We don't know anything about this function so we assume it has side effects.
        summary.setMutatesGlobalStateAndAllOtherFlags();
      }
    }
  }

  private static final Predicate<Node> RHS_IS_ALWAYS_LOCAL = lhs -> true;
  private static final Predicate<Node> RHS_IS_NEVER_LOCAL = lhs -> false;
  private static final Predicate<Node> FIND_RHS_AND_CHECK_FOR_LOCAL_VALUE =
      lhs -> {
        Node rhs = NodeUtil.getRValueOfLValue(lhs);
        return rhs == null || NodeUtil.evaluatesToLocalValue(rhs);
      };

  /**
   * Inspects function bodies for side effects and applies them to the associated {@link
   * AmbiguatedFunctionSummary}.
   *
   * <p>This callback also fills {@link #allFunctionCalls}
   */
  private final class FunctionBodyAnalyzer implements ScopedCallback {

    // Preloaded with an entry to represent the global scope.
    private final ArrayDeque<FunctionStackEntry> functionScopeStack =
        new ArrayDeque<>(ImmutableList.of(new FunctionStackEntry(null)));

    final class FunctionStackEntry {
      final Node root;
      final LinkedHashSet<Var> skiplistedVars = new LinkedHashSet<>();
      final LinkedHashSet<Var> taintedVars = new LinkedHashSet<>();
      int catchDepth = 0; // The number of try-catch blocks around the current node.

      FunctionStackEntry(Node root) {
        this.root = root;
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      this.addToCatchDepthIfTryBlock(node, 1);
      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      this.addToCatchDepthIfTryBlock(node, -1);

      if (NodeUtil.isInvocation(node)) {
        // We collect these after filtering for side-effects because there's no point re-processing
        // a known pure call. This analysis is run multiple times, but no optimization will make a
        // pure function impure.
        allFunctionCalls.add(node);
      }

      if (node.isFunction()) {
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(node);
        if (jsdoc != null && jsdoc.isNoSideEffects()) {
          // Treat all names (both local aliases and exported names) as if they don't have side
          // effects.
          for (AmbiguatedFunctionSummary summary : summariesForAllNamesOfFunctionByNode.get(node)) {
            summary.setIsArtificiallyPure(true);
            summary.bitmask = 0; // no side effects
          }
        }
      }

      Node root = this.functionScopeStack.getLast().root;
      if (root != null) {
        for (AmbiguatedFunctionSummary summary : summariesForAllNamesOfFunctionByNode.get(root)) {
          if (summary.isArtificiallyPure()) {
            // Ignore node side effects for summaries that have been marked as artificially pure.
            // We can't skip traversing the nodes in case an artificially pure node contains other
            // function definitions.
            continue;
          }
          updateSideEffectsForNode(checkNotNull(summary), traversal, node);
        }
      }
    }

    /**
     * Updates the side effects of summary based on a given node.
     *
     * <p>This node should be known to (possibly have) side effects. This method does not check if
     * the node (possibly) has side effects.
     */
    private void updateSideEffectsForNode(
        AmbiguatedFunctionSummary encloserSummary, NodeTraversal traversal, Node node) {
      if (encloserSummary.mutatesGlobalState()) {
        return; // Functions with MUTATES_GLOBAL_STATE already have all side-effects set.
      }

      switch (node.getToken()) {
        case ASSIGN:
          // e.g.
          // lhs = rhs;
          // ({x, y} = object);
          Node lhs = node.getFirstChild();
          // Consider destructured properties or values to be nonlocal.
          Predicate<Node> rhsLocality =
              lhs.isDestructuringPattern()
                  ? RHS_IS_NEVER_LOCAL
                  : FIND_RHS_AND_CHECK_FOR_LOCAL_VALUE;
          NodeUtil.visitLhsNodesInNode(
              node,
              (lhsNode) ->
                  visitLhsNode(encloserSummary, traversal.getScope(), lhsNode, rhsLocality));
          break;

        case INC: // e.g. x++;
        case DEC:
        case DELPROP:
          visitLhsNode(
              encloserSummary,
              traversal.getScope(),
              node.getOnlyChild(),
              // The value assigned by a unary op is always local.
              RHS_IS_ALWAYS_LOCAL);
          break;

        case FOR_AWAIT_OF:
          deprecatedSetSideEffectsForControlLoss(encloserSummary); // Control is lost during await.
          // Fall through.
        case FOR_OF:
          // e.g.
          // for (const {prop1, prop2} of iterable) {...}
          // for ({prop1: x.p1, prop2: x.p2} of iterable) {...}
          NodeUtil.visitLhsNodesInNode(
              node,
              (lhsNode) ->
                  visitLhsNode(
                      encloserSummary,
                      traversal.getScope(),
                      lhsNode,
                      // The RHS of a for-of must always be an iterable, making it a container, so
                      // we can't
                      // consider its contents to be local
                      RHS_IS_NEVER_LOCAL));
          checkIteratesImpureIterable(node, encloserSummary);
          break;

        case FOR_IN:
          // e.g.
          // for (prop in obj) {...}
          // Also this, though not very useful or readable.
          // for ([char1, char2, ...x.rest] in obj) {...}
          NodeUtil.visitLhsNodesInNode(
              node,
              (lhsNode) ->
                  visitLhsNode(
                      encloserSummary,
                      traversal.getScope(),
                      lhsNode,
                      // A for-in always assigns a string, which is a local value by definition.
                      RHS_IS_ALWAYS_LOCAL));
          break;

        case OPTCHAIN_CALL:
        case CALL:
        case NEW:
        case TAGGED_TEMPLATELIT:
          visitCall(encloserSummary, node);
          break;

        case DESTRUCTURING_LHS:
          if (NodeUtil.isAnyFor(node.getParent())) {
            // This case is handled when visiting the enclosing for loop.
            break;
          }
          // Assume the value assigned to each item is potentially global state. This is overly
          // conservative but necessary because in the common case the rhs is not a literal.
          NodeUtil.visitLhsNodesInNode(
              node.getParent(),
              (lhsNode) ->
                  visitLhsNode(encloserSummary, traversal.getScope(), lhsNode, RHS_IS_NEVER_LOCAL));
          break;

        case NAME:
          // Local variable declarations are not a side-effect, but we do want to track them.
          if (NodeUtil.isNameDeclaration(node.getParent())) {
            Node value = node.getFirstChild();
            // Assignment to local, if the value isn't a safe local value,
            // new object creation or literal or known primitive result
            // value, add it to the local skiplist.
            if (value != null && !NodeUtil.evaluatesToLocalValue(value)) {
              Scope scope = traversal.getScope();
              Var var = scope.getVar(node.getString());
              this.functionScopeStack.getLast().skiplistedVars.add(var);
            }
          }
          break;

        case THROW:
          this.recordThrowsBasedOnContext(encloserSummary);
          break;

        case YIELD:
          checkIteratesImpureIterable(node, encloserSummary); // `yield*` triggers iteration.
          // 'yield' throws if the caller calls `.throw` on the generator object.
          deprecatedSetSideEffectsForControlLoss(encloserSummary);
          break;

        case AWAIT:
          // 'await' throws if the promise it's waiting on is rejected.
          deprecatedSetSideEffectsForControlLoss(encloserSummary);
          break;

        case OBJECT_REST:
        case OBJECT_SPREAD:
          if (!assumeGettersArePure) {
            // May trigger a getter.
            encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
          }
          break;

        case ITER_REST:
        case ITER_SPREAD:
          checkIteratesImpureIterable(node, encloserSummary);
          break;

        case STRING_KEY:
          if (node.getParent().isObjectPattern()) {
            // This is an l-value STRING_KEY.
            // Assumption: GETELEM (via a COMPUTED_PROP) is never side-effectful.
            if (getPropertyKind(node.getString()).hasGetter()) {
              encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
            }
          }
          break;

        case OPTCHAIN_GETPROP:
        case GETPROP:
          // Assumption: GETELEM and OPTCHAIN_GETELEM are never side-effectful.
          if (getPropertyKind(node.getString()).hasGetterOrSetter()) {
            encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
          }
          break;

        case DYNAMIC_IMPORT:
          // Modules may be imported for side-effects only. This is frequently
          // a pattern used to load polyfills.
          encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
          break;

        default:
          if (NodeUtil.isCompoundAssignmentOp(node)) {
            // e.g.
            // x += 3;
            visitLhsNode(
                encloserSummary,
                traversal.getScope(),
                node.getFirstChild(),
                // The update assignments (e.g. `+=) always assign primitive, and therefore local,
                // values.
                RHS_IS_ALWAYS_LOCAL);
            break;
          }

          if (compiler.getAstAnalyzer().nodeTypeMayHaveSideEffects(node)) {
            throw new IllegalArgumentException("Unhandled side effect node type " + node);
          }
      }
    }

    /**
     * Inspect {@code node} for impure iteration and assign the appropriate side-effects to {@code
     * encloserSummary} if so.
     */
    private void checkIteratesImpureIterable(Node node, AmbiguatedFunctionSummary encloserSummary) {
      if (!NodeUtil.iteratesImpureIterable(node)) {
        return;
      }
      encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
    }

    /**
     * Assigns the set of side-effects associated with an arbitrary loss of control flow to {@code
     * encloserSummary}.
     *
     * <p>This function is kept to retain behaviour but marks places where the analysis is
     * inaccurate.
     *
     * @see b/135475880
     */
    private void deprecatedSetSideEffectsForControlLoss(AmbiguatedFunctionSummary encloserSummary) {
      this.recordThrowsBasedOnContext(encloserSummary);
    }

    private void recordThrowsBasedOnContext(AmbiguatedFunctionSummary encloserSummary) {
      if (this.functionScopeStack.getLast().catchDepth == 0) {
        encloserSummary.setThrows();
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      if (!t.getScope().isFunctionScope()) {
        return;
      }

      Node function = t.getScopeRoot();
      checkState(function.isFunction(), function);

      this.functionScopeStack.addLast(new FunctionStackEntry(function));
      if (!summariesForAllNamesOfFunctionByNode.containsKey(function)) {
        // This function was not part of a definition which is why it was not created by
        // {@link populateDatastructuresForAnalysisTraversal}. For example, an anonymous
        // function.
        AmbiguatedFunctionSummary summary =
            AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<anonymous>");
        summariesForAllNamesOfFunctionByNode.put(function, summary);
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      // We want to process block scope as well as function scopes
      Scope functionScope = t.getScope().getClosestContainerScope();
      if (!functionScope.isFunctionScope()) {
        return;
      }

      FunctionStackEntry functionEntry = this.functionScopeStack.getLast();
      checkState(
          functionScope.getRootNode().equals(functionEntry.root), functionScope.getRootNode());
      if (t.getScopeRoot().equals(functionEntry.root)) {
        this.functionScopeStack.removeLast();
      }

      // Handle deferred local variable modifications:
      for (AmbiguatedFunctionSummary sideEffectInfo :
          summariesForAllNamesOfFunctionByNode.get(functionEntry.root)) {
        checkNotNull(sideEffectInfo, "%s has no side effect info.", functionEntry.root);

        if (sideEffectInfo.mutatesGlobalState()) {
          continue;
        }

        for (Var v : t.getScope().getVarIterable()) {
          boolean isFromDestructuring = NodeUtil.isLhsByDestructuring(v.getNameNode());
          if (v.isParam()
              // Ignore destructuring parameters because they don't directly correspond to an
              // argument passed to the function for the purposes of "setMutatesArguments"
              && !isFromDestructuring
              && !functionEntry.skiplistedVars.contains(v)
              && functionEntry.taintedVars.contains(v)) {
            sideEffectInfo.setMutatesArguments();
            continue;
          }

          boolean localVar = false;
          // Parameters and catch values can come from other scopes.
          if (!v.isParam() && !v.isCatch()) {
            // TODO(johnlenz): create a useful parameter list
            // sideEffectInfo.addKnownLocal(v.getName());
            localVar = true;
          }

          // Take care of locals that might have been tainted.
          if (!localVar || functionEntry.skiplistedVars.contains(v)) {
            if (functionEntry.taintedVars.contains(v)) {
              // If the function has global side-effects
              // don't bother with the local side-effects.
              sideEffectInfo.setMutatesGlobalStateAndAllOtherFlags();
              break;
            }
          }
        }
      }
    }

    private boolean isVarDeclaredInSameContainerScope(@Nullable Var v, Scope scope) {
      return v != null && v.getScope().hasSameContainerScope(scope);
    }

    /**
     * Record information about the side effects caused by assigning a value to a given LHS.
     *
     * <p>If the operation modifies this or taints global state, mark the enclosing function as
     * having those side effects.
     *
     * @param encloserSummary Function side effect record to be updated
     * @param scope variable scope in which the variable assignment occurs
     * @param lhsNodes LHS nodes that are all assigned values by a given parent node
     * @param hasLocalRhs Predicate indicating whether a given LHS is being assigned a local value
     */
    private void visitLhsNode(
        AmbiguatedFunctionSummary encloserSummary,
        Scope scope,
        Node lhs,
        Predicate<Node> hasLocalRhs) {
      if (NodeUtil.isNormalOrOptChainGet(lhs)) {
        // Although OPTCHAIN_GETPROP can not be an LHS of an assign, it can be a child to DELPROP.
        // e.g. `delete obj?.prop` <==> `obj == null ?  true : delete obj.prop;`
        // Hence the enclosing function's side effects must be recorded.
        if (lhs.getFirstChild().isThis()) {
          encloserSummary.setMutatesThis();
        } else {
          Node objectNode = lhs.getFirstChild();
          if (objectNode.isName()) {
            Var var = scope.getVar(objectNode.getString());
            if (isVarDeclaredInSameContainerScope(var, scope)) {
              // Maybe a local object modification.  We won't know for sure until
              // we exit the scope and can validate the value of the local.
              this.functionScopeStack.getLast().taintedVars.add(var);
            } else {
              encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
            }
          } else {
            // Don't track multi level locals: local.prop.prop2++;
            encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
          }
        }
      } else {
        checkState(lhs.isName(), lhs);
        Var var = scope.getVar(lhs.getString());
        if (isVarDeclaredInSameContainerScope(var, scope)) {
          if (!hasLocalRhs.test(lhs)) {
            // Assigned value is not guaranteed to be a local value,
            // so if we see any property assignments on this variable,
            // they could be tainting a non-local value.
            this.functionScopeStack.getLast().skiplistedVars.add(var);
          }
        } else {
          encloserSummary.setMutatesGlobalStateAndAllOtherFlags();
        }
      }
    }

    /** Record information about a call site. */
    private void visitCall(AmbiguatedFunctionSummary callerInfo, Node invocation) {
      // Handle special cases (Math, RegExp)
      // TODO: This logic can probably be replaced with @nosideeffects annotations in externs.
      if (invocation.isCall() && !astAnalyzer.functionCallHasSideEffects(invocation)) {
        return;
      }

      // Handle known cases now (Object, Date, RegExp, etc)
      if (invocation.isNew() && !astAnalyzer.constructorCallHasSideEffects(invocation)) {
        return;
      }

      ImmutableList<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(invocation);
      if (calleeSummaries.isEmpty()) {
        callerInfo.setMutatesGlobalStateAndAllOtherFlags();
        return;
      }

      boolean propatesThrows = this.functionScopeStack.getLast().catchDepth == 0;
      for (AmbiguatedFunctionSummary calleeInfo : calleeSummaries) {
        SideEffectPropagation edge =
            SideEffectPropagation.forInvocation(invocation, propatesThrows);
        reverseCallGraph.connect(calleeInfo.graphNode, edge, callerInfo.graphNode);
      }
    }

    private void addToCatchDepthIfTryBlock(Node n, int delta) {
      Node parent = n.getParent();
      if (!n.isBlock() || !parent.isTry() || !n.isFirstChildOf(parent)) {
        return;
      }

      Node jsCatch = n.getNext().getFirstChild();
      if (jsCatch == null) {
        return;
      }

      this.functionScopeStack.getLast().catchDepth += delta;
    }
  }

  private static boolean isInvocationViaCallOrApply(Node callSite) {
    Node receiver = callSite.getFirstFirstChild();
    if (receiver == null
        || !(receiver.isName() || receiver.isGetProp() || receiver.isOptChainGetProp())) {
      return false;
    }

    return NodeUtil.isFunctionObjectCall(callSite) || NodeUtil.isFunctionObjectApply(callSite);
  }

  private static boolean isCallOrTaggedTemplateLit(Node invocation) {
    return invocation.isCall() || invocation.isOptChainCall() || invocation.isTaggedTemplateLit();
  }

  /**
   * Returns the unqualified name associated with an R-value.
   *
   * <p>For NAMEs this is the name. For GETPROPs this is the last segment including a leading dot.
   */
  private static @Nullable String nameForReference(Node nameRef) {
    switch (nameRef.getToken()) {
      case NAME:
        return nameRef.getString();
      case GETPROP:
      case OPTCHAIN_GETPROP:
        return PROP_NAME_PREFIX + nameRef.getString();
      default:
        throw new IllegalStateException("Unexpected name reference: " + nameRef);
    }
  }

  private PropertyAccessKind getPropertyKind(String name) {
    return assumeGettersArePure
        ? PropertyAccessKind.NORMAL
        : compiler.getAccessorSummary().getKind(name);
  }

  /**
   * This class stores all the information about a connection between functions needed to propagate
   * side effects from one instance of {@link AmbiguatedFunctionSummary} to another.
   */
  private static class SideEffectPropagation {

    // Whether this propagation represents an aliasing of one name by another. In that case, all
    // side effects of the "callee" just need to be copied onto the "caller".
    private final boolean callerIsAlias;

    // If all the arguments passed to the callee are local to the caller.
    private final boolean allArgsUnescapedLocal;

    /*
     * If you call a function with apply or call, one of the arguments at the call site will be used
     * as 'this' inside the implementation. If this is pass into apply like so: function.apply(this,
     * ...) then 'this' in the caller is tainted.
     */
    private final boolean calleeThisEqualsCallerThis;

    /**
     * Whether this propagation includes the "throws" bit.
     *
     * <p>In some contexts, such as an invocation inside a "try", the caller is uneffected by the
     * callee throwing.
     */
    private final boolean propagateThrows;

    // The token used to invoke the callee by the caller.
    private final @Nullable Node invocation;

    private SideEffectPropagation(
        boolean callerIsAlias,
        boolean allArgsUnescapedLocal,
        boolean calleeThisEqualsCallerThis,
        boolean propagateThrows,
        @Nullable Node invocation) {
      checkArgument(invocation == null || NodeUtil.isInvocation(invocation), invocation);

      this.callerIsAlias = callerIsAlias;
      this.allArgsUnescapedLocal = allArgsUnescapedLocal;
      this.calleeThisEqualsCallerThis = calleeThisEqualsCallerThis;
      this.propagateThrows = propagateThrows;
      this.invocation = invocation;
    }

    static SideEffectPropagation forAlias() {
      return new SideEffectPropagation(true, false, false, true, null);
    }

    static SideEffectPropagation forInvocation(Node invocation, boolean propagateThrows) {
      checkArgument(NodeUtil.isInvocation(invocation), invocation);

      return new SideEffectPropagation(
          false,
          NodeUtil.allArgsUnescapedLocal(invocation),
          calleeAndCallerShareThis(invocation),
          propagateThrows,
          invocation);
    }

    private static boolean calleeAndCallerShareThis(Node invocation) {
      if (!isCallOrTaggedTemplateLit(invocation)) {
        return false; // Calling a constructor creates a new object bound to `this`.
      }

      Node callee = invocation.getFirstChild();
      if (callee.isSuper()) {
        return true;
      }

      final @Nullable Node thisArg;
      if (isInvocationViaCallOrApply(invocation)) {
        // If the call site is actually a `.call` or `.apply`, then `this` will be an argument.
        thisArg = invocation.getSecondChild();
      } else if (callee.isGetProp() || callee.isOptChainGetProp()) {
        thisArg = callee.getFirstChild();
      } else {
        thisArg = null;
      }

      if (thisArg == null) {
        return false; // No `this` is being passed.
      } else if (thisArg.isThis() || thisArg.isSuper()) {
        return true;
      }

      // TODO(nickreid): If `thisArg` is a known local or known arg we could say something more
      // specific about the effect of the callee mutating `this`.

      // We're not sure what `this` is being passed, so make a conservative choice.
      return false;
    }

    /**
     * Propagate the side effects from the callee to the caller.
     *
     * @param callee propagate from
     * @param caller propagate to
     * @return Returns true if the propagation changed the side effects on the caller.
     */
    boolean propagate(AmbiguatedFunctionSummary callee, AmbiguatedFunctionSummary caller) {
      if (callee.isArtificiallyPure() || caller.isArtificiallyPure()) {
        // Pure callees should never propagate their side effects to their callers
        //   - This would already happen - this condition is just a shortcut.
        // Pure callers should never receive side effects propagated from their callees
        return false;
      }
      int initialCallerFlags = caller.bitmask;

      if (callerIsAlias) {
        caller.setMask(callee.bitmask);
        return caller.bitmask != initialCallerFlags;
      }

      if (callee.mutatesGlobalState()) {
        // If the callee modifies global state then so does that caller.
        caller.setMutatesGlobalStateAndAllOtherFlags();
      }
      if (this.propagateThrows && callee.functionThrows()) {
        // If the callee throws an exception then so does the caller.
        caller.setThrows();
      }
      if (callee.mutatesArguments() && !allArgsUnescapedLocal) {
        // If the callee mutates its input arguments and the arguments escape the caller then it has
        // unbounded side effects.
        caller.setMutatesGlobalStateAndAllOtherFlags();
      }
      if (callee.mutatesThis()) {
        if (invocation.isNew()) {
          // NEWing a constructor provide a unescaped "this" making side-effects impossible.
        } else if (calleeThisEqualsCallerThis) {
          caller.setMutatesThis();
        } else {
          caller.setMutatesGlobalStateAndAllOtherFlags();
        }
      }

      return caller.bitmask != initialCallerFlags;
    }
  }

  /**
   * A summary for the set of functions that share a particular name.
   *
   * <p>Side-effects of the functions are the most significant aspect of this summary. Because the
   * functions are "ambiguated", the recorded side-effects are the union of all side effects
   * detected in any member of the set.
   *
   * <p>Name in this context refers to a short name, not a qualified name; only the last segment of
   * a qualified name is used.
   */
  private static final class AmbiguatedFunctionSummary {

    // Side effect types:
    private static final int THROWS = 1 << 0;
    private static final int MUTATES_GLOBAL_STATE = 1 << 1;
    private static final int MUTATES_THIS = 1 << 2;
    private static final int MUTATES_ARGUMENTS = 1 << 3;

    // The name shared by the set of functions that defined this summary.
    private final String name;
    // The node holding this summary in the reverse call graph.
    private final DiGraphNode<AmbiguatedFunctionSummary, SideEffectPropagation> graphNode;
    // The side effect flags for this set of functions.
    // TODO(nickreid): Replace this with a `Node.SideEffectFlags`.
    private int bitmask = 0;
    private boolean isArtificiallyPure = false;

    /** Adds a new summary node to {@code graph}, storing the node and returning the summary. */
    static AmbiguatedFunctionSummary createInGraph(
        DiGraph<AmbiguatedFunctionSummary, SideEffectPropagation> graph, String name) {
      return new AmbiguatedFunctionSummary(graph, name);
    }

    private AmbiguatedFunctionSummary(
        DiGraph<AmbiguatedFunctionSummary, SideEffectPropagation> graph, String name) {
      this.name = checkNotNull(name);
      this.graphNode = graph.createNode(this);
    }

    @CanIgnoreReturnValue
    private AmbiguatedFunctionSummary setMask(int mask) {
      checkState(!isArtificiallyPure, "Artificially pure summaries should not be modified");
      bitmask |= mask;
      return this;
    }

    private boolean getMask(int mask) {
      return (bitmask & mask) != 0;
    }

    boolean mutatesThis() {
      // MUTATES_GLOBAL_STATE implies MUTATES_THIS
      return getMask(MUTATES_THIS);
    }

    /** Marks the function as having "modifies this" side effects. */
    AmbiguatedFunctionSummary setMutatesThis() {
      return setMask(MUTATES_THIS);
    }

    /** Returns true if function has an explicit "throw". */
    boolean functionThrows() {
      // MUTATES_GLOBAL_STATE implies THROWS
      return getMask(THROWS);
    }

    /** Marks the function as having "throw" side effects. */
    AmbiguatedFunctionSummary setThrows() {
      return setMask(THROWS);
    }

    /** Returns true if function mutates global state. */
    boolean mutatesGlobalState() {
      return getMask(MUTATES_GLOBAL_STATE);
    }

    /** Marks the function as having "modifies globals" side effects. */
    AmbiguatedFunctionSummary setMutatesGlobalStateAndAllOtherFlags() {
      return setMask(THROWS | MUTATES_THIS | MUTATES_ARGUMENTS | MUTATES_GLOBAL_STATE);
    }

    /** Returns true if function mutates its arguments. */
    boolean mutatesArguments() {
      // MUTATES_GLOBAL_STATE implies MUTATES_ARGUMENTS
      return getMask(MUTATES_ARGUMENTS);
    }

    /** Marks the function as having "modifies arguments" side effects. */
    AmbiguatedFunctionSummary setMutatesArguments() {
      return setMask(MUTATES_ARGUMENTS);
    }

    boolean hasNoFlagsSet() {
      return this.bitmask == 0;
    }

    void setIsArtificiallyPure(boolean isArtificiallyPure) {
      this.isArtificiallyPure = isArtificiallyPure;
    }

    boolean isArtificiallyPure() {
      return isArtificiallyPure;
    }

    @Override
    @DoNotCall // For debugging only.
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("name", name)
          // Passing `graphNode` directly causes recursion as its `toString` calls `toString` on the
          // summary it contains.
          .add("graphNode", graphNode.hashCode())
          .add("sideEffects", sideEffectsToString())
          .toString();
    }

    private String sideEffectsToString() {
      List<String> status = new ArrayList<>();
      if (mutatesThis()) {
        status.add("this");
      }

      if (mutatesGlobalState()) {
        status.add("global");
      }

      if (mutatesArguments()) {
        status.add("args");
      }

      if (functionThrows()) {
        status.add("throw");
      }

      return status.toString();
    }
  }

  /**
   * A compiler pass that constructs a reference graph and drives the PureFunctionIdentifier across
   * it.
   */
  static final class Driver implements CompilerPass {
    private final AbstractCompiler compiler;

    Driver(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      OptimizeCalls.builder()
          .setCompiler(compiler)
          .setConsiderExterns(true)
          .addPass(
              new PureFunctionIdentifier(compiler, compiler.getOptions().getAssumeGettersArePure()))
          .build()
          .process(externs, root);
    }
  }
}
