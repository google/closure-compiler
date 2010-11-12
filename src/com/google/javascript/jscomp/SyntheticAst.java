/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Token;


/**
 * An AST generated totally by the compiler.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class SyntheticAst implements SourceAst {
  private static final long serialVersionUID = 1L;

  private final String sourceName;

  private Node root;

  SyntheticAst(String sourceName) {
    this.sourceName = sourceName;
    clearAst();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    return root;
  }

  @Override
  public void clearAst() {
    root = new Node(Token.SCRIPT);
    root.putProp(Node.SOURCENAME_PROP, sourceName);
  }

  @Override
  public SourceFile getSourceFile() {
    return null;
  }

  @Override
  public void setSourceFile(SourceFile file) {
    throw new IllegalStateException(
        "Cannot set a source file for a synthetic AST");
  }
}
