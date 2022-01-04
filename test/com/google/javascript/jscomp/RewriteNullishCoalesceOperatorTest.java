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
package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for transpilation pass that replaces the nullish coalesce operator (`??`). */
@RunWith(JUnit4.class)
public final class RewriteNullishCoalesceOperatorTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteNullishCoalesceOperator(compiler);
  }

  @Test
  public void simple() {
    test(
        srcs("a ?? b"),
        expected(
            "let $jscomp$nullish$tmp0; ($jscomp$nullish$tmp0 = a) != null ? $jscomp$nullish$tmp0 :"
                + " b"));
  }

  @Test
  public void withOtherOperators() {
    test(
        srcs("(x + y) ?? (a && b)"),
        expected(
            "let $jscomp$nullish$tmp0; ($jscomp$nullish$tmp0 = x + y) != null ?"
                + " $jscomp$nullish$tmp0 : (a && b)"));
  }

  @Test
  public void insideArrowFunctionBody() {
    test(
        srcs("() => (x + y) ?? (a && b)"),
        expected(
            "() => { let $jscomp$nullish$tmp0; return ($jscomp$nullish$tmp0 = x + y) != null ?"
                + " $jscomp$nullish$tmp0 : (a && b) }"));
  }

  @Test
  public void chain() {
    test(
        srcs("a ?? b ?? c"),
        expected(
            lines(
                "let $jscomp$nullish$tmp0; let $jscomp$nullish$tmp1;",
                "($jscomp$nullish$tmp1 = ($jscomp$nullish$tmp0 = a) != null ? $jscomp$nullish$tmp0"
                    + " : b) != null",
                "? $jscomp$nullish$tmp1 : c;")));
  }
}
