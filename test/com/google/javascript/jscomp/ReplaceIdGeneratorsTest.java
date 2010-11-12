/*
 * Copyright 2009 The Closure Compiler Authors.
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


/**
 * Tests for {@link ReplaceIdGenerators}.
 *
 */
public class ReplaceIdGeneratorsTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ReplaceIdGenerators(
        compiler,
        new ImmutableSet.Builder<String>()
        .add("goog.events.getUniqueId")
        .add("goog.place.getUniqueId").build());
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testAssign() {
    test("foo.bar = goog.events.getUniqueId('foo_bar')",
         "foo.bar = 'a'");
  }

  public void testObjectLiteral() {
    test("foo = { bar : goog.events.getUniqueId('foo_bar')}",
         "foo = { bar : 'a' }");
  }

  public void testTwoNamespaces() {
    test("foo.bar = goog.events.getUniqueId('foo_bar');\n"
         + "baz.blah = goog.place.getUniqueId('baz_blah');\n",
         "foo.bar = 'a';\n"
         + "baz.blah = 'a'\n");
  }

  public void testLocalCall() {
    testSame(new String[] {
          "function Foo() { goog.events.getUniqueId('foo'); }"
        },
        ReplaceIdGenerators.NON_GLOBAL_ID_GENERATOR_CALL);
  }

  public void testConditionalCall() {
    testSame(new String[] {"if (x) foo = goog.events.getUniqueId('foo')"},
             ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);
  }
}
