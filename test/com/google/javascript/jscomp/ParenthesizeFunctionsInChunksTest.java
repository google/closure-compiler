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

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ParenthesizeFunctionsInChunks} in isolation. */
@RunWith(JUnit4.class)
public final class ParenthesizeFunctionsInChunksTest extends CompilerTestCase {
  private ImmutableSet<String> chunksToParenthesize;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    chunksToParenthesize = ImmutableSet.of(); // Disable parenthesization by default.
    // The `ParenthesizeFunctionsInChunks` compiler pass adds a `MARK_FOR_PARENTHESIZE` boolean
    // property to each relevant function node. But, original source-level parenthesis add a
    // different `IS_PARENTHESIZED` boolean property. We need to compare the exact string output to
    // ensure that the parenthesis match the expected output.
    disableCompareAsTree();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ParenthesizeFunctionsInChunks(compiler, chunksToParenthesize);
  }

  @Test
  public void testCompilerPass_shouldAddParens_whenWithinChunk() {
    chunksToParenthesize = ImmutableSet.of(JSChunk.STRONG_CHUNK_NAME);
    test("function f(){}", "var f=(function(){})");
    test("f();function f(){}", "var f=(function(){});f()");
    test("function a(){}function b(){}", "var a=(function(){});var b=(function(){})");
    test("var a=function(){},b=function(){}", "var a=(function(){}),b=(function(){})");
    test("var x=1;function b(){}", "var b=(function(){});var x=1");
    test("var f=function foo(){}", "var f=(function foo(){})");
    test("var f=function(){}", "var f=(function(){})");
    test("const f=function(){}", "const f=(function(){})");
    test("let f=function(){}", "let f=(function(){})");
    test("f=function f(){}", "f=(function f(){})");
    test("f(function(){})", "f((function(){}))");
    test("var x={f:function(){}}", "var x={f:(function(){})}");
    test("if(x)var f=function(){}", "if(x)var f=(function(){})");
    test("if(x){function f(){}f()}", "if(x){var f=(function(){});f()}");
    test("if(x){function a(){}function b(){}}", "if(x){var a=(function(){});var b=(function(){})}");
    test("{f();function f(){}}", "{var f=(function(){});f()}");
    test("for(;true;){f();function f(){}}", "for(;true;){var f=(function(){});f()}");
    test("switch(1){case 1:f();function f(){}}", "switch(1){case 1:var f=(function(){});f()}");
    test("function a(){function b(){}}", "var a=(function(){function b(){}})");
    test("var f=()=>{}", "var f=(()=>{})");
    test("const f=()=>{}", "const f=(()=>{})");
    test("let f=()=>{}", "let f=(()=>{})");
    test("f=()=>{}", "f=(()=>{})");
  }

  @Test
  public void testCompilerPass_shouldNoOp_whenWithinChunk_andUnsupportedSyntax() {
    chunksToParenthesize = ImmutableSet.of(JSChunk.STRONG_CHUNK_NAME);
    // TODO(user): We might want to support these use cases in the future.
    testSame("var x={f(){}}");
    testSame("class A{m(){}}");
  }

  @Test
  public void testCompilerPass_shouldNoOp_whenWithinChunk_andAlreadyWrapped() {
    chunksToParenthesize = ImmutableSet.of(JSChunk.STRONG_CHUNK_NAME);
    testSame("var f=(function(){})");
    testSame("var f=(function foo(){})");
    testSame("var f=(()=>{})");
    testSame("if(x){var a=(function(){});var b=(function(){})}");
  }

  @Test
  public void testCompilerPass_shouldNoOp_whenOutsideChunk() {
    chunksToParenthesize = ImmutableSet.of(JSChunk.WEAK_CHUNK_NAME);
    testSame("function f(){}");
    testSame("f();function f(){}");
    testSame("function a(){}function b(){}");
    testSame("var foo=function foo(){}");
    testSame("foo=function f(){}");
    testSame("const fn=function(){}");
    testSame("if(x)var f=function(){}");
    testSame("if(x){function f(){}f()}");
    testSame("if(x){function a(){}function b(){}}");
  }
}
