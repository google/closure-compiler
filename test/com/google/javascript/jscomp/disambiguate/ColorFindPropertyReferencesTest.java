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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Multimaps.toMultimap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.GETTER_DEF;
import static com.google.javascript.rhino.Token.MEMBER_FUNCTION_DEF;
import static com.google.javascript.rhino.Token.OPTCHAIN_GETPROP;
import static com.google.javascript.rhino.Token.SETTER_DEF;
import static com.google.javascript.rhino.Token.STRING_KEY;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static java.util.Arrays.stream;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.MultimapSubject;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.WarningsGuard;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ColorFindPropertyReferencesTest extends CompilerTestCase {

  private final Compiler compiler = new Compiler();

  private CompilerPass processor;
  private ImmutableSet<String> expectedOriginalNameTypes = ImmutableSet.of();

  /**
   * Maps a label name to information about the color of the labeled statement node.
   *
   * <p>This map is recreated each time collectProperties() is run.
   */
  private HashBiMap<String, Color> labeledStatementMap;

  private Map<String, PropertyClustering> propIndex;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.enableTypeCheck();
    this.replaceTypesWithColors();
    this.disableCompareJsDoc();
  }

  @Override
  protected Compiler createCompiler() {
    return this.compiler;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.addWarningsGuard(SILENCE_CHECKS_WARNINGS_GUARD);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    checkState(compiler == this.compiler);
    return checkNotNull(this.processor);
  }

  @After
  public void verifyProps_onlyHaveUseSitesWithCorrectName() {
    for (PropertyClustering prop : this.propIndex.values()) {
      assertThat(
              prop.getUseSites().keySet().stream()
                  .filter((n) -> !Objects.equals(prop.getName(), n.getString()))
                  .collect(toImmutableSet()))
          .isEmpty();
    }
  }

  @After
  public void verifyProps_trackOriginalNamedTypesCorrectly() {
    for (PropertyClustering prop : this.propIndex.values()) {
      ColorGraphNode originalNameRep = prop.getOriginalNameClusterRep();
      Set<ColorGraphNode> originalNameCluster =
          (originalNameRep == null)
              ? ImmutableSet.of()
              : prop.getClusters().findAll(originalNameRep);
      assertThat(originalNameCluster)
          .containsExactlyElementsIn(
              Sets.intersection(
                  prop.getClusters().elements(),
                  this.expectedOriginalNameTypes.stream()
                      .map((label) -> this.flattener.createdNodes.get(this.getLabelledColor(label)))
                      .collect(toImmutableSet())));
    }
  }

  @Test
  public void getProp_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo { }", //
                "",
                "new Foo().a;",
                "",
                "FOO: new Foo();"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly("FOO", GETPROP);
  }

  @Test
  public void optChainGetProp_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo { }", //
                "",
                "new Foo()?.a;",
                "",
                "FOO: new Foo();"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly("FOO", OPTCHAIN_GETPROP);
  }

  @Test
  public void objectLitProp_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo { }",
                "",
                "/** @type {!Foo} */ ({", //
                "      a: 0,",
                "      b() { },",
                "  get c() { },",
                "  set d(x) { },",
                "     'e': 0,",
                "    ['f']: 0,",
                "    ...x,",
                "})",
                "",
                "FOO: new Foo();"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a", "b", "c", "d");

    this.assertThatUsesOf("a").containsExactly("FOO", STRING_KEY);
    this.assertThatUsesOf("b").containsExactly("FOO", MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly("FOO", GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly("FOO", SETTER_DEF);
  }

  @Test
  public void objectPatternProp_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo { }",
                "",
                "const {", //
                "      a: a,",
                "     'e': e,",
                "    ['f']: f,",
                "    ...x",
                "} = /** @type {!Foo} */ (unknown);",
                "",
                "FOO: new Foo();"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly("FOO", STRING_KEY);
  }

  @Test
  public void classMemberProp_onPrototype_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo {", //
                "      b() { }",
                "  get c() { }",
                "  set d(x) { }",
                "    ['f']() { }",
                "}",
                "",
                "FOO_PROTOTYPE: Foo.prototype;"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("b", "c", "d", "prototype");

    this.assertThatUsesOf("b").containsExactly("FOO_PROTOTYPE", MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly("FOO_PROTOTYPE", GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly("FOO_PROTOTYPE", SETTER_DEF);
  }

  @Test
  public void classMemberProp_onInstance_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo {", //
                "  constructor() {",
                "    this.a = 0;",
                "  }",
                "}",
                "",
                "FOO: new Foo();"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a", "constructor");

    this.assertThatUsesOf("a").containsExactly("FOO", GETPROP);
  }

  @Test
  public void classMemberProp_onCtor_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo {", //
                "  static     b() { }",
                "  static get c() { }",
                "  static set d(x) { }",
                "  static   ['f']() { }",
                "}",
                "",
                "TYPEOF_FOO: Foo;"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("b", "c", "d");

    this.assertThatUsesOf("b").containsExactly("TYPEOF_FOO", MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly("TYPEOF_FOO", GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly("TYPEOF_FOO", SETTER_DEF);
  }

  @Test
  public void objectLitProp_inObjectDefineProperties_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo { }", //
                "",
                "Object.defineProperties(Foo.prototype, {",
                "      a: 0,",
                "      b() { },",
                "  get c() { },",
                "  set d(x) { },",
                "    ['f']() { },",
                "});",
                "",
                "FOO_PROTOTYPE: Foo.prototype;"));

    // Then
    assertThat(this.propIndex.keySet())
        .containsExactly("a", "b", "c", "d", "defineProperties", "prototype");

    this.assertThatUsesOf("a").containsExactly("FOO_PROTOTYPE", STRING_KEY);
    this.assertThatUsesOf("b").containsExactly("FOO_PROTOTYPE", MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly("FOO_PROTOTYPE", GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly("FOO_PROTOTYPE", SETTER_DEF);
  }

  @Test
  public void stringLiteralProp_viaPropDefinerFunction_isFound() {
    // When
    this.propIndex =
        this.collectProperties(
            ImmutableSet.of("reflect"),
            "",
            lines(
                "class Foo { }", //
                "",
                "reflect('a', Foo.prototype);",
                "",
                "FOO_PROTOTYPE: Foo.prototype;"));

    // Then
    this.assertThatUsesOf("a").containsExactly("FOO_PROTOTYPE", Token.STRINGLIT);
  }

  @Test
  public void propDefinerFunction_noObject_associatesWithJavaNull() {
    // When
    this.propIndex =
        this.collectProperties(
            ImmutableSet.of("reflect"),
            "",
            lines(
                "reflect('a');" //
                ));

    // Then
    this.assertThatUsesOf("a").containsExactly(null, Token.STRINGLIT);
  }

  @Test
  public void propDefinerFunction_noString_findsNoNodes() {
    // When
    this.propIndex =
        this.collectProperties(
            ImmutableSet.of("reflect"),
            "",
            lines(
                "reflect();" //
                ));

    // Then
    assertThat(this.propIndex.keySet()).isEmpty();
  }

  @Test
  public void propDefinerFunction_nonLiteralName_findsNoNodes() {
    // When
    this.propIndex =
        this.collectProperties(
            ImmutableSet.of("reflect"),
            "",
            lines(
                "const foo = 'a';", //
                "reflect(foo, {});"));

    // Then
    assertThat(this.propIndex.keySet()).isEmpty();
  }

  @Test
  public void externProps_areClusteredTogether() {
    // Given
    this.expectedOriginalNameTypes = ImmutableSet.of("FOO", "BAR", "TUM");

    // When
    this.propIndex =
        this.collectProperties(
            lines(
                "class Foo { }", //
                "class Bar { }",
                "class Tum { }",
                "",
                "new Foo().a;",
                "new Bar().a;",
                "new Tum().notA;"),
            lines(
                "class Qux { }", //
                "",
                "new Qux().a;",
                "",
                "FOO: new Foo();",
                "BAR: new Bar();",
                "QUX: new Qux();",
                "TUM: new Tum();"));

    // Then
    this.assertThatUsesOf("a").containsExactly("FOO", GETPROP, "BAR", GETPROP, "QUX", GETPROP);

    // "Original name" type clusters are checked during teardown.
  }

  @Test
  public void enumsAreClusteredWithExterns() {
    // Given
    this.expectedOriginalNameTypes = ImmutableSet.of("FOO", "BAR");

    // When
    this.propIndex =
        this.collectProperties(
            lines(
                "class Foo {}", //
                "new Foo().a;"),
            lines(
                "class Qux { }", //
                "/** @enum */",
                "const Bar = {",
                "  a: 0",
                "};",
                "",
                "new Qux().a;",
                "",
                "FOO: new Foo();",
                "BAR: Bar;",
                "QUX: new Qux();"));

    // Then
    this.assertThatUsesOf("a").containsExactly("FOO", GETPROP, "BAR", STRING_KEY, "QUX", GETPROP);

    // "Original name" type clusters are checked during teardown.
  }

  @Test
  public void propertylessConstructorsAreRecordedInTypeFlattener() {
    this.propIndex =
        this.collectProperties(
            "",
            lines(
                "class Foo {}",
                "/** @constructor */ function Bar() {}",
                "/** @interface */ function Quz() {}",
                "/** @record */ function Baz() {}",
                "function other() {}",
                "",
                "TYPEOF_FOO: Foo;",
                "TYPEOF_BAR: Bar;",
                "TYPEOF_QUZ: Quz;",
                "TYPEOF_BAZ: Baz;"));

    assertThat(this.flattener.created)
        .containsExactlyElementsIn(
            getLabelledColors("TYPEOF_FOO", "TYPEOF_BAR", "TYPEOF_QUZ", "TYPEOF_BAZ"));
  }

  /**
   * Returns a subject for a map from a label for a particular color, to the AST Tokens of all the
   * associations of property {@code name} and that color
   */
  private MultimapSubject assertThatUsesOf(String name) {
    PropertyClustering prop = this.propIndex.get(name);

    Map<ColorGraphNode, Color> nodeToColor = this.flattener.createdNodes.inverse();
    Map<Color, String> colorToLabel = this.labeledStatementMap.inverse();
    ArrayListMultimap<String, Token> actual =
        prop.getUseSites().entrySet().stream()
            .collect(
                toMultimap(
                    (e) -> colorToLabel.get(nodeToColor.get(e.getValue())), //
                    (e) -> e.getKey().getToken(),
                    ArrayListMultimap::create));
    return assertThat(actual);
  }

  private ColorGraphNode createColorGraphNode() {
    return ColorGraphNode.createForTesting(-1);
  }

  private ColorFindPropertyReferences finder;
  private StubColorGraphNodeFactory flattener;

  private Map<String, PropertyClustering> collectProperties(String externs, String src) {
    return this.collectProperties(/* propertyReflectorNames= */ ImmutableSet.of(), externs, src);
  }

  private Map<String, PropertyClustering> collectProperties(
      ImmutableSet<String> propertyReflectorNames, String externs, String src) {

    // Create a fresh statement map for each test case.
    labeledStatementMap = HashBiMap.create();
    this.processor =
        (e, s) -> {
          NodeTraversal.traverse(compiler, s, new LabelledStatementCollector());
          this.flattener = new StubColorGraphNodeFactory();
          this.finder =
              new ColorFindPropertyReferences(this.flattener, propertyReflectorNames::contains);
          NodeTraversal.traverse(this.compiler, e.getParent(), checkNotNull(this.finder));
        };

    this.test(srcs(src), expected(src), externs(externs));
    this.processor = null;

    return this.finder.getPropertyIndex();
  }

  private final class StubColorGraphNodeFactory extends ColorGraphNodeFactory {

    private final LinkedHashSet<Color> created = new LinkedHashSet<>();
    private final HashBiMap<Color, ColorGraphNode> createdNodes = HashBiMap.create();

    StubColorGraphNodeFactory() {
      super(
          new LinkedHashMap<>(), ColorFindPropertyReferencesTest.this.compiler.getColorRegistry());
    }

    @Override
    public ColorGraphNode createNode(@Nullable Color color) {
      this.created.add(color);
      return createdNodes.computeIfAbsent(
          color, (c) -> ColorFindPropertyReferencesTest.this.createColorGraphNode());
    }

    @Override
    public ImmutableSet<ColorGraphNode> getAllKnownTypes() {
      throw new UnsupportedOperationException();
    }
  }

  private static final WarningsGuard SILENCE_CHECKS_WARNINGS_GUARD =
      new WarningsGuard() {
        @Override
        protected int getPriority() {
          return WarningsGuard.Priority.MAX.getValue();
        }

        @Override
        public CheckLevel level(JSError error) {
          return error.getDescription().contains("Parse") ? null : CheckLevel.OFF;
        }
      };

  private class LabelledStatementCollector extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (parent != null && parent.isLabel() && !n.isLabelName()) {
        // First child of a LABEL is a LABEL_NAME, n is the second child.
        Node labelNameNode = checkNotNull(n.getPrevious(), n);
        checkState(labelNameNode.isLabelName(), labelNameNode);
        String labelName = labelNameNode.getString();
        assertWithMessage("Duplicate label name: %s", labelName)
            .that(labeledStatementMap)
            .doesNotContainKey(labelName);
        assertNode(n).hasToken(Token.EXPR_RESULT);
        labeledStatementMap.put(labelName, n.getOnlyChild().getColor());
      }
    }
  }

  private Color getLabelledColor(String label) {
    assertWithMessage("No statement found for label: %s", label)
        .that(labeledStatementMap)
        .containsKey(label);
    return labeledStatementMap.get(label);
  }

  private ImmutableSet<Color> getLabelledColors(String... labels) {
    return stream(labels).map(this::getLabelledColor).collect(toImmutableSet());
  }
}
