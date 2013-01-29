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
  private final ImmutableList<String> templateKeys;
  private final ImmutableList<JSType> templateValues;
  final JSTypeRegistry registry;

  TemplateTypeMap(JSTypeRegistry registry,
                  ImmutableList<String> templateKeys,
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
   * Returns a list of all template keys.
   */
  public ImmutableList<String> getTemplateKeys() {
    return templateKeys;
  }

  /**
   * Returns true if this map contains the specified template key, false
   * otherwise.
   */
  public boolean hasTemplateKey(String templateKey) {
    return templateKeys.contains(templateKey);
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
  ImmutableList<String> getUnfilledTemplateKeys() {
    return templateKeys.subList(templateValues.size(), templateKeys.size());
  }

  /**
   * Returns true if there is a JSType value associated with the specified
   * template key; false otherwise.
   */
  public boolean hasTemplateType(String key) {
    return getTemplateTypeInternal(key) != null;
  }

  /**
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns UNKNOWN_TYPE.
   */
  public JSType getTemplateType(String key) {
    JSType templateType = getTemplateTypeInternal(key);
    return (templateType == null) ?
        registry.getNativeType(JSTypeNative.UNKNOWN_TYPE) : templateType;
  }

  /**
   * Returns the JSType value associated with the specified template key. If no
   * JSType value is associated, returns null.
   */
  private JSType getTemplateTypeInternal(String key) {
    int index = templateKeys.indexOf(key);
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
   * Returns a new TemplateTypeMap whose keys have been extended with the
   * specified list.
   */
  TemplateTypeMap extendKeys(ImmutableList<String> newKeys) {
    return registry.createTemplateTypeMap(
        concatImmutableLists(templateKeys, newKeys), templateValues);
  }

  /**
   * Returns a new TemplateTypeMap whose values have been extended with the
   * specified list.
   */
  TemplateTypeMap extendValues(ImmutableList<JSType> newValues) {
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
