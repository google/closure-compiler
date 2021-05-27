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

package com.google.javascript.jscomp.disambiguate;

/**
 * Describes one way in which a property became invalidated.
 *
 * <p>This information is only used diagnostically and doesn't affect the outcome of optimizations.
 * Since diagnostic information isn't usually rendered, this class is designed to defer work as much
 * as possible.
 */
@SuppressWarnings({"UnusedVariable", "FieldCanBeLocal"}) // GSON reflection.
class Invalidation {

  static Invalidation wellKnownProperty() {
    return WELL_KNOWN_PROPERTY;
  }

  static Invalidation invalidatingType(int recieverType) {
    return new WithRecieverType(Reason.INVALIDATING_TYPE, recieverType);
  }

  static Invalidation undeclaredAccess(int recieverType) {
    return new WithRecieverType(Reason.UNDECLARED_ACCESS, recieverType);
  }

  private enum Reason {
    /** Certain well-known properties like "prototype" are always invalidated. */
    WELL_KNOWN_PROPERTY,
    /** Properties accessed on invalidating types (like Object) are invalidated. */
    INVALIDATING_TYPE,
    /** Properties accessed on types that don't declare them are invalidated. */
    UNDECLARED_ACCESS;
  }

  private final Reason reason;

  private Invalidation(Reason reason) {
    this.reason = reason;
  }

  /** Anonymous classes are not serialized correctly by GSON. */
  private static final class WithRecieverType extends Invalidation {
    final int recieverType;

    WithRecieverType(Reason reason, int recieverType) {
      super(reason);
      this.recieverType = recieverType;
    }
  }

  private static final Invalidation WELL_KNOWN_PROPERTY =
      new Invalidation(Reason.WELL_KNOWN_PROPERTY);
}
