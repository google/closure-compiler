/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill.Kind;
import com.google.javascript.jscomp.PolyfillUsageFinder.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.NodeSubject;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PolyfillUsageFinder} */
@RunWith(JUnit4.class)
public final class PolyfillUsageFinderTest {

  /**
   * Covers testing of most common cases.
   *
   * <ul>
   *   <li>guarded & unguarded references
   *   <li>global symbols & prototype methods
   *   <li>non-polyfill references
   *   <li>references via a named global object
   * </ul>
   */
  @Test
  public void basicCoverageTest() {
    final TestPolyfillUsageFinder testPolyfillUsageFinder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines(
                "Array.prototype.fill es6 es3 es6/array/fill",
                "Array.from es6 es3 es6/array/from",
                "Map es6 es3 es6/map")
            .forSourceLines(
                "use(new Map());", //
                "globalThis.Array.from ? globalThis.Array.from([]) : [];",
                "[1, 2, 3].fill(0);",
                "notAPolyfill();")
            .build();

    final ImmutableList<PolyfillUsage> unguardedPolyfillUsages =
        testPolyfillUsageFinder.getUnguardedUsages();

    // Map() and [].fill() references
    assertThat(unguardedPolyfillUsages).hasSize(2);

    // Source line 1: `use(new Map())`
    final PolyfillUsageSubject mapUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(0));
    mapUsageSubject.hasNodeThat().isName("Map").hasLineno(1).hasCharno(8).hasLength(3);
    mapUsageSubject.hasName("Map").isNotExplicitGlobal();
    mapUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Map")
        .hasNativeVersion(FeatureSet.ES6)
        .hasPolyfillVersion(FeatureSet.ES3)
        .hasLibrary("es6/map")
        .hasKind(Kind.STATIC);

    // Source line 3: `[1, 2, 3].fill(0);`
    final PolyfillUsageSubject arrayFillUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(1));
    arrayFillUsageSubject.hasNodeThat().isGetProp().hasLineno(3).hasCharno(0).hasLength(14);
    arrayFillUsageSubject.hasName("fill").isNotExplicitGlobal();
    arrayFillUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Array.prototype.fill")
        .hasNativeVersion(FeatureSet.ES6)
        .hasPolyfillVersion(FeatureSet.ES3)
        .hasLibrary("es6/array/fill")
        .hasKind(Kind.METHOD);

    // The 2 references to `Array.from` on line 2.
    // Note that the guard expression itself counts as a guarded use.
    // Source line 2: `globalThis.Array.from ? globalThis.Array.from([]) : [];`
    final ImmutableList<PolyfillUsage> guardedPolyfillUsages =
        testPolyfillUsageFinder.getGuardedUsages();
    assertThat(guardedPolyfillUsages).hasSize(2);

    final PolyfillUsageSubject hookConditionUseSubject =
        PolyfillUsageSubject.assertPolyfillUsage(guardedPolyfillUsages.get(0));
    hookConditionUseSubject.hasNodeThat().isGetProp().hasLineno(2).hasCharno(0).hasLength(21);

    final PolyfillUsageSubject arrayDotFromUseSubject =
        PolyfillUsageSubject.assertPolyfillUsage(guardedPolyfillUsages.get(1));
    arrayDotFromUseSubject.hasNodeThat().isGetProp().hasLineno(2).hasCharno(24).hasLength(21);

    // Other than the node they reference, both usage objects are the same
    for (PolyfillUsageSubject arrayDotFromUsageSubject :
        ImmutableList.of(hookConditionUseSubject, arrayDotFromUseSubject)) {
      arrayDotFromUsageSubject.hasName("Array.from");
      // reference via `globalThis` makes these explicit global references
      arrayDotFromUsageSubject.isExplicitGlobal();
      arrayDotFromUsageSubject
          .hasPolyfillThat()
          .hasNativeSymbol("Array.from")
          .hasNativeVersion(FeatureSet.ES6)
          .hasPolyfillVersion(FeatureSet.ES3)
          .hasLibrary("es6/array/from")
          .hasKind(Kind.STATIC);
    }
  }

  /** Uses {@link PolyfillUsageFinder} to find polyfill usages in source code for testing. */
  private static final class TestPolyfillUsageFinder {
    private final PolyfillUsageFinder polyfillUsageFinder;
    private final Node rootNode;

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private Polyfills polyfillsTable = null;
      private Compiler compiler = null;
      private PolyfillUsageFinder polyfillUsageFinder = null;
      private Node rootNode = null;

      Builder withPolyfillTableLines(String... polyfillTableLines) {
        polyfillsTable = Polyfills.fromTable(lines(polyfillTableLines));
        return this;
      }

      private Builder forSourceLines(String... srcLines) {
        final SourceFile srcFile = SourceFile.builder().buildFromCode("src.js", lines(srcLines));
        final CompilerOptions options = new CompilerOptions();
        // Don't include `"use strict";` when printing the AST as source text
        options.setEmitUseStrict(false);
        options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
        compiler = new Compiler();
        compiler.init(
            ImmutableList.of(new TestExternsBuilder().buildExternsFile("externs.js")),
            ImmutableList.of(srcFile),
            options);
        compiler.parse();
        rootNode = compiler.getJsRoot();
        return this;
      }

      TestPolyfillUsageFinder build() {
        polyfillUsageFinder =
            new PolyfillUsageFinder(checkNotNull(compiler), checkNotNull(polyfillsTable));
        return new TestPolyfillUsageFinder(this);
      }
    }

    TestPolyfillUsageFinder(Builder builder) {
      this.polyfillUsageFinder = builder.polyfillUsageFinder;
      this.rootNode = builder.rootNode;
    }

    private ImmutableList<PolyfillUsage> getUnguardedUsages() {
      PolyfillUsageCollectingConsumer consumer = new PolyfillUsageCollectingConsumer();
      polyfillUsageFinder.traverseExcludingGuarded(rootNode, consumer);
      return consumer.getUsages();
    }

    private ImmutableList<PolyfillUsage> getGuardedUsages() {
      PolyfillUsageCollectingConsumer consumer = new PolyfillUsageCollectingConsumer();
      polyfillUsageFinder.traverseOnlyGuarded(rootNode, consumer);
      return consumer.getUsages();
    }
  }

  private static String lines(String... lines) {
    return Arrays.stream(lines).collect(Collectors.joining("\n", "", "\n"));
  }

  /** Consumes {@link PolyfillUsage} objects by storing them into a retrievable list. */
  private static final class PolyfillUsageCollectingConsumer implements Consumer<PolyfillUsage> {

    private final ImmutableList.Builder<PolyfillUsage> polyfillUsageListBuilder =
        ImmutableList.builder();

    @Override
    public void accept(PolyfillUsage polyfillUsage) {
      polyfillUsageListBuilder.add(polyfillUsage);
    }

    ImmutableList<PolyfillUsage> getUsages() {
      return polyfillUsageListBuilder.build();
    }
  }

  private static final class PolyfillSubject extends Subject {
    private final Polyfill actual;

    PolyfillSubject(FailureMetadata failureMetadata, Polyfill polyfill) {
      super(failureMetadata, polyfill);
      this.actual = polyfill;
    }

    static PolyfillSubject assertPolyfill(Polyfill polyfill) {
      return assertAbout(PolyfillSubject::new).that(polyfill);
    }

    PolyfillSubject hasNativeSymbol(String expected) {
      check("nativeSymbol").that(actual.nativeSymbol).isEqualTo(expected);
      return this;
    }

    PolyfillSubject hasNativeVersion(FeatureSet expected) {
      check("nativeVersion").that(actual.nativeVersion).isEqualTo(expected);
      return this;
    }

    PolyfillSubject hasPolyfillVersion(FeatureSet expected) {
      check("polyfillVersion").that(actual.polyfillVersion).isEqualTo(expected);
      return this;
    }

    PolyfillSubject hasLibrary(String expectedLibraryPath) {
      check("library").that(actual.library).isEqualTo(expectedLibraryPath);
      return this;
    }

    PolyfillSubject hasKind(Polyfill.Kind expectedKind) {
      check("kind").that(actual.kind).isEqualTo(expectedKind);
      return this;
    }
  }

  private static final class PolyfillUsageSubject extends Subject {

    private final PolyfillUsage actual;

    PolyfillUsageSubject(FailureMetadata failureMetadata, PolyfillUsage polyfillUsage) {
      super(failureMetadata, polyfillUsage);
      this.actual = polyfillUsage;
    }

    static PolyfillUsageSubject assertPolyfillUsage(PolyfillUsage polyfillUsage) {
      return assertAbout(PolyfillUsageSubject::new).that(polyfillUsage);
    }

    PolyfillUsageSubject hasName(String expectedName) {
      check("name()").that(actual.name()).isEqualTo(expectedName);
      return this;
    }

    PolyfillUsageSubject isExplicitGlobal() {
      check("isExplicitGlobal()").that(actual.isExplicitGlobal()).isTrue();
      return this;
    }

    PolyfillUsageSubject isNotExplicitGlobal() {
      check("isExplicitGlobal()").that(actual.isExplicitGlobal()).isFalse();
      return this;
    }

    NodeSubject hasNodeThat() {
      return NodeSubject.assertNode(actual.node());
    }

    PolyfillSubject hasPolyfillThat() {
      return PolyfillSubject.assertPolyfill(actual.polyfill());
    }
  }
}
