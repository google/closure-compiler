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
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_WARNING;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckMissingAndExtraRequires} in single-file mode. */
@RunWith(JUnit4.class)
public final class SingleFileCheckRequiresTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckMissingAndExtraRequires(
        compiler, CheckMissingAndExtraRequires.Mode.SINGLE_FILE);
  }

  @Test
  public void testReferenceToSingleName() {
    testSame("new Foo();");
    testSame("new Array();");
    testSame("new Error();");
  }

  @Test
  public void testCtorExtendsSingleName() {
    testSame("/** @constructor @extends {Foo} */ function MyFoo() {}");
    testSame("/** @constructor @extends {Error} */ function MyError() {}");
    testSame("/** @constructor @extends {Array} */ function MyArray() {}");
  }

  @Test
  public void testCtorExtendsSingleName_withES6Modules() {
    testSame("export /** @constructor @extends {Foo} */ function MyFoo() {}");
    testSame("export /** @constructor @extends {Error} */ function MyError() {}");
    testSame("export /** @constructor @extends {Array} */ function MyArray() {}");
  }

  @Test
  public void testClassExtendsSingleName() {
    testSame("class MyFoo extends Foo {}");
    testSame("class MyError extends Error {}");
    testSame("class MyArray extends Array {}");
  }

  @Test
  public void testClassExtendsSingleName_withES6Modules() {
    testSame("export class MyFoo extends Foo {}");
    testSame("export class MyError extends Error {}");
    testSame("export class MyArray extends Array {}");
  }

  @Test
  public void testReferenceToQualifiedName() {
    testError(
        lines(
            "goog.require('x.y.z');",
            "goog.require('bar.Abc');",
            "new x.y.z();",
            "new bar.Abc();",
            "new bar.Foo();"),
        MISSING_REQUIRE_WARNING);
  }

  // Since there are no goog.require()s for any bar.* names, assume that bar
  // is a "non-Closurized" namespace, i.e. that all bar.* names come from the externs.
  @Test
  public void testReferenceToQualifiedName_nonClosurizedNamespace() {
    testSame(
        lines(
            "goog.require('x.y.z');",
            "new x.y.z();",
            "new bar.Foo();"));
  }

  @Test
  public void testReferenceToUnqualifiedName() {
    testSame(
        lines(
            "goog.module('a.b.c');",
            "var z = goog.require('x.y.z');",
            "",
            "exports = { foobar : z };"));

    testSame(
        lines(
            "goog.module('a.b.c');",
            "var {z} = goog.require('x.y');",
            "",
            "exports = { foobar : z };"));

    testSame(
        lines(
            "import {z} from 'x.y'",
            "",
            "export var foobar = z;"));

    testSame(
        lines(
            "import z from 'x.y.z'",
            "",
            "export var foobar = z;"));
  }

  @Test
  public void testExtraRequire() {
    testError("goog.require('foo.Bar');", EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testExtraImport() {
    testError("import z from 'x.y';", EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testUnqualifiedRequireUsedInJSDoc() {
    testSame("goog.require('Bar'); /** @type {Bar} */ var x;");
  }

  @Test
  public void testUnqualifiedImportUsedInJSDoc() {
    testSame("import { Something } from 'somewhere'; /** @type {Something} */ var x;");
  }

  @Test
  public void testReferenceToSingleNameWithRequire() {
    testSame("goog.require('Foo'); new Foo();");
  }

  @Test
  public void testReferenceToSingleNameWithImport() {
    testSame("import 'Foo'; new Foo();");
  }

  @Test
  public void testReferenceInDefaultParam() {
    testSame("function func( a = new Bar() ){}; func();");
  }

  @Test
  public void testReferenceInDefaultParam_withES6Modules() {
    testSame("export function func( a = new Bar() ){}; func();");
  }

  @Test
  public void testReferenceInDestructuringParam() {
    testSame("var {a = new Bar()} = b;");
  }

  @Test
  public void testReferenceInDestructuringParam_withES6Modules() {
    testSame("export var {a = new Bar()} = b;");
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
}
