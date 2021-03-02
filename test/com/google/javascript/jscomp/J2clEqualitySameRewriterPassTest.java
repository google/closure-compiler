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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class J2clEqualitySameRewriterPassTest extends CompilerTestCase {
  private static final String EXTERN =
      "Equality.$same = function(opt_a, opt_b) {};\n"
          + "String.m_equals__java_lang_String__java_lang_Object = function(opt_a, opt_b) {};";

  private static boolean useTypes;

  public J2clEqualitySameRewriterPassTest() {
    super(MINIMAL_EXTERNS + EXTERN);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    useTypes = false;
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), new J2clEqualitySameRewriterPass(useTypes));
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testRewriteEqualitySame() {
    test(
        lines(
            "Equality.$same(0, 0);",
            "Equality.$same(-0, -0);",
            "Equality.$same(0, -0);",
            "Equality.$same(NaN, NaN);",
            "Equality.$same(Infinity, Infinity);",
            "Equality.$same(Infinity, -Infinity);",
            "Equality.$same(0, null);",
            "Equality.$same(0, '');",
            "var a = 'ABC';",
            "Equality.$same(a, 'ABC');",
            "var b = {}",
            "Equality.$same(b, 5);",
            "Equality.$same(b, 5n)",
            "Equality.$same(b, []);",
            "Equality.$same(b, !a);",
            "Equality.$same(b, null);",
            "Equality.$same(null, b);",
            "Equality.$same(b, /** @type {null} */ (null));"),
        lines(
            "true",
            "true",
            "false",
            "true",
            "true",
            "false",
            "0 == null;",
            "0 === '';",
            "var a = 'ABC';",
            "a === 'ABC';",
            "var b = {};",
            "b === 5;",
            "b === 5n;",
            "b === [];",
            "b === !a;",
            "b == null;",
            "b == null;",
            "b == /** @type {null} */ (null);"));
  }

  @Test
  public void testRewriteEqualitySame_useColors() {
    useTypes = true;
    replaceTypesWithColors();
    test(
        lines(
            "var b = {};",
            "Equality.$same(b, null);",
            "Equality.$same(null, b);",
            "Equality.$same(b, undefined);",
            "Equality.$same(b, 0);",
            "Equality.$same(b, -0);",
            "Equality.$same(b, NaN);",
            "Equality.$same(b, Infinity);",
            "var c = 5;",
            "Equality.$same(c, null);",
            "Equality.$same(null, c);",
            "Equality.$same(c, undefined);",
            "var d = 5n;",
            "Equality.$same(d, null);",
            "Equality.$same(null, d);",
            "Equality.$same(d, undefined);"),
        lines(
            "var b = {};",
            "!b",
            "!b;",
            "!b;",
            "b === 0;",
            "b === -0;",
            "b === NaN;",
            "b === Infinity;",
            "var c = 5;",
            "c == null;", // Note that the semantics are preserved for number.
            "c == null;",
            "c == undefined;",
            "var d = 5n;",
            "d == null;",
            "d == null;",
            "d == undefined;"));
  }

  @Test
  public void testNotRewriteEqualitySame() {
    testSame(
        lines(
            "Equality.$same(c, d);",
            "/** @type {number} */",
            "var num = 5",
            "/** @type {bigint} */",
            "var bigint = 5n",
            "/** @type {string} */",
            "var str = 'ABC';",
            "/** @type {*} */",
            "var allType = null;",
            "Equality.$same(num, str);",
            "Equality.$same(num, bigint);",
            "Equality.$same(num, allType);",
            "Equality.$same(bigint, str);",
            "Equality.$same(bigint, allType);",
            "Equality.$same(str, allType);",
            "function hasSideEffects(){};",
            // Note that the first parameter has value 'undefined' but it has side effects.
            "Equality.$same(void hasSideEffects(), hasSideEffects());"));
  }

  @Test
  public void testNotRewriteEqualitySame_useColors() {
    useTypes = true;
    replaceTypesWithColors();
    disableCompareJsDoc(); // type -> color replacement also removes JSDoc
    testSame(
        lines(
            "/** @type {number|undefined} */",
            "var num1 = 5;",
            "/** @type {?number} */",
            "var num2 = 5;",
            "Equality.$same(num1, num2);",
            "/** @type {string} */",
            "var str1 = '';",
            "/** @type {string|undefined} */",
            "var str2 = 'abc';",
            "Equality.$same(str1, str2);",
            "/** @type {bigint} */",
            "var bigint1 = 0n;",
            "/** @type {bigint|undefined} */",
            "var bigint2 = 5n;",
            "Equality.$same(bigint1, bigint2);",
            "/** @type {!Object} */",
            "var obj1 = {};",
            "/** @type {Object} */",
            "var obj2 = null;",
            "Equality.$same(obj1, obj2);",
            "Equality.$same(obj1, str2);",
            "Equality.$same(obj1, num2);",
            "Equality.$same(obj1, bigint2);",
            "/** @type {number} */",
            "var num3 = 5;",
            "Equality.$same(num3, 0);",
            "Equality.$same(num3, -0);",
            "Equality.$same(num3, NaN);",
            "Equality.$same(0/0, NaN);",
            "/** @type {*} */",
            "var allType1 = 1;",
            "/** @type {*} */",
            "var allType2 = '1';",
            "Equality.$same(allType1, allType2);"));
  }

  @Test
  public void testNotRewriteEqualitySame_parametersOptimizedAway() {
    testSame(lines("Equality.$same(null);", "Equality.$same();"));
  }

  @Test
  public void testRewriteStringEquals() {
    test(
        LINE_JOINER.join(
            "String.m_equals__java_lang_String__java_lang_Object('', 0);",
            "var a = 'ABC';",
            "String.m_equals__java_lang_String__java_lang_Object('ABC', a);"),
        LINE_JOINER.join(
            "'' === 0;", //
            "var a = 'ABC';",
            "'ABC' === a;"));
  }

  @Test
  public void testNotRewriteStringEquals() {
    testSame(
        LINE_JOINER.join(
            "/** @type {?string} */",
            "var b = null;",
            "String.m_equals__java_lang_String__java_lang_Object(b, null);",
            "String.m_equals__java_lang_String__java_lang_Object(null, b);",
            "String.m_equals__java_lang_String__java_lang_Object(c, d);"));
  }
}
