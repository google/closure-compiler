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
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClusterPropagatorTest {

  private final ClusterPropagator propagator = new ClusterPropagator();

  private final ColorGraphNode src = ColorGraphNode.createForTesting(-1);
  private final ColorGraphNode dest = ColorGraphNode.createForTesting(-2);

  private final PropertyClustering prop = new PropertyClustering("prop");

  private boolean result;

  @Test
  public void propagation_mergesProperties_fromSrcToDest() {
    // Given
    associate(this.prop, this.src);

    // When
    this.propagateFromSrcToDest();

    // Then
    assertThat(this.result).isTrue();
  }

  @Test
  public void propagation_mergesProperties_fromSrc_thatExistOnDest() {
    // Given
    associate(this.prop, this.src);
    associate(this.prop, this.dest);

    // When
    this.propagateFromSrcToDest();

    // Then
    assertThat(this.result).isFalse();
  }

  @Test
  public void propagation_doesNotMergeProperties_fromDestToSrc() {
    // Given
    associate(this.prop, this.dest);

    // When
    this.propagateFromSrcToDest();

    // Then
    assertThat(this.result).isFalse();
    assertThat(this.src.getAssociatedProps()).isEmpty();
  }

  @Test
  public void propagation_doesNotMergeProperties_thatAreInvalidated() {
    // Given
    associate(this.prop, this.src);
    this.prop.invalidate(Invalidation.wellKnownProperty());

    // When
    this.propagateFromSrcToDest();

    // Then
    assertThat(this.result).isFalse();
    assertThat(this.dest.getAssociatedProps()).isEmpty();
  }

  @After
  public void verifyPropertyFlow() {
    ImmutableSet<PropertyClustering> validSrcProps =
        this.src.getAssociatedProps().keySet().stream()
            .filter((p) -> !p.isInvalidated())
            .collect(toImmutableSet());

    assertThat(this.dest.getAssociatedProps().keySet()).containsAtLeastElementsIn(validSrcProps);
  }

  @After
  public void verifyClusterMerging() {
    if (this.prop.isInvalidated()) {
      return;
    }

    if (areAssociated(this.prop, this.src) && areAssociated(this.prop, this.dest)) {
      assertThat(this.prop.getClusters().areEquivalent(this.src, this.dest)).isTrue();
    }
  }

  private static boolean areAssociated(PropertyClustering prop, ColorGraphNode flat) {
    if (prop.getClusters().elements().contains(flat)) {
      assertThat(flat.getAssociatedProps()).containsKey(prop);
      return true;
    }

    assertThat(flat.getAssociatedProps()).doesNotContainKey(prop);
    return false;
  }

  private static void associate(PropertyClustering prop, ColorGraphNode node) {
    node.getAssociatedProps().put(prop, ColorGraphNode.PropAssociation.AST);
    prop.getClusters().add(node);
  }

  private void propagateFromSrcToDest() {
    this.result = this.propagator.traverseEdge(this.src, null, this.dest);
  }
}
