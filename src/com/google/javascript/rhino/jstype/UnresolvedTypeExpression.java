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
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;

/**
 * An {@code UnresolvedType} is a reference to some type expression.
 * This provides a convenient mechanism for implementing forward
 * references to types; a {@code UnresolvedType} can be used as a
 * placeholder until its reference is resolved.
 *
 * The {@code UnresolvedType} will behave like an opaque unknown type.
 * When its {@code #resolve} method is called, it will return the underlying
 * type. The underlying type can resolve to any JS type.<p>
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class UnresolvedTypeExpression extends UnknownType {
  private static final long serialVersionUID = 1L;

  private final Node typeExpr;
  private final String sourceName;

  /**
   * Create a named type based on the reference.
   */
  UnresolvedTypeExpression(JSTypeRegistry registry, Node typeExpr,
      String sourceName) {
    super(registry, false);

    Preconditions.checkNotNull(typeExpr);
    this.typeExpr = typeExpr;
    this.sourceName = sourceName;
  }

  /**
   * Resolve the referenced type within the enclosing scope.
   */
  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> enclosing) {
    return registry.createFromTypeNodes(typeExpr, sourceName, enclosing);
  }
}
