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

package com.google.javascript.rhino;

/**
 * Represents a position in some piece of source code, with an associated
 * item of type T found at that position.
 *
 */
public abstract class SourcePosition<T> {
  /**
   * The (well typed) item found at the source position.
   */
  private T item = null;

  /**
   * The starting line number.
   */
  private int startLineno = 0;

  /**
   * The character position on the starting line.
   */
  private int startCharno = 0;

  /**
   * The ending line number.
   */
  private int endLineno = 0;

  /**
   * The character position on the ending line.
   */
  private int endCharno = 0;

  /**
   * Sets the item that this source position references.
   */
  public void setItem(T item) {
    this.item = item;
  }

  /**
   * Sets the position information contained in this source position.
   */
  public void setPositionInformation(int startLineno, int startCharno,
                                     int endLineno, int endCharno) {
    if (startLineno == endLineno) {
      if (startCharno >= endCharno) {
        throw new IllegalStateException(
            "Recorded bad position information\n" +
            "start-char: " + startCharno + "\n" +
            "end-char: " + endCharno);
      }
    } else {
      if (startLineno > endLineno) {
        throw new IllegalStateException(
            "Recorded bad position information\n" +
            "start-line: " + startLineno + "\n" +
            "end-line: " + endLineno);
      }
    }

    this.startLineno = startLineno;
    this.startCharno = startCharno;
    this.endLineno = endLineno;
    this.endCharno = endCharno;
  }

  /**
   * Returns the item found at this source position.
   */
  public T getItem() {
    return item;
  }

  /**
   * Returns the starting line number of this position.
   */
  public int getStartLine() {
    return startLineno;
  }

  /**
   * Returns the character position on the starting line.
   */
  public int getPositionOnStartLine() {
    return startCharno;
  }

  /**
   * Returns the ending line number of this position.
   */
  public int getEndLine() {
    return endLineno;
  }

  /**
   * Returns the character position on the ending line.
   */
  public int getPositionOnEndLine() {
    return endCharno;
  }
}
