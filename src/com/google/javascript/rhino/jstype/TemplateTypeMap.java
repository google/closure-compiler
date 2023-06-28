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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Manages a mapping from TemplateType to its resolved JSType. Provides utility methods for
 * cloning/extending the map.
 */
public final class TemplateTypeMap {

  // The TemplateType keys of the map.
  private final ImmutableList<TemplateType> templateKeys;
  // The JSType values, which are index-aligned with their corresponding keys.
  // These values are left as specified in the TemplateTypeMap constructor; they
  // may refer to TemplateTypes that are keys in this TemplateTypeMap, requiring
  // iterative type resolution to find their true, resolved type.
  private final ImmutableList<JSType> templateValues;
  // The JSType values, which are index-aligned with their corresponding keys.
  // These values have been iteratively type-resolved using this TemplateTypeMap
  // instance. These fully-resolved values are necessary for determining the
  // equivalence of two TemplateTypeMap instances.
  private final JSType[] resolvedTemplateValues;
  private final JSTypeRegistry registry;

  static final TemplateTypeMap createEmpty(JSTypeRegistry registry) {
    // This method should only be called during registry initialization.
    checkArgument(registry.getEmptyTemplateTypeMap() == null);
    return new TemplateTypeMap(registry, ImmutableList.of(), ImmutableList.of());
  }

  private TemplateTypeMap(
      JSTypeRegistry registry,
      ImmutableList<TemplateType> templateKeys,
      ImmutableList<JSType> templateValues) {
    checkNotNull(templateKeys);
    checkNotNull(templateValues);
    checkArgument(templateValues.size() <= templateKeys.size());

    this.registry = registry;
    this.templateKeys = templateKeys;
    this.templateValues = templateValues;

    // Iteratively resolve any JSType values that refer to the TemplateType keys
    // of this TemplateTypeMap.
    TemplateTypeReplacer replacer = TemplateTypeReplacer.forTotalReplacement(registry, this);

    int nValues = this.templateValues.size();
    int nKeys = this.templateKeys.size();
    JSType[] resolvedValues = new JSType[nKeys];
    for (int i = 0; i < nKeys; i++) {
      if (i < nValues) {
        TemplateType templateKey = this.templateKeys.get(i);
        replacer.setKeyType(templateKey);
        JSType templateValue = this.templateValues.get(i);
        resolvedValues[i] = templateValue.visit(replacer);
      } else {
        resolvedValues[i] = this.templateKeys.get(i).getBound();
      }
    }
    this.resolvedTemplateValues = resolvedValues;
  }

  /**
   * Create a new map in which any unfilled values in this map have been filled with {@code values}.
   *
   * <p>If there are fewer {@code values} than unfilled values, `?` will be used to fill the rest.
   */
  TemplateTypeMap copyFilledWithValues(ImmutableList<JSType> values) {
    int requiredUnknownCount = numUnfilledTemplateKeys() - values.size();
    checkArgument(requiredUnknownCount >= 0, requiredUnknownCount);

    if (numUnfilledTemplateKeys() == 0) {
      return this; // Nothing will change.
    }

    ArrayList<JSType> extendedValues = new ArrayList<>();
    extendedValues.addAll(this.templateValues);
    extendedValues.addAll(values);
    padToSameLength(this.templateKeys, extendedValues);

    return new TemplateTypeMap(
        this.registry, this.templateKeys, ImmutableList.copyOf(extendedValues));
  }

  /**
   * Create a new map in which the keys and values have been extended by {@code extension}.
   *
   * <p>Before extension, any unfilled values in the initial map will be filled with `?`.
   */
  public TemplateTypeMap copyWithExtension(TemplateTypeMap extension) {
    return copyWithExtension(extension.templateKeys, extension.templateValues);
  }

  /**
   * Create a new map in which the keys and values have been extended by {@code keys} and {@code
   * values} respectively.
   *
   * <p>Before extension, any unfilled values in the initial map will be filled with `?`.
   */
  public TemplateTypeMap copyWithExtension(
      ImmutableList<TemplateType> keys, ImmutableList<JSType> values) {
    int extendedUnfilledCount = keys.size() - values.size();
    checkArgument(extendedUnfilledCount >= 0, extendedUnfilledCount);

    if (numUnfilledTemplateKeys() == 0 && keys.isEmpty()) {
      return this; // Nothing will change.
    }

    ImmutableList<TemplateType> extendedKeys =
        ImmutableList.<TemplateType>builder().addAll(this.templateKeys).addAll(keys).build();

    ArrayList<JSType> extendedValues = new ArrayList<>();
    extendedValues.addAll(this.templateValues);
    padToSameLength(this.templateKeys, extendedValues);
    extendedValues.addAll(values);

    return new TemplateTypeMap(this.registry, extendedKeys, ImmutableList.copyOf(extendedValues));
  }

  /**
   * Create a new map in which keys contained in {@code removals} are eliminated.
   *
   * <p>The keys in {@code removals} will only be removed if they are unfilled.
   */
  TemplateTypeMap copyWithoutKeys(Set<TemplateType> removals) {
    ImmutableList.Builder<TemplateType> keys = ImmutableList.builder();
    keys.addAll(templateKeys.subList(0, templateValues.size()));
    for (int i = templateValues.size(); i < templateKeys.size(); i++) {
      TemplateType key = templateKeys.get(i);
      if (!removals.contains(key)) {
        keys.add(key);
      }
    }

    // There are some checks we could do for this before calculating the removal, but it was less
    // error prone to only check in one place.
    if (keys.build().size() == templateKeys.size()) {
      return this; // Nothing will change.
    }

    return new TemplateTypeMap(this.registry, keys.build(), this.templateValues);
  }

  public int size() {
    return this.templateKeys.size();
  }

  /**
   * Returns true if the map is empty; false otherwise.
   */
  public boolean isEmpty() {
    return templateKeys.isEmpty();
  }

  /** Returns a list of all template keys. */
  public ImmutableList<TemplateType> getTemplateKeys() {
    return templateKeys;
  }

  public ImmutableList<JSType> getTemplateValues() {
    return templateValues;
  }

  /**
   * Returns true if this map contains the specified template key, false
   * otherwise.
   */
  public boolean hasTemplateKey(TemplateType templateKey) {
    // Note: match by identity, not equality

    // NOTE: avoid iterators, for-each for performance and GC reasons
    int keyCount = templateKeys.size();
    for (int i = 0; i < keyCount; i++) {
      var entry = templateKeys.get(i);
      if (identical(templateKey, entry)) {
        return true;
      }
    }
    return false;
  }

  // TODO(b/139230800): This method should be deleted. It checks what should be an impossible case.
  int getTemplateKeyCountThisShouldAlwaysBeOneOrZeroButIsnt(TemplateType templateKey) {
    int matches = 0;
    int keyCount = templateKeys.size();

    // NOTE: avoid iterators, for-each for performance and GC reasons
    for (int i = 0; i < keyCount; i++) {
      var entry = templateKeys.get(i);
      if (identical(templateKey, entry)) {
        matches++;
      }
    }
    return matches;
  }

  private int numUnfilledTemplateKeys() {
    return templateKeys.size() - templateValues.size();
  }

  /**
   * Returns true if there is a JSType value associated with the specified
   * template key; false otherwise.
   */
  public boolean hasTemplateType(TemplateType key) {
    return getTemplateTypeIndex(key) != -1;
  }

  JSType getUnresolvedOriginalTemplateType(TemplateType key) {
    int index = getTemplateTypeIndex(key);
    return (index == -1)
        ? registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)
        : templateValues.get(index);
  }

  /**
   * Returns the final template key matching this name in the ordered list of template keys
   *
   * <p>Caution: there may be multiple template keys with the same name. Before using this method,
   * consider whether you really want reference name string equality over TemplateType identity-
   * based equality.
   */
  public @Nullable TemplateType getLastTemplateTypeKeyByName(String keyName) {
    int size = this.templateKeys.size();
    for (int i = size - 1; i >= 0; i--) {
      TemplateType key = this.templateKeys.get(i);
      if (key.getReferenceName().equals(keyName)) {
        return key;
      }
    }
    return null;
  }

  /**
   * Returns the index of the JSType value associated with the specified
   * template key. If no JSType value is associated, returns -1.
   */
  private int getTemplateTypeIndex(TemplateType key) {
    int maxIndex = Math.min(templateKeys.size(), templateValues.size());
    for (int i = maxIndex - 1; i >= 0; i--) {
      if (identical(templateKeys.get(i), key)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns the upper bound for generic, UNKNOWN_TYPE if unspecified.
   */
  public JSType getResolvedTemplateType(TemplateType key) {
    int index = getTemplateTypeIndex(key);
    return (index == -1)
        ? defaultValueType(key)
        : resolvedTemplateValues[index];
  }

  boolean hasAnyTemplateTypesInternal() {
    if (resolvedTemplateValues != null) {
      for (JSType templateValue : resolvedTemplateValues) {
        if (templateValue.hasAnyTemplateTypes()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    String s = "";

    int len = templateKeys.size();
    s += "{ ";
    for (int i = 0; i < len; i++) {
      s += "(";
      s += templateKeys.get(i);
      s += ",";
      s += (i < templateValues.size()) ? templateValues.get(i) : "";
      s += ",";
      s += (resolvedTemplateValues != null && i < resolvedTemplateValues.length)
          ? resolvedTemplateValues[i]
          : "";
      s += ") ";
    }
    s += "}";

    return s;
  }

  private void padToSameLength(ImmutableList<TemplateType> keys, ArrayList<JSType> builder) {
    checkArgument(builder.size() <= keys.size());

    for (int i = builder.size(); i < keys.size(); i++) {
      builder.add(defaultValueType(keys.get(i)));
    }
  }

  /**
   * Returns the default value type for the given key type. Since the bounded generics feature
   * was removed, in practice this always returns `?`.
   */
  JSType defaultValueType(TemplateType type) {
    return type.getBound().isUnknownType()
        ? this.registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)
        : type;
  }
}
