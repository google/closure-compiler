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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Creates a {@link ModuleMap}. */
public class ModuleMapCreator implements HotSwapCompilerPass {
  public static final DiagnosticType MISSING_NAMESPACE_IMPORT =
      DiagnosticType.error(
          "JSC_MISSING_NAMESPACE_IMPORT", "Imported Closure namespace \"{0}\" never defined.");

  public static final DiagnosticType DOES_NOT_HAVE_EXPORT =
      DiagnosticType.error(
          "JSC_DOES_NOT_HAVE_EXPORT", "Requested module does not have an export \"{0}\".");

  public static final DiagnosticType DOES_NOT_HAVE_EXPORT_WITH_DETAILS =
      DiagnosticType.error(
          "JSC_DOES_NOT_HAVE_EXPORT_WITH_DETAILS",
          "Requested module does not have an export \"{0}\".{1}");

  private final class ModuleRequestResolverImpl implements ModuleRequestResolver {
    private UnresolvedModule getFallbackForMissingNonClosureModule(ModuleLoader.ModulePath path) {
      ModuleMetadata metadata =
          ModuleMetadata.builder()
              .rootNode(null)
              .path(path)
              .moduleType(ModuleType.ES6_MODULE)
              .isTestOnly(false)
              .usesClosure(false)
              .build();

      return new UnresolvedModule() {
        @Override
        void reset() {}

        @Override
        Module resolve(
            ModuleRequestResolver moduleRequestResolver, @Nullable String moduleSpecifier) {
          return Module.builder()
              .boundNames(ImmutableMap.of())
              .namespace(ImmutableMap.of())
              .localNameToLocalExport(ImmutableMap.of())
              .path(path)
              .metadata(metadata)
              .unresolvedModule(this)
              .build();
        }

        @Override
        ModuleMetadata metadata() {
          return metadata;
        }

        @Override
        ImmutableSet<String> getExportedNames(ModuleRequestResolver moduleRequestResolver) {
          return ImmutableSet.of();
        }

        @Override
        protected ImmutableSet<String> getExportedNames(
            ModuleRequestResolver moduleRequestResolver, Set<UnresolvedModule> visited) {
          return ImmutableSet.of();
        }

        @Override
        ResolveExportResult resolveExport(
            ModuleRequestResolver moduleRequestResolver,
            @Nullable String moduleSpecifier,
            String exportName,
            Set<ExportTrace> resolveSet,
            Set<UnresolvedModule> exportStarSet) {
          return ResolveExportResult.of(
              Binding.from(
                  Export.builder()
                      .localName(exportName)
                      .moduleMetadata(metadata)
                      .modulePath(path)
                      .closureNamespace(null)
                      .build(),
                  /* sourceNode= */ null));
        }
      };
    }

    private UnresolvedModule getFallbackForMissingClosureModule(String namespace) {
      return nonEsModuleProcessor.process(
          ModuleMetadata.builder()
              .addGoogNamespace(namespace)
              .isTestOnly(false)
              .moduleType(ModuleType.GOOG_PROVIDE)
              .path(null)
              .rootNode(null)
              .usesClosure(true)
              .build(),
          /* path= */ null,
          /* script= */ null);
    }

    @Override
    @Nullable
    public UnresolvedModule resolve(Import i) {
      if (i.modulePath() == null) {
        return resolveForClosure(i.moduleRequest());
      }
      return resolve(i.moduleRequest(), i.modulePath(), i.importNode());
    }

    @Override
    @Nullable
    public UnresolvedModule resolve(Export e) {
      return resolve(e.moduleRequest(), e.modulePath(), e.exportNode());
    }

    @Nullable
    private UnresolvedModule resolveForClosure(String namespace) {
      UnresolvedModule module = unresolvedModulesByClosureNamespace.get(namespace);
      if (module == null) {
        module = getFallbackForMissingClosureModule(namespace);
        unresolvedModulesByClosureNamespace.put(namespace, module);
      }
      return module;
    }

    @Nullable
    private UnresolvedModule resolve(
        String moduleRequest, ModuleLoader.ModulePath modulePath, Node forLineInfo) {

      if (GoogEsImports.isGoogImportSpecifier(moduleRequest)) {
        String namespace = GoogEsImports.getClosureIdFromGoogImportSpecifier(moduleRequest);
        return resolveForClosure(namespace);
      }

      ModuleLoader.ModulePath requestedPath =
          modulePath.resolveJsModule(
              moduleRequest,
              modulePath.toString(),
              forLineInfo.getLineno(),
              forLineInfo.getCharno());

      if (requestedPath == null) {
        requestedPath = modulePath.resolveModuleAsPath(moduleRequest);

        if (!unresolvedModules.containsKey(requestedPath.toModuleName())) {
          UnresolvedModule module = getFallbackForMissingNonClosureModule(requestedPath);
          unresolvedModules.put(requestedPath.toModuleName(), module);
          return module;
        }
      }

      return unresolvedModules.get(requestedPath.toModuleName());
    }
  }

  /** A basic interface that can scan and return information about a module. */
  interface ModuleProcessor {
    UnresolvedModule process(ModuleMetadata metadata, ModulePath path, Node script);
  }

  private final AbstractCompiler compiler;
  private final EsModuleProcessor esModuleProcessor;
  private final ClosureModuleProcessor closureModuleProcessor;
  private final NonEsModuleProcessor nonEsModuleProcessor;
  private final Map<String, UnresolvedModule> unresolvedModules;
  private final Map<String, UnresolvedModule> unresolvedModulesByClosureNamespace;
  private final ModuleMetadataMap moduleMetadataMap;

  public ModuleMapCreator(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
    this.compiler = compiler;
    this.moduleMetadataMap = moduleMetadataMap;
    this.esModuleProcessor = new EsModuleProcessor(compiler);
    this.closureModuleProcessor = new ClosureModuleProcessor(compiler);
    this.nonEsModuleProcessor = new NonEsModuleProcessor();
    unresolvedModules = new HashMap<>();
    unresolvedModulesByClosureNamespace = new HashMap<>();
  }

  private ModuleMap create() {
    unresolvedModules.clear();
    unresolvedModulesByClosureNamespace.clear();

    // There are modules that aren't associated with scripts - nested goog.modules in
    // goog.loadModule calls.
    for (ModuleMetadata moduleMetadata : moduleMetadataMap.getAllModuleMetadata()) {
      process(moduleMetadata);
    }

    return resolve();
  }

  private ModuleMap resolve() {
    ModuleRequestResolver requestResolver = new ModuleRequestResolverImpl();
    Map<String, Module> resolvedModules = new HashMap<>();
    Map<String, Module> resolvedClosureModules = new HashMap<>();

    // We need to resolve in a loop as any missing reference will add a fake to the
    // unresolvedModules map (see getFallback* methods above). This would cause a concurrent
    // modification exception if we just iterated over unresolvedModules. So the first loop through
    // should resolve any "known" modules, and the second any "unrecognized" modules.
    do {
      Set<String> toResolve =
          Sets.difference(unresolvedModules.keySet(), resolvedModules.keySet()).immutableCopy();

      for (String key : toResolve) {
        Module resolved = unresolvedModules.get(key).resolve(requestResolver);
        resolvedModules.put(key, resolved);

        for (String namespace : resolved.metadata().googNamespaces()) {
          resolvedClosureModules.put(namespace, resolved);
        }
      }

    } while (!resolvedModules.keySet().containsAll(unresolvedModules.keySet()));

    do {
      Set<String> toResolve =
          Sets.difference(
                  unresolvedModulesByClosureNamespace.keySet(), resolvedClosureModules.keySet())
              .immutableCopy();

      for (String namespace : toResolve) {
        resolvedClosureModules.put(
            namespace, unresolvedModulesByClosureNamespace.get(namespace).resolve(requestResolver));
      }
    } while (!resolvedClosureModules
        .keySet()
        .containsAll(unresolvedModulesByClosureNamespace.keySet()));

    unresolvedModules.clear();
    unresolvedModulesByClosureNamespace.clear();

    return new ModuleMap(
        ImmutableMap.copyOf(resolvedModules), ImmutableMap.copyOf(resolvedClosureModules));
  }

  private void process(ModuleMetadata moduleMetadata) {
    final ModuleProcessor processor;
    switch (moduleMetadata.moduleType()) {
      case ES6_MODULE:
        processor = esModuleProcessor;
        break;
      case GOOG_MODULE:
      case LEGACY_GOOG_MODULE:
        processor = closureModuleProcessor;
        break;
      default:
        processor = nonEsModuleProcessor;
        break;
    }
    UnresolvedModule module =
        processor.process(moduleMetadata, moduleMetadata.path(), moduleMetadata.rootNode());

    // Have to use module names as keys because path "resolution" (ModuleLoader) "respects"
    // leading slashes. Meaning that if you look up "file.js" and "/file.js" you'll get
    // different paths back. But they'll have the same module name.
    if (moduleMetadata.path() != null) {
      unresolvedModules.put(moduleMetadata.path().toModuleName(), module);
    }

    for (String namespace : moduleMetadata.googNamespaces()) {
      unresolvedModulesByClosureNamespace.put(namespace, module);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    compiler.setModuleMap(create());
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    for (ModuleMetadata metadata : compiler.getModuleMetadataMap().getAllModuleMetadata()) {
      // Call NodeUtil.getInputId on the metadata's root node as it could be a block of a nested
      // goog.module, which won't have an input ID on the node itself.
      if (originalRoot.getInputId().equals(NodeUtil.getInputId(metadata.rootNode()))) {
        // We're hotswapping this module, rescan it.
        process(metadata);
      } else {
        // Existing module we aren't hot swapping. We need to pull off the previously created
        // UnresolvedModule - we can't scan this again since the module is probably transpiled now.
        if (metadata.path() != null) {
          Module module = compiler.getModuleMap().getModule(metadata.path());
          unresolvedModules.put(metadata.path().toModuleName(), module.unresolvedModule());
          module.unresolvedModule().reset();
        }

        for (String namespace : metadata.googNamespaces()) {
          Module module = compiler.getModuleMap().getClosureModule(namespace);
          unresolvedModulesByClosureNamespace.put(namespace, module.unresolvedModule());
          module.unresolvedModule().reset();
        }
      }
    }

    compiler.setModuleMap(resolve());
  }
}
