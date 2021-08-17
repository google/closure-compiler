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

package com.google.javascript.jscomp.serialization;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;
import static com.google.javascript.jscomp.serialization.MalformedTypedAstException.checkWellFormed;
import static com.google.javascript.jscomp.serialization.TypePointers.OFFSET_TO_AXIOMATIC_COLOR;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;
import static com.google.javascript.jscomp.serialization.TypePointers.untrimOffset;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.base.IdentityRef;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.StandardColors;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeSet;

/**
 * A set of {@link Color}s reconstructed from possibly many {@link TypePool} protos.
 *
 * <p>Protos representing the same Color are reconciled while the pool is built. Reconciliation
 * ensures that, within a pool, each ColorId corresponds to at most one Color.
 *
 * <p>It's possible for two Color objects with the same ID to have different definitions between
 * different ColorPools, but mixing multiple ColorPools is likely a design error.
 */
public final class ColorPool {
  private final ImmutableMap<ColorId, Color> idToColor;
  private final ImmutableList<ShardView> shardViews;
  private final ColorRegistry colorRegistry;

  private ColorPool(Builder builder) {
    this.idToColor = ImmutableMap.copyOf(builder.idToColor);
    this.shardViews = ImmutableList.copyOf(builder.protoToShard.values());
    this.colorRegistry = builder.registry.build();
  }

  public Color getColor(ColorId id) {
    return this.idToColor.get(id);
  }

  public ColorRegistry getRegistry() {
    return this.colorRegistry;
  }

  public ShardView getOnlyShard() {
    return Iterables.getOnlyElement(this.shardViews);
  }

  /** A view of the pool based on one of the input shards. */
  public static final class ShardView {
    private final TypePool typePool;
    private final StringPool stringPool;
    private final ImmutableList<ColorId> trimmedOffsetToId;

    private ColorPool colorPool; // Set once the complete pool is built.

    private ShardView(
        TypePool typePool, StringPool stringPool, ImmutableList<ColorId> trimmedOffsetToId) {
      this.typePool = typePool;
      this.stringPool = stringPool;
      this.trimmedOffsetToId = trimmedOffsetToId;
    }

    public Color getColor(TypePointer pointer) {
      checkState(this.colorPool != null, this);
      return this.colorPool.getColor(this.getId(pointer));
    }

    private ColorId getId(TypePointer pointer) {
      return this.getId(pointer.getPoolOffset());
    }

    private ColorId getId(int untrimmedOffset) {
      if (isAxiomatic(untrimmedOffset)) {
        return OFFSET_TO_AXIOMATIC_COLOR.get(untrimmedOffset).getId();
      } else {
        return this.trimmedOffsetToId.get(trimOffset(untrimmedOffset));
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ColorPool fromOnlyShard(TypePool typePool, StringPool stringPool) {
    return ColorPool.builder().addShardAnd(typePool, stringPool).build();
  }

  /**
   * Collects {@link TypePool}s and other data into {@link ShardView}s and then reconciles them into
   * a single {@link ColorPool}.
   */
  public static final class Builder {
    private final LinkedHashMap<IdentityRef<TypePool>, ShardView> protoToShard =
        new LinkedHashMap<>();
    private final LinkedHashMap<ColorId, Color> idToColor = new LinkedHashMap<>();
    private final ColorRegistry.Builder registry = ColorRegistry.builder();
    private final HashBasedTable<ColorId, ShardView, TypeProto> idToProto = HashBasedTable.create();

    private final ArrayDeque<ColorId> reconcilationDebugStack = new ArrayDeque<>();

    private Builder() {
      this.idToColor.putAll(StandardColors.AXIOMATIC_COLORS);
    }

    public Builder addShardAnd(TypePool typePool, StringPool stringPool) {
      this.addShard(typePool, stringPool);
      return this;
    }

    public ShardView addShard(TypePool typePool, StringPool stringPool) {
      checkState(this.idToProto.isEmpty(), "build has already been called");
      IdentityRef<TypePool> typePoolRef = IdentityRef.of(typePool);

      ShardView existing = this.protoToShard.get(typePoolRef);
      if (existing != null) {
        checkState(identical(typePool, TypePool.getDefaultInstance()), typePool);
        return existing;
      }

      ImmutableList<ColorId> trimmedOffsetToId = createTrimmedOffsetToId(typePool);
      ShardView shard = new ShardView(typePool, stringPool, trimmedOffsetToId);
      this.protoToShard.put(typePoolRef, shard);

      if (typePool.hasDebugInfo()) {
        for (TypePool.DebugInfo.Mismatch m : typePool.getDebugInfo().getMismatchList()) {
          for (TypePointer pointer : m.getInvolvedColorList()) {
            this.registry.addMismatchLocation(shard.getId(pointer), m.getSourceRef());
          }
        }
      }

      return shard;
    }

    public ColorPool build() {
      checkState(this.idToProto.isEmpty(), "build has already been called");

      for (ShardView shard : this.protoToShard.values()) {
        for (int i = 0; i < shard.typePool.getTypeCount(); i++) {
          ColorId id = shard.trimmedOffsetToId.get(i);
          this.idToProto.put(id, shard, shard.typePool.getType(i));
        }
      }

      for (ColorId id : StandardColors.AXIOMATIC_COLORS.keySet()) {
        checkWellFormed(
            !this.idToProto.containsRow(id), "Found serialized definiton for axiomatic color", id);
      }

      for (ColorId id : this.idToProto.rowKeySet()) {
        this.lookupOrReconcileColor(id);
      }

      for (ColorId colorId : ColorRegistry.REQUIRED_IDS) {
        this.registry.setNativeColor(
            this.idToColor.computeIfAbsent(
                colorId, (unused) -> Color.singleBuilder().setId(colorId).build()));
      }

      for (ShardView shard : this.protoToShard.values()) {
        for (SubtypingEdge edge : shard.typePool.getDisambiguationEdgesList()) {
          this.registry.addDisambiguationEdge(
              this.idToColor.get(shard.getId(validatePointer(edge.getSubtype(), shard))), //
              this.idToColor.get(shard.getId(validatePointer(edge.getSupertype(), shard))));
        }
      }

      ColorPool colorPool = new ColorPool(this);
      for (ShardView shard : this.protoToShard.values()) {
        shard.colorPool = colorPool;
      }
      return colorPool;
    }

    private Color lookupOrReconcileColor(ColorId id) {
      this.reconcilationDebugStack.addLast(id);
      try {
        Color existing = this.idToColor.putIfAbsent(id, PENDING_COLOR);
        if (existing != null) {
          if (identical(existing, PENDING_COLOR)) {
            throw new MalformedTypedAstException(
                "Cyclic Color structure detected: "
                    + this.reconcilationDebugStack.stream()
                        .map(this.idToProto::row)
                        .map(ImmutableMap::copyOf)
                        .collect(toImmutableList()));
          }
          return existing;
        }

        Map<ShardView, TypeProto> viewToProto = this.idToProto.row(id);
        TypeProto sample = Iterables.getFirst(viewToProto.values(), null);
        checkNotNull(sample, id);

        final Color result;
        switch (sample.getKindCase()) {
          case OBJECT:
            result = this.reconcileObjectProtos(id, viewToProto);
            break;

          case UNION:
            result = this.reconcileUnionProtos(id, viewToProto);
            break;

          default:
            throw new AssertionError(sample);
        }

        checkState(result != null, id);
        this.idToColor.put(id, result);
        return result;
      } finally {
        this.reconcilationDebugStack.removeLast();
      }
    }

    private Color reconcileObjectProtos(ColorId id, Map<ShardView, TypeProto> viewToProto) {
      TreeSet<String> debugTypenames = new TreeSet<>();
      ImmutableSet.Builder<Color> instanceColors = ImmutableSet.builder();
      ImmutableSet.Builder<Color> prototypes = ImmutableSet.builder();
      ImmutableSet.Builder<String> ownProperties = ImmutableSet.builder();
      Tri isClosureAssert = Tri.UNKNOWN;
      boolean isConstructor = false;
      boolean isInvalidating = false;
      boolean propertiesKeepOriginalName = false;

      for (Map.Entry<ShardView, TypeProto> entry : viewToProto.entrySet()) {
        ShardView shard = entry.getKey();
        TypeProto proto = entry.getValue();

        checkState(proto.hasObject());
        ObjectTypeProto objProto = proto.getObject();

        if (objProto.hasDebugInfo()) {
          debugTypenames.addAll(objProto.getDebugInfo().getTypenameList());
        }
        for (TypePointer p : objProto.getInstanceTypeList()) {
          instanceColors.add(this.lookupOrReconcileColor(shard.getId(p)));
        }

        boolean isClosureAssertBool = objProto.getClosureAssert();
        checkWellFormed(
            isClosureAssert.toBoolean(isClosureAssertBool) == isClosureAssertBool,
            "Inconsistent values for closure_assert",
            objProto);
        isClosureAssert = Tri.forBoolean(isClosureAssertBool);

        isConstructor |= objProto.getMarkedConstructor();
        isInvalidating |= objProto.getIsInvalidating();
        propertiesKeepOriginalName |= objProto.getPropertiesKeepOriginalName();
        for (TypePointer p : objProto.getPrototypeList()) {
          prototypes.add(this.lookupOrReconcileColor(shard.getId(p)));
        }
        for (int i = 0; i < objProto.getOwnPropertyCount(); i++) {
          ownProperties.add(shard.stringPool.get(objProto.getOwnProperty(i)));
        }
      }

      DebugInfo debugInfo = DebugInfo.EMPTY;
      if (!debugTypenames.isEmpty()) {
        debugInfo =
            DebugInfo.builder().setCompositeTypename(String.join("/", debugTypenames)).build();
      }

      return Color.singleBuilder()
          .setId(id)
          .setDebugInfo(debugInfo)
          .setInstanceColors(instanceColors.build())
          .setPrototypes(prototypes.build())
          .setOwnProperties(ownProperties.build())
          .setClosureAssert(isClosureAssert.toBoolean(false))
          .setConstructor(isConstructor)
          .setInvalidating(isInvalidating)
          .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
          .build();
    }

    private Color reconcileUnionProtos(ColorId id, Map<ShardView, TypeProto> viewToProto) {
      LinkedHashSet<Color> union = new LinkedHashSet<>();
      viewToProto.forEach(
          (shard, proto) -> {
            checkState(proto.hasUnion(), proto);
            for (TypePointer memberPointer : proto.getUnion().getUnionMemberList()) {
              ColorId memberId = shard.getId(memberPointer);
              Color member = this.lookupOrReconcileColor(memberId);
              checkWellFormed(!member.isUnion(), "Reconciling union with non-union", proto);
              union.add(member);
            }
          });

      Color result = Color.createUnion(union);
      checkState(id.equals(result.getId()), "%s == %s", id, result);
      return result;
    }
  }

  private static ImmutableList<ColorId> createTrimmedOffsetToId(TypePool typePool) {
    ColorId[] ids = new ColorId[typePool.getTypeCount()];

    for (int i = 0; i < ids.length; i++) {
      TypeProto proto = typePool.getType(i);
      switch (proto.getKindCase()) {
        case OBJECT:
          ids[i] = ColorId.fromBytes(proto.getObject().getUuid());
          break;
        case UNION:
          // Defer generating union IDs until we have the IDs of all the element types.
          break;
        default:
          throw new MalformedTypedAstException(proto);
      }
    }

    for (int i = 0; i < ids.length; i++) {
      TypeProto proto = typePool.getType(i);
      switch (proto.getKindCase()) {
        case OBJECT:
          break;
        case UNION:
          {
            checkWellFormed(
                proto.getUnion().getUnionMemberCount() > 1, "Union has too few members", proto);
            LinkedHashSet<ColorId> members = new LinkedHashSet<>();
            for (TypePointer memberPointer : proto.getUnion().getUnionMemberList()) {
              int offset = memberPointer.getPoolOffset();
              ColorId memberId =
                  isAxiomatic(offset)
                      ? OFFSET_TO_AXIOMATIC_COLOR.get(offset).getId()
                      : ids[trimOffset(offset)];
              checkWellFormed(memberId != null, "Union member not found", proto);
              members.add(memberId);
            }
            ids[i] = ColorId.union(members);
          }
          break;
        default:
          throw new AssertionError(proto);
      }
    }

    LinkedHashSet<ColorId> seenIds = new LinkedHashSet<>();
    for (int i = 0; i < ids.length; i++) {
      TypeProto proto = typePool.getType(i);
      checkWellFormed(seenIds.add(ids[i]), "Duplicate ID in single shard", proto);
    }

    return ImmutableList.copyOf(ids);
  }

  private static TypePointer validatePointer(TypePointer p, ShardView shard) {
    int offset = p.getPoolOffset();
    checkWellFormed(
        0 <= offset && offset < untrimOffset(shard.trimmedOffsetToId.size()),
        "Pointer offset outside of shard",
        p);
    return p;
  }

  private static final Color PENDING_COLOR =
      Color.singleBuilder().setId(ColorId.fromUnsigned(0xDEADBEEF)).build();
}
