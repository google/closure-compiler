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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
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

  @Test
  public void testRemoveDuplicates() {
    test(
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "  someClass.$clinit();",
            "};"),
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  someClass.$clinit();",
            "};"));
  }

  @Test
  public void testRemoveDuplicates_commaExpressions() {
    test(
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  (someClass.$clinit(), true);",
            "  (someClass.$clinit(), true);",
            "};"),
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {}",
            "someClass.someOtherFunction = function() {",
            "  (someClass.$clinit(), true);",
            "  (void 0, true);",
            "};"));
  }

  @Test
  public void testRemoveDuplicates_controlBlocks() {
    test(
        lines(
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
        lines(
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

  @Test
  public void testRemoveDuplicates_selfRemoval() {
    test(
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit();",
            "};"),
        lines("var someClass = {};", "someClass.$clinit = function() {}"));

    test(
        lines(
            "function someClass$$0clinit() {",
            "  someClass$$0clinit();",
            "}"),
        "function someClass$$0clinit() {}");
  }

  @Test
  public void testRemoveDuplicates_jumpFunctionDeclarations() {
    test(
        lines(
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
        lines(
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

  @Test
  public void testRemoveDuplicates_avoidControlBlocks() {
    testSame(
        lines(
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

  @Test
  public void testRemoveDuplicates_avoidRemovalAcrossScriptRoots() {
    testSame(
        new FlatSources(
            ImmutableList.of(
                SourceFile.fromCode(
                    "file1",
                    lines(
                        "var someClass = {};",
                        "someClass.$clinit = function() {}",
                        "someClass.$clinit();")),
                SourceFile.fromCode("file2", lines("someClass.$clinit();")))));
  }

  @Test
  public void testRedundantClinit_returnCtor() {
    test(
        lines(
            "var Foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"),
        lines(
            "var Foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return new Foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_returnCall() {
    test(
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return foo();",
            "};"),
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_exprResult() {
    test(
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  foo();",
            "};"),
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_var() {
    test(
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  var x = foo();",
            "};"),
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  var x = foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_let() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  let x = foo();",
            "};"),
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  let x = foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_const() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    test(
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  const x = foo();",
            "};"),
        lines(
            "var foo = function() {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  const x = foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_literalArgs() {
    test(
        lines(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo(1);",
            "};"),
        lines(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  return new Foo(1);",
            "};"));
  }

  @Test
  public void testRedundantClinit_paramArgs() {
    test(
        lines(
            "var Foo = function(a, b) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function(a) {",
            "  Foo.$clinit();",
            "  return new Foo(a, 1);",
            "};"),
        lines(
            "var Foo = function(a, b) {",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function(a) {",
            "  return new Foo(a, 1);",
            "};"));
  }

  @Test
  public void testRedundantClinit_unsafeArgs() {
    testSame(
        lines(
            "var Foo = function(a) {",
            "  Foo.$clinit();",
            "};",
            "Foo.STATIC_VAR = null;",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo(Foo.STATIC_VAR);",
            "};"));
  }

  @Test
  public void testRedundantClinit_otherClinit() {
    testSame(
        lines(
            "var Foo = function() {",
            "  Foo1.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_clinitNotFirstStatement() {
    testSame(
        lines(
            "var Foo = function() {",
            "  var x = 1;",
            "  Foo.$clinit();",
            "};",
            "Foo.ctor = function() {",
            "  Foo.$clinit();",
            "  return new Foo();",
            "};"));
  }

  @Test
  public void testRedundantClinit_recursiveCall() {
    testSame(
        lines(
            "var foo = function() {",
            "  Foo1.$clinit();",
            "  foo();",
            "};"));
  }

  @Test
  public void testFoldClinit() {
    test(
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "};"),
        lines("var someClass = {};", "someClass.$clinit = function() {};"));
  }

  @Test
  public void testFoldClinit_classHierarchy() {
    test(
        lines(
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
        lines(
            "var someClass = {};",
            "someClass.$clinit = function() {};",
            "var someChildClass = {};",
            "someChildClass.$clinit = function() {};",
            "someChildClass.someFunction = function() {};"));
  }

  @Test
  public void testFoldClinit_classHierarchyNonEmpty() {
    test(
        lines(
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
        lines(
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

  @Test
  public void testFoldClinit_invalidCandidates() {
    testSame(
        lines(
            "var someClass = /** @constructor */ function() {};",
            "someClass.foo = function() {};",
            "someClass.$clinit = function() {",
            "  someClass.$clinit = function() {};",
            "  someClass.foo();",
            "};"));
    testSame(
        lines(
            "var someClass = {}, otherClass = {};",
            "someClass.$clinit = function() {",
            "  otherClass.$clinit = function() {};",
            "};"));
    testSame(
        lines(
            "var someClass = {};",
            "someClass.$notClinit = function() {",
            "  someClass.$notClinit = function() {};",
            "};"));
  }
}
