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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
    String template = ""
        + "function before_foo() {\n"
        + "  var a = 'str';\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  'bar';\n"
        + "}\n";
    Compiler compiler = createCompiler();
    String testCode = "var loc = 'str';";
    compileTestCode(compiler, testCode, "");
    Node root = getScriptRoot(compiler);
    RefasterJsScanner scanner = createScanner(compiler, template);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    assertTrue(scanner.matches(match.getNode(), match.getMetadata()));

    List<SuggestedFix> fixes = scanner.processMatch(match);
    assertEquals(1, fixes.size());
    Set<CodeReplacement> replacements = fixes.get(0).getReplacements().get("test");
    assertEquals(1, replacements.size());
    assertEquals(
        new CodeReplacement(0, "var loc = 'str';".length(), "'bar';\n"),
        replacements.iterator().next());
  }

  @Test
  public void test_semicolonCorrect() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function Location() {};\n"
        + "/** @type {string} */\n"
        + "Location.prototype.href;\n"
        + "function foo() {}";
    String template = ""
        + "/** @param {Location} loc */"
        + "function before_foo(loc) {\n"
        + "  loc.href = 'str';\n"
        + "};\n"
        + "function after_foo() {\n"
        + "  foo();\n"
        + "}\n";
    Compiler compiler = createCompiler();
    String preamble = "var loc = new Location();";
    String testCode = "loc.href = 'str';";
    compileTestCode(compiler, preamble + testCode, externs);
    Node root = getScriptRoot(compiler);
    RefasterJsScanner scanner = createScanner(compiler, template);
    Match match = new Match(
        root.getFirstChild().getNext().getFirstChild(), new NodeMetadata(compiler));
    assertTrue(scanner.matches(match.getNode(), match.getMetadata()));

    List<SuggestedFix> fixes = scanner.processMatch(match);
    assertEquals(1, fixes.size());
    Set<CodeReplacement> replacements = fixes.get(0).getReplacements().get("test");
    assertEquals(1, replacements.size());
    assertEquals(
        new CodeReplacement(preamble.length(), testCode.length(), "foo();\n"),
        replacements.iterator().next());
  }

  @Test
  public void test_withTypes() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};";
    String template = externs
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
    Compiler compiler = createCompiler();
    String preamble = "var obj = new FooType();\n";
    String testCode = preamble + "obj.bar();";
    compileTestCode(compiler, testCode, externs);
    Node root = getScriptRoot(compiler);
    RefasterJsScanner scanner = createScanner(compiler, template);
    Match match = new Match(root.getLastChild().getFirstChild(), new NodeMetadata(compiler));
    assertTrue(scanner.matches(match.getNode(), match.getMetadata()));

    List<SuggestedFix> fixes = scanner.processMatch(match);
    assertEquals(1, fixes.size());
    Set<CodeReplacement> replacements = fixes.get(0).getReplacements().get("test");
    assertEquals(1, replacements.size());
    assertEquals(
        new CodeReplacement(preamble.length(), "obj.bar();".length(), "obj.baz();\n"),
        replacements.iterator().next());
  }


  @Test
  public void test_multiLines() throws Exception {
    String externs = ""
        + "/** @constructor */\n"
        + "function FooType() {}\n"
        + "FooType.prototype.bar = function() {};"
        + "FooType.prototype.baz = function() {};";
    String template = externs
        + "/**\n"
        + " * @param {FooType} foo\n"
        + " */\n"
        + "function before_foo(foo) {\n"
        + "  foo.bar();\n"
        + "  foo.baz();\n"
        + "};\n"
        + "function after_foo() {\n"
        + "}\n";
    Compiler compiler = createCompiler();
    String preamble = "var obj = new FooType();\n";
    String postamble = "var someOtherCode = 3;\n";
    String testCode = ""
        + preamble
        + "obj.bar();\n"
        + "obj.baz();\n"
        + postamble;
    compileTestCode(compiler, testCode, externs);
    Node root = getScriptRoot(compiler);
    RefasterJsScanner scanner = createScanner(compiler, template);
    Match match = new Match(root.getFirstChild().getNext(), new NodeMetadata(compiler));
    assertTrue(scanner.matches(match.getNode(), match.getMetadata()));

    List<SuggestedFix> fixes = scanner.processMatch(match);
    assertEquals(1, fixes.size());
    Set<CodeReplacement> replacements = fixes.get(0).getReplacements().get("test");
    assertEquals(2, replacements.size());
    Iterator<CodeReplacement> iterator = replacements.iterator();
    assertEquals(
        new CodeReplacement(preamble.length(), "obj.bar();".length(), ""),
        iterator.next());
    assertEquals(
        new CodeReplacement(preamble.length() + "obj.bar();\n".length(), "obj.baz();".length(), ""),
        iterator.next());
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

  private Node getScriptRoot(Compiler compiler) {
    Node root = compiler.getRoot();
    // The last child of the compiler root is a Block node, and the first child
    // of that is the Script node.
    return root.getLastChild().getFirstChild();
  }
}
