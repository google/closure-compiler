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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Tests for {@link TemplateAstMatcher}.
 */
public class TemplateAstMatcherTest extends TestCase {

  private Compiler lastCompiler;

  public void testMatches_primitives() {
    String template = ""
        + "function template() {\n"
        + "  3;\n"
        + "}\n";

    TestNodePair pair = compile("", template, "3");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    pair = compile("", template, "5");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var foo = 3;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());
    pair = compile("", template, "obj.foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template = ""
        + "function template() {\n"
        + "  'str';\n"
        + "}\n";
    pair = compile("", template, "'str'");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "'not_str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var foo = 'str';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());
    pair = compile("", template, "obj.foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template = ""
        + "function template() {\n"
        + "  true;\n"
        + "}\n";
    pair = compile("", template, "true");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "!true");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "false");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var foo = true;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());
    pair = compile("", template, "!undefined");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
  }

  public void testMatches_varDeclarations() {
    String template = ""
        + "function template() {\n"
        + "  var a = 3;\n"
        + "}\n";
    TestNodePair pair;

    pair = compile("", template, "var a = 3;");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    // Make sure to let variable names differ in var declarations.
    pair = compile("", template, "var b = 3;");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "var a = 5;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "5;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());

    template = ""
        + "function template() {\n"
        + "  var a = {};\n"
        + "}\n";
    pair = compile("", template, "var a = {};");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "var a = {'a': 'b'};");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());

    template = ""
        + "function template() {\n"
        + "  var a = {\n"
        + "    'a': 'b'\n"
        + "  };\n"
        + "}\n";
    pair = compile("", template, "var a = {'a': 'b'};");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "var a = {};");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
  }

  public void testMatches_templateParameterType() {
    String externs = "";
    String template = ""
        + "/**\n"
        + " * @param {string} foo\n"
        + " */\n"
        + "function template(foo) {\n"
        + "  foo;\n"
        + "}\n";

    TestNodePair pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "'different_str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "var foo = 'str';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());

    template = ""
        + "/**\n"
        + " * @param {*} foo\n"
        + " */\n"
        + "function template(foo) {\n"
        + "  foo;\n"
        + "}\n";
    pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "3");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "new Object()");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template = ""
        + "/**\n"
        + " * @param {string} foo\n"
        + " * @param {number} bar\n"
        + " */\n"
        + "function template(foo, bar) {\n"
        + "  bar + foo;\n"
        + "}\n";
    pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "3");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "new Object()");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "3 + ''");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "7 + 'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
  }

  public void testMatches_functionCall() {
    String externs = ""
        + "function foo() {};\n"
        + "function bar(arg) {};\n";
    String template = ""
        + "function template() {\n"
        + "  foo();\n"
        + "}\n";
    TestNodePair pair = compile(externs, template, "foo();");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    pair = compile(externs, template, "bar();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    pair = compile(externs, template, "bar(foo());");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getLastChild());
  }

  public void testMatches_functionCallWithArguments() {
    String externs = ""
        + "/** @return {string} */\n"
        + "function foo() {};\n"
        + "/** @param {string} arg */\n"
        + "function bar(arg) {};\n"
        + "/**\n"
        + " * @param {string} arg\n"
        + " * @param {number arg2\n"
        + " */\n"
        + "function baz(arg, arg2) {};\n";
    String template = ""
        + "function template() {\n"
        + "  bar('str');\n"
        + "}\n";
    TestNodePair pair = compile(externs, template, "foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar('str');");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar(foo());");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    assertNotMatch(
        pair.templateNode, pair.getTestExprResultRoot().getFirstChild().getLastChild());

    template = ""
        + "/** @param {string} str */\n"
        + "function template(str) {\n"
        + "  bar(str);\n"
        + "}\n";
    pair = compile(externs, template, "foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar('str');");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar('str' + 'other_str');");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar(String(3));");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template = ""
        + "/**\n"
        + " * @param {string} str\n"
        + " * @param {number} num\n"
        + " */\n"
        + "function template(str, num) {\n"
        + "  baz(str, num);\n"
        + "}\n";
    pair = compile(externs, template, "foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "baz('str', 3);");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "baz('str' + 'other_str', 3 + 4);");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
  }

  public void testMatches_methodCall() {
    String externs = ""
        + "/** @return {string} */\n"
        + "function foo() {};\n";
    String template = ""
        + "/**\n"
        + " * @param {string} str\n"
        + " */\n"
        + "function template(str) {\n"
        + "  str.toString();\n"
        + "}\n";

    TestNodePair pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "'str'.toString()");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "foo().toString()");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
  }

  public void testMatches_methodCallWithArguments() {
    String externs = ""
        + "/** @constructor */\n"
        + "function AppContext() {}\n"
        + "AppContext.prototype.init = function() {};\n"
        + "/**\n"
        + " * @param {string} arg\n"
        + " */\n"
        + "AppContext.prototype.get = function(arg) {};\n"
        + "/**\n"
        + " * @param {string} arg\n"
        + " */\n"
        + "AppContext.prototype.getOrNull = function(arg) {};";
    String template = ""
        + "/**\n"
        + " * @param {AppContext} context\n"
        + " */\n"
        + "function template(context) {\n"
        + "  context.init();\n"
        + "}\n";

    TestNodePair pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.init();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get('str');");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());

    template = ""
        + "/**\n"
        + " * @param {AppContext} context\n"
        + " * @param {string} service\n"
        + " */\n"
        + "function template(context, service) {\n"
        + "  context.get(service);\n"
        + "}\n";

    pair = compile(externs, template, "var context = new AppContext(); context.init();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get('s');");
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get(3);");
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.getOrNull('s');");
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
  }

  public void testMatches_instantiation() {
    String externs = ""
        + "/** @constructor */\n"
        + "function AppContext() {}\n";
    String template = ""
        + "function template() {\n"
        + "  new AppContext();"
        + "}\n";

    TestNodePair pair = compile(externs, template, "var foo = new AppContext()");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());
    pair = compile(externs, template, "var foo = new Object()");
    assertNotMatch(
        pair.templateNode, pair.testNode.getFirstChild().getFirstChild().getFirstChild());
  }

  public void testMatches_propertyAccess() {
    String externs = ""
        + "/** @constructor */\n"
        + "function AppContext() {}\n"
        + "/** @type {string} */\n"
        + "AppContext.prototype.location;\n";
    String template = ""
        + "/**\n"
        + " * @param {AppContext} context\n"
        + " */\n"
        + "function template(context) {\n"
        + "  context.location;"
        + "}\n";

    TestNodePair pair = compile(
        externs, template, "var context = new AppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(
        pair.templateNode, pair.testNode.getLastChild().getFirstChild().getFirstChild());
  }

  public void testMatches_multiLineTemplates() {
    String externs = ""
        + "/** @constructor */\n"
        + "function AppContext() {}\n"
        + "/** @type {string} */\n"
        + "AppContext.prototype.location;\n";
    String template = ""
        + "/**\n"
        + " * @param {AppContext} context\n"
        + " * @param {string} str\n"
        + " */\n"
        + "function template(context, str) {\n"
        + "  context.location = str;\n"
        + "  delete context.location;\n"
        + "}\n";

    TestNodePair pair = compile(
        externs, template, "var context = new AppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertNotMatch(
        pair.templateNode, pair.testNode.getLastChild().getFirstChild().getFirstChild());

    pair = compile(
        externs, template, "var ac = new AppContext(); ac.location = '3'; delete ac.location;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstChild().getNext());

    // Ensure that if a variable is declared within the template and reused
    // across multiple statements, ensure that the test code matches the same
    // pattern. For example:
    // var a = b();
    // fn(a);
    externs = ""
        + "/** @param {string} arg */\n"
        + "function bar(arg) {};\n";
    template = ""
        + "function template() {\n"
        + "  var a = 'string';\n"
        + "  bar(a);\n"
        + "}\n";

    pair = compile(
        externs, template, "var loc = 'string'; bar(loc);");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile(
        externs, template, "var loc = 'string'; bar('foo');");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile(
        externs, template, "var baz = 'qux'; var loc = 'string'; bar(baz);");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getNext());
  }

  public void testMatches_subclasses() {
    String externs = ""
        + "/** @constructor */\n"
        + "function AppContext() {}\n"
        + "/** @type {string} */\n"
        + "AppContext.prototype.location;\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {AppContext}\n"
        + " */\n"
        + "function SubAppContext() {}\n";
    String template = ""
        + "/**\n"
        + " * @param {AppContext} context\n"
        + " * @param {string} str\n"
        + " */\n"
        + "function template(context, str) {\n"
        + "  context.location = str;\n"
        + "}\n";

    TestNodePair pair = compile(
        externs, template, "var context = new SubAppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild().getFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
  }

  private void assertMatch(Node templateRoot, Node testNode, boolean shouldMatch) {
    TemplateAstMatcher matcher = new TemplateAstMatcher(lastCompiler, templateRoot.getFirstChild());
    StringBuilder sb = new StringBuilder();
    sb.append("The nodes should").append(shouldMatch ? "" : " not").append(" have matched.\n");
    sb.append("Template node:\n").append(templateRoot.toStringTree()).append("\n");
    sb.append("Test node:\n").append(testNode.getParent().toStringTree()).append("\n");
    assertEquals(sb.toString(), shouldMatch, matcher.matches(testNode));
  }

  private void assertMatch(Node templateRoot, Node testNode) {
    assertMatch(templateRoot, testNode, true);
  }

  private void assertNotMatch(Node templateRoot, Node testNode) {
    assertMatch(templateRoot, testNode, false);
  }

  /**
   * Compiles the template and test code. The code must be compiled together
   * using the same Compiler in order for the JsSourceMatcher to work properly.
   */
  private TestNodePair compile(String externs, String template, String code) {
    Compiler compiler = lastCompiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);

    Node templateNode = compiler.parse(SourceFile.fromCode("template", template));

    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(
            // The extra block allows easier separation of template and test
            // code.
            SourceFile.fromCode("test", "{" + code + "}")),
        options);
    Node root = compiler.getRoot();
    return new TestNodePair(templateNode, root.getLastChild());
  }

  private static class TestNodePair {
    final Node templateNode;
    final Node testNode;

    TestNodePair(Node template, Node root) {
      this.templateNode = template;
      this.testNode = root.getLastChild().getFirstChild();
    }

    Node getTestExprResultRoot() {
      return testNode.getFirstChild().isExprResult() ? testNode.getFirstChild() : null;
    }
  }
}
