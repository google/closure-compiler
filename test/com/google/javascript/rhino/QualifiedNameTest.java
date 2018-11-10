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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QualifiedNameTest {

  // All of these qualified names are "foo.bar.baz"
  private static final QualifiedName FROM_STRING = QualifiedName.of("foo.bar.baz");
  private static final QualifiedName FROM_NODE =
      IR.getprop(IR.getprop(IR.name("foo"), IR.string("bar")), IR.string("baz"))
          .getQualifiedNameObject();
  private static final QualifiedName FROM_GETPROP =
      QualifiedName.of("foo").getprop("bar").getprop("baz");

  private static Node qname(Node root, String... props) {
    Node n = root;
    for (String p : props) {
      n = IR.getprop(n, IR.string(p));
    }
    return n;
  }

  @Test
  public void testJoin_fromString() {
    QualifiedName n = QualifiedName.of("foo.bar.baz");
    assertThat(n.join()).isEqualTo("foo.bar.baz");
  }

  @Test
  public void testJoin_fromNode() {
    QualifiedName n = qname(IR.name("foo"), "bar", "baz").getQualifiedNameObject();
    assertThat(n.join()).isEqualTo("foo.bar.baz");

    n = qname(IR.thisNode(), "bar", "baz").getQualifiedNameObject();
    assertThat(n.join()).isEqualTo("this.bar.baz");

    n = qname(IR.superNode(), "bar", "baz").getQualifiedNameObject();
    assertThat(n.join()).isEqualTo("super.bar.baz");
  }

  @Test
  public void testJoin_fromGetprop() {
    QualifiedName n = QualifiedName.of("foo").getprop("bar").getprop("baz");
    assertThat(n.join()).isEqualTo("foo.bar.baz");
  }

  @Test
  public void testComponents_fromString() {
    QualifiedName n = QualifiedName.of("foo.bar.baz");
    assertThat(n.components()).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void testComponents_fromNode() {
    QualifiedName n = qname(IR.name("foo"), "bar", "baz").getQualifiedNameObject();
    assertThat(n.components()).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void testComponents_fromGetprop() {
    QualifiedName n = QualifiedName.of("foo").getprop("bar").getprop("baz");
    assertThat(n.components()).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void testGetOwner_fromString() {
    assertThat(QualifiedName.of("foo.bar.baz").getOwner().join()).isEqualTo("foo.bar");
    assertThat(QualifiedName.of("foo").getOwner()).isNull();
  }

  @Test
  public void testGetOwner_fromNode() {
    QualifiedName n = qname(IR.name("foo"), "bar", "baz").getQualifiedNameObject();
    assertThat(n.getOwner().join()).isEqualTo("foo.bar");
    assertThat(IR.name("foo").getQualifiedNameObject().getOwner()).isNull();
    assertThat(IR.thisNode().getQualifiedNameObject().getOwner()).isNull();
    assertThat(IR.superNode().getQualifiedNameObject().getOwner()).isNull();
  }

  @Test
  public void testGetOwner_fromGetprop() {
    QualifiedName foo = QualifiedName.of("foo");
    QualifiedName fooBar = foo.getprop("bar");
    assertThat(fooBar.getOwner()).isEqualTo(foo);
    assertThat(fooBar.getprop("baz").getOwner()).isEqualTo(fooBar);
  }

  @Test
  public void testGetComponent_fromString() {
    assertThat(QualifiedName.of("foo.bar.baz").getComponent()).isEqualTo("baz");
    assertThat(QualifiedName.of("foo").getComponent()).isEqualTo("foo");
  }

  @Test
  public void testGetComponent_fromNode() {
    QualifiedName n = qname(IR.name("foo"), "bar", "baz").getQualifiedNameObject();
    assertThat(n.getComponent()).isEqualTo("baz");
    assertThat(IR.name("foo").getQualifiedNameObject().getComponent()).isEqualTo("foo");
    assertThat(IR.thisNode().getQualifiedNameObject().getComponent()).isEqualTo("this");
    assertThat(IR.superNode().getQualifiedNameObject().getComponent()).isEqualTo("super");
  }

  @Test
  public void testGetComponent_fromGetprop() {
    QualifiedName foo = QualifiedName.of("foo");
    QualifiedName fooBar = foo.getprop("bar");
    assertThat(fooBar.getComponent()).isEqualTo("bar");
    assertThat(fooBar.getprop("baz").getComponent()).isEqualTo("baz");
  }

  @Test
  public void testMatch_fromString() {
    QualifiedName n = QualifiedName.of("foo.bar.baz");
    assertThat(n.matches(qname(IR.name("foo"), "bar", "baz"))).isTrue();
    assertThat(n.matches(qname(IR.thisNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.superNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.name("foo"), "baz", "bar"))).isFalse();

    n = QualifiedName.of("this.qux");
    assertThat(n.matches(qname(IR.thisNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();

    n = QualifiedName.of("super.qux");
    assertThat(n.matches(qname(IR.superNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();
  }

  @Test
  public void testMatch_fromNode() {
    QualifiedName n = qname(IR.name("foo"), "bar", "baz").getQualifiedNameObject();
    assertThat(n.matches(qname(IR.name("foo"), "bar", "baz"))).isTrue();
    assertThat(n.matches(qname(IR.thisNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.superNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.name("foo"), "baz", "bar"))).isFalse();

    n = qname(IR.thisNode(), "qux").getQualifiedNameObject();
    assertThat(n.matches(qname(IR.thisNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();

    n = qname(IR.superNode(), "qux").getQualifiedNameObject();
    assertThat(n.matches(qname(IR.superNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();
  }

  @Test
  public void testMatch_fromGetprop() {
    QualifiedName n = QualifiedName.of("foo").getprop("bar").getprop("baz");
    assertThat(n.matches(qname(IR.name("foo"), "bar", "baz"))).isTrue();
    assertThat(n.matches(qname(IR.thisNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.superNode(), "bar", "baz"))).isFalse();
    assertThat(n.matches(qname(IR.name("foo"), "baz", "bar"))).isFalse();

    n = QualifiedName.of("this").getprop("qux");
    assertThat(n.matches(qname(IR.thisNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();

    n = QualifiedName.of("super").getprop("qux");
    assertThat(n.matches(qname(IR.superNode(), "qux"))).isTrue();
    assertThat(n.matches(qname(IR.name("x"), "qux"))).isFalse();
  }
}
