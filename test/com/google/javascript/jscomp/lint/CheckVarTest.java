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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckVar}. */
@RunWith(JUnit4.class)
public class CheckVarTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckVar(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  private void testWarning(String js) {
    testWarning(js, CheckVar.VAR);
  }

  @Test
  public void testWarning() {
    testWarning("var x;");
    testWarning("var x = 12;");
    testWarning("export var x;");
    testWarning("function f() { var x = 12; return x; }");
  }

  @Test
  public void testSuppressWarning() {
    testSame(
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {lintVarDeclarations}",
            " */",
            "var x;",
            "var x12 = 12;",
            "export var x;",
            "function f() { var x = 12; return x; }"));
  }

  @Test
  public void testNoWarning() {
    testSame("function f() { return 'var'; }");
    testSame("let x;");
    testSame("let x = 12;");
    testSame("const x = 12;");
    testSame("function f() { let x; x = 12; return x; }");
    testSame("function f() { const x = 12; return x; }");
  }
}
