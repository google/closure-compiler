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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtIncompatible;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * A source transformer for lowering JS language versions. May also include a runtime that needs to
 * be shipped with the final bundle.
 */
@GwtIncompatible
@Immutable
public class Transpiler extends CompilerBasedTransformer {

  private final String runtimeLibraryName;

  public Transpiler(CompilerBasedTransformer.CompilerSupplier compilerSupplier,
      String runtimeLibraryName) {
    super(compilerSupplier);
    this.runtimeLibraryName = checkNotNull(runtimeLibraryName);
  }

  public static CompilerBasedTransformer.CompilerSupplier compilerSupplier() {
    return new CompilerBasedTransformer.CompilerSupplier();
  }

  @Override
  public Optional<String> getRuntime() {
    return Optional.of(runtimeLibraryName);
  }

  @Override
  public String getTranformationName() {
    return "Transpilation";
  }

  public static final Transpiler ES5_TRANSPILER =
      new Transpiler(new CompilerBasedTransformer.CompilerSupplier(), "es6_runtime");


  /** Recommended transpiler. */
  public static Transpiler toEs5() {
    return TO_ES5;
  }

  private static final Transpiler TO_ES5 =
      new Transpiler(new CompilerBasedTransformer.CompilerSupplier(), "es6_runtime");
}
