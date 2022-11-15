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

import com.google.javascript.rhino.Node;

/**
 * Contains a {@link JsMessage} representing an ICU template message and information about the
 * {@code goog.i18n.messages.declareIcuTemplate()} call from which it was extracted.
 */
public interface IcuTemplateDefinition {
  /** The JsMessage object built from the `declareIcuTemplate()` call. */
  JsMessage getMessage();

  /**
   * The RHS node of the message assignment statement.
   *
   * <p>e.g. The <code>declareIcuTemplate()</code> call in:
   *
   * <pre><code>
   *   const MSG_HELLO =
   *       declareIcuTemplate(
   *           'Hello, {NAME}.',
   *           {
   *             description: 'A greeting',
   *             example: {
   *               'NAME': 'Jane'
   *             });
   * </code></pre>
   */
  Node getMessageNode();

  /**
   * The Node representing the message text template.
   *
   * <p>This node may be a literal string or a concatenation of literal strings.
   */
  Node getTemplateTextNode();
}
