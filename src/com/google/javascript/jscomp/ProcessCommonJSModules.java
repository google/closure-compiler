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
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
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

  public static final String DEFAULT_FILENAME_PREFIX = "." + File.separator;

  private static final String MODULE_NAME_SEPARATOR = "\\$";
  private static final String MODULE_NAME_PREFIX = "module$";

  private final AbstractCompiler compiler;
  private final String filenamePrefix;
  private final boolean reportDependencies;
  private JSModule module;

  ProcessCommonJSModules(AbstractCompiler compiler, String filenamePrefix) {
    this(compiler, filenamePrefix, true);
  }

  ProcessCommonJSModules(AbstractCompiler compiler, String filenamePrefix,
      boolean reportDependencies) {
    this.compiler = compiler;
    this.filenamePrefix = filenamePrefix.endsWith(File.separator) ?
        filenamePrefix : filenamePrefix + File.separator;
    this.reportDependencies = reportDependencies;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal
        .traverse(compiler, root, new ProcessCommonJsModulesCallback());
  }

  String guessCJSModuleName(String filename) {
    return toModuleName(normalizeSourceName(filename));
  }

  /**
   * For every file that is being processed this returns the module that
   * created for it.
   */
  JSModule getModule() {
    return module;
  }

  /**
   * Turns a filename into a JS identifier that is used for moduleNames in
   * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
   * and replaces - with _. All moduleNames get a "module$" prefix.
   */
  public static String toModuleName(String filename) {
    return MODULE_NAME_PREFIX +
        filename.replaceAll("^\\." + Pattern.quote(File.separator), "")
            .replaceAll(Pattern.quote(File.separator), MODULE_NAME_SEPARATOR)
            .replaceAll("\\.js$", "").replaceAll("-", "_");
  }

  /**
   * Turn a filename into a moduleName with support for relative addressing
   * with ./ and ../ based on currentFilename;
   */
  public static String toModuleName(String requiredFilename,
      String currentFilename) {
    requiredFilename = requiredFilename.replaceAll("\\.js$", "");
    currentFilename = currentFilename.replaceAll("\\.js$", "");

    if (requiredFilename.startsWith("." + File.separator) ||
        requiredFilename.startsWith(".." + File.separator)) {
      try {
        requiredFilename = (new URI(currentFilename)).resolve(new URI(requiredFilename))
            .toString();
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
    return toModuleName(requiredFilename);
  }

  private String normalizeSourceName(String filename) {
    if (filename.indexOf(filenamePrefix) == 0) {
      filename = filename.substring(filenamePrefix.length());
    }
    return filename;
  }

  /**
   * Visits require, every "script" and special module.exports assignments.
   */
  private class ProcessCommonJsModulesCallback extends
      AbstractPostOrderCallback {

    private int scriptNodeCount = 0;
    private Set<String> modulesWithExports = Sets.newHashSet();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall() && n.getChildCount() == 2 &&
          "require".equals(n.getFirstChild().getQualifiedName()) &&
          n.getChildAtIndex(1).isString()) {
        visitRequireCall(t, n, parent);
      }

      if (n.isScript()) {
        scriptNodeCount++;
        visitScript(t, n);
      }

      if (n.isGetProp() &&
          "module.exports".equals(n.getQualifiedName())) {
        visitModuleExports(n);
      }
    }

    /**
     * Visit require calls. Emit corresponding goog.require and rewrite require
     * to be a direct reference to name of require module.
     */
    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      String moduleName = toModuleName(require.getChildAtIndex(1).getString(),
          normalizeSourceName(t.getSourceName()));
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
      String moduleName = guessCJSModuleName(normalizeSourceName(script
          .getSourceFileName()));
      script.addChildToFront(IR.var(IR.name(moduleName), IR.objectlit())
          .copyInformationFromForTree(script));
      if (reportDependencies) {
        CompilerInput ci = t.getInput();
        ci.addProvide(moduleName);
        JSModule m = new JSModule(moduleName);
        m.addAndOverrideModule(ci);
        module = m;
      }
      script.addChildToFront(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("provide")),
              IR.string(moduleName))).copyInformationFromForTree(script));

      emitOptionalModuleExportsOverride(script, moduleName);

      // Rename vars to not conflict in global scope.
      NodeTraversal.traverse(compiler, script, new SuffixVarsCallback(
          moduleName));

      compiler.reportCodeChange();
    }

    /**
     * Emit <code>if (moduleName.module$exports) {
     *    moduleName = moduleName.module$export;
     * }</code> at end of file.
     */
    private void emitOptionalModuleExportsOverride(Node script,
        String moduleName) {
      if (!modulesWithExports.contains(moduleName)) {
        return;
      }

      Node moduleExportsProp = IR.getprop(IR.name(moduleName),
          IR.string("module$exports"));
      script.addChildToBack(IR.ifNode(
          moduleExportsProp,
          IR.block(IR.exprResult(IR.assign(IR.name(moduleName),
              moduleExportsProp.cloneTree())))).copyInformationFromForTree(
          script));
    }

    /**
     * Rewrite module.exports to moduleName.module$exports.
     */
    private void visitModuleExports(Node prop) {
      String moduleName = guessCJSModuleName(prop.getSourceFileName());
      Node module = prop.getChildAtIndex(0);
      module.putProp(Node.ORIGINALNAME_PROP, "module");
      module.setString(moduleName);
      Node exports = prop.getChildAtIndex(1);
      exports.putProp(Node.ORIGINALNAME_PROP, "exports");
      exports.setString("module$exports");
      modulesWithExports.add(moduleName);
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

    private static final String EXPORTS = "exports";

    private final String suffix;

    SuffixVarsCallback(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if (suffix.equals(name)) {
          return;
        }
        if (EXPORTS.equals(name)) {
          n.setString(suffix);
          n.putProp(Node.ORIGINALNAME_PROP, EXPORTS);
        } else {
          Scope.Var var = t.getScope().getVar(name);
          if (var != null && var.isGlobal()) {
            n.setString(name + "$$" + suffix);
            n.putProp(Node.ORIGINALNAME_PROP, name);
          }
        }
      }
    }
  }
}
