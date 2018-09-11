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

import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the "extra requires" check in {@link CheckMissingAndExtraRequires}. */
@RunWith(JUnit4.class)
public final class ExtraRequireTest extends CompilerTestCase {
  public ExtraRequireTest() {
    super();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingAndExtraRequires(
        compiler, CheckMissingAndExtraRequires.Mode.FULL_COMPILE);
  }

  @Test
  public void testNoWarning() {
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
  public void testNoWarning_externsJsDoc() {
    String js = "goog.require('ns.Foo'); /** @type {ns.Foo} */ var f;";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testNoWarning_externsNew() {
    String js = "goog.require('ns.Foo'); new ns.Foo();";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    testSame(externs(externs), srcs(js));
  }

  @Test
  public void testNoWarning_objlitShorthand() {
    testSame(
        lines(
            "goog.module('example.module');",
            "",
            "const X = goog.require('example.X');",
            "alert({X});"));

    testSame(
        lines(
            "goog.require('X');",
            "alert({X});"));
  }

  @Test
  public void testNoWarning_objlitShorthand_withES6Modules() {
    testSame(
        lines(
            "import 'example.module';",
            "",
            "import X from 'example.X';",
            "alert({X});"));
  }

  @Test
  public void testNoWarning_InnerClassInExtends() {
    String js =
        lines(
            "var goog = {};",
            "goog.require('goog.foo.Bar');",
            "",
            "/** @constructor @extends {goog.foo.Bar.Inner} */",
            "function SubClass() {}");
    testSame(js);
  }

  @Test
  public void testWarning() {
    testError("goog.require('foo.bar');", EXTRA_REQUIRE_WARNING);

    testError(lines(
        "goog.require('Bar');",
        "function func( {a} ){}",
        "func( {a: 1} );"), EXTRA_REQUIRE_WARNING);
    testError(lines(
        "goog.require('Bar');",
        "function func( a = 1 ){}",
        "func(42);"), EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testNoWarningMultipleFiles() {
    String[] js = new String[] {
      "goog.require('Foo'); var foo = new Foo();",
      "goog.require('Bar'); var bar = new Bar();"
    };
    testSame(js);
  }

  @Test
  public void testPassModule() {
    testSame(
        lines(
            "import {Foo} from 'bar';",
            "new Foo();"));

    testSame(
        lines(
            "import Bar from 'bar';",
            "new Bar();"));

    testSame(
        lines(
            "import {CoolFeature as Foo} from 'bar';",
            "new Foo();"));

    testSame(
        lines(
            "import Bar, {CoolFeature as Foo, OtherThing as Baz} from 'bar';",
            "new Foo(); new Bar(); new Baz();"));
  }

  @Test
  public void testFailModule() {
    testError(
        "import {Foo} from 'bar';",
        EXTRA_REQUIRE_WARNING);

    testError(
        "import {Foo as Foo} from 'bar';",
        EXTRA_REQUIRE_WARNING);

    testError(
        "import {Foo as Bar} from 'bar';",
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import {Foo} from 'bar';",
            "goog.require('example.ExtraRequire');",
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
  public void testUnusedForwardDeclareInModule() {
    // Reports extra require warning, but only in single-file mode.
    testSame(
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
  public void testFailForwardDeclare() {
    // Reports extra require warning, but only in single-file mode.
    testSame(
        lines(
            "goog.forwardDeclare('goog.events.Event');",
            "goog.forwardDeclare('goog.events.Unused');",
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
  public void testGoogModuleWithDestructuringRequire() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "var dom = goog.require('goog.dom');",
            "var {assert} = goog.require('goog.asserts');",
            "",
            "/**",
            " * @param {Array<string>} ids",
            " * @return {Array<HTMLElement>}",
            " */",
            "function getElems(ids) {",
            "  return ids.map(id => dom.getElement(id));",
            "}",
            "",
            "exports = getElems;"),
        EXTRA_REQUIRE_WARNING);

     testSame(
        lines(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  googAssert(true);",
            "};"));

     testError(
        lines(
            "goog.module('example');",
            "",
            "var {assert, fail} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testError(
        lines(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testError(
        lines(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testES6ModuleWithDestructuringRequire() {
    testError(
        lines(
            "import 'example';",
            "",
            "import {assert, fail} from 'goog.asserts';",
            "",
            "export default function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import 'example';",
            "",
            "import {assert as assert, fail as fail} from 'goog.asserts';",
            "",
            "export default function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

    testError(
        lines(
            "import 'example';",
            "",
            "import {assert as a, fail as f} from 'goog.asserts';",
            "",
            "export default function() {",
            "  a(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithEmptyDestructuringRequire() {
    testError(
        lines(
            "goog.module('example');",
            "",
            "var {} = goog.require('goog.asserts');"),
        EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testGoogModuleWithNamespaceRequire() {
    testNoWarning(
        lines(
            "goog.module('example');",
            "",
            "const ns = goog.require('a.namespace');",
            "",
            "/** @implements {ns.Interface} */",
            "class AGreatClass {}",
            ""));
  }
}
