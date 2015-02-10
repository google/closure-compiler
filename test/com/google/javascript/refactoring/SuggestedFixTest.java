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

import static com.google.javascript.refactoring.testing.SuggestedFixes.assertChanges;
import static com.google.javascript.refactoring.testing.SuggestedFixes.assertReplacement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for JsFlume {@link SuggestedFix}.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
@RunWith(JUnit4.class)
public class SuggestedFixTest {

  @Test
  public void testInsertBefore() {
    String before = "var someRandomCode = {};";
    String after = "/** some comment */\ngoog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog2"), IR.string("get")),
        IR.string("service")));
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertBefore(root.getLastChild().getFirstChild(), newNode, compiler)
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), 0, "goog2.get('service');\n");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete() {
    String input = "var foo = new Bar();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .delete(root.getFirstChild())
        .build();
    CodeReplacement replacement = new CodeReplacement(0, input.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_spaceBeforeNode() {
    String before = "var foo = new Bar();";
    String after = "\n\nvar baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .delete(root.getLastChild())
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_dontDeleteSpaceBeforeNode() {
    String before = "var foo = new Bar();\n\n";
    String after = "var baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .deleteWithoutRemovingSurroundWhitespace(root.getLastChild())
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleVarDeclaration() {
    String input = "var foo = 3, bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder()
        .delete(root.getFirstChild().getFirstChild())
        .build();
    CodeReplacement replacement = new CodeReplacement(4, "foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder()
        .delete(root.getFirstChild().getFirstChild().getNext())
        .build();
    replacement = new CodeReplacement(13, "bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder()
        .delete(root.getFirstChild().getLastChild())
        .build();
    replacement = new CodeReplacement(16, ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameStringKey() {
    String input = "var obj = {foo: 'bar'};";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node node = root.getFirstChild().getFirstChild().getFirstChild()
        .getFirstChild();
    SuggestedFix fix = new SuggestedFix.Builder()
        .rename(node, "fooBar")
        .build();
    CodeReplacement replacement = new CodeReplacement(11, "foo".length(), "fooBar");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameProperty_justPropertyName() {
    String input = "obj.test.property";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .rename(root.getFirstChild().getFirstChild(), "renamedProperty")
        .build();
    CodeReplacement replacement = new CodeReplacement(9, "property".length(), "renamedProperty");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameProperty_entireName() {
    String input = "obj.test.property";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .rename(root.getFirstChild().getFirstChild(), "renamedProperty", true)
        .build();
    CodeReplacement replacement = new CodeReplacement(0, input.length(), "renamedProperty");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameFunction_justFunctionName() {
    String input = "obj.fnCall();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .rename(root.getFirstChild().getFirstChild(), "renamedFnCall")
        .build();
    CodeReplacement replacement = new CodeReplacement(4, "fnCall".length(), "renamedFnCall");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameFunction_entireName() {
    String fnName = "goog.dom.classes.add";
    String newFnName = "goog.dom.classlist.add";
    String input = fnName + "(foo, bar);";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .rename(root.getFirstChild().getFirstChild(), newFnName, true)
        .build();
    CodeReplacement replacement = new CodeReplacement(0, fnName.length(), newFnName);
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace() {
    String before = "var someRandomCode = {};\n/** some comment */\n";
    String after = "goog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog2"), IR.string("get")),
        IR.string("service")));
    SuggestedFix fix = new SuggestedFix.Builder()
        .replace(root.getLastChild().getFirstChild(), newNode, compiler)
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), after.length(), "goog2.get('service');");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_functionArgument() {
    String before = ""
        + "var MyClass = function() {};\n"
        + "MyClass.prototype.foo = function() {};\n"
        + "MyClass.prototype.bar = function() {};\n"
        + "var clazz = new MyClass();\n";
    String after = "alert(clazz.foo());";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.call(IR.getprop(IR.name("clazz"), IR.string("bar")));
    SuggestedFix fix = new SuggestedFix.Builder()
        .replace(root.getLastChild().getFirstChild().getLastChild(), newNode, compiler)
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length() + "alert(".length(), "clazz.foo()".length(), "clazz.bar()");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_leftHandSideAssignment() {
    String before = "var MyClass = function() {};\n";
    String after = "MyClass.prototype.foo = function() {};\n";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.getprop(
        IR.getprop(IR.name("MyClass"), IR.string("prototype")),
        IR.string("bar"));
    SuggestedFix fix = new SuggestedFix.Builder()
        .replace(root.getLastChild().getFirstChild().getFirstChild(), newNode, compiler)
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), "MyClass.prototype.foo".length(), "MyClass.prototype.bar");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testAddCast() {
    String input = "obj.fnCall();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node objNode = root.getFirstChild().getFirstChild().getFirstChild().getFirstChild();
    SuggestedFix fix = new SuggestedFix.Builder()
        .addCast(objNode, compiler, "FooBar")
        .build();
    CodeReplacement replacement = new CodeReplacement(
        0, "obj".length(), "/** @type {FooBar} */ (obj)");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveCast() {
    String input = "var x = /** @type {string} */ (y);";
    String expectedCode = "var x = y;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node castNode = root.getFirstChild().getFirstChild().getFirstChild();
    assertTrue(castNode.isCast());

    SuggestedFix fix = new SuggestedFix.Builder()
        .removeCast(castNode, compiler)
        .build();
    assertChanges(fix, "", input, expectedCode);
  }

  @Test
  public void testRemoveCast_complexStatement() {
    String input = ""
        + "var x = /** @type {string} */ (function() {\n"
        + "  // Inline comment that should be preserved.\n"
        + "  var blah = bleh;\n"
        + "});";
    String expectedCode = ""
        + "var x = function() {\n"
        + "  // Inline comment that should be preserved.\n"
        + "  var blah = bleh;\n"
        + "};";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node castNode = root.getFirstChild().getFirstChild().getFirstChild();
    assertTrue(castNode.isCast());

    SuggestedFix fix = new SuggestedFix.Builder()
        .removeCast(castNode, compiler)
        .build();
    assertChanges(fix, "", input, expectedCode);
  }

  @Test
  public void testChangeJsDocType() {
    String before = "/** ";
    String after = "@type {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .changeJsDocType(root.getFirstChild(), compiler, "Object")
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), "@type {Foo}".length(), "@type {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType2() {
    String code = "/** @type {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    Node varNode = root.getFirstChild();
    Node jsdocRoot =
        Iterables.getOnlyElement(varNode.getJSDocInfo().getTypeNodes());
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertBefore(jsdocRoot, "!")
        .build();
    CodeReplacement replacement = new CodeReplacement(
        "/** @type {".length(), 0, "!");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_privateType() {
    String before = "/** ";
    String after = "@private Foo */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .changeJsDocType(root.getFirstChild(), compiler, "Object")
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), "@private Foo".length(), "@private {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments() {
    String before = "goog.dom.classes.add(";
    String after = "foo, bar);";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertArguments(root.getFirstChild().getFirstChild(), 0, "baz")
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), 0, "baz, ");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_emptyArguments() {
    String before = "goog.dom.classes.add(";
    String after = ");";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertArguments(root.getFirstChild().getFirstChild(), 0, "baz")
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), 0, "baz");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_notFirstArgument() {
    String before = "goog.dom.classes.add(foo, ";
    String after = "bar);";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertArguments(root.getFirstChild().getFirstChild(), 1, "baz")
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), 0, "baz, ");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_lastArgument() {
    String before = "goog.dom.classes.add(foo, bar";
    String after = ");";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertArguments(root.getFirstChild().getFirstChild(), 2, "baz")
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), 0, ", baz");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testAddGoogRequire() {
    String before = "goog.provide('js.Foo');\n";
    String after =
        "goog.require('js.Bar');\n"
        + "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_afterAllOtherGoogRequires() {
    String before = "goog.provide('js.Foo');\n"
        + "goog.require('js.Bar');\n";
    String after =
        "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    assertAddGoogRequire(before, after, "zyx.w");
  }

  @Test
  public void testAddGoogRequire_noGoogRequire() {
    String before = "goog.provide('js.Foo');\n";
    String after =
        "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_noGoogRequireOrGoogProvide() {
    String before = "";
    String after =
        "/** @private */\n"
        + "function foo_() {};\n";
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_alreadyExists() {
    String input =
        "goog.provide('js.Foo');\n"
        + "goog.require('abc.def');\n"
        + "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder()
        .addGoogRequire(match, "abc.def")
        .build();
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertEquals(0, replacementMap.size());
  }

  private void assertAddGoogRequire(String before, String after, String namespace) {
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder()
        .addGoogRequire(match, namespace)
        .build();
    CodeReplacement replacement = new CodeReplacement(
        before.length(), 0, "goog.require('" + namespace + "');\n");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveGoogRequire() {
    String before = "/** @fileoverview blah */\n\n"
        + "goog.provide('js.Foo');\n\n";
    String googRequire = "goog.require('abc.def');";
    String input =
        before
        + googRequire
        + "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder()
        .removeGoogRequire(match, "abc.def")
        .build();
    CodeReplacement replacement = new CodeReplacement(before.length(), googRequire.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveGoogRequire_doesNotExist() {
    String input =
        "goog.require('abc.def');\n"
        + "\n"
        + "/** @private */\n"
        + "function foo_() {};\n";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder()
        .removeGoogRequire(match, "fakefake")
        .build();
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertEquals(0, replacementMap.size());
  }

  /**
   * Returns the root script node produced from the compiled JS input.
   */
  private Node compileToScriptRoot(Compiler compiler) {
    Node root = compiler.getRoot();
    // The last child of the compiler root is a Block node, and the first child
    // of that is the Script node.
    return root.getLastChild().getFirstChild();
  }

  private Compiler getCompiler(String jsInput) {
    Compiler compiler = new Compiler();
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", jsInput)),
        options);
    compiler.parse();
    return compiler;
  }
}
