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

import static com.google.javascript.jscomp.lint.CheckDuplicateCase.DUPLICATE_CASE;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

/**
 * Test case for {@link CheckDuplicateCase}.
 */
public final class CheckDuplicateCaseTest extends Es6CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckDuplicateCase(compiler);
  }

  public void testCheckDuplicateCase_noWarning() {
    testSame("switch (foo) { case 1: break; case 2: break; }");
    testSame("switch (foo) { case 1: break; case '1': break; }");
    testSame("switch (foo) { case bar: break; case 'bar': break; }");
    testSame("switch (foo) { case 1: break; } switch (bar) { case 1: break; }");
  }

  public void testCheckDuplicateCase_warning() {
    testWarning("switch (foo) { case 1: var a = 3; case 1: break; }", DUPLICATE_CASE);
    testWarning("switch (foo) { case 1: break; case 2: break; case 1: break; }", DUPLICATE_CASE);
    testWarning("switch (foo) { case '1': break; case '1': break; }", DUPLICATE_CASE);
    testWarning("switch (foo) { case bar: break; case bar: break; }", DUPLICATE_CASE);
    testWarning("switch (foo) { case i++: break; case i++: break; }", DUPLICATE_CASE);
  }
}
