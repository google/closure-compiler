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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.parsing.Config.StrictMode.SLOPPY;
import static com.google.javascript.jscomp.parsing.Config.StrictMode.STRICT;
import static com.google.javascript.jscomp.parsing.JsDocInfoParser.BAD_TYPE_WIKI_LINK;
import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParserTest extends BaseJSTypeTestCase {
  private static final String SUSPICIOUS_COMMENT_WARNING =
      IRFactory.SUSPICIOUS_COMMENT_WARNING;

  private static final String TRAILING_COMMA_MESSAGE =
      "Trailing comma is not legal in an ECMA-262 object initializer";

  private static final String MISSING_GT_MESSAGE =
      "Bad type annotation. missing closing >" + BAD_TYPE_WIKI_LINK;


  private static final String UNLABELED_BREAK = "unlabelled break must be inside loop or switch";

  private static final String UNEXPECTED_CONTINUE =
      "continue must be inside loop";

  private static final String UNEXPECTED_RETURN =
      "return must be inside function";

  private static final String UNEXPECTED_LABELLED_CONTINUE =
      "continue can only use labeles of iteration statements";

  private static final String UNDEFINED_LABEL = "undefined label";

  private static final String HTML_COMMENT_WARNING = "In some cases, '<!--' " +
      "and '-->' are treated as a '//' " +
      "for legacy reasons. Removing this from your code is " +
      "safe for all browsers currently in use.";

  private static final String INVALID_ASSIGNMENT_TARGET = "invalid assignment target";

  private static final String SEMICOLON_EXPECTED = "Semi-colon expected";

  private Config.LanguageMode mode;
  private Config.StrictMode strictMode;
  private boolean isIdeMode = false;
  private FeatureSet expectedFeatures;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;
    isIdeMode = false;
    expectedFeatures = FeatureSet.BARE_MINIMUM;
  }

  @Test
  public void testExponentOperator() {
    mode = LanguageMode.ECMASCRIPT7;
    strictMode = STRICT;

    parseError("-x**y", "Unary operator '-' requires parentheses before '**'");

    expectFeatures(Feature.EXPONENT_OP);

    parse("x**y");
    // Parentheses are required for disambiguation when a unary expression is desired as
    // the left operand.
    parse("-(x**y)");
    parse("(-x)**y");
    // Parens are not required for unary operator on the right operand
    parse("x**-y");
    parse("x/y**z");

    parse("2 ** 3 > 3");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseWarning(
        "x**y", requiresLanguageModeMessage(LanguageMode.ECMASCRIPT7, Feature.EXPONENT_OP));
  }

  @Test
  public void testExponentAssignmentOperator() {
    mode = LanguageMode.ECMASCRIPT7;
    strictMode = STRICT;
    expectFeatures(Feature.EXPONENT_OP);
    parse("x**=y;");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseWarning(
        "x**=y;", requiresLanguageModeMessage(LanguageMode.ECMASCRIPT7, Feature.EXPONENT_OP));
  }

  @Test
  public void testFunction() {
    parse("var f = function(x,y,z) { return 0; }");
    parse("function f(x,y,z) { return 0; }");
  }

  @Test
  public void testFunctionTrailingComma() {
    mode = LanguageMode.ECMASCRIPT8;

    expectFeatures(Feature.TRAILING_COMMA_IN_PARAM_LIST);
    parse("var f = function(x,y,z,) {}");
    parse("function f(x,y,z,) {}");
  }

  @Test
  public void testFunctionTrailingCommaPreES8() {
    mode = LanguageMode.ECMASCRIPT7;

    parseError(
        "var f = function(x,y,z,) {}",
        "Invalid trailing comma in formal parameter list");
    parseError(
        "function f(x,y,z,) {}",
        "Invalid trailing comma in formal parameter list");
  }

  @Test
  public void testFunctionExtraTrailingComma() {
    parseError("var f = function(x,y,z,,) {}", "')' expected");
    parseError("function f(x,y,z,,) {}", "')' expected");
  }

  @Test
  public void testCallTrailingComma() {
    mode = LanguageMode.ECMASCRIPT8;

    expectFeatures(Feature.TRAILING_COMMA_IN_PARAM_LIST);
    parse("f(x,y,z,);");
  }

  @Test
  public void testCallTrailingCommaPreES8() {
    mode = LanguageMode.ECMASCRIPT7;

    parseError(
        "f(x,y,z,);",
        "Invalid trailing comma in arguments list");
  }

  @Test
  public void testCallExtraTrailingComma() {
    parseError("f(x,y,z,,);", "')' expected");
  }

  @Test
  public void testWhile() {
    parse("while(1) { break; }");
  }

  @Test
  public void testNestedWhile() {
    parse("while(1) { while(1) { break; } }");
  }

  @Test
  public void testBreak() {
    parseError("break;", UNLABELED_BREAK);
  }

  @Test
  public void testContinue() {
    parseError("continue;", UNEXPECTED_CONTINUE);
  }

  @Test
  public void testBreakCrossFunction() {
    parseError("while(1) { var f = function() { break; } }", UNLABELED_BREAK);
  }

  @Test
  public void testBreakCrossFunctionInFor() {
    parseError("while(1) {for(var f = function () { break; };;) {}}", UNLABELED_BREAK);
  }

  @Test
  public void testBreakInForOf() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.FOR_OF);
    parse(""
        + "for (var x of [1, 2, 3]) {\n"
        + "  if (x == 2) break;\n"
        + "}");
  }

  @Test
  public void testContinueToSwitch() {
    parseError("switch(1) {case(1): continue; }", UNEXPECTED_CONTINUE);
  }

  @Test
  public void testContinueToSwitchWithNoCases() {
    parse("switch(1){}");
  }

  @Test
  public void testContinueToSwitchWithTwoCases() {
    parseError("switch(1){case(1):break;case(2):continue;}", UNEXPECTED_CONTINUE);
  }

  @Test
  public void testContinueToSwitchWithDefault() {
    parseError("switch(1){case(1):break;case(2):default:continue;}", UNEXPECTED_CONTINUE);
  }

  @Test
  public void testContinueToLabelSwitch() {
    parseError(
        "while(1) {a: switch(1) {case(1): continue a; }}",
        UNEXPECTED_LABELLED_CONTINUE);
  }

  @Test
  public void testContinueOutsideSwitch() {
    parse("b: while(1) { a: switch(1) { case(1): continue b; } }");
  }

  @Test
  public void testContinueNotCrossFunction1() {
    parse("a:switch(1){case(1):var f = function(){a:while(1){continue a;}}}");
  }

  @Test
  public void testContinueNotCrossFunction2() {
    parseError(
        "a:switch(1){case(1):var f = function(){while(1){continue a;}}}",
        UNDEFINED_LABEL + " \"a\"");
  }

  @Test
  public void testContinueInForOf() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.FOR_OF);
    parse(""
        + "for (var x of [1, 2, 3]) {\n"
        + "  if (x == 2) continue;\n"
        + "}");
  }

  /** @bug 19100575 */
  @Test
  public void testVarSourceLocations() {
    isIdeMode = true;

    Node n = parse("var x, y = 1;");
    Node var = n.getFirstChild();
    assertNode(var).hasType(Token.VAR);

    Node x = var.getFirstChild();
    assertNode(x).hasType(Token.NAME);
    assertNode(x).hasCharno("var ".length());

    Node y = x.getNext();
    assertNode(y).hasType(Token.NAME);
    assertNode(y).hasCharno("var x, ".length());
    assertNode(y).hasLength("y = 1".length());
  }

  @Test
  public void testSourceLocationsNonAscii() {
    Node n = parse("'안녕세계!'");
    Node exprResult = n.getFirstChild();
    Node string = exprResult.getFirstChild();
    assertNode(string).hasType(Token.STRING);
    assertNode(string).hasLength(7);  // 2 quotes, plus 5 characters
  }

  @Test
  public void testReturn() {
    parse("function foo() { return 1; }");
    parseError("return;", UNEXPECTED_RETURN);
    parseError("return 1;", UNEXPECTED_RETURN);
  }

  @Test
  public void testThrow() {
    parse("throw Error();");
    parse("throw new Error();");
    parse("throw '';");
    parseError("throw;", "semicolon/newline not allowed after 'throw'");
    parseError("throw\nError();", "semicolon/newline not allowed after 'throw'");
  }

  @Test
  public void testLabel1() {
    parse("foo:bar");
  }

  @Test
  public void testLabel2() {
    parse("{foo:bar}");
  }

  @Test
  public void testLabel3() {
    parse("foo:bar:baz");
  }

  @Test
  public void testDuplicateLabelWithoutBraces() {
    parseError("foo:foo:bar", "Duplicate label \"foo\"");
  }

  @Test
  public void testDuplicateLabelWithBraces() {
    parseError("foo:{bar;foo:baz}", "Duplicate label \"foo\"");
  }

  @Test
  public void testDuplicateLabelWithFor() {
    parseError("foo:for(;;){foo:bar}", "Duplicate label \"foo\"");
  }

  @Test
  public void testNonDuplicateLabelSiblings() {
    parse("foo:1;foo:2");
  }

  @Test
  public void testNonDuplicateLabelCrossFunction() {
    parse("foo:(function(){foo:2})");
  }

  @Test
  public void testLabeledFunctionDeclaration() {
    parseError(
        "foo:function f() {}", "Functions can only be declared at top level or inside a block.");
  }

  @Test
  public void testLabeledClassDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError(
        "foo:class Foo {}", "Classes can only be declared at top level or inside a block.");
  }

  @Test
  public void testLinenoCharnoAssign1() {
    Node assign = parse("a = b").getFirstFirstChild();

    assertNode(assign).hasType(Token.ASSIGN);
    assertNode(assign).hasLineno(1);
    assertNode(assign).hasCharno(0);
  }

  @Test
  public void testLinenoCharnoAssign2() {
    Node assign = parse("\n a.g.h.k    =  45").getFirstFirstChild();

    assertNode(assign).hasType(Token.ASSIGN);
    assertNode(assign).hasLineno(2);
    assertNode(assign).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoCall() {
    Node call = parse("\n foo(123);").getFirstFirstChild();

    assertNode(call).hasType(Token.CALL);
    assertNode(call).hasLineno(2);
    assertNode(call).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoGetProp1() {
    Node getprop = parse("\n foo.bar").getFirstFirstChild();

    assertNode(getprop).hasType(Token.GETPROP);
    assertNode(getprop).hasLineno(2);
    assertNode(getprop).hasCharno(1);

    Node name = getprop.getSecondChild();
    assertNode(name).hasType(Token.STRING);
    assertNode(name).hasLineno(2);
    assertNode(name).hasCharno(5);
  }

  @Test
  public void testLinenoCharnoGetProp2() {
    Node getprop = parse("\n foo.\nbar").getFirstFirstChild();

    assertNode(getprop).hasType(Token.GETPROP);
    assertNode(getprop).hasLineno(2);
    assertNode(getprop).hasCharno(1);

    Node name = getprop.getSecondChild();
    assertNode(name).hasType(Token.STRING);
    assertNode(name).hasLineno(3);
    assertNode(name).hasCharno(0);
  }

  @Test
  public void testLinenoCharnoGetelem1() {
    Node call = parse("\n foo[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(2);
    assertNode(call).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoGetelem2() {
    Node call = parse("\n   \n foo()[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(3);
    assertNode(call).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoGetelem3() {
    Node call = parse("\n   \n (8 + kl)[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(3);
    assertNode(call).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoForComparison() {
    Node lt =
      parse("for (; i < j;){}").getFirstChild().getSecondChild();

    assertNode(lt).hasType(Token.LT);
    assertNode(lt).hasLineno(1);
    assertNode(lt).hasCharno(7);
  }

  @Test
  public void testLinenoCharnoHook() {
    Node n = parse("\n a ? 9 : 0").getFirstFirstChild();

    assertNode(n).hasType(Token.HOOK);
    assertNode(n).hasLineno(2);
    assertNode(n).hasCharno(1);
  }

  @Test
  public void testLinenoCharnoArrayLiteral() {
    Node n = parse("\n  [8, 9]").getFirstFirstChild();

    assertNode(n).hasType(Token.ARRAYLIT);
    assertNode(n).hasLineno(2);
    assertNode(n).hasCharno(2);

    n = n.getFirstChild();

    assertNode(n).hasType(Token.NUMBER);
    assertNode(n).hasLineno(2);
    assertNode(n).hasCharno(3);

    n = n.getNext();

    assertNode(n).hasType(Token.NUMBER);
    assertNode(n).hasLineno(2);
    assertNode(n).hasCharno(6);
  }

  @Test
  public void testLinenoCharnoObjectLiteral() {
    Node n = parse("\n\n var a = {a:0\n,b :1};")
        .getFirstFirstChild().getFirstChild();

    assertNode(n).hasType(Token.OBJECTLIT);
    assertNode(n).hasLineno(3);
    assertNode(n).hasCharno(9);

    Node key = n.getFirstChild();

    assertNode(key).hasType(Token.STRING_KEY);
    assertNode(key).hasLineno(3);
    assertNode(key).hasCharno(10);

    Node value = key.getFirstChild();

    assertNode(value).hasType(Token.NUMBER);
    assertNode(value).hasLineno(3);
    assertNode(value).hasCharno(12);

    key = key.getNext();

    assertNode(key).hasType(Token.STRING_KEY);
    assertNode(key).hasLineno(4);
    assertNode(key).hasCharno(1);

    value = key.getFirstChild();

    assertNode(value).hasType(Token.NUMBER);
    assertNode(value).hasLineno(4);
    assertNode(value).hasCharno(4);
  }

  @Test
  public void testLinenoCharnoObjectLiteralMemberFunction() {
    mode = LanguageMode.ECMASCRIPT6;
    Node n = parse("var a = {\n fn() {} };").getFirstFirstChild().getFirstChild();

    assertNode(n).hasType(Token.OBJECTLIT);
    assertNode(n).hasLineno(1);
    assertNode(n).hasCharno(8);

    // fn() {}
    Node key = n.getFirstChild();

    assertNode(key).hasType(Token.MEMBER_FUNCTION_DEF);
    assertNode(key).hasLineno(2);
    assertNode(key).hasCharno(1);
    assertNode(key).hasLength(2); // "fn"

    Node value = key.getFirstChild();

    assertNode(value).hasType(Token.FUNCTION);
    assertNode(value).hasLineno(2);
    assertNode(value).hasCharno(1);
    assertNode(value).hasLength(7); // "fn() {}"
  }

  @Test
  public void testLinenoCharnoEs6Class() {
    mode = LanguageMode.ECMASCRIPT6;
    Node n = parse("class C {\n  fn1() {}\n  static fn2() {}\n };").getFirstChild();

    assertNode(n).hasType(Token.CLASS);
    assertNode(n).hasLineno(1);
    assertNode(n).hasCharno(0);

    Node members = NodeUtil.getClassMembers(n);
    assertNode(members).hasType(Token.CLASS_MEMBERS);

    // fn1 () {}
    Node memberFn = members.getFirstChild();

    assertNode(memberFn).hasType(Token.MEMBER_FUNCTION_DEF);
    assertNode(memberFn).hasLineno(2);
    assertNode(memberFn).hasCharno(2);
    assertNode(memberFn).hasLength(3); // "fn"

    Node fn = memberFn.getFirstChild();
    assertNode(fn).hasType((Token.FUNCTION));
    assertNode(fn).hasLineno(2);
    assertNode(fn).hasCharno(2);
    assertNode(fn).hasLength(8); // "fn1() {}"

    // static fn2() {}
    memberFn = memberFn.getNext();

    assertNode(memberFn).hasType(Token.MEMBER_FUNCTION_DEF);
    assertNode(memberFn).hasLineno(3);
    assertNode(memberFn).hasCharno(9);
    assertNode(memberFn).hasLength(3); // "fn2"

    fn = memberFn.getFirstChild();
    assertNode(fn).hasType((Token.FUNCTION));
    assertNode(fn).hasLineno(3);
    assertNode(fn).hasCharno(2);
    assertNode(fn).hasLength(15); // "static fn2() {}"
  }

  @Test
  public void testLinenoCharnoAdd() {
    testLinenoCharnoBinop("+");
  }

  @Test
  public void testLinenoCharnoSub() {
    testLinenoCharnoBinop("-");
  }

  @Test
  public void testLinenoCharnoMul() {
    testLinenoCharnoBinop("*");
  }

  @Test
  public void testLinenoCharnoDiv() {
    testLinenoCharnoBinop("/");
  }

  @Test
  public void testLinenoCharnoMod() {
    testLinenoCharnoBinop("%");
  }

  @Test
  public void testLinenoCharnoShift() {
    testLinenoCharnoBinop("<<");
  }

  @Test
  public void testLinenoCharnoBinaryAnd() {
    testLinenoCharnoBinop("&");
  }

  @Test
  public void testLinenoCharnoAnd() {
    testLinenoCharnoBinop("&&");
  }

  @Test
  public void testLinenoCharnoBinaryOr() {
    testLinenoCharnoBinop("|");
  }

  @Test
  public void testLinenoCharnoOr() {
    testLinenoCharnoBinop("||");
  }

  @Test
  public void testLinenoCharnoLt() {
    testLinenoCharnoBinop("<");
  }

  @Test
  public void testLinenoCharnoLe() {
    testLinenoCharnoBinop("<=");
  }

  @Test
  public void testLinenoCharnoGt() {
    testLinenoCharnoBinop(">");
  }

  @Test
  public void testLinenoCharnoGe() {
    testLinenoCharnoBinop(">=");
  }

  private void testLinenoCharnoBinop(String binop) {
    Node op = parse("var a = 89 " + binop + " 76;").getFirstChild().
        getFirstFirstChild();

    assertNode(op).hasLineno(1);
    assertNode(op).hasCharno(8);
  }

  @Test
  public void testJSDocAttachment1() {
    Node varNode = parse("/** @type {number} */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
    assertNodeHasJSDocInfoWithJSType(varNode, NUMBER_TYPE);

    // VAR NAME
    Node varNameNode = varNode.getFirstChild();
    assertNode(varNameNode).hasType(Token.NAME);
    assertThat(varNameNode.getJSDocInfo()).isNull();

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.LET_DECLARATIONS);

    Node letNode = parse("/** @type {number} */let a;").getFirstChild();

    // LET
    assertNode(letNode).hasType(Token.LET);
    assertNodeHasJSDocInfoWithJSType(letNode, NUMBER_TYPE);

    // LET NAME
    Node letNameNode = letNode.getFirstChild();
    assertNode(letNameNode).hasType(Token.NAME);
    assertThat(letNameNode.getJSDocInfo()).isNull();

    expectFeatures(Feature.CONST_DECLARATIONS);
    Node constNode = parse("/** @type {number} */const a = 0;").getFirstChild();

    // CONST
    assertNode(constNode).hasType(Token.CONST);
    assertNodeHasJSDocInfoWithJSType(constNode, NUMBER_TYPE);

    // CONST NAME
    Node constNameNode = constNode.getFirstChild();
    assertNode(constNameNode).hasType(Token.NAME);
    assertThat(constNameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment2() {
    Node varNode = parse("/** @type {number} */var a,b;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
    assertNodeHasJSDocInfoWithJSType(varNode, NUMBER_TYPE);

    // First NAME
    Node nameNode1 = varNode.getFirstChild();
    assertNode(nameNode1).hasType(Token.NAME);
    assertThat(nameNode1.getJSDocInfo()).isNull();

    // Second NAME
    Node nameNode2 = nameNode1.getNext();
    assertNode(nameNode2).hasType(Token.NAME);
    assertThat(nameNode2.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment3() {
    Node assignNode = parse("/** @type {number} */goog.FOO = 5;").getFirstFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);
    assertNodeHasJSDocInfoWithJSType(assignNode, NUMBER_TYPE);
  }

  @Test
  public void testJSDocAttachment4() {
    Node varNode = parse(
        "var a, /** @define {number} */ b = 5;").getFirstChild();

    // ASSIGN
    assertNode(varNode).hasType(Token.VAR);
    assertThat(varNode.getJSDocInfo()).isNull();

    // a
    Node a = varNode.getFirstChild();
    assertThat(a.getJSDocInfo()).isNull();

    // b
    Node b = a.getNext();
    JSDocInfo info = b.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.isDefine()).isTrue();
    assertTypeEquals(NUMBER_TYPE, info.getType());
  }

  @Test
  public void testJSDocAttachment5() {
    Node varNode =
        parse("var /** @type {number} */a, /** @define {number} */b = 5;").getFirstChild();

    // ASSIGN
    assertNode(varNode).hasType(Token.VAR);
    assertThat(varNode.getJSDocInfo()).isNull();

    // a
    Node a = varNode.getFirstChild();
    assertThat(a.getJSDocInfo()).isNotNull();
    JSDocInfo info = a.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.isDefine()).isFalse();
    assertTypeEquals(NUMBER_TYPE, info.getType());

    // b
    Node b = a.getNext();
    info = b.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.isDefine()).isTrue();
    assertTypeEquals(NUMBER_TYPE, info.getType());
  }

  /**
   * Tests that a JSDoc comment in an unexpected place of the code does not propagate to following
   * code due to {@link JSDocInfo} aggregation.
   */
  @Test
  public void testJSDocAttachment6() {
    Node functionNode = parse(
        "var a = /** @param {number} index */5;"
        + "/** @return {boolean} */function f(index){}")
        .getSecondChild();

    assertNode(functionNode).hasType(Token.FUNCTION);
    JSDocInfo info = functionNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.hasParameter("index")).isFalse();
    assertThat(info.hasReturnType()).isTrue();
    assertTypeEquals(BOOLEAN_TYPE, info.getReturnType());
  }

  @Test
  public void testJSDocAttachment7() {
    Node varNode = parse("/** */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment8() {
    Node varNode = parse("/** x */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment9() {
    Node varNode = parse("/** \n x */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment10() {
    Node varNode = parse("/** x\n */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment11() {
    Node varNode =
       parse("/** @type {{x : number, 'y' : string, z}} */var a;")
        .getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
    assertNodeHasJSDocInfoWithJSType(
        varNode,
        createRecordTypeBuilder()
            .addProperty("x", NUMBER_TYPE, null)
            .addProperty("y", STRING_TYPE, null)
            .addProperty("z", UNKNOWN_TYPE, null)
            .build());

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment12() {
    Node varNode =
       parse("var a = {/** @type {Object} */ b: c};")
        .getFirstChild();
    Node objectLitNode = varNode.getFirstFirstChild();
    assertNode(objectLitNode).hasType(Token.OBJECTLIT);
    assertThat(objectLitNode.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJSDocAttachment13() {
    Node varNode = parse("/** foo */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJSDocAttachment14() {
    Node varNode = parse("/** */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment15() {
    Node varNode = parse("/** \n * \n */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocAttachment16() {
    Node exprCall =
        parse("/** @private */ x(); function f() {};").getFirstChild();
    assertNode(exprCall).hasType(Token.EXPR_RESULT);
    assertThat(exprCall.getNext().getJSDocInfo()).isNull();
    assertThat(exprCall.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJSDocAttachment17() {
    Node fn =
        parse(
            "function f() { " +
            "  return /** @type {string} */ (g(1 /** @desc x */));" +
            "};").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);
    Node cast = fn.getLastChild().getFirstFirstChild();
    assertNode(cast).hasType(Token.CAST);
  }

  @Test
  public void testJSDocAttachment18() {
    Node fn =
        parse(
            "function f() { " +
            "  var x = /** @type {string} */ (y);" +
            "};").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);
    Node cast = fn.getLastChild().getFirstFirstChild().getFirstChild();
    assertNode(cast).hasType(Token.CAST);
  }

  @Test
  public void testJSDocAttachment19() {
    Node fn =
        parse(
            "function f() { " +
            "  /** @type {string} */" +
            "  return;" +
            "};").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    Node ret = fn.getLastChild().getFirstChild();
    assertNode(ret).hasType(Token.RETURN);
    assertThat(ret.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJSDocAttachment20() {
    Node fn =
        parse(
            "function f() { " +
            "  /** @type {string} */" +
            "  if (true) return;" +
            "};").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    Node ret = fn.getLastChild().getFirstChild();
    assertNode(ret).hasType(Token.IF);
    assertThat(ret.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJSDocAttachment21() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.CONST_DECLARATIONS);
    parse("/** @param {string} x */ const f = function() {};");

    expectFeatures(Feature.LET_DECLARATIONS);
    parse("/** @param {string} x */ let f = function() {};");
  }

  // Tests that JSDoc gets attached to the children of export nodes, and there are no warnings.
  // See https://github.com/google/closure-compiler/issues/781
  @Test
  public void testJSDocAttachment22() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.MODULES);

    Node n = parse("/** @param {string} x */ export function f(x) {};");
    Node export = n.getFirstFirstChild();

    assertNode(export).hasType(Token.EXPORT);
    assertThat(export.getJSDocInfo()).isNull();
    assertThat(export.getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(export.getFirstChild().getJSDocInfo().hasParameter("x")).isTrue();
  }

  @Test
  public void testInlineJSDocAttachmentToVar() {
    Node letNode = parse("let /** string */ x = 'a';").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    JSDocInfo info = letNode.getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatNormalProp() {
    Node letNode =
        parse("let { normalProp: /** string */ normalPropTarget } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node normalProp = objectPattern.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    Node normalPropTarget = normalProp.getOnlyChild();
    assertNodeHasJSDocInfoWithJSType(normalPropTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatNormalPropKey() {
    Node letNode = parse("let { /** string */ normalProp: normalProp } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node normalProp = objectPattern.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    // TODO(bradfordcsmith): Putting the inline jsdoc on the key should be an error,
    //     because it isn't clear what that should mean.
    assertNodeHasNoJSDocInfo(normalProp);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatShorthandProp() {
    Node letNode = parse("let { /** string */ shorthandProp } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node shorthandProp = objectPattern.getFirstChild();
    assertNode(shorthandProp).hasType(Token.STRING_KEY);
    Node shorthandPropTarget = shorthandProp.getOnlyChild();
    assertNodeHasJSDocInfoWithJSType(shorthandPropTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatNormalPropWithDefault() {
    Node letNode =
        parse("let { normalPropWithDefault: /** string */ normalPropWithDefault = 'hi' } = {};")
            .getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node normalPropWithDefault = objectPattern.getFirstChild();
    assertNode(normalPropWithDefault).hasType(Token.STRING_KEY);
    Node normalPropDefaultValue = normalPropWithDefault.getOnlyChild();
    assertNode(normalPropDefaultValue).hasType(Token.DEFAULT_VALUE);
    Node normalPropWithDefaultTarget = normalPropDefaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(normalPropWithDefaultTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatShorthandWithDefault() {
    Node letNode =
        parse("let { /** string */ shorthandPropWithDefault = 'lo' } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node shorthandPropWithDefault = objectPattern.getFirstChild();
    assertNode(shorthandPropWithDefault).hasType(Token.STRING_KEY);
    Node shorthandPropDefaultValue = shorthandPropWithDefault.getOnlyChild();
    assertNode(shorthandPropDefaultValue).hasType(Token.DEFAULT_VALUE);
    Node shorthandPropWithDefaultTarget = shorthandPropDefaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(shorthandPropWithDefaultTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatComputedPropKey() {
    Node letNode =
        parse("let { /** string */ ['computedProp']: computedProp } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node computedProp = objectPattern.getFirstChild();
    assertNode(computedProp).hasType(Token.COMPUTED_PROP);
    // TODO(bradfordcsmith): Putting inline JSDoc on the computed property key should be an error,
    //     since it's not clear what it should mean.
    assertNodeHasNoJSDocInfo(computedProp);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatComputedProp() {
    Node letNode =
        parse("let { ['computedProp']: /** string */ computedProp } = {};").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node computedProp = objectPattern.getFirstChild();
    assertNode(computedProp).hasType(Token.COMPUTED_PROP);
    Node computedPropTarget = computedProp.getSecondChild();
    assertNodeHasJSDocInfoWithJSType(computedPropTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatComputedPropWithDefault() {
    Node letNode =
        parse("let { ['computedPropWithDefault']: /** string */ computedProp = 'go' } = {};")
            .getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node objectPattern = destructuringLhs.getFirstChild();

    Node computedPropWithDefault = objectPattern.getFirstChild();
    assertNode(computedPropWithDefault).hasType(Token.COMPUTED_PROP);
    Node computedPropDefaultValue = computedPropWithDefault.getSecondChild();
    assertNode(computedPropDefaultValue).hasType(Token.DEFAULT_VALUE);
    Node computedPropWithDefaultTarget = computedPropDefaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(computedPropWithDefaultTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatNormalPropWithQualifiedName() {
    Node exprResult =
        parse("({ normalProp: /** string */ ns.normalPropTarget } = {});").getFirstChild();
    Node assignNode = exprResult.getFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);

    Node objectPattern = assignNode.getFirstChild();

    Node normalProp = objectPattern.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    Node nsNormalPropTarget = normalProp.getOnlyChild();
    assertNodeHasJSDocInfoWithJSType(nsNormalPropTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjPatNormalPropWithQualifiedNameWithDefault() {
    Node exprResult =
        parse("({ normalProp: /** string */ ns.normalPropTarget = 'foo' } = {});").getFirstChild();
    Node assignNode = exprResult.getFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);

    Node objectPattern = assignNode.getFirstChild();

    Node normalProp = objectPattern.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    Node defaultValue = normalProp.getFirstChild();
    assertNode(defaultValue).hasType(Token.DEFAULT_VALUE);
    Node nsNormalPropTarget = defaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(nsNormalPropTarget, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToArrayPatElement() {
    Node letNode =
        parse("let [/** string */ x] = [];")
            .getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node arrayPattern = destructuringLhs.getFirstChild();
    Node xVarName = arrayPattern.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(xVarName, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToArrayPatElementWithDefault() {
    Node letNode =
        parse("let [/** string */ x = 'hi'] = [];")
            .getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node arrayPattern = destructuringLhs.getFirstChild();
    Node defaultValue = arrayPattern.getFirstChild();
    assertNode(defaultValue).hasType(Token.DEFAULT_VALUE);

    Node xVarName = defaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(xVarName, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToArrayPatElementQualifiedName() {
    Node exprResult = parse("[/** string */ x.y.z] = [];").getFirstChild();
    Node assignNode = exprResult.getFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);

    Node arrayPattern = assignNode.getFirstChild();
    assertNode(arrayPattern).hasType(Token.ARRAY_PATTERN);
    Node xYZName = arrayPattern.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(xYZName, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToArrayPatElementQualifiedNameWithDefault() {
    Node exprResult = parse("[/** string */ x.y.z = 'foo'] = [];").getFirstChild();
    Node assignNode = exprResult.getFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);

    Node arrayPattern = assignNode.getFirstChild();
    assertNode(arrayPattern).hasType(Token.ARRAY_PATTERN);
    Node defaultValue = arrayPattern.getOnlyChild();
    assertNode(defaultValue).hasType(Token.DEFAULT_VALUE);
    Node xYZName = defaultValue.getFirstChild();
    assertNodeHasJSDocInfoWithJSType(xYZName, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToArrayPatElementAfterElision() {
    Node letNode =
        parse("let [, /** string */ x] = [];")
            .getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node destructuringLhs = letNode.getFirstChild();
    Node arrayPattern = destructuringLhs.getFirstChild();
    Node empty = arrayPattern.getFirstChild();
    assertNode(empty).hasToken(Token.EMPTY);
    assertNode(empty).hasCharno(5);
    assertNode(empty).hasLength(1);
    Node xVarName = arrayPattern.getSecondChild();
    assertNodeHasJSDocInfoWithJSType(xVarName, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjLitNormalProp() {
    Node letNode = parse("let x = { normalProp: /** string */ normalPropTarget };").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node xNode = letNode.getFirstChild();
    Node objectLit = xNode.getFirstChild();

    Node normalProp = objectLit.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    Node normalPropTarget = normalProp.getOnlyChild();
    // TODO(bradfordcsmith): Make sure CheckJsDoc considers this an error, because it doesn't
    //     make sense to have inline JSDoc on the value.
    assertNodeHasJSDocInfoWithNoJSType(normalPropTarget);
  }

  @Test
  public void testInlineJSDocAttachmentToObjLitNormalPropKey() {
    Node letNode = parse("let x = { /** string */ normalProp: normalProp };").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node xNode = letNode.getFirstChild();
    Node objectLit = xNode.getFirstChild();

    Node normalProp = objectLit.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    // TODO(bradfordcsmith): We should either disallow inline JSDoc here or correctly pull the type
    //     out of it.
    assertNodeHasJSDocInfoWithNoJSType(normalProp);
  }

  @Test
  public void testJSDocAttachmentToObjLitNormalPropKey() {
    Node letNode =
        parse("let x = { /** @type {string} */ normalProp: normalProp };").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node xNode = letNode.getFirstChild();
    Node objectLit = xNode.getFirstChild();

    Node normalProp = objectLit.getFirstChild();
    assertNode(normalProp).hasType(Token.STRING_KEY);
    assertNodeHasJSDocInfoWithJSType(normalProp, STRING_TYPE);
  }

  @Test
  public void testInlineJSDocAttachmentToObjLitShorthandProp() {
    Node letNode = parse("let x = { /** string */ shorthandProp };").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node xNode = letNode.getFirstChild();
    Node objectLit = xNode.getFirstChild();

    Node shorthandPropKey = objectLit.getFirstChild();
    assertNode(shorthandPropKey).hasType(Token.STRING_KEY);
    // TODO(bradfordcsmith): We should either disallow inline JSDoc here or correctly pull the type
    //     out of it.
    assertNodeHasJSDocInfoWithNoJSType(shorthandPropKey);
    Node shorthandPropTarget = shorthandPropKey.getOnlyChild();
    assertNodeHasNoJSDocInfo(shorthandPropTarget);
  }

  @Test
  public void testJSDocAttachmentToObjLitShorthandProp() {
    Node letNode = parse("let x = { /** @type {string} */ shorthandProp };").getFirstChild();
    assertNode(letNode).hasType(Token.LET);

    Node xNode = letNode.getFirstChild();
    Node objectLit = xNode.getFirstChild();

    Node shorthandPropKey = objectLit.getFirstChild();
    assertNode(shorthandPropKey).hasType(Token.STRING_KEY);
    assertNodeHasJSDocInfoWithJSType(shorthandPropKey, STRING_TYPE);
    Node shorthandPropTarget = shorthandPropKey.getOnlyChild();
    assertNodeHasNoJSDocInfo(shorthandPropTarget);
  }

  @Test
  public void testInlineJSDocAttachment1() {
    Node fn = parse("function f(/** string */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocAttachment2() {
    Node fn = parse(
        "function f(/** ? */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(UNKNOWN_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocAttachment3() {
    parse("function f(/** @type {string} */ x) {}");
  }

  @Test
  public void testInlineJSDocAttachment4() {
    parse(
        "function f(/**\n" +
        " * @type {string}\n" +
        " */ x) {}");
  }

  @Test
  public void testInlineJSDocAttachment5() {
    Node vardecl = parse("var /** string */ x = 'asdf';").getFirstChild();
    JSDocInfo info = vardecl.getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocAttachment6() {
    Node fn = parse("function f(/** {attr: number} */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(
        createRecordTypeBuilder().addProperty("attr", NUMBER_TYPE, null).build(), info.getType());
  }

  @Test
  public void testInlineJSDocWithOptionalType() {
    Node fn = parse("function f(/** string= */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info.getType().isOptionalArg()).isTrue();
  }

  @Test
  public void testInlineJSDocWithVarArgs() {
    Node fn = parse("function f(/** ...string */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info.getType().isVarArgs()).isTrue();
  }

  @Test
  public void testInlineJSDocReturnType() {
    Node fn = parse("function /** string */ f(x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getFirstChild().getJSDocInfo();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocReturnType_generator1() {
    mode = LanguageMode.ECMASCRIPT6;

    Node fn = parse("function * /** string */ f(x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getFirstChild().getJSDocInfo();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocReturnType_generator2() {
    mode = LanguageMode.ECMASCRIPT6;

    Node fn = parse("function /** string */ *f(x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getFirstChild().getJSDocInfo();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testInlineJSDocReturnType_async() {
    mode = LanguageMode.ECMASCRIPT8;

    Node fn = parse("async function /** string */ f(x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getFirstChild().getJSDocInfo();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing1() {
    assertNodeEquality(
        parse("var a = [1,2]"),
        parseWarning("/** @type {Array<number} */var a = [1,2]",
            MISSING_GT_MESSAGE));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing2() {
    assertNodeEquality(
        parse("var a = [1,2]"),
        parseWarning("/** @type {Array.<number}*/var a = [1,2]",
            MISSING_GT_MESSAGE));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing3() {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @param {Array.<number} nums */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
            MISSING_GT_MESSAGE));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing4() {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parse("/** @return {boolean} */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};"));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing5() {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parse("/** @param {boolean} this is some string*/" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};"));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing6() {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning(
            "/** @param {bool!*%E$} */"
                + "C.prototype.say=function(nums) {alert(nums.join(','));};",
            "Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK,
            "Bad type annotation. expecting a variable name in a @param tag."
                + BAD_TYPE_WIKI_LINK));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing7() {
    isIdeMode = true;

    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @see */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "@see tag missing description"));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing8() {
    isIdeMode = true;

    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @author */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "@author tag missing author"));
  }

  @Test
  public void testIncorrectJSDocDoesNotAlterJSParsing9() {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @someillegaltag */" +
              "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "illegal use of unknown JSDoc tag \"someillegaltag\";"
              + " ignoring it"));
  }

  @Test
  public void testMisplacedDescAnnotation_noWarning() {
    parse("/** @desc Foo. */ var MSG_BAR = goog.getMsg('hello');");
    parse("/** @desc Foo. */ x.y.z.MSG_BAR = goog.getMsg('hello');");
    parse("/** @desc Foo. */ MSG_BAR = goog.getMsg('hello');");
    parse("var msgs = {/** @desc x */ MSG_X: goog.getMsg('x')}");
  }

  @Test
  public void testUnescapedSlashInRegexpCharClass() {
    parse("var foo = /[/]/;");
    parse("var foo = /[hi there/]/;");
    parse("var foo = /[/yo dude]/;");
    parse("var foo = /\\/[@#$/watashi/wa/suteevu/desu]/;");
  }

  /** Test for https://github.com/google/closure-compiler/issues/389. */
  @Test
  public void testMalformedRegexp() {
    // Simple repro case
    String js = "var x = com\\";
    parseError(js, "Invalid escape sequence");

    // The original repro case as reported.
    js = Joiner.on('\n').join(
        "(function() {",
        "  var url=\"\";",
        "  switch(true)",
        "  {",
        "    case /a.com\\/g|l.i/N/.test(url):",
        "      return \"\";",
        "    case /b.com\\/T/.test(url):",
        "      return \"\";",
        "  }",
        "}",
        ")();");
    parseError(js, "primary expression expected");
  }

  private static void assertNodeEquality(Node expected, Node found) {
    String message = expected.checkTreeEquals(found);
    if (message != null) {
      assertWithMessage(message).fail();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testParse() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    Node a = Node.newString(Token.NAME, "a");
    a.addChildToFront(Node.newString(Token.NAME, "b"));
    List<ParserResult> testCases = ImmutableList.of(
        new ParserResult(
            "3;",
            createScript(new Node(Token.EXPR_RESULT, Node.newNumber(3.0)))),
        new ParserResult(
            "var a = b;",
             createScript(new Node(Token.VAR, a)))
        );

    for (ParserResult testCase : testCases) {
      assertNodeEquality(testCase.node, parse(testCase.code));
    }
  }

  @Test
  public void testPostfixExpression() {
    parse("a++");
    parse("a.b--");
    parse("a[0]++");
    parse("/** @type {number} */ (a)++;");

    parseError("a()++", "Invalid postfix increment operand.");
    parseError("(new C)--", "Invalid postfix decrement operand.");
    parseError("this++", "Invalid postfix increment operand.");
    parseError("(a--)++", "Invalid postfix increment operand.");
    parseError("(+a)++", "Invalid postfix increment operand.");
    parseError("[1,2]++", "Invalid postfix increment operand.");
    parseError("'literal'++", "Invalid postfix increment operand.");
    parseError("/** @type {number} */ (a())++;", "Invalid postfix increment operand.");
  }

  @Test
  public void testUnaryExpression() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("delete a.b");
    parse("delete a[0]");
    parse("void f()");
    parse("typeof new C");
    parse("++a[0]");
    parse("--a.b");
    parse("+{a: 1}");
    parse("-[1,2]");
    parse("~'42'");
    expectFeatures(Feature.SUPER);
    parse("!super.a");
    expectFeatures();

    parseError("delete f()", "Invalid delete operand. Only properties can be deleted.");
    parseError("++a++", "Invalid prefix increment operand.");
    parseError("--{a: 1}", "Invalid prefix decrement operand.");
    parseError("++this", "Invalid prefix increment operand.");
    parseError("++(-a)", "Invalid prefix increment operand.");
    parseError("++{a: 1}", "Invalid prefix increment operand.");
    parseError("++'literal'", "Invalid prefix increment operand.");
    parseError("++delete a.b", "Invalid prefix increment operand.");
  }

  @Test
  public void testAutomaticSemicolonInsertion() {
    // var statements
    assertNodeEquality(
        parse("var x = 1\nvar y = 2"),
        parse("var x = 1; var y = 2;"));
    assertNodeEquality(
        parse("var x = 1\n, y = 2"),
        parse("var x = 1, y = 2;"));

    // assign statements
    assertNodeEquality(
        parse("x = 1\ny = 2"),
        parse("x = 1; y = 2;"));

    assertNodeEquality(
        parse("x = 1\n;y = 2"),
        parse("x = 1;; y = 2;"));

    // if/else statements
    assertNodeEquality(
        parse("if (x)\n;else{}"),
        parse("if (x) {} else {}"));
  }

  /** Test all the ASI examples from http://www.ecma-international.org/ecma-262/5.1/#sec-7.9.2 */
  @Test
  public void testAutomaticSemicolonInsertionExamplesFromSpec() {
    parseError("{ 1 2 } 3", SEMICOLON_EXPECTED);

    assertNodeEquality(
        parse("{ 1\n2 } 3"),
        parse("{ 1; 2; } 3;"));

    parseError("for (a; b\n)", "';' expected");

    assertNodeEquality(
        parse("function f() { return\na + b }"),
        parse("function f() { return; a + b; }"));

    assertNodeEquality(
        parse("a = b\n++c"),
        parse("a = b; ++c;"));

    parseError("if (a > b)\nelse c = d", "primary expression expected");

    assertNodeEquality(
        parse("a = b + c\n(d + e).print()"),
        parse("a = b + c(d + e).print()"));
  }

  private static Node createScript(Node n) {
    Node script = new Node(Token.SCRIPT);
    script.addChildToBack(n);
    return script;
  }

  @Test
  public void testMethodInObjectLiteral() {
    expectFeatures(Feature.MEMBER_DECLARATIONS);
    testMethodInObjectLiteral("var a = {b() {}};");
    testMethodInObjectLiteral("var a = {b() { alert('b'); }};");

    // Static methods not allowed in object literals.
    expectFeatures();
    parseError("var a = {static b() { alert('b'); }};",
        "Cannot use keyword in short object literal");
  }

  private void testMethodInObjectLiteral(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.MEMBER_DECLARATIONS));
  }

  @Test
  public void testExtendedObjectLiteral() {
    expectFeatures(Feature.EXTENDED_OBJECT_LITERALS);
    testExtendedObjectLiteral("var a = {b};");
    testExtendedObjectLiteral("var a = {b, c};");
    testExtendedObjectLiteral("var a = {b, c: d, e};");
    testExtendedObjectLiteral("var a = {type};");
    testExtendedObjectLiteral("var a = {declare};");
    testExtendedObjectLiteral("var a = {namespace};");
    testExtendedObjectLiteral("var a = {module};");

    expectFeatures();
    parseError("var a = { '!@#$%' };", "':' expected");
    parseError("var a = { 123 };", "':' expected");
    parseError("var a = { let };", "Cannot use keyword in short object literal");
    parseError("var a = { else };", "Cannot use keyword in short object literal");
  }

  private void testExtendedObjectLiteral(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.EXTENDED_OBJECT_LITERALS));
  }

  @Test
  public void testComputedPropertiesObjLit() {
    expectFeatures(Feature.COMPUTED_PROPERTIES);

    // Method
    testComputedProperty("var x = {  [prop + '_']() {} }");
    // NOTE: we treat string and number keys as if they were computed properties for method
    // shorthand, but not getters and setters.
    testComputedProperty("var x = {  'abc'() {} }");
    testComputedProperty("var x = {  123() {} }");

    // Getter
    testComputedProperty("var x = {  get [prop + '_']() {} }");

    // Setter
    testComputedProperty("var x = { set [prop + '_'](val) {} }");

    // Generator method
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("var x = { *[prop + '_']() {} }");
    parse("var x = { *'abc'() {} }");
    parse("var x = { *123() {} }");

    mode = LanguageMode.ECMASCRIPT8;
    parse("var x = { async [prop + '_']() {} }");
    parse("var x = { async 'abc'() {} }");
    parse("var x = { async 123() {} }");
  }

  @Test
  public void testComputedMethodClass() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.CLASSES, Feature.COMPUTED_PROPERTIES);
    parse("class X { [prop + '_']() {} }");
    // Note that we pretend string and number keys are computed property names, because
    // this makes it easier to treat class and object-literal cases consistently.
    parse("class X { 'abc'() {} }");
    parse("class X { 123() {} }");

    parse("class X { static [prop + '_']() {} }");
    parse("class X { static 'abc'() {} }");
    parse("class X { static 123() {} }");

    parse("class X { *[prop + '_']() {} }");
    parse("class X { *'abc'() {} }");
    parse("class X { *123() {} }");

    mode = LanguageMode.ECMASCRIPT8;
    parse("class X { async [prop + '_']() {} }");
    parse("class X { async 'abc'() {} }");
    parse("class X { async 123() {} }");
  }

  @Test
  public void testComputedProperty() {
    expectFeatures(Feature.COMPUTED_PROPERTIES);

    testComputedProperty(Joiner.on('\n').join(
        "var prop = 'some complex expression';",
        "",
        "var x = {",
        "  [prop]: 'foo'",
        "}"));

    testComputedProperty(Joiner.on('\n').join(
        "var prop = 'some complex expression';",
        "",
        "var x = {",
        "  [prop + '!']: 'foo'",
        "}"));

    testComputedProperty(Joiner.on('\n').join(
        "var prop;",
        "",
        "var x = {",
        "  [prop = 'some expr']: 'foo'",
        "}"));

    testComputedProperty(Joiner.on('\n').join(
        "var x = {",
        "  [1 << 8]: 'foo'",
        "}"));

    String js = Joiner.on('\n').join(
        "var x = {",
        "  [1 << 8]: 'foo',",
        "  [1 << 7]: 'bar'",
        "}");
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(js);
    mode = LanguageMode.ECMASCRIPT5;
    String warning = getRequiresEs6Message(Feature.COMPUTED_PROPERTIES);
    parseWarning(js, warning, warning);
  }

  private void testComputedProperty(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.COMPUTED_PROPERTIES));
  }

  @Test
  public void testTrailingCommaWarning1() {
    parse("var a = ['foo', 'bar'];");
  }

  @Test
  public void testTrailingCommaWarning2() {
    parse("var a = ['foo',,'bar'];");
  }

  @Test
  public void testTrailingCommaWarning3() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.TRAILING_COMMA);
    parseWarning("var a = ['foo', 'bar',];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    parse("var a = ['foo', 'bar',];");
  }

  @Test
  public void testTrailingCommaWarning4() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.TRAILING_COMMA);
    parseWarning("var a = [,];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    parse("var a = [,];");
  }

  @Test
  public void testTrailingCommaWarning5() {
    parse("var a = {'foo': 'bar'};");
  }

  @Test
  public void testTrailingCommaWarning6() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.TRAILING_COMMA);
    parseWarning("var a = {'foo': 'bar',};", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    parse("var a = {'foo': 'bar',};");
  }

  @Test
  public void testTrailingCommaWarning7() {
    parseError("var a = {,};",
        "'}' expected");
  }

  @Test
  public void testSuspiciousBlockCommentWarning1() {
    parseWarning("/* @type {number} */ var x = 3;", SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning2() {
    parseWarning("/* \n * @type {number} */ var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning3() {
    parseWarning("/* \n *@type {number} */ var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning4() {
    parseWarning(
        "  /*\n" +
        "   * @type {number}\n" +
        "   */\n" +
        "  var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning5() {
    parseWarning(
        "  /*\n" +
        "   * some random text here\n" +
        "   * @type {number}\n" +
        "   */\n" +
        "  var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning6() {
    parseWarning("/* @type{number} */ var x = 3;", SUSPICIOUS_COMMENT_WARNING);
  }

  @Test
  public void testSuspiciousBlockCommentWarning7() {
    // jsdoc tags contain letters only, no underscores etc.
    parse("/* @cc_on */ var x = 3;");
  }

  @Test
  public void testSuspiciousBlockCommentWarning8() {
    // a jsdoc tag can't be immediately followed by a paren
    parse("/* @TODO(username) */ var x = 3;");
  }

  @Test
  public void testCatchClauseForbidden() {
    parseError("try { } catch (e if true) {}",
        "')' expected");
  }

  @Test
  public void testConstForbidden() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.CONST_DECLARATIONS);
    parseWarning("const x = 3;",
        getRequiresEs6Message(Feature.CONST_DECLARATIONS));
  }

  @Test
  public void testAnonymousFunctionExpression() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    parseError("function () {}", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("function () {}", "'identifier' expected");

    isIdeMode = true;
    parseError("function () {}", "'identifier' expected", "unnamed function statement");
  }

  @Test
  public void testArrayDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parseWarning("var [x,y] = foo();", getRequiresEs6Message(Feature.ARRAY_DESTRUCTURING));

    mode = LanguageMode.ECMASCRIPT6;
    parse("var [x,y] = foo();");
  }

  @Test
  public void testArrayDestructuringVarInvalid() {
    // arbitrary LHS assignment target not allowed
    parseError(
        "var [x,y[15]] = foo();", "Only an identifier or destructuring pattern is allowed here.");
  }

  @Test
  public void testArrayDestructuringAssign() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parseWarning("[x,y] = foo();", getRequiresEs6Message(Feature.ARRAY_DESTRUCTURING));

    mode = LanguageMode.ECMASCRIPT6;
    parse("[x,y] = foo();");
    // arbitrary LHS assignment target is allowed
    parse("[x,y[15]] = foo();");
  }

  @Test
  public void testArrayDestructuringInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parse("var [x=1,y] = foo();");
    parse("[x=1,y] = foo();");
    parse("var [x,y=2] = foo();");
    parse("[x,y=2] = foo();");

    parse("var [[a] = ['b']] = [];");
    parse("[[a] = ['b']] = [];");
    // arbitrary LHS target allowed in assignment, but not declaration
    parse("[[a.x] = ['b']] = [];");
  }

  @Test
  public void testArrayDestructuringInitializerInvalid() {
    parseError(
        "var [[a.x] = ['b']] = [];",
        "Only an identifier or destructuring pattern is allowed here.");
  }

  @Test
  public void testArrayDestructuringDeclarationRest() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.ARRAY_PATTERN_REST);
    parse("var [first, ...rest] = foo();");
    parse("let [first, ...rest] = foo();");
    parse("const [first, ...rest] = foo();");

    // nested destructuring in regular parameters and rest parameters
    parse("var [first, {a, b}, ...[re, st, ...{length}]] = foo();");

    expectFeatures();
    parseError(
        "var [first, ...more = 'default'] = foo();",
        "A default value cannot be specified after '...'");
    parseError("var [first, ...more, last] = foo();", "']' expected");


    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "var [first, ...rest] = foo();",
        getRequiresEs6Message(Feature.ARRAY_DESTRUCTURING),
        getRequiresEs6Message(Feature.ARRAY_PATTERN_REST));
  }

  @Test
  public void testObjectDestructuringDeclarationRest() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.OBJECT_PATTERN_REST);
    parse("var {first, ...rest} = foo();");
    parse("let {first, ...rest} = foo();");
    parse("const {first, ...rest} = foo();");

    expectFeatures();
    parseError(
        "var {first, ...more = 'default'} = foo();",
        "A default value cannot be specified after '...'");
    parseError("var {first, ...more, last} = foo();", "'}' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning(
        "var {first, ...rest} = foo();",
        getRequiresEs2018Message(Feature.OBJECT_PATTERN_REST));
  }

  @Test
  public void testArrayLiteralDeclarationSpread() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.SPREAD_EXPRESSIONS);
    parse("var o = [first, ...spread];");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "var o = [first, ...spread];",
        getRequiresEs6Message(Feature.SPREAD_EXPRESSIONS));
  }

  @Test
  public void testObjectLiteralDeclarationSpread() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    expectFeatures(Feature.OBJECT_LITERALS_WITH_SPREAD);
    parse("var o = {first: 1, ...spread};");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "var o = {first: 1, ...spread};",
        getRequiresEs6Message(Feature.SPREAD_EXPRESSIONS),
        getRequiresEs2018Message(Feature.OBJECT_LITERALS_WITH_SPREAD));

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning(
        "var o = {first: 1, ...spread};",
        getRequiresEs2018Message(Feature.OBJECT_LITERALS_WITH_SPREAD));
  }

  @Test
  public void testArrayDestructuringAssignRest() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.ARRAY_PATTERN_REST);
    parse("[first, ...rest] = foo();");
    // nested destructuring in regular parameters and rest parameters
    parse("[first, {a, b}, ...[re, st, ...{length}]] = foo();");
    // arbitrary LHS assignment target is allowed
    parse("[x, ...y[15]] = foo();");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "var [first, ...rest] = foo();",
        getRequiresEs6Message(Feature.ARRAY_DESTRUCTURING),
        getRequiresEs6Message(Feature.ARRAY_PATTERN_REST));
  }

  @Test
  public void testObjectDestructuringAssignRest() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.OBJECT_PATTERN_REST);
    parse("const {first, ...rest} = foo();");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("var {first, ...rest} = foo();",
        getRequiresEs2018Message(Feature.OBJECT_PATTERN_REST));
  }

  @Test
  public void testArrayDestructuringAssignRestInvalid() {
    // arbitrary LHS assignment target not allowed
    parseError(
        "var [x, ...y[15]] = foo();",
        "Only an identifier or destructuring pattern is allowed here.");

    parseError(
        "[first, ...more = 'default'] = foo();", "A default value cannot be specified after '...'");
    parseError("var [first, ...more, last] = foo();", "']' expected");
  }

  @Test
  public void testArrayDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parse("function f([x, y]) { use(x); use(y); }");
    parse("function f([x, [y, z]]) {}");
    parse("function f([x, {y, foo: z}]) {}");
    parse("function f([x, y] = [1, 2]) { use(x); use(y); }");
    parse("function f([x, x]) {}");
  }

  @Test
  public void testArrayDestructuringFnDeclarationInvalid() {
    // arbitrary LHS expression not allowed as a formal parameter
    parseError(
        "function f([a[0], x]) {}", "Only an identifier or destructuring pattern is allowed here.");
    // restriction applies to sub-patterns
    parseError(
        "function f([a, [x.foo]]) {}",
        "Only an identifier or destructuring pattern is allowed here.");
    parseError(
        "function f([a, {foo: x.foo}]) {}",
        "Only an identifier or destructuring pattern is allowed here.");
  }

  @Test
  public void testObjectDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("var {x, y} = foo();");
    parse("var {x: x, y: y} = foo();");
    parse("var {x: {y, z}} = foo();");
    parse("var {x: {y: {z}}} = foo();");

    // Useless, but legal.
    parse("var {} = foo();");
  }

  @Test
  public void testObjectDestructuringVarInvalid() {
    // Arbitrary LHS target not allowed in declaration
    parseError("var {x.a, y} = foo();", "'}' expected");
    parseError(
        "var {a: x.a, y} = foo();", "Only an identifier or destructuring pattern is allowed here.");
  }

  @Test
  public void testObjectDestructuringVarWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.DEFAULT_PARAMETERS);
    parse("var {x = 1} = foo();");
    parse("var {x: {y = 1}} = foo();");
    parse("var {x: y = 1} = foo();");
    parse("var {x: v1 = 5, y: v2 = 'str'} = foo();");
    parse("var {k1: {k2 : x} = bar(), k3: y} = foo();");
  }

  @Test
  public void testObjectDestructuringAssign() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("({x, y}) = foo();", "invalid assignment target");
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("({x, y} = foo());");
    parse("({x: x, y: y} = foo());");
    parse("({x: {y, z}} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");

    // Useless, but legal.
    parse("({} = foo());");
  }

  @Test
  public void testObjectDestructuringAssignWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("({x = 1}) = foo();", "invalid assignment target");
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("({x = 1} = foo());");
    parse("({x: {y = 1}} = foo());");
    parse("({x: y = 1} = foo());");
    parse("({x: v1 = 5, y: v2 = 'str'} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");
  }

  @Test
  public void testObjectDestructuringWithInitializerInvalid() {
    parseError("var {{x}} = foo();", "'}' expected");
    parseError("({{x}}) = foo();", "'}' expected");
    parseError("({{a} = {a: 'b'}}) = foo();", "'}' expected");
    parseError("({{a : b} = {a: 'b'}}) = foo();", "'}' expected");
  }

  @Test
  public void testObjectDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("function f({x, y}) { use(x); use(y); }");
    parse("function f({w, x: {y, z}}) {}");
    parse("function f({x, y} = {x:1, y:2}) {}");
    parse("function f({x, x}) {}");
  }

  @Test
  public void testObjectDestructuringFnDeclarationInvalid() {
    // arbitrary LHS expression not allowed as a formal parameter
    parseError("function f({a[0], x}) {}", "'}' expected");
    parseError(
        "function f({foo: a[0], x}) {}",
        "Only an identifier or destructuring pattern is allowed here.");
    // restriction applies to sub-patterns
    parseError(
        "function f({a, foo: [x.foo]}) {}",
        "Only an identifier or destructuring pattern is allowed here.");
    parseError(
        "function f({a, x: {foo: x.foo}}) {}",
        "Only an identifier or destructuring pattern is allowed here.");
  }

  @Test
  public void testObjectDestructuringComputedProp() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("var {[x]} = z;", "':' expected");

    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("var {[x]: y} = z;");
    parse("var { [foo()] : [x,y,z] = bar() } = baz();");
  }

  @Test
  public void testObjectDestructuringStringAndNumberKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("var { 'hello world' } = foo();", "':' expected");
    parseError("var { 4 } = foo();", "':' expected");
    parseError("var { 'hello' = 'world' } = foo();", "':' expected");
    parseError("var { 2 = 5 } = foo();", "':' expected");

    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("var {'s': x} = foo();");
    parse("var {3: x} = foo();");
  }

  /** See https://github.com/google/closure-compiler/issues/1262 */
  @Test
  public void testObjectNumberKeysSpecial() {
    Node n = parse("var a = {12345678901234567890: 2}");

    Node objectLit = n.getFirstChild().getFirstFirstChild();
    assertThat(objectLit.getToken()).isEqualTo(Token.OBJECTLIT);

    Node number = objectLit.getFirstChild();
    assertThat(number.getToken()).isEqualTo(Token.STRING_KEY);
    assertThat(number.getString()).isEqualTo("12345678901234567000");
  }

  @Test
  public void testObjectDestructuringKeywordKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("var {if: x, else: y} = foo();");
    parse("var {while: x=1, for: y} = foo();");
    parse("var {type} = foo();");
    parse("var {declare} = foo();");
    parse("var {module} = foo();");
    parse("var {namespace} = foo();");
  }

  @Test
  public void testObjectDestructuringKeywordKeysInvalid() {
    parseError("var {while} = foo();", "cannot use keyword 'while' here.");
    parseError("var {implements} = foo();", "cannot use keyword 'implements' here.");
  }

  @Test
  public void testObjectDestructuringComplexTarget() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError(
        "var {foo: bar.x} = baz();",
        "Only an identifier or destructuring pattern is allowed here.");

    parseError(
        "var {foo: bar[x]} = baz();",
        "Only an identifier or destructuring pattern is allowed here.");

    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("({foo: bar.x} = baz());");
    parse("for ({foo: bar.x} in baz());");

    parse("({foo: bar[x]} = baz());");
    parse("for ({foo: bar[x]} in baz());");
  }

  @Test
  public void testObjectDestructuringExtraParens() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("({x: y} = z);");
    parse("({x: (y)} = z);");
    parse("({x: ((y))} = z);");

    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parse("([x] = y);");
    parse("[(x), y] = z;");
    parse("[x, (y)] = z;");
  }

  @Test
  public void testObjectDestructuringExtraParensInvalid() {
    parseError("[x, ([y])] = z;", INVALID_ASSIGNMENT_TARGET);
    parseError("[x, (([y]))] = z;", INVALID_ASSIGNMENT_TARGET);
  }

  @Test
  public void testObjectLiteralCannotUseDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("var o = {x = 5}", "Default value cannot appear at top level of an object literal.");
  }

  @Test
  public void testMixedDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.OBJECT_DESTRUCTURING);
    parse("var {x: [y, z]} = foo();");
    parse("var [x, {y, z}] = foo();");

    parse("({x: [y, z]} = foo());");
    parse("[x, {y, z}] = foo();");

    parse("function f({x: [y, z]}) {}");
    parse("function f([x, {y, z}]) {}");
  }

  @Test
  public void testMixedDestructuringWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.OBJECT_DESTRUCTURING);
    parse("var {x: [y, z] = [1, 2]} = foo();");
    parse("var [x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("({x: [y, z] = [1, 2]} = foo());");
    parse("[x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("function f({x: [y, z] = [1, 2]}) {}");
    parse("function f([x, {y, z} = {y: 3, z: 4}]) {}");
  }

  @Test
  public void testDestructuringNoRHS() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("var {x: y};", "destructuring must have an initializer");
    parseError("let {x: y};", "destructuring must have an initializer");
    parseError("const {x: y};", "const variables must have an initializer");
    parseError("var {x};", "destructuring must have an initializer");
    parseError("let {x};", "destructuring must have an initializer");
    parseError("const {x};", "const variables must have an initializer");
    parseError("var [x, y];", "destructuring must have an initializer");
    parseError("let [x, y];", "destructuring must have an initializer");
    parseError("const [x, y];", "const variables must have an initializer");
  }

  @Test
  public void testComprehensions() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    String error = "unsupported language feature:"
        + " array/generator comprehensions";

    // array comprehensions
    parseError("[for (x of y) z];", error);
    parseError("[for ({x,y} of z) x+y];", error);
    parseError("[for (x of y) if (x<10) z];", error);
    parseError("[for (a = 5 of v) a];", "'identifier' expected");

    // generator comprehensions
    parseError("(for (x of y) z);", error);
    parseError("(for ({x,y} of z) x+y);", error);
    parseError("(for (x of y) if (x<10) z);", error);
    parseError("(for (a = 5 of v) a);", "'identifier' expected");
  }

  @Test
  public void testLetForbidden1() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.LET_DECLARATIONS);
    parseWarning("let x = 3;",
        getRequiresEs6Message(Feature.LET_DECLARATIONS));
  }

  @Test
  public void testLetForbidden2() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.LET_DECLARATIONS);
    parseWarning("function f() { let x = 3; };",
        getRequiresEs6Message(Feature.LET_DECLARATIONS));
  }

  @Test
  public void testBlockScopedFunctionDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    parse("{ function foo() {} }");
    parse("if (true) { function foo() {} }");
    parse("{ function* gen() {} }");
    parse("if (true) function foo() {}");
    parse("if (true) function foo() {} else {}");
    parse("if (true) {} else function foo() {}");
    parse("if (true) function foo() {} else function foo() {}");

    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures();
    // Function expressions and functions directly inside other functions do not trigger this
    parse("function foo() {}");
    parse("(function foo() {})");
    parse("function foo() { function bar() {} }");
    parse("{ var foo = function() {}; }");
    parse("{ var foo = function bar() {}; }");
    parse("{ (function() {})(); }");
    parse("{ (function foo() {})(); }");

    parseWarning(
        "{ function f() {} }", getRequiresEs6Message(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION));
  }

  @Test
  public void testLetForbidden3() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;
    parseError("function f() { var let = 3; }",
        "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("function f() { var let = 3; }",
        "'identifier' expected");
  }

  @Test
  public void testYieldForbidden() {
    parseError("function f() { yield 3; }",
        "primary expression expected");
  }

  @Test
  public void testGenerator() {
    expectFeatures(Feature.GENERATORS);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = STRICT;
    parse("var obj = { *f() { yield 3; } };");
    parse("function* f() { yield 3; }");
    parse("function f() { return function* g() {} }");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("function* f() { yield 3; }",
        getRequiresEs6Message(Feature.GENERATORS));
    parseWarning("var obj = { * f() { yield 3; } };",
        getRequiresEs6Message(Feature.GENERATORS),
        getRequiresEs6Message(Feature.MEMBER_DECLARATIONS));
  }

  @Test
  public void testBracelessFunctionForbidden() {
    parseError("var sq = function(x) x * x;",
        "'{' expected");
  }

  @Test
  public void testGeneratorsForbidden() {
    parseError("var i = (x for (x in obj));",
        "')' expected");
  }

  @Test
  public void testGettersForbidden1() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.GETTER);
    parseError("var x = {get foo() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  @Test
  public void testGettersForbidden2() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("var x = {get foo bar() { return 3; }};",
        "'(' expected");
  }

  @Test
  public void testGettersForbidden3() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("var x = {a getter:function b() { return 3; }};",
        "'}' expected");
  }

  @Test
  public void testGettersForbidden4() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("var x = {\"a\" getter:function b() { return 3; }};",
        "':' expected");
  }

  @Test
  public void testGettersForbidden5() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.GETTER);
    parseError("var x = {a: 2, get foo() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  @Test
  public void testGettersForbidden6() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.GETTER);
    parseError("var x = {get 'foo'() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  @Test
  public void testSettersForbidden() {
    mode = LanguageMode.ECMASCRIPT3;
    expectFeatures(Feature.SETTER);
    parseError("var x = {set foo(a) { y = 3; }};",
        IRFactory.SETTER_ERROR_MESSAGE);
  }

  @Test
  public void testSettersForbidden2() {
    mode = LanguageMode.ECMASCRIPT3;
    // TODO(johnlenz): maybe just report the first error, when not in IDE mode?
    parseError("var x = {a setter:function b() { return 3; }};",
        "'}' expected");
  }

  @Test
  public void testFileOverviewJSDoc1() {
    isIdeMode = true;

    Node n = parse("/** @fileoverview Hi mom! */ function Foo() {}");
    assertNode(n.getFirstChild()).hasType(Token.FUNCTION);
    assertThat(n.getJSDocInfo()).isNotNull();
    assertThat(n.getFirstChild().getJSDocInfo()).isNull();
    assertThat(n.getJSDocInfo().getFileOverview()).isEqualTo("Hi mom!");
  }

  @Test
  public void testFileOverviewJSDocDoesNotHoseParsing() {
    assertNode(
            parse("/** @fileoverview Hi mom! \n */ function Foo() {}").getFirstChild())
        .hasType(Token.FUNCTION);
    assertNode(
            parse("/** @fileoverview Hi mom! \n * * * */ function Foo() {}").getFirstChild())
        .hasType(Token.FUNCTION);
    assertNode(parse("/** @fileoverview \n * x */ function Foo() {}").getFirstChild())
        .hasType(Token.FUNCTION);
    assertNode(
            parse("/** @fileoverview \n * x \n */ function Foo() {}").getFirstChild())
        .hasType(Token.FUNCTION);
  }

  @Test
  public void testFileOverviewJSDoc2() {
    isIdeMode = true;

    Node n = parse("/** @fileoverview Hi mom! */"
        + " /** @constructor */ function Foo() {}");
    assertThat(n.getJSDocInfo()).isNotNull();
    assertThat(n.getJSDocInfo().getFileOverview()).isEqualTo("Hi mom!");
    assertThat(n.getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(n.getFirstChild().getJSDocInfo().hasFileOverview()).isFalse();
    assertThat(n.getFirstChild().getJSDocInfo().isConstructor()).isTrue();
  }

  @Test
  public void testImportantComment() {
    isIdeMode = true;

    Node n = parse("/*! Hi mom! */ function Foo() {}");
    assertNode(n.getFirstChild()).hasType(Token.FUNCTION);
    assertThat(n.getJSDocInfo()).isNotNull();
    assertThat(n.getFirstChild().getJSDocInfo()).isNull();
    assertThat(n.getJSDocInfo().getLicense()).isEqualTo(" Hi mom! ");
  }

  @Test
  public void testObjectLiteralDoc1() {
    Node n = parse("var x = {/** @type {number} */ 1: 2};");

    Node objectLit = n.getFirstFirstChild().getFirstChild();
    assertNode(objectLit).hasType(Token.OBJECTLIT);

    Node number = objectLit.getFirstChild();
    assertNode(number).hasType(Token.STRING_KEY);
    assertThat(number.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testDuplicatedParam() {
    parseWarning("function foo(x, x) {}", "Duplicate parameter name \"x\"");
  }

  @Test
  public void testLetAsIdentifier() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT5;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;
    parseError("var let", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = STRICT;
    parseError("var let", "'identifier' expected");
  }

  @Test
  public void testLet() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.LET_DECLARATIONS);

    parse("let x;");
    parse("let x = 1;");
    parse("let x, y = 2;");
    parse("let x = 1, y = 2;");
  }

  @Test
  public void testConst() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("const x;", "const variables must have an initializer");
    parseError("const x, y = 2;", "const variables must have an initializer");

    expectFeatures(Feature.CONST_DECLARATIONS);

    parse("const x = 1;");
    parse("const x = 1, y = 2;");
  }

  @Test
  public void testYield1() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT5;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;
    parseError("var yield", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = STRICT;
    parseError("var yield", "'identifier' expected");
  }

  @Test
  public void testYield2() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = STRICT;
    expectFeatures(Feature.GENERATORS);
    parse("function * f() { yield; }");
    parse("function * f() { yield /a/i; }");

    expectFeatures();
    parseError("function * f() { 1 + yield; }", "primary expression expected");
    parseError("function * f() { 1 + yield 2; }", "primary expression expected");
    parseError("function * f() { yield 1 + yield 2; }", "primary expression expected");
    parseError("function * f() { yield(1) + yield(2); }", "primary expression expected");
    expectFeatures(Feature.GENERATORS);
    parse("function * f() { (yield 1) + (yield 2); }"); // OK
    parse("function * f() { yield * yield; }"); // OK  (yield * (yield))
    expectFeatures();
    parseError("function * f() { yield + yield; }", "primary expression expected");
    expectFeatures(Feature.GENERATORS);
    parse("function * f() { (yield) + (yield); }"); // OK
    parse("function * f() { return yield; }"); // OK
    parse("function * f() { return yield 1; }"); // OK
    parse(LINE_JOINER.join(
        "function * f() {",
        "  yield *", // line break allowed here
        "      [1, 2, 3];",
        "}"));
    expectFeatures();
    parseError(LINE_JOINER.join(
        "function * f() {",
        "  yield", // line break not allowed here
        "      *[1, 2, 3];",
        "}"),
        "'}' expected");
    parseError("function * f() { yield *; }", "yield* requires an expression");
  }

  @Test
  public void testYield3() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = STRICT;
    expectFeatures(Feature.GENERATORS);
    // TODO(johnlenz): validate "yield" parsing. Firefox rejects this
    // use of "yield".
    parseError("function * f() { yield , yield; }");
  }

  @Test
  public void testStringLineContinuationWarningsByMode() {
    String unrecommendedWarning =
        "String continuations are not recommended. See"
            + " https://google.github.io/styleguide/jsguide.html#features-strings-no-line-continuations";

    expectFeatures(Feature.STRING_CONTINUATION);
    strictMode = SLOPPY;

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning(
        "'one\\\ntwo';",
        requiresLanguageModeMessage(LanguageMode.ECMASCRIPT5, Feature.STRING_CONTINUATION),
        unrecommendedWarning);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("'one\\\ntwo';", unrecommendedWarning);

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("'one\\\ntwo';", unrecommendedWarning);
  }

  @Test
  public void testStringLineContinuationNormalization() {
    String unrecommendedWarning =
        "String continuations are not recommended. See"
            + " https://google.github.io/styleguide/jsguide.html#features-strings-no-line-continuations";

    expectFeatures(Feature.STRING_CONTINUATION);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    Node n = parseWarning("'one\\\ntwo';", unrecommendedWarning);
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");

    n = parseWarning("'one\\\rtwo';", unrecommendedWarning);
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");

    n = parseWarning("'one\\\r\ntwo';", unrecommendedWarning);
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");

    n = parseWarning("'one \\\ntwo';", unrecommendedWarning);
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("one two");

    n = parseWarning("'one\\\n two';", unrecommendedWarning);
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("one two");
  }

  @Test
  public void testStringLiteral() {
    Node n = parse("'foo'");
    Node stringNode = n.getFirstFirstChild();
    assertNode(stringNode).hasType(Token.STRING);
    assertThat(stringNode.getString()).isEqualTo("foo");
  }

  private Node testTemplateLiteral(String s) {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    parseWarning(s,
        getRequiresEs6Message(Feature.TEMPLATE_LITERALS));

    mode = LanguageMode.ECMASCRIPT6;
    return parse(s);
  }

  private void assertSimpleTemplateLiteral(String expectedContents, String literal) {
    Node node = testTemplateLiteral(literal).getFirstFirstChild();
    assertNode(node).hasType(Token.TEMPLATELIT);
    assertThat(node.getChildCount()).isEqualTo(1);
    assertNode(node.getFirstChild()).hasType(Token.TEMPLATELIT_STRING);
    assertThat(node.getFirstChild().getCookedString()).isEqualTo(expectedContents);
  }

  @Test
  public void testUseTemplateLiteral() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    testTemplateLiteral("f`hello world`;");
    testTemplateLiteral("`hello ${name} ${world}`.length;");
  }

  @Test
  public void testTemplateLiterals() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    testTemplateLiteral("``");
    testTemplateLiteral("`\"`");
    testTemplateLiteral("`\\\"`");
    testTemplateLiteral("`\\``");
    testTemplateLiteral("`hello world`;");
    testTemplateLiteral("`hello\nworld`;");
    testTemplateLiteral("`string containing \\`escaped\\` backticks`;");
    testTemplateLiteral("{ `in block` }");
    testTemplateLiteral("{ `in ${block}` }");
  }

  @Test
  public void testEscapedTemplateLiteral() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    assertSimpleTemplateLiteral("${escaped}", "`\\${escaped}`");
  }

  @Test
  public void testTemplateLiteralWithNulChar() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    mode = LanguageMode.ECMASCRIPT6;

    strictMode = SLOPPY;
    parse("var test = `\nhello\\0`");

    strictMode = STRICT;
    parse("var test = `\nhello\\0`");
  }

  @Test
  public void testTemplateLiteralWithNewline() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    assertSimpleTemplateLiteral("hello\nworld", "`hello\nworld`");
    assertSimpleTemplateLiteral("\n", "`\r`");
    assertSimpleTemplateLiteral("\n", "`\r\n`");
    assertSimpleTemplateLiteral("\\\n", "`\\\\\n`");
    assertSimpleTemplateLiteral("\\\n", "`\\\\\r\n`");
    assertSimpleTemplateLiteral("\r\n", "`\\r\\n`"); // template literals support explicit escapes
    assertSimpleTemplateLiteral("\\r\\n", "`\\\\r\\\\n`"); // note: no actual newlines here
  }

  @Test
  public void testTemplateLiteralWithLineContinuation() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.TEMPLATE_LITERALS);
    Node n = parseWarning("`string \\\ncontinuation`",
        "String continuations are not recommended. See"
        + " https://google.github.io/styleguide/jsguide.html#features-strings-no-line-continuations");
    Node templateLiteral = n.getFirstFirstChild();
    Node stringNode = templateLiteral.getFirstChild();
    assertNode(stringNode).hasType(Token.TEMPLATELIT_STRING);
    assertThat(stringNode.getCookedString()).isEqualTo("string continuation");
  }

  @Test
  public void testTemplateLiteralSubstitution() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.TEMPLATE_LITERALS);
    parse("`hello ${name}`;");
    parse("`hello ${name} ${world}`;");
    parse("`hello ${name }`");

    expectFeatures();
    parseError("`hello ${name", "Expected '}' after expression in template literal");
    parseError("`hello ${name tail}", "Expected '}' after expression in template literal");
  }

  @Test
  public void testUnterminatedTemplateLiteral() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("`hello",
        "Unterminated template literal");
    parseError("`hello\\`",
        "Unterminated template literal");
  }

  @Test
  public void testTemplateLiteralOctalEscapes() {
    assertSimpleTemplateLiteral("\0", "`\\0`");
    assertSimpleTemplateLiteral("aaa\0aaa", "`aaa\\0aaa`");
  }

  @Test
  public void testIncorrectEscapeSequenceInTemplateLiteral() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("`hello\\x`", "Hex digit expected");
    parseError("`hello\\x`",
        "Hex digit expected");

    parseError("`hello\\1`", "Invalid escape sequence");
    parseError("`hello\\2`", "Invalid escape sequence");
    parseError("`hello\\3`", "Invalid escape sequence");
    parseError("`hello\\4`", "Invalid escape sequence");
    parseError("`hello\\5`", "Invalid escape sequence");
    parseError("`hello\\6`", "Invalid escape sequence");
    parseError("`hello\\7`", "Invalid escape sequence");
    parseError("`hello\\01`", "Invalid escape sequence");
    parseError("`hello\\02`", "Invalid escape sequence");
    parseError("`hello\\03`", "Invalid escape sequence");
    parseError("`hello\\04`", "Invalid escape sequence");
    parseError("`hello\\05`", "Invalid escape sequence");
    parseError("`hello\\06`", "Invalid escape sequence");
    parseError("`hello\\07`", "Invalid escape sequence");
  }

  @Test
  public void testTemplateLiteralSubstitutionWithCast() {
    mode = LanguageMode.ECMASCRIPT6;

    Node root = parse("`${ /** @type {?} */ (3)}`");
    Node exprResult = root.getFirstChild();
    Node templateLiteral = exprResult.getFirstChild();
    assertNode(templateLiteral).hasType(Token.TEMPLATELIT);

    Node substitution = templateLiteral.getSecondChild();
    assertNode(substitution).hasType(Token.TEMPLATELIT_SUB);

    Node cast = substitution.getFirstChild();
    assertNode(cast).hasType(Token.CAST);

    Node number = cast.getFirstChild();
    assertNode(number).hasType(Token.NUMBER);
  }

  @Test
  public void testExponentialLiterals() {
    parse("0e0");
    parse("0E0");
    parse("0E1");
    parse("1E0");
    parse("1E-0");
    parse("10E10");
    parse("10E-10");
    parse("1.0E1");
    parseError("01E0", SEMICOLON_EXPECTED);
    parseError("0E",
        "Exponent part must contain at least one digit");
    parseError("1E-",
        "Exponent part must contain at least one digit");
    parseError("1E1.1", SEMICOLON_EXPECTED);
  }

  @Test
  public void testBinaryLiterals() {
    expectFeatures(Feature.BINARY_LITERALS);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parseWarning("0b0001;", getRequiresEs6Message(Feature.BINARY_LITERALS));
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0b0001;", getRequiresEs6Message(Feature.BINARY_LITERALS));
    mode = LanguageMode.ECMASCRIPT6;
    parse("0b0001;");
  }

  @Test
  public void testOctalLiterals() {
    expectFeatures(Feature.OCTAL_LITERALS);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parseWarning("0o0001;", getRequiresEs6Message(Feature.OCTAL_LITERALS));
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0o0001;", getRequiresEs6Message(Feature.OCTAL_LITERALS));
    mode = LanguageMode.ECMASCRIPT6;
    parse("0o0001;");
  }

  @Test
  public void testOldStyleOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parseWarning("0001;",
        "Octal integer literals are not supported in strict mode.");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0001;",
        "Octal integer literals are not supported in strict mode.");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("0001;",
        "Octal integer literals are not supported in strict mode.");
  }

  @Test
  public void testOldStyleOctalLiterals_strictMode() {
    strictMode = STRICT;

    mode = LanguageMode.ECMASCRIPT5;
    parseError("0001;",
        "Octal integer literals are not supported in strict mode.");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("0001;",
        "Octal integer literals are not supported in strict mode.");
  }

  @Test
  public void testInvalidOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT5;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");
  }

  @Test
  public void testInvalidOldStyleOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;
    parseError("08;",
        "Invalid octal digit in octal literal.");
    parseError("01238;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT5;
    parseError("08;",
        "Invalid octal digit in octal literal.");
    parseError("01238;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("08;",
        "Invalid octal digit in octal literal.");
    parseError("01238;",
        "Invalid octal digit in octal literal.");
  }

  @Test
  public void testGetter_ObjectLiteral_Es3() {
    expectFeatures(Feature.GETTER);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parseError("var x = {get 1(){}};", IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get 'a'(){}};", IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get a(){}};", IRFactory.GETTER_ERROR_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var x = {get 1(){}};");
    parse("var x = {get 'a'(){}};");
    parse("var x = {get a(){}};");
  }

  @Test
  public void testGetter_ObjectLiteral_Es5() {
    expectFeatures(Feature.GETTER);
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;

    parse("var x = {get 1(){}};");
    parse("var x = {get 'a'(){}};");
    parse("var x = {get a(){}};");
  }

  @Test
  public void testGetterInvalid_ObjectLiteral_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("var x = {get a(b){}};", "')' expected");
  }

  @Test
  public void testGetter_Computed_ObjectLiteral_Es6() {
    expectFeatures(Feature.GETTER, Feature.COMPUTED_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("var x = {get [1](){}};");
    parse("var x = {get ['a'](){}};");
    parse("var x = {get [a](){}};");
  }

  @Test
  public void testGetterInvalid_Computed_ObjectLiteral_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("var x = {get [a](b){}};", "')' expected");
  }

  @Test
  public void testGetter_ClassSyntax() {
    expectFeatures(Feature.CLASSES, Feature.GETTER);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("class Foo { get 1() {} };");
    parse("class Foo { get 'a'() {} };");
    parse("class Foo { get a() {} };");
  }

  @Test
  public void testGetterInvalid_ClassSyntax_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("class Foo { get a(b) {} };", "')' expected");
  }

  @Test
  public void testGetter_Computed_ClassSyntax() {
    expectFeatures(Feature.CLASSES, Feature.GETTER, Feature.COMPUTED_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("class Foo { get [1]() {} };");
    parse("class Foo { get ['a']() {} };");
    parse("class Foo { get [a]() {} };");
  }

  @Test
  public void testGetterInvalid_Computed_ClassSyntax_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("class Foo { get [a](b) {} };", "')' expected");
  }

  @Test
  public void testSetter_ObjectLiteral_Es3() {
    expectFeatures(Feature.SETTER);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parseError("var x = {set 1(x){}};", IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set 'a'(x){}};", IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set a(x){}};", IRFactory.SETTER_ERROR_MESSAGE);
  }

  @Test
  public void testSetter_ObjectLiteral_Es5() {
    expectFeatures(Feature.SETTER);
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;

    parse("var x = {set 1(x){}};");
    parse("var x = {set 'a'(x){}};");
    parse("var x = {set a(x){}};");
  }

  // We only cover some of the common permutations though.
  @Test
  public void testSetter_ObjectLiteral_Es6() {
    expectFeatures(Feature.SETTER);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("var x = {set 1(x){}};");
    parse("var x = {set 'a'(x){}};");
    parse("var x = {set a(x){}};");

    parse("var x = {set setter(x = 5) {}};");
    parse("var x = {set setter(x = a) {}};");
    parse("var x = {set setter(x = a + 5) {}};");

    parse("var x = {set setter([x, y, z]) {}};");
    parse("var x = {set setter([x, y, ...z]) {}};");
    parse("var x = {set setter([x, y, z] = [1, 2, 3]) {}};");
    parse("var x = {set setter([x = 1, y = 2, z = 3]) {}};");

    parse("var x = {set setter({x, y, z}) {}};");
    parse("var x = {set setter({x, y, z} = {x: 1, y: 2, z: 3}) {}};");
    parse("var x = {set setter({x = 1, y = 2, z = 3}) {}};");
  }

  @Test
  public void testSetterInvalid_ObjectLiteral_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("var x = {set a() {}};", "Setter must have exactly 1 parameter, found 0");
    parseError("var x = {set a(x, y) {}};", "Setter must have exactly 1 parameter, found 2");
    parseError("var x = {set a(...x, y) {}};", "Setter must have exactly 1 parameter, found 2");
    parseError("var x = {set a(...x) {}};", "Setter must not have a rest parameter");
  }

  // We only cover some of the common permutations though.
  @Test
  public void testSetter_Computed_ObjectLiteral_Es6() {
    expectFeatures(Feature.SETTER, Feature.COMPUTED_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("var x = {set [setter](x = 5) {}};");
    parse("var x = {set [setter](x = a) {}};");
    parse("var x = {set [setter](x = a + 5) {}};");

    parse("var x = {set [setter]([x, y, z]) {}};");
    parse("var x = {set [setter]([x, y, ...z]) {}};");
    parse("var x = {set [setter]([x, y, z] = [1, 2, 3]) {}};");
    parse("var x = {set [setter]([x = 1, y = 2, z = 3]) {}};");

    parse("var x = {set [setter]({x, y, z}) {}};");
    parse("var x = {set [setter]({x, y, z} = {x: 1, y: 2, z: 3}) {}};");
    parse("var x = {set [setter]({x = 1, y = 2, z = 3}) {}};");
  }

  // We only cover some of the common permutations though.
  @Test
  public void testSetterInvalid_Computed_ObjectLiteral_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("var x = {set [setter]() {}};", "Setter must have exactly 1 parameter, found 0");
    parseError("var x = {set [setter](x, y) {}};", "Setter must have exactly 1 parameter, found 2");
    parseError(
        "var x = {set [setter](...x, y) {}};", "Setter must have exactly 1 parameter, found 2");
    parseError("var x = {set [setter](...x) {}};", "Setter must not have a rest parameter");
  }

  @Test
  public void testSetter_ClassSyntax() {
    expectFeatures(Feature.CLASSES, Feature.SETTER);
    mode = LanguageMode.ECMASCRIPT6; // We only cover some of the common permutations though.

    parse("class Foo { set setter(x = 5) {} };");
    parse("class Foo { set setter(x = a) {} };");
    parse("class Foo { set setter(x = a + 5) {} };");

    parse("class Foo { set setter([x, y, z]) {} };");
    parse("class Foo { set setter([x, y, ...z]) {}};");
    parse("class Foo { set setter([x, y, z] = [1, 2, 3]) {} };");
    parse("class Foo { set setter([x = 1, y = 2, z = 3]) {} };");

    parse("class Foo { set setter({x, y, z}) {}};");
    parse("class Foo { set setter({x, y, z} = {x: 1, y: 2, z: 3}) {} };");
    parse("class Foo { set setter({x = 1, y = 2, z = 3}) {} };");
  }

  @Test
  public void testSetterInvalid_ClassSyntax_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;

    parseError("class Foo { set setter() {} };", "Setter must have exactly 1 parameter, found 0");
    parseError(
        "class Foo { set setter(x, y) {} };", "Setter must have exactly 1 parameter, found 2");
    parseError(
        "class Foo { set setter(...x, y) {} };", "Setter must have exactly 1 parameter, found 2");
    parseError("class Foo { set setter(...x) {} };", "Setter must not have a rest parameter");
  }

  // We only cover some of the common permutations though.
  @Test
  public void testSetter_Computed_ClassSyntax() {
    expectFeatures(Feature.CLASSES, Feature.SETTER, Feature.COMPUTED_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;

    parse("class Foo { set [setter](x = 5) {} };");
    parse("class Foo { set [setter](x = a) {} };");
    parse("class Foo { set [setter](x = a + 5) {} };");

    parse("class Foo { set [setter]([x, y, z]) {} };");
    parse("class Foo { set [setter]([x, y, ...z]) {}};");
    parse("class Foo { set [setter]([x, y, z] = [1, 2, 3]) {} };");
    parse("class Foo { set [setter]([x = 1, y = 2, z = 3]) {} };");

    parse("class Foo { set [setter]({x, y, z}) {}};");
    parse("class Foo { set [setter]({x, y, z} = {x: 1, y: 2, z: 3}) {} };");
    parse("class Foo { set [setter]({x = 1, y = 2, z = 3}) {} };");
  }

  @Test
  public void testSetterInvalid_Computed_ClassSyntax_EsNext() {
    expectFeatures();
    mode = LanguageMode.ES_NEXT;

    parseError("class Foo { set [setter]() {} };", "Setter must have exactly 1 parameter, found 0");
    parseError(
        "class Foo { set [setter](x, y) {} };", "Setter must have exactly 1 parameter, found 2");
    parseError(
        "class Foo { set [setter](...x, y) {} };", "Setter must have exactly 1 parameter, found 2");
    parseError("class Foo { set [setter](...x) {} };", "Setter must not have a rest parameter");
  }

  @Test
  public void testLamestWarningEver() {
    // This used to be a warning.
    parse("var x = /** @type {undefined} */ (y);");
    parse("var x = /** @type {void} */ (y);");
  }

  @Test
  public void testUnfinishedComment() {
    parseError("/** this is a comment ", "unterminated comment");
  }

  @Test
  public void testHtmlStartCommentAtStartOfLine() {
    parseWarning("<!-- This text is ignored.\nalert(1)", HTML_COMMENT_WARNING);
  }

  @Test
  public void testHtmlStartComment() {
    parseWarning("alert(1) <!-- This text is ignored.\nalert(2)",
        HTML_COMMENT_WARNING);
  }

  @Test
  public void testHtmlEndCommentAtStartOfLine() {
    parseWarning("alert(1)\n --> This text is ignored.", HTML_COMMENT_WARNING);
  }

  // "-->" is not the start of a comment, when it is not at the beginning
  // of a line.
  @Test
  public void testHtmlEndComment() {
    parse("while (x --> 0) {\n  alert(1)\n}");
  }

  @Test
  public void testParseBlockDescription() {
    isIdeMode = true;

    Node n = parse("/** This is a variable. */ var x;");
    Node var = n.getFirstChild();
    assertThat(var.getJSDocInfo()).isNotNull();
    assertThat(var.getJSDocInfo().getBlockDescription()).isEqualTo("This is a variable.");
  }

  @Test
  public void testUnnamedFunctionStatement() {
    // Statements
    parseError("function() {};", "'identifier' expected");
    parseError("if (true) { function() {}; }", "'identifier' expected");
    parse("function f() {};");
    // Expressions
    parse("(function f() {});");
    parse("(function () {});");
  }

  @Test
  public void testReservedKeywords() {
    expectFeatures(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parseError("var boolean;", "identifier is a reserved word");
    parseError("function boolean() {};",
        "identifier is a reserved word");
    parseError("boolean = 1;", "identifier is a reserved word");

    expectFeatures();
    parseError("class = 1;", "'identifier' expected");
    parseError("public = 2;", "primary expression expected");

    mode = LanguageMode.ECMASCRIPT5;

    expectFeatures(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
    parse("var boolean;");
    parse("function boolean() {};");
    parse("boolean = 1;");

    expectFeatures();
    parseError("class = 1;", "'identifier' expected");
    parseError("var import = 0;", "'identifier' expected");
    // TODO(johnlenz): reenable
    //parse("public = 2;");

    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;

    expectFeatures(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
    parse("var boolean;");
    parse("function boolean() {};");
    parse("boolean = 1;");

    expectFeatures();
    parseError("public = 2;", "primary expression expected");
    parseError("class = 1;", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("const else = 1;", "'identifier' expected");
  }

  @Test
  public void testTypeScriptKeywords() {
    parse("type = 2;");
    parse("var type = 3;");
    parse("type\nx = 5");
    parse("while (i--) { type = types[i]; }");

    parse("declare = 2;");
    parse("var declare = 3;");
    parse("declare\nx = 5");
    parse("while (i--) { declare = declares[i]; }");

    parse("module = 2;");
    parse("var module = 3;");
    parse("module\nx = 5");
    parse("while (i--) { module = module[i]; }");
  }

  @Test
  public void testKeywordsAsProperties1() {
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parseWarning("var x = {function: 1};", IRFactory.INVALID_ES3_PROP_NAME);
    parseWarning("x.function;", IRFactory.INVALID_ES3_PROP_NAME);
    parseWarning("var x = {class: 1};", IRFactory.INVALID_ES3_PROP_NAME);
    expectFeatures();
    parse("var x = {'class': 1};");
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
    parseWarning("x.class;", IRFactory.INVALID_ES3_PROP_NAME);
    expectFeatures();
    parse("x['class'];");
    parse("var x = {let: 1};");  // 'let' is not reserved in ES3
    parse("x.let;");
    parse("var x = {yield: 1};"); // 'yield' is not reserved in ES3
    parse("x.yield;");
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
    parseWarning("x.prototype.catch = function() {};",
        IRFactory.INVALID_ES3_PROP_NAME);
    parseWarning("x().catch();", IRFactory.INVALID_ES3_PROP_NAME);

    mode = LanguageMode.ECMASCRIPT5;

    parse("var x = {function: 1};");
    parse("x.function;");
    parse("var x = {get function(){} };");
    parse("var x = {set function(a){} };");
    parse("var x = {class: 1};");
    parse("x.class;");
    expectFeatures();
    parse("var x = {let: 1};");
    parse("x.let;");
    parse("var x = {yield: 1};");
    parse("x.yield;");
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
    parse("x.prototype.catch = function() {};");
    parse("x().catch();");

    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;

    parse("var x = {function: 1};");
    parse("x.function;");
    parse("var x = {get function(){} };");
    parse("var x = {set function(a){} };");
    parse("var x = {class: 1};");
    parse("x.class;");
    expectFeatures();
    parse("var x = {let: 1};");
    parse("x.let;");
    parse("var x = {yield: 1};");
    parse("x.yield;");
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
    parse("x.prototype.catch = function() {};");
    parse("x().catch();");
  }

  @Test
  public void testKeywordsAsProperties2() {
    mode = LanguageMode.ECMASCRIPT5;

    parse("var x = {get 'function'(){} };");
    parse("var x = {get 1(){} };");
    parse("var x = {set 'function'(a){} };");
    parse("var x = {set 1(a){} };");
  }

  @Test
  public void testKeywordsAsProperties3() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = STRICT;

    parse("var x = {get 'function'(){} };");
    parse("var x = {get 1(){} };");
    parse("var x = {set 'function'(a){} };");
    parse("var x = {set 1(a){} };");
  }

  @Test
  public void testKeywordsAsPropertiesInExterns1() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parse("/** @fileoverview\n@externs\n*/\n var x = {function: 1};");
  }

  @Test
  public void testKeywordsAsPropertiesInExterns2() {
    mode = LanguageMode.ECMASCRIPT3;
    strictMode = SLOPPY;

    parse("/** @fileoverview\n@externs\n*/\n var x = {}; x.function + 1;");
  }

  @Test
  public void testUnicodeInIdentifiers() {
    parse("var \\u00fb");
    parse("var \\u00fbtest\\u00fb");
    parse("Js\\u00C7ompiler");
    parse("Js\\u0043ompiler");
    parse("if(true){foo=\\u03b5}");
    parse("if(true){foo=\\u03b5}else bar()");
  }

  @Test
  public void testUnicodePointEscapeInIdentifiers() {
    parse("var \\u{0043}");
    parse("var \\u{0043}test\\u{0043}");
    parse("var \\u0043test\\u{0043}");
    parse("var \\u{0043}test\\u0043");
    parse("Js\\u{0043}ompiler");
    parse("Js\\u{765}ompiler");
    parse("var \\u0043;{43}");
  }

  @Test
  public void testUnicodePointEscapeStringLiterals() {
    parse("var i = \'\\u0043ompiler\'");
    parse("var i = \'\\u{43}ompiler\'");
    parse("var i = \'\\u{1f42a}ompiler\'");
    parse("var i = \'\\u{2603}ompiler\'");
    parse("var i = \'\\u{1}ompiler\'");
  }

  @Test
  public void testUnicodePointEscapeTemplateLiterals() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var i = `\\u0043ompiler`");
    parse("var i = `\\u{43}ompiler`");
    parse("var i = `\\u{1f42a}ompiler`");
    parse("var i = `\\u{2603}ompiler`");
    parse("var i = `\\u{1}ompiler`");
  }

  @Test
  public void testInvalidUnicodePointEscapeInIdentifiers() {
    parseError("var \\u{defg", "Invalid escape sequence");
    parseError("var \\u{03b5", "Invalid escape sequence");
    parseError("var \\u43{43}", "Invalid escape sequence");
    parseError("var \\u{defgRestOfIdentifier", "Invalid escape sequence");
    parseError("var \\u03b5}", "primary expression expected");
    parseError("var \\u{03b5}}}", "primary expression expected");
    parseError("var \\u{03b5}{}", SEMICOLON_EXPECTED);
    parseError("var \\u0043{43}", SEMICOLON_EXPECTED);
    parseError("var \\u{DEFG}", "Invalid escape sequence");
    parseError("Js\\u{}ompiler", "Invalid escape sequence");
    // Legal unicode but invalid in identifier
    parseError("Js\\u{99}ompiler", "Invalid escape sequence");
    parseError("Js\\u{10000}ompiler", "Invalid escape sequence");
  }

  @Test
  public void testInvalidUnicodePointEscapeStringLiterals() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("var i = \'\\u{defg\'", "Hex digit expected");
    parseError("var i = \'\\u{defgRestOfIdentifier\'", "Hex digit expected");
    parseError("var i = \'\\u{DEFG}\'", "Hex digit expected");
    parseError("var i = \'Js\\u{}ompiler\'", "Empty unicode escape");
    parseError("var i = \'\\u{345", "Hex digit expected");
    parseError("var i = \'\\u{110000}\'", "Undefined Unicode code-point");
  }

  @Test
  public void testInvalidUnicodePointEscapeTemplateLiterals() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("var i = `\\u{defg`", "Hex digit expected");
    parseError("var i = `\\u{defgRestOfIdentifier`", "Hex digit expected");
    parseError("var i = `\\u{DEFG}`", "Hex digit expected");
    parseError("var i = `Js\\u{}ompiler`", "Empty unicode escape");
    parseError("var i = `\\u{345`", "Hex digit expected");
    parseError("var i = `\\u{110000}`", "Undefined Unicode code-point");
  }

  @Test
  public void testEs2018LiftIllegalEscapeSequenceRestrictionOnTaggedTemplates() {
    // These should not generate errors, even though they contain illegal escape sequences.
    // https://github.com/tc39/proposal-template-literal-revision
    parse("latex`\\unicode`");
    parse("foo`\\xerxes`");
    parse("bar`\\u{h}ere`");
    parse("bar`\\u{43`");

    // tagged malformed template literal throws error
    parseError("foo`\\unicode", "Unterminated template literal");
    // normal template literals still throw error
    parseError("var bad = `\\unicode`;", "Hex digit expected");
  }

  @Test
  public void testInvalidEscape() {
    parseError("var \\x39abc", "Invalid escape sequence");
    parseError("var abc\\t", "Invalid escape sequence");
  }

  @Test
  public void testUnnecessaryEscape() {
    parseWarning("var str = '\\a'", "Unnecessary escape: '\\a' is equivalent to just 'a'");
    parse("var str = '\\b'");
    parseWarning("var str = '\\c'", "Unnecessary escape: '\\c' is equivalent to just 'c'");
    parseWarning("var str = '\\d'", "Unnecessary escape: '\\d' is equivalent to just 'd'");
    parseWarning("var str = '\\e'", "Unnecessary escape: '\\e' is equivalent to just 'e'");
    parse("var str = '\\f'");
    parse("var str = '\\/'");
    parse("var str = '\\0'");
    parseWarning("var str = '\\1'", "Unnecessary escape: '\\1' is equivalent to just '1'");
    parseWarning("var str = '\\2'", "Unnecessary escape: '\\2' is equivalent to just '2'");
    parseWarning("var str = '\\3'", "Unnecessary escape: '\\3' is equivalent to just '3'");
    parseWarning("var str = '\\4'", "Unnecessary escape: '\\4' is equivalent to just '4'");
    parseWarning("var str = '\\5'", "Unnecessary escape: '\\5' is equivalent to just '5'");
    parseWarning("var str = '\\6'", "Unnecessary escape: '\\6' is equivalent to just '6'");
    parseWarning("var str = '\\7'", "Unnecessary escape: '\\7' is equivalent to just '7'");
    parseWarning("var str = '\\8'", "Unnecessary escape: '\\8' is equivalent to just '8'");
    parseWarning("var str = '\\9'", "Unnecessary escape: '\\9' is equivalent to just '9'");
    parseWarning("var str = '\\%'", "Unnecessary escape: '\\%' is equivalent to just '%'");

    parseWarning("var str = '\\$'", "Unnecessary escape: '\\$' is equivalent to just '$'");
  }

  @Test
  public void testUnnecessaryEscapeTemplateLiterals() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.TEMPLATE_LITERALS);

    // Don't warn for unnecessary escapes in template literals since tagged template literals
    // can access the raw string value
    parse("var str = `\\a`");
    parse("var str = `\\b`");
    parse("var str = `\\c`");
    parse("var str = `\\d`");
    parse("var str = `\\e`");
    parse("var str = `\\f`");
    parse("var str = `\\/`");
    parse("var str = `\\0`");
    parse("var str = `\\8`");
    parse("var str = `\\9`");
    parse("var str = `\\%`");
    parse("var str = `\\$`");
  }

  @Test
  public void testEOFInUnicodeEscape() {
    parseError("var \\u1", "Invalid escape sequence");
    parseError("var \\u12", "Invalid escape sequence");
    parseError("var \\u123", "Invalid escape sequence");
  }

  @Test
  public void testEndOfIdentifierInUnicodeEscape() {
    parseError("var \\u1 = 1;", "Invalid escape sequence");
    parseError("var \\u12 = 2;", "Invalid escape sequence");
    parseError("var \\u123 = 3;", "Invalid escape sequence");
  }

  @Test
  public void testInvalidUnicodeEscape() {
    parseError("var \\uDEFG", "Invalid escape sequence");
  }

  @Test
  public void testUnicodeEscapeInvalidIdentifierStart() {
    parseError("var \\u0037yler",
        "Character '7' (U+0037) is not a valid identifier start char");
    parseError("var \\u{37}yler",
        "Character '7' (U+0037) is not a valid identifier start char");
    parseError("var \\u0020space",
        "Invalid escape sequence");
  }

  @Test
  public void testUnicodeEscapeInvalidIdentifierChar() {
    parseError("var sp\\u0020ce",
        "Invalid escape sequence");
  }

  /**
   * It is illegal to use a keyword as an identifier, even if you use unicode escapes to obscure the
   * fact that you are trying do that.
   */
  @Test
  public void testKeywordAsIdentifier() {
    parseError("var while;", "'identifier' expected");
    parseError("var wh\\u0069le;", "'identifier' expected");
  }

  @Test
  public void testGetPropFunctionName() {
    parseError("function a.b() {}",
        "'(' expected");
    parseError("var x = function a.b() {}",
        "'(' expected");
  }

  @Test
  public void testIdeModePartialTree() {
    Node partialTree = parseError("function Foo() {} f.",
        "'identifier' expected");
    assertThat(partialTree).isNull();

    isIdeMode = true;
    partialTree = parseError("function Foo() {} f.",
        "'identifier' expected");
    assertThat(partialTree).isNotNull();
  }

  @Test
  public void testForEach() {
    parseError(
        "function f(stamp, status) {\n" +
        "  for each ( var curTiming in this.timeLog.timings ) {\n" +
        "    if ( curTiming.callId == stamp ) {\n" +
        "      curTiming.flag = status;\n" +
        "      break;\n" +
        "    }\n" +
        "  }\n" +
        "};",
        "'(' expected");
  }

  @Test
  public void testValidTypeAnnotation1() {
    parse("/** @type {string} */ var o = 'str';");
    parse("var /** @type {string} */ o = 'str', /** @type {number} */ p = 0;");
    parse("/** @type {function():string} */ function o() { return 'str'; }");
    parse("var o = {}; /** @type {string} */ o.prop = 'str';");
    parse("var o = {}; /** @type {string} */ o['prop'] = 'str';");
    parse("var o = { /** @type {string} */ prop : 'str' };");
    parse("var o = { /** @type {string} */ 'prop' : 'str' };");
    parse("var o = { /** @type {string} */ 1 : 'str' };");
  }

  @Test
  public void testValidTypeAnnotation2() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    expectFeatures(Feature.GETTER);
    parse("var o = { /** @type {string} */ get prop() { return 'str' }};");
    expectFeatures(Feature.SETTER);
    parse("var o = { /** @type {string} */ set prop(s) {}};");
  }

  @Test
  public void testValidTypeAnnotation3() {
    // This one we don't currently support in the type checker but
    // we would like to.
    parse("try {} catch (/** @type {Error} */ e) {}");
  }

  @Test
  public void testValidTypeAnnotation4() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.MODULES);
    parse("/** @type {number} */ export var x = 3;");
  }

  @Test
  public void testParsingAssociativity() {
    assertNodeEquality(parse("x * y * z"), parse("(x * y) * z"));
    assertNodeEquality(parse("x + y + z"), parse("(x + y) + z"));
    assertNodeEquality(parse("x | y | z"), parse("(x | y) | z"));
    assertNodeEquality(parse("x & y & z"), parse("(x & y) & z"));
    assertNodeEquality(parse("x ^ y ^ z"), parse("(x ^ y) ^ z"));
    assertNodeEquality(parse("x || y || z"), parse("(x || y) || z"));
    assertNodeEquality(parse("x && y && z"), parse("(x && y) && z"));
  }

  @Test
  public void testIssue1116() {
    parse("/**/");
  }

  @Test
  public void testUnterminatedStringLiteral() {
    parseError("var unterm = 'forgot closing quote",
        "Unterminated string literal");

    parseError("var unterm = 'forgot closing quote\n"
        + "alert(unterm);",
        "Unterminated string literal");
  }

  /** @bug 14231379 */
  @Test
  public void testUnterminatedRegExp() {
    parseError("var unterm = /forgot trailing slash",
        "Expected '/' in regular expression literal");

    parseError("var unterm = /forgot trailing slash\n" +
        "alert(unterm);",
        "Expected '/' in regular expression literal");
  }

  @Test
  public void testRegExp() {
    assertNodeEquality(parse("/a/"), script(expr(regex("a"))));
    assertNodeEquality(parse("/\\\\/"), script(expr(regex("\\\\"))));
    assertNodeEquality(parse("/\\s/"), script(expr(regex("\\s"))));
    assertNodeEquality(parse("/\\u000A/"), script(expr(regex("\\u000A"))));
    assertNodeEquality(parse("/[\\]]/"), script(expr(regex("[\\]]"))));
  }

  @Test
  public void testRegExpError() {
    parseError("/a\\/", "Expected '/' in regular expression literal");
    parseError("/\\ca\\/", "Expected '/' in regular expression literal");
    parseError("/\b.\\/", "Expected '/' in regular expression literal");
  }

  @Test
  public void testRegExpFlags() {
    // Various valid combinations.
    parse("/a/");
    parse("/a/i");
    parse("/a/g");
    parse("/a/m");
    parse("/a/ig");
    parse("/a/gm");
    parse("/a/mgi");

    // Invalid combinations
    parseError("/a/a", "Invalid RegExp flag 'a'");
    parseError("/a/b", "Invalid RegExp flag 'b'");
    parseError("/a/abc",
        "Invalid RegExp flag 'a'",
        "Invalid RegExp flag 'b'",
        "Invalid RegExp flag 'c'");
  }

  /** New RegExp flags added in ES6. */
  @Test
  public void testES6RegExpFlags() {
    expectFeatures(Feature.REGEXP_FLAG_Y);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("/a/y");
    expectFeatures(Feature.REGEXP_FLAG_U);
    parse("/a/u");

    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.REGEXP_FLAG_Y);
    parseWarning("/a/y",
        getRequiresEs6Message(Feature.REGEXP_FLAG_Y));
    expectFeatures(Feature.REGEXP_FLAG_U);
    parseWarning("/a/u",
        getRequiresEs6Message(Feature.REGEXP_FLAG_U));
    parseWarning("/a/yu",
        getRequiresEs6Message(Feature.REGEXP_FLAG_Y),
        getRequiresEs6Message(Feature.REGEXP_FLAG_U));
  }

  /** New RegExp flag 's' added in ES2018. */
  @Test
  public void testES2018RegExpFlagS() {
    expectFeatures(Feature.REGEXP_FLAG_S);
    mode = LanguageMode.ECMASCRIPT_2018;
    parse("/a/s");

    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.REGEXP_FLAG_S);
    parseWarning("/a/s", getRequiresEs2018Message(Feature.REGEXP_FLAG_S));
    parseWarning(
        "/a/us", // 'u' added in es6
        getRequiresEs2018Message(Feature.REGEXP_FLAG_S));

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "/a/us", // 'u' added in es6
        getRequiresEs6Message(Feature.REGEXP_FLAG_U),
        getRequiresEs2018Message(Feature.REGEXP_FLAG_S));
  }

  @Test
  public void testDefaultParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.DEFAULT_PARAMETERS);
    parse("function f(a, b=0) {}");
    parse("function f(a, b=0, c) {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("function f(a, b=0) {}",
        getRequiresEs6Message(Feature.DEFAULT_PARAMETERS));
  }

  @Test
  public void testDefaultParameterInlineJSDoc() {
    expectFeatures(Feature.DEFAULT_PARAMETERS);
    Node functionNode = parse("function f(/** number */ a = 0) {}").getFirstChild();
    Node parameterList = functionNode.getSecondChild();
    Node defaultValue = parameterList.getFirstChild();
    assertNode(defaultValue).hasType(Token.DEFAULT_VALUE);

    Node aName = defaultValue.getFirstChild();
    assertNode(aName).hasType(Token.NAME);
    assertNodeHasJSDocInfoWithJSType(aName, NUMBER_TYPE);
  }

  @Test
  public void testRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("(...xs, x) => xs", "')' expected");
    parseError(
        "function f(...a[0]) {}", "Only an identifier or destructuring pattern is allowed here.");

    expectFeatures(Feature.REST_PARAMETERS);
    parse("function f(...b) {}");
    parse("(...xs) => xs");
    parse("(x, ...xs) => xs");
    parse("(x, y, ...xs) => xs");
  }

  @Test
  public void testDestructuredRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError(
        "function f(...[a[0]]) {}", "Only an identifier or destructuring pattern is allowed here.");

    expectFeatures(Feature.REST_PARAMETERS, Feature.ARRAY_DESTRUCTURING);
    parse("(...[x]) => xs");
    parse("(...[x, y]) => xs");
    parse("(a, b, c, ...[x, y, z]) => x");
  }

  @Test
  public void testRestParameters_ES5() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    expectFeatures(Feature.REST_PARAMETERS);
    parseWarning("function f(...b) {}",
        getRequiresEs6Message(Feature.REST_PARAMETERS));
  }

  @Test
  public void testExpressionsThatLookLikeParameters1() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("();", "invalid parenthesized expression");
    parseError("(...xs);", "invalid parenthesized expression");
    parseError("(x, ...xs);", "A rest parameter must be in a parameter list.");
    parseError("(a, b, c, ...xs);", "A rest parameter must be in a parameter list.");
  }

  @Test
  public void testExpressionsThatLookLikeParameters2() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("!()", "invalid parenthesized expression");
    parseError("().method", "invalid parenthesized expression");
    parseError("() || a", "invalid parenthesized expression");
    parseError("() && a", "invalid parenthesized expression");
    parseError("x = ()", "invalid parenthesized expression");
  }

  @Test
  public void testExpressionsThatLookLikeParameters3() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("!(...x)", "invalid parenthesized expression");
    parseError("(...x).method", "invalid parenthesized expression");
    parseError("(...x) || a", "invalid parenthesized expression");
    parseError("(...x) && a", "invalid parenthesized expression");
    parseError("x = (...x)", "invalid parenthesized expression");
  }

  @Test
  public void testDefaultParametersWithRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("function f(a=1, ...b=3) {}", "A default value cannot be specified after '...'");

    expectFeatures(Feature.DEFAULT_PARAMETERS, Feature.REST_PARAMETERS);
    parse("function f(a=0, ...b) {}");
    parse("function f(a, b=0, ...c) {}");
    parse("function f(a, b=0, c=1, ...d) {}");
  }

  @Test
  public void testClass1() {
    expectFeatures(Feature.CLASSES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("class C {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("class C {}",
        getRequiresEs6Message(Feature.CLASSES));

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("class C {}",
        getRequiresEs6Message(Feature.CLASSES));

  }

  @Test
  public void testClass2() {
    expectFeatures(Feature.CLASSES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("class C {}");

    parse("class C {\n" +
          "  member() {}\n" +
          "  get field() {}\n" +
          "  set field(a) {}\n" +
          "}\n");

    parse("class C {\n" +
        "  static member() {}\n" +
        "  static get field() {}\n" +
        "  static set field(a) {}\n" +
        "}\n");
  }

  @Test
  public void testClass3() {
    expectFeatures(Feature.CLASSES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse("class C {\n" +
          "  member() {};\n" +
          "  get field() {};\n" +
          "  set field(a) {};\n" +
          "}\n");

    parse("class C {\n" +
        "  static member() {};\n" +
        "  static get field() {};\n" +
        "  static set field(a) {};\n" +
        "}\n");
  }

  @Test
  public void testClassKeywordsAsMethodNames() {
    expectFeatures(Feature.CLASSES, Feature.KEYWORDS_AS_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(Joiner.on('\n').join(
        "class KeywordMethods {",
        "  continue() {}",
        "  throw() {}",
        "  else() {}",
        "}"));
  }

  @Test
  public void testClassReservedWordsAsMethodNames() {
    expectFeatures(Feature.CLASSES, Feature.KEYWORDS_AS_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parse(
        LINE_JOINER.join(
            "class C {",
            "  import() {};",
            "  get break() {};",
            "  set break(a) {};",
            "}"));

    parse(
        LINE_JOINER.join(
            "class C {",
            "  static import() {};",
            "  static get break() {};",
            "  static set break(a) {};",
            "}"));
  }

  @Test
  public void testSuper1() {
    expectFeatures(Feature.SUPER);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    // TODO(johnlenz): super in global scope should be a syntax error
    parse("super;");

    parse("function f() {super;};");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("super;",
        getRequiresEs6Message(Feature.SUPER));

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("super;",
        getRequiresEs6Message(Feature.SUPER));
  }

  @Test
  public void testNewTarget() {
    expectFeatures(Feature.NEW_TARGET);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("new.target;", "new.target must be inside a function");

    parse("function f() { new.target; };");

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning(
        "class C { f() { new.target; } }",
        getRequiresEs6Message(Feature.CLASSES),
        getRequiresEs6Message(Feature.MEMBER_DECLARATIONS),
        getRequiresEs6Message(Feature.NEW_TARGET));

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "class C { f() { new.target; } }",
        getRequiresEs6Message(Feature.CLASSES),
        getRequiresEs6Message(Feature.MEMBER_DECLARATIONS),
        getRequiresEs6Message(Feature.NEW_TARGET));

    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.CLASSES, Feature.MEMBER_DECLARATIONS, Feature.NEW_TARGET);
    parse("class C { f() { new.target; } }");
  }

  @Test
  public void testNewDotSomethingInvalid() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("function f(){new.something}", "'target' expected");
  }

  @Test
  public void testArrow1() {
    expectFeatures(Feature.ARROW_FUNCTIONS);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("()=>1;");
    parse("()=>{}");
    parse("(a,b) => a + b;");
    parse("a => b");
    parse("a => { return b }");
    parse("a => b");
    parse("var x = (a => b);");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("a => b",
        getRequiresEs6Message(Feature.ARROW_FUNCTIONS));

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("a => b;",
        getRequiresEs6Message(Feature.ARROW_FUNCTIONS));
  }

  @Test
  public void testArrowInvalid1() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    parseError("*()=>1;", "primary expression expected");
    parseError("var f = x\n=>2", "No newline allowed before '=>'");
    parseError("f = (x,y)\n=>2;", "No newline allowed before '=>'");
    parseError("f( (x,y)\n=>2)", "No newline allowed before '=>'");
  }

  @Test
  public void testInvalidAwait() {
    parseError("await 15;", "'await' used in a non-async function context");
    parseError(
        "function f() { return await 5; }", "'await' used in a non-async function context");
  }

  @Test
  public void testAsyncFunction() {
    String asyncFunctionExpressionSource = "f = async function() {};";
    String asyncFunctionDeclarationSource = "async function f() {}";
    expectFeatures(Feature.ASYNC_FUNCTIONS);

    for (LanguageMode m : LanguageMode.values()) {
      mode = m;
      strictMode = (m == LanguageMode.ECMASCRIPT3) ? SLOPPY : STRICT;
      if (m.featureSet.has(Feature.ASYNC_FUNCTIONS)) {
        parse(asyncFunctionExpressionSource);
        parse(asyncFunctionDeclarationSource);
      } else {
        parseWarning(
            asyncFunctionExpressionSource,
            requiresLanguageModeMessage(LanguageMode.ECMASCRIPT8, Feature.ASYNC_FUNCTIONS));
        parseWarning(
            asyncFunctionDeclarationSource,
            requiresLanguageModeMessage(LanguageMode.ECMASCRIPT8, Feature.ASYNC_FUNCTIONS));
      }
    }
  }

  @Test
  public void testAsyncNamedFunction() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(
        Feature.CLASSES,
        Feature.MEMBER_DECLARATIONS,
        Feature.CONST_DECLARATIONS,
        Feature.LET_DECLARATIONS);
    parse(LINE_JOINER.join(
        "class C {",
        "  async(x) { return x; }",
        "}",
        "const c = new C();",
        "c.async(1);",
        "let foo = async(5);"));
  }

  @Test
  public void testAsyncGeneratorFunction() {
    mode = LanguageMode.ES_NEXT;
    expectFeatures(Feature.ASYNC_FUNCTIONS, Feature.GENERATORS, Feature.ASYNC_GENERATORS);
    strictMode = STRICT;
    parse("async function *f(){}");
    parse("f = async function *(){}");
    parse("class C { async *foo(){} }");
  }

  @Test
  public void testAsyncArrowFunction() {
    doAsyncArrowFunctionTest("f = async (x) => x + 1");
    doAsyncArrowFunctionTest("f = async x => x + 1");
  }

  private void doAsyncArrowFunctionTest(String arrowFunctionSource) {
    expectFeatures(Feature.ASYNC_FUNCTIONS, Feature.ARROW_FUNCTIONS);

    for (LanguageMode m : LanguageMode.values()) {
      mode = m;
      strictMode = (m == LanguageMode.ECMASCRIPT3) ? SLOPPY : STRICT;
      if (m.featureSet.has(Feature.ASYNC_FUNCTIONS)) {
        parse(arrowFunctionSource);
      } else if (m.featureSet.has(Feature.ARROW_FUNCTIONS)) {
        parseWarning(
            arrowFunctionSource,
            requiresLanguageModeMessage(LanguageMode.ECMASCRIPT8, Feature.ASYNC_FUNCTIONS));
      } else {
        parseWarning(
            arrowFunctionSource,
            requiresLanguageModeMessage(LanguageMode.ECMASCRIPT6, Feature.ARROW_FUNCTIONS),
            requiresLanguageModeMessage(LanguageMode.ECMASCRIPT8, Feature.ASYNC_FUNCTIONS));
      }
    }
  }

  @Test
  public void testAsyncArrowInvalid() {
    mode = LanguageMode.ECMASCRIPT8;
    strictMode = STRICT;
    parseError("f = not_async (x) => x + 1;", "'=>' unexpected");
  }

  @Test
  public void testAsyncMethod() {
    mode = LanguageMode.ECMASCRIPT8;
    strictMode = STRICT;
    expectFeatures(Feature.ASYNC_FUNCTIONS);
    parse("o={async m(){}}");
    parse("o={async [a+b](){}}");
    parse("class C{async m(){}}");
    parse("class C{static async m(){}}");
    parse("class C{async [a+b](){}}");
    parse("class C{static async [a+b](){}}");
  }

  @Test
  public void testInvalidAsyncMethod() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;
    expectFeatures(Feature.MEMBER_DECLARATIONS);
    // 'async' allowed as a name
    parse("o={async(){}}");
    parse("class C{async(){}}");
    parse("class C{static async(){}}");

    expectFeatures();
    parse("o={async:false}");
    parseError("class C{async};", "'(' expected");

    // newline after 'async' forces it to be the property name
    mode = LanguageMode.ECMASCRIPT8;
    strictMode = STRICT;
    parseError("o={async\nm(){}}", "'}' expected");
    parseError("o={static async\nm(){}}", "Cannot use keyword in short object literal");
    parseError("class C{async\nm(){}}", "'(' expected");
    parseError("class C{static async\nm(){}}", "'(' expected");
  }

  @Test
  public void testAwaitExpression() {
    mode = LanguageMode.ECMASCRIPT8;
    strictMode = STRICT;
    expectFeatures(Feature.ASYNC_FUNCTIONS);
    parse("async function f(p){await p}");
    parse("f = async function(p){await p}");
    parse("f = async(p)=>await p");
    parse("class C{async m(p){await p}}");
    parse("class C{static async m(p){await p}}");
  }

  @Test
  public void testAwaitExpressionInvalid() {
    parseError("async function f() { await; }", "primary expression expected");
  }

  @Test
  public void testFor_ES5() {
    parse("for (var x; x != 10; x = next()) {}");
    parse("for (var x; x != 10; x = next());");
    parse("for (var x = 0; x != 10; x++) {}");
    parse("for (var x = 0; x != 10; x++);");

    parse("var x; for (x; x != 10; x = next()) {}");
    parse("var x; for (x; x != 10; x = next());");

    parseError("for (x in {};;) {}", "')' expected");
  }

  @Test
  public void testFor_ES6() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.LET_DECLARATIONS);
    parse("for (let x; x != 10; x = next()) {}");
    parse("for (let x; x != 10; x = next());");
    parse("for (let x = 0; x != 10; x++) {}");
    parse("for (let x = 0; x != 10; x++);");

    expectFeatures(Feature.CONST_DECLARATIONS);
    parse("for (const x = 0; x != 10; x++) {}");
    parse("for (const x = 0; x != 10; x++);");
  }

  @Test
  public void testForConstNoInitializer() {
    parseError("for (const x; x != 10; x = next()) {}", "const variables must have an initializer");
    parseError("for (const x; x != 10; x = next());", "const variables must have an initializer");
  }

  @Test
  public void testForIn_ES6() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("for (a in b) c;");
    parse("for (var a in b) c;");

    expectFeatures(Feature.LET_DECLARATIONS);
    parse("for (let a in b) c;");

    expectFeatures(Feature.CONST_DECLARATIONS);
    parse("for (const a in b) c;");

    expectFeatures();
    parseError("for (a,b in c) d;", INVALID_ASSIGNMENT_TARGET);
    parseError(
        "for (var a,b in c) d;",
        "for-in statement may not have more than one variable declaration");

    parseError(
        "for (let a,b in c) d;",
        "for-in statement may not have more than one variable declaration");

    parseError(
        "for (const a,b in c) d;",
        "for-in statement may not have more than one variable declaration");

    parseError("for (a=1 in b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for (let a=1 in b) c;", "for-in statement may not have initializer");
    parseError("for (const a=1 in b) c;", "for-in statement may not have initializer");
    parseError("for (var a=1 in b) c;", "for-in statement may not have initializer");
    parseError("for (\"a\" in b) c;", INVALID_ASSIGNMENT_TARGET);
  }

  @Test
  public void testForIn_ES5() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;

    parse("for (a in b) c;");
    parse("for (var a in b) c;");

    parseError("for (a=1 in b) c;", INVALID_ASSIGNMENT_TARGET);
    parseWarning("for (var a=1 in b) c;", "for-in statement should not have initializer");
  }

  @Test
  public void testForInDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("for ({a} in b) c;");
    parse("for (var {a} in b) c;");
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let {a} in b) c;");
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const {a} in b) c;");

    expectFeatures(Feature.OBJECT_DESTRUCTURING);
    parse("for ({a: b} in c) d;");
    parse("for (var {a: b} in c) d;");
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let {a: b} in c) d;");
    expectFeatures(Feature.OBJECT_DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const {a: b} in c) d;");

    expectFeatures(Feature.ARRAY_DESTRUCTURING);
    parse("for ([a] in b) c;");
    parse("for (var [a] in b) c;");
    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let [a] in b) c;");
    expectFeatures(Feature.ARRAY_DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const [a] in b) c;");
  }

  @Test
  public void testForInDestructuringInvalid() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("for ({a: b} = foo() in c) d;", INVALID_ASSIGNMENT_TARGET);
    parseError("for (var {a: b} = foo() in c) d;", "for-in statement may not have initializer");
    parseError("for (let {a: b} = foo() in c) d;", "for-in statement may not have initializer");
    parseError("for (const {a: b} = foo() in c) d;", "for-in statement may not have initializer");

    parseError("for ([a] = foo() in b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for (var [a] = foo() in b) c;", "for-in statement may not have initializer");
    parseError("for (let [a] = foo() in b) c;", "for-in statement may not have initializer");
    parseError("for (const [a] = foo() in b) c;", "for-in statement may not have initializer");
  }

  @Test
  public void testForOf1() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.FOR_OF);
    parse("for(a of b) c;");
    parse("for(var a of b) c;");
    expectFeatures(Feature.FOR_OF, Feature.LET_DECLARATIONS);
    parse("for(let a of b) c;");
    expectFeatures(Feature.FOR_OF, Feature.CONST_DECLARATIONS);
    parse("for(const a of b) c;");
  }

  @Test
  public void testForOf2() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("for(a=1 of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for(var a=1 of b) c;", "for-of statement may not have initializer");
    parseError("for(let a=1 of b) c;", "for-of statement may not have initializer");
    parseError("for(const a=1 of b) c;", "for-of statement may not have initializer");
  }

  @Test
  public void testForOf3() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("for(var a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
    parseError("for(let a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
    parseError("for(const a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
  }

  @Test
  public void testForOf4() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("for(a, b of c) d;", INVALID_ASSIGNMENT_TARGET);
  }

  @Test
  public void testValidForAwaitOf() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    expectFeatures(Feature.FOR_AWAIT_OF);
    parse("for await(a of b) c;");
    parse("for await(var a of b) c;");
    parse("for await (a.x of b) c;");
    parse("for await ([a1, a2, a3] of b) c;");
    parse("for await (const {x, y, z} of b) c;");
    // default value inside a pattern isn't an initializer
    parse("for await (const {x, y = 2, z} of b) c;");
    expectFeatures(Feature.FOR_AWAIT_OF, Feature.LET_DECLARATIONS);
    parse("for await(let a of b) c;");
    expectFeatures(Feature.FOR_AWAIT_OF, Feature.CONST_DECLARATIONS);
    parse("for await(const a of b) c;");
  }

  @Test
  public void testInvalidForAwaitOfInitializers() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("for await (a=1 of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for await (var a=1 of b) c;", "for-await-of statement may not have initializer");
    parseError("for await (let a=1 of b) c;", "for-await-of statement may not have initializer");
    parseError("for await (const a=1 of b) c;", "for-await-of statement may not have initializer");
    parseError(
        "for await (let {a} = {} of b) c;", "for-await-of statement may not have initializer");
  }

  @Test
  public void testInvalidForAwaitOfMultipleInitializerTargets() {
    mode = LanguageMode.ES_NEXT;
    strictMode = SLOPPY;

    parseError("for await (a, b of c) d;", INVALID_ASSIGNMENT_TARGET);

    parseError(
        "for await (var a, b of c) d;",
        "for-await-of statement may not have more than one variable declaration");
    parseError(
        "for await (let a, b of c) d;",
        "for-await-of statement may not have more than one variable declaration");
    parseError(
        "for await (const a, b of c) d;",
        "for-await-of statement may not have more than one variable declaration");
  }

  @Test
  public void testDestructuringInForLoops() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    // Destructuring forbids an initializer in for-in/for-of
    parseError("for (var {x: y} = foo() in bar()) {}",
        "for-in statement may not have initializer");
    parseError("for (let {x: y} = foo() in bar()) {}",
        "for-in statement may not have initializer");
    parseError("for (const {x: y} = foo() in bar()) {}",
        "for-in statement may not have initializer");

    parseError("for (var {x: y} = foo() of bar()) {}",
        "for-of statement may not have initializer");
    parseError("for (let {x: y} = foo() of bar()) {}",
        "for-of statement may not have initializer");
    parseError("for (const {x: y} = foo() of bar()) {}",
        "for-of statement may not have initializer");

    // but requires it in a vanilla for loop
    parseError("for (var {x: y};;) {}", "destructuring must have an initializer");
    parseError("for (let {x: y};;) {}", "destructuring must have an initializer");
    parseError("for (const {x: y};;) {}", "const variables must have an initializer");
  }

  @Test
  public void testInvalidDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    // {x: 5} and {x: 'str'} are valid object literals but not valid patterns.
    parseError("for ({x: 5} in foo()) {}", INVALID_ASSIGNMENT_TARGET);
    parseError("for ({x: 'str'} in foo()) {}", INVALID_ASSIGNMENT_TARGET);
    parseError("var {x: 5} = foo();", INVALID_ASSIGNMENT_TARGET);
    parseError("var {x: 'str'} = foo();", INVALID_ASSIGNMENT_TARGET);
    parseError("({x: 5} = foo());", INVALID_ASSIGNMENT_TARGET);
    parseError("({x: 'str'} = foo());", INVALID_ASSIGNMENT_TARGET);

    // {method(){}} is a valid object literal but not a valid object pattern.
    parseError("function f({method(){}}) {}", "'}' expected");
    parseError("function f({method(){}} = foo()) {}", "'}' expected");
  }

  @Test
  public void testForOfPatterns() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.FOR_OF, Feature.OBJECT_DESTRUCTURING);
    parse("for({x} of b) c;");
    parse("for({x: y} of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.ARRAY_DESTRUCTURING);
    parse("for([x, y] of b) c;");
    parse("for([x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.OBJECT_DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for(let {x} of b) c;");
    parse("for(let {x: y} of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.ARRAY_DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for(let [x, y] of b) c;");
    parse("for(let [x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.OBJECT_DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for(const {x} of b) c;");
    parse("for(const {x: y} of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.ARRAY_DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for(const [x, y] of b) c;");
    parse("for(const [x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.OBJECT_DESTRUCTURING);
    parse("for(var {x} of b) c;");
    parse("for(var {x: y} of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.ARRAY_DESTRUCTURING);
    parse("for(var [x, y] of b) c;");
    parse("for(var [x, ...y] of b) c;");
  }

  @Test
  public void testForOfPatternsWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parseError("for({x}=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for({x: y}=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for([x, y]=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for([x, ...y]=a of b) c;", INVALID_ASSIGNMENT_TARGET);

    parseError("for(let {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, ...y]=a of b) c;", "for-of statement may not have initializer");

    parseError("for(const {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, ...y]=a of b) c;", "for-of statement may not have initializer");
  }

  @Test
  public void testImport() {
    expectFeatures(Feature.MODULES);
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    parse("import 'someModule'");
    parse("import d from './someModule'");
    parse("import {} from './someModule'");
    parse("import {x, y} from './someModule'");
    parse("import {x as x1, y as y1} from './someModule'");
    parse("import {x as x1, y as y1, } from './someModule'");
    parse("import {default as d, class as c} from './someModule'");
    parse("import d, {x as x1, y as y1} from './someModule'");
    parse("import * as sm from './someModule'");

    expectFeatures();
    parseError("import class from './someModule'",
            "cannot use keyword 'class' here.");
    parseError("import * as class from './someModule'",
            "'identifier' expected");
    parseError("import {a as class} from './someModule'",
            "'identifier' expected");
    parseError("import {class} from './someModule'",
            "'as' expected");
  }

  @Test
  public void testExport() {
    mode = LanguageMode.ECMASCRIPT6;
    strictMode = SLOPPY;

    expectFeatures(Feature.MODULES);
    parse("export const x = 1");
    parse("export var x = 1");
    parse("export function f() {}");
    parse("export class c {}");
    parse("export {x, y}");
    parse("export {x as x1}");
    parse("export {x as x1, y as x2}");
    parse("export {x as default, y as class}");

    expectFeatures();
    parseError("export {default as x}",
        "cannot use keyword 'default' here.");
    parseError("export {package as x}",
        "cannot use keyword 'package' here.");
    parseError("export {package}",
        "cannot use keyword 'package' here.");

    expectFeatures(Feature.MODULES);
    parse("export {x as x1, y as y1} from './someModule'");
    parse("export {x as x1, y as y1, } from './someModule'");
    parse("export {default as d} from './someModule'");
    parse("export {d as default, c as class} from './someModule'");
    parse("export {default as default, class as class} from './someModule'");
    parse("export {class} from './someModule'");
    parse("export * from './someModule'");

    expectFeatures();
    parseError("export * as s from './someModule';", "'from' expected");
  }

  @Test
  public void testExportAsync() {
    mode = LanguageMode.ECMASCRIPT8;
    strictMode = SLOPPY;

    expectFeatures(Feature.MODULES, Feature.ASYNC_FUNCTIONS);
    parse("export async function f() {}");
  }

  @Test
  public void testImportExportTypescriptKeyword() {
    mode = LanguageMode.TYPESCRIPT;
    parseError("export { namespace };", "cannot use keyword 'namespace' here.");

    mode = LanguageMode.ECMASCRIPT6;
    parse("export { namespace };");
    parse("import { namespace } from './input0.js';");
  }

  @Test
  public void testGoogModule() {
    Node tree = parse("goog.module('example');");
    assertNode(tree).hasType(Token.SCRIPT);
    assertThat(tree.getStaticSourceFile()).isNotNull();
    assertNode(tree.getFirstChild()).hasType(Token.MODULE_BODY);
    assertThat(tree.getFirstChild().getStaticSourceFile()).isNotNull();
  }

  @Test
  public void testShebang() {
    parse("#!/usr/bin/node\n var x = 1;");
    parseError("var x = 1; \n #!/usr/bin/node",
        "primary expression expected");
  }

  @Test
  public void testLookaheadGithubIssue699() {
    long start = System.currentTimeMillis();
    parse(
        "[1,[1,[1,[1,[1,[1,\n" +
        "[1,[1,[1,[1,[1,[1,\n" +
        "[1,[1,[1,[1,[1,[1,\n" +
        "[1,[1,[1,[1,[1,[1,\n" +
        "[1,[1,[1,[1,[1,[1,\n" +
        "[1,[1,\n" +
        "[1]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]] ");

    long stop = System.currentTimeMillis();

    assertThat(stop - start).named("runtime").isLessThan(5000L);
  }

  @Test
  public void testInvalidHandling1() {
    parse(""
        + "/**\n"
        + " * @fileoverview Definition.\n"
        + " * @mods {ns.bar}\n"
        + " * @modName mod\n"
        + " *\n"
        + " * @extends {ns.bar}\n"
        + " * @author someone\n"
        + " */\n"
        + "\n"
        + "goog.provide('ns.foo');\n"
        + "");
  }

  @Test
  public void testUtf8() {
    mode = LanguageMode.ECMASCRIPT5;
    strictMode = SLOPPY;
    Node n = parse("\uFEFFfunction f() {}\n");
    Node fn = n.getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);
  }

  @Test
  public void testParseDeep1() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "\'end\';n";
    parse(code);
  }

  @Test
  public void testParseDeep2() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "\'end\'; /** a comment */\n";
    parse(code);
  }

  @Test
  public void testParseDeep3() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "  /** @type {string} */ (x);\n";
    parse(code);
  }

  @Test
  public void testParseDeep4() {
    // Currently, we back off if there is any JSDoc in the tree of binary expressions
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      if (i == 5) {
        code += "  /** @type {string} */ (x) +\n";
      }
      code += "  \'" + i + "\' +\n";
    }
    code += "\'end\';n";
    try {
      parse(code);
      throw new AssertionError();
    } catch (RuntimeException e) {
      // expected exception
      assertThat(e).hasMessageThat().contains("Exception parsing");
    }
  }

  @Test
  public void testParseInlineSourceMap() {
    String code = "var X = (function () {\n"
        + "    function X(input) {\n"
        + "        this.y = input;\n"
        + "    }\n"
        + "    return X;\n"
        + "}());\n"
        + "console.log(new X(1));\n"
        + "//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZm9vLmpz"
        + "Iiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiZm9vLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiJBQU"
        + "FBO0lBR0UsV0FBWSxLQUFhO1FBQ3ZCLElBQUksQ0FBQyxDQUFDLEdBQUcsS0FBSyxDQUFDO0lBQ2pCLENBQUM7"
        + "SUFDSCxRQUFDO0FBQUQsQ0FBQyxBQU5ELElBTUM7QUFFRCxPQUFPLENBQUMsR0FBRyxDQUFDLElBQUksQ0FBQy"
        + "xDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMifQ==";
    ParseResult result = doParse(code);
    assertThat(result.sourceMapURL)
        .isEqualTo(
            "data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZm9vLmpz"
                + "Iiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiZm9vLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiJBQU"
                + "FBO0lBR0UsV0FBWSxLQUFhO1FBQ3ZCLElBQUksQ0FBQyxDQUFDLEdBQUcsS0FBSyxDQUFDO0lBQ2pCLENBQUM7"
                + "SUFDSCxRQUFDO0FBQUQsQ0FBQyxBQU5ELElBTUM7QUFFRCxPQUFPLENBQUMsR0FBRyxDQUFDLElBQUksQ0FBQy"
                + "xDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMifQ==");
  }

  @Test
  public void testParseSourceMapRelativeURL() {
    String code =
        "var X = (function () {\n"
            + "    function X(input) {\n"
            + "        this.y = input;\n"
            + "    }\n"
            + "    return X;\n"
            + "}());\n"
            + "console.log(new X(1));\n"
            + "//# sourceMappingURL=somefile.js.map";
    ParseResult result = doParse(code);
    assertThat(result.sourceMapURL).isEqualTo("somefile.js.map");
  }

  /**
   * In the future, we may want absolute URLs to be mapable based on how the server exposes the
   * sources. See: b/62544959.
   */
  @Test
  public void testParseSourceMapAbsoluteURL() {
    String code =
        "console.log('asdf');\n" + "//# sourceMappingURL=/some/absolute/path/to/somefile.js.map";
    ParseResult result = doParse(code);
    assertThat(result.sourceMapURL).isEqualTo("/some/absolute/path/to/somefile.js.map");
  }

  /**
   * In the future, we may want absolute URLs to me mapable based on how the server exposes the
   * sources. See: b/62544959.
   */
  @Test
  public void testParseSourceMapAbsoluteURLHTTP() {
    String code =
        "console.log('asdf');\n"
            + "//# sourceMappingURL=http://google.com/some/absolute/path/to/somefile.js.map";
    ParseResult result = doParse(code);
    assertThat(result.sourceMapURL)
        .isEqualTo("http://google.com/some/absolute/path/to/somefile.js.map");
  }

  @Test
  public void testIncorrectAssignmentDoesntCrash() {
    // Check that error make sense in default "stop on error" mode.
    parseError("[1 + 2] = 3;", "invalid assignment target");

    // Ensure that in IDE mode parser doesn't crash. It produces much more errors but it's
    // "ignore errors" mode so it's ok.
    isIdeMode = true;
    parseError(
        "[1 + 2] = 3;",
        "invalid assignment target",
        "']' expected",
        "invalid assignment target",
        "Semi-colon expected",
        "Semi-colon expected",
        "primary expression expected",
        "invalid assignment target",
        "Semi-colon expected",
        "primary expression expected",
        "Semi-colon expected");
  }

  private void assertNodeHasJSDocInfoWithJSType(Node node, JSType jsType) {
    JSDocInfo info = node.getJSDocInfo();
    assertWithMessage("Node has no JSDocInfo: %s", node).that(info).isNotNull();
    assertTypeEquals(jsType, info.getType());
  }

  private void assertNodeHasJSDocInfoWithNoJSType(Node node) {
    JSDocInfo info = node.getJSDocInfo();
    assertWithMessage("Node has no JSDocInfo: %s", node).that(info).isNotNull();
    JSTypeExpression type = info.getType();
    assertWithMessage("JSDoc unexpectedly has type").that(type).isNull();
  }

  private void assertNodeHasNoJSDocInfo(Node node) {
    JSDocInfo info = node.getJSDocInfo();
    assertWithMessage("Node %s has unexpected JSDocInfo %s", node, info).that(info).isNull();
  }

  private static String getRequiresEs6Message(Feature feature) {
    return requiresLanguageModeMessage(LanguageMode.ECMASCRIPT6, feature);
  }

  private static String getRequiresEs2018Message(Feature feature) {
    return requiresLanguageModeMessage(LanguageMode.ECMASCRIPT_2018, feature);
  }

  private static String requiresLanguageModeMessage(LanguageMode languageMode, Feature feature) {
    return String.format(
        "This language feature is only supported for %s mode or better: %s",
        languageMode,
        feature);
  }

  private static Node script(Node stmt) {
    Node n = new Node(Token.SCRIPT, stmt);
    return n;
  }

  private static Node expr(Node n) {
    return new Node(Token.EXPR_RESULT, n);
  }

  private static Node regex(String regex) {
    return new Node(Token.REGEXP, Node.newString(regex));
  }

  /**
   * Verify that the given code has the given parse errors.
   * @return If in IDE mode, returns a partial tree.
   */
  private Node parseError(String source, String... errors) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(errors, null);
    ParseResult result =
        ParserRunner.parse(
            new SimpleSourceFile("input", SourceKind.STRONG),
            source,
            createConfig(),
            testErrorReporter);
    Node script = result.ast;

    // check expected features if specified
    assertFS(result.features).contains(expectedFeatures);

    // verifying that all errors were seen
    testErrorReporter.assertHasEncounteredAllErrors();
    testErrorReporter.assertHasEncounteredAllWarnings();

    return script;
  }

  /**
   * Verify that the given code has the given parse warnings.
   * @return The parse tree.
   */
  private Node parseWarning(String string, String... warnings) {
    return doParse(string, warnings).ast;
  }

  private ParserRunner.ParseResult doParse(String string, String... warnings) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    StaticSourceFile file = new SimpleSourceFile("input", SourceKind.STRONG);
    ParserRunner.ParseResult result = ParserRunner.parse(
        file,
        string,
        createConfig(),
        testErrorReporter);

    // check expected features if specified
    assertFS(result.features).contains(expectedFeatures);

    // verifying that all warnings were seen
    testErrorReporter.assertHasEncounteredAllErrors();
    testErrorReporter.assertHasEncounteredAllWarnings();
    assertSourceInfoPresent(result.ast);
    return result;
  }

  private void assertSourceInfoPresent(Node node) {
    ArrayDeque<Node> deque = new ArrayDeque<>();
    deque.add(node);

    while (!deque.isEmpty()) {
      node = deque.remove();

      assertWithMessage("Source information must be present on %s", node)
          .that(node.getLineno() >= 0)
          .isTrue();

      for (Node child : node.children()) {
        deque.add(child);
      }
    }
  }

  /**
   * Verify that the given code has no parse warnings or errors.
   * @return The parse tree.
   */
  private Node parse(String string) {
    return parseWarning(string);
  }

  private Config createConfig() {
    if (isIdeMode) {
      return ParserRunner.createConfig(
          mode,
          Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
          Config.RunMode.KEEP_GOING,
          null,
          true,
          strictMode);
    } else {
      return ParserRunner.createConfig(mode, null, strictMode);
    }
  }

  /** Sets expectedFeatures based on the list of features. */
  private void expectFeatures(Feature... features) {
    expectedFeatures = FeatureSet.BARE_MINIMUM.with(features);
  }

  private static class ParserResult {
    private final String code;
    private final Node node;

    private ParserResult(String code, Node node) {
      this.code = code;
      this.node = node;
    }
  }
}
