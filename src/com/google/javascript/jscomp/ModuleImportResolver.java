/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.ModuleNames;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Export;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Resolves module requires into {@link TypedVar}s.
 *
 * <p>Currently this only supports goog.modules, but can be extended for ES modules.
 */
final class ModuleImportResolver {

  private final ModuleMap moduleMap;
  private final Function<Node, TypedScope> nodeToScopeMapper;
  private final JSTypeRegistry registry;

  private static final String GOOG = "goog";
  private static final ImmutableSet<String> googDependencyCalls =
      ImmutableSet.of("require", "requireType", "forwardDeclare");

  ModuleImportResolver(
      ModuleMap moduleMap, Function<Node, TypedScope> nodeToScopeMapper, JSTypeRegistry registry) {
    this.moduleMap = moduleMap;
    this.nodeToScopeMapper = nodeToScopeMapper;
    this.registry = registry;
  }

  /** Returns whether this is a CALL node for goog.require(Type) or goog.forwardDeclare */
  static boolean isGoogModuleDependencyCall(Node value) {
    if (value == null || !value.isCall()) {
      return false;
    }
    Node callee = value.getFirstChild();
    if (!callee.isGetProp()) {
      return false;
    }
    Node owner = callee.getFirstChild();
    Node property = callee.getSecondChild();
    return owner.isName()
        && owner.getString().equals(GOOG)
        && googDependencyCalls.contains(property.getString());
  }

  /**
   * Attempts to look up the type of a Closure namespace from a require call
   *
   * <p>This returns null if the given {@link ModuleMap} is null, if the required module does not
   * exist, or if support is missing for the type of required {@link Module}. Currently only
   * requires of other goog.modules are supported.
   *
   * @param googRequire a CALL node representing some kind of Closure require.
   */
  ScopedName getClosureNamespaceTypeFromCall(Node googRequire) {
    if (moduleMap == null) {
      // TODO(b/124919359): make sure all tests have generated a ModuleMap
      return null;
    }
    String moduleId = googRequire.getSecondChild().getString();
    Module module = moduleMap.getClosureModule(moduleId);
    if (module == null) {
      return null;
    }
    switch (module.metadata().moduleType()) {
      case GOOG_PROVIDE:
        // Expect this to be a global variable
        Node provide = module.metadata().rootNode();
        if (provide != null && provide.isScript()) {
          return ScopedName.of(moduleId, provide.getGrandparent());
        } else {
          // Unknown module requires default to 'goog provides', but we don't want to type them.
          return null;
        }

      case GOOG_MODULE:
      case LEGACY_GOOG_MODULE:
        // TODO(b/124919359): Fix getGoogModuleScopeRoot to never return null.
        Node scopeRoot = getGoogModuleScopeRoot(module);
        return scopeRoot != null ? ScopedName.of("exports", scopeRoot) : null;
      case ES6_MODULE:
        throw new IllegalStateException("Type checking ES modules not yet supported");
      case COMMON_JS:
        throw new IllegalStateException("Type checking CommonJs modules not yet supported");
      case SCRIPT:
        throw new IllegalStateException("Cannot import a name from a SCRIPT");
    }
    throw new AssertionError();
  }

  /** Returns the corresponding scope root Node from a goog.module. */
  @Nullable
  private Node getGoogModuleScopeRoot(@Nullable Module module) {
    checkArgument(module.metadata().isGoogModule(), module.metadata());
    Node scriptNode = module.metadata().rootNode();

    if (scriptNode.isScript()
        && scriptNode.hasOneChild()
        && scriptNode.getOnlyChild().isModuleBody()) {
      // The module root node should be a SCRIPT, whose first child is a MODULE_BODY.
      // The map is keyed off a MODULE_BODY node for a goog.module,
      // which is the only child of our SCRIPT node.
      return scriptNode.getOnlyChild();
    } else if (scriptNode.isCall()) {
      // This is a goog.loadModule call, and the scope is keyed off the FUNCTION node's BLOCK in:
      //   goog.loadModule(function(exports)
      Node functionLiteral = scriptNode.getSecondChild();
      return NodeUtil.getFunctionBody(functionLiteral);
    }
    // TODO(b/124919359): this case should not happen, but is triggering on goog.require calls in
    // rewritten modules with preserveClosurePrimitives enabled.
    return null;
  }

  /** Declares/updates the type of all bindings imported into the ES module scope */
  void declareEsModuleImports(Module module, TypedScope scope, CompilerInput moduleInput) {
    checkArgument(module.metadata().isEs6Module(), module);
    checkArgument(scope.isModuleScope(), scope);
    for (Map.Entry<String, Binding> boundName : module.boundNames().entrySet()) {
      Binding binding = boundName.getValue();
      String localName = boundName.getKey();
      if (!binding.isCreatedByEsImport()) {
        continue;
      }
      // ES imports fall into two categories:
      //  - namespace imports. These correspond to an object type containing all named exports.
      //  - named imports. These always correspond, eventually, to a name local to a module.
      //    Note that we include default imports in this case.
      if (binding.isModuleNamespace()) {
        // TODO(b/128633181): Support import *.
        scope.declare(
            localName,
            binding.sourceNode(),
            registry.getNativeType(JSTypeNative.UNKNOWN_TYPE),
            moduleInput);
      } else {
        Export originatingExport = binding.originatingExport();
        Node exportModuleRoot = originatingExport.moduleMetadata().rootNode().getFirstChild();
        TypedScope modScope = nodeToScopeMapper.apply(exportModuleRoot);
        // NB: If the original export was an `export default` then the local name is *default*.
        // We've already declared a dummy variable named `*default*` in the scope.
        TypedVar originalVar = modScope.getSlot(originatingExport.localName());
        JSType importType = originalVar.getType();
        scope.declare(
            localName,
            binding.sourceNode(),
            importType,
            moduleInput,
            /* inferred= */ originalVar.isTypeInferred());
        if (originalVar.getNameNode().getTypedefTypeProp() != null
            && binding.sourceNode().getTypedefTypeProp() == null) {
          binding.sourceNode().setTypedefTypeProp(originalVar.getNameNode().getTypedefTypeProp());
          registry.declareType(scope, localName, originalVar.getNameNode().getTypedefTypeProp());
        }
      }
    }
  }

  /** Returns the {@link Module} corresponding to this scope root, or null if not a module root. */
  @Nullable
  Module getModuleFromScopeRoot(Node moduleBody) {
    if (moduleBody.isModuleBody()) {
      Node scriptNode = moduleBody.getParent();
      if (scriptNode.getBooleanProp(Node.GOOG_MODULE)) {
        Node googModuleCall = moduleBody.getFirstChild();
        String namespace = googModuleCall.getFirstChild().getSecondChild().getString();
        return moduleMap.getClosureModule(namespace);
      } else {
        String modulePath = ModuleNames.fileToModuleName(scriptNode.getSourceFileName());
        Module module = moduleMap.getModule(modulePath);
        // TODO(b/131418081): Also cover CommonJS modules.
        checkState(
            module.metadata().isEs6Module(),
            "Typechecking of non-goog- and non-es-modules not supported");
        return module;
      }
    } else if (isGoogLoadModuleBlock(moduleBody)) {
      Node googModuleCall = moduleBody.getFirstChild();
      String namespace = googModuleCall.getFirstChild().getSecondChild().getString();
      return moduleMap.getClosureModule(namespace);
    }
    return null;
  }

  private static boolean isGoogLoadModuleBlock(Node scopeRoot) {
    return scopeRoot.isBlock()
        && scopeRoot.getParent().isFunction()
        && NodeUtil.isBundledGoogModuleCall(scopeRoot.getGrandparent());
  }
}
