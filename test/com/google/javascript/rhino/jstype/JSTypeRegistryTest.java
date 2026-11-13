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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.AbstractStaticScope;
import com.google.javascript.rhino.testing.MapBasedScope;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    StaticTypedScope thisScope =
        new AbstractStaticScope() {
          @Override
          public Node getRootNode() {
            return IR.block();
          }

          @Override
          public StaticTypedScope getParentScope() {
            return globalScope;
          }

          @Override
          public StaticTypedSlot getSlot(String name) {
            return null;
          }

          @Override
          public JSType getTypeOfThis() {
            return ownerThis;
          }
        };
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
