/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeResolver;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeFlattenerTest {

  private final Compiler compiler = new Compiler();
  private final JSTypeRegistry registry = this.compiler.getTypeRegistry();

  @Test
  public void topLikeTypes_flattenToTop() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);
    FlatType flatTop = flattener.flatten(JSTypeNative.ALL_TYPE);

    // When & Then
    assertThat(flattener.flatten((JSType) null)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(JSTypeNative.NULL_TYPE)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(JSTypeNative.VOID_TYPE)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(JSTypeNative.NULL_VOID)).isSameInstanceAs(flatTop);
  }

  @Test
  public void unknownLikeTypes_flattenToTop() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);
    FlatType flatTop = flattener.flatten(JSTypeNative.ALL_TYPE);

    // When & Then
    assertThat(flattener.flatten(JSTypeNative.UNKNOWN_TYPE)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(JSTypeNative.CHECKED_UNKNOWN_TYPE)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(this.registry.createTemplateType("X"))).isSameInstanceAs(flatTop);
    assertThat(
            flattener.flatten(
                this.registry.createUnionType(JSTypeNative.UNKNOWN_TYPE, JSTypeNative.VOID_TYPE)))
        .isSameInstanceAs(flatTop);
  }

  @Test
  public void bottomLikeTypes_flattenToTop() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);
    FlatType flatTop = flattener.flatten(JSTypeNative.ALL_TYPE);

    // When & Then
    assertThat(flattener.flatten(JSTypeNative.NO_TYPE)).isSameInstanceAs(flatTop);
    assertThat(flattener.flatten(JSTypeNative.NO_OBJECT_TYPE)).isSameInstanceAs(flatTop);
  }

  @Test
  public void templatizedTypes_flattenToRawType() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    FlatType flatFoo;
    JSType fooOfString;
    JSType fooOfDate;
    JSType fooOfUnknown;

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType fooCtorType =
          this.registry.createConstructorType(
              "Foo",
              null,
              null,
              null,
              ImmutableList.of(this.registry.createTemplateType("T")),
              false);
      ObjectType fooType = fooCtorType.getInstanceType();
      flatFoo = flattener.flatten(fooType);
      assertThat(fooType.isRawTypeOfTemplatizedType()).isTrue();

      fooOfString =
          this.registry.createTemplatizedType(
              fooType, ImmutableList.of(this.registry.getNativeType(JSTypeNative.STRING_TYPE)));
      fooOfDate =
          this.registry.createTemplatizedType(
              fooType, ImmutableList.of(this.registry.getNativeType(JSTypeNative.DATE_TYPE)));
      fooOfUnknown =
          this.registry.createTemplatizedType(
              fooType, ImmutableList.of(this.registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)));
    }

    // When & Then
    assertThat(flattener.flatten(fooOfString)).isSameInstanceAs(flatFoo);
    assertThat(flattener.flatten(fooOfDate)).isSameInstanceAs(flatFoo);
    assertThat(flattener.flatten(fooOfUnknown)).isSameInstanceAs(flatFoo);
  }

  @Test
  public void primitiveTypes_flattenToBoxingObjectTypes() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    ImmutableList<JSTypeNative> primitiveNativeTypes =
        ImmutableList.of(
            JSTypeNative.BOOLEAN_TYPE,
            JSTypeNative.NUMBER_TYPE,
            JSTypeNative.STRING_TYPE,
            JSTypeNative.SYMBOL_TYPE);

    // When
    ImmutableList<FlatType> flatPrimitives =
        primitiveNativeTypes.stream().map(flattener::flatten).collect(toImmutableList());

    // Then
    assertThat(flatPrimitives)
        .comparingElementsUsing(isBoxingTypeFor)
        .containsExactlyElementsIn(primitiveNativeTypes)
        .inOrder();
  }

  @Test
  public void unionTypes_whenFlattened_flattenTheirMembers() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    JSType numberOrStringType =
        this.registry.createUnionType(JSTypeNative.STRING_TYPE, JSTypeNative.NUMBER_TYPE);
    JSType numberOrStringObjectType =
        this.registry.createUnionType(
            JSTypeNative.NUMBER_OBJECT_TYPE, JSTypeNative.STRING_OBJECT_TYPE);

    FlatType flatNumberObject = flattener.flatten(JSTypeNative.NUMBER_OBJECT_TYPE);
    FlatType flatStringObject = flattener.flatten(JSTypeNative.STRING_OBJECT_TYPE);
    FlatType flatNumberOrStringObject = flattener.flatten(numberOrStringObjectType);

    // Given
    FlatType flatNumberOrString = flattener.flatten(numberOrStringType);

    // Then
    assertThat(flatNumberOrString).isSameInstanceAs(flatNumberOrStringObject);
    assertThat(flatNumberOrString.getTypeUnion())
        .containsExactly(flatNumberObject, flatStringObject);
  }

  @Test
  public void unionTypes_whenFlattened_dropNullAndUndefined() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    JSType nullOrVoidOrNumberType =
        this.registry.createUnionType(
            JSTypeNative.NULL_TYPE, JSTypeNative.VOID_TYPE, JSTypeNative.NUMBER_TYPE);

    FlatType flatNumber = flattener.flatten(JSTypeNative.NUMBER_TYPE);

    // Given
    FlatType flatNullOrVoidOrNumber = flattener.flatten(nullOrVoidOrNumberType);

    // Then
    assertThat(flatNullOrVoidOrNumber).isSameInstanceAs(flatNumber);
  }

  @Test
  public void unionTypes_whenFlattened_toSingleElement_hasSingleArity() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    TemplateType aKey = this.registry.createTemplateType("A");
    TemplateType bKey = this.registry.createTemplateType("B");
    ObjectType rawArray = this.registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);

    JSType arrayOfAOrArrayOfB =
        this.registry.createUnionType(
            this.registry.createTemplatizedType(rawArray, aKey),
            this.registry.createTemplatizedType(rawArray, bKey));
    assertThat(arrayOfAOrArrayOfB.getUnionMembers()).hasSize(2);

    // When
    FlatType flatArray = flattener.flatten(arrayOfAOrArrayOfB);

    // Then
    assertThat(flatArray.getArity()).isEqualTo(FlatType.Arity.SINGLE);
    assertType(flatArray.getTypeSingle()).isEqualTo(rawArray);
  }

  @Test
  public void invalidatingTypesAreChecked_duringFlattening() {
    // Given
    JSType numberType = this.registry.getNativeType(JSTypeNative.NUMBER_TYPE);

    TypeFlattener flattener = this.createFlattener(numberType::equals);

    FlatType flatNumberObject = flattener.flatten(JSTypeNative.NUMBER_OBJECT_TYPE);
    assertThat(flatNumberObject.isInvalidating()).isFalse();

    // Given
    FlatType flatNumber = flattener.flatten(numberType);

    // Then
    assertThat(flatNumber.isInvalidating()).isTrue();
    assertThat(flatNumber).isSameInstanceAs(flatNumberObject);
  }

  @Test
  public void flatTypes_areCached() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    JSType fooType = this.registry.createObjectType("Foo", null);
    FlatType flatFoo = flattener.flatten(fooType);

    // When
    FlatType flatFooAgain = flattener.flatten(fooType);

    // Then
    assertThat(flatFooAgain).isSameInstanceAs(flatFoo);
  }

  @Test
  public void flatTypes_areTracked() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    ImmutableSet<JSType> sampleTypes =
        ImmutableSet.of(
            this.registry.getNativeType(JSTypeNative.NUMBER_TYPE),
            this.registry.createObjectType("Foo", null),
            this.registry.createTemplatizedType(
                this.registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), ImmutableList.of()));

    // When
    ImmutableSet<FlatType> flatSamples =
        sampleTypes.stream().map(flattener::flatten).collect(toImmutableSet());
    ImmutableSet<FlatType> allKnownTypes = flattener.getAllKnownTypes();

    // Then
    assertThat(allKnownTypes).containsAtLeastElementsIn(flatSamples);
  }

  @Test
  public void flatTypes_areTracked_insideUnions() {
    TypeFlattener flattener = this.createFlattener(null);

    UnionType sampleUnion =
        this.registry
            .createUnionType(
                this.registry.getNativeType(JSTypeNative.NUMBER_TYPE),
                this.registry.createObjectType("Foo", null),
                this.registry.createTemplatizedType(
                    this.registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), ImmutableList.of()))
            .toMaybeUnionType();

    // When
    FlatType flatSampleUnion = flattener.flatten(sampleUnion);
    ImmutableSet<FlatType> allKnownTypes = flattener.getAllKnownTypes();

    // Then
    ImmutableSet<FlatType> flatSampleUnionMembers =
        sampleUnion.getAlternates().stream().map(flattener::flatten).collect(toImmutableSet());

    assertThat(allKnownTypes).contains(flatSampleUnion);
    assertThat(allKnownTypes).containsAtLeastElementsIn(flatSampleUnionMembers);
  }

  @Test
  public void flatTypeIds_areUnique() {
    // Given
    TypeFlattener flattener = this.createFlattener(null);

    ImmutableSet<JSType> sampleTypes =
        ImmutableSet.of(
            this.registry.getNativeType(JSTypeNative.NUMBER_TYPE),
            this.registry.createObjectType("Foo", null),
            this.registry.createTemplatizedType(
                this.registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), ImmutableList.of()));

    // When
    ImmutableSet<FlatType> flatSamples =
        sampleTypes.stream().map(flattener::flatten).collect(toImmutableSet());

    // Then
    assertThat(flatSamples.stream().map(FlatType::getId).collect(toImmutableSet()))
        .containsNoDuplicates();
  }

  @Test
  public void flatTypeIds_areDeterministic() {
    // Given
    ImmutableSet<FlatType> sampleA = this.createSampleFlatTypesForDeterminismTest();
    ImmutableSet<FlatType> sampleB = this.createSampleFlatTypesForDeterminismTest();

    // Then
    assertThat(sampleA)
        .comparingElementsUsing(ID_MATCH)
        .containsExactlyElementsIn(sampleB)
        .inOrder();
    assertThat(sampleA)
        .comparingElementsUsing(JSCompCorrespondences.referenceEquality())
        .containsNoneIn(sampleB);
  }

  private ImmutableSet<FlatType> createSampleFlatTypesForDeterminismTest() {
    TypeFlattener flattener = this.createFlattener(null);

    ImmutableSet<JSType> sampleTypes =
        ImmutableSet.of(
            this.registry.getNativeType(JSTypeNative.NUMBER_TYPE),
            this.registry.createObjectType("Foo", null),
            this.registry.createTemplatizedType(
                this.registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), ImmutableList.of()));

    return sampleTypes.stream().map(flattener::flatten).collect(toImmutableSet());
  }

  private TypeFlattener createFlattener(@Nullable Predicate<JSType> isInvalidating) {
    if (isInvalidating == null) {
      isInvalidating = (t) -> false;
    }

    return new TypeFlattener(this.registry, isInvalidating);
  }

  private final Correspondence<FlatType, JSTypeNative> isBoxingTypeFor =
      Correspondence.transforming(
          (f) -> f.getTypeSingle(),
          (n) -> this.registry.getNativeType(n).autobox(),
          "is a boxing object type for");

  private static final Correspondence<FlatType, FlatType> ID_MATCH =
      Correspondence.transforming(FlatType::getId, FlatType::getId, "has same ID as");
}
