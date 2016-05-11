/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Replicates the effect of {@link ClosureBundler} in whitespace-only mode and wraps goog.modules
 * in goog.loadModule calls. See comment block below.
 */
public class WhitespaceWrapGoogModules implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  WhitespaceWrapGoogModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      Preconditions.checkState(c.isScript());
      hotSwapScript(c, null);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    if (!NodeUtil.isModuleFile(scriptRoot)) {
      return;
    }

    // As per ClosureBundler:
    /*
     // add the prefix on the first line so the line numbers aren't affected.
     out.append(
         "goog.loadModule(function(exports) {"
         + "'use strict';");
     append(out, Mode.NORMAL, contents);
     out.append(
         "\n" // terminate any trailing single line comment.
         + ";" // terminate any trailing expression.
         + "return exports;});\n");
     appendSourceUrl(out, Mode.NORMAL);
    */

    Node block = IR.block();
    block.addChildToBack(
        IR.exprResult(IR.string("use strict"))); // needs to be explicit, to match ClosureBundler

    Node loadMod =
        IR.exprResult(
                IR.call(
                    IR.getprop(IR.name("goog"), IR.string("loadModule")),
                    IR.function(IR.name(""), IR.paramList(IR.name("exports")), block)))
            .srcrefTree(scriptRoot);

    if (scriptRoot.hasChildren()) {
      block.addChildrenToBack(scriptRoot.removeChildren());
    }
    block.addChildToBack(IR.returnNode(IR.name("exports")).srcrefTree(scriptRoot));

    scriptRoot.addChildToBack(loadMod);
    compiler.reportCodeChange();
  }
}
