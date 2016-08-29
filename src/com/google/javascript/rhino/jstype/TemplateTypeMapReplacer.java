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

import java.util.ArrayDeque;

/**
 * Uses a TemplateTypeMap to replace TemplateTypes with their associated JSType
 * values.
 *
 * <p>Equality checks in this file are done with reference equality on purpose.
 *
 * @author izaakr@google.com (Izaak Rubin)
 */
public class TemplateTypeMapReplacer extends ModificationVisitor {
  private final TemplateTypeMap replacements;
  private ArrayDeque<TemplateType> visitedTypes;
  private TemplateType keyType = null;
  private boolean replaceMissingTypesWithUnknown = false;

  public TemplateTypeMapReplacer(
      JSTypeRegistry registry, TemplateTypeMap replacements) {
    super(registry, false);
    this.replacements = replacements;
    this.visitedTypes = new ArrayDeque<>();
  }

  public TemplateTypeMapReplacer(
      JSTypeRegistry registry,
      TemplateTypeMap replacements,
      boolean replaceMissingTypesWithUnknown) {
    this(registry, replacements);
    this.replaceMissingTypesWithUnknown = replaceMissingTypesWithUnknown;
  }

  void setKeyType(TemplateType keyType) {
    this.keyType = keyType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public JSType caseTemplateType(TemplateType type) {
    if (replacements.hasTemplateKey(type)) {
      if (hasVisitedType(type)) {
        // If we have already encountered this TemplateType during replacement
        // (i.e. there is a reference loop) then return the TemplateType type itself.
        return type;
      } else if (!replacements.hasTemplateType(type)) {
        // If there is no JSType substitution for the TemplateType, return either the
        // UNKNOWN_TYPE or the TemplateType type itself, depending on configuration.
        return replaceMissingTypesWithUnknown
            ? registry.getNativeType(JSTypeNative.UNKNOWN_TYPE)
            : type;
      } else {
        JSType replacement = replacements.getUnresolvedOriginalTemplateType(type);
        if (replacement == keyType || isRecursive(type, replacement)) {
          // Recursive templated type definition (e.g. T resolved to Foo<T>).
          return type;
        }

        visitedTypes.push(type);
        JSType visitedReplacement = replacement.visit(this);
        visitedTypes.pop();

        Preconditions.checkState(
            visitedReplacement != keyType, "Trying to replace key %s with the same value", keyType);
        return visitedReplacement;
      }
    } else {
      return type;
    }
  }

  /**
   * Returns whether the replacement type is a templatized type which contains the current type.
   * e.g. current type T is being replaced with Foo<T>
   */
  private boolean isRecursive(TemplateType currentType, JSType replacementType) {
    TemplatizedType replacementTemplatizedType =
        replacementType.restrictByNotNullOrUndefined().toMaybeTemplatizedType();
    if (replacementTemplatizedType == null) {
      return false;
    }

    Iterable<JSType> replacementTemplateTypes = replacementTemplatizedType.getTemplateTypes();
    for (JSType replacementTemplateType : replacementTemplateTypes) {
      if (replacementTemplateType.isTemplateType()
          && isSameType(currentType, replacementTemplateType.toMaybeTemplateType())) {
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isSameType(TemplateType currentType, TemplateType replacementType) {
    return currentType == replacementType
        || currentType == replacements.getUnresolvedOriginalTemplateType(replacementType);
  }

  /**
   * Checks if the specified type has already been visited during the Visitor's
   * traversal of a JSType.
   */
  @SuppressWarnings("ReferenceEquality")
  private boolean hasVisitedType(TemplateType type) {
    for (TemplateType visitedType : visitedTypes) {
      if (visitedType == type) {
        return true;
      }
    }
    return false;
  }
}
