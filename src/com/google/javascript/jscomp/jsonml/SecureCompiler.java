/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.VariableRenamingPolicy;

import java.util.ArrayList;

/**
 * Compilation of JavaScript code which guarantees that all security
 * capabilities are preserved after the process. In particular, it can be
 * safely applied to cajoled source.
 *
 * JS Compiler is used for code analysis and optimization. It runs a series
 * of passes which try to improve the code.
 *
 * For safety reasons, only a subset of local passes, which are provided by
 * JS Compiler, are processed. Currently it includes:
 * - elimination of temporary variables
 *
 * Using SecureCompiler is quite straightforward. A user just needs to create
 * a new instance and call compile() method. Currently the only input which
 * is supported is JsonML.
 *
 * @author dhans@google.com (Daniel Hans)
 */
public class SecureCompiler {

  private static final String COMPILATION_UNCOMPLETED_MSG =
      "No compilation has been completed yet.";

  private static final String COMPILATION_UNSUCCESSFUL_MSG =
      "The last compilation was not successful.";

  private static final String COMPILATION_ALREADY_COMPLETED_MSG =
      "This instance has already compiled one source.";

  private Compiler compiler;
  private CompilerOptions options;
  private JsonMLAst sourceAst;

  /** Report from the last compilation */
  private Report report;

  public SecureCompiler() {
    compiler = new Compiler();
    options = getSecureCompilerOptions();
  }

  /**
   * Returns compiled source in JsonML format.
   */
  public JsonML getJsonML() {
    Preconditions.checkState(report != null, COMPILATION_UNCOMPLETED_MSG);
    Preconditions.checkState(report.success, COMPILATION_UNSUCCESSFUL_MSG);
    return sourceAst.convertToJsonML();
  }

  /**
   * Returns compiled source as a JavaScript.
   */
  public String getString() {
    Preconditions.checkState(report != null, COMPILATION_UNCOMPLETED_MSG);
    Preconditions.checkState(report.success, COMPILATION_UNSUCCESSFUL_MSG);
    return compiler.toSource();
  }

  /**
   * Returns report from the last compilation.
   */
  public Report getReport() {
    Preconditions.checkState(report != null, COMPILATION_UNCOMPLETED_MSG);
    return report;
  }

  public void compile(JsonML source) {
    if (report != null) {
      throw new IllegalStateException(COMPILATION_ALREADY_COMPLETED_MSG);
    }

    sourceAst = new JsonMLAst(source);

    CompilerInput input = new CompilerInput(
        sourceAst, "[[jsonmlsource]]", false);

    JSModule module = new JSModule("[[jsonmlmodule]]");
    module.add(input);

    Result result = compiler.compile(
        new JSSourceFile[] {},
        new JSModule[] { module },
        options);

    report = generateReport(result);
  }

  /**
   * Returns compiler options which are safe for compilation of a cajoled
   * module. The set of options is similar to the one which is used by
   * CompilationLevel in simple mode. The main difference is that variable
   * renaming and closurePass options are turned off.
   */
  private CompilerOptions getSecureCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    options.variableRenaming = VariableRenamingPolicy.OFF;
    options.inlineLocalVariables = true;
    options.inlineLocalFunctions = true;
    options.checkGlobalThisLevel = CheckLevel.OFF;
    options.coalesceVariableNames = true;
    options.deadAssignmentElimination = true;
    options.collapseVariableDeclarations = true;
    options.convertToDottedProperties = true;
    options.labelRenaming = true;
    options.removeDeadCode = true;
    options.optimizeArgumentsArray = true;
    options.removeUnusedVars = false;
    options.removeUnusedLocalVars = true;

    return options;
  }

  public void enableFoldConstant() {
    options.foldConstants = true;
  }

  Report generateReport(Result result) {
    // a report may be generated only after actual compilation is complete
    if (result == null) {
      return null;
    }

    ArrayList<JsonMLError> errors = Lists.newArrayList();
    for (JSError error : result.errors) {
      errors.add(JsonMLError.make(error, sourceAst));
    }

    ArrayList<JsonMLError> warnings = Lists.newArrayList();
    for (JSError warning : result.warnings) {
      warnings.add(JsonMLError.make(warning, sourceAst));
    }

    return new Report(
        errors.toArray(new JsonMLError[0]),
        warnings.toArray(new JsonMLError[0]));
  }

  public class Report {
    private final boolean success;
    private final JsonMLError[] errors;
    private final JsonMLError[] warnings;

    private Report(JsonMLError[] errors, JsonMLError[] warnings) {
      this.success = errors.length == 0;
      this.errors = errors;
      this.warnings = warnings;
    }

    public boolean isSuccessful() {
      return success;
    }

    public JsonMLError[] getErrors() {
      return errors;
    }

    public JsonMLError[] getWarnings() {
      return warnings;
    }
  }

}
