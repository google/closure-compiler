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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites a ES6 module into a form that can be safely concatenated.
 * Note that we treat a file as an ES6 module if it has at least one import or
 * export statement.
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class ProcessEs6Modules extends AbstractPostOrderCallback {
  private static final String DEFAULT_EXPORT_NAME = "$jscompDefaultExport";

  private final ES6ModuleLoader loader;

  private final Compiler compiler;
  private int scriptNodeCount = 0;

  /**
   * Maps exported names to their names in current module.
   */
  private Map<String, NameNodePair> exportMap = new LinkedHashMap<>();

  /**
   * Maps symbol names to a pair of (moduleName, originalName). The original
   * name is the name of the symbol exported by the module. This is required
   * because we want to be able to update the original property on the module
   * object. Eg: "import {foo as f} from 'm'" maps 'f' to the pair ('m', 'foo').
   * In the entry for "import * as ns", the originalName will be the empty string.
   */
  private Map<String, ModuleOriginalNamePair> importMap = new HashMap<>();

  private Set<String> classes = new HashSet<>();
  private Set<String> typedefs = new HashSet<>();

  private Set<String> alreadyRequired = new HashSet<>();

  private boolean isEs6Module;

  private boolean reportDependencies;

  /**
   * Creates a new ProcessEs6Modules instance which can be used to rewrite
   * ES6 modules to a concatenable form.
   *
   * @param compiler The compiler
   * @param loader The module loader which is used to locate ES6 modules
   * @param reportDependencies Whether the rewriter should report dependency
   *     information to the Closure dependency manager. This needs to be true
   *     if we want to sort ES6 module inputs correctly. Note that goog.provide
   *     and goog.require calls will still be generated if this argument is
   *     false.
   */
  public ProcessEs6Modules(Compiler compiler, ES6ModuleLoader loader,
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
    URI loadAddress = loader.locateEs6Module(importName, t.getInput());
    if (loadAddress == null) {
      compiler.report(t.makeError(importDecl, ES6ModuleLoader.LOAD_ERROR, importName));
      return;
    }

    String moduleName = ES6ModuleLoader.toModuleName(loadAddress);
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

    Node script = NodeUtil.getEnclosingScript(parent);
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

  private void visitExport(NodeTraversal t, Node export, Node parent) {
    if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
      // export default

      // If the thing being exported is a class or function that has a name,
      // extract it from the export statement, so that it can be referenced
      // from within the module.
      //
      //   export default class X {} -> class X {}; ... moduleName.default = X;
      //   export default function X() {} -> function X() {}; ... moduleName.default = X;
      //
      // Otherwise, create a local variable for it and export that.
      //
      //   export default 'someExpression'
      //     ->
      //   var $jscompDefaultExport = 'someExpression';
      //   ...
      //   moduleName.default = $jscompDefaultExport;
      Node child = export.getFirstChild();
      String name = null;

      if (child.isFunction()) {
        name = NodeUtil.getFunctionName(child);
      } else if (child.isClass()) {
        name = NodeUtil.getClassName(child);
      }

      if (name != null) {
        Node decl = child.cloneTree();
        decl.setJSDocInfo(export.getJSDocInfo());
        parent.replaceChild(export, decl);
        exportMap.put("default", new NameNodePair(name, child));
      } else {
        Node var = IR.var(IR.name(DEFAULT_EXPORT_NAME), export.removeFirstChild());
        var.useSourceInfoIfMissingFromForTree(export);
        parent.replaceChild(export, var);
        exportMap.put("default", new NameNodePair(DEFAULT_EXPORT_NAME, child));
      }
    } else if (export.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      //   export * from 'moduleIdentifier';
      compiler.report(JSError.make(export, Es6ToEs3Converter.CANNOT_CONVERT_YET,
          "Wildcard export"));
    } else if (export.getChildCount() == 2) {
      //   export {x, y as z} from 'moduleIdentifier';
      Node moduleIdentifier = export.getLastChild();
      Node importNode = new Node(Token.IMPORT, moduleIdentifier.cloneNode());
      importNode.copyInformationFrom(export);
      parent.addChildBefore(importNode, export);
      visit(t, importNode, parent);

      URI loadAddress = loader.locateEs6Module(moduleIdentifier.getString(), t.getInput());
      if (loadAddress == null) {
        compiler.report(t.makeError(moduleIdentifier, ES6ModuleLoader.LOAD_ERROR,
            moduleIdentifier.getString()));
        return;
      }
      String moduleName = ES6ModuleLoader.toModuleName(loadAddress);

      for (Node exportSpec : export.getFirstChild().children()) {
        String nameFromOtherModule = exportSpec.getFirstChild().getString();
        String exportedName = exportSpec.getLastChild().getString();
        exportMap.put(exportedName,
            new NameNodePair(moduleName + "." + nameFromOtherModule, exportSpec));
      }
      parent.removeChild(export);
    } else {
      if (export.getFirstChild().getType() == Token.EXPORT_SPECS) {
        //     export {Foo};
        for (Node exportSpec : export.getFirstChild().children()) {
          Node origName = exportSpec.getFirstChild();
          exportMap.put(
              exportSpec.getChildCount() == 2
                  ? exportSpec.getLastChild().getString()
                  : origName.getString(),
              new NameNodePair(origName.getString(), exportSpec));
        }
        parent.removeChild(export);
      } else {
        //    export var Foo;
        //    export function Foo() {}
        // etc.
        Node declaration = export.getFirstChild();
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
            exportMap.put(name, new NameNodePair(name, maybeName));
          }

          // If the declaration declares a new type, create annotations for
          // the type checker.
          // TODO(moz): Currently we only record ES6 classes and typedefs,
          // need to handle other kinds of type declarations too.
          if (declaration.isClass()) {
            classes.add(name);
          }
          if (export.getJSDocInfo() != null && export.getJSDocInfo().hasTypedefType()) {
            typedefs.add(name);
          }
        }
        declaration.setJSDocInfo(export.getJSDocInfo());
        export.setJSDocInfo(null);
        parent.replaceChild(export, declaration.detachFromParent());
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

    URI normalizedAddress = loader.normalizeInputAddress(t.getInput());
    String moduleName = ES6ModuleLoader.toModuleName(normalizedAddress);

    if (!exportMap.isEmpty()) {
      // Creates an export object for this module.
      // var moduleName = {};
      Node objectlit = IR.objectlit();
      Node varNode = IR.var(IR.name(moduleName), objectlit)
          .useSourceInfoIfMissingFromForTree(script);
      script.addChildToBack(varNode);
    }

    for (Map.Entry<String, NameNodePair> entry : exportMap.entrySet()) {
      String exportedName = entry.getKey();
      String withSuffix = entry.getValue().name;
      Node nodeForSourceInfo = entry.getValue().nodeForSourceInfo;
      Node getProp = IR.getprop(IR.name(moduleName), IR.string(exportedName));

      if (typedefs.contains(exportedName)) {
        // /** @typedef {foo} */
        // moduleName.foo;
        JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
        JSTypeExpression typeExpr = new JSTypeExpression(
            IR.string(exportedName), script.getSourceFileName());
        builder.recordTypedef(typeExpr);
        JSDocInfo info = builder.build();
        getProp.setJSDocInfo(info);
        Node exprResult = IR.exprResult(getProp)
            .useSourceInfoIfMissingFromForTree(nodeForSourceInfo);
        script.addChildToBack(exprResult);
      } else {
        //   moduleName.foo = foo;
        // with a @const annotation if needed.
        Node assign = IR.assign(
            getProp,
            NodeUtil.newQName(compiler, withSuffix));
        Node exprResult = IR.exprResult(assign)
            .useSourceInfoIfMissingFromForTree(nodeForSourceInfo);
        if (classes.contains(exportedName)) {
          JSDocInfoBuilder builder = new JSDocInfoBuilder(true);
          builder.recordConstancy();
          JSDocInfo info = builder.build();
          assign.setJSDocInfo(info);
        }
        script.addChildToBack(exprResult);
      }
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
    script.setJSDocInfo(jsDocInfo.build());

    exportMap.clear();
    compiler.reportCodeChange();
  }

  private void rewriteRequires(Node script) {
    NodeTraversal.traverse(compiler, script, new NodeTraversal.AbstractShallowCallback() {
      @Override
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
   * Traverses a node tree and
   * <ol>
   *   <li>Appends a suffix to all global variable names defined in this module.
   *   <li>Changes references to imported values to be property accesses on the
   *    imported module object.
   * </ol>
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

        Var var = t.getScope().getVar(name);
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
          URI loadAddress = loader.locateEs6Module(moduleName, t.getInput());
          if (loadAddress == null) {
            compiler.report(t.makeError(
                typeNode, ES6ModuleLoader.LOAD_ERROR, moduleName));
            return;
          }

          String globalModuleName = ES6ModuleLoader.toModuleName(loadAddress);
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
          Var var = t.getScope().getVar(baseName);
          if (var != null && var.isGlobal()) {
            typeNode.setString(baseName + "$$" + suffix + rest);
          } else if (var == null && importMap.containsKey(baseName)) {
            ModuleOriginalNamePair pair = importMap.get(baseName);
            if (pair.originalName.isEmpty()) {
              typeNode.setString(pair.module + rest);
            } else {
              typeNode.setString(baseName + "$$" + pair.module + rest);
            }
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

  private static class ModuleOriginalNamePair {
    private String module;
    private String originalName;

    private ModuleOriginalNamePair(String module, String originalName) {
      this.module = module;
      this.originalName = originalName;
    }

    @Override
    public String toString() {
      return "(" + module + ", " + originalName + ")";
    }
  }

  private static class NameNodePair {
    final String name;
    final Node nodeForSourceInfo;

    private NameNodePair(String name, Node nodeForSourceInfo) {
      this.name = name;
      this.nodeForSourceInfo = nodeForSourceInfo;
    }

    @Override
    public String toString() {
      return "(" + name + ", " + nodeForSourceInfo + ")";
    }
  }
}
