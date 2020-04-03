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

import com.google.javascript.rhino.ErrorReporter;

/**
 * A minimal implementation of {@link JSType} for unit tests and nothing else.
 *
 * <p>This class has no innate behaviour. It is intended as a stand-in for testing behaviours on
 * {@link JSType} that require a concrete instance. Test cases are responsible for any
 * configuration.
 *
 * <p>This class is defined under "javatests" because no {@link JSType} subclass should exist
 * outside the "jstype" package.
 */
class UnitTestingJSType extends JSType {

  UnitTestingJSType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  JSTypeClass getTypeClass() {
    throw new UnsupportedOperationException();
  }

  @Override
  int recursionUnsafeHashCode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    throw new UnsupportedOperationException();
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    return this;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    throw new UnsupportedOperationException();
  }
}
