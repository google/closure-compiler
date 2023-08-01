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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Finds all references to global symbols in a different output chunk and add ES Module imports and
 * exports for them.
 *
 * <pre><code>
 * // chunk1.js
 * var a = 1;
 * function b() { return a }
 * </code></pre>
 *
 * <pre><code>
 * // chunk2.js
 * console.log(a);
 * </code></pre>
 *
 * becomes
 *
 * <pre><code>
 * // chunk1.js
 * var a = 1;
 * var b = function b() { return a };
 * export {a};
 * </code></pre>
 *
 * <pre><code>
 * // chunk2.js
 * import {a} from './chunk1.js';
 * console.log(a);
 * </code></pre>
 *
 * This allows splitting code into es modules that depend on each other's symbols, without using a
 * global namespace or polluting the global scope.
 */
final class ConvertChunksToESModules implements CompilerPass {
  private enum ImportType {
    STATIC,
    DYNAMIC
  }

  private final AbstractCompiler compiler;
  private final Map<JSChunk, Set<String>> crossChunkExports = new LinkedHashMap<>();
  private final Map<JSChunk, Map<JSChunk, Set<String>>> crossChunkImports = new LinkedHashMap<>();
  private final List<Node> dynamicImportCallbacks = new ArrayList<>();

  static final String DYNAMIC_IMPORT_CALLBACK_FN = "jscomp$DynamicImportCallback";

  static final DiagnosticType ASSIGNMENT_TO_IMPORT =
      DiagnosticType.error(
          "JSC_IMPORT_ASSIGN", "Imported symbol \"{0}\" in chunk \"{1}\" cannot be assigned");

  static final DiagnosticType UNABLE_TO_COMPUTE_RELATIVE_PATH =
      DiagnosticType.error(
          "JSC_UNABLE_TO_COMPUTE_RELATIVE_PATH",
          "Unable to compute relative import path from \"{0}\" to \"{1}\"");

  static final DiagnosticType UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK =
      DiagnosticType.error(
          "JSC_UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK",
          "Dynamic import callback encountered wih an invalid format.{0}");

  /**
   * Constructor for the ConvertChunksToESModules compiler pass.
   *
   * @param compiler The JSCompiler, for reporting code changes.
   */
  ConvertChunksToESModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    // Find global names that are used in more than one chunk. Those that
    // are have to have import and export statements added.
    NodeTraversal.traverse(compiler, root, new FindCrossChunkReferences());

    // Force every output chunk to parse as an ES Module. If a chunk has no imports and
    // no exports, add an empty export list to generate an empty export statement:
    // example: export {};
    for (JSChunk chunk : compiler.getModuleGraph().getAllChunks()) {
      if (!crossChunkExports.containsKey(chunk)
          && !crossChunkImports.containsKey(chunk)
          && !chunk.getInputs().isEmpty()) {
        crossChunkExports.put(chunk, new LinkedHashSet<>());
      }
    }

    convertChunkSourcesToModules();
    addExportStatements();
    addImportStatements();
    rewriteDynamicImportCallbacks();

    NodeUtil.addFeatureToAllScripts(root, FeatureSet.Feature.MODULES, compiler);
  }

  /**
   * Move all code in a chunk into the first input and mark it as an ESModule. At this point in the
   * compilation, all input files should be scripts.
   */
  private void convertChunkSourcesToModules() {
    for (JSChunk chunk : compiler.getModuleGraph().getAllChunks()) {
      if (chunk.getInputs().isEmpty()) {
        continue;
      }

      CompilerInput firstInput = null;
      for (CompilerInput input : chunk.getInputs()) {
        Node astRoot = input.getAstRoot(compiler);
        FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(astRoot);
        checkState(!scriptFeatures.contains(FeatureSet.ES2015_MODULES));
        if (firstInput == null) {
          firstInput = input;
          scriptFeatures = scriptFeatures.union(FeatureSet.ES2015_MODULES);
          astRoot.putProp(Node.FEATURE_SET, scriptFeatures);
          Node moduleBody = new Node(Token.MODULE_BODY);
          moduleBody.srcref(astRoot);
          moduleBody.addChildrenToFront(astRoot.removeChildren());
          astRoot.addChildToFront(moduleBody);
          compiler.reportChangeToEnclosingScope(moduleBody);
        } else {
          Node firstInputAstRoot = firstInput.getAstRoot(compiler);
          FeatureSet firstInputScriptFeatures = NodeUtil.getFeatureSetOfScript(firstInputAstRoot);
          FeatureSet combinedFeatureSet =
              firstInputScriptFeatures.union(NodeUtil.getFeatureSetOfScript(astRoot));
          astRoot.putProp(Node.FEATURE_SET, combinedFeatureSet);
          Node moduleBody = firstInputAstRoot.getFirstChild();
          checkState(moduleBody != null && moduleBody.isModuleBody());
          moduleBody.addChildrenToBack(astRoot.removeChildren());
          compiler.reportChangeToEnclosingScope(firstInputAstRoot);
          compiler.reportChangeToChangeScope(astRoot);
        }
      }
    }
  }

  /** Add export statements to chunks */
  private void addExportStatements() {
    for (Map.Entry<JSChunk, Set<String>> jsModuleExports : crossChunkExports.entrySet()) {
      CompilerInput firstInput = jsModuleExports.getKey().getFirst();
      Node moduleBody = firstInput.getAstRoot(compiler).getFirstChild();
      checkState(moduleBody != null && moduleBody.isModuleBody());
      Node exportSpecs = new Node(Token.EXPORT_SPECS);
      for (String name : jsModuleExports.getValue()) {
        Node exportSpec = new Node(Token.EXPORT_SPEC);
        exportSpec.addChildToFront(IR.name(name));
        exportSpec.addChildToFront(IR.name(name));
        exportSpec.putIntProp(Node.IS_SHORTHAND_PROPERTY, 1);
        exportSpecs.addChildToBack(exportSpec);
      }
      Map<JSChunk, Set<String>> importsByChunk = crossChunkImports.get(jsModuleExports.getKey());

      // Force the chunk to parse as a module by adding an empty export spec when no actual
      // static imports or exports exist
      if (exportSpecs.hasChildren() || importsByChunk == null || importsByChunk.isEmpty()) {
        Node export = IR.export(exportSpecs).srcrefTree(moduleBody);
        moduleBody.addChildToBack(export);
        compiler.reportChangeToEnclosingScope(moduleBody);
      }
    }
  }

  private static String getChunkName(JSChunk chunk) {
    return chunk.getName() + ".js";
  }

  /** Add import statements to chunks */
  private void addImportStatements() {
    for (Map.Entry<JSChunk, Map<JSChunk, Set<String>>> chunkImportsEntry :
        crossChunkImports.entrySet()) {
      ArrayList<Node> importStatements = new ArrayList<>();
      JSChunk importingChunk = chunkImportsEntry.getKey();
      CompilerInput firstInput = importingChunk.getFirst();
      Node moduleBody = firstInput.getAstRoot(compiler).getFirstChild();
      checkState(moduleBody != null && moduleBody.isModuleBody());

      // For each distinct chunk where a referenced symbol is defined, create an import statement
      // referencing the names.
      for (Map.Entry<JSChunk, Set<String>> importsByChunk :
          chunkImportsEntry.getValue().entrySet()) {
        Node importSpecs = new Node(Token.IMPORT_SPECS);
        for (String name : importsByChunk.getValue()) {
          Node importSpec = new Node(Token.IMPORT_SPEC);
          importSpec.addChildToFront(IR.name(name));
          importSpec.addChildToFront(IR.name(name));
          importSpec.putIntProp(Node.IS_SHORTHAND_PROPERTY, 1);
          importSpecs.addChildToBack(importSpec);
        }
        Node importStatement = new Node(Token.IMPORT);
        JSChunk exportingChunk = importsByChunk.getKey();
        String importPath = getChunkName(exportingChunk);
        try {
          importPath =
              ModuleLoader.relativePathFrom(
                  getChunkName(importingChunk), getChunkName(exportingChunk));
        } catch (IllegalArgumentException e) {
          compiler.report(
              JSError.make(
                  moduleBody,
                  UNABLE_TO_COMPUTE_RELATIVE_PATH,
                  getChunkName(importingChunk),
                  getChunkName(exportingChunk)));
        }
        importStatement.addChildToFront(IR.string(importPath));
        if (importSpecs.hasChildren()) {
          importStatement.addChildToFront(importSpecs);
        } else {
          // Empty import of a dependent chunk for side effects
          // import './chunk.js'
          importStatement.addChildToFront(IR.empty());
        }
        importStatement.addChildToFront(IR.empty());

        importStatement.srcrefTree(moduleBody);
        importStatements.add(0, importStatement);
      }
      for (Node importStatement : importStatements) {
        moduleBody.addChildToFront(importStatement);
      }
      compiler.reportChangeToEnclosingScope(moduleBody);
    }
  }

  /** Find and return the module namespace name node in a dynamic import callback function */
  public static @Nullable Node getDynamicImportCallbackModuleNamespace(
      AbstractCompiler compiler, Node call) {
    checkState(call.isCall());
    Node callbackFn = NodeUtil.getArgumentForCallOrNew(call, 0);
    if (callbackFn == null
        || !callbackFn.isFunction()
        || NodeUtil.getFunctionParameters(callbackFn).hasChildren()) {
      compiler.report(
          JSError.make(
              call,
              UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK,
              " Unable to find valid callback function."));
      return null;
    }
    Node callbackBody = NodeUtil.getFunctionBody(callbackFn);

    // The callback body should have a single statement that returns a name.
    // Support both standard and arrow function semantics
    if (callbackBody.isName()) {
      return callbackBody;
    } else if (callbackBody.isBlock()
        && callbackBody.hasOneChild()
        && callbackBody.getFirstChild().isReturn()
        && callbackBody.getFirstChild().hasOneChild()
        && callbackBody.getFirstFirstChild().isName()) {
      return callbackBody.getFirstFirstChild();
    }
    compiler.report(
        JSError.make(
            call,
            UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK,
            " Unable to find valid namespace reference."));
    return null;
  }

  /**
   * The RewriteDynamicImports pass adds direct references to the original input module namespace
   * and wraps the callback in a special external function call so that this pass can recognize
   * them.
   *
   * <p>Example:
   *
   * <p>import('./chunk0.js').then(jscomp$DynamicImportCallback(() => module$input0));
   *
   * <p>We need to remove the special external function call and update the original module
   * namespace reference to be a property of the chunk namespace.
   *
   * <p>import('./chunk0.js').then(($) => $.module$input0);
   */
  private void rewriteDynamicImportCallbacks() {
    AstFactory astFactory = compiler.createAstFactory();
    for (Node dynamicImportCallback : dynamicImportCallbacks) {
      checkState(dynamicImportCallback.isCall());
      Node moduleNamespace =
          getDynamicImportCallbackModuleNamespace(compiler, dynamicImportCallback);
      if (moduleNamespace == null) {
        continue;
      }
      Node callbackFn = NodeUtil.getArgumentForCallOrNew(dynamicImportCallback, 0);
      Node callbackParamList = NodeUtil.getFunctionParameters(callbackFn);
      Node importNamespaceParam = astFactory.createNameWithUnknownType("$").srcref(moduleNamespace);
      callbackParamList.addChildToFront(importNamespaceParam);
      compiler.reportChangeToEnclosingScope(importNamespaceParam);

      Node namespaceGetprop =
          astFactory.createGetPropWithUnknownType(
              astFactory.createNameWithUnknownType("$").srcref(moduleNamespace),
              moduleNamespace.getString());

      moduleNamespace.replaceWith(namespaceGetprop);
      compiler.reportChangeToEnclosingScope(namespaceGetprop);
      Node innerCallback = NodeUtil.getArgumentForCallOrNew(dynamicImportCallback, 0).detach();
      dynamicImportCallback.replaceWith(innerCallback);
      compiler.reportChangeToEnclosingScope(innerCallback);
    }
  }

  /** Find names in a module that are defined in a different module. */
  private class FindCrossChunkReferences extends AbstractPreOrderCallback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript(t, n);
        return true;
      } else if (n.isCall()) {
        return visitCallAndTraverse(t, n);
      } else if (n.isName()) {
        visitName(t, n, ImportType.STATIC);
        return true;
      }
      return true;
    }

    public void visitScript(NodeTraversal t, Node script) {
      checkState(script.isScript());
      JSChunk chunk = t.getChunk();
      ImmutableList<JSChunk> chunkDependencies = chunk.getDependencies();

      crossChunkExports.putIfAbsent(chunk, new LinkedHashSet<>());

      // Ensure every chunk dependency is explicitly listed with an import
      // Dependent chunks may have side effects even if there isn't an explicit name reference
      if (!chunkDependencies.isEmpty()) {
        Map<JSChunk, Set<String>> namesToImportByModule =
            crossChunkImports.computeIfAbsent(chunk, (JSChunk k) -> new LinkedHashMap<>());
        for (JSChunk dependency : chunkDependencies) {
          namesToImportByModule.computeIfAbsent(dependency, (JSChunk k) -> new LinkedHashSet<>());
        }
      }
    }
  }

  /**
   * Test if a node is a .then callback as inserted by the RewriteDynamicImports pass Only finds
   * callbacks when wrapped in the specially named extern function injected when the chunk output
   * type is ES_MODULES
   */
  public static boolean isDynamicImportCallback(Node call) {
    if (!call.isCall()) {
      return false;
    }
    if (NodeUtil.isCallTo(call, DYNAMIC_IMPORT_CALLBACK_FN)) {
      return true;
    }
    return false;
  }

  /** Test if a node is a .then callback as inserted by the RewriteDynamicImports pass */
  private boolean visitCallAndTraverse(NodeTraversal t, Node call) {
    checkState(call.isCall());
    if (!isDynamicImportCallback(call)) {
      return true;
    }

    Node moduleNamespace = getDynamicImportCallbackModuleNamespace(compiler, call);
    if (moduleNamespace == null) {
      return true;
    }
    boolean isValidModuleNamespace = visitName(t, moduleNamespace, ImportType.DYNAMIC);
    if (isValidModuleNamespace) {
      dynamicImportCallbacks.add(call);
    } else {
      compiler.report(
          JSError.make(
              call,
              UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK,
              " Unable to find valid namespace reference."));
    }
    return false;
  }

  public boolean visitName(NodeTraversal t, Node nameNode, ImportType importType) {
    checkState(nameNode.isName());
    String name = nameNode.getString();

    if ("".equals(name)) {
      return false;
    }

    Scope s = t.getScope();
    Var v = s.getVar(name);
    if (v == null || !v.isGlobal()) {
      return false;
    }
    CompilerInput input = v.getInput();
    if (input == null) {
      return false;
    }
    JSChunk definingChunk = input.getChunk();
    JSChunk referencingChunk = t.getChunk();

    if (definingChunk != referencingChunk) {
      if (NodeUtil.isLhsOfAssign(nameNode)) {
        t.report(
            nameNode, ASSIGNMENT_TO_IMPORT, nameNode.getString(), getChunkName(referencingChunk));
      }

      // Mark the chunk where the name is declared as needing an export for this name
      Set<String> namesToExport =
          crossChunkExports.computeIfAbsent(definingChunk, (JSChunk k) -> new LinkedHashSet<>());
      namesToExport.add(name);

      // Add an import for this name to this module from the source module
      Map<JSChunk, Set<String>> namesToImportByModule =
          crossChunkImports.computeIfAbsent(referencingChunk, (JSChunk k) -> new LinkedHashMap<>());
      if (importType == ImportType.STATIC) {
        Set<String> importsForModule =
            namesToImportByModule.computeIfAbsent(
                definingChunk, (JSChunk k) -> new LinkedHashSet<>());
        importsForModule.add(name);
      }
    }
    return true;
  }
}
