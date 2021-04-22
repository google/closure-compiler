/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.colors.ColorId.fromAscii;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
import com.google.javascript.jscomp.serialization.ColorDeserializer.InvalidSerializedFormatException;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ColorDeserializerTest {

  @Test
  public void deserializesJavaScriptPrimitivesFromEmptyTypePool() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(
            TypePool.getDefaultInstance(), StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)))
        .isNative(NativeColorId.NUMBER);
    assertThat(deserializer.pointerToColor(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)))
        .isNotInvalidating();
    assertThat(deserializer.pointerToColor(primitiveTypePointer(PrimitiveType.STRING_TYPE)))
        .isNative(NativeColorId.STRING);
    assertThat(deserializer.pointerToColor(primitiveTypePointer(PrimitiveType.UNKNOWN_TYPE)))
        .isNative(NativeColorId.UNKNOWN);
  }

  @Test
  public void deserializesTopObjectTypeFromEmptyTypePool() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(
            TypePool.getDefaultInstance(), StringPool.getDefaultInstance());

    Color object = deserializer.pointerToColor(primitiveTypePointer(PrimitiveType.TOP_OBJECT));
    assertThat(object).isInvalidating();
    assertThat(object.getDisambiguationSupertypes()).isEmpty();
    assertThat(object.getInstanceColor()).isEmpty();
    assertThat(object.getPrototype()).isEmpty();
  }

  @Test
  public void deserializesNativeObjectTableIntoNativeColor() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid("Number").setIsInvalidating(true))
                    .build())
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Num.pro"))
                    .build())
            .setNativeObjectTable(NativeObjectTable.newBuilder().setNumberObject(poolPointer(0)))
            .addDisambiguationEdges(
                // Number is a subtype of Number.prototype
                SubtypingEdge.newBuilder().setSubtype(poolPointer(0)).setSupertype(poolPointer(1)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    Color numberObject = deserializer.getRegistry().get(NativeColorId.NUMBER_OBJECT);
    assertThat(numberObject).isInvalidating();
    assertThat(numberObject)
        .hasDisambiguationSupertypes(deserializer.pointerToColor(poolPointer(1)));
    assertThat(numberObject).isSameInstanceAs(deserializer.pointerToColor(poolPointer(0)));
  }

  @Test
  public void deserializesSimpleObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0)))
        .isEqualTo(
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Foo")).build()));
  }

  @Test
  public void deserializesObjectWithPrototypeAndInstanceType() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo.pro")))
            .addType(
                TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo.ins")))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid("Foo")
                            .setPrototype(poolPointer(0))
                            .setInstanceType(poolPointer(1))
                            .setMarkedConstructor(true)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(2)))
        .isEqualTo(
            Color.createSingleton(
                createObjectColorBuilder()
                    .setId(fromAscii("Foo"))
                    .setInstanceColor(
                        Color.createSingleton(
                            createObjectColorBuilder().setId(fromAscii("Foo.ins")).build()))
                    .setPrototype(
                        Color.createSingleton(
                            createObjectColorBuilder().setId(fromAscii("Foo.pro")).build()))
                    .setConstructor(true)
                    .build()));
  }

  @Test
  public void deserializesPropertiesFromStringPool() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid("Foo")
                            .addOwnProperty(0)
                            .addOwnProperty(1)))
            .build();
    StringPool stringPool =
        StringPool.newBuilder()
            .addStrings(ByteString.copyFromUtf8("x"))
            .addStrings(ByteString.copyFromUtf8("y"))
            .setMaxLength(1)
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool, stringPool);

    assertThat(deserializer.pointerToColor(poolPointer(0)))
        .isEqualTo(
            Color.createSingleton(
                createObjectColorBuilder()
                    .setId(fromAscii("Foo"))
                    .setOwnProperties(ImmutableSet.of("x", "y"))
                    .build()));
  }

  @Test
  public void marksInvalidatingObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo").setIsInvalidating(true)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0))).isInvalidating();
    assertThat(deserializer.pointerToColor(poolPointer(0)).getId())
        .isEqualTo(ColorId.fromAscii("Foo"));
  }

  @Test
  public void marksClosureAssert() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo").setClosureAssert(true)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0))).isClosureAssert();
    assertThat(deserializer.pointerToColor(poolPointer(0)).getId())
        .isEqualTo(ColorId.fromAscii("Foo"));
  }

  @Test
  public void addsSingleSupertypeToDirectSupertypesField() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Bar")))
            // Bar is a subtype of Foo
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(0)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasDisambiguationSupertypes(
            Color.createSingleton(createObjectColorBuilder().setId(fromAscii("Foo")).build()));
  }

  @Test
  public void addsMultipleSupertypesToDirectSupertypesField() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Bar")))
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Baz")))
            // Bar is a subtype of Foo
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(0)))
            // Bar is also a subtype of Baz
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(2)))
            .build();
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasDisambiguationSupertypes(
            Color.createSingleton(createObjectColorBuilder().setId(fromAscii("Foo")).build()),
            Color.createSingleton(createObjectColorBuilder().setId(fromAscii("Baz")).build()));
  }

  @Test
  public void throwsErrorIfDisambiguationEdgesContainsInvalidId() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(0)).setSupertype(poolPointer(1)))
            .build();
    assertThrows(
        InvalidSerializedFormatException.class,
        () ->
            ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance())
                .pointerToColor(poolPointer(0)));
  }

  @Test
  public void deserializesMultipleUnionsOfNativeTypes() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.STRING_TYPE))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.BIGINT_TYPE))))
            .build();

    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0)))
        .hasAlternates(
            deserializer.getRegistry().get(NativeColorId.STRING),
            deserializer.getRegistry().get(NativeColorId.NUMBER));
    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasAlternates(
            deserializer.getRegistry().get(NativeColorId.BIGINT),
            deserializer.getRegistry().get(NativeColorId.NUMBER));
  }

  @Test
  public void deserializesUnionReferencingEarlierTypeInPool() {
    TypePool typePool =
        TypePool.newBuilder()
            // U0 := (number, string)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.STRING_TYPE))))
            // U1 := (U1, bigint)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(poolPointer(0))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.BIGINT_TYPE))))
            .build();

    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0)))
        .hasAlternates(
            deserializer.getRegistry().get(NativeColorId.STRING),
            deserializer.getRegistry().get(NativeColorId.NUMBER));

    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasAlternates(
            deserializer.getRegistry().get(NativeColorId.BIGINT),
            deserializer.getRegistry().get(NativeColorId.STRING),
            deserializer.getRegistry().get(NativeColorId.NUMBER));
  }

  @Test
  public void deserializingUnionsInCycleThrowsErrors() {
    // Create two union types U0 and U1. U0 := (U1 | NUMBER), U1 := (U0 | number)
    // These cycles are at least possible to construct in the Closure type system.
    // (Note - cycles are much more likely in object types)
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(
                                poolPointerBuilder(1)
                                    .setDebugInfo(
                                        TypePointer.DebugInfo.newBuilder().setDescription("U1")))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(
                                poolPointerBuilder(0)
                                    .setDebugInfo(
                                        TypePointer.DebugInfo.newBuilder().setDescription("U0")))))
            .build();

    // Eventually we may need to support this case, but for now throwing an explicit exception is
    // better than infinite recursion.

    assertThrows(
        InvalidSerializedFormatException.class,
        () ->
            ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance())
                .pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnTypeWithoutKindCase() {
    TypePool typePool = TypePool.newBuilder().addType(TypeProto.getDefaultInstance()).build();

    assertThrows(
        InvalidSerializedFormatException.class,
        () ->
            ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance())
                .pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnSerializedUnionOfSingleElement() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))))
            .build();

    assertThrows(
        InvalidSerializedFormatException.class,
        () ->
            ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance())
                .pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnOutOfBoundsStringPoolOffset() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo").addOwnProperty(1001)))
            .build();
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance()));
  }

  private static SingletonColorFields.Builder createObjectColorBuilder() {
    return SingletonColorFields.builder().setId(fromAscii(""));
  }

  @Test
  public void unionThatDeduplicatesIntoSingleElementBecomesSingularColor() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))))
            .build();

    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(typePool, StringPool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(poolPointer(0))).isNative(NativeColorId.NUMBER);
    assertThat(deserializer.pointerToColor(poolPointer(0)).isUnion()).isFalse();
  }

  private static TypePointer primitiveTypePointer(PrimitiveType primitive) {
    return TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
  }

  private static TypePointer poolPointer(int offset) {
    return poolPointerBuilder(offset).build();
  }

  private static TypePointer.Builder poolPointerBuilder(int offset) {
    return TypePointer.newBuilder().setPoolOffset(offset + JSTypeSerializer.PRIMITIVE_POOL_SIZE);
  }
}
