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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** Configuration options for serialization time. */
@AutoValue
public abstract class SerializationOptions {

  public static Builder builder() {
    return new AutoValue_SerializationOptions.Builder()
        .setRunValidation(false)
        .setIncludeDebugInfo(false)
        .setRuntimeLibraries(ImmutableList.of());
  }

  /** Builder for {@link SerializationOptions}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setIncludeDebugInfo(boolean includeDebugInfo);

    public abstract Builder setRunValidation(boolean runValidation);

    public abstract Builder setRuntimeLibraries(ImmutableList<String> runtimeLibraries);

    public abstract SerializationOptions build();
  }

  public abstract boolean getIncludeDebugInfo();

  public abstract boolean getRunValidation();

  public abstract ImmutableList<String> getRuntimeLibraries();
}
