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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;

/**
 * Useful debugging info for Color objects that may or may not be populated, and thus should not
 * effect their value.
 */
@AutoValue
@Immutable
public abstract class DebugInfo {
  /**
   * Since null and non-null objects would unequal, DebugInfo fields should never be null. Instead,
   * we suggest using this unintersting empty instance to signal absence of meaningful debug info.
   */
  public static final DebugInfo EMPTY = builder().build();

  public abstract String getCompositeTypename();

  /** Builder for {@link DebugInfo} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCompositeTypename(String value);

    public abstract DebugInfo build();
  }

  public static Builder builder() {
    return new AutoValue_DebugInfo.Builder().setCompositeTypename("");
  }

  /** All DebugInfo objects are equal */
  @Override
  public final boolean equals(Object o) {
    return o instanceof DebugInfo;
  }

  /** All DebugInfo objects have the same hashCode */
  @Override
  public final int hashCode() {
    return 0;
  }
}
