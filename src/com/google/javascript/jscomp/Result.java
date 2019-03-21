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
import java.util.Map;
import java.util.Set;

/**
 * Compilation results
 */
public class Result {
  public final boolean success;
  public final ImmutableList<JSError> errors;
  public final ImmutableList<JSError> warnings;

  public final VariableMap variableMap;
  public final VariableMap propertyMap;
  public final VariableMap namedAnonFunctionMap;
  public final VariableMap stringMap;
  public final SourceMap sourceMap;
  public final Map<String, Integer> cssNames;
  public final String externExport;
  public final String idGeneratorMap;
  public final Set<SourceFile> transpiledFiles;

  Result(
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings,
      VariableMap variableMap,
      VariableMap propertyMap,
      VariableMap namedAnonFunctionMap,
      VariableMap stringMap,
      SourceMap sourceMap,
      String externExport,
      Map<String, Integer> cssNames,
      String idGeneratorMap,
      Set<SourceFile> transpiledFiles) {
    this.success = errors.isEmpty();
    this.errors  = errors;
    this.warnings = warnings;
    this.variableMap = variableMap;
    this.propertyMap = propertyMap;
    this.namedAnonFunctionMap = namedAnonFunctionMap;
    this.stringMap = stringMap;
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
        null,
        sourceMap,
        externExport,
        null,
        null,
        null);
  }

  /**
   * Returns an almost empty result that is used for multistage compilation.
   *
   * <p>For multistage compilations, Result for stage1 only cares about errors and warnings. It is
   * unnecessary to write all of other results in the disk.
   */
  public static Result createResultForStage1(Result result) {
    VariableMap emptyVariableMap = new VariableMap(ImmutableMap.of());
    return new Result(
        result.errors,
        result.warnings,
        emptyVariableMap,
        emptyVariableMap,
        emptyVariableMap,
        null,
        null,
        "",
        null,
        null,
        null);
  }
}
