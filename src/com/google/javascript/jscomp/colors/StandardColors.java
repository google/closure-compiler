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

package com.google.javascript.jscomp.colors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Colors that are expected to be referencable as part of any compilation.
 *
 * <p>This set describes any Color that needs to be used a priori by a compiler pass. That includes:
 *
 * <ul>
 *   <li>"axiomatic" Colors (those with constant definitions, e.g. UNKNOWN)
 *   <li>primitive Colors (e.g. `number`, `null`)
 *   <li>box Colors (e.g. `Number`, `String`)
 *   <li>Colors used in AstFactory to type synthetic nodes (e.g. `Array`)
 * </ul>
 */
public final class StandardColors {

  // These IDs are randomly chosen.
  public static final ColorId ARGUMENTS_ID = ColorId.fromUnsigned(0x1939a66d);
  public static final ColorId ARRAY_ID = ColorId.fromUnsigned(0x79d4a603);
  public static final ColorId GENERATOR_ID = ColorId.fromUnsigned(0x9bb1303f);
  public static final ColorId I_TEMPLATE_ARRAY_ID = ColorId.fromUnsigned(0x46ab3f0e);
  public static final ColorId ITERATOR_ID = ColorId.fromUnsigned(0x417ed2ab);
  public static final ColorId ASYNC_ITERATOR_ITERABLE_ID = ColorId.fromUnsigned(0xcb382e0a);
  public static final ColorId PROMISE_ID = ColorId.fromUnsigned(0x39581abf);

  public static final ColorId BIGINT_OBJECT_ID = ColorId.fromUnsigned(0xa9d9ad6d);
  public static final ColorId BOOLEAN_OBJECT_ID = ColorId.fromUnsigned(0x9205dc06);
  public static final ColorId NUMBER_OBJECT_ID = ColorId.fromUnsigned(0x34ba2fb1);
  public static final ColorId STRING_OBJECT_ID = ColorId.fromUnsigned(0x186008a9);
  public static final ColorId SYMBOL_OBJECT_ID = ColorId.fromUnsigned(0x5e514f7e);

  public static final Color BIGINT =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0x234eb61a))
          .setBoxId(BIGINT_OBJECT_ID)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("bigint").build())
          .setInvalidating(false)
          .buildAxiomatic();

  public static final Color BOOLEAN =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0x126812ee))
          .setBoxId(BOOLEAN_OBJECT_ID)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("boolean").build())
          .setInvalidating(false)
          .buildAxiomatic();

  public static final Color NULL_OR_VOID =
      Color.singleBuilder()
          .setBoxId(null)
          .setId(ColorId.fromUnsigned(0x22b49f69))
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("null_or_void").build())
          .setInvalidating(false)
          .buildAxiomatic();

  public static final Color NUMBER =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0xd081722c))
          .setBoxId(NUMBER_OBJECT_ID)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("number").build())
          .setInvalidating(false)
          .buildAxiomatic();

  public static final Color STRING =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0x8c4d8f65))
          .setBoxId(STRING_OBJECT_ID)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("string").build())
          .setInvalidating(false)
          .buildAxiomatic();

  public static final Color SYMBOL =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0x759f2066))
          .setBoxId(SYMBOL_OBJECT_ID)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("symbol").build())
          .setInvalidating(false)
          .buildAxiomatic();

  /**
   * The supertype of all objects but not primitives.
   *
   * <p>Separate from UNKNOWN because some optimizations back off on any non-object primitives +
   * unknown but operate on the top object.
   */
  public static final Color TOP_OBJECT =
      Color.singleBuilder()
          .setId(ColorId.fromUnsigned(0x889b6838))
          .setBoxId(null)
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("top_object").build())
          .setInvalidating(true)
          .buildAxiomatic();

  /** Analagous to Closure '*'/'?' and TS unknown/any */
  public static final Color UNKNOWN =
      Color.singleBuilder()
          .setBoxId(null)
          .setId(ColorId.fromUnsigned(0)) // Make UNKNOWN the "default" numerical value.
          .setDebugInfo(DebugInfo.builder().setCompositeTypename("unknown").build())
          .setInvalidating(true)
          .buildAxiomatic();

  /**
   * The set of Colors whose definitions are independent of all JS code.
   *
   * <p>Because they have no dependencies, they are instantated once, here, and made globally
   * available. No other Color object may ever have these IDs.
   *
   * <p>Some axiomatic Colors do not have an associated runtime type or set of runtime instances.
   * These represent abstract concepts, rather than anything in a JSVM.
   */
  public static final ImmutableMap<ColorId, Color> AXIOMATIC_COLORS =
      ImmutableMap.<ColorId, Color>builder()
          .put(BIGINT.getId(), BIGINT)
          .put(BOOLEAN.getId(), BOOLEAN)
          .put(NULL_OR_VOID.getId(), NULL_OR_VOID)
          .put(NUMBER.getId(), NUMBER)
          .put(STRING.getId(), STRING)
          .put(SYMBOL.getId(), SYMBOL)
          .put(TOP_OBJECT.getId(), TOP_OBJECT)
          .put(UNKNOWN.getId(), UNKNOWN)
          .buildOrThrow();

  /**
   * The set of Colors that have associated runtime values but are not objects.
   *
   * <p>In JS, instances of primitve Colors are deeply immutable.
   */
  public static final ImmutableMap<ColorId, Color> PRIMITIVE_COLORS =
      ImmutableMap.<ColorId, Color>builder()
          .put(BIGINT.getId(), BIGINT)
          .put(BOOLEAN.getId(), BOOLEAN)
          .put(NULL_OR_VOID.getId(), NULL_OR_VOID)
          .put(NUMBER.getId(), NUMBER)
          .put(STRING.getId(), STRING)
          .put(SYMBOL.getId(), SYMBOL)
          .buildOrThrow();

  /**
   * The set of ColorIds for object Colors that "box" primitive Colors.
   *
   * <p>Boxing is when the JSVM automatically wraps a primitive value in a temporary object so it
   * can participate in a method call or other object-only operation.
   *
   * <p>The eaxct definition of each box Color may be altered by JS code.
   */
  public static final ImmutableSet<ColorId> PRIMITIVE_BOX_IDS =
      ImmutableSet.of(
          BIGINT_OBJECT_ID,
          BOOLEAN_OBJECT_ID,
          NUMBER_OBJECT_ID,
          STRING_OBJECT_ID,
          SYMBOL_OBJECT_ID);

  /**
   * The set of ColorIds for object Colors that need to be referenced in optimizations
   *
   * <p>The exact definition of each box Color may be altered by JS code.
   */
  static final ImmutableSet<ColorId> STANDARD_OBJECT_IDS =
      ImmutableSet.<ColorId>builder()
          .addAll(PRIMITIVE_BOX_IDS)
          .add(ARRAY_ID)
          .add(ARGUMENTS_ID)
          .add(ASYNC_ITERATOR_ITERABLE_ID)
          .add(GENERATOR_ID)
          .add(I_TEMPLATE_ARRAY_ID)
          .add(ITERATOR_ID)
          .add(PROMISE_ID)
          .build();

  private StandardColors() {
    throw new AssertionError();
  }
}
