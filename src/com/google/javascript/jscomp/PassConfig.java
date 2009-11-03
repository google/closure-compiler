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


import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.Serializable;
import java.util.Collections;
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

  /** Names exported by goog.exportSymbol. */
  private Set<String> exportedNames = null;

  /**
   * Ids for cross-module method stubbing, so that each method has
   * a unique id.
   */
  private CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator =
      new CrossModuleMethodMotion.IdGenerator();

  /**
   * A memoized version of scopeCreator. It must be memoized so that
   * we can make two separate passes over the AST, one for inferring types
   * and one for checking types.
   */
  MemoizedScopeCreator typedScopeCreator;

  /** The global typed scope. */
  Scope topScope = null;

  /**
   * Keys are arguments passed to getCssName() found during compilation; values
   * are the number of times the key appeared as an argument to getCssName().
   */
  private Map<String, Integer> cssNames = null;

  public PassConfig(CompilerOptions options) {
    this.options = options;
  }

  // Setters and getters for intermediate state.
  //
  // This makes it possible to start and stop the compiler mid-compile.
  // Each getter and setter corresponds to a type of state that is produced
  // by an earlier pass and consumed by a later pass. When the compiler is
  // stopped mid-compile, the getter should return any state which has
  // been produced, and the setter should restore that state when the compiler
  // is restarted.
  //
  // TODO(nicksantos): Perhaps we should just make PassConfig serializable.

  /**
   * Gets a map of CSS names found in the JS code, to the number of times
   * they appear.
   */
  Map<String, Integer> getCssNames() {
    return cssNames == null ? null : Maps.newHashMap(cssNames);
  }

  /**
   * Gets the symbols exported by the passes.
   */
  Set<String> getExportedNames() {
    return exportedNames == null ? null :
        Collections.unmodifiableSet(exportedNames);
  }

  /**
   * Gets a generator for cross-module method ids, so that the ids
   * are stable across compiled.
   */
  CrossModuleMethodMotion.IdGenerator getCrossModuleIdGenerator() {
    return crossModuleIdGenerator;
  }

  /**
   * Sets the map of CSS names found in the JS code during previous runs.
   */
  void setCssNames(Map<String, Integer> newVal) {
    cssNames = newVal == null ? null : Maps.newHashMap(newVal);
  }

  /**
   * Sets the symbols exported by previous passes.
   */
  void setExportedNames(Set<String> newVal) {
    exportedNames = newVal == null ? null : Sets.newHashSet(newVal);
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
  State getIntermediateState() {
    return new State(getCssNames(), getExportedNames(),
                     crossModuleIdGenerator);
  }

  /**
   * Set the intermediate state for a pass config, to restart
   * a compilation process that had been previously paused.
   */
  void setIntermediateState(State state) {
    setCssNames(state.cssNames);
    setExportedNames(state.exportedNames);
    crossModuleIdGenerator = state.crossModuleIdGenerator;
  }

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

    @Override Map<String, Integer> getCssNames() {
      return delegate.getCssNames();
    }

    @Override Set<String> getExportedNames() {
      return delegate.getExportedNames();
    }

    @Override void setCssNames(Map<String, Integer> newVal) {
      delegate.setCssNames(newVal);
    }

    @Override void setExportedNames(Set<String> newVal) {
      delegate.setExportedNames(newVal);
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

    private final Map<String, Integer> cssNames;
    private final Set<String> exportedNames;
    private final CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator;

    private State(Map<String, Integer> cssNames, Set<String> exportedNames,
                  CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator) {
      this.cssNames = cssNames;
      this.exportedNames = exportedNames;
      this.crossModuleIdGenerator = crossModuleIdGenerator;
    }
  }
}
