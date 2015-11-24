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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Adds runtime libraries to the beginning of the AST. The libraries for ES6 transpilation
 * and the Dart pass are added, if needed, as well as any other libraries explicitly
 * requested via the CompilerOptions#forceLibraryInjection field.
 */
class InjectEs6RuntimeLibrary implements CompilerPass {
  private AbstractCompiler compiler;

  public InjectEs6RuntimeLibrary(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void process(Node externs, Node root) {
    for (String forced : compiler.getOptions().forceLibraryInjection) {
      compiler.ensureLibraryInjected(forced, false);
    }

    if (compiler.needsEs6Runtime) {
      compiler.ensureLibraryInjected("es6_runtime", false);
      // es6_runtime.js refers to 'window' and 'global' which are only defined in the browser
      // externs and the node externs, respectively. Therefore one or both of them may be
      // undeclared. Add synthetic externs for them. The VarCheck pass would do this for us but it
      // runs before this one.
      for (String name : new String[] {"window", "global"}) {
        compiler.getSynthesizedExternsInput().getAstRoot(compiler).addChildToBack(
            IR.var(IR.name(name)));
      }
    }
    if (compiler.needsEs6DartRuntime) {
      compiler.ensureLibraryInjected("es6_dart_runtime", false);
    }
  }
}
