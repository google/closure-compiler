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

import java.io.Serializable;

/**
 * An interface for accessing the AST root of an input.
 *
 */
public interface SourceAst extends Serializable {
  /**
   * Gets the root node of the AST for the source file this represents. The AST
   * is lazily instantiated and cached.
   */
  public Node getAstRoot(AbstractCompiler compiler);

  /**
   * Removes any references to root node of the AST.  If it is requested again,
   * another parse will be performed.  This method is needed to allow the ASTs
   * to be garbage collected if the inputs are still around after compilation.
   */
  public void clearAst();

  /** Returns the source file the generated AST represents. */
  public SourceFile getSourceFile();

  /**
   * Sets the source file the generated AST represents. This can be called after
   * deserializing if access to the source file is needed. If a different file
   * is provided than that with which this was created, an IllegalStateException
   * will be thrown.
   */
  public void setSourceFile(SourceFile file);
}
