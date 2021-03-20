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
public class SetLocationTest {

  /** Path of the directory containing test inputs and expected outputs. */
  private static final String TESTDATA_DIR =
      "test/"
          + "com/google/javascript/refactoring/examples/security/testdata";

  /** The RefasterJs template to use for Location#href. */
  private static final String SET_LOCATION_HREF_TEMPLATE =
      "com/google/javascript/refactoring/examples/refasterjs/security/set_location_href.js";

  /** The RefasterJs template to use for Window#location. */
  private static final String SET_WINDOW_LOCATION_TEMPLATE =
      "com/google/javascript/refactoring/examples/refasterjs/security/set_window_location.js";

  @Test
  public void testLocationHref() throws Exception {
    RefasterJsTestCase.builder()
        .setTemplatePath(SET_LOCATION_HREF_TEMPLATE)
        .setInputPath(TESTDATA_DIR + "/set_location_href_test_in.js")
        .addExpectedOutputPath(TESTDATA_DIR + "/set_location_href_test_out.js")
        .addAdditionalSourcePath(TESTDATA_DIR + "/goog_base.js")
        .test();
  }

  @Test
  public void testWindowLocation() throws Exception {
    RefasterJsTestCase.builder()
        .setTemplatePath(SET_WINDOW_LOCATION_TEMPLATE)
        .setInputPath(TESTDATA_DIR + "/set_window_location_test_in.js")
        .addExpectedOutputPath(TESTDATA_DIR + "/set_window_location_test_out.js")
        .addAdditionalSourcePath(TESTDATA_DIR + "/goog_base.js")
        .test();
  }
}
