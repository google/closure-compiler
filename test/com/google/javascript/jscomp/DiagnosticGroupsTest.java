/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DiagnosticGroupsTest {

  /**
   * Tests that {@link DiagnosticGroup.LINT_CHECKS} does not contain DiagnosticTypes that also
   * belong to other DiagnosticGroups.
   *
   * <p>Because all {@link DiagnosticGroup.LINT_CHECKS} run in a single pass, enabling a diagnostic
   * group containing one of the checks causes all of the other checks to run, which is surprising.
   */
  @Test
  public void lintChecksGroupIsDisjointFromEveryOtherGroup() throws Exception {
    DiagnosticGroup lintChecks = DiagnosticGroups.LINT_CHECKS;
    for (DiagnosticGroup group : DiagnosticGroups.getRegisteredGroups().values()) {
      // TODO(lharker): stop ignoring USE_OF_GOOG_PROVIDE after migrating rules_closure
      // code to suppress useOfGoogProvide instead of lintChecks.
      if (group.equals(lintChecks) || group.equals(DiagnosticGroups.USE_OF_GOOG_PROVIDE)) {
        continue;
      }
      assertWithMessage(
              "DiagnosticTypes common to DiagnosticGroups "
                  + lintChecks.getName()
                  + " and "
                  + group.getName())
          .that(Sets.intersection(lintChecks.getTypes(), group.getTypes()))
          .isEmpty();
    }
  }
}
