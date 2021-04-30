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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckRequiresSorted.REQUIRES_NOT_SORTED;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckRequiresSorted}.
 *
 * <p>Note that the sort/deduplication logic is extensively tested by {@link ErrorToFixMapperTest}.
 * These tests are only for asserting that warnings are reported correctly.
 */
@RunWith(JUnit4.class)
public final class CheckRequiresSortedTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    CheckRequiresSorted callback =
        new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_AND_REPORT);
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, root, callback);
      }
    };
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
    options.setPreserveDetailedSourceInfo(true);
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    options.setPreferSingleQuotes(true);
    options.setEmitUseStrict(false);
    return options;
  }

  @Test
  public void testNoWarning() {
    testNoWarning(
        lines(
            "goog.module('x');",
            "",
            "const c = goog.require('c');",
            "const {b} = goog.require('b');",
            "goog.require('a');"));
  }

  @Test
  public void testNoWarning_noRequires() {
    testNoWarning(lines("goog.module('x');", "", "alert(1);"));
  }

  @Test
  public void testWarning() {
    test(
        srcs(
            lines(
                "goog.module('x');",
                "",
                "const b = goog.require('b');",
                "const a = goog.require('a')",
                "",
                "alert(1);")),
        warning(REQUIRES_NOT_SORTED)
            .withMessageContaining(
                lines(
                    "The correct order is:",
                    "",
                    "const a = goog.require('a');",
                    "const b = goog.require('b');")));
  }

  @Test
  public void testWarning_withJsDoc() {
    test(
        srcs(
            lines(
                "goog.module('x');",
                "",
                "/**",
                " * @suppress {extraRequire}",
                " */",
                "goog.require('b');",
                "goog.require('a');",
                "",
                "alert(1);")),
        warning(REQUIRES_NOT_SORTED)
            .withMessageContaining(
                lines(
                    "The correct order is:",
                    "",
                    "goog.require('a');",
                    "/**",
                    " * @suppress {extraRequire}",
                    " */",
                    "goog.require('b');")));
  }

  @Test
  public void testWarning_destructuringWithNoShorthandProperties() {
    test(
        srcs(lines("goog.module('x');", "", "const {a: a} = goog.require('a');", "", "alert(1);")),
        warning(REQUIRES_NOT_SORTED)
            .withMessageContaining(
                lines("The correct order is:", "", "const {a} = goog.require('a');")));
  }
}
