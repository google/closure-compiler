/*
 * Copyright 2009 The Closure Compiler Authors.
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


import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * An abstract representation of a JavaScript source file, as input to
 * JSCompiler.
 *
 * @author nicksantos@google.com (Nick Santos)
 * @author moedinger@google.com (Andrew Moedinger)
 * @deprecated JSSourceFile is an empty wrapper around SourceFile. Just
 *     use SourceFile directly.
 */
// TODO(nicksantos): Delete this file.
@Deprecated
public class JSSourceFile extends SourceFile implements Serializable {
  private static final long serialVersionUID = 1L;

  public static JSSourceFile fromFile(String fileName, Charset charSet) {
    return new JSSourceFile(SourceFile.fromFile(fileName, charSet));
  }

  public static JSSourceFile fromFile(String fileName) {
    return new JSSourceFile(SourceFile.fromFile(fileName, Charsets.UTF_8));
  }

  public static JSSourceFile fromFile(File file, Charset charSet) {
    return new JSSourceFile(SourceFile.fromFile(file, charSet));
  }

  public static JSSourceFile fromFile(File file) {
    return new JSSourceFile(SourceFile.fromFile(file, Charsets.UTF_8));
  }

  public static JSSourceFile fromCode(String fileName, String code) {
    return new JSSourceFile(SourceFile.fromCode(fileName, code));
  }

  public static JSSourceFile fromInputStream(String fileName, InputStream s)
      throws IOException {
    return new JSSourceFile(SourceFile.fromInputStream(fileName, s));
  }

  public static JSSourceFile fromGenerator(String fileName,
      Generator generator) {
    return new JSSourceFile(SourceFile.fromGenerator(fileName, generator));
  }


  private SourceFile referenced;

  private JSSourceFile(SourceFile referenced) {
    super(referenced.getName());
    this.referenced = referenced;
  }

  @Override
  public String getCode() throws IOException {
    return referenced.getCode();
  }

  @Override
  public void clearCachedSource() {
    referenced.clearCachedSource();
  }

  @Override
  @VisibleForTesting
  String getCodeNoCache() {
    return referenced.getCodeNoCache();
  }
}
