/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class CheckRegExpTest extends CompilerTestCase {
  CheckRegExp last = null;

  public CheckRegExpTest() {
    super("var RegExp;");
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_REGEXP, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    last = new CheckRegExp(compiler);
    return last;
  }

  private void testReference(String code, boolean expected) {
    if (expected) {
      testWarning(code, CheckRegExp.REGEXP_REFERENCE);
    } else {
      testSame(code);
    }
    assertThat(last.isGlobalRegExpPropertiesUsed()).isEqualTo(expected);
  }

  @Test
  public void testRegExp() {
    // Creating RegExp's is OK.
    testReference("RegExp();", false);
    testReference("var x = RegExp();", false);
    testReference("new RegExp();", false);
    testReference("var x = new RegExp();", false);

    // Checking for RegExp instances is OK, as well.
    testReference("x instanceof RegExp;", false);

    // Comparing for equality with RegExp is OK.
    testReference("x === RegExp;", false);
    testReference("x !== RegExp;", false);
    testReference("switch (x) { case RegExp: }", false);
    testReference("x == RegExp;", false);
    testReference("x != RegExp;", false);

    // Access to non-magical properties is OK.
    testReference("RegExp.test();", false);
    testReference("var x = RegExp.test();", false);
    testReference("RegExp.exec();", false);
    testReference("RegExp.foobar;", false);

    // Magical properties aren't allowed.
    testReference("RegExp.$1;", true);
    testReference("RegExp.$_;", true);
    testReference("RegExp.$input;", true);
    testReference("RegExp.rightContext;", true);
    testReference("RegExp.multiline;", true);

    // Any other reference isn't allowed.
    testReference("delete RegExp;", true);
    testReference("RegExp;", true);
    testReference("if (RegExp);", true);
    testReference("if (!RegExp);", true);

    // Aliases aren't allowed.
    testReference("var x = RegExp;", true);
    testReference("f(RegExp);", true);
    testReference("new f(RegExp);", true);
    testReference("var x = RegExp; x.test()", true);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testReference("let x = RegExp;", true);
    testReference("const x = RegExp;", true);

    // No RegExp reference is OK.
    testReference("var x;", false);

    // Local RegExp is OK.
    testReference("function f() {var RegExp; RegExp.test();}", false);
    testReference("function f() {let RegExp; RegExp.test();}", false);
    testReference("function *gen() {var RegExp; yield RegExp.test();}", false);

    // Property named 'RegExp' is OK.
    testReference("var x = {RegExp: {}}; x.RegExp.$1;", false);

    // Class property is also OK.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testReference(lines(
        "class x {",
        "  constructor() {this.RegExp = {};}",
        "  method() {",
        "    this.RegExp.$1;",
        "    this.RegExp.test();",
        "  }",
        "}"), false);
  }

  @Test
  public void testInvalidRange() {
    this.testWarning("\"asdf\".match(/[z-a]/)", CheckRegExp.MALFORMED_REGEXP);
  }
}
