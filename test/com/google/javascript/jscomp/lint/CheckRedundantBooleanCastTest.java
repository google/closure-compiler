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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckRedundantBooleanCast.REDUNDANT_BOOLEAN_CAST;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

/**
 * Test case for {@link CheckRedundantBooleanCast}.
 */
public final class CheckRedundantBooleanCastTest extends Es6CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckRedundantBooleanCast(compiler);
  }

  public void testCheckRedundantBooleanCast_noWarning() {
    testSame("var x = !!y;");
    testSame("var x = Boolean(y)");
    testSame("function f() { return !!x; }");
    testSame("var x = y ? !!z : !!w;");
  }

  public void testCheckRedundantBooleanCast_warning() {
    testWarning("var x = !!!y;", REDUNDANT_BOOLEAN_CAST);
    testWarning("var x = !!y ? 1 : 2;", REDUNDANT_BOOLEAN_CAST);
    testWarning("var x = Boolean(y) ? 1 : 2;", REDUNDANT_BOOLEAN_CAST);
    testWarning("var x = Boolean(!!y);", REDUNDANT_BOOLEAN_CAST);
    testWarning("var x = new Boolean(!!y);", REDUNDANT_BOOLEAN_CAST);
    testWarning("if (!!x) {}", REDUNDANT_BOOLEAN_CAST);
    testWarning("if (Boolean(x)) {}", REDUNDANT_BOOLEAN_CAST);
    testWarning("while (!!x) {}", REDUNDANT_BOOLEAN_CAST);
    testWarning("while (Boolean(x)) {}", REDUNDANT_BOOLEAN_CAST);
    testWarning("do {} while (!!x)", REDUNDANT_BOOLEAN_CAST);
    testWarning("do {} while (Boolean(x))", REDUNDANT_BOOLEAN_CAST);
    testWarning("for (; !!x; ) {}", REDUNDANT_BOOLEAN_CAST);
    testWarning("for (; Boolean(x); ) {}", REDUNDANT_BOOLEAN_CAST);
  }
}
