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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.JsMessage.Style;
import static com.google.javascript.jscomp.JsMessage.Style.CLOSURE;
import static com.google.javascript.jscomp.JsMessage.Style.LEGACY;
import static com.google.javascript.jscomp.JsMessage.Style.RELAX;
import static com.google.javascript.jscomp.JsMessageVisitor.isLowerCamelCaseWithNumericSuffixes;
import static com.google.javascript.jscomp.JsMessageVisitor.toLowerCamelCaseWithNumericSuffixes;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.LinkedList;
import java.util.List;

/**
 * Test for {@link JsMessageVisitor}.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public final class JsMessageVisitorTest extends TestCase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private static class RenameMessagesVisitor extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && n.getString() != null && n.getString().startsWith("MSG_")) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("some_prefix_" + originalName);
      } else if (n.isGetProp() && parent.isAssign() && n.getQualifiedName().contains(".MSG_")) {
        String originalName = n.getLastChild().getString();
        n.setOriginalName(originalName);
        n.getLastChild().setString("some_prefix_" + originalName);
      }
    }
  }

  private CompilerOptions compilerOptions;
  private Compiler compiler;
  private List<JsMessage> messages;
  private JsMessage.Style mode;
  private boolean renameMessages = false;

  @Override
  protected void setUp() throws Exception {
    messages = new LinkedList<>();
    mode = JsMessage.Style.LEGACY;
    compilerOptions = null;
    renameMessages = false;
  }

  public void testJsMessageOnVar() {
    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_HELLO", msg.getKey());
    assertEquals("Hello", msg.getDesc());
    assertEquals("[testcode]", msg.getSourceName());
  }

  public void testJsMessageOnLet() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT6);
    extractMessagesSafely(
        "/** @desc Hello */ let MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_HELLO", msg.getKey());
    assertEquals("Hello", msg.getDesc());
    assertEquals("[testcode]", msg.getSourceName());
  }

  public void testJsMessageOnConst() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT6);
    extractMessagesSafely(
        "/** @desc Hello */ const MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_HELLO", msg.getKey());
    assertEquals("Hello", msg.getDesc());
    assertEquals("[testcode]", msg.getSourceName());
  }

  public void testJsMessagesWithSrcMap() throws Exception {
    SourceMapGeneratorV3 sourceMap = new SourceMapGeneratorV3();
    sourceMap.addMapping("source1.html", null, new FilePosition(10, 0),
        new FilePosition(0, 0), new FilePosition(0, 100));
    sourceMap.addMapping("source2.html", null, new FilePosition(10, 0),
        new FilePosition(1, 0), new FilePosition(1, 100));
    StringBuilder output = new StringBuilder();
    sourceMap.appendTo(output, "unused.js");

    compilerOptions = new CompilerOptions();
    compilerOptions.inputSourceMaps = ImmutableMap.of(
       "[testcode]", new SourceMapInput(
           SourceFile.fromCode("example.srcmap", output.toString())));

    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a');\n"
        + "/** @desc Hi */ var MSG_HI = goog.getMsg('b');\n");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(2);

    JsMessage msg1 = messages.get(0);
    assertEquals("MSG_HELLO", msg1.getKey());
    assertEquals("Hello", msg1.getDesc());
    assertEquals("source1.html", msg1.getSourceName());

    JsMessage msg2 = messages.get(1);
    assertEquals("MSG_HI", msg2.getKey());
    assertEquals("Hi", msg2.getDesc());
    assertEquals("source2.html", msg2.getSourceName());
  }

  public void testJsMessageOnProperty() {
    extractMessagesSafely("/** @desc a */ " +
        "pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_MENU_MARK_AS_UNREAD", msg.getKey());
    assertEquals("a", msg.getDesc());
  }

  public void testStaticInheritance() {
    extractMessagesSafely(
        LINE_JOINER.join(
            "/** @desc a */",
            "foo.bar.BaseClass.MSG_MENU = goog.getMsg('hi');",
            "/**",
            " * @desc a",
            " * @suppress {visibility}",
            " */",
            "foo.bar.Subclass.MSG_MENU = foo.bar.BaseClass.MSG_MENU;"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_MENU", msg.getKey());
    assertEquals("a", msg.getDesc());
  }

  public void testJsMessageOnObjLit() {
    extractMessagesSafely("" +
        "pint.sub = {" +
        "/** @desc a */ MSG_MENU_MARK_AS_UNREAD: goog.getMsg('a')}");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_MENU_MARK_AS_UNREAD", msg.getKey());
    assertEquals("a", msg.getDesc());
  }

  public void testOrphanedJsMessage() {
    extractMessagesSafely("goog.getMsg('a')");
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(messages).isEmpty();

    JSError warn = compiler.getWarnings()[0];
    assertError(warn).hasType(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
  }

  public void testMessageWithoutDescription() {
    extractMessagesSafely("var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_HELLO", msg.getKey());

    assertError(compiler.getWarnings()[0]).hasType(JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);
  }

  public void testIncorrectMessageReporting() {
    extractMessages("var MSG_HELLO = goog.getMsg('a' + + 'b')");
    assertThat(compiler.getErrors()).hasLength(1);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();

    JSError malformedTreeError = compiler.getErrors()[0];
    assertError(malformedTreeError).hasType(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertEquals("Message parse tree malformed. "
        + "STRING or ADD node expected; found: POS",
        malformedTreeError.description);
  }

  public void testEmptyMessage() {
    // This is an edge case. Empty messages are useless, but shouldn't fail
    extractMessagesSafely("var MSG_EMPTY = '';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_EMPTY", msg.getKey());
    assertThat(msg.toString()).isEmpty();
  }

  public void testConcatOfStrings() {
    extractMessagesSafely("var MSG_NOTEMPTY = 'aa' + 'bbb' \n + ' ccc';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_NOTEMPTY", msg.getKey());
    assertEquals("aabbb ccc", msg.toString());
  }

  public void testLegacyFormatDescription() {
    extractMessagesSafely("var MSG_SILLY = 'silly test message';\n"
        + "var MSG_SILLY_HELP = 'help text';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_SILLY", msg.getKey());
    assertEquals("help text", msg.getDesc());
    assertEquals("silly test message", msg.toString());
  }

  public void testLegacyFormatParametizedFunction() {
    extractMessagesSafely("var MSG_SILLY = function(one, two) {"
        + "  return one + ', ' + two + ', buckle my shoe';"
        + "};");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_SILLY", msg.getKey());
    assertEquals(null, msg.getDesc());
    assertEquals("{$one}, {$two}, buckle my shoe", msg.toString());
  }

  public void testLegacyMessageWithDescAnnotation() {
    // Well, is was better do not allow legacy messages with @desc annotations,
    // but people love to mix styles so we need to check @desc also.
    extractMessagesSafely(
        "/** @desc The description */ var MSG_A = 'The Message';");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
    JsMessage msg = messages.get(0);
    assertEquals("MSG_A", msg.getKey());
    assertEquals("The Message", msg.toString());
    assertEquals("The description", msg.getDesc());
  }

  public void testLegacyMessageWithDescAnnotationAndHelpVar() {
    mode = RELAX;

    // Well, is was better do not allow legacy messages with @desc annotations,
    // but people love to mix styles so we need to check @desc also.
    extractMessagesSafely(
        "var MSG_A_HELP = 'This is a help var';\n" +
        "/** @desc The description in @desc*/ var MSG_A = 'The Message';");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).hasLength(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_A", msg.getKey());
    assertEquals("The Message", msg.toString());
    assertEquals("The description in @desc", msg.getDesc());
  }

  public void testClosureMessageWithHelpPostfix() {
    extractMessagesSafely("/** @desc help text */\n"
        + "var MSG_FOO_HELP = goog.getMsg('Help!');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_FOO_HELP", msg.getKey());
    assertEquals("help text", msg.getDesc());
    assertEquals("Help!", msg.toString());
  }

  public void testClosureMessageWithoutGoogGetmsg() {
    mode = CLOSURE;

    extractMessages("var MSG_FOO_HELP = 'I am a bad message';");

    assertThat(messages).isEmpty();
    assertThat(compiler.getWarnings()).hasLength(1);
    assertError(compiler.getWarnings()[0]).hasType(
        JsMessageVisitor.MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX);
  }

  public void testClosureFormatParametizedFunction() {
    extractMessagesSafely("/** @desc help text */"
        + "var MSG_SILLY = goog.getMsg('{$adjective} ' + 'message', "
        + "{'adjective': 'silly'});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_SILLY", msg.getKey());
    assertEquals("help text", msg.getDesc());
    assertEquals("{$adjective} message", msg.toString());
  }

  public void testHugeMessage() {
    extractMessagesSafely("/**" +
        " * @desc A message with lots of stuff.\n" +
        " * @hidden\n" +
        " */" +
        "var MSG_HUGE = goog.getMsg(" +
        "    '{$startLink_1}Google{$endLink}' +" +
        "    '{$startLink_2}blah{$endLink}{$boo}{$foo_001}{$boo}' +" +
        "    '{$foo_002}{$xxx_001}{$image}{$image_001}{$xxx_002}'," +
        "    {'startLink_1': '<a href=http://www.google.com/>'," +
        "     'endLink': '</a>'," +
        "     'startLink_2': '<a href=\"' + opt_data.url + '\">'," +
        "     'boo': opt_data.boo," +
        "     'foo_001': opt_data.foo," +
        "     'foo_002': opt_data.boo.foo," +
        "     'xxx_001': opt_data.boo + opt_data.foo," +
        "     'image': htmlTag7," +
        "     'image_001': opt_data.image," +
        "     'xxx_002': foo.callWithOnlyTopLevelKeys(" +
        "         bogusFn, opt_data, null, 'bogusKey1'," +
        "         opt_data.moo, 'bogusKey2', param10)});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_HUGE", msg.getKey());
    assertEquals("A message with lots of stuff.", msg.getDesc());
    assertTrue(msg.isHidden());
    assertEquals("{$startLink_1}Google{$endLink}{$startLink_2}blah{$endLink}" +
        "{$boo}{$foo_001}{$boo}{$foo_002}{$xxx_001}{$image}" +
        "{$image_001}{$xxx_002}", msg.toString());
  }

  public void testUnnamedGoogleMessage() {
    extractMessagesSafely("var MSG_UNNAMED_2 = goog.getMsg('Hullo');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals(null, msg.getDesc());
    assertEquals("MSG_16LJMYKCXT84X", msg.getKey());
    assertEquals("MSG_16LJMYKCXT84X", msg.getId());
  }

  public void testEmptyTextMessage() {
    extractMessagesSafely("/** @desc text */ var MSG_FOO = goog.getMsg('');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).hasLength(1);
    assertEquals("Message value of MSG_FOO is just an empty string. "
        + "Empty messages are forbidden.",
        compiler.getWarnings()[0].description);
  }

  public void testEmptyTextComplexMessage() {
    extractMessagesSafely("/** @desc text */ var MSG_BAR = goog.getMsg("
        + "'' + '' + ''     + ''\n+'');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).hasLength(1);
    assertEquals("Message value of MSG_BAR is just an empty string. "
        + "Empty messages are forbidden.",
        compiler.getWarnings()[0].description);
  }

  public void testMessageIsNoUnnamed() {
    extractMessagesSafely("var MSG_UNNAMED_ITEM = goog.getMsg('Hullo');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_UNNAMED_ITEM", msg.getKey());
    assertFalse(msg.isHidden());
  }

  public void testMsgVarWithoutAssignment() {
    extractMessages("var MSG_SILLY;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertEquals(JsMessageVisitor.MESSAGE_HAS_NO_VALUE, error.getType());
  }

  public void testRegularVarWithoutAssignment() {
    extractMessagesSafely("var SILLY;");

    assertThat(messages).isEmpty();
  }

  public void itIsNotImplementedYet_testMsgPropertyWithoutAssignment() {
    extractMessages("goog.message.MSG_SILLY_PROP;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertEquals("Message MSG_SILLY_PROP has no value", error.description);
  }

  public void testMsgVarWithIncorrectRightSide() {
    extractMessages("var MSG_SILLY = 0;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertEquals("Message parse tree malformed. Cannot parse value of "
        + "message MSG_SILLY", error.description);
  }

  public void testIncorrectMessage() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = {};");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertEquals("Message parse tree malformed. "+
                 "Message must be initialized using goog.getMsg function.",
                 error.description);
  }

  public void testUnrecognizedFunction() {
    mode = CLOSURE;
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = somefunc('a')");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertEquals("Message parse tree malformed. "+
                 "Message initialized using unrecognized function. " +
                 "Please use goog.getMsg() instead.",
                 error.description);
  }

  public void testExtractPropertyMessage() {
    extractMessagesSafely("/**"
        + " * @desc A message that demonstrates placeholders\n"
        + " * @hidden\n"
        + " */"
        + "a.b.MSG_SILLY = goog.getMsg(\n"
        + "    '{$adjective} ' + '{$someNoun}',\n"
        + "    {'adjective': adj, 'someNoun': noun});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_SILLY", msg.getKey());
    assertEquals("{$adjective} {$someNoun}", msg.toString());
    assertEquals("A message that demonstrates placeholders", msg.getDesc());
    assertTrue(msg.isHidden());
  }

  public void testExtractPropertyMessageInFunction() {
    extractMessagesSafely(""
        + "function f() {\n"
        + "  /**\n"
        + "   * @desc A message that demonstrates placeholders\n"
        + "   * @hidden\n"
        + "   */\n"
        + "  a.b.MSG_SILLY = goog.getMsg(\n"
        + "      '{$adjective} ' + '{$someNoun}',\n"
        + "      {'adjective': adj, 'someNoun': noun});\n"
        + "}");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_SILLY", msg.getKey());
    assertEquals("{$adjective} {$someNoun}", msg.toString());
    assertEquals("A message that demonstrates placeholders", msg.getDesc());
    assertTrue(msg.isHidden());
  }

  public void testAlmostButNotExternalMessage() {
    extractMessagesSafely(
        "/** @desc External */ var MSG_EXTERNAL = goog.getMsg('External');");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    assertFalse(messages.get(0).isExternal());
    assertEquals("MSG_EXTERNAL", messages.get(0).getKey());
  }

  public void testExternalMessage() {
    extractMessagesSafely("var MSG_EXTERNAL_111 = goog.getMsg('Hello World');");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    assertTrue(messages.get(0).isExternal());
    assertEquals("111", messages.get(0).getId());
  }

  public void testIsValidMessageNameStrict() {
    JsMessageVisitor visitor = new DummyJsVisitor(CLOSURE);

    assertTrue(visitor.isMessageName("MSG_HELLO", true));
    assertTrue(visitor.isMessageName("MSG_", true));
    assertTrue(visitor.isMessageName("MSG_HELP", true));
    assertTrue(visitor.isMessageName("MSG_FOO_HELP", true));

    assertFalse(visitor.isMessageName("_FOO_HELP", true));
    assertFalse(visitor.isMessageName("MSGFOOP", true));
  }

  public void testIsValidMessageNameRelax() {
    JsMessageVisitor visitor = new DummyJsVisitor(RELAX);

    assertFalse(visitor.isMessageName("MSG_HELP", false));
    assertFalse(visitor.isMessageName("MSG_FOO_HELP", false));
  }

  public void testIsValidMessageNameLegacy() {
    theseAreLegacyMessageNames(new DummyJsVisitor(RELAX));
    theseAreLegacyMessageNames(new DummyJsVisitor(LEGACY));
  }

  private void theseAreLegacyMessageNames(JsMessageVisitor visitor) {
    assertTrue(visitor.isMessageName("MSG_HELLO", false));
    assertTrue(visitor.isMessageName("MSG_", false));

    assertFalse(visitor.isMessageName("MSG_HELP", false));
    assertFalse(visitor.isMessageName("MSG_FOO_HELP", false));
    assertFalse(visitor.isMessageName("_FOO_HELP", false));
    assertFalse(visitor.isMessageName("MSGFOOP", false));
  }

  public void testUnexistedPlaceholders() {
    extractMessages("var MSG_FOO = goog.getMsg('{$foo}:', {});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertEquals(JsMessageVisitor.MESSAGE_TREE_MALFORMED, error.getType());
    assertEquals("Message parse tree malformed. Unrecognized message "
        + "placeholder referenced: foo", error.description);
  }

  public void testUnusedReferenesAreNotOK() {
    extractMessages("/** @desc AA */ "
        + "var MSG_FOO = goog.getMsg('lalala:', {foo:1});");
    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertEquals(JsMessageVisitor.MESSAGE_TREE_MALFORMED, error.getType());
    assertEquals("Message parse tree malformed. Unused message placeholder: "
        + "foo", error.description);
  }

  public void testDuplicatePlaceHoldersAreBad() {
    extractMessages("var MSG_FOO = goog.getMsg("
        + "'{$foo}:', {'foo': 1, 'foo' : 2});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertEquals(JsMessageVisitor.MESSAGE_TREE_MALFORMED, error.getType());
    assertEquals("Message parse tree malformed. Duplicate placeholder "
        + "name: foo", error.description);
  }

  public void testDuplicatePlaceholderReferencesAreOk() {
    extractMessagesSafely("var MSG_FOO = goog.getMsg("
        + "'{$foo}:, {$foo}', {'foo': 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("{$foo}:, {$foo}", msg.toString());
  }

  public void testCamelcasePlaceholderNamesAreOk() {
    extractMessagesSafely("var MSG_WITH_CAMELCASE = goog.getMsg("
        + "'Slide {$slideNumber}:', {'slideNumber': opt_index + 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertEquals("MSG_WITH_CAMELCASE", msg.getKey());
    assertEquals("Slide {$slideNumber}:", msg.toString());
    List<CharSequence> parts = msg.parts();
    assertThat(parts).hasSize(3);
    assertEquals("slideNumber", ((JsMessage.PlaceholderReference) parts.get(1)).getName());
  }

  public void testWithNonCamelcasePlaceholderNamesAreNotOk() {
    extractMessages("var MSG_WITH_CAMELCASE = goog.getMsg("
        + "'Slide {$slide_number}:', {'slide_number': opt_index + 1});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertEquals(JsMessageVisitor.MESSAGE_TREE_MALFORMED, error.getType());
    assertEquals("Message parse tree malformed. Placeholder name not in "
        + "lowerCamelCase: slide_number", error.description);
  }

  public void testUnquotedPlaceholdersAreOk() {
    extractMessagesSafely("/** @desc Hello */ "
        + "var MSG_FOO = goog.getMsg('foo {$unquoted}:', {unquoted: 12});");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  public void testIsLowerCamelCaseWithNumericSuffixes() {
    assertTrue(isLowerCamelCaseWithNumericSuffixes("name"));
    assertFalse(isLowerCamelCaseWithNumericSuffixes("NAME"));
    assertFalse(isLowerCamelCaseWithNumericSuffixes("Name"));

    assertTrue(isLowerCamelCaseWithNumericSuffixes("a4Letter"));
    assertFalse(isLowerCamelCaseWithNumericSuffixes("A4_LETTER"));

    assertTrue(isLowerCamelCaseWithNumericSuffixes("startSpan_1_23"));
    assertFalse(isLowerCamelCaseWithNumericSuffixes("startSpan_1_23b"));
    assertFalse(isLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23"));

    assertFalse(isLowerCamelCaseWithNumericSuffixes(""));
  }

  public void testToLowerCamelCaseWithNumericSuffixes() {
    assertEquals("name", toLowerCamelCaseWithNumericSuffixes("NAME"));
    assertEquals("a4Letter", toLowerCamelCaseWithNumericSuffixes("A4_LETTER"));
    assertEquals("startSpan_1_23",
        toLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23"));
  }

  public void testDuplicateMessageError() {
    extractMessages(
        "(function () {/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')})" +
        "(function () {/** @desc Hello2 */ var MSG_HELLO = goog.getMsg('a')})");

    assertThat(compiler.getWarnings()).isEmpty();
    assertOneError(JsMessageVisitor.MESSAGE_DUPLICATE_KEY);
  }

  public void testNoDuplicateErrorOnExternMessage() {
    extractMessagesSafely(
        "(function () {/** @desc Hello */ " +
        "var MSG_EXTERNAL_2 = goog.getMsg('a')})" +
        "(function () {/** @desc Hello2 */ " +
        "var MSG_EXTERNAL_2 = goog.getMsg('a')})");
  }

  public void testUsingMsgPrefixWithFallback() {
    extractMessages(
        "function f() {\n" +
        "/** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');\n" +
        "/** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');\n" +
        "var x = goog.getMsgWithFallback(\n" +
        "    MSG_UNNAMED_1, MSG_UNNAMED_2);\n" +
        "}\n");
    assertNoErrors();
  }

  public void testErrorWhenUsingMsgPrefixWithFallback() {
    extractMessages(
        "/** @desc Hello */ var MSG_HELLO_1 = goog.getMsg('hello');\n" +
        "/** @desc Hello */ var MSG_HELLO_2 = goog.getMsg('hello');\n" +
        "/** @desc Hello */ " +
        "var MSG_HELLO_3 = goog.getMsgWithFallback(MSG_HELLO_1, MSG_HELLO_2);");
    assertOneError(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  public void testRenamedMessages_var() {
    renameMessages = true;

    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_HELLO", msg.getKey());
    assertEquals("Hello", msg.getDesc());
    assertEquals("[testcode]", msg.getSourceName());
  }

  public void testRenamedMessages_getprop() {
    renameMessages = true;

    extractMessagesSafely("/** @desc a */ pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertEquals("MSG_MENU_MARK_AS_UNREAD", msg.getKey());
    assertEquals("a", msg.getDesc());
  }

  private void assertNoErrors() {
    assertThat(compiler.getErrors()).isEmpty();
  }

  private void assertOneError(DiagnosticType type) {
    assertThat(compiler.getErrors()).hasLength(1);
    assertError(compiler.getErrors()[0]).hasType(type);
  }

  private void extractMessagesSafely(String input) {
    extractMessages(input);
    assertThat(compiler.getErrors()).isEmpty();
  }

  private void extractMessages(String input) {
    compiler = new Compiler();
    if (compilerOptions != null) {
      compiler.initOptions(compilerOptions);
    }
    Node root = compiler.parseTestCode(input);
    JsMessageVisitor visitor = new CollectMessages(compiler);
    if (renameMessages) {
      RenameMessagesVisitor renameMessagesVisitor = new RenameMessagesVisitor();
      NodeTraversal.traverseEs6(compiler, root, renameMessagesVisitor);
    }
    visitor.process(null, root);
  }

  private class CollectMessages extends JsMessageVisitor {

    private CollectMessages(Compiler compiler) {
      super(compiler, true, mode, null);
    }

    @Override
    protected void processJsMessage(JsMessage message,
        JsMessageDefinition definition) {
      messages.add(message);
    }
  }

  private class DummyJsVisitor extends JsMessageVisitor {

    private DummyJsVisitor(Style style) {
      super(null, true, style, null);
    }

    @Override
    protected void processJsMessage(JsMessage message,
        JsMessageDefinition definition) {
      // no-op
    }
  }
}
