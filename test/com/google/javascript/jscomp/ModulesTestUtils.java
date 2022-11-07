/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;

/**
 * Test utilities for testing modules, used by {@link Es6RewriteModulesTest} and {@link
 * ProcessCommonJSModulesTest}.
 */
final class ModulesTestUtils {

  static void testModules(CompilerTestCase test, String fileName, String input, String expected) {
    // Shared with ProcessCommonJSModulesTest.
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("other.js", "goog.provide('module$other');"),
            SourceFile.fromCode("yet_another.js", "goog.provide('module$yet_another');"),
            SourceFile.fromCode(fileName, input));
    ImmutableList<SourceFile> expecteds =
        ImmutableList.of(
            SourceFile.fromCode("other.js", "goog.provide('module$other');"),
            SourceFile.fromCode("yet_another.js", "goog.provide('module$yet_another');"),
            SourceFile.fromCode(fileName, expected));
    test.test(CompilerTestCase.srcs(inputs), CompilerTestCase.expected(expecteds));
  }

  static void testModulesError(
      CompilerTestCase test, String input, DiagnosticType error) {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("other.js", ""), SourceFile.fromCode("testcode.js", input));
    test.testError(CompilerTestCase.srcs(inputs), CompilerTestCase.error(error));
  }

  private ModulesTestUtils() {}
}
