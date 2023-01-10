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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
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
        ColorPool.fromOnlyShardForTesting(TypePool.getDefaultInstance(), StringPool.empty())
            .getOnlyShardForTesting();

    assertThat(colorPool.getColor(PrimitiveType.NUMBER_TYPE.getNumber()))
        .isEqualTo(StandardColors.NUMBER);
    assertThat(colorPool.getColor(PrimitiveType.NUMBER_TYPE.getNumber())).isNotInvalidating();
    assertThat(colorPool.getColor(PrimitiveType.STRING_TYPE.getNumber()))
        .isEqualTo(StandardColors.STRING);
    assertThat(colorPool.getColor(PrimitiveType.UNKNOWN_TYPE.getNumber()))
        .isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void deserializesTopObjectTypeFromEmptyTypePool() {
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShardForTesting(TypePool.getDefaultInstance(), StringPool.empty())
            .getOnlyShardForTesting();

    Color object = colorPool.getColor(PrimitiveType.TOP_OBJECT.getNumber());
    assertThat(object).isInvalidating();
    assertThat(object.getInstanceColors()).isEmpty();
    assertThat(object.getPrototypes()).isEmpty();
  }

  @Test
  public void deserializesNativeObjectTableIntoNativeColor() {
    StringPool.Builder stringPool = StringPool.builder();
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder()
                            .setUuid(StandardColors.NUMBER_OBJECT_ID.asByteString())
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

    ColorPool colorPool = ColorPool.fromOnlyShardForTesting(typePool, stringPool.build());
    ColorPool.ShardView view = colorPool.getOnlyShardForTesting();

    Color numberObject = colorPool.getColor(StandardColors.NUMBER_OBJECT_ID);
    assertThat(numberObject).isInvalidating();
    assertThat(numberObject)
        .hasDisambiguationSupertypesThat(colorPool.getRegistry())
        .containsExactly(view.getColor(poolPointer(1)));
    assertThat(numberObject).isSameInstanceAs(view.getColor(poolPointer(0)));
    assertThat(numberObject.getId()).isEqualTo(StandardColors.NUMBER_OBJECT_ID);
  }

  @Test
  public void testSynthesizesMissingStandardColors() {
    ColorPool colorPool =
        ColorPool.fromOnlyShardForTesting(TypePool.getDefaultInstance(), StringPool.empty());

    for (ColorId id : ColorRegistry.REQUIRED_IDS) {
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
        ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()).getOnlyShardForTesting();

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
        ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()).getOnlyShardForTesting();

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
    ColorPool.ShardView colorPool =
        ColorPool.fromOnlyShardForTesting(typePool, stringPool).getOnlyShardForTesting();

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
        ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()).getOnlyShardForTesting();

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
        ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()).getOnlyShardForTesting();

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
        () -> ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()));
  }

  @Test
  public void deserializesMultipleUnionsOfNativeTypes() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())
                            .addUnionMember(PrimitiveType.STRING_TYPE.getNumber())))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())
                            .addUnionMember(PrimitiveType.BIGINT_TYPE.getNumber())))
            .build();

    ColorPool colorPool = ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty());
    ColorPool.ShardView view = colorPool.getOnlyShardForTesting();

    assertThat(view.getColor(poolPointer(0)))
        .hasAlternates(StandardColors.STRING, StandardColors.NUMBER);
    assertThat(view.getColor(poolPointer(1)))
        .hasAlternates(StandardColors.BIGINT, StandardColors.NUMBER);
  }

  @Test
  public void deserializesUnionReferencingOtherUnion() {
    var typePool =
        TypePool.newBuilder()
            // U0 := (number, string)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())
                            .addUnionMember(PrimitiveType.STRING_TYPE.getNumber())))
            // U1 := (U0, bigint)
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(poolPointer(0))
                            .addUnionMember(PrimitiveType.BIGINT_TYPE.getNumber())))
            .build();

    assertThrows(
        MalformedTypedAstException.class,
        () -> ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()));
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
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())
                            .addUnionMember(poolPointer(1))))
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())
                            .addUnionMember(poolPointer(0))))
            .build();

    // Eventually we may need to support this case, but for now throwing an explicit exception is
    // better than infinite recursion.

    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty())
                .getOnlyShardForTesting()
                .getColor(poolPointer(0)));
  }

  @Test
  public void throwsException_onTypeWithoutKindCase() {
    TypePool typePool = TypePool.newBuilder().addType(TypeProto.getDefaultInstance()).build();

    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty())
                .getOnlyShardForTesting()
                .getColor(poolPointer(0)));
  }

  @Test
  public void throwsException_onSerializedUnionOfSingleElement() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setUnion(
                        UnionTypeProto.newBuilder()
                            .addUnionMember(PrimitiveType.NUMBER_TYPE.getNumber())))
            .build();

    assertThrows(
        MalformedTypedAstException.class,
        () ->
            ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty())
                .getOnlyShardForTesting()
                .getColor(poolPointer(0)));
  }

  @Test
  public void throwsException_onOutOfBoundsStringPoolOffset() {
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
        () -> ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()));
  }

  @Test
  public void throwsException_onIdenticalPools() {
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(ByteString.copyFromUtf8("Foo"))))
            .build();
    ColorPool.Builder builder = ColorPool.builder().addShardAnd(typePool, StringPool.empty());

    assertThrows(Exception.class, () -> builder.addShard(typePool, StringPool.empty()));
  }

  @Test
  public void throwsException_onIdenticalPools_exceptDefaultInstance() {
    // Given
    ColorPool.Builder builder = ColorPool.builder();
    ColorPool.ShardView shard = builder.addShard(TypePool.getDefaultInstance(), StringPool.empty());

    // When
    ColorPool.ShardView otherShard =
        builder.addShard(TypePool.getDefaultInstance(), StringPool.empty());

    // Then
    assertThat(shard).isSameInstanceAs(otherShard);
  }

  @Test
  public void throwsException_onDuplicateIdsInSinglePool() {
    // Given
    TypePool typePool =
        TypePool.newBuilder()
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .addType(
                TypeProto.newBuilder()
                    .setObject(
                        ObjectTypeProto.newBuilder().setUuid(TEST_ID.asByteString()).build()))
            .build();

    // When & Then
    assertThrows(
        MalformedTypedAstException.class,
        () -> ColorPool.fromOnlyShardForTesting(typePool, StringPool.empty()));
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
                    .setSupertype(PrimitiveType.NUMBER_TYPE.getNumber())
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
                    .setSupertype(PrimitiveType.STRING_TYPE.getNumber())
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
                .addInstanceType(PrimitiveType.NUMBER_TYPE.getNumber()));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addInstanceType(PrimitiveType.STRING_TYPE.getNumber()));

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
                .addPrototype(PrimitiveType.NUMBER_TYPE.getNumber()));

    TypePool typePool1 =
        singleObjectPool(
            ObjectTypeProto.newBuilder()
                .setUuid(TEST_ID.asByteString())
                .addPrototype(PrimitiveType.STRING_TYPE.getNumber()));

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
  public void reconcile_debugInfo_mismatches() {
    // Given
    TypePool typePool0 =
        TypePool.newBuilder()
            .setDebugInfo(
                TypePool.DebugInfo.newBuilder()
                    .addMismatch(
                        TypePool.DebugInfo.Mismatch.newBuilder()
                            .setSourceRef("location_0")
                            .addInvolvedColor(PrimitiveType.SYMBOL_TYPE.getNumber())
                            .addInvolvedColor(PrimitiveType.STRING_TYPE.getNumber())
                            .build())
                    .addMismatch(
                        TypePool.DebugInfo.Mismatch.newBuilder()
                            .setSourceRef("location_1")
                            .addInvolvedColor(PrimitiveType.NUMBER_TYPE.getNumber())
                            .build()))
            .build();

    TypePool typePool1 =
        TypePool.newBuilder()
            .setDebugInfo(
                TypePool.DebugInfo.newBuilder()
                    .addMismatch(
                        TypePool.DebugInfo.Mismatch.newBuilder()
                            .setSourceRef("location_0")
                            .addInvolvedColor(PrimitiveType.SYMBOL_TYPE.getNumber())
                            .addInvolvedColor(PrimitiveType.BOOLEAN_TYPE.getNumber())
                            .build())
                    .addMismatch(
                        TypePool.DebugInfo.Mismatch.newBuilder()
                            .setSourceRef("location_2")
                            .addInvolvedColor(PrimitiveType.BIGINT_TYPE.getNumber())
                            .build()))
            .build();

    // When
    ColorPool colorPool =
        ColorPool.builder()
            .addShardAnd(typePool0, StringPool.empty())
            .addShardAnd(typePool1, StringPool.empty())
            .build();

    // Then
    assertThat(colorPool.getRegistry().getMismatchLocationsForDebugging())
        .isEqualTo(
            ImmutableSetMultimap.builder()
                .put(StandardColors.BOOLEAN.getId(), "location_0")
                .put(StandardColors.STRING.getId(), "location_0")
                .put(StandardColors.SYMBOL.getId(), "location_0")
                .put(StandardColors.NUMBER.getId(), "location_1")
                .put(StandardColors.BIGINT.getId(), "location_2")
                .build());
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

  private static Color.Builder createObjectColorBuilder() {
    return Color.singleBuilder().setId(fromAscii(""));
  }

  private static TypePool singleObjectPool(ObjectTypeProto.Builder builder) {
    return TypePool.newBuilder().addType(TypeProto.newBuilder().setObject(builder.build())).build();
  }

  private static int poolPointer(int offset) {
    return untrimOffset(offset);
  }
}
