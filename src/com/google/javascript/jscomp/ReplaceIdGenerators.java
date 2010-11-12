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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces calls to id generators with ids.
 *
 * Use this to get unique and short ids.
 *
 */
class ReplaceIdGenerators implements CompilerPass {
  static final DiagnosticType NON_GLOBAL_ID_GENERATOR_CALL =
      DiagnosticType.error(
          "JSC_NON_GLOBAL_ID_GENERATOR_CALL",
          "Id generator call must be in the global scope");

  static final DiagnosticType CONDITIONAL_ID_GENERATOR_CALL =
      DiagnosticType.error(
          "JSC_CONDITIONAL_ID_GENERATOR_CALL",
          "Id generator call must be unconditional");

  private final AbstractCompiler compiler;
  private final Map<String, NameGenerator> nameGenerators;
  private final Map<String, List<Replacement>> idGeneratorMaps;

  public ReplaceIdGenerators(AbstractCompiler compiler,
                             Set<String> idGenerators) {
    this.compiler = compiler;
    nameGenerators = Maps.newHashMap();
    idGeneratorMaps = Maps.newLinkedHashMap();
    for (String idGenerator : idGenerators) {
      nameGenerators.put(
          idGenerator,
          new NameGenerator(Collections.<String>emptySet(), "", null));
      idGeneratorMaps.put(idGenerator, Lists.<Replacement>newArrayList());
    }
  }

  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Callback());
  }

  private class Callback extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() != Token.CALL) {
        return;
      }

      String callName = n.getFirstChild().getQualifiedName();
      NameGenerator nameGenerator = nameGenerators.get(callName);
      if (nameGenerator == null) {
        return;
      }
      List<Replacement> idGeneratorMap = idGeneratorMaps.get(callName);

      if (!t.inGlobalScope()) {
        // Warn about calls not in the global scope.
        compiler.report(t.makeError(n, NON_GLOBAL_ID_GENERATOR_CALL));
        return;
      }

      for (Node ancestor : n.getAncestors()) {
        if (NodeUtil.isControlStructure(ancestor)) {
          // Warn about conditional calls.
          compiler.report(t.makeError(n, CONDITIONAL_ID_GENERATOR_CALL));
          return;
        }
      }

      String nextName = nameGenerator.generateNextName();

      parent.replaceChild(n, Node.newString(nextName));
      idGeneratorMap.add(
          new Replacement(nextName, t.getSourceName(), t.getLineNumber()));

      compiler.reportCodeChange();
    }
  }

  /**
   * @return the id generator map.
   */
  public String getIdGeneratorMap() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<Replacement>> entry :
        idGeneratorMaps.entrySet()) {
      sb.append("[");
      sb.append(entry.getKey());
      sb.append("]\n\n");
      for (Replacement replacement : entry.getValue()) {
        sb.append(replacement.toString());
        sb.append("\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  private static class Replacement {
    private final String name;
    private final String sourceName;
    private final int lineNumber;

    private Replacement(String name, String sourceName, int lineNumber) {
      this.name = name;
      this.sourceName = sourceName;
      this.lineNumber = lineNumber;
    }

    @Override
    public String toString() {
      return name + ":" + sourceName + ":" + lineNumber;
    }
  }
}
