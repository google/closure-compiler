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

package com.google.javascript.jscomp.serialization;

import com.google.common.collect.ImmutableMultimap;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.function.Predicate;

/** Fail-fast replacement */
public abstract class JSTypeReconserializer {

  public static JSTypeReconserializer create(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      StringPool.Builder stringPoolBuilder,
      Predicate<String> shouldPropagatePropertyName,
      SerializationOptions serializationMode) {
    throw new UnsupportedOperationException();
  }

  private JSTypeReconserializer() {}

  abstract TypePool generateTypePool();

  abstract ImmutableMultimap<String, String> getColorIdToJSTypeMapForDebugging();

  abstract Integer serializeType(JSType type);
}
