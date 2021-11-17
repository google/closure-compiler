/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckTypeImportCodeReferences.TYPE_IMPORT_CODE_REFERENCE;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckTypeImportCodeReferences}. */
@RunWith(JUnit4.class)
public final class CheckTypeImportCodeReferencesTest extends CompilerTestCase {
  public CheckTypeImportCodeReferencesTest() {
    super();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckTypeImportCodeReferences(compiler);
  }

  @Test
  public void testRequireTypeCodeReference_namespace() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "/** @type {namespace} */ let x;",
                "namespace;")));
  }

  @Test
  public void testRequireTypeCodeReference_alias() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "/** @type {alias} */ let x;")));

    test(
        srcs(
            lines(
                "goog.module('test');", //
                "const alias = goog.requireType('namespace');",
                "alias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));

    test(
        srcs(
            lines(
                "goog.module('test');", //
                "let alias = goog.requireType('namespace');",
                "alias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));

    test(
        srcs(
            lines(
                "goog.module('test');", //
                "var alias = goog.requireType('namespace');",
                "alias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_destructuring() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const {alias} = goog.requireType('namespace');",
                "/** @type {alias} */ let x;")));

    test(
        srcs(
            lines(
                "goog.module('test');", //
                "const {alias} = goog.requireType('namespace');",
                "alias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_destructuring_shortname() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const {alias: myAlias} = goog.requireType('namespace');",
                "alias;")));

    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const {alias: myAlias} = goog.requireType('namespace');",
                "/** @type {myAlias} */ let x;")));

    test(
        srcs(
            lines(
                "goog.module('test');",
                "const {alias: myAlias} = goog.requireType('namespace');",
                "myAlias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_destructuring_multiple() {
    test(
        srcs(
            lines(
                "goog.module('test');",
                "const {otherAlias, alias: myAlias, anotherAlias} = goog.requireType('namespace');",
                "myAlias;")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_shadowing() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "function foo(alias) { alias; }")));

    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "function foo() { let alias; }")));

    test(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "function foo() { alias; }")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_getprop_left() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "/** @type {alias.prop} */ let x;")));

    test(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "alias.prop;")),
        error(TYPE_IMPORT_CODE_REFERENCE));
  }

  @Test
  public void testRequireTypeCodeReference_getprop_right() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "obj.alias;")));
  }

  @Test
  public void testRequireTypeCodeReference_implicit_var() {
    testSame(
        srcs(
            lines(
                "goog.module('test');",
                "const alias = goog.requireType('namespace');",
                "function f() { arguments; }")));
  }
}
