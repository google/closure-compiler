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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.rhino.Node;
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

  private static final String GOOG = "goog";
  private static final ImmutableSet<String> googDependencyCalls =
      ImmutableSet.of("require", "requireType", "forwardDeclare");

  ModuleImportResolver(ModuleMap moduleMap, Function<Node, TypedScope> nodeToScopeMapper) {
    this.moduleMap = moduleMap;
    this.nodeToScopeMapper = nodeToScopeMapper;
  }

  /** Returns whether this is a CALL node for goog.require(Type) or goog.forwardDeclare */
  boolean isGoogModuleDependencyCall(Node value) {
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
   * <p>This returns null if the namespace does not exist.
   *
   * <p>This also relies on being able to look up the other module's scope by its root node. If the
   * other module is not in {@code nodeToScopeMapper} then this returns null.
   */
  @Nullable
  TypedVar getClosureNamespaceTypeFromCall(Node googRequire) {
    if (moduleMap == null) {
      // TODO(b/124919359): make sure all tests have generated a ModuleMap
      return null;
    }
    String moduleId = googRequire.getSecondChild().getString();
    Module module = moduleMap.getClosureModule(moduleId);

    TypedScope moduleScope = module != null ? getModuleScope(module) : null;
    if (moduleScope != null) {
      return moduleScope.getVar("exports");
    }
    // TODO(b/124919359): handle this case, which happens with, e.g., requireType
    return null;
  }

  /** Converts a {@link Module} reference to a {@link TypedScope} */
  @Nullable
  private TypedScope getModuleScope(@Nullable Module module) {

    // TODO(b/124919359): also handle ES modules and goog.provides
    Node scriptNode = module.metadata().rootNode();
    if (scriptNode == null) {
      return null;
    }

    if (scriptNode.isScript() && scriptNode.hasOneChild()) {
      // The module root node should be a SCRIPT, whose first child is a MODULE_BODY.
      // The map is keyed off a MODULE_BODY node for a goog.module,
      // which is the only child of our SCRIPT node.
      return nodeToScopeMapper.apply(scriptNode.getOnlyChild());
    } else if (NodeUtil.isBundledGoogModuleCall(scriptNode)) {
      // This is a goog.loadModule call, and the scope is keyed off the FUNCTION node's BLOCK in:
      //   goog.loadModule(function(exports) {
      Node functionLiteral = scriptNode.getSecondChild();
      return nodeToScopeMapper.apply(NodeUtil.getFunctionBody(functionLiteral));
    }

    return null;
  }
}
