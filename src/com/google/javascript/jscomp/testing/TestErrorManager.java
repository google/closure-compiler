/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static org.junit.Assert.assertEquals;

import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;

/**
 * An ErrorManager that compares errors with a list of expected errors.
 */
public class TestErrorManager extends BasicErrorManager {

  private int errorIndex = 0;
  private String[] errors = new String[0];
  private int warningIndex = 0;
  private String[] warnings = new String[0];

  public void expectErrors(String... expectedErrors) {
    this.errorIndex = 0;
    this.errors = expectedErrors;
  }

  public void expectWarnings(String... expectedWarnings) {
    this.warningIndex = 0;
    this.warnings = expectedWarnings;
  }

  @Override
  public void report(CheckLevel level, JSError error) {
    super.report(level, error);

    switch (level) {
      case ERROR:
        if (errorIndex >= errors.length) {
          throw new AssertionError("Unexpected error: " + error);
        }
        assertEquals(errors[errorIndex++], error.description);
        break;
      case WARNING:
        if (errorIndex >= warnings.length) {
          throw new AssertionError("Unexpected warning: " + error);
        }
        assertEquals(warnings[warningIndex++], error.description);
        break;
      case OFF:
        // no-op
        break;
    }
  }

  @Override
  public void println(CheckLevel level, JSError error) {
    // no-op
  }

  @Override
  protected void printSummary() {
    // no-op
  }

  public boolean hasEncounteredAllErrors() {
    return errorIndex == errors.length;
  }

  public boolean hasEncounteredAllWarnings() {
    return warningIndex == warnings.length;
  }
}
