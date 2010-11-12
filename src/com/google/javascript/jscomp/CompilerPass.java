/*
 * Copyright 2006 The Closure Compiler Authors.
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
 * <p>Interface for classes that can compile JS.</p>
 *
 * <p>Class has single function "process", which is passed
 * the root node of the parsed JS tree, as well as the
 * root node of the external JS tree (used to provide a public API
 * and prevent renaming of system functions).</p>
 *
 * <p>Use this class to support testing with BaseCompilerTest</p>
 *
 */
public interface CompilerPass {

  /**
   * Process the JS with root node root.
   * Can modify the contents of each Node tree
   * @param externs Top of external JS tree
   * @param root Top of JS tree
   */
  void process(Node externs, Node root);
}
