/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.integration;

import static com.google.javascript.jscomp.base.JSCompStrings.lines;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for transpile-only mode.
 *
 * <p>This class actually tests several transpilation passes together.
 */
@RunWith(JUnit4.class)
public final class TranspileOnlyIntegrationTest extends IntegrationTestCase {

  private @Nullable CompilerOptions options = null;

  @Before
  public void init() {
    options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    options.setSkipNonTranspilationPasses(true);
    options.setEmitUseStrict(false);
  }

  @Test
  public void esModuleNoTranspilationForSameLanguageLevel() {
    String js = "export default function fn(){};";
    test(options, js, js);
  }

  @Test
  public void esModuleTranspilationForDifferentLanguageLevel() {
    String js = "export default function fn() {}";
    String transpiled =
        "function fn$$module$i0(){}var module$i0={};module$i0.default=fn$$module$i0;";
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    test(options, js, transpiled);
  }

  // Added when moving Es6ExtractClasses before RewriteClassMembers and fixing an issue with
  // not rewriting extends
  @Test
  public void testClassExtendsAnonymousClass() {
    options.setLanguage(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        lines(
            "class Bar {}", //
            "class Foo extends (class extends Bar {}) {",
            "  static x;",
            "}"),
        lines(
            "var Bar = function() {};",
            "var i0$classextends$var0 = function() {",
            "  Bar.apply(this, arguments)",
            "};",
            "$jscomp.inherits(i0$classextends$var0, Bar);",
            "var Foo = function() {",
            "  i0$classextends$var0.apply(this, arguments)",
            "};",
            "$jscomp.inherits(Foo, i0$classextends$var0);",
            "Foo.x;"));
  }
}
