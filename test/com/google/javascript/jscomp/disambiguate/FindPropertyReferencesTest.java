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
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DIAGNOSTIC_EQUALITY;
import static com.google.javascript.rhino.Token.GETTER_DEF;
import static com.google.javascript.rhino.Token.MEMBER_FUNCTION_DEF;
import static com.google.javascript.rhino.Token.SETTER_DEF;
import static com.google.javascript.rhino.Token.STRING;
import static com.google.javascript.rhino.Token.STRING_KEY;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
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
import com.google.javascript.jscomp.PropertyRenamingDiagnostics;
import com.google.javascript.jscomp.WarningsGuard;
import com.google.javascript.jscomp.disambiguate.FindPropertyReferences.IsPropertyDefiner;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FindPropertyReferencesTest extends CompilerTestCase {

  private final Compiler compiler = new Compiler();
  private final JSTypeRegistry registry = this.compiler.getTypeRegistry();

  private CompilerPass processor;
  private ImmutableSet<FlatType> expectedOriginalNameTypes = ImmutableSet.of();

  private Map<String, PropertyClustering> propIndex;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.enableTypeCheck();
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
      FlatType originalNameRep = prop.getOriginalNameClusterRep();
      Set<FlatType> originalNameCluster =
          (originalNameRep == null)
              ? ImmutableSet.of()
              : prop.getClusters().findAll(originalNameRep);
      assertThat(originalNameCluster)
          .containsExactlyElementsIn(
              Sets.intersection(prop.getClusters().elements(), this.expectedOriginalNameTypes));
    }
  }

  @Test
  public void getProp_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder = this.createFinder(ImmutableMap.of("Foo", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo { }", //
                "",
                "new Foo().a;"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING);
  }

  @Test
  public void optChainGetProp_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder = this.createFinder(ImmutableMap.of("Foo", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo { }", //
                "",
                "new Foo()?.a;"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING);
  }

  @Test
  public void objectLitProp_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder = this.createFinder(ImmutableMap.of("Foo", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
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
                "})"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a", "b", "c", "d");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING_KEY);
    this.assertThatUsesOf("b").containsExactly(flatFoo, MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly(flatFoo, GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly(flatFoo, SETTER_DEF);
  }

  @Test
  public void objectPatternProp_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder = this.createFinder(ImmutableMap.of("Foo", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo { }",
                "",
                "const {", //
                "      a: a,",
                "     'e': e,",
                "    ['f']: f,",
                "    ...x",
                "} = /** @type {!Foo} */ (unknown);"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING_KEY);
  }

  @Test
  public void classMemberProp_onPrototype_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of("Foo.prototype", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo {", //
                "      b() { }",
                "  get c() { }",
                "  set d(x) { }",
                "    ['f']() { }",
                "}"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("b", "c", "d");

    this.assertThatUsesOf("b").containsExactly(flatFoo, MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly(flatFoo, GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly(flatFoo, SETTER_DEF);
  }

  @Test
  public void classMemberProp_onInstance_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder = this.createFinder(ImmutableMap.of("Foo", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo {", //
                "  constructor() {",
                "    this.a = 0;",
                "  }",
                "}"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("a", "constructor");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING);
  }

  @Test
  public void classMemberProp_onCtor_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of("(typeof Foo)", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo {", //
                "  static     b() { }",
                "  static get c() { }",
                "  static set d(x) { }",
                "  static   ['f']() { }",
                "}"));

    // Then
    assertThat(this.propIndex.keySet()).containsExactly("b", "c", "d");

    this.assertThatUsesOf("b").containsExactly(flatFoo, MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly(flatFoo, GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly(flatFoo, SETTER_DEF);
  }

  @Test
  public void objectLitProp_inObjectDefineProperties_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of("Foo.prototype", flatFoo), null, null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
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
                "});"));

    // Then
    assertThat(this.propIndex.keySet())
        .containsExactly("a", "b", "c", "d", "defineProperties", "prototype");

    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING_KEY);
    this.assertThatUsesOf("b").containsExactly(flatFoo, MEMBER_FUNCTION_DEF);
    this.assertThatUsesOf("c").containsExactly(flatFoo, GETTER_DEF);
    this.assertThatUsesOf("d").containsExactly(flatFoo, SETTER_DEF);
  }

  @Test
  public void stringLiteralProp_viaPropDefinerFunction_isFound() {
    // Given
    FlatType flatFoo = this.createFlatType();

    FindPropertyReferences finder =
        this.createFinder(
            ImmutableMap.of("Foo.prototype", flatFoo), null, (s) -> s.equals("define"));

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo { }", //
                "",
                "define('a', Foo.prototype);"));

    // Then
    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING);
  }

  @Test
  public void propDefinerFunction_tooFewArgs_isReported() {
    // Given
    ArrayList<JSError> errors = new ArrayList<>();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of(), errors::add, (s) -> s.equals("define"));

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "define();" //
                ));

    // Then
    assertThat(errors)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION);
  }

  @Test
  public void propDefinerFunction_nonLiteralName_isReported() {
    // Given
    ArrayList<JSError> errors = new ArrayList<>();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of(), errors::add, (s) -> s.equals("define"));

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "const foo = 'a';", //
                "define(foo, {});"));

    // Then
    assertThat(errors)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION);
  }

  @Test
  public void propDefinerFunction_illegalName_isReported() {
    // Given
    ArrayList<JSError> errors = new ArrayList<>();

    FindPropertyReferences finder =
        this.createFinder(ImmutableMap.of(), errors::add, (s) -> s.equals("define"));

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "define('a.b', {});" //
                ));

    // Then
    assertThat(errors)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(PropertyRenamingDiagnostics.INVALID_RENAME_FUNCTION);
  }

  @Test
  public void externProps_areClusteredTogether() {
    // Given
    FlatType flatFoo = this.createFlatType();
    FlatType flatBar = this.createFlatType();
    FlatType flatTum = this.createFlatType();
    FlatType flatQux = this.createFlatType();
    this.expectedOriginalNameTypes = ImmutableSet.of(flatFoo, flatBar, flatTum);

    FindPropertyReferences finder =
        this.createFinder(
            ImmutableMap.of("Foo", flatFoo, "Bar", flatBar, "Qux", flatQux, "Tum", flatTum),
            null,
            null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
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
                "new Qux().a;"));

    // Then
    this.assertThatUsesOf("a").containsExactly(flatFoo, STRING, flatBar, STRING, flatQux, STRING);

    // "Original name" type clusters are checked during teardown.
  }

  @Test
  public void enumsAndBoxableScalars_areClusteredWithExterns() {
    // Given
    FlatType flatFoo = FlatType.createForTesting(-1);
    FlatType flatBar = FlatType.createForTesting(-2);
    FlatType flatString = FlatType.createForTesting(-3);
    FlatType flatQux = FlatType.createForTesting(-4);
    this.expectedOriginalNameTypes = ImmutableSet.of(flatFoo, flatBar, flatString);

    FindPropertyReferences finder =
        this.createFinder(
            ImmutableMap.<String, FlatType>builder()
                .put("Foo", flatFoo)
                .put("enum{Bar}", flatBar)
                .put("Qux", flatQux)
                .put("string", flatString)
                .build(),
            null,
            null);

    // When
    this.propIndex =
        this.collectProperties(
            finder,
            lines(
                "class Foo {}", //
                "new Foo().a;"),
            lines(
                "'x'.a;", //
                "class Qux { }",
                "/** @enum */",
                "const Bar = {",
                "  a: 0",
                "};",
                "",
                "new Qux().a;"));

    // Then
    this.assertThatUsesOf("a")
        .containsExactly(flatFoo, STRING, flatString, STRING, flatBar, STRING_KEY, flatQux, STRING);

    // "Original name" type clusters are checked during teardown.
  }

  @Test
  public void propertylessConstructorsAreRecordedInTypeFlattener() {
    Consumer<JSError> errorCb =
        (e) -> {
          assertWithMessage(e.getDescription()).fail();
        };
    StubTypeFlattener flattener = new StubTypeFlattener(ImmutableMap.of());
    FindPropertyReferences finder = new FindPropertyReferences(flattener, errorCb, (s) -> false);

    this.propIndex =
        this.collectProperties(
            finder,
            "",
            lines(
                "class Foo {}",
                "/** @constructor */ function Bar() {}",
                "/** @interface */ function Quz() {}",
                "/** @record */ function Baz() {}",
                "function other() {}"));

    assertThat(flattener.flattened)
        .containsExactly("(typeof Foo)", "(typeof Bar)", "(typeof Quz)", "(typeof Baz)");
  }

  private MultimapSubject assertThatUsesOf(String name) {
    PropertyClustering prop = this.propIndex.get(name);
    ArrayListMultimap<FlatType, Token> actual =
        prop.getUseSites().entrySet().stream()
            .collect(
                toMultimap(
                    Map.Entry::getValue, //
                    (e) -> e.getKey().getToken(),
                    ArrayListMultimap::create));
    return assertThat(actual);
  }

  private FindPropertyReferences createFinder(
      ImmutableMap<String, FlatType> typeIndex,
      @Nullable Consumer<JSError> errorCb,
      @Nullable IsPropertyDefiner isPropertyDefiner) {
    if (errorCb == null) {
      errorCb =
          (e) -> {
            assertWithMessage(e.getDescription()).fail();
          };
    }
    if (isPropertyDefiner == null) {
      isPropertyDefiner = (s) -> false;
    }

    return new FindPropertyReferences(new StubTypeFlattener(typeIndex), errorCb, isPropertyDefiner);
  }

  private FlatType createFlatType() {
    return FlatType.createForTesting(-1);
  }

  private Map<String, PropertyClustering> collectProperties(
      FindPropertyReferences finder, String externs, String src) {
    this.processor =
        (e, s) -> NodeTraversal.traverse(this.compiler, e.getParent(), checkNotNull(finder));
    this.test(srcs(src), expected(src), externs(externs));
    this.processor = null;

    return finder.getPropertyIndex();
  }

  private final class StubTypeFlattener extends TypeFlattener {
    private final FlatType fallbackType = FindPropertyReferencesTest.this.createFlatType();

    private final ImmutableMap<String, FlatType> stubs;
    private final LinkedHashSet<String> flattened = new LinkedHashSet<>();

    StubTypeFlattener(ImmutableMap<String, FlatType> stubs) {
      super(FindPropertyReferencesTest.this.registry, null);
      this.stubs = stubs;
    }

    @Override
    public FlatType flatten(JSType type) {
      flattened.add(type.toString());
      return stubs.getOrDefault(type.toString(), this.fallbackType);
    }

    @Override
    public FlatType flatten(JSTypeNative type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableSet<FlatType> getAllKnownTypes() {
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
}
