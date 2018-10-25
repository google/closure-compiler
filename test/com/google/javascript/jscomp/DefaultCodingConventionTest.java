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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test class for the default {@link CodingConvention}. */
@RunWith(JUnit4.class)
public final class DefaultCodingConventionTest {
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

    assertThat(conv.isVarArgsParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(args.getLastChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getLastChild())).isFalse();

    assertThat(conv.isOptionalParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isOptionalParameter(args.getLastChild())).isFalse();
    assertThat(conv.isOptionalParameter(optArgs.getFirstChild())).isFalse();
    assertThat(conv.isOptionalParameter(optArgs.getLastChild())).isFalse();

    assertThat(conv.isVarArgsParameter(rest.getLastChild())).isTrue();
    assertThat(conv.isOptionalParameter(rest.getFirstChild())).isFalse();
  }

  @Test
  public void testInlineName() {
    assertThat(conv.isConstant("a")).isFalse();
    assertThat(conv.isConstant("XYZ123_")).isFalse();
    assertThat(conv.isConstant("ABC")).isFalse();
    assertThat(conv.isConstant("ABCdef")).isFalse();
    assertThat(conv.isConstant("aBC")).isFalse();
    assertThat(conv.isConstant("A")).isFalse();
    assertThat(conv.isConstant("_XYZ123")).isFalse();
    assertThat(conv.isConstant("a$b$XYZ123_")).isFalse();
    assertThat(conv.isConstant("a$b$ABC_DEF")).isFalse();
    assertThat(conv.isConstant("a$b$A")).isFalse();
    assertThat(conv.isConstant("a$b$a")).isFalse();
    assertThat(conv.isConstant("a$b$ABCdef")).isFalse();
    assertThat(conv.isConstant("a$b$aBC")).isFalse();
    assertThat(conv.isConstant("a$b$")).isFalse();
    assertThat(conv.isConstant("$")).isFalse();
  }

  @Test
  public void testExportedName() {
    assertThat(conv.isExported("_a")).isFalse();
    assertThat(conv.isExported("_a_")).isFalse();
    assertThat(conv.isExported("a")).isFalse();

    assertThat(conv.isExported("$super", false)).isFalse();
    assertThat(conv.isExported("$super", true)).isTrue();
    assertThat(conv.isExported("$super")).isTrue();
  }

  @Test
  public void testPrivateName() {
    assertThat(conv.isPrivate("a_")).isFalse();
    assertThat(conv.isPrivate("a")).isFalse();
    assertThat(conv.isPrivate("_a_")).isFalse();
  }

  @Test
  public void testEnumKey() {
    assertThat(conv.isValidEnumKey("A")).isTrue();
    assertThat(conv.isValidEnumKey("123")).isTrue();
    assertThat(conv.isValidEnumKey("FOO_BAR")).isTrue();

    assertThat(conv.isValidEnumKey("a")).isTrue();
    assertThat(conv.isValidEnumKey("someKeyInCamelCase")).isTrue();
    assertThat(conv.isValidEnumKey("_FOO_BAR")).isTrue();
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
    assertThat(conv.getPackageName(sourceFile)).isEqualTo(expectedPackageName);
  }

  private void assertFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.describeFunctionBind(n.getFirstChild())).isNotNull();
  }

  private void assertNotFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.describeFunctionBind(n.getFirstChild())).isNull();
  }

  private void assertNotClassDefining(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.getClassesDefinedByCall(n.getFirstChild())).isNull();
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
