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

import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ParenthesizeFunctionsInChunks} in isolation. */
@RunWith(JUnit4.class)
public final class ParenthesizeFunctionsInChunksTest extends CompilerTestCase {
  private final HashSet<String> chunksToParaenthesize = new HashSet<>();
  private static final String CHUNK_NAME_FOR_TEST = "$strong$";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableValidateAstChangeMarking();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ParenthesizeFunctionsInChunks(compiler, chunksToParaenthesize);
  }

  @Test
  public void testFunctionWrappingWithParens_withinChunk() {
    chunksToParaenthesize.add(CHUNK_NAME_FOR_TEST);
    test("function f() {}", "var f = (function() {})");
    test("var foo = function foo() {}", "var foo = (function foo() {})");
    test("foo = function f() {}", "foo = (function f() {})");
    test("const fn = function() {};", "const fn = (function() {});");
    test("if (x < 3) { var fn = function() {}; }", "if (x < 3) { var fn = (function() {}); }");
  }

  @Test
  public void testFunctionWrappingWithParens_outsideChunk() {
    chunksToParaenthesize.clear();
    testSame("function f() {}");
    testSame("var foo = function foo() {}");
    testSame("foo = function f() {}");
    testSame("const fn = function() {};");
    testSame("if (x < 3) { var fn = function() {}; }");
  }
}
