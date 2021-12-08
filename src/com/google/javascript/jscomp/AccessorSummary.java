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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.Map;

/** A strongly typed view of information about getters and setters collected from the AST. */
@Immutable
final class AccessorSummary implements Serializable {

  /** Indicates whether a property has a getter or a setter, or both. */
  public enum PropertyAccessKind {
    // To save space properties without getters or setters won't appear
    // in the maps at all, but NORMAL will be returned by some methods.
    NORMAL(0),
    GETTER_ONLY(1),
    SETTER_ONLY(2),
    GETTER_AND_SETTER(3);

    final byte flags;

    PropertyAccessKind(int flags) {
      this.flags = (byte) flags;
    }

    boolean hasGetter() {
      return (flags & 1) != 0;
    }

    boolean hasSetter() {
      return (flags & 2) != 0;
    }

    boolean hasGetterOrSetter() {
      return (flags & 3) != 0;
    }

    // used to combine information from externs and from sources
    PropertyAccessKind unionWith(PropertyAccessKind other) {
      int combinedFlags = this.flags | other.flags;
      switch (combinedFlags) {
        case 0:
          return NORMAL;
        case 1:
          return GETTER_ONLY;
        case 2:
          return SETTER_ONLY;
        case 3:
          return GETTER_AND_SETTER;
        default:
          throw new IllegalStateException("unexpected value: " + combinedFlags);
      }
    }
  }

  static AccessorSummary create(Map<String, PropertyAccessKind> accessors) {
    // TODO(nickreid): Efficiently verify that no entry in `accessor` is `NORMAL`.
    return new AccessorSummary(ImmutableMap.copyOf(accessors));
  }

  private final ImmutableMap<String, PropertyAccessKind> accessors;

  private AccessorSummary(ImmutableMap<String, PropertyAccessKind> accessors) {
    this.accessors = accessors;
  }

  public ImmutableMap<String, PropertyAccessKind> getAccessors() {
    return accessors;
  }

  public PropertyAccessKind getKind(String name) {
    return accessors.getOrDefault(name, PropertyAccessKind.NORMAL);
  }
}
