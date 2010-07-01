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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.ParallelCompilerPass.Result;
import com.google.javascript.jscomp.ParallelCompilerPass.Task;
import com.google.javascript.jscomp.RemoveConstantExpressions.RemoveConstantRValuesCallback;
import com.google.javascript.rhino.Node;

/**
 * Execute {@link RemoveConstantExpressions} in parallel.
 *
*
 */
final class RemoveConstantExpressionsParallel implements CompilerPass {

  private final AbstractCompiler compiler;

  private final int numThreads;

  RemoveConstantExpressionsParallel(AbstractCompiler compiler, int numThreads) {
    this.compiler = compiler;
    this.numThreads = numThreads;
  }

  RemoveConstantExpressionsParallel(AbstractCompiler compiler) {
    this(compiler, Runtime.getRuntime().availableProcessors());
  }

  @Override
  public void process(Node externs, Node root) {
    // Estimate the number of CPU needed.
    AstParallelizer splitter = AstParallelizer
      .createNewFileLevelAstParallelizer(root);

    // Clean supply of RemoveConstantRValuesCallback.
    Supplier<Task> supplier = new Supplier<Task>() {
      @Override
      public Task get() {
        return new Task() {
          @Override
          public Result processSubtree(Node subtree) {
            RemoveConstantRValuesCallback cb =
                new RemoveConstantRValuesCallback();
            NodeTraversal.traverse(null, subtree, cb);
            return cb.getResult();
          }
        };
      }
    };

    // Execute in parallel.
    (new ParallelCompilerPass(compiler, splitter, supplier, numThreads))
      .process(externs, root);
  }
}
