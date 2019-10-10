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
import java.io.IOException;
import jsinterop.annotations.JsMethod;

/** Entry point that exports just the GWT version of the compiler. */
final class JsRunnerMainGwtEntryPoint implements EntryPoint {
  @Override
  public void onModuleLoad() {
    JsRunnerMain.exportCompile();
  }

  // We need to forward this function from a GWT only file so we can specify a custom namespace.
  @JsMethod(namespace = "jscomp")
  public static JsRunnerMain.ChunkOutput compile(
      JsRunnerMain.Flags flags, JsRunnerMain.File[] inputs) throws IOException {
    return JsRunnerMain.compile(flags, inputs);
  }
}
