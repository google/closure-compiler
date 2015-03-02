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
import static com.google.javascript.jscomp.parsing.IRFactory.MISPLACED_FUNCTION_ANNOTATION;
import static com.google.javascript.jscomp.parsing.IRFactory.MISPLACED_TYPE_ANNOTATION;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;

import java.util.List;

public class NewParserTest extends BaseJSTypeTestCase {
  private static final String SUSPICIOUS_COMMENT_WARNING =
      IRFactory.SUSPICIOUS_COMMENT_WARNING;

  private static final String TRAILING_COMMA_MESSAGE =
      "Trailing comma is not legal in an ECMA-262 object initializer";

  private static final String MISSING_GT_MESSAGE =
      "Bad type annotation. missing closing >";

  private static final String UNLABELED_BREAK =
      "unlabelled break must be inside loop or switch";

  private static final String UNEXPECTED_CONTINUE =
      "continue must be inside loop";

  private static final String UNEXPECTED_RETURN =
      "return must be inside function";

  private static final String UNEXPECTED_LABELED_CONTINUE =
      "continue can only use labeles of iteration statements";

  private static final String UNDEFINED_LABEL = "undefined label";

  private static final String HTML_COMMENT_WARNING = "In some cases, '<!--' " +
      "and '-->' are treated as a '//' " +
      "for legacy reasons. Removing this from your code is " +
      "safe for all browsers currently in use.";

  private Config.LanguageMode mode;
  private boolean isIdeMode = false;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mode = LanguageMode.ECMASCRIPT3;
    isIdeMode = false;
  }

  public void testFunction() {
    parse("var f = function(x,y,z) { return 0; }");
    parse("function f(x,y,z) { return 0; }");

    parseError("var f = function(x,y,z,) {}",
        "Invalid trailing comma in formal parameter list");
    parseError("function f(x,y,z,) {}",
        "Invalid trailing comma in formal parameter list");
  }

  public void testWhile() {
    parse("while(1) { break; }");
  }

  public void testNestedWhile() {
    parse("while(1) { while(1) { break; } }");
  }

  public void testBreak() {
    parseError("break;", UNLABELED_BREAK);
  }

  public void testContinue() {
    parseError("continue;", UNEXPECTED_CONTINUE);
  }

  public void testBreakCrossFunction() {
    parseError("while(1) { function f() { break; } }", UNLABELED_BREAK);
  }

  public void testBreakCrossFunctionInFor() {
    parseError("while(1) {for(var f = function () { break; };;) {}}", UNLABELED_BREAK);
  }

  public void testBreakInForOf() {
    parse(""
        + "for (var x of [1, 2, 3]) {\n"
        + "  if (x == 2) break;\n"
        + "}");
  }

  public void testContinueToSwitch() {
    parseError("switch(1) {case(1): continue; }", UNEXPECTED_CONTINUE);
  }

  public void testContinueToSwitchWithNoCases() {
    parse("switch(1){}");
  }

  public void testContinueToSwitchWithTwoCases() {
    parseError("switch(1){case(1):break;case(2):continue;}", UNEXPECTED_CONTINUE);
  }

  public void testContinueToSwitchWithDefault() {
    parseError("switch(1){case(1):break;case(2):default:continue;}", UNEXPECTED_CONTINUE);
  }

  public void testContinueToLabelSwitch() {
    parseError(
        "while(1) {a: switch(1) {case(1): continue a; }}",
        UNEXPECTED_LABELED_CONTINUE);
  }

  public void testContinueOutsideSwitch() {
    parse("b: while(1) { a: switch(1) { case(1): continue b; } }");
  }

  public void testContinueNotCrossFunction1() {
    parse("a:switch(1){case(1):function f(){a:while(1){continue a;}}}");
  }

  public void testContinueNotCrossFunction2() {
    parseError(
        "a:switch(1){case(1):function f(){while(1){continue a;}}}",
        UNDEFINED_LABEL + " \"a\"");
  }

  public void testContinueInForOf() {
    parse(""
        + "for (var x of [1, 2, 3]) {\n"
        + "  if (x == 2) continue;\n"
        + "}");
  }

  public void testReturn() {
    parse("function foo() { return 1; }");
    parseError("return;", UNEXPECTED_RETURN);
    parseError("return 1;", UNEXPECTED_RETURN);
  }

  public void testThrow() {
    parse("throw Error();");
    parse("throw new Error();");
    parse("throw '';");
    parseError("throw;", "semicolon/newline not allowed after 'throw'");
    parseError("throw\nError();", "semicolon/newline not allowed after 'throw'");
  }

  public void testLabel1() {
    parse("foo:bar");
  }

  public void testLabel2() {
    parse("{foo:bar}");
  }

  public void testLabel3() {
    parse("foo:bar:baz");
  }

  public void testDuplicateLabelWithoutBraces() {
    parseError("foo:foo:bar", "Duplicate label \"foo\"");
  }

  public void testDuplicateLabelWithBraces() {
    parseError("foo:{bar;foo:baz}", "Duplicate label \"foo\"");
  }

  public void testDuplicateLabelWithFor() {
    parseError("foo:for(;;){foo:bar}", "Duplicate label \"foo\"");
  }

  public void testNonDuplicateLabelSiblings() {
    parse("foo:1;foo:2");
  }

  public void testNonDuplicateLabelCrossFunction() {
    parse("foo:(function(){foo:2})");
  }

  public void testLinenoCharnoAssign1() throws Exception {
    Node assign = parse("a = b").getFirstChild().getFirstChild();

    assertThat(assign.getType()).isEqualTo(Token.ASSIGN);
    assertThat(assign.getLineno()).isEqualTo(1);
    assertThat(assign.getCharno()).isEqualTo(0);
  }

  public void testLinenoCharnoAssign2() throws Exception {
    Node assign = parse("\n a.g.h.k    =  45").getFirstChild().getFirstChild();

    assertThat(assign.getType()).isEqualTo(Token.ASSIGN);
    assertThat(assign.getLineno()).isEqualTo(2);
    assertThat(assign.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoCall() throws Exception {
    Node call = parse("\n foo(123);").getFirstChild().getFirstChild();

    assertThat(call.getType()).isEqualTo(Token.CALL);
    assertThat(call.getLineno()).isEqualTo(2);
    assertThat(call.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoGetProp1() throws Exception {
    Node getprop = parse("\n foo.bar").getFirstChild().getFirstChild();

    assertThat(getprop.getType()).isEqualTo(Token.GETPROP);
    assertThat(getprop.getLineno()).isEqualTo(2);
    assertThat(getprop.getCharno()).isEqualTo(1);

    Node name = getprop.getFirstChild().getNext();
    assertThat(name.getType()).isEqualTo(Token.STRING);
    assertThat(name.getLineno()).isEqualTo(2);
    assertThat(name.getCharno()).isEqualTo(5);
  }

  public void testLinenoCharnoGetProp2() throws Exception {
    Node getprop = parse("\n foo.\nbar").getFirstChild().getFirstChild();

    assertThat(getprop.getType()).isEqualTo(Token.GETPROP);
    assertThat(getprop.getLineno()).isEqualTo(2);
    assertThat(getprop.getCharno()).isEqualTo(1);

    Node name = getprop.getFirstChild().getNext();
    assertThat(name.getType()).isEqualTo(Token.STRING);
    assertThat(name.getLineno()).isEqualTo(3);
    assertThat(name.getCharno()).isEqualTo(0);
  }

  public void testLinenoCharnoGetelem1() throws Exception {
    Node call = parse("\n foo[123]").getFirstChild().getFirstChild();

    assertThat(call.getType()).isEqualTo(Token.GETELEM);
    assertThat(call.getLineno()).isEqualTo(2);
    assertThat(call.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoGetelem2() throws Exception {
    Node call = parse("\n   \n foo()[123]").getFirstChild().getFirstChild();

    assertThat(call.getType()).isEqualTo(Token.GETELEM);
    assertThat(call.getLineno()).isEqualTo(3);
    assertThat(call.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoGetelem3() throws Exception {
    Node call = parse("\n   \n (8 + kl)[123]").getFirstChild().getFirstChild();

    assertThat(call.getType()).isEqualTo(Token.GETELEM);
    assertThat(call.getLineno()).isEqualTo(3);
    assertThat(call.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoForComparison() throws Exception {
    Node lt =
      parse("for (; i < j;){}").getFirstChild().getFirstChild().getNext();

    assertThat(lt.getType()).isEqualTo(Token.LT);
    assertThat(lt.getLineno()).isEqualTo(1);
    assertThat(lt.getCharno()).isEqualTo(7);
  }

  public void testLinenoCharnoHook() throws Exception {
    Node n = parse("\n a ? 9 : 0").getFirstChild().getFirstChild();

    assertThat(n.getType()).isEqualTo(Token.HOOK);
    assertThat(n.getLineno()).isEqualTo(2);
    assertThat(n.getCharno()).isEqualTo(1);
  }

  public void testLinenoCharnoArrayLiteral() throws Exception {
    Node n = parse("\n  [8, 9]").getFirstChild().getFirstChild();

    assertThat(n.getType()).isEqualTo(Token.ARRAYLIT);
    assertThat(n.getLineno()).isEqualTo(2);
    assertThat(n.getCharno()).isEqualTo(2);

    n = n.getFirstChild();

    assertThat(n.getType()).isEqualTo(Token.NUMBER);
    assertThat(n.getLineno()).isEqualTo(2);
    assertThat(n.getCharno()).isEqualTo(3);

    n = n.getNext();

    assertThat(n.getType()).isEqualTo(Token.NUMBER);
    assertThat(n.getLineno()).isEqualTo(2);
    assertThat(n.getCharno()).isEqualTo(6);
  }

  public void testLinenoCharnoObjectLiteral() throws Exception {
    Node n = parse("\n\n var a = {a:0\n,b :1};")
        .getFirstChild().getFirstChild().getFirstChild();

    assertThat(n.getType()).isEqualTo(Token.OBJECTLIT);
    assertThat(n.getLineno()).isEqualTo(3);
    assertThat(n.getCharno()).isEqualTo(9);

    Node key = n.getFirstChild();

    assertThat(key.getType()).isEqualTo(Token.STRING_KEY);
    assertThat(key.getLineno()).isEqualTo(3);
    assertThat(key.getCharno()).isEqualTo(10);

    Node value = key.getFirstChild();

    assertThat(value.getType()).isEqualTo(Token.NUMBER);
    assertThat(value.getLineno()).isEqualTo(3);
    assertThat(value.getCharno()).isEqualTo(12);

    key = key.getNext();

    assertThat(key.getType()).isEqualTo(Token.STRING_KEY);
    assertThat(key.getLineno()).isEqualTo(4);
    assertThat(key.getCharno()).isEqualTo(1);

    value = key.getFirstChild();

    assertThat(value.getType()).isEqualTo(Token.NUMBER);
    assertThat(value.getLineno()).isEqualTo(4);
    assertThat(value.getCharno()).isEqualTo(4);
  }

  public void testLinenoCharnoAdd() throws Exception {
    testLinenoCharnoBinop("+");
  }

  public void testLinenoCharnoSub() throws Exception {
    testLinenoCharnoBinop("-");
  }

  public void testLinenoCharnoMul() throws Exception {
    testLinenoCharnoBinop("*");
  }

  public void testLinenoCharnoDiv() throws Exception {
    testLinenoCharnoBinop("/");
  }

  public void testLinenoCharnoMod() throws Exception {
    testLinenoCharnoBinop("%");
  }

  public void testLinenoCharnoShift() throws Exception {
    testLinenoCharnoBinop("<<");
  }

  public void testLinenoCharnoBinaryAnd() throws Exception {
    testLinenoCharnoBinop("&");
  }

  public void testLinenoCharnoAnd() throws Exception {
    testLinenoCharnoBinop("&&");
  }

  public void testLinenoCharnoBinaryOr() throws Exception {
    testLinenoCharnoBinop("|");
  }

  public void testLinenoCharnoOr() throws Exception {
    testLinenoCharnoBinop("||");
  }

  public void testLinenoCharnoLt() throws Exception {
    testLinenoCharnoBinop("<");
  }

  public void testLinenoCharnoLe() throws Exception {
    testLinenoCharnoBinop("<=");
  }

  public void testLinenoCharnoGt() throws Exception {
    testLinenoCharnoBinop(">");
  }

  public void testLinenoCharnoGe() throws Exception {
    testLinenoCharnoBinop(">=");
  }

  private void testLinenoCharnoBinop(String binop) {
    Node op = parse("var a = 89 " + binop + " 76;").getFirstChild().
        getFirstChild().getFirstChild();

    assertThat(op.getLineno()).isEqualTo(1);
    assertThat(op.getCharno()).isEqualTo(8);
  }

  public void testJSDocAttachment1() {
    Node varNode = parse("/** @type number */var a;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);
    JSDocInfo varInfo = varNode.getJSDocInfo();
    assertThat(varInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, varInfo.getType());

    // VAR NAME
    Node varNameNode = varNode.getFirstChild();
    assertThat(varNameNode.getType()).isEqualTo(Token.NAME);
    assertThat(varNameNode.getJSDocInfo()).isNull();

    mode = LanguageMode.ECMASCRIPT6;

    Node letNode = parse("/** @type number */let a;").getFirstChild();

    // LET
    assertThat(letNode.getType()).isEqualTo(Token.LET);
    JSDocInfo letInfo = letNode.getJSDocInfo();
    assertThat(letInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, letInfo.getType());

    // LET NAME
    Node letNameNode = letNode.getFirstChild();
    assertThat(letNameNode.getType()).isEqualTo(Token.NAME);
    assertThat(letNameNode.getJSDocInfo()).isNull();

    Node constNode = parse("/** @type number */const a = 0;").getFirstChild();

    // CONST
    assertThat(constNode.getType()).isEqualTo(Token.CONST);
    JSDocInfo constInfo = constNode.getJSDocInfo();
    assertThat(constInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, constInfo.getType());

    // LET NAME
    Node constNameNode = constNode.getFirstChild();
    assertThat(constNameNode.getType()).isEqualTo(Token.NAME);
    assertThat(constNameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment2() {
    Node varNode = parse("/** @type number */var a,b;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);
    JSDocInfo info = varNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(NUMBER_TYPE, info.getType());

    // First NAME
    Node nameNode1 = varNode.getFirstChild();
    assertThat(nameNode1.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode1.getJSDocInfo()).isNull();

    // Second NAME
    Node nameNode2 = nameNode1.getNext();
    assertThat(nameNode2.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode2.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment3() {
    Node assignNode = parse(
        "/** @type number */goog.FOO = 5;").getFirstChild().getFirstChild();
    assertThat(assignNode.getType()).isEqualTo(Token.ASSIGN);
    JSDocInfo info = assignNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(NUMBER_TYPE, info.getType());
  }

  public void testJSDocAttachment4() {
    Node varNode = parse(
        "var a, /** @define {number} */ b = 5;").getFirstChild();

    // ASSIGN
    assertThat(varNode.getType()).isEqualTo(Token.VAR);
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

  public void testJSDocAttachment5() {
    Node varNode = parse(
        "var /** @type number */a, /** @define {number} */b = 5;")
        .getFirstChild();

    // ASSIGN
    assertThat(varNode.getType()).isEqualTo(Token.VAR);
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
   * Tests that a JSDoc comment in an unexpected place of the code does not
   * propagate to following code due to {@link JSDocInfo} aggregation.
   */
  public void testJSDocAttachment6() throws Exception {
    Node functionNode = parseWarning(
        "var a = /** @param {number} index */5;"
        + "/** @return boolean */function f(index){}",
        MISPLACED_FUNCTION_ANNOTATION)
        .getFirstChild().getNext();

    assertThat(functionNode.getType()).isEqualTo(Token.FUNCTION);
    JSDocInfo info = functionNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.hasParameter("index")).isFalse();
    assertThat(info.hasReturnType()).isTrue();
    assertTypeEquals(UNKNOWN_TYPE, info.getReturnType());
  }

  public void testJSDocAttachment7() {
    Node varNode = parse("/** */var a;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertThat(nameNode.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment8() {
    Node varNode = parse("/** x */var a;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertThat(nameNode.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment9() {
    Node varNode = parse("/** \n x */var a;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertThat(nameNode.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment10() {
    Node varNode = parse("/** x\n */var a;").getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertThat(nameNode.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment11() {
    Node varNode =
       parse("/** @type {{x : number, 'y' : string, z}} */var a;")
        .getFirstChild();

    // VAR
    assertThat(varNode.getType()).isEqualTo(Token.VAR);
    JSDocInfo info = varNode.getJSDocInfo();
    assertThat(info).isNotNull();

    assertTypeEquals(
        createRecordTypeBuilder()
            .addProperty("x", NUMBER_TYPE, null)
            .addProperty("y", STRING_TYPE, null)
            .addProperty("z", UNKNOWN_TYPE, null)
            .build(),
        info.getType());

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertThat(nameNode.getType()).isEqualTo(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment12() {
    Node varNode =
       parse("var a = {/** @type {Object} */ b: c};")
        .getFirstChild();
    Node objectLitNode = varNode.getFirstChild().getFirstChild();
    assertThat(objectLitNode.getType()).isEqualTo(Token.OBJECTLIT);
    assertThat(objectLitNode.getFirstChild().getJSDocInfo()).isNotNull();
  }

  public void testJSDocAttachment13() {
    Node varNode = parse("/** foo */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNotNull();
  }

  public void testJSDocAttachment14() {
    Node varNode = parse("/** */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment15() {
    Node varNode = parse("/** \n * \n */ var a;").getFirstChild();
    assertThat(varNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment16() {
    Node exprCall =
        parse("/** @private */ x(); function f() {};").getFirstChild();
    assertThat(exprCall.getType()).isEqualTo(Token.EXPR_RESULT);
    assertThat(exprCall.getNext().getJSDocInfo()).isNull();
    assertThat(exprCall.getFirstChild().getJSDocInfo()).isNotNull();
  }

  public void testJSDocAttachment17() {
    Node fn =
        parse(
            "function f() { " +
            "  return /** @type {string} */ (g(1 /** @desc x */));" +
            "};").getFirstChild();
    assertThat(fn.getType()).isEqualTo(Token.FUNCTION);
    Node cast = fn.getLastChild().getFirstChild().getFirstChild();
    assertThat(cast.getType()).isEqualTo(Token.CAST);
  }

  public void testJSDocAttachment18() {
    Node fn =
        parse(
            "function f() { " +
            "  var x = /** @type {string} */ (y);" +
            "};").getFirstChild();
    assertThat(fn.getType()).isEqualTo(Token.FUNCTION);
    Node cast = fn.getLastChild().getFirstChild().getFirstChild().getFirstChild();
    assertThat(cast.getType()).isEqualTo(Token.CAST);
  }

  public void testJSDocAttachment19() {
    Node fn =
        parseWarning(
            "function f() { " +
            "  /** @type {string} */" +
            "  return;" +
            "};",
            MISPLACED_TYPE_ANNOTATION).getFirstChild();
    assertThat(fn.getType()).isEqualTo(Token.FUNCTION);

    Node ret = fn.getLastChild().getFirstChild();
    assertThat(ret.getType()).isEqualTo(Token.RETURN);
    assertThat(ret.getJSDocInfo()).isNotNull();
  }

  public void testJSDocAttachment20() {
    Node fn =
        parseWarning(
            "function f() { " +
            "  /** @type {string} */" +
            "  if (true) return;" +
            "};", MISPLACED_TYPE_ANNOTATION).getFirstChild();
    assertThat(fn.getType()).isEqualTo(Token.FUNCTION);

    Node ret = fn.getLastChild().getFirstChild();
    assertThat(ret.getType()).isEqualTo(Token.IF);
    assertThat(ret.getJSDocInfo()).isNotNull();
  }

  public void testJSDocAttachment21() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("/** @param {string} x */ const f = function() {};");
    parse("/** @param {string} x */ let f = function() {};");
  }

  // Tests that JSDoc gets attached to export nodes, and there are no warnings.
  // See https://github.com/google/closure-compiler/issues/781
  public void testJSDocAttachment22() {
    mode = LanguageMode.ECMASCRIPT6;

    Node n = parse("/** @param {string} x */ export function f(x) {};");
    Node export = n.getFirstChild();

    assertNode(export).hasType(Token.EXPORT);
    assertThat(export.getJSDocInfo()).isNotNull();
    assertThat(export.getJSDocInfo().hasParameter("x")).isTrue();
  }

  public void testInlineJSDocAttachment1() {
    Node fn = parse("function f(/** string */ x) {}").getFirstChild();
    assertThat(fn.isFunction()).isTrue();

    JSDocInfo info = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testInlineJSDocAttachment2() {
    Node fn = parse(
        "function f(/** ? */ x) {}").getFirstChild();
    assertThat(fn.isFunction()).isTrue();

    JSDocInfo info = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(UNKNOWN_TYPE, info.getType());
  }

  public void testInlineJSDocAttachment3() {
    parseWarning(
        "function f(/** @type {string} */ x) {}",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testInlineJSDocAttachment4() {
    parseWarning(
        "function f(/**\n" +
        " * @type {string}\n" +
        " */ x) {}",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testInlineJSDocAttachment5() {
    Node vardecl = parse("var /** string */ x = 'asdf';").getFirstChild();
    JSDocInfo info = vardecl.getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testInlineJSDocAttachment6() {
    Node fn = parse("function f(/** {attr: number} */ x) {}").getFirstChild();
    assertThat(fn.isFunction()).isTrue();

    JSDocInfo info = fn.getFirstChild().getNext().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(
        createRecordTypeBuilder().addProperty("attr", NUMBER_TYPE, null).build(), info.getType());
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing1() throws Exception {
    assertNodeEquality(
        parse("var a = [1,2]"),
        parseWarning("/** @type Array.<number*/var a = [1,2]",
            MISSING_GT_MESSAGE));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing2() throws Exception {
    assertNodeEquality(
        parse("var a = [1,2]"),
        parseWarning("/** @type {Array.<number}*/var a = [1,2]",
            MISSING_GT_MESSAGE));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing3() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @param {Array.<number} nums */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
            MISSING_GT_MESSAGE));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing4() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parse("/** @return boolean */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing5() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parse("/** @param boolean this is some string*/" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing6() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @param {bool!*%E$} */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
            "Bad type annotation. expected closing }",
            "Bad type annotation. expecting a variable name in a @param tag"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing7() throws Exception {
    isIdeMode = true;

    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @see */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "@see tag missing description"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing8() throws Exception {
    isIdeMode = true;

    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @author */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "@author tag missing author"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing9() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parseWarning("/** @someillegaltag */" +
              "C.prototype.say=function(nums) {alert(nums.join(','));};",
              "illegal use of unknown JSDoc tag \"someillegaltag\";"
              + " ignoring it"));
  }

  public void testUnescapedSlashInRegexpCharClass() {
    parse("var foo = /[/]/;");
    parse("var foo = /[hi there/]/;");
    parse("var foo = /[/yo dude]/;");
    parse("var foo = /\\/[@#$/watashi/wa/suteevu/desu]/;");
  }

  /**
   * Test for https://github.com/google/closure-compiler/issues/389.
   */
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

  private void assertNodeEquality(Node expected, Node found) {
    String message = expected.checkTreeEquals(found);
    if (message != null) {
      fail(message);
    }
  }

  @SuppressWarnings("unchecked")
  public void testParse() {
    mode = LanguageMode.ECMASCRIPT5;
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

    // This fails because an EMPTY statement
    // is inserted after the 'x=1'.
    // TODO(tbreisacher): Fix and re-enable.
    //assertNodeEquality(
    //    parse("x = 1\n;y = 2"),
    //    parse("x = 1; y = 2;"));

    // if/else statements
    assertNodeEquality(
        parse("if (x)\n;else{}"),
        parse("if (x) {} else {}"));
  }

  /**
   * Test all the ASI examples from
   * http://www.ecma-international.org/ecma-262/5.1/#sec-7.9.2
   */
  public void testAutomaticSemicolonInsertionExamplesFromSpec() {
    parseError("{ 1 2 } 3", "Semi-colon expected");

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

  private Node createScript(Node n) {
    Node script = new Node(Token.SCRIPT);
    script.addChildToBack(n);
    return script;
  }

  public void testMethodInObjectLiteral() {
    testMethodInObjectLiteral("var a = {b() {}};");
    testMethodInObjectLiteral("var a = {b() { alert('b'); }};");

    // Static methods not allowed in object literals.
    parseError("var a = {static b() { alert('b'); }};",
        "Cannot use keyword in short object literal");
  }

  private void testMethodInObjectLiteral(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, "this language feature is only supported in es6 mode:"
        + " member declarations");
  }

  public void testExtendedObjectLiteral() {
    testExtendedObjectLiteral("var a = {b};");
    testExtendedObjectLiteral("var a = {b, c};");
    testExtendedObjectLiteral("var a = {b, c: d, e};");

    parseError("var a = { '!@#$%' };", "':' expected");
    parseError("var a = { 123 };", "':' expected");
    parseError("var a = { let };", "Cannot use keyword in short object literal");
    parseError("var a = { else };", "Cannot use keyword in short object literal");
  }

  private void testExtendedObjectLiteral(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, "this language feature is only supported in es6 mode:"
        + " extended object literals");
  }

  public void testComputedPropertiesObjLit() {
    // Method
    testComputedProperty(Joiner.on('\n').join(
        "var x = {",
        "  [prop + '_']() {}",
        "}"));

    // Getter
    testComputedProperty(Joiner.on('\n').join(
        "var x = {",
        "  get [prop + '_']() {}",
        "}"));

    // Setter
    testComputedProperty(Joiner.on('\n').join(
        "var x = {",
        "  set [prop + '_'](val) {}",
        "}"));

    // Generator method
    mode = LanguageMode.ECMASCRIPT6;
    parse(Joiner.on('\n').join(
        "var x = {",
        "  *[prop + '_']() {}",
        "}"));

  }

  public void testComputedMethodClass() {
    mode = LanguageMode.ECMASCRIPT6;
    parse(Joiner.on('\n').join(
        "class X {",
        "  [prop + '_']() {}",
        "}"));

    parse(Joiner.on('\n').join(
        "class X {",
        "  static [prop + '_']() {}",
        "}"));
  }

  public void testComputedProperty() {
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
    parse(js);
    mode = LanguageMode.ECMASCRIPT5;
    String warning = "this language feature is only supported in es6 mode:"
        + " computed property";
    parseWarning(js, warning, warning);
  }

  private void testComputedProperty(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, "this language feature is only supported in es6 mode:"
        + " computed property");
  }

  public void testTrailingCommaWarning1() {
    parse("var a = ['foo', 'bar'];");
  }

  public void testTrailingCommaWarning2() {
    parse("var a = ['foo',,'bar'];");
  }

  public void testTrailingCommaWarning3() {
    parseWarning("var a = ['foo', 'bar',];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var a = ['foo', 'bar',];");
  }

  public void testTrailingCommaWarning4() {
    parseWarning("var a = [,];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var a = [,];");
  }

  public void testTrailingCommaWarning5() {
    parse("var a = {'foo': 'bar'};");
  }

  public void testTrailingCommaWarning6() {
    parseWarning("var a = {'foo': 'bar',};", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var a = {'foo': 'bar',};");
  }

  public void testTrailingCommaWarning7() {
    parseError("var a = {,};",
        "'}' expected");
  }

  public void testSuspiciousBlockCommentWarning1() {
    parseWarning("/* @type {number} */ var x = 3;", SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning2() {
    parseWarning("/* \n * @type {number} */ var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning3() {
    parseWarning("/* \n *@type {number} */ var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning4() {
    parseWarning(
        "  /*\n" +
        "   * @type {number}\n" +
        "   */\n" +
        "  var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning5() {
    parseWarning(
        "  /*\n" +
        "   * some random text here\n" +
        "   * @type {number}\n" +
        "   */\n" +
        "  var x = 3;",
        SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning6() {
    parseWarning("/* @type{number} */ var x = 3;", SUSPICIOUS_COMMENT_WARNING);
  }

  public void testSuspiciousBlockCommentWarning7() {
    // jsdoc tags contain letters only, no underscores etc.
    parse("/* @cc_on */ var x = 3;");
  }

  public void testSuspiciousBlockCommentWarning8() {
    // a jsdoc tag can't be immediately followed by a paren
    parse("/* @TODO(username) */ var x = 3;");
  }

  public void testCatchClauseForbidden() {
    parseError("try { } catch (e if true) {}",
        "')' expected");
  }

  public void testConstForbidden() {
    parseWarning("const x = 3;",
        "this language feature is only supported in es6 mode: " +
        "const declarations");
  }

  public void testAnonymousFunctionExpression() {
    mode = LanguageMode.ECMASCRIPT5;
    parseError("function () {}", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("function () {}", "'identifier' expected");
  }

  public void testArrayDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("var [x,y] = foo();",
        "this language feature is only supported in es6 mode: destructuring");
    parseWarning("[x,y] = foo();",
        "this language feature is only supported in es6 mode: destructuring");

    mode = LanguageMode.ECMASCRIPT6;
    parse("var [x,y] = foo();");
    parse("[x,y] = foo();");
  }

  public void testArrayDestructuringInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var [x=1,y] = foo();");
    parse("[x=1,y] = foo();");
    parse("var [x,y=2] = foo();");
    parse("[x,y=2] = foo();");

    parse("[[a] = ['b']] = [];");
  }

  public void testArrayDestructuringTrailingComma() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("var [x,] = ['x',];", "Array pattern may not end with a comma");
  }

  public void testArrayDestructuringRest() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var [first, ...rest] = foo();");
    parse("let [first, ...rest] = foo();");
    parse("const [first, ...rest] = foo();");

    parseError("var [first, ...more, last] = foo();", "']' expected");
    parseError("var [first, ...[re, st]] = foo();", "lvalues in rest elements must be identifiers");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("var [first, ...rest] = foo();",
        "this language feature is only supported in es6 mode: destructuring");
  }

  public void testArrayDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("function f([x, y]) { use(x); use(y); }");
    parse("function f([x, [y, z]]) {}");
    parse("function f([x, y] = [1, 2]) { use(x); use(y); }");
    parse("function f([x, x]) {}");
  }

  public void testObjectDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {x, y} = foo();");
    parse("var {x: x, y: y} = foo();");
    parse("var {x: {y, z}} = foo();");
    parse("var {x: {y: {z}}} = foo();");

    // Useless, but legal.
    parse("var {} = foo();");
  }

  public void testObjectDestructuringVarWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {x = 1} = foo();");
    parse("var {x: {y = 1}} = foo();");
    parse("var {x: y = 1} = foo();");
    parse("var {x: v1 = 5, y: v2 = 'str'} = foo();");
    parse("var {k1: {k2 : x} = bar(), k3: y} = foo();");
  }

  public void testObjectDestructuringAssign() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("({x, y}) = foo();");
    parse("({x, y} = foo());");
    parse("({x: x, y: y}) = foo();");
    parse("({x: x, y: y} = foo());");
    parse("({x: {y, z}}) = foo();");
    parse("({x: {y, z}} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y}) = foo();");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");

    // Useless, but legal.
    parse("({}) = foo();");
    parse("({} = foo());");
  }

  public void testObjectDestructuringAssignWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("({x = 1}) = foo();");
    parse("({x = 1} = foo());");
    parse("({x: {y = 1}}) = foo();");
    parse("({x: {y = 1}} = foo());");
    parse("({x: y = 1}) = foo();");
    parse("({x: y = 1} = foo());");
    parse("({x: v1 = 5, y: v2 = 'str'}) = foo();");
    parse("({x: v1 = 5, y: v2 = 'str'} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y}) = foo();");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");
  }

  public void testObjectDestructuringWithInitializerInvalid() {
    parseError("var {{x}} = foo();", "'}' expected");
    parseError("({{x}}) = foo();", "'}' expected");
    parseError("({{a} = {a: 'b'}}) = foo();", "'}' expected");
    parseError("({{a : b} = {a: 'b'}}) = foo();", "'}' expected");
  }

  public void testObjectDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("function f({x, y}) { use(x); use(y); }");
    parse("function f({w, x: {y, z}}) {}");
    parse("function f({x, y} = {x:1, y:2}) {}");
    parse("function f({x, x}) {}");
  }

  public void testObjectDestructuringComputedProp() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {[x]: y} = z;");
    parse("var { [foo()] : [x,y,z] = bar() } = baz();");
    parseError("var {[x]} = z;", "':' expected");
  }

  public void testObjectDestructuringStringAndNumberKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {'s': x} = foo();");
    parse("var {3: x} = foo();");

    parseError("var { 'hello world' } = foo();", "':' expected");
    parseError("var { 4 } = foo();", "':' expected");
    parseError("var { 'hello' = 'world' } = foo();", "':' expected");
    parseError("var { 2 = 5 } = foo();", "':' expected");
  }

  public void testObjectDestructuringKeywordKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {if: x, else: y} = foo();");
    parse("var {while: x=1, for: y} = foo();");
    parseError("var {while} = foo();", "cannot use keyword 'while' here.");
    parseError("var {implements} = foo();", "cannot use keyword 'implements' here.");
  }

  public void testObjectDestructuringComplexTarget() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("var {foo: bar.x} = baz();", "'}' expected");
    parse("({foo: bar.x} = baz());");
    parse("for ({foo: bar.x} in baz());");

    parseError("var {foo: bar[x]} = baz();", "'}' expected");
    parse("({foo: bar[x]} = baz());");
    parse("for ({foo: bar[x]} in baz());");
  }

  public void testObjectDestructuringExtraParens() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("({x}) = y;");
    parse("(({x})) = y;");
    parse("((({x}))) = y;");

    parse("([x]) = y;");
    parse("[x, (y)] = z;");
    parse("[x, ([y])] = z;");
    parse("[x, (([y]))] = z;");
  }

  public void testMixedDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {x: [y, z]} = foo();");
    parse("var [x, {y, z}] = foo();");

    parse("({x: [y, z]} = foo());");
    parse("[x, {y, z}] = foo();");

    parse("function f({x: [y, z]}) {}");
    parse("function f([x, {y, z}]) {}");
  }

  public void testMixedDestructuringWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("var {x: [y, z] = [1, 2]} = foo();");
    parse("var [x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("({x: [y, z] = [1, 2]} = foo());");
    parse("[x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("function f({x: [y, z] = [1, 2]}) {}");
    parse("function f([x, {y, z} = {y: 3, z: 4}]) {}");
  }

  public void testDestructuringNoRHS() {
    mode = LanguageMode.ECMASCRIPT6;
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

  public void testComprehensions() {
    mode = LanguageMode.ECMASCRIPT6;
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

  public void testLetForbidden1() {
    parseWarning("let x = 3;",
        "this language feature is only supported in es6 mode:"
        + " let declarations");
  }

  public void testLetForbidden2() {
    parseWarning("function f() { let x = 3; };",
        "this language feature is only supported in es6 mode:"
        + " let declarations");
  }

  public void testLetForbidden3() {
    mode = LanguageMode.ECMASCRIPT5_STRICT;
    parseError("function f() { var let = 3; }",
        "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parseError("function f() { var let = 3; }",
        "'identifier' expected");
  }

  public void testYieldForbidden() {
    parseError("function f() { yield 3; }",
        "primary expression expected");
  }

  public void testGenerator() {
    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parse("var obj = { *f() { yield 3; } };");
    parse("function* f() { yield 3; }");
    parse("function f() { return function* g() {} }");

    mode = LanguageMode.ECMASCRIPT5_STRICT;
    parseWarning("function* f() { yield 3; }",
        "this language feature is only supported in es6 mode: generators");
    parseWarning("var obj = { * f() { yield 3; } };",
        "this language feature is only supported in es6 mode: generators",
        "this language feature is only supported in es6 mode: member declarations");
  }

  public void testBracelessFunctionForbidden() {
    parseError("var sq = function(x) x * x;",
        "'{' expected");
  }

  public void testGeneratorsForbidden() {
    parseError("var i = (x for (x in obj));",
        "')' expected");
  }

  public void testGettersForbidden1() {
    parseError("var x = {get foo() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  public void testGettersForbidden2() {
    parseError("var x = {get foo bar() { return 3; }};",
        "'(' expected");
  }

  public void testGettersForbidden3() {
    parseError("var x = {a getter:function b() { return 3; }};",
        "'}' expected");
  }

  public void testGettersForbidden4() {
    parseError("var x = {\"a\" getter:function b() { return 3; }};",
        "':' expected");
  }

  public void testGettersForbidden5() {
    parseError("var x = {a: 2, get foo() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  public void testGettersForbidden6() {
    parseError("var x = {get 'foo'() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  public void testSettersForbidden() {
    parseError("var x = {set foo(a) { y = 3; }};",
        IRFactory.SETTER_ERROR_MESSAGE);
  }

  public void testSettersForbidden2() {
    // TODO(johnlenz): maybe just report the first error, when not in IDE mode?
    parseError("var x = {a setter:function b() { return 3; }};",
        "'}' expected");
  }

  public void testFileOverviewJSDoc1() {
    isIdeMode = true;

    Node n = parse("/** @fileoverview Hi mom! */ function Foo() {}");
    assertThat(n.getFirstChild().getType()).isEqualTo(Token.FUNCTION);
    assertThat(n.getJSDocInfo()).isNotNull();
    assertThat(n.getFirstChild().getJSDocInfo()).isNull();
    assertThat(n.getJSDocInfo().getFileOverview()).isEqualTo("Hi mom!");
  }

  public void testFileOverviewJSDocDoesNotHoseParsing() {
    assertThat(parse("/** @fileoverview Hi mom! \n */ function Foo() {}").getFirstChild().getType())
        .isEqualTo(Token.FUNCTION);
    assertThat(
        parse("/** @fileoverview Hi mom! \n * * * */ function Foo() {}").getFirstChild().getType())
        .isEqualTo(Token.FUNCTION);
    assertThat(parse("/** @fileoverview \n * x */ function Foo() {}").getFirstChild().getType())
        .isEqualTo(Token.FUNCTION);
    assertThat(parse("/** @fileoverview \n * x \n */ function Foo() {}").getFirstChild().getType())
        .isEqualTo(Token.FUNCTION);
  }

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

  public void testObjectLiteralDoc1() {
    Node n = parse("var x = {/** @type {number} */ 1: 2};");

    Node objectLit = n.getFirstChild().getFirstChild().getFirstChild();
    assertThat(objectLit.getType()).isEqualTo(Token.OBJECTLIT);

    Node number = objectLit.getFirstChild();
    assertThat(number.getType()).isEqualTo(Token.STRING_KEY);
    assertThat(number.getJSDocInfo()).isNotNull();
  }

  public void testDuplicatedParam() {
    parseWarning("function foo(x, x) {}", "Duplicate parameter name \"x\"");
  }

  public void testLet() {
    mode = LanguageMode.ECMASCRIPT3;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT5;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT5_STRICT;
    parseError("var let", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parse("var let");

    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parseError("var let", "'identifier' expected");
  }

  public void testYield1() {
    mode = LanguageMode.ECMASCRIPT3;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT5;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT5_STRICT;
    parseError("var yield", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parse("var yield");

    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parseError("var yield", "'identifier' expected");
  }

  public void testYield2() {
    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parse("function * f() { yield; }");
    parse("function * f() { yield /a/i; }");

    parseError("function * f() { 1 + yield; }", "primary expression expected");
    parseError("function * f() { 1 + yield 2; }", "primary expression expected");
    parseError("function * f() { yield 1 + yield 2; }", "primary expression expected");
    parseError("function * f() { yield(1) + yield(2); }", "primary expression expected");
    parse("function * f() { (yield 1) + (yield 2); }"); // OK
    parse("function * f() { yield * yield; }"); // OK  (yield * (yield))
    parseError("function * f() { yield + yield; }", "primary expression expected");
    parse("function * f() { (yield) + (yield); }"); // OK
    parse("function * f() { return yield; }"); // OK
    parse("function * f() { return yield 1; }"); // OK
  }

  public void testYield3() {
    mode = LanguageMode.ECMASCRIPT6_STRICT;
    // TODO(johnlenz): validate "yield" parsing. Firefox rejects this
    // use of "yield".
    parseError("function * f() { yield , yield; }");
  }

  public void testStringLineContinuation() {
    mode = LanguageMode.ECMASCRIPT3;
    Node n = parseError("'one\\\ntwo';",
        "String continuations are not supported in this language mode.");
    assertThat(n.getFirstChild().getFirstChild().getString()).isEqualTo("onetwo");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("'one\\\ntwo';", "String continuations are not recommended. See"
        + " https://google-styleguide.googlecode.com/svn/trunk/javascriptguide.xml#Multiline_string_literals");
    assertThat(n.getFirstChild().getFirstChild().getString()).isEqualTo("onetwo");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("'one\\\ntwo';", "String continuations are not recommended. See"
        + " https://google-styleguide.googlecode.com/svn/trunk/javascriptguide.xml#Multiline_string_literals");
    assertThat(n.getFirstChild().getFirstChild().getString()).isEqualTo("onetwo");
  }

  public void testStringLiteral() {
    Node n = parse("'foo'");
    Node stringNode = n.getFirstChild().getFirstChild();
    assertThat(stringNode.getType()).isEqualTo(Token.STRING);
    assertThat(stringNode.getString()).isEqualTo("foo");
  }

  private Node testTemplateLiteral(String s) {
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(s,
        "this language feature is only supported in es6 mode: template literals");

    mode = LanguageMode.ECMASCRIPT6;
    return parse(s);
  }

  public void testUseTemplateLiteral() {
    testTemplateLiteral("f`hello world`;");
    testTemplateLiteral("`hello ${name} ${world}`.length;");
  }

  public void testTemplateLiteral() {
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

  public void testTemplateLiteralWithLineContinuation() {
    mode = LanguageMode.ECMASCRIPT6;
    Node n = parseWarning("`string \\\ncontinuation`",
        "String continuations are not recommended. See"
        + " https://google-styleguide.googlecode.com/svn/trunk/javascriptguide.xml#Multiline_string_literals");
    Node templateLiteral = n.getFirstChild().getFirstChild();
    Node stringNode = templateLiteral.getFirstChild();
    assertThat(stringNode.getType()).isEqualTo(Token.STRING);
    assertThat(stringNode.getString()).isEqualTo("string continuation");
  }

  public void testTemplateLiteralSubstitution() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("`hello ${name}`;");
    parse("`hello ${name} ${world}`;");
    parse("`hello ${name }`");
    parseError("`hello ${name", "Expected '}' after expression in template literal");
    parseError("`hello ${name tail}", "Expected '}' after expression in template literal");
  }

  public void testUnterminatedTemplateLiteral() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("`hello",
        "Unterminated template literal");
    parseError("`hello\\`",
        "Unterminated template literal");
  }

  public void testIncorrectEscapeSequenceInTemplateLiteral() {
    parseError("`hello\\x",
        "Hex digit expected");
    parseError("`hello\\x`",
        "Hex digit expected");
  }

  public void testExponentialLiterals() {
    parse("0e0");
    parse("0E0");
    parse("0E1");
    parse("1E0");
    parse("1E-0");
    parse("10E10");
    parse("10E-10");
    parse("1.0E1");
    parseError("01E0",
        "Semi-colon expected");
    parseError("0E",
        "Exponent part must contain at least one digit");
    parseError("1E-",
        "Exponent part must contain at least one digit");
    parseError("1E1.1",
        "Semi-colon expected");
  }

  public void testBinaryLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("0b0001;",
        "Binary integer literals are not supported in this language mode.");
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0b0001;",
        "Binary integer literals are not supported in this language mode.");
    mode = LanguageMode.ECMASCRIPT6;
    parse("0b0001;");
  }

  public void testOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("0o0001;",
        "Octal integer literals are not supported in this language mode.");
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0o0001;",
        "Octal integer literals are not supported in this language mode.");
    mode = LanguageMode.ECMASCRIPT6;
    parse("0o0001;");
  }

  public void testOldStyleOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("0001;",
        "Octal integer literals are not supported in Ecmascript 5 strict mode.");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("0001;",
        "Octal integer literals are not supported in Ecmascript 5 strict mode.");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("0001;",
        "Octal integer literals are not supported in Ecmascript 5 strict mode.");
  }

  // TODO(tbreisacher): We need much clearer error messages for this case.
  public void testInvalidOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("0o08;",
        "Semi-colon expected");

    mode = LanguageMode.ECMASCRIPT5;
    parseError("0o08;",
        "Semi-colon expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("0o08;",
        "Semi-colon expected");
  }

  public void testInvalidOldStyleOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("08;",
        "Invalid number literal.");

    mode = LanguageMode.ECMASCRIPT5;
    parseError("08;",
        "Invalid number literal.");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("08;",
        "Invalid number literal.");
  }

  public void testGetter() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("var x = {get 1(){}};",
        IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get 'a'(){}};",
        IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get a(){}};",
        IRFactory.GETTER_ERROR_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var x = {get 1(){}};");
    parse("var x = {get 'a'(){}};");
    parse("var x = {get a(){}};");
    parseError("var x = {get a(b){}};", "')' expected");
  }

  public void testSetter() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("var x = {set 1(x){}};",
        IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set 'a'(x){}};",
        IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set a(x){}};",
        IRFactory.SETTER_ERROR_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var x = {set 1(x){}};");
    parse("var x = {set 'a'(x){}};");
    parse("var x = {set a(x){}};");
    parseError("var x = {set a(){}};",
        "'identifier' expected");
  }

  public void testLamestWarningEver() {
    // This used to be a warning.
    parse("var x = /** @type {undefined} */ (y);");
    parse("var x = /** @type {void} */ (y);");
  }

  public void testUnfinishedComment() {
    parseError("/** this is a comment ", "unterminated comment");
  }

  public void testHtmlStartCommentAtStartOfLine() {
    parseWarning("<!-- This text is ignored.\nalert(1)", HTML_COMMENT_WARNING);
  }

  public void testHtmlStartComment() {
    parseWarning("alert(1) <!-- This text is ignored.\nalert(2)",
        HTML_COMMENT_WARNING);
  }

  public void testHtmlEndCommentAtStartOfLine() {
    parseWarning("alert(1)\n --> This text is ignored.", HTML_COMMENT_WARNING);
  }

  // "-->" is not the start of a comment, when it is not at the beginning
  // of a line.
  public void testHtmlEndComment() {
    parse("while (x --> 0) {\n  alert(1)\n}");
  }

  public void testParseBlockDescription() {
    isIdeMode = true;

    Node n = parse("/** This is a variable. */ var x;");
    Node var = n.getFirstChild();
    assertThat(var.getJSDocInfo()).isNotNull();
    assertThat(var.getJSDocInfo().getBlockDescription()).isEqualTo("This is a variable.");
  }

  public void testUnnamedFunctionStatement() {
    // Statements
    parseError("function() {};", "'identifier' expected");
    parseError("if (true) { function() {}; }", "'identifier' expected");
    parse("function f() {};");
    // Expressions
    parse("(function f() {});");
    parse("(function () {});");
  }

  public void testReservedKeywords() {
    mode = LanguageMode.ECMASCRIPT3;

    parseError("var boolean;", "identifier is a reserved word");
    parseError("function boolean() {};",
        "identifier is a reserved word");
    parseError("boolean = 1;", "identifier is a reserved word");

    parseError("class = 1;", "'identifier' expected");
    parseError("public = 2;", "primary expression expected");

    mode = LanguageMode.ECMASCRIPT5;

    parse("var boolean;");
    parse("function boolean() {};");
    parse("boolean = 1;");

    parseError("class = 1;", "'identifier' expected");
    // TODO(johnlenz): reenable
    //parse("public = 2;");

    mode = LanguageMode.ECMASCRIPT5_STRICT;

    parse("var boolean;");
    parse("function boolean() {};");
    parse("boolean = 1;");
    parseError("public = 2;", "primary expression expected");

    parseError("class = 1;", "'identifier' expected");
  }

  public void testKeywordsAsProperties() {
    mode = LanguageMode.ECMASCRIPT3;

    parseWarning("var x = {function: 1};", IRFactory.INVALID_ES3_PROP_NAME);
    parseWarning("x.function;", IRFactory.INVALID_ES3_PROP_NAME);
    parseError("var x = {get x(){} };",
        IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get function(){} };", IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get 'function'(){} };",
        IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {get 1(){} };",
        IRFactory.GETTER_ERROR_MESSAGE);
    parseError("var x = {set function(a){} };", IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set 'function'(a){} };",
        IRFactory.SETTER_ERROR_MESSAGE);
    parseError("var x = {set 1(a){} };",
        IRFactory.SETTER_ERROR_MESSAGE);
    parseWarning("var x = {class: 1};", IRFactory.INVALID_ES3_PROP_NAME);
    parse("var x = {'class': 1};");
    parseWarning("x.class;", IRFactory.INVALID_ES3_PROP_NAME);
    parse("x['class'];");
    parse("var x = {let: 1};");  // 'let' is not reserved in ES3
    parse("x.let;");
    parse("var x = {yield: 1};"); // 'yield' is not reserved in ES3
    parse("x.yield;");
    parseWarning("x.prototype.catch = function() {};",
        IRFactory.INVALID_ES3_PROP_NAME);
    parseWarning("x().catch();", IRFactory.INVALID_ES3_PROP_NAME);

    mode = LanguageMode.ECMASCRIPT5;

    parse("var x = {function: 1};");
    parse("x.function;");
    parse("var x = {get function(){} };");
    parse("var x = {get 'function'(){} };");
    parse("var x = {get 1(){} };");
    parse("var x = {set function(a){} };");
    parse("var x = {set 'function'(a){} };");
    parse("var x = {set 1(a){} };");
    parse("var x = {class: 1};");
    parse("x.class;");
    parse("var x = {let: 1};");
    parse("x.let;");
    parse("var x = {yield: 1};");
    parse("x.yield;");
    parse("x.prototype.catch = function() {};");
    parse("x().catch();");

    mode = LanguageMode.ECMASCRIPT5_STRICT;

    parse("var x = {function: 1};");
    parse("x.function;");
    parse("var x = {get function(){} };");
    parse("var x = {get 'function'(){} };");
    parse("var x = {get 1(){} };");
    parse("var x = {set function(a){} };");
    parse("var x = {set 'function'(a){} };");
    parse("var x = {set 1(a){} };");
    parse("var x = {class: 1};");
    parse("x.class;");
    parse("var x = {let: 1};");
    parse("x.let;");
    parse("var x = {yield: 1};");
    parse("x.yield;");
    parse("x.prototype.catch = function() {};");
    parse("x().catch();");
  }

  public void testKeywordsAsPropertiesInExterns1() {
    mode = LanguageMode.ECMASCRIPT3;

    parse("/** @fileoverview\n@externs\n*/\n var x = {function: 1};");
  }

  public void testKeywordsAsPropertiesInExterns2() {
    mode = LanguageMode.ECMASCRIPT3;

    parse("/** @fileoverview\n@externs\n*/\n var x = {}; x.function + 1;");
  }

  public void testUnicodeInIdentifiers() {
    parse("var \\u00fb");
    parse("Js\\u00C7ompiler");
    parse("Js\\u0043ompiler");
  }

  public void testUnicodePointEscapeInIdentifiers() {
    parse("var \\u{0043}");
    parse("Js\\u{0043}ompiler");
    parse("Js\\u{765}ompiler");
  }

  public void testUnicodePointEscapeStringLiterals() {
    parse("var i = \'\\u0043ompiler\'");
    parse("var i = \'\\u{43}ompiler\'");
    parse("var i = \'\\u{1f42a}ompiler\'");
    parse("var i = \'\\u{2603}ompiler\'");
    parse("var i = \'\\u{1}ompiler\'");
  }

  public void testInvalidUnicodePointEscapeInIdentifiers() {
    parseError("var \\u{defg", "Invalid escape sequence");
    parseError("var \\u{defgRestOfIdentifier", "Invalid escape sequence");
    parseError("var \\u{DEFG}", "Invalid escape sequence");
    parseError("Js\\u{}ompiler", "Invalid escape sequence");
    // Legal unicode but invalid in identifier
    parseError("Js\\u{99}ompiler", "Invalid escape sequence");
    parseError("Js\\u{10000}ompiler", "Invalid escape sequence");
  }

  public void testInvalidUnicodePointEscapeStringLiterals() {
    parseError("var i = \'\\u{defg\'", "Hex digit expected");
    parseError("var i = \'\\u{defgRestOfIdentifier\'", "Hex digit expected");
    parseError("var i = \'\\u{DEFG}\'", "Hex digit expected");
    parseError("var i = \'Js\\u{}ompiler\'", "Empty unicode escape");
    parseError("var i = \'\\u{345", "Hex digit expected");
  }

  public void testInvalidEscape() {
    parseError("var \\x39abc", "Invalid escape sequence");
    parseError("var abc\\t", "Invalid escape sequence");
  }

  public void testEOFInUnicodeEscape() {
    parseError("var \\u1", "Invalid escape sequence");
    parseError("var \\u12", "Invalid escape sequence");
    parseError("var \\u123", "Invalid escape sequence");
  }

  public void testEndOfIdentifierInUnicodeEscape() {
    parseError("var \\u1 = 1;", "Invalid escape sequence");
    parseError("var \\u12 = 2;", "Invalid escape sequence");
    parseError("var \\u123 = 3;", "Invalid escape sequence");
  }

  public void testInvalidUnicodeEscape() {
    parseError("var \\uDEFG", "Invalid escape sequence");
  }

  public void testUnicodeEscapeInvalidIdentifierStart() {
    parseError("var \\u0037yler",
        "Character '7' (U+0037) is not a valid identifier start char");
    parseError("var \\u{37}yler",
        "Character '7' (U+0037) is not a valid identifier start char");
    parseError("var \\u0020space",
        "Invalid escape sequence");
  }

  public void testUnicodeEscapeInvalidIdentifierChar() {
    parseError("var sp\\u0020ce",
        "Invalid escape sequence");
  }

  /**
   * It is illegal to use a keyword as an identifier, even if you use
   * unicode escapes to obscure the fact that you are trying do that.
   */
  public void testKeywordAsIdentifier() {
    parseError("var while;", "'identifier' expected");
    parseError("var wh\\u0069le;", "'identifier' expected");
  }

  public void testGetPropFunctionName() {
    parseError("function a.b() {}",
        "'(' expected");
    parseError("var x = function a.b() {}",
        "'(' expected");
  }

  public void testGetPropFunctionNameIdeMode() {
    // In IDE mode, we try to fix up the tree, but sometimes
    // this leads to even more errors.
    isIdeMode = true;
    parseError("function a.b() {}",
        "'(' expected",
        "',' expected",
        "Invalid trailing comma in formal parameter list");
    parseError("var x = function a.b() {}",
        "'(' expected",
        "',' expected",
        "Invalid trailing comma in formal parameter list");
  }

  public void testIdeModePartialTree() {
    Node partialTree = parseError("function Foo() {} f.",
        "'identifier' expected");
    assertThat(partialTree).isNull();

    isIdeMode = true;
    partialTree = parseError("function Foo() {} f.",
        "'identifier' expected");
    assertThat(partialTree).isNotNull();
  }

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

  public void testMisplacedTypeAnnotation1() {
    // misuse with COMMA
    parseWarning(
        "var o = {};" +
        "/** @type {string} */ o.prop1 = 1, o.prop2 = 2;",
        MISPLACED_TYPE_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation2() {
    // missing parentheses for the cast.
    parseWarning(
        "var o = /** @type {string} */ getValue();",
        MISPLACED_TYPE_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation3() {
    // missing parentheses for the cast.
    parseWarning(
        "var o = 1 + /** @type {string} */ value;",
        MISPLACED_TYPE_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation4() {
    // missing parentheses for the cast.
    parseWarning(
        "var o = /** @type {!Array.<string>} */ ['hello', 'you'];",
        MISPLACED_TYPE_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation5() {
    // missing parentheses for the cast.
    parseWarning(
        "var o = (/** @type {!Foo} */ {});",
        MISPLACED_TYPE_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation6() {
    parseWarning("var o = /** @type {function():string} */ function() {return 'str';}",
        MISPLACED_TYPE_ANNOTATION);
  }

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

  public void testValidTypeAnnotation2() {
    mode = LanguageMode.ECMASCRIPT5;
    parse("var o = { /** @type {string} */ get prop() { return 'str' }};");
    parse("var o = { /** @type {string} */ set prop(s) {}};");
  }

  public void testValidTypeAnnotation3() {
    // This one we don't currently support in the type checker but
    // we would like to.
    parse("try {} catch (/** @type {Error} */ e) {}");
  }

  public void testValidTypeAnnotation4() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("/** @type {number} */ export var x = 3;");
  }

  public void testParsingAssociativity() {
    assertNodeEquality(parse("x * y * z"), parse("(x * y) * z"));
    assertNodeEquality(parse("x + y + z"), parse("(x + y) + z"));
    assertNodeEquality(parse("x | y | z"), parse("(x | y) | z"));
    assertNodeEquality(parse("x & y & z"), parse("(x & y) & z"));
    assertNodeEquality(parse("x ^ y ^ z"), parse("(x ^ y) ^ z"));
    assertNodeEquality(parse("x || y || z"), parse("(x || y) || z"));
    assertNodeEquality(parse("x && y && z"), parse("(x && y) && z"));
  }

  public void testIssue1116() {
    parse("/**/");
  }

  public void testUnterminatedStringLiteral() {
    parseError("var unterm = 'forgot closing quote",
        "Unterminated string literal");

    parseError("var unterm = 'forgot closing quote\n"
        + "alert(unterm);",
        "Unterminated string literal");
  }

  /**
   * @bug 14231379
   */
  public void testUnterminatedRegExp() {
    parseError("var unterm = /forgot trailing slash",
        "Expected '/' in regular expression literal");

    parseError("var unterm = /forgot trailing slash\n" +
        "alert(unterm);",
        "Expected '/' in regular expression literal");
  }

  public void testRegExp() {
    assertNodeEquality(parse("/a/"), script(expr(regex("a"))));
    assertNodeEquality(parse("/\\\\/"), script(expr(regex("\\\\"))));
    assertNodeEquality(parse("/\\s/"), script(expr(regex("\\s"))));
    assertNodeEquality(parse("/\\u000A/"), script(expr(regex("\\u000A"))));
    assertNodeEquality(parse("/[\\]]/"), script(expr(regex("[\\]]"))));
  }

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

  /**
   * New RegExp flags added in ES6.
   */
  public void testES6RegExpFlags() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("/a/y");
    parse("/a/u");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("/a/y",
        "this language feature is only supported in es6 mode: new RegExp flag 'y'");
    parseWarning("/a/u",
        "this language feature is only supported in es6 mode: new RegExp flag 'u'");
    parseWarning("/a/yu",
        "this language feature is only supported in es6 mode: new RegExp flag 'y'",
        "this language feature is only supported in es6 mode: new RegExp flag 'u'");
  }

  public void testDefaultParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("function f(a, b=0) {}");
    parse("function f(a, b=0, c) {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("function f(a, b=0) {}",
        "this language feature is only supported in es6 mode: default parameters");
  }

  public void testDefaultParametersWithRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("function f(a=0, ...b) {}");
    parse("function f(a, b=0, ...c) {}");
    parse("function f(a, b=0, c=1, ...d) {}");
  }

  public void testClass1() {
    mode = LanguageMode.ECMASCRIPT6;
    parse("class C {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("class C {}",
        "this language feature is only supported in es6 mode: class");

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("class C {}",
        "this language feature is only supported in es6 mode: class");

  }

  public void testClass2() {
    mode = LanguageMode.ECMASCRIPT6;
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

  public void testClass3() {
    mode = LanguageMode.ECMASCRIPT6;
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

  public void testClassKeywordsAsMethodNames() {
    mode = LanguageMode.ECMASCRIPT6;
    parse(Joiner.on('\n').join(
        "class KeywordMethods {",
        "  continue() {}",
        "  throw() {}",
        "  else() {}",
        "}"));
  }

  public void testSuper1() {
    mode = LanguageMode.ECMASCRIPT6;

    // TODO(johnlenz): super in global scope should be a syntax error
    parse("super;");

    parse("function f() {super;};");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("super;",
        "this language feature is only supported in es6 mode: super");

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("super;",
        "this language feature is only supported in es6 mode: super");
  }

  public void testArrow1() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("()=>1;");
    parse("()=>{}");
    parse("(a,b) => a + b;");
    parse("a => b");
    parse("a => { return b }");
    parse("a => b");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("a => b",
        "this language feature is only supported in es6 mode: " +
        "short function syntax");

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("a => b;",
        "this language feature is only supported in es6 mode: " +
        "short function syntax");
  }

  public void testArrowInvalid() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("*()=>1;", "primary expression expected");
    parseError("var f = x\n=>2", "No newline allowed before '=>'");
    parseError("f = (x,y)\n=>2;", "No newline allowed before '=>'");
    parseError("f( (x,y)\n=>2)", "No newline allowed before '=>'");
  }

  public void testForIn_ES6() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("for (a in b) c;");
    parse("for (var a in b) c;");
    parse("for (let a in b) c;");
    parse("for (const a in b) c;");

    parseError("for (a=1 in b) c;", "';' expected");
    parseError("for (let a=1 in b) c;",
        "for-in statement may not have initializer");
    parseError("for (const a=1 in b) c;",
        "for-in statement may not have initializer");
    parseError("for (var a=1 in b) c;",
        "for-in statement may not have initializer");
  }

  public void testForIn_ES5() {
    mode = LanguageMode.ECMASCRIPT5;

    parse("for (a in b) c;");
    parse("for (var a in b) c;");

    parseError("for (a=1 in b) c;", "';' expected");
    parseWarning("for (var a=1 in b) c;",
        "for-in statement should not have initializer");
  }

  public void testForInDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("for ({a} in b) c;");
    parse("for (var {a} in b) c;");
    parse("for (let {a} in b) c;");
    parse("for (const {a} in b) c;");

    parse("for ({a: b} in c) d;");
    parse("for (var {a: b} in c) d;");
    parse("for (let {a: b} in c) d;");
    parse("for (const {a: b} in c) d;");

    parse("for ([a] in b) c;");
    parse("for (var [a] in b) c;");
    parse("for (let [a] in b) c;");
    parse("for (const [a] in b) c;");

    parseError("for ({a: b} = foo() in c) d;", "';' expected");
    parseError("for (let {a: b} = foo() in c) d;",
        "for-in statement may not have initializer");
    parseError("for (const {a: b} = foo() in c) d;",
        "for-in statement may not have initializer");
    parseError("for (var {a: b} = foo() in c) d;",
        "for-in statement may not have initializer");

    parseError("for ([a] = foo() in b) c;",
        "';' expected");
    parseError("for (let [a] = foo() in b) c;",
        "for-in statement may not have initializer");
    parseError("for (const [a] = foo() in b) c;",
        "for-in statement may not have initializer");
    parseError("for (var [a] = foo() in b) c;",
        "for-in statement may not have initializer");
  }

  public void testForOf1() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("for(a of b) c;");
    parse("for(let a of b) c;");
    parse("for(const a of b) c;");
  }

  public void testForOf2() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("for(a=1 of b) c;", "';' expected");
    parseError("for(let a=1 of b) c;",
        "for-of statement may not have initializer");
    parseError("for(const a=1 of b) c;",
        "for-of statement may not have initializer");
  }

  public void testInvalidDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;

    // {x: 5} and {x: 'str'} are valid object literals but not valid patterns.
    parseError("for ({x: 5} in foo()) {}", "Invalid LHS for a for-in loop");
    parseError("for ({x: 'str'} in foo()) {}", "Invalid LHS for a for-in loop");
    parseError("var {x: 5} = foo();", "'identifier' expected");
    parseError("var {x: 'str'} = foo();", "'identifier' expected");
    parseError("({x: 5} = foo());", "invalid assignment target");
    parseError("({x: 'str'} = foo());", "invalid assignment target");

    // {method(){}} is a valid object literal but not a valid object pattern.
    parseError("function f({method(){}}) {}", "'}' expected");
    parseError("function f({method(){}} = foo()) {}", "'}' expected");
  }

  public void testForOfPatterns() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("for({x} of b) c;");
    parse("for({x: y} of b) c;");
    parse("for([x, y] of b) c;");
    parse("for([x, ...y] of b) c;");

    parse("for(let {x} of b) c;");
    parse("for(let {x: y} of b) c;");
    parse("for(let [x, y] of b) c;");
    parse("for(let [x, ...y] of b) c;");

    parse("for(const {x} of b) c;");
    parse("for(const {x: y} of b) c;");
    parse("for(const [x, y] of b) c;");
    parse("for(const [x, ...y] of b) c;");

    parse("for(var {x} of b) c;");
    parse("for(var {x: y} of b) c;");
    parse("for(var [x, y] of b) c;");
    parse("for(var [x, ...y] of b) c;");
  }

  public void testForOfPatternsWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("for({x}=a of b) c;", "';' expected");
    parseError("for({x: y}=a of b) c;", "';' expected");
    parseError("for([x, y]=a of b) c;", "';' expected");
    parseError("for([x, ...y]=a of b) c;", "';' expected");

    parseError("for(let {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, ...y]=a of b) c;", "for-of statement may not have initializer");

    parseError("for(const {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, ...y]=a of b) c;", "for-of statement may not have initializer");
  }

  public void testImport() {
    mode = LanguageMode.ECMASCRIPT6;

    parse("import 'someModule'");
    parse("import d from './someModule'");
    parse("import {} from './someModule'");
    parse("import {x, y} from './someModule'");
    parse("import {x as x1, y as y1} from './someModule'");
    parse("import {x as x1, y as y1, } from './someModule'");
    parse("import * as sm from './someModule'");
  }

  public void testShebang() {
    parse("#!/usr/bin/node\n var x = 1;");
    parseError("var x = 1; \n #!/usr/bin/node",
        "primary expression expected");
  }


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

  private Node script(Node stmt) {
    Node n = new Node(Token.SCRIPT, stmt);
    n.setIsSyntheticBlock(true);
    return n;
  }

  private Node expr(Node n) {
    return new Node(Token.EXPR_RESULT, n);
  }

  private Node regex(String regex) {
    return new Node(Token.REGEXP, Node.newString(regex));
  }

  /**
   * Verify that the given code has the given parse errors.
   * @return If in IDE mode, returns a partial tree.
   */
  private Node parseError(String source, String... errors) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(errors, null);
    ParseResult result = ParserRunner.parse(
        new SimpleSourceFile("input", false),
        source,
        ParserRunner.createConfig(isIdeMode, mode, false, null),
        testErrorReporter);
    Node script = result.ast;

    // verifying that all errors were seen
    assertThat(testErrorReporter.hasEncounteredAllErrors()).isTrue();
    assertThat(testErrorReporter.hasEncounteredAllWarnings()).isTrue();

    return script;
  }


  /**
   * Verify that the given code has the given parse warnings.
   * @return The parse tree.
   */
  private Node parseWarning(String string, String... warnings) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    StaticSourceFile file = new SimpleSourceFile("input", false);
    Node script = ParserRunner.parse(
        file,
        string,
        ParserRunner.createConfig(isIdeMode, mode, false, null),
        testErrorReporter).ast;

    // verifying that all warnings were seen
    assertThat(testErrorReporter.hasEncounteredAllErrors()).isTrue();
    assertThat(testErrorReporter.hasEncounteredAllWarnings()).isTrue();

    return script;
  }

  /**
   * Verify that the given code has no parse warnings or errors.
   * @return The parse tree.
   */
  private Node parse(String string) {
    return parseWarning(string);
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
