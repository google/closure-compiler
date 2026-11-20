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
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERATOR_LIKE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.I_TEMPLATE_ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_VOID;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.AbstractStaticScope;
import com.google.javascript.rhino.testing.MapBasedScope;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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

  @Before
  @SuppressWarnings({"MustBeClosedChecker"})
  public void setUp() throws Exception {
    JSTypeResolver.Closer unused = registry.getResolver().openForDefinition();
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
    assertType(registry.getGlobalType("Iterable")).isEqualTo(registry.getNativeType(ITERABLE_TYPE));
  }

  @Test
  public void testGetBuiltInType_iterator() {
    assertType(registry.getGlobalType("Iterator")).isEqualTo(registry.getNativeType(ITERATOR_TYPE));
  }

  @Test
  public void testGetBuiltInType_iteratorLike() {
    assertType(registry.getGlobalType("IteratorLike"))
        .isEqualTo(registry.getNativeType(ITERATOR_LIKE_TYPE));
  }

  @Test
  public void testIteratorExtendsIteratorLike() {
    assertThat(
            registry
                .getNativeType(ITERATOR_TYPE)
                .isSubtypeOf(registry.getNativeType(ITERATOR_LIKE_TYPE)))
        .isTrue();
  }

  @Test
  public void testGetBuiltInType_generator() {
    assertType(registry.getGlobalType("Generator"))
        .isEqualTo(registry.getNativeType(GENERATOR_TYPE));
  }

  @Test
  public void testGeneratorExtendsIterator() {
    assertThat(
            registry
                .getNativeType(GENERATOR_TYPE)
                .isSubtypeOf(registry.getNativeType(ITERATOR_TYPE)))
        .isTrue();
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
  public void testGetBuiltInType_gbigint() {
    assertType(registry.getGlobalType("gbigint"))
        .isEqualTo(registry.getNativeType(JSTypeNative.GBIGINT_TYPE));
  }

  @Test
  public void testGetBuiltInType_gbigint_unification() {
    // This simulates what happens when the compiler parses: /** @interface */ function gbigint() {}
    FunctionType interfaceType =
        FunctionType.builder(registry).forInterface().withName("gbigint").build();
    registry.declareType(null, "gbigint", interfaceType.getInstanceType());

    assertType(registry.getGlobalType("gbigint"))
        .isEqualTo(registry.getNativeType(JSTypeNative.GBIGINT_TYPE));
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
    ImmutableList<FunctionType.Parameter> paramList = promiseCtor.getParameters();
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
    assertThat(typeRegistry2.getType(null, name)).isNull();
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

  @Test
  public void testGetBuiltInType_ReadonlyMap() {
    ObjectType readonlyMapType = registry.getNativeObjectType(JSTypeNative.READONLY_MAP_TYPE);
    assertType(registry.getGlobalType("ReadonlyMap")).isEqualTo(readonlyMapType);
  }

  @Test
  public void testGetBuiltInType_Map() {
    // Ensure we have a Map.
    ObjectType mapType = registry.getNativeObjectType(JSTypeNative.MAP_TYPE);
    assertType(registry.getGlobalType("Map")).isEqualTo(mapType);

    // Ensure it implements ReadonlyMap.
    ObjectType readonlyMapType = registry.getNativeObjectType(JSTypeNative.READONLY_MAP_TYPE);
    assertThat(mapType.getCtorImplementedInterfaces()).hasSize(1);
    assertThat(ImmutableList.copyOf(mapType.getCtorImplementedInterfaces()).get(0).getRawType())
        .isEqualTo(readonlyMapType);

    // Ensure it takes one optional parameter of the appropriate type.
    ImmutableList<FunctionType.Parameter> paramList = mapType.getConstructor().getParameters();
    assertThat(paramList).hasSize(1);
    assertThat(paramList.get(0).isOptional()).isTrue();
    assertThat(paramList.get(0).getJSType().toString())
        .isEqualTo("(Array<Array<(K|V)>>|Iterable<Array<(K|V)>,?,?>|null|undefined)");
  }

  /** Returns a scope that overrides a few methods from {@link AbstractStaticScope} */
  private StaticTypedScope createStaticTypedScope(
      Node root,
      @Nullable StaticTypedScope parentScope,
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

  private static StaticTypedScope createScopeWithTypeOfThis(
      StaticTypedScope parentScope, Supplier<@Nullable JSType> typeOfThis) {
    return new AbstractStaticScope() {
      @Override
      public Node getRootNode() {
        return IR.block();
      }

      @Override
      public StaticTypedScope getParentScope() {
        return parentScope;
      }

      @Override
      public @Nullable StaticTypedSlot getSlot(String name) {
        return null;
      }

      @Override
      public @Nullable JSType getTypeOfThis() {
        return typeOfThis.get();
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

  @Test
  public void testCreateTypeFromCommentNode_internedBuiltinTypesAndTemplates_closedResolver() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    // Builtin generic specializations (non-null via BANG, nullable via bare STRINGLIT/QMARK,
    // optional).
    JSType nonNullArrayStr =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "a.js", globalScope);
    JSType nullableArrayStr =
        closedRegistry.createTypeFromCommentNode(
            templatizedCommentNode("Array", IR.string("string")), "b.js", globalScope);
    JSType qmarkArrayStr =
        closedRegistry.createTypeFromCommentNode(
            new Node(Token.QMARK, templatizedCommentNode("Array", IR.string("string"))),
            "b.js",
            globalScope);
    JSType optArrayStr =
        closedRegistry.createTypeFromCommentNode(
            new Node(Token.EQUALS, nullableArrayStrNode()), "b.js", globalScope);
    JSType nonNullArrayUnknown =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", new Node(Token.QMARK))), "c.js", globalScope);
    JSType arrayUnknown =
        closedRegistry.createTypeFromCommentNode(
            templatizedCommentNode("Array", new Node(Token.QMARK)), "c.js", globalScope);

    assertType(nonNullArrayStr)
        .isEqualTo(
            closedRegistry.createTemplatizedType(
                closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
                closedRegistry.getNativeType(STRING_TYPE)));
    assertThat(nonNullArrayStr.toString()).isEqualTo("Array<string>");
    assertThat(nonNullArrayStr.toAnnotationString(JSType.Nullability.EXPLICIT))
        .isEqualTo("!Array<string>");
    assertType(nullableArrayStr).isEqualTo(closedRegistry.createNullableType(nonNullArrayStr));
    assertType(qmarkArrayStr).isEqualTo(nullableArrayStr);
    assertType(optArrayStr).isEqualTo(closedRegistry.createOptionalType(nullableArrayStr));
    assertType(nonNullArrayUnknown)
        .isEqualTo(
            closedRegistry.createTemplatizedType(
                closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
                closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE)));
    assertType(nonNullArrayUnknown).isNotEqualTo(nonNullArrayStr);
    assertThat(nonNullArrayUnknown.toString()).isEqualTo("Array<?>");
    assertThat(arrayUnknown.toString()).isEqualTo("(Array<?>|null)");

    // Repeated evaluation yields structurally equal types with identical template arguments.
    JSType nonNullArrayStr2 =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "d.js", null);
    assertType(nonNullArrayStr2).isEqualTo(nonNullArrayStr);
    assertThat(nonNullArrayStr2.isResolved()).isTrue();
    assertThat(nonNullArrayStr2.getTemplateTypeMap().getTemplateValues())
        .containsExactlyElementsIn(nonNullArrayStr.getTemplateTypeMap().getTemplateValues())
        .inOrder();

    // ReadonlyArray, Promise, IThenable, IIterableResult, ReadonlyMap, Map, and unions.
    JSType readonlyArrNum =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("ReadonlyArray", IR.string("number"))),
            "a.js",
            globalScope);
    assertThat(readonlyArrNum.toString()).isEqualTo("ReadonlyArray<number>");

    JSType promiseStr =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Promise", IR.string("string"))), "a.js", globalScope);
    assertThat(promiseStr.toString()).isEqualTo("Promise<string>");

    JSType ithenableBool =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("IThenable", IR.string("boolean"))),
            "a.js",
            globalScope);
    assertThat(ithenableBool.toString()).isEqualTo("IThenable<boolean>");

    JSType iterResultNum =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("IIterableResult", IR.string("number"))),
            "a.js",
            globalScope);
    assertThat(iterResultNum.toString()).isEqualTo("IIterableResult<number>");

    JSType readonlyMapStrNum =
        closedRegistry.createTypeFromCommentNode(
            bangNode(
                templatizedCommentNode("ReadonlyMap", IR.string("string"), IR.string("number"))),
            "a.js",
            globalScope);
    assertThat(readonlyMapStrNum.toString()).isEqualTo("ReadonlyMap<string,number>");

    JSType nestedMap =
        closedRegistry.createTypeFromCommentNode(
            bangNode(
                templatizedCommentNode(
                    "Map",
                    IR.string("string"),
                    bangNode(templatizedCommentNode("Array", IR.string("string"))))),
            "a.js",
            globalScope);
    assertThat(nestedMap.toString()).isEqualTo("Map<string,Array<string>>");

    Node unionNode = new Node(Token.PIPE);
    unionNode.addChildToBack(IR.string("string"));
    unionNode.addChildToBack(IR.string("number"));
    unionNode.addChildToBack(IR.string("boolean"));
    unionNode.addChildToBack(IR.string("bigint"));
    unionNode.addChildToBack(IR.string("null"));
    unionNode.addChildToBack(IR.string("undefined"));
    unionNode.addChildToBack(IR.string("symbol"));
    JSType bigUnion = closedRegistry.createTypeFromCommentNode(unionNode, "a.js", globalScope);
    assertThat(bigUnion.toString())
        .isEqualTo("(bigint|boolean|null|number|string|symbol|undefined)");

    assertType(closedRegistry.createTypeFromCommentNode(new Node(Token.STAR), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(ALL_TYPE));
    assertType(closedRegistry.createTypeFromCommentNode(new Node(Token.QMARK), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    assertType(closedRegistry.createTypeFromCommentNode(new Node(Token.EMPTY), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    assertType(closedRegistry.createTypeFromCommentNode(IR.string("null"), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(NULL_TYPE));
    // !null removes null from the type, yielding bottom (None).
    assertType(
            closedRegistry.createTypeFromCommentNode(
                bangNode(IR.string("null")), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(JSTypeNative.NO_TYPE));
    assertType(
            closedRegistry.createTypeFromCommentNode(
                new Node(Token.QMARK, bangNode(IR.string("string"))), "a.js", globalScope))
        .isEqualTo(closedRegistry.createNullableType(closedRegistry.getNativeType(STRING_TYPE)));
    assertType(
            closedRegistry.createTypeFromCommentNode(
                bangNode(new Node(Token.QMARK, IR.string("string"))), "a.js", globalScope))
        .isEqualTo(closedRegistry.getNativeType(STRING_TYPE));

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_templateScopeShadowing_doesNotLeakCachedBuiltins() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    // Warm builtin Array evaluation first.
    JSType builtinArrayStr =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "a.js", globalScope);
    assertThat(builtinArrayStr.isTemplatizedType()).isTrue();

    // Shadow "Array" via @template in SyntheticTemplateScope.
    TemplateType templateArray = new TemplateType(closedRegistry, "Array");
    StaticTypedScope templateScope =
        closedRegistry.createScopeWithTemplates(globalScope, ImmutableList.of(templateArray));

    JSType shadowedBare =
        closedRegistry.createTypeFromCommentNode(IR.string("Array"), "a.js", templateScope);
    JSType shadowedWithArgs =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "a.js", templateScope);
    assertType(shadowedBare).isEqualTo(templateArray);
    assertType(shadowedWithArgs).isEqualTo(templateArray);

    // Shadow "Promise" via typeOfThis template key map.
    TemplateType templatePromise = new TemplateType(closedRegistry, "Promise");
    ObjectType ownerThis;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      ownerThis =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("PromiseOwner")
              .withTemplateKeys(templatePromise)
              .build()
              .getInstanceType();
    }
    StaticTypedScope thisScope = createScopeWithTypeOfThis(globalScope, () -> ownerThis);
    JSType shadowedThisPromise =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Promise", IR.string("string"))), "a.js", thisScope);
    assertType(shadowedThisPromise).isEqualTo(templatePromise);

    // Shadowing must not leak back to clean global scope.
    JSType cleanAgain =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "a.js", globalScope);
    assertType(cleanAgain).isEqualTo(builtinArrayStr);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_localScopeAndNonNullableShadowing() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    JSType globalPromise =
        closedRegistry.createTypeFromCommentNode(
            templatizedCommentNode("Promise", IR.string("string")), "a.js", globalScope);
    assertThat(globalPromise.toString()).isEqualTo("(Promise<string>|null)");

    // Local slot shadowing built-in Array must produce NamedType, not builtin Array.
    Map<String, StaticTypedSlot> slots = new HashMap<>();
    StaticTypedScope localScope =
        createStaticTypedScope(IR.block(), globalScope, slots, ImmutableSet.of());
    slots.put(
        "Array",
        new SimpleSlot("Array", closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE), true) {
          @Override
          public StaticTypedScope getScope() {
            return localScope;
          }
        });
    reporter.expectAllWarnings(
        "Bad type annotation. Unknown type Array\n"
            + "It's possible that 'Array' refers to a value, not a type.");
    JSType shadowedLocal =
        closedRegistry.createTypeFromCommentNode(
            bangNode(templatizedCommentNode("Array", IR.string("string"))), "local.js", localScope);
    assertThat(shadowedLocal).isInstanceOf(NamedType.class);

    // Registering a non-nullable name must change subsequent un-banged evaluations.
    closedRegistry.identifyNonNullableName(null, "Promise");
    JSType nonNullablePromise =
        closedRegistry.createTypeFromCommentNode(
            templatizedCommentNode("Promise", IR.string("string")), "b.js", globalScope);
    assertThat(nonNullablePromise.toString()).isEqualTo("Promise<string>");

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_repeatedTooManyTemplateParametersEmitsAllWarnings() {
    TestErrorReporter warningReporter = new TestErrorReporter();
    String expected = "Too many template parameters\nFound 2, required at most 1";
    warningReporter.expectAllWarnings(expected, expected);
    JSTypeRegistry warnRegistry = new JSTypeRegistry(warningReporter);

    Node firstExtra = IR.string("number");
    firstExtra.setLinenoCharno(10, 20);
    Node firstArray = IR.string("Array");
    firstArray.addChildToFront(new Node(Token.BLOCK, IR.string("string"), firstExtra));

    Node secondExtra = IR.string("number");
    secondExtra.setLinenoCharno(25, 4);
    Node secondArray = IR.string("Array");
    secondArray.addChildToFront(new Node(Token.BLOCK, IR.string("string"), secondExtra));

    JSType t1 = warnRegistry.createTypeFromCommentNode(bangNode(firstArray), "warn.js", null);
    JSType t2 = warnRegistry.createTypeFromCommentNode(bangNode(secondArray), "warn.js", null);

    assertType(t1).isEqualTo(t2);
    assertThat(t1.toString()).isEqualTo("Array<string>");
    warningReporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_recordTypePreservesDistinctPropertyNodes() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    Node propNode1 = IR.string("a");
    propNode1.setLinenoCharno(10, 3);
    Node recNode1 =
        new Node(
            Token.LC, new Node(Token.LB, new Node(Token.COLON, propNode1, IR.string("string"))));

    Node propNode2 = IR.string("a");
    propNode2.setLinenoCharno(20, 3);
    Node recNode2 =
        new Node(
            Token.LC, new Node(Token.LB, new Node(Token.COLON, propNode2, IR.string("string"))));

    JSType r1 = closedRegistry.createTypeFromCommentNode(recNode1, "rec.js", null);
    JSType r2 = closedRegistry.createTypeFromCommentNode(recNode2, "rec.js", null);

    assertType(r1).isEqualTo(r2);
    assertThat(r1.toMaybeRecordType().getPropertyNode("a")).isSameInstanceAs(propNode1);
    assertThat(r2.toMaybeRecordType().getPropertyNode("a")).isSameInstanceAs(propNode2);
    assertThat(r2.toMaybeRecordType().getPropertyNode("a")).isNotSameInstanceAs(propNode1);
    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_unionPreservesInsertionOrder() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    JSType voidType = closedRegistry.getNativeType(JSTypeNative.VOID_TYPE);
    JSType nullType = closedRegistry.getNativeType(JSTypeNative.NULL_TYPE);
    JSType stringType = closedRegistry.getNativeType(STRING_TYPE);
    JSType numberType = closedRegistry.getNativeType(NUMBER_TYPE);
    JSType booleanType = closedRegistry.getNativeType(BOOLEAN_TYPE);
    JSType bigIntType = closedRegistry.getNativeType(JSTypeNative.BIGINT_TYPE);

    // 2-way, 3-way, and 4-way permutations preserve exact alternate insertion order.
    assertOrderedUnionPair(
        closedRegistry,
        () -> pipeNode(new Node(Token.VOID), IR.string("string")),
        () -> pipeNode(IR.string("string"), new Node(Token.VOID)),
        ImmutableList.of(voidType, stringType),
        ImmutableList.of(stringType, voidType));
    assertOrderedUnionPair(
        closedRegistry,
        () -> pipeNode(IR.string("null"), new Node(Token.VOID)),
        () -> pipeNode(new Node(Token.VOID), IR.string("null")),
        ImmutableList.of(nullType, voidType),
        ImmutableList.of(voidType, nullType));
    assertThat(evalComment(closedRegistry, pipeNode(IR.string("null"), new Node(Token.VOID))))
        .isSameInstanceAs(closedRegistry.getNativeType(NULL_VOID));

    assertOrderedUnionPair(
        closedRegistry,
        () -> pipeStrings("string", "number", "boolean"),
        () -> pipeStrings("boolean", "number", "string"),
        ImmutableList.of(stringType, numberType, booleanType),
        ImmutableList.of(booleanType, numberType, stringType));
    assertOrderedUnionPair(
        closedRegistry,
        () -> pipeStrings("string", "number", "boolean", "bigint"),
        () -> pipeStrings("bigint", "boolean", "number", "string"),
        ImmutableList.of(stringType, numberType, booleanType, bigIntType),
        ImmutableList.of(bigIntType, booleanType, numberType, stringType));

    // Non-adjacent duplicate templatized types match UnionType.Builder Case 7a exact order.
    Node case7aNode =
        pipeNode(
            bangGeneric("Array", "string"),
            bangGeneric("Array", "number"),
            IR.string("string"),
            bangGeneric("Array", "string"));
    JSType arrayUnknown =
        closedRegistry.createTemplatizedType(
            closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
            closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    JSType case7aType = evalComment(closedRegistry, case7aNode);
    assertThat(case7aType.toMaybeUnionType().getAlternates())
        .containsExactly(stringType, arrayUnknown)
        .inOrder();
    assertThat(evalComment(closedRegistry, case7aNode)).isSameInstanceAs(case7aType);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  private static void assertOrderedUnionPair(
      JSTypeRegistry registry,
      Supplier<Node> forwardNode,
      Supplier<Node> reverseNode,
      ImmutableList<JSType> expectedForwardAlts,
      ImmutableList<JSType> expectedReverseAlts) {
    JSType forward = evalComment(registry, forwardNode.get());
    JSType reverse = evalComment(registry, reverseNode.get());
    assertThat(forward.toMaybeUnionType().getAlternates())
        .containsExactlyElementsIn(expectedForwardAlts)
        .inOrder();
    assertThat(reverse.toMaybeUnionType().getAlternates())
        .containsExactlyElementsIn(expectedReverseAlts)
        .inOrder();
    assertThat(reverse).isNotSameInstanceAs(forward);
    assertThat(evalComment(registry, forwardNode.get())).isSameInstanceAs(forward);
    assertThat(evalComment(registry, reverseNode.get())).isSameInstanceAs(reverse);
  }

  @Test
  public void testCreateTypeFromCommentNode_openEpochDefersResolutionUntilClose() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    // 1. Pre-open CLOSED epoch evaluates built-in generics and unions in resolved state.
    JSType preOpenArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
    JSType preOpenUnion = evalComment(closedRegistry, pipeStrings("string", "number"));
    JSType preOpenMap = evalComment(closedRegistry, bangGeneric("Map", "string", "number"));
    assertThat(preOpenArrayStr.isResolved()).isTrue();
    assertThat(preOpenUnion.isResolved()).isTrue();
    assertThat(preOpenMap.isResolved()).isTrue();
    assertThat(evalComment(closedRegistry, bangGeneric("Array", "string")))
        .isSameInstanceAs(preOpenArrayStr);
    assertThat(evalComment(closedRegistry, pipeStrings("string", "number")))
        .isSameInstanceAs(preOpenUnion);
    assertThat(evalComment(closedRegistry, bangGeneric("Map", "string", "number")))
        .isSameInstanceAs(preOpenMap);

    // 2. Inside openForDefinition(), newly constructed types remain unresolved until Closer.close()
    // and are distinct instances from pre-open CLOSED types.
    JSType openArrayStr;
    JSType openUnion;
    JSType openMap;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      openArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
      openUnion = evalComment(closedRegistry, pipeStrings("string", "number"));
      openMap = evalComment(closedRegistry, bangGeneric("Map", "string", "number"));

      assertThat(openArrayStr.isResolved()).isFalse();
      assertThat(openUnion.isResolved()).isFalse();
      assertThat(openMap.isResolved()).isFalse();
      assertThat(openArrayStr).isNotSameInstanceAs(preOpenArrayStr);
      assertThat(openUnion).isNotSameInstanceAs(preOpenUnion);
      assertThat(openMap).isNotSameInstanceAs(preOpenMap);

      assertThat(evalComment(closedRegistry, bangGeneric("Array", "string")))
          .isSameInstanceAs(openArrayStr);
      assertThat(evalComment(closedRegistry, pipeStrings("string", "number")))
          .isSameInstanceAs(openUnion);
      assertThat(evalComment(closedRegistry, bangGeneric("Map", "string", "number")))
          .isSameInstanceAs(openMap);
    }

    // 3. Completing Closer.close() resolves the OPEN types, and subsequent CLOSED evaluations
    // construct resolved instances distinct from the OPEN-epoch instances.
    assertThat(openArrayStr.isResolved()).isTrue();
    assertThat(openUnion.isResolved()).isTrue();
    assertThat(openMap.isResolved()).isTrue();
    JSType postCloseArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
    JSType postCloseUnion = evalComment(closedRegistry, pipeStrings("string", "number"));
    JSType postCloseMap = evalComment(closedRegistry, bangGeneric("Map", "string", "number"));

    assertThat(postCloseArrayStr.isResolved()).isTrue();
    assertThat(postCloseArrayStr).isNotSameInstanceAs(openArrayStr);
    assertThat(postCloseArrayStr).isNotSameInstanceAs(preOpenArrayStr);
    assertThat(postCloseUnion).isNotSameInstanceAs(openUnion);
    assertThat(postCloseUnion).isNotSameInstanceAs(preOpenUnion);
    assertThat(postCloseMap).isNotSameInstanceAs(openMap);
    assertThat(postCloseMap).isNotSameInstanceAs(preOpenMap);

    assertThat(evalComment(closedRegistry, bangGeneric("Array", "string")))
        .isSameInstanceAs(postCloseArrayStr);
    assertThat(evalComment(closedRegistry, pipeStrings("string", "number")))
        .isSameInstanceAs(postCloseUnion);
    assertThat(evalComment(closedRegistry, bangGeneric("Map", "string", "number")))
        .isSameInstanceAs(postCloseMap);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_scopeShadowingOverridesBuiltinsWithoutLeaking() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    // Define a distinct nominal type to shadow built-in Array in a local type scope.
    ObjectType localNominalArray;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      localNominalArray =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("LocalArrayClass")
              .withTemplateKeys(new TemplateType(closedRegistry, "E"))
              .build()
              .getInstanceType();
    }

    JSType globalArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"), globalScope);
    assertThat(evalComment(closedRegistry, bangGeneric("Array", "string"), globalScope))
        .isSameInstanceAs(globalArrayStr);

    // Local nominal type declaration shadowing Array resolves to the local nominal type.
    StaticTypedScope localTypeScope =
        createStaticTypedScope(IR.block(), globalScope, ImmutableMap.of(), ImmutableSet.of("Array"));
    closedRegistry.declareTypeForExactScope(localTypeScope, "Array", localNominalArray);
    JSType fromLocalTypeScope =
        evalComment(closedRegistry, bangGeneric("Array", "string"), localTypeScope);
    assertThat(fromLocalTypeScope.toString()).isEqualTo("LocalArrayClass<string>");
    assertThat(fromLocalTypeScope.toMaybeTemplatizedType().getReferencedType())
        .isSameInstanceAs(localNominalArray);
    assertThat(fromLocalTypeScope).isNotSameInstanceAs(globalArrayStr);

    // Clean globalScope continues resolving to built-in types without contamination.
    assertThat(evalComment(closedRegistry, bangGeneric("Array", "string"), globalScope))
        .isSameInstanceAs(globalArrayStr);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_excessTemplateArgumentsEmitWarningsAndTruncateType() {
    TestErrorReporter reporter = new TestErrorReporter();
    reporter.expectAllWarnings(
        "Too many template parameters\nFound 2, required at most 1",
        "Too many template parameters\nFound 2, required at most 1",
        "Too many template parameters\nFound 3, required at most 2");
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    JSType canonicalArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
    JSType canonicalMapStrNum = evalComment(closedRegistry, bangGeneric("Map", "string", "number"));

    Node extraArg1 = IR.string("number");
    extraArg1.setLinenoCharno(10, 15);
    Node extraArg2 = IR.string("boolean");
    extraArg2.setLinenoCharno(20, 15);
    Node extraMapArg = IR.string("boolean");
    extraMapArg.setLinenoCharno(30, 22);

    JSType extraArray1 =
        evalComment(
            closedRegistry, bangNode(templatizedCommentNode("Array", IR.string("string"), extraArg1)));
    JSType extraArray2 =
        evalComment(
            closedRegistry, bangNode(templatizedCommentNode("Array", IR.string("string"), extraArg2)));
    JSType extraMap =
        evalComment(
            closedRegistry,
            bangNode(
                templatizedCommentNode("Map", IR.string("string"), IR.string("number"), extraMapArg)));

    assertThat(extraArray1).isSameInstanceAs(canonicalArrayStr);
    assertThat(extraArray2).isSameInstanceAs(canonicalArrayStr);
    assertThat(extraMap).isSameInstanceAs(canonicalMapStrNum);
    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void
      testCreateTypeFromCommentNode_optionalNullableApisAndDuplicateUnionsPreserveOrder() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    JSType stringType = closedRegistry.getNativeType(STRING_TYPE);
    JSType numberType = closedRegistry.getNativeType(NUMBER_TYPE);
    JSType voidType = closedRegistry.getNativeType(JSTypeNative.VOID_TYPE);
    JSType nullType = closedRegistry.getNativeType(NULL_TYPE);
    JSType arrayOfString =
        closedRegistry.createTemplatizedType(
            closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), stringType);

    // Direct createOptionalType, createNullableType, and createOptionalNullableType match
    // equivalent JSDoc comment evaluations and alternate ordering.
    JSType optStr1 = closedRegistry.createOptionalType(stringType);
    JSType optStr2 = closedRegistry.createOptionalType(stringType);
    JSType optStrComment = evalComment(closedRegistry, new Node(Token.EQUALS, IR.string("string")));
    assertThat(optStr2).isSameInstanceAs(optStr1);
    assertThat(optStrComment).isSameInstanceAs(optStr1);
    assertThat(optStrComment.toMaybeUnionType().getAlternates())
        .containsExactly(stringType, voidType)
        .inOrder();

    JSType nullableArr1 = closedRegistry.createNullableType(arrayOfString);
    JSType nullableArr2 = closedRegistry.createNullableType(arrayOfString);
    JSType nullableArrComment = evalComment(closedRegistry, nullableArrayStrNode());
    assertThat(nullableArr2).isSameInstanceAs(nullableArr1);
    assertThat(nullableArrComment).isSameInstanceAs(nullableArr1);

    JSType optNullStr1 = closedRegistry.createOptionalNullableType(stringType);
    JSType optNullStr2 = closedRegistry.createOptionalNullableType(stringType);
    JSType optNullStrComment =
        evalComment(
            closedRegistry, pipeNode(IR.string("string"), new Node(Token.VOID), IR.string("null")));
    assertThat(optNullStr2).isSameInstanceAs(optNullStr1);
    assertThat(optNullStrComment).isSameInstanceAs(optNullStr1);
    assertThat(optNullStrComment.toMaybeUnionType().getAlternates())
        .containsExactly(stringType, voidType, nullType)
        .inOrder();

    JSType optNullArr1 = closedRegistry.createOptionalNullableType(arrayOfString);
    JSType optNullArr2 = closedRegistry.createOptionalNullableType(arrayOfString);
    assertThat(optNullArr2).isSameInstanceAs(optNullArr1);

    // 3-way unions with duplicate alternates collapse to exact 2-way insertion order (or the
    // single alternate when all 3 are identical).
    JSType strOrNum = evalComment(closedRegistry, pipeStrings("string", "number"));
    assertThat(closedRegistry.createUnionType(stringType, numberType)).isSameInstanceAs(strOrNum);
    assertThat(strOrNum.toMaybeUnionType().getAlternates())
        .containsExactly(stringType, numberType)
        .inOrder();

    for (Node duplicateUnionNode :
        ImmutableList.of(
            pipeStrings("string", "string", "number"),
            pipeStrings("string", "number", "number"),
            pipeStrings("string", "number", "string"))) {
      JSType collapsed = evalComment(closedRegistry, duplicateUnionNode);
      assertThat(collapsed).isSameInstanceAs(strOrNum);
      assertThat(collapsed.toMaybeUnionType().getAlternates())
          .containsExactly(stringType, numberType)
          .inOrder();
    }

    assertThat(evalComment(closedRegistry, pipeStrings("string", "string", "string")))
        .isSameInstanceAs(stringType);
    assertThat(closedRegistry.createUnionType(stringType, numberType, numberType))
        .isSameInstanceAs(strOrNum);
    assertThat(closedRegistry.createUnionType(stringType, numberType, stringType))
        .isSameInstanceAs(strOrNum);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_multiParameterGenericsPreserveArgumentOrder() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    ObjectType mapBase = closedRegistry.getNativeObjectType(JSTypeNative.MAP_TYPE);
    ObjectType readonlyMapBase = closedRegistry.getNativeObjectType(JSTypeNative.READONLY_MAP_TYPE);
    ObjectType arrayBase = closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);
    JSType stringType = closedRegistry.getNativeType(STRING_TYPE);
    JSType numberType = closedRegistry.getNativeType(NUMBER_TYPE);
    JSType unknownType = closedRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    JSType mapStrNumFromComment1 =
        evalComment(closedRegistry, bangGeneric("Map", "string", "number"));
    JSType mapStrNumFromComment2 =
        evalComment(closedRegistry, bangGeneric("Map", "string", "number"));
    JSType mapStrNumFromApi = closedRegistry.createTemplatizedType(mapBase, stringType, numberType);
    JSType mapNumStrFromComment =
        evalComment(closedRegistry, bangGeneric("Map", "number", "string"));

    assertThat(mapStrNumFromComment2).isSameInstanceAs(mapStrNumFromComment1);
    assertThat(mapStrNumFromApi).isSameInstanceAs(mapStrNumFromComment1);
    assertType(mapNumStrFromComment).isNotEqualTo(mapStrNumFromComment1);
    assertThat(mapNumStrFromComment).isNotSameInstanceAs(mapStrNumFromComment1);

    // Nested 2-parameter ReadonlyMap<string, !Array<number>> and wildcard Map<?, ?>.
    JSType arrayOfNumber = closedRegistry.createTemplatizedType(arrayBase, numberType);
    JSType readonlyMapNestedComment =
        evalComment(
            closedRegistry,
            bangNode(
                templatizedCommentNode(
                    "ReadonlyMap", IR.string("string"), bangGeneric("Array", "number"))));
    JSType readonlyMapNestedApi =
        closedRegistry.createTemplatizedType(readonlyMapBase, stringType, arrayOfNumber);
    assertThat(readonlyMapNestedApi).isSameInstanceAs(readonlyMapNestedComment);

    JSType mapWildcardComment1 =
        evalComment(
            closedRegistry,
            bangNode(templatizedCommentNode("Map", new Node(Token.QMARK), new Node(Token.QMARK))));
    JSType mapWildcardComment2 =
        evalComment(
            closedRegistry,
            bangNode(templatizedCommentNode("Map", new Node(Token.QMARK), new Node(Token.QMARK))));
    JSType mapWildcardApi =
        closedRegistry.createTemplatizedType(mapBase, unknownType, unknownType);
    assertThat(mapWildcardComment2).isSameInstanceAs(mapWildcardComment1);
    assertThat(mapWildcardApi).isSameInstanceAs(mapWildcardComment1);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_scalarNativeUnionsMatchCanonicalAlternates() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    JSType numberType = closedRegistry.getNativeType(NUMBER_TYPE);
    JSType stringType = closedRegistry.getNativeType(STRING_TYPE);
    JSType booleanType = closedRegistry.getNativeType(BOOLEAN_TYPE);
    JSType symbolType = closedRegistry.getNativeType(JSTypeNative.SYMBOL_TYPE);
    JSType bigIntType = closedRegistry.getNativeType(JSTypeNative.BIGINT_TYPE);
    JSType nullType = closedRegistry.getNativeType(NULL_TYPE);
    JSType voidType = closedRegistry.getNativeType(JSTypeNative.VOID_TYPE);

    assertScalarNativeUnion(
        closedRegistry,
        NULL_VOID,
        pipeNode(IR.string("null"), new Node(Token.VOID)),
        pipeNode(new Node(Token.VOID), IR.string("null")),
        nullType,
        voidType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.NUMBER_STRING,
        pipeStrings("number", "string"),
        pipeStrings("string", "number"),
        numberType,
        stringType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.NUMBER_STRING_BOOLEAN,
        pipeStrings("number", "string", "boolean"),
        pipeStrings("string", "number", "boolean"),
        numberType,
        stringType,
        booleanType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.VALUE_TYPES,
        pipeStrings("number", "string", "boolean", "symbol"),
        pipeStrings("string", "number", "boolean", "symbol"),
        numberType,
        stringType,
        booleanType,
        symbolType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.NUMBER_SYMBOL,
        pipeStrings("number", "symbol"),
        pipeStrings("symbol", "number"),
        numberType,
        symbolType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.STRING_SYMBOL,
        pipeStrings("string", "symbol"),
        pipeStrings("symbol", "string"),
        stringType,
        symbolType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.BIGINT_NUMBER,
        pipeStrings("bigint", "number"),
        pipeStrings("number", "bigint"),
        bigIntType,
        numberType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.BIGINT_NUMBER_STRING,
        pipeStrings("bigint", "number", "string"),
        pipeStrings("string", "number", "bigint"),
        bigIntType,
        numberType,
        stringType);
    assertScalarNativeUnion(
        closedRegistry,
        JSTypeNative.NUMBER_STRING_SYMBOL,
        pipeStrings("number", "string", "symbol"),
        pipeStrings("string", "number", "symbol"),
        numberType,
        stringType,
        symbolType);

    // In OPEN epochs, native-ordered unions construct unresolved OPEN-epoch UnionTypes queued on
    // JSTypeResolver rather than returning pre-resolved native singletons.
    JSType openNumberString;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      openNumberString = evalComment(closedRegistry, pipeStrings("number", "string"));
      assertThat(openNumberString.isResolved()).isFalse();
      assertThat(openNumberString)
          .isNotSameInstanceAs(closedRegistry.getNativeType(JSTypeNative.NUMBER_STRING));
      assertThat(closedRegistry.createUnionType(numberType, stringType))
          .isSameInstanceAs(openNumberString);
    }
    assertThat(openNumberString.isResolved()).isTrue();
    assertThat(evalComment(closedRegistry, pipeStrings("number", "string")))
        .isSameInstanceAs(closedRegistry.getNativeType(JSTypeNative.NUMBER_STRING));

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  private static void assertScalarNativeUnion(
      JSTypeRegistry registry,
      JSTypeNative nativeId,
      Node canonicalComment,
      Node reorderedComment,
      JSType... canonicalAlternates) {
    JSType expectedNative = registry.getNativeType(nativeId);
    ImmutableList<JSType> expectedAlternates = expectedNative.toMaybeUnionType().getAlternates();
    JSType fromCanonicalComment = evalComment(registry, canonicalComment);
    JSType fromCanonicalApi = registry.createUnionType(canonicalAlternates);
    JSType fromReorderedComment = evalComment(registry, reorderedComment);
    assertThat(fromCanonicalComment).isSameInstanceAs(expectedNative);
    assertThat(fromCanonicalComment.toMaybeUnionType().getAlternates())
        .containsExactlyElementsIn(expectedAlternates)
        .inOrder();
    assertThat(fromCanonicalApi).isSameInstanceAs(expectedNative);
    assertThat(fromCanonicalApi.toMaybeUnionType().getAlternates())
        .containsExactlyElementsIn(expectedAlternates)
        .inOrder();
    assertType(fromReorderedComment).isEqualTo(expectedNative);
    assertThat(fromReorderedComment).isNotSameInstanceAs(expectedNative);
  }

  @Test
  public void testCreateTypeFromCommentNode_bangRestrictionStripsNullAndUndefinedFromUnions() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);

    JSType stringType = closedRegistry.getNativeType(STRING_TYPE);
    JSType numberType = closedRegistry.getNativeType(NUMBER_TYPE);

    JSType strOrNum = evalComment(closedRegistry, pipeStrings("string", "number"));
    JSType bangStrNumNull =
        evalComment(closedRegistry, bangNode(pipeStrings("string", "number", "null")));
    JSType bangStrNumVoid =
        evalComment(
            closedRegistry,
            bangNode(pipeNode(IR.string("string"), IR.string("number"), new Node(Token.VOID))));
    JSType bangNullStrVoidNum =
        evalComment(
            closedRegistry,
            bangNode(
                pipeNode(
                    IR.string("null"),
                    IR.string("string"),
                    new Node(Token.VOID),
                    IR.string("number"))));

    assertThat(bangStrNumNull).isSameInstanceAs(strOrNum);
    assertThat(bangStrNumVoid).isSameInstanceAs(strOrNum);
    assertThat(bangNullStrVoidNum).isSameInstanceAs(strOrNum);
    assertThat(bangNullStrVoidNum.toMaybeUnionType().getAlternates())
        .containsExactly(stringType, numberType)
        .inOrder();

    // Restricting (number|string|null) equals NUMBER_STRING, and !(null|undefined) is NO_TYPE.
    assertThat(evalComment(closedRegistry, bangNode(pipeStrings("number", "string", "null"))))
        .isSameInstanceAs(closedRegistry.getNativeType(JSTypeNative.NUMBER_STRING));
    assertThat(
            evalComment(closedRegistry, bangNode(pipeNode(IR.string("null"), new Node(Token.VOID)))))
        .isSameInstanceAs(closedRegistry.getNativeType(JSTypeNative.NO_TYPE));

    // Nested generics over !-restricted unions match the unrestricted non-null union generic.
    JSType arrayOfStrOrNum =
        evalComment(
            closedRegistry,
            bangNode(templatizedCommentNode("Array", pipeStrings("string", "number"))));
    JSType arrayOfBangStrNumNull =
        evalComment(
            closedRegistry,
            bangNode(
                templatizedCommentNode("Array", bangNode(pipeStrings("string", "number", "null")))));
    assertThat(arrayOfBangStrNumNull).isSameInstanceAs(arrayOfStrOrNum);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_closingPhaseResolvesNewTypesEagerly() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    List<JSType> closingEvaluations = new ArrayList<>();
    AtomicBoolean observedClosingState = new AtomicBoolean(false);

    JSType openArrayStr;
    JSType openUnion;
    JSType forwardAliasToArray;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      openArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
      openUnion = evalComment(closedRegistry, pipeStrings("string", "number"));

      AtomicReference<JSType> queuedAfterProbe = new AtomicReference<>();
      ProxyObjectType unusedProbe =
          new ProxyObjectType(
              closedRegistry, closedRegistry.getNativeObjectType(JSTypeNative.OBJECT_TYPE)) {
            @Override
            JSType resolveInternal(ErrorReporter t) {
              closingEvaluations.add(evalComment(closedRegistry, bangGeneric("Array", "string")));
              closingEvaluations.add(evalComment(closedRegistry, bangGeneric("Array", "string")));
              closingEvaluations.add(evalComment(closedRegistry, pipeStrings("string", "number")));
              closingEvaluations.add(evalComment(closedRegistry, pipeStrings("string", "number")));
              // Verify we are mid-drain inside CLOSING: later queue entries are still unresolved,
              // whereas types synthesized during CLOSING resolve eagerly.
              assertThat(closedRegistry.getResolver().isOpenOrClosed()).isFalse();
              assertThat(queuedAfterProbe.get().isResolved()).isFalse();
              assertThat(closingEvaluations.get(0).isResolved()).isTrue();
              observedClosingState.set(true);
              return super.resolveInternal(t);
            }
          };
      assertThat(unusedProbe.isResolved()).isFalse();

      forwardAliasToArray =
          evalComment(closedRegistry, bangGeneric("AliasToArray", "string"), globalScope);
      queuedAfterProbe.set(forwardAliasToArray);
      closedRegistry.declareType(
          globalScope,
          "AliasToArray",
          closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE));
    }

    assertThat(observedClosingState.get()).isTrue();
    assertThat(closingEvaluations).hasSize(4);
    for (JSType evaluatedDuringClosing : closingEvaluations) {
      assertThat(evaluatedDuringClosing.isResolved()).isTrue();
    }
    assertThat(closingEvaluations.get(0)).isNotSameInstanceAs(openArrayStr);
    assertThat(closingEvaluations.get(0)).isNotSameInstanceAs(closingEvaluations.get(1));
    assertThat(closingEvaluations.get(2)).isNotSameInstanceAs(openUnion);
    assertThat(closingEvaluations.get(2)).isNotSameInstanceAs(closingEvaluations.get(3));
    assertThat(forwardAliasToArray.isResolved()).isTrue();

    JSType postCloseArrayStr = evalComment(closedRegistry, bangGeneric("Array", "string"));
    JSType postCloseUnion = evalComment(closedRegistry, pipeStrings("string", "number"));
    assertThat(postCloseArrayStr).isNotSameInstanceAs(closingEvaluations.get(0));
    assertThat(postCloseArrayStr).isNotSameInstanceAs(closingEvaluations.get(1));
    assertThat(postCloseArrayStr)
        .isNotSameInstanceAs(forwardAliasToArray.toMaybeNamedType().getReferencedType());
    assertThat(postCloseUnion).isNotSameInstanceAs(closingEvaluations.get(2));
    assertThat(postCloseUnion).isNotSameInstanceAs(closingEvaluations.get(3));
    assertThat(evalComment(closedRegistry, bangGeneric("Array", "string")))
        .isSameInstanceAs(postCloseArrayStr);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testCreateTypeFromCommentNode_mutatingTypeOfThisUpdatesTemplateResolution() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    StaticTypedScope globalScope =
        createStaticTypedScope(IR.root(), null, ImmutableMap.of(), ImmutableSet.of());

    TemplateType thisTemplateArray = new TemplateType(closedRegistry, "Array");
    ObjectType arrayThisOwner;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      arrayThisOwner =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("DynamicThisOwner")
              .withTemplateKeys(thisTemplateArray)
              .build()
              .getInstanceType();
    }

    AtomicReference<JSType> currentThisType = new AtomicReference<>();
    StaticTypedScope mutableThisScope =
        createScopeWithTypeOfThis(globalScope, currentThisType::get);

    JSType beforeMutation =
        evalComment(closedRegistry, bangGeneric("Array", "string"), mutableThisScope);
    JSType canonicalArrayStr =
        evalComment(closedRegistry, bangGeneric("Array", "string"), globalScope);
    assertThat(beforeMutation).isSameInstanceAs(canonicalArrayStr);

    // Mutating getTypeOfThis() on the same StaticTypedScope instance switches resolution to the
    // owner's @template Array parameter.
    currentThisType.set(arrayThisOwner);
    JSType duringMutation =
        evalComment(closedRegistry, bangGeneric("Array", "string"), mutableThisScope);
    assertThat(duringMutation).isSameInstanceAs(thisTemplateArray);

    // Restoring getTypeOfThis() to null resumes resolving to the built-in Array<string> type.
    currentThisType.set(null);
    JSType afterRestore =
        evalComment(closedRegistry, bangGeneric("Array", "string"), mutableThisScope);
    assertThat(afterRestore).isSameInstanceAs(canonicalArrayStr);

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  @Test
  public void testBuiltinTypeInterner_deepOpenEpochDagAndGenericSubclassInterning() {
    TestErrorReporter reporter = new TestErrorReporter();
    JSTypeRegistry closedRegistry = new JSTypeRegistry(reporter);
    ObjectType arrayBase = closedRegistry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);
    ObjectType readonlyArrayBase =
        closedRegistry.getNativeObjectType(JSTypeNative.READONLY_ARRAY_TYPE);
    JSType stringType = closedRegistry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType numberType = closedRegistry.getNativeType(JSTypeNative.NUMBER_TYPE);

    TemplateType superParam = new TemplateType(closedRegistry, "S");
    TemplateType subParam = new TemplateType(closedRegistry, "T");
    TemplateType outerParam = new TemplateType(closedRegistry, "W");
    TemplateType capturingParam = new TemplateType(closedRegistry, "U");
    ObjectType subInstance;
    ObjectType capturingSubInstance;
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      FunctionType superCtor =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("GenericSuper")
              .withTemplateKeys(superParam)
              .build();
      ObjectType superBase = superCtor.getInstanceType();

      FunctionType subCtor =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("GenericSub")
              .withTemplateKeys(subParam)
              .build();
      TemplatizedType superWithSubParam =
          closedRegistry.createTemplatizedType(superBase, ImmutableList.of(subParam));
      subCtor.setPrototypeBasedOn(superWithSubParam);
      subInstance = subCtor.getInstanceType();
      subInstance.mergeSupertypeTemplateTypes(superWithSubParam);

      FunctionType capturingCtor =
          FunctionType.builder(closedRegistry)
              .forConstructor()
              .withName("CapturingSub")
              .withTemplateKeys(capturingParam)
              .build();
      TemplatizedType superWithOuterParam =
          closedRegistry.createTemplatizedType(superBase, ImmutableList.of(outerParam));
      capturingCtor.setPrototypeBasedOn(superWithOuterParam);
      capturingSubInstance = capturingCtor.getInstanceType();
      capturingSubInstance.mergeSupertypeTemplateTypes(superWithOuterParam);
    }

    // A depth-16 shared (!Array<U>|!ReadonlyArray<U>|string) DAG (65,536 virtual paths) in an OPEN
    // epoch interns into O(depth) shared instances without unmemoized hasAnyTemplateTypes() walks.
    try (JSTypeResolver.Closer closer = closedRegistry.getResolver().openForDefinition()) {
      JSType current = stringType;
      for (int depth = 0; depth < 16; depth++) {
        TemplatizedType arr = closedRegistry.createTemplatizedType(arrayBase, current);
        TemplatizedType roArr = closedRegistry.createTemplatizedType(readonlyArrayBase, current);
        JSType union1 = closedRegistry.createUnionType(arr, roArr, stringType);
        JSType union2 =
            closedRegistry.createUnionType(
                closedRegistry.createTemplatizedType(arrayBase, current),
                closedRegistry.createTemplatizedType(readonlyArrayBase, current),
                stringType);
        assertThat(union1.isResolved()).isFalse();
        assertThat(union2).isSameInstanceAs(union1);
        current = union1;
      }
    }

    // GenericSub<string> fills superclass binding [S -> string], so it is template-free and
    // eligible as an interned union alternate.
    TemplatizedType subStr = closedRegistry.createTemplatizedType(subInstance, stringType);
    assertThat(subStr.hasAnyTemplateTypes()).isFalse();
    assertThat(closedRegistry.createNullableType(subStr))
        .isSameInstanceAs(closedRegistry.createNullableType(subStr));

    // CapturingSub<string> retains unbound outer template parameter W in [S -> W], so it is not
    // added to internedTypes.
    TemplatizedType capturingStr =
        closedRegistry.createTemplatizedType(capturingSubInstance, stringType);
    assertThat(capturingStr.hasAnyTemplateTypes()).isTrue();
    assertThat(closedRegistry.createUnionType(capturingStr, numberType))
        .isNotSameInstanceAs(closedRegistry.createUnionType(capturingStr, numberType));

    reporter.verifyHasEncounteredAllWarningsAndErrors();
  }

  private static JSType evalComment(JSTypeRegistry registry, Node commentNode) {
    return registry.createTypeFromCommentNode(commentNode, "test.js", /* scope= */ null);
  }

  private static JSType evalComment(
      JSTypeRegistry registry, Node commentNode, @Nullable StaticTypedScope scope) {
    return registry.createTypeFromCommentNode(commentNode, "test.js", scope);
  }

  private static Node pipeStrings(String... names) {
    Node pipe = new Node(Token.PIPE);
    for (String name : names) {
      pipe.addChildToBack(IR.string(name));
    }
    return pipe;
  }

  private static Node bangGeneric(String name, String... scalarArgs) {
    Node[] args = new Node[scalarArgs.length];
    for (int i = 0; i < scalarArgs.length; i++) {
      args[i] = IR.string(scalarArgs[i]);
    }
    return bangNode(templatizedCommentNode(name, args));
  }

  private static Node pipeNode(Node... children) {
    Node pipe = new Node(Token.PIPE);
    for (Node child : children) {
      pipe.addChildToBack(child);
    }
    return pipe;
  }

  private static Node nullableArrayStrNode() {
    return templatizedCommentNode("Array", IR.string("string"));
  }

  private static Node bangNode(Node child) {
    return new Node(Token.BANG, child);
  }

  private static Node templatizedCommentNode(String name, Node... templateArgs) {
    Node node = IR.string(name);
    Node block = new Node(Token.BLOCK);
    for (Node arg : templateArgs) {
      block.addChildToBack(arg);
    }
    node.addChildToFront(block);
    return node;
  }
}
