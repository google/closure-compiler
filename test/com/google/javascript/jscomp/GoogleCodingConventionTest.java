/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/**
 * Test class for {@link GoogleCodingConvention}.
 */
public class GoogleCodingConventionTest extends TestCase {
  private GoogleCodingConvention conv = new GoogleCodingConvention();


  public void testVarAndOptionalParams() {
    Node args = new Node(Token.LP,
        Node.newString(Token.NAME, "a"),
        Node.newString(Token.NAME, "b"));
    assertFalse(conv.isVarArgsParameter(args.getFirstChild(), "a"));
    assertFalse(conv.isVarArgsParameter(args.getLastChild(), "b"));
    assertFalse(conv.isOptionalParameter("a"));
    assertTrue(conv.isOptionalParameter("opt_a"));
  }

  public void testInlineName() {
    assertFalse(conv.isConstant("a"));
    assertTrue(conv.isConstant("XYZ123_"));
    assertTrue(conv.isConstant("ABC"));
    assertFalse(conv.isConstant("ABCdef"));
    assertFalse(conv.isConstant("aBC"));
    assertFalse(conv.isConstant("A"));
    assertFalse(conv.isConstant("_XYZ123"));
    assertTrue(conv.isConstant("a$b$XYZ123_"));
    assertTrue(conv.isConstant("a$b$ABC_DEF"));
    assertTrue(conv.isConstant("a$b$A"));
    assertFalse(conv.isConstant("a$b$a"));
    assertFalse(conv.isConstant("a$b$ABCdef"));
    assertFalse(conv.isConstant("a$b$aBC"));
    assertFalse(conv.isConstant("a$b$"));
    assertFalse(conv.isConstant("$"));
  }

  public void testExportedName() {
    assertTrue(conv.isExported("_a"));
    assertTrue(conv.isExported("_a_"));
    assertFalse(conv.isExported("a"));
  }

  public void testPrivateName() {
    assertTrue(conv.isPrivate("a_"));
    assertFalse(conv.isPrivate("a"));
    assertFalse(conv.isPrivate("_a_"));
  }

  public void testEnumKey() {
    assertTrue(conv.isValidEnumKey("A"));
    assertTrue(conv.isValidEnumKey("123"));
    assertTrue(conv.isValidEnumKey("FOO_BAR"));

    assertFalse(conv.isValidEnumKey("a"));
    assertFalse(conv.isValidEnumKey("someKeyInCamelCase"));
    assertFalse(conv.isValidEnumKey("_FOO_BAR"));
  }

  public void testInheritanceDetection1() {
    assertNotClassDefining("goog.foo(A, B);");
  }

  public void testInheritanceDetection2() {
    assertDefinesClasses("goog.inherits(A, B);", "A", "B");
  }

  public void testInheritanceDetection3() {
    assertDefinesClasses("A.inherits(B);", "A", "B");
  }

  public void testInheritanceDetection4() {
    assertDefinesClasses("goog.inherits(goog.A, goog.B);", "goog.A", "goog.B");
  }

  public void testInheritanceDetection5() {
    assertDefinesClasses("goog.A.inherits(goog.B);", "goog.A", "goog.B");
  }

  public void testInheritanceDetection6() {
    assertNotClassDefining("A.inherits(this.B);");
  }

  public void testInheritanceDetection7() {
    assertNotClassDefining("this.A.inherits(B);");
  }

  public void testInheritanceDetection8() {
    assertNotClassDefining("goog.inherits(A, B, C);");
  }

  public void testInheritanceDetection9() {
    assertDefinesClasses("A.mixin(B.prototype);",
        "A", "B");
  }

  public void testInheritanceDetection10() {
    assertDefinesClasses("goog.mixin(A.prototype, B.prototype);",
        "A", "B");
  }

  public void testInheritanceDetectionPostCollapseProperties() {
    assertDefinesClasses("goog$inherits(A, B);", "A", "B");
    assertNotClassDefining("goog$inherits(A);");
  }

  private void assertNotClassDefining(String code) {
    Node n = parseTestCode(code);
    assertNull(conv.getClassesDefinedByCall(n.getFirstChild()));
  }

  private void assertDefinesClasses(String code, String subclassName,
      String superclassName) {
    Node n = parseTestCode(code);
    SubclassRelationship classes =
        conv.getClassesDefinedByCall(n.getFirstChild());
    assertNotNull(classes);
    assertEquals(subclassName, classes.subclassName);
    assertEquals(superclassName, classes.superclassName);
  }

  private Node parseTestCode(String code) {
    Compiler compiler = new Compiler();
    return compiler.parseTestCode(code).getFirstChild();
  }
}
