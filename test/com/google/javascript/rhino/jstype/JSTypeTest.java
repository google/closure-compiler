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

import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.TRUE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType.TypePair;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;
import com.google.javascript.rhino.testing.AbstractStaticScope;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

// TODO(nicksantos): Split some of this up into per-class unit tests.
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

  private static final StaticTypedScope<JSType> EMPTY_SCOPE =
      MapBasedScope.emptyScope();

  /**
   * A non exhaustive list of representative types used to test simple
   * properties that should hold for all types (such as the reflexivity
   * of subtyping).
   */
  private List<JSType> types;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

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
    unresolvedNamedType =
        new NamedType(registry, "not.resolved.named.type", null, -1, -1);
    namedGoogBar = new NamedType(registry, "goog.Bar", null, -1, -1);

    subclassCtor =
        new FunctionType(registry, null, null, createArrowType(null),
            null, null, true, false);
    subclassCtor.setPrototypeBasedOn(unresolvedNamedType);
    subclassOfUnresolvedNamedType = subclassCtor.getInstanceType();

    interfaceType = FunctionType.forInterface(registry, "Interface", null,
        registry.createTemplateTypeMap(null, null));
    interfaceInstType = interfaceType.getInstanceType();

    subInterfaceType = FunctionType.forInterface(
        registry, "SubInterface", null,
        registry.createTemplateTypeMap(null, null));
    subInterfaceType.setExtendedInterfaces(
        Lists.<ObjectType>newArrayList(interfaceInstType));
    subInterfaceInstType = subInterfaceType.getInstanceType();

    googBar = registry.createConstructorType(
        "goog.Bar", null, null, null, null);
    googBar.getPrototype().defineDeclaredProperty("date", DATE_TYPE,
        null);
    googBar.setImplementedInterfaces(
        Lists.<ObjectType>newArrayList(interfaceInstType));
    googBarInst = googBar.getInstanceType();

    googSubBar = registry.createConstructorType(
        "googSubBar", null, null, null, null);
    googSubBar.setPrototypeBasedOn(googBar.getInstanceType());
    googSubBarInst = googSubBar.getInstanceType();

    googSubSubBar = registry.createConstructorType(
        "googSubSubBar", null, null, null, null);
    googSubSubBar.setPrototypeBasedOn(googSubBar.getInstanceType());
    googSubSubBarInst = googSubSubBar.getInstanceType();

    final ObjectType googObject = registry.createAnonymousObjectType(null);
    googObject.defineDeclaredProperty("Bar", googBar, null);

    namedGoogBar.resolve(null, new AbstractStaticScope<JSType>() {
          @Override
          public StaticTypedSlot<JSType> getSlot(String name) {
            if ("goog".equals(name)) {
              return new SimpleSlot("goog", googObject, false);
            } else {
              return null;
            }
          }
        });
    assertNotNull(namedGoogBar.getImplicitPrototype());

    forwardDeclaredNamedType =
        new NamedType(registry, "forwardDeclared", "source", 1, 0);
    registry.forwardDeclareType("forwardDeclared");
    forwardDeclaredNamedType.resolve(
        new SimpleErrorReporter(), EMPTY_SCOPE);

    types = ImmutableList.of(
        NO_OBJECT_TYPE,
        NO_RESOLVED_TYPE,
        NO_TYPE,
        BOOLEAN_OBJECT_TYPE,
        BOOLEAN_TYPE,
        STRING_OBJECT_TYPE,
        STRING_TYPE,
        VOID_TYPE,
        UNKNOWN_TYPE,
        NULL_TYPE,
        NUMBER_OBJECT_TYPE,
        NUMBER_TYPE,
        DATE_TYPE,
        ERROR_TYPE,
        SYNTAX_ERROR_TYPE,
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

  /**
   * Tests the behavior of the top constructor type.
   */
  public void testUniversalConstructorType() throws Exception {
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
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(functionType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(recordType));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NULL_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(STRING_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.isSubtype(TYPE_ERROR_TYPE));
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
        canTestForEqualityWith(ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(EVAL_ERROR_TYPE));
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
        canTestForEqualityWith(URI_ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(STRING_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.
        canTestForEqualityWith(TYPE_ERROR_TYPE));
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
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
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
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(U2U_CONSTRUCTOR_TYPE.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesInt32Context());
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesNumberContext());
    assertTrue(U2U_CONSTRUCTOR_TYPE.matchesObjectContext());
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesStringContext());
    assertFalse(U2U_CONSTRUCTOR_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the Bottom Object type.
   */
  public void testNoObjectType() throws Exception {
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
    assertFalse(NO_OBJECT_TYPE.isEnumType());
    assertFalse(NO_OBJECT_TYPE.isUnionType());
    assertFalse(NO_OBJECT_TYPE.isStruct());
    assertFalse(NO_OBJECT_TYPE.isDict());
    assertFalse(NO_OBJECT_TYPE.isAllType());
    assertFalse(NO_OBJECT_TYPE.isVoidType());
    assertTrue(NO_OBJECT_TYPE.isConstructor());
    assertFalse(NO_OBJECT_TYPE.isInstanceType());

    // isSubtype
    assertFalse(NO_OBJECT_TYPE.isSubtype(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(functionType));
    assertTrue(NO_OBJECT_TYPE.isSubtype(recordType));
    assertFalse(NO_OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(URI_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(VOID_TYPE));

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, EVAL_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, recordType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, URI_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, RANGE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, REFERENCE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, SYNTAX_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, TYPE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(NO_OBJECT_TYPE.isNullable());
    assertFalse(NO_OBJECT_TYPE.isVoidable());

    // isObject
    assertTrue(NO_OBJECT_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_OBJECT_TYPE.matchesInt32Context());
    assertTrue(NO_OBJECT_TYPE.matchesNumberContext());
    assertTrue(NO_OBJECT_TYPE.matchesObjectContext());
    assertTrue(NO_OBJECT_TYPE.matchesStringContext());
    assertTrue(NO_OBJECT_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the Bottom type.
   */
  public void testNoType() throws Exception {
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
    assertFalse(NO_TYPE.isEnumType());
    assertFalse(NO_TYPE.isUnionType());
    assertFalse(NO_TYPE.isStruct());
    assertFalse(NO_TYPE.isDict());
    assertFalse(NO_TYPE.isAllType());
    assertFalse(NO_TYPE.isVoidType());
    assertTrue(NO_TYPE.isConstructor());
    assertFalse(NO_TYPE.isInstanceType());

    // isSubtype
    assertTrue(NO_TYPE.isSubtype(NO_TYPE));
    assertTrue(NO_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(NO_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NO_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(functionType));
    assertTrue(NO_TYPE.isSubtype(NULL_TYPE));
    assertTrue(NO_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(NO_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(URI_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(REGEXP_TYPE));
    assertTrue(NO_TYPE.isSubtype(STRING_TYPE));
    assertTrue(NO_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(ALL_TYPE));
    assertTrue(NO_TYPE.isSubtype(VOID_TYPE));

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, EVAL_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, functionType);
    assertCanTestForEqualityWith(NO_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, URI_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, RANGE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, REFERENCE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, SYNTAX_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, TYPE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(NO_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertTrue(NO_TYPE.isNullable());
    assertTrue(NO_TYPE.isVoidable());

    // isObject
    assertTrue(NO_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_TYPE.matchesInt32Context());
    assertTrue(NO_TYPE.matchesNumberContext());
    assertTrue(NO_TYPE.matchesObjectContext());
    assertTrue(NO_TYPE.matchesStringContext());
    assertTrue(NO_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the unresolved Bottom type.
   */
  public void testNoResolvedType() throws Exception {
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
    assertFalse(NO_RESOLVED_TYPE.isEnumType());
    assertFalse(NO_RESOLVED_TYPE.isUnionType());
    assertFalse(NO_RESOLVED_TYPE.isStruct());
    assertFalse(NO_RESOLVED_TYPE.isDict());
    assertFalse(NO_RESOLVED_TYPE.isAllType());
    assertFalse(NO_RESOLVED_TYPE.isVoidType());
    assertFalse(NO_RESOLVED_TYPE.isConstructor());
    assertFalse(NO_RESOLVED_TYPE.isInstanceType());

    // isSubtype
    assertTrue(NO_RESOLVED_TYPE.isSubtype(NO_RESOLVED_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(functionType));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(NULL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(URI_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(REGEXP_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(STRING_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(ALL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.isSubtype(VOID_TYPE));

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_RESOLVED_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, EVAL_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, functionType);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, URI_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, RANGE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, REFERENCE_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, SYNTAX_ERROR_TYPE);
    assertCanTestForEqualityWith(NO_RESOLVED_TYPE, TYPE_ERROR_TYPE);
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
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(
        NO_RESOLVED_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(NO_RESOLVED_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertTrue(NO_RESOLVED_TYPE.isNullable());
    assertTrue(NO_RESOLVED_TYPE.isVoidable());

    // isObject
    assertTrue(NO_RESOLVED_TYPE.isObject());

    // matchesXxx
    assertTrue(NO_RESOLVED_TYPE.matchesInt32Context());
    assertTrue(NO_RESOLVED_TYPE.matchesNumberContext());
    assertTrue(NO_RESOLVED_TYPE.matchesObjectContext());
    assertTrue(NO_RESOLVED_TYPE.matchesStringContext());
    assertTrue(NO_RESOLVED_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the Array type.
   */
  public void testArrayType() throws Exception {
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
    assertFalse(ARRAY_TYPE.isEnumType());
    assertFalse(ARRAY_TYPE.isUnionType());
    assertFalse(ARRAY_TYPE.isStruct());
    assertFalse(ARRAY_TYPE.isDict());
    assertFalse(ARRAY_TYPE.isAllType());
    assertFalse(ARRAY_TYPE.isVoidType());
    assertFalse(ARRAY_TYPE.isConstructor());
    assertTrue(ARRAY_TYPE.isInstanceType());

    // isSubtype
    assertFalse(ARRAY_TYPE.isSubtype(NO_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(ALL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(functionType));
    assertFalse(ARRAY_TYPE.isSubtype(recordType));
    assertFalse(ARRAY_TYPE.isSubtype(NULL_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(DATE_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(ARRAY_TYPE.isSubtype(namedGoogBar));
    assertFalse(ARRAY_TYPE.isSubtype(REGEXP_TYPE));

    // canBeCalled
    assertFalse(ARRAY_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, STRING_OBJECT_TYPE);
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
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertFalse(ARRAY_TYPE.matchesInt32Context());
    assertFalse(ARRAY_TYPE.matchesNumberContext());
    assertTrue(ARRAY_TYPE.matchesObjectContext());
    assertTrue(ARRAY_TYPE.matchesStringContext());
    assertFalse(ARRAY_TYPE.matchesUint32Context());

    // toString
    assertEquals("Array", ARRAY_TYPE.toString());
    assertTrue(ARRAY_TYPE.hasDisplayName());
    assertEquals("Array", ARRAY_TYPE.getDisplayName());

    assertTrue(ARRAY_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(ARRAY_TYPE);

    assertFalse(ARRAY_TYPE.isNominalConstructor());
    assertTrue(ARRAY_TYPE.getConstructor().isNominalConstructor());
  }

  /**
   * Tests the behavior of the unknown type.
   */
  public void testUnknownType() throws Exception {
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
    assertTrue(UNKNOWN_TYPE.isSubtype(UNKNOWN_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(STRING_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(functionType));
    assertTrue(UNKNOWN_TYPE.isSubtype(recordType));
    assertTrue(UNKNOWN_TYPE.isSubtype(NULL_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(DATE_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(namedGoogBar));
    assertTrue(UNKNOWN_TYPE.isSubtype(unresolvedNamedType));
    assertTrue(UNKNOWN_TYPE.isSubtype(REGEXP_TYPE));
    assertTrue(UNKNOWN_TYPE.isSubtype(VOID_TYPE));

    // canBeCalled
    assertTrue(UNKNOWN_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, STRING_TYPE);
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
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertTrue(UNKNOWN_TYPE.matchesInt32Context());
    assertTrue(UNKNOWN_TYPE.matchesNumberContext());
    assertTrue(UNKNOWN_TYPE.matchesObjectContext());
    assertTrue(UNKNOWN_TYPE.matchesStringContext());
    assertTrue(UNKNOWN_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the checked unknown type.
   */
  public void testCheckedUnknownType() throws Exception {
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

  /**
   * Tests the behavior of the unknown type.
   */
  public void testAllType() throws Exception {
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
    assertFalse(ALL_TYPE.isEnumType());
    assertFalse(ALL_TYPE.isUnionType());
    assertFalse(ALL_TYPE.isStruct());
    assertFalse(ALL_TYPE.isDict());
    assertTrue(ALL_TYPE.isAllType());
    assertFalse(ALL_TYPE.isVoidType());
    assertFalse(ALL_TYPE.isConstructor());
    assertFalse(ALL_TYPE.isInstanceType());

    // isSubtype
    assertFalse(ALL_TYPE.isSubtype(NO_TYPE));
    assertFalse(ALL_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(ALL_TYPE.isSubtype(ALL_TYPE));
    assertFalse(ALL_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(ALL_TYPE.isSubtype(functionType));
    assertFalse(ALL_TYPE.isSubtype(recordType));
    assertFalse(ALL_TYPE.isSubtype(NULL_TYPE));
    assertFalse(ALL_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(DATE_TYPE));
    assertTrue(ALL_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(ALL_TYPE.isSubtype(namedGoogBar));
    assertFalse(ALL_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(ALL_TYPE.isSubtype(VOID_TYPE));
    assertTrue(ALL_TYPE.isSubtype(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(ALL_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ALL_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, STRING_OBJECT_TYPE);
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
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(recordType));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(ALL_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(ALL_TYPE.isNullable());
    assertFalse(ALL_TYPE.isVoidable());

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertFalse(ALL_TYPE.matchesInt32Context());
    assertFalse(ALL_TYPE.matchesNumberContext());
    assertTrue(ALL_TYPE.matchesObjectContext());
    assertTrue(ALL_TYPE.matchesStringContext());
    assertFalse(ALL_TYPE.matchesUint32Context());

    // toString
    assertEquals("*", ALL_TYPE.toString());

    assertTrue(ALL_TYPE.hasDisplayName());
    assertEquals("<Any Type>", ALL_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(ALL_TYPE);
    assertFalse(ALL_TYPE.isNominalConstructor());
  }

  /**
   * Tests the behavior of the Object type (the object
   * at the top of the JavaScript hierarchy).
   */
  public void testTheObjectType() throws Exception {
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
    assertFalse(OBJECT_TYPE.isEnumType());
    assertFalse(OBJECT_TYPE.isUnionType());
    assertFalse(OBJECT_TYPE.isStruct());
    assertFalse(OBJECT_TYPE.isDict());
    assertFalse(OBJECT_TYPE.isAllType());
    assertFalse(OBJECT_TYPE.isVoidType());
    assertFalse(OBJECT_TYPE.isConstructor());
    assertTrue(OBJECT_TYPE.isInstanceType());

    // isSubtype
    assertFalse(OBJECT_TYPE.isSubtype(NO_TYPE));
    assertTrue(OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(functionType));
    assertFalse(OBJECT_TYPE.isSubtype(recordType));
    assertFalse(OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertTrue(OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(namedGoogBar));
    assertTrue(OBJECT_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(OBJECT_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(OBJECT_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(OBJECT_TYPE.isSubtype(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, STRING_OBJECT_TYPE);
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
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(recordType));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertTrue(OBJECT_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertTrue(OBJECT_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertFalse(OBJECT_TYPE.matchesInt32Context());
    assertFalse(OBJECT_TYPE.matchesNumberContext());
    assertTrue(OBJECT_TYPE.matchesObjectContext());
    assertTrue(OBJECT_TYPE.matchesStringContext());
    assertFalse(OBJECT_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the number value type.
   */
  public void testNumberObjectType() throws Exception {
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
    assertTrue(NUMBER_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(functionType));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(namedGoogBar));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtype(
            createUnionType(NUMBER_OBJECT_TYPE, NULL_TYPE)));
    assertFalse(NUMBER_OBJECT_TYPE.isSubtype(
            createUnionType(NUMBER_TYPE, NULL_TYPE)));
    assertTrue(NUMBER_OBJECT_TYPE.isSubtype(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(NUMBER_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, STRING_OBJECT_TYPE);
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
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(NUMBER_OBJECT_TYPE.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertTrue(NUMBER_OBJECT_TYPE.matchesInt32Context());
    assertTrue(NUMBER_OBJECT_TYPE.matchesNumberContext());
    assertTrue(NUMBER_OBJECT_TYPE.matchesObjectContext());
    assertTrue(NUMBER_OBJECT_TYPE.matchesStringContext());
    assertTrue(NUMBER_OBJECT_TYPE.matchesUint32Context());

    // toString
    assertEquals("Number", NUMBER_OBJECT_TYPE.toString());
    assertTrue(NUMBER_OBJECT_TYPE.hasDisplayName());
    assertEquals("Number", NUMBER_OBJECT_TYPE.getDisplayName());

    assertTrue(NUMBER_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(NUMBER_OBJECT_TYPE);
  }

  /**
   * Tests the behavior of the number value type.
   */
  public void testNumberValueType() throws Exception {
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
    assertTrue(NUMBER_TYPE.isSubtype(ALL_TYPE));
    assertFalse(NUMBER_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NUMBER_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(NUMBER_TYPE.isSubtype(functionType));
    assertFalse(NUMBER_TYPE.isSubtype(NULL_TYPE));
    assertFalse(NUMBER_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NUMBER_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(NUMBER_TYPE.isSubtype(namedGoogBar));
    assertTrue(NUMBER_TYPE.isSubtype(
            createUnionType(NUMBER_TYPE, NULL_TYPE)));
    assertTrue(NUMBER_TYPE.isSubtype(UNKNOWN_TYPE));

    // canBeCalled
    assertFalse(NUMBER_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, STRING_OBJECT_TYPE);
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
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(NUMBER_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(NUMBER_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertTrue(NUMBER_TYPE.matchesInt32Context());
    assertTrue(NUMBER_TYPE.matchesNumberContext());
    assertTrue(NUMBER_TYPE.matchesObjectContext());
    assertTrue(NUMBER_TYPE.matchesStringContext());
    assertTrue(NUMBER_TYPE.matchesUint32Context());

    // toString
    assertEquals("number", NUMBER_TYPE.toString());
    assertTrue(NUMBER_TYPE.hasDisplayName());
    assertEquals("number", NUMBER_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(NUMBER_TYPE);
    assertFalse(NUMBER_TYPE.isNominalConstructor());
  }

  /**
   * Tests the behavior of the null type.
   */
  public void testNullType() throws Exception {

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
    assertFalse(NULL_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtype(NO_TYPE));
    assertTrue(NULL_TYPE.isSubtype(NULL_TYPE));
    assertTrue(NULL_TYPE.isSubtype(ALL_TYPE));
    assertFalse(NULL_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(NULL_TYPE.isSubtype(functionType));
    assertFalse(NULL_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(NULL_TYPE.isSubtype(DATE_TYPE));
    assertFalse(NULL_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(NULL_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(NULL_TYPE.isSubtype(UNKNOWN_TYPE));

    assertTrue(NULL_TYPE.isSubtype(createNullableType(NO_OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(NO_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(NULL_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(ALL_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(STRING_OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(NUMBER_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(functionType)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(OBJECT_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(DATE_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(REGEXP_TYPE)));
    assertTrue(NULL_TYPE.isSubtype(createNullableType(ARRAY_TYPE)));

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
    assertCannotTestForEqualityWith(NULL_TYPE, ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, EVAL_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, functionType);
    assertCannotTestForEqualityWith(NULL_TYPE, NULL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, URI_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, RANGE_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, REFERENCE_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, REGEXP_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, SYNTAX_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, TYPE_ERROR_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(NO_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(NULL_TYPE.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(functionType));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(NULL_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(NULL_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(NULL_TYPE.canTestForShallowEqualityWith(
            createNullableType(STRING_OBJECT_TYPE)));

    // getLeastSupertype
    assertTypeEquals(NULL_TYPE, NULL_TYPE.getLeastSupertype(NULL_TYPE));
    assertTypeEquals(ALL_TYPE, NULL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createNullableType(STRING_OBJECT_TYPE),
        NULL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
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
    assertTrue(NULL_TYPE.matchesInt32Context());
    assertTrue(NULL_TYPE.matchesNumberContext());
    assertFalse(NULL_TYPE.matchesObjectContext());
    assertTrue(NULL_TYPE.matchesStringContext());
    assertTrue(NULL_TYPE.matchesUint32Context());

    // matchesObjectContext
    assertFalse(NULL_TYPE.matchesObjectContext());

    // toString
    assertEquals("null", NULL_TYPE.toString());
    assertTrue(NULL_TYPE.hasDisplayName());
    assertEquals("null", NULL_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(NULL_TYPE);

    // getGreatestSubtype
    assertTrue(
        NULL_TYPE.isSubtype(
            createUnionType(forwardDeclaredNamedType, NULL_TYPE)));
    assertTypeEquals(
        createUnionType(forwardDeclaredNamedType, NULL_TYPE),
        NULL_TYPE.getGreatestSubtype(
            createUnionType(forwardDeclaredNamedType, NULL_TYPE)));
    assertFalse(NULL_TYPE.isNominalConstructor());

    assertTrue(NULL_TYPE.differsFrom(UNKNOWN_TYPE));
  }

  /**
   * Tests the behavior of the Date type.
   */
  public void testDateType() throws Exception {
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
    assertFalse(DATE_TYPE.isSubtype(NO_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(DATE_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(DATE_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtype(DATE_TYPE));
    assertFalse(DATE_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(functionType));
    assertFalse(DATE_TYPE.isSubtype(NULL_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(DATE_TYPE.isSubtype(STRING_TYPE));
    assertFalse(DATE_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(DATE_TYPE.isSubtype(ALL_TYPE));
    assertFalse(DATE_TYPE.isSubtype(VOID_TYPE));

    // canBeCalled
    assertFalse(DATE_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(DATE_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, NUMBER_TYPE);
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
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(DATE_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(DATE_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertTrue(DATE_TYPE.matchesInt32Context());
    assertTrue(DATE_TYPE.matchesNumberContext());
    assertTrue(DATE_TYPE.matchesObjectContext());
    assertTrue(DATE_TYPE.matchesStringContext());
    assertTrue(DATE_TYPE.matchesUint32Context());

    // toString
    assertEquals("Date", DATE_TYPE.toString());
    assertTrue(DATE_TYPE.hasDisplayName());
    assertEquals("Date", DATE_TYPE.getDisplayName());

    assertTrue(DATE_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(DATE_TYPE);
    assertFalse(DATE_TYPE.isNominalConstructor());
    assertTrue(DATE_TYPE.getConstructor().isNominalConstructor());
  }

  /**
   * Tests the behavior of the RegExp type.
   */
  public void testRegExpType() throws Exception {
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
    assertFalse(REGEXP_TYPE.isEnumType());
    assertFalse(REGEXP_TYPE.isUnionType());
    assertFalse(REGEXP_TYPE.isStruct());
    assertFalse(REGEXP_TYPE.isDict());
    assertFalse(REGEXP_TYPE.isAllType());
    assertFalse(REGEXP_TYPE.isVoidType());

    // autoboxesTo
    assertNull(REGEXP_TYPE.autoboxesTo());

    // isSubtype
    assertFalse(REGEXP_TYPE.isSubtype(NO_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(DATE_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(functionType));
    assertFalse(REGEXP_TYPE.isSubtype(NULL_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(REGEXP_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(STRING_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(REGEXP_TYPE.isSubtype(ALL_TYPE));
    assertFalse(REGEXP_TYPE.isSubtype(VOID_TYPE));

    // canBeCalled
    assertTrue(REGEXP_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(REGEXP_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, NUMBER_TYPE);
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
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertTrue(REGEXP_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(REGEXP_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
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
    assertFalse(REGEXP_TYPE.matchesInt32Context());
    assertFalse(REGEXP_TYPE.matchesNumberContext());
    assertTrue(REGEXP_TYPE.matchesObjectContext());
    assertTrue(REGEXP_TYPE.matchesStringContext());
    assertFalse(REGEXP_TYPE.matchesUint32Context());

    // toString
    assertEquals("RegExp", REGEXP_TYPE.toString());
    assertTrue(REGEXP_TYPE.hasDisplayName());
    assertEquals("RegExp", REGEXP_TYPE.getDisplayName());

    assertTrue(REGEXP_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(REGEXP_TYPE);
    assertFalse(REGEXP_TYPE.isNominalConstructor());
    assertTrue(REGEXP_TYPE.getConstructor().isNominalConstructor());
  }

  /**
   * Tests the behavior of the string object type.
   */
  public void testStringObjectType() throws Exception {
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
    assertTrue(STRING_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertTrue(STRING_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(STRING_TYPE));
    assertTrue(STRING_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(STRING_OBJECT_TYPE.isSubtype(STRING_TYPE));

    // canBeCalled
    assertFalse(STRING_OBJECT_TYPE.canBeCalled());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_TYPE);
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
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertTrue(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(STRING_OBJECT_TYPE.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // properties (ECMA-262 page 98 - 106)
    assertEquals(23, STRING_OBJECT_TYPE.getImplicitPrototype().
        getPropertiesCount());
    assertEquals(24, STRING_OBJECT_TYPE.getPropertiesCount());

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
    assertTrue(STRING_OBJECT_TYPE.matchesInt32Context());
    assertTrue(STRING_OBJECT_TYPE.matchesNumberContext());
    assertTrue(STRING_OBJECT_TYPE.matchesObjectContext());
    assertTrue(STRING_OBJECT_TYPE.matchesStringContext());
    assertTrue(STRING_OBJECT_TYPE.matchesUint32Context());

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

  /**
   * Tests the behavior of the string value type.
   */
  public void testStringValueType() throws Exception {
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
    assertTrue(STRING_TYPE.isSubtype(ALL_TYPE));
    assertFalse(STRING_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(STRING_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(STRING_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(STRING_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(STRING_TYPE.isSubtype(DATE_TYPE));
    assertFalse(STRING_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(STRING_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(STRING_TYPE.isSubtype(STRING_TYPE));
    assertTrue(STRING_TYPE.isSubtype(UNKNOWN_TYPE));

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
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(STRING_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(STRING_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(STRING_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertTrue(STRING_TYPE.matchesInt32Context());
    assertTrue(STRING_TYPE.matchesNumberContext());
    assertTrue(STRING_TYPE.matchesObjectContext());
    assertTrue(STRING_TYPE.matchesStringContext());
    assertTrue(STRING_TYPE.matchesUint32Context());

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


  /**
   * Tests the behavior of record types.
   */
  public void testRecordType() throws Exception {
    // isXxx
    assertTrue(recordType.isObject());
    assertFalse(recordType.isFunctionPrototypeType());

    // isSubtype
    assertTrue(recordType.isSubtype(ALL_TYPE));
    assertFalse(recordType.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(recordType.isSubtype(NUMBER_TYPE));
    assertFalse(recordType.isSubtype(DATE_TYPE));
    assertFalse(recordType.isSubtype(REGEXP_TYPE));
    assertTrue(recordType.isSubtype(UNKNOWN_TYPE));
    assertTrue(recordType.isSubtype(OBJECT_TYPE));
    assertFalse(recordType.isSubtype(U2U_CONSTRUCTOR_TYPE));

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

    // canTestForShallowEqualityWith
    assertTrue(recordType.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(recordType.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(recordType));
    assertFalse(recordType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(recordType.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(recordType.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(recordType.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(recordType.matchesInt32Context());
    assertFalse(recordType.matchesNumberContext());
    assertTrue(recordType.matchesObjectContext());
    assertFalse(recordType.matchesStringContext());
    assertFalse(recordType.matchesUint32Context());

    Asserts.assertResolvesToSame(recordType);
  }

  /**
   * Tests the behavior of the instance of Function.
   */
  public void testFunctionInstanceType() throws Exception {
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
    assertFalse(functionInst.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(functionInst));
    assertFalse(functionInst.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(functionInst.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(functionInst.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(functionInst.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(functionInst.matchesInt32Context());
    assertFalse(functionInst.matchesNumberContext());
    assertTrue(functionInst.matchesObjectContext());
    assertFalse(functionInst.matchesStringContext());
    assertFalse(functionInst.matchesUint32Context());

    // hasProperty
    assertTrue(functionInst.hasProperty("prototype"));
    assertPropertyTypeInferred(functionInst, "prototype");

    // misc
    assertTypeEquals(FUNCTION_FUNCTION_TYPE, functionInst.getConstructor());
    assertTypeEquals(FUNCTION_PROTOTYPE, functionInst.getImplicitPrototype());
    assertTypeEquals(functionInst, FUNCTION_FUNCTION_TYPE.getInstanceType());

    Asserts.assertResolvesToSame(functionInst);
  }

  /**
   * Tests the behavior of functional types.
   */
  public void testFunctionType() throws Exception {
    // isXxx
    assertTrue(functionType.isObject());
    assertFalse(functionType.isFunctionPrototypeType());
    assertTrue(functionType.getImplicitPrototype().getImplicitPrototype()
        .isFunctionPrototypeType());

    // isSubtype
    assertTrue(functionType.isSubtype(ALL_TYPE));
    assertFalse(functionType.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(functionType.isSubtype(NUMBER_TYPE));
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

    // canTestForShallowEqualityWith
    assertTrue(functionType.canTestForShallowEqualityWith(NO_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(NO_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(ARRAY_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(BOOLEAN_TYPE));
    assertFalse(functionType.
        canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(DATE_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(functionType));
    assertFalse(functionType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(functionType.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(functionType.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(functionType.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // matchesXxx
    assertFalse(functionType.matchesInt32Context());
    assertFalse(functionType.matchesNumberContext());
    assertTrue(functionType.matchesObjectContext());
    assertFalse(functionType.matchesStringContext());
    assertFalse(functionType.matchesUint32Context());

    // hasProperty
    assertTrue(functionType.hasProperty("prototype"));
    assertPropertyTypeInferred(functionType, "prototype");

    Asserts.assertResolvesToSame(functionType);


    assertEquals("aFunctionName", new FunctionBuilder(registry).
        withName("aFunctionName").build().getDisplayName());
  }

  /**
   * Tests the subtyping relation of record types.
   */
  public void testRecordTypeSubtyping() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    assertTrue(subRecordType.isSubtype(recordType));
    assertFalse(recordType.isSubtype(subRecordType));

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", OBJECT_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    JSType differentRecordType = builder.build();

    assertFalse(differentRecordType.isSubtype(recordType));
    assertFalse(recordType.isSubtype(differentRecordType));
  }

  /**
   * Tests the subtyping relation of record types when an object has
   * an inferred property..
   */
  public void testRecordTypeSubtypingWithInferredProperties() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", googSubBarInst, null);
    JSType record = builder.build();

    ObjectType subtypeProp = registry.createAnonymousObjectType(null);
    subtypeProp.defineInferredProperty("a", googSubSubBarInst, null);
    assertTrue(subtypeProp.isSubtype(record));
    assertFalse(record.isSubtype(subtypeProp));

    ObjectType supertypeProp = registry.createAnonymousObjectType(null);
    supertypeProp.defineInferredProperty("a", googBarInst, null);
    assertFalse(supertypeProp.isSubtype(record));
    assertFalse(record.isSubtype(supertypeProp));

    ObjectType declaredSubtypeProp = registry.createAnonymousObjectType(null);
    declaredSubtypeProp.defineDeclaredProperty("a", googSubSubBarInst, null);
    assertTrue(declaredSubtypeProp.isSubtype(record));
    assertFalse(record.isSubtype(declaredSubtypeProp));
  }

  /**
   * Tests the getLeastSupertype method for record types.
   */
  public void testRecordTypeLeastSuperType1() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    JSType leastSupertype = recordType.getLeastSupertype(subRecordType);
    assertTypeEquals(leastSupertype, recordType);
  }

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

  public void testRecordTypeLeastSuperType4() {
    JSType leastSupertype = recordType.getLeastSupertype(OBJECT_TYPE);
    assertTypeEquals(leastSupertype, OBJECT_TYPE);
  }

  /**
   * Tests the getGreatestSubtype method for record types.
   */
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

  public void testRecordTypeGreatestSubType2() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

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

  public void testRecordTypeGreatestSubType4() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);
    assertTypeEquals(subtype, NO_TYPE);
  }

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

  public void testRecordTypeGreatestSubType12() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    FunctionType googBarArgConstructor = registry.createConstructorType(
        "barArg", null, registry.createParameters(googBar), null, null);

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

  /**
   * Tests the "apply" method on the function type.
   */
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

  /**
   * Tests the "call" method on the function type.
   */
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

  /**
   * Tests the representation of function types.
   */
  public void testFunctionTypeRepresentation() {
    assertEquals("function (number, string): boolean",
        registry.createFunctionType(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE).toString());

    assertEquals("function (new:Array, ...*): Array",
        ARRAY_FUNCTION_TYPE.toString());

    assertEquals("function (new:Boolean, *=): boolean",
        BOOLEAN_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function (new:Number, *=): number",
        NUMBER_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function (new:String, *=): string",
        STRING_OBJECT_FUNCTION_TYPE.toString());

    assertEquals("function (...number): boolean",
        registry.createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE).toString());

    assertEquals("function (number, ...string): boolean",
        registry.createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE).toString());

    assertEquals("function (this:Date, number): (boolean|number|string)",
        new FunctionBuilder(registry)
            .withParamsNode(registry.createParameters(NUMBER_TYPE))
            .withReturnType(NUMBER_STRING_BOOLEAN)
            .withTypeOfThis(DATE_TYPE)
            .build().toString());
  }

  /**
   * Tests relationships between structural function types.
   */
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

  /**
   * Tests relationships between structural function types.
   */
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
   * Tests that defining a property of a function's {@code prototype} adds the
   * property to it instance type.
   */
  public void testFunctionPrototypeAndImplicitPrototype1() {
    FunctionType constructor =
        registry.createConstructorType("Foo", null, null, null, null);
    ObjectType instance = constructor.getInstanceType();

    // adding one property on the prototype
    ObjectType prototype =
        (ObjectType) constructor.getPropertyType("prototype");
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);

    assertEquals(NATIVE_PROPERTIES_COUNT + 1, instance.getPropertiesCount());
  }

  /**
   * Tests that replacing a function's {@code prototype} changes the visible
   * properties of its instance type.
   */
  public void testFunctionPrototypeAndImplicitPrototype2() {
    FunctionType constructor = registry.createConstructorType(
        null, null, registry.createParameters(null, null, null), null, null);
    ObjectType instance = constructor.getInstanceType();

    // replacing the prototype
    ObjectType prototype = registry.createAnonymousObjectType(null);
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);
    constructor.defineDeclaredProperty("prototype", prototype, null);

    assertEquals(NATIVE_PROPERTIES_COUNT + 1, instance.getPropertiesCount());
  }

  /** Tests assigning JsDoc on a prototype property. */
  public void testJSDocOnPrototypeProperty() throws Exception {
    subclassCtor.setPropertyJSDocInfo("prototype",
        new JSDocInfoBuilder(false).build());
    assertNull(subclassCtor.getOwnPropertyJSDocInfo("prototype"));
  }

  /**
   * Tests operation of {@code isVoidable}.
   * @throws Exception
   */
  public void testIsVoidable() throws Exception {
    assertTrue(VOID_TYPE.isVoidable());
    assertFalse(NULL_TYPE.isVoidable());
    assertTrue(createUnionType(NUMBER_TYPE, VOID_TYPE).isVoidable());
  }

  /**
   * Tests the behavior of the void type.
   */
  public void testVoidType() throws Exception {
    // isSubtype
    assertTrue(VOID_TYPE.isSubtype(ALL_TYPE));
    assertFalse(VOID_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(VOID_TYPE.isSubtype(REGEXP_TYPE));

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
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(VOID_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(VOID_TYPE.canTestForShallowEqualityWith(
            createUnionType(NUMBER_TYPE, VOID_TYPE)));

    // matchesXxx
    assertFalse(VOID_TYPE.matchesInt32Context());
    assertFalse(VOID_TYPE.matchesNumberContext());
    assertFalse(VOID_TYPE.matchesObjectContext());
    assertTrue(VOID_TYPE.matchesStringContext());
    assertFalse(VOID_TYPE.matchesUint32Context());

    Asserts.assertResolvesToSame(VOID_TYPE);
  }

  /**
   * Tests the behavior of the boolean type.
   */
  public void testBooleanValueType() throws Exception {
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
    assertTrue(BOOLEAN_TYPE.isSubtype(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(functionType));
    assertFalse(BOOLEAN_TYPE.isSubtype(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(DATE_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(BOOLEAN_TYPE.isSubtype(namedGoogBar));
    assertFalse(BOOLEAN_TYPE.isSubtype(REGEXP_TYPE));

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
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(functionType));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.canTestForShallowEqualityWith(VOID_TYPE));
    assertTrue(BOOLEAN_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE));

    // isNullable
    assertFalse(BOOLEAN_TYPE.isNullable());
    assertFalse(BOOLEAN_TYPE.isVoidable());

    // matchesXxx
    assertTrue(BOOLEAN_TYPE.matchesInt32Context());
    assertTrue(BOOLEAN_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_TYPE.matchesObjectContext());
    assertTrue(BOOLEAN_TYPE.matchesStringContext());
    assertTrue(BOOLEAN_TYPE.matchesUint32Context());

    // toString
    assertEquals("boolean", BOOLEAN_TYPE.toString());
    assertTrue(BOOLEAN_TYPE.hasDisplayName());
    assertEquals("boolean", BOOLEAN_TYPE.getDisplayName());

    Asserts.assertResolvesToSame(BOOLEAN_TYPE);
  }

  /**
   * Tests the behavior of the Boolean type.
   */
  public void testBooleanObjectType() throws Exception {
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
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(unresolvedNamedType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(namedGoogBar));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(REGEXP_TYPE));
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
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(BOOLEAN_OBJECT_TYPE.isNullable());
    assertFalse(BOOLEAN_OBJECT_TYPE.isVoidable());

    // matchesXxx
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesInt32Context());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesNumberContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesObjectContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesStringContext());
    assertTrue(BOOLEAN_OBJECT_TYPE.matchesUint32Context());

    // toString
    assertEquals("Boolean", BOOLEAN_OBJECT_TYPE.toString());
    assertTrue(BOOLEAN_OBJECT_TYPE.hasDisplayName());
    assertEquals("Boolean", BOOLEAN_OBJECT_TYPE.getDisplayName());

    assertTrue(BOOLEAN_OBJECT_TYPE.isNativeObjectType());

    Asserts.assertResolvesToSame(BOOLEAN_OBJECT_TYPE);
  }

  /**
   * Tests the behavior of the enum type.
   */
  public void testEnumType() throws Exception {
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
    assertFalse(enumType.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(functionType));
    assertFalse(enumType.canTestForShallowEqualityWith(NULL_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertTrue(enumType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(enumType.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(enumType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(enumType.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(enumType.isNullable());
    assertFalse(enumType.isVoidable());

    // matchesXxx
    assertFalse(enumType.matchesInt32Context());
    assertFalse(enumType.matchesNumberContext());
    assertTrue(enumType.matchesObjectContext());
    assertTrue(enumType.matchesStringContext());
    assertFalse(enumType.matchesUint32Context());

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

  /**
   * Tests the behavior of the enum element type.
   */
  public void testEnumElementType() throws Exception {
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
    assertFalse(elementsType.canTestForShallowEqualityWith(ERROR_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(EVAL_ERROR_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(functionType));
    assertFalse(elementsType.canTestForShallowEqualityWith(NULL_TYPE));
    assertTrue(elementsType.canTestForShallowEqualityWith(NUMBER_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(OBJECT_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(URI_ERROR_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(RANGE_ERROR_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(REFERENCE_ERROR_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(REGEXP_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(STRING_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(STRING_OBJECT_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(SYNTAX_ERROR_TYPE));
    assertFalse(elementsType.
        canTestForShallowEqualityWith(TYPE_ERROR_TYPE));
    assertTrue(elementsType.canTestForShallowEqualityWith(ALL_TYPE));
    assertFalse(elementsType.canTestForShallowEqualityWith(VOID_TYPE));

    // isNullable
    assertFalse(elementsType.isNullable());
    assertFalse(elementsType.isVoidable());

    // matchesXxx
    assertTrue(elementsType.matchesInt32Context());
    assertTrue(elementsType.matchesNumberContext());
    assertTrue(elementsType.matchesObjectContext());
    assertTrue(elementsType.matchesStringContext());
    assertTrue(elementsType.matchesUint32Context());

    // toString
    assertEquals("Enum<number>", elementsType.toString());
    assertTrue(elementsType.hasDisplayName());
    assertEquals("Enum", elementsType.getDisplayName());

    Asserts.assertResolvesToSame(elementsType);
  }

  public void testStringEnumType() throws Exception {
    EnumElementType stringEnum =
        new EnumType(registry, "Enum", null, STRING_TYPE).getElementsType();

    assertTypeEquals(UNKNOWN_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertEquals(false, stringEnum.hasProperty("length"));
    assertTypeEquals(STRING_OBJECT_TYPE, stringEnum.autoboxesTo());
    assertNull(stringEnum.getConstructor());

    Asserts.assertResolvesToSame(stringEnum);
  }

  public void testStringObjectEnumType() throws Exception {
    EnumElementType stringEnum =
        new EnumType(registry, "Enum", null, STRING_OBJECT_TYPE)
        .getElementsType();

    assertTypeEquals(NUMBER_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertEquals(true, stringEnum.hasProperty("length"));
    assertTypeEquals(STRING_OBJECT_FUNCTION_TYPE, stringEnum.getConstructor());
  }


  /**
   * Tests object types.
   */
  public void testObjectType() throws Exception {
    PrototypeObjectType objectType =
        new PrototypeObjectType(registry, null, null);

    // isXxx
    assertFalse(objectType.isAllType());
    assertFalse(objectType.isArrayType());
    assertFalse(objectType.isDateType());
    assertFalse(objectType.isFunctionPrototypeType());
    assertTrue(objectType.getImplicitPrototype() == OBJECT_TYPE);

    // isSubtype
    assertTrue(objectType.isSubtype(ALL_TYPE));
    assertFalse(objectType.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(objectType.isSubtype(NUMBER_TYPE));
    assertFalse(objectType.isSubtype(functionType));
    assertFalse(objectType.isSubtype(NULL_TYPE));
    assertFalse(objectType.isSubtype(DATE_TYPE));
    assertTrue(objectType.isSubtype(OBJECT_TYPE));
    assertTrue(objectType.isSubtype(unresolvedNamedType));
    assertFalse(objectType.isSubtype(namedGoogBar));
    assertFalse(objectType.isSubtype(REGEXP_TYPE));

    // autoboxesTo
    assertNull(objectType.autoboxesTo());

    // canTestForEqualityWith
    assertCanTestForEqualityWith(objectType, NUMBER_TYPE);

    // matchesXxxContext
    assertFalse(objectType.matchesInt32Context());
    assertFalse(objectType.matchesNumberContext());
    assertTrue(objectType.matchesObjectContext());
    assertFalse(objectType.matchesStringContext());
    assertFalse(objectType.matchesUint32Context());

    // isNullable
    assertFalse(objectType.isNullable());
    assertFalse(objectType.isVoidable());
    assertTrue(createNullableType(objectType).isNullable());
    assertTrue(createUnionType(objectType, VOID_TYPE).isVoidable());

    // toString
    assertEquals("{...}", objectType.toString());
    assertEquals(null, objectType.getDisplayName());
    assertFalse(objectType.hasReferenceName());
    assertEquals("anObject", new PrototypeObjectType(registry, "anObject",
        null).getDisplayName());

    Asserts.assertResolvesToSame(objectType);
  }

  /**
   * Tests the goog.Bar type.
   */
  public void testGoogBar() throws Exception {
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

  /**
   * Tests how properties are counted for object types.
   */
  public void testObjectTypePropertiesCount() throws Exception {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", DATE_TYPE, null);
    assertEquals(nativeProperties + 1, sup.getPropertiesCount());

    sup.defineDeclaredProperty("b", DATE_TYPE, null);
    assertEquals(nativeProperties + 2, sup.getPropertiesCount());

    ObjectType sub = registry.createObjectType(null, sup);
    assertEquals(nativeProperties + 2, sub.getPropertiesCount());
  }

  /**
   * Tests how properties are defined.
   */
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

  /**
   * Tests that properties are correctly counted even when shadowing occurs.
   */
  public void testObjectTypePropertiesCountWithShadowing() {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertEquals(nativeProperties + 1, sup.getPropertiesCount());

    ObjectType sub = registry.createObjectType(null, sup);
    sub.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertEquals(nativeProperties + 1, sub.getPropertiesCount());
  }

  /**
   * Tests the named type goog.Bar.
   */
  public void testNamedGoogBar() throws Exception {
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

  /**
   * Tests the prototype chaining of native objects.
   */
  public void testPrototypeChaining() throws Exception {
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
        ERROR_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        EVAL_ERROR_TYPE.getImplicitPrototype().getImplicitPrototype(),
        ERROR_TYPE);
    assertTypeEquals(
        NUMBER_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        URI_ERROR_TYPE.getImplicitPrototype().getImplicitPrototype(),
        ERROR_TYPE);
    assertTypeEquals(
        RANGE_ERROR_TYPE.getImplicitPrototype().getImplicitPrototype(),
        ERROR_TYPE);
    assertTypeEquals(
        REFERENCE_ERROR_TYPE.getImplicitPrototype().
        getImplicitPrototype(), ERROR_TYPE);
    assertTypeEquals(
        STRING_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        REGEXP_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        SYNTAX_ERROR_TYPE.getImplicitPrototype().
        getImplicitPrototype(), ERROR_TYPE);
    assertTypeEquals(
        TYPE_ERROR_TYPE.getImplicitPrototype().
        getImplicitPrototype(), ERROR_TYPE);

    // not same
    assertNotSame(EVAL_ERROR_TYPE.getImplicitPrototype(),
        URI_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(EVAL_ERROR_TYPE.getImplicitPrototype(),
        RANGE_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(EVAL_ERROR_TYPE.getImplicitPrototype(),
        REFERENCE_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(EVAL_ERROR_TYPE.getImplicitPrototype(),
        SYNTAX_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(EVAL_ERROR_TYPE.getImplicitPrototype(),
        TYPE_ERROR_TYPE.getImplicitPrototype());

    assertNotSame(URI_ERROR_TYPE.getImplicitPrototype(),
        RANGE_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(URI_ERROR_TYPE.getImplicitPrototype(),
        REFERENCE_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(URI_ERROR_TYPE.getImplicitPrototype(),
        SYNTAX_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(URI_ERROR_TYPE.getImplicitPrototype(),
        TYPE_ERROR_TYPE.getImplicitPrototype());

    assertNotSame(RANGE_ERROR_TYPE.getImplicitPrototype(),
        REFERENCE_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(RANGE_ERROR_TYPE.getImplicitPrototype(),
        SYNTAX_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(RANGE_ERROR_TYPE.getImplicitPrototype(),
        TYPE_ERROR_TYPE.getImplicitPrototype());

    assertNotSame(REFERENCE_ERROR_TYPE.getImplicitPrototype(),
        SYNTAX_ERROR_TYPE.getImplicitPrototype());
    assertNotSame(REFERENCE_ERROR_TYPE.getImplicitPrototype(),
        TYPE_ERROR_TYPE.getImplicitPrototype());

    assertNotSame(SYNTAX_ERROR_TYPE.getImplicitPrototype(),
        TYPE_ERROR_TYPE.getImplicitPrototype());
  }

  /**
   * Tests that function instances have their constructor pointer back at the
   * function that created them.
   */
  public void testInstanceFunctionChaining() throws Exception {
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

    // Error
    assertTypeEquals(
        ERROR_FUNCTION_TYPE, ERROR_TYPE.getConstructor());

    // EvalError
    assertTypeEquals(
        EVAL_ERROR_FUNCTION_TYPE, EVAL_ERROR_TYPE.getConstructor());

    // Number
    assertTypeEquals(
        NUMBER_OBJECT_FUNCTION_TYPE,
        NUMBER_OBJECT_TYPE.getConstructor());

    // Object
    assertTypeEquals(
        OBJECT_FUNCTION_TYPE, OBJECT_TYPE.getConstructor());

    // RangeError
    assertTypeEquals(
        RANGE_ERROR_FUNCTION_TYPE, RANGE_ERROR_TYPE.getConstructor());

    // ReferenceError
    assertTypeEquals(
        REFERENCE_ERROR_FUNCTION_TYPE,
        REFERENCE_ERROR_TYPE.getConstructor());

    // RegExp
    assertTypeEquals(REGEXP_FUNCTION_TYPE, REGEXP_TYPE.getConstructor());

    // String
    assertTypeEquals(
        STRING_OBJECT_FUNCTION_TYPE,
        STRING_OBJECT_TYPE.getConstructor());

    // SyntaxError
    assertTypeEquals(
        SYNTAX_ERROR_FUNCTION_TYPE,
        SYNTAX_ERROR_TYPE.getConstructor());

    // TypeError
    assertTypeEquals(
        TYPE_ERROR_FUNCTION_TYPE, TYPE_ERROR_TYPE.getConstructor());

    // URIError
    assertTypeEquals(
        URI_ERROR_FUNCTION_TYPE, URI_ERROR_TYPE.getConstructor());
  }

  /**
   * Tests that the method {@link JSType#canTestForEqualityWith(JSType)} handles
   * special corner cases.
   */
  @SuppressWarnings("checked")
  public void testCanTestForEqualityWithCornerCases() {
    // null == undefined is always true
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // (Object,null) == undefined could be true or false
    UnionType nullableObject =
        (UnionType) createUnionType(OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(nullableObject, VOID_TYPE);
    assertCanTestForEqualityWith(VOID_TYPE, nullableObject);
  }

  /**
   * Tests the {@link JSType#testForEquality(JSType)} method.
   */
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

  /**
   * Tests the subtyping relationships among simple types.
   */
  public void testSubtypingSimpleTypes() throws Exception {
    // Any
    assertTrue(NO_TYPE.isSubtype(NO_TYPE));
    assertTrue(NO_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(NO_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(NO_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NO_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(functionType));
    assertTrue(NO_TYPE.isSubtype(NULL_TYPE));
    assertTrue(NO_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(NO_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(URI_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(REGEXP_TYPE));
    assertTrue(NO_TYPE.isSubtype(STRING_TYPE));
    assertTrue(NO_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NO_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(NO_TYPE.isSubtype(ALL_TYPE));
    assertTrue(NO_TYPE.isSubtype(VOID_TYPE));

    // AnyObject
    assertFalse(NO_OBJECT_TYPE.isSubtype(NO_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(functionType));
    assertFalse(NO_OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(URI_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(STRING_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(NO_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(NO_OBJECT_TYPE.isSubtype(VOID_TYPE));

    // Array
    assertFalse(ARRAY_TYPE.isSubtype(NO_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(DATE_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(functionType));
    assertFalse(ARRAY_TYPE.isSubtype(NULL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(STRING_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(ALL_TYPE));
    assertFalse(ARRAY_TYPE.isSubtype(VOID_TYPE));

    // boolean
    assertFalse(BOOLEAN_TYPE.isSubtype(NO_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(ARRAY_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(DATE_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(functionType));
    assertFalse(BOOLEAN_TYPE.isSubtype(NULL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(STRING_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(BOOLEAN_TYPE.isSubtype(ALL_TYPE));
    assertFalse(BOOLEAN_TYPE.isSubtype(VOID_TYPE));

    // Boolean
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NO_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(BOOLEAN_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(DATE_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(functionType));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NULL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(STRING_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(BOOLEAN_OBJECT_TYPE.isSubtype(ALL_TYPE));
    assertFalse(BOOLEAN_OBJECT_TYPE.isSubtype(VOID_TYPE));

    // Date
    assertFalse(DATE_TYPE.isSubtype(NO_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(DATE_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(DATE_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtype(DATE_TYPE));
    assertFalse(DATE_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(functionType));
    assertFalse(DATE_TYPE.isSubtype(NULL_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(DATE_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(DATE_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(DATE_TYPE.isSubtype(STRING_TYPE));
    assertFalse(DATE_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(DATE_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(DATE_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(DATE_TYPE.isSubtype(ALL_TYPE));
    assertFalse(DATE_TYPE.isSubtype(VOID_TYPE));

    // Error
    assertFalse(ERROR_TYPE.isSubtype(NO_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(DATE_TYPE));
    assertTrue(ERROR_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(functionType));
    assertFalse(ERROR_TYPE.isSubtype(NULL_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(ERROR_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(STRING_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(ERROR_TYPE.isSubtype(ALL_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(VOID_TYPE));

    // EvalError
    assertFalse(EVAL_ERROR_TYPE.isSubtype(NO_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(DATE_TYPE));
    assertTrue(EVAL_ERROR_TYPE.isSubtype(ERROR_TYPE));
    assertTrue(EVAL_ERROR_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(functionType));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(NULL_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertTrue(EVAL_ERROR_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(STRING_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(EVAL_ERROR_TYPE.isSubtype(ALL_TYPE));
    assertFalse(EVAL_ERROR_TYPE.isSubtype(VOID_TYPE));

    // RangeError
    assertTrue(RANGE_ERROR_TYPE.isSubtype(ERROR_TYPE));

    // ReferenceError
    assertTrue(REFERENCE_ERROR_TYPE.isSubtype(ERROR_TYPE));

    // TypeError
    assertTrue(TYPE_ERROR_TYPE.isSubtype(ERROR_TYPE));

    // UriError
    assertTrue(URI_ERROR_TYPE.isSubtype(ERROR_TYPE));

    // Unknown
    assertFalse(ALL_TYPE.isSubtype(NO_TYPE));
    assertFalse(ALL_TYPE.isSubtype(NO_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(ARRAY_TYPE));
    assertFalse(ALL_TYPE.isSubtype(BOOLEAN_TYPE));
    assertFalse(ALL_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE));
    assertFalse(ERROR_TYPE.isSubtype(DATE_TYPE));
    assertFalse(ALL_TYPE.isSubtype(ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(EVAL_ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(functionType));
    assertFalse(ALL_TYPE.isSubtype(NULL_TYPE));
    assertFalse(ALL_TYPE.isSubtype(NUMBER_TYPE));
    assertFalse(ALL_TYPE.isSubtype(NUMBER_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(URI_ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(RANGE_ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(REFERENCE_ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(REGEXP_TYPE));
    assertFalse(ALL_TYPE.isSubtype(STRING_TYPE));
    assertFalse(ALL_TYPE.isSubtype(STRING_OBJECT_TYPE));
    assertFalse(ALL_TYPE.isSubtype(SYNTAX_ERROR_TYPE));
    assertFalse(ALL_TYPE.isSubtype(TYPE_ERROR_TYPE));
    assertTrue(ALL_TYPE.isSubtype(ALL_TYPE));
    assertFalse(ALL_TYPE.isSubtype(VOID_TYPE));
  }

  /**
   * Tests that the Object type is the greatest element (top) of the object
   * hierarchy.
   */
  public void testSubtypingObjectTopOfObjects() throws Exception {
    assertTrue(OBJECT_TYPE.isSubtype(OBJECT_TYPE));
    assertTrue(createUnionType(DATE_TYPE, REGEXP_TYPE).isSubtype(OBJECT_TYPE));
    assertTrue(createUnionType(OBJECT_TYPE, NO_OBJECT_TYPE).
        isSubtype(OBJECT_TYPE));
    assertTrue(functionType.isSubtype(OBJECT_TYPE));
  }

  public void testSubtypingFunctionPrototypeType() throws Exception {
    FunctionType sub1 = registry.createConstructorType(
        null, null, registry.createParameters(null, null, null), null, null);
    sub1.setPrototypeBasedOn(googBar);
    FunctionType sub2 = registry.createConstructorType(
        null, null, registry.createParameters(null, null, null), null, null);
    sub2.setPrototypeBasedOn(googBar);

    ObjectType o1 = sub1.getInstanceType();
    ObjectType o2 = sub2.getInstanceType();

    assertFalse(o1.isSubtype(o2));
    assertFalse(o1.getImplicitPrototype().isSubtype(o2.getImplicitPrototype()));
    assertTrue(o1.getImplicitPrototype().isSubtype(googBar));
    assertTrue(o2.getImplicitPrototype().isSubtype(googBar));
  }

  public void testSubtypingFunctionFixedArgs() throws Exception {
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

  public void testSubtypingFunctionMultipleFixedArgs() throws Exception {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, EVAL_ERROR_TYPE, STRING_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, ERROR_TYPE, ALL_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  public void testSubtypingFunctionFixedArgsNotMatching() throws Exception {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, EVAL_ERROR_TYPE, UNKNOWN_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, ERROR_TYPE, ALL_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  public void testSubtypingFunctionVariableArgsOneOnly() throws Exception {
    // f1 = (EvalError...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, EVAL_ERROR_TYPE);
    // f2 = (Error, Object) -> String
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, ERROR_TYPE, OBJECT_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertFalse(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  public void testSubtypingFunctionVariableArgsBoth() throws Exception {
    // f1 = (UriError, EvalError, EvalError...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, URI_ERROR_TYPE, EVAL_ERROR_TYPE, EVAL_ERROR_TYPE);
    // f2 = (Error, Object, EvalError...) -> String
    FunctionType f2 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, ERROR_TYPE, OBJECT_TYPE, EVAL_ERROR_TYPE);

    assertTrue(f1.isSubtype(f1));
    assertFalse(f1.isSubtype(f2));
    assertTrue(f2.isSubtype(f1));
    assertTrue(f2.isSubtype(f2));

    assertTrue(f1.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(f2.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f1));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(f2));
  }

  public void testSubtypingMostGeneralFunction() throws Exception {
    // (EvalError, String) -> Object
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, EVAL_ERROR_TYPE, STRING_TYPE);
    // (string, void) -> number
    FunctionType f2 = registry.createFunctionType(NUMBER_TYPE, STRING_TYPE, VOID_TYPE);
    // (Date, string, number) -> AnyObject
    FunctionType f3 = registry.createFunctionType(
        NO_OBJECT_TYPE, DATE_TYPE, STRING_TYPE, NUMBER_TYPE);
    // (Number) -> Any
    FunctionType f4 = registry.createFunctionType(NO_TYPE, NUMBER_OBJECT_TYPE);
    // f1 = (EvalError...) -> Object
    FunctionType f5 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, EVAL_ERROR_TYPE);
    // f2 = (Error, Object) -> String
    FunctionType f6 = registry.createFunctionType(STRING_OBJECT_TYPE, ERROR_TYPE, OBJECT_TYPE);
    // f1 = (UriError, EvalError...) -> Object
    FunctionType f7 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, URI_ERROR_TYPE, EVAL_ERROR_TYPE);
    // f2 = (Error, Object, EvalError...) -> String
    FunctionType f8 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, ERROR_TYPE, OBJECT_TYPE, EVAL_ERROR_TYPE);

    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(GREATEST_FUNCTION_TYPE));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(LEAST_FUNCTION_TYPE));

    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(LEAST_FUNCTION_TYPE));
    assertTrue(GREATEST_FUNCTION_TYPE.isSubtype(U2U_CONSTRUCTOR_TYPE));
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

    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f1));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f2));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f3));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f4));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f5));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f6));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f7));
    assertTrue(LEAST_FUNCTION_TYPE.isSubtype(f8));

    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f1));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f2));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f3));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f4));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f5));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f6));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f7));
    assertFalse(GREATEST_FUNCTION_TYPE.isSubtype(f8));
  }

  /**
   * Types to test for symmetrical relationships.
   */
  private List<JSType> getTypesToTestForSymmetry() {
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

  /**
   * Tests that getLeastSupertype is a symmetric relation.
   */
  public void testSymmetryOfLeastSupertype() {
    List<JSType> listA = getTypesToTestForSymmetry();
    List<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getLeastSupertype(typeB);
        JSType bOnA = typeB.getLeastSupertype(typeA);

        // Use a custom assert message instead of the normal assertTypeEquals,
        // to make it more helpful.
        assertTrue(
            String.format("getLeastSupertype not symmetrical:\n" +
                "typeA: %s\ntypeB: %s\n" +
                "a.getLeastSupertype(b): %s\n" +
                "b.getLeastSupertype(a): %s\n",
                typeA, typeB, aOnB, bOnA),
            aOnB.isEquivalentTo(bOnA));
      }
    }
  }

  public void testWeirdBug() {
    assertTypeNotEquals(googBar, googBar.getInstanceType());
    assertFalse(googBar.isSubtype(googBar.getInstanceType()));
    assertFalse(googBar.getInstanceType().isSubtype(googBar));
  }

  /**
   * Tests that getGreatestSubtype is a symmetric relation.
   */
  public void testSymmetryOfGreatestSubtype() {
    List<JSType> listA = getTypesToTestForSymmetry();
    List<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getGreatestSubtype(typeB);
        JSType bOnA = typeB.getGreatestSubtype(typeA);

        // Use a custom assert message instead of the normal assertTypeEquals,
        // to make it more helpful.
        assertTrue(
            String.format("getGreatestSubtype not symmetrical:\n" +
                "typeA: %s\ntypeB: %s\n" +
                "a.getGreatestSubtype(b): %s\n" +
                "b.getGreatestSubtype(a): %s\n",
                typeA, typeB, aOnB, bOnA),
            aOnB.isEquivalentTo(bOnA));
      }
    }
  }

  /**
   * Tests that getLeastSupertype is a reflexive relation.
   */
  public void testReflexivityOfLeastSupertype() {
    List<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getLeastSupertype not reflexive",
          type, type.getLeastSupertype(type));
    }
  }

  /**
   * Tests that getGreatestSubtype is a reflexive relation.
   */
  public void testReflexivityOfGreatestSubtype() {
    List<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getGreatestSubtype not reflexive",
          type, type.getGreatestSubtype(type));
    }
  }

  /**
   * Tests {@link JSType#getLeastSupertype(JSType)} for unresolved named types.
   */
  public void testLeastSupertypeUnresolvedNamedType() {
    // (undefined,function(?):?) and ? unresolved named type
    JSType expected = registry.createUnionType(
        unresolvedNamedType, U2U_FUNCTION_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(U2U_FUNCTION_TYPE));
    assertTypeEquals(expected,
        U2U_FUNCTION_TYPE.getLeastSupertype(unresolvedNamedType));
    assertEquals("(function (...?): ?|not.resolved.named.type)",
        expected.toString());
  }

  public void testLeastSupertypeUnresolvedNamedType2() {
    JSType expected = registry.createUnionType(
        unresolvedNamedType, UNKNOWN_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(expected,
        UNKNOWN_TYPE.getLeastSupertype(unresolvedNamedType));
    assertTypeEquals(UNKNOWN_TYPE, expected);
  }

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
  public void testSubclassOfUnresolvedNamedType() {
    assertTrue(subclassOfUnresolvedNamedType.isUnknownType());
  }

  /**
   * Tests that Proxied FunctionTypes behave the same over getLeastSupertype and
   * getGreatestSubtype as non proxied FunctionTypes
   */
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

  public void testTypeOfThisIsProxied() {
    ObjectType fnType = new FunctionBuilder(registry)
        .withReturnType(NUMBER_TYPE).withTypeOfThis(OBJECT_TYPE).build();
    ObjectType proxyType = new ProxyObjectType(registry, fnType);
    assertTypeEquals(fnType.getTypeOfThis(), proxyType.getTypeOfThis());
  }

  /**
   * Tests the {@link NamedType#equals} function, which had a bug in it.
   */
  public void testNamedTypeEquals() {
    JSTypeRegistry jst = new JSTypeRegistry(null);

    // test == if references are equal
    NamedType a = new NamedType(jst, "type1", "source", 1, 0);
    NamedType b = new NamedType(jst, "type1", "source", 1, 0);
    assertTrue(a.isEquivalentTo(b));

    // test == instance of referenced type
    assertTrue(namedGoogBar.isEquivalentTo(googBar.getInstanceType()));
    assertTrue(googBar.getInstanceType().isEquivalentTo(namedGoogBar));
  }

  /**
   * Tests the {@link NamedType#equals} function against other types.
   */
  public void testNamedTypeEquals2() {
    // test == if references are equal
    NamedType a = new NamedType(registry, "typeA", "source", 1, 0);
    NamedType b = new NamedType(registry, "typeB", "source", 1, 0);

    ObjectType realA = registry.createConstructorType(
        "typeA", null, null, null, null).getInstanceType();
    ObjectType realB = registry.createEnumType(
        "typeB", null, NUMBER_TYPE).getElementsType();
    registry.declareType("typeA", realA);
    registry.declareType("typeB", realB);

    assertTypeEquals(a, realA);
    assertTypeEquals(b, realB);

    a.resolve(null, null);
    b.resolve(null, null);

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

  /**
   * Tests the {@link NamedType#equals} function against other types
   * when it's forward-declared.
   */
  public void testForwardDeclaredNamedTypeEquals() {
    // test == if references are equal
    NamedType a = new NamedType(registry, "typeA", "source", 1, 0);
    NamedType b = new NamedType(registry, "typeA", "source", 1, 0);
    registry.forwardDeclareType("typeA");

    assertTypeEquals(a, b);

    a.resolve(null, EMPTY_SCOPE);

    assertTrue(a.isResolved());
    assertFalse(b.isResolved());

    assertTypeEquals(a, b);

    assertFalse(a.isEquivalentTo(UNKNOWN_TYPE));
    assertFalse(b.isEquivalentTo(UNKNOWN_TYPE));
    assertTrue(a.isEmptyType());
    assertFalse(a.isNoType());
    assertTrue(a.isNoResolvedType());
  }

  public void testForwardDeclaredNamedType() {
    NamedType a = new NamedType(registry, "typeA", "source", 1, 0);
    registry.forwardDeclareType("typeA");

    assertTypeEquals(UNKNOWN_TYPE, a.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        a.getLeastSupertype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(a));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE.getLeastSupertype(a));
  }

  /**
   * Tests {@link JSType#getGreatestSubtype(JSType)} on simple types.
   */
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
    assertTypeEquals(EVAL_ERROR_TYPE,
        ERROR_TYPE.getGreatestSubtype(EVAL_ERROR_TYPE));
    assertTypeEquals(EVAL_ERROR_TYPE,
        EVAL_ERROR_TYPE.getGreatestSubtype(ERROR_TYPE));
    assertTypeEquals(NO_TYPE,
        NULL_TYPE.getGreatestSubtype(ERROR_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        NUMBER_TYPE.getGreatestSubtype(UNKNOWN_TYPE));

    assertTypeEquals(NO_RESOLVED_TYPE,
        NO_OBJECT_TYPE.getGreatestSubtype(forwardDeclaredNamedType));
    assertTypeEquals(NO_RESOLVED_TYPE,
        forwardDeclaredNamedType.getGreatestSubtype(NO_OBJECT_TYPE));

  }

  /**
   * Tests that a derived class extending a type via a named type is a subtype
   * of it.
   */
  public void testSubtypingDerivedExtendsNamedBaseType() throws Exception {
    ObjectType derived =
        registry.createObjectType(null, registry.createObjectType(null, namedGoogBar));

    assertTrue(derived.isSubtype(googBar.getInstanceType()));
  }

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

  public void testGoogBarSubtypeChain() throws Exception {
    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        googBar,
        googSubBar,
        googSubSubBar,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  public void testConstructorWithArgSubtypeChain() throws Exception {
    FunctionType googBarArgConstructor = registry.createConstructorType(
        "barArg", null, registry.createParameters(googBar), null, null);
    FunctionType googSubBarArgConstructor = registry.createConstructorType(
        "subBarArg", null, registry.createParameters(googSubBar), null, null);

    List<JSType> typeChain = ImmutableList.of(
        registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
        googBarArgConstructor,
        googSubBarArgConstructor,
        registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

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

  public void testInterfaceInheritanceSubtypeChain() throws Exception {
    FunctionType tempType =
      registry.createConstructorType("goog.TempType", null, null, null, null);
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

  public void testTemplatizedTypeSubtypes() {
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfUnknown = createTemplatizedType(
        ARRAY_TYPE, UNKNOWN_TYPE);

    assertFalse(objectOfString.isSubtype(ARRAY_TYPE));
    // TODO(johnlenz): should this be false?
    assertTrue(ARRAY_TYPE.isSubtype(objectOfString));
    assertFalse(objectOfString.isSubtype(ARRAY_TYPE));
    // TODO(johnlenz): should this be false?
    assertTrue(ARRAY_TYPE.isSubtype(objectOfString));

    assertTrue(arrayOfString.isSubtype(ARRAY_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(arrayOfString));
    assertTrue(arrayOfString.isSubtype(arrayOfUnknown));
    assertTrue(arrayOfUnknown.isSubtype(arrayOfString));

    assertFalse(arrayOfString.isSubtype(arrayOfNumber));
    assertFalse(arrayOfNumber.isSubtype(arrayOfString));

    assertTrue(arrayOfNumber.isSubtype(createUnionType(arrayOfNumber, NULL_VOID)));
    assertFalse(createUnionType(arrayOfNumber, NULL_VOID).isSubtype(arrayOfNumber));
    assertFalse(arrayOfString.isSubtype(createUnionType(arrayOfNumber, NULL_VOID)));
  }

  public void testTemplatizedTypeRelations() throws Exception {
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

    assertEquals(
        "Array",
        JSType.getLeastSupertype(arrayOfString, arrayOfNumber).toString());
    assertEquals(
        "Array",
        JSType.getLeastSupertype(arrayOfNumber, arrayOfString).toString());
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
              if (constructorI.checkEquivalenceHelper(constructorJ,
                  EquivalenceMethod.IDENTITY)) {
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
        if (typeI.isSubtype(typeJ) && typeJ.isSubtype(typeI)) {
          continue;
        }

        if (checkSubtyping) {
          if (i <= j) {
            assertTrue(typeJ + " should be a subtype of " + typeI,
                typeJ.isSubtype(typeI));
            assertTrue(
                "Named " + typeJ + " should be a subtype of Named " + typeI,
                namedTypeJ.isSubtype(namedTypeI));
            assertTrue(
                "Proxy " + typeJ + " should be a subtype of Proxy " + typeI,
                proxyTypeJ.isSubtype(proxyTypeI));
          } else {
            assertFalse(typeJ + " should not be a subtype of " + typeI,
                typeJ.isSubtype(typeI));
            assertFalse(
                "Named " + typeJ + " should not be a subtype of Named " + typeI,
                namedTypeJ.isSubtype(namedTypeI));
            assertFalse(
                "Named " + typeJ + " should not be a subtype of Named " + typeI,
                proxyTypeJ.isSubtype(proxyTypeI));
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
      NamedType namedWrapper = new NamedType(
          registry, name, "[testcode]", -1, -1);
      namedWrapper.setReferencedType(jstype);
      return namedWrapper;
    } else {
      return jstype;
    }
  }

  /**
   * Tests the behavior of
   * {@link JSType#getRestrictedTypeGivenToBooleanOutcome(boolean)}.
   */
  @SuppressWarnings("checked")
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

  public void testRegisterProperty() {
    int i = 0;
    List<JSType> allObjects = new ArrayList<>();
    for (JSType type : types) {
      String propName = "ALF" + i++;
      if (type instanceof ObjectType) {

        ObjectType objType = (ObjectType) type;
        objType.defineDeclaredProperty(propName, UNKNOWN_TYPE, null);
        objType.defineDeclaredProperty("allHaz", UNKNOWN_TYPE, null);

        assertTypeEquals(type,
            registry.getGreatestSubtypeWithProperty(type, propName));

        assertTypeEquals(NO_TYPE,
            registry.getGreatestSubtypeWithProperty(type, "GRRR"));
        allObjects.add(type);
      }
    }
  }

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

  /**
   * Tests
   * {@link JSTypeRegistry#getGreatestSubtypeWithProperty(JSType, String)}.
   */
  public void testGreatestSubtypeWithProperty() {
    ObjectType foo = registry.createObjectType("foo", OBJECT_TYPE);
    ObjectType bar = registry.createObjectType("bar", namedGoogBar);

    foo.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);
    bar.defineDeclaredProperty("propz", UNKNOWN_TYPE, null);

    assertTypeEquals(bar,
        registry.getGreatestSubtypeWithProperty(namedGoogBar, "propz"));
  }

  public void testGoodSetPrototypeBasedOn() {
    FunctionType fun = registry.createConstructorType(
        "fun", null, null, null, null);
    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertTrue(fun.getInstanceType().isUnknownType());
  }

  public void testLateSetPrototypeBasedOn() {
    FunctionType fun = registry.createConstructorType(
        "fun", null, null, null, null);
    assertFalse(fun.getInstanceType().isUnknownType());

    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertTrue(fun.getInstanceType().isUnknownType());
  }

  public void testGetTypeUnderEquality1() {
    for (JSType type : types) {
      testGetTypeUnderEquality(type, type, type, type);
    }
  }

  public void testGetTypesUnderEquality2() {
    // objects can be equal to numbers
    testGetTypeUnderEquality(
        NUMBER_TYPE, OBJECT_TYPE,
        NUMBER_TYPE, OBJECT_TYPE);
  }

  public void testGetTypesUnderEquality3() {
    // null == undefined
    testGetTypeUnderEquality(
        NULL_TYPE, VOID_TYPE,
        NULL_TYPE, VOID_TYPE);
  }

  @SuppressWarnings("checked")
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
  public void testGetTypesUnderInequality4() throws Exception {
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


  /**
   * Tests the factory method
   * {@link JSTypeRegistry#createRecordType}.
   */
  public void testCreateRecordType() throws Exception {
    Map<String, RecordProperty> properties = new HashMap<>();
    properties.put("hello", new RecordProperty(NUMBER_TYPE, null));

    JSType recordType = registry.createRecordType(properties);
    assertEquals("{hello: number}", recordType.toString());
  }

  /**
   * Tests the factory method {@link JSTypeRegistry#createOptionalType(JSType)}.
   */
  public void testCreateOptionalType() throws Exception {
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

  /**
   * Tests the factory method
   * {@link JSTypeRegistry#createAnonymousObjectType}}.
   */
  public void testCreateAnonymousObjectType() throws Exception {
    // anonymous
    ObjectType anonymous = registry.createAnonymousObjectType(null);
    assertTypeEquals(OBJECT_TYPE, anonymous.getImplicitPrototype());
    assertNull(anonymous.getReferenceName());
    assertEquals("{}", anonymous.toString());
  }

  /**
   * Tests the factory method
   * {@link JSTypeRegistry#createAnonymousObjectType}} and adds
   * some properties to it.
   */
  public void testCreateAnonymousObjectType2() throws Exception {
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
            "  a: number, ",
            "  b: number, ",
            "  c: number, ",
            "  d: number, ",
            "  e: number, ",
            "  f: number",
            "}"),
        anonymous.toString());
  }

  /**
   * Tests the factory method
   * {@link JSTypeRegistry#createObjectType(String, ObjectType)}}.
   */
  public void testCreateObjectType() throws Exception {
    // simple
    ObjectType subDate =
        registry.createObjectType(null, DATE_TYPE.getImplicitPrototype());
    assertTypeEquals(DATE_TYPE.getImplicitPrototype(),
        subDate.getImplicitPrototype());
    assertNull(subDate.getReferenceName());
    assertEquals("{...}", subDate.toString());

    // name, node, prototype
    ObjectType subError = registry.createObjectType("Foo",
        ERROR_TYPE.getImplicitPrototype());
    assertTypeEquals(ERROR_TYPE.getImplicitPrototype(),
        subError.getImplicitPrototype());
    assertEquals("Foo", subError.getReferenceName());
  }

  /**
   * Tests {@code (U2U_CONSTRUCTOR,undefined) <: (U2U_CONSTRUCTOR,undefined)}.
   */
  @SuppressWarnings("checked")
  public void testBug903110() throws Exception {
    UnionType union =
        (UnionType) createUnionType(U2U_CONSTRUCTOR_TYPE, VOID_TYPE);
    assertTrue(VOID_TYPE.isSubtype(union));
    assertTrue(U2U_CONSTRUCTOR_TYPE.isSubtype(union));
    assertTrue(union.isSubtype(union));
  }

  /**
   * Tests {@code U2U_FUNCTION_TYPE <: U2U_CONSTRUCTOR} and
   * {@code U2U_FUNCTION_TYPE <: (U2U_CONSTRUCTOR,undefined)}.
   */
  public void testBug904123() throws Exception {
    assertTrue(U2U_FUNCTION_TYPE.isSubtype(U2U_CONSTRUCTOR_TYPE));
    assertTrue(U2U_FUNCTION_TYPE.
        isSubtype(createOptionalType(U2U_CONSTRUCTOR_TYPE)));
  }

  /**
   * Assert that a type can assign to itself.
   */
  private void assertTypeCanAssignToItself(JSType type) {
    assertTrue(type.isSubtype(type));
  }

  /**
   * Tests that hasOwnProperty returns true when a property is defined directly
   * on a class and false if the property is defined on the supertype or not at
   * all.
   */
  public void testHasOwnProperty() throws Exception {
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

  public void testNamedTypeHasOwnProperty() throws Exception {
    namedGoogBar.getImplicitPrototype().defineProperty("base", null, false,
        null);
    namedGoogBar.defineProperty("sub", null, false, null);

    assertFalse(namedGoogBar.hasOwnProperty("base"));
    assertTrue(namedGoogBar.hasProperty("base"));
    assertTrue(namedGoogBar.hasOwnProperty("sub"));
    assertTrue(namedGoogBar.hasProperty("sub"));
  }

  public void testInterfaceHasOwnProperty() throws Exception {
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

  public void testGetPropertyNames() throws Exception {
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

  public void testGetAndSetJSDocInfoWithNamedType() throws Exception {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordDeprecated();
    JSDocInfo info = builder.build();

    assertNull(namedGoogBar.getOwnPropertyJSDocInfo("X"));
    namedGoogBar.setPropertyJSDocInfo("X", info);
    assertTrue(namedGoogBar.getOwnPropertyJSDocInfo("X").isDeprecated());
    assertPropertyTypeInferred(namedGoogBar, "X");
    assertTypeEquals(UNKNOWN_TYPE, namedGoogBar.getPropertyType("X"));
  }

  public void testGetAndSetJSDocInfoWithObjectTypes() throws Exception {
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

  public void testGetAndSetJSDocInfoWithNoType() throws Exception {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordDeprecated();
    JSDocInfo deprecated = builder.build();

    NO_TYPE.setPropertyJSDocInfo("X", deprecated);
    assertNull(NO_TYPE.getOwnPropertyJSDocInfo("X"));
  }

  public void testObjectGetSubTypes() throws Exception {
    assertTrue(
        containsType(
            OBJECT_FUNCTION_TYPE.getSubTypes(), googBar));
    assertTrue(
        containsType(
            googBar.getSubTypes(), googSubBar));
    assertFalse(
        containsType(
            googBar.getSubTypes(), googSubSubBar));
    assertFalse(
        containsType(
            googSubBar.getSubTypes(), googSubBar));
    assertTrue(
        containsType(
            googSubBar.getSubTypes(), googSubSubBar));
  }

  public void testImplementingType() throws Exception {
    assertTrue(
        containsType(
            registry.getDirectImplementors(
                interfaceType.getInstanceType()),
            googBar));
  }

  public void testIsTemplatedType() throws Exception {
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

  public void testTemplatizedType() throws Exception {
    FunctionType templatizedCtor = registry.createConstructorType(
        "TestingType", null, null, UNKNOWN_TYPE, ImmutableList.of(
            registry.createTemplateType("A"),
            registry.createTemplateType("B")));
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

  public void testPartiallyTemplatizedType() throws Exception {
    FunctionType templatizedCtor = registry.createConstructorType(
        "TestingType", null, null, UNKNOWN_TYPE, ImmutableList.of(
            registry.createTemplateType("A"),
            registry.createTemplateType("B")));
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

  private static boolean containsType(
      Iterable<? extends JSType> types, JSType type) {
    for (JSType alt : types) {
      if (alt.isEquivalentTo(type)) {
        return true;
      }
    }
    return false;
  }

  private static boolean assertTypeListEquals(
      Iterable<? extends JSType> typeListA,
      Iterable<? extends JSType> typeListB) {
    for (JSType alt : typeListA) {
      assertTrue(
          "List : " + typeListA + "\n" +
          "does not contain: " + alt,
          containsType(typeListA, alt));
    }
    for (JSType alt : typeListB) {
      assertTrue(
          "List : " + typeListB + "\n" +
          "does not contain: " + alt,
          containsType(typeListB, alt));
    }
    return false;
  }

  private ArrowType createArrowType(Node params) {
    return registry.createArrowType(params);
  }
}
