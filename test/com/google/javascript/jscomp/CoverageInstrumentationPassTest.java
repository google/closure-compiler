/*
 * Copyright 2016 The Closure Compiler Authors.
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

/**
 * Tests for {@link CoverageInstrumentationPass}.
 */
public final class CoverageInstrumentationPassTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CoverageInstrumentationPass(compiler, CoverageInstrumentationPass.CoverageReach.ALL);
  }

  @Override
  public void setUp() {
    allowExternsChanges(true);
  }

  public void testFunction() {
    test(
        "function f() { console.log('hi'); }",
        LINE_JOINER.join(
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "{",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "{",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "function f() {",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  console.log('hi');",
            "}"));
  }


  // If the body of the arrow function is a block, it is instrumented.
  public void testArrowFunction_block() {
    testEs6(
        "var f = () => { console.log('hi'); };",
        LINE_JOINER.join(
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "{",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "{",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "var f = () => {",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  console.log('hi');",
            "}"));
  }

  // If the body of the arrow function is an expression, it is converted to a block,
  // then instrumented.
  public void testArrowFunction_expression() {
    testEs6(
        "var f = (x => x+1);",
        LINE_JOINER.join(
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "{",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_fileNames = JSCompiler_lcov_fileNames || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_instrumentedLines = JSCompiler_lcov_instrumentedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "/** @suppress {duplicate} */",
            "var JSCompiler_lcov_executedLines = JSCompiler_lcov_executedLines || [];",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "{",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  var JSCompiler_lcov_data_testcode = [];",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_testcode);",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_instrumentedLines.push('01');",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_fileNames.push('testcode');",
            "}",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "JSCompiler_lcov_data_testcode[0] = true;",
            "var f = (x) => {",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  JSCompiler_lcov_data_testcode[0] = true;",
            "  return x + 1;",
            "}"));
  }
}
