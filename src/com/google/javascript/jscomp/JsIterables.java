/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * Models type transformations of JavaScript `Iterable` and `Iterator` types.
 *
 * <p>These tranformations can be especially helpful when working with generator functions.
 * `Generator` is a subtype of `Iterable`.
 */
final class JsIterables {

  /**
   * Returns the given `Iterable`s element type.
   *
   * <p>If the given type is not an `Iterator`, `Iterable`, `AsyncIterator`, or `AsyncIterable`,
   * returns the unknown type.
   */
  static final JSType getElementType(JSType iterableOrIterator, JSTypeRegistry typeRegistry) {
    TemplateTypeMap templateTypeMap =
        iterableOrIterator
            // Remember that `string` will box to a `Iterable`.
            .autobox()
            .getTemplateTypeMap();

    if (templateTypeMap.hasTemplateKey(typeRegistry.getIterableTemplate())) {
      // `Iterable<SomeElementType>` or `Generator<SomeElementType>`
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getIterableTemplate());
    } else if (templateTypeMap.hasTemplateKey(typeRegistry.getIteratorValueTemplate())) {
      // `Iterator<SomeElementType>`
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getIteratorValueTemplate());
    } else if (templateTypeMap.hasTemplateKey(typeRegistry.getAsyncIterableTemplate())) {
      // `AsyncIterable<SomeElementType>` or `AsyncGenerator<SomeElementType>`
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getAsyncIterableTemplate());
    } else if (templateTypeMap.hasTemplateKey(typeRegistry.getAsyncIteratorValueTemplate())) {
      // `AsyncIterator<SomeElementType>`
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getAsyncIteratorValueTemplate());
    }
    return typeRegistry.getNativeType(UNKNOWN_TYPE);
  }

  /**
   * Returns an `Iterable` type templated on {@code elementType}.
   *
   * <p>Example: `number' => `Iterable<number>`.
   */
  static final JSType createIterableTypeOf(JSType elementType, JSTypeRegistry typeRegistry) {
    return typeRegistry.createTemplatizedType(
        typeRegistry.getNativeObjectType(ITERABLE_TYPE), elementType);
  }

  /**
   * The template of an Iterable|AsyncIterable, or a mismatch if subtype of Iterable|AsyncIterable.
   */
  static final class MaybeBoxedType {
    private final JSType templatedType;
    private final JSType mismatchType;

    /**
     * @param templatedType the unwrapped type that is being yieled by the iterable / async
     *     iterable. e.g. in {@code Iterable<number>|AsyncIterable<Qux>|string}, this should be
     *     {@code number|Qux|string}. Null if a mismatch.
     * @param mismatchType the type that caused the mismatch. This can be the entire type (e.g.
     *     {@code number} is not iterable or async iterable) or a piece of a union that caused the
     *     mismatch (e.g. {@code number} in {@code number|Iterable<Qux>}). Null if a match.
     */
    private MaybeBoxedType(@Nullable JSType templatedType, @Nullable JSType mismatchType) {
      this.templatedType = templatedType;
      this.mismatchType = mismatchType;
    }

    /**
     * @return the template of the iterable / async iterable
     * @throws if not an iterable / async iterable
     */
    JSType getTemplatedType() {
      if (!isMatch()) {
        throw new IllegalStateException("Type was not boxable to iterable or async iterable!");
      }
      return templatedType;
    }

    /** @return the type that caused the mismatch, if any */
    JSType getMismatchType() {
      return mismatchType;
    }

    JSType orElse(JSType type) {
      if (isMatch()) {
        return templatedType;
      }

      return type;
    }

    boolean isMatch() {
      return templatedType != null;
    }
  }

  /**
   * Returns the template for iterable types.
   *
   * <p>This does not apply to async-iterables.
   *
   * <p>If this value is not an Iterable, we return a sentinel.
   */
  static final MaybeBoxedType maybeBoxIterable(JSType type, JSTypeRegistry typeRegistry) {
    return maybeBoxIterableAndPossiblyAsyncIterable(
        type, typeRegistry, /* includeAsyncIterable= */ false);
  }

  /**
   * Returns the template for iterable or async-iterable types.
   *
   * <p>If this value is not an Iterable or AsyncIterable, we return a sentinel.
   */
  static final MaybeBoxedType maybeBoxIterableOrAsyncIterable(
      JSType type, JSTypeRegistry typeRegistry) {
    return maybeBoxIterableAndPossiblyAsyncIterable(
        type, typeRegistry, /* includeAsyncIterable= */ true);
  }

  /**
   * Given a type, if it is an iterable or async iterable, will return its template. If not a
   * subtype of Iterable|AsyncIterable, returns an object that has no match, and will indicate the
   * mismatch. e.g. both {@code number} and {@code number|Iterable} are not subtypes of
   * Iterable|AsyncIterable.
   *
   * @param includeAsyncIterable whether to look for AsyncIterable as opposed to just Iterable
   */
  private static final MaybeBoxedType maybeBoxIterableAndPossiblyAsyncIterable(
      JSType type, JSTypeRegistry typeRegistry, boolean includeAsyncIterable) {
    List<JSType> templatedTypes = new ArrayList<>();

    // Note: we don't just use JSType.autobox() here because that removes null and undefined.
    // We want to keep null and undefined around because they should cause a mismatch.
    if (type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        alt = alt.isBoxableScalar() ? alt.autoboxesTo() : alt;
        boolean isIterable = alt.isSubtypeOf(typeRegistry.getNativeType(ITERABLE_TYPE));
        boolean isAsyncIterable =
            includeAsyncIterable
                && alt.isSubtypeOf(typeRegistry.getNativeType(ASYNC_ITERABLE_TYPE));
        if (!isIterable && !isAsyncIterable) {
          return new MaybeBoxedType(null, alt);
        }
        TemplateType valueTemplate =
            isAsyncIterable
                ? typeRegistry.getAsyncIterableTemplate()
                : typeRegistry.getIterableTemplate();
        templatedTypes.add(alt.getTemplateTypeMap().getResolvedTemplateType(valueTemplate));
      }
    } else {
      JSType autoboxedType = type.isBoxableScalar() ? type.autoboxesTo() : type;
      boolean isIterable = autoboxedType.isSubtypeOf(typeRegistry.getNativeType(ITERABLE_TYPE));
      boolean isAsyncIterable =
          includeAsyncIterable
              && autoboxedType.isSubtypeOf(typeRegistry.getNativeType(ASYNC_ITERABLE_TYPE));
      if (!isIterable && !isAsyncIterable) {
        return new MaybeBoxedType(null, autoboxedType);
      }
      TemplateType templateType =
          isAsyncIterable
              ? typeRegistry.getAsyncIterableTemplate()
              : typeRegistry.getIterableTemplate();
      templatedTypes.add(autoboxedType.getTemplateTypeMap().getResolvedTemplateType(templateType));
    }
    return new MaybeBoxedType(typeRegistry.createUnionType(templatedTypes), null);
  }

  private JsIterables() {}
}
