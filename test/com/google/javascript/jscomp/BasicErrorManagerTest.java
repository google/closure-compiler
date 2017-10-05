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

import com.google.javascript.jscomp.BasicErrorManager.ErrorWithLevel;
import com.google.javascript.jscomp.BasicErrorManager.LeveledJSErrorComparator;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests {@link BasicErrorManager}.
 *
 */
public final class BasicErrorManagerTest extends TestCase {
  private static final String NULL_SOURCE = null;

  private final LeveledJSErrorComparator comparator = new LeveledJSErrorComparator();

  static final CheckLevel E = CheckLevel.ERROR;

  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "Foo");

  private static final DiagnosticType JOO_TYPE =
      DiagnosticType.error("TEST_JOO", "Joo");

  public void testOrderingBothNull() throws Exception {
    assertEquals(0, comparator.compare(null, null));
  }

  public void testOrderingSourceName1() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make("a", -1, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  public void testOrderingSourceName2() throws Exception {
    JSError e1 = JSError.make("a", -1, -1, FOO_TYPE);
    JSError e2 = JSError.make("b", -1, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  public void testOrderingLineno1() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 2, -1, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

  public void testOrderingLineno2() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, 8, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 56, -1, FOO_TYPE);
    assertSmaller(error(e1), error(e2));
  }

  public void testOrderingCheckLevel() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);

    assertSmaller(warning(e1), error(e2));
  }

  public void testOrderingCharno1() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, 5, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 5, 2, FOO_TYPE);

    assertSmaller(error(e1), error(e2));
    // CheckLevel preempts charno comparison
    assertSmaller(warning(e1), error(e2));
  }

  public void testOrderingCharno2() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, 8, 7, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, 8, 5, FOO_TYPE);

    assertSmaller(error(e2), error(e1));
    // CheckLevel preempts charno comparison
    assertSmaller(warning(e2), error(e1));
  }

  public void testOrderingDescription() throws Exception {
    JSError e1 = JSError.make(NULL_SOURCE, -1, -1, FOO_TYPE);
    JSError e2 = JSError.make(NULL_SOURCE, -1, -1, JOO_TYPE);

    assertSmaller(error(e1), error(e2));
  }

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

  // Ensure that more warnings can be added from generating the report.
  // One case in which this happened is when the report tries to use the source maps to map back to
  // the original source, yet a warning was produced because of a corrupted source map. This test
  // ensures that there is no java.util.ConcurrentModificationException while adding a new warning
  // when iterating over the existing warnings to print them out.
  public void testGenerateReportCausesMoreWarnings() {
    BasicErrorManager manager =
        new BasicErrorManager() {
          private int printed = 0;

          @Override
          public void println(CheckLevel level, JSError error) {
            if (error.getType().equals(FOO_TYPE)) {
              this.report(CheckLevel.ERROR, JSError.make(NULL_SOURCE, -1, -1, JOO_TYPE));
            }
            printed++;
          }

          @Override
          protected void printSummary() {
            assertEquals(2, printed);
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
    assertTrue(Integer.toString(p1p2), p1p2 < 0);
    int p2p1 = comparator.compare(p2, p1);
    assertTrue(Integer.toString(p2p1), p2p1 > 0);
  }
}
