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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class J2clConstantHoisterPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new J2clConstantHoisterPass(compiler);
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testHoistClinitConstantAssignments() {
    test(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass$foo = true;",
            "  someClass$bar = 'hey';",
            "  someClass$buzz = function() { return someClass$bar; };",
            "};",
            "var someClass$foo = false;",
            "var someClass$bar = null;",
            "var someClass$buzz = null;"),
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};",
            "var someClass$foo = true;",
            "var someClass$bar = 'hey';",
            "var someClass$buzz = function(){ return someClass$bar; };"));
  }

  @Test
  public void testHoistClinitConstantAssignments_assignmentAfterInit() {
    test(
        LINE_JOINER.join(
            "var someClass = /** @constructor */ function() {};",
            "var someClass$foo = false;",
            "var someClass$bar = null;",
            "var someClass$buzz = null;",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass$foo = true;",
            "  someClass$bar = 'hey';",
            "  someClass$buzz = function() { return someClass$bar; };",
            "};"),
        LINE_JOINER.join(
            "var someClass = /** @constructor */ function() {};",
            "var someClass$foo = true;",
            "var someClass$bar = 'hey';",
            "var someClass$buzz = function(){ return someClass$bar; };",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};"));
  }

  @Test
  public void testHoistClinitConstantAssignments_stubInit() {
    test(
        LINE_JOINER.join(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass$foo = true;",
            "  someClass$bar = 'hey';",
            "  someClass$buzz = function() { return someClass$bar; };",
            "};",
            "var someClass$foo = false;",
            "var someClass$bar;",
            "var someClass$buzz;"),
        LINE_JOINER.join(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};",
            "var someClass$foo = true;",
            "var someClass$bar = 'hey';",
            "var someClass$buzz = function(){ return someClass$bar; };"));
  }

  @Test
  public void testHoistClinitConstantAssignments_avoidUnsafe() {
    testSame(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass$foo1 = true;", // More than two assignments.
            "  someClass$foo2 = true;", // More than two assignments.
            "  someClass$bar = true;", // Also initialized by another method.
            "  someClass$zoo = true;", // Not initialized in declaration phase.
            "  someClass$hoo = someClass$zoo;", // Not literal value.
            "};",
            "someClass.$otherMethod = function() {",
            "  someClass$foo1 |= true;", // Compound assignment to make it less trivial to detect.
            "  someClass$foo2++;", // Compound assignment to make it less trivial to detect.
            "  someClass$bar = true;",
            "};",
            "var someClass$foo1 = false;",
            "var someClass$foo2 = false;",
            "var someClass$hoo = false;"));
  }

  /**
   * Tests that hoisting works as expected when encountering a clinit that has been devirtualized.
   */
  @Test
  public void testHoistClinitConstantAssignments_devirtualized() {
    test(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "var someClass$$0clinit = function() {",
            "  someClass$$0clinit = function() {};",
            "  someClass$shouldHoist = true;",
            "  someClass$shouldNotHoist = new Object();",
            "};",
            "var someClass$shouldHoist = false;",
            "var someClass$shouldNotHoist = null;"),
        lines(
            "var someClass = /** @constructor */ function() {};",
            "var someClass$$0clinit = function() {",
            "  someClass$$0clinit = function() {};",
            "  someClass$shouldNotHoist = new Object();",
            "};",
            "var someClass$shouldHoist = true;",
            "var someClass$shouldNotHoist = null;"));
  }

  @Test
  public void testHoistClinitConstantAssignments_avoidUnsafeFunc() {
    testSame(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  var x = 10;",
            "  someClass$foo = function() { return x; };", // Depends on the scope of the clinit.
            "};",
            "var someClass$foo = null;"));
    testSame(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  var x = 10;",
            "  someClass$foo = function() { return 1; };", // x is assumed to be used
            "};",
            "var someClass$foo = null;"));
  }
}
