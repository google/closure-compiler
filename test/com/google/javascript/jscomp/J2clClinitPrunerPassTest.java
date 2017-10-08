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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class J2clClinitPrunerPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clClinitPrunerPass(
        compiler, compiler.getChangedScopeNodesForPass("J2clClinitPrunerPass"));
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Override
  protected int getNumRepetitions() {
    // A single run should be sufficient.
    return 1;
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
            "  (void 0, true);",
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
            "  var a = (someClass.$clinit(), true) ? (someClass.$clinit(), void 0) : void 0;",
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
            "    while(true) {",
            "    }",
            "  } else {",
            "  }",
            "  var a = (void 0, true) ? (void 0, void 0) : void 0;",
            "  var b = function() {};",
            "  var c = function c() {};",
            "  [].forEach(function() {});",
            "};"));
  }

  public void testRemoveDuplicates_selfRemoval() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join("var someClass = {};", "someClass.$clinit = function() {}"));

    test(
        LINE_JOINER.join(
            "function someClass$$0clinit() {",
            "  someClass$$0clinit();",
            "}"),
        "function someClass$$0clinit() {}");
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
            "  var a = true ? (someClass.$clinit(), void 0) : void 0;",
            "  var b = function() { someClass.$clinit(); }",
            "  someClass.$clinit();",
            "};"));
  }

  public void testRedundantClinit_returnCtor() {
    test(
        LINE_JOINER.join(
            "var Foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"),
        LINE_JOINER.join(
            "var Foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return new Foo();",
            "};"));
  }

  public void testRedundantClinit_returnCall() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return foo();",
            "};"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return foo();",
            "};"));
  }

  public void testRedundantClinit_exprResult() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  foo();",
            "};"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  foo();",
            "};"));
  }

  public void testRedundantClinit_var() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  var x = foo();",
            "};"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  var x = foo();",
            "};"));
  }

  public void testRedundantClinit_let() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  let x = foo();",
            "};"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  let x = foo();",
            "};"));
  }

  public void testRedundantClinit_const() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  const x = foo();",
            "};"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  const x = foo();",
            "};"));
  }

  public void testRedundantClinit_literalArgs() {
    test(
        LINE_JOINER.join(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo(1);",
            "};"),
        LINE_JOINER.join(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return new Foo(1);",
            "};"));
  }

  public void testRedundantClinit_paramArgs() {
    test(
        LINE_JOINER.join(
            "var Foo = function(a, b) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function(a) {",
            "  Foo.$clinit();",
            "  return new Foo(a, 1);",
            "};"),
        LINE_JOINER.join(
            "var Foo = function(a, b) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function(a) {",
            "  return new Foo(a, 1);",
            "};"));
  }

  public void testRedundantClinit_unsafeArgs() {
    testSame(
        LINE_JOINER.join(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.STATIC_VAR = null;",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo(Foo.STATIC_VAR);",
            "};"));
  }

  public void testRedundantClinit_otherClinit() {
    testSame(
        LINE_JOINER.join(
            "var Foo = function() {",
            "  Foo1.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"));
  }

  public void testRedundantClinit_clinitNotFirstStatement() {
    testSame(
        LINE_JOINER.join(
            "var Foo = function() {",
            "  var x = 1;",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"));
  }

  public void testRedundantClinit_recursiveCall() {
    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  Foo1.$clinit();",
            "  foo();",
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
  }

  public void testFoldClinit_classHierarchy() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};",
            "var someChildClass = {};",
            "someChildClass.$clinit = function() {",
            "  someChildClass.$clinit = function() {};",
            "  someClass.$clinit();",
            "};",
            "someChildClass.someFunction = function() {",
            "  someChildClass.$clinit();",
            "  someClass.$clinit();",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {};",
            "var someChildClass = {};",
            "someChildClass.$clinit = function() {};",
            "someChildClass.someFunction = function() {};"));
  }

  public void testFoldClinit_classHierarchyNonEmpty() {
    test(
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  somefn();",
            "};",
            "var someChildClass = {};",
            "someChildClass.$clinit = function() {",
            "  someChildClass.$clinit = function() {};",
            "  someClass.$clinit();",
            "};",
            "someChildClass.someFunction = function() {",
            "  someChildClass.$clinit();",
            "};"),
        LINE_JOINER.join(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  somefn();",
            "};",
            "var someChildClass = {};",
            "someChildClass.$clinit = function() {",
            "  someClass.$clinit();",
            "};",
            "someChildClass.someFunction = function() {",
            "  someClass.$clinit();",
            "};"));
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
