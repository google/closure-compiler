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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Creates a {@link ModuleMap}. */
public class ModuleMapCreator implements CompilerPass {
  static final DiagnosticType MISSING_NAMESPACE_IMPORT =
      DiagnosticType.error(
          "JSC_MISSING_NAMESPACE_IMPORT", "Imported Closure namespace \"{0}\" never defined.");

  /**
   * The result of resolving an export, which can be a valid binding, ambiguous, not found, or an
   * error.
   */
  static final class ResolveExportResult {
    private enum State {
      RESOLVED,
      AMBIGUOUS,
      NOT_FOUND,
      ERROR,
    }

    @Nullable private final Binding binding;
    private final State state;

    private ResolveExportResult(@Nullable Binding binding, State state) {
      this.binding = binding;
      this.state = state;
    }

    /** Creates a new result that has the given node for the source of the binding. */
    ResolveExportResult withSource(Node sourceNode) {
      checkNotNull(sourceNode);

      if (binding == null) {
        return this;
      }

      return new ResolveExportResult(binding.withSource(sourceNode), state);
    }

    /** True if there was an error resolving the export, false otherwise. */
    boolean hadError() {
      return state == State.ERROR;
    }

    /** True if the export is ambiguous, false otherwise. */
    boolean isAmbiguous() {
      return state == State.AMBIGUOUS;
    }

    /** True if the export was successfully resolved, false otherwise. */
    boolean resolved() {
      return state == State.RESOLVED;
    }

    /**
     * True if the export key exists on the given module, even if it is ambiguous or had an error.
     */
    public boolean found() {
      return state != State.NOT_FOUND;
    }

    @Nullable
    public Binding getBinding() {
      return binding;
    }

    /**
     * The result of resolving the export was ambiguous.
     *
     * <p>This happens when there are multiple {@code export * from} statements that end up causing
     * the same key to be re-exported.
     *
     * <p>When resolving an import or transitive export, if the result is ambiguous, an error should
     * be reported at the import / transitive export site, an then {@link #ERROR} returned so that
     * more ambiguous errors are not reported.
     */
    static final ResolveExportResult AMBIGUOUS = new ResolveExportResult(null, State.AMBIGUOUS);

    /**
     * The export was not found because the module never exported the key.
     *
     * <p>When resolving an import or transitive export, if the result is not found, an error should
     * be reported at the import / transitive export site, an then {@link #ERROR} returned so that
     * more ambiguous errors are not reported.
     */
    static final ResolveExportResult NOT_FOUND = new ResolveExportResult(null, State.NOT_FOUND);

    /**
     * There was an error resolving the export.
     *
     * <p>This can mean that:
     *
     * <ol>
     *   <li>When resolving a transitive export, the transitive export was not found.
     *   <li>When resolving a transitive export, the transitive export was ambiguous.
     *   <li>There was a cycle resolving an export.
     *   <li>The requested module does not exist.
     * </ol>
     *
     * <p>When resolving an import or transitive export, if the result is {@code ERROR}, then
     * resolving should also return {@code ERROR}. No error needs to be reported, this is an
     * indication that something has already been reported.
     */
    static final ResolveExportResult ERROR = new ResolveExportResult(null, State.ERROR);

    static ResolveExportResult of(Binding binding) {
      checkNotNull(binding);
      return new ResolveExportResult(binding, State.RESOLVED);
    }
  }

  /**
   * A module which has had some of its imports and exports statements scanned but has yet to
   * resolve anything transitively.
   */
  abstract static class UnresolvedModule {
    final Module resolve() {
      return resolve(/* moduleSpecifier= */ null);
    }

    /**
     * Resolves all imports and exports and returns a resolved module.
     *
     * @param moduleSpecifier the module specifier that was used to import this module, if resolving
     *     an import
     */
    abstract Module resolve(@Nullable String moduleSpecifier);

    abstract boolean isEsModule();

    /**
     * Returns all names in this module's namespace. Names are sorted per Java's string ordering,
     * which should be the same as JavaScript's Array.protype.sort, which is how the spec says these
     * keys should be ordered in the module object.
     */
    abstract ImmutableSet<String> getExportedNames();

    /**
     * Returns all names in this module's namespace. Names are sorted per Java's string ordering,
     * which should be the same as JavaScript's Array.prototype.sort, which is how the spec says
     * these keys should be ordered in the module object.
     *
     * @param visited set used to detect {@code export *} cycles.
     */
    protected abstract ImmutableSet<String> getExportedNames(Set<UnresolvedModule> visited);

    /**
     * @param exportName name of the export to resolve
     * @return the result of resolving the export, which can be one of several states:
     *     <ul>
     *       <li>The resolved export with the binding, if found.
     *       <li>A result indicating that the export is ambiguous.
     *       <li>A result indicating that the module has no such export.
     *       <li>A result indicating that there was some other error resolving, like a cycle, or a
     *           module transitively returned that there was no such export.
     *     </ul>
     */
    ResolveExportResult resolveExport(String exportName) {
      return resolveExport(
          /* moduleSpecifier= */ null, exportName, new HashSet<>(), new HashSet<>());
    }

    /**
     * @param moduleSpecifier the specifier used to reference this module, if this trace is from an
     *     import
     * @param exportName name of the export to resolve
     * @param resolveSet set used to detect invalid cycles. It is invalid to reach the same exact
     *     export (same module with the same export name) in a given cycle.
     * @param exportStarSet set used for cycle checking with {@code export *} statements
     * @return the result of resolving the export, which can be one of several states:
     *     <ul>
     *       <li>The resolved export with the binding, if found.
     *       <li>A result indicating that the export is ambiguous.
     *       <li>A result indicating that the module has no such export.
     *       <li>A result indicating that there was some other error resolving, like a cycle, or a
     *           module transitively returned that there was no such export.
     *     </ul>
     */
    abstract ResolveExportResult resolveExport(
        @Nullable String moduleSpecifier,
        String exportName,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet);

    // Reference equality is expected in ExportTrace. Prevent subclasses from changing this.
    @Override
    public final boolean equals(Object other) {
      return super.equals(other);
    }

    @Override
    public final int hashCode() {
      return super.hashCode();
    }
  }

  final class ModuleRequestResolver {
    private ModuleRequestResolver() {}

    @Nullable
    UnresolvedModule resolve(Import i) {
      return resolve(i.moduleRequest(), i.modulePath(), i.importNode());
    }

    @Nullable
    UnresolvedModule resolve(Export e) {
      return resolve(e.moduleRequest(), e.modulePath(), e.exportNode());
    }

    @Nullable
    private UnresolvedModule resolve(
        String moduleRequest, ModuleLoader.ModulePath modulePath, Node forLineInfo) {
      if (GoogEsImports.isGoogImportSpecifier(moduleRequest)) {
        String namespace = GoogEsImports.getClosureIdFromGoogImportSpecifier(moduleRequest);
        UnresolvedModule module = unresolvedModulesByClosureNamespace.get(namespace);
        if (module == null) {
          compiler.report(JSError.make(forLineInfo, MISSING_NAMESPACE_IMPORT, namespace));
        }
        return module;
      }

      ModuleLoader.ModulePath requestedPath =
          modulePath.resolveJsModule(
              moduleRequest,
              modulePath.toString(),
              forLineInfo.getLineno(),
              forLineInfo.getCharno());

      if (requestedPath == null) {
        return null;
      }

      return unresolvedModules.get(requestedPath.toModuleName());
    }
  }

  /** A basic interface that can scan and return information about a module. */
  interface ModuleProcessor {
    UnresolvedModule process(
        ModuleRequestResolver resolver, ModuleMetadata metadata, ModulePath path, Node script);
  }

  /**
   * Simple class to keep track of which modules and exports have been visited when resolving
   * exports. It is invalid to visit the same (module, name) pair more than once when resolving an
   * export (invalid cycle).
   *
   * <p>This is an AutoValue used for its hashCode / equals implementation and used in a Set for
   * equality checks. So fields may appear to be "unused".
   */
  @AutoValue
  abstract static class ExportTrace {
    static ExportTrace create(UnresolvedModule module, String exportName) {
      return new AutoValue_ModuleMapCreator_ExportTrace(module, exportName);
    }

    abstract UnresolvedModule module();

    abstract String exportName();
  }

  private final AbstractCompiler compiler;
  private final EsModuleProcessor esModuleProcessor;
  private final NonEsModuleProcessor nonEsModuleProcessor;
  private final Map<String, UnresolvedModule> unresolvedModules;
  private final Map<String, UnresolvedModule> unresolvedModulesByClosureNamespace;
  private final ModuleMetadataMap moduleMetadataMap;

  public ModuleMapCreator(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
    this.compiler = compiler;
    this.moduleMetadataMap = moduleMetadataMap;
    this.esModuleProcessor = new EsModuleProcessor(compiler);
    this.nonEsModuleProcessor = new NonEsModuleProcessor();
    unresolvedModules = new HashMap<>();
    unresolvedModulesByClosureNamespace = new HashMap<>();
  }

  private ModuleMap create() {
    ModuleRequestResolver requestResolver = new ModuleRequestResolver();
    Map<String, Module> resolvedModules = new HashMap<>();
    Map<String, Module> resolvedClosureModules = new HashMap<>();

    unresolvedModules.clear();
    unresolvedModulesByClosureNamespace.clear();

    // There are modules that aren't associated with scripts - nested goog.modules in
    // goog.loadModule calls.
    for (ModuleMetadata moduleMetadata : moduleMetadataMap.getAllModuleMetadata()) {

      ModuleProcessor processor =
          moduleMetadata.isEs6Module() ? esModuleProcessor : nonEsModuleProcessor;
      UnresolvedModule module =
          processor.process(
              requestResolver, moduleMetadata, moduleMetadata.path(), moduleMetadata.rootNode());

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

    for (Map.Entry<String, UnresolvedModule> e : unresolvedModules.entrySet()) {
      Module resolved = e.getValue().resolve();
      resolvedModules.put(e.getKey(), resolved);

      for (String namespace : resolved.metadata().googNamespaces()) {
        resolvedClosureModules.put(namespace, resolved);
      }
    }

    for (Map.Entry<String, UnresolvedModule> e : unresolvedModulesByClosureNamespace.entrySet()) {
      resolvedClosureModules.put(e.getKey(), e.getValue().resolve());
    }

    unresolvedModules.clear();
    unresolvedModulesByClosureNamespace.clear();

    return new ModuleMap(
        ImmutableMap.copyOf(resolvedModules), ImmutableMap.copyOf(resolvedClosureModules));
  }

  @Override
  public void process(Node externs, Node root) {
    compiler.setModuleMap(create());
  }
}
