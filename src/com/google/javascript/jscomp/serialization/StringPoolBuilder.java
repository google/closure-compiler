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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashMap;

/**
 * Aggregates strings into a {@link StringPool}
 *
 * <p>The zeroth offset in the string pool is always the empty string. This is validated inside
 * {@link TypedAstDeserializer}.
 *
 * <p>This implies default/unset/0-valued uuint32 StringPool pointers in protos are equivalent to
 * the empty string.
 */
final class StringPoolBuilder {
  private final LinkedHashMap<String, Integer> stringPool = new LinkedHashMap<>();
  private int maxLength = 0;

  StringPoolBuilder() {
    this.put("");
  }

  /** Inserts the given string into the string pool if not present and returns its index */
  int put(String string) {
    checkNotNull(string);

    if (string.length() > this.maxLength) {
      this.maxLength = string.length();
    }

    return this.stringPool.computeIfAbsent(string, (unused) -> this.stringPool.size());
  }

  StringPool build() {
    StringPool.Builder builder = StringPool.newBuilder().setMaxLength(this.maxLength);
    this.stringPool.keySet().stream().map(Wtf8::encodeToWtf8).forEachOrdered(builder::addStrings);
    return builder.build();
  }
}
