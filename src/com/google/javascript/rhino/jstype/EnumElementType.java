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
import com.google.javascript.rhino.Node;

/**
 * The type of individual elements of an enum type
 * (see {@link EnumType}).
 */
public class EnumElementType extends ObjectType {
  private static final long serialVersionUID = 1L;

  /**
   * The primitive type this enum element type wraps. For instance, in
   * the following code defining the {@code LOCAL_CODES} enum
   * <pre>var LOCAL_CODES = {A: 3, B: 9, C: 8}</pre>
   * the primitive type of the the constants is {@code number}.
   */
  private JSType primitiveType;

  // The primitive type, if it is an object.
  private ObjectType primitiveObjectType;

  private final String name;

  private final EnumType enumType;

  EnumElementType(JSTypeRegistry registry, JSType elementType,
      String name, EnumType enumType) {
    super(registry);
    this.primitiveType = elementType;
    this.primitiveObjectType = elementType.toObjectType();
    this.name = name;
    this.enumType = enumType;
  }

  public EnumType getEnumType() {
    return enumType;
  }

  @Override
  public HasPropertyKind getPropertyKind(String propertyName, boolean autobox) {
    return primitiveType.getPropertyKind(propertyName, autobox);
  }

  @Override
  public PropertyMap getPropertyMap() {
    return primitiveObjectType == null
        ? PropertyMap.immutableEmptyMap()
        : primitiveObjectType.getPropertyMap();
  }

  @Override
  public EnumElementType toMaybeEnumElementType() {
    return this;
  }

  @Override
  public boolean matchesNumberContext() {
    return primitiveType.matchesNumberContext();
  }

  @Override
  public boolean matchesStringContext() {
    return primitiveType.matchesStringContext();
  }

  @Override
  public boolean matchesObjectContext() {
    return primitiveType.matchesObjectContext();
  }

  @Override
  public boolean canBeCalled() {
    return primitiveType.canBeCalled();
  }

  @Override
  public boolean isObject() {
    return primitiveType.isObject();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    return primitiveType.testForEquality(that);
  }

  @Override
  public boolean isNullable() {
    return primitiveType.isNullable();
  }

  @Override
  public boolean isVoidable() {
    return primitiveType.isVoidable();
  }


  @Override
  public boolean isNominalType() {
    return hasReferenceName();
  }

  @Override
  int recursionUnsafeHashCode() {
    return NamedType.nominalHashCode(this);
  }

  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    if (forAnnotations) {
      // TODO(dimvar): this should use getReferenceName() instead of this.primitiveType
      return sb.append(this.primitiveType);
    }
    return sb.append(getReferenceName()).append("<").append(this.primitiveType).append(">");
  }

  @Override
  public String getReferenceName() {
    return name;
  }

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create(), SubtypingMode.NORMAL);
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    if (JSType.isSubtypeHelper(this, that, implicitImplCache, subtypingMode)) {
      return true;
    } else {
      return primitiveType.isSubtype(that, implicitImplCache, subtypingMode);
    }
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseEnumElementType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseEnumElementType(this, that);
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    // nothing
    return true;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return null;
  }

  @Override
  protected JSType findPropertyTypeWithoutConsideringTemplateTypes(String propertyName) {
    return primitiveType.findPropertyType(propertyName);
  }

  @Override
  public FunctionType getConstructor() {
    return primitiveObjectType == null ?
        null : primitiveObjectType.getConstructor();
  }

  @Override
  public JSType autoboxesTo() {
    return primitiveType.autoboxesTo();
  }

  /**
   * Gets the primitive type of this enum element.
   */
  public JSType getPrimitiveType() {
    return primitiveType;
  }

  /**
   * Returns the infimum of a enum element type and another type, or null
   * if the infimum is empty.
   *
   * This can be a little bit weird. For example, suppose you have an enum
   * of {(string|number)}, and you want the greatest subtype of the enum
   * and a {number}.
   *
   * The infimum is non-empty. But at the same time, we don't really have
   * a name for this infimum. It's equivalent to "elements of this enum that
   * are numbers".
   *
   * The best we can do is make up a new type. This is similar to what
   * we do in UnionType#meet, which kind-of-sort-of makes sense, because
   * an EnumElementType is a union of instances of a type.
   */
  JSType meet(JSType that) {
    JSType meetPrimitive = primitiveType.getGreatestSubtype(that);
    if (meetPrimitive.isEmptyType()) {
      return null;
    } else {
      return new EnumElementType(registry, meetPrimitive, name, getEnumType());
    }
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    primitiveType = primitiveType.resolve(reporter);
    primitiveObjectType = ObjectType.cast(primitiveType);
    return this;
  }
}
