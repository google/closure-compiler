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

import static com.google.javascript.jscomp.CheckMissingSuper.MISSING_CALL_TO_SUPER;
import static com.google.javascript.jscomp.CheckMissingSuper.THIS_BEFORE_SUPER;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class CheckMissingSuperTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingSuper(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
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
}
