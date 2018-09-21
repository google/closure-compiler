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
    assertTrue(type.isSubtypeOf(type));
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
    assertTrue(nullOrUnknown.isUnknownType());
    assertType(NULL_TYPE.getLeastSupertype(nullOrUnknown)).isStructurallyEqualTo(nullOrUnknown);
    assertType(nullOrUnknown.getLeastSupertype(NULL_TYPE)).isStructurallyEqualTo(nullOrUnknown);
    assertType(NULL_TYPE.getGreatestSubtype(nullOrUnknown)).isStructurallyEqualTo(UNKNOWN_TYPE);
    assertType(nullOrUnknown.getGreatestSubtype(NULL_TYPE)).isStructurallyEqualTo(UNKNOWN_TYPE);

    assertTrue(NULL_TYPE.differsFrom(nullOrUnknown));
    assertTrue(nullOrUnknown.differsFrom(NULL_TYPE));
    assertFalse(nullOrUnknown.differsFrom(unresolvedNamedType));

    assertTrue(NULL_TYPE.isSubtypeOf(nullOrUnknown));
    assertTrue(unresolvedNamedType.isSubtype(nullOrUnknown));
    assertTrue(nullOrUnknown.isSubtype(NULL_TYPE));

    assertType(nullOrUnknown.restrictByNotNullOrUndefined())
        .isStructurallyEqualTo(unresolvedNamedType);

    // findPropertyType
    assertType(nullOrString.findPropertyType("length")).isStructurallyEqualTo(NUMBER_TYPE);
    assertEquals(null, nullOrString.findPropertyType("lengthx"));

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
    assertTrue(BOOLEAN_TYPE.
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE)));
    assertTrue(createUnionType(BOOLEAN_TYPE, STRING_TYPE).
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE)));
    assertTrue(createUnionType(BOOLEAN_TYPE, STRING_TYPE).
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)));
    assertTrue(createUnionType(BOOLEAN_TYPE, STRING_TYPE).
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)));
    assertTrue(createUnionType(BOOLEAN_TYPE).
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)));
    assertTrue(createUnionType(STRING_TYPE).
        isSubtypeOf(createUnionType(BOOLEAN_TYPE, STRING_TYPE, NULL_TYPE)));
    assertTrue(createUnionType(STRING_TYPE, NULL_TYPE).isSubtypeOf(ALL_TYPE));
    assertTrue(createUnionType(DATE_TYPE, REGEXP_TYPE).isSubtypeOf(OBJECT_TYPE));
    assertTrue(createUnionType(sub1, sub2).isSubtypeOf(base));
    assertTrue(createUnionType(sub1, sub2).isSubtypeOf(OBJECT_TYPE));

    // not subtypes
    assertFalse(createUnionType(STRING_TYPE, NULL_TYPE).isSubtypeOf(NO_TYPE));
    assertFalse(createUnionType(STRING_TYPE, NULL_TYPE).
        isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(createUnionType(NO_OBJECT_TYPE, NULL_TYPE).
        isSubtypeOf(OBJECT_TYPE));

    // defined unions
    assertTrue(NUMBER_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING));
    assertTrue(OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING));
    assertTrue(STRING_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING));

    assertTrue(NUMBER_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN));
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN));
    assertTrue(STRING_TYPE.isSubtypeOf(NUMBER_STRING_BOOLEAN));

    assertTrue(NUMBER_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN));
    assertTrue(OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN));
    assertTrue(STRING_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN));
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_NUMBER_STRING_BOOLEAN));
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
    assertFalse(numbers.isSubtype(NUMBER_TYPE));
    assertFalse(numbers.isSubtype(NUMBER_OBJECT_TYPE));
    assertFalse(numbers.isSubtype(sub1));

    UnionType strings = (UnionType) createUnionType(STRING_OBJECT_TYPE, STRING_TYPE);
    assertFalse(strings.isSubtype(STRING_TYPE));
    assertFalse(strings.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(strings.isSubtype(DATE_TYPE));

    UnionType booleans = (UnionType) createUnionType(BOOLEAN_OBJECT_TYPE, BOOLEAN_TYPE);
    assertFalse(booleans.isSubtype(BOOLEAN_TYPE));
    assertFalse(booleans.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(booleans.isSubtype(REGEXP_TYPE));

    // unknown quirks
    JSType unknown = createUnionType(UNKNOWN_TYPE, DATE_TYPE);
    assertTrue(unknown.isSubtypeOf(STRING_TYPE));

    // all members need to be assignable to
    UnionType stringDate = (UnionType) createUnionType(STRING_OBJECT_TYPE, DATE_TYPE);
    assertTrue(stringDate.isSubtype(OBJECT_TYPE));
    assertFalse(stringDate.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(stringDate.isSubtype(DATE_TYPE));
  }

  /** Tests the factory method {@link JSTypeRegistry#createUnionType(JSType...)}. */
  @SuppressWarnings("checked")
  @Test
  public void testCreateUnionType() {
    // number
    UnionType optNumber =
        (UnionType) registry.createUnionType(NUMBER_TYPE, DATE_TYPE);
    assertTrue(optNumber.contains(NUMBER_TYPE));
    assertTrue(optNumber.contains(DATE_TYPE));

    // union
    UnionType optUnion =
        (UnionType) registry.createUnionType(REGEXP_TYPE,
            registry.createUnionType(STRING_OBJECT_TYPE, DATE_TYPE));
    assertTrue(optUnion.contains(DATE_TYPE));
    assertTrue(optUnion.contains(STRING_OBJECT_TYPE));
    assertTrue(optUnion.contains(REGEXP_TYPE));
  }

  @Test
  public void testUnionWithUnknown() {
    assertTrue(createUnionType(UNKNOWN_TYPE, NULL_TYPE).isUnknownType());
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
    assertFalse(type.equals(null));
    assertTrue(type.isEquivalentTo(type));
  }

  @Test
  public void testProxyUnionType() {
    UnionType stringOrNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    UnionType stringOrBoolean =
        (UnionType) createUnionType(BOOLEAN_TYPE, STRING_TYPE);

    assertEquals(
        "(boolean|number|string)",
        stringOrNumber.getLeastSupertype(stringOrBoolean).toString());
    assertEquals(
        "string",
        stringOrNumber.getGreatestSubtype(stringOrBoolean).toString());
    assertEquals(
        TernaryValue.UNKNOWN,
        stringOrNumber.testForEquality(stringOrBoolean));
    assertEquals(
        "(number|string)",
        stringOrNumber.getTypesUnderEquality(
            stringOrBoolean).typeA.toString());
    assertEquals(
        "string",
        stringOrNumber.getTypesUnderShallowEquality(
            stringOrBoolean).typeA.toString());
    assertEquals(
        "(number|string)",
        stringOrNumber.getTypesUnderInequality(
            stringOrBoolean).typeA.toString());
    assertEquals(
        "(number|string)",
        stringOrNumber.getTypesUnderShallowInequality(
            stringOrBoolean).typeA.toString());

    ObjectType stringOrNumberProxy =
        new ProxyObjectType(registry, stringOrNumber);
    ObjectType stringOrBooleanProxy =
        new ProxyObjectType(registry, stringOrBoolean);
    assertEquals(
        "(boolean|number|string)",
        stringOrNumberProxy.getLeastSupertype(
            stringOrBooleanProxy).toString());
    assertEquals(
        "string",
        stringOrNumberProxy.getGreatestSubtype(
            stringOrBooleanProxy).toString());
    assertEquals(
        TernaryValue.UNKNOWN,
        stringOrNumberProxy.testForEquality(stringOrBooleanProxy));
    assertEquals(
        "(number|string)",
        stringOrNumberProxy.getTypesUnderEquality(
            stringOrBooleanProxy).typeA.toString());
    assertEquals(
        "string",
        stringOrNumberProxy.getTypesUnderShallowEquality(
            stringOrBooleanProxy).typeA.toString());
    assertEquals(
        "(number|string)",
        stringOrNumberProxy.getTypesUnderInequality(
            stringOrBooleanProxy).typeA.toString());
    assertEquals(
        "(number|string)",
        stringOrNumberProxy.getTypesUnderShallowInequality(
            stringOrBooleanProxy).typeA.toString());
  }

  @Test
  public void testCollapseUnion1() {
    assertEquals(
        "*",
        registry.createUnionType(NUMBER_TYPE, STRING_TYPE)
        .collapseUnion().toString());
  }

  @Test
  public void testCollapseUnion2() {
    assertEquals(
        "?",
        registry.createUnionType(UNKNOWN_TYPE, NUMBER_TYPE)
        .collapseUnion().toString());
    assertEquals(
        "?",
        registry.createUnionType(NUMBER_TYPE, UNKNOWN_TYPE)
        .collapseUnion().toString());
  }

  @Test
  public void testCollapseUnion3() {
    assertEquals(
        "Object",
        registry.createUnionType(ARRAY_TYPE, DATE_TYPE).collapseUnion().toString());
    assertEquals(
        "Object",
        registry.createUnionType(ARRAY_TYPE, OBJECT_TYPE).collapseUnion().toString());
    assertEquals(
        "Base",
        registry.createUnionType(base, sub1).collapseUnion().toString());
    assertEquals(
        "Base",
        registry.createUnionType(sub1, sub2).collapseUnion().toString());
    assertEquals(
        "Base",
        registry.createUnionType(sub1, sub2, sub3).collapseUnion().toString());
  }

  @Test
  public void testCollapseUnion4() {
    assertEquals(
        "*",
        registry.createUnionType(OBJECT_TYPE, STRING_TYPE)
        .collapseUnion().toString());
    assertEquals(
        "*",
        registry.createUnionType(STRING_TYPE, OBJECT_TYPE)
        .collapseUnion().toString());
  }

  @Test
  public void testCollapseProxyUnion() {
    // Make sure we don't unbox the proxy.
    ProxyObjectType type = new ProxyObjectType(registry, OBJECT_TYPE);
    assertTrue(type == type.collapseUnion());
  }

  @Test
  public void testShallowEquality() {
    assertTrue(
        registry.createUnionType(ARRAY_TYPE, STRING_TYPE)
        .canTestForShallowEqualityWith(OBJECT_TYPE));
  }
}
