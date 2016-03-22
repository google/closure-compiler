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
import static com.google.javascript.jscomp.CheckRequiresForConstructors.MISSING_REQUIRE_WARNING;

/**
 * Tests for {@link CheckRequiresForConstructors} in single-file mode.
 */
public final class SingleFileCheckRequiresTest extends Es6CompilerTestCase {

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    return super.getOptions(options);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckRequiresForConstructors(compiler,
        CheckRequiresForConstructors.Mode.SINGLE_FILE);
  }

  public void testReferenceToSingleName() {
    testSame("new Foo();");
    testSame("new Array();");
    testSame("new Error();");
  }

  public void testCtorExtendsSingleName() {
    testSame("/** @constructor @extends {Foo} */ function MyFoo() {}");
    testSame("/** @constructor @extends {Error} */ function MyError() {}");
    testSame("/** @constructor @extends {Array} */ function MyArray() {}");
  }

  public void testClassExtendsSingleName() {
    testSameEs6("class MyFoo extends Foo {}");
    testSameEs6("class MyError extends Error {}");
    testSameEs6("class MyArray extends Array {}");
  }

  public void testReferenceToQualifiedName() {
    testError(
        LINE_JOINER.join(
            "goog.require('x.y.z');",
            "goog.require('bar.Abc');",
            "new x.y.z();",
            "new bar.Abc();",
            "new bar.Foo();"),
        MISSING_REQUIRE_WARNING);
  }

  // Since there are no goog.require()s for any bar.* names, assume that bar
  // is a "non-Closurized" namespace, i.e. that all bar.* names come from the externs.
  public void testReferenceToQualifiedName_nonClosurizedNamespace() {
    testSame(
        LINE_JOINER.join(
            "goog.require('x.y.z');",
            "new x.y.z();",
            "new bar.Foo();"));
  }

  public void testReferenceToUnqualifiedName() {
    testSameEs6(
        LINE_JOINER.join(
            "goog.module('a.b.c');",
            "var z = goog.require('x.y.z');",
            "",
            "exports = { foobar : z };"));

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('a.b.c');",
            "var {z} = goog.require('x.y');",
            "",
            "exports = { foobar : z };"));

    testSameEs6(
        LINE_JOINER.join(
            "import {z} from 'x.y'",
            "",
            "export var foobar = z;"));

    testSameEs6(
        LINE_JOINER.join(
            "import z from 'x.y.z'",
            "",
            "export var foobar = z;"));
  }

  public void testExtraRequire() {
    testErrorEs6("goog.require('foo.Bar');", EXTRA_REQUIRE_WARNING);
  }

  public void testUnqualifiedRequireUsedInJSDoc() {
    testSameEs6("goog.require('Bar'); /** @type {Bar} */ var x;");
  }

  public void testUnqualifiedImportUsedInJSDoc() {
    testSameEs6("import { Something } from 'somewhere'; /** @type {Something} */ var x;");
  }

  public void testReferenceToSingleNameWithRequire() {
    testSameEs6("goog.require('Foo'); new Foo();");
  }

  public void testReferenceInDefaultParam() {
    testSameEs6("function func( a = new Bar() ){}; func();");
  }

  public void testReferenceInDestructuringParam() {
    testSameEs6("var {a = new Bar()} = b;");
  }
}
