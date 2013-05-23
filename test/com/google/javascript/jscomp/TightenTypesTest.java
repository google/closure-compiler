/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ConcreteType.ConcreteFunctionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteInstanceType;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.TightenTypes.ConcreteSlot;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

/**
 * Unit test for the TightenTypes pass.
 *
 */
public class TightenTypesTest extends CompilerTestCase {
  private TightenTypes tt;

  public TightenTypesTest() {
    parseTypeInfo = true;
    enableTypeCheck(CheckLevel.WARNING);
    enableNormalize(true);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return (tt = new TightenTypes(compiler));
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    return new CompilerOptions(); // no missing properties check
  }

  public void testTopLevelVariables() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "var a = new Foo();\n"
             + "var b = a;\n");

    assertTrue(getType("Foo").isFunction());
    assertTrue(getType("a").isInstance());
    assertType("function (this:Foo): ()", getType("Foo"));
    assertType("Foo", getType("a"));
    assertType("Foo", getType("b"));

    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "var a = new Foo();\n"
             + "a = new Bar();\n"
             + "var b = a;\n");

    assertTrue(getType("a").isUnion());
    assertType("(Bar,Foo)", getType("a"));
    assertType("Bar", getType("b"));
  }

  public void testNamespacedVariables() {
    testSame("var goog = goog || {}; goog.foo = {};\n"
             + "/** @constructor */ goog.foo.Foo = function() {};\n"
             + "goog.foo.Foo.prototype.blah = function() {};\n"
             + "/** @constructor */ goog.foo.Bar = function() {};\n"
             + "goog.foo.Bar.prototype.blah = function() {};\n"
             + "function bar(a) { a.blah(); }\n"
             + "var baz = bar;\n"
             + "bar(new goog.foo.Foo);\n"
             + "baz(new goog.foo.Bar);\n");

    assertType("(goog.foo.Bar,goog.foo.Foo)", getParamType(getType("bar"), 0));
    assertType("(goog.foo.Bar,goog.foo.Foo)", getParamType(getType("baz"), 0));
  }

  public void testReturnSlot() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "function bar() {\n"
             + "  var a = new Foo();\n"
             + "  return a;\n"
             + "}\n"
             + "var b = bar();\n");

    assertType("Foo", getType("b"));
  }

  public void testParameterSlots() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "function bar(a, b) {}\n"
             + "bar(new Foo, new Foo);\n"
             + "bar(new Bar, null);\n");

    assertType("(Bar,Foo)", getParamType(getType("bar"), 0));
    assertType("Foo", getParamType(getType("bar"), 1));
    assertNull(getParamVar(getType("bar"), 2));
  }

  public void testAliasedFunction() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "function bar(a) {}\n"
             + "var baz = bar;\n"
             + "bar(new Foo);\n"
             + "baz(new Bar);\n");

    assertType("(Bar,Foo)", getParamType(getType("bar"), 0));
    assertType("(Bar,Foo)", getParamType(getType("baz"), 0));
  }

  public void testCatchStatement() {
    testSame(BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES,
             "/** @constructor */ function Bar() {}\n"
             + "function bar() { try { } catch (e) { return e; } }\n"
             + "/** @constructor\n@extends{Error}*/ function ID10TError() {}\n"
             + "var a = bar(); throw new ID10TError();\n", null, null);

    assertType("(Error,EvalError,ID10TError,RangeError,ReferenceError,"
        + "SyntaxError,TypeError,URIError)", getType("a"));
  }

  public void testConstructorParameterSlots() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "/** @constructor */ function Baz(a) {}\n"
             + "new Baz(new Foo);\n"
             + "new Baz(new Bar);\n");

    assertType("(Bar,Foo)", getParamType(getType("Baz"), 0));
  }

  public void testCallSlot() {
    testSame("function foo() {}\n"
             + "function bar() {}\n"
             + "function baz() {}\n"
             + "var a = foo;\n"
             + "a = bar;\n"
             + "a();\n");

    assertTrue(isCalled(getType("foo")));
    assertTrue(isCalled(getType("bar")));
    assertFalse(isCalled(getType("baz")));
  }

  public void testObjectLiteralTraversal() {
    testSame("var foo = function() {}\n"
             + "function bar() { return { 'a': foo()} };\n"
             + "bar();");
    assertTrue(isCalled(getType("foo")));
   }

  public void testThis() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "Foo.prototype.foo = function() { return this; }\n"
             + "var a = new Foo();\n"
             + "var b = a.foo();\n");

    assertType("Foo", getType("a"));
    assertType("Foo", getType("b"));
  }

  public void testAssign() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "var a = new Foo();\n"
             + "var b = a = new Bar();\n");

    assertType("(Bar,Foo)", getType("a"));
    assertType("Bar", getType("b"));
  }

  public void testComma() {
    testSame("/** @constructor */ function Foo() {b=new Foo()}\n"
             + "var b;"
             + "/** @constructor */ function Bar() {}\n"
             + "var a = (new Foo, new Bar);\n");

    assertType("Bar", getType("a"));
    assertType("Foo", getType("b"));
  }

  public void testAnd() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "var a = (new Foo && new Bar);\n");

    assertType("Bar", getType("a"));
  }

  public void testOr() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "/** @type {Foo} */ var f = new Foo();\n"
             + "/** @type {Bar} */ var b = new Bar();\n"
             + "var a = (f || b);\n");

    assertType("(Bar,Foo)", getType("a"));
  }

  public void testHook() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "var a = (1+1 == 2) ? new Foo : new Bar;\n");

    assertType("(Bar,Foo)", getType("a"));
  }

  public void testFunctionLiteral() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "var a = (function() { return new Foo; })();\n");

    assertType("Foo", getType("a"));
  }

  public void testNameLookup() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "var a = new Foo;\n"
             + "var b = (function() { return a; })();\n");

    assertType("Foo", getType("a"));
    assertType("Foo", getType("b"));
  }

  public void testGetProp() {
    testSame("/** @constructor */ function Foo() {\n"
             + "  this.foo = new A();\n"
             + "}\n"
             + "/** @constructor */ function Bar() {\n"
             + "  this.foo = new B();\n"
             + "}\n"
             + "/** @constructor */ function Baz() {}\n"
             + "/** @constructor */ function A() {}\n"
             + "/** @constructor */ function B() {}\n"
             // add the casts to make the JSType a union with null
             + "/** @type {Foo} */ var foo = new Foo();\n"
             + "/** @type {Bar} */ var bar = new Bar();\n"
             + "/** @type {Baz} */ var baz = new Baz();\n" // has no 'foo'
             + "var a = foo || bar || baz\n"
             + "var b = a.foo;\n");

    assertType("(A,B)", getType("b"));
  }

  public void testGetPrototypeProperty() {
    testSame("/** @constructor */ function Foo() {};\n"
             + "/** @constructor */ function Bar() {};\n"
             + "Bar.prototype.a = new Foo();\n"
             + "var a = Bar.prototype.a;\n");

    assertType("Foo", getType("a"));
  }

  public void testGetElem() {
    testSame(
        "/**\n"
        + " * @constructor\n"
        + " * @extends {Object}\n"
        + " * @param {...*} var_args\n"
        + " * @return {!Array}\n"
        + " */\n"
        + "function Array(var_args) {}\n",
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "var a = [];\n"
        + "a[0] = new Foo;\n"
        + "a[1] = new Bar;\n"
        + "var b = a[0];\n"
        + "var c = [new Foo, new Bar];\n", null);

    assertType("Array", getType("a"));
    assertType("(Array,Bar,Foo)", getType("b"));
    assertType("Array", getType("c"));

    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "/** @constructor */ function Baz() {\n"
             + "  this.arr = [];\n"
             + "}\n"
             + "var b = new Baz;\n"
             + "b.arr[0] = new Foo;\n"
             + "b.arr[1] = new Bar;\n"
             + "var c = b.arr;\n");

    assertType("Array", getType("c"));
  }

  public void testGetElem3() {
    testSame(BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES,
             "/** @constructor */ function Foo() {}\n"
             + "/** @constructor */ function Bar() {}\n"
             + "/** @constructor */ function Baz() {\n"
             + "  this.arr = [];\n"
             + "}\n"
             + "function foo(anarr) {"
             + "}\n"
             + "var ar = [];\n"
             + "foo(ar);\n", null);

    assertType("Array", getType("ar"));
  }

  public void testScopeDiscovery() {
    testSame("function spam() {}\n"
             + "function foo() {}\n"
             + "function bar() {\n"
             + "  return function() { foo(); };\n"
             + "}"
             + "function baz() {\n"
             + "  return function() { bar()(); };\n"
             + "}"
             + "baz()()();\n");

    assertFalse(isCalled(getType("spam")));
    assertTrue(isCalled(getType("foo")));
  }

  public void testSheqDiscovery() {
    testSame("function spam() {}\n"
             + "/** @constructor */\n"
             + "function Foo() {}\n"
             + "Foo.prototype.foo1 = function() { f1(); }\n"
             + "Foo.prototype.foo2 = function() { f2(); }\n"
             + "Foo.prototype.foo3 = function() { f3(); }\n"
             + "function baz(a) {\n"
             + "  a === null || a instanceof Foo ?\n"
             + "  Foo.prototype.foo1.call(this) :\n"
             + "  Foo.prototype.foo2.call(this);\n"
             + "}\n"
             + "function f1() {}\n"
             + "function f2() {}\n"
             + "function f3() {}\n"
             + "baz(3);\n");

    assertFalse(isCalled(getType("spam")));
    assertFalse(isCalled(getType("f3")));
    assertTrue(isCalled(getType("f1")));
    assertTrue(isCalled(getType("f2")));
  }

  public void testSubclass() {
    testSame("/** @constructor */\n"
             + "function Foo() {}\n"
             + "Foo.prototype.foo = function() { return this.bar; };\n"
             + "Foo.prototype.bar = function() { return new A(); };\n"
             + "/**\n"
             + " * @constructor\n"
             + " * @extends Foo\n"
             + " */\n"
             + "function Bar() {}\n"
             + "/** @override */\n"
             + "Bar.prototype.bar = function() { return new B(); };\n"
             + "/** @constructor */ function A() {}\n"
             + "/** @constructor */ function B() {}\n"
             + "var a = (new Foo()).foo()();\n"
             + "a = (new Bar()).foo()();\n");

    ConcreteType fooType =
        getPropertyType(getFunctionPrototype(getType("Foo")), "foo");
    assertType("(Bar,Foo)", getThisType(fooType));
    assertType("(A,B)", getType("a"));

    testSame("/** @constructor */\n"
             + "function Foo() {}\n"
             + "Foo.prototype.foo = function() { return this.bar; };\n"
             + "Foo.prototype.bar = function() { return new A(); };\n"
             + "/**\n"
             + " * @constructor\n"
             + " * @extends Foo\n"
             + " */\n"
             + "function Bar() {}\n"
             + "/** @override */\n"
             + "Bar.prototype.bar = function() { return new B(); };\n"
             + "/** @constructor */ function A() {}\n"
             + "/** @constructor */ function B() {}\n"
             + "var a = (new Bar()).foo()();\n");

    fooType = getPropertyType(getFunctionPrototype(getType("Foo")), "foo");
    assertType("Bar", getThisType(fooType));
    assertType("B", getType("a"));
  }

  public void testArrayAssignments() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "var a = [];\n"
             + "function foo() { return []; }\n"
             + "(a.length == 0 ? a : foo())[0] = new Foo;\n"
             + "var b = a[0];\n"
             + "var c = foo()[0];\n");

    assertType("(Array,Foo)", getType("b"));
    assertType("(Array,Foo)", getType("c"));
  }

  public void testAllPropertyReference() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "Foo.prototype.prop = function() { this.prop2(); }\n"
             + "Foo.prototype.prop2 = function() { b = new Foo; }\n"
             + "var a = new Foo;\n"
             + "a = [][0];\n"
             + "function fun(a) {\n"
             + "  return a.prop();\n"
             + "}\n"
             + "var b;\n"
             + "fun(a);\n"
             );

    assertType("Foo", getType("a"));
    assertType("Foo", getType("b"));
  }

  public void testCallFunction() {
    testSame("/** @constructor */ function Foo() { this.a = new A; }\n"
             + "/** @constructor \n @extends Foo */ function Bar() {\n"
             + "  Foo.call(this);\n"
             + "}\n"
             + "/** @constructor */ function A() {};\n"
             + "new Bar;");

    assertTrue(isCalled(getType("Foo")));
    assertTrue(isCalled(getType("A")));
    ConcreteType fooType = getThisType(getType("Foo"));
    assertType("A", getPropertyType(fooType, "a"));

    ConcreteType barType = getThisType(getType("Bar"));
    assertType("A", getPropertyType(barType, "a"));
  }

  public void testCallFunctionWithArgs() {
    testSame("/** @constructor */ function Foo(o) { this.a = o; }\n"
             + "/** @constructor \n @extends Foo */ function Bar() {\n"
             + "  Foo.call(this, new A());\n"
             + "}\n"
             + "/** @constructor */ function A() {};\n"
             + "var b = new Bar;");

    assertTrue(isCalled(getType("Foo")));
    assertTrue(isCalled(getType("A")));

    ConcreteType barType = getThisType(getType("Bar"));
    assertType("A", getPropertyType(barType, "a"));

    ConcreteType fooType = getThisType(getType("Foo"));
    assertType("A", getPropertyType(fooType, "a"));
  }

  public void testCallPrototypeFunction() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "Foo.prototype.a = function() { return new A; }\n"
             + "Foo.prototype.a = function() { return new A; };\n"
             + "/** @constructor \n @extends Foo */ function Bar() {}\n"
             + "/** @override */"
             + "Bar.prototype.a = function() { return new B; };\n"
             + "/** @constructor */ function A() {};\n"
             + "/** @constructor */ function B() {};\n"
             + "var ret = Foo.prototype.a.call(new Bar);");

    assertType("A", getType("ret"));
  }

  public void testCallPrototypeFunctionWithArgs() {
    testSame("/** @constructor */ function Foo() { this.p = null }\n"
             + "Foo.prototype.set = function(arg) { this.p = arg; };\n"
             + "Foo.prototype.get = function() { return this.p; };\n"
             + "/** @constructor */ function A() {};\n"
             + "Foo.prototype.set.call(new Foo, new A);\n"
             + "var ret = Foo.prototype.get.call(new Foo);");

    ConcreteType fooP = getFunctionPrototype(getType("Foo"));
    ConcreteFunctionType gFun = getPropertyType(fooP, "get").toFunction();
    ConcreteFunctionType sFun = getPropertyType(fooP, "set").toFunction();

    assertTrue(isCalled(sFun));
    assertTrue(isCalled(gFun));
    assertTrue(isCalled(getType("A")));
    assertType("A", getType("ret"));
  }

  public void testSetTimeout() {
    testSame("/** @constructor */ function Window() {};\n"
             + "Window.prototype.setTimeout = function(f, t) {};\n"
             + "/** @type Window */ var window;",
             "/** @constructor*/ function A() {}\n"
             + "A.prototype.handle = function() { foo(); };\n"
             + "function foo() {}\n"
             + "window.setTimeout((new A).handle, 3);", null);

    assertTrue(isCalled(getType("foo")));
  }

  public void testExternType() {
    testSame("/** @constructor */ function T() {};\n"
             + "/** @constructor */ function Ext() {};\n"
             + "/** @return {T} */\n"
             + "Ext.prototype.getT = function() {};\n"
             + "/** @type T */ Ext.prototype.prop;\n"
             + "/** @type Ext */ var ext;",
             "var b = ext.getT();\n"
             + "var p = ext.prop;", null);

    assertType("Ext", getType("ext"));
    assertType("T", getType("b"));
    assertType("T", getType("p"));
  }

  public void testExternSubTypes() {
    testSame("/** @constructor */ function A() {};\n"
             + "/** @constructor \n@extends A */ function B() {};\n"
             + "/** @constructor \n@extends A */ function C() {};\n"
             + "/** @constructor \n@extends B */ function D() {};\n"
             + "/** @constructor */ function Ext() {};\n"
             + "/** @type A */ Ext.prototype.a;\n"
             + "/** @type B */ Ext.prototype.b;\n"
             + "/** @type D */ Ext.prototype.d;\n"
             + "/** @return {A} */ Ext.prototype.getA = function() {};\n"
             + "/** @return {B} */ Ext.prototype.getB = function() {};\n",
             "var a = (new Ext).a;\n"
             + "var a2 = (new Ext).getA();\n"
             + "var b = (new Ext).b;\n"
             + "var b2 = (new Ext).getB();\n"
             + "var d = (new Ext).d;\n", null);

    assertType("(A,B,C,D)", getType("a"));
    assertType("(A,B,C,D)", getType("a2"));
    assertType("(B,D)", getType("b"));
    assertType("(B,D)", getType("b2"));
    assertType("D", getType("d"));
  }

  public void testExternSubTypesForObject() {
    testSame(BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES
             + "/** @constructor */ function A() {};\n"
             + "/** @constructor \n@extends A */ function B() {};\n"
             + "/** @return {Object} */ "
             + "Object.prototype.eval = function(code) {};\n"
             + "/** @type {Object} */\n"
             + "A.prototype.a;\n"
             + "/** @return {Object} */\n"
             + "A.prototype.b = function(){};\n",
             "var a = (new A).b()", null, null);
    assertType("(A,ActiveXObject,Array,B,Boolean,Date,Error,EvalError,"
               + "Function,Number,Object,"
               + "RangeError,ReferenceError,RegExp,String,SyntaxError,"
               + "TypeError,URIError)", getType("a"));
  }

  public void testImplicitPropCall() {
    testSame("/** @constructor */ function Window() {};\n"
             + "/** @param {function()} f \n@param {number} d */\n"
             + "Window.prototype.setTimeout = function(f, d) {};",
             "function foo() {};\n"
             + "(new Window).setTimeout(foo, 20);", null);

    assertTrue(isCalled(getType("foo")));
  }

  public void testImplicitPropCallWithArgs() {
    testSame("/** @constructor */ function Window() {};\n"
             + "/** @constructor */ function EventListener() {};\n"
             + "/** @param {string} t\n"
             + "  * @param {EventListener|function(Event)} f */\n"
             + "Window.prototype.addEventListener = function(t, f) {};\n"
             + "/** @constructor */ function Event() {};",
             "function foo(evt) {};\n"
             + "(new Window).addEventListener('click', foo);", null);

    assertTrue(isCalled(getType("foo")));
    assertType("Event", getParamType(getType("foo"), 0));
  }

  public void testUntypedImplicitCallFromProperty() {
    testSame("/** @constructor */ function Element() {};\n"
             + "/** @type {?function(Event)} */Element.prototype.onclick;\n"
             + "/** @constructor */ function Event() {};"
             + "/** @return {Event} */ Event.prototype.erv;",
             " function foo(evt) { return bar(evt); };\n"
             + "function bar(a) { return a.type() }\n"
             + "/** @type Object */ var ar = new Element;\n"
             + "ar.onclick = foo;", null);

    assertTrue(isCalled(getType("foo")));
    assertTrue(isCalled(getType("bar")));
    assertType("Event", getParamType(getType("foo"), 0));
    assertType("Event", getParamType(getType("bar"), 0));
    assertType("Element", getThisType(getType("foo").toFunction()));
  }

  public void testImplicitCallFromProperty() {
    testSame("/** @constructor */ function Element() {};\n"
             + "/** @type {function(this:Element,Event)} */\n"
             + "Element.prototype.onclick;\n"
             + "/** @constructor */ function Event() {};",
             "function foo(evt) {};\n"
             + "(new Element).onclick = foo;", null);

    assertTrue(isCalled(getType("foo")));
    assertType("Event", getParamType(getType("foo"), 0));
    assertType("Element", getThisType(getType("foo").toFunction()));
  }

  public void testImplicitCallFromPropertyOfUnion() {
    testSame("/** @constructor */ function Element() {};\n"
             + "/** @type {function(this:Element,Event)} */\n"
             + "Element.prototype.onclick;\n"
             + "/** @constructor */ function Event() {};",
             "function foo(evt) {};\n"
             + "(new Element).onclick = foo;", null);

    assertTrue(isCalled(getType("foo")));
    assertType("Event", getParamType(getType("foo"), 0));
    assertType("Element", getThisType(getType("foo").toFunction()));
  }

  public void testImplicitCallFromPropertyOfAllType() {
    testSame("/** @constructor */ function Element() {};\n"
             + "/** @type {function(this:Element,Event)} */\n"
             + "Element.prototype.onclick;\n"
             + "/** @constructor */ function Event() {};",
             "function foo(evt) {};\n"
             + "var elems = [];\n"
             + "var elem = elems[0];\n" // assign it the all type
             + "elem.onclick = foo;", null);

    assertTrue(isCalled(getType("foo")));
    assertType("Event", getParamType(getType("foo"), 0));
    assertType("Element", getThisType(getType("foo").toFunction()));
  }

  public void testRestrictToCast() {
    testSame("/** @constructor */ function Foo() {};\n"
             + "var a = [];\n"
             + "var foo = /** @type {Foo} */ (a[0]);\n"
             + "var u = a[0];\n"
             + "new Foo");

    assertType("Foo", getType("foo"));
    assertType("(Array,Foo)", getType("u"));
  }

  public void testRestrictToInterfaceCast() {
    testSame("/** @constructor \n @implements Int */ function Foo() {};\n"
             + "/** @interface */ function Int() {};\n"
             + "var a = [];\n"
             + "var foo = /** @type {Int} */ (a[0]);\n"
             + "new Foo");

    assertType("Foo", getType("foo"));
  }

  public void testRestrictToCastWithNonInstantiatedTypes() {
    testSame(
             "/** @constructor */ function Super() {}\n"
             + "/** @constructor \n @extends {Super} */ function Foo() {};\n"
             + "Foo.prototype.blah = function() { foofunc() };\n"
             + "/** @constructor \n @extends {Super} */ function Bar() {};\n"
             + "Bar.prototype.blah = function() { barfunc() };\n"
             + "function barfunc() {}\n"
             + "function foofunc() {}\n"
             + "var a = [];\n"
             + "var u = /** @type {Super} */ (a[0]);\n"
             + "u.blah()\n"
             + "new Foo");

    assertTrue(isCalled(getType("foofunc")));
    assertFalse(isCalled(getType("barfunc")));
    assertType("Array", getType("a"));
  }

  public void testFunctionToString() {
    testSame("/** @constructor */ function Foo() {}\n"
             + "/** @constructor \n * @extends Foo */\n"
             + "function Bar() { Foo.call(this); }\n"
             + "var a = function(a) { return new Foo; };\n;"
             + "a(new Foo);\n"
             + "a(new Bar);\n"
             + "new Bar;");

    assertType("function ((Bar,Foo)): Foo", getType("a"));
    assertType("function (this:(Bar,Foo)): ()", getType("Foo"));
    assertType("function (this:Bar): ()", getType("Bar"));
  }

  private void assertType(String expected, ConcreteType type) {
    assertEquals(expected, type.toString());
  }

  /** Returns the type of the given variable in the top-most scope. */
  private ConcreteType getType(String var) {
    assertNotNull(tt.getTopScope().getSlot(var));
    return tt.getTopScope().getSlot(var).getType();
  }

  /** Returns the variable for the given parameter of the given function. */
  private ConcreteSlot getParamVar(ConcreteType funType, int param) {
    assertTrue(funType.isFunction());
    return (ConcreteSlot)
        ((ConcreteFunctionType) funType).getParameterSlot(param);
  }

  /** Returns the type of the given parameter of the given function. */
  private ConcreteType getParamType(ConcreteType funType, int param) {
    ConcreteSlot paramVar = getParamVar(funType, param);
    return (paramVar != null) ? paramVar.getType() : ConcreteType.NONE;
  }

  /** Returns the variable for the this variable of the given function. */
  private ConcreteSlot getThisSlot(ConcreteType funType) {
    assertTrue(funType.isFunction());
    return (ConcreteSlot) ((ConcreteFunctionType) funType).getThisSlot();
  }

  /** Returns the type of the this variable of the given function. */
  private ConcreteType getThisType(ConcreteType funType) {
    return getThisSlot(funType).getType();
  }

  /** Returns the prototype type of the given function. */
  private ConcreteType getFunctionPrototype(ConcreteType funType) {
    assertTrue(funType.isFunction());
    return ((ConcreteFunctionType) funType).getPrototypeType();
  }

  /**
   * Returns the variable for the property with the give name on the given
   * instance type.
   */
  private ConcreteSlot getPropertyVar(ConcreteType instType, String name) {
    assertTrue(instType.isInstance());
    return (ConcreteSlot)
        ((ConcreteInstanceType) instType).getPropertySlot(name);
  }

  /** Returns the type of the property with the give name on the given type. */
  private ConcreteType getPropertyType(ConcreteType instType, String name) {
    return getPropertyVar(instType, name).getType();
  }

  /** Returns whether the given function is called. */
  private boolean isCalled(ConcreteType funType) {
    assertTrue(funType.isFunction());
    ConcreteSlot callVar = (ConcreteSlot)
        ((ConcreteFunctionType) funType).getCallSlot();
    return !callVar.getType().isNone();
  }
}
