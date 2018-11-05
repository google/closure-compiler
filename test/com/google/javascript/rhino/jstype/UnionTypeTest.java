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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UnionTypeTest extends BaseJSTypeTestCase {
  private static final MapBasedScope EMPTY_SCOPE = MapBasedScope.emptyScope();

  private NamedType unresolvedNamedType;
  private ObjectType base;
  private ObjectType sub1;
  private ObjectType sub2;
  private ObjectType sub3;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    unresolvedNamedType =
        new NamedType(EMPTY_SCOPE, registry, "not.resolved.named.type", null, -1, -1);

    this.base =
        new FunctionBuilder(registry).forConstructor().withName("Base").build().getInstanceType();
    this.sub1 =
        new FunctionBuilder(registry)
            .forConstructor()
            .withName("Sub1")
            .withPrototypeBasedOn(base)
            .build()
            .getInstanceType();
    this.sub2 =
        new FunctionBuilder(registry)
            .forConstructor()
            .withName("Sub2")
            .withPrototypeBasedOn(base)
            .build()
            .getInstanceType();
    this.sub3 =
        new FunctionBuilder(registry)
            .forConstructor()
            .withName("Sub3")
            .withPrototypeBasedOn(base)
            .build()
            .getInstanceType();
  }

  /**
   * Assert that a type can assign to itself.
   */
  private void assertTypeCanAssignToItself(JSType type) {
    assertThat(type.isSubtypeOf(type)).isTrue();
  }

  /** Tests the behavior of variants type. */
  @SuppressWarnings("checked")
  @Test
  public void testUnionType() {
    UnionType nullOrString =
        (UnionType) createUnionType(NULL_TYPE, STRING_OBJECT_TYPE);
    UnionType stringOrNull =
        (UnionType) createUnionType(STRING_OBJECT_TYPE, NULL_TYPE);

    assertType(stringOrNull).isStructurallyEqualTo(nullOrString);
    assertType(nullOrString).isStructurallyEqualTo(stringOrNull);

    assertTypeCanAssignToItself(createUnionType(VOID_TYPE, NUMBER_TYPE));
    assertTypeCanAssignToItself(
        createUnionType(NUMBER_TYPE, STRING_TYPE, OBJECT_TYPE));
    assertTypeCanAssignToItself(createUnionType(NUMBER_TYPE, BOOLEAN_TYPE));
    assertTypeCanAssignToItself(createUnionType(VOID_TYPE));

    UnionType nullOrUnknown =
        (UnionType) createUnionType(NULL_TYPE, unresolvedNamedType);
    assertThat(nullOrUnknown.isUnknownType()).isTrue();
    assertType(NULL_TYPE.getLeastSupertype(nullOrUnknown)).isStructurallyEqualTo(nullOrUnknown);
    assertType(nullOrUnknown.getLeastSupertype(NULL_TYPE)).isStructurallyEqualTo(nullOrUnknown);
    assertType(NULL_TYPE.getGreatestSubtype(nullOrUnknown)).isStructurallyEqualTo(UNKNOWN_TYPE);
    assertType(nullOrUnknown.getGreatestSubtype(NULL_TYPE)).isStructurallyEqualTo(UNKNOWN_TYPE);

    assertThat(NULL_TYPE.differsFrom(nullOrUnknown)).isTrue();
    assertThat(nullOrUnknown.differsFrom(NULL_TYPE)).isTrue();
    assertThat(nullOrUnknown.differsFrom(unresolvedNamedType)).isFalse();

    assertThat(NULL_TYPE.isSubtypeOf(nullOrUnknown)).isTrue();
    assertThat(unresolvedNamedType.isSubtype(nullOrUnknown)).isTrue();
    assertThat(nullOrUnknown.isSubtype(NULL_TYPE)).isTrue();

    assertType(nullOrUnknown.restrictByNotNullOrUndefined())
        .isStructurallyEqualTo(unresolvedNamedType);

    // findPropertyType
    assertType(nullOrString.findPropertyType("length")).isStructurallyEqualTo(NUMBER_TYPE);
    assertThat(nullOrString.findPropertyType("lengthx")).isNull();

    Asserts.assertResolvesToSame(nullOrString);
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on union types. */
  @Test
  public void testGreatestSubtypeUnionTypes1() {
    assertType(createNullableType(STRING_TYPE).getGreatestSubtype(createNullableType(NUMBER_TYPE)))
        .isStructurallyEqualTo(NULL_TYPE);
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on union types. */
  @SuppressWarnings("checked")
  @Test
  public void testGreatestSubtypeUnionTypes2() {
    UnionType subUnion = (UnionType) createUnionType(sub1, sub2);
    assertType(subUnion.getGreatestSubtype(base)).isStructurallyEqualTo(subUnion);
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on union types. */
  @SuppressWarnings("checked")
  @Test
  public void testGreatestSubtypeUnionTypes3() {
    // (number,undefined,null)
    UnionType nullableOptionalNumber =
        (UnionType) createUnionType(NULL_TYPE, VOID_TYPE, NUMBER_TYPE);
    // (null,undefined)
    UnionType nullUndefined =
        (UnionType) createUnionType(VOID_TYPE, NULL_TYPE);
    assertType(nullUndefined.getGreatestSubtype(nullableOptionalNumber))
        .isStructurallyEqualTo(nullUndefined);
    assertType(nullableOptionalNumber.getGreatestSubtype(nullUndefined))
        .isStructurallyEqualTo(nullUndefined);
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on union types. */
  @Test
  public void testGreatestSubtypeUnionTypes4() {
    UnionType union = (UnionType) createUnionType(NULL_TYPE, sub1, sub2);
    assertType(union.getGreatestSubtype(base)).isStructurallyEqualTo(createUnionType(sub1, sub2));
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on union types. */
  @Test
  public void testGreatestSubtypeUnionTypes5() {
    JSType subUnion = createUnionType(sub1, sub2);
    assertType(subUnion.getGreatestSubtype(STRING_OBJECT_TYPE))
        .isStructurallyEqualTo(NO_OBJECT_TYPE);
  }

  /** Tests subtyping of union types. */
  @Test
  public void testSubtypingUnionTypes() {
    // subtypes
    assertThat(BOOLEAN_TYPE.isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE))).isTrue();
    assertThat(
            createUnionType(BOOLEAN_TYPE, STRING_TYPE)
                .isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE)))
        .isTrue();
    assertThat(
            createUnionType(BOOLEAN_TYPE, STRING_TYPE)
                .isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(
            createUnionType(BOOLEAN_TYPE, STRING_TYPE)
                .isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(
            createUnionType(BOOLEAN_TYPE)
                .isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(
            createUnionType(STRING_TYPE)
                .isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(createUnionType(STRING_TYPE, NULL_TYPE).isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(createUnionType(DATE_TYPE, REGEXP_TYPE).isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(createUnionType(sub1, sub2).isSubtypeOf(base)).isTrue();
    assertThat(createUnionType(sub1, sub2).isSubtypeOf(OBJECT_TYPE)).isTrue();

    // not subtypes
    assertThat(createUnionType(STRING_TYPE, NULL_TYPE).isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(createUnionType(STRING_TYPE, NULL_TYPE).isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(createUnionType(NO_OBJECT_TYPE, NULL_TYPE).isSubtypeOf(OBJECT_TYPE)).isFalse();

    // defined unions
    assertThat(NUMBER_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING)).isTrue();
    assertThat(OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING)).isTrue();
    assertThat(STRING_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING)).isTrue();

    assertThat(NUMBER_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(STRING_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN)).isTrue();

    assertThat(NUMBER_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(STRING_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN)).isTrue();
  }

  /**
   * Tests that special union types can assign to other types. Unions containing the unknown type
   * should be able to assign to any other type.
   */
  @SuppressWarnings("checked")
  @Test
  public void testSpecialUnionCanAssignTo() {
    // autoboxing quirks
    UnionType numbers = (UnionType) createUnionType(NUMBER_TYPE, NUMBER_OBJECT_TYPE);
    assertThat(numbers.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(numbers.isSubtype(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(numbers.isSubtype(sub1)).isFalse();

    UnionType strings = (UnionType) createUnionType(STRING_OBJECT_TYPE, STRING_TYPE);
    assertThat(strings.isSubtype(STRING_TYPE)).isFalse();
    assertThat(strings.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(strings.isSubtype(DATE_TYPE)).isFalse();

    UnionType booleans = (UnionType) createUnionType(BOOLEAN_OBJECT_TYPE, BOOLEAN_TYPE);
    assertThat(booleans.isSubtype(BOOLEAN_TYPE)).isFalse();
    assertThat(booleans.isSubtype(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(booleans.isSubtype(REGEXP_TYPE)).isFalse();

    // unknown quirks
    JSType unknown = createUnionType(UNKNOWN_TYPE, DATE_TYPE);
    assertThat(unknown.isSubtypeOf(STRING_TYPE)).isTrue();

    // all members need to be assignable to
    UnionType stringDate = (UnionType) createUnionType(STRING_OBJECT_TYPE, DATE_TYPE);
    assertThat(stringDate.isSubtype(OBJECT_TYPE)).isTrue();
    assertThat(stringDate.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(stringDate.isSubtype(DATE_TYPE)).isFalse();
  }

  /** Tests the factory method {@link JSTypeRegistry#createUnionType(JSType...)}. */
  @SuppressWarnings("checked")
  @Test
  public void testCreateUnionType() {
    // number
    UnionType optNumber =
        (UnionType) registry.createUnionType(NUMBER_TYPE, DATE_TYPE);
    assertThat(optNumber.contains(NUMBER_TYPE)).isTrue();
    assertThat(optNumber.contains(DATE_TYPE)).isTrue();

    // union
    UnionType optUnion =
        (UnionType) registry.createUnionType(REGEXP_TYPE,
            registry.createUnionType(STRING_OBJECT_TYPE, DATE_TYPE));
    assertThat(optUnion.contains(DATE_TYPE)).isTrue();
    assertThat(optUnion.contains(STRING_OBJECT_TYPE)).isTrue();
    assertThat(optUnion.contains(REGEXP_TYPE)).isTrue();
  }

  @Test
  public void testUnionWithUnknown() {
    assertThat(createUnionType(UNKNOWN_TYPE, NULL_TYPE).isUnknownType()).isTrue();
  }

  @Test
  public void testGetRestrictedUnion1() {
    UnionType numStr = (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    assertType(numStr.getRestrictedUnion(NUMBER_TYPE)).isStructurallyEqualTo(STRING_TYPE);
  }

  @Test
  public void testGetRestrictedUnion2() {
    UnionType numStr = (UnionType) createUnionType(NULL_TYPE, sub1, sub2);
    assertType(numStr.getRestrictedUnion(base)).isStructurallyEqualTo(NULL_TYPE);
  }

  @Test
  public void testIsEquivalentTo() {
    UnionType type = (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    assertThat(type.equals(null)).isFalse();
    assertThat(type.isEquivalentTo(type)).isTrue();
  }

  @Test
  public void testProxyUnionType() {
    UnionType stringOrNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    UnionType stringOrBoolean =
        (UnionType) createUnionType(BOOLEAN_TYPE, STRING_TYPE);

    assertThat(stringOrNumber.getLeastSupertype(stringOrBoolean).toString())
        .isEqualTo("(boolean|number|string)");
    assertThat(stringOrNumber.getGreatestSubtype(stringOrBoolean).toString()).isEqualTo("string");
    assertThat(stringOrNumber.testForEquality(stringOrBoolean)).isEqualTo(TernaryValue.UNKNOWN);
    assertThat(stringOrNumber.getTypesUnderEquality(stringOrBoolean).typeA.toString())
        .isEqualTo("(number|string)");
    assertThat(stringOrNumber.getTypesUnderShallowEquality(stringOrBoolean).typeA.toString())
        .isEqualTo("string");
    assertThat(stringOrNumber.getTypesUnderInequality(stringOrBoolean).typeA.toString())
        .isEqualTo("(number|string)");
    assertThat(stringOrNumber.getTypesUnderShallowInequality(stringOrBoolean).typeA.toString())
        .isEqualTo("(number|string)");

    ObjectType stringOrNumberProxy =
        new ProxyObjectType(registry, stringOrNumber);
    ObjectType stringOrBooleanProxy =
        new ProxyObjectType(registry, stringOrBoolean);
    assertThat(stringOrNumberProxy.getLeastSupertype(stringOrBooleanProxy).toString())
        .isEqualTo("(boolean|number|string)");
    assertThat(stringOrNumberProxy.getGreatestSubtype(stringOrBooleanProxy).toString())
        .isEqualTo("string");
    assertThat(stringOrNumberProxy.testForEquality(stringOrBooleanProxy))
        .isEqualTo(TernaryValue.UNKNOWN);
    assertThat(stringOrNumberProxy.getTypesUnderEquality(stringOrBooleanProxy).typeA.toString())
        .isEqualTo("(number|string)");
    assertThat(
            stringOrNumberProxy.getTypesUnderShallowEquality(stringOrBooleanProxy).typeA.toString())
        .isEqualTo("string");
    assertThat(stringOrNumberProxy.getTypesUnderInequality(stringOrBooleanProxy).typeA.toString())
        .isEqualTo("(number|string)");
    assertThat(
            stringOrNumberProxy
                .getTypesUnderShallowInequality(stringOrBooleanProxy)
                .typeA
                .toString())
        .isEqualTo("(number|string)");
  }

  @Test
  public void testCollapseUnion1() {
    assertThat(registry.createUnionType(NUMBER_TYPE, STRING_TYPE).collapseUnion().toString())
        .isEqualTo("*");
  }

  @Test
  public void testCollapseUnion2() {
    assertThat(registry.createUnionType(UNKNOWN_TYPE, NUMBER_TYPE).collapseUnion().toString())
        .isEqualTo("?");
    assertThat(registry.createUnionType(NUMBER_TYPE, UNKNOWN_TYPE).collapseUnion().toString())
        .isEqualTo("?");
  }

  @Test
  public void testCollapseUnion3() {
    assertThat(registry.createUnionType(ARRAY_TYPE, DATE_TYPE).collapseUnion().toString())
        .isEqualTo("Object");
    assertThat(registry.createUnionType(ARRAY_TYPE, OBJECT_TYPE).collapseUnion().toString())
        .isEqualTo("Object");
    assertThat(registry.createUnionType(base, sub1).collapseUnion().toString()).isEqualTo("Base");
    assertThat(registry.createUnionType(sub1, sub2).collapseUnion().toString()).isEqualTo("Base");
    assertThat(registry.createUnionType(sub1, sub2, sub3).collapseUnion().toString())
        .isEqualTo("Base");
  }

  @Test
  public void testCollapseUnion4() {
    assertThat(registry.createUnionType(OBJECT_TYPE, STRING_TYPE).collapseUnion().toString())
        .isEqualTo("*");
    assertThat(registry.createUnionType(STRING_TYPE, OBJECT_TYPE).collapseUnion().toString())
        .isEqualTo("*");
  }

  @Test
  public void testCollapseProxyUnion() {
    // Make sure we don't unbox the proxy.
    ProxyObjectType type = new ProxyObjectType(registry, OBJECT_TYPE);
    assertThat(type == type.collapseUnion()).isTrue();
  }

  @Test
  public void testShallowEquality() {
    assertThat(
            registry
                .createUnionType(ARRAY_TYPE, STRING_TYPE)
                .canTestForShallowEqualityWith(OBJECT_TYPE))
        .isTrue();
  }
}
