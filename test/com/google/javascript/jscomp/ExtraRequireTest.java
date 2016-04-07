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

import static com.google.javascript.jscomp.CheckRequiresForConstructors.DUPLICATE_REQUIRE_WARNING;
import static com.google.javascript.jscomp.CheckRequiresForConstructors.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Tests for the "extra requires" check in {@link CheckRequiresForConstructors}.
 */
public final class ExtraRequireTest extends Es6CompilerTestCase {
  public ExtraRequireTest() {
    super();
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresForConstructors(compiler,
        CheckRequiresForConstructors.Mode.FULL_COMPILE);
  }

  public void testNoWarning() {
    testSame("goog.require('foo.Bar'); var x = new foo.Bar();");
    testSameEs6("goog.require('foo.Bar'); let x = new foo.Bar();");
    testSameEs6("goog.require('foo.Bar'); const x = new foo.Bar();");
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

  public void testNoWarning_externsJsDoc() {
    String js = "goog.require('ns.Foo'); /** @type {ns.Foo} */ var f;";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    test(externs, js, js, null, null, null);
  }

  public void testNoWarning_externsNew() {
    String js = "goog.require('ns.Foo'); new ns.Foo();";
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs",
        "/** @const */ var ns;"));
    test(externs, js, js, null, null, null);
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

    testErrorEs6(LINE_JOINER.join(
        "goog.require('Bar');",
        "function func( {a} ){}",
        "func( {a: 1} );"), EXTRA_REQUIRE_WARNING);
    testErrorEs6(LINE_JOINER.join(
        "goog.require('Bar');",
        "function func( a = 1 ){}",
        "func(42);"), EXTRA_REQUIRE_WARNING);

    testError(
        LINE_JOINER.join(
            "goog.require('Bar');",
            "goog.require('Bar');",
            "var b = new Bar();"),
        DUPLICATE_REQUIRE_WARNING);
  }

  public void testNoWarningMultipleFiles() {
    String[] js = new String[] {
      "goog.require('Foo'); var foo = new Foo();",
      "goog.require('Bar'); var bar = new Bar();"
    };
    testSame(js);
  }

  public void testPassModule() {
    testSameEs6(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "new Foo();"));

    testSameEs6(
        LINE_JOINER.join(
            "import Bar from 'bar';",
            "new Bar();"));

    testSameEs6(
        LINE_JOINER.join(
            "import {CoolFeature as Foo} from 'bar';",
            "new Foo();"));

    testSameEs6(
        LINE_JOINER.join(
            "import Bar, {CoolFeature as Foo, OtherThing as Baz} from 'bar';",
            "new Foo(); new Bar(); new Baz();"));
  }

  public void testFailModule() {
    testErrorEs6(
        "import {Foo} from 'bar';",
        EXTRA_REQUIRE_WARNING);

    testErrorEs6(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "import {Bar as Foo} from 'bar';",
            "new Foo;"),
            DUPLICATE_REQUIRE_WARNING);

    testErrorEs6(
        LINE_JOINER.join(
            "import Foo from 'bar';",
            "import {Bar as Foo} from 'bar';",
            "new Foo;"),
            DUPLICATE_REQUIRE_WARNING);

    testErrorEs6(
        LINE_JOINER.join(
            "import {Foo} from 'bar';",
            "goog.require('example.ExtraRequire');",
            "new Foo;"),
            EXTRA_REQUIRE_WARNING);
  }

  public void testGoogModuleGet() {
    testSame(
        LINE_JOINER.join(
            "goog.provide('x.y');",
            "goog.require('foo.bar');",
            "",
            "goog.scope(function() {",
            "var bar = goog.module.get('foo.bar');",
            "x.y = function() {};",
            "});"));
  }

  public void testGoogModuleWithDestructuringRequire() {
    testErrorEs6(
        LINE_JOINER.join(
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

     testSameEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  googAssert(true);",
            "};"));

     testErrorEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert, fail} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testErrorEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  goog.asserts(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);

     testErrorEs6(
        LINE_JOINER.join(
            "goog.module('example');",
            "",
            "var {assert : googAssert} = goog.require('goog.asserts');",
            "",
            "exports = function() {",
            "  assert(true);",
            "};"),
        EXTRA_REQUIRE_WARNING);
  }
}
