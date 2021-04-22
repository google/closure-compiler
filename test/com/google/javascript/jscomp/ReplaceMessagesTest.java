/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.JsMessage.Style.RELAX;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;

import com.google.javascript.jscomp.JsMessage.Style;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@RunWith(JUnit4.class)
public final class ReplaceMessagesTest extends CompilerTestCase {

  // Messages returned from fake bundle, keyed by `JsMessage.id`.
  private Map<String, JsMessage> messages;
  private Style style;
  private boolean strictReplacement;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ReplaceMessages(compiler, new SimpleMessageBundle(), style, strictReplacement);
  }

  @Override
  protected int getNumRepetitions() {
    // No longer valid on the second run.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    messages = new HashMap<>();
    strictReplacement = false;
    style = RELAX;
  }

  @Test
  public void testReplaceSimpleMessage() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("Hi\nthere").build());

    test(
        "/** @desc d */\n var MSG_A = goog.getMsg('asdf');",
        "/** @desc d */\n var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testReplaceSimpleMessageDefinedWithAdd() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("Hi\nthere").build());

    test(
        "/** @desc d */\n var MSG_A = goog.getMsg('abcd' + 'efgh');",
        "/** @desc d */\n var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testMissingAlternateMessage() {
    test(
        "/**\n    @desc d\n    @alternateMessageId 1984\n*/\n var MSG_A = goog.getMsg('asdf');",
        "/**\n    @desc d\n    @alternateMessageId 1984\n*/\n var MSG_A='asdf'");
  }

  @Test
  public void testAlternateMessageWithMismatchedParts() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello!")
            .appendStringPart(" Welcome!")
            .build((meaning, messageParts) -> "1984"));

    test(
        "/**\n    @desc d\n    @alternateMessageId 1984\n*/\n var MSG_A = goog.getMsg('asdf');",
        "/**\n    @desc d\n    @alternateMessageId 1984\n*/\n var MSG_A = 'Hello! Welcome!';");
  }

  @Test
  public void testAlternateMessageWithMismatchedPlaceholders() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .setDesc("B desc")
            .setMeaning("B meaning")
            .appendStringPart("Hello, ")
            .appendPlaceholderReference("first_name")
            .appendStringPart("!")
            .build((meaning, messageParts) -> "1984"));

    testError(
        "/**\n    @desc B desc"
            + "\n    @meaning B meaning"
            + "\n    @alternateMessageId 1984"
            + "\n*/"
            + "\n var MSG_A = goog.getMsg('Hello, {$name}!', {name: name});",
        ReplaceMessages.INVALID_ALTERNATE_MESSAGE_PLACEHOLDERS);
  }

  @Test
  public void testReplaceValidAlternateMessage() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("Howdy\npardner")
            .build((meaning, messageParts) -> "1984"));

    test(
        "/**\n    @desc B desc"
            + "\n    @alternateMessageId 1984"
            + "\n*/"
            + "\n var MSG_A = goog.getMsg('asdf');",
        "/**\n    @desc B desc"
            + "\n    @alternateMessageId 1984"
            + "\n*/"
            + "\n var MSG_A='Howdy\\npardner'");
  }

  @Test
  public void testIgnoreUnnecessaryAlternateMessage() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("Howdy\npardner")
            .build((meaning, messageParts) -> "1984"));
    registerMessage(
        new JsMessage.Builder("MSG_A")
            .setDesc("Greeting.")
            .setAlternateId("1984")
            .appendStringPart("Hi\nthere")
            .build());

    test(
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = goog.getMsg('asdf');"),
        lines(
            "/**", //
            " * @desc d",
            " * @alternateMessageId 1984",
            " */",
            "var MSG_A = 'Hi\\nthere';"));
  }

  @Test
  public void testAlternateTrumpsFallback() {
    registerMessage(
        new JsMessage.Builder("MSG_C")
            .appendStringPart("Howdy\npardner")
            .build((meaning, messageParts) -> "1984"));

    registerMessage(new JsMessage.Builder("MSG_B").appendStringPart("Good\nmorrow, sir").build());

    test(
        lines(
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_A = goog.getMsg('asdf');",
            "/**",
            "    @desc d",
            "*/",
            "var MSG_B = goog.getMsg('ghjk');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_A = 'Howdy\\npardner';",
            "/**",
            "    @desc d",
            "*/",
            "var MSG_B = 'Good\\nmorrow, sir';",
            "var x = MSG_A;"));
  }

  @Test
  public void testFallbackWithAlternate() {
    registerMessage(
        new JsMessage.Builder("MSG_C")
            .appendStringPart("Howdy\npardner")
            .build((meaning, messageParts) -> "1984"));

    test(
        lines(
            "/**",
            "    @desc d",
            "*/",
            "var MSG_A = goog.getMsg('asdf');",
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_B = goog.getMsg('ghjk');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/**",
            "    @desc d",
            "*/",
            "var MSG_A = 'asdf';",
            "/**",
            "    @desc d",
            "    @alternateMessageId 1984",
            "*/",
            "var MSG_B = 'Howdy\\npardner';",
            "var x = MSG_B;"));
  }

  @Test
  public void testNameReplacement() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    test(
        "/** @desc d */\n var MSG_B=goog.getMsg('asdf {$measly}', {measly: x});",
        "/** @desc d */\n var MSG_B='One '+ (x +' ph' )");
  }

  @Test
  public void testGetPropReplacement() {
    registerMessage(new JsMessage.Builder("MSG_C").appendPlaceholderReference("amount").build());

    test(
        "/** @desc d */\n var MSG_C = goog.getMsg('${$amount}', {amount: a.b.amount});",
        "/** @desc d */\n var MSG_C=a.b.amount");
  }

  @Test
  public void testFunctionCallReplacement() {
    registerMessage(new JsMessage.Builder("MSG_D").appendPlaceholderReference("amount").build());

    test(
        "/** @desc d */\n var MSG_D = goog.getMsg('${$amount}', {amount: getAmt()});",
        "/** @desc d */\n var MSG_D=getAmt()");
  }

  @Test
  public void testMethodCallReplacement() {
    registerMessage(new JsMessage.Builder("MSG_E").appendPlaceholderReference("amount").build());

    test(
        "/** @desc d */\n var MSG_E = goog.getMsg('${$amount}', {amount: obj.getAmt()});",
        "/** @desc d */\n var MSG_E=obj.getAmt()");
  }

  @Test
  public void testMethodCallReplacementEmptyMessage() {
    registerMessage(new JsMessage.Builder("MSG_M").build());

    test(
        "/** @desc d */\n var MSG_M = goog.getMsg('${$amount}', {amount: obj.getAmt()});",
        "/** @desc d */\n var MSG_M=\"\"");
  }

  @Test
  public void testHookReplacement() {
    registerMessage(
        new JsMessage.Builder("MSG_F")
            .appendStringPart("#")
            .appendPlaceholderReference("amount")
            .appendStringPart(".")
            .build());

    test(
        "/** @desc d */\n var MSG_F = goog.getMsg('${$amount}', {amount: (a ? b : c)});",
        "/** @desc d */\n var MSG_F='#'+((a?b:c)+'.')");
  }

  @Test
  public void testAddReplacement() {
    registerMessage(new JsMessage.Builder("MSG_G").appendPlaceholderReference("amount").build());

    test(
        "/** @desc d */\n var MSG_G = goog.getMsg('${$amount}', {amount: x + ''});",
        "/** @desc d */\n var MSG_G=x+''");
  }

  @Test
  public void testPlaceholderValueReferencedTwice() {
    registerMessage(
        new JsMessage.Builder("MSG_H")
            .appendPlaceholderReference("dick")
            .appendStringPart(", ")
            .appendPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendPlaceholderReference("jane")
            .build());

    test(
        "/** @desc d */\n var MSG_H = goog.getMsg('{$dick}{$jane}', {jane: x, dick: y});",
        "/** @desc d */\n var MSG_H = y + (', ' + (y + (' and ' + x)))");
  }

  @Test
  public void testPlaceholderNameInLowerCamelCase() {
    registerMessage(
        new JsMessage.Builder("MSG_I")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amtEarned")
            .build());

    test(
        "/** @desc d */\n var MSG_I = goog.getMsg('${$amtEarned}', {amtEarned: x});",
        "/** @desc d */\n var MSG_I='Sum: $'+x");
  }

  @Test
  public void testQualifiedMessageName() {
    registerMessage(
        new JsMessage.Builder("MSG_J")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    test(
        "/** @desc d */\n a.b.c.MSG_J = goog.getMsg('asdf {$measly}', {measly: x});",
        "/** @desc d */\n a.b.c.MSG_J='One '+(x+' ph')");
  }

  @Test
  public void testPlaceholderInPlaceholderValue() {
    registerMessage(
        new JsMessage.Builder("MSG_L")
            .appendPlaceholderReference("a")
            .appendStringPart(" has ")
            .appendPlaceholderReference("b")
            .build());

    test(
        "/** @desc d */\n var MSG_L = goog.getMsg('{$a} has {$b}', {a: '{$b}', b: 1});",
        "/** @desc d */\n var MSG_L='{$b}'+(' has '+1);");
  }

  @Test
  public void testSimpleMessageReplacementMissing() {
    style = Style.LEGACY;
    test("/** @desc d */\n var MSG_E = 'd*6a0@z>t';", "/** @desc d */\n var MSG_E = 'd*6a0@z>t'");
  }

  @Test
  public void testSimpleMessageReplacementMissingWithNewStyle() {
    test(
        "/** @desc d */\n var MSG_E = goog.getMsg('missing');",
        "/** @desc d */\n var MSG_E = 'missing'");
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInBundle() {
    style = Style.LEGACY;

    strictReplacement = true;
    testError("var MSG_E = 'Hello';", ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testStrictModeAndMessageReplacementAbsentInNonEmptyBundle() {
    style = Style.LEGACY;

    registerMessage(
        new JsMessage.Builder("MSG_J")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    strictReplacement = true;
    testError("var MSG_E = 'Hello';", ReplaceMessages.BUNDLE_DOES_NOT_HAVE_THE_MESSAGE);
  }

  @Test
  public void testFunctionReplacementMissing() {
    style = Style.LEGACY;
    test("var MSG_F = function() {return 'asdf'};", "var MSG_F = function() {return'asdf'}");
  }

  @Test
  public void testFunctionWithParamReplacementMissing() {
    style = Style.LEGACY;
    test(
        "var MSG_G = function(measly) {return 'asdf' + measly};",
        "var MSG_G=function(measly){return'asdf'+measly}");
  }

  @Test
  public void testPlaceholderNameInLowerUnderscoreCase() {
    testError(
        "var MSG_J = goog.getMsg('${$amt_earned}', {amt_earned: x});", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadPlaceholderReferenceInReplacement() {
    registerMessage(new JsMessage.Builder("MSG_K").appendPlaceholderReference("amount").build());

    testError("var MSG_K = goog.getMsg('Hi {$jane}', {jane: x});", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testEmptyObjLit() {
    registerMessage(new JsMessage.Builder("MSG_E").appendPlaceholderReference("amount").build());

    testError(
        "/** @desc d */\nvar MSG_E = goog.getMsg('');",
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. "
            + "Empty placeholder value map for a translated message "
            + "with placeholders.");
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntaxConcat() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("Hi\nthere").build());
    style = Style.LEGACY;
    test("var MSG_A = 'abc' + 'def';", "var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testLegacyStyleNoPlaceholdersVarSyntax() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("Hi\nthere").build());
    style = Style.LEGACY;
    test("var MSG_A = 'd*6a0@z>t';", "var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testLegacyStyleNoPlaceholdersFunctionSyntax() {
    registerMessage(new JsMessage.Builder("MSG_B").appendStringPart("Hi\nthere").build());
    style = Style.LEGACY;
    test("var MSG_B = function() {return 'asdf'};", "var MSG_B=function(){return'Hi\\nthere'}");
  }

  @Test
  public void testLegacyStyleOnePlaceholder() {
    registerMessage(
        new JsMessage.Builder("MSG_C")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());
    style = Style.LEGACY;
    test(
        "var MSG_C = function(measly) {return 'asdf' + measly};",
        "var MSG_C=function(measly){return'One '+(measly+' ph')}");
  }

  @Test
  public void testLegacyStyleTwoPlaceholders() {
    registerMessage(
        new JsMessage.Builder("MSG_D")
            .appendPlaceholderReference("dick")
            .appendStringPart(" and ")
            .appendPlaceholderReference("jane")
            .build());
    style = Style.LEGACY;
    test(
        "var MSG_D = function(jane, dick) {return jane + dick};",
        "var MSG_D=function(jane,dick){return dick+(' and '+jane)}");
  }

  @Test
  public void testLegacyStylePlaceholderNameInLowerCamelCase() {
    registerMessage(
        new JsMessage.Builder("MSG_E")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amtEarned")
            .build());
    style = Style.LEGACY;
    test(
        "var MSG_E = function(amtEarned) {return amtEarned + 'x'};",
        "var MSG_E=function(amtEarned){return'Sum: $'+amtEarned}");
  }

  @Test
  public void testLegacyStylePlaceholderNameInLowerUnderscoreCase() {
    registerMessage(
        new JsMessage.Builder("MSG_F")
            .appendStringPart("Sum: $")
            .appendPlaceholderReference("amt_earned")
            .build());

    // Placeholder named in lower-underscore case (discouraged nowadays)
    style = Style.LEGACY;
    test(
        "var MSG_F = function(amt_earned) {return amt_earned + 'x'};",
        "var MSG_F=function(amt_earned){return'Sum: $'+amt_earned}");
  }

  @Test
  public void testLegacyStyleBadPlaceholderReferenceInReplacement() {
    style = Style.LEGACY;

    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("Ola, ")
            .appendPlaceholderReference("chimp")
            .build());

    testError(
        "var MSG_B = function(chump) {return chump + 'x'};",
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testTranslatedPlaceHolderMissMatch() {
    registerMessage(
        new JsMessage.Builder("MSG_A")
            .appendPlaceholderReference("a")
            .appendStringPart("!")
            .build());

    testError("var MSG_A = goog.getMsg('{$a}');", MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testBadFallbackSyntax1() {
    testError(
        "/** @desc d */\n"
            + "var MSG_A = goog.getMsg('asdf');"
            + "var x = goog.getMsgWithFallback(MSG_A);",
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax2() {
    testError(
        "var x = goog.getMsgWithFallback('abc', 'bcd');", JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax3() {
    testError(
        "/** @desc d */\n"
            + "var MSG_A = goog.getMsg('asdf');"
            + "var x = goog.getMsgWithFallback(MSG_A, NOT_A_MESSAGE);",
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax4() {
    testError(
        "/** @desc d */\n"
            + "var MSG_A = goog.getMsg('asdf');"
            + "var x = goog.getMsgWithFallback(NOT_A_MESSAGE, MSG_A);",
        JsMessageVisitor.BAD_FALLBACK_SYNTAX);
  }

  @Test
  public void testBadFallbackSyntax5() {
    testError(
        "/** @desc d */\n"
            + "var MSG_A = goog.getMsg('asdf');"
            + "var x = goog.getMsgWithFallback(MSG_A, MSG_DOES_NOT_EXIST);",
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testBadFallbackSyntax6() {
    testError(
        "/** @desc d */\n"
            + "var MSG_A = goog.getMsg('asdf');"
            + "var x = goog.getMsgWithFallback(MSG_DOES_NOT_EXIST, MSG_A);",
        JsMessageVisitor.FALLBACK_ARG_ERROR);
  }

  @Test
  public void testUseFallback() {
    registerMessage(new JsMessage.Builder("MSG_B").appendStringPart("translated").build());
    test(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'translated';",
            "var x = MSG_B;"));
  }

  @Test
  public void testFallbackEmptyBundle() {
    test(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testNoUseFallback() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("translated").build());
    test(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'translated';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testNoUseFallback2() {
    registerMessage(new JsMessage.Builder("MSG_C").appendStringPart("translated").build());
    test(
        lines(
            "/** @desc d */",
            "var MSG_A = goog.getMsg('msg A');",
            "/** @desc d */",
            "var MSG_B = goog.getMsg('msg B');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);"),
        lines(
            "/** @desc d */",
            "var MSG_A = 'msg A';",
            "/** @desc d */",
            "var MSG_B = 'msg B';",
            "var x = MSG_A;"));
  }

  @Test
  public void testTemplateLiteralSimple() {
    registerMessage(new JsMessage.Builder("MSG_A").appendStringPart("Hi\nthere").build());

    test(
        "/** @desc d */\n var MSG_A = goog.getMsg(`asdf`);",
        "/** @desc d */\n var MSG_A='Hi\\nthere'");
  }

  @Test
  public void testTemplateLiteralNameReplacement() {
    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("One ")
            .appendPlaceholderReference("measly")
            .appendStringPart(" ph")
            .build());

    test(
        "/** @desc d */\n var MSG_B=goog.getMsg(`asdf {$measly}`, {measly: x});",
        "/** @desc d */\n var MSG_B='One ' + (x + ' ph')");
  }

  @Test
  public void testTemplateLiteralSubstitutions() {
    // Only allow template literals that are constant strings
    registerMessage(new JsMessage.Builder("MSG_C").appendStringPart("Hi\nthere").build());

    testError(
        "/** @desc d */\n var MSG_C = goog.getMsg(`asdf ${42}`);",
        JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessage() {
    test(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_A = 'A';");
    test(
        "/** @desc d */\n"
            + " var MSG_A = goog.getMsg('User&apos;s &lt; email &amp; address &gt; are"
            + " &quot;correct&quot;', {}, {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_A = 'User\\'s < email & address > are \"correct\"';");
    test(
        "/** @desc d */\n"
            + " var MSG_A = goog.getMsg('&lt; {$startSpan}email &amp; address{$endSpan} &gt;', "
            + "{'startSpan': '<span title=\"&lt;info&gt;\">', 'endSpan': '</span>'}, "
            + "{unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_A = '< ' + ('<span title=\"&lt;info&gt;\">' + "
            + "('email & address' + ('</span>' + (' >'))));");
    test(
        "/** @desc d */\n"
            + " var MSG_A = goog.getMsg('&amp;lt;double &amp;amp; escaping&amp;gt;', {},"
            + " {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_A = '&lt;double &amp; escaping&gt;';");
  }

  @Test
  public void testReplaceUnescapeHtmlEntitiesMessageWithReplacement() {
    registerMessage(
        new JsMessage.Builder("MSG_A")
            .appendStringPart("User")
            .appendStringPart("&")
            .appendStringPart("apos;s &")
            .appendStringPart("lt;")
            .appendStringPart(" email &a")
            .appendStringPart("mp; address &gt")
            .appendStringPart("; are &quot;correct")
            .appendStringPart("&quot;")
            .build());
    test(
        "/** @desc d */\n var MSG_A = goog.getMsg('A', {}, {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_A = 'User\\'s < email & address > are \"correct\"';");

    registerMessage(
        new JsMessage.Builder("MSG_B")
            .appendStringPart("User")
            .appendStringPart("&apos;")
            .appendStringPart("s ")
            .appendStringPart("&lt;")
            .appendStringPart(" email ")
            .appendStringPart("&amp;")
            .appendStringPart(" address ")
            .appendStringPart("&gt;")
            .appendStringPart(" are ")
            .appendStringPart("&quot;")
            .appendStringPart("correct")
            .appendStringPart("&quot;")
            .build());
    test(
        "/** @desc d */\n var MSG_B = goog.getMsg('B', {}, {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_B = 'User\\'s < email & address > are \"correct\"';");

    registerMessage(
        new JsMessage.Builder("MSG_C")
            .appendPlaceholderReference("br")
            .appendStringPart("&")
            .appendStringPart("amp;")
            .appendPlaceholderReference("x")
            .appendPlaceholderReference("y")
            .appendStringPart("&ap")
            .appendPlaceholderReference("z")
            .appendStringPart("os;")
            .build());
    test(
        "/** @desc d */\n"
            + " var MSG_C = goog.getMsg('{$br}{$x}{$y}{$z}', {'br': '<br>', 'x': 'X', 'y': 'Y',"
            + " 'z': 'Z'}, {unescapeHtmlEntities: true});",
        "/** @desc d */\n var MSG_C = '<br>'+('&'+('X'+('Y'+('&ap'+('Z'+('os;'))))))");
  }

  @Test
  public void testReplaceHtmlMessageWithPlaceholder() {
    registerMessage(
        new JsMessage.Builder("MSG_A")
            .appendStringPart("Hello <")
            .appendPlaceholderReference("br")
            .appendStringPart("&gt;")
            .build());

    test(
        "/** @desc d */\n var MSG_A = goog.getMsg('{$br}', {'br': '<br>'}, {html: true});",
        "/** @desc d */\n var MSG_A='Hello &lt;'+('<br>'+'&gt;')");
  }

  private void registerMessage(JsMessage message) {
    messages.put(message.getId(), message);
  }

  private class SimpleMessageBundle implements MessageBundle {

    @Override
    public JsMessage getMessage(String id) {
      return messages.get(id);
    }

    @Override
    public Iterable<JsMessage> getAllMessages() {
      return messages.values();
    }

    @Override
    public JsMessage.IdGenerator idGenerator() {
      return null;
    }
  }
}
