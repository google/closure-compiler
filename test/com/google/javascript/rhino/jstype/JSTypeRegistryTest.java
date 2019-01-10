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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.I_TEMPLATE_ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_VOID;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static com.google.javascript.rhino.testing.TypeSubject.types;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link JSTypeRegistry}.
 *
 */
@RunWith(JUnit4.class)
public class JSTypeRegistryTest {
  // TODO(user): extend this class with more tests, as JSTypeRegistry is
  // now much larger
  @Test
  public void testGetBuiltInType_boolean() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    assertType(typeRegistry.getType(null, "boolean"))
        .isStructurallyEqualTo(typeRegistry.getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testGetBuiltInType_iterable() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    assertType(typeRegistry.getGlobalType("Iterable"))
        .isStructurallyEqualTo(typeRegistry.getNativeType(ITERABLE_TYPE));
  }

  @Test
  public void testGetBuiltInType_iterator() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    assertType(typeRegistry.getGlobalType("Iterator"))
        .isStructurallyEqualTo(typeRegistry.getNativeType(ITERATOR_TYPE));
  }

  @Test
  public void testGetBuiltInType_generator() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    assertType(typeRegistry.getGlobalType("Generator"))
        .isStructurallyEqualTo(typeRegistry.getNativeType(GENERATOR_TYPE));
  }

  @Test
  public void testGetBuildInType_iTemplateArray() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    assertType(typeRegistry.getGlobalType("ITemplateArray"))
        .isStructurallyEqualTo(typeRegistry.getNativeType(I_TEMPLATE_ARRAY_TYPE));
  }

  @Test
  public void testGetBuiltInType_Promise() {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    ObjectType promiseType = registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE);
    assertType(registry.getGlobalType("Promise")).isStructurallyEqualTo(promiseType);

    // Test that it takes one parameter of type
    // function(function((IThenable<TYPE>|TYPE|null|{then: ?})=): ?, function(*=): ?): ?
    FunctionType promiseCtor = promiseType.getConstructor();
    Node paramList = promiseCtor.getParametersNode();
    Node firstParameter = paramList.getFirstChild();
    assertThat(firstParameter).isNotNull();
    FunctionType paramType = paramList.getFirstChild().getJSType().toMaybeFunctionType();
    assertThat(paramType.toString())
        .isEqualTo(
            "function(function((IThenable<TYPE>|TYPE|null|{then: ?})=): ?, function(*=): ?): ?");
  }

  @Test
  public void testGetDeclaredType() {
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);
    JSType type = typeRegistry.createAnonymousObjectType(null);
    String name = "Foo";
    typeRegistry.declareType(null, name, type);
    assertType(typeRegistry.getType(null, name)).isStructurallyEqualTo(type);

    // Ensure different instances are independent.
    JSTypeRegistry typeRegistry2 = new JSTypeRegistry(null);
    assertThat(typeRegistry2.getType(null, name)).isEqualTo(null);
    assertType(typeRegistry.getType(null, name)).isStructurallyEqualTo(type);
  }

  @Test
  public void testPropertyOnManyTypes() {
    // Given
    JSTypeRegistry typeRegistry = new JSTypeRegistry(null);

    // By default the UnionTypeBuilder will treat a union of more than 30
    // types as an unknown type. We don't want that for property checking
    // so test that the limit is higher.
    for (int i = 0; i < 100; i++) {
      JSType type = typeRegistry.createObjectType("type: " + i, null);

      // When
      typeRegistry.registerPropertyOnType("foo", type);

      // Then
      assertWithMessage("Registered property `foo` on <%s> types.", i + 1)
          .about(types())
          .that(typeRegistry.getGreatestSubtypeWithProperty(type, "foo"))
          .isNotUnknown();
    }
  }

  @Test
  public void testReadableTypeName() {
    JSTypeRegistry registry = new JSTypeRegistry(null);

    assertThat(getReadableTypeNameHelper(registry, ALL_TYPE)).isEqualTo("*");

    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_TYPE)).isEqualTo("boolean");
    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_OBJECT_TYPE)).isEqualTo("Boolean");
    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_OBJECT_FUNCTION_TYPE))
        .isEqualTo("function");

    assertThat(getReadableTypeNameHelper(registry, STRING_VALUE_OR_OBJECT_TYPE))
        .isEqualTo("(String|string)");

    assertThat(getReadableTypeNameHelper(registry, NULL_VOID)).isEqualTo("(null|undefined)");
    assertThat(getReadableTypeNameHelper(registry, NULL_VOID, true)).isEqualTo("(null|undefined)");

    assertThat(
            getReadableTypeNameHelper(
                registry, union(registry, NUMBER_TYPE, STRING_TYPE, NULL_TYPE)))
        .isEqualTo("(number|string|null)");

    assertThat(
            getReadableTypeNameHelper(
                registry, union(registry, NUMBER_TYPE, STRING_TYPE, NULL_TYPE), true))
        .isEqualTo("(Number|String)");
  }

  private JSType union(JSTypeRegistry registry, JSTypeNative... types) {
    return registry.createUnionType(types);
  }

  private String getReadableTypeNameHelper(JSTypeRegistry registry, JSTypeNative type) {
    return getReadableTypeNameHelper(registry, registry.getNativeType(type), false);
  }

  private String getReadableTypeNameHelper(
      JSTypeRegistry registry, JSTypeNative type, boolean deref) {
    return getReadableTypeNameHelper(registry, registry.getNativeType(type), deref);
  }

  private String getReadableTypeNameHelper(JSTypeRegistry registry, JSType type) {
    return getReadableTypeNameHelper(registry, type, false);
  }

  private String getReadableTypeNameHelper(JSTypeRegistry registry, JSType type, boolean deref) {
    Node n = new Node(Token.ADD);
    n.setJSType(type);
    return registry.getReadableJSTypeName(n, deref);
  }
}
