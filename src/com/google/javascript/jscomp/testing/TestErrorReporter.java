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

package com.google.javascript.jscomp.testing;

import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.EvaluatorException;

import junit.framework.Assert;

/**
 * <p>An error reporter for testing that verifies that messages reported to the
 * reporter are expected.</p>
 *
 * <p>Sample use</p>
 * <pre>
 * TestErrorReporter e =
 *   new TestErrorReporter(null, new String[] { "first warning" });
 * ...
 * assertTrue(e.hasEncounteredAllWarnings());
 * </pre>
 *
 */
public final class TestErrorReporter extends Assert implements ErrorReporter {
  private final String[] errors;
  private final String[] warnings;
  private int errorsIndex = 0;
  private int warningsIndex = 0;

  public TestErrorReporter(String[] errors, String[] warnings) {
    this.errors = errors;
    this.warnings = warnings;
  }

  @Override
  public void error(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
    if (errors != null && errorsIndex < errors.length) {
      assertEquals(errors[errorsIndex++], message);
    } else {
      fail("extra error: " + message);
    }
  }

  @Override
  public void warning(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
    if (warnings != null && warningsIndex < warnings.length) {
      assertEquals(warnings[warningsIndex++], message);
    } else {
      fail("extra warning: " + message);
    }
  }

  @Override
  public EvaluatorException runtimeError(String message, String sourceName,
      int line, String lineSource, int lineOffset) {
    return new EvaluatorException("JSCompiler test code: " + message);
  }

  /**
   * Returns whether all warnings were reported to this reporter.
   */
  public boolean hasEncounteredAllWarnings() {
    return (warnings == null) ?
        warningsIndex == 0 :
        warnings.length == warningsIndex;
  }

  /**
   * Returns whether all errors were reported to this reporter.
   */
  public boolean hasEncounteredAllErrors() {
    return (errors == null) ?
        errorsIndex == 0 :
        errors.length == errorsIndex;
  }
}
