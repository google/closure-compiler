/*
 * Copyright 2011 The Closure Compiler Authors.
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

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class RemoveUnusedClassPropertiesTest extends CompilerTestCase {

  private static final String EXTERNS = LINE_JOINER.join(
      "var window;",
      "function alert(a) {}",
      "var EXT = {};",
      "EXT.ext;",
      "var Object;",
      "Object.defineProperties;",
      "var foo");

  public RemoveUnusedClassPropertiesTest() {
    super(EXTERNS);
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveUnusedClassProperties(compiler, true);
  }

  public void testSimple1() {
    // A property defined on "this" can be removed
    test("this.a = 2", "2");
    test("x = (this.a = 2)", "x = 2");
    testSame("this.a = 2; x = this.a;");
  }

  public void testSimple2() {
    // A property defined on "this" can be removed, even when defined
    // as part of an expression
    test("this.a = 2, f()", "2, f()");
    test("x = (this.a = 2, f())", "x = (2, f())");
    test("x = (f(), this.a = 2)", "x = (f(), 2)");
  }

  public void testSimple3() {
    // A property defined on an object other than "this" can not be removed.
    testSame("y.a = 2");
    // and prevents the removal of the definition on 'this'.
    testSame("y.a = 2; this.a = 2");
    // Some use of the property "a" prevents the removal.
    testSame("y.a = 2; this.a = 1; alert(x.a)");
  }

  public void testObjLit() {
    // A property defined on an object other than "this" can not be removed.
    testSame("({a:2})");
    // and prevent the removal of the definition on 'this'.
    testSame("({a:0}); this.a = 1;");
    // Some use of the property "a" prevents the removal.
    testSame("x = ({a:0}); this.a = 1; alert(x.a)");
  }

  public void testExtern() {
    // A property defined in the externs is can not be removed.
    testSame("this.ext = 2");
  }

  public void testExport() {
    // An exported property can not be removed.
    testSame("this.ext = 2; window['export'] = this.ext;");
    testSame("function f() { this.ext = 2; } window['export'] = this.ext;");
  }


  public void testAssignOp1() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.x += 2", "2");
    testSame("x = (this.x += 2)");
    testSame("this.x += 2; x = this.x;");
    // But, of course, a later use prevents its removal.
    testSame("this.x += 2; x.x;");
  }

  public void testAssignOp2() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.a += 2, f()", "2, f()");
    test("x = (this.a += 2, f())", "x = (2, f())");
    testSame("x = (f(), this.a += 2)");
  }

  public void testInc1() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.x++", "0");
    testSame("x = (this.x++)");
    testSame("this.x++; x = this.x;");

    test("--this.x", "0");
    testSame("x = (--this.x)");
    testSame("--this.x; x = this.x;");
  }

  public void testInc2() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.a++, f()", "0, f()");
    test("x = (this.a++, f())", "x = (0, f())");
    testSame("x = (f(), this.a++)");

    test("--this.a, f()", "0, f()");
    test("x = (--this.a, f())", "x = (0, f())");
    testSame("x = (f(), --this.a)");
  }

  public void testJSCompiler_renameProperty() {
    // JSCompiler_renameProperty introduces a use of the property
    testSame("this.a = 2; x[JSCompiler_renameProperty('a')]");
    testSame("this.a = 2; JSCompiler_renameProperty('a')");
  }

  public void testForIn() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object is used in a FOR-IN loop
    test("this.y = 1;for (var a in x) { alert(x[a]) }",
         "1;for (var a in x) { alert(x[a]) }");
  }

  public void testObjectKeys() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object are referenced
    test("this.y = 1;alert(Object.keys(this))",
         "1;alert(Object.keys(this))");
  }

  public void testObjectReflection1() {
    // Verify reflection prevents removal.
    testSame(
        "/** @constructor */ function A() {this.foo = 1;}\n" +
        "use(goog.reflect.object(A, {foo: 'foo'}));\n");
  }

  public void testObjectReflection2() {
    // Any object literal definition prevents removal.
    // Type based removal would allow this to be removed.
    testSame(
        "/** @constructor */ function A() {this.foo = 1;}\n" +
        "use({foo: 'foo'});\n");
  }

  public void testIssue730() {
    // Partial removal of properties can causes problems if the object is
    // sealed.
    testSame(
        "function A() {this.foo = 0;}\n" +
        "function B() {this.a = new A();}\n" +
        "B.prototype.dostuff = function() {this.a.foo++;alert('hi');}\n" +
        "new B().dostuff();\n");
  }

  public void testNoRemoveSideEffect1() {
    test(
        "function A() {alert('me'); return function(){}}\n" +
        "A().prototype.foo = function() {};\n",
        "function A() {alert('me'); return function(){}}\n" +
        "A(),function(){};\n");
  }

  public void testNoRemoveSideEffect2() {
    test(
        "function A() {alert('me'); return function(){}}\n" +
        "A().prototype.foo++;\n",
        "function A() {alert('me'); return function(){}}\n" +
        "A(),0;\n");
  }

  public void testPrototypeProps1() {
    test(
        "function A() {this.foo = 1;}\n" +
        "A.prototype.foo = 0;\n" +
        "A.prototype.method = function() {this.foo++};\n" +
        "new A().method()\n",
        "function A() {1;}\n" +
        "0;\n" +
        "A.prototype.method = function() {0;};\n" +
        "new A().method()\n");
  }

  public void testPrototypeProps2() {
    // don't remove properties that are exported by convention
    testSame(
        "function A() {this._foo = 1;}\n" +
        "A.prototype._foo = 0;\n" +
        "A.prototype.method = function() {this._foo++};\n" +
        "new A().method()\n");
  }

  public void testConstructorProperty1() {
    enableTypeCheck();

    test(
        "/** @constructor */ function C() {} C.prop = 1;",
        "/** @constructor */ function C() {} 1");
  }

  public void testConstructorProperty2() {
    enableTypeCheck();

    testSame(
        "/** @constructor */ function C() {} "
        + "C.prop = 1; "
        + "function use(a) { alert(a.prop) }; "
        + "use(C)");
  }

  public void testObjectDefineProperties1() {
    enableTypeCheck();

    testSame(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{value:1}});",
            "function use(a) { alert(a.prop) };",
            "use(C)"));
  }

  public void testObjectDefineProperties2() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{value:1}});"),
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties3() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, ",
            "  {prop:{",
            "    get:function(){},",
            "    set:function(a){},",
            "}});"),
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  // side-effect in definition retains property
  public void testObjectDefineProperties4() {
    enableTypeCheck();

    testSame(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:alert('')});"));
  }

  // quoted properties retains property
  public void testObjectDefineProperties5() {
    enableTypeCheck();

    testSame(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {'prop': {value: 1}});"));
  }

  public void testObjectDefineProperties6() {
    enableTypeCheck();

    // an unknown destination object doesn't prevent removal.
    test(
        "Object.defineProperties(foo(), {prop:{value:1}});",
        "Object.defineProperties(foo(), {});");
  }

  public void testObjectDefineProperties7() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{get:function () {return new C}}});"),
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties8() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{set:function (a) {return alert(a)}}});"),
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties_used_setter_removed() {
    // TODO: Either remove fix this or document it as a limitation of advanced mode optimizations.
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{set:function (a) {alert(2)}}});",
            "C.prop = 2;"),
        LINE_JOINER.join(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});2"));
  }

}
