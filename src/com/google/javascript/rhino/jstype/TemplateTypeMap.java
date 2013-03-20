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

import java.io.Serializable;

/**
 * Manages a mapping from TemplateType to its resolved JSType. Provides utility
 * methods for cloning/extending the map.
 *
 * @author izaakr@google.com (Izaak Rubin)
 */
public class TemplateTypeMap implements Serializable {
  private final ImmutableList<TemplateType> templateKeys;
  private final ImmutableList<JSType> templateValues;
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
    return getTemplateTypeInternal(key) != null;
  }

  /**
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns UNKNOWN_TYPE.
   */
  public JSType getTemplateType(TemplateType key) {
    JSType templateType = getTemplateTypeInternal(key);
    return (templateType == null) ?
        registry.getNativeType(JSTypeNative.UNKNOWN_TYPE) : templateType;
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
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns null.
   */
  private JSType getTemplateTypeInternal(TemplateType key) {
    int index = 0;
    for (TemplateType item : templateKeys) {
      // Note: match by identity.
      if (item == key) {
        break;
      }
      index++;
    }
    if (index < 0 || index >= templateValues.size()) {
      return null;
    }
    return templateValues.get(index);
  }

  /**
   * Determines if this map and the specified map have equivalent template
   * types.
   */
  public boolean checkEquivalenceHelper(
      TemplateTypeMap that, EquivalenceMethod eqMethod) {
    int thisNumKeys = templateKeys.size();
    int thatNumKeys = that.getTemplateKeys().size();

    for (int i = 0; i < Math.min(thisNumKeys, thatNumKeys); i++) {
      JSType thisTemplateType = getTemplateType(templateKeys.get(i));
      JSType thatTemplateType = that.getTemplateType(
          that.getTemplateKeys().get(i));
      if (!thisTemplateType.checkEquivalenceHelper(
          thatTemplateType, eqMethod)) {
        return false;
      }
    }

    return thisNumKeys == thatNumKeys ||
        eqMethod == EquivalenceMethod.INVARIANT;
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
    for (JSType templateValue : templateValues) {
      if (templateValue.hasAnyTemplateTypes()) {
        return true;
      }
    }
    return false;
  }
}
