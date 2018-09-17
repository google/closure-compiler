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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.ModuleMetadataMap.ModuleMetadata;
import javax.annotation.Nullable;

/** Centralized location for determining how to rename modules. */
final class ModuleRenaming {
  /**
   * @param moduleMetadata the metadata of the module to get the global name of
   * @param googNamespace the Closure namespace that is being referenced from this module, if any
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
}
