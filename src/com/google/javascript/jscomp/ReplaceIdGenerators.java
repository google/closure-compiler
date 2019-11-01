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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.primitives.Booleans;
import com.google.debugging.sourcemap.Base64;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Replaces calls to id generators with ids.
 *
 * Use this to get unique and short ids.
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
          "Id generator can only be one of " +
          "consistent, inconsistent, mapped, stable, or xid.");

  static final DiagnosticType INVALID_GENERATOR_ID_MAPPING =
      DiagnosticType.error(
          "JSC_INVALID_GENERATOR_ID_MAPPING",
          "Invalid generator id mapping. {0}");

  static final DiagnosticType MISSING_NAME_MAP_FOR_GENERATOR =
      DiagnosticType.warning(
          "JSC_MISSING_NAME_MAP_FOR_GENERATOR",
          "The mapped id generator, does not have a renaming map supplied.");

  static final DiagnosticType INVALID_GENERATOR_PARAMETER =
      DiagnosticType.warning(
          "JSC_INVALID_GENERATOR_PARAMETER",
          "An id generator must be called with a literal.");

  static final DiagnosticType SHORTHAND_FUNCTION_NOT_SUPPORTED_IN_ID_GEN =
      DiagnosticType.error(
          "JSC_SHORTHAND_FUNCTION_NOT_SUPPORTED_IN_ID_GEN",
          "Object literal shorthand functions is not allowed in the "
          + "arguments of an id generator");

  static final DiagnosticType COMPUTED_PROP_NOT_SUPPORTED_IN_ID_GEN =
      DiagnosticType.error(
          "JSC_COMPUTED_PROP_NOT_SUPPORTED_IN_ID_GEN",
          "Object literal computed property name is not allowed in the "
          + "arguments of an id generator");


  private final AbstractCompiler compiler;
  private final Map<String, NameSupplier> nameGenerators;
  private final Map<String, Map<String, String>> consistNameMap;

  private final Map<String, Map<String, String>> idGeneratorMaps;
  private final Map<String, BiMap<String, String>> previousMap;

  private final boolean generatePseudoNames;
  private final Xid.HashFunction xidHashFunction;

  public ReplaceIdGenerators(
      AbstractCompiler compiler, Map<String, RenamingMap> idGens,
      boolean generatePseudoNames,
      String previousMapSerialized,
      Xid.HashFunction xidHashFunction) {
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    this.xidHashFunction = xidHashFunction;
    nameGenerators = new LinkedHashMap<>();
    idGeneratorMaps = new LinkedHashMap<>();
    consistNameMap = new LinkedHashMap<>();

    Map<String, BiMap<String, String>> previousMap;
    previousMap = IdMappingUtil.parseSerializedIdMappings(previousMapSerialized);
    this.previousMap = previousMap;

    if (idGens != null) {
      for (Entry<String, RenamingMap> gen : idGens.entrySet()) {
        String name = gen.getKey();
        RenamingMap map = gen.getValue();
        if (map instanceof UniqueRenamingToken) {
          nameGenerators.put(name,
              createNameSupplier(
                  RenameStrategy.INCONSISTENT, previousMap.get(name)));
        } else {
          nameGenerators.put(name,
              createNameSupplier(
                  RenameStrategy.MAPPED, map));
        }
        idGeneratorMaps.put(name, new LinkedHashMap<String, String>());
      }
    }
  }

  enum RenameStrategy {
    CONSISTENT,
    INCONSISTENT,
    MAPPED,
    STABLE,
    XID
  }

  private static interface NameSupplier {
    String getName(String id, String name);
    RenameStrategy getRenameStrategy();
  }

  private static class ObfuscatedNameSupplier implements NameSupplier {
    private final NameGenerator generator;
    private final Map<String, String> previousMappings;
    private final RenameStrategy renameStrategy;

    public ObfuscatedNameSupplier(
        RenameStrategy renameStrategy, BiMap<String, String> previousMappings) {
      this.previousMappings = previousMappings.inverse();
      this.generator =
          new DefaultNameGenerator(previousMappings.keySet(), "", null);
      this.renameStrategy = renameStrategy;
    }

    @Override
    public String getName(String id, String name) {
      String newName = previousMappings.get(id);
      if (newName == null) {
        newName = generator.generateNextName();
      }
      return newName;
    }

    @Override
    public RenameStrategy getRenameStrategy() {
      return renameStrategy;
    }
  }

  private static class PseudoNameSupplier implements NameSupplier {
    private int counter = 0;
    private final RenameStrategy renameStrategy;

    public PseudoNameSupplier(RenameStrategy renameStrategy) {
      this.renameStrategy = renameStrategy;
    }

    @Override
    public String getName(String id, String name) {
      if (renameStrategy == RenameStrategy.INCONSISTENT) {
        return name + "$" + counter++;
      }
      return name + "$0";
    }

    @Override
    public RenameStrategy getRenameStrategy() {
      return renameStrategy;
    }
  }

  private static class StableNameSupplier implements NameSupplier {
    @Override
    public String getName(String id, String name) {
      return Base64.base64EncodeInt(name.hashCode());
    }
    @Override
    public RenameStrategy getRenameStrategy() {
      return RenameStrategy.STABLE;
    }
  }

  private static class XidNameSupplier implements NameSupplier {
    final Xid xid;

    XidNameSupplier(Xid.HashFunction hashFunction) {
      this.xid = hashFunction == null ? new Xid() : new Xid(hashFunction);
    }

    @Override
    public String getName(String id, String name) {
      return xid.get(name);
    }
    @Override
    public RenameStrategy getRenameStrategy() {
      return RenameStrategy.XID;
    }
  }

  private static class MappedNameSupplier implements NameSupplier {
    private final RenamingMap map;

    MappedNameSupplier(RenamingMap map) {
      this.map = map;
    }

    @Override
    public String getName(String id, String name) {
      return map.get(name);
    }

    @Override
    public RenameStrategy getRenameStrategy() {
      return RenameStrategy.MAPPED;
    }
  }

  private NameSupplier createNameSupplier(
      RenameStrategy renameStrategy, BiMap<String, String> previousMappings) {
    previousMappings = previousMappings != null ?
        previousMappings :
        ImmutableBiMap.<String, String>of();
    if (renameStrategy == RenameStrategy.STABLE) {
      return new StableNameSupplier();
    } else if (renameStrategy == RenameStrategy.XID) {
      return new XidNameSupplier(this.xidHashFunction);
    } else if (generatePseudoNames) {
      return new PseudoNameSupplier(renameStrategy);
    } else {
      return new ObfuscatedNameSupplier(renameStrategy, previousMappings);
    }
  }

  private static NameSupplier createNameSupplier(
      RenameStrategy renameStrategy, RenamingMap mappings) {
    checkState(renameStrategy == RenameStrategy.MAPPED);
    return new MappedNameSupplier(mappings);
  }

  private class GatherGenerators extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal unused, Node n, Node parent) {
      JSDocInfo doc = n.getJSDocInfo();
      if (doc == null) {
        return;
      }

      int numGeneratorAnnotations =
          Booleans.countTrue(
              doc.isConsistentIdGenerator(),
              doc.isIdGenerator(),
              doc.isStableIdGenerator(),
              doc.isXidGenerator(),
              doc.isMappedIdGenerator());
      if (numGeneratorAnnotations == 0) {
        return;
      } else if (numGeneratorAnnotations > 1) {
        compiler.report(JSError.make(n, CONFLICTING_GENERATOR_TYPE));
      }

      String name = null;
      if (n.isAssign()) {
        name = n.getFirstChild().getQualifiedName();
      } else if (NodeUtil.isNameDeclaration(n)) {
        name = n.getFirstChild().getString();
      } else if (n.isFunction()){
        name = n.getFirstChild().getString();
        if (name.isEmpty()) {
          return;
        }
      }

      if (doc.isConsistentIdGenerator()) {
        consistNameMap.put(name, new LinkedHashMap<String, String>());
        nameGenerators.put(
            name, createNameSupplier(
                RenameStrategy.CONSISTENT, previousMap.get(name)));
      } else if (doc.isStableIdGenerator()) {
        nameGenerators.put(
            name, createNameSupplier(
                RenameStrategy.STABLE, previousMap.get(name)));
      } else if (doc.isXidGenerator()) {
        nameGenerators.put(
            name, createNameSupplier(
                RenameStrategy.XID, previousMap.get(name)));
      } else if (doc.isIdGenerator()) {
        nameGenerators.put(
            name, createNameSupplier(
                RenameStrategy.INCONSISTENT, previousMap.get(name)));
      } else if (doc.isMappedIdGenerator()) {
        NameSupplier supplier = nameGenerators.get(name);
        if (supplier == null
            || supplier.getRenameStrategy() != RenameStrategy.MAPPED) {
          compiler.report(JSError.make(n, MISSING_NAME_MAP_FOR_GENERATOR));
          // skip registering the name in the list of Generators if there no
          // mapping.
          return;
        }
      } else {
        throw new IllegalStateException("unexpected");
      }
      idGeneratorMaps.put(name, new LinkedHashMap<String, String>());
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new GatherGenerators());
    if (!nameGenerators.isEmpty()) {
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
      NameSupplier nameGenerator = nameGenerators.get(callName);
      if (nameGenerator == null) {
        return;
      }

      if (!t.inGlobalHoistScope()
          && nameGenerator.getRenameStrategy() == RenameStrategy.INCONSISTENT) {
        // Warn about calls not in the global scope.
        compiler.report(JSError.make(n, NON_GLOBAL_ID_GENERATOR_CALL));
        return;
      }

      if (nameGenerator.getRenameStrategy() == RenameStrategy.INCONSISTENT) {
        for (Node ancestor : n.getAncestors()) {
          if (NodeUtil.isControlStructure(ancestor)) {
            // Warn about conditional calls.
            compiler.report(JSError.make(n, CONDITIONAL_ID_GENERATOR_CALL));
            return;
          }
        }
      }

      Node arg = n.getSecondChild();
      if (arg == null) {
        compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
      } else if (arg.isString()) {
        String rename = getObfuscatedName(
            arg, callName, nameGenerator, arg.getString());
        parent.replaceChild(n, IR.string(rename));
        t.reportCodeChange();
      } else if (arg.isObjectLit()) {
        for (Node key : arg.children()) {
          if (key.isMemberFunctionDef()) {
            compiler.report(JSError.make(n, SHORTHAND_FUNCTION_NOT_SUPPORTED_IN_ID_GEN));
            return;
          }
          if (key.isComputedProp()) {
            compiler.report(JSError.make(n, COMPUTED_PROP_NOT_SUPPORTED_IN_ID_GEN));
            return;
          }

          String rename = getObfuscatedName(
              key, callName, nameGenerator, key.getString());
          key.setString(rename);
          // Prevent standard renaming by marking the key as quoted.
          key.putBooleanProp(Node.QUOTED_PROP, true);
        }
        arg.detach();
        parent.replaceChild(n, arg);
        t.reportCodeChange();
      } else {
        compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
      }
    }

    private String getObfuscatedName(
        Node id, String callName, NameSupplier nameGenerator, String name) {
      String rename = null;
      Map<String, String> idGeneratorMap = idGeneratorMaps.get(callName);
      String instanceId = getIdForGeneratorNode(
          nameGenerator.getRenameStrategy() != RenameStrategy.INCONSISTENT, id);
      if (nameGenerator.getRenameStrategy() == RenameStrategy.CONSISTENT) {
        Map<String, String> entry = consistNameMap.get(callName);
        rename = entry.get(instanceId);
        if (rename == null) {
          rename = nameGenerator.getName(instanceId, name);
          entry.put(instanceId, rename);
        }
      } else {
        rename = nameGenerator.getName(instanceId, name);
      }
      idGeneratorMap.put(rename, instanceId);
      return rename;
    }
  }


  /**
   * @return The serialize map of generators and their ids and their
   *     replacements.
   */
  public String getSerializedIdMappings() {
    return IdMappingUtil.generateSerializedIdMappings(idGeneratorMaps);
  }

  static String getIdForGeneratorNode(boolean consistent, Node n) {
    checkState(n.isString() || n.isStringKey(), n);
    if (consistent) {
      return n.getString();
    } else {
      return n.getSourceFileName() + ':' + n.getLineno() + ":" + n.getCharno();
    }
  }
}
