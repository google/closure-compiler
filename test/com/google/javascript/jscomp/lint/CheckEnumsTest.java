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

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticType;

/**
 * Test case for {@link CheckEnums}.
 */
public class CheckEnumsTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckEnums(compiler);
  }

  public void testCheckEnums() throws Exception {
    testOk("/** @enum {number} */ ns.Enum = {A: 1, B: 2};");
    testOk("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'bar'};");
    testSame("/** @enum {number} */ ns.Enum = {A: 1, B: 1};",
        CheckEnums.DUPLICATE_ENUM_VALUE);
    testSame("/** @enum {string} */ ns.Enum = {A: 'foo', B: 'foo'};",
        CheckEnums.DUPLICATE_ENUM_VALUE);

    testOk("/** @enum {number} */ var Enum = {A: 1, B: 2};");
    testOk("/** @enum {string} */ var Enum = {A: 'foo', B: 'bar'};");
    testSame("/** @enum {number} */ var Enum = {A: 1, B: 1};",
        CheckEnums.DUPLICATE_ENUM_VALUE);
    testSame("/** @enum {string} */ var Enum = {A: 'foo', B: 'foo'};",
        CheckEnums.DUPLICATE_ENUM_VALUE);
  }

  private void testOk(String js) {
    test(js, js, null, (DiagnosticType) null);
  }
}
