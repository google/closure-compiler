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

import static com.google.javascript.jscomp.lint.CheckJSDoc.DISALLOWED_MEMBER_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDoc.MISSING_PARAM_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDoc.MUST_BE_PRIVATE;
import static com.google.javascript.jscomp.lint.CheckJSDoc.OPTIONAL_NAME_NOT_MARKED_OPTIONAL;
import static com.google.javascript.jscomp.lint.CheckJSDoc.OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;

/**
 * Test case for {@link CheckJSDoc}.
 */
public final class CheckJSDocTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckJSDoc(compiler);
  }

  public void testInvalidClassJsdoc() {
    this.setAcceptedLanguage(LanguageMode.ECMASCRIPT6_STRICT);

    testSame(
        LINE_JOINER.join("class Foo {", "  /** @param {number} x */", "  constructor(x) {}", "}"));

    testSame(
        LINE_JOINER.join("class Foo {", "  /** @constructor */", "  constructor() {}", "}"),
        DISALLOWED_MEMBER_JSDOC);

    testSame(
        LINE_JOINER.join("class Foo {", "  /** @interface */", "  constructor() {}", "}"),
        DISALLOWED_MEMBER_JSDOC);

    testSame(
        LINE_JOINER.join("class Foo {", "  /** @extends {Foo} */", "  constructor() {}", "}"),
        DISALLOWED_MEMBER_JSDOC);

    testSame(
        LINE_JOINER.join("class Foo {", "  /** @implements {Foo} */", "  constructor() {}", "}"),
        DISALLOWED_MEMBER_JSDOC);
  }

  public void testMissingParam() {
    testSame(
        LINE_JOINER.join(
            "/**",
            " * @param {string} x",
            // No @param for y.
            " */",
            "function f(x, y) {}"),
        MISSING_PARAM_JSDOC);

    testSame(
        LINE_JOINER.join(
            "/**", " * @param {string} x", " * @param {string} y", " */", "function f(x, y) {}"));

    testSame(LINE_JOINER.join("/** @override */", "Foo.bar = function(x, y) {}"));
  }

  public void testMissingPrivate() {
    testSame(
        LINE_JOINER.join(
            "/**", " * @return {number}", " */", "X.prototype.foo_ = function() { return 0; }"),
        MUST_BE_PRIVATE);

    testSame(
        LINE_JOINER.join(
            "/**",
            " * @return {number}",
            " * @private",
            " */",
            "X.prototype.foo_ = function() { return 0; }"));
  }

  public void testOptionalArgs() {
    testSame(
        LINE_JOINER.join("/**", " * @param {number=} n", " */", "function f(n) {}"),
        OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME);

    testSame(
        LINE_JOINER.join("/**", " * @param {number} opt_n", " */", "function f(opt_n) {}"),
        OPTIONAL_NAME_NOT_MARKED_OPTIONAL);

    testSame(LINE_JOINER.join("/**", " * @param {number=} opt_n", " */", "function f(opt_n) {}"));
  }
}
