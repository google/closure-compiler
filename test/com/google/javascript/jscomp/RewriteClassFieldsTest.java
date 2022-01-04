/*
 * Copyright 2021 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for transpilation pass that replaces public class fields: `class C { x = 2; ['y'] = 3;
 * static a; static ['b'] = 'hi'; }`
 */
@RunWith(JUnit4.class)
public final class RewriteClassFieldsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteClassFields(compiler);
  }

  @Test
  public void testCannotConvertYet() {
    testError(
        lines(
            "class C {", //
            "  x = 2;",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  ['x'] = 2;",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError(
        lines(
            "class C {", //
            "  static x = 2;",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  static ['x'] = 2;",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
  }
}
