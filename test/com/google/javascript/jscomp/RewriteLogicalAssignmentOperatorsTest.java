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
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteLogicalAssignmentOperatorsPass(compiler);
  }

  @Test
  public void testSimple() {
    test(srcs("a ||= b"), expected("a || (a = b);"));
    test(srcs("a &&= b"), expected("a && (a = b);"));
    test(srcs("a ??= b"), expected("a ?? (a = b);"));
  }

  @Test
  public void testPropertyReference() {
    test(
        srcs("a.x ||= b"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "($jscomp$logical$assign$tmpm1146332801$0 = a).x ",
                "   ||",
                "($jscomp$logical$assign$tmpm1146332801$0.x = b);")));
    test(
        srcs("a.foo &&= null"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "($jscomp$logical$assign$tmpm1146332801$0 = a).foo ",
                "   &&",
                "($jscomp$logical$assign$tmpm1146332801$0.foo = null);")));
    test(
        srcs(
            lines(
                "foo().x = null;", //
                "foo().x ??= y")),
        expected(
            lines(
                "foo().x = null;", //
                "let $jscomp$logical$assign$tmpm1146332801$0;",
                "($jscomp$logical$assign$tmpm1146332801$0 = foo()).x ",
                "   ??",
                "($jscomp$logical$assign$tmpm1146332801$0.x = y);")));
  }

  @Test
  public void testPropertyReferenceElement() {
    test(
        srcs("a[x] ||= b"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "($jscomp$logical$assign$tmpm1146332801$0 = a)",
                "[$jscomp$logical$assign$tmpindexm1146332801$0 = x]",
                "   ||",
                "($jscomp$logical$assign$tmpm1146332801$0",
                "[$jscomp$logical$assign$tmpindexm1146332801$0] = b);")));
    test(
        srcs("a[x + 5 + 's'] &&= b"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "($jscomp$logical$assign$tmpm1146332801$0 = a)",
                "[$jscomp$logical$assign$tmpindexm1146332801$0 = (x + 5 + 's')] ",
                "   &&",
                "($jscomp$logical$assign$tmpm1146332801$0",
                "[$jscomp$logical$assign$tmpindexm1146332801$0] = b);")));
    test(
        srcs("foo[x] ??= bar[y]"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "($jscomp$logical$assign$tmpm1146332801$0 = foo)",
                "[$jscomp$logical$assign$tmpindexm1146332801$0 = x]",
                "   ??",
                "($jscomp$logical$assign$tmpm1146332801$0",
                "[$jscomp$logical$assign$tmpindexm1146332801$0] = bar[y]);")));
  }

  @Test
  public void testInExpressions() {
    test(srcs("a ?? (b ??= 1) ?? (c ??= 2)"), expected("a ?? (b ?? (b = 1)) ?? (c ?? (c = 2));"));
    test(
        srcs("for (let x = foo(); !x.b; x.b ||= {}) {}"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "for (let x = foo(); !x.b;",
                "($jscomp$logical$assign$tmpm1146332801$0 = x).b",
                "   ||",
                "($jscomp$logical$assign$tmpm1146332801$0.b = {})",
                ") {}")));
    test(
        srcs(lines("for (let x = foo; !x[b]; x[b] &&= {}) {}")),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "for (let x = foo; !x[b];",
                "($jscomp$logical$assign$tmpm1146332801$0 = x)",
                "[$jscomp$logical$assign$tmpindexm1146332801$0 = b]",
                "   &&",
                "($jscomp$logical$assign$tmpm1146332801$0",
                "[$jscomp$logical$assign$tmpindexm1146332801$0] = {})",
                ") {}")));
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
    test(
        srcs(
            lines(
                "foo().x = 4;", //
                "do {",
                "  foo().x = foo().x - 1;",
                "} while (foo().x ||= null);")),
        expected(
            lines(
                "foo().x = 4;", //
                "let $jscomp$logical$assign$tmpm1146332801$0;",
                "do {",
                "  foo().x = foo().x - 1;",
                "} while (($jscomp$logical$assign$tmpm1146332801$0 = foo()).x ",
                "          ||",
                "         ($jscomp$logical$assign$tmpm1146332801$0.x = null));")));
    test(
        srcs(
            lines(
                "foo[x] = 4;", //
                "do {",
                "  foo[x] = foo[x] - 1;",
                "} while (foo[x] &&= null);")),
        expected(
            lines(
                "foo[x] = 4;", //
                "let $jscomp$logical$assign$tmpm1146332801$0;",
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "do {",
                "  foo[x] = foo[x] - 1;",
                "} while (($jscomp$logical$assign$tmpm1146332801$0 = foo)",
                "         [$jscomp$logical$assign$tmpindexm1146332801$0 = x] ",
                "          &&",
                "         ($jscomp$logical$assign$tmpm1146332801$0",
                "         [$jscomp$logical$assign$tmpindexm1146332801$0] = null));")));
  }

  @Test
  public void testNestedPropertyReference() {
    test(
        srcs("a ||= (b &&= (c ??= d));"), //
        expected("a || (a = (b && (b = (c ?? (c = d)))));"));

    test(
        srcs("a.x ??= (b.x ||= {});"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpm1146332801$1;",
                "(($jscomp$logical$assign$tmpm1146332801$1 = a).x",
                "   ??",
                "($jscomp$logical$assign$tmpm1146332801$1.x = ",
                "($jscomp$logical$assign$tmpm1146332801$0 = b).x",
                "   ||",
                "($jscomp$logical$assign$tmpm1146332801$0.x = {})));")));

    test(
        srcs("a[x] &&= (b[x] ??= a[x]);"),
        expected(
            lines(
                "let $jscomp$logical$assign$tmpm1146332801$0;", //
                "let $jscomp$logical$assign$tmpindexm1146332801$0;",
                "let $jscomp$logical$assign$tmpm1146332801$1;",
                "let $jscomp$logical$assign$tmpindexm1146332801$1;",
                "($jscomp$logical$assign$tmpm1146332801$1 = a)",
                "[$jscomp$logical$assign$tmpindexm1146332801$1 = x]",
                "   &&",
                "($jscomp$logical$assign$tmpm1146332801$1",
                "[$jscomp$logical$assign$tmpindexm1146332801$1] =",
                "($jscomp$logical$assign$tmpm1146332801$0 = b)",
                "[$jscomp$logical$assign$tmpindexm1146332801$0 = x]",
                "   ??",
                "($jscomp$logical$assign$tmpm1146332801$0",
                "[$jscomp$logical$assign$tmpindexm1146332801$0] = a[x]));")));
  }
}
