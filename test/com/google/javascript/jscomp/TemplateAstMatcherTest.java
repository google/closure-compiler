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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TemplateAstMatcher}. */
@RunWith(JUnit4.class)
public final class TemplateAstMatcherTest {

  private Compiler lastCompiler;

  @Test
  public void testMatches_primitives() {
    String template =
        """

        function template() {
          3;
        }
        """;

    TestNodePair pair = compile("", template, "3");
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    pair = compile("", template, "5");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var foo = 3;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
    pair = compile("", template, "obj.foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template =
        """

        function template() {
          'str';
        }
        """;
    pair = compile("", template, "'str'");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "'not_str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var foo = 'str';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
    pair = compile("", template, "obj.foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template =
        """

        function template() {
          true;
        }
        """;
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
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
    pair = compile("", template, "!undefined");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
  }

  @Test
  public void testMatches_varDeclarations() {
    String template =
        """

        function template() {
          var a = 3;
        }
        """;
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

    template =
        """

        function template() {
          var a = {};
        }
        """;
    pair = compile("", template, "var a = {};");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "var a = {'a': 'b'};");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());

    template =
        """

        function template() {
          var a = {
            'a': 'b'
          };
        }
        """;
    pair = compile("", template, "var a = {'a': 'b'};");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile("", template, "var a = {};");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
  }

  @Test
  public void testMatches_templateParameterType() {
    String externs = "";
    String template =
        """
        /**
         * @param {string} foo
         */
        function template(foo) {
          foo;
        }
        """;

    TestNodePair pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "'different_str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "var foo = 'str';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());

    template =
        """
        /**
         * @param {*} foo
         */
        function template(foo) {
          foo;
        }
        """;
    pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "3");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "new Object()");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template =
        """
        /**
         * @param {string} foo
         * @param {number} bar
         */
        function template(foo, bar) {
          bar + foo;
        }
        """;
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

    template =
        """
        /**
         * @param {string} string_literal_foo
         */
        function template(string_literal_foo) {
          string_literal_foo;
        }
        """;
    pair = compile("", template, "\"foo\"");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "\"foo\" + \"bar\"");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "3");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile("", template, "var s = \"3\"; s;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getSecondChild());
  }

  @Test
  public void testMatches_againstUnresolvedType_fromTemplate_withImplicitNullability_noMatch() {
    assertMatches_againstUnresolvedType_fromTemplate_withTypeModifier("");
  }

  @Test
  public void testMatches_againstUnresolvedType_fromTemplate_withBangNullability_noMatch() {
    assertMatches_againstUnresolvedType_fromTemplate_withTypeModifier("!");
  }

  @Test
  public void testMatches_againstUnresolvedType_fromTemplate_withQmarkNullability_noMatch() {
    assertMatches_againstUnresolvedType_fromTemplate_withTypeModifier("?");
  }

  @Test
  public void testMatches_againstUnresolvedType_fromTemplate_inUnion_noMatch() {
    // TODO(b/146173738): consider making this a match. It's certain that `str` is a subtype of
    // the union `!Some.Missing.Type|string`, even though the compiler does not know what type
    // `Some.Missing.Type` is.
    assertMatches_againstUnresolvedType_fromTemplate_withTypeModifier("string|?");
  }

  private void assertMatches_againstUnresolvedType_fromTemplate_withTypeModifier(String modifier) {
    TestNodePair pair =
        compile(
            "",
            """
            /**
             * @param {MODIFIERSome.Missing.Type} foo
             */
            function template(foo) {
              foo;
            }
            """
                .replace("MODIFIER", modifier),
            "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
  }

  @Test
  public void testMatches_functionCall() {
    String externs =
        """

        function foo() {};
        function bar(arg) {};
        """;
    String template =
        """

        function template() {
          foo();
        }
        """;
    TestNodePair pair = compile(externs, template, "foo();");
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    pair = compile(externs, template, "bar();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    pair = compile(externs, template, "bar(foo());");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getLastChild());
  }

  @Test
  public void testMatches_functionCallWithArguments() {
    String externs =
        """
        /** @return {string} */
        function foo() {};
        /** @param {string} arg */
        function bar(arg) {};
        /**
         * @param {string} arg
         * @param {number arg2
         */
        function baz(arg, arg2) {};
        """;
    String template =
        """

        function template() {
          bar('str');
        }
        """;
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
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild().getLastChild());

    template =
        """
        /** @param {string} str */
        function template(str) {
          bar(str);
        }
        """;
    pair = compile(externs, template, "foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar('str');");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar('str' + 'other_str');");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "bar(String(3));");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());

    template =
        """
        /**
         * @param {string} str
         * @param {number} num
         */
        function template(str, num) {
          baz(str, num);
        }
        """;
    pair = compile(externs, template, "foo();");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "baz('str', 3);");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "baz('str' + 'other_str', 3 + 4);");
    assertMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
  }

  @Test
  public void testMatches_methodCall() {
    String externs =
        """

        /** @return {string} */
        function foo() {};
        """;
    String template =
        """
        /**
         * @param {string} str
         */
        function template(str) {
          str.toString();
        }
        """;

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

  @Test
  public void testMatches_methodCallWithArguments() {
    String externs =
        """
        /** @constructor */
        function AppContext() {}
        AppContext.prototype.init = function() {};
        /**
         * @param {string} arg
         */
        AppContext.prototype.get = function(arg) {};
        /**
         * @param {string} arg
         */
        AppContext.prototype.getOrNull = function(arg) {};
        """;
    String template =
        """
        /**
         * @param {AppContext} context
         */
        function template(context) {
          context.init();
        }
        """;

    TestNodePair pair = compile(externs, template, "'str'");
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot());
    assertNotMatch(pair.templateNode, pair.getTestExprResultRoot().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.init();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get('str');");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());

    template =
        """
        /**
         * @param {AppContext} context
         * @param {string} service
         */
        function template(context, service) {
          context.get(service);
        }
        """;

    pair = compile(externs, template, "var context = new AppContext(); context.init();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get('s');");
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.get(3);");
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    pair = compile(externs, template, "var context = new AppContext(); context.getOrNull('s');");
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
  }

  @Test
  public void testMatches_instantiation() {
    String externs =
        """

        /** @constructor */
        function AppContext() {}
        """;
    String template =
        """

        function template() {
          new AppContext();
        }
        """;

    TestNodePair pair = compile(externs, template, "var foo = new AppContext()");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
    pair = compile(externs, template, "var foo = new Object()");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild().getFirstChild());
  }

  @Test
  public void testMatches_propertyAccess() {
    String externs =
        """
        /** @constructor */
        function AppContext() {}
        /** @type {string} */
        AppContext.prototype.location;
        """;
    String template =
        """
        /**
         * @param {AppContext} context
         */
        function template(context) {
          context.location;
        }
        """;

    TestNodePair pair =
        compile(externs, template, "var context = new AppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstFirstChild());
  }

  @Test
  public void testMatches_multiLineTemplates() {
    String externs =
        """
        /** @constructor */
        function AppContext() {}
        /** @type {string} */
        AppContext.prototype.location;
        """;
    String template =
        """
        /**
         * @param {AppContext} context
         * @param {string} str
         */
        function template(context, str) {
          context.location = str;
          delete context.location;
        }
        """;

    TestNodePair pair =
        compile(externs, template, "var context = new AppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getLastChild().getFirstFirstChild());

    pair =
        compile(
            externs, template, "var ac = new AppContext(); ac.location = '3'; delete ac.location;");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getSecondChild());

    // Ensure that if a variable is declared within the template and reused
    // across multiple statements, ensure that the test code matches the same
    // pattern. For example:
    // var a = b();
    // fn(a);
    externs =
        """

        /** @param {string} arg */
        function bar(arg) {};
        """;
    template =
        """

        function template() {
          var a = 'string';
          bar(a);
        }
        """;

    pair = compile(externs, template, "var loc = 'string'; bar(loc);");
    assertMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile(externs, template, "var loc = 'string'; bar('foo');");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    pair = compile(externs, template, "var baz = 'qux'; var loc = 'string'; bar(baz);");
    assertNotMatch(pair.templateNode, pair.testNode.getSecondChild());
  }

  @Test
  public void testMatches_subclasses() {
    String externs =
        """
        /** @constructor */
        function AppContext() {}
        /** @type {string} */
        AppContext.prototype.location;
        /**
         * @constructor
         * @extends {AppContext}
         */
        function SubAppContext() {}
        """;
    String template =
        """
        /**
         * @param {AppContext} context
         * @param {string} str
         */
        function template(context, str) {
          context.location = str;
        }
        """;

    TestNodePair pair =
        compile(externs, template, "var context = new SubAppContext(); context.location = '3';");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
  }

  @Test
  public void testMatches_nonDefaultStrategy() {
    String externs =
        """
        /** @constructor */
        function AppContext() {}
        /** @type {string} */
        AppContext.prototype.location;
        /**
         * @constructor
         * @extends {AppContext}
         */
        function SubAppContext() {}
        var context = new AppContext();
        var subContext = new SubAppContext();
        """;
    String template =
        """
        /**
         * @param {!AppContext} context
         * @param {string} str
         */
        function template(context, str) {
          context.location = str;
        }
        """;

    TestNodePair pair = compile(externs, template, "subContext.location = '3';");
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
    assertMatch(
        pair.templateNode,
        pair.testNode.getLastChild().getFirstChild(),
        false,
        TypeMatchingStrategy.EXACT);
  }

  @Test
  public void testMatches_namespace_method() {
    String externs =
        """
        /** @const */ const ns = {};
        /** @const */ ns.sub = {};
        /** @return {boolean} */ ns.sub.method = function() {};
        """;

    String template =
        """
        /**
         * @param {typeof ns.sub} target
         */
        function template(target) {
          target.method();
        }
        """;

    TestNodePair pair = compile(externs, template, "var alias = ns.sub; alias.method();");
    assertNotMatch(pair.templateNode, pair.testNode.getFirstChild());
    assertNotMatch(pair.templateNode, pair.testNode.getFirstFirstChild());
    assertMatch(pair.templateNode, pair.testNode.getLastChild().getFirstChild());
  }

  private void assertMatch(Node templateRoot, Node testNode, boolean shouldMatch) {
    assertMatch(templateRoot, testNode, shouldMatch, TypeMatchingStrategy.LOOSE);
  }

  private void assertMatch(
      Node templateRoot,
      Node testNode,
      boolean shouldMatch,
      TypeMatchingStrategy typeMatchingStrategy) {
    TemplateAstMatcher matcher =
        new TemplateAstMatcher(lastCompiler, templateRoot.getFirstChild(), typeMatchingStrategy);
    StringBuilder sb = new StringBuilder();
    sb.append("The nodes should").append(shouldMatch ? "" : " not").append(" have matched.\n");
    sb.append("Template node:\n").append(templateRoot.toStringTree()).append("\n");
    sb.append("Test node:\n").append(testNode.getParent().toStringTree()).append("\n");
    assertWithMessage(sb.toString()).that(matcher.matches(testNode)).isEqualTo(shouldMatch);
  }

  private void assertMatch(Node templateRoot, Node testNode) {
    assertMatch(templateRoot, testNode, true);
  }

  private void assertNotMatch(Node templateRoot, Node testNode) {
    assertMatch(templateRoot, testNode, false);
  }

  /**
   * Compiles the template and test code. The code must be compiled together using the same Compiler
   * in order for the JsSourceMatcher to work properly.
   */
  private TestNodePair compile(String externs, String template, String code) {
    Compiler compiler = lastCompiler = new Compiler();
    compiler.disableThreads();
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setChecksOnly(true);
    options.setPreserveDetailedSourceInfo(true);

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
