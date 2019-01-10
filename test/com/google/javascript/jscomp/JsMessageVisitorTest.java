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
import static com.google.javascript.jscomp.JsMessage.Style.CLOSURE;
import static com.google.javascript.jscomp.JsMessage.Style.LEGACY;
import static com.google.javascript.jscomp.JsMessage.Style.RELAX;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;
import static com.google.javascript.jscomp.JsMessageVisitor.isLowerCamelCaseWithNumericSuffixes;
import static com.google.javascript.jscomp.JsMessageVisitor.toLowerCamelCaseWithNumericSuffixes;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.JsMessage.Style;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link JsMessageVisitor}.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
@RunWith(JUnit4.class)
public final class JsMessageVisitorTest {
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

  @Before
  public void setUp() throws Exception {
    messages = new ArrayList<>();
    mode = JsMessage.Style.LEGACY;
    compilerOptions = null;
    renameMessages = false;
  }

  @Test
  public void testJsMessageOnVar() {
    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    assertThat(msg.getSourceName()).isEqualTo("[testcode]");
  }

  @Test
  public void testJsMessageOnLet() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    extractMessagesSafely(
        "/** @desc Hello */ let MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    assertThat(msg.getSourceName()).isEqualTo("[testcode]");
  }

  @Test
  public void testJsMessageOnConst() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    extractMessagesSafely(
        "/** @desc Hello */ const MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    assertThat(msg.getSourceName()).isEqualTo("[testcode]");
  }

  @Test
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
    assertThat(msg1.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg1.getDesc()).isEqualTo("Hello");
    assertThat(msg1.getSourceName()).isEqualTo("source1.html");

    JsMessage msg2 = messages.get(1);
    assertThat(msg2.getKey()).isEqualTo("MSG_HI");
    assertThat(msg2.getDesc()).isEqualTo("Hi");
    assertThat(msg2.getSourceName()).isEqualTo("source2.html");
  }

  @Test
  public void testJsMessageOnProperty() {
    extractMessagesSafely("/** @desc a */ " +
        "pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
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
    assertThat(msg.getKey()).isEqualTo("MSG_MENU");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testMsgInEnum() {
    extractMessages(
        LINE_JOINER.join(
            "/**",
            " * @enum {number}",
            " */",
            "var MyEnum = {",
            "  MSG_ONE: 0",
            "};"));
    assertThat(compiler.getErrors()).hasLength(1);
    assertError(compiler.getErrors()[0]).hasType(MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testMsgInEnumWithSuppression() {
    extractMessagesSafely(
        LINE_JOINER.join(
            "/** @fileoverview",
            " * @suppress {messageConventions}",
            " */",
            "",
            "/**",
            " * @enum {number}",
            " */",
            "var MyEnum = {",
            "  MSG_ONE: 0",
            "};"));
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testJsMessageOnObjLit() {
    extractMessagesSafely("" +
        "pint.sub = {" +
        "/** @desc a */ MSG_MENU_MARK_AS_UNREAD: goog.getMsg('a')}");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testInvalidJsMessageOnObjLit() {
    extractMessages(""
        + "pint.sub = {"
        + "  /** @desc a */ MSG_MENU_MARK_AS_UNREAD: undefined"
        + "}");
    assertThat(compiler.getErrors()).hasLength(1);
    assertError(compiler.getErrors()[0]).hasType(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testJsMessageAliasOnObjLit() {
    extractMessagesSafely(""
        + "pint.sub = {"
        + "  MSG_MENU_MARK_AS_UNREAD: another.namespace.MSG_MENU_MARK_AS_UNREAD"
        + "}");
  }

  @Test
  public void testJsMessageOnRHSOfVar() {
    extractMessagesSafely("var MSG_MENU_MARK_AS_UNREAD = a.name.space.MSG_MENU_MARK_AS_UNREAD;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testOrphanedJsMessage() {
    extractMessagesSafely("goog.getMsg('a')");
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(messages).isEmpty();

    JSError warn = compiler.getWarnings()[0];
    assertError(warn).hasType(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
  }

  @Test
  public void testMessageWithoutDescription() {
    extractMessagesSafely("var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");

    assertError(compiler.getWarnings()[0]).hasType(JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);
  }

  @Test
  public void testIncorrectMessageReporting() {
    extractMessages("var MSG_HELLO = goog.getMsg('a' + + 'b')");
    assertThat(compiler.getErrors()).hasLength(1);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();

    JSError malformedTreeError = compiler.getErrors()[0];
    assertError(malformedTreeError).hasType(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(malformedTreeError.description)
        .isEqualTo("Message parse tree malformed. " + "STRING or ADD node expected; found: POS");
  }

  @Test
  public void testTemplateLiteral() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);

    extractMessages("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello`);");
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.toString()).isEqualTo("hello");
  }

  @Test
  public void testTemplateLiteralWithSubstitution() {
    compilerOptions = new CompilerOptions();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);

    extractMessages("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello ${name}`);");
    assertThat(compiler.getErrors()).hasLength(1);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();

    JSError malformedTreeError = compiler.getErrors()[0];
    assertError(malformedTreeError).hasType(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(malformedTreeError.description).isEqualTo(
        "Message parse tree malformed."
            + " Template literals with substitutions are not allowed.");
  }

  @Test
  public void testEmptyMessage() {
    // This is an edge case. Empty messages are useless, but shouldn't fail
    extractMessagesSafely("var MSG_EMPTY = '';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_EMPTY");
    assertThat(msg.toString()).isEmpty();
  }

  @Test
  public void testConcatOfStrings() {
    extractMessagesSafely("var MSG_NOTEMPTY = 'aa' + 'bbb' \n + ' ccc';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_NOTEMPTY");
    assertThat(msg.toString()).isEqualTo("aabbb ccc");
  }

  @Test
  public void testLegacyFormatDescription() {
    extractMessagesSafely("var MSG_SILLY = 'silly test message';\n"
        + "var MSG_SILLY_HELP = 'help text';");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.toString()).isEqualTo("silly test message");
  }

  @Test
  public void testLegacyFormatParametizedFunction() {
    extractMessagesSafely("var MSG_SILLY = function(one, two) {"
        + "  return one + ', ' + two + ', buckle my shoe';"
        + "};");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.getDesc()).isNull();
    assertThat(msg.toString()).isEqualTo("{$one}, {$two}, buckle my shoe");
  }

  @Test
  public void testLegacyMessageWithDescAnnotation() {
    // Well, is was better do not allow legacy messages with @desc annotations,
    // but people love to mix styles so we need to check @desc also.
    extractMessagesSafely(
        "/** @desc The description */ var MSG_A = 'The Message';");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_A");
    assertThat(msg.toString()).isEqualTo("The Message");
    assertThat(msg.getDesc()).isEqualTo("The description");
  }

  @Test
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
    assertThat(msg.getKey()).isEqualTo("MSG_A");
    assertThat(msg.toString()).isEqualTo("The Message");
    assertThat(msg.getDesc()).isEqualTo("The description in @desc");
  }

  @Test
  public void testClosureMessageWithHelpPostfix() {
    extractMessagesSafely("/** @desc help text */\n"
        + "var MSG_FOO_HELP = goog.getMsg('Help!');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_FOO_HELP");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.toString()).isEqualTo("Help!");
  }

  @Test
  public void testClosureMessageWithoutGoogGetmsg() {
    mode = CLOSURE;

    extractMessages("var MSG_FOO_HELP = 'I am a bad message';");

    assertThat(messages).isEmpty();
    assertThat(compiler.getWarnings()).hasLength(1);
    assertError(compiler.getWarnings()[0]).hasType(
        JsMessageVisitor.MESSAGE_NOT_INITIALIZED_USING_NEW_SYNTAX);
  }

  @Test
  public void testClosureFormatParametizedFunction() {
    extractMessagesSafely("/** @desc help text */"
        + "var MSG_SILLY = goog.getMsg('{$adjective} ' + 'message', "
        + "{'adjective': 'silly'});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.toString()).isEqualTo("{$adjective} message");
  }

  @Test
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
    assertThat(msg.getKey()).isEqualTo("MSG_HUGE");
    assertThat(msg.getDesc()).isEqualTo("A message with lots of stuff.");
    assertThat(msg.isHidden()).isTrue();
    assertThat(msg.toString())
        .isEqualTo(
            "{$startLink_1}Google{$endLink}{$startLink_2}blah{$endLink}"
                + "{$boo}{$foo_001}{$boo}{$foo_002}{$xxx_001}{$image}"
                + "{$image_001}{$xxx_002}");
  }

  @Test
  public void testUnnamedGoogleMessage() {
    extractMessagesSafely("var MSG_UNNAMED = goog.getMsg('Hullo');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getDesc()).isNull();
    assertThat(msg.getKey()).isEqualTo("MSG_16LJMYKCXT84X");
    assertThat(msg.getId()).isEqualTo("MSG_16LJMYKCXT84X");
  }

  @Test
  public void testEmptyTextMessage() {
    extractMessagesSafely("/** @desc text */ var MSG_FOO = goog.getMsg('');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(compiler.getWarnings()[0].description)
        .isEqualTo(
            "Message value of MSG_FOO is just an empty string. " + "Empty messages are forbidden.");
  }

  @Test
  public void testEmptyTextComplexMessage() {
    extractMessagesSafely("/** @desc text */ var MSG_BAR = goog.getMsg("
        + "'' + '' + ''     + ''\n+'');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).hasLength(1);
    assertThat(compiler.getWarnings()[0].description)
        .isEqualTo(
            "Message value of MSG_BAR is just an empty string. " + "Empty messages are forbidden.");
  }

  @Test
  public void testMsgVarWithoutAssignment() {
    extractMessages("var MSG_SILLY;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_HAS_NO_VALUE);
  }

  @Test
  public void testRegularVarWithoutAssignment() {
    extractMessagesSafely("var SILLY;");

    assertThat(messages).isEmpty();
  }

  @Test
  @Ignore // Currently unimplemented.
  public void testMsgPropertyWithoutAssignment() {
    extractMessages("goog.message.MSG_SILLY_PROP;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertThat(error.description).isEqualTo("Message MSG_SILLY_PROP has no value");
  }

  @Test
  public void testMsgVarWithIncorrectRightSide() {
    extractMessages("var MSG_SILLY = 0;");

    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertThat(error.description)
        .isEqualTo("Message parse tree malformed. Cannot parse value of " + "message MSG_SILLY");
  }

  @Test
  public void testIncorrectMessage() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = {};");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertThat(error.description)
        .isEqualTo(
            "Message parse tree malformed."
                + " Message must be initialized using goog.getMsg function.");
  }

  @Test
  public void testUnrecognizedFunction() {
    mode = CLOSURE;
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = somefunc('a')");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).hasLength(1);
    JSError error = compiler.getErrors()[0];
    assertThat(error.description)
        .isEqualTo(
            "Message parse tree malformed. Message initialized using unrecognized function. "
                + "Please use goog.getMsg() instead.");
  }

  @Test
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
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.toString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
    assertThat(msg.isHidden()).isTrue();
  }

  @Test
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
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.toString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
    assertThat(msg.isHidden()).isTrue();
  }

  @Test
  public void testAlmostButNotExternalMessage() {
    extractMessagesSafely(
        "/** @desc External */ var MSG_EXTERNAL = goog.getMsg('External');");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).isExternal()).isFalse();
    assertThat(messages.get(0).getKey()).isEqualTo("MSG_EXTERNAL");
  }

  @Test
  public void testExternalMessage() {
    extractMessagesSafely("var MSG_EXTERNAL_111 = goog.getMsg('Hello World');");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).isExternal()).isTrue();
    assertThat(messages.get(0).getId()).isEqualTo("111");
  }

  @Test
  public void testExternalMessage_customSuffix() {
    extractMessagesSafely("var MSG_EXTERNAL_111$$1 = goog.getMsg('Hello World');");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).isExternal()).isTrue();
    assertThat(messages.get(0).getId()).isEqualTo("111");
  }

  @Test
  public void testIsValidMessageNameStrict() {
    JsMessageVisitor visitor = new DummyJsVisitor(CLOSURE);

    assertThat(visitor.isMessageName("MSG_HELLO", true)).isTrue();
    assertThat(visitor.isMessageName("MSG_", true)).isTrue();
    assertThat(visitor.isMessageName("MSG_HELP", true)).isTrue();
    assertThat(visitor.isMessageName("MSG_FOO_HELP", true)).isTrue();

    assertThat(visitor.isMessageName("_FOO_HELP", true)).isFalse();
    assertThat(visitor.isMessageName("MSGFOOP", true)).isFalse();
  }

  @Test
  public void testIsValidMessageNameRelax() {
    JsMessageVisitor visitor = new DummyJsVisitor(RELAX);

    assertThat(visitor.isMessageName("MSG_HELP", false)).isFalse();
    assertThat(visitor.isMessageName("MSG_FOO_HELP", false)).isFalse();
  }

  @Test
  public void testIsValidMessageNameLegacy() {
    theseAreLegacyMessageNames(new DummyJsVisitor(RELAX));
    theseAreLegacyMessageNames(new DummyJsVisitor(LEGACY));
  }

  private void theseAreLegacyMessageNames(JsMessageVisitor visitor) {
    assertThat(visitor.isMessageName("MSG_HELLO", false)).isTrue();
    assertThat(visitor.isMessageName("MSG_", false)).isTrue();

    assertThat(visitor.isMessageName("MSG_HELP", false)).isFalse();
    assertThat(visitor.isMessageName("MSG_FOO_HELP", false)).isFalse();
    assertThat(visitor.isMessageName("_FOO_HELP", false)).isFalse();
    assertThat(visitor.isMessageName("MSGFOOP", false)).isFalse();
  }

  @Test
  public void testUnexistedPlaceholders() {
    extractMessages("var MSG_FOO = goog.getMsg('{$foo}:', {});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(error.description)
        .isEqualTo(
            "Message parse tree malformed. Unrecognized message " + "placeholder referenced: foo");
  }

  @Test
  public void testUnusedReferenesAreNotOK() {
    extractMessages("/** @desc AA */ "
        + "var MSG_FOO = goog.getMsg('lalala:', {foo:1});");
    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(error.description)
        .isEqualTo("Message parse tree malformed. Unused message placeholder: " + "foo");
  }

  @Test
  public void testDuplicatePlaceHoldersAreBad() {
    extractMessages("var MSG_FOO = goog.getMsg("
        + "'{$foo}:', {'foo': 1, 'foo' : 2});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(error.description)
        .isEqualTo("Message parse tree malformed. Duplicate placeholder " + "name: foo");
  }

  @Test
  public void testDuplicatePlaceholderReferencesAreOk() {
    extractMessagesSafely("var MSG_FOO = goog.getMsg("
        + "'{$foo}:, {$foo}', {'foo': 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.toString()).isEqualTo("{$foo}:, {$foo}");
  }

  @Test
  public void testCamelcasePlaceholderNamesAreOk() {
    extractMessagesSafely("var MSG_WITH_CAMELCASE = goog.getMsg("
        + "'Slide {$slideNumber}:', {'slideNumber': opt_index + 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_WITH_CAMELCASE");
    assertThat(msg.toString()).isEqualTo("Slide {$slideNumber}:");
    List<CharSequence> parts = msg.parts();
    assertThat(parts).hasSize(3);
    assertThat(((JsMessage.PlaceholderReference) parts.get(1)).getName()).isEqualTo("slideNumber");
  }

  @Test
  public void testWithNonCamelcasePlaceholderNamesAreNotOk() {
    extractMessages("var MSG_WITH_CAMELCASE = goog.getMsg("
        + "'Slide {$slide_number}:', {'slide_number': opt_index + 1});");

    assertThat(messages).isEmpty();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).hasLength(1);
    JSError error = errors[0];
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(error.description)
        .isEqualTo(
            "Message parse tree malformed. Placeholder name not in "
                + "lowerCamelCase: slide_number");
  }

  @Test
  public void testUnquotedPlaceholdersAreOk() {
    extractMessagesSafely("/** @desc Hello */ "
        + "var MSG_FOO = goog.getMsg('foo {$unquoted}:', {unquoted: 12});");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testIsLowerCamelCaseWithNumericSuffixes() {
    assertThat(isLowerCamelCaseWithNumericSuffixes("name")).isTrue();
    assertThat(isLowerCamelCaseWithNumericSuffixes("NAME")).isFalse();
    assertThat(isLowerCamelCaseWithNumericSuffixes("Name")).isFalse();

    assertThat(isLowerCamelCaseWithNumericSuffixes("a4Letter")).isTrue();
    assertThat(isLowerCamelCaseWithNumericSuffixes("A4_LETTER")).isFalse();

    assertThat(isLowerCamelCaseWithNumericSuffixes("startSpan_1_23")).isTrue();
    assertThat(isLowerCamelCaseWithNumericSuffixes("startSpan_1_23b")).isFalse();
    assertThat(isLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23")).isFalse();

    assertThat(isLowerCamelCaseWithNumericSuffixes("")).isFalse();
  }

  @Test
  public void testToLowerCamelCaseWithNumericSuffixes() {
    assertThat(toLowerCamelCaseWithNumericSuffixes("NAME")).isEqualTo("name");
    assertThat(toLowerCamelCaseWithNumericSuffixes("A4_LETTER")).isEqualTo("a4Letter");
    assertThat(toLowerCamelCaseWithNumericSuffixes("START_SPAN_1_23")).isEqualTo("startSpan_1_23");
  }

  @Test
  public void testDuplicateMessageError() {
    extractMessages(
        "(function () {/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')})" +
        "(function () {/** @desc Hello2 */ var MSG_HELLO = goog.getMsg('a')})");

    assertThat(compiler.getWarnings()).isEmpty();
    assertOneError(JsMessageVisitor.MESSAGE_DUPLICATE_KEY);
  }

  @Test
  public void testNoDuplicateErrorOnExternMessage() {
    extractMessagesSafely(
        "(function () {/** @desc Hello */ " +
        "var MSG_EXTERNAL_2 = goog.getMsg('a')})" +
        "(function () {/** @desc Hello2 */ " +
        "var MSG_EXTERNAL_2 = goog.getMsg('a')})");
  }

  @Test
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

  @Test
  public void testUsingMsgPrefixWithFallback_rename() {
    renameMessages = true;
    extractMessages(
        LINE_JOINER.join(
            "function f() {",
            "/** @desc Hello */ var MSG_A = goog.getMsg('hello');",
            "/** @desc Hello */ var MSG_B = goog.getMsg('hello!');",
            "var x = goog.getMsgWithFallback(MSG_A, MSG_B);",
            "}"));
    assertNoErrors();
  }

  @Test
  public void testErrorWhenUsingMsgPrefixWithFallback() {
    extractMessages(
        "/** @desc Hello */ var MSG_HELLO_1 = goog.getMsg('hello');\n" +
        "/** @desc Hello */ var MSG_HELLO_2 = goog.getMsg('hello');\n" +
        "/** @desc Hello */ " +
        "var MSG_HELLO_3 = goog.getMsgWithFallback(MSG_HELLO_1, MSG_HELLO_2);");
    assertOneError(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testRenamedMessages_var() {
    renameMessages = true;

    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    assertThat(msg.getSourceName()).isEqualTo("[testcode]");
  }

  @Test
  public void testRenamedMessages_getprop() {
    renameMessages = true;

    extractMessagesSafely("/** @desc a */ pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
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
      NodeTraversal.traverse(compiler, root, renameMessagesVisitor);
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

  private static class DummyJsVisitor extends JsMessageVisitor {

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
