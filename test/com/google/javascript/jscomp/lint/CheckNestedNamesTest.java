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
  private static final String CLOSURE_EXTERNS =
      new TestExternsBuilder().addClosureExterns().build();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableCreateModuleMap();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNestedNames(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testNoWarningOnNonStaticPropertyAssignment() {
    noWarning("goog.module('a'); class C {}; C.prototype.E = class {};");
    noWarning("goog.module('a'); /** @interface */ C = class {}; C.prototype.E = class {};");

    disableTypeCheck(); // type checking reports inexistent .prototype access on enum, typedef
    noWarning("goog.module('a'); /** @enum */ C = {}; C.prototype.E = class {};");
    noWarning("goog.module('a'); /** @typedef {?} */ let T; T.prototype.E = class {};");
  }

  @Test
  public void testNoWarningInScriptScope() {
    noWarning("class C {}; C.E = class {};");
    noWarning("/** @enum */ let C = {}; C.E = class {};");
    noWarning("/** @interface */ let C = function() {}; C.E = class {};");
    noWarning("/** @typedef {?} */ const C = {}; C.E = class {};");
  }

  @Test
  public void testNoWarningInFunctionScope() {
    noWarning("goog.module('a'); function foo() { class C {}; /** @enum */ C.E = {};} foo();");
    noWarning("goog.module('a'); function foo() { let C = {}; /** @enum */ C.E = {};} foo();");
    noWarning(
        lines(
            "goog.module('a');",
            "/** @param {!Function} x */",
            "function foo(x) {",
            "  /** @enum */ x.E = {};",
            "}",
            "function y() {}",
            "foo(y);"));
  }

  @Test
  public void testNestedClass() {
    nestedNameWarning("goog.module('a'); class C {}; C.C = class {};");

    nestedNameWarning("goog.module('a'); let obj = {}; obj.C = class {};");
    nestedNameWarning("goog.module('a'); function F() {}; F.C = class {};");
    nestedNameWarning("goog.module('a'); let F = function() {}; F.E = class {};");
    nestedNameWarning("goog.module('a'); let F = function() {}; /** something */ F.E = class {};");
  }

  @Test
  public void testNestedNames_noRHS() {
    noWarning("goog.module('a'); class C {}; C.E;");

    disableTypeCheck();
    // interfaces and enums that do not have rhs get reported in type checking; don't report here.
    noWarning("goog.module('a'); class C {}; /** @interface */ C.E;");
    noWarning("goog.module('a'); class C {}; /** @enum {string} */ C.E;");

    enableTypeCheck();
    nestedNameWarning("goog.module('a'); class C {}; /** @typedef {{a:number}} */ C.T;");
  }

  @Test
  public void testNestedEnum() {
    nestedNameWarning("goog.module('a'); let obj = {}; /** @enum */ obj.E = {};");
    nestedNameWarning("goog.module('a'); class C {}; /** @enum */ C.E = {};");
    nestedNameWarning("goog.module('a'); function F() {}; /** @enum */ F.E = {};");
    nestedNameWarning("goog.module('a'); let F = function() {}; /** @enum */ F.E = {};");
  }

  @Test
  public void testNestedInterfaces() {
    nestedNameWarning("goog.module('a'); let obj = {}; /** @interface */ obj.I = class {};");
    nestedNameWarning("goog.module('a'); class C {}; /** @interface */ C.I = class {};");
    nestedNameWarning("goog.module('a'); function F() {}; /** @interface */ F.I = class {};");
    nestedNameWarning("goog.module('a'); let F = function() {}; /** @interface */ F.I = class {};");
  }

  @Test
  public void testNoAnnotations() {
    // tests `X.Y = ...` where `X.Y` is missing the @typedef/@interface/@enum annotation
    nestedNameWarning("goog.module('a'); class C {}; class D {}; /** @const */ C.C = D;");
    nestedNameWarning("goog.module('a'); class C {}; C.C = class {};");

    nestedNameWarning(
        "goog.module('a'); class C {}; /** @constructor */ function D() {}; /** @const */ C.C ="
            + " D;");
    noWarning("goog.module('a'); class C {}; function D() {}; /** @const */ C.C = D;");

    nestedNameWarning("goog.module('a'); class C {}; /** @enum */ E = {}; C.E = E;");
    nestedNameWarning("goog.module('a'); class C {}; /** @interface */ I = class {}; C.I = I;");

    // For the lhs to be a valid typedef, it must be annotated with `@const`.
    noWarning("goog.module('a'); class C {}; /** @typedef {number} */ let T; C.T = T;");
    // The `@const` makes JSCompiler infer the lhs as a typedef, and hence we warn on it.
    nestedNameWarning(
        "goog.module('a'); class C {}; /** @typedef {number} */ let T; /** @const */ C.T = T;");
  }

  @Test
  public void testNestingOnExternNames() {
    testWarning(
        externs(new TestExternsBuilder().addClosureExterns().addArray().build()),
        srcs("goog.module('a');  Array.C = class {};"),
        warning(NESTED_NAME_IN_GOOG_MODULE));
  }

  @Test
  public void testNestingOnGlobalNames() {
    testWarning(
        externs(
            "let GlobalFn = function() {};" + new TestExternsBuilder().addClosureExterns().build()),
        srcs("goog.module('a');  GlobalFn.C = class {};"),
        warning(NESTED_NAME_IN_GOOG_MODULE));
  }

  @Test
  public void testErrorMessageText() {
    testWarning(
        externs(new TestExternsBuilder().addClosureExterns().build()),
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
    noWarning("goog.module('a'); class C {}; C.E = '';");
    noWarning("goog.module('a'); class SomeClass {} class C {}; C.E = new SomeClass();");
    noWarning("goog.module('a'); /** @typedef {?} */ let C; C.E = {};");
    noWarning("goog.module('a'); /** @interface {?} */ let C = class {}; C.E = {};");
    noWarning("goog.module('a'); let C = {}; C.E = {};");
  }

  @Test
  public void testNoWarningOnNamedExports() {
    noWarning(
        "goog.module('a'); /** @enum {string} */ exports.SomeEnum = { ENUM_CONSTANT: 'value' };");
  }

  private void noWarning(String js) {
    testNoWarning(externs(CLOSURE_EXTERNS), srcs(js));
  }

  private void nestedNameWarning(String js) {
    testWarning(externs(CLOSURE_EXTERNS), srcs(js), warning(NESTED_NAME_IN_GOOG_MODULE));
  }
}
