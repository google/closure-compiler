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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.BlackHoleErrorManager;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableMap;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@GwtIncompatible("FileInstrumentationData")
public final class ProductionCoverageInstrumentationPassIntegrationTest
    extends IntegrationTestCase {

  private final String instrumentCodeSource =
      lines(
          "goog.module('instrument.code');",
          "class InstrumentCode {",
          "   instrumentCode(a,b) {}",
          "}",
          "const instrumentCodeInstance = new InstrumentCode();",
          "exports = {instrumentCodeInstance};");

  private final String instrumentCodeExpected =
      lines(
          "var module$exports$instrument$code = {}",
          "var module$contents$instrument$code_InstrumentCode = function() {};",
          "module$contents$instrument$code_InstrumentCode.prototype.instrumentCode = function(a,"
              + " b) {};",
          "module$exports$instrument$code.instrumentCodeInstance = ",
          "new module$contents$instrument$code_InstrumentCode;");

  @Test
  public void testFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();

    String source = lines("function foo() { ", "   console.log('Hello'); ", "}");

    String[] sourceArr = {instrumentCodeSource, source};

    String expected =
        lines(
            "function foo() { ",
            "   module$exports$instrument$code.instrumentCodeInstance.instrumentCode(\"C\", 1);",
            "   console.log('Hello'); ",
            "}");

    String[] expectedArr = {instrumentCodeExpected, expected};
    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testNoFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();

    String source = lines("var global = 23;", "console.log(global);");

    String[] sourceArr = {instrumentCodeSource, source};

    String expected = lines("var global = 23;", "console.log(global);");

    String[] expectedArr = {instrumentCodeExpected, expected};
    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testNoTranspilationFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();

    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    String source = lines("function foo() { ", "   console.log('Hello'); ", "}");

    String[] sourceArr = {instrumentCodeSource, source};

    String noTranspilationInstrumentCodeExpected =
        lines(
            "var module$exports$instrument$code = {}",
            "class module$contents$instrument$code_InstrumentCode {",
            "   instrumentCode(a, b) {};",
            "}",
            "module$exports$instrument$code.instrumentCodeInstance = ",
            "new module$contents$instrument$code_InstrumentCode;");

    String expected =
        lines(
            "function foo() { ",
            "   module$exports$instrument$code.instrumentCodeInstance.instrumentCode(\"C\", 1);",
            "   console.log('Hello'); ",
            "}");

    String[] expectedArr = {noTranspilationInstrumentCodeExpected, expected};
    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testInstrumentationMappingIsCreated() {
    CompilerOptions options = createCompilerOptions();

    String source = lines("function foo() { ", "   console.log('Hello');", "}");

    String[] sourceArr = {instrumentCodeSource, source};

    Compiler compiledSourceCode = compile(options, sourceArr);
    VariableMap variableParamMap = compiledSourceCode.getInstrumentationMapping();

    ImmutableMap<String, String> paramMap = variableParamMap.getOriginalNameToNewNameMap();

    assertThat(paramMap).hasSize(4);

    assertWithMessage("FunctionNames in the parameter mapping are not properly set")
        .that(paramMap.get(" FunctionNames"))
        .isEqualTo("[foo]");
    assertWithMessage("FileNames in the parameter mapping are not properly set")
        .that(paramMap.get(" FileNames"))
        .isEqualTo("[i1.js]");
    assertWithMessage("Types in the parameter mapping are not properly set")
        .that(paramMap.get(" Types"))
        .isEqualTo("[Type.FUNCTION]");
    assertWithMessage("Array index encoding is not performed properly")
        .that(paramMap.get("C"))
        .isEqualTo("AAA");
  }

  @Override
  protected Compiler compile(CompilerOptions options, String[] original) {
    Compiler compiler =
        useNoninjectingCompiler
            ? new NoninjectingCompiler(new BlackHoleErrorManager())
            : new Compiler(new BlackHoleErrorManager());

    lastCompiler = compiler;

    List<SourceFile> inputs = new ArrayList<>();

    inputs.add(SourceFile.fromCode("InstrumentCode.js", original[0]));

    for (int i = 1; i < original.length; i++) {
      inputs.add(SourceFile.fromCode(inputFileNamePrefix + i + inputFileNameSuffix, original[i]));
    }

    compiler.compile(externs, inputs, options);

    return compiler;
  }

  @Override
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    options.setClosurePass(true);

    options.setInstrumentForCoverageOption(InstrumentOption.PRODUCTION);
    options.setPrettyPrint(true);
    options.preserveTypeAnnotations = true;
    return options;
  }
}
