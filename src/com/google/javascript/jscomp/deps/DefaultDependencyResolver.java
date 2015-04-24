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

package com.google.javascript.jscomp.deps;

import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.LoggerErrorManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Class for resolving Closure dependencies.
 *
 * Given a valid deps.js file the dependency tree is parsed and stored in
 * memory. The DependencyResolver can then be used to calculate the full list of
 * transitive dependencies from: a block of code (
 * {@link #getDependencies(String)}), or a list of symbols
 * {@link #getDependencies(Collection)}.
 *
 */
public final class DefaultDependencyResolver implements DependencyResolver  {

  /** Filename for Closure's base.js file which is always added. */
  static final String CLOSURE_BASE = "base.js";

  /** Provide for Closure's base.js. */
  static final String CLOSURE_BASE_PROVIDE = "goog";

  /** Source files used to look up the dependencies. */
  private final List<DependencyFile> depsFiles;

  /**
   * Flag that determines if the resolver will strictly require all
   * goog.requires.
   * */
  private final boolean strictRequires;

  /** Logger for DependencyResolver. */
  private static Logger logger =
      Logger.getLogger(DefaultDependencyResolver.class.getName());

  /**
   * Creates a new dependency resolver.
   * @param depsFiles List of deps file.
   * @param strictRequires Determines if the resolver will through an exception
   *     on a missing dependency.
   */
  public DefaultDependencyResolver(
      List<DependencyFile> depsFiles, boolean strictRequires) {
    this.depsFiles = depsFiles;
    this.strictRequires = strictRequires;
  }

  /** Gets a list of dependencies for the provided code. */
  @Override
  public List<String> getDependencies(String code)
      throws ServiceException {
    return getDependencies(parseRequires(code, true));
  }

  /** Gets a list of dependencies for the provided list of symbols. */
  @Override
  public List<String> getDependencies(Collection<String> symbols)
      throws ServiceException {
    return getDependencies(symbols, new HashSet<String>());
  }

  /**
   * @param code The raw code to be parsed for requires.
   * @param seen The set of already seen symbols.
   * @param addClosureBaseFile Indicates whether the closure base file should be
   *        added to the dependency list.
   * @return A list of filenames for each of the dependencies for the provided
   *         code.
   * @throws ServiceException
   */
  @Override
  public List<String> getDependencies(String code, Set<String> seen,
      boolean addClosureBaseFile) throws ServiceException {
    return getDependencies(parseRequires(code, addClosureBaseFile), seen);
  }

  /**
   * @param symbols A list of required symbols.
   * @param seen The set of already seen symbols.
   * @return A list of filenames for each of the required symbols.
   * @throws ServiceException
   */
  @Override
  public List<String> getDependencies(Collection<String> symbols,
      Set<String> seen) throws ServiceException {
    List<String> list = new ArrayList<>();
    for (DependencyFile depsFile : depsFiles) {
      depsFile.ensureUpToDate();
    }

    for (String symbol : symbols) {
      addDependency(symbol, seen, list);
    }
    return list;
  }

  /**
   * Adds all the transitive dependencies for a symbol to the provided list. The
   * set is used to avoid adding dupes while keeping the correct order. NOTE:
   * Use of a LinkedHashSet would require reversing the results to get correct
   * dependency ordering.
   */
  private void addDependency(String symbol, Set<String> seen, List<String> list)
      throws ServiceException {
    DependencyInfo dependency = getDependencyInfo(symbol);
    if (dependency == null) {
      if (this.strictRequires) {
        throw new ServiceException("Unknown require of " + symbol);
      }
    } else if (!seen.containsAll(dependency.getProvides())) {
      seen.addAll(dependency.getProvides());
      for (String require : dependency.getRequires()) {
        addDependency(require, seen, list);
      }
      list.add(dependency.getPathRelativeToClosureBase());
    }
  }

  /**
   * Parses a block of code for goog.require statements and extracts the
   * required symbols.
   */
  private Collection<String> parseRequires(
      String code, boolean addClosureBase) {
    ErrorManager errorManager = new LoggerErrorManager(logger);
    JsFileParser parser = new JsFileParser(errorManager);
    DependencyInfo deps =
        parser.parseFile("<unknown path>", "<unknown path>", code);
    List<String> requires = new ArrayList<>();
    if (addClosureBase) {
      requires.add(CLOSURE_BASE_PROVIDE);
    }
    requires.addAll(deps.getRequires());
    errorManager.generateReport();
    return requires;
  }

  /** Looks at each of the dependency files for dependency information. */
  private DependencyInfo getDependencyInfo(String symbol) {
    for (DependencyFile depsFile : depsFiles) {
      DependencyInfo di = depsFile.getDependencyInfo(symbol);
      if (di != null) {
        return di;
      }
    }
    return null;
  }

}
