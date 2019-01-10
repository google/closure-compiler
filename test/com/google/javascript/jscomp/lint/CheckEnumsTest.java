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
import static com.google.javascript.jscomp.lint.CheckEnums.SHORTHAND_ASSIGNMENT_IN_ENUM;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
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
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Test
  public void testCheckEnums() {
    testSame("/** @enum {number} */ ns.Enum = {A: 1, B: 2};");
    testSame("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'bar'};");
    testWarning("/** @enum {number} */ ns.Enum = {A: 1, B: 1};",
        DUPLICATE_ENUM_VALUE);
    testWarning("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'foo'};",
        DUPLICATE_ENUM_VALUE);

    testSame("/** @enum {number} */ var Enum = {A: 1, B: 2};");
    testSame("/** @enum {string} */ var Enum = {A: 'foo', B: 'bar'};");
    testWarning("/** @enum {number} */ var Enum = {A: 1, B: 1};",
        DUPLICATE_ENUM_VALUE);
    testWarning("/** @enum {string} */ var Enum = {A: 'foo', B: 'foo'};",
        DUPLICATE_ENUM_VALUE);

    testWarning("/** @enum {number} */ var Enum = {A};",
        SHORTHAND_ASSIGNMENT_IN_ENUM);
    testWarning("/** @enum {string} */ var Enum = {['prop' + f()]: 'foo'};",
        COMPUTED_PROP_NAME_IN_ENUM);

    testWarning(
        "/** @enum {number} */ var E = { a: 1 };",
        ENUM_PROP_NOT_CONSTANT);
    testWarning(
        "/** @enum {number} */ var E = { ABC: 1, abc: 2 };",
        ENUM_PROP_NOT_CONSTANT);
  }

  @Test
  public void testCheckValidEnums_withES6Modules() {
    testSame("export /** @enum {number} */ var Enum = {A: 1, B: 2};");
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules01() {
    testWarning("export /** @enum {number} */ var Enum = {A: 1, B: 1};", DUPLICATE_ENUM_VALUE);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules02() {
    testWarning("export /** @enum {number} */ var Enum = {A};", SHORTHAND_ASSIGNMENT_IN_ENUM);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules03() {
    testWarning(
        "export /** @enum {string} */ var Enum = {['prop' + f()]: 'foo'};",
        COMPUTED_PROP_NAME_IN_ENUM);
  }

  @Test
  public void testCheckInvalidEnums_withES6Modules04() {
    testWarning("export /** @enum {number} */ var E = { a: 1 };", ENUM_PROP_NOT_CONSTANT);
  }
}
