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
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill.Kind;
import com.google.javascript.jscomp.PolyfillUsageFinder.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.NodeSubject;
import java.util.function.Consumer;
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
                "use(new Map());", // 1
                "globalThis.Array.from", // 2
                "  ? globalThis.Array.from([])", // 3
                "  : [];",
                "[1, 2, 3].fill(0);", // 5
                "notAPolyfill();")
            .build();

    final ImmutableList<PolyfillUsage> unguardedPolyfillUsages =
        testPolyfillUsageFinder.getUnguardedUsages();

    // Map() and [].fill() references
    assertThat(unguardedPolyfillUsages).hasSize(2);

    // Source line 1: `use(new Map())`
    final PolyfillUsageSubject mapUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(0));
    mapUsageSubject.hasNodeThat().isName("Map").hasLineno(1);
    mapUsageSubject.hasName("Map").isNotExplicitGlobal();
    mapUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Map")
        .hasNativeVersionStr("es6")
        .hasPolyfillVersionStr("es3")
        .hasLibrary("es6/map")
        .hasKind(Kind.STATIC);

    // Source line 3: `[1, 2, 3].fill(0);`
    final PolyfillUsageSubject arrayFillUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(1));
    arrayFillUsageSubject.hasNodeThat().isGetProp().hasLineno(5);
    arrayFillUsageSubject.hasName("fill").isNotExplicitGlobal();
    arrayFillUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Array.prototype.fill")
        .hasNativeVersionStr("es6")
        .hasPolyfillVersionStr("es3")
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
    hookConditionUseSubject.hasNodeThat().isGetProp().hasLineno(2);

    final PolyfillUsageSubject arrayDotFromUseSubject =
        PolyfillUsageSubject.assertPolyfillUsage(guardedPolyfillUsages.get(1));
    arrayDotFromUseSubject.hasNodeThat().isGetProp().hasLineno(3);

    // Other than the node they reference, both usage objects are the same
    for (PolyfillUsageSubject arrayDotFromUsageSubject :
        ImmutableList.of(hookConditionUseSubject, arrayDotFromUseSubject)) {
      arrayDotFromUsageSubject.hasName("Array.from");
      // reference via `globalThis` makes these explicit global references
      arrayDotFromUsageSubject.isExplicitGlobal();
      arrayDotFromUsageSubject
          .hasPolyfillThat()
          .hasNativeSymbol("Array.from")
          .hasNativeVersionStr("es6")
          .hasPolyfillVersionStr("es3")
          .hasLibrary("es6/array/from")
          .hasKind(Kind.STATIC);
    }
  }

  @Test
  public void shadowedPolyfillTest() {
    final TestPolyfillUsageFinder testPolyfillUsageFinder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines(
                "Promise es6 es3 es6/promise/promise",
                "Promise.allSettled es_2020 es3 es6/promise/allsettled")
            .forSourceLines(
                // usage of Promise and of Promise.allSettled
                "Promise.allSettled([])(result => console.log(x));",
                "function foo() {",
                // These aren't references to Promise or Promise.allSettled because of the
                // shadowing variable
                "  let Promise = { allSettled: 'I solemnly swear that I am up to no good.' };",
                "  console.log(Promise.allSettled);",
                "}")
            .build();

    final ImmutableList<PolyfillUsage> allUsages = testPolyfillUsageFinder.getAllUsages();
    assertThat(allUsages).hasSize(2);

    final PolyfillUsageSubject promiseUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(allUsages.get(0));
    promiseUsageSubject //
        .hasNodeThat()
        .isName("Promise")
        .hasLineno(1);
    promiseUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Promise")
        .hasNativeVersionStr("es6")
        .hasPolyfillVersionStr("es3")
        .hasLibrary("es6/promise/promise")
        .hasKind(Kind.STATIC);

    final PolyfillUsageSubject allSettledUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(allUsages.get(1));

    allSettledUsageSubject //
        .hasNodeThat()
        .matchesQualifiedName("Promise.allSettled")
        .hasLineno(1);
    allSettledUsageSubject
        .hasPolyfillThat()
        .hasNativeSymbol("Promise.allSettled")
        .hasNativeVersionStr("es_2020")
        .hasPolyfillVersionStr("es3")
        .hasLibrary("es6/promise/allsettled")
        .hasKind(Kind.STATIC);
  }

  @Test
  public void optChainCoverageTest() {
    final TestPolyfillUsageFinder testPolyfillUsageFinder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines("Array.from es6 es3 es6/array/from")
            .forSourceLines(
                // Checks for the existence of `Array`, but not `.from`, so not guarded
                "Array?.from([]) ?? [];",
                // Correctly guarded on existence of `Array.from`
                "Array.from?.([]) ?? [];")
            .build();

    // First usage is unguarded
    final ImmutableList<PolyfillUsage> unguardedPolyfillUsages =
        testPolyfillUsageFinder.getUnguardedUsages();
    assertThat(unguardedPolyfillUsages).hasSize(1);
    final PolyfillUsageSubject unguardedUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(0));
    unguardedUsageSubject.hasNodeThat().hasToken(Token.OPTCHAIN_GETPROP).hasLineno(1);

    // Second usage is guarded
    final ImmutableList<PolyfillUsage> guardedPolyfillUsages =
        testPolyfillUsageFinder.getGuardedUsages();
    assertThat(guardedPolyfillUsages).hasSize(1);
    final PolyfillUsageSubject guardedUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(guardedPolyfillUsages.get(0));
    guardedUsageSubject.hasNodeThat().hasToken(Token.GETPROP).hasLineno(2);

    // Other than the node they reference, both usage objects are the same
    for (PolyfillUsageSubject arrayDotFromUsageSubject :
        ImmutableList.of(unguardedUsageSubject, guardedUsageSubject)) {
      arrayDotFromUsageSubject.hasName("Array.from");
      // reference via `globalThis` makes these explicit global references
      arrayDotFromUsageSubject.isNotExplicitGlobal();
      arrayDotFromUsageSubject
          .hasPolyfillThat()
          .hasNativeSymbol("Array.from")
          .hasNativeVersionStr("es6")
          .hasPolyfillVersionStr("es3")
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

    private ImmutableList<PolyfillUsage> getAllUsages() {
      PolyfillUsageCollectingConsumer consumer = new PolyfillUsageCollectingConsumer();
      polyfillUsageFinder.traverseIncludingGuarded(rootNode, consumer);
      return consumer.getUsages();
    }
  }

  private static String lines(String... lines) {
    return String.join("\n", lines);
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

    PolyfillSubject hasNativeVersionStr(String expected) {
      check("nativeVersion").that(actual.nativeVersion).isEqualTo(expected);
      return this;
    }

    PolyfillSubject hasPolyfillVersionStr(String expected) {
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
