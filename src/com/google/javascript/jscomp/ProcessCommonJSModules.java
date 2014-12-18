/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Rewrites a CommonJS module http://wiki.commonjs.org/wiki/Modules/1.1.1
 * into a form that can be safely concatenated.
 * Does not add a function around the module body but instead adds suffixes
 * to global variables to avoid conflicts.
 * Calls to require are changed to reference the required module directly.
 * goog.provide and goog.require are emitted for closure compiler automatic
 * ordering.
 */
public class ProcessCommonJSModules implements CompilerPass {
  public static final String DEFAULT_FILENAME_PREFIX =
      "." + ES6ModuleLoader.MODULE_SLASH;

  private static final String MODULE_NAME_SEPARATOR = "\\$";
  private static final String MODULE_NAME_PREFIX = "module$";

  private static final String EXPORTS = "exports";

  private final Compiler compiler;
  private final ES6ModuleLoader loader;
  private final boolean reportDependencies;

  ProcessCommonJSModules(Compiler compiler, ES6ModuleLoader loader) {
    this(compiler, loader, true);
  }

  ProcessCommonJSModules(Compiler compiler, ES6ModuleLoader loader,
      boolean reportDependencies) {
    this.compiler = compiler;
    this.loader = loader;
    this.reportDependencies = reportDependencies;
  }

  @Override
  public void process(Node externs, Node root) {
    FindGoogProvideOrGoogModule finder = new FindGoogProvideOrGoogModule();
    NodeTraversal.traverse(compiler, root, finder);
    if (finder.found) {
      return;
    }
    NodeTraversal
        .traverse(compiler, root, new ProcessCommonJsModulesCallback());
  }

  String inputToModuleName(CompilerInput input) {
    return toModuleName(loader.getLoadAddress(input));
  }

  /**
   * Turns a filename into a JS identifier that is used for moduleNames in
   * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
   * and replaces - with _. All moduleNames get a "module$" prefix.
   */
  public static String toModuleName(String filename) {
    return MODULE_NAME_PREFIX +
        filename.replaceAll("^\\." + Pattern.quote(ES6ModuleLoader.MODULE_SLASH), "")
            .replaceAll(Pattern.quote(ES6ModuleLoader.MODULE_SLASH), MODULE_NAME_SEPARATOR)
            .replaceAll(Pattern.quote("\\"), MODULE_NAME_SEPARATOR)
            .replaceAll("\\.js$", "")
            .replaceAll("-", "_")
            .replaceAll(":", "_")
            .replaceAll("\\.", "");
  }

  /**
   * Avoid processing if we find the appearance of goog.provide or goog.module.
   *
   * TODO(moz): Let ES6, CommonJS and goog.provide live happily together.
   */
  static class FindGoogProvideOrGoogModule extends AbstractPreOrderCallback {

    private boolean found;

    boolean isFound() {
      return found;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      // Shallow traversal, since we don't need to inspect within function declarations.
      if (parent == null || !parent.isFunction()
          || n == parent.getFirstChild()) {
        if (n.isExprResult()) {
          Node maybeGetProp = n.getFirstChild().getFirstChild();
          if (maybeGetProp != null
              && (maybeGetProp.matchesQualifiedName("goog.provide")
                  || maybeGetProp.matchesQualifiedName("goog.module"))) {
            found = true;
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }

  /**
   * Visits require, every "script" and special module.exports assignments.
   */
  private class ProcessCommonJsModulesCallback extends
      AbstractPostOrderCallback {

    private int scriptNodeCount = 0;
    private List<Node> moduleExportRefs = Lists.newArrayList();
    private List<Node> exportRefs = Lists.newArrayList();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall() && n.getChildCount() == 2 &&
          n.getFirstChild().matchesQualifiedName("require") &&
          n.getChildAtIndex(1).isString()) {
        visitRequireCall(t, n, parent);
      }

      if (n.isScript()) {
        scriptNodeCount++;
        visitScript(t, n);
      }

      if (n.isGetProp() &&
          "module.exports".equals(n.getQualifiedName())) {
        moduleExportRefs.add(n);
      }

      if (n.isName() && EXPORTS.equals(n.getString())) {
        Var v = t.getScope().getVar(n.getString());
        if (v == null || v.isGlobal()) {
          exportRefs.add(n);
        }
      }
    }

    /**
     * Visit require calls. Emit corresponding goog.require and rewrite require
     * to be a direct reference to name of require module.
     */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String requireName = require.getChildAtIndex(1).getString();
      String loadAddress = loader.locate(requireName, t.getInput());
      try {
        loader.load(loadAddress);
      } catch (ES6ModuleLoader.LoadFailedException e) {
        t.makeError(require, ES6ModuleLoader.LOAD_ERROR, requireName);
      }

      String moduleName = toModuleName(loadAddress);
      Node moduleRef = IR.name(moduleName).srcref(require);
      parent.replaceChild(require, moduleRef);
      Node script = getCurrentScriptNode(parent);
      if (reportDependencies) {
        t.getInput().addRequire(moduleName);
      }
      // Rewrite require("name").
      script.addChildToFront(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("require")),
              IR.string(moduleName))).copyInformationFromForTree(require));
      compiler.reportCodeChange();
    }

    /**
     * Emit goog.provide and add suffix to all global vars to avoid conflicts
     * with other modules.
     */
    private void visitScript(NodeTraversal t, Node script) {
      Preconditions.checkArgument(scriptNodeCount == 1,
          "ProcessCommonJSModules supports only one invocation per " +
          "CompilerInput / script node");

      String moduleName = inputToModuleName(t.getInput());

      // Rename vars to not conflict in global scope.
      NodeTraversal.traverse(compiler, script, new SuffixVarsCallback(
          moduleName));

      // Replace all refs to module.exports and exports
      processExports(script, moduleName);
      moduleExportRefs.clear();
      exportRefs.clear();

      // Add goog.provide calls.
      if (reportDependencies) {
        CompilerInput ci = t.getInput();
        ci.addProvide(moduleName);
      }
      script.addChildToFront(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("provide")),
              IR.string(moduleName))).copyInformationFromForTree(script));

      compiler.reportCodeChange();
    }


    /**
     * Process all references to module.exports and exports.
     *
     * In CommonJS systems, module.exports and exports point to
     * the same object, unless one of them is re-assigned.
     *
     * We handle 2 special forms:
     * 1) Exactly 1 top-level assign to module.exports.
     *    module.exports = ...;
     * 2) Direct reads of exports and module.exports.
     *    This includes assignments to properties of exports,
     *    because these only read the slot itself.
     *    module.exports.prop = ...; // 1 or more times.
     *
     * We do this so that these forms type-check better.
     *
     * All other forms are handled by a more general algorithm.
     */
    private void processExports(Node script, String moduleName) {
      if (hasOneTopLevelModuleExportAssign()) {
        // One top-level assign: transform to
        // /** @const */ var moduleName = rhs
        Node ref = moduleExportRefs.get(0);
        Node newName = IR.name(moduleName);
        newName.putProp(Node.ORIGINALNAME_PROP, ref.getQualifiedName());

        Node newVar = IR.var(newName)
            .copyInformationFromForTree(ref.getParent());
        Node rhsValue = ref.getNext().detachFromParent();
        newVar.getFirstChild().addChildToFront(rhsValue);
        newVar.setJSDocInfo(NodeUtil.createConstantJsDoc());

        // If the rValue is an object literal, check each property to see if
        // it's an alias, and if it is, copy the annotation over.
        // This is a common idiom to export a set of constructors.
        if (rhsValue.isObjectLit()) {
          Scope globalScope = new SyntacticScopeCreator(compiler)
              .createScope(script, null);
          for (Node key = rhsValue.getFirstChild();
               key != null; key = key.getNext()) {
            if (key.getJSDocInfo() == null
                && key.getFirstChild().isName()) {
              Var aliasedVar =
                  globalScope.getVar(key.getFirstChild().getString());
              JSDocInfo info =
                  aliasedVar == null ? null : aliasedVar.getJSDocInfo();
              if (info != null &&
                  info.getVisibility() != JSDocInfo.Visibility.PRIVATE) {
                key.setJSDocInfo(info);
              }
            }
          }
        }

        Node assign = ref.getParent();
        Node exprResult = assign.getParent();
        script.replaceChild(exprResult, newVar);
        return;
      }

      if (!hasExportLValues()) {
        // Transform to:
        //
        // /** @const */ var moduleName = {};
        // moduleName.prop0 = 0; // etc.
        //
        // We consider the 0-ref case a special case of this.
        Node newVar = injectExportsObject(script, moduleName);
        newVar.setJSDocInfo(NodeUtil.createConstantJsDoc());

        for (Node ref : Iterables.concat(moduleExportRefs, exportRefs)) {
          Node newRef = IR.name(moduleName).copyInformationFrom(ref);
          newRef.putProp(Node.ORIGINALNAME_PROP, ref.getQualifiedName());
          ref.getParent().replaceChild(ref, newRef);
        }
        return;
      }

      // The general case:
      // At the beginning, add the stanza:
      // var moduleName = {}; var moduleName$$exports = moduleName;
      // Transform module.exports to moduleName
      // Transform exports to moduleName$$exports
      Node exportsNode = injectExportsObject(script, moduleName);

      for (Node ref : moduleExportRefs) {
        Node newRef = IR.name(moduleName).copyInformationFrom(ref);
        ref.getParent().replaceChild(ref, newRef);
      }

      if (!exportRefs.isEmpty()) {
        String aliasName = "exports$$" + moduleName;
        Node aliasNode = IR.var(IR.name(aliasName), IR.name(moduleName))
            .copyInformationFromForTree(script);
        script.addChildAfter(aliasNode, exportsNode);

        for (Node ref : exportRefs) {
          ref.putProp(Node.ORIGINALNAME_PROP, ref.getString());
          ref.setString(aliasName);
        }
      }
    }

    /**
     * Creates an exports object for this module.
     * var moduleName = {};
     */
    private Node injectExportsObject(Node script, String moduleName) {
      Node varNode = IR.var(IR.name(moduleName), IR.objectlit())
          .copyInformationFromForTree(script);
      script.addChildToFront(varNode);
      return varNode;
    }

    /**
     * Recognize export pattern [1] (see above).
     */
    private boolean hasOneTopLevelModuleExportAssign() {
      return moduleExportRefs.size() == 1 &&
          exportRefs.isEmpty() &&
          isTopLevelAssignLhs(moduleExportRefs.get(0));
    }

    private boolean isTopLevelAssignLhs(Node n) {
      Node parent = n.getParent();
      return parent.isAssign() && n == parent.getFirstChild() &&
          parent.getParent().isExprResult() &&
          parent.getParent().getParent().isScript();
    }

    /**
     * Recognize the opposite of export pattern [2] (see above).
     */
    private boolean hasExportLValues() {
      for (Node ref : Iterables.concat(moduleExportRefs, exportRefs)) {
        if (NodeUtil.isLValue(ref)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns next script node in parents.
     */
    private Node getCurrentScriptNode(Node n) {
      while (true) {
        if (n.isScript()) {
          return n;
        }
        n = n.getParent();
      }
    }
  }

  /**
   * Traverses a node tree and appends a suffix to all global variable names.
   */
  private class SuffixVarsCallback extends AbstractPostOrderCallback {
    private final String suffix;

    SuffixVarsCallback(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node typeNode : info.getTypeNodes()) {
          fixTypeNode(t, typeNode);
        }
      }

      if (n.isName()) {
        String name = n.getString();
        if (suffix.equals(name)) {
          return;
        }

        // refs to 'exports' are handled separately.
        if (EXPORTS.equals(name)) {
          return;
        }

        // closure_test_suite looks for test*() functions
        if (compiler.getOptions().exportTestFunctions && name.startsWith("test")) {
          return;
        }

        Scope.Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          n.setString(name + "$$" + suffix);
          n.putProp(Node.ORIGINALNAME_PROP, name);
        }
      }
    }

    /**
     * Replace type name references.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isString()) {
        String name = typeNode.getString();
        if (ES6ModuleLoader.isRelativeIdentifier(name)) {
          int lastSlash = name.lastIndexOf('/');
          int endIndex = name.indexOf('.', lastSlash);
          String localTypeName = null;
          if (endIndex == -1) {
            endIndex = name.length();
          } else {
            localTypeName = name.substring(endIndex);
          }

          String moduleName = name.substring(0, endIndex);
          String loadAddress = loader.locate(moduleName, t.getInput());
          if (loadAddress == null) {
            t.makeError(typeNode, ES6ModuleLoader.LOAD_ERROR, moduleName);
            return;
          }

          String globalModuleName = toModuleName(loadAddress);
          typeNode.setString(
              localTypeName == null ?
              globalModuleName :
              globalModuleName + localTypeName);
        } else {
          int endIndex = name.indexOf('.');
          if (endIndex == -1) {
            endIndex = name.length();
          }
          String baseName = name.substring(0, endIndex);
          Scope.Var var = t.getScope().getVar(baseName);
          if (var != null && var.isGlobal()) {
            typeNode.setString(baseName + "$$" + suffix + name.substring(endIndex));
            typeNode.putProp(Node.ORIGINALNAME_PROP, name);
          }
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(t, child);
      }
    }
  }
}
