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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckEnums.COMPUTED_PROP_NAME_IN_ENUM;
import static com.google.javascript.jscomp.lint.CheckEnums.DUPLICATE_ENUM_VALUE;
import static com.google.javascript.jscomp.lint.CheckEnums.ENUM_PROP_NOT_CONSTANT;
import static com.google.javascript.jscomp.lint.CheckEnums.ENUM_TYPE_NOT_STRING_OR_NUMBER;
import static com.google.javascript.jscomp.lint.CheckEnums.NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM;
import static com.google.javascript.jscomp.lint.CheckEnums.SHORTHAND_ASSIGNMENT_IN_ENUM;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckEnums}. */
@RunWith(JUnit4.class)
public final class CheckEnumsTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckEnums(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testCheckEnums() {
    testSame("/** @enum {number} */ ns.Enum = {A: 1, B: 2};");
    testSame("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'bar'};");
    testWarning("/** @enum {number} */ ns.Enum = {A: 1, B: 1};", DUPLICATE_ENUM_VALUE);
    testWarning("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'foo'};", DUPLICATE_ENUM_VALUE);

    testSame("/** @enum {number} */ let Enum = {A: 1, B: 2};");
    testSame("/** @enum {string} */ let Enum = {A: 'foo', B: 'bar'};");
    testWarning("/** @enum {number} */ let Enum = {A: 1, B: 1};", DUPLICATE_ENUM_VALUE);
    testNoWarning("/** @enum {number} */ let Enum = {A: 1+1, B: 1+1};"); // uncaught dup values

    testWarning("/** @enum {string} */ let Enum = {A: 'foo', B: 'foo'};", DUPLICATE_ENUM_VALUE);
    testNoWarning(
        "/** @enum {number} */ let Enum = {A: 'a'+'b', B: 'a'+'b'};"); // uncaught dup values

    testWarning("/** @enum {number} */ let Enum = {A};", SHORTHAND_ASSIGNMENT_IN_ENUM);
    testWarning(
        "/** @enum {string} */ let Enum = {['prop' + f()]: 'foo'};", COMPUTED_PROP_NAME_IN_ENUM);

    testWarning("/** @enum {number} */ let E = { a: 1 };", ENUM_PROP_NOT_CONSTANT);
    testWarning("/** @enum {number} */ let E = { ABC: 1, abc: 2 };", ENUM_PROP_NOT_CONSTANT);
  }

  @Test
  public void testCheckValidEnums_withES6Modules() {
    testSame("export /** @enum {number} */ let Enum = {A: 1, B: 2};");
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules01() {
    testWarning("export /** @enum {number} */ let Enum = {A: 1, B: 1};", DUPLICATE_ENUM_VALUE);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules02() {
    testWarning("export /** @enum {number} */ let Enum = {A};", SHORTHAND_ASSIGNMENT_IN_ENUM);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules03() {
    testWarning(
        "export /** @enum {string} */ let Enum = {['prop' + f()]: 'foo'};",
        COMPUTED_PROP_NAME_IN_ENUM);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules04() {
    testWarning("export /** @enum {number} */ let E = { a: 1 };", ENUM_PROP_NOT_CONSTANT);
  }

  @Test
  public void testEnumTypeIsDeclaredStringOrNumber() {
    testNoWarning("/** @enum {number} */ let E = { A: 1 };");
    testNoWarning("/** @enum {number} */ let E = { A: 1+1 };");
    testNoWarning("/** @enum {string} */ let E = { A: 'static str' };");
    testNoWarning("/** @enum {string} */ let E = { A: `template lit` };");

    testWarning("/** @enum {?number} */ let E = { A: 1 };", ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        "/** @enum {!string} */ let E = { A: 'static str' };", ENUM_TYPE_NOT_STRING_OR_NUMBER);

    testWarning("/** @enum {boolean} */ let E = {A: true };", ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        "function foo() {} /** @enum {Function} */ let E = {A: foo };",
        ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        "function foo() {} /** @enum {?} */ let E = {A: foo() };", ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        "/** @typedef {number} */ var someName; /** @enum {!someName} */ const"
            + " EnumTwo = { FOO: EnumOne.FOO}",
        ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        "/** @typedef {string} */ var someName; /** @enum {!someName} */ const"
            + " EnumTwo = { FOO: 'some'}",
        ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        lines(
            "/** @enum {number} */",
            "let EnumOne = {  FOO: 1, BAR: 2};",
            "/** @enum {!EnumOne}*/",
            "let EnumTwo = { FOO: EnumOne.FOO}"),
        ENUM_TYPE_NOT_STRING_OR_NUMBER);
    testWarning(
        lines(
            "/** @enum {string} */",
            "let EnumOne = {  FOO: 'foo',  BAR: 'bar'};",
            "/** @enum {!EnumOne}*/",
            "let EnumTwo = { FOO: EnumOne.FOO}"),
        ENUM_TYPE_NOT_STRING_OR_NUMBER);

    testWarning(" /** @enum {{x: number}} */ var E = {A: true };", ENUM_TYPE_NOT_STRING_OR_NUMBER);
  }

  @Test
  public void testNonStaticNumber_noWarning() {
    // We do not warn on number enum values that are non-statically initialized.
    testNoWarning("let fooVal = 1; /** @enum {number} */ let E = {A: fooVal };");
    testNoWarning("let obj = {fooVal : 2}; /** @enum {number} */ let E = {A: obj.fooVal };");
    testNoWarning("let obj = {fooVal : 2}; /** @enum {number} */ let E = {A: obj.fooVal + 1 };");
    testNoWarning(
        "let obj = {fooVal : 2}; /** @enum {number} */ let E = {A: obj.fooVal - obj.fooVal};");
    testNoWarning("function foo() {return 3;} /** @enum {number} */ let E = { A: foo() };");
  }

  @Test
  public void testDefaultEnums_noWarning() {
    // Default enums are of number type; we must not warn when non-statically initialized
    testNoWarning("let fooVal = 1; /** @enum */ let E = {A: fooVal };");
    testNoWarning("let obj = {fooVal : 2}; /** @enum */ let E = {A: obj.fooVal };");
    testNoWarning("function foo() {return 3;} /** @enum */ let E = { A: foo() };");
  }

  @Test
  public void testStringEnums_withArithmeticOperations() {
    // String enum values computed using arithmetic get reported
    testWarning(
        "/** @enum {string} */ let E = { A: 'a'+ 2};",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // 'a2'
    testWarning(
        "/** @enum {string} */ let E = { A: 'a'+ 'b' };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // 'ab'
  }

  @Test
  public void testNonStaticStringValue_reportsWarning() {
    testWarning(
        "let fooVal = ''; /** @enum {string} */ let E = {A: fooVal };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with name

    testWarning(
        "/** @return {string} */ function foo() {return ''} /** @enum {string} */ let E = { A:"
            + " foo() };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with call

    // call to a function missing `@return` annotation
    testWarning(
        "function foo() {return ''} /** @enum {string} */ let E = { A: foo() };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM);

    // call to a tagged template function
    testWarning(
        "/** @return {string} */ function tag(x) {return x} /** @enum {string} */ let E = { A:"
            + " tag`some` };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM);

    testWarning(
        "let obj = {fooVal : ''}; /** @enum {string} */ let E = {A: obj.fooVal };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with GETPROP

    testWarning(
        "let obj = {fooVal : ''}; /** @enum {string} */ let E = {A: obj.fooVal + '' };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with a
    // GETPROP operand in `+`

    testWarning(
        "/** @type {number} */ let num = 5; /** @enum {string} */ let E = { A: 'a'+ num };",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with var `num`.

    testWarning(
        "/** @type {number} */ let num = 5; /** @enum {string} */ let E = { A: `a${num}`};",
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // non-statically initialized with
    // template lit substitution.

    testWarning(
        lines(
            "/** @enum {string} */",
            "let EnumOne = {  FOO: 'foo',  BAR: 'bar'};",
            "/** @enum {string}*/",
            "let EnumTwo = { FOO: EnumOne.FOO}"),
        NON_STATIC_INITIALIZER_STRING_VALUE_IN_ENUM); // using another string enum as value gets
    // reported
  }
}
