/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;

/**
 * Logic for answering questions about portions of the AST.
 *
 * <p><b>What kind of methods should go here?</b>
 *
 * <p>Methods that answer questions about some portion of the AST and that may require global
 * information about the compilation, generally taking at least one {@link Node} as an argument. For
 * example:
 *
 * <ul>
 *   <li>Does a node have side effects?
 *   <li>Can we statically determine the value of a node?
 * </ul>
 *
 * <p><b>What kind of logic should not go here?</b>
 *
 * <p>Really simple logic that requires no global information, like finding the parameter list node
 * of a function, should be in {@link NodeUtil}. Logic that creates new Nodes or modifies the AST
 * should go in {@link AstFactory}.
 */
public class AstAnalyzer {

  private final AbstractCompiler compiler;

  AstAnalyzer(AbstractCompiler compiler) {
    this.compiler = checkNotNull(compiler);
  }

  /**
   * Returns true if the node may create new mutable state, or change existing state.
   *
   * @see <a href="http://www.xkcd.org/326/">XKCD Cartoon</a>
   */
  boolean mayEffectMutableState(Node n) {
    // TODO(bradfordcsmith): Move the implementation into this class when possible.
    return NodeUtil.mayEffectMutableState(n, compiler);
  }
}
