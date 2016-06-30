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
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;

import java.util.List;

public final class ParserTest extends BaseJSTypeTestCase {
  private static final String SUSPICIOUS_COMMENT_WARNING =
      IRFactory.SUSPICIOUS_COMMENT_WARNING;

  private static final String TRAILING_COMMA_MESSAGE =
      "Trailing comma is not legal in an ECMA-262 object initializer";

  private static final String MISSING_GT_MESSAGE =
      "Bad type annotation. missing closing >";


  private static final String UNLABELED_BREAK = "unlabelled break must be inside loop or switch";

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

  private static final String INVALID_ASSIGNMENT_TARGET = "invalid assignment target";

  private Config.LanguageMode mode;
  private boolean isIdeMode = false;
  private FeatureSet expectedFeatures;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mode = LanguageMode.ECMASCRIPT3;
    isIdeMode = false;
    expectedFeatures = FeatureSet.ES3;
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
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.FOR_OF);
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
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.FOR_OF);
    parse(""
        + "for (var x of [1, 2, 3]) {\n"
        + "  if (x == 2) continue;\n"
        + "}");
  }

  /** @bug 19100575 */
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
    Node assign = parse("a = b").getFirstFirstChild();

    assertNode(assign).hasType(Token.ASSIGN);
    assertNode(assign).hasLineno(1);
    assertNode(assign).hasCharno(0);
  }

  public void testLinenoCharnoAssign2() throws Exception {
    Node assign = parse("\n a.g.h.k    =  45").getFirstFirstChild();

    assertNode(assign).hasType(Token.ASSIGN);
    assertNode(assign).hasLineno(2);
    assertNode(assign).hasCharno(1);
  }

  public void testLinenoCharnoCall() throws Exception {
    Node call = parse("\n foo(123);").getFirstFirstChild();

    assertNode(call).hasType(Token.CALL);
    assertNode(call).hasLineno(2);
    assertNode(call).hasCharno(1);
  }

  public void testLinenoCharnoGetProp1() throws Exception {
    Node getprop = parse("\n foo.bar").getFirstFirstChild();

    assertNode(getprop).hasType(Token.GETPROP);
    assertNode(getprop).hasLineno(2);
    assertNode(getprop).hasCharno(1);

    Node name = getprop.getSecondChild();
    assertNode(name).hasType(Token.STRING);
    assertNode(name).hasLineno(2);
    assertNode(name).hasCharno(5);
  }

  public void testLinenoCharnoGetProp2() throws Exception {
    Node getprop = parse("\n foo.\nbar").getFirstFirstChild();

    assertNode(getprop).hasType(Token.GETPROP);
    assertNode(getprop).hasLineno(2);
    assertNode(getprop).hasCharno(1);

    Node name = getprop.getSecondChild();
    assertNode(name).hasType(Token.STRING);
    assertNode(name).hasLineno(3);
    assertNode(name).hasCharno(0);
  }

  public void testLinenoCharnoGetelem1() throws Exception {
    Node call = parse("\n foo[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(2);
    assertNode(call).hasCharno(1);
  }

  public void testLinenoCharnoGetelem2() throws Exception {
    Node call = parse("\n   \n foo()[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(3);
    assertNode(call).hasCharno(1);
  }

  public void testLinenoCharnoGetelem3() throws Exception {
    Node call = parse("\n   \n (8 + kl)[123]").getFirstFirstChild();

    assertNode(call).hasType(Token.GETELEM);
    assertNode(call).hasLineno(3);
    assertNode(call).hasCharno(1);
  }

  public void testLinenoCharnoForComparison() throws Exception {
    Node lt =
      parse("for (; i < j;){}").getFirstChild().getSecondChild();

    assertNode(lt).hasType(Token.LT);
    assertNode(lt).hasLineno(1);
    assertNode(lt).hasCharno(7);
  }

  public void testLinenoCharnoHook() throws Exception {
    Node n = parse("\n a ? 9 : 0").getFirstFirstChild();

    assertNode(n).hasType(Token.HOOK);
    assertNode(n).hasLineno(2);
    assertNode(n).hasCharno(1);
  }

  public void testLinenoCharnoArrayLiteral() throws Exception {
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

  public void testLinenoCharnoObjectLiteral() throws Exception {
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
        getFirstFirstChild();

    assertNode(op).hasLineno(1);
    assertNode(op).hasCharno(8);
  }

  public void testJSDocAttachment1() {
    Node varNode = parse("/** @type {number} */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
    JSDocInfo varInfo = varNode.getJSDocInfo();
    assertThat(varInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, varInfo.getType());

    // VAR NAME
    Node varNameNode = varNode.getFirstChild();
    assertNode(varNameNode).hasType(Token.NAME);
    assertThat(varNameNode.getJSDocInfo()).isNull();

    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.LET_DECLARATIONS);

    Node letNode = parse("/** @type {number} */let a;").getFirstChild();

    // LET
    assertNode(letNode).hasType(Token.LET);
    JSDocInfo letInfo = letNode.getJSDocInfo();
    assertThat(letInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, letInfo.getType());

    // LET NAME
    Node letNameNode = letNode.getFirstChild();
    assertNode(letNameNode).hasType(Token.NAME);
    assertThat(letNameNode.getJSDocInfo()).isNull();

    expectFeatures(Feature.CONST_DECLARATIONS);
    Node constNode = parse("/** @type {number} */const a = 0;").getFirstChild();

    // CONST
    assertNode(constNode).hasType(Token.CONST);
    JSDocInfo constInfo = constNode.getJSDocInfo();
    assertThat(constInfo).isNotNull();
    assertTypeEquals(NUMBER_TYPE, constInfo.getType());

    // CONST NAME
    Node constNameNode = constNode.getFirstChild();
    assertNode(constNameNode).hasType(Token.NAME);
    assertThat(constNameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment2() {
    Node varNode = parse("/** @type {number} */var a,b;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
    JSDocInfo info = varNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(NUMBER_TYPE, info.getType());

    // First NAME
    Node nameNode1 = varNode.getFirstChild();
    assertNode(nameNode1).hasType(Token.NAME);
    assertThat(nameNode1.getJSDocInfo()).isNull();

    // Second NAME
    Node nameNode2 = nameNode1.getNext();
    assertNode(nameNode2).hasType(Token.NAME);
    assertThat(nameNode2.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment3() {
    Node assignNode = parse("/** @type {number} */goog.FOO = 5;").getFirstFirstChild();
    assertNode(assignNode).hasType(Token.ASSIGN);
    JSDocInfo info = assignNode.getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(NUMBER_TYPE, info.getType());
  }

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
   * Tests that a JSDoc comment in an unexpected place of the code does not
   * propagate to following code due to {@link JSDocInfo} aggregation.
   */
  public void testJSDocAttachment6() throws Exception {
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

  public void testJSDocAttachment7() {
    Node varNode = parse("/** */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment8() {
    Node varNode = parse("/** x */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment9() {
    Node varNode = parse("/** \n x */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment10() {
    Node varNode = parse("/** x\n */var a;").getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);

    // NAME
    Node nameNode = varNode.getFirstChild();
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment11() {
    Node varNode =
       parse("/** @type {{x : number, 'y' : string, z}} */var a;")
        .getFirstChild();

    // VAR
    assertNode(varNode).hasType(Token.VAR);
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
    assertNode(nameNode).hasType(Token.NAME);
    assertThat(nameNode.getJSDocInfo()).isNull();
  }

  public void testJSDocAttachment12() {
    Node varNode =
       parse("var a = {/** @type {Object} */ b: c};")
        .getFirstChild();
    Node objectLitNode = varNode.getFirstFirstChild();
    assertNode(objectLitNode).hasType(Token.OBJECTLIT);
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
    assertNode(exprCall).hasType(Token.EXPR_RESULT);
    assertThat(exprCall.getNext().getJSDocInfo()).isNull();
    assertThat(exprCall.getFirstChild().getJSDocInfo()).isNotNull();
  }

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

  public void testJSDocAttachment21() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.CONST_DECLARATIONS);
    parse("/** @param {string} x */ const f = function() {};");

    expectFeatures(Feature.LET_DECLARATIONS);
    parse("/** @param {string} x */ let f = function() {};");
  }

  // Tests that JSDoc gets attached to the children of export nodes, and there are no warnings.
  // See https://github.com/google/closure-compiler/issues/781
  public void testJSDocAttachment22() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.MODULES);

    Node n = parse("/** @param {string} x */ export function f(x) {};");
    Node export = n.getFirstChild();

    assertNode(export).hasType(Token.EXPORT);
    assertThat(export.getJSDocInfo()).isNull();
    assertThat(export.getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(export.getFirstChild().getJSDocInfo().hasParameter("x")).isTrue();
  }

  public void testInlineJSDocAttachment1() {
    Node fn = parse("function f(/** string */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testInlineJSDocAttachment2() {
    Node fn = parse(
        "function f(/** ? */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(UNKNOWN_TYPE, info.getType());
  }

  public void testInlineJSDocAttachment3() {
    parse("function f(/** @type {string} */ x) {}");
  }

  public void testInlineJSDocAttachment4() {
    parse(
        "function f(/**\n" +
        " * @type {string}\n" +
        " */ x) {}");
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
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertTypeEquals(
        createRecordTypeBuilder().addProperty("attr", NUMBER_TYPE, null).build(), info.getType());
  }

  public void testInlineJSDocWithOptionalType() {
    Node fn = parse("function f(/** string= */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info.getType().isOptionalArg()).isTrue();
  }

  public void testInlineJSDocWithVarArgs() {
    Node fn = parse("function f(/** ...string */ x) {}").getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);

    JSDocInfo info = fn.getSecondChild().getFirstChild().getJSDocInfo();
    assertThat(info.getType().isVarArgs()).isTrue();
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing1() throws Exception {
    assertNodeEquality(
        parse("var a = [1,2]"),
        parseWarning("/** @type {Array<number} */var a = [1,2]",
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
        parse("/** @return {boolean} */" +
            "C.prototype.say=function(nums) {alert(nums.join(','));};"));
  }

  public void testIncorrectJSDocDoesNotAlterJSParsing5() throws Exception {
    assertNodeEquality(
        parse("C.prototype.say=function(nums) {alert(nums.join(','));};"),
        parse("/** @param {boolean} this is some string*/" +
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

  public void testMisplacedDescAnnotation_noWarning() {
    parse("/** @desc Foo. */ var MSG_BAR = goog.getMsg('hello');");
    parse("/** @desc Foo. */ x.y.z.MSG_BAR = goog.getMsg('hello');");
    parse("/** @desc Foo. */ MSG_BAR = goog.getMsg('hello');");
    parse("var msgs = {/** @desc x */ MSG_X: goog.getMsg('x')}");
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

  public void testPostfixExpression() {
    parse("a++");
    parse("a.b--");
    parse("a[0]++");

    parseError("a()++", "Invalid postfix increment operand.");
    parseError("(new C)--", "Invalid postfix decrement operand.");
    parseError("this++", "Invalid postfix increment operand.");
    parseError("(a--)++", "Invalid postfix increment operand.");
    parseError("(+a)++", "Invalid postfix increment operand.");
    parseError("[1,2]++", "Invalid postfix increment operand.");
    parseError("'literal'++", "Invalid postfix increment operand.");
  }

  public void testUnaryExpression() {
    mode = LanguageMode.ECMASCRIPT6;
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
    expectFeatures(Feature.EXTENDED_OBJECT_LITERALS);
    testMethodInObjectLiteral("var a = {b() {}};");
    testMethodInObjectLiteral("var a = {b() { alert('b'); }};");

    // Static methods not allowed in object literals.
    expectFeatures();
    parseError("var a = {static b() { alert('b'); }};",
        "Cannot use keyword in short object literal");
  }

  private void testMethodInObjectLiteral(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.MEMBER_DECLARATIONS));
  }

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
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.EXTENDED_OBJECT_LITERALS));
  }

  public void testComputedPropertiesObjLit() {
    expectFeatures(Feature.COMPUTED_PROPERTIES);

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
    expectFeatures(Feature.CLASSES, Feature.COMPUTED_PROPERTIES);
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
    parse(js);
    mode = LanguageMode.ECMASCRIPT5;
    String warning = getRequiresEs6Message(Feature.COMPUTED_PROPERTIES);
    parseWarning(js, warning, warning);
  }

  private void testComputedProperty(String js) {
    mode = LanguageMode.ECMASCRIPT6;
    parse(js);

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(js, getRequiresEs6Message(Feature.COMPUTED_PROPERTIES));
  }

  public void testTrailingCommaWarning1() {
    parse("var a = ['foo', 'bar'];");
  }

  public void testTrailingCommaWarning2() {
    parse("var a = ['foo',,'bar'];");
  }

  public void testTrailingCommaWarning3() {
    expectFeatures(Feature.TRAILING_COMMA);
    parseWarning("var a = ['foo', 'bar',];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var a = ['foo', 'bar',];");
  }

  public void testTrailingCommaWarning4() {
    expectFeatures(Feature.TRAILING_COMMA);
    parseWarning("var a = [,];", TRAILING_COMMA_MESSAGE);
    mode = LanguageMode.ECMASCRIPT5;
    parse("var a = [,];");
  }

  public void testTrailingCommaWarning5() {
    parse("var a = {'foo': 'bar'};");
  }

  public void testTrailingCommaWarning6() {
    expectFeatures(Feature.TRAILING_COMMA);
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
    expectFeatures(Feature.CONST_DECLARATIONS);
    parseWarning("const x = 3;",
        getRequiresEs6Message(Feature.CONST_DECLARATIONS));
  }

  public void testAnonymousFunctionExpression() {
    mode = LanguageMode.ECMASCRIPT5;
    parseError("function () {}", "'identifier' expected");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("function () {}", "'identifier' expected");
  }

  public void testArrayDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.DESTRUCTURING);
    parseWarning("var [x,y] = foo();",
        getRequiresEs6Message(Feature.DESTRUCTURING));

    mode = LanguageMode.ECMASCRIPT6;
    parse("var [x,y] = foo();");
    // arbitrary LHS assignment target not allowed
    parseError(
        "var [x,y[15]] = foo();", "Only an identifier or destructuring pattern is allowed here.");
  }

  public void testArrayDestructuringAssign() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.DESTRUCTURING);
    parseWarning("[x,y] = foo();",
        getRequiresEs6Message(Feature.DESTRUCTURING));

    mode = LanguageMode.ECMASCRIPT6;
    parse("[x,y] = foo();");
    // arbitrary LHS assignment target is allowed
    parse("[x,y[15]] = foo();");
  }

  public void testArrayDestructuringInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var [x=1,y] = foo();");
    parse("[x=1,y] = foo();");
    parse("var [x,y=2] = foo();");
    parse("[x,y=2] = foo();");

    parse("var [[a] = ['b']] = [];");
    parse("[[a] = ['b']] = [];");
    // arbitrary LHS target allowed in assignment, but not declaration
    parse("[[a.x] = ['b']] = [];");
    parseError(
        "var [[a.x] = ['b']] = [];",
        "Only an identifier or destructuring pattern is allowed here.");
  }

  public void testArrayDestructuringTrailingComma() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING, Feature.TRAILING_COMMA);
    parseError("var [x,] = ['x',];", "Array pattern may not end with a comma");
  }

  public void testArrayDestructuringDeclarationRest() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.DESTRUCTURING, Feature.REST_PARAMETERS);
    parse("var [first, ...rest] = foo();");

    expectFeatures(Feature.DESTRUCTURING, Feature.REST_PARAMETERS, Feature.LET_DECLARATIONS);
    parse("let [first, ...rest] = foo();");

    expectFeatures(Feature.DESTRUCTURING, Feature.REST_PARAMETERS, Feature.CONST_DECLARATIONS);
    parse("const [first, ...rest] = foo();");

    // nested destructuring in regular parameters and rest parameters
    expectFeatures(Feature.DESTRUCTURING, Feature.REST_PARAMETERS);
    parse("var [first, {a, b}, ...[re, st, ...{length}]] = foo();");

    parseError(
        "var [first, ...more = 'default'] = foo();",
        "A default value cannot be specified after '...'");
    parseError("var [first, ...more, last] = foo();", "']' expected");


    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "var [first, ...rest] = foo();",
        getRequiresEs6Message(Feature.DESTRUCTURING));
  }

  public void testArrayDestructuringAssignRest() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING, Feature.REST_PARAMETERS);
    parse("[first, ...rest] = foo();");
    // nested destructuring in regular parameters and rest parameters
    parse("[first, {a, b}, ...[re, st, ...{length}]] = foo();");
    // arbitrary LHS assignment target is allowed
    parse("[x, ...y[15]] = foo();");
    // arbitrary LHS assignment target not allowed
    parseError(
        "var [x, ...y[15]] = foo();",
        "Only an identifier or destructuring pattern is allowed here.");

    parseError(
        "[first, ...more = 'default'] = foo();", "A default value cannot be specified after '...'");
    parseError("var [first, ...more, last] = foo();", "']' expected");


    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("var [first, ...rest] = foo();",
        getRequiresEs6Message(Feature.DESTRUCTURING));
  }

  public void testArrayDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("function f([x, y]) { use(x); use(y); }");
    parse("function f([x, [y, z]]) {}");
    parse("function f([x, {y, foo: z}]) {}");
    parse("function f([x, y] = [1, 2]) { use(x); use(y); }");
    parse("function f([x, x]) {}");
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

  public void testObjectDestructuringVar() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {x, y} = foo();");
    parse("var {x: x, y: y} = foo();");
    parse("var {x: {y, z}} = foo();");
    parse("var {x: {y: {z}}} = foo();");

    // Useless, but legal.
    parse("var {} = foo();");
    // Arbitrary LHS target not allowed in declaration
    parseError("var {x.a, y} = foo();", "'}' expected");
    parseError(
        "var {a: x.a, y} = foo();", "Only an identifier or destructuring pattern is allowed here.");
  }

  public void testObjectDestructuringVarWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {x = 1} = foo();");
    parse("var {x: {y = 1}} = foo();");
    parse("var {x: y = 1} = foo();");
    parse("var {x: v1 = 5, y: v2 = 'str'} = foo();");
    parse("var {k1: {k2 : x} = bar(), k3: y} = foo();");
  }

  public void testObjectDestructuringAssign() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("({x, y}) = foo();", "invalid assignment target");
    expectFeatures(Feature.DESTRUCTURING);
    parse("({x, y} = foo());");
    parse("({x: x, y: y} = foo());");
    parse("({x: {y, z}} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");

    // Useless, but legal.
    parse("({} = foo());");
  }

  public void testObjectDestructuringAssignWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("({x = 1}) = foo();", "invalid assignment target");
    expectFeatures(Feature.DESTRUCTURING);
    parse("({x = 1} = foo());");
    parse("({x: {y = 1}} = foo());");
    parse("({x: y = 1} = foo());");
    parse("({x: v1 = 5, y: v2 = 'str'} = foo());");
    parse("({k1: {k2 : x} = bar(), k3: y} = foo());");
  }

  public void testObjectDestructuringWithInitializerInvalid() {
    expectFeatures(Feature.DESTRUCTURING);
    parseError("var {{x}} = foo();", "'}' expected");
    expectFeatures();
    parseError("({{x}}) = foo();", "'}' expected");
    parseError("({{a} = {a: 'b'}}) = foo();", "'}' expected");
    parseError("({{a : b} = {a: 'b'}}) = foo();", "'}' expected");
  }

  public void testObjectDestructuringFnDeclaration() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("function f({x, y}) { use(x); use(y); }");
    parse("function f({w, x: {y, z}}) {}");
    parse("function f({x, y} = {x:1, y:2}) {}");
    parse("function f({x, x}) {}");
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

  public void testObjectDestructuringComputedProp() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {[x]: y} = z;");
    parse("var { [foo()] : [x,y,z] = bar() } = baz();");
    parseError("var {[x]} = z;", "':' expected");
  }

  public void testObjectDestructuringStringAndNumberKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {'s': x} = foo();");
    parse("var {3: x} = foo();");

    parseError("var { 'hello world' } = foo();", "':' expected");
    parseError("var { 4 } = foo();", "':' expected");
    parseError("var { 'hello' = 'world' } = foo();", "':' expected");
    parseError("var { 2 = 5 } = foo();", "':' expected");
  }

  /**
   * See https://github.com/google/closure-compiler/issues/1262
   */
  public void testObjectNumberKeysSpecial() {
    Node n = parse("var a = {12345678901234567890: 2}");

    Node objectLit = n.getFirstChild().getFirstChild().getFirstChild();
    assertThat(objectLit.getType()).isEqualTo(Token.OBJECTLIT);

    Node number = objectLit.getFirstChild();
    assertThat(number.getType()).isEqualTo(Token.STRING_KEY);
    assertThat(number.getString()).isEqualTo("12345678901234567000");
  }

  public void testObjectDestructuringKeywordKeys() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {if: x, else: y} = foo();");
    parse("var {while: x=1, for: y} = foo();");
    parse("var {type} = foo();");
    parse("var {declare} = foo();");
    parse("var {module} = foo();");
    parse("var {namespace} = foo();");

    parseError("var {while} = foo();", "cannot use keyword 'while' here.");
    parseError("var {implements} = foo();", "cannot use keyword 'implements' here.");
  }

  public void testObjectDestructuringComplexTarget() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parseError(
        "var {foo: bar.x} = baz();",
        "Only an identifier or destructuring pattern is allowed here.");
    parse("({foo: bar.x} = baz());");
    parse("for ({foo: bar.x} in baz());");

    parseError(
        "var {foo: bar[x]} = baz();",
        "Only an identifier or destructuring pattern is allowed here.");
    parse("({foo: bar[x]} = baz());");
    parse("for ({foo: bar[x]} in baz());");
  }

  public void testObjectDestructuringExtraParens() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("({x: y} = z);");
    parse("({x: (y)} = z);");
    parse("({x: ((y))} = z);");

    parse("([x] = y);");
    parse("[(x), y] = z;");
    parse("[x, (y)] = z;");
    parseError("[x, ([y])] = z;", INVALID_ASSIGNMENT_TARGET);
    parseError("[x, (([y]))] = z;", INVALID_ASSIGNMENT_TARGET);
  }

  public void testObjectLiteralCannotUseDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("var o = {x = 5}", "Default value cannot appear at top level of an object literal.");
  }

  public void testMixedDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {x: [y, z]} = foo();");
    parse("var [x, {y, z}] = foo();");

    parse("({x: [y, z]} = foo());");
    parse("[x, {y, z}] = foo();");

    parse("function f({x: [y, z]}) {}");
    parse("function f([x, {y, z}]) {}");
  }

  public void testMixedDestructuringWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DESTRUCTURING);
    parse("var {x: [y, z] = [1, 2]} = foo();");
    parse("var [x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("({x: [y, z] = [1, 2]} = foo());");
    parse("[x, {y, z} = {y: 3, z: 4}] = foo();");

    parse("function f({x: [y, z] = [1, 2]}) {}");
    parse("function f([x, {y, z} = {y: 3, z: 4}]) {}");
  }

  public void testDestructuringNoRHS() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.DESTRUCTURING);
    parseError("var {x: y};", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("let {x: y};", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("const {x: y};", "const variables must have an initializer");

    expectFeatures(Feature.DESTRUCTURING);
    parseError("var {x};", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("let {x};", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("const {x};", "const variables must have an initializer");

    expectFeatures(Feature.DESTRUCTURING);
    parseError("var [x, y];", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("let [x, y];", "destructuring must have an initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("const [x, y];", "const variables must have an initializer");
  }

  public void testComprehensions() {
    mode = LanguageMode.ECMASCRIPT6;
    String error = "unsupported language feature:"
        + " array/generator comprehensions";

    // array comprehensions
    parseError("[for (x of y) z];", error);
    expectFeatures(Feature.DESTRUCTURING); // Note: the object pattern triggers this
    parseError("[for ({x,y} of z) x+y];", error);
    expectFeatures();
    parseError("[for (x of y) if (x<10) z];", error);
    parseError("[for (a = 5 of v) a];", "'identifier' expected");

    // generator comprehensions
    parseError("(for (x of y) z);", error);
    expectFeatures(Feature.DESTRUCTURING); // Note: the object pattern triggers this
    parseError("(for ({x,y} of z) x+y);", error);
    expectFeatures();
    parseError("(for (x of y) if (x<10) z);", error);
    parseError("(for (a = 5 of v) a);", "'identifier' expected");
  }

  public void testLetForbidden1() {
    expectFeatures(Feature.LET_DECLARATIONS);
    parseWarning("let x = 3;",
        getRequiresEs6Message(Feature.LET_DECLARATIONS));
  }

  public void testLetForbidden2() {
    expectFeatures(Feature.LET_DECLARATIONS);
    parseWarning("function f() { let x = 3; };",
        getRequiresEs6Message(Feature.LET_DECLARATIONS));
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
    expectFeatures(Feature.GENERATORS);
    mode = LanguageMode.ECMASCRIPT6_STRICT;
    parse("var obj = { *f() { yield 3; } };");
    parse("function* f() { yield 3; }");
    parse("function f() { return function* g() {} }");

    mode = LanguageMode.ECMASCRIPT5_STRICT;
    parseWarning("function* f() { yield 3; }",
        getRequiresEs6Message(Feature.GENERATORS));
    parseWarning("var obj = { * f() { yield 3; } };",
        getRequiresEs6Message(Feature.GENERATORS),
        getRequiresEs6Message(Feature.MEMBER_DECLARATIONS));
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
    expectFeatures(Feature.GETTER);
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
    expectFeatures(Feature.GETTER);
    parseError("var x = {a: 2, get foo() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  public void testGettersForbidden6() {
    expectFeatures(Feature.GETTER);
    parseError("var x = {get 'foo'() { return 3; }};",
        IRFactory.GETTER_ERROR_MESSAGE);
  }

  public void testSettersForbidden() {
    expectFeatures(Feature.SETTER);
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
    assertNode(n.getFirstChild()).hasType(Token.FUNCTION);
    assertThat(n.getJSDocInfo()).isNotNull();
    assertThat(n.getFirstChild().getJSDocInfo()).isNull();
    assertThat(n.getJSDocInfo().getFileOverview()).isEqualTo("Hi mom!");
  }

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

    Node objectLit = n.getFirstFirstChild().getFirstChild();
    assertNode(objectLit).hasType(Token.OBJECTLIT);

    Node number = objectLit.getFirstChild();
    assertNode(number).hasType(Token.STRING_KEY);
    assertThat(number.getJSDocInfo()).isNotNull();
  }

  public void testDuplicatedParam() {
    parseWarning("function foo(x, x) {}", "Duplicate parameter name \"x\"");
  }

  public void testLetAsIdentifier() {
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

  public void testLet() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.LET_DECLARATIONS);

    parse("let x;");
    parse("let x = 1;");
    parse("let x, y = 2;");
    parse("let x = 1, y = 2;");
  }

  public void testConst() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.CONST_DECLARATIONS);

    parseError("const x;", "const variables must have an initializer");
    parse("const x = 1;");
    parseError("const x, y = 2;", "const variables must have an initializer");
    parse("const x = 1, y = 2;");
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
  }

  public void testYield3() {
    mode = LanguageMode.ECMASCRIPT6_STRICT;
    expectFeatures(Feature.GENERATORS);
    // TODO(johnlenz): validate "yield" parsing. Firefox rejects this
    // use of "yield".
    parseError("function * f() { yield , yield; }");
  }

  public void testStringLineContinuation() {
    expectFeatures(Feature.STRING_CONTINUATION);
    mode = LanguageMode.ECMASCRIPT3;
    Node n = parseError("'one\\\ntwo';",
        "String continuations are not supported in this language mode.");
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("'one\\\ntwo';", "String continuations are not recommended. See"
        + " https://google.github.io/styleguide/javascriptguide.xml?showone=Multiline_string_literals#Multiline_string_literals");
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");

    mode = LanguageMode.ECMASCRIPT6;
    parseWarning("'one\\\ntwo';", "String continuations are not recommended. See"
        + " https://google.github.io/styleguide/javascriptguide.xml?showone=Multiline_string_literals#Multiline_string_literals");
    assertThat(n.getFirstFirstChild().getString()).isEqualTo("onetwo");
  }

  public void testStringLiteral() {
    Node n = parse("'foo'");
    Node stringNode = n.getFirstFirstChild();
    assertNode(stringNode).hasType(Token.STRING);
    assertThat(stringNode.getString()).isEqualTo("foo");
  }

  private Node testTemplateLiteral(String s) {
    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(s,
        getRequiresEs6Message(Feature.TEMPLATE_LITERALS));

    mode = LanguageMode.ECMASCRIPT6;
    return parse(s);
  }

  private void assertSimpleTemplateLiteral(String expectedContents, String literal) {
    Node node = testTemplateLiteral(literal).getFirstFirstChild();
    assertNode(node).hasType(Token.TEMPLATELIT);
    assertThat(node.getChildCount()).isEqualTo(1);
    assertNode(node.getFirstChild()).hasType(Token.STRING);
    assertThat(node.getFirstChild().getString()).isEqualTo(expectedContents);
  }

  public void testUseTemplateLiteral() {
    expectFeatures(Feature.TEMPLATE_LITERALS);
    testTemplateLiteral("f`hello world`;");
    testTemplateLiteral("`hello ${name} ${world}`.length;");
  }

  public void testTemplateLiteral() {
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

  public void testTemplateLiteralWithLineContinuation() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.TEMPLATE_LITERALS);
    Node n = parseWarning("`string \\\ncontinuation`",
        "String continuations are not recommended. See"
        + " https://google.github.io/styleguide/javascriptguide.xml?showone=Multiline_string_literals#Multiline_string_literals");
    Node templateLiteral = n.getFirstFirstChild();
    Node stringNode = templateLiteral.getFirstChild();
    assertNode(stringNode).hasType(Token.STRING);
    assertThat(stringNode.getString()).isEqualTo("string continuation");
  }

  public void testTemplateLiteralSubstitution() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.TEMPLATE_LITERALS);
    parse("`hello ${name}`;");
    parse("`hello ${name} ${world}`;");
    parse("`hello ${name }`");

    expectFeatures();
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
    expectFeatures(Feature.BINARY_LITERALS);
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
    expectFeatures(Feature.OCTAL_LITERALS);
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

  public void testInvalidOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT5;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");

    mode = LanguageMode.ECMASCRIPT6;
    parseError("0o08;",
        "Invalid octal digit in octal literal.");
  }

  public void testInvalidOldStyleOctalLiterals() {
    mode = LanguageMode.ECMASCRIPT3;
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

  public void testGetter() {
    expectFeatures(Feature.GETTER);
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

    expectFeatures();
    parseError("var x = {get a(b){}};", "')' expected");
  }

  public void testSetter() {
    expectFeatures(Feature.SETTER);
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
    expectFeatures();
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
    expectFeatures(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
    mode = LanguageMode.ECMASCRIPT3;

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
    // TODO(johnlenz): reenable
    //parse("public = 2;");

    mode = LanguageMode.ECMASCRIPT5_STRICT;

    expectFeatures(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
    parse("var boolean;");
    parse("function boolean() {};");
    parse("boolean = 1;");

    expectFeatures();
    parseError("public = 2;", "primary expression expected");
    parseError("class = 1;", "'identifier' expected");
  }

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

  public void testKeywordsAsProperties() {
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
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
    parse("var x = {get 'function'(){} };");
    parse("var x = {get 1(){} };");
    parse("var x = {set function(a){} };");
    parse("var x = {set 'function'(a){} };");
    parse("var x = {set 1(a){} };");
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
    expectFeatures();
    parse("var x = {let: 1};");
    parse("x.let;");
    parse("var x = {yield: 1};");
    parse("x.yield;");
    expectFeatures(Feature.KEYWORDS_AS_PROPERTIES);
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
    parse("var \\u00fbtest\\u00fb");
    parse("Js\\u00C7ompiler");
    parse("Js\\u0043ompiler");
    parse("if(true){foo=\\u03b5}");
    parse("if(true){foo=\\u03b5}else bar()");
  }

  public void testUnicodePointEscapeInIdentifiers() {
    parse("var \\u{0043}");
    parse("var \\u{0043}test\\u{0043}");
    parse("var \\u0043test\\u{0043}");
    parse("var \\u{0043}test\\u0043");
    parse("Js\\u{0043}ompiler");
    parse("Js\\u{765}ompiler");
    parse("var \\u0043;{43}");
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
    parseError("var \\u{03b5", "Invalid escape sequence");
    parseError("var \\u43{43}", "Invalid escape sequence");
    parseError("var \\u{defgRestOfIdentifier", "Invalid escape sequence");
    parseError("var \\u03b5}", "primary expression expected");
    parseError("var \\u{03b5}}}", "primary expression expected");
    parseError("var \\u{03b5}{}", "Semi-colon expected");
    parseError("var \\u0043{43}", "Semi-colon expected");
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
    expectFeatures(Feature.GETTER);
    parse("var o = { /** @type {string} */ get prop() { return 'str' }};");
    expectFeatures(Feature.SETTER);
    parse("var o = { /** @type {string} */ set prop(s) {}};");
  }

  public void testValidTypeAnnotation3() {
    // This one we don't currently support in the type checker but
    // we would like to.
    parse("try {} catch (/** @type {Error} */ e) {}");
  }

  public void testValidTypeAnnotation4() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.MODULES);
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
    expectFeatures(Feature.REGEXP_FLAG_Y);
    mode = LanguageMode.ECMASCRIPT6;
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

  public void testDefaultParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DEFAULT_PARAMETERS);
    parse("function f(a, b=0) {}");
    parse("function f(a, b=0, c) {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("function f(a, b=0) {}",
        getRequiresEs6Message(Feature.DEFAULT_PARAMETERS));
  }

  public void testRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.REST_PARAMETERS);
    parse("function f(...b) {}");
    parse("(...xs) => xs");
    parse("(x, ...xs) => xs");
    parse("(x, y, ...xs) => xs");
    parseError("(...xs, x) => xs", "')' expected");
    parseError(
        "function f(...a[0]) {}", "Only an identifier or destructuring pattern is allowed here.");
  }

  public void testDestructuredRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.REST_PARAMETERS, Feature.DESTRUCTURING);
    parse("(...[x]) => xs");
    parse("(...[x, y]) => xs");
    parse("(a, b, c, ...[x, y, z]) => x");
    parseError(
        "function f(...[a[0]]) {}", "Only an identifier or destructuring pattern is allowed here.");
  }

  public void testRestParameters_ES5() {
    mode = LanguageMode.ECMASCRIPT5;
    expectFeatures(Feature.REST_PARAMETERS);
    parseWarning("function f(...b) {}",
        getRequiresEs6Message(Feature.REST_PARAMETERS));
  }

  public void testExpressionsThatLookLikeParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("();", "invalid paren expression");
    expectFeatures(Feature.REST_PARAMETERS);
    parseError("(...xs);", "invalid paren expression");
    parseError("(x, ...xs);", "A rest parameter must be in a parameter list.");
    parseError("(a, b, c, ...xs);", "A rest parameter must be in a parameter list.");
  }

  public void testDefaultParametersWithRestParameters() {
    mode = LanguageMode.ECMASCRIPT6;
    expectFeatures(Feature.DEFAULT_PARAMETERS, Feature.REST_PARAMETERS);
    parse("function f(a=0, ...b) {}");
    parse("function f(a, b=0, ...c) {}");
    parse("function f(a, b=0, c=1, ...d) {}");
    parseError("function f(a=1, ...b=3) {}", "A default value cannot be specified after '...'");
  }

  public void testClass1() {
    expectFeatures(Feature.CLASSES);
    mode = LanguageMode.ECMASCRIPT6;
    parse("class C {}");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning("class C {}",
        getRequiresEs6Message(Feature.CLASSES));

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning("class C {}",
        getRequiresEs6Message(Feature.CLASSES));

  }

  public void testClass2() {
    expectFeatures(Feature.CLASSES);
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
    expectFeatures(Feature.CLASSES);
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
    expectFeatures(Feature.CLASSES, Feature.KEYWORDS_AS_PROPERTIES);
    mode = LanguageMode.ECMASCRIPT6;
    parse(Joiner.on('\n').join(
        "class KeywordMethods {",
        "  continue() {}",
        "  throw() {}",
        "  else() {}",
        "}"));
  }

  public void testSuper1() {
    expectFeatures(Feature.SUPER);
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testNewTarget() {
    expectFeatures(Feature.NEW_TARGET);
    mode = LanguageMode.ECMASCRIPT6;

    // TODO(bradfordcsmith): new.target in global scope should be a syntax error
    parse("new.target;");

    parse("function f() { new.target; };");

    mode = LanguageMode.ECMASCRIPT5;
    parseWarning(
        "function f() { new.target; }",
        getRequiresEs6Message(Feature.NEW_TARGET));

    mode = LanguageMode.ECMASCRIPT3;
    parseWarning(
        "function f() { new.target; }",
        getRequiresEs6Message(Feature.NEW_TARGET));
  }

  public void testNewDotSomethingInvalid() {
    expectFeatures(Feature.NEW_TARGET);
    mode = LanguageMode.ECMASCRIPT6;

    parseError("function f(){new.something}", "'target' expected");
  }

  public void testArrow1() {
    expectFeatures(Feature.ARROW_FUNCTIONS);
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testArrowInvalid() {
    mode = LanguageMode.ECMASCRIPT6;
    parseError("*()=>1;", "primary expression expected");
    expectFeatures(Feature.ARROW_FUNCTIONS);
    parseError("var f = x\n=>2", "No newline allowed before '=>'");
    parseError("f = (x,y)\n=>2;", "No newline allowed before '=>'");
    parseError("f( (x,y)\n=>2)", "No newline allowed before '=>'");
  }

  public void testAsyncFunction() {
    String asyncFunctionExpressionSource = "f = async function() {};";
    String asyncFunctionDeclarationSource = "async function f() {}";
    expectFeatures(Feature.ASYNC_FUNCTIONS);

    for (LanguageMode m : LanguageMode.values()) {
      mode = m;
      if (m.featureSet.contains(Feature.ASYNC_FUNCTIONS)) {
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

  public void testInvalidAsyncFunction() {
    mode = LanguageMode.ECMASCRIPT8;
    parseError("async function *f(){}", "async functions cannot be generators");
    parseError("f = async function *(){}", "async functions cannot be generators");
  }

  public void testAsyncArrowFunction() {
    String arrowFunctionSource = "f = async (x) => x + 1";
    expectFeatures(Feature.ASYNC_FUNCTIONS, Feature.ARROW_FUNCTIONS);

    for (LanguageMode m : LanguageMode.values()) {
      mode = m;
      if (m.featureSet.contains(Feature.ASYNC_FUNCTIONS)) {
        parse(arrowFunctionSource);
      } else if (m.featureSet.contains(Feature.ARROW_FUNCTIONS)) {
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

  public void testAsyncArrowInvalid() {
    mode = LanguageMode.ECMASCRIPT8;
    parseError("f = not_async (x) => x + 1;", "'=>' unexpected");

    expectFeatures(Feature.ARROW_FUNCTIONS);
    // async requires parens
    parseError("f = async x => x + 1;", "Semi-colon expected");
  }

  public void testAsyncMethod() {
    mode = LanguageMode.ECMASCRIPT8;
    expectFeatures(Feature.ASYNC_FUNCTIONS);
    parse("o={async m(){}}");
    parse("o={async [a+b](){}}");
    parse("class C{async m(){}}");
    parse("class C{static async m(){}}");
    parse("class C{async [a+b](){}}");
    parse("class C{static async [a+b](){}}");
  }

  public void testInvalidAsyncMethod() {
    mode = LanguageMode.ECMASCRIPT6;
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
    parseError("o={async\nm(){}}", "'}' expected");
    parseError("o={static async\nm(){}}", "Cannot use keyword in short object literal");
    parseError("class C{async\nm(){}}", "'(' expected");
    parseError("class C{static async\nm(){}}", "'(' expected");
  }

  public void testAwaitExpression() {
    mode = LanguageMode.ECMASCRIPT8;
    expectFeatures(Feature.ASYNC_FUNCTIONS);
    parse("async function f(p){await p}");
    parse("f = async function(p){await p}");
    parse("f = async(p)=>await p");
    parse("class C{async m(p){await p}}");
    parse("class C{static async m(p){await p}}");
  }

  public void testFor_ES5() {
    parse("for (var x; x != 10; x = next()) {}");
    parse("for (var x; x != 10; x = next());");
    parse("for (var x = 0; x != 10; x++) {}");
    parse("for (var x = 0; x != 10; x++);");

    parse("var x; for (x; x != 10; x = next()) {}");
    parse("var x; for (x; x != 10; x = next());");

    parseError("for (x in {};;) {}", "')' expected");
  }

  public void testFor_ES6() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.LET_DECLARATIONS);
    parse("for (let x; x != 10; x = next()) {}");
    parse("for (let x; x != 10; x = next());");
    parse("for (let x = 0; x != 10; x++) {}");
    parse("for (let x = 0; x != 10; x++);");

    expectFeatures(Feature.CONST_DECLARATIONS);
    parseError("for (const x; x != 10; x = next()) {}", "const variables must have an initializer");
    parseError("for (const x; x != 10; x = next());", "const variables must have an initializer");
    parse("for (const x = 0; x != 10; x++) {}");
    parse("for (const x = 0; x != 10; x++);");
  }

  public void testForIn_ES6() {
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testForIn_ES5() {
    mode = LanguageMode.ECMASCRIPT5;

    parse("for (a in b) c;");
    parse("for (var a in b) c;");

    parseError("for (a=1 in b) c;", INVALID_ASSIGNMENT_TARGET);
    parseWarning("for (var a=1 in b) c;", "for-in statement should not have initializer");
  }

  public void testForInDestructuring() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.DESTRUCTURING);
    parse("for ({a} in b) c;");
    parse("for (var {a} in b) c;");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let {a} in b) c;");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const {a} in b) c;");

    expectFeatures(Feature.DESTRUCTURING);
    parse("for ({a: b} in c) d;");
    parse("for (var {a: b} in c) d;");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let {a: b} in c) d;");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const {a: b} in c) d;");

    expectFeatures(Feature.DESTRUCTURING);
    parse("for ([a] in b) c;");
    parse("for (var [a] in b) c;");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for (let [a] in b) c;");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for (const [a] in b) c;");

    expectFeatures(Feature.DESTRUCTURING);
    parseError("for ({a: b} = foo() in c) d;", INVALID_ASSIGNMENT_TARGET);
    parseError("for (var {a: b} = foo() in c) d;", "for-in statement may not have initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("for (let {a: b} = foo() in c) d;", "for-in statement may not have initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("for (const {a: b} = foo() in c) d;", "for-in statement may not have initializer");

    expectFeatures(Feature.DESTRUCTURING);
    parseError("for ([a] = foo() in b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for (var [a] = foo() in b) c;", "for-in statement may not have initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("for (let [a] = foo() in b) c;", "for-in statement may not have initializer");
    expectFeatures(Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("for (const [a] = foo() in b) c;", "for-in statement may not have initializer");
  }

  public void testForOf1() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.FOR_OF);
    parse("for(a of b) c;");
    parse("for(var a of b) c;");
    expectFeatures(Feature.FOR_OF, Feature.LET_DECLARATIONS);
    parse("for(let a of b) c;");
    expectFeatures(Feature.FOR_OF, Feature.CONST_DECLARATIONS);
    parse("for(const a of b) c;");
  }

  public void testForOf2() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("for(a=1 of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for(var a=1 of b) c;", "for-of statement may not have initializer");
    parseError("for(let a=1 of b) c;", "for-of statement may not have initializer");
    parseError("for(const a=1 of b) c;", "for-of statement may not have initializer");
  }

  public void testForOf3() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("for(var a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
    parseError("for(let a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
    parseError("for(const a, b of c) d;",
        "for-of statement may not have more than one variable declaration");
  }

  public void testForOf4() {
    mode = LanguageMode.ECMASCRIPT6;

    parseError("for(a, b of c) d;", INVALID_ASSIGNMENT_TARGET);
  }

  public void testDestructuringInForLoops() {
    expectFeatures(Feature.DESTRUCTURING);
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testInvalidDestructuring() {
    expectFeatures(Feature.DESTRUCTURING);
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testForOfPatterns() {
    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING);
    mode = LanguageMode.ECMASCRIPT6;

    parse("for({x} of b) c;");
    parse("for({x: y} of b) c;");
    parse("for([x, y] of b) c;");
    parse("for([x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parse("for(let {x} of b) c;");
    parse("for(let {x: y} of b) c;");
    parse("for(let [x, y] of b) c;");
    parse("for(let [x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parse("for(const {x} of b) c;");
    parse("for(const {x: y} of b) c;");
    parse("for(const [x, y] of b) c;");
    parse("for(const [x, ...y] of b) c;");

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING);
    parse("for(var {x} of b) c;");
    parse("for(var {x: y} of b) c;");
    parse("for(var [x, y] of b) c;");
    parse("for(var [x, ...y] of b) c;");
  }

  public void testForOfPatternsWithInitializer() {
    mode = LanguageMode.ECMASCRIPT6;

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING);
    parseError("for({x}=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for({x: y}=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for([x, y]=a of b) c;", INVALID_ASSIGNMENT_TARGET);
    parseError("for([x, ...y]=a of b) c;", INVALID_ASSIGNMENT_TARGET);

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING, Feature.LET_DECLARATIONS);
    parseError("for(let {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(let [x, ...y]=a of b) c;", "for-of statement may not have initializer");

    expectFeatures(Feature.FOR_OF, Feature.DESTRUCTURING, Feature.CONST_DECLARATIONS);
    parseError("for(const {x}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const {x: y}=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, y]=a of b) c;", "for-of statement may not have initializer");
    parseError("for(const [x, ...y]=a of b) c;", "for-of statement may not have initializer");
  }

  public void testImport() {
    expectFeatures(Feature.MODULES);
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testExport() {
    mode = LanguageMode.ECMASCRIPT6;

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

  public void testUtf8() {
    mode = LanguageMode.ECMASCRIPT5;
    Node n = parse("\uFEFFfunction f() {}\n");
    Node fn = n.getFirstChild();
    assertNode(fn).hasType(Token.FUNCTION);
  }

  public void testParseDeep1() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "\'end\';n";
    parse(code);
  }

  public void testParseDeep2() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "\'end\'; /** a comment */\n";
    parse(code);
  }

  public void testParseDeep3() {
    String code = "var x; x = \n";
    for (int i = 1; i < 15000; i++) {
      code += "  \'" + i + "\' +\n";
    }
    code += "  /** @type {string} */ (x);\n";
    parse(code);
  }

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
      fail();
    } catch (java.lang.StackOverflowError e) {
      // expected exception
    }
  }

  private String getRequiresEs6Message(Feature feature) {
    return requiresLanguageModeMessage(LanguageMode.ECMASCRIPT6, feature);
  }

  private String requiresLanguageModeMessage(LanguageMode languageMode, Feature feature) {
    return String.format(
        "this language feature is only supported for %s mode or better: %s",
        languageMode,
        feature);
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
        createConfig(),
        testErrorReporter);
    Node script = result.ast;

    // check expected features if specified
    if (expectedFeatures != null) {
      assertThat(result.features).isEqualTo(expectedFeatures);
    }

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
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    StaticSourceFile file = new SimpleSourceFile("input", false);
    ParserRunner.ParseResult result = ParserRunner.parse(
        file,
        string,
        createConfig(),
        testErrorReporter);
    Node script = result.ast;

    // check expected features if specified
    if (expectedFeatures != null) {
      assertThat(result.features).isEqualTo(expectedFeatures);
    }

    // verifying that all warnings were seen
    testErrorReporter.assertHasEncounteredAllErrors();
    testErrorReporter.assertHasEncounteredAllWarnings();

    return script;
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
          Config.SourceLocationInformation.PRESERVE,
          Config.RunMode.KEEP_GOING,
          null);
    } else {
      return ParserRunner.createConfig(mode, null);
    }
  }

  /** Sets expectedFeatures based on the list of features. */
  private void expectFeatures(Feature... features) {
    expectedFeatures = FeatureSet.ES3;
    for (Feature feature : features) {
      expectedFeatures = expectedFeatures.require(feature);
    }
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
