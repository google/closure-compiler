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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.deps.ModuleLoader;
import javax.annotation.Nullable;

/**
 * Information for modules, particularly ES modules, that is useful for rewriting. The primary
 * pieces of information are what variables are exported (transitive or local), and what names are
 * imported.
 */
@AutoValue
public abstract class Module {
  // Prevent unwanted subclasses.
  Module() {}

  public abstract ModuleMetadata metadata();

  /** Path of this module. Null if this module is a nested {@code goog.loadModule}. */
  @Nullable
  public abstract ModuleLoader.ModulePath path();

  /**
   * Map of exported identifiers to originating binding.
   *
   * <p>Note that the keys are different than {@link #boundNames()}. Here the keys are the exported
   * name, not the local name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Locally defined exports (e.g. <code>export let x;</code>, <code>let x; export {x as v};
   *       </code>) creates an entry with the exported name for the local module's export
   *       definition.
   *   <li><code>export default function foo() {};</code> creates an entry with the name "default"
   *       for the local module's default export definition.
   *   <li><code>export {x as v} from 'mod';</code> creates an entry with the name "v" for the
   *       export definition x from 'mod'.
   *   <li><code>import</code> statements make no entries on their own. If imported values are
   *       exported with <code> export {};</code> then an entry is created like <code>export {} from
   *       </code>.
   * </ul>
   */
  public abstract ImmutableMap<String, Binding> namespace();

  /**
   * Map of local identifiers to originating binding.
   *
   * <p>This includes all names bound by import and exported names which originate in this module.
   * Used for rewriting in later stages of the compiler.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li><code>import {x as v} from 'mod';</code> creates an entry with the name "v" for the
   *       export definition x from 'mod'.
   *   <li><code>import * as ns from 'mod';</code> creates an entry with the name "ns" with a
   *       binding containing all of mod's bindings.
   *   <li><code>export default function foo() {}</code> creates an entry with the name "foo" for
   *       the local module's export definition.
   *   <li><code>export {x as v} from 'mod';</code> does not create any entry in this module.
   * </ul>
   */
  public abstract ImmutableMap<String, Binding> boundNames();

  /** Map of local identifier name to local export definition. */
  public abstract ImmutableMap<String, Export> localNameToLocalExport();

  /**
   * The specific Closure namespace this module represents, if any. This can be from {@code
   * goog.provide}, {@code goog.module}, or {@code goog.module.declareNamespace}. Null otherwise.
   */
  @Nullable
  public abstract String closureNamespace();

  static Builder builder() {
    return new AutoValue_Module.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder metadata(ModuleMetadata value);

    abstract Builder path(@Nullable ModuleLoader.ModulePath value);

    abstract Builder namespace(ImmutableMap<String, Binding> value);

    abstract Builder boundNames(ImmutableMap<String, Binding> value);

    abstract Builder localNameToLocalExport(ImmutableMap<String, Export> value);

    abstract Builder closureNamespace(@Nullable String value);

    abstract Module build();
  }
}
