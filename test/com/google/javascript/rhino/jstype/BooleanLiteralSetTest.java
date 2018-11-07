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
 *   Nick Santos
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

package com.google.javascript.rhino.jstype;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.BOTH;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.EMPTY;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.FALSE;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.TRUE;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link BooleanLiteralSet}.
 *
 */
@RunWith(JUnit4.class)
public class BooleanLiteralSetTest {

  @Test
  public void testIntersection() {
    assertThat(EMPTY.intersection(EMPTY)).isEqualTo(EMPTY);
    assertThat(EMPTY.intersection(TRUE)).isEqualTo(EMPTY);
    assertThat(EMPTY.intersection(FALSE)).isEqualTo(EMPTY);
    assertThat(EMPTY.intersection(BOTH)).isEqualTo(EMPTY);
    assertThat(TRUE.intersection(EMPTY)).isEqualTo(EMPTY);
    assertThat(TRUE.intersection(TRUE)).isEqualTo(TRUE);
    assertThat(TRUE.intersection(FALSE)).isEqualTo(EMPTY);
    assertThat(TRUE.intersection(BOTH)).isEqualTo(TRUE);
    assertThat(FALSE.intersection(EMPTY)).isEqualTo(EMPTY);
    assertThat(FALSE.intersection(TRUE)).isEqualTo(EMPTY);
    assertThat(FALSE.intersection(FALSE)).isEqualTo(FALSE);
    assertThat(FALSE.intersection(BOTH)).isEqualTo(FALSE);
    assertThat(BOTH.intersection(EMPTY)).isEqualTo(EMPTY);
    assertThat(BOTH.intersection(TRUE)).isEqualTo(TRUE);
    assertThat(BOTH.intersection(FALSE)).isEqualTo(FALSE);
    assertThat(BOTH.intersection(BOTH)).isEqualTo(BOTH);
  }

  @Test
  public void testUnion() {
    assertThat(EMPTY.union(EMPTY)).isEqualTo(EMPTY);
    assertThat(EMPTY.union(TRUE)).isEqualTo(TRUE);
    assertThat(EMPTY.union(FALSE)).isEqualTo(FALSE);
    assertThat(EMPTY.union(BOTH)).isEqualTo(BOTH);
    assertThat(TRUE.union(EMPTY)).isEqualTo(TRUE);
    assertThat(TRUE.union(TRUE)).isEqualTo(TRUE);
    assertThat(TRUE.union(FALSE)).isEqualTo(BOTH);
    assertThat(TRUE.union(BOTH)).isEqualTo(BOTH);
    assertThat(FALSE.union(EMPTY)).isEqualTo(FALSE);
    assertThat(FALSE.union(TRUE)).isEqualTo(BOTH);
    assertThat(FALSE.union(FALSE)).isEqualTo(FALSE);
    assertThat(FALSE.union(BOTH)).isEqualTo(BOTH);
    assertThat(BOTH.union(EMPTY)).isEqualTo(BOTH);
    assertThat(BOTH.union(TRUE)).isEqualTo(BOTH);
    assertThat(BOTH.union(FALSE)).isEqualTo(BOTH);
    assertThat(BOTH.union(BOTH)).isEqualTo(BOTH);
  }

  @Test
  public void testGet() {
    assertThat(BooleanLiteralSet.get(true)).isEqualTo(TRUE);
    assertThat(BooleanLiteralSet.get(false)).isEqualTo(FALSE);
  }

  @Test
  public void testContains() {
    assertThat(EMPTY.contains(true)).isFalse();
    assertThat(EMPTY.contains(false)).isFalse();
    assertThat(TRUE.contains(true)).isTrue();
    assertThat(TRUE.contains(false)).isFalse();
    assertThat(FALSE.contains(true)).isFalse();
    assertThat(FALSE.contains(false)).isTrue();
    assertThat(BOTH.contains(true)).isTrue();
    assertThat(BOTH.contains(false)).isTrue();
  }
}
