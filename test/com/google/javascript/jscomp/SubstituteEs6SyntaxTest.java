/*
 * Copyright 2015 The Closure Compiler Authors.
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
 * Tests for {@link SubstituteEs6Syntax} in isolation.
 */
public final class SubstituteEs6SyntaxTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new SubstituteEs6Syntax(compiler);
  }

  public void testArrowFunctions() {
    testSame("function f() {}");
    testSame("(function() { this.x = 5; })");
    testSame("(function() { return arguments[0]; })");
    testSame("(function() { return ()=>this; })");
    testSame("(function() { return ()=>arguments[0]; })");
    testSame("(function() { x++; return 5; })");
    test("()=>{ return 5; }", "()=>5");
    test("()=>{ return; }", "()=>undefined");
    test("(x)=>{ return x+1 }", "(x) => x+1");
    test("(x)=>{ return x++,5; }", "(x) => (x++,5)");
  }
}
