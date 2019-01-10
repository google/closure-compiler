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

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplateTypeMap;

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
   * <p>If the given type is not an `Iterator` or `Iterable`, returns the unknown type.
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
    } else if (templateTypeMap.hasTemplateKey(typeRegistry.getIteratorTemplate())) {
      // `Iterator<SomeElementType>`
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getIteratorTemplate());
    }
    return typeRegistry.getNativeType(UNKNOWN_TYPE);
  }

  private JsIterables() {}
}
