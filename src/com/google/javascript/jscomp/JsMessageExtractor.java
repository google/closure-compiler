/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Extracts messages and message comments from JS code.
 *
 * <p> Uses a special prefix (e.g. {@code MSG_}) to determine which variables
 * are messages. Here are the recognized formats:
 *
 *   <code>
 *   var MSG_FOO = "foo";
 *   var MSG_FOO_HELP = "this message is used for foo";
 *   </code>
 *
 *   <code>
 *   var MSG_BAR = function(a, b) {
 *     return a + " bar " + b;
 *   }
 *   var MSG_BAR_HELP = "the bar message";
 *   </code>
 *
 * <p>This class enforces the policy that message variable names must be unique
 * across all JS files.
 *
 */
@GwtIncompatible("JsMessage.Builder")
public final class JsMessageExtractor {

  private final JsMessage.Style style;
  private final JsMessage.IdGenerator idGenerator;
  private final CompilerOptions options;
  private final boolean extractExternalMessages;

  public JsMessageExtractor(
      JsMessage.IdGenerator idGenerator,
      JsMessage.Style style) {
    this(idGenerator, style, new CompilerOptions(), false /* extractExternalMessages */);
  }

  public JsMessageExtractor(
      JsMessage.IdGenerator idGenerator,
      JsMessage.Style style,
      CompilerOptions options,
      boolean extractExternalMessages) {
    this.idGenerator = idGenerator;
    this.style = style;
    this.options = options;
    this.extractExternalMessages = extractExternalMessages;
  }

  /**
   * Visitor that collects messages.
   */
  private class ExtractMessagesVisitor extends JsMessageVisitor {
    // We use List here as we want to preserve insertion-order for found
    // messages.
    // Take into account that messages with the same id could be present in the
    // result list. Message could have the same id only in case if they are
    // unnamed and have the same text but located in different source files.
    private final List<JsMessage> messages = new LinkedList<>();

    private ExtractMessagesVisitor(AbstractCompiler compiler) {
      super(compiler, true, style, idGenerator);
    }

    @Override
    protected void processJsMessage(JsMessage message,
        JsMessageDefinition definition) {
      if (extractExternalMessages || !message.isExternal()) {
        messages.add(message);
      }
    }

    /**
     * Returns extracted messages.
     *
     * @return collection of JsMessage objects that was found in js sources.
     */
    public Collection<JsMessage> getMessages() {
      return messages;
    }
  }

  /**
   * Extracts JS messages from JavaScript code.
   */
  public Collection<JsMessage> extractMessages(SourceFile... inputs)
      throws IOException {
    return extractMessages(ImmutableList.copyOf(inputs));
  }


  /**
   * Extracts JS messages from JavaScript code.
   *
   * @param inputs  the JavaScript source code inputs
   * @return the extracted messages collection
   * @throws RuntimeException if there are problems parsing the JS code or the
   *     JS messages, or if two messages have the same key
   */
  public <T extends SourceFile> Collection<JsMessage> extractMessages(Iterable<T> inputs) {

    Compiler compiler = new Compiler();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.copyOf(inputs),
        options);
    compiler.parseInputs();

    ExtractMessagesVisitor extractCompilerPass =
        new ExtractMessagesVisitor(compiler);
    if (compiler.getErrors().length == 0) {
      extractCompilerPass.process(null, compiler.getRoot());
    }

    JSError[] errors = compiler.getErrors();
    // Check for errors.
    if (errors.length > 0) {
      StringBuilder msg = new StringBuilder("JSCompiler errors\n");
      MessageFormatter formatter = new LightweightMessageFormatter(compiler);
      for (JSError e : errors) {
        msg.append(formatter.formatError(e));
      }
      throw new RuntimeException(msg.toString());
    }

    return extractCompilerPass.getMessages();
  }
}
