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

/**
 * @author johnlenz@google.com (John Lenz)
 */
public class CheckRegExpTest extends CompilerTestCase {
  CheckRegExp last = null;

  public CheckRegExpTest() {
    super("var RegExp;", true);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    compiler.options.setWarningLevel(
        DiagnosticGroups.CHECK_REGEXP, CheckLevel.WARNING);
    last = new CheckRegExp(compiler);
    return last;
  }

  private void testReference(String code, boolean expected) {
    testSame(code, (expected) ? CheckRegExp.REGEXP_REFERENCE : null);
    assertEquals(expected, last.isGlobalRegExpPropertiesUsed());
  }

  public void testRegExp() {
    // Creating regexp's is ok
    testReference("RegExp();", false);
    testReference("var x = RegExp();", false);
    testReference("new RegExp();", false);
    testReference("var x = new RegExp();", false);

    // Checking for RegExp instances is ok, as well.
    testReference("x instanceof RegExp;", false);

    // Any other reference isn't
    testReference("RegExp.test();", true);
    testReference("var x = RegExp.test();", true);
    testReference("RegExp.exec();", true);
    testReference("RegExp.$1;", true);
    testReference("RegExp.foobar;", true);
    testReference("delete RegExp;", true);

    // Aliases aren't allowed
    testReference("var x = RegExp;", true);
    testReference("f(RegExp);", true);
    testReference("new f(RegExp);", true);
    testReference("var x = RegExp; x.test()", true);

    // No RegExp reference is ok
    testReference("var x;", false);
    
    // Local RegExp is ok
    testReference("function f() {var RegExp; RegExp.test();}", false);
  }
}
