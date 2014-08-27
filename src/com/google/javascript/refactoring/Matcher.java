/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import com.google.javascript.rhino.Node;

/**
 * Interface for a class that knows how to match a {@link Node} for a specific
 * pattern. For example of Matchers, see {@link Matchers}.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public interface Matcher {
  /**
   * Returns true if the specified {@link Node} and {@link NodeMetadata} match
   * the given pattern.
   */
  boolean matches(Node n, NodeMetadata metadata);
}
