/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;

import java.util.List;

/**
 * Provides a single place to manage transpilation passes.
 */
public class TranspilationPasses {
  private TranspilationPasses() {}

  /**
   * Adds all the early ES6 transpilation passes, which go before the Dart pass.
   *
   * <p>Includes ES6 features that are straightforward to transpile.
   * We won't handle them natively in the rest of the compiler, so we always
   * transpile them, even if the output language is also ES6.
   */
  public static void addEs6EarlyPasses(List<PassFactory> passes) {
    passes.add(es6RewriteArrowFunction);
    passes.add(es6RenameVariablesInParamLists);
    passes.add(es6SplitVariableDeclarations);
    passes.add(es6RewriteDestructuring);
  }

  /**
   * Adds all the late ES6 transpilation passes, which go after the Dart pass.
   *
   * <p>Includes ES6 features that are best handled natively by the compiler.
   * As we convert more passes to handle these features, we will be moving the
   * transpilation later in the compilation, and eventually only transpiling
   * when the output is lower than ES6.
   */
  public static void addEs6LatePasses(List<PassFactory> passes) {
    passes.add(es6ConvertSuper);
    passes.add(convertEs6ToEs3);
    passes.add(rewriteBlockScopedDeclaration);
    passes.add(rewriteGenerators);
    passes.add(rewritePolyfills);
  }

  static final HotSwapPassFactory es6RewriteDestructuring =
      new HotSwapPassFactory("Es6RewriteDestructuring", true) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RewriteDestructuring(compiler);
        }
      };

  static final HotSwapPassFactory es6RenameVariablesInParamLists =
      new HotSwapPassFactory("Es6RenameVariablesInParamLists", true) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RenameVariablesInParamLists(compiler);
        }
      };

  static final HotSwapPassFactory es6RewriteArrowFunction =
      new HotSwapPassFactory("Es6RewriteArrowFunction", true) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6RewriteArrowFunction(compiler);
        }
      };

  static final HotSwapPassFactory rewritePolyfills =
      new HotSwapPassFactory("RewritePolyfills", true) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new RewritePolyfills(compiler);
        }
      };

  static final HotSwapPassFactory es6SplitVariableDeclarations =
      new HotSwapPassFactory("Es6SplitVariableDeclarations", true) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6SplitVariableDeclarations(compiler);
        }
      };

  static final HotSwapPassFactory es6ConvertSuper =
      new HotSwapPassFactory("es6ConvertSuper", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6ConvertSuper(compiler);
    }
  };

  /**
   * Does the main ES6 to ES3 conversion.
   * There are a few other passes which run before or after this one,
   * to convert constructs which are not converted by this pass.
   */
  static final HotSwapPassFactory convertEs6ToEs3 =
      new HotSwapPassFactory("convertEs6", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6ToEs3Converter(compiler);
    }
  };

  static final HotSwapPassFactory rewriteBlockScopedDeclaration =
      new HotSwapPassFactory("Es6RewriteBlockScopedDeclaration", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteBlockScopedDeclaration(compiler);
    }
  };

  static final HotSwapPassFactory rewriteGenerators =
      new HotSwapPassFactory("rewriteGenerators", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteGenerators(compiler);
    }
  };
}
