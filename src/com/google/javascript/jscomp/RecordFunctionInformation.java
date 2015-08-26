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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Records information about functions and modules.
 *
 */
class RecordFunctionInformation extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Compiler compiler;
  private final FunctionNames functionNames;
  private final JSModuleGraph moduleGraph;

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
    this.moduleGraph = compiler.getModuleGraph();
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
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!n.isFunction()) {
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
      .setSourceName(NodeUtil.getSourceName(n))
      .setLineNumber(n.getLineno())
      .setModuleName(moduleGraph == null ? "" : module.getName())
      .setSize(compiledSource.length())
      .setName(functionNames.getFunctionName(n))
      .setCompiledSource(compiledSource).build());
  }
}
