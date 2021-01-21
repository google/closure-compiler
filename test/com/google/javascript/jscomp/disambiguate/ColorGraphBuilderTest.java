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
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.common.truth.TableSubject;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
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
import javax.annotation.Nullable;
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
  private final ColorGraphNodeFactory graphNodeFactory =
      ColorGraphNodeFactory.createFactory(
          ColorRegistry.createWithInvalidatingNatives(ImmutableSet.of(NativeColorId.UNKNOWN)));

  private CompilerPass processor;
  private DiGraph<ColorGraphNode, Object> result;

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

  private LinkedHashMap<String, ColorGraphNode> collectTypesFromCode(String src) {
    ColorGraphNodeFactory graphNodeFactory = this.graphNodeFactory;
    LinkedHashMap<String, ColorGraphNode> testTypes = new LinkedHashMap<>();

    /** Flatten and collect the types of all NAMEs that start with "test". */
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
                  }
                });
    this.testSame(srcs(src));
    this.processor = null;

    return testTypes;
  }

  @Test
  public void top_isAboveInterface() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "/** @interface */", //
                "class IFoo { }",
                "",
                "let /** !IFoo */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    // (Note that Object doesn't appear. This is because it's deserialized to UNKNOWN. If we begin
    // to deserialize Object to an actual known color, then this test will require an update.)
    this.assertThatResultAsTable().containsCell("UNKNOWN", "IFoo.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("IFoo.prototype", "IFoo", CAN_HOLD);
  }

  @Test
  public void prototypeChain_isInserted() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "",
                "const test = new Foo();"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("UNKNOWN", "Foo.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", CAN_HOLD);
  }

  @Test
  public void prototypeChain_isInserted_throughExtends() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "class Bar extends Foo { }",
                "",
                "const test = new Bar();"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("UNKNOWN", "Foo.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("Foo", "Bar.prototype", CAN_HOLD);
  }

  @Test
  public void prototypeChain_canBranch() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "class Bar extends Foo { }", //
                "class Qux extends Foo { }", //
                "",
                "const testBar = new Bar();",
                "const testQux = new Qux();"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("Foo", "Bar.prototype", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("Bar.prototype", "Bar", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("Foo", "Qux.prototype", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("Qux.prototype", "Qux", CAN_HOLD);
  }

  @Test
  public void constructorDef_includesPrototypeAndInstanceType_evenIfUnused() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "",
                "const test = Foo;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("UNKNOWN", "Foo.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", CAN_HOLD);
  }

  @Test
  public void prototypeChain_connectsInterfaces() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "/** @interface */", //
                "class IFoo { }",
                "",
                "let /** !IFoo */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("IFoo.prototype", "IFoo", CAN_HOLD);
  }

  @Test
  public void prototypeChain_connectsSubclasses_viaClassSideInheritance() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "/** @constructor */ function Foo0 () { }",
                "class Foo1 extends Foo0 { }", //
                "class Foo2 extends Foo1 { }",
                "",
                "let /** !(typeof Foo2) */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("UNKNOWN", "(typeof Foo0)", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("(typeof Foo0)", "(typeof Foo1)", CAN_HOLD);
    this.assertThatResultAsTable().containsCell("(typeof Foo1)", "(typeof Foo2)", CAN_HOLD);
  }

  @Test
  public void disambiguationSupertypes_createConnection() {
    // Given
    ColorGraphBuilder builder = this.createBuilder(null);

    Color parent = Color.createSingleton(createWithUniqueName("Parent").build());
    Color child =
        Color.createSingleton(
            createWithUniqueName("Child")
                .setDisambiguationSupertypes(ImmutableList.of(parent))
                .build());

    builder.add(graphNodeFactory.createNode(child));

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Parent", "Child", CAN_HOLD);
  }

  @Test
  public void unions_connectedAboveMembers() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(
                ImmutableSet.of("Foo", "Bar", "Qux"), ImmutableSet.of("(Bar|Foo|Qux)", "UNKNOWN"));
    ColorGraphBuilder builder = this.createBuilder(stubFinder);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("(Bar|Foo|Qux)", "Bar", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("(Bar|Foo|Qux)", "Foo", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("(Bar|Foo|Qux)", "Qux", ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(ImmutableSet.of("Foo", "Bar", "Qux"), ImmutableSet.of("(Bar|Foo|Qux)", "Kif"));
    ColorGraphBuilder builder = this.createBuilder(stubFinder);

    ColorGraphNode flatKif =
        this.graphNodeFactory.createNode(
            Color.createSingleton(createWithUniqueName("Kif").build()));
    builder.add(flatKif);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Kif", "(Bar|Foo|Qux)", ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca_withMultipleLcas() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(
                ImmutableSet.of("Foo", "Bar", "Qux"),
                ImmutableSet.of("(Bar|Foo|Qux)", "Kif", "Lop"));
    ColorGraphBuilder builder = this.createBuilder(stubFinder);

    ColorGraphNode flatKif =
        this.graphNodeFactory.createNode(
            Color.createSingleton(createWithUniqueName("Kif").build()));
    builder.add(flatKif);

    ColorGraphNode flatLop =
        this.graphNodeFactory.createNode(
            Color.createSingleton(createWithUniqueName("Lop").build()));
    builder.add(flatLop);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Kif", "(Bar|Foo|Qux)", ALGEBRAIC);
    // this.assertThatResultAsTable().containsCell("Lop", "(Bar|Foo|Qux
    // )", ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca_whichIsAlsoUnion() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(ImmutableSet.of("Foo", "Bar", "Qux"), ImmutableSet.of("(Bar|Foo|Qux)", "Kif"))
            .addStub(ImmutableSet.of("Foo", "Bar"), ImmutableSet.of("(Bar|Foo)", "(Bar|Foo|Qux)"));
    ColorGraphBuilder builder = this.createBuilder(stubFinder);

    ColorGraphNode flatKif =
        this.graphNodeFactory.createNode(
            Color.createSingleton(createWithUniqueName("Kif").build()));
    builder.add(flatKif);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }",
                "class Bar { }",
                "class Qux { }",
                "",
                "let /** (!Foo|!Bar|!Qux) */ testA;",
                "let /** (!Foo|!Bar) */ testB;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Kif", "(Bar|Foo|Qux)", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("(Bar|Foo|Qux)", "(Bar|Foo)", ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLac_whichHasSameDescendantCount() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(
                ImmutableSet.of("Foo.prototype", "Bar.prototype"),
                ImmutableSet.of("(Bar.prototype|Foo.prototype)", "Kif"));
    ColorGraphBuilder builder = this.createBuilder(stubFinder);

    LinkedHashMap<String, ColorGraphNode> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Kif { }",
                "class Foo extends Kif { }",
                "class Bar extends Kif { }",
                "",
                "let /** ((typeof Foo.prototype)|(typeof Bar.prototype)) */ test;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Kif", "(Bar.prototype|Foo.prototype)", ALGEBRAIC);

    // Also check post-conditions.
  }

  @After
  public void verifyResult_topNodeIsOnlyRoot() {
    assertThat(
            this.result.getNodes().stream()
                .filter((n) -> n.getInEdges().isEmpty())
                .collect(toImmutableSet()))
        .comparingElementsUsing(NODE_HAS_TYPENAME)
        .containsExactly("UNKNOWN");
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
    ImmutableTable.Builder<String, String, EdgeReason> table = ImmutableTable.builder();
    for (DiGraphEdge<ColorGraphNode, Object> edge : this.result.getEdges()) {
      table.put(
          nameOf(edge.getSource()), nameOf(edge.getDestination()), (EdgeReason) edge.getValue());
    }
    return assertThat(table.build());
  }

  private ColorGraphBuilder createBuilder(@Nullable StubLcaFinder optLcaFinder) {
    StubLcaFinder lcaFinder = (optLcaFinder == null) ? new StubLcaFinder() : optLcaFinder;
    return new ColorGraphBuilder(
        this.graphNodeFactory,
        lcaFinder::setGraph,
        ColorRegistry.createWithInvalidatingNatives(ImmutableSet.of(NativeColorId.UNKNOWN)));
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
    private final LinkedHashMap<ImmutableSet<String>, ImmutableSet<String>> stubs =
        new LinkedHashMap<>();
    private final LinkedHashMap<Runnable, Integer> preconditions = new LinkedHashMap<>();

    StubLcaFinder() {
      super(null);
    }

    StubLcaFinder setGraph(DiGraph<ColorGraphNode, Object> graph) {
      this.graph = graph;
      return this;
    }

    StubLcaFinder addStub(ImmutableSet<String> from, ImmutableSet<String> to) {
      this.stubs.put(from, to);
      return this;
    }

    @Override
    public ImmutableSet<ColorGraphNode> findAll(Set<ColorGraphNode> roots) {
      for (Map.Entry<Runnable, Integer> entry : this.preconditions.entrySet()) {
        entry.getKey().run();
        entry.setValue(entry.getValue() - 1);
      }

      ImmutableSet<String> rootNames =
          roots.stream().map(ColorGraphBuilderTest::nameOf).collect(toImmutableSet());
      assertThat(this.stubs).containsKey(rootNames);
      ImmutableSet<String> resultNames = this.stubs.get(rootNames);

      ImmutableSet<ColorGraphNode> results =
          this.graph.getNodes().stream()
              .map(DiGraphNode::getValue)
              .filter((t) -> resultNames.contains(nameOf(t)))
              .collect(toImmutableSet());
      assertThat(results).hasSize(resultNames.size());

      return results;
    }
  }

  private static String nameOf(DiGraphNode<ColorGraphNode, Object> node) {
    return nameOf(node.getValue());
  }

  private static String nameOf(ColorGraphNode flat) {
    return nameOf(flat.getColor());
  }

  private static String nameOf(Color color) {
    if (color.isUnion()) {
      return "("
          + color.union().stream().map(ColorGraphBuilderTest::nameOf).sorted().collect(joining("|"))
          + ")";
    } else {
      return Iterables.getOnlyElement(color.getDebugInfo()).getClassName();
    }
  }

  private static SingletonColorFields.Builder createWithUniqueName(String name) {
    SingletonColorFields.Builder builder = SingletonColorFields.builder().setId(name);
    builder.setDebugInfo(DebugInfo.builder().setClassName(name).build());
    return builder;
  }

  private static final Correspondence<DiGraphNode<ColorGraphNode, Object>, String>
      NODE_HAS_TYPENAME =
          Correspondence.transforming(ColorGraphBuilderTest::nameOf, "in a node with type");
}
