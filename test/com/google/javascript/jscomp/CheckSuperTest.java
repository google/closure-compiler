/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_ACCESS;
import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_CALL;
import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_CALL_WITH_SUGGESTION;
import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_USAGE;
import static com.google.javascript.jscomp.CheckSuper.MISSING_CALL_TO_SUPER;
import static com.google.javascript.jscomp.CheckSuper.SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR;
import static com.google.javascript.jscomp.CheckSuper.SUPER_CALL_IN_ARROW;
import static com.google.javascript.jscomp.CheckSuper.THIS_BEFORE_SUPER;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckSuperTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSuper(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // AST validation will throw exceptions for the cases we want to report as errors here.
    disableAstValidation();
  }

  @Test
  public void testInvalidUsage() {
    // 'super' may only appear directly within a CALL, a GETPROP, or a GETELEM.
    testError("class Foo extends Bar { constructor() { super(); super; } }", INVALID_SUPER_USAGE);
    testError("class Foo extends Bar { foo() { super; } }", INVALID_SUPER_USAGE);
    testError("function f() { super; }", INVALID_SUPER_USAGE);
    testError("super;", INVALID_SUPER_USAGE);
  }

  @Test
  public void testMissingSuper() {
    testError("class C extends D { constructor() {} }", MISSING_CALL_TO_SUPER);
    test(
        srcs("class C extends D { constructor() { super.foo(); } }"),
        error(MISSING_CALL_TO_SUPER),
        error(SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR));
  }

  @Test
  public void testFieldSuperCall() {
    testSame(
        lines(
            "class Foo {",
            "  x() {",
            "    return 5;",
            "  }",
            "}",
            "class Bar extends Foo {",
            "  y = () => super.x()",
            "}"));

    testError(
        lines(
            "class A {}", //
            "class B extends A { ",
            "  x = super();",
            "}"),
        INVALID_SUPER_CALL);
  }

  @Test
  public void testComputedSuperFields() {
    testError(
        lines(
            "class A {", //
            "  x = 'fieldOne'",
            "}",
            "class B extends A { ",
            "  [super.x] = 2;",
            "}"),
        INVALID_SUPER_ACCESS);

    testError(
        lines(
            "class A {", //
            "  x = 'fieldOne'",
            "}",
            "class B extends A { ",
            "  static [super.x] = 2;",
            "}"),
        INVALID_SUPER_ACCESS);
  }

  @Test
  public void testMissingSuper_nestedClass() {
    // Note that super is only called for anonymous class "E", not C.
    testError(
        lines(
            "class C extends D {",
            "  constructor() { ",
            "    const E = class extends D { constructor() { super(); } };",
            "  }",
            "}"),
        MISSING_CALL_TO_SUPER);
  }

  @Test
  public void testNoWarning() {
    testSame("class C extends D { constructor() { super(); } }");
    testSame("class C { constructor() {} }");
    testSame("class C extends D {}");
  }

  // just verifying that import.meta doesn't cause problems with super
  @Test
  public void testImportMeta() {
    testSame("class C extends D { constructor() { super(import.meta); } }");
  }

  @Test
  public void testThisBeforeSuper() {
    testError("class C extends D { constructor() { this.foo(); super(); } }", THIS_BEFORE_SUPER);
  }

  @Test
  public void testSuperPropAccessBeforeSuperCall() {
    testError(
        "class C extends D { constructor() { super.foo(); super(); } }",
        SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR);
  }

  @Test
  public void testThisAndSuperPropAccessBeforeSuperCall() {
    test(
        srcs("class C extends D { constructor() { this.foo(); super.foo(); super(); } }"),
        error(SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR),
        error(THIS_BEFORE_SUPER));
  }

  @Test
  public void testThisAndSuperPropAccessBeforeSuperCall_inSuperConstructorArgs() {
    test(
        srcs("class C extends D { constructor() { super(this.foo(), super.foo()); } }"),
        error(SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR),
        error(THIS_BEFORE_SUPER));
  }

  // We could require that the super() call is the first statement in the constructor, except that
  // doing so breaks J2CL-compiled code, which needs to do the static initialization for the class
  // before anything else.
  @Test
  public void testNoWarning_J2CL() {
    testSame("class C extends D { constructor() { C.init(); super(); } }");
  }

  // Referencing this within a function before calling super is acceptable at runtime so long as
  // it's never executed before the super() has returned. It's also acceptable since this might
  // be bound to something other than the class being constructed.
  @Test
  public void testNoWarning_thisWithinFunction() {
    testSame("class C extends D { constructor() { const c = () => this; super(); } }");
    testSame("class C extends D { constructor() { const c = function() { this; }; super(); } }");
  }

  @Test
  public void testError_superPropWithinConstructorArrowFunction() {
    testError(
        "class C extends D { constructor() { const c = () => super.foo; super(); } }",
        SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR);
  }

  @Test
  public void testOutsideClass() {
    testError("var i = super();", INVALID_SUPER_CALL);
    testError("var i = super.i;", INVALID_SUPER_ACCESS);
    testError("var i = super[x];", INVALID_SUPER_ACCESS);
  }

  @Test
  public void testInOrdinaryFunction() {
    testError("function f() { super(); }", INVALID_SUPER_CALL);
    testError("function f() { var i = super.i; }", INVALID_SUPER_ACCESS);
    testError("function f() { var i = super[x]; }", INVALID_SUPER_ACCESS);
  }

  @Test
  public void testSuperAccessInNestedOrdinaryFunction() {
    testError(
        lines(
            "", //
            "class C {",
            "  method() {}",
            "}",
            "class Sub extends C {",
            "  method() {",
            "    (function() { super.method() }).call(this);",
            "  }",
            "}",
            ""),
        INVALID_SUPER_ACCESS);
  }

  @Test
  public void testSuperAccessInNestedArrowFunction() {
    testSame(
        lines(
            "", //
            "class C {",
            "  method() {}",
            "}",
            "class Sub extends C {",
            "  method() {",
            "    (() => { super.method() })();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperAccessInNestedOrdinaryFunctionInGetter() {
    testError(
        lines(
            "", //
            "class C {",
            "  get value() { return 1; }",
            "}",
            "class Sub extends C {",
            "  get value() {",
            "    return (function() { super.value }).call(this);",
            "  }",
            "}",
            ""),
        INVALID_SUPER_ACCESS);
  }

  @Test
  public void testSuperAccessInNestedArrowFunctionInGetter() {
    testSame(
        lines(
            "", //
            "class C {",
            "  get value() { return 1; }",
            "}",
            "class Sub extends C {",
            "  get value() {",
            "    return (() => super.value)();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperCallInNestedOrdinaryFunction() {
    testError(
        lines(
            "", //
            "class C {",
            "  constructor() {}",
            "}",
            "class Sub extends C {",
            "  constructor() {",
            "    super();", // avoid getting missing super call warning
            "    (function() { super() }).call(this);",
            "  }",
            "}",
            ""),
        INVALID_SUPER_CALL);
  }

  @Test
  public void testSuperCallInNestedObjectLiteralMethod() {
    testError(
        lines(
            "", //
            "class C {",
            "  constructor() {}",
            "}",
            "class Sub extends C {",
            "  constructor() {",
            "    super();", // avoid getting missing super call warning
            "    this.objLit = {",
            // Object literals don't have special constructor methods, so calling super()
            // makes no sense. We nest this one inside of a method named constructor
            // to make sure that doesn't fool our logic.
            "      constructor() { super(); }",
            "    };",
            "  }",
            "}",
            ""),
        INVALID_SUPER_CALL);
  }

  @Test
  public void testObjLitMethodCanAccessSuperProps() {
    testSame(
        lines(
            "", //
            "const objLit = {",
            // Object literals don't have special constructor methods, so calling super()
            // makes no sense. We nest this one inside of a method named constructor
            // to make sure that doesn't fool our logic.
            "      method() { super.toString(); }",
            "};",
            ""));
  }

  @Test
  public void testObjLitPropDefinedWithArrowFunctionCannotAccessSuperProps() {
    testError(
        lines(
            "", //
            "const objLit = {",
            // This is not a proper object literal method, so it cannot refer to properties using
            // `super`
            "      method: () => { super.toString(); }",
            "};",
            ""),
        INVALID_SUPER_ACCESS);
  }

  @Test
  public void testObjLitDoesNotFoolArrowFunctionAccessLogicInMethod() {
    testSame(
        lines(
            "", //
            "class C {",
            "  method() {",
            "    this.objLit = {",
            // This is not a proper object literal method, but the arrow function is wrapped in
            // a class method, so reference to `super` properties is allowed here.
            "      method: () => { super.toString(); }",
            "    };",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperCallInNestedArrowFunction() {
    testError(
        lines(
            "", //
            "class C {",
            "  constructor() {}",
            "}",
            "class Sub extends C {",
            "  constructor() {",
            "    (() => { super() })();",
            "  }",
            "}",
            ""),
        SUPER_CALL_IN_ARROW);
  }

  @Test
  public void testCallWithNoBaseClass() {
    testError("class C { constructor() { super(); }}", INVALID_SUPER_CALL);
    testError("class C { constructor() { super(1); }}", INVALID_SUPER_CALL);
  }

  @Test
  public void testCallInConstructor() {
    testSame("class C extends D { constructor() { super(); }}");
    testSame("class C extends D { constructor() { super(1); }}");
  }

  @Test
  public void testNestedCallInConstructor() {
    // Technically it is legal to call `super()` in an arrow function within a constructor,
    // but doing so makes it harder to tell that the constructor is calling `super()` correctly and
    // there's really no good reason to do it. Such code should be refactored.
    testError("class C extends D { constructor() { (()=>{ super(); })(); }}", SUPER_CALL_IN_ARROW);
  }

  @Test
  public void testOutOfOrderNestedCallInConstructor() {
    test(
        srcs("class C extends D { constructor() { this.foo(); (()=>{ super(); })(); }}"),
        error(SUPER_CALL_IN_ARROW),
        error(THIS_BEFORE_SUPER));
  }

  @Test
  public void testCallInMethod() {
    test(
        srcs("class C extends D { foo() { super(); }}"),
        error(INVALID_SUPER_CALL_WITH_SUGGESTION)
            .withMessage("super() not allowed here. Did you mean super.foo?"));
    test(
        srcs("class C extends D { foo() { super(1); }}"),
        error(INVALID_SUPER_CALL_WITH_SUGGESTION)
            .withMessage("super() not allowed here. Did you mean super.foo?"));
    test(
        srcs("class C extends D { static foo() { super(1); }}"),
        error(INVALID_SUPER_CALL_WITH_SUGGESTION)
            .withMessage("super() not allowed here. Did you mean super.foo?"));
    testError("class C { static foo() { super(); }}", INVALID_SUPER_CALL_WITH_SUGGESTION);
    testError(
        "class C extends D { foo() { (()=>{ super(); })(); }}", INVALID_SUPER_CALL_WITH_SUGGESTION);
    testError(
        "class C extends D { static foo() { (()=>{ super(); })(); }}",
        INVALID_SUPER_CALL_WITH_SUGGESTION);
  }

  @Test
  public void testSuperCallInStaticBlock() {
    testError("class C extends D {static {super();}}", INVALID_SUPER_CALL);
    testError("class C extends D {static {super(1);}}", INVALID_SUPER_CALL);
    testError("class C extends D { static { (()=>{ super(); })(); }}", INVALID_SUPER_CALL);
  }

  @Test
  public void testPropertyInStaticBlock() {
    testSame("class C extends D { static { super.foo(); }}");
    testSame("class C extends D { static { super.foo(1); }}");

    // TODO(tbreisacher): Consider warning for this. It's valid but likely indicates a mistake.
    testSame("class C extends D { static { var x = super.bar; }}");
    testSame("class C extends D { static { var x = super[y]; }}");
    testSame("class C extends D { static { var x = super.bar(); }}");

    testSame("class C extends D { static { this[super.x] = super.bar; }}");
    testSame("class C extends D { static { this[super.x] = super[y]; }}");
    testSame("class C extends D { static { this[super.x] = super.bar(); }}");
  }

  @Test
  public void testPropertyInMethod() {
    testSame("class C extends D { foo() { super.foo(); }}");
    testSame("class C extends D { foo() { super.foo(1); }}");

    // TODO(tbreisacher): Consider warning for this. It's valid but likely indicates a mistake.
    testSame("class C extends D { foo() { var x = super.bar; }}");
    testSame("class C extends D { foo() { var x = super[y]; }}");
    testSame("class C extends D { foo() { var x = super.bar(); }}");

    testSame("class C extends D { foo() { this[super.x] = super.bar; }}");
    testSame("class C extends D { foo() { this[super.x] = super[y]; }}");
    testSame("class C extends D { foo() { this[super.x] = super.bar(); }}");
  }

  @Test
  public void testPropertyInStaticMethod() {
    testSame("class C extends D { static foo() { super.foo(); }}");
    testSame("class C extends D { static foo() { super.foo(1); }}");

    // TODO(tbreisacher): Consider warning for this. It's valid but likely indicates a mistake.
    testSame("class C extends D { static foo() { var x = super.bar; }}");
    testSame("class C extends D { static foo() { var x = super[y]; }}");
    testSame("class C extends D { static foo() { var x = super.bar(); }}");

    testSame("class C extends D { static foo() { this[super.x] = super.bar; }}");
    testSame("class C extends D { static foo() { this[super.x] = super[y]; }}");
    testSame("class C extends D { static foo() { this[super.x] = super.bar(); }}");
  }

  @Test
  public void testNoWarning_withExplicitReturnInConstructor() {
    testNoWarning("class C extends D { constructor() { return {}; } }");
    testNoWarning("class C extends D { constructor() { return f(); } }");
    // the following cases will error at runtime but we don't bother checking them.
    testNoWarning("class C extends D { constructor() { return 3; } }");
    testNoWarning("class C extends D { constructor() { if (false) return {}; } }");
  }

  @Test
  public void testWarning_withInvalidReturnInConstructor() {
    // empty return
    testError("class C extends D { constructor() { return; } }", MISSING_CALL_TO_SUPER);

    // return in arrow function
    testError(
        "class C extends D { constructor() { () => { return {}; } } }", MISSING_CALL_TO_SUPER);
  }

  @Test
  public void testInvalidThisReference_withExplicitReturnInConstructor() {
    testError(
        "class C extends D { constructor() { this.x = 3; super(); return {}; } }",
        THIS_BEFORE_SUPER);
  }

  @Test
  public void testThisReference_withExplicitReturnInConstructor() {
    // You don't have to call `super()` when the constructor returns a value, but then you also
    // cannot have references to `this` either.
    testError("class C extends D { constructor() { this.x = 3; return {}; } }", THIS_BEFORE_SUPER);
  }

  @Test
  public void testPropertyInConstructor() {
    // TODO(sdh): See note in testPropertyInMethod - these are valid but questionable.
    testSame("class C extends D { constructor() { super(); super.foo(); }}");
    testSame("class C extends D { constructor() { super(); super.foo(1); }}");
    testSame("class C extends D { constructor() { super(); var x = super.bar; }}");
  }

  @Test
  public void testNestedProperty() {
    testSame("class C extends D { constructor() { super(); (()=>{ super.foo(); })(); }}");
    testSame("class C extends D { foo() { (()=>{ super.foo(); })(); }}");
    testSame("class C extends D { static foo() { (()=>{ super.foo(); })(); }}");
    testSame("class C extends D { static { (()=>{ super.foo(); })(); }}");
  }

  @Test
  public void testPropertyNoBaseClass() {
    // TODO(sdh): See note in testPropertyInMethod - these are valid but questionable.
    testSame("class C { constructor() { super.foo(); }}");
    testSame("class C { foo() { super.foo(1); }}");
    testSame("class C { foo() { super.bar(); }}");
    testSame("class C { foo() { super.baz; }}");
    testSame("class C { foo() { super[x](); }}");
  }
}
