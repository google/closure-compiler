/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {RefasterJsScanner}.
 *
 * The RefasterJsScanner must be initialized with the compiler used to compile the
 * test code so that the types from the template and the test code match.
 * Therefore, it is important to compile the test code first before creating the
 * scanner, and to reuse the same compiler object for both the test code and the
 * scanner.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
// TODO(mknichel): Make this a SmallTest by disabling threads in the JS Compiler.

@RunWith(JUnit4.class)
public class RefasterJsScannerTest {

  @BeforeClass
  public static void noLogSpam() {
    Logger.getLogger("com.google").setLevel(Level.OFF);
  }

  @Test
  public void testInitialize_missingTemplates() throws Exception {
    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "");
      fail("An exception should have been thrown for missing templates.");
    } catch (IllegalStateException expected) {}

    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "function notATemplate() {}");
      fail("An exception should have been thrown for missing templates.");
    } catch (IllegalStateException expected) {}

    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "function after_foo() {}");
      fail("An exception should have been thrown for missing templates.");
    } catch (IllegalStateException expected) {}
  }

  @Test
  public void testInitialize_missingAfterTemplate() throws Exception {
    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "function before_foo() {'bar'};");
      fail("An exception should have been thrown for missing the after template.");
    } catch (IllegalStateException expected) {}
  }

  @Test
  public void testInitialize_duplicateTemplateName() throws Exception {
    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "function before_foo() {}; function before_foo() {};");
      fail("RefasterJS templates are not allowed to have the same name.");
    } catch (IllegalStateException expected) {}
  }

  @Test
  public void testInitialize_emptyBeforeTemplates() throws Exception {
    try {
      Compiler compiler = createCompiler();
      compileTestCode(compiler, "", "");
      createScanner(compiler, "function before_foo() {}; function after_foo() {};");
      fail("RefasterJS templates are not allowed to be empty!.");
    } catch (IllegalStateException expected) {}
  }

  @Test
  public void testInitialize_success() throws Exception {
    Compiler compiler = createCompiler();
    compileTestCode(compiler, "", "");
    createScanner(compiler, "function before_foo() {'str';}; function after_foo() {};");
  }

  @Test
  public void test_simple() throws Exception {
    String originalCode = "var loc = 'str';";
    String expectedCode = "'bar';";
    String template = ""
        + "function before_foo() {\n"
        + "  var a = 'str';\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  'bar';\n"
        + "}\n";
    assertChanges("", originalCode, template, expectedCode);
  }

  @Test
  public void test_semicolonCorrect() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function Location() {};\n"
        + "/** @type {string} */\n"
        + "Location.prototype.href;\n"
        + "function foo() {}\n"
        + "/** @type {Location} */ var loc;";
    String originalCode = "loc.href = 'str';";
    String expectedCode = "foo();";
    String template = ""
        + "/** @param {Location} loc */"
        + "function before_foo(loc) {\n"
        + "  loc.href = 'str';\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  foo();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_operatorPrecedence() throws Exception {
    String originalCode = "f(1 || 2) && f(3) && f(4) == 5 && 6 == f(7) && 8 + f(9);";
    String expectedCode =
        "(1 || 2) == 0 && 3 == 0 && (4 == 0) == 5 && 6 == (7 == 0) && 8 + (9 == 0);";
    String template = Joiner.on("\n").join(
        "/** @param {?} x */",
        "function before_foo(x) {",
        "  f(x);",
        "};",
        "/** @param {?} x */",
        "function after_foo(x) {",
        "  x == 0;",
        "}");
    assertChanges("", originalCode, template, expectedCode);
  }

  @Test
  public void test_withTypes() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};\n"
        + "/** @type {FooType} */ var obj;";
    String originalCode = "obj.bar();";
    String expectedCode = "obj.baz();";
    String template = ""
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " */\n"
        + "function before_foo(foo) {\n"
        + "  foo.bar();\n"
        + "};\n"
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " */\n"
        + "function after_foo(foo) {\n"
        + "  foo.baz();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_multiLines() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};\n"
        + "FooType.prototype.baz = function() {};";
    String preamble = "var obj = new FooType();\n";
    String postamble = "var someOtherCode = 3;\n";
    String originalCode = ""
        + preamble
        + "obj.bar();\n"
        + "obj.baz();\n"
        + postamble;
    String expectedCode = preamble + postamble;
    String template = ""
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " */\n"
        + "function before_foo(foo) {\n"
        + "  foo.bar();\n"
        + "  foo.baz();\n"
        + "};\n"
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " */\n"
        + "function after_foo(foo) {\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_replaceFunctionArgument() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n"
        + "/** @type {MyClass} */ var clazz;";
    String originalCode = "alert(clazz.foo());";
    String expectedCode = "alert(clazz.bar());";
    String template = ""
        + "/** @param {MyClass} clazz */"
        + "function before_foo(clazz) {\n"
        + "  clazz.foo();\n"
        + "};\n"
        + "/** @param {MyClass} clazz */"
        + "function after_foo(clazz) {\n"
        + "  clazz.bar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_replaceLeftHandSideOfAssignment() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n";
    String originalCode = "MyClass.prototype.foo = function() {};\n";
    String expectedCode = "MyClass.prototype.bar = function() {};\n";
    String template = ""
        + "function before_foo() {\n"
        + "  MyClass.prototype.foo\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  MyClass.prototype.bar\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_replaceRightHandSideOfAssignment() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n";
    String originalCode = "var x = MyClass.prototype.foo;";
    String expectedCode = "var x = MyClass.prototype.bar;";
    String template = ""
        + "function before_foo() {\n"
        + "  MyClass.prototype.foo\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  MyClass.prototype.bar\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_doesNotAddSpuriousNewline() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n"
        + "/** @type {MyClass} */ var clazz;\n";
    String originalCode = "clazz.foo();";
    String expectedCode = "clazz.bar();";
    String template = ""
        + "/** @param {MyClass} clazz */"
        + "function before_foo(clazz) {\n"
        + "  clazz.foo();\n"
        + "};\n"
        + "/** @param {MyClass} clazz */"
        + "function after_foo(clazz) {\n"
        + "  clazz.bar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_throwStatements() throws Exception {
    String externs = "";
    String originalCode = "throw Error('foo');";
    String expectedCode = "throw getError();";
    String template = ""
        + "/** @param {string} msg */\n"
        + "function before_template(msg) {\n"
        + "  throw Error(msg);\n"
        + "}\n"
        + "/** @param {string} msg */\n"
        + "function after_template(msg) {\n"
        + "  throw getError();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = "function f() {throw Error('foo');}";
    expectedCode = "function f() {throw getError();}";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = ""
        + "if (true) {\n"
        + "  throw Error('foo');\n"
        + "}";
    expectedCode = ""
        + "if (true) {\n"
        + "  throw getError();\n"
        + "}";
    assertChanges(externs, originalCode, template, expectedCode);
}

  @Test
  public void test_whileStatements() throws Exception {
    String externs = "/** @return {string} */ function getFoo() {return 'foo';}";
    String originalCode = "while(getFoo()) {}";
    String expectedCode = "while(getBar()) {}";
    String template = ""
        + "function before_template() {\n"
        + "  getFoo();\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getBar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_doWhileStatements() throws Exception {
    String externs = "/** @return {string} */ function getFoo() {return 'foo';}";
    String originalCode = "do {} while(getFoo());";
    String expectedCode = "do {} while(getBar());";
    String template = ""
        + "function before_template() {\n"
        + "  getFoo();\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getBar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_returnStatement() throws Exception {
    String externs = "/** @return {string} */ function getFoo() {return 'foo';}";
    String originalCode = "function f() { return getFoo(); }";
    String expectedCode = "function f() { return getBar(); }";
    String template = ""
        + "function before_template() {\n"
        + "  getFoo();\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getBar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = "function f() { return getFoo() == 'foo'; }";
    expectedCode = "function f() { return getBar() == 'foo'; }";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_switchStatement() throws Exception {
    String externs = "/** @return {string} */ function getFoo() {return 'foo';}";
    String originalCode = ""
        + "switch(getFoo()) {\n"
        + "  default:\n"
        + "    break;\n"
        + "}";
    String expectedCode = ""
        + "switch(getBar()) {\n"
        + "  default:\n"
        + "    break;\n"
        + "}";
    String template = ""
        + "function before_template() {\n"
        + "  getFoo();\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getBar();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_caseStatement() throws Exception {
    String externs = ""
        + "var str = 'foo';\n"
        + "var CONSTANT = 'bar';\n";
    String originalCode = ""
        + "switch(str) {\n"
        + "  case CONSTANT:\n"
        + "    break;\n"
        + "}";
    String expectedCode = ""
        + "switch(str) {\n"
        + "  case getValue():\n"
        + "    break;\n"
        + "}";
    String template = ""
        + "function before_template() {\n"
        + "  CONSTANT\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getValue()\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_forStatement() throws Exception {
    String externs = ""
        + "var obj = {};\n"
        + "obj.prop = 6;"
        + "var CONSTANT = 3;\n";
    String originalCode = "for (var i = CONSTANT; i < 5; i++) {}";
    String expectedCode = "for (var i = CONSTANT2; i < 5; i++) {}";
    String template = ""
        + "function before_template() {\n"
        + "  CONSTANT\n"
        + "}\n"
        + "function after_template() {\n"
        + "  CONSTANT2\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = "for (var i = 0; i < CONSTANT; i++) {}";
    expectedCode = "for (var i = 0; i < CONSTANT2; i++) {}";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = "for (var i = 0; i < CONSTANT; i++) {}";
    expectedCode = "for (var i = 0; i < obj.prop; i++) {}";
    template = ""
        + "function before_template() {\n"
        + "  CONSTANT\n"
        + "}\n"
        + "function after_template() {\n"
        + "  obj.prop\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);

    originalCode = "for (var prop in obj) {}";
    expectedCode = "for (var prop in getObj()) {}";
    template = ""
        + "function before_template() {\n"
        + "  obj\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getObj()\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_comparisons() throws Exception {
    String externs = ""
        + "var obj = {};\n"
        + "obj.prop = 5;";
    String originalCode = "if (obj.prop == 5) {}";
    String expectedCode = "if (3 == 5) {}";
    String template = ""
        + "function before_template() {\n"
        + "  obj.prop;\n"
        + "}\n"
        + "function after_template() {\n"
        + "  3;\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_arrayAccess() throws Exception {
    String externs = ""
        + "var arr = [];\n"
        + "var i = 0;\n"
        + "/** @return {number} */ function getNewIndex() {}";
    String originalCode = "arr[i];";
    String expectedCode = "arr[getNewIndex()];";
    String template = ""
        + "function before_template() {\n"
        + "  i;\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getNewIndex();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_functionCalls() throws Exception {
    // Assigning the function as a property of an object is important to this test since it
    // tracks a corner case in the TemplateAstMatcher code.
    String externs = "var foo = {}; /** @return {number} */ foo.someFn = function() {}";
    String originalCode = "foo.someFn();";
    String expectedCode = "foo.someFn().someOtherFn();";
    String template = ""
        + "/** @param {function():number} fn */\n"
        + "function before_template(fn) {\n"
        + "  fn();\n"
        + "}\n"
        + "/** @param {function():number} fn */\n"
        + "function after_template(fn) {\n"
        + "  fn().someOtherFn();\n"
        + "}\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_strictSubtypeMatching() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function T() {};\n"
        + "/** @type {string} */\n"
        + "T.prototype.p;\n"
        + "/** @constructor @extends {T} */\n"
        + "function S() {};\n"
        + "/** @param {!T} someT */\n"
        + "function setP(someT) {};\n";
    String template = ""
        + "/** @param {!T} t */\n"
        + "function before_template(t) {\n"
        + "  t.p = 'foo';\n"
        + "}\n"
        + "/** @param {!T} t */\n"
        + "function after_template(t) {\n"
        + "  setP(t);\n"
        + "}\n";
    String originalCode = "theT.p = 'foo';";
    String expectedCode = "setP(theT);";

    // {!T} matches {!T}
    assertChanges(externs + "/** @type {!T} */ var theT;", originalCode, template, expectedCode);

    // {?T} in the code does not match {!T} in the template.
    assertChanges(
        externs + "/** @type {?T} */ var theT;",
        originalCode,
        template,
        (String) null); // No changes.

    // {unknown} does not match {!T}
    assertChanges(externs + "var theT;", originalCode, template, (String) null); // No changes.

    // {!S} matches {!T}
    assertChanges(externs + "/** @type {!S} */ var theT;", originalCode, template, expectedCode);

    // {?S} does not match {!T}
    assertChanges(
        externs + "/** @type {?S} */ var theT;",
        originalCode,
        template,
        (String) null); // No changes.
  }

  @Test
  public void test_templatesEvaluatedInOrder() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function T() {};\n"
        + "/** @type {string} */\n"
        + "T.prototype.p;\n"
        + "/** @constructor @extends {T} */\n"
        + "function S() {};\n"
        + "/** @param {!T} someT */\n"
        + "function setP(someT) {};\n"
        + "/** @param {!S} someS */\n"
        + "function setPonS(someS) {};\n"
        + "/** @type {!T} */ var theT;"
        + "/** @type {!S} */ var theS;";
    String template = ""
        + "/** @param {!S} s */\n"
        + "function before_template_S(s) {\n"
        + "  s.p = 'foo';\n"
        + "}\n"
        + "/** @param {!S} s */\n"
        + "function after_template_S(t) {\n"
        + "  setPonS(s);\n"
        + "}\n"
        + "\n"
        + "/** @param {!T} t */\n"
        + "function before_template_T(t) {\n"
        + "  t.p = 'foo';\n"
        + "}\n"
        + "/** @param {!T} t */\n"
        + "function after_template_T(t) {\n"
        + "  setP(t);\n"
        + "}\n";
    String originalCode = "theT.p = 'foo'; theS.p = 'foo';";
    // Templates are evaluated in order:
    //  - theT.p does not match before_template_S but matches before_template_T
    //  - theS.p would match either template (see {@link #test_strictSubtypeMatching}),
    //    but since before_template_S comes first it takes precedence.
    String expectedCode = "setP(theT); setPonS(theS);";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_es6() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "/** @param {string} str */"
        + "FooType.prototype.bar = function(str) {};\n"
        + "/** @param {string} str */"
        + "FooType.prototype.baz = function(str) {};\n";
    String template = ""
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " * @param {string} str\n"
        + " */\n"
        + "function before_foo(foo, str) {\n"
        + "  foo.bar(str);\n"
        + "};\n"
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " * @param {string} str\n"
        + " */\n"
        + "function after_foo(foo, str) {\n"
        + "  foo.baz(str);\n"
        + "}\n";
    String originalCode = ""
        + "goog.module('foo.bar');\n"
        + "const STR = '3';\n"
        + "const Clazz = class {\n"
        + "  constructor() { /** @const */ this.obj = new FooType(); }\n"
        + "  someMethod() { this.obj.bar(STR); }\n"
        + "};\n"
        + "exports.Clazz = Clazz;\n";
    String expectedCode = ""
        + "goog.module('foo.bar');\n"
        + "const STR = '3';\n"
        + "const Clazz = class {\n"
        + "  constructor() { /** @const */ this.obj = new FooType(); }\n"
        + "  someMethod() { this.obj.baz(STR); }\n"
        + "};\n"
        + "exports.Clazz = Clazz;\n";
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_unknownTemplateTypes() throws Exception {
    // By declaring a new type in the template code that does not appear in the original code,
    // the result of this refactoring should be a no-op. However, if template type matching isn't
    // correct, the template type could be treated as an unknown type which would incorrectly
    // match the original code. This test ensures this behavior is right.
    String externs = "";
    String originalCode = ""
        + "/** @constructor */\n"
        + "function Clazz() {};\n"
        + "var cls = new Clazz();\n"
        + "cls.showError('boo');\n";
    String template = ""
        + "/** @constructor */\n"
        + "function SomeClassNotInCompilationUnit() {};\n"
        + "var foo = new SomeClassNotInCompilationUnit();\n"
        + "foo.showError('bar');\n"
        + "\n"
        + "/**"
        + " * @param {SomeClassNotInCompilationUnit} obj\n"
        + " * @param {string} msg\n"
        + " */\n"
        + "function before_template(obj, msg) {\n"
        + "  obj.showError(msg);\n"
        + "}\n"
        + "/**"
        + " * @param {SomeClassNotInCompilationUnit} obj\n"
        + " * @param {string} msg\n"
        + " */\n"
        + "function after_template(obj, msg) {\n"
        + "  obj.showError(msg, false);\n"
        + "}\n";
    assertChanges(externs, originalCode, template, (String) null);
  }

  @Test
  public void test_unknownTemplateTypesNonNullable() throws Exception {
    // By declaring a new type in the template code that does not appear in the original code,
    // the result of this refactoring should be a no-op. However, if template type matching isn't
    // correct, the template type could be treated as an unknown type which would incorrectly
    // match the original code. This test ensures this behavior is right.
    String externs = "";
    String originalCode = ""
        + "/** @constructor */\n"
        + "function Clazz() {};\n"
        + "var cls = new Clazz();\n"
        + "cls.showError('boo');\n";
    String template = ""
        + "/** @constructor */\n"
        + "function SomeClassNotInCompilationUnit() {};\n"
        + "var foo = new SomeClassNotInCompilationUnit();\n"
        + "foo.showError('bar');\n"
        + "\n"
        + "/**"
        + " * @param {!SomeClassNotInCompilationUnit} obj\n"
        + " * @param {string} msg\n"
        + " */\n"
        + "function before_template(obj, msg) {\n"
        + "  obj.showError(msg);\n"
        + "}\n"
        + "/**"
        + " * @param {!SomeClassNotInCompilationUnit} obj\n"
        + " * @param {string} msg\n"
        + " */\n"
        + "function after_template(obj, msg) {\n"
        + "  obj.showError(msg, false);\n"
        + "}\n";
    assertChanges(externs, originalCode, template, (String) null);
  }

  @Test
  public void test_importConstGoogRequire() throws Exception {
    String externs = "";
    String originalCode =
        Joiner.on('\n').join(
            "goog.module('testcase');",
            "",
            "function f() { var loc = 'str'; }");
    String expectedCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
            "const foo = goog.require('goog.foo');",
            "",
            "function f() { var loc = foo.f(); }");
    String template =
        Joiner.on('\n').join(
            "/**",
            "* +require {goog.foo}",
            "*/",
            "function before_foo() {",
            "  var a = 'str';",
            "};",
            "function after_foo() {",
            "  var a = goog.foo.f();",
            "}");
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_importConstGoogRequireMultipleImports() throws Exception {
    String externs = "";
    String originalCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
            "const alpha = goog.require('goog.alpha');",
            "const omega = goog.require('goog.omega');",
            "",
            "function f() { var loc = 'str'; }");
    String expectedCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
            "const alpha = goog.require('goog.alpha');",
            "const foo = goog.require('goog.foo');",
            "const omega = goog.require('goog.omega');",
            "",
            "function f() { var loc = foo.f(); }");
    String template =
        Joiner.on('\n').join(
            "/**",
            "* +require {goog.foo}",
            "*/",
            "function before_foo() {",
            "  var a = 'str';",
            "};",
            "function after_foo() {",
            "  var a = goog.foo.f();",
            "}");
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_importConstGoogRequireAlreadyNamed() throws Exception {
    String externs = "";
    String originalCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
            "const bar = goog.require('goog.foo');",
            "",
            "var loc = 'str';");
    String expectedCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
            "const bar = goog.require('goog.foo');",
            "",
            "var loc = bar.f();");
    String template =
        Joiner.on('\n').join(
            "/**",
            "* +require {goog.foo}",
            "*/",
            "function before_foo() {",
            "  var a = 'str';",
            "};",
            "function after_foo() {",
            "  var a = goog.foo.f();",
            "}");
    assertChanges(externs, originalCode, template, expectedCode);
  }

  @Test
  public void test_multipleChoices() throws Exception {
    String externs = "";

    String originalCode = Joiner.on('\n').join("goog.module('testcase');", "", "var loc = 'str';");
    String expectedCode1 =
        Joiner.on('\n').join("goog.module('testcase');", "", "var loc = 'foo' + 'str';");
    String expectedCode2 =
        Joiner.on('\n').join("goog.module('testcase');", "", "var loc = 'bar' + 'str';");
    String template =
        Joiner.on('\n')
            .join(
                "/**",
                "* @param {string} str",
                "*/",
                "function before_foo(str) {",
                "  var a = str;",
                "};",
                "/**",
                " * @param {string} str",
                "*/",
                "function after_option_1_foo(str) {",
                "  var a = 'foo' + str",
                "}",
                "/**",
                " * @param {string} str",
                "*/",
                "function after_option_2_foo(str) {",
                "  var a = 'bar' + str",
                "}");
    assertChanges(externs, originalCode, template, expectedCode1, expectedCode2);
  }

  @Test
  public void test_multipleChoicesDifferentImports() throws Exception {
    String externs = "";

    String originalCode = Joiner.on('\n').join("goog.module('testcase');", "", "var loc = 'str';");
    String expectedCode1 =
        Joiner.on('\n')
            .join(
                "goog.module('testcase');",
                "const bar = goog.require('goog.bar');",
                "const foo = goog.require('goog.foo');",
                "",
                "var loc = foo.f(bar.b('option1'));");
    String expectedCode2 =
        Joiner.on('\n')
            .join(
                "goog.module('testcase');",
                "const baz = goog.require('goog.baz');",
                "const foo = goog.require('goog.foo');",
                "",
                "var loc = foo.f(baz.f('option2'));");
    String template =
        Joiner.on('\n')
            .join(
                "/**",
                "* +require {goog.foo}",
                "*/",
                "function before_foo() {",
                "  var a = 'str';",
                "};",
                "/**",
                "* +require {goog.foo}", // Duplicates should be ok
                "* +require {goog.bar}",
                "*/",
                "function after_option_1_foo() {",
                "  var a = goog.foo.f(goog.bar.b('option1'))",
                "}",
                "/**",
                "* +require {goog.baz}",
                "*/",
                "function after_option_2_foo() {",
                "  var a = goog.foo.f(goog.baz.f('option2'))",
                "}");
    assertChanges(externs, originalCode, template, expectedCode1, expectedCode2);
  }

  @Test
  public void test_withGetCssName() throws Exception {
    String externs = "";

    String originalCode = Joiner.on('\n').join(
        "goog.module('testcase');",
        "",
        "document.getElementById('foo').class = goog.getCssName('str');");
    String expectedCode =
        Joiner.on('\n').join(
        "goog.module('testcase');",
        "",
        "document.getElementById('foo').class = 'foo' + goog.getCssName('str');");
    String template =
        Joiner.on('\n')
            .join(
                "/**",
                "* @param {?} obj",
                "* @param {?} value",
                "*/",
                "function before_foo(obj, value) {",
                "  obj.class = value;",
                "};",
                "/**",
                " * @param {?} obj",
                " * @param {?} value",
                "*/",
                "function after_foo(obj, value) {",
                "  obj.class = 'foo' + value;",
                "}");
    assertChanges(externs, originalCode, template, expectedCode);
  }

  private static Compiler createCompiler() {
    return new Compiler();
  }

  private static RefasterJsScanner createScanner(Compiler compiler, String template)
      throws Exception {
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplateFromCode(template);
    scanner.initialize(compiler);
    return scanner;
  }

  private static void compileTestCode(Compiler compiler, String testCode, String externs) {
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", "function Symbol() {};" + externs)),
        ImmutableList.of(SourceFile.fromCode("test", testCode)),
        options);
  }

  private static void assertChanges(
      String externs, String originalCode, String refasterJsTemplate, String... expectedChoices)
      throws Exception {
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplateFromCode(refasterJsTemplate);

    RefactoringDriver driver =
        new RefactoringDriver.Builder()
            .addExternsFromCode("function Symbol() {};" + externs)
            .addInputsFromCode(originalCode)
            .addInputsFromCode(
                Joiner.on('\n')
                    .join(
                        "goog.module('goog.foo');",
                        "/** Trivial function. \n",
                        " * @return {string}",
                        " */",
                        "exports.f = function () { ",
                        "  return 'str';",
                        "};"),
                "foo.js")
            .build();
    List<SuggestedFix> fixes = driver.drive(scanner);
    ImmutableList<ImmutableMap<String, String>> outputChoices =
        ApplySuggestedFixes.applyAllSuggestedFixChoicesToCode(
            fixes, ImmutableMap.of("input", originalCode));
    assertThat(outputChoices).hasSize(expectedChoices.length);

    for (int i = 0; i < outputChoices.size(); i++) {
      assertEquals("Choice " + i, expectedChoices[i], outputChoices.get(i).get("input"));
    }
  }
}
