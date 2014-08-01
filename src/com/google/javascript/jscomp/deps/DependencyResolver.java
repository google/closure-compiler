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

package com.google.javascript.jscomp.deps;

import java.util.Collection;
import java.util.Set;

/**
 * Interface for DependencyResolver to keep in line with 
 * {@link com.google.javascript.jscomp.deps.DefaultDependencyResolver}, which doesn't
 * provide an interface. This is so that later on, we can merge back with it.
 */
public interface DependencyResolver {

  /** Gets a list of dependencies for the provided code. */
  public Collection<String> getDependencies(String code)
      throws ServiceException;

  /** Gets a list of dependencies for  *the provided list of symbols. */
  public Collection<String> getDependencies(Collection<String> symbols)
      throws ServiceException;

  /**
   * @param code The raw code to be parsed for requires.
   * @param seen The set of already seen symbols.
   * @param addClosureBaseFile Indicates whether the closure base file should be
   *        added to the dependency list.
   * @return A list of filenames for each of the dependencies for the provided
   *         code.
   * @throws ServiceException
   */
  public Collection<String> getDependencies(String code,
      Set<String> seen, boolean addClosureBaseFile) throws ServiceException;

  /**
   * @param symbols A list of required symbols.
   * @param seen The set of already seen symbols.
   * @return A list of filenames for each of the required symbols.
   * @throws ServiceException
   */
  public Collection<String> getDependencies(Collection<String> symbols,
      Set<String> seen) throws ServiceException;

}
