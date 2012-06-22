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

/**
 * Constants corresponding to types that are built into a JavaScript engine
 * and other types that occur very often in the type system. See
 * {@link com.google.javascript.rhino.jstype.JSTypeRegistry#getNativeType(JSTypeNative)}.
 */
public enum JSTypeNative {
  // Built-in types (please keep alphabetized)

  ARRAY_TYPE,
  ARRAY_FUNCTION_TYPE,

  BOOLEAN_TYPE,
  BOOLEAN_OBJECT_TYPE,
  BOOLEAN_OBJECT_FUNCTION_TYPE,

  /**
   * A checked unknown type is a type that we know something about,
   * but we're not really sure what we know about it.
   *
   * Examples of checked unknown types include:
   * <code>
   * if (x) { // x is unknown
   *   alert(x); // x is checked unknown
   * }
   * </code>
   *
   * <code>
   * /* @param {SomeForwardDeclaredType} x /
   * function f(x) {
   *   // x is checked unknown. We know it's some type, but the type
   *   // has not been included in this binary.
   * }
   * </code>
   *
   * This is useful for missing property warnings, where we don't
   * want to emit warnings on things that have been checked.
   */
  CHECKED_UNKNOWN_TYPE,

  DATE_TYPE,
  DATE_FUNCTION_TYPE,

  ERROR_FUNCTION_TYPE,
  ERROR_TYPE,

  EVAL_ERROR_FUNCTION_TYPE,
  EVAL_ERROR_TYPE,

  FUNCTION_FUNCTION_TYPE,
  FUNCTION_INSTANCE_TYPE, // equivalent to U2U_CONSTRUCTOR_TYPE
  FUNCTION_PROTOTYPE,

  NULL_TYPE,

  NUMBER_TYPE,
  NUMBER_OBJECT_TYPE,
  NUMBER_OBJECT_FUNCTION_TYPE,

  OBJECT_TYPE,
  OBJECT_FUNCTION_TYPE,
  OBJECT_PROTOTYPE,

  RANGE_ERROR_FUNCTION_TYPE,
  RANGE_ERROR_TYPE,

  REFERENCE_ERROR_FUNCTION_TYPE,
  REFERENCE_ERROR_TYPE,

  REGEXP_TYPE,
  REGEXP_FUNCTION_TYPE,

  STRING_OBJECT_TYPE,
  STRING_OBJECT_FUNCTION_TYPE,
  STRING_TYPE,

  SYNTAX_ERROR_FUNCTION_TYPE,
  SYNTAX_ERROR_TYPE,

  TYPE_ERROR_FUNCTION_TYPE,
  TYPE_ERROR_TYPE,

  UNKNOWN_TYPE,

  URI_ERROR_FUNCTION_TYPE,
  URI_ERROR_TYPE,

  VOID_TYPE,

  // Commonly used types

  TOP_LEVEL_PROTOTYPE,
  STRING_VALUE_OR_OBJECT_TYPE,
  NUMBER_VALUE_OR_OBJECT_TYPE,
  ALL_TYPE,
  NO_TYPE,
  NO_OBJECT_TYPE,
  NO_RESOLVED_TYPE,
  GLOBAL_THIS,
  U2U_CONSTRUCTOR_TYPE,
  U2U_FUNCTION_TYPE,

  LEAST_FUNCTION_TYPE,
  GREATEST_FUNCTION_TYPE,

  /**
   * (null, void)
   */
  NULL_VOID,

  /**
   * (Object,number,string)
   */
  OBJECT_NUMBER_STRING,

  /**
   * (Object,number,string,boolean)
   */
  OBJECT_NUMBER_STRING_BOOLEAN,

  /**
   * (number,string,boolean)
   */
  NUMBER_STRING_BOOLEAN,

  /**
   * (number,string)
   */
  NUMBER_STRING,
}
