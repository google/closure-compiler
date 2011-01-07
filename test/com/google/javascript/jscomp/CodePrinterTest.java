/*
 * Copyright 2004 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

public class CodePrinterTest extends TestCase {
  static Node parse(String js) {
    return parse(js, false);
  }

  static Node parse(String js, boolean checkTypes) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.languageIn = LanguageMode.ECMASCRIPT5; // Allow getters and setters.
    compiler.initOptions(options);
    Node n = compiler.parseTestCode(js);

    if (checkTypes) {
      DefaultPassConfig passConfig = new DefaultPassConfig(null);
      CompilerPass typeResolver = passConfig.resolveTypes.create(compiler);
      Node externs = new Node(Token.SCRIPT);
      Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
      externAndJsRoot.setIsSyntheticBlock(true);
      typeResolver.process(externs, n);
      CompilerPass inferTypes = passConfig.inferTypes.create(compiler);
      inferTypes.process(externs, n);
    }

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return n;
  }

  private static void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().length + compiler.getWarnings().length;
    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err.toString() + "\n";
      }
      for (JSError err : compiler.getWarnings()) {
        msg += "Warning:" + err.toString() + "\n";
      }
      assertEquals("Unexpected warnings or errors.\n " + msg, expected, actual);
    }
  }

  String parsePrint(String js, boolean prettyprint, int lineThreshold) {
    return new CodePrinter.Builder(parse(js)).setPrettyPrint(prettyprint)
        .setLineLengthThreshold(lineThreshold).build();
  }

  String parsePrint(String js, boolean prettyprint, boolean lineBreak,
      int lineThreshold) {
    return new CodePrinter.Builder(parse(js)).setPrettyPrint(prettyprint)
        .setLineLengthThreshold(lineThreshold).setLineBreak(lineBreak).build();
  }

  String parsePrint(String js, boolean prettyprint, boolean lineBreak,
      int lineThreshold, boolean outputTypes) {
    return new CodePrinter.Builder(parse(js, true)).setPrettyPrint(prettyprint)
        .setOutputTypes(outputTypes)
        .setLineLengthThreshold(lineThreshold).setLineBreak(lineBreak).build();
  }

  String printNode(Node n) {
    return new CodePrinter.Builder(n).setLineLengthThreshold(
        CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD).build();
  }

  void assertPrintNode(String expectedJs, Node ast) {
    assertEquals(expectedJs, printNode(ast));
  }

  public void testPrint() {
    assertPrint("10 + a + b", "10+a+b");
    assertPrint("10 + (30*50)", "10+30*50");
    assertPrint("with(x) { x + 3; }", "with(x)x+3");
    assertPrint("\"aa'a\"", "\"aa'a\"");
    assertPrint("\"aa\\\"a\"", "'aa\"a'");
    assertPrint("function foo()\n{return 10;}", "function foo(){return 10}");
    assertPrint("a instanceof b", "a instanceof b");
    assertPrint("typeof(a)", "typeof a");
    assertPrint(
        "var foo = x ? { a : 1 } : {a: 3, b:4, \"default\": 5, \"foo-bar\": 6}",
        "var foo=x?{a:1}:{a:3,b:4,\"default\":5,\"foo-bar\":6}");

    // Safari: needs ';' at the end of a throw statement
    assertPrint("function foo(){throw 'error';}",
        "function foo(){throw\"error\";}");
    // Safari 3 needs a "{" around a single function
    assertPrint("if (true) function foo(){return}",
        "if(true){function foo(){return}}");

    assertPrint("var x = 10; { var y = 20; }", "var x=10;var y=20");

    assertPrint("while (x-- > 0);", "while(x-- >0);");
    assertPrint("x-- >> 1", "x-- >>1");

    assertPrint("(function () {})(); ",
        "(function(){})()");

    // Associativity
    assertPrint("var a,b,c,d;a || (b&& c) && (a || d)",
        "var a,b,c,d;a||b&&c&&(a||d)");
    assertPrint("var a,b,c; a || (b || c); a * (b * c); a | (b | c)",
        "var a,b,c;a||b||c;a*b*c;a|b|c");
    assertPrint("var a,b,c; a / b / c;a / (b / c); a - (b - c);",
        "var a,b,c;a/b/c;a/(b/c);a-(b-c)");
    assertPrint("var a,b; a = b = 3;",
        "var a,b;a=b=3");
    assertPrint("var a,b,c,d; a = (b = c = (d = 3));",
        "var a,b,c,d;a=b=c=d=3");
    assertPrint("var a,b,c; a += (b = c += 3);",
        "var a,b,c;a+=b=c+=3");
    assertPrint("var a,b,c; a *= (b -= c);",
        "var a,b,c;a*=b-=c");

    // Break scripts
    assertPrint("'<script>'", "\"<script>\"");
    assertPrint("'</script>'", "\"<\\/script>\"");
    assertPrint("\"</script> </SCRIPT>\"", "\"<\\/script> <\\/SCRIPT>\"");

    assertPrint("'-->'", "\"--\\>\"");
    assertPrint("']]>'", "\"]]\\>\"");
    assertPrint("' --></script>'", "\" --\\><\\/script>\"");

    assertPrint("/--> <\\/script>/g", "/--\\> <\\/script>/g");

    // Precedence
    assertPrint("a ? delete b[0] : 3", "a?delete b[0]:3");
    assertPrint("(delete a[0])/10", "delete a[0]/10");

    // optional '()' for new

    // simple new
    assertPrint("new A", "new A");
    assertPrint("new A()", "new A");
    assertPrint("new A('x')", "new A(\"x\")");

    // calling instance method directly after new
    assertPrint("new A().a()", "(new A).a()");
    assertPrint("(new A).a()", "(new A).a()");

    // this case should be fixed
    assertPrint("new A('y').a()", "(new A(\"y\")).a()");

    // internal class
    assertPrint("new A.B", "new A.B");
    assertPrint("new A.B()", "new A.B");
    assertPrint("new A.B('z')", "new A.B(\"z\")");

    // calling instance method directly after new internal class
    assertPrint("(new A.B).a()", "(new A.B).a()");
    assertPrint("new A.B().a()", "(new A.B).a()");
    // this case should be fixed
    assertPrint("new A.B('w').a()", "(new A.B(\"w\")).a()");

    // Operators: make sure we don't convert binary + and unary + into ++
    assertPrint("x + +y", "x+ +y");
    assertPrint("x - (-y)", "x- -y");
    assertPrint("x++ +y", "x++ +y");
    assertPrint("x-- -y", "x-- -y");
    assertPrint("x++ -y", "x++-y");

    // Label
    assertPrint("foo:for(;;){break foo;}", "foo:for(;;)break foo");
    assertPrint("foo:while(1){continue foo;}", "foo:while(1)continue foo");

    // Object literals.
    assertPrint("({})", "({})");
    assertPrint("var x = {};", "var x={}");
    assertPrint("({}).x", "({}).x");
    assertPrint("({})['x']", "({})[\"x\"]");
    assertPrint("({}) instanceof Object", "({})instanceof Object");
    assertPrint("({}) || 1", "({})||1");
    assertPrint("1 || ({})", "1||{}");
    assertPrint("({}) ? 1 : 2", "({})?1:2");
    assertPrint("0 ? ({}) : 2", "0?{}:2");
    assertPrint("0 ? 1 : ({})", "0?1:{}");
    assertPrint("typeof ({})", "typeof{}");
    assertPrint("f({})", "f({})");

    // Anonymous function expressions.
    assertPrint("(function(){})", "(function(){})");
    assertPrint("(function(){})()", "(function(){})()");
    assertPrint("(function(){})instanceof Object",
        "(function(){})instanceof Object");
    assertPrint("(function(){}).bind().call()",
        "(function(){}).bind().call()");
    assertPrint("var x = function() { };", "var x=function(){}");
    assertPrint("var x = function() { }();", "var x=function(){}()");
    assertPrint("(function() {}), 2", "(function(){}),2");

    // Name functions expression.
    assertPrint("(function f(){})", "(function f(){})");

    // Function declaration.
    assertPrint("function f(){}", "function f(){}");

    // Make sure we don't treat non-latin character escapes as raw strings.
    assertPrint("({ 'a': 4, '\\u0100': 4 })", "({a:4,\"\\u0100\":4})");

    // Test if statement and for statements with single statements in body.
    assertPrint("if (true) { alert();}", "if(true)alert()");
    assertPrint("if (false) {} else {alert(\"a\");}",
        "if(false);else alert(\"a\")");
    assertPrint("for(;;) { alert();};", "for(;;)alert()");

    assertPrint("do { alert(); } while(true);",
        "do alert();while(true)");
    assertPrint("myLabel: { alert();}",
        "myLabel:alert()");
    assertPrint("myLabel: for(;;) continue myLabel;",
        "myLabel:for(;;)continue myLabel");

    // Test nested var statement
    assertPrint("if (true) var x; x = 4;", "if(true)var x;x=4");

    // Non-latin identifier. Make sure we keep them escaped.
    assertPrint("\\u00fb", "\\u00fb");
    assertPrint("\\u00fa=1", "\\u00fa=1");
    assertPrint("function \\u00f9(){}", "function \\u00f9(){}");
    assertPrint("x.\\u00f8", "x.\\u00f8");
    assertPrint("x.\\u00f8", "x.\\u00f8");
    assertPrint("abc\\u4e00\\u4e01jkl", "abc\\u4e00\\u4e01jkl");

    // Test the right-associative unary operators for spurious parens
    assertPrint("! ! true", "!!true");
    assertPrint("!(!(true))", "!!true");
    assertPrint("typeof(void(0))", "typeof void 0");
    assertPrint("typeof(void(!0))", "typeof void!0");
    assertPrint("+ - + + - + 3", "+-+ +-+3"); // chained unary plus/minus
    assertPrint("+(--x)", "+--x");
    assertPrint("-(++x)", "-++x");

    // needs a space to prevent an ambiguous parse
    assertPrint("-(--x)", "- --x");
    assertPrint("!(~~5)", "!~~5");
    assertPrint("~(a/b)", "~(a/b)");

    // Preserve parens to overcome greedy binding of NEW
    assertPrint("new (foo.bar()).factory(baz)", "new (foo.bar().factory)(baz)");
    assertPrint("new (bar()).factory(baz)", "new (bar().factory)(baz)");
    assertPrint("new (new foobar(x)).factory(baz)",
        "new (new foobar(x)).factory(baz)");

    // Make sure that HOOK is right associative
    assertPrint("a ? b : (c ? d : e)", "a?b:c?d:e");
    assertPrint("a ? (b ? c : d) : e", "a?b?c:d:e");
    assertPrint("(a ? b : c) ? d : e", "(a?b:c)?d:e");

    // Test nested ifs
    assertPrint("if (x) if (y); else;", "if(x)if(y);else;");

    // Test comma.
    assertPrint("a,b,c", "a,b,c");
    assertPrint("(a,b),c", "a,b,c");
    assertPrint("a,(b,c)", "a,b,c");
    assertPrint("x=a,b,c", "x=a,b,c");
    assertPrint("x=(a,b),c", "x=(a,b),c");
    assertPrint("x=a,(b,c)", "x=a,b,c");
    assertPrint("x=a,y=b,z=c", "x=a,y=b,z=c");
    assertPrint("x=(a,y=b,z=c)", "x=(a,y=b,z=c)");
    assertPrint("x=[a,b,c,d]", "x=[a,b,c,d]");
    assertPrint("x=[(a,b,c),d]", "x=[(a,b,c),d]");
    assertPrint("x=[(a,(b,c)),d]", "x=[(a,b,c),d]");
    assertPrint("x=[a,(b,c,d)]", "x=[a,(b,c,d)]");
    assertPrint("var x=(a,b)", "var x=(a,b)");
    assertPrint("var x=a,b,c", "var x=a,b,c");
    assertPrint("var x=(a,b),c", "var x=(a,b),c");
    assertPrint("var x=a,b=(c,d)", "var x=a,b=(c,d)");
    assertPrint("foo(a,b,c,d)", "foo(a,b,c,d)");
    assertPrint("foo((a,b,c),d)", "foo((a,b,c),d)");
    assertPrint("foo((a,(b,c)),d)", "foo((a,b,c),d)");
    assertPrint("f(a+b,(c,d,(e,f,g)))", "f(a+b,(c,d,e,f,g))");
    assertPrint("({}) , 1 , 2", "({}),1,2");
    assertPrint("({}) , {} , {}", "({}),{},{}");

    // EMPTY nodes
    assertPrint("if (x){}", "if(x);");
    assertPrint("if(x);", "if(x);");
    assertPrint("if(x)if(y);", "if(x)if(y);");
    assertPrint("if(x){if(y);}", "if(x)if(y);");
    assertPrint("if(x){if(y){};;;}", "if(x)if(y);");
    assertPrint("if(x){;;function y(){};;}", "if(x){function y(){}}");
  }

  public void testHook() {
    assertPrint("a ? b = 1 : c = 2", "a?b=1:c=2");
    assertPrint("x = a ? b = 1 : c = 2", "x=a?b=1:c=2");
    assertPrint("(x = a) ? b = 1 : c = 2", "(x=a)?b=1:c=2");

    assertPrint("x, a ? b = 1 : c = 2", "x,a?b=1:c=2");
    assertPrint("x, (a ? b = 1 : c = 2)", "x,a?b=1:c=2");
    assertPrint("(x, a) ? b = 1 : c = 2", "(x,a)?b=1:c=2");

    assertPrint("a ? (x, b) : c = 2", "a?(x,b):c=2");
    assertPrint("a ? b = 1 : (x,c)", "a?b=1:(x,c)");

    assertPrint("a ? b = 1 : c = 2 + x", "a?b=1:c=2+x");
    assertPrint("(a ? b = 1 : c = 2) + x", "(a?b=1:c=2)+x");
    assertPrint("a ? b = 1 : (c = 2) + x", "a?b=1:(c=2)+x");

    assertPrint("a ? (b?1:2) : 3", "a?b?1:2:3");
  }

  public void testPrintInOperatorInForLoop() {
    // Check for in expression in for's init expression.
    // Check alone, with + (higher precedence), with ?: (lower precedence),
    // and with conditional.
    assertPrint("var a={}; for (var i = (\"length\" in a); i;) {}",
        "var a={};for(var i=(\"length\"in a);i;);");
    assertPrint("var a={}; for (var i = (\"length\" in a) ? 0 : 1; i;) {}",
        "var a={};for(var i=(\"length\"in a)?0:1;i;);");
    assertPrint("var a={}; for (var i = (\"length\" in a) + 1; i;) {}",
        "var a={};for(var i=(\"length\"in a)+1;i;);");
    assertPrint("var a={};for (var i = (\"length\" in a|| \"size\" in a);;);",
        "var a={};for(var i=(\"length\"in a)||(\"size\"in a);;);");
    assertPrint("var a={};for (var i = a || a || (\"size\" in a);;);",
        "var a={};for(var i=a||a||(\"size\"in a);;);");

    // Test works with unary operators and calls.
    assertPrint("var a={}; for (var i = -(\"length\" in a); i;) {}",
        "var a={};for(var i=-(\"length\"in a);i;);");
    assertPrint("var a={};function b_(p){ return p;};" +
        "for(var i=1,j=b_(\"length\" in a);;) {}",
        "var a={};function b_(p){return p}" +
            "for(var i=1,j=b_(\"length\"in a);;);");

    // Test we correctly handle an in operator in the test clause.
    assertPrint("var a={}; for (;(\"length\" in a);) {}",
        "var a={};for(;\"length\"in a;);");
  }

  public void testLiteralProperty() {
    assertPrint("(64).toString()", "(64).toString()");
  }

  private void assertPrint(String js, String expected) {
    parse(expected); // validate the expected string is valid js
    assertEquals(expected,
        parsePrint(js, false, CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }

  // Make sure that the code generator doesn't associate an
  // else clause with the wrong if clause.
  public void testAmbiguousElseClauses() {
    assertPrintNode("if(x)if(y);else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK),

                    // ELSE clause for the inner if
                    new Node(Token.BLOCK)))));

    assertPrintNode("if(x){if(y);}else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK))),

            // ELSE clause for the outer if
            new Node(Token.BLOCK)));

    assertPrintNode("if(x)if(y);else{if(z);}else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK),
                    new Node(Token.BLOCK,
                        new Node(Token.IF,
                            Node.newString(Token.NAME, "z"),
                            new Node(Token.BLOCK))))),

            // ELSE clause for the outermost if
            new Node(Token.BLOCK)));
  }

  public void testLineBreak() {
    // line break after function if in a statement context
    assertLineBreak("function a() {}\n" +
        "function b() {}",
        "function a(){}\n" +
        "function b(){}\n");

    // line break after ; after a function
    assertLineBreak("var a = {};\n" +
        "a.foo = function () {}\n" +
        "function b() {}",
        "var a={};a.foo=function(){};\n" +
        "function b(){}\n");

    // break after comma after a function
    assertLineBreak("var a = {\n" +
        "  b: function() {},\n" +
        "  c: function() {}\n" +
        "};\n" +
        "alert(a);",

        "var a={b:function(){},\n" +
        "c:function(){}};\n" +
        "alert(a)");
  }

  private void assertLineBreak(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, false, true,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }

  public void testPrettyPrinter() {
    // Ensure that the pretty printer inserts line breaks at appropriate
    // places.
    assertPrettyPrint("(function(){})();","(function() {\n})();\n");
    assertPrettyPrint("var a = (function() {});alert(a);",
        "var a = function() {\n};\nalert(a);\n");

    // Check we correctly handle putting brackets around all if clauses so
    // we can put breakpoints inside statements.
    assertPrettyPrint("if (1) {}",
        "if(1) {\n" +
        "}\n");
    assertPrettyPrint("if (1) {alert(\"\");}",
        "if(1) {\n" +
        "  alert(\"\")\n" +
        "}\n");
    assertPrettyPrint("if (1)alert(\"\");",
        "if(1) {\n" +
        "  alert(\"\")\n" +
        "}\n");
    assertPrettyPrint("if (1) {alert();alert();}",
        "if(1) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}\n");

    // Don't add blocks if they weren't there already.
    assertPrettyPrint("label: alert();",
        "label:alert();\n");

    // But if statements and loops get blocks automagically.
    assertPrettyPrint("if (1) alert();",
        "if(1) {\n" +
        "  alert()\n" +
        "}\n");
    assertPrettyPrint("for (;;) alert();",
        "for(;;) {\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint("while (1) alert();",
        "while(1) {\n" +
        "  alert()\n" +
        "}\n");

    // Do we put else clauses in blocks?
    assertPrettyPrint("if (1) {} else {alert(a);}",
        "if(1) {\n" +
        "}else {\n  alert(a)\n}\n");

    // Do we add blocks to else clauses?
    assertPrettyPrint("if (1) alert(a); else alert(b);",
        "if(1) {\n" +
        "  alert(a)\n" +
        "}else {\n" +
        "  alert(b)\n" +
        "}\n");

    // Do we put for bodies in blocks?
    assertPrettyPrint("for(;;) { alert();}",
        "for(;;) {\n" +
         "  alert()\n" +
         "}\n");
    assertPrettyPrint("for(;;) {}",
        "for(;;) {\n" +
        "}\n");
    assertPrettyPrint("for(;;) { alert(); alert(); }",
        "for(;;) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}\n");

    // How about do loops?
    assertPrettyPrint("do { alert(); } while(true);",
        "do {\n" +
        "  alert()\n" +
        "}while(true);\n");

    // label?
    assertPrettyPrint("myLabel: { alert();}",
        "myLabel: {\n" +
        "  alert()\n" +
        "}\n");

    // Don't move the label on a loop, because then break {label} and
    // continue {label} won't work.
    assertPrettyPrint("myLabel: for(;;) continue myLabel;",
        "myLabel:for(;;) {\n" +
        "  continue myLabel\n" +
        "}\n");

    assertPrettyPrint("var a;", "var a;\n");
  }

  public void testPrettyPrinter2() {
    assertPrettyPrint(
        "if(true) f();",
        "if(true) {\n" +
        "  f()\n" +
        "}\n");

    assertPrettyPrint(
        "if (true) { f() } else { g() }",
        "if(true) {\n" +
        "  f()\n" +
        "}else {\n" +
        "  g()\n" +
        "}\n");

    assertPrettyPrint(
        "if(true) f(); for(;;) g();",
        "if(true) {\n" +
        "  f()\n" +
        "}\n" +
        "for(;;) {\n" +
        "  g()\n" +
        "}\n");
  }

  public void testPrettyPrinter3() {
    assertPrettyPrint(
        "try {} catch(e) {}if (1) {alert();alert();}",
        "try {\n" +
        "}catch(e) {\n" +
        "}\n" +
        "if(1) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint(
        "try {} finally {}if (1) {alert();alert();}",
        "try {\n" +
        "}finally {\n" +
        "}\n" +
        "if(1) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint(
        "try {} catch(e) {} finally {} if (1) {alert();alert();}",
        "try {\n" +
        "}catch(e) {\n" +
        "}finally {\n" +
        "}\n" +
        "if(1) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}\n");
  }

  public void testPrettyPrinter4() {
    assertPrettyPrint(
        "function f() {}if (1) {alert();}",
        "function f() {\n" +
        "}\n" +
        "if(1) {\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint(
        "var f = function() {};if (1) {alert();}",
        "var f = function() {\n" +
        "};\n" +
        "if(1) {\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint(
        "(function() {})();if (1) {alert();}",
        "(function() {\n" +
        "})();\n" +
        "if(1) {\n" +
        "  alert()\n" +
        "}\n");

    assertPrettyPrint(
        "(function() {alert();alert();})();if (1) {alert();}",
        "(function() {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "})();\n" +
        "if(1) {\n" +
        "  alert()\n" +
        "}\n");
  }

  public void testTypeAnnotations() {
    assertTypeAnnotations(
        "/** @constructor */ function Foo(){}",
        "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "function Foo() {\n}\n");
  }

  public void testTypeAnnotationsTypeDef() {
    // TODO(johnlenz): It would be nice if there were some way to preserve
    // typedefs but currently they are resolved into the basic types in the
    // type registry.
    assertTypeAnnotations(
        "/** @typedef {Array.<number>} */ goog.java.Long;\n"
        + "/** @param {!goog.java.Long} a*/\n"
        + "function f(a){};\n",
        "goog.java.Long;\n"
        + "/**\n"
        + " * @param {Array} a\n"
        + " * @return {undefined}\n"
        + " */\n"
        + "function f(a) {\n}\n");
  }

  public void testTypeAnnotationsAssign() {
    assertTypeAnnotations("/** @constructor */ var Foo = function(){}",
        "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "var Foo = function() {\n};\n");
  }

  public void testTypeAnnotationsNamespace() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){}",
        "var a = {};\n"
        + "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n");
  }

  public void testTypeAnnotationsMemberSubclass() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){};"
        + "/** @constructor \n @extends {a.Foo} */ a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n * @return {undefined}\n * @extends {a.Foo}\n"
        + " * @constructor\n */\n"
        + "a.Bar = function() {\n};\n");
  }

  public void testTypeAnnotationsInterface() {
    assertTypeAnnotations("var a = {};"
        + "/** @interface */ a.Foo = function(){};"
        + "/** @interface \n @extends {a.Foo} */ a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @interface\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n"
        + " * @interface\n */\n"
        + "a.Bar = function() {\n};\n");
  }

  public void testTypeAnnotationsMember() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){}"
        + "/** @param {string} foo\n"
        + "  * @return {number} */\n"
        + "a.Foo.prototype.foo = function(foo) { return 3; };"
        + "/** @type {string|undefined} */"
        + "a.Foo.prototype.bar = '';",
        "var a = {};\n"
        + "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n"
        + " * @param {string} foo\n"
        + " * @return {number}\n"
        + " */\n"
        + "a.Foo.prototype.foo = function(foo) {\n  return 3\n};\n"
        + "/** @type {string} */\n"
        + "a.Foo.prototype.bar = \"\";\n");
  }

  public void testTypeAnnotationsImplements() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){};\n"
        + "/** @interface */ a.I = function(){};\n"
        + "/** @interface */ a.I2 = function(){};\n"
        + "/** @constructor \n @extends {a.Foo}\n"
        + " * @implements {a.I} \n @implements {a.I2}\n"
        + "*/ a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @return {undefined}\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n * @interface\n */\n"
        + "a.I = function() {\n};\n"
        + "/**\n * @interface\n */\n"
        + "a.I2 = function() {\n};\n"
        + "/**\n * @return {undefined}\n * @extends {a.Foo}\n"
        + " * @implements {a.I}\n"
        + " * @implements {a.I2}\n * @constructor\n */\n"
        + "a.Bar = function() {\n};\n");
  }

  public void testTypeAnnotationsDispatcher1() {
    assertTypeAnnotations(
        "var a = {};\n" +
        "/** \n" +
        " * @constructor \n" +
        " * @javadispatch \n" +
        " */\n" +
        "a.Foo = function(){}",
        "var a = {};\n" +
        "/**\n" +
        " * @return {undefined}\n" +
        " * @constructor\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo = function() {\n" +
        "};\n");
  }

  public void testTypeAnnotationsDispatcher2() {
    assertTypeAnnotations(
        "var a = {};\n" +
        "/** \n" +
        " * @constructor \n" +
        " */\n" +
        "a.Foo = function(){}\n" +
        "/**\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo.prototype.foo = function() {};",

        "var a = {};\n" +
        "/**\n" +
        " * @return {undefined}\n" +
        " * @constructor\n" +
        " */\n" +
        "a.Foo = function() {\n" +
        "};\n" +
        "/**\n" +
        " * @return {undefined}\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo.prototype.foo = function() {\n" +
        "};\n");
  }

  public void testU2UFunctionTypeAnnotation() {
    assertTypeAnnotations(
        "/** @type {!Function} */ var x = function() {}",
        "/**\n * @constructor\n */\nvar x = function() {\n};\n");
  }

  public void testEmitUnknownParamTypesAsAllType() {
    assertTypeAnnotations(
        "var a = function(x) {}",
        "/**\n" +
        " * @param {*} x\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var a = function(x) {\n};\n");
  }

  public void testOptionalTypesAnnotation() {
    assertTypeAnnotations(
        "/**\n" +
        " * @param {string=} x \n" +
        " */\n" +
        "var a = function(x) {}",
        "/**\n" +
        " * @param {string=} x\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var a = function(x) {\n};\n");
  }

  public void testVariableArgumentsTypesAnnotation() {
    assertTypeAnnotations(
        "/**\n" +
        " * @param {...string} x \n" +
        " */\n" +
        "var a = function(x) {}",
        "/**\n" +
        " * @param {...string} x\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "var a = function(x) {\n};\n");
  }

  public void testTempConstructor() {
    assertTypeAnnotations(
        "var x = function() {\n/**\n * @constructor\n */\nfunction t1() {}\n" +
        " /**\n * @constructor\n */\nfunction t2() {}\n" +
        " t1.prototype = t2.prototype}",
        "/**\n * @return {undefined}\n */\nvar x = function() {\n" +
        "  /**\n * @return {undefined}\n * @constructor\n */\n" +
        "function t1() {\n  }\n" +
        "  /**\n * @return {undefined}\n * @constructor\n */\n" +
        "function t2() {\n  }\n" +
        "  t1.prototype = t2.prototype\n};\n"
    );
  }

  private void assertPrettyPrint(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, true, false,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }

  private void assertTypeAnnotations(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, true, false,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD, true));
  }

  public void testSubtraction() {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("x - -4");
    assertEquals(0, compiler.getErrorCount());

    assertEquals(
        "x- -4",
        new CodePrinter.Builder(n).setLineLengthThreshold(
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD).build());
  }

  public void testFunctionWithCall() {
    assertPrint(
        "var user = new function() {"
        + "alert(\"foo\")}",
        "var user=new function(){"
        + "alert(\"foo\")}");
    assertPrint(
        "var user = new function() {"
        + "this.name = \"foo\";"
        + "this.local = function(){alert(this.name)};}",
        "var user=new function(){"
        + "this.name=\"foo\";"
        + "this.local=function(){alert(this.name)}}");
  }

  public void testLineLength() {
    // list
    assertLineLength("var aba,bcb,cdc",
        "var aba,bcb," +
        "\ncdc");

    // operators, and two breaks
    assertLineLength(
        "\"foo\"+\"bar,baz,bomb\"+\"whee\"+\";long-string\"\n+\"aaa\"",
        "\"foo\"+\"bar,baz,bomb\"+" +
        "\n\"whee\"+\";long-string\"+" +
        "\n\"aaa\"");

    // assignment
    assertLineLength("var abazaba=1234",
        "var abazaba=" +
        "\n1234");

    // statements
    assertLineLength("var abab=1;var bab=2",
        "var abab=1;" +
        "\nvar bab=2");

    // don't break regexes
    assertLineLength("var a=/some[reg](ex),with.*we?rd|chars/i;var b=a",
        "var a=/some[reg](ex),with.*we?rd|chars/i;" +
        "\nvar b=a");

    // don't break strings
    assertLineLength("var a=\"foo,{bar};baz\";var b=a",
        "var a=\"foo,{bar};baz\";" +
        "\nvar b=a");

    // don't break before post inc/dec
    assertLineLength("var a=\"a\";a++;var b=\"bbb\";",
        "var a=\"a\";a++;\n" +
        "var b=\"bbb\"");
  }

  private void assertLineLength(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, false, true, 10));
  }

  public void testParsePrintParse() {
    testReparse("3;");
    testReparse("var a = b;");
    testReparse("var x, y, z;");
    testReparse("try { foo() } catch(e) { bar() }");
    testReparse("try { foo() } catch(e) { bar() } finally { stuff() }");
    testReparse("try { foo() } finally { stuff() }");
    testReparse("throw 'me'");
    testReparse("function foo(a) { return a + 4; }");
    testReparse("function foo() { return; }");
    testReparse("var a = function(a, b) { foo(); return a + b; }");
    testReparse("b = [3, 4, 'paul', \"Buchhe it\",,5];");
    testReparse("v = (5, 6, 7, 8)");
    testReparse("d = 34.0; x = 0; y = .3; z = -22");
    testReparse("d = -x; t = !x + ~y;");
    testReparse("'hi'; /* just a test */ stuff(a,b) \n foo(); // and another \n bar();");
    testReparse("a = b++ + ++c; a = b++-++c; a = - --b; a = - ++b;");
    testReparse("a++; b= a++; b = ++a; b = a--; b = --a; a+=2; b-=5");
    testReparse("a = (2 + 3) * 4;");
    testReparse("a = 1 + (2 + 3) + 4;");
    testReparse("x = a ? b : c; x = a ? (b,3,5) : (foo(),bar());");
    testReparse("a = b | c || d ^ e && f & !g != h << i <= j < k >>> l > m * n % !o");
    testReparse("a == b; a != b; a === b; a == b == a; (a == b) == a; a == (b == a);");
    testReparse("if (a > b) a = b; if (b < 3) a = 3; else c = 4;");
    testReparse("if (a == b) { a++; } if (a == 0) { a++; } else { a --; }");
    testReparse("for (var i in a) b += i;");
    testReparse("for (var i = 0; i < 10; i++){ b /= 2; if (b == 2)break;else continue;}");
    testReparse("for (x = 0; x < 10; x++) a /= 2;");
    testReparse("for (;;) a++;");
    testReparse("while(true) { blah(); }while(true) blah();");
    testReparse("do stuff(); while(a>b);");
    testReparse("[0, null, , true, false, this];");
    testReparse("s.replace(/absc/, 'X').replace(/ab/gi, 'Y');");
    testReparse("new Foo; new Bar(a, b,c);");
    testReparse("with(foo()) { x = z; y = t; } with(bar()) a = z;");
    testReparse("delete foo['bar']; delete foo;");
    testReparse("var x = { 'a':'paul', 1:'3', 2:(3,4) };");
    testReparse("switch(a) { case 2: case 3: stuff(); break;" +
        "case 4: morestuff(); break; default: done();}");
    testReparse("x = foo['bar'] + foo['my stuff'] + foo[bar] + f.stuff;");
    testReparse("a.v = b.v; x['foo'] = y['zoo'];");
    testReparse("'test' in x; 3 in x; a in x;");
    testReparse("'foo\"bar' + \"foo'c\" + 'stuff\\n and \\\\more'");
    testReparse("x.__proto__;");
  }

  private void testReparse(String code) {
    Compiler compiler = new Compiler();
    Node parse1 = parse(code);
    Node parse2 = parse(new CodePrinter.Builder(parse1).build());
    String explanation = parse1.checkTreeEquals(parse2);
    assertNull("\nExpected: " + compiler.toSource(parse1) +
        "\nResult: " + compiler.toSource(parse2) +
        "\n" + explanation, explanation);
  }

  public void testDoLoopIECompatiblity() {
    // Do loops within IFs cause syntax errors in IE6 and IE7.
    assertPrint("function(){if(e1){do foo();while(e2)}else foo()}",
        "function(){if(e1){do foo();while(e2)}else foo()}");

    assertPrint("function(){if(e1)do foo();while(e2)else foo()}",
        "function(){if(e1){do foo();while(e2)}else foo()}");

    assertPrint("if(x){do{foo()}while(y)}else bar()",
        "if(x){do foo();while(y)}else bar()");

    assertPrint("if(x)do{foo()}while(y);else bar()",
        "if(x){do foo();while(y)}else bar()");

    assertPrint("if(x){do{foo()}while(y)}",
        "if(x){do foo();while(y)}");

    assertPrint("if(x)do{foo()}while(y);",
        "if(x){do foo();while(y)}");

    assertPrint("if(x)A:do{foo()}while(y);",
        "if(x){A:do foo();while(y)}");

    assertPrint("var i = 0;a: do{b: do{i++;break b;} while(0);} while(0);",
        "var i=0;a:do{b:do{i++;break b}while(0)}while(0)");
  }

  public void testFunctionSafariCompatiblity() {
    // Functions within IFs cause syntax errors on Safari.
    assertPrint("function(){if(e1){function goo(){return true}}else foo()}",
        "function(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("function(){if(e1)function goo(){return true}else foo()}",
        "function(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("if(e1){function goo(){return true}}",
        "if(e1){function goo(){return true}}");

    assertPrint("if(e1)function goo(){return true}",
        "if(e1){function goo(){return true}}");

    assertPrint("if(e1)A:function goo(){return true}",
        "if(e1){A:function goo(){return true}}");
  }

  public void testExponents() {
    assertPrintNumber("1", 1);
    assertPrintNumber("10", 10);
    assertPrintNumber("100", 100);
    assertPrintNumber("1E3", 1000);
    assertPrintNumber("1E4", 10000);
    assertPrintNumber("1E5", 100000);
    assertPrintNumber("-1", -1);
    assertPrintNumber("-10", -10);
    assertPrintNumber("-100", -100);
    assertPrintNumber("-1E3", -1000);
    assertPrintNumber("-12341234E4", -123412340000L);
    assertPrintNumber("1E18", 1000000000000000000L);
    assertPrintNumber("1E5", 100000.0);
    assertPrintNumber("100000.1", 100000.1);

    assertPrintNumber("1.0E-6", 0.000001);
  }

  // Make sure to test as both a String and a Node, because
  // negative numbers do not parse consistently from strings.
  private void assertPrintNumber(String expected, double number) {
    assertPrint(String.valueOf(number), expected);
    assertPrintNode(expected, Node.newNumber(number));
  }

  private void assertPrintNumber(String expected, int number) {
    assertPrint(String.valueOf(number), expected);
    assertPrintNode(expected, Node.newNumber(number));
  }

  public void testDirectEval() {
    assertPrint("eval('1');", "eval(\"1\")");
  }

  public void testIndirectEval() {
    Node n = parse("eval('1');");
    assertPrintNode("eval(\"1\")", n);
    n.getFirstChild().getFirstChild().getFirstChild().putBooleanProp(
        Node.DIRECT_EVAL, false);
    assertPrintNode("(0,eval)(\"1\")", n);
  }

  public void testFreeCall1() {
    assertPrint("foo(a);", "foo(a)");
    assertPrint("x.foo(a);", "x.foo(a)");
  }

  public void testFreeCall2() {
    Node n = parse("foo(a);");
    assertPrintNode("foo(a)", n);
    Node call =  n.getFirstChild().getFirstChild();
    assertTrue(call.getType() == Token.CALL);
    call.putBooleanProp(Node.FREE_CALL, true);
    assertPrintNode("foo(a)", n);
  }

  public void testFreeCall3() {
    Node n = parse("x.foo(a);");
    assertPrintNode("x.foo(a)", n);
    Node call =  n.getFirstChild().getFirstChild();
    assertTrue(call.getType() == Token.CALL);
    call.putBooleanProp(Node.FREE_CALL, true);
    assertPrintNode("(0,x.foo)(a)", n);
  }

  public void testPrintScript() {
    // Verify that SCRIPT nodes not marked as synthetic are printed as
    // blocks.
    Node ast = new Node(Token.SCRIPT,
        new Node(Token.EXPR_RESULT, Node.newString("f")),
        new Node(Token.EXPR_RESULT, Node.newString("g")));
    String result = new CodePrinter.Builder(ast).setPrettyPrint(true).build();
    assertEquals("\"f\";\n\"g\";\n", result);
  }

  public void testGetter() {
    assertPrint("var x = {}", "var x={}");
    assertPrint("var x = {get a() {return 1}}", "var x={get a(){return 1}}");
    assertPrint(
      "var x = {get a() {}, get b(){}}",
      "var x={get a(){},get b(){}}");

    // Valid ES5 but Rhino doesn't accept this yet.
    // assertPrint(
    //  "var x = {get 1() {return 1}}",
    //  "var x={get \"1\"(){return 1}}");

    // Valid ES5 but Rhino doesn't accept this yet.
    // assertPrint(
    //  "var x = {get \"()\"() {return 1}}",
    //   "var x={get \"()\"(){return 1}}");
  }

  public void testSetter() {
    assertPrint("var x = {}", "var x={}");
    assertPrint("var x = {set a(x) {return 1}}", "var x={set a(x){return 1}}");

    // Valid ES5 but Rhino doesn't accept this yet.
    // assertPrint(
    //  "var x = {set 1(x) {return 1}}",
    //  "var x={set \"1\"(x){return 1}}");

    // Valid ES5 but Rhino doesn't accept this yet.
    // assertPrint(
    //  "var x = {set \"(x)\"() {return 1}}",
    //   "var x={set \"(x)\"(){return 1}}");
  }

  public void testNegCollapse() {
    // Collapse the negative symbol on numbers at generation time,
    // to match the Rhino behavior.
    assertPrint("var x = - - 2;", "var x=2");
    assertPrint("var x = - (2);", "var x=-2");
  }
}
