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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;

/**
 * Resolves module requires into {@link TypedVar}s.
 *
 * <p>Currently this only supports goog.modules, but can be extended for ES modules.
 */
final class ModuleImportResolver {

  private final ModuleMap moduleMap;

  private static final String GOOG = "goog";
  private static final ImmutableSet<String> googDependencyCalls =
      ImmutableSet.of("require", "requireType", "forwardDeclare");

  ModuleImportResolver(ModuleMap moduleMap) {
    this.moduleMap = moduleMap;
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
}
