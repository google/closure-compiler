/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

/**
 * Adds runtime libraries to the beginning of the AST. Any libraries explicitly requested via the
 * {@link CompilerOptions#forceLibraryInjection} field.
 *
 * <p>TODO(b/120486392): merge this pass with {@link InjectTranspilationRuntimeLibraries}.
 */
class InjectRuntimeLibraries implements CompilerPass {
  private final AbstractCompiler compiler;
  private final Stage stage;

  private enum Stage {
    CHECKS,
    OPTIMIZATIONS
  }

  private InjectRuntimeLibraries(AbstractCompiler compiler, Stage stage) {
    this.compiler = compiler;
    this.stage = stage;
  }

  @Override
  public void process(Node externs, Node root) {
    // TODO(bradfordcsmith): Passes should not read the compiler options object.
    CompilerOptions options = compiler.getOptions();
    switch (this.stage) {
      case CHECKS:
        return;
      case OPTIMIZATIONS:
        injectOptimizationsLibraries(options);
        return;
    }
    throw new AssertionError();
  }

  private void injectOptimizationsLibraries(CompilerOptions options) {
    for (String forced : options.forceLibraryInjection) {
      compiler.ensureLibraryInjected(forced, true);
    }
  }

  public static InjectRuntimeLibraries forChecks(AbstractCompiler compiler) {
    return new InjectRuntimeLibraries(compiler, Stage.CHECKS);
  }

  public static InjectRuntimeLibraries forOptimizations(AbstractCompiler compiler) {
    return new InjectRuntimeLibraries(compiler, Stage.OPTIMIZATIONS);
  }
}
