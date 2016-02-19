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

import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.PROVIDES_AFTER_REQUIRES;
import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.PROVIDES_NOT_SORTED;
import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.REQUIRES_NOT_SORTED;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

public final class CheckRequiresAndProvidesSortedTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresAndProvidesSorted(compiler);
  }

  public void testNoWarning_require() {
    testSame("goog.require('a.b');\ngoog.require('a.c')");
    testSame(
        LINE_JOINER.join(
            "goog.require('namespace');",
            "goog.require('namespace.ExampleClass');",
            "goog.require('namespace.ExampleClass.ExampleInnerClass');"));
    testSame(
        LINE_JOINER.join(
            "goog.require('namespace.Example');",
            "goog.require('namespace.example');"));
  }

  public void testNoWarning_provide() {
    testSame("goog.provide('a.b');\ngoog.provide('a.c')");
    testSame(
        LINE_JOINER.join(
            "goog.provide('namespace');",
            "goog.provide('namespace.ExampleClass');",
            "goog.provide('namespace.ExampleClass.ExampleInnerClass');"));
    testSame(
        LINE_JOINER.join(
            "goog.provide('namespace.Example');",
            "goog.provide('namespace.example');"));
  }

  public void testWarning_require() {
    testWarning("goog.require('a.c');\ngoog.require('a.b')", REQUIRES_NOT_SORTED);
    testWarning("goog.require('a.c');\ngoog.require('a')", REQUIRES_NOT_SORTED);
  }

  public void testWarning_provide() {
    testWarning("goog.provide('a.c');\ngoog.provide('a.b')", PROVIDES_NOT_SORTED);
    testWarning("goog.provide('a.c');\ngoog.provide('a')", PROVIDES_NOT_SORTED);
  }

  public void testWarning_requiresFirst() {
    testWarning("goog.require('a');\ngoog.provide('b')", PROVIDES_AFTER_REQUIRES);
  }

  public void testB3473189() {
    testSame(
        LINE_JOINER.join(
            "goog.provide('abc');",
            "if (typeof goog != 'undefined' && typeof goog.provide == 'function') {",
            "  goog.provide('MyLib.Base');",
            "}"));
  }

  /**
   * If a goog.module uses the "var x = goog.require('x')" form, don't warn.
   */
  public void testGoogModuleWithShorthand() {
    testSame(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "var d = goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);"));

    testSame(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var d = goog.require('a.b.d');",
            "var c = goog.require('a.c');",
            "",
            "alert(1);"));
  }

  public void testGoogModuleWithDestructuring() {
    testSameEs6(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('z');",
            "goog.require('a');",
            "var {someFunction, anotherFunction} = goog.require('example.utils');",
            "var {A_CONST, ANOTHER_CONST} = goog.require('example.constants');",
            "",
            "alert(1);"));
  }
}
