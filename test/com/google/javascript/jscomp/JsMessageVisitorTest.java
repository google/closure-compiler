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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.JsMessage.Part;
import com.google.javascript.jscomp.JsMessageVisitor.ExtractedIcuTemplateParts;
import com.google.javascript.jscomp.JsMessageVisitor.IcuMessageTemplateString;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link JsMessageVisitor}. */
@RunWith(JUnit4.class)
public final class JsMessageVisitorTest {
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
            // Some placeholders are ignored, because there's no additional information for them
            // (original code or example text). There's no need to generate placeholder parts for
            // those.
            """
            {NUM_PEOPLE, plural, offset:1
            =0 {I see {START_BOLD}no one at all{END_BOLD} in {INTERPOLATION_2}.}
            =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}
            =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}
            other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}
            }
            """);
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
            "{NUM_PEOPLE, plural, offset:1\n=0 {I see {START_BOLD}no one at all{END_BOLD} in ");
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
    assertThat(parts.get(14).getString()).isEqualTo(".}\n}\n");
  }

  @Test
  public void testIcuTemplate() {
    extractMessagesSafely(
        """
        const MSG_ICU_EXAMPLE = declareIcuTemplate(
            `{NUM_PEOPLE, plural, offset:1
                =0 {I see no one at all in {INTERPOLATION_2}.}
                =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}
                =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}
                other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}
            }`,
            {
              description: 'ICU example message',
              original_code: {
                'INTERPOLATION_1': '{{getPerson()}}',
                'INTERPOLATION_2': '{{getPlaceName()}}',
              },
              example: {
                'INTERPOLATION_1': 'Jane Doe',
                'INTERPOLATION_2': 'Paris, France',
              }
            });
        """);
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
            """
            {NUM_PEOPLE, plural, offset:1
                    =0 {I see no one at all in {INTERPOLATION_2}.}
                    =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}
                    =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}
                    other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}
                }\
            """);
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testIcuTemplatePlaceholderTypo() {
    extractMessages(
        """
        const MSG_ICU_EXAMPLE = declareIcuTemplate(
            `{NUM_PEOPLE, plural, offset:1
                =0 {I see no one at all in {INTERPOLATION_2}.}
                =1 {I see {INTERPOLATION_1} in {INTERPOLATION_2}.}
                =2 {I see {INTERPOLATION_1} and one other person in {INTERPOLATION_2}.}
                other {I see {INTERPOLATION_1} and # other people in {INTERPOLATION_2}.}
            }`,
            {
              description: 'ICU example message',
              example: {
                'INTERPOLATION_1': 'Jane Doe',
        // This placeholder name doesn't match any placeholders in the message, so it should
        // be reported as an error
                'INTERPOLATION_TYPO_2': 'Paris, France',
              }
            });
        """);
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Unknown placeholder: INTERPOLATION_TYPO_2");
    assertThat(compiler.getWarnings()).isEmpty();
    // The malformed message is skipped.
    assertThat(messages).isEmpty();
  }

  @Test
  public void testIcuTemplatePlaceholderUpperSnakeCase() {
    // For ICU templates you must always use UPPER_SNAKE_CASE for your placeholder names.
    // This matches the way the placeholder names are formatted in the XMB/XTB files.
    // Note that `goog.getMsg()` requires lowerCamelCase for placeholder names, but
    // actually converts them to UPPER_SNAKE_CASE when storing them in XMB and must convert
    // the UPPER_SNAKE_CASE to lowerCamelCase when reading from XTB files. We want to avoid
    // this needless complication for `declareIcuTemplate()` message declarations.
    extractMessagesSafely(
        """
        const MSG_ICU_EXAMPLE = declareIcuTemplate(
            'Email: {USER_EMAIL}',
            {
              description: 'Labeled email address',
              example: {
                'USER_EMAIL': 'jane@doe.com',
              }
            });
        """);
    JsMessage jsMessage = assertOneMessage();
    assertThat(jsMessage.canonicalPlaceholderNames()).containsExactly("USER_EMAIL");
  }

  @Test
  public void testIcuTemplatePlaceholderLowerCamelCase() {
    // For ICU templates you must always use UPPER_SNAKE_CASE for your placeholder names.
    // This matches the way the placeholder names are formatted in the XMB/XTB files.
    // Note that `goog.getMsg()` requires lowerCamelCase for placeholder names, but
    // actually converts them to UPPER_SNAKE_CASE when storing them in XMB and must convert
    // the UPPER_SNAKE_CASE to lowerCamelCase when reading from XTB files. We want to avoid
    // this needless complication for `declareIcuTemplate()` message declarations.
    extractMessages(
        """
        const MSG_ICU_EXAMPLE = declareIcuTemplate(
            'Email: {userEmail}', // should be USER_EMAIL
            {
              description: 'Labeled email address',
              example: {
                'userEmail': 'jane@doe.com', // should be USER_EMAIL
              }
            });
        """);
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Placeholder not in UPPER_SNAKE_CASE: userEmail");
    assertThat(compiler.getWarnings()).isEmpty();
    // The malformed message is skipped.
    assertThat(messages).isEmpty();
  }

  @Test
  public void testJsMessageOnVar() {
    extractMessagesSafely("/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testJsMessageOnLet() {
    compilerOptions = new CompilerOptions();
    extractMessagesSafely("/** @desc Hello */ let MSG_HELLO = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testJsMessageOnConst() {
    compilerOptions = new CompilerOptions();
    extractMessagesSafely("/** @desc Hello */ const MSG_HELLO = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
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
    compilerOptions.setInputSourceMaps(
        ImmutableMap.of(
            "testcode",
            new SourceMapInput(SourceFile.fromCode("example.srcmap", output.toString()))));

    extractMessagesSafely(
        """
        /** @desc Hello */ var MSG_HELLO = goog.getMsg('a');
        /** @desc Hi */ var MSG_HI = goog.getMsg('b');
        """);
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

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testJsMessageOnPublicField() {
    extractMessagesSafely(
        """
        class Foo {
          /** @desc overflow menu */
          MSG_OVERFLOW_MENU = goog.getMsg('More options');
        }
        """);
    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_OVERFLOW_MENU");
    assertThat(msg.asJsMessageString()).isEqualTo("More options");

    // Anonymous class
    extractMessagesSafely(
        """
        foo(class {
          /** @desc hi */
          MSG_HELLO = goog.getMsg('Greetings');
        });
        """);
    assertThat(messages).hasSize(2);
    msg = messages.get(1);
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.asJsMessageString()).isEqualTo("Greetings");
  }

  @Test
  public void testJsMessageOnPublicField_directAlias() {
    extractMessagesSafely(
        """
        /** @desc Something */
        const MSG_SOMETHING = goog.getMsg('Something');
        class Foo {
          MSG_SOMETHING = MSG_SOMETHING;
        }
        """);
    assertThat(assertOneMessage().getKey()).isEqualTo("MSG_SOMETHING");
  }

  // Note: This may be undesirable but represents the current behavior.
  // Ideally the assignment to this MSG in the constructor should be sufficient to avoid the error.
  @Test
  public void testJsMessageOnPublicField_indirectAliasPresentlyErrors() {
    extractMessages(
        """
        /** @desc Anything */
        const MSG_ANYTHING = goog.getMsg('Anything');
        class Baz {
          MSG_ANYTHING;
          constructor() {
            this.MSG_ANYTHING = MSG_ANYTHING;
          }
        }
        """);
    assertOneError(MESSAGE_HAS_NO_VALUE);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testErrorOnPublicFields() {
    extractMessages(
        """
        class Foo {
          /** @desc */
          MSG_WITH_NO_RHS;
        }
        """);
    assertOneError(MESSAGE_HAS_NO_VALUE);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testErrorOnStaticField() {
    extractMessages(
        """
        class Bar {
          /** @desc */
          static MSG_STATIC_FIELD_WITH_NO_RHS;
        }
        """);
    assertOneError(MESSAGE_HAS_NO_VALUE);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testJsMessageOnPublicStaticField() {
    extractMessagesSafely(
        """
        class Bar {
          /** @desc menu */
          static MSG_MENU = goog.getMsg('Options');
        }
        """);
    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_MENU");
    assertThat(msg.asJsMessageString()).isEqualTo("Options");

    extractMessagesSafely(
        """
        let G = class {
          /** @desc apples */
          static MSG_FRUIT = goog.getMsg('Apples');
        }
        """);
    assertThat(messages).hasSize(2);
    msg = messages.get(1);
    assertThat(msg.getKey()).isEqualTo("MSG_FRUIT");
    assertThat(msg.asJsMessageString()).isEqualTo("Apples");
  }

  @Test
  public void testStaticInheritance() {
    extractMessagesSafely(
        """
        /** @desc a */
        foo.bar.BaseClass.MSG_MENU = goog.getMsg('hi');
        /**
         * @desc a
         * @suppress {visibility}
         */
        foo.bar.Subclass.MSG_MENU = foo.bar.BaseClass.MSG_MENU;
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_MENU");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testMsgInEnum() {
    extractMessages(
        """
        /**
         * @enum {number}
         */
        var MyEnum = {
          MSG_ONE: 0
        };
        """);
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testMsgInEnumWithSuppression() {
    extractMessagesSafely(
        """
        /** @fileoverview
         * @suppress {messageConventions}
         */

        /**
         * @enum {number}
         */
        var MyEnum = {
          MSG_ONE: 0
        };
        """);
  }

  @Test
  public void testJsMessageOnObjLit() {
    extractMessagesSafely(
        """
        pint.sub = {
        /** @desc a */ MSG_MENU_MARK_AS_UNREAD: goog.getMsg('a')}
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testInvalidJsMessageOnObjLit() {
    extractMessages(
        """
        pint.sub = {
          /** @desc a */ MSG_MENU_MARK_AS_UNREAD: undefined
        }
        """);
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testJsMessageAliasOnObjLit() {
    extractMessagesSafely(
        """
        pint.sub = {
          MSG_MENU_MARK_AS_UNREAD: another.namespace.MSG_MENU_MARK_AS_UNREAD
        }
        """);
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMessageAliasedToObject() {
    extractMessagesSafely("a.b.MSG_FOO = MSG_FOO;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMsgPropertyAliasesMsgVariable_mismatchedMSGNameIsAllowed() {
    extractMessagesSafely("a.b.MSG_FOO_ALIAS = MSG_FOO;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMsgPropertyAliasesMsgProperty_mismatchedMSGNameIsAllowed() {
    extractMessagesSafely("a.b.MSG_FOO_ALIAS = c.d.MSG_FOO;");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testMessageAliasedToObject_nonMSGNameIsNotAllowed() {
    extractMessages("a.b.MSG_FOO_ALIAS = someVarName;");
    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(MESSAGE_NOT_INITIALIZED_CORRECTLY);
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
        """
        exports = {
          /** @desc Description. */
          MSG_FOO: goog.getMsg('Foo'),
        };
        """);
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_longhand() {
    extractMessagesSafely("({MSG_MENU_MARK_AS_UNREAD: MSG_MENU_MARK_AS_UNREAD} = x);");
    assertThat(messages).isEmpty();
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_MSGlonghand_allowed() {
    extractMessagesSafely(
        "({MSG_FOO: MSG_FOO_ALIAS} = {/** @desc Foo */ MSG_FOO: goog.getMsg('Foo')});");
    assertOneMessage();
  }

  @Test
  public void testJsMessageAlias_fromObjectDestrucuturing_shorthand() {
    extractMessagesSafely("({MSG_MENU_MARK_AS_UNREAD} = x);");
    assertThat(messages).isEmpty();
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

    assertOneError(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testOrphanedIcuTemplate() {
    extractMessages(
        """
        const {declareIcuTemplate} = goog.require('goog.i18n.messages');

        declareIcuTemplate('a')
        """);
    assertThat(messages).isEmpty();

    assertOneError(JsMessageVisitor.MESSAGE_NODE_IS_ORPHANED);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testMessageWithoutDescription() {
    extractMessages("var MSG_HELLO = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");

    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);
  }

  @Test
  public void testIncorrectMessageReporting() {
    extractMessages("var MSG_HELLO = goog.getMsg('a' + + 'b')");
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. literal string or concatenation expected");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();
  }

  @Test
  public void testTemplateLiteral() {
    compilerOptions = new CompilerOptions();

    extractMessagesSafely("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello`);");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.asJsMessageString()).isEqualTo("hello");
  }

  @Test
  public void testTemplateLiteralWithSubstitution() {
    compilerOptions = new CompilerOptions();

    extractMessages("/** @desc Hello */ var MSG_HELLO = goog.getMsg(`hello ${name}`);");
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Template literals with substitutions are not allowed.");
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(messages).isEmpty();
  }

  @Test
  public void testClosureMessageWithHelpPostfix() {
    extractMessagesSafely(
        """
        /** @desc help text */
        var MSG_FOO_HELP = goog.getMsg('Help!');
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_FOO_HELP");
    assertThat(msg.getDesc()).isEqualTo("help text");
    assertThat(msg.asJsMessageString()).isEqualTo("Help!");
  }

  @Test
  public void testClosureMessageWithoutGoogGetmsg() {
    extractMessages("var MSG_FOO_HELP = 'I am a bad message';");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testAllowOneMSGtoAliasAnotherMSG() {
    // NOTE: tsickle code generation can end up creating new MSG_* variables that are temporary
    // aliases of existing ones that were defined correctly using goog.getMsg(). Don't complain
    // about them.
    extractMessagesSafely(
        """
        /** @desc A foo message */
        var MSG_FOO = goog.getMsg('Foo message');
        var MSG_FOO_1 = MSG_FOO;
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_FOO");
    assertThat(msg.asJsMessageString()).isEqualTo("Foo message");
  }

  @Test
  public void testDisallowOneMSGtoAliasNONMSG() {
    // NOTE: tsickle code generation can end up creating new MSG_* variables that are temporary
    // aliases of existing ones that were defined correctly using goog.getMsg(). Don't complain
    // about them.
    extractMessages(
        """
        /** @desc A foo message */
        var mymsg = 'Foo message';
        var MSG_FOO_1 = mymsg;
        """);

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(JsMessageVisitor.MESSAGE_NOT_INITIALIZED_CORRECTLY);
  }

  @Test
  public void testClosureFormatParametizedFunction() {
    extractMessagesSafely(
        """
        /** @desc help text */
        var MSG_SILLY = goog.getMsg('{$adjective} ' + 'message',
        {'adjective': 'silly'});
        """);

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
        """
        /**
         * @desc A message with lots of stuff.
         */
        var MSG_HUGE = goog.getMsg(
            '{$startLink_1}Google{$endLink}' +
            '{$startLink_2}blah{$endLink}{$boo}{$foo_001}{$boo}' +
            '{$foo_002}{$xxx_001}{$image}{$image_001}{$xxx_002}',
            {'startLink_1': '<a href=http://www.google.com/>',
             'endLink': '</a>',
             'startLink_2': '<a href="' + opt_data.url + '">',
             'boo': opt_data.boo,
             'foo_001': opt_data.foo,
             'foo_002': opt_data.boo.foo,
             'xxx_001': opt_data.boo + opt_data.foo,
             'image': htmlTag7,
             'image_001': opt_data.image,
             'xxx_002': foo.callWithOnlyTopLevelKeys(
                 bogusFn, opt_data, null, 'bogusKey1',
                 opt_data.moo, 'bogusKey2', param10)});
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HUGE");
    assertThat(msg.getDesc()).isEqualTo("A message with lots of stuff.");
    assertThat(msg.asJsMessageString())
        .isEqualTo(
            """
            {$startLink_1}Google{$endLink}{$startLink_2}blah{$endLink}\
            {$boo}{$foo_001}{$boo}{$foo_002}{$xxx_001}{$image}\
            {$image_001}{$xxx_002}\
            """);
  }

  @Test
  public void testUnnamedGoogleMessage() {
    extractMessages("var MSG_UNNAMED = goog.getMsg('Hullo');");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getDesc()).isNull();
    assertThat(msg.getKey()).isEqualTo("MSG_16LJMYKCXT84X");
    assertThat(msg.getId()).isEqualTo("MSG_16LJMYKCXT84X");
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(JsMessageVisitor.MESSAGE_HAS_NO_DESCRIPTION);
  }

  @Test
  public void testUnnamedIcuTemplate() {
    // Unlike `goog.getMsg()` we do require a description for anonymous ICU templates.
    // Requiring the description is less complicated, and there doesn't seem to be any reason
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
        """
        /**
         * @desc some description
         * @meaning some meaning
         */
        var MSG_HULLO = goog.getMsg('Hullo');
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getDesc()).isEqualTo("some description");
    assertThat(msg.getKey()).isEqualTo("MSG_HULLO");
    assertThat(msg.getMeaning()).isEqualTo("some meaning");
    assertThat(msg.getId()).isEqualTo("some meaning");
  }

  @Test
  public void testEmptyTextMessage() {
    extractMessages("/** @desc text */ var MSG_FOO = goog.getMsg('');");

    assertOneMessage();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(
        "Message value of MSG_FOO is just an empty string. Empty messages are forbidden.");
  }

  @Test
  public void testEmptyTextComplexMessage() {
    extractMessages(
        """
        /** @desc text */ var MSG_BAR = goog.getMsg(
        '' + '' + ''     + '' +'');
        """);

    assertOneMessage();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(
        "Message value of MSG_BAR is just an empty string. Empty messages are forbidden.");
  }

  @Test
  public void testMsgVarWithoutAssignment() {
    extractMessages("var MSG_SILLY;");

    assertOneError(JsMessageVisitor.MESSAGE_HAS_NO_VALUE);
    assertThat(compiler.getWarnings()).isEmpty();
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

    assertOneError("Message MSG_SILLY_PROP has no value");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testMsgVarWithIncorrectRightSide() {
    extractMessages("var MSG_SILLY = 0;");

    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(
        MESSAGE_NOT_INITIALIZED_CORRECTLY,
        "Message must be initialized using a call to goog.getMsg or"
            + " goog.i18n.messages.declareIcuTemplate");
  }

  @Test
  public void testIncorrectMessage() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = {};");

    assertThat(messages).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
    assertOneWarning(
        MESSAGE_NOT_INITIALIZED_CORRECTLY,
        "Message must be initialized using a call to goog.getMsg or"
            + " goog.i18n.messages.declareIcuTemplate");
  }

  @Test
  public void testUnrecognizedFunction() {
    extractMessages("DP_DatePicker.MSG_DATE_SELECTION = somefunc('a')");

    assertThat(messages).isEmpty();
    assertOneError(
        "Message parse tree malformed. Message must be initialized using a call to goog.getMsg"
            + " or declareIcuTemplate (from goog.i18n.messages).");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testExtractPropertyMessage() {
    extractMessagesSafely(
        """
        /**
         * @desc A message that demonstrates placeholders
         */
        a.b.MSG_SILLY = goog.getMsg(
            '{$adjective} ' + '{$someNoun}',
            {'adjective': adj, 'someNoun': noun});
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.asJsMessageString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
  }

  @Test
  public void testExtractPropertyMessageInFunction() {
    extractMessagesSafely(
        """
        function f() {
          /**
           * @desc A message that demonstrates placeholders
           */
          a.b.MSG_SILLY = goog.getMsg(
              '{$adjective} ' + '{$someNoun}',
              {'adjective': adj, 'someNoun': noun});
        }
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_SILLY");
    assertThat(msg.asJsMessageString()).isEqualTo("{$adjective} {$someNoun}");
    assertThat(msg.getDesc()).isEqualTo("A message that demonstrates placeholders");
  }

  @Test
  public void testAlmostButNotExternalMessage() {
    extractMessagesSafely("/** @desc External */ var MSG_EXTERNAL = goog.getMsg('External');");

    JsMessage msg = assertOneMessage();
    assertThat(msg.isExternal()).isFalse();
    assertThat(msg.getKey()).isEqualTo("MSG_EXTERNAL");
  }

  @Test
  public void testExternalMessage() {
    extractMessagesSafely("var MSG_EXTERNAL_111 = goog.getMsg('Hello World');");

    JsMessage msg = assertOneMessage();
    assertThat(msg.isExternal()).isTrue();
    assertThat(msg.getId()).isEqualTo("111");
  }

  @Test
  public void testExternalMessage_customSuffix() {
    extractMessagesSafely("var MSG_EXTERNAL_111$$1 = goog.getMsg('Hello World');");

    JsMessage msg = assertOneMessage();
    assertThat(msg.isExternal()).isTrue();
    assertThat(msg.getId()).isEqualTo("111");
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
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Unrecognized message placeholder referenced: foo");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testUnusedReferenesAreNotOK() {
    extractMessages(
        """
        /** @desc AA */
        var MSG_FOO = goog.getMsg('lalala:', {foo:1});
        """);
    assertThat(messages).isEmpty();
    assertOneError(
        MESSAGE_TREE_MALFORMED, "Message parse tree malformed. Unused message placeholder: foo");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testDuplicatePlaceHoldersAreBad() {
    extractMessages(
        """
        var MSG_FOO = goog.getMsg(
        '{$foo}:', {'foo': 1, 'foo' : 2});
        """);

    assertThat(messages).isEmpty();
    assertOneError(
        MESSAGE_TREE_MALFORMED, "Message parse tree malformed. duplicate string key: foo");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testDuplicatePlaceholderReferencesAreOk() {
    extractMessagesSafely(
        """
        /** @desc Sample description */
        var MSG_FOO = goog.getMsg('{$foo}:, {$foo}', {'foo': 1});
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.asJsMessageString()).isEqualTo("{$foo}:, {$foo}");
  }

  @Test
  public void testCamelcasePlaceholderNamesAreOk() {
    extractMessagesSafely(
"""
/** @desc Hello */
var MSG_WITH_CAMELCASE = goog.getMsg('Slide {$slideNumber}:', {'slideNumber': opt_index + 1});
""");

    JsMessage msg = assertOneMessage();
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
        """
        var MSG_WITH_CAMELCASE = goog.getMsg(
            'Slide {$SLIDE_NUMBER}:',
            {'slideNumber': opt_index + 1});
        """);

    assertThat(messages).isEmpty();
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Placeholder name not in lowerCamelCase: SLIDE_NUMBER");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testNonCamelcasePlaceholderNamesAreNotOkInPlaceholderObject() {
    extractMessages(
        """
        var MSG_WITH_CAMELCASE = goog.getMsg(
            'Slide {$slideNumber}:',
            {'SLIDE_NUMBER': opt_index + 1});
        """);

    assertThat(messages).isEmpty();
    assertOneError(
        MESSAGE_TREE_MALFORMED,
        "Message parse tree malformed. Unrecognized message placeholder referenced: slideNumber");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testUnquotedPlaceholdersAreOk() {
    extractMessagesSafely(
        """
        /** @desc Hello */
        var MSG_FOO = goog.getMsg('foo {$unquoted}:', {unquoted: 12});
        """);

    assertOneMessage();
  }

  @Test
  public void testDuplicateMessageError() {
    extractMessages(
        """
        (function () {/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')})
        (function () {/** @desc Hello2 */ var MSG_HELLO = goog.getMsg('a')})
        """);

    assertOneError(JsMessageVisitor.MESSAGE_DUPLICATE_KEY);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testNoDuplicateErrorOnExternMessage() {
    extractMessagesSafely(
        """
        (function () {/** @desc Hello */
        var MSG_EXTERNAL_2 = goog.getMsg('a')})
        (function () {/** @desc Hello2 */
        var MSG_EXTERNAL_2 = goog.getMsg('a')})
        """);
  }

  @Test
  public void testUsingMsgPrefixWithFallback() {
    extractMessagesSafely(
        """
        function f() {
        /** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');
        /** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');
        var x = goog.getMsgWithFallback(
            MSG_UNNAMED_1, MSG_UNNAMED_2);
        }
        """);
  }

  @Test
  public void testUsingMsgPrefixWithFallback_rename() {
    renameMessages = true;
    extractMessagesSafely(
        """
        function f() {
        /** @desc Hello */ var MSG_A = goog.getMsg('hello');
        /** @desc Hello */ var MSG_B = goog.getMsg('hello!');
        var x = goog.getMsgWithFallback(MSG_A, MSG_B);
        }
        """);
  }

  @Test
  public void testUsingMsgPrefixWithFallback_duplicateUnnamedKeys_rename() {
    renameMessages = true;
    extractMessagesSafely(
        """
        function f() {
          /** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');
          /** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');
          var x = goog.getMsgWithFallback(
              MSG_UNNAMED_1, MSG_UNNAMED_2);
        }
        function g() {
          /** @desc Hello */ var MSG_UNNAMED_1 = goog.getMsg('hello');
          /** @desc Hello */ var MSG_UNNAMED_2 = goog.getMsg('hello');
          var x = goog.getMsgWithFallback(
              MSG_UNNAMED_1, MSG_UNNAMED_2);
        }
        """);
  }

  @Test
  public void testUsingMsgPrefixWithFallback_module() {
    extractMessagesSafely(
        """
        /** @desc Hello */ var MSG_A = goog.getMsg('hello');
        /** @desc Hello */ var MSG_B = goog.getMsg('hello!');
        var x = goog.getMsgWithFallback(messages.MSG_A, MSG_B);
        """);
  }

  @Test
  public void testUsingMsgPrefixWithFallback_moduleRenamed() {
    extractMessagesSafely(
        """
        /** @desc Hello */ var MSG_A = goog.getMsg('hello');
        /** @desc Hello */ var MSG_B = goog.getMsg('hello!');
        var x = goog.getMsgWithFallback(module$exports$messages$MSG_A, MSG_B);
        """);
  }

  @Test
  public void testErrorWhenUsingMsgPrefixWithFallback() {
    extractMessages(
        """
        /** @desc Hello */ var MSG_HELLO_1 = goog.getMsg('hello');
        /** @desc Hello */ var MSG_HELLO_2 = goog.getMsg('hello');
        /** @desc Hello */
        var MSG_HELLO_3 = goog.getMsgWithFallback(MSG_HELLO_1, MSG_HELLO_2);
        """);
    assertOneError(MESSAGE_TREE_MALFORMED);
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testRenamedMessages_var() {
    renameMessages = true;

    extractMessagesSafely("/** @desc Hello */ var MSG_HELLO = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_HELLO");
    assertThat(msg.getDesc()).isEqualTo("Hello");
    // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
    assertThat(msg.getSourceName()).isEqualTo("testcode:1");
  }

  @Test
  public void testRenamedMessages_getprop() {
    renameMessages = true;

    extractMessagesSafely("/** @desc a */ pint.sub.MSG_MENU_MARK_AS_UNREAD = goog.getMsg('a')");

    JsMessage msg = assertOneMessage();
    assertThat(msg.getKey()).isEqualTo("MSG_MENU_MARK_AS_UNREAD");
    assertThat(msg.getDesc()).isEqualTo("a");
  }

  @Test
  public void testGetMsgWithOptions() {
    extractMessagesSafely(
        """
        /** @desc Hello */
        var MSG_HELLO =
            goog.getMsg(
                'Hello, {$name}',
                {
                  'name': getName(),
                },
                {
                  html: false,
                  unescapeHtmlEntities: true,
                  example: {
                    'name': 'George',
                  },
                  original_code: {
                    'name': 'getName()',
                  },
                })
        """);

    JsMessage msg = assertOneMessage();
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
        """
        /** @desc something */
        const MSG_X = goog.getMsg(); // no arguments
        """);
    assertOneError("Message parse tree malformed. Message string literal expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithBadValuesArg() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                getName()); // should be an object literal
        """);
    assertOneError("Message parse tree malformed. object literal expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithBadValuesKey() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                {
                  [name]: getName() // a computed key is not allowed
                });
        """);
    assertOneError("Message parse tree malformed. string key expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithBadOptionsArg() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                options); // options bag must be an object literal
        """);
    assertOneError("Message parse tree malformed. object literal expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithComputedKeyInOptions() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  [options]: true // option names cannot be computed keys
                });
        """);
    assertOneError("Message parse tree malformed. string key expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithUnknownOption() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  unknownOption: true // not a valid option name
                });
        """);
    assertOneError("Message parse tree malformed. Unknown option: unknownOption");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithInvalidBooleanOption() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  html: 'true' // boolean option value must be a boolean literal
                });
        """);
    assertOneError("Message parse tree malformed. html: Literal true or false expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithOriginalCodeForInvalidExample() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  example: 'name: something' // not an object literal
                });
        """);
    assertOneError("Message parse tree malformed. object literal expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithInvalidOriginalCodeValue() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  original_code: {
                    'name': getName() // value is not a string
                  }
                });
        """);
    assertOneError("Message parse tree malformed. literal string or concatenation expected");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGoogGetMsgWithOriginalCodeForUnknownPlaceholder() {
    extractMessages(
        """
        /** @desc something */
        const MSG_X =
            goog.getMsg(
                'Hello, {$name}',
                { 'name': getName() },
                {
                  original_code: {
                    'unknownPlaceholder': 'something' // not a valid placeholder name
                  }
                });
        """);
    assertOneError("Message parse tree malformed. Unknown placeholder: unknownPlaceholder");
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testGetMsgWithGoogScope() {
    extractMessagesSafely(
        """
        /** @desc Suggestion Code found outside of <head> tag. */
        var $jscomp$scope$12345$0$MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG =
        goog.getMsg('Code should be added to <body> tag.');
        """);

    JsMessage msg = assertOneMessage();
    assertThat(msg.getId()).isEqualTo("MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG");
    assertThat(msg.getKey())
        .isEqualTo("$jscomp$scope$12345$0$MSG_CONSUMER_SURVEY_CODE_OUTSIDE_BODY_TAG");
  }

  @CanIgnoreReturnValue
  private JsMessage assertOneMessage() {
    assertThat(messages).hasSize(1);
    return messages.get(0);
  }

  private void assertOneError(DiagnosticType type, String description) {
    assertOneError(type);
    assertOneError(description);
  }

  private void assertOneError(DiagnosticType type) {
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(type);
  }

  private void assertOneError(String description) {
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(description);
  }

  private void assertOneWarning(DiagnosticType type, String description) {
    assertOneWarning(type);
    assertOneWarning(description);
  }

  private void assertOneWarning(DiagnosticType type) {
    assertThat(compiler.getWarnings())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(type);
  }

  private void assertOneWarning(String description) {
    assertThat(compiler.getWarnings())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(description);
  }

  private void extractMessagesSafely(String input) {
    extractMessages(input);
    assertThat(compiler.getWarnings()).isEmpty();
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
