/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.ErrorReporter;
import java.util.Arrays;
import org.junit.Assert;

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
public final class TestErrorReporter implements ErrorReporter {
  private String[] errors;
  private String[] warnings;
  private int errorsIndex = 0;
  private int warningsIndex = 0;

  public TestErrorReporter(String[] errors, String[] warnings) {
    this.errors = errors == null ? new String[] {} : errors;
    this.warnings = warnings == null ? new String[] {} : warnings;
  }

  public static TestErrorReporter forNoExpectedReports() {
    return new TestErrorReporter(null, null);
  }

  public void setErrors(String[] errors) {
    this.errors = errors;
    errorsIndex = 0;
  }

  public void setWarnings(String[] warnings) {
    this.warnings = warnings;
    warningsIndex = 0;
  }

  @Override
  public void error(String message, String sourceName, int line,
      int lineOffset) {
    if (errorsIndex < errors.length) {
      assertThat(message).isEqualTo(errors[errorsIndex++]);
    } else {
      Assert.fail("extra error: " + message);
    }
  }

  @Override
  public void warning(String message, String sourceName, int line, int lineOffset) {
    if (warningsIndex < warnings.length) {
      assertThat(message).isEqualTo(warnings[warningsIndex++]);
    } else {
      Assert.fail("extra warning: " + message);
    }
  }

  public void assertHasEncounteredAllWarnings() {
    if (warnings.length != warningsIndex) {
      Assert.fail(
          "missing warnings: " + Arrays.asList(warnings).subList(warningsIndex, warnings.length));
    }
  }

  public void assertHasEncounteredAllErrors() {
    if (errors.length != errorsIndex) {
      Assert.fail(
          "missing errors: " + Arrays.asList(errors).subList(errorsIndex, errors.length));
    }
  }
}
