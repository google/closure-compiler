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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.truth.extensions.proto.ProtoSubject;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.DebugInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ColorSerializerTest {

  @Test
  public void generateEmptyTypePool() {
    // Expect an empty TypePool when no colors are added.
    new Tester()
        .init()
        // don't add anything
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(
            TypePool.newBuilder()
                // DebugInfo is present, but empty
                .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
                .build());

    new Tester()
        .setSerializationMode(SerializationOptions.SKIP_DEBUG_INFO)
        .init()
        // don't add anything
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(TypePool.getDefaultInstance());
  }

  @Test
  public void onlyAddAxiomaticColors() {
    // These are the colors that are never actually serialized, even when requested.
    // They are always just assumed to be there when deserializing and to have fixed indices as
    // indicated by their index in TypePointers.OFFSET_TO_AXIOMATIC_COLOR.
    final List<TestColor> axiomaticTestColors =
        TypePointers.OFFSET_TO_AXIOMATIC_COLOR.stream()
            .map(this::getAxiomaticTestColor)
            .collect(Collectors.toList());

    // Expect an empty TypePool when only axiomatic colors are added.
    new Tester()
        .init()
        // add all the axiomatic colors, but only those
        .addColors(axiomaticTestColors)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(
            TypePool.newBuilder()
                // No `type` values, because axiomatic colors are never serialized.
                // DebugInfo is empty, but present.
                .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
                .build());
  }

  @Test
  public void addASuperTypeRelationship() {
    final TestColor superTypeColor =
        new TestObjectColorBuilder().setColorId("sup_type").setTrimmedPoolOffset(0).build();
    final TestColor subTypeColor =
        new TestObjectColorBuilder().setColorId("sub_type").setTrimmedPoolOffset(1).build();
    final TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(superTypeColor.getExpectedTypeProto())
            .addType(subTypeColor.getExpectedTypeProto())
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(subTypeColor.getExpectedTypePointer())
                    .setSupertype(superTypeColor.getExpectedTypePointer())
                    .build())
            // empty DebugInfo
            .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
            .build();

    new Tester()
        .init()
        .linkSubColorToSuperColor(subTypeColor, superTypeColor)
        .addColor(superTypeColor)
        .addColor(subTypeColor)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void implicitAdditionOfSuperType() {
    final TestColor subTypeColor =
        new TestObjectColorBuilder()
            .setColorId("sub_type")
            // The subtype will be serialized first, because it is explicitly added.
            .setTrimmedPoolOffset(0)
            .build();
    final TestColor superTypeColor =
        new TestObjectColorBuilder()
            .setColorId("sup_type")
            // The supertype will be serialized second, because it gets added implicitly
            // by its relationship to the subtype.
            .setTrimmedPoolOffset(1)
            .build();
    final TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(subTypeColor.getExpectedTypeProto())
            .addType(superTypeColor.getExpectedTypeProto())
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(subTypeColor.getExpectedTypePointer())
                    .setSupertype(superTypeColor.getExpectedTypePointer())
                    .build())
            // DebugInfo is present but empty.
            .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
            .build();

    new Tester()
        .init()
        .linkSubColorToSuperColor(subTypeColor, superTypeColor)
        // Only explicitly add the subtype
        .addColor(subTypeColor)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void simulatedClasses() {
    // Simulate Colors for this class:
    //
    // class Base {
    //   static baseStaticField;
    //   baseField;
    // }
    //
    // We will explicitly add the color representing `typeof Base`.
    // The colors for `Base.prototype` and the instances of `Base` will be added implicitly
    // in that order.
    final PooledString baseFieldPooledString = PooledString.create("baseField", 1);
    final TestColor baseClassInstanceTestColor =
        new TestObjectColorBuilder()
            .setColorId("Base.ins")
            // 0 => explicitly added base class constructor
            // 1 => explicitly added child class constructor
            // Then this instance color gets added implicitly
            .setTrimmedPoolOffset(2)
            .setTypeName("Base")
            .addOwnProperty(baseFieldPooledString)
            .build();
    final TestColor baseClassPrototypeTestColor =
        new TestObjectColorBuilder()
            .setColorId("Base.pro")
            // This is implicitly added after the instance color above.
            .setTrimmedPoolOffset(3)
            .setTypeName("typeof Base.prototype")
            .build();
    final PooledString baseStaticFieldPooledString = PooledString.create("baseStaticField", 0);
    final TestColor baseClassConstructorTestColor =
        new TestObjectColorBuilder()
            .setColorId("Base.con")
            // This is the first explicitly-added type.
            .setTrimmedPoolOffset(0)
            .setTypeName("typeof Base")
            .setConstructor(true)
            .addPrototypeTestColor(baseClassPrototypeTestColor)
            .addInstanceTestColor(baseClassInstanceTestColor)
            .addOwnProperty(baseStaticFieldPooledString)
            .build();

    // class Child extends Base {
    //   static childStaticField;
    //   childField;
    // }
    // We will explicitly add the color representing `typeof Child`.
    // The colors for `Child.prototype` and the instances of `Child` will be added implicitly
    // in that order.
    final PooledString childFieldPooledString = PooledString.create("childField", 3);
    final TestColor childClassInstanceTestColor =
        new TestObjectColorBuilder()
            .setColorId("Child.in")
            // 0 => explicitly added base class constructor color
            // 1 => explicitly added child class constructor color
            // 2 => implicitly added base class instance color
            // 3 => implicitly added base class prototype color
            // Then this instance color gets added implicitly
            .setTrimmedPoolOffset(4)
            .setTypeName("Child")
            .addOwnProperty(childFieldPooledString)
            .build();
    final TestColor childClassPrototypeTestColor =
        new TestObjectColorBuilder()
            .setColorId("Child.pr")
            // This is implicitly added after the instance color above.
            .setTrimmedPoolOffset(5)
            .setTypeName("typeof Child.prototype")
            .build();
    final PooledString childStaticFieldPooledString = PooledString.create("childStaticField", 2);
    final TestColor childClassConstructorTestColor =
        new TestObjectColorBuilder()
            .setColorId("Child.co")
            // This is the second explicitly-added type
            .setTrimmedPoolOffset(1)
            .setTypeName("typeof Child")
            .setConstructor(true)
            .addPrototypeTestColor(childClassPrototypeTestColor)
            .addInstanceTestColor(childClassInstanceTestColor)
            .addOwnProperty(childStaticFieldPooledString)
            .build();

    final TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(baseClassConstructorTestColor.getExpectedTypeProto())
            .addType(childClassConstructorTestColor.getExpectedTypeProto())
            .addType(baseClassInstanceTestColor.getExpectedTypeProto())
            .addType(baseClassPrototypeTestColor.getExpectedTypeProto())
            .addType(childClassInstanceTestColor.getExpectedTypeProto())
            .addType(childClassPrototypeTestColor.getExpectedTypeProto())
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(childClassConstructorTestColor.getExpectedTypePointer())
                    .setSupertype(baseClassConstructorTestColor.getExpectedTypePointer())
                    .build())
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(childClassInstanceTestColor.getExpectedTypePointer())
                    .setSupertype(baseClassInstanceTestColor.getExpectedTypePointer())
                    .build())
            .addDisambiguationEdges(
                SubtypingEdge.newBuilder()
                    .setSubtype(childClassPrototypeTestColor.getExpectedTypePointer())
                    .setSupertype(baseClassPrototypeTestColor.getExpectedTypePointer())
                    .build())
            // DebugInfo is present but empty.
            .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
            .build();

    new Tester()
        .init()
        .addPooledString(baseStaticFieldPooledString)
        .addPooledString(baseFieldPooledString)
        .addPooledString(childStaticFieldPooledString)
        .addPooledString(childFieldPooledString)
        // Only explicitly add the constructor types
        .addColor(baseClassConstructorTestColor)
        .addColor(childClassConstructorTestColor)
        .linkSubColorToSuperColor(childClassConstructorTestColor, baseClassConstructorTestColor)
        .linkSubColorToSuperColor(childClassInstanceTestColor, baseClassInstanceTestColor)
        .linkSubColorToSuperColor(childClassPrototypeTestColor, baseClassPrototypeTestColor)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void includeMismatchesAndTypenames() {
    TestColor testColor1 =
        new TestObjectColorBuilder()
            .setColorId("color001")
            .setTrimmedPoolOffset(0)
            .setTypeName("typeName1")
            .build();
    TestColor testColor2 =
        new TestObjectColorBuilder()
            .setColorId("color002")
            .setTrimmedPoolOffset(1)
            .setTypeName("typeName2")
            .build();
    TestMismatch testMismatch1 = TestMismatch.create("location1", testColor1, testColor2);

    TestColor testColor3 =
        new TestObjectColorBuilder()
            .setColorId("color003")
            .setTrimmedPoolOffset(2)
            .setTypeName("typeName3")
            .build();
    TestColor testColor4 =
        new TestObjectColorBuilder()
            .setColorId("color004")
            .setTrimmedPoolOffset(3)
            .setTypeName("typeName4")
            .build();
    TestMismatch testMismatch2 = TestMismatch.create("location2", testColor3, testColor4);

    final TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(testColor1.getExpectedTypeProto())
            .addType(testColor2.getExpectedTypeProto())
            .addType(testColor3.getExpectedTypeProto())
            .addType(testColor4.getExpectedTypeProto())
            .setDebugInfo(
                TypePool.DebugInfo.newBuilder()
                    .addMismatch(testMismatch1.getExpectedMismatch())
                    .addMismatch(testMismatch2.getExpectedMismatch())
                    .build())
            .build();

    new Tester()
        .init()
        .addColors(testColor1, testColor2, testColor3, testColor4)
        .addMismatch(testMismatch1)
        .addMismatch(testMismatch2)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void skipMismatchesAndTypenames() {
    TestColor testColor1 =
        new TestObjectColorBuilder()
            .setColorId("color001")
            .setTrimmedPoolOffset(0)
            .setTypeName("typeName1")
            .setIncludeDebugInfoInProto(false)
            .build();
    TestColor testColor2 =
        new TestObjectColorBuilder()
            .setColorId("color002")
            .setTrimmedPoolOffset(1)
            .setTypeName("typeName2")
            .setIncludeDebugInfoInProto(false)
            .build();
    TestMismatch testMismatch1 = TestMismatch.create("location1", testColor1, testColor2);

    TestColor testColor3 =
        new TestObjectColorBuilder()
            .setColorId("color003")
            .setTrimmedPoolOffset(2)
            .setTypeName("typeName3")
            .setIncludeDebugInfoInProto(false)
            .build();
    TestColor testColor4 =
        new TestObjectColorBuilder()
            .setColorId("color004")
            .setTrimmedPoolOffset(3)
            .setTypeName("typeName4")
            .setIncludeDebugInfoInProto(false)
            .build();
    TestMismatch testMismatch2 = TestMismatch.create("location2", testColor3, testColor4);

    final TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(testColor1.getExpectedTypeProto())
            .addType(testColor2.getExpectedTypeProto())
            .addType(testColor3.getExpectedTypeProto())
            .addType(testColor4.getExpectedTypeProto())
            // No debug info
            .build();

    new Tester()
        .setSerializationMode(SerializationOptions.SKIP_DEBUG_INFO)
        .init()
        .addColors(testColor1, testColor2, testColor3, testColor4)
        .addMismatch(testMismatch1)
        .addMismatch(testMismatch2)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void filterPropertyNames() {
    // Tell ColorSerializer to only keep property names beginning with "kept"
    final Predicate<String> propertyNameFilter = propName -> propName.startsWith("kept");

    // Build a type that has a property name that should be kept and one that should be
    // dropped.
    final PooledString keptPropertyName = PooledString.create("keptProperty", 0);
    final PooledString droppedPropertyName = PooledString.create("droppedProperty", 1);
    TestColor testColor =
        new TestObjectColorBuilder()
            .setColorId("color001")
            .setTrimmedPoolOffset(0)
            .addOwnProperty(keptPropertyName)
            .addOwnProperty(droppedPropertyName)
            .build();

    final TypePool actualTypePool =
        new Tester()
            .setPropertyNameFilter(propertyNameFilter)
            .init()
            .addPooledString(keptPropertyName)
            .addPooledString(droppedPropertyName)
            .addColor(testColor)
            .generateTypePool()
            .getTypePool();

    final List<Integer> ownPropertyList =
        actualTypePool.getType(0).getObject().getOwnPropertyList();
    assertThat(ownPropertyList).containsExactly(keptPropertyName.getPoolOffset());
  }

  @Test
  public void addUnionImplicitlyAddsMembers() {
    final TestColor testColor1 =
        new TestObjectColorBuilder()
            .setColorId("color001")
            // We will explicitly add this color first
            .setTrimmedPoolOffset(1)
            .setTypeName("testColor1")
            .build();
    final TestColor testColor2 =
        new TestObjectColorBuilder()
            .setColorId("color002")
            .setTrimmedPoolOffset(2)
            .setTypeName("testColor2")
            .build();
    final TestColor unionTestColor =
        new TestUnionColorBuilder()
            .setTrimmedPoolOffset(0)
            .addTestColor(testColor1)
            .addTestColor(testColor2)
            .build();

    TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(unionTestColor.getExpectedTypeProto())
            .addType(testColor1.getExpectedTypeProto())
            .addType(testColor2.getExpectedTypeProto())
            // empty DebugInfo
            .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
            .build();

    new Tester()
        .init()
        .addColor(unionTestColor)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  @Test
  public void avoidAddingDuplicateUnions() {
    final TestColor testColor1 =
        new TestObjectColorBuilder()
            .setColorId("color001")
            // We will explicitly add this color first
            .setTrimmedPoolOffset(1)
            .setTypeName("testColor1")
            .build();
    final TestColor testColor2 =
        new TestObjectColorBuilder()
            .setColorId("color002")
            .setTrimmedPoolOffset(2)
            .setTypeName("testColor2")
            .build();
    final TestColor unionTestColor =
        new TestUnionColorBuilder()
            .setTrimmedPoolOffset(0)
            .addTestColor(testColor1)
            .addTestColor(testColor2)
            .build();
    // Creating a second union containing the same types will create a distinct
    // Java object, but it will have the same ColorId.
    final TestColor duplicateUnionTestColor =
        new TestUnionColorBuilder()
            .setTrimmedPoolOffset(0) // should end up getting the same offset
            .addTestColor(testColor1)
            .addTestColor(testColor2)
            .build();

    TypePool expectedTypePool =
        TypePool.newBuilder()
            .addType(unionTestColor.getExpectedTypeProto())
            // duplicate Union color should not be serialized
            .addType(testColor1.getExpectedTypeProto())
            .addType(testColor2.getExpectedTypeProto())
            // empty DebugInfo
            .setDebugInfo(TypePool.DebugInfo.getDefaultInstance())
            .build();

    new Tester()
        .init()
        .addColor(unionTestColor)
        // Try to add the duplicate union color. It should be ignored.
        .addColor(duplicateUnionTestColor)
        .generateTypePool()
        .assertThatTypePool()
        .isEqualTo(expectedTypePool);
  }

  /**
   * Represents a string that is stored in a StringPool, recording both its value and its offset.
   */
  @AutoValue
  abstract static class PooledString {
    public abstract String getValue();

    public abstract int getPoolOffset();

    static PooledString create(String value, int poolOffset) {
      return new AutoValue_ColorSerializerTest_PooledString(value, poolOffset);
    }
  }

  /**
   * Builds objects to represent both an object Color to be added and the TypePointer and TypeProto
   * we expect to be generated for it.
   */
  private static class TestObjectColorBuilder {
    private final ArrayList<TestColor> instanceTestColors = new ArrayList<>();
    private final ArrayList<TestColor> prototypeTestColors = new ArrayList<>();
    private final ArrayList<PooledString> ownProperties = new ArrayList<>();

    private ColorId colorId;
    private int trimmedPoolOffset = -1;
    // An empty string indicates that the type has no name.
    private String typeName = "";
    private boolean isConstructor = false;
    private boolean includeDebugInfoInProto = true;

    public TestObjectColorBuilder setColorId(String colorIdString) {
      checkArgument(colorIdString.length() <= 8, "color ID string too long: %s", colorIdString);
      return setColorId(ColorId.fromAscii(colorIdString));
    }

    public TestObjectColorBuilder setColorId(ColorId colorId) {
      this.colorId = colorId;
      return this;
    }

    public TestObjectColorBuilder setTrimmedPoolOffset(int trimmedPoolOffset) {
      checkState(trimmedPoolOffset >= 0, "invalid trimmedPoolOffset: %s", trimmedPoolOffset);
      this.trimmedPoolOffset = trimmedPoolOffset;
      return this;
    }

    public TestObjectColorBuilder setConstructor(boolean constructor) {
      isConstructor = constructor;
      return this;
    }

    public TestObjectColorBuilder setTypeName(String typeName) {
      this.typeName = typeName;
      return this;
    }

    public TestObjectColorBuilder addInstanceTestColor(TestColor instanceTestColor) {
      this.instanceTestColors.add(instanceTestColor);
      return this;
    }

    public TestObjectColorBuilder addPrototypeTestColor(TestColor prototypeTestColor) {
      this.prototypeTestColors.add(prototypeTestColor);
      return this;
    }

    public TestObjectColorBuilder addOwnProperty(PooledString ownProperty) {
      ownProperties.add(ownProperty);
      return this;
    }

    public TestObjectColorBuilder setIncludeDebugInfoInProto(boolean includeDebugInfoInProto) {
      this.includeDebugInfoInProto = includeDebugInfoInProto;
      return this;
    }

    TestColor build() {
      checkState(trimmedPoolOffset >= 0, "call setTrimmedPoolOffset() first");
      // There's no testing benefit to varying these values
      final boolean propertiesKeepOriginalName = false;
      final boolean isInvalidating = false;
      final boolean isClosureAssert = false;

      final Color.Builder colorBuilder =
          Color.singleBuilder()
              .setId(colorId)
              .setInvalidating(isInvalidating)
              .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
              .setConstructor(isConstructor)
              .setClosureAssert(isClosureAssert)
              .setInstanceColors(
                  instanceTestColors.stream().map(TestColor::getColor).collect(toImmutableSet()))
              .setPrototypes(
                  prototypeTestColors.stream().map(TestColor::getColor).collect(toImmutableSet()))
              .setOwnProperties(
                  ownProperties.stream().map(PooledString::getValue).collect(toImmutableSet()));
      final TypePointer typePointer =
          TypePointer.newBuilder()
              .setPoolOffset(TypePointers.untrimOffset(trimmedPoolOffset))
              .build();
      final TypeProto.Builder typeProtoBuilder = TypeProto.newBuilder();
      final ObjectTypeProto.Builder objectTypeProtoBuilder = typeProtoBuilder.getObjectBuilder();
      objectTypeProtoBuilder
          .setUuid(colorId.asByteString())
          .setIsInvalidating(isInvalidating)
          .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
          .setMarkedConstructor(isConstructor)
          .setClosureAssert(isClosureAssert)
          .addAllInstanceType(
              instanceTestColors.stream()
                  .map(TestColor::getExpectedTypePointer)
                  .collect(Collectors.toList()))
          .addAllPrototype(
              prototypeTestColors.stream()
                  .map(TestColor::getExpectedTypePointer)
                  .collect(Collectors.toList()))
          .addAllOwnProperty(
              ownProperties.stream().map(PooledString::getPoolOffset).collect(Collectors.toList()));
      if (!typeName.isEmpty()) {
        colorBuilder.setDebugInfo(DebugInfo.builder().setCompositeTypename(typeName).build());
        if (includeDebugInfoInProto) {
          objectTypeProtoBuilder.getDebugInfoBuilder().addTypename(typeName);
        }
      }
      return TestColor.create(colorBuilder.build(), typeProtoBuilder.build(), typePointer);
    }
  }

  static class TestUnionColorBuilder {
    private final ArrayList<TestColor> memberTestColors = new ArrayList<>();

    private int trimmedPoolOffset = -1;

    public TestUnionColorBuilder setTrimmedPoolOffset(int trimmedPoolOffset) {
      checkState(trimmedPoolOffset >= 0, "invalid trimmedPoolOffset: %s", trimmedPoolOffset);
      this.trimmedPoolOffset = trimmedPoolOffset;
      return this;
    }

    TestUnionColorBuilder addTestColor(TestColor testColor) {
      memberTestColors.add(testColor);
      return this;
    }

    TestColor build() {
      checkState(trimmedPoolOffset >= 0, "call setTrimmedPoolOffset() first");
      final ImmutableSet<Color> memberColors =
          memberTestColors.stream().map(TestColor::getColor).collect(toImmutableSet());
      Color color = Color.createUnion(memberColors);

      final TypePointer typePointer =
          TypePointer.newBuilder()
              .setPoolOffset(TypePointers.untrimOffset(trimmedPoolOffset))
              .build();

      final List<TypePointer> memberTypePoiners =
          memberTestColors.stream()
              .map(TestColor::getExpectedTypePointer)
              .collect(Collectors.toList());
      final TypeProto.Builder typeProtoBuilder = TypeProto.newBuilder();
      typeProtoBuilder.getUnionBuilder().addAllUnionMember(memberTypePoiners);
      final TypeProto typeProto = typeProtoBuilder.build();
      return TestColor.create(color, typeProto, typePointer);
    }
  }

  TestColor getAxiomaticTestColor(Color axiomaticColor) {
    final int poolOffset = TypePointers.OFFSET_TO_AXIOMATIC_COLOR.indexOf(axiomaticColor);
    checkArgument(poolOffset >= 0, "Not an axiomatic color: %s", axiomaticColor);
    return TestColor.create(
        axiomaticColor, null, TypePointer.newBuilder().setPoolOffset(poolOffset).build());
  }

  /** Represents a Color that has been or will be added to the ColorSerializer. */
  @AutoValue
  abstract static class TestColor {
    // The Color that will be added to the ColorSerializer.
    public abstract Color getColor();

    /**
     * The TypeProto we expect ColorSerializer to create for this Color.
     *
     * <p>For an axiomatic color this will return `null`, since those are never stored into a
     * `TypeProto`. Generally test code should call `getExpectedTypeProto()` instead in order to get
     * an exception if an attempt is made to serialize an axiomatic color.
     */
    @Nullable
    public abstract TypeProto getNullableExpectedTypeProto();

    // The TypePointer we expect ColorSerializer to create for this Color.
    public abstract TypePointer getExpectedTypePointer();

    public TypeProto getExpectedTypeProto() {
      return checkNotNull(getNullableExpectedTypeProto());
    }

    static TestColor create(
        Color color, TypeProto expectedTypeProto, TypePointer nullableExpectedTypePointer) {
      return new AutoValue_ColorSerializerTest_TestColor(
          color, expectedTypeProto, nullableExpectedTypePointer);
    }
  }

  /**
   * Represents a color/type mismatch both as it would appear in a ColorRegistry and in a TypePool.
   */
  @AutoValue
  abstract static class TestMismatch {
    public abstract String getLocationString();

    public abstract ImmutableList<TestColor> getTestColors();

    public List<Color> getColors() {
      return getTestColors().stream().map(TestColor::getColor).collect(Collectors.toList());
    }

    public TypePool.DebugInfo.Mismatch getExpectedMismatch() {
      final List<TypePointer> involvedColorTypePointers =
          getTestColors().stream()
              .map(TestColor::getExpectedTypePointer)
              .collect(Collectors.toList());
      return TypePool.DebugInfo.Mismatch.newBuilder()
          .setSourceRef(getLocationString())
          .addAllInvolvedColor(involvedColorTypePointers)
          .build();
    }

    /**
     * @param locationString Used as the `source_ref` string in the Mismatch proto
     * @param testColors Used as the `involved_color` repeated field in the Mismatch proto, in the
     *     order
     */
    static TestMismatch create(String locationString, TestColor... testColors) {
      return new AutoValue_ColorSerializerTest_TestMismatch(
          locationString, ImmutableList.copyOf(testColors));
    }
  }

  private static class Tester {
    // Simulates ColorRegistry#getDisambiguationSuperTypes()
    private final SetMultimap<Color, Color> subColorToSuperColorsMap = HashMultimap.create();
    // Simulates ColorRegistry#getMismatchLocationsForDebugging()
    private final SetMultimap<Color, String> colorToMismatchLocationStringsMap =
        HashMultimap.create();
    // Simulates StringPool.Builder#put()
    private final HashMap<String, Integer> stringToPoolOffsetMap = new HashMap<>();
    // Test object
    private ColorSerializer colorSerializer;
    // Default to the serialization mode that includes the most information.
    // If the test case includes information that doesn't make it into the expected result,
    // then I want the test case to clearly request that the information be omitted.
    private SerializationOptions serializationMode =
        SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS;
    // Tells ColorSerializer which property names to serialize
    private Predicate<String> propertyNameFilter = propName -> true;

    Tester() {}

    Tester setSerializationMode(SerializationOptions serializationMode) {
      checkState(colorSerializer == null, "call this method before init()");
      this.serializationMode = serializationMode;
      return this;
    }

    Tester setPropertyNameFilter(Predicate<String> propertyNameFilter) {
      checkState(colorSerializer == null, "call this method before init()");
      this.propertyNameFilter = propertyNameFilter;
      return this;
    }

    Tester init() {
      checkState(colorSerializer == null, "init() called more than once");
      colorSerializer =
          new ColorSerializer(
              serializationMode,
              str -> checkNotNull(stringToPoolOffsetMap.get(str), "unexpected string: %s", str),
              propertyNameFilter);
      return this;
    }

    /** Be prepared to respond to a request for this string. */
    Tester addPooledString(PooledString pooledString) {
      final String string = pooledString.getValue();
      checkState(!stringToPoolOffsetMap.containsKey(string), "duplicate string added: %s", string);
      stringToPoolOffsetMap.put(string, pooledString.getPoolOffset());
      return this;
    }

    Tester linkSubColorToSuperColor(Color subColor, Color superColor) {
      subColorToSuperColorsMap.put(subColor, superColor);
      return this;
    }

    Tester linkSubColorToSuperColor(TestColor subColor, TestColor superColor) {
      return linkSubColorToSuperColor(subColor.getColor(), superColor.getColor());
    }

    Tester addMismatch(TestMismatch testMismatch) {
      final String locationString = testMismatch.getLocationString();
      for (Color color : testMismatch.getColors()) {
        colorToMismatchLocationStringsMap.put(color, locationString);
      }
      return this;
    }

    Tester addColor(TestColor testColor) {
      final TypePointer typePointer = colorSerializer.addColor(testColor.getColor());
      ProtoTruth.assertThat(typePointer).isEqualTo(testColor.getExpectedTypePointer());
      return this;
    }

    Tester addColors(TestColor... testColors) {
      return addColors(Arrays.asList(testColors));
    }

    Tester addColors(List<TestColor> testColorList) {
      checkNotNull(colorSerializer, "call init() first");
      final List<Color> colors =
          testColorList.stream().map(TestColor::getColor).collect(Collectors.toList());
      final ImmutableList<TypePointer> typePointers = colorSerializer.addColors(colors);
      for (int i = 0; i < testColorList.size(); ++i) {
        ProtoTruth.assertThat(typePointers.get(i))
            .isEqualTo(testColorList.get(i).getExpectedTypePointer());
      }
      return this;
    }

    GenerateTypePoolTestResult generateTypePool() {
      checkNotNull(colorSerializer, "call init() first");
      final TypePool typePool =
          colorSerializer.generateTypePool(
              subtypeColor -> ImmutableSet.copyOf(subColorToSuperColorsMap.get(subtypeColor)),
              color -> ImmutableSet.copyOf(colorToMismatchLocationStringsMap.get(color)));
      return GenerateTypePoolTestResult.create(typePool);
    }
  }

  /** The result of Tester::generateTypePool() */
  @AutoValue
  abstract static class GenerateTypePoolTestResult {
    public abstract TypePool getTypePool();

    ProtoSubject assertThatTypePool() {
      return ProtoTruth.assertThat(getTypePool());
    }

    static GenerateTypePoolTestResult create(TypePool typePool) {
      return new AutoValue_ColorSerializerTest_GenerateTypePoolTestResult(typePool);
    }
  }
}
