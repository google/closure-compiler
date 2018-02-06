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

import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_CALL;
import static com.google.javascript.jscomp.CheckSuper.INVALID_SUPER_CALL_WITH_SUGGESTION;
import static com.google.javascript.jscomp.CheckSuper.MISSING_CALL_TO_SUPER;
import static com.google.javascript.jscomp.CheckSuper.THIS_BEFORE_SUPER;

public final class CheckSuperTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSuper(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testMissingSuper() {
    testError("class C extends D { constructor() {} }", MISSING_CALL_TO_SUPER);
    testError("class C extends D { constructor() { super.foo(); } }", MISSING_CALL_TO_SUPER);
  }

  public void testMissingSuper_nestedClass() {
    // Note that super is only called for anonymous class "E", not C.
    testError(lines(
        "class C extends D {",
        "  constructor() { ",
        "    const E = class extends D { constructor() { super(); } };",
        "  }",
        "}"),
        MISSING_CALL_TO_SUPER);
  }

  public void testNoWarning() {
    testSame("class C extends D { constructor() { super(); } }");
    testSame("class C { constructor() {} }");
    testSame("class C extends D {}");
  }

  public void testThisBeforeSuper() {
    testError("class C extends D { constructor() { this.foo(); super(); } }", THIS_BEFORE_SUPER);
  }

  // We could require that the super() call is the first statement in the constructor, except that
  // doing so breaks J2CL-compiled code, which needs to do the static initialization for the class
  // before anything else.
  public void testNoWarning_J2CL() {
    testSame("class C extends D { constructor() { C.init(); super(); } }");
  }

  // Referencing this within a function before calling super is acceptable at runtime so long as
  // it's never executed before the super() has returned. It's also acceptable since this might
  // be bound to something other than the class being constructed.
  public void testNoWarning_thisWithinFunction() {
    testSame("class C extends D { constructor() { const c = () => this; super(); } }");
    testSame("class C extends D { constructor() { const c = function() { this; }; super(); } }");
  }

  public void testInConstructorNoBaseClass() {
    testError("var i = super();", INVALID_SUPER_CALL);
  }

  public void testNoBaseClass() {
    testError("class C { constructor() { super(); }}", INVALID_SUPER_CALL);
    testError("class C { constructor() { super(1); }}", INVALID_SUPER_CALL);
    testError("class C { static foo() { super(); }}", INVALID_SUPER_CALL);
  }

  public void testInConstructor() {
    testSame("class C extends D { constructor() { super(); }}");
    testSame("class C extends D { constructor() { super(1); }}");
  }

  public void testNestedInConstructor() {
    testError(
        "class C extends D { constructor() { (()=>{ super(); })(); }}", MISSING_CALL_TO_SUPER);
  }

  public void testInNonConstructor() {
    test(
        srcs("class C extends D { foo() { super(); }}"),
        error(INVALID_SUPER_CALL_WITH_SUGGESTION)
            .withMessage("super() not allowed here. Did you mean super.foo?"));
    test(
        srcs("class C extends D { foo() { super(1); }}"),
        error(INVALID_SUPER_CALL_WITH_SUGGESTION)
            .withMessage("super() not allowed here. Did you mean super.foo?"));
  }

  public void testNestedInNonConstructor() {
    testError("class C extends D { foo() { (()=>{ super(); })(); }}", INVALID_SUPER_CALL);
  }

  public void testDotMethodInNonConstructor() {
    testSame("class C extends D { foo() { super.foo(); }}");
    testSame("class C extends D { foo() { super.foo(1); }}");

    // TODO(tbreisacher): Consider warning for this. It's valid but likely indicates a mistake.
    testSame("class C extends D { foo() { super.bar(); }}");
  }
}
