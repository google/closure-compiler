/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.rhino;

import elemental2.core.JsRegExp;

/** Determines whether a string is a valid JS identifier. */
public class JSIdentifier {
  // TODO(moz): Support full range of valid characters.
  private static final JsRegExp JS_IDENTIFIER_REGEX = new JsRegExp("^[a-zA-Z_$][\\w$]*$");

  /** Determines whether a string is a valid JS identifier. */
  public static boolean isJSIdentifier(String s) {
    return JS_IDENTIFIER_REGEX.test(s);
  }
}
