/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckRequiresForConstructors.EXTRA_REQUIRE_WARNING;

/**
 * Tests for the "extra requires" check in {@link CheckRequiresForConstructors}.
 */
public final class ExtraRequireTest extends Es6CompilerTestCase {
  public ExtraRequireTest() {
    super();
    enableRewriteClosureCode();
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresForConstructors(compiler);
  }

  public void testNoWarning() {
    testSame("goog.require('foo.Bar'); var x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); /** @type {foo.Bar} */ var x;");
    testSame("goog.require('foo.Bar'); /** @type {Array<foo.Bar>} */ var x;");
    testSame("goog.require('foo.Bar'); var x = new foo.Bar.Baz();");
    testSame("goog.require('foo.bar'); var x = foo.bar();");
    testSame("goog.require('foo.bar'); var x = /** @type {foo.bar} */ (null);");
    testSame("goog.require('foo.bar'); function f(/** foo.bar */ x) {}");
    testSame("goog.require('foo.bar'); alert(foo.bar.baz);");
    testSame("/** @suppress {extraRequire} */ goog.require('foo.bar');");
    test("goog.require('foo.bar'); goog.scope(function() { var bar = foo.bar; alert(bar); });",
        "goog.require('foo.bar'); alert(foo.bar);");
    testSame("goog.require('foo'); foo();");
  }

  public void testNoWarning_InnerClassInExtends() {
    String js =
        LINE_JOINER.join(
            "var goog = {};",
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}");
    testSame(js);
  }

  public void testWarning() {
    testError("goog.require('foo.bar');", EXTRA_REQUIRE_WARNING);
    // The local var "bar" is unused so after goog.scope rewriting, foo.bar is unused.
    testError("goog.require('foo.bar'); goog.scope(function() { var bar = foo.bar; });",
        EXTRA_REQUIRE_WARNING);
  }

  public void testNoWarningMultipleFiles() {
    String[] js = new String[] {
      "goog.require('Foo'); var foo = new Foo();",
      "goog.require('Bar'); var bar = new Bar();"
    };
    testSame(js);
  }

}
