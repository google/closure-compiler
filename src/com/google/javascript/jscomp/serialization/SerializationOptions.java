/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;

/** Configuration options for serialization time. */
public record SerializationOptions(
    boolean includeDebugInfo, boolean runValidation, ImmutableList<String> runtimeLibraries) {
  public SerializationOptions {
    requireNonNull(runtimeLibraries, "runtimeLibraries");
  }

  public static Builder builder() {
    return new AutoBuilder_SerializationOptions_Builder()
        .setRunValidation(false)
        .setIncludeDebugInfo(false)
        .setRuntimeLibraries(ImmutableList.of());
  }

  /** Builder for {@link SerializationOptions}. */
  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setIncludeDebugInfo(boolean includeDebugInfo);

    public abstract Builder setRunValidation(boolean runValidation);

    public abstract Builder setRuntimeLibraries(ImmutableList<String> runtimeLibraries);

    public abstract SerializationOptions build();
  }

}
