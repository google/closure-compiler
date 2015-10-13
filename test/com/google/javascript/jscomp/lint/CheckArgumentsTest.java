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

import static com.google.javascript.jscomp.lint.CheckArguments.BAD_ARGUMENTS_USAGE;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

public final class CheckArgumentsTest extends Es6CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckArguments(compiler);
  }

  public void testCheckArguments_noWarning() {
    testSameEs6("function f() { for (let a of arguments) {} }");
    testSameEs6("function f() { return [...arguments]; }");
    testSameEs6("function f() { return arguments[1]; }");
    testSameEs6("function f() { for (var i=0; i<arguments.length; i++) alert(arguments[i]); }");
  }

  public void testCheckArguments_warning() {
    testWarning("function f() { g(arguments); }", BAD_ARGUMENTS_USAGE);
    testWarning("function f() { var args = arguments; }", BAD_ARGUMENTS_USAGE);
    testWarning("function f() { var args = [0, 1, arguments]; }", BAD_ARGUMENTS_USAGE);
    testWarning("function f() { arguments.caller; }", BAD_ARGUMENTS_USAGE);
    testWarning("function f() { arguments.callee; }", BAD_ARGUMENTS_USAGE);
    testWarning("function f() { for (var i in arguments) {} }", BAD_ARGUMENTS_USAGE);
  }
}
