/*
 * Copyright 2012 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.DefaultPassConfig.HotSwapPassFactory;
import com.google.javascript.jscomp.GlobalVarReferenceMap.GlobalVarRefCleanupPass;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;

import java.util.List;

/**
 * Provides passes that should be run before hot-swap/incremental builds.
 *
 * @author tylerg@google.com (Tyler Goodwin)
 */
class CleanupPasses extends PassConfig {

  private State state;

  public CleanupPasses(CompilerOptions options) {
    super(options);
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = Lists.newArrayList();
    checks.add(fieldCleanupPassFactory);
    checks.add(scopeCleanupPassFactory);
    checks.add(globalVarRefCleanupPassFactory);
    return checks;
  }

  @Override
  protected State getIntermediateState() {
    return state;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  @Override
  protected void setIntermediateState(State state) {
    this.state = state;
  }

  final PassFactory fieldCleanupPassFactory =
      new HotSwapPassFactory("FieldCleaupPassFactory", false) {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new FieldCleanupPass(compiler);
        }
      };

  final PassFactory scopeCleanupPassFactory =
      new HotSwapPassFactory("ScopeCleanupPassFactory", false) {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new MemoizedScopeCleanupPass(compiler);
        }
      };

  final PassFactory globalVarRefCleanupPassFactory =
      new HotSwapPassFactory("GlobalVarRefCleanupPassFactory", false) {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new GlobalVarRefCleanupPass(compiler);
        }
  };

  /**
   * A CleanupPass implementation that will remove stored scopes from the
   * MemoizedScopeCreator of the compiler instance for a the hot swapped script.
   * <p>
   * This pass will also clear out Source Nodes of Function Types declared on
   * Vars tracked by MemoizedScopeCreator
   */
  static class MemoizedScopeCleanupPass implements HotSwapCompilerPass {

    private final AbstractCompiler compiler;

    public MemoizedScopeCleanupPass(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
      ScopeCreator creator = compiler.getTypedScopeCreator();
      if (creator instanceof MemoizedScopeCreator) {
        MemoizedScopeCreator scopeCreator = (MemoizedScopeCreator) creator;
        String newSrc = scriptRoot.getSourceFileName();
        for (Var var : scopeCreator.getAllSymbols()) {
          TypeI type = var.getType();
          if (type != null) {
            FunctionTypeI fnType = type.toMaybeFunctionType();
            if (fnType != null
                && newSrc.equals(NodeUtil.getSourceName(fnType.getSource()))) {
              fnType.setSource(null);
            }
          }
        }
        scopeCreator.removeScopesForScript(originalRoot.getSourceFileName());
      }
    }

    @Override
    public void process(Node externs, Node root) {
      // MemoizedScopeCleanupPass should not do work during process.
    }
  }
}
