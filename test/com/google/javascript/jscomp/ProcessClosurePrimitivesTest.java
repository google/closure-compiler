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
import static com.google.javascript.jscomp.ProcessClosurePrimitives.EXPECTED_OBJECTLIT_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_CSS_RENAMING_MAP;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_RENAME_FUNCTION;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_STYLE_ERROR;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ProcessClosurePrimitives}.
 *
 */
@RunWith(JUnit4.class)
public final class ProcessClosurePrimitivesTest extends CompilerTestCase {

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();

    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ProcessClosurePrimitives(compiler);
  }

  @Test
  public void testAddDependency() {
    test("goog.addDependency('x.js', ['A', 'B'], []);", "0");

    Compiler compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A")).isFalse();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("B")).isFalse();
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

    testSame("goog.module('mod'); goog.forwardDeclare('A.B');");

    compiler = getLastCompiler();
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A.B")).isTrue();

    testSame(
        srcs("goog.provide('A.B');", "goog.module('mod'); const B = goog.forwardDeclare('A.B');"));

    compiler = getLastCompiler();
    // This is a valid forward declaration, but for historical reasons, does not actually forward
    // declare the type 'A.B'.
    assertThat(compiler.getTypeRegistry().isForwardDeclaredType("A.B")).isFalse();
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
  public void testValidBase_exportsAssignmentsBeforeGoogInherits() {
    test(
        lines(
            "goog.module('my.Foo');",
            "class Bar {}",
            "function Foo() { Foo.base(this, 'constructor', 1, 2); }",
            "exports.Foo = Foo;",
            "exports.Bar = Bar;",
            FOO_INHERITS),
        lines(
            "goog.module('my.Foo');",
            "class Bar {}",
            "function Foo() { BaseFoo.call(this, 1, 2); }",
            "exports.Foo = Foo;",
            "exports.Bar = Bar;",
            FOO_INHERITS));
  }

  @Test
  public void testInvalidBase_nonAliasLinesBeforeGoogInherits() {
    testSame(
        lines(
            "goog.module('my.Foo');",
            "function Foo() { Foo.base(this, 'constructor', 1, 2); }",
            "alert(0);",
            "alert(1);",
            "alert(2);",
            FOO_INHERITS));
  }

  @Test
  public void testValidPrimitiveCalls() {
    testNoWarning(
        lines(
            "goog.module('c');", //
            "goog.forwardDeclare('A.b');"));
    testNoWarning(
        lines(
            "goog.module('d');", //
            "goog.addDependency('C.D');"));
  }

  @Test
  public void testOtherBaseCall() {
    testSame("class Foo extends BaseFoo { method() { baz.base('arg'); } }");
  }

  @Test
  public void testRenameFunction_withOneStringLit_isOk() {
    testSame("const p = JSCompiler_renameProperty('a')");
  }

  @Test
  public void testRenameFunction_withOneStringLit_andAnotherArg_isOk() {
    testSame("const p = JSCompiler_renameProperty('a', 0)");
  }

  @Test
  public void testRenameFunction_withZeroArgs_isReported() {
    test(
        srcs("const p = JSCompiler_renameProperty()"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("1 or 2 arguments"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("string literal"));
  }

  @Test
  public void testRenameFunction_withThreeArgs_isReported() {
    test(
        srcs("const p = JSCompiler_renameProperty(1, 2, 3)"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("1 or 2 arguments"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("string literal"));
  }

  @Test
  public void testRenameFunction_withNonStringArg_isReported() {
    test(
        srcs("const p = JSCompiler_renameProperty(0)"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("string literal"));
  }

  @Test
  public void testInvalidRenameFunction_withPropertyRefInFirstArg_isReported() {
    test(
        srcs("const p = JSCompiler_renameProperty('a.b')"),
        error(INVALID_RENAME_FUNCTION).withMessageContaining("property path"));
  }
}
