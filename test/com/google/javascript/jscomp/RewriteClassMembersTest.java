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
 * Test cases for transpilation pass that replaces public class fields and class static blocks:
 * <code><pre>
 * class C {
 *   x = 2;
 *   ['y'] = 3;
 *   static a;
 *   static ['b'] = 'hi';
 *   static {
 *     let c = 4;
 *     this.z = c;
 *   }
 * }
 * </pre></code>
 */
@RunWith(JUnit4.class)
public final class RewriteClassMembersTest extends CompilerTestCase {

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
    return new RewriteClassMembers(compiler);
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

    testError(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    this.y = x",
            "  }",
            "}"),
        /*lines(
        "class C {}", //
        "{",
        "  let x = 2;",
        "  C.y = x", // TODO(b/235871861): Need to correct references to `this`
        "}")*/
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "class C extends B{", //
            "  static {",
            "    let x = super.y",
            "  }",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "let c = class C{", //
            "  static {",
            "    C.y = 2;",
            "    let x = C.y",
            "  }",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "foo(class C{", //
            "  static {",
            "    C.y = 2;",
            "    let x = C.y",
            "  }",
            "})"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "foo(class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "})"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "let c = class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "class C {", //
            "  static {",
            "    C.x = 2",
            "    const y = this.x",
            "  }",
            "}"),
        /*lines(
        "class C {}", //
        "{",
        "  C.x = 2;",
        "  const y = C.x",
        "}")*/
        Es6ToEs3Util.CANNOT_CONVERT_YET);

    testError(
        lines(
            "var z = 1", //
            "class C {",
            "  static {",
            "    let x = 2",
            "    var z = 3;",
            "  }",
            "}"),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  @Test
  public void testClassStaticBlocksNoFieldAssign() {
    test(
        lines(
            "class C {", //
            "  static {",
            "  }",
            "}"),
        lines(
            "class C {", //
            "}",
            "{}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    const y = x",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "  const y = x",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    const y = x",
            "    let z;",
            "    if (x - y == 0) {z = 1} else {z = 2}",
            "    while (x - z > 10) {z++;}",
            "    for (;;) {break;}",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "  const y = x",
            "  let z;",
            "  if (x - y == 0) {z = 1} else {z = 2}",
            "  while (x - z > 10) {z++;}",
            "  for (;;) {break;}",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "  }",
            "  static {",
            "    const y = x",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "}",
            "{",
            "  const y = x",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "  }",
            "  static {",
            "    const y = x",
            "  }",
            "}",
            "class D {",
            "  static {",
            "    let z = 1",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "}",
            "{",
            "  const y = x",
            "}",
            "class D {}",
            "{",
            "  let z = 1;",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = function () {return 1;}",
            "    const y = () => {return 2;}",
            "    function a() {return 3;}",
            "    let z = (() => {return 4;})();",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = function () {return 1;}",
            "  const y = () => {return 2;}",
            "  function a() {return 3;}",
            "  let z = (() => {return 4;})();",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    C.x = 2",
            // "    const y = C.x", //TODO(b/189993301) blocked on typechecking, gets
            // JSC_INEXISTENT_PROPERTY
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  C.x = 2;",
            // "  const y = C.x",
            "}"));
    test(
        lines(
            "class Foo {",
            "  static {",
            "    let x = 5;",
            "    class Bar {",
            "      static {",
            "        let x = 'str';",
            "      }",
            "    }",
            "  }",
            "}"),
        lines(
            "class Foo {}", //
            "{",
            "  let x = 5;",
            "  class Bar {}",
            "  {let x = 'str';}",
            "}"));
  }
}
