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
import static com.google.javascript.jscomp.serialization.TypePointers.untrimOffset;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ColorPoolTest {

  @Test
  public void deserializesJavaScriptPrimitivesFromEmptyTypePool() {
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(TypePool.getDefaultInstance(), StringPool.empty()).getOnlyShard();

    assertThat(colorPool.getColor(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)))
        .isEqualTo(StandardColors.NUMBER);
    assertThat(colorPool.getColor(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)))
        .isNotInvalidating();
    assertThat(colorPool.getColor(primitiveTypePointer(PrimitiveType.STRING_TYPE)))
        .isEqualTo(StandardColors.STRING);
    assertThat(colorPool.getColor(primitiveTypePointer(PrimitiveType.UNKNOWN_TYPE)))
        .isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void deserializesTopObjectTypeFromEmptyTypePool() {
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(TypePool.getDefaultInstance(), StringPool.empty()).getOnlyShard();

    Color object = colorPool.getColor(primitiveTypePointer(PrimitiveType.TOP_OBJECT));
    assertThat(object).isInvalidating();
    assertThat(object.getInstanceColors()).isEmpty();
    assertThat(object.getPrototypes()).isEmpty();
  }

  @Test
  public void deserializesNativeObjectTableIntoNativeColor() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(StandardColors.NUMBER_OBJECT_ID.asByteString())
                            .setDebugInfo(
                                ObjectTypeProto.DebugInfo.newBuilder().setClassName("Number"))
                            .setIsInvalidating(true))
                    .build())
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Num.pro")))
                    .build())
            .addDisambiguationEdges(
                // Number is a subtype of Number.prototype
                SubtypingEdge.newBuilder().setSubtype(poolPointer(0)).setSupertype(poolPointer(1)))
            .build();

    ColorPool colorPool = ColorPool.fromOnlyShard(typePool, StringPool.empty());
    ColorPool.ShardView view = colorPool.getOnlyShard();

    Color numberObject = colorPool.getColor(StandardColors.NUMBER_OBJECT_ID);
    assertThat(numberObject).isInvalidating();
    assertThat(numberObject)
        .hasDisambiguationSupertypesThat(colorPool.getRegistry())
        .containsExactly(view.getColor(poolPointer(1)));
    assertThat(numberObject).isSameInstanceAs(view.getColor(poolPointer(0)));
  }

  @Test
  public void testSynthesizesMissingBoxColors() {
    ColorPool colorPool =
        ColorPool.fromOnlyShard(TypePool.getDefaultInstance(), StringPool.empty());

    for (ColorId id : StandardColors.PRIMITIVE_BOX_IDS) {
      assertThat(colorPool.getColor(id)).hasId(id);
    }
  }

  @Test
  public void deserializesSimpleObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo"))))
            .build();
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(typePool, StringPool.empty()).getOnlyShard();

    assertThat(colorPool.getColor(poolPointer(0)))
        .isEqualTo(Color.singleBuilder().setId(fromAscii("Foo")).build());
  }

  @Test
  public void deserializesObjectWithPrototypeAndInstanceType() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo.pro"))))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo.ins"))))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(ByteString.copyFromUtf8("Foo"))
                            .addPrototype(poolPointer(0))
                            .addInstanceType(poolPointer(1))
                            .setMarkedConstructor(true)))
            .build();
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(typePool, StringPool.empty()).getOnlyShard();

    assertThat(colorPool.getColor(poolPointer(2)))
        .isEqualTo(
            createObjectColorBuilder()
                .setId(fromAscii("Foo"))
                .setInstanceColor(createObjectColorBuilder().setId(fromAscii("Foo.ins")).build())
                .setPrototype(createObjectColorBuilder().setId(fromAscii("Foo.pro")).build())
                .setConstructor(true)
                .build());
  }

  @Test
  public void deserializesPropertiesFromStringPool() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(ByteString.copyFromUtf8("Foo"))
                            .addOwnProperty(1)
                            .addOwnProperty(2)))
            .build();
    StringPool stringPool = StringPool.builder().putAnd("x").putAnd("y").build();
    ColorPool.ShardView colorPool = ColorPool.fromOnlyShard(typePool, stringPool).getOnlyShard();

    assertThat(colorPool.getColor(poolPointer(0)))
        .isEqualTo(
            createObjectColorBuilder()
                .setId(fromAscii("Foo"))
                .setOwnProperties(ImmutableSet.of("x", "y"))
                .build());
  }

  @Test
  public void marksInvalidatingObject() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(ByteString.copyFromUtf8("Foo"))
                            .setIsInvalidating(true)))
            .build();
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(typePool, StringPool.empty()).getOnlyShard();

    assertThat(colorPool.getColor(poolPointer(0))).isInvalidating();
    assertThat(colorPool.getColor(poolPointer(0)).getId()).isEqualTo(ColorId.fromAscii("Foo"));
  }

  @Test
  public void marksClosureAssert() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(ByteString.copyFromUtf8("Foo"))
                            .setClosureAssert(true)))
            .build();
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShard(typePool, StringPool.empty()).getOnlyShard();

    assertThat(colorPool.getColor(poolPointer(0))).isClosureAssert();
    assertThat(colorPool.getColor(poolPointer(0)).getId()).isEqualTo(ColorId.fromAscii("Foo"));
  }

  @Test
  public void recordsDisambiguationsSupertypes_multipleSupertypesPerSubtype() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo"))))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Bar"))))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Baz"))))
            // Bar is a subtype of Foo
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(0)))
            // Bar is also a subtype of Baz
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(2)))
            .build();

    // When
    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    ColorPool.ShardView shard = colorPoolBuilder.addShard(typePool, StringPool.empty());
    ColorPool colorPool = colorPoolBuilder.build();

    // Then
    Color foo = shard.getColor(poolPointer(0));
    Color bar = shard.getColor(poolPointer(1));
    Color baz = shard.getColor(poolPointer(2));

    assertThat(bar)
        .hasDisambiguationSupertypesThat(colorPool.getRegistry())
        .containsExactly(foo, baz);
  }

  @Test
  public void recordsDisambiguationsSupertypes_cylceInSupertypeGraph() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo"))))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Bar"))))
            // Foo is a subtype of Bar
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(0)).setSupertype(poolPointer(1)))
            // Bar is also a subtype of Foo
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(1)).setSupertype(poolPointer(0)))
            .build();

    // When
    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    ColorPool.ShardView shard = colorPoolBuilder.addShard(typePool, StringPool.empty());
    ColorPool colorPool = colorPoolBuilder.build();

    // Then
    Color foo = shard.getColor(poolPointer(0));
    Color bar = shard.getColor(poolPointer(1));

    assertThat(foo).hasDisambiguationSupertypesThat(colorPool.getRegistry()).containsExactly(bar);
    assertThat(bar).hasDisambiguationSupertypesThat(colorPool.getRegistry()).containsExactly(foo);
  }

  @Test
  public void throwsErrorIfDisambiguationEdgesContainsInvalidOffset() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo"))))
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder().setSubtype(poolPointer(0)).setSupertype(poolPointer(1)))
            .build();
    assertThrows(
        MalformedTypedAstException.class,
        () -> ColorPool.fromOnlyShard(typePool, StringPool.empty()));
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

    ColorPool colorPool = ColorPool.fromOnlyShard(typePool, StringPool.empty());
    ColorPool.ShardView view = colorPool.getOnlyShard();

    assertThat(view.getColor(poolPointer(0)))
        .hasAlternates(StandardColors.STRING, StandardColors.NUMBER);
    assertThat(view.getColor(poolPointer(1)))
        .hasAlternates(StandardColors.BIGINT, StandardColors.NUMBER);
  }

  @Test
  public void deserializesUnionReferencingOtherUnion() {
    TypePool typePool =
        TypePool.newBuilder()
            // U0 := (number, string)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.STRING_TYPE))))
            // U1 := (U0, bigint)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(poolPointer(0))
                            .addUnionMember(primitiveTypePointer(PrimitiveType.BIGINT_TYPE))))
            .build();

    assertThrows(
        MalformedTypedAstException.class,
        () -> ColorPool.fromOnlyShard(typePool, StringPool.empty()));
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
                            .addUnionMember(poolPointer(1))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                            .addUnionMember(poolPointer(0))))
            .build();

    // Eventually we may need to support this case, but for now throwing an explicit exception is
    // better than infinite recursion.

    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShard(typePool, StringPool.empty())
                .getOnlyShard()
                .getColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnTypeWithoutKindCase() {
    TypePool typePool = TypePool.newBuilder().addType(TypeProto.getDefaultInstance()).build();

    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShard(typePool, StringPool.empty())
                .getOnlyShard()
                .getColor(poolPointer(0)));
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
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShard(typePool, StringPool.empty())
                .getOnlyShard()
                .getColor(poolPointer(0)));
  }

  @Test
  public void throwsExceptionOnOutOfBoundsStringPoolOffset() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(ByteString.copyFromUtf8("Foo"))
                            .addOwnProperty(1001)))
            .build();
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> ColorPool.fromOnlyShard(typePool, StringPool.empty()));
  }

  private static Color.Builder createObjectColorBuilder() {
    return Color.singleBuilder().setId(fromAscii(""));
  }

  @Test
  public void union_whoseMembersHaveSameId_becomesThatMember() {
    // Given
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(poolPointer(1))
                            .addUnionMember(poolPointer(2))
                            .build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .build();

    // When
    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    ColorPool.ShardView shard = colorPoolBuilder.addShard(typePool, StringPool.empty());
    ColorPool colorPool = colorPoolBuilder.build();

    // Then
    assertThat(colorPool.getColor(TEST_ID).isUnion()).isFalse();
    assertThat(shard.getColor(poolPointer(0))).isSameInstanceAs(shard.getColor(poolPointer(1)));
    assertThat(shard.getColor(poolPointer(0))).isSameInstanceAs(shard.getColor(poolPointer(2)));
  }

  @Test
  public void uuid_mustBeSet() {
    // Given
    TypePool typePool = singleObjectPool(ObjectTypeProto.newBuilder());

    // When & Then
    assertThrows(
        MalformedTypedAstException.class,
        () -> ColorPool.builder().addShardAnd(typePool, StringPool.empty()).build());
  }

  @Test
  public void uuid_mustNotBeAxiomatic() {
    for (ColorId id : StandardColors.AXIOMATIC_COLORS.keySet()) {
      // Given
      TypePool typePool = singleObjectPool(ObjectTypeProto.newBuilder().setUuid(id.asByteString()));

      // When & Then
      assertThrows(
          MalformedTypedAstException.class,
          () -> ColorPool.builder().addShardAnd(typePool, StringPool.empty()).build());
    }
  }

  @Test
  public void reconcile_sameId_sameShard() {
    // Given
    StringPool stringPool = StringPool.builder().putAnd("hello").putAnd("world").build();
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(TEST_ID.asByteString())
                            .addOwnProperty(1)
                            .build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(TEST_ID.asByteString())
                            .addOwnProperty(2)
                            .build()))
            .build();

    // When
    ColorPool colorPool = ColorPool.fromOnlyShard(typePool, stringPool);

    // Then
    assertThat(colorPool.getColor(TEST_ID))
        .hasOwnPropertiesSetThat()
        .containsExactly("hello", "world");
  }

  @Test
  public void reconcile_defaultValues() {
    // Given
    TypePool typePool =
        singleObjectPool(ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()));

    // When
    ColorPool colorPool = ColorPool.builder().addShardAnd(typePool, StringPool.empty()).build();

    // Then
    Color defaultColor = colorPool.getColor(TEST_ID);
    assertThat(defaultColor.getInstanceColors()).isEmpty();
    assertThat(defaultColor.getPrototypes()).isEmpty();
    assertThat(defaultColor.getOwnProperties()).isEmpty();
    assertThat(defaultColor.isClosureAssert()).isFalse();
    assertThat(defaultColor.isConstructor()).isFalse();
    assertThat(defaultColor.isInvalidating()).isFalse();
    assertThat(defaultColor.getPropertiesKeepOriginalName()).isFalse();
    assertThat(defaultColor.getDebugInfo()).isSameInstanceAs(DebugInfo.EMPTY);
  }

  @Test
  public void reconcile_setDisambiguationSupertypes() {
    // Given
    TypePool typePool0 =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(poolPointer(0))
                    .setSupertype(primitiveTypePointer(PrimitiveType.NUMBER_TYPE))
                    .build())
            .build();

    TypePool typePool1 =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(poolPointer(0))
                    .setSupertype(primitiveTypePointer(PrimitiveType.STRING_TYPE))
                    .build())
            .build();

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID))
        .hasDisambiguationSupertypesThat(colorPool.getRegistry())
        .containsExactly(StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void reconcile_setInstanceColors() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addInstanceType(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addInstanceType(primitiveTypePointer(PrimitiveType.STRING_TYPE)));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID).getInstanceColors())
        .containsExactly(StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void reconcile_addPrototypes() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addPrototype(primitiveTypePointer(PrimitiveType.NUMBER_TYPE)));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addPrototype(primitiveTypePointer(PrimitiveType.STRING_TYPE)));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID).getPrototypes())
        .containsExactly(StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void reconcile_setOwnProperties() {
    // Given
    StringPool stringPool0 = StringPool.builder().putAnd("hello").build();
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).addOwnProperty(1));

    StringPool stringPool1 = StringPool.builder().putAnd("world").build();
    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).addOwnProperty(1));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, stringPool0)
            .addShardAnd(typePool1, stringPool1)
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID))
        .hasOwnPropertiesSetThat()
        .containsExactly("hello", "world");
  }

  @Test
  public void reconcile_setClosureAssert_true() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(true));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(true));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID).isClosureAssert()).isTrue();
  }

  @Test
  public void reconcile_setClosureAssert_false() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(false));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(false));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID).isClosureAssert()).isFalse();
  }

  @Test
  public void reconcile_setClosureAssert_mixed() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(true));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setClosureAssert(false));

    // When & Then
    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.builder()
                .addShardAnd(typePool0, StringPool.empty())
                .addShardAnd(typePool1, StringPool.empty())
                .build());
  }

  @Test
  public void reconcile_setConstructor() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .setMarkedConstructor(false));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .setMarkedConstructor(true));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID)).isConstructor();
  }

  @Test
  public void reconcile_setInvalidating() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setIsInvalidating(false));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).setIsInvalidating(true));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID)).isInvalidating();
  }

  @Test
  public void reconcile_setPropertiesKeepOriginalName() {
    // Given
    TypePool typePool0 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .setPropertiesKeepOriginalName(false));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .setPropertiesKeepOriginalName(true));

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getColor(TEST_ID)).propertiesKeepOriginalName();
  }

  @Test
  public void reconcile_shards_preserveOriginalOffsets() {
    // Given
    ColorId otherId = ColorId.fromAscii("other");

    TypePool typePool0 =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(otherId.asByteString()).build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .build();

    TypePool typePool1 =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(otherId.asByteString()).build()))
            .build();

    // When
    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    ColorPool.ShardView shard0 = colorPoolBuilder.addShard(typePool0, StringPool.empty());
    ColorPool.ShardView shard1 = colorPoolBuilder.addShard(typePool1, StringPool.empty());
    colorPoolBuilder.build();

    // Then
    assertThat(shard0.getColor(poolPointer(1))).hasId(TEST_ID);
    assertThat(shard0.getColor(poolPointer(1))).isSameInstanceAs(shard1.getColor(poolPointer(0)));
  }

  private static final ColorId TEST_ID = ColorId.fromUnsigned(100);

  private static TypePool singleObjectPool(ObjectTypeProto.Builder builder) {
    return TypePool.newBuilder().addType(TypeProto.newBuilder().setObject(builder.build())).build();
  }

  private static TypePointer primitiveTypePointer(PrimitiveType primitive) {
    return TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
  }

  private static TypePointer poolPointer(int offset) {
    return TypePointer.newBuilder().setPoolOffset(untrimOffset(offset)).build();
  }
}
