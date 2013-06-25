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
 * An unresolved type that was forward declared. So we know it exists,
 * but that it wasn't pulled into this binary.
 *
 * In most cases, it behaves like a bottom type in the type lattice:
 * no real type should be assigned to a NoResolvedType, but the
 * NoResolvedType is a subtype of everything. In a few cases, it behaves
 * like the unknown type: properties of this type are also NoResolved types,
 * and comparisons to other types always have an unknown result.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class NoResolvedType extends NoType {
  private static final long serialVersionUID = 1L;

  NoResolvedType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  public boolean isNoResolvedType() {
    return true;
  }

  @Override
  public boolean isNoType() {
    return false;
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @Override
  public boolean isSubtype(JSType that) {
    if (JSType.isSubtypeHelper(this, that)) {
      return true;
    } else {
      return !that.isNoType();
    }
  }

  @Override
  String toStringHelper(boolean forAnnotations) {
    return forAnnotations ? "?" : "NoResolvedType";
  }
}
