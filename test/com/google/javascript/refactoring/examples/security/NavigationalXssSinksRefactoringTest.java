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
package com.google.javascript.refactoring.examples.security;

import com.google.javascript.refactoring.testing.RefasterJsTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NavigationalXssSinksRefactoringTest {

  /** Path of the directory containing test inputs and expected outputs. */
  private static final String TESTDATA_DIR =
      "test/"
          + "com/google/javascript/refactoring/examples/security/testdata";

  /** The RefasterJs template to use. */
  private static final String NAVIGATIONAL_XSS_SINKS_TEMPLATE =
      "com/google/javascript/refactoring/examples/refasterjs/security/navigational_xss_sinks.js";

  @Test
  public void test_refactorings() throws Exception {
    RefasterJsTestCase.builder()
        .setTemplatePath(NAVIGATIONAL_XSS_SINKS_TEMPLATE)
        .setInputPath(TESTDATA_DIR + "/navigational_xss_sinks_test_in.js")
        .addExpectedOutputPath(TESTDATA_DIR + "/navigational_xss_sinks_test_out.js")
        .addAdditionalSourcePath(TESTDATA_DIR + "/goog_base.js")
        .test();
  }

  @Test
  public void testModuleRefactoring() throws Exception {
    RefasterJsTestCase.builder()
        .setTemplatePath(NAVIGATIONAL_XSS_SINKS_TEMPLATE)
        .setInputPath(TESTDATA_DIR + "/navigational_xss_sinks_test_module_in.js")
        .addExpectedOutputPath(TESTDATA_DIR + "/navigational_xss_sinks_test_module_out.js")
        .addAdditionalSourcePath(TESTDATA_DIR + "/goog_base.js")
        .addAdditionalSourcePath(TESTDATA_DIR + "/goog_foo.js")
        .test();
  }
}
