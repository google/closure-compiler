/*
 * Copyright 2016 The Closure Compiler Authors.
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

@RunWith(JUnit4.class)
public class CheckMissingSemicolonTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingSemicolon(compiler);
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

  private void testWarning(String js) {
    testWarning(js, CheckMissingSemicolon.MISSING_SEMICOLON);
  }

  @Test
  public void testWarning() {
    testWarning("var x");
    testWarning("alert(1)");
    testWarning("do { things; } while (true)");
  }

  @Test
  public void testNoWarning_withSemicolon() {
    testSame("var x;");
    testSame("alert(1);");
  }

  @Test
  public void testNoWarning_controlStructure() {
    testSame("if (true) {}");
    testSame("while (true) {}");
    testSame("do { things; } while (true);");
    testSame("switch (n) { case 3: alert(4); }");
    testSame("switch (n) { case 5: { alert(6); } }");
    testSame("alert(1); LABEL: while (true) { alert(2); }");
    testSame("for (;;) {}");
    testSame("for (x of y) {}");
    testSame("for (x in y) {}");
  }

  @Test
  public void testNoWarning_functionOrClass() {
    testSame("function f() {}");
    testSame("function* f() {}");
    testSame("class Example {}");
  }

  @Test
  public void testWarning_export() {
    testWarning("export var x = 3");
    testWarning("export * from 'other'");
  }

  @Test
  public void testNoWarning_export() {
    testSame("export function f() {}");
    testSame("export class C {}");
  }
}
