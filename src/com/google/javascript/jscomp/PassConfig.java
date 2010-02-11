/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.graph.GraphvizGraph;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass factories and meta-data for native Compiler passes.
 *
*
 */
public abstract class PassConfig {

  // Used by subclasses in this package.
  final CompilerOptions options;

  /**
   * A memoized version of scopeCreator. It must be memoized so that
   * we can make two separate passes over the AST, one for inferring types
   * and one for checking types.
   */
  MemoizedScopeCreator typedScopeCreator;

  /** The global typed scope. */
  Scope topScope = null;

  public PassConfig(CompilerOptions options) {
    this.options = options;
  }

  /**
   * Gets the scope creator for typed scopes.
   */
  ScopeCreator getScopeCreator() {
    return typedScopeCreator;
  }

  /**
   * Gets the global scope, with type information.
   */
  Scope getTopScope() {
    return topScope;
  }

  /**
   * Gets the checking passes to run.
   *
   * Checking passes revolve around emitting warnings and errors.
   * They also may include pre-processor passes needed to do
   * error analysis more effectively.
   *
   * Clients that only want to analyze code (like IDEs) and not emit
   * code will only run checks and not optimizations.
   */
  abstract protected List<PassFactory> getChecks();

  /**
   * Gets the optimization passes to run.
   *
   * Optimization passes revolve around producing smaller and faster code.
   * They should always run after checking passes.
   */
  abstract protected List<PassFactory> getOptimizations();

  /**
   * Gets a graph of the passes run. For debugging.
   */
  GraphvizGraph getPassGraph() {
    LinkedDirectedGraph<String, String> graph = LinkedDirectedGraph.create();
    Iterable<PassFactory> allPasses =
        Iterables.concat(getChecks(), getOptimizations());
    String lastPass = null;
    String loopStart = null;
    for (PassFactory pass : allPasses) {
      String passName = pass.getName();
      int i = 1;
      while (graph.hasNode(passName)) {
        passName = pass.getName() + (i++);
      }
      graph.createNode(passName);

      if (loopStart == null && !pass.isOneTimePass()) {
        loopStart = passName;
      } else if (loopStart != null && pass.isOneTimePass()) {
        graph.connect(lastPass, "loop", loopStart);
        loopStart = null;
      }

      if (lastPass != null) {
        graph.connect(lastPass, "", passName);
      }
      lastPass = passName;
    }
    return graph;
  }

  /**
   * Create a type inference pass.
   */
  final TypeInferencePass makeTypeInference(AbstractCompiler compiler) {
    return new TypeInferencePass(
        compiler, compiler.getReverseAbstractInterpreter(),
        topScope, typedScopeCreator);
  }

  /**
   * Create a type-checking pass.
   */
  final TypeCheck makeTypeCheck(AbstractCompiler compiler) {
    return new TypeCheck(
        compiler,
        compiler.getReverseAbstractInterpreter(),
        compiler.getTypeRegistry(),
        topScope,
        typedScopeCreator,
        options.reportMissingOverride,
        options.reportUnknownTypes)
        .reportMissingProperties(options.enables(
            DiagnosticGroup.forType(TypeCheck.INEXISTENT_PROPERTY)));
  }

  final static void addPassFactoryBefore(
      List<PassFactory> factoryList, PassFactory factory, String passName) {
    for (int i = 0; i < factoryList.size(); i++) {
      if (factoryList.get(i).getName().equals(passName)) {
        factoryList.add(i, factory);
        return;
      }
    }

    throw new IllegalArgumentException(
        "No factory named '" + passName + "' in the factory list");
  }

  /**
   * Find the first pass provider that does not have a delegate.
   */
  final PassConfig getBasePassConfig() {
    PassConfig current = this;
    while (current instanceof PassConfigDelegate) {
      current = ((PassConfigDelegate) current).delegate;
    }
    return current;
  }

  /**
   * Get intermediate state for a running pass config, so it can
   * be paused and started again later.
   */
  abstract State getIntermediateState();

  /**
   * Set the intermediate state for a pass config, to restart
   * a compilation process that had been previously paused.
   */
  abstract void setIntermediateState(State state);

  /**
   * An implementation of PassConfig that just proxies all its method calls
   * into an inner class.
   */
  static class PassConfigDelegate extends PassConfig {

    private final PassConfig delegate;

    PassConfigDelegate(PassConfig delegate) {
      super(delegate.options);
      this.delegate = delegate;
    }

    @Override protected List<PassFactory> getChecks() {
      return delegate.getChecks();
    }

    @Override protected List<PassFactory> getOptimizations() {
      return delegate.getOptimizations();
    }

    @Override ScopeCreator getScopeCreator() {
      return delegate.getScopeCreator();
    }

    @Override Scope getTopScope() {
      return delegate.getTopScope();
    }

    @Override State getIntermediateState() {
      return delegate.getIntermediateState();
    }

    @Override void setIntermediateState(State state) {
      delegate.setIntermediateState(state);
    }
  }

  /**
   * Intermediate state for a running pass configuration.
   */
  static class State implements Serializable {
    private static final long serialVersionUID = 1L;

    final Map<String, Integer> cssNames;
    final Set<String> exportedNames;
    final CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator;
    final VariableMap variableMap;
    final VariableMap propertyMap;
    final VariableMap anonymousFunctionNameMap;
    final FunctionNames functionNames;

    State(Map<String, Integer> cssNames, Set<String> exportedNames,
          CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator,
          VariableMap variableMap, VariableMap propertyMap,
          VariableMap anonymousFunctionNameMap, FunctionNames functionNames) {
      this.cssNames = cssNames;
      this.exportedNames = exportedNames;
      this.crossModuleIdGenerator = crossModuleIdGenerator;
      this.variableMap = variableMap;
      this.propertyMap = propertyMap;
      this.anonymousFunctionNameMap = anonymousFunctionNameMap;
      this.functionNames = functionNames;
    }
  }
}
