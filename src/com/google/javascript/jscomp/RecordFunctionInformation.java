/*
 * Copyright 2008 Google Inc.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Comparator;

/**
 * Records information about functions and modules.
 *
*
*
 */
class RecordFunctionInformation extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Compiler compiler;
  private final FunctionNames functionNames;

  /**
   * Protocol buffer builder.
   */
  private final FunctionInformationMap.Builder mapBuilder;

  /**
   * Creates a record function information compiler pass.
   *
   * @param compiler       The JSCompiler
   * @param functionNames  Assigned function identifiers.
   */
  RecordFunctionInformation(Compiler compiler,
      FunctionNames functionNames) {
    this.compiler = compiler;
    this.functionNames = functionNames;
    this.mapBuilder = FunctionInformationMap.newBuilder();
  }

  /**
   * Returns the built-out map.
   */
  FunctionInformationMap getMap() {
    return mapBuilder.build();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);

    JSModuleGraph graph = compiler.getModuleGraph();
    if (graph == null) {
      addModuleInformation(null);
    } else {
      // The test expects a consistent module order.
      for (JSModule m : Sets.newTreeSet(new Comparator<JSModule>() {
            public int compare(JSModule o1, JSModule o2) {
              return o1.getName().compareTo(o2.getName());
            }
          }, graph.getAllModules())) {
        addModuleInformation(m);
      }
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getType() != Token.FUNCTION) {
      return;
    }

    int id = functionNames.getFunctionId(n);
    if (id < 0) {
      // Function node was added during compilation; don't instrument.
      return;
    }

    String compiledSource = compiler.toSource(n);
    JSModule module = t.getModule();
    mapBuilder.addEntry(FunctionInformationMap.Entry.newBuilder()
      .setId(id)
      .setSourceName(t.getSourceName())
      .setLineNumber(n.getLineno())
      .setModuleName(module == null ? "" : module.getName())
      .setSize(compiledSource.length())
      .setName(functionNames.getFunctionName(n))
      .setCompiledSource(compiledSource).build());
  }

  /**
   * Record a module's compiled source.  The view of the source we get
   * from function sources alone is not complete; it doesn't contain
   * assignments and function calls in the global scope which are
   * crucial to understanding how the application works.
   *
   * This version of the source is also written out to js_output_file,
   * module_output_path_prefix or other places.  Duplicating it here
   * simplifies the process of writing tools that combine and present
   * module and function for debugging purposes.
   */
  private void addModuleInformation(JSModule module) {
    String name;
    String source;
    if (module != null) {
      name = module.getName();
      source = compiler.toSource(module);
    } else {
      name = "";
      source = compiler.toSource();
    }

    mapBuilder.addModule(FunctionInformationMap.Module.newBuilder()
        .setName(name)
        .setCompiledSource(source).build());
  }
}
