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

package com.google.javascript.rhino.jstype;

/**
 * A set in the domain {true,false}.
 * There are four possible sets: {}, {true}, {false}, {true,false}.
 *
 */
public enum BooleanLiteralSet {
  EMPTY,
  TRUE,
  FALSE,
  BOTH;

  private BooleanLiteralSet fromOrdinal(int ordinal) {
    switch (ordinal) {
      case 0: return EMPTY;
      case 1: return TRUE;
      case 2: return FALSE;
      case 3: return BOTH;
      default: throw new IllegalArgumentException("Ordinal: " + ordinal);
    }
  }

  /**
   * Computes the intersection of this set and {@code that}.
   */
  public BooleanLiteralSet intersection(BooleanLiteralSet that) {
    return fromOrdinal(this.ordinal() & that.ordinal());
  }

  /**
   * Computes the union of this set and {@code that}.
   */
  public BooleanLiteralSet union(BooleanLiteralSet that) {
    return fromOrdinal(this.ordinal() | that.ordinal());
  }

  /**
   * Returns whether {@code this} contains the given literal value.
   */
  public boolean contains(boolean literalValue) {
    switch (this.ordinal()) {
      case 0: return false;
      case 1: return literalValue;
      case 2: return !literalValue;
      case 3: return true;
      default: throw new IndexOutOfBoundsException("Ordinal: " +
          this.ordinal());
    }
  }

  /**
   * Returns the singleton set {literalValue}.
   */
  public static BooleanLiteralSet get(boolean literalValue) {
    return literalValue ? TRUE : FALSE;
  }
}
