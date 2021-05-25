/*
 * Copyright 2021 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.serialization;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;
import static com.google.javascript.jscomp.serialization.TypePointers.untrimOffset;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSTypeReconserializerTest extends CompilerTestCase {

  // individual test cases may override this
  private ImmutableSet<String> typesToForwardDeclare = null;

  private TypePool typePool;
  private StringPool.Builder stringPoolBuilder;

  // Proto fields commonly ignored in tests because hardcoding their values is brittle
  private static final FieldDescriptor OBJECT_UUID =
      ObjectTypeProto.getDescriptor().findFieldByName("uuid");
  private static final FieldDescriptor OBJECT_PROPERTIES =
      ObjectTypeProto.getDescriptor().findFieldByName("own_property");
  private static final FieldDescriptor IS_INVALIDATING =
      ObjectTypeProto.getDescriptor().findFieldByName("is_invalidating");

  private static final FieldDescriptor TYPE_OFFSET =
      TypePointer.getDescriptor().findFieldByName("pool_offset");

  private static final ImmutableList<FieldDescriptor> BRITTLE_TYPE_FIELDS =
      ImmutableList.of(OBJECT_UUID, OBJECT_PROPERTIES);

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    this.typesToForwardDeclare = ImmutableSet.of();
    enableSourceInformationAnnotator();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    this.stringPoolBuilder = StringPool.builder();

    return (externs, root) -> {
      JSTypeReconserializer serializer =
          JSTypeReconserializer.create(
              compiler.getTypeRegistry(),
              new InvalidatingTypes.Builder(compiler.getTypeRegistry())
                  .addAllTypeMismatches(compiler.getTypeMismatches())
                  .build(),
              this.stringPoolBuilder,
              SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS);

      NodeTraversal.traverseRoots(
          compiler,
          new NodeTraversal.AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.getJSType() != null) {
                serializer.serializeType(n.getJSType());
              }
            }
          },
          externs,
          root);

      this.typePool = serializer.generateTypePool();
    };
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    for (String typeName : typesToForwardDeclare) {
      compiler.forwardDeclareType(typeName);
    }
    return compiler;
  }

  @Test
  public void testNativeTypesAreNotSerialized() {
    assertThat(compileToTypes("const /** bigint */ x = 1n;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** boolean */ x = false;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** number */ x = 5;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** string */ x = '';"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** symbol */ x = Symbol('x');"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** null */ x = null;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** undefined */ x = void 0;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** ? */ x = /** @type {?} */ (0);"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
    assertThat(compileToTypes("const /** * */ x = /** @type {?} */ (0);"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
  }

  @Test
  public void testObjectTypeSerializationFormat() {
    List<TypeProto> typePool = compileToTypes("class Foo { m() {} } new Foo().m();");

    assertThat(typePool)
        .ignoringFieldDescriptors(ObjectTypeProto.getDescriptor().findFieldByName("uuid"))
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("(typeof Foo)")
                        .addPrototype(pointerForType("Foo.prototype"))
                        .addInstanceType(pointerForType("Foo"))
                        .setMarkedConstructor(true)
                        .addOwnProperty(findInStringPool("prototype")))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("Foo.prototype")
                        .addOwnProperty(findInStringPool("constructor"))
                        .addOwnProperty(findInStringPool("m")))
                .build(),
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("Foo.prototype.m").setIsInvalidating(true))
                .build());
  }

  @Test
  public void testDisambiguationEdges_pointFromInstanceToPrototype() {
    TypePool typePool = compileToTypePool("class Foo { m() {} } new Foo().m();");

    assertThat(getNonPrimitiveSupertypesFor(typePool, "Foo"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(TypeProto.newBuilder().setObject(namedObjectBuilder("Foo.prototype")).build());
  }

  @Test
  public void testDisambiguationEdges_pointFromInterfaceToPrototype() {
    TypePool typePool =
        compileToTypePool(
            lines(
                "/** @interface */ class IFoo { m() {} }", //
                "let /** !IFoo */ x;"));

    assertThat(getNonPrimitiveSupertypesFor(typePool, "IFoo"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(TypeProto.newBuilder().setObject(namedObjectBuilder("IFoo.prototype")).build());
  }

  @Test
  public void testDisambiguationEdges_pointFromInstanceToInterface() {
    TypePool typePool =
        compileToTypePool(
            lines("/** @interface */ class IFoo {}", "/** @implements {IFoo} */ class Foo {}"));

    assertThat(getNonPrimitiveSupertypesFor(typePool, "Foo"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            ObjectTypeProto.getDescriptor().findFieldByName("prototype"),
            ObjectTypeProto.getDescriptor().findFieldByName("instance_type"))
        .contains(TypeProto.newBuilder().setObject(namedObjectBuilder("IFoo")).build());
  }

  @Test
  public void testDisambiguationEdges_pointFromSubctorToSuperctor() {
    TypePool typePool = compileToTypePool("class Foo {} class Bar extends Foo {}");

    assertThat(getNonPrimitiveSupertypesFor(typePool, "(typeof Bar)"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            ObjectTypeProto.getDescriptor().findFieldByName("prototype"),
            ObjectTypeProto.getDescriptor().findFieldByName("instance_type"))
        .containsExactly(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("(typeof Foo)").setMarkedConstructor(true))
                .build());
  }

  @Test
  public void testDisambiguationEdges_dont_pointFromPrototypeToInstance() {
    TypePool typePool = compileToTypePool("class Foo { m() {} } new Foo().m();");

    assertThat(getNonPrimitiveSupertypesFor(typePool, "Foo.prototype")).isEmpty();
  }

  @Test
  public void testMultipleAnonymousTypesDoesntCrash() {
    compileToTypes("(() => 5); (() => 'str');");
  }

  @Test
  public void testUnion_proxyLikeTypes_dontPreventUnionCollapsing() {
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "/** @enum {boolean|number} */", //
                "const Foo = {};",
                "/** @enum {string|number|!Foo} */", //
                "const Bar = {};",
                "",
                "let /** symbol|!Bar|string */ x;"));

    List<UnionTypeProto> unionsContainingUnions =
        typePool.stream()
            .filter(TypeProto::hasUnion)
            .map(TypeProto::getUnion)
            .filter(
                (u) ->
                    u.getUnionMemberList().stream()
                        .filter((p) -> !isAxiomatic(p))
                        .anyMatch((e) -> typePool.get(trimOffset(e)).hasUnion()))
            .collect(toImmutableList());
    assertThat(unionsContainingUnions).isEmpty();

    assertThat(typePool)
        .contains(
            TypeProto.newBuilder()
                .setUnion(
                    UnionTypeProto.newBuilder()
                        .addUnionMember(pointerForType(PrimitiveType.BOOLEAN_TYPE))
                        .addUnionMember(pointerForType(PrimitiveType.STRING_TYPE))
                        .addUnionMember(pointerForType(PrimitiveType.NUMBER_TYPE))
                        .addUnionMember(pointerForType(PrimitiveType.SYMBOL_TYPE))
                        .build())
                .build());
  }

  @Test
  public void testSerializedInstanceTypeHasClassName() {
    assertThat(compileToTypes("/** @constructor */ function Foo() {} new Foo;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build());
  }

  @Test
  public void testEnumElementSerializesToPrimitive() {
    // Serialize E but treat E.A as a number
    assertThat(compileToTypes("/** @enum {number} */ const E = {A: 0, B: 1}; E.A;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("enum{E}").setPropertiesKeepOriginalName(true))
                .build());
  }

  @Test
  public void testBoxableScalarTypesKeepOriginalName() {
    // Serialize E but treat E.A as a number
    assertThat(compileToTypes("/** @enum {number} */ const E = {A: 0, B: 1}; E.A;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("enum{E}").setPropertiesKeepOriginalName(true))
                .build());
  }

  @Test
  public void marksClosureAssertions() {
    assertThat(
            compileToTypes(
                lines(
                    "/** @closurePrimitive {asserts.truthy} */",
                    "function assert(x) { if (!x) { throw new Error(); }}",
                    "",
                    "/** @closurePrimitive {asserts.fail} */",
                    "function fail() { throw new Error(); }")))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("assert").setClosureAssert(true).setIsInvalidating(true))
                .build(),
            TypeProto.newBuilder()
                .setObject(
                    // ClosurePrimitive.ASSERTS_FAIL is not a removable Closure assertion
                    namedObjectBuilder("fail").setClosureAssert(false).setIsInvalidating(true))
                .build());
  }

  @Test
  public void testSerializeObjectLiteralTypesAsInvalidating() {
    List<TypeProto> typePool = compileToTypes("const /** {x: string} */ obj = {x: ''};");
    assertThat(typePool)
        .ignoringFieldDescriptors(TYPE_OFFSET)
        .ignoringFieldDescriptors(OBJECT_UUID)
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    ObjectTypeProto.newBuilder()
                        .setIsInvalidating(true)
                        .addOwnProperty(findInStringPool("x")))
                .build());
  }

  @Test
  public void serializesTemplateTypesAsUnknown() {
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "/** @interface @template T */",
                "class Foo {",
                // Add in this reference to `T` so that it is seen by the serializer.
                "  constructor() { /** @type {T} */ this.x; }",
                "}"));
    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("(typeof Foo)")
                        .addPrototype(pointerForType("Foo.prototype"))
                        .addInstanceType(pointerForType("Foo")))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo.prototype")).build());
    // Verify no additional objects were serialized to represent the template type: we only have
    // the well-known types and the three TypeProtos tested for above.
    assertThat(typePool).hasSize(nativeObjects().size() + 3);
  }

  @Test
  public void reconciles_differentFunctionsWithSameJspath() {
    assertThat(
            compileToTypes(
                lines(
                    "const ns = {};", //
                    "",
                    "ns.f = x => x;",
                    "ns.f.a = 0;",
                    "",
                    "ns.f = (/** number */ x) => x;",
                    "ns.f.b = 1;",
                    "",
                    "ns.f = (/** string */ x) => x;",
                    "ns.f.c = 2;")))
        .ignoringFieldDescriptors(OBJECT_UUID)
        .ignoringFieldDescriptors(ObjectTypeProto.getDescriptor().findFieldByName("prototype"))
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("ns.f")
                        .setIsInvalidating(true)
                        .addAllOwnProperty(findAllInStringPool("a", "b", "c")))
                .build());
  }

  @Test
  public void discards_differentTemplatizationsOfSameRawType() {
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "/** @interface @template T */",
                "class Foo {}",
                "var /** !Foo<string> */ fooString;",
                "var /** !Foo<number> */ fooNumber;"));

    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("(typeof Foo)")
                        .addPrototype(pointerForType("Foo.prototype"))
                        .addInstanceType(pointerForType("Foo")))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo.prototype")).build());
  }

  @Test
  public void doesntCrashOnFunctionWithNonObjectThisType() {
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "var /** function(new: AllType) */ x = function() {};",
                "/** @typedef {*} */ let AllType;"));

    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    ObjectTypeProto.newBuilder()
                        .setIsInvalidating(true)
                        .addPrototype(pointerForType(PrimitiveType.UNKNOWN_TYPE))
                        .addInstanceType(pointerForType(PrimitiveType.UNKNOWN_TYPE))
                        .setMarkedConstructor(true)
                        .build())
                .build());
  }

  @Test
  public void supertypeListForInterface_includesIllegalExtendedTypes() {
    String source =
        lines(
            "class Foo {}", //
            "/** @interface */",
            "class Bar {}",
            "/** @typedef {{x: string}} */",
            "let RecordType;",
            "/** @interface",
            // [JSC_CONFLICTING_EXTENDS_TYPE]: interfaces can only extend interfaces
            " * @extends {Foo}",
            " * @extends {Bar}",
            " * @extends {RecordType}",
            " * @suppress {checkTypes}",
            "*/",
            "class Baz {}");
    TypePool typePool = compileToTypePool(source);

    assertThat(getNonPrimitiveSupertypesFor(typePool, "Baz"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactly(
            TypeProto.newBuilder()
                .setObject(ObjectTypeProto.newBuilder().setIsInvalidating(true))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Bar")).build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Baz.prototype")).build());
  }

  @Test
  public void doesntSerializeNullOrUndefinedUnion() {
    assertThat(compileToTypes("var /** null|undefined */ x;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
  }

  @Test
  public void doesntSerializeForwardDeclaredType() {
    this.typesToForwardDeclare = ImmutableSet.of("forward.declared.TypeProto");
    assertThat(compileToTypes("var /** !forward.declared.TypeProto */ x;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
  }

  @Test
  public void doesntSerializeMissingType() {
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    assertThat(compileToTypes("var /** !missing.TypeProto */ x;"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .containsExactlyElementsIn(nativeObjects());
  }

  @Test
  public void serializesSourceOfSyntheticCodeDoesntCrash() throws IOException {
    ensureLibraryInjected("base");
    compileToTypePool(new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");
  }

  @Test
  public void reconcile_setInstanceColors() {
    // When
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "class Foo { }",
                "class Bar { }",
                "",
                "let /** function(new:Foo) */ x;",
                "let /** function(new:Bar) */ y;"));

    // Then
    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(IS_INVALIDATING)
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    ObjectTypeProto.newBuilder()
                        .setMarkedConstructor(true)
                        .addInstanceType(pointerForType("Foo"))
                        .addInstanceType(pointerForType("Bar"))
                        .addPrototype(TypePointer.getDefaultInstance()))
                .build());
  }

  @Test
  public void reconcile_addPrototypes_noActualTestcase() {
    // TODO(b/185519307): Come up with a situation where this can happen.
  }

  @Test
  public void reconcile_setOwnProperties() {
    // When
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "var Foo = class { a() { } }", //
                "Foo = class { b() { } }"));

    // Then
    assertThat(typePool)
        .ignoringFieldDescriptors(OBJECT_UUID)
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("Foo.prototype")
                        .addOwnProperty(findInStringPool("a"))
                        .addOwnProperty(findInStringPool("b"))
                        .addOwnProperty(findInStringPool("constructor")))
                .build());
  }

  @Test
  public void reconcile_setClosureAssert() {
    // When
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "var /** ? */ assertFoo;",
                "",
                "assertFoo = function(/** ? */ a) { }", //
                "",
                "/** @closurePrimitive {asserts.truthy} */",
                "assertFoo = function(/** ? */ a) { }",
                "",
                "assertFoo = function(/** ? */ a) { }"));

    // Then
    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(IS_INVALIDATING)
        .contains(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("assertFoo").setClosureAssert(true))
                .build());
  }

  @Test
  public void reconcile_setConstructor_noActualTestcase() {
    // TODO(b/185519307): Come up with a situation where this can happen.
    // Being a constructor is part of the ColorId
  }

  @Test
  public void reconcile_setInvalidating() {
    // When
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "var /** ? */ Foo;", //
                "",
                "Foo = function() { };",
                "",
                "Foo = class { };"));

    // Then
    assertThat(typePool)
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .contains(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("Foo").setIsInvalidating(true))
                .build());
  }

  @Test
  public void reconcile_setPropertiesKeepOriginalName_noActualTestcase() {
    // TODO(b/185519307): Come up with a situation where this can happen.
    // Enum types have a unique reference names format which is part of the ColorId
  }

  private ObjectTypeProto.Builder namedObjectBuilder(String className) {
    return ObjectTypeProto.newBuilder()
        .setDebugInfo(
            ObjectTypeProto.DebugInfo.newBuilder()
                .setClassName(className)
                .setFilename("testcode")
                .build());
  }

  private List<TypeProto> compileToTypes(String source) {
    return compileToTypePool(source).getTypeList();
  }

  private TypePool compileToTypePool(String source) {
    return compileToTypePool(DEFAULT_EXTERNS, source);
  }

  private TypePool compileToTypePool(String externs, String source) {
    testNoWarning(externs(externs), srcs(source));
    return this.typePool;
  }

  private int findInStringPool(String str) {
    return this.stringPoolBuilder.put(str);
  }

  private ImmutableList<Integer> findAllInStringPool(String... str) {
    return stream(str).map(this::findInStringPool).collect(toImmutableList());
  }

  private static TypePointer pointerForType(PrimitiveType primitive) {
    return TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
  }

  private TypePointer pointerForType(String className) {
    List<TypeProto> types = this.typePool.getTypeList();
    for (int i = 0; i < types.size(); i++) {
      if (types.get(i).getObject().getDebugInfo().getClassName().equals(className)) {
        return TypePointer.newBuilder().setPoolOffset(untrimOffset(i)).build();
      }
    }
    throw new AssertionError("Unable to find type '" + className + "' in " + this.typePool);
  }

  /** Returns the types that are serialized for every compilation, even given an empty source */
  private ImmutableList<TypeProto> nativeObjects() {
    return ImmutableList.copyOf(compileToTypes(""));
  }

  private static List<TypeProto> getNonPrimitiveSupertypesFor(TypePool typePool, String className) {
    ArrayList<TypeProto> supertypes = new ArrayList<>();
    for (SubtypingEdge edge : typePool.getDisambiguationEdgesList()) {
      TypeProto subtype = typePool.getType(trimOffset(edge.getSubtype()));
      if (!subtype.hasObject()) {
        continue;
      }
      ObjectTypeProto objectSubtype = subtype.getObject();
      if (!objectSubtype.getDebugInfo().getClassName().equals(className)) {
        continue;
      }
      if (isAxiomatic(edge.getSupertype())) {
        continue; // Skip axiomatic supertpyes as they don't have a TypeProto
      }
      supertypes.add(typePool.getType(trimOffset(edge.getSupertype())));
    }
    return supertypes;
  }
}
