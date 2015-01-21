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

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Tests {@link XtbMessageBundle}.
 *
 */
public class XtbMessageBundleTest extends TestCase {

  private final static String PROJECT_ID = "TestProject";

  private static final String XTB =
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

  public void test() {
    InputStream stream = new ByteArrayInputStream(XTB.getBytes(UTF_8));
    XtbMessageBundle bundle = new XtbMessageBundle(
        stream, PROJECT_ID);

    JsMessage message = bundle.getMessage("7639678437384034548");
    assertEquals("descargar", message.toString());

    message = bundle.getMessage("2398375912250604550");
    assertEquals("Se han\nignorado {$num} conversaciones.",
        message.toString());

    message = bundle.getMessage("6323937743550839320");
    assertEquals("{$pStart}Si, puede {$linkStart_1_3}hacer "
        + "clic{$linkEnd_1_3} para utilizar.{$pEnd}{$pStart}Esperamos "
        + "poder ampliar.{$pEnd}",
        message.toString());

    message = bundle.getMessage("3945720239421293834");
    assertThat(message.toString()).isEmpty();
    assertThat(message.parts()).isNotEmpty();
  }
}
