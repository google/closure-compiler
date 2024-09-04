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
import com.google.debugging.sourcemap.Base64;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

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

  static final DiagnosticType INVALID_TEMPLATE_LITERAL_PARAMETER =
      DiagnosticType.warning(
          "JSC_INVALID_GENERATOR_PARAMETER",
          "An id generator must be called with a template literals with no invalid escape"
              + " sequences.");

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
  private final boolean templateLiteralsAreTranspiled;
  private final Map<String, NameSupplier> nameGenerators;
  private final Map<String, Map<String, String>> consistNameMap;

  private final Map<String, Map<String, String>> idGeneratorMaps;
  private final Map<String, BiMap<String, String>> previousMap;

  private final boolean generatePseudoNames;
  private final Xid.HashFunction xidHashFunction;

  public ReplaceIdGenerators(
      AbstractCompiler compiler,
      boolean templateLiteralsAreTranspiled,
      Map<String, RenamingMap> idGens,
      boolean generatePseudoNames,
      String previousMapSerialized,
      Xid.HashFunction xidHashFunction) {
    this.compiler = compiler;
    this.templateLiteralsAreTranspiled = templateLiteralsAreTranspiled;
    this.generatePseudoNames = generatePseudoNames;
    this.xidHashFunction = xidHashFunction;
    nameGenerators = new LinkedHashMap<>();
    idGeneratorMaps = new LinkedHashMap<>();
    consistNameMap = new LinkedHashMap<>();

    Map<String, BiMap<String, String>> previousMap =
        IdMappingUtil.parseSerializedIdMappings(previousMapSerialized);
    this.previousMap = previousMap;

    if (idGens != null) {
      for (Entry<String, RenamingMap> gen : idGens.entrySet()) {
        String name = gen.getKey();
        RenamingMap map = gen.getValue();
        if (map instanceof RenamingToken) {
          switch ((RenamingToken) map) {
            case DISABLE:
              nameGenerators.put(name, null);
              continue; // don't put an entry in idGeneratorsMap
            case INCONSISTENT:
              nameGenerators.put(
                  name, createNameSupplier(RenameStrategy.INCONSISTENT, previousMap.get(name)));
              break;
            case STABLE:
              nameGenerators.put(
                  name, createNameSupplier(RenameStrategy.STABLE, previousMap.get(name)));
              break;
          }
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

  private static final QualifiedName CREATE_TEMPLATE_TAG_FIRST_ARG =
      QualifiedName.of("$jscomp.createTemplateTagFirstArg");

  private class GatherGenerators extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal unused, Node n, Node parent) {
      JSDocInfo doc = n.getJSDocInfo();
      if (doc == null || !doc.isAnyIdGenerator()) {
        return;
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
      if (nameGenerators.containsKey(name)) {
        // This generator is already registered from our constructor.
        // Don't override it.
        return;
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
      if (!n.isCall() && !n.isTaggedTemplateLit()) {
        return;
      }

      Node qname = NodeUtil.getCallTargetResolvingIndirectCalls(n);
      String callName = qname.getQualifiedName();
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

      if (n.isCall()) {
        maybeReplaceCall(t, n, callName, nameGenerator);
      } else {
        maybeReplaceTaggedTemplateLit(t, n, callName, nameGenerator);
      }
    }

    private void maybeReplaceTaggedTemplateLit(
        NodeTraversal t, Node n, String callName, NameSupplier nameGenerator) {
      Node arg = n.getLastChild();

      if (arg == null || !arg.isTemplateLit()) {
        throw new IllegalStateException();
      } else if (arg.hasOneChild()) {
        var cooked = arg.getFirstChild().getCookedString();
        if (cooked == null) {
          // We don't allow strings with odd escape sequences... We could but it doesn't seem
          // necessary
          compiler.report(JSError.make(n, INVALID_TEMPLATE_LITERAL_PARAMETER));
          return;
        }

        String rename = getObfuscatedName(arg, callName, nameGenerator, cooked);
        n.replaceWith(IR.string(rename));
        t.reportCodeChange();
      } else {
        // There is an alternating sequence of template literals and expressions, in this case we
        // need to preserve the function call but obfuscate the literals.
        Node newTemplateLit = IR.templateLiteral();
        for (Node child = arg.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isTemplateLitString()) {
            var cooked = child.getCookedString();
            if (cooked == null) {
              compiler.report(JSError.make(n, INVALID_TEMPLATE_LITERAL_PARAMETER));
              return;
            }
            String rename = getObfuscatedName(child, callName, nameGenerator, cooked);
            newTemplateLit.addChildToBack(
                IR.templateLiteralString(rename, rename).srcrefIfMissing(child));
          } else {
            newTemplateLit.addChildToBack(
                IR.templateLiteralSubstitution(child.getFirstChild().detach())
                    .srcrefIfMissing(child));
          }
        }
        arg.replaceWith(newTemplateLit);
        t.reportCodeChange(newTemplateLit);
      }
    }

    private void maybeReplaceCall(
        NodeTraversal t, Node n, String callName, NameSupplier nameGenerator) {
      Node arg = n.getSecondChild();
      if (arg == null) {
        compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
      } else if (arg.isStringLit()) {
        String rename = getObfuscatedName(arg, callName, nameGenerator, arg.getString());
        n.replaceWith(IR.string(rename));
        t.reportCodeChange();
      } else if (arg.isTemplateLit() && arg.hasOneChild()) {
        var cooked = arg.getFirstChild().getCookedString();
        if (cooked == null) {
          compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
        } else {
          String rename = getObfuscatedName(arg, callName, nameGenerator, cooked);
          n.replaceWith(IR.string(rename));
          t.reportCodeChange();
        }
      } else if (arg.isObjectLit()) {
        for (Node key = arg.getFirstChild(); key != null; key = key.getNext()) {
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
        n.replaceWith(arg);
        t.reportCodeChange();
      } else if (templateLiteralsAreTranspiled && arg.isName()) {
        // This might be a transpiled template literal.  Find the definition.
        // The pass always injects it at the current script root, typically at the top, but not
        // always.
        Node ttlVar = findTtlVar(t, arg);
        if (ttlVar == null) {
          // This could be some other call to the ttl function not using ttl syntax.  These are
          // weird but not illegal.
          compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
          return;
        }
        var ttlFunctionCall = ttlVar.getFirstFirstChild();
        var paramsArrayLiteral = ttlFunctionCall.getSecondChild();
        if (paramsArrayLiteral == null || !paramsArrayLiteral.isArrayLit()) {
          throw new IllegalStateException("bad transpiled structure");
        }
        // 2 cases, if there is exactly 1 element we can just replace everything
        // otherwise we need to rewrite the array in place.
        if (paramsArrayLiteral.getChildCount() == 1) {
          String originalName = paramsArrayLiteral.getFirstChild().getString();
          String rename = getObfuscatedName(arg, callName, nameGenerator, originalName);
          n.replaceWith(IR.string(rename));
          t.reportCodeChange();
          t.reportCodeChange(ttlVar);
          ttlVar.detach();
        } else {
          // We have parameters, so we need to rewrite the TTL array in place and leave the function
          // call.
          for (Node param = paramsArrayLiteral.getFirstChild();
              param != null;
              param = param.getNext()) {
            String originalName = param.getString();
            String rename = getObfuscatedName(arg, callName, nameGenerator, originalName);
            param.setString(rename);
          }
          t.reportCodeChange(paramsArrayLiteral);
        }
      } else {
        compiler.report(JSError.make(n, INVALID_GENERATOR_PARAMETER));
      }
    }

    private @Nullable Node findTtlVar(NodeTraversal t, Node arg) {
      var name = arg.getString();
      var ttlVarName = t.getScope().getVar(name).getNode();
      var parent = ttlVarName.getParent();
      // We are only interested in top level var definitions since that is what the transpiler
      // creates
      if (!parent.isVar() || !parent.getParent().isScript()) {
        return null;
      }
      // Because the AST is normalized there is only one child
      checkState(
          ttlVarName.getParent().hasOneChild(),
          "The AST is normalized so there should only be one name per var");
      var callNode = ttlVarName.getFirstChild();
      if (!callNode.isCall()) {
        return null;
      }
      var callExpr = callNode.getFirstChild();
      if (callExpr.matchesName("$jscomp$createTemplateTagFirstArg")
          // The dot case is about our unit tests mostly.
          || CREATE_TEMPLATE_TAG_FIRST_ARG.matches(callExpr)) {
        return ttlVarName.getParent();
      }

      return null;
    }

    private String getObfuscatedName(
        Node id, String callName, NameSupplier nameGenerator, String name) {
      String rename = null;
      Map<String, String> idGeneratorMap = idGeneratorMaps.get(callName);
      String instanceId =
          getIdForGeneratorNode(
              nameGenerator.getRenameStrategy() != RenameStrategy.INCONSISTENT, id, name);
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

  private static String getIdForGeneratorNode(boolean consistent, Node n, String name) {
    if (consistent) {
      return name;
    } else {
      return n.getSourceFileName() + ':' + n.getLineno() + ":" + n.getCharno();
    }
  }
}
