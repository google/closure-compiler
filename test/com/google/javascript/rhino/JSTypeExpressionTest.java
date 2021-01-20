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

package com.google.javascript.rhino;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.JSTypeExpression.IMPLICIT_TEMPLATE_BOUND;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSTypeExpressionTest {

  @Test
  public void testGetAllTypeNames() throws Exception {
    assertThat(getTestExpression().getAllTypeNames())
        .containsExactly("foo.Bar", "string", "Object");
  }

  @Test
  public void testGetAllTypeNodes() throws Exception {
    assertThat(
            getTestExpression().getAllTypeNodes().stream()
                .map(Node::getString)
                .collect(toImmutableList()))
        .containsExactly("foo.Bar", "string", "Object", "string");
  }

  @Test
  public void testIsExplicitUnknownTemplateBound() throws Exception {
    // Explicit unknown template bound should return true.
    assertThat(new JSTypeExpression(new Node(Token.QMARK), "").isExplicitUnknownTemplateBound())
        .isTrue();

    // The implicit bound should return false, including copies of it.
    JSTypeExpression implicitBoundCopy = JSTypeExpression.IMPLICIT_TEMPLATE_BOUND.copy();
    assertThat(implicitBoundCopy).isNotSameInstanceAs(IMPLICIT_TEMPLATE_BOUND);
    assertThat(IMPLICIT_TEMPLATE_BOUND.isExplicitUnknownTemplateBound()).isFalse();
    assertThat(implicitBoundCopy.isExplicitUnknownTemplateBound()).isFalse();

    // Non-unknown bounds should return false, even if they start with a "?".
    assertThat(new JSTypeExpression(new Node(Token.STAR), "").isExplicitUnknownTemplateBound())
        .isFalse();
    assertThat(
            new JSTypeExpression(new Node(Token.QMARK, Node.newString("number")), "")
                .isExplicitUnknownTemplateBound())
        .isFalse();
  }

  private static JSTypeExpression getTestExpression() throws Exception {
    Node a = Node.newString("foo.Bar");
    Node b = Node.newString("string");
    Node c = new Node(Token.PIPE);
    c.addChildToBack(a);
    c.addChildToBack(b);
    Node d = Node.newString("Object");
    Node e = Node.newString("string");
    Node f = new Node(Token.PIPE);
    f.addChildToBack(c);
    f.addChildToBack(d);
    f.addChildToBack(e);
    return new JSTypeExpression(f, "");
  }
}
