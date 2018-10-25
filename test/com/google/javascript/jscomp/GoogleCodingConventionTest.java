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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test class for {@link GoogleCodingConvention}. */
@RunWith(JUnit4.class)
public final class GoogleCodingConventionTest {
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

    assertThat(conv.isVarArgsParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(args.getLastChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getLastChild())).isFalse();

    assertThat(conv.isOptionalParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isOptionalParameter(args.getLastChild())).isFalse();
    assertThat(conv.isOptionalParameter(optArgs.getFirstChild())).isTrue();
    assertThat(conv.isOptionalParameter(optArgs.getLastChild())).isTrue();

    assertThat(conv.isVarArgsParameter(rest.getLastChild())).isTrue();
    assertThat(conv.isOptionalParameter(rest.getFirstChild())).isFalse();
  }

  @Test
  public void testInlineName() {
    assertThat(conv.isConstant("a")).isFalse();
    assertThat(conv.isConstant("XYZ123_")).isTrue();
    assertThat(conv.isConstant("ABC")).isTrue();
    assertThat(conv.isConstant("ABCdef")).isFalse();
    assertThat(conv.isConstant("aBC")).isFalse();
    assertThat(conv.isConstant("A")).isFalse();
    assertThat(conv.isConstant("_XYZ123")).isFalse();
    assertThat(conv.isConstant("a$b$XYZ123_")).isTrue();
    assertThat(conv.isConstant("a$b$ABC_DEF")).isTrue();
    assertThat(conv.isConstant("a$b$A")).isTrue();
    assertThat(conv.isConstant("a$b$a")).isFalse();
    assertThat(conv.isConstant("a$b$ABCdef")).isFalse();
    assertThat(conv.isConstant("a$b$aBC")).isFalse();
    assertThat(conv.isConstant("a$b$")).isFalse();
    assertThat(conv.isConstant("$")).isFalse();
  }

  @Test
  public void testExportedName() {
    assertThat(conv.isExported("_a")).isTrue();
    assertThat(conv.isExported("_a_")).isTrue();
    assertThat(conv.isExported("a")).isFalse();

    assertThat(conv.isExported("$super", false)).isFalse();
    assertThat(conv.isExported("$super", true)).isTrue();
    assertThat(conv.isExported("$super")).isTrue();
  }

  @Test
  public void testPrivateName() {
    assertThat(conv.isPrivate("a_")).isTrue();
    assertThat(conv.isPrivate("a")).isFalse();
    assertThat(conv.isPrivate("_a_")).isFalse();
  }

  @Test
  public void testEnumKey() {
    assertThat(conv.isValidEnumKey("A")).isTrue();
    assertThat(conv.isValidEnumKey("123")).isTrue();
    assertThat(conv.isValidEnumKey("FOO_BAR")).isTrue();

    assertThat(conv.isValidEnumKey("a")).isFalse();
    assertThat(conv.isValidEnumKey("someKeyInCamelCase")).isFalse();
    assertThat(conv.isValidEnumKey("_FOO_BAR")).isFalse();
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
    assertThat(conv.getPackageName(sourceFile)).isEqualTo(expectedPackageName);
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
