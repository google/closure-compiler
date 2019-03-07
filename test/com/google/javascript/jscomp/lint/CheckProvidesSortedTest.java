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

import static com.google.javascript.jscomp.lint.CheckProvidesSorted.PROVIDES_NOT_SORTED;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckProvidesSorted}.
 *
 * <p>Note that the sort/deduplication logic is extensively tested by {@link ErrorToFixMapperTest}.
 * These tests are only for asserting that warnings are reported correctly.
 */
@RunWith(JUnit4.class)
public final class CheckProvidesSortedTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    CheckProvidesSorted callback =
        new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_AND_REPORT);
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
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
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
            "/** @fileoverview foo */",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('c');",
            "",
            "alert(1);"));
  }

  @Test
  public void testNoWarning_noProvides() {
    testNoWarning(
        lines("/** @fileoverview foo */", "", "goog.module('m');", "", "goog.require('x');"));
  }

  @Test
  public void testWarning() {
    test(
        srcs(
            lines(
                "/** @fileoverview foo */",
                "",
                "goog.provide('b');",
                "goog.provide('a');",
                "goog.provide('c');",
                "",
                "alert(1);")),
        warning(PROVIDES_NOT_SORTED)
            .withMessageContaining(
                lines(
                    "The correct order is:",
                    "",
                    "goog.provide('a');",
                    "goog.provide('b');",
                    "goog.provide('c');")));
  }
}
