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

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
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

  static final DiagnosticType INVALID_GENERATOR_ID_MAPPING =
      DiagnosticType.error(
          "JSC_INVALID_GENERATOR_ID_MAPPING",
          "Invalid generator id mapping. {0}");

  private final AbstractCompiler compiler;
  private final Map<String, NameSupplier> nameGenerators;
  private final Map<String, NameSupplier> consistNameGenerators;
  private final Map<String, Map<String, String>> consistNameMap;

  private final Map<String, Map<String, String>> idGeneratorMaps;
  private final Map<String, BiMap<String, String>> previousMap;

  private final boolean generatePseudoNames;

  public ReplaceIdGenerators(
      AbstractCompiler compiler, Set<String> idGens,
      boolean generatePseudoNames,
      String previousMapSerialized) {
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    nameGenerators = Maps.newLinkedHashMap();
    consistNameGenerators = Maps.newLinkedHashMap();
    idGeneratorMaps = Maps.newLinkedHashMap();
    consistNameMap = Maps.newLinkedHashMap();

    Map<String, BiMap<String, String>> previousMap;
    previousMap = parsePreviousResults(previousMapSerialized);
    this.previousMap = previousMap;

    if (idGens != null) {
      for (String gen : idGens) {
        nameGenerators.put(gen, createNameSupplier(previousMap.get(gen)));
        idGeneratorMaps.put(gen, Maps.<String, String>newLinkedHashMap());
      }
    }
  }

  private static interface NameSupplier {
    String getName(String id, String name);
  }

  private static class ObfuscatedNameSuppier implements NameSupplier {
    private final NameGenerator generator;
    private final Map<String, String> previousMappings;
    public ObfuscatedNameSuppier(BiMap<String, String> previousMappings) {
      this.previousMappings = previousMappings.inverse();
      this.generator =
          new NameGenerator(previousMappings.keySet(), "", null);
    }

    @Override
    public String getName(String id, String name) {
      String newName = previousMappings.get(id);
      if (newName == null) {
        newName = generator.generateNextName();
      }
      return newName;
    }
  }

  private static class PseudoNameSuppier implements NameSupplier {
    private int counter = 0;
    @Override
    public String getName(String id, String name) {
      return name + "$" + counter++;
    }
  }

  private NameSupplier createNameSupplier(
      BiMap<String, String> previousMappings) {
    previousMappings = previousMappings != null ?
        previousMappings :
        ImmutableBiMap.<String, String>of();
    if (generatePseudoNames) {
      return new PseudoNameSuppier();
    } else {
      return new ObfuscatedNameSuppier(previousMappings);
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
        consistNameGenerators.put(
            name, createNameSupplier(previousMap.get(name)));
        consistNameMap.put(name, Maps.<String, String>newLinkedHashMap());
      } else {
        nameGenerators.put(name, createNameSupplier(previousMap.get(name)));
      }
      idGeneratorMaps.put(name, Maps.<String, String>newLinkedHashMap());
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
      NameSupplier nameGenerator = nameGenerators.get(callName);
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

      Map<String, String> idGeneratorMap = idGeneratorMaps.get(callName);
      String rename = null;

      String name = id.getString();
      String instanceId = getIdForGeneratorNode(consistent, id);
      if (consistent) {
        Map<String, String> entry = consistNameMap.get(callName);
        rename = entry.get(instanceId);
        if (rename == null) {
          rename = nameGenerator.getName(instanceId, name);
          entry.put(instanceId, rename);
        }
      } else {
        rename = nameGenerator.getName(instanceId, name);
      }

      parent.replaceChild(n, IR.string(rename));
      idGeneratorMap.put(rename, instanceId);

      compiler.reportCodeChange();
    }
  }

  /**
   * @return The serialize map of generators and their ids and their
   *     replacements.
   */
  public String getSerializedIdMappings() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Map<String, String>> replacements :
        idGeneratorMaps.entrySet()) {
      if (!replacements.getValue().isEmpty()) {
        sb.append("[");
        sb.append(replacements.getKey());
        sb.append("]\n\n");
        for (Map.Entry<String, String> replacement :
            replacements.getValue().entrySet()) {
          sb.append(replacement.getKey());
          sb.append(':');
          sb.append(replacement.getValue());
          sb.append("\n");
        }
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private Map<String, BiMap<String, String>> parsePreviousResults(
      String serializedMap) {

    //
    // The expected format looks like this:
    //
    // [generatorName]
    // someId:someFile:theLine:theColumn
    //
    //

    if (serializedMap == null || serializedMap.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, BiMap<String, String>> resultMap = Maps.newHashMap();
    BufferedReader reader = new BufferedReader(new StringReader(serializedMap));
    BiMap<String, String> currentSectionMap = null;

    String line;
    int lineIndex = 0;
    try {
      while ((line = reader.readLine()) != null) {
        lineIndex++;
        if (line.isEmpty()) {
          continue;
        }
        if (line.charAt(0) == '[') {
          String currentSection = line.substring(1, line.length() - 1);
          currentSectionMap = resultMap.get(currentSection);
          if (currentSectionMap == null) {
            currentSectionMap = HashBiMap.create();
            resultMap.put(currentSection, currentSectionMap);
          } else {
            reportInvalidLine(line, lineIndex);
            return Collections.emptyMap();
          }
        } else {
          int split = line.indexOf(':');
          if (split != -1) {
            String name = line.substring(0, split);
            String location = line.substring(split + 1, line.length());
            currentSectionMap.put(name, location);
          } else {
            reportInvalidLine(line, lineIndex);
            return Collections.emptyMap();
          }
        }
      }
    } catch (IOException e) {
      JSError.make(INVALID_GENERATOR_ID_MAPPING, e.getMessage());
    }
    return resultMap;
  }

  private void reportInvalidLine(String line, int lineIndex) {
    JSError.make(INVALID_GENERATOR_ID_MAPPING,
        "line(" + line + "): " + lineIndex);
  }

  String getIdForGeneratorNode(boolean consistent, Node n) {
    Preconditions.checkState(n.isString());
    if (consistent) {
      return n.getString();
    } else {
      return n.getSourceFileName() + ':' + n.getLineno() + ":" + n.getCharno();
    }
  }
}
