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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckExtraRequires.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.SourceFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the "extra requires" check in {@link CheckMissingAndExtraRequires}. */
@RunWith(JUnit4.class)
public final class CheckExtraRequiresTest extends CompilerTestCase {
  public CheckExtraRequiresTest() {
    super();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckExtraRequires(compiler, null);
  }

  @Test
  public void testExtraRequire() {
    testError("goog.require('foo.Bar');", EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testExtraImport() {
    testError("import z from '/x.y';", EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testFailForwardDeclareInModule() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "var Unused = goog.forwardDeclare('goog.events.Unused');",
            "",
            "/**",
            " * @param {!Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}",
            "",
            "exports = listener;"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testShadowedUnusedImport() {
    // It would be nice to catch this, but currently the pass is name based and thus misses
    // the fact that the import is unused.
    testSame(
        lines(
            "goog.module('example');",
            "",
            "var Shadowed = goog.forwardDeclare('foo.Shadowed');",
            "",
            "function f(Shadowed) {",
            "  alert(Shadowed);",
            "}"));
  }

  @Test
  public void testFailForwardDeclare() {
    testError(
        lines(
            "goog.forwardDeclare('goog.events.Event');",
            "goog.forwardDeclare('goog.events.Unused');",
            "",
            "/**",
            " * @param {!goog.events.Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testNoWarning_require() {
    testSame("goog.require('foo.Bar'); var x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); let x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); const x = new foo.Bar();");
    testSame("goog.require('foo.Bar'); /** @type {foo.Bar} */ var x;");
    testSame("goog.require('foo.Bar'); /** @type {Array<foo.Bar>} */ var x;");
    testSame("goog.require('foo.Bar'); var x = new foo.Bar.Baz();");
    testSame("goog.require('foo.bar'); var x = foo.bar();");
    testSame("goog.require('foo.bar'); var x = /** @type {foo.bar} */ (null);");
    testSame("goog.require('foo.bar'); function f(/** foo.bar */ x) {}");
    testSame("goog.require('foo.bar'); alert(foo.bar.baz);");
    testSame("/** @suppress {extraRequire} */ goog.require('foo.bar');");
    testSame("goog.require('foo.bar'); goog.scope(function() { var bar = foo.bar; alert(bar); });");
    testSame("goog.require('foo'); foo();");
    testSame("goog.require('foo'); new foo();");
    testSame("/** @suppress {extraRequire} */ var bar = goog.require('foo.bar');");
  }

  @Test
  public void testNoWarning_requireType() {
    testSame("goog.requireType('foo.Bar'); /** @type {foo.Bar} */ var x;");
    testSame("goog.requireType('foo.Bar'); /** @type {Array<foo.Bar>} */ var x;");
    testSame("goog.requireType('foo.bar'); function f(/** foo.bar */ x) {}");
    testSame("/** @suppress {extraRequire} */ goog.requireType('foo.bar');");
    testSame("/** @suppress {extraRequire} */ var bar = goog.requireType('foo.bar');");
  }

  @Test
  public void testNoWarning_require_externsJsDoc() {
    testSame(
        externs(ImmutableList.of(SourceFile.fromCode("externs", "/** @const */ var ns;"))),
        srcs("goog.require('ns.Foo'); /** @type {ns.Foo} */ var f;"));
  }

  @Test
  public void testNoWarning_requireType_externsJsDoc() {
    testSame(
        externs(ImmutableList.of(SourceFile.fromCode("externs", "/** @const */ var ns;"))),
        srcs("goog.requireType('ns.Foo'); /** @type {ns.Foo} */ var f;"));
  }

  @Test
  public void testNoWarning_require_externsNew() {
    testSame(
        externs(ImmutableList.of(SourceFile.fromCode("externs", "/** @const */ var ns;"))),
        srcs("goog.require('ns.Foo'); new ns.Foo();"));
  }

  @Test
  public void testNoWarning_requireType_externsNew() {
    testSame(
        externs(ImmutableList.of(SourceFile.fromCode("externs", "/** @const */ var ns;"))),
        srcs("goog.requireType('ns.Foo'); new ns.Foo();"));
  }

  @Test
  public void testNoWarning_esImport_objlitShorthand() {
    testSame(
        lines(
            "import '/example.module';", //
            "",
            "import X from '/example.X';",
            "alert({X});"));
  }

  @Test
  public void testNoWarning_require_InnerClassInExtends() {
    testSame(
        lines(
            "var goog = {};",
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}"));
  }

  @Test
  public void testNoWarning_requireType_InnerClassInExtends() {
    testSame(
        lines(
            "var goog = {};",
            "goog.requireType('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}"));
  }

  @Test
  public void testWarning_require() {
    testError("goog.require('foo.bar');", EXTRA_REQUIRE_WARNING);

    testError(
        lines("goog.require('Bar');", "function func( {a} ){}", "func( {a: 1} );"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines("goog.require('Bar');", "function func( a = 1 ){}", "func(42);"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testWarning_requireType() {
    testError("goog.requireType('foo.bar');", EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "goog.requireType('Bar');", //
            "/** @type {string} */ var x"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testNoWarningMultipleFiles() {
    testSame(
        srcs(
            "goog.require('Foo'); var foo = new Foo();",
            "goog.require('Bar'); var bar = new Bar();"));
  }

  @Test
  public void testPassModule() {
    testSame(lines("import {Foo} from '/bar';", "new Foo();"));

    testSame(lines("import Bar from '/bar';", "new Bar();"));

    testSame(lines("import {CoolFeature as Foo} from '/bar';", "new Foo();"));

    testSame(
        lines(
            "import Bar, {CoolFeature as Foo, OtherThing as Baz} from '/bar';",
            "new Foo(); new Bar(); new Baz();"));
  }

  @Test
  public void testFailModule() {
    testError("import {Foo} from '/bar';", EXTRA_REQUIRE_WARNING);

    testError("import {Foo as Foo} from '/bar';", EXTRA_REQUIRE_WARNING);

    testError("import {Foo as Bar} from '/bar';", EXTRA_REQUIRE_WARNING);

    testError(
        lines("import {Foo} from '/bar';", "goog.require('example.ExtraRequire');", "new Foo;"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import {Foo} from '/bar';", //
            "goog.requireType('example.ExtraRequire');",
            "new Foo;"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testPassForwardDeclareInModule() {
    testSame(
        lines(
            "goog.module('example');",
            "",
            "var Event = goog.forwardDeclare('goog.events.Event');",
            "",
            "/**",
            " * @param {!Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}",
            "",
            "exports = listener;"));
  }

  @Test
  public void testPassForwardDeclare() {
    testSame(
        lines(
            "goog.forwardDeclare('goog.events.Event');",
            "",
            "/**",
            " * @param {!goog.events.Event} event",
            " */",
            "function listener(event) {",
            "  alert(event);",
            "}"));
  }

  @Test
  public void testGoogModuleGet() {
    testSame(
        lines(
            "goog.provide('x.y');",
            "goog.require('foo.bar');",
            "",
            "goog.scope(function() {",
            "var bar = goog.module.get('foo.bar');",
            "x.y = function() {};",
            "});"));
  }

  @Test
  public void testGoogModuleWithAliasedRequire() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const asserts = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  asserts.assert(true);",
            "};"));

    testError(
        lines(
            "goog.module('example');",
            "",
            "const asserts = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts.assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithDestructuringRequire() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const {assert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"));

    testError(
        lines(
            "goog.module('example');",
            "",
            "const {assert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts.assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithDestructuringShortnameRequire() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const {assert: googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  googAssert(true);",
            "};"));

    testError(
        lines(
            "goog.module('example');",
            "",
            "var {assert: googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "goog.module('example');",
            "",
            "const {assert: googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts.assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithPartiallyUnusedDestructuringRequire() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "const {assert, fail} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithEmptyDestructuringRequire() {
    testError(
        lines("goog.module('example');", "", "var {} = goog.require('goog.asserts');"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithAliasedRequireType() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const color = goog.requireType('goog.color');",
            "",
            "exports = /** @param {color.Rgb} x */ function(x) { alert(x); };"));

    testError(
        lines(
            "goog.module('example');",
            "",
            "const color = goog.requireType('goog.color');",
            "",
            "exports = /** @param {goog.color.Rgb} x */ function(x) { alert(x); };"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithDestructuringRequireType() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "const {Rgb} = goog.requireType('goog.color');",
            "",
            "exports = /** @param {goog.color.Rgb} x */ function(x) { alert(x); };"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithDestructuringShortnameRequireType() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const {Rgb: googColorRgb} = goog.requireType('goog.color');",
            "",
            "exports = /** @param {googColorRgb} x */ function(x) { alert(x); };"));

    testError(
        lines(
            "goog.module('example');",
            "",
            "const {Rgb: googColorRgb} = goog.requireType('goog.color');",
            "",
            "exports = /** @param {Rgb} x */ function(x) { alert(x); };"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "goog.module('example');",
            "",
            "const {Rgb: googColorRgb} = goog.requireType('goog.color');",
            "",
            "exports = /** @param {goog.color.Rgb} x */ function(x) { alert(x); };"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithPartiallyUnusedDestructuringRequireType() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "const {Rgb, Hsv} = goog.require('goog.color');",
            "",
            "exports = /** @param {Rgb} x */ function(x) { alert(x); };"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithEmptyDestructuringRequireType() {
    testError(
        lines(
            "goog.module('example');", //
            "",
            "var {} = goog.requireType('goog.color');"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testES6ModuleWithDestructuringRequire() {
    testError(
        lines(
            "import '/example';",
            "",
            "import {assert, fail} from '/goog.asserts';",
            "",
            "export default function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import '/example';",
            "",
            "import {assert as assert, fail as fail} from '/goog.asserts';",
            "",
            "export default function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import '/example';",
            "",
            "import {assert as a, fail as f} from '/goog.asserts';",
            "",
            "export default function() {",
            "  a(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }
}
