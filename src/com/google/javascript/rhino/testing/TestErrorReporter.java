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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import java.util.ArrayList;

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
  private ImmutableList<String> expectedErrors;
  private ImmutableList<String> expectedWarnings;
  private final ArrayList<String> seenErrors = new ArrayList<>();
  private final ArrayList<String> seenWarnings = new ArrayList<>();

  public TestErrorReporter(String[] errors, String[] warnings) {
    setErrors(errors);
    setWarnings(warnings);
  }

  public static TestErrorReporter forNoExpectedReports() {
    return new TestErrorReporter(null, null);
  }

  public void setErrors(String[] errors) {
    this.expectedErrors = errors == null ? ImmutableList.of() : ImmutableList.copyOf(errors);
    this.seenErrors.clear();
  }

  public void setWarnings(String[] warnings) {
    this.expectedWarnings = warnings == null ? ImmutableList.of() : ImmutableList.copyOf(warnings);
    this.seenWarnings.clear();
  }

  @Override
  public void error(String message, String sourceName, int line, int lineOffset) {
    seenErrors.add(message);

    final String expected;
    if (seenErrors.size() > expectedErrors.size()) {
      expected = null;
    } else {
      expected = expectedErrors.get(seenErrors.size() - 1);
    }

    if (!message.equals(expected)) {
      assertHasEncounteredAllErrors();
    }
  }

  @Override
  public void warning(String message, String sourceName, int line, int lineOffset) {
    seenWarnings.add(message);

    final String expected;
    if (seenWarnings.size() > expectedWarnings.size()) {
      expected = null;
    } else {
      expected = expectedWarnings.get(seenWarnings.size() - 1);
    }

    if (!message.equals(expected)) {
      assertHasEncounteredAllWarnings();
    }
  }

  public void assertHasEncounteredAllWarnings() {
    assertThat(seenWarnings).containsExactlyElementsIn(expectedWarnings).inOrder();
  }

  public void assertHasEncounteredAllErrors() {
    assertThat(seenErrors).containsExactlyElementsIn(expectedErrors).inOrder();
  }
}
