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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NameReferenceGraph.Name;
import com.google.javascript.jscomp.NameReferenceGraph.Reference;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.Annotation;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.rhino.Node;

/**
 * Analyzes names and references usage by determining:
 * <p><ol>
 * <li>If the name is reachable from the {@link NameReferenceGraph#MAIN}.</li>
 * <li>as well as the deepest common module that references it.</li>
 * </ol>
 *
 * The two pieces of information will be annotated to {@link NameReferenceGraph}
 * by {@link NameInfo} objects.
 *
 * This is an analysis based on {@link AnalyzeNameReferences} using the more
 * accurate graph and will soon replace it.
 *
 */
class AnalyzeNameReferences implements CompilerPass {

  private NameReferenceGraph graph;
  private final JSModuleGraph moduleGraph;
  private final AbstractCompiler compiler;

  AnalyzeNameReferences(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.moduleGraph = compiler.getModuleGraph();
  }

  @Override
  public void process(Node externs, Node root) {
    NameReferenceGraphConstruction gc =
        new NameReferenceGraphConstruction(compiler);
    gc.process(externs, root);
    graph = gc.getNameReferenceGraph();
    FixedPointGraphTraversal<Name, Reference> t =
        FixedPointGraphTraversal.newTraversal(new PropagateReferences());
    getInfo(graph.MAIN).markReference(null);
    t.computeFixedPoint(graph, Sets.newHashSet(graph.MAIN));
  }

  public NameReferenceGraph getGraph() {
    return graph;
  }

  private class PropagateReferences implements EdgeCallback<Name, Reference> {
    @Override
    public boolean traverseEdge(Name start, Reference edge, Name dest) {
      NameInfo startInfo = getInfo(start);
      NameInfo destInfo = getInfo(dest);
      if (startInfo.isReferenced()) {
        JSModule startModule = startInfo.getDeepestCommonModuleRef();
        if (startModule != null &&
            moduleGraph.dependsOn(startModule, edge.getModule())) {
          return destInfo.markReference(startModule);
        } else {
          return destInfo.markReference(edge.getModule());
        }
      }
      return false;
    }
  }

  private NameInfo getInfo(Name symbol) {
    GraphNode<Name, Reference> name = graph.getNode(symbol);
    NameInfo info = name.getAnnotation();
    if (info == null) {
      info = new NameInfo();
      name.setAnnotation(info);
    }
    return info;
  }

  final class NameInfo implements Annotation {
    private boolean referenced = false;
    private JSModule deepestCommonModuleRef = null;

    /** Determines whether we've marked a reference to this property name. */
    boolean isReferenced() {
      return referenced;
    }

    /**
     * Returns the deepest common module of all the references to this
     * property.
     */
    JSModule getDeepestCommonModuleRef() {
      return deepestCommonModuleRef;
    }

    /**
     * Mark a reference in a given module to this property name, and record
     * the deepest common module reference.
     * @param module The module where it was referenced.
     * @return Whether the name info has changed.
     */
    boolean markReference(JSModule module) {
      boolean hasChanged = false;
      if (!referenced) {
        referenced = true;
        hasChanged = true;
      }

      if (moduleGraph != null) {
        JSModule originalDeepestCommon = deepestCommonModuleRef;

        if (deepestCommonModuleRef == null) {
          deepestCommonModuleRef = module;
        } else {
          deepestCommonModuleRef =
              moduleGraph.getDeepestCommonDependencyInclusive(
                  deepestCommonModuleRef, module);
        }

        if (originalDeepestCommon != deepestCommonModuleRef) {
          hasChanged = true;
        }
      }
      return hasChanged;
    }
  }
}
