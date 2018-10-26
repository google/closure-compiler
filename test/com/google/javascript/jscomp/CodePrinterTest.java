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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class CodePrinterTest extends CodePrinterTestBase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  @Test
  public void testExponentiationOperator() {
    languageMode = LanguageMode.ECMASCRIPT_2016;
    assertPrintSame("x**y");
    // Exponentiation is right associative
    assertPrint("x**(y**z)", "x**y**z");
    assertPrintSame("(x**y)**z");
    // parens are kept because ExponentiationExpression cannot expand to
    //     UnaryExpression ** ExponentiationExpression
    assertPrintSame("(-x)**y");
    // parens are kept because unary operators are higher precedence than '**'
    assertPrintSame("-(x**y)");
    // parens are not needed for a unary operator on the right operand
    assertPrint("x**(-y)", "x**-y");
    // NOTE: "-x**y" is a syntax error tested in ParserTest

    // ** has a higher precedence than /
    assertPrint("x/(y**z)", "x/y**z");
    assertPrintSame("(x/y)**z");
  }

  @Test
  public void testExponentiationAssignmentOperator() {
    languageMode = LanguageMode.ECMASCRIPT_2016;
    assertPrintSame("x**=y");
  }

  @Test
  public void testObjectLiteralWithSpread() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("({...{}})");
    assertPrintSame("({...x})");
    assertPrintSame("({...x,a:1})");
    assertPrintSame("({a:1,...x})");
    assertPrintSame("({a:1,...x,b:1})");
    assertPrintSame("({...x,...y})");
    assertPrintSame("({...x,...f()})");
    assertPrintSame("({...{...{}}})");
  }

  @Test
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

    // The code printer does not eliminate unnecessary blocks.
    assertPrint("var x = 10; { var y = 20; }", "var x=10;{var y=20}");

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

    // calling new on the result of a call
    assertPrintSame("new (a())");
    assertPrint("new (a())()", "new (a())");
    assertPrintSame("new (a.b())");
    assertPrint("new (a.b())()", "new (a.b())");


    // Operators: make sure we don't convert binary + and unary + into ++
    assertPrint("x + +y", "x+ +y");
    assertPrint("x - (-y)", "x- -y");
    assertPrint("x++ +y", "x++ +y");
    assertPrint("x-- -y", "x-- -y");
    assertPrint("x++ -y", "x++-y");

    // Label
    assertPrint("foo:for(;;){break foo;}", "foo:for(;;)break foo");
    assertPrint("foo:while(1){continue foo;}", "foo:while(1)continue foo");
    assertPrintSame("foo:;");
    assertPrint("foo: {}", "foo:;");

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

    assertPrintSame("var a=(b=c,d)");
    assertPrintSame("var a=(b[c]=d,e)");
    assertPrintSame("var a=(b[c]=d,e[f]=g,h)");

    assertPrint("var a = /** @type {?} */ (b=c,d)", "var a=(b=c,d)");
    assertPrint("var a = /** @type {?} */ (b[c]=d,e)", "var a=(b[c]=d,e)");
    assertPrint("var a = /** @type {?} */ (b[c]=d,e[f]=g,h)", "var a=(b[c]=d,e[f]=g,h)");

    // EMPTY nodes
    assertPrint("if (x){}", "if(x);");
    assertPrint("if(x);", "if(x);");
    assertPrint("if(x)if(y);", "if(x)if(y);");
    assertPrint("if(x){if(y);}", "if(x)if(y);");
    assertPrint("if(x){if(y){};;;}", "if(x)if(y);");
  }

  @Test
  public void testPrintNewVoid() {
    // Odd looking but valid. This, of course, will cause a runtime exception but
    // should not cause a parse error as "new void 0" would.
    assertPrintSame("new (void 0)");
  }

  @Test
  public void testPrintComma1() {
    Node node = IR.var(
        IR.name("a"),
            IR.comma(
                IR.comma(
                    IR.name("b"),
                    IR.name("c")),
                IR.name("d")));
    assertPrintNode("var a=(b,c,d)", node);
  }

  @Test
  public void testPrintComma2() {
    Node node = IR.var(
        IR.name("a"),
            IR.comma(
                IR.name("b"),
                IR.comma(
                    IR.name("c"),
                    IR.name("d"))));
    assertPrintNode("var a=(b,c,d)", node);
  }

  @Test
  public void testPrintCast1() {
    assertPrint("var x = /** @type {number} */ (0);", "var x=0");
    assertPrettyPrintSame("var x = /** @type {number} */ (0);\n");
  }

  @Test
  public void testPrintCast2() {
    assertPrint("var x = (2+3) * 4;", "var x=(2+3)*4");
    assertPrint("var x = /** @type {number} */ (2+3) * 4;", "var x=(2+3)*4");
    assertPrettyPrintSame("var x = (/** @type {number} */ (2 + 3)) * 4;\n");
  }

  @Test
  public void testPrintCast3() {
    assertPrint("var x = (2*3) + 4;", "var x=2*3+4");
    assertPrint("var x = /** @type {number} */ (2*3) + 4;", "var x=2*3+4");
    assertPrettyPrintSame("var x = /** @type {number} */ (2 * 3) + 4;\n");
  }

  @Test
  public void testLetConstInIf() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrint("if (true) { let x; };", "if(true){let x}");
    assertPrint("if (true) { const x = 0; };", "if(true){const x=0}");
  }

  @Test
  public void testPrintBlockScopedFunctions() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    // Safari 3 needs a "{" around a single function
    assertPrint("if (true) function foo(){return}",
        "if(true){function foo(){return}}");
    assertPrint("if(x){;;function y(){};;}", "if(x){function y(){}}");
  }

  @Test
  public void testPrintArrayPatternVar() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testPrintArrayPatternLet() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testPrintArrayPatternConst() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testPrintArrayPatternAssign() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testPrintArrayPatternWithInitializer() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("[x=1]=[]");
    assertPrintSame("[a,,c=2,,e]=[1,2,3,4,5]");
    assertPrintSame("[a=1,b=2,c=3]=foo()");
    assertPrintSame("[a=(1,2),b]=foo()");
    assertPrintSame("[a=[b=(1,2)]=bar(),c]=foo()");
  }

  @Test
  public void testPrintNestedArrayPattern() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("var [a,[b,c],d]=[1,[2,3],4]");
    assertPrintSame("var [[[[a]]]]=[[[[1]]]]");

    assertPrintSame("[a,[b,c],d]=[1,[2,3],4]");
    assertPrintSame("[[[[a]]]]=[[[[1]]]]");
  }

  @Test
  public void testPrettyPrintArrayPattern() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint("let [a,b,c]=foo();", "let [a, b, c] = foo();\n");
  }

  @Test
  public void testPrintObjectPatternVar() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("var {a}=foo()");
    assertPrintSame("var {a,b}=foo()");
    assertPrintSame("var {a:a,b:b}=foo()");
  }

  @Test
  public void testPrintObjectPatternLet() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("let {a}=foo()");
    assertPrintSame("let {a,b}=foo()");
    assertPrintSame("let {a:a,b:b}=foo()");
  }

  @Test
  public void testPrintObjectPatternConst() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("const {a}=foo()");
    assertPrintSame("const {a,b}=foo()");
    assertPrintSame("const {a:a,b:b}=foo()");
  }

  @Test
  public void testPrintObjectPatternAssign() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("({a}=foo())");
    assertPrintSame("({a,b}=foo())");
    assertPrintSame("({a:a,b:b}=foo())");
  }

  @Test
  public void testPrintNestedObjectPattern() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("({a:{b,c}}=foo())");
    assertPrintSame("({a:{b:{c:{d}}}}=foo())");
  }

  @Test
  public void testPrintObjectPatternInitializer() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("({a=1}=foo())");
    assertPrintSame("({a:{b=2}}=foo())");
    assertPrintSame("({a:b=2}=foo())");
    assertPrintSame("({a,b:{c=2}}=foo())");
    assertPrintSame("({a:{b=2},c}=foo())");
    assertPrintSame("({a=(1,2),b}=foo())");
    assertPrintSame("({a:b=(1,2),c}=foo())");
  }

  @Test
  public void testPrintObjectPatternWithRest() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("const {a,...rest}=foo()");
    assertPrintSame("var {a,...rest}=foo()");
    assertPrintSame("let {a,...rest}=foo()");
    assertPrintSame("({a,...rest}=foo())");
    assertPrintSame("({a=2,...rest}=foo())");
    assertPrintSame("({a:b=2,...rest}=foo())");
  }

  @Test
  public void testPrettyPrintObjectPattern() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint("const {a,b,c}=foo();", "const {a, b, c} = foo();\n");
  }

  @Test
  public void testPrintMixedDestructuring() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("({a:[b,c]}=foo())");
    assertPrintSame("[a,{b,c}]=foo()");
  }

  @Test
  public void testPrintDestructuringInParamList() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f([a]){}");
    assertPrintSame("function f([a,b]){}");
    assertPrintSame("function f([a,b]=c()){}");
    assertPrintSame("function f([a=(1,2),b=(3,4)]=c()){}");
    assertPrintSame("function f({a}){}");
    assertPrintSame("function f({a,b}){}");
    assertPrintSame("function f({a,b}=c()){}");
    assertPrintSame("function f([a,{b,c}]){}");
    assertPrintSame("function f({a,b:[c,d]}){}");
  }

  @Test
  public void testPrintDestructuringInRestParam() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f(...[a,b]){}");
    assertPrintSame("function f(...{length:num_params}){}");
  }

  @Test
  public void testDestructuringForInLoops() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("for({a}in b)c");
    assertPrintSame("for(var {a}in b)c");
    assertPrintSame("for(let {a}in b)c");
    assertPrintSame("for(const {a}in b)c");

    assertPrintSame("for({a:b}in c)d");
    assertPrintSame("for(var {a:b}in c)d");
    assertPrintSame("for(let {a:b}in c)d");
    assertPrintSame("for(const {a:b}in c)d");

    assertPrintSame("for([a]in b)c");
    assertPrintSame("for(var [a]in b)c");
    assertPrintSame("for(let [a]in b)c");
    assertPrintSame("for(const [a]in b)c");
  }

  @Test
  public void testDestructuringForOfLoops1() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("for({a}of b)c");
    assertPrintSame("for(var {a}of b)c");
    assertPrintSame("for(let {a}of b)c");
    assertPrintSame("for(const {a}of b)c");

    assertPrintSame("for({a:b}of c)d");
    assertPrintSame("for(var {a:b}of c)d");
    assertPrintSame("for(let {a:b}of c)d");
    assertPrintSame("for(const {a:b}of c)d");

    assertPrintSame("for([a]of b)c");
    assertPrintSame("for(var [a]of b)c");
    assertPrintSame("for(let [a]of b)c");
    assertPrintSame("for(const [a]of b)c");
  }

  @Test
  public void testDestructuringForOfLoops2() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    // The destructuring 'var' statement is a child of the for-of loop, but
    // not the first child.
    assertPrintSame("for(a of b)var {x}=y");
  }

  @Test
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

  @Test
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

  @Test
  public void testHtmlComments() {
    assertPrint("3< !(--x)", "3< !--x");
    assertPrint("while (x-- > 0) {}", "while(x-- >0);");
  }

  @Test
  public void testPrintArray() {
    assertPrint("[void 0, void 0]", "[void 0,void 0]");
    assertPrint("[undefined, undefined]", "[undefined,undefined]");
    assertPrint("[ , , , undefined]", "[,,,undefined]");
    assertPrint("[ , , , 0]", "[,,,0]");
  }

  @Test
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

  @Test
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
    assertPrint("var a={};function b_(p){ return p;};"
            + "for(var i=1,j=b_(\"length\" in a);;) {}",
        "var a={};function b_(p){return p}"
            + "for(var i=1,j=b_(\"length\"in a);;);");

    // Test we correctly handle an in operator in the test clause.
    assertPrint("var a={}; for (;(\"length\" in a);) {}",
        "var a={};for(;\"length\"in a;);");

    // Test we correctly handle an in operator inside a comma.
    assertPrintSame("for(x,(y in z);;)foo()");
    assertPrintSame("for(var x,w=(y in z);;)foo()");

    // And in operator inside a hook.
    assertPrintSame("for(a=c?0:(0 in d);;)foo()");

    // And inside an arrow function body
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrint(
        "var a={}; for(var i = () => (0 in a); i;) {}", "var a={};for(var i=()=>(0 in a);i;);");
    assertPrint(
        "var a={}; for(var i = () => ({} in a); i;) {}", "var a={};for(var i=()=>({}in a);i;);");
  }

  @Test
  public void testForOf() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("for(a of b)c");
    assertPrintSame("for(var a of b)c");
  }

  // In pretty-print mode, make sure there is a space before and after the 'of' in a for/of loop.
  @Test
  public void testForOfPretty() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrettyPrintSame("for ([x, y] of b) {\n  c;\n}\n");
    assertPrettyPrintSame("for (x of [[1, 2]]) {\n  c;\n}\n");
    assertPrettyPrintSame("for ([x, y] of [[1, 2]]) {\n  c;\n}\n");
  }

  @Test
  public void testForAwaitOf() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;

    assertPrintSame("for await(a of b)c");
    assertPrintSame("for await(var a of b)c");
  }

  // In pretty-print mode, make sure there is a space before and after the 'of' in a for/of loop.
  @Test
  public void testForAwaitOfPretty() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;

    assertPrettyPrintSame("for await ([x, y] of b) {\n  c;\n}\n");
    assertPrettyPrintSame("for await (x of [[1, 2]]) {\n  c;\n}\n");
    assertPrettyPrintSame("for await ([x, y] of [[1, 2]]) {\n  c;\n}\n");
  }

  @Test
  public void testLetFor() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("for(let a=0;a<5;a++)b");
    assertPrintSame("for(let a in b)c");
    assertPrintSame("for(let a of b)c");
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("for await(let a of b)c");
  }

  @Test
  public void testConstFor() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("for(const a=5;b<a;b++)c");
    assertPrintSame("for(const a in b)c");
    assertPrintSame("for(const a of b)c");

    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("for await(const a of b)c");
  }

  @Test
  public void testLiteralProperty() {
    assertPrint("(64).toString()", "(64).toString()");
  }

  // Make sure that the code generator doesn't associate an
  // else clause with the wrong if clause.
  @Test
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

  @Test
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
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(false);
                        options.setLineBreak(true);
                        options.setLineLengthThreshold(
                            CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
                      }
                    })))
        .isEqualTo(expected);
  }

  @Test
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
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(false);
                        options.setLineBreak(false);
                        options.setLineLengthThreshold(30);
                        options.setPreferLineBreakAtEndOfFile(false);
                      }
                    })))
        .isEqualTo(expectedWithoutBreakAtEnd);
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(false);
                        options.setLineBreak(false);
                        options.setLineLengthThreshold(30);
                        options.setPreferLineBreakAtEndOfFile(true);
                      }
                    })))
        .isEqualTo(expectedWithBreakAtEnd);
  }

  @Test
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
    assertPrettyPrint(
        "for (;;) alert();",
        "for (;;) {\n"
        + "  alert();\n"
        + "}\n");

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
        "for (;;) {\n"
        + "  alert();\n"
        + "}\n");
    assertPrettyPrint("for(;;) {}",
        "for (;;) {\n"
        + "}\n");
    assertPrettyPrint(
        "for(;;) { alert(); alert(); }",
        "for (;;) {\n"
        + "  alert();\n"
        + "  alert();\n"
        + "}\n");
    assertPrettyPrint(
        "for(var x=0;x<10;x++) { alert(); alert(); }",
        "for (var x = 0; x < 10; x++) {\n"
        + "  alert();\n"
        + "  alert();\n"
        + "}\n");

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
    assertPrettyPrint("myLabel: {}", "myLabel: {\n}\n");
    assertPrettyPrint("myLabel: ;", "myLabel: ;\n");

    // Don't move the label on a loop, because then break {label} and
    // continue {label} won't work.
    assertPrettyPrint(
        "myLabel: for(;;) continue myLabel;",
        "myLabel: for (;;) {\n"
        + "  continue myLabel;\n"
        + "}\n");

    assertPrettyPrint("var a;", "var a;\n");
    assertPrettyPrint("i--", "i--;\n");
    assertPrettyPrint("i++", "i++;\n");

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
        "throw \"foo\";\n");

    // Test that loops properly have spaces inserted.
    assertPrettyPrint("do{ alert(); } while(true);",
        "do {\n  alert();\n} while (true);\n");
    assertPrettyPrint("while(true) { alert(); }",
        "while (true) {\n  alert();\n}\n");
  }

  @Test
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
        "if (true) {\n"
        + "  f();\n"
        + "}\n"
        + "for (;;) {\n"
        + "  g();\n"
        + "}\n");
  }

  @Test
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

  @Test
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

  @Test
  public void testPrettyPrinter_arrow() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint("(a)=>123;", "(a) => 123;\n");
  }

  @Test
  public void testPrettyPrinter_defaultValue() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint("(a=1)=>123;", "(a = 1) => 123;\n");
    assertPrettyPrint("[a=(1,2)]=[];", "[a = (1, 2)] = [];\n");
  }

  // For https://github.com/google/closure-compiler/issues/782
  @Test
  public void testPrettyPrinter_spaceBeforeSingleQuote() {
    assertPrettyPrint("var f = function() { return 'hello'; };",
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
  @Test
  public void testPrettyPrinter_spaceBeforeUnaryOperators() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrettyPrint("var f = function() { return !b; };",
        "var f = function() {\n" +
            "  return !b;\n" +
            "};\n");
    assertPrettyPrint("var f = function*(){yield -b}",
        "var f = function*() {\n" +
            "  yield -b;\n" +
            "};\n");
    assertPrettyPrint("var f = function() { return +b; };",
        "var f = function() {\n" +
            "  return +b;\n" +
            "};\n");
    assertPrettyPrint("var f = function() { throw ~b; };",
        "var f = function() {\n" +
            "  throw ~b;\n" +
            "};\n");
    assertPrettyPrint("var f = function() { return ++b; };",
        "var f = function() {\n" +
            "  return ++b;\n" +
            "};\n");
    assertPrettyPrint("var f = function() { return --b; };",
        "var f = function() {\n" +
            "  return --b;\n" +
            "};\n");
  }

  @Test
  public void testPrettyPrinter_varLetConst() {
    assertPrettyPrint("var x=0;", "var x = 0;\n");

    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrettyPrint("const x=0;", "const x = 0;\n");
    assertPrettyPrint("let x=0;", "let x = 0;\n");
  }

  @Test
  public void testPrettyPrinter_number() {
    assertPrettyPrintSame("var x = 10;\n");
    assertPrettyPrintSame("var x = 1.;\n");
    assertPrettyPrint("var x = 0xFE;", "var x = 254;\n");
    assertPrettyPrintSame("var x = 1" + String.format("%0100d", 0) + ";\n"); // a googol
    assertPrettyPrintSame("f(10000);\n");
    assertPrettyPrintSame("var x = -10000;\n");
    assertPrettyPrintSame("var x = y - -10000;\n");
    assertPrettyPrintSame("f(-10000);\n");
    assertPrettyPrintSame("x < 2592000;\n");
    assertPrettyPrintSame("x < 1000.000;\n");
    assertPrettyPrintSame("x < 1000.912;\n");
    assertPrettyPrintSame("var x = 1E20;\n");
    assertPrettyPrintSame("var x = 1E1;\n");
    assertPrettyPrintSame("var x = void 0;\n");
    assertPrettyPrintSame("foo(-0);\n");
    assertPrettyPrint("var x = 4-1000;", "var x = 4 - 1000;\n");
  }

  @Test
  public void testTypeAnnotations() {
    assertTypeAnnotations(
        "/** @constructor */ function Foo(){}",
        "/**\n * @constructor\n */\nfunction Foo() {\n}\n");
  }

  @Test
  public void testNonNullTypes() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {!Foo} */",
            "Foo.prototype.f = function() { return new Foo; };"),
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " */",
            "function Foo() {\n}",
            "/**",
            " * @return {!Foo}",
            " */",
            "Foo.prototype.f = function() {",
            "  return new Foo;",
            "};\n"));
  }

  @Test
  public void testTypeAnnotationsTypeDef() {
    // TODO(johnlenz): It would be nice if there were some way to preserve
    // typedefs but currently they are resolved into the basic types in the
    // type registry.
    // NOTE(sdh): The type inferrence does not correctly remove null
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var goog = {};",
            "/** @const */ goog.java = {};",
            "/** @typedef {Array<number>} */ goog.java.Long;",
            "/** @param {!goog.java.Long} a*/",
            "function f(a){};"),
        LINE_JOINER.join(
            "/** @const */ var goog = {};",
            "/** @const */ goog.java = {};",
            "goog.java.Long;",
            "/**",
            " * @param {(Array<number>|null)} a",
            " * @return {undefined}",
            " */",
            "function f(a) {\n}\n"));
  }

  @Test
  public void testTypeAnnotationsAssign() {
    assertTypeAnnotations(
        "/** @constructor */ var Foo = function(){}",
        LINE_JOINER.join(
            "/**\n * @constructor\n */",
            "var Foo = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationsNamespace() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "var a = {};",
            "/** @constructor */ a.Foo = function(){}"),
        LINE_JOINER.join(
            "var a = {};",
            "/**\n * @constructor\n */",
            "a.Foo = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationsMemberSubclass() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/** @constructor */ a.Foo = function(){};",
            "/** @constructor \n @extends {a.Foo} */ a.Bar = function(){}"),
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/**\n * @constructor\n */",
            "a.Foo = function() {\n};",
            "/**\n * @extends {a.Foo}",
            " * @constructor\n */",
            "a.Bar = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationsInterface() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/** @interface */ a.Foo = function(){};",
            "/** @interface \n @extends {a.Foo} */ a.Bar = function(){}"),
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/**\n * @interface\n */",
            "a.Foo = function() {\n};",
            "/**\n * @extends {a.Foo}",
            " * @interface\n */",
            "a.Bar = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationsMultipleInterface() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/** @interface */ a.Foo1 = function(){};",
            "/** @interface */ a.Foo2 = function(){};",
            "/** @interface \n @extends {a.Foo1} \n @extends {a.Foo2} */",
            "a.Bar = function(){}"),
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/**\n * @interface\n */",
            "a.Foo1 = function() {\n};",
            "/**\n * @interface\n */",
            "a.Foo2 = function() {\n};",
            "/**\n * @extends {a.Foo1}",
            " * @extends {a.Foo2}",
            " * @interface\n */",
            "a.Bar = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationsMember() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "var a = {};",
            "/** @constructor */ a.Foo = function(){}",
            "/** @param {string} foo",
            "  * @return {number} */",
            "a.Foo.prototype.foo = function(foo) { return 3; };",
            "/** @type {!Array|undefined} */",
            "a.Foo.prototype.bar = [];"),
        LINE_JOINER.join(
            "var a = {};",
            "/**\n * @constructor\n */",
            "a.Foo = function() {\n};",
            "/**",
            " * @param {string} foo",
            " * @return {number}",
            " */",
            "a.Foo.prototype.foo = function(foo) {\n  return 3;\n};",
            "/** @type {!Array} */",
            "a.Foo.prototype.bar = [];\n"));
  }

  @Test
  public void testTypeAnnotationsMemberStub() {
    // TODO(blickly): Investigate why the method's type isn't preserved.
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @interface */ function I(){};",
            "/** @return {undefined} @param {number} x */ I.prototype.method;"),
        "/**\n * @interface\n */\nfunction I() {\n}\nI.prototype.method;\n");
  }

  @Test
  public void testTypeAnnotationsImplements() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/** @constructor */ a.Foo = function(){};",
            "/** @interface */ a.I = function(){};",
            "/** @record */ a.I2 = function(){};",
            "/** @record @extends {a.I2} */ a.I3 = function(){};",
            "/** @constructor \n @extends {a.Foo}",
            " * @implements {a.I} \n @implements {a.I2}",
            " */ a.Bar = function(){}"),
        LINE_JOINER.join(
            "/** @const */ var a = {};",
            "/**\n * @constructor\n */",
            "a.Foo = function() {\n};",
            "/**\n * @interface\n */",
            "a.I = function() {\n};",
            "/**\n * @record\n */",
            "a.I2 = function() {\n};",
            "/**\n * @extends {a.I2}",
            " * @record\n */",
            "a.I3 = function() {\n};",
            "/**\n * @extends {a.Foo}",
            " * @implements {a.I}",
            " * @implements {a.I2}",
            " * @constructor\n */",
            "a.Bar = function() {\n};\n"));
  }

  @Test
  public void testTypeAnnotationClassImplements() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @interface */ class Foo {}", //
            "/** @implements {Foo} */ class Bar {}"),
        lines(
            // TODO(b/111818228): Should print annotations
            // "/**\n * @interface\n */",
            "class Foo {\n}",
            // "/**\n * @implements {Foo}\n */",
            "class Bar {\n}\n"));
  }

  @Test
  public void testTypeAnnotationClassMember() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "class Foo {", //
            "  /** @return {number} */ method(/** string */ arg) {}",
            "}"),
        lines(
            "class Foo {",
            // TODO(b/111818228): Should print annotations
            // "  /**\n * @param {string} arg\n * @return {number}\n */",
            "  method(arg) {\n  }",
            "}\n"));
  }

  @Test
  public void testRestParameter() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {...string} args */", //
            "function f(...args) {}"),
        lines(
            "/**\n * @param {...string} args\n * @return {undefined}\n */",
            "function f(...args) {\n}\n"));
  }

  @Test
  public void testDefaultParameter() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {string=} msg */", //
            "function f(msg = 'hi') {}"),
        lines(
            "/**\n * @param {string=} msg\n * @return {undefined}\n */",
            "function f(msg = \"hi\") {\n}\n"));
  }

  @Test
  public void testObjectDestructuringParameter() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {{a: number, b: number}} ignoredName */", //
            "function f({a, b}) {}"),
        lines(
            "/**",
            " * @param {{a: number, b: number}} p0", // old JSDoc name is ignored
            " * @return {undefined}",
            " */",
            "function f({a, b}) {", // whitespace in output must match
            "}",
            ""));
  }

  @Test
  public void testObjectDestructuringParameterWithDefault() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {{a: number, b: number}=} ignoredName */", //
            "function f({a, b} = {a: 1, b: 2}) {}"),
        lines(
            "/**",
            " * @param {{a: number, b: number}=} p0", // old JSDoc name is ignored
            " * @return {undefined}",
            " */",
            "function f({a, b} = {a:1, b:2}) {", // whitespace in output must match
            "}",
            ""));
  }

  @Test
  public void testArrayDestructuringParameter() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {!Iterable<number>} ignoredName */", //
            "function f([a, b]) {}"),
        lines(
            "/**",
            " * @param {!Iterable<number>} p0", // old JSDoc name is ignored
            " * @return {undefined}",
            " */",
            "function f([a, b]) {", // whitespace in output must match
            "}",
            ""));
  }

  @Test
  public void testArrayDestructuringParameterWithDefault() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertTypeAnnotations(
        lines(
            "/** @param {!Iterable<number>=} ignoredName */", //
            "function f([a, b] = [1, 2]) {}"),
        lines(
            "/**",
            " * @param {!Iterable<number>=} p0", // old JSDoc name is ignored
            " * @return {undefined}",
            " */",
            "function f([a, b] = [1, 2]) {", // whitespace in output must match
            "}",
            ""));
  }

  @Test
  public void testU2UFunctionTypeAnnotation1() {
    assertTypeAnnotations(
        "/** @type {!Function} */ var x = function() {}",
        "/** @type {!Function} */\nvar x = function() {\n};\n");
  }

  @Test
  public void testU2UFunctionTypeAnnotation2() {
    // TODO(johnlenz): we currently report the type of the RHS which is not
    // correct, we should export the type of the LHS.
    assertTypeAnnotations(
        "/** @type {Function} */ var x = function() {}",
        "/** @type {!Function} */\nvar x = function() {\n};\n");
  }

  @Test
  public void testEmitUnknownParamTypesAsAllType() {
    // x is unused, so NTI infers that x can be omitted.
    assertTypeAnnotations(
        "var a = function(x) {}",
        LINE_JOINER.join(
            "/**",
            " * @param {?} x",
            " * @return {undefined}",
            " */",
            "var a = function(x) {\n};\n"));
  }

  @Test
  public void testOptionalTypesAnnotation() {
    assertTypeAnnotations(
        "/** @param {string=} x */ var a = function(x) {}",
        LINE_JOINER.join(
            "/**",
            " * @param {string=} x",
            " * @return {undefined}",
            " */",
            "var a = function(x) {\n};\n"));
  }

  @Test
  public void testOptionalTypesAnnotation2() {
    assertTypeAnnotations(
        "/** @param {undefined=} x */ var a = function(x) {}",
        LINE_JOINER.join(
            "/**",
            " * @param {undefined=} x",
            " * @return {undefined}",
            " */",
            "var a = function(x) {\n};\n"));
  }

  @Test
  public void testVariableArgumentsTypesAnnotation() {
    assertTypeAnnotations(
        "/** @param {...string} x */ var a = function(x) {}",
        LINE_JOINER.join(
            "/**",
            " * @param {...string} x",
            " * @return {undefined}",
            " */",
            "var a = function(x) {\n};\n"));
  }

  @Test
  public void testTempConstructor() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "var x = function() {",
            "  /** @constructor */ function t1() {}",
            "  /** @constructor */ function t2() {}",
            "  t1.prototype = t2.prototype",
            "}"),
        LINE_JOINER.join(
            "/**\n * @return {undefined}\n */",
            "var x = function() {",
            "  /**\n * @constructor\n */",
            "function t1() {\n  }",
            "  /**\n * @constructor\n */",
            "function t2() {\n  }",
            "  t1.prototype = t2.prototype;",
            "};\n"));
  }

  @Test
  public void testEnumAnnotation1() {
    assertTypeAnnotations(
        "/** @enum {string} */ var Enum = {FOO: 'x', BAR: 'y'};",
        "/** @enum {string} */\nvar Enum = {FOO:\"x\", BAR:\"y\"};\n");
  }

  @Test
  public void testEnumAnnotation2() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var goog = goog || {};",
            "/** @enum {string} */ goog.Enum = {FOO: 'x', BAR: 'y'};",
            "/** @const */ goog.Enum2 = goog.x ? {} : goog.Enum;"),
        LINE_JOINER.join(
            "/** @const */ var goog = goog || {};",
            "/** @enum {string} */\ngoog.Enum = {FOO:\"x\", BAR:\"y\"};",
            "/** @type {(!Object|{})} */\ngoog.Enum2 = goog.x ? {} : goog.Enum;\n"));
  }

  @Test
  public void testEnumAnnotation3() {
    assertTypeAnnotations(
        "/** @enum {!Object} */ var Enum = {FOO: {}};",
        "/** @enum {!Object} */\nvar Enum = {FOO:{}};\n");
  }

  @Test
  public void testEnumAnnotation4() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @enum {number} */ var E = {A:1, B:2};",
            "function f(/** !E */ x) { return x; }"),
        LINE_JOINER.join(
            "/** @enum {number} */",
            "var E = {A:1, B:2};",
            "/**",
            " * @param {number} x",
            " * @return {?}",
            " */",
            "function f(x) {",
            "  return x;",
            "}",
            ""));
  }

  @Test
  public void testClosureLibraryTypeAnnotationExamples() {
    assertTypeAnnotations(
        LINE_JOINER.join(
            "/** @const */ var goog = goog || {};",
            "/** @param {Object} obj */goog.removeUid = function(obj) {};",
            "/** @param {Object} obj The object to remove the field from. */",
            "goog.removeHashCode = goog.removeUid;"),
        LINE_JOINER.join(
            "/** @const */ var goog = goog || {};",
            "/**",
            " * @param {(Object|null)} obj",
            " * @return {undefined}",
            " */",
            "goog.removeUid = function(obj) {",
            "};",
            "/**",
            " * @param {(Object|null)} p0",
            " * @return {undefined}",
            " */",
            "goog.removeHashCode = goog.removeUid;\n"));
  }

  @Test
  public void testFunctionTypeAnnotation() {
    assertTypeAnnotations(
        "/**\n * @param {{foo:number}} arg\n */\nfunction f(arg) {}",
        "/**\n * @param {{foo: number}} arg\n * @return {undefined}\n */\nfunction f(arg) {\n}\n");
    assertTypeAnnotations(
        "/**\n * @param {number} arg\n */\nfunction f(arg) {}",
        "/**\n * @param {number} arg\n * @return {undefined}\n */\nfunction f(arg) {\n}\n");
    assertTypeAnnotations(
        "/**\n * @param {!Array<string>} arg\n */\nfunction f(arg) {}",
        "/**\n * @param {!Array<string>} arg\n * @return {undefined}\n */\nfunction f(arg) {\n}\n");
  }

  @Test
  public void testFunctionWithThisTypeAnnotation() {
    assertTypeAnnotations(
        "/**\n * @this {{foo:number}}\n */\nfunction foo() {}",
        "/**\n * @return {undefined}\n * @this {{foo: number}}\n */\nfunction foo() {\n}\n");
    assertTypeAnnotations(
        "/**\n * @this {!Array<string>}\n */\nfunction foo() {}",
        "/**\n * @return {undefined}\n * @this {!Array<string>}\n */\nfunction foo() {\n}\n");
  }

  @Test
  public void testReturnWithTypeAnnotation() {
    preserveTypeAnnotations = true;
    assertPrettyPrint(
        "function f() { return (/** @return {number} */ function() { return 42; }); }",
        LINE_JOINER.join(
            "function f() {",
            "  return (/**",
            " @return {number}",
            " */",
            "function() {",
            "    return 42;",
            "  });",
            "}",
            ""));
  }

  @Test
  public void testDeprecatedAnnotationIncludesNewline() {
    String js =
        LINE_JOINER.join(
            "/**",
            " @type {number}",
            " @deprecated See {@link replacementClass} for more details.",
            " */",
            "var x;",
            "");

    assertPrettyPrint(js, js);
  }

  private void assertPrettyPrintSame(String js) {
    assertPrettyPrint(js, js);
  }

  private void assertPrettyPrint(String js, String expected) {
    assertPrettyPrint(js, expected, new CompilerOptionBuilder() {
      @Override void setOptions(CompilerOptions options) { /* no-op */ }
    });
  }

  private void assertPrettyPrint(String js, String expected,
                                 final CompilerOptionBuilder optionBuilder) {
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(true);
                        options.setPreserveTypeAnnotations(true);
                        options.setLineBreak(false);
                        options.setLineLengthThreshold(
                            CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
                        optionBuilder.setOptions(options);
                      }
                    })))
        .isEqualTo(expected);
  }

  private void assertTypeAnnotations(String js, String expected) {
    String actual =
        new CodePrinter.Builder(parse(js, /* typeChecked= */ true))
            .setCompilerOptions(
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(true);
                        options.setLineBreak(false);
                        options.setLineLengthThreshold(
                            CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
                      }
                    }))
            .setOutputTypes(true)
            .setTypeRegistry(lastCompiler.getTypeRegistry())
            .build();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testSubtraction() {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("x - -4");
    assertThat(compiler.getErrorCount()).isEqualTo(0);

    assertThat(printNode(n)).isEqualTo("x- -4");
  }

  @Test
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

  @Test
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
    assertThat(
            parsePrint(
                js,
                newCompilerOptions(
                    new CompilerOptionBuilder() {
                      @Override
                      void setOptions(CompilerOptions options) {
                        options.setPrettyPrint(false);
                        options.setLineBreak(true);
                        options.setLineLengthThreshold(10);
                      }
                    })))
        .isEqualTo(expected);
  }

  @Test
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
    assertWithMessage(
            "\nExpected: "
                + compiler.toSource(parse1)
                + "\nResult: "
                + compiler.toSource(parse2)
                + "\n"
                + explanation)
        .that(explanation)
        .isNull();
  }

  @Test
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

  @Test
  public void testFunctionSafariCompatibility() {
    // Functions within IFs cause syntax errors on Safari.
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrint("function f(){if(e1){function goo(){return true}}else foo()}",
        "function f(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("function f(){if(e1)function goo(){return true}else foo()}",
        "function f(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("if(e1){function goo(){return true}}",
        "if(e1){function goo(){return true}}");

    assertPrint("if(e1)function goo(){return true}",
        "if(e1){function goo(){return true}}");
  }

  @Test
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

  @Test
  public void testBiggerThanMaxLongNumericLiterals() {
    // Since ECMAScript implements IEEE 754 "round to nearest, ties to even",
    // any literal in the range [0x7ffffffffffffe00,0x8000000000000400] will
    // round to the same value, namely 2^63. The fact that we print this as
    // 2^63-1 doesn't matter, since it must be rounded back to 2^63 at runtime.
    // See:
    //   http://www.ecma-international.org/ecma-262/5.1/#sec-8.5
    assertPrint("9223372036854775808", "0x7fffffffffffffff");
    assertPrint("0x8000000000000000", "0x7fffffffffffffff");
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testDirectEval() {
    assertPrint("eval('1');", "eval(\"1\")");
  }

  @Test
  public void testIndirectEval() {
    Node n = parse("eval('1');");
    assertPrintNode("eval(\"1\")", n);
    n.getFirstFirstChild().getFirstChild().putBooleanProp(
        Node.DIRECT_EVAL, false);
    assertPrintNode("(0,eval)(\"1\")", n);
  }

  @Test
  public void testFreeCall1() {
    assertPrint("foo(a);", "foo(a)");
    assertPrint("x.foo(a);", "x.foo(a)");
  }

  @Test
  public void testFreeCall2() {
    Node n = parse("foo(a);");
    assertPrintNode("foo(a)", n);
    Node call = n.getFirstFirstChild();
    assertThat(call.isCall()).isTrue();
    call.putBooleanProp(Node.FREE_CALL, true);
    assertPrintNode("foo(a)", n);
  }

  @Test
  public void testFreeCall3() {
    Node n = parse("x.foo(a);");
    assertPrintNode("x.foo(a)", n);
    Node call = n.getFirstFirstChild();
    assertThat(call.isCall()).isTrue();
    call.putBooleanProp(Node.FREE_CALL, true);
    assertPrintNode("(0,x.foo)(a)", n);
  }

  @Test
  public void testPrintScript() {
    // Verify that SCRIPT nodes not marked as synthetic are printed as
    // blocks.
    Node ast = new Node(Token.SCRIPT,
        new Node(Token.EXPR_RESULT, Node.newString("f")),
        new Node(Token.EXPR_RESULT, Node.newString("g")));
    String result = new CodePrinter.Builder(ast).setPrettyPrint(true).build();
    assertThat(result).isEqualTo("\"f\";\n\"g\";\n");
  }

  @Test
  public void testObjectLit() {
    assertPrint("({x:1})", "({x:1})");
    assertPrint("var x=({x:1})", "var x={x:1}");
    assertPrint("var x={'x':1}", "var x={\"x\":1}");
    assertPrint("var x={1:1}", "var x={1:1}");
    assertPrint("({},42)+0", "({},42)+0");
  }

  @Test
  public void testObjectLit2() {
    assertPrint("var x={1:1}", "var x={1:1}");
    assertPrint("var x={'1':1}", "var x={1:1}");
    assertPrint("var x={'1.0':1}", "var x={\"1.0\":1}");
    assertPrint("var x={1.5:1}", "var x={\"1.5\":1}");
  }

  @Test
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

  @Test
  public void testObjectLit4() {
    // More than 128 bits.
    assertPrint(
        "var x={\"123456789012345671234567890123456712345678901234567\":1}",
        "var x={\"123456789012345671234567890123456712345678901234567\":1}");
  }

  @Test
  public void testExtendedObjectLit() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("var a={b}");
    assertPrintSame("var a={b,c}");
    assertPrintSame("var a={b,c:d,e}");
    assertPrintSame("var a={b,c(){},d,e:f}");
  }

  @Test
  public void testComputedProperties() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("var a={[b]:c}");
    assertPrintSame("var a={[b+3]:c}");

    assertPrintSame("var a={[b](){}}");
    assertPrintSame("var a={[b](){alert(foo)}}");
    assertPrintSame("var a={*[b](){yield\"foo\"}}");
    assertPrintSame("var a={[b]:()=>c}");

    assertPrintSame("var a={get [b](){return null}}");
    assertPrintSame("var a={set [b](val){window.b=val}}");
  }

  @Test
  public void testComputedPropertiesClassMethods() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("class C{[m](){}}");

    assertPrintSame("class C{[\"foo\"+bar](){alert(1)}}");
  }

  @Test
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

  @Test
  public void testGetterInEs3() {
    // Getters and setters and not supported in ES3 but if someone sets the
    // the ES3 output mode on an AST containing them we still produce them.
    languageMode = LanguageMode.ECMASCRIPT3;

    Node getter = Node.newString(Token.GETTER_DEF, "f");
    getter.addChildToBack(NodeUtil.emptyFunction());
    assertPrintNode("({get f(){}})",
        IR.exprResult(IR.objectlit(getter)));
  }

  @Test
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

  @Test
  public void testSetterInEs3() {
    // Getters and setters and not supported in ES3 but if someone sets the
    // the ES3 output mode on an AST containing them we still produce them.
    languageMode = LanguageMode.ECMASCRIPT3;

    Node getter = Node.newString(Token.SETTER_DEF, "f");
    getter.addChildToBack(IR.function(IR.name(""), IR.paramList(IR.name("a")), IR.block()));
    assertPrintNode("({set f(a){}})",
        IR.exprResult(IR.objectlit(getter)));
  }

  @Test
  public void testNegCollapse() {
    // Collapse the negative symbol on numbers at generation time,
    // to match the Rhino behavior.
    assertPrint("var x = - - 2;", "var x=2");
    assertPrint("var x = - (2);", "var x=-2");
  }

  private CodePrinter.Builder defaultBuilder(Node jsRoot) {
    return new CodePrinter.Builder(jsRoot)
        .setCompilerOptions(newCompilerOptions(new CompilerOptionBuilder() {
          @Override
          void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineBreak(false);
            options.setLineLengthThreshold(0);
          }
        }))
        .setOutputTypes(false)
        .setTypeRegistry(lastCompiler.getTypeRegistry());
  }

  @Test
  public void testStrict() {
    String result =
        defaultBuilder(parse("var x", /* typeChecked= */ true)).setTagAsStrict(true).build();
    assertThat(result).isEqualTo("'use strict';var x");
  }

  @Test
  public void testStrictPretty() {
    String result =
        defaultBuilder(parse("var x", /* typeChecked= */ true))
            .setTagAsStrict(true)
            .setPrettyPrint(true)
            .build();
    assertThat(result).isEqualTo("'use strict';\nvar x;\n");
  }

  @Test
  public void testIjs() {
    String result =
        defaultBuilder(parse("var x", /* typeChecked= */ true))
            .setTagAsTypeSummary(true)
            .build();
    assertThat(result).isEqualTo("/** @fileoverview @typeSummary */\nvar x");
  }

  @Test
  public void testArrayLiteral() {
    assertPrint("var x = [,];", "var x=[,]");
    assertPrint("var x = [,,];", "var x=[,,]");
    assertPrint("var x = [,s,,];", "var x=[,s,,]");
    assertPrint("var x = [,s];", "var x=[,s]");
    assertPrint("var x = [s,];", "var x=[s]");
  }

  @Test
  public void testZero() {
    assertPrint("var x ='\\0';", "var x=\"\\x00\"");
    assertPrint("var x ='\\x00';", "var x=\"\\x00\"");
    assertPrint("var x ='\\u0000';", "var x=\"\\x00\"");
    assertPrint("var x ='\\u00003';", "var x=\"\\x003\"");
  }

  @Test
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

  @Test
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

  @Test
  public void testUnicode() {
    assertPrint("var x ='\\x0f';", "var x=\"\\u000f\"");
    assertPrint("var x ='\\x68';", "var x=\"h\"");
    assertPrint("var x ='\\x7f';", "var x=\"\\u007f\"");
  }

  // Separate from testNumericKeys() so we can set allowWarnings.
  @Test
  public void testOctalNumericKey() {
    allowWarnings = true;
    languageMode = LanguageMode.ECMASCRIPT5;

    assertPrint("var x = {010: 1};", "var x={8:1}");
  }

  @Test
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

  @Test
  public void testIssue582() {
    assertPrint("var x = -0.0;", "var x=-0");
  }

  @Test
  public void testIssue942() {
    assertPrint("var x = {0: 1};", "var x={0:1}");
  }

  @Test
  public void testIssue601() {
    assertPrint("'\\v' == 'v'", "\"\\v\"==\"v\"");
    assertPrint("'\\u000B' == '\\v'", "\"\\x0B\"==\"\\v\"");
    assertPrint("'\\x0B' == '\\v'", "\"\\x0B\"==\"\\v\"");
  }

  @Test
  public void testIssue620() {
    assertPrint("alert(/ / / / /);", "alert(/ // / /)");
    assertPrint("alert(/ // / /);", "alert(/ // / /)");
  }

  @Test
  public void testIssue5746867() {
    assertPrint("var a = { '$\\\\' : 5 };", "var a={\"$\\\\\":5}");
  }

  @Test
  public void testCommaSpacing() {
    assertPrint("var a = (b = 5, c = 5);",
        "var a=(b=5,c=5)");
    assertPrettyPrint("var a = (b = 5, c = 5);",
        "var a = (b = 5, c = 5);\n");
  }

  @Test
  public void testManyCommas() {
    int numCommas = 10000;
    List<String> numbers = new ArrayList<>();
    numbers.add("0");
    numbers.add("1");
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
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testManyAdds() {
    int numAdds = 10000;
    List<String> numbers = new ArrayList<>();
    numbers.add("0");
    numbers.add("1");
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
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testMinusNegativeZero() {
    // Negative zero is weird, because we have to be able to distinguish
    // it from positive zero (there are some subtle differences in behavior).
    assertPrint("x- -0", "x- -0");
  }

  @Test
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

  @Test
  public void testRegexp_escape() {
    assertPrintSame("/\\bword\\b/");
    assertPrintSame("/Java\\BScript/");
    assertPrintSame("/\\ca/");
    assertPrintSame("/\\cb/");
    assertPrintSame("/\\cc/");
    assertPrintSame("/\\cA/");
    assertPrintSame("/\\cB/");
    assertPrintSame("/\\cC/");
    assertPrintSame("/\\d/");
    assertPrintSame("/\\D/");
    assertPrintSame("/\\0/");
    assertPrintSame("/\\\\/");
    assertPrintSame("/(.)\\1/");
  }

  @Test
  public void testRegexp_unnecessaryEscape() {
    assertPrint("/\\a/", "/a/");
    assertPrint("/\\e/", "/e/");
    assertPrint("/\\g/", "/g/");
    assertPrint("/\\h/", "/h/");
    assertPrint("/\\i/", "/i/");
    assertPrint("/\\¡/", "/\\u00a1/");
  }

  @Test
  public void testKeywordProperties1() {
    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("x.foo=2");
    assertPrintSame("x.function=2");

    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintSame("x.foo=2");
  }

  @Test
  public void testKeywordProperties1a() {
    languageMode = LanguageMode.ECMASCRIPT5;
    Node nodes = parse("x.function=2");
    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintNode("x[\"function\"]=2", nodes);
  }

  @Test
  public void testKeywordProperties2() {
    languageMode = LanguageMode.ECMASCRIPT5;
    assertPrintSame("x={foo:2}");
    assertPrintSame("x={function:2}");

    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintSame("x={foo:2}");
  }

  @Test
  public void testKeywordProperties2a() {
    languageMode = LanguageMode.ECMASCRIPT5;
    Node nodes = parse("x={function:2}");
    languageMode = LanguageMode.ECMASCRIPT3;
    assertPrintNode("x={\"function\":2}", nodes);
  }

  @Test
  public void testIssue1062() {
    assertPrintSame("3*(4%3*5)");
  }

  @Test
  public void testPreserveTypeAnnotations() {
    preserveTypeAnnotations = true;
    assertPrintSame("/** @type {foo} */ var bar");
    assertPrintSame("function/** void */ f(/** string */ s,/** number */ n){}");

    preserveTypeAnnotations = false;
    assertPrint("/** @type {foo} */ var bar;", "var bar");
  }

  @Test
  public void testPreserveTypeAnnotations2() {
    preserveTypeAnnotations = true;

    assertPrintSame("/** @const */ var ns={}");

    assertPrintSame(
        LINE_JOINER.join(
            "/**",
            " @const",
            " @suppress {const,duplicate}",
            " */",
            "var ns={}"));
  }

  @Test
  public void testDefaultParameters() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f(a=0){}");
    assertPrintSame("function f(a,b=0){}");
    assertPrintSame("function f(a=(1,2),b){}");
    assertPrintSame("function f(a,b=(1,2)){}");
  }

  @Test
  public void testRestParameters() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f(...args){}");
    assertPrintSame("function f(first,...rest){}");
  }

  @Test
  public void testDefaultParametersWithRestParameters() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f(first=0,...args){}");
    assertPrintSame("function f(first,second=0,...rest){}");
  }

  @Test
  public void testSpreadExpression() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("f(...args)");
    assertPrintSame("f(...arrayOfArrays[0])");
    assertPrintSame("f(...[1,2,3])");
  }

  @Test
  public void testClass() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("class C{}");
    assertPrintSame("(class C{})");
    assertPrintSame("class C extends D{}");
    assertPrintSame("class C{static member(){}}");
    assertPrintSame("class C{member(){}get f(){}}");
    assertPrintSame("var x=class C{}");
  }

  @Test
  public void testClassComputedProperties() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    assertPrintSame("class C{[x](){}}");
    assertPrintSame("class C{get [x](){}}");
    assertPrintSame("class C{set [x](val){}}");

    assertPrintSame("class C{static [x](){}}");
    assertPrintSame("class C{static get [x](){}}");
    assertPrintSame("class C{static set [x](val){}}");
  }

  @Test
  public void testClassPretty() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint(
        "class C{}",
        "class C {\n}\n");
    assertPrettyPrint(
        "class C{member(){}get f(){}}",
        "class C {\n" +
        "  member() {\n" +
        "  }\n" +
        "  get f() {\n" +
        "  }\n" +
        "}\n");
    assertPrettyPrint(
        "var x=class C{}",
        "var x = class C {\n};\n");
  }

  @Test
  public void testSuper() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("class C extends foo(){}");
    assertPrintSame("class C extends m.foo(){}");
    assertPrintSame("class C extends D{member(){super.foo()}}");
  }

  @Test
  public void testNewTarget() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function f(){new.target}");
    assertPrint("function f() {\nnew\n.\ntarget;\n}", "function f(){new.target}");
  }

  @Test
  public void testGeneratorYield() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("function*f(){yield 1}");
    assertPrintSame("function*f(){yield}");
    assertPrintSame("function*f(){yield 1?0:2}");
    assertPrintSame("function*f(){yield 1,0}");
    assertPrintSame("function*f(){1,yield 0}");
    assertPrintSame("function*f(){yield(a=0)}");
    assertPrintSame("function*f(){a=yield 0}");
    assertPrintSame("function*f(){(yield 1)+(yield 1)}");
  }

  @Test
  public void testGeneratorYieldPretty() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint(
        "function *f() {yield 1}",
        LINE_JOINER.join(
            "function* f() {",
            "  yield 1;",
            "}",
            ""));

    assertPrettyPrint(
        "function *f() {yield}",
        LINE_JOINER.join(
            "function* f() {",
            "  yield;",
            "}",
            ""));
  }

  @Test
  public void testMemberGeneratorYield1() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("class C{*member(){(yield 1)+(yield 1)}}");
    assertPrintSame("var obj={*member(){(yield 1)+(yield 1)}}");
  }

  @Test
  public void testArrowFunction() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("()=>1");
    assertPrint("(()=>1)", "()=>1");
    assertPrintSame("()=>{}");
    assertPrint("a=>b", "(a)=>b");
    assertPrint("(a=>b)(1)", "((a)=>b)(1)");
    assertPrintSame("var z={x:(a)=>1}");
    assertPrint("(a,b)=>b", "(a,b)=>b");
    assertPrintSame("()=>(a,b)");
    assertPrint("(()=>a),b", "()=>a,b");
    assertPrint("()=>(a=b)", "()=>a=b");
    assertPrintSame("[1,2].forEach((x)=>y)");
    assertPrintSame("()=>({a:1})");
    assertPrintSame("()=>{return 1}");
  }

  @Test
  public void testAsyncFunction() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("async function f(){}");
    assertPrintSame("let f=async function f(){}");
    assertPrintSame("let f=async function(){}");
    // implicit semicolon prevents async being treated as a keyword
    assertPrint("async\nfunction f(){}", "async;function f(){}");
    assertPrint("let f=async\nfunction f(){}", "let f=async;function f(){}");
  }

  @Test
  public void testAsyncGeneratorFunction() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("async function*f(){}");
    assertPrintSame("let f=async function*f(){}");
    assertPrintSame("let f=async function*(){}");
    // implicit semicolon prevents async being treated as a keyword
    assertPrint("async\nfunction*f(){}", "async;function*f(){}");
    assertPrint("let f=async\nfunction*f(){}", "let f=async;function*f(){}");
  }

  @Test
  public void testAsyncArrowFunction() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("async()=>1");
    // implicit semicolon prevents async being treated as a keyword
    assertPrint("f=async\n()=>1", "f=async;()=>1");
  }

  @Test
  public void testAsyncMethod() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("o={async m(){}}");
    assertPrintSame("o={async[a+b](){}}");
    assertPrintSame("class C{async m(){}}");
    assertPrintSame("class C{async[a+b](){}}");
    assertPrintSame("class C{static async m(){}}");
    assertPrintSame("class C{static async[a+b](){}}");
  }

  @Test
  public void testAsyncGeneratorMethod() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("o={async *m(){}}");
    assertPrintSame("o={async*[a+b](){}}");
    assertPrintSame("class C{async *m(){}}");
    assertPrintSame("class C{async*[a+b](){}}");
    assertPrintSame("class C{static async *m(){}}");
    assertPrintSame("class C{static async*[a+b](){}}");
  }

  @Test
  public void testAwaitExpression() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    assertPrintSame("async function f(promise){return await promise}");
    assertPrintSame("pwait=async function(promise){return await promise}");
    assertPrintSame("class C{async pwait(promise){await promise}}");
    assertPrintSame("o={async pwait(promise){await promise}}");
    assertPrintSame("pwait=async(promise)=>await promise");
  }

  /** Regression test for b/28633247 - necessary parens dropped around arrow functions. */
  @Test
  public void testParensAroundArrow() {
    languageMode = LanguageMode.ECMASCRIPT_2015;

    // Parens required for non-assignment binary operator
    assertPrintSame("x||((_)=>true)");
    // Parens required for unary operator
    assertPrintSame("void((e)=>e*5)");
    // Parens not required for comma operator
    assertPrint("((_) => true), ((_) => false)", "(_)=>true,(_)=>false");
    // Parens not required for right side of assignment operator
    // NOTE: An arrow function on the left side would be a parse error.
    assertPrint("x = ((_) => _ + 1)", "x=(_)=>_+1");
    // Parens required for template tag
    assertPrintSame("((_)=>\"\")`template`");
    // Parens required to reference a property
    assertPrintSame("((a,b,c)=>a+b+c).length");
    assertPrintSame("((a,b,c)=>a+b+c)[\"length\"]");
    // Parens not required when evaluating property name.
    // (It doesn't make much sense to do it, though.)
    assertPrint("x[((_)=>0)]", "x[(_)=>0]");
    // Parens required to call the arrow function immediately
    assertPrintSame("((x)=>x*5)(10)");
    // Parens not required for function call arguments
    assertPrint("x(((_) => true), ((_) => false))", "x((_)=>true,(_)=>false)");
    // Parens required for first operand to a conditional, but not the rest.
    assertPrintSame("((x)=>1)?a:b");
    assertPrint("x?((x)=>0):((x)=>1)", "x?(x)=>0:(x)=>1");
  }

  @Test
  public void testParensAroundArrowReturnValue() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("()=>({})");
    assertPrintSame("()=>({a:1})");
    assertPrintSame("()=>({a:1,b:2})");
    assertPrint("()=>/** @type {Object} */({})", "()=>({})");
    assertPrint("()=>/** @type {Object} */({a:1})", "()=>({a:1})");
    assertPrint("()=>/** @type {Object} */({a:1,b:2})", "()=>({a:1,b:2})");
    assertPrint("()=>/** @type {number} */(3)", "()=>3");
    assertPrint("()=>/** @type {Object} */ ({}={})", "()=>({}={})");

    assertPrintSame("()=>(1,2)");
    assertPrintSame("()=>({},2)");
    assertPrintSame("()=>(1,{})");
    assertPrint("()=>/** @type {?} */(1,2)", "()=>(1,2)");
    assertPrint("()=>/** @type {?} */({},2)", "()=>({},2)");
    assertPrint("()=>/** @type {?} */(1,{})", "()=>(1,{})");

    // Test object literals more deeply nested
    assertPrintSame("fn=()=>({})||3");
    assertPrintSame("fn=()=>3||{}");
    assertPrintSame("fn=()=>({}={})");
    assertPrintSame("()=>function(){}"); // don't need parentheses around a function
    assertPrintSame("for(var i=()=>({});;);");

    preserveTypeAnnotations = true;
    assertPrintSame("()=>/** @type {Object} */ ({})");
  }

  @Test
  public void testPrettyArrowFunction() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrettyPrint("if (x) {var f = ()=>{alert(1); alert(2)}}",
        LINE_JOINER.join(
            "if (x) {",
            "  var f = () => {",
            "    alert(1);",
            "    alert(2);",
            "  };",
            "}",
            ""));
  }

  @Test
  public void testPrettyPrint_switch() {
    assertPrettyPrint("switch(something){case 0:alert(0);break;case 1:alert(1);break}",
        LINE_JOINER.join(
            "switch(something) {",
            "  case 0:",
            "    alert(0);",
            "    break;",
            "  case 1:",
            "    alert(1);",
            "    break;",
            "}",
            ""));
  }

  @Test
  public void testBlocksInCaseArePreserved() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    String js = LINE_JOINER.join(
        "switch(something) {",
        "  case 0:",
        "    {",
        "      const x = 1;",
        "      break;",
        "    }",
        "  case 1:",
        "    break;",
        "  case 2:",
        "    console.log(`case 2!`);",
        "    {",
        "      const x = 2;",
        "      break;",
        "    }",
        "}",
        "");
    assertPrettyPrint(js, js);
  }

  @Test
  public void testBlocksArePreserved() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    String js = LINE_JOINER.join(
        "console.log(0);",
        "{",
        "  let x = 1;",
        "  console.log(x);",
        "}",
        "console.log(x);",
        "");
    assertPrettyPrint(js, js);
  }

  @Test
  public void testBlocksNotPreserved() {
    assertPrint("if (x) {};", "if(x);");
    assertPrint("while (x) {};", "while(x);");
  }

  @Test
  public void testDeclarations() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
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

  @Test
  public void testImports() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("import x from\"foo\"");
    assertPrintSame("import\"foo\"");
    assertPrintSame("import x,{a as b}from\"foo\"");
    assertPrintSame("import{a as b,c as d}from\"foo\"");
    assertPrintSame("import x,{a}from\"foo\"");
    assertPrintSame("import{a,c}from\"foo\"");
    assertPrintSame("import x,*as f from\"foo\"");
    assertPrintSame("import*as f from\"foo\"");
  }

  @Test
  public void testExports() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    // export declarations
    assertPrintSame("export var x=1");
    assertPrintSame("export var x;export var y");
    assertPrintSame("export let x=1");
    assertPrintSame("export const x=1");
    assertPrintSame("export function f(){}");
    assertPrintSame("export class f{}");
    assertPrintSame("export class f{}export class b{}");

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
    assertPrintSame("export default class Foo{}export function f(){}");
    assertPrintSame("export function f(){}export default class Foo{}");
  }

  @Test
  public void testExportAsyncFunction() {
    languageMode = LanguageMode.ECMASCRIPT_2017;
    assertPrintSame("export async function f(){}");
  }

  @Test
  public void testTemplateLiteral() {
    languageMode = LanguageMode.ECMASCRIPT_NEXT;
    // We need to use the raw string instead of the normalized string in template literals
    assertPrintSame("`hello`");
    assertPrintSame("`\\\\bhello`");
    assertPrintSame("`hel\rlo`");
    assertPrintSame("`hel\\rlo`");
    assertPrintSame("`hel\r\nlo`");
    assertPrintSame("`hel\\r\\nlo`");
    assertPrint("`hello`\n'world'", "`hello`;\"world\"");
    assertPrint("`hello`\n`world`", "`hello``world`");
    assertPrint("var x=`TestA`\n`TemplateB`", "var x=`TestA``TemplateB`");
    assertPrintSame("`hello``world`");

    assertPrintSame("`hello${world}!`");
    assertPrintSame("`hello${world} ${name}!`");

    assertPrintSame("`hello${(function(){let x=3})()}`");
    assertPrintSame("(function(){})()`${(function(){})()}`");
    assertPrintSame("url`hello`");
    assertPrintSame("url(`hello`)");
    assertPrintSame("`\\u{2026}`");
    assertPrintSame("`start\\u{2026}end`");
    assertPrintSame("`\\u{1f42a}`");
    assertPrintSame("`start\\u{1f42a}end`");
    assertPrintSame("`\\u2026`");
    assertPrintSame("`start\\u2026end`");
    assertPrintSame("`\"`");
    assertPrintSame("`'`");
    assertPrintSame("`\\\"`");
    assertPrintSame("`\\'`");
    assertPrintSame("`\\``");

    assertPrintSame("foo`\\unicode`");
    // b/114808380
    assertPrintSame("String.raw`a\\ b`");
  }

  @Test
  public void testEs6GoogModule() {
    String code = ""
        + "goog.module('foo.bar');\n"
        + "const STR = '3';\n"
        + "function fn() {\n"
        + "  alert(STR);\n"
        + "}\n"
        + "exports.fn = fn;\n";
    String expectedCode = ""
        + "goog.module('foo.bar');\n"
        + "void 0;\n"
        + "var module$exports$foo$bar = {};\n"
        + "const STR = '3';\n"
        + "module$exports$foo$bar.fn = function fn() {\n"
        + "  alert(STR);\n"
        + "};\n";

    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.setClosurePass(true);
    compilerOptions.setPreserveDetailedSourceInfo(true);
    compilerOptions.setChecksOnly(true);
    compilerOptions.setContinueAfterErrors(true);
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    checkWithOriginalName(code, expectedCode, compilerOptions);
  }

  @Test
  public void testEs6ArrowFunctionSetsOriginalNameForThis() {
    String code = "(x)=>{this.foo[0](3);}";
    String expectedCode = ""
        + "var $jscomp$this = this;\n" // TODO(tomnguyen): Avoid printing this line.
        + "(function(x) {\n"  // TODO(tomnguyen): This should print as an => function.
        + "  this.foo[0](3);\n"
        + "});\n";
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
    checkWithOriginalName(code, expectedCode, compilerOptions);
  }

  @Test
  public void testEs6ArrowFunctionSetsOriginalNameForArguments() {
    // With original names in output set, the end result is not correct code, but the "this" is
    // not rewritten.
    String code = "(x)=>{arguments[0]();}";
    String expectedCode = ""
        + "var $jscomp$arguments = arguments;\n"
        + "(function(x) {\n"
        + "  arguments[0]();\n"
        + "});\n";
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
    checkWithOriginalName(code, expectedCode, compilerOptions);
  }

  @Test
  public void testEs6NewTargetBare() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame("class C{constructor(){new.target.prototype}}");
  }

  @Test
  public void testEs6NewTargetPrototype() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrintSame(
        "class C{constructor(){var callable=Object.setPrototypeOf(obj,new.target.prototype)}}");
  }

  @Test
  public void testEs6NewTargetConditional() {
    languageMode = LanguageMode.ECMASCRIPT_2015;
    assertPrint(
        LINE_JOINER.join(
            "function f() {",
            "  if (!new.target) throw 'Must be called with new!';",
            "}"),
        "function f(){if(!new.target)throw\"Must be called with new!\";}");
  }

  @Test
  public void testGoogScope() {
    // TODO(mknichel): Function declarations need to be rewritten to match the original source
    // instead of being assigned to a local variable with duplicate JS Doc.
    String code = ""
        + "goog.provide('foo.bar');\n"
        + "goog.require('baz.qux.Quux');\n"
        + "goog.require('foo.ScopedType');\n"
        + "\n"
        + "goog.scope(function() {\n"
        + "var Quux = baz.qux.Quux;\n"
        + "var ScopedType = foo.ScopedType;\n"
        + "\n"
        + "var STR = '3';\n"
        + "/** @param {ScopedType} obj */\n"
        + "function fn(obj) {\n"
        + "  alert(STR);\n"
        + "  alert(Quux.someProperty);\n"
        + "}\n"
        + "}); // goog.scope\n";
    String expectedCode = ""
        + "/** @const */ var $jscomp = $jscomp || {};\n"
        + "/** @const */ $jscomp.scope = {};\n"
        + "/** @const */ var foo = {};\n"
        + "/** @const */ foo.bar = {};\n"
        + "goog.provide('foo.bar');\n"
        + "goog.require('baz.qux.Quux');\n"
        + "goog.require('foo.ScopedType');\n"
        + "/**\n"
        + " @param {ScopedType} obj\n"
        + " */\n"
        + "var fn = /**\n"
        + " @param {ScopedType} obj\n"
        + " */\n"
        + "function(obj) {\n"
        + "  alert(STR);\n"
        + "  alert(Quux.someProperty);\n"
        + "};\n"
        + "var STR = '3';\n";

    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.setClosurePass(true);
    compilerOptions.setPreserveDetailedSourceInfo(true);
    compilerOptions.setChecksOnly(true);
    compilerOptions.setCheckTypes(true);
    compilerOptions.setContinueAfterErrors(true);
    compilerOptions.setPreserveClosurePrimitives(true);
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", code)),
        compilerOptions);
    Node node = compiler.getRoot().getLastChild().getFirstChild();

    CompilerOptions codePrinterOptions = new CompilerOptions();
    codePrinterOptions.setPreferSingleQuotes(true);
    codePrinterOptions.setLineLengthThreshold(80);
    codePrinterOptions.setPreserveTypeAnnotations(true);
    codePrinterOptions.setUseOriginalNamesInOutput(true);
    assertThat(
            new CodePrinter.Builder(node)
                .setCompilerOptions(codePrinterOptions)
                .setPrettyPrint(true)
                .setLineBreak(true)
                .build())
        .isEqualTo(expectedCode);
  }

  @Test
  public void testEscapeDollarInTemplateLiteralInOutput() {
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    checkWithOriginalName(
        "let Foo; const x = `${Foo}`;", "let Foo;\nconst x = `${Foo}`;\n", compilerOptions);

    checkWithOriginalName("const x = `\\${Foo}`;", "const x = `\\${Foo}`;\n", compilerOptions);

    checkWithOriginalName(
        "let Foo; const x = `${Foo}\\${Foo}`;",
        "let Foo;\nconst x = `${Foo}\\${Foo}`;\n",
        compilerOptions);

    checkWithOriginalName(
        "let Foo; const x = `\\${Foo}${Foo}`;",
        "let Foo;\nconst x = `\\${Foo}${Foo}`;\n",
        compilerOptions);
  }

  @Test
  public void testEscapeDollarInTemplateLiteralEs5Output() {
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);

    checkWithOriginalName(
        "let Foo; const x = `${Foo}`;", "var Foo;\nvar x = '' + Foo;\n", compilerOptions);

    checkWithOriginalName("const x = `\\${Foo}`;", "var x = '${Foo}';\n", compilerOptions);

    checkWithOriginalName(
        "let Foo; const x = `${Foo}\\${Foo}`;",
        "var Foo;\nvar x = Foo + '${Foo}';\n",
        compilerOptions);
    checkWithOriginalName(
        "let Foo; const x = `\\${Foo}${Foo}`;",
        "var Foo;\nvar x = '${Foo}' + Foo;\n",
        compilerOptions);
  }

  @Test
  public void testDoNotEscapeDollarInRegex() {
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
    checkWithOriginalName("var x = /\\$qux/;", "var x = /\\$qux/;\n", compilerOptions);
    checkWithOriginalName("var x = /$qux/;", "var x = /$qux/;\n", compilerOptions);
  }

  @Test
  public void testDoNotEscapeDollarInStringLiteral() {
    String code = "var x = '\\$qux';";
    String expectedCode = "var x = '$qux';\n";
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.skipAllCompilerPasses();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
    checkWithOriginalName(code, expectedCode, compilerOptions);
    checkWithOriginalName("var x = '\\$qux';", "var x = '$qux';\n", compilerOptions);
    checkWithOriginalName("var x = '$qux';", "var x = '$qux';\n", compilerOptions);
  }

  private void checkWithOriginalName(
      String code, String expectedCode, CompilerOptions compilerOptions) {
    compilerOptions.setCheckSymbols(true);
    compilerOptions.setCheckTypes(true);
    compilerOptions.setPreserveDetailedSourceInfo(true);
    compilerOptions.setPreserveClosurePrimitives(true);
    compilerOptions.setClosurePass(true);
    Compiler compiler = new Compiler();
    compiler.disableThreads();
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", code)),
        compilerOptions);
    Node node = compiler.getRoot().getLastChild().getFirstChild();

    CompilerOptions codePrinterOptions = new CompilerOptions();
    codePrinterOptions.setPreferSingleQuotes(true);
    codePrinterOptions.setLineLengthThreshold(80);
    codePrinterOptions.setUseOriginalNamesInOutput(true);
    assertThat(
            new CodePrinter.Builder(node)
                .setCompilerOptions(codePrinterOptions)
                .setPrettyPrint(true)
                .setLineBreak(true)
                .build())
        .isEqualTo(expectedCode);
  }
}
