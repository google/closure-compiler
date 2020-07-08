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
 *   Brock Smickley
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
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

/** BigInt type. */
public class BigIntType extends ValueType {
  private static final long serialVersionUID = 1L;

  BigIntType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  JSTypeClass getTypeClass() {
    return JSTypeClass.BIGINT;
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }
    if (that.isUnknownType()
        || that.isSubtypeOf(getNativeType(JSTypeNative.OBJECT_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.NUMBER_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.STRING_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.BOOLEAN_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.BIGINT_TYPE))) {
      return UNKNOWN;
    }
    return FALSE;
  }

  @Override
  public boolean isBigIntValueType() {
    return true;
  }

  @Override
  public boolean matchesNumberContext() {
    // BigInt cannot be coerced to a Number
    return false;
  }

  @Override
  public boolean matchesStringContext() {
    return true;
  }

  @Override
  public boolean matchesObjectContext() {
    return true;
  }

  @Override
  public String getDisplayName() {
    return "bigint";
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.BOTH;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseBigIntType();
  }

  @Override
  public JSType autoboxesTo() {
    return getNativeType(JSTypeNative.BIGINT_OBJECT_TYPE);
  }
}
