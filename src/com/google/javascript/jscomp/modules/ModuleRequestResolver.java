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

import org.jspecify.nullness.Nullable;

/** Resolves requests for other modules. */
public interface ModuleRequestResolver {
  /** Returns the module that this import references, if it exists in the compilation. */
  @Nullable
  UnresolvedModule resolve(Import i);

  /** Returns the module that this export references, if it exists in the compilation. */
  @Nullable
  UnresolvedModule resolve(Export e);
}
