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

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;

/**
 * An interning pool for strings used by the Rhino package.
 *
 * <p>As of 2021-03-16, this custom pool is measurably more performant than `String::intern`. Some
 * reasons for this are presented at https://shipilev.net/jvm/anatomy-quarks/10-string-intern/.
 */
public final class RhinoStringPool {

  /**
   * The threadsafe datastructure that backs this pool.
   *
   * <p>We use weak-refs, rather than strong-refs, to prevent a memory leak in server-like
   * applications.
   */
  private static final Interner<String> INTERNER = Interners.newWeakInterner();

  /**
   * Check if two strings are the same according to interning.
   *
   * <p>The caller is responsible for ensuring that strings passed to this method are actually
   * interned. This method mainly exists to highlight where equality checks depend on interning for
   * correctness.
   *
   * <p>Ideally we could use something like a branded-type, for interned strings, to verify correct
   * usage. However, Java doesn't support type-brands, and using like wrapper objects would
   * undermine performance. This also needs to be ergonomic enough that callers don't resort to
   * using `==` directly.
   */
  public static boolean uncheckedEquals(String a, String b) {
    return identical(a, b);
  }

  static String addOrGet(String s) {
    return INTERNER.intern(s);
  }

  private RhinoStringPool() {}
}
