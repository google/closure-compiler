/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * A CleanupPass implementation that will remove all Global Vars contributed by
 * the original file.
 *
 * @author tylerg@google.com (Tyler Goodwin)
 */
public class GlobalVarCleanupPass implements HotSwapCompilerPass {

  public GlobalVarCleanupPass(AbstractCompiler compiler) {
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
  }

  @Override
  public void process(Node externs, Node root) {
    // GlobalVarCleanupPass should not do work during process.
  }

}
