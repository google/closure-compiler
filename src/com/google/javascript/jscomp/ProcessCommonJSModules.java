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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
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
  private static final String REQUIRE = "require";

  public static final DiagnosticType UNKNOWN_REQUIRE_ENSURE =
      DiagnosticType.warning(
          "JSC_COMMONJS_UNKNOWN_REQUIRE_ENSURE_ERROR", "Unrecognized require.ensure call: {0}");

  public static final DiagnosticType SUSPICIOUS_EXPORTS_ASSIGNMENT =
      DiagnosticType.warning(
          "JSC_COMMONJS_SUSPICIOUS_EXPORTS_ASSIGNMENT",
          "Suspicious re-assignment of \"exports\" variable."
              + " Did you actually intend to export something?");

  private final Compiler compiler;

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to
   * rewrite CommonJS modules to a concatenable form.
   *
   * @param compiler The compiler
   */
  public ProcessCommonJSModules(Compiler compiler) {
    this.compiler = compiler;
  }


  /**
   * Module rewriting is done a on per-file basis prior to main compilation. The pass must handle
   * ES6+ syntax and the root node for each file is a SCRIPT - not the typical jsRoot of other
   * passes.
   */
  @Override
  public void process(Node externs, Node root) {
    process(externs, root, false);
  }

  public void process(Node externs, Node root, boolean forceModuleDetection) {
    checkState(root.isScript());
    FindImportsAndExports finder = new FindImportsAndExports();
    NodeTraversal.traverseEs6(compiler, root, finder);

    ImmutableList.Builder<ExportInfo> exports = ImmutableList.builder();
    if (finder.isCommonJsModule() || forceModuleDetection) {
      finder.reportModuleErrors();

      if (!finder.umdPatterns.isEmpty()) {
        boolean needsRetraverse = finder.replaceUmdPatterns();

        if (!needsRetraverse) {
          needsRetraverse = removeIIFEWrapper(root);
        }

        // Inlining functions rewrites vars. We need to re-traverse
        // to get the new references.
        if (needsRetraverse) {
          finder = new FindImportsAndExports();
          NodeTraversal.traverseEs6(compiler, root, finder);
        }
      }

      //UMD pattern replacement can leave detached export references - don't include those
      for (ExportInfo export : finder.getModuleExports()) {
        if (NodeUtil.getEnclosingScript(export.node) != null) {
          exports.add(export);
        }
      }
      for (ExportInfo export : finder.getExports()) {
        if (NodeUtil.getEnclosingScript(export.node) != null) {
          exports.add(export);
        }
      }

      finder.initializeModule();
    }

    NodeTraversal.traverseEs6(
        compiler,
        root,
        new RewriteModule(finder.isCommonJsModule() || forceModuleDetection, exports.build()));
  }

  /**
   * Recognize if a node is a module import. We recognize two forms:
   *
   *  - require("something");
   *  - __webpack_require__(4); // only when the module resolution is WEBPACK
   */
  public static boolean isCommonJsImport(Node requireCall) {
    if (requireCall.isCall() && requireCall.hasTwoChildren()) {
      if (requireCall.getFirstChild().matchesQualifiedName(REQUIRE)
          && requireCall.getSecondChild().isString()) {
        return true;
      }
    }
    return false;
  }

  public static String getCommonJsImportPath(Node requireCall) {
    return requireCall.getSecondChild().getString();
  }

  /**
   * Recognize if a node is a module export. We recognize several forms:
   *
   *  - module.exports = something;
   *  - module.exports.something = something;
   *  - exports.something = something;
   *  - __webpack_exports__["something"] = something; // only when the module resolution is WEBPACK
   *
   * <p>In addition, we only recognize an export if the base export object is not defined or is
   * defined in externs.
   */
  public static boolean isCommonJsExport(NodeTraversal t, Node export) {
    if (export.matchesQualifiedName(MODULE + "." + EXPORTS)) {
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

  /**
   * Information on a Universal Module Definition A UMD is an IF statement and a reference to which
   * branch contains the commonjs export
   */
  static class UmdPattern {
    final Node ifRoot;
    final Node activeBranch;

    UmdPattern(Node ifRoot, Node activeBranch) {
      this.ifRoot = ifRoot;
      this.activeBranch = activeBranch;
    }
  }

  static class ExportInfo {
    final Node node;
    final Scope scope;

    ExportInfo(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;
    }
  }

  private Node getBaseQualifiedNameNode(Node n) {
    Node refParent = n;
    while (refParent.getParent() != null && refParent.getParent().isQualifiedName()) {
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
    if (n != null && n.getFirstChild() != null && n.getFirstChild().isNot()) {
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
        && call.getFirstFirstChild().getNext().isString()
        && call.getFirstFirstChild().getNext().getString().equals("call")) {
      fnc = call.getFirstFirstChild();

      // We only support explicitly binding "this" to the parent "this"
      if (!(call.getSecondChild() != null && call.getSecondChild().isThis())) {
        return false;
      }
    } else {
      return false;
    }

    if (NodeUtil.isVarArgsFunction(fnc)) {
      return false;
    }

    CompilerInput ci = compiler.getInput(root.getInputId());
    ModulePath modulePath = ci.getPath();
    if (modulePath == null) {
      return false;
    }

    String iifeLabel = modulePath.toModuleName() + "_iifeWrapper";

    FunctionToBlockMutator mutator =
        new FunctionToBlockMutator(compiler, compiler.getUniqueNameIdSupplier());
    Node block = mutator.unwrapIifeInModule(iifeLabel, fnc, call);
    root.removeChildren();
    root.addChildrenToFront(block.removeChildren());
    compiler.reportChangeToEnclosingScope(root);

    return true;
  }

  /**
   * Traverse the script. Find all references to CommonJS require (import) and module.exports or
   * export statements. Rewrites any require calls to reference the rewritten module name.
   */
  class FindImportsAndExports implements NodeTraversal.Callback {
    private boolean hasGoogProvideOrModule = false;
    private Node script = null;

    boolean isCommonJsModule() {
      return (exports.size() > 0 || moduleExports.size() > 0)
          && !hasGoogProvideOrModule;
    }

    List<UmdPattern> umdPatterns = new ArrayList<>();
    List<ExportInfo> moduleExports = new ArrayList<>();
    List<ExportInfo> exports = new ArrayList<>();
    List<JSError> errors = new ArrayList<>();

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
      if (t.inGlobalScope()) {
        // Check for goog.provide or goog.module statements
        if (parent == null
            || NodeUtil.isControlStructure(parent)
            || NodeUtil.isStatementBlock(parent)) {
          if (n.isExprResult()) {
            Node maybeGetProp = n.getFirstFirstChild();
            if (maybeGetProp != null
                && (maybeGetProp.matchesQualifiedName("goog.provide")
                    || maybeGetProp.matchesQualifiedName("goog.module"))) {
              hasGoogProvideOrModule = true;
            }
          }
        }
      }

      // Find require.ensure calls
      if (n.isCall() && n.getFirstChild().matchesQualifiedName("require.ensure")) {
        visitRequireEnsureCall(t, n);
      }

      if (n.matchesQualifiedName(MODULE + "." + EXPORTS)) {
        if (ProcessCommonJSModules.isCommonJsExport(t, n)) {
          moduleExports.add(new ExportInfo(n, t.getScope()));

          // If the module.exports statement is nested in the then branch of an if statement,
          // assume the if statement is an UMD pattern with a common js export in the then branch
          // This seems fragile but has worked well for a long time.
          // TODO(ChadKillingsworth): Discover if there is a better way to detect these.
          Node ifAncestor = getOutermostIfAncestor(parent);
          if (ifAncestor != null && !umdPatternsContains(umdPatterns, ifAncestor)) {
            umdPatterns.add(new UmdPattern(ifAncestor, ifAncestor.getSecondChild()));
          }
        }
      } else if (n.matchesQualifiedName("define.amd")) {
        // If a define.amd statement is nested in the then branch of an if statement,
        // assume the if statement is an UMD pattern with a common js export
        // in the else branch
        // This seems fragile but has worked well for a long time.
        // TODO(ChadKillingsworth): Discover if there is a better way to detect these.
        Node ifAncestor = getOutermostIfAncestor(parent);
        if (ifAncestor != null && !umdPatternsContains(umdPatterns, ifAncestor)) {
          umdPatterns.add(new UmdPattern(ifAncestor, ifAncestor.getChildAtIndex(2)));
        }
      }

      if (n.isName() && EXPORTS.equals(n.getString())) {
        Var v = t.getScope().getVar(EXPORTS);
        if (v == null || v.isGlobal()) {
          Node qNameRoot = getBaseQualifiedNameNode(n);
          if (qNameRoot != null
              && qNameRoot.matchesQualifiedName(EXPORTS)
              && NodeUtil.isLValue(qNameRoot)) {
            if (!this.hasGoogProvideOrModule) {
              errors.add(t.makeError(qNameRoot, SUSPICIOUS_EXPORTS_ASSIGNMENT));
            }
          } else {
            exports.add(new ExportInfo(n, t.getScope()));

            // If the exports statement is nested in the then branch of an if statement,
            // assume the if statement is an UMD pattern with a common js export in the then branch
            // This seems fragile but has worked well for a long time.
            // TODO(ChadKillingsworth): Discover if there is a better way to detect these.
            Node ifAncestor = getOutermostIfAncestor(parent);
            if (ifAncestor != null && !umdPatternsContains(umdPatterns, ifAncestor)) {
              umdPatterns.add(new UmdPattern(ifAncestor, ifAncestor.getSecondChild()));
            }
          }
        }
      }

      if (ProcessCommonJSModules.isCommonJsImport(n)) {
        visitRequireCall(t, n, parent);
      }
    }

    /** Visit require calls.  */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String requireName = ProcessCommonJSModules.getCommonJsImportPath(require);
      ModulePath modulePath =
          t.getInput()
              .getPath()
              .resolveJsModule(
                  requireName,
                  require.getSourceFileName(),
                  require.getLineno(),
                  require.getCharno());
      if (modulePath == null) {
        // The module loader will issue an error
        return;
      }

      // When require("name") is used as a standalone statement (the result isn't used)
      // it indicates that a module is being loaded for the side effects it produces.
      // In this case the require statement should just be removed as the dependency
      // sorting will insert the file for us.
      if (!NodeUtil.isExpressionResultUsed(require)
          && parent.isExprResult()
          && NodeUtil.isStatementBlock(parent.getParent())) {
        parent.detach();
      }
    }

    /**
     * Visit require.ensure calls. Replace the call with an IIFE. Require.ensure must always be of
     * the form:
     *
     * <p>require.ensure(['module1', ...], function(require) {})
     */
    private void visitRequireEnsureCall(NodeTraversal t, Node call) {
      if (call.getChildCount() != 3) {
        compiler.report(
            t.makeError(
                call,
                UNKNOWN_REQUIRE_ENSURE,
                "Expected the function to have 2 arguments but instead found {0}",
                "" + call.getChildCount()));
        return;
      }

      Node dependencies = call.getSecondChild();
      if (!dependencies.isArrayLit()) {
        compiler.report(
            t.makeError(
                dependencies,
                UNKNOWN_REQUIRE_ENSURE,
                "The first argument must be an array literal of string literals."));
        return;
      }

      for (Node dep : dependencies.children()) {
        if (!dep.isString()) {
          compiler.report(
              t.makeError(
                  dep,
                  UNKNOWN_REQUIRE_ENSURE,
                  "The first argument must be an array literal of string literals."));
          return;
        }
      }
      Node callback = dependencies.getNext();
      if (!(callback.isFunction()
          && callback.getSecondChild().getChildCount() == 1
          && callback.getSecondChild().getFirstChild().isName()
          && "require".equals(callback.getSecondChild().getFirstChild().getString()))) {
        compiler.report(
            t.makeError(
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
     */
    void initializeModule() {
      CompilerInput ci = compiler.getInput(this.script.getInputId());
      ModulePath modulePath = ci.getPath();
      if (modulePath == null) {
        return;
      }

      String moduleName = modulePath.toModuleName();

      int directAssignmentsAtTopLevel = 0;
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
            Node expr = export.node.getParent().getParent();
            if (expr.isExprResult()
                && (NodeUtil.isTopLevel(expr.getParent())
                || (expr.getParent().isNormalBlock()
                    && NodeUtil.isTopLevel(expr.getGrandparent())))) {
              directAssignmentsAtTopLevel++;
            }
          }
        }
      }

      if (directAssignmentsAtTopLevel > 1
          || (directAssignmentsAtTopLevel == 0 && directAssignments > 0)
          || directAssignments == 0) {
        int totalExportStatements = this.moduleExports.size() + this.exports.size();
        Node initModule = IR.var(IR.name(moduleName));
        if (directAssignments < totalExportStatements) {
          initModule.getFirstChild().addChildToFront(IR.objectlit());

          // If all the assignments are property exports, initialize the
          // module as a namespace
          if (directAssignments == 0) {
            JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
            builder.recordConstancy();
            initModule.setJSDocInfo(builder.build());
          }
        }
        initModule.useSourceInfoIfMissingFromForTree(this.script);

        this.script.addChildToFront(initModule);
        compiler.reportChangeToEnclosingScope(this.script);
      }
    }

    /** Find the outermost if node ancestor for a node without leaving the function scope */
    private Node getOutermostIfAncestor(Node n) {
      if (n == null || NodeUtil.isTopLevel(n) || n.isFunction()) {
        return null;
      }
      Node parent = n.getParent();
      if (parent == null) {
        return null;
      }

      // When walking up ternary operations (hook), don't check if parent is the condition,
      // because one ternary operation can be then/else branch of another.
      if ((parent.isIf() && parent.getFirstChild() == n) || parent.isHook()) {
        Node outerIf = getOutermostIfAncestor(parent);
        if (outerIf != null) {
          return outerIf;
        }

        return parent;
      }

      return getOutermostIfAncestor(parent);
    }

    /** Remove a Universal Module Definition and leave just the commonjs export statement */
    boolean replaceUmdPatterns() {
      boolean needsRetraverse = false;
      Node changeScope;
      for (UmdPattern umdPattern : umdPatterns) {
        Node parent = umdPattern.ifRoot.getParent();
        Node newNode = umdPattern.activeBranch;

        if (newNode == null) {
          parent.removeChild(umdPattern.ifRoot);
          compiler.reportChangeToEnclosingScope(parent);
          continue;
        }

        // Remove redundant block node. Not strictly necessary, but makes tests more legible.
        if (umdPattern.activeBranch.isNormalBlock()
            && umdPattern.activeBranch.getChildCount() == 1) {
          newNode = umdPattern.activeBranch.getFirstChild();
          umdPattern.activeBranch.detachChildren();
        } else {
          umdPattern.ifRoot.detachChildren();
        }
        parent.replaceChild(umdPattern.ifRoot, newNode);
        changeScope = NodeUtil.getEnclosingChangeScopeRoot(newNode);
        if (changeScope != null) {
          compiler.reportChangeToEnclosingScope(newNode);
        }

        Node block = parent;
        if (block.isExprResult()) {
          block = block.getParent();
        }

        // Detect UMD Factory Patterns and inline the functions
        if (block.isNormalBlock() && block.getParent().isFunction()
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
          String factoryLabel = modulePath.toModuleName() + "_factory";

          FunctionToBlockMutator mutator = new FunctionToBlockMutator(
              compiler, compiler.getUniqueNameIdSupplier());
          Node newStatements = mutator.mutate(
              factoryLabel, fn, enclosingFnCall, null, false, false);

          // Check to see if the returned block is of the form:
          // {
          //   var jscomp$inline = function() {};
          //   jscomp$inline();
          // }
          //
          // If so, inline again
          if (newStatements.isNormalBlock()
              && newStatements.hasTwoChildren()
              && newStatements.getFirstChild().isVar()
              && newStatements.getFirstFirstChild().hasOneChild()
              && newStatements.getFirstFirstChild().getFirstChild().isFunction()
              && newStatements.getSecondChild().isExprResult()) {
            Node inlinedFn = newStatements.getFirstFirstChild().getFirstChild();
            String fnName = newStatements.getFirstFirstChild().getString();
            Node expr = newStatements.getSecondChild().getFirstChild();
            Node call = null;
            if (expr.isAssign() && expr.getSecondChild().isCall()) {
              call = expr.getSecondChild();
            } else if (expr.isCall()) {
              call = expr;
            }

            if (call != null) {
              newStatements = mutator.mutate(
                  factoryLabel, inlinedFn, call, null, false, false);
              if (expr.isAssign() && newStatements.hasOneChild()
                  && newStatements.getFirstChild().isExprResult()) {
                expr.replaceChild(
                    expr.getSecondChild(),
                    newStatements.getFirstFirstChild().detach());
                newStatements = expr.getParent().detach();
              }
            }
          }

          Node callRoot = enclosingFnCall.getParent();
          if (callRoot.isNot()) {
            callRoot = callRoot.getParent();
          }
          if (callRoot.isExprResult()) {
            callRoot = callRoot.getParent();

            callRoot.detachChildren();
            callRoot.addChildToFront(newStatements);
            changeScope = NodeUtil.getEnclosingChangeScopeRoot(callRoot);
            if (changeScope != null) {
              compiler.reportChangeToEnclosingScope(callRoot);
            }
          } else {
            parent.replaceChild(umdPattern.ifRoot, newNode);
            changeScope = NodeUtil.getEnclosingChangeScopeRoot(newNode);
            if (changeScope != null) {
              compiler.reportChangeToEnclosingScope(newNode);
            }
          }
        }
      }
      return needsRetraverse;
    }
  }

  private static boolean umdPatternsContains(List<UmdPattern> umdPatterns, Node n) {
    for (UmdPattern umd : umdPatterns) {
      if (umd.ifRoot == n) {
        return true;
      }
    }
    return false;
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

    public RewriteModule(boolean allowFullRewrite, ImmutableCollection<ExportInfo> exports) {
      this.allowFullRewrite = allowFullRewrite;
      this.exports = exports;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case SCRIPT:
          // Class names can't be changed during the middle of a traversal. Unlike functions,
          // the name can be the EMPTY token rather than just a zero length string.
          for (Node clazz : rewrittenClassExpressions) {
            clazz.replaceChild(
                clazz.getFirstChild(), IR.empty().useSourceInfoFrom(clazz.getFirstChild()));
            t.reportCodeChange();
          }

          CompilerInput ci = compiler.getInput(n.getInputId());
          ModulePath modulePath = ci.getPath();
          String moduleName = modulePath.toModuleName();

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
              scopeRoot.addChildAfter(functionExpr.detach(), insertionPoint);
            }
          }

          for (ExportInfo export : exports) {
            visitExport(t, export.node);
          }

          for (Node require : imports) {
            visitRequireCall(t, require, require.getParent());
          }

          break;

        case CALL:
          if (ProcessCommonJSModules.isCommonJsImport(n)) {
            imports.add(n);
          }
          break;

        case VAR:
        case LET:
        case CONST:
          // Multiple declarations need split apart so that they can be refactored into
          // property assignments or removed altogether.
          if (n.hasMoreThanOneChild() && !NodeUtil.isAnyFor(parent)) {
            List<Node> vars = splitMultipleDeclarations(n);
            t.reportCodeChange();
            for (Node var : vars) {
              visit(t, var.getFirstChild(), var);
            }
          }
          break;

        case NAME:
          {
            // If this is a name declaration with multiple names, it will be split apart when
            // the parent is visited and then revisit the children.
            if (NodeUtil.isNameDeclaration(n.getParent()) && n.getParent().hasMoreThanOneChild()) {
              break;
            }

            String qName = n.getQualifiedName();
            if (qName == null) {
              break;
            }
            Var nameDeclaration = t.getScope().getVar(qName);
            if (nameDeclaration != null
                && nameDeclaration.getNode() != null
                && nameDeclaration.getNode().getInputId() == n.getInputId()) {
              maybeUpdateName(t, n, nameDeclaration);
            }

            break;
          }

          // ES6 object literal shorthand notation can refer to renamed variables
        case STRING_KEY:
          {
            if (n.hasChildren()
                || n.isQuotedString()
                || n.getParent().getParent().isDestructuringLhs()) {
              break;
            }
            Var nameDeclaration = t.getScope().getVar(n.getString());
            if (nameDeclaration == null) {
              break;
            }
            String importedName = getModuleImportName(t, nameDeclaration.getNode());
            if (nameDeclaration.isGlobal() || importedName != null) {
              Node value = IR.name(n.getString()).useSourceInfoFrom(n);
              n.addChildToBack(value);
              maybeUpdateName(t, value, nameDeclaration);
            }
            break;
          }

        default:
          break;
      }

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
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String requireName = ProcessCommonJSModules.getCommonJsImportPath(require);

      ModulePath modulePath =
          t.getInput()
              .getPath()
              .resolveJsModule(
                  requireName,
                  require.getSourceFileName(),
                  require.getLineno(),
                  require.getCharno());
      if (modulePath == null) {
        // The module loader will issue an error
        return;
      }

      String moduleName = modulePath.toModuleName();
      Node moduleRef = IR.name(moduleName).srcref(require);
      parent.replaceChild(require, moduleRef);

      t.reportCodeChange();
    }

    /**
     * Visit export statements. Export statements can be either a direct assignment: module.exports
     * = foo or a property assignment: module.exports.foo = foo; exports.foo = foo;
     */
    private void visitExport(NodeTraversal t, Node export) {
      Node root = getBaseQualifiedNameNode(export);
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
      if (root.matchesQualifiedName("module.exports")) {
        if (rValue != null
            && rValue.isObjectLit()
            && root.getParent().isAssign()
            && root.getParent().getParent().isExprResult()) {
          expandObjectLitAssignment(t, root);
          return;
        }
      }

      ModulePath modulePath = t.getInput().getPath();
      String moduleName = modulePath.toModuleName();

      // If this is an assignment to module.exports or exports, renaming
      // has already handled this case. Remove the export.
      Var rValueVar = null;
      if (rValue != null && rValue.isQualifiedName()) {
        rValueVar = t.getScope().getVar(rValue.getQualifiedName());

        // If the exported name is not found and this is a direct assignment
        // to modules.exports, look to see if the module name has a var definition
        if (rValueVar == null && root == export) {
          rValueVar = t.getScope().getVar(moduleName);
          if (rValueVar != null && rValueVar.getNode() == root) {
            rValueVar = null;
          }
        }
      }

      if (root.getParent().isAssign()
          && (root.getNext() != null && (root.getNext().isName() || root.getNext().isGetProp()))
          && root.getParent().getParent().isExprResult()
          && rValueVar != null) {
        root.getParent().getParent().detach();
        t.reportCodeChange();
        return;
      }

      Node updatedExport =
          NodeUtil.newName(compiler, moduleName, export, export.getQualifiedName());

      if (root.matchesQualifiedName("module.exports")
          && rValue != null
          && t.getScope().getVar("module.exports") == null
          && root.getParent().isAssign()) {
        if (root.getParent().getParent().isExprResult()) {
          // Rewrite "module.exports = foo;" to "var moduleName = foo;"
          Node parent = root.getParent();
          Node var = IR.var(updatedExport, rValue.detach()).useSourceInfoFrom(root.getParent());
          parent.getParent().replaceWith(var);
        } else if (root.getNext() != null && root.getNext().isName()
            && rValueVar != null && rValueVar.isGlobal()) {
          // This is a where a module export assignment is used in a complex expression.
          // Before: `SOME_VALUE !== undefined && module.exports = SOME_VALUE`
          // After: `SOME_VALUE !== undefined && module$name`
          root.getParent().replaceWith(updatedExport);
        } else {
          // Other references to "module.exports" are just replaced with the module name.
          export.replaceWith(updatedExport);
        }
      } else {
        // Other references to "module.exports" are just replaced with the module name.
        export.replaceWith(updatedExport);
      }
      t.reportCodeChange();
    }

    /**
     * Since CommonJS modules may have only a single export, it's common to see the export be an
     * object literal. We want to expand this to individual property assignments. If any individual
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
    private void expandObjectLitAssignment(NodeTraversal t, Node export) {
      checkState(export.getParent().isAssign());
      Node insertionRef = export.getParent().getParent();
      checkState(insertionRef.isExprResult());
      Node insertionParent = insertionRef.getParent();
      checkNotNull(insertionParent);

      Node rValue = NodeUtil.getRValueOfLValue(export);
      Node key = rValue.getFirstChild();

      while (key != null) {
        Node lhs;
        if (key.isQuotedString()) {
          lhs = IR.getelem(export.cloneTree(), IR.string(key.getString()));
        } else {
          lhs = IR.getprop(export.cloneTree(), IR.string(key.getString()));
        }

        Node value = null;
        if (key.isStringKey()) {
          if (key.hasChildren()) {
            value = key.removeFirstChild();
          } else {
            value = IR.name(key.getString());
          }
        } else if (key.isMemberFunctionDef()) {
          value = key.getFirstChild().detach();
        }

        Node expr = null;
        if (!key.isGetterDef()) {
          expr = IR.exprResult(IR.assign(lhs, value)).useSourceInfoIfMissingFromForTree(key);
          insertionParent.addChildAfter(expr, insertionRef);
          visitExport(t, lhs.getFirstChild());
        } else {
          Node getter = key.detach();
          String moduleName = t.getInput().getPath().toModuleName();
          Node moduleObj = t.getScope().getVar(moduleName).getNode().getFirstChild();
          moduleObj.addChildToBack(getter);
        }

        // Export statements can be removed in visitExport
        if (expr != null && expr.getParent() != null) {
          insertionRef = expr;
        }

        key = key.getNext();
      }

      export.getParent().getParent().detach();
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
      checkState(n.getParent() != null);
      String importedModuleName = getModuleImportName(t, var.getNode());
      String originalName = n.getOriginalQualifiedName();

      // Check if the name refers to a alias for a require('foo') import.
      if (importedModuleName != null && n != var.getNode()) {
        // Reference the imported name directly, rather than the alias
        updateNameReference(t, n, originalName, importedModuleName, false);

      } else if (allowFullRewrite) {
        String exportedName = getExportedName(t, n, var);

        // We need to exclude the alias created by the require import. We assume dead
        // code elimination will remove these later.
        if ((n != var.getNode() || n.getParent().isClass()) && exportedName == null) {
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
            && !exportedName.equals(originalName)) {
          updateNameReference(t, n, originalName, exportedName, true);

          // If it's a global name, rename it to prevent conflicts with other scripts
        } else if (var.isGlobal()) {
          ModulePath modulePath = t.getInput().getPath();
          String currentModuleName = modulePath.toModuleName();

          if (currentModuleName.equals(originalName)) {
            return;
          }

          // refs to 'exports' are handled separately.
          if (EXPORTS.equals(originalName)) {
            return;
          }

          // closure_test_suite looks for test*() functions
          if (compiler.getOptions().exportTestFunctions && currentModuleName.startsWith("test")) {
            return;
          }

          String newName = originalName + "$$" + currentModuleName;
          updateNameReference(t, n, originalName, newName, false);
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
        boolean requireFunctionExpressions) {
      Node parent = nameRef.getParent();
      checkNotNull(parent);
      checkNotNull(newName);
      boolean newNameIsQualified = newName.indexOf('.') >= 0;

      Var newNameDeclaration = t.getScope().getVar(newName);

      switch (parent.getToken()) {
        case CLASS:
          if (parent.getIndexOfChild(nameRef) == 0
              && (newNameIsQualified || requireFunctionExpressions)) {
            // Refactor a named class to a class expression
            // We can't remove the class name during a traversal, so save it for later
            rewrittenClassExpressions.add(parent);

            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            Node grandparent = parent.getParent();

            Node expr;
            if (!newNameIsQualified && newNameDeclaration == null) {
              expr = IR.let(newNameRef, IR.nullNode()).useSourceInfoIfMissingFromForTree(nameRef);
            } else {
              expr =
                  IR.exprResult(IR.assign(newNameRef, IR.nullNode()))
                      .useSourceInfoIfMissingFromForTree(nameRef);
            }
            grandparent.replaceChild(parent, expr);
            if (expr.isLet()) {
              expr.getFirstChild().replaceChild(expr.getFirstFirstChild(), parent);
            } else {
              expr.getFirstChild().replaceChild(expr.getFirstChild().getSecondChild(), parent);
            }
          } else if (parent.getIndexOfChild(nameRef) == 1) {
            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            parent.replaceChild(nameRef, newNameRef);
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
          break;

        case FUNCTION:
          if (newNameIsQualified || requireFunctionExpressions) {
            // Refactor a named function to a function expression
            if (NodeUtil.isFunctionExpression(parent)) {
              // Don't refactor if the parent is a named function expression.
              // e.g. var foo = function foo() {};
              break;
            }
            Node newNameRef = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            Node grandparent = parent.getParent();
            nameRef.setString("");

            Node expr;
            if (!newNameIsQualified && newNameDeclaration == null) {
              expr = IR.var(newNameRef, IR.nullNode()).useSourceInfoIfMissingFromForTree(nameRef);
            } else {
              expr =
                  IR.exprResult(IR.assign(newNameRef, IR.nullNode()))
                      .useSourceInfoIfMissingFromForTree(nameRef);
            }
            grandparent.replaceChild(parent, expr);
            if (expr.isVar()) {
              expr.getFirstChild().replaceChild(expr.getFirstFirstChild(), parent);
            } else {
              expr.getFirstChild().replaceChild(expr.getFirstChild().getSecondChild(), parent);
            }
            functionsToHoist.add(expr);
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
          break;

        case VAR:
        case LET:
        case CONST:
          if (newNameIsQualified) {
            // Refactor a var declaration to a getprop assignment
            Node getProp = NodeUtil.newQName(compiler, newName, nameRef, originalName);
            JSDocInfo info = parent.getJSDocInfo();
            parent.setJSDocInfo(null);
            if (nameRef.hasChildren()) {
              Node assign = IR.assign(getProp, nameRef.removeFirstChild());
              assign.setJSDocInfo(info);
              Node expr = IR.exprResult(assign).useSourceInfoIfMissingFromForTree(nameRef);
              parent.replaceWith(expr);
            } else {
              getProp.setJSDocInfo(info);
              parent.replaceWith(IR.exprResult(getProp).useSourceInfoFrom(getProp));
            }
          } else if (newNameDeclaration != null) {
            // Variable is already defined. Convert this to an assignment.
            // If the variable declaration has no initialization, we simply
            // remove the node. This can occur when the variable which is exported
            // is declared in an outer scope but assigned in an inner one.
            if (!nameRef.hasChildren()) {
              parent.detachFromParent();
              break;
            }

            Node name = NodeUtil.newName(compiler, newName, nameRef, originalName);
            Node assign = IR.assign(name, nameRef.removeFirstChild());
            JSDocInfo info = parent.getJSDocInfo();
            if (info != null) {
              parent.setJSDocInfo(null);
              assign.setJSDocInfo(info);
            }

            parent.replaceWith(IR.exprResult(assign).useSourceInfoFromForTree(nameRef));
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
          break;

        default:
          {
            Node name =
                newNameIsQualified
                    ? NodeUtil.newQName(compiler, newName, nameRef, originalName)
                    : NodeUtil.newName(compiler, newName, nameRef, originalName);

            JSDocInfo info = nameRef.getJSDocInfo();
            if (info != null) {
              nameRef.setJSDocInfo(null);
              name.setJSDocInfo(info);
            }
            parent.replaceChild(nameRef, name);
            if (nameRef.hasChildren()) {
              name.addChildrenToFront(nameRef.removeChildren());
            }

            break;
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
    private String getExportedName(NodeTraversal t, Node n, Var var) {
      if (var == null || var.getNode().getInputId() != n.getInputId()) {
        return n.getQualifiedName();
      }

      String moduleName = t.getInput().getPath().toModuleName();

      for (ExportInfo export : this.exports) {
        Node exportBase = getBaseQualifiedNameNode(export.node);
        Node exportRValue = NodeUtil.getRValueOfLValue(exportBase);

        if (exportRValue == null) {
          continue;
        }

        Node exportedName = getExportedNameNode(export);
        // We don't want to handle the export itself
        if (exportRValue == n
            || ((NodeUtil.isClassExpression(exportRValue)
                    || NodeUtil.isFunctionExpression(exportRValue))
                && exportedName == n)) {
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
                && !key.isQuotedString()
                && NodeUtil.isValidPropertyName(
                    compiler.getOptions().getLanguageIn().toFeatureSet(), key.getString())) {
              if (key.hasChildren()) {
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
              } else {
                if (key == n) {
                  return null;
                }

                // Handle ES6 object lit shorthand assignments
                Var valVar = t.getScope().getVar(key.getString());
                if (valVar != null && valVar.getNameNode() == var.getNameNode()) {
                  keyIsExport = true;
                  break;
                }
              }
            }

            key = key.getNext();
          }
          if (key != null && keyIsExport) {
            return moduleName + "." + key.getString();
          }
        } else {
          if (var.getNameNode() == exportedName) {
            String exportPrefix = exportBaseQName.startsWith(MODULE) ? "module.exports" : EXPORTS;

            if (exportBaseQName.length() == exportPrefix.length()) {
              return moduleName;
            }

            return moduleName + exportBaseQName.substring(exportPrefix.length());
          }
        }
      }
      return n.getQualifiedName();
    }

    private Node getExportedNameNode(ExportInfo info) {
      Node qNameBase = getBaseQualifiedNameNode(info.node);
      Node rValue = NodeUtil.getRValueOfLValue(qNameBase);

      if (rValue == null) {
        return null;
      }

      if (NodeUtil.isFunctionExpression(rValue) || NodeUtil.isClassExpression(rValue)) {
        return rValue.getFirstChild();
      }

      Var var = info.scope.getVar(rValue.getQualifiedName());
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
    private String getModuleImportName(NodeTraversal t, Node n) {
      Node rValue = null;
      String propSuffix = "";
      if (n.isStringKey()
          && n.getParent().isObjectPattern()
          && n.getParent().getParent().isDestructuringLhs()) {
        rValue = n.getParent().getNext();
        propSuffix = "." + n.getString();
      } else if (n.getParent() != null) {
        rValue = NodeUtil.getRValueOfLValue(n);
      }

      if (rValue == null) {
        return null;
      }

      if (rValue.isCall()) {
        // var foo = require('bar');
        if (ProcessCommonJSModules.isCommonJsImport(rValue)
            && t.getScope().getVar(rValue.getFirstChild().getQualifiedName()) == null) {
          String requireName = ProcessCommonJSModules.getCommonJsImportPath(rValue);
          ModulePath modulePath =
              t.getInput()
                  .getPath()
                  .resolveJsModule(
                      requireName, n.getSourceFileName(), n.getLineno(), n.getCharno());
          if (modulePath == null) {
            return null;
          }
          return modulePath.toModuleName() + propSuffix;
        }
        return null;

      } else if (rValue.isGetProp()) {
        // var foo = require('bar').foo;
        String moduleName = getModuleImportName(t, rValue.getFirstChild());
        if (moduleName != null) {
          return moduleName + "." + n.getSecondChild().getString() + propSuffix;
        }
      }

      return null;
    }

    /**
     * Update any type references in JSDoc annotations to account for all the rewriting we've done.
     */
    private void fixTypeNode(NodeTraversal t, Node typeNode) {
      if (typeNode.isString()) {
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
          ModulePath modulePath =
              t.getInput()
                  .getPath()
                  .resolveJsModule(
                      moduleName,
                      typeNode.getSourceFileName(),
                      typeNode.getLineno(),
                      typeNode.getCharno());
          if (modulePath == null) {
            // The module loader will issue an error
            return;
          }

          String globalModuleName = modulePath.toModuleName();
          typeNode.setString(
              localTypeName == null ? globalModuleName : globalModuleName + localTypeName);

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
            if (typeDeclaration != null
                && typeDeclaration.getNode().getInputId() == typeNode.getInputId()) {
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
              String moduleName = t.getInput().getPath().toModuleName();
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
      while (var.getSecondChild() != null) {
        Node newVar = new Node(var.getToken(), var.removeFirstChild());
        newVar.useSourceInfoFrom(var);
        var.getParent().addChildBefore(newVar, var);
        vars.add(newVar);
      }
      vars.add(var);
      return vars;
    }
  }
}
