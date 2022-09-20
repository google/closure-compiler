/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.nullness.Nullable;

/**
 * Fail-fast replacement for `ColorSerializer`.
 *
 * <p>Serialization cannot be supported for the j2cl-compiled version of closure-compiler, so it
 * will use this replacement class that throws exceptions if it is ever used.
 */
class ColorSerializer {
  ColorSerializer(
      SerializationOptions serializationMode,
      Function<String, Integer> stringPoolIndexFn,
      Predicate<String> propertyFilter) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }

  ImmutableList<Integer> addColors(Collection<Color> colors) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }

  Integer addColor(Color color) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }

  TypePool generateTypePool(
      Function<Color, ImmutableSet<Color>> getDisambiguationSupertypesFn,
      @Nullable Function<Color, ImmutableSet<String>> getMismatchSourceRefsFn) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }
}
