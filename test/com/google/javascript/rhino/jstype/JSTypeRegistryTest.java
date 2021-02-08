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
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_ITERATOR_TYPE;
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
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.AbstractStaticScope;
import com.google.javascript.rhino.testing.MapBasedScope;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
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

  private final JSTypeRegistry registry = new JSTypeRegistry(null, null);
  private JSTypeResolver.Closer closer;

  @Before
  @SuppressWarnings({"MustBeClosedChecker"})
  public void setUp() throws Exception {
    this.closer = registry.getResolver().openForDefinition();
  }

  @Test
  public void testGetBuiltInType_boolean() {
    assertType(registry.getType(null, "boolean"))
        .isEqualTo(registry.getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testGetBuiltInType_bigint() {
    assertType(registry.getType(null, "bigint"))
        .isEqualTo(registry.getNativeType(JSTypeNative.BIGINT_TYPE));
  }

  @Test
  public void testGetBuiltInType_iterable() {
    assertType(registry.getGlobalType("Iterable"))
        .isEqualTo(registry.getNativeType(ITERABLE_TYPE));
  }

  @Test
  public void testGetBuiltInType_iterator() {
    assertType(registry.getGlobalType("Iterator"))
        .isEqualTo(registry.getNativeType(ITERATOR_TYPE));
  }

  @Test
  public void testGetBuiltInType_generator() {
    assertType(registry.getGlobalType("Generator"))
        .isEqualTo(registry.getNativeType(GENERATOR_TYPE));
  }

  @Test
  public void testGetBuiltInType_async_iterable() {
    assertType(registry.getGlobalType("AsyncIterable"))
        .isEqualTo(registry.getNativeType(ASYNC_ITERABLE_TYPE));
  }

  @Test
  public void testGetBuiltInType_async_iterator() {
    assertType(registry.getGlobalType("AsyncIterator"))
        .isEqualTo(registry.getNativeType(ASYNC_ITERATOR_TYPE));
  }

  @Test
  public void testGetBuiltInType_async_generator() {
    assertType(registry.getGlobalType("AsyncGenerator"))
        .isEqualTo(registry.getNativeType(ASYNC_GENERATOR_TYPE));
  }

  @Test
  public void testGetBuildInType_iTemplateArray() {
    assertType(registry.getGlobalType("ITemplateArray"))
        .isEqualTo(registry.getNativeType(I_TEMPLATE_ARRAY_TYPE));
  }

  @Test
  public void testGetBuiltInType_Promise() {
    ObjectType promiseType = registry.getNativeObjectType(JSTypeNative.PROMISE_TYPE);
    assertType(registry.getGlobalType("Promise")).isEqualTo(promiseType);

    // Test that it takes one parameter of type
    // function(function((IThenable<TYPE>|TYPE|null|{then: ?})=): ?, function(*=): ?): ?
    FunctionType promiseCtor = promiseType.getConstructor();
    List<FunctionType.Parameter> paramList = promiseCtor.getParameters();
    assertThat(paramList).hasSize(1);
    FunctionType.Parameter firstParameter = paramList.get(0);
    FunctionType paramType = firstParameter.getJSType().toMaybeFunctionType();
    assertThat(paramType.toString())
        .isEqualTo(
            "function(function((IThenable<TYPE>|TYPE|null|{then: ?})=): ?, function(*=): ?): ?");
  }

  @Test
  public void testGetDeclaredType() {
    JSType type = registry.createAnonymousObjectType(null);
    String name = "Foo";
    registry.declareType(null, name, type);
    assertType(registry.getType(null, name)).isEqualTo(type);

    // Ensure different instances are independent.
    JSTypeRegistry typeRegistry2 = new JSTypeRegistry(null);
    assertThat(typeRegistry2.getType(null, name)).isEqualTo(null);
    assertType(registry.getType(null, name)).isEqualTo(type);
  }

  @Test
  public void testReadableTypeName() {

    assertThat(getReadableTypeNameHelper(registry, ALL_TYPE)).isEqualTo("*");

    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_TYPE)).isEqualTo("boolean");
    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_OBJECT_TYPE)).isEqualTo("Boolean");
    assertThat(getReadableTypeNameHelper(registry, BOOLEAN_OBJECT_FUNCTION_TYPE))
        .isEqualTo("function");

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

  @Test
  public void testCreateTypeFromCommentNode_createsNamedTypeIfNameIsUndefined() {
    // Create an empty global scope.
    StaticTypedScope globalScope = new MapBasedScope(ImmutableMap.of());

    JSType type =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("a.b.c")), "srcfile.js", globalScope);

    // The type registry assumes that 'a.b.c' will be defined later.
    assertThat(type).isInstanceOf(NamedType.class);
  }

  @Test
  public void testCreateTypeFromCommentNode_multipleLookupsInSameScope() {
    // Create an empty global scope.
    StaticTypedScope globalScope = new MapBasedScope(ImmutableMap.of());

    JSType typeOne =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("Foo")), "srcfile.js", globalScope);
    JSType typeTwo =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("Foo")), "srcfile.js", globalScope);

    // Different lookups of Foo create equivalent instances of NamedType.
    assertThat(typeOne).isInstanceOf(NamedType.class);
    assertThat(typeTwo).isInstanceOf(NamedType.class);
    assertType(typeOne).isEqualTo(typeTwo);
  }

  @Test
  public void testCreateTypeFromCommentNode_usesGlobalTypeIfExists() {
    // Create a global scope and a global type 'Foo'.
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), new HashSet<>());
    registry.declareType(globalScope, "Foo", registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    // Create an empty child scope.
    StaticTypedScope emptyLocalScope =
        createStaticTypedScope(IR.block(), globalScope, ImmutableMap.of(), new HashSet<>());

    // Looking up 'Foo' in the child scope resolves to the global type `Foo`, not a NamedType.
    JSType type =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("Foo")), "srcfile.js", emptyLocalScope);
    assertThat(type).isNotInstanceOf(NamedType.class);
  }

  @Test
  public void testCreateTypeFromCommentNode_createsNamedTypeIfLocalShadowsGlobalType() {
    // Create a global scope and a global type 'Foo'.
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());
    registry.declareType(globalScope, "Foo", unknownType);

    // Create a child scope containing a slot for 'Foo'.
    Map<String, StaticTypedSlot> slots = new HashMap<>();
    StaticTypedScope localScopeWithFoo =
        createStaticTypedScope(IR.block(), globalScope, slots, ImmutableSet.of());
    slots.put(
        "Foo",
        new SimpleSlot("Foo", unknownType, /* inferred= */ true) {
          @Override
          public StaticTypedScope getScope() {
            return localScopeWithFoo;
          }
        });

    // Looking up 'Foo' in the local scope creates a NamedType; even though `Foo` is not in the
    // JSTypeRegistry in the local scope, the registry assumes it will be defined later.
    JSType type =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("Foo")), "srcfile.js", localScopeWithFoo);
    assertThat(type).isInstanceOf(NamedType.class);
  }

  @Test
  public void testNativeTypesAreUnique() {
    for (JSTypeNative n1 : JSTypeNative.values()) {
      for (JSTypeNative n2 : JSTypeNative.values()) {
        JSType t1 = registry.getNativeType(n1);
        JSType t2 = registry.getNativeType(n2);
        if (!t1.equals(t2)) {
          continue;
        }
        assertThat(t1).isSameInstanceAs(t2);
        assertThat(n1).isEqualTo(n2);
      }
    }
  }

  @Test
  public void testCreateTypeFromCommentNode_usesTopMostScopeOfName() {
    // Create a global scope and a global type 'Foo'.
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), new HashSet<>());
    registry.declareType(globalScope, "Foo", registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    // Create a child scope with no actual variables, but that returns the child scope when asked
    // for the eventual scope of the name "Foo".
    StaticTypedScope emptyLocalScope =
        createStaticTypedScope(IR.block(), globalScope, ImmutableMap.of(), ImmutableSet.of("Foo"));

    // Looking up 'Foo' in the child scope resolves to a NamedType.
    JSType type =
        registry.createTypeFromCommentNode(
            new Node(Token.BANG, IR.string("Foo")), "srcfile.js", emptyLocalScope);
    assertThat(type).isInstanceOf(NamedType.class);
  }

  /** Returns a scope that overrides a few methods from {@link AbstractStaticScope} */
  private StaticTypedScope createStaticTypedScope(
      Node root,
      StaticTypedScope parentScope,
      Map<String, StaticTypedSlot> slots,
      Set<String> reservedNames) {
    return new AbstractStaticScope() {
      @Override
      public Node getRootNode() {
        return root;
      }

      @Override
      public StaticTypedScope getParentScope() {
        return parentScope;
      }

      @Override
      public StaticTypedSlot getSlot(String name) {
        return slots.get(name);
      }

      @Override
      public StaticScope getTopmostScopeOfEventualDeclaration(String name) {
        if (slots.containsKey(name) || reservedNames.contains(name)) {
          return this;
        }
        return parentScope != null ? parentScope.getTopmostScopeOfEventualDeclaration(name) : null;
      }
    };
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
