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

import com.google.javascript.rhino.Node.NodeMismatch;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.TestErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NodeTest {
  @Test
  public void testMergeExtractNormal() {
    testMergeExtract(5, 6);
    testMergeExtract(456, 3423);
    testMergeExtract(0, 0);
  }

  @Test
  public void testMergeExtractErroneous() {
    assertThat(Node.mergeLineCharNo(-5, 90)).isEqualTo(-1);
    assertThat(Node.mergeLineCharNo(0, -1)).isEqualTo(-1);
    assertThat(Node.extractLineno(-1)).isEqualTo(-1);
    assertThat(Node.extractCharno(-1)).isEqualTo(-1);
  }

  @Test
  public void testMergeOverflowGraciously() {
    int linecharno = Node.mergeLineCharNo(89, 4096);
    assertThat(Node.extractLineno(linecharno)).isEqualTo(89);
    assertThat(Node.extractCharno(linecharno)).isEqualTo(4095);
  }

  @Test
  public void testCheckTreeEqualsImplSame() {
    Node node1 = new Node(Token.LET, new Node(Token.VAR));
    Node node2 = new Node(Token.LET, new Node(Token.VAR));
    assertThat(node1.checkTreeEqualsImpl(node2)).isNull();
  }

  @Test
  public void testCheckTreeEqualsImplDifferentType() {
    Node node1 = new Node(Token.LET, new Node(Token.VAR));
    Node node2 = new Node(Token.VAR, new Node(Token.VAR));
    assertThat(node1.checkTreeEqualsImpl(node2)).isEqualTo(new NodeMismatch(node1, node2));
  }

  @Test
  public void testCheckTreeEqualsImplDifferentChildCount() {
    Node node1 = new Node(Token.LET, new Node(Token.VAR));
    Node node2 = new Node(Token.LET);
    assertThat(node1.checkTreeEqualsImpl(node2)).isEqualTo(new NodeMismatch(node1, node2));
  }

  @Test
  public void testCheckTreeEqualsImplDifferentChild() {
    Node child1 = new Node(Token.LET);
    Node child2 = new Node(Token.VAR);
    Node node1 = new Node(Token.LET, child1);
    Node node2 = new Node(Token.LET, child2);
    assertThat(node1.checkTreeEqualsImpl(node2)).isEqualTo(new NodeMismatch(child1, child2));
  }

  @Test
  public void testCheckTreeEqualsSame() {
    Node node1 = new Node(Token.LET);
    assertThat(node1.checkTreeEquals(node1)).isNull();
  }

  @Test
  public void testCheckTreeEqualsStringDifferent() {
    Node node1 = new Node(Token.ADD);
    Node node2 = new Node(Token.SUB);
    assertThat(node1.checkTreeEquals(node2)).isNotNull();
  }

  @Test
  public void testCheckTreeEqualsBooleanSame() {
    Node node1 = new Node(Token.LET);
    assertThat(node1.isEquivalentTo(node1)).isTrue();
  }

  @Test
  public void testCheckTreeEqualsBooleanDifferent() {
    Node node1 = new Node(Token.LET);
    Node node2 = new Node(Token.VAR);
    assertThat(node1.isEquivalentTo(node2)).isFalse();
  }

  @Test
  public void testCheckTreeEqualsSlashVDifferent() {
    Node node1 = Node.newString("\u000B");
    node1.putBooleanProp(Node.SLASH_V, true);
    Node node2 = Node.newString("\u000B");
    assertThat(node1.isEquivalentTo(node2)).isFalse();
  }

  @Test
  public void testCheckTreeEqualsImplDifferentIncProp() {
    Node node1 = new Node(Token.INC);
    node1.putBooleanProp(Node.INCRDECR_PROP, true);
    Node node2 = new Node(Token.INC);
    assertThat(node1.checkTreeEqualsImpl(node2)).isNotNull();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsSame() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsSameNull() {
    Node node1 = Node.newString(Token.NAME, "f");
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isTrue();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsDifferent() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
  }

  @Test
  public void testCheckTreeTypeAwareEqualsDifferentNull() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    assertThat(node1.isEquivalentToTyped(node2)).isFalse();
  }

  @Test
  public void testVarArgs1() {
    assertThat(new Node(Token.LET).isVarArgs()).isFalse();
  }

  @Test
  public void testVarArgs2() {
    Node n = new Node(Token.LET);
    n.setVarArgs(false);
    assertThat(n.isVarArgs()).isFalse();
  }

  @Test
  public void testVarArgs3() {
    Node n = new Node(Token.LET);
    n.setVarArgs(true);
    assertThat(n.isVarArgs()).isTrue();
  }

  private void testMergeExtract(int lineno, int charno) {
    int linecharno = Node.mergeLineCharNo(lineno, charno);
    assertThat(Node.extractLineno(linecharno)).isEqualTo(lineno);
    assertThat(Node.extractCharno(linecharno)).isEqualTo(charno);
  }

  @Test
  public void testIsQualifiedName() {
    assertThat(IR.name("a").isQualifiedName()).isTrue();
    assertThat(IR.name("$").isQualifiedName()).isTrue();
    assertThat(IR.name("_").isQualifiedName()).isTrue();
    assertThat(IR.getprop(IR.name("a"), IR.string("b")).isQualifiedName()).isTrue();
    assertThat(IR.getprop(IR.thisNode(), IR.string("b")).isQualifiedName()).isTrue();
    assertThat(IR.number(0).isQualifiedName()).isFalse();
    assertThat(IR.arraylit().isQualifiedName()).isFalse();
    assertThat(IR.objectlit().isQualifiedName()).isFalse();
    assertThat(IR.string("").isQualifiedName()).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).isQualifiedName()).isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), IR.string("c")).isQualifiedName())
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), IR.string("b")), IR.string("c")).isQualifiedName())
        .isFalse();
    assertThat(IR.call(IR.name("a")).isQualifiedName()).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), IR.string("b")).isQualifiedName())
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), IR.string("b"))).isQualifiedName())
        .isFalse();
    assertThat(IR.string("a").isQualifiedName()).isFalse();
    assertThat(IR.regexp(IR.string("x")).isQualifiedName()).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).isQualifiedName()).isFalse();
  }

  @Test
  public void testMatchesQualifiedNameX() {
    assertThat(qname("this.b").matchesQualifiedName("this.b")).isTrue();
  }

  @Test
  public void testMatchesQualifiedName1() {
    assertThat(IR.name("a").matchesQualifiedName("a")).isTrue();
    assertThat(IR.name("a").matchesQualifiedName("ab")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName((String) null)).isFalse();
    assertThat(IR.name("a").matchesQualifiedName(".b")).isFalse();
    assertThat(IR.name("a").matchesQualifiedName("a.")).isFalse();

    assertThat(qname("a.b").matchesQualifiedName("a")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName("a.b")).isTrue();
    assertThat(qname("a.b").matchesQualifiedName("a.bc")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(".b")).isFalse();
    assertThat(qname("a.b").matchesQualifiedName("this.b")).isFalse();

    assertThat(qname("this").matchesQualifiedName("this")).isTrue();
    assertThat(qname("this").matchesQualifiedName("thisx")).isFalse();

    assertThat(qname("this.b").matchesQualifiedName("a")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("a.b")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(".b")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("a.")).isFalse();
    assertThat(qname("this.b").matchesQualifiedName("this.b")).isTrue();

    assertThat(qname("a.b.c").matchesQualifiedName("a.b.c")).isTrue();
    assertThat(qname("a.b.c").matchesQualifiedName("a.b.c")).isTrue();

    assertThat(IR.number(0).matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.arraylit().matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.objectlit().matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.string("").matchesQualifiedName("a.b")).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).matchesQualifiedName("a.b")).isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), IR.string("c"))
                .matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), IR.string("b")), IR.string("c"))
                .matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat(IR.call(IR.name("a")).matchesQualifiedName("a")).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), IR.string("b")).matchesQualifiedName("a.b"))
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), IR.string("b"))).matchesQualifiedName("a.b"))
        .isFalse();
    assertThat(IR.string("a").matchesQualifiedName("a")).isFalse();
    assertThat(IR.regexp(IR.string("x")).matchesQualifiedName("x")).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).matchesQualifiedName("x")).isFalse();
  }

  @Test
  public void testMatchesQualifiedName2() {
    assertThat(IR.name("a").matchesQualifiedName(qname("a"))).isTrue();
    assertThat(IR.name("a").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.name("a").matchesQualifiedName((Node) null)).isFalse();

    assertThat(qname("a.b").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(qname("a.b"))).isTrue();
    assertThat(qname("a.b").matchesQualifiedName(qname(".b"))).isFalse();
    assertThat(qname("a.b").matchesQualifiedName(qname("this.b"))).isFalse();

    assertThat(qname("this.b").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(qname("this.b").matchesQualifiedName(qname("this.b"))).isTrue();

    assertThat(qname("a.b.c").matchesQualifiedName(qname("a.b.c"))).isTrue();
    assertThat(qname("a.b.c").matchesQualifiedName(qname("a.b.c"))).isTrue();

    assertThat(IR.number(0).matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.arraylit().matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.objectlit().matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.string("").matchesQualifiedName(qname("a.b"))).isFalse();
    assertThat(IR.getelem(IR.name("a"), IR.string("b")).matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat( // a[b].c
            IR.getprop(IR.getelem(IR.name("a"), IR.string("b")), IR.string("c"))
                .matchesQualifiedName(qname("a.b.c")))
        .isFalse();
    assertThat( // a.b[c]
            IR.getelem(IR.getprop(IR.name("a"), IR.string("b")), IR.string("c"))
                .matchesQualifiedName("a.b.c"))
        .isFalse();
    assertThat(IR.call(IR.name("a")).matchesQualifiedName(qname("a"))).isFalse();
    assertThat( // a().b
            IR.getprop(IR.call(IR.name("a")), IR.string("b")).matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat( // (a.b)()
            IR.call(IR.getprop(IR.name("a"), IR.string("b"))).matchesQualifiedName(qname("a.b")))
        .isFalse();
    assertThat(IR.string("a").matchesQualifiedName(qname("a"))).isFalse();
    assertThat(IR.regexp(IR.string("x")).matchesQualifiedName(qname("x"))).isFalse();
    assertThat(new Node(Token.INC, IR.name("x")).matchesQualifiedName(qname("x"))).isFalse();
  }

  public static Node qname(String name) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      return IR.name(name);
    }
    Node node;
    String nodeName = name.substring(0, endPos);
    if ("this".equals(nodeName)) {
      node = IR.thisNode();
    } else {
      node = IR.name(nodeName);
    }
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1
                     ? name.substring(startPos)
                     : name.substring(startPos, endPos));
      Node propNode = IR.string(part);
      node = IR.getprop(node, propNode);
    } while (endPos != -1);

    return node;
  }

  @Test
  public void testCloneAnnontations() {
    Node n = getVarRef("a");
    n.setLength(1);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node nodeClone = n.cloneNode();
    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
    assertThat(nodeClone.getLength()).isEqualTo(1);
  }

  @Test
  public void testSharedProps1() {
    Node n = getCall("A");
    n.setSideEffectFlags(5);
    Node m = new Node(Token.TRUE);
    m.clonePropsFrom(n);
    assertThat(n.getPropListHeadForTesting()).isEqualTo(m.getPropListHeadForTesting());
    assertThat(n.getSideEffectFlags()).isEqualTo(5);
    assertThat(m.getSideEffectFlags()).isEqualTo(5);
  }

  @Test
  public void testSharedProps2() {
    Node n = getCall("A");
    n.setSideEffectFlags(5);
    Node m = getCall("B");
    m.clonePropsFrom(n);

    n.setSideEffectFlags(6);
    assertThat(n.getSideEffectFlags()).isEqualTo(6);
    assertThat(m.getSideEffectFlags()).isEqualTo(5);
    assertThat(m.getPropListHeadForTesting() == n.getPropListHeadForTesting()).isFalse();

    m.setSideEffectFlags(7);
    assertThat(n.getSideEffectFlags()).isEqualTo(6);
    assertThat(m.getSideEffectFlags()).isEqualTo(7);
  }

  @Test
  public void testSharedProps3() {
    Node n = getCall("A");
    n.setSideEffectFlags(2);
    n.putBooleanProp(Node.INCRDECR_PROP, true);
    Node m = new Node(Token.TRUE);
    m.clonePropsFrom(n);
    n.setSideEffectFlags(4);

    assertThat(n.getSideEffectFlags()).isEqualTo(4);
    assertThat(m.getSideEffectFlags()).isEqualTo(2);
  }

  @Test
  public void testBooleanProp() {
    Node n = getVarRef("a");

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();

    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNotNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertThat(n.lookupProperty(Node.IS_CONSTANT_NAME)).isNull();
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isFalse();
  }

  // Verify that annotations on cloned nodes are properly handled.
  @Test
  public void testCloneAnnontations2() {
    Node n = getVarRef("a");
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node nodeClone = n.cloneNode();
    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    assertThat(n.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    assertThat(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testGetIndexOfChild() {
    Node assign = getAssignExpr("b", "c");
    assertThat(assign.getChildCount()).isEqualTo(2);

    Node firstChild = assign.getFirstChild();
    Node secondChild = firstChild.getNext();
    assertThat(secondChild).isNotNull();

    assertThat(assign.getIndexOfChild(firstChild)).isEqualTo(0);
    assertThat(assign.getIndexOfChild(secondChild)).isEqualTo(1);
    assertThat(assign.getIndexOfChild(assign)).isEqualTo(-1);
  }

  @Test
  public void testUseSourceInfoIfMissingFrom() {
    Node assign = getAssignExpr("b", "c");
    assign.setSourceEncodedPosition(99);
    assign.setSourceFileForTesting("foo.js");

    Node lhs = assign.getFirstChild();
    lhs.useSourceInfoIfMissingFrom(assign);
    assertThat(lhs.getSourcePosition()).isEqualTo(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");

    assign.setSourceEncodedPosition(101);
    assign.setSourceFileForTesting("bar.js");
    lhs.useSourceInfoIfMissingFrom(assign);
    assertThat(lhs.getSourcePosition()).isEqualTo(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");
  }

  @Test
  public void testUseSourceInfoFrom() {
    Node assign = getAssignExpr("b", "c");
    assign.setSourceEncodedPosition(99);
    assign.setSourceFileForTesting("foo.js");

    Node lhs = assign.getFirstChild();
    lhs.useSourceInfoFrom(assign);
    assertThat(lhs.getSourcePosition()).isEqualTo(99);
    assertThat(lhs.getSourceFileName()).isEqualTo("foo.js");

    assign.setSourceEncodedPosition(101);
    assign.setSourceFileForTesting("bar.js");
    lhs.useSourceInfoFrom(assign);
    assertThat(lhs.getSourcePosition()).isEqualTo(101);
    assertThat(lhs.getSourceFileName()).isEqualTo("bar.js");
  }

  @Test
  public void testInvalidSourceOffset() {
    Node string = Node.newString("a");

    string.setSourceEncodedPosition(-1);
    assertThat(string.getSourceOffset()).isLessThan(0);

    string.setSourceFileForTesting("foo.js");
    assertThat(string.getSourceOffset()).isLessThan(0);
  }

  @Test
  public void testQualifiedName() {
    assertThat(IR.name("").getQualifiedName()).isNull();
    assertThat(IR.name("a").getQualifiedName()).isEqualTo("a");
    assertThat(IR.getprop(IR.name("a"), IR.string("b")).getQualifiedName()).isEqualTo("a.b");
    assertThat(IR.getprop(IR.thisNode(), IR.string("b")).getQualifiedName()).isEqualTo("this.b");
    assertThat(IR.getprop(IR.call(IR.name("a")), IR.string("b")).getQualifiedName()).isNull();
  }

  @Test
  public void testJSDocInfoClone() {
    Node original = IR.var(IR.name("varName"));
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordType(new JSTypeExpression(IR.name("TypeName"), "blah"));
    JSDocInfo info = builder.build();
    original.getFirstChild().setJSDocInfo(info);

    // By default the JSDocInfo and JSTypeExpression objects are not cloned
    Node clone = original.cloneTree();
    assertThat(clone.getFirstChild().getJSDocInfo())
        .isSameAs(original.getFirstChild().getJSDocInfo());
    assertThat(clone.getFirstChild().getJSDocInfo().getType())
        .isSameAs(original.getFirstChild().getJSDocInfo().getType());
    assertThat(clone.getFirstChild().getJSDocInfo().getType().getRoot())
        .isSameAs(original.getFirstChild().getJSDocInfo().getType().getRoot());

    // If requested the JSDocInfo and JSTypeExpression objects are cloned.
    // This is required because compiler classes are modifying the type expressions in place
    clone = original.cloneTree(true);
    assertThat(clone.getFirstChild().getJSDocInfo())
        .isNotSameAs(original.getFirstChild().getJSDocInfo());
    assertThat(clone.getFirstChild().getJSDocInfo().getType())
        .isNotSameAs(original.getFirstChild().getJSDocInfo().getType());
    assertThat(clone.getFirstChild().getJSDocInfo().getType().getRoot())
        .isNotSameAs(original.getFirstChild().getJSDocInfo().getType().getRoot());
  }

  @Test
  public void testAddChildToFrontWithSingleNode() {
    Node root = new Node(Token.SCRIPT);
    Node nodeToAdd = new Node(Token.SCRIPT);

    root.addChildToFront(nodeToAdd);

    assertThat(nodeToAdd.parent).isEqualTo(root);
    assertThat(nodeToAdd).isEqualTo(root.getFirstChild());
    assertThat(nodeToAdd).isEqualTo(root.getLastChild());
    assertThat(nodeToAdd).isEqualTo(nodeToAdd.previous);
    assertThat(nodeToAdd.next).isNull();
  }

  @Test
  public void testAddChildToFrontWithLargerTree() {
    Node left = Node.newString("left");
    Node m1 = Node.newString("m1");
    Node m2 = Node.newString("m2");
    Node right = Node.newString("right");
    Node root = new Node(Token.SCRIPT, left, m1, m2, right);
    Node nodeToAdd = new Node(Token.SCRIPT);

    root.addChildToFront(nodeToAdd);

    assertThat(nodeToAdd.parent).isEqualTo(root);
    assertThat(nodeToAdd).isEqualTo(root.getFirstChild());
    assertThat(right).isEqualTo(root.getLastChild());
    assertThat(root.getLastChild()).isEqualTo(nodeToAdd.previous);
    assertThat(nodeToAdd.next).isEqualTo(left);
    assertThat(left.previous).isEqualTo(nodeToAdd);
  }

  @Test
  public void testDetach1() {
    Node left = Node.newString("left");
    Node mid = Node.newString("mid");
    Node right = Node.newString("right");
    Node root = new Node(Token.SCRIPT, left, mid, right);

    assertThat(mid.parent).isEqualTo(root);
    assertThat(mid.previous).isEqualTo(left);
    assertThat(mid.next).isEqualTo(right);

    mid.detach();

    assertThat(mid.parent).isNull();
    assertThat(mid.previous).isNull();
    assertThat(mid.next).isNull();

    assertThat(right.getPrevious()).isEqualTo(left);
    assertThat(left.getNext()).isEqualTo(right);
  }

  private static Node getVarRef(String name) {
    return Node.newString(Token.NAME, name);
  }

  private static Node getAssignExpr(String name1, String name2) {
    return new Node(Token.ASSIGN, getVarRef(name1), getVarRef(name2));
  }

  private static Node getCall(String name1) {
    return new Node(Token.CALL, getVarRef(name1));
  }
}
