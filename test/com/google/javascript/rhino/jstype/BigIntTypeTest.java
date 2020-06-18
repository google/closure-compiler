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
 *   Brock Smickley
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BigIntTypes. */
@RunWith(JUnit4.class)
public class BigIntTypeTest extends BaseJSTypeTestCase {
  private FunctionType functionType;
  private NamedType unresolvedNamedType;
  private EnumType enumType;
  private EnumElementType elementsType;

  @Before
  public void setUp() {
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      final ObjectType googObject = registry.createAnonymousObjectType(null);
      MapBasedScope scope = new MapBasedScope(ImmutableMap.of("goog", googObject));
      functionType = FunctionType.builder(registry).withReturnType(NUMBER_TYPE).build();
      errorReporter.expectAllWarnings("Bad type annotation. Unknown type not.resolved.named.type");
      unresolvedNamedType = registry.createNamedType(scope, "not.resolved.named.type", "", -1, -1);
      enumType = new EnumType(registry, "Enum", null, BIGINT_TYPE);
      elementsType = enumType.getElementsType();
    }
  }

  @Test
  public void testBigIntValueTypeIsXxx() {
    assertThat(BIGINT_TYPE.isAllType()).isFalse();
    assertThat(BIGINT_TYPE.isArrayType()).isFalse();
    assertThat(BIGINT_TYPE.isBigIntValueType()).isTrue();
    assertThat(BIGINT_TYPE.isBigIntObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isBooleanObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isBooleanValueType()).isFalse();
    assertThat(BIGINT_TYPE.isCheckedUnknownType()).isFalse();
    assertThat(BIGINT_TYPE.isConstructor()).isFalse();
    assertThat(BIGINT_TYPE.isDateType()).isFalse();
    assertThat(BIGINT_TYPE.isDict()).isFalse();
    assertThat(BIGINT_TYPE.isEmptyType()).isFalse();
    assertThat(BIGINT_TYPE.isEnumElementType()).isFalse();
    assertThat(BIGINT_TYPE.isEnumType()).isFalse();
    assertThat(BIGINT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(BIGINT_TYPE.isFunctionType()).isFalse();
    assertThat(BIGINT_TYPE.isGlobalThisType()).isFalse();
    assertThat(BIGINT_TYPE.isInstanceType()).isFalse();
    assertThat(BIGINT_TYPE.isNamedType()).isFalse();
    assertThat(BIGINT_TYPE.isNativeObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isNoObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isNoResolvedType()).isFalse();
    assertThat(BIGINT_TYPE.isNoType()).isFalse();
    assertThat(BIGINT_TYPE.isNullType()).isFalse();
    assertThat(BIGINT_TYPE.isNumber()).isFalse();
    assertThat(BIGINT_TYPE.isNumberObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isNumberValueType()).isFalse();
    assertThat(BIGINT_TYPE.isObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isRegexpType()).isFalse();
    assertThat(BIGINT_TYPE.isSomeUnknownType()).isFalse();
    assertThat(BIGINT_TYPE.isString()).isFalse();
    assertThat(BIGINT_TYPE.isStringObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isStringValueType()).isFalse();
    assertThat(BIGINT_TYPE.isStruct()).isFalse();
    assertThat(BIGINT_TYPE.isSymbol()).isFalse();
    assertThat(BIGINT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(BIGINT_TYPE.isSymbolValueType()).isFalse();
    assertThat(BIGINT_TYPE.isUnionType()).isFalse();
    assertThat(BIGINT_TYPE.isVoidType()).isFalse();
  }

  @Test
  public void testBigIntObjectTypeIsXxx() {
    assertThat(BIGINT_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isBigIntValueType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isBigIntObjectType()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isBooleanObjectType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isCheckedUnknownType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isDict()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isEmptyType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isFunctionType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isGlobalThisType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isInstanceType()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNativeObjectType()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isNoObjectType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNoResolvedType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNoType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNumber()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isObjectType()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSomeUnknownType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isString()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSymbol()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSymbolValueType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isVoidType()).isFalse();
  }

  @Test
  public void testIsOnlyBigInt() {
    assertType(BIGINT_TYPE).isOnlyBigInt();
    assertType(BIGINT_OBJECT_TYPE).isOnlyBigInt();
    assertType(ALL_TYPE).isNotOnlyBigInt();
    assertType(UNKNOWN_TYPE).isNotOnlyBigInt();
    assertType(NO_TYPE).isNotOnlyBigInt();
    assertType(createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE)).isNotOnlyBigInt();
    assertType(BIGINT_NUMBER).isNotOnlyBigInt();
    assertType(registry.createEnumType("Enum", null, BIGINT_TYPE).getElementsType())
        .isNotOnlyBigInt();
    assertType(registry.createEnumType("Enum", null, NUMBER_TYPE).getElementsType())
        .isNotOnlyBigInt();
    assertType(registry.createEnumType("Enum", null, BIGINT_NUMBER).getElementsType())
        .isNotOnlyBigInt();
    assertType(registry.createEnumType("Enum", null, NUMBER_STRING).getElementsType())
        .isNotOnlyBigInt();
  }

  @Test
  public void testBigIntValueTypeIsSubtype() {
    assertThat(BIGINT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BIGINT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(BIGINT_TYPE)).isTrue();
    assertThat(BIGINT_TYPE.isSubtypeOf(FUNCTION_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(BIGINT_TYPE.isSubtypeOf(createUnionType(BIGINT_TYPE, NULL_TYPE))).isTrue();
    assertThat(BIGINT_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testBigIntValueObjectIsSubtype() {
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(BIGINT_OBJECT_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(BIGINT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(FUNCTION_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(createUnionType(BIGINT_OBJECT_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testBigIntValueTypeEquality() {
    assertCanTestForEqualityWith(BIGINT_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, BIGINT_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, BIGINT_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, enumType);
    assertCannotTestForEqualityWith(BIGINT_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(BIGINT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(BIGINT_TYPE, functionType);
    assertCannotTestForEqualityWith(BIGINT_TYPE, VOID_TYPE);
    assertCannotTestForEqualityWith(BIGINT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(BIGINT_TYPE, UNKNOWN_TYPE);
  }

  @Test
  public void testBigIntObjectTypeEquality() {
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, BIGINT_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, BIGINT_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(BIGINT_OBJECT_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(BIGINT_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, elementsType);
    assertCannotTestForEqualityWith(BIGINT_OBJECT_TYPE, VOID_TYPE);
    assertCannotTestForEqualityWith(BIGINT_OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(BIGINT_OBJECT_TYPE, UNKNOWN_TYPE);
  }

  @Test
  public void testBigIntValueTypeShallowEquality() {
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(BIGINT_TYPE)).isTrue();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(BIGINT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testBigIntObjectTypeShallowEquality() {
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(BIGINT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(BIGINT_OBJECT_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testGetLeastSupertypeForValueType() {
    assertTypeEquals(ALL_TYPE, BIGINT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, STRING_OBJECT_TYPE),
        BIGINT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, SYMBOL_OBJECT_TYPE),
        BIGINT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, SYMBOL_TYPE), BIGINT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(BIGINT_TYPE, BIGINT_TYPE.getLeastSupertype(BIGINT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, functionType), BIGINT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, OBJECT_TYPE), BIGINT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, DATE_TYPE), BIGINT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_TYPE, REGEXP_TYPE), BIGINT_TYPE.getLeastSupertype(REGEXP_TYPE));
  }

  @Test
  public void testGetLeastSupertypeForObjectType() {
    assertTypeEquals(ALL_TYPE, BIGINT_OBJECT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, STRING_OBJECT_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, SYMBOL_OBJECT_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, SYMBOL_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(BIGINT_OBJECT_TYPE, BIGINT_OBJECT_TYPE.getLeastSupertype(BIGINT_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, functionType),
        BIGINT_OBJECT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, OBJECT_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, DATE_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(
        createUnionType(BIGINT_OBJECT_TYPE, REGEXP_TYPE),
        BIGINT_OBJECT_TYPE.getLeastSupertype(REGEXP_TYPE));
  }

  @Test
  public void testBigIntAutoboxAndUnbox() {
    assertTypeEquals(BIGINT_TYPE.autoboxesTo(), BIGINT_OBJECT_TYPE);
    assertTypeEquals(BIGINT_TYPE, BIGINT_OBJECT_TYPE.unboxesTo());
  }

  @Test
  public void testCanBeCalled() {
    assertThat(BIGINT_TYPE.canBeCalled()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.canBeCalled()).isFalse();
  }

  @Test
  public void testIsNullable() {
    assertThat(BIGINT_TYPE.isNullable()).isFalse();
    assertThat(BIGINT_TYPE.isVoidable()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isVoidable()).isFalse();
  }

  @Test
  public void testMatchesXxx() {
    assertThat(BIGINT_TYPE.matchesNumberContext()).isFalse();
    assertThat(BIGINT_TYPE.matchesObjectContext()).isTrue();
    assertThat(BIGINT_TYPE.matchesStringContext()).isTrue();
    assertThat(BIGINT_TYPE.matchesSymbolContext()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.matchesNumberContext()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.matchesSymbolContext()).isFalse();
  }

  @Test
  public void testToString() {
    assertThat(BIGINT_TYPE.toString()).isEqualTo("bigint");
    assertThat(BIGINT_TYPE.hasDisplayName()).isTrue();
    assertThat(BIGINT_TYPE.getDisplayName()).isEqualTo("bigint");
    assertThat(BIGINT_OBJECT_TYPE.toString()).isEqualTo("BigInt");
    assertThat(BIGINT_OBJECT_TYPE.hasDisplayName()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.getDisplayName()).isEqualTo("BigInt");
  }

  @Test
  public void testIsNotNominalConstructor() {
    assertThat(BIGINT_TYPE.isNominalConstructor()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.isNominalConstructor()).isFalse();
    assertThat(BIGINT_OBJECT_TYPE.getConstructor().isNominalConstructor()).isTrue();
  }

  @Test
  public void testResolvesToSame() {
    Asserts.assertResolvesToSame(BIGINT_TYPE);
    Asserts.assertResolvesToSame(BIGINT_OBJECT_TYPE);
  }
}
