/*
 * Copyright 2011 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {#link {@link PeepholeReplaceArrayOf}
 *
 */
@RunWith(JUnit4.class)
public final class PeepholeReplaceArrayOfTest extends CompilerTestCase {

  public PeepholeReplaceArrayOfTest() {
    super(
        MINIMAL_EXTERNS
            + lines(
                "/**",
                " * @param {...T} var_args",
                " * @return {!Array<T>}",
                " * @template T",
                " */",
                "Array.of = function(var_args) {};"));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), new PeepholeReplaceArrayOf());
  }

  @Test
  public void testSpread() {
    test("x = Array.of(...['a', 'b', 'c'])", "x = [...['a', 'b', 'c']]");
    test("x = Array.of(...['a', 'b', 'c',])", "x = [...['a', 'b', 'c']]");
    test("x = Array.of(...['a'], ...['b', 'c'])", "x = [...['a'], ...['b', 'c']]");
    test("x = Array.of('a', ...['b', 'c'])", "x = ['a', ...['b', 'c']]");
    test("x = Array.of('a', ...['b', 'c'])", "x = ['a', ...['b', 'c']]");
  }

  @Test
  public void testNoSpread() {
    test("x = Array.of('a', 'b', 'c')", "x = ['a', 'b', 'c']");
    test("x = Array.of('a', ['b', 'c'])", "x = ['a', ['b', 'c']]");
    test("x = Array.of('a', ['b', 'c'],)", "x = ['a', ['b', 'c']]");
  }

  @Test
  public void testNoArgs() {
    test("x = Array.of()", "x = []");
  }
}
