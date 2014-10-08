/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.lint.CheckNullableReturn;

/**
 * Tests for {@link CheckNullableReturn}.
 */
public class CheckNullableReturnTest extends CompilerTestCase {

  public CheckNullableReturnTest() {
    enableTypeCheck(CheckLevel.OFF);
    enableLintChecks(CheckLevel.ERROR);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckNullableReturn(compiler));
  }

  public void testNullableReturn() {
    testOk("return null;");
    testOk("if (a) { return null; } return {};");
    testOk("switch(1) { case 12: return null; } return {};");
    testOk(
        "try { if (a) throw ''; }\n"
        + " catch (e) { return null; }\n"
        + " return {}");
    testOk(
        "/** @return {number} */ function f() { var x; }; return null;");
  }

  public void testReturnNotNullable()  {
    // Empty function body. Ignore this case. The remainder of the functions in
    // this test have non-empty bodies.
    testOk("");

    // Simple cases.
    testWithError("return {};");
    testOk("throw new Error('Not implemented');");

    // Test try catch finally.
    testOk("try { return bar() } catch (e) { } finally { }");

    // Nested function.
    testWithError(
        "/** @return {number} */ function f() { return 1; }; return {};");

    testWithError("try { return {}; } finally { return {}; }");
    testWithError("try { } finally { return {}; }");
    testWithError("switch(1) { default: return {}; }");
    testWithError("switch(g) { case 1: return {}; default: return {}; }");
  }

  public void testFinallyStatements() {
    // The control flow analysis (CFA) treats finally blocks somewhat strangely.
    // The CFA might indicate that a finally block implicitly returns. However,
    // if entry into the finally block is normally caused by an explicit return
    // statement, then a return statement isn't missing:
    //
    // try {
    //   return 1;
    // } finally {
    //   // CFA determines implicit return. However, return not missing
    //   // because of return statement in try block.
    // }
    //
    // Hence extra tests are warranted for various cases involving finally
    // blocks.

    // Simple finally case.
    testWithError("try { return {}; } finally { }");
    testWithError("try { } finally { return {}; }");
    testWithError("try { } finally { }");

    // Cycles in the CFG within the finally block were causing problems before.
    testWithError("try { return {}; } finally { while (true) { } }");
    testWithError("try { } finally { while (x) { } }");
    testWithError("try { } finally { while (x) { if (x) { break; } } }");
    testWithError(
        "try { return {}; } finally { while (x) { if (x) { break; } } }");
  }

  public void testKnownConditions() {
    testWithError("if (true) return {}; return null;");
    testOk("if (true) { return null; } else { return {}; }");

    testOk("if (false) return {}; return null;");
    testWithError("if (false) { return null; } else { return {}; }");

    testWithError("if (1) return {}");
    testOk("if (1) { return null; } else { return {}; }");

    testOk("if (0) return {}; return null;");
    testWithError("if (0) { return null; } else { return {}; }");

    testWithError("if (3) return {}");
    testOk("if (3) { return null; } else { return {}; }");
  }

  public void testKnownWhileLoop() {
    testWithError("while (1) return {}");
    testWithError("while (1) { if (x) { return {}; } else { return {}; }}");
    testWithError("while (0) {} return {}");

    // Not known.
    testWithError("while(x) { return {}; }");
  }

  public void testMultiConditions() {
    testOk("if (a) { return null; } else { while (1) {return {}; } }");
    testWithError("if (a) { return {}; } else { while (1) {return {}; } }");
  }

  public void testExtendsOverride() {
    String js =
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {" +
        "  /** @return {?Object} */ method: function() { throw new Error() }" +
        "};" +
        "/** @constructor\n @extends {Foo} */ function Bar() {}" +
        "Bar.prototype = {" +
        "  /** @return {?Object} */ method: function() { return {}; }" +
        "};";
    testSame(js);
  }

  public void testTwoLevelExtendsOverride() {
    String js =
        "/** @constructor */ function Top() {}" +
        "Top.prototype = {" +
        "  /** @return {?Object} */ method: function() { throw new Error() }" +
        "};" +
        "/** @constructor @extends {Top} */ function Foo() {}" +
        "Foo.prototype = {" +
        "};" +
        "/** @constructor\n @extends {Foo} */ function Bar() {}" +
        "Bar.prototype = {" +
        "  /** @return {?Object} */ method: function() { return {}; }" +
        "};";
    testSame(js);
  }

  public void testImplementsOverride() {
    String js =
        "/** @interface */ function Foo() {}" +
        "Foo.prototype = {" +
        "  /** @return {?Object} */ method: function() { }" +
        "};" +
        "/** @constructor @implements {Foo} */ function Bar() {}" +
        "Bar.prototype = {" +
        "  /** @return {?Object} */ method: function() { return {}; }" +
        "};";
    testSame(js);
  }

  public void testTwoLevelImplementsOverride() {
    String js =
      "/** @interface */ function Top() {}" +
      "Top.prototype = {" +
      "  /** @return {?Object} */ method: function() { }" +
      "};" +
      "/** @interface @extends {Top} */ function Foo() {}" +
      "Foo.prototype = {" +
      "};" +
      "/** @constructor @implements {Foo} */ function Bar() {}" +
      "Bar.prototype = {" +
      "  /** @return {?Object} */ method: function() { return {}; }" +
      "};";
    testSame(js);
  }

  private static String createFunction(String returnType, String body) {
    return "/** @return {" + returnType + "} */ function foo() {" + body + "}";
  }

  private void testOk(String returnType, String body) {
    testSame(createFunction(returnType, body));
  }

  private void testWithError(String returnType, String body) {
    String js = createFunction(returnType, body);
    test(js, js, CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  /** Creates function with return type {?Object} */
  private void testWithError(String body) {
    testWithError("?Object", body);
  }

  /** Creates function with return type {?Object} */
  private void testOk(String body) {
    testOk("?Object", body);
  }
}
