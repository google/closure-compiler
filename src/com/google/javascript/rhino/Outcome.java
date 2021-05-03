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
 *   Annie Wang
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

import com.google.javascript.jscomp.base.Tri;

/**
 * An enum for representing truthiness and nullishness of outcomes. The values are TRUE, FALSE,
 * FALSE_NOT_NULL, and NULLISH. FALSE represents all falsy values so it contains both FALSE_NOT_NULL
 * and NULLISH. It specifically differentiates between falsy and explicitly nullish values. e.g. 0
 * is falsy but not nullish.
 */
public enum Outcome {
  /** Represents truthy values. For example: {}, true, 1, etc. */
  TRUE {
    @Override
    public boolean isTruthy() {
      return true;
    }

    @Override
    public Tri isNullish() {
      return Tri.FALSE;
    }

    @Override
    public Outcome not() {
      return Outcome.FALSE;
    }
  },

  /** Represents falsy values. For examples: '', 0, false, null, etc. */
  FALSE {
    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public Tri isNullish() {
      return Tri.UNKNOWN;
    }

    @Override
    public Outcome not() {
      return Outcome.TRUE;
    }
  },

  FALSE_NOT_NULL {
    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public Tri isNullish() {
      return Tri.FALSE;
    }

    @Override
    public Outcome not() {
      return Outcome.TRUE;
    }
  },

  NULLISH {
    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public Tri isNullish() {
      return Tri.TRUE;
    }

    @Override
    public Outcome not() {
      return Outcome.TRUE;
    }
  };

  /** Determines whether an Outcome enum value is truthy. */
  public abstract boolean isTruthy();

  /**
   * Determines whether an Outcome enum value is nullish. Using Tri instead of a boolean because 0
   * is Outcome.FALSE but not nullish so sometimes it is unclear.
   */
  public abstract Tri isNullish();

  /** Gets the {@code not} of {@code this}. */
  public abstract Outcome not();

  /** Gets the Outcome for the given boolean. */
  public static Outcome forBoolean(boolean val) {
    return val ? Outcome.TRUE : Outcome.FALSE;
  }
}
