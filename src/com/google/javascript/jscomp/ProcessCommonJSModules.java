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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a CommonJS module http://wiki.commonjs.org/wiki/Modules/1.1.1
 * into a form that can be safely concatenated.
 * Does not add a function around the module body but instead adds suffixes
 * to global variables to avoid conflicts.
 * Calls to require are changed to reference the required module directly.
 * goog.provide and goog.require are emitted for closure compiler automatic
 * ordering.
 */
public final class ProcessCommonJSModules implements CompilerPass {
  private static final String EXPORTS = "exports";
  private static final String MODULE = "module";

  private final Compiler compiler;
  private final ES6ModuleLoader loader;
  private final boolean reportDependencies;

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to
   * rewrite CommonJS modules to a concatenable form.
   *
   * @param compiler The compiler
   * @param loader The module loader which is used to locate CommonJS modules
   */
  public ProcessCommonJSModules(Compiler compiler, ES6ModuleLoader loader) {
    this(compiler, loader, true);
  }

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to
   * rewrite CommonJS modules to a concatenable form.
   *
   * @param compiler The compiler
   * @param loader The module loader which is used to locate CommonJS modules
   * @param reportDependencies Whether the rewriter should report dependency
   *     information to the Closure dependency manager. This needs to be true
   *     if we want to sort CommonJS module inputs correctly. Note that goog.provide
   *     and goog.require calls will still be generated if this argument is
   *     false.
   */
  public ProcessCommonJSModules(Compiler compiler, ES6ModuleLoader loader,
      boolean reportDependencies) {
    this.compiler = compiler;
    this.loader = loader;
    this.reportDependencies = reportDependencies;
  }

  @Override
  public void process(Node externs, Node root) {
    FindGoogProvideOrGoogModule finder = new FindGoogProvideOrGoogModule();
    NodeTraversal.traverseEs6(compiler, root, finder);
    NodeTraversal
        .traverseEs6(compiler, root, new ProcessCommonJsModulesCallback(!finder.found));
  }

  String inputToModuleName(CompilerInput input) {
    return ES6ModuleLoader.toModuleName(loader.normalizeInputAddress(input));
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
      if (found) {
        return false;
      }
      // Shallow traversal, since we don't need to inspect within functions or expressions.
      if (parent == null
          || NodeUtil.isControlStructure(parent)
          || NodeUtil.isStatementBlock(parent)) {
        if (n.isExprResult()) {
          Node maybeGetProp = n.getFirstFirstChild();
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
   * This class detects the UMD pattern by checking if a node includes
   * a "module.exports" or "exports" statement.
   */
  static class FindModuleExportStatements extends AbstractPreOrderCallback {

    private boolean found;

    boolean isFound() {
      return found;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if ((n.isGetProp() &&
           "module.exports".equals(n.getQualifiedName())) ||
          (n.isName() &&
           EXPORTS.equals(n.getString()))) {
        found = true;
      }

      return true;
    }
  }

  /**
   * This class detects the UMD pattern by checking if a node includes
   * a "define.amd" statement.
   */
  static class FindDefineAmdStatements extends AbstractPreOrderCallback {

    private boolean found;

    boolean isFound() {
      return found;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isGetProp() &&
          "define.amd".equals(n.getQualifiedName())) {
        found = true;
      }

      return true;
    }
  }

  /**
   * Visits require, every "script" and special module.exports assignments.
   */
  private class ProcessCommonJsModulesCallback extends
      AbstractPostOrderCallback {

    private int scriptNodeCount = 0;
    private List<Node> moduleExportRefs = new ArrayList<>();
    private List<Node> exportRefs = new ArrayList<>();
    Multiset<String> propertyExportRefCount = HashMultiset.create();
    private final boolean allowFullRewrite;

    public ProcessCommonJsModulesCallback(boolean allowFullRewrite) {
      this.allowFullRewrite = allowFullRewrite;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall() && n.getChildCount() == 2 &&
          n.getFirstChild().matchesQualifiedName("require") &&
          n.getSecondChild().isString()) {
        visitRequireCall(t, n, parent);
      }

      // Finds calls to require.ensure which is a method to allow async loading of
      // CommonJS modules:
      //
      // require.ensure(['/path/to/module1', '/path/to/module2'], function(require) {
      //    var module1 = require('/path/to/module1');
      //    var module2 = require('/path/to/module2');
      // });
      //
      // will be rewritten as an IIFE
      //
      // (function() {
      //   var module1 = require('/path/to/module1');
      //   var module2 = require('/path/to/module2');
      // })()
      //
      // See http://wiki.commonjs.org/wiki/Modules/Async/A
      //     http://www.injectjs.com/docs/0.7.x/cjs/require.ensure.html
      if (n.isCall()
          && n.getChildCount() == 3
          && n.getFirstChild().matchesQualifiedName("require.ensure")) {
        visitRequireEnsureCall(n);
      }

      // Detects UMD pattern, by checking for CommonJS exports and AMD define
      // statements in if-conditions and rewrites the if-then-else block as
      // follows to make sure the CommonJS exports can be reached:
      // 1. When detecting a CommonJS exports statement, it removes the
      // if-condition and the else-branch and adds the then-branch directly
      // to the current parent node:
      //
      // if (typeof module == "object" && module.exports) {
      //   module.exports = foobar;
      // } else if (typeof define === "function" && define.amd) {...}
      //
      // will be rewritten to:
      //
      // module.exports = foobar;
      //
      // 2. When detecting an AMD define statement, it removes the if-condition and
      // the then-branch and adds the else-branch directly to the current parent node:
      //
      // if (typeof define === "function" && define.amd) {
      // ...} else if (typeof module == "object" && module.exports) {...}
      //
      // will be rewritten to:
      //
      // if (typeof module == "object" && module.exports) {...}
      if (allowFullRewrite && n.isIf()) {
        FindModuleExportStatements commonjsFinder = new FindModuleExportStatements();
        Node condition = n.getFirstChild();
        NodeTraversal.traverseEs6(compiler, condition, commonjsFinder);

        if (commonjsFinder.isFound()) {
          visitCommonJSIfStatement(n);
        } else {
          FindDefineAmdStatements amdFinder = new FindDefineAmdStatements();
          NodeTraversal.traverseEs6(compiler, condition, amdFinder);

          if (amdFinder.isFound()) {
            visitAMDIfStatement(n);
          }
        }
      }

      if (n.isScript()) {
        scriptNodeCount++;
        visitScript(t, n);
      }

      if (allowFullRewrite &&  n.isGetProp() &&
          "module.exports".equals(n.getQualifiedName())) {
        Var v = t.getScope().getVar(MODULE);
        // only rewrite "module.exports" if "module" is a free variable,
        // meaning it is not defined in the current scope as a local
        // variable or function parameter
        if (v == null) {
          moduleExportRefs.add(n);
          maybeAddReferenceCount(n);
        }
      }

      if (allowFullRewrite && n.isName() && EXPORTS.equals(n.getString())) {
        Var v = t.getScope().getVar(n.getString());
        if (v == null || v.isGlobal()) {
          exportRefs.add(n);
          maybeAddReferenceCount(n);
        }
      }
    }

    private Node getBaseQualifiedNameNode(Node n) {
      Node refParent = n;
      while (refParent.getParent() != null && refParent.getParent().isQualifiedName()) {
        refParent = refParent.getParent();
      }

      if (refParent == null || !refParent.getParent().isAssign()) {
        return null;
      }

      return refParent;
    }

    private void maybeAddReferenceCount(Node n) {
      Node refParent = getBaseQualifiedNameNode(n);

      if (refParent == null) {
        return;
      }

      String qName = refParent.getQualifiedName();
      if (qName.startsWith("module.exports.")) {
        qName = qName.substring("module.exports.".length());
      } else if (qName.startsWith("exports.")) {
        qName = qName.substring("exports.".length());
      } else {
        return;
      }

      propertyExportRefCount.add(qName);
    }

    /**
     * Visit require calls. Emit corresponding goog.require and rewrite require
     * to be a direct reference to name of require module.
     */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String requireName = require.getSecondChild().getString();
      URI loadAddress = loader.locateCommonJsModule(requireName, t.getInput());
      if (loadAddress == null) {
        compiler.report(t.makeError(require, ES6ModuleLoader.LOAD_ERROR, requireName));
        return;
      }

      String moduleName = ES6ModuleLoader.toModuleName(loadAddress);
      Node script = getCurrentScriptNode(parent);

      // When require("name") is used as a standalone statement (the result isn't used)
      // it indicates that a module is being loaded for the side effects it produces.
      // In this case the require statement should just be removed as the goog.require
      // call inserted will import the module.
      if (!NodeUtil.isExpressionResultUsed(require)
          && parent.isExprResult()
          && NodeUtil.isStatementBlock(parent.getParent())) {
        parent.getParent().removeChild(parent);
      } else {
        Node moduleRef = IR.name(moduleName).srcref(require);
        parent.replaceChild(require, moduleRef);
      }
      if (reportDependencies) {
        t.getInput().addRequire(moduleName);
      }
      // Rewrite require("name").
      script.addChildToFront(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("require")),
              IR.string(moduleName))).useSourceInfoIfMissingFromForTree(require));
      compiler.reportCodeChange();
    }

    /**
     * Visit require.ensure calls. Replace the call with an IIFE.
     */
    private void visitRequireEnsureCall(Node n) {
      Preconditions.checkState(n.getChildCount() == 3);
      Node callbackFunction = n.getChildAtIndex(2);

      // We only support the form where the first argument is an array literal and
      // the the second a callback function which has a single argument
      // with the name "require".
      if (!(n.getSecondChild().isArrayLit()
          && callbackFunction.isFunction()
          && callbackFunction.getChildCount() == 3
          && callbackFunction.getSecondChild().getChildCount() == 1
          && callbackFunction.getSecondChild().getFirstChild().matchesQualifiedName("require"))) {
        return;
      }

      callbackFunction.detachFromParent();

      // Remove the "require" argument from the parameter list.
      callbackFunction.getSecondChild().removeChildren();
      n.removeChildren();
      n.putBooleanProp(Node.FREE_CALL, true);
      n.addChildrenToFront(callbackFunction);

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

      boolean hasExports = !(moduleExportRefs.isEmpty() && exportRefs.isEmpty());

      // Rename vars to not conflict in global scope - but only if the script exports something.
      // If there are no exports, we still need to rewrite type annotations which
      // are module paths
      NodeTraversal.traverseEs6(compiler, script,
          new SuffixVarsCallback(moduleName, hasExports));

      // If the script has no exports, we don't want to output a goog.provide statement
      if (!hasExports) {
        return;
      }

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
              IR.string(moduleName))).useSourceInfoIfMissingFromForTree(script));

      compiler.reportCodeChange();
    }

    /**
     * Rewrites CommonJS part of UMD pattern by removing the if-condition and the
     * else-branch and adds the then-branch directly to the current parent node.
     */
    private void visitCommonJSIfStatement(Node n) {
      Node p = n.getParent();
      if (p != null) {
        // pull out then-branch
        replaceIfStatementWithBranch(n, n.getSecondChild());
      }
    }

    /**
     * Rewrites AMD part of UMD pattern by removing the if-condition and the
     * then-branch and adds the else-branch directly to the current parent node.
     */
    private void visitAMDIfStatement(Node n) {
      Node p = n.getParent();
      if (p != null) {
        if (n.getChildCount() == 3) {
          // pull out else-branch
          replaceIfStatementWithBranch(n, n.getChildAtIndex(2));
        } else {
          // remove entire if-statement if it doesn't have an else-branch
          p.removeChild(n);
        }
      }
    }

    private void replaceIfStatementWithBranch(Node ifStatement, Node branch) {
      Node p = ifStatement.getParent();
      Node newNode = branch;
      // Remove redundant block node. Not strictly necessary, but makes tests more legible.
      if (branch.isBlock() && branch.getChildCount() == 1) {
        newNode = branch.getFirstChild();
        branch.detachChildren();
      } else {
        ifStatement.detachChildren();
      }
      p.replaceChild(ifStatement, newNode);
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
        // moduleName = rhs
        Node ref = moduleExportRefs.get(0);
        Node newName = IR.name(moduleName).srcref(ref);
        newName.setOriginalName(ref.getQualifiedName());
        Node rhsValue = ref.getNext();

        if (rhsValue.isObjectLit()) {
          addConstToObjLitKeys(rhsValue);
        }

        Node assign = ref.getParent();
        assign.replaceChild(ref, newName);
        JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
        builder.recordConstancy();
        JSDocInfo info = builder.build();
        assign.setJSDocInfo(info);
        return;
      }

      Iterable<Node> exports;
      boolean hasLValues = hasExportLValues();
      if (hasLValues) {
        exports = moduleExportRefs;
      } else {
        exports = Iterables.concat(moduleExportRefs, exportRefs);
      }

      // Transform to:
      //
      // moduleName.prop0 = 0; // etc.
      boolean declaredModuleExports = false;
      for (Node ref : exports) {
        // If there is a module exports assignment at this point, we need to
        // add a variable declaration for the module name, because otherwise
        // the default declaration for the goog.provide is a constant and the
        // assignment would violate that constant.
        // Note that the hasOneTopLevelModuleExportAssign() case handles the
        // more common case of assigning to module.exports on the top level,
        // but CommonJS code also sometimes assigns to module.exports inside
        // of more complex expressions.
        if (ref.getParent().isAssign()
            && !ref.getGrandparent().isExprResult()
            && !declaredModuleExports) {
          // Adds "var moduleName" to front of the current file.
          script.addChildToFront(
              IR.var(IR.name(moduleName))
                  .useSourceInfoIfMissingFromForTree(ref));
          declaredModuleExports = true;
        }

        String qName = null;
        if (ref.isQualifiedName()) {
          Node baseName = getBaseQualifiedNameNode(ref);
          if (baseName != null) {
            qName = baseName.getQualifiedName();
            if (qName.startsWith("module.exports.")) {
              qName = qName.substring("module.exports.".length());
            } else {
              qName = qName.substring("exports.".length());
            }
          }
        }

        Node rhsValue = ref.getNext();
        Node newName = IR.name(moduleName).srcref(ref);
        newName.setOriginalName(qName);

        Node parent = ref.getParent();
        parent.replaceChild(ref, newName);

        // If the property was assigned to exactly once, add an @const annotation
        if (parent.isAssign() && qName != null &&  propertyExportRefCount.count(qName) == 1) {
          if (rhsValue != null && rhsValue.isObjectLit()) {
            addConstToObjLitKeys(rhsValue);
          }

          JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
          builder.recordConstancy();
          JSDocInfo info = builder.build();
          parent.setJSDocInfo(info);
        }
      }

      if(!hasLValues) {
        return;
      }

      // Transform exports to exports$$moduleName and set to point
      // to module namespace: exports$$moduleName = moduleName;
      if (!exportRefs.isEmpty()) {
        String aliasName = "exports$$" + moduleName;
        Node aliasNode = IR.var(IR.name(aliasName), IR.name(moduleName))
            .useSourceInfoIfMissingFromForTree(script);
        script.addChildToFront(aliasNode);

        for (Node ref : exportRefs) {
          ref.setOriginalName(ref.getString());
          ref.setString(aliasName);
        }
      }
    }

    /**
     * Add an @const annotation to each key of an object literal
     */
    private void addConstToObjLitKeys(Node n) {
      Preconditions.checkState(n.isObjectLit());
      for (Node key = n.getFirstChild();
           key != null; key = key.getNext()) {
        if (key.getJSDocInfo() == null) {
          JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
          builder.recordConstancy();
          JSDocInfo info = builder.build();
          key.setJSDocInfo(info);
        }
      }
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
          parent.getGrandparent().isScript();
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
    private final boolean fullRewrite;

    SuffixVarsCallback(String suffix, boolean fullRewrite) {
      this.suffix = suffix;
      this.fullRewrite = fullRewrite;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node typeNode : info.getTypeNodes()) {
          fixTypeNode(t, typeNode);
        }
      }

      boolean isShorthandObjLitKey = n.isStringKey() && !n.hasChildren();
      if (fullRewrite && n.isName() || isShorthandObjLitKey) {
        String name = n.getString();
        if (suffix.equals(name)) {
          // TODO(moz): Investigate whether we need to return early in this unlikely situation.
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

        Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          String newName = name + "$$" + suffix;
          if (isShorthandObjLitKey) {
            // Change {a} to {a: a$$module$foo}
            n.addChildToBack(IR.name(newName).useSourceInfoIfMissingFrom(n));
          } else {
            n.setString(newName);
            n.setOriginalName(name);
          }
        }
      }
    }

    /**
     * Replace type name references.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isString()) {
        String name = typeNode.getString();
        if (ES6ModuleLoader.isRelativeIdentifier(name)
            || ES6ModuleLoader.isAbsoluteIdentifier(name)) {
          int lastSlash = name.lastIndexOf('/');
          int endIndex = name.indexOf('.', lastSlash);
          String localTypeName = null;
          if (endIndex == -1) {
            endIndex = name.length();
          } else {
            localTypeName = name.substring(endIndex);
          }

          String moduleName = name.substring(0, endIndex);
          URI loadAddress = loader.locateCommonJsModule(moduleName, t.getInput());
          if (loadAddress == null) {
            t.makeError(typeNode, ES6ModuleLoader.LOAD_ERROR, moduleName);
            return;
          }

          String globalModuleName = ES6ModuleLoader.toModuleName(loadAddress);
          typeNode.setString(
              localTypeName == null ? globalModuleName : globalModuleName + localTypeName);
        } else if (fullRewrite) {
          int endIndex = name.indexOf('.');
          if (endIndex == -1) {
            endIndex = name.length();
          }
          String baseName = name.substring(0, endIndex);
          Var var = t.getScope().getVar(baseName);
          if (var != null && var.isGlobal()) {
            typeNode.setString(baseName + "$$" + suffix + name.substring(endIndex));
            typeNode.setOriginalName(name);
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
