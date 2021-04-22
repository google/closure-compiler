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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.colors.ColorId.fromAscii;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ColorGraphNodeFactoryTest {

  private ColorRegistry colorRegistry;

  @Before
  public void initRegistry() {
    this.colorRegistry = ColorRegistry.createForTesting();
  }

  @Test
  public void topLikeTypes_flattenToTop() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);
    ColorGraphNode flatTop = factory.createNode(colorRegistry.get(NativeColorId.UNKNOWN));

    // When & Then
    assertThat(factory.createNode((Color) null)).isSameInstanceAs(flatTop);
    assertThat(factory.createNode(colorRegistry.get(NativeColorId.NULL_OR_VOID)))
        .isSameInstanceAs(flatTop);
  }

  @Test
  public void primitiveTypes_flattenToBoxedType() {
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);
    ColorGraphNode flatString = factory.createNode(colorRegistry.get(NativeColorId.STRING));
    ColorGraphNode flatStringObject =
        factory.createNode(colorRegistry.get(NativeColorId.STRING_OBJECT));
    ColorGraphNode flatTop = factory.createNode(colorRegistry.get(NativeColorId.UNKNOWN));

    assertThat(flatString).isNotEqualTo(flatTop);
    assertThat(flatString).isSameInstanceAs(flatStringObject);

    assertThat(
            factory.createNode(
                Color.createUnion(
                    ImmutableSet.of(
                        colorRegistry.get(NativeColorId.STRING),
                        colorRegistry.get(NativeColorId.NUMBER)))))
        .isSameInstanceAs(
            factory.createNode(
                Color.createUnion(
                    ImmutableSet.of(
                        colorRegistry.get(NativeColorId.STRING_OBJECT),
                        colorRegistry.get(NativeColorId.NUMBER_OBJECT)))));
  }

  @Test
  public void unionTypes_whenFlattened_dropNullAndUndefined() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    Color nullOrVoidOrNumberType =
        Color.createUnion(
            ImmutableSet.of(
                colorRegistry.get(NativeColorId.NULL_OR_VOID),
                colorRegistry.get(NativeColorId.NUMBER)));

    ColorGraphNode flatNumber = factory.createNode(colorRegistry.get(NativeColorId.NUMBER));

    // Given
    ColorGraphNode flatNullOrVoidOrNumber = factory.createNode(nullOrVoidOrNumberType);

    // Then
    assertThat(flatNullOrVoidOrNumber).isSameInstanceAs(flatNumber);
  }

  @Test
  public void colorGraphNodes_areCached() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    Color fooType =
        Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Foo")).build());
    ColorGraphNode flatFoo = factory.createNode(fooType);

    // When
    ColorGraphNode flatFooAgain = factory.createNode(fooType);

    // Then
    assertThat(flatFooAgain).isSameInstanceAs(flatFoo);
  }

  @Test
  public void colorGraphNodes_areTracked() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            colorRegistry.get(NativeColorId.NUMBER),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Foo")).build()),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Bar")).build()));

    // When
    ImmutableSet<ColorGraphNode> flatSamples =
        sampleTypes.stream().map(factory::createNode).collect(toImmutableSet());
    ImmutableSet<ColorGraphNode> allKnownTypes = factory.getAllKnownTypes();

    // Then
    assertThat(allKnownTypes).containsAtLeastElementsIn(flatSamples);
  }

  @Test
  public void colorGraphNodeIds_areUnique() {
    // Given

    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            colorRegistry.get(NativeColorId.NUMBER),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Foo")).build()),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Bar")).build()));

    // When
    ImmutableSet<ColorGraphNode> flatSamples =
        sampleTypes.stream().map(factory::createNode).collect(toImmutableSet());

    // Then
    assertThat(flatSamples.stream().map(ColorGraphNode::getId).collect(toImmutableSet()))
        .containsNoDuplicates();
  }

  @Test
  public void colorGraphNodeIds_areDeterministic() {
    // Given
    ImmutableSet<ColorGraphNode> sampleA = this.createSampleColorGraphNodesForDeterminismTest();
    ImmutableSet<ColorGraphNode> sampleB = this.createSampleColorGraphNodesForDeterminismTest();

    // Then
    assertThat(sampleA)
        .comparingElementsUsing(ID_MATCH)
        .containsExactlyElementsIn(sampleB)
        .inOrder();
    assertThat(sampleA)
        .comparingElementsUsing(JSCompCorrespondences.referenceEquality())
        .containsNoneIn(sampleB);
  }

  private ImmutableSet<ColorGraphNode> createSampleColorGraphNodesForDeterminismTest() {

    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory(this.colorRegistry);

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            colorRegistry.get(NativeColorId.NUMBER),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Foo")).build()),
            Color.createSingleton(SingletonColorFields.builder().setId(fromAscii("Bar")).build()));

    return sampleTypes.stream().map(factory::createNode).collect(toImmutableSet());
  }

  private static final Correspondence<ColorGraphNode, ColorGraphNode> ID_MATCH =
      Correspondence.transforming(ColorGraphNode::getId, ColorGraphNode::getId, "has same ID as");
}
