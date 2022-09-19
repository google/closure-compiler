/*
 * Copyright 2006 The Closure Compiler Authors.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.JsMessage.Part;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link XtbMessageBundle}.
 */
@RunWith(JUnit4.class)
public final class XtbMessageBundleTest {

  private static final String PROJECT_ID = "TestProject";

  @Test
  public void testXtbBundle() {
    String xtb =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE translationbundle SYSTEM"
            + " \"translationbundle.dtd\">\n"
            + "<translationbundle lang=\"zh-HK\">\n"
            + "<translation id=\"7639678437384034548\">descargar</translation>\n"
            + "<translation id=\"2398375912250604550\">Se han\nignorado"
            + " <ph name=\"NUM\"/> conversaciones.</translation>\n"
            + "<translation id=\"6323937743550839320\"><ph name=\"P_START\"/>Si,"
            + " puede <ph name=\"LINK_START_1_3\"/>hacer"
            + " clic<ph name=\"LINK_END_1_3\"/>"
            + " para utilizar.<ph name=\"P_END\"/><ph name=\"P_START\"/>Esperamos"
            + " poder ampliar.<ph name=\"P_END\"/></translation>\n"
            + "<translation id=\"3945720239421293834\"></translation>\n"
            + "</translationbundle>";
    InputStream stream = new ByteArrayInputStream(xtb.getBytes(UTF_8));
    XtbMessageBundle bundle = new XtbMessageBundle(stream, PROJECT_ID);

    JsMessage message = bundle.getMessage("7639678437384034548");
    assertThat(message.asJsMessageString()).isEqualTo("descargar");

    message = bundle.getMessage("2398375912250604550");
    assertThat(message.asJsMessageString()).isEqualTo("Se han\nignorado {$num} conversaciones.");

    message = bundle.getMessage("6323937743550839320");
    assertThat(message.asJsMessageString())
        .isEqualTo(
            "{$pStart}Si, puede {$linkStart_1_3}hacer "
                + "clic{$linkEnd_1_3} para utilizar.{$pEnd}{$pStart}Esperamos "
                + "poder ampliar.{$pEnd}");

    message = bundle.getMessage("3945720239421293834");
    assertThat(message.asJsMessageString()).isEmpty();
    assertThat(message.getParts()).isNotEmpty();
  }

  /**
   * When using EXTERNAL messages with plurals/selects, the XTB files may contain a mix of ICU style
   * placeholders (i.e. {@code {foo}}) and regular placeholders (i.e. {@code <ph name="foo"/>}).
   * However, JsMessage and the Closure Library runtime don't expect to see regular placeholders, so
   * they must be rewritten.
   */
  @Test
  public void testXtbBundle_mixedPlaceholders() throws IOException {
    String xtbWithMixedPlaceholders =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE translationbundle SYSTEM"
            + " \"translationbundle.dtd\">\n"
            + "<translationbundle lang=\"ru_RU\">\n"
            + "<translation id=\"123456\">"
            + "{USER_GENDER,select,"
            + "female{Hello <ph name=\"USER_IDENTIFIER\"/>.}"
            + "male{Hello <ph name=\"USER_IDENTIFIER\"/>.}"
            + "other{Hello <ph name=\"USER_IDENTIFIER\"/>.}}"
            + "</translation>\n"
            + "<translation id=\"123457\">"
            + "<ph name=\"START_PARAGRAPH\"/>p1<ph name=\"END_PARAGRAPH\"/>"
            + "<ph name=\"START_PARAGRAPH\"/>p1<ph name=\"END_PARAGRAPH\"/>"
            + "</translation>"
            + "</translationbundle>";
    InputStream stream = new ByteArrayInputStream(xtbWithMixedPlaceholders.getBytes(UTF_8));
    XtbMessageBundle bundle = new XtbMessageBundle(stream, PROJECT_ID);

    assertThat(bundle.getAllMessages()).hasSize(2);
    final JsMessage icuMsg = bundle.getMessage("123456");
    assertThat(icuMsg.asIcuMessageString())
        .isEqualTo(
            "{USER_GENDER,select,"
                + "female{Hello {USER_IDENTIFIER}.}"
                + "male{Hello {USER_IDENTIFIER}.}"
                + "other{Hello {USER_IDENTIFIER}.}}");
    // For an ICU selector formatted message, XtbMessageBundle automatically converts all the
    // placeholders into normal strings.
    final ImmutableList<Part> icuMsgParts = icuMsg.getParts();
    assertThat(icuMsgParts).hasSize(7);
    assertThat(icuMsgParts.get(0).getString()).isEqualTo("{USER_GENDER,select,female{Hello ");
    assertThat(icuMsgParts.get(1).getCanonicalPlaceholderName()).isEqualTo("USER_IDENTIFIER");
    assertThat(icuMsgParts.get(2).getString()).isEqualTo(".}male{Hello ");
    assertThat(icuMsgParts.get(3).getCanonicalPlaceholderName()).isEqualTo("USER_IDENTIFIER");
    assertThat(icuMsgParts.get(4).getString()).isEqualTo(".}other{Hello ");
    assertThat(icuMsgParts.get(5).getCanonicalPlaceholderName()).isEqualTo("USER_IDENTIFIER");
    assertThat(icuMsgParts.get(6).getString()).isEqualTo(".}}");

    // Previous ICU message should not to affect next message
    final JsMessage normalMsg = bundle.getMessage("123457");
    assertThat(normalMsg.asJsMessageString())
        .isEqualTo("{$startParagraph}p1{$endParagraph}{$startParagraph}p1{$endParagraph}");
    final ImmutableList<Part> normalMsgParts = normalMsg.getParts();
    // For a normal message the placeholders are not turned into strings
    assertThat(normalMsgParts).hasSize(6);
    assertThat(normalMsgParts.get(0).getJsPlaceholderName()).isEqualTo("startParagraph");
    assertThat(normalMsgParts.get(1).getString()).isEqualTo("p1");
    assertThat(normalMsgParts.get(2).getJsPlaceholderName()).isEqualTo("endParagraph");
    assertThat(normalMsgParts.get(3).getJsPlaceholderName()).isEqualTo("startParagraph");
    assertThat(normalMsgParts.get(4).getString()).isEqualTo("p1");
    assertThat(normalMsgParts.get(5).getJsPlaceholderName()).isEqualTo("endParagraph");
  }

  /**
   * An ICU message using {@code phex} to describe its variables should result in the same
   * JavaScript code as the same ICU message without those {@code phex} attributes.
   */
  @Test
  public void testXtbBundle_icuPluralWithAndWithoutPhex() throws IOException {
    String xtb =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE translationbundle SYSTEM"
            + " \"translationbundle.dtd\">\n"
            + "<translationbundle lang=\"de_CH\">\n"
            // With phex
            + "<translation id=\"123456\">"
            + "{NUM,plural, "
            + "=1{Setting: {START_STRONG}<ph name=\"UDC_SETTING\"/>{END_STRONG}"
            + " Products: {START_STRONG}<ph name=\"PRODUCT_LIST\"/>{END_STRONG}.}"
            + "other{Settings: {START_STRONG}<ph name=\"UDC_SETTING_LIST\"/>{END_STRONG}"
            + " Products: {START_STRONG}<ph name=\"PRODUCT_LIST\"/>{END_STRONG}.}"
            + "}"
            + "</translation>\n"
            // Without phex
            + "<translation id=\"987654\">"
            + "{NUM,plural, "
            + "=1{Setting: {START_STRONG}{UDC_SETTING}{END_STRONG}"
            + " Products: {START_STRONG}{PRODUCT_LIST}{END_STRONG}.}"
            + "other{Settings: {START_STRONG}{UDC_SETTING_LIST}{END_STRONG}"
            + " Products: {START_STRONG}{PRODUCT_LIST}{END_STRONG}.}"
            + "}"
            + "</translation>\n"
            + "</translationbundle>";
    InputStream stream = new ByteArrayInputStream(xtb.getBytes(UTF_8));
    XtbMessageBundle bundle = new XtbMessageBundle(stream, PROJECT_ID);

    assertThat(bundle.getAllMessages()).hasSize(2);
    assertThat(bundle.getMessage("123456").asIcuMessageString())
        .isEqualTo(
            "{NUM,plural, "
                + "=1{Setting: {START_STRONG}{UDC_SETTING}{END_STRONG}"
                + " Products: {START_STRONG}{PRODUCT_LIST}{END_STRONG}.}"
                + "other{Settings: {START_STRONG}{UDC_SETTING_LIST}{END_STRONG}"
                + " Products: {START_STRONG}{PRODUCT_LIST}{END_STRONG}.}}");
    // Both translation entries should result into the same message in JavaScript.
    assertThat(bundle.getMessage("987654").asIcuMessageString())
        .isEqualTo(bundle.getMessage("123456").asIcuMessageString());
  }
}
