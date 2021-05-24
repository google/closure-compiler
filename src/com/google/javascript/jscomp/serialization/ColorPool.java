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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.StandardColors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
    this.shardViews = ImmutableList.copyOf(builder.indexToShard);
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
      if (isAxiomatic(pointer)) {
        return OFFSET_TO_AXIOMATIC_COLOR.get(pointer.getPoolOffset()).getId();
      } else {
        return this.trimmedOffsetToId.get(trimOffset(pointer));
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
    private final IdentityHashMap<TypePool, Void> seenPools = new IdentityHashMap<>();
    private final ArrayList<ShardView> indexToShard = new ArrayList<>();

    private final LinkedHashMap<ColorId, Color> idToColor = new LinkedHashMap<>();
    private final ColorRegistry.Builder registry = ColorRegistry.builder();

    // Like a 3D table, (ColorId, ShardView, index) => TypeProto
    private final LinkedHashMap<ColorId, ListMultimap<ShardView, TypeProto>> idToProto =
        new LinkedHashMap<>();

    private final ArrayDeque<ColorId> reconcliationDebugStack = new ArrayDeque<>();

    private Builder() {
      this.idToColor.putAll(StandardColors.AXIOMATIC_COLORS);
    }

    public Builder addShardAnd(TypePool typePool, StringPool stringPool) {
      this.addShard(typePool, stringPool);
      return this;
    }

    public ShardView addShard(TypePool typePool, StringPool stringPool) {
      checkState(!this.seenPools.containsKey(typePool), typePool);
      checkState(this.idToProto.isEmpty(), "build has already been called");

      ImmutableList<ColorId> trimmedOffsetToId = createTrimmedOffsetToId(typePool);
      ShardView shard = new ShardView(typePool, stringPool, trimmedOffsetToId);

      this.seenPools.put(typePool, null);
      this.indexToShard.add(shard);
      return shard;
    }

    public ColorPool build() {
      checkState(this.idToProto.isEmpty(), "build has already been called");

      for (ShardView shard : this.indexToShard) {
        for (int i = 0; i < shard.typePool.getTypeCount(); i++) {
          ColorId id = shard.trimmedOffsetToId.get(i);
          // TODO(b/185519307): Serialization shouldn't put duplicate IDs in a TypePool.
          ListMultimap<ShardView, TypeProto> row =
              this.idToProto.computeIfAbsent(id, ColorPool::createListMultimap);
          addProtoDroppingRedundantUnions(row.get(shard), shard.typePool.getType(i));
        }
      }

      for (ColorId id : StandardColors.AXIOMATIC_COLORS.keySet()) {
        checkWellFormed(!this.idToProto.containsKey(id), id);
      }

      for (ColorId id : this.idToProto.keySet()) {
        this.lookupOrReconcileColor(id);
      }

      for (ColorId boxId : StandardColors.PRIMITIVE_BOX_IDS) {
        this.registry.setNativeColor(
            this.idToColor.computeIfAbsent(
                boxId, (unused) -> Color.singleBuilder().setId(boxId).build()));
      }

      for (ShardView shard : this.indexToShard) {
        for (SubtypingEdge edge : shard.typePool.getDisambiguationEdgesList()) {
          this.registry.addDisambiguationEdge(
              this.idToColor.get(shard.getId(validatePointer(edge.getSubtype(), shard))), //
              this.idToColor.get(shard.getId(validatePointer(edge.getSupertype(), shard))));
        }
      }

      ColorPool colorPool = new ColorPool(this);
      for (ShardView shard : this.indexToShard) {
        shard.colorPool = colorPool;
      }
      return colorPool;
    }

    private Color lookupOrReconcileColor(ColorId id) {
      this.reconcliationDebugStack.addLast(id);
      try {
        Color existing = this.idToColor.putIfAbsent(id, PENDING_COLOR);
        if (existing != null) {
          if (identical(existing, PENDING_COLOR)) {
            throw new MalformedTypedAstException(
                "Cyclic Color structure detected: "
                    + this.reconcliationDebugStack.stream()
                        .map(this.idToProto::get)
                        .map(Multimap::asMap)
                        .collect(toImmutableList()));
          }
          return existing;
        }

        ListMultimap<ShardView, TypeProto> viewToProto = this.idToProto.get(id);
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
        this.reconcliationDebugStack.removeLast();
      }
    }

    private Color reconcileObjectProtos(
        ColorId id, ListMultimap<ShardView, TypeProto> viewToProto) {
      DebugInfo sampleDebugInfo = DebugInfo.EMPTY;
      ImmutableSet.Builder<Color> instanceColors = ImmutableSet.builder();
      ImmutableSet.Builder<Color> prototypes = ImmutableSet.builder();
      ImmutableSet.Builder<String> ownProperties = ImmutableSet.builder();
      Tri isClosureAssert = Tri.UNKNOWN;
      boolean isConstructor = false;
      boolean isInvalidating = false;
      boolean propertiesKeepOriginalName = false;

      for (Map.Entry<ShardView, TypeProto> entry : viewToProto.entries()) {
        ShardView shard = entry.getKey();
        TypeProto proto = entry.getValue();

        checkState(proto.hasObject());
        ObjectTypeProto objProto = proto.getObject();

        if (identical(sampleDebugInfo, DebugInfo.EMPTY) && objProto.hasDebugInfo()) {
          ObjectTypeProto.DebugInfo info = objProto.getDebugInfo();
          sampleDebugInfo =
              DebugInfo.builder()
                  .setFilename(info.getFilename())
                  .setClassName(info.getClassName())
                  .build();
        }
        for (TypePointer p : objProto.getInstanceTypeList()) {
          instanceColors.add(this.lookupOrReconcileColor(shard.getId(p)));
        }

        boolean isClosureAssertBool = objProto.getClosureAssert();
        checkWellFormed(
            isClosureAssert.toBoolean(isClosureAssertBool) == isClosureAssertBool, objProto);
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

      return Color.singleBuilder()
          .setId(id)
          .setDebugInfo(sampleDebugInfo)
          .setInstanceColors(instanceColors.build())
          .setPrototypes(prototypes.build())
          .setOwnProperties(ownProperties.build())
          .setClosureAssert(isClosureAssert.toBoolean(false))
          .setConstructor(isConstructor)
          .setInvalidating(isInvalidating)
          .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
          .build();
    }

    private Color reconcileUnionProtos(ColorId id, ListMultimap<ShardView, TypeProto> viewToProto) {
      LinkedHashSet<Color> union = new LinkedHashSet<>();
      viewToProto.forEach(
          (shard, proto) -> {
            checkState(proto.hasUnion(), proto);
            for (TypePointer memberPointer : proto.getUnion().getUnionMemberList()) {
              ColorId memberId = shard.getId(memberPointer);
              Color member = this.lookupOrReconcileColor(memberId);
              checkWellFormed(!member.isUnion(), proto);
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
            checkWellFormed(proto.getUnion().getUnionMemberCount() > 1, proto);
            LinkedHashSet<ColorId> members = new LinkedHashSet<>();
            for (TypePointer memberPointer : proto.getUnion().getUnionMemberList()) {
              int offset = memberPointer.getPoolOffset();
              ColorId memberId =
                  isAxiomatic(offset)
                      ? OFFSET_TO_AXIOMATIC_COLOR.get(offset).getId()
                      : ids[trimOffset(offset)];
              checkWellFormed(memberId != null, proto);
              members.add(memberId);
            }
            ids[i] = ColorId.union(members);
          }
          break;
        default:
          throw new AssertionError(proto);
      }
    }

    return ImmutableList.copyOf(ids);
  }

  private static TypePointer validatePointer(TypePointer p, ShardView shard) {
    int offset = p.getPoolOffset();
    checkWellFormed(0 <= offset && offset < untrimOffset(shard.trimmedOffsetToId.size()), p);
    return p;
  }

  private static <K, V> ListMultimap<K, V> createListMultimap(Object unused) {
    return MultimapBuilder.linkedHashKeys().arrayListValues(1).build();
  }

  /**
   * Don't let unions and object mix under the same ID.
   *
   * <p>TODO(b/185519307): Because TypePool protos are allowed to have multiple entries with the
   * same ID (are not pre-reconciled) it can happen that there are unions who's elements all have
   * the same ID. In turn, this means that the union has that same ID, because the set of member IDs
   * has size = 1. Those unions are filtered out here prevent self-cycles when instantiating Colors.
   *
   * <p>For this to work, it is also assumed that accidental ID collisions never happen.
   * Reconciliation in general depends on this assumption.
   */
  private static void addProtoDroppingRedundantUnions(List<TypeProto> current, TypeProto proto) {
    if (current.isEmpty()) {
      current.add(proto);
      return;
    }

    if (proto.hasUnion()) {
      return;
    }

    if (current.size() == 1 && current.get(0).hasUnion()) {
      current.clear();
    }
    current.add(proto);
  }

  private static final Color PENDING_COLOR =
      Color.singleBuilder().setId(ColorId.fromUnsigned(0xDEADBEEF)).build();
}
