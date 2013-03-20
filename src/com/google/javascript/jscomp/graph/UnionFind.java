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
package com.google.javascript.jscomp.graph;

import java.util.Collection;
import java.util.Set;

/**
 * Union-Find is a classical algorithm used to find connected components in
 * graph theory.
 *
 * <p>Each equivalence class has a representative element that is chosen
 * arbitrarily and is used to determine if two elements are members of the same
 * class.
 *
 * <p>See <a
 * href="http://www.algorithmist.com/index.php?title=Union_Find&oldid=7575">
 * algorithmist</a> for more detail.
 *
 * @param <E> element type
 */
public interface UnionFind<E> {

  /**
   * Adds the given element to a new set if it is not already in a set.
   *
   * @throws UnsupportedOperationException if the add operation is not
   *     supported by this union-find.
   */
  public void add(E e);

  /**
   * Unions the equivalence classes of {@code a} and {@code b} and returns the
   * representative of the resulting equivalence class.  The elements will be
   * added if they are not already present.
   *
   * @throws UnsupportedOperationException if the add operation is not
   *     supported by this union-find.
   */
  public E union(E a, E b);

  /** Returns the representative of the equivalence class of {@code e}. */
  public E find(E e);

  /**
   * Returns true if {@code a} and {@code b} belong to the same equivalence
   * class.
   *
   * @throws IllegalArgumentException if any argument is not an element of this
   *     structure.
   */
  public boolean areEquivalent(E a, E b);

  /** Returns an unmodifiable set of all elements added to the UnionFind. */
  public Set<E> elements();

  /**
   * Returns an immutable collection containing all equivalence classes.  The
   * returned collection represents a snapshot of the current state and will not
   * reflect changes made to the data structure.
   */
  public Collection<Set<E>> allEquivalenceClasses();

  /**
   * Returns the elements in the same equivalence class as {@code value}.
   *
   * @return an unmodifiable view. As equivalence classes are merged, this set
   *     will reflect those changes.
   * @throws IllegalArgumentException if a requested element does not belong
   *     to the structure.
   */
  public Set<E> findAll(final E value);

}
