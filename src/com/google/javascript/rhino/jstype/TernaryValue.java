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
 * <p>An enum for ternary logic. The {@link #TRUE} and {@link #FALSE} values
 * are equivalent to typical booleans, and the {@link #UNKNOWN} value plays the
 * role of a placeholder, which can be either {@link #TRUE} or
 * {@link #FALSE}.</p>
 *
 * <p>A ternary value expression evaluates to {@link #TRUE} or
 * {@link #FALSE} only if all replacements of {@link #UNKNOWN} in this
 * expression yield the same result. Therefore, the ternary logic coincides
 * with typical Boolean logic if the {@link #UNKNOWN} value is not
 * present in an expression.</p>
 *
 * @see <a href="http://en.wikipedia.org/wiki/Ternary_logic">Ternary Logic</a>
 */
public enum TernaryValue {
  /**
   * {@code false}
   */
  FALSE {
    @Override
    public TernaryValue and(TernaryValue that) {
      return FALSE;
    }

    @Override
    public TernaryValue not() {
      return TRUE;
    }

    @Override
    public TernaryValue or(TernaryValue that) {
      return that;
    }

    @Override
    public TernaryValue xor(TernaryValue that) {
      return that;
    }

    @Override
    public boolean toBoolean(boolean unknown) {
      return false;
    }

    @Override
    public String toString() {
      return "false";
    }
  },

  /**
   * {@code true}
   */
  TRUE {
    @Override
    public TernaryValue and(TernaryValue that) {
      return that;
    }

    @Override
    public TernaryValue not() {
      return FALSE;
    }

    @Override
    public TernaryValue or(TernaryValue that) {
      return TRUE;
    }

    @Override
    public TernaryValue xor(TernaryValue that) {
      return that.not();
    }

    @Override
    public boolean toBoolean(boolean unknown) {
      return true;
    }

    @Override
    public String toString() {
      return "true";
    }
  },

  /**
   * {@code unknown}, it represents lack of knowledge about whether this value
   * is {@code true} or {@code false}.
   */
  UNKNOWN {
    @Override
    public TernaryValue and(TernaryValue that) {
      return (FALSE.equals(that)) ? FALSE :  UNKNOWN;
    }

    @Override
    public TernaryValue not() {
      return UNKNOWN;
    }

    @Override
    public TernaryValue or(TernaryValue that) {
      return (TRUE.equals(that)) ? TRUE : UNKNOWN;
    }

    @Override
    public TernaryValue xor(TernaryValue that) {
      return UNKNOWN;
    }

    @Override
    public boolean toBoolean(boolean unknown) {
      return unknown;
    }

    @Override
    public String toString() {
      return "unknown";
    }
  };

  /**
   * Gets the {@code and} of {@code this} and {@code that}.
   */
  public abstract TernaryValue and(TernaryValue that);

  /**
   * Gets the {@code not} of {@code this}.
   */
  public abstract TernaryValue not();

  /**
   * Gets the {@code or} of {@code this} and {@code that}.
   */
  public abstract TernaryValue or(TernaryValue that);

  /**
   * Gets the {@code xor} of {@code this} and {@code that}.
   */
  public abstract TernaryValue xor(TernaryValue that);

  /**
   * Converts {@code this} ternary value to boolean. The {@code #TRUE} and
   * {@code #FALSE} values are simply converted to {@code true} and
   * {@code false} respectively, whilst the {@link #UNKNOWN} is converted
   * to the specified {@code unknown} value.
   *
   * @param unknown the boolean value to which the {@link #UNKNOWN} value is
   *     converted
   * @return <pre>return
   *     this == TRUE ? true :
   *     this == FALSE ? false :
   *     unknown</pre>
   */
  public abstract boolean toBoolean(boolean unknown);

  /**
   * Gets the TernaryValue for the given boolean.
   */
  public static TernaryValue forBoolean(boolean val) {
    return val ? TernaryValue.TRUE : TernaryValue.FALSE;
  }
}
