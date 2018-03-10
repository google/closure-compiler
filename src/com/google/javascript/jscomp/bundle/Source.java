/*
 * Copyright 2017 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.bundle;

import static java.util.Arrays.asList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.deps.DependencyInfo;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** An abstract representation of a source file. */
@AutoValue
@GwtIncompatible
@Immutable
public abstract class Source {

  /** The path of this source. This may refer to a path on disk or a path on the HTTP server. */
  public abstract Path path();
  /** The text of any source map applicable to this file. */
  public abstract String sourceMap();
  /** The source URL associated with this file. */
  public abstract String sourceUrl();
  /** The URL for a source map associated with this file. */
  public abstract String sourceMappingUrl();
  /**
   * Any runtime libraries necessary for this source. Any transformation that adds a runtime library
   * to any sources must be responsible to never add the same library as a substring to a different
   * source (so that the "no duplicates" invariant of Set will work correctly).
   */
  public abstract ImmutableSet<String> runtimes();
  /** The load flags, specifying module type and language level. */
  public abstract ImmutableMap<String, String> loadFlags();
  /** A best estimate of the size of this source (in case the source itself is not yet loaded. */
  public abstract int estimatedSize();

  /** The actual code in this source file. */
  public final String code() {
    return codeSupplier().get();
  }

  /** The untransformed code from the original source file. */
  public final String originalCode() {
    return originalCodeSupplier().get();
  }

  /** Copies the data from this source to a new builder. */
  public abstract Builder toBuilder();

  /** Makes a new empty builder. */
  public static Builder builder() {
    return new AutoValue_Source.Builder()
        .setPath(DEV_NULL)
        .setCode("")
        .setOriginalCodeSupplier(null)
        .setSourceMap("")
        .setSourceUrl("")
        .setSourceMappingUrl("")
        .setRuntimes(ImmutableSet.of())
        .setLoadFlags(ImmutableMap.of())
        .setEstimatedSize(0);
  }

  private static final Path DEV_NULL = Paths.get("/dev/null");

  // Internal-only properties: the code suppliers are necessary for lazy bundling,
  // but we cannot use an ordinary supplier since we need guarantees about equals and
  // hash code.  Thus, we use an internal-only Supplier subtype.
  abstract Lazy<String> codeSupplier();

  @Nullable
  abstract Lazy<String> originalCodeSupplier();

  /** Builder for Source instances. */
  @AutoValue.Builder
  @GwtIncompatible
  public abstract static class Builder {
    public abstract Builder setPath(Path path);
    public abstract Builder setSourceMap(String sourceMap);
    public abstract Builder setSourceUrl(String sourceUrl);
    public abstract Builder setSourceMappingUrl(String sourceMappingUrl);
    public abstract Builder setRuntimes(ImmutableSet<String> runtimes);
    public abstract Builder setLoadFlags(ImmutableMap<String, String> flags);
    public abstract Builder setEstimatedSize(int estimatedSize);

    public final Builder setCode(Supplier<String> code) {
      return setCodeSupplier(Lazy.memoize(code));
    }

    public final Builder setCode(String code) {
      return setCodeSupplier(Lazy.ofInstance(code));
    }

    public final Builder setOriginalCode(String code) {
      return setOriginalCodeSupplier(Lazy.ofInstance(code));
    }

    public final Builder addRuntime(String... runtimes) {
      return setRuntimes(
          ImmutableSet.<String>builder().addAll(runtimes()).addAll(asList(runtimes)).build());
    }

    public final Builder setDependencyInfo(DependencyInfo info) {
      // TODO(sdh): consider whether to set path.
      return setLoadFlags(info.getLoadFlags());
    }

    public final Source build() {
      if (originalCodeSupplier() == null) {
        setOriginalCodeSupplier(codeSupplier());
      }
      return autoBuild();
    }

    // Internal-only getters and setters.
    abstract Builder setCodeSupplier(Lazy<String> code);
    abstract Builder setOriginalCodeSupplier(@Nullable Lazy<String> code);
    abstract ImmutableSet<String> runtimes();
    abstract Lazy<String> codeSupplier();
    abstract Source autoBuild();

    @Nullable
    abstract Lazy<String> originalCodeSupplier();
  }

  /** An automorphic transformation on sources. */
  @FunctionalInterface
  public interface Transformer {

    /** The main transformation method. */
    Source transform(Source input);

    static Transformer of(Function<Source, Source> function) {
      return function::apply;
    }

    /** Returns an identity transformer. */
    static Transformer identity() {
      return x -> x;
    }

    /** Converts this Transformer to a Function. */
    default Function<Source, Source> asFunction() {
      return this::transform;
    }

    /** Concatenates two Transformers. */
    @CheckReturnValue
    default Transformer andThen(Transformer after) {
      Transformer before = this;
      return x -> after.transform(before.transform(x));
    }

    /** Concatenates two Transformers. */
    @CheckReturnValue
    default Transformer compose(Transformer before) {
      Transformer after = this;
      return x -> after.transform(before.transform(x));
    }
  }

  /** Essentially the same as Supplier, but wraps equals and hashCode. */
  @GwtIncompatible
  @Immutable
  abstract static class Lazy<T> implements Supplier<T> {

    @Override
    public boolean equals(Object other) {
      return other instanceof Lazy<?> && Objects.equals(get(), ((Lazy<?>) other).get());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(get());
    }

    /** Returns a Lazy that always returns the same instance. */
    static <T> Lazy<T> ofInstance(T instance) {
      return new Lazy<T>() {
        @Override
        public T get() {
          return instance;
        }
      };
    }

    /** Returns a Lazy from a memoized supplier. */
    static <T> Lazy<T> memoize(Supplier<T> supplier) {
      Supplier<T> memoized = Suppliers.memoize(supplier);
      return new Lazy<T>() {
        @Override
        public T get() {
          return memoized.get();
        }
      };
    }
  }
}
