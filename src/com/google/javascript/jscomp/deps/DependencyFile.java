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

package com.google.javascript.jscomp.deps;

import static com.google.javascript.jscomp.deps.DefaultDependencyResolver.CLOSURE_BASE;
import static com.google.javascript.jscomp.deps.DefaultDependencyResolver.CLOSURE_BASE_PROVIDE;

import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.LoggerErrorManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SourceFile containing dependency information.  Delegates file handling to
 * another SourceFile such that a VirtualFile, LocalFile or RemoteFile can be
 * used.
 */
public final class DependencyFile implements SourceFile {

  /** Map of name spaces to their dependency info. */
  private final Map<String, DependencyInfo> dependencies = new HashMap<>();

  /** A source file to delegate functionality too. */
  private final SourceFile delegate;

  /** Logger for DependencyResolver. */
  private static Logger logger =
      Logger.getLogger(DependencyFile.class.getName());

  /** Creates a new dependency file. */
  public DependencyFile(SourceFile delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getName() throws ServiceException {
    return delegate.getName();
  }

  @Override
  public String getContent() throws ServiceException {
    return delegate.getContent();
  }

  @Override
  public boolean wasModified() throws ServiceException {
    return delegate.wasModified();
  }

  /**
   * Ensures that the dependency graph is up to date and reloads the graph if
   * necessary.
   */
  public void ensureUpToDate() throws ServiceException {
    if (dependencies.isEmpty() || wasModified()) {
      loadGraph();
    }
  }

  /**
   * Gets the dependency info for the provided symbol, if contained in this
   * dependency file.
   */
  public DependencyInfo getDependencyInfo(String symbol) {
    return dependencies.get(symbol);
  }

  /** Loads the dependency graph. */
  private void loadGraph() throws ServiceException {
    dependencies.clear();

    logger.info("Loading dependency graph");

    // Parse the deps.js file.
    ErrorManager errorManager = new LoggerErrorManager(logger);
    DepsFileParser parser =
        new DepsFileParser(errorManager);
    List<DependencyInfo> depInfos =
        parser.parseFile(getName(), getContent());

    // Ensure the parse succeeded.
    if (!parser.didParseSucceed()) {
      throw new ServiceException("Problem parsing " + getName()
          + ". See logs for details.");
    }
    // Incorporate the dependencies into our maps.
    for (DependencyInfo depInfo : depInfos) {
      for (String provide : depInfo.getProvides()) {
        DependencyInfo existing = dependencies.get(provide);
        if (existing != null && !existing.equals(depInfo)) {
          throw new ServiceException("Duplicate provide of " + provide
              + ". Was provided by " + existing.getPathRelativeToClosureBase()
              + " and " + depInfo.getPathRelativeToClosureBase());
        }
        dependencies.put(provide, depInfo);
      }
    }

    List<String> provides = new ArrayList<>();
    provides.add(CLOSURE_BASE_PROVIDE);

    // Add implicit base.js entry.
    dependencies.put(CLOSURE_BASE_PROVIDE,
        new SimpleDependencyInfo(CLOSURE_BASE, CLOSURE_BASE,
            provides,
            Collections.<String>emptyList(), false));
    errorManager.generateReport();

    logger.info("Dependencies loaded");
  }

}
