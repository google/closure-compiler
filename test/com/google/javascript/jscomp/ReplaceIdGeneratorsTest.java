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

  public void testBackwardCompat() {
    test("foo.bar = goog.events.getUniqueId('foo_bar')",
         "foo.bar = 'a'");
  }

  public void testSimple() {
    test("/** @idGenerator */ foo.getUniqueId = function() {};" +
         "foo.bar = foo.getUniqueId('foo_bar')",

         "foo.getUniqueId = function() {};" +
         "foo.bar = 'a'");

    test("/** @idGenerator */ goog.events.getUniqueId = function() {};" +
        "foo1 = goog.events.getUniqueId('foo1');" +
        "foo1 = goog.events.getUniqueId('foo1');",

        "goog.events.getUniqueId = function() {};" +
        "foo1 = 'a';" +
        "foo1 = 'b';");
  }

  public void testSimpleConsistent() {
    test("/** @consistentIdGenerator */ id = function() {};" +
         "foo.bar = id('foo_bar')",

         "id = function() {};" +
         "foo.bar = 'a'");

    test("/** @consistentIdGenerator */ id = function() {};" +
         "f1 = id('f1');" +
         "f1 = id('f1')",

         "id = function() {};" +
         "f1 = 'a';" +
         "f1 = 'a'");

    test("/** @consistentIdGenerator */ id = function() {};" +
        "f1 = id('f1');" +
        "f1 = id('f1');" +
        "f1 = id('f1')",

        "id = function() {};" +
        "f1 = 'a';" +
        "f1 = 'a';" +
        "f1 = 'a'");
  }

  public void testVar() {
    test("/** @consistentIdGenerator */ var id = function() {};" +
         "foo.bar = id('foo_bar')",

         "var id = function() {};" +
         "foo.bar = 'a'");
  }

  public void testObjLit() {
    test("/** @consistentIdGenerator */ get.id = function() {};" +
         "foo.bar = {a: get.id('foo_bar')}",

         "get.id = function() {};" +
         "foo.bar = {a: 'a'}");
  }

  public void testTwoGenerators() {
    test("/** @idGenerator */ var id1 = function() {};" +
         "/** @idGenerator */ var id2 = function() {};" +
         "f1 = id1('1');" +
         "f2 = id1('1');" +
         "f3 = id2('1');" +
         "f4 = id2('1');",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "f1 = 'a';" +
         "f2 = 'b';" +
         "f3 = 'a';" +
         "f4 = 'b';");
  }

  public void testTwoMixedGenerators() {
    test("/** @idGenerator */ var id1 = function() {};" +
         "/** @consistentIdGenerator */ var id2 = function() {};" +
         "f1 = id1('1');" +
         "f2 = id1('1');" +
         "f3 = id2('1');" +
         "f4 = id2('1');",

         "var id1 = function() {};" +
         "var id2 = function() {};" +
         "f1 = 'a';" +
         "f2 = 'b';" +
         "f3 = 'a';" +
         "f4 = 'a';");
  }

  public void testLocalCall() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "function Foo() { id('foo'); }"},
        ReplaceIdGenerators.NON_GLOBAL_ID_GENERATOR_CALL);
  }

  public void testConditionalCall() {
    testSame(new String[] {"/** @idGenerator */ var id = function() {}; " +
                           "if(x) id('foo');"},
        ReplaceIdGenerators.CONDITIONAL_ID_GENERATOR_CALL);

    test("/** @consistentIdGenerator */ var id = function() {};" +
        "function fb() {foo.bar = id('foo_bar')}",

        "var id = function() {};" +
        "function fb() {foo.bar = 'a'}");
  }

  public void testConflictingIdGenerator() {
    testSame(new String[] {"/** @idGenerator \n @consistentIdGenerator \n*/" +
                           "var id = function() {}; "},
        ReplaceIdGenerators.CONFLICTING_GENERATOR_TYPE);

    test("/** @consistentIdGenerator */ var id = function() {};" +
        "if (x) {foo.bar = id('foo_bar')}",

        "var id = function() {};" +
        "if (x) {foo.bar = 'a'}");
  }
}