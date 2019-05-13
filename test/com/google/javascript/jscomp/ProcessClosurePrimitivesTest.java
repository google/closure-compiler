/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.CLOSURE_DEFINES_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.EXPECTED_OBJECTLIT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.GOOG_BASE_CLASS_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_CSS_RENAMING_MAP;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_DEFINE_NAME_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_STYLE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.MISSING_DEFINE_ANNOTATION;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.NULL_ARGUMENT_ERROR;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ProcessClosurePrimitives}.
 *
 */

@RunWith(JUnit4.class)
public final class ProcessClosurePrimitivesTest extends CompilerTestCase {
  private boolean banGoogBase;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    banGoogBase = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();

    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    if (banGoogBase) {
      options.setWarningLevel(
          DiagnosticGroups.USE_OF_GOOG_BASE, CheckLevel.ERROR);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ProcessClosurePrimitives(compiler, /* preprocessorSymbolTable= */ null);
  }

  @Test
  public void testAddDependency() {
    test("goog.addDependency('x.js', ['A', 'B'], []);", "0");

    Compiler compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("B")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("C")).isFalse();
  }

  @Test
  public void testValidSetCssNameMapping() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'});", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");
  }

  @Test
  public void testValidSetCssNameMappingWithType() {
    test("goog.setCssNameMapping({foo:'bar',\"biz\":'baz'}, 'BY_PART');", "");
    CssRenamingMap map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");

    test("goog.setCssNameMapping({foo:'bar',biz:'baz','biz-foo':'baz-bar'}," +
        " 'BY_WHOLE');", "");
    map = getLastCompiler().getCssRenamingMap();
    assertThat(map).isNotNull();
    assertThat(map.get("foo")).isEqualTo("bar");
    assertThat(map.get("biz")).isEqualTo("baz");
    assertThat(map.get("biz-foo")).isEqualTo("baz-bar");
  }

  @Test
  public void testSetCssNameMappingByShortHand() {
    testError("goog.setCssNameMapping({shortHandFirst, shortHandSecond});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testSetCssNameMappingByTemplate() {
    testError("goog.setCssNameMapping({foo: `bar`});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo: `${vari}bar`});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testSetCssNameMappingNonStringValueReturnsError() {
    // Make sure the argument is an object literal.
    testError("var BAR = {foo:'bar'}; goog.setCssNameMapping(BAR);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping([]);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(false);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(null);", EXPECTED_OBJECTLIT_ERROR);
    testError("goog.setCssNameMapping(undefined);", EXPECTED_OBJECTLIT_ERROR);

    // Make sure all values of the object literal are string literals.
    testError("var BAR = 'bar'; goog.setCssNameMapping({foo:BAR});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:6});", NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:false});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:null});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
    testError("goog.setCssNameMapping({foo:undefined});",
        NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
  }

  @Test
  public void testForwardDeclarations() {
    testSame("goog.forwardDeclare('A.B')");

    Compiler compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A.B")).isTrue();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("C.D")).isFalse();
  }

  @Test
  public void testInvalidForwardDeclarations() {
    testError(
        "const B = goog.forwardDeclare('A.B');",
        ProcessClosurePrimitives.CLOSURE_CALL_CANNOT_BE_ALIASED_OUTSIDE_MODULE_ERROR);
    testError("goog.forwardDeclare();", ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);

    testError(
        "goog.forwardDeclare('A.B', 'C.D');", ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);

    testError("goog.forwardDeclare(`template`);", ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);
    testError(
        "goog.forwardDeclare(`${template}Sub`);", ProcessClosurePrimitives.INVALID_FORWARD_DECLARE);
  }

  @Test
  public void testSetCssNameMappingValidity() {
    // Make sure that the keys don't have -'s
    test("goog.setCssNameMapping({'a': 'b', 'a-a': 'c'})", "", warning(INVALID_CSS_RENAMING_MAP));

    // In full mode, we check that map(a-b)=map(a)-map(b)
    test(
        "goog.setCssNameMapping({'a': 'b', 'a-a': 'c'}, 'BY_WHOLE')",
        "",
        warning(INVALID_CSS_RENAMING_MAP));

    // Unknown mapping type
    testError("goog.setCssNameMapping({foo:'bar'}, 'UNKNOWN');",
        INVALID_STYLE_ERROR);
  }

  @Test
  public void testInvalidAddDependency() {
    testError(
        "goog.provide('a.b'); var x = x || goog.addDependency('a.b', ['foo'], ['bar']);",
        CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR);
    testError(
        "goog.provide('a.b'); x = goog.addDependency('a.b', ['foo'], ['bar']);",
        CLOSURE_CALL_CANNOT_BE_ALIASED_ERROR);
    testError(
        "goog.provide('a.b'); function f() { goog.addDependency('a.b', ['foo'], ['bar']); }",
        INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void testValidGoogMethod() {
    testSame("function f() { goog.isDef('a.b'); }");
    testSame("function f() { goog.inherits(a, b); }");
    testSame("function f() { goog.exportSymbol(a, b); }");
    test("function f() { goog.setCssNameMapping({}); }", "function f() {}");
    testSame("x || goog.isDef('a.b');");
    testSame("x || goog.inherits(a, b);");
    testSame("x || goog.exportSymbol(a, b);");
    testSame("x || void 0");
  }

  private static final String METHOD_FORMAT =
      "function Foo() {} Foo.prototype.method = function() { %s };";

  private static final String FOO_INHERITS =
      "goog.inherits(Foo, BaseFoo);";

  @Test
  public void testInvalidGoogBase1() {
    testError("goog.base(this, 'method');", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase2() {
    testError("function Foo() {}" +
         "Foo.method = function() {" +
         "  goog.base(this, 'method');" +
         "};", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase3() {
    testError(String.format(METHOD_FORMAT, "goog.base();"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase4() {
    testError(String.format(METHOD_FORMAT, "goog.base(this, 'bar');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase5() {
    testError(String.format(METHOD_FORMAT, "goog.base('foo', 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase6() {
    testError(String.format(METHOD_FORMAT, "goog.base.call(null, this, 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase6b() {
    testError(String.format(METHOD_FORMAT, "goog.base.call(this, 'method');"),
         GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase7() {
    testError("function Foo() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase8() {
    testError("var Foo = function() { goog.base(this); }", GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.base(this); }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase10() {
    testError("class Foo extends BaseFoo { constructor() { goog.base(this); } }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase11() {
    testError("class Foo extends BaseFoo { someMethod() { goog.base(this, 'someMethod'); } }",
        GOOG_BASE_CLASS_ERROR);
  }

  @Test
  public void testValidGoogBase1() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method');"),
         String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidGoogBase2() {
    test(String.format(METHOD_FORMAT, "goog.base(this, 'method', 1, 2);"),
         String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  @Test
  public void testValidGoogBase3() {
    test(String.format(METHOD_FORMAT, "return goog.base(this, 'method');"),
         String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidGoogBase4() {
    test("function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  @Test
  public void testValidGoogBase5() {
    test("var Foo = function() { goog.base(this, 1); };" + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  @Test
  public void testValidGoogBase6() {
    test("var goog = {}; goog.Foo = function() { goog.base(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  @Test
  public void testBanGoogBase() {
    banGoogBase = true;
    testError(
        "function Foo() { goog.base(this, 1, 2); }" + FOO_INHERITS,
        ProcessClosurePrimitives.USE_OF_GOOG_BASE);
  }

  @Test
  public void testInvalidBase1() {
    testError(
        "var Foo = function() {};" + FOO_INHERITS +
        "Foo.base(this, 'method');", BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase2() {
    testError("function Foo() {}" + FOO_INHERITS +
        "Foo.method = function() {" +
        "  Foo.base(this, 'method');" +
        "};", BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase3() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base();"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase4() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT, "Foo.base(this, 'bar');"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase5() {
    testError(String.format(FOO_INHERITS + METHOD_FORMAT,
        "Foo.base('foo', 'method');"),
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase7() {
    testError("function Foo() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase8() {
    testError("var Foo = function() { Foo.base(this); };" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase9() {
    testError("var goog = {}; goog.Foo = function() { goog.Foo.base(this); };"
        + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase10() {
    testError("function Foo() { Foo.base(this); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase11() {
    testError("function Foo() { Foo.base(this, 'method'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase12() {
    testError("function Foo() { Foo.base(this, 1, 2); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidBase13() {
    testError(
        "function Bar(){ Bar.base(this, 'constructor'); }" +
        "goog.inherits(Bar, Goo);" +
        "function Foo(){ Bar.base(this, 'constructor'); }" + FOO_INHERITS,
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase14() {
    testError("class Foo extends BaseFoo { constructor() { Foo.base(this); } }",
        BASE_CLASS_ERROR);
  }

  @Test
  public void testInvalidGoogBase14b() {
    testError("class Foo extends BaseFoo { method() { Foo.base(this, 'method'); } }",
        BASE_CLASS_ERROR);
  }

  @Test
  public void testValidBase1() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidBase2() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "Foo.base(this, 'method', 1, 2);"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "Foo.superClass_.method.call(this, 1, 2)"));
  }

  @Test
  public void testValidBase3() {
    test(FOO_INHERITS
         + String.format(METHOD_FORMAT, "return Foo.base(this, 'method');"),
         FOO_INHERITS
         + String.format(METHOD_FORMAT,
             "return Foo.superClass_.method.call(this)"));
  }

  @Test
  public void testValidBase4() {
    test("function Foo() { Foo.base(this, 'constructor', 1, 2); }"
         + FOO_INHERITS,
         "function Foo() { BaseFoo.call(this, 1, 2); } " + FOO_INHERITS);
  }

  @Test
  public void testValidBase5() {
    test("var Foo = function() { Foo.base(this, 'constructor', 1); };"
         + FOO_INHERITS,
         "var Foo = function() { BaseFoo.call(this, 1); }; " + FOO_INHERITS);
  }

  @Test
  public void testValidBase6() {
    test("var goog = {}; goog.Foo = function() {" +
         "goog.Foo.base(this, 'constructor'); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);",
         "var goog = {}; goog.Foo = function() { goog.BaseFoo.call(this); }; " +
         "goog.inherits(goog.Foo, goog.BaseFoo);");
  }

  @Test
  public void testValidBase7() {
    // No goog.inherits, so this is probably a different 'base' function.
    testSame(""
        + "var a = function() {"
        + "  a.base(this, 'constructor');"
        + "};");
  }

  @Test
  public void testDefineCases() {
    String jsdoc = "/** @define {number} */\n";
    test(jsdoc + "goog.define('name', 1);", jsdoc + "var name = 1");
    test(jsdoc + "goog.define('ns.name', 1);", jsdoc + "ns.name = 1");
    test(jsdoc + "const x = goog.define('ns.name', 1);", jsdoc + "const x = 1");
  }

  @Test
  public void testDefineErrorCases() {
    String jsdoc = "/** @define {number} */\n";
    testError("goog.define('name', 1);", MISSING_DEFINE_ANNOTATION);
    testError(jsdoc + "goog.define('name.2', 1);", INVALID_DEFINE_NAME_ERROR);
    testError(jsdoc + "goog.define();", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define('value');", NULL_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define(5);", INVALID_ARGUMENT_ERROR);

    testError(jsdoc + "goog.define(`templateName`, 1);", INVALID_ARGUMENT_ERROR);
    testError(jsdoc + "goog.define(`${template}Name`, 1);", INVALID_ARGUMENT_ERROR);
  }

  @Test
  public void testInvalidDefine() {
    testError(
        "goog.provide('a.b'); var x = x || goog.define('goog.DEBUG', true);",
        INVALID_CLOSURE_CALL_SCOPE_ERROR);
    testError(
        "goog.provide('a.b'); function f() { goog.define('goog.DEBUG', true); }",
        INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void testDefineValues() {
    testSame("var CLOSURE_DEFINES = {'FOO': 'string'};");
    testSame("var CLOSURE_DEFINES = {'FOO': true};");
    testSame("var CLOSURE_DEFINES = {'FOO': false};");
    testSame("var CLOSURE_DEFINES = {'FOO': 1};");
    testSame("var CLOSURE_DEFINES = {'FOO': 0xABCD};");
    testSame("var CLOSURE_DEFINES = {'FOO': -1};");
    testSame("let CLOSURE_DEFINES = {'FOO': 'string'};");
    testSame("const CLOSURE_DEFINES = {'FOO': 'string'};");
  }

  @Test
  public void testDefineValuesErrors() {
    testError("var CLOSURE_DEFINES = {'FOO': a};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': 0+1};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': 'value' + 'value'};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': !true};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'FOO': -true};", CLOSURE_DEFINES_ERROR);

    testError("var CLOSURE_DEFINES = {SHORTHAND};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'TEMPLATE': `template`};", CLOSURE_DEFINES_ERROR);
    testError("var CLOSURE_DEFINES = {'TEMPLATE': `${template}Sub`};", CLOSURE_DEFINES_ERROR);
  }

  @Test
  public void testOtherBaseCall() {
    testSame("class Foo extends BaseFoo { method() { baz.base('arg'); } }");
  }
}
