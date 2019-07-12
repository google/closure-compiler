/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Looks for references to Closure's goog.js file and globalizes. The goog.js file is an ES6 module
 * that forwards some symbols on goog from Closure's base.js file, like require.
 *
 * <p>This pass scans the goog.js file to find what keys are exported. When rewriting imports only
 * those in the goog.js file are globalized; if there is a reference to something not in the goog.js
 * file it is rewritten in such a way to cause an error later at type checking.
 *
 * <p>This is a separate pass that needs to run before Es6RewriteModules which will remove import
 * statements.
 *
 * <p>This pass enforces the following so that later compiler passes and regex based tools can
 * correctly recognize references to these properties of goog (like goog.require):
 *
 * <ul>
 *   <li>No other file is named goog.js
 *   <li>The file is always imported as <code>import * as goog</code>
 *   <li>No other module level variable is named <code>goog</code>
 *   <li><code>export from</code> is never used on goog.js
 * </ul>
 *
 * <p>Example:
 *
 * <p>{@code import * as goog from 'path/to/closure/goog.js'; const myNamespace =
 * goog.require('my.namespace'); }
 *
 * <p>Will be rewritten to:
 *
 * <p>{@code import 'path/to/closure/goog.js'; const myNamespace = goog.require('my.namespace'); }
 */
public class RewriteGoogJsImports implements HotSwapCompilerPass {
  static final DiagnosticType GOOG_JS_IMPORT_MUST_BE_GOOG_STAR =
      DiagnosticType.error(
          "JSC_GOOG_JS_IMPORT_MUST_BE_GOOG_STAR",
          "Closure''s goog.js file must be imported as `import * as goog`.");

  // Since many tools scan for "goog.require" ban re-exporting.
  static final DiagnosticType GOOG_JS_REEXPORTED =
      DiagnosticType.error("JSC_GOOG_JS_REEXPORTED", "Do not re-export from goog.js.");

  static final DiagnosticType CANNOT_NAME_FILE_GOOG =
      DiagnosticType.error(
          "JSC_CANNOT_NAME_FILE_GOOG",
          "Do not name files goog.js, it is reserved for Closure Library.");

  static final DiagnosticType CANNOT_HAVE_MODULE_VAR_NAMED_GOOG =
      DiagnosticType.error(
          "JSC_CANNOT_HAVE_MODULE_VAR_NAMED_GOOG",
          "Module scoped variables named ''goog'' must come from importing Closure Library''s "
              + "goog.js file..");

  /**
   * Possible traversal modes - either linting or linting+rewriting.
   */
  public enum Mode {
    /* Lint only. */
    LINT_ONLY,
    /* Lint and perform a rewrite on an entire compilation job */
    LINT_AND_REWRITE
  }

  private static final String EXPECTED_BASE_PROVIDE = "goog";

  private final Mode mode;
  private final ModuleMap moduleMap;
  private final AbstractCompiler compiler;
  private Module googModule;
  private final Map<Module, Module> moduleReplacements = new HashMap<>();

  public RewriteGoogJsImports(AbstractCompiler compiler, Mode mode, ModuleMap moduleMap) {
    checkNotNull(moduleMap);
    this.compiler = compiler;
    this.mode = mode;
    this.moduleMap = moduleMap;
  }

  private void changeModules() {
    ImmutableMap.Builder<String, Module> resolvedModules = ImmutableMap.builder();
    ImmutableMap.Builder<String, Module> closureModules = ImmutableMap.builder();

    for (Map.Entry<String, Module> m : moduleMap.getModulesByPath().entrySet()) {
      Module newModule = moduleReplacements.getOrDefault(m.getValue(), m.getValue());
      resolvedModules.put(m.getKey(), newModule);
    }

    for (Map.Entry<String, Module> m : moduleMap.getModulesByClosureNamespace().entrySet()) {
      Module newModule = moduleReplacements.getOrDefault(m.getValue(), m.getValue());
      closureModules.put(m.getKey(), newModule);
    }

    compiler.setModuleMap(new ModuleMap(resolvedModules.build(), closureModules.build()));
    moduleReplacements.clear();
  }

  /**
   * Determines if a script imports the goog.js file and if so ensures that property accesses are
   * valid and then detaches the import.
   */
  private class ReferenceReplacer extends AbstractPostOrderCallback {
    private boolean hasBadExport;
    private final Node googImportNode;
    private final boolean globalizeAllReferences;

    ReferenceReplacer(
        Node script, Node googImportNode, Module module, boolean globalizeAllReferences) {
      this.googImportNode = googImportNode;
      this.globalizeAllReferences = globalizeAllReferences;
      NodeTraversal.traverse(compiler, script, this);

      if (googImportNode.getSecondChild().isImportStar()) {
        Map<String, Binding> newBindings = new LinkedHashMap<>(module.boundNames());
        newBindings.remove("goog");
        if (hasBadExport) {
          // We might be able to get rid of this case now. Originally this was to cause a type error
          // later in compilation. But now the ModuleMapCreator should log an error for the bad
          // export. However if we're still running we might be in some tool that expects us to be
          // fault tolerant and still rewrite modules. So assume this is needed in those tools.
          googImportNode.getSecondChild().setOriginalName("goog");
          googImportNode.getSecondChild().setString("$goog");
          newBindings.put("$goog", module.boundNames().get("goog"));
        } else {
          googImportNode.getSecondChild().replaceWith(IR.empty());
        }
        Module newModule = module.toBuilder().boundNames(ImmutableMap.copyOf(newBindings)).build();
        moduleReplacements.put(module, newModule);
        compiler.reportChangeToEnclosingScope(googImportNode);
      }
    }

    private void maybeRewriteBadGoogJsImportRef(NodeTraversal t, Node nameNode, Node parent) {
      if (!parent.isGetProp() || !nameNode.getString().equals("goog")) {
        return;
      }

      Var var = t.getScope().getVar(nameNode.getString());

      if (var == null
          || var.getNameNode() == null
          || var.getNameNode().getParent() != googImportNode) {
        return;
      }

      if (globalizeAllReferences
          || googModule.namespace().containsKey(parent.getSecondChild().getString())) {
        return;
      }

      // Rewrite and keep the import so later type checking can report an error that this property
      // does not exist on the ES6 module.
      hasBadExport = true;
      nameNode.setOriginalName("goog");
      nameNode.setString("$goog");
      t.reportCodeChange();

      return;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case NAME:
          maybeRewriteBadGoogJsImportRef(t, n, parent);
          break;
        default:
          break;
      }
    }
  }

  /**
   * Finds instances where goog is reexported. Not an exhaustive search; does not find alias, e.g.
   * {@code export const x = goog;}. Not to say that people should export using aliases; they
   * shouldn't. Just that we're catching the 99% case and that should be an indication to people not
   * to do bad things.
   */
  private static class FindReexports extends AbstractPreOrderCallback {
    private final boolean hasGoogImport;

    public FindReexports(boolean hasGoogImport) {
      this.hasGoogImport = hasGoogImport;
    }

    private void checkIfForwardingExport(NodeTraversal t, Node export) {
      if (export.hasTwoChildren() && export.getLastChild().getString().endsWith("/goog.js")) {
        t.report(export, GOOG_JS_REEXPORTED);
      }
    }

    private void checkIfNameFowardedExport(NodeTraversal t, Node nameNode, Node parent) {
      if (hasGoogImport && nameNode.getString().equals("goog")) {
        if ((parent.isExportSpec() && parent.getFirstChild() == nameNode) || parent.isExport()) {
          t.report(nameNode, GOOG_JS_REEXPORTED);
        }
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case ROOT:
        case SCRIPT:
        case MODULE_BODY:
        case EXPORT_SPECS:
        case EXPORT_SPEC:
          return true;
        case EXPORT:
          checkIfForwardingExport(t, n);
          return true;
        case NAME:
          // Visit names in export specs and export defaults.
          checkIfNameFowardedExport(t, n, parent);
          return false;
        default:
          return false;
      }
    }
  }

  @Nullable
  private Node findGoogImportNode(Node scriptRoot) {
    // Cannot use the module map here - information is lost about the imports. The "bound names"
    // could be from transitive imports, but we lose the original import.
    boolean valid = true;
    Node googImportNode = null;

    for (Node child : scriptRoot.getFirstChild().children()) {
      if (child.isImport() && child.getLastChild().getString().endsWith("/goog.js")) {
        if (child.getFirstChild().isEmpty()
            && child.getSecondChild().isImportStar()
            && child.getSecondChild().getString().equals("goog")) {
          googImportNode = child;
        } else {
          valid = false;
          compiler.report(
              JSError.make(
                  scriptRoot.getSourceFileName(),
                  child.getLineno(),
                  child.getCharno(),
                  GOOG_JS_IMPORT_MUST_BE_GOOG_STAR));
        }
      }
    }

    if (!valid) {
      return null;
    } else if (googImportNode != null) {
      return googImportNode;
    }

    Scope moduleScope =
        new SyntacticScopeCreator(compiler)
            .createScope(scriptRoot.getFirstChild(), Scope.createGlobalScope(scriptRoot));
    Var googVar = moduleScope.getVar("goog");

    if (googVar != null && googVar.getNameNode() != null) {
      compiler.report(
          JSError.make(
              scriptRoot.getSourceFileName(),
              googVar.getNameNode().getLineno(),
              googVar.getNameNode().getCharno(),
              CANNOT_HAVE_MODULE_VAR_NAMED_GOOG));
    }

    return null;
  }

  private void rewriteImports(Node scriptRoot) {
    if (Es6RewriteModules.isEs6ModuleRoot(scriptRoot)) {
      Node googImportNode = findGoogImportNode(scriptRoot);
      NodeTraversal.traverse(compiler, scriptRoot, new FindReexports(googImportNode != null));
      Module module = moduleMap.getModule(compiler.getInput(scriptRoot.getInputId()).getPath());
      checkNotNull(module);

      if (googImportNode != null && mode == Mode.LINT_AND_REWRITE) {
        // If googModule is null then goog.js was not part of the input. Try to be fault tolerant
        // and just assume that everything exported is on the global goog.
        new ReferenceReplacer(
            scriptRoot, googImportNode, module, /* globalizeAllReferences= */ googModule == null);
      }
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    rewriteImports(scriptRoot);
    changeModules();
  }

  @Nullable
  private Node findGoogJsScriptNode(Node root) {
    ModulePath expectedGoogPath = null;

    // Find Closure's base.js file. goog.js should be right next to it.
    for (Node script : root.children()) {
      ImmutableList<String> provides = compiler.getInput(script.getInputId()).getProvides();
      if (provides.contains(EXPECTED_BASE_PROVIDE)) {
        // Use resolveModuleAsPath as if it is not part of the input we don't want to report an
        // error.
        expectedGoogPath =
            compiler.getInput(script.getInputId()).getPath().resolveModuleAsPath("./goog.js");
        break;
      }
    }

    if (expectedGoogPath != null) {
      Node googScriptNode = null;

      for (Node script : root.children()) {
        if (compiler
            .getInput(script.getInputId())
            .getPath()
            .equalsIgnoreLeadingSlash(expectedGoogPath)) {
          googScriptNode = script;
        } else if (script.getSourceFileName().endsWith("/goog.js")) {
          // Ban the name goog.js as input except for Closure's goog.js file. This simplifies a lot
          // of logic if the only file that is allowed to be named goog.js is Closure's.
          compiler.report(
              JSError.make(
                  script.getSourceFileName(), -1, -1, CheckLevel.ERROR, CANNOT_NAME_FILE_GOOG));
        }
      }

      return googScriptNode;
    }

    return null;
  }

  @Override
  public void process(Node externs, Node root) {
    googModule = null;

    Node googJsScriptNode = findGoogJsScriptNode(root);

    if (googJsScriptNode == null) {
      // Potentially in externs if library level type checking.
      googJsScriptNode = findGoogJsScriptNode(externs);
    }

    if (mode == Mode.LINT_AND_REWRITE) {
      if (googJsScriptNode != null) {
        ModulePath googJsPath = compiler.getInput(googJsScriptNode.getInputId()).getPath();
        googModule = moduleMap.getModule(googJsPath);
        checkNotNull(googModule);
        Predicate<Binding> isFromGoog =
            (Binding b) -> b.originatingExport().modulePath() == googJsPath;
        checkState(
            googModule.boundNames().values().stream().allMatch(isFromGoog),
            "goog.js should never import anything");
        checkState(
            !googModule.namespace().containsKey("default"),
            "goog.js should never have a default export.");
        checkState(
            googModule.namespace().values().stream().allMatch(isFromGoog),
            "goog.js should never export from anything.");
      }
    } else {
      checkState(mode == Mode.LINT_ONLY);
    }

    for (Node script : root.children()) {
      rewriteImports(script);
    }

    changeModules();
  }
}
