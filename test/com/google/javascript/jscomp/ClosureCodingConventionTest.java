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
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import junit.framework.TestCase;

/**
 * Test class for {@link GoogleCodingConvention}.
 */
public final class ClosureCodingConventionTest extends TestCase {
  private ClosureCodingConvention conv = new ClosureCodingConvention();

  public void testVarAndOptionalParams() {
    Node args = new Node(Token.PARAM_LIST,
        Node.newString(Token.NAME, "a"),
        Node.newString(Token.NAME, "b"));
    Node optArgs = new Node(Token.PARAM_LIST,
        Node.newString(Token.NAME, "opt_a"),
        Node.newString(Token.NAME, "opt_b"));

    assertFalse(conv.isVarArgsParameter(args.getFirstChild()));
    assertFalse(conv.isVarArgsParameter(args.getLastChild()));
    assertFalse(conv.isVarArgsParameter(optArgs.getFirstChild()));
    assertFalse(conv.isVarArgsParameter(optArgs.getLastChild()));

    assertFalse(conv.isOptionalParameter(args.getFirstChild()));
    assertFalse(conv.isOptionalParameter(args.getLastChild()));
    assertFalse(conv.isOptionalParameter(optArgs.getFirstChild()));
    assertFalse(conv.isOptionalParameter(optArgs.getLastChild()));
  }

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

  public void testExportedName() {
    assertFalse(conv.isExported("_a"));
    assertFalse(conv.isExported("_a_"));
    assertFalse(conv.isExported("a"));

    assertFalse(conv.isExported("$super", false));
    assertTrue(conv.isExported("$super", true));
    assertTrue(conv.isExported("$super"));
  }

  public void testPrivateName() {
    assertFalse(conv.isPrivate("a_"));
    assertFalse(conv.isPrivate("a"));
    assertFalse(conv.isPrivate("_a_"));
  }

  public void testEnumKey() {
    assertTrue(conv.isValidEnumKey("A"));
    assertTrue(conv.isValidEnumKey("123"));
    assertTrue(conv.isValidEnumKey("FOO_BAR"));

    assertTrue(conv.isValidEnumKey("a"));
    assertTrue(conv.isValidEnumKey("someKeyInCamelCase"));
    assertTrue(conv.isValidEnumKey("_FOO_BAR"));
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

  public void testInheritanceDetection11() {
    assertNotClassDefining("A.mixin(B)");
  }

  public void testInheritanceDetection12() {
    assertNotClassDefining("goog.mixin(A.prototype, B)");
  }

  public void testInheritanceDetection13() {
    assertNotClassDefining("goog.mixin(A, B)");
  }

  public void testInheritanceDetection14() {
    assertNotClassDefining("goog$mixin((function(){}).prototype)");
  }

  public void testInheritanceDetection15() {
    assertDefinesClasses("$jscomp.inherits(A, B)", "A", "B");
  }

  public void testInheritanceDetection16() {
    assertDefinesClasses("$jscomp$inherits(A, B)", "A", "B");
  }

  public void testInheritanceDetectionPostCollapseProperties() {
    assertDefinesClasses("goog$inherits(A, B);", "A", "B");
    assertNotClassDefining("goog$inherits(A);");
  }

  public void testObjectLiteralCast() {
    assertNotObjectLiteralCast("goog.reflect.object();");
    assertNotObjectLiteralCast("goog.reflect.object(A);");
    assertNotObjectLiteralCast("goog.reflect.object(1, {});");
    assertObjectLiteralCast("goog.reflect.object(A, {});");
  }

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

  public void testRequire() {
    assertRequire("goog.require('foo')");
    assertNotRequire("goog.require(foo)");
    assertNotRequire("goog.require()");
    assertNotRequire("foo()");
  }

  public void testApplySubclassRelationship() {
    JSTypeRegistry registry = new JSTypeRegistry(null);

    Node nodeA = new Node(Token.FUNCTION);
    FunctionType ctorA = registry.createConstructorType("A", nodeA,
        new Node(Token.PARAM_LIST), null, null);

    Node nodeB = new Node(Token.FUNCTION);
    FunctionType ctorB = registry.createConstructorType("B", nodeB,
        new Node(Token.PARAM_LIST), null, null);

    conv.applySubclassRelationship(ctorA, ctorB, SubclassType.INHERITS);

    assertTrue(ctorB.getPrototype().hasOwnProperty("constructor"));
    assertEquals(nodeB, ctorB.getPrototype().getPropertyNode("constructor"));

    assertTrue(ctorB.hasOwnProperty("superClass_"));
    assertEquals(nodeB, ctorB.getPropertyNode("superClass_"));
  }

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
    assertNotNull(conv.describeFunctionBind(n.getFirstChild()));
  }

  private void assertNotFunctionBind(String code) {
    Node n = parseTestCode(code);
    assertNull(conv.describeFunctionBind(n.getFirstChild()));
  }

  private void assertRequire(String code) {
    Node n = parseTestCode(code);
    assertNotNull(conv.extractClassNameIfRequire(n.getFirstChild(), n));
  }

  private void assertNotRequire(String code) {
    Node n = parseTestCode(code);
    assertNull(conv.extractClassNameIfRequire(n.getFirstChild(), n));
  }

  private void assertNotObjectLiteralCast(String code) {
    Node n = parseTestCode(code);
    assertNull(conv.getObjectLiteralCast(n.getFirstChild()));
  }

  private void assertObjectLiteralCast(String code) {
    Node n = parseTestCode(code);
    assertNotNull(conv.getObjectLiteralCast(n.getFirstChild()));
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

  private void assertCachingCall(String code) {
    Node node = parseTestCode(code);
    assertNotNull(conv.describeCachingCall(node.getFirstChild()));
  }

  private void assertNotCachingCall(String code) {
    Node node = parseTestCode(code);
    assertNull(conv.describeCachingCall(node.getFirstChild()));
  }

  private Node parseTestCode(String code) {
    Compiler compiler = new Compiler();
    return compiler.parseTestCode(code).getFirstChild();
  }
}
