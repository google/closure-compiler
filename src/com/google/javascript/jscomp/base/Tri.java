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

package com.google.javascript.jscomp.base;

import java.util.Locale;

/**
 * An enum for ternary logic.
 *
 * <p>The {@link #TRUE} and {@link #FALSE} values are equivalent to typical booleans, and the {@link
 * #UNKNOWN} value plays the role of a placeholder, which can be either {@link #TRUE} or {@link
 * #FALSE}.
 *
 * <p>A ternary value expression evaluates to {@link #TRUE} or {@link #FALSE} only if all
 * replacements of {@link #UNKNOWN} in this expression yield the same result. Therefore, the ternary
 * logic coincides with typical Boolean logic if the {@link #UNKNOWN} value is not present in an
 * expression.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Ternary_logic">Ternary Logic</a>
 */
public enum Tri {
  FALSE(-1),
  UNKNOWN(0),
  TRUE(1);

  private final int value;

  private Tri(int value) {
    this.value = value;
  }

  public Tri or(Tri x) {
    return (this.value > x.value) ? this : x;
  }

  public Tri and(Tri x) {
    return (this.value < x.value) ? this : x;
  }

  public Tri xor(Tri x) {
    return forInt(-this.value * x.value);
  }

  public Tri not() {
    return forInt(-this.value);
  }

  public boolean toBoolean(boolean x) {
    switch (this) {
      case FALSE:
        return false;
      case UNKNOWN:
        return x;
      case TRUE:
        return true;
    }
    throw new AssertionError(this);
  }

  public static Tri forBoolean(boolean x) {
    return x ? TRUE : FALSE;
  }

  @Override
  public String toString() {
    return this.name().toLowerCase(Locale.ROOT);
  }

  private static Tri forInt(int x) {
    return VALUES[x + 1];
  }

  private static final Tri[] VALUES = values();
}
