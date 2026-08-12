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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;

/** A strongly typed view of information about getters and setters collected from the AST. */
@Immutable
final class AccessorSummary {

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
      return switch (combinedFlags) {
        case 0 -> NORMAL;
        case 1 -> GETTER_ONLY;
        case 2 -> SETTER_ONLY;
        case 3 -> GETTER_AND_SETTER;
        default -> throw new IllegalStateException("unexpected value: " + combinedFlags);
      };
    }
  }

  private final boolean assumeAlwaysGetterAndSetter;

  static AccessorSummary create(Map<String, PropertyAccessKind> accessors) {
    // TODO(nickreid): Efficiently verify that no entry in `accessor` is `NORMAL`.
    return new AccessorSummary(ImmutableMap.copyOf(accessors));
  }

  private final ImmutableMap<String, PropertyAccessKind> accessors;

  private AccessorSummary(ImmutableMap<String, PropertyAccessKind> accessors) {
    this.accessors = accessors;
    this.assumeAlwaysGetterAndSetter = false;
  }

  private AccessorSummary(boolean assumeAlwaysGetterAndSetter) {
    this.accessors = ImmutableMap.of();
    this.assumeAlwaysGetterAndSetter = assumeAlwaysGetterAndSetter;
  }

  public ImmutableMap<String, PropertyAccessKind> getAccessors() {
    return accessors;
  }

  public PropertyAccessKind getKind(String name) {
    if (assumeAlwaysGetterAndSetter) {
      return PropertyAccessKind.GETTER_AND_SETTER;
    }
    return accessors.getOrDefault(name, PropertyAccessKind.NORMAL);
  }

  /** Returns an accessor summary that assumes every access is a potential getter or setter. */
  public static AccessorSummary createAssumingAlwaysGetterAndSetter() {
    return new AccessorSummary(true);
  }

  public AccessorSummaryProto toProto() {
    AccessorSummaryProto.Builder builder =
        AccessorSummaryProto.newBuilder()
            .setAssumeAlwaysGetterAndSetter(assumeAlwaysGetterAndSetter);
    if (assumeAlwaysGetterAndSetter) {
      checkState(accessors.isEmpty());
    } else {
      for (Map.Entry<String, PropertyAccessKind> entry : accessors.entrySet()) {
        builder.addAccessors(
            AccessorSummaryEntryProto.newBuilder()
                .setName(entry.getKey())
                .setKind(toProto(entry.getValue()))
                .build());
      }
    }
    return builder.build();
  }

  private static PropertyAccessKindProto toProto(PropertyAccessKind kind) {
    return switch (kind) {
      case NORMAL -> PropertyAccessKindProto.KIND_NORMAL;
      case GETTER_ONLY -> PropertyAccessKindProto.KIND_GETTER_ONLY;
      case SETTER_ONLY -> PropertyAccessKindProto.KIND_SETTER_ONLY;
      case GETTER_AND_SETTER -> PropertyAccessKindProto.KIND_GETTER_AND_SETTER;
    };
  }

  public static AccessorSummary fromProto(AccessorSummaryProto proto) {
    if (proto.getAssumeAlwaysGetterAndSetter()) {
      return createAssumingAlwaysGetterAndSetter();
    }
    ImmutableMap.Builder<String, PropertyAccessKind> builder = ImmutableMap.builder();
    for (AccessorSummaryEntryProto entry : proto.getAccessorsList()) {
      builder.put(entry.getName(), fromProto(entry.getKind()));
    }
    return create(builder.buildOrThrow());
  }

  private static PropertyAccessKind fromProto(PropertyAccessKindProto kind) {
    return switch (kind) {
      case KIND_NORMAL -> PropertyAccessKind.NORMAL;
      case KIND_GETTER_ONLY -> PropertyAccessKind.GETTER_ONLY;
      case KIND_SETTER_ONLY -> PropertyAccessKind.SETTER_ONLY;
      case KIND_GETTER_AND_SETTER -> PropertyAccessKind.GETTER_AND_SETTER;
      default -> throw new IllegalArgumentException("Unknown kind: " + kind);
    };
  }
}
