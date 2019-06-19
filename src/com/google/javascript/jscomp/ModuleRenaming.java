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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Export;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
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
  static String getGlobalNameOfEsModuleLocalVariable(
      ModuleMetadata moduleMetadata, String variableName) {
    return variableName + "$$" + getGlobalName(moduleMetadata, /* googNamespace= */ null);
  }

  /** Returns the global name of the anonymous default export for the given module. */
  static String getGlobalNameOfAnonymousDefaultExport(ModuleMetadata moduleMetadata) {
    return getGlobalNameOfEsModuleLocalVariable(moduleMetadata, DEFAULT_EXPORT_VAR_PREFIX);
  }

  /**
   * @param moduleMetadata the metadata of the module to get the global name of
   * @param googNamespace the Closure namespace that is being referenced fromEsModule this module,
   *     if any
   * @return the global, qualified name to rewrite any references to this module to
   */
  static String getGlobalName(ModuleMetadata moduleMetadata, @Nullable String googNamespace) {
    checkState(googNamespace == null || moduleMetadata.googNamespaces().contains(googNamespace));
    switch (moduleMetadata.moduleType()) {
      case GOOG_MODULE:
        return ClosureRewriteModule.getBinaryModuleNamespace(googNamespace);
      case GOOG_PROVIDE:
      case LEGACY_GOOG_MODULE:
        return checkNotNull(googNamespace);
      case ES6_MODULE:
      case COMMON_JS:
        return moduleMetadata.path().toModuleName();
      case SCRIPT:
        // fall through, throw an error
    }
    throw new IllegalStateException("Unexpected module type: " + moduleMetadata.moduleType());
  }

  /** Returns the post-transpilation, globalized name of the export. */
  static String getGlobalName(Export export) {
    if (export.moduleMetadata().isEs6Module()) {
      if (export.localName().equals(Export.DEFAULT_EXPORT_NAME)) {
        return getGlobalNameOfAnonymousDefaultExport(export.moduleMetadata());
      }
      return getGlobalNameOfEsModuleLocalVariable(export.moduleMetadata(), export.localName());
    }
    return getGlobalName(export.moduleMetadata(), export.closureNamespace())
        + "."
        + export.exportName();
  }

  /** Returns the post-transpilation, globalized name of the binding. */
  static String getGlobalName(Binding binding) {
    if (binding.isModuleNamespace()) {
      return getGlobalName(binding.metadata(), binding.closureNamespace());
    }
    return getGlobalName(binding.originatingExport());
  }

  /**
   * Returns the globalized name of a reference to a binding in JS Doc. See {@link
   * #replace(AbstractCompiler, ModuleMap, Binding, Node)} to replace actual code nodes.
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

    String globalName = getGlobalName(binding);

    if (prop < propertyChain.size()) {
      globalName =
          globalName + "." + Joiner.on('.').join(propertyChain.subList(prop, propertyChain.size()));
    }

    return globalName;
  }

  /**
   * Replaces the reference to a given binding. See {@link #getGlobalNameForJsDoc(ModuleMap,
   * Binding, List)} for a JS Doc version.
   *
   * <p>For example:
   *
   * <pre>
   *   // bar
   *   export let baz = {qux: 0};
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
   *   use(foo.bar.baz.qux);
   * </pre>
   *
   * <p>Should call this method with the binding and node for {@code foo}. In this example any of
   * these properties could also be modules. This method will replace as much as the GETPROP as it
   * can with module exported variables. Meaning in the above example this would return something
   * like "baz$$module$bar.qux", whereas if this method were called for just "foo.bar" it would
   * return "module$bar", as it refers to a module object itself.
   *
   * @param binding the binding nameNode is a reference to
   * @param nameNode the node to replace
   */
  static Node replace(
      AbstractCompiler compiler, ModuleMap moduleMap, Binding binding, Node nameNode) {
    checkState(nameNode.isName());
    Node n = nameNode;

    while (binding.isModuleNamespace()
        && binding.metadata().isEs6Module()
        && n.getParent().isGetProp()) {
      String propertyName = n.getParent().getSecondChild().getString();
      Module m = moduleMap.getModule(binding.metadata().path());
      if (m.namespace().containsKey(propertyName)) {
        binding = m.namespace().get(propertyName);
        n = n.getParent();
      } else {
        // This means someone referenced an invalid export on a module object. This should be an
        // error, so just rewrite and let the type checker complain later. It isn't a super clear
        // error, but we're working on type checking modules soon.
        break;
      }
    }

    String globalName = getGlobalName(binding);
    Node newNode = NodeUtil.newQName(compiler, globalName);

    // For kythe: the new node only represents the last name it replaced, not all the names.
    // e.g. if we rewrite `a.b.c.d.e` to `x.d.e`, then `x` should map to `c`, not `a.b.c`.
    Node forSourceInfo = n.isGetProp() ? n.getSecondChild() : n;

    n.replaceWith(newNode);
    newNode.srcrefTree(forSourceInfo);
    newNode.setOriginalName(forSourceInfo.getString());
    return newNode;
  }
}
