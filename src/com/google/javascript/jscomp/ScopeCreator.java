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
 * This interface defines how objects capable of creating scopes from the parse
 * tree behave.
 *
 */
interface ScopeCreator {
  /**
   * Creates a {@link Scope} object.
   *
   * @param n the root node (either a FUNCTION node, a SCRIPT node, or a
   *    synthetic block node whose children are all SCRIPT nodes)
   * @param parent the parent Scope object (may be null)
   */
  <T extends Scope> T createScope(Node n, T parent);

  boolean hasBlockScope();
}
