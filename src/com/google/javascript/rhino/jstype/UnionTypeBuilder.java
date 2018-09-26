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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A builder for union types.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class UnionTypeBuilder implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Generally, if the best we can do is say "this object is one of thirty things", then we should
   * just give up and admit that we have no clue.
   */
  private static final int DEFAULT_MAX_UNION_SIZE = 30;

  /**
   * A special case maximum size for use in the type registry.
   *
   * <p>The registry uses a union type to track all the types that have a given property. In this
   * scenario, there can be <em>many</em> alternates but it's still valuable to differentiate them.
   *
   * <p>This value was semi-reandomly selected based on the Google+ FE project.
   */
  private static final int PROPERTY_CHECKING_MAX_UNION_SIZE = 3000;

  private final JSTypeRegistry registry;
  private final boolean structuralSubtypesAreCollapsed;
  private final int maxUnionSize;

  private final List<JSType> alternates = new ArrayList<>();
  // If a union has ? or *, we do not care about any other types, except for undefined (for optional
  // properties).
  private boolean containsVoidType = false;
  private boolean isAllType = false;
  private boolean isNativeUnknownType = false;
  private boolean areAllUnknownsChecked = true;

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

  public static UnionTypeBuilder create(JSTypeRegistry registry) {
    return new UnionTypeBuilder(registry, DEFAULT_MAX_UNION_SIZE, false);
  }

  // This is only supposed to be used within `JSTypeRegistry`.
  static UnionTypeBuilder createForPropertyChecking(JSTypeRegistry registry) {
    return new UnionTypeBuilder(registry, PROPERTY_CHECKING_MAX_UNION_SIZE, false);
  }

  // This is only supposed to be used within `UnionType`.
  static UnionTypeBuilder createForCollapsingStructuralSubtypes(JSTypeRegistry registry) {
    return new UnionTypeBuilder(registry, DEFAULT_MAX_UNION_SIZE, true);
  }

  private UnionTypeBuilder(
      JSTypeRegistry registry, int maxUnionSize, boolean structuralSubtypesAreCollapsed) {
    this.registry = registry;
    this.maxUnionSize = maxUnionSize;
    this.structuralSubtypesAreCollapsed = structuralSubtypesAreCollapsed;
  }

  ImmutableList<JSType> getAlternates() {
    JSType specialCaseType = reduceAlternatesWithoutUnion();
    if (specialCaseType != null) {
      return ImmutableList.of(specialCaseType);
    }

    JSType wildcard = getNativeWildcardType();
    if (wildcard != null && containsVoidType) {
      return ImmutableList.of(wildcard, registry.getNativeType(VOID_TYPE));
    }
    // This copy should be pretty cheap since in the common case alternates only contains 2-3 items
    return ImmutableList.copyOf(alternates);
  }

  @VisibleForTesting
  int getAlternatesCount() {
    return alternates.size();
  }

  private boolean isSubtype(JSType rightType, JSType leftType) {
    // if thisType or thatType is an unresolved templatized type,
    // then there is no structural interface matching
    boolean thisUnresolved = rightType.isTemplatizedType()
        && !rightType.toMaybeTemplatizedType().isResolved();
    boolean thatUnresolved = leftType.isTemplatizedType()
        && !leftType.toMaybeTemplatizedType().isResolved();
    if (structuralSubtypesAreCollapsed && !thisUnresolved && !thatUnresolved) {
      return rightType.isSubtypeOf(leftType);
    } else {
      return rightType.isSubtypeWithoutStructuralTyping(leftType);
    }
  }

  /**
   * Adds an alternate to the union type under construction.
   *
   * <p>Returns this for easy chaining.
   *
   * <p>TODO(nickreid): This method shouldn't eagerly collapse types. Doing so risks holding onto a
   * nested union if an alternate resolves so a union after being added. We should do use some
   * technique to determine if the builder has become dirty and caching the result of `build()`
   * until then.
   */
  public UnionTypeBuilder addAlternate(JSType alternate) {
    // build() returns the bottom type by default, so we can
    // just bail out early here.
    if (alternate.isNoType()) {
      return this;
    }

    isAllType = isAllType || alternate.isAllType();
    containsVoidType = containsVoidType || alternate.isVoidType();

    boolean isAlternateUnknown = alternate instanceof UnknownType;
    isNativeUnknownType = isNativeUnknownType || isAlternateUnknown;
    if (isAlternateUnknown) {
      areAllUnknownsChecked = areAllUnknownsChecked &&
          alternate.isCheckedUnknownType();
    }
    if (!isAllType && !isNativeUnknownType) {
      if (alternate.isUnionType()) {
        UnionType union = alternate.toMaybeUnionType();
        List<JSType> alternatesWithoutStructuralTyping =
            union.getAlternatesWithoutStructuralTyping();
        for (int i = 0; i < alternatesWithoutStructuralTyping.size(); i++) {
          JSType unionAlt = alternatesWithoutStructuralTyping.get(i);
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
            if (alternate.isEquivalentTo(current, structuralSubtypesAreCollapsed)) {
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
              // 1) alternate:Array<string> and current:Object ==> Object
              // 2) alternate:Array<string> and current:Array ==> Array
              // 3) alternate:Object.<string> and
              //    current:Array ==> Array|Object<string>
              // 4) alternate:Object and current:Array<string> ==> Object
              // 5) alternate:Array and current:Array<string> ==> Array
              // 6) alternate:Array and
              //    current:Object<string> ==> Array|Object<string>
              // 7) alternate:Array<string> and
              //    current:Array<number> ==> Array<?>
              // 8) alternate:Array<string> and
              //    current:Array<string> ==> Array<string>
              // 9) alternate:Array<string> and
              //    current:Object<string> ==> Object<string>|Array<string>

              if (!current.isTemplatizedType()) {
                if (isSubtype(alternate, current)) {
                  // case 1, 2
                  return this;
                }
                // case 3: leave current, add alternate
              } else if (!alternate.isTemplatizedType()) {
                if (isSubtype(current, alternate)) {
                  // case 4, 5
                  removeCurrent = true;
                }
                // case 6: leave current, add alternate
              } else {
                checkState(current.isTemplatizedType() && alternate.isTemplatizedType());
                TemplatizedType templatizedAlternate = alternate.toMaybeTemplatizedType();
                TemplatizedType templatizedCurrent = current.toMaybeTemplatizedType();

                if (templatizedCurrent.wrapsSameRawType(templatizedAlternate)) {
                  if (alternate.getTemplateTypeMap().checkEquivalenceHelper(
                      current.getTemplateTypeMap(),
                      EquivalenceMethod.IDENTITY,
                      SubtypingMode.NORMAL)) {
                    // case 8
                    return this;
                  } else {
                    // case 7: replace with a merged alternate specialized on `?`.
                    ObjectType rawType = templatizedCurrent.getReferencedObjTypeInternal();
                    // Providing no type-parameter values specializes `rawType` on `?` by default.
                    alternate = registry.createTemplatizedType(rawType, ImmutableList.of());
                    removeCurrent = true;
                  }
                }
                // case 9: leave current, add alternate
              }
              // Otherwise leave both templatized types.
            } else if (isSubtype(alternate, current)) {
              // Alternate is unnecessary.
              mayRegisterDroppedProperties(alternate, current);
              return this;
            } else if (isSubtype(current, alternate)) {
              // Alternate makes current obsolete
              mayRegisterDroppedProperties(current, alternate);
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
          checkState(functionTypePosition == -1);
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

  private void mayRegisterDroppedProperties(JSType subtype, JSType supertype) {
    if (subtype.toMaybeRecordType() != null && supertype.toMaybeRecordType() != null) {
      this.registry.registerDroppedPropertiesInUnion(
          subtype.toMaybeRecordType(), supertype.toMaybeRecordType());
    }
  }

  /**
   * Reduce the alternates into a non-union type.
   * If the alternates can't be accurately represented with a non-union
   * type, return null.
   */
  private JSType reduceAlternatesWithoutUnion() {
    JSType wildcard = getNativeWildcardType();
    if (wildcard != null) {
      return containsVoidType ? null : wildcard;
    }
    int size = alternates.size();
    if (size > maxUnionSize) {
      return registry.getNativeType(UNKNOWN_TYPE);
    } else if (size > 1) {
      return null;
    } else if (size == 1) {
      return alternates.get(0);
    } else {
      return registry.getNativeType(NO_TYPE);
    }
  }

  /** Returns ALL_TYPE, UNKNOWN_TYPE, or CHECKED_UNKNOWN_TYPE, as specified by the flags, or null */
  private JSType getNativeWildcardType() {
    if (isAllType) {
      return registry.getNativeType(ALL_TYPE);
    } else if (isNativeUnknownType) {
      if (areAllUnknownsChecked) {
        return registry.getNativeType(CHECKED_UNKNOWN_TYPE);
      } else {
        return registry.getNativeType(UNKNOWN_TYPE);
      }
    }
    return null;
  }

  /**
   * Creates a union.
   * @return A UnionType if it has two or more alternates, the
   *    only alternate if it has one and otherwise {@code NO_TYPE}.
   */
  public JSType build() {
    if (result == null) {
      result = reduceAlternatesWithoutUnion();
      if (result == null) {
        result = new UnionType(registry, ImmutableList.copyOf(getAlternates()));
      }
    }
    return result;
  }
}
