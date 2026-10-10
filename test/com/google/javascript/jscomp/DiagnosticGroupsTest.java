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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
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
      if (group.equals(lintChecks)
          // TODO(lharker): stop ignoring USE_OF_GOOG_PROVIDE after migrating rules_closure
          // code to suppress useOfGoogProvide instead of lintChecks.
          || group.equals(DiagnosticGroups.USE_OF_GOOG_PROVIDE)
          // We need a separate group for "lintVarDeclarations" so code that really cannot be
          // updated to use only `let` and `const` can suppress the warnings about `var` without
          // suppressing all lint checks.
          || group.equals(DiagnosticGroups.LINT_VAR_DECLARATIONS)) {
        continue;
      }
      assertWithMessage(
              "DiagnosticTypes common to DiagnosticGroups %s and %s",
              lintChecks.getName(), group.getName())
          .that(Sets.intersection(lintChecks.getTypes(), group.getTypes()))
          .isEmpty();
    }
  }

  @Test
  public void conformanceErrorsCannotBeDowngraded() {
    for (DiagnosticGroup group : DiagnosticGroups.getRegisteredGroups().values()) {
      assertWithMessage("Group '%s' should not include JSC_CONFORMANCE_ERROR", group.getName())
          .that(group.getTypes())
          .doesNotContain(CheckConformance.CONFORMANCE_ERROR);
    }
  }

  @Test
  public void testClosureUnawareCodeWarningsGuard() {
    ClosureUnawareCodeWarningsGuard guard = new ClosureUnawareCodeWarningsGuard();
    Node n1 = Node.newString("test");
    SourceFile src1 = SourceFile.fromCode("foo.js", "");
    src1.markAsClosureUnawareCode();
    n1.setStaticSourceFile(src1);
    n1.setIsInClosureUnawareSubtree(true);
    JSError suppressibleError = JSError.make(n1, RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING, "");
    assertThat(guard.level(suppressibleError)).isEqualTo(CheckLevel.OFF);

    Node n2 = Node.newString("test");
    JSError nonSuppressibleError =
        JSError.make(n2, RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING, "");
    assertThat(guard.level(nonSuppressibleError)).isNull();

    Node n3 = Node.newString("test");
    SourceFile src3 = SourceFile.fromCode("foo.js", "");
    src3.markAsClosureUnawareCode();
    n3.setStaticSourceFile(src3);
    n3.setIsInClosureUnawareSubtree(true);
    JSError otherError = JSError.make(n3, DiagnosticType.error("FOO", "foo"), "");
    assertThat(guard.level(otherError)).isNull();
  }

  @Test
  public void testDiagnosticGroupPathSuppressingWarningsGuard() {
    DiagnosticGroupPathSuppressingWarningsGuard guard =
        new DiagnosticGroupPathSuppressingWarningsGuard(
            DiagnosticGroups.LINT_CHECKS, "suppress_me");

    DiagnosticType lintCheck = DiagnosticGroups.LINT_CHECKS.getTypes().iterator().next();
    JSError errorInPath = JSError.make("some/path/suppress_me/file.js", -1, -1, lintCheck);
    assertWithMessage(
            "DiagnosticGroupPathSuppressingWarningsGuard should suppress in matching path")
        .that(guard.level(errorInPath))
        .isEqualTo(CheckLevel.OFF);

    JSError errorInOtherPath = JSError.make("some/other/file.js", -1, -1, lintCheck);
    assertWithMessage(
            "DiagnosticGroupPathSuppressingWarningsGuard should not suppress in other path")
        .that(guard.level(errorInOtherPath))
        .isNull();

    JSError otherErrorInPath =
        JSError.make("some/path/suppress_me/file.js", -1, -1, DiagnosticType.error("FOO", "foo"));
    assertWithMessage(
            "DiagnosticGroupPathSuppressingWarningsGuard should not suppress unrelated errors")
        .that(guard.level(otherErrorInPath))
        .isNull();
  }
}
