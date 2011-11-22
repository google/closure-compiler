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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
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

  static final DiagnosticType CONFLICTING_GENERATOR_TYPE =
      DiagnosticType.error(
          "JSC_CONFLICTING_ID_GENERATOR_TYPE",
          "Id generator can only be consistent or inconsistent");

  private final AbstractCompiler compiler;
  private final Map<String, NameGenerator> nameGenerators;
  private final Map<String, NameGenerator> consistNameGenerators;
  private final Map<String, Map<String, String>> consistNameMap;

  private final Map<String, List<Replacement>> idGeneratorMaps;

  public ReplaceIdGenerators(AbstractCompiler compiler, Set<String> idGens) {
    this.compiler = compiler;
    nameGenerators = Maps.newLinkedHashMap();
    consistNameGenerators = Maps.newLinkedHashMap();
    idGeneratorMaps = Maps.newLinkedHashMap();
    consistNameMap = Maps.newLinkedHashMap();

    if (idGens != null) {
      for(String gen : idGens) {
        nameGenerators.put(gen,
            new NameGenerator(Collections.<String>emptySet(), "", null));
        idGeneratorMaps.put(gen, Lists.<Replacement>newLinkedList());
      }
    }
  }

  private class GatherGenerators extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo doc = n.getJSDocInfo();
      if (doc == null) {
        return;
      }

      if (!doc.isConsistentIdGenerator() &&
          !doc.isIdGenerator()) {
        return;
      }

      if (doc.isConsistentIdGenerator() && doc.isIdGenerator()) {
        compiler.report(t.makeError(n, CONFLICTING_GENERATOR_TYPE));
      }

      String name = null;
      if (n.isAssign()) {
        name = n.getFirstChild().getQualifiedName();
      } else if (n.isVar()) {
        name = n.getFirstChild().getString();
      } else if (n.isFunction()){
        name = n.getFirstChild().getString();
        if (name.isEmpty()) {
          return;
        }
      }

      // TODO(user): Error on function that has both. Or redeclartion
      // on the same function.
      if (doc.isConsistentIdGenerator()) {
        consistNameGenerators.put(name,
            new NameGenerator(Collections.<String>emptySet(), "", null));
        consistNameMap.put(name, Maps.<String, String>newLinkedHashMap());
      } else {
        nameGenerators.put(name,
          new NameGenerator(Collections.<String>emptySet(), "", null));
      }
      idGeneratorMaps.put(name, Lists.<Replacement>newArrayList());
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new GatherGenerators());
    if (!nameGenerators.isEmpty() || !this.consistNameGenerators.isEmpty()) {
      NodeTraversal.traverse(compiler, root, new ReplaceGenerators());
    }
  }

  private class ReplaceGenerators extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isCall()) {
        return;
      }

      String callName = n.getFirstChild().getQualifiedName();
      boolean consistent = false;
      NameGenerator nameGenerator = nameGenerators.get(callName);
      if (nameGenerator == null) {
        nameGenerator = consistNameGenerators.get(callName);
        consistent = true;
      }
      if (nameGenerator == null) {
        return;
      }

      if (!t.inGlobalScope() && !consistent) {
        // Warn about calls not in the global scope.
        compiler.report(t.makeError(n, NON_GLOBAL_ID_GENERATOR_CALL));
        return;
      }

      if (!consistent) {
        for (Node ancestor : n.getAncestors()) {
          if (NodeUtil.isControlStructure(ancestor)) {
            // Warn about conditional calls.
            compiler.report(t.makeError(n, CONDITIONAL_ID_GENERATOR_CALL));
            return;
          }
        }
      }

      Node id = n.getFirstChild().getNext();

      // TODO(user): Error on id not a string literal.
      if (!id.isString()) {
        return;
      }

      List<Replacement> idGeneratorMap = idGeneratorMaps.get(callName);
      String rename = null;

      if (consistent) {
        Map<String, String> entry = consistNameMap.get(callName);
        rename = entry.get(id.getString());
        if (rename == null) {
          rename = nameGenerator.generateNextName();
          entry.put(id.getString(), rename);
        }
      } else {
        rename = nameGenerator.generateNextName();
      }

      parent.replaceChild(n, IR.string(rename));
      idGeneratorMap.add(
          new Replacement(rename, t.getSourceName(), t.getLineNumber()));

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
