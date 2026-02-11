/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerInput.ModuleType;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import org.jspecify.annotations.Nullable;

/**
 * Find and update any direct dependencies of an input. Used to walk the dependency graph and
 * support a strict depth-first dependency ordering. Marks an input as providing its module name.
 *
 * <p>Discovers dependencies from:
 *
 * <ul>
 *   <li>goog.require calls
 *   <li>ES6 import statements
 *   <li>CommonJS require statements
 *   <li>goog.requireDynamic calls
 * </ul>
 *
 * <p>The order of dependency references is preserved so that a deterministic depth-first ordering
 * can be achieved.
 */
public class FindModuleDependencies implements NodeTraversal.ScopedCallback {
  private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");
  private static final QualifiedName GOOG_PROVIDE = QualifiedName.of("goog.provide");

  private final AbstractCompiler compiler;
  private final boolean supportsEs6Modules;
  private final boolean supportsCommonJsModules;
  private ModuleType moduleType = ModuleType.NONE;
  private @Nullable Scope dynamicImportScope = null;
  private final ImmutableMap<String, String> inputPathByWebpackId;

  FindModuleDependencies(
      AbstractCompiler compiler,
      boolean supportsEs6Modules,
      boolean supportsCommonJsModules,
      ImmutableMap<String, String> inputPathByWebpackId) {
    this.compiler = compiler;
    this.supportsEs6Modules = supportsEs6Modules;
    this.supportsCommonJsModules = supportsCommonJsModules;
    this.inputPathByWebpackId = inputPathByWebpackId;
  }

  private static class FindGoogProvideOrGoogModule extends NodeTraversal.AbstractPreOrderCallback {

    private boolean found;

    boolean isFound() {
      return found;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (found) {
        return false;
      }
      // Shallow traversal, since we don't need to inspect within functions or expressions.
      if (NodeUtil.isShallowStatementTree(parent)) {
        if (n.isExprResult()) {
          Node maybeGetProp = n.getFirstFirstChild();
          if (maybeGetProp != null
              && (GOOG_PROVIDE.matches(maybeGetProp) || GOOG_MODULE.matches(maybeGetProp))) {
            found = true;
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }

  public void process(Node root) {
    checkArgument(root.isScript());
    if (Es6RewriteModules.isEs6ModuleRoot(root)) {
      moduleType = ModuleType.ES6;
    }
    CompilerInput input = compiler.getInput(root.getInputId());

    // The "goog" namespace isn't always specifically required.
    // The deps parser will pick up any access to a `goog.foo()` call
    // and add "goog" as a dependency. If "goog" is a dependency of the
    // file we add it here to the ordered requires so that it's always
    // first.
    if (input.getRequires().contains(Require.BASE)) {
      input.addOrderedRequire(Require.BASE);
    }

    NodeTraversal.traverse(compiler, root, this);

    if (moduleType == ModuleType.ES6) {
      convertToEs6Module(root, true);
    } else if (moduleType == ModuleType.NONE
        && inputPathByWebpackId != null
        && inputPathByWebpackId.containsValue(input.getPath().toString())) {
      moduleType = ModuleType.IMPORTED_SCRIPT;
    }

    input.addProvide(input.getPath().toModuleName());
    input.setJsModuleType(moduleType);
    input.setHasFullParseDependencyInfo(true);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (supportsCommonJsModules
        && n.isFunction()
        && ProcessCommonJSModules.isCommonJsDynamicImportCallback(
            n, compiler.getOptions().getModuleResolutionMode())) {
      if (dynamicImportScope == null) {
        dynamicImportScope = t.getScope();
      }
    }

    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    ModuleLoader.ResolutionMode resolutionMode = compiler.getOptions().getModuleResolutionMode();
    if (NodeUtil.isShallowStatementTree(parent)) {
      if (n.isExprResult()) {
        Node maybeGetProp = n.getFirstFirstChild();
        if (maybeGetProp != null
            && (GOOG_PROVIDE.matches(maybeGetProp) || GOOG_MODULE.matches(maybeGetProp))) {
          moduleType = ModuleType.GOOG;
          return;
        }
      }
    }

    // goog.requireDynamic()
    if (NodeUtil.isGoogRequireDynamicCall(n)) {
      t.getInput().addRequireDynamicImports(n.getLastChild().getString());
    }

    if (supportsEs6Modules && n.isExport()) {
      moduleType = ModuleType.ES6;
      if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
        // export default
      } else if (n.hasTwoChildren()) {
        // export * from 'moduleIdentifier';
        // export {x, y as z} from 'moduleIdentifier';
        addEs6ModuleImportToGraph(t, n);
      }
    } else if (supportsEs6Modules && n.isImport()) {
      moduleType = ModuleType.ES6;
      addEs6ModuleImportToGraph(t, n);
    } else if (supportsEs6Modules
        && n.getToken() == Token.DYNAMIC_IMPORT
        && n.getFirstChild().isString()) {
      String path = n.getFirstChild().getString();
      ModuleLoader.ModulePath modulePath =
          t.getInput()
              .getPath()
              .resolveJsModule(path, n.getSourceFileName(), n.getLineno(), n.getCharno());
      if (modulePath != null) {
        t.getInput().addDynamicRequire(modulePath.toModuleName());
      }
    } else if (supportsCommonJsModules) {
      if (moduleType != ModuleType.GOOG
          && ProcessCommonJSModules.isCommonJsExport(t, n, resolutionMode)) {
        moduleType = ModuleType.COMMONJS;
      } else if (ProcessCommonJSModules.isCommonJsImport(n, resolutionMode)) {
        String path = ProcessCommonJSModules.getCommonJsImportPath(n, resolutionMode);

        ModuleLoader.ModulePath modulePath =
            t.getInput()
                .getPath()
                .resolveJsModule(path, n.getSourceFileName(), n.getLineno(), n.getCharno());

        if (modulePath != null) {
          if (dynamicImportScope != null
              || ProcessCommonJSModules.isCommonJsDynamicImportCallback(
                  NodeUtil.getEnclosingFunction(n), resolutionMode)) {
            t.getInput().addDynamicRequire(modulePath.toModuleName());
          } else {
            t.getInput().addOrderedRequire(Require.commonJs(modulePath.toModuleName(), path));
          }
        }
      }

      // TODO(ChadKillingsworth) add require.ensure support
    }

    if (parent != null
        && (parent.isExprResult() || !t.inGlobalHoistScope())
        && NodeUtil.isGoogRequireCall(n)
        && n.getSecondChild() != null
        && n.getSecondChild().isStringLit()) {
      String namespace = n.getSecondChild().getString();
      if (namespace.startsWith("goog.")) {
        t.getInput().addOrderedRequire(Require.BASE);
      }
      t.getInput().addOrderedRequire(Require.googRequireSymbol(namespace));
    }
  }

  @Override
  public void enterScope(NodeTraversal t) {}

  @Override
  public void exitScope(NodeTraversal t) {
    if (t.getScope() == dynamicImportScope) {
      dynamicImportScope = null;
    }
  }

  /** Adds an es6 module from an import node (import or export statement) to the graph. */
  private void addEs6ModuleImportToGraph(NodeTraversal t, Node n) {
    String moduleName = getEs6ModuleNameFromImportNode(t, n);
    if (moduleName.startsWith("goog.")) {
      t.getInput().addOrderedRequire(Require.BASE);
    }
    t.getInput().addOrderedRequire(Require.es6Import(moduleName, n.getLastChild().getString()));
  }

  /** Get the module name from an import node (import or export statement). */
  private String getEs6ModuleNameFromImportNode(NodeTraversal t, Node n) {
    String importName = n.getLastChild().getString();
    boolean isNamespaceImport = importName.startsWith("goog:");
    if (isNamespaceImport) {
      // Allow importing Closure namespace objects (e.g. from goog.provide or goog.module) as
      //   import ... from 'goog:my.ns.Object'.
      // These are rewritten to plain namespace object accesses.
      return importName.substring("goog:".length());
    } else {
      ModuleLoader.ModulePath modulePath =
          t.getInput()
              .getPath()
              .resolveJsModule(importName, n.getSourceFileName(), n.getLineno(), n.getCharno());
      if (modulePath == null) {
        // The module loader issues an error
        // Fall back to assuming the module is a file path
        modulePath = t.getInput().getPath().resolveModuleAsPath(importName);
      }
      return modulePath.toModuleName();
    }
  }

  private boolean convertToEs6Module(Node root, boolean skipGoogProvideModuleCheck) {
    if (Es6RewriteModules.isEs6ModuleRoot(root)) {
      return true;
    }
    if (!skipGoogProvideModuleCheck) {
      FindGoogProvideOrGoogModule finder = new FindGoogProvideOrGoogModule();
      NodeTraversal.traverse(compiler, root, finder);
      if (finder.isFound()) {
        return false;
      }
    }
    Node moduleNode = new Node(Token.MODULE_BODY).srcref(root);
    moduleNode.addChildrenToBack(root.removeChildren());
    root.addChildToBack(moduleNode);
    return true;
  }
}
