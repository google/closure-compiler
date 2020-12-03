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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Memoizes all native color instances */
public final class ColorRegistry {
  ImmutableMap<NativeColorId, Color> nativeColors;

  private ColorRegistry(ImmutableMap<NativeColorId, Color> nativeColors) {
    this.nativeColors = nativeColors;
  }

  public static ColorRegistry createWithInvalidatingNatives(
      ImmutableSet<NativeColorId> invalidatingNativeColors) {
    ImmutableMap.Builder<NativeColorId, Color> nativeColors = ImmutableMap.builder();

    for (NativeColorId nativeColorId : NativeColorId.values()) {
      addNativeColor(nativeColors, nativeColorId, invalidatingNativeColors);
    }

    return new ColorRegistry(nativeColors.build());
  }

  private static void addNativeColor(
      ImmutableMap.Builder<NativeColorId, Color> nativeColors,
      NativeColorId nativeColorId,
      ImmutableSet<NativeColorId> invalidatingNativeColors) {
    SingletonColorFields fields =
        SingletonColorFields.builder()
            .setNativeColorId(nativeColorId)
            .setId("__nativeColor__" + nativeColorId.name())
            .setDebugInfo(DebugInfo.builder().setClassName(nativeColorId.name()).build())
            .setInvalidating(invalidatingNativeColors.contains(nativeColorId))
            .build();
    nativeColors.put(nativeColorId, Color.createSingleton(fields));
  }

  public final Color get(NativeColorId nativeColorId) {
    return this.nativeColors.get(checkNotNull(nativeColorId));
  }
}
