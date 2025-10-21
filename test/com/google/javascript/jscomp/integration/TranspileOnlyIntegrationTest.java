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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.javascript.jscomp.AstValidator;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.jspecify.annotations.Nullable;
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

  /**
   * Tests an uncommon case where a module body is not the first child of a script. This may happen
   * in a specific circumstance where the {@code LateEs6ToEs3Rewriter} pass injects code above a
   * module body. Valid only when skipNonTranspilationPasses=true and
   * setWrapGoogModulesForWhitespaceOnly=false
   */
  @Test
  public void testASTValidator_transpileOnly_withModuleNotFirstChildOfScript() {
    // to ensure tagged template literals transpiled
    this.options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    // to preserve modules during transpilation
    this.options.setWrapGoogModulesForWhitespaceOnly(false);
    this.options.setSkipNonTranspilationPasses(true);

    String source =
        """
        goog.module('x');
        function tag(x) {
          console.log(x);
        }
         tag``
        """;

    String expected =
        """
        var $jscomp$templatelit$98447280$0=$jscomp.createTemplateTagFirstArg([""]);\
        goog.module("x");\
        function tag(x){\
        console.log(x)\
        }\
        tag($jscomp$templatelit$98447280$0)\
        """;

    Compiler compiler = compile(options, source);

    // Verify that there are no compiler errors
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.toSource(compiler.getRoot().getLastChild())).isEqualTo(expected);
    // Create an astValidator and validate the script
    AstValidator astValidator = new AstValidator(compiler);
    checkState(compiler.getRoot().getLastChild().getOnlyChild().isScript());
    astValidator.validateScript(compiler.getRoot().getLastChild().getOnlyChild());

    // In regular (non transpile-only) compilation this is reported
    compiler.getOptions().setSkipNonTranspilationPasses(false);
    assertThrows(
        IllegalStateException.class,
        () -> astValidator.validateScript(compiler.getRoot().getLastChild().getOnlyChild()));
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

  // Added when moving Es6ExtractClasses before RewriteClassMembers (now both merged into
  // Es6NormalizeClasses) and fixing an issue with not rewriting extends
  @Test
  public void testClassExtendsAnonymousClass() {
    options.setLanguage(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        """
        class Bar {}
        class Foo extends (class extends Bar {}) {
          static x;
        }
        """,
        """
        var Bar = function() {};
        var $jscomp$classExtends$98447280$0 = function() {
          Bar.apply(this, arguments)
        };
        $jscomp.inherits($jscomp$classExtends$98447280$0, Bar);
        var Foo = function() {
          $jscomp$classExtends$98447280$0.apply(this, arguments)
        };
        $jscomp.inherits(Foo, $jscomp$classExtends$98447280$0);
        Foo.$jscomp$staticInit$98447280$1 = function() {
          Foo.x
        };
        Foo.$jscomp$staticInit$98447280$1()
        """);
  }
}
