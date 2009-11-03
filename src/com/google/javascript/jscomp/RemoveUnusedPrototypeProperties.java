/*
 * Copyright 2006 Google Inc.
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

import com.google.javascript.jscomp.AnalyzePrototypeProperties.NameInfo;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Symbol;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Removes unused properties from prototypes.
 *
*
*
 */
class RemoveUnusedPrototypeProperties implements CompilerPass {

  private static final Logger logger =
    Logger.getLogger(RemoveUnusedPrototypeProperties.class.getName());

  private final AbstractCompiler compiler;
  private final boolean canModifyExterns;
  private final boolean anchorUnusedVars;

  /**
   * Creates a new pass for removing unused prototype properties, based
   * on the uniqueness of property names.
   * @param compiler The compiler.
   * @param canModifyExterns If true, then we can remove prototype
   *     properties that are declared in the externs file.
   * @param anchorUnusedVars If true, then we must keep unused variables
   *     and the prototype properties they reference, even if they are
   *     never used.
   */
  RemoveUnusedPrototypeProperties(AbstractCompiler compiler,
      boolean canModifyExterns, boolean anchorUnusedVars) {
    this.compiler = compiler;
    this.canModifyExterns = canModifyExterns;
    this.anchorUnusedVars = anchorUnusedVars;
  }

  public void process(Node externRoot, Node root) {
    AnalyzePrototypeProperties analyzer =
        new AnalyzePrototypeProperties(compiler,
            null /* no module graph */, canModifyExterns, anchorUnusedVars);
    analyzer.process(externRoot, root);
    removeUnusedSymbols(analyzer.getAllNameInfo());
  }

  /**
   * Remove all properties under a given name if the property name is
   * never referenced.
   */
  private void removeUnusedSymbols(Collection<NameInfo> allNameInfo) {
    boolean changed = false;
    for (NameInfo nameInfo : allNameInfo) {
      if (!nameInfo.isReferenced()) {
        for (Symbol declaration : nameInfo.getDeclarations()) {
          declaration.remove();
          changed = true;
        }

        logger.fine("Removed unused prototype property: " + nameInfo.name);
      }
    }

    if (changed) {
      compiler.reportCodeChange();
    }
  }
}
