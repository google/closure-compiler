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

/**
 * Information about the context in which a Definition is used.
 * Includes the referring node, and context in which the reference
 * occurs - including the module in which the reference appears.
 *
 */

class UseSite {
  final Node node;
  final Scope scope;
  final JSModule module;

  UseSite(Node node, Scope scope, JSModule module) {
    this.node = node;
    this.scope = scope;
    this.module = module;
  }

  // Use the node as the identifying feature to make the UseSite recreatable.

  @Override
  public int hashCode() {
    return this.node.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof UseSite && ((UseSite)(o)).node.equals(this.node));
  }
}
