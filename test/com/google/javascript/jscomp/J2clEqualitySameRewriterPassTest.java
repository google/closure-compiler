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

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clEqualitySameRewriterPass(compiler);
  }

  public void testRewriteEqualitySame() {
    test(
        LINE_JOINER.join(
            "Equality$$0same(0, '');",
            "var a = 'ABC';",
            "Equality$$0same(a, 'ABC');",
            "var b = 5;",
            "Equality$$0same(b, 5);",
            "Equality$$0same(b, []);",
            "Equality$$0same(b, null);",
            "Equality$$0same(null, b);"),
        LINE_JOINER.join(
            "0 === '';",
            "var a = 'ABC';",
            "a === 'ABC';",
            "var b = 5;",
            "b === 5;",
            "b === [];",
            "b == null;",
            "null == b;"));
  }

  public void testRewriteEqualitySame_avoidNonLiterals() {
    testSame(
        LINE_JOINER.join(
            "Equality$$0same(c, d);",
            "var a = 5, b = 5;",
            "Equality$$0same(a, b);"));
  }
}
