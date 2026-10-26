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
import static com.google.javascript.jscomp.colors.ColorId.fromAscii;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfill.Kind;
import com.google.javascript.jscomp.PolyfillUsageFinder.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.NodeSubject;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
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
                """
                Array.prototype.fill es6 es3 es6/array/fill
                Array.from es6 es3 es6/array/from
                Map es6 es3 es6/map
                """)
            .forSourceLines(
                """
                use(new Map()); // 1
                globalThis.Array.from // 2
                  ? globalThis.Array.from([]) // 3
                  : []; // 4
                [1, 2, 3].fill(0); // 5
                notAPolyfill(); // 6
                """)
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

    // Source line 5: `[1, 2, 3].fill(0);`
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

    // The 2 references to `Array.from` on line 2 and 3
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
                """
                Promise es6 es3 es6/promise/promise
                Promise.allSettled es_2020 es3 es6/promise/allsettled
                """)
            .forSourceLines(
                """
                // usage of Promise and of Promise.allSettled
                Promise.allSettled([])(result => console.log(x));
                function foo() {
                  // These aren't references to Promise or Promise.allSettled because of the
                  // shadowing variable
                  let Promise = { allSettled: 'I solemnly swear that I am up to no good.' };
                  console.log(Promise.allSettled);
                }
                """)
            .build();

    final ImmutableList<PolyfillUsage> allUsages = testPolyfillUsageFinder.getAllUsages();
    assertThat(allUsages).hasSize(2);

    final PolyfillUsageSubject promiseUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(allUsages.get(0));
    promiseUsageSubject //
        .hasNodeThat()
        .isName("Promise")
        .hasLineno(2);
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
        .hasLineno(2);
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
                """
                // Checks for the existence of `Array`, but not `.from`, so not guarded
                Array?.from([]) ?? [];
                // Correctly guarded on existence of `Array.from`
                Array.from?.([]) ?? [];
                """)
            .build();

    // First usage is unguarded
    final ImmutableList<PolyfillUsage> unguardedPolyfillUsages =
        testPolyfillUsageFinder.getUnguardedUsages();
    assertThat(unguardedPolyfillUsages).hasSize(1);
    final PolyfillUsageSubject unguardedUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(unguardedPolyfillUsages.get(0));
    unguardedUsageSubject.hasNodeThat().hasToken(Token.OPTCHAIN_GETPROP).hasLineno(2);

    // Second usage is guarded
    final ImmutableList<PolyfillUsage> guardedPolyfillUsages =
        testPolyfillUsageFinder.getGuardedUsages();
    assertThat(guardedPolyfillUsages).hasSize(1);
    final PolyfillUsageSubject guardedUsageSubject =
        PolyfillUsageSubject.assertPolyfillUsage(guardedPolyfillUsages.get(0));
    guardedUsageSubject.hasNodeThat().hasToken(Token.GETPROP).hasLineno(4);

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

  private static PolyfillUsageFinder createFinderWithRegistry(ColorRegistry registry) {
    TestCompiler compiler = new TestCompiler();
    compiler.setTestColorRegistry(registry);
    return new PolyfillUsageFinder(
        compiler,
        Polyfills.fromTable("Iterator.prototype.toArray es_next es3 es6/iterator/toarray"),
        /* disambiguateProperties= */ true);
  }

  @Test
  public void isDisjointFromIterator_unknownAndTopObject_returnsFalse() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    assertThat(finder.isDisjointFromIterator(StandardColors.UNKNOWN)).isFalse();
    assertThat(finder.isDisjointFromIterator(StandardColors.TOP_OBJECT)).isFalse();
    assertThat(finder.isDisjointFromIterator(StandardColors.TOP_FUNCTION)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_sameColor_returnsFalse() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);
    Color iterator = registry.get(StandardColors.ITERATOR_ID);

    assertThat(finder.isDisjointFromIterator(iterator)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_unrelatedColors_returnsTrue() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);
    Color array = registry.get(StandardColors.ARRAY_ID);
    Color customFoo = Color.singleBuilder().setId(fromAscii("Foo")).build();

    assertThat(finder.isDisjointFromIterator(array)).isTrue();
    assertThat(finder.isDisjointFromIterator(customFoo)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_generatorAndIterator_returnsFalse() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);
    Color generator = registry.get(StandardColors.GENERATOR_ID);

    // Generator is a sibling of Iterator in externs, but inherits from %IteratorPrototype%
    // at runtime in ES2025, so Generator and Iterator are known not to be disjoint.
    assertThat(finder.isDisjointFromIterator(generator)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_subtypesAndSupertypes_returnsFalse() {
    Color iterator = Color.singleBuilder().setId(StandardColors.ITERATOR_ID).build();
    Color subIterator = Color.singleBuilder().setId(fromAscii("SubIter")).build();
    Color subSubIterator = Color.singleBuilder().setId(fromAscii("SubSub")).build();
    Color superIterable = Color.singleBuilder().setId(fromAscii("SuperIt")).build();
    Color unrelated = Color.singleBuilder().setId(fromAscii("Unrel")).build();

    ColorRegistry registry =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(subIterator, iterator)
            .addDisambiguationEdge(subSubIterator, subIterator)
            .addDisambiguationEdge(iterator, superIterable)
            .build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    // Direct subtype: SubIterator extends Iterator
    assertThat(finder.isDisjointFromIterator(subIterator)).isFalse();

    // Indirect subtype: SubSubIterator extends SubIterator extends Iterator
    assertThat(finder.isDisjointFromIterator(subSubIterator)).isFalse();

    // Supertype: Iterator extends SuperIterable (receiver is supertype)
    assertThat(finder.isDisjointFromIterator(superIterable)).isFalse();

    // Unrelated type with no edges
    assertThat(finder.isDisjointFromIterator(unrelated)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_branchingAndSiblingHierarchies() {
    Color iterator = Color.singleBuilder().setId(StandardColors.ITERATOR_ID).build();
    Color subIteratorA = Color.singleBuilder().setId(fromAscii("SubItA")).build();
    Color subIteratorB = Color.singleBuilder().setId(fromAscii("SubItB")).build();
    Color baseClass = Color.singleBuilder().setId(fromAscii("Base")).build();
    Color childClass = Color.singleBuilder().setId(fromAscii("Child")).build();

    ColorRegistry registry =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(subIteratorA, iterator)
            .addDisambiguationEdge(subIteratorB, iterator)
            .addDisambiguationEdge(childClass, baseClass)
            .build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    // Siblings under Iterator are both not disjoint from Iterator
    assertThat(finder.isDisjointFromIterator(subIteratorA)).isFalse();
    assertThat(finder.isDisjointFromIterator(subIteratorB)).isFalse();

    // Separate class hierarchy is disjoint from Iterator
    assertThat(finder.isDisjointFromIterator(childClass)).isTrue();
    assertThat(finder.isDisjointFromIterator(baseClass)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_cyclesInSupertypeGraph_terminatesGracefully() {
    Color iterator = Color.singleBuilder().setId(StandardColors.ITERATOR_ID).build();
    Color classA = Color.singleBuilder().setId(fromAscii("ClassA")).build();
    Color classB = Color.singleBuilder().setId(fromAscii("ClassB")).build();

    // Cycle A <-> B with no connection to Iterator
    ColorRegistry registryWithCycle =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(classA, classB)
            .addDisambiguationEdge(classB, classA)
            .build();
    PolyfillUsageFinder finderWithCycle = createFinderWithRegistry(registryWithCycle);

    assertThat(finderWithCycle.isDisjointFromIterator(classA)).isTrue();
    assertThat(finderWithCycle.isDisjointFromIterator(classB)).isTrue();

    // Cycle A <-> B where B also extends Iterator
    ColorRegistry registryWithCycleAndIterator =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(classA, classB)
            .addDisambiguationEdge(classB, classA)
            .addDisambiguationEdge(classB, iterator)
            .build();
    PolyfillUsageFinder finderWithCycleAndIterator =
        createFinderWithRegistry(registryWithCycleAndIterator);

    assertThat(finderWithCycleAndIterator.isDisjointFromIterator(classA)).isFalse();
    assertThat(finderWithCycleAndIterator.isDisjointFromIterator(classB)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_unionSupertypesInHierarchy() {
    Color iterator = Color.singleBuilder().setId(StandardColors.ITERATOR_ID).build();
    Color classFoo = Color.singleBuilder().setId(fromAscii("Foo")).build();
    Color classBar = Color.singleBuilder().setId(fromAscii("Bar")).build();
    Color classSub1 = Color.singleBuilder().setId(fromAscii("Sub1")).build();
    Color classSub2 = Color.singleBuilder().setId(fromAscii("Sub2")).build();

    Color unionWithIterator = Color.createUnion(ImmutableSet.of(classFoo, iterator));
    Color unionWithoutIterator = Color.createUnion(ImmutableSet.of(classFoo, classBar));

    ColorRegistry registry =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(classSub1, unionWithIterator)
            .addDisambiguationEdge(classSub2, unionWithoutIterator)
            .build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    // Sub1 has a union supertype that includes Iterator
    assertThat(finder.isDisjointFromIterator(classSub1)).isFalse();

    // Sub2 has a union supertype that only includes disjoint types
    assertThat(finder.isDisjointFromIterator(classSub2)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_primitives_returnsTrue() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    // Primitives are disjoint from Iterator
    assertThat(finder.isDisjointFromIterator(StandardColors.STRING)).isTrue();
    assertThat(finder.isDisjointFromIterator(StandardColors.NUMBER)).isTrue();
    assertThat(finder.isDisjointFromIterator(StandardColors.BOOLEAN)).isTrue();
    assertThat(finder.isDisjointFromIterator(StandardColors.BIGINT)).isTrue();
    assertThat(finder.isDisjointFromIterator(StandardColors.SYMBOL)).isTrue();
    assertThat(finder.isDisjointFromIterator(StandardColors.NULL_OR_VOID)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_nullAndUndefinedUnions() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);
    Color iterator = registry.get(StandardColors.ITERATOR_ID);
    Color array = registry.get(StandardColors.ARRAY_ID);

    Color nullableArray = Color.createUnion(ImmutableSet.of(array, StandardColors.NULL_OR_VOID));
    Color nullableIterator =
        Color.createUnion(ImmutableSet.of(iterator, StandardColors.NULL_OR_VOID));

    // Nullable/undef Array is still disjoint from Iterator after subtraction
    assertThat(finder.isDisjointFromIterator(nullableArray)).isTrue();

    // Nullable/undef Iterator is not disjoint from Iterator
    assertThat(finder.isDisjointFromIterator(nullableIterator)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_unionReceivers() {
    Color iterator = Color.singleBuilder().setId(StandardColors.ITERATOR_ID).build();
    Color subIterator = Color.singleBuilder().setId(fromAscii("SubIter")).build();
    Color classFoo = Color.singleBuilder().setId(fromAscii("Foo")).build();
    Color classBar = Color.singleBuilder().setId(fromAscii("Bar")).build();

    ColorRegistry registry =
        ColorRegistry.builder()
            .setDefaultNativeColorsForTesting()
            .addDisambiguationEdge(subIterator, iterator)
            .build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    // All elements disjoint -> disjoint
    Color unionAllDisjoint = Color.createUnion(ImmutableSet.of(classFoo, classBar));
    assertThat(finder.isDisjointFromIterator(unionAllDisjoint)).isTrue();

    // One element is Iterator -> not disjoint
    Color unionWithIterator = Color.createUnion(ImmutableSet.of(classFoo, iterator));
    assertThat(finder.isDisjointFromIterator(unionWithIterator)).isFalse();

    // One element is SubIterator -> not disjoint
    Color unionWithSubIterator = Color.createUnion(ImmutableSet.of(classFoo, subIterator));
    assertThat(finder.isDisjointFromIterator(unionWithSubIterator)).isFalse();

    // One element is UNKNOWN -> not disjoint
    Color unionWithUnknown = Color.createUnion(ImmutableSet.of(classFoo, StandardColors.UNKNOWN));
    assertThat(finder.isDisjointFromIterator(unionWithUnknown)).isFalse();

    // One element is TOP_OBJECT -> not disjoint
    Color unionWithTopObject =
        Color.createUnion(ImmutableSet.of(classFoo, StandardColors.TOP_OBJECT));
    assertThat(finder.isDisjointFromIterator(unionWithTopObject)).isFalse();
  }

  @Test
  public void isDisjointFromIterator_invalidatingColors_structuralRecordsVsNativeArrays() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    Color structuralRecord =
        Color.singleBuilder().setId(fromAscii("Struct")).setInvalidating(true).build();
    Color normalClass =
        Color.singleBuilder().setId(fromAscii("Normal")).setInvalidating(false).build();

    Color invalidatingArray =
        Color.singleBuilder().setId(StandardColors.ARRAY_ID).setInvalidating(true).build();
    Color invalidatingReadonlyArray =
        Color.singleBuilder().setId(StandardColors.READONLY_ARRAY_ID).setInvalidating(true).build();
    Color invalidatingTemplateArray =
        Color.singleBuilder()
            .setId(StandardColors.I_TEMPLATE_ARRAY_ID)
            .setInvalidating(true)
            .build();

    Color invalidatingMap =
        Color.singleBuilder().setId(StandardColors.MAP_ID).setInvalidating(true).build();
    Color invalidatingMapPrototype =
        Color.singleBuilder().setId(StandardColors.MAP_PROTOTYPE_ID).setInvalidating(true).build();
    Color invalidatingReadonlyMap =
        Color.singleBuilder().setId(StandardColors.READONLY_MAP_ID).setInvalidating(true).build();
    Color invalidatingSet =
        Color.singleBuilder().setId(StandardColors.SET_ID).setInvalidating(true).build();
    Color invalidatingSetPrototype =
        Color.singleBuilder().setId(StandardColors.SET_PROTOTYPE_ID).setInvalidating(true).build();
    Color invalidatingReadonlySet =
        Color.singleBuilder().setId(StandardColors.READONLY_SET_ID).setInvalidating(true).build();

    // Structural interface / record: isInvalidating causes disjoint to return false (b/571244650)
    assertThat(finder.isDisjointFromIterator(structuralRecord)).isFalse();

    // Normal non-invalidating class: is disjoint
    assertThat(finder.isDisjointFromIterator(normalClass)).isTrue();

    // Native Array/Map/Set types marked invalidating are exempted
    assertThat(finder.isDisjointFromIterator(invalidatingArray)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingReadonlyArray)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingTemplateArray)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingMap)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingMapPrototype)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingReadonlyMap)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingSet)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingSetPrototype)).isTrue();
    assertThat(finder.isDisjointFromIterator(invalidatingReadonlySet)).isTrue();
  }

  @Test
  public void isDisjointFromIterator_nodeAndOwnerChecks() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    Color array = registry.get(StandardColors.ARRAY_ID);
    Color iterator = registry.get(StandardColors.ITERATOR_ID);

    PolyfillUsageFinder finderWithRegistry = createFinderWithRegistry(registry);

    TestCompiler compilerWithoutRegistry = new TestCompiler();
    PolyfillUsageFinder finderWithoutRegistry =
        new PolyfillUsageFinder(
            compilerWithoutRegistry,
            Polyfills.fromTable("Iterator.prototype.toArray es_next es3 es6/iterator/toarray"),
            /* disambiguateProperties= */ true);

    Node receiverNode = IR.name("x");
    Node getPropNode = IR.getprop(receiverNode, "toArray");

    // Receiver has Array color, owner is Iterator -> disjoint
    receiverNode.setColor(array);
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, "Iterator")).isTrue();

    // Receiver has Iterator color, owner is Iterator -> not disjoint
    receiverNode.setColor(iterator);
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, "Iterator")).isFalse();

    // Receiver has null color -> not disjoint
    receiverNode.setColor(null);
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, "Iterator")).isFalse();

    // Owner is null or not Iterator -> not disjoint
    receiverNode.setColor(array);
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, (String) null)).isFalse();
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, "Array")).isFalse();
    assertThat(finderWithRegistry.isDisjointFromIterator(getPropNode, "UnknownType")).isFalse();

    // Compiler without registry -> always not disjoint
    receiverNode.setColor(array);
    assertThat(finderWithoutRegistry.isDisjointFromIterator(getPropNode, "Iterator")).isFalse();
  }

  @Test
  public void isDisjointFromIterator_receiverWithNullColor_returnsFalse() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    PolyfillUsageFinder finder = createFinderWithRegistry(registry);

    Node receiverNode = IR.name("x");
    assertThat(receiverNode.getColor()).isNull();
    Node getPropNode = IR.getprop(receiverNode, "toArray");

    assertThat(finder.isDisjointFromIterator(getPropNode, "Iterator")).isFalse();
  }

  @Test
  public void traverse_withColorRegistry_filtersDisjointUsages() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    Color array = registry.get(StandardColors.ARRAY_ID);
    Color iterator = registry.get(StandardColors.ITERATOR_ID);

    TestPolyfillUsageFinder finder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines(
                """
                Iterator.prototype.toArray es_next es3 es6/iterator/toarray
                Array.prototype.fill es6 es3 es6/array/fill
                """)
            .withDisambiguateProperties(true)
            .forSourceLines(
                """
                arr.toArray(); // 1: receiver is Array, disjoint from Iterator -> filtered!
                iter.toArray(); // 2: receiver is Iterator -> kept!
                untyped.toArray(); // 3: untyped -> kept!
                arr.fill(0); // 4: owner is Array (not Iterator) -> kept!
                """)
            .withColorRegistry(registry)
            .build();

    // Annotate AST nodes with colors
    Node rootNode = finder.getRootNode();
    ImmutableList<Node> arrNodes =
        findAllNodes(rootNode, n -> n.isName() && n.getString().equals("arr"));
    assertThat(arrNodes).hasSize(2);
    for (Node arrNode : arrNodes) {
      arrNode.setColor(array);
    }
    checkNotNull(findFirstNode(rootNode, n -> n.isName() && n.getString().equals("iter")))
        .setColor(iterator);

    ImmutableList<PolyfillUsage> usages = finder.getUnguardedUsages();

    // Should include: iter.toArray (line 2), untyped.toArray (line 3), arr.fill (line 4)
    // Should NOT include: arr.toArray (line 1)
    assertThat(usages).hasSize(3);

    PolyfillUsageSubject.assertPolyfillUsage(usages.get(0))
        .hasName("toArray")
        .hasNodeThat()
        .hasLineno(2);

    PolyfillUsageSubject.assertPolyfillUsage(usages.get(1))
        .hasName("toArray")
        .hasNodeThat()
        .hasLineno(3);

    PolyfillUsageSubject.assertPolyfillUsage(usages.get(2))
        .hasName("fill")
        .hasNodeThat()
        .hasLineno(4);
  }

  @Test
  public void traverse_withColorRegistry_disambiguatePropertiesDisabled_doesNotFilterUsages() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    Color array = registry.get(StandardColors.ARRAY_ID);

    TestPolyfillUsageFinder finder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines(
                """
                Iterator.prototype.toArray es_next es3 es6/iterator/toarray
                """)
            .withDisambiguateProperties(false)
            .forSourceLines(
                """
                arr.toArray();
                """)
            .withColorRegistry(registry)
            .build();

    Node rootNode = finder.getRootNode();
    checkNotNull(findFirstNode(rootNode, n -> n.isName() && n.getString().equals("arr")))
        .setColor(array);

    ImmutableList<PolyfillUsage> usages = finder.getUnguardedUsages();
    assertThat(usages).hasSize(1);
    PolyfillUsageSubject.assertPolyfillUsage(usages.get(0)).hasName("toArray");
  }

  @Test
  public void traverse_withoutColorRegistry_doesNotFilterUsages() {
    ColorRegistry registry = ColorRegistry.builder().setDefaultNativeColorsForTesting().build();
    Color array = registry.get(StandardColors.ARRAY_ID);

    TestPolyfillUsageFinder finder =
        TestPolyfillUsageFinder.builder()
            .withPolyfillTableLines(
                """
                Iterator.prototype.toArray es_next es3 es6/iterator/toarray
                """)
            .forSourceLines(
                """
                arr.toArray();
                """)
            .build(); // No color registry

    // Even if arr has a color, without ColorRegistry on compiler it should not filter
    Node rootNode = finder.getRootNode();
    checkNotNull(findFirstNode(rootNode, n -> n.isName() && n.getString().equals("arr")))
        .setColor(array);

    ImmutableList<PolyfillUsage> usages = finder.getUnguardedUsages();
    assertThat(usages).hasSize(1);
    PolyfillUsageSubject.assertPolyfillUsage(usages.get(0)).hasName("toArray");
  }

  private static @Nullable Node findFirstNode(Node root, Predicate<Node> predicate) {
    if (predicate.test(root)) {
      return root;
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node found = findFirstNode(child, predicate);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static ImmutableList<Node> findAllNodes(Node root, Predicate<Node> predicate) {
    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    findAllNodesInternal(root, predicate, builder);
    return builder.build();
  }

  private static void findAllNodesInternal(
      Node root, Predicate<Node> predicate, ImmutableList.Builder<Node> builder) {
    if (predicate.test(root)) {
      builder.add(root);
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      findAllNodesInternal(child, predicate, builder);
    }
  }

  private static final class TestCompiler extends Compiler {
    private @Nullable ColorRegistry testColorRegistry = null;

    void setTestColorRegistry(ColorRegistry registry) {
      this.testColorRegistry = registry;
    }

    @Override
    public boolean hasOptimizationColors() {
      return this.testColorRegistry != null;
    }

    @Override
    public ColorRegistry getColorRegistry() {
      return checkNotNull(this.testColorRegistry);
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
      private @Nullable Polyfills polyfillsTable = null;
      private @Nullable TestCompiler compiler = null;
      private @Nullable PolyfillUsageFinder polyfillUsageFinder = null;
      private @Nullable Node rootNode = null;
      private @Nullable ColorRegistry colorRegistry = null;
      private boolean disambiguateProperties = false;

      Builder withPolyfillTableLines(String polyfillTable) {
        polyfillsTable = Polyfills.fromTable(polyfillTable);
        return this;
      }

      Builder withColorRegistry(ColorRegistry colorRegistry) {
        this.colorRegistry = colorRegistry;
        return this;
      }

      Builder withDisambiguateProperties(boolean disambiguateProperties) {
        this.disambiguateProperties = disambiguateProperties;
        return this;
      }

      private Builder forSourceLines(String src) {
        final SourceFile srcFile = SourceFile.builder().withPath("src.js").withContent(src).build();
        final CompilerOptions options = new CompilerOptions();
        // Don't include `"use strict";` when printing the AST as source text
        options.setEmitUseStrict(false);
        compiler = new TestCompiler();
        compiler.init(
            ImmutableList.of(new TestExternsBuilder().buildExternsFile("externs.js")),
            ImmutableList.of(srcFile),
            options);
        compiler.parse();
        rootNode = compiler.getJsRoot();
        return this;
      }

      TestPolyfillUsageFinder build() {
        checkNotNull(compiler);
        if (this.colorRegistry != null) {
          compiler.setTestColorRegistry(this.colorRegistry);
        }
        polyfillUsageFinder =
            new PolyfillUsageFinder(
                compiler, checkNotNull(polyfillsTable), this.disambiguateProperties);
        return new TestPolyfillUsageFinder(this);
      }
    }

    TestPolyfillUsageFinder(Builder builder) {
      this.polyfillUsageFinder = builder.polyfillUsageFinder;
      this.rootNode = builder.rootNode;
    }

    Node getRootNode() {
      return rootNode;
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

    PolyfillSubject hasKind(Kind expectedKind) {
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
