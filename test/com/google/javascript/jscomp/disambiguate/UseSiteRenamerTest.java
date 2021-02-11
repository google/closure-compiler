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
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UseSiteRenamerTest {

  private static final String PROP_NAME = "prop";

  private final ArrayList<JSError> reportedErrors = new ArrayList<>();
  private final LinkedHashSet<Node> reportedMutations = new LinkedHashSet<>();

  private final PropertyClustering prop = new PropertyClustering(PROP_NAME);

  private ImmutableSetMultimap<String, String> renamingIndex;

  @After
  public void verifyRenamingIndex_containsExactlyNewNames() {
    if (this.prop.isInvalidated()) {
      assertThat(this.renamingIndex).containsExactly(PROP_NAME, "<INVALIDATED>");
      return;
    }

    assertThat(this.renamingIndex.values())
        .containsExactlyElementsIn(namesOf(this.prop.getUseSites().keySet()));
  }

  @After
  public void verifyNewNames_areReportedAsMutations() {
    if (this.prop.isInvalidated()) {
      return;
    }

    ImmutableSet<Node> mutatedNodes =
        this.prop.getUseSites().keySet().stream()
            .filter((n) -> !Objects.equals(n.getString(), PROP_NAME))
            .collect(toImmutableSet());

    assertThat(this.reportedMutations).containsExactlyElementsIn(mutatedNodes);
  }

  @After
  public void verifyNoUnexpectedErrors() {
    assertThat(this.reportedErrors).isEmpty();
  }

  @Test
  public void renameUses_renamesConsistently_withinEachCluster() {
    // Given
    Node name1a = IR.name(PROP_NAME);
    Node name1b = IR.name(PROP_NAME);
    Node name1c = IR.name(PROP_NAME);
    Node name2 = IR.name(PROP_NAME);

    ColorGraphNode type1a = ColorGraphNode.createForTesting(-1);
    ColorGraphNode type1b = ColorGraphNode.createForTesting(-2);
    ColorGraphNode type1c = ColorGraphNode.createForTesting(-3);
    ColorGraphNode type2 = ColorGraphNode.createForTesting(-4);

    this.prop.getUseSites().put(name1a, type1a);
    this.prop.getUseSites().put(name1b, type1b);
    this.prop.getUseSites().put(name1c, type1c);
    this.prop.getUseSites().put(name2, type2);

    this.prop.getClusters().union(type1a, type1b);
    this.prop.getClusters().union(type1a, type1c);
    this.prop.getClusters().add(type2);

    // When
    this.runRename(null);

    // Then
    assertThat(name1b.getString()).isEqualTo(name1a.getString());
    assertThat(name1c.getString()).isEqualTo(name1a.getString());
    assertThat(name1a.getString()).isNotEqualTo(PROP_NAME);
  }

  @Test
  public void renameUses_renamesDistinctly_betweenEachCluster() {
    // Given
    Node name1 = IR.name(PROP_NAME);
    Node name2 = IR.name(PROP_NAME);
    Node name3 = IR.name(PROP_NAME);

    ColorGraphNode type1 = ColorGraphNode.createForTesting(-1);
    ColorGraphNode type2 = ColorGraphNode.createForTesting(-2);
    ColorGraphNode type3 = ColorGraphNode.createForTesting(-3);

    this.prop.getUseSites().put(name1, type1);
    this.prop.getUseSites().put(name2, type2);
    this.prop.getUseSites().put(name3, type3);

    this.prop.getClusters().add(type1);
    this.prop.getClusters().add(type2);
    this.prop.getClusters().add(type3);

    // When
    this.runRename(null);

    // Then
    assertThat(name1.getString()).isNotEqualTo(name2.getString());
    assertThat(name1.getString()).isNotEqualTo(name3.getString());
    assertThat(name2.getString()).isNotEqualTo(name3.getString());
  }

  @Test
  public void renameUses_doesntRename_externsCluster() {
    // Given
    Node externName1 = IR.name(PROP_NAME);
    Node externName2 = IR.name(PROP_NAME);
    Node externName3 = IR.name(PROP_NAME);
    Node srcName = IR.name(PROP_NAME);

    ColorGraphNode externType1 = ColorGraphNode.createForTesting(-1);
    ColorGraphNode externType2 = ColorGraphNode.createForTesting(-2);
    ColorGraphNode externType3 = ColorGraphNode.createForTesting(-3);
    ColorGraphNode srcType = ColorGraphNode.createForTesting(-4);

    this.prop.getUseSites().put(externName1, externType1);
    this.prop.getUseSites().put(externName2, externType2);
    this.prop.getUseSites().put(externName3, externType3);
    this.prop.getUseSites().put(srcName, srcType);

    this.prop.registerOriginalNameType(externType1);
    this.prop.registerOriginalNameType(externType2);
    this.prop.registerOriginalNameType(externType3);

    this.prop.getClusters().add(srcType);

    // When
    this.runRename(null);

    // Then
    assertThat(externName1.getString()).isEqualTo(PROP_NAME);
    assertThat(externName2.getString()).isEqualTo(PROP_NAME);
    assertThat(externName3.getString()).isEqualTo(PROP_NAME);
    assertThat(srcName.getString()).isNotEqualTo(PROP_NAME);
  }

  @Test
  public void renameUses_doesntRename_invalidatedProps() {
    // Given
    Node name1 = IR.name(PROP_NAME);
    Node name2 = IR.name(PROP_NAME);
    Node name3 = IR.name(PROP_NAME);

    ColorGraphNode type1 = ColorGraphNode.createForTesting(-1);
    ColorGraphNode type2 = ColorGraphNode.createForTesting(-2);
    ColorGraphNode type3 = ColorGraphNode.createForTesting(-3);

    this.prop.getUseSites().put(name1, type1);
    this.prop.getUseSites().put(name2, type2);
    this.prop.getUseSites().put(name3, type3);

    this.prop.invalidate(Invalidation.wellKnownProperty());

    // When
    this.runRename(null);

    // Then
    assertThat(name1.getString()).isEqualTo(PROP_NAME);
    assertThat(name1.getString()).isEqualTo(PROP_NAME);
    assertThat(name2.getString()).isEqualTo(PROP_NAME);
  }

  @Test
  public void renameUses_doesntRenameProp_whenThereIsOnlyOneCluster() {
    // Given
    Node name1a = IR.name(PROP_NAME);
    Node name1b = IR.name(PROP_NAME);
    Node name1c = IR.name(PROP_NAME);

    ColorGraphNode type1a = ColorGraphNode.createForTesting(-1);
    ColorGraphNode type1b = ColorGraphNode.createForTesting(-2);
    ColorGraphNode type1c = ColorGraphNode.createForTesting(-3);

    this.prop.getUseSites().put(name1a, type1a);
    this.prop.getUseSites().put(name1b, type1b);
    this.prop.getUseSites().put(name1c, type1c);

    this.prop.getClusters().union(type1a, type1b);
    this.prop.getClusters().union(type1a, type1c);

    // When
    this.runRename(null);

    // Then
    assertThat(name1b.getString()).isEqualTo(PROP_NAME);
    assertThat(name1c.getString()).isEqualTo(PROP_NAME);
    assertThat(name1a.getString()).isEqualTo(PROP_NAME);
  }

  @Test
  public void renameUses_reportsInvalidation() {
    // Given
    this.prop.invalidate(Invalidation.wellKnownProperty());

    // When
    this.runRename(ImmutableSet.of(PROP_NAME));

    // Then
    assertError(this.reportedErrors.remove(0))
        .hasType(DisambiguateProperties2.PROPERTY_INVALIDATION);
  }

  private void runRename(@Nullable ImmutableSet<String> propertiesThatMustDisambiguate) {
    if (propertiesThatMustDisambiguate == null) {
      propertiesThatMustDisambiguate = ImmutableSet.of();
    }

    UseSiteRenamer renamer =
        new UseSiteRenamer(
            propertiesThatMustDisambiguate, this.reportedErrors::add, this.reportedMutations::add);
    renamer.renameUses(this.prop);

    this.renamingIndex = renamer.getRenamingIndex();
  }

  private static ImmutableSet<String> namesOf(Collection<Node> useSites) {
    return useSites.stream().map(Node::getString).collect(toImmutableSet());
  }
}
