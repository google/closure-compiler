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
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Export;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.jstype.JSType;
import java.util.List;
import javax.annotation.Nullable;

/** Centralized location for determining how to rename modules. */
final class ModuleRenaming {

  private ModuleRenaming() {}

  /**
   * The name of the temporary variable created for the default export before globalization of the
   * module.
   */
  static final String DEFAULT_EXPORT_VAR_PREFIX = "$jscompDefaultExport";

  /** Returns the global name of a variable declared in an ES module. */
  static QualifiedName getGlobalNameOfEsModuleLocalVariable(
      ModuleMetadata moduleMetadata, String variableName) {
    return QualifiedName.of(
        variableName + "$$" + getGlobalName(moduleMetadata, /* googNamespace= */ null).join());
  }

  /** Returns the global name of the anonymous default export for the given module. */
  static QualifiedName getGlobalNameOfAnonymousDefaultExport(ModuleMetadata moduleMetadata) {
    return getGlobalNameOfEsModuleLocalVariable(moduleMetadata, DEFAULT_EXPORT_VAR_PREFIX);
  }

  /**
   * @param moduleMetadata the metadata of the module to get the global name of
   * @param googNamespace the Closure namespace that is being referenced fromEsModule this module,
   *     if any
   * @return the global, qualified name to rewrite any references to this module to
   */
  static QualifiedName getGlobalName(
      ModuleMetadata moduleMetadata, @Nullable String googNamespace) {
    return GlobalizedModuleName.create(moduleMetadata, googNamespace, null).aliasName();
  }

  /** Returns the post-transpilation, globalized name of the export. */
  static QualifiedName getGlobalName(Export export) {
    if (export.moduleMetadata().isEs6Module()) {
      if (export.localName().equals(Export.DEFAULT_EXPORT_NAME)) {
        return getGlobalNameOfAnonymousDefaultExport(export.moduleMetadata());
      }
      return getGlobalNameOfEsModuleLocalVariable(export.moduleMetadata(), export.localName());
    }
    return getGlobalName(export.moduleMetadata(), export.closureNamespace())
        .getprop(export.exportName());
  }

  /** Returns the post-transpilation, globalized name of the binding. */
  static QualifiedName getGlobalName(Binding binding) {
    if (binding.isModuleNamespace()) {
      return getGlobalName(binding.metadata(), binding.closureNamespace());
    }
    return getGlobalName(binding.originatingExport());
  }

  private static JSType getNameRootType(String qname, @Nullable TypedScope globalTypedScope) {
    if (globalTypedScope == null) {
      return null;
    }
    String root = NodeUtil.getRootOfQualifiedName(qname);
    return checkNotNull(globalTypedScope.getVar(root), "missing var for %s", root).getType();
  }

  /**
   * Returns the globalized name of a reference to a binding in JS Doc.
   *
   * <p>For example:
   *
   * <pre>
   *   // bar
   *   export class Bar {}
   * </pre>
   *
   * <pre>
   *   // foo
   *   import * as bar from 'bar';
   *   export {bar};
   * </pre>
   *
   * <pre>
   *   import * as foo from 'foo';
   *   let /** !foo.bar.Bar *\/ b;
   * </pre>
   *
   * <p>Should call this method with the binding for {@code foo} and a list ("bar", "Bar"). In this
   * example any of these properties could also be modules. This method will replace as much as the
   * GETPROP as it can with module exported variables. Meaning in the above example this would
   * return something like "baz$$module$bar", whereas if this method were called for just "foo.bar"
   * it would return "module$bar", as it refers to a module object itself.
   */
  static String getGlobalNameForJsDoc(
      ModuleMap moduleMap, Binding binding, List<String> propertyChain) {
    int prop = 0;

    while (binding.isModuleNamespace()
        && binding.metadata().isEs6Module()
        && prop < propertyChain.size()) {
      String propertyName = propertyChain.get(prop);
      Module m = moduleMap.getModule(binding.metadata().path());
      if (m.namespace().containsKey(propertyName)) {
        binding = m.namespace().get(propertyName);
      } else {
        // This means someone referenced an invalid export on a module object. This should be an
        // error, so just rewrite and let the type checker complain later. It isn't a super clear
        // error, but we're working on type checking modules soon.
        break;
      }
      prop++;
    }

    QualifiedName globalName = getGlobalName(binding);

    while (prop < propertyChain.size()) {
      globalName = globalName.getprop(propertyChain.get(prop++));
    }

    return globalName.join();
  }

  /**
   * Stores a fully qualified globalized name of a module or provide + the type of its root node.
   *
   * <p>For goog.provides and legacy goog.modules, the fully qualified name is just the namespace of
   * the module "a.b.c.d". For ES modules, it's deterministically generated based on the module
   * path, e.g. "module$path$to$my$dir". For non-legacy goog.modules, it's deterministically
   * generated based on the module name, e.g. $module$exports$a$b$c$d".
   *
   * <p>Used because in some cases, the root of the qualified name will not be present in any scopes
   * until a subsequent run of {@link ClosureRewriteModule}. It's useful to store the type of
   * Closure module exports `module$exports$my$goog$module` separately.
   */
  @AutoValue
  abstract static class GlobalizedModuleName {
    abstract QualifiedName aliasName();
    // The type of the root of `aliasName`, as it may not always exist in the scope yet.
    @Nullable
    abstract JSType rootNameType();

    /** Creates a GETPROP chain with type information representing this name */
    Node toQname(AstFactory astFactory) {
      Node rootName = astFactory.createName(this.aliasName().getRoot(), type(this.rootNameType()));
      if (this.aliasName().isSimple()) {
        return rootName;
      }
      return astFactory.createGetPropsWithoutColors(
          rootName, Iterables.skip(this.aliasName().components(), 1));
    }

    /**
     * Returns a copy of this name with the given {@code property} appended to the {@link
     * #aliasName()}
     */
    GlobalizedModuleName getprop(String property) {
      Preconditions.checkArgument(!property.isEmpty() && !property.contains("."));
      return GlobalizedModuleName.create(aliasName().getprop(property), rootNameType());
    }

    static GlobalizedModuleName create(QualifiedName aliasName, JSType rootNameType) {
      return new AutoValue_ModuleRenaming_GlobalizedModuleName(aliasName, rootNameType);
    }

    /**
     * @param moduleMetadata the metadata of the module to get the global name of
     * @param googNamespace the Closure namespace that is being referenced fromEsModule this module,
     *     if any
     * @param globalTypedScope A global scope expected to contain the types of goog.provided names
     *     and rewritten ES6 module names
     * @return the global, qualified name to rewrite any references to this module to, along with
     *     the type of the root of the module if {@code globalTypedScope} is not null.
     */
    static GlobalizedModuleName create(
        ModuleMetadata moduleMetadata,
        @Nullable String googNamespace,
        @Nullable TypedScope globalTypedScope) {
      checkState(googNamespace == null || moduleMetadata.googNamespaces().contains(googNamespace));
      switch (moduleMetadata.moduleType()) {
        case GOOG_MODULE:
          // The exported type is stored on the MODULE_BODY node.
          Node moduleBody = moduleMetadata.rootNode().getFirstChild();
          return new AutoValue_ModuleRenaming_GlobalizedModuleName(
              QualifiedName.of(ClosureRewriteModule.getBinaryModuleNamespace(googNamespace)),
              moduleBody.getJSType());
        case GOOG_PROVIDE:
        case LEGACY_GOOG_MODULE:
          return new AutoValue_ModuleRenaming_GlobalizedModuleName(
              QualifiedName.of(googNamespace), getNameRootType(googNamespace, globalTypedScope));
        case ES6_MODULE:
        case COMMON_JS:
          return new AutoValue_ModuleRenaming_GlobalizedModuleName(
              QualifiedName.of(moduleMetadata.path().toModuleName()),
              getNameRootType(moduleMetadata.path().toModuleName(), globalTypedScope));
        case SCRIPT:
          // fall through, throw an error
      }
      throw new IllegalStateException("Unexpected module type: " + moduleMetadata.moduleType());
    }
  }
}
