/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * Tests for {@link VariableShadowDeclarationCheck}
 *
 */
public class VariableShadowDeclarationCheckTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new VariableShadowDeclarationCheck(compiler, CheckLevel.WARNING);
  }

  public void testNoWarnShadowGlobal() {
    // shadowing a global should not produce a warning unless the
    // global is marked @noshadow.
    assertNoError("", "var x; function foo() { var x } ");
    assertNoError("var x", "function foo() { var x } ");
  }

  public void testWarnShadowLocal1() {
    assertError("", "function a(){ var x; function b() { var x = 1; } }");
  }

  public void testWarnShadowLocal2() {
    assertError("",
                "function a(){" +
                "  /** @noshadow */ var x;" +
                "  function b() {" +
                "    var x = 1;" +
                "  }" +
                "}");
  }

  public void testUseShadowGlobals1() {
    assertNoError("", "/** @noshadow */ var x; function foo() { x = 1 } ");
    assertNoError("", "function a() { var x; function b() { x = 1; } }");
  }

  public void testNoShadowAnnotation() {
    assertError("",
                "/** @noshadow */ var x; function a() { var x } ");

    assertError("",
                "/** @noshadow */ var x; function a() {function b(){var x}} ");
  }

  public void testNoShadowAnnotationInExterns1() {
    assertError("/** @noshadow */ var x",
                "function a() { var x } ");
  }

  public void testNoShadowAnnotationInExterns2() {
    assertError("/** @noshadow */ var x",
                "function a() {function b(){var x}} ");
  }

  private void assertError(String externs, String js) {
    test(externs, js, js, null,
         VariableShadowDeclarationCheck.SHADOW_VAR_ERROR);
  }

  private void assertNoError(String externs, String js) {
    test(externs, js, js, null, null);
  }
}
