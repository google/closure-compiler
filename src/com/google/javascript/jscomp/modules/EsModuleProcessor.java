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
package com.google.javascript.jscomp.modules;

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.modules.ModuleMapCreator.DOES_NOT_HAVE_EXPORT;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.TranspilationUtil;
import com.google.javascript.jscomp.Var;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.ModuleMapCreator.ModuleProcessor;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.nullness.Nullable;

/**
 * Collects information related to and resolves ES imports and exports. Also performs several ES
 * module related checks.
 *
 * <p>This information is stored outside of any {@link Scope} because it should never be
 * recalculated because it is expensive.
 *
 * <p><a
 * href="https://www.ecma-international.org/ecma-262/9.0/index.html#sec-source-text-module-records">
 * Suggested reading</a>
 */
public final class EsModuleProcessor implements NodeTraversal.Callback, ModuleProcessor {

  /**
   * Error occurs when there is an ambiguous export, which can happen if there are multiple {@code
   * export * from} statements. If two modules that were {@code export * from}'d have the same key
   * as an export that export is now ambiguous. It can be resolved by exporting that key explicitly
   * locally.
   *
   * <p>So if modules "first" and "second" both export "x" then this is ambiguous:
   *
   * <pre>
   * export * from 'first';
   * export * from 'second';
   * </pre>
   *
   * <p>However we can resolve it by explicitly exporting an "x" locally:
   *
   * <pre>
   * // Note: any one of these is a solution. Using 2 or more together causes a duplicate key error.
   *
   * // specifically export x from first
   * export {x} from 'first';
   * // specifically export x from second
   * export {x} from 'second';
   * // export our own x instead
   * export let x;
   * </pre>
   *
   * Note: This is purposefully a warning. The spec does not treat this as an error. It is only an
   * error if an import attempts to use an ambiguous name. But just having ambiguous names is not
   * itself an error.
   */
  public static final DiagnosticType AMBIGUOUS_EXPORT_DEFINITION =
      DiagnosticType.warning("JSC_AMBIGUOUS_EXPORT_DEFINITION", "The export \"{0}\" is ambiguous.");

  public static final DiagnosticType CYCLIC_EXPORT_DEFINITION =
      DiagnosticType.error(
          "JSC_CYCLIC_EXPORT_DEFINITION", "Cyclic export detected while resolving name \"{0}\".");

  // Note: We only check for duplicate exports, not imports. Imports cannot be shadowed (by any
  // other binding, including other imports) and so we check that in VariableReferenceCheck.
  public static final DiagnosticType DUPLICATE_EXPORT =
      DiagnosticType.error("JSC_DUPLICATE_EXPORT", "Duplicate export of \"{0}\".");

  public static final DiagnosticType IMPORTED_AMBIGUOUS_EXPORT =
      DiagnosticType.error(
          "JSC_IMPORTED_AMBIGUOUS_EXPORT", "The requested name \"{0}\" is ambiguous.");

  public static final DiagnosticType NAMESPACE_IMPORT_CANNOT_USE_STAR =
      DiagnosticType.error(
          "JSC_NAMESPACE_IMPORT_CANNOT_USE_STAR",
          "Namespace imports ('goog:some.Namespace') cannot use import * as. "
              + "Did you mean to import {0} from ''{1}'';?");

  public static final DiagnosticType CANNOT_PATH_IMPORT_CLOSURE_FILE =
      DiagnosticType.error(
          "JSC_CANNOT_PATH_IMPORT_CLOSURE_FILE",
          "Cannot import Closure files by path. Use either import 'goog:namespace' or"
              + " goog.require('namespace')");

  /**
   * Marks all exports that are mutated in an inner scope as mutable.
   *
   * <p>Exports mutated at the module scope are not marked as mutable as they are effectively
   * constant after module evaluation.
   */
  private static final class FindMutableExports extends AbstractPostOrderCallback {
    final List<Export> exports;
    final ListMultimap<String, Export> exportsByLocalName;

    FindMutableExports(List<Export> exports) {
      // There may be multiple exports with the same local name because you can export a local
      // variable with an alias many different times. Example:
      //
      // let x;
      // export {x as y, x as z};
      this.exports = exports;
      exportsByLocalName = ArrayListMultimap.create();
      for (Export e : exports) {
        exportsByLocalName.put(e.localName(), e);
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isName()) {
        return;
      }

      Scope scope = t.getScope();
      if (!NodeUtil.isLValue(n) || scope.getClosestHoistScope().isModuleScope()) {
        return;
      }

      List<Export> exports = exportsByLocalName.get(n.getString());
      if (exports.isEmpty()) {
        return;
      }

      Var var = scope.getVar(n.getString());
      // A var declared in the module scope with the same name as an export must be the
      // export. And we know we're setting it in a function scope, so this cannot be the
      // declaration itself. We must be mutating.
      if (var == null || !var.getScope().isModuleScope()) {
        return;
      }

      for (Export e : new ArrayList<>(exports)) {
        int i = this.exports.indexOf(e);
        Export mutated = e.mutatedCopy();
        this.exports.set(i, mutated);
        exportsByLocalName.remove(e.localName(), e);
        exportsByLocalName.put(e.localName(), mutated);
      }
    }
  }

  /**
   * Collects all imports and exports of a module. Builds a {@link UnresolvedEsModule} by slotting
   * the exports into one of three categories:
   *
   * <ul>
   *   <li>Local exports - exports whose original definition is in this file.
   *   <li>Indirect exports - exports that come from another file. Either via {@code export {name}
   *       from} or {@code import {name}; export {name};}
   *   <li>Star exports - {@code export * from} exports
   * </ul>
   */
  private final class UnresolvedModuleBuilder {
    private final ModulePath path;
    private final Node root;
    private final Map<String, Import> importsByLocalName;
    private final List<Export> exports;
    private final Set<String> exportedNames;

    UnresolvedModuleBuilder(ModulePath path, Node root) {
      this.path = path;
      this.root = root;
      importsByLocalName = new HashMap<>();
      exports = new ArrayList<>();
      exportedNames = new HashSet<>();
    }

    void add(Import i) {
      importsByLocalName.put(i.localName(), i);
    }

    /** True if the export was successfully added or false if the exported name already exists. */
    boolean add(Export e) {
      if (e.exportName() == null) {
        exports.add(e);
        return true;
      } else {
        if (exportedNames.contains(e.exportName())) {
          return false;
        }

        exports.add(e);
        exportedNames.add(e.exportName());
        return true;
      }
    }

    UnresolvedModule build() {
      List<Export> localExports = new ArrayList<>();
      ImmutableList.Builder<Export> indirectExportsBuilder = new ImmutableList.Builder<>();
      ImmutableList.Builder<Export> starExportsBuilder = new ImmutableList.Builder<>();

      for (Export ee : exports) {
        if (ee.moduleRequest() == null) {
          if (importsByLocalName.containsKey(ee.localName())) {
            // import A from '';
            // export { A };

            // import * as ns from '';
            // export { ns };
            indirectExportsBuilder.add(ee);
          } else {
            // var y;
            // export { y };
            // export var x;
            localExports.add(ee);
          }
        } else if ("*".equals(ee.importName())) {
          // export * from '';
          starExportsBuilder.add(ee);
        } else {
          // export { A } from '';
          indirectExportsBuilder.add(ee);
        }
      }

      NodeTraversal.traverse(compiler, root, new FindMutableExports(localExports));

      return new UnresolvedEsModule(
          metadata,
          path,
          ImmutableMap.copyOf(importsByLocalName),
          ImmutableList.copyOf(localExports),
          indirectExportsBuilder.build(),
          starExportsBuilder.build());
    }
  }

  /**
   * A module that has had its exports and imports parsed and categorized but has yet to
   * transitively resolve them.
   */
  private final class UnresolvedEsModule extends UnresolvedModule {

    private final ModuleMetadata metadata;
    private final ModulePath path;
    private final ImmutableMap<String, Import> importsByLocalName;
    private final ImmutableList<Export> localExports;
    private final ImmutableList<Export> indirectExports;
    private final ImmutableList<Export> starExports;
    private @Nullable ImmutableSet<String> exportedNames;
    private final Map<String, ResolveExportResult> resolvedImports;
    private final Map<String, ResolveExportResult> resolvedExports;
    private @Nullable Module resolved;

    private UnresolvedEsModule(
        ModuleMetadata metadata,
        ModulePath path,
        ImmutableMap<String, Import> importsByLocalName,
        ImmutableList<Export> localExports,
        ImmutableList<Export> indirectExports,
        ImmutableList<Export> starExports) {
      this.metadata = metadata;
      this.path = path;
      this.importsByLocalName = importsByLocalName;
      this.localExports = localExports;
      this.indirectExports = indirectExports;
      this.starExports = starExports;
      exportedNames = null;
      resolvedImports = new HashMap<>();
      resolvedExports = new HashMap<>();
      resolved = null;
    }

    @Override
    void reset() {
      resolved = null;
      exportedNames = null;
      resolvedImports.clear();
      resolvedExports.clear();
    }

    @Override
    public Module resolve(
        ModuleRequestResolver moduleRequestResolver, @Nullable String moduleSpecifier) {
      if (resolved == null) {
        // Every import creates a locally bound name.
        Map<String, Binding> boundNames =
            new LinkedHashMap<>(getAllResolvedImports(moduleRequestResolver));

        Map<String, Export> localNameToLocalExport = new HashMap<>();

        // Only local exports that are not an anonymous default export create local bindings.
        for (Export e : localExports) {
          localNameToLocalExport.put(e.localName(), e);

          if (!Export.DEFAULT_EXPORT_NAME.equals(e.localName())) {
            ResolveExportResult b = resolveExport(moduleRequestResolver, e.exportName());
            checkState(b.resolved(), "Cannot have invalid missing own export!");
            if (!b.isAmbiguous()) {
              boundNames.put(e.localName(), b.getBinding());
            }
          }
        }

        // getAllResolvedExports is required to make a module but also performs cycle checking.
        resolved =
            Module.builder()
                .boundNames(ImmutableMap.copyOf(boundNames))
                .namespace(ImmutableMap.copyOf(getAllResolvedExports(moduleRequestResolver)))
                .metadata(metadata)
                .path(path)
                .localNameToLocalExport(ImmutableMap.copyOf(localNameToLocalExport))
                .build();
      }

      return resolved;
    }

    @Override
    ModuleMetadata metadata() {
      return metadata;
    }

    /** A map from import bound name to binding. */
    Map<String, Binding> getAllResolvedImports(ModuleRequestResolver moduleRequestResolver) {
      Map<String, Binding> imports = new HashMap<>();

      for (String name : importsByLocalName.keySet()) {
        ResolveExportResult b = resolveImport(moduleRequestResolver, name);
        if (b.resolved()) {
          imports.put(name, b.getBinding());
        }
      }

      return imports;
    }

    public ResolveExportResult resolveImport(
        ModuleRequestResolver moduleRequestResolver,
        String name,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet) {
      if (resolvedImports.containsKey(name)) {
        return resolvedImports.get(name);
      }
      ResolveExportResult b =
          resolveImportImpl(moduleRequestResolver, name, resolveSet, exportStarSet);
      resolvedImports.put(name, b);
      return b;
    }

    public ResolveExportResult resolveImport(
        ModuleRequestResolver moduleRequestResolver, String name) {
      return resolveImport(moduleRequestResolver, name, new HashSet<>(), new HashSet<>());
    }

    private ResolveExportResult resolveImportImpl(
        ModuleRequestResolver moduleRequestResolver,
        String name,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet) {
      Import i = importsByLocalName.get(name);

      UnresolvedModule requested = moduleRequestResolver.resolve(i);
      if (requested == null) {
        return ResolveExportResult.ERROR;
      } else {
        boolean importStar = i.importName().equals("*");
        if (importStar
            || (i.importName().equals(Export.DEFAULT)
                && (requested.metadata().isGoogProvide() || requested.metadata().isGoogModule()))) {
          if (!GoogEsImports.isGoogImportSpecifier(i.moduleRequest())
              && (requested.metadata().isGoogModule() || requested.metadata().isGoogProvide())) {
            compiler.report(
                JSError.make(
                    path.toString(),
                    i.importNode().getLineno(),
                    i.importNode().getCharno(),
                    CANNOT_PATH_IMPORT_CLOSURE_FILE,
                    i.localName(),
                    i.moduleRequest()));
            return ResolveExportResult.ERROR;
          }
          if (importStar && GoogEsImports.isGoogImportSpecifier(i.moduleRequest())) {
            compiler.report(
                JSError.make(
                    path.toString(),
                    i.importNode().getLineno(),
                    i.importNode().getCharno(),
                    NAMESPACE_IMPORT_CANNOT_USE_STAR,
                    i.localName(),
                    i.moduleRequest()));
            return ResolveExportResult.ERROR;
          }
          String closureNamespace =
              GoogEsImports.isGoogImportSpecifier(i.moduleRequest())
                  ? GoogEsImports.getClosureIdFromGoogImportSpecifier(i.moduleRequest())
                  : null;
          return ResolveExportResult.of(
              Binding.from(requested.metadata(), closureNamespace, i.nameNode()));
        } else {
          ResolveExportResult result =
              requested.resolveExport(
                  moduleRequestResolver,
                  i.moduleRequest(),
                  i.importName(),
                  resolveSet,
                  exportStarSet);
          if (!result.found() && !result.hadError()) {
            compiler.report(
                JSError.make(
                    path.toString(),
                    i.importNode().getLineno(),
                    i.importNode().getCharno(),
                    DOES_NOT_HAVE_EXPORT,
                    i.importName()));
            return ResolveExportResult.ERROR;
          } else if (result.isAmbiguous()) {
            compiler.report(
                JSError.make(
                    path.toString(),
                    i.importNode().getLineno(),
                    i.importNode().getCharno(),
                    IMPORTED_AMBIGUOUS_EXPORT,
                    i.importName()));
            return ResolveExportResult.ERROR;
          }
          Node forSourceInfo = i.nameNode() == null ? i.importNode() : i.nameNode();
          return result.copy(forSourceInfo, Binding.CreatedBy.IMPORT);
        }
      }
    }

    @Override
    public ImmutableSet<String> getExportedNames(ModuleRequestResolver moduleRequestResolver) {
      if (exportedNames == null) {
        exportedNames = getExportedNames(moduleRequestResolver, new HashSet<>());
      }
      return exportedNames;
    }

    @Override
    public ImmutableSet<String> getExportedNames(
        ModuleRequestResolver moduleRequestResolver, Set<UnresolvedModule> visited) {
      if (visited.contains(this)) {
        // import * cycle
        return ImmutableSet.of();
      }

      visited.add(this);
      // exports are in Array.prototype.sort() order.
      Set<String> exportedNames = new TreeSet<>();

      for (Export e : localExports) {
        exportedNames.add(e.exportName());
      }

      for (Export e : indirectExports) {
        exportedNames.add(e.exportName());
      }

      for (Export e : starExports) {
        UnresolvedModule requested = moduleRequestResolver.resolve(e);

        if (requested != null) {
          if (requested.metadata().isEs6Module()) {
            for (String n : requested.getExportedNames(moduleRequestResolver, visited)) {
              // Default exports are not exported with export *.
              if (!Export.DEFAULT.equals(n) && !exportedNames.contains(n)) {
                exportedNames.add(n);
              }
            }
          } else {
            compiler.report(
                JSError.make(
                    e.exportNode(),
                    TranspilationUtil.CANNOT_CONVERT_YET,
                    "Wildcard export for non-ES module"));
          }
        }
      }

      return ImmutableSet.copyOf(exportedNames);
    }

    /** Map of exported name to binding. */
    Map<String, Binding> getAllResolvedExports(ModuleRequestResolver moduleRequestResolver) {
      Map<String, Binding> exports = new LinkedHashMap<>();

      for (String name : getExportedNames(moduleRequestResolver)) {
        ResolveExportResult b = resolveExport(moduleRequestResolver, name);
        checkState(b.found(), "Cannot have invalid own export.");
        if (b.resolved()) {
          exports.put(name, b.getBinding());
        } else if (b.isAmbiguous()) {
          compiler.report(JSError.make(path.toString(), -1, -1, AMBIGUOUS_EXPORT_DEFINITION, name));
        }
      }

      return exports;
    }

    private ResolveExportResult resolveExport(
        ModuleRequestResolver moduleRequestResolver,
        String exportName,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet) {
      if (!getExportedNames(moduleRequestResolver).contains(exportName)) {
        return ResolveExportResult.NOT_FOUND;
      }

      if (!resolveSet.add(ExportTrace.create(this, exportName))) {
        // Cycle!
        compiler.report(JSError.make(path.toString(), 0, 0, CYCLIC_EXPORT_DEFINITION, exportName));
        return ResolveExportResult.ERROR;
      }

      for (Export e : localExports) {
        if (exportName.equals(e.exportName())) {
          Node forSourceInfo = e.nameNode() != null ? e.nameNode() : e.exportNode();
          return ResolveExportResult.of(Binding.from(e, forSourceInfo));
        }
      }

      for (Export e : indirectExports) {
        if (exportName.equals(e.exportName())) {
          if (importsByLocalName.containsKey(e.localName())) {
            // import whatever from 'mod';
            // export { whatever };
            return resolveImport(moduleRequestResolver, e.localName(), resolveSet, exportStarSet)
                .copy(e.nameNode(), Binding.CreatedBy.EXPORT);
          } else {
            UnresolvedModule requested = moduleRequestResolver.resolve(e);

            if (requested == null) {
              return ResolveExportResult.ERROR;
            } else {
              // export { whatever } from 'mod';
              ResolveExportResult result =
                  requested.resolveExport(
                      moduleRequestResolver,
                      e.moduleRequest(),
                      e.importName(),
                      resolveSet,
                      exportStarSet);
              if (!result.found() && !result.hadError()) {
                compiler.report(
                    JSError.make(
                        path.toString(),
                        e.exportNode().getLineno(),
                        e.exportNode().getCharno(),
                        DOES_NOT_HAVE_EXPORT,
                        e.importName()));
                return ResolveExportResult.ERROR;
              } else if (result.isAmbiguous()) {
                compiler.report(
                    JSError.make(
                        path.toString(),
                        e.exportNode().getLineno(),
                        e.exportNode().getCharno(),
                        IMPORTED_AMBIGUOUS_EXPORT,
                        e.importName()));
              }
              return result.copy(e.nameNode(), Binding.CreatedBy.EXPORT);
            }
          }
        }
      }

      checkState(!Export.DEFAULT.equals(exportName), "Default export cannot come from export *.");

      if (exportStarSet.contains(this)) {
        // Cycle!
        compiler.report(
            JSError.make(path.toString(), -1, -1, CYCLIC_EXPORT_DEFINITION, exportName));
        return ResolveExportResult.ERROR;
      }

      exportStarSet.add(this);

      ResolveExportResult starResolution = null;

      for (Export e : starExports) {
        UnresolvedModule requested = moduleRequestResolver.resolve(e);

        if (requested == null) {
          return ResolveExportResult.ERROR;
        } else if (requested.getExportedNames(moduleRequestResolver).contains(exportName)) {
          ResolveExportResult resolution =
              requested.resolveExport(
                  moduleRequestResolver, e.moduleRequest(), exportName, resolveSet, exportStarSet);

          if (resolution.hadError()) {
            // Recursive case; error was reported on base case.
            return resolution;
          } else if (resolution.isAmbiguous()) {
            return resolution;
          } else {
            if (starResolution == null) {
              // First time finding something, not ambiguous.
              starResolution = resolution.copy(e.exportNode(), Binding.CreatedBy.EXPORT);
            } else {
              // Second time finding something, might be ambiguous!
              // Not ambiguous if it is the same export (same module and export name).
              if (starResolution != resolution) {
                return ResolveExportResult.AMBIGUOUS;
              }
            }
          }
        }
      }

      if (starResolution == null) {
        return ResolveExportResult.ERROR;
      }

      return starResolution;
    }

    @Override
    public ResolveExportResult resolveExport(
        ModuleRequestResolver moduleRequestResolver,
        @Nullable String moduleSpecifier,
        String exportName,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet) {
      // Explicit containsKey check here since values can be null!
      if (resolvedExports.containsKey(exportName)) {
        return resolvedExports.get(exportName);
      }

      ResolveExportResult b =
          resolveExport(moduleRequestResolver, exportName, resolveSet, exportStarSet);
      resolvedExports.put(exportName, b);
      return b;
    }
  }

  EsModuleProcessor(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private final AbstractCompiler compiler;
  private @Nullable UnresolvedModuleBuilder currentModuleBuilder;
  private @Nullable ModuleMetadata metadata;

  @Override
  public UnresolvedModule process(
      ModuleMetadata metadata,
      ModulePath path,
      Node script) {
    this.metadata = metadata;
    currentModuleBuilder = new UnresolvedModuleBuilder(path, script);

    NodeTraversal.traverse(compiler, script, this);

    UnresolvedModule m = currentModuleBuilder.build();
    this.metadata = null;
    currentModuleBuilder = null;
    return m;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case ROOT:
      case SCRIPT:
      case MODULE_BODY:
      case EXPORT:
      case IMPORT:
        return true;
      default:
        return false;
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case EXPORT:
        visitExport(t, n);
        break;
      case IMPORT:
        visitImport(t, n);
        break;
      default:
        break;
    }
  }

  private void visitExportAllFrom(NodeTraversal t, Node export) {
    // export * from '';
    currentModuleBuilder.add(
        Export.builder()
            .exportName(null)
            .moduleRequest(export.getSecondChild().getString())
            .importName("*")
            .localName(null)
            .modulePath(t.getInput().getPath())
            .exportNode(export)
            .moduleMetadata(metadata)
            .build());
  }

  private void visitExportDefault(NodeTraversal t, Node export) {
    // export default <expression>;
    Node child = export.getFirstChild();
    String name = Export.DEFAULT_EXPORT_NAME;

    if (child.isFunction() || child.isClass()) {
      String maybeName = NodeUtil.getName(child);

      if (!Strings.isNullOrEmpty(maybeName)) {
        name = maybeName;
      }
    }

    if (!currentModuleBuilder.add(
        Export.builder()
            .exportName(Export.DEFAULT)
            .moduleRequest(null)
            .importName(null)
            .localName(name)
            .modulePath(t.getInput().getPath())
            .exportNode(export)
            .moduleMetadata(metadata)
            .build())) {
      t.report(export, DUPLICATE_EXPORT, Export.DEFAULT);
    }
  }

  private void visitExportFrom(NodeTraversal t, Node export) {
    // export { Foo, Bar as Rab } from '';
    for (Node child = export.getFirstFirstChild(); child != null; child = child.getNext()) {
      String importName = child.getFirstChild().getString();
      String exportedName = child.getLastChild().getString();
      if (!currentModuleBuilder.add(
          Export.builder()
              .exportName(exportedName)
              .moduleRequest(export.getSecondChild().getString())
              .importName(importName)
              .localName(null)
              .modulePath(t.getInput().getPath())
              .exportNode(export)
              .nameNode(child.getFirstChild())
              .moduleMetadata(metadata)
              .build())) {
        t.report(export, DUPLICATE_EXPORT, exportedName);
      }
    }
  }

  private void visitExportSpecs(NodeTraversal t, Node export) {
    // export { Foo, Bar as Rab };
    for (Node child = export.getFirstFirstChild(); child != null; child = child.getNext()) {
      String localName = child.getFirstChild().getString();
      String exportedName = child.getLastChild().getString();
      if (!currentModuleBuilder.add(
          Export.builder()
              .exportName(exportedName)
              .moduleRequest(null)
              .importName(null)
              .localName(localName)
              .modulePath(t.getInput().getPath())
              .exportNode(export)
              .nameNode(child.getFirstChild())
              .moduleMetadata(metadata)
              .build())) {
        t.report(export, DUPLICATE_EXPORT, exportedName);
      }
    }
  }

  private void visitExportNameDeclaration(NodeTraversal t, Node export, Node declaration) {
    //    export var Foo;
    //    export let {a, b:[c,d]} = {};
    NodeUtil.visitLhsNodesInNode(
        declaration, lhs -> addExportNameDeclaration(t, lhs, export, declaration));
  }

  private void addExportNameDeclaration(NodeTraversal t, Node lhs, Node export, Node declaration) {
    checkState(lhs.isName());
    String name = lhs.getString();
    if (!currentModuleBuilder.add(
        Export.builder()
            .exportName(name)
            .moduleRequest(null)
            .importName(null)
            .localName(name)
            .modulePath(t.getInput().getPath())
            .exportNode(export)
            .nameNode(lhs)
            .moduleMetadata(metadata)
            .build())) {
      t.report(export, DUPLICATE_EXPORT, name);
    }
  }

  private void visitExportFunctionOrClass(NodeTraversal t, Node export, Node declaration) {
    // export function foo() {}
    // export class Foo {}
    checkState(declaration.isFunction() || declaration.isClass());
    Node nameNode = declaration.getFirstChild();
    String name = nameNode.getString();
    if (!currentModuleBuilder.add(
        Export.builder()
            .exportName(name)
            .moduleRequest(null)
            .importName(null)
            .localName(name)
            .modulePath(t.getInput().getPath())
            .exportNode(export)
            .nameNode(nameNode)
            .moduleMetadata(metadata)
            .build())) {
      t.report(export, DUPLICATE_EXPORT, name);
    }
  }

  private void visitExport(NodeTraversal t, Node export) {
    if (export.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      visitExportAllFrom(t, export);
    } else if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
      visitExportDefault(t, export);
    } else if (export.hasTwoChildren()) {
      visitExportFrom(t, export);
    } else if (export.getFirstChild().isExportSpecs()) {
      visitExportSpecs(t, export);
    } else {
      Node declaration = export.getFirstChild();
      if (NodeUtil.isNameDeclaration(declaration)) {
        visitExportNameDeclaration(t, export, declaration);
      } else {
        visitExportFunctionOrClass(t, export, declaration);
      }
    }
  }

  private void visitImportDefault(NodeTraversal t, Node importNode, String moduleRequest) {
    // import Default from '';
    currentModuleBuilder.add(
        Import.builder()
            .moduleRequest(moduleRequest)
            .importName(Export.DEFAULT)
            .localName(importNode.getFirstChild().getString())
            .modulePath(t.getInput().getPath())
            .importNode(importNode)
            .nameNode(importNode.getFirstChild())
            .build());
  }

  private void visitImportSpecs(NodeTraversal t, Node importNode, String moduleRequest) {
    // import { A, b as B } from '';
    for (Node child = importNode.getSecondChild().getFirstChild();
        child != null;
        child = child.getNext()) {
      String importName = child.getFirstChild().getString();
      String localName = child.getLastChild().getString();
      currentModuleBuilder.add(
          Import.builder()
              .moduleRequest(moduleRequest)
              .importName(importName)
              .localName(localName)
              .modulePath(t.getInput().getPath())
              .importNode(importNode)
              .nameNode(child.getSecondChild())
              .build());
    }
  }

  private void visitImportStar(NodeTraversal t, Node importNode, String moduleRequest) {
    // import * as ns from '';
    currentModuleBuilder.add(
        Import.builder()
            .moduleRequest(moduleRequest)
            .importName("*")
            .localName(importNode.getSecondChild().getString())
            .importNode(importNode)
            .modulePath(t.getInput().getPath())
            .nameNode(importNode.getSecondChild())
            .build());
  }

  private void visitImport(NodeTraversal t, Node importNode) {
    String moduleRequest = importNode.getLastChild().getString();

    if (importNode.getFirstChild().isName()) {
      visitImportDefault(t, importNode, moduleRequest);
    }

    if (importNode.getSecondChild().isImportSpecs()) {
      visitImportSpecs(t, importNode, moduleRequest);
    } else if (importNode.getSecondChild().isImportStar()) {
      visitImportStar(t, importNode, moduleRequest);
    }

    // no entry for import '';
  }
}
