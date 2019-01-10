/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.SortingErrorManager.ErrorWithLevel;
import com.google.javascript.jscomp.SortingErrorManager.LeveledJSErrorComparator;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link SortingErrorManager}.
 *
 */
@RunWith(JUnit4.class)
public final class SortingErrorManagerTest {
  private static final String NULL_SOURCE = null;

  private final LeveledJSErrorComparator comparator = new LeveledJSErrorComparator();

  static final CheckLevel E = CheckLevel.ERROR;

  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "Foo");

  private static final DiagnosticType JOO_TYPE =
      DiagnosticType.error("TEST_JOO", "Joo");

  @Test
  public void testOrderingBothNull() {
    assertThat(comparator.compare(null, null)).isEqualTo(0);
  }

  @Test
  public void testOrderingSourceName1() {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make("a", -1, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  @Test
  public void testOrderingSourceName2() {
    JSError e1 = JSError.make("a", -1, -1, FOO_TYPE);
    JSError e2 = JSError.make("b", -1, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  @Test
  public void testOrderingLineno1() {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 2, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  @Test
  public void testOrderingLineno2() {
    JSError e1 = JSError.make(NULL_SOURCE, 8, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 56, -1, FOO_TYPE);
    assertSmaller(error(e1), error(e2));
  }

  @Test
  public void testOrderingCheckLevel() {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);

    assertSmaller(warning(e1), error(e2));
  }

  @Test
  public void testOrderingCharno1() {
    JSError e1 = JSError.make(NULL_SOURCE, 5, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 5, 2, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
    // CheckLevel preempts charno comparison
    assertSmaller(warning(e1), error(e2));
  }

  @Test
  public void testOrderingCharno2() {
    JSError e1 = JSError.make(NULL_SOURCE, 8, 7, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 8, 5, FOO_TYPE);

    assertSmaller(error(e2), error(e1));
    // CheckLevel preempts charno comparison
    assertSmaller(warning(e2), error(e1));
  }

  @Test
  public void testOrderingDescription() {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, -1, -1, JOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  @Test
  public void testDeduplicatedErrors() {
    final List<JSError> printedErrors = new ArrayList<>();
    BasicErrorManager manager = new BasicErrorManager() {
      @Override
      public void println(CheckLevel level, JSError error) {
        printedErrors.add(error);
      }

      @Override
      protected void printSummary() { }
    };
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    manager.report(CheckLevel.ERROR, e1);
    manager.report(CheckLevel.ERROR, e2);
    manager.generateReport();
    assertThat(printedErrors).hasSize(1);
  }

  // This test is testing a "feature" that seems bogus and should likely be forbidden.
  @Test
  public void testGenerateReportCausesMoreWarnings() {
    BasicErrorManager manager =
        new BasicErrorManager() {
          private int printed = 0;

          @Override
          public void println(CheckLevel level, JSError error) {
            if (error.getType().equals(FOO_TYPE)) {
              // TODO(b/114762232) This behavior should not be supported, and will become malformed
              // when migrating to a SortingErrorManager with an ErrorReportGenerator.
              this.report(CheckLevel.ERROR, JSError.make(NULL_SOURCE, -1, -1, JOO_TYPE));
            }
            printed++;
          }

          @Override
          protected void printSummary() {
            assertThat(printed).isEqualTo(1);
          }
        };
    manager.report(CheckLevel.ERROR, JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE));
    manager.generateReport();
  }

  private ErrorWithLevel error(JSError e) {
    return new ErrorWithLevel(e, CheckLevel.ERROR);
  }

  private ErrorWithLevel warning(JSError e) {
    return new ErrorWithLevel(e, CheckLevel.WARNING);
  }

  private void assertSmaller(ErrorWithLevel p1, ErrorWithLevel p2) {
    int p1p2 = comparator.compare(p1, p2);
    assertWithMessage(Integer.toString(p1p2)).that(p1p2 < 0).isTrue();
    int p2p1 = comparator.compare(p2, p1);
    assertWithMessage(Integer.toString(p2p1)).that(p2p1 > 0).isTrue();
  }
}
