/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.rhino.TypeI;
import java.util.Objects;

/**
 * Signals that the first type and the second type have been
 * used interchangeably.
 *
 * Type-based optimizations should take this into account
 * so that they don't wreck code with type warnings.
 */
class TypeMismatch {
  final TypeI typeA;
  final TypeI typeB;
  final JSError src;

  /**
   * It's the responsibility of the class that creates the
   * {@code TypeMismatch} to ensure that {@code a} and {@code b} are
   * non-matching types.
   */
  TypeMismatch(TypeI a, TypeI b, JSError src) {
    this.typeA = a;
    this.typeB = b;
    this.src = src;
  }

  @Override public boolean equals(Object object) {
    if (object instanceof TypeMismatch) {
      TypeMismatch that = (TypeMismatch) object;
      return (that.typeA.equals(this.typeA) && that.typeB.equals(this.typeB))
          || (that.typeB.equals(this.typeA) && that.typeA.equals(this.typeB));
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hash(typeA, typeB);
  }

  @Override public String toString() {
    return "(" + typeA + ", " + typeB + ")";
  }
}
