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

import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.jstype.UnionType;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A builder for union types.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class UnionTypeBuilder implements Serializable {
  private static final long serialVersionUID = 1L;

  // If the best we can do is say "this object is one of twenty things",
  // then we should just give up and admit that we have no clue.
  private static final int MAX_UNION_SIZE = 20;

  private final JSTypeRegistry registry;
  private final List<JSType> alternates = Lists.newArrayList();
  private boolean isAllType = false;
  private boolean isNativeUnknownType = false;
  private boolean areAllUnknownsChecked = true;

  // Memoize the result, in case build() is called multiple times.
  private JSType result = null;

  UnionTypeBuilder(JSTypeRegistry registry) {
    this.registry = registry;
  }

  Iterable<JSType> getAlternates() {
    JSType specialCaseType = reduceAlternatesWithoutUnion();
    if (specialCaseType != null) {
      return ImmutableList.of(specialCaseType);
    }
    return alternates;
  }

  /**
   * Adds an alternate to the union type under construction. Returns this
   * for easy chaining.
   */
  UnionTypeBuilder addAlternate(JSType alternate) {
    // build() returns the bottom type by default, so we can
    // just bail out early here.
    if (alternate.isNoType()) {
      return this;
    }

    isAllType = isAllType || alternate.isAllType();

    boolean isAlternateUnknown = alternate instanceof UnknownType;
    isNativeUnknownType = isNativeUnknownType || isAlternateUnknown;
    if (isAlternateUnknown) {
      areAllUnknownsChecked = areAllUnknownsChecked &&
          alternate.isCheckedUnknownType();
    }
    if (!isAllType && !isNativeUnknownType) {
      if (alternate instanceof UnionType) {
        UnionType union = (UnionType) alternate;
        for (JSType unionAlt : union.getAlternates()) {
          addAlternate(unionAlt);
        }
      } else {
        if (alternates.size() > MAX_UNION_SIZE) {
          return this;
        }

        // Look through the alternates we've got so far,
        // and check if any of them are duplicates of
        // one another.
        Iterator<JSType> it = alternates.iterator();
        while (it.hasNext()) {
          JSType current = it.next();
          if (alternate.isUnknownType() ||
              current.isUnknownType()) {
            if (alternate.isEquivalentTo(current)) {
              // Alternate is unnecessary.
              return this;
            }
          } else {
            if (alternate.isSubtype(current)) {
              // Alternate is unnecessary.
              return this;
            } else if (current.isSubtype(alternate)) {
              // Alternate makes current obsolete
              it.remove();
            }
          }
        }
        alternates.add(alternate);
        result = null; // invalidate the memoized result
      }
    } else {
      result = null;
    }
    return this;
  }

  /**
   * Reduce the alternates into a non-union type.
   * If the alternates can't be accurately represented with a non-union
   * type, return null.
   */
  private JSType reduceAlternatesWithoutUnion() {
    if (isAllType) {
      return registry.getNativeType(ALL_TYPE);
    } else if (isNativeUnknownType) {
      if (areAllUnknownsChecked) {
        return registry.getNativeType(CHECKED_UNKNOWN_TYPE);
      } else {
        return registry.getNativeType(UNKNOWN_TYPE);
      }
    } else {
      int size = alternates.size();
      if (size > MAX_UNION_SIZE) {
        return registry.getNativeType(UNKNOWN_TYPE);
      } else if (size > 1) {
        return null;
      } else if (size == 1) {
        return alternates.iterator().next();
      } else {
        return registry.getNativeType(NO_TYPE);
      }
    }
  }

  /**
   * Creates a union.
   * @return A UnionType if it has two or more alternates, the
   *    only alternate if it has one and otherwise {@code NO_TYPE}.
   */
  JSType build() {
    if (result == null) {
      result = reduceAlternatesWithoutUnion();
      if (result == null) {
        result = new UnionType(registry, getAlternateListCopy());
      }
    }
    return result;
  }

  private static final Comparator<JSType> typeSorter =
      new Comparator<JSType>() {
    @Override public int compare(JSType a, JSType b) {
      return b.hashCode() - a.hashCode();
    }
  };

  private Collection<JSType> getAlternateListCopy() {
    // TODO(nicksantos): Until we're at a place where we're no longer
    // using java's built-in equals to test type equivalence, we need
    // hash codes to be the same. So the alternates need to be sorted.
    Collections.sort(alternates, typeSorter);

    return ImmutableList.copyOf(alternates);
  }
}
