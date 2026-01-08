/*
 * Copyright 2012 The Closure Compiler Authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link AngularPass}. */
@RunWith(JUnit4.class)
public final class AngularPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new AngularPass(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    // enables angularPass.
    options.setAngularPass(true);
    return options;
  }

  @Test
  public void testNgInjectAddsInjectToFunctions() {
    test(
        "/** @ngInject */ function fn(a, b) {}",
        "/** @ngInject */ function fn(a, b) {} /** @public */ fn['$inject']=['a', 'b']");

    testSame("function fn(a, b) {}");
  }

  @Test
  public void testNgInjectSetVisibility() {
    test(
        "/** @ngInject */ function fn(a, b) {}",
        "/** @ngInject */ function fn(a, b) {} /** @public */ fn['$inject']=['a', 'b']");
  }

  @Test
  public void testNgInjectAddsInjectAfterGoogInherits() {
    test(
        """
        /** @ngInject @constructor */
        function fn(a, b) {}
        goog.inherits(fn, parent);
        """,
        """
        /** @ngInject @constructor */
        function fn(a, b) {}
        goog.inherits(fn, parent);
        /** @public */
        fn['$inject']=['a', 'b']
        """);

    test(
        """
        /** @ngInject @constructor */
        function fn(a, b) {}
        goog.inherits(fn, parent);
        var foo = 42;
        """,
        """
        /** @ngInject @constructor */
        function fn(a, b) {}
        goog.inherits(fn, parent);
        /** @public */
        fn['$inject']=['a', 'b'];
        var foo = 42;
        """);
  }

  @Test
  public void testNgInjectAddsInjectToProps() {
    test(
        "var ns = {}; /** @ngInject */ ns.fn = function (a, b) {}",
        """
        var ns = {}; /** @ngInject */ ns.fn = function (a, b) {};
        /** @public */ ns.fn['$inject']=['a', 'b']
        """);

    testSame("var ns = {}; ns.fn = function (a, b) {}");
  }

  @Test
  public void testNgInjectAddsInjectToNestedProps() {
    test(
        """
        var ns = {}; ns.subns = {};
        /** @ngInject */ ns.subns.fn = function (a, b) {}
        """,
        """
        var ns = {}; ns.subns = {};
        /** @ngInject */
        ns.subns.fn = function (a, b) {};
        /** @public */
        ns.subns.fn['$inject']=['a', 'b']
        """);

    testSame("var ns = {}; ns.fn = function (a, b) {}");
  }

  @Test
  public void testNgInjectAddsInjectToVars() {
    test(
        "/** @ngInject */ var fn = function (a, b) {}",
        "/** @ngInject */ var fn = function (a, b) {}; /** @public */ fn['$inject']=['a', 'b']");

    testSame("var fn = function (a, b) {}");
  }

  @Test
  public void testNgInjectAddsInjectToLet() {
    test(
        "/** @ngInject */ let fn = function (a, b) {}",
        "/** @ngInject */ let fn = function (a, b) {}; /** @public */ fn['$inject']=['a', 'b']");

    testSame("let fn = function (a, b) {}");
  }

  @Test
  public void testNgInjectAddsInjectToConst() {
    test(
        "/** @ngInject */ const fn = function (a, b) {}",
        "/** @ngInject */ const fn = function (a, b) {}; /** @public */ fn['$inject']=['a', 'b']");

    testSame("const fn = function (a, b) {}");
  }

  @Test
  public void testNgInjectAddsInjectToVarsWithChainedAssignment() throws Exception {
    test(
        "var ns = {}; /** @ngInject */ var fn = ns.func = function (a, b) {}",
        """
        var ns = {};
        /** @ngInject */
        var fn = ns.func = function (a, b) {};
        /** @public */
        fn['$inject']=['a', 'b']
        """);

    testSame("var ns = {}; var fn = ns.func = function (a, b) {}");
  }

  @Test
  public void testNgInjectInBlock() {
    test(
        """
        (function() {
          var ns = {};
          /** @ngInject */ var fn = ns.func = function (a, b) {}
        })()
        """,
        """
        (function() {
          var ns = {};
          /** @ngInject */
          var fn = ns.func = function (a, b) {};
          /** @public */
          fn['$inject']=['a', 'b']
        })()
        """);

    testSame(
        """
        (function() {
          var ns = {}; var fn = ns.func = function (a, b) {}
        })()
        """);
  }

  @Test
  public void testNgInjectAddsToTheRightBlock() {
    test(
        """
        var fn = 10;
        (function() {
          var ns = {};
          /** @ngInject */ var fn = ns.func = function (a, b) {};
        })()
        """,
        """
        var fn = 10;
        (function() {
          var ns = {};
          /** @ngInject */
          var fn = ns.func = function (a, b) {};
          /** @public */
          fn['$inject']=['a', 'b'];
        })()
        """);
  }

  @Test
  public void testNgInjectInNonBlock() {
    testError(
        """
        function fake(){};
        var ns = {};
        fake( /** @ngInject */ ns.func = function (a, b) {} )
        """,
        AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR);

    testError(
        "/** @ngInject */( function (a, b) {} )", AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR);
  }

  @Test
  public void testNgInjectNonFunction() {
    testError(
        """
        var ns = {}; ns.subns = {};
        ns.subns.fake = function(x, y){};
        /** @ngInject */ ns.subns.fake(1);
        """,
        AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError("/** @ngInject */ var a = 10", AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError("/** @ngInject */ var x", AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError(
        "class FnClass {constructor(a, b) {/** @ngInject */ this.x = 42}}",
        AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError(
        "class FnClass {constructor(a, b) {/** @ngInject */ this.x}}",
        AngularPass.INJECT_NON_FUNCTION_ERROR);
  }

  @Test
  public void testNgInjectOnGetElem() {
    testError(
        "/** @ngInject */ foo.bar['baz'] = function(a) {};",
        AngularPass.INJECTED_FUNCTION_ON_NON_QNAME);
  }

  @Test
  public void testNgInjectAddsInjectToClass() {
    testError(
        "/** @ngInject */ class FnClass {constructor(a, b) {}}",
        AngularPass.INJECT_NON_FUNCTION_ERROR);
  }

  @Test
  public void testNgInjectAddsInjectToClassConstructor() {
    test(
        "class FnClass {/** @ngInject */ constructor(a, b) {}}",
        """
        class FnClass{ /** @ngInject */ constructor(a, b){}}
        /** @public */ FnClass['$inject'] = ['a', 'b'];
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassMethod1() {
    test(
        """
        class FnClass {
          constructor(a, b) {}
          /** @ngInject */
          methodA(c, d){}
        }
        """,
        """
        class FnClass {
          constructor(a, b){}
          /** @ngInject */
          methodA(c, d){}
        }
        /** @public */
        FnClass.prototype.methodA['$inject'] = ['c','d']
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassMethod2() {
    test(
        """
        FnClass.foo = class {
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        FnClass.foo = class {
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        FnClass.foo['$inject'] = ['a','b'];
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassMethod3() {
    test(
        """
        var foo = class {
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        var foo = class {
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);

    test(
        """
        let foo = class {
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        let foo = class {
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);

    test(
        """
        const foo = class {
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        const foo = class {
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);
  }

  @Test
  public void testNgInjectAddsInjectToStaticMethod() {
    test(
        """
        class FnClass {
          constructor(a, b) {}
          /** @ngInject */
          static methodA(c, d) {}
        }
        """,
        """
        class FnClass {
          constructor(a, b) {}
          /** @ngInject */
          static methodA(c, d) {}
        }
        /** @public */
        FnClass.methodA['$inject'] = ['c','d']
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassGenerator() {
    test(
        """
        class FnClass {
          constructor(a, b) {}
          /** @ngInject */
          * methodA(c, d){}
        }
        """,
        """
        class FnClass {
          constructor(a, b){}
          /** @ngInject */
          *methodA(c, d){}
        }
        /** @public */
        FnClass.prototype.methodA['$inject'] = ['c','d']
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassMixOldStyle() {
    test(
        """
        class FnClass {
          constructor() {
            /** @ngInject */
            this.someMethod = function(a, b){}
          }
        }
        """,
        """
        class FnClass {
          constructor() {
            /** @ngInject */
            this.someMethod = function(a, b){}
            /** @public */
            this.someMethod['$inject'] = ['a','b']
          }
        }
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassWithExtraName() {
    test(
        """
        var foo = class bar{
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        var foo = class bar{
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);

    test(
        """
        let foo = class bar{
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        let foo = class bar{
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);

    test(
        """
        const foo = class bar{
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        const foo = class bar{
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        foo['$inject'] = ['a','b'];
        """);

    test(
        """
        x.y = class bar{
          /** @ngInject */
          constructor(a, b) {}
        };
        """,
        """
        x.y = class bar{
          /** @ngInject */
          constructor(a, b){}
        };
        /** @public */
        x.y['$inject'] = ['a','b'];
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassArrowFunc() {
    test(
        """
        class FnClass {
          constructor() {
            /** @ngInject */
            this.someMethod = (a, b) => 42
          }
        }
        """,
        """
        class FnClass {
          constructor() {
            /** @ngInject */
            this.someMethod = (a, b) => 42
            /** @public */
            this.someMethod['$inject'] = ['a','b']
          }
        }
        """);
  }

  @Test
  public void testNgInjectAddsInjectToClassCompMethodName() {
    testError(
        """
        class FnClass {
          constructor() {}
            /** @ngInject */
          ['comp' + 'MethodName'](a, b){}
        }
        """,
        AngularPass.INJECT_NON_FUNCTION_ERROR);
  }

  @Test
  public void testNgInjectToArrowFunctions() {
    test(
        "/** @ngInject */ var fn = (a, b, c)=>{};",
        "/** @ngInject */ var fn = (a, b, c)=>{}; /** @public */ fn['$inject']=['a', 'b', 'c'];");
    testSame("/** @ngInject */ var fn = ()=>{}");
  }

  @Test
  public void testNgInjectToFunctionsWithDestructuredParam() {
    testError(
        "/** @ngInject */ function fn(a, {b, c}){}",
        AngularPass.INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM);
    testError(
        "/** @ngInject */ function fn(a, [b, c]){}",
        AngularPass.INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM);
    testError(
        "/** @ngInject */ function fn(a, {b, c}, d){}",
        AngularPass.INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM);
  }

  @Test
  public void testNgInjectToFunctionsWithDefaultValue() {
    testError(
        "/** @ngInject */ function fn(a, b = 1){}",
        AngularPass.INJECTED_FUNCTION_HAS_DEFAULT_VALUE);
    testError(
        "/** @ngInject */ function fn(a, {b, c} = {b: 1, c: 2}){}",
        AngularPass.INJECTED_FUNCTION_HAS_DEFAULT_VALUE);
    testError(
        "/** @ngInject */ function fn(a, [b, c] = [1, 2]){}",
        AngularPass.INJECTED_FUNCTION_HAS_DEFAULT_VALUE);
  }

  @Test
  public void testInGoogModule() {
    enableRewriteClosureCode();
    test(
        """
        goog.module('my.module');
        /** @ngInject */
        function fn(a, b) {}
        """,
        """
        goog.module('my.module');
        /** @ngInject */
        function fn(a, b) {}
        /** @public */ fn['$inject'] = ['a', 'b'];
        """);
  }

  @Test
  public void testInEsModule() {
    String js =
        """
        import {Foo} from './foo';

        class Bar extends Foo { /** @ngInject */ constructor(x, y) {} }
        """;
    test(
        js,
        js
            + """
            /** @public */
            Bar['$inject'] = ['x', 'y'];
            """);
  }

  @Test
  public void testInExportInEsModule() {
    String js =
        """
        import {Foo} from './foo';

        export class Bar extends Foo { /** @ngInject */ constructor(x, y) {} }
        """;
    test(
        js,
        js
            + """
            /** @public */
            Bar['$inject'] = ['x', 'y'];
            """);
  }

  @Test
  public void testInGoogScope() {
    test(
        """
        goog.scope(function() {
        /** @ngInject */
        function fn(a, b) {}
        });
        """,
        """
        goog.scope(function() {
        /** @ngInject */
        function fn(a, b) {}
        /** @public */ fn['$inject'] = ['a', 'b'];
        });
        """);
  }

  @Test
  public void testNameDeclarationAndAssign() {
    test(
        """
        let A = A_1 = class A {
          /**
           * @ngInject
           * @param {?} foo
           */
          constructor(foo) {}
        };
        """,
        """
        let A = A_1 = class A {
          /**
           * @ngInject
           * @param {?} foo
           */
          constructor(foo) {}
        };
        /** @public */
        A_1["$inject"] = ["foo"];
        """);
  }
}
