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

import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.TRUE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

/**
 * Void type whose only element is the {@code undefined} value.
 */
public class VoidType extends ValueType {
  private static final long serialVersionUID = 1L;

  VoidType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  public JSType restrictByNotNullOrUndefined() {
    return registry.getNativeType(JSTypeNative.NO_TYPE);
  }

  @Override
  public JSType restrictByNotUndefined() {
    return registry.getNativeType(JSTypeNative.NO_TYPE);
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    if (UNKNOWN.equals(super.testForEquality(that))) {
      return UNKNOWN;
    }
    if (that.isSubtypeOf(this) || that.isSubtypeOf(getNativeType(JSTypeNative.NULL_TYPE))) {
      return TRUE;
    }
    return FALSE;
  }

  @Override
  public boolean matchesNumberContext() {
    return false;
  }

  @Override
  public boolean matchesObjectContext() {
    return false;
  }

  @Override
  public boolean matchesStringContext() {
    return true;
  }

  @Override
  public boolean isVoidType() {
    return true;
  }

  @Override
  public boolean isVoidable() {
    return true;
  }

  @Override
  public boolean isExplicitlyVoidable() {
    return true;
  }

  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    return sb.append(getDisplayName());
  }

  @Override
  public String getDisplayName() {
    return "undefined";
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.FALSE;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseVoidType();
  }
}
