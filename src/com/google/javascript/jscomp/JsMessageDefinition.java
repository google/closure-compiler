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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.Node;
import org.jspecify.nullness.Nullable;

/**
 * Container class that holds a {@link JsMessage} and information about the {@code goog.getMsg()}
 * call it was built from.
 */
public interface JsMessageDefinition {

  /** The JsMessage object built from the `goog.getMsg()` call. */
  JsMessage getMessage();

  /**
   * The RHS node of the message assignment statement.
   *
   * <p>e.g. The <code>goog.getMsg()</code> call in:
   *
   * <pre><code>
   *   const MSG_HELLO =
   *       goog.getMsg(
   *           'Hello, {$name}.',
   *           { 'name', getName() },
   *           { unescapeHtmlEntities: true });
   * </code></pre>
   */
  Node getMessageNode();

  /**
   * The Node representing the message text template.
   *
   * <p>This node may be a literal string or a concatenation of literal strings.
   *
   * <p>For a {@code goog.getMsg()} call this is the first argument.
   */
  Node getTemplateTextNode();

  /**
   * The object literal {@link Node} that maps placeholder names to expressions providing their
   * values.
   *
   * <p>This value will be {@code null} if the message definition didn't specify placeholder values.
   */
  @Nullable Node getPlaceholderValuesNode();

  /**
   * A map from placehlolder name to the Node assigned to it in the values map argument of
   * `goog.getMsg()`.
   *
   * <p>This will be an empty map if there was no object or it was an empty object literal.
   */
  ImmutableMap<String, Node> getPlaceholderValueMap();

  /**
   * The value of the 'html' options key in the options bag argument.
   *
   * <p>This value will be <code>false</code> if there was no options bag argument, or if it didn't
   * contain an 'html' property.
   */
  boolean shouldEscapeLessThan();

  /**
   * The value of the 'unescapeHtmlEntities' options key in the options bag argument.
   *
   * <p>This value will be <code>false</code> if there was no options bag argument, or if it didn't
   * contain an 'unescapeHtmlEntities' property.
   */
  boolean shouldUnescapeHtmlEntities();
}
