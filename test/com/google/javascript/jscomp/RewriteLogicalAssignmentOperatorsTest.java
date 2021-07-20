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
 * Test cases for transpilation pass that replaces the logical assignment operators (`||=`, `&&=`,
 * `??=`).
 */
@RunWith(JUnit4.class)
public final class RewriteLogicalAssignmentOperatorsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeInfoValidation();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteLogicalAssignmentOperators(compiler);
  }

  @Test
  public void testSimple() {
    test(srcs("a ||= b"), expected("a || (a = b);"));
    test(srcs("a &&= b"), expected("a && (a = b);"));
    test(srcs("a ??= b"), expected("a ?? (a = b);"));
  }

  @Test
  public void testInExpressions() {
    test(srcs("a ?? (b ??= 1) ?? (c ??= 2)"), expected("a ?? (b ?? (b = 1)) ?? (c ?? (c = 2));"));
    test(
        srcs(
            lines(
                "a = 4;", //
                "do {",
                "  a = a - 1;",
                "} while (a ||= null);")),
        expected(
            lines(
                "a = 4;", //
                "do {",
                "  a = a - 1;",
                "} while (a || (a = null));")));
  }
}
