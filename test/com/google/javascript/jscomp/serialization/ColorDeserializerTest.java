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
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
import com.google.javascript.jscomp.serialization.ColorDeserializer.InvalidSerializedFormatException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ColorDeserializerTest {

  @Test
  public void deserializesNativeTypesFromEmptyTypePool() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(TypePool.getDefaultInstance());

    assertThat(deserializer.pointerToColor(nativeTypePointer(NativeType.NUMBER_TYPE)))
        .isNative(NativeColorId.NUMBER);
    assertThat(deserializer.pointerToColor(nativeTypePointer(NativeType.NUMBER_TYPE)))
        .isNotInvalidating();
    assertThat(deserializer.pointerToColor(nativeTypePointer(NativeType.STRING_TYPE)))
        .isNative(NativeColorId.STRING);
    assertThat(deserializer.pointerToColor(nativeTypePointer(NativeType.UNKNOWN_TYPE)))
        .isNative(NativeColorId.UNKNOWN);
  }

  @Test
  public void deserializesNativeTypesFromInvalidatingDefinitionsInTypePool() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(
            TypePool.newBuilder()
                .addInvalidatingNative(NativeType.NUMBER_TYPE)
                .addInvalidatingNative(NativeType.NUMBER_OBJECT_TYPE)
                .build());

    assertThat(deserializer.getRegistry().get(NativeColorId.NUMBER)).isInvalidating();
    assertThat(deserializer.getRegistry().get(NativeColorId.NUMBER_OBJECT)).isInvalidating();
    assertThat(deserializer.getRegistry().get(NativeColorId.STRING)).isNotInvalidating();
  }

  @Test
  public void deserializesNativeObjectTypesFromEmptyTypePool() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(TypePool.getDefaultInstance());

    Color object = deserializer.pointerToColor(nativeTypePointer(NativeType.TOP_OBJECT));
    // while in practice the top object is always invalidating, we currently rely on the user-
    // defined type pool to tell us it is invalidating instead of hardcoding this information.
    assertThat(object).isNotInvalidating();
    assertThat(object.getDisambiguationSupertypes()).isEmpty();
    assertThat(object.getInstanceColor()).isEmpty();
    assertThat(object.getPrototype()).isEmpty();
  }

  @Test
  public void deserializesSimpleObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(0)).getId())
        .isEqualTo(ImmutableSet.of("Foo"));
  }

  @Test
  public void deserializesObjectWithPrototypeAndInstanceType() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo.prototype")))
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo instance")))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid("Foo")
                            .setPrototype(poolPointer(0))
                            .setInstanceType(poolPointer(1))
                            .setMarkedConstructor(true)))
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(2)))
        .isEqualTo(
            Color.createSingleton(
                createObjectColorBuilder()
                    .setId("Foo")
                    .setInstanceColor(
                        Color.createSingleton(
                            createObjectColorBuilder().setId("Foo instance").build()))
                    .setPrototype(
                        Color.createSingleton(
                            createObjectColorBuilder().setId("Foo.prototype").build()))
                    .setConstructor(true)
                    .build()));
  }

  @Test
  public void deserializesInvalidatingObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(ObjectTypeProto.newBuilder().setUuid("Foo").setIsInvalidating(true)))
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(0))).isInvalidating();
    assertThat(deserializer.pointerToColor(poolPointer(0)).getId()).containsExactly("Foo");
  }

  @Test
  public void addsSingleSupertypeToDirectSupertypesField() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Bar")))
            // Bar is a subtype of Foo
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(TypePointer.newBuilder().setPoolOffset(1))
                    .setSupertype(TypePointer.newBuilder().setPoolOffset(0)))
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasDisambiguationSupertypes(
            Color.createSingleton(createObjectColorBuilder().setId("Foo").build()));
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
                SubtypingEdge.newBuilder()
                    .setSubtype(TypePointer.newBuilder().setPoolOffset(1))
                    .setSupertype(TypePointer.newBuilder().setPoolOffset(0)))
            // Bar is also a subtype of Baz
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(TypePointer.newBuilder().setPoolOffset(1))
                    .setSupertype(TypePointer.newBuilder().setPoolOffset(2)))
            .build();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(1)))
        .hasDisambiguationSupertypes(
            Color.createSingleton(createObjectColorBuilder().setId("Foo").build()),
            Color.createSingleton(createObjectColorBuilder().setId("Baz").build()));
  }

  @Test
  public void throwsErrorIfDisambiguationEdgesContainsInvalidId() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(TypeProto.newBuilder().setObject(ObjectTypeProto.newBuilder().setUuid("Foo")))
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(TypePointer.newBuilder().setPoolOffset(0))
                    .setSupertype(TypePointer.newBuilder().setPoolOffset(1)))
            .build();
    assertThrows(
        InvalidSerializedFormatException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool).pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsErrorIfDisambiguationEdgesContainsNativeType() {
    // Disambiguation doesn't care about supertyping/subtyping for native types. It's assumed that
    // every type is a subtype of NativeType.UNKNOWN_TYPE.
    TypePool typePool =
        TypePool.newBuilder()
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(TypePointer.newBuilder().setNativeType(NativeType.UNKNOWN_TYPE))
                    .setSupertype(TypePointer.newBuilder().setNativeType(NativeType.NUMBER_TYPE)))
            .build();
    assertThrows(
        InvalidSerializedFormatException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool));
  }

  @Test
  public void deserializesMultipleUnionsOfNativeTypes() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(nativeTypePointer(NativeType.STRING_TYPE))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(nativeTypePointer(NativeType.BIGINT_TYPE))))
            .build();

    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

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
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(nativeTypePointer(NativeType.STRING_TYPE))))
            // U1 := (U1, bigint)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(poolPointer(0))
                            .addUnionMember(nativeTypePointer(NativeType.BIGINT_TYPE))))
            .build();

    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

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
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(
                                TypePointer.newBuilder()
                                    .setPoolOffset(1)
                                    .setDebugInfo(
                                        TypePointer.DebugInfo.newBuilder().setDescription("U1")))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(
                                TypePointer.newBuilder()
                                    .setPoolOffset(0)
                                    .setDebugInfo(
                                        TypePointer.DebugInfo.newBuilder().setDescription("U0")))))
            .build();

    // Eventually we may need to support this case, but for now throwing an explicit exception is
    // better than infinite recursion.

    assertThrows(
        InvalidSerializedFormatException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool).pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnTypePointerWithoutKind() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(TypePool.getDefaultInstance());

    assertThrows(
        InvalidSerializedFormatException.class,
        () -> deserializer.pointerToColor(TypePointer.getDefaultInstance()));
  }

  @Test
  public void throwsExceptionOnUnrecognizedNativeType() {
    ColorDeserializer deserializer =
        ColorDeserializer.buildFromTypePool(TypePool.getDefaultInstance());

    assertThrows(
        InvalidSerializedFormatException.class,
        () -> deserializer.pointerToColor(TypePointer.newBuilder().setNativeTypeValue(-1).build()));
  }

  @Test
  public void throwsExceptionOnTypeWithoutKindCase() {
    TypePool typePool = TypePool.newBuilder().addType(TypeProto.getDefaultInstance()).build();

    assertThrows(
        InvalidSerializedFormatException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool).pointerToColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnSerializedUnionOfSingleElement() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))))
            .build();

    assertThrows(
        InvalidSerializedFormatException.class,
        () -> ColorDeserializer.buildFromTypePool(typePool).pointerToColor(poolPointer(0)));
  }

  private static SingletonColorFields.Builder createObjectColorBuilder() {
    return SingletonColorFields.builder().setId("");
  }

  @Test
  public void unionThatDeduplicatesIntoSingleElementBecomesSingularColor() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))
                            .addUnionMember(nativeTypePointer(NativeType.NUMBER_TYPE))))
            .build();

    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);

    assertThat(deserializer.pointerToColor(poolPointer(0))).isNative(NativeColorId.NUMBER);
    assertThat(deserializer.pointerToColor(poolPointer(0)).isUnion()).isFalse();
  }

  private static TypePointer nativeTypePointer(NativeType nativeType) {
    return TypePointer.newBuilder().setNativeType(nativeType).build();
  }

  private static TypePointer poolPointer(int offset) {
    return TypePointer.newBuilder().setPoolOffset(offset).build();
  }
}
