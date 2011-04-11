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

/**
 * This type is for built-in error constructors.
 */
class ErrorFunctionType extends FunctionType {
  private static final long serialVersionUID = 1L;

  ErrorFunctionType(JSTypeRegistry registry, String name) {
    super(
        registry, name, null,
        registry.createArrowType(
            registry.createOptionalParameters(
                registry.getNativeType(ALL_TYPE),
                registry.getNativeType(ALL_TYPE),
                registry.getNativeType(ALL_TYPE)),
            null),
        null, null, true, true);

    // NOTE(nicksantos): Errors have the weird behavior in that they can
    // be called as functions, and they will return instances of themselves.
    // Error('x') instanceof Error => true
    //
    // In user-defined types, we would deal with this case by creating
    // a NamedType with the name "Error" and then resolve it later.
    //
    // For native types, we don't really want the native types to
    // depend on type-resolution. So we just set the return type manually
    // at the end of construction.
    //
    // There's similar logic in JSTypeRegistry for Array and RegExp.
    getInternalArrowType().returnType = getInstanceType();
  }
}
