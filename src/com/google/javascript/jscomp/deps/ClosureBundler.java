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

import com.google.common.io.CharSource;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;


/**
 * A utility class to assist in creating JS bundle files.
 */
public class ClosureBundler {
  private boolean useEval = false;
  private String sourceUrl = null;

  public ClosureBundler() {
  }

  public final ClosureBundler useEval(boolean useEval) {
    this.useEval = useEval;
    return this;
  }

  public final ClosureBundler withSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
    return this;
  }

  /** Append the contents of the string to the supplied appendable. */
  public static void appendInput(
      Appendable out,
      DependencyInfo info,
      String contents) throws IOException {
    new ClosureBundler().appendTo(out, info, contents);
  }

  /** Append the contents of the string to the supplied appendable. */
  public void appendTo(
      Appendable out,
      DependencyInfo info,
      String content) throws IOException {
    appendTo(out, info, CharSource.wrap(content));
  }

  /** Append the contents of the file to the supplied appendable. */
  public void appendTo(
      Appendable out,
      DependencyInfo info,
      File content, Charset contentCharset) throws IOException {
    appendTo(out, info, Files.asCharSource(content, contentCharset));
  }

  /** Append the contents of the CharSource to the supplied appendable. */
  public void appendTo(
      Appendable out,
      DependencyInfo info,
      CharSource content) throws IOException {
    if (info.isModule()) {
      appendGoogModule(out, content);
    } else {
      appendTraditional(out, content);
    }
  }

  private void appendTraditional(Appendable out, CharSource contents)
      throws IOException {
    if (useEval) {
      out.append("(0,eval(\"");
      append(out, Mode.ESCAPED, contents);
      appendSourceUrl(out, Mode.ESCAPED);
      out.append("\"));");
    } else {
      append(out, Mode.NORMAL, contents);
      appendSourceUrl(out, Mode.NORMAL);
    }
  }

  private void appendGoogModule(Appendable out, CharSource contents)
      throws IOException {
    if (useEval) {
      out.append("goog.loadModule(\"");
      append(out, Mode.ESCAPED, contents);
      appendSourceUrl(out, Mode.ESCAPED);
      out.append("\");");
    } else {
      // add the prefix on the first line so the line numbers aren't affected.
      out.append(
          "goog.loadModule(function(exports) {"
          + "'use strict';");
      append(out, Mode.NORMAL, contents);
      out.append(
          "\n" // terminate any trailing single line comment.
          + ";" // terminate any trailing expression.
          + "return exports;});\n");
      appendSourceUrl(out, Mode.NORMAL);
    }
  }

  private enum Mode {
    ESCAPED {
      @Override void append(String s, Appendable out) throws IOException {
        out.append(SourceCodeEscapers.javascriptEscaper().escape(s));
      }
    },
    NORMAL {
      @Override void append(String s, Appendable out) throws IOException {
        out.append(s);
      }
    };

    abstract void append(String s, Appendable out) throws IOException;
  }

  private void append(Appendable out, Mode mode, String s) throws IOException {
    String transformed = transformInput(s);
    mode.append(transformed, out);
  }

  private void append(Appendable out, Mode mode, CharSource cs)
      throws IOException {
    append(out, mode, cs.read());
  }

  private void appendSourceUrl(Appendable out, Mode mode) throws IOException {
    if (sourceUrl == null) {
      return;
    }
    String toAppend = "\n//# sourceURL=" + sourceUrl + "\n";
    // Don't go through #append. That method relies on #transformInput,
    // but source URLs generally aren't valid JS inputs.
    mode.append(toAppend, out);
  }

  /**
   * Template method. Subclasses that need to transform the inputs should override this method.
   * (For example, {@link TranspilingClosureBundler#transformInput} transpiles inputs from ES6
   * to ES5.)
   */
  protected String transformInput(String input) {
    return input;
  }
}

