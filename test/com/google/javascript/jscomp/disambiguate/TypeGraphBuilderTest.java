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
import static com.google.javascript.jscomp.disambiguate.TypeGraphBuilder.EdgeReason.ALGEBRAIC;
import static com.google.javascript.jscomp.disambiguate.TypeGraphBuilder.EdgeReason.ENUM_ELEMENT;
import static com.google.javascript.jscomp.disambiguate.TypeGraphBuilder.EdgeReason.INTERFACE;
import static com.google.javascript.jscomp.disambiguate.TypeGraphBuilder.EdgeReason.PROTOTYPE;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.truth.Correspondence;
import com.google.common.truth.MultimapSubject;
import com.google.common.truth.TableSubject;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.disambiguate.TypeGraphBuilder.EdgeReason;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeResolver;
import java.util.LinkedHashMap;
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
public final class TypeGraphBuilderTest extends CompilerTestCase {

  @Rule @GwtIncompatible public final TestName testName = new TestName();

  private final Compiler compiler = new Compiler();
  private final JSTypeRegistry registry = this.compiler.getTypeRegistry();
  private final TypeFlattener flattener = new TypeFlattener(this.registry, (t) -> false);

  private CompilerPass processor;
  private DiGraph<FlatType, Object> result;

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
  }

  private LinkedHashMap<String, FlatType> collectTypesFromCode(String src) {
    TypeFlattener flattener = this.flattener;
    LinkedHashMap<String, FlatType> testTypes = new LinkedHashMap<>();

    /** Flatten and collect the types of all NAMEs that start with "test". */
    this.processor =
        (externs, main) -> {
          NodeTraversal.traverse(
              this.compiler,
              main,
              new AbstractPostOrderCallback() {
                @Override
                public void visit(NodeTraversal t, Node n, Node unused) {
                  if (n.isName() && n.getString().startsWith("test")) {
                    testTypes.put(n.getString(), flattener.flatten(n.getJSType()));
                  }
                }
              });
        };
    this.testSame(srcs(src));
    this.processor = null;

    return testTypes;
  }

  @Test
  public void top_isAboveObject() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
        this.collectTypesFromCode(
            lines( //
                "const test = new Object();"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("*", "Object.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Object.prototype", "Object", PROTOTYPE);
  }

  @Test
  public void top_isAboveInterface() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
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
    this.assertThatResultAsTable().containsCell("*", "Object.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Object.prototype", "Object", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Object", "IFoo.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("IFoo.prototype", "IFoo", PROTOTYPE);
  }

  @Test
  public void prototypeChain_isInserted() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "",
                "const test = new Foo();"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Object.prototype", "Object", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Object", "Foo.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", PROTOTYPE);
  }

  @Test
  public void prototypeChain_canBranch() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
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
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Foo", "Bar.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Bar.prototype", "Bar", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Foo", "Qux.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Qux.prototype", "Qux", PROTOTYPE);
  }

  @Test
  public void prototypeChain_includesInstanceType_evenIfUnused() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }", //
                "",
                "const test = Foo.prototype;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("*", "Object.prototype", ALGEBRAIC);
    this.assertThatResultAsTable().containsCell("Object.prototype", "Object", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Object", "Foo.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("Foo.prototype", "Foo", PROTOTYPE);
  }

  @Test
  public void prototypeChain_connectsInterfaces() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
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
    this.assertThatResultAsTable().containsCell("Object", "IFoo.prototype", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("IFoo.prototype", "IFoo", PROTOTYPE);
  }

  @Test
  public void prototypeChain_connectsSubclasses_viaClassSideInheritance() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
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
    this.assertThatResultAsTable().containsCell("Function.prototype", "(typeof Foo0)", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("(typeof Foo0)", "(typeof Foo1)", PROTOTYPE);
    this.assertThatResultAsTable().containsCell("(typeof Foo1)", "(typeof Foo2)", PROTOTYPE);
  }

  @Test
  public void interfaces_classImplementingClass_doesNotCreateConnection() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType child =
          FunctionType.builder(this.registry).forConstructor().withName("Child").build();
      FunctionType parent =
          FunctionType.builder(this.registry).forConstructor().withName("Parent").build();

      child.setImplementedInterfaces(ImmutableList.of(parent.getInstanceType()));

      builder.add(flattener.flatten(child.getInstanceType()));
    }

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsMultimap().doesNotContainEntry("Parent", "Child");
  }

  @Test
  public void interfaces_classImplementingInterface_createsConnection() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType child =
          FunctionType.builder(this.registry).forConstructor().withName("Child").build();
      FunctionType parent =
          FunctionType.builder(this.registry).forInterface().withName("Parent").build();

      child.setImplementedInterfaces(ImmutableList.of(parent.getInstanceType()));

      builder.add(flattener.flatten(child.getInstanceType()));
    }

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Parent", "Child", INTERFACE);
  }

  @Test
  public void interfaces_interfaceExtendingClass_doesNotCreateConnection() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType child =
          FunctionType.builder(this.registry).forInterface().withName("Child").build();
      FunctionType parent =
          FunctionType.builder(this.registry).forConstructor().withName("Parent").build();

      child.setExtendedInterfaces(ImmutableList.of(parent.getInstanceType()));

      builder.add(flattener.flatten(child.getInstanceType()));
    }

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsMultimap().doesNotContainEntry("Parent", "Child");
  }

  @Test
  public void interfaces_interfaceExtendingInterface_createsConnection() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      FunctionType child =
          FunctionType.builder(this.registry).forInterface().withName("Child").build();
      FunctionType parent =
          FunctionType.builder(this.registry).forInterface().withName("Parent").build();

      child.setExtendedInterfaces(ImmutableList.of(parent.getInstanceType()));

      builder.add(flattener.flatten(child.getInstanceType()));
    }

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Parent", "Child", INTERFACE);
  }

  @Test
  public void unions_connectedAboveMembers() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(ImmutableSet.of("Foo", "Bar", "Qux"), ImmutableSet.of("(Bar|Foo|Qux)", "*"));
    TypeGraphBuilder builder = this.createBuilder(stubFinder);

    LinkedHashMap<String, FlatType> testTypes =
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
    TypeGraphBuilder builder = this.createBuilder(stubFinder);

    FlatType flatKif = this.flattener.flatten(this.registry.createObjectType("Kif", null));
    builder.add(flatKif);

    LinkedHashMap<String, FlatType> testTypes =
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
    TypeGraphBuilder builder = this.createBuilder(stubFinder);

    FlatType flatKif = this.flattener.flatten(this.registry.createObjectType("Kif", null));
    builder.add(flatKif);

    FlatType flatLop = this.flattener.flatten(this.registry.createObjectType("Lop", null));
    builder.add(flatLop);

    LinkedHashMap<String, FlatType> testTypes =
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
    this.assertThatResultAsTable().containsCell("Lop", "(Bar|Foo|Qux)", ALGEBRAIC);
  }

  @Test
  public void unions_connectBelowLca_whichIsAlsoUnion() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(ImmutableSet.of("Foo", "Bar", "Qux"), ImmutableSet.of("(Bar|Foo|Qux)", "Kif"))
            .addStub(ImmutableSet.of("Foo", "Bar"), ImmutableSet.of("(Bar|Foo)", "(Bar|Foo|Qux)"));
    TypeGraphBuilder builder = this.createBuilder(stubFinder);

    FlatType flatKif = this.flattener.flatten(this.registry.createObjectType("Kif", null));
    builder.add(flatKif);

    LinkedHashMap<String, FlatType> testTypes =
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
  public void unions_connectBelowLca_whichHasSameDescendentCount() {
    // Given
    StubLcaFinder stubFinder =
        new StubLcaFinder()
            .addStub(
                ImmutableSet.of("Foo.prototype", "Bar.prototype"),
                ImmutableSet.of("(Bar.prototype|Foo.prototype)", "Kif"));
    TypeGraphBuilder builder = this.createBuilder(stubFinder);

    LinkedHashMap<String, FlatType> testTypes =
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

  @Test
  public void enumElements_connectedToElementType() {
    // Given
    TypeGraphBuilder builder = this.createBuilder(null);

    LinkedHashMap<String, FlatType> testTypes =
        this.collectTypesFromCode(
            lines(
                "class Foo { }",
                "",
                "/** @enum {!Foo} */",
                "const FooEnum = { A: new Foo(), }",
                "",
                "const test = FooEnum.A;"));
    builder.addAll(testTypes.values());

    // When
    this.result = builder.build();

    // Then
    this.assertThatResultAsTable().containsCell("Foo", "FooEnum<Foo>", ENUM_ELEMENT);
  }

  @After
  public void verifyResult_topNodeIsOnlyRoot() {
    assertThat(
            this.result.getNodes().stream()
                .filter((n) -> n.getInEdges().isEmpty())
                .collect(toImmutableSet()))
        .comparingElementsUsing(NODE_HAS_TYPENAME)
        .containsExactly("*");
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
            Correspondence.<DiGraphEdge<FlatType, Object>, DiGraphEdge<FlatType, Object>>from(
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
    for (DiGraphEdge<FlatType, Object> edge : this.result.getEdges()) {
      table.put(
          edge.getSource().getValue().getType().toString(),
          edge.getDestination().getValue().getType().toString(),
          (EdgeReason) edge.getValue());
    }
    return assertThat(table.build());
  }

  private MultimapSubject assertThatResultAsMultimap() {
    ImmutableMultimap.Builder<String, String> multimap = ImmutableMultimap.builder();
    for (DiGraphEdge<FlatType, Object> edge : this.result.getEdges()) {
      multimap.put(
          edge.getSource().getValue().getType().toString(),
          edge.getDestination().getValue().getType().toString());
    }
    return assertThat(multimap.build());
  }

  private TypeGraphBuilder createBuilder(@Nullable StubLcaFinder optLcaFinder) {
    StubLcaFinder lcaFinder = (optLcaFinder == null) ? new StubLcaFinder() : optLcaFinder;
    return new TypeGraphBuilder(this.flattener, lcaFinder::setGraph);
  }

  /**
   * A fake implementation of a finder.
   *
   * <p>Instances allow setting stub responses for {@code findAll} calls. Inputs an outputs are
   * specfied using sets of type names.
   */
  private static final class StubLcaFinder extends LowestCommonAncestorFinder<FlatType, Object> {
    private DiGraph<FlatType, Object> graph;
    private LinkedHashMap<ImmutableSet<String>, ImmutableSet<String>> stubs = new LinkedHashMap<>();

    StubLcaFinder() {
      super(null);
    }

    StubLcaFinder setGraph(DiGraph<FlatType, Object> graph) {
      this.graph = graph;
      return this;
    }

    StubLcaFinder addStub(ImmutableSet<String> from, ImmutableSet<String> to) {
      this.stubs.put(from, to);
      return this;
    }

    @Override
    public ImmutableSet<FlatType> findAll(Set<FlatType> roots) {
      ImmutableSet<String> rootNames =
          roots.stream().map(TypeGraphBuilderTest::nameOf).collect(toImmutableSet());
      ImmutableSet<String> resultNames = this.stubs.get(rootNames);
      assertThat(resultNames).isNotNull();

      ImmutableSet<FlatType> results =
          this.graph.getNodes().stream()
              .map(DiGraphNode::getValue)
              .filter((t) -> resultNames.contains(nameOf(t)))
              .collect(toImmutableSet());
      assertThat(results).hasSize(resultNames.size());

      return results;
    }
  }

  private static String nameOf(DiGraphNode<FlatType, Object> node) {
    return nameOf(node.getValue());
  }

  private static String nameOf(FlatType flat) {
    return flat.getType().toString();
  }

  private static final Correspondence<DiGraphNode<FlatType, Object>, String> NODE_HAS_TYPENAME =
      Correspondence.transforming(TypeGraphBuilderTest::nameOf, "in a node with type");
}
