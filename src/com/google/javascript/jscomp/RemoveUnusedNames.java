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

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.AnalyzeNameReferences.NameInfo;
import com.google.javascript.jscomp.NameReferenceGraph.Name;
import com.google.javascript.jscomp.NameReferenceGraph.Reference;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.rhino.Node;

import java.util.logging.Logger;

/**
 * Removes unused names.
 *
 */
class RemoveUnusedNames implements CompilerPass {

  private static final Logger logger =
    Logger.getLogger(RemoveUnusedNames.class.getName());

  private final AbstractCompiler compiler;

  private final boolean canModifyExterns;

  /**
   * Creates a new pass for removing unused prototype properties, based
   * on the uniqueness of property names.
   * @param compiler The compiler.
   */
  RemoveUnusedNames(AbstractCompiler compiler,
      boolean canModifyExterns) {
    this.compiler = compiler;
    this.canModifyExterns = canModifyExterns;
  }

  public void process(Node externRoot, Node root) {
    AnalyzeNameReferences analyzer =
        new AnalyzeNameReferences(compiler);
    analyzer.process(externRoot, root);
    removeUnusedProperties(analyzer.getGraph());
  }

  /**
   * Remove all properties under a given name if the property name is
   * never referenced.
   */
  private void removeUnusedProperties(NameReferenceGraph graph) {
    for (GraphNode<Name, Reference> node : graph.getNodes()) {
      Name name = node.getValue();
      NameInfo nameInfo = node.getAnnotation();
      if (nameInfo == null || !nameInfo.isReferenced()) {
        if (canModifyExterns || !name.isExtern()) {
          name.remove();
          compiler.reportCodeChange();
          logger.fine("Removed unused name" + name);
        }
      }
    }
  }
}
