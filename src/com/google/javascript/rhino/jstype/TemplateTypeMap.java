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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.jstype.JSType.EqCache;

import java.io.Serializable;

/**
 * Manages a mapping from TemplateType to its resolved JSType. Provides utility
 * methods for cloning/extending the map.
 *
 * @author izaakr@google.com (Izaak Rubin)
 */
public class TemplateTypeMap implements Serializable {
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
  private final ImmutableList<JSType> resolvedTemplateValues;
  private boolean inRecursiveEquivalenceCheck = false;
  final JSTypeRegistry registry;

  TemplateTypeMap(JSTypeRegistry registry,
                  ImmutableList<TemplateType> templateKeys,
                  ImmutableList<JSType> templateValues) {
    Preconditions.checkNotNull(templateKeys);
    Preconditions.checkNotNull(templateValues);

    this.registry = registry;
    this.templateKeys = templateKeys;

    int nKeys = templateKeys.size();
    this.templateValues = templateValues.size() > nKeys ?
        templateValues.subList(0, nKeys) : templateValues;

    // Iteratively resolve any JSType values that refer to the TemplateType keys
    // of this TemplateTypeMap.
    TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(
        registry, this);
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (JSType templateValue : this.templateValues) {
      builder.add(templateValue.visit(replacer));
    }
    this.resolvedTemplateValues = builder.build();
  }

  /**
   * Returns true if the map is empty; false otherwise.
   */
  public boolean isEmpty() {
    return templateKeys.isEmpty();
  }

  /**
   * Returns a list of all template keys.
   */
  public ImmutableList<TemplateType> getTemplateKeys() {
    return templateKeys;
  }

  /**
   * Returns true if this map contains the specified template key, false
   * otherwise.
   */
  public boolean hasTemplateKey(TemplateType templateKey) {
    // Note: match by identity, not equality
    for (TemplateType entry : templateKeys) {
      if (entry == templateKey) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the number of template keys in this map that do not have a
   * corresponding JSType value.
   */
  int numUnfilledTemplateKeys() {
    return templateKeys.size() - templateValues.size();
  }

  /**
   * Returns a list of template keys in this map that do not have corresponding
   * JSType values.
   */
  ImmutableList<TemplateType> getUnfilledTemplateKeys() {
    return templateKeys.subList(templateValues.size(), templateKeys.size());
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
    return (index == -1) ? registry.getNativeType(JSTypeNative.UNKNOWN_TYPE) :
         templateValues.get(index);
  }

  public TemplateType getTemplateTypeKeyByName(String keyName) {
    for (TemplateType key : templateKeys) {
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
      if (templateKeys.get(i) == key) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns UNKNOWN_TYPE.
   */
  public JSType getResolvedTemplateType(TemplateType key) {
    TemplateTypeMap resolvedMap = this.addUnknownValues();
    int index = resolvedMap.getTemplateTypeIndex(key);
    return (index == -1) ? registry.getNativeType(JSTypeNative.UNKNOWN_TYPE) :
         resolvedMap.resolvedTemplateValues.get(index);
  }

  /**
   * An enum tracking the three different equivalence match states for a
   * template key-value pair.
   */
  private enum EquivalenceMatch {
    NO_KEY_MATCH, VALUE_MISMATCH, VALUE_MATCH
  }

  /**
   * Determines if this map and the specified map have equivalent template
   * types.
   */
  public boolean checkEquivalenceHelper(
      TemplateTypeMap that, EquivalenceMethod eqMethod) {
    return checkEquivalenceHelper(that, eqMethod, EqCache.create());
  }

  public boolean checkEquivalenceHelper(TemplateTypeMap that,
      EquivalenceMethod eqMethod, EqCache eqCache) {
    boolean result = false;
    if (!this.inRecursiveEquivalenceCheck &&
        !that.inRecursiveEquivalenceCheck) {
      this.inRecursiveEquivalenceCheck = true;
      that.inRecursiveEquivalenceCheck = true;

      result = checkEquivalenceHelper(eqMethod, this, that, eqCache)
          && checkEquivalenceHelper(eqMethod, that, this, eqCache);

      this.inRecursiveEquivalenceCheck = false;
      that.inRecursiveEquivalenceCheck = false;
    }
    return result;
  }

  private static boolean checkEquivalenceHelper(EquivalenceMethod eqMethod,
    TemplateTypeMap thisMap, TemplateTypeMap thatMap, EqCache eqCache) {
    ImmutableList<TemplateType> thisKeys = thisMap.getTemplateKeys();
    ImmutableList<TemplateType> thatKeys = thatMap.getTemplateKeys();

    for (int i = 0; i < thisKeys.size(); i++) {
      TemplateType thisKey = thisKeys.get(i);
      JSType thisType = thisMap.getResolvedTemplateType(thisKey);
      EquivalenceMatch thisMatch = EquivalenceMatch.NO_KEY_MATCH;

      for (int j = 0; j < thatKeys.size(); j++) {
        TemplateType thatKey = thatKeys.get(j);
        JSType thatType = thatMap.getResolvedTemplateType(thatKey);

        // Cross-compare every key-value pair in this TemplateTypeMap with
        // those in that TemplateTypeMap. Update the Equivalence match for both
        // key-value pairs involved.
        if (thisKey == thatKey) {
          EquivalenceMatch newMatchType = EquivalenceMatch.VALUE_MISMATCH;
          if (thisType.checkEquivalenceHelper(thatType, eqMethod, eqCache)) {
            newMatchType = EquivalenceMatch.VALUE_MATCH;
          }

          if (thisMatch != EquivalenceMatch.VALUE_MATCH) {
            thisMatch = newMatchType;
          }
        }
      }

      if (failedEquivalenceCheck(thisMatch, eqMethod)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines if the specified EquivalenceMatch is considered a failing
   * condition for an equivalence check, given the EquivalenceMethod used for
   * the check.
   */
  private static boolean failedEquivalenceCheck(
      EquivalenceMatch eqMatch, EquivalenceMethod eqMethod) {
    return eqMatch == EquivalenceMatch.VALUE_MISMATCH ||
        (eqMatch == EquivalenceMatch.NO_KEY_MATCH &&
         eqMethod != EquivalenceMethod.INVARIANT);
  }

  /**
   * Extends this TemplateTypeMap with the contents of the specified map.
   * UNKNOWN_TYPE will be used as the value for any missing values in the
   * specified map.
   */
  TemplateTypeMap extend(TemplateTypeMap thatMap) {
    thatMap = thatMap.addUnknownValues();
    return registry.createTemplateTypeMap(
        concatImmutableLists(thatMap.templateKeys, templateKeys),
        concatImmutableLists(thatMap.templateValues, templateValues));
  }

  /**
   * Returns a new TemplateTypeMap whose values have been extended with the
   * specified list.
   */
  TemplateTypeMap addValues(ImmutableList<JSType> newValues) {
    // Ignore any new template values that will not align with an existing
    // template key.
    int numUnfilledKeys = numUnfilledTemplateKeys();
    if (numUnfilledKeys < newValues.size()) {
      newValues = newValues.subList(0, numUnfilledKeys);
    }

    return registry.createTemplateTypeMap(
        templateKeys, concatImmutableLists(templateValues, newValues));
  }

  /**
   * Returns a new TemplateTypeMap, where all unfilled values have been filled
   * with UNKNOWN_TYPE.
   */
  private TemplateTypeMap addUnknownValues() {
    int numUnfilledTemplateKeys = numUnfilledTemplateKeys();
    if (numUnfilledTemplateKeys == 0) {
      return this;
    }

    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (int i = 0; i < numUnfilledTemplateKeys; i++) {
      builder.add(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    }
    return addValues(builder.build());
  }

  /**
   * Concatenates two ImmutableList instances. If either input is empty, the
   * other is returned; otherwise, a new ImmutableList instance is created that
   * contains the contents of both arguments.
   */
  private <T> ImmutableList<T> concatImmutableLists(
    ImmutableList<T> first, ImmutableList<T> second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty()) {
      return first;
    }
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    builder.addAll(first);
    builder.addAll(second);
    return builder.build();
  }

  boolean hasAnyTemplateTypesInternal() {
    for (JSType templateValue : addUnknownValues().resolvedTemplateValues) {
      if (templateValue.hasAnyTemplateTypes()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    String s = "";

    int len = Math.max(Math.max(templateKeys.size(), templateValues.size()),
        resolvedTemplateValues.size());
    s += "{ ";
    for (int i = 0; i < len; i++) {
      s += "(";
      s += (i < templateKeys.size()) ? templateKeys.get(i) : "";
      s += ",";
      s += (i < templateValues.size()) ? templateValues.get(i) : "";
      s += ",";
      s += (i < resolvedTemplateValues.size()) ? resolvedTemplateValues.get(i) : "";
      s += ") ";
    }
    s += "}";

    return s;
  }
}
