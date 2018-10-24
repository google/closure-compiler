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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * A MessageBundle that parses messages from an XML Translation Bundle (XTB)
 * file.
 *
 * TODO(moz): Make this GWT compatible.
 *
 */
@GwtIncompatible("Currently not used in GWT version")
@SuppressWarnings("sunapi")
public final class XtbMessageBundle implements MessageBundle {
  /**
   * Detects an ICU-formatted plural or select message. Any placeholders occurring inside these
   * messages must be rewritten in ICU format.
   */
  static boolean isStartOfIcuMessage(String part) {
    // ICU messages start with a '{' followed by an identifier, followed by a ',' and then 'plural'
    // or 'select' follows by another comma.
    // the 'startsWith' check is redundant but should allow us to skip using the matcher
    if (!part.startsWith("{")) {
      return false;
    }
    int commaIndex = part.indexOf(',', 1);
    // if commaIndex == 1 that means the identifier is empty, which isn't allowed.
    if (commaIndex <= 1) {
      return false;
    }
    int nextBracketIndex = part.indexOf('{', 1);
    return (nextBracketIndex == -1 || nextBracketIndex > commaIndex)
        && (part.startsWith("plural,", commaIndex + 1)
            || part.startsWith("select,", commaIndex + 1));
  }

  static String asIcuPlaceholder(String phName) {
    return SimpleFormat.format("{%s}", phName);
  }

  private static final SecureEntityResolver NOOP_RESOLVER
      = new SecureEntityResolver();

  private final Map<String, JsMessage> messages;
  private final JsMessage.IdGenerator idGenerator;

  public XtbMessageBundle(
      InputStream xtb, @Nullable String projectId,
      @SuppressWarnings("unused") boolean unused) {
    this(xtb, projectId);
  }

  /**
   * Creates an instance and initializes it with the messages in an XTB file.
   *
   * @param xtb  the XTB file as a byte stream
   * @param projectId  the translation console project id (i.e. name)
   */
  public XtbMessageBundle(InputStream xtb, @Nullable String projectId) {
    this.messages = new HashMap<>();
    this.idGenerator = new GoogleJsMessageIdGenerator(projectId);

    try {
      // Use a SAX parser for speed and less memory usage.
      SAXParser parser = createSAXParser();
      XMLReader reader = parser.getXMLReader();
      Handler contentHandler = new Handler();
      reader.setContentHandler(contentHandler);
      reader.parse(new InputSource(xtb));
    } catch (ParserConfigurationException | IOException | SAXException e) {
      throw new RuntimeException(e);
    }
  }

  // Inlined from guava-internal.
  private static SAXParser createSAXParser()
      throws ParserConfigurationException, SAXException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setValidating(false);
    factory.setXIncludeAware(false);
    factory.setFeature(
        "http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature(
        "http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

    SAXParser parser = factory.newSAXParser();
    XMLReader xmlReader = parser.getXMLReader();
    xmlReader.setEntityResolver(NOOP_RESOLVER);
    return parser;
  }

  @Override
  public JsMessage getMessage(String id) {
    return messages.get(id);
  }

  @Override
  public JsMessage.IdGenerator idGenerator() {
    return idGenerator;
  }

  @Override
  public Iterable<JsMessage> getAllMessages() {
    return Iterables.unmodifiableIterable(messages.values());
  }

  /**
   * A {@link ContentHandler} that creates a {@link JsMessage} for each message
   * parsed from an XML Translation Bundle (XTB) file.
   */
  private class Handler implements ContentHandler {
    private static final String BUNDLE_ELEM_NAME = "translationbundle";
    private static final String LANG_ATT_NAME = "lang";

    private static final String TRANSLATION_ELEM_NAME = "translation";
    private static final String MESSAGE_ID_ATT_NAME = "id";

    private static final String PLACEHOLDER_ELEM_NAME = "ph";
    private static final String PLACEHOLDER_NAME_ATT_NAME = "name";

    boolean isIcuMessage;

    String lang;
    JsMessage.Builder msgBuilder;

    @Override
    public void setDocumentLocator(Locator locator) {}

    @Override
    public void startDocument() {}

    @Override
    public void endDocument() {}

    @Override
    public void startPrefixMapping(String prefix, String uri) {}

    @Override
    public void endPrefixMapping(String prefix) {}

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes atts) {
      switch (qName) {
        case BUNDLE_ELEM_NAME:
          checkState(lang == null);
          lang = atts.getValue(LANG_ATT_NAME);
          checkState(lang != null && !lang.isEmpty());
          break;
        case TRANSLATION_ELEM_NAME:
          checkState(msgBuilder == null);
          String id = atts.getValue(MESSAGE_ID_ATT_NAME);
          checkState(id != null && !id.isEmpty());
          msgBuilder = new JsMessage.Builder(id);
          break;
        case PLACEHOLDER_ELEM_NAME:
          checkState(msgBuilder != null);
          String phRef = atts.getValue(PLACEHOLDER_NAME_ATT_NAME);
          phRef = JsMessageVisitor.toLowerCamelCaseWithNumericSuffixes(phRef);
          if (isIcuMessage) {
            msgBuilder.appendStringPart(asIcuPlaceholder(phRef));
          } else {
            msgBuilder.appendPlaceholderReference(phRef);
          }
          break;
        default: // fall out
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if (TRANSLATION_ELEM_NAME.equals(qName)) {
        checkState(msgBuilder != null);
        if (!msgBuilder.hasParts()) {
          msgBuilder.appendStringPart("");
        }
        String key = msgBuilder.getKey();
        messages.put(key, msgBuilder.build());
        msgBuilder = null;
        isIcuMessage = false;
      }
    }

    @Override
    public void characters(char ch[], int start, int length) {
      if (msgBuilder != null) {
        String part = String.valueOf(ch, start, length);
        if (!msgBuilder.hasParts()) {
          isIcuMessage = isStartOfIcuMessage(part);
        }
        // Append a string literal to the message.
        msgBuilder.appendStringPart(part);
      }
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) {
      if (msgBuilder != null) {
        // Preserve whitespace in messages.
        msgBuilder.appendStringPart(String.valueOf(ch, start, length));
      }
    }

    @Override
    public void processingInstruction(String target, String data) {}

    @Override
    public void skippedEntity(String name) {}
  }

  /**
   * A secure EntityResolver that returns an empty string in response to
   * any attempt to resolve an external entity. The class is used by our
   * secure version of the internal saxon SAX parser.
   */
  private static final class SecureEntityResolver implements EntityResolver {

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
      return new InputSource(new StringReader(""));
    }
  }
}
