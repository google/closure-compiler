/*
 * Copyright 2020 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds all references to global symbols in a different output chunk and add ES Module imports and
 * exports for them.
 *
 * chunk1.js
 * <pre>var a = 1; function b() { return a }</pre>
 *
 * chunk2.js
 * <pre>console.log(a);</pre>
 *
 * becomes
 *
 * chunk1.js
 * <pre>var a = 1; var b = function b() { return a }; export {a};</pre>
 *
 * chunk2.js
 * <pre>import {a} from './chunk1.js'; console.log(a);</pre>
 *
 * This allows splitting code into es modules that depend on each other's symbols, without using
 * a global namespace or polluting the global scope.
 */
final class ConvertChunksToESModules implements CompilerPass {
  private final AbstractCompiler compiler;
  private final Map<JSModule, Set<String>> crossChunkExports = new HashMap<>();
  private final Map<JSModule, Map<JSModule, Set<String>>> crossChunkImports = new HashMap<>();

  static final DiagnosticType ASSIGNMENT_TO_IMPORT = DiagnosticType.error(
      "JSC_IMPORT_ASSIGN",
      "Imported symbol \"{0}\" in chunk \"{1}\" cannot be assigned");

  static final DiagnosticType UNABLE_TO_COMPUTE_RELATIVE_PATH = DiagnosticType.error(
      "JSC_UNABLE_TO_COMPUTE_RELATIVE_PATH",
      "Unable to compute relative import path from \"{0}\" to \"{1}\"");

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
    for (JSModule chunk : compiler.getModuleGraph().getAllModules()) {
      if (!crossChunkExports.containsKey(chunk) &&
          !crossChunkImports.containsKey(chunk) &&
          chunk.getInputs().size() > 0) {
        crossChunkExports.put(chunk, new HashSet<>());
      }
    }

    convertChunkSourcesToModules();
    addExportStatements();
    addImportStatements();
    compiler.setFeatureSet(compiler.getFeatureSet().with(Feature.MODULES));
  }

  /**
   * Move all code in a chunk into the first input and mark it as an ESModule.
   * At this point in the compilation, all input files should be scripts.
   *
   * Original AST:
   * [ROOT] top JS sources root
   *     [SCRIPT] // 1st chunk's 1st input
   *         statements from 1st chunk's 1st script
   *     [SCRIPT] // 1st chunk's 2nd input
   *         statements from 1st chunk's 2nd script
   *     ...
   *     [SCRIPT] // 1st chunk's nth input
   *     [SCRIPT] // 2nd chunk's 1st input
   *     ...
   *
   * After this method
   * [ROOT] top JS sources root
   *     [SCRIPT] // 1st chunk's 1st input script
   *         [MODULE_BODY] // new module body to contain the 1st chunk
   *             // statements from all scripts in the first chunk
   *             // All script nodes from the other inputs of the chunk are now empty
   *     [SCRIPT] // 2nd chunk's 1st input script
   *         [MODULE_BODY] // new module body to contain the 2nd chunk
   *             // statements from all scripts in the first chunk
   *             // All script nodes from the other inputs of the chunk are now empty
   */
  private void convertChunkSourcesToModules() {
    for (JSModule chunk : compiler.getModuleGraph().getAllModules()) {
      if (chunk.getInputs().size() == 0) {
        continue;
      }

      CompilerInput firstInput = null;
      for (CompilerInput input : chunk.getInputs()) {
        Node inputScript = input.getAstRoot(compiler);
        FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(inputScript);
        checkState(!scriptFeatures.contains(FeatureSet.ES6_MODULES));
        if (firstInput == null) {
          firstInput = input;
          scriptFeatures = scriptFeatures.union(FeatureSet.ES6_MODULES);
          inputScript.putProp(Node.FEATURE_SET, scriptFeatures);
          Node moduleBody = new Node(Token.MODULE_BODY);
          moduleBody.useSourceInfoFrom(inputScript);
          moduleBody.addChildrenToFront(inputScript.removeChildren());
          inputScript.addChildToFront(moduleBody);
          compiler.reportChangeToEnclosingScope(moduleBody);
        } else {
          Node firstInputAstRoot = firstInput.getAstRoot(compiler);
          FeatureSet firstInputScriptFeatures = NodeUtil.getFeatureSetOfScript(firstInputAstRoot);
          FeatureSet combinedFeatureSet =
              firstInputScriptFeatures.union(NodeUtil.getFeatureSetOfScript(inputScript));
          inputScript.putProp(Node.FEATURE_SET, combinedFeatureSet);
          Node moduleBody = firstInputAstRoot.getFirstChild();
          checkState(moduleBody != null && moduleBody.isModuleBody());
          moduleBody.addChildrenToBack(inputScript.removeChildren());
          compiler.reportChangeToEnclosingScope(firstInputAstRoot);
          compiler.reportChangeToChangeScope(inputScript);
        }
      }
    }
  }

  /**
   * Add an export statement to all chunks. Any name in a chunk which is referenced in another
   * chunk is added to the export. If a chunk has no names exported, an empty export statement
   * is added to ensure the chunk is parsed as a module.
   *
   * <pre>export {name1, name2, ..., nameN};</pre>
   */
  private void addExportStatements() {
    for (Map.Entry<JSModule, Set<String>> jsModuleExports : crossChunkExports.entrySet()) {
      CompilerInput firstInput = jsModuleExports.getKey().getInput(0);
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
      Node export = IR.export(exportSpecs).useSourceInfoFromForTree(moduleBody);
      moduleBody.addChildToBack(export);
      compiler.reportChangeToEnclosingScope(moduleBody);
    }
  }

  private static String getChunkFileName(JSModule chunk) {
    return chunk.getName() + ".js";
  }

  /**
   * Add import statements to chunks. One import statement is added for each chunk where a
   * referenced name is defined.
   *
   * <pre>import {name1, name2, ..., nameN} from './other-chunk1.js';
   * import {name3, name4, ..., nameN} from './other-chunk2.js';</pre>
   */
  private void addImportStatements() {
    for (Map.Entry<JSModule, Map<JSModule, Set<String>>> chunkImportsEntry :
        crossChunkImports.entrySet()) {
      ArrayList<Node> importStatements = new ArrayList<>();
      JSModule importingChunk = chunkImportsEntry.getKey();
      CompilerInput firstInput = importingChunk.getInput(0);
      Node moduleBody = firstInput.getAstRoot(compiler).getFirstChild();
      checkState(moduleBody != null && moduleBody.isModuleBody());

      // For each distinct chunk where a referenced symbol is defined, create an import statement
      // referencing the names.
      for (Map.Entry<JSModule, Set<String>> importsByChunk : chunkImportsEntry.getValue().entrySet()) {
        Node importSpecs = new Node(Token.IMPORT_SPECS);
        for (String name : importsByChunk.getValue()) {
          Node importSpec = new Node(Token.IMPORT_SPEC);
          importSpec.addChildToFront(IR.name(name));
          importSpec.addChildToFront(IR.name(name));
          importSpec.setShorthandProperty(true);
          importSpecs.addChildToBack(importSpec);
        }
        Node importStatement = new Node(Token.IMPORT);
        JSModule exportingChunk = importsByChunk.getKey();
        String importPath = getChunkFileName(exportingChunk);
        try {
          importPath =
              this.relativePath(
                  getChunkFileName(importingChunk),
                  getChunkFileName(exportingChunk));
        } catch (IllegalArgumentException e) {
          compiler.report(
              JSError.make(
                  moduleBody,
                  UNABLE_TO_COMPUTE_RELATIVE_PATH,
                  getChunkFileName(importingChunk),
                  getChunkFileName(exportingChunk)));
        }
        importStatement.addChildToFront(IR.string(importPath));
        importStatement.addChildToFront(importSpecs);
        importStatement.addChildToFront(IR.empty());
        importStatement.useSourceInfoFromForTree(moduleBody);
        importStatements.add(0, importStatement);
      }
      for (Node importStatement : importStatements) {
        moduleBody.addChildToFront(importStatement);
      }
      compiler.reportChangeToEnclosingScope(moduleBody);
    }
  }

  /** Find names in a module that are defined in a different module. */
  private class FindCrossChunkReferences extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        // Function expressions without names have a NAME node containing an empty string.
        if ("".equals(name)) {
          return;
        }
        Scope s = t.getScope();
        Var v = s.getVar(name);
        // No Var will be found for variables declared in extern files.
        if (v == null || !v.isGlobal()) {
          return;
        }
        CompilerInput input = v.getInput();
        // Compare the chunk where the variable is declared to the current
        // chunk. If they are different, the variable is used across modules.
        JSModule definingChunk = input.getModule();
        JSModule referencingChunk = t.getModule();
        if (definingChunk != referencingChunk) {
          if (NodeUtil.isLValue(n)) {
            t.report(
                n,
                ASSIGNMENT_TO_IMPORT,
                n.getString(),
                getChunkFileName(referencingChunk));
          }

          // Mark the chunk where the name is declared as needing an export for this name
          Set<String> namesToExport = crossChunkExports.get(definingChunk);
          if (namesToExport == null) {
            namesToExport = new HashSet<>();
            crossChunkExports.put(definingChunk, namesToExport);
          }
          namesToExport.add(name);

          // Add an import for this name to this module from the source module
          Map<JSModule, Set<String>> namesToImportByModule =
              crossChunkImports.get(referencingChunk);
          if (namesToImportByModule == null) {
            namesToImportByModule = new HashMap<>();
            crossChunkImports.put(referencingChunk, namesToImportByModule);
          }
          Set<String> importsForModule = namesToImportByModule.get(definingChunk);
          if (importsForModule == null) {
            importsForModule = new HashSet<>();
            namesToImportByModule.put(definingChunk, importsForModule);
          }
          importsForModule.add(name);
        }
      }
    }
  }

  /**
   * Calculate the relative path between two URI paths. To remain compliant with
   * ES Module loading restrictions, paths must always begin with a "./", "../" or "/"
   * or they are otherwise treated as a bare module specifier.
   *
   * TODO(ChadKillingsworth): This method likely has use cases beyond this class
   * and should be moved.
   */
  private String relativePath(String fromUriPath, String toUriPath) {
    Path fromPath = Paths.get(fromUriPath);
    Path toPath = Paths.get(toUriPath);
    Path fromFolder = fromPath.getParent();

    // if the from URIs are simple names without paths, they are in the same folder
    // example: m0.js
    if (fromFolder == null) {
      return "./" + toUriPath;
    }

    String calculatedPath = fromFolder.relativize(toPath).toString();
    if (calculatedPath.startsWith(".") || calculatedPath.startsWith("/")) {
      return calculatedPath;
    }
    return "./" + calculatedPath;
  }
}
