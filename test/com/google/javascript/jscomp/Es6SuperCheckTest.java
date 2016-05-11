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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;


/**
 * Test case for {@link Es6SuperCheck}.
 */
public final class Es6SuperCheckTest extends CompilerTestCase {
  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6SuperCheck(compiler);
  }

  public void testInConstructorNoBaseClass() {
    testError("var i = super();");
  }

  public void testNoBaseClass() {
    testError("class C { constructor() { super(); }}");
    testError("class C { constructor() { super(1); }}");
    testError("class C { static foo() { super(); }}");
  }

  public void testInConstructor() {
    testSame("class C extends D { constructor() { super(); }}");
    testSame("class C extends D { constructor() { super(1); }}");
  }

  public void testNestedInConstructor() {
    testError("class C extends D { constructor() { (()=>{ super(); })(); }}");
  }

  public void testInNonConstructor() {
    testErrorWithSuggestion(
        "class C extends D { foo() { super(); }}",
        "super() not allowed here. Did you mean super.foo?");
    testErrorWithSuggestion(
        "class C extends D { foo() { super(1); }}",
        "super() not allowed here. Did you mean super.foo?");
  }

  public void testNestedInNonConstructor() {
    testError("class C extends D { foo() { (()=>{ super(); })(); }}");
  }

  public void testDotMethodInNonConstructor() {
    testSame("class C extends D { foo() { super.foo(); }}");
    testSame("class C extends D { foo() { super.foo(1); }}");

    // TODO(tbreisacher): Consider warning for this. It's valid but likely indicates a mistake.
    testSame("class C extends D { foo() { super.bar(); }}");
  }

  private void testError(String js) {
    testError(js, Es6SuperCheck.INVALID_SUPER_CALL);
  }

  private void testErrorWithSuggestion(String js, String message) {
    testError(js, Es6SuperCheck.INVALID_SUPER_CALL_WITH_SUGGESTION, message);
  }
}

