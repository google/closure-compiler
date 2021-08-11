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
import static com.google.javascript.jscomp.base.JSCompStrings.lines;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.VariableMap;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@GwtIncompatible("FileInstrumentationData")
public final class ProductionCoverageInstrumentationPassIntegrationTest
    extends IntegrationTestCase {

  private final String instrumentCodeExpected = lines("var ist_arr = [];\n");

  private static String getInstrumentCodeLine(String encodedParam) {
    return Strings.lenientFormat("%s.push(\"%s\")", "ist_arr", encodedParam);
  }

  @Test
  public void testFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "function foo() { ", //
            "   console.log('Hello'); ",
            "}");

    String expected =
        lines(
            "function foo() { ", //
            getInstrumentCodeLine("C"),
            ";",
            "  console.log('Hello'); ",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testNoFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("var global = 23;", "console.log(global);");

    String expected = lines("var global = 23;", "console.log(global);");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testNoTranspilationFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    String source =
        lines(
            "function foo() { ", //
            "   console.log('Hello'); ",
            "}");

    String expected =
        lines(
            "function foo() { ", //
            getInstrumentCodeLine("C"),
            ";",
            "   console.log('Hello'); ",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testIfInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "if (tempBool) {", //
            "   console.log('Hello');",
            "}");

    String expected =
        lines(
            "if (tempBool) { ",
            getInstrumentCodeLine("C"),
            ";",
            "  console.log('Hello'); ",
            "} else {",
            getInstrumentCodeLine("E"),
            ";",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testIfElseInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "if (tempBool) {",
            "   console.log('Hello');",
            "} else if(anotherTempBool) {",
            "   console.log('hi');",
            "}");

    String expected =
        lines(
            "if (tempBool) {",
            getInstrumentCodeLine("G"),
            ";",
            "   console.log('Hello');",
            "} else if(anotherTempBool) {",
            getInstrumentCodeLine("C"),
            ";",
            "   console.log('hi');",
            "} else {",
            getInstrumentCodeLine("E"),
            ";",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testOrInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("tempObj.a || tempObj.b;");

    String expected = lines("tempObj.a || (", getInstrumentCodeLine("C"), ", tempObj.b)");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testAndInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("tempObj.a && tempObj.b;");

    String expected = lines("tempObj.a && (", getInstrumentCodeLine("C"), ", tempObj.b)");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testCoalesceInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    String source = lines("someObj ?? 24");

    String expected = lines("someObj ?? (", getInstrumentCodeLine("C"), ", 24)");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testTernaryInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("flag ? foo() : fooBar()");

    String expected =
        lines(
            "flag ? (",
            getInstrumentCodeLine("C"),
            ", foo()) :",
            "(",
            getInstrumentCodeLine("E"),
            ", fooBar())");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testForLoopInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "for(var i = 0 ; i < 10 ; ++i) {", //
            "   console.log('*');",
            "}");

    String expected =
        lines(
            "for(var i = 0 ; i < 10 ; ++i) {",
            getInstrumentCodeLine("C"),
            ";",
            "   console.log('*');",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testSwitchInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "switch (x) {", //
            "   case 1: ",
            "      x = 5;",
            "};");

    String expected =
        lines(
            "switch (x) {",
            "   case 1: ",
            getInstrumentCodeLine("C"),
            ";",
            "      x = 5;",
            "   default: ",
            getInstrumentCodeLine("E"),
            ";",
            "};");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testLambdaInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("window.setInterval((x) => { console.log(x); }, 500);");

    String expected =
        lines(
            "window.setInterval(function(x) { "
                + "ist_arr.push('C');"
                + "console.log(x);"
                + "}, 500);");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testNoNameFunctionsAreNamedProperly() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("tempObj.a || tempObj.b;");

    Compiler compiledSourceCode = compile(options, source);

    VariableMap variableParamMap = compiledSourceCode.getInstrumentationMapping();

    ImmutableMap<String, String> paramMap = variableParamMap.getOriginalNameToNewNameMap();

    assertWithMessage("Function name is not converted properly when it has no name")
        .that(paramMap.get(" FunctionNames"))
        .isEqualTo("[\"<Anonymous>\"]");
  }

  @Test
  public void testExternIsNotDeclaredException() {
    CompilerOptions options = createCompilerOptions();

    String source =
        lines(
            "for(var i = 0 ; i < 10 ; ++i) {", //
            "   console.log('*');",
            "}");

    AssertionError e = assertThrows(AssertionError.class, () -> compile(options, source));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            "The array name passed to --production_instrumentation_array_name was not "
                + "declared as an extern. This will result in undefined behaviour");
  }

  @Test
  public void testNestedIfInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = lines("if (tempBool) if(someBool) console.log('*');");

    String expected =
        lines(
            "if (tempBool) {",
            getInstrumentCodeLine("G"),
            ";",
            "  if (someBool) {",
            getInstrumentCodeLine("C"),
            ";",
            "    console.log('*');",
            "   } else {",
            getInstrumentCodeLine("E"),
            ";",
            "   }",
            "} else {",
            getInstrumentCodeLine("I"),
            ";",
            "}");

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  @Test
  public void testInstrumentationMappingIsCreated() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "function foo() { ", //
            "   console.log('Hello');",
            "}");

    Compiler compiledSourceCode = compile(options, source);
    VariableMap variableParamMap = compiledSourceCode.getInstrumentationMapping();

    ImmutableMap<String, String> paramMap = variableParamMap.getOriginalNameToNewNameMap();

    assertThat(paramMap).hasSize(4);

    assertWithMessage("FunctionNames in the parameter mapping are not properly set")
        .that(paramMap.get(" FunctionNames"))
        .isEqualTo("[\"foo\"]");
    assertWithMessage("FileNames in the parameter mapping are not properly set")
        .that(paramMap.get(" FileNames"))
        .isEqualTo("[\"i0.js\"]");
    assertWithMessage("Types in the parameter mapping are not properly set")
        .that(paramMap.get(" Types"))
        .isEqualTo("[\"FUNCTION\"]");
    assertWithMessage("Array index encoding is not performed properly")
        .that(paramMap.get("C"))
        .isEqualTo("AAACA");
  }

  @Test
  public void testGlobalArrayIsDeclared() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "function foo (x) {", //
            "   console.log(x+5);",
            "};");
    String useGlobalArray =
        lines(
            "function ist(){", //
            "   console.log(ist_array)",
            "}");

    String expected =
        lines(
            "function foo (x) {", //
            getInstrumentCodeLine("C"),
            ";",
            "   console.log( x + 5);",
            "};");

    String expectedUseGlobalArray =
        lines("function ist(){", getInstrumentCodeLine("E"), ";", "console.log(ist_array);", "}");

    String[] sourceArr = new String[] {source, useGlobalArray};
    String[] expectedArr =
        new String[] {instrumentCodeExpected.concat(expected), expectedUseGlobalArray};

    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testInstrumentationWithAdvancedOpt() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        lines(
            "function foo (x) {", //
            "   if(x) { ",
            "      alert(x+5); ",
            "   }",
            "} ",
            "foo(true); foo(false);");

    String expected =
        lines(
            "function a(b) {",
            getInstrumentCodeLine("G"),
            ";",
            "b ? (",
            getInstrumentCodeLine("C"),
            ", alert(b+5)) : ",
            getInstrumentCodeLine("E"),
            ";",
            "}",
            "a(!0);",
            "a(!1);");

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(options, source, instrumentCodeExpected.concat(expected));
  }

  private void declareIstArrExtern() {
    String externDefinition =
        lines(
            "/**", //
            "* @externs",
            "*/",
            "/**",
            "*@type {!Array<string>}",
            "*/",
            "let ist_arr;");
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addArray()
                .addAlert()
                .addExtra(externDefinition)
                .buildExternsFile("externs"));
  }

  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    options.setProductionInstrumentationArrayName("ist_arr");

    options.setClosurePass(true);

    options.setInstrumentForCoverageOption(InstrumentOption.PRODUCTION);
    options.setPrettyPrint(true);
    options.preserveTypeAnnotations = true;
    return options;
  }
}
