/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.graph;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LowestCommonAncestorFinderTest {

  @Test
  public void findAll_onTree_withCommonParent() {
    new CaseBuilder() //
        .edge(3, 1)
        .edge(3, 2)
        .roots(1, 2)
        .expect(3)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onTree_withCommonGrandParent() {
    new CaseBuilder() //
        .edge(5, 4)
        .edge(5, 3)
        .edge(4, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect(5)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onTree_whenLcaHasParent() {
    new CaseBuilder() //
        .edge(4, 3)
        .edge(3, 1)
        .edge(3, 2)
        .roots(1, 2)
        .expect(3)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDag_withNestedVs_findsSingleLca() {
    new CaseBuilder() //
        .edge(4, 3)
        .edge(4, 2)
        .edge(4, 1)
        .edge(3, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect(3)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDag_findsMultipleLcas() {
    new CaseBuilder() //
        .edge(4, 2)
        .edge(4, 1)
        .edge(3, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect(3, 4)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDag_withCommonParent_findsMultipleLcas() {
    new CaseBuilder() //
        .edge(5, 4)
        .edge(5, 3)
        .edge(4, 2)
        .edge(4, 1)
        .edge(3, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect(3, 4)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDag_withCommonParent_findsMultipleLcas_atDifferentDistances() {
    new CaseBuilder()
        .edge(10, 3)
        .edge(10, 9)
        .edge(9, 8)
        .edge(9, 7)
        .edge(8, 6)
        .edge(7, 5)
        .edge(6, 1)
        .edge(5, 2)
        .edge(3, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect(3, 9)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDag_sidePathsIntoNonLowest() {
    new CaseBuilder()
        .edge(20, 18)
        .edge(20, 19)
        .edge(19, 17)
        .edge(19, 12)
        .edge(18, 11)
        .edge(17, 15)
        .edge(16, 15)
        .edge(15, 14)
        .edge(15, 2)
        .edge(14, 1)
        .edge(12, 9)
        .edge(11, 10)
        .edge(10, 1)
        .edge(9, 2)
        .roots(1, 2)
        .expect(15)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onCyclic_whenLcaInsideCycle_findsSomeMember_deterministically() {
    new CaseBuilder()
        .edge(1, 2)
        .edge(2, 3)
        .edge(3, 4)
        .edge(4, 1)
        .edge(1, 5)
        .edge(2, 6)
        .roots(5, 6)
        .expect(1)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onCyclic_whenLcaInsideCycle_appearsUnique_mayFindOtherMember() {
    new CaseBuilder()
        .edge(1, 2)
        .edge(2, 3)
        .edge(3, 4)
        .edge(4, 1)
        .edge(1, 5)
        // Notice how 3 is a parent of both 5 and 6, yet isn't selected.
        .edge(3, 5)
        .edge(3, 6)
        .roots(5, 6)
        .expect(1)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onCyclic_whenLcaBelowCycle_ignoresCycle() {
    new CaseBuilder()
        .edge(1, 2)
        .edge(2, 3)
        .edge(3, 1)
        .edge(3, 4)
        .edge(4, 5)
        .edge(4, 6)
        .roots(5, 6)
        .expect(4)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDigitLattice_withUnrelatedRoots_findsMultipleLcas() {
    new CaseBuilder() //
        .copyGraph(DIGIT_SUBSTRING_LATTICE)
        .roots(1, 3, 5)
        .expect(135, 153, 315, 351, 531, 513)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDigitLattice_withRelatedRoots_findsMultipleLcas() {
    new CaseBuilder() //
        .copyGraph(DIGIT_SUBSTRING_LATTICE)
        .roots(1, 13, 5)
        .expect(135, 513)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDigitLattice_withOverlappingRoots_findsSingleLca() {
    new CaseBuilder() //
        .copyGraph(DIGIT_SUBSTRING_LATTICE)
        .roots(7, 4, 8, 48, 74)
        .expect(748)
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDigitLattice_noLca() {
    new CaseBuilder()
        .copyGraph(DIGIT_SUBSTRING_LATTICE) //
        .roots(2, 4, 7, 8)
        .expect()
        .testAndRenderGraph();
  }

  @Test
  public void findAll_onDisjoint_noSolution() {
    new CaseBuilder() //
        .edge(4, 2)
        .edge(3, 1)
        .roots(1, 2)
        .expect()
        .testAndRenderGraph();
  }

  @Test
  public void findAll_rejectsTooManyRoots() {
    // Given
    CaseBuilder builder = new CaseBuilder().expect(40);
    Integer[] roots = new Integer[Integer.SIZE];
    for (int i = 0; i < roots.length; i++) {
      builder.edge(40, roots[i] = i);
    }
    builder.roots(roots);

    // Then
    assertThrows(Exception.class, builder::testAndRenderGraph);
  }

  private static final class CaseBuilder {
    private final LinkedDirectedGraph<Integer, Void> graph = LinkedDirectedGraph.create();
    private ImmutableSet<Integer> roots;
    private ImmutableSet<Integer> expected;
    private ImmutableSet<Integer> actual;

    CaseBuilder edge(int src, int dest) {
      this.graph.createNode(src);
      this.graph.createNode(dest);
      this.graph.connect(src, null, dest);
      assertThat(this.graph.getEdges(src, dest)).hasSize(1);
      return this;
    }

    CaseBuilder copyGraph(LinkedDirectedGraph<Integer, Void> src) {
      for (DiGraphEdge<Integer, Void> edge : src.getEdges()) {
        this.edge(edge.getSource().getValue(), edge.getDestination().getValue());
      }
      return this;
    }

    CaseBuilder roots(Integer... roots) {
      checkState(this.roots == null);
      this.roots = ImmutableSet.copyOf(roots);
      return this;
    }

    CaseBuilder expect(Integer... expected) {
      checkState(this.expected == null);
      this.expected = ImmutableSet.copyOf(expected);
      return this;
    }

    void testAndRenderGraph() {
      checkState(this.actual == null);
      LowestCommonAncestorFinder<Integer, Void> finder =
          new LowestCommonAncestorFinder<>(this.graph);
      this.actual = finder.findAll(this.roots);

      String message =
          this.renderGraph() ? "Look for an SVG of the test graph in the test artifacts" : "";
      assertWithMessage(message).that(this.actual).containsExactlyElementsIn(this.expected);
    }

    private boolean renderGraph() {
      if (this.graph.getNodeCount() > 100) {
        return false; // This graph won't be readable anyway.
      }

      for (DiGraphNode<Integer, Void> node : this.graph.getNodes()) {
        NodePurpose purpose = NodePurpose.NONE;
        if (this.roots.contains(node.getValue())) {
          purpose = purpose.mix(NodePurpose.ROOT);
        }
        if (this.expected.contains(node.getValue())) {
          purpose = purpose.mix(NodePurpose.EXPECTED);
        }
        if (this.actual.contains(node.getValue())) {
          purpose = purpose.mix(NodePurpose.ACTUAL);
        }
        node.setAnnotation(purpose);
      }

      return true;
    }
  }

  private enum NodePurpose implements Annotation {
    // The order of these elements it loadbearing.
    NONE,
    ACTUAL,
    EXPECTED,
    ACTUAL_EXPECTED,
    ROOT,
    ACTUAL_ROOT,
    EXPECTED_ROOT,
    ACTUAL_EXPECTED_ROOT;

    NodePurpose mix(NodePurpose x) {
      return values()[this.ordinal() | x.ordinal()];
    }

    @Override
    public String toString() {
      return this.name();
    }
  }

  /**
   * A graph of integers in which each value (X) connects to a value (Y) if the decimal
   * representation of (Y) appears as a substring in the decimal representation of (X).
   *
   * <p>The largest element is 999. There are no transitive edges.
   *
   * <p>TODO(nickreid): Switch to a graph that's simpler to describe but has the same non-trivial
   * structural aspects.
   */
  private static final LinkedDirectedGraph<Integer, Void> DIGIT_SUBSTRING_LATTICE =
      makeDigitLattice();

  private static LinkedDirectedGraph<Integer, Void> makeDigitLattice() {
    LinkedDirectedGraph<Integer, Void> result = LinkedDirectedGraph.createWithoutAnnotations();

    for (int i = 0; i < 1000; i++) {
      String iStr = String.valueOf(i);
      int windowSize = iStr.length() - 1;

      result.createNode(i);

      if (windowSize == 0) {
        continue;
      }
      for (int offset = 0; offset + windowSize <= iStr.length(); offset++) {
        String descStr = iStr.substring(offset, offset + windowSize);
        result.connectIfNotConnectedInDirection(i, null, Integer.parseInt(descStr));
      }
    }

    return result;
  }
}
