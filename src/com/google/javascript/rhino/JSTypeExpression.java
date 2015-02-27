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

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.StaticTypedScope;

import java.io.Serializable;

/**
 * Represents a type expression as a miniature Rhino AST, so that the
 * type expression can be evaluated later.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class JSTypeExpression implements Serializable {
  private static final long serialVersionUID = 1L;

  /** The root of the AST. */
  private final Node root;

  /** The source name where the type expression appears. */
  private final String sourceName;

  public JSTypeExpression(Node root, String sourceName) {
    this.root = root;
    this.sourceName = sourceName;
  }

  /**
   * Make the given type expression into an optional type expression,
   * if possible.
   */
  public static JSTypeExpression makeOptionalArg(JSTypeExpression expr) {
    if (expr.isOptionalArg() || expr.isVarArgs()) {
      return expr;
    } else {
      return new JSTypeExpression(
          new Node(Token.EQUALS, expr.root), expr.sourceName);
    }
  }

  /**
   * @return Whether this expression denotes an optional {@code @param}.
   */
  public boolean isOptionalArg() {
    return root.getType() == Token.EQUALS;
  }

  /**
   * @return Whether this expression denotes a rest args {@code @param}.
   */
  public boolean isVarArgs() {
    return root.getType() == Token.ELLIPSIS;
  }

  /**
   * Evaluates the type expression into a {@code JSType} object.
   */
  public JSType evaluate(StaticTypedScope<JSType> scope, JSTypeRegistry registry) {
    JSType type = registry.createFromTypeNodes(root, sourceName, scope);
    root.setJSType(type);
    return type;
  }

  public TypeI evaluateInEmptyScope(TypeIRegistry registry) {
    return evaluate(null, (JSTypeRegistry) registry);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JSTypeExpression &&
        ((JSTypeExpression) other).root.isEquivalentTo(root);
  }

  @Override
  public int hashCode() {
    return root.toStringTree().hashCode();
  }

  /**
   * @return The source for this type expression.  Note that it will not
   * contain an expression if there's an @override tag.
   */
  public Node getRoot() {
    return root;
  }

  @Override
  public String toString() {
    return "type: " + root.toString();
  }
}
