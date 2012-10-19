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
 * Represents different ways for comparing equality among types.
 * @author nicksantos@google.com (Nick Santos)
 */
enum EquivalenceMethod {
  /**
   * Indicates that the two types should behave exactly the same under
   * all type operations.
   *
   * Thus, {string} != {?} and {Unresolved} != {?}
   */
  IDENTITY,

  /**
   * Indicates that the two types are almost exactly the same, and that a
   * data flow analysis algorithm comparing them should consider them equal.
   *
   * In traditional type inference, the types form a finite lattice, and this
   * ensures that type inference will terminate.
   *
   * In our type system, the unknown types do not obey the lattice rules. So
   * if we continue to perform inference over the unknown types, we may
   * never terminate.
   *
   * By treating all unknown types as equivalent for the purposes of data
   * flow analysis, we ensure that the algorithm will terminate.
   *
   * Thus, {string} != {?} and {Unresolved} ~= {?}
   */
  DATA_FLOW,

  /**
   * Indicates that two types are invariant.
   *
   * In a type system without unknown types, this would be the same
   * as IDENTITY. But we always want to consider type A invariant with type B
   * if B is unknown.
   *
   * Thus, {string} ~= {?} and {Unresolved} ~= {?}
   */
  INVARIANT
}
