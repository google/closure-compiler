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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
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
  private static final int DEFAULT_MAX_UNION_SIZE = 20;

  private final JSTypeRegistry registry;
  private final List<JSType> alternates = Lists.newArrayList();
  private boolean isAllType = false;
  private boolean isNativeUnknownType = false;
  private boolean areAllUnknownsChecked = true;
  private final int maxUnionSize;

  // Every UnionType may have at most one structural function in it.
  //
  // NOTE(nicksantos): I've read some literature that says that type-inferenced
  // languages are fundamentally incompatible with union types. I refuse
  // to believe this. But they do make the type lattice much more complicated.
  //
  // For this reason, when we deal with function types, we actually merge some
  // nodes on the lattice, and treat them as fundamentally equivalent.
  // For example, we treat
  // function(): string | function(): number
  // as equivalent to
  // function(): (string|number)
  // and normalize the first type into the second type.
  //
  // To perform this normalization, we've modified UnionTypeBuilder to disallow
  // multiple structural functions in a union. We always delegate to
  // FunctionType::getLeastSupertype, which either merges the functions into
  // one structural function, or just bails out and uses the top function type.
  private int functionTypePosition = -1;

  // Memoize the result, in case build() is called multiple times.
  private JSType result = null;

  UnionTypeBuilder(JSTypeRegistry registry) {
    this(registry, DEFAULT_MAX_UNION_SIZE);
  }

  UnionTypeBuilder(JSTypeRegistry registry, int maxUnionSize) {
    this.registry = registry;
    this.maxUnionSize = maxUnionSize;
  }

  Collection<JSType> getAlternates() {
    JSType specialCaseType = reduceAlternatesWithoutUnion();
    if (specialCaseType != null) {
      return ImmutableList.of(specialCaseType);
    }
    return Collections.unmodifiableList(alternates);
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
      if (alternate.isUnionType()) {
        UnionType union = alternate.toMaybeUnionType();
        for (JSType unionAlt : union.getAlternates()) {
          addAlternate(unionAlt);
        }
      } else {
        if (alternates.size() > maxUnionSize) {
          return this;
        }

        // Function types are special, because they have their
        // own bizarre sub-lattice. See the comments on
        // FunctionType#supAndInf helper and above at functionTypePosition.
        if (alternate.isFunctionType() && functionTypePosition != -1) {
          // See the comments on functionTypePosition above.
          FunctionType other =
              alternates.get(functionTypePosition).toMaybeFunctionType();
          FunctionType supremum =
              alternate.toMaybeFunctionType().supAndInfHelper(other, true);
          alternates.set(functionTypePosition, supremum);
          result = null;
          return this;
        }

        // Look through the alternates we've got so far,
        // and check if any of them are duplicates of
        // one another.
        int currentIndex = 0;
        Iterator<JSType> it = alternates.iterator();
        while (it.hasNext()) {
          boolean removeCurrent = false;
          JSType current = it.next();

          // Unknown and NoResolved types may just be names that haven't
          // been resolved yet. So keep these in the union, and just use
          // equality checking for simple de-duping.
          if (alternate.isUnknownType() ||
              current.isUnknownType() ||
              alternate.isNoResolvedType() ||
              current.isNoResolvedType() ||
              alternate.hasAnyTemplateTypes() ||
              current.hasAnyTemplateTypes()) {
            if (alternate.isEquivalentTo(current)) {
              // Alternate is unnecessary.
              return this;
            }
          } else {

            // Because "Foo" and "Foo.<?>" are roughly equivalent
            // templatized types, special care is needed when building the
            // union. For example:
            //   Object is consider a subtype of Object.<string>
            // but we want to leave "Object" not "Object.<string>" when
            // building the subtype.
            //

            if (alternate.isTemplatizedType() || current.isTemplatizedType()) {
              // Cases:
              // 1) alternate:Array.<string> and current:Object ==> Object
              // 2) alternate:Array.<string> and current:Array ==> Array
              // 3) alternate:Object.<string> and
              //    current:Array ==> Array|Object.<string>
              // 4) alternate:Object and current:Array.<string> ==> Object
              // 5) alternate:Array and current:Array.<string> ==> Array
              // 6) alternate:Array and
              //    current:Object.<string> ==> Array|Object.<string>
              // 7) alternate:Array.<string> and
              //    current:Array.<number> ==> Array.<?>
              // 8) alternate:Array.<string> and
              //    current:Array.<string> ==> Array.<string>
              // 9) alternate:Array.<string> and
              //    current:Object.<string> ==> Object.<string>|Array.<string>

              if (!current.isTemplatizedType()) {
                if (alternate.isSubtype(current)) {
                  // case 1, 2
                  return this;
                }
                // case 3: leave current, add alternate
              } else if (!alternate.isTemplatizedType()) {
                if (current.isSubtype(alternate)) {
                  // case 4, 5
                  removeCurrent = true;
                }
                // case 6: leave current, add alternate
              } else {
                Preconditions.checkState(current.isTemplatizedType()
                    && alternate.isTemplatizedType());
                TemplatizedType templatizedAlternate = alternate.toMaybeTemplatizedType();
                TemplatizedType templatizedCurrent = current.toMaybeTemplatizedType();

                if (templatizedCurrent.wrapsSameRawType(templatizedAlternate)) {
                  if (alternate.getTemplateTypeMap().checkEquivalenceHelper(
                      current.getTemplateTypeMap(),
                      EquivalenceMethod.IDENTITY)) {
                    // case 8
                    return this;
                  } else {
                    // TODO(johnlenz): should we leave both types?
                    // case 7: add a merged alternate
                    // We currently merge to the templatized types to "unknown"
                    // which is equivalent to the raw type.
                    JSType merged = templatizedCurrent
                        .getReferencedObjTypeInternal();
                    return addAlternate(merged);
                  }
                }
                // case 9: leave current, add alternate
              }
              // Otherwise leave both templatized types.
            } else if (alternate.isSubtype(current)) {
              // Alternate is unnecessary.
              return this;
            } else if (current.isSubtype(alternate)) {
              // Alternate makes current obsolete
              removeCurrent = true;
            }
          }

          if (removeCurrent) {
            it.remove();

            if (currentIndex == functionTypePosition) {
              functionTypePosition = -1;
            } else if (currentIndex < functionTypePosition) {
              functionTypePosition--;
              currentIndex--;
            }
          }
          currentIndex++;
        }

        if (alternate.isFunctionType()) {
          // See the comments on functionTypePosition above.
          Preconditions.checkState(functionTypePosition == -1);
          functionTypePosition = alternates.size();
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
      if (size > maxUnionSize) {
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

  private Collection<JSType> getAlternateListCopy() {
    return ImmutableList.copyOf(alternates);
  }
}
