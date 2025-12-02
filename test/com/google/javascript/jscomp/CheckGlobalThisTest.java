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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link CheckGlobalThis}. */
@RunWith(JUnit4.class)
public final class CheckGlobalThisTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableParseTypeInfo();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler, new CheckGlobalThis(compiler));
  }

  private void testFailure(String js) {
    testWarning(js, CheckGlobalThis.GLOBAL_THIS);
  }

  @Test
  public void testGlobalThis1() {
    testSame("var a = this;");
  }

  @Test
  public void testGlobalThis2() {
    testFailure("this.foo = 5;");
  }

  @Test
  public void testGlobalThis3() {
    testFailure("this[foo] = 5;");
  }

  @Test
  public void testGlobalThis4() {
    testFailure("this['foo'] = 5;");
  }

  @Test
  public void testGlobalThis5() {
    testFailure("(a = this).foo = 4;");
  }

  @Test
  public void testGlobalThis6() {
    testSame("a = this;");
  }

  @Test
  public void testGlobalThis7() {
    testFailure("var a = this.foo;");
  }

  @Test
  public void testStaticFunction1() {
    testSame("function a() { return this; }");
  }

  @Test
  public void testStaticFunction2() {
    testFailure("function a() { this.complex = 5; }");
  }

  @Test
  public void testStaticFunction3() {
    testSame("var a = function() { return this; }");
  }

  @Test
  public void testStaticFunction4() {
    testFailure("var a = function() { this.foo.bar = 6; }");
  }

  @Test
  public void testStaticFunction5() {
    testSame("function a() { return function() { return this; } }");
  }

  @Test
  public void testStaticFunction6() {
    testSame("function a() { return function() { this.x = 8; } }");
  }

  @Test
  public void testStaticFunction7() {
    testSame("var a = function() { return function() { this.x = 8; } }");
  }

  @Test
  public void testStaticFunction8() {
    testFailure("var a = function() { return this.foo; };");
  }

  @Test
  public void testConstructor1() {
    testSame("/** @constructor */function A() { this.m2 = 5; }");
  }

  @Test
  public void testConstructor2() {
    testSame("/** @constructor */var A = function() { this.m2 = 5; }");
  }

  @Test
  public void testConstructor3() {
    testSame("/** @constructor */a.A = function() { this.m2 = 5; }");
  }

  @Test
  public void testInterface1() {
    testSame("/** @interface */function A() { /** @type {string} */ this.m2; }");
  }

  @Test
  public void testOverride1() {
    testSame(
        """
        /** @constructor */function A() { } var a = new A();
        /** @override */ a.foo = function() { this.bar = 5; };
        """);
  }

  @Test
  public void testThisJSDoc1() {
    testSame("/** @this {whatever} */function h() { this.foo = 56; }");
  }

  @Test
  public void testThisJSDoc2() {
    testSame("/** @this {whatever} */var h = function() { this.foo = 56; }");
  }

  @Test
  public void testThisJSDoc3() {
    testSame("/** @this {whatever} */foo.bar = function() { this.foo = 56; }");
  }

  @Test
  public void testThisJSDoc4() {
    testSame("/** @this {whatever} */function f() { this.foo = 56; }");
  }

  @Test
  public void testThisJSDoc5() {
    testSame("function a() { /** @this {x} */function f() { this.foo = 56; } }");
  }

  @Test
  public void testMethod1() {
    testSame("A.prototype.m1 = function() { this.m2 = 5; }");
  }

  @Test
  public void testMethod2() {
    testSame("a.B.prototype.m1 = function() { this.m2 = 5; }");
  }

  @Test
  public void testMethod3() {
    testSame("a.b.c.D.prototype.m1 = function() { this.m2 = 5; }");
  }

  @Test
  public void testMethod4() {
    testSame("a.prototype['x' + 'y'] =  function() { this.foo = 3; };");
  }

  @Test
  public void testPropertyOfMethod() {
    testFailure(
        """
        a.protoype.b = {};
        a.prototype.b.c = function() { this.foo = 3; };
        """);
  }

  @Test
  public void testStaticMethod1() {
    testFailure("a.b = function() { this.m2 = 5; }");
  }

  @Test
  public void testStaticMethod2() {
    testSame("a.b = function() { return function() { this.m2 = 5; } }");
  }

  @Test
  public void testStaticMethod3() {
    testSame("a.b.c = function() { return function() { this.m2 = 5; } }");
  }

  @Test
  public void testMethodInStaticFunction() {
    testSame("function f() { A.prototype.m1 = function() { this.m2 = 5; } }");
  }

  @Test
  public void testStaticFunctionInMethod1() {
    testSame("A.prototype.m1 = function() { function me() { this.m2 = 5; } }");
  }

  @Test
  public void testStaticFunctionInMethod2() {
    testSame(
        """
        A.prototype.m1 = function() {
          function me() {
            function myself() {
              function andI() { this.m2 = 5; } } } }
        """);
  }

  @Test
  public void testInnerFunction1() {
    testFailure("function f() { function g() { return this.x; } }");
  }

  @Test
  public void testInnerFunction2() {
    testFailure("function f() { var g = function() { return this.x; } }");
  }

  @Test
  public void testInnerFunction3() {
    testFailure("function f() { var x = {}; x.y = function() { return this.x; } }");
  }

  @Test
  public void testInnerFunction4() {
    testSame("function f() { var x = {}; x.y(function() { return this.x; }); }");
  }

  @Test
  public void testIssue182a() {
    testFailure("var NS = {read: function() { return this.foo; }};");
  }

  @Test
  public void testIssue182b() {
    testFailure("var NS = {write: function() { this.foo = 3; }};");
  }

  @Test
  public void testIssue182c() {
    testFailure("var NS = {}; NS.write2 = function() { this.foo = 3; };");
  }

  @Test
  public void testIssue182d() {
    testSame(
        """
        function Foo() {}
        Foo.prototype = {write: function() { this.foo = 3; }};
        """);
  }

  @Test
  public void testLendsAnnotation1() {
    testFailure(
        """
        /** @constructor */ function F() {}
        dojo.declare(F, {foo: function() { return this.foo; }});
        """);
  }

  @Test
  public void testLendsAnnotation2() {
    testFailure(
        """
        /** @constructor */ function F() {}
        dojo.declare(F, /** @lends {F.bar} */ (
            {foo: function() { return this.foo; }}));
        """);
  }

  @Test
  public void testLendsAnnotation3() {
    testSame(
        """
        /** @constructor */ function F() {}
        dojo.declare(F, /** @lends {F.prototype} */ (
            {foo: function() { return this.foo; }}));
        """);
  }

  @Test
  public void testSuppressWarning() {
    testFailure("var x = function() { this.complex = 5; };");
  }

  @Test
  public void testArrowFunction1() {
    testFailure("var a = () => this.foo;");
  }

  @Test
  public void testArrowFunction2() {
    testFailure("(() => this.foo)();");
  }

  @Test
  public void testArrowFunction3() {
    testFailure(
        """
        function Foo() {}
        Foo.prototype.getFoo = () => this.foo;
        """);
  }

  @Test
  public void testArrowFunction4() {
    testFailure(
        """
        function Foo() {}
        Foo.prototype.setFoo = (f) => { this.foo = f; };
        """);
  }

  @Test
  public void testInnerFunctionInClassMethod1() {
    // TODO(user): It would be nice to warn for using 'this' here
    testSame(
        """
        function Foo() {}
        Foo.prototype.init = function() {
          button.addEventListener('click', function () {
            this.click();
          });
        }
        Foo.prototype.click = function() {}
        """);
  }

  @Test
  public void testInnerFunctionInClassMethod2() {
    // TODO(user): It would be nice to warn for using 'this' here
    testSame(
        """
        function Foo() {
          var x = function() {
            button.addEventListener('click', function () {
              this.click();
            });
          }
        }
        """);
  }

  @Test
  public void testInnerFunctionInEs6ClassMethod() {
    // TODO(user): It would be nice to warn for using 'this' here
    testSame(
        """
        class Foo {
          constructor() {
            button.addEventListener('click', function () {
              this.click();
            });
          }
          click() {}
        }
        """);
  }

  @Test
  public void testStaticBlockThis() {
    testSame("class Foo { static {var x = this.y;} }");
    testSame("class Foo {static {this.x = 2; }}");
    testSame("class Foo {static {this[this.x] = 3;}}");
    testSame(
        """
        class Foo {
          static {
            function g() {
              return this.f() + 1;
            }
            var y = g() + 1;
          }
          static f() {return 1;}
        }
        """);
    testSame(
        """
        class Foo {
          static {
            button.addEventListener('click', function () {
              this.click();
            });
          }
          static click() {}
        }
        """);
  }

  @Test
  public void testES6ClassStaticMethodThis() {
    testSame("class Foo { static h() {var x = this.y;} }");
    testSame("class Foo {static h() {this.x = 2; }}");
    testSame("class Foo {static h() {this[this.x] = 3;}}");
    testSame(
        """
        class Foo {
          static h() {
            function g() {
              return this.f() + 1;
            }
            var y = g() + 1;
          }
          static f() {return 1;}
        }
        """);
    testSame(
        """
        class Foo {
          static h() {
            button.addEventListener('click', function () {
              this.click();
            });
          }
          static click() {}
        }
        """);
  }

  // Note: No static initializer block tests because they are always removed by Es6NormalizeClasses.

  @Test
  public void testES6ClassField() {
    testSame(
        """
        class Clazz {
          x = 1;
          y = this.x;
        }
        """);

    testSame(
        """
        class Clazz {
          static x = 1;
          static y = this.x;
        }
        """);
  }

  @Test
  public void testES6ClassComputedProperty_thisInComputed() {
    testFailure(
        """
        class Clazz {
          [this.val] = 1;
        }
        """);
    testFailure(
        """
        class Clazz {
          static [this.val] = 1;
        }
        """);

    testFailure(
        """
        class Clazz {
          [this.val]() {}
        }
        """);
    testFailure(
        """
        class Clazz {
          static [this.val]() {}
        }
        """);

    testFailure(
        """
        class Clazz {
          get [this.val]() {
            return 1;
          }
        }
        """);
    testFailure(
        """
        class Clazz {
          static get [this.val]() {
            return 1;
          }
        }
        """);

    testFailure(
        """
        class Clazz {
          set [this.val](x) {}
        }
        """);
    testFailure(
        """
        class Clazz {
          static set [this.val](x) {}
        }
        """);
  }

  @Test
  public void testES6ClassComputedProperty_thisInBody() {
    testSame(
        """
        class Clazz {
          x = 1;
          ['prop'] = this.x;
        }
        """);
    testSame(
        """
        class Clazz {
          static x = 1;
          static ['prop'] = this.x;
        }
        """);

    testSame(
        """
        class Clazz {
          x = 1;
          ['prop']() {
            this.x = 2;
          }
        }
        """);
    testSame(
        """
        class Clazz {
          static x = 1;
          static ['prop']() {
            this.x = 2;
          }
        }
        """);

    testSame(
        """
        class Clazz {
          x = 1
          get ['prop']() {
            return this.x;
          }
        }
        """);
    testSame(
        """
        class Clazz {
          static x = 1;
          static get ['prop']() {
            return this.x;
          }
        }
        """);

    testSame(
        """
        class Clazz {
          x = 1;
          set ['prop'](x) {
            this.x = x;
          }
        }
        """);
    testSame(
        """
        class Clazz {
          static x = 1;
          static set ['prop'](x) {
            this.x = x;
          }
        }
        """);
  }

  @Test
  public void testFunctionWithThisTypeAnnotated() throws Exception {
    testSame(
        """
        /**
         * @type {function(this:{hello:string})}
         */
        function test() {
          console.log(this.hello)
        }
        """);
  }

  @Test
  public void testFunctionWithoutThisTypeAnnotated() throws Exception {
    testFailure(
        """
        /**
         * @type {function()}
         */
        function test() {
          console.log(this.hello)
        }
        """);
  }
}
