/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.deps;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;


/**
 * A utility class to assist in creating JS bundle files.
 */
public class ClosureBundler {

  public ClosureBundler() {
  }

  static void appendPrefix(Appendable out, DependencyInfo info) throws IOException {
    if (info.isModule()) {
      // add the prefix on the first line so the line numbers aren't affected.
      out.append("goog.loadModule(function(exports) {"
          + "'use strict';");
    }
  }

  static void appendPostfix(Appendable out, DependencyInfo info) throws IOException {
    if (info.isModule()) {
      out.append(
          "\n" // terminate any trailing single line comment.
          + ";" // terminate any trailing expression.
          + "return exports;});\n");
    }
  }

  /** Append the contents of the file to the supplied appendable. */
  public static void appendInput(
      Appendable out,
      DependencyInfo info,
      File input, Charset inputCharset) throws IOException {
    appendPrefix(out, info);
    Files.copy(input, inputCharset, out);
    appendPostfix(out, info);
  }

  /** Append the contents of the string to the supplied appendable. */
  public static void appendInput(
      Appendable out,
      DependencyInfo info,
      String contents) throws IOException {
    appendPrefix(out, info);
    out.append(contents);
    appendPostfix(out, info);
  }
}

