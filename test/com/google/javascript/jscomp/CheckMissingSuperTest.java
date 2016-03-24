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

public final class CheckMissingSuperTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckMissingSuper(compiler);
  }

  public void testMissingSuper() {
    testErrorEs6("class C extends D { constructor() {} }", MISSING_CALL_TO_SUPER);
    testErrorEs6("class C extends D { constructor() { super.foo(); } }", MISSING_CALL_TO_SUPER);
  }

  public void testNoWarning() {
    testSameEs6("class C extends D { constructor() { super(); } }");
    testSameEs6("class C { constructor() {} }");
    testSameEs6("class C extends D {}");
  }

  // We could require that the super() call is the first statement in the constructor, except that
  // doing so breaks J2CL-compiled code, which needs to do the static initialization for the class
  // before anything else.
  public void testNoWarning_J2CL() {
    testSameEs6("class C extends D { constructor() { C.init(); super(); } }");
  }
}
