/*
 * Copyright 2015 The Closure Compiler Authors.
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

/**
 * Test case for {@link CheckEmptyStatements}.
 */
public class CheckEmptyStatementsTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckEmptyStatements(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  private void testWarning(String js) {
    testWarning(js, CheckEmptyStatements.USELESS_EMPTY_STATEMENT);
  }

  public void testWarning() {
    testWarning("function f() {};");
    testWarning("var x;;");
    testWarning("alert(1);;");
  }

  public void testNoWarning() {
    testSame("function f() {}");
    testSame("var x;");
    testSame("alert(1);");
    testSame("if (x); y;");
  }
}
