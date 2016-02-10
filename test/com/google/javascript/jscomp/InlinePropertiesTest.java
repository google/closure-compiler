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

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class InlinePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS = LINE_JOINER.join(
      "Function.prototype.call=function(){};",
      "Function.prototype.inherits=function(){};",
      "prop.toString;",
      "var google = { gears: { factory: {}, workerPool: {} } };",
      "/** @type {?} */ var externUnknownVar;",
      "/** @type {!Function} */ var externGenericFn;");

  public InlinePropertiesTest() {
    super(EXTERNS);
    enableNormalize();
    enableTypeCheck();
    enableClosurePass();
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineProperties(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testConstInstanceProp1() {
    // Replace a reference to known constant property.
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "new C().foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "new C(), 1;"));

    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  {",
        "    this.foo = 1;",
        "  }",
        "}",
        "new C().foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  {",
        "    this.foo = 1;",
        "  }",
        "}",
        "new C(), 1;"));
  }

  public void testConstInstanceProp2() {
    // Replace a constant reference
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "x.foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1",
        "}",
        "var x = new C();",
        "1;\n"));
  }


  public void testConstInstanceProp3() {
    // Replace a constant reference
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "/** @type {C} */",
        "var x = new C();",
        "x.foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1",
        "}",
        "/** @type {C} */",
        "var x = new C();",
        "1;\n"));
  }

  public void testConstInstanceProp4() {
    // This pass replies on DisambiguateProperties to distinguish like named
    // properties so it doesn't handle this case.
    testSame(
        LINE_JOINER.join(
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


  public void testConstClassProps1() {
    // Inline constant class properties,
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "C.foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "1;"));
  }

  public void testConstClassProps2() {
    // Don't confuse, class properties with instance properties
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "C.foo;"));
  }

  public void testConstClassProps3() {
    // Don't confuse, class properties with prototype properties
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "C.foo;\n"));
  }

  public void testConstClassProps4() {
    // Don't confuse unique constructors with similiar function types
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "/** @constructor @extends {C} */",
        "function D() {}",
        "/** @type {function(new:C)} */",
        "var x = D;",
        "x.foo = 1;",
        "C.foo;\n"));
  }

  public void testConstClassProps5() {
    // Don't confuse subtype constructors properties
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "/** @constructor @extends {C} */",
        "function D() {}",
        "D.foo = 1;",
        "C.foo;\n"));
  }

  public void testConstClassProps6() {
    // Don't inline to unknowns
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.foo = 1;",
        "externUnknownVar.foo;\n"));
  }

  public void testConstClassProps7() {
    // Don't inline to Function prop
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.foo = 1;",
        "externGenericFn.foo;\n"));
  }

  public void testNonConstClassProp1() {
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "alert(C.foo);",
        "delete C.foo;"));
  }

  public void testNonConstClassProp2() {
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "}",
        "C.foo = 1;",
        "alert(C.foo);",
        "C.foo = 2;"));
  }

  public void testNonConstClassProp3() {
    testSame(LINE_JOINER.join(
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

  public void testNonConstInstanceProp1() {
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "alert(x.foo);",
        "delete x.foo;"));
  }

  public void testNonConstInstanceProp2() {
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  this.foo = 1;",
        "}",
        "var x = new C();",
        "alert(x.foo);",
        "x.foo = 2;"));
  }

  public void testNonConstructorInstanceProp1() {
    testSame(LINE_JOINER.join(
        "function C() {",
        "  this.foo = 1;",
        "  return this;",
        "}",
        "C().foo;"));
  }

  public void testConditionalInstanceProp1() {
    testSame(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {",
        "  if (false) this.foo = 1;",
        "}",
        "new C().foo;"));
  }

  public void testConstPrototypeProp1() {
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "new C().foo;\n"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "new C(), 1;\n"));
  }

  public void testConstPrototypeProp2() {
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "var x = new C();",
        "x.foo;\n"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "var x = new C();",
        "1;\n"));
  }

  public void testConstPrototypePropInGlobalBlockScope() {
    test(LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "{",
        "  C.prototype.foo = 1;",
        "}",
        "var x = new C();",
        "x.foo;"),
        LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "{",
        "  C.prototype.foo = 1;",
        "}",
        "var x = new C();",
        "1;"));
  }

  public void testGlobalThisNotInlined() {
    testSame(LINE_JOINER.join(
        "this.foo = 1;",
        "/** @constructor */",
        "function C() {",
        "  foo;",
        "}"));
  }

  public void testConstPrototypePropFromSuper() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "(new D).foo;"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "/** @constructor @extends {C} */",
            "function D() {}",
            "new D, 1;"));
  }

  public void testTypedPropInlining() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "function f(/** !C */ x) { return x.foo; }",
            "f(new C);"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function C() {}",
            "C.prototype.foo = 1;",
            "function f(/** !C */ x) { return 1; }",
            "f(new C);"));
  }

  public void testTypeMismatchNoPropInlining() {
    String js = LINE_JOINER.join(
        "/** @constructor */",
        "function C() {}",
        "C.prototype.foo = 1;",
        "function f(/** !C */ x) { return x.foo; }",
        "f([]);");

    testSame(js, TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testStructuralInterfacesNoPropInlining() {
    String js = LINE_JOINER.join(
        "/** @record */ function I() {}",
        "/** @type {number|undefined} */ I.prototype.foo;",
        "",
        "/** @constructor @implements {I} */",
        "function C() {}",
        "/** @override */",
        "C.prototype.foo = 1;",
        "",
        "function f(/** !I */ x) { return x.foo; }",
        "f([]);");

    testSame(js);
  }
}
