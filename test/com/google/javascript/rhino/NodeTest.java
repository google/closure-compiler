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

import com.google.javascript.rhino.Node.NodeMismatch;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.testing.TestErrorReporter;

import junit.framework.TestCase;

public class NodeTest extends TestCase {
  public void testMergeExtractNormal() throws Exception {
    testMergeExtract(5, 6);
    testMergeExtract(456, 3423);
    testMergeExtract(0, 0);
  }

  public void testMergeExtractErroneous() throws Exception {
    assertEquals(-1, Node.mergeLineCharNo(-5, 90));
    assertEquals(-1, Node.mergeLineCharNo(0, -1));
    assertEquals(-1, Node.extractLineno(-1));
    assertEquals(-1, Node.extractCharno(-1));
  }

  public void testMergeOverflowGraciously() throws Exception {
    int linecharno = Node.mergeLineCharNo(89, 4096);
    assertEquals(89, Node.extractLineno(linecharno));
    assertEquals(4095, Node.extractCharno(linecharno));
  }

  public void testCheckTreeEqualsImplSame() {
    Node node1 = new Node(1, new Node(2));
    Node node2 = new Node(1, new Node(2));
    assertEquals(null, node1.checkTreeEqualsImpl(node2));
  }

  public void testCheckTreeEqualsImplDifferentType() {
    Node node1 = new Node(1, new Node(2));
    Node node2 = new Node(2, new Node(2));
    assertEquals(new NodeMismatch(node1, node2),
        node1.checkTreeEqualsImpl(node2));
  }

  public void testCheckTreeEqualsImplDifferentChildCount() {
    Node node1 = new Node(1, new Node(2));
    Node node2 = new Node(1);
    assertEquals(new NodeMismatch(node1, node2),
        node1.checkTreeEqualsImpl(node2));
  }

  public void testCheckTreeEqualsImplDifferentChild() {
    Node child1 = new Node(1);
    Node child2 = new Node(2);
    Node node1 = new Node(1, child1);
    Node node2 = new Node(1, child2);
    assertEquals(new NodeMismatch(child1, child2),
        node1.checkTreeEqualsImpl(node2));
  }

  public void testCheckTreeEqualsSame() {
    Node node1 = new Node(1);
    assertEquals(null, node1.checkTreeEquals(node1));
  }

  public void testCheckTreeEqualsStringDifferent() {
    Node node1 = new Node(1);
    Node node2 = new Node(2);
    assertNotNull(node1.checkTreeEquals(node2));
  }

  public void testCheckTreeEqualsBooleanSame() {
    Node node1 = new Node(1);
    assertEquals(true, node1.checkTreeEqualsSilent(node1));
  }

  public void testCheckTreeEqualsBooleanDifferent() {
    Node node1 = new Node(1);
    Node node2 = new Node(2);
    assertEquals(false, node1.checkTreeEqualsSilent(node2));
  }

  public void testCheckTreeEqualsImplDifferentIncProp() {
    Node node1 = new Node(Token.INC);
    node1.putIntProp(Node.INCRDECR_PROP, 1);
    Node node2 = new Node(Token.INC);
    assertNotNull(node1.checkTreeEqualsImpl(node2));
  }

  public void testCheckTreeTypeAwareEqualsSame() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    assertTrue(node1.checkTreeTypeAwareEqualsSilent(node2));
  }

  public void testCheckTreeTypeAwareEqualsSameNull() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    Node node2 = Node.newString(Token.NAME, "f");
    assertTrue(node1.checkTreeTypeAwareEqualsSilent(node2));
  }

  public void testCheckTreeTypeAwareEqualsDifferent() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    node2.setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));
    assertFalse(node1.checkTreeTypeAwareEqualsSilent(node2));
  }

  public void testCheckTreeTypeAwareEqualsDifferentNull() {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    JSTypeRegistry registry = new JSTypeRegistry(testErrorReporter);
    Node node1 = Node.newString(Token.NAME, "f");
    node1.setJSType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    Node node2 = Node.newString(Token.NAME, "f");
    assertFalse(node1.checkTreeTypeAwareEqualsSilent(node2));
  }

  public void testVarArgs1() {
    assertFalse(new Node(1).isVarArgs());
  }

  public void testVarArgs2() {
    Node n = new Node(1);
    n.setVarArgs(false);
    assertFalse(n.isVarArgs());
  }

  public void testVarArgs3() {
    Node n = new Node(1);
    n.setVarArgs(true);
    assertTrue(n.isVarArgs());
  }

  private void testMergeExtract(int lineno, int charno) {
    int linecharno = Node.mergeLineCharNo(lineno, charno);
    assertEquals(lineno, Node.extractLineno(linecharno));
    assertEquals(charno, Node.extractCharno(linecharno));
  }

  public void testFileLevelJSDocAppender() {
    Node n = new Node(1);
    Node.FileLevelJsDocBuilder builder = n.getJsDocBuilderForNode();
    builder.append("foo");
    builder.append("bar");
    assertEquals("foobar", n.getJSDocInfo().getLicense());
  }

  public void testIsQualifiedName() {
    assertTrue(getNode("a").isQualifiedName());
    assertTrue(getNode("$").isQualifiedName());
    assertTrue(getNode("_").isQualifiedName());
    assertTrue(getNode("a.b").isQualifiedName());
    assertTrue(getNode("a_b.cccccc$d4.x.y.zA$").isQualifiedName());
    assertTrue(getNode("this.foo").isQualifiedName());
    assertFalse(getNode("0").isQualifiedName());
    assertFalse(getNode("[]").isQualifiedName());
    assertFalse(getNode("{}").isQualifiedName());
    assertFalse(getNode("''").isQualifiedName());
    assertFalse(getNode("a[b]").isQualifiedName());
    assertFalse(getNode("a[b].c").isQualifiedName());
    assertFalse(getNode("c.a[b]").isQualifiedName());
    assertFalse(getNode("a()").isQualifiedName());
    assertFalse(getNode("a().b").isQualifiedName());
    assertFalse(getNode("b.a()").isQualifiedName());
    assertFalse(getNode("'a'").isQualifiedName());
    assertFalse(getNode("/x/").isQualifiedName());
    assertFalse(getNode("++x").isQualifiedName());
  }

  public void testCloneAnnontations() {
    Node n = getNode("a");
    assertFalse(n.getBooleanProp(Node.IS_CONSTANT_NAME));
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertTrue(n.getBooleanProp(Node.IS_CONSTANT_NAME));

    Node nodeClone = n.cloneNode();
    assertTrue(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  public void testBooleanProp() {
    Node n = getNode("a");

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertNull(n.lookupProperty(Node.IS_CONSTANT_NAME));
    assertFalse(n.getBooleanProp(Node.IS_CONSTANT_NAME));

    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);

    assertNotNull(n.lookupProperty(Node.IS_CONSTANT_NAME));
    assertTrue(n.getBooleanProp(Node.IS_CONSTANT_NAME));

    n.putBooleanProp(Node.IS_CONSTANT_NAME, false);

    assertNull(n.lookupProperty(Node.IS_CONSTANT_NAME));
    assertFalse(n.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  // Verify that annotations on cloned nodes are properly handled.
  public void testCloneAnnontations2() {
    Node n = getNode("a");
    n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    n.putBooleanProp(Node.IS_DISPATCHER, true);
    assertTrue(n.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertTrue(n.getBooleanProp(Node.IS_DISPATCHER));

    Node nodeClone = n.cloneNode();
    assertTrue(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertTrue(nodeClone.getBooleanProp(Node.IS_DISPATCHER));

    n.putBooleanProp(Node.IS_DISPATCHER, false);
    assertTrue(n.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertFalse(n.getBooleanProp(Node.IS_DISPATCHER));

    assertTrue(nodeClone.getBooleanProp(Node.IS_CONSTANT_NAME));
    assertTrue(nodeClone.getBooleanProp(Node.IS_DISPATCHER));
  }

  private static Node getNode(String js) {
    Node root = parse("var a=(" + js + ");");
    Node expr = root.getFirstChild();
    Node var = expr.getFirstChild();
    return var.getFirstChild();
  }

  private static Node parse(String string) {
    CompilerEnvirons environment = new CompilerEnvirons();
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, null);
    environment.setErrorReporter(testErrorReporter);
    environment.setParseJSDoc(true);
    Parser p = new Parser(environment, testErrorReporter);
    return p.parse(string, null, 0);
  }
}
