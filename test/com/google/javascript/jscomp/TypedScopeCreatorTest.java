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

import static com.google.javascript.jscomp.TypedScopeCreator.CTOR_INITIALIZER;
import static com.google.javascript.jscomp.TypedScopeCreator.IFACE_INITIALIZER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.testing.Asserts;

import java.util.Deque;

/**
 * Tests for {@link TypedScopeCreator} and {@link TypeInference}. Admittedly,
 * the name is a bit of a misnomer.
 * @author nicksantos@google.com (Nick Santos)
 */
public class TypedScopeCreatorTest extends CompilerTestCase {

  private JSTypeRegistry registry;
  private Scope globalScope;
  private Scope lastLocalScope;

  @Override
  public int getNumRepetitions() {
    return 1;
  }

  private final Callback callback = new AbstractPostOrderCallback() {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Scope s = t.getScope();
      if (s.isGlobal()) {
        globalScope = s;
      } else {
        lastLocalScope = s;
      }
    }
  };

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    registry = compiler.getTypeRegistry();
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        MemoizedScopeCreator scopeCreator =
            new MemoizedScopeCreator(new TypedScopeCreator(compiler));
        Scope topScope = scopeCreator.createScope(root.getParent(), null);
        (new TypeInferencePass(
            compiler, compiler.getReverseAbstractInterpreter(),
            topScope, scopeCreator)).process(externs, root);
        NodeTraversal t = new NodeTraversal(
            compiler, callback, scopeCreator);
        t.traverseRoots(Lists.newArrayList(externs, root));
      }
    };
  }

  public void testStubProperty() {
    testSame("function Foo() {}; Foo.bar;");
    ObjectType foo = (ObjectType) globalScope.getVar("Foo").getType();
    assertFalse(foo.hasProperty("bar"));
    Asserts.assertTypeEquals(registry.getNativeType(UNKNOWN_TYPE),
        foo.getPropertyType("bar"));
    Asserts.assertTypeCollectionEquals(
        Lists.newArrayList(foo), registry.getTypesWithProperty("bar"));
  }

  public void testConstructorProperty() {
    testSame("var foo = {}; /** @constructor */ foo.Bar = function() {};");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.hasProperty("Bar"));
    assertFalse(foo.isPropertyTypeInferred("Bar"));

    JSType fooBar = foo.getPropertyType("Bar");
    assertEquals("function (new:foo.Bar): undefined", fooBar.toString());
    Asserts.assertTypeCollectionEquals(
        Lists.newArrayList(foo), registry.getTypesWithProperty("Bar"));
  }

  public void testPrototypePropertyMethodWithoutAnnotation() {
    testSame("var Foo = function Foo() {};" +
             "var proto = Foo.prototype = {" +
             "   bar: function(a, b){}" +
             "};" +
             "proto.baz = function(c) {};" +
             "(function() { proto.baz = function() {}; })();");
    ObjectType foo = (ObjectType) findNameType("Foo", globalScope);
    assertTrue(foo.hasProperty("prototype"));

    ObjectType fooProto = (ObjectType) foo.getPropertyType("prototype");
    assertTrue(fooProto.hasProperty("bar"));
    assertEquals("function (?, ?): undefined",
        fooProto.getPropertyType("bar").toString());

    assertTrue(fooProto.hasProperty("baz"));
    assertEquals("function (?): undefined",
        fooProto.getPropertyType("baz").toString());
  }

  public void testEnumProperty() {
    testSame("var foo = {}; /** @enum */ foo.Bar = {XXX: 'xxx'};");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.hasProperty("Bar"));
    assertFalse(foo.isPropertyTypeInferred("Bar"));
    assertTrue(foo.isPropertyTypeDeclared("Bar"));

    JSType fooBar = foo.getPropertyType("Bar");
    assertEquals("enum{foo.Bar}", fooBar.toString());
    Asserts.assertTypeCollectionEquals(
        Lists.newArrayList(foo), registry.getTypesWithProperty("Bar"));
  }

  public void testInferredProperty1() {
    testSame("var foo = {}; foo.Bar = 3;");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertTrue(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty1a() {
    testSame("var foo = {}; /** @type {number} */ foo.Bar = 3;");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertFalse(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty2() {
    testSame("var foo = { Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertTrue(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty2b() {
    testSame("var foo = { /** @type {number} */ Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertFalse(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty2c() {
    testSame("var foo = { /** @return {number} */ Bar: 3 };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("function (): number", foo.getPropertyType("Bar").toString());
    assertFalse(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty3() {
    testSame("var foo = { /** @type {number} */ get Bar() { return 3 } };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("?", foo.getPropertyType("Bar").toString());
    assertTrue(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty4() {
    testSame("var foo = { /** @type {number} */ set Bar(a) {} };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("?", foo.getPropertyType("Bar").toString());
    assertTrue(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty5() {
    testSame("var foo = { /** @return {number} */ get Bar() { return 3 } };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertFalse(foo.isPropertyTypeInferred("Bar"));
  }

  public void testInferredProperty6() {
    testSame("var foo = { /** @param {number} a */ set Bar(a) {} };");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.toString(), foo.hasProperty("Bar"));
    assertEquals("number", foo.getPropertyType("Bar").toString());
    assertFalse(foo.isPropertyTypeInferred("Bar"));
  }

  public void testPrototypeInit() {
    testSame("/** @constructor */ var Foo = function() {};" +
        "Foo.prototype = {bar: 1}; var foo = new Foo();");
    ObjectType foo = (ObjectType) findNameType("foo", globalScope);
    assertTrue(foo.hasProperty("bar"));
    assertEquals("number", foo.getPropertyType("bar").toString());
    assertTrue(foo.isPropertyTypeInferred("bar"));
  }

  public void testBogusPrototypeInit() {
    // This used to cause a compiler crash.
    testSame("/** @const */ var goog = {}; " +
        "goog.F = {}; /** @const */ goog.F.prototype = {};" +
        "/** @constructor */ goog.F = function() {};");
  }

  public void testInferredPrototypeProperty1() {
    testSame("/** @constructor */ var Foo = function() {};" +
        "Foo.prototype.bar = 1; var x = new Foo();");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertTrue(x.hasProperty("bar"));
    assertEquals("number", x.getPropertyType("bar").toString());
    assertTrue(x.isPropertyTypeInferred("bar"));
  }

  public void testInferredPrototypeProperty2() {
    testSame("/** @constructor */ var Foo = function() {};" +
        "Foo.prototype = {bar: 1}; var x = new Foo();");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertTrue(x.hasProperty("bar"));
    assertEquals("number", x.getPropertyType("bar").toString());
    assertTrue(x.isPropertyTypeInferred("bar"));
  }

  public void testEnum() {
    testSame("/** @enum */ var Foo = {BAR: 1}; var f = Foo;");
    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertTrue(f.hasProperty("BAR"));
    assertEquals("Foo.<number>", f.getPropertyType("BAR").toString());
    assertTrue(f instanceof EnumType);
  }

  public void testEnumElement() {
    testSame("/** @enum */ var Foo = {BAR: 1}; var f = Foo;");
    Var bar = globalScope.getVar("Foo.BAR");
    assertNotNull(bar);
    assertEquals("Foo.<number>", bar.getType().toString());
  }

  public void testNamespacedEnum() {
    testSame("var goog = {}; goog.ui = {};" +
        "/** @constructor */goog.ui.Zippy = function() {};" +
        "/** @enum{string} */goog.ui.Zippy.EventType = { TOGGLE: 'toggle' };" +
        "var x = goog.ui.Zippy.EventType;" +
        "var y = goog.ui.Zippy.EventType.TOGGLE;");

    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertTrue(x.isEnumType());
    assertTrue(x.hasProperty("TOGGLE"));
    assertEquals("enum{goog.ui.Zippy.EventType}", x.getReferenceName());

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertTrue(y.isSubtype(getNativeType(STRING_TYPE)));
    assertTrue(y.isEnumElementType());
    assertEquals("goog.ui.Zippy.EventType", y.getReferenceName());
  }

  public void testEnumAlias() {
    testSame("/** @enum */ var Foo = {BAR: 1}; " +
        "/** @enum */ var FooAlias = Foo; var f = FooAlias;");

    assertEquals("Foo.<number>",
        registry.getType("FooAlias").toString());
    Asserts.assertTypeEquals(registry.getType("FooAlias"),
        registry.getType("Foo"));

    ObjectType f = (ObjectType) findNameType("f", globalScope);
    assertTrue(f.hasProperty("BAR"));
    assertEquals("Foo.<number>", f.getPropertyType("BAR").toString());
    assertTrue(f instanceof EnumType);
  }

  public void testNamespacesEnumAlias() {
    testSame("var goog = {}; /** @enum */ goog.Foo = {BAR: 1}; " +
        "/** @enum */ goog.FooAlias = goog.Foo;");

    assertEquals("goog.Foo.<number>",
        registry.getType("goog.FooAlias").toString());
    Asserts.assertTypeEquals(registry.getType("goog.Foo"),
        registry.getType("goog.FooAlias"));
  }

  public void testCollectedFunctionStub() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("f", x.toString());
    assertTrue(x.hasProperty("foo"));
    assertEquals("function (this:f): number",
        x.getPropertyType("foo").toString());
    assertFalse(x.isPropertyTypeInferred("foo"));
  }

  public void testCollectedFunctionStubLocal() {
    testSame(
        "(function() {" +
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}" +
        "var x = new f();" +
        "});");
    ObjectType x = (ObjectType) findNameType("x", lastLocalScope);
    assertEquals("f", x.toString());
    assertTrue(x.hasProperty("foo"));
    assertEquals("function (this:f): number",
        x.getPropertyType("foo").toString());
    assertFalse(x.isPropertyTypeInferred("foo"));
  }

  public void testNamespacedFunctionStub() {
    testSame(
        "var goog = {};" +
        "/** @param {number} x */ goog.foo;");

    ObjectType goog = (ObjectType) findNameType("goog", globalScope);
    assertTrue(goog.hasProperty("foo"));
    assertEquals("function (number): ?",
        goog.getPropertyType("foo").toString());
    assertTrue(goog.isPropertyTypeDeclared("foo"));

    Asserts.assertTypeEquals(globalScope.getVar("goog.foo").getType(),
        goog.getPropertyType("foo"));
  }

  public void testNamespacedFunctionStubLocal() {
    testSame(
        "(function() {" +
        "var goog = {};" +
        "/** @param {number} x */ goog.foo;" +
        "});");

    ObjectType goog = (ObjectType) findNameType("goog", lastLocalScope);
    assertTrue(goog.hasProperty("foo"));
    assertEquals("function (number): ?",
        goog.getPropertyType("foo").toString());
    assertTrue(goog.isPropertyTypeDeclared("foo"));

    Asserts.assertTypeEquals(lastLocalScope.getVar("goog.foo").getType(),
        goog.getPropertyType("foo"));
  }

  public void testCollectedCtorProperty() {
    testSame(
        "/** @constructor */ function f() { " +
        "  /** @type {number} */ this.foo = 3;" +
        "}" +
        "var x = new f();");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("f", x.toString());
    assertTrue(x.hasProperty("foo"));
    assertEquals("number", x.getPropertyType("foo").toString());
    assertFalse(x.isPropertyTypeInferred("foo"));
  }

  public void testPropertyOnUnknownSuperClass1() {
    testSame(
        "var goog = this.foo();" +
        "/** @constructor \n * @extends {goog.Unknown} */" +
        "function Foo() {}" +
        "Foo.prototype.bar = 1;" +
        "var x = new Foo();",
        RhinoErrorReporter.TYPE_PARSE_ERROR);
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("Foo", x.toString());
    assertTrue(x.getImplicitPrototype().hasOwnProperty("bar"));
    assertEquals("?", x.getPropertyType("bar").toString());
    assertTrue(x.isPropertyTypeInferred("bar"));
  }

  public void testPropertyOnUnknownSuperClass2() {
    testSame(
        "var goog = this.foo();" +
        "/** @constructor \n * @extends {goog.Unknown} */" +
        "function Foo() {}" +
        "Foo.prototype = {bar: 1};" +
        "var x = new Foo();",
        RhinoErrorReporter.TYPE_PARSE_ERROR);
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("Foo", x.toString());
    assertEquals("Foo.prototype", x.getImplicitPrototype().toString());
    assertTrue(x.getImplicitPrototype().hasOwnProperty("bar"));
    assertEquals("?", x.getPropertyType("bar").toString());
    assertTrue(x.isPropertyTypeInferred("bar"));
  }

  public void testMethodBeforeFunction1() throws Exception {
    testSame(
        "var y = Window.prototype;" +
        "Window.prototype.alert = function(message) {};" +
        "/** @constructor */ function Window() {}\n" +
        "var window = new Window(); \n" +
        "var x = window;");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("Window", x.toString());
    assertTrue(x.getImplicitPrototype().hasOwnProperty("alert"));
    assertEquals("function (this:Window, ?): undefined",
        x.getPropertyType("alert").toString());
    assertTrue(x.isPropertyTypeDeclared("alert"));

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertEquals("function (this:Window, ?): undefined",
        y.getPropertyType("alert").toString());
  }

  public void testMethodBeforeFunction2() throws Exception {
    testSame(
        "var y = Window.prototype;" +
        "Window.prototype = {alert: function(message) {}};" +
        "/** @constructor */ function Window() {}\n" +
        "var window = new Window(); \n" +
        "var x = window;");
    ObjectType x = (ObjectType) findNameType("x", globalScope);
    assertEquals("Window", x.toString());
    assertTrue(x.getImplicitPrototype().hasOwnProperty("alert"));
    assertEquals("function (this:Window, ?): undefined",
        x.getPropertyType("alert").toString());
    assertFalse(x.isPropertyTypeDeclared("alert"));

    ObjectType y = (ObjectType) findNameType("y", globalScope);
    assertEquals("?",
        y.getPropertyType("alert").toString());
  }

  public void testAddMethodsPrototypeTwoWays() throws Exception {
    testSame(
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true};" +
        "A.prototype.m3 = 'third property!';" +
        "var x = new A();");

    ObjectType instanceType = (ObjectType) findNameType("x", globalScope);
    assertEquals(
        getNativeObjectType(OBJECT_TYPE).getPropertiesCount() + 3,
        instanceType.getPropertiesCount());
    Asserts.assertTypeEquals(getNativeType(NUMBER_TYPE),
        instanceType.getPropertyType("m1"));
    Asserts.assertTypeEquals(getNativeType(BOOLEAN_TYPE),
        instanceType.getPropertyType("m2"));
    Asserts.assertTypeEquals(getNativeType(STRING_TYPE),
        instanceType.getPropertyType("m3"));

    // Verify the prototype chain.
    // This is a special case where we want the anonymous object to
    // become a prototype.
    assertFalse(instanceType.hasOwnProperty("m1"));
    assertFalse(instanceType.hasOwnProperty("m2"));
    assertFalse(instanceType.hasOwnProperty("m3"));

    ObjectType proto1 = instanceType.getImplicitPrototype();
    assertTrue(proto1.hasOwnProperty("m1"));
    assertTrue(proto1.hasOwnProperty("m2"));
    assertTrue(proto1.hasOwnProperty("m3"));

    ObjectType proto2 = proto1.getImplicitPrototype();
    assertFalse(proto2.hasProperty("m1"));
    assertFalse(proto2.hasProperty("m2"));
    assertFalse(proto2.hasProperty("m3"));
  }

  public void testInferredVar() throws Exception {
    testSame("var x = 3; x = 'x'; x = true;");

    Var x = globalScope.getVar("x");
    assertEquals("(boolean|number|string)", x.getType().toString());
    assertTrue(x.isTypeInferred());
  }

  public void testDeclaredVar() throws Exception {
    testSame("/** @type {?number} */ var x = 3; var y = x;");

    Var x = globalScope.getVar("x");
    assertEquals("(null|number)", x.getType().toString());
    assertFalse(x.isTypeInferred());

    JSType y = findNameType("y", globalScope);
    assertEquals("(null|number)", y.toString());
  }

  public void testPropertiesOnInterface() throws Exception {
    testSame("/** @interface */ var I = function() {};" +
        "/** @type {number} */ I.prototype.bar;" +
        "I.prototype.baz = function(){};");

    Var i = globalScope.getVar("I");
    assertEquals("function (this:I): ?", i.getType().toString());
    assertTrue(i.getType().isInterface());

    ObjectType iPrototype = (ObjectType)
        ((ObjectType) i.getType()).getPropertyType("prototype");
    assertEquals("I.prototype", iPrototype.toString());
    assertTrue(iPrototype.isFunctionPrototypeType());

    assertEquals("number", iPrototype.getPropertyType("bar").toString());
    assertEquals("function (this:I): undefined",
        iPrototype.getPropertyType("baz").toString());

    Asserts.assertTypeEquals(iPrototype, globalScope.getVar("I.prototype").getType());
  }

  public void testPropertiesOnInterface2() throws Exception {
    testSame("/** @interface */ var I = function() {};" +
        "I.prototype = {baz: function(){}};" +
        "/** @type {number} */ I.prototype.bar;");

    Var i = globalScope.getVar("I");
    assertEquals("function (this:I): ?", i.getType().toString());
    assertTrue(i.getType().isInterface());

    ObjectType iPrototype = (ObjectType)
        ((ObjectType) i.getType()).getPropertyType("prototype");
    assertEquals("I.prototype", iPrototype.toString());
    assertTrue(iPrototype.isFunctionPrototypeType());

    assertEquals("number", iPrototype.getPropertyType("bar").toString());

    assertEquals("function (this:I): undefined",
        iPrototype.getPropertyType("baz").toString());

    // should not be null
    assertNull(globalScope.getVar("I.prototype"));
    // assertEquals(iPrototype, globalScope.getVar("I.prototype").getType());
  }

  // TODO(johnlenz): A syntax for stubs using object literals?

  public void testStubsInExterns() {
    testSame(
        "/** @constructor */ function Extern() {}" +
        "Extern.prototype.bar;" +
        "var e = new Extern(); e.baz;",
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar;" +
        "var f = new Foo(); f.baz;", null);

    ObjectType e = (ObjectType) globalScope.getVar("e").getType();
    assertEquals("?", e.getPropertyType("bar").toString());
    assertEquals("?", e.getPropertyType("baz").toString());

    ObjectType f = (ObjectType) globalScope.getVar("f").getType();
    assertEquals("?", f.getPropertyType("bar").toString());
    assertFalse(f.hasProperty("baz"));
  }

  public void testStubsInExterns2() {
    testSame(
        "/** @constructor */ function Extern() {}" +
        "/** @type {Extern} */ var myExtern;" +
        "/** @type {number} */ myExtern.foo;",
        "", null);

    JSType e = globalScope.getVar("myExtern").getType();
    assertEquals("(Extern|null)", e.toString());

    ObjectType externType = (ObjectType) e.restrictByNotNullOrUndefined();
    assertTrue(globalScope.getRootNode().toStringTree(),
        externType.hasOwnProperty("foo"));
    assertTrue(externType.isPropertyTypeDeclared("foo"));
    assertEquals("number", externType.getPropertyType("foo").toString());
    assertTrue(externType.isPropertyInExterns("foo"));
  }

  public void testStubsInExterns3() {
    testSame(
        "/** @type {number} */ myExtern.foo;" +
        "/** @type {Extern} */ var myExtern;" +
        "/** @constructor */ function Extern() {}",
        "", null);

    JSType e = globalScope.getVar("myExtern").getType();
    assertEquals("(Extern|null)", e.toString());

    ObjectType externType = (ObjectType) e.restrictByNotNullOrUndefined();
    assertTrue(globalScope.getRootNode().toStringTree(),
        externType.hasOwnProperty("foo"));
    assertTrue(externType.isPropertyTypeDeclared("foo"));
    assertEquals("number", externType.getPropertyType("foo").toString());
    assertTrue(externType.isPropertyInExterns("foo"));
  }

  public void testStubsInExterns4() {
    testSame(
        "Extern.prototype.foo;" +
        "/** @constructor */ function Extern() {}",
        "", null);

    JSType e = globalScope.getVar("Extern").getType();
    assertEquals("function (new:Extern): ?", e.toString());

    ObjectType externProto = ((FunctionType) e).getPrototype();
    assertTrue(globalScope.getRootNode().toStringTree(),
        externProto.hasOwnProperty("foo"));
    assertTrue(externProto.isPropertyTypeInferred("foo"));
    assertEquals("?", externProto.getPropertyType("foo").toString());
    assertTrue(externProto.isPropertyInExterns("foo"));
  }

  public void testPropertyInExterns1() {
    testSame(
        "/** @constructor */ function Extern() {}" +
        "/** @type {Extern} */ var extern;" +
        "/** @return {number} */ extern.one;",
        "/** @constructor */ function Normal() {}" +
        "/** @type {Normal} */ var normal;" +
        "/** @return {number} */ normal.one;", null);

    JSType e = globalScope.getVar("Extern").getType();
    ObjectType externInstance = ((FunctionType) e).getInstanceType();
    assertTrue(externInstance.hasOwnProperty("one"));
    assertTrue(externInstance.isPropertyTypeDeclared("one"));
    assertEquals("function (): number",
        externInstance.getPropertyType("one").toString());

    JSType n = globalScope.getVar("Normal").getType();
    ObjectType normalInstance = ((FunctionType) n).getInstanceType();
    assertFalse(normalInstance.hasOwnProperty("one"));
  }

  public void testPropertyInExterns2() {
    testSame(
        "/** @type {Object} */ var extern;" +
        "/** @return {number} */ extern.one;",
        "/** @type {Object} */ var normal;" +
        "/** @return {number} */ normal.one;", null);

    JSType e = globalScope.getVar("extern").getType();
    assertFalse(e.dereference().hasOwnProperty("one"));

    JSType normal = globalScope.getVar("normal").getType();
    assertFalse(normal.dereference().hasOwnProperty("one"));
  }

  public void testPropertyInExterns3() {
    testSame(
        "/** @constructor \n * @param {*=} x */ function Object(x) {}" +
        "/** @type {number} */ Object.one;", "", null);

    ObjectType obj = globalScope.getVar("Object").getType().dereference();
    assertTrue(obj.hasOwnProperty("one"));
    assertEquals("number", obj.getPropertyType("one").toString());
  }

  public void testTypedStubsInExterns() {
    testSame(
        "/** @constructor \n * @param {*} var_args */ " +
        "function Function(var_args) {}" +
        "/** @type {!Function} */ Function.prototype.apply;",
        "var f = new Function();", null);

    ObjectType f = (ObjectType) globalScope.getVar("f").getType();

    // The type of apply() on a function instance is resolved dynamically,
    // since apply varies with the type of the function it's called on.
    assertEquals(
        "function (?=, (Object|null)=): ?",
        f.getPropertyType("apply").toString());

    // The type of apply() on the function prototype just takes what it was
    // declared with.
    FunctionType func = (FunctionType) globalScope.getVar("Function").getType();
    assertEquals("Function",
        func.getPrototype().getPropertyType("apply").toString());
  }

  public void testTypesInExterns() throws Exception {
    testSame(
        CompilerTypeTestCase.DEFAULT_EXTERNS,
        "", null);

    Var v = globalScope.getVar("Object");
    FunctionType obj = (FunctionType) v.getType();
    assertEquals("function (new:Object, *=): ?", obj.toString());
    assertNotNull(v.getNode());
    assertNotNull(v.input);
  }

  public void testPropertyDeclarationOnInstanceType() {
    testSame(
        "/** @type {!Object} */ var a = {};" +
        "/** @type {number} */ a.name = 0;");

    assertEquals("number", globalScope.getVar("a.name").getType().toString());

    ObjectType a = (ObjectType) (globalScope.getVar("a").getType());
    assertFalse(a.hasProperty("name"));
    assertFalse(getNativeObjectType(OBJECT_TYPE).hasProperty("name"));
  }

  public void testPropertyDeclarationOnRecordType() {
    testSame(
        "/** @type {{foo: number}} */ var a = {foo: 3};" +
        "/** @type {number} */ a.name = 0;");

    assertEquals("number", globalScope.getVar("a.name").getType().toString());

    ObjectType a = (ObjectType) (globalScope.getVar("a").getType());
    assertEquals("{foo: number}", a.toString());
    assertFalse(a.hasProperty("name"));
  }

  public void testGlobalThis1() {
    testSame(
        "/** @constructor */ function Window() {}" +
        "Window.prototype.alert = function() {};" +
        "var x = this;");

    ObjectType x = (ObjectType) (globalScope.getVar("x").getType());
    FunctionType windowCtor =
        (FunctionType) (globalScope.getVar("Window").getType());
    assertEquals("global this", x.toString());
    assertTrue(x.isSubtype(windowCtor.getInstanceType()));
    assertFalse(x.isEquivalentTo(windowCtor.getInstanceType()));
    assertTrue(x.hasProperty("alert"));
  }

  public void testGlobalThis2() {
    testSame(
        "/** @constructor */ function Window() {}" +
        "Window.prototype = {alert: function() {}};" +
        "var x = this;");

    ObjectType x = (ObjectType) (globalScope.getVar("x").getType());
    FunctionType windowCtor =
        (FunctionType) (globalScope.getVar("Window").getType());
    assertEquals("global this", x.toString());
    assertTrue(x.isSubtype(windowCtor.getInstanceType()));
    assertFalse(x.isEquivalentTo(windowCtor.getInstanceType()));
    assertTrue(x.hasProperty("alert"));
  }

  public void testObjectLiteralCast() {
    // Verify that "goog.reflect.object" does not modify the types on
    // "A.B"
    testSame("/** @constructor */ A.B = function() {}\n" +
             "A.B.prototype.isEnabled = true;\n" +
             "goog.reflect.object(A.B, {isEnabled: 3})\n" +
             "var x = (new A.B()).isEnabled;");

    assertEquals("A.B",
        findTokenType(Token.OBJECTLIT, globalScope).toString());
    assertEquals("boolean",
        findNameType("x", globalScope).toString());
  }

  public void testBadObjectLiteralCast1() {
    testSame("/** @constructor */ A.B = function() {}\n" +
             "goog.reflect.object(A.B, 1)",
             ClosureCodingConvention.OBJECTLIT_EXPECTED);
  }

  public void testBadObjectLiteralCast2() {
    testSame("goog.reflect.object(A.B, {})",
             TypedScopeCreator.CONSTRUCTOR_EXPECTED);
  }

  public void testConstructorNode() {
    testSame("var goog = {}; /** @constructor */ goog.Foo = function() {};");

    ObjectType ctor = (ObjectType) (findNameType("goog.Foo", globalScope));
    assertNotNull(ctor);
    assertTrue(ctor.isConstructor());
    assertEquals("function (new:goog.Foo): undefined", ctor.toString());
  }

  public void testForLoopIntegration() {
    testSame("var y = 3; for (var x = true; x; y = x) {}");

    Var y = globalScope.getVar("y");
    assertTrue(y.isTypeInferred());
    assertEquals("(boolean|number)", y.getType().toString());
  }

  public void testConstructorAlias() {
    testSame(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;");
    assertEquals("Foo", registry.getType("FooAlias").toString());
    Asserts.assertTypeEquals(registry.getType("Foo"), registry.getType("FooAlias"));
  }

  public void testNamespacedConstructorAlias() {
    testSame(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function() {};" +
        "/** @constructor */ goog.FooAlias = goog.Foo;");
    assertEquals("goog.Foo", registry.getType("goog.FooAlias").toString());
    Asserts.assertTypeEquals(registry.getType("goog.Foo"),
        registry.getType("goog.FooAlias"));
  }

  public void testTemplateType1() {
    testSame(
        "/**\n" +
        " * @param {function(this:T, ...)} fn\n" +
        " * @param {T} thisObj\n" +
        " * @template T\n" +
        " */\n" +
        "function bind(fn, thisObj) {}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @return {number} */\n" +
        "Foo.prototype.baz = function() {};\n" +
        "bind(function() { var g = this; var f = this.baz(); }, new Foo());");
    assertEquals("Foo", findNameType("g", lastLocalScope).toString());
    assertEquals("number", findNameType("f", lastLocalScope).toString());
  }

  public void testTemplateType2() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertEquals("string", findNameType("result", globalScope).toString());
  }

  public void testTemplateType2a() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T|undefined}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertEquals("(string|undefined)",
        findNameType("result", globalScope).toString());
  }

  public void testTemplateType2b() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string|undefined} */\n" +
        "var val = 'hi';\n" +
        "var result = f(val);");
    assertEquals("(string|undefined)",
        findNameType("result", globalScope).toString());
  }

  public void testTemplateType3() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {string} */\n" +
        "var val1 = 'hi';\n" +
        "var result1 = f(val1);" +
        "/** @type {number} */\n" +
        "var val2 = 0;\n" +
        "var result2 = f(val2);");

    assertEquals("string", findNameType("result1", globalScope).toString());
    assertEquals("number", findNameType("result2", globalScope).toString());
  }

  public void testTemplateType4() {
    testSame(
        "/**\n" +
        " * @param {T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @type {!Array.<string>} */\n" +
        "var arr = [];\n" +
        "(function () {var result = f(arr);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertEquals("Array.<string>", resultType.toString());
  }

  public void testTemplateType4a() {
    testSame(
        "/**\n" +
        " * @param {function():T} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @return {string} */\n" +
        "var g = function(){return 'hi'};\n" +
        "(function () {var result = f(g);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertEquals("string", resultType.toString());
  }

  public void testTemplateType4b() {
    testSame(
        "/**\n" +
        " * @param {function(T):void} x\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(x) {\n" +
        "  return x;\n" +
        "}" +
        "/** @param {string} x */\n" +
        "var g = function(x){};\n" +
        "(function () {var result = f(g);})();");

    JSType resultType = findNameType("result", lastLocalScope);
    assertEquals("string", resultType.toString());
  }

  public void testTemplateType5() {
    testSame(
        "/**\n" +
        " * @param {Array.<T>} arr\n" +
        " * @return {!Array.<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(arr) {\n" +
        "  return arr;\n" +
        "}" +
        "/** @type {Array.<string>} */\n" +
        "var arr = [];\n" +
        "var result = f(arr);");

    assertEquals("Array.<string>", findNameTypeStr("result", globalScope));
  }

  public void testTemplateType6() {
    testSame(
        "/**\n" +
        " * @param {Array.<T>|string|undefined} arr\n" +
        " * @return {!Array.<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "function f(arr) {\n" +
        "  return arr;\n" +
        "}" +
        "/** @type {Array.<string>} */\n" +
        "var arr = [];\n" +
        "var result = f(arr);");

    assertEquals("Array.<string>", findNameTypeStr("result", globalScope));
  }


  public void testTemplateType7() {
    testSame(
        "var goog = {};\n" +
        "goog.array = {};\n" +
        "/**\n" +
        " * @param {Array.<T>} arr\n" +
        " * @param {function(this:S, !T, number, !Array.<!T>):boolean} f\n" +
        " * @param {!S=} opt_obj\n" +
        " * @return {!Array.<T>}\n" +
        " * @template T,S\n" +
        " */\n" +
        "goog.array.filter = function(arr, f, opt_obj) {\n" +
        "  var res = [];\n" +
        "  for (var i = 0; i < arr.length; i++) {\n" +
        "     if (f.call(opt_obj, arr[i], i, arr)) {\n" +
        "        res.push(val);\n" +
        "     }\n" +
        "  }\n" +
        "  return res;\n" +
        "}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @type {Array.<string>} */\n" +
        "var arr = [];\n" +
        "var result = goog.array.filter(arr," +
        "  function(a,b,c) {var self=this;}, new Foo());");

    assertEquals("Foo", findNameType("self", lastLocalScope).toString());
    assertEquals("string", findNameType("a", lastLocalScope).toString());
    assertEquals("number", findNameType("b", lastLocalScope).toString());
    assertEquals("Array.<string>",
        findNameType("c", lastLocalScope).toString());
    assertEquals("Array.<string>",
        findNameType("result", globalScope).toString());
  }

  public void testTemplateType7b() {
    testSame(
        "var goog = {};\n" +
        "goog.array = {};\n" +
        "/**\n" +
        " * @param {Array.<T>} arr\n" +
        " * @param {function(this:S, !T, number, !Array.<T>):boolean} f\n" +
        " * @param {!S=} opt_obj\n" +
        " * @return {!Array.<T>}\n" +
        " * @template T,S\n" +
        " */\n" +
        "goog.array.filter = function(arr, f, opt_obj) {\n" +
        "  var res = [];\n" +
        "  for (var i = 0; i < arr.length; i++) {\n" +
        "     if (f.call(opt_obj, arr[i], i, arr)) {\n" +
        "        res.push(val);\n" +
        "     }\n" +
        "  }\n" +
        "  return res;\n" +
        "}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @type {Array.<string>} */\n" +
        "var arr = [];\n" +
        "var result = goog.array.filter(arr," +
        "  function(a,b,c) {var self=this;}, new Foo());");

    assertEquals("Foo", findNameType("self", lastLocalScope).toString());
    assertEquals("string", findNameType("a", lastLocalScope).toString());
    assertEquals("number", findNameType("b", lastLocalScope).toString());
    assertEquals("Array.<string>",
        findNameType("c", lastLocalScope).toString());
    assertEquals("Array.<string>",
        findNameType("result", globalScope).toString());
  }

  public void testTemplateType7c() {
    testSame(
        "var goog = {};\n" +
        "goog.array = {};\n" +
        "/**\n" +
        " * @param {Array.<T>} arr\n" +
        " * @param {function(this:S, T, number, Array.<T>):boolean} f\n" +
        " * @param {!S=} opt_obj\n" +
        " * @return {!Array.<T>}\n" +
        " * @template T,S\n" +
        " */\n" +
        "goog.array.filter = function(arr, f, opt_obj) {\n" +
        "  var res = [];\n" +
        "  for (var i = 0; i < arr.length; i++) {\n" +
        "     if (f.call(opt_obj, arr[i], i, arr)) {\n" +
        "        res.push(val);\n" +
        "     }\n" +
        "  }\n" +
        "  return res;\n" +
        "}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @type {Array.<string>} */\n" +
        "var arr = [];\n" +
        "var result = goog.array.filter(arr," +
        "  function(a,b,c) {var self=this;}, new Foo());");

    assertEquals("Foo", findNameType("self", lastLocalScope).toString());
    assertEquals("string", findNameType("a", lastLocalScope).toString());
    assertEquals("number", findNameType("b", lastLocalScope).toString());
    assertEquals("(Array.<string>|null)",
        findNameType("c", lastLocalScope).toString());
    assertEquals("Array.<string>",
        findNameType("result", globalScope).toString());
  }

  public void disable_testTemplateType8() {
    // TODO(johnlenz): somehow allow templated typedefs
    testSame(
        "/** @constructor */ NodeList = function() {};" +
        "/** @constructor */ Arguments = function() {};" +
        "var goog = {};" +
        "goog.array = {};" +
        "/**\n" +
        " * @typedef {Array.<T>|NodeList|Arguments|{length: number}}\n" +
        " * @template T\n" +
        " */\n" +
        "goog.array.ArrayLike;" +
        "/**\n" +
        " * @param {function(this:T, ...)} fn\n" +
        " * @param {T} thisObj\n" +
        " * @template T\n" +
        " */\n" +
        "function bind(fn, thisObj) {}" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/** @return {number} */\n" +
        "Foo.prototype.baz = function() {};\n" +
        "bind(function() { var g = this; var f = this.baz(); }, new Foo());");
    assertEquals("T", findNameType("g", lastLocalScope).toString());
    assertTrue(findNameType("g", lastLocalScope).isEquivalentTo(
        registry.getType("Foo")));
    assertEquals("number", findNameType("f", lastLocalScope).toString());
  }

  public void testTemplateType9() {
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {}\n" +
        "\n" +
        "var g = new Bar().method();\n");
    assertEquals("Bar", findNameType("g", globalScope).toString());
  }

  public void testTemplateType10() {
    // NOTE: we would like the type within the function to remain "Foo"
    // we can handle this by support template type like "T extends Foo"
    // to provide a "minimum" type for "Foo" within the function body.
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {var g = this;};\n");
    assertEquals("T", findNameType("g", lastLocalScope).toString());
  }

  public void testTemplateType11() {
    testSame(
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "var method = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "function Bar() {}\n" +
        "\n" +
        "var g = method().call(new Bar());\n");
    // NOTE: we would like this to be "Bar"
    assertEquals("?", findNameType("g", globalScope).toString());
  }

  public void testTemplateType12() {
    testSame(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/**\n" +
        " * @this {Array.<T>|{length:number}}\n" +
        " * @return {T} fn\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {var g = this;};\n");
    assertEquals("(Array.<T>|{length: number})",
        findNameType("g", lastLocalScope).toString());
  }

  public void testClosureParameterTypesWithoutJSDoc() {
    testSame(
        "/**\n" +
        " * @param {function(!Object)} bar\n" +
        " */\n" +
        "function foo(bar) {}\n" +
        "foo(function(baz) { var f = baz; })\n");
    assertEquals("Object", findNameType("f", lastLocalScope).toString());
  }

  public void testClosureParameterTypesWithJSDoc() {
    testSame(
        "/**\n" +
        " * @param {function(!Object)} bar\n" +
        " */\n" +
        "function foo(bar) {}\n" +
        "foo((/** @type {function(string)} */" +
        "function(baz) { var f = baz; }))\n");
    assertEquals("string", findNameType("f", lastLocalScope).toString());
  }

  public void testDuplicateExternProperty1() {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar;" +
        "/** @type {number} */ Foo.prototype.bar; var x = (new Foo).bar;",
        null);
    assertEquals("number", findNameType("x", globalScope).toString());
  }

  public void testDuplicateExternProperty2() {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "/** @type {number} */ Foo.prototype.bar;" +
        "Foo.prototype.bar; var x = (new Foo).bar;", null);
    assertEquals("number", findNameType("x", globalScope).toString());
  }

  public void testAbstractMethod() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = abstractMethod;");
    assertEquals(
        "Function", findNameType("abstractMethod", globalScope).toString());

    FunctionType ctor = (FunctionType) findNameType("Foo", globalScope);
    ObjectType instance = ctor.getInstanceType();
    assertEquals("Foo", instance.toString());

    ObjectType proto = instance.getImplicitPrototype();
    assertEquals("Foo.prototype", proto.toString());

    assertEquals(
        "function (this:Foo, number): ?",
        proto.getPropertyType("bar").toString());
  }

  public void testAbstractMethod2() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @param {number} x */ var y = abstractMethod;");
    assertEquals(
        "Function",
        findNameType("y", globalScope).toString());
    assertEquals(
        "function (number): ?",
        globalScope.getVar("y").getType().toString());
  }

  public void testAbstractMethod3() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @param {number} x */ var y = abstractMethod; y;");
    assertEquals(
        "function (number): ?",
        findNameType("y", globalScope).toString());
  }

  public void testAbstractMethod4() {
    testSame(
        "/** @type {!Function} */ var abstractMethod;" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {/** @param {number} x */ bar: abstractMethod};");
    assertEquals(
        "Function", findNameType("abstractMethod", globalScope).toString());

    FunctionType ctor = (FunctionType) findNameType("Foo", globalScope);
    ObjectType instance = ctor.getInstanceType();
    assertEquals("Foo", instance.toString());

    ObjectType proto = instance.getImplicitPrototype();
    assertEquals("Foo.prototype", proto.toString());

    assertEquals(
        // should be: "function (this:Foo, number): ?"
        "function (this:Foo, number): ?",
        proto.getPropertyType("bar").toString());
  }

  public void testActiveXObject() {
    testSame(
        CompilerTypeTestCase.ACTIVE_X_OBJECT_DEF,
        "var x = new ActiveXObject();", null);
    assertEquals(
        "?",
        findNameType("x", globalScope).toString());
  }

  public void testReturnTypeInference1() {
    testSame("function f() {}");
    assertEquals(
        "function (): undefined",
        findNameType("f", globalScope).toString());
  }

  public void testReturnTypeInference2() {
    testSame("/** @return {?} */ function f() {}");
    assertEquals(
        "function (): ?",
        findNameType("f", globalScope).toString());
  }

  public void testReturnTypeInference3() {
    testSame("function f() {x: return 3;}");
    assertEquals(
        "function (): ?",
        findNameType("f", globalScope).toString());
  }

  public void testReturnTypeInference4() {
    testSame("function f() { throw Error(); }");
    assertEquals(
        "function (): ?",
        findNameType("f", globalScope).toString());
  }

  public void testReturnTypeInference5() {
    testSame("function f() { if (true) { return 1; } }");
    assertEquals(
        "function (): ?",
        findNameType("f", globalScope).toString());
  }

  public void testLiteralTypesInferred() {
    testSame("null + true + false + 0 + '' + {}");
    assertEquals(
        "null", findTokenType(Token.NULL, globalScope).toString());
    assertEquals(
        "boolean", findTokenType(Token.TRUE, globalScope).toString());
    assertEquals(
        "boolean", findTokenType(Token.FALSE, globalScope).toString());
    assertEquals(
        "number", findTokenType(Token.NUMBER, globalScope).toString());
    assertEquals(
        "string", findTokenType(Token.STRING, globalScope).toString());
    assertEquals(
        "{}", findTokenType(Token.OBJECTLIT, globalScope).toString());
  }

  public void testGlobalQualifiedNameInLocalScope() {
    testSame(
        "var ns = {}; " +
        "(function() { " +
        "    /** @param {number} x */ ns.foo = function(x) {}; })();" +
        "(function() { ns.foo(3); })();");
    assertNotNull(globalScope.getVar("ns.foo"));
    assertEquals(
        "function (number): undefined",
        globalScope.getVar("ns.foo").getType().toString());
  }

  public void testDeclaredObjectLitProperty1() throws Exception {
    testSame("var x = {/** @type {number} */ y: 3};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertEquals(
        "number",
         xType.getPropertyType("y").toString());
    assertEquals(
        "{y: number}",
        xType.toString());
  }

  public void testDeclaredObjectLitProperty2() throws Exception {
    testSame("var x = {/** @param {number} z */ y: function(z){}};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertEquals(
        "function (number): undefined",
         xType.getPropertyType("y").toString());
    assertEquals(
        "{y: function (number): undefined}",
        xType.toString());
  }

  public void testDeclaredObjectLitProperty3() throws Exception {
    testSame("function f() {" +
        "  var x = {/** @return {number} */ y: function(z){ return 3; }};" +
        "}");
    ObjectType xType = ObjectType.cast(lastLocalScope.getVar("x").getType());
    assertEquals(
        "function (?): number",
         xType.getPropertyType("y").toString());
    assertEquals(
        "{y: function (?): number}",
        xType.toString());
  }

  public void testDeclaredObjectLitProperty4() throws Exception {
    testSame("var x = {y: 5, /** @type {number} */ z: 3};");
    ObjectType xType = ObjectType.cast(globalScope.getVar("x").getType());
    assertEquals(
        "number", xType.getPropertyType("y").toString());
    assertFalse(xType.isPropertyTypeDeclared("y"));
    assertTrue(xType.isPropertyTypeDeclared("z"));
    assertEquals(
        "{y: number, z: number}",
        xType.toString());
  }

  public void testDeclaredObjectLitProperty5() throws Exception {
    testSame("var x = {/** @type {number} */ prop: 3};" +
             "function f() { var y = x.prop; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("number", yType.toString());
  }

  public void testDeclaredObjectLitProperty6() throws Exception {
    testSame("var x = {/** This is JsDoc */ prop: function(){}};");
    Var prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertEquals("function (): undefined", propType.toString());
    assertFalse(prop.isTypeInferred());
    assertFalse(
        ObjectType.cast(globalScope.getVar("x").getType())
        .isPropertyTypeInferred("prop"));
  }

  public void testInferredObjectLitProperty1() throws Exception {
    testSame("var x = {prop: 3};");
    Var prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertEquals("number", propType.toString());
    assertTrue(prop.isTypeInferred());
    assertTrue(
        ObjectType.cast(globalScope.getVar("x").getType())
        .isPropertyTypeInferred("prop"));
  }

  public void testInferredObjectLitProperty2() throws Exception {
    testSame("var x = {prop: function(){}};");
    Var prop = globalScope.getVar("x.prop");
    JSType propType = prop.getType();
    assertEquals("function (): undefined", propType.toString());
    assertTrue(prop.isTypeInferred());
    assertTrue(
        ObjectType.cast(globalScope.getVar("x").getType())
        .isPropertyTypeInferred("prop"));
  }

  public void testDeclaredConstType1() throws Exception {
    testSame(
        "/** @const */ var x = 3;" +
        "function f() { var y = x; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("number", yType.toString());
  }

  public void testDeclaredConstType2() throws Exception {
    testSame(
        "/** @const */ var x = {};" +
        "function f() { var y = x; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("{}", yType.toString());
  }

  public void testDeclaredConstType3() throws Exception {
    testSame(
        "/** @const */ var x = {};" +
        "/** @const */ x.z = 'hi';" +
        "function f() { var y = x.z; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("string", yType.toString());
  }

  public void testDeclaredConstType4() throws Exception {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.z = 'hi';" +
        "function f() { var y = (new Foo()).z; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("string", yType.toString());

    ObjectType fooType =
        ((FunctionType) globalScope.getVar("Foo").getType()).getInstanceType();
    assertTrue(fooType.isPropertyTypeDeclared("z"));
  }

  public void testDeclaredConstType5() throws Exception {
    testSame(
        "/** @const */ var goog = goog || {};" +
        "/** @const */ var foo = goog || {};" +
        "function f() { var y = goog; var z = foo; }");
    JSType yType = lastLocalScope.getVar("y").getType();
    assertEquals("{}", yType.toString());

    JSType zType = lastLocalScope.getVar("z").getType();
    assertEquals("?", zType.toString());
  }

  public void testBadCtorInit1() throws Exception {
    testSame("/** @constructor */ var f;", CTOR_INITIALIZER);
  }

  public void testBadCtorInit2() throws Exception {
    testSame("var x = {}; /** @constructor */ x.f;", CTOR_INITIALIZER);
  }

  public void testBadIfaceInit1() throws Exception {
    testSame("/** @interface */ var f;", IFACE_INITIALIZER);
  }

  public void testBadIfaceInit2() throws Exception {
    testSame("var x = {}; /** @interface */ x.f;", IFACE_INITIALIZER);
  }

  public void testFunctionInHook() throws Exception {
    testSame("/** @param {number} x */ var f = Math.random() ? " +
        "function(x) {} : function(x) {};");
    assertEquals("number", lastLocalScope.getVar("x").getType().toString());
  }

  public void testFunctionInAnd() throws Exception {
    testSame("/** @param {number} x */ var f = Math.random() && " +
        "function(x) {};");
    assertEquals("number", lastLocalScope.getVar("x").getType().toString());
  }

  public void testFunctionInOr() throws Exception {
    testSame("/** @param {number} x */ var f = Math.random() || " +
        "function(x) {};");
    assertEquals("number", lastLocalScope.getVar("x").getType().toString());
  }

  public void testFunctionInComma() throws Exception {
    testSame("/** @param {number} x */ var f = (Math.random(), " +
        "function(x) {});");
    assertEquals("number", lastLocalScope.getVar("x").getType().toString());
  }

  public void testDeclaredCatchExpression1() {
    testSame(
        "try {} catch (e) {}");
    // Note: "e" actually belongs to a inner scope but we don't
    // model catches as separate scopes currently.
    assertEquals(null, globalScope.getVar("e").getType());
  }

  public void testDeclaredCatchExpression2() {
    testSame(
        "try {} catch (/** @type {string} */ e) {}");
    // Note: "e" actually belongs to a inner scope but we don't
    // model catches as separate scopes currently.
    assertEquals("string", globalScope.getVar("e").getType().toString());
  }

  private JSType findNameType(final String name, Scope scope) {
    return findTypeOnMatchedNode(new Predicate<Node>() {
      @Override public boolean apply(Node n) {
        return name.equals(n.getQualifiedName());
      }
    }, scope);
  }

  private String findNameTypeStr(final String name, Scope scope) {
    return findNameType(name, scope).toString();
  }

  private JSType findTokenType(final int type, Scope scope) {
    return findTypeOnMatchedNode(new Predicate<Node>() {
      @Override public boolean apply(Node n) {
        return type == n.getType();
      }
    }, scope);
  }

  private JSType findTypeOnMatchedNode(Predicate<Node> matcher, Scope scope) {
    Node root = scope.getRootNode();
    Deque<Node> queue = Lists.newLinkedList();
    queue.push(root);
    while (!queue.isEmpty()) {
      Node current = queue.pop();
      if (matcher.apply(current) &&
          current.getJSType() != null) {
        return current.getJSType();
      }

      for (Node child : current.children()) {
        queue.push(child);
      }
    }
    return null;
  }

  private JSType getNativeType(JSTypeNative type) {
    return registry.getNativeType(type);
  }

  private ObjectType getNativeObjectType(JSTypeNative type) {
    return (ObjectType) registry.getNativeType(type);
  }
}
