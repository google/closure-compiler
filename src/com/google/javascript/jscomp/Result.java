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
import java.util.Map;
import java.util.Set;

/**
 * Compilation results
 */
public class Result {
  public final boolean success;
  public final JSError[] errors;
  public final JSError[] warnings;

  @Deprecated
  public final String debugLog;
  public final VariableMap variableMap;
  public final VariableMap propertyMap;
  public final VariableMap namedAnonFunctionMap;
  public final VariableMap stringMap;
  public final FunctionInformationMap functionInformationMap;
  public final SourceMap sourceMap;
  public final Map<String, Integer> cssNames;
  public final String externExport;
  public final String idGeneratorMap;
  public final Set<SourceFile> transpiledFiles;

  Result(JSError[] errors, JSError[] warnings,
         VariableMap variableMap, VariableMap propertyMap,
         VariableMap namedAnonFunctionMap,
         VariableMap stringMap,
         FunctionInformationMap functionInformationMap,
         SourceMap sourceMap, String externExport,
         Map<String, Integer> cssNames, String idGeneratorMap,
         Set<SourceFile> transpiledFiles) {
    this.success = errors.length == 0;
    this.errors  = errors;
    this.warnings = warnings;
    this.debugLog = null;
    this.variableMap = variableMap;
    this.propertyMap = propertyMap;
    this.namedAnonFunctionMap = namedAnonFunctionMap;
    this.stringMap = stringMap;
    this.functionInformationMap = functionInformationMap;
    this.sourceMap = sourceMap;
    this.externExport = externExport;
    this.cssNames = cssNames;
    this.idGeneratorMap = idGeneratorMap;
    this.transpiledFiles = transpiledFiles;
  }

  /**
   * @deprecated Use the version below (identical except for the 'debugLog' parameter).
   */
  @Deprecated
  @VisibleForTesting
  public Result(JSError[] errors, JSError[] warnings, String debugLog,
                VariableMap variableMap, VariableMap propertyMap,
                VariableMap namedAnonFunctionMap,
                FunctionInformationMap functionInformationMap,
                SourceMap sourceMap, String externExport) {
    this(errors, warnings, variableMap, propertyMap,
         namedAnonFunctionMap, null, functionInformationMap, sourceMap,
         externExport, null, null, null);
  }

  @VisibleForTesting
  public Result(JSError[] errors, JSError[] warnings,
                VariableMap variableMap, VariableMap propertyMap,
                VariableMap namedAnonFunctionMap,
                FunctionInformationMap functionInformationMap,
                SourceMap sourceMap, String externExport) {
    this(errors, warnings, variableMap, propertyMap,
         namedAnonFunctionMap, null, functionInformationMap, sourceMap,
         externExport, null, null, null);
  }
}
