/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.type;

import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import javax.annotation.CheckReturnValue;

/**
 * A symbol table for inferring types during data flow analysis.
 *
 * <p>Each flow scope represents the types of all variables in the scope at a particular point in
 * the flow analysis.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public interface FlowScope extends StaticTypedScope, LatticeElement {

  /**
   * Returns a flow scope with the given syntactic scope, which may be required to be a specific
   * subclass, such as TypedScope.
   */
  FlowScope withSyntacticScope(StaticTypedScope scope);

  /**
   * Returns a flow scope with the type of the given {@code symbol} updated to {@code type}.
   *
   * @throws IllegalArgumentException If no slot for this symbol exists.
   */
  @CheckReturnValue
  FlowScope inferSlotType(String symbol, JSType type);

  /**
   * Returns a flow scope with the type of the given {@code symbol} updated to {@code inferredType}.
   * Updates are not performed in-place.
   *
   * <p>When traversing the control flow of a function, simple names are declared at the bottom of
   * the flow lattice. But there are far too many qualified names to be able to do this and be
   * performant. So the bottoms of qualified names are declared lazily.
   *
   * <p>Therefore, when inferring a qualified slot, we need both the "bottom" type of the slot when
   * we enter the scope, and the current type being inferred.
   */
  @CheckReturnValue
  FlowScope inferQualifiedSlot(
      Node node, String symbol, JSType bottomType, JSType inferredType, boolean declare);

  /** Returns the underlying TypedScope. */
  StaticTypedScope getDeclarationScope();
}
