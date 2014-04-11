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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A MessageBundle that parses messages from an XML Translation Bundle (XTB)
 * file.
 *
 */
@SuppressWarnings("sunapi")
public class XtbMessageBundle implements MessageBundle {
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
    Preconditions.checkState(!"".equals(projectId));
    this.messages = Maps.newHashMap();
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
      if (BUNDLE_ELEM_NAME.equals(qName)) {
        Preconditions.checkState(lang == null);
        lang = atts.getValue(LANG_ATT_NAME);
        Preconditions.checkState(lang != null && !lang.isEmpty());
      } else if (TRANSLATION_ELEM_NAME.equals(qName)) {
        Preconditions.checkState(msgBuilder == null);
        String id = atts.getValue(MESSAGE_ID_ATT_NAME);
        Preconditions.checkState(id != null && !id.isEmpty());
        msgBuilder = new JsMessage.Builder(id);
      } else if (PLACEHOLDER_ELEM_NAME.equals(qName)) {
        Preconditions.checkState(msgBuilder != null);
        String phRef = atts.getValue(PLACEHOLDER_NAME_ATT_NAME);
        phRef = JsMessageVisitor.toLowerCamelCaseWithNumericSuffixes(phRef);
        msgBuilder.appendPlaceholderReference(phRef);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if (TRANSLATION_ELEM_NAME.equals(qName)) {
        Preconditions.checkState(msgBuilder != null);
        if (!msgBuilder.hasParts()) {
          msgBuilder.appendStringPart("");
        }
        String key = msgBuilder.getKey();
        messages.put(key, msgBuilder.build());
        msgBuilder = null;
      }
    }

    @Override
    public void characters(char ch[], int start, int length) {
      if (msgBuilder != null) {
        // Append a string literal to the message.
        msgBuilder.appendStringPart(String.valueOf(ch, start, length));
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
