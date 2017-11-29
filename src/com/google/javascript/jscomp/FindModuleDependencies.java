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

import com.google.javascript.jscomp.CompilerInput.ModuleType;
import com.google.javascript.jscomp.Es6RewriteModules.FindGoogProvideOrGoogModule;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Find and update any direct dependencies of an input. Used to walk the dependency graph and
 * support a strict depth-first dependency ordering. Marks an input as providing its module name.
 *
 * <p>Discovers dependencies from:
 * <ul>
 *   <li> goog.require calls
 *   <li> ES6 import statements
 *   <li> CommonJS require statements
 * </ul>
 *
 * <p>The order of dependency references is preserved so that a deterministic depth-first ordering
 * can be achieved.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
public class FindModuleDependencies extends NodeTraversal.AbstractPostOrderCallback {
  private final AbstractCompiler compiler;
  private final boolean supportsEs6Modules;
  private final boolean supportsCommonJsModules;
  private ModuleType moduleType = ModuleType.NONE;

  FindModuleDependencies(
      AbstractCompiler compiler, boolean supportsEs6Modules, boolean supportsCommonJsModules) {
    this.compiler = compiler;
    this.supportsEs6Modules = supportsEs6Modules;
    this.supportsCommonJsModules = supportsCommonJsModules;
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
    if (input.getRequires().contains("goog")) {
      input.addOrderedRequire("goog");
    }

    NodeTraversal.traverseEs6(compiler, root, this);

    if (moduleType == ModuleType.ES6) {
      convertToEs6Module(root, true);
    }
    input.addProvide(input.getPath().toModuleName());
    input.setJsModuleType(moduleType);
    input.setHasFullParseDependencyInfo(true);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null
        || NodeUtil.isControlStructure(parent)
        || NodeUtil.isStatementBlock(parent)) {
      if (n.isExprResult()) {
        Node maybeGetProp = n.getFirstFirstChild();
        if (maybeGetProp != null
            && (maybeGetProp.matchesQualifiedName("goog.provide")
                || maybeGetProp.matchesQualifiedName("goog.module"))) {
          moduleType = ModuleType.GOOG;
          return;
        }
      }
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
    } else if (supportsCommonJsModules) {
      if (moduleType != ModuleType.GOOG && ProcessCommonJSModules.isCommonJsExport(t, n)) {
        moduleType = ModuleType.COMMONJS;
      } else if (ProcessCommonJSModules.isCommonJsImport(n)) {
        String path = ProcessCommonJSModules.getCommonJsImportPath(n);

        ModuleLoader.ModulePath modulePath =
            t.getInput()
                .getPath()
                .resolveJsModule(path, n.getSourceFileName(), n.getLineno(), n.getCharno());

        if (modulePath != null) {
          t.getInput().addOrderedRequire(modulePath.toModuleName());
        }
      }

      // TODO(ChadKillingsworth) add require.ensure support
    }

    if (parent != null
        && (parent.isExprResult() || !t.inGlobalHoistScope())
        && n.isCall()
        && n.getFirstChild().matchesQualifiedName("goog.require")
        && n.getSecondChild() != null
        && n.getSecondChild().isString()) {
      String namespace = n.getSecondChild().getString();
      if (namespace.startsWith("goog.")) {
        t.getInput().addOrderedRequire("goog");
      }
      t.getInput().addOrderedRequire(namespace);
    }
  }

  /**
   * Convert a script into a module by marking it's root node as a module body. This allows a script
   * which is imported as a module to be scoped as a module even without "import" or "export"
   * statements. Fails if the file contains a goog.provide or goog.module.
   *
   * @return True, if the file is now an ES6 module. False, if the file must remain a script.
   */
  public boolean convertToEs6Module(Node root) {
    return this.convertToEs6Module(root, false);
  }

  /**
   * Adds an es6 module from an import node (import or export statement) to the graph.
   */
  private void addEs6ModuleImportToGraph(NodeTraversal t, Node n) {
    String moduleName = getEs6ModuleNameFromImportNode(t, n);
    if (moduleName.startsWith("goog.")) {
      t.getInput().addOrderedRequire("goog");
    }
    t.getInput().addOrderedRequire(moduleName);
  }

  /**
   * Get the module name from an import node (import or export statement).
   */
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
      NodeTraversal.traverseEs6(compiler, root, finder);
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
