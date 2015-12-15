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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;

import java.util.Map;

/**
 * A record (structural) type.
 *
 * Subtyping: The subtyping of a record type is defined via structural
 * comparison of a record type's properties. For example, a record
 * type of the form { a : TYPE_1 } is a supertype of a record type
 * of the form { b : TYPE_2, a : TYPE_1 } because B can be assigned to
 * A and matches all constraints. Similarly, a defined type can be assigned
 * to a record type so long as that defined type matches all property
 * constraints of the record type. A record type of the form { a : A, b : B }
 * can be assigned to a record of type { a : A }.
 *
 */
public class RecordType extends PrototypeObjectType {
  private static final long serialVersionUID = 1L;

  private final boolean declared;
  private boolean isFrozen = false;

  RecordType(JSTypeRegistry registry, Map<String, RecordProperty> properties) {
    this(registry, properties, true);
  }

  /**
   * Creates a record type.
   *
   * @param registry The type registry under which this type lives.
   * @param properties A map of all the properties of this record type.
   * @param declared Whether this is a declared or synthesized type.
   *     A synthesized record type is just used for bookkeeping
   *     in the type system. A declared record type was actually used in the
   *     user's program.
   * @throws IllegalStateException if the {@code RecordProperty} associated
   *         with a property is null.
   */
  RecordType(JSTypeRegistry registry, Map<String, RecordProperty> properties,
      boolean declared) {
    super(registry, null, null);
    setPrettyPrint(true);
    this.declared = declared;

    for (String property : properties.keySet()) {
      RecordProperty prop = properties.get(property);
      if (prop == null) {
        throw new IllegalStateException(
            "RecordProperty associated with a property should not be null!");
      }
      if (declared) {
        defineDeclaredProperty(
            property, prop.getType(), prop.getPropertyNode());
      } else {
        defineSynthesizedProperty(
            property, prop.getType(), prop.getPropertyNode());
      }
    }
    // Freeze the record type.
    isFrozen = true;
  }

  /** @return Is this synthesized for internal bookkeeping? */
  boolean isSynthetic() {
    return !declared;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    if (isFrozen) {
      return false;
    }

    return super.defineProperty(propertyName, type, inferred,
        propertyNode);
  }

  JSType getGreatestSubtypeHelper(JSType that) {
    if (that.isRecordType()) {
      RecordType thatRecord = that.toMaybeRecordType();
      RecordTypeBuilder builder = new RecordTypeBuilder(registry);
      builder.setSynthesized(true);

      JSType noType = registry.getNativeObjectType(JSTypeNative.NO_TYPE);

      // The greatest subtype consists of those *unique* properties of both
      // record types. If any property conflicts, then the NO_TYPE type
      // is returned.
      for (String property : getOwnPropertyNames()) {
        JSType thisPropertyType = getPropertyType(property);
        JSType propType = null;
        if (thatRecord.hasProperty(property)) {
          JSType thatPropertyType = thatRecord.getPropertyType(property);
          propType = thisPropertyType.getGreatestSubtype(thatPropertyType);
          if (propType.isEquivalentTo(noType)) { return noType; }
        } else {
          propType = thisPropertyType;
        }
        builder.addProperty(property, propType, getPropertyNode(property));
      }

      for (String property : thatRecord.getOwnPropertyNames()) {
        if (!hasProperty(property)) {
          builder.addProperty(property, thatRecord.getPropertyType(property),
              thatRecord.getPropertyNode(property));
        }
      }
      return builder.build();
    }

    JSType greatestSubtype = registry.getNativeType(
        JSTypeNative.NO_OBJECT_TYPE);
    JSType thatRestrictedToObj =
        registry.getNativeType(JSTypeNative.OBJECT_TYPE)
        .getGreatestSubtype(that);
    if (!thatRestrictedToObj.isEmptyType()) {
      // In this branch, the other type is some object type. We find
      // the greatest subtype with the following algorithm:
      // 1) For each property "x" of this record type, take the union
      //    of all classes with a property "x" with a compatible property type.
      //    and which are a subtype of {@code that}.
      // 2) Take the intersection of all of these unions.
      for (String propName : getOwnPropertyNames()) {
        JSType propType = getPropertyType(propName);
        UnionTypeBuilder builder = new UnionTypeBuilder(registry);
        for (ObjectType alt :
          registry.getEachReferenceTypeWithProperty(propName)) {
          JSType altPropType = alt.getPropertyType(propName);
          if (altPropType != null && !alt.isEquivalentTo(this)
              && alt.isSubtype(that) && altPropType.isSubtype(propType)) {
            builder.addAlternate(alt);
          }
        }
        greatestSubtype = greatestSubtype.getLeastSupertype(builder.build());
      }
    }
    return greatestSubtype;
  }

  @Override
  public RecordType toMaybeRecordType() {
    return this;
  }

  @Override
  public boolean isStructuralType() {
    return true;
  }

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create());
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache) {
    if (JSType.isSubtypeHelper(this, that, implicitImplCache)) {
      return true;
    }

    // Top of the record types is the empty record, or OBJECT_TYPE.
    if (registry.getNativeObjectType(
            JSTypeNative.OBJECT_TYPE).isSubtype(that, implicitImplCache)) {
      return true;
    }

    // A type is a subtype of a record type if it itself is a record
    // type and it has at least the same members as the parent record type
    // with the same types.
    if (!that.isRecordType()) {
      return false;
    }

    return this.isStructuralSubtype(that.toMaybeRecordType(), implicitImplCache);
  }
}
