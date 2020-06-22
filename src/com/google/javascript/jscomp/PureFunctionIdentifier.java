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
import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.CodingConvention.Cache;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

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
      AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<unknown>").setAllFlags();

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
  @Nullable
  private static ImmutableList<Node> collectCallableLeaves(Node expr) {
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
   * <p>This function uses a white-list approach. If a node that isn't understood is detected, the
   * entire collection is invalidated.
   *
   * @see {@link #collectCallableLeaves}
   * @param exp A possibly complicated expression.
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
          Node function = checkNotNull(NodeUtil.getEnclosingFunction(expr));
          Node ctorDef = checkNotNull(NodeUtil.getEs6ClassConstructorMemberFunctionDef(clazz));

          // The only place SUPER should be a callable expression is in a class ctor.
          checkState(
              function.isFirstChildOf(ctorDef), "Unknown SUPER reference: %s", expr.toStringTree());
          return collectCallableLeavesInternal(clazz.getSecondChild(), results);
        }

      case CLASS:
        {
          // Collect the constructor function, or failing that, the superclass reference.
          @Nullable Node ctorDef = NodeUtil.getEs6ClassConstructorMemberFunctionDef(expr);
          if (ctorDef != null) {
            return collectCallableLeavesInternal(ctorDef.getOnlyChild(), results);
          } else if (expr.getSecondChild().isEmpty()) {
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
   * Therefore, this method is a very explict allowed. Anything that's unrecognized is considered
   * not an R-value. This is insurance against new syntax.
   *
   * <p>New cases can be added as needed to increase the accuracy of the analysis. They just have to
   * be verified as always R-values.
   */
  private static boolean isDefinitelyRValue(Node rvalue) {
    Node parent = rvalue.getParent();

    switch (parent.getToken()) {
      case AND:
      case COMMA:
      case HOOK:
      case OR:
      case COALESCE:
        // Function values pass through conditionals.
      case EQ:
      case NOT:
      case SHEQ:
        // Functions can be usefully compared for equality / existence.
      case ARRAYLIT:
      case CALL:
      case OPTCHAIN_CALL:
      case NEW:
      case TAGGED_TEMPLATELIT:
        // Functions are the callees and parameters of an invocation.
      case INSTANCEOF:
      case TYPEOF:
        // Often used to determine if a ctor/method exists/matches.
      case GETELEM:
      case OPTCHAIN_GETELEM:
      case GETPROP:
      case OPTCHAIN_GETPROP:
        // Many functions, especially ctors, have properties.
      case RETURN:
      case YIELD:
        // Higher order functions return functions.
        return true;

      case SWITCH:
      case CASE:
        // Delegating on the identity of a function.
      case IF:
      case WHILE:
        // Checking the existence of an optional function.
        return rvalue.isFirstChildOf(parent);

      case EXPR_RESULT:
        // Extern declarations are sometimes stubs. These must be considered L-values with no
        // associated R-values.
        return !rvalue.isFromExterns();

      case CLASS: // `extends` clause.
      case ASSIGN:
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
    List<ImmutableList<Node>> rvaluesAssignedToName =
        references.stream()
            // Eliminate any references that we're sure are R-values themselves. Otherwise
            // there's a high probability we'll inspect an R-value for futher R-values. We wouldn't
            // find any, and then we'd have to consider `name` impure.
            .filter((n) -> !isDefinitelyRValue(n))
            // For anything that might be an L-reference, get the expression being assigned to it.
            .map(NodeUtil::getRValueOfLValue)
            // If the assigned R-value is an analyzable expression, collect all the possible
            // FUNCTIONs that could result from that expression. If the expression isn't analyzable,
            // represent that with `null` so we can skiplist `name`.
            .map((n) -> (n == null) ? null : collectCallableLeaves(n))
            .collect(toList());

    if (rvaluesAssignedToName.isEmpty() || rvaluesAssignedToName.contains(null)) {
      // Any of:
      // - There are no L-values with this name.
      // - There's a an L-value and we can't find the associated R-values.
      // - There's a an L-value with R-values are not all known to be callable.
      summaryForName.setAllFlags();
    } else {
      rvaluesAssignedToName.stream()
          .flatMap(List::stream)
          .forEach(
              (rvalue) -> {
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
              });
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
      List<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(callNode);
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

          if (calleeSummary.escapedReturn()) {
            flags.setReturnsTainted();
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
          flags.clearSideEffectFlags();
        }
      } else if (callNode.isNew()) {
        // Handle known cases now (Object, Date, RegExp, etc)
        if (!astAnalyzer.constructorCallHasSideEffects(callNode)) {
          flags.clearSideEffectFlags();
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
  private final class ExternFunctionAnnotationAnalyzer implements Callback {
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

    /** Update function for @nosideeffects annotations. */
    private void updateSideEffectsForExternFunction(
        Node externFunction, AmbiguatedFunctionSummary summary) {
      checkArgument(externFunction.isFunction());
      checkArgument(externFunction.isFromExterns());

      JSDocInfo info = NodeUtil.getBestJSDocInfo(externFunction);
      // Handle externs.
      JSType typei = externFunction.getJSType();
      FunctionType functionType = typei == null ? null : typei.toMaybeFunctionType();
      if (functionType == null) {
        // Assume extern functions return tainted values when we have no type info to say otherwise.
        summary.setEscapedReturn();
      } else {
        JSType retType = functionType.getReturnType();
        if (!isLocalValueType(retType, compiler)) {
          summary.setEscapedReturn();
        }
      }

      if (info == null) {
        // We don't know anything about this function so we assume it has side effects.
        summary.setMutatesGlobalState();
        summary.setFunctionThrows();
      } else {
        if (info.modifiesThis()) {
          summary.setMutatesThis();
        } else if (info.hasSideEffectsArgumentsAnnotation()) {
          summary.setMutatesArguments();
        } else if (!info.getThrownTypes().isEmpty()) {
          summary.setFunctionThrows();
        } else if (info.isNoSideEffects()) {
          // Do nothing.
        } else {
          summary.setMutatesGlobalState();
        }
      }
    }

    /**
     * Return whether {@code type} is guaranteed to be a that of a "local value".
     *
     * <p>For the purposes of purity analysis we really only care whether a return value is
     * immutable and identity-less; such values can't contribute to side-effects. Therefore, this
     * method is implemented to check if {@code type} is that of a primitive, since primitives
     * exhibit both relevant behaviours.
     */
    private boolean isLocalValueType(JSType type, AbstractCompiler compiler) {
      checkNotNull(type);
      JSType nativeObj = compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE);
      JSType subtype = type.getGreatestSubtype(nativeObj);
      // If the type includes anything related to a object type, don't assume
      // anything about the locality of the value.
      return subtype.isEmptyType();
    }
  }

  private static final Predicate<Node> RHS_IS_ALWAYS_LOCAL = lhs -> true;
  private static final Predicate<Node> RHS_IS_NEVER_LOCAL = lhs -> false;
  private static final Predicate<Node> FIND_RHS_AND_CHECK_FOR_LOCAL_VALUE = lhs -> {
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
    private final SetMultimap<Node, Var> skiplistedVarsByFunction = HashMultimap.create();
    private final SetMultimap<Node, Var> taintedVarsByFunction = HashMultimap.create();

    /**
     * For each function we're inside, the number of "catches" around the current node.
     *
     * <p>The stack is preloaded with a `0` to represent the global scope.
     */
    private final ArrayDeque<Integer> catchDepthStack = new ArrayDeque<>(ImmutableList.of(0));

    @Override
    public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      this.addToCatchDepthIfTryBlock(node, 1);

      if (node.isFunction()) {
        this.catchDepthStack.addLast(0);

        // Functions need to be processed as part of pre-traversal so that an entry for the
        // function
        // exists in the summariesForAllNamesOfFunctionByNode map when processing assignments
        // and
        // calls within the body.

        if (!summariesForAllNamesOfFunctionByNode.containsKey(node)) {
          // This function was not part of a definition which is why it was not created by
          // {@link populateDatastructuresForAnalysisTraversal}. For example, an anonymous
          // function.
          AmbiguatedFunctionSummary summary =
              AmbiguatedFunctionSummary.createInGraph(reverseCallGraph, "<anonymous>");
          summariesForAllNamesOfFunctionByNode.put(node, summary);
        }
      }

      return true;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {
      this.addToCatchDepthIfTryBlock(node, -1);
      if (node.isFunction()) {
        checkState(this.catchDepthStack.removeLast() == 0);
      }

      if (!compiler.getAstAnalyzer().nodeTypeMayHaveSideEffects(node) && !node.isReturn()) {
        return;
      }

      if (NodeUtil.isInvocation(node)) {
        // We collect these after filtering for side-effects because there's no point re-processing
        // a known pure call. This analysis is run multiple times, but no optimization will make a
        // pure function impure.
        allFunctionCalls.add(node);
      }

      Scope containerScope = traversal.getScope().getClosestContainerScope();
      if (!containerScope.isFunctionScope()) {
        // We only need to look at nodes in function scopes.
        return;
      }
      Node enclosingFunction = containerScope.getRootNode();

      for (AmbiguatedFunctionSummary encloserSummary :
          summariesForAllNamesOfFunctionByNode.get(enclosingFunction)) {
        checkNotNull(encloserSummary);
        updateSideEffectsForNode(encloserSummary, traversal, node, enclosingFunction);
      }
    }

    /**
     * Updates the side effects of a given node.
     *
     * <p>This node should be known to (possibly have) side effects. This method does not check if
     * the node (possibly) has side effects.
     */
    private void updateSideEffectsForNode(
        AmbiguatedFunctionSummary encloserSummary,
        NodeTraversal traversal,
        Node node,
        Node enclosingFunction) {

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
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              rhsLocality);
          break;

        case INC: // e.g. x++;
        case DEC:
        case DELPROP:
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              ImmutableList.of(node.getOnlyChild()),
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
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              // The RHS of a for-of must always be an iterable, making it a container, so we can't
              // consider its contents to be local
              RHS_IS_NEVER_LOCAL);
          checkIteratesImpureIterable(node, encloserSummary);
          break;

        case FOR_IN:
          // e.g.
          // for (prop in obj) {...}
          // Also this, though not very useful or readable.
          // for ([char1, char2, ...x.rest] in obj) {...}
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node),
              // A for-in always assigns a string, which is a local value by definition.
              RHS_IS_ALWAYS_LOCAL);
          break;

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
          visitLhsNodes(
              encloserSummary,
              traversal.getScope(),
              enclosingFunction,
              NodeUtil.findLhsNodesInNode(node.getParent()),
              RHS_IS_NEVER_LOCAL);
          break;

        case NAME:
          // Variable definition are not side effects. Check that the name appears in the context of
          // a variable declaration.
          checkArgument(NodeUtil.isNameDeclaration(node.getParent()), node.getParent());
          Node value = node.getFirstChild();
          // Assignment to local, if the value isn't a safe local value,
          // new object creation or literal or known primitive result
          // value, add it to the local skiplist.
          if (value != null && !NodeUtil.evaluatesToLocalValue(value)) {
            Scope scope = traversal.getScope();
            Var var = scope.getVar(node.getString());
            skiplistedVarsByFunction.put(enclosingFunction, var);
          }
          break;

        case THROW:
          this.recordThrowsBasedOnContext(encloserSummary);
          break;

        case RETURN:
          if (node.hasChildren() && !NodeUtil.evaluatesToLocalValue(node.getFirstChild())) {
            encloserSummary.setEscapedReturn();
          }
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

        case ITER_REST:
        case OBJECT_REST:
        case ITER_SPREAD:
        case OBJECT_SPREAD:
          if (node.getParent().isObjectPattern() || node.getParent().isObjectLit()) {
            if (!assumeGettersArePure) {
              // Object-rest and object-spread may trigger a getter.
              setSideEffectsForUnknownCall(encloserSummary);
            }
          } else {
            checkIteratesImpureIterable(node, encloserSummary);
          }
          break;

        case STRING_KEY:
          if (node.getParent().isObjectPattern()) {
            // This is an l-value STRING_KEY.
            // Assumption: GETELEM (via a COMPUTED_PROP) is never side-effectful.
            if (getPropertyKind(node.getString()).hasGetter()) {
              setSideEffectsForUnknownCall(encloserSummary);
            }
          }
          break;

        case OPTCHAIN_GETPROP:
        case GETPROP:
          // Assumption: GETELEM and OPTCHAIN_GETELEM are never side-effectful.
          if (getPropertyKind(node.getLastChild().getString()).hasGetterOrSetter()) {
            setSideEffectsForUnknownCall(encloserSummary);
          }
          break;

        default:
          if (NodeUtil.isCompoundAssignmentOp(node)) {
            // e.g.
            // x += 3;
            visitLhsNodes(
                encloserSummary,
                traversal.getScope(),
                enclosingFunction,
                ImmutableList.of(node.getFirstChild()),
                // The update assignments (e.g. `+=) always assign primitive, and therefore local,
                // values.
                RHS_IS_ALWAYS_LOCAL);
            break;
          }

          throw new IllegalArgumentException("Unhandled side effect node type " + node);
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
      setSideEffectsForUnknownCall(encloserSummary);
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

    /**
     * Assigns the set of side-effects associated with an unknown function to {@code
     * encloserSummary}.
     */
    private void setSideEffectsForUnknownCall(AmbiguatedFunctionSummary encloserSummary) {
      this.recordThrowsBasedOnContext(encloserSummary);
      encloserSummary.setMutatesGlobalState();
      encloserSummary.setMutatesArguments();
      encloserSummary.setMutatesThis();
    }

    private void recordThrowsBasedOnContext(AmbiguatedFunctionSummary encloserSummary) {
      if (this.catchDepthStack.getLast() == 0) {
        encloserSummary.setFunctionThrows();
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {
      // Nothing to do.
    }

    @Override
    public void exitScope(NodeTraversal t) {
      Scope closestContainerScope = t.getScope().getClosestContainerScope();
      if (!closestContainerScope.isFunctionScope()) {
        // Only functions and the scopes within them are of interest to us.
        return;
      }
      Node function = closestContainerScope.getRootNode();

      // Handle deferred local variable modifications:
      for (AmbiguatedFunctionSummary sideEffectInfo :
          summariesForAllNamesOfFunctionByNode.get(function)) {
        checkNotNull(sideEffectInfo, "%s has no side effect info.", function);

        if (sideEffectInfo.mutatesGlobalState()) {
          continue;
        }

        for (Var v : t.getScope().getVarIterable()) {
          if (v.isParam()
              && !skiplistedVarsByFunction.containsEntry(function, v)
              && taintedVarsByFunction.containsEntry(function, v)) {
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
          if (!localVar || skiplistedVarsByFunction.containsEntry(function, v)) {
            if (taintedVarsByFunction.containsEntry(function, v)) {
              // If the function has global side-effects
              // don't bother with the local side-effects.
              sideEffectInfo.setMutatesGlobalState();
              break;
            }
          }
        }
      }

      // Clean up memory after exiting out of the function scope where we will no longer need these.
      if (t.getScopeRoot().isFunction()) {
        skiplistedVarsByFunction.removeAll(function);
        taintedVarsByFunction.removeAll(function);
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
     * @param sideEffectInfo Function side effect record to be updated
     * @param scope variable scope in which the variable assignment occurs
     * @param enclosingFunction FUNCTION node for the enclosing function
     * @param lhsNodes LHS nodes that are all assigned values by a given parent node
     * @param hasLocalRhs Predicate indicating whether a given LHS is being assigned a local value
     */
    private void visitLhsNodes(
        AmbiguatedFunctionSummary sideEffectInfo,
        Scope scope,
        Node enclosingFunction,
        List<Node> lhsNodes,
        Predicate<Node> hasLocalRhs) {
      for (Node lhs : lhsNodes) {
        if (NodeUtil.isNormalGet(lhs)) {
          if (lhs.getFirstChild().isThis()) {
            sideEffectInfo.setMutatesThis();
          } else {
            Node objectNode = lhs.getFirstChild();
            if (objectNode.isName()) {
              Var var = scope.getVar(objectNode.getString());
              if (isVarDeclaredInSameContainerScope(var, scope)) {
                // Maybe a local object modification.  We won't know for sure until
                // we exit the scope and can validate the value of the local.
                taintedVarsByFunction.put(enclosingFunction, var);
              } else {
                sideEffectInfo.setMutatesGlobalState();
              }
            } else {
              // Don't track multi level locals: local.prop.prop2++;
              sideEffectInfo.setMutatesGlobalState();
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
              skiplistedVarsByFunction.put(enclosingFunction, var);
            }
          } else {
            sideEffectInfo.setMutatesGlobalState();
          }
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

      List<AmbiguatedFunctionSummary> calleeSummaries = getSummariesForCallee(invocation);
      if (calleeSummaries.isEmpty()) {
        callerInfo.setAllFlags();
        return;
      }

      boolean propatesThrows = this.catchDepthStack.getLast() == 0;
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

      this.catchDepthStack.addLast(this.catchDepthStack.removeLast() + delta);
    }
  }

  private static boolean isInvocationViaCallOrApply(Node callSite) {
    Node receiver = callSite.getFirstFirstChild();
    if (receiver == null || (!receiver.isName() && !receiver.isGetProp())) {
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
  @Nullable
  private static String nameForReference(Node nameRef) {
    switch (nameRef.getToken()) {
      case NAME:
        return nameRef.getString();
      case GETPROP:
      case OPTCHAIN_GETPROP:
        return PROP_NAME_PREFIX + nameRef.getSecondChild().getString();
      default:
        throw new IllegalStateException("Unexpected name reference: " + nameRef.toStringTree());
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
    @Nullable private final Node invocation;

    private SideEffectPropagation(
        boolean callerIsAlias,
        boolean allArgsUnescapedLocal,
        boolean calleeThisEqualsCallerThis,
        boolean propagateThrows,
        Node invocation) {
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

      @Nullable final Node thisArg;
      if (isInvocationViaCallOrApply(invocation)) {
        // If the call site is actually a `.call` or `.apply`, then `this` will be an argument.
        thisArg = invocation.getSecondChild();
      } else if (callee.isGetProp()) {
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
      int initialCallerFlags = caller.bitmask;

      if (callerIsAlias) {
        caller.setMask(callee.bitmask);
        return caller.bitmask != initialCallerFlags;
      }

      if (callee.mutatesGlobalState()) {
        // If the callee modifies global state then so does that caller.
        caller.setMutatesGlobalState();
      }
      if (this.propagateThrows && callee.functionThrows()) {
        // If the callee throws an exception then so does the caller.
        caller.setFunctionThrows();
      }
      if (callee.mutatesArguments() && !allArgsUnescapedLocal) {
        // If the callee mutates its input arguments and the arguments escape the caller then it has
        // unbounded side effects.
        caller.setMutatesGlobalState();
      }
      if (callee.mutatesThis()) {
        if (invocation.isNew()) {
          // NEWing a constructor provide a unescaped "this" making side-effects impossible.
        } else if (calleeThisEqualsCallerThis) {
          caller.setMutatesThis();
        } else {
          caller.setMutatesGlobalState();
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
    // Function metatdata
    private static final int ESCAPED_RETURN = 1 << 4;

    // The name shared by the set of functions that defined this summary.
    private final String name;
    // The node holding this summary in the reverse call graph.
    private final DiGraphNode<AmbiguatedFunctionSummary, SideEffectPropagation> graphNode;
    // The side effect flags for this set of functions.
    // TODO(nickreid): Replace this with a `Node.SideEffectFlags`.
    private int bitmask = 0;

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

    private AmbiguatedFunctionSummary setMask(int mask) {
      bitmask |= mask;
      return this;
    }

    private boolean getMask(int mask) {
      return (bitmask & mask) != 0;
    }

    boolean mutatesThis() {
      return getMask(MUTATES_THIS);
    }

    /** Marks the function as having "modifies this" side effects. */
    AmbiguatedFunctionSummary setMutatesThis() {
      return setMask(MUTATES_THIS);
    }

    /** Returns whether the function returns something that may be affected by global state. */
    boolean escapedReturn() {
      return getMask(ESCAPED_RETURN);
    }

    /** Marks the function as having non-local return result. */
    AmbiguatedFunctionSummary setEscapedReturn() {
      return setMask(ESCAPED_RETURN);
    }

    /** Returns true if function has an explicit "throw". */
    boolean functionThrows() {
      return getMask(THROWS);
    }

    /** Marks the function as having "throw" side effects. */
    AmbiguatedFunctionSummary setFunctionThrows() {
      return setMask(THROWS);
    }

    /** Returns true if function mutates global state. */
    boolean mutatesGlobalState() {
      return getMask(MUTATES_GLOBAL_STATE);
    }

    /** Marks the function as having "modifies globals" side effects. */
    AmbiguatedFunctionSummary setMutatesGlobalState() {
      return setMask(MUTATES_GLOBAL_STATE);
    }

    /** Returns true if function mutates its arguments. */
    boolean mutatesArguments() {
      return getMask(MUTATES_GLOBAL_STATE | MUTATES_ARGUMENTS);
    }

    /** Marks the function as having "modifies arguments" side effects. */
    AmbiguatedFunctionSummary setMutatesArguments() {
      return setMask(MUTATES_ARGUMENTS);
    }

    AmbiguatedFunctionSummary setAllFlags() {
      return setMask(
          THROWS | MUTATES_THIS | MUTATES_ARGUMENTS | MUTATES_GLOBAL_STATE | ESCAPED_RETURN);
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

      if (escapedReturn()) {
        status.add("return");
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
