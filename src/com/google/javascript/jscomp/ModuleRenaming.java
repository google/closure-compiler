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

import com.google.javascript.jscomp.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.Binding;
import com.google.javascript.jscomp.modules.Export;
import javax.annotation.Nullable;

/** Centralized location for determining how to rename modules. */
final class ModuleRenaming {

  private ModuleRenaming() {}

  // TODO(johnplaisted): Consolidate this and Es6RewriteModule's constant.
  private static final String DEFAULT_EXPORT_VAR_PREFIX = "$jscompDefaultExport";

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
        return googNamespace;
      case ES6_MODULE:
      case COMMON_JS:
        return moduleMetadata.path().toModuleName();
      case SCRIPT:
        // fall through, throw an error
    }
    throw new IllegalStateException("Unexpected module type: " + moduleMetadata.moduleType());
  }

  static String getGlobalName(Export export) {
    if (export.moduleMetadata().isEs6Module()) {
      String prefix = checkNotNull(export.localName());
      if (export.localName().equals(Export.DEFAULT_EXPORT_NAME)) {
        prefix = DEFAULT_EXPORT_VAR_PREFIX;
      }
      return prefix + "$$" + getGlobalName(export.moduleMetadata(), /* googNamespace= */ null);
    }
    return getGlobalName(export.moduleMetadata(), export.closureNamespace())
        + "."
        + export.localName();
  }

  static String getGlobalName(Binding binding) {
    if (binding.isModuleNamespace()) {
      return getGlobalName(binding.metadata(), binding.closureNamespace());
    }
    return getGlobalName(binding.originatingExport());
  }
}
