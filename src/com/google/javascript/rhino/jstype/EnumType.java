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

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An enum type representing a branded collection of elements. Each element
 * is referenced by its name, and has an {@link EnumElementType} type.
 */
public class EnumType extends PrototypeObjectType {
  private static final long serialVersionUID = 1L;

  private static final JSTypeClass TYPE_CLASS = JSTypeClass.ENUM;

  // the type of the individual elements
  private EnumElementType elementsType;
  // the elements' names (they all have the same type)
  private final Set<String> elements = new HashSet<>();
  // the node representing rhs of the enum
  private final Node source;

  /**
   * Creates an enum type.
   *
   * @param name the enum's name
   * @param source the object literal that creates the enum, a reference to another enum, or null.
   * @param elementsType the base type of the individual elements
   */
  EnumType(JSTypeRegistry registry, String name, Node source, JSType elementsType) {
    super(PrototypeObjectType.builder(registry).setName("enum{" + name + "}"));
    this.elementsType = new EnumElementType(registry, elementsType, name, this);
    this.source = source;

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  public EnumType toMaybeEnumType() {
    return this;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }

  /**
   * Gets the elements defined on this enum.
   * @return the elements' names defined on this enum. The returned set is
   *         immutable.
   */
  public Set<String> getElements() {
    return Collections.unmodifiableSet(elements);
  }

  /**
   * Defines a new element on this enum.
   * @param name the name of the new element
   * @param definingNode the {@code Node} that defines this new element
   * @return true iff the new element is added successfully
   */
  public boolean defineElement(String name, Node definingNode) {
    elements.add(name);
    return defineDeclaredProperty(name, elementsType, definingNode);
  }

  /** Gets the elements' type, which is a subtype of the enumerated type. */
  public EnumElementType getElementsType() {
    return elementsType;
  }

  /** Gets the enumerated type. */
  @Override
  public JSType getEnumeratedTypeOfEnumObject() {
    return elementsType.getPrimitiveType();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }
    return this.equals(that) ? TRUE : FALSE;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    sb.append(sb.isForAnnotations() ? "!Object" : getReferenceName());
  }

  @Override
  public String getDisplayName() {
    return elementsType.getDisplayName();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseObjectType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseObjectType(this, that);
  }

  @Override
  public FunctionType getConstructor() {
    return null;
  }

  @Override
  public boolean matchesNumberContext() {
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
  JSType resolveInternal(ErrorReporter reporter) {
    elementsType = (EnumElementType) elementsType.resolve(reporter);
    return super.resolveInternal(reporter);
  }
}
