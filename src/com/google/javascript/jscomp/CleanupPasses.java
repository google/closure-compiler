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

import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES5;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.GlobalVarReferenceMap.GlobalVarRefCleanupPass;
import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides passes that should be run before hot-swap/incremental builds.
 *
 * @author tylerg@google.com (Tyler Goodwin)
 */
class CleanupPasses extends PassConfig {

  public CleanupPasses(CompilerOptions options) {
    super(options);
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = new ArrayList<>();
    checks.add(fieldCleanupPassFactory);
    checks.add(scopeCleanupPassFactory);
    checks.add(globalVarRefCleanupPassFactory);
    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  final PassFactory fieldCleanupPassFactory =
      new HotSwapPassFactory("FieldCleanupPassFactory") {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new FieldCleanupPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  final PassFactory scopeCleanupPassFactory =
      new HotSwapPassFactory("ScopeCleanupPassFactory") {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new MemoizedScopeCleanupPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  final PassFactory globalVarRefCleanupPassFactory =
      new HotSwapPassFactory("GlobalVarRefCleanupPassFactory") {
        @Override
        protected HotSwapCompilerPass create(
            AbstractCompiler compiler) {
          return new GlobalVarRefCleanupPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
  };

  /**
   * A CleanupPass implementation that will remove stored scopes from the
   * TypedScopeCreator of the compiler instance for a the hot swapped script.
   * <p>
   * This pass will also clear out Source Nodes of Function Types declared on
   * Vars tracked by TypedScopeCreator
   */
  static class MemoizedScopeCleanupPass implements HotSwapCompilerPass {

    private final AbstractCompiler compiler;

    public MemoizedScopeCleanupPass(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
      ScopeCreator creator = compiler.getTypedScopeCreator();
      if (creator instanceof TypedScopeCreator) {
        TypedScopeCreator scopeCreator = (TypedScopeCreator) creator;
        String newSrc = scriptRoot.getSourceFileName();
        for (TypedVar var : scopeCreator.getAllSymbols()) {
          JSType type = var.getType();
          if (type != null) {
            FunctionType fnType = type.toMaybeFunctionType();
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
