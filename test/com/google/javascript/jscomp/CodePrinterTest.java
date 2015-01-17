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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.List;


public class CodePrinterTest extends TestCase {
  // If this is set, ignore parse warnings and only fail the test
  // for parse errors.
  private boolean allowWarnings = false;

  private boolean trustedStrings = true;
  private boolean preserveTypeAnnotations = false;
  private Compiler lastCompiler = null;
  private LanguageMode languageMode = LanguageMode.ECMASCRIPT5;

  @Override public void setUp() {
    allowWarnings = false;
    preserveTypeAnnotations = false;
    trustedStrings = true;
    lastCompiler = null;
    languageMode = LanguageMode.ECMASCRIPT5;
  }

  Node parse(String js) {
    return parse(js, false);
  }

  Node parse(String js, boolean checkTypes) {
    Compiler compiler = new Compiler();
    lastCompiler = compiler;
    CompilerOptions options = new CompilerOptions();
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;

    // Allow getters and setters.
    options.setLanguageIn(languageMode);
    compiler.initOptions(options);
    Node n = compiler.parseTestCode(js);

    if (checkTypes) {
      DefaultPassConfig passConfig = new DefaultPassConfig(null);
      CompilerPass typeResolver = passConfig.resolveTypes.create(compiler);
      Node externs = new Node(Token.SCRIPT);
      externs.setInputId(new InputId("externs"));
      Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
      externAndJsRoot.setIsSyntheticBlock(true);
      typeResolver.process(externs, n);
      CompilerPass inferTypes = passConfig.inferTypes.create(compiler);
      inferTypes.process(externs, n);
    }

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return n;
  }

  private void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().length;
    if (!allowWarnings) {
      actual += compiler.getWarnings().length;
    }

    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err + "\n";
      }
      if (!allowWarnings) {
        for (JSError err : compiler.getWarnings()) {
          msg += "Warning:" + err + "\n";
        }
      }
      assertEquals("Unexpected warnings or errors.\n " + msg, expected, actual);
    }
  }

  String parsePrint(String js, CompilerOptions options) {
    return new CodePrinter.Builder(parse(js)).setCompilerOptions(options).build();
  }

  private abstract class CompilerOptionBuilder {
    abstract void setOptions(CompilerOptions options);
  }

  CompilerOptions newCompilerOptions(CompilerOptionBuilder builder) {
    CompilerOptions options = new CompilerOptions();
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;
    options.setLanguageOut(languageMode);
    builder.setOptions(options);
    return options;
  }

  String printNode(Node n) {
    CompilerOptions options = new CompilerOptions();
    options.setLineLengthThreshold(CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
    options.setLanguageOut(languageMode);
    return new CodePrinter.Builder(n).setCompilerOptions(options).build();
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
        "var a,b,c;a||(b||c);a*(b*c);a|(b|c)");
    assertPrint("var a,b,c; a / b / c;a / (b / c); a - (b - c);",
        "var a,b,c;a/b/c;a/(b/c);a-(b-c)");

    // Nested assignments
    assertPrint("var a,b; a = b = 3;",
        "var a,b;a=b=3");
    assertPrint("var a,b,c,d; a = (b = c = (d = 3));",
        "var a,b,c,d;a=b=c=d=3");
    assertPrint("var a,b,c; a += (b = c += 3);",
        "var a,b,c;a+=b=c+=3");
    assertPrint("var a,b,c; a *= (b -= c);",
        "var a,b,c;a*=b-=c");

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

    // Make sure we don't treat non-Latin character escapes as raw strings.
    assertPrint("({ 'a': 4, '\\u0100': 4 })", "({\"a\":4,\"\\u0100\":4})");
    assertPrint("({ a: 4, '\\u0100': 4 })", "({a:4,\"\\u0100\":4})");

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

  public void testPrintArrayPatternVar() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("var []=[]");
    assertPrintSame("var [a]=[1]");
    assertPrintSame("var [a,b]=[1,2]");
    assertPrintSame("var [a,...b]=[1,2]");
    assertPrintSame("var [,b]=[1,2]");
    assertPrintSame("var [,,,,,,g]=[1,2,3,4,5,6,7]");
    assertPrintSame("var [a,,c]=[1,2,3]");
    assertPrintSame("var [a,,,d]=[1,2,3,4]");
    assertPrintSame("var [a,,c,,e]=[1,2,3,4,5]");
  }

  public void testPrintArrayPatternLet() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("let []=[]");
    assertPrintSame("let [a]=[1]");
    assertPrintSame("let [a,b]=[1,2]");
    assertPrintSame("let [a,...b]=[1,2]");
    assertPrintSame("let [,b]=[1,2]");
    assertPrintSame("let [,,,,,,g]=[1,2,3,4,5,6,7]");
    assertPrintSame("let [a,,c]=[1,2,3]");
    assertPrintSame("let [a,,,d]=[1,2,3,4]");
    assertPrintSame("let [a,,c,,e]=[1,2,3,4,5]");
  }

  public void testPrintArrayPatternConst() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("const []=[]");
    assertPrintSame("const [a]=[1]");
    assertPrintSame("const [a,b]=[1,2]");
    assertPrintSame("const [a,...b]=[1,2]");
    assertPrintSame("const [,b]=[1,2]");
    assertPrintSame("const [,,,,,,g]=[1,2,3,4,5,6,7]");
    assertPrintSame("const [a,,c]=[1,2,3]");
    assertPrintSame("const [a,,,d]=[1,2,3,4]");
    assertPrintSame("const [a,,c,,e]=[1,2,3,4,5]");
  }

  public void testPrintArrayPatternAssign() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("[]=[]");
    assertPrintSame("[a]=[1]");
    assertPrintSame("[a,b]=[1,2]");
    assertPrintSame("[a,...b]=[1,2]");
    assertPrintSame("[,b]=[1,2]");
    assertPrintSame("[,,,,,,g]=[1,2,3,4,5,6,7]");
    assertPrintSame("[a,,c]=[1,2,3]");
    assertPrintSame("[a,,,d]=[1,2,3,4]");
    assertPrintSame("[a,,c,,e]=[1,2,3,4,5]");
  }

  public void testPrintArrayPatternWithInitializer() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("[x=1]=[]");
    assertPrintSame("[a,,c=2,,e]=[1,2,3,4,5]");
    assertPrintSame("[a=1,b=2,c=3]=foo()");
  }

  public void testPrintNestedArrayPattern() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("var [a,[b,c],d]=[1,[2,3],4]");
    assertPrintSame("var [[[[a]]]]=[[[[1]]]]");

    assertPrintSame("[a,[b,c],d]=[1,[2,3],4]");
    assertPrintSame("[[[[a]]]]=[[[[1]]]]");
  }

  public void testPrintObjectPatternVar() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("var {a}=foo()");
    assertPrintSame("var {a,b}=foo()");
    assertPrintSame("var {a:a,b:b}=foo()");
  }

  public void testPrintObjectPatternLet() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("let {a}=foo()");
    assertPrintSame("let {a,b}=foo()");
    assertPrintSame("let {a:a,b:b}=foo()");
  }

  public void testPrintObjectPatternConst() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("const {a}=foo()");
    assertPrintSame("const {a,b}=foo()");
    assertPrintSame("const {a:a,b:b}=foo()");
  }

  public void testPrintObjectPatternAssign() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("({a})=foo()");
    assertPrintSame("({a,b})=foo()");
    assertPrintSame("({a:a,b:b})=foo()");
  }

  public void testPrintNestedObjectPattern() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("({a:{b,c}})=foo()");
    assertPrintSame("({a:{b:{c:{d}}}})=foo()");
  }

  public void testPrintObjectPatternInitializer() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("({a=1})=foo()");
    assertPrintSame("({a:{b=2}})=foo()");
    assertPrintSame("({a:b=2})=foo()");
    assertPrintSame("({a,b:{c=2}})=foo()");
    assertPrintSame("({a:{b=2},c})=foo()");
  }

  public void testPrintMixedDestructuring() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("({a:[b,c]})=foo()");
    assertPrintSame("[a,{b,c}]=foo()");
  }

  public void testPrintDestructuringInParamList() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("function f([a]){}");
    assertPrintSame("function f([a,b]){}");
    assertPrintSame("function f([a,b]=c()){}");
    assertPrintSame("function f({a}){}");
    assertPrintSame("function f({a,b}){}");
    assertPrintSame("function f({a,b}=c()){}");
    assertPrintSame("function f([a,{b,c}]){}");
    assertPrintSame("function f({a,b:[c,d]}){}");
  }

  public void testBreakTrustedStrings() {
    // Break scripts
    assertPrint("'<script>'", "\"<script>\"");
    assertPrint("'</script>'", "\"\\x3c/script>\"");
    assertPrint("\"</script> </SCRIPT>\"", "\"\\x3c/script> \\x3c/SCRIPT>\"");

    assertPrint("'-->'", "\"--\\x3e\"");
    assertPrint("']]>'", "\"]]\\x3e\"");
    assertPrint("' --></script>'", "\" --\\x3e\\x3c/script>\"");

    assertPrint("/--> <\\/script>/g", "/--\\x3e <\\/script>/g");

    // Break HTML start comments. Certain versions of WebKit
    // begin an HTML comment when they see this.
    assertPrint("'<!-- I am a string -->'",
        "\"\\x3c!-- I am a string --\\x3e\"");

    assertPrint("'<=&>'", "\"<=&>\"");
  }

  public void testBreakUntrustedStrings() {
    trustedStrings = false;

    // Break scripts
    assertPrint("'<script>'", "\"\\x3cscript\\x3e\"");
    assertPrint("'</script>'", "\"\\x3c/script\\x3e\"");
    assertPrint("\"</script> </SCRIPT>\"", "\"\\x3c/script\\x3e \\x3c/SCRIPT\\x3e\"");

    assertPrint("'-->'", "\"--\\x3e\"");
    assertPrint("']]>'", "\"]]\\x3e\"");
    assertPrint("' --></script>'", "\" --\\x3e\\x3c/script\\x3e\"");

    assertPrint("/--> <\\/script>/g", "/--\\x3e <\\/script>/g");

    // Break HTML start comments. Certain versions of WebKit
    // begin an HTML comment when they see this.
    assertPrint("'<!-- I am a string -->'",
        "\"\\x3c!-- I am a string --\\x3e\"");

    assertPrint("'<=&>'", "\"\\x3c\\x3d\\x26\\x3e\"");
    assertPrint("/(?=x)/", "/(?=x)/");
  }

  public void testHtmlComments() {
    assertPrint("3< !(--x)", "3< !--x");
    assertPrint("while (x-- > 0) {}", "while(x-- >0);");
  }

  public void testPrintArray() {
    assertPrint("[void 0, void 0]", "[void 0,void 0]");
    assertPrint("[undefined, undefined]", "[undefined,undefined]");
    assertPrint("[ , , , undefined]", "[,,,undefined]");
    assertPrint("[ , , , 0]", "[,,,0]");
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
    assertPrint("var a={};for (var i = (a || a) || (\"size\" in a);;);",
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

    // Test we correctly handle an in operator inside a comma.
    assertPrintSame("for(x,(y in z);;)foo()");
    assertPrintSame("for(var x,w=(y in z);;)foo()");

    // And in operator inside a hook.
    assertPrintSame("for(a=c?0:(0 in d);;)foo()");
  }

  public void testForOf() {
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrintSame("for(a of b)c");
    assertPrintSame("for(var a of b)c");
  }

  public void testLetFor() {
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrintSame("for(let a=0;a<5;a++)b");
    assertPrintSame("for(let a in b)c");
    assertPrintSame("for(let a of b)c");
  }

  public void testConstFor() {
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrintSame("for(const a=5;b<a;b++)c");
    assertPrintSame("for(const a in b)c");
    assertPrintSame("for(const a of b)c");
  }

  public void testLiteralProperty() {
    assertPrint("(64).toString()", "(64).toString()");
  }

  private void assertPrint(String js, String expected) {
    parse(expected); // validate the expected string is valid JS
    assertEquals(expected,
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineLengthThreshold(CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
          }
        })));
  }

  private void assertPrintSame(String js) {
    assertPrint(js, js);
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
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(true);
            options.setLineLengthThreshold(CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
          }
        })));
  }

  public void testPreferLineBreakAtEndOfFile() {
    // short final line, no previous break, do nothing
    assertLineBreakAtEndOfFile(
        "\"1234567890\";",
        "\"1234567890\"",
        "\"1234567890\"");

    // short final line, shift previous break to end
    assertLineBreakAtEndOfFile(
        "\"123456789012345678901234567890\";\"1234567890\"",
        "\"123456789012345678901234567890\";\n\"1234567890\"",
        "\"123456789012345678901234567890\"; \"1234567890\";\n");
    assertLineBreakAtEndOfFile(
        "var12345678901234567890123456 instanceof Object;",
        "var12345678901234567890123456 instanceof\nObject",
        "var12345678901234567890123456 instanceof Object;\n");

    // long final line, no previous break, add a break at end
    assertLineBreakAtEndOfFile(
        "\"1234567890\";\"12345678901234567890\";",
        "\"1234567890\";\"12345678901234567890\"",
        "\"1234567890\";\"12345678901234567890\";\n");

    // long final line, previous break, add a break at end
    assertLineBreakAtEndOfFile(
        "\"123456789012345678901234567890\";\"12345678901234567890\";",
        "\"123456789012345678901234567890\";\n\"12345678901234567890\"",
        "\"123456789012345678901234567890\";\n\"12345678901234567890\";\n");
  }

  private void assertLineBreakAtEndOfFile(String js,
      String expectedWithoutBreakAtEnd, String expectedWithBreakAtEnd) {
    assertEquals(expectedWithoutBreakAtEnd,
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(false);
            options.setLineLengthThreshold(30);
            options.setPreferLineBreakAtEndOfFile(false);
          }
        })));
    assertEquals(expectedWithBreakAtEnd,
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(false);
            options.setLineLengthThreshold(30);
            options.setPreferLineBreakAtEndOfFile(true);
          }
        })));
  }

  public void testPrettyPrinter() {
    // Ensure that the pretty printer inserts line breaks at appropriate
    // places.
    assertPrettyPrint("(function(){})();", "(function() {\n})();\n");
    assertPrettyPrint("var a = (function() {});alert(a);",
        "var a = function() {\n};\nalert(a);\n");

    // Check we correctly handle putting brackets around all if clauses so
    // we can put breakpoints inside statements.
    assertPrettyPrint("if (1) {}",
        "if (1) {\n" +
        "}\n");
    assertPrettyPrint("if (1) {alert(\"\");}",
        "if (1) {\n" +
        "  alert(\"\");\n" +
        "}\n");
    assertPrettyPrint("if (1)alert(\"\");",
        "if (1) {\n" +
        "  alert(\"\");\n" +
        "}\n");
    assertPrettyPrint("if (1) {alert();alert();}",
        "if (1) {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "}\n");

    // Don't add blocks if they weren't there already.
    assertPrettyPrint("label: alert();",
        "label: alert();\n");

    // But if statements and loops get blocks automagically.
    assertPrettyPrint("if (1) alert();",
        "if (1) {\n" +
        "  alert();\n" +
        "}\n");
    assertPrettyPrint("for (;;) alert();",
        "for (;;) {\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint("while (1) alert();",
        "while (1) {\n" +
        "  alert();\n" +
        "}\n");

    // Do we put else clauses in blocks?
    assertPrettyPrint("if (1) {} else {alert(a);}",
        "if (1) {\n" +
        "} else {\n  alert(a);\n}\n");

    // Do we add blocks to else clauses?
    assertPrettyPrint("if (1) alert(a); else alert(b);",
        "if (1) {\n" +
        "  alert(a);\n" +
        "} else {\n" +
        "  alert(b);\n" +
        "}\n");

    // Do we put for bodies in blocks?
    assertPrettyPrint("for(;;) { alert();}",
        "for (;;) {\n" +
         "  alert();\n" +
         "}\n");
    assertPrettyPrint("for(;;) {}",
        "for (;;) {\n" +
        "}\n");
    assertPrettyPrint("for(;;) { alert(); alert(); }",
        "for (;;) {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "}\n");

    // How about do loops?
    assertPrettyPrint("do { alert(); } while(true);",
        "do {\n" +
        "  alert();\n" +
        "} while (true);\n");

    // label?
    assertPrettyPrint("myLabel: { alert();}",
        "myLabel: {\n" +
        "  alert();\n" +
        "}\n");

    // Don't move the label on a loop, because then break {label} and
    // continue {label} won't work.
    assertPrettyPrint("myLabel: for(;;) continue myLabel;",
        "myLabel: for (;;) {\n" +
        "  continue myLabel;\n" +
        "}\n");

    assertPrettyPrint("var a;", "var a;\n");

    // There must be a space before and after binary operators.
    assertPrettyPrint("var foo = 3+5;",
        "var foo = 3 + 5;\n");

    // There should be spaces between the ternary operator
    assertPrettyPrint("var foo = bar ? 3 : null;",
        "var foo = bar ? 3 : null;\n");

    // Ensure that string literals after return and throw have a space.
    assertPrettyPrint("function foo() { return \"foo\"; }",
        "function foo() {\n  return \"foo\";\n}\n");
    assertPrettyPrint("throw \"foo\";",
        "throw \"foo\";");

    // Test that loops properly have spaces inserted.
    assertPrettyPrint("do{ alert(); } while(true);",
        "do {\n  alert();\n} while (true);\n");
    assertPrettyPrint("while(true) { alert(); }",
        "while (true) {\n  alert();\n}\n");
  }

  public void testPrettyPrinter2() {
    assertPrettyPrint(
        "if(true) f();",
        "if (true) {\n" +
        "  f();\n" +
        "}\n");

    assertPrettyPrint(
        "if (true) { f() } else { g() }",
        "if (true) {\n" +
        "  f();\n" +
        "} else {\n" +
        "  g();\n" +
        "}\n");

    assertPrettyPrint(
        "if(true) f(); for(;;) g();",
        "if (true) {\n" +
        "  f();\n" +
        "}\n" +
        "for (;;) {\n" +
        "  g();\n" +
        "}\n");
  }

  public void testPrettyPrinter3() {
    assertPrettyPrint(
        "try {} catch(e) {}if (1) {alert();alert();}",
        "try {\n" +
        "} catch (e) {\n" +
        "}\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint(
        "try {} finally {}if (1) {alert();alert();}",
        "try {\n" +
        "} finally {\n" +
        "}\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint(
        "try {} catch(e) {} finally {} if (1) {alert();alert();}",
        "try {\n" +
        "} catch (e) {\n" +
        "} finally {\n" +
        "}\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "}\n");
  }

  public void testPrettyPrinter4() {
    assertPrettyPrint(
        "function f() {}if (1) {alert();}",
        "function f() {\n" +
        "}\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint(
        "var f = function() {};if (1) {alert();}",
        "var f = function() {\n" +
        "};\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint(
        "(function() {})();if (1) {alert();}",
        "(function() {\n" +
        "})();\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "}\n");

    assertPrettyPrint(
        "(function() {alert();alert();})();if (1) {alert();}",
        "(function() {\n" +
        "  alert();\n" +
        "  alert();\n" +
        "})();\n" +
        "if (1) {\n" +
        "  alert();\n" +
        "}\n");
  }

  // For https://github.com/google/closure-compiler/issues/782
  public void testPrettyPrinter_spaceBeforeSingleQuote() throws Exception {
    assertPrettyPrint("var f = function() { return 'hello';};",
        "var f = function() {\n" +
            "  return 'hello';\n" +
            "};\n",
        new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPreferSingleQuotes(true);
          }
        });
  }

  // For https://github.com/google/closure-compiler/issues/782
  public void testPrettyPrinter_spaceBeforeBangOperator() throws Exception {
    assertPrettyPrint("var f = function() { return !b; };",
        "var f = function() {\n" +
        "  return !b;\n" +
        "};\n");
  }

  public void testTypeAnnotations() {
    assertTypeAnnotations(
        "/** @constructor */ function Foo(){}",
        "/**\n * @constructor\n */\n"
        + "function Foo() {\n}\n");
  }

  public void testTypeAnnotationsTypeDef() {
    // TODO(johnlenz): It would be nice if there were some way to preserve
    // typedefs but currently they are resolved into the basic types in the
    // type registry.
    assertTypeAnnotations(
        "/** @typedef {Array<number>} */ goog.java.Long;\n"
        + "/** @param {!goog.java.Long} a*/\n"
        + "function f(a){};\n",
        "goog.java.Long;\n"
        + "/**\n"
        + " * @param {(Array<number>|null)} a\n"
        + " * @return {undefined}\n"
        + " */\n"
        + "function f(a) {\n}\n");
  }

  public void testTypeAnnotationsAssign() {
    assertTypeAnnotations("/** @constructor */ var Foo = function(){}",
        "/**\n * @constructor\n */\n"
        + "var Foo = function() {\n};\n");
  }

  public void testTypeAnnotationsNamespace() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){}",
        "var a = {};\n"
        + "/**\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n");
  }

  public void testTypeAnnotationsMemberSubclass() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){};"
        + "/** @constructor \n @extends {a.Foo} */ a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n"
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

  public void testTypeAnnotationsMultipleInterface() {
    assertTypeAnnotations("var a = {};"
        + "/** @interface */ a.Foo1 = function(){};"
        + "/** @interface */ a.Foo2 = function(){};"
        + "/** @interface \n @extends {a.Foo1} \n @extends {a.Foo2} */"
        + "a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @interface\n */\n"
        + "a.Foo1 = function() {\n};\n"
        + "/**\n * @interface\n */\n"
        + "a.Foo2 = function() {\n};\n"
        + "/**\n * @extends {a.Foo1}\n"
        + " * @extends {a.Foo2}\n"
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
        + "/**\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n"
        + " * @param {string} foo\n"
        + " * @return {number}\n"
        + " */\n"
        + "a.Foo.prototype.foo = function(foo) {\n  return 3;\n};\n"
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
        + "/**\n * @constructor\n */\n"
        + "a.Foo = function() {\n};\n"
        + "/**\n * @interface\n */\n"
        + "a.I = function() {\n};\n"
        + "/**\n * @interface\n */\n"
        + "a.I2 = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n"
        + " * @implements {a.I}\n"
        + " * @implements {a.I2}\n * @constructor\n */\n"
        + "a.Bar = function() {\n};\n");
  }

  public void testU2UFunctionTypeAnnotation1() {
    assertTypeAnnotations(
        "/** @type {!Function} */ var x = function() {}",
        "/** @type {!Function} */\n" +
        "var x = function() {\n};\n");
  }

  public void testU2UFunctionTypeAnnotation2() {
    // TODO(johnlenz): we currently report the type of the RHS which is not
    // correct, we should export the type of the LHS.
    assertTypeAnnotations(
        "/** @type {Function} */ var x = function() {}",
        "/** @type {!Function} */\n" +
        "var x = function() {\n};\n");
  }

  public void testEmitUnknownParamTypesAsAllType() {
    assertTypeAnnotations(
        "var a = function(x) {}",
        "/**\n" +
        " * @param {?} x\n" +
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
        "  /**\n * @constructor\n */\n" +
        "function t1() {\n  }\n" +
        "  /**\n * @constructor\n */\n" +
        "function t2() {\n  }\n" +
        "  t1.prototype = t2.prototype;\n};\n"
    );
  }

  public void testEnumAnnotation1() {
    assertTypeAnnotations(
        "/** @enum {string} */ var Enum = {FOO: 'x', BAR: 'y'};",
        "/** @enum {string} */\nvar Enum = {FOO:\"x\", BAR:\"y\"};\n");
  }

  public void testEnumAnnotation2() {
    assertTypeAnnotations(
        "var goog = goog || {};" +
        "/** @enum {string} */ goog.Enum = {FOO: 'x', BAR: 'y'};" +
        "/** @const */ goog.Enum2 = goog.x ? {} : goog.Enum;",
        "var goog = goog || {};\n" +
        "/** @enum {string} */\ngoog.Enum = {FOO:\"x\", BAR:\"y\"};\n" +
        "/** @type {(Object|{})} */\ngoog.Enum2 = goog.x ? {} : goog.Enum;\n");
  }

  private void assertPrettyPrint(String js, String expected) {
    assertPrettyPrint(js, expected, new CompilerOptionBuilder() {
      @Override void setOptions(CompilerOptions options) { }
    });
  }

  private void assertPrettyPrint(String js, String expected,
                                 final CompilerOptionBuilder optionBuilder) {
    assertEquals(expected,
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPrettyPrint(true);
            options.setLineBreak(false);
            options.setLineLengthThreshold(CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
            optionBuilder.setOptions(options);
          }
        })));
  }

  private void assertTypeAnnotations(String js, String expected) {
    assertEquals(expected,
        new CodePrinter.Builder(parse(js, true))
            .setCompilerOptions(newCompilerOptions(new CompilerOptionBuilder() {
              @Override
              void setOptions(CompilerOptions options) {
                options.setPrettyPrint(true);
                options.setLineBreak(false);
                options.setLineLengthThreshold(CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD);
              }
            }))
            .setOutputTypes(true)
            .setTypeRegistry(lastCompiler.getTypeRegistry())
            .build());
  }

  public void testSubtraction() {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("x - -4");
    assertEquals(0, compiler.getErrorCount());

    assertEquals(
        "x- -4",
        printNode(n));
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
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(true);
            options.setLineLengthThreshold(10);
          }
        })));
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
    testReparse("'hi'; /* just a test */ stuff(a,b) \n" +
            " foo(); // and another \n" +
            " bar();");
    testReparse("a = b++ + ++c; a = b++-++c; a = - --b; a = - ++b;");
    testReparse("a++; b= a++; b = ++a; b = a--; b = --a; a+=2; b-=5");
    testReparse("a = (2 + 3) * 4;");
    testReparse("a = 1 + (2 + 3) + 4;");
    testReparse("x = a ? b : c; x = a ? (b,3,5) : (foo(),bar());");
    testReparse("a = b | c || d ^ e " +
            "&& f & !g != h << i <= j < k >>> l > m * n % !o");
    testReparse("a == b; a != b; a === b; a == b == a;" +
            " (a == b) == a; a == (b == a);");
    testReparse("if (a > b) a = b; if (b < 3) a = 3; else c = 4;");
    testReparse("if (a == b) { a++; } if (a == 0) { a++; } else { a --; }");
    testReparse("for (var i in a) b += i;");
    testReparse("for (var i = 0; i < 10; i++){ b /= 2;" +
            " if (b == 2)break;else continue;}");
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

  public void testDoLoopIECompatibility() {
    // Do loops within IFs cause syntax errors in IE6 and IE7.
    assertPrint("function f(){if(e1){do foo();while(e2)}else foo()}",
        "function f(){if(e1){do foo();while(e2)}else foo()}");

    assertPrint("function f(){if(e1)do foo();while(e2)else foo()}",
        "function f(){if(e1){do foo();while(e2)}else foo()}");

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

  public void testFunctionSafariCompatibility() {
    // Functions within IFs cause syntax errors on Safari.
    assertPrint("function f(){if(e1){function goo(){return true}}else foo()}",
        "function f(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("function f(){if(e1)function goo(){return true}else foo()}",
        "function f(){if(e1){function goo(){return true}}else foo()}");

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

    assertPrintNumber("1E-6", 0.000001);
    assertPrintNumber("-0x38d7ea4c68001", -0x38d7ea4c68001L);
    assertPrintNumber("0x38d7ea4c68001", 0x38d7ea4c68001L);
    assertPrintNumber("0x7fffffffffffffff", 0x7fffffffffffffffL);

    assertPrintNumber("-1.01", -1.01);
    assertPrintNumber("-.01", -0.01);
    assertPrintNumber(".01", 0.01);
    assertPrintNumber("1.01", 1.01);
  }

  public void testBiggerThanMaxLongNumericLiterals() {
    // Since ECMAScript implements IEEE 754 "round to nearest, ties to even",
    // any literal in the range [0x7ffffffffffffe00,0x8000000000000400] will
    // round to the same value, namely 2^63. The fact that we print this as
    // 2^63-1 doesn't matter, since it must be rounded back to 2^63 at runtime.
    // See:
    //   http://www.ecma-international.org/ecma-262/5.1/#sec-8.5
    assertPrint("9223372036854775808", "0x7fffffffffffffff");
    assertPrint("0x8000000000000000", "0x7fffffffffffffff");
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrint(
        "0b1000000000000000000000000000000000000000000000000000000000000000",
        "0x7fffffffffffffff");
    assertPrint("0o1000000000000000000000", "0x7fffffffffffffff");
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
    assertTrue(call.isCall());
    call.putBooleanProp(Node.FREE_CALL, true);
    assertPrintNode("foo(a)", n);
  }

  public void testFreeCall3() {
    Node n = parse("x.foo(a);");
    assertPrintNode("x.foo(a)", n);
    Node call =  n.getFirstChild().getFirstChild();
    assertTrue(call.isCall());
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

  public void testObjectLit() {
    assertPrint("({x:1})", "({x:1})");
    assertPrint("var x=({x:1})", "var x={x:1}");
    assertPrint("var x={'x':1}", "var x={\"x\":1}");
    assertPrint("var x={1:1}", "var x={1:1}");
    assertPrint("({},42)+0", "({},42)+0");
  }

  public void testObjectLit2() {
    assertPrint("var x={1:1}", "var x={1:1}");
    assertPrint("var x={'1':1}", "var x={1:1}");
    assertPrint("var x={'1.0':1}", "var x={\"1.0\":1}");
    assertPrint("var x={1.5:1}", "var x={\"1.5\":1}");

  }

  public void testObjectLit3() {
    assertPrint("var x={3E9:1}",
                "var x={3E9:1}");
    assertPrint("var x={'3000000000':1}", // More than 31 bits
                "var x={3E9:1}");
    assertPrint("var x={'3000000001':1}",
                "var x={3000000001:1}");
    assertPrint("var x={'6000000001':1}",  // More than 32 bits
                "var x={6000000001:1}");
    assertPrint("var x={\"12345678901234567\":1}",  // More than 53 bits
                "var x={\"12345678901234567\":1}");
  }

  public void testObjectLit4() {
    // More than 128 bits.
    assertPrint(
        "var x={\"123456789012345671234567890123456712345678901234567\":1}",
        "var x={\"123456789012345671234567890123456712345678901234567\":1}");
  }

  public void testExtendedObjectLit() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("var a={b}");
    assertPrintSame("var a={b,c}");
    assertPrintSame("var a={b,c:d,e}");
    assertPrintSame("var a={b,c(){},d,e:f}");
  }

  public void testComputedProperties() {
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrintSame("var a={[b]:c}");
    assertPrintSame("var a={[b+3]:c}");

    assertPrintSame("var a={[b](){}}");
    assertPrintSame("var a={[b](){alert(foo)}}");
    assertPrintSame("var a={*[b](){yield\"foo\"}}");
    assertPrintSame("var a={[b]:(()=>c)}");

    assertPrintSame("var a={get [b](){return null}}");
    assertPrintSame("var a={set [b](val){window.b=val}}");
  }

  public void testComputedPropertiesClassMethods() {
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrintSame("class C{[m](){}}");

    assertPrintSame("class C{[\"foo\"+bar](){alert(1)}}");
  }

  public void testGetter() {
    assertPrint("var x = {}", "var x={}");
    assertPrint("var x = {get a() {return 1}}", "var x={get a(){return 1}}");
    assertPrint(
      "var x = {get a() {}, get b(){}}",
      "var x={get a(){},get b(){}}");

    assertPrint(
      "var x = {get 'a'() {return 1}}",
      "var x={get \"a\"(){return 1}}");

    assertPrint(
      "var x = {get 1() {return 1}}",
      "var x={get 1(){return 1}}");

    assertPrint(
      "var x = {get \"()\"() {return 1}}",
      "var x={get \"()\"(){return 1}}");

    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("var x={get function(){return 1}}");

  }

  public void testGetterInEs3() {
    // Getters and setters and not supported in ES3 but if someone sets the
    // the ES3 output mode on an AST containing them we still produce them.
    languageMode = LanguageMode.ECMASCRIPT3;

    Node getter = Node.newString(Token.GETTER_DEF, "f");
    getter.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    assertPrintNode("({get f(){}})",
        IR.exprResult(IR.objectlit(getter)));
  }


  public void testSetter() {
    assertPrint("var x = {}", "var x={}");
    assertPrint(
       "var x = {set a(y) {return 1}}",
       "var x={set a(y){return 1}}");

    assertPrint(
      "var x = {get 'a'() {return 1}}",
      "var x={get \"a\"(){return 1}}");

    assertPrint(
      "var x = {set 1(y) {return 1}}",
      "var x={set 1(y){return 1}}");

    assertPrint(
      "var x = {set \"(x)\"(y) {return 1}}",
      "var x={set \"(x)\"(y){return 1}}");

    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("var x={set function(x){}}");
  }

  public void testSetterInEs3() {
    // Getters and setters and not supported in ES3 but if someone sets the
    // the ES3 output mode on an AST containing them we still produce them.
    languageMode = LanguageMode.ECMASCRIPT3;

    Node getter = Node.newString(Token.SETTER_DEF, "f");
    getter.addChildToBack(IR.function(
        IR.name(""), IR.paramList(IR.name("a")), IR.block()));
    assertPrintNode("({set f(a){}})",
        IR.exprResult(IR.objectlit(getter)));
  }

  public void testNegCollapse() {
    // Collapse the negative symbol on numbers at generation time,
    // to match the Rhino behavior.
    assertPrint("var x = - - 2;", "var x=2");
    assertPrint("var x = - (2);", "var x=-2");
  }

  public void testStrict() {
    String result = new CodePrinter.Builder(parse("var x", true))
        .setCompilerOptions(newCompilerOptions(new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(false);
            options.setLineLengthThreshold(0);
          }
        }))
        .setOutputTypes(false)
        .setTypeRegistry(lastCompiler.getTypeRegistry())
        .setTagAsStrict(true)
        .build();
    assertEquals("'use strict';var x", result);
  }

  public void testArrayLiteral() {
    assertPrint("var x = [,];", "var x=[,]");
    assertPrint("var x = [,,];", "var x=[,,]");
    assertPrint("var x = [,s,,];", "var x=[,s,,]");
    assertPrint("var x = [,s];", "var x=[,s]");
    assertPrint("var x = [s,];", "var x=[s]");
  }

  public void testZero() {
    assertPrint("var x ='\\0';", "var x=\"\\x00\"");
    assertPrint("var x ='\\x00';", "var x=\"\\x00\"");
    assertPrint("var x ='\\u0000';", "var x=\"\\x00\"");
    assertPrint("var x ='\\u00003';", "var x=\"\\x003\"");
  }

  public void testOctalInString() {
    assertPrint("var x ='\\0';", "var x=\"\\x00\"");
    assertPrint("var x ='\\07';", "var x=\"\\u0007\"");

    // Octal 12 = Hex 0A = \n
    assertPrint("var x ='\\012';", "var x=\"\\n\"");

    // Octal 13 = Hex 0B = \v, but we print it as \x0B. See issue 601.
    assertPrint("var x ='\\013';", "var x=\"\\x0B\"");

    // Octal 34 = Hex 1C
    assertPrint("var x ='\\034';", "var x=\"\\u001c\"");

    // 8 and 9 are not octal digits
    assertPrint("var x ='\\08';", "var x=\"\\x008\"");
    assertPrint("var x ='\\09';", "var x=\"\\x009\"");

    // Only the first two digits are part of the octal literal.
    assertPrint("var x ='\\01234';", "var x=\"\\n34\"");
  }

  public void testOctalInStringNoLeadingZero() {
    assertPrint("var x ='\\7';", "var x=\"\\u0007\"");

    // Octal 12 = Hex 0A = \n
    assertPrint("var x ='\\12';", "var x=\"\\n\"");

    // Octal 13 = Hex 0B = \v, but we print it as \x0B. See issue 601.
    assertPrint("var x ='\\13';", "var x=\"\\x0B\"");

    // Octal 34 = Hex 1C
    assertPrint("var x ='\\34';", "var x=\"\\u001c\"");

    // Octal 240 = Hex A0
    assertPrint("var x ='\\240';", "var x=\"\\u00a0\"");

    // Only the first three digits are part of the octal literal.
    assertPrint("var x ='\\2400';", "var x=\"\\u00a00\"");

    // Only the first two digits are part of the octal literal because '8'
    // is not an octal digit.
    // Octal 67 = Hex 37 = "7"
    assertPrint("var x ='\\6789';", "var x=\"789\"");

    // 8 and 9 are not octal digits. '\' is ignored and the digit
    // is just a regular character.
    assertPrint("var x ='\\8';", "var x=\"8\"");
    assertPrint("var x ='\\9';", "var x=\"9\"");

    // Only the first three digits are part of the octal literal.
    // Octal 123 = Hex 53 = "S"
    assertPrint("var x ='\\1234';", "var x=\"S4\"");
  }

  public void testUnicode() {
    assertPrint("var x ='\\x0f';", "var x=\"\\u000f\"");
    assertPrint("var x ='\\x68';", "var x=\"h\"");
    assertPrint("var x ='\\x7f';", "var x=\"\\u007f\"");
  }

  // Separate from testNumericKeys() so we can set allowWarnings.
  public void testOctalNumericKey() {
    allowWarnings = true;
    languageMode = LanguageMode.ECMASCRIPT6;

    assertPrint("var x = {010: 1};", "var x={8:1}");
  }

  public void testNumericKeys() {
    assertPrint("var x = {'010': 1};", "var x={\"010\":1}");

    assertPrint("var x = {0x10: 1};", "var x={16:1}");
    assertPrint("var x = {'0x10': 1};", "var x={\"0x10\":1}");

    // I was surprised at this result too.
    assertPrint("var x = {.2: 1};", "var x={\"0.2\":1}");
    assertPrint("var x = {'.2': 1};", "var x={\".2\":1}");

    assertPrint("var x = {0.2: 1};", "var x={\"0.2\":1}");
    assertPrint("var x = {'0.2': 1};", "var x={\"0.2\":1}");
  }

  public void testIssue582() {
    assertPrint("var x = -0.0;", "var x=-0");
  }

  public void testIssue942() {
    assertPrint("var x = {0: 1};", "var x={0:1}");
  }

  public void testIssue601() {
    assertPrint("'\\v' == 'v'", "\"\\v\"==\"v\"");
    assertPrint("'\\u000B' == '\\v'", "\"\\x0B\"==\"\\v\"");
    assertPrint("'\\x0B' == '\\v'", "\"\\x0B\"==\"\\v\"");
  }

  public void testIssue620() {
    assertPrint("alert(/ / / / /);", "alert(/ // / /)");
    assertPrint("alert(/ // / /);", "alert(/ // / /)");
  }

  public void testIssue5746867() {
    assertPrint("var a = { '$\\\\' : 5 };", "var a={\"$\\\\\":5}");
  }

  public void testCommaSpacing() {
    assertPrint("var a = (b = 5, c = 5);",
        "var a=(b=5,c=5)");
    assertPrettyPrint("var a = (b = 5, c = 5);",
        "var a = (b = 5, c = 5);\n");
  }

  public void testManyCommas() {
    int numCommas = 10000;
    List<String> numbers = Lists.newArrayList("0", "1");
    Node current = new Node(Token.COMMA, Node.newNumber(0), Node.newNumber(1));
    for (int i = 2; i < numCommas; i++) {
      current = new Node(Token.COMMA, current);

      // 1000 is printed as 1E3, and screws up our test.
      int num = i % 1000;
      numbers.add(String.valueOf(num));
      current.addChildToBack(Node.newNumber(num));
    }

    String expected = Joiner.on(",").join(numbers);
    String actual = printNode(current).replace("\n", "");
    assertEquals(expected, actual);
  }

  public void testManyAdds() {
    int numAdds = 10000;
    List<String> numbers = Lists.newArrayList("0", "1");
    Node current = new Node(Token.ADD, Node.newNumber(0), Node.newNumber(1));
    for (int i = 2; i < numAdds; i++) {
      current = new Node(Token.ADD, current);

      // 1000 is printed as 1E3, and screws up our test.
      int num = i % 1000;
      numbers.add(String.valueOf(num));
      current.addChildToBack(Node.newNumber(num));
    }

    String expected = Joiner.on("+").join(numbers);
    String actual = printNode(current).replace("\n", "");
    assertEquals(expected, actual);
  }

  public void testMinusNegativeZero() {
    // Negative zero is weird, because we have to be able to distinguish
    // it from positive zero (there are some subtle differences in behavior).
    assertPrint("x- -0", "x- -0");
  }

  public void testStringEscapeSequences() {
    // From the SingleEscapeCharacter grammar production.
    assertPrintSame("var x=\"\\b\"");
    assertPrintSame("var x=\"\\f\"");
    assertPrintSame("var x=\"\\n\"");
    assertPrintSame("var x=\"\\r\"");
    assertPrintSame("var x=\"\\t\"");
    assertPrintSame("var x=\"\\v\"");
    assertPrint("var x=\"\\\"\"", "var x='\"'");
    assertPrint("var x=\"\\\'\"", "var x=\"'\"");

    // From the LineTerminator grammar
    assertPrint("var x=\"\\u000A\"", "var x=\"\\n\"");
    assertPrint("var x=\"\\u000D\"", "var x=\"\\r\"");
    assertPrintSame("var x=\"\\u2028\"");
    assertPrintSame("var x=\"\\u2029\"");

    // Now with regular expressions.
    assertPrintSame("var x=/\\b/");
    assertPrintSame("var x=/\\f/");
    assertPrintSame("var x=/\\n/");
    assertPrintSame("var x=/\\r/");
    assertPrintSame("var x=/\\t/");
    assertPrintSame("var x=/\\v/");
    assertPrintSame("var x=/\\u000A/");
    assertPrintSame("var x=/\\u000D/");
    assertPrintSame("var x=/\\u2028/");
    assertPrintSame("var x=/\\u2029/");
  }

  public void testKeywordProperties1() {
    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("x.foo=2");
    assertPrintSame("x.function=2");

    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintSame("x.foo=2");
  }

  public void testKeywordProperties1a() {
    languageMode = LanguageMode.ECMASCRIPT5;
    Node nodes = parse("x.function=2");
    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintNode("x[\"function\"]=2", nodes);
  }

  public void testKeywordProperties2() {
    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("x={foo:2}");
    assertPrintSame("x={function:2}");

    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintSame("x={foo:2}");
  }

  public void testKeywordProperties2a() {
    languageMode = LanguageMode.ECMASCRIPT5;
    Node nodes = parse("x={function:2}");
    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintNode("x={\"function\":2}", nodes);
  }

  public void testIssue1062() {
    assertPrintSame("3*(4%3*5)");
  }

  public void testPreserveTypeAnnotations() {
    preserveTypeAnnotations = true;
    assertPrintSame("/**@type {foo} */var bar");
    assertPrintSame(
        "function/** void */f(/** string */s,/** number */n){}");

    preserveTypeAnnotations = false;
    assertPrint("/** @type {foo} */\nvar bar;", "var bar");
  }

  public void testDefaultParameters() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("function f(a=0){}");
    assertPrintSame("function f(a,b=0){}");
  }

  public void testRestParameters() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("function f(...args){}");
    assertPrintSame("function f(first,...rest){}");
  }

  public void testDefaultParametersWithRestParameters() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("function f(first=0,...args){}");
    assertPrintSame("function f(first,second=0,...rest){}");
  }

  public void testSpreadExpression() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("f(...args)");
    assertPrintSame("f(...arrayOfArrays[0])");
    assertPrintSame("f(...[1,2,3])");
  }

  public void testClass() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("class C{}");
    assertPrintSame("(class C{})");
    assertPrintSame("class C extends D{}");
    assertPrintSame("class C{static member(){}}");
    assertPrintSame("class C{member(){}get f(){}}");
    assertPrintSame("var x=class C{}");
  }

  public void testClassPretty() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrettyPrint(
        "class C{}",
        "class C {\n}\n");
    // TODO(johnlenz): fix line breaks
    assertPrettyPrint(
        "class C{member(){}get f(){}}",
        "class C {\n" +
        "  member() {\n" +
        "  }get f() {\n" +
        "  }\n" +
        "}\n");
    assertPrettyPrint(
        "var x=class C{}",
        "var x = class C {\n};\n");
  }

  public void testSuper() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("class C extends foo(){}");
    assertPrintSame("class C extends m.foo(){}");
    assertPrintSame("class C extends D{member(){super.foo()}}");
  }

  public void testGeneratorYield1() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("function*f(){yield 1}");
    assertPrintSame("function*f(){yield 1?0:2}");
    assertPrintSame("function*f(){yield 1,0}");
    assertPrintSame("function*f(){1,yield 0}");
    assertPrintSame("function*f(){yield(a=0)}");
    assertPrintSame("function*f(){a=yield 0}");
    assertPrintSame("function*f(){(yield 1)+(yield 1)}");
  }

  public void testMemberGeneratorYield1() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("class C{*member(){(yield 1)+(yield 1)}}");
    assertPrintSame("var obj={*member(){(yield 1)+(yield 1)}}");
  }

  public void testArrowFunction() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrint("()=>1", "(()=>1)");
    assertPrint("()=>{}", "(()=>{})");
    assertPrint("a=>b", "((a)=>b)");
    assertPrint("(a,b)=>b", "((a,b)=>b)");
  }

  public void testDeclarations() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("let x");
    assertPrintSame("let x,y");
    assertPrintSame("let x=1");
    assertPrintSame("let x=1,y=2");
    assertPrintSame("if(a){let x}");

    assertPrintSame("const x=1");
    assertPrintSame("const x=1,y=2");
    assertPrintSame("if(a){const x=1}");

    assertPrintSame("function f(){}");
    assertPrintSame("if(a){function f(){}}");
    assertPrintSame("if(a)(function(){})");

    assertPrintSame("class f{}");
    assertPrintSame("if(a){class f{}}");
    assertPrintSame("if(a)(class{})");
  }

  public void testImports() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("import x from\"foo\"");
    assertPrintSame("import\"foo\"");
    assertPrintSame("import x,{a as b}from\"foo\"");
    assertPrintSame("import{a as b,c as d}from\"foo\"");
    assertPrintSame("import x,{a}from\"foo\"");
    assertPrintSame("import{a,c}from\"foo\"");
    assertPrintSame("import x,*as f from\"foo\"");
    assertPrintSame("import*as f from\"foo\"");
  }

  public void testExports() {
    languageMode = LanguageMode.ECMASCRIPT6;
    // export declarations
    assertPrintSame("export var x=1");
    assertPrintSame("export let x=1");
    assertPrintSame("export const x=1");
    assertPrintSame("export function f(){}");
    assertPrintSame("export class f{}");

    // export all from
    assertPrint("export * from 'a.b.c'", "export*from\"a.b.c\"");

    // from
    assertPrintSame("export{a}from\"a.b.c\"");
    assertPrintSame("export{a as x}from\"a.b.c\"");
    assertPrintSame("export{a,b}from\"a.b.c\"");
    assertPrintSame("export{a as x,b as y}from\"a.b.c\"");
    assertPrintSame("export{a}");
    assertPrintSame("export{a as x}");

    assertPrintSame("export{a,b}");
    assertPrintSame("export{a as x,b as y}");

    // export default
    assertPrintSame("export default x");
    assertPrintSame("export default 1");
  }

  public void testTemplateLiteral() {
    languageMode = LanguageMode.ECMASCRIPT6;
    assertPrintSame("`hello`");
    assertPrint("`hel\rlo`", "`hel\nlo`");
    assertPrint("`hel\r\nlo`", "`hel\nlo`");

    assertPrintSame("`hello${world}!`");
    assertPrintSame("`hello${world} ${name}!`");

    assertPrintSame("`hello${(function(){let x=3})()}`");
    assertPrintSame("(function(){})()`${(function(){})()}`");
    assertPrintSame("url`hello`");
    assertPrintSame("url(`hello`)");
  }
}
