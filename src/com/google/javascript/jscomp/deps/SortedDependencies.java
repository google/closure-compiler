/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import java.util.List;

/**
 * A sorted list of inputs with dependency information.
 * <p>
 * Performs a sort to make sure that an input always comes after its
 * dependencies.
 * <p>
 * Also exposes other information about the inputs, like which inputs
 * do not provide symbols.
 *
 * TODO(tbreisacher): Consider removing this interface, since it now
 * has only one class implementing it.
 */
public interface SortedDependencies<INPUT extends DependencyInfo> {

  /**
   * Return the input that gives us the given symbol.
   * @throws MissingProvideException An exception if there is no
   *     input for this symbol.
   */
  public INPUT getInputProviding(String symbol) throws MissingProvideException;

  /**
   * Return the input that gives us the given symbol, or null.
   */
  public INPUT maybeGetInputProviding(String symbol);

  public List<INPUT> getSortedList();

  /**
   * Gets all the strong dependencies of the given roots. The inputs must be returned in a stable
   * order. In other words, if A comes before B, and A does not transitively depend on B, then A
   * must also come before B in the returned list.
   */
  public List<INPUT> getSortedStrongDependenciesOf(List<INPUT> roots);

  /**
   * Gets all the weak dependencies of the given roots. The inputs must be returned in stable order.
   * In other words, if A comes before B, and A does not * transitively depend on B, then A must
   * also come before B in the returned * list.
   *
   * <p>The weak dependencies are those that are only reachable via type requires from the roots.
   * Note that if a root weakly requires another input, then all of its transitive dependencies
   * (strong or weak) that are not strongly reachable from the roots will be included. e.g. if A
   * weakly requires B, and B strongly requires C, and A is the sole root, then this will return B
   * and C. However, if we add D as a root, and D strongly requires C, then this will only return B.
   *
   * <p>Root inputs will never be in the returned list as they are all considered strong.
   */
  public List<INPUT> getSortedWeakDependenciesOf(List<INPUT> roots);

  /**
   * Gets all the strong dependencies of the given roots. The inputs must be returned in a stable
   * order. In other words, if A comes before B, and A does not transitively depend on B, then A
   * must also come before B in the returned list.
   *
   * @param sorted If true, get them in topologically sorted order. If false, get them in the
   *     original order they were passed to the compiler.
   */
  public List<INPUT> getStrongDependenciesOf(List<INPUT> roots, boolean sorted);

  public List<INPUT> getInputsWithoutProvides();

  public static class MissingProvideException extends Exception {
    public MissingProvideException(String provide) {
      super(provide);
    }

    public MissingProvideException(String provide, Exception e) {
      super(provide, e);
    }
  }
}
