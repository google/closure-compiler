/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Epoch-scoped structural interner for closed, parameter-free built-in {@link TemplatizedType}s and
 * insertion-order-preserving {@link UnionType}s.
 *
 * <p>Interns compound types built recursively from the concrete {@link ValueType} scalar singletons
 * and resolved non-proxy generic base types (such as {@code Array}, {@code ReadonlyArray}, {@code
 * Promise}, {@code IThenable}, {@code IIterableResult}, {@code ReadonlyMap}, and {@code Map}),
 * excluding expressions containing {@code @template} type variables or unresolved nominal
 * references ({@link NamedType}s). Union interning keys preserve {@link UnionType#getAlternates()}
 * insertion order.
 *
 * <p>Owned 1:1 by {@link JSTypeRegistry}; not thread-safe.
 */
final class BuiltinTypeInterner {

  private static final @Nullable JSType[] EMPTY_JSTYPE_ARRAY = new JSType[0];

  /**
   * The 7 concrete {@link ValueType} scalar singletons seeded into {@link #internedTypes}
   * (excluding {@code ?}, {@code ??}, {@code *}, and {@code None}).
   */
  private static final ImmutableList<JSTypeNative> SEEDED_SCALAR_NATIVES =
      ImmutableList.of(
          JSTypeNative.BOOLEAN_TYPE,
          JSTypeNative.NUMBER_TYPE,
          JSTypeNative.BIGINT_TYPE,
          JSTypeNative.STRING_TYPE,
          JSTypeNative.VOID_TYPE,
          JSTypeNative.NULL_TYPE,
          JSTypeNative.SYMBOL_TYPE);

  private final JSTypeRegistry registry;
  private final JSTypeResolver resolver;

  private int cacheEpoch = -1;

  // Point lookups only; set and maps are never iterated, so iteration order cannot affect
  // determinism.
  @SuppressWarnings("DeterministicDatastructure")
  private final Set<JSType> internedTypes = Collections.newSetFromMap(new IdentityHashMap<>(256));

  // Point lookups only; map is never iterated, so iteration order cannot affect determinism.
  @SuppressWarnings("DeterministicDatastructure")
  private final HashMap<IdentityKey, TemplatizedType> internedTemplatized =
      HashMap.newHashMap(256);

  // Point lookups only; map is never iterated, so iteration order cannot affect determinism.
  @SuppressWarnings("DeterministicDatastructure")
  private final HashMap<IdentityKey, JSType> internedUnions = HashMap.newHashMap(256);

  private @Nullable JSType[] scratchTypes = new JSType[16];
  private final IdentityKey scratchLookupKey = new IdentityKey();

  BuiltinTypeInterner(JSTypeRegistry registry) {
    this.registry = registry;
    this.resolver = registry.getResolver();
    ensureCurrentEpoch();
  }

  void ensureCurrentEpoch() {
    int epoch = this.resolver.getDefinitionEpoch();
    if (this.cacheEpoch != epoch) {
      resetAndSeed(epoch);
    }
  }

  private void resetAndSeed(int epoch) {
    this.internedTypes.clear();
    this.internedTemplatized.clear();
    this.internedUnions.clear();

    // 1. Seed strictly the 7 concrete ValueType scalar singletons.
    for (int i = 0; i < SEEDED_SCALAR_NATIVES.size(); i++) {
      this.internedTypes.add(this.registry.getNativeType(SEEDED_SCALAR_NATIVES.get(i)));
    }

    // 2. In CLOSED epochs, seed every scalar-backed native UnionType singleton under its native
    // alternate order. In OPEN epochs, newly constructed UnionTypes remain unresolved and queued on
    // JSTypeResolver until Closer.close(), so pre-resolved native UnionType singletons are not
    // returned during OPEN epochs.
    if (this.resolver.isClosed()) {
      for (JSTypeNative nativeId : JSTypeNative.values()) {
        JSType nativeType = this.registry.getNativeType(nativeId);
        if (!(nativeType instanceof UnionType nativeUnion)) {
          continue;
        }
        ImmutableList<JSType> alternates = nativeUnion.getAlternates();
        int altCount = alternates.size();
        if (altCount < 2) {
          continue;
        }
        ensureScratchCapacity(altCount);
        boolean allSeededScalars = true;
        for (int i = 0; i < altCount; i++) {
          JSType alt = alternates.get(i);
          if (alt instanceof UnionType || !this.internedTypes.contains(alt)) {
            allSeededScalars = false;
            Arrays.fill(this.scratchTypes, 0, i, null);
            break;
          }
          this.scratchTypes[i] = alt;
        }
        if (allSeededScalars) {
          @Nullable JSType[] exactAlts = Arrays.copyOf(this.scratchTypes, altCount);
          Arrays.fill(this.scratchTypes, 0, altCount, null);
          this.internedUnions.putIfAbsent(new IdentityKey(/* baseType= */ null, exactAlts), nativeUnion);
          this.internedTypes.add(nativeUnion);
        }
      }
    }

    this.cacheEpoch = epoch;
  }

  private void ensureScratchCapacity(int required) {
    if (required > this.scratchTypes.length) {
      int newCap = this.scratchTypes.length * 2;
      while (newCap < required) {
        newCap *= 2;
      }
      this.scratchTypes = Arrays.copyOf(this.scratchTypes, newCap);
    }
  }

  private boolean isUnknownOrCheckedUnknown(JSType type) {
    return identical(type, this.registry.getNativeType(JSTypeNative.UNKNOWN_TYPE))
        || identical(type, this.registry.getNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE));
  }

  @Nullable TemplatizedType tryInternTemplatized(
      ObjectType baseType, List<JSType> templatizedTypes) {
    if (!baseType.isResolved()
        || baseType instanceof ProxyObjectType
        || baseType.isUnknownType()
        || baseType.isEmptyType()) {
      return null;
    }
    int count = templatizedTypes.size();
    ensureScratchCapacity(count);
    boolean allStrictlyInternable = count > 0 && count == baseType.getTemplateParamCount();
    for (int i = 0; i < count; i++) {
      JSType arg = checkNotNull(templatizedTypes.get(i));
      if (this.internedTypes.contains(arg)) {
        this.scratchTypes[i] = arg;
        continue;
      }
      if (isUnknownOrCheckedUnknown(arg)) {
        this.scratchTypes[i] = arg;
        allStrictlyInternable = false;
        continue;
      }
      Arrays.fill(this.scratchTypes, 0, i, null);
      return null;
    }
    this.scratchLookupKey.setLookup(baseType, this.scratchTypes, count);
    TemplatizedType cached = this.internedTemplatized.get(this.scratchLookupKey);
    this.scratchLookupKey.clearLookup();
    if (cached != null) {
      Arrays.fill(this.scratchTypes, 0, count, null);
      return cached;
    }
    @Nullable JSType[] exactArgs =
        count == 0 ? EMPTY_JSTYPE_ARRAY : Arrays.copyOf(this.scratchTypes, count);
    Arrays.fill(this.scratchTypes, 0, count, null);
    ImmutableList<JSType> argList =
        templatizedTypes instanceof ImmutableList<JSType> immutable
            ? immutable
            : ImmutableList.copyOf(templatizedTypes);
    TemplatizedType created = new TemplatizedType(this.registry, baseType, argList);
    if (this.resolver.isOpen() || created.isResolved()) {
      this.internedTemplatized.put(new IdentityKey(baseType, exactArgs), created);
      if (allStrictlyInternable && hasOnlyInternedTemplateBindings(baseType, created, count)) {
        this.internedTypes.add(created);
      }
    }
    return created;
  }

  /**
   * Checks in O(prefilled superclass bindings) that every resolved template binding on {@code
   * created} is in {@link #internedTypes}, avoiding {@link JSType#hasAnyTemplateTypes()} (which is
   * unmemoized on unresolved types during {@code OPEN} epochs and would re-walk shared DAGs).
   */
  private boolean hasOnlyInternedTemplateBindings(
      ObjectType baseType, TemplatizedType created, int argCount) {
    TemplateTypeMap baseMap = baseType.getTemplateTypeMap();
    ImmutableList<TemplateType> keys = baseMap.getTemplateKeys();
    int prefilledCount = baseMap.getTemplateValues().size();
    if (keys.size() - prefilledCount != argCount) {
      return false;
    }
    if (prefilledCount == 0) {
      return true;
    }
    TemplateTypeMap createdMap = created.getTemplateTypeMap();
    for (int i = 0; i < prefilledCount; i++) {
      JSType resolved = createdMap.getResolvedTemplateType(keys.get(i));
      if (!this.internedTypes.contains(resolved)
          && !(resolved.isResolved() && !resolved.hasAnyTemplateTypes())) {
        return false;
      }
    }
    return true;
  }

  @Nullable JSType tryInternUnion(List<? extends JSType> variants) {
    int count = 0;
    JSType noType = this.registry.getNativeType(JSTypeNative.NO_TYPE);
    int variantCount = variants.size();
    for (int i = 0; i < variantCount; i++) {
      JSType variant = checkNotNull(variants.get(i));
      if (identical(variant, noType)) {
        continue;
      }
      if (variant instanceof UnionType union) {
        if (!this.resolver.isOpen() && !union.isResolved()) {
          Arrays.fill(this.scratchTypes, 0, count, null);
          return null;
        }
        ImmutableList<JSType> alternates = union.getAlternates();
        int altCount = alternates.size();
        ensureScratchCapacity(count + altCount);
        for (int j = 0; j < altCount; j++) {
          JSType alt = alternates.get(j);
          if (!this.internedTypes.contains(alt)) {
            Arrays.fill(this.scratchTypes, 0, count, null);
            return null;
          }
          this.scratchTypes[count++] = alt;
        }
        continue;
      }
      if (!this.internedTypes.contains(variant)) {
        Arrays.fill(this.scratchTypes, 0, count, null);
        return null;
      }
      ensureScratchCapacity(count + 1);
      this.scratchTypes[count++] = variant;
    }
    return lookupOrBuildUnionFromScratch(count);
  }

  private JSType lookupOrBuildUnionFromScratch(int count) {
    if (count == 0) {
      return this.registry.getNativeType(JSTypeNative.NO_TYPE);
    }
    if (count == 1 || (count == 2 && identical(this.scratchTypes[0], this.scratchTypes[1]))) {
      JSType single = checkNotNull(this.scratchTypes[0]);
      Arrays.fill(this.scratchTypes, 0, count, null);
      return single;
    }
    this.scratchLookupKey.setLookup(/* baseType= */ null, this.scratchTypes, count);
    JSType cached = this.internedUnions.get(this.scratchLookupKey);
    this.scratchLookupKey.clearLookup();
    if (cached != null) {
      Arrays.fill(this.scratchTypes, 0, count, null);
      return cached;
    }
    // Copy and clear scratchTypes before UnionType.Builder, as Case 7a may re-enter
    // createTemplatizedType.
    @Nullable JSType[] orderedInputs = Arrays.copyOf(this.scratchTypes, count);
    Arrays.fill(this.scratchTypes, 0, count, null);
    UnionType.Builder builder = UnionType.builder(this.registry);
    for (int i = 0; i < count; i++) {
      builder.addAlternate(checkNotNull(orderedInputs[i]));
    }
    JSType built = builder.build();
    return cacheAndCanonicalizeUnion(built, orderedInputs);
  }

  private JSType cacheAndCanonicalizeUnion(JSType built, @Nullable JSType[] orderedInputs) {
    if (!this.resolver.isOpen() && !built.isResolved()) {
      return built;
    }
    if (built instanceof UnionType builtUnion) {
      ImmutableList<JSType> builtAlts = builtUnion.getAlternates();
      int builtCount = builtAlts.size();
      ensureScratchCapacity(builtCount);
      boolean allBuiltAltsInterned = builtCount >= 2;
      for (int i = 0; i < builtCount; i++) {
        JSType alt = builtAlts.get(i);
        if (!this.internedTypes.contains(alt)) {
          allBuiltAltsInterned = false;
          Arrays.fill(this.scratchTypes, 0, i, null);
          break;
        }
        this.scratchTypes[i] = alt;
      }
      if (allBuiltAltsInterned) {
        this.scratchLookupKey.setLookup(/* baseType= */ null, this.scratchTypes, builtCount);
        JSType existingCanonical = this.internedUnions.get(this.scratchLookupKey);
        this.scratchLookupKey.clearLookup();
        if (existingCanonical != null) {
          built = existingCanonical;
        } else {
          this.internedTypes.add(builtUnion);
          if (!sameIdentitySlice(this.scratchTypes, builtCount, orderedInputs)) {
            this.internedUnions.put(
                new IdentityKey(/* baseType= */ null, Arrays.copyOf(this.scratchTypes, builtCount)),
                builtUnion);
          }
        }
        Arrays.fill(this.scratchTypes, 0, builtCount, null);
      }
    }
    this.internedUnions.put(new IdentityKey(/* baseType= */ null, orderedInputs), built);
    return built;
  }

  private static boolean sameIdentitySlice(
      @Nullable JSType[] slice, int sliceLen, @Nullable JSType[] exact) {
    if (sliceLen != exact.length) {
      return false;
    }
    for (int i = 0; i < sliceLen; i++) {
      if (!identical(slice[i], exact[i])) {
        return false;
      }
    }
    return true;
  }

  private static final class IdentityKey {
    @Nullable ObjectType baseType;
    @Nullable JSType[] types;
    int length;
    int hash;

    IdentityKey() {
      this.baseType = null;
      this.types = EMPTY_JSTYPE_ARRAY;
      this.length = 0;
      this.hash = 0;
    }

    IdentityKey(@Nullable ObjectType baseType, @Nullable JSType[] exactTypes) {
      this.baseType = baseType;
      this.types = exactTypes;
      this.length = exactTypes.length;
      this.hash = computeHash(baseType, exactTypes, exactTypes.length);
    }

    void setLookup(@Nullable ObjectType baseType, @Nullable JSType[] types, int len) {
      this.baseType = baseType;
      this.types = types;
      this.length = len;
      this.hash = computeHash(baseType, types, len);
    }

    void clearLookup() {
      this.baseType = null;
      this.types = EMPTY_JSTYPE_ARRAY;
      this.length = 0;
    }

    private static int computeHash(
        @Nullable ObjectType baseType, @Nullable JSType[] arr, int len) {
      int h = System.identityHashCode(baseType) * 31 + len;
      for (int i = 0; i < len; i++) {
        h = h * 31 + System.identityHashCode(arr[i]);
      }
      return h;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (identical(this, obj)) {
        return true;
      }
      if (!(obj instanceof IdentityKey other)) {
        return false;
      }
      if (this.hash != other.hash
          || !identical(this.baseType, other.baseType)
          || this.length != other.length) {
        return false;
      }
      for (int i = 0; i < this.length; i++) {
        if (!identical(this.types[i], other.types[i])) {
          return false;
        }
      }
      return true;
    }
  }
}
