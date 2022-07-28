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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static java.util.Comparator.comparingInt;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionLookup;
import com.google.javascript.jscomp.DataFlowAnalysis.LinearFlowState;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeResolver;
import java.util.Comparator;
import java.util.LinkedHashMap;
import org.jspecify.nullness.Nullable;

/** A compiler pass to run the type inference analysis. */
class TypeInferencePass {

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final ReverseAbstractInterpreter reverseInterpreter;
  private TypedScope topScope;
  private final TypedScopeCreator scopeCreator;
  private final AssertionFunctionLookup assertionFunctionLookup;

  // (stepCount, Token) -> populationCount
  private final @Nullable LinkedHashMap<Integer, HashMultiset<Token>> stepCountHistogram;

  TypeInferencePass(
      AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      TypedScopeCreator scopeCreator) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.reverseInterpreter = reverseInterpreter;
    this.scopeCreator = scopeCreator;
    this.assertionFunctionLookup =
        AssertionFunctionLookup.of(compiler.getCodingConvention().getAssertionFunctions());
    this.stepCountHistogram = compiler.isDebugLoggingEnabled() ? new LinkedHashMap<>() : null;
  }

  /**
   * Execute type inference running over part of the scope tree.
   *
   * @return the top scope, either newly created, or patched by this inference.
   */
  TypedScope inferAllScopes(Node inferenceRoot) {
    // Type analysis happens in two major phases.
    // 1) Finding all the symbols.
    // 2) Propagating all the inferred types.
    //
    // The order of this analysis is non-obvious. In a complete inference
    // system, we may need to backtrack arbitrarily far. But the compile-time
    // costs would be unacceptable.
    //
    // We do one pass where we do typed scope creation for all scopes
    // in pre-order.
    //
    // Then we do a second pass where we do all type inference
    // (type propagation) in pre-order.
    //
    // We use a memoized scope creator so that we never create a scope
    // more than once.
    //
    // This will allow us to handle cases like:
    // var ns = {};
    // (function() { /** JSDoc */ ns.method = function() {}; })();
    // ns.method();
    // In this code, we need to build the symbol table for the inner scope in
    // order to propagate the type of ns.method in the outer scope.
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      checkState(inferenceRoot.isRoot());
      checkState(inferenceRoot.getParent() == null);
      checkState(this.topScope == null);
      this.topScope = scopeCreator.createScope(inferenceRoot, null);

      NodeTraversal.builder()
          .setCompiler(compiler)
          .setCallback(new FirstScopeBuildingCallback())
          .setScopeCreator(scopeCreator)
          .traverseWithScope(inferenceRoot, this.topScope);
      scopeCreator.resolveWeakImportsPreResolution();
    }
    scopeCreator.finishAndFreeze();

    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(new SecondScopeBuildingCallback())
        .setScopeCreator(scopeCreator)
        .traverseWithScope(inferenceRoot, this.topScope);

    // Normalize TypedVars to have the '?' type instead of null after inference is complete. This
    // currently cannot be done any earlier because it breaks inference of variables assigned in
    // local scopes.
    // TODO(b/149843534): this should be a crash instead.
    final JSType unknownType = this.registry.getNativeType(UNKNOWN_TYPE);
    for (TypedVar var : this.scopeCreator.getAllSymbols()) {
      if (var.getType() == null) {
        var.setType(unknownType);
      }
    }

    if (this.stepCountHistogram != null) {
      try (LogFile histogram =
          this.compiler.createOrReopenLog(this.getClass(), "step_histogram.log")) {
        histogram.log("step_count token population");

        int[] totals = new int[] {0, 0};
        this.stepCountHistogram.keySet().stream()
            .sorted(Comparator.<Integer>naturalOrder().reversed())
            .forEach(
                (stepCount) ->
                    this.stepCountHistogram.get(stepCount).entrySet().stream()
                        .sorted(comparingInt(Multiset.Entry::getCount))
                        .forEach(
                            (e) -> {
                              totals[0] += stepCount * e.getCount();
                              totals[1] += e.getCount();
                              histogram.log("%s %s %s", stepCount, e.getElement(), e.getCount());
                            }));
        histogram.log("%s TOTAL %s", totals[0], totals[1]);
      }
    }

    return this.topScope;
  }

  private void inferScope(Node n, TypedScope scope) {
    ControlFlowGraph<Node> cfg = computeCfg(n);
    TypeInference typeInference =
        new TypeInference(
            compiler, cfg, reverseInterpreter, scope, scopeCreator, assertionFunctionLookup);
    typeInference.analyze();

    if (this.stepCountHistogram != null) {
      for (DiGraphNode<Node, ?> node : cfg.getNodes()) {
        if (node == cfg.getImplicitReturn()) {
          continue;
        }

        LinearFlowState<?> state = node.getAnnotation();
        this.stepCountHistogram
            .computeIfAbsent(state.getStepCount(), (k) -> HashMultiset.create())
            .add(node.getValue().getToken());
      }
    }
  }

  private static class FirstScopeBuildingCallback extends AbstractScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      t.getTypedScope();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Do nothing
    }
  }

  private class SecondScopeBuildingCallback extends AbstractScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      // Only infer the entry root, rather than the scope root.
      // This ensures that incremental compilation only touches the root
      // that's been swapped out.
      TypedScope scope = t.getTypedScope();
      if (scope.isCfgRootScope() && !scope.isModuleScope()) {
        // ignore scopes that don't have their own CFGs and module scopes, which are visited
        // as if they were a regular script.
        inferScope(t.getCurrentNode(), scope);
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Do nothing
    }
  }

  private ControlFlowGraph<Node> computeCfg(Node n) {
    return ControlFlowAnalysis.builder()
        .setCompiler(compiler)
        .setCfgRoot(n)
        .setIncludeEdgeAnnotations(true)
        .computeCfg();
  }
}
