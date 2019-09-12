/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * An abstract WarningsGuard that provides an additional getScriptNodeForError() method
 * for accessing the containing SCRIPT node of the AST in a robust way.
 */
public abstract class FileAwareWarningsGuard extends WarningsGuard {

  private final AbstractCompiler compiler;

  protected FileAwareWarningsGuard(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Nullable
  protected final Node getScriptNodeForError(JSError error) {
    // If error.node is connected to AST, this will be much faster than compiler.getScriptNode
    for (Node n = error.getNode(); n != null; n = n.getParent()) {
      if (n.isScript()) {
        return n;
      }
    }
    if (error.getSourceName() == null) {
      return null;
    }
    Node scriptNode = compiler.getScriptNode(error.getSourceName());
    if (scriptNode != null) {
      // TODO(b/73088845): This should always be a SCRIPT node
      if (!scriptNode.isScript()) {
        return null;
      }
      checkState(scriptNode.isScript(), scriptNode);
      return scriptNode;
    }
    return null;
  }
}
