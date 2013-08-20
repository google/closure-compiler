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
 *   Nick Santos
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

import com.google.javascript.rhino.ErrorReporter;

/**
 * A namespace type is a reference to a particular object.
 *
 * This is generally useful when you have a particular object
 * in mind, but need to give it a name when it's passed to a function.
 *
 * For example,
 * <code>
 * /** @namespace /
 * var jQuery = {};
 *
 * /** @return {jQuery.} /
 * jQuery.get = function () {
 *   return jQuery // for easy chaining
 * }
 * </code>
 *
 * @see https://docs.google.com/document/d/1r37CJ6ZW0zk28IMn1Tu8UKKjs2WcJ-6dNEb3om7FoHQ
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */
class NamespaceType extends NamedType {
  private static final long serialVersionUID = 1L;

  /**
   * Create a namespace type based on the reference.
   */
  NamespaceType(JSTypeRegistry registry, String reference,
      String sourceName, int lineno, int charno) {
    super(registry, reference, sourceName, lineno, charno);
  }

  /**
   * Resolve the referenced type within the enclosing scope.
   */
  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> enclosing) {
    warning(t, "Namespaces not supported yet (" + getReferenceName() + ")");
    return registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
  }
}
