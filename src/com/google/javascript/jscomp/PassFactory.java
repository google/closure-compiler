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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
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

  /** Whether this factory must or must not appear in a {@link PhaseOptimizer} loop. */
  public abstract boolean isRunInFixedPointLoop();

  /**
   * The set of features that this pass understands.
   *
   * <p>Passes that can handle any code (no-op passes, extremely simple passes that are unlikely to
   * be broken by new features, etc.) should return {@link FeatureSet#latest()}.
   */
  public abstract FeatureSet getFeatureSet();

  /**
   * A simple factory function for creating actual pass instances.
   *
   * <p>Users should call {@link #create()} rather than use this object directly.
   */
  abstract Function<AbstractCompiler, ? extends CompilerPass> getInternalFactory();

  /** Whether or not his factory produces {@link HotSwapCompilerPass}es. */
  abstract boolean isHotSwapable();

  public abstract Builder toBuilder();

  PassFactory() {
    // Sublasses in this package only.
  }

  /** A builder for a {@link PassFactory}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String x);

    public abstract Builder setRunInFixedPointLoop(boolean b);

    public abstract Builder setFeatureSet(FeatureSet x);

    public abstract Builder setInternalFactory(
        Function<AbstractCompiler, ? extends CompilerPass> x);

    abstract Builder setHotSwapable(boolean x);

    @ForOverride
    abstract PassFactory autoBuild();

    public final PassFactory build() {
      PassFactory result = autoBuild();

      // Every pass must have a nonempty name.
      checkState(!result.getName().isEmpty());
      if (result.isHotSwapable()) {
        // HotSwap passes are for transpilation, not optimization, so running them in a loop
        // makes no sense.
        checkState(!result.isRunInFixedPointLoop());
      }

      return result;
    }
  }

  public static Builder builder() {
    return new AutoValue_PassFactory.Builder().setRunInFixedPointLoop(false).setHotSwapable(false);
  }

  public static Builder builderForHotSwap() {
    return new AutoValue_PassFactory.Builder().setRunInFixedPointLoop(false).setHotSwapable(true);
  }

  /** Create a no-op pass that can only run once. Used to break up loops. */
  public static PassFactory createEmptyPass(String name) {
    return builder()
        .setName(name)
        .setFeatureSet(FeatureSet.latest())
        .setInternalFactory((c) -> (CompilerPass) (externs, root) -> {})
        .build();
  }

  /** Creates a new compiler pass to be run. */
  final CompilerPass create(AbstractCompiler compiler) {
    return getInternalFactory().apply(compiler);
  }
}
