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
package com.google.javascript.jscomp.gwt.client;

import com.google.gwt.core.client.EntryPoint;

/**
 * Entry point that exports the GWT version of the compiler and the JsfileParser in the same binary.
 * These two classes share a lot of code and thus producing one binary should save code over two.
 */
final class CompilerMain implements EntryPoint {
  private static final GwtRunner gwtRunner = new GwtRunner();
  private static final JsfileParser jsFileParser = new JsfileParser();

  @Override
  public void onModuleLoad() {
    gwtRunner.exportCompile();
    jsFileParser.exportGjd();
  }
}
