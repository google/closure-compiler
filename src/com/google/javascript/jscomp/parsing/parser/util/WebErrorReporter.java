/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.util;

import com.google.common.collect.TreeMultimap;

/**
 * An error reporter that dump error messages to browser's JS console.
 */
public class WebErrorReporter extends ErrorReporter {

  private TreeMultimap<String, String> messages = TreeMultimap.create();

  @Override
  protected void reportMessage(SourcePosition location, String message) {
    System.err.println(message);
    messages.put(location.source.name, message);
  }

  public String getMessages(String source) {
    if (!messages.containsKey(source)) {
      return "// JSPP compilation failed due to errors in other source files\n";
    }
    StringBuilder buffer = new StringBuilder();
    for (String message : messages.get(source)) {
      buffer.append("console.error('" + message.replace("'", "\\'") + "');\n");
    }
    return buffer.toString();
  }
}
