/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.resources.ResourceLoader;

class ErrorFormat {

  private static final ImmutableMap<String, String> MESSAGES =
      ResourceLoader.loadPropertiesMap("rhino/Messages.properties");

  static String format(String key, Object... args) {
    return justFormat(MESSAGES.get(key), args);
  }

  private static String justFormat(String s, Object... args) {
    // Note that this doesn't hand single-quote hence not compatible with MessageFormat.
    // TODO: consider moving to a simple shared message format implementation in both JVM and Web.
    for (int i = 0; i < args.length; i++) {
      String toReplace = "{" + i + "}";
      s = s.replace(toReplace, String.valueOf(args[i]));
    }
    return s;
  }
}
