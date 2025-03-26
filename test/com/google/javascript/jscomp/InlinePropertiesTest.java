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

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class InlinePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      MINIMAL_EXTERNS
          + """
          Function.prototype.call=function(){};
          Function.prototype.inherits=function(){};
          prop.toString;
          var google = { gears: { factory: {}, workerPool: {} } };
          /** @type {?} */ var externUnknownVar;
          /** @type {!Function} */ var externFn;
          """;

  private boolean runSmartNameRemoval = false;

  public InlinePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // Ignore a few type warnings: we intentionally trigger these warnings
    // to make sure that the pass still operates correctly with bad code.
    DiagnosticGroup ignored =
        new DiagnosticGroup(TypeCheck.INEXISTENT_PROPERTY, TypeValidator.TYPE_MISMATCH_WARNING);
    options.setWarningLevel(ignored, CheckLevel.OFF);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    final CompilerPass pass = new InlineProperties(compiler);
    if (runSmartNameRemoval) {
      final CompilerPass removalPass =
          new RemoveUnusedCode.Builder(compiler)
              .removeLocalVars(true)
              .removeGlobals(true)
              .preserveFunctionExpressionNames(true)
              .removeUnusedPrototypeProperties(true)
              .removeUnusedThisProperties(true)
              .removeUnusedObjectDefinePropertiesDefinitions(true)
              .build();
      return new CompilerPass() {

        @Override
        public void process(Node externs, Node root) {
          removalPass.process(externs, root);
          pass.process(externs, root);
        }
      };
    }
    return pass;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableNormalize();
    enableClosurePass();
    enableRewriteClosureProvides();
    enableGatherExternProperties();
    this.runSmartNameRemoval = false;
    replaceTypesWithColors();
    disableCompareJsDoc();
  }

  @Test
  public void testConstInstanceProp1() {
    // Replace a reference to known constant property.
    test(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        new C().foo;
        """,
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        new C(), 1;
        """);

    test(
        """
        /** @constructor */
        function C() {
          {
            this.foo = 1;
          }
        }
        new C().foo;
        """,
        """
        /** @constructor */
        function C() {
          {
            this.foo = 1;
          }
        }
        new C(), 1;
        """);
  }

  @Test
  public void testConstInstanceProp2() {
    // Replace a constant reference
    test(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        var x = new C();
        x.foo;
        """,
        """
        /** @constructor */
        function C() {
          this.foo = 1
        }
        var x = new C();
        1;
        """);
  }

  @Test
  public void testConstInstanceProp3() {
    // Replace a constant reference
    test(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        /** @type {C} */
        var x = new C();
        x.foo;
        """,
        """
        /** @constructor */
        function C() {
          this.foo = 1
        }
        /** @type {C} */
        var x = new C();
        1;
        """);
  }

  @Test
  public void testConstInstanceProp4() {
    // This pass replies on DisambiguateProperties to distinguish like named
    // properties so it doesn't handle this case.
    testSame(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        /** @constructor */
        function B() {
          this.foo = 1;
        }
        new C().foo;
        """);
  }

  @Test
  public void testConstInstanceProp5() {
    test(
        """
        /** @constructor */
        function Foo() {
          /** @type {?number} */
          this.a = 1;
          /** @type {number} */
          this.b = 2;
        }
        var x = (new Foo).b;
        """,
        """
        /** @constructor */
        function Foo() {
          /** @type {?number} */
          this.a = 1;
          /** @type {number} */
          this.b = 2;
        }
        var x = (new Foo, 2);
        """);
  }

  @Test
  public void testConstClassProps1() {
    // Inline constant class properties,
    test(
        """
        /** @constructor */
        function C() {
        }
        C.bar = 2;
        C.foo = 1;
        var z = C.foo;
        """,
        """
        /** @constructor */
        function C() {
        }
        C.bar = 2;
        C.foo = 1;
        var z = 1;
        """);
  }

  @Test
  public void testConstClassProps2() {
    // Don't confuse, class properties with instance properties
    testSame(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        var z = C.foo;
        """);
  }

  @Test
  public void testConstClassProps3() {
    // Don't confuse, class properties with prototype properties
    testSame(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testConstClassProps4() {
    // Don't confuse unique constructors with similiar function types
    testSame(
        """
        /** @constructor */
        function C() {}
        /** @constructor @extends {C} */
        function D() {}
        /** @type {function(new:C): undefined} */
        var x = D;
        /** @type {number} */ x.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testConstClassProps5() {
    // Don't confuse subtype constructors properties
    testSame(
        """
        /** @constructor */
        function C() {}
        /** @constructor @extends {C} */
        function D() {}
        D.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testConstClassProps6() {
    // Don't inline to unknowns
    testSame(
        """
        /** @constructor */
        function C() {}
        C.foo = 1;
        var z = externUnknownVar.foo;
        """);
  }

  @Test
  public void testConstClassProps7() {
    // Don't inline to Function prop
    testSame(
        """
        /** @constructor */
        function C() {}
        C.foo = 1;
        var z = externFn.foo;
        """);
  }

  @Test
  public void testNonConstClassProp1() {
    testSame(
        """
        /** @constructor */
        function C() {
        }
        C.foo = 1;
        alert(C.foo);
        delete C.foo;
        """);
  }

  @Test
  public void testNonConstClassProp2() {
    testSame(
        """
        /** @constructor */
        function C() {
        }
        C.foo = 1;
        alert(C.foo);
        C.foo = 2;
        """);
  }

  @Test
  public void testNonConstClassProp3() {
    testSame(
        """
        /** @constructor */
        function C() {
        }
        C.foo = 1;
        function f(a) {
         a.foo = 2;
        }
        alert(C.foo);
        f(C);
        """);
  }

  @Test
  public void testNonConstInstanceProp1() {
    testSame(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        var x = new C();
        alert(x.foo);
        delete x.foo;
        """);
  }

  @Test
  public void testNonConstInstanceProp2() {
    testSame(
        """
        /** @constructor */
        function C() {
          this.foo = 1;
        }
        var x = new C();
        alert(x.foo);
        x.foo = 2;
        """);
  }

  @Test
  public void testNonConstructorInstanceProp1() {
    testSame(
        """
        function C() {
          this.foo = 1;
          return this;
        }
        C().foo;
        """);
  }

  @Test
  public void testConditionalInstanceProp1() {
    testSame(
        """
        /** @constructor */
        function C() {
          if (false) this.foo = 1;
        }
        new C().foo;
        """);
  }

  @Test
  public void testConstPrototypeProp1() {
    test(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        new C().foo;
        """,
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        new C(), 1;
        """);
  }

  @Test
  public void testConstPrototypeProp2() {
    test(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        var x = new C();
        x.foo;
        """,
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        var x = new C();
        1;
        """);
  }

  @Test
  public void testConstPrototypePropInGlobalBlockScope() {
    test(
        """
        /** @constructor */
        function C() {}
        {
          C.prototype.foo = 1;
        }
        var x = new C();
        x.foo;
        """,
        """
        /** @constructor */
        function C() {}
        {
          C.prototype.foo = 1;
        }
        var x = new C();
        1;
        """);
  }

  @Test
  public void testGlobalThisNotInlined() {
    testSame(
        """
        this.foo = 1;
        /** @constructor */
        function C() {
          foo;
        }
        """);
  }

  @Test
  public void testConstPrototypePropFromSuper() {
    test(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        /** @constructor @extends {C} */
        function D() {}
        (new D).foo;
        """,
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        /** @constructor @extends {C} */
        function D() {}
        new D, 1;
        """);
  }

  @Test
  public void testTypedPropInlining() {
    test(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        function f(/** !C */ x) { return x.foo; }
        f(new C);
        """,
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        function f(/** !C */ x) { return 1; }
        f(new C);
        """);
  }

  @Test
  public void testTypeMismatchNoPropInlining() {
    testSame(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        function f(/** !C */ x) { return x.foo; }
        f([]);
        """);
  }

  @Test
  public void testTypeMismatchForPrototypeNoPropInlining() {
    testSame(
        """
        /** @constructor */
        function C() {}
        C.prototype.foo = 1;
        function f(/** number */ x) {}
        f(C.prototype);
        new C().foo;
        """);
  }

  @Test
  public void testStructuralInterfacesNoPropInlining() {
    testSame(
        """
        /** @record */ function I() {}
        /** @type {number|undefined} */ I.prototype.foo;

        /** @constructor @implements {I} */
        function C() {}
        /** @override */
        C.prototype.foo = 1;

        function f(/** !I */ x) { return x.foo; }
        f([]);
        """);
  }

  @Test
  public void testStructuralInterfacesNoPropInlining2() {
    this.runSmartNameRemoval = true;

    test(
        """
        /** @record */
        function I() {
          /** @type {number} */ this.foo;
        }

        /** @constructor @implements {I} */
        function C() { /** @type {number} */ this.foo = 1; }

        function f(/** ? */ x) { return x.foo; }
        f(new C());
        """,
        """
        /** @constructor @implements {I} */
        function C() { /** @type {number} */ this.foo = 1; }

        function f(/** ? */ x) { return x.foo; }
        f(new C());
        """);
  }

  @Test
  public void testConstInstanceProp_es6Class() {
    // Replace a reference to known constant property.
    test(
        """
        class C {
          constructor() {
            this.foo = 1;
          }
        }
        new C().foo;
        """,
        """
        class C {
          constructor() {
            this.foo = 1;
          }
        }
        new C(), 1;
        """);
  }

  @Test
  public void testMultipleConstInstanceProp_es6Class() {
    test(
        """
        class Foo {
          constructor() {
            /** @type {?number} */
            this.a = 1;
            /** @type {number} */
            this.b = 2;
          }
        }
        var x = (new Foo).b;
        """,
        """
        class Foo {
          constructor() {
            /** @type {?number} */
            this.a = 1;
            /** @type {number} */
            this.b = 2;
          }
        }
        var x = (new Foo, 2);
        """);
  }

  @Test
  public void testConstInstancePropInArrowFunction_es6Class() {
    // Don't replace a reference to known constant property defined in an arrow function.
    testSame(
        """
        /** @unrestricted */ // make this not a struct, so we can define this.foo
        class C {
          constructor() {
            (() => {
              this.foo = 1;
            })();
          }
        }
        new C().foo;
        """);
  }

  @Test
  public void testConstClassProps_es6Class() {
    // Inline constant class properties,
    test(
        """
        class C {}
        C.bar = 2;
        C.foo = 1;
        var z = C.foo;
        """,
        """
        class C {}
        C.bar = 2;
        C.foo = 1;
        var z = 1;
        """);
  }

  @Test
  public void testConstClassPropsInheritedProp_es6Class() {
    test(
        """
        class C {}
        class D extends C {}
        C.foo = 1;
        var z = D.foo;
        """,
        """
        class C {}
        class D extends C {}
        C.foo = 1;
        var z = 1;
        """);
  }

  @Test
  public void testConstClassPropsInheritedPropChain_es6Class() {
    test(
        """
        class C {}
        class D extends C {}
        class E extends D {}
        class F extends E {}
        C.foo = 1;
        var z = F.foo;
        """,
        """
        class C {}
        class D extends C {}
        class E extends D {}
        class F extends E {}
        C.foo = 1;
        var z = 1;
        """);
  }

  @Test
  public void testConstClassPropsNonInheritedProp_es6Class() {
    // Test that we don't accidentally treat the superclass as having a subclass prop
    testSame(
        """
        class C {}
        class D extends C {}
        D.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testNonConstClassProp_es6ClassWithStaticMethod() {
    testSame(
        """
        class C { static foo() {} }
        alert(C.foo);
        C.foo = 1;
        """);
  }

  @Test
  public void testConstPrototypeProp_es6Class() {
    test(
        """
        class C {}
        C.prototype.foo = 1;
        new C().foo;
        """,
        """
        class C {}
        C.prototype.foo = 1;
        new C(), 1;
        """);
  }

  @Test
  public void testNonConstPrototypePropFromMemberFn() {
    testSame(
        """
        class C {
          foo() {}
        }
        C.prototype.foo = 4;
        (new C()).foo
        """);
  }

  @Test
  public void testObjectPatternStringKeyDoesntInvalidateProp() {
    test(
        """
        /** @constructor */
        function C() {
          this.foo = 3;
        }
        (new C()).foo
        const {foo} = new C();
        """,
        """
        /** @constructor */
        function C() {
          this.foo = 3;
        }
        new C(), 3;
        const {foo} = new C();
        """);
  }

  @Test
  public void testNoInlineOnRecordType() {
    testSame(
        """
        /** @record */
        class C {}
        C.bar = 2;
        C.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testNoInlineOnInterfaceType() {
    testSame(
        """
        /** @interface */
        class C {}
        C.bar = 2;
        C.foo = 1;
        var z = C.foo;
        """);
  }

  @Test
  public void testClassField() {
    test(
        """
        class C {
          a = 1;
          b;
        }
        (new C()).a;
        (new C()).b
        """,
        """
        class C {
          a = 1;
          b;
        }
        new C(), 1;
        (new C()).b
        """);
  }

  @Test
  public void testClassFieldWithInheritance() {
    test(
        """
        class C {
          a = 1;
          b;
        }
        class D extends C {};
        (new D()).a;
        (new D()).b
        """,
        """
        class C {
          a = 1;
          b;
        }
        class D extends C {};
        new D(), 1;
        (new D()).b;
        """);
  }

  @Test
  public void testClassField_static() {
    test(
        """
        class C {
          static a = 1;
          static b;
        }
        C.a;
        C.b
        """,
        """
        class C {
          static a = 1;
          static b;
        }
        1;
        C.b
        """);
  }

  @Test
  public void testClassComputedField() {
    testSame(
        """
        /** @dict */
        class C {
          ['a'] = 1;
          ['b'];
        }
        (new C())['a'];
        (new C())['b']
        """);
  }

  @Test
  public void testClassComputedField_static() {
    testSame(
        """
        /** @dict */
        class C {
          static ['a'] = 1;
          static ['b'];
        }
        C['a']
        C['b']
        """);
  }
}
