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

import com.google.javascript.rhino.Node;

/**
 * The {@code StaticScope} interface must be implemented by any object that
 * defines variables for the purposes of static analysis.  It is distinguished
 * from the {@code Scriptable} class that Rhino normally uses to represent a
 * run-time scope.
 *
 * @param <T> The type of information stored about the slot
 */
public interface StaticScope<T> {
  /**
   * Returns the root node associated with this scope. May be null.
   */
  Node getRootNode();

  /** Returns the scope enclosing this one or null if none. */
  StaticScope<T> getParentScope();

  /**
   * Returns any defined slot within this scope for this name.  This call
   * continues searching through parent scopes if a slot with this name is not
   * found in the current scope.
   * @param name The name of the variable slot to look up.
   * @return The defined slot for the variable, or {@code null} if no
   *         definition exists.
   */
  StaticSlot<T> getSlot(String name);

  /** Like {@code getSlot} but does not recurse into parent scopes. */
  StaticSlot<T> getOwnSlot(String name);

  /** Returns the expected type of {@code this} in the current scope. */
  T getTypeOfThis();
}
