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

package com.google.common.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/** GWT compatible no-op replacement for {@code Files} */
public final class Files {

  public static CharSink asCharSink(File to, Charset charset) {
    throw new UnsupportedOperationException("Files.asCharSink not implemented");
  }

  public static void write(CharSequence from, File to, Charset charset)
      throws IOException {
  }

  public static void append(CharSequence from, File to, Charset charset)
      throws IOException {
  }

  public static String toString(File file, Charset charset) throws IOException {
    throw new UnsupportedOperationException("Files.toString not implemented");
  }
}
