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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/** Compilation results */
public class Result {
  public final boolean success;
  public final ImmutableList<JSError> errors;
  public final ImmutableList<JSError> warnings;

  public final VariableMap variableMap;
  public final VariableMap propertyMap;
  public final VariableMap namedAnonFunctionMap;
  public final VariableMap stringMap;
  public final VariableMap instrumentationMappings;
  public final SourceMap sourceMap;
  public final Set<String> cssNames;
  public final String externExport;
  public final String idGeneratorMap;
  public final boolean transpiledFiles;

  Result(
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings,
      VariableMap variableMap,
      VariableMap propertyMap,
      VariableMap namedAnonFunctionMap,
      @Nullable VariableMap stringMap,
      @Nullable VariableMap instrumentationMappings,
      @Nullable SourceMap sourceMap,
      String externExport,
      @Nullable Set<String> cssNames,
      @Nullable String idGeneratorMap,
      boolean transpiledFiles) {
    this.success = errors.isEmpty();
    this.errors = errors;
    this.warnings = warnings;
    this.variableMap = variableMap;
    this.propertyMap = propertyMap;
    this.namedAnonFunctionMap = namedAnonFunctionMap;
    this.stringMap = stringMap;
    this.instrumentationMappings = instrumentationMappings;
    this.sourceMap = sourceMap;
    this.externExport = externExport;
    this.cssNames = cssNames;
    this.idGeneratorMap = idGeneratorMap;
    this.transpiledFiles = transpiledFiles;
  }

  @VisibleForTesting
  @Deprecated
  public Result(
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings,
      VariableMap variableMap,
      VariableMap propertyMap,
      VariableMap namedAnonFunctionMap,
      SourceMap sourceMap,
      String externExport) {
    this(
        errors,
        warnings,
        variableMap,
        propertyMap,
        namedAnonFunctionMap,
        /* stringMap= */ null,
        /* instrumentationMappings= */ null,
        sourceMap,
        externExport,
        /* cssNames= */ null,
        /* idGeneratorMap= */ null,
        /* transpiledFiles= */ false);
  }

  /**
   * Returns an almost empty result that is more appropriate for a partial compilation.
   *
   * <p>For a partial compilation we only care about errors and warnings. It is unnecessary to
   * examine all of the other results.
   *
   * @param result the full `Result` object provided by the compiler
   */
  public static Result pruneResultForPartialCompilation(Result result) {
    VariableMap emptyVariableMap = new VariableMap(ImmutableMap.of());
    return new Result(
        /* errors= */ result.errors,
        /* warnings= */ result.warnings,
        /* variableMap= */ emptyVariableMap,
        /* propertyMap= */ emptyVariableMap,
        /* namedAnonFunctionMap= */ emptyVariableMap,
        /* stringMap= */ null,
        /* instrumentationMappings= */ emptyVariableMap,
        /* sourceMap= */ null,
        /* externExport= */ "",
        /* cssNames= */ null,
        /* idGeneratorMap= */ null,
        /* transpiledFiles= */ false);
  }
}
