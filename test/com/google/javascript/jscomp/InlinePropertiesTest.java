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

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class InlinePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          MINIMAL_EXTERNS,
          "Function.prototype.call=function(){};",
          "Function.prototype.inherits=function(){};",
          "prop.toString;",
          "var google = { gears: { factory: {}, workerPool: {} } };",
          "/** @type {?} */ var externUnknownVar;",
          "/** @type {!Function} */ var externFn;");

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
        new DiagnosticGroup(
            TypeCheck.INEXISTENT_PROPERTY,
            TypeValidator.TYPE_MISMATCH_WARNING);
    options.setWarningLevel(ignored, CheckLevel.OFF);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    final CompilerPass pass =  new InlineProperties(compiler);
    if (runSmartNameRemoval) {
      final CompilerPass removalPass =
          new RemoveUnusedCode.Builder(compiler)
              .removeLocalVars(true)
              .removeGlobals(true)
              .preserveFunctionExpressionNames(true)
              .removeUnusedPrototypeProperties(true)
              .allowRemovalOfExternProperties(false)
              .removeUnusedThisProperties(true)
              .removeUnusedObjectDefinePropertiesDefinitions(true)
              .removeUnusedConstructorProperties(true)
              .build();
      return new CompilerPass(){

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
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableNormalize();
    enableClosurePass();
    enableGatherExternProperties();
    this.runSmartNameRemoval = false;
  }

  @Test
  public void testConstInstanceProp1() {
    // Replace a reference to known constant property.
    test(lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "new C().foo;"),
        lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "new C(), 1;"));

    test(lines(
        "/** @constructor */",
        "function C() {",
        "  {",
        "    this.foo = 1;",
        "  }",
        "}",
        "new C().foo;"),
        lines(
        "/** @constructor */",
        "function C() {",
        "  {",
        "    this.foo = 1;",
        "  }",
        "}",
        "new C(), 1;"));
  }

  @Test
  public void testConstInstanceProp2() {
    // Replace a constant reference
    test(lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "x.foo;"),
        lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1",
        "}",
        "var x = new C();",
        "1;\n"));
  }

  @Test
  public void testConstInstanceProp3() {
    // Replace a constant reference
    test(lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "/** @type {C} */",
        "var x = new C();",
        "x.foo;"),
        lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1",
        "}",
        "/** @type {C} */",
        "var x = new C();",
        "1;\n"));
  }

  @Test
  public void testConstInstanceProp4() {
    // This pass replies on DisambiguateProperties to distinguish like named
    // properties so it doesn't handle this case.
    testSame(
        lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "/** @constructor */",
        "function B() {",
        "  this.foo = 1;",
        "}",
        "new C().foo;\n"));
  }

  @Test
  public void testConstInstanceProp5() {
    test(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {?number} */",
            "  this.a = 1;",
            "  /** @type {number} */",
            "  this.b = 2;",
            "}",
            "var x = (new Foo).b;"),
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {?number} */",
            "  this.a = 1;",
            "  /** @type {number} */",
            "  this.b = 2;",
            "}",
            "var x = (new Foo, 2);"));
  }

  @Test
  public void testConstClassProps1() {
    // Inline constant class properties,
    test(
        lines(
            "/** @constructor */",
            "function C() {",
            "}",
            "C.bar = 2;",
            "C.foo = 1;",
            "var z = C.foo;"),
        lines(
            "/** @constructor */",
            "function C() {",
            "}",
            "C.bar = 2;",
            "C.foo = 1;",
            "var z = 1;"));
  }

  @Test
  public void testConstClassProps2() {
    // Don't confuse, class properties with instance properties
    testSame(
        lines(
            "/** @constructor */",
            "function C() {",
            "  this.foo = 1;",
            "}",
            "var z = C.foo;"));
  }

  @Test
  public void testConstClassProps3() {
    // Don't confuse, class properties with prototype properties
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "var z = C.foo;\n"));
  }

  @Test
  public void testConstClassProps4() {
    // Don't confuse unique constructors with similiar function types
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "/** @type {function(new:C): undefined} */",
            "var x = D;",
            "/** @type {number} */ x.foo = 1;",
            "var z = C.foo;\n"));
  }

  @Test
  public void testConstClassProps5() {
    // Don't confuse subtype constructors properties
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "D.foo = 1;",
            "var z = C.foo;\n"));
  }

  @Test
  public void testConstClassProps6() {
    // Don't inline to unknowns
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.foo = 1;",
            "var z = externUnknownVar.foo;\n"));
  }

  @Test
  public void testConstClassProps7() {
    // Don't inline to Function prop
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.foo = 1;",
            "var z = externFn.foo;\n"));
  }

  @Test
  public void testNonConstClassProp1() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "alert(C.foo);",
        "delete C.foo;"));
  }

  @Test
  public void testNonConstClassProp2() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "alert(C.foo);",
        "C.foo = 2;"));
  }

  @Test
  public void testNonConstClassProp3() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "function f(a) {",
        " a.foo = 2;",
        "}",
        "alert(C.foo);",
        "f(C);"));
  }

  @Test
  public void testNonConstInstanceProp1() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "alert(x.foo);",
        "delete x.foo;"));
  }

  @Test
  public void testNonConstInstanceProp2() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "alert(x.foo);",
        "x.foo = 2;"));
  }

  @Test
  public void testNonConstructorInstanceProp1() {
    testSame(lines(
        "function C() {",
        "  this.foo = 1;",
        "  return this;",
        "}",
        "C().foo;"));
  }

  @Test
  public void testConditionalInstanceProp1() {
    testSame(lines(
        "/** @constructor */",
        "function C() {",
        "  if (false) this.foo = 1;",
        "}",
        "new C().foo;"));
  }

  @Test
  public void testConstPrototypeProp1() {
    test(lines(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "new C().foo;\n"),
        lines(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "new C(), 1;\n"));
  }

  @Test
  public void testConstPrototypeProp2() {
    test(lines(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "var x = new C();",
        "x.foo;\n"),
        lines(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "var x = new C();",
        "1;\n"));
  }

  @Test
  public void testConstPrototypePropInGlobalBlockScope() {
    test(lines(
        "/** @constructor */",
        "function C() {}",
        "{",
        "  C.prototype.foo = 1;",
        "}",
        "var x = new C();",
        "x.foo;"),
        lines(
        "/** @constructor */",
        "function C() {}",
        "{",
        "  C.prototype.foo = 1;",
        "}",
        "var x = new C();",
        "1;"));
  }

  @Test
  public void testGlobalThisNotInlined() {
    testSame(lines(
        "this.foo = 1;",
        "/** @constructor */",
        "function C() {",
        "  foo;",
        "}"));
  }

  @Test
  public void testConstPrototypePropFromSuper() {
    test(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "(new D).foo;"),
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "new D, 1;"));
  }

  @Test
  public void testTypedPropInlining() {
    test(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "function f(/** !C */ x) { return x.foo; }",
            "f(new C);"),
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "function f(/** !C */ x) { return 1; }",
            "f(new C);"));
  }

  @Test
  public void testTypeMismatchNoPropInlining() {
    testSame(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "function f(/** !C */ x) { return x.foo; }",
            "f([]);"));
  }

  @Test
  public void testStructuralInterfacesNoPropInlining() {
    testSame(
        lines(
            "/** @record */ function I() {}",
            "/** @type {number|undefined} */ I.prototype.foo;",
            "",
            "/** @constructor @implements {I} */",
            "function C() {}",
            "/** @override */",
            "C.prototype.foo = 1;",
            "",
            "function f(/** !I */ x) { return x.foo; }",
            "f([]);"));
  }

  @Test
  public void testStructuralInterfacesNoPropInlining2() {
    this.runSmartNameRemoval = true;

    test(
        lines(
            "/** @record */",
            "function I() {",
            "  /** @type {number} */ this.foo;",
            "}",
            "",
            "/** @constructor @implements {I} */",
            "function C() { /** @type {number} */ this.foo = 1; }",
            "",
            "function f(/** ? */ x) { return x.foo; }",
            "f(new C());"),
        lines(
            "/** @constructor @implements {I} */",
            "function C() { /** @type {number} */ this.foo = 1; }",
            "",
            "function f(/** ? */ x) { return x.foo; }",
            "f(new C());"));
  }
}
