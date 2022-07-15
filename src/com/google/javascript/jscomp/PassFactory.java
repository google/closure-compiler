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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.ForOverride;
import java.util.function.Function;

/**
 * A factory for creating JSCompiler passes based on the Options injected.
 *
 * <p>Contains all meta-data about compiler passes (like whether it can be run multiple times, a
 * human-readable name for logging, etc.).
 */
@AutoValue
public abstract class PassFactory {

  /** The name of the pass as it will appear in logs. */
  public abstract String getName();

  public abstract Function<CompilerOptions, Boolean> getCondition();

  /** Whether this factory must or must not appear in a {@link PhaseOptimizer} loop. */
  public abstract boolean isRunInFixedPointLoop();

  /**
   * A simple factory function for creating actual pass instances.
   *
   * <p>Users should call {@link #create(AbstractCompiler)} rather than use this object directly.
   */
  abstract Function<AbstractCompiler, ? extends CompilerPass> getInternalFactory();

  public abstract Builder toBuilder();

  PassFactory() {
    // Subclasses in this package only.
  }

  /** A builder for a {@link PassFactory}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String x);

    public abstract Builder setRunInFixedPointLoop(boolean b);

    public abstract Builder setCondition(Function<CompilerOptions, Boolean> cond);

    public abstract Builder setInternalFactory(
        Function<AbstractCompiler, ? extends CompilerPass> x);

    @ForOverride
    abstract PassFactory autoBuild();

    public final PassFactory build() {
      PassFactory result = autoBuild();
      checkState(!result.getName().isEmpty());
      return result;
    }
  }

  public static Builder builder() {
    return new AutoValue_PassFactory.Builder()
        .setRunInFixedPointLoop(false)
        .setCondition((o) -> true);
  }

  /** Create a no-op pass that can only run once. Used to break up loops. */
  public static PassFactory createEmptyPass(String name) {
    return builder().setName(name).setInternalFactory((c) -> (externs, root) -> {}).build();
  }

  /** Creates a new compiler pass to be run. */
  final CompilerPass create(AbstractCompiler compiler) {
    return getInternalFactory().apply(compiler);
  }
}
