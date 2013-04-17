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

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.AssignmentProperty;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.GlobalFunction;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.LiteralProperty;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.NameInfo;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.Symbol;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Removes unused properties from prototypes.
 *
 */
class RemoveUnusedPrototypeProperties implements
    SpecializationAwareCompilerPass {

  private static final Logger logger =
    Logger.getLogger(RemoveUnusedPrototypeProperties.class.getName());

  private final AbstractCompiler compiler;
  private final boolean canModifyExterns;
  private final boolean anchorUnusedVars;
  private SpecializeModule.SpecializationState specializationState;

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
      boolean canModifyExterns,
      boolean anchorUnusedVars) {
    this.compiler = compiler;
    this.canModifyExterns = canModifyExterns;
    this.anchorUnusedVars = anchorUnusedVars;
  }

  @Override
  public void enableSpecialization(SpecializeModule.SpecializationState state) {
    this.specializationState = state;
  }

  @Override
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
    for (NameInfo nameInfo : allNameInfo) {
      if (!nameInfo.isReferenced()) {
        for (Symbol declaration : nameInfo.getDeclarations()) {
          boolean canRemove = false;
          if (specializationState == null) {
            canRemove = true;
          } else {
            Node specializableFunction =
              getSpecializableFunctionFromSymbol(declaration);

            if (specializableFunction != null) {
              specializationState.reportRemovedFunction(
                  specializableFunction, null);
              canRemove = true;
            }
          }
          if (canRemove) {
            // Code-change reporting happens at the remove methods
            declaration.remove(compiler);
          }
        }
        logger.fine("Removed unused prototype property: " + nameInfo.name);
      }
    }
  }

  /**
   * Attempts to find a specializable function from the Symbol.
   */
  private Node getSpecializableFunctionFromSymbol(Symbol symbol) {
    Preconditions.checkNotNull(specializationState);

    Node specializableFunction = null;

    if (symbol instanceof GlobalFunction) {
      specializableFunction = ((GlobalFunction) symbol).getFunctionNode();
    } else if (symbol instanceof AssignmentProperty) {
      Node propertyValue = ((AssignmentProperty) symbol).getValue();
      if (propertyValue.isFunction()) {
        specializableFunction = propertyValue;
      }
    } else if (symbol instanceof LiteralProperty) {
      // Module specialization doesn't know how to handle these
      // because the "name" of the function isn't the name
      // it needs to add an unspecialized version of.

      return null;
    } else {
      Preconditions.checkState(false, "Should be unreachable.");
    }

    if (specializableFunction != null &&
        specializationState.canFixupFunction(specializableFunction)) {
      return specializableFunction;
    } else {
      return null;
    }
  }
}
