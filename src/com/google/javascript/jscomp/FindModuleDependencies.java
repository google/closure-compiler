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

import com.google.javascript.jscomp.Es6RewriteModules.FindGoogProvideOrGoogModule;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Find and update any direct dependencies of an input. Used to walk the dependency graph and
 * support a strict depth-first dependency ordering. Marks an input as providing its module name.
 *
 * <p>Discovers dependencies from: - goog.require calls - ES6 import statements - CommonJS require
 * statements
 *
 * <p>The order of dependency references is preserved so that a deterministic depth-first ordering
 * can be achieved.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
public class FindModuleDependencies implements NodeTraversal.Callback {
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
    if (FindModuleDependencies.isEs6ModuleRoot(root)) {
      moduleType = ModuleType.ES6;
    }

    NodeTraversal.traverseEs6(compiler, root, this);

    if (moduleType == ModuleType.ES6) {
      convertToEs6Module(root, true);
    }
    CompilerInput input = compiler.getInput(root.getInputId());
    input.addProvide(input.getPath().toModuleName());
    if (moduleType != ModuleType.NONE) {
      input.markAsModule(true);
    }
    input.setHasFullParseDependencyInfo(true);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (supportsEs6Modules && n.isExport()) {
      moduleType = ModuleType.ES6;

    } else if (supportsEs6Modules && n.isImport()) {
      moduleType = ModuleType.ES6;
      String moduleName;
      String importName = n.getLastChild().getString();
      boolean isNamespaceImport = importName.startsWith("goog:");
      if (isNamespaceImport) {
        // Allow importing Closure namespace objects (e.g. from goog.provide or goog.module) as
        //   import ... from 'goog:my.ns.Object'.
        // These are rewritten to plain namespace object accesses.
        moduleName = importName.substring("goog:".length());
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
        moduleName = modulePath.toModuleName();
      }
      t.getInput().addOrderedRequire(moduleName);
    } else if (supportsCommonJsModules) {
      if (n.matchesQualifiedName("module.exports")) {
        Var v = t.getScope().getVar("module");
        if (v == null) {
          moduleType = ModuleType.COMMONJS;
        }
      } else if (n.isName() && "exports".equals(n.getString())) {
        Var v = t.getScope().getVar("exports");
        if (v == null) {
          moduleType = ModuleType.COMMONJS;
        }
      }

      if (n.isCall()
          && n.hasTwoChildren()
          && n.getFirstChild().matchesQualifiedName("require")
          && n.getSecondChild().isString()) {
        String requireName = n.getSecondChild().getString();
        ModuleLoader.ModulePath modulePath =
            t.getInput()
                .getPath()
                .resolveJsModule(requireName, n.getSourceFileName(), n.getLineno(), n.getCharno());

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
      t.getInput().addOrderedRequire(namespace);
    }
  }

  /** Return whether or not the given script node represents an ES6 module file. */
  public static boolean isEs6ModuleRoot(Node scriptNode) {
    checkArgument(scriptNode.isScript());
    if (scriptNode.getBooleanProp(Node.GOOG_MODULE)) {
      return false;
    }
    return scriptNode.hasChildren() && scriptNode.getFirstChild().isModuleBody();
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

  private boolean convertToEs6Module(Node root, boolean skipGoogProvideModuleCheck) {
    if (isEs6ModuleRoot(root)) {
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

  enum ModuleType {
    NONE,
    GOOG_MODULE,
    ES6,
    COMMONJS
  }
}
