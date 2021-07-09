/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.AbstractCompiler.LocaleData;
import com.google.javascript.rhino.Node;

// ** GWT compatible no-op replacement for {@code LocaleDataPasses} */
final class LocaleDataPasses {

  private LocaleDataPasses() {}

  static class ExtractAndProtect implements CompilerPass {

    ExtractAndProtect(AbstractCompiler compiler) {}

    @Override
    public void process(Node externs, Node root) {}

    public LocaleData getLocaleValuesDataMaps() {
      return new LocaleData() {};
    }
  }

  static class LocaleSubstitutions implements CompilerPass {
    LocaleSubstitutions(AbstractCompiler compiler, String locale, LocaleData localeData) {}

    public void process(Node externs, Node root) {}
  }
}
