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

import com.google.javascript.rhino.ErrorReporter;

/**
 * Value types (null, void, number, boolean, string).
 */
abstract class ValueType extends JSType {
  ValueType(JSTypeRegistry registry) {
    super(registry);
    this.eagerlyResolveToSelf();
  }

  @Override
  final JSType resolveInternal(ErrorReporter reporter) {
    throw new AssertionError();
  }

  @Override
  final void appendTo(TypeStringBuilder sb) {
    sb.append(this.getDisplayName());
  }

  // Subclasses must override and return non-null.
  @Override
  public abstract String getDisplayName();

  @Override
  public boolean hasDisplayName() {
    return true;
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseValueType(this, that);
  }

  @Override
  public HasPropertyKind getPropertyKind(String propertyName, boolean autobox) {
    if (autobox && isBoxableScalar()) {
      return autoboxesTo().getPropertyKind(propertyName, autobox);
    } else {
      return super.getPropertyKind(propertyName, autobox);
    }
  }

  @Override
  final int recursionUnsafeHashCode() {
    // Subclasses of this type are unique within a JSTypeRegisty.
    return System.identityHashCode(this);
  }
}
