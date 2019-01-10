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

package com.google.javascript.jscomp.deps;

import com.google.javascript.jscomp.ErrorManager;

/** GWT compatible no-op replacement for {@code JsFileParser} */
public final class JsFileParser {
  public JsFileParser(ErrorManager errorManager) {
  }

  public JsFileParser setModuleLoader(ModuleLoader loader) {
    throw new UnsupportedOperationException("JsFileParser.setModuleLoader not implemented");
  }

  public JsFileParser setIncludeGoogBase(boolean include) {
    throw new UnsupportedOperationException("JsFileParser.setIncludeGoogBase not implemented");
  }

  public DependencyInfo parseFile(String filePath, String closureRelativePath,
      String fileContents) {
    throw new UnsupportedOperationException("JsFileParser.parseFile not implemented");
  }

  public static boolean isSupported() {
    return false;
  }
}
