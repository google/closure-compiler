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

import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;

import java.util.Set;

/**
 * The {@code Unknown} type.
 */
public class UnknownType extends ObjectType {
  private static final long serialVersionUID = 1L;

  // See the explanation of checked unknown types in JSTypeNative.
  private final boolean isChecked;

  UnknownType(JSTypeRegistry registry, boolean isChecked) {
    super(registry);
    this.isChecked = isChecked;
  }

  @Override
  public boolean isUnknownType() {
    return true;
  }

  @Override
  public boolean isCheckedUnknownType() {
    return isChecked;
  }

  @Override
  public boolean canAssignTo(JSType that) {
    return true;
  }

  @Override
  public boolean canBeCalled() {
    return true;
  }

  @Override
  public boolean matchesNumberContext() {
    return true;
  }

  @Override
  public boolean matchesObjectContext() {
    return true;
  }

  @Override
  public boolean matchesStringContext() {
    return true;
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    return UNKNOWN;
  }

  @Override
  public boolean isNullable() {
    return true;
  }

  @Override
  public boolean isSubtype(JSType that) {
    return true;
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    return this;
  }

  @Override
  public JSType getGreatestSubtype(JSType that) {
    return this;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseUnknownType();
  }

  @Override
  public String toString() {
    return getReferenceName();
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, boolean inExterns, Node propertyNode) {
    // nothing to define
    return true;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return null;
  }

  @Override
  public int getPropertiesCount() {
    return Integer.MAX_VALUE;
  }

  @Override
  void collectPropertyNames(Set<String> props) {
  }

  @Override
  public JSType getPropertyType(String propertyName) {
    return this;
  }

  @Override
  public boolean hasProperty(String propertyName) {
    return true;
  }

  @Override
  public FunctionType getConstructor() {
    return null;
  }

  @Override
  public String getReferenceName() {
    return isChecked ? "??" : "?";
  }

  @Override
  public String getDisplayName() {
    return "Unknown";
  }

  @Override
  public boolean hasDisplayName() {
    return true;
  }

  @Override
  public boolean isPropertyTypeDeclared(String propertyName) {
    return false;
  }

  @Override
  public boolean isPropertyTypeInferred(String propertyName) {
    return false;
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.BOTH;
  }

  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    return this;
  }
}
