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
import com.google.javascript.rhino.jstype.StaticTypedSlot;

/**
 * A symbol table for inferring types during data flow analysis.
 *
 * Each flow scope represents the types of all variables in the scope at
 * a particular point in the flow analysis.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public interface FlowScope extends StaticTypedScope<JSType>, LatticeElement {

  /**
   * Creates a child of this flow scope, to represent an instruction
   * directly following this one.
   */
  FlowScope createChildFlowScope();

  /**
   * Defines the type of a symbol at this point in the flow.
   * @throws IllegalArgumentException If no slot for this symbol exists.
   */
  void inferSlotType(String symbol, JSType type);

  /**
   * Infer the type of a qualified name.
   *
   * When traversing the control flow of a function, simple names are
   * declared at the bottom of the flow lattice. But there are far too many
   * qualified names to be able to do this and be performant. So the bottoms
   * of qualified names are declared lazily.
   *
   * Therefore, when inferring a qualified slot, we need both the "bottom"
   * type of the slot when we enter the scope, and the current type being
   * inferred.
   */
  void inferQualifiedSlot(Node node, String symbol, JSType bottomType,
      JSType inferredType);

  /**
   * Optimize this scope and return a new FlowScope with faster lookup.
   */
  FlowScope optimize();

  /**
   * Tries to find a unique refined variable in the refined scope, up to the
   * the blind scope.
   * @param blindScope The scope before the refinement, i.e. some parent of the
   *     this scope or itself.
   * @return The unique refined variable if found or null.
   */
  StaticTypedSlot<JSType> findUniqueRefinedSlot(FlowScope blindScope);

  /**
   * Look through the given scope, and try to find slots where it doesn't
   * have enough type information. Then fill in that type information
   * with stuff that we've inferred in the local flow.
   */
  void completeScope(StaticTypedScope<JSType> scope);
}
