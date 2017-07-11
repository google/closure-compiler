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
package com.google.javascript.refactoring.examples;

import static com.google.javascript.refactoring.testing.RefasterJsTestUtils.assertFileRefactoring;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class StringIndexOfToIncludesTest {

  /** Path of the directory containing test inputs and expected outputs. */
  private static final String TESTDATA_DIR =
      "test/"
          + "com/google/javascript/refactoring/examples/testdata";

  /** The RefasterJs template to use. */
  private static final String STRING_INDEXOF_TO_INCLUDES_TEMPLATE =
      "src/" +
      "com/google/javascript/refactoring/examples/refasterjs/string_indexof_to_includes.js";

  @Test
  public void test_refactorings() throws Exception {
    assertFileRefactoring(
        STRING_INDEXOF_TO_INCLUDES_TEMPLATE,
        TESTDATA_DIR,
        "string_indexof_to_includes_in.js",
        ImmutableList.of("goog_base.js"),
        "string_indexof_to_includes_out.js");
  }
}
