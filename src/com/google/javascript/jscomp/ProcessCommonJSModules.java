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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites a CommonJS module http://wiki.commonjs.org/wiki/Modules/1.1.1 into a form that can be
 * safely concatenated. Does not add a function around the module body but instead adds suffixes to
 * global variables to avoid conflicts. Calls to require are changed to reference the required
 * module directly.
 */
public final class ProcessCommonJSModules extends NodeTraversal.AbstractPreOrderCallback
    implements CompilerPass {
  private static final String EXPORTS = "exports";
  private static final String MODULE = "module";
  private static final String REQUIRE = "require";
  private static final String WEBPACK_REQUIRE = "__webpack_require__";
  // Webpack transpiles import() statements to __webpack_require__.t(modulePath)
  // Always imports the module namespace regardless of module type
  private static final String WEBPACK_REQUIRE_NAMESPACE = "__webpack_require__.t";
  private static final String EXPORT_PROPERTY_NAME = "default";

  public static final DiagnosticType UNKNOWN_REQUIRE_ENSURE =
      DiagnosticType.warning(
          "JSC_COMMONJS_UNKNOWN_REQUIRE_ENSURE_ERROR", "Unrecognized require.ensure call: {0}");

  public static final DiagnosticType SUSPICIOUS_EXPORTS_ASSIGNMENT =
      DiagnosticType.warning(
          "JSC_COMMONJS_SUSPICIOUS_EXPORTS_ASSIGNMENT",
          "Suspicious re-assignment of \"exports\" variable."
              + " Did you actually intend to export something?");

  private final AbstractCompiler compiler;

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to rewrite CommonJS modules to
   * a concatenable form.
   *
   * @param compiler The compiler
   */
  public ProcessCommonJSModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    NodeTraversal.traverse(compiler, externs, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isRoot()) {
      return true;
    } else if (n.isScript()) {
      if (compiler.getOptions().getModuleResolutionMode() == ModuleLoader.ResolutionMode.WEBPACK) {
        removeWebpackModuleShim(n);
      }

      FindImportsAndExports finder = new FindImportsAndExports();
      ErrorHandler moduleLoaderErrorHandler = compiler.getModuleLoader().getErrorHandler();
      compiler.getModuleLoader().setErrorHandler(finder);
      NodeTraversal.traverse(compiler, n, finder);

      CompilerInput.ModuleType moduleType = compiler.getInput(n.getInputId()).getJsModuleType();

      boolean forceModuleDetection = moduleType == CompilerInput.ModuleType.IMPORTED_SCRIPT;
      boolean defaultExportIsConst = true;

      boolean isCommonJsModule = finder.isCommonJsModule();
      ImmutableList.Builder<ExportInfo> exports = ImmutableList.builder();
      if (isCommonJsModule || forceModuleDetection) {
        if (!finder.umdPatterns.isEmpty()) {
          boolean needsRetraverse = false;
          if (finder.replaceUmdPatterns()) {
            needsRetraverse = true;
          }
          // Removing the IIFE rewrites vars. We need to re-traverse
          // to get the new references.
          if (removeIIFEWrapper(n)) {
            needsRetraverse = true;
          }

          if (needsRetraverse) {
            finder = new FindImportsAndExports();
            compiler.getModuleLoader().setErrorHandler(finder);
            NodeTraversal.traverse(compiler, n, finder);
          }
        }

        defaultExportIsConst = finder.initializeModule();

        exports.addAll(finder.getModuleExports());
        exports.addAll(finder.getExports());
      }
      finder.reportModuleErrors();
      compiler.getModuleLoader().setErrorHandler(moduleLoaderErrorHandler);

      NodeTraversal.traverse(
          compiler,
          n,
          new RewriteModule(
              isCommonJsModule || forceModuleDetection, exports.build(), defaultExportIsConst));
    }
    return false;
  }

  public static @Nullable String getModuleName(CompilerInput input) {
    ModulePath modulePath = input.getPath();
    if (modulePath == null) {
      return null;
    }

    return getModuleName(modulePath);
  }

  public static String getModuleName(ModulePath input) {
    return input.toModuleName();
  }

  String getBasePropertyImport(String moduleName, Node requireCall) {
    checkArgument(requireCall.isCall());
    if (compiler.getOptions().getModuleResolutionMode() == ModuleLoader.ResolutionMode.WEBPACK
        && requireCall.getFirstChild().matchesQualifiedName(WEBPACK_REQUIRE_NAMESPACE)) {
      return moduleName;
    }

    return getBasePropertyImport(moduleName);
  }

  public String getBasePropertyImport(String moduleName) {
    CompilerInput.ModuleType moduleType = compiler.getModuleTypeByName(moduleName);
    if (moduleType != null && moduleType != CompilerInput.ModuleType.COMMONJS) {
      return moduleName;
    }

    return moduleName + "." + EXPORT_PROPERTY_NAME;
  }

  public boolean isCommonJsImport(Node requireCall) {
    return isCommonJsImport(requireCall, compiler.getOptions().getModuleResolutionMode());
  }

  /**
   * Recognize if a node is a module import. We recognize two forms:
   *
   * <ul>
   *   <li>require("something");
   *   <li>__webpack_require__(4); // only when the module resolution is WEBPACK
   *   <li>__webpack_require__.t(4); // only when the module resolution is WEBPACK
   * </ul>
   */
  public static boolean isCommonJsImport(
      Node requireCall, ModuleLoader.ResolutionMode resolutionMode) {
    if (requireCall.isCall() && requireCall.hasTwoChildren()) {
      if (resolutionMode == ModuleLoader.ResolutionMode.WEBPACK
          && (requireCall.getFirstChild().matchesQualifiedName(WEBPACK_REQUIRE)
              || requireCall.getFirstChild().matchesQualifiedName(WEBPACK_REQUIRE_NAMESPACE))
          && (requireCall.getSecondChild().isNumber()
              || requireCall.getSecondChild().isStringLit())) {
        return true;
      } else if (requireCall.getFirstChild().matchesQualifiedName(REQUIRE)
          && requireCall.getSecondChild().isStringLit()) {
        return true;
      }
    } else if (requireCall.isCall()
        && requireCall.hasXChildren(3)
        && resolutionMode == ModuleLoader.ResolutionMode.WEBPACK
        && requireCall.getFirstChild().matchesQualifiedName(WEBPACK_REQUIRE + ".bind")
        && requireCall.getSecondChild().isNull()
        && (requireCall.getLastChild().isNumber() || requireCall.getLastChild().isStringLit())) {
      return true;
    }
    return false;
  }

  public String getCommonJsImportPath(Node requireCall) {
    return getCommonJsImportPath(requireCall, compiler.getOptions().getModuleResolutionMode());
  }

  public static String getCommonJsImportPath(
      Node requireCall, ModuleLoader.ResolutionMode resolutionMode) {
    if (resolutionMode == ModuleLoader.ResolutionMode.WEBPACK) {
      Node pathArgument =
          requireCall.getChildCount() >= 3
              ? requireCall.getChildAtIndex(2)
              : requireCall.getSecondChild();
      if (pathArgument.isNumber()) {
        return String.valueOf((int) pathArgument.getDouble());
      } else {
        return pathArgument.getString();
      }
    }

    return requireCall.getSecondChild().getString();
  }

  private String getImportedModuleName(NodeTraversal t, Node requireCall) {
    return getImportedModuleName(t, requireCall, getCommonJsImportPath(requireCall));
  }

  private String getImportedModuleName(NodeTraversal t, Node n, String importPath) {
    ModulePath modulePath =
        t.getInput()
            .getPath()
            .resolveJsModule(importPath, n.getSourceFileName(), n.getLineno(), n.getCharno());

    if (modulePath == null) {
      return ModuleIdentifier.forFile(importPath).moduleName();
    }
    return modulePath.toModuleName();
  }

  /**
   * Recognize if a node is a module export. We recognize several forms:
   *
   * <ul>
   *   <li>module.exports = something;
   *   <li>module.exports.something = something;
   *   <li>exports.something = something;
   * </ul>
   *
   * <p>In addition, we only recognize an export if the base export object is not defined or is
   * defined in externs.
   */
  public static boolean isCommonJsExport(
      NodeTraversal t, Node export, ModuleLoader.ResolutionMode resolutionMode) {
    if (export.matchesQualifiedName(MODULE + "." + EXPORTS)
        || (export.isGetElem()
            && export.getFirstChild().matchesQualifiedName(MODULE)
            && export.getSecondChild().isStringLit()
            && export.getSecondChild().getString().equals(EXPORTS))) {
      Var v = t.getScope().getVar(MODULE);
      if (v == null || v.isExtern()) {
        return true;
      }
    } else if (export.isName() && EXPORTS.equals(export.getString())) {
      Var v = t.getScope().getVar(export.getString());
      if (v == null || v.isGlobal()) {
        return true;
      }
    }
    return false;
  }

  private boolean isCommonJsExport(NodeTraversal t, Node export) {
    return ProcessCommonJSModules.isCommonJsExport(
        t, export, compiler.getOptions().getModuleResolutionMode());
  }

  /**
   * Recognize if a node is a dynamic module import. Currently only the webpack dynamic import is
   * recognized:
   *
   * <ul>
   *   <li>__webpack_require__.e(0).then(function() { return __webpack_require__(4);})
   *   <li>Promise.all([__webpack_require__.e(0)]).then(function() { return
   *       __webpack_require__(4);})
   * </ul>
   */
  public static boolean isCommonJsDynamicImportCallback(
      Node n, ModuleLoader.ResolutionMode resolutionMode) {
    if (n == null || resolutionMode != ModuleLoader.ResolutionMode.WEBPACK) {
      return false;
    }
    if (n.isFunction() && isWebpackRequireEnsureCallback(n)) {
      return true;
    }

    return false;
  }

  private static final QualifiedName PROMISE_ALL = QualifiedName.of("Promise.all");

  /**
   * Recognizes __webpack_require__ calls that are the .then callback of a __webpack_require__.e
   * call. Example:
   *
   * <ul>
   *   <li>__webpack_require__.e(0).then(function() { return __webpack_require__(4); })
   *   <li>Promise.all([__webpack_require__.e(0)]).then(function() { return
   *       __webpack_require__(4);})
   * </ul>
   */
  private static boolean isWebpackRequireEnsureCallback(Node fnc) {
    checkArgument(fnc.isFunction());
    if (fnc.getParent() == null) {
      return false;
    }

    Node callParent = fnc.getParent();
    if (!callParent.isCall()) {
      return false;
    }

    if (!(callParent.hasChildren()
        && callParent.getFirstChild().isGetProp()
        && callParent.getFirstFirstChild().isCall())) {
      return false;
    }
    Node callParentTarget = callParent.getFirstFirstChild().getFirstChild();

    if (callParentTarget.matchesQualifiedName(WEBPACK_REQUIRE + ".e")
        && callParent.getFirstChild().getString().equals("then")) {
      return true;
    } else if (PROMISE_ALL.matches(callParentTarget)
        && callParentTarget.getNext() != null
        && callParentTarget.getNext().isArrayLit()) {
      boolean allElementsAreDynamicImports = false;
      for (Node arrayItem = callParentTarget.getNext().getFirstChild();
          arrayItem != null;
          arrayItem = arrayItem.getNext()) {
        if (!(arrayItem.isCall()
            && arrayItem.hasTwoChildren()
            && arrayItem.getFirstChild().matchesQualifiedName(WEBPACK_REQUIRE + ".e"))) {
          return false;
        }
        allElementsAreDynamicImports = true;
      }
      return allElementsAreDynamicImports;
    }
    return false;
  }

  /**
   * Information on a Universal Module Definition A UMD is an IF statement and a reference to which
   * branch contains the commonjs export
   */
  static class UmdPattern {
    final Node ifRoot;
    final Node activeBranch;

    UmdPattern(Node ifRoot, @Nullable Node activeBranch) {
      this.ifRoot = ifRoot;
      this.activeBranch = activeBranch;
    }
  }

  static class ExportInfo {
    final Node node;
    final Scope scope;
    final boolean isInSupportedScope;

    ExportInfo(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;

      Node disqualifyingParent =
          NodeUtil.getEnclosingNode(
              node, (n) -> n.isIf() || n.isHook() || n.isFunction() || n.isArrowFunction());
      this.isInSupportedScope = disqualifyingParent == null;
    }
  }

  private Node getBaseQualifiedNameNode(Node n) {
    Node refParent = n;
    while (refParent.hasParent() && refParent.getParent().isQualifiedName()) {
      refParent = refParent.getParent();
    }

    return refParent;
  }

  /**
   * UMD modules are often wrapped in an IIFE for cases where they are used as scripts instead of
   * modules. Remove the wrapper.
   * @return Whether an IIFE wrapper was found and removed.
   */
  private boolean removeIIFEWrapper(Node root) {
    checkState(root.isScript());
    Node n = root.getFirstChild();

    // Sometimes scripts start with a semicolon for easy concatenation.
    // Skip any empty statements from those
    while (n != null && n.isEmpty()) {
      n = n.getNext();
    }

    // An IIFE wrapper must be the only non-empty statement in the script,
    // and it must be an expression statement.
    if (n == null || !n.isExprResult() || n.getNext() != null) {
      return false;
    }

    // Function expression can be forced with !, just skip !
    // TODO(ChadKillingsworth):
    //   Expression could also be forced with: + - ~ void
    //   ! ~ void can be repeated any number of times
    if (n != null && n.hasChildren() && n.getFirstChild().isNot()) {
      n = n.getFirstChild();
    }

    Node call = n.getFirstChild();
    if (call == null || !call.isCall()) {
      return false;
    }

    // Find the IIFE call and function nodes
    Node fnc;
    if (call.getFirstChild().isFunction()) {
      fnc = n.getFirstFirstChild();
    } else if (call.getFirstChild().isGetProp()
        && call.getFirstFirstChild().isFunction()
        && call.getFirstChild().getString().equals("call")) {
      fnc = call.getFirstFirstChild();

      // We only support explicitly binding "this" to the parent "this" or "exports"
      if (!(call.getSecondChild() != null
          && (call.getSecondChild().isThis()
              || call.getSecondChild().matchesQualifiedName(EXPORTS)))) {
        return false;
      }
    } else {
      return false;
    }

    if (NodeUtil.doesFunctionReferenceOwnArgumentsObject(fnc)) {
      return false;
    }

    CompilerInput ci = compiler.getInput(root.getInputId());
    ModulePath modulePath = ci.getPath();
    if (modulePath == null) {
      return false;
    }

    String iifeLabel = getModuleName(modulePath) + "_iifeWrapper";

    FunctionToBlockMutator mutator =
        new FunctionToBlockMutator(compiler, compiler.getUniqueNameIdSupplier());
    Node block = mutator.mutateWithoutRenaming(iifeLabel, fnc, call, null, false, false);
    root.removeChildren();
    root.addChildrenToFront(block.removeChildren());
    reportNestedScopesDeleted(fnc);
    compiler.reportChangeToEnclosingScope(root);

    return true;
  }

  /**
   * For AMD wrappers, webpack adds a shim for the "module" variable. We need that to be a free var
   * so we remove the shim.
   */
  private void removeWebpackModuleShim(Node root) {
    checkState(root.isScript());
    Node n = root.getFirstChild();

    // Sometimes scripts start with a semicolon for easy concatenation.
    // Skip any empty statements from those
    while (n != null && n.isEmpty()) {
      n = n.getNext();
    }

    // An IIFE wrapper must be the only non-empty statement in the script,
    // and it must be an expression statement.
    if (n == null || !n.isExprResult() || n.getNext() != null) {
      return;
    }

    Node call = n.getFirstChild();
    if (call == null || !call.isCall()) {
      return;
    }

    // Find the IIFE call and function nodes

    Node callTarget = call.getFirstChild();
    if (!callTarget.isFunction()) {
      return;
    }

    Node fnc = callTarget;

    Node params = NodeUtil.getFunctionParameters(fnc);
    Node moduleParam = null;
    Node param = params.getFirstChild();
    int paramNumber = 0;
    while (param != null) {
      paramNumber++;
      if (param.isName() && param.getString().equals(MODULE)) {
        moduleParam = param;
        break;
      }
      param = param.getNext();
    }
    if (moduleParam == null) {
      return;
    }

    boolean isFreeCall = call.getBooleanProp(Node.FREE_CALL);
    Node arg = call.getChildAtIndex(isFreeCall ? paramNumber : paramNumber + 1);
    if (arg == null) {
      return;
    }

    Node argCallTarget = arg.getFirstChild();
    if (arg.isCall()
        && argCallTarget.isCall()
        && isCommonJsImport(argCallTarget)
        && argCallTarget.getNext().matchesName(MODULE)) {
      String importPath = getCommonJsImportPath(argCallTarget);

      ModulePath modulePath =
          compiler
              .getInput(root.getInputId())
              .getPath()
              .resolveJsModule(
                  importPath, arg.getSourceFileName(), arg.getLineno(), arg.getCharno());
      if (modulePath == null) {
        // The module loader will issue an error
        return;
      }

      if (modulePath.toString().contains("/buildin/module.js")) {
        arg.detach();
        param.detach();
        compiler.reportChangeToChangeScope(fnc);
        compiler.reportChangeToEnclosingScope(fnc);
      }
    }
  }

  private static final QualifiedName DEFINE_AMD = QualifiedName.of("define.amd");
  private static final QualifiedName WINDOW_DEFINE = QualifiedName.of("window.define");
  private static final QualifiedName WINDOW_DEFINE_AMD = QualifiedName.of("window.define.amd");
  private static final QualifiedName REQUIRE_ENSURE = QualifiedName.of("require.ensure");
  private static final QualifiedName GOOG_PROVIDE = QualifiedName.of("goog.provide");
  private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");
  private static final QualifiedName MODULE_EXPORTS = QualifiedName.of("module.exports");

  /**
   * Traverse the script. Find all references to CommonJS require (import) and module.exports or
   * export statements. Rewrites any require calls to reference the rewritten module name.
   */
  class FindImportsAndExports implements NodeTraversal.Callback, ErrorHandler {
    private boolean hasGoogProvideOrModule = false;
    private @Nullable Node script = null;

    boolean isCommonJsModule() {
      return (!exports.isEmpty() || !moduleExports.isEmpty()) && !hasGoogProvideOrModule;
    }

    List<UmdPattern> umdPatterns = new ArrayList<>();
    List<ExportInfo> moduleExports = new ArrayList<>();
    List<ExportInfo> exports = new ArrayList<>();
    List<JSError> errors = new ArrayList<>();

    @Override
    public void report(CheckLevel ignoredLevel, JSError error) {
      errors.add(error);
    }

    public List<ExportInfo> getModuleExports() {
      return ImmutableList.copyOf(moduleExports);
    }

    public List<ExportInfo> getExports() {
      return ImmutableList.copyOf(exports);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isScript()) {
        checkState(this.script == null);
        this.script = n;
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Check for goog.provide or goog.module statements
      if (NodeUtil.isShallowStatementTree(parent)) {
        if (n.isExprResult()) {
          Node maybeGetProp = n.getFirstFirstChild();
          if (maybeGetProp != null
              && (GOOG_PROVIDE.matches(maybeGetProp) || GOOG_MODULE.matches(maybeGetProp))) {
            hasGoogProvideOrModule = true;
          }
        }
      }

      // Find require.ensure calls
      if (n.isCall() && REQUIRE_ENSURE.matches(n.getFirstChild())) {
        visitRequireEnsureCall(t, n);
      }

      if (n.matchesQualifiedName(MODULE + "." + EXPORTS)
          || (n.isGetElem()
              && n.getFirstChild().matchesQualifiedName(MODULE)
              && n.getSecondChild().isStringLit()
              && n.getSecondChild().getString().equals(EXPORTS))) {
        if (isCommonJsExport(t, n)) {
          moduleExports.add(new ExportInfo(n, t.getScope()));

          // If the module.exports statement is nested in an if statement,
          // and the test of the if checks for "module" or "define,
          // assume the if statement is an UMD pattern with a common js export in the then branch
          UmdTestInfo umdTestAncestor = getOutermostUmdTest(parent);
          if (umdTestAncestor != null && !isInIfTest(n)) {
            UmdPattern existingPattern = findUmdPattern(umdPatterns, umdTestAncestor.enclosingIf);
            if (existingPattern == null) {
              umdPatterns.add(
                  new UmdPattern(umdTestAncestor.enclosingIf, umdTestAncestor.activeBranch));
            }
          }
        }
      } else if (DEFINE_AMD.matches(n) || WINDOW_DEFINE_AMD.matches(n)) {
        // If a define.amd statement is in the test of an if statement,
        // and the statement has no "else" block, we simply want to remove
        // the entire if statement.
        UmdTestInfo umdTestAncestor = getOutermostUmdTest(parent);
        if (umdTestAncestor != null && isInIfTest(n)) {
          UmdPattern existingPattern = findUmdPattern(umdPatterns, umdTestAncestor.enclosingIf);
          if (existingPattern == null && umdTestAncestor.enclosingIf.getChildAtIndex(2) == null) {
            umdPatterns.add(new UmdPattern(umdTestAncestor.enclosingIf, null));
          }
        }
      }

      if (n.isName() && EXPORTS.equals(n.getString())) {
        Var v = t.getScope().getVar(EXPORTS);
        if (v == null || v.isGlobal()) {
          Node qNameRoot = getBaseQualifiedNameNode(n);
          if (qNameRoot != null
              && qNameRoot.matchesQualifiedName(EXPORTS)
              && NodeUtil.isLValue(qNameRoot)) {
            // Match the special assignment
            // exports = module.exports
            if (n.getGrandparent().isExprResult()
                && n.getNext() != null
                && ((n.getNext().isGetProp()
                        && n.getNext().matchesQualifiedName(MODULE + "." + EXPORTS))
                    || (n.getNext().isAssign()
                        && n.getNext()
                            .getFirstChild()
                            .matchesQualifiedName(MODULE + "." + EXPORTS)))) {
              exports.add(new ExportInfo(n, t.getScope()));

              // Ignore inlining created identity vars
              // var exports = exports
            } else if (!this.hasGoogProvideOrModule
                && (v == null
                    || (v.getNameNode() == null && v.getNameNode().getFirstChild() != n))) {
              errors.add(JSError.make(qNameRoot, SUSPICIOUS_EXPORTS_ASSIGNMENT));
            }
          } else {
            exports.add(new ExportInfo(n, t.getScope()));

            // If the exports statement is nested in the then branch of an if statement,
            // and the test of the if checks for "module" or "define,
            // assume the if statement is an UMD pattern with a common js export in the then branch
            UmdTestInfo umdTestAncestor = getOutermostUmdTest(parent);
            if (umdTestAncestor != null && !isInIfTest(n)) {
              UmdPattern existingPattern = findUmdPattern(umdPatterns, umdTestAncestor.enclosingIf);
              if (existingPattern == null) {
                umdPatterns.add(
                    new UmdPattern(umdTestAncestor.enclosingIf, umdTestAncestor.activeBranch));
              }
            }
          }
        }
      } else if (n.isThis() && n.getParent().isGetProp() && t.inGlobalScope()) {
        exports.add(new ExportInfo(n, t.getScope()));
      }

      if (isCommonJsImport(n)) {
        visitRequireCall(t, n, parent);
      }
    }

    /** Visit require calls. */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      // When require("name") is used as a standalone statement (the result isn't used)
      // it indicates that a module is being loaded for the side effects it produces.
      // In this case the require statement should just be removed as the dependency
      // sorting will insert the file for us.
      if (!NodeUtil.isExpressionResultUsed(require)
          && parent.isExprResult()
          && NodeUtil.isStatementBlock(parent.getParent())) {

        // Attempt to resolve the module so that load warnings are issued
        t.getInput()
            .getPath()
            .resolveJsModule(
                getCommonJsImportPath(require),
                require.getSourceFileName(),
                require.getLineno(),
                require.getCharno());
        Node grandparent = parent.getParent();
        parent.detach();
        compiler.reportChangeToEnclosingScope(grandparent);
      }
    }

    /**
     * Visit require.ensure calls. Replace the call with an IIFE. Require.ensure must always be of
     * the form:
     *
     * <p>require.ensure(['module1', ...], function(require) {})
     */
    private void visitRequireEnsureCall(NodeTraversal t, Node call) {
      if (!call.hasXChildren(3)) {
        compiler.report(
            JSError.make(
                call,
                UNKNOWN_REQUIRE_ENSURE,
                "Expected the function to have 2 arguments but instead found {0}",
                "" + call.getChildCount()));
        return;
      }

      Node dependencies = call.getSecondChild();
      if (!dependencies.isArrayLit()) {
        compiler.report(
            JSError.make(
                dependencies,
                UNKNOWN_REQUIRE_ENSURE,
                "The first argument must be an array literal of string literals."));
        return;
      }

      for (Node dep = dependencies.getFirstChild(); dep != null; dep = dep.getNext()) {
        if (!dep.isStringLit()) {
          compiler.report(
              JSError.make(
                  dep,
                  UNKNOWN_REQUIRE_ENSURE,
                  "The first argument must be an array literal of string literals."));
          return;
        }
      }
      Node callback = dependencies.getNext();
      if (!(callback.isFunction()
          && callback.getSecondChild().hasOneChild()
          && callback.getSecondChild().getFirstChild().isName()
          && "require".equals(callback.getSecondChild().getFirstChild().getString()))) {
        compiler.report(
            JSError.make(
                callback,
                UNKNOWN_REQUIRE_ENSURE,
                "The second argument must be a function"
                    + " whose first argument is named \"require\"."));
        return;
      }

      callback.detach();

      // Remove the "require" argument from the parameter list.
      callback.getSecondChild().removeChildren();
      call.removeChildren();
      call.putBooleanProp(Node.FREE_CALL, true);
      call.addChildToFront(callback);

      t.reportCodeChange();
    }

    void reportModuleErrors() {
      for (JSError error : errors) {
        compiler.report(error);
      }
    }

    /**
     * If the export is directly assigned more than once, or the assignments are not global, declare
     * the module name variable.
     *
     * <p>If all of the assignments are simply property assignments, initialize the module name
     * variable as a namespace.
     *
     * <p>Returns whether the default export can be declared constant
     */
    boolean initializeModule() {
      CompilerInput ci = compiler.getInput(this.script.getInputId());
      ModulePath modulePath = ci.getPath();
      if (modulePath == null) {
        return true;
      }

      String moduleName = getModuleName(ci);

      List<ExportInfo> exportsToRemove = new ArrayList<>();
      for (ExportInfo export : exports) {
        if (NodeUtil.getEnclosingScript(export.node) == null) {
          exportsToRemove.add(export);
          continue;
        }
        Node qNameBase = getBaseQualifiedNameNode(export.node);
        if (export.node == qNameBase
            && export.node.getParent().isAssign()
            && export.node.getGrandparent().isExprResult()
            && export.node.getPrevious() == null
            && export.node.getNext() != null) {

          // Find any identity assignments and just remove them
          // exports = module.exports;
          if (export.node.getNext().isGetProp()
              && export.node.getNext().matchesQualifiedName(MODULE + "." + EXPORTS)) {
            for (ExportInfo moduleExport : moduleExports) {
              if (moduleExport.node == export.node.getNext()) {
                moduleExports.remove(moduleExport);
                break;
              }
            }

            Node changeRoot = export.node.getGrandparent().getParent();
            export.node.getGrandparent().detach();
            exportsToRemove.add(export);
            compiler.reportChangeToEnclosingScope(changeRoot);

            // Find compound identity assignments and remove the exports = portion
            // exports = module.exports = foo;
          } else if (export.node.getNext().isAssign()
              && export
                  .node
                  .getNext()
                  .getFirstChild()
                  .matchesQualifiedName(MODULE + "." + EXPORTS)) {
            Node assign = export.node.getNext();
            export.node.getParent().replaceWith(assign.detach());
            exportsToRemove.add(export);
            compiler.reportChangeToEnclosingScope(assign);
          }
          // Find babel transpiled interop assignment
          // module.exports = exports['default'];
        } else if (export.node.getParent().isGetElem()
            && export.node.getNext().isStringLit()
            && export.node.getNext().getString().equals(EXPORT_PROPERTY_NAME)
            && export.node.getGrandparent().isAssign()
            && export.node.getParent().getPrevious() != null
            && export.node.getParent().getPrevious().matchesQualifiedName(MODULE + "." + EXPORTS)) {
          Node parent = export.node.getParent();
          Node grandparent = parent.getParent();
          Node prop = export.node.getNext();
          parent.replaceWith(
              IR.getprop(export.node.detach(), prop.detach().getString()).srcref(parent));

          compiler.reportChangeToEnclosingScope(grandparent);
        }
      }

      exports.removeAll(exportsToRemove);
      exportsToRemove.clear();
      LinkedHashMap<ExportInfo, ExportInfo> exportsToReplace = new LinkedHashMap<>();
      for (ExportInfo export : moduleExports) {
        if (NodeUtil.getEnclosingScript(export.node) == null) {
          exportsToRemove.add(export);
        } else if (export.node.isGetElem()) {
          Node prop = export.node.getSecondChild().detach();
          ExportInfo newExport =
              new ExportInfo(
                  IR.getprop(export.node.removeFirstChild(), prop.getString()), export.scope);
          export.node.replaceWith(newExport.node);
          compiler.reportChangeToEnclosingScope(newExport.node);
          exportsToReplace.put(export, newExport);
        }
      }
      moduleExports.removeAll(exportsToRemove);
      for (ExportInfo oldExport : exportsToReplace.keySet()) {
        int oldIndex = moduleExports.indexOf(oldExport);
        moduleExports.remove(oldIndex);
        moduleExports.add(oldIndex, exportsToReplace.get(oldExport));
      }

      // If we assign to the variable more than once or all the assignments
      // are properties, initialize the variable as well.
      int directAssignments = 0;
      for (ExportInfo export : moduleExports) {
        if (NodeUtil.getEnclosingScript(export.node) == null) {
          continue;
        }

        Node base = getBaseQualifiedNameNode(export.node);
        if (base == export.node && export.node.getParent().isAssign()) {
          Node rValue = NodeUtil.getRValueOfLValue(export.node);
          if (rValue == null || !rValue.isObjectLit()) {
            directAssignments++;
          } else if (rValue.isObjectLit()) {
            for (Node key = rValue.getFirstChild(); key != null; key = key.getNext()) {
              if ((!key.isStringKey() || key.isQuotedStringKey()) && !key.isMemberFunctionDef()) {
                directAssignments++;
                break;
              }
            }
          }
        }
      }

      Node initModule = IR.var(IR.name(moduleName), IR.objectlit());
      initModule.getFirstChild().putBooleanProp(Node.MODULE_EXPORT, true);
      initModule.getFirstChild().makeNonIndexable();
      JSDocInfo.Builder builder = JSDocInfo.builder().parseDocumentation();
      builder.recordConstancy();
      initModule.setJSDocInfo(builder.build());
      if (directAssignments == 0 || (!exports.isEmpty() && !moduleExports.isEmpty())) {
        Node defaultProp = IR.stringKey(EXPORT_PROPERTY_NAME);
        defaultProp.putBooleanProp(Node.MODULE_EXPORT, true);
        defaultProp.addChildToFront(IR.objectlit());
        initModule.getFirstFirstChild().addChildToFront(defaultProp);
        if (exports.isEmpty() || moduleExports.isEmpty()) {
          builder = JSDocInfo.builder().parseDocumentation();
          builder.recordConstancy();
          defaultProp.setJSDocInfo(builder.build());
        }
      }
      this.script.addChildToFront(initModule.srcrefTree(this.script));
      compiler.reportChangeToEnclosingScope(this.script);

      return directAssignments < 2 && (exports.isEmpty() || moduleExports.isEmpty());
    }

    private static class UmdTestInfo {
      public final Node enclosingIf;
      public final Node activeBranch;

      UmdTestInfo(Node enclosingIf, Node activeBranch) {
        this.enclosingIf = enclosingIf;
        this.activeBranch = activeBranch;
      }
    }

    /**
     * Find the outermost if node ancestor for a node without leaving the function scope. To match,
     * the test class of the "if" statement must reference "module" or "define" names.
     */
    private @Nullable UmdTestInfo getOutermostUmdTest(Node n) {
      if (n == null || NodeUtil.isTopLevel(n) || n.isFunction()) {
        return null;
      }
      Node parent = n.getParent();
      if (parent == null) {
        return null;
      }

      // When walking up ternary operations (hook), don't check if parent is the condition,
      // because one ternary operation can be then/else branch of another.
      if ((parent.isIf() || parent.isHook())) {
        UmdTestInfo umdTestInfo = getOutermostUmdTest(parent);
        if (umdTestInfo != null) {
          // If this is the then block of an else-if statement, set the active branch to the
          // then block rather than the "if" itself.
          if (parent.isIf()
              && parent.getSecondChild() == n
              && parent.hasParent()
              && parent.getParent().isBlock()
              && parent.getNext() == null
              && umdTestInfo.activeBranch == parent.getParent()) {
            return new UmdTestInfo(umdTestInfo.enclosingIf, n);
          }

          return umdTestInfo;
        }

        final List<Node> umdTests = new ArrayList<>();
        NodeUtil.visitPreOrder(
            parent.getFirstChild(),
            new NodeUtil.Visitor() {
              @Override
              public void visit(Node node) {
                switch (node.getToken()) {
                  case NAME -> {
                    if (node.getString().equals(MODULE) || node.getString().equals("define")) {
                      break;
                    }
                    return;
                  }
                  case GETPROP -> {
                    if (WINDOW_DEFINE.matches(node)) {
                      break;
                    }
                    return;
                  }
                  case STRINGLIT -> {
                    if (node.getParent().isIn() && node.getString().equals("amd")) {
                      break;
                    }
                    return;
                  }
                  default -> {
                    return;
                  }
                }
                umdTests.add(node);
              }
            });

        // Webpack replaces tests of `typeof module !== 'undefined'` with `true`
        if (umdTests.isEmpty() && parent.getFirstChild().isTrue()) {
          umdTests.add(parent.getFirstChild());
        }

        if (!umdTests.isEmpty()) {
          return new UmdTestInfo(parent, n);
        }

        return null;
      }

      return getOutermostUmdTest(parent);
    }

    /** Return whether the node is within the test portion of an if statement */
    private boolean isInIfTest(Node n) {
      if (n == null || NodeUtil.isTopLevel(n) || n.isFunction()) {
        return false;
      }
      Node parent = n.getParent();
      if (parent == null) {
        return false;
      }

      if ((parent.isIf() || parent.isHook()) && parent.getFirstChild() == n) {
        return true;
      }

      return isInIfTest(parent);
    }

    /** Remove a Universal Module Definition and leave just the commonjs export statement */
    boolean replaceUmdPatterns() {
      boolean needsRetraverse = false;
      Node changeScope;
      for (UmdPattern umdPattern : umdPatterns) {
        if (NodeUtil.getEnclosingScript(umdPattern.ifRoot) == null) {
          reportNestedScopesDeleted(umdPattern.ifRoot);
          continue;
        }

        Node parent = umdPattern.ifRoot.getParent();
        Node newNode = umdPattern.activeBranch;

        if (newNode == null) {
          umdPattern.ifRoot.detach();
          reportNestedScopesDeleted(umdPattern.ifRoot);
          compiler.reportChangeToEnclosingScope(parent);
          needsRetraverse = true;
          continue;
        }

        // Remove redundant block node. Not strictly necessary, but makes tests more legible.
        if (umdPattern.activeBranch.isBlock() && umdPattern.activeBranch.hasOneChild()) {
          newNode = umdPattern.activeBranch.removeFirstChild();
        } else {
          newNode.detach();
        }
        needsRetraverse = true;
        umdPattern.ifRoot.replaceWith(newNode);
        reportNestedScopesDeleted(umdPattern.ifRoot);
        changeScope = ChangeTracker.getEnclosingChangeScopeRoot(newNode);
        if (changeScope != null) {
          compiler.reportChangeToEnclosingScope(newNode);
        }

        Node block = parent;
        if (block.isExprResult()) {
          block = block.getParent();
        }

        // Detect UMD Factory Patterns and inline the functions
        if (block.isBlock() && block.getParent().isFunction()
            && block.getGrandparent().isCall()
            && parent.hasOneChild()) {
          Node enclosingFnCall = block.getGrandparent();
          Node fn = block.getParent();

          Node enclosingScript = NodeUtil.getEnclosingScript(enclosingFnCall);
          if (enclosingScript == null) {
            continue;
          }
          CompilerInput ci = compiler.getInput(
              NodeUtil.getEnclosingScript(enclosingFnCall).getInputId());
          ModulePath modulePath = ci.getPath();
          if (modulePath == null) {
            continue;
          }
          needsRetraverse = true;
          String factoryLabel =
              modulePath.toModuleName() + "_factory" + compiler.getUniqueNameIdSupplier().get();

          FunctionToBlockMutator mutator =
              new FunctionToBlockMutator(compiler, compiler.getUniqueNameIdSupplier());
          Node newStatements =
              mutator.mutateWithoutRenaming(factoryLabel, fn, enclosingFnCall, null, false, false);

          // Check to see if the returned block is of the form:
          // {
          //   var jscomp$inline = function() {};
          //   jscomp$inline();
          // }
          //
          // or
          //
          // {
          //   var jscomp$inline = function() {};
          //   module.exports = jscomp$inline();
          // }
          //
          // If so, inline again
          if (newStatements.isBlock()
              && newStatements.hasTwoChildren()
              && newStatements.getFirstChild().isVar()
              && newStatements.getFirstFirstChild().hasOneChild()
              && newStatements.getFirstFirstChild().getFirstChild().isFunction()
              && newStatements.getSecondChild().isExprResult()) {
            Node inlinedFn = newStatements.getFirstFirstChild().getFirstChild();
            Node expr = newStatements.getSecondChild().getFirstChild();
            Node call = null;
            String assignedName = null;
            if (expr.isAssign() && expr.getSecondChild().isCall()) {
              call = expr.getSecondChild();
              assignedName =
                  modulePath.toModuleName() + "_iife" + compiler.getUniqueNameIdSupplier().get();
            } else if (expr.isCall()) {
              call = expr;
            }

            if (call != null) {
              newStatements =
                  mutator.mutateWithoutRenaming(
                      factoryLabel, inlinedFn, call, assignedName, false, false);
              if (assignedName != null) {
                Node newName =
                    IR.var(
                            NodeUtil.newName(
                                compiler,
                                assignedName,
                                fn,
                                expr.getFirstChild().getQualifiedName()))
                        .srcrefTree(fn);
                if (newStatements.hasChildren()
                    && newStatements.getFirstChild().isExprResult()
                    && newStatements.getFirstFirstChild().isAssign()
                    && newStatements.getFirstFirstChild().getFirstChild().isName()
                    && newStatements
                        .getFirstFirstChild()
                        .getFirstChild()
                        .getString()
                        .equals(assignedName)) {
                  newName
                      .getFirstChild()
                      .addChildToFront(
                          newStatements.getFirstFirstChild().getSecondChild().detach());
                  newStatements.getFirstChild().replaceWith(newName);
                } else {
                  newStatements.addChildToFront(newName);
                }
                expr.getSecondChild().replaceWith(newName.getFirstChild().cloneNode());
                newStatements.addChildToBack(expr.getParent().detach());
              }
            }
          }

          Node callRoot = enclosingFnCall.getParent();
          if (callRoot.isNot()) {
            callRoot = callRoot.getParent();
          }
          if (callRoot.isExprResult()) {
            Node callRootParent = callRoot.getParent();
            callRootParent.addChildrenAfter(newStatements.removeChildren(), callRoot);
            callRoot.detach();
            reportNestedScopesChanged(callRootParent);
            compiler.reportChangeToEnclosingScope(callRootParent);
            reportNestedScopesDeleted(enclosingFnCall);
          } else {
            umdPattern.ifRoot.replaceWith(newNode);
            compiler.reportChangeToEnclosingScope(newNode);
            reportNestedScopesDeleted(umdPattern.ifRoot);
          }
        }
      }
      return needsRetraverse;
    }
  }

  private void reportNestedScopesDeleted(Node n) {
    NodeUtil.visitPreOrder(
        n,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isFunction()) {
              compiler.reportFunctionDeleted(n);
            }
          }
        });
  }

  private void reportNestedScopesChanged(Node n) {
    NodeUtil.visitPreOrder(
        n,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isFunction()) {
              compiler.reportChangeToChangeScope(n);
            }
          }
        });
  }

  private static @Nullable UmdPattern findUmdPattern(List<UmdPattern> umdPatterns, Node n) {
    for (UmdPattern umd : umdPatterns) {
      if (umd.ifRoot == n) {
        return umd;
      }
    }
    return null;
  }

  /**
   * Traverse a file and rewrite all references to imported names directly to the targeted module
   * name.
   *
   * <p>If a file is a CommonJS module, rewrite export statements. Typically exports create an alias
   * - the rewriting tries to avoid such aliases.
   */
  private class RewriteModule extends AbstractPostOrderCallback {
    private final boolean allowFullRewrite;
    private final ImmutableCollection<ExportInfo> exports;
    private final List<Node> imports = new ArrayList<>();
    private final List<Node> rewrittenClassExpressions = new ArrayList<>();
    private final List<Node> functionsToHoist = new ArrayList<>();
    private final boolean defaultExportIsConst;

    public RewriteModule(
        boolean allowFullRewrite,
        ImmutableCollection<ExportInfo> exports,
        boolean defaultExportIsConst) {
      this.allowFullRewrite = allowFullRewrite;
      this.exports = exports;
      this.defaultExportIsConst = defaultExportIsConst;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT -> {
          // Class names can't be changed during the middle of a traversal. Unlike functions,
          // the name can be the EMPTY token rather than just a zero length string.
          for (Node clazz : rewrittenClassExpressions) {
            clazz.getFirstChild().replaceWith(IR.empty().srcref(clazz.getFirstChild()));
            t.reportCodeChange();
          }

          CompilerInput ci = compiler.getInput(n.getInputId());
          String moduleName = getModuleName(ci);

          // If a function is the direct module export, move it to the top.
          for (int i = 1; i < functionsToHoist.size(); i++) {
            if (functionsToHoist
                .get(i)
                .getFirstFirstChild()
                .matchesQualifiedName(getBasePropertyImport(moduleName))) {
              Node fncVar = functionsToHoist.get(i);
              functionsToHoist.remove(i);
              functionsToHoist.add(0, fncVar);
              break;
            }
          }

          // Hoist functions in reverse order so that they maintain the same relative
          // order after hoisting.
          for (int i = functionsToHoist.size() - 1; i >= 0; i--) {
            Node functionExpr = functionsToHoist.get(i);
            Node scopeRoot = t.getClosestHoistScopeRoot();
            Node insertionPoint = scopeRoot.getFirstChild();
            if (insertionPoint == null
                || !(insertionPoint.isVar()
                    && insertionPoint.getFirstChild().getString().equals(moduleName))) {
              insertionPoint = null;
            }

            if (insertionPoint == null) {
              if (scopeRoot.getFirstChild() != functionExpr) {
                scopeRoot.addChildToFront(functionExpr.detach());
              }
            } else if (insertionPoint != functionExpr && insertionPoint.getNext() != functionExpr) {
              functionExpr.detach().insertAfter(insertionPoint);
            }
          }

          for (ExportInfo export : exports) {
            visitExport(t, export);
          }

          for (Node require : imports) {
            visitRequireCall(t, require);
          }
        }
        case CALL -> {
          if (isCommonJsImport(n)) {
            imports.add(n);
          }
        }
        case VAR, LET, CONST -> {
          // Multiple declarations need split apart so that they can be refactored into
          // property assignments or removed altogether. Don't split declarations for
          // ES module export calls as it breaks AST and ES modules shouldn't be affected at all
          // by this pass.
          if (n.hasMoreThanOneChild() && !NodeUtil.isAnyFor(parent) && !parent.isExport()) {
            List<Node> vars = splitMultipleDeclarations(n);
            t.reportCodeChange();
            for (Node var : vars) {
              visit(t, var.getFirstChild(), var);
            }
          }

          // UMD Inlining can shadow global variables - these are just removed.
          //
          // var exports = exports;
          if (n.getFirstChild().hasChildren()
              && n.getFirstFirstChild().isName()
              && n.getFirstChild().getString().equals(n.getFirstFirstChild().getString())) {
            n.detach();
            t.reportCodeChange();
            return;
          }
        }
        case NAME -> {
          // If this is a name declaration with multiple names, it will be split apart when
          // the parent is visited and then revisit the children.
          if (NodeUtil.isNameDeclaration(n.getParent())
              && n.getParent().hasMoreThanOneChild()
              && !NodeUtil.isAnyFor(n.getGrandparent())) {
            break;
          }

          String qName = n.getQualifiedName();
          if (qName == null) {
            break;
          }
          final Var nameDeclaration = t.getScope().getVar(qName);
          if (nameDeclaration != null) {
            if (NodeUtil.isLhsByDestructuring(n)) {
              maybeUpdateName(t, n, nameDeclaration);
            } else if (nameDeclaration.getNode() != null
                && Objects.equals(nameDeclaration.getNode().getInputId(), n.getInputId())) {
              // Avoid renaming a shadowed global
              //
              // var angular = angular;  // value is global ref
              Node enclosingDeclaration =
                  NodeUtil.getEnclosingNode(
                      n, (Node node) -> node == nameDeclaration.getNameNode());

              if (enclosingDeclaration == null
                  || enclosingDeclaration == n
                  || nameDeclaration.getScope() != t.getScope()) {
                maybeUpdateName(t, n, nameDeclaration);
              }
            }
            // Replace loose "module" references with an object literal
          } else if (allowFullRewrite
              && n.getString().equals(MODULE)
              && !(n.getParent().isGetProp()
                  || n.getParent().isGetElem()
                  || n.getParent().isTypeOf())) {
            Node objectLit = IR.objectlit().srcref(n);
            n.replaceWith(objectLit);
            t.reportCodeChange(objectLit);
          }
        }
        case GETPROP -> {
          if (n.matchesQualifiedName(MODULE + ".id")
              || n.matchesQualifiedName(MODULE + ".filename")) {
            Var v = t.getScope().getVar(MODULE);
            if (v == null || v.isExtern()) {
              n.replaceWith(IR.string(t.getInput().getPath().toString()).srcref(n));
            }
          } else if (allowFullRewrite
              && n.getFirstChild().isName()
              && n.getFirstChild().getString().equals(MODULE)
              && !n.matchesQualifiedName(MODULE + "." + EXPORTS)) {
            Var v = t.getScope().getVar(MODULE);
            if (v == null || v.isExtern()) {
              n.getFirstChild().replaceWith(IR.objectlit().srcref(n.getFirstChild()));
            }
          }
        }
        case TYPEOF -> {
          if (allowFullRewrite
              && n.getFirstChild().isName()
              && (n.getFirstChild().getString().equals(MODULE)
                  || n.getFirstChild().getString().equals(EXPORTS))) {
            Var v = t.getScope().getVar(n.getFirstChild().getString());
            if (v == null || v.isExtern()) {
              n.replaceWith(IR.string("object"));
            }
          }
        }
        default -> {}
      }

      fixTypeAnnotationsForNode(t, n);
    }

    private void fixTypeAnnotationsForNode(NodeTraversal t, Node n) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node typeNode : info.getTypeNodes()) {
          fixTypeNode(t, typeNode);
        }
      }
    }

    /**
     * Visit require calls. Rewrite require statements to be a direct reference to name of require
     * module. By this point all references to the import alias should have already been renamed.
     */
    private void visitRequireCall(NodeTraversal t, Node require) {
      String moduleName = getImportedModuleName(t, require);
      Node moduleRef =
          NodeUtil.newQName(compiler, getBasePropertyImport(moduleName, require))
              .srcrefTree(require.getSecondChild());
      require.replaceWith(moduleRef);

      t.reportCodeChange();
    }

    /**
     * Visit export statements. Export statements can be either a direct assignment: module.exports
     * = foo or a property assignment: module.exports.foo = foo; exports.foo = foo;
     */
    private void visitExport(NodeTraversal t, ExportInfo export) {
      Node root = getBaseQualifiedNameNode(export.node);
      Node rValue = NodeUtil.getRValueOfLValue(root);

      // For object literal assignments to module.exports, convert them to
      // individual property assignments.
      //
      //     module.exports = { foo: bar};
      //
      // becomes
      //
      //     module.exports = {};
      //     module.exports.foo = bar;
      if (MODULE_EXPORTS.matches(root)) {
        if (rValue != null
            && rValue.isObjectLit()
            && root.getParent().isAssign()
            && root.getGrandparent().isExprResult()) {
          if (expandObjectLitAssignment(t, root, export.scope)) {
            return;
          }
        }
      }

      String moduleName = getModuleName(t.getInput());
      Var moduleInitialization = t.getScope().getVar(moduleName);

      // If this is an assignment to module.exports or exports, renaming
      // has already handled this case. Remove the export.
      Var rValueVar = null;
      if (rValue != null && rValue.isQualifiedName()) {
        rValueVar = export.scope.getVar(rValue.getQualifiedName());
      }

      if (root.getParent().isAssign()
          && root.getGrandparent().isExprResult()
          && (root.getNext() != null && (root.getNext().isName() || root.getNext().isGetProp()))
          && root.getGrandparent().isExprResult()
          && rValueVar != null
          && (NodeUtil.getEnclosingScript(rValueVar.getNameNode()) == null
              || (rValueVar.getNameNode().hasParent() && !rValueVar.isParam()))
          && export.isInSupportedScope
          && (rValueVar.getNameNode().getParent() == null
              || !NodeUtil.isLhsByDestructuring(rValueVar.getNameNode()))) {
        root.getGrandparent().detach();
        t.reportCodeChange();
        return;
      }

      moduleName = moduleName + "." + EXPORT_PROPERTY_NAME;

      Node updatedExport =
          NodeUtil.newQName(compiler, moduleName, export.node, export.node.getQualifiedName());
      updatedExport.putBooleanProp(Node.MODULE_EXPORT, true);
      boolean exportIsConst =
          defaultExportIsConst
              && updatedExport.matchesQualifiedName(
                  getBasePropertyImport(getModuleName(t.getInput())))
              && root == export.node
              && NodeUtil.isLValue(export.node);

      Node changeScope = null;

      if (MODULE_EXPORTS.matches(root)
          && rValue != null
          && export.scope.getVar("module.exports") == null
          && root.getParent().isAssign()
          && root.getGrandparent().isExprResult()
          && moduleInitialization == null) {
        // Rewrite "module.exports = foo;" to "var moduleName = {default: foo};"
        Node parent = root.getParent();
        Node exportName = IR.exprResult(IR.assign(updatedExport, rValue.detach()));
        if (exportIsConst) {
          JSDocInfo.Builder info = JSDocInfo.builder();
          info.recordConstancy();
          exportName.getFirstChild().setJSDocInfo(info.build());
        }
        parent.getParent().replaceWith(exportName.srcrefTree(root.getParent()));
        changeScope = ChangeTracker.getEnclosingChangeScopeRoot(parent);
      } else if (root.getNext() != null
          && root.getNext().isName()
          && rValueVar != null
          && rValueVar.isGlobal()
          && export.isInSupportedScope
          && (rValueVar.getNameNode().getParent() == null
              || !NodeUtil.isLhsByDestructuring(rValueVar.getNameNode()))) {
        // This is a where a module export assignment is used in a complex expression.
        // Before: `SOME_VALUE !== undefined && module.exports = SOME_VALUE`
        // After: `SOME_VALUE !== undefined && module$name`
        Node parent = root.getParent();
        root.detach();
        parent.replaceWith(root);
        if (root == export.node) {
          root = updatedExport;
        }
        export.node.replaceWith(updatedExport);
        changeScope = ChangeTracker.getEnclosingChangeScopeRoot(root);
      } else {
        // Other references to "module.exports" are just replaced with the module name.
        export.node.replaceWith(updatedExport);
        if (updatedExport.getParent().isAssign() && exportIsConst) {
          JSDocInfo.Builder infoBuilder =
              JSDocInfo.Builder.maybeCopyFrom(updatedExport.getParent().getJSDocInfo());
          infoBuilder.recordConstancy();
          updatedExport.getParent().setJSDocInfo(infoBuilder.build());
        }

        changeScope = ChangeTracker.getEnclosingChangeScopeRoot(updatedExport);
      }

      if (changeScope != null) {
        compiler.reportChangeToChangeScope(changeScope);
      }
    }

    /**
     * Since CommonJS modules may have only a single export, it's common to see the export be an
     * object pattern. We want to expand this to individual property assignments. If any individual
     * property assignment has been renamed, it will be removed.
     *
     * <p>We need to keep assignments which aren't names
     *
     * <p>module.exports = { foo: bar, baz: function() {} }
     *
     * <p>becomes
     *
     * <p>module.exports.foo = bar; // removed later module.exports.baz = function() {};
     */
    private boolean expandObjectLitAssignment(NodeTraversal t, Node export, Scope scope) {
      checkState(export.getParent().isAssign());
      Node insertionRef = export.getGrandparent();
      checkState(insertionRef.isExprResult());
      Node insertionParent = insertionRef.getParent();
      checkNotNull(insertionParent);

      Node rValue = NodeUtil.getRValueOfLValue(export);
      Node key = rValue.getFirstChild();

      boolean removedNodes = false;
      while (key != null) {
        if ((!key.isStringKey() || key.isQuotedStringKey()) && !key.isMemberFunctionDef()) {
          key = key.getNext();
          continue;
        }

        Node lhs = IR.getprop(export.cloneTree(), key.getString());
        Node value = null;
        if (key.isStringKey()) {
          value = key.removeFirstChild();
        } else if (key.isMemberFunctionDef()) {
          value = key.removeFirstChild();
        }

        Node expr = IR.exprResult(IR.assign(lhs, value)).srcrefTreeIfMissing(key);
        expr.insertAfter(insertionRef);
        ExportInfo newExport = new ExportInfo(lhs.getFirstChild(), scope);
        visitExport(t, newExport);

        // Export statements can be removed in visitExport
        if (expr != null && expr.hasParent()) {
          insertionRef = expr;
        }

        Node currentKey = key;
        key = key.getNext();
        currentKey.detach();
        removedNodes = true;
      }

      if (!rValue.hasChildren()) {
        export.getGrandparent().detach();
        return true;
      }

      if (removedNodes) {
        t.reportCodeChange(rValue);
      }
      return false;
    }

    /**
     * Given a name reference, check to see if it needs renamed.
     *
     * <p>We handle 3 main cases: 1. References to an import alias. These are replaced with a direct
     * reference to the imported module. 2. Names which are exported. These are rewritten to be the
     * export assignment directly. 3. Global names: If a name is global to the script, add a suffix
     * so it doesn't collide with any other global.
     *
     * <p>Rewriting case 1 is safe to perform on all files. Cases 2 and 3 can only be done if this
     * file is a commonjs module.
     */
    private void maybeUpdateName(NodeTraversal t, Node n, Var var) {
      checkNotNull(var);
      checkState(n.isName() || n.isGetProp());
      checkState(n.hasParent());
      String importedModuleName = getModuleImportName(t, var.getNode());
      String name = n.getQualifiedName();

      // Check if the name refers to a alias for a require('foo') import.
      if (importedModuleName != null && n != var.getNode()) {
        // Reference the imported name directly, rather than the alias
        updateNameReference(t, n, name, importedModuleName, false, false);

      } else if (allowFullRewrite) {
        String exportedName = getExportedName(t, n, var);

        // We need to exclude the alias created by the require import. We assume dead
        // code elimination will remove these later.
        if ((n != var.getNode() || n.getParent().isClass())
            && exportedName == null
            && (var == null
                || var.getNameNode().getParent() == null
                || !NodeUtil.isLhsByDestructuring(var.getNameNode()))) {
          // The name is actually the export reference itself.
          // This will be handled later by visitExports.
          if (n.getParent().isClass() && n.getParent().getFirstChild() == n) {
            rewrittenClassExpressions.add(n.getParent());
          }

          return;
        }

        // Check if the name is used as an export
        if (importedModuleName == null
            && exportedName != null
            && !exportedName.equals(name)
            && !var.isParam()
            && (var.getNameNode().getParent() == null
                || !NodeUtil.isLhsByDestructuring(var.getNameNode()))) {
          boolean exportPropIsConst =
              defaultExportIsConst
                  && getBasePropertyImport(getModuleName(t.getInput())).equals(exportedName)
                  && getBaseQualifiedNameNode(n) == n
                  && NodeUtil.isLValue(n);
          updateNameReference(t, n, name, exportedName, true, exportPropIsConst);

          // If it's a global name, rename it to prevent conflicts with other scripts
        } else if (var.isGlobal()) {
          String currentModuleName = getModuleName(t.getInput());

          if (currentModuleName.equals(name)) {
            return;
          }

          // refs to 'exports' are handled separately.
          if (EXPORTS.equals(name)) {
            return;
          }

          // closure_test_suite looks for test*() functions
          if (compiler.getOptions().shouldExportTestFunctions()
              && currentModuleName.startsWith("test")) {
            return;
          }

          String newName = name + "$$" + currentModuleName;
          updateNameReference(t, n, name, newName, false, false);
        }
      }
    }

    /**
     * @param nameRef the qualified name node
     * @param originalName of nameRef
     * @param newName for nameRef
     * @param requireFunctionExpressions Whether named class or functions should be rewritten to
     *     variable assignments
     */
    private void updateNameReference(
        NodeTraversal t,
        Node nameRef,
        String originalName,
        String newName,
        boolean requireFunctionExpressions,
        boolean qualifiedNameIsConst) {
      Node parent = nameRef.getParent();
      checkNotNull(parent);
      checkNotNull(newName);
      boolean newNameIsQualified = newName.indexOf('.') >= 0;
      boolean newNameIsModuleExport =
          newName.equals(getBasePropertyImport(getModuleName(t.getInput())));

      Var newNameDeclaration = t.getScope().getVar(newName);

      switch (parent.getToken()) {
        case CLASS -> {
          if (parent.getIndexOfChild(nameRef) == 0
              && (newNameIsQualified || requireFunctionExpressions)) {
            // Refactor a named class to a class expression
            // We can't remove the class name during a traversal, so save it for later
            rewrittenClassExpressions.add(parent);

            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            if (newNameIsModuleExport) {
              newNameRef.putBooleanProp(Node.MODULE_EXPORT, true);
            }

            Node expr;
            if (!newNameIsQualified && newNameDeclaration == null) {
              expr = IR.let(newNameRef, IR.nullNode()).srcrefTreeIfMissing(nameRef);
            } else {
              expr =
                  IR.exprResult(IR.assign(newNameRef, IR.nullNode())).srcrefTreeIfMissing(nameRef);
              JSDocInfo.Builder info = JSDocInfo.Builder.maybeCopyFrom(parent.getJSDocInfo());
              parent.setJSDocInfo(null);
              if (qualifiedNameIsConst) {
                info.recordConstancy();
              }
              expr.getFirstChild().setJSDocInfo(info.build());
              fixTypeAnnotationsForNode(t, expr.getFirstChild());
            }
            parent.replaceWith(expr);
            if (expr.isLet()) {
              expr.getFirstFirstChild().replaceWith(parent);
            } else {
              expr.getFirstChild().getSecondChild().replaceWith(parent);
            }
          } else if (parent.getIndexOfChild(nameRef) == 1) {
            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            if (newNameIsModuleExport) {
              newNameRef.putBooleanProp(Node.MODULE_EXPORT, true);
            }
            nameRef.replaceWith(newNameRef);
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
        }
        case FUNCTION -> {
          if (newNameIsQualified || requireFunctionExpressions) {
            // Refactor a named function to a function expression
            if (NodeUtil.isFunctionExpression(parent)) {
              // Don't refactor if the parent is a named function expression.
              // e.g. var foo = function foo() {};
              return;
            }
            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            if (newNameIsModuleExport) {
              newNameRef.putBooleanProp(Node.MODULE_EXPORT, true);
            }
            nameRef.setString("");

            Node expr;
            if (!newNameIsQualified && newNameDeclaration == null) {
              expr = IR.var(newNameRef, IR.nullNode()).srcrefTreeIfMissing(nameRef);
            } else {
              expr =
                  IR.exprResult(IR.assign(newNameRef, IR.nullNode())).srcrefTreeIfMissing(nameRef);
            }
            parent.replaceWith(expr);
            if (expr.isVar()) {
              expr.getFirstFirstChild().replaceWith(parent);
            } else {
              expr.getFirstChild().getSecondChild().replaceWith(parent);
              JSDocInfo.Builder info = JSDocInfo.Builder.maybeCopyFrom(parent.getJSDocInfo());
              parent.setJSDocInfo(null);
              if (qualifiedNameIsConst) {
                info.recordConstancy();
              }
              expr.getFirstChild().setJSDocInfo(info.build());
              fixTypeAnnotationsForNode(t, expr.getFirstChild());
            }
            functionsToHoist.add(expr);
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
        }
        case VAR, LET, CONST -> {
          // Multiple declaration - needs split apart.
          if (parent.hasMoreThanOneChild() && !NodeUtil.isAnyFor(parent.getParent())) {
            splitMultipleDeclarations(parent);
            parent = nameRef.getParent();
            newNameDeclaration = t.getScope().getVar(newName);
          }

          if (newNameIsQualified) {
            // Var declarations without initialization can simply
            // be removed if they are being converted to a property.
            if (!nameRef.hasChildren() && parent.getJSDocInfo() == null) {
              parent.detach();
              break;
            }

            // Refactor a var declaration to a getprop assignment
            Node getProp = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            if (newNameIsModuleExport) {
              getProp.putBooleanProp(Node.MODULE_EXPORT, true);
            }
            JSDocInfo info = parent.getJSDocInfo();
            parent.setJSDocInfo(null);
            if (nameRef.hasChildren()) {
              Node assign = IR.assign(getProp, nameRef.removeFirstChild());
              assign.setJSDocInfo(info);
              Node expr = IR.exprResult(assign).srcrefTreeIfMissing(nameRef);
              parent.replaceWith(expr);
              JSDocInfo.Builder infoBuilder = JSDocInfo.Builder.maybeCopyFrom(info);
              parent.setJSDocInfo(null);
              if (qualifiedNameIsConst) {
                infoBuilder.recordConstancy();
              }
              assign.setJSDocInfo(infoBuilder.build());
              fixTypeAnnotationsForNode(t, assign);
            } else {
              getProp.setJSDocInfo(info);
              parent.replaceWith(IR.exprResult(getProp).srcref(getProp));
            }
          } else if (newNameDeclaration != null && newNameDeclaration.getNameNode() != nameRef) {
            // Variable is already defined. Convert this to an assignment.
            // If the variable declaration has no initialization, we simply
            // remove the node. This can occur when the variable which is exported
            // is declared in an outer scope but assigned in an inner one.
            if (!nameRef.hasChildren()) {
              parent.detach();
              break;
            }

            Node name = NodeUtil.newName(compiler, newName, nameRef, originalName);
            Node assign = IR.assign(name, nameRef.removeFirstChild());
            JSDocInfo info = parent.getJSDocInfo();
            if (info != null) {
              parent.setJSDocInfo(null);
              assign.setJSDocInfo(info);
            }

            parent.replaceWith(IR.exprResult(assign).srcrefTree(nameRef));
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
        }
        default -> {
          // Whenever possible, reuse the existing reference
          if (!newNameIsQualified && nameRef.isName()) {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          } else {
            Node name =
                newNameIsQualified
                    ? NodeUtil.newQName(compiler, newName, nameRef, originalName)
                    : NodeUtil.newName(compiler, newName, nameRef, originalName);

            if (newNameIsModuleExport) {
              name.putBooleanProp(Node.MODULE_EXPORT, true);
            }
            JSDocInfo info = nameRef.getJSDocInfo();
            if (info != null) {
              nameRef.setJSDocInfo(null);
              name.setJSDocInfo(info);
            }
            name.srcrefTree(nameRef);
            nameRef.replaceWith(name);
            if (nameRef.hasChildren()) {
              name.addChildrenToFront(nameRef.removeChildren());
            }
          }
        }
      }

      t.reportCodeChange();
    }

    /**
     * Determine whether the given name Node n is referenced in an export
     *
     * @return string - If the name is not used in an export, return it's own name If the name node
     *     is actually the export target itself, return null;
     */
    private @Nullable String getExportedName(NodeTraversal t, Node n, Var var) {
      if (var == null || !Objects.equals(var.getNode().getInputId(), n.getInputId())) {
        return n.getQualifiedName();
      }

      String baseExportName = getBasePropertyImport(getModuleName(t.getInput()));

      for (ExportInfo export : this.exports) {
        Node exportBase = getBaseQualifiedNameNode(export.node);
        Node exportRValue = NodeUtil.getRValueOfLValue(exportBase);

        if (exportRValue == null) {
          continue;
        }

        Node exportedName = getExportedNameNode(export);
        // We don't want to handle the export itself
        if (export.isInSupportedScope
            && (exportRValue == n
                || ((NodeUtil.isClassExpression(exportRValue)
                        || NodeUtil.isFunctionExpression(exportRValue))
                    && exportedName == n))) {
          return null;
        }

        String exportBaseQName = exportBase.getQualifiedName();

        if (exportRValue.isObjectLit()) {
          if (!"module.exports".equals(exportBaseQName)) {
            return n.getQualifiedName();
          }

          Node key = exportRValue.getFirstChild();
          boolean keyIsExport = false;
          while (key != null) {
            if (key.isStringKey()
                && !key.isQuotedStringKey()
                && NodeUtil.isValidPropertyName(
                    compiler.getOptions().getLanguageIn().toFeatureSet(), key.getString())) {
              if (key.getFirstChild().isQualifiedName()) {
                if (key.getFirstChild() == n) {
                  return null;
                }

                Var valVar = t.getScope().getVar(key.getFirstChild().getQualifiedName());
                if (valVar != null && valVar.getNameNode() == var.getNameNode()) {
                  keyIsExport = true;
                  break;
                }
              }
            }

            key = key.getNext();
          }
          if (key != null && keyIsExport) {
            if (export.isInSupportedScope) {
              return baseExportName + "." + key.getString();
            } else {
              return n.getQualifiedName();
            }
          }
        } else {
          if (!export.isInSupportedScope) {
            return n.getQualifiedName();
          }
          if (var.getNameNode() == exportedName) {
            String exportPrefix;
            if (exportBaseQName.startsWith(MODULE)) {
              exportPrefix = MODULE + "." + EXPORTS;
            } else {
              exportPrefix = EXPORTS;
            }

            if (exportBaseQName.length() == exportPrefix.length()) {
              return baseExportName;
            }

            return baseExportName + exportBaseQName.substring(exportPrefix.length());
          }
        }
      }
      return n.getQualifiedName();
    }

    private @Nullable Node getExportedNameNode(ExportInfo info) {
      Node qNameBase = getBaseQualifiedNameNode(info.node);
      Node rValue = NodeUtil.getRValueOfLValue(qNameBase);

      if (rValue == null) {
        return null;
      }

      if (NodeUtil.isFunctionExpression(rValue)) {
        return rValue.getFirstChild();
      }

      if (NodeUtil.isClassExpression(rValue) && rValue.getFirstChild() == qNameBase) {
        return rValue.getFirstChild();
      }

      if (!rValue.isName()) {
        return null;
      }

      Var var = info.scope.getVar(rValue.getString());
      if (var == null) {
        return null;
      }

      return var.getNameNode();
    }

    /**
     * Determine if the given Node n is an alias created by a module import.
     *
     * @return null if it's not an alias or the imported module name
     */
    private @Nullable String getModuleImportName(NodeTraversal t, Node n) {
      Node rValue = null;
      String propSuffix = "";
      Node parent = n.getParent();
      if (parent != null && parent.isStringKey()) {
        Node grandparent = parent.getParent();
        if (grandparent.isObjectPattern() && grandparent.getParent().isDestructuringLhs()) {
          rValue = grandparent.getNext();
          propSuffix = "." + parent.getString();
        }
      }
      if (propSuffix.isEmpty() && parent != null) {
        rValue = NodeUtil.getRValueOfLValue(n);
      }

      if (rValue == null) {
        return null;
      }

      if (rValue.isCall() && isCommonJsImport(rValue)) {
        return getBasePropertyImport(getImportedModuleName(t, rValue), rValue) + propSuffix;
      } else if (rValue.isGetProp() && isCommonJsImport(rValue.getFirstChild())) {
        // var foo = require('bar').foo;
        String importName =
            getBasePropertyImport(
                getImportedModuleName(t, rValue.getFirstChild()), rValue.getFirstChild());

        String suffix = rValue.getString();

        return importName + "." + suffix + propSuffix;
      }

      return null;
    }

    /**
     * Update any type references in JSDoc annotations to account for all the rewriting we've done.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isStringLit()) {
        String name = typeNode.getString();
        // Type nodes can be module paths.
        if (ModuleLoader.isPathIdentifier(name)) {
          int lastSlash = name.lastIndexOf('/');
          int endIndex = name.indexOf('.', lastSlash);
          String localTypeName = null;
          if (endIndex == -1) {
            endIndex = name.length();
          } else {
            localTypeName = name.substring(endIndex);
          }

          String moduleName = name.substring(0, endIndex);
          String globalModuleName = getImportedModuleName(t, typeNode, moduleName);
          String baseImportProperty = getBasePropertyImport(globalModuleName);
          typeNode.setString(
              localTypeName == null ? baseImportProperty : baseImportProperty + localTypeName);

        } else {
          // A type node can be a getprop. Any portion of the getprop
          // can be either an import alias or export alias. Check each
          // segment.
          boolean wasRewritten = false;
          int endIndex = -1;
          while (endIndex < name.length()) {
            endIndex = name.indexOf('.', endIndex + 1);
            if (endIndex == -1) {
              endIndex = name.length();
            }
            String baseName = name.substring(0, endIndex);
            String suffix = endIndex < name.length() ? name.substring(endIndex) : "";
            Var typeDeclaration = t.getScope().getVar(baseName);

            // Make sure we can find a variable declaration (and it's in this file)
            if (typeDeclaration != null && typeDeclaration.getNode() != null
                && Objects.equals(typeDeclaration.getNode().getInputId(), typeNode.getInputId())) {
              String importedModuleName = getModuleImportName(t, typeDeclaration.getNode());

              // If the name is an import alias, rewrite it to be a reference to the
              // module name directly
              if (importedModuleName != null) {
                typeNode.setString(importedModuleName + suffix);
                typeNode.setOriginalName(name);
                wasRewritten = true;
                break;
              } else if (this.allowFullRewrite) {
                // Names referenced in export statements can only be rewritten in
                // commonjs modules.
                String exportedName = getExportedName(t, typeNode, typeDeclaration);
                if (exportedName != null && !exportedName.equals(name)) {
                  typeNode.setString(exportedName + suffix);
                  typeNode.setOriginalName(name);
                  wasRewritten = true;
                  break;
                }
              }
            }
          }

          // If the name was neither an import alias or referenced in an export,
          // We still may need to rename it if it's global
          if (!wasRewritten && this.allowFullRewrite) {
            endIndex = name.indexOf('.');
            if (endIndex == -1) {
              endIndex = name.length();
            }
            String baseName = name.substring(0, endIndex);
            Var typeDeclaration = t.getScope().getVar(baseName);
            if (typeDeclaration != null && typeDeclaration.isGlobal()) {
              String moduleName = getModuleName(t.getInput());
              String newName = baseName + "$$" + moduleName;
              if (endIndex < name.length()) {
                newName += name.substring(endIndex);
              }

              typeNode.setString(newName);
              typeNode.setOriginalName(name);
            }
          }
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(t, child);
      }
    }

    private List<Node> splitMultipleDeclarations(Node var) {
      checkState(NodeUtil.isNameDeclaration(var));
      List<Node> vars = new ArrayList<>();
      JSDocInfo info = var.getJSDocInfo();
      while (var.getSecondChild() != null) {
        Node newVar = new Node(var.getToken(), var.removeFirstChild());

        if (info != null) {
          newVar.setJSDocInfo(info.clone());
        }

        newVar.srcref(var);
        newVar.insertBefore(var);
        vars.add(newVar);
      }
      vars.add(var);
      return vars;
    }
  }
}
