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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  public static final DiagnosticType COMMON_JS_MODULE_LOAD_ERROR = DiagnosticType.error(
      "JSC_COMMONJS_MODULE_LOAD_ERROR",
      "Failed to load module \"{0}\"");

  public static final DiagnosticType UNKNOWN_REQUIRE_ENSURE =
      DiagnosticType.warning(
          "JSC_COMMONJS_UNKNOWN_REQUIRE_ENSURE_ERROR", "Unrecognized require.ensure call: {0}");

  public static final DiagnosticType SUSPICIOUS_EXPORTS_ASSIGNMENT =
      DiagnosticType.warning(
          "JSC_COMMONJS_SUSPICIOUS_EXPORTS_ASSIGNMENT",
          "Suspicious re-assignment of \"exports\" variable."
              + " Did you actually intend to export something?");

  private final Compiler compiler;
  private final boolean reportDependencies;

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to
   * rewrite CommonJS modules to a concatenable form.
   *
   * @param compiler The compiler
   */
  public ProcessCommonJSModules(Compiler compiler) {
    this(compiler, true);
  }

  /**
   * Creates a new ProcessCommonJSModules instance which can be used to
   * rewrite CommonJS modules to a concatenable form.
   *
   * @param compiler The compiler
   * @param reportDependencies Whether the rewriter should report dependency
   *     information to the Closure dependency manager. This needs to be true
   *     if we want to sort CommonJS module inputs correctly. Note that goog.provide
   *     and goog.require calls will still be generated if this argument is
   *     false.
   */
  public ProcessCommonJSModules(Compiler compiler, boolean reportDependencies) {
    this.compiler = compiler;
    this.reportDependencies = reportDependencies;
  }


  /**
   * Module rewriting is done a on per-file basis prior to main compilation. The pass must handle
   * ES6+ syntax and the root node for each file is a SCRIPT - not the typical jsRoot of other
   * passes.
   */
  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(root.isScript());
    FindImportsAndExports finder = new FindImportsAndExports();
    NodeTraversal.traverseEs6(compiler, root, finder);

    Builder<Node> exports = ImmutableList.builder();
    if (finder.isCommonJsModule()) {
      finder.reportModuleErrors();
      finder.replaceUmdPatterns();

      //UMD pattern replacement can leave detached export references - don't include those
      for (Node export : finder.getModuleExports()) {
        if (NodeUtil.getEnclosingScript(export) != null) {
          exports.add(export);
        }
      }
      for (Node export : finder.getExports()) {
        if (NodeUtil.getEnclosingScript(export) != null) {
          exports.add(export);
        }
      }

      finder.addGoogProvide();
      compiler.reportCodeChange();
    }

    NodeTraversal.traverseEs6(
        compiler, root, new RewriteModule(finder.isCommonJsModule(), exports.build()));
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

  private Node getBaseQualifiedNameNode(Node n) {
    Node refParent = n;
    while (refParent.getParent() != null && refParent.getParent().isQualifiedName()) {
      refParent = refParent.getParent();
    }

    return refParent;
  }

  /**
   * Traverse the script. Find all references to CommonJS require (import) and module.exports or
   * export statements. Add goog.require statements for any require statements. Rewrites any require
   * calls to reference the rewritten module name.
   */
  class FindImportsAndExports implements NodeTraversal.Callback {
    private boolean hasGoogProvideOrModule = false;
    private Node script = null;

    boolean isCommonJsModule() {
      return (exports.size() > 0 || moduleExports.size() > 0) && !hasGoogProvideOrModule;
    }

    List<UmdPattern> umdPatterns = new ArrayList<>();
    List<Node> moduleExports = new ArrayList<>();
    List<Node> exports = new ArrayList<>();
    Set<String> imports = new HashSet<>();
    List<JSError> errors = new ArrayList<>();

    public List<Node> getModuleExports() {
      return ImmutableList.copyOf(moduleExports);
    }

    public List<Node> getExports() {
      return ImmutableList.copyOf(exports);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isScript()) {
        Preconditions.checkState(this.script == null);
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

      if (n.matchesQualifiedName("module.exports")) {
        Var v = t.getScope().getVar(MODULE);
        // only rewrite "module.exports" if "module" is a free variable,
        // meaning it is not defined in the current scope as a local
        // variable or function parameter
        if (v == null) {
          moduleExports.add(n);

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
              && EXPORTS.equals(qNameRoot.getQualifiedName())
              && NodeUtil.isLValue(qNameRoot)) {
            if (!this.hasGoogProvideOrModule) {
              errors.add(t.makeError(qNameRoot, SUSPICIOUS_EXPORTS_ASSIGNMENT));
            }
          } else {
            exports.add(n);

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

      if (n.isCall()
          && n.getChildCount() == 2
          && n.getFirstChild().matchesQualifiedName("require")
          && n.getSecondChild().isString()) {
        visitRequireCall(t, n, parent);
      }
    }

    /** Visit require calls. Emit corresponding goog.require call. */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String requireName = require.getSecondChild().getString();
      ModulePath modulePath = t.getInput().getPath().resolveCommonJsModule(requireName);
      if (modulePath == null) {
        compiler.report(t.makeError(require, COMMON_JS_MODULE_LOAD_ERROR, requireName));
        return;
      }


      String moduleName = modulePath.toModuleName();

      // When require("name") is used as a standalone statement (the result isn't used)
      // it indicates that a module is being loaded for the side effects it produces.
      // In this case the require statement should just be removed as the goog.require
      // call inserted will import the module.
      if (!NodeUtil.isExpressionResultUsed(require)
          && parent.isExprResult()
          && NodeUtil.isStatementBlock(parent.getParent())) {
        parent.getParent().removeChild(parent);
      }

      if (imports.add(moduleName)) {
        if (reportDependencies) {
          t.getInput().addRequire(moduleName);
        }
        this.script.addChildToFront(
            IR.exprResult(
                    IR.call(
                        IR.getprop(IR.name("goog"), IR.string("require")), IR.string(moduleName)))
                .useSourceInfoIfMissingFromForTree(require));
        compiler.reportCodeChange();
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

      compiler.reportCodeChange();
    }

    void reportModuleErrors() {
      for (JSError error : errors) {
        compiler.report(error);
      }
    }

    /**
     * Add a goog.provide statement for the module. If the export is directly assigned more than
     * once, or the assignments are not global, declare the module name variable.
     *
     * <p>If all of the assignments are simply property assignments, initialize the module name
     * variable as a namespace.
     */
    void addGoogProvide() {
      CompilerInput ci = compiler.getInput(this.script.getInputId());
      ModulePath modulePath = ci.getPath();
      if (modulePath == null) {
        return;
      }

      String moduleName = modulePath.toModuleName();

      // Add goog.provide calls.
      if (reportDependencies) {
        ci.addProvide(moduleName);
      }
      this.script.addChildToFront(
          IR.exprResult(
                  IR.call(IR.getprop(IR.name("goog"), IR.string("provide")), IR.string(moduleName)))
              .useSourceInfoIfMissingFromForTree(this.script));

      // The default declaration for the goog.provide is a constant so
      // we need to declare the variable if we have more than one
      // assignment to module.exports or those assignments are not
      // at the top level.
      //
      // If we assign to the variable more than once or all the assignments
      // are properties, initialize the variable as well.
      int directAssignmentsAtTopLevel = 0;
      int directAssignments = 0;
      for (Node export : moduleExports) {
        if (NodeUtil.getEnclosingScript(export) == null) {
          continue;
        }

        Node base = getBaseQualifiedNameNode(export);
        if (base == export && export.getParent().isAssign()) {
          Node rValue = NodeUtil.getRValueOfLValue(export);
          if (rValue == null || !rValue.isObjectLit()) {
            directAssignments++;
            if (export.getParent().getParent().isExprResult()
                && NodeUtil.isTopLevel(export.getParent().getParent().getParent())) {
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

        Node refChild = this.script.getFirstChild();
        while (refChild.getNext() != null & refChild.getNext().isExprResult()
            && refChild.getNext().getFirstChild().isCall()
            && (refChild.getNext().getFirstFirstChild().matchesQualifiedName("goog.require")
                || refChild.getNext().getFirstFirstChild().matchesQualifiedName("goog.provide"))) {
          refChild = refChild.getNext();
        }
        this.script.addChildAfter(initModule, refChild);
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

      if (parent.isIf() && parent.getFirstChild() == n) {
        Node outerIf = getOutermostIfAncestor(parent);
        if (outerIf != null) {
          return outerIf;
        }

        return parent;
      }

      return getOutermostIfAncestor(parent);
    }

    /** Remove a Universal Module Definition and leave just the commonjs export statement */
    void replaceUmdPatterns() {
      for (UmdPattern umdPattern : umdPatterns) {
        Node p = umdPattern.ifRoot.getParent();
        Node newNode = umdPattern.activeBranch;

        if (newNode == null) {
          p.removeChild(umdPattern.ifRoot);
          return;
        }

        // Remove redundant block node. Not strictly necessary, but makes tests more legible.
        if (umdPattern.activeBranch.isBlock() && umdPattern.activeBranch.getChildCount() == 1) {
          newNode = umdPattern.activeBranch.getFirstChild();
          umdPattern.activeBranch.detachChildren();
        } else {
          umdPattern.ifRoot.detachChildren();
        }
        p.replaceChild(umdPattern.ifRoot, newNode);
      }
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
    private final ImmutableCollection<Node> exports;
    private final List<Node> imports = new ArrayList<>();
    private final List<Node> rewrittenClassExpressions = new ArrayList<>();

    public RewriteModule(boolean allowFullRewrite, ImmutableCollection<Node> exports) {
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
            compiler.reportCodeChange();
          }

          for (Node export : exports) {
            visitExport(t, export);
          }

          for (Node require : imports) {
            visitRequireCall(t, require, require.getParent());
          }

          break;

        case CALL:
          if (n.getChildCount() == 2
              && n.getFirstChild().matchesQualifiedName("require")
              && n.getSecondChild().isString()) {
            imports.add(n);
          }
          break;

        case NAME:
          {
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
      String requireName = require.getSecondChild().getString();
      ModulePath modulePath = t.getInput().getPath().resolveCommonJsModule(requireName);
      if (modulePath == null) {
        compiler.report(t.makeError(require, COMMON_JS_MODULE_LOAD_ERROR, requireName));
        return;
      }

      String moduleName = modulePath.toModuleName();
      Node moduleRef = IR.name(moduleName).srcref(require);
      parent.replaceChild(require, moduleRef);

      compiler.reportCodeChange();
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
      if ("module.exports".equals(root.getQualifiedName())) {
        if (rValue != null
            && rValue.isObjectLit()
            && root.getParent().isAssign()
            && root.getParent().getParent().isExprResult()) {
          expandObjectLitAssignment(t, root);
          return;
        }
      }

      // If this is an assignment to module.exports or exports, renaming
      // has already handled this case. Remove the export.
      Var rValueVar = null;
      if (rValue != null && rValue.isQualifiedName()) {
        rValueVar = t.getScope().getVar(rValue.getQualifiedName());
      }

      if (root.getParent().isAssign()
          && (root.getNext().isName() || root.getNext().isGetProp())
          && root.getParent().getParent().isExprResult()
          && rValueVar != null) {
        root.getParent().getParent().detachFromParent();
        compiler.reportCodeChange();
        return;
      }

      ModulePath modulePath = t.getInput().getPath();
      String moduleName = modulePath.toModuleName();
      Node updatedExport =
          NodeUtil.newName(compiler, moduleName, export, export.getQualifiedName());

      // Ensure that direct assignments to "module.exports" have var definitions
      if ("module.exports".equals(root.getQualifiedName())
          && rValue != null
          && t.getScope().getVar("module.exports") == null
          && root.getParent().isAssign()
          && root.getParent().getParent().isExprResult()) {
        Node parent = root.getParent();
        Node var = IR.var(updatedExport, rValue.detach()).useSourceInfoFrom(root.getParent());
        parent.getParent().getParent().replaceChild(parent.getParent(), var);
      } else {
        export.getParent().replaceChild(export, updatedExport);
      }
      compiler.reportCodeChange();
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
      Preconditions.checkState(export.getParent().isAssign());
      Node insertionRef = export.getParent().getParent();
      Preconditions.checkState(insertionRef.isExprResult());
      Node insertionParent = insertionRef.getParent();
      Preconditions.checkNotNull(insertionParent);

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
            value = key.getFirstChild().detachFromParent();
          } else {
            value = IR.name(key.getString());
          }
        } else if (key.isMemberFunctionDef()) {
          value = key.getFirstChild().detach();
        }

        Node expr = IR.exprResult(IR.assign(lhs, value)).useSourceInfoIfMissingFromForTree(key);

        insertionParent.addChildAfter(expr, insertionRef);
        visitExport(t, lhs.getFirstChild());

        // Export statements can be removed in visitExport
        if (expr.getParent() != null) {
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
      Preconditions.checkNotNull(var);
      Preconditions.checkState(n.isName() || n.isGetProp());
      Preconditions.checkState(n.getParent() != null);
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
      Preconditions.checkNotNull(parent);
      Preconditions.checkNotNull(newName);
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
              expr.getFirstChild().replaceChild(expr.getFirstChild().getFirstChild(), parent);
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
              expr.getFirstChild().replaceChild(expr.getFirstChild().getFirstChild(), parent);
            } else {
              expr.getFirstChild().replaceChild(expr.getFirstChild().getSecondChild(), parent);
            }
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
            if (nameRef.hasChildren()) {
              Node expr =
                  IR.exprResult(IR.assign(getProp, nameRef.getFirstChild().detachFromParent()))
                      .useSourceInfoIfMissingFromForTree(nameRef);
              parent.getParent().replaceChild(parent, expr);
            } else {
              parent.getParent().replaceChild(parent, getProp);
            }
          } else if (newNameDeclaration != null) {
            // Variable is already defined. Convert this to an assignment.
            Node name = NodeUtil.newName(compiler, newName, nameRef, originalName);
            parent
                .getParent()
                .replaceChild(
                    parent,
                    IR.exprResult(IR.assign(name, nameRef.getFirstChild().detachFromParent()))
                        .useSourceInfoFromForTree(nameRef));
          } else {
            nameRef.setString(newName);
            nameRef.setOriginalName(originalName);
          }
          break;

        case GETPROP:
          {
            Node name =
                newNameIsQualified
                    ? NodeUtil.newQName(compiler, newName, nameRef, originalName)
                    : NodeUtil.newName(compiler, newName, nameRef, originalName);
            parent.replaceChild(nameRef, name);
            moveChildrenToNewNode(nameRef, name);

            break;
        }
        default:
          {
            Node name =
                newNameIsQualified
                    ? NodeUtil.newQName(compiler, newName, nameRef, originalName)
                    : NodeUtil.newName(compiler, newName, nameRef, originalName);
            parent.replaceChild(nameRef, name);
            moveChildrenToNewNode(nameRef, name);
            break;
          }
      }

      compiler.reportCodeChange();
    }

    private void moveChildrenToNewNode(Node oldNode, Node newNode) {
      while (oldNode.hasChildren()) {
        newNode.addChildToBack(oldNode.getFirstChild().detachFromParent());
      }
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

      for (Node export : this.exports) {
        Node qNameBase = getBaseQualifiedNameNode(export);
        Node rValue = NodeUtil.getRValueOfLValue(qNameBase);
        if (rValue == null) {
          continue;
        }

        // We don't want to handle the export itself
        if (rValue == n || (rValue.isClass() && rValue.getFirstChild() == n)) {
          return null;
        }

        String exportedNameBase = qNameBase.getQualifiedName();

        if (rValue.isObjectLit()) {
          if (!"module.exports".equals(exportedNameBase)) {
            return n.getQualifiedName();
          }

          Node key = rValue.getFirstChild();
          Var exportVar = null;
          while (key != null) {
            if (key.isStringKey()
                && !key.isQuotedString()
                && NodeUtil.isValidPropertyName(compiler.getLanguageMode(), key.getString())) {
              if (key.hasChildren()) {
                if (key.getFirstChild().isQualifiedName()) {
                  if (key.getFirstChild() == n) {
                    return null;
                  }

                  Var valVar = t.getScope().getVar(key.getFirstChild().getQualifiedName());
                  if (valVar != null && valVar.getNode() == var.getNode()) {
                    exportVar = valVar;
                    break;
                  }
                }
              } else {
                if (key == n) {
                  return null;
                }

                // Handle ES6 object lit shorthand assignments
                Var valVar = t.getScope().getVar(key.getString());
                if (valVar != null && valVar.getNode() == var.getNode()) {
                  exportVar = valVar;
                  break;
                }
              }
            }

            key = key.getNext();
          }
          if (key != null && exportVar != null) {
            return moduleName + "." + key.getString();
          }
        } else {
          String qName;
          if (rValue.isClass()) {
            qName = rValue.getFirstChild().getQualifiedName();
          } else {
            qName = rValue.getQualifiedName();
          }
          if (qName != null) {
            Var exportVar = t.getScope().getVar(qName);
            if (exportVar != null && exportVar.getNode() == var.getNode()) {
              String exportPrefix =
                  exportedNameBase.startsWith(MODULE) ? "module.exports" : EXPORTS;

              if (exportedNameBase.length() == exportPrefix.length()) {
                return moduleName;
              }

              return moduleName + exportedNameBase.substring(exportPrefix.length());
            }
          }
        }
      }
      return n.getQualifiedName();
    }

    /**
     * Determine if the given Node n is an alias created by a module import.
     *
     * @return null if it's not an alias or the imported module name
     */
    private String getModuleImportName(NodeTraversal t, Node n) {
      Node rValue;
      String propSuffix = "";
      if (n.isStringKey()
          && n.getParent().isObjectPattern()
          && n.getParent().getParent().isDestructuringLhs()) {
        rValue = n.getParent().getNext();
        propSuffix = "." + n.getString();
      } else {
        rValue = NodeUtil.getRValueOfLValue(n);
      }

      if (rValue == null) {
        return null;
      }

      if (rValue.isCall()) {
        // var foo = require('bar');
        if (rValue.getChildCount() == 2
            && rValue.getFirstChild().matchesQualifiedName("require")
            && rValue.getSecondChild().isString()
            && t.getScope().getVar(rValue.getFirstChild().getQualifiedName()) == null) {
          String requireName = rValue.getSecondChild().getString();
          ModulePath modulePath = t.getInput().getPath().resolveCommonJsModule(requireName);
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
        if (ModuleLoader.isRelativeIdentifier(name) || ModuleLoader.isAbsoluteIdentifier(name)) {
          int lastSlash = name.lastIndexOf('/');
          int endIndex = name.indexOf('.', lastSlash);
          String localTypeName = null;
          if (endIndex == -1) {
            endIndex = name.length();
          } else {
            localTypeName = name.substring(endIndex);
          }

          String moduleName = name.substring(0, endIndex);
          ModulePath modulePath = t.getInput().getPath().resolveCommonJsModule(moduleName);
          if (modulePath == null) {
            t.makeError(typeNode, COMMON_JS_MODULE_LOAD_ERROR, moduleName);
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
  }
}
