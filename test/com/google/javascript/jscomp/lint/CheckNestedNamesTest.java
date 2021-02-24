/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckNestedNames.NESTED_NAME_IN_GOOG_MODULE;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckNestedNames}. */
@RunWith(JUnit4.class)
public final class CheckNestedNamesTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNestedNames(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testNoWarningOnNonStaticPropertyAssignment() {
    testNoWarning("goog.module('a'); class C {}; C.prototype.E = class {};");
    testNoWarning("goog.module('a'); /** @enum */ C = {}; C.prototype.E = class {};");
    testNoWarning("goog.module('a'); /** @interface */ C = class {}; C.prototype.E = class {};");
    testNoWarning("goog.module('a'); /** @typedef {?} */ const C = {}; C.prototype.E = class {};");
  }

  @Test
  public void testNoWarningInScriptScope() {
    testNoWarning("class C {}; C.E = class {};");
    testNoWarning("/** @enum */ let C = {}; C.E = class {};");
    testNoWarning("/** @interface */ let C = {}; C.E = class {};");
    testNoWarning("/** @typedef {?} */ const C = {};; C.E = class {};");
  }

  @Test
  public void testNoWarningInFunctionScope() {
    testNoWarning("goog.module('a'); function foo() { class C {}; /** @enum */ C.E = {};} foo();");
    testNoWarning("goog.module('a'); function foo() { let C = {}; /** @enum */ C.E = {};} foo();");
    testNoWarning(
        lines(
            "goog.module('a');",
            "/** @param {!Function} x */",
            "function foo(x) {",
            "  /** @enum */ x.E = {};",
            "}",
            "foo();"));
  }

  @Test
  public void testNestedClass() {
    testWarning("goog.module('a'); class C {}; C.C = class {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning("goog.module('a'); let obj = {}; obj.C = class {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning("goog.module('a'); function F() {}; F.C = class {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); let F = function() {}; F.E = class {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); let F = function() {}; /** something */ F.E = class {};",
        NESTED_NAME_IN_GOOG_MODULE);
  }

  @Test
  public void testNestedNames_noRHS() {
    testNoWarning("goog.module('a'); class C {}; C.E;");
    testWarning("goog.module('a'); class C {}; /** @interface */ C.E;", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); class C {}; /** @enum {string} */ C.E;", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); class C {}; /** @typedef {{a:2}} */ C.T;", NESTED_NAME_IN_GOOG_MODULE);
  }

  @Test
  public void testNestedEnum() {
    testWarning(
        "goog.module('a'); let obj = {}; /** @enum */ obj.E = {};",
        CheckNestedNames.NESTED_NAME_IN_GOOG_MODULE);
    testWarning("goog.module('a'); class C {}; /** @enum */ C.E = {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); function F() {}; /** @enum */ F.E = {};", NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); let F = function() {}; /** @enum */ F.E = {};",
        NESTED_NAME_IN_GOOG_MODULE);
  }

  @Test
  public void testNestedInterfaces() {
    testWarning(
        "goog.module('a'); let obj = {}; /** @interface */ obj.I = class {};",
        NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); class C {}; /** @interface */ C.I = class {};",
        NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); function F() {}; /** @interface */ F.I = class {};",
        NESTED_NAME_IN_GOOG_MODULE);
    testWarning(
        "goog.module('a'); let F = function() {}; /** @interface */ F.I = class {};",
        NESTED_NAME_IN_GOOG_MODULE);
  }

  @Test
  public void testNestingOnExternNames() {
    testWarning(
        externs(new TestExternsBuilder().addArray().build()),
        srcs("goog.module('a');  Array.C = class {};"),
        warning(NESTED_NAME_IN_GOOG_MODULE));
  }

  @Test
  public void testNestingOnGlobalNames() {
    testWarning(
        srcs("let GlobalFn = function() {}", "goog.module('a');  GlobalFn.C = class {};"),
        warning(NESTED_NAME_IN_GOOG_MODULE));
  }

  @Test
  public void testErrorMessageText() {
    testWarning(
        srcs("goog.module('a'); class C {}; C.E = class {};"),
        warning(NESTED_NAME_IN_GOOG_MODULE)
            .withMessageContaining(
                "A nested class is created on the name `C`."
                    + " Fix this linter finding by converting the module-level static property"
                    + " assignment on `C` into a module-level flat name (i.e. change `C.prop ="
                    + " ...` into `C_prop = ...`. You can (if required) export this flat name"
                    + " using named exports (`exports.C_prop = C_prop`)."
                ));
  }

  @Test
  public void testNoWarningOnNestedValues() {
    testNoWarning("goog.module('a'); class C {}; C.E = '';");
    testNoWarning("goog.module('a'); class SomeClass {} class C {}; C.E = new SomeClass();");
    testNoWarning("goog.module('a'); /** @typedef {?} */ let C; C.E = {};");
    testNoWarning("goog.module('a'); /** @interface {?} */ let C; C.E = {};");
    testNoWarning("goog.module('a'); let C = {}; C.E = {};");
  }

  @Test
  public void testNoWarningOnNamedExports() {
    testNoWarning("goog.module('a'); /** @enum */ exports.SomeEnum = { ENUM_CONSTANT: 'value' };");
  }
}
