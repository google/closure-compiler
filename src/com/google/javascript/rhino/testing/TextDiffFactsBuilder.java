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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Fact.fact;
import static java.util.stream.Collectors.joining;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import java.util.List;

/**
 * Builds Fact objects for use with the Truth library that represent the difference between two text
 * strings.
 */
public final class TextDiffFactsBuilder {

  private final String title;
  private String expectedText;
  private String actualText;

  public TextDiffFactsBuilder(String title) {
    this.title = title;
  }

  public TextDiffFactsBuilder expectedText(String expectedText) {
    this.expectedText = expectedText;
    return this;
  }

  public TextDiffFactsBuilder actualText(String actualText) {
    this.actualText = actualText;
    return this;
  }

  /**
   * Returns one or more Fact objects representing the difference between the expected and actual
   * text strings, which must be specified by calling their methods before calling this one.
   */
  public ImmutableList<Fact> build() {
    try {
      // The diff algorithm expects to work on a list, so we need to split the text into
      // lines.
      final Splitter lineSplitter = Splitter.on('\n');
      final List<String> expectedLines = lineSplitter.splitToList(checkNotNull(expectedText));
      final List<String> actualLines = lineSplitter.splitToList(checkNotNull(actualText));
      final Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);
      final List<String> unifiedDiff =
          UnifiedDiffUtils.generateUnifiedDiff(
              "expected", "actual", expectedLines, patch, /* contextSize= */ 10);
      return ImmutableList.of(fact(title, unifiedDiff.stream().collect(joining("\n"))));
    } catch (DiffException e) {
      // It's unclear when this exception happens.
      // It may indicate a bug in the diff library itself.
      throw new IllegalStateException(e);
    }
  }
}
