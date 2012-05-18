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
 * Interface for compiler passes that can be used in a hot-swap fashion.
 * <p>
 * The additional method is {@code hotSwapScript} which runs this pass on a
 * subtree of the AST. Each pass that is intended to support hot-swap style
 * should implement this interface.
 * <p>
 * It is assumed that {@code Node} argument of {@code hotSwapScript} is the root
 * of a sub-tree in AST that represents a JS file and so is of type {@code
 * Token.SCRIPT}.
 *
 * @author bashir@google.com (Bashir Sadjad)
 */
public interface HotSwapCompilerPass extends CompilerPass {

  /**
   * Process the JS with root node root. This is supposed to be significantly
   * faster compared to corresponding full-compiler passes.
   *
   * @param scriptRoot Root node corresponding to the file that is modified,
   *        should be of type {@code Token.SCRIPT}.
   * @param originalRoot Root node corresponding to the original version of the
   *        file that is modified. Should be of type {@code token.SCRIPT}.
   */
  void hotSwapScript(Node scriptRoot, Node originalRoot);

}
