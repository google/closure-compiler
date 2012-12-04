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
import java.util.Set;

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
class RecordType extends PrototypeObjectType {
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

  boolean checkRecordEquivalenceHelper(
      RecordType otherRecord, EquivalenceMethod eqMethod) {
    Set<String> keySet = getOwnPropertyNames();
    Set<String> otherKeySet = otherRecord.getOwnPropertyNames();
    if (!otherKeySet.equals(keySet)) {
      return false;
    }
    for (String key : keySet) {
      if (!otherRecord.getPropertyType(key).checkEquivalenceHelper(
              getPropertyType(key), eqMethod)) {
        return false;
      }
    }
    return true;
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

      // The greatest subtype consists of those *unique* properties of both
      // record types. If any property conflicts, then the NO_TYPE type
      // is returned.
      for (String property : getOwnPropertyNames()) {
        if (thatRecord.hasProperty(property) &&
            !thatRecord.getPropertyType(property).isInvariant(
                getPropertyType(property))) {
          return registry.getNativeObjectType(JSTypeNative.NO_TYPE);
        }

        builder.addProperty(property, getPropertyType(property),
            getPropertyNode(property));
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
          if (altPropType != null && !alt.isEquivalentTo(this) &&
              alt.isSubtype(that) &&
              propType.isInvariant(altPropType)) {
            builder.addAlternate(alt);
          }
        }
        greatestSubtype = greatestSubtype.getLeastSupertype(builder.build());
      }
    }
    return greatestSubtype;
  }

  @Override
  RecordType toMaybeRecordType() {
    return this;
  }

  @Override
  public boolean isSubtype(JSType that) {
    if (JSType.isSubtypeHelper(this, that)) {
      return true;
    }

    // Top of the record types is the empty record, or OBJECT_TYPE.
    if (registry.getNativeObjectType(
            JSTypeNative.OBJECT_TYPE).isSubtype(that)) {
      return true;
    }

    // A type is a subtype of a record type if it itself is a record
    // type and it has at least the same members as the parent record type
    // with the same types.
    if (!that.isRecordType()) {
      return false;
    }

    return RecordType.isSubtype(this, that.toMaybeRecordType());
  }

  /** Determines if typeA is a subtype of typeB */
  static boolean isSubtype(ObjectType typeA, RecordType typeB) {
    // typeA is a subtype of record type typeB iff:
    // 1) typeA has all the properties declared in typeB.
    // 2) And for each property of typeB,
    //    2a) if the property of typeA is declared, it must be equal
    //        to the type of the property of typeB,
    //    2b) otherwise, it must be a subtype of the property of typeB.
    //
    // To figure out why this is true, consider the following pseudo-code:
    // /** @type {{a: (Object,null)}} */ var x;
    // /** @type {{a: !Object}} */ var y;
    // var z = {a: {}};
    // x.a = null;
    //
    // y cannot be assigned to x, because line 4 would violate y's declared
    // properties. But z can be assigned to x. Even though z and y are the
    // same type, the properties of z are inferred--and so an assignment
    // to the property of z would not violate any restrictions on it.
    for (String property : typeB.getOwnPropertyNames()) {
      if (!typeA.hasProperty(property)) {
        return false;
      }

      JSType propA = typeA.getPropertyType(property);
      JSType propB = typeB.getPropertyType(property);
      if (typeA.isPropertyTypeDeclared(property)) {
        // If one declared property isn't invariant,
        // then the whole record isn't covariant.
        if (!propA.isInvariant(propB)) {
          return false;
        }
      } else {
        // If one inferred property isn't a subtype,
        // then the whole record isn't covariant.
        if (!propA.isSubtype(propB)) {
          return false;
        }
      }
    }

    return true;
  }
}
