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

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.io.Serializable;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.nullness.Nullable;

/**
 * When parsing a jsdoc, a type-annotation string is parsed to a type AST. Somewhat confusingly, we
 * use the Node class both for type ASTs and for the source-code AST. JSTypeExpression wraps a type
 * AST. During type checking, type ASTs are evaluated to JavaScript types.
 */
public final class JSTypeExpression implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String IMPLICIT_TEMPLATE_BOUND_SOURCE = "<IMPLICIT_TEMPLATE_BOUND>";

  static final JSTypeExpression IMPLICIT_TEMPLATE_BOUND =
      new JSTypeExpression(new Node(Token.QMARK), IMPLICIT_TEMPLATE_BOUND_SOURCE);

  static {
    IMPLICIT_TEMPLATE_BOUND
        .getRoot()
        .setStaticSourceFile(
            new SimpleSourceFile(
                IMPLICIT_TEMPLATE_BOUND_SOURCE, StaticSourceFile.SourceKind.STRONG));
  }

  /** The root of the AST. */
  private final Node root;

  /** The source name where the type expression appears. */
  private final String sourceName;

  public JSTypeExpression(Node root, String sourceName) {
    this.root = root;
    this.sourceName = sourceName;
  }

  /** Replaces given names in this type expression with unknown */
  public JSTypeExpression replaceNamesWithUnknownType(Set<String> names) {
    Node oldExprRoot = this.root.cloneTree();
    Node newExprRoot = replaceNames(oldExprRoot, names);
    JSTypeExpression newTypeExpression = new JSTypeExpression(newExprRoot, sourceName);
    return newTypeExpression;
  }

  /**
   * Recursively traverse over the type tree and replace matched types with unknown type
   *
   * @param n Root of the JsTypeExpression on which replacement is applied
   * @param names The set of names to replace in this type expression
   * @return the new root after replacing the names
   */
  private static @Nullable Node replaceNames(Node n, Set<String> names) {
    if (n == null) {
      return null;
    }
    for (Node child = n.getFirstChild(); child != null; ) {
      final Node next = child.getNext();
      replaceNames(child, names);
      child = next;
    }
    if (n.isStringLit() && names.contains(n.getString())) {
      Node qMark = new Node(Token.QMARK);
      qMark.addChildrenToBack(n.removeChildren());
      if (n.hasParent()) {
        n.replaceWith(qMark);
      }
      return qMark;
    }
    return n;
  }

  /** Returns a list of all type nodes in this type expression. */
  public ImmutableList<Node> getAllTypeNodes() {
    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    visitAllTypeNodes(this.root, builder::add);
    return builder.build();
  }

  /** Returns a set of all string names in this type expression */
  public ImmutableSet<String> getAllTypeNames() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    visitAllTypeNodes(this.root, (n) -> builder.add(n.getString()));
    return builder.build();
  }

  /** Recursively traverse the type tree and visit all type nodes. */
  private static void visitAllTypeNodes(Node n, Consumer<Node> visitor) {
    if (n == null) {
      return;
    }
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      visitAllTypeNodes(child, visitor);
    }
    if (n.isStringLit()) {
      visitor.accept(n);
    }
  }

  /** Make the given type expression into an optional type expression, if possible. */
  public static JSTypeExpression makeOptionalArg(JSTypeExpression expr) {
    if (expr.isOptionalArg() || expr.isVarArgs()) {
      return expr;
    } else {
      Node equals = new Node(Token.EQUALS, expr.root);
      equals.clonePropsFrom(expr.root);
      return new JSTypeExpression(equals, expr.sourceName);
    }
  }

  /** Does this expression denote an optional {@code @param}? */
  public boolean isOptionalArg() {
    return root.getToken() == Token.EQUALS;
  }

  /** Does this expression denote a rest args {@code @param}? */
  public boolean isVarArgs() {
    return root.getToken() == Token.ITER_REST;
  }

  /** Evaluates the type expression into a {@code JSType} object. */
  public JSType evaluate(@Nullable StaticTypedScope scope, JSTypeRegistry registry) {
    JSType type = registry.createTypeFromCommentNode(root, sourceName, scope);
    root.setJSType(type);
    return type;
  }

  /** Does this object represent a type expression that is equivalent to the other one? */
  public boolean isEquivalentTo(@Nullable JSTypeExpression other) {
    return other != null && root.isEquivalentTo(other.root);
  }

  /**
   * @return The source for this type expression. Note that it will not contain an expression if
   *     there's an @override tag.
   */
  public Node getRoot() {
    return root;
  }

  public String getSourceName() {
    return this.sourceName;
  }

  @Override
  public String toString() {
    return "type: " + root.toStringTree();
  }

  public JSTypeExpression copy() {
    return new JSTypeExpression(root.cloneTree(), sourceName);
  }

  /** Whether this expression is an explicit unknown template bound. */
  public boolean isExplicitUnknownTemplateBound() {
    return identical(root.getToken(), Token.QMARK)
        && !root.hasChildren()
        && !sourceName.equals(IMPLICIT_TEMPLATE_BOUND_SOURCE);
  }

  /**
   * Returns a set of keys of all record types (e.g. {{key : string }}) present in this
   * JSTypeExpression.
   */
  public ImmutableSet<String> getRecordPropertyNames() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    getRecordPropertyNamesRecursive(this.root, builder);
    return builder.build();
  }

  private static void getRecordPropertyNamesRecursive(Node n, ImmutableSet.Builder<String> names) {
    if (n == null) {
      return;
    }

    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      getRecordPropertyNamesRecursive(child, names);
    }
    if (n.isStringKey()) {
      names.add(n.getString());
    }
  }
}
