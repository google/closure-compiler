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

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ObjectColor;
import com.google.javascript.jscomp.colors.PrimitiveColor;
import com.google.javascript.jscomp.colors.UnionColor;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ColorGraphNodeFactoryTest {

  @Test
  public void topLikeTypes_flattenToTop() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();
    ColorGraphNode flatTop = factory.createNode(PrimitiveColor.UNKNOWN);

    // When & Then
    assertThat(factory.createNode((Color) null)).isSameInstanceAs(flatTop);
    assertThat(factory.createNode(PrimitiveColor.NULL_OR_VOID)).isSameInstanceAs(flatTop);
  }

  @Test
  public void primitiveTypes_flattenToTop() {
    // In the future, it might be feasible to instead autobox these primitives to their
    // corresponding object types.
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();
    ColorGraphNode flatTop = factory.createNode(PrimitiveColor.UNKNOWN);

    assertThat(factory.createNode(PrimitiveColor.STRING)).isSameInstanceAs(flatTop);
    assertThat(
            factory.createNode(
                UnionColor.create(ImmutableSet.of(PrimitiveColor.STRING, PrimitiveColor.NUMBER))))
        .isSameInstanceAs(flatTop);
  }

  @Test
  public void unionTypes_whenFlattened_dropNullAndUndefined() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();

    Color nullOrVoidOrNumberType =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.NULL_OR_VOID, PrimitiveColor.NUMBER));

    ColorGraphNode flatNumber = factory.createNode(PrimitiveColor.NUMBER);

    // Given
    ColorGraphNode flatNullOrVoidOrNumber = factory.createNode(nullOrVoidOrNumberType);

    // Then
    assertThat(flatNullOrVoidOrNumber).isSameInstanceAs(flatNumber);
  }

  @Test
  public void colorGraphNodes_areCached() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();

    Color fooType = ObjectColor.builder().setId("Foo").build();
    ColorGraphNode flatFoo = factory.createNode(fooType);

    // When
    ColorGraphNode flatFooAgain = factory.createNode(fooType);

    // Then
    assertThat(flatFooAgain).isSameInstanceAs(flatFoo);
  }

  @Test
  public void colorGraphNodes_areTracked() {
    // Given
    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            PrimitiveColor.NUMBER,
            ObjectColor.builder().setId("Foo").build(),
            ObjectColor.builder().setId("Bar").build());

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

    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            PrimitiveColor.NUMBER,
            ObjectColor.builder().setId("Foo").build(),
            ObjectColor.builder().setId("Bar").build());

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

    ColorGraphNodeFactory factory = ColorGraphNodeFactory.createFactory();

    ImmutableSet<Color> sampleTypes =
        ImmutableSet.of(
            PrimitiveColor.NUMBER,
            ObjectColor.builder().setId("Foo").build(),
            ObjectColor.builder().setId("Bar").build());

    return sampleTypes.stream().map(factory::createNode).collect(toImmutableSet());
  }

  private static final Correspondence<ColorGraphNode, ColorGraphNode> ID_MATCH =
      Correspondence.transforming(ColorGraphNode::getId, ColorGraphNode::getId, "has same ID as");
}
