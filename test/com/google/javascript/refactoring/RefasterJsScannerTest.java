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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

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
    assertChanges("", originalCode, expectedCode, template);
  }

  @Test
  public void test_semicolonCorrect() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function Location() {};\n"
        + "/** @type {string} */\n"
        + "Location.prototype.href;\n"
        + "function foo() {}\n"
        + "/** @type {Location} var loc;";
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_withTypes() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};\n"
        + "var obj = new FooType();";
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_multiLines() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};"
        + "FooType.prototype.baz = function() {};";
    String preamble = "var obj = new FooType();\n";
    String postamble = "var someOtherCode = 3;\n";
    String originalCode = ""
        + preamble
        + "obj.bar();\n"
        + "obj.baz();\n"
        + postamble;
    // TODO(mknichel): Correctly handle removing newlines in the multiline case.
    String expectedCode = preamble + "\n\n" + postamble;
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_replaceFunctionArgument() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n"
        + "var clazz = new MyClass();";
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
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_doesNotAddSpuriousNewline() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function MyClass() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n"
        + "var clazz = new MyClass();\n";
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
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = "function f() {throw Error('foo');}";
    expectedCode = "function f() {throw getError();}";
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = ""
        + "if (true) {\n"
        + "  throw Error('foo');\n"
        + "}";
    expectedCode = ""
        + "if (true) {\n"
        + "  throw getError();\n"
        + "}";
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_returnStatement() throws Exception {
    String externs = "/** @return {string} */ function getFoo() {return 'foo';}";
    String originalCode = "function() { return getFoo(); }";
    String expectedCode = "function() { return getBar(); }";
    String template = ""
        + "function before_template() {\n"
        + "  getFoo();\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getBar();\n"
        + "}\n";
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = "function() { return getFoo() == 'foo'; }";
    expectedCode = "function() { return getBar() == 'foo'; }";
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_caseStatement() throws Exception {
    String externs = ""
        + "var str = 'foo';\n"
        + "var CONSTANT = 'bar';n";
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  @Test
  public void test_forStatement() throws Exception {
    String externs = ""
        + "var obj = {};\n"
        + "obj.prop = 6;"
        + "var CONSTANT = 3;n";
    String originalCode = "for (var i = CONSTANT; i < 5; i++) {}";
    String expectedCode = "for (var i = CONSTANT2; i < 5; i++) {}";
    String template = ""
        + "function before_template() {\n"
        + "  CONSTANT\n"
        + "}\n"
        + "function after_template() {\n"
        + "  CONSTANT2\n"
        + "}\n";
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = "for (var i = 0; i < CONSTANT; i++) {}";
    expectedCode = "for (var i = 0; i < CONSTANT2; i++) {}";
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = "for (var i = 0; i < CONSTANT; i++) {}";
    expectedCode = "for (var i = 0; i < obj.prop; i++) {}";
    template = ""
        + "function before_template() {\n"
        + "  CONSTANT\n"
        + "}\n"
        + "function after_template() {\n"
        + "  obj.prop\n"
        + "}\n";
    assertChanges(externs, originalCode, expectedCode, template);

    originalCode = "for (var prop in obj) {}";
    expectedCode = "for (var prop in getObj()) {}";
    template = ""
        + "function before_template() {\n"
        + "  obj\n"
        + "}\n"
        + "function after_template() {\n"
        + "  getObj()\n"
        + "}\n";
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
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
    assertChanges(externs, originalCode, expectedCode, template);
  }

  private Compiler createCompiler() {
    return new Compiler();
  }

  private RefasterJsScanner createScanner(Compiler compiler, String template) throws Exception {
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplateFromCode(template);
    scanner.initialize(compiler);
    return scanner;
  }

  private void compileTestCode(Compiler compiler, String testCode, String externs) {
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(SourceFile.fromCode("test", testCode)),
        options);
  }

  private void assertChanges(
      String externs, String originalCode, String expectedCode, String refasterJsTemplate)
      throws Exception {
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplateFromCode(refasterJsTemplate);

    RefactoringDriver driver = new RefactoringDriver.Builder(scanner)
        .addExternsFromCode(externs)
        .addInputsFromCode(originalCode)
        .build();
    List<SuggestedFix> fixes = driver.drive();
    String newCode = ApplySuggestedFixes.applySuggestedFixesToCode(
        fixes, ImmutableMap.of("input", originalCode)).get("input");
    assertEquals(expectedCode, newCode);
  }
}
