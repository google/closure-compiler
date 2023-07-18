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
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_HAS_NO_VALUE;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY;
import static com.google.javascript.jscomp.JsMessageVisitor.MESSAGE_TREE_MALFORMED;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DESCRIPTION_EQUALITY;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DIAGNOSTIC_EQUALITY;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessageVisitor.ExtractedIcuTemplateParts;
import com.google.javascript.jscomp.JsMessageVisitor.IcuMessageTemplateString;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link JsMessageVisitor}. */
@RunWith(JUnit4.class)
public final class JsMessageVisitorTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }

  private static class RenameMessagesVisitor extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && n.getString() != null && n.getString().startsWith("MSG_")) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("some_prefix_" + originalName);
      } else if (n.isGetProp() && parent.isAssign() && n.getQualifiedName().contains(".MSG_")) {
        String originalName = n.getString();
        n.setOriginalName(originalName);
        n.setString("some_prefix_" + originalName);
      }
    }
  }

  private @Nullable CompilerOptions compilerOptions;
  private Compiler compiler;
  private List<JsMessage> messages;
  private List<JsMessageDefinition> messageDefinitions;
  private List<IcuTemplateDefinition> icuTemplateDefinitions;
  private boolean renameMessages = false;

  @Before
  public void setUp() throws Exception {
    messages = new ArrayList<>();
    messageDefinitions = new ArrayList<>();
    icuTemplateDefinitions = new ArrayList<>();
    compilerOptions = null;
    renameMessages = false;
  }

  @Test
  public void testIcuTemplateParsing() {
    final IcuMessageTemplateString icuMessageTemplateString =
        new IcuMessageTemplateString(
            lines(
                "{NUM_PEOPLE, plural, offset:1 ",
                // Some placeholders are ignored, because there's no additional information
                // for them (original code or example text). There's no need to generate
                // placeholder parts for those.
                "=0 {I see {START_BOLD}no one at all{END_BOLD} in {INTERPOLATION_2}.}",
                "=1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}",
                "=2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}",
                "other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}",
                "}"));
    final ExtractedIcuTemplateParts extractedParts =
        icuMessageTemplateString.extractParts(
            ImmutableSet.of("INTERPOLATION_1", "INTERPOLATION_2", "MISSING"));

    // The non-existent placeholder name "MISSING" should be missing from this list.
    // JsMessageVisitor will notice the name is missing from the resulting set of
    // seen placeholders and report an error, though we won't do that in this test.
    assertThat(extractedParts.extractedPlaceholderNames)
        .containsExactly("INTERPOLATION_1", "INTERPOLATION_2");
    final ImmutableList<Part> parts = extractedParts.extractedParts;

    assertThat(parts.get(0).getString())
        .isEqualTo(
            "{NUM_PEOPLE, plural, offset:1 \n=0 {I see {START_BOLD}no one at all{END_BOLD} in ");
    assertThat(parts.get(1).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_2");
    assertThat(parts.get(2).getString()).isEqualTo(".}\n=1 {I see ");
    assertThat(parts.get(3).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_1");
    assertThat(parts.get(4).getString()).isEqualTo(" in ");
    assertThat(parts.get(5).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_2");
    assertThat(parts.get(6).getString()).isEqualTo(".}\n=2 {I see ");
    assertThat(parts.get(7).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_1");
    assertThat(parts.get(8).getString()).isEqualTo(" and one other person in ");
    assertThat(parts.get(9).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_2");
    assertThat(parts.get(10).getString()).isEqualTo(".}\nother {I see ");
    assertThat(parts.get(11).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_1");
    assertThat(parts.get(12).getString()).isEqualTo(" and # other people in ");
    assertThat(parts.get(13).getCanonicalPlaceholderName()).isEqualTo("INTERPOLATION_2");
    assertThat(parts.get(14).getString()).isEqualTo(".}\n}");
  }

  @Test
  public void testIcuTemplate() {
    extractMessagesSafely(
        lines(
            "const MSG_ICU_EXAMPLE = declareIcuTemplate(",
            "    `{NUM_PEOPLE, plural, offset:1 ",
            "        =0 {I see no one at all in {INTERPOLATION_2}.}",
            "        =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}",
            "        =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}",
            "        other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}",
            "    }`,",
            "    {",
            "      description: 'ICU example message',",
            "      original_code: {",
            "        'INTERPOLATION_1': '{{getPerson()}}',",
            "        'INTERPOLATION_2': '{{getPlaceName()}}',",
            "      },",
            "      example: {",
            "        'INTERPOLATION_1': 'Jane Doe',",
            "        'INTERPOLATION_2': 'Paris, France',",
            "      }",
            "    });",
            ""));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(icuTemplateDefinitions).hasSize(1);
    IcuTemplateDefinition definition = icuTemplateDefinitions.get(0);

    final Node messageNode = definition.getMessageNode();
    assertNode(messageNode).isCall().hasFirstChildThat().isName("declareIcuTemplate");

    final Node templateTextNode = definition.getTemplateTextNode();
    assertNode(templateTextNode).hasToken(Token.TEMPLATELIT);

    JsMessage msg = definition.getMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_ICU_EXAMPLE");
    assertThat(msg.getDesc()).isEqualTo("ICU example message");
    assertThat(msg.asIcuMessageString())
        .isEqualTo(
            lines(
                "{NUM_PEOPLE, plural, offset:1 ",
                "        =0 {I see no one at all in {INTERPOLATION_2}.}",
                "        =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}",
                "        =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}",
                "        other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}",
                "    }"));
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testIcuTemplatePlaceholderTypo() {
    extractMessages(
        lines(
            "const MSG_ICU_EXAMPLE = declareIcuTemplate(",
            "    `{NUM_PEOPLE, plural, offset:1 ",
            "        =0 {I see no one at all in {INTERPOLATION_2}.}",
            "        =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}",
            "        =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}",
            "        other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}",
            "    }`,",
            "    {",
            "      description: 'ICU example message',",
            "      example: {",
            "        'INTERPOLATION_1': 'Jane Doe',",
            // This placeholder name doesn't match any placeholders in the message, so it should
            // be reported as an error
            "        'INTERPOLATION_TYPO_2': 'Paris, France',",
            "      }",
            "    });",
            ""));
    final ImmutableList<JSError> actualErrors = compiler.getErrors();
    assertThat(actualErrors)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(MESSAGE_TREE_MALFORMED);
    assertThat(actualErrors)
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. Unknown placeholder: INTERPOLATION_TYPO_2");
    assertThat(compiler.getWarnings()).isEmpty();
    // The malformed message is skipped.
    assertThat(messages).isEmpty();
  }

  @Test
  public void testJsMessageOnVar() {
    extractMessagesSafely("/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testJsMessageOnLet() {
    compilerOptions = new CompilerOptions();
    extractMessagesSafely("/** @desc Hello */ let MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testJsMessageOnConst() {
    compilerOptions = new CompilerOptions();
    extractMessagesSafely("/** @desc Hello */ const MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testJsMessagesWithSrcMap() throws Exception {
    SourceMapGeneratorV3 sourceMap = new SourceMapGeneratorV3();
    sourceMap.addMapping(
        "source1.html",
        null,
        new FilePosition(10, 0),
        new FilePosition(0, 0),
        new FilePosition(0, 100));
    sourceMap.addMapping(
        "source2.html",
        null,
        new FilePosition(10, 0),
        new FilePosition(1, 0),
        new FilePosition(1, 100));
    StringBuilder output = new StringBuilder();
    sourceMap.appendTo(output, "unused.js");

    compilerOptions = new CompilerOptions();
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    compilerOptions.inputSourceMaps =
        ImmutableMap.of(
            "testcode",
            new SourceMapInput(SourceFile.fromCode("example.srcmap", output.toString())));

    extractMessagesSafely(
        "/** @desc Hello */ var MSG_HELLO = goog.getMsg('a');\n"
            + "/** @desc Hi */ var MSG_HI = goog.getMsg('b');\n");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(2);

    JsMessage msg1 = messages.get(0);
    assertThat(msg1.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg1.getDesc()).isEqualTo("Hello");
    assertThat(msg1.getSourceName()).isEqualTo("source1.html:11");

    JsMessage msg2 = messages.get(1);
    assertThat(msg2.getKey()).isEqualTo("MSG_HI");
    assertThat(msg2.getDesc()).isEqualTo("Hi");
    assertThat(msg2.getSourceName()).isEqualTo("source2.html:11");
  }

  @Test
  public void testJsMessageOnProperty() {
    extractMessagesSafely("/** @desc a */ pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testJsMessageOnPublicField() {
    extractMessages(
        lines(
            "class Foo {",
            "  /** @desc overflow menu */",
            "  MSG_OVERFLOW_MENU = goog.getMsg('More options');",
            "}"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_OVERFLOW_MENU");
    assertThat(msg.asJsMessageString()).isEqualTo("More options");

    // Anonymous class
    extractMessages(
        lines(
            "foo(class {", "  /** @desc hi */", "  MSG_HELLO = goog.getMsg('Greetings');", "});"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(2);
    msg = messages.get(1);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.asJsMessageString()).isEqualTo("Greetings");
  }

  @Test
  public void testErrorOnPublicFields() {
    extractMessages(
        lines(
            "class Foo {", //
            "  /** @desc */",
            "  MSG_WITH_NO_RHS;",
            "}"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertOneError(MESSAGE_HAS_NO_VALUE);
  }

  @Test
  public void testErrorOnStaticField() {
    extractMessages(
        lines(
            "class Bar {", //
            "  /** @desc */",
            "  static MSG_STATIC_FIELD_WITH_NO_RHS;",
            "}"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertOneError(MESSAGE_HAS_NO_VALUE);
  }

  @Test
  public void testJsMessageOnPublicStaticField() {
    extractMessages(
        lines(
            "class Bar {",
            "  /** @desc menu */",
            "  static MSG_MENU = goog.getMsg('Options');",
            "}"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU");
    assertThat(msg.asJsMessageString()).isEqualTo("Options");

    extractMessages(
        lines(
            "let G = class {",
            "  /** @desc apples */",
            "  static MSG_FRUIT = goog.getMsg('Apples');",
            "}"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(2);
    msg = messages.get(1);
    assertThat(msg.getKey()).isEqualTo("MSG_FRUIT");
    assertThat(msg.asJsMessageString()).isEqualTo("Apples");
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
            "/**", " * @enum {number}", " */", "var MyEnum = {", "  MSG_ONE: 0", "};"));
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0)).hasType(MESSAGE_NOT_INITIALIZED_CORRECTLY);
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
    extractMessagesSafely(
        "pint.sub = {" + "/** @desc a */ MSG_MENU_MARK_AS_UNREAD: goog.getMsg('a')}");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testInvalidJsMessageOnObjLit() {
    extractMessages(
        "" + "pint.sub = {" + "  /** @desc a */ MSG_MENU_MARK_AS_UNREAD: undefined" + "}");
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0)).hasType(MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testJsMessageAliasOnObjLit() {
    extractMessagesSafely(
        ""
            + "pint.sub = {"
            + "  MSG_MENU_MARK_AS_UNREAD: another.namespace.MSG_MENU_MARK_AS_UNREAD"
            + "}");
  }

  @Test
  public void testMessageAliasedToObject() {
    extractMessagesSafely("a.b.MSG_FOO = MSG_FOO;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMsgPropertyAliasesMsgVariable_mismatchedMSGNameIsAllowed() {
    extractMessages("a.b.MSG_FOO_ALIAS = MSG_FOO;");
    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void testMsgPropertyAliasesMsgProperty_mismatchedMSGNameIsAllowed() {
    extractMessages("a.b.MSG_FOO_ALIAS = c.d.MSG_FOO;");
    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void testMessageAliasedToObject_nonMSGNameIsNotAllowed() {
    extractMessages("a.b.MSG_FOO_ALIAS = someVarName;");
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0)).hasType(MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testMessageExport_shortHand() {
    extractMessagesSafely("exports = {MSG_FOO};");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMessageExport_longHand() {
    extractMessagesSafely("exports = {MSG_FOO: MSG_FOO};");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMessageDefinedInExportsIsNotOrphaned() {
    extractMessagesSafely(
        ""
            + "exports = {"
            + "  /** @desc Description. */"
            + "  MSG_FOO: goog.getMsg('Foo'),"
            + "};");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_longhand() {
    extractMessagesSafely("({MSG_MENU_MARK_AS_UNREAD: MSG_MENU_MARK_AS_UNREAD} = x);");
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_MSGlonghand_allowed() {
    extractMessages("({MSG_FOO: MSG_FOO_ALIAS} = {MSG_FOO: goog.getMsg('Foo')});");

    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_shorthand() {
    extractMessagesSafely("({MSG_MENU_MARK_AS_UNREAD} = x);");
  }

  @Test
  public void testJsMessageOnRHSOfVar() {
    extractMessagesSafely("var MSG_MENU_MARK_AS_UNREAD = a.name.space.MSG_MENU_MARK_AS_UNREAD;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testOrphanedJsMessage() {
    extractMessages("goog.getMsg('a')");
    assertThat(messages).isEmpty();

    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
  }

  @Test
  public void testOrphanedIcuTemplate() {
    extractMessages(
        lines(
            "const {declareIcuTemplate} = goog.require('goog.i18n.messages');", //
            "",
            "declareIcuTemplate('a')"));
    assertThat(messages).isEmpty();

    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
  }

  @Test
  public void testMessageWithoutDescription() {
    extractMessagesSafely("var MSG_HELLO = goog.getMsg('a')");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");

    assertThat(compiler.getWarnings())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);
  }

  @Test
  public void testIncorrectMessageReporting() {
    extractMessages("var MSG_HELLO = goog.getMsg('a' + + 'b')");
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();

    JSError malformedTreeError = compiler.getErrors().get(0);
    assertError(malformedTreeError).hasType(MESSAGE_TREE_MALFORMED);
    assertThat(malformedTreeError.getDescription())
        .isEqualTo("Message parse tree malformed. literal string or concatenation expected");
  }

  @Test
  public void testTemplateLiteral() {
    compilerOptions = new CompilerOptions();

    extractMessages("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello`);");
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.asJsMessageString()).isEqualTo("hello");
  }

  @Test
  public void testTemplateLiteralWithSubstitution() {
    compilerOptions = new CompilerOptions();

    extractMessages("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello ${name}`);");
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();

    JSError malformedTreeError = compiler.getErrors().get(0);
    assertError(malformedTreeError).hasType(MESSAGE_TREE_MALFORMED);
    assertThat(malformedTreeError.getDescription())
        .isEqualTo(
            "Message parse tree malformed."
                + " Template literals with substitutions are not allowed.");
  }

  @Test
  public void testClosureMessageWithHelpPostfix() {
    extractMessagesSafely("/** @desc help text */\n" + "var MSG_FOO_HELP = goog.getMsg('Help!');");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_FOO_HELP");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.asJsMessageString()).isEqualTo("Help!");
  }

  @Test
  public void testClosureMessageWithoutGoogGetmsg() {

    extractMessages("var MSG_FOO_HELP = 'I am a bad message';");

    assertThat(messages).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0))
        .hasType(JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testAllowOneMSGtoAliasAnotherMSG() {

    // NOTE: tsickle code generation can end up creating new MSG_* variables that are temporary
    // aliases of existing ones that were defined correctly using goog.getMsg(). Don't complain
    // about them.
    extractMessages(
        lines(
            "/** @desc A foo message */",
            "var MSG_FOO = goog.getMsg('Foo message');",
            "var MSG_FOO_1 = MSG_FOO;",
            ""));

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_FOO");
    assertThat(msg.asJsMessageString()).isEqualTo("Foo message");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testDisallowOneMSGtoAliasNONMSG() {

    // NOTE: tsickle code generation can end up creating new MSG_* variables that are temporary
    // aliases of existing ones that were defined correctly using goog.getMsg(). Don't complain
    // about them.
    extractMessages(
        lines(
            "/** @desc A foo message */",
            "var mymsg = 'Foo message';",
            "var MSG_FOO_1 = mymsg;",
            ""));

    assertThat(messages).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0))
        .hasType(JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testClosureFormatParametizedFunction() {
    extractMessagesSafely(
        lines(
            "/** @desc help text */",
            "var MSG_SILLY = goog.getMsg('{$adjective} ' + 'message', ",
            "{'adjective': 'silly'});"));

    assertThat(messageDefinitions).hasSize(1);
    final JsMessageDefinition jsMessageDefinition = messageDefinitions.get(0);

    // `goog.getMsg(...)`
    final Node messageNode = jsMessageDefinition.getMessageNode();
    assertNode(messageNode).isCall().hasFirstChildThat().matchesQualifiedName("goog.getMsg");

    // `'{$adjective} ' + 'message'`
    final Node templateTextNode = jsMessageDefinition.getTemplateTextNode();
    assertNode(templateTextNode).hasToken(Token.ADD).hasFirstChildThat().isString("{$adjective} ");

    // `{'adjective': ...}`
    final Node placeholderValuesNode = jsMessageDefinition.getPlaceholderValuesNode();
    assertNode(placeholderValuesNode)
        .isObjectLit()
        .hasFirstChildThat()
        .hasToken(Token.STRING_KEY)
        .hasStringThat()
        .isEqualTo("adjective");

    // Map containing "adjective" -> string node containing "silly"
    final ImmutableMap<String, Node> placeholderValueMap =
        jsMessageDefinition.getPlaceholderValueMap();
    assertThat(placeholderValueMap).hasSize(1);
    assertNode(placeholderValueMap.get("adjective")).isString("silly");

    JsMessage msg = jsMessageDefinition.getMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.asJsMessageString()).isEqualTo("{$adjective} message");
  }

  @Test
  public void testHugeMessage() {
    extractMessagesSafely(
        "/**"
            + " * @desc A message with lots of stuff.\n"
            + " */"
            + "var MSG_HUGE = goog.getMsg("
            + "    '{$startLink_1}Google{$endLink}' +"
            + "    '{$startLink_2}blah{$endLink}{$boo}{$foo_001}{$boo}' +"
            + "    '{$foo_002}{$xxx_001}{$image}{$image_001}{$xxx_002}',"
            + "    {'startLink_1': '<a href=http://www.google.com/>',"
            + "     'endLink': '</a>',"
            + "     'startLink_2': '<a href=\"' + opt_data.url + '\">',"
            + "     'boo': opt_data.boo,"
            + "     'foo_001': opt_data.foo,"
            + "     'foo_002': opt_data.boo.foo,"
            + "     'xxx_001': opt_data.boo + opt_data.foo,"
            + "     'image': htmlTag7,"
            + "     'image_001': opt_data.image,"
            + "     'xxx_002': foo.callWithOnlyTopLevelKeys("
            + "         bogusFn, opt_data, null, 'bogusKey1',"
            + "         opt_data.moo, 'bogusKey2', param10)});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HUGE");
    assertThat(msg.getDesc()).isEqualTo("A message with lots of stuff.");
    assertThat(msg.asJsMessageString())
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
  public void testUnnamedIcuTemplate() {
    // Unlike `goog.getMsg()` we do require a description for anonymous ICU templates.
    // Requiring the description is less complicated, and there doesn't seem to be any reaon
    // not to require them.
    extractMessagesSafely(
        "var MSG_UNNAMED = declareIcuTemplate('Hullo', {description: 'description'});");

    assertThat(icuTemplateDefinitions).hasSize(1);
    JsMessage msg = icuTemplateDefinitions.get(0).getMessage();
    assertThat(msg.getDesc()).isEqualTo("description");
    assertThat(msg.getKey()).isEqualTo("MSG_16LJMYKCXT84X");
    assertThat(msg.getId()).isEqualTo("MSG_16LJMYKCXT84X");
  }

  @Test
  public void testMeaningGetsUsedAsIdIfTheresNoGenerator() {
    extractMessagesSafely(
        lines(
            "/**", //
            " * @desc some description",
            " * @meaning some meaning",
            " */",
            "var MSG_HULLO = goog.getMsg('Hullo');"));

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getDesc()).isEqualTo("some description");
    assertThat(msg.getKey()).isEqualTo("MSG_HULLO");
    assertThat(msg.getMeaning()).isEqualTo("some meaning");
    assertThat(msg.getId()).isEqualTo("some meaning");
  }

  @Test
  public void testEmptyTextMessage() {
    extractMessagesSafely("/** @desc text */ var MSG_FOO = goog.getMsg('');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(
            "Message value of MSG_FOO is just an empty string. Empty messages are forbidden.");
  }

  @Test
  public void testEmptyTextComplexMessage() {
    extractMessagesSafely(
        "/** @desc text */ var MSG_BAR = goog.getMsg(" + "'' + '' + ''     + ''\n+'');");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(
            "Message value of MSG_BAR is just an empty string. " + "Empty messages are forbidden.");
  }

  @Test
  public void testMsgVarWithoutAssignment() {
    extractMessages("var MSG_SILLY;");

    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(JsMessageVisitor.MESSAGE_HAS_NO_VALUE);
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

    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message MSG_SILLY_PROP has no value");
  }

  @Test
  public void testMsgVarWithIncorrectRightSide() {
    extractMessages("var MSG_SILLY = 0;");

    final ImmutableList<JSError> warnings = compiler.getWarnings();
    assertThat(warnings)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(MESSAGE_NOT_INITIALIZED_CORRECTLY);
    assertThat(warnings)
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(
            "Message must be initialized using a call to goog.getMsg or"
                + " goog.i18n.messages.declareIcuTemplate");
  }

  @Test
  public void testIncorrectMessage() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = {};");

    assertThat(messages).isEmpty();
    final ImmutableList<JSError> warnings = compiler.getWarnings();
    assertThat(warnings)
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(MESSAGE_NOT_INITIALIZED_CORRECTLY);
    assertThat(warnings)
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(
            "Message must be initialized using a call to goog.getMsg or"
                + " goog.i18n.messages.declareIcuTemplate");
  }

  @Test
  public void testUnrecognizedFunction() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = somefunc('a')");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(
            "Message parse tree malformed. Message must be initialized using a call to goog.getMsg"
                + " or declareIcuTemplate (from goog.i18n.messages).");
  }

  @Test
  public void testExtractPropertyMessage() {
    extractMessagesSafely(
        "/**"
            + " * @desc A message that demonstrates placeholders\n"
            + " */"
            + "a.b.MSG_SILLY = goog.getMsg(\n"
            + "    '{$adjective} ' + '{$someNoun}',\n"
            + "    {'adjective': adj, 'someNoun': noun});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.asJsMessageString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
  }

  @Test
  public void testExtractPropertyMessageInFunction() {
    extractMessagesSafely(
        ""
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
    assertThat(msg.asJsMessageString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
  }

  @Test
  public void testAlmostButNotExternalMessage() {
    extractMessagesSafely("/** @desc External */ var MSG_EXTERNAL = goog.getMsg('External');");
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
    JsMessageVisitor visitor = new DummyJsVisitor();

    assertThat(visitor.isMessageName("MSG_HELLO")).isTrue();
    assertThat(visitor.isMessageName("MSG_")).isTrue();
    assertThat(visitor.isMessageName("MSG_HELP")).isTrue();
    assertThat(visitor.isMessageName("MSG_FOO_HELP")).isTrue();

    assertThat(visitor.isMessageName("_FOO_HELP")).isFalse();
    assertThat(visitor.isMessageName("MSGFOOP")).isFalse();
  }

  @Test
  public void testUnexistedPlaceholders() {
    extractMessages("var MSG_FOO = goog.getMsg('{$foo}:', {});");

    assertThat(messages).isEmpty();
    ImmutableList<JSError> errors = compiler.getErrors();
    assertThat(errors).hasSize(1);
    JSError error = errors.get(0);
    assertThat(error.getType()).isEqualTo(MESSAGE_TREE_MALFORMED);
    assertThat(error.getDescription())
        .isEqualTo(
            "Message parse tree malformed. Unrecognized message " + "placeholder referenced: foo");
  }

  @Test
  public void testUnusedReferenesAreNotOK() {
    extractMessages("/** @desc AA */ " + "var MSG_FOO = goog.getMsg('lalala:', {foo:1});");
    assertThat(messages).isEmpty();
    ImmutableList<JSError> errors = compiler.getErrors();
    assertThat(errors).hasSize(1);
    JSError error = errors.get(0);
    assertThat(error.getType()).isEqualTo(MESSAGE_TREE_MALFORMED);
    assertThat(error.getDescription())
        .isEqualTo("Message parse tree malformed. Unused message placeholder: " + "foo");
  }

  @Test
  public void testDuplicatePlaceHoldersAreBad() {
    extractMessages("var MSG_FOO = goog.getMsg(" + "'{$foo}:', {'foo': 1, 'foo' : 2});");

    assertThat(messages).isEmpty();
    ImmutableList<JSError> errors = compiler.getErrors();
    assertThat(errors).hasSize(1);
    JSError error = errors.get(0);
    assertThat(error.getType()).isEqualTo(MESSAGE_TREE_MALFORMED);
    assertThat(error.getDescription())
        .isEqualTo("Message parse tree malformed. duplicate string key: foo");
  }

  @Test
  public void testDuplicatePlaceholderReferencesAreOk() {
    extractMessagesSafely("var MSG_FOO = goog.getMsg(" + "'{$foo}:, {$foo}', {'foo': 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.asJsMessageString()).isEqualTo("{$foo}:, {$foo}");
  }

  @Test
  public void testCamelcasePlaceholderNamesAreOk() {
    extractMessagesSafely(
        "var MSG_WITH_CAMELCASE = goog.getMsg("
            + "'Slide {$slideNumber}:', {'slideNumber': opt_index + 1});");

    assertThat(messages).hasSize(1);
    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_WITH_CAMELCASE");
    assertThat(msg.asJsMessageString()).isEqualTo("Slide {$slideNumber}:");
    ImmutableList<Part> parts = msg.getParts();
    assertThat(parts).hasSize(3);
    assertThat(parts.get(1).getJsPlaceholderName()).isEqualTo("slideNumber");
    assertThat(parts.get(1).getCanonicalPlaceholderName()).isEqualTo("SLIDE_NUMBER");
  }

  @Test
  public void testNonCamelcasePlaceholderNamesAreNotOkInMsgText() {
    extractMessages(
        lines(
            "var MSG_WITH_CAMELCASE = goog.getMsg(",
            "    'Slide {$SLIDE_NUMBER}:',",
            "    {'slideNumber': opt_index + 1});"));

    assertThat(messages).isEmpty();
    ImmutableList<JSError> errors = compiler.getErrors();
    assertThat(errors).hasSize(1);
    JSError error = errors.get(0);
    assertThat(error.getType()).isEqualTo(MESSAGE_TREE_MALFORMED);
    assertThat(error.getDescription())
        .isEqualTo(
            "Message parse tree malformed. Placeholder name not in lowerCamelCase: SLIDE_NUMBER");
  }

  @Test
  public void testNonCamelcasePlaceholderNamesAreNotOkInPlaceholderObject() {
    extractMessages(
        lines(
            "var MSG_WITH_CAMELCASE = goog.getMsg(",
            "    'Slide {$slideNumber}:',",
            "    {'SLIDE_NUMBER': opt_index + 1});"));

    assertThat(messages).isEmpty();
    ImmutableList<JSError> errors = compiler.getErrors();
    assertThat(errors).hasSize(1);
    JSError error = errors.get(0);
    assertThat(error.getType()).isEqualTo(JsMessageVisitor.MESSAGE_TREE_MALFORMED);
    assertThat(error.getDescription())
        .isEqualTo(
            "Message parse tree malformed. Unrecognized message placeholder referenced:"
                + " slideNumber");
  }

  @Test
  public void testUnquotedPlaceholdersAreOk() {
    extractMessagesSafely(
        "/** @desc Hello */ " + "var MSG_FOO = goog.getMsg('foo {$unquoted}:', {unquoted: 12});");

    assertThat(messages).hasSize(1);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testDuplicateMessageError() {
    extractMessages(
        "(function () {/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')})"
            + "(function () {/** @desc Hello2 */ var MSG_HELLO = goog.getMsg('a')})");

    assertThat(compiler.getWarnings()).isEmpty();
    assertOneError(JsMessageVisitor.MESSAGE_DUPLICATE_KEY);
  }

  @Test
  public void testNoDuplicateErrorOnExternMessage() {
    extractMessagesSafely(
        "(function () {/** @desc Hello */ "
            + "var MSG_EXTERNAL_2 = goog.getMsg('a')})"
            + "(function () {/** @desc Hello2 */ "
            + "var MSG_EXTERNAL_2 = goog.getMsg('a')})");
  }

  @Test
  public void testUsingMsgPrefixWithFallback() {
    extractMessages(
        "function f() {\n"
            + "/** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');\n"
            + "/** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');\n"
            + "var x = goog.getMsgWithFallback(\n"
            + "    MSG_UNNAMED_1, MSG_UNNAMED_2);\n"
            + "}\n");
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
  public void testUsingMsgPrefixWithFallback_duplicateUnnamedKeys_rename() {
    renameMessages = true;
    extractMessages(
        lines(
            "function f() {",
            "  /** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');",
            "  /** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');",
            "  var x = goog.getMsgWithFallback(",
            "      MSG_UNNAMED_1, MSG_UNNAMED_2);",
            "}",
            "function g() {",
            "  /** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');",
            "  /** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');",
            "  var x = goog.getMsgWithFallback(",
            "      MSG_UNNAMED_1, MSG_UNNAMED_2);",
            "}"));
    assertNoErrors();
  }

  @Test
  public void testUsingMsgPrefixWithFallback_module() {
    extractMessages(
        LINE_JOINER.join(
            "/** @desc Hello */ var MSG_A = goog.getMsg('hello');",
            "/** @desc Hello */ var MSG_B = goog.getMsg('hello!');",
            "var x = goog.getMsgWithFallback(messages.MSG_A, MSG_B);"));
    assertNoErrors();
  }

  @Test
  public void testUsingMsgPrefixWithFallback_moduleRenamed() {
    extractMessages(
        LINE_JOINER.join(
            "/** @desc Hello */ var MSG_A = goog.getMsg('hello');",
            "/** @desc Hello */ var MSG_B = goog.getMsg('hello!');",
            "var x = goog.getMsgWithFallback(module$exports$messages$MSG_A, MSG_B);"));
    assertNoErrors();
  }

  @Test
  public void testErrorWhenUsingMsgPrefixWithFallback() {
    extractMessages(
        "/** @desc Hello */ var MSG_HELLO_1 = goog.getMsg('hello');\n"
            + "/** @desc Hello */ var MSG_HELLO_2 = goog.getMsg('hello');\n"
            + "/** @desc Hello */ "
            + "var MSG_HELLO_3 = goog.getMsgWithFallback(MSG_HELLO_1, MSG_HELLO_2);");
    assertOneError(MESSAGE_TREE_MALFORMED);
  }

  @Test
  public void testRenamedMessages_var() {
    renameMessages = true;

    extractMessagesSafely("/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
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

  @Test
  public void testGetMsgWithOptions() {
    extractMessagesSafely(
        lines(
            "/** @desc Hello */",
            "var MSG_HELLO =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        {",
            "          'name': getName(),",
            "        },",
            "        {",
            "          html: false,",
            "          unescapeHtmlEntities: true,",
            "          example: {",
            "            'name': 'George',",
            "          },",
            "          original_code: {",
            "            'name': 'getName()',",
            "          },",
            "        })"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    // MSG_HELLO =
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    // * @desc Hello
    assertThat(msg.getDesc()).isEqualTo("Hello");

    JsMessageDefinition msgDefinition = messageDefinitions.get(0);

    // goog.getMsg(...)
    final Node callNode = msgDefinition.getMessageNode();
    assertNode(callNode).isCall().hasFirstChildThat().matchesQualifiedName("goog.getMsg");

    assertNode(msgDefinition.getTemplateTextNode()).isString("Hello, {$name}");

    // `{ 'name': getName() }`
    final Node placeholderValuesNode = msgDefinition.getPlaceholderValuesNode();
    assertNode(placeholderValuesNode).isObjectLit();
    assertThat(callNode.getChildAtIndex(2)).isSameInstanceAs(placeholderValuesNode);

    // placeholder name 'name' maps to the `getName()` call in the values map
    final ImmutableMap<String, Node> placeholderValueMap = msgDefinition.getPlaceholderValueMap();
    assertThat(placeholderValueMap.keySet()).containsExactly("name");
    final Node nameValueNode = placeholderValueMap.get("name");
    assertNode(nameValueNode).isCall().hasOneChildThat().isName("getName");
    assertThat(nameValueNode.getGrandparent()).isSameInstanceAs(placeholderValuesNode);

    // `html: false`
    assertThat(msgDefinition.shouldEscapeLessThan()).isFalse();
    // `unescapeHtmlEntities: true`
    assertThat(msgDefinition.shouldUnescapeHtmlEntities()).isTrue();

    // `example: { 'name': 'George' }`
    assertThat(msg.getPlaceholderNameToExampleMap()).containsExactly("name", "George");
    // `original_code: {'name': 'getName()' }`
    assertThat(msg.getPlaceholderNameToOriginalCodeMap()).containsExactly("name", "getName()");
  }

  @Test
  public void testGoogGetMsgWithNoArgs() {
    extractMessages(
        lines(
            "/** @desc something */",
            "const MSG_X = goog.getMsg();", // no arguments
            ""));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. Message string literal expected");
  }

  @Test
  public void testGoogGetMsgWithBadValuesArg() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        getName());", // should be an object literal
            ""));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. object literal expected");
  }

  @Test
  public void testGoogGetMsgWithBadValuesKey() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        {",
            "          [name]: getName()", // a computed key is not allowed
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. string key expected");
  }

  @Test
  public void testGoogGetMsgWithBadOptionsArg() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        options);", // options bag must be an object literal
            ""));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. object literal expected");
  }

  @Test
  public void testGoogGetMsgWithComputedKeyInOptions() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          [options]: true", // option names cannot be computed keys
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. string key expected");
  }

  @Test
  public void testGoogGetMsgWithUnknownOption() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          unknownOption: true", // not a valid option name
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. Unknown option: unknownOption");
  }

  @Test
  public void testGoogGetMsgWithInvalidBooleanOption() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          html: 'true'", // boolean option value must be a boolean literal
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. html: Literal true or false expected");
  }

  @Test
  public void testGoogGetMsgWithOriginalCodeForInvalidExample() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          example: 'name: something'", // not an object literal
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. object literal expected");
  }

  @Test
  public void testGoogGetMsgWithInvalidOriginalCodeValue() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          original_code: {",
            "            'name': getName()", // value is not a string
            "          }",
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. literal string or concatenation expected");
  }

  @Test
  public void testGoogGetMsgWithOriginalCodeForUnknownPlaceholder() {
    extractMessages(
        lines(
            "/** @desc something */", //
            "const MSG_X =",
            "    goog.getMsg(",
            "        'Hello, {$name}',",
            "        { 'name': getName() },",
            "        {",
            "          original_code: {",
            "            'unknownPlaceholder': 'something'", // not a valid placeholder name
            "          }",
            "        });"));
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly("Message parse tree malformed. Unknown placeholder: unknownPlaceholder");
  }

  @Test
  public void testGetMsgWithGoogScope() {
    extractMessagesSafely(
        lines(
            "/** @desc Suggestion Code found outside of <head> tag. */",
            "var $jscomp$scope$12345$0$MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG =",
            "goog.getMsg('Code should be added to <body> tag.');"));
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).hasSize(1);

    JsMessage msg = messages.get(0);
    assertThat(msg.getId()).isEqualTo("MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG");
    assertThat(msg.getKey())
        .isEqualTo("$jscomp$scope$12345$0$MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG");
  }

  private void assertNoErrors() {
    assertThat(compiler.getErrors()).isEmpty();
  }

  private void assertOneError(DiagnosticType type) {
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(type);
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
      super(compiler, null);
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      messages.add(definition.getMessage());
      messageDefinitions.add(definition);
    }

    @Override
    protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
      icuTemplateDefinitions.add(definition);
      messages.add(definition.getMessage());
    }
  }

  private static class DummyJsVisitor extends JsMessageVisitor {

    private DummyJsVisitor() {
      super(null, null);
    }

    @Override
    protected void processJsMessageDefinition(JsMessageDefinition definition) {
      // no-op
    }

    @Override
    protected void processIcuTemplateDefinition(IcuTemplateDefinition definition) {
      // no-op
    }
  }
}
