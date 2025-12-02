/*
 * Copyright 2025 The Closure Compiler Authors.
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

@RunWith(JUnit4.class)
public class J2clUndefinedChecksRewriterPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), new J2clUndefinedChecksRewriterPass());
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void optimizeIsUndefined() {
    test("nativebootstrap$Util$impl$isUndefined(undefined)", "true");
    test("nativebootstrap$Util$impl$isUndefined(void 0)", "true");
    test("nativebootstrap$Util$impl$isUndefined(null)", "null, false");
    test("nativebootstrap$Util$impl$isUndefined(1)", "1, false");
    test("nativebootstrap$Util$impl$isUndefined('foo')", "'foo', false");
    test("nativebootstrap$Util$impl$isUndefined([])", "[], false");
    test("nativebootstrap$Util$impl$isUndefined({})", "({}, false)");
    test("nativebootstrap$Util$impl$isUndefined(new Error())", "new Error(), false");

    testSame(
        """
        var x = undefined;
        isUndefined(x);
        """);
    testSame("nativebootstrap$Util$impl$isUndefined(this)");
  }

  @Test
  public void optimizeCoerceToNull() {
    test("nativebootstrap$Util$impl$coerceToNull(undefined)", "null");
    test("nativebootstrap$Util$impl$coerceToNull(null)", "null");
    test("nativebootstrap$Util$impl$coerceToNull(1)", "1");
    test("nativebootstrap$Util$impl$coerceToNull('foo')", "'foo'");
    test("nativebootstrap$Util$impl$coerceToNull([])", "[]");
    test("nativebootstrap$Util$impl$coerceToNull({})", "({})");
    test("nativebootstrap$Util$impl$coerceToNull(new Error())", "(new Error())");

    testSame(
        """
        var x = 1;
        nativebootstrap$Util$impl$coerceToNull(x);
        """);
    testSame("nativebootstrap$Util$impl$coerceToNull(this)");
  }
}
