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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
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
  /**
   * The set of builtin constructors that don't have side effects.
   *
   * <p>TODO(bradfordcsmith): If all of these are annotated {@code sideefectfree}, can we drop this
   * list?
   */
  private static final ImmutableSet<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS =
      ImmutableSet.of("Array", "Date", "Error", "Object", "RegExp", "XMLHttpRequest");

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

  /**
   * Returns true if the node which may have side effects when executed. This version default to the
   * "safe" assumptions when the compiler object is not provided (RegExp have side-effects, etc).
   */
  public boolean mayHaveSideEffects(Node n) {
    // TODO(b/131178806): Move implementation here when possible
    return NodeUtil.mayHaveSideEffects(n, compiler);
  }

  /**
   * Returns true if this function call may have side effects.
   *
   * <p>This method is guaranteed to return true all calls that have side-effects, but may also
   * return true for calls that have none.
   *
   * @param callNode - function call node
   */
  boolean functionCallHasSideEffects(Node callNode) {
    return NodeUtil.functionCallHasSideEffects(callNode, compiler);
  }

  /**
   * Do calls to this constructor have side effects?
   *
   * @param newNode - constructor call node
   */
  boolean constructorCallHasSideEffects(Node newNode) {
    checkArgument(newNode.isNew(), "Expected NEW node, got %s", newNode.getToken());

    if (newNode.isNoSideEffectsCall()) {
      return false;
    }

    // allArgsUnescapedLocal() is actually confirming that all of the arguments are literals or
    // values created at the point they are passed in to the call and are not saved anywhere in the
    // calling scope.
    // TODO(bradfordcsmith): It would be good to rename allArgsUnescapedLocal() to something
    // that makes this clearer.
    if (newNode.isOnlyModifiesArgumentsCall() && NodeUtil.allArgsUnescapedLocal(newNode)) {
      return false;
    }

    Node nameNode = newNode.getFirstChild();
    return !nameNode.isName() || !CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString());
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * <p>This is a non-recursive version of the may have side effects check; used to check wherever
   * the current node's type is one of the reasons why a subtree has side effects.
   */
  boolean nodeTypeMayHaveSideEffects(Node n) {
    return NodeUtil.nodeTypeMayHaveSideEffects(n, compiler);
  }
}
