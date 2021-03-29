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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Export;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
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
  private static final ImmutableSet<String> GOOG_DEPENDENCY_CALLS =
      ImmutableSet.of("require", "requireType", "forwardDeclare");
  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");

  ModuleImportResolver(
      ModuleMap moduleMap, Function<Node, TypedScope> nodeToScopeMapper, JSTypeRegistry registry) {
    this.moduleMap = moduleMap;
    this.nodeToScopeMapper = nodeToScopeMapper;
    this.registry = registry;
  }

  /**
   * Returns whether this is a CALL node for goog.require(Type), goog.forwardDeclare, or
   * goog.module.get.
   *
   * <p>This method does not verify that the call is actually in a valid location. For example, this
   * method does not verify that goog.require calls are at the top-level. That is left to the
   * caller.
   */
  static boolean isGoogModuleDependencyCall(Node value) {
    if (value == null
        || !value.isCall()
        || !value.hasTwoChildren()
        || !value.getSecondChild().isStringLit()) {
      return false;
    }
    Node callee = value.getFirstChild();
    if (!callee.isGetProp()) {
      return false;
    }
    Node owner = callee.getFirstChild();
    return (owner.isName()
            && owner.getString().equals(GOOG)
            && GOOG_DEPENDENCY_CALLS.contains(callee.getString()))
        || GOOG_MODULE_GET.matches(callee);
  }

  /**
   * Attempts to look up the type of a Closure namespace from a require call
   *
   * <p>This returns null if the given {@link ModuleMap} is null, if the required module does not
   * exist, or if support is missing for the type of required {@link Module}. Currently only
   * requires of goog.modules, goog.provides, and ES module with goog.declareModuleId are supported.
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
        Node moduleBody = module.metadata().rootNode().getFirstChild(); // SCRIPT -> MODULE_BODY
        return ScopedName.of(Export.NAMESPACE, moduleBody);
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

  /**
   * Declares/updates the type of all bindings imported into the ES module scope
   *
   * @return A map from local nodes to ScopedNames for which {@link #nodeToScopeMapper} couldn't
   *     find a scope, despite the original module existing. This is expected to happen for circular
   *     references if not all module scopes are created and the caller should handle declaring
   *     these names later, e.g. in TypedScopeCreator.
   */
  Map<Node, ScopedName> declareEsModuleImports(
      Module module, TypedScope scope, CompilerInput moduleInput) {
    checkArgument(module.metadata().isEs6Module(), module);
    checkArgument(scope.isModuleScope(), scope);
    ImmutableMap.Builder<Node, ScopedName> missingNames = ImmutableMap.builder();
    for (Map.Entry<String, Binding> boundName : module.boundNames().entrySet()) {
      Binding binding = boundName.getValue();
      String localName = boundName.getKey();
      if (!binding.isCreatedByEsImport()) {
        continue;
      }
      // ES imports fall into two categories:
      //  - namespace imports. These correspond to an object type containing all named exports.
      //  - named imports. These always correspond, eventually, to a name local to a module.
      //    Note that we include imports of an `export default` in this case and map them to a
      //    pseudo-variable named *default*.
      ScopedName export = getScopedNameFromEsBinding(binding);
      TypedScope modScope = nodeToScopeMapper.apply(export.getScopeRoot());
      if (modScope == null) {
        checkState(binding.sourceNode().getString().equals(localName), binding.sourceNode());
        missingNames.put(binding.sourceNode(), export);
        continue;
      }

      TypedVar originalVar = modScope.getVar(export.getName());
      JSType importType = originalVar.getType();
      scope.declare(
          localName,
          binding.sourceNode(),
          importType,
          moduleInput,
          /* inferred= */ originalVar.isTypeInferred());

      // Non-namespace imports may be typedefs; if so, propagate the typedef prop onto the
      // export and import bindings, if not already there.
      if (!binding.isModuleNamespace() && binding.sourceNode().getTypedefTypeProp() == null) {
        JSType typedefType = originalVar.getNameNode().getTypedefTypeProp();
        if (typedefType != null) {
          binding.sourceNode().setTypedefTypeProp(typedefType);
          registry.declareType(scope, localName, typedefType);
        }
      }
    }
    return missingNames.build();
  }

  /**
   * Declares or updates the type of properties representing exported names from ES module
   *
   * <p>When the given object type does not have existing properties corresponding to exported
   * names, this method adds new properties to the object type. If the object type already has
   * properties, this method will ignore declared properties and update the type of inferred
   * properties.
   *
   * <p>The additional properties will be inferred (instead of declared) if and only if {@link
   * TypedVar#isTypeInferred()} is true for the original exported name.
   *
   * <p>We create this type to support 'import *' and goog.requires of this module. Note: we could
   * lazily initialize this type if always creating it hurts performance.
   *
   * @param namespace An object type which may already have properties representing exported names.
   * @param scope The scope rooted at the given module.
   */
  void updateEsModuleNamespaceType(ObjectType namespace, Module module, TypedScope scope) {
    checkArgument(module.metadata().isEs6Module(), module);
    checkArgument(scope.isModuleScope(), scope);

    for (Map.Entry<String, Binding> boundName : module.namespace().entrySet()) {
      String exportKey = boundName.getKey();
      if (namespace.isPropertyTypeDeclared(exportKey)) {
        // Cannot change the type of a declared property after it is added to the ObjectType.
        continue;
      }

      Binding binding = boundName.getValue();
      Node bindingSourceNode = binding.sourceNode(); // e.g. 'x' in `export let x;` or `export {x};`
      ScopedName export = getScopedNameFromEsBinding(binding);
      TypedScope originalScope =
          export.getScopeRoot() == scope.getRootNode()
              ? scope
              : nodeToScopeMapper.apply(export.getScopeRoot());
      if (originalScope == null) {
        // Exporting an import from an invalid module load or early reference.
        namespace.defineInferredProperty(
            exportKey, registry.getNativeType(JSTypeNative.UNKNOWN_TYPE), bindingSourceNode);
        continue;
      }

      TypedVar originalName = originalScope.getSlot(export.getName());
      JSType exportType = originalName.getType();
      if (exportType == null) {
        exportType = registry.getNativeType(JSTypeNative.NO_TYPE);
      }
      if (originalName.isTypeInferred()) {
        // NB: this method may be either adding a new inferred property or updating the type of an
        // existing inferred property.
        namespace.defineInferredProperty(exportKey, exportType, bindingSourceNode);
      } else {
        namespace.defineDeclaredProperty(exportKey, exportType, bindingSourceNode);
      }

      bindingSourceNode.setTypedefTypeProp(originalName.getNameNode().getTypedefTypeProp());
    }
  }

  /** Given a Binding from an ES module, return the name and scope of the bound name. */
  private static ScopedName getScopedNameFromEsBinding(Binding binding) {
    // NB: If the original export was an `export default` then the local name is *default*.
    // We've already declared a dummy variable named `*default*` in the scope.
    String name = binding.isModuleNamespace() ? Export.NAMESPACE : binding.boundName();
    ModuleMetadata originalMetadata =
        binding.isModuleNamespace()
            ? binding.metadata()
            : binding.originatingExport().moduleMetadata();
    if (!originalMetadata.isEs6Module()) {
      // Importing SCRIPTs should not allow you to look up names in scope.
      return ScopedName.of(name, null);
    }
    Node scriptNode = originalMetadata.rootNode();
    // Imports of nonexistent modules have a null 'root node'. Imports of names from scripts are
    // meaningless.
    checkState(scriptNode == null || scriptNode.isScript(), scriptNode);
    return ScopedName.of(name, scriptNode != null ? scriptNode.getOnlyChild() : null);
  }

  /** Returns the {@link Module} corresponding to this scope root, or null if not a module root. */
  @Nullable
  static Module getModuleFromScopeRoot(
      ModuleMap moduleMap, CompilerInputProvider inputProvider, Node moduleBody) {
    if (isGoogModuleBody(moduleBody)) {
      Node googModuleCall = moduleBody.getFirstChild();
      String namespace = googModuleCall.getFirstChild().getSecondChild().getString();
      return moduleMap.getClosureModule(namespace);
    } else if (moduleBody.isModuleBody()) {
      Node scriptNode = moduleBody.getParent();
      CompilerInput input = checkNotNull(inputProvider.getInput(scriptNode.getInputId()));
      Module module = moduleMap.getModule(input.getPath());
      // TODO(b/131418081): Also cover CommonJS modules.
      checkState(
          module.metadata().isEs6Module(),
          "Typechecking of non-goog- and non-es-modules not supported");
      return module;
    }
    return null;
  }

  private static boolean isGoogModuleBody(Node moduleBody) {
    if (moduleBody.isModuleBody()) {
      return moduleBody.getParent().getBooleanProp(Node.GOOG_MODULE);
    } else if (moduleBody.isBlock()) {
      return moduleBody.getParent().isFunction()
          && NodeUtil.isBundledGoogModuleCall(moduleBody.getGrandparent());
    }
    return false;
  }
}
