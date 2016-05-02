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
 * Adds runtime libraries to the beginning of the AST. The libraries for ES6 transpilation
 * and the Dart pass are added, if needed, as well as any other libraries explicitly
 * requested via the CompilerOptions#forceLibraryInjection field.
 */
class InjectRuntimeLibraries implements CompilerPass {
  private AbstractCompiler compiler;

  public InjectRuntimeLibraries(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (String forced : compiler.getOptions().forceLibraryInjection) {
      compiler.ensureLibraryInjected(forced, true);
    }
  }
}
