/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test class for the default {@link CodingConvention}. */
@RunWith(JUnit4.class)
public final class DefaultCodingConventionTest extends TestCase {
  private final CodingConvention conv = CodingConventions.getDefault();

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
    assertFalse(conv.isOptionalParameter(optArgs.getFirstChild()));
    assertFalse(conv.isOptionalParameter(optArgs.getLastChild()));

    assertTrue(conv.isVarArgsParameter(rest.getLastChild()));
    assertFalse(conv.isOptionalParameter(rest.getFirstChild()));
  }

  @Test
  public void testInlineName() {
    assertFalse(conv.isConstant("a"));
    assertFalse(conv.isConstant("XYZ123_"));
    assertFalse(conv.isConstant("ABC"));
    assertFalse(conv.isConstant("ABCdef"));
    assertFalse(conv.isConstant("aBC"));
    assertFalse(conv.isConstant("A"));
    assertFalse(conv.isConstant("_XYZ123"));
    assertFalse(conv.isConstant("a$b$XYZ123_"));
    assertFalse(conv.isConstant("a$b$ABC_DEF"));
    assertFalse(conv.isConstant("a$b$A"));
    assertFalse(conv.isConstant("a$b$a"));
    assertFalse(conv.isConstant("a$b$ABCdef"));
    assertFalse(conv.isConstant("a$b$aBC"));
    assertFalse(conv.isConstant("a$b$"));
    assertFalse(conv.isConstant("$"));
  }

  @Test
  public void testExportedName() {
    assertFalse(conv.isExported("_a"));
    assertFalse(conv.isExported("_a_"));
    assertFalse(conv.isExported("a"));

    assertFalse(conv.isExported("$super", false));
    assertTrue(conv.isExported("$super", true));
    assertTrue(conv.isExported("$super"));
  }

  @Test
  public void testPrivateName() {
    assertFalse(conv.isPrivate("a_"));
    assertFalse(conv.isPrivate("a"));
    assertFalse(conv.isPrivate("_a_"));
  }

  @Test
  public void testEnumKey() {
    assertTrue(conv.isValidEnumKey("A"));
    assertTrue(conv.isValidEnumKey("123"));
    assertTrue(conv.isValidEnumKey("FOO_BAR"));

    assertTrue(conv.isValidEnumKey("a"));
    assertTrue(conv.isValidEnumKey("someKeyInCamelCase"));
    assertTrue(conv.isValidEnumKey("_FOO_BAR"));
  }

  @Test
  public void testInheritanceDetection1() {
    assertNotClassDefining("goog.foo(A, B);");
  }

  @Test
  public void testInheritanceDetection2() {
    assertNotClassDefining("goog.inherits(A, B);");
  }

  @Test
  public void testInheritanceDetection3() {
    assertNotClassDefining("A.inherits(B);");
  }

  @Test
  public void testInheritanceDetection4() {
    assertNotClassDefining("goog.inherits(goog.A, goog.B);");
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
    assertNotClassDefining("goog.mixin(A.prototype, B.prototype);");
  }

  @Test
  public void testInheritanceDetection11() {
    assertDefinesClasses("$jscomp.inherits(A, B)", "A", "B");
  }

  @Test
  public void testInheritanceDetection12() {
    assertDefinesClasses("$jscomp$inherits(A, B)", "A", "B");
  }

  @Test
  public void testInheritanceDetectionPostCollapseProperties() {
    assertNotClassDefining("goog$inherits(A, B);");
    assertNotClassDefining("goog$inherits(A);");
  }

  @Test
  public void testFunctionBind() {
    assertNotFunctionBind("goog.bind(f)");
    assertNotFunctionBind("goog$bind(f)");
    assertNotFunctionBind("goog.partial(f)");
    assertNotFunctionBind("goog$partial(f)");

    assertFunctionBind("(function(){}).bind()");
    assertFunctionBind("(function(){}).bind(obj)");
    assertFunctionBind("(function(){}).bind(obj, p1)");

    assertNotFunctionBind("Function.prototype.bind.call()");
    assertFunctionBind("Function.prototype.bind.call(obj)");
    assertFunctionBind("Function.prototype.bind.call(obj, p1)");
  }

  @Test
  public void testPackageNames() {
    assertPackageName("foo.js", "");
    assertPackageName("foo/bar.js", "foo");
    assertPackageName("foo/bar/baz.js", "foo/bar");
    assertPackageName("foo/bar/baz/quux.js", "foo/bar/baz");
    assertPackageName("foo/test/bar.js", "foo/test");
    assertPackageName("foo/testxyz/bar.js", "foo/testxyz");
  }

  private void assertPackageName(String filename, String expectedPackageName) {
    StaticSourceFile sourceFile = SourceFile.fromCode(filename, "");
    assertEquals(expectedPackageName, conv.getPackageName(sourceFile));
  }

  private void assertFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertNotNull(conv.describeFunctionBind(n.getFirstChild()));
  }

  private void assertNotFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertNull(conv.describeFunctionBind(n.getFirstChild()));
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
    assertThat(classes).isNotNull();
    assertThat(classes.subclassName).isEqualTo(subclassName);
    assertThat(classes.superclassName).isEqualTo(superclassName);
  }

  private Node parseTestCode(String code) {
    Compiler compiler = new Compiler();
    return compiler.parseTestCode(code).getFirstChild();
  }
}
