/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.gwt.client;

import java.io.OutputStream;
import java.io.PrintStream;
import jsinterop.annotations.JsMethod;

/**
 * A {@link PrintSteam} implementation for Node.js.
 *
 * <p>TODO(johnlenz): remove this once GWT has a proper PrintStream implementation
 */
class NodePrintStream extends PrintStream {
  private String line = "";

  NodePrintStream() {
    super((OutputStream) null);
  }

  @Override
  public void println(String s) {
    print(s + "\n");
  }

  @Override
  public void print(String s) {
    if (useStdErr()) {
      writeToStdErr(s);
    } else {
      writeFinishedLinesToConsole(s);
    }
  }

  private void writeFinishedLinesToConsole(String s) {
    line = line + s;
    int start = 0;
    int end = 0;
    while ((end = line.indexOf('\n', start)) != -1) {
      writeToConsole(line.substring(start, end));
      start = end + 1;
    }
    line = line.substring(start);
  }

  @JsMethod
  private static native boolean useStdErr() /*-{
    return !!(typeof process != "undefined" && process.stderr);
  }-*/;

  @JsMethod
  private native void writeToStdErr(String s) /*-{
    process.stderr.write(s);
  }-*/;

  // NOTE: console methods always add a newline following the text.
  @JsMethod
  private native void writeToConsole(String s) /*-{
    console.log(s);
  }-*/;

  @Override
  public void close() {}

  @Override
  public void flush() {}

  @Override
  public void write(byte[] buffer, int offset, int length) {}

  @Override
  public void write(int oneByte) {}
}
