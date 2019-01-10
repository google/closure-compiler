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

import static com.google.javascript.jscomp.RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the "Too many template parameters" warning. Ideally this would be part of
 * JSDocInfoParserTest but that test is set up to handle warnings reported from JSDocInfoParser, (as
 * strings) not ones from JSTypeRegistry (as DiagnosticTypes).
 */
@RunWith(JUnit4.class)
public final class CheckTemplateParamsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // No-op. We're just checking for warnings during JSDoc parsing.
    return (externs, root) -> {};
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.TOO_MANY_TYPE_PARAMS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testArray() {
    testSame("/** @type {!Array} */ var x;");
    testSame("/** @type {!Array<string>} */ var x;");
    test(
        srcs("/** @type {!Array<string, number>} */ var x;"),
        warning(TOO_MANY_TEMPLATE_PARAMS));
  }

  @Test
  public void testObject() {
    testSame("/** @type {!Object} */ var x;");
    testSame("/** @type {!Object<number>} */ var x;");
    testSame("/** @type {!Object<string, number>} */ var x;");
    test(
        srcs("/** @type {!Object<string, number, boolean>} */ var x;"),
        warning(TOO_MANY_TEMPLATE_PARAMS));
  }

  @Test
  public void testClass() {
    testSame("/** @constructor */ function SomeClass() {}; /** @type {!SomeClass} */ var x;");
    test(
        srcs(lines(
            "/** @constructor */ function SomeClass() {};",
            "/** @type {!SomeClass<string>} */ var x;")),
        warning(TOO_MANY_TEMPLATE_PARAMS));

    testSame(lines(
        "/** @constructor @template T */ function SomeClass() {};",
        "/** @type {!SomeClass<string>} */ var x;"));

    test(
        srcs(lines(
            "/** @constructor @template T */ function SomeClass() {};",
            "/** @type {!SomeClass<number, string>} */ var x;")),
        warning(TOO_MANY_TEMPLATE_PARAMS));
  }

}
