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

public class J2clClinitPrunerPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clClinitPrunerPass(compiler);
  }

  public void testRemoveDuplicates() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "  0;",
            "};"));
  }

  public void testRemoveDuplicates_commaExpressions() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  (someClass.$clinit(), true);",
            "  (someClass.$clinit(), true);",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  (someClass.$clinit(), true);",
            "  (0, true);",
            "};"));
  }

  public void testRemoveDuplicates_controlBlocks() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "  if (true) {",
            "    someClass.$clinit();",
            "    while(true) {",
            "      someClass.$clinit();",
            "    }",
            "  } else {",
            "    someClass.$clinit();",
            "  }",
            "  var a = (someClass.$clinit(), true) ? (someClass.$clinit(), 0) : 0;",
            "  var b = function() { someClass.$clinit(); };",
            "  var c = function c() { someClass.$clinit(); };",
            "  [].forEach(function() { someClass.$clinit(); });",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "  if (true) {",
            "    0;",
            "    while(true) {",
            "      0;",
            "    }",
            "  } else {",
            "    0;",
            "  }",
            "  var a = (0, true) ? (0, 0) : 0;",
            "  var b = function() { 0; };",
            "  var c = function c() { 0; };",
            "  [].forEach(function() { 0; });",
            "  0;",
            "};"));
  }

  public void testRemoveDuplicates_selfRemoval() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join("var someClass = {};", "someClass.$clinit = function() {0;}"));

    test(
        LINE_JOINER.join(
            "function someClass$$0clinit() {",
            "  someClass$$0clinit();",
            "}"),
        "function someClass$$0clinit() {0;}");
  }

  public void testRemoveDuplicates_jumpFunctionDeclarations() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  myFunc();",
            "  someClass.$clinit();",
            "  function myFunc() {",
            "    someClass.$clinit();",
            "    someClass.$clinit();",
            "  }",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  myFunc();",
            "  someClass.$clinit();",
            "  function myFunc() {",
            "    someClass.$clinit();",
            "    0;",
            "  }",
            "};"));
  }

  public void testRemoveDuplicates_avoidControlBlocks() {
    testSame(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.anotherMethod = function() {",
            "  (false && someClass.$clinit());",
            "  (true || someClass.$clinit());",
            "  if (true) {",
            "    someClass.$clinit();",
            "  } else {",
            "    someClass.$clinit();",
            "  }",
            "  while(false) {",
            "    someClass.$clinit();",
            "  }",
            "  for(;false;) {",
            "    someClass.$clinit();",
            "  }",
            "  try {",
            "    someClass.$clinit();",
            "  } catch(e) {",
            "    someClass.$clinit();",
            "  }",
            "  switch(2) {",
            "    case 1: someClass.$clinit(); break;",
            "    case 2: break;",
            "    default: someClass.$clinit();",
            "  }",
            "  var a = true ? (someClass.$clinit(), 0) : 0;",
            "  var b = function() { someClass.$clinit(); }",
            "  someClass.$clinit();",
            "};"));
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
            "};"));
    testSame(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$notClinit = function() {",
            "  someClass.$notClinit = function() {};",
            "};"));
  }
}
