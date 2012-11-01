/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

/**
 * Common interface for {@link ArrayLiteral} and {@link ObjectLiteral}
 * node types, both of which may appear in "destructuring" expressions or
 * contexts.
 */
public interface DestructuringForm {

  /**
   * Marks this node as being a destructuring form - that is, appearing
   * in a context such as {@code for ([a, b] in ...)} where it's the
   * target of a destructuring assignment.
   */
  void setIsDestructuring(boolean destructuring);

  /**
   * Returns true if this node is in a destructuring position:
   * a function parameter, the target of a variable initializer, the
   * iterator of a for..in loop, etc.
   */
  boolean isDestructuring();
}
