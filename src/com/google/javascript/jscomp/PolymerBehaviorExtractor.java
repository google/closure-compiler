/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds the Polymer behavior definitions associated with Polymer element definitions.
 *
 * @see https://www.polymer-project.org/1.0/docs/devguide/behaviors
 */
final class PolymerBehaviorExtractor {

  private static final ImmutableSet<String> BEHAVIOR_NAMES_NOT_TO_COPY =
      ImmutableSet.of(
          "created",
          "attached",
          "detached",
          "attributeChanged",
          "configure",
          "ready",
          "properties",
          "listeners",
          "observers",
          "hostAttributes");

  private static final String GOOG_MODULE_EXPORTS = "exports";

  private final AbstractCompiler compiler;
  private final GlobalNamespace globalNames;
  private final ModuleMetadataMap moduleMetadataMap;
  private final ModuleMap moduleMap;

  private final Table<String, ModuleMetadata, ResolveBehaviorNameResult> resolveMemoized =
      HashBasedTable.create();
  private final Map<String, ResolveBehaviorNameResult> globalResolveMemoized =
      new LinkedHashMap<>();

  PolymerBehaviorExtractor(
      AbstractCompiler compiler,
      GlobalNamespace globalNames,
      ModuleMetadataMap moduleMetadataMap,
      ModuleMap moduleMap) {
    this.compiler = compiler;
    this.globalNames = globalNames;
    this.moduleMetadataMap = moduleMetadataMap;
    this.moduleMap = moduleMap;
  }

  /**
   * Extracts all Behaviors from an array literal, recursively. Entries in the array can be object
   * literals or array literals (of other behaviors). Behavior names must be global, fully qualified
   * names. TODO(rishipal): Make this function better handle case where the same behavior
   * transitively gets included in the same Polymer element more than once.
   *
   * @see https://github.com/Polymer/polymer/blob/0.8-preview/PRIMER.md#behaviors
   * @param moduleMetadata The module in which these behaviors are being resolved, or null if not in
   *     a module.
   * @return A list of all {@code BehaviorDefinitions} in the array.
   */
  ImmutableList<BehaviorDefinition> extractBehaviors(
      Node behaviorArray, @Nullable ModuleMetadata moduleMetadata) {
    if (behaviorArray == null) {
      return ImmutableList.of();
    }

    if (!behaviorArray.isArrayLit()) {
      compiler.report(
          JSError.make(behaviorArray, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR_ARRAY));
      return ImmutableList.of();
    }

    ImmutableList.Builder<BehaviorDefinition> behaviors = ImmutableList.builder();
    for (Node behaviorName = behaviorArray.getFirstChild();
        behaviorName != null;
        behaviorName = behaviorName.getNext()) {
      if (behaviorName.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(behaviorName, compiler);
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorName, compiler);
        PolymerPassStaticUtils.protectObserverAndPropertyFunctionKeys(behaviorName);
        if (NodeUtil.getFirstPropMatchingKey(behaviorName, "is") != null) {
          compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR));
        }
        Node behaviorModule = NodeUtil.getEnclosingModuleIfPresent(behaviorName);
        behaviors.add(
            new BehaviorDefinition(
                PolymerPassStaticUtils.extractProperties(
                    behaviorName,
                    PolymerClassDefinition.DefinitionType.ObjectLiteral,
                    compiler,
                    /* constructor= */ null),
                getBehaviorFunctionsToCopy(behaviorName),
                getNonPropertyMembersToCopy(behaviorName),
                /* isGlobalDeclaration= */ NodeUtil.getEnclosingScopeRoot(behaviorName).isRoot(),
                (FeatureSet) NodeUtil.getEnclosingScript(behaviorName).getProp(Node.FEATURE_SET),
                behaviorModule));
        continue;
      }

      ResolveBehaviorNameResult resolveResult;
      if (isGoogModuleGetCall(behaviorName)) {
        resolveResult = resolveGoogModuleGet(behaviorName.getSecondChild().getString());
      } else {
        resolveResult =
            resolveBehaviorName(getQualifiedNameThroughCast(behaviorName), moduleMetadata);
      }
      if (resolveResult.equals(FAILED_RESOLVE_RESULT)) {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
        continue;
      }
      Node behaviorValue = resolveResult.node;

      if (behaviorValue.isArrayLit()) {
        // Individual behaviors can also be arrays of behaviors. Parse them recursively.
        behaviors.addAll(extractBehaviors(behaviorValue, resolveResult.moduleMetadata));
      } else if (behaviorValue.isObjectLit()) {
        PolymerPassStaticUtils.switchDollarSignPropsToBrackets(behaviorValue, compiler);
        PolymerPassStaticUtils.quoteListenerAndHostAttributeKeys(behaviorValue, compiler);
        if (NodeUtil.getFirstPropMatchingKey(behaviorValue, "is") != null) {
          compiler.report(JSError.make(behaviorValue, PolymerPassErrors.POLYMER_INVALID_BEHAVIOR));
        }
        Node behaviorModule = NodeUtil.getEnclosingModuleIfPresent(behaviorValue);
        behaviors.add(
            new BehaviorDefinition(
                PolymerPassStaticUtils.extractProperties(
                    behaviorValue,
                    PolymerClassDefinition.DefinitionType.ObjectLiteral,
                    compiler,
                    /* constructor= */ null),
                getBehaviorFunctionsToCopy(behaviorValue),
                getNonPropertyMembersToCopy(behaviorValue),
                resolveResult.isGlobalDeclaration,
                (FeatureSet) NodeUtil.getEnclosingScript(behaviorValue).getProp(Node.FEATURE_SET),
                behaviorModule));
      } else {
        compiler.report(JSError.make(behaviorName, PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR));
      }
    }
    return behaviors.build();
  }

  private static class ResolveBehaviorNameResult {
    final Node node;
    final boolean isGlobalDeclaration;
    final ModuleMetadata moduleMetadata;

    ResolveBehaviorNameResult(
        @Nullable Node node, boolean isGlobalDeclaration, @Nullable ModuleMetadata moduleMetadata) {
      this.node = node;
      this.isGlobalDeclaration = isGlobalDeclaration;
      this.moduleMetadata = moduleMetadata;
    }
  }

  private static final ResolveBehaviorNameResult FAILED_RESOLVE_RESULT =
      new ResolveBehaviorNameResult(null, false, null);

  /**
   * Resolve an identifier, which is presumed to refer to a Polymer Behavior declaration, using the
   * global namespace. Recurses to resolve assignment chains of any length.
   *
   * <p>This method memoizes {@link #resolveBehaviorNameInternal(String, ModuleMetadata)}
   *
   * @param name the name of the identifier, which may be qualified.
   * @param moduleMetadata the module (ES module or goog.module) this name is resolved in, or null
   *     if not in a module.
   * @return The behavior declaration node, or {@link #FAILED_RESOLVE_RESULT} if it couldn't be
   *     resolved.
   */
  private ResolveBehaviorNameResult resolveBehaviorName(
      @Nullable String name, @Nullable ModuleMetadata moduleMetadata) {
    if (name == null) {
      return FAILED_RESOLVE_RESULT;
    }
    ResolveBehaviorNameResult memoized =
        moduleMetadata != null
            ? resolveMemoized.get(name, moduleMetadata)
            : globalResolveMemoized.get(name);
    if (memoized == null) {
      memoized = checkNotNull(resolveBehaviorNameInternal(name, moduleMetadata));
      if (moduleMetadata != null) {
        resolveMemoized.put(name, moduleMetadata, memoized);
      } else {
        globalResolveMemoized.put(name, memoized);
      }
    }
    return memoized;
  }

  /**
   * Implements behavior resolution. Call {@link #resolveBehaviorName(String, ModuleMetadata)}}
   * instead.
   */
  private ResolveBehaviorNameResult resolveBehaviorNameInternal(
      String name, ModuleMetadata moduleMetadata) {
    // Check if this name is a module import/require.
    ResolveBehaviorNameResult result = getNameIfModuleImport(name, moduleMetadata);
    if (result != null) {
      return result;
    }

    // Check if this name is possibly from a legacy goog.module
    ResolveBehaviorNameResult legacyResolve = resolveReferenceToLegacyGoogModule(name);
    if (legacyResolve != null) {
      return legacyResolve;
    }

    // If not, look it up within the current module.
    Name moduleLevelName =
        moduleMetadata != null ? globalNames.getNameFromModule(moduleMetadata, name) : null;
    Name globalName = moduleLevelName == null ? globalNames.getSlot(name) : moduleLevelName;
    if (globalName == null) {
      return FAILED_RESOLVE_RESULT;
    }

    // Whether the declaration of this node is in the top-level global scope, as opposed to a module
    // or an IIFE.
    boolean isGlobalDeclaration = moduleLevelName == null;

    // Use any set as a backup declaration, even if it's local.
    Ref declarationRef = globalName.getDeclaration();
    if (declarationRef == null) {
      for (Ref ref : globalName.getRefs()) {
        if (ref.isSet()) {
          isGlobalDeclaration = false;
          declarationRef = ref;
          break;
        }
      }
    }
    if (declarationRef == null) {
      return FAILED_RESOLVE_RESULT;
    }

    Node declarationNode = declarationRef.getNode();
    if (declarationNode == null) {
      return FAILED_RESOLVE_RESULT;
    }
    Node rValue = NodeUtil.getRValueOfLValue(declarationNode);
    if (rValue == null) {
      return FAILED_RESOLVE_RESULT;
    }

    if (rValue.isQualifiedName()) {
      // Another identifier; recurse.
      Scope declarationScope = declarationRef.scope.getClosestHoistScope();
      Module m =
          ModuleImportResolver.getModuleFromScopeRoot(
              compiler.getModuleMap(), compiler, declarationScope.getRootNode());
      return resolveBehaviorName(
          getQualifiedNameThroughCast(rValue), m != null ? m.metadata() : null);
    }

    JSDocInfo behaviorInfo = NodeUtil.getBestJSDocInfo(declarationNode);
    if (behaviorInfo == null || !behaviorInfo.isPolymerBehavior()) {
      compiler.report(
          JSError.make(declarationNode, PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR));
    }

    return new ResolveBehaviorNameResult(rValue, isGlobalDeclaration, moduleMetadata);
  }

  /**
   * Handles resolving behaviors if they are references to legacy modules
   *
   * <p>Returns null if the name is not from a legacy module, and resolution should continue
   * normally.
   */
  private @Nullable ResolveBehaviorNameResult resolveReferenceToLegacyGoogModule(String name) {
    int dot = name.length();
    while (dot >= 0) {
      String subNamespace = name.substring(0, dot);
      ModuleMetadata metadata = moduleMetadataMap.getModulesByGoogNamespace().get(subNamespace);

      if (metadata == null || !metadata.isLegacyGoogModule()) {
        dot = name.lastIndexOf('.', dot - 1);
        continue;
      }

      String rest = dot == name.length() ? "" : name.substring(dot);
      ResolveBehaviorNameResult result = resolveBehaviorName(GOOG_MODULE_EXPORTS + rest, metadata);
      // TODO(lharker): Remove this check and just fail to resolve once we have moved module
      // rewriting unconditionally after the PolymerPass.
      return result.equals(FAILED_RESOLVE_RESULT) ? null : result;
    }
    return null;
  }

  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");

  private static boolean isGoogModuleGetCall(Node callNode) {
    if (!callNode.isCall()) {
      return false;
    }
    return GOOG_MODULE_GET.matches(callNode.getFirstChild())
        && callNode.hasTwoChildren()
        && callNode.getSecondChild().isStringLit();
  }

  private ResolveBehaviorNameResult resolveGoogModuleGet(String moduleNamespace) {
    ModuleMetadata closureModule =
        moduleMetadataMap.getModulesByGoogNamespace().get(moduleNamespace);
    if (closureModule == null) {
      // Invalid goog.module.get() call.
      return FAILED_RESOLVE_RESULT;
    } else if (closureModule.isGoogProvide()) {
      return resolveBehaviorName(moduleNamespace, null);
    }
    checkState(closureModule.isGoogModule(), closureModule);
    return resolveBehaviorName(GOOG_MODULE_EXPORTS, closureModule);
  }

  /**
   * Handles resolving behaviors whose root is imported from another module or a provide.
   *
   * <p>Returns null if the given name is not imported or {@link #FAILED_RESOLVE_RESULT} if it is
   * imported but is not annotated @polymerBehavior.
   */
  private @Nullable ResolveBehaviorNameResult getNameIfModuleImport(
      String name, ModuleMetadata metadata) {
    if (metadata == null || (!metadata.isEs6Module() && !metadata.isGoogModule())) {
      return null;
    }
    Module module =
        metadata.isGoogModule()
            ? moduleMap.getClosureModule(metadata.googNamespaces().asList().get(0))
            : moduleMap.getModule(metadata.path());

    checkNotNull(module, metadata);

    int dot = name.indexOf('.');
    String root = dot == -1 ? name : name.substring(0, dot);
    Binding b = module.boundNames().get(root);

    if (b == null || !b.isSomeImport()) {
      return null;
    }
    String rest = dot == -1 ? "" : name.substring(dot);

    if (b.isModuleNamespace()) {
      // `import * as x from '';` or `const ns = goog.require('...`
      return resolveModuleNamespaceBinding(b, rest);
    }
    ModuleMetadata importMetadata = b.originatingExport().moduleMetadata();
    String originatingName; // The name in the module being imported
    if (importMetadata.isEs6Module()) {
      // import {exportName} from './mod';
      originatingName = b.originatingExport().localName() + rest;
    } else if (importMetadata.isGoogModule()) {
      // `const {exportName: localName} = goog.require('some.module');`
      originatingName = GOOG_MODULE_EXPORTS + "." + b.originatingExport().exportName() + rest;
    } else {
      // `const {exportName: localName} = goog.require('some.provide');`
      checkState(importMetadata.isGoogProvide(), importMetadata);
      originatingName = b.closureNamespace() + "." + b.originatingExport().exportName() + rest;
    }
    return resolveBehaviorName(originatingName, importMetadata);
  }

  /** Resolves a name that imports the 'namespace' of a module or provide. */
  private ResolveBehaviorNameResult resolveModuleNamespaceBinding(Binding b, String rest) {
    if (b.metadata().isGoogModule()) {
      return resolveBehaviorName(GOOG_MODULE_EXPORTS + rest, b.metadata());
    } else if (b.metadata().isGoogProvide()) {
      return resolveBehaviorName(b.closureNamespace() + rest, b.metadata());
    }

    // ES module import *.
    checkState(b.metadata().isEs6Module());
    if (rest.isEmpty()) {
      // The namespace imported by `import *` is never a @polymerBehavior.
      return FAILED_RESOLVE_RESULT;
    }
    rest = rest.substring(1); // Remove leading '.'.
    int dot = rest.indexOf('.');

    // Given:
    //   `const internalName = 0; export {internalName as exportName};`
    //   `import * as mod from './x'; use(mod.exportName.Behavior);`
    // 1. get the internal name `internalName` from` exportName`.
    // 2. then proceed to resolve `internalName.Behavior` in './x'.
    String exportedName = dot == -1 ? rest : rest.substring(0, dot);
    Module originalModule = moduleMap.getModule(b.metadata().path());
    Binding exportBinding = originalModule.namespace().get(exportedName);
    if (exportBinding == null || !exportBinding.isCreatedByEsExport()) {
      // This is an invalid import, and will cause an error elsewhere.
      return FAILED_RESOLVE_RESULT;
    }
    return resolveBehaviorName(
        exportBinding.originatingExport().localName() + (dot == -1 ? "" : rest.substring(dot)),
        b.metadata());
  }

  /**
   * @return A list of functions from a behavior which should be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getBehaviorFunctionsToCopy(Node behaviorObjLit) {
    checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> functionsToCopy = ImmutableList.builder();
    Node enclosingModule = NodeUtil.getEnclosingModuleIfPresent(behaviorObjLit);

    for (Node keyNode = behaviorObjLit.getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      boolean isFunctionDefinition =
          (keyNode.isStringKey() && keyNode.getFirstChild().isFunction())
              || keyNode.isMemberFunctionDef();
      if (isFunctionDefinition && !BEHAVIOR_NAMES_NOT_TO_COPY.contains(keyNode.getString())) {
        functionsToCopy.add(
            new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode),
                keyNode,
                keyNode.getFirstChild(),
                enclosingModule));
      }
    }

    return functionsToCopy.build();
  }

  /**
   * Similar to {@link Node#getQualifiedName} but also handles CAST nodes. For example, given a
   * GETPROP representing "(/** @type {?} *\/ (x)).y.z" returns "x.y.z". Returns null if node is not
   * a NAME, GETPROP, or CAST. See b/64389806 for Polymer-specific context.
   */
  private static @Nullable String getQualifiedNameThroughCast(Node node) {
    if (node.isName()) {
      String name = node.getString();
      return name.isEmpty() ? null : name;
    } else if (node.isGetProp()) {
      String left = getQualifiedNameThroughCast(node.getFirstChild());
      if (left == null) {
        return null;
      }
      String right = node.getString();
      return left + "." + right;
    } else if (node.isCast()) {
      return getQualifiedNameThroughCast(node.getFirstChild());
    }
    return null;
  }

  /**
   * @return A list of MemberDefinitions in a behavior which are not in the properties block, but
   *     should still be copied to the element prototype.
   */
  private static ImmutableList<MemberDefinition> getNonPropertyMembersToCopy(Node behaviorObjLit) {
    checkState(behaviorObjLit.isObjectLit());
    ImmutableList.Builder<MemberDefinition> membersToCopy = ImmutableList.builder();
    Node enclosingModule = NodeUtil.getEnclosingModuleIfPresent(behaviorObjLit);

    for (Node keyNode = behaviorObjLit.getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      boolean isNonFunctionMember =
          keyNode.isGetterDef() || (keyNode.isStringKey() && !keyNode.getFirstChild().isFunction());
      if (isNonFunctionMember && !BEHAVIOR_NAMES_NOT_TO_COPY.contains(keyNode.getString())) {
        membersToCopy.add(
            new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode),
                keyNode,
                keyNode.getFirstChild(),
                enclosingModule));
      }
    }

    return membersToCopy.build();
  }

  /**
   * Parsed definition of a Polymer Behavior. Includes members which should be copied to elements
   * which use the behavior.
   */
  static final class BehaviorDefinition {
    /** Properties declared in the behavior 'properties' block. */
    final List<MemberDefinition> props;

    /** Functions intended to be copied to elements which use this Behavior. */
    final List<MemberDefinition> functionsToCopy;

    /** Other members intended to be copied to elements which use this Behavior. */
    final List<MemberDefinition> nonPropertyMembersToCopy;

    /** Whether this Behavior is declared in the global scope. */
    final boolean isGlobalDeclaration;

    /** Language features to carry over to the extraction destination. */
    final FeatureSet features;

    /** Containing MODULE_BODY if this behavior is defined inside a module, otherwise null */
    final Node behaviorModule;

    private @Nullable Set<String> lazyModuleLocalNames;

    BehaviorDefinition(
        List<MemberDefinition> props,
        List<MemberDefinition> functionsToCopy,
        List<MemberDefinition> nonPropertyMembersToCopy,
        boolean isGlobalDeclaration,
        FeatureSet features,
        Node behaviorModule) {
      this.props = props;
      this.functionsToCopy = functionsToCopy;
      this.nonPropertyMembersToCopy = nonPropertyMembersToCopy;
      this.isGlobalDeclaration = isGlobalDeclaration;
      this.features = features;
      this.behaviorModule = behaviorModule;
    }

    Set<String> getModuleLocalNames(AbstractCompiler compiler) {
      if (lazyModuleLocalNames == null) {
        lazyModuleLocalNames = this.accumulateModuleLocalVars(compiler);
      }
      return lazyModuleLocalNames;
    }

    private Set<String> accumulateModuleLocalVars(AbstractCompiler compiler) {
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
      Scope globalScope = Scope.createGlobalScope(behaviorModule.getParent());
      return NodeUtil.getAllVarNamesDeclaredInModule(
          behaviorModule, compiler, scopeCreator, globalScope);
    }
  }
}
