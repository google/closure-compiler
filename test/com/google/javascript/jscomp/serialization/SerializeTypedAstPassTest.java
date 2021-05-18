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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;
import static com.google.javascript.jscomp.serialization.TypePointers.untrimOffset;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializeTypedAstPassTest extends CompilerTestCase {

  private Consumer<TypedAst> astConsumer;
  // individual test cases may override this
  private ImmutableSet<String> typesToForwardDeclare = null;

  // Proto fields commonly ignored in tests because hardcoding their values is brittle
  private static final ImmutableList<FieldDescriptor> BRITTLE_TYPE_FIELDS =
      ImmutableList.of(
          ObjectTypeProto.getDescriptor().findFieldByName("uuid"),
          ObjectTypeProto.getDescriptor().findFieldByName("own_property"));

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
    return new SerializeTypedAstPass(
        compiler,
        SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS,
        astConsumer);
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
    TypedAst typedAst = compile("class Foo { m() {} } new Foo().m();");
    List<TypeProto> typePool = typedAst.getTypePool().getTypeList();

    assertThat(typePool)
        .ignoringFieldDescriptors(ObjectTypeProto.getDescriptor().findFieldByName("uuid"))
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("(typeof Foo)")
                        .setPrototype(pointerForType("Foo.prototype", typePool))
                        .setInstanceType(pointerForType("Foo", typePool))
                        .setMarkedConstructor(true)
                        .addOwnProperty(findInStringPool(typedAst.getStringPool(), "prototype")))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder()
                .setObject(
                    namedObjectBuilder("Foo.prototype")
                        .addOwnProperty(findInStringPool(typedAst.getStringPool(), "constructor"))
                        .addOwnProperty(findInStringPool(typedAst.getStringPool(), "m")))
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
  public void testDisambiguationEdgesDontPointFromPrototypeToInstance() {
    TypePool typePool = compileToTypePool("class Foo { m() {} } new Foo().m();");

    assertThat(getNonPrimitiveSupertypesFor(typePool, "Foo.prototype")).isEmpty();
  }

  @Test
  public void testMultipleAnonymousTypesDoesntCrash() {
    compile("(() => 5); (() => 'str');");
  }

  @Test
  public void testUnion_proxyLikeTypes_dontPreventUnionCollapsing() {
    List<TypeProto> typePool =
        compileToTypes(
            lines(
                "/** @enum {boolean|number} */", //
                "const Foo = {};",
                "",
                "let /** !Foo|string|number */ x;"));

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
                        .addUnionMember(pointerForType(PrimitiveType.NUMBER_TYPE))
                        .addUnionMember(pointerForType(PrimitiveType.STRING_TYPE))
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
    TypedAst typedAst = compile("const /** {x: string} */ obj = {x: ''};");
    assertThat(typedAst.getTypePool().getTypeList())
        .ignoringFieldDescriptors(
            TypePointer.getDescriptor().findFieldByName("pool_offset"),
            ObjectTypeProto.getDescriptor().findFieldByName("uuid"))
        .contains(
            TypeProto.newBuilder()
                .setObject(
                    ObjectTypeProto.newBuilder()
                        .setIsInvalidating(true)
                        .addOwnProperty(findInStringPool(typedAst.getStringPool(), "x")))
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
                        .setPrototype(pointerForType("Foo.prototype", typePool))
                        .setInstanceType(pointerForType("Foo", typePool)))
                .build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo")).build(),
            TypeProto.newBuilder().setObject(namedObjectBuilder("Foo.prototype")).build());
    // Verify no additional objects were serialized to represent the template type: we only have
    // the well-known types and the three TypeProtos tested for above.
    assertThat(typePool).hasSize(nativeObjects().size() + 3);
  }

  @Test
  public void uniquifiesTwoDifferentFunctionsWithSameJspath() {
    assertThat(
            compileToTypes(
                lines(
                    "const ns = {};", //
                    "ns.f = x => x;",
                    "ns.f = (/** number */ x) => x;")))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(ObjectTypeProto.getDescriptor().findFieldByName("prototype"))
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("ns.f").setIsInvalidating(true))
                .build(),
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("ns.f").setIsInvalidating(true))
                .build());
  }

  @Test
  public void uniquifiesThreeDifferentFunctionsWithSameJspath() {
    assertThat(
            compileToTypes(
                lines(
                    "const ns = {};", //
                    "ns.f = x => x;",
                    "ns.f = (/** number */ x) => x;",
                    "ns.f = (/** string */ x) => x;")))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(ObjectTypeProto.getDescriptor().findFieldByName("prototype"))
        .containsAtLeast(
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("ns.f").setIsInvalidating(true))
                .build(),
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("ns.f").setIsInvalidating(true))
                .build(),
            TypeProto.newBuilder()
                .setObject(namedObjectBuilder("ns.f").setIsInvalidating(true))
                .build());
  }

  @Test
  public void discardsDifferentTemplatizationsOfObject() {
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
                        .setPrototype(pointerForType("Foo.prototype", typePool))
                        .setInstanceType(pointerForType("Foo", typePool)))
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
                        .setPrototype(pointerForType(PrimitiveType.UNKNOWN_TYPE))
                        .setInstanceType(pointerForType(PrimitiveType.UNKNOWN_TYPE))
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
  public void testAst_constNumber() {
    TypePointer numberType = pointerForType(PrimitiveType.NUMBER_TYPE);
    TypedAst ast = compile("const x = 5;");
    StringPoolProto stringPool = ast.getStringPool();

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .setRelativeLine(1)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.CONST_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(findInStringPool(stringPool, "x"))
                                .setOriginalNamePointer(findInStringPool(stringPool, "x"))
                                .setRelativeColumn(6)
                                .setType(numberType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.NUMBER_LITERAL)
                                        .setDoubleValue(5)
                                        .setRelativeColumn(4)
                                        .setType(numberType)
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_externs() {
    // 2 externs files, the default (empty) externs file + the source file marked with @externs
    assertThat(compile("/** @externs */ function foo() {}").getExternFileList()).hasSize(2);
  }

  @Test
  public void testAst_typeSummary() {
    // @typeSummary files are omitted, leaving only 1 serialized extern file
    assertThat(compile("/** @typeSummary */ function foo() {}").getExternFileList()).hasSize(1);
  }

  @Test
  public void testAst_letString() {
    TypePointer stringType = pointerForType(PrimitiveType.STRING_TYPE);
    TypedAst ast = compile("let s = 'hello';");
    StringPoolProto stringPool = ast.getStringPool();

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.LET_DECLARATION)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(findInStringPool(stringPool, "s"))
                                .setOriginalNamePointer(findInStringPool(stringPool, "s"))
                                .setType(stringType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.STRING_LITERAL)
                                        .setStringValuePointer(
                                            findInStringPool(stringPool, "hello"))
                                        .setType(stringType)
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testAst_numberInCast() {
    TypePointer unknownType = pointerForType(PrimitiveType.UNKNOWN_TYPE);
    assertThat(compileToAst("/** @type {?} */ (1);"))
        .ignoringFieldDescriptors(BRITTLE_TYPE_FIELDS)
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.CAST)
                                .setType(unknownType)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.NUMBER_LITERAL)
                                        .addBooleanProperty(NodeProperty.IS_PARENTHESIZED)
                                        .addBooleanProperty(NodeProperty.COLOR_FROM_CAST)
                                        .setDoubleValue(1)
                                        .setType(unknownType)
                                        .build())
                                .build()))
                .build());
  }

  @Test
  public void testAst_arrowFunction() {
    TypedAst ast = compile("() => 'hello';");

    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("type"),
            AstNode.getDescriptor().findFieldByName("relative_column"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.EXPRESSION_STATEMENT)
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.FUNCTION_LITERAL)
                                .addBooleanProperty(NodeProperty.ARROW_FN)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(0)
                                        .build())
                                .addChild(
                                    AstNode.newBuilder().setKind(NodeKind.PARAMETER_LIST).build())
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.STRING_LITERAL)
                                        .setStringValuePointer(
                                            findInStringPool(ast.getStringPool(), "hello"))
                                        .build())
                                .build())
                        .build())
                .build());
  }

  @Test
  public void testRewrittenGoogModule_containsOriginalNames() {
    enableRewriteClosureCode();

    TypedAst ast = compile("goog.module('a.b.c'); function f(x) {}");
    assertThat(ast.getCodeFileList().get(0))
        .ignoringFieldDescriptors(
            AstNode.getDescriptor().findFieldByName("relative_line"),
            AstNode.getDescriptor().findFieldByName("relative_column"),
            AstNode.getDescriptor().findFieldByName("type"))
        .isEqualTo(
            AstNode.newBuilder()
                .setKind(NodeKind.SOURCE_FILE)
                .setSourceFile(2)
                .addBooleanProperty(NodeProperty.GOOG_MODULE)
                // `/** @const */ var module$exports$a$b$c = {};`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.VAR_DECLARATION)
                        .addBooleanProperty(NodeProperty.IS_NAMESPACE)
                        .setJsdoc(OptimizationJsdoc.newBuilder().addKind(JsdocTag.JSDOC_CONST))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    findInStringPool(ast.getStringPool(), "module$exports$a$b$c"))
                                .addChild(AstNode.newBuilder().setKind(NodeKind.OBJECT_LITERAL))))
                // `function module$contents$a$b$c_f(x) {}`
                .addChild(
                    AstNode.newBuilder()
                        .setKind(NodeKind.FUNCTION_LITERAL)
                        .setOriginalNamePointer(findInStringPool(ast.getStringPool(), "f"))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.IDENTIFIER)
                                .setStringValuePointer(
                                    findInStringPool(
                                        ast.getStringPool(), "module$contents$a$b$c_f"))
                                .setOriginalNamePointer(findInStringPool(ast.getStringPool(), "f")))
                        .addChild(
                            AstNode.newBuilder()
                                .setKind(NodeKind.PARAMETER_LIST)
                                .addChild(
                                    AstNode.newBuilder()
                                        .setKind(NodeKind.IDENTIFIER)
                                        .setStringValuePointer(
                                            findInStringPool(ast.getStringPool(), "x"))
                                        .setOriginalNamePointer(
                                            findInStringPool(ast.getStringPool(), "x"))
                                        .build()))
                        .addChild(AstNode.newBuilder().setKind(NodeKind.BLOCK)))
                .build());
  }

  @Test
  public void serializesExternsFile() throws IOException {
    ensureLibraryInjected("base");

    TypedAst ast =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    AstNode externs = ast.getExternFileList().get(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(externs.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("externs");
  }

  @Test
  public void serializesSourceOfSyntheticCode() throws IOException {
    ensureLibraryInjected("base");

    TypedAst ast =
        compileWithExterns(
            new TestExternsBuilder().addMath().addObject().build(), "let s = 'hello';");

    AstNode script = ast.getCodeFileList().get(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(script.getSourceFile() - 1)
                .getFilename())
        .isEqualTo("testcode");

    // 'base' is loaded at the beginning of the first script
    AstNode baseLibraryStart = script.getChild(0);
    assertThat(
            ast.getSourceFilePool()
                .getSourceFileList()
                .get(baseLibraryStart.getSourceFile() - 1)
                .getFilename())
        .isEqualTo(" [synthetic:base] ");
    // children of the synthetic subtree default to the root's file [synthetic:base]
    assertThat(baseLibraryStart.getChild(0).getSourceFile()).isEqualTo(0);
    // "let s = 'hello'" defaults to the parent's file 'testcode'
    assertThat(script.getChild(script.getChildCount() - 1).getSourceFile()).isEqualTo(0);
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
    return compile(source).getTypePool().getTypeList();
  }

  private TypePool compileToTypePool(String source) {
    return compile(source).getTypePool();
  }

  private AstNode compileToAst(String source) {
    return compile(source).getCodeFileList().get(0);
  }

  private TypedAst compile(String source) {
    return compileWithExterns(DEFAULT_EXTERNS, source);
  }

  private TypedAst compileWithExterns(String externs, String source) {
    TypedAst[] resultAst = new TypedAst[1];
    astConsumer = (ast) -> resultAst[0] = ast;
    testNoWarning(externs(externs), srcs(source));
    return resultAst[0];
  }

  private static TypePointer pointerForType(PrimitiveType primitive) {
    return TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
  }

  private static TypePointer pointerForType(String className, List<TypeProto> pool) {
    for (int i = 0; i < pool.size(); i++) {
      if (pool.get(i).getObject().getDebugInfo().getClassName().equals(className)) {
        return TypePointer.newBuilder().setPoolOffset(untrimOffset(i)).build();
      }
    }
    throw new AssertionError("Unable to find type '" + className + "' in " + pool);
  }

  /** Returns the types that are serialized for every compilation, even given an empty source */
  private ImmutableList<TypeProto> nativeObjects() {
    return ImmutableList.copyOf(compileToTypes(""));
  }

  /**
   * Returns the offset of the given string in the given pool, throwing an exception if not present
   */
  private static int findInStringPool(StringPoolProto stringPool, String str) {
    int offset = stringPool.getStringsList().indexOf(ByteString.copyFromUtf8(str));
    checkState(offset != -1, "Could not find string '%s' in string pool %s", str, stringPool);
    return offset;
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

  private void generateDiagnosticFiles() {
    compile(
        lines(
            "class Foo0 {",
            "  w() { }",
            "}",
            "class Foo1 extends Foo0 {",
            "  y() { }",
            "}",
            "const ns = {Foo0, Foo1};",
            "/** @suppress {checkTypes} */",
            "const /** !Foo1 */ typeMismatch = new Foo0();"));
  }

  @GwtIncompatible
  private static String loadFile(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.collect(joining("\n"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @GwtIncompatible
  private ImmutableList<Path> debugLogFiles() {
    try {
      Path dir =
          Paths.get(
              this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
              SerializeTypesToPointers.class.getSimpleName());

      try (Stream<Path> files = Files.list(dir)) {
        return files.collect(toImmutableList());
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void assertValidJson(String src) {
    assertThat(src).isNotEmpty();

    Class<?> clazz = (src.charAt(0) == '{') ? LinkedHashMap.class : ArrayList.class;
    new Gson().fromJson(src, clazz); // Throws if invalid
  }
}
