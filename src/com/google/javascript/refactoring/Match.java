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
 * Object that contains the information for a given match.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class Match {

  private final Node node;
  private final NodeMetadata metadata;

  public Match(Node node, NodeMetadata metadata) {
    this.node = node;
    this.metadata = metadata;
  }

  /**
   * Returns the node that matched the given conditions.
   */
  public Node getNode() {
    return node;
  }

  /**
   * Returns the metadata for this match.
   */
  public NodeMetadata getMetadata() {
    return metadata;
  }
}
