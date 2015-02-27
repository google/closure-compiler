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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

/**
 * Compiler pass that collects visibility annotations in {@code @fileoverview}
 * blocks. Used by {@link CheckAccessControls}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
class CollectFileOverviewVisibility implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;
  private final ImmutableMap.Builder<StaticSourceFile, Visibility> builder =
      ImmutableMap.builder();

  CollectFileOverviewVisibility(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node script = root.getFirstChild();
        script != null;
        script = script.getNext()) {
      Preconditions.checkState(script.isScript());
      visit(script);
    }
  }

  private void visit(Node scriptNode) {
    JSDocInfo jsDocInfo = scriptNode.getJSDocInfo();
    if (jsDocInfo == null) {
      return;
    }
    Visibility v = jsDocInfo.getVisibility();
    if (v == null) {
      return;
    }
    builder.put(scriptNode.getStaticSourceFile(), v);
  }

  ImmutableMap<StaticSourceFile, Visibility> getFileOverviewVisibilityMap() {
    return builder.build();
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    compiler.process(this);
  }

}

