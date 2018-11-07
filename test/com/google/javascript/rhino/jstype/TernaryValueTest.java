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
import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.TRUE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the behavior of {@link TernaryValue} by verifying the truth tables of the operations {@link
 * TernaryValue#and(TernaryValue)}, {@link TernaryValue#not()}, {@link
 * TernaryValue#or(TernaryValue)} and {@link TernaryValue#xor(TernaryValue)} as well as the {@link
 * TernaryValue#toBoolean(boolean)} method.
 *
 */
@RunWith(JUnit4.class)
public class TernaryValueTest {
  @Test
  public void testOrdinal() {
    assertThat(FALSE.ordinal()).isEqualTo(0);
    assertThat(TRUE.ordinal()).isEqualTo(1);
    assertThat(UNKNOWN.ordinal()).isEqualTo(2);
  }

  @Test
  public void testAnd() {
    assertThat(TRUE.and(TRUE)).isEqualTo(TRUE);
    assertThat(TRUE.and(FALSE)).isEqualTo(FALSE);
    assertThat(TRUE.and(UNKNOWN)).isEqualTo(UNKNOWN);

    assertThat(FALSE.and(TRUE)).isEqualTo(FALSE);
    assertThat(FALSE.and(FALSE)).isEqualTo(FALSE);
    assertThat(FALSE.and(UNKNOWN)).isEqualTo(FALSE);

    assertThat(UNKNOWN.and(TRUE)).isEqualTo(UNKNOWN);
    assertThat(UNKNOWN.and(FALSE)).isEqualTo(FALSE);
    assertThat(UNKNOWN.and(UNKNOWN)).isEqualTo(UNKNOWN);
  }

  @Test
  public void testNot() {
    assertThat(TRUE.not()).isEqualTo(FALSE);
    assertThat(FALSE.not()).isEqualTo(TRUE);
    assertThat(UNKNOWN.not()).isEqualTo(UNKNOWN);
  }

  @Test
  public void testOr() {
    assertThat(TRUE.or(TRUE)).isEqualTo(TRUE);
    assertThat(TRUE.or(FALSE)).isEqualTo(TRUE);
    assertThat(TRUE.or(UNKNOWN)).isEqualTo(TRUE);

    assertThat(FALSE.or(TRUE)).isEqualTo(TRUE);
    assertThat(FALSE.or(FALSE)).isEqualTo(FALSE);
    assertThat(FALSE.or(UNKNOWN)).isEqualTo(UNKNOWN);

    assertThat(UNKNOWN.or(TRUE)).isEqualTo(TRUE);
    assertThat(UNKNOWN.or(FALSE)).isEqualTo(UNKNOWN);
    assertThat(UNKNOWN.or(UNKNOWN)).isEqualTo(UNKNOWN);
  }

  @Test
  public void testXor() {
    assertThat(TRUE.xor(TRUE)).isEqualTo(FALSE);
    assertThat(TRUE.xor(FALSE)).isEqualTo(TRUE);
    assertThat(TRUE.xor(UNKNOWN)).isEqualTo(UNKNOWN);

    assertThat(FALSE.xor(TRUE)).isEqualTo(TRUE);
    assertThat(FALSE.xor(FALSE)).isEqualTo(FALSE);
    assertThat(FALSE.xor(UNKNOWN)).isEqualTo(UNKNOWN);

    assertThat(UNKNOWN.xor(TRUE)).isEqualTo(UNKNOWN);
    assertThat(UNKNOWN.xor(FALSE)).isEqualTo(UNKNOWN);
    assertThat(UNKNOWN.xor(UNKNOWN)).isEqualTo(UNKNOWN);
  }

  @Test
  public void testToBoolean() {
    assertThat(TRUE.toBoolean(true)).isTrue();
    assertThat(TRUE.toBoolean(false)).isTrue();

    assertThat(FALSE.toBoolean(true)).isFalse();
    assertThat(FALSE.toBoolean(false)).isFalse();

    assertThat(UNKNOWN.toBoolean(true)).isTrue();
    assertThat(UNKNOWN.toBoolean(false)).isFalse();
  }

  @Test
  public void testToString() {
    assertThat(TRUE.toString()).isEqualTo("true");
    assertThat(FALSE.toString()).isEqualTo("false");
    assertThat(UNKNOWN.toString()).isEqualTo("unknown");
  }
}
