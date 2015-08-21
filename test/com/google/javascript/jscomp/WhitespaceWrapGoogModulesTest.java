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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class WhitespaceWrapGoogModulesTest extends CompilerTestCase {

  private LanguageMode languageOut;

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_STRICT);
    languageOut = LanguageMode.ECMASCRIPT6_STRICT;
    disableTypeCheck();
    enableLineNumberCheck(false);
  }
  
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new WhitespaceWrapGoogModules(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(languageOut);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testGoogModuleRewrite() {
    test(
        LINE_JOINER.join(
            "goog.module('test');",
            "var f = 5;",
            "exports = f;"),
        LINE_JOINER.join(
            "goog.loadModule(function(exports) {",
            "  \"use strict\";",
            "  goog.module('test');",
            "  var f = 5;",
            "  exports = f;",
            "  return exports;",
            "});"));
  }
}
