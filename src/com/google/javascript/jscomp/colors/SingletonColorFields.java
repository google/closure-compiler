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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import javax.annotation.Nullable;

/**
 * Fields present on a color representing a primitive or object. Note that some may be null except
 * for on certain colors like functions
 *
 * <p>Only the {@link Builder} for this class is public. This is intentional. All getters should be
 * exposed via accessors on {@link Color} instead of on this class directly, as we want to make the
 * API of union and singleton colors almost identical.
 */
@AutoValue
@Immutable
public abstract class SingletonColorFields implements Serializable {

  abstract ColorId getId();

  abstract DebugInfo getDebugInfo();

  // given `function Foo() {}` or `class Foo {}`, color of Foo.prototype. null otherwise.
  @Nullable
  abstract Color getPrototype();

  @Nullable
  abstract Color getInstanceColor();

  // List of other colors directly above this in the subtyping graph for the purposes of property
  // (dis)ambiguation.
  abstract ImmutableList<Color> getDisambiguationSupertypes();

  abstract boolean isInvalidating();

  abstract boolean getPropertiesKeepOriginalName();

  abstract boolean isConstructor();

  // property names 'declared' on an object (as opposed to being conceptually inherited from some
  // supertype).
  abstract ImmutableSet<String> getOwnProperties();

  @Nullable
  abstract NativeColorId getNativeColorId();

  abstract boolean isClosureAssert();

  /**
   * Builder for a singleton color. Should be passed to {@link
   * Color#createSingleton(SingletonColorFields)} after building and before using
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(ColorId value);

    public abstract Builder setInvalidating(boolean value);

    public abstract Builder setPropertiesKeepOriginalName(boolean value);

    public abstract Builder setDisambiguationSupertypes(ImmutableList<Color> supertypes);

    public abstract Builder setPrototype(Color prototype);

    public abstract Builder setInstanceColor(Color instanceColor);

    public abstract Builder setConstructor(boolean isConstructor);

    public abstract Builder setOwnProperties(ImmutableSet<String> ownProperties);

    public abstract Builder setDebugInfo(DebugInfo debugInfo);

    public abstract Builder setNativeColorId(NativeColorId id);

    public abstract Builder setClosureAssert(boolean isClosurePrimitiveAssertion);

    @VisibleForTesting
    public Builder setDebugName(String name) {
      setDebugInfo(DebugInfo.builder().setClassName(name).build());
      return this;
    }

    public abstract SingletonColorFields build();
  }

  public static Builder builder() {
    return new AutoValue_SingletonColorFields.Builder()
        .setDebugInfo(DebugInfo.EMPTY)
        .setInvalidating(false)
        .setPropertiesKeepOriginalName(false)
        .setDisambiguationSupertypes(ImmutableList.of())
        .setConstructor(false)
        .setOwnProperties(ImmutableSet.of())
        .setClosureAssert(false);
  }
}
