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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;

/**
 * Test case for {@link CheckInterfaces}.
 */
public final class CheckInterfacesTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckInterfaces(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  public void setUp() {
    enableTypeCheck();
  }

  public void testInterfaceArgs() throws Exception {
    testSame("/** @interface */ function A(x) {}",
        CheckInterfaces.INTERFACE_SHOULD_NOT_TAKE_ARGS);

    testSame("var ns = {};\n"
        + "/** @interface */\n"
        + "ns.SomeInterface = function(x) {};",
        CheckInterfaces.INTERFACE_SHOULD_NOT_TAKE_ARGS);
  }

  public void testInterfaceNotEmpty() {
    testSame("/** @interface */ function A() { this.foo; }",
        CheckInterfaces.INTERFACE_FUNCTION_NOT_EMPTY);

    testSame("var ns = {};\n"
        + "/** @interface */\n"
        + "ns.SomeInterface = function() { this.foo; };",
        CheckInterfaces.INTERFACE_FUNCTION_NOT_EMPTY);
  }
}
