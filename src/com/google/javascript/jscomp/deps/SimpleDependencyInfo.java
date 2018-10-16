/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Collection;
import java.util.Map;

/**
 * A class to hold JS dependency information for a single .js file.
 */
@Immutable @AutoValue @AutoValue.CopyAnnotations
public abstract class SimpleDependencyInfo extends DependencyInfo.Base {

  public static Builder builder(String srcPathRelativeToClosure, String pathOfDefiningFile) {
    return new AutoValue_SimpleDependencyInfo.Builder()
        .setName(pathOfDefiningFile)
        .setPathRelativeToClosureBase(srcPathRelativeToClosure)
        .setProvides(ImmutableList.of())
        .setRequires(ImmutableList.of())
        .setTypeRequires(ImmutableList.of())
        .setLoadFlags(ImmutableMap.of());
  }

  /**
   * Builder for constructing instances of SimpleDependencyInfo.
   * Use the {@link SimpleDependencyInfo#builder(String, String)} method to create an instance.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public static Builder from(DependencyInfo copy) {
      return new AutoValue_SimpleDependencyInfo.Builder()
          .setName(copy.getName())
          .setPathRelativeToClosureBase(copy.getPathRelativeToClosureBase())
          .setProvides(copy.getProvides())
          .setRequires(copy.getRequires())
          .setTypeRequires(copy.getTypeRequires())
          .setLoadFlags(copy.getLoadFlags());
    }

    abstract Builder setName(String name);
    abstract Builder setPathRelativeToClosureBase(String srcPathRelativeToClosure);

    public abstract Builder setProvides(Collection<String> provides);
    public abstract Builder setProvides(String... provides);

    public abstract Builder setRequires(Collection<Require> requires);

    public abstract Builder setRequires(Require... requires);

    public abstract Builder setTypeRequires(Collection<String> typeRequires);

    public abstract Builder setTypeRequires(String... typeRequires);

    public abstract Builder setLoadFlags(Map<String, String> loadFlags);

    private static final ImmutableMap<String, String> GOOG_MODULE_FLAGS =
        ImmutableMap.of("module", "goog");

    public Builder setGoogModule(boolean isModule) {
      return setLoadFlags(isModule ? GOOG_MODULE_FLAGS : ImmutableMap.of());
    }

    public abstract SimpleDependencyInfo build();
  }

  public static final SimpleDependencyInfo EMPTY = builder("", "").build();
}
