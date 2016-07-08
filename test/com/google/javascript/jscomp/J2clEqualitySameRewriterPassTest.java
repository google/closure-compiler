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

public class J2clEqualitySameRewriterPassTest extends CompilerTestCase {
  private static final String EXTERN = "Equality.$same = function(a, b) {};";

  private boolean useTypesForOptimization = true;

  public J2clEqualitySameRewriterPassTest() {
    super(EXTERN);
  }

  @Override
  public void setUp() {
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clEqualitySameRewriterPass(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.useTypesForOptimization = useTypesForOptimization;
    return super.getOptions(options);
  }

  public void testRewriteEqualitySame() {
    test(
        LINE_JOINER.join(
            "Equality.$same(0, '');",
            "var a = 'ABC';",
            "Equality.$same(a, 'ABC');",
            "var b = 5;",
            "Equality.$same(b, 5);",
            "Equality.$same(b, []);",
            "Equality.$same(b, null);",
            "Equality.$same(null, b);",
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
            "/** @type {!Object} */",
            "var obj1 = {};",
            "/** @type {Object} */",
            "var obj2 = null;",
            "Equality.$same(obj1, obj2);",
            "Equality.$same(obj1, str2);",
            "Equality.$same(obj1, num2);"),
        LINE_JOINER.join(
            "0 === '';",
            "var a = 'ABC';",
            "a === 'ABC';",
            "var b = 5;",
            "b === 5;",
            "b === [];",
            "b == null;",
            "null == b;",
            "/** @type {number|undefined} */",
            "var num1 = 5;",
            "/** @type {?number} */",
            "var num2 = 5;",
            "num1 == num2;",
            "/** @type {string} */",
            "var str1 = '';",
            "/** @type {string|undefined} */",
            "var str2 = 'abc';",
            "str1 == str2;",
            "/** @type {!Object} */",
            "var obj1 = {};",
            "/** @type {Object} */",
            "var obj2 = null;",
            "obj1 == obj2;",
            "obj1 == str2;",
            "obj1 == num2;"));
  }

  public void testNotRewriteEqualitySame() {
    testSame(
        LINE_JOINER.join(
            "Equality.$same(c, d);",
            "/** @type {number} */",
            "var num = 5",
            "/** @type {string} */",
            "var str = 'ABC';",
            "/** @type {*} */",
            "var allType = null;",
            "Equality.$same(num, str);",
            "Equality.$same(num, allType);",
            "Equality.$same(str, allType);"));
  }

  public void testNotRewriteEqualitySame_allType() {
    testSame(
        LINE_JOINER.join(
            "/** @type {*} */",
            "var allType1 = 1;",
            "/** @type {*} */",
            "var allType2 = '1';",
            "Equality.$same(allType1, allType2);"));
  }

  public void testNotRewriteEqualitySame_noTypeCheck() {
    useTypesForOptimization = false;
    testSame(
        LINE_JOINER.join(
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
            "/** @type {!Object} */",
            "var obj1 = {};",
            "/** @type {Object} */",
            "var obj2 = null;",
            "Equality.$same(obj1, obj2);",
            "Equality.$same(obj1, str2);",
            "Equality.$same(obj1, num2);"));
  }
}
