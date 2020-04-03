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

package com.google.javascript.refactoring;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeTraversal;
import javax.annotation.Nullable;

/**
 * Class that holds metadata (or meta objects) for use by JsFlume that aren't
 * contained within the Node itself.
 */
public final class NodeMetadata {

  private final AbstractCompiler compiler;

  @Nullable private final NodeTraversal traversal;

  public NodeMetadata(AbstractCompiler compiler) {
    this(compiler, null);
  }

  private NodeMetadata(AbstractCompiler compiler, NodeTraversal traversal) {
    this.compiler = compiler;
    this.traversal = traversal;
  }

  public static NodeMetadata fromTraversal(NodeTraversal traversal) {
    return new NodeMetadata(traversal.getCompiler(), traversal);
  }

  public AbstractCompiler getCompiler() {
    return compiler;
  }

  @Nullable
  public NodeTraversal getTraversal() {
    return traversal;
  }
}
