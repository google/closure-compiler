/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * A root pass that container for other passes that should run on
 * with a single call graph (currently a SimpleDefinitionFinder).
 * Expected passes include:
 *   - optimize parameters
 *   - optimize returns
 *   - devirtualize prototype methods
 * 
 * @author johnlenz@google.com (John Lenz)
 */
class OptimizeCalls implements CompilerPass {
  List<CallGraphCompilerPass> passes = Lists.newArrayList();
  private AbstractCompiler compiler;
  
  OptimizeCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
  }
  
  OptimizeCalls addPass(CallGraphCompilerPass pass) {
    passes.add(pass);
    return this;
  }
  
  interface CallGraphCompilerPass {
    void process(Node externs, Node root, SimpleDefinitionFinder definitions);
  }

  @Override
  public void process(Node externs, Node root) {
    if (passes.size() > 0) {
      SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
      defFinder.process(externs, root);
      for (CallGraphCompilerPass pass : passes) {
        pass.process(externs, root, defFinder);
      }
    }
  }
}
