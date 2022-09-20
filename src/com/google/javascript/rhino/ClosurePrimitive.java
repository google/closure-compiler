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

package com.google.javascript.rhino;

import com.google.common.collect.ImmutableMap;
import org.jspecify.nullness.Nullable;

/**
 * Enum of primitive functions that the compiler recognizes
 *
 * <p>These correspond to the @closurePrimitive tag in code; in order to parse new primitives, add
 * any entry to the list in parsing/ParserConfig.properties, then map it to an enum member in the
 * idToEnum map.
 *
 * <p>After typechecking is done, all calls to one of these primitive types should have their
 * FunctionType annotated with the corresponding enum member. This is intended to make identifying
 * these calls more accurate than previous methods of finding primitives by qualified name.
 */
public enum ClosurePrimitive {
  ASSERTS_FAIL, // A function that always throws an error
  ASSERTS_MATCHES_RETURN, // A function that asserts its first parameter matches the return type
  ASSERTS_TRUTHY; // A function that asserts its first parameter is truthy and returns the param

  /**
   * Maps human-readable ids to enum members.
   *
   * <p>The expected mapping (although not enforced) of keys -> values is that the enum member maps
   * to a lowercase string with "_" replaced with "."
   */
  private static final ImmutableMap<String, ClosurePrimitive> ID_TO_ENUM =
      ImmutableMap.of(
          "asserts.fail",
          ASSERTS_FAIL,
          "asserts.truthy",
          ASSERTS_TRUTHY,
          "asserts.matchesReturn",
          ASSERTS_MATCHES_RETURN);

  /**
   * Returns the ClosurePrimitive corresponding to the given string id.
   *
   * <p>This is to make reading {@code @closurePrimitive} easier in code. Using Enum.valueOf to
   * parse closure primitive identifiers from JSDoc directly would require code like {@code
   * closurePrimitive {ASSERTS_FAIL}}; instead we separate the string ids from the enum names.
   *
   * @param id a string id that normalized to an enum member, or null
   * @throws IllegalArgumentException if the id is non-null but does not match an enum member
   * @return null if the argument is null, otherwise the corresponding enum member
   */
  public static @Nullable ClosurePrimitive fromStringId(@Nullable String id) {
    if (id == null) {
      return null;
    }

    ClosurePrimitive primitive = ID_TO_ENUM.get(id);
    if (primitive == null) {
      throw new IllegalArgumentException(
          "String id " + id + " does not match any ClosurePrimitive");
    }

    return primitive;
  }
}
