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

public class J2clEmptyClinitPrunerPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clEmptyClinitPrunerPass(compiler);
  }

  public void testFoldClinit() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};"),
        LINE_JOINER.join("var someClass = {};", "someClass.$clinit = function() {};"));
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass.$clinit();",
            "  someClass.$clinit();",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join("var someClass = {};", "someClass.$clinit = function() {};"));
  }

  public void testFoldClinit_invalidCandidates() {
    testSame(
        LINE_JOINER.join(
            "var someClass = /** @constructor */ function() {};",
            "someClass.foo = function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass.foo();",
            "};"));
    testSame(
        LINE_JOINER.join(
            "var someClass = {}, otherClass = {};",
            "someClass.$clinit = function() {",
            "  otherClass.$clinit = function() {};",
            "  someClass.$clinit();",
            "};"));
    testSame(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$notClinit = function() {",
            "  someClass.$notClinit = function() {};",
            "};"));
  }
}
