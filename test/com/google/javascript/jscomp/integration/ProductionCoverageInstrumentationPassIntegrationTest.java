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
import static org.junit.Assert.assertThrows;

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
public final class ProductionCoverageInstrumentationPassIntegrationTest
    extends IntegrationTestCase {

  private static final String EXPECTED_LEADING_INSTRUMENT_CODE = "var ist_arr = [];\n";

  @Test
  public void testFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        function foo() {
          console.log('Hello');
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            function foo() {
              ist_arr.push("C");
              console.log('Hello');
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testNoFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        var global = 23;
        console.log(global);
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            var global = 23;
            console.log(global);
            """;

    test(options, source, expected);
  }

  @Test
  public void testNoTranspilationFunctionInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    String source =
        """
        function foo() {
          console.log('Hello');
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            function foo() {
              ist_arr.push("C");
              console.log('Hello');
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testIfInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        if (tempBool) {
          console.log('Hello');
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            if (tempBool) {
              ist_arr.push("C");
              console.log('Hello');
            } else {
              ist_arr.push("E");
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testIfElseInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        if (tempBool) {
          console.log('Hello');
        } else if(anotherTempBool) {
          console.log('hi');
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            if (tempBool) {
              ist_arr.push("G");
              console.log('Hello');
            } else if(anotherTempBool) {
              ist_arr.push("C");
              console.log('hi');
            } else {
              ist_arr.push("E");
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testOrInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = "tempObj.a || tempObj.b;";

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            tempObj.a || (ist_arr.push("C"), tempObj.b)
            """;

    test(options, source, expected);
  }

  @Test
  public void testAndInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = "tempObj.a && tempObj.b;";

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            tempObj.a && (ist_arr.push("C"), tempObj.b)
            """;

    test(options, source, expected);
  }

  @Test
  public void testCoalesceInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    String source = "someObj ?? 24";

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            someObj ?? (ist_arr.push("C"), 24)
            """;

    test(options, source, expected);
  }

  @Test
  public void testTernaryInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = "flag ? foo() : fooBar()";

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            flag ? (ist_arr.push("C"), foo()) : (ist_arr.push("E"), fooBar())
            """;

    test(options, source, expected);
  }

  @Test
  public void testForLoopInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        for(var i = 0 ; i < 10 ; ++i) {
          console.log('*');
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            for(var i = 0 ; i < 10 ; ++i) {
              ist_arr.push("C");
              console.log('*');
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testSwitchInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        switch (x) {
          case 1:
            x = 5;
        };
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            switch (x) {
              case 1:
                ist_arr.push("C");
                x = 5;
              default:
                ist_arr.push("E");
            };
            """;

    test(options, source, expected);
  }

  @Test
  public void testLambdaInstrumentation() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        window.setInterval(
          (x) => {
            console.log(x);
          },
          500);
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            window.setInterval(
              function(x) {
                ist_arr.push('C');
                console.log(x);
              },
              500);
            """;

    test(options, source, expected);
  }

  @Test
  public void testNoNameFunctionsAreNamedProperly() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source = "tempObj.a || tempObj.b;";

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
        """
        for(var i = 0 ; i < 10 ; ++i) {
           console.log('*');
        }
        """;

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

    String source = "if (tempBool) if(someBool) console.log('*');";

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            if (tempBool) {
              ist_arr.push("G");
              if (someBool) {
                ist_arr.push("C");
                console.log('*');
              } else {
                ist_arr.push("E");
              }
            } else {
              ist_arr.push("I");
            }
            """;

    test(options, source, expected);
  }

  @Test
  public void testInstrumentationMappingIsCreated() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        function foo() {
         console.log('Hello');
        }
        """;

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
        """
        function foo (x) {
          console.log(x+5);
        };
        """;
    String useGlobalArray =
        """
        function ist(){
          console.log(ist_array)
        }
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            function foo (x) {
              ist_arr.push("C");
              console.log( x + 5);
            };
            """;

    String expectedUseGlobalArray =
        """
        function ist(){
          ist_arr.push("E");
          console.log(ist_array);
        }
        """;

    String[] sourceArr = new String[] {source, useGlobalArray};
    String[] expectedArr = new String[] {expected, expectedUseGlobalArray};

    test(options, sourceArr, expectedArr);
  }

  @Test
  public void testInstrumentationWithAdvancedOpt() {
    CompilerOptions options = createCompilerOptions();
    declareIstArrExtern();

    String source =
        """
        function foo(x) {
           if(x) {
              alert(x+5);
           }
        }
        foo(true);
        foo(false);
        """;

    String expected =
        EXPECTED_LEADING_INSTRUMENT_CODE
            + """
            function a(b) {
              ist_arr.push("G");
              b ? (ist_arr.push("C"), alert(b+5)) : ist_arr.push("E");
            }
            a(!0);
            a(!1);
            """;

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    test(options, source, expected);
  }

  private void declareIstArrExtern() {
    String externDefinition =
        """
        /**
         * @externs
         */
        /**
         * @type {!Array<string>}
         */
        let ist_arr;
        """;
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
    options.setPreserveTypeAnnotations(true);
    return options;
  }
}
