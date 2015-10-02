/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

import java.io.IOException;

/**
 * Tests for the "Too many template parameters" warning. Ideally this would be part of
 * JSDocInfoParserTest but that test is set up to handle warnings reported from JSDocInfoParser,
 * (as strings) not ones from JSTypeRegistry (as DiagnosticTypes).
 */
public final class CheckTemplateParamsTest extends CompilerTestCase {
  @Override
  public void setUp() throws IOException {
    enableTypeCheck();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // No-op. We're just checking for warnings during JSDoc parsing.
    return new CompilerPass() {
      public void process(Node externs, Node root) {}
    };
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);

    return options;
  }

  public void testArray() {
    testSame("/** @type {!Array} */ var x;");
    testSame("/** @type {!Array<string>} */ var x;");
    testWarning("/** @type {!Array<string, number>} */ var x;",
        RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);
  }

  public void testObject() {
    testSame("/** @type {!Object} */ var x;");
    testSame("/** @type {!Object<number>} */ var x;");
    testSame("/** @type {!Object<string, number>} */ var x;");
    testWarning("/** @type {!Object<string, number, boolean>} */ var x;",
        RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);
  }

  public void testClass() {
    testSame("/** @constructor */ function SomeClass() {}; /** @type {!SomeClass} */ var x;");
    testWarning(
        "/** @constructor */ function SomeClass() {}; /** @type {!SomeClass<string>} */ var x;",
        RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);

    testSame(
        "/** @constructor @template T */ function SomeClass() {};"
            + "/** @type {!SomeClass<string>} */ var x;");

    testWarning(
        "/** @constructor @template T */ function SomeClass() {};"
            + "/** @type {!SomeClass<number, string>} */ var x;",
        RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS);

  }

}
