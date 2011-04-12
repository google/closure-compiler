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

import static com.google.javascript.rhino.jstype.BooleanLiteralSet.BOTH;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.EMPTY;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.FALSE;
import static com.google.javascript.rhino.jstype.BooleanLiteralSet.TRUE;

import junit.framework.TestCase;

/**
 * Tests {@link BooleanLiteralSet}.
 *
 */
public class BooleanLiteralSetTest extends TestCase {

  public void testIntersection() {
    assertEquals(EMPTY, EMPTY.intersection(EMPTY));
    assertEquals(EMPTY, EMPTY.intersection(TRUE));
    assertEquals(EMPTY, EMPTY.intersection(FALSE));
    assertEquals(EMPTY, EMPTY.intersection(BOTH));
    assertEquals(EMPTY, TRUE.intersection(EMPTY));
    assertEquals(TRUE, TRUE.intersection(TRUE));
    assertEquals(EMPTY, TRUE.intersection(FALSE));
    assertEquals(TRUE, TRUE.intersection(BOTH));
    assertEquals(EMPTY, FALSE.intersection(EMPTY));
    assertEquals(EMPTY, FALSE.intersection(TRUE));
    assertEquals(FALSE, FALSE.intersection(FALSE));
    assertEquals(FALSE, FALSE.intersection(BOTH));
    assertEquals(EMPTY, BOTH.intersection(EMPTY));
    assertEquals(TRUE, BOTH.intersection(TRUE));
    assertEquals(FALSE, BOTH.intersection(FALSE));
    assertEquals(BOTH, BOTH.intersection(BOTH));
  }

  public void testUnion() {
    assertEquals(EMPTY, EMPTY.union(EMPTY));
    assertEquals(TRUE, EMPTY.union(TRUE));
    assertEquals(FALSE, EMPTY.union(FALSE));
    assertEquals(BOTH, EMPTY.union(BOTH));
    assertEquals(TRUE, TRUE.union(EMPTY));
    assertEquals(TRUE, TRUE.union(TRUE));
    assertEquals(BOTH, TRUE.union(FALSE));
    assertEquals(BOTH, TRUE.union(BOTH));
    assertEquals(FALSE, FALSE.union(EMPTY));
    assertEquals(BOTH, FALSE.union(TRUE));
    assertEquals(FALSE, FALSE.union(FALSE));
    assertEquals(BOTH, FALSE.union(BOTH));
    assertEquals(BOTH, BOTH.union(EMPTY));
    assertEquals(BOTH, BOTH.union(TRUE));
    assertEquals(BOTH, BOTH.union(FALSE));
    assertEquals(BOTH, BOTH.union(BOTH));
  }

  public void testGet() {
    assertEquals(TRUE, BooleanLiteralSet.get(true));
    assertEquals(FALSE, BooleanLiteralSet.get(false));
  }

  public void testContains() {
    assertFalse(EMPTY.contains(true));
    assertFalse(EMPTY.contains(false));
    assertTrue(TRUE.contains(true));
    assertFalse(TRUE.contains(false));
    assertFalse(FALSE.contains(true));
    assertTrue(FALSE.contains(false));
    assertTrue(BOTH.contains(true));
    assertTrue(BOTH.contains(false));
  }
}
