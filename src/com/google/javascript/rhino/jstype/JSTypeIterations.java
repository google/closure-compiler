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

import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@code Stream}-like functions for efficient querying of collections of {@code JSType}s.
 *
 * <p>These methods are designed to minimize allocations since they are expected to be called
 * <em>very</em> often. That's why they don't:
 *
 * <ul>
 *   <li>instantiate {@link Iterator}s
 *   <li>instantiate {@link Stream}s
 *   <li>(un)box primitives
 *   <li>expect closure-generating lambdas
 * </ul>
 */
final class JSTypeIterations {

  /** @return whether any element of {@code types} matches {@code predicate}. */
  static boolean anyTypeMatches(
      Predicate<? super JSType> predicate, ImmutableList<? extends JSType> types) {
    for (int i = 0; i < types.size(); i++) {
      if (predicate.test(types.get(i))) {
        return true;
      }
    }
    return false;
  }

  /** @return whether any element of {@code union} matches {@code predicate}. */
  static boolean anyTypeMatches(Predicate<? super JSType> predicate, UnionType union) {
    return anyTypeMatches(predicate, union.getAlternates());
  }

  /** @return whether all elements of {@code types} match {@code predicate}. */
  static boolean allTypesMatch(
      Predicate<? super JSType> predicate, ImmutableList<? extends JSType> types) {
    for (int i = 0; i < types.size(); i++) {
      if (!predicate.test(types.get(i))) {
        return false;
      }
    }
    return true;
  }

  /** @return whether all elements of {@code union} match {@code predicate}. */
  static boolean allTypesMatch(Predicate<? super JSType> predicate, UnionType union) {
    return allTypesMatch(predicate, union.getAlternates());
  }

  /** @return the result of applying {@code mapper} to all elements of {@code types}. */
  static <T extends JSType> ImmutableList<T> mapTypes(
      Function<? super JSType, T> mapper, ImmutableList<? extends JSType> types) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < types.size(); i++) {
      builder.add(mapper.apply(types.get(i)));
    }
    return builder.build();
  }

  /** @return the union of applying {@code mapper} to all elements of {@code types}. */
  static JSType mapTypes(Function<? super JSType, ? extends JSType> mapper, UnionType union) {
    return UnionType.builder(union.registry)
        .addAlternates(mapTypes(mapper, union.getAlternates()))
        .build();
  }

  private JSTypeIterations() {
    // Not instantiable.
  }
}
