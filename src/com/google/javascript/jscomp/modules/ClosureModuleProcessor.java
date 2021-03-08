/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.modules.ModuleMapCreator.DOES_NOT_HAVE_EXPORT_WITH_DETAILS;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPreOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.ClosureRequireProcessor.Require;
import com.google.javascript.jscomp.modules.ModuleMapCreator.ModuleProcessor;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Processor for goog.module
 *
 * <p>The namespace of a goog.module contains all named exports, e.g. {@code exports.x = 0}, and any
 * 'default export's that assign directly to the `exports` object, e.g. {@code exports = class {}}).
 *
 * <p>The bound names include any names imported through a goog.require(Type)/forwardDeclare.
 */
final class ClosureModuleProcessor implements ModuleProcessor {

  private static class UnresolvedGoogModule extends UnresolvedModule {

    private final ModuleMetadata metadata;
    private final String srcFileName;
    @Nullable private final ModulePath path;
    private final ImmutableMap<String, Binding> namespace;
    private final ImmutableMap<String, Require> requiresByLocalName;
    private final AbstractCompiler compiler;
    private Module resolved = null;

    UnresolvedGoogModule(
        ModuleMetadata metadata,
        String srcFileName,
        ModulePath path,
        ImmutableMap<String, Binding> namespace,
        ImmutableMap<String, Require> requires,
        AbstractCompiler compiler) {
      this.metadata = metadata;
      this.srcFileName = srcFileName;
      this.path = path;
      this.namespace = namespace;
      this.requiresByLocalName = requires;
      this.compiler = compiler;
    }

    @Nullable
    @Override
    public ResolveExportResult resolveExport(
        ModuleRequestResolver moduleRequestResolver, String exportName) {
      if (namespace.containsKey(exportName)) {
        return ResolveExportResult.of(namespace.get(exportName));
      }
      return ResolveExportResult.NOT_FOUND;
    }

    @Nullable
    @Override
    public ResolveExportResult resolveExport(
        ModuleRequestResolver moduleRequestResolver,
        @Nullable String moduleSpecifier,
        String exportName,
        Set<ExportTrace> resolveSet,
        Set<UnresolvedModule> exportStarSet) {
      return resolveExport(moduleRequestResolver, exportName);
    }

    @Override
    public Module resolve(
        ModuleRequestResolver moduleRequestResolver, @Nullable String moduleSpecifier) {
      if (resolved == null) {
        // Every import creates a locally bound name.
        Map<String, Binding> boundNames =
            new LinkedHashMap<>(getAllResolvedImports(moduleRequestResolver));

        resolved =
            Module.builder()
                .path(path)
                .metadata(metadata)
                .namespace(namespace)
                .boundNames(ImmutableMap.copyOf(boundNames))
                .localNameToLocalExport(ImmutableMap.of())
                .closureNamespace(Iterables.getOnlyElement(metadata.googNamespaces()))
                .unresolvedModule(this)
                .build();
      }
      return resolved;
    }

    /** A map from import bound name to binding. */
    Map<String, Binding> getAllResolvedImports(ModuleRequestResolver moduleRequestResolver) {
      Map<String, Binding> imports = new HashMap<>();

      for (String name : requiresByLocalName.keySet()) {
        ResolveExportResult b = resolveImport(moduleRequestResolver, name);
        if (b.resolved()) {
          imports.put(name, b.getBinding());
        }
      }

      return imports;
    }

    ResolveExportResult resolveImport(ModuleRequestResolver moduleRequestResolver, String name) {
      Require require = requiresByLocalName.get(name);
      Import importRecord = require.importRecord();

      UnresolvedModule requested = moduleRequestResolver.resolve(importRecord);

      if (requested == null) {
        return ResolveExportResult.ERROR;
      } else if (importRecord.importName().equals(Export.NAMESPACE)) {
        // Return a binding based on the other module's metadata.
        return ResolveExportResult.of(
            Binding.from(
                requested.metadata(),
                importRecord.nameNode(),
                importRecord.moduleRequest(),
                require.createdBy()));

      } else {
        ResolveExportResult result =
            requested.resolveExport(
                moduleRequestResolver,
                importRecord.moduleRequest(),
                importRecord.importName(),
                new HashSet<>(),
                new HashSet<>());
        if (!result.found() && !result.hadError()) {
          reportInvalidDestructuringRequire(requested, importRecord);
          return ResolveExportResult.ERROR;
        }
        Node forSourceInfo =
            importRecord.nameNode() == null ? importRecord.importNode() : importRecord.nameNode();
        return result.copy(forSourceInfo, require.createdBy());
      }
    }

    @Override
    ModuleMetadata metadata() {
      return metadata;
    }

    @Override
    public ImmutableSet<String> getExportedNames(ModuleRequestResolver moduleRequestResolver) {
      // Unsupported until such time as it becomes useful
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableSet<String> getExportedNames(
        ModuleRequestResolver moduleRequestResolver, Set<UnresolvedModule> visited) {
      throw new UnsupportedOperationException();
    }

    @Override
    void reset() {
      resolved = null;
    }

    /** Reports an error given an invalid destructuring require. */
    private void reportInvalidDestructuringRequire(
        UnresolvedModule requested, Import importRecord) {
      String additionalInfo = "";
      if (requested instanceof UnresolvedGoogModule) {
        // Detect some edge cases and given more helpful error messages.
        Map<String, Binding> exports = ((UnresolvedGoogModule) requested).namespace;
        if (exports.containsKey(Export.NAMESPACE)) {
          // Can't use destructuring imports on a goog.module with a default export like
          //   exports = class {
          // (even if there is an assignment like `exports.Bar = 0;` later)
          additionalInfo =
              Strings.lenientFormat( // Use Strings.lenientFormat for GWT/J2CL compatability
                  "\n"
                      + "The goog.module \"%s\" cannot be destructured as it contains a default"
                      + " export, not named exports. See %s.",
                  importRecord.moduleRequest(),
                  "https://github.com/google/closure-library/wiki/goog.module%3A-an-ES6-module-like-alternative-to-goog.provide#destructuring-imports");

          if (mayBeAccidentalDefaultExport(importRecord.importName(), exports)) {
            // Give the user a more detailed error message, since this is a tricky edge case.
            additionalInfo +=
                Strings.lenientFormat(
                    "\n"
                        + "Either use a non-destructuring require or rewrite the goog.module"
                        + " \"%s\" to support destructuring requires. For example, consider"
                        + " replacing\n"
                        + "  exports = {%s: <value>[, ...]};\n"
                        + "with individual named export assignments like\n"
                        + "  exports.%s = <value>;\n",
                    importRecord.moduleRequest(),
                    importRecord.importName(),
                    importRecord.importName());
          }
        }
      }
      compiler.report(
          JSError.make(
              srcFileName,
              importRecord.importNode().getLineno(),
              importRecord.importNode().getCharno(),
              DOES_NOT_HAVE_EXPORT_WITH_DETAILS,
              importRecord.importName(),
              additionalInfo));
    }
  }

  /**
   * Returns whether the user appears to have confused a default export of an object literal with
   * the named export object literal shorthand.
   *
   * <p>Basically, `exports = {foo: 0};`, does /not/ create a 'named export' of 'foo' because 0 is
   * not a name. So users cannot destructuring-require `const {foo} = goog.require('the.module');`.
   * However, if instead of `0` the user exported a name like `bar`, the user could use a
   * destructuring require.
   */
  private static boolean mayBeAccidentalDefaultExport(
      String importName, Map<String, Binding> exports) {
    Node defaultExport = exports.get(Export.NAMESPACE).originatingExport().exportNode();
    checkState(
        defaultExport.matchesName("exports") && defaultExport.getParent().isAssign(),
        defaultExport);
    Node exportedValue = defaultExport.getNext();
    if (!exportedValue.isObjectLit()) {
      return false;
    }
    // Look for `importName` in the exported object literal.
    for (Node key = exportedValue.getFirstChild(); key != null; key = key.getNext()) {
      if (key.isStringKey() && key.getString().equals(importName)) {
        return true;
      }
    }
    return false;
  }

  private final AbstractCompiler compiler;

  public ClosureModuleProcessor(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public UnresolvedModule process(ModuleMetadata metadata, ModulePath path, Node script) {
    Preconditions.checkArgument(
        script.isScript() || script.isCall(), "Unexpected module root %s", script);
    Preconditions.checkArgument(
        script.isCall() || path != null, "Non goog.loadModules must have a path");

    ModuleProcessingCallback moduleProcessingCallback = new ModuleProcessingCallback(metadata);
    NodeTraversal.traverse(compiler, script, moduleProcessingCallback);
    return new UnresolvedGoogModule(
        metadata,
        script.getSourceFileName(),
        path,
        ImmutableMap.copyOf(moduleProcessingCallback.namespace),
        ImmutableMap.copyOf(moduleProcessingCallback.requiresByLocalName),
        compiler);
  }

  /** Traverses a subtree rooted at a module, gathering all exports and requires */
  private static class ModuleProcessingCallback extends AbstractPreOrderCallback {
    private final ModuleMetadata metadata;
    /** The Closure namespace 'a.b.c' from the `goog.module('a.b.c');` statement */
    private final String closureNamespace;
    // Note: the following two maps are mutable because in some cases, we need to check if a key has
    // already been added before trying to add a second.

    /** All named exports and explicit assignments of the `exports` object */
    private final Map<String, Binding> namespace;
    /** All required/forwardDeclared local names */
    private final Map<String, Require> requiresByLocalName;
    /** Whether we've come across an "exports = ..." assignment */
    private boolean seenExportsAssignment;

    ModuleProcessingCallback(ModuleMetadata metadata) {
      this.metadata = metadata;
      this.namespace = new LinkedHashMap<>();
      this.requiresByLocalName = new LinkedHashMap<>();
      this.closureNamespace = Iterables.getOnlyElement(metadata.googNamespaces());
      this.seenExportsAssignment = false;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case MODULE_BODY:
        case SCRIPT:
        case CALL: // Traverse into goog.loadModule calls.
        case BLOCK:
          return true;
        case FUNCTION:
          // Only traverse into functions that are the argument of a goog.loadModule call, which is
          // the module root. Avoid traversing function declarations like:
          //     goog.module('a.b'); function (exports) { exports.x = 0; }
          return parent.isCall() && parent == metadata.rootNode();
        case EXPR_RESULT:
          Node expr = n.getFirstChild();
          if (expr.isAssign()) {
            maybeInitializeExports(expr);
          } else if (expr.isGetProp()) {
            maybeInitializeExportsStub(expr);
          }
          return false;
        case CONST:
        case VAR:
        case LET:
          // Note that `let` is valid only for `goog.forwardDeclare`.
          maybeInitializeRequire(n);
          return false;
        default:
          return false;
      }
    }

    /** If an assignment is to 'exports', adds it to the list of Exports */
    private void maybeInitializeExports(Node assignment) {
      Node lhs = assignment.getFirstChild();
      Node rhs = assignment.getSecondChild();
      if (lhs.isName() && lhs.getString().equals("exports")) {
        // This may be a 'named exports' or may be a default export.
        // It is a 'named export' if and only if it is assigned an object literal w/ string keys,
        // whose values are all names.
        if (NodeUtil.isNamedExportsLiteral(rhs)) {
          initializeNamedExportsLiteral(rhs);
        } else {
          seenExportsAssignment = true;
        }
        markExportsAssignmentInNamespace(lhs);
      } else if (lhs.isGetProp()
          && lhs.getFirstChild().isName()
          && lhs.getFirstChild().getString().equals("exports")) {
        String exportedId = lhs.getString();
        addPropertyExport(exportedId, lhs);
      }
    }

    /** Adds stub export declarations `exports.Foo;` to the list of Exports */
    private void maybeInitializeExportsStub(Node qname) {
      Node owner = qname.getFirstChild();
      if (owner.isName() && owner.getString().equals("exports")) {
        addPropertyExport(qname.getString(), qname);
      }
    }

    /**
     * Adds an explicit namespace export.
     *
     * <p>Note that all goog.modules create an 'exports' object, but this object is only added to
     * the Module namespace if there is an explicit' exports = ...' assignment
     */
    private void markExportsAssignmentInNamespace(Node exportsNode) {
      namespace.put(
          Export.NAMESPACE,
          Binding.from(
              Export.builder()
                  .exportName(Export.NAMESPACE)
                  .exportNode(exportsNode)
                  .moduleMetadata(metadata)
                  .closureNamespace(closureNamespace)
                  .modulePath(metadata.path())
                  .build(),
              exportsNode));
    }

    private void initializeNamedExportsLiteral(Node objectLit) {
      for (Node key = objectLit.getFirstChild(); key != null; key = key.getNext()) {
        addPropertyExport(key.getString(), key);
      }
    }

    /** Adds a named export to the list of Exports */
    private void addPropertyExport(String exportedId, Node propNode) {
      if (seenExportsAssignment) {
        // We've seen an assignment "exports = ...", so this is not a named export.
        return;
      } else if (namespace.containsKey(exportedId)) {
        // Ignore duplicate exports - this is an error but checked elsewhere.
        return;
      }

      namespace.put(
          exportedId,
          Binding.from(
              Export.builder()
                  .exportName(exportedId)
                  .exportNode(propNode)
                  .moduleMetadata(metadata)
                  .closureNamespace(closureNamespace)
                  .modulePath(metadata.path())
                  .build(),
              propNode));
    }

    /** Adds a goog.require(Type) or forwardDeclare to the list of {@code requiresByLocalName} */
    private void maybeInitializeRequire(Node nameDeclaration) {
      for (Require require : ClosureRequireProcessor.getAllRequires(nameDeclaration)) {
        requiresByLocalName.putIfAbsent(require.localName(), require);
      }
    }
  }
}
