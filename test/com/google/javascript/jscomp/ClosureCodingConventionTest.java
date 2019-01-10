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
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NominalTypeBuilderOti;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test class for {@link GoogleCodingConvention}. */
@RunWith(JUnit4.class)
public final class ClosureCodingConventionTest {
  private final ClosureCodingConvention conv = new ClosureCodingConvention();

  @Test
  public void testVarAndOptionalParams() {
    Node args = new Node(Token.PARAM_LIST,
        Node.newString(Token.NAME, "a"),
        Node.newString(Token.NAME, "b"));
    Node optArgs = new Node(Token.PARAM_LIST,
        Node.newString(Token.NAME, "opt_a"),
        Node.newString(Token.NAME, "opt_b"));

    assertThat(conv.isVarArgsParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(args.getLastChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getFirstChild())).isFalse();
    assertThat(conv.isVarArgsParameter(optArgs.getLastChild())).isFalse();

    assertThat(conv.isOptionalParameter(args.getFirstChild())).isFalse();
    assertThat(conv.isOptionalParameter(args.getLastChild())).isFalse();
    assertThat(conv.isOptionalParameter(optArgs.getFirstChild())).isFalse();
    assertThat(conv.isOptionalParameter(optArgs.getLastChild())).isFalse();
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
  public void testInheritanceDetection11() {
    assertNotClassDefining("A.mixin(B)");
  }

  @Test
  public void testInheritanceDetection12() {
    assertNotClassDefining("goog.mixin(A.prototype, B)");
  }

  @Test
  public void testInheritanceDetection13() {
    assertNotClassDefining("goog.mixin(A, B)");
  }

  @Test
  public void testInheritanceDetection14() {
    assertNotClassDefining("goog$mixin((function(){}).prototype)");
  }

  @Test
  public void testInheritanceDetection15() {
    assertDefinesClasses("$jscomp.inherits(A, B)", "A", "B");
  }

  @Test
  public void testInheritanceDetection16() {
    assertDefinesClasses("$jscomp$inherits(A, B)", "A", "B");
  }

  @Test
  public void testInheritanceDetectionPostCollapseProperties() {
    assertDefinesClasses("goog$inherits(A, B);", "A", "B");
    assertNotClassDefining("goog$inherits(A);");
  }

  @Test
  public void testObjectLiteralCast() {
    assertNotObjectLiteralCast("goog.reflect.object();");
    assertNotObjectLiteralCast("goog.reflect.object(A);");
    assertNotObjectLiteralCast("goog.reflect.object(1, {});");
    assertObjectLiteralCast("goog.reflect.object(A, {});");

    assertNotObjectLiteralCast("$jscomp.reflectObject();");
    assertNotObjectLiteralCast("$jscomp.reflectObject(A);");
    assertNotObjectLiteralCast("$jscomp.reflectObject(1, {});");
    assertObjectLiteralCast("$jscomp.reflectObject(A, {});");
  }

  @Test
  public void testFunctionBind() {
    assertNotFunctionBind("goog.bind()");  // invalid bind
    assertFunctionBind("goog.bind(f)");
    assertFunctionBind("goog.bind(f, obj)");
    assertFunctionBind("goog.bind(f, obj, p1)");

    assertNotFunctionBind("goog$bind()");  // invalid bind
    assertFunctionBind("goog$bind(f)");
    assertFunctionBind("goog$bind(f, obj)");
    assertFunctionBind("goog$bind(f, obj, p1)");

    assertNotFunctionBind("goog.partial()");  // invalid bind
    assertFunctionBind("goog.partial(f)");
    assertFunctionBind("goog.partial(f, obj)");
    assertFunctionBind("goog.partial(f, obj, p1)");

    assertNotFunctionBind("goog$partial()");  // invalid bind
    assertFunctionBind("goog$partial(f)");
    assertFunctionBind("goog$partial(f, obj)");
    assertFunctionBind("goog$partial(f, obj, p1)");

    assertFunctionBind("(function(){}).bind()");
    assertFunctionBind("(function(){}).bind(obj)");
    assertFunctionBind("(function(){}).bind(obj, p1)");

    assertNotFunctionBind("Function.prototype.bind.call()");
    assertFunctionBind("Function.prototype.bind.call(obj)");
    assertFunctionBind("Function.prototype.bind.call(obj, p1)");
  }

  @Test
  public void testRequire() {
    assertRequire("goog.require('foo')");
    assertNotRequire("goog.require(foo)");
    assertNotRequire("goog.require()");
    assertNotRequire("foo()");
  }

  @Test
  public void testApplySubclassRelationship() {
    JSTypeRegistry registry = new JSTypeRegistry(null);

    Node nodeA = new Node(Token.FUNCTION);
    FunctionType ctorA =
        registry.createConstructorType("A", nodeA, new Node(Token.PARAM_LIST), null, null, false);

    Node nodeB = new Node(Token.FUNCTION);
    FunctionType ctorB =
        registry.createConstructorType("B", nodeB, new Node(Token.PARAM_LIST), null, null, false);

    conv.applySubclassRelationship(
        new NominalTypeBuilderOti(ctorA, ctorA.getInstanceType()),
        new NominalTypeBuilderOti(ctorB, ctorB.getInstanceType()),
        SubclassType.INHERITS);

    assertThat(ctorB.getPrototype().hasOwnProperty("constructor")).isTrue();
    assertThat(ctorB.getPrototype().getPropertyNode("constructor")).isEqualTo(nodeB);

    assertThat(ctorB.hasOwnProperty("superClass_")).isTrue();
    assertThat(ctorB.getPropertyNode("superClass_")).isEqualTo(nodeB);
  }

  @Test
  public void testDescribeCachingCall() {
    assertCachingCall("goog.reflect.cache(obj, 10, function() {})");
    assertCachingCall("goog.reflect.cache(obj, 10, function() {}, function() {})");
    assertCachingCall("goog$reflect$cache(obj, 10, function() {})");
    assertCachingCall("goog$reflect$cache(obj, 10, function() {}, function() {})");
    assertNotCachingCall("goog.reflect.cache()");
    assertNotCachingCall("goog.reflect.cache(obj)");
    assertNotCachingCall("goog.reflect.cache(obj, 10)");
    assertNotCachingCall("foo.cache(obj, 10, function() {}, function() {})");
  }

  private void assertFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.describeFunctionBind(n.getFirstChild())).isNotNull();
  }

  private void assertNotFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.describeFunctionBind(n.getFirstChild())).isNull();
  }

  private void assertRequire(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.extractClassNameIfRequire(n.getFirstChild(), n)).isNotNull();
  }

  private void assertNotRequire(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.extractClassNameIfRequire(n.getFirstChild(), n)).isNull();
  }

  private void assertNotObjectLiteralCast(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.getObjectLiteralCast(n.getFirstChild())).isNull();
  }

  private void assertObjectLiteralCast(String code) {
    Node n = parseTestCode(code);
    assertThat(conv.getObjectLiteralCast(n.getFirstChild())).isNotNull();
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

  private void assertCachingCall(String code) {
    Node node = parseTestCode(code);
    assertThat(conv.describeCachingCall(node.getFirstChild())).isNotNull();
  }

  private void assertNotCachingCall(String code) {
    Node node = parseTestCode(code);
    assertThat(conv.describeCachingCall(node.getFirstChild())).isNull();
  }

  private Node parseTestCode(String code) {
    Compiler compiler = new Compiler();
    return compiler.parseTestCode(code).getFirstChild();
  }
}
