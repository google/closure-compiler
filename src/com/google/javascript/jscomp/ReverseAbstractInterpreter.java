/*
 * Copyright 2007 The Closure Compiler Authors.
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
 * This interface defines what reversed abstract interpreters provide.
 * <p>Abstract interpretation is the process of interpreting a program at an
 * abstracted level (such as at the type level) instead of the concrete level
 * (the flow of values). This reversed abstract interpreter reverses the
 * abstract interpretation process by knowing the outcome of some computation
 * and calculating a preciser view of the world than the view without knowing
 * the outcome of the computation.</p>
 *
 */
interface ReverseAbstractInterpreter {
  /**
   * Calculates a precise version of the scope knowing the outcome of the
   * condition.
   *
   *  @param condition the condition's expression
   *  @param blindScope the scope without knowledge about the outcome of the
   *  condition
   *  @param outcome the outcome of the condition
   */
  FlowScope getPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome);
}
