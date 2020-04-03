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

import static com.google.javascript.rhino.jstype.JSTypeNative.SYMBOL_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.SYMBOL_TYPE;
import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;


/**
 * Symbol type.
 * @author johnlenz@google.com (John Lenz)
 */
public final class SymbolType extends ValueType {
  private static final long serialVersionUID = 1L;

  SymbolType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  JSTypeClass getTypeClass() {
    return JSTypeClass.SYMBOL;
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }

    if (that.canCastTo(getNativeType(SYMBOL_TYPE))
        || that.canCastTo(getNativeType(SYMBOL_OBJECT_TYPE))) {
      return UNKNOWN;
    }
    return FALSE;
  }

  @Override
  public boolean isSymbolValueType() {
    return true;
  }

  @Override
  public boolean matchesNumberContext() {
    return false;
  }

  @Override
  public boolean matchesStringContext() {
    return false;
  }

  @Override
  public boolean matchesSymbolContext() {
    return true;
  }

  @Override
  public boolean matchesObjectContext() {
    return true;
  }

  @Override
  public String getDisplayName() {
    return "symbol";
  }

  @Override
  public JSType autoboxesTo() {
    return getNativeType(SYMBOL_OBJECT_TYPE);
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.TRUE;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseSymbolType();
  }
}
