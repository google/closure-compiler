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
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.disambiguate.ColorGraphBuilder.EdgeReason.ALGEBRAIC;
import static com.google.javascript.jscomp.disambiguate.ColorGraphBuilder.EdgeReason.CAN_HOLD;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.truth.Correspondence;
import com.google.common.truth.TableSubject;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.disambiguate.ColorGraphBuilder.EdgeReason;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.nullness.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ColorGraphBuilderTest extends CompilerTestCase {

  @Rule @GwtIncompatible public final TestName testName = new TestName();

  private final Compiler compiler = new Compiler();

  /**
   * This registry is only used to lookup box colors so it doesn't have to be the same one used by
   * the graph builder.
   */
  private final ColorGraphNodeFactory graphNodeFactory =
      ColorGraphNodeFactory.createFactory(
          ColorRegistry.builder().setDefaultNativeColorsForTesting().build());

  private @Nullable CompilerPass processor;
  private DiGraph<ColorGraphNode, Object> result;
  private LinkedHashMap<String, ColorId> labelToId;

  @Override
  protected Compiler createCompiler() {
    return this.compiler;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    assertThat(compiler).isSameInstanceAs(this.compiler);
    return this.processor;
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    this.enableTypeCheck();
    this.replaceTypesWithColors();
    this.disableCompareJsDoc(); // type -> color conversion also erases JSDoc
  }

  private ColorGraphBuilder createBuilder(
      @Nullable StubLcaFinder optLcaFinder, ColorRegistry registry) {
    StubLcaFinder lcaFinder = (optLcaFinder == null) ? new StubLcaFinder() : optLcaFinder;
    return new ColorGraphBuilder(this.graphNodeFactory, lcaFinder::setGraph, registry);
  }

  private ColorGraphBuilder createBuilderIncludingCode(
      // takes a Supplier<StubLcaFinder> so that callers may lazily create this StubLcaFinder after
      // the labelToId map has been populated.
      @Nullable Supplier<StubLcaFinder> optLcaFinder, String src) {
    ColorGraphNodeFactory graphNodeFactory = this.graphNodeFactory;
    LinkedHashMap<String, ColorGraphNode> testTypes = new LinkedHashMap<>();
    this.labelToId = new LinkedHashMap<>();

    /* Flatten and collect the types of all NAMEs that start with "test". */
    this.processor =
        (externs, main) ->
            NodeTraversal.traverse(
                this.compiler,
                main,
                new AbstractPostOrderCallback() {
                  @Override
                  public void visit(NodeTraversal t, Node n, Node unused) {
                    if (n.isName() && n.getString().startsWith("test")) {
                      testTypes.put(n.getString(), graphNodeFactory.createNode(n.getColor()));
                    }

                    if (n.isLabel()) {
                      String labelName = n.getFirstChild().getString();
                      Node labeledExpr =
                          n.getSecondChild().isExprResult()
                              ? n.getSecondChild().getOnlyChild()
                              : n.getSecondChild();
                      labelToId.put(labelName, labeledExpr.getColor().getId());
                    }
                  }
                });
    this.testSame(srcs(src));

    this.processor = null;

    ColorGraphBuilder graphBuilder =
        this.createBuilder(
            optLcaFinder != null ? optLcaFinder.get() : null, this.compiler.getColorRegistry());
    graphBuilder.addAll(testTypes.values());
    return graphBuilder;
  }

  @Test
  public void top_isAboveInterface() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "/** @interface */", //
                "class IFoo { }",
                "",
                "let /** !IFoo */ test;",
                "",
                "IFOO_PROTOTYPE: IFoo.prototype",
                "IFOO: test;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(StandardColors.TOP_OBJECT.getId(), id("IFOO_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("IFOO_PROTOTYPE"), id("IFOO"), CAN_HOLD);
  }

  @Test
  public void prototypeChain_isInserted() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "class Foo { }", //
                "",
                "const test = new Foo();",
                "",
                "FOO_PROTOTYPE: Foo.prototype;",
                "FOO: new Foo();"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(StandardColors.TOP_OBJECT.getId(), id("FOO_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO_PROTOTYPE"), id("FOO"), CAN_HOLD);
  }

  @Test
  public void prototypeChain_isInserted_throughExtends() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "class Foo { }", //
                "class Bar extends Foo { }",
                "",
                "const test = new Bar();",
                "",
                "FOO_PROTOTYPE: Foo.prototype;",
                "FOO: new Foo();",
                "BAR_PROTOTYPE: Bar.prototype;",
                "FOO_PROTOTYPE: Foo.prototype;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(StandardColors.TOP_OBJECT.getId(), id("FOO_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO_PROTOTYPE"), id("FOO"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO"), id("BAR_PROTOTYPE"), CAN_HOLD);
  }

  @Test
  public void prototypeChain_canBranch() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "class Foo { }", //
                "class Bar extends Foo { }", //
                "class Qux extends Foo { }", //
                "",
                "const testBar = new Bar();",
                "const testQux = new Qux();",
                "",
                "FOO_PROTOTYPE: Foo.prototype;",
                "FOO: new Foo();",
                "BAR_PROTOTYPE: Bar.prototype;",
                "BAR: new Bar();",
                "QUX_PROTOTYPE: Qux.prototype;",
                "QUX: new Qux();"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(id("FOO_PROTOTYPE"), id("FOO"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO"), id("BAR_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("BAR_PROTOTYPE"), id("BAR"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO"), id("QUX_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("QUX_PROTOTYPE"), id("QUX"), CAN_HOLD);
  }

  @Test
  public void constructorDef_includesPrototypeAndInstanceType_evenIfUnused() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "class Foo { }", //
                "",
                "const test = Foo;",
                "",
                "FOO_PROTOTYPE: Foo.prototype;",
                "FOO: new Foo();",
                "BAR_PROTOTYPE: Bar.prototype;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(StandardColors.TOP_OBJECT.getId(), id("FOO_PROTOTYPE"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO_PROTOTYPE"), id("FOO"), CAN_HOLD);
  }

  @Test
  public void prototypeChain_connectsInterfaces() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "/** @interface */", //
                "class IFoo { }",
                "",
                "let /** !IFoo */ test;",
                "",
                "IFOO: test;",
                "IFOO_PROTOTYPE: IFoo.prototype;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(id("IFOO_PROTOTYPE"), id("IFOO"), CAN_HOLD);
  }

  @Test
  public void prototypeChain_connectsSubclasses_viaClassSideInheritance() {
    // Given
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            null,
            lines(
                "/** @constructor */ function Foo0 () { }",
                "class Foo1 extends Foo0 { }", //
                "class Foo2 extends Foo1 { }",
                "",
                "let /** !(typeof Foo2) */ test;",
                "FOO0_CTOR: Foo0;",
                "FOO1_CTOR: Foo1;",
                "FOO2_CTOR: Foo2;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(StandardColors.TOP_OBJECT.getId(), id("FOO0_CTOR"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO0_CTOR"), id("FOO1_CTOR"), CAN_HOLD);
    this.assertThatResultAsTable().containsCell(id("FOO1_CTOR"), id("FOO2_CTOR"), CAN_HOLD);
  }

  @Test
  public void disambiguationSupertypes_createConnection() {
    // Given
    Color parent = colorWithId(ColorId.fromUnsigned(100)).build();
    Color child = colorWithId(ColorId.fromUnsigned(101)).build();

    ColorGraphBuilder builder =
        this.createBuilder(
            null,
            ColorRegistry.builder()
                .setDefaultNativeColorsForTesting()
                .addDisambiguationEdge(child, parent)
                .build());

    builder.add(graphNodeFactory.createNode(child));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(parent.getId(), child.getId(), CAN_HOLD);
  }

  @Test
  public void unions_connectedAboveMembers() {
    // Given
    Supplier<StubLcaFinder> stubFinder =
        () ->
            new StubLcaFinder()
                .addStub(
                    ImmutableSet.of(id("FOO"), id("BAR"), id("QUX")),
                    ImmutableSet.of(id("FOO", "BAR", "QUX"), StandardColors.UNKNOWN.getId()));
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            stubFinder,
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;",
                "",
                "FOO: new Foo();",
                "BAR: new Bar();",
                "QUX: new Qux();"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(id("FOO", "BAR", "QUX"), id("BAR"), ALGEBRAIC);
    this.assertThatResultAsTable().containsCell(id("FOO", "BAR", "QUX"), id("FOO"), ALGEBRAIC);
    this.assertThatResultAsTable().containsCell(id("FOO", "BAR", "QUX"), id("QUX"), ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca() {
    // Given
    ColorId kifId = ColorId.fromUnsigned(100);
    Supplier<StubLcaFinder> stubFinder =
        () ->
            new StubLcaFinder()
                .addStub(
                    ImmutableSet.of(id("FOO"), id("BAR"), id("QUX")),
                    ImmutableSet.of(id("FOO", "BAR", "QUX"), kifId));
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            stubFinder,
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;",
                "",
                "FOO: new Foo();",
                "BAR: new Bar();",
                "QUX: new Qux();"));
    builder.add(this.graphNodeFactory.createNode(colorWithId(kifId).build()));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(kifId, id("FOO", "BAR", "QUX"), ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca_withMultipleLcas() {
    // Given
    ColorId kixId = ColorId.fromUnsigned(100);
    ColorId lopId = ColorId.fromUnsigned(101);
    Supplier<StubLcaFinder> stubFinder =
        () ->
            new StubLcaFinder()
                .addStub(
                    ImmutableSet.of(id("FOO"), id("BAR"), id("QUX")),
                    ImmutableSet.of(id("FOO", "BAR", "QUX"), kixId, lopId));
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            stubFinder,
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;",
                "",
                "FOO: new Foo();",
                "BAR: new Bar();",
                "QUX: new Qux();"));
    builder.add(this.graphNodeFactory.createNode(colorWithId(kixId).build()));
    builder.add(this.graphNodeFactory.createNode(colorWithId(lopId).build()));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(kixId, id("BAR", "FOO", "QUX"), ALGEBRAIC);
    this.assertThatResultAsTable().containsCell(lopId, id("BAR", "FOO", "QUX"), ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca_whichIsAlsoUnion() {
    // Given
    ColorId kifId = ColorId.fromUnsigned(100);
    Supplier<StubLcaFinder> stubFinder =
        () ->
            new StubLcaFinder()
                .addStub(
                    ImmutableSet.of(id("FOO"), id("BAR"), id("QUX")),
                    ImmutableSet.of(id("FOO", "BAR", "QUX"), kifId))
                .addStub(
                    ImmutableSet.of(id("FOO"), id("BAR")),
                    ImmutableSet.of(id("FOO", "BAR"), id("FOO", "BAR", "QUX")));
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            stubFinder,
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ testA;",
                "let /** (!Foo|!Bar) */ testB;",
                "",
                "FOO: new Foo();",
                "BAR: new Bar();",
                "QUX: new Qux();"));
    builder.add(this.graphNodeFactory.createNode(colorWithId(kifId).build()));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell(kifId, id("FOO", "BAR", "QUX"), ALGEBRAIC);
    this.assertThatResultAsTable()
        .containsCell(id("FOO", "BAR", "QUX"), id("FOO", "BAR"), ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLac_whichHasSameDescendantCount() {
    // Given
    Supplier<StubLcaFinder> stubFinder =
        () ->
            new StubLcaFinder()
                .addStub(
                    ImmutableSet.of(id("FOO_PROTOTYPE"), id("BAR_PROTOTYPE")),
                    ImmutableSet.of(id("FOO_PROTOTYPE", "BAR_PROTOTYPE"), id("KIF")));
    ColorGraphBuilder builder =
        this.createBuilderIncludingCode(
            stubFinder,
            lines(
                "class Kif { }",
                "class Foo extends Kif { }",
                "class Bar extends Kif { }",
                "",
                "let /** ((typeof Foo.prototype)|(typeof Bar.prototype)) */ test;",
                "",
                "KIF: new Kif();",
                "FOO_PROTOTYPE: Foo.prototype;",
                "BAR_PROTOTYPE: Bar.prototype;"));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable()
        .containsCell(id("KIF"), id("FOO_PROTOTYPE", "BAR_PROTOTYPE"), ALGEBRAIC);

    // Also check post-conditions.
  }

  @After
  public void verifyResult_topNodeIsOnlyRoot() {
    assertThat(
            this.result.getNodes().stream()
                .filter((n) -> n.getInEdges().isEmpty())
                .collect(toImmutableSet()))
        .comparingElementsUsing(NODE_HAS_ID)
        .containsExactly(StandardColors.UNKNOWN.getId());
  }

  @After
  public void verifyResult_hasNoSelfEdges() {
    assertThat(
            this.result.getEdges().stream()
                .filter((e) -> Objects.equals(e.getSource(), e.getDestination()))
                .collect(toImmutableSet()))
        .isEmpty();
  }

  @After
  public void verifyResult_hasNoParallelEdges() {
    assertThat(this.result.getEdges())
        .comparingElementsUsing(
            Correspondence
                .<DiGraphEdge<ColorGraphNode, Object>, DiGraphEdge<ColorGraphNode, Object>>from(
                    (a, e) ->
                        Objects.equals(a.getSource(), e.getSource())
                            && Objects.equals(a.getDestination(), e.getDestination())
                            && !Objects.equals(a, e),
                    "is parallel to"))
        .containsNoneIn(this.result.getEdges());
  }

  @After
  @GwtIncompatible
  public void renderResultGraph() {
  }

  private TableSubject assertThatResultAsTable() {
    ImmutableTable.Builder<ColorId, ColorId, EdgeReason> table = ImmutableTable.builder();
    for (DiGraphEdge<ColorGraphNode, Object> edge : this.result.getEdges()) {
      table.put(
          nameOf(edge.getSource()), nameOf(edge.getDestination()), (EdgeReason) edge.getValue());
    }
    return assertThat(table.buildOrThrow());
  }

  /**
   * A fake implementation of a finder.
   *
   * <p>Instances allow setting stub responses for {@code findAll} calls. Inputs an outputs are
   * specfied using sets of type names.
   */
  private static final class StubLcaFinder
      extends LowestCommonAncestorFinder<ColorGraphNode, Object> {
    private DiGraph<ColorGraphNode, Object> graph;
    private final LinkedHashMap<ImmutableSet<ColorId>, ImmutableSet<ColorId>> stubs =
        new LinkedHashMap<>();
    private final LinkedHashMap<Runnable, Integer> preconditions = new LinkedHashMap<>();

    StubLcaFinder() {
      super(null);
    }

    StubLcaFinder setGraph(DiGraph<ColorGraphNode, Object> graph) {
      this.graph = graph;
      return this;
    }

    StubLcaFinder addStub(ImmutableSet<ColorId> from, ImmutableSet<ColorId> to) {
      this.stubs.put(from, to);
      return this;
    }

    @Override
    public ImmutableSet<ColorGraphNode> findAll(Set<ColorGraphNode> roots) {
      for (Map.Entry<Runnable, Integer> entry : this.preconditions.entrySet()) {
        entry.getKey().run();
        entry.setValue(entry.getValue() - 1);
      }

      ImmutableSet<ColorId> rootIds =
          roots.stream().map(ColorGraphBuilderTest::nameOf).collect(toImmutableSet());
      assertThat(this.stubs).containsKey(rootIds);
      ImmutableSet<ColorId> resultNames = this.stubs.get(rootIds);

      ImmutableSet<ColorGraphNode> results =
          this.graph.getNodes().stream()
              .map(DiGraphNode::getValue)
              .filter((t) -> resultNames.contains(nameOf(t)))
              .collect(toImmutableSet());
      assertThat(results).hasSize(resultNames.size());

      return results;
    }
  }

  private static ColorId nameOf(DiGraphNode<ColorGraphNode, Object> node) {
    return nameOf(node.getValue());
  }

  private static ColorId nameOf(ColorGraphNode flat) {
    return flat.getColor().getId();
  }

  private static Color.Builder colorWithId(ColorId id) {
    return Color.singleBuilder().setId(id);
  }

  private static final Correspondence<DiGraphNode<ColorGraphNode, Object>, ColorId> NODE_HAS_ID =
      Correspondence.transforming(ColorGraphBuilderTest::nameOf, "in a node with type");

  private ColorId id(String labelName) {
    return Preconditions.checkNotNull(
        this.labelToId.get(labelName), "Could not find label %s", labelName);
  }

  private ColorId id(String... idSources) {
    ImmutableSet.Builder<ColorId> ids = ImmutableSet.builder();
    for (String idSource : idSources) {
      ids.add(id(idSource));
    }
    return ColorId.union(ids.build());
  }
}
