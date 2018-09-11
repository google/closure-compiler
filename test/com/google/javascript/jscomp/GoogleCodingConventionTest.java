/*
 * Copyright 2007 The Closure Compiler Authors.
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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test class for {@link GoogleCodingConvention}. */
@RunWith(JUnit4.class)
public final class GoogleCodingConventionTest extends TestCase {
  private final GoogleCodingConvention conv = new GoogleCodingConvention();

  @Test
  public void testVarAndOptionalParams() {
    Node args = IR.paramList(
        IR.name("a"),
        IR.name("b"));
    Node optArgs = IR.paramList(
        IR.name("opt_a"),
        IR.name("opt_b"));
    Node rest = IR.paramList(
        IR.rest(IR.name("more")));

    assertFalse(conv.isVarArgsParameter(args.getFirstChild()));
    assertFalse(conv.isVarArgsParameter(args.getLastChild()));
    assertFalse(conv.isVarArgsParameter(optArgs.getFirstChild()));
    assertFalse(conv.isVarArgsParameter(optArgs.getLastChild()));

    assertFalse(conv.isOptionalParameter(args.getFirstChild()));
    assertFalse(conv.isOptionalParameter(args.getLastChild()));
    assertTrue(conv.isOptionalParameter(optArgs.getFirstChild()));
    assertTrue(conv.isOptionalParameter(optArgs.getLastChild()));

    assertTrue(conv.isVarArgsParameter(rest.getLastChild()));
    assertFalse(conv.isOptionalParameter(rest.getFirstChild()));
  }

  @Test
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

  @Test
  public void testExportedName() {
    assertTrue(conv.isExported("_a"));
    assertTrue(conv.isExported("_a_"));
    assertFalse(conv.isExported("a"));

    assertFalse(conv.isExported("$super", false));
    assertTrue(conv.isExported("$super", true));
    assertTrue(conv.isExported("$super"));
  }

  @Test
  public void testPrivateName() {
    assertTrue(conv.isPrivate("a_"));
    assertFalse(conv.isPrivate("a"));
    assertFalse(conv.isPrivate("_a_"));
  }

  @Test
  public void testEnumKey() {
    assertTrue(conv.isValidEnumKey("A"));
    assertTrue(conv.isValidEnumKey("123"));
    assertTrue(conv.isValidEnumKey("FOO_BAR"));

    assertFalse(conv.isValidEnumKey("a"));
    assertFalse(conv.isValidEnumKey("someKeyInCamelCase"));
    assertFalse(conv.isValidEnumKey("_FOO_BAR"));
  }

  @Test
  public void testInheritanceDetection1() {
    assertNotClassDefining("goog.foo(A, B);");
  }

  @Test
  public void testInheritanceDetection2() {
    assertDefinesClasses("goog.inherits(A, B);", "A", "B");
  }

  @Test
  public void testInheritanceDetection3() {
    assertNotClassDefining("A.inherits(B);");
  }

  @Test
  public void testInheritanceDetection4() {
    assertDefinesClasses("goog.inherits(goog.A, goog.B);", "goog.A", "goog.B");
  }

  @Test
  public void testInheritanceDetection5() {
    assertNotClassDefining("goog.A.inherits(goog.B);");
  }

  @Test
  public void testInheritanceDetection6() {
    assertNotClassDefining("A.inherits(this.B);");
  }

  @Test
  public void testInheritanceDetection7() {
    assertNotClassDefining("this.A.inherits(B);");
  }

  @Test
  public void testInheritanceDetection8() {
    assertNotClassDefining("goog.inherits(A, B, C);");
  }

  @Test
  public void testInheritanceDetection9() {
    assertNotClassDefining("A.mixin(B.prototype);");
  }

  @Test
  public void testInheritanceDetection10() {
    assertDefinesClasses("goog.mixin(A.prototype, B.prototype);",
        "A", "B");
  }

  @Test
  public void testInheritanceDetectionPostCollapseProperties() {
    assertDefinesClasses("goog$inherits(A, B);", "A", "B");
    assertNotClassDefining("goog$inherits(A);");
  }

  @Test
  public void testPackageNames() {
    assertPackageName("foo.js", "");
    assertPackageName("foo/bar.js", "foo");
    assertPackageName("foo/bar/baz.js", "foo/bar");
    assertPackageName("foo/bar/baz/quux.js", "foo/bar/baz");
    assertPackageName("foo/test/bar.js", "foo");
    assertPackageName("foo/tests/bar.js", "foo");
    assertPackageName("foo/testing/bar.js", "foo");
    assertPackageName("foo/jstest/bar.js", "foo/jstest");
    assertPackageName("foo/bar/test/baz.js", "foo/bar");
    assertPackageName("foo/bar/tests/baz.js", "foo/bar");
    assertPackageName("foo/bar/testing/baz.js", "foo/bar");
    assertPackageName("foo/bar/testament/baz.js", "foo/bar/testament");
    assertPackageName("foo/test/bar/baz.js", "foo/test/bar");
    assertPackageName("foo/bar/baz/test/quux.js", "foo/bar/baz");
    assertPackageName("foo/bar/baz/tests/quux.js", "foo/bar/baz");
    assertPackageName("foo/bar/baz/testing/quux.js", "foo/bar/baz");
    assertPackageName("foo/bar/baz/unittests/quux.js", "foo/bar/baz/unittests");
    assertPackageName("foo/bar/test/baz/quux.js", "foo/bar/test/baz");
    assertPackageName("foo/test/bar/baz/quux.js", "foo/test/bar/baz");
    assertPackageName("bazel-out/host/genfiles/bar/baz/quux.js", "bar/baz");
    assertPackageName("bazel-out/host/genfiles/foo/test/bar.js", "foo");
    assertPackageName("bazel-out/host/bin/bar/baz/quux.js", "bar/baz");
    assertPackageName("bazel-out/host/bin/foo/test/bar.js", "foo");
  }

  private void assertPackageName(String filename, String expectedPackageName) {
    StaticSourceFile sourceFile = SourceFile.fromCode(filename, "");
    assertEquals(expectedPackageName, conv.getPackageName(sourceFile));
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
