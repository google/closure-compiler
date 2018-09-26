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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.TRUE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType.TypePair;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(nicksantos): Split some of this up into per-class unit tests.
@RunWith(JUnit4.class)
public class JSTypeTest extends BaseJSTypeTestCase {
  private FunctionType dateMethod;
  private FunctionType functionType;
  private NamedType unresolvedNamedType;
  private FunctionType googBar;
  private FunctionType googSubBar;
  private FunctionType googSubSubBar;
  private ObjectType googBarInst;
  private ObjectType googSubBarInst;
  private ObjectType googSubSubBarInst;
  private NamedType namedGoogBar;
  private ObjectType subclassOfUnresolvedNamedType;
  private FunctionType subclassCtor;
  private FunctionType interfaceType;
  private ObjectType interfaceInstType;
  private FunctionType subInterfaceType;
  private ObjectType subInterfaceInstType;
  private JSType recordType;
  private EnumType enumType;
  private EnumElementType elementsType;
  private NamedType forwardDeclaredNamedType;

  private static final StaticTypedScope EMPTY_SCOPE = MapBasedScope.emptyScope();

  /**
   * A non exhaustive list of representative types used to test simple
   * properties that should hold for all types (such as the reflexivity
   * of subtyping).
   */
  private List<JSType> types;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    final ObjectType googObject = registry.createAnonymousObjectType(null);
    MapBasedScope scope = new MapBasedScope(ImmutableMap.of("goog", googObject));

    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    recordType = builder.build();

    enumType = new EnumType(registry, "Enum", null, NUMBER_TYPE);
    elementsType = enumType.getElementsType();
    functionType = new FunctionBuilder(registry)
        .withReturnType(NUMBER_TYPE)
        .build();
    dateMethod = new FunctionBuilder(registry)
        .withParamsNode(new Node(Token.PARAM_LIST))
        .withReturnType(NUMBER_TYPE)
        .withTypeOfThis(DATE_TYPE)
        .build();
    unresolvedNamedType = new NamedType(scope, registry, "not.resolved.named.type", null, -1, -1);
    namedGoogBar = new NamedType(scope, registry, "goog.Bar", null, -1, -1);

    subclassCtor = new FunctionBuilder(registry).forConstructor().build();
    subclassCtor.setPrototypeBasedOn(unresolvedNamedType);
    subclassOfUnresolvedNamedType = subclassCtor.getInstanceType();

    interfaceType = registry.createInterfaceType("Interface", null, null, false);
    interfaceInstType = interfaceType.getInstanceType();

    subInterfaceType = registry.createInterfaceType("SubInterface", null, null, false);
    subInterfaceType.setExtendedInterfaces(Lists.<ObjectType>newArrayList(interfaceInstType));
    subInterfaceInstType = subInterfaceType.getInstanceType();

    googBar = registry.createConstructorType("goog.Bar", null, null, null, null, false);
    googBar.getPrototype().defineDeclaredProperty("date", DATE_TYPE, null);
    googBar.setImplementedInterfaces(Lists.<ObjectType>newArrayList(interfaceInstType));
    googBarInst = googBar.getInstanceType();

    googSubBar = registry.createConstructorType("googSubBar", null, null, null, null, false);
    googSubBar.setPrototypeBasedOn(googBar.getInstanceType());
    googSubBarInst = googSubBar.getInstanceType();

    googSubSubBar = registry.createConstructorType("googSubSubBar", null, null, null, null, false);
    googSubSubBar.setPrototypeBasedOn(googSubBar.getInstanceType());
    googSubSubBarInst = googSubSubBar.getInstanceType();

    googObject.defineDeclaredProperty("Bar", googBar, null);

    namedGoogBar.resolve(null);
    assertNotNull(namedGoogBar.getImplicitPrototype());

    forwardDeclaredNamedType = new NamedType(scope, registry, "forwardDeclared", "source", 1, 0);
    forwardDeclaredNamedType.resolve(new SimpleErrorReporter());

    types =
        ImmutableList.of(
            NO_OBJECT_TYPE,
            NO_RESOLVED_TYPE,
            NO_TYPE,
            BOOLEAN_OBJECT_TYPE,
            BOOLEAN_TYPE,
            STRING_OBJECT_TYPE,
            STRING_TYPE,
            SYMBOL_OBJECT_TYPE,
            SYMBOL_TYPE,
            VOID_TYPE,
            UNKNOWN_TYPE,
            NULL_TYPE,
            NUMBER_OBJECT_TYPE,
            NUMBER_TYPE,
            DATE_TYPE,
            dateMethod,
            functionType,
            unresolvedNamedType,
            googBar,
            googSubBar,
            googSubSubBar,
            namedGoogBar,
            googBar.getInstanceType(),
            subclassOfUnresolvedNamedType,
            subclassCtor,
            recordType,
            enumType,
            elementsType,
            googBar,
            googSubBar,
            forwardDeclaredNamedType);
  }

  /** Tests the behavior of the top constructor type. */
  @Test
  public void testUniversalConstructorType() {
    // isXxx
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNoObjectType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNoType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isArrayType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isBooleanValueType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isDateType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isEnumElementType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNullType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNamedType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNullType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNumber());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNumberObjectType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNumberValueType());
    assertTrue(U2U_CONSTRUCTOR_TYPE.isObject());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isFunctionPrototypeType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isRegexpType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isString());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isStringObjectType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isStringValueType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSymbol());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSymbolObjectType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSymbolValueType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isEnumType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isUnionType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isStruct());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isDict());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isAllType());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isVoidType());
    assertTrue(U2U_CONSTRUCTOR_TYPE.isConstructor());
    assertTrue(U2U_CONSTRUCTOR_TYPE.isInstanceType());

    // isSubtype
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NO_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(DATE_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(functionType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(recordType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NULL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(STRING_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(SYMBOL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(SYMBOL_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(ALL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(VOID_TYPE));

    // canTestForEqualityWith
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(NO_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(NO_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(ALL_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(ARRAY_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(BOOLEAN_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(DATE_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(functionType));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(recordType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(NULL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(NUMBER_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(STRING_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.canTestForEqualityWith(SYMBOL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.canTestForEqualityWith(SYMBOL_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(VOID_TYPE));

    // canTestForShallowEqualityWith
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(functionType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(recordType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(U2U_CONSTRUCTOR_TYPE.isNullable());
    assertFalse(U2U_CONSTRUCTOR_TYPE.isVoidable());

    // isObject
    assertTrue(U2U_CONSTRUCTOR_TYPE.isObject());

    // matchesXxx
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesNumberContext());
    assertTrue(U2U_CONSTRUCTOR_TYPE.matchesObjectContext());
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesStringContext());
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesSymbolContext());

    // toString
    assertEquals("Function",
        U2U_CONSTRUCTOR_TYPE.toString());
    assertTrue(U2U_CONSTRUCTOR_TYPE.hasDisplayName());
    assertEquals("Function", U2U_CONSTRUCTOR_TYPE.getDisplayName());

    // getPropertyType
    assertTypeEquals(UNKNOWN_TYPE,
        U2U_CONSTRUCTOR_TYPE.getPropertyType("anyProperty"));

    assertTrue(U2U_CONSTRUCTOR_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(U2U_CONSTRUCTOR_TYPE);

    assertTrue(U2U_CONSTRUCTOR_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the Bottom Object type. */
  @Test
  public void testNoObjectType() {
    // isXxx
    assertTrue(NO_OBJECT_TYPE.isNoObjectType());
    assertFalse(NO_OBJECT_TYPE.isNoType());
    assertFalse(NO_OBJECT_TYPE.isArrayType());
    assertFalse(NO_OBJECT_TYPE.isBooleanValueType());
    assertFalse(NO_OBJECT_TYPE.isDateType());
    assertFalse(NO_OBJECT_TYPE.isEnumElementType());
    assertFalse(NO_OBJECT_TYPE.isNullType());
    assertFalse(NO_OBJECT_TYPE.isNamedType());
    assertFalse(NO_OBJECT_TYPE.isNullType());
    assertTrue(NO_OBJECT_TYPE.isNumber());
    assertFalse(NO_OBJECT_TYPE.isNumberObjectType());
    assertFalse(NO_OBJECT_TYPE.isNumberValueType());
    assertTrue(NO_OBJECT_TYPE.isObject());
    assertFalse(NO_OBJECT_TYPE.isFunctionPrototypeType());
    assertFalse(NO_OBJECT_TYPE.isRegexpType());
    assertTrue(NO_OBJECT_TYPE.isString());
    assertFalse(NO_OBJECT_TYPE.isStringObjectType());
    assertFalse(NO_OBJECT_TYPE.isStringValueType());
    assertTrue(NO_OBJECT_TYPE.isSymbol());
    assertFalse(NO_OBJECT_TYPE.isSymbolObjectType());
    assertFalse(NO_OBJECT_TYPE.isSymbolValueType());
    assertFalse(NO_OBJECT_TYPE.isEnumType());
    assertFalse(NO_OBJECT_TYPE.isUnionType());
    assertFalse(NO_OBJECT_TYPE.isStruct());
    assertFalse(NO_OBJECT_TYPE.isDict());
    assertFalse(NO_OBJECT_TYPE.isAllType());
    assertFalse(NO_OBJECT_TYPE.isVoidType());
    assertFalse(NO_OBJECT_TYPE.isConstructor());
    assertFalse(NO_OBJECT_TYPE.isInstanceType());

    // isSubtype
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(functionType));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(recordType));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(VOID_TYPE));

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, recordType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(NO_OBJECT_TYPE.isNullable());
    assertFalse(NO_OBJECT_TYPE.isVoidable());

    // isObject
    assertTrue(NO_OBJECT_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_OBJECT_TYPE.matchesNumberContext());
    assertTrue(NO_OBJECT_TYPE.matchesObjectContext());
    assertTrue(NO_OBJECT_TYPE.matchesStringContext());
    assertTrue(NO_OBJECT_TYPE.matchesSymbolContext());

    // toString
    assertEquals("NoObject", NO_OBJECT_TYPE.toString());
    assertFalse(NO_OBJECT_TYPE.hasDisplayName());
    assertEquals(null, NO_OBJECT_TYPE.getDisplayName());

    // getPropertyType
    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getPropertyType("anyProperty"));

    Asserts.assertResolvesToSame(NO_OBJECT_TYPE);

    assertFalse(NO_OBJECT_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the Bottom type. */
  @Test
  public void testNoType() {
    // isXxx
    assertFalse(NO_TYPE.isNoObjectType());
    assertTrue(NO_TYPE.isNoType());
    assertFalse(NO_TYPE.isArrayType());
    assertFalse(NO_TYPE.isBooleanValueType());
    assertFalse(NO_TYPE.isDateType());
    assertFalse(NO_TYPE.isEnumElementType());
    assertFalse(NO_TYPE.isNullType());
    assertFalse(NO_TYPE.isNamedType());
    assertFalse(NO_TYPE.isNullType());
    assertTrue(NO_TYPE.isNumber());
    assertFalse(NO_TYPE.isNumberObjectType());
    assertFalse(NO_TYPE.isNumberValueType());
    assertTrue(NO_TYPE.isObject());
    assertFalse(NO_TYPE.isFunctionPrototypeType());
    assertFalse(NO_TYPE.isRegexpType());
    assertTrue(NO_TYPE.isString());
    assertFalse(NO_TYPE.isStringObjectType());
    assertFalse(NO_TYPE.isStringValueType());
    assertTrue(NO_TYPE.isSymbol());
    assertFalse(NO_TYPE.isSymbolObjectType());
    assertFalse(NO_TYPE.isSymbolValueType());
    assertFalse(NO_TYPE.isEnumType());
    assertFalse(NO_TYPE.isUnionType());
    assertFalse(NO_TYPE.isStruct());
    assertFalse(NO_TYPE.isDict());
    assertFalse(NO_TYPE.isAllType());
    assertFalse(NO_TYPE.isVoidType());
    assertFalse(NO_TYPE.isConstructor());
    assertFalse(NO_TYPE.isInstanceType());

    // isSubtype
    assertTrue(NO_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(functionType));
    assertTrue(NO_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(ALL_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(VOID_TYPE));

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, functionType);
    assertCanTestForEqualityWith(NO_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertTrue(NO_TYPE.isNullable());
    assertTrue(NO_TYPE.isVoidable());

    // isObject
    assertTrue(NO_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_TYPE.matchesNumberContext());
    assertTrue(NO_TYPE.matchesObjectContext());
    assertTrue(NO_TYPE.matchesStringContext());
    assertTrue(NO_TYPE.matchesSymbolContext());

    // toString
    assertEquals("None", NO_TYPE.toString());
    assertEquals(null, NO_TYPE.getDisplayName());
    assertFalse(NO_TYPE.hasDisplayName());

    // getPropertyType
    assertTypeEquals(NO_TYPE,
        NO_TYPE.getPropertyType("anyProperty"));

    Asserts.assertResolvesToSame(NO_TYPE);

    assertFalse(NO_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the unresolved Bottom type. */
  @Test
  public void testNoResolvedType() {
    // isXxx
    assertFalse(NO_RESOLVED_TYPE.isNoObjectType());
    assertFalse(NO_RESOLVED_TYPE.isNoType());
    assertTrue(NO_RESOLVED_TYPE.isNoResolvedType());
    assertFalse(NO_RESOLVED_TYPE.isArrayType());
    assertFalse(NO_RESOLVED_TYPE.isBooleanValueType());
    assertFalse(NO_RESOLVED_TYPE.isDateType());
    assertFalse(NO_RESOLVED_TYPE.isEnumElementType());
    assertFalse(NO_RESOLVED_TYPE.isNullType());
    assertFalse(NO_RESOLVED_TYPE.isNamedType());
    assertTrue(NO_RESOLVED_TYPE.isNumber());
    assertFalse(NO_RESOLVED_TYPE.isNumberObjectType());
    assertFalse(NO_RESOLVED_TYPE.isNumberValueType());
    assertTrue(NO_RESOLVED_TYPE.isObject());
    assertFalse(NO_RESOLVED_TYPE.isFunctionPrototypeType());
    assertFalse(NO_RESOLVED_TYPE.isRegexpType());
    assertTrue(NO_RESOLVED_TYPE.isString());
    assertFalse(NO_RESOLVED_TYPE.isStringObjectType());
    assertFalse(NO_RESOLVED_TYPE.isStringValueType());
    assertTrue(NO_RESOLVED_TYPE.isSymbol());
    assertFalse(NO_RESOLVED_TYPE.isSymbolObjectType());
    assertFalse(NO_RESOLVED_TYPE.isSymbolValueType());
    assertFalse(NO_RESOLVED_TYPE.isEnumType());
    assertFalse(NO_RESOLVED_TYPE.isUnionType());
    assertFalse(NO_RESOLVED_TYPE.isStruct());
    assertFalse(NO_RESOLVED_TYPE.isDict());
    assertFalse(NO_RESOLVED_TYPE.isAllType());
    assertFalse(NO_RESOLVED_TYPE.isVoidType());
    assertFalse(NO_RESOLVED_TYPE.isConstructor());
    assertFalse(NO_RESOLVED_TYPE.isInstanceType());

    // isSubtype
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(NO_RESOLVED_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(functionType));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(ALL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtypeOf(VOID_TYPE));

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_RESOLVED_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, functionType);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NO_RESOLVED_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertTrue(NO_RESOLVED_TYPE.isNullable());
    assertTrue(NO_RESOLVED_TYPE.isVoidable());

    // isObject
    assertTrue(NO_RESOLVED_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_RESOLVED_TYPE.matchesNumberContext());
    assertTrue(NO_RESOLVED_TYPE.matchesObjectContext());
    assertTrue(NO_RESOLVED_TYPE.matchesStringContext());
    assertTrue(NO_RESOLVED_TYPE.matchesSymbolContext());

    // toString
    assertEquals("NoResolvedType", NO_RESOLVED_TYPE.toString());
    assertEquals(null, NO_RESOLVED_TYPE.getDisplayName());
    assertFalse(NO_RESOLVED_TYPE.hasDisplayName());

    // getPropertyType
    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        NO_RESOLVED_TYPE.getPropertyType("anyProperty"));

    Asserts.assertResolvesToSame(NO_RESOLVED_TYPE);

    assertTrue(forwardDeclaredNamedType.isEmptyType());
    assertTrue(forwardDeclaredNamedType.isNoResolvedType());

    UnionType nullable =
        (UnionType) registry.createNullableType(NO_RESOLVED_TYPE);
    assertTypeEquals(
        nullable, nullable.getGreatestSubtype(NULL_TYPE));
    assertTypeEquals(NO_RESOLVED_TYPE, nullable.getRestrictedUnion(NULL_TYPE));
  }

  /** Tests the behavior of the Array type. */
  @Test
  public void testArrayType() {
    // isXxx
    assertTrue(ARRAY_TYPE.isArrayType());
    assertFalse(ARRAY_TYPE.isBooleanValueType());
    assertFalse(ARRAY_TYPE.isDateType());
    assertFalse(ARRAY_TYPE.isEnumElementType());
    assertFalse(ARRAY_TYPE.isNamedType());
    assertFalse(ARRAY_TYPE.isNullType());
    assertFalse(ARRAY_TYPE.isNumber());
    assertFalse(ARRAY_TYPE.isNumberObjectType());
    assertFalse(ARRAY_TYPE.isNumberValueType());
    assertTrue(ARRAY_TYPE.isObject());
    assertFalse(ARRAY_TYPE.isFunctionPrototypeType());
    assertTrue(ARRAY_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(ARRAY_TYPE.isRegexpType());
    assertFalse(ARRAY_TYPE.isString());
    assertFalse(ARRAY_TYPE.isStringObjectType());
    assertFalse(ARRAY_TYPE.isStringValueType());
    assertFalse(ARRAY_TYPE.isSymbol());
    assertFalse(ARRAY_TYPE.isSymbolObjectType());
    assertFalse(ARRAY_TYPE.isSymbolValueType());
    assertFalse(ARRAY_TYPE.isEnumType());
    assertFalse(ARRAY_TYPE.isUnionType());
    assertFalse(ARRAY_TYPE.isStruct());
    assertFalse(ARRAY_TYPE.isDict());
    assertFalse(ARRAY_TYPE.isAllType());
    assertFalse(ARRAY_TYPE.isVoidType());
    assertFalse(ARRAY_TYPE.isConstructor());
    assertTrue(ARRAY_TYPE.isInstanceType());

    // isSubtype
    assertFalse(ARRAY_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(functionType));
    assertFalse(ARRAY_TYPE.isSubtypeOf(recordType));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(ARRAY_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(ARRAY_TYPE.isSubtypeOf(REGEXP_TYPE));

    // canBeCalled
    assertFalse(ARRAY_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(ARRAY_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(ARRAY_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, functionType);
    assertCanTestForEqualityWith(ARRAY_TYPE, recordType);
    assertCannotTestForEqualityWith(ARRAY_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(ARRAY_TYPE.isNullable());
    assertFalse(ARRAY_TYPE.isVoidable());
    assertTrue(createUnionType(ARRAY_TYPE, NULL_TYPE).isNullable());
    assertTrue(createUnionType(ARRAY_TYPE, VOID_TYPE).isVoidable());

    // isObject
    assertTrue(ARRAY_TYPE.isObject());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        ARRAY_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(STRING_OBJECT_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(SYMBOL_OBJECT_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(ARRAY_TYPE, functionType),
        ARRAY_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, ARRAY_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertEquals(17, ARRAY_TYPE.getImplicitPrototype().getPropertiesCount());
    assertEquals(18, ARRAY_TYPE.getPropertiesCount());
    assertReturnTypeEquals(ARRAY_TYPE,
        ARRAY_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("concat"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("join"));
    assertReturnTypeEquals(UNKNOWN_TYPE, ARRAY_TYPE.getPropertyType("pop"));
    assertReturnTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("push"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("reverse"));
    assertReturnTypeEquals(UNKNOWN_TYPE, ARRAY_TYPE.getPropertyType("shift"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("slice"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("sort"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("splice"));
    assertReturnTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("unshift"));
    assertTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("length"));

    // isPropertyType*
    assertPropertyTypeDeclared(ARRAY_TYPE, "pop");

    // matchesXxx
    assertFalse(ARRAY_TYPE.matchesNumberContext());
    assertTrue(ARRAY_TYPE.matchesObjectContext());
    assertTrue(ARRAY_TYPE.matchesStringContext());
    assertFalse(ARRAY_TYPE.matchesSymbolContext());

    // toString
    assertEquals("Array", ARRAY_TYPE.toString());
    assertTrue(ARRAY_TYPE.hasDisplayName());
    assertEquals("Array", ARRAY_TYPE.getDisplayName());

    assertTrue(ARRAY_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(ARRAY_TYPE);

    assertFalse(ARRAY_TYPE.isNominalConstructor());
    assertTrue(ARRAY_TYPE.getConstructor().isNominalConstructor());
  }

  /** Tests the behavior of the unknown type. */
  @Test
  public void testUnknownType() {
    // isXxx
    assertFalse(UNKNOWN_TYPE.isArrayType());
    assertFalse(UNKNOWN_TYPE.isBooleanObjectType());
    assertFalse(UNKNOWN_TYPE.isBooleanValueType());
    assertFalse(UNKNOWN_TYPE.isDateType());
    assertFalse(UNKNOWN_TYPE.isEnumElementType());
    assertFalse(UNKNOWN_TYPE.isNamedType());
    assertFalse(UNKNOWN_TYPE.isNullType());
    assertFalse(UNKNOWN_TYPE.isNumberObjectType());
    assertFalse(UNKNOWN_TYPE.isNumberValueType());
    assertTrue(UNKNOWN_TYPE.isObject());
    assertFalse(UNKNOWN_TYPE.isFunctionPrototypeType());
    assertFalse(UNKNOWN_TYPE.isRegexpType());
    assertFalse(UNKNOWN_TYPE.isStringObjectType());
    assertFalse(UNKNOWN_TYPE.isStringValueType());
    assertFalse(UNKNOWN_TYPE.isSymbolObjectType());
    assertFalse(UNKNOWN_TYPE.isSymbolValueType());
    assertFalse(UNKNOWN_TYPE.isEnumType());
    assertFalse(UNKNOWN_TYPE.isUnionType());
    assertFalse(UNKNOWN_TYPE.isStruct());
    assertFalse(UNKNOWN_TYPE.isDict());
    assertTrue(UNKNOWN_TYPE.isUnknownType());
    assertFalse(UNKNOWN_TYPE.isVoidType());
    assertFalse(UNKNOWN_TYPE.isConstructor());
    assertFalse(UNKNOWN_TYPE.isInstanceType());

    // autoboxesTo
    assertNull(UNKNOWN_TYPE.autoboxesTo());

    // isSubtype
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(UNKNOWN_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(functionType));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(recordType));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(namedGoogBar));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(unresolvedNamedType));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtypeOf(VOID_TYPE));

    // canBeCalled
    assertTrue(UNKNOWN_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, functionType);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, recordType);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, BOOLEAN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(recordType));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(UNKNOWN_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));

    // canHaveNullValue
    assertTrue(UNKNOWN_TYPE.isNullable());
    assertTrue(UNKNOWN_TYPE.isVoidable());

    // getGreatestCommonType
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(STRING_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertTrue(UNKNOWN_TYPE.matchesNumberContext());
    assertTrue(UNKNOWN_TYPE.matchesObjectContext());
    assertTrue(UNKNOWN_TYPE.matchesStringContext());
    assertTrue(UNKNOWN_TYPE.matchesSymbolContext());

    // isPropertyType*
    assertPropertyTypeUnknown(UNKNOWN_TYPE, "XXX");

    // toString
    assertEquals("?", UNKNOWN_TYPE.toString());
    assertTrue(UNKNOWN_TYPE.hasDisplayName());
    assertEquals("Unknown", UNKNOWN_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(UNKNOWN_TYPE);
    assertFalse(UNKNOWN_TYPE.isNominalConstructor());

    assertEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getPropertyType("abc"));
  }

  /** Tests the behavior of the checked unknown type. */
  @Test
  public void testCheckedUnknownType() {
    // isPropertyType*
    assertPropertyTypeUnknown(CHECKED_UNKNOWN_TYPE, "XXX");

    // toString
    assertEquals("??", CHECKED_UNKNOWN_TYPE.toString());
    assertTrue(CHECKED_UNKNOWN_TYPE.hasDisplayName());
    assertEquals("Unknown", CHECKED_UNKNOWN_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(CHECKED_UNKNOWN_TYPE);
    assertFalse(CHECKED_UNKNOWN_TYPE.isNominalConstructor());

    assertEquals(CHECKED_UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE.getPropertyType("abc"));
  }

  /** Tests the behavior of the unknown type. */
  @Test
  public void testAllType() {
    // isXxx
    assertFalse(ALL_TYPE.isArrayType());
    assertFalse(ALL_TYPE.isBooleanValueType());
    assertFalse(ALL_TYPE.isDateType());
    assertFalse(ALL_TYPE.isEnumElementType());
    assertFalse(ALL_TYPE.isNamedType());
    assertFalse(ALL_TYPE.isNullType());
    assertFalse(ALL_TYPE.isNumber());
    assertFalse(ALL_TYPE.isNumberObjectType());
    assertFalse(ALL_TYPE.isNumberValueType());
    assertFalse(ALL_TYPE.isObject());
    assertFalse(ALL_TYPE.isFunctionPrototypeType());
    assertFalse(ALL_TYPE.isRegexpType());
    assertFalse(ALL_TYPE.isString());
    assertFalse(ALL_TYPE.isStringObjectType());
    assertFalse(ALL_TYPE.isStringValueType());
    assertFalse(ALL_TYPE.isSymbol());
    assertFalse(ALL_TYPE.isSymbolObjectType());
    assertFalse(ALL_TYPE.isSymbolValueType());
    assertFalse(ALL_TYPE.isEnumType());
    assertFalse(ALL_TYPE.isUnionType());
    assertFalse(ALL_TYPE.isStruct());
    assertFalse(ALL_TYPE.isDict());
    assertTrue(ALL_TYPE.isAllType());
    assertFalse(ALL_TYPE.isVoidType());
    assertFalse(ALL_TYPE.isConstructor());
    assertFalse(ALL_TYPE.isInstanceType());

    // isSubtype
    assertFalse(ALL_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(ALL_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(functionType));
    assertFalse(ALL_TYPE.isSubtypeOf(recordType));
    assertFalse(ALL_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(ALL_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(ALL_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(ALL_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(VOID_TYPE));
    assertTrue(ALL_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(ALL_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ALL_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, functionType);
    assertCanTestForEqualityWith(ALL_TYPE, recordType);
    assertCanTestForEqualityWith(ALL_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(recordType));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertTrue(ALL_TYPE.isNullable());
    assertTrue(ALL_TYPE.isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertFalse(ALL_TYPE.matchesNumberContext()); // ?
    assertTrue(ALL_TYPE.matchesObjectContext());
    assertTrue(ALL_TYPE.matchesStringContext());
    assertFalse(ALL_TYPE.matchesSymbolContext()); // ?

    // toString
    assertEquals("*", ALL_TYPE.toString());

    assertTrue(ALL_TYPE.hasDisplayName());
    assertEquals("<Any Type>", ALL_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(ALL_TYPE);
    assertFalse(ALL_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the Object type (the object at the top of the JavaScript hierarchy). */
  @Test
  public void testTheObjectType() {
    // implicit prototype
    assertTypeEquals(OBJECT_PROTOTYPE, OBJECT_TYPE.getImplicitPrototype());

    // isXxx
    assertFalse(OBJECT_TYPE.isNoObjectType());
    assertFalse(OBJECT_TYPE.isNoType());
    assertFalse(OBJECT_TYPE.isArrayType());
    assertFalse(OBJECT_TYPE.isBooleanValueType());
    assertFalse(OBJECT_TYPE.isDateType());
    assertFalse(OBJECT_TYPE.isEnumElementType());
    assertFalse(OBJECT_TYPE.isNullType());
    assertFalse(OBJECT_TYPE.isNamedType());
    assertFalse(OBJECT_TYPE.isNullType());
    assertFalse(OBJECT_TYPE.isNumber());
    assertFalse(OBJECT_TYPE.isNumberObjectType());
    assertFalse(OBJECT_TYPE.isNumberValueType());
    assertTrue(OBJECT_TYPE.isObject());
    assertFalse(OBJECT_TYPE.isFunctionPrototypeType());
    assertTrue(OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(OBJECT_TYPE.isRegexpType());
    assertFalse(OBJECT_TYPE.isString());
    assertFalse(OBJECT_TYPE.isStringObjectType());
    assertFalse(OBJECT_TYPE.isStringValueType());
    assertFalse(OBJECT_TYPE.isSymbol());
    assertFalse(OBJECT_TYPE.isSymbolObjectType());
    assertFalse(OBJECT_TYPE.isSymbolValueType());
    assertFalse(OBJECT_TYPE.isEnumType());
    assertFalse(OBJECT_TYPE.isUnionType());
    assertFalse(OBJECT_TYPE.isStruct());
    assertFalse(OBJECT_TYPE.isDict());
    assertFalse(OBJECT_TYPE.isAllType());
    assertFalse(OBJECT_TYPE.isVoidType());
    assertFalse(OBJECT_TYPE.isConstructor());
    assertTrue(OBJECT_TYPE.isInstanceType());

    // isSubtype
    assertFalse(OBJECT_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(OBJECT_TYPE.isSubtypeOf(recordType));
    assertFalse(OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(namedGoogBar));
    assertTrue(OBJECT_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(OBJECT_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(OBJECT_TYPE, recordType);
    assertCannotTestForEqualityWith(OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // isNullable
    assertFalse(OBJECT_TYPE.isNullable());
    assertFalse(OBJECT_TYPE.isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        OBJECT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(OBJECT_TYPE, OBJECT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(OBJECT_TYPE, SYMBOL_TYPE), OBJECT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createUnionType(OBJECT_TYPE, NUMBER_TYPE),
        OBJECT_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertEquals(7, OBJECT_TYPE.getPropertiesCount());
    assertReturnTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        OBJECT_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        OBJECT_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(UNKNOWN_TYPE,
        OBJECT_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("hasOwnProperty"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("isPrototypeOf"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("propertyIsEnumerable"));

    // matchesXxx
    assertFalse(OBJECT_TYPE.matchesNumberContext());
    assertTrue(OBJECT_TYPE.matchesObjectContext());
    assertTrue(OBJECT_TYPE.matchesStringContext());
    assertFalse(OBJECT_TYPE.matchesSymbolContext());

    // implicit prototype
    assertTypeEquals(OBJECT_PROTOTYPE, OBJECT_TYPE.getImplicitPrototype());

    // toString
    assertEquals("Object", OBJECT_TYPE.toString());

    assertTrue(OBJECT_TYPE.isNativeObjectType());
    assertTrue(OBJECT_TYPE.getImplicitPrototype().isNativeObjectType());

    Asserts.assertResolvesToSame(OBJECT_TYPE);
    assertFalse(OBJECT_TYPE.isNominalConstructor());
    assertTrue(OBJECT_TYPE.getConstructor().isNominalConstructor());
  }

  /** Tests the behavior of the number value type. */
  @Test
  public void testNumberObjectType() {
    // isXxx
    assertFalse(NUMBER_OBJECT_TYPE.isArrayType());
    assertFalse(NUMBER_OBJECT_TYPE.isBooleanObjectType());
    assertFalse(NUMBER_OBJECT_TYPE.isBooleanValueType());
    assertFalse(NUMBER_OBJECT_TYPE.isDateType());
    assertFalse(NUMBER_OBJECT_TYPE.isEnumElementType());
    assertFalse(NUMBER_OBJECT_TYPE.isNamedType());
    assertFalse(NUMBER_OBJECT_TYPE.isNullType());
    assertTrue(NUMBER_OBJECT_TYPE.isNumber());
    assertTrue(NUMBER_OBJECT_TYPE.isNumberObjectType());
    assertFalse(NUMBER_OBJECT_TYPE.isNumberValueType());
    assertTrue(NUMBER_OBJECT_TYPE.isObject());
    assertFalse(NUMBER_OBJECT_TYPE.isFunctionPrototypeType());
    assertTrue(
        NUMBER_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(NUMBER_OBJECT_TYPE.isRegexpType());
    assertFalse(NUMBER_OBJECT_TYPE.isString());
    assertFalse(NUMBER_OBJECT_TYPE.isStringObjectType());
    assertFalse(NUMBER_OBJECT_TYPE.isStringValueType());
    assertFalse(NUMBER_OBJECT_TYPE.isSymbol());
    assertFalse(NUMBER_OBJECT_TYPE.isSymbolObjectType());
    assertFalse(NUMBER_OBJECT_TYPE.isSymbolValueType());
    assertFalse(NUMBER_OBJECT_TYPE.isEnumType());
    assertFalse(NUMBER_OBJECT_TYPE.isUnionType());
    assertFalse(NUMBER_OBJECT_TYPE.isStruct());
    assertFalse(NUMBER_OBJECT_TYPE.isDict());
    assertFalse(NUMBER_OBJECT_TYPE.isAllType());
    assertFalse(NUMBER_OBJECT_TYPE.isVoidType());
    assertFalse(NUMBER_OBJECT_TYPE.isConstructor());
    assertTrue(NUMBER_OBJECT_TYPE.isInstanceType());

    // autoboxesTo
    assertTypeEquals(NUMBER_OBJECT_TYPE, NUMBER_TYPE.autoboxesTo());

    // unboxesTo
    assertTypeEquals(NUMBER_TYPE, NUMBER_OBJECT_TYPE.unboxesTo());

    // isSubtype
    assertTrue(NUMBER_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(namedGoogBar));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtypeOf(
            createUnionType(NUMBER_OBJECT_TYPE, NULL_TYPE)));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtypeOf(
            createUnionType(NUMBER_TYPE, NULL_TYPE)));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(NUMBER_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, elementsType);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(NUMBER_OBJECT_TYPE.isNullable());
    assertFalse(NUMBER_OBJECT_TYPE.isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        NUMBER_OBJECT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, STRING_OBJECT_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_OBJECT_TYPE, SYMBOL_OBJECT_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_OBJECT_TYPE, SYMBOL_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, NUMBER_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, functionType),
        NUMBER_OBJECT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE,
        NUMBER_OBJECT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, DATE_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, REGEXP_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertTrue(NUMBER_OBJECT_TYPE.matchesNumberContext());
    assertTrue(NUMBER_OBJECT_TYPE.matchesObjectContext());
    assertTrue(NUMBER_OBJECT_TYPE.matchesStringContext());
    assertFalse(NUMBER_OBJECT_TYPE.matchesSymbolContext());

    // toString
    assertEquals("Number", NUMBER_OBJECT_TYPE.toString());
    assertTrue(NUMBER_OBJECT_TYPE.hasDisplayName());
    assertEquals("Number", NUMBER_OBJECT_TYPE.getDisplayName());

    assertTrue(NUMBER_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(NUMBER_OBJECT_TYPE);
  }

  /** Tests the behavior of the number value type. */
  @Test
  public void testNumberValueType() {
    // isXxx
    assertFalse(NUMBER_TYPE.isArrayType());
    assertFalse(NUMBER_TYPE.isBooleanObjectType());
    assertFalse(NUMBER_TYPE.isBooleanValueType());
    assertFalse(NUMBER_TYPE.isDateType());
    assertFalse(NUMBER_TYPE.isEnumElementType());
    assertFalse(NUMBER_TYPE.isNamedType());
    assertFalse(NUMBER_TYPE.isNullType());
    assertTrue(NUMBER_TYPE.isNumber());
    assertFalse(NUMBER_TYPE.isNumberObjectType());
    assertTrue(NUMBER_TYPE.isNumberValueType());
    assertFalse(NUMBER_TYPE.isFunctionPrototypeType());
    assertFalse(NUMBER_TYPE.isRegexpType());
    assertFalse(NUMBER_TYPE.isString());
    assertFalse(NUMBER_TYPE.isStringObjectType());
    assertFalse(NUMBER_TYPE.isStringValueType());
    assertFalse(NUMBER_TYPE.isSymbol());
    assertFalse(NUMBER_TYPE.isSymbolObjectType());
    assertFalse(NUMBER_TYPE.isSymbolValueType());
    assertFalse(NUMBER_TYPE.isEnumType());
    assertFalse(NUMBER_TYPE.isUnionType());
    assertFalse(NUMBER_TYPE.isStruct());
    assertFalse(NUMBER_TYPE.isDict());
    assertFalse(NUMBER_TYPE.isAllType());
    assertFalse(NUMBER_TYPE.isVoidType());
    assertFalse(NUMBER_TYPE.isConstructor());
    assertFalse(NUMBER_TYPE.isInstanceType());

    // autoboxesTo
    assertTypeEquals(NUMBER_OBJECT_TYPE, NUMBER_TYPE.autoboxesTo());

    // isSubtype
    assertTrue(NUMBER_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertTrue(NUMBER_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(functionType));
    assertFalse(NUMBER_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NUMBER_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(NUMBER_TYPE.isSubtypeOf(namedGoogBar));
    assertTrue(NUMBER_TYPE.isSubtypeOf(
            createUnionType(NUMBER_TYPE, NULL_TYPE)));
    assertTrue(NUMBER_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(NUMBER_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, functionType);
    assertCannotTestForEqualityWith(NUMBER_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NUMBER_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(NUMBER_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(NUMBER_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // isNullable
    assertFalse(NUMBER_TYPE.isNullable());
    assertFalse(NUMBER_TYPE.isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        NUMBER_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, SYMBOL_OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, SYMBOL_TYPE), NUMBER_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, functionType),
        NUMBER_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(createUnionType(NUMBER_TYPE, OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, DATE_TYPE),
        NUMBER_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, REGEXP_TYPE),
        NUMBER_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertTrue(NUMBER_TYPE.matchesNumberContext());
    assertTrue(NUMBER_TYPE.matchesObjectContext());
    assertTrue(NUMBER_TYPE.matchesStringContext());
    assertFalse(NUMBER_TYPE.matchesSymbolContext());

    // toString
    assertEquals("number", NUMBER_TYPE.toString());
    assertTrue(NUMBER_TYPE.hasDisplayName());
    assertEquals("number", NUMBER_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(NUMBER_TYPE);
    assertFalse(NUMBER_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the null type. */
  @Test
  public void testNullType() {

    // isXxx
    assertFalse(NULL_TYPE.isArrayType());
    assertFalse(NULL_TYPE.isBooleanValueType());
    assertFalse(NULL_TYPE.isDateType());
    assertFalse(NULL_TYPE.isEnumElementType());
    assertFalse(NULL_TYPE.isNamedType());
    assertTrue(NULL_TYPE.isNullType());
    assertFalse(NULL_TYPE.isNumber());
    assertFalse(NULL_TYPE.isNumberObjectType());
    assertFalse(NULL_TYPE.isNumberValueType());
    assertFalse(NULL_TYPE.isFunctionPrototypeType());
    assertFalse(NULL_TYPE.isRegexpType());
    assertFalse(NULL_TYPE.isString());
    assertFalse(NULL_TYPE.isStringObjectType());
    assertFalse(NULL_TYPE.isStringValueType());
    assertFalse(NULL_TYPE.isSymbol());
    assertFalse(NULL_TYPE.isSymbolObjectType());
    assertFalse(NULL_TYPE.isSymbolValueType());
    assertFalse(NULL_TYPE.isEnumType());
    assertFalse(NULL_TYPE.isUnionType());
    assertFalse(NULL_TYPE.isStruct());
    assertFalse(NULL_TYPE.isDict());
    assertFalse(NULL_TYPE.isAllType());
    assertFalse(NULL_TYPE.isVoidType());
    assertFalse(NULL_TYPE.isConstructor());
    assertFalse(NULL_TYPE.isInstanceType());

    // autoboxesTo
    assertNull(NULL_TYPE.autoboxesTo());

    // isSubtype
    assertFalse(NULL_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(NULL_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(NULL_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(functionType));
    assertFalse(NULL_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(NULL_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(NULL_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(NO_OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(NO_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(NULL_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(ALL_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(STRING_OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(SYMBOL_OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(SYMBOL_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(NUMBER_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(functionType)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(DATE_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(REGEXP_TYPE)));
    assertTrue(NULL_TYPE.isSubtypeOf(createNullableType(ARRAY_TYPE)));

    // canBeCalled
    assertFalse(NULL_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NULL_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NULL_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NULL_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, ARRAY_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, BOOLEAN_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, functionType);
    assertCannotTestForEqualityWith(NULL_TYPE, NULL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, REGEXP_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(NULL_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(
            createNullableType(STRING_OBJECT_TYPE)));

    // getLeastSupertype
    assertTypeEquals(NULL_TYPE, NULL_TYPE.getLeastSupertype(NULL_TYPE));
    assertTypeEquals(ALL_TYPE, NULL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createNullableType(STRING_OBJECT_TYPE),
        NULL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createNullableType(SYMBOL_OBJECT_TYPE), NULL_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(createNullableType(SYMBOL_TYPE), NULL_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createNullableType(NUMBER_TYPE),
        NULL_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createNullableType(functionType),
        NULL_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(createNullableType(OBJECT_TYPE),
        NULL_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createNullableType(DATE_TYPE),
        NULL_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createNullableType(REGEXP_TYPE),
        NULL_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertTrue(NULL_TYPE.matchesNumberContext());
    assertFalse(NULL_TYPE.matchesObjectContext());
    assertTrue(NULL_TYPE.matchesStringContext());
    assertFalse(NULL_TYPE.matchesSymbolContext());

    // toString
    assertEquals("null", NULL_TYPE.toString());
    assertTrue(NULL_TYPE.hasDisplayName());
    assertEquals("null", NULL_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(NULL_TYPE);

    // getGreatestSubtype
    assertTrue(
        NULL_TYPE.isSubtypeOf(
            createUnionType(forwardDeclaredNamedType, NULL_TYPE)));
    assertTypeEquals(
        createUnionType(forwardDeclaredNamedType, NULL_TYPE),
        NULL_TYPE.getGreatestSubtype(
            createUnionType(forwardDeclaredNamedType, NULL_TYPE)));
    assertFalse(NULL_TYPE.isNominalConstructor());

    assertTrue(NULL_TYPE.differsFrom(UNKNOWN_TYPE));
  }

  @Test
  public void testDateTypeX() {
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, functionType);
  }

  /** Tests the behavior of the Date type. */
  @Test
  public void testDateType() {
    // isXxx
    assertFalse(DATE_TYPE.isArrayType());
    assertFalse(DATE_TYPE.isBooleanValueType());
    assertTrue(DATE_TYPE.isDateType());
    assertFalse(DATE_TYPE.isEnumElementType());
    assertFalse(DATE_TYPE.isNamedType());
    assertFalse(DATE_TYPE.isNullType());
    assertFalse(DATE_TYPE.isNumberValueType());
    assertFalse(DATE_TYPE.isFunctionPrototypeType());
    assertTrue(DATE_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(DATE_TYPE.isRegexpType());
    assertFalse(DATE_TYPE.isStringValueType());
    assertFalse(DATE_TYPE.isSymbolValueType());
    assertFalse(DATE_TYPE.isEnumType());
    assertFalse(DATE_TYPE.isUnionType());
    assertFalse(DATE_TYPE.isStruct());
    assertFalse(DATE_TYPE.isDict());
    assertFalse(DATE_TYPE.isAllType());
    assertFalse(DATE_TYPE.isVoidType());
    assertFalse(DATE_TYPE.isConstructor());
    assertTrue(DATE_TYPE.isInstanceType());

    // autoboxesTo
    assertNull(DATE_TYPE.autoboxesTo());

    // isSubtype
    assertFalse(DATE_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(functionType));
    assertFalse(DATE_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(VOID_TYPE));

    // canBeCalled
    assertFalse(DATE_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(DATE_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(DATE_TYPE, SYMBOL_OBJECT_TYPE); // fix this
    assertCannotTestForEqualityWith(DATE_TYPE, SYMBOL_TYPE); // fix this
    assertCanTestForEqualityWith(DATE_TYPE, functionType);
    assertCannotTestForEqualityWith(DATE_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(DATE_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(DATE_TYPE.isNullable());
    assertFalse(DATE_TYPE.isVoidable());
    assertTrue(createNullableType(DATE_TYPE).isNullable());
    assertTrue(createUnionType(DATE_TYPE, VOID_TYPE).isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        DATE_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, STRING_OBJECT_TYPE),
        DATE_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, NUMBER_TYPE),
        DATE_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, functionType),
        DATE_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, DATE_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(DATE_TYPE, DATE_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, REGEXP_TYPE),
        DATE_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertEquals(46, DATE_TYPE.getImplicitPrototype().getPropertiesCount());
    assertEquals(46, DATE_TYPE.getPropertiesCount());
    assertReturnTypeEquals(DATE_TYPE, DATE_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toDateString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toTimeString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleDateString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleTimeString"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getTime"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMonth"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getDate"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCDate"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getDay"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getUTCDay"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getTimezoneOffset"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setTime"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCHours"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setDate"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCDate"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCFullYear"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toUTCString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toGMTString"));

    // matchesXxx
    assertTrue(DATE_TYPE.matchesNumberContext());
    assertTrue(DATE_TYPE.matchesObjectContext());
    assertTrue(DATE_TYPE.matchesStringContext());
    assertFalse(DATE_TYPE.matchesSymbolContext());

    // toString
    assertEquals("Date", DATE_TYPE.toString());
    assertTrue(DATE_TYPE.hasDisplayName());
    assertEquals("Date", DATE_TYPE.getDisplayName());

    assertTrue(DATE_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(DATE_TYPE);
    assertFalse(DATE_TYPE.isNominalConstructor());
    assertTrue(DATE_TYPE.getConstructor().isNominalConstructor());
  }

  /** Tests the behavior of the RegExp type. */
  @Test
  public void testRegExpType() {
    // isXxx
    assertFalse(REGEXP_TYPE.isNoType());
    assertFalse(REGEXP_TYPE.isNoObjectType());
    assertFalse(REGEXP_TYPE.isArrayType());
    assertFalse(REGEXP_TYPE.isBooleanValueType());
    assertFalse(REGEXP_TYPE.isDateType());
    assertFalse(REGEXP_TYPE.isEnumElementType());
    assertFalse(REGEXP_TYPE.isNamedType());
    assertFalse(REGEXP_TYPE.isNullType());
    assertFalse(REGEXP_TYPE.isNumberValueType());
    assertFalse(REGEXP_TYPE.isFunctionPrototypeType());
    assertTrue(REGEXP_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertTrue(REGEXP_TYPE.isRegexpType());
    assertFalse(REGEXP_TYPE.isStringValueType());
    assertFalse(REGEXP_TYPE.isSymbolValueType());
    assertFalse(REGEXP_TYPE.isEnumType());
    assertFalse(REGEXP_TYPE.isUnionType());
    assertFalse(REGEXP_TYPE.isStruct());
    assertFalse(REGEXP_TYPE.isDict());
    assertFalse(REGEXP_TYPE.isAllType());
    assertFalse(REGEXP_TYPE.isVoidType());

    // autoboxesTo
    assertNull(REGEXP_TYPE.autoboxesTo());

    // isSubtype
    assertFalse(REGEXP_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(functionType));
    assertFalse(REGEXP_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(REGEXP_TYPE.isSubtypeOf(VOID_TYPE));

    // canBeCalled
    assertFalse(REGEXP_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(REGEXP_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(REGEXP_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(REGEXP_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, functionType);
    assertCannotTestForEqualityWith(REGEXP_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(REGEXP_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(REGEXP_TYPE.isNullable());
    assertFalse(REGEXP_TYPE.isVoidable());
    assertTrue(createNullableType(REGEXP_TYPE).isNullable());
    assertTrue(createUnionType(REGEXP_TYPE, VOID_TYPE).isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        REGEXP_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, STRING_OBJECT_TYPE),
        REGEXP_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, NUMBER_TYPE),
        REGEXP_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, functionType),
        REGEXP_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, REGEXP_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, REGEXP_TYPE),
        REGEXP_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(REGEXP_TYPE,
        REGEXP_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertEquals(9, REGEXP_TYPE.getImplicitPrototype().getPropertiesCount());
    assertEquals(14, REGEXP_TYPE.getPropertiesCount());
    assertReturnTypeEquals(REGEXP_TYPE,
        REGEXP_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(createNullableType(ARRAY_TYPE),
        REGEXP_TYPE.getPropertyType("exec"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        REGEXP_TYPE.getPropertyType("test"));
    assertReturnTypeEquals(STRING_TYPE,
        REGEXP_TYPE.getPropertyType("toString"));
    assertTypeEquals(STRING_TYPE, REGEXP_TYPE.getPropertyType("source"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("global"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("ignoreCase"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("multiline"));
    assertTypeEquals(NUMBER_TYPE, REGEXP_TYPE.getPropertyType("lastIndex"));

    // matchesXxx
    assertFalse(REGEXP_TYPE.matchesNumberContext());
    assertTrue(REGEXP_TYPE.matchesObjectContext());
    assertTrue(REGEXP_TYPE.matchesStringContext());
    assertFalse(REGEXP_TYPE.matchesSymbolContext());

    // toString
    assertEquals("RegExp", REGEXP_TYPE.toString());
    assertTrue(REGEXP_TYPE.hasDisplayName());
    assertEquals("RegExp", REGEXP_TYPE.getDisplayName());

    assertTrue(REGEXP_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(REGEXP_TYPE);
    assertFalse(REGEXP_TYPE.isNominalConstructor());
    assertTrue(REGEXP_TYPE.getConstructor().isNominalConstructor());
  }

  /** Tests the behavior of the string object type. */
  @Test
  public void testStringObjectType() {
    // isXxx
    assertFalse(STRING_OBJECT_TYPE.isArrayType());
    assertFalse(STRING_OBJECT_TYPE.isBooleanObjectType());
    assertFalse(STRING_OBJECT_TYPE.isBooleanValueType());
    assertFalse(STRING_OBJECT_TYPE.isDateType());
    assertFalse(STRING_OBJECT_TYPE.isEnumElementType());
    assertFalse(STRING_OBJECT_TYPE.isNamedType());
    assertFalse(STRING_OBJECT_TYPE.isNullType());
    assertFalse(STRING_OBJECT_TYPE.isNumber());
    assertFalse(STRING_OBJECT_TYPE.isNumberObjectType());
    assertFalse(STRING_OBJECT_TYPE.isNumberValueType());
    assertFalse(STRING_OBJECT_TYPE.isFunctionPrototypeType());
    assertTrue(
        STRING_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(STRING_OBJECT_TYPE.isRegexpType());
    assertTrue(STRING_OBJECT_TYPE.isString());
    assertTrue(STRING_OBJECT_TYPE.isStringObjectType());
    assertFalse(STRING_OBJECT_TYPE.isStringValueType());
    assertFalse(STRING_OBJECT_TYPE.isSymbol());
    assertFalse(STRING_OBJECT_TYPE.isSymbolObjectType());
    assertFalse(STRING_OBJECT_TYPE.isSymbolValueType());
    assertFalse(STRING_OBJECT_TYPE.isEnumType());
    assertFalse(STRING_OBJECT_TYPE.isUnionType());
    assertFalse(STRING_OBJECT_TYPE.isStruct());
    assertFalse(STRING_OBJECT_TYPE.isDict());
    assertFalse(STRING_OBJECT_TYPE.isAllType());
    assertFalse(STRING_OBJECT_TYPE.isVoidType());
    assertFalse(STRING_OBJECT_TYPE.isConstructor());
    assertTrue(STRING_OBJECT_TYPE.isInstanceType());

    // autoboxesTo
    assertTypeEquals(STRING_OBJECT_TYPE, STRING_TYPE.autoboxesTo());

    // unboxesTo
    assertTypeEquals(STRING_TYPE, STRING_OBJECT_TYPE.unboxesTo());

    // isSubtype
    assertTrue(STRING_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertTrue(STRING_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(STRING_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtypeOf(STRING_TYPE));

    // canBeCalled
    assertFalse(STRING_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_TYPE);
    assertCannotTestForEqualityWith(STRING_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // properties (ECMA-262 page 98 - 106)
    assertEquals(24, STRING_OBJECT_TYPE.getImplicitPrototype().
        getPropertiesCount());
    assertEquals(25, STRING_OBJECT_TYPE.getPropertiesCount());

    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("charAt"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("charCodeAt"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("concat"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("indexOf"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("lastIndexOf"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("localeCompare"));
    assertReturnTypeEquals(createNullableType(ARRAY_TYPE),
        STRING_OBJECT_TYPE.getPropertyType("match"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("replace"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("search"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("slice"));
    assertReturnTypeEquals(ARRAY_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("split"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("substr"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("substring"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLowerCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLocaleLowerCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toUpperCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLocaleUpperCase"));
    assertTypeEquals(NUMBER_TYPE, STRING_OBJECT_TYPE.getPropertyType("length"));

    // matchesXxx
    assertTrue(STRING_OBJECT_TYPE.matchesNumberContext());
    assertTrue(STRING_OBJECT_TYPE.matchesObjectContext());
    assertTrue(STRING_OBJECT_TYPE.matchesStringContext());
    assertFalse(STRING_OBJECT_TYPE.matchesSymbolContext());

    // isNullable
    assertFalse(STRING_OBJECT_TYPE.isNullable());
    assertFalse(STRING_OBJECT_TYPE.isVoidable());
    assertTrue(createNullableType(STRING_OBJECT_TYPE).isNullable());
    assertTrue(createUnionType(STRING_OBJECT_TYPE, VOID_TYPE).isVoidable());

    assertTrue(STRING_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(STRING_OBJECT_TYPE);

    assertTrue(STRING_OBJECT_TYPE.hasDisplayName());
    assertEquals("String", STRING_OBJECT_TYPE.getDisplayName());
    assertFalse(STRING_OBJECT_TYPE.isNominalConstructor());
    assertTrue(STRING_OBJECT_TYPE.getConstructor().isNominalConstructor());
  }

  /** Tests the behavior of the string value type. */
  @Test
  public void testStringValueType() {
    // isXxx
    assertFalse(STRING_TYPE.isArrayType());
    assertFalse(STRING_TYPE.isBooleanObjectType());
    assertFalse(STRING_TYPE.isBooleanValueType());
    assertFalse(STRING_TYPE.isDateType());
    assertFalse(STRING_TYPE.isEnumElementType());
    assertFalse(STRING_TYPE.isNamedType());
    assertFalse(STRING_TYPE.isNullType());
    assertFalse(STRING_TYPE.isNumber());
    assertFalse(STRING_TYPE.isNumberObjectType());
    assertFalse(STRING_TYPE.isNumberValueType());
    assertFalse(STRING_TYPE.isFunctionPrototypeType());
    assertFalse(STRING_TYPE.isRegexpType());
    assertTrue(STRING_TYPE.isString());
    assertFalse(STRING_TYPE.isStringObjectType());
    assertTrue(STRING_TYPE.isStringValueType());
    assertFalse(STRING_TYPE.isSymbol());
    assertFalse(STRING_TYPE.isSymbolObjectType());
    assertFalse(STRING_TYPE.isSymbolValueType());
    assertFalse(STRING_TYPE.isEnumType());
    assertFalse(STRING_TYPE.isUnionType());
    assertFalse(STRING_TYPE.isStruct());
    assertFalse(STRING_TYPE.isDict());
    assertFalse(STRING_TYPE.isAllType());
    assertFalse(STRING_TYPE.isVoidType());
    assertFalse(STRING_TYPE.isConstructor());
    assertFalse(STRING_TYPE.isInstanceType());

    // autoboxesTo
    assertTypeEquals(STRING_OBJECT_TYPE, STRING_TYPE.autoboxesTo());

    // unboxesTo
    assertTypeEquals(STRING_TYPE, STRING_OBJECT_TYPE.unboxesTo());

    // isSubtype
    assertTrue(STRING_TYPE.isSubtypeOf(ALL_TYPE));
    assertTrue(STRING_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(STRING_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(STRING_TYPE.isSubtypeOf(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(STRING_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(STRING_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, functionType);
    assertCanTestForEqualityWith(STRING_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertTrue(STRING_TYPE.matchesNumberContext());
    assertTrue(STRING_TYPE.matchesObjectContext());
    assertTrue(STRING_TYPE.matchesStringContext());
    assertFalse(STRING_TYPE.matchesSymbolContext());

    // isNullable
    assertFalse(STRING_TYPE.isNullable());
    assertFalse(STRING_TYPE.isVoidable());
    assertTrue(createNullableType(STRING_TYPE).isNullable());
    assertTrue(createUnionType(STRING_TYPE, VOID_TYPE).isVoidable());

    // toString
    assertEquals("string", STRING_TYPE.toString());
    assertTrue(STRING_TYPE.hasDisplayName());
    assertEquals("string", STRING_TYPE.getDisplayName());

    // findPropertyType
    assertTypeEquals(NUMBER_TYPE, STRING_TYPE.findPropertyType("length"));
    assertEquals(null, STRING_TYPE.findPropertyType("unknownProperty"));

    Asserts.assertResolvesToSame(STRING_TYPE);
    assertFalse(STRING_TYPE.isNominalConstructor());
  }

  /** Tests the behavior of the symbol type. */
  @Test
  public void testSymbolValueType() {
    // isXxx
    assertFalse(SYMBOL_TYPE.isArrayType());
    assertFalse(SYMBOL_TYPE.isBooleanObjectType());
    assertFalse(SYMBOL_TYPE.isBooleanValueType());
    assertFalse(SYMBOL_TYPE.isDateType());
    assertFalse(SYMBOL_TYPE.isEnumElementType());
    assertFalse(SYMBOL_TYPE.isNamedType());
    assertFalse(SYMBOL_TYPE.isNullType());
    assertFalse(SYMBOL_TYPE.isNumberObjectType());
    assertFalse(SYMBOL_TYPE.isNumberValueType());
    assertFalse(SYMBOL_TYPE.isFunctionPrototypeType());
    assertFalse(SYMBOL_TYPE.isRegexpType());
    assertFalse(SYMBOL_TYPE.isStringObjectType());
    assertFalse(SYMBOL_TYPE.isStringValueType());
    assertFalse(SYMBOL_TYPE.isEnumType());
    assertFalse(SYMBOL_TYPE.isUnionType());
    assertFalse(SYMBOL_TYPE.isStruct());
    assertFalse(SYMBOL_TYPE.isDict());
    assertFalse(SYMBOL_TYPE.isAllType());
    assertFalse(SYMBOL_TYPE.isVoidType());
    assertFalse(SYMBOL_TYPE.isConstructor());
    assertFalse(SYMBOL_TYPE.isInstanceType());
    assertTrue(SYMBOL_TYPE.isSymbol());
    assertFalse(SYMBOL_TYPE.isSymbolObjectType());
    assertTrue(SYMBOL_TYPE.isSymbolValueType());

    // autoboxesTo
    assertTypeEquals(SYMBOL_OBJECT_TYPE, SYMBOL_TYPE.autoboxesTo());

    // unboxesTo
    assertTypeEquals(SYMBOL_TYPE, SYMBOL_OBJECT_TYPE.unboxesTo());

    // isSubtype
    assertTrue(SYMBOL_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(functionType));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(SYMBOL_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(SYMBOL_TYPE.isSubtypeOf(REGEXP_TYPE));

    // canBeCalled
    assertFalse(SYMBOL_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(SYMBOL_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, functionType);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(SYMBOL_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)); // ?
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(SYMBOL_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(SYMBOL_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(SYMBOL_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // isNullable
    assertFalse(SYMBOL_TYPE.isNullable());
    assertFalse(SYMBOL_TYPE.isVoidable());

    // matchesXxx
    assertFalse(SYMBOL_TYPE.matchesNumberContext());
    assertTrue(SYMBOL_TYPE.matchesObjectContext());
    assertFalse(SYMBOL_TYPE.matchesStringContext());
    assertTrue(SYMBOL_TYPE.matchesSymbolContext());

    // toString
    assertEquals("symbol", SYMBOL_TYPE.toString());
    assertTrue(SYMBOL_TYPE.hasDisplayName());
    assertEquals("symbol", SYMBOL_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(SYMBOL_TYPE);
  }

  /** Tests the behavior of the Symbol type. */
  @Test
  public void testSymbolObjectType() {
    // isXxx
    assertFalse(SYMBOL_OBJECT_TYPE.isArrayType());
    assertFalse(SYMBOL_OBJECT_TYPE.isBooleanObjectType());
    assertFalse(SYMBOL_OBJECT_TYPE.isBooleanValueType());
    assertFalse(SYMBOL_OBJECT_TYPE.isDateType());
    assertFalse(SYMBOL_OBJECT_TYPE.isEnumElementType());
    assertFalse(SYMBOL_OBJECT_TYPE.isNamedType());
    assertFalse(SYMBOL_OBJECT_TYPE.isNullType());
    assertFalse(SYMBOL_OBJECT_TYPE.isNumberObjectType());
    assertFalse(SYMBOL_OBJECT_TYPE.isNumberValueType());
    assertFalse(SYMBOL_OBJECT_TYPE.isFunctionPrototypeType());
    assertTrue(SYMBOL_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(SYMBOL_OBJECT_TYPE.isRegexpType());
    assertFalse(SYMBOL_OBJECT_TYPE.isStringObjectType());
    assertFalse(SYMBOL_OBJECT_TYPE.isStringValueType());
    assertFalse(SYMBOL_OBJECT_TYPE.isEnumType());
    assertFalse(SYMBOL_OBJECT_TYPE.isUnionType());
    assertFalse(SYMBOL_OBJECT_TYPE.isStruct());
    assertFalse(SYMBOL_OBJECT_TYPE.isDict());
    assertFalse(SYMBOL_OBJECT_TYPE.isAllType());
    assertFalse(SYMBOL_OBJECT_TYPE.isVoidType());
    assertFalse(SYMBOL_OBJECT_TYPE.isConstructor());
    assertTrue(SYMBOL_OBJECT_TYPE.isInstanceType());
    assertTrue(SYMBOL_OBJECT_TYPE.isSymbol());
    assertTrue(SYMBOL_OBJECT_TYPE.isSymbolObjectType());
    assertFalse(SYMBOL_OBJECT_TYPE.isSymbolValueType());

    // isSubtype
    assertTrue(SYMBOL_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(SYMBOL_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    // canBeCalled
    assertFalse(SYMBOL_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(SYMBOL_OBJECT_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, functionType);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(SYMBOL_OBJECT_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(SYMBOL_OBJECT_TYPE.isNullable());
    assertFalse(SYMBOL_OBJECT_TYPE.isVoidable());

    // matchesXxx
    assertFalse(SYMBOL_OBJECT_TYPE.matchesNumberContext());
    assertTrue(SYMBOL_OBJECT_TYPE.matchesObjectContext());
    assertFalse(SYMBOL_OBJECT_TYPE.matchesStringContext());
    assertTrue(SYMBOL_OBJECT_TYPE.matchesSymbolContext());

    // toString
    assertEquals("Symbol", SYMBOL_OBJECT_TYPE.toString());
    assertTrue(SYMBOL_OBJECT_TYPE.hasDisplayName());
    assertEquals("Symbol", SYMBOL_OBJECT_TYPE.getDisplayName());

    assertTrue(SYMBOL_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(SYMBOL_OBJECT_TYPE);
  }

  private void assertPropertyTypeDeclared(ObjectType ownerType, String prop) {
    assertTrue(ownerType.isPropertyTypeDeclared(prop));
    assertFalse(ownerType.isPropertyTypeInferred(prop));
  }

  private void assertPropertyTypeInferred(ObjectType ownerType, String prop) {
    assertFalse(ownerType.isPropertyTypeDeclared(prop));
    assertTrue(ownerType.isPropertyTypeInferred(prop));
  }

  private void assertPropertyTypeUnknown(ObjectType ownerType, String prop) {
    assertFalse(ownerType.isPropertyTypeDeclared(prop));
    assertFalse(ownerType.isPropertyTypeInferred(prop));
    assertTrue(ownerType.getPropertyType(prop).isUnknownType());
  }

  private void assertReturnTypeEquals(JSType expectedReturnType,
      JSType function) {
    assertTrue(function instanceof FunctionType);
    assertTypeEquals(expectedReturnType,
        ((FunctionType) function).getReturnType());
  }

  /** Tests the behavior of record types. */
  @Test
  public void testRecordType() {
    // isXxx
    assertTrue(recordType.isObject());
    assertFalse(recordType.isFunctionPrototypeType());

    // isSubtype
    assertTrue(recordType.isSubtypeOf(ALL_TYPE));
    assertFalse(recordType.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(recordType.isSubtypeOf(NUMBER_TYPE));
    assertFalse(recordType.isSubtypeOf(SYMBOL_OBJECT_TYPE));
    assertFalse(recordType.isSubtypeOf(SYMBOL_TYPE));
    assertFalse(recordType.isSubtypeOf(DATE_TYPE));
    assertFalse(recordType.isSubtypeOf(REGEXP_TYPE));
    assertTrue(recordType.isSubtypeOf(UNKNOWN_TYPE));
    assertTrue(recordType.isSubtypeOf(OBJECT_TYPE));
    assertFalse(recordType.isSubtypeOf(U2U_CONSTRUCTOR_TYPE));

    // autoboxesTo
    assertNull(recordType.autoboxesTo());

    // canBeCalled
    assertFalse(recordType.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(recordType, ALL_TYPE);
    assertCanTestForEqualityWith(recordType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, recordType);
    assertCanTestForEqualityWith(recordType, functionType);
    assertCanTestForEqualityWith(recordType, OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, NUMBER_TYPE);
    assertCanTestForEqualityWith(recordType, DATE_TYPE);
    assertCanTestForEqualityWith(recordType, REGEXP_TYPE);
    assertCanTestForEqualityWith(recordType, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, SYMBOL_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(recordType.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(recordType.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(recordType));
    assertFalse(recordType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(recordType.matchesNumberContext());
    assertTrue(recordType.matchesObjectContext());
    assertFalse(recordType.matchesStringContext());
    assertFalse(recordType.matchesSymbolContext());

    Asserts.assertResolvesToSame(recordType);
  }

  /** Tests the behavior of the instance of Function. */
  @Test
  public void testFunctionInstanceType() {
    FunctionType functionInst = FUNCTION_INSTANCE_TYPE;

    // isXxx
    assertTrue(functionInst.isObject());
    assertFalse(functionInst.isFunctionPrototypeType());
    assertTrue(functionInst.getImplicitPrototype()
        .isFunctionPrototypeType());

    // isSubtype
    assertTrue(functionInst.isSubtype(ALL_TYPE));
    assertFalse(functionInst.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(functionInst.isSubtype(NUMBER_TYPE));
    assertFalse(functionInst.isSubtype(SYMBOL_OBJECT_TYPE));
    assertFalse(functionInst.isSubtype(SYMBOL_TYPE));
    assertFalse(functionInst.isSubtype(DATE_TYPE));
    assertFalse(functionInst.isSubtype(REGEXP_TYPE));
    assertTrue(functionInst.isSubtype(UNKNOWN_TYPE));
    assertTrue(functionInst.isSubtype(U2U_CONSTRUCTOR_TYPE));

    // autoboxesTo
    assertNull(functionInst.autoboxesTo());

    // canBeCalled
    assertTrue(functionInst.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(functionInst, ALL_TYPE);
    assertCanTestForEqualityWith(functionInst, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(functionInst, functionInst);
    assertCanTestForEqualityWith(functionInst, OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionInst, NUMBER_TYPE);
    assertCannotTestForEqualityWith(functionInst, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionInst, SYMBOL_TYPE);
    assertCanTestForEqualityWith(functionInst, DATE_TYPE);
    assertCanTestForEqualityWith(functionInst, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(functionInst.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(functionInst.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(functionInst));
    assertFalse(functionInst.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(functionInst.matchesNumberContext());
    assertTrue(functionInst.matchesObjectContext());
    assertFalse(functionInst.matchesStringContext());
    assertFalse(functionInst.matchesSymbolContext());

    // hasProperty
    assertTrue(functionInst.hasProperty("prototype"));
    assertPropertyTypeInferred(functionInst, "prototype");

    // misc
    assertTypeEquals(FUNCTION_FUNCTION_TYPE, functionInst.getConstructor());
    assertTypeEquals(FUNCTION_PROTOTYPE, functionInst.getImplicitPrototype());
    assertTypeEquals(functionInst, FUNCTION_FUNCTION_TYPE.getInstanceType());

    Asserts.assertResolvesToSame(functionInst);
  }

  /** Tests the behavior of functional types. */
  @Test
  public void testFunctionType() {
    // isXxx
    assertTrue(functionType.isObject());
    assertFalse(functionType.isFunctionPrototypeType());
    assertTrue(functionType.getImplicitPrototype().getImplicitPrototype()
        .isFunctionPrototypeType());

    // isSubtype
    assertTrue(functionType.isSubtype(ALL_TYPE));
    assertFalse(functionType.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(functionType.isSubtype(NUMBER_TYPE));
    assertFalse(functionType.isSubtype(SYMBOL_OBJECT_TYPE));
    assertFalse(functionType.isSubtype(SYMBOL_TYPE));
    assertFalse(functionType.isSubtype(DATE_TYPE));
    assertFalse(functionType.isSubtype(REGEXP_TYPE));
    assertTrue(functionType.isSubtype(UNKNOWN_TYPE));
    assertTrue(functionType.isSubtype(U2U_CONSTRUCTOR_TYPE));

    // autoboxesTo
    assertNull(functionType.autoboxesTo());

    // canBeCalled
    assertTrue(functionType.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(functionType, ALL_TYPE);
    assertCanTestForEqualityWith(functionType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(functionType, functionType);
    assertCanTestForEqualityWith(functionType, OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionType, NUMBER_TYPE);
    assertCanTestForEqualityWith(functionType, DATE_TYPE);
    assertCanTestForEqualityWith(functionType, REGEXP_TYPE);
    assertCannotTestForEqualityWith(functionType, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionType, SYMBOL_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(functionType.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(functionType.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(functionType));
    assertFalse(functionType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(SYMBOL_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(functionType.matchesNumberContext());
    assertTrue(functionType.matchesObjectContext());
    assertFalse(functionType.matchesStringContext());
    assertFalse(functionType.matchesSymbolContext());

    // hasProperty
    assertTrue(functionType.hasProperty("prototype"));
    assertPropertyTypeInferred(functionType, "prototype");

    Asserts.assertResolvesToSame(functionType);


    assertEquals("aFunctionName", new FunctionBuilder(registry).
        withName("aFunctionName").build().getDisplayName());
  }

  /** Tests the subtyping relation of record types. */
  @Test
  public void testRecordTypeSubtyping() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    assertTrue(subRecordType.isSubtypeOf(recordType));
    assertFalse(recordType.isSubtypeOf(subRecordType));

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", OBJECT_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    JSType differentRecordType = builder.build();

    assertFalse(differentRecordType.isSubtypeOf(recordType));
    assertFalse(recordType.isSubtypeOf(differentRecordType));
  }

  /** Tests the subtyping relation of record types when an object has an inferred property.. */
  @Test
  public void testRecordTypeSubtypingWithInferredProperties() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", googSubBarInst, null);
    JSType record = builder.build();

    ObjectType subtypeProp = registry.createAnonymousObjectType(null);
    subtypeProp.defineInferredProperty("a", googSubSubBarInst, null);
    assertTrue(subtypeProp.isSubtypeOf(record));
    assertFalse(record.isSubtypeOf(subtypeProp));

    ObjectType supertypeProp = registry.createAnonymousObjectType(null);
    supertypeProp.defineInferredProperty("a", googBarInst, null);
    assertFalse(supertypeProp.isSubtypeOf(record));
    assertFalse(record.isSubtypeOf(supertypeProp));

    ObjectType declaredSubtypeProp = registry.createAnonymousObjectType(null);
    declaredSubtypeProp.defineDeclaredProperty("a", googSubSubBarInst, null);
    assertTrue(declaredSubtypeProp.isSubtypeOf(record));
    assertFalse(record.isSubtypeOf(declaredSubtypeProp));
  }

  /** Tests the getLeastSupertype method for record types. */
  @Test
  public void testRecordTypeLeastSuperType1() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    JSType leastSupertype = recordType.getLeastSupertype(subRecordType);
    assertTypeEquals(leastSupertype, recordType);
  }

  @Test
  public void testRecordTypeLeastSuperType2() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("e", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType otherRecordType = builder.build();

    assertTypeEquals(
        registry.createUnionType(recordType, otherRecordType),
        recordType.getLeastSupertype(otherRecordType));
  }

  @Test
  public void testRecordTypeLeastSuperType3() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);
    JSType otherRecordType = builder.build();

    assertTypeEquals(
        registry.createUnionType(recordType, otherRecordType),
        recordType.getLeastSupertype(otherRecordType));
  }

  @Test
  public void testRecordTypeLeastSuperType4() {
    JSType leastSupertype = recordType.getLeastSupertype(OBJECT_TYPE);
    assertTypeEquals(leastSupertype, OBJECT_TYPE);
  }

  /** Tests the getGreatestSubtype method for record types. */
  @Test
  public void testRecordTypeGreatestSubType1() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType2() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType3() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType4() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);
    assertTypeEquals(subtype, NO_TYPE);
  }

  @Test
  public void testRecordTypeGreatestSubType5() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));

    // if Function is given a property "a" of type "string", then it's
    // a subtype of the record type {a: string}.
    U2U_CONSTRUCTOR_TYPE.defineDeclaredProperty("a", STRING_TYPE, null);
    assertTypeEquals(U2U_CONSTRUCTOR_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTypeEquals(U2U_CONSTRUCTOR_TYPE,
                 U2U_CONSTRUCTOR_TYPE.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType6() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("x", UNKNOWN_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));

    // if Function is given a property "x" of type "string", then it's
    // also a subtype of the record type {x: ?}.
    U2U_CONSTRUCTOR_TYPE.defineDeclaredProperty("x", STRING_TYPE, null);
    assertTypeEquals(U2U_CONSTRUCTOR_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTypeEquals(U2U_CONSTRUCTOR_TYPE,
                 U2U_CONSTRUCTOR_TYPE.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType7() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("x", NUMBER_TYPE, null);

    JSType recordType = builder.build();

    // if Function is given a property "x" of type "string", then it's
    // not a subtype of the record type {x: number}.
    U2U_CONSTRUCTOR_TYPE.defineDeclaredProperty("x", STRING_TYPE, null);
    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));
  }

  @Test
  public void testRecordTypeGreatestSubType8() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("xyz", UNKNOWN_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));

    // if goog.Bar is given a property "xyz" of type "string", then it's
    // also a subtype of the record type {x: ?}.
    googBar.defineDeclaredProperty("xyz", STRING_TYPE, null);

    assertTypeEquals(googBar,
                 recordType.getGreatestSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTypeEquals(googBar,
                 U2U_CONSTRUCTOR_TYPE.getGreatestSubtype(recordType));

    ObjectType googBarInst = googBar.getInstanceType();
    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(googBarInst));
    assertTypeEquals(NO_OBJECT_TYPE,
                 googBarInst.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType9() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType1.getGreatestSubtype(recordType2);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType10() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType11() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", createUnionType(NUMBER_TYPE, STRING_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", createUnionType(NUMBER_TYPE, BOOLEAN_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType12() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    FunctionType googBarArgConstructor =
        registry.createConstructorType(
            "barArg", null, registry.createParameters(googBar), null, null, false);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBarArgConstructor, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", registry.getNativeObjectType(
        JSTypeNative.NO_OBJECT_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  /** Tests the "apply" method on the function type. */
  @Test
  public void testApplyOfDateMethod() {
    JSType applyType = dateMethod.getPropertyType("apply");
    assertTrue("apply should be a function",
        applyType instanceof FunctionType);

    FunctionType applyFn = (FunctionType) applyType;
    assertTypeEquals("apply should have the same return type as its function",
        NUMBER_TYPE, applyFn.getReturnType());

    Node params = applyFn.getParametersNode();
    assertEquals("apply takes two args",
        2, params.getChildCount());
    assertTypeEquals("apply's first arg is the @this type",
        registry.createOptionalNullableType(DATE_TYPE),
        params.getFirstChild().getJSType());
    assertTypeEquals("apply's second arg is an Array",
        registry.createOptionalNullableType(OBJECT_TYPE),
        params.getLastChild().getJSType());
    assertTrue("apply's args must be optional",
        params.getFirstChild().isOptionalArg());
    assertTrue("apply's args must be optional",
        params.getLastChild().isOptionalArg());
  }

  /** Tests the "call" method on the function type. */
  @Test
  public void testCallOfDateMethod() {
    JSType callType = dateMethod.getPropertyType("call");
    assertTrue("call should be a function",
        callType instanceof FunctionType);

    FunctionType callFn = (FunctionType) callType;
    assertTypeEquals("call should have the same return type as its function",
        NUMBER_TYPE, callFn.getReturnType());

    Node params = callFn.getParametersNode();
    assertEquals("call takes one argument in this case",
        1, params.getChildCount());
    assertTypeEquals("call's first arg is the @this type",
        registry.createOptionalNullableType(DATE_TYPE),
        params.getFirstChild().getJSType());
    assertTrue("call's args must be optional",
        params.getFirstChild().isOptionalArg());
  }

  /** Tests the representation of function types. */
  @Test
  public void testFunctionTypeRepresentation() {
    assertEquals("function(number, string): boolean",
        registry.createFunctionType(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE).toString());

    assertEquals("function(new:Array, ...*): Array",
        ARRAY_FUNCTION_TYPE.toString());

    assertEquals("function(new:Boolean, *=): boolean",
        BOOLEAN_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function(new:Number, *=): number",
        NUMBER_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function(new:String, *=): string",
        STRING_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function(...number): boolean",
        registry.createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE).toString());

    assertEquals("function(number, ...string): boolean",
        registry.createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE).toString());

    assertEquals("function(this:Date, number): (boolean|number|string)",
        new FunctionBuilder(registry)
            .withParamsNode(registry.createParameters(NUMBER_TYPE))
            .withReturnType(NUMBER_STRING_BOOLEAN)
            .withTypeOfThis(DATE_TYPE)
            .build().toString());
  }

  /** Tests relationships between structural function types. */
  @Test
  public void testFunctionTypeRelationships() {
    FunctionType dateMethodEmpty = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(DATE_TYPE).build();
    FunctionType dateMethodWithParam = new FunctionBuilder(registry)
        .withParamsNode(registry.createOptionalParameters(NUMBER_TYPE))
        .withTypeOfThis(DATE_TYPE).build();
    FunctionType dateMethodWithReturn = new FunctionBuilder(registry)
        .withReturnType(NUMBER_TYPE)
        .withTypeOfThis(DATE_TYPE).build();
    FunctionType stringMethodEmpty = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withTypeOfThis(STRING_OBJECT_TYPE).build();
    FunctionType stringMethodWithParam = new FunctionBuilder(registry)
        .withParamsNode(registry.createOptionalParameters(NUMBER_TYPE))
        .withTypeOfThis(STRING_OBJECT_TYPE).build();
    FunctionType stringMethodWithReturn = new FunctionBuilder(registry)
        .withReturnType(NUMBER_TYPE)
        .withTypeOfThis(STRING_OBJECT_TYPE).build();

    // One-off tests.
    assertFalse(stringMethodEmpty.isSubtype(dateMethodEmpty));

    // Systemic tests.
    List<FunctionType> allFunctions = ImmutableList.of(
        dateMethodEmpty, dateMethodWithParam, dateMethodWithReturn,
        stringMethodEmpty, stringMethodWithParam, stringMethodWithReturn);
    for (int i = 0; i < allFunctions.size(); i++) {
      for (int j = 0; j < allFunctions.size(); j++) {
        FunctionType typeA = allFunctions.get(i);
        FunctionType typeB = allFunctions.get(j);
        assertEquals(String.format("equals(%s, %s)", typeA, typeB),
            i == j, typeA.isEquivalentTo(typeB));

        // For this particular set of functions, the functions are subtypes
        // of each other iff they have the same "this" type.
        assertEquals(String.format("isSubtype(%s, %s)", typeA, typeB),
            typeA.getTypeOfThis().isEquivalentTo(typeB.getTypeOfThis()),
            typeA.isSubtype(typeB));

        if (i == j) {
          assertTypeEquals(typeA, typeA.getLeastSupertype(typeB));
          assertTypeEquals(typeA, typeA.getGreatestSubtype(typeB));
        } else {
          assertTypeEquals(String.format("sup(%s, %s)", typeA, typeB),
              U2U_CONSTRUCTOR_TYPE, typeA.getLeastSupertype(typeB));
          assertTypeEquals(String.format("inf(%s, %s)", typeA, typeB),
              LEAST_FUNCTION_TYPE, typeA.getGreatestSubtype(typeB));
        }
      }
    }
  }

  @Test
  public void testProxiedFunctionTypeRelationships() {
    FunctionType dateMethodEmpty = new FunctionBuilder(registry)
      .withParamsNode(registry.createParameters())
      .withTypeOfThis(DATE_TYPE).build().toMaybeFunctionType();
    FunctionType dateMethodWithParam = new FunctionBuilder(registry)
      .withParamsNode(registry.createParameters(NUMBER_TYPE))
      .withTypeOfThis(DATE_TYPE).build().toMaybeFunctionType();
    ProxyObjectType proxyDateMethodEmpty =
        new ProxyObjectType(registry, dateMethodEmpty);
    ProxyObjectType proxyDateMethodWithParam =
        new ProxyObjectType(registry, dateMethodWithParam);

    assertTypeEquals(U2U_CONSTRUCTOR_TYPE,
        proxyDateMethodEmpty.getLeastSupertype(proxyDateMethodWithParam));
    assertTypeEquals(LEAST_FUNCTION_TYPE,
        proxyDateMethodEmpty.getGreatestSubtype(proxyDateMethodWithParam));
  }

  /** Tests relationships between structural function types. */
  @Test
  public void testFunctionSubTypeRelationships() {
    FunctionType googBarMethod = new FunctionBuilder(registry)
        .withTypeOfThis(googBar).build();
    FunctionType googBarParamFn = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(googBar)).build();
    FunctionType googBarReturnFn = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withReturnType(googBar).build();
    FunctionType googSubBarMethod = new FunctionBuilder(registry)
        .withTypeOfThis(googSubBar).build();
    FunctionType googSubBarParamFn = new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters(googSubBar)).build();
    FunctionType googSubBarReturnFn = new FunctionBuilder(registry)
        .withReturnType(googSubBar).build();

    assertTrue(googBarMethod.isSubtype(googSubBarMethod));
    assertTrue(googBarReturnFn.isSubtype(googSubBarReturnFn));

    List<FunctionType> allFunctions = ImmutableList.of(
        googBarMethod, googBarParamFn, googBarReturnFn,
        googSubBarMethod, googSubBarParamFn, googSubBarReturnFn);
    for (int i = 0; i < allFunctions.size(); i++) {
      for (int j = 0; j < allFunctions.size(); j++) {
        FunctionType typeA = allFunctions.get(i);
        FunctionType typeB = allFunctions.get(j);
        assertEquals(String.format("equals(%s, %s)", typeA, typeB),
            i == j, typeA.isEquivalentTo(typeB));

        // TODO(nicksantos): This formulation of least subtype and greatest
        // supertype is a bit loose. We might want to tighten it up later.
        if (i == j) {
          assertTypeEquals(typeA, typeA.getLeastSupertype(typeB));
          assertTypeEquals(typeA, typeA.getGreatestSubtype(typeB));
        } else {
          assertTypeEquals(String.format("sup(%s, %s)", typeA, typeB),
              U2U_CONSTRUCTOR_TYPE, typeA.getLeastSupertype(typeB));
          assertTypeEquals(String.format("inf(%s, %s)", typeA, typeB),
              LEAST_FUNCTION_TYPE, typeA.getGreatestSubtype(typeB));
        }
      }
    }
  }

  /**
   * Tests that defining a property of a function's {@code prototype} adds the property to it
   * instance type.
   */
  @Test
  public void testFunctionPrototypeAndImplicitPrototype1() {
    FunctionType constructor = registry.createConstructorType("Foo", null, null, null, null, false);
    ObjectType instance = constructor.getInstanceType();

    // adding one property on the prototype
    ObjectType prototype =
        (ObjectType) constructor.getPropertyType("prototype");
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);

    assertEquals(NATIVE_PROPERTIES_COUNT + 1, instance.getPropertiesCount());
  }

  /**
   * Tests that replacing a function's {@code prototype} changes the visible properties of its
   * instance type.
   */
  @Test
  public void testFunctionPrototypeAndImplicitPrototype2() {
    FunctionType constructor =
        registry.createConstructorType(
            null, null, registry.createParameters(null, null, null), null, null, false);
    ObjectType instance = constructor.getInstanceType();

    // replacing the prototype
    ObjectType prototype = registry.createAnonymousObjectType(null);
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);
    constructor.defineDeclaredProperty("prototype", prototype, null);

    assertEquals(NATIVE_PROPERTIES_COUNT + 1, instance.getPropertiesCount());
  }

  /** Tests assigning JsDoc on a prototype property. */
  @Test
  public void testJSDocOnPrototypeProperty() {
    subclassCtor.setPropertyJSDocInfo("prototype",
        new JSDocInfoBuilder(false).build());
    assertNull(subclassCtor.getOwnPropertyJSDocInfo("prototype"));
  }

  /**
   * Tests operation of {@code isVoidable}.
   *
   * @throws Exception
   */
  @Test
  public void testIsVoidable() {
    assertTrue(VOID_TYPE.isVoidable());
    assertFalse(NULL_TYPE.isVoidable());
    assertTrue(createUnionType(NUMBER_TYPE, VOID_TYPE).isVoidable());
  }

  /** Tests the behavior of the void type. */
  @Test
  public void testVoidType() {
    // isSubtype
    assertTrue(VOID_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(VOID_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(VOID_TYPE.isSubtypeOf(REGEXP_TYPE));

    // autoboxesTo
    assertNull(VOID_TYPE.autoboxesTo());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(VOID_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(VOID_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(
            createUnionType(NUMBER_TYPE, VOID_TYPE)));

    // matchesXxx
    assertFalse(VOID_TYPE.matchesNumberContext());
    assertFalse(VOID_TYPE.matchesNumberContext());
    assertFalse(VOID_TYPE.matchesObjectContext());
    assertTrue(VOID_TYPE.matchesStringContext());
    assertFalse(VOID_TYPE.matchesNumberContext());

    Asserts.assertResolvesToSame(VOID_TYPE);
  }

  /** Tests the behavior of the boolean type. */
  @Test
  public void testBooleanValueType() {
    // isXxx
    assertFalse(BOOLEAN_TYPE.isArrayType());
    assertFalse(BOOLEAN_TYPE.isBooleanObjectType());
    assertTrue(BOOLEAN_TYPE.isBooleanValueType());
    assertFalse(BOOLEAN_TYPE.isDateType());
    assertFalse(BOOLEAN_TYPE.isEnumElementType());
    assertFalse(BOOLEAN_TYPE.isNamedType());
    assertFalse(BOOLEAN_TYPE.isNullType());
    assertFalse(BOOLEAN_TYPE.isNumberObjectType());
    assertFalse(BOOLEAN_TYPE.isNumberValueType());
    assertFalse(BOOLEAN_TYPE.isFunctionPrototypeType());
    assertFalse(BOOLEAN_TYPE.isRegexpType());
    assertFalse(BOOLEAN_TYPE.isStringObjectType());
    assertFalse(BOOLEAN_TYPE.isStringValueType());
    assertFalse(BOOLEAN_TYPE.isEnumType());
    assertFalse(BOOLEAN_TYPE.isUnionType());
    assertFalse(BOOLEAN_TYPE.isStruct());
    assertFalse(BOOLEAN_TYPE.isDict());
    assertFalse(BOOLEAN_TYPE.isAllType());
    assertFalse(BOOLEAN_TYPE.isVoidType());
    assertFalse(BOOLEAN_TYPE.isConstructor());
    assertFalse(BOOLEAN_TYPE.isInstanceType());

    // autoboxesTo
    assertTypeEquals(BOOLEAN_OBJECT_TYPE, BOOLEAN_TYPE.autoboxesTo());

    // unboxesTo
    assertTypeEquals(BOOLEAN_TYPE, BOOLEAN_OBJECT_TYPE.unboxesTo());

    // isSubtype
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(functionType));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(REGEXP_TYPE));

    // canBeCalled
    assertFalse(BOOLEAN_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(BOOLEAN_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(BOOLEAN_TYPE, functionType);
    assertCannotTestForEqualityWith(BOOLEAN_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(BOOLEAN_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // isNullable
    assertFalse(BOOLEAN_TYPE.isNullable());
    assertFalse(BOOLEAN_TYPE.isVoidable());

    // matchesXxx
    assertTrue(BOOLEAN_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_TYPE.matchesObjectContext());
    assertTrue(BOOLEAN_TYPE.matchesStringContext());
    assertTrue(BOOLEAN_TYPE.matchesNumberContext());

    // toString
    assertEquals("boolean", BOOLEAN_TYPE.toString());
    assertTrue(BOOLEAN_TYPE.hasDisplayName());
    assertEquals("boolean", BOOLEAN_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(BOOLEAN_TYPE);
  }

  /** Tests the behavior of the Boolean type. */
  @Test
  public void testBooleanObjectType() {
    // isXxx
    assertFalse(BOOLEAN_OBJECT_TYPE.isArrayType());
    assertTrue(BOOLEAN_OBJECT_TYPE.isBooleanObjectType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isBooleanValueType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isDateType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isEnumElementType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isNamedType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isNullType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isNumberObjectType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isNumberValueType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isFunctionPrototypeType());
    assertTrue(
        BOOLEAN_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isRegexpType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isStringObjectType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isStringValueType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isEnumType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isUnionType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isStruct());
    assertFalse(BOOLEAN_OBJECT_TYPE.isDict());
    assertFalse(BOOLEAN_OBJECT_TYPE.isAllType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isVoidType());
    assertFalse(BOOLEAN_OBJECT_TYPE.isConstructor());
    assertTrue(BOOLEAN_OBJECT_TYPE.isInstanceType());

    // isSubtype
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(namedGoogBar));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    // canBeCalled
    assertFalse(BOOLEAN_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, functionType);
    assertCannotTestForEqualityWith(BOOLEAN_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(BOOLEAN_OBJECT_TYPE.isNullable());
    assertFalse(BOOLEAN_OBJECT_TYPE.isVoidable());

    // matchesXxx
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesObjectContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesStringContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesNumberContext());

    // toString
    assertEquals("Boolean", BOOLEAN_OBJECT_TYPE.toString());
    assertTrue(BOOLEAN_OBJECT_TYPE.hasDisplayName());
    assertEquals("Boolean", BOOLEAN_OBJECT_TYPE.getDisplayName());

    assertTrue(BOOLEAN_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(BOOLEAN_OBJECT_TYPE);
  }

  /** Tests the behavior of the enum type. */
  @Test
  public void testEnumType() {
    EnumType enumType = new EnumType(registry, "Enum", null, NUMBER_TYPE);

    // isXxx
    assertFalse(enumType.isArrayType());
    assertFalse(enumType.isBooleanObjectType());
    assertFalse(enumType.isBooleanValueType());
    assertFalse(enumType.isDateType());
    assertFalse(enumType.isEnumElementType());
    assertFalse(enumType.isNamedType());
    assertFalse(enumType.isNullType());
    assertFalse(enumType.isNumberObjectType());
    assertFalse(enumType.isNumberValueType());
    assertFalse(enumType.isFunctionPrototypeType());
    assertFalse(enumType.isRegexpType());
    assertFalse(enumType.isStringObjectType());
    assertFalse(enumType.isStringValueType());
    assertTrue(enumType.isEnumType());
    assertFalse(enumType.isUnionType());
    assertFalse(enumType.isStruct());
    assertFalse(enumType.isDict());
    assertFalse(enumType.isAllType());
    assertFalse(enumType.isVoidType());
    assertFalse(enumType.isConstructor());
    assertFalse(enumType.isInstanceType());

    // isSubtype
    assertTrue(enumType.isSubtype(ALL_TYPE));
    assertFalse(enumType.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(enumType.isSubtype(NUMBER_TYPE));
    assertFalse(enumType.isSubtype(functionType));
    assertFalse(enumType.isSubtype(NULL_TYPE));
    assertTrue(enumType.isSubtype(OBJECT_TYPE));
    assertFalse(enumType.isSubtype(DATE_TYPE));
    assertTrue(enumType.isSubtype(unresolvedNamedType));
    assertFalse(enumType.isSubtype(namedGoogBar));
    assertFalse(enumType.isSubtype(REGEXP_TYPE));

    // canBeCalled
    assertFalse(enumType.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(enumType, ALL_TYPE);
    assertCanTestForEqualityWith(enumType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(enumType, NUMBER_TYPE);
    assertCanTestForEqualityWith(enumType, functionType);
    assertCannotTestForEqualityWith(enumType, VOID_TYPE);
    assertCanTestForEqualityWith(enumType, OBJECT_TYPE);
    assertCanTestForEqualityWith(enumType, DATE_TYPE);
    assertCanTestForEqualityWith(enumType, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(enumType.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(enumType.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(enumType.
        canTestForShallowEqualityWith(enumType));
    assertFalse(enumType.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(functionType));
    assertFalse(enumType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(enumType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(enumType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(enumType.isNullable());
    assertFalse(enumType.isVoidable());

    // matchesXxx
    assertFalse(enumType.matchesNumberContext());
    assertFalse(enumType.matchesNumberContext());
    assertTrue(enumType.matchesObjectContext());
    assertTrue(enumType.matchesStringContext());
    assertFalse(enumType.matchesNumberContext());

    // toString
    assertEquals("enum{Enum}", enumType.toString());
    assertTrue(enumType.hasDisplayName());
    assertEquals("Enum", enumType.getDisplayName());

    assertEquals("AnotherEnum", new EnumType(registry, "AnotherEnum",
        null, NUMBER_TYPE).getDisplayName());
    assertFalse(
        new EnumType(registry, null, null, NUMBER_TYPE).hasDisplayName());

    Asserts.assertResolvesToSame(enumType);
  }

  /** Tests the behavior of the enum element type. */
  @Test
  public void testEnumElementType() {
    // isXxx
    assertFalse(elementsType.isArrayType());
    assertFalse(elementsType.isBooleanObjectType());
    assertFalse(elementsType.isBooleanValueType());
    assertFalse(elementsType.isDateType());
    assertTrue(elementsType.isEnumElementType());
    assertFalse(elementsType.isNamedType());
    assertFalse(elementsType.isNullType());
    assertFalse(elementsType.isNumberObjectType());
    assertFalse(elementsType.isNumberValueType());
    assertFalse(elementsType.isFunctionPrototypeType());
    assertFalse(elementsType.isRegexpType());
    assertFalse(elementsType.isStringObjectType());
    assertFalse(elementsType.isStringValueType());
    assertFalse(elementsType.isEnumType());
    assertFalse(elementsType.isUnionType());
    assertFalse(elementsType.isStruct());
    assertFalse(elementsType.isDict());
    assertFalse(elementsType.isAllType());
    assertFalse(elementsType.isVoidType());
    assertFalse(elementsType.isConstructor());
    assertFalse(elementsType.isInstanceType());

    // isSubtype
    assertTrue(elementsType.isSubtype(ALL_TYPE));
    assertFalse(elementsType.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(elementsType.isSubtype(NUMBER_TYPE));
    assertFalse(elementsType.isSubtype(functionType));
    assertFalse(elementsType.isSubtype(NULL_TYPE));
    assertFalse(elementsType.isSubtype(OBJECT_TYPE)); // no more autoboxing
    assertFalse(elementsType.isSubtype(DATE_TYPE));
    assertTrue(elementsType.isSubtype(unresolvedNamedType));
    assertFalse(elementsType.isSubtype(namedGoogBar));
    assertFalse(elementsType.isSubtype(REGEXP_TYPE));

    // canBeCalled
    assertFalse(elementsType.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(elementsType, ALL_TYPE);
    assertCanTestForEqualityWith(elementsType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, NUMBER_TYPE);
    assertCanTestForEqualityWith(elementsType, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, elementsType);
    assertCannotTestForEqualityWith(elementsType, functionType);
    assertCannotTestForEqualityWith(elementsType, VOID_TYPE);
    assertCanTestForEqualityWith(elementsType, OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, DATE_TYPE);
    assertCanTestForEqualityWith(elementsType, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(elementsType.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(elementsType.
        canTestForShallowEqualityWith(elementsType));
    assertFalse(elementsType.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(functionType));
    assertFalse(elementsType.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(elementsType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(elementsType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(elementsType.isNullable());
    assertFalse(elementsType.isVoidable());

    // matchesXxx
    assertTrue(elementsType.matchesNumberContext());
    assertTrue(elementsType.matchesNumberContext());
    assertTrue(elementsType.matchesObjectContext());
    assertTrue(elementsType.matchesStringContext());
    assertTrue(elementsType.matchesNumberContext());

    // toString
    assertEquals("Enum<number>", elementsType.toString());
    assertTrue(elementsType.hasDisplayName());
    assertEquals("Enum", elementsType.getDisplayName());

    Asserts.assertResolvesToSame(elementsType);
  }

  @Test
  public void testStringEnumType() {
    EnumElementType stringEnum =
        new EnumType(registry, "Enum", null, STRING_TYPE).getElementsType();

    assertTypeEquals(UNKNOWN_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertEquals(false, stringEnum.hasProperty("length"));
    assertTypeEquals(STRING_OBJECT_TYPE, stringEnum.autoboxesTo());
    assertNull(stringEnum.getConstructor());

    Asserts.assertResolvesToSame(stringEnum);
  }

  @Test
  public void testStringObjectEnumType() {
    EnumElementType stringEnum =
        new EnumType(registry, "Enum", null, STRING_OBJECT_TYPE)
        .getElementsType();

    assertTypeEquals(NUMBER_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertEquals(true, stringEnum.hasProperty("length"));
    assertTypeEquals(STRING_OBJECT_FUNCTION_TYPE, stringEnum.getConstructor());
  }

  /** Tests object types. */
  @Test
  public void testObjectType() {
    PrototypeObjectType objectType =
        new PrototypeObjectType(registry, null, null);

    // isXxx
    assertFalse(objectType.isAllType());
    assertFalse(objectType.isArrayType());
    assertFalse(objectType.isDateType());
    assertFalse(objectType.isFunctionPrototypeType());
    assertSame(objectType.getImplicitPrototype(), OBJECT_TYPE);

    // isSubtype
    assertTrue(objectType.isSubtypeOf(ALL_TYPE));
    assertFalse(objectType.isSubtypeOf(STRING_OBJECT_TYPE));
    assertFalse(objectType.isSubtypeOf(NUMBER_TYPE));
    assertFalse(objectType.isSubtypeOf(functionType));
    assertFalse(objectType.isSubtypeOf(NULL_TYPE));
    assertFalse(objectType.isSubtypeOf(DATE_TYPE));
    assertTrue(objectType.isSubtypeOf(OBJECT_TYPE));
    assertTrue(objectType.isSubtypeOf(unresolvedNamedType));
    assertFalse(objectType.isSubtypeOf(namedGoogBar));
    assertFalse(objectType.isSubtypeOf(REGEXP_TYPE));

    // autoboxesTo
    assertNull(objectType.autoboxesTo());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(objectType, NUMBER_TYPE);

    // matchesXxxContext
    assertFalse(objectType.matchesNumberContext());
    assertFalse(objectType.matchesNumberContext());
    assertTrue(objectType.matchesObjectContext());
    assertFalse(objectType.matchesStringContext());
    assertFalse(objectType.matchesNumberContext());

    // isNullable
    assertFalse(objectType.isNullable());
    assertFalse(objectType.isVoidable());
    assertTrue(createNullableType(objectType).isNullable());
    assertTrue(createUnionType(objectType, VOID_TYPE).isVoidable());

    // toString
    assertEquals("{...}", objectType.toString());
    assertEquals(null, objectType.getDisplayName());
    assertType(objectType).getReferenceNameIsNull();
    assertEquals("anObject", new PrototypeObjectType(registry, "anObject",
        null).getDisplayName());

    Asserts.assertResolvesToSame(objectType);
  }

  /** Tests the goog.Bar type. */
  @Test
  public void testGoogBar() {
    assertTrue(namedGoogBar.isInstanceType());
    assertFalse(googBar.isInstanceType());
    assertFalse(namedGoogBar.isConstructor());
    assertTrue(googBar.isConstructor());
    assertTrue(googBar.getInstanceType().isInstanceType());
    assertTrue(namedGoogBar.getConstructor().isConstructor());
    assertTrue(namedGoogBar.getImplicitPrototype().isFunctionPrototypeType());

    // isSubtype
    assertTypeCanAssignToItself(googBar);
    assertTypeCanAssignToItself(namedGoogBar);
    googBar.isSubtype(namedGoogBar);
    namedGoogBar.isSubtype(googBar);
    assertTypeEquals(googBar, googBar);
    assertTypeNotEquals(googBar, googSubBar);

    Asserts.assertResolvesToSame(googBar);
    Asserts.assertResolvesToSame(googSubBar);
  }

  /** Tests how properties are counted for object types. */
  @Test
  public void testObjectTypePropertiesCount() {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", DATE_TYPE, null);
    assertEquals(nativeProperties + 1, sup.getPropertiesCount());

    sup.defineDeclaredProperty("b", DATE_TYPE, null);
    assertEquals(nativeProperties + 2, sup.getPropertiesCount());

    ObjectType sub = registry.createObjectType(null, sup);
    assertEquals(nativeProperties + 2, sub.getPropertiesCount());
  }

  /** Tests how properties are defined. */
  @Test
  public void testDefineProperties() {
    ObjectType prototype = googBar.getPrototype();
    ObjectType instance = googBar.getInstanceType();

    assertTypeEquals(instance.getImplicitPrototype(), prototype);

    // Test declarations.
    assertTrue(
        prototype.defineDeclaredProperty("declared", NUMBER_TYPE, null));
    assertFalse(
        prototype.defineDeclaredProperty("declared", NUMBER_TYPE, null));
    assertFalse(
        instance.defineDeclaredProperty("declared", NUMBER_TYPE, null));
    assertTypeEquals(NUMBER_TYPE, instance.getPropertyType("declared"));

    // Test inferring different types.
    assertTrue(prototype.defineInferredProperty("inferred1", STRING_TYPE,
        null));
    assertTrue(prototype.defineInferredProperty("inferred1", NUMBER_TYPE,
        null));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        instance.getPropertyType("inferred1"));

    // Test inferring different types on different objects.
    assertTrue(prototype.defineInferredProperty("inferred2", STRING_TYPE,
        null));
    assertTrue(instance.defineInferredProperty("inferred2", NUMBER_TYPE,
        null));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        instance.getPropertyType("inferred2"));

    // Test inferring on the supertype and declaring on the subtype.
    assertTrue(
        prototype.defineInferredProperty("prop", STRING_TYPE, null));
    assertTrue(
        instance.defineDeclaredProperty("prop", NUMBER_TYPE, null));
    assertTypeEquals(NUMBER_TYPE, instance.getPropertyType("prop"));
    assertTypeEquals(STRING_TYPE, prototype.getPropertyType("prop"));
  }

  /** Tests that properties are correctly counted even when shadowing occurs. */
  @Test
  public void testObjectTypePropertiesCountWithShadowing() {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertEquals(nativeProperties + 1, sup.getPropertiesCount());

    ObjectType sub = registry.createObjectType(null, sup);
    sub.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertEquals(nativeProperties + 1, sub.getPropertiesCount());
  }

  /** Tests the named type goog.Bar. */
  @Test
  public void testNamedGoogBar() {
    // isXxx
    assertFalse(namedGoogBar.isFunctionPrototypeType());
    assertTrue(namedGoogBar.getImplicitPrototype().isFunctionPrototypeType());

    // isSubtype
    assertTrue(namedGoogBar.isSubtype(ALL_TYPE));
    assertFalse(namedGoogBar.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(namedGoogBar.isSubtype(NUMBER_TYPE));
    assertFalse(namedGoogBar.isSubtype(functionType));
    assertFalse(namedGoogBar.isSubtype(NULL_TYPE));
    assertTrue(namedGoogBar.isSubtype(OBJECT_TYPE));
    assertFalse(namedGoogBar.isSubtype(DATE_TYPE));
    assertTrue(namedGoogBar.isSubtype(namedGoogBar));
    assertTrue(namedGoogBar.isSubtype(unresolvedNamedType));
    assertFalse(namedGoogBar.isSubtype(REGEXP_TYPE));
    assertFalse(namedGoogBar.isSubtype(ARRAY_TYPE));

    // autoboxesTo
    assertNull(namedGoogBar.autoboxesTo());

    // properties
    assertTypeEquals(DATE_TYPE, namedGoogBar.getPropertyType("date"));

    assertFalse(namedGoogBar.isNativeObjectType());
    assertFalse(namedGoogBar.getImplicitPrototype().isNativeObjectType());

    JSType resolvedNamedGoogBar = Asserts.assertValidResolve(namedGoogBar);
    assertNotSame(resolvedNamedGoogBar, namedGoogBar);
    assertSame(resolvedNamedGoogBar, googBar.getInstanceType());
  }

  /** Tests the prototype chaining of native objects. */
  @Test
  public void testPrototypeChaining() {
    // equals
    assertTypeEquals(
        ARRAY_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        BOOLEAN_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        DATE_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        NUMBER_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        STRING_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        REGEXP_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
  }

  /**
   * Tests that function instances have their constructor pointer back at the function that created
   * them.
   */
  @Test
  public void testInstanceFunctionChaining() {
    // Array
    assertTypeEquals(
        ARRAY_FUNCTION_TYPE, ARRAY_TYPE.getConstructor());

    // Boolean
    assertTypeEquals(
        BOOLEAN_OBJECT_FUNCTION_TYPE,
        BOOLEAN_OBJECT_TYPE.getConstructor());

    // Date
    assertTypeEquals(
        DATE_FUNCTION_TYPE, DATE_TYPE.getConstructor());

    // Number
    assertTypeEquals(
        NUMBER_OBJECT_FUNCTION_TYPE,
        NUMBER_OBJECT_TYPE.getConstructor());

    // Object
    assertTypeEquals(
        OBJECT_FUNCTION_TYPE, OBJECT_TYPE.getConstructor());

    // RegExp
    assertTypeEquals(REGEXP_FUNCTION_TYPE, REGEXP_TYPE.getConstructor());

    // String
    assertTypeEquals(
        STRING_OBJECT_FUNCTION_TYPE,
        STRING_OBJECT_TYPE.getConstructor());
  }

  /**
   * Tests that the method {@link JSType#canTestForEqualityWith(JSType)} handles special corner
   * cases.
   */
  @SuppressWarnings("checked")
  @Test
  public void testCanTestForEqualityWithCornerCases() {
    // null == undefined is always true
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // (Object,null) == undefined could be true or false
    UnionType nullableObject =
        (UnionType) createUnionType(OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(nullableObject, VOID_TYPE);
    assertCanTestForEqualityWith(VOID_TYPE, nullableObject);
  }

  /** Tests the {@link JSType#testForEquality(JSType)} method. */
  @Test
  public void testTestForEquality() {
    compare(TRUE, NO_OBJECT_TYPE, NO_OBJECT_TYPE);
    compare(UNKNOWN, ALL_TYPE, ALL_TYPE);
    compare(TRUE, NO_TYPE, NO_TYPE);
    compare(UNKNOWN, NO_RESOLVED_TYPE, NO_RESOLVED_TYPE);
    compare(UNKNOWN, NO_OBJECT_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, ALL_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, NO_TYPE, NUMBER_TYPE);

    compare(FALSE, NULL_TYPE, BOOLEAN_TYPE);
    compare(TRUE, NULL_TYPE, NULL_TYPE);
    compare(FALSE, NULL_TYPE, NUMBER_TYPE);
    compare(FALSE, NULL_TYPE, OBJECT_TYPE);
    compare(FALSE, NULL_TYPE, STRING_TYPE);
    compare(TRUE, NULL_TYPE, VOID_TYPE);
    compare(UNKNOWN, NULL_TYPE, createUnionType(UNKNOWN_TYPE, VOID_TYPE));
    compare(UNKNOWN, NULL_TYPE, createUnionType(OBJECT_TYPE, VOID_TYPE));
    compare(UNKNOWN, NULL_TYPE, unresolvedNamedType);
    compare(UNKNOWN,
        NULL_TYPE, createUnionType(unresolvedNamedType, DATE_TYPE));

    compare(FALSE, VOID_TYPE, REGEXP_TYPE);
    compare(TRUE, VOID_TYPE, VOID_TYPE);
    compare(UNKNOWN, VOID_TYPE, createUnionType(REGEXP_TYPE, VOID_TYPE));

    compare(UNKNOWN, NUMBER_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, NUMBER_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, NUMBER_TYPE, OBJECT_TYPE);

    compare(UNKNOWN, ARRAY_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, OBJECT_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, OBJECT_TYPE, STRING_TYPE);

    compare(UNKNOWN, STRING_TYPE, STRING_TYPE);

    compare(UNKNOWN, STRING_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, STRING_TYPE, NUMBER_TYPE);
    compare(FALSE, STRING_TYPE, VOID_TYPE);
    compare(FALSE, STRING_TYPE, NULL_TYPE);
    compare(FALSE, STRING_TYPE, createUnionType(NULL_TYPE, VOID_TYPE));

    compare(UNKNOWN, UNKNOWN_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, UNKNOWN_TYPE, NULL_TYPE);
    compare(UNKNOWN, UNKNOWN_TYPE, VOID_TYPE);

    compare(FALSE, U2U_CONSTRUCTOR_TYPE, BOOLEAN_TYPE);
    compare(FALSE, U2U_CONSTRUCTOR_TYPE, NUMBER_TYPE);
    compare(FALSE, U2U_CONSTRUCTOR_TYPE, STRING_TYPE);
    compare(FALSE, U2U_CONSTRUCTOR_TYPE, VOID_TYPE);
    compare(FALSE, U2U_CONSTRUCTOR_TYPE, NULL_TYPE);
    compare(UNKNOWN, U2U_CONSTRUCTOR_TYPE, OBJECT_TYPE);
    compare(UNKNOWN, U2U_CONSTRUCTOR_TYPE, ALL_TYPE);

    compare(UNKNOWN, NULL_TYPE, subclassOfUnresolvedNamedType);

    JSType functionAndNull = createUnionType(NULL_TYPE, dateMethod);
    compare(UNKNOWN, functionAndNull, dateMethod);

    compare(UNKNOWN, NULL_TYPE, NO_TYPE);
    compare(UNKNOWN, VOID_TYPE, NO_TYPE);
    compare(UNKNOWN, NULL_TYPE, unresolvedNamedType);
    compare(UNKNOWN, VOID_TYPE, unresolvedNamedType);
    compare(TRUE, NO_TYPE, NO_TYPE);
  }

  private void compare(TernaryValue r, JSType t1, JSType t2) {
    assertEquals(r, t1.testForEquality(t2));
    assertEquals(r, t2.testForEquality(t1));
  }

  private void assertCanTestForEqualityWith(JSType t1, JSType t2) {
    assertTrue(t1.canTestForEqualityWith(t2));
    assertTrue(t2.canTestForEqualityWith(t1));
  }

  private void assertCannotTestForEqualityWith(JSType t1, JSType t2) {
    assertFalse(t1.canTestForEqualityWith(t2));
    assertFalse(t2.canTestForEqualityWith(t1));
  }

  /** Tests the subtyping relationships among simple types. */
  @Test
  public void testSubtypingSimpleTypes() {
    // Any
    assertTrue(NO_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(functionType));
    assertTrue(NO_TYPE.isSubtypeOf(NULL_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(ALL_TYPE));
    assertTrue(NO_TYPE.isSubtypeOf(VOID_TYPE));

    // AnyObject
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtypeOf(VOID_TYPE));

    // Array
    assertFalse(ARRAY_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(functionType));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtypeOf(VOID_TYPE));

    // boolean
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(functionType));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtypeOf(VOID_TYPE));

    // Boolean
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtypeOf(VOID_TYPE));

    // Date
    assertFalse(DATE_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(DATE_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(functionType));
    assertFalse(DATE_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(DATE_TYPE.isSubtypeOf(VOID_TYPE));

    // Unknown
    assertFalse(ALL_TYPE.isSubtypeOf(NO_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(NO_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(ARRAY_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(BOOLEAN_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(functionType));
    assertFalse(ALL_TYPE.isSubtypeOf(NULL_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(NUMBER_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(REGEXP_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(STRING_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE));
    assertTrue(ALL_TYPE.isSubtypeOf(ALL_TYPE));
    assertFalse(ALL_TYPE.isSubtypeOf(VOID_TYPE));
  }

  /** Tests that the Object type is the greatest element (top) of the object hierarchy. */
  @Test
  public void testSubtypingObjectTopOfObjects() {
    assertTrue(OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE));
    assertTrue(createUnionType(DATE_TYPE, REGEXP_TYPE).isSubtypeOf(OBJECT_TYPE));
    assertTrue(createUnionType(OBJECT_TYPE, NO_OBJECT_TYPE).
        isSubtypeOf(OBJECT_TYPE));
    assertTrue(functionType.isSubtype(OBJECT_TYPE));
  }

  @Test
  public void testSubtypingFunctionPrototypeType() {
    FunctionType sub1 =
        registry.createConstructorType(
            null, null, registry.createParameters(null, null, null), null, null, false);
    sub1.setPrototypeBasedOn(googBar);
    FunctionType sub2 =
        registry.createConstructorType(
            null, null, registry.createParameters(null, null, null), null, null, false);
    sub2.setPrototypeBasedOn(googBar);

    ObjectType o1 = sub1.getInstanceType();
    ObjectType o2 = sub2.getInstanceType();

    assertFalse(o1.isSubtypeOf(o2));
    assertFalse(o1.getImplicitPrototype().isSubtypeOf(o2.getImplicitPrototype()));
    assertTrue(o1.getImplicitPrototype().isSubtypeOf(googBar));
    assertTrue(o2.getImplicitPrototype().isSubtypeOf(googBar));
  }

  @Test
  public void testSubtypingFunctionFixedArgs() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, BOOLEAN_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, BOOLEAN_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  @Test
  public void testSubtypingFunctionMultipleFixedArgs() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, ALL_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  @Test
  public void testSubtypingFunctionFixedArgsNotMatching() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, STRING_TYPE, UNKNOWN_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, ALL_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  @Test
  public void testSubtypingFunctionVariableArgsOneOnly() {
    // f1 = (string...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object) -> String
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertFalse(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  @Test
  public void testSubtypingFunctionVariableArgsBoth() {
    // f1 = (number, String, string...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, NUMBER_TYPE, STRING_OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object, string...) -> String
    FunctionType f2 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE, STRING_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  @Test
  public void testSubtypingMostGeneralFunction() {
    // (string, string) -> Object
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, STRING_TYPE, STRING_TYPE);
    // (string, void) -> number
    FunctionType f2 = registry.createFunctionType(NUMBER_TYPE, STRING_TYPE, VOID_TYPE);
    // (Date, string, number) -> AnyObject
    FunctionType f3 = registry.createFunctionType(
        NO_OBJECT_TYPE, DATE_TYPE, STRING_TYPE, NUMBER_TYPE);
    // (Number) -> Any
    FunctionType f4 = registry.createFunctionType(NO_TYPE, NUMBER_OBJECT_TYPE);
    // f1 = (string...) -> Object
    FunctionType f5 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object) -> String
    FunctionType f6 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE);
    // f1 = (number, string...) -> Object
    FunctionType f7 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
    // f2 = (string|number, Object, string...) -> String
    FunctionType f8 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE, STRING_TYPE);

    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(GREATEST_FUNCTION_TYPE));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(LEAST_FUNCTION_TYPE));

    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(LEAST_FUNCTION_TYPE));
    assertTrue(GREATEST_FUNCTION_TYPE.isSubtypeOf(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(GREATEST_FUNCTION_TYPE));

    assertTrue(f1.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f2.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f3.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f4.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f5.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f6.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f7.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(f8.isSubtype(GREATEST_FUNCTION_TYPE));

    assertFalse(f1.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f2.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f3.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f4.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f5.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f6.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f7.isSubtype(LEAST_FUNCTION_TYPE));
    assertFalse(f8.isSubtype(LEAST_FUNCTION_TYPE));

    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f1));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f2));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f3));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f4));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f5));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f6));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f7));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtypeOf(f8));

    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f1));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f2));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f3));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f4));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f5));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f6));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f7));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtypeOf(f8));
  }

  /** Types to test for symmetrical relationships. */
  private ImmutableList<JSType> getTypesToTestForSymmetry() {
    return ImmutableList.of(
        UNKNOWN_TYPE,
        NULL_TYPE,
        VOID_TYPE,
        NUMBER_TYPE,
        STRING_TYPE,
        BOOLEAN_TYPE,
        OBJECT_TYPE,
        U2U_CONSTRUCTOR_TYPE,
        LEAST_FUNCTION_TYPE,
        GREATEST_FUNCTION_TYPE,
        ALL_TYPE,
        NO_TYPE,
        NO_OBJECT_TYPE,
        NO_RESOLVED_TYPE,
        createUnionType(BOOLEAN_TYPE, STRING_TYPE),
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        createUnionType(NULL_TYPE, dateMethod),
        createUnionType(UNKNOWN_TYPE, dateMethod),
        createUnionType(namedGoogBar, dateMethod),
        createUnionType(NULL_TYPE, unresolvedNamedType),
        enumType,
        elementsType,
        dateMethod,
        functionType,
        unresolvedNamedType,
        googBar,
        namedGoogBar,
        googBar.getInstanceType(),
        namedGoogBar,
        subclassOfUnresolvedNamedType,
        subclassCtor,
        recordType,
        forwardDeclaredNamedType,
        createUnionType(forwardDeclaredNamedType, NULL_TYPE),
        createTemplatizedType(OBJECT_TYPE, STRING_TYPE),
        createTemplatizedType(OBJECT_TYPE, NUMBER_TYPE),
        createTemplatizedType(ARRAY_TYPE, STRING_TYPE),
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE),
        createUnionType(
            createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE),
        createUnionType(
            createTemplatizedType(OBJECT_TYPE, BOOLEAN_TYPE), NULL_TYPE)
        );
  }

  @Test
  public void testSymmetryOfTestForEquality() {
    List<JSType> listA = getTypesToTestForSymmetry();
    List<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        TernaryValue aOnB = typeA.testForEquality(typeB);
        TernaryValue bOnA = typeB.testForEquality(typeA);
        assertTrue(
            String.format("testForEquality not symmetrical:\n" +
                "typeA: %s\ntypeB: %s\n" +
                "a.testForEquality(b): %s\n" +
                "b.testForEquality(a): %s\n",
                typeA, typeB, aOnB, bOnA),
            aOnB == bOnA);
      }
    }
  }

  /** Tests that getLeastSupertype is a symmetric relation. */
  @Test
  public void testSymmetryOfLeastSupertype() {
    List<JSType> listA = getTypesToTestForSymmetry();
    List<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getLeastSupertype(typeB);
        JSType bOnA = typeB.getLeastSupertype(typeA);

        // TODO(b/110226422): This should use `assertTypeEquals` but at least one of the cases
        // doesn't have matching `hashCode()` values.
        assertTrue(
            String.format(
                lines(
                    "getLeastSupertype not symmetrical:",
                    "typeA: %s",
                    "typeB: %s",
                    "a.getLeastSupertype(b): %s",
                    "b.getLeastSupertype(a): %s"),
                typeA,
                typeB,
                aOnB,
                bOnA),
            aOnB.isEquivalentTo(bOnA));
      }
    }
  }

  @Test
  public void testWeirdBug() {
    assertTypeNotEquals(googBar, googBar.getInstanceType());
    assertFalse(googBar.isSubtype(googBar.getInstanceType()));
    assertFalse(googBar.getInstanceType().isSubtypeOf(googBar));
  }

  /** Tests that getGreatestSubtype is a symmetric relation. */
  @Test
  public void testSymmetryOfGreatestSubtype() {
    List<JSType> listA = getTypesToTestForSymmetry();
    List<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getGreatestSubtype(typeB);
        JSType bOnA = typeB.getGreatestSubtype(typeA);

        // TODO(b/110226422): This should use `assertTypeEquals` but at least one of the cases
        // doesn't have matching `hashCode()` values.
        assertTrue(
            String.format(
                lines(
                    "getGreatestSubtype not symmetrical:",
                    "typeA: %s",
                    "typeB: %s",
                    "a.getGreatestSubtype(b): %s",
                    "b.getGreatestSubtype(a): %s"),
                typeA,
                typeB,
                aOnB,
                bOnA),
            aOnB.isEquivalentTo(bOnA));
      }
    }
  }

  /** Tests that getLeastSupertype is a reflexive relation. */
  @Test
  public void testReflexivityOfLeastSupertype() {
    List<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getLeastSupertype not reflexive",
          type, type.getLeastSupertype(type));
    }
  }

  /** Tests that getGreatestSubtype is a reflexive relation. */
  @Test
  public void testReflexivityOfGreatestSubtype() {
    List<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getGreatestSubtype not reflexive",
          type, type.getGreatestSubtype(type));
    }
  }

  /** Tests {@link JSType#getLeastSupertype(JSType)} for unresolved named types. */
  @Test
  public void testLeastSupertypeUnresolvedNamedType() {
    // (undefined,function(?):?) and ? unresolved named type
    JSType expected = registry.createUnionType(
        unresolvedNamedType, U2U_FUNCTION_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(U2U_FUNCTION_TYPE));
    assertTypeEquals(expected,
        U2U_FUNCTION_TYPE.getLeastSupertype(unresolvedNamedType));
    assertEquals("(function(...?): ?|not.resolved.named.type)",
        expected.toString());
  }

  @Test
  public void testLeastSupertypeUnresolvedNamedType2() {
    JSType expected = registry.createUnionType(
        unresolvedNamedType, UNKNOWN_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(expected,
        UNKNOWN_TYPE.getLeastSupertype(unresolvedNamedType));
    assertTypeEquals(UNKNOWN_TYPE, expected);
  }

  @Test
  public void testLeastSupertypeUnresolvedNamedType3() {
    JSType expected = registry.createUnionType(
        unresolvedNamedType, CHECKED_UNKNOWN_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(expected,
        CHECKED_UNKNOWN_TYPE.getLeastSupertype(unresolvedNamedType));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, expected);
  }

  /** Tests the subclass of an unresolved named type */
  @Test
  public void testSubclassOfUnresolvedNamedType() {
    assertTrue(subclassOfUnresolvedNamedType.isUnknownType());
  }

  /**
   * Tests that Proxied FunctionTypes behave the same over getLeastSupertype and getGreatestSubtype
   * as non proxied FunctionTypes
   */
  @Test
  public void testSupertypeOfProxiedFunctionTypes() {
    ObjectType fn1 =
        new FunctionBuilder(registry)
        .withParamsNode(new Node(Token.PARAM_LIST))
        .withReturnType(NUMBER_TYPE)
        .build();
    ObjectType fn2 =
        new FunctionBuilder(registry)
        .withParamsNode(new Node(Token.PARAM_LIST))
        .withReturnType(STRING_TYPE)
        .build();
    ObjectType p1 = new ProxyObjectType(registry, fn1);
    ObjectType p2 = new ProxyObjectType(registry, fn2);
    ObjectType supremum =
        new FunctionBuilder(registry)
        .withParamsNode(new Node(Token.PARAM_LIST))
        .withReturnType(registry.createUnionType(STRING_TYPE, NUMBER_TYPE))
        .build();

    assertTypeEquals(fn1.getLeastSupertype(fn2), p1.getLeastSupertype(p2));
    assertTypeEquals(supremum, fn1.getLeastSupertype(fn2));
    assertTypeEquals(supremum, fn1.getLeastSupertype(p2));
    assertTypeEquals(supremum, p1.getLeastSupertype(fn2));
    assertTypeEquals(supremum, p1.getLeastSupertype(p2));
  }

  @Test
  public void testTypeOfThisIsProxied() {
    ObjectType fnType = new FunctionBuilder(registry)
        .withReturnType(NUMBER_TYPE).withTypeOfThis(OBJECT_TYPE).build();
    ObjectType proxyType = new ProxyObjectType(registry, fnType);
    assertTypeEquals(fnType.getTypeOfThis(), proxyType.getTypeOfThis());
  }

  /** Tests the {@link NamedType#equals} function, which had a bug in it. */
  @Test
  public void testNamedTypeEquals() {
    JSTypeRegistry jst = new JSTypeRegistry(null);

    // test == if references are equal
    NamedType a = new NamedType(EMPTY_SCOPE, jst, "type1", "source", 1, 0);
    NamedType b = new NamedType(EMPTY_SCOPE, jst, "type1", "source", 1, 0);
    assertTrue(a.isEquivalentTo(b));

    // test == instance of referenced type
    assertTrue(namedGoogBar.isEquivalentTo(googBar.getInstanceType()));
    assertTrue(googBar.getInstanceType().isEquivalentTo(namedGoogBar));
  }

  /** Tests the {@link NamedType#equals} function against other types. */
  @Test
  public void testNamedTypeEquals2() {
    // test == if references are equal
    NamedType a = new NamedType(EMPTY_SCOPE, registry, "typeA", "source", 1, 0);
    NamedType b = new NamedType(EMPTY_SCOPE, registry, "typeB", "source", 1, 0);

    ObjectType realA =
        registry.createConstructorType("typeA", null, null, null, null, false).getInstanceType();
    ObjectType realB = registry.createEnumType(
        "typeB", null, NUMBER_TYPE).getElementsType();
    registry.declareType(null, "typeA", realA);
    registry.declareType(null, "typeB", realB);

    assertTypeEquals(a, realA);
    assertTypeEquals(b, realB);

    a.resolve(null);
    b.resolve(null);

    assertTrue(a.isResolved());
    assertTrue(b.isResolved());
    assertTypeEquals(a, realA);
    assertTypeEquals(b, realB);

    JSType resolvedA = Asserts.assertValidResolve(a);
    assertNotSame(resolvedA, a);
    assertSame(resolvedA, realA);

    JSType resolvedB = Asserts.assertValidResolve(b);
    assertNotSame(resolvedB, b);
    assertSame(resolvedB, realB);
  }

  @Test
  public void testMeaningOfUnresolved() {
    // Given
    JSType underTest = new UnitTestingJSType(registry);

    // When
    // No resolution.

    // Then
    assertFalse(underTest.isResolved());
    assertFalse(underTest.isSuccessfullyResolved());
    assertFalse(underTest.isUnsuccessfullyResolved());
  }

  @Test
  public void testMeaningOfSuccessfullyResolved() {
    // Given
    JSType underTest =
        new UnitTestingJSType(registry) {
          @Override
          public boolean isNoResolvedType() {
            return false;
          }
        };

    // When
    underTest.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertTrue(underTest.isResolved());
    assertTrue(underTest.isSuccessfullyResolved());
    assertFalse(underTest.isUnsuccessfullyResolved());
  }

  @Test
  public void testMeaningOfUnsuccessfullyResolved() {
    // Given
    JSType underTest =
        new UnitTestingJSType(registry) {
          @Override
          public boolean isNoResolvedType() {
            return true;
          }
        };

    // When
    underTest.resolve(ErrorReporter.NULL_INSTANCE);

    // Then
    assertTrue(underTest.isResolved());
    assertFalse(underTest.isSuccessfullyResolved());
    assertTrue(underTest.isUnsuccessfullyResolved());
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on simple types. */
  @Test
  public void testGreatestSubtypeSimpleTypes() {
    assertTypeEquals(ARRAY_TYPE,
        ARRAY_TYPE.getGreatestSubtype(ALL_TYPE));
    assertTypeEquals(ARRAY_TYPE,
        ALL_TYPE.getGreatestSubtype(ARRAY_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        REGEXP_TYPE.getGreatestSubtype(NO_OBJECT_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        NO_OBJECT_TYPE.getGreatestSubtype(REGEXP_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(STRING_OBJECT_TYPE));
    assertTypeEquals(NO_TYPE, ARRAY_TYPE.getGreatestSubtype(NUMBER_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(functionType));
    assertTypeEquals(STRING_OBJECT_TYPE,
        STRING_OBJECT_TYPE.getGreatestSubtype(OBJECT_TYPE));
    assertTypeEquals(STRING_OBJECT_TYPE,
        OBJECT_TYPE.getGreatestSubtype(STRING_OBJECT_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(DATE_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(REGEXP_TYPE));
    assertTypeEquals(NO_TYPE,
        NULL_TYPE.getGreatestSubtype(ARRAY_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        NUMBER_TYPE.getGreatestSubtype(UNKNOWN_TYPE));

    assertTypeEquals(NO_RESOLVED_TYPE,
        NO_OBJECT_TYPE.getGreatestSubtype(forwardDeclaredNamedType));
    assertTypeEquals(NO_RESOLVED_TYPE,
        forwardDeclaredNamedType.getGreatestSubtype(NO_OBJECT_TYPE));

  }

  /** Tests that a derived class extending a type via a named type is a subtype of it. */
  @Test
  public void testSubtypingDerivedExtendsNamedBaseType() {
    ObjectType derived =
        registry.createObjectType(null, registry.createObjectType(null, namedGoogBar));

    assertTrue(derived.isSubtypeOf(googBar.getInstanceType()));
  }

  @Test
  public void testNamedSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        googBar.getPrototype(),
        googBar.getInstanceType(),
        googSubBar.getPrototype(),
        googSubBar.getInstanceType(),
        googSubSubBar.getPrototype(),
        googSubSubBar.getInstanceType(),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordSubtypeChain() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    JSType aType = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    JSType abType = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType acType = builder.build();
    JSType abOrAcType = registry.createUnionType(abType, acType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", NUMBER_TYPE, null);
    JSType abcType = builder.build();

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        aType,
        abOrAcType,
        abType,
        abcType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordAndObjectChain2() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("date", DATE_TYPE, null);
    JSType hasDateProperty = builder.build();

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        hasDateProperty,
        googBar.getInstanceType(),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordAndObjectChain3() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("date", UNKNOWN_TYPE, null);
    JSType hasUnknownDateProperty = builder.build();

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        hasUnknownDateProperty,
        googBar.getInstanceType(),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testNullableNamedTypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.createOptionalNullableType(
            registry.getNativeType(JSTypeNative.ALL_TYPE)),
        registry.createOptionalNullableType(
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE)),
        registry.createOptionalNullableType(
            registry.getNativeType(JSTypeNative.OBJECT_TYPE)),
        registry.createOptionalNullableType(googBar.getPrototype()),
        registry.createOptionalNullableType(googBar.getInstanceType()),
        registry.createNullableType(googSubBar.getPrototype()),
        registry.createNullableType(googSubBar.getInstanceType()),
        googSubSubBar.getPrototype(),
        googSubSubBar.getInstanceType(),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testEnumTypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        enumType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testFunctionSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
        registry.getNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE),
        dateMethod,
        registry.getNativeType(JSTypeNative.LEAST_FUNCTION_TYPE),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testFunctionUnionSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        createUnionType(
            OBJECT_TYPE,
            STRING_TYPE),
        createUnionType(
            GREATEST_FUNCTION_TYPE,
            googBarInst,
            STRING_TYPE),
        createUnionType(
            STRING_TYPE,
            registry.createFunctionType(
                createUnionType(STRING_TYPE, NUMBER_TYPE)),
            googBarInst),
        createUnionType(
            registry.createFunctionType(NUMBER_TYPE),
            googSubBarInst),
        LEAST_FUNCTION_TYPE,
        NO_OBJECT_TYPE,
        NO_TYPE);
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testConstructorSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testGoogBarSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        googBar,
        googSubBar,
        googSubSubBar,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testConstructorWithArgSubtypeChain() throws Exception {
    FunctionType googBarArgConstructor =
        registry.createConstructorType(
            "barArg", null, registry.createParameters(googBar), null, null, false);
    FunctionType googSubBarArgConstructor =
        registry.createConstructorType(
            "subBarArg", null, registry.createParameters(googSubBar), null, null, false);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        googBarArgConstructor,
        googSubBarArgConstructor,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testInterfaceInstanceSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        ALL_TYPE,
        OBJECT_TYPE,
        interfaceInstType,
        googBar.getPrototype(),
        googBarInst,
        googSubBar.getPrototype(),
        googSubBarInst,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testInterfaceInheritanceSubtypeChain() throws Exception {
    FunctionType tempType =
        registry.createConstructorType("goog.TempType", null, null, null, null, false);
    tempType.setImplementedInterfaces(
        Lists.<ObjectType>newArrayList(subInterfaceInstType));
    List<JSType> typeChain = ImmutableList.of(
        ALL_TYPE,
        OBJECT_TYPE,
        interfaceInstType,
        subInterfaceInstType,
        tempType.getPrototype(),
        tempType.getInstanceType(),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testAnonymousObjectChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        ALL_TYPE,
        createNullableType(OBJECT_TYPE),
        OBJECT_TYPE,
        registry.createAnonymousObjectType(null),
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testAnonymousEnumElementChain() throws Exception {
    ObjectType enumElemType = registry.createEnumType(
        "typeB", null,
        registry.createAnonymousObjectType(null)).getElementsType();
    List<JSType> typeChain = ImmutableList.of(
        ALL_TYPE,
        createNullableType(OBJECT_TYPE),
        OBJECT_TYPE,
        enumElemType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testTemplatizedArrayChain() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfStringOrNumber = createTemplatizedType(
        ARRAY_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType arrayOfAllType = createTemplatizedType(
        ARRAY_TYPE, ALL_TYPE);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        arrayOfAllType,
        arrayOfStringOrNumber,
        arrayOfString,
        arrayOfNoType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedArrayChain2() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfNoObjectType = createTemplatizedType(
        ARRAY_TYPE, NO_OBJECT_TYPE);
    JSType arrayOfArray = createTemplatizedType(
        ARRAY_TYPE, ARRAY_TYPE);
    JSType arrayOfObject = createTemplatizedType(
        ARRAY_TYPE, OBJECT_TYPE);
    JSType arrayOfAllType = createTemplatizedType(
        ARRAY_TYPE, ALL_TYPE);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        arrayOfAllType,
        arrayOfObject,
        arrayOfArray,
        arrayOfNoObjectType,
        arrayOfNoType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedObjectChain() throws Exception {
    JSType objectOfNoType = createTemplatizedType(
        OBJECT_TYPE, NO_TYPE);
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType objectOfStringOrNumber = createTemplatizedType(
        OBJECT_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType objectOfAllType = createTemplatizedType(
        OBJECT_TYPE, ALL_TYPE);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        objectOfAllType,
        objectOfStringOrNumber,
        objectOfString,
        objectOfNoType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testMixedTemplatizedTypeChain() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType objectOfStringOrNumber = createTemplatizedType(
        OBJECT_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType objectOfAllType = createTemplatizedType(
        OBJECT_TYPE, ALL_TYPE);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.ALL_TYPE),
        registry.getNativeType(JSTypeNative.OBJECT_TYPE),
        objectOfAllType,
        objectOfStringOrNumber,
        objectOfString,
        arrayOfString,
        arrayOfNoType,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
        registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedTypeSubtypes() {
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfUnknown = createTemplatizedType(
        ARRAY_TYPE, UNKNOWN_TYPE);

    assertFalse(objectOfString.isSubtypeOf(ARRAY_TYPE));
    // TODO(johnlenz): should this be false?
    assertTrue(ARRAY_TYPE.isSubtypeOf(objectOfString));
    assertFalse(objectOfString.isSubtypeOf(ARRAY_TYPE));
    // TODO(johnlenz): should this be false?
    assertTrue(ARRAY_TYPE.isSubtypeOf(objectOfString));

    assertTrue(arrayOfString.isSubtypeOf(ARRAY_TYPE));
    assertTrue(ARRAY_TYPE.isSubtypeOf(arrayOfString));
    assertTrue(arrayOfString.isSubtypeOf(arrayOfUnknown));
    assertTrue(arrayOfUnknown.isSubtypeOf(arrayOfString));

    assertFalse(arrayOfString.isSubtypeOf(arrayOfNumber));
    assertFalse(arrayOfNumber.isSubtypeOf(arrayOfString));

    assertTrue(arrayOfNumber.isSubtypeOf(createUnionType(arrayOfNumber, NULL_VOID)));
    assertFalse(createUnionType(arrayOfNumber, NULL_VOID).isSubtypeOf(arrayOfNumber));
    assertFalse(arrayOfString.isSubtypeOf(createUnionType(arrayOfNumber, NULL_VOID)));
  }

  @Test
  public void testTemplatizedTypeRelations() {
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);

    // Union and least super type cases:
    //
    // 1) alternate:Array<string> and current:Object ==> Object
    // 2) alternate:Array<string> and current:Array ==> Array
    // 3) alternate:Object<string> and current:Array ==> Array|Object<string>
    // 4) alternate:Object and current:Array<string> ==> Object
    // 5) alternate:Array and current:Array<string> ==> Array
    // 6) alternate:Array and current:Object<string> ==> Array|Object<string>
    // 7) alternate:Array<string> and current:Array<number> ==> Array<?>
    // 8) alternate:Array<string> and current:Array<string> ==> Array<string>
    // 9) alternate:Array<string> and
    //    current:Object<string> ==> Object<string>|Array<string>

    assertTypeEquals(
        OBJECT_TYPE,
        JSType.getLeastSupertype(arrayOfString, OBJECT_TYPE));
    assertTypeEquals(
        OBJECT_TYPE,
        JSType.getLeastSupertype(OBJECT_TYPE, arrayOfString));

    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getLeastSupertype(arrayOfString, ARRAY_TYPE));
    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getLeastSupertype(ARRAY_TYPE, arrayOfString));

    assertEquals(
        "(Array|Object<string,?>)",
        JSType.getLeastSupertype(objectOfString, ARRAY_TYPE).toString());
    assertEquals(
        "(Array|Object<string,?>)",
        JSType.getLeastSupertype(ARRAY_TYPE, objectOfString).toString());

    assertType(JSType.getLeastSupertype(arrayOfString, arrayOfNumber))
        .toStringIsEqualTo("Array<?>");
    assertType(JSType.getLeastSupertype(arrayOfNumber, arrayOfString))
        .toStringIsEqualTo("Array<?>");

    assertTypeEquals(
        arrayOfString,
        JSType.getLeastSupertype(arrayOfString, arrayOfString));

    assertEquals(
        "(Array<string>|Object<string,?>)",
        JSType.getLeastSupertype(objectOfString, arrayOfString).toString());
    assertEquals(
        "(Array<string>|Object<string,?>)",
        JSType.getLeastSupertype(arrayOfString, objectOfString).toString());

    assertTypeEquals(
        objectOfString,
        JSType.getGreatestSubtype(OBJECT_TYPE, objectOfString));

    assertTypeEquals(
        objectOfString,
        JSType.getGreatestSubtype(objectOfString, OBJECT_TYPE));

    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getGreatestSubtype(objectOfString, ARRAY_TYPE));

    assertTypeEquals(
        JSType.getGreatestSubtype(objectOfString, arrayOfString),
        NO_OBJECT_TYPE);

    assertTypeEquals(
        JSType.getGreatestSubtype(OBJECT_TYPE, arrayOfString),
        arrayOfString);
  }

  /**
   * Tests that the given chain of types has a total ordering defined
   * by the subtype relationship, with types at the top of the lattice
   * listed first.
   *
   * Also verifies that the infimum of any two types on the chain
   * is the lower type, and the supremum of any two types on the chain
   * is the higher type.
   */
  public void verifySubtypeChain(List<JSType> typeChain) throws Exception {
    verifySubtypeChain(typeChain, true);
  }

  public void verifySubtypeChain(List<JSType> typeChain,
                                 boolean checkSubtyping) throws Exception {
    // Ugh. This wouldn't require so much copy-and-paste if we had a functional
    // programming language.
    for (int i = 0; i < typeChain.size(); i++) {
      for (int j = 0; j < typeChain.size(); j++) {
        JSType typeI = typeChain.get(i);
        JSType typeJ = typeChain.get(j);

        JSType namedTypeI = getNamedWrapper("TypeI", typeI);
        JSType namedTypeJ = getNamedWrapper("TypeJ", typeJ);
        JSType proxyTypeI = new ProxyObjectType(registry, typeI);
        JSType proxyTypeJ = new ProxyObjectType(registry, typeJ);

        if (i == j) {
          assertTrue(typeI + " should equal itself",
              typeI.isEquivalentTo(typeI));
          assertTrue("Named " + typeI + " should equal itself",
              namedTypeI.isEquivalentTo(namedTypeI));
          assertTrue("Proxy " + typeI + " should equal itself",
              proxyTypeI.isEquivalentTo(proxyTypeI));
        } else {
          boolean shouldCheck = true;
          // due to structural interface matching and its updated equivalence
          // checking, a subtype interface could be considered as equivalent
          // to its super type interface (if they are structurally the same)
          // when this happens, the following checks are skipped.
          ObjectType objectI = typeI.toObjectType();
          ObjectType objectJ = typeJ.toObjectType();
          if (objectI != null && objectJ != null) {
            FunctionType constructorI = objectI.getConstructor();
            FunctionType constructorJ = objectJ.getConstructor();
            if (constructorI != null && constructorJ != null
                && constructorI.isStructuralInterface()
                && constructorJ.isStructuralInterface()) {
              if (constructorI.isEquivalentTo(constructorJ)) {
                shouldCheck = false;
              }
            }
          }
          if (shouldCheck) {
            assertFalse(typeI + " should not equal " + typeJ,
                typeI.isEquivalentTo(typeJ));
            assertFalse("Named " + typeI + " should not equal " + typeJ,
                namedTypeI.isEquivalentTo(namedTypeJ));
            assertFalse("Proxy " + typeI + " should not equal " + typeJ,
                proxyTypeI.isEquivalentTo(proxyTypeJ));
          }
        }

        assertTrue(typeJ + " should be castable to " + typeI,
            typeJ.canCastTo(typeI));
        assertTrue(typeJ + " should be castable to Named " + namedTypeI,
            typeJ.canCastTo(namedTypeI));
        assertTrue(typeJ + " should be castable to Proxy " + proxyTypeI,
            typeJ.canCastTo(proxyTypeI));

        assertTrue(
            "Named " + typeJ + " should be castable to " + typeI,
            namedTypeJ.canCastTo(typeI));
        assertTrue(
            "Named " + typeJ + " should be castable to Named " + typeI,
            namedTypeJ.canCastTo(namedTypeI));
        assertTrue(
            "Named " + typeJ + " should be castable to Proxy " + typeI,
            namedTypeJ.canCastTo(proxyTypeI));

        assertTrue(
            "Proxy " + typeJ + " should be castable to " + typeI,
            proxyTypeJ.canCastTo(typeI));
        assertTrue(
            "Proxy " + typeJ + " should be castable to Named " + typeI,
            proxyTypeJ.canCastTo(namedTypeI));
        assertTrue(
            "Proxy " + typeJ + " should be castable to Proxy " + typeI,
            proxyTypeJ.canCastTo(proxyTypeI));

        // due to structural interface matching, a subtype could be considered
        // as the super type of its super type (if they are structurally the same)
        // when this happens, the following checks are skipped.
        if (typeI.isSubtypeOf(typeJ) && typeJ.isSubtypeOf(typeI)) {
          continue;
        }

        if (checkSubtyping) {
          if (i <= j) {
            assertTrue(typeJ + " should be a subtype of " + typeI,
                typeJ.isSubtypeOf(typeI));
            assertTrue(
                "Named " + typeJ + " should be a subtype of Named " + typeI,
                namedTypeJ.isSubtypeOf(namedTypeI));
            assertTrue(
                "Proxy " + typeJ + " should be a subtype of Proxy " + typeI,
                proxyTypeJ.isSubtypeOf(proxyTypeI));
          } else {
            assertFalse(typeJ + " should not be a subtype of " + typeI,
                typeJ.isSubtypeOf(typeI));
            assertFalse(
                "Named " + typeJ + " should not be a subtype of Named " + typeI,
                namedTypeJ.isSubtypeOf(namedTypeI));
            assertFalse(
                "Named " + typeJ + " should not be a subtype of Named " + typeI,
                proxyTypeJ.isSubtypeOf(proxyTypeI));
          }

          JSType expectedSupremum = i < j ? typeI : typeJ;
          JSType expectedInfimum = i > j ? typeI : typeJ;

          assertTypeEquals(
              expectedSupremum + " should be the least supertype of " + typeI +
              " and " + typeJ,
              expectedSupremum, typeI.getLeastSupertype(typeJ));

          // TODO(nicksantos): Should these tests pass?
          //assertTypeEquals(
          //    expectedSupremum + " should be the least supertype of Named " +
          //    typeI + " and Named " + typeJ,
          //    expectedSupremum, namedTypeI.getLeastSupertype(namedTypeJ));
          //assertTypeEquals(
          //    expectedSupremum + " should be the least supertype of Proxy " +
          //    typeI + " and Proxy " + typeJ,
          //    expectedSupremum, proxyTypeI.getLeastSupertype(proxyTypeJ));

          assertTypeEquals(
              expectedInfimum + " should be the greatest subtype of " + typeI +
              " and " + typeJ,
              expectedInfimum, typeI.getGreatestSubtype(typeJ));

          // TODO(nicksantos): Should these tests pass?
          //assertTypeEquals(
          //    expectedInfimum + " should be the greatest subtype of Named " +
          //    typeI + " and Named " + typeJ,
          //    expectedInfimum, namedTypeI.getGreatestSubtype(namedTypeJ));
          //assertTypeEquals(
          //    expectedInfimum + " should be the greatest subtype of Proxy " +
          //    typeI + " and Proxy " + typeJ,
          //    expectedInfimum, proxyTypeI.getGreatestSubtype(proxyTypeJ));
        }
      }
    }
  }

  JSType getNamedWrapper(String name, JSType jstype) {
    // Normally, there is no way to create a Named NoType alias so
    // avoid confusing things by doing it here..
    if (!jstype.isNoType()) {
      NamedType namedWrapper = new NamedType(EMPTY_SCOPE, registry, name, "[testcode]", -1, -1);
      namedWrapper.setReferencedType(jstype);
      return namedWrapper;
    } else {
      return jstype;
    }
  }

  /** Tests the behavior of {@link JSType#getRestrictedTypeGivenToBooleanOutcome(boolean)}. */
  @SuppressWarnings("checked")
  @Test
  public void testRestrictedTypeGivenToBoolean() {
    // simple cases
    assertTypeEquals(BOOLEAN_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(BOOLEAN_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(NO_TYPE,
        NULL_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NULL_TYPE,
        NULL_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(STRING_TYPE,
        STRING_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(STRING_TYPE,
        STRING_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(STRING_OBJECT_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NO_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(NO_TYPE,
        VOID_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(VOID_TYPE,
        VOID_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(NO_OBJECT_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenToBooleanOutcome(false));

    // unions
    UnionType nullableStringValue =
        (UnionType) createNullableType(STRING_TYPE);
    assertTypeEquals(STRING_TYPE,
        nullableStringValue.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(nullableStringValue,
        nullableStringValue.getRestrictedTypeGivenToBooleanOutcome(false));

    UnionType nullableStringObject =
        (UnionType) createNullableType(STRING_OBJECT_TYPE);
    assertTypeEquals(STRING_OBJECT_TYPE,
        nullableStringObject.getRestrictedTypeGivenToBooleanOutcome(true));
    assertTypeEquals(NULL_TYPE,
        nullableStringObject.getRestrictedTypeGivenToBooleanOutcome(false));
  }

  @Test
  public void testRegisterProperty() {
    // Get a subset of our list of standard types to test containing just ObjectTypes and already
    // cast to ObjectType.
    ImmutableList<ObjectType> objectTypes =
        types
            .stream()
            .map(JSType::toMaybeObjectType)
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    assertThat(objectTypes).isNotEmpty();

    for (int i = 0; i < objectTypes.size(); i++) {
      ObjectType type = objectTypes.get(i);
      String propName = "ALF" + i;

      type.defineDeclaredProperty(propName, UNKNOWN_TYPE, null);
      type.defineDeclaredProperty("allHaz", UNKNOWN_TYPE, null);

      // We exclude {a: number, b: string} because, for inline record types,
      // we register their properties on a sentinel object literal in the registry.
      if (!type.equals(this.recordType)) {
        assertTypeEquals(type, registry.getGreatestSubtypeWithProperty(type, propName));
      }

      // We don't define property "GRRR" on any of our ObjectTypes.
      assertTypeEquals(NO_TYPE, registry.getGreatestSubtypeWithProperty(type, "GRRR"));
    }
  }

  @Test
  public void testRegisterPropertyMemoization() {
    ObjectType derived1 = registry.createObjectType("d1", namedGoogBar);
    ObjectType derived2 = registry.createObjectType("d2", namedGoogBar);

    derived1.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);

    assertTypeEquals(derived1,
        registry.getGreatestSubtypeWithProperty(derived1, "propz"));
    assertTypeEquals(NO_OBJECT_TYPE,
        registry.getGreatestSubtypeWithProperty(derived2, "propz"));

    derived2.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);

    assertTypeEquals(derived1,
        registry.getGreatestSubtypeWithProperty(derived1, "propz"));
    assertTypeEquals(derived2,
        registry.getGreatestSubtypeWithProperty(derived2, "propz"));
  }

  /** Tests {@link JSTypeRegistry#getGreatestSubtypeWithProperty(JSType, String)}. */
  @Test
  public void testGreatestSubtypeWithProperty() {
    ObjectType foo = registry.createObjectType("foo", OBJECT_TYPE);
    ObjectType bar = registry.createObjectType("bar", namedGoogBar);

    foo.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);
    bar.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);

    assertTypeEquals(bar,
        registry.getGreatestSubtypeWithProperty(namedGoogBar, "propz"));
  }

  @Test
  public void testGoodSetPrototypeBasedOn() {
    FunctionType fun = registry.createConstructorType("fun", null, null, null, null, false);
    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertTrue(fun.getInstanceType().isUnknownType());
  }

  @Test
  public void testLateSetPrototypeBasedOn() {
    FunctionType fun = registry.createConstructorType("fun", null, null, null, null, false);
    assertFalse(fun.getInstanceType().isUnknownType());

    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertTrue(fun.getInstanceType().isUnknownType());
  }

  @Test
  public void testGetTypeUnderEquality1() {
    for (JSType type : types) {
      testGetTypeUnderEquality(type, type, type, type);
    }
  }

  @Test
  public void testGetTypesUnderEquality2() {
    // objects can be equal to numbers
    testGetTypeUnderEquality(
        NUMBER_TYPE, OBJECT_TYPE,
        NUMBER_TYPE, OBJECT_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality3() {
    // null == undefined
    testGetTypeUnderEquality(
        NULL_TYPE, VOID_TYPE,
        NULL_TYPE, VOID_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderEquality4() {
    // (number,string) and number/string
    UnionType stringNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    testGetTypeUnderEquality(
        stringNumber, STRING_TYPE,
        stringNumber, STRING_TYPE);
    testGetTypeUnderEquality(
        stringNumber, NUMBER_TYPE,
        stringNumber, NUMBER_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality5() {
    // (number,null) and undefined
    JSType nullUndefined = createUnionType(VOID_TYPE, NULL_TYPE);
    testGetTypeUnderEquality(
        nullUndefined, NULL_TYPE,
        nullUndefined, NULL_TYPE);
    testGetTypeUnderEquality(
        nullUndefined, VOID_TYPE,
        nullUndefined, VOID_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality6() {
    // (number,undefined,null) == null
    JSType optNullNumber = createUnionType(VOID_TYPE, NULL_TYPE, NUMBER_TYPE);
    testGetTypeUnderEquality(
        optNullNumber, NULL_TYPE,
        createUnionType(NULL_TYPE, VOID_TYPE), NULL_TYPE);
  }

  private void testGetTypeUnderEquality(
      JSType t1, JSType t2, JSType t1Eq, JSType t2Eq) {
    // creating the pairs
    TypePair p12 = t1.getTypesUnderEquality(t2);
    TypePair p21 = t2.getTypesUnderEquality(t1);

    // t1Eq
    assertTypeEquals(t1Eq, p12.typeA);
    assertTypeEquals(t1Eq, p21.typeB);

    // t2Eq
    assertTypeEquals(t2Eq, p12.typeB);
    assertTypeEquals(t2Eq, p21.typeA);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality1() {
    // objects can be not equal to numbers
    UnionType numberObject =
        (UnionType) createUnionType(NUMBER_TYPE, OBJECT_TYPE);
    testGetTypesUnderInequality(
        numberObject, NUMBER_TYPE,
        numberObject, NUMBER_TYPE);
    testGetTypesUnderInequality(
        numberObject, OBJECT_TYPE,
        numberObject, OBJECT_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality2() {
    // null == undefined
    UnionType nullUndefined =
        (UnionType) createUnionType(VOID_TYPE, NULL_TYPE);
    testGetTypesUnderInequality(
        nullUndefined, NULL_TYPE,
        NO_TYPE, NO_TYPE);
    testGetTypesUnderInequality(
        nullUndefined, VOID_TYPE,
        NO_TYPE, NO_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality3() {
    // (number,string)
    UnionType stringNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    testGetTypesUnderInequality(
        stringNumber, NUMBER_TYPE,
        stringNumber, NUMBER_TYPE);
    testGetTypesUnderInequality(
        stringNumber, STRING_TYPE,
        stringNumber, STRING_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality4() {
    // (number,undefined,null) and null
    UnionType nullableOptionalNumber =
        (UnionType) createUnionType(NULL_TYPE, VOID_TYPE, NUMBER_TYPE);
    testGetTypesUnderInequality(
        nullableOptionalNumber, NULL_TYPE,
        NUMBER_TYPE, NULL_TYPE);
  }

  private void testGetTypesUnderInequality(
      JSType t1, JSType t2, JSType t1Eq, JSType t2Eq) {
    // creating the pairs
    TypePair p12 = t1.getTypesUnderInequality(t2);
    TypePair p21 = t2.getTypesUnderInequality(t1);

    // t1Eq
    assertTypeEquals(t1Eq, p12.typeA);
    assertTypeEquals(t1Eq, p21.typeB);

    // t2Eq
    assertTypeEquals(t2Eq, p12.typeB);
    assertTypeEquals(t2Eq, p21.typeA);
  }

  /** Tests the factory method {@link JSTypeRegistry#createOptionalType(JSType)}. */
  @Test
  public void testCreateOptionalType() {
    // number
    UnionType optNumber = (UnionType) registry.createOptionalType(NUMBER_TYPE);
    assertUnionContains(optNumber, NUMBER_TYPE);
    assertUnionContains(optNumber, VOID_TYPE);

    // union
    UnionType optUnion =
        (UnionType) registry.createOptionalType(
            createUnionType(STRING_OBJECT_TYPE, DATE_TYPE));
    assertUnionContains(optUnion, DATE_TYPE);
    assertUnionContains(optUnion, STRING_OBJECT_TYPE);
    assertUnionContains(optUnion, VOID_TYPE);
  }

  public void assertUnionContains(UnionType union, JSType type) {
    assertTrue(union + " should contain " + type, union.contains(type));
  }

  /** Tests the factory method {@link JSTypeRegistry#createAnonymousObjectType}}. */
  @Test
  public void testCreateAnonymousObjectType() {
    // anonymous
    ObjectType anonymous = registry.createAnonymousObjectType(null);
    assertTypeEquals(OBJECT_TYPE, anonymous.getImplicitPrototype());
    assertType(anonymous).getReferenceNameIsNull();
    assertEquals("{}", anonymous.toString());
  }

  /**
   * Tests the factory method {@link JSTypeRegistry#createAnonymousObjectType}} and adds some
   * properties to it.
   */
  @Test
  public void testCreateAnonymousObjectType2() {
    // anonymous
    ObjectType anonymous = registry.createAnonymousObjectType(null);
    anonymous.defineDeclaredProperty(
        "a", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "b", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "c", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "d", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "e", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "f", NUMBER_TYPE, null);
    assertEquals(
        LINE_JOINER.join(
            "{",
            "  a: number,",
            "  b: number,",
            "  c: number,",
            "  d: number,",
            "  e: number,",
            "  f: number",
            "}"),
        anonymous.toString());
  }

  /** Tests the factory method {@link JSTypeRegistry#createObjectType(String, ObjectType)}}. */
  @Test
  public void testCreateObjectType() {
    // simple
    ObjectType subDate =
        registry.createObjectType(null, DATE_TYPE.getImplicitPrototype());
    assertTypeEquals(DATE_TYPE.getImplicitPrototype(),
        subDate.getImplicitPrototype());
    assertType(subDate).getReferenceNameIsNull();
    assertEquals("{...}", subDate.toString());

    // name, node, prototype
    ObjectType subArray = registry.createObjectType("Foo",
        ARRAY_TYPE.getImplicitPrototype());
    assertTypeEquals(ARRAY_TYPE.getImplicitPrototype(),
        subArray.getImplicitPrototype());
    assertType(subArray).getReferenceNameIsEqualTo("Foo");
  }

  /** Tests {@code (U2U_CONSTRUCTOR,undefined) <: (U2U_CONSTRUCTOR,undefined)}. */
  @SuppressWarnings("checked")
  @Test
  public void testBug903110() {
    UnionType union =
        (UnionType) createUnionType(U2U_CONSTRUCTOR_TYPE, VOID_TYPE);
    assertTrue(VOID_TYPE.isSubtypeOf(union));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(union));
    assertTrue(union.isSubtype(union));
  }

  /**
   * Tests {@code U2U_FUNCTION_TYPE <: U2U_CONSTRUCTOR} and {@code U2U_FUNCTION_TYPE <:
   * (U2U_CONSTRUCTOR,undefined)}.
   */
  @Test
  public void testBug904123() {
    assertTrue(U2U_FUNCTION_TYPE.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_FUNCTION_TYPE.
        isSubtype(createOptionalType(U2U_CONSTRUCTOR_TYPE)));
  }

  /**
   * Assert that a type can assign to itself.
   */
  private void assertTypeCanAssignToItself(JSType type) {
    assertTrue(type.isSubtypeOf(type));
  }

  /**
   * Tests that hasOwnProperty returns true when a property is defined directly on a class and false
   * if the property is defined on the supertype or not at all.
   */
  @Test
  public void testHasOwnProperty() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);

    sup.defineProperty("base", null, false, null);
    sub.defineProperty("sub", null, false, null);

    assertTrue(sup.hasProperty("base"));
    assertFalse(sup.hasProperty("sub"));
    assertTrue(sup.hasOwnProperty("base"));
    assertFalse(sup.hasOwnProperty("sub"));
    assertFalse(sup.hasOwnProperty("none"));

    assertTrue(sub.hasProperty("base"));
    assertTrue(sub.hasProperty("sub"));
    assertFalse(sub.hasOwnProperty("base"));
    assertTrue(sub.hasOwnProperty("sub"));
    assertFalse(sub.hasOwnProperty("none"));
  }

  @Test
  public void testNamedTypeHasOwnProperty() {
    namedGoogBar.getImplicitPrototype().defineProperty("base", null, false,
        null);
    namedGoogBar.defineProperty("sub", null, false, null);

    assertFalse(namedGoogBar.hasOwnProperty("base"));
    assertTrue(namedGoogBar.hasProperty("base"));
    assertTrue(namedGoogBar.hasOwnProperty("sub"));
    assertTrue(namedGoogBar.hasProperty("sub"));
  }

  @Test
  public void testInterfaceHasOwnProperty() {
    interfaceInstType.defineProperty("base", null, false, null);
    subInterfaceInstType.defineProperty("sub", null, false, null);

    assertTrue(interfaceInstType.hasProperty("base"));
    assertFalse(interfaceInstType.hasProperty("sub"));
    assertTrue(interfaceInstType.hasOwnProperty("base"));
    assertFalse(interfaceInstType.hasOwnProperty("sub"));
    assertFalse(interfaceInstType.hasOwnProperty("none"));

    assertTrue(subInterfaceInstType.hasProperty("base"));
    assertTrue(subInterfaceInstType.hasProperty("sub"));
    assertFalse(subInterfaceInstType.hasOwnProperty("base"));
    assertTrue(subInterfaceInstType.hasOwnProperty("sub"));
    assertFalse(subInterfaceInstType.hasOwnProperty("none"));
  }

  @Test
  public void testGetPropertyNames() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);

    sup.defineProperty("base", null, false, null);
    sub.defineProperty("sub", null, false, null);

    assertEquals(ImmutableSet.of("isPrototypeOf", "toLocaleString",
          "propertyIsEnumerable", "toString", "valueOf", "hasOwnProperty",
          "constructor", "base", "sub"), sub.getPropertyNames());
    assertEquals(ImmutableSet.of("isPrototypeOf", "toLocaleString",
          "propertyIsEnumerable", "toString", "valueOf", "hasOwnProperty",
          "constructor", "base"), sup.getPropertyNames());

    assertEquals(new HashSet<>(), NO_OBJECT_TYPE.getPropertyNames());
  }

  @Test
  public void testGetAndSetJSDocInfoWithNamedType() {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordDeprecated();
    JSDocInfo info = builder.build();

    assertNull(namedGoogBar.getOwnPropertyJSDocInfo("X"));
    namedGoogBar.setPropertyJSDocInfo("X", info);
    assertTrue(namedGoogBar.getOwnPropertyJSDocInfo("X").isDeprecated());
    assertPropertyTypeInferred(namedGoogBar, "X");
    assertTypeEquals(UNKNOWN_TYPE, namedGoogBar.getPropertyType("X"));
  }

  @Test
  public void testGetAndSetJSDocInfoWithObjectTypes() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);

    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordDeprecated();
    JSDocInfo deprecated = builder.build();

    builder = new JSDocInfoBuilder(false);
    builder.recordVisibility(Visibility.PRIVATE);
    JSDocInfo privateInfo = builder.build();

    sup.defineProperty("X", NUMBER_TYPE, true, null);
    sup.setPropertyJSDocInfo("X", privateInfo);

    sub.defineProperty("X", NUMBER_TYPE, true, null);
    sub.setPropertyJSDocInfo("X", deprecated);

    assertFalse(sup.getOwnPropertyJSDocInfo("X").isDeprecated());
    assertEquals(Visibility.PRIVATE,
        sup.getOwnPropertyJSDocInfo("X").getVisibility());
    assertTypeEquals(NUMBER_TYPE, sup.getPropertyType("X"));
    assertTrue(sub.getOwnPropertyJSDocInfo("X").isDeprecated());
    assertEquals(Visibility.INHERITED,
        sub.getOwnPropertyJSDocInfo("X").getVisibility());
    assertTypeEquals(NUMBER_TYPE, sub.getPropertyType("X"));
  }

  @Test
  public void testGetAndSetJSDocInfoWithNoType() {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordDeprecated();
    JSDocInfo deprecated = builder.build();

    NO_TYPE.setPropertyJSDocInfo("X", deprecated);
    assertNull(NO_TYPE.getOwnPropertyJSDocInfo("X"));
  }

  @Test
  public void testObjectGetSubTypes() {
    assertThat(OBJECT_FUNCTION_TYPE.getDirectSubTypes()).contains(googBar);
    assertThat(googBar.getDirectSubTypes()).contains(googSubBar);
    assertThat(googBar.getDirectSubTypes()).doesNotContain(googSubSubBar);
    assertThat(googSubBar.getDirectSubTypes()).doesNotContain(googSubBar);
    assertThat(googSubBar.getDirectSubTypes()).contains(googSubSubBar);
  }

  @Test
  public void testImplementingType() {
    assertThat(registry.getDirectImplementors(interfaceType.getInstanceType())).contains(googBar);
  }

  @Test
  public void testIsTemplatedType() {
    assertTrue(
        new TemplateType(registry, "T")
            .hasAnyTemplateTypes());
    assertFalse(ARRAY_TYPE.hasAnyTemplateTypes());

    assertTrue(
        createTemplatizedType(ARRAY_TYPE, new TemplateType(registry, "T"))
            .hasAnyTemplateTypes());
    assertFalse(
        createTemplatizedType(ARRAY_TYPE, STRING_TYPE).hasAnyTemplateTypes());

    assertTrue(
        new FunctionBuilder(registry)
            .withReturnType(new TemplateType(registry, "T"))
            .build()
            .hasAnyTemplateTypes());
    assertTrue(
        new FunctionBuilder(registry)
            .withTypeOfThis(new TemplateType(registry, "T"))
            .build()
            .hasAnyTemplateTypes());
    assertFalse(
        new FunctionBuilder(registry)
            .withReturnType(STRING_TYPE)
            .build()
            .hasAnyTemplateTypes());

    assertTrue(
        registry.createUnionType(
            NULL_TYPE, new TemplateType(registry, "T"), STRING_TYPE)
            .hasAnyTemplateTypes());
    assertFalse(
        registry.createUnionType(
            NULL_TYPE, ARRAY_TYPE, STRING_TYPE)
            .hasAnyTemplateTypes());
  }

  @Test
  public void testTemplatizedType() {
    FunctionType templatizedCtor =
        registry.createConstructorType(
            "TestingType",
            null,
            null,
            UNKNOWN_TYPE,
            ImmutableList.of(registry.createTemplateType("A"), registry.createTemplateType("B")),
            false);
    JSType templatizedInstance = registry.createTemplatizedType(
        templatizedCtor.getInstanceType(),
        ImmutableList.of(NUMBER_TYPE, STRING_TYPE));

    TemplateTypeMap ctrTypeMap = templatizedCtor.getTemplateTypeMap();
    TemplateType keyA = ctrTypeMap.getTemplateTypeKeyByName("A");
    assertNotNull(keyA);
    TemplateType keyB = ctrTypeMap.getTemplateTypeKeyByName("B");
    assertNotNull(keyB);
    TemplateType keyC = ctrTypeMap.getTemplateTypeKeyByName("C");
    assertNull(keyC);
    TemplateType unknownKey = registry.createTemplateType("C");

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();
    assertTrue(templateTypeMap.hasTemplateKey(keyA));
    assertTrue(templateTypeMap.hasTemplateKey(keyB));
    assertFalse(templateTypeMap.hasTemplateKey(unknownKey));

    assertEquals(NUMBER_TYPE, templateTypeMap.getResolvedTemplateType(keyA));
    assertEquals(STRING_TYPE, templateTypeMap.getResolvedTemplateType(keyB));
    assertEquals(UNKNOWN_TYPE, templateTypeMap.getResolvedTemplateType(unknownKey));

    assertEquals("TestingType<number,string>", templatizedInstance.toString());
  }

  @Test
  public void testPartiallyTemplatizedType() {
    FunctionType templatizedCtor =
        registry.createConstructorType(
            "TestingType",
            null,
            null,
            UNKNOWN_TYPE,
            ImmutableList.of(registry.createTemplateType("A"), registry.createTemplateType("B")),
            false);
    JSType templatizedInstance = registry.createTemplatizedType(
        templatizedCtor.getInstanceType(),
        ImmutableList.of(NUMBER_TYPE));

    TemplateTypeMap ctrTypeMap = templatizedCtor.getTemplateTypeMap();
    TemplateType keyA = ctrTypeMap.getTemplateTypeKeyByName("A");
    assertNotNull(keyA);
    TemplateType keyB = ctrTypeMap.getTemplateTypeKeyByName("B");
    assertNotNull(keyB);
    TemplateType keyC = ctrTypeMap.getTemplateTypeKeyByName("C");
    assertNull(keyC);
    TemplateType unknownKey = registry.createTemplateType("C");

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();
    assertTrue(templateTypeMap.hasTemplateKey(keyA));
    assertTrue(templateTypeMap.hasTemplateKey(keyB));
    assertFalse(templateTypeMap.hasTemplateKey(unknownKey));

    assertEquals(NUMBER_TYPE, templateTypeMap.getResolvedTemplateType(keyA));
    assertEquals(UNKNOWN_TYPE, templateTypeMap.getResolvedTemplateType(keyB));
    assertEquals(UNKNOWN_TYPE, templateTypeMap.getResolvedTemplateType(unknownKey));

    assertEquals("TestingType<number,?>", templatizedInstance.toString());
  }

  @Test
  public void testTemplateTypeValidator() {
    // The template type setValidator() will see the TemplateType, not the referenced unknown type
    // like other ProxyObjectTypes do.
    TemplateType t = new TemplateType(registry, "T");

    assertThat(t.setValidator(type -> type.isTemplateType())).isTrue();
    assertThat(t.setValidator(type -> !type.isTemplateType())).isFalse();
  }

  @Test
  public void testTemplateTypeHasReferenceName() {
    TemplateType t = new TemplateType(registry, "T");

    assertType(t).getReferenceNameIsEqualTo("T");
  }

  @Test
  public void testCanCastTo() {
    assertTrue(ALL_TYPE.canCastTo(NULL_TYPE));
    assertTrue(ALL_TYPE.canCastTo(VOID_TYPE));
    assertTrue(ALL_TYPE.canCastTo(STRING_TYPE));
    assertTrue(ALL_TYPE.canCastTo(NUMBER_TYPE));
    assertTrue(ALL_TYPE.canCastTo(BOOLEAN_TYPE));
    assertTrue(ALL_TYPE.canCastTo(OBJECT_TYPE));

    assertFalse(NUMBER_TYPE.canCastTo(NULL_TYPE));
    assertFalse(NUMBER_TYPE.canCastTo(VOID_TYPE));
    assertFalse(NUMBER_TYPE.canCastTo(STRING_TYPE));
    assertTrue(NUMBER_TYPE.canCastTo(NUMBER_TYPE));
    assertFalse(NUMBER_TYPE.canCastTo(BOOLEAN_TYPE));
    assertFalse(NUMBER_TYPE.canCastTo(OBJECT_TYPE));

    assertFalse(STRING_TYPE.canCastTo(NULL_TYPE));
    assertFalse(STRING_TYPE.canCastTo(VOID_TYPE));
    assertTrue(STRING_TYPE.canCastTo(STRING_TYPE));
    assertFalse(STRING_TYPE.canCastTo(NUMBER_TYPE));
    assertFalse(STRING_TYPE.canCastTo(BOOLEAN_TYPE));
    assertFalse(STRING_TYPE.canCastTo(OBJECT_TYPE));

    assertFalse(BOOLEAN_TYPE.canCastTo(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.canCastTo(VOID_TYPE));
    assertFalse(BOOLEAN_TYPE.canCastTo(STRING_TYPE));
    assertFalse(BOOLEAN_TYPE.canCastTo(NUMBER_TYPE));
    assertTrue(BOOLEAN_TYPE.canCastTo(BOOLEAN_TYPE));
    assertFalse(BOOLEAN_TYPE.canCastTo(OBJECT_TYPE));

    assertFalse(OBJECT_TYPE.canCastTo(NULL_TYPE));
    assertFalse(OBJECT_TYPE.canCastTo(VOID_TYPE));
    assertFalse(OBJECT_TYPE.canCastTo(STRING_TYPE));
    assertFalse(OBJECT_TYPE.canCastTo(NUMBER_TYPE));
    assertFalse(OBJECT_TYPE.canCastTo(BOOLEAN_TYPE));
    assertTrue(OBJECT_TYPE.canCastTo(OBJECT_TYPE));

    assertFalse(BOOLEAN_TYPE.canCastTo(OBJECT_NUMBER_STRING));
    assertFalse(OBJECT_NUMBER_STRING.canCastTo(BOOLEAN_TYPE));

    assertFalse(ARRAY_TYPE.canCastTo(U2U_FUNCTION_TYPE));
    assertFalse(U2U_FUNCTION_TYPE.canCastTo(ARRAY_TYPE));

    assertFalse(NULL_VOID.canCastTo(ARRAY_TYPE));
    assertTrue(NULL_VOID.canCastTo(createUnionType(ARRAY_TYPE, NULL_TYPE)));

    // We currently allow any function to be cast to any other function type
    assertTrue(ARRAY_FUNCTION_TYPE.canCastTo(BOOLEAN_OBJECT_FUNCTION_TYPE));
  }

  @Test
  public void testRecordTypeEquality() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    JSType secondType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));

    assertType(firstType).isNotSameAs(secondType);
    assertType(firstType).isEqualTo(secondType);
    assertType(firstType).isStructurallyEqualTo(secondType);
  }

  @Test
  public void testRecordAndInterfaceObjectTypeNotEqualWithSameProperties() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    // /** @interface */
    // function Foo() {}
    // /** @type {number} */
    // Foo.prototype.x;
    FunctionType secondTypeConstructor =
        registry.createInterfaceType("Foo", null, ImmutableList.of(), false);

    secondTypeConstructor.getPrototype().defineProperty("x", NUMBER_TYPE, false, null);
    secondTypeConstructor.setImplicitMatch(true);
    JSType secondType = secondTypeConstructor.getInstanceType();

    // These are not equal but are structurally equivalent
    assertType(firstType).isNotEqualTo(secondType);
    assertType(firstType).isStructurallyEqualTo(secondType);
  }

  @Test
  public void testRecordAndObjectLiteralWithSameProperties() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    assertType(firstType).toStringIsEqualTo("{x: number}");
    // the type inferred for `const obj = {x: 1};`
    ObjectType secondType = registry.createAnonymousObjectType(null);
    secondType.defineDeclaredProperty("x", NUMBER_TYPE, null);
    assertType(secondType).toStringIsEqualTo("{x: number}");

    // These are neither equal nor structurally equivalent because the second type is not a
    // structural type.
    assertType(firstType).isNotEqualTo(secondType);
    assertType(firstType).isNotStructurallyEqualTo(secondType);

    // The second type is a subtype of the first type but not vice versa because only the first type
    // is structural.
    assertType(firstType).isNotSubtypeOf(secondType);
    assertType(secondType).isSubtypeOf(firstType);
  }

  /**
   * A minimal implementation of {@link JSType} for unit tests and nothing else.
   *
   * <p>This class has no innate behaviour. It is intended as a stand-in for testing behaviours on
   * {@link JSType} that require a concrete instance. Test cases are responsible for any
   * configuration.
   */
  private static class UnitTestingJSType extends JSType {

    UnitTestingJSType(JSTypeRegistry registry) {
      super(registry);
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
    StringBuilder appendTo(StringBuilder builder, boolean forAnnotation) {
      throw new UnsupportedOperationException();
    }
  }
}
