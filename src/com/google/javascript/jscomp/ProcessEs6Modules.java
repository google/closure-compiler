/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.ProcessCommonJSModules.FindGoogProvideOrGoogModule;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rewrites a ES6 module into a form that can be safely concatenated.
 * Note that we treat a file as an ES6 module if it has at least one import or
 * export statement.
 *
 * @author moz@google.com (Michael Zhou)
 */
public class ProcessEs6Modules extends AbstractPostOrderCallback {
  private static final String MODULE_SLASH = ES6ModuleLoader.MODULE_SLASH;

  private static final String MODULE_NAME_SEPARATOR = "\\$";
  private static final String MODULE_NAME_PREFIX = "module$";
  private static final String DEFAULT_EXPORT_NAME = "$jscompDefaultExport";

  private final ES6ModuleLoader loader;

  private final Compiler compiler;
  private int scriptNodeCount = 0;

  /**
   * Maps exported names to their names in current module.
   */
  private Map<String, String> exportMap = new LinkedHashMap<>();

  /**
   * Maps symbol names to a pair of <moduleName, originalName>. The original
   * name is the name of the symbol exported by the module. This is required
   * because we want to be able to update the original property on the module
   * object. Eg: "import {foo as f} from 'm'" maps 'f' to the pair <'m', 'foo'>.
   */
  private Map<String, ModuleOriginalNamePair> importMap = new HashMap<>();

  private Set<String> types = new LinkedHashSet<>();

  private Set<String> alreadyRequired = new HashSet<>();

  private boolean isEs6Module;

  private boolean reportDependencies;

  /**
   * @param reportDependencies Whether the rewriter should report dependency
   *     information to the Closure dependency manager. This needs to be true
   *     if we want to sort ES6 module inputs correctly. Note that goog.provide
   *     and goog.require calls will still be generated if this argument is
   *     false.
   */
  ProcessEs6Modules(Compiler compiler, ES6ModuleLoader loader,
      boolean reportDependencies) {
    this.compiler = compiler;
    this.loader = loader;
    this.reportDependencies = reportDependencies;
  }

  public void processFile(Node root) {
    FindGoogProvideOrGoogModule finder = new FindGoogProvideOrGoogModule();
    NodeTraversal.traverse(compiler, root, finder);
    if (finder.isFound()) {
      return;
    }
    isEs6Module = false;
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isImport()) {
      isEs6Module = true;
      visitImport(t, n, parent);
    } else if (n.isExport()) {
      isEs6Module = true;
      visitExport(t, n, parent);
    } else if (n.isScript()) {
      scriptNodeCount++;
      visitScript(t, n);
    }
  }

  private void visitImport(NodeTraversal t, Node importDecl, Node parent) {
    String importName = importDecl.getLastChild().getString();
    String loadAddress = loader.locate(importName, t.getInput());
    try {
      loader.load(loadAddress);
    } catch (ES6ModuleLoader.LoadFailedException e) {
      compiler.report(t.makeError(
          importDecl, ES6ModuleLoader.LOAD_ERROR, importName));
    }

    String moduleName = toModuleName(loadAddress);
    Set<String> namesToRequire = new LinkedHashSet<>();
    for (Node child : importDecl.children()) {
      if (child.isEmpty() || child.isString()) {
        continue;
      } else if (child.isName()) { // import a from "mod"
        importMap.put(child.getString(),
            new ModuleOriginalNamePair(moduleName, "default"));
        namesToRequire.add("default");
      } else if (child.getType() == Token.IMPORT_SPECS) {
        for (Node grandChild : child.children()) {
          String origName = grandChild.getFirstChild().getString();
          namesToRequire.add(origName);
          if (grandChild.getChildCount() == 2) { // import {a as foo} from "mod"
            importMap.put(
                grandChild.getLastChild().getString(),
                new ModuleOriginalNamePair(moduleName, origName));
          } else { // import {a} from "mod"
            importMap.put(
                origName,
                new ModuleOriginalNamePair(moduleName, origName));
          }
        }
      } else {
        // import * as ns from "mod"
        Preconditions.checkState(child.getType() == Token.IMPORT_STAR,
            "Expected an IMPORT_STAR node, but was: %s", child);
        importMap.put(
            child.getString(),
            new ModuleOriginalNamePair(moduleName, ""));
      }
    }

    Node script = NodeUtil.getEnclosingType(parent, Token.SCRIPT);
    // Emit goog.require call for the module.
    if (alreadyRequired.add(moduleName)) {
      Node require = IR.exprResult(
          IR.call(NodeUtil.newQName(compiler, "goog.require"), IR.string(moduleName)));
      require.copyInformationFromForTree(importDecl);
      script.addChildToFront(require);
      if (reportDependencies) {
        t.getInput().addRequire(moduleName);
      }
    }

    parent.removeChild(importDecl);
    compiler.reportCodeChange();
  }

  private void visitExport(NodeTraversal t, Node n, Node parent) {
    if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
      // export default var Foo;
      Node var = IR.var(IR.name(DEFAULT_EXPORT_NAME), n.removeFirstChild());
      var.useSourceInfoIfMissingFromForTree(n);
      var.setJSDocInfo(n.getJSDocInfo());
      n.setJSDocInfo(null);
      n.getParent().replaceChild(n, var);
      exportMap.put("default", DEFAULT_EXPORT_NAME);
    } else if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      //   export * from 'moduleIdentifier';
      compiler.report(JSError.make(n, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Wildcard export"));
    } else if (n.getChildCount() == 2) {
      //   export {x, y as z} from 'moduleIdentifier';
      Node moduleIdentifier = n.getLastChild();
      Node importNode = new Node(Token.IMPORT, moduleIdentifier.cloneNode());
      importNode.copyInformationFrom(n);
      parent.addChildBefore(importNode, n);
      visit(t, importNode, parent);

      String loadAddress = loader.locate(moduleIdentifier.getString(), t.getInput());
      String moduleName = toModuleName(loadAddress);

      for (Node exportSpec : n.getFirstChild().children()) {
        String nameFromOtherModule = exportSpec.getFirstChild().getString();
        String exportedName = exportSpec.getLastChild().getString();
        exportMap.put(exportedName, moduleName + "." + nameFromOtherModule);
      }
      parent.removeChild(n);
    } else {
      if (n.getFirstChild().getType() == Token.EXPORT_SPECS) {
        //     export {Foo};
        for (Node exportSpec : n.getFirstChild().children()) {
          Node origName = exportSpec.getFirstChild();
          exportMap.put(
              exportSpec.getChildCount() == 2
                  ? exportSpec.getLastChild().getString()
                  : origName.getString(),
              origName.getString());
        }
        parent.removeChild(n);
      } else {
        //    export var Foo;
        //    export function Foo() {}
        // etc.
        Node declaration = n.getFirstChild();
        for (int i = 0; i < declaration.getChildCount(); i++) {
          Node maybeName = declaration.getChildAtIndex(i);
          if (!maybeName.isName()) {
            break;
          }
          // Break out on "B" in "class A extends B"
          if (declaration.isClass() && i > 0) {
            break;
          }
          String name = maybeName.getString();
          Var v = t.getScope().getVar(name);
          if (v == null || v.isGlobal()) {
            exportMap.put(name, name);
          }

          // If the declaration declares a new type, we need to create @typedef
          // annotations for the type checker later.
          // TODO(moz): Currently we only record ES6 classes, need to handle
          // other kinds of type declarations too.
          if (declaration.isClass()) {
            types.add(name);
          }
        }
        declaration.setJSDocInfo(n.getJSDocInfo());
        n.setJSDocInfo(null);
        parent.replaceChild(n, declaration.detachFromParent());
      }
      compiler.reportCodeChange();
    }
  }

  private void visitScript(NodeTraversal t, Node script) {
    if (!isEs6Module) {
      return;
    }
    Preconditions.checkArgument(scriptNodeCount == 1,
        "ProcessEs6Modules supports only one invocation per "
        + "CompilerInput / script node");

    // rewriteRequires is here (rather than being part of the main visit()
    // method, because we only want to rewrite the requires if this is an
    // ES6 module.
    rewriteRequires(script);

    String moduleName = toModuleName(loader.getLoadAddress(t.getInput()));

    if (!exportMap.isEmpty()) {
      // Creates an export object for this module.
      // var moduleName = {};
      Node objectlit = IR.objectlit();
      Node varNode = IR.var(IR.name(moduleName), objectlit)
          .useSourceInfoIfMissingFromForTree(script);
      script.addChildToBack(varNode);
    }

    // moduleName.foo = foo;
    for (Map.Entry<String, String> entry : exportMap.entrySet()) {
      String exportedName = entry.getKey();
      String withSuffix = entry.getValue();
      Node getProp = IR.getprop(IR.name(moduleName), IR.string(exportedName));
      Node assign = IR.assign(
          getProp,
          NodeUtil.newQName(compiler, withSuffix));
      Node exprResult = IR.exprResult(assign)
          .useSourceInfoIfMissingFromForTree(script);
      // Hack: Remove this annotation, once type inference improves.
      if (types.contains(exportedName)) {
        JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
        builder.recordConstancy();
        JSDocInfo info = builder.build(assign);
        assign.setJSDocInfo(info);
      }
      script.addChildToBack(exprResult);
    }

    // Rename vars to not conflict in global scope.
    NodeTraversal.traverse(compiler, script, new RenameGlobalVars(moduleName));

    if (!exportMap.isEmpty()) {
      // Add goog.provide call.
      Node googProvide = IR.exprResult(
          IR.call(NodeUtil.newQName(compiler, "goog.provide"),
              IR.string(moduleName)));
      script.addChildToFront(googProvide.copyInformationFromForTree(script));
      if (reportDependencies) {
        t.getInput().addProvide(moduleName);
      }
    }

    JSDocInfoBuilder jsDocInfo = script.getJSDocInfo() == null
        ? new JSDocInfoBuilder(false)
        : JSDocInfoBuilder.copyFrom(script.getJSDocInfo());
    if (!jsDocInfo.isPopulatedWithFileOverview()) {
      jsDocInfo.recordFileOverview("");
    }
    // Don't check provides and requires, since most of them are auto-generated.
    jsDocInfo.recordSuppressions(ImmutableSet.of("missingProvide", "missingRequire"));
    script.setJSDocInfo(jsDocInfo.build(script));

    exportMap.clear();
    compiler.reportCodeChange();
  }

  private void rewriteRequires(Node script) {
    NodeTraversal.traverse(compiler, script, new NodeTraversal.AbstractShallowCallback() {
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isCall()
            && n.getFirstChild().matchesQualifiedName("goog.require")
            && parent.isName()) {
          visitRequire(n, parent);
        }
      }

      private void visitRequire(Node requireCall, Node parent) {
        // Rewrite
        //
        //   var foo = goog.require('bar.foo');
        //
        // to
        //
        //   goog.require('bar.foo');
        //   var foo = bar.foo;

        String namespace = requireCall.getLastChild().getString();

        Node replacement = NodeUtil.newQName(compiler, namespace).srcrefTree(requireCall);
        parent.replaceChild(requireCall, replacement);
        Node varNode = parent.getParent();
        varNode.getParent().addChildBefore(
            IR.exprResult(requireCall).srcrefTree(requireCall),
            varNode);
      }
    });
  }

  /**
   * Turns a filename into a JS identifier that is used for moduleNames in
   * rewritten code. For example, "./foo.js" transformed to "foo".
   */
  public static String toModuleName(String filename) {
    return MODULE_NAME_PREFIX
        + filename.replaceAll("^\\." + Pattern.quote(MODULE_SLASH), "")
            .replaceAll(Pattern.quote(MODULE_SLASH), MODULE_NAME_SEPARATOR)
            .replaceAll(Pattern.quote("\\"), MODULE_NAME_SEPARATOR)
            .replaceAll("\\.js$", "")
            .replaceAll("-", "_")
            .replaceAll("\\.", "");
  }

  /**
   * Traverses a node tree and
   * 1. Appends a suffix to all global variable names defined in this module.
   * 2. Changes references to imported values to be property accesses on the
   *    imported module object.
   */
  private class RenameGlobalVars extends AbstractPostOrderCallback {
    private final String suffix;

    RenameGlobalVars(String suffix) {
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

        Scope.Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          // Avoid polluting the global namespace.
          n.setString(name + "$$" + suffix);
          n.putProp(Node.ORIGINALNAME_PROP, name);
        } else if (var == null && importMap.containsKey(name)) {
          // Change to property access on the imported module object.
          if (parent.isCall() && parent.getFirstChild() == n) {
            parent.putBooleanProp(Node.FREE_CALL, false);
          }
          ModuleOriginalNamePair pair = importMap.get(name);
          if (pair.originalName.isEmpty()) {
            n.getParent().replaceChild(
                n, IR.name(pair.module).useSourceInfoIfMissingFromForTree(n));
          } else {
            n.getParent().replaceChild(n,
                IR.getprop(IR.name(pair.module), IR.string(pair.originalName))
                .useSourceInfoIfMissingFromForTree(n));
          }
        }
      }
    }

    /**
     * Replace type name references. Change short names to fully qualified names
     * with namespace prefixes. Eg: {Foo} becomes {module$test.Foo}.
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
            compiler.report(t.makeError(
                typeNode, ES6ModuleLoader.LOAD_ERROR, moduleName));
            return;
          }

          String globalModuleName = toModuleName(loadAddress);
          typeNode.setString(
              localTypeName == null
                  ? globalModuleName
                  : globalModuleName + localTypeName);
        } else {
          List<String> splitted = Splitter.on('.').limit(2).splitToList(name);
          String baseName = splitted.get(0);
          String rest = "";
          if (splitted.size() == 2) {
            rest = "." + splitted.get(1);
          }
          Scope.Var var = t.getScope().getVar(baseName);
          if (var != null && var.isGlobal()) {
            typeNode.setString(baseName + "$$" + suffix + rest);
          } else if (var == null && importMap.containsKey(baseName)) {
            ModuleOriginalNamePair pair = importMap.get(baseName);
            typeNode.setString(baseName + "$$" + pair.module + rest);
          }
          typeNode.putProp(Node.ORIGINALNAME_PROP, name);
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null;
           child = child.getNext()) {
        fixTypeNode(t, child);
      }
      compiler.reportCodeChange();
    }
  }

  private class ModuleOriginalNamePair {
    private String module;
    private String originalName;

    private ModuleOriginalNamePair(String module, String originalName) {
      this.module = module;
      this.originalName = originalName;
    }
  }
}
