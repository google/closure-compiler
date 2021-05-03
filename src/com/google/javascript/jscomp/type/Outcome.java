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
package com.google.javascript.jscomp.type;

import com.google.javascript.jscomp.base.Tri;

/**
 * An enum for representing boolean outcomes. Currently they just represent typical booleans but
 * this enum will be expanded to specifically differentiate between falsy and explicitly nullish
 * values. e.g. 0 is falsy but not nullish.
 */
public enum Outcome {
  /** Represents truthy values. For example: {}, true, 1, etc. */
  TRUE {
    @Override
    public boolean isTruthy() {
      return true;
    }

    @Override
    public Tri isNullish() {
      return Tri.FALSE;
    }

    @Override
    public Outcome not() {
      return Outcome.FALSE;
    }
  },

  /** Represents falsy values. For examples: '', 0, false, null, etc. */
  FALSE {
    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public Tri isNullish() {
      return Tri.UNKNOWN;
    }

    @Override
    public Outcome not() {
      return Outcome.TRUE;
    }
  };

  /** Determines whether an Outcome enum value is truthy. */
  public abstract boolean isTruthy();

  /**
   * Determines whether an Outcome enum value is nullish. Using Tri instead of a boolean because 0
   * is Outcome.FALSE but not nullish so sometimes it is unclear.
   */
  public abstract Tri isNullish();

  /** Gets the {@code not} of {@code this}. */
  public abstract Outcome not();

  /** Gets the Outcome for the given boolean. */
  public static Outcome forBoolean(boolean val) {
    return val ? Outcome.TRUE : Outcome.FALSE;
  }
}
