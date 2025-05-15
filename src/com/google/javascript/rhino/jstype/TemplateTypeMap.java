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
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
  // A positive boolean indicates that the corresponding key is the start of a new submap, which
  // corresponds to separate individual ObjectTypes with their own keys.
  // For example, consider:
  //   class Foo<T> {}
  //   class Bar<U, V, X> extends Foo<U | V> {}
  //   class Baz<Y> extends Bar<Y, number, string> {}
  // The TemplateTypeMap for Baz will have:
  //  templateKeys:   [  T, U,      V,      X, Y]
  //  templateValues: [U|V, Y, number, string, ?]
  //  subMapStarts: [  1, 1,      0,      0, 1]
  // (resolvedTemplateValues would be all ? at the start)
  // We would say there are three "submaps" in the Baz TemplateMap:
  //  1. [T -> U], for Foo
  //  2. [U -> Y, V -> number, X -> string] for Bar
  //  3. [Y -> ?] for Baz
  // In theory, we could model this with having multiple arrays of keys and values - a single
  // submap would have pointers to the parent maps for its extended/implemented types, and those
  // could further extend other types.
  // But in practice, it has historically seemed more useful to view these as a single template
  // key/value list pair.
  // A TemplateType key may only be referenced by a template value if the key is either
  //     1. to the "right" of the value, i.e. at an index greater than or equal to the value
  //     2. in the same submap as the value.
  // For example, given
  //    class Parent<T> {} and class Child<U> extends Parent<(whatever is here)> {}
  // the TemplateTypeMap for Child can never look like:
  //   templateKeys: [T, U]
  //   templateValues: [?, T]
  // There's no way for the templateValue of U to depend on T. It's not in scope.
  // Of course, it's fine for T to depend on U, if e.g. Child<U> extends Parent<U> {}
  // Note: this map is only read during 1) construction and 2）this.copy* methods.
  private final BitSet subMapStarts;
  private final JSTypeRegistry registry;

  static final TemplateTypeMap createEmpty(JSTypeRegistry registry) {
    // This method should only be called during registry initialization.
    checkArgument(registry.getEmptyTemplateTypeMap() == null);

    return new TemplateTypeMap(registry, ImmutableList.of(), ImmutableList.of(), new BitSet());
  }

  private TemplateTypeMap(
      JSTypeRegistry registry,
      ImmutableList<TemplateType> templateKeys,
      ImmutableList<JSType> templateValues,
      BitSet subMapStarts) {
    checkNotNull(templateKeys);
    checkNotNull(templateValues);
    checkArgument(templateValues.size() <= templateKeys.size());
    checkArgument(templateKeys.size() <= subMapStarts.size());

    this.registry = registry;
    this.templateKeys = templateKeys;
    this.templateValues = templateValues;
    this.subMapStarts = subMapStarts;

    // Iteratively resolve any JSType values that refer to the TemplateType keys
    // of this TemplateTypeMap.
    TemplateTypeReplacer replacer = TemplateTypeReplacer.forTotalReplacement(registry, this);

    int nValues = this.templateValues.size();
    int nKeys = this.templateKeys.size();
    JSType[] resolvedValues = new JSType[nKeys];
    for (int i = 0; i < nKeys; i++) {
      if (i < nValues) {
        int nextSubMap = nextSubMapStart(i + 1);
        TemplateType templateKey = this.templateKeys.get(i);
        replacer.setKeyType(templateKey, nextSubMap);
        JSType templateValue = this.templateValues.get(i);
        resolvedValues[i] = templateValue.visit(replacer);
      } else {
        resolvedValues[i] = this.templateKeys.get(i).getBound();
      }
    }
    this.resolvedTemplateValues = resolvedValues;
  }

  // Returns the index of the next submap in subMapStarts, starting at index (inclusive);
  // will return subMapStarts.length if there is no next submap & we are in the last one.
  private int nextSubMapStart(int index) {
    while (index < this.templateKeys.size() && !subMapStarts.get(index)) {
      index++;
    }
    return index;
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
        this.registry, this.templateKeys, ImmutableList.copyOf(extendedValues), subMapStarts);
  }

  /**
   * Create a new map in which the keys and values have been extended by {@code extension}.
   *
   * <p>Before extension, any unfilled values in the initial map will be filled with `?`.
   */
  public TemplateTypeMap copyWithExtension(TemplateTypeMap extension) {
    return copyWithExtension(
        extension.templateKeys, extension.templateValues, extension.subMapStarts);
  }

  /**
   * Create a new map in which the keys and values have been extended by {@code keys} and {@code
   * values} respectively.
   *
   * <p>Before extension, any unfilled values in the initial map will be filled with `?`.
   */
  public TemplateTypeMap copyWithExtension(
      ImmutableList<TemplateType> keys, ImmutableList<JSType> values) {
    if (keys.isEmpty()) {
      return copyWithExtension(keys, values, new BitSet());
    }
    BitSet subMapStarts = new BitSet(keys.size());
    subMapStarts.set(0);
    return copyWithExtension(keys, values, subMapStarts);
  }

  /**
   * Create a new map in which the keys and values have been extended by {@code keys} and {@code
   * values} respectively.
   *
   * <p>Before extension, any unfilled values in the initial map will be filled with `?`.
   */
  private TemplateTypeMap copyWithExtension(
      ImmutableList<TemplateType> keys, ImmutableList<JSType> values, BitSet newsubMapStarts) {
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
    BitSet extendedsubMapStarts = new BitSet(keys.size() + this.templateKeys.size());
    extendedsubMapStarts.or(subMapStarts);
    for (int i = 0; i < keys.size(); i++) {
      extendedsubMapStarts.set(i + this.templateKeys.size(), newsubMapStarts.get(i));
    }

    return new TemplateTypeMap(
        this.registry, extendedKeys, ImmutableList.copyOf(extendedValues), extendedsubMapStarts);
  }

  /**
   * Create a new map in which keys contained in {@code removals} are eliminated.
   *
   * <p>The keys in {@code removals} will only be removed if they are unfilled.
   */
  TemplateTypeMap copyWithoutKeys(Set<TemplateType> removals) {
    ImmutableList.Builder<TemplateType> keys = ImmutableList.builder();
    BitSet newsubMapStarts = new BitSet();
    keys.addAll(templateKeys.subList(0, templateValues.size()));
    for (int i = 0; i < templateValues.size(); i++) {
      newsubMapStarts.set(i, subMapStarts.get(i));
    }
    int newKeysSize = 0;
    boolean inNewSubmap = false;
    for (int i = templateValues.size(); i < templateKeys.size(); i++) {
      TemplateType key = templateKeys.get(i);
      inNewSubmap = inNewSubmap || subMapStarts.get(i);
      if (!removals.contains(key)) {
        keys.add(key);
        newsubMapStarts.set(templateValues.size() + newKeysSize++, inNewSubmap);
        inNewSubmap = false;
      }
    }

    // There are some checks we could do for this before calculating the removal, but it was less
    // error prone to only check in one place.
    if (keys.build().size() == templateKeys.size()) {
      return this; // Nothing will change.
    }

    return new TemplateTypeMap(this.registry, keys.build(), this.templateValues, newsubMapStarts);
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
    return hasTemplateKey(templateKey, 0);
  }

  /**
   * Returns true if this map contains the specified template key, false otherwise.
   *
   * <p>If max is non-negative, this search will not consider any keys in the map from [0, max]. The
   * intended use is to exclude keys that are conceptually defined on a type earlier in the
   * supertype chain.
   *
   * <p>For use in {@link TemplateTypeReplacer}.
   */
  boolean hasTemplateKey(TemplateType templateKey, int nextSubMap) {
    // The "nextSubMap" parameter tells this method to only consider template keys in the range
    // [nextSubMap, templateKeys.size()).
    //
    // This is used in the TemplateTypeMap constructor to avoid "leaking" sibling generic types
    // from the same sub map. It is /not/ used in TypeInference when further specializing templates.
    //
    // Consider:
    //   class Foo<T> {
    //     t(): T
    //   }
    //   class Bar<U, V> extends Foo<U>  {
    //     b(): Bar<U|V, string>
    //   }
    // Bar's subMapStarts array is [1, 0, 1], representing:
    //   1. [T -> U] for Foo
    //   2. [U -> ?, V -> ?] for Bar
    //
    // The 'b()' method on Bar returns a partially specialized version of Bar's own default submap.
    // The TypedScopeCreator will initialize a new TemplateTypeMap for Bar<U|V, string>. In the
    // constructor, it will run TemplateTypeReplacer, so that the map becomes:
    //   1. [T -> U]
    //   2. [U -> U|V, V -> string]
    //
    // Finally, TypeInference may call a TemplateTypeReplacer many times on a copy of this map -
    // e.g. for
    //     const b = new Bar<number, symbol>(); -
    // The final post-type-inference map will be:
    //    [T -> (number|symbol)] for Foo
    //    [U -> (number|symbol), V -> string] for Bar.
    //
    // So - why does "nextSubMap" matter? We need to avoid replacing references to U & V too hastily
    // when TypedScopeCreator first creates the TemplateTypeMap. Otherwise, we risk this outcome
    // after TypedScopeCreator: [INTENTIONALLY WRONG EXAMPLE]
    //   1. [T -> U|string]
    //   2. [U -> U|string, V -> string]
    // which would lead to the final post-type-inference map:
    //   1. [T -> (number|string)]
    //   2. [U -> (number|string), V -> string] for Bar.
    // Oops - we lost U -> symbol.
    //
    // How do we avoid this?
    //
    // In the TemplateTypeMap constructor, we call TemplateTypeReplacer on all the keys in this map
    // and call replacer.setKeyType(key, nextSubMap) for each key.
    // To create the return type of b(): Bar<U|V, string>, we would have done:
    // Case 1: TemplateTypeMap: replace T -> U.
    //   replacer.setKeyType(T, 1); // skip everything after Foo
    //    TemplateTypeReplacer calls hasBinding(U, subMapStarts = 1). This returns true: U is
    //    exactly at index 1. So TemplateTypeReplacer will replace T -> U with U's replacement,
    //    so now T -> (U|V). This is what we want.
    // Case 2: TemplateTypeMap: replace U -> U|V.
    //   replacer.setKeyType(U, 3); // skip everything
    //    TemplateTypeReplacer calls hasBinding(V, subMapStarts = 3). This returns false - 3 is
    //    the end of the template array. So TemplateTypeReplacer will leave U -> U|V alone for now.
    // Case 3: TemplateTypeMap: replace V -> string.
    //   replacer.setKeyType(V, 3); // skip everything
    //    This replacement is more trivial - string is just a primtiive type.
    // So now, after evaluating the return type of b, we have:
    //   T -> U|V
    //   U -> U|V
    //   V -> string
    //
    // Later on, during TypeInference, we may call the replacer again - consider e.g.
    //  const b = new Bar<number, symbol>(); This time, we do not provide any subMapStarts - it
    // defaults to -1. The replacer calls hasBinding(U, -1) and hasBinding(V, -1) which return true.
    // So we finally get:
    //    T -> (number|symbol)
    //    U -> (number|symbol)
    //    V -> string
    //
    // What would happen if we always passed "-1" as the nextSubMap? During the initial
    // TemplateTypeReplacer pass, hasBinding(V, -1) returns true, when visiting
    //  U -> (U|V). Then TemplateTypeReplacer would see the binding V -> string, and
    //  replace U -> U|V with U|V -> string. This is wrong!
    // On the other hand, what would happen if we passed nextSubMap during TypeInference, when
    // specializing Bar<number, symbol? That would also be wrong. hasBinding(V, 3) would return
    // false. So the template type replacer would leave U -> U|V as U -> (number|V). Also wrong!

    // NOTE: avoid iterators, for-each for performance and GC reasons
    int keyCount = templateKeys.size();
    int start = max(0, nextSubMap);
    for (int i = start; i < keyCount; i++) {
      var entry = templateKeys.get(i);
      // Note: match by identity, not equality
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
    int maxIndex = min(templateKeys.size(), templateValues.size());
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

  @VisibleForTesting
  public ImmutableList<Integer> getIndicesOfSubmapsForTesting() {
    ImmutableList.Builder<Integer> indices = ImmutableList.builder();
    for (int i = 0; i < subMapStarts.length(); i++) {
      if (subMapStarts.get(i)) {
        indices.add(i);
      }
    }
    return indices.build();
  }
}
